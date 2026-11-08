# ProjectDesignDemo 交付与演示指南

## 1. 交付范围

本 Demo 提供从文物图片、三维重建、成果版本管理到公开数字展示的完整最小闭环。默认使用 `DEMO_MESH` 进行快速、确定性的系统验收；需要验证真实摄影测量链路时，可切换为 `COLMAP_MESH`。

## 2. 环境要求

- Windows 10/11、macOS 或 Linux
- Docker Desktop，或 Docker Engine 与 Docker Compose v2
- 可用于 Docker 的 8 GB 以上内存和约 10 GB 可用磁盘空间
- Git
- Python 3.10 以上，仅自动验收和样例图片生成脚本需要
- 首次构建时能够访问 Docker Hub 和依赖仓库

默认端口为 PostgreSQL `5432`、API `8080`、Worker `8001`、Web `5173`。端口冲突时，在 `.env` 中修改对应值。

## 3. 一键启动

```powershell
Copy-Item .env.example .env
docker compose up --build --wait -d
docker compose ps
```

所有服务显示 `healthy` 后访问：

- 管理工作台：http://localhost:5173
- 公开数字馆藏：http://localhost:5173/exhibits
- API 就绪检查：http://localhost:8080/actuator/health/readiness

建议在 `.env` 中将 `WORKER_TOKEN` 改为本地随机值。`.env` 已被 Git 忽略。

## 4. 自动化交付验收

### 4.1 验收当前运行环境

```powershell
python tools/demo_e2e.py --report .tmp/demo-report.json
```

脚本自动完成以下操作：

1. 检查 API 和 Web 是否就绪。
2. 创建一份完整的文物档案，并验证草稿不可公开访问。
3. 生成并上传 8 张确定性多视角 BMP 图片。
4. 创建 `DEMO_MESH` 任务并等待三维成果。
5. 校验成果版本，选择当前展示成果并发布。
6. 校验公开列表、详情、代表图、GLB 文件签名和 MIME 类型。
7. 验证取消发布后返回 404，并重新发布。
8. 删除临时验收项目及其图片和成果文件。

若要保留验收项目用于演示，增加 `--keep-project`。

### 4.2 验证真实 COLMAP 重建

```powershell
python tools/demo_e2e.py --algorithm COLMAP_MESH --timeout 1800 --report .tmp/colmap-report.json
```

此命令使用相同的确定性图片运行真实特征提取、匹配和增量建图，再生成 CPU 闭合表面与 GLB。运行时间取决于 CPU 和 Docker 资源。

### 4.3 验证全新安装

```powershell
python tools/verify_fresh_install.py --report .tmp/fresh-install-report.json
```

该脚本使用唯一 Compose 项目名、随机空闲端口和全新命名卷执行 `docker compose up --build --wait -d`，运行完整业务验收后删除临时容器、网络和专用数据卷。现有 `project-design-demo` 环境和数据不会被修改。

调试时可增加 `--keep-environment` 保留隔离环境。此时应根据脚本输出的 Compose 项目名手动清理：

```powershell
docker compose --project-name <项目名> down --volumes --remove-orphans
```

## 5. 人工演示流程

1. 打开管理工作台并创建文物项目。
2. 填写藏品编号、年代、材质、尺寸、来源和简介。
3. 上传至少两张图片；真实 COLMAP 重建建议使用 20–60 张高重叠照片。
4. 快速演示选择 `DEMO_MESH`，真实重建选择 `COLMAP_MESH`。
5. 等待任务成功，在三维查看器中切换表面模型和点云。
6. 在成果版本区确认或切换当前展示版本。
7. 点击“发布”，再通过“查看公开页”进入独立数字馆藏。
8. 在公开页检查代表图、文物档案和交互式三维模型。

确定性样例图片也可单独生成：

```powershell
python tools/generate_colmap_fixture.py .tmp/colmap-fixture --images 8
```

## 6. 常见故障处理

| 现象 | 检查与处理 |
| --- | --- |
| 无法连接 Docker 管道 | 启动 Docker Desktop，确认当前用户有权限运行 `docker info`。 |
| 端口已被占用 | 在 `.env` 修改 `POSTGRES_PORT`、`API_PORT`、`WORKER_PORT` 或 `WEB_PORT`。 |
| 服务长期处于 `starting`/`unhealthy` | 运行 `docker compose ps` 和 `docker compose logs --tail 200 <服务名>`。优先检查 PostgreSQL、API、Worker 的依赖顺序。 |
| API 启动失败 | 检查数据库口令是否与已存在的数据卷一致；开发环境可在确认无需保留数据后重建卷。 |
| Worker 回调失败 | 确认 API 与 Worker 使用相同的 `WORKER_TOKEN`，并查看 `docker compose logs reconstruction-worker`。 |
| COLMAP 未生成模型 | 增加照片数量和相邻画面重叠，避免反光、模糊、纯色背景及运动物体。可先用确定性样例排除环境问题。 |
| `COLMAP_DENSE` 提示 CUDA 不可用 | 默认镜像中的 COLMAP 为 CPU 构建；使用 `COLMAP_MESH`，或自行构建带 CUDA 的 Worker 镜像。 |
| 浏览器无法显示模型 | 检查公开模型接口是否返回 `model/gltf-binary`，并确认浏览器启用了 WebGL。 |

## 7. 停止与清理

停止服务但保留数据：

```powershell
docker compose down
```

删除全部本地 Demo 数据：

```powershell
docker compose down --volumes
```

第二条命令会永久删除数据库、上传图片和重建成果，仅应在明确不需要数据时执行。自动验收脚本默认只删除自己创建的临时项目；全新安装脚本只删除其唯一隔离环境。
