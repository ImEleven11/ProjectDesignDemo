# ProjectDesignDemo

数字文博三维重建 Demo。当前已具备文物档案、图片管理、真实 COLMAP 异步重建、GLB/PLY 在线查看、成果版本管理、发布和独立公开展示的完整运行闭环。

项目各阶段的完成记录见 [`DEMO_PROGRESS.md`](DEMO_PROGRESS.md)。部署、演示和故障处理见 [`docs/DEMO_DELIVERY.md`](docs/DEMO_DELIVERY.md)，最终交付结果见 [`docs/STAGE10_ACCEPTANCE.md`](docs/STAGE10_ACCEPTANCE.md)。

## 模块

- `apps/web`：Vue 3、TypeScript、Three.js、Vite 和 Nginx，提供项目工作台以及 GLB/PLY 双模式查看器。
- `services/api`：Java 17、Spring Boot 3、Flyway 和 Actuator。
- `services/reconstruction-worker`：Python 3.12、FastAPI、COLMAP 3.10 和 SciPy，执行 CPU 稀疏重建与闭合表面生成，并预留 CUDA 稠密重建流程。
- `compose.yaml`：本地服务编排和健康检查。

## 技术栈

| 层级 | 技术 | 主要用途 |
| --- | --- | --- |
| Web 前端 | Vue 3、TypeScript、Vite | 项目工作台、文物档案管理、成果发布和公开数字馆藏页面 |
| 三维展示 | Three.js、WebGL、GLB/PLY | 表面模型与点云加载、旋转、平移、缩放和成果切换 |
| Web 服务 | Nginx | 前端静态资源托管、健康检查和 `/api` 同源反向代理 |
| 业务后端 | Java 17、Spring Boot 3、Spring Web、Bean Validation | 项目、图片、任务、成果版本和公开发布 API |
| 数据库 | PostgreSQL 15、Flyway | 业务数据持久化和数据库版本迁移 |
| 重建服务 | Python 3.12、FastAPI | 异步接收重建任务、执行流水线并回调任务结果 |
| 摄影测量 | COLMAP 3.10 | SIFT 特征提取与匹配、相机位姿估计、稀疏重建；预留 CUDA 稠密重建 |
| 几何处理 | SciPy、NumPy | CPU 环境下由稀疏点云生成闭合凸包表面及模型数据处理 |
| 三维成果格式 | glTF 2.0/GLB、PLY | Web 展示模型、三角网格和点云交换 |
| 容器与编排 | Docker、Docker Compose | Web、API、Worker 和 PostgreSQL 的构建、部署与健康检查 |
| 测试与构建 | JUnit、Spring MockMvc、pytest、Maven、npm | 后端与 Worker 自动测试、前端生产构建和交付验收 |

当前默认环境使用 CPU 完成稀疏重建和凸包表面生成。高清真实纹理摄影测量将使用云端 NVIDIA CUDA GPU 执行稠密重建，并补充网格优化、UV 展开、纹理烘焙和带纹理 GLB 导出流程。

## 安装部署总览

推荐使用 Docker Compose 部署。宿主机不需要单独安装 Java、Node.js、PostgreSQL、COLMAP 或项目依赖；它们都会在容器构建过程中安装。完整流程为：

1. 安装 Git、Docker 和 Docker Compose。
2. 克隆仓库并进入项目根目录。
3. 从 `.env.example` 创建 `.env`，修改口令和冲突端口。
4. 执行 `docker compose up --build --wait -d`。
5. 确认四个服务均为 `healthy`。
6. 打开管理工作台，或运行自动端到端验收脚本。

默认 Compose 配置适合本机、教学演示和受信任局域网试运行。项目目前没有登录认证和 RBAC，不能未经安全加固直接暴露到公网。

## 一、环境要求

### 1. 支持的操作系统

- Windows 10/11 64 位，推荐使用 WSL 2 后端的 Docker Desktop。
- macOS 12 及以上，Intel 或 Apple Silicon 均可使用 Docker Desktop。
- 主流 64 位 Linux 发行版，安装 Docker Engine 和 Docker Compose v2 插件。

Windows 用户需要在 BIOS/UEFI 中启用 CPU 虚拟化，并确保 Docker Desktop 使用 Linux 容器。若 `docker info` 显示无法连接引擎，请先启动 Docker Desktop。

### 2. 推荐硬件

