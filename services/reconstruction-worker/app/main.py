from contextlib import asynccontextmanager
from pathlib import Path
import hmac
import json
import math
import os
import random
from typing import Literal
from uuid import UUID

import httpx
from fastapi import BackgroundTasks, Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field

from app.colmap_pipeline import run_colmap_reconstruction
from app.mesh_export import create_demo_mesh


SERVICE_NAME = "reconstruction-worker"
DATA_ROOT = Path(os.getenv("WORKER_DATA_ROOT", "/data")).resolve()
INPUT_ROOT = Path(os.getenv("WORKER_INPUT_ROOT", "/inputs")).resolve()
API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8080").rstrip("/")
WORKER_TOKEN = os.getenv("WORKER_TOKEN", "demo-worker-local-token")
COLMAP_TIMEOUT_SECONDS = int(os.getenv("COLMAP_TIMEOUT_SECONDS", "1800"))


class JobRequest(BaseModel):
    job_id: UUID = Field(alias="jobId")
    project_id: UUID = Field(alias="projectId")
    algorithm: Literal[
        "COLMAP_SPARSE", "COLMAP_MESH", "COLMAP_DENSE", "DEMO_SPARSE", "DEMO_MESH"
    ]
    image_count: int = Field(alias="imageCount", ge=2)


class JobAccepted(BaseModel):
    job_id: UUID
    status: Literal["ACCEPTED"] = "ACCEPTED"


@asynccontextmanager
async def lifespan(_: FastAPI):
    DATA_ROOT.mkdir(parents=True, exist_ok=True)
    yield


app = FastAPI(
    title="Project Design Reconstruction Worker",
    version="0.3.0",
    lifespan=lifespan,
)


def require_worker_token(authorization: str | None = Header(default=None)) -> None:
    expected = f"Bearer {WORKER_TOKEN}"
    if authorization is None or not hmac.compare_digest(authorization, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid worker token")


@app.get("/health", tags=["operations"])
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "service": SERVICE_NAME,
        "stage": "reconstruction-pipeline",
    }


@app.post("/jobs", response_model=JobAccepted, status_code=status.HTTP_202_ACCEPTED)
def submit_job(
    request: JobRequest,
    background_tasks: BackgroundTasks,
    _: None = Depends(require_worker_token),
) -> JobAccepted:
    background_tasks.add_task(run_reconstruction, request)
    return JobAccepted(job_id=request.job_id)


