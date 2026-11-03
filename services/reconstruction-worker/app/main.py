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


SERVICE_NAME = "reconstruction-worker"
DATA_ROOT = Path(os.getenv("WORKER_DATA_ROOT", "/data")).resolve()
API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8080").rstrip("/")
WORKER_TOKEN = os.getenv("WORKER_TOKEN", "demo-worker-local-token")


class JobRequest(BaseModel):
    job_id: UUID = Field(alias="jobId")
    project_id: UUID = Field(alias="projectId")
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
    version="0.2.0",
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
