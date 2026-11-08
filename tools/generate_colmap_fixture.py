"""Generate a deterministic multi-view BMP capture for local COLMAP verification."""

from pathlib import Path
import argparse
import math
import random
import struct


WIDTH = 960
HEIGHT = 720
FOCAL_LENGTH = 820.0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    parser.add_argument("--images", type=int, default=8)
    args = parser.parse_args()
    generate_fixture(args.output, args.images)
    print(f"generated {args.images} images in {args.output.resolve()}")


def generate_fixture(output: Path, image_count: int = 8) -> list[Path]:
    """Generate the deterministic capture set and return its image paths."""
    if image_count < 2:
        raise ValueError("image_count must be at least 2")
    output.mkdir(parents=True, exist_ok=True)

    randomizer = random.Random(20261103)
    points = [
        (
            randomizer.uniform(-2.2, 2.2),
            randomizer.uniform(-1.6, 1.6),
            randomizer.uniform(4.0, 8.5),
            randomizer.getrandbits(25),
        )
        for _ in range(420)
    ]
    generated: list[Path] = []
    for index in range(image_count):
        fraction = index / max(image_count - 1, 1)
        camera = (
            -0.9 + 1.8 * fraction,
            0.18 * math.sin(fraction * math.pi * 2),
            0.12 * math.cos(fraction * math.pi),
        )
        pixels = bytearray([215] * (WIDTH * HEIGHT * 3))
        basis = camera_basis(camera, (0.0, 0.0, 6.2))
        projected = []
        for point in points:
            projection = project(point[:3], camera, basis)
            if projection is not None:
                projected.append((point[2], projection[0], projection[1], point[3]))
        for _, x, y, pattern in sorted(projected, reverse=True):
            draw_marker(pixels, x, y, pattern)
        image_path = output / f"capture_{index:02d}.bmp"
        write_bmp(image_path, pixels)
        generated.append(image_path)
    return generated


def camera_basis(camera: tuple[float, float, float], target: tuple[float, float, float]):
    forward = normalize(tuple(target[i] - camera[i] for i in range(3)))
    right = normalize(cross((0.0, 1.0, 0.0), forward))
    up = cross(forward, right)
    return right, up, forward


def project(point, camera, basis):
    relative = tuple(point[i] - camera[i] for i in range(3))
    right, up, forward = basis
    depth = dot(relative, forward)
    if depth <= 0:
        return None
    x = round(WIDTH / 2 + FOCAL_LENGTH * dot(relative, right) / depth)
    y = round(HEIGHT / 2 - FOCAL_LENGTH * dot(relative, up) / depth)
    if 16 <= x < WIDTH - 16 and 16 <= y < HEIGHT - 16:
        return x, y
    return None


def draw_marker(pixels: bytearray, center_x: int, center_y: int, pattern: int) -> None:
    cell_size = 2
    marker_size = 14
    left = center_x - marker_size // 2
    top = center_y - marker_size // 2
    for y in range(marker_size):
        for x in range(marker_size):
            border = x < 2 or y < 2 or x >= marker_size - 2 or y >= marker_size - 2
            if border:
                value = 25
            else:
                cell_x = (x - 2) // cell_size
                cell_y = (y - 2) // cell_size
                bit = cell_y * 5 + cell_x
                value = 245 if (pattern >> bit) & 1 else 55
            set_pixel(pixels, left + x, top + y, value)


def set_pixel(pixels: bytearray, x: int, y: int, value: int) -> None:
    offset = (y * WIDTH + x) * 3
    pixels[offset:offset + 3] = bytes((value, value, value))


def write_bmp(path: Path, rgb_pixels: bytearray) -> None:
    row_size = (WIDTH * 3 + 3) & ~3
    pixel_bytes = row_size * HEIGHT
    header = struct.pack("<2sIHHI", b"BM", 54 + pixel_bytes, 0, 0, 54)
    dib = struct.pack(
        "<IIIHHIIIIII", 40, WIDTH, HEIGHT, 1, 24, 0, pixel_bytes, 2835, 2835, 0, 0
    )
    with path.open("wb") as bmp:
        bmp.write(header)
        bmp.write(dib)
        padding = bytes(row_size - WIDTH * 3)
        for y in range(HEIGHT - 1, -1, -1):
            start = y * WIDTH * 3
            row = rgb_pixels[start:start + WIDTH * 3]
            for x in range(0, len(row), 3):
                bmp.write(bytes((row[x + 2], row[x + 1], row[x])))
            bmp.write(padding)


def dot(left, right):
    return sum(left[i] * right[i] for i in range(3))


def cross(left, right):
    return (
        left[1] * right[2] - left[2] * right[1],
        left[2] * right[0] - left[0] * right[2],
        left[0] * right[1] - left[1] * right[0],
    )


def normalize(vector):
    length = math.sqrt(dot(vector, vector))
    return tuple(component / length for component in vector)


if __name__ == "__main__":
    main()
