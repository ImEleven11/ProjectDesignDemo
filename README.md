# ProjectDesignDemo

数字文博稀疏三维重建 Demo。当前已具备从项目与图像管理、异步任务执行，到 PLY 稀疏点云产物下载的完整运行闭环。

## 模块

- `apps/web`：Vue 3、TypeScript、Vite 和 Nginx。
- `services/api`：Java 17、Spring Boot 3、Flyway 和 Actuator。
- `services/reconstruction-worker`：Python 3.12、FastAPI，当前提供确定性 Demo 稀疏点云引擎，并预留真实 COLMAP 执行器边界。
- `compose.yaml`：本地服务编排和健康检查。

## 本地启动

1. 可选：复制 `.env.example` 为 `.env` 并调整端口或数据库口令。
2. 运行 `docker compose up --build -d`。
3. 运行 `docker compose ps`，等待所有服务显示为 `healthy`。

默认入口：

- Web：http://localhost:5173
- API 健康检查：http://localhost:8080/actuator/health/readiness
- 重建工作进程健康检查：http://localhost:8001/health

## 业务 API

当前最小业务链路为：创建文博项目 → 上传至少两张图像 → 创建 `COLMAP_SPARSE` 稀疏重建任务 → Worker 异步生成 Demo 稀疏点云 → 查询任务状态并下载 PLY。

> 当前产物由 `DEMO_SPARSE_V1` 确定性引擎生成，用于验证系统闭环，不代表基于上传照片完成了真实摄影测量。后续步骤将把执行器替换为 COLMAP。

| 方法 | 接口 | 用途 |
| --- | --- | --- |
| `POST` | `/api/projects` | 创建文博项目 |
| `GET` | `/api/projects` | 查询项目列表 |
| `GET` | `/api/projects/{projectId}` | 查询项目详情 |
| `POST` | `/api/projects/{projectId}/images` | 以 multipart 形式上传一张图像 |
| `GET` | `/api/projects/{projectId}/images` | 查询项目图像列表 |
| `GET` | `/api/projects/{projectId}/images/{imageId}` | 查询图像元数据 |
| `POST` | `/api/projects/{projectId}/jobs` | 创建稀疏重建任务 |
| `GET` | `/api/projects/{projectId}/jobs` | 查询项目任务列表 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}` | 查询任务详情 |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}/result` | 下载成功任务的 `sparse.ply` |

创建项目的 JSON 示例：

```json
{
  "name": "青铜器试验项目",
  "description": "用于稀疏重建流程验证",
  "heritageType": "器物"
}
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
