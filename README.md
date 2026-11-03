# ProjectDesignDemo

数字文博稀疏三维重建 Demo。当前已具备从项目与图像管理、真实 COLMAP 异步重建，到 PLY 稀疏点云产物下载的完整运行闭环。

## 模块

- `apps/web`：Vue 3、TypeScript、Three.js、Vite 和 Nginx，提供项目工作台与 PLY 点云查看器。
- `services/api`：Java 17、Spring Boot 3、Flyway 和 Actuator。
- `services/reconstruction-worker`：Python 3.12、FastAPI 和 COLMAP 3.10，执行 CPU 稀疏重建。
- `compose.yaml`：本地服务编排和健康检查。

## 本地启动

1. 可选：复制 `.env.example` 为 `.env` 并调整端口或数据库口令。
2. 运行 `docker compose up --build -d`。
3. 运行 `docker compose ps`，等待所有服务显示为 `healthy`。

默认入口：

- Web：http://localhost:5173
- API 健康检查：http://localhost:8080/actuator/health/readiness
- 重建工作进程健康检查：http://localhost:8001/health

Web 工作台支持创建项目、批量上传图片、启动 COLMAP、轮询任务状态、下载 PLY，并通过鼠标旋转、平移和缩放查看稀疏点云。Nginx 将 `/api` 同源代理到 Spring Boot 服务。

## 业务 API

当前最小业务链路为：创建文博项目 → 上传至少三张图像 → 创建 `COLMAP_SPARSE` 稀疏重建任务 → Worker 异步执行特征提取、穷举匹配和增量建图 → 查询任务状态并下载 PLY。

`COLMAP_SPARSE` 是默认真实重建引擎。`DEMO_SPARSE` 仍可用于不依赖照片内容的快速系统联调，最低需要两张图片。

| 方法 | 接口 | 用途 |
| --- | --- | --- |
| `POST` | `/api/projects` | 创建文博项目 |
| `GET` | `/api/projects` | 查询项目列表 |
| `GET` | `/api/projects/{projectId}` | 查询项目详情 |
| `POST` | `/api/projects/{projectId}/images` | 以 multipart 形式上传一张图像 |
| `GET` | `/api/projects/{projectId}/images` | 查询项目图像列表 |
| `GET` | `/api/projects/{projectId}/images/{imageId}` | 查询图像元数据 |
| `GET` | `/api/projects/{projectId}/images/{imageId}/content` | 读取图像内容/缩略图 |
| `DELETE` | `/api/projects/{projectId}/images/{imageId}` | 删除非运行任务使用的图像 |
| `POST` | `/api/projects/{projectId}/jobs` | 创建稀疏重建任务 |
| `GET` | `/api/projects/{projectId}/jobs` | 查询项目任务列表 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}` | 查询任务详情 |
| `POST` | `/api/projects/{projectId}/jobs/{jobId}/retry` | 将失败任务重新排队 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}/log` | 查看 COLMAP 日志末尾 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}/result` | 下载成功任务的 `sparse.ply` |

创建项目的 JSON 示例：

```json
{
  "name": "青铜器试验项目",
  "description": "用于稀疏重建流程验证",
  "heritageType": "器物"
}
```

创建真实重建任务：

```json
{
  "algorithm": "COLMAP_SPARSE"
}
```

建议围绕静止文物连续拍摄 20–60 张照片，相邻照片保持约 60%–80% 画面重叠，避免反光、运动模糊和纯色背景。当前容器使用 CPU SIFT，单任务超时时间可通过 `COLMAP_TIMEOUT_SECONDS` 调整。

如需生成一组仅用于本地管线验收的确定性多视角图片：

```powershell
python tools/generate_colmap_fixture.py .tmp/colmap-fixture --images 8
```

停止服务：

```powershell
docker compose down
```

同时清除本地开发数据：

```powershell
docker compose down --volumes
```

## 独立验证

```powershell
mvn -pl services/api -am test
docker compose config --quiet
docker compose run --rm reconstruction-worker pytest -q
```
