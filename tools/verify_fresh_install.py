"""Build an isolated clean Compose environment and run the delivery workflow."""

from __future__ import annotations

import argparse
import os
import socket
import subprocess
import sys
import time
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def available_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def command(arguments: list[str], environment: dict[str, str]) -> None:
    print("+", " ".join(arguments), flush=True)
    subprocess.run(arguments, cwd=REPOSITORY_ROOT, env=environment, check=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=Path(".tmp/demo-delivery-report.json"))
    parser.add_argument("--algorithm", choices=("DEMO_MESH", "COLMAP_MESH"), default="DEMO_MESH")
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--keep-environment", action="store_true")
    args = parser.parse_args()

    project_name = f"pddelivery{int(time.time())}"
    environment = os.environ.copy()
    ports = {
        "POSTGRES_PORT": available_port(),
        "API_PORT": available_port(),
        "WORKER_PORT": available_port(),
        "WEB_PORT": available_port(),
    }
    environment.update({key: str(value) for key, value in ports.items()})
    environment["WORKER_TOKEN"] = f"delivery-{project_name}"
    compose = ["docker", "compose", "--project-name", project_name]

    try:
        command(compose + ["up", "--build", "--wait", "-d"], environment)
        command(compose + ["ps"], environment)
        command(
            [
                sys.executable,
                str(REPOSITORY_ROOT / "tools" / "demo_e2e.py"),
                "--api-root",
                f"http://localhost:{ports['API_PORT']}",
                "--web-root",
                f"http://localhost:{ports['WEB_PORT']}",
                "--algorithm",
                args.algorithm,
                "--timeout",
                str(args.timeout),
                "--environment-label",
                f"isolated Compose project {project_name} with clean volumes",
                "--report",
                str(args.report.resolve()),
            ],
            environment,
        )
    finally:
        if args.keep_environment:
            print(f"保留隔离环境：{project_name}")
        else:
            command(compose + ["down", "--volumes", "--remove-orphans"], environment)


if __name__ == "__main__":
    main()
