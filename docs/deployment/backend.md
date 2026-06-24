# 后端 Docker 部署说明

## 部署边界

生产和研发测试环境只将 `test-agent-app` 后端 Java 进程放入 Docker 容器。PostgreSQL、Redis 和 opencode server 都是外部服务，通过环境变量或配置中心注入地址和凭据；后端镜像不包含也不启动这些依赖。

研发测试环境的 PG/PostgreSQL 数据库由远端环境启动和维护，不在后端容器或本仓库 Docker Compose 中启动；后端只通过 `TEST_AGENT_TEST_DB_*` 或生产 `TEST_AGENT_DB_*` 配置连接该远端数据库。

`deploy/local/docker-compose.yml` 只作为个人离线开发备用入口，不能作为研发测试或生产部署拓扑。

## opencode-manager 容器进程管理

用户专属 opencode server 进程由每个 opencode 容器内的 `opencode-manager` 管理。`opencode-manager` 是与 `backend/` 平级的 Go 单二进制工程，不打包进后端 Java 镜像；本批只提供容器内 CLI 和本地状态文件，后端 socket 控制面在后续批次接入。

容器内必须挂载以下目录：

```text
/data/opencode/session              # 用户进程 XDG_DATA_HOME 根目录，按端口分目录
/data/opencode/.config/opencode/    # 公共 agent、插件、skill 等配置
/data/opencode/manager              # manager 本地 state 和日志
```

容器环境变量示例：

```dotenv
OPENCODE_MANAGER_CONTAINER_ID=ctr_01
OPENCODE_MANAGER_LINUX_SERVER_ID=10.8.0.12
OPENCODE_MANAGER_PORT_START=4096
OPENCODE_MANAGER_PORT_END=4100
OPENCODE_MANAGER_MAX_PROCESSES=5
OPENCODE_BIN=opencode
OPENCODE_MANAGER_STATE_DIR=/data/opencode/manager
OPENCODE_SESSION_ROOT=/data/opencode/session
OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/
```

启动单个用户进程时，manager 会执行：

```bash
XDG_DATA_HOME=/data/opencode/session/{port} \
OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ \
opencode serve --hostname 0.0.0.0 --port {port} --print-logs
```

opencode server 默认不设置 `OPENCODE_SERVER_PASSWORD`，后端仍按 `http://{linuxServerIp}:{port}` 访问。生产部署必须通过容器网络、主机防火墙或网关限制端口池访问面，不得把用户进程端口暴露到不可信网络。

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

`tools/dev-backend-run.sh` 是本地启动后端的统一入口：默认读取仓库根目录未跟踪的 `.env.local` 并启动 `local` profile；传入 `--profile test` 时读取 `.env.test` 并启动 `test` profile。`.env.local` 和 `.env.test` 已被 `.gitignore` 排除，真实数据库密码只允许写入这些本机文件。

其他本地脚本：

```bash
tools/dev-local-up.sh            # 启用备用 Postgres；--redis 额外启动 Redis
tools/dev-health-check.sh --api
tools/dev-backend-check.sh
```

`deploy/local/docker-compose.yml` 默认启动备用 Postgres，映射到 `127.0.0.1:15432`；Redis 是可选 profile，默认映射到 `127.0.0.1:16379`。脚本只读取环境变量，不生成或写入密钥。

## dotenv 示例

`.env.local`（local profile）：

```dotenv
SPRING_PROFILES_ACTIVE=local
TEST_AGENT_LOCAL_DB_HOST=<dev-pg-host>
TEST_AGENT_LOCAL_DB_PORT=5432
TEST_AGENT_LOCAL_DB_NAME=<database>
TEST_AGENT_LOCAL_DB_USERNAME=<username>
TEST_AGENT_LOCAL_DB_PASSWORD=<password>
TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=<redis-password>
TEST_AGENT_OPENCODE_BASE_URL=http://127.0.0.1:4096
TEST_AGENT_MODEL_CATALOG_SOURCE=bailian
MODELSTUDIO_API_KEY=<bailian-api-key>
```

`.env.test`（test profile）：

```dotenv
SPRING_PROFILES_ACTIVE=test
TEST_AGENT_TEST_DB_HOST=<test-pg-host>
TEST_AGENT_TEST_DB_PORT=5432
TEST_AGENT_TEST_DB_NAME=<database>
TEST_AGENT_TEST_DB_USERNAME=<username>
TEST_AGENT_TEST_DB_PASSWORD=<password>
TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=<redis-password>
TEST_AGENT_OPENCODE_BASE_URL=http://127.0.0.1:4096
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
ICBC_OPENAI_AUTH_TOKEN=<icbc-openai-token>
```

配置 `TEST_AGENT_API_TOKEN` 后，`/api/**` 要求 `Authorization: Bearer <token>`；未配置时本地默认放行。

本地 profile 默认允许主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E origin。生产必须设置 `TEST_AGENT_CORS_ALLOWED_ORIGINS`，不要沿用本地端口白名单。

