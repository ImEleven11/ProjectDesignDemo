from dataclasses import dataclass
from pathlib import Path
import json
import os
import shutil
import subprocess
from uuid import UUID

from app.mesh_export import convert_ply_mesh_to_glb, create_convex_hull_glb


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".tif", ".tiff", ".bmp", ".webp"}


class ColmapPipelineError(RuntimeError):
    pass


@dataclass(frozen=True)
class ColmapResult:
    result_path: str
    sparse_result_path: str
    point_count: int
    face_count: int
    image_count: int
    model_directory: str
    result_kind: str


def run_colmap_sparse(
    job_id: UUID,
    project_id: UUID,
    data_root: Path,
    input_root: Path,
    timeout_seconds: int,
) -> ColmapResult:
    return run_colmap_reconstruction(
        job_id, project_id, data_root, input_root, timeout_seconds, "SPARSE"
    )


def run_colmap_reconstruction(
    job_id: UUID,
    project_id: UUID,
    data_root: Path,
    input_root: Path,
    timeout_seconds: int,
    mode: str,
) -> ColmapResult:
    if mode not in {"SPARSE", "MESH", "DENSE"}:
        raise ValueError(f"未知 COLMAP 重建模式: {mode}")
    project_images = (input_root / str(project_id)).resolve()
    if input_root not in project_images.parents or not project_images.is_dir():
        raise ColmapPipelineError("项目图像目录不存在")

    source_images = sorted(
        path for path in project_images.iterdir()
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
    )
    if len(source_images) < 3:
        raise ColmapPipelineError("COLMAP 至少需要 3 张有效图像")

    relative_job_directory = Path("jobs") / str(job_id)
    workspace = (data_root / relative_job_directory).resolve()
    if data_root not in workspace.parents:
        raise ColmapPipelineError("任务目录越过数据根目录")
    images_directory = workspace / "images"
    sparse_directory = workspace / "sparse"
    images_directory.mkdir(parents=True, exist_ok=False)
    sparse_directory.mkdir(parents=True, exist_ok=False)
    for source in source_images:
        shutil.copy2(source, images_directory / source.name)

    database_path = workspace / "database.db"
    log_path = workspace / "colmap.log"
    feature_help = _help_text("feature_extractor")
    matching_help = _help_text("exhaustive_matcher")
    extraction_gpu_option = (
        "--FeatureExtraction.use_gpu"
        if "FeatureExtraction.use_gpu" in feature_help
        else "--SiftExtraction.use_gpu"
    )
    matching_gpu_option = (
        "--FeatureMatching.use_gpu"
        if "FeatureMatching.use_gpu" in matching_help
        else "--SiftMatching.use_gpu"
    )

    _run(
        [
            "colmap", "feature_extractor",
            "--database_path", str(database_path),
            "--image_path", str(images_directory),
            extraction_gpu_option, "0",
            "--ImageReader.camera_model", "SIMPLE_RADIAL",
            "--ImageReader.single_camera", "1",
            "--SiftExtraction.max_num_features", "8192",
        ],
        log_path,
        timeout_seconds,
    )
    _run(
        [
            "colmap", "exhaustive_matcher",
            "--database_path", str(database_path),
            matching_gpu_option, "0",
        ],
        log_path,
        timeout_seconds,
    )
    _run(
        [
            "colmap", "mapper",
            "--database_path", str(database_path),
            "--image_path", str(images_directory),
            "--output_path", str(sparse_directory),
        ],
        log_path,
        timeout_seconds,
    )

    models = [path for path in sparse_directory.iterdir() if path.is_dir()]
    if not models:
        raise ColmapPipelineError("COLMAP 未能注册足够的图像，请检查照片重叠度和清晰度")
    model = max(models, key=_model_size)
    sparse_result_path = workspace / "sparse.ply"
    _run(
        [
            "colmap", "model_converter",
            "--input_path", str(model),
            "--output_path", str(sparse_result_path),
            "--output_type", "PLY",
        ],
        log_path,
        timeout_seconds,
    )
    point_count = _ply_vertex_count(sparse_result_path)
    if point_count <= 0:
        raise ColmapPipelineError("COLMAP 模型不包含有效三维点")

    result_path = sparse_result_path
    face_count = 0
    result_kind = "POINT_CLOUD"
    if mode == "MESH":
        result_path = workspace / "model.glb"
        face_count = create_convex_hull_glb(sparse_result_path, result_path)
        result_kind = "SURFACE_MODEL"
    elif mode == "DENSE":
        dense_directory = workspace / "dense"
        dense_directory.mkdir(parents=True, exist_ok=False)
        _run(
            [
                "colmap", "image_undistorter",
                "--image_path", str(images_directory),
                "--input_path", str(model),
                "--output_path", str(dense_directory),
                "--output_type", "COLMAP",
                "--max_image_size", "2000",
            ],
            log_path,
            timeout_seconds,
        )
        _run(
            [
                "colmap", "patch_match_stereo",
                "--workspace_path", str(dense_directory),
                "--workspace_format", "COLMAP",
                "--PatchMatchStereo.geom_consistency", "true",
            ],
            log_path,
            timeout_seconds,
        )
        dense_cloud = dense_directory / "fused.ply"
        _run(
            [
                "colmap", "stereo_fusion",
                "--workspace_path", str(dense_directory),
                "--workspace_format", "COLMAP",
                "--input_type", "geometric",
                "--output_path", str(dense_cloud),
            ],
            log_path,
            timeout_seconds,
        )
        dense_mesh = dense_directory / "meshed-poisson.ply"
        _run(
            [
                "colmap", "poisson_mesher",
                "--input_path", str(dense_cloud),
                "--output_path", str(dense_mesh),
            ],
            log_path,
            timeout_seconds,
        )
        result_path = workspace / "model.glb"
        face_count = convert_ply_mesh_to_glb(dense_mesh, result_path)
        point_count = _ply_vertex_count(dense_cloud)
        result_kind = "DENSE_MODEL"

    relative_result = result_path.relative_to(data_root).as_posix()
    relative_sparse_result = sparse_result_path.relative_to(data_root).as_posix()
    metadata = {
        "jobId": str(job_id),
        "projectId": str(project_id),
        "engine": f"COLMAP_{mode}",
        "surfaceMethod": "SCIPY_CONVEX_HULL" if mode == "MESH" else None,
        "colmapVersion": _colmap_version(),
        "imageCount": len(source_images),
        "pointCount": point_count,
        "faceCount": face_count,
        "resultKind": result_kind,
        "resultPath": relative_result,
        "sparseResultPath": relative_sparse_result,
        "modelDirectory": model.relative_to(workspace).as_posix(),
        "logPath": log_path.relative_to(workspace).as_posix(),
    }
    (workspace / "result.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return ColmapResult(
        result_path=relative_result,
        sparse_result_path=relative_sparse_result,
        point_count=point_count,
        face_count=face_count,
        image_count=len(source_images),
        model_directory=model.relative_to(workspace).as_posix(),
        result_kind=result_kind,
    )