| 资源 | 最低试运行配置 | 推荐配置 |
| --- | --- | --- |
| CPU | 4 核 64 位处理器 | 8 核及以上 |
| 内存 | 8 GB，并分配至少 6 GB 给 Docker | 16 GB 及以上 |
| 磁盘 | 10 GB 可用空间 | 20 GB 以上 SSD |
| GPU | 不要求 | `COLMAP_DENSE` 需要另行构建 CUDA Worker |
| 浏览器 | 支持 WebGL 的现代浏览器 | 最新版 Edge、Chrome 或 Firefox |

`DEMO_MESH` 和 `COLMAP_MESH` 可在普通 CPU 环境运行。默认 Worker 镜像中的 COLMAP 没有 CUDA，不能直接运行 GPU 稠密重建。

### 3. 必需软件

- Git
- Docker Engine 或 Docker Desktop
- Docker Compose v2（使用 `docker compose`，不是旧版 `docker-compose`）
- Python 3.10 以上：仅运行样例生成和自动验收脚本时需要

安装后执行以下命令检查环境：

```powershell
git --version
docker version
docker compose version
python --version
```

如果只准备手工操作网页，可以不安装 Python。

## 二、获取源码

使用 HTTPS 克隆仓库：

```powershell
git clone https://github.com/ImEleven11/ProjectDesignDemo.git
cd ProjectDesignDemo
```

已经下载 ZIP 的用户，应先完整解压，然后在包含 `compose.yaml` 的目录中打开 PowerShell、终端或命令行。后续所有命令都必须在项目根目录执行。

可用以下命令确认位置正确：

```powershell
Get-ChildItem compose.yaml, .env.example, apps, services
```

Linux/macOS 使用：

```bash
ls compose.yaml .env.example apps services
```

## 三、配置环境变量

### 1. 创建 `.env`

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

Linux/macOS：

```bash
cp .env.example .env
```

`.env` 只在本机使用，已被 `.gitignore` 忽略，不要提交到 GitHub。

### 2. 配置项说明

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `POSTGRES_DB` | `project_design_demo` | PostgreSQL 数据库名 |
| `POSTGRES_USER` | `project_design` | PostgreSQL 用户名 |
| `POSTGRES_PASSWORD` | `project_design_local` | PostgreSQL 密码，首次启动前必须修改 |
| `POSTGRES_PORT` | `5432` | 映射到宿主机的数据库端口 |
| `API_PORT` | `8080` | Spring Boot API 端口 |
| `WORKER_PORT` | `8001` | 重建 Worker 端口 |
| `WEB_PORT` | `5173` | 管理工作台和公开馆藏端口 |
| `WORKER_TOKEN` | 示例占位值 | API 与 Worker 之间的回调令牌，必须修改 |
| `COLMAP_TIMEOUT_SECONDS` | `1800` | 单次真实重建最长运行时间，单位为秒 |

可使用 Python 生成随机 Worker 令牌：

```powershell
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

将输出写入 `.env` 的 `WORKER_TOKEN`。建议同时将 `POSTGRES_PASSWORD` 改为强密码，例如：

```dotenv
POSTGRES_PASSWORD=请替换为本机强密码
WORKER_TOKEN=请替换为随机令牌
```

数据库名称、用户和密码只应在第一次创建数据卷之前确定。已有数据卷建立后再修改这些值，API 会因为无法连接旧数据库而启动失败。确需更改时，应先备份数据，再按“卸载与重新安装”章节重建数据卷。

### 3. 解决端口冲突

如果电脑已经运行 PostgreSQL、Java 服务或其他前端，可只修改宿主机端口，例如：

```dotenv
POSTGRES_PORT=15432
API_PORT=18080
WORKER_PORT=18001
WEB_PORT=15173
```

容器内部端口不变。修改后管理页面地址将变为 `http://localhost:15173`，API 健康检查地址变为 `http://localhost:18080/actuator/health/readiness`。

## 四、首次构建与启动

### 1. 检查配置

```powershell
docker compose config --quiet
```

没有输出且退出码为 0 表示 Compose 配置有效。需要查看合并后的完整配置时运行 `docker compose config`，注意不要把包含真实口令的输出公开。

### 2. 构建并启动

```powershell
docker compose up --build --wait -d
```

首次构建会下载 PostgreSQL、Maven、Java、Python、Node.js 和 Nginx 基础镜像，并在 Worker 镜像中安装 COLMAP，通常需要 5–20 分钟，具体取决于网络、CPU 和磁盘速度。构建期间不要关闭 Docker Desktop。

如果当前 Compose 版本不支持 `--wait`，使用：