## 测试环境 profile

`test-agent-app` 提供 `test` profile 连接外部 PostgreSQL 测试库和外部 opencode server。真实主机、账号和密码必须通过环境变量注入，仓库内配置文件不保存密钥：

```bash
export SPRING_PROFILES_ACTIVE=test
export TEST_AGENT_TEST_DB_HOST=<test-pg-host>
export TEST_AGENT_TEST_DB_PORT=5432
export TEST_AGENT_TEST_DB_NAME=<database>
export TEST_AGENT_TEST_DB_USERNAME=<username>
export TEST_AGENT_TEST_DB_PASSWORD=<password>
export TEST_AGENT_OPENCODE_BASE_URL=http://<opencode-host>:4096
export TEST_AGENT_MODEL_CATALOG_SOURCE=internal
export ICBC_OPENAI_AUTH_TOKEN=<icbc-openai-token>
```

启用该 profile 后，Spring Boot 通过 Druid 管理 JDBC 连接池，并使用 `test-agent-persistence` 中的 Flyway migration 初始化或校验数据库结构；Actuator `health` 包含数据库健康检查；Druid Web 控制台默认关闭，不提供 `/druid/*` 管理入口。

## 连接池配置

连接池大小和借出校验可通过以下环境变量覆盖，默认值适合轻量测试和本地集成；远端 PostgreSQL 断开 idle 连接后，默认在借出连接时执行 `SELECT 1`，避免首个业务请求拿到 stale connection 后返回 500：

```bash
export TEST_AGENT_DB_POOL_INITIAL_SIZE=1
export TEST_AGENT_DB_POOL_MIN_IDLE=1
export TEST_AGENT_DB_POOL_MAX_ACTIVE=10
export TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
export TEST_AGENT_DB_POOL_TEST_ON_BORROW=true
```

## 生产必填环境变量

```bash
SPRING_PROFILES_ACTIVE=prod
TEST_AGENT_DB_URL=jdbc:postgresql://<pg-host>:5432/<database>
TEST_AGENT_DB_USERNAME=<username>
TEST_AGENT_DB_PASSWORD=<password>
TEST_AGENT_API_TOKEN=<api-token>
TEST_AGENT_CORS_ALLOWED_ORIGINS=https://<frontend-origin>
TEST_AGENT_OPENCODE_BASE_URL=http://<opencode-host>:4096
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
ICBC_OPENAI_AUTH_TOKEN=<icbc-openai-token>
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
TEST_AGENT_INTERNAL_DEFAULT_MODEL=DeepSeek-V4-Flash-W8A8
TEST_AGENT_ICBC_OPENAI_BASE_URL=http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1
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
  -e TEST_AGENT_MODEL_CATALOG_SOURCE=internal \
  -e ICBC_OPENAI_AUTH_TOKEN=change-me \
  test-agent-backend:local
```

启动后检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

`DatabaseMigrationRunner` 会在启动时执行 Flyway migration；`ExecutionNodeSeeder` 会把配置中的 opencode node 写入 `execution_nodes` 作为 Run 路由来源。启用 `TEST_AGENT_MODEL_CATALOG_SOURCE=internal` 时，`ModelCatalogApplicationService` 会把企业内模型清单 seed 到 `ai_model_configs`，后续可通过改表控制模型显示、启停和默认值。

## 模型目录配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `TEST_AGENT_MODEL_CATALOG_SOURCE` | local: `bailian`；test/prod: `internal` | 模型目录来源。`opencode` 保持旧代理，`bailian` 直连百炼 `/models`，`internal` 从数据库读取企业内模型。 |
| `TEST_AGENT_BAILIAN_BASE_URL` | `https://coding.dashscope.aliyuncs.com/v1` | 外网百炼 OpenAI-compatible base URL。 |
| `TEST_AGENT_BAILIAN_API_KEY_ENV` | `MODELSTUDIO_API_KEY` | 外网百炼密钥所在环境变量名。 |
| `TEST_AGENT_BAILIAN_DEFAULT_MODEL` | `qwen3.5-plus` | 外网模式同步给 opencode 的默认模型。 |
| `TEST_AGENT_ICBC_OPENAI_BASE_URL` | `http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1` | 企业内 OpenAI-compatible base URL，与 openclaw 企业 patch 保持一致。 |
| `TEST_AGENT_ICBC_OPENAI_TOKEN_ENV` | `ICBC_OPENAI_AUTH_TOKEN` | 企业内 token 所在环境变量名。 |
| `TEST_AGENT_ICBC_OPENAI_AUTH_MODE` | `auth-token` | 企业内调用鉴权头模式，默认写入 `Auth-Token`。 |
| `TEST_AGENT_INTERNAL_DEFAULT_MODEL` | `DeepSeek-V4-Flash-W8A8` | 企业内默认模型，前端模型切换会优先选中该模型。 |
