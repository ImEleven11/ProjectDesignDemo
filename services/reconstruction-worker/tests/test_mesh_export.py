from pathlib import Path
import json
import struct

from app.mesh_export import (
    convert_ply_mesh_to_glb,
    create_convex_hull_glb,
    create_demo_mesh,
    read_ply_mesh,
)


def test_converts_ascii_ply_triangle_to_valid_glb(tmp_path: Path) -> None:
    source = tmp_path / "triangle.ply"
    source.write_text(
        """ply
format ascii 1.0
element vertex 3
property float x
property float y
property float z
property uchar red
property uchar green
property uchar blue
element face 1
property list uchar int vertex_indices
end_header
0 0 0 255 0 0
1 0 0 0 255 0
0 1 0 0 0 255
3 0 1 2
""",
        encoding="ascii",
    )
    output = tmp_path / "triangle.glb"

    face_count = convert_ply_mesh_to_glb(source, output)

    assert face_count == 1
    data = output.read_bytes()
    magic, version, length = struct.unpack("<4sII", data[:12])
    assert magic == b"glTF"
    assert version == 2
    assert length == len(data)
    json_length, chunk_type = struct.unpack("<I4s", data[12:20])
    assert chunk_type == b"JSON"
    document = json.loads(data[20:20 + json_length].decode("utf-8"))
    assert document["meshes"][0]["primitives"][0]["mode"] == 4


def test_demo_mesh_is_deterministic_and_contains_faces(tmp_path: Path) -> None:
    first = tmp_path / "first.glb"
    second = tmp_path / "second.glb"

    face_count = create_demo_mesh(first, latitude_segments=8, longitude_segments=12)
    create_demo_mesh(second, latitude_segments=8, longitude_segments=12)

    assert face_count == 168
    assert first.read_bytes() == second.read_bytes()


def test_reads_binary_little_endian_ply(tmp_path: Path) -> None:
    source = tmp_path / "binary.ply"
    header = (
        "ply\nformat binary_little_endian 1.0\n"
        "element vertex 3\nproperty float x\nproperty float y\nproperty float z\n"
        "property uchar red\nproperty uchar green\nproperty uchar blue\n"
        "element face 1\nproperty list uchar int vertex_indices\nend_header\n"
    ).encode("ascii")
    vertices = b"".join(
        struct.pack("<3f3B", *vertex)
        for vertex in ((0, 0, 0, 255, 0, 0), (1, 0, 0, 0, 255, 0), (0, 1, 0, 0, 0, 255))
    )
    source.write_bytes(header + vertices + struct.pack("<B3i", 3, 0, 1, 2))

    mesh = read_ply_mesh(source)

    assert len(mesh.positions) == 3
    assert mesh.colors[1] == (0, 255, 0)
    assert mesh.faces == [(0, 1, 2)]


def test_creates_closed_surface_from_sparse_point_cloud(tmp_path: Path) -> None:
    source = tmp_path / "points.ply"
    source.write_text(
        """ply
format ascii 1.0
element vertex 5
property float x
property float y
property float z
property uchar red
property uchar green
property uchar blue
end_header
0 0 0 255 0 0
1 0 0 0 255 0
0 1 0 0 0 255
0 0 1 255 255 0
0.2 0.2 0.2 255 255 255
""",
        encoding="ascii",
    )
    output = tmp_path / "surface.glb"

    face_count = create_convex_hull_glb(source, output)

    assert face_count >= 4
    assert output.read_bytes()[:4] == b"glTF"
