from dataclasses import dataclass
from pathlib import Path
import json
import math
import struct


PLY_TYPES = {
    "char": ("b", 1), "int8": ("b", 1),
    "uchar": ("B", 1), "uint8": ("B", 1),
    "short": ("h", 2), "int16": ("h", 2),
    "ushort": ("H", 2), "uint16": ("H", 2),
    "int": ("i", 4), "int32": ("i", 4),
    "uint": ("I", 4), "uint32": ("I", 4),
    "float": ("f", 4), "float32": ("f", 4),
    "double": ("d", 8), "float64": ("d", 8),
}


@dataclass(frozen=True)
class MeshData:
    positions: list[tuple[float, float, float]]
    colors: list[tuple[int, int, int]]
    faces: list[tuple[int, int, int]]


def convert_ply_mesh_to_glb(input_path: Path, output_path: Path) -> int:
    mesh = read_ply_mesh(input_path)
    write_glb(mesh, output_path)
    return len(mesh.faces)


def create_convex_hull_glb(input_path: Path, output_path: Path) -> int:
    """Create a deterministic, closed CPU surface around a COLMAP point cloud."""
    from scipy.spatial import ConvexHull

    positions, colors = read_ply_points(input_path)
    if len(positions) < 4:
        raise ValueError("表面模型至少需要 4 个三维点")
    hull = ConvexHull(positions, qhull_options="QJ")
    faces = [tuple(int(index) for index in face) for face in hull.simplices]
    write_glb(MeshData(positions, colors, faces), output_path)
    return len(faces)


def read_ply_points(path: Path) -> tuple[list[tuple[float, float, float]], list[tuple[int, int, int]]]:
    with path.open("rb") as stream:
        header_lines: list[str] = []
        while True:
            raw = stream.readline()
            if not raw:
                raise ValueError("PLY 文件缺少 end_header")
            line = raw.decode("ascii", errors="strict").strip()
            header_lines.append(line)
            if line == "end_header":
                break

        file_format = next(
            (line.split()[1] for line in header_lines if line.startswith("format ")), None
        )
        if file_format not in {"ascii", "binary_little_endian"}:
            raise ValueError(f"不支持的 PLY 格式: {file_format}")

        vertex_count = 0
        vertex_properties: list[tuple[str, str]] = []
        current_element = ""
        for line in header_lines:
            parts = line.split()
            if len(parts) >= 3 and parts[0] == "element":
                current_element = parts[1]
                if current_element == "vertex":
                    vertex_count = int(parts[2])
            elif parts[:1] == ["property"] and current_element == "vertex" and len(parts) == 3:
                vertex_properties.append((parts[1], parts[2]))

        if vertex_count <= 0 or not {"x", "y", "z"}.issubset(
            {name for _, name in vertex_properties}
        ):
            raise ValueError("PLY 点云缺少有效顶点坐标")
        if file_format == "ascii":
            return _read_ascii_vertices(stream, vertex_count, vertex_properties)
        return _read_binary_vertices(stream, vertex_count, vertex_properties)


def read_ply_mesh(path: Path) -> MeshData:
    with path.open("rb") as stream:
        header_lines: list[str] = []
        while True:
            raw = stream.readline()
            if not raw:
                raise ValueError("PLY 文件缺少 end_header")
            line = raw.decode("ascii", errors="strict").strip()
            header_lines.append(line)
            if line == "end_header":
                break

        file_format = next(
            (line.split()[1] for line in header_lines if line.startswith("format ")), None
        )
        if file_format not in {"ascii", "binary_little_endian"}:
            raise ValueError(f"不支持的 PLY 格式: {file_format}")

        vertex_count = 0
        face_count = 0
        vertex_properties: list[tuple[str, str]] = []
        face_list_types: tuple[str, str] | None = None
        current_element = ""
        for line in header_lines:
            parts = line.split()
            if len(parts) >= 3 and parts[0] == "element":
                current_element = parts[1]
                if current_element == "vertex":
                    vertex_count = int(parts[2])
                elif current_element == "face":
                    face_count = int(parts[2])
            elif parts[:1] == ["property"] and current_element == "vertex" and len(parts) == 3:
                vertex_properties.append((parts[1], parts[2]))
            elif parts[:2] == ["property", "list"] and current_element == "face" and len(parts) == 5:
                face_list_types = (parts[2], parts[3])

        required = {"x", "y", "z"}
        if vertex_count <= 0 or not required.issubset({name for _, name in vertex_properties}):
            raise ValueError("PLY 网格缺少有效顶点坐标")
        if face_count <= 0 or face_list_types is None:
            raise ValueError("PLY 文件不包含三角面")

        if file_format == "ascii":
            positions, colors = _read_ascii_vertices(stream, vertex_count, vertex_properties)
            faces = _read_ascii_faces(stream, face_count)
        else:
            positions, colors = _read_binary_vertices(stream, vertex_count, vertex_properties)
            faces = _read_binary_faces(stream, face_count, face_list_types)

    triangles = _triangulate(faces)
    if not triangles:
        raise ValueError("PLY 文件不包含可用三角面")
    return MeshData(positions, colors, triangles)