```powershell
docker compose up --build -d
docker compose ps
```

反复运行 `docker compose ps`，直到四个服务均显示 `healthy`。

### 3. 正常状态

应看到以下服务：

| 服务 | 作用 | 正常状态 |
| --- | --- | --- |
| `postgres` | 保存文物、任务和成果元数据 | `healthy` |
| `api` | 提供业务 API 和数据库迁移 | `healthy` |
| `reconstruction-worker` | 执行 COLMAP 和模型导出 | `healthy` |
| `web` | 提供前端并反向代理 `/api` | `healthy` |

查看实时启动日志：

```powershell
docker compose logs -f --tail 200
```

按 `Ctrl+C` 只会退出日志查看，不会停止后台容器。

## 五、启动后验证

使用默认端口时访问：

- 管理工作台：http://localhost:5173
- 公开数字馆藏：http://localhost:5173/exhibits
- API 就绪检查：http://localhost:8080/actuator/health/readiness
- Worker 健康检查：http://localhost:8001/health

PowerShell 命令行检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://localhost:8001/health
Invoke-WebRequest http://localhost:5173/health
```

预期 API 和 Worker 返回 `UP`，Web 健康接口返回 HTTP 200。

### 自动完成一次完整验收

已安装 Python 时，可运行：

```powershell
python tools/demo_e2e.py --report .tmp/demo-report.json
```

该脚本会自动创建临时文物项目、生成并上传 8 张样例图片、执行 `DEMO_MESH`、选择成果、发布、验证公开页面、下架并重新发布，最后删除自己创建的临时项目。成功时输出 `"status": "PASSED"`。

使用全新隔离容器和全新数据卷验证整个安装过程：

```powershell
python tools/verify_fresh_install.py --report .tmp/fresh-install-report.json
```

该命令不会删除当前正式环境的数据；它会使用唯一 Compose 项目名和随机端口，验收完成后自动删除自己的容器、网络和数据卷。

## 六、第一次手工试运行

1. 打开管理工作台 `http://localhost:5173`。
2. 点击创建项目，填写项目名称和文物类型。
3. 在档案区补充藏品编号、年代、材质、尺寸、来源和简介。
4. 上传至少两张图片。快速演示可使用任意受支持图片；真实 COLMAP 重建应使用 20–60 张高重叠、多角度照片。
5. 首次试运行选择 `DEMO_MESH`，它会快速生成确定性的 GLB 表面模型和 PLY 点云。
6. 等待任务变为成功，在三维查看器中测试旋转、缩放、平移以及模型/点云切换。
7. 在成果版本区确认当前展示版本，然后点击发布。
8. 打开公开页面，确认代表图、文物档案和三维模型均可访问。

生成一套可用于真实 COLMAP 验收的确定性图片：

```powershell
python tools/generate_colmap_fixture.py .tmp/colmap-fixture --images 8
```

然后在网页中上传 `.tmp/colmap-fixture` 内的 8 张 BMP，或直接运行：

```powershell
python tools/demo_e2e.py --algorithm COLMAP_MESH --timeout 1800 --report .tmp/colmap-report.json
```

## 七、局域网试运行

部署电脑和访问电脑处于同一可信局域网时：

1. 在部署电脑运行 `ipconfig`（Windows）或 `ip addr`（Linux）查找局域网 IPv4 地址。
2. 在系统防火墙中只放行 `WEB_PORT`，默认是 TCP `5173`。
3. 其他电脑访问 `http://部署电脑IP:5173`。
4. 公开馆藏地址为 `http://部署电脑IP:5173/exhibits`。

浏览器通过 Nginx 的 `/api` 同源代理访问后端，因此通常不需要向其他电脑开放 `API_PORT`、`WORKER_PORT` 或 `POSTGRES_PORT`。默认 Compose 为调试方便映射了这些端口；若计划长期在服务器运行，应删除或限制内部服务的端口映射，并在 Web 前增加 HTTPS 反向代理、身份认证、访问控制、限流和备份。

当前版本没有用户登录和权限隔离。不要将默认配置直接部署到公网，也不要使用真实敏感馆藏数据进行无保护测试。

## 八、日常启动、停止与日志

启动已有环境：

```powershell
docker compose up -d --wait
```

查看状态：

```powershell
docker compose ps
```

查看全部日志或指定服务日志：

```powershell
docker compose logs --tail 200
docker compose logs -f api
docker compose logs -f reconstruction-worker
```

重启单个服务：

```powershell
docker compose restart api
```

