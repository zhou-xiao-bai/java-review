# Java Review

Java Review 是一个面向高级 Java 后端面试复习的个人工作台。它把复习范围管理、每日计划、严格问答、项目深挖和进度追踪放在同一个系统里，适合用来持续准备 Java、JVM、并发、Spring、MySQL、Redis、消息队列和分布式相关面试。

## 功能

- 首次启动创建管理员账号，后续使用登录态访问。
- 管理复习范围，按领域选择内置主题并初始化复习点。
- 生成今日复习计划，支持顺延、加练、跳过和移除任务。
- 进行沉浸式复习会话，由 OpenAI-compatible LLM 生成追问、评价和薄弱点。
- 维护项目案例，并进行项目面试深挖。
- 查看复习进度、领域掌握度、薄弱点排行、待复验点和近期会话。
- 在设置页维护多个 LLM 端点配置，API Key 只保存在后端数据库中。

## 技术栈

- Frontend: React 19, TypeScript, Vite, Tailwind CSS, React Router, TanStack Query
- Backend: Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway
- Database: PostgreSQL 16
- Deployment: Docker Compose, Nginx, multi-stage Docker build

## 一键部署

生产 Compose 会启动三个服务：PostgreSQL、Spring Boot 后端、Nginx 前端。

```bash
cp .env.example .env
```

修改 `.env` 中的密钥，不要使用示例值上线：

```dotenv
APP_PORT=8088

POSTGRES_DB=java_review
POSTGRES_USER=java_review
POSTGRES_PASSWORD=change-me-long-random-password

REMEMBER_ME_KEY=change-me-64-plus-random-characters
REMEMBER_ME_SECONDS=1209600
```

启动：

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

访问：

```text
http://localhost:8088
```

健康检查：

```bash
curl http://localhost:8088/api/health
```

停止服务：

```bash
docker compose -f docker-compose.prod.yml down
```

如果需要同时删除数据库卷：

```bash
docker compose -f docker-compose.prod.yml down -v
```

## 本地开发

启动开发数据库：

```bash
docker compose up -d
```

启动后端：

```bash
cd backend
./gradlew bootRun
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

本地开发地址：

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`。

## 常用命令

后端测试：

```bash
cd backend
./gradlew test
```

前端构建：

```bash
cd frontend
npm run build
```

前端 lint：

```bash
cd frontend
npm run lint
```

生产镜像构建验证：

```bash
docker compose -f docker-compose.prod.yml build
```

## 项目结构

```text
.
├── backend/                  # Spring Boot API
│   ├── src/main/java/         # 业务代码
│   ├── src/main/resources/    # 应用配置和 Flyway migration
│   └── Dockerfile
├── frontend/                 # React SPA
│   ├── src/
│   ├── nginx.conf
│   └── Dockerfile
├── docker-compose.yml        # 本地开发 PostgreSQL
├── docker-compose.prod.yml   # 生产一键部署
└── .env.example              # 环境变量示例
```

## 生产注意事项

- 公开部署前必须修改 `POSTGRES_PASSWORD` 和 `REMEMBER_ME_KEY`。
- 建议在公网环境前置 HTTPS 反向代理，并限制数据库端口只在容器网络内访问。
- LLM API Key 通过应用设置页配置，后端持久化保存，不会写入前端构建产物。
- PostgreSQL 数据保存在 Docker volume 中，正式使用时需要配置备份策略。
