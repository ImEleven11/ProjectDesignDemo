# ProjectDesignDemo

数字文博三维重建 Demo。当前已具备文物档案、图片管理、真实 COLMAP 异步重建、GLB/PLY 在线查看、成果版本管理、发布和独立公开展示的完整运行闭环。

项目各阶段的完成记录、验收结果和后续步骤见 [`DEMO_PROGRESS.md`](DEMO_PROGRESS.md)。

## 模块

- `apps/web`：Vue 3、TypeScript、Three.js、Vite 和 Nginx，提供项目工作台以及 GLB/PLY 双模式查看器。
- `services/api`：Java 17、Spring Boot 3、Flyway 和 Actuator。
- `services/reconstruction-worker`：Python 3.12、FastAPI、COLMAP 3.10 和 SciPy，执行 CPU 稀疏重建与闭合表面生成，并预留 CUDA 稠密重建流程。
- `compose.yaml`：本地服务编排和健康检查。

## 本地启动

1. 可选：复制 `.env.example` 为 `.env` 并调整端口或数据库口令。
2. 运行 `docker compose up --build -d`。
3. 运行 `docker compose ps`，等待所有服务显示为 `healthy`。

默认入口：

- Web：http://localhost:5173
- 公开数字馆藏：http://localhost:5173/exhibits
- API 健康检查：http://localhost:8080/actuator/health/readiness
- 重建工作进程健康检查：http://localhost:8001/health

Web 工作台支持创建项目、批量上传图片、启动 COLMAP、轮询任务状态、下载 GLB/PLY，并通过鼠标旋转、平移和缩放切换查看表面模型与稀疏点云。Nginx 将 `/api` 同源代理到 Spring Boot 服务。

每个项目可记录藏品编号、年代、材质、尺寸、来源与简介。每次成功重建都会自动形成可追溯成果版本，记录格式、点数、面数、文件大小和生成时间；用户可重命名、删除成果并选择当前展示版本。

项目默认处于草稿状态。选择当前展示成果后，可从管理工作台发布或取消发布；访客只能在独立数字馆藏页面查看已发布文物的代表图、档案信息和当前三维模型。

## 业务 API

当前最小业务链路为：创建文博项目 → 上传至少三张图像 → 创建 `COLMAP_MESH` 表面重建任务 → Worker 异步执行特征提取、穷举匹配和增量建图 → 使用 CPU 生成闭合三角表面 → 查询任务状态并查看或下载 GLB/PLY。

- `COLMAP_MESH`：适用于普通 CPU 环境，保留 COLMAP 彩色稀疏点云，并以其空间外壳生成可交互 GLB 表面模型。
- `COLMAP_SPARSE`：只生成真实 PLY 稀疏点云。
- `COLMAP_DENSE`：运行 COLMAP 官方 CUDA 稠密重建、融合和 Poisson 网格流程，需要支持 CUDA 的 Worker 镜像与 GPU。
- `DEMO_MESH` / `DEMO_SPARSE`：不依赖照片内容的快速系统联调模式，最低需要两张图片。

| 方法 | 接口 | 用途 |
| --- | --- | --- |
| `POST` | `/api/projects` | 创建文博项目 |
| `GET` | `/api/projects` | 查询项目列表 |
| `GET` | `/api/projects/{projectId}` | 查询项目详情 |
| `PATCH` | `/api/projects/{projectId}` | 编辑文物档案与项目名称 |
| `DELETE` | `/api/projects/{projectId}` | 删除非运行中的项目及其文件 |
| `GET` | `/api/projects/{projectId}/artifacts` | 查询模型成果版本及技术元数据 |
| `PATCH` | `/api/projects/{projectId}/artifacts/{artifactId}` | 重命名成果版本 |
| `POST` | `/api/projects/{projectId}/artifacts/{artifactId}/select` | 设置当前展示版本 |
| `DELETE` | `/api/projects/{projectId}/artifacts/{artifactId}` | 删除成果版本及对应文件 |
| `POST` | `/api/projects/{projectId}/publish` | 发布具有当前展示成果的文物 |
| `POST` | `/api/projects/{projectId}/unpublish` | 取消发布文物 |
| `POST` | `/api/projects/{projectId}/images` | 以 multipart 形式上传一张图像 |
| `GET` | `/api/projects/{projectId}/images` | 查询项目图像列表 |
| `GET` | `/api/projects/{projectId}/images/{imageId}` | 查询图像元数据 |
| `GET` | `/api/projects/{projectId}/images/{imageId}/content` | 读取图像内容/缩略图 |
| `DELETE` | `/api/projects/{projectId}/images/{imageId}` | 删除非运行任务使用的图像 |
| `POST` | `/api/projects/{projectId}/jobs` | 创建点云或表面重建任务 |
| `GET` | `/api/projects/{projectId}/jobs` | 查询项目任务列表 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}` | 查询任务详情 |
| `POST` | `/api/projects/{projectId}/jobs/{jobId}/retry` | 将失败任务重新排队 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}/log` | 查看 COLMAP 日志末尾 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}/result` | 下载成功任务的 GLB 模型或 PLY 点云 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}/point-cloud` | 下载表面模型任务配套的稀疏 PLY 点云 |
| `GET` | `/api/public/heritage` | 查询已发布文物列表 |
| `GET` | `/api/public/heritage/{projectId}` | 查询已发布文物详情和当前成果 |
| `GET` | `/api/public/heritage/{projectId}/cover` | 读取公开代表图片 |
| `GET` | `/api/public/heritage/{projectId}/model` | 读取公开 GLB 模型 |

创建项目的 JSON 示例：

```json
{
  "name": "青铜器试验项目",
  "description": "用于稀疏重建流程验证",
  "heritageType": "器物",
  "collectionCode": "BW-2026-001",
  "era": "商代晚期",
  "material": "青铜",
  "dimensions": "高 32 cm，口径 18 cm",
  "provenance": "教学数字文博示范藏品"
}
```

创建真实重建任务：

```json
{
  "algorithm": "COLMAP_MESH"
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