def _run(command: list[str], log_path: Path, timeout_seconds: int) -> None:
    with log_path.open("a", encoding="utf-8") as log:
        log.write("\n$ " + " ".join(command) + "\n")
        log.flush()
        try:
            completed = subprocess.run(
                command,
                stdout=log,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=timeout_seconds,
                check=False,
                env={**os.environ, "QT_QPA_PLATFORM": "offscreen"},
            )
        except subprocess.TimeoutExpired as exception:
            raise ColmapPipelineError(
                f"COLMAP 步骤超时（{timeout_seconds} 秒）: {command[1]}"
            ) from exception
    if completed.returncode != 0:
        tail = _log_tail(log_path)
        raise ColmapPipelineError(
            f"COLMAP 步骤失败: {command[1]}（退出码 {completed.returncode}）\n{tail}"
        )


def _help_text(command: str) -> str:
    completed = subprocess.run(
        ["colmap", command, "-h"],
        capture_output=True,
        text=True,
        timeout=30,
        check=False,
        env={**os.environ, "QT_QPA_PLATFORM": "offscreen"},
    )
    return completed.stdout + completed.stderr


def _colmap_version() -> str:
    completed = subprocess.run(
        ["colmap", "-h"], capture_output=True, text=True, timeout=30, check=False
    )
    first_line = next((line.strip() for line in completed.stdout.splitlines() if line.strip()), "COLMAP")
    return first_line[:200]


def _model_size(model: Path) -> int:
    points = model / "points3D.bin"
    return points.stat().st_size if points.exists() else 0


def _ply_vertex_count(path: Path) -> int:
    with path.open("r", encoding="ascii", errors="replace") as ply_file:
        for line in ply_file:
            if line.startswith("element vertex "):
                return int(line.split()[-1])
            if line.strip() == "end_header":
                break
    return 0


def _log_tail(path: Path, maximum_characters: int = 1200) -> str:
    text = path.read_text(encoding="utf-8", errors="replace")
    return text[-maximum_characters:]
