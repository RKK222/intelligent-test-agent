# 后端 Docker 部署说明

## 部署边界

生产和研发测试环境只将 `test-agent-app` 后端 Java 进程放入 Docker 容器。PostgreSQL、Redis 和 opencode server 都是外部服务，通过环境变量或配置中心注入地址和凭据；后端镜像不包含也不启动这些依赖。

`deploy/local/docker-compose.yml` 只作为个人离线开发备用入口，不能作为研发测试或生产部署拓扑。

## 构建镜像

```bash
docker build -f backend/Dockerfile -t test-agent-backend:local backend
```

该镜像使用 multi-stage build 构建 `test-agent-app` executable jar，最终运行层只包含 JRE、应用 jar 和非 root 用户。

## 本地脚本启动

本地开发和测试优先使用仓库根目录的未跟踪 dotenv 文件启动后端：

```bash
tools/dev-backend-run.sh
tools/dev-backend-run.sh --profile test
```

脚本默认读取 `.env.local`，`--profile test` 读取 `.env.test`，也可以通过 `--env-file <path>` 覆盖。脚本只解析 `KEY=VALUE` 行，不执行 dotenv 文件内容；生产容器仍通过外部环境变量或配置中心注入配置。

## 生产必填环境变量

```bash
SPRING_PROFILES_ACTIVE=prod
TEST_AGENT_DB_URL=jdbc:postgresql://<pg-host>:5432/<database>
TEST_AGENT_DB_USERNAME=<username>
TEST_AGENT_DB_PASSWORD=<password>
TEST_AGENT_API_TOKEN=<api-token>
TEST_AGENT_CORS_ALLOWED_ORIGINS=https://<frontend-origin>
TEST_AGENT_OPENCODE_BASE_URL=http://<opencode-host>:4096
```

可选运行参数：

```bash
TEST_AGENT_OPENCODE_NODE_ID=node_prod_opencode
TEST_AGENT_OPENCODE_MAX_RUNS=4
TEST_AGENT_OPENCODE_WEIGHT=100
TEST_AGENT_DB_POOL_INITIAL_SIZE=1
TEST_AGENT_DB_POOL_MIN_IDLE=1
TEST_AGENT_DB_POOL_MAX_ACTIVE=10
TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
TEST_AGENT_REDIS_ENABLED=false
```

Redis 只有在启用时才需要提供外部地址：

```bash
TEST_AGENT_REDIS_ENABLED=true
TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
```

## 运行示例

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e TEST_AGENT_DB_URL=jdbc:postgresql://pg.example.internal:5432/test_agent \
  -e TEST_AGENT_DB_USERNAME=test_agent \
  -e TEST_AGENT_DB_PASSWORD=change-me \
  -e TEST_AGENT_API_TOKEN=change-me \
  -e TEST_AGENT_CORS_ALLOWED_ORIGINS=https://agent.example.com \
  -e TEST_AGENT_OPENCODE_BASE_URL=http://opencode.example.internal:4096 \
  test-agent-backend:local
```

启动后检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

`DatabaseMigrationRunner` 会在启动时执行 Flyway migration；`ExecutionNodeSeeder` 会把配置中的 opencode node 写入 `execution_nodes` 作为 Run 路由来源。
