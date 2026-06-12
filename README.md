# CodeAgent

CodeAgent 是面向软件研发场景的多 Agent 缺陷定位与修复辅助系统。当前仓库按实施方案搭建了 Java 21 + Spring Boot 3 多模块后端、Vue 3 + TypeScript 前端，以及本地 MySQL/Redis/MinIO/Milvus/Jenkins/SonarQube 基础设施。

## 目录

```text
code-agent-platform   Maven 多模块后端
code-agent-web        Vue 3 前端控制台
infra                 本地中间件 Docker Compose
```

## 本地启动

1. 启动中间件：

```bash
docker compose -f infra/docker-compose.yml up -d
```

Jenkins 本地地址是 `http://localhost:8081`。首次启动后用下面命令查看初始管理员密码：

```bash
docker exec code-agent-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

SonarQube 本地地址是 `http://localhost:9002`，默认账号密码通常是 `admin/admin`，首次登录后需要修改密码并生成 Token。Linux 宿主机如果 SonarQube 启动失败，需要先设置：

```bash
sudo sysctl -w vm.max_map_count=524288
sudo sysctl -w fs.file-max=131072
```

MinIO API 本地地址是 `http://localhost:9100`，Console 地址是 `http://localhost:9101`，默认本地账号密码是 `minioadmin/minioadmin`。

2. 配置环境变量：

```bash
cp .env.example .env
```

把 `.env` 中的 `DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`、`GITLAB_TOKEN`、`JENKINS_USERNAME`、`JENKINS_TOKEN`、`SONARQUBE_TOKEN` 按真实平台填写。代码不会内置 Token，也不会生成 mock 平台数据。

3. 启动后端：

```bash
cd code-agent-platform
mvn -pl code-agent-api -am spring-boot:run
```

4. 启动前端：

```bash
cd code-agent-web
npm install
npm run dev
```

前端默认访问 `http://127.0.0.1:5173`，后端健康检查为 `http://localhost:8080/api/health`。

## 已实现范围

- Maven 多模块后端工程与基础 API。
- Agent 任务状态机、Planner、并发工具执行、Critique、FinalReport 调用链。
- GitLab、Jenkins、SonarQube MCP 工具的固定注册、参数校验、真实 HTTP 调用、审计记录。
- MinIO RawOutputStore，所有工具原始响应要求写入 MinIO。
- MySQL Flyway 建表脚本和 JPA Repository。
- Redis Working Memory、Core/Episodic Memory 基础接口。
- DeepSeek LLM Client 和千问/DashScope Embedding Client 抽象。
- Vue 控制台页面：Dashboard、任务创建/详情、Evidence、RAG、Memory、MCP Tools、Tool Audit、Integration Config。

## 当前约束

- 未配置真实平台凭据时，工具调用会失败并记录明确错误，不会用假数据兜底。
- RAG 的 Milvus SDK 写入/检索适配保留在 Phase 7 实现；当前 MVP 保留配置、Docker Compose 和 VectorStore 接口。
- 最终报告必须由 Evidence Pack 触发。证据不足或 LLM Key 缺失时，任务会失败并说明原因。