def write_glb(mesh: MeshData, output_path: Path) -> None:
    if not mesh.positions or not mesh.faces:
        raise ValueError("GLB 导出需要顶点和三角面")
    normals = _vertex_normals(mesh.positions, mesh.faces)
    indices = [index for face in mesh.faces for index in face]
    if max(indices) >= len(mesh.positions):
        raise ValueError("网格面引用了不存在的顶点")

    binary = bytearray()
    views: list[dict[str, int]] = []

    def append_view(payload: bytes, target: int) -> int:
        while len(binary) % 4:
            binary.append(0)
        offset = len(binary)
        binary.extend(payload)
        views.append({"buffer": 0, "byteOffset": offset, "byteLength": len(payload), "target": target})
        return len(views) - 1

    position_bytes = b"".join(struct.pack("<3f", *position) for position in mesh.positions)
    normal_bytes = b"".join(struct.pack("<3f", *normal) for normal in normals)
    color_bytes = bytes(channel for color in mesh.colors for channel in (*color, 255))
    index_bytes = b"".join(struct.pack("<I", index) for index in indices)
    position_view = append_view(position_bytes, 34962)
    normal_view = append_view(normal_bytes, 34962)
    color_view = append_view(color_bytes, 34962)
    index_view = append_view(index_bytes, 34963)

    minima = [min(position[axis] for position in mesh.positions) for axis in range(3)]
    maxima = [max(position[axis] for position in mesh.positions) for axis in range(3)]
    accessors = [
        {"bufferView": position_view, "componentType": 5126, "count": len(mesh.positions),
         "type": "VEC3", "min": minima, "max": maxima},
        {"bufferView": normal_view, "componentType": 5126, "count": len(normals), "type": "VEC3"},
        {"bufferView": color_view, "componentType": 5121, "normalized": True,
         "count": len(mesh.colors), "type": "VEC4"},
        {"bufferView": index_view, "componentType": 5125, "count": len(indices), "type": "SCALAR"},
    ]
    document = {
        "asset": {"version": "2.0", "generator": "ProjectDesignDemo mesh_export"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0, "name": "Heritage surface"}],
        "meshes": [{"primitives": [{
            "attributes": {"POSITION": 0, "NORMAL": 1, "COLOR_0": 2},
            "indices": 3,
            "material": 0,
            "mode": 4,
        }]}],
        "materials": [{
            "name": "Vertex color material",
            "pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 1], "metallicFactor": 0.0,
                                      "roughnessFactor": 0.82},
            "doubleSided": True,
        }],
        "buffers": [{"byteLength": len(binary)}],
        "bufferViews": views,
        "accessors": accessors,
    }
    json_bytes = json.dumps(document, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    json_bytes += b" " * ((4 - len(json_bytes) % 4) % 4)
    binary.extend(b"\x00" * ((4 - len(binary) % 4) % 4))
    total_length = 12 + 8 + len(json_bytes) + 8 + len(binary)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as glb:
        glb.write(struct.pack("<4sII", b"glTF", 2, total_length))
        glb.write(struct.pack("<I4s", len(json_bytes), b"JSON"))
        glb.write(json_bytes)
        glb.write(struct.pack("<I4s", len(binary), b"BIN\x00"))
        glb.write(binary)


def create_demo_mesh(output_path: Path, latitude_segments: int = 24, longitude_segments: int = 36) -> int:
    positions: list[tuple[float, float, float]] = []
    colors: list[tuple[int, int, int]] = []
    for latitude in range(latitude_segments + 1):
        phi = math.pi * latitude / latitude_segments
        y = math.cos(phi) * 1.2
        ring = math.sin(phi)
        for longitude in range(longitude_segments):
            theta = 2 * math.pi * longitude / longitude_segments
            x = ring * math.cos(theta) * (0.82 + 0.08 * math.cos(3 * theta))
            z = ring * math.sin(theta) * (0.82 + 0.08 * math.cos(3 * theta))
            positions.append((x, y, z))
            colors.append((int(145 + 70 * (y / 1.2 + 1) / 2), int(78 + 58 * ring), int(35 + 38 * (z + 1) / 2)))
    faces: list[tuple[int, int, int]] = []
    for latitude in range(latitude_segments):
        for longitude in range(longitude_segments):
            next_longitude = (longitude + 1) % longitude_segments
            current = latitude * longitude_segments + longitude
            right = latitude * longitude_segments + next_longitude
            below = (latitude + 1) * longitude_segments + longitude
            below_right = (latitude + 1) * longitude_segments + next_longitude
            if latitude > 0:
                faces.append((current, below, right))
            if latitude < latitude_segments - 1:
                faces.append((right, below, below_right))
    write_glb(MeshData(positions, colors, faces), output_path)
    return len(faces)


def _read_ascii_vertices(stream, count, properties):
    positions, colors = [], []
    names = [name for _, name in properties]
    for _ in range(count):
        values = stream.readline().decode("ascii").split()
        record = {name: float(values[index]) for index, name in enumerate(names)}
        positions.append((record["x"], record["y"], record["z"]))
        colors.append(tuple(int(record.get(name, default)) for name, default in (("red", 183), ("green", 132), ("blue", 78))))
    return positions, colors


def _read_ascii_faces(stream, count):
    faces = []
    for _ in range(count):
        values = [int(value) for value in stream.readline().decode("ascii").split()]
        faces.append(values[1:1 + values[0]])
    return faces


def _read_binary_vertices(stream, count, properties):
    formats = [PLY_TYPES[data_type][0] for data_type, _ in properties]
    record_struct = struct.Struct("<" + "".join(formats))
    names = [name for _, name in properties]
    positions, colors = [], []
    for _ in range(count):
        values = record_struct.unpack(stream.read(record_struct.size))
        record = dict(zip(names, values))
        positions.append((float(record["x"]), float(record["y"]), float(record["z"])))
        colors.append(tuple(int(record.get(name, default)) for name, default in (("red", 183), ("green", 132), ("blue", 78))))
    return positions, colors


def _read_binary_faces(stream, count, list_types):
    count_format, count_size = PLY_TYPES[list_types[0]]
    index_format, index_size = PLY_TYPES[list_types[1]]
    faces = []
    for _ in range(count):
        face_size = struct.unpack("<" + count_format, stream.read(count_size))[0]
        values = struct.unpack("<" + index_format * face_size, stream.read(index_size * face_size))
        faces.append(list(values))
    return faces


def _triangulate(faces):
    triangles = []
    for face in faces:
        for offset in range(1, len(face) - 1):
            triangles.append((face[0], face[offset], face[offset + 1]))
    return triangles


def _vertex_normals(positions, faces):
    normals = [[0.0, 0.0, 0.0] for _ in positions]
    for first, second, third in faces:
        a, b, c = positions[first], positions[second], positions[third]
        ab = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
        ac = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
        normal = (ab[1] * ac[2] - ab[2] * ac[1], ab[2] * ac[0] - ab[0] * ac[2],
                  ab[0] * ac[1] - ab[1] * ac[0])
        for index in (first, second, third):
            for axis in range(3):
                normals[index][axis] += normal[axis]
    result = []
    for normal in normals:
        length = math.sqrt(sum(value * value for value in normal)) or 1.0
        result.append(tuple(value / length for value in normal))
    return result