def run_reconstruction(request: JobRequest) -> None:
    try:
        if request.algorithm.startswith("COLMAP_"):
            mode = {
                "COLMAP_SPARSE": "SPARSE",
                "COLMAP_MESH": "MESH",
                "COLMAP_DENSE": "DENSE",
            }[request.algorithm]
            stage_message = {
                "SPARSE": "COLMAP 正在提取特征并执行稀疏重建",
                "MESH": "COLMAP 正在重建稀疏模型并生成彩色表面",
                "DENSE": "COLMAP 正在执行 CUDA 稠密重建与网格化",
            }[mode]
            callback(request.job_id, "RUNNING", stage_message)
            result = run_colmap_reconstruction(
                request.job_id,
                request.project_id,
                DATA_ROOT,
                INPUT_ROOT,
                COLMAP_TIMEOUT_SECONDS,
                mode,
            )
            detail = (
                f"{result.point_count} 个三维点"
                if result.face_count == 0
                else f"{result.point_count} 个三维点，{result.face_count} 个三角面"
            )
            callback(
                request.job_id,
                "SUCCEEDED",
                f"COLMAP 重建完成：{result.image_count} 张图像，{detail}",
                result.result_path,
            )
            return

        if request.algorithm == "DEMO_MESH":
            callback(request.job_id, "RUNNING", "Demo 正在生成可交互表面模型")
            relative_directory = Path("jobs") / str(request.job_id)
            output_directory = (DATA_ROOT / relative_directory).resolve()
            if DATA_ROOT not in output_directory.parents:
                raise ValueError("result path escaped data root")
            output_directory.mkdir(parents=True, exist_ok=True)
            sparse_path = output_directory / "sparse.ply"
            point_count = create_demo_sparse_cloud(
                sparse_path, request.job_id, request.image_count
            )
            result_path = output_directory / "model.glb"
            face_count = create_demo_mesh(result_path)
            relative_result = relative_directory / "model.glb"
            metadata = {
                "jobId": str(request.job_id),
                "projectId": str(request.project_id),
                "engine": "DEMO_MESH_V1",
                "imageCount": request.image_count,
                "pointCount": point_count,
                "faceCount": face_count,
                "resultKind": "SURFACE_MODEL",
                "resultPath": relative_result.as_posix(),
                "sparseResultPath": (relative_directory / "sparse.ply").as_posix(),
            }
            (output_directory / "result.json").write_text(
                json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            callback(
                request.job_id,
                "SUCCEEDED",
                f"Demo 表面模型生成完成，共 {face_count} 个三角面",
                relative_result.as_posix(),
            )
            return

        callback(request.job_id, "RUNNING", "Demo 稀疏重建正在生成点云")
        relative_result = Path("jobs") / str(request.job_id) / "sparse.ply"
        output_path = (DATA_ROOT / relative_result).resolve()
        if DATA_ROOT not in output_path.parents:
            raise ValueError("result path escaped data root")
        output_path.parent.mkdir(parents=True, exist_ok=True)
        point_count = create_demo_sparse_cloud(output_path, request.job_id, request.image_count)
        metadata = {
            "jobId": str(request.job_id),
            "projectId": str(request.project_id),
            "engine": "DEMO_SPARSE_V1",
            "imageCount": request.image_count,
            "pointCount": point_count,
            "resultPath": relative_result.as_posix(),
        }
        (output_path.parent / "result.json").write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        callback(
            request.job_id,
            "SUCCEEDED",
            f"Demo 稀疏点云生成完成，共 {point_count} 个点",
            relative_result.as_posix(),
        )
    except Exception as exception:
        try:
            callback(request.job_id, "FAILED", f"重建失败: {exception}")
        except Exception:
            pass


def create_demo_sparse_cloud(output_path: Path, job_id: UUID, image_count: int) -> int:
    """Create a deterministic PLY artifact so the demo runs without COLMAP or a GPU."""
    point_count = max(120, image_count * 80)
    randomizer = random.Random(job_id.int)
    points: list[str] = []
    for index in range(point_count):
        angle = index * (math.pi * (3 - math.sqrt(5)))
        height = 1 - 2 * (index + 0.5) / point_count
        radius = math.sqrt(max(0.0, 1 - height * height))
        jitter = 1 + randomizer.uniform(-0.06, 0.06)
        x = radius * math.cos(angle) * jitter
        y = height * 1.25 * jitter
        z = radius * math.sin(angle) * jitter
        red = int(150 + 70 * (x + 1) / 2)
        green = int(95 + 65 * (y / 1.25 + 1) / 2)
        blue = int(45 + 55 * (z + 1) / 2)
        points.append(f"{x:.6f} {y:.6f} {z:.6f} {red} {green} {blue}")
    header = (
        "ply\nformat ascii 1.0\n"
        "comment ProjectDesignDemo deterministic sparse cloud\n"
        f"element vertex {point_count}\n"
        "property float x\nproperty float y\nproperty float z\n"
        "property uchar red\nproperty uchar green\nproperty uchar blue\n"
        "end_header\n"
    )
    output_path.write_text(header + "\n".join(points) + "\n", encoding="ascii")
    return point_count


def callback(job_id: UUID, status_value: str, message: str, result_path: str | None = None) -> None:
    payload = {"status": status_value, "message": message, "resultPath": result_path}
    with httpx.Client(timeout=10.0) as client:
        response = client.patch(
            f"{API_BASE_URL}/internal/jobs/{job_id}",
            headers={"Authorization": f"Bearer {WORKER_TOKEN}"},
            json=payload,
        )
        response.raise_for_status()
