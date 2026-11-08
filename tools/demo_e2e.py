"""Run the complete ProjectDesignDemo workflow against a running environment."""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from generate_colmap_fixture import generate_fixture


class DemoError(RuntimeError):
    pass


class DemoClient:
    def __init__(self, api_root: str, web_root: str):
        self.api_root = api_root.rstrip("/")
        self.api_base = f"{self.api_root}/api"
        self.web_root = web_root.rstrip("/")

    def request(
        self,
        method: str,
        url: str,
        body: bytes | None = None,
        headers: dict[str, str] | None = None,
        expected: tuple[int, ...] = (200,),
    ) -> tuple[int, Any, bytes]:
        request = urllib.request.Request(url, data=body, method=method, headers=headers or {})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status = response.status
                response_body = response.read()
                response_headers = response.headers
        except urllib.error.HTTPError as error:
            status = error.code
            response_body = error.read()
            response_headers = error.headers
        except urllib.error.URLError as error:
            raise DemoError(f"无法访问 {url}: {error.reason}") from error
        if status not in expected:
            detail = response_body.decode("utf-8", errors="replace")[:800]
            raise DemoError(f"{method} {url} 返回 HTTP {status}: {detail}")
        return status, response_headers, response_body

    def json(
        self,
        method: str,
        path: str,
        payload: dict[str, Any] | None = None,
        expected: tuple[int, ...] = (200,),
    ) -> Any:
        body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {"Content-Type": "application/json"} if body is not None else {}
        _, _, response_body = self.request(
            method, f"{self.api_base}{path}", body, headers, expected
        )
        return json.loads(response_body) if response_body else None

    def upload(self, project_id: str, image: Path) -> dict[str, Any]:
        boundary = f"----ProjectDesignDemo{uuid.uuid4().hex}"
        prefix = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="{image.name}"\r\n'
            "Content-Type: image/bmp\r\n\r\n"
        ).encode("ascii")
        suffix = f"\r\n--{boundary}--\r\n".encode("ascii")
        body = prefix + image.read_bytes() + suffix
        _, _, response_body = self.request(
            "POST",
            f"{self.api_base}/projects/{project_id}/images",
            body,
            {"Content-Type": f"multipart/form-data; boundary={boundary}"},
            (201,),
        )
        return json.loads(response_body)


