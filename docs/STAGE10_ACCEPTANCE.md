# 阶段十交付验收报告

验收日期：2026-11-08  
结论：通过

## 验收环境

- Docker Compose v5.5.0
- Python 3.13.9
- Java 17 / Spring Boot 3
- Node.js 22 构建镜像
- PostgreSQL 15
- COLMAP 3.10 CPU 构建

## 验收结果

### 隔离全新环境

- 使用唯一 Compose 项目 `pddelivery1794130394`
- 使用随机端口和三个全新命名卷
- PostgreSQL、API、Worker、Web 全部达到 `healthy`
- 8 张确定性样例图片上传成功
- `DEMO_MESH` 生成 640 个点、1656 个三角面、46,220 字节 GLB
- 创建、重建、成果选择、发布、公开访问、取消发布和重新发布全部通过
- 公开代表图返回 `image/bmp`，模型返回 `model/gltf-binary` 且签名为 `glTF`
- 临时项目、容器、网络和三个专用数据卷均已清理

### 真实 COLMAP 链路

- 使用相同的 8 张确定性样例图片
- `COLMAP_MESH` 生成 2666 个三维点、196 个三角面、78,192 字节 GLB
- 从上传到公开发布的 9 项自动检查全部通过
- 验收项目及对应图片、任务和成果文件已自动删除

### 回归检查

- API 测试：12 项通过
- Worker 测试：5 项通过
- Vue/TypeScript 生产构建：通过
- Python 交付脚本语法检查：通过
- Docker Compose 配置与四服务健康检查：通过

## 自动检查项目

1. 草稿项目不公开。
2. 确定性样例图片数量正确。
3. 重建任务成功。
4. 当前成果版本可选择。
5. 发布项目进入公开列表。
6. 代表图片可读取且格式正确。
7. GLB 模型可读取且格式正确。
8. 独立公开页面可访问。
9. 下架后隐藏，并可重新发布。

验收脚本：`tools/demo_e2e.py`  
全新环境脚本：`tools/verify_fresh_install.py`
