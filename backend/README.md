# test-agent Backend

## 工程定位

基于 Maven multi-module 的单后端服务工程。只有 `test-agent-app` 负责产出可运行 Spring Boot 包，其余 `test-agent-*` 模块都是内部 library jar。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Maven 3.9+
- Spring WebFlux
- Micrometer
- Druid JDBC 连接池
- OpenAPI Generator 生成的 opencode Java SDK

## 模块说明

| 模块 | 作用 |
|---|---|
| `test-agent-common` | 公共基础模型与工具 |
| `test-agent-domain` | 纯领域模型与状态机 |
| `test-agent-observability` | 日志、trace、指标等观测性封装 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK |
| `test-agent-opencode-client` | 业务侧 opencode client facade |
| `test-agent-persistence` | 持久化、迁移、Redis/PostgreSQL 访问 |
| `test-agent-event` | RunEvent、SSE、事件转换与回放 |
| `test-agent-test-support` | 测试支撑、fixture、mock server |
| `test-agent-app` | 唯一启动入口和唯一可部署后端服务包 |

## 构建方式

```bash
cd backend
mvn clean package -DskipTests
```

如果本机默认 `java` 不是 21，请显式指定 JDK 21：

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

## 本地运行依赖

Phase 05 提供本地 Compose 和检查脚本：

```bash
tools/dev-local-up.sh
tools/dev-local-up.sh --redis
tools/dev-health-check.sh --api
tools/dev-backend-check.sh
```

`deploy/local/docker-compose.yml` 默认启动 Postgres，映射到 `127.0.0.1:15432`；Redis 是可选 profile，默认映射到 `127.0.0.1:16379`。脚本只读取环境变量，不生成或写入密钥。

本地 profile 使用 Compose 默认值：

```bash
cd backend
export SPRING_PROFILES_ACTIVE=local
export TEST_AGENT_OPENCODE_BASE_URL=http://127.0.0.1:4096
mvn -pl test-agent-app spring-boot:run
```

配置 `TEST_AGENT_API_TOKEN` 后，`/api/**` 要求 `Authorization: Bearer <token>`；未配置时本地默认放行。

## 测试环境数据库

`test-agent-app` 提供 `test` profile 连接 PostgreSQL 测试库。真实主机、账号和密码必须通过环境变量注入，仓库内配置文件不保存密钥：

```bash
export SPRING_PROFILES_ACTIVE=test
export TEST_AGENT_TEST_DB_HOST=<test-pg-host>
export TEST_AGENT_TEST_DB_PORT=5432
export TEST_AGENT_TEST_DB_NAME=<database>
export TEST_AGENT_TEST_DB_USERNAME=<username>
export TEST_AGENT_TEST_DB_PASSWORD=<password>
```

启用该 profile 后，Spring Boot 会通过 Druid 管理 JDBC 连接池，并使用 `test-agent-persistence` 中的 Flyway migration 初始化或校验数据库结构，Actuator `health` 会包含数据库健康检查。Druid Web 控制台默认关闭，不提供 `/druid/*` 管理入口。

连接池大小可通过以下环境变量覆盖，默认值适合轻量测试和本地集成：

```bash
export TEST_AGENT_DB_POOL_INITIAL_SIZE=1
export TEST_AGENT_DB_POOL_MIN_IDLE=1
export TEST_AGENT_DB_POOL_MAX_ACTIVE=10
export TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
```

## 后续 AI 编码指引

- 新增可部署入口只允许放在 `test-agent-app`。
- 业务模块不要直接依赖 `test-agent-opencode-sdk-generated`，应通过 `test-agent-opencode-client`。
- 领域模型保持在 `test-agent-domain`，不要依赖 Spring Web 或持久化技术。
- 对外成功/错误响应使用 `test-agent-common` 的 `ApiResponse` 和 `ApiErrorResponse`。
- HTTP 入口 traceId 使用 `X-Trace-Id`，由 `test-agent-observability` 和 `test-agent-app` 协作生成或透传。
