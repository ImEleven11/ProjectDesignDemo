# ProjectDesignDemo

数字文博稀疏三维重建 Demo。当前完成第一步基础设施：Web、API、重建工作进程和 PostgreSQL 的多模块骨架，以及统一的 Docker Compose 启动与健康检查。

## 模块

- `apps/web`：Vue 3、TypeScript、Vite 和 Nginx。
- `services/api`：Java 17、Spring Boot 3、Flyway 和 Actuator。
- `services/reconstruction-worker`：Python 3.12、FastAPI，后续封装 COLMAP。
- `compose.yaml`：本地服务编排和健康检查。

## 本地启动

1. 可选：复制 `.env.example` 为 `.env` 并调整端口或数据库口令。
2. 运行 `docker compose up --build -d`。
3. 运行 `docker compose ps`，等待所有服务显示为 `healthy`。

默认入口：

- Web：http://localhost:5173
- API 健康检查：http://localhost:8080/actuator/health/readiness
- 重建工作进程健康检查：http://localhost:8001/health

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
```