def wait_until_ready(client: DemoClient, timeout: int) -> None:
    deadline = time.monotonic() + timeout
    last_error = ""
    while time.monotonic() < deadline:
        try:
            _, _, body = client.request(
                "GET", f"{client.api_root}/actuator/health/readiness", expected=(200,)
            )
            if json.loads(body).get("status") == "UP":
                client.request("GET", f"{client.web_root}/health", expected=(200,))
                return
        except (DemoError, json.JSONDecodeError) as error:
            last_error = str(error)
        time.sleep(1)
    raise DemoError(f"服务未在 {timeout} 秒内就绪。{last_error}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise DemoError(message)


def run_demo(args: argparse.Namespace) -> dict[str, Any]:
    started_at = datetime.now(timezone.utc)
    client = DemoClient(args.api_root, args.web_root)
    wait_until_ready(client, args.ready_timeout)
    project_id: str | None = None
    checks: list[str] = []
    cleanup_status = "not requested"

    try:
        project = client.json(
            "POST",
            "/projects",
            {
                "name": "自动验收青铜礼器",
                "description": "阶段十自动化验收项目：验证从图片上传、三维重建到公开发布的完整链路。",
                "heritageType": "青铜器",
                "collectionCode": f"E2E-{started_at:%Y%m%d%H%M%S}",
                "era": "商代晚期",
                "material": "青铜",
                "dimensions": "高 32 cm，口径 18 cm",
                "provenance": "ProjectDesignDemo 确定性验收样例",
            },
            (201,),
        )
        project_id = project["id"]
        require(project["publicationStatus"] == "DRAFT", "新项目不是草稿状态")
        public_before = client.json("GET", "/public/heritage")
        require(all(item["id"] != project_id for item in public_before), "草稿项目出现在公开列表")
        checks.append("draft-hidden")

        with tempfile.TemporaryDirectory(prefix="project-design-demo-") as fixture_directory:
            images = generate_fixture(Path(fixture_directory), args.images)
            for image in images:
                client.upload(project_id, image)
        uploaded = client.json("GET", f"/projects/{project_id}/images")
        require(len(uploaded) == args.images, "上传后的图片数量不正确")
        checks.append("fixture-uploaded")

        job = client.json(
            "POST",
            f"/projects/{project_id}/jobs",
            {"algorithm": args.algorithm},
            (201,),
        )
        job_id = job["id"]
        deadline = time.monotonic() + args.timeout
        while job["status"] not in {"SUCCEEDED", "FAILED"} and time.monotonic() < deadline:
            time.sleep(args.poll_interval)
            job = client.json("GET", f"/projects/{project_id}/jobs/{job_id}")
        require(job["status"] == "SUCCEEDED", f"重建未成功：{job.get('message')}")
        checks.append("reconstruction-succeeded")

        artifacts = client.json("GET", f"/projects/{project_id}/artifacts")
        require(len(artifacts) >= 1, "重建成功后没有成果版本")
        artifact = next((item for item in artifacts if item["current"]), artifacts[0])
        client.json(
            "POST", f"/projects/{project_id}/artifacts/{artifact['id']}/select"
        )
        checks.append("artifact-selected")

        published = client.json("POST", f"/projects/{project_id}/publish")
        require(published["publicationStatus"] == "PUBLISHED", "项目发布状态不正确")
        public_list = client.json("GET", "/public/heritage")
        require(any(item["id"] == project_id for item in public_list), "发布项目未出现在公开列表")
        public_detail = client.json("GET", f"/public/heritage/{project_id}")
        require(public_detail["artifactId"] == artifact["id"], "公开成果不是当前选中版本")

        _, cover_headers, cover = client.request(
            "GET", f"{client.api_base}/public/heritage/{project_id}/cover"
        )
        require(cover.startswith(b"BM"), "公开代表图不是有效 BMP")
        require(cover_headers.get_content_type() == "image/bmp", "代表图 MIME 类型不正确")
        _, model_headers, model = client.request(
            "GET", f"{client.api_base}/public/heritage/{project_id}/model"
        )
        require(model.startswith(b"glTF"), "公开模型不是有效 GLB")
        require(model_headers.get_content_type() == "model/gltf-binary", "模型 MIME 类型不正确")
        client.request("GET", f"{client.web_root}/exhibits/{project_id}", expected=(200,))
        checks.extend(["published-visible", "cover-readable", "model-readable", "public-page-readable"])

        unpublished = client.json("POST", f"/projects/{project_id}/unpublish")
        require(unpublished["publicationStatus"] == "UNPUBLISHED", "取消发布状态不正确")
        client.request(
            "GET", f"{client.api_base}/public/heritage/{project_id}", expected=(404,)
        )
        client.json("POST", f"/projects/{project_id}/publish")
        checks.append("unpublish-republish")

        completed_at = datetime.now(timezone.utc)
        report = {
            "status": "PASSED",
            "environment": args.environment_label,
            "startedAt": started_at.isoformat(),
            "completedAt": completed_at.isoformat(),
            "durationSeconds": round((completed_at - started_at).total_seconds(), 3),
            "apiRoot": args.api_root,
            "webRoot": args.web_root,
            "projectId": project_id,
            "jobId": job_id,
            "algorithm": args.algorithm,
            "imageCount": args.images,
            "artifact": {
                "id": artifact["id"],
                "versionNumber": artifact["versionNumber"],
                "format": artifact["format"],
                "pointCount": artifact["pointCount"],
                "faceCount": artifact["faceCount"],
                "sizeBytes": artifact["sizeBytes"],
            },
            "checks": checks,
        }
        return report
    finally:
        if project_id and not args.keep_project:
            try:
                client.json("DELETE", f"/projects/{project_id}", expected=(204,))
                cleanup_status = "project deleted"
            except DemoError as error:
                print(f"警告：验收项目清理失败：{error}", file=sys.stderr)
                cleanup_status = "project cleanup failed"
        if "report" in locals():
            report["cleanup"] = cleanup_status


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-root", default="http://localhost:8080")
    parser.add_argument("--web-root", default="http://localhost:5173")
    parser.add_argument(
        "--algorithm",
        choices=("DEMO_MESH", "COLMAP_MESH"),
        default="DEMO_MESH",
        help="DEMO_MESH is the fast deterministic check; COLMAP_MESH runs real reconstruction.",
    )
    parser.add_argument("--images", type=int, default=8)
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--ready-timeout", type=int, default=180)
    parser.add_argument("--poll-interval", type=float, default=1.0)
    parser.add_argument("--environment-label", default="running local environment")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--keep-project", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    try:
        report = run_demo(args)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(
                json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
        print(json.dumps(report, ensure_ascii=False, indent=2))
    except (DemoError, OSError, ValueError) as error:
        print(f"DELIVERY CHECK FAILED: {error}", file=sys.stderr)
        raise SystemExit(1) from error


if __name__ == "__main__":
    main()