停止并删除容器，但保留数据库、图片和重建成果：

```powershell
docker compose down
```

Compose 中配置了 `restart: unless-stopped`。Docker 服务或电脑重启后，容器通常会自动恢复；仍应使用 `docker compose ps` 确认健康状态。

## 九、数据位置、备份与恢复

业务数据保存在 Docker 命名卷中，不在 Git 仓库里：

| 命名卷 | 内容 |
| --- | --- |
| `project-design-demo_postgres-data` | PostgreSQL 业务数据 |
| `project-design-demo_api-upload-data` | 上传的原始图片 |
| `project-design-demo_reconstruction-data` | 日志、PLY、GLB 和重建中间结果 |

删除项目文件夹不会自动删除这些卷；运行 `docker compose down --volumes` 会永久删除它们。

### 1. 备份数据库

为了让数据库和文件成果处于一致时间点，先停止会写入数据的服务。PostgreSQL 保持运行以完成导出。以下命令使用 `.env.example` 的默认数据库名和用户名；修改过配置时请替换参数：

```powershell
New-Item -ItemType Directory -Force backup
docker compose stop web reconstruction-worker api
docker compose exec -T postgres pg_dump -U project_design -d project_design_demo -Fc -f /tmp/project_design_demo.dump
docker compose cp postgres:/tmp/project_design_demo.dump backup/database.dump
docker compose exec -T postgres rm /tmp/project_design_demo.dump
```

Linux/macOS 将第一行替换为 `mkdir -p backup`，其余数据库导出命令相同。

### 2. 备份上传图片和重建成果

Windows PowerShell：

```powershell
docker run --rm -v project-design-demo_api-upload-data:/source:ro -v "$($PWD.Path)\backup:/backup" alpine sh -c "tar -czf /backup/uploads.tar.gz -C /source ."
docker run --rm -v project-design-demo_reconstruction-data:/source:ro -v "$($PWD.Path)\backup:/backup" alpine sh -c "tar -czf /backup/reconstruction.tar.gz -C /source ."
```

Linux/macOS：

```bash
mkdir -p backup
docker run --rm -v project-design-demo_api-upload-data:/source:ro -v "$PWD/backup:/backup" alpine sh -c "tar -czf /backup/uploads.tar.gz -C /source ."
docker run --rm -v project-design-demo_reconstruction-data:/source:ro -v "$PWD/backup:/backup" alpine sh -c "tar -czf /backup/reconstruction.tar.gz -C /source ."
```

备份完成后恢复服务：

```powershell
docker compose up -d --wait
```

### 3. 恢复

先在同版本项目中启动空环境，然后恢复文件卷，最后导入数据库。恢复前应停止 API 和 Worker，避免写入：

```powershell
docker compose stop web reconstruction-worker api
docker run --rm -v project-design-demo_api-upload-data:/target -v "$($PWD.Path)\backup:/backup:ro" alpine sh -c "tar -xzf /backup/uploads.tar.gz -C /target"
docker run --rm -v project-design-demo_reconstruction-data:/target -v "$($PWD.Path)\backup:/backup:ro" alpine sh -c "tar -xzf /backup/reconstruction.tar.gz -C /target"
docker compose cp backup/database.dump postgres:/tmp/project_design_demo.dump
docker compose exec -T postgres pg_restore -U project_design -d project_design_demo --clean --if-exists --no-owner /tmp/project_design_demo.dump
docker compose exec -T postgres rm /tmp/project_design_demo.dump
docker compose up -d --wait
```

正式数据恢复前建议先在隔离环境演练，并确认数据库备份与两个文件归档来自同一时间点。

Linux/macOS 恢复文件卷时使用：

```bash
docker compose stop web reconstruction-worker api
docker run --rm -v project-design-demo_api-upload-data:/target -v "$PWD/backup:/backup:ro" alpine sh -c "tar -xzf /backup/uploads.tar.gz -C /target"
docker run --rm -v project-design-demo_reconstruction-data:/target -v "$PWD/backup:/backup:ro" alpine sh -c "tar -xzf /backup/reconstruction.tar.gz -C /target"
docker compose cp backup/database.dump postgres:/tmp/project_design_demo.dump
docker compose exec -T postgres pg_restore -U project_design -d project_design_demo --clean --if-exists --no-owner /tmp/project_design_demo.dump
docker compose exec -T postgres rm /tmp/project_design_demo.dump
docker compose up -d --wait
```

## 十、更新项目版本

更新前先备份数据，然后执行：

