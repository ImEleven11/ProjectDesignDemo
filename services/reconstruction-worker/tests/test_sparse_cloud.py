from pathlib import Path
from uuid import UUID

from app.main import create_demo_sparse_cloud


def test_sparse_cloud_is_deterministic_and_valid(tmp_path: Path) -> None:
    job_id = UUID("12345678-1234-5678-1234-567812345678")
    first = tmp_path / "first.ply"
    second = tmp_path / "second.ply"

    point_count = create_demo_sparse_cloud(first, job_id, 3)
    create_demo_sparse_cloud(second, job_id, 3)

    assert point_count == 240
    assert first.read_bytes() == second.read_bytes()
    assert "element vertex 240" in first.read_text(encoding="ascii")