```powershell
git status
git pull --ff-only
docker compose up --build --wait -d
docker compose ps
```

API 启动时 Flyway 会自动执行尚未应用的数据库迁移。不要手工修改已经执行过的 `V1`–`V5` 迁移文件。若本地有未提交修改，先处理 `git status` 中的内容，不要直接覆盖。

更新后建议运行：

```powershell
python tools/demo_e2e.py --report .tmp/after-upgrade-report.json
```

## 十一、卸载与重新安装

### 保留数据，仅删除容器

```powershell
docker compose down
```

### 永久删除项目数据

```powershell
docker compose down --volumes --remove-orphans
```

第二条命令会永久删除数据库、上传图片和全部重建成果，无法通过 Git 恢复。执行前必须确认已经备份，且当前目录确实是本项目根目录。

如需同时清理本项目构建镜像，可在确认镜像名称后执行：

```powershell
docker images --filter "reference=project-design-demo*"
docker image rm project-design-demo-api project-design-demo-reconstruction-worker project-design-demo-web
```

镜像删除不会删除命名卷。若镜像仍被容器使用，应先运行 `docker compose down`。

## 十二、常见故障排查

### Docker 命令无法连接引擎

- 确认 Docker Desktop 已启动并显示 Engine running。
- Windows 确认使用 Linux containers 和 WSL 2 后端。
- Linux 执行 `docker info`，确认当前用户具有 Docker 权限。

### 构建阶段下载失败

- 检查是否可以访问 Docker Hub、Maven Central、npm 和 Debian 软件源。
- 重新运行 `docker compose build --pull`；Docker 会复用已经成功的层。
- 若公司或校园网络使用代理，应先为 Docker Desktop 配置代理，而不只是为浏览器配置。

### 端口已被占用

修改 `.env` 中的宿主机端口。Windows 可用以下命令查询占用者：

```powershell
Get-NetTCPConnection -LocalPort 5432,8080,8001,5173 -ErrorAction SilentlyContinue
```

### 服务处于 `unhealthy`

```powershell
docker compose ps
docker compose logs --tail 200 postgres
docker compose logs --tail 200 api
docker compose logs --tail 200 reconstruction-worker
docker compose logs --tail 200 web
```

按依赖顺序排查：PostgreSQL → API → Worker → Web。下游服务会等待上游健康。

### 修改数据库密码后 API 无法启动

已有 PostgreSQL 卷仍保留旧用户和旧密码。恢复原 `.env`，或先备份后执行 `docker compose down --volumes` 重建空数据库。

### 图片上传返回 413 或失败

Nginx 单文件上传限制为 25 MB。压缩单张图片，或在 `apps/web/nginx.conf` 调整 `client_max_body_size` 后重新构建 Web 镜像。

### 重建任务失败或没有足够三维点

- 查看网页中的任务日志，或运行 `docker compose logs reconstruction-worker`。
- 使用 20–60 张清晰照片，相邻视角保持约 60%–80% 重叠。
- 避免透明、强反光、重复纹理、运动模糊和纯色背景。
- 先使用 `DEMO_MESH` 验证系统，再使用确定性样例验证 COLMAP 环境。
- CPU 重建期间避免让 Docker Desktop 休眠或资源不足。

### `COLMAP_DENSE` 报 CUDA 不可用

这是默认镜像的预期限制。使用 `COLMAP_MESH`，或另行构建支持 NVIDIA Container Toolkit 和 CUDA COLMAP 的 Worker 镜像。

### 页面可以打开但看不到三维模型

- 使用支持 WebGL 的最新浏览器，并确认没有禁用硬件加速。
- 检查浏览器开发者工具 Network 和 Console。
- 确认模型接口返回 HTTP 200 与 `model/gltf-binary`。
- 远程访问时只使用 Web 地址，由 Nginx 同源代理 `/api`。

Web 工作台支持创建项目、批量上传图片、启动 COLMAP、轮询任务状态、下载 GLB/PLY，并通过鼠标旋转、平移和缩放切换查看表面模型与稀疏点云。每次成功重建都会形成可追溯成果版本；项目选择当前展示成果后即可发布到独立数字馆藏。

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

## 开发者测试

以下命令分别需要本机 Maven、Node.js/npm 和 Docker。普通试运行用户只需执行前文的 Docker Compose 部署与自动验收，无需安装这些开发工具。

```powershell
mvn -pl services/api -am test
npm --prefix apps/web run build
docker compose config --quiet
docker compose run --rm reconstruction-worker pytest -q
```
