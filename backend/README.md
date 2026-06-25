# test-agent Backend

## 工程定位

基于 Maven multi-module 的单后端服务工程。只有 `test-agent-app` 负责产出可运行 Spring Boot 包，其余 `test-agent-*` 模块都是内部 library jar。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Maven 3.9+
- Spring WebFlux
- Log4j2
- Micrometer
- Druid JDBC 连接池
- OpenAPI Generator 生成的 opencode Java SDK

## 模块说明

| 模块 | 作用 |
|---|---|
| `test-agent-common` | 公共基础模型与工具 |
| `test-agent-domain` | 纯领域模型与状态机，包括 opencode 用户进程管理拓扑模型 |
| `test-agent-observability` | 日志、trace、指标等观测性封装 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK |
| `test-agent-opencode-client` | 业务侧 opencode client facade |
| `test-agent-agent-runtime` | 多 agent 运行时接口、registry、统一日志/指标包装和 opencode 适配器 |
| `test-agent-workspace-management` | Workspace、文件、受控目录选择、git/diff、应用版本工作区、个人工作区、agent 和 skill 管理业务 |
| `test-agent-opencode-runtime` | Session、Run、RunEvent 编排、agent runtime 调用、Diff/revert 和 PTY terminal 业务 |
| `test-agent-system-management` | 用户、角色、权限等系统内部管理业务，包括用户注册、登录认证、Token 管理等 |
| `test-agent-configuration-management` | 应用、应用成员、代码库关联、应用工作空间和个人 SSH key 配置管理 |
| `test-agent-integration` | 非 opencode 外部系统联动业务边界，目前为空骨架 |
| `test-agent-api` | HTTP/SSE/WebSocket API 定义、DTO、鉴权、限流、traceId 和统一异常入口 |
| `test-agent-persistence` | 持久化、迁移、Redis/PostgreSQL 访问，包括 opencode 用户进程管理表映射 |
| `test-agent-event` | RunEvent、SSE、事件转换与回放 |
| `test-agent-test-support` | 测试支撑、fixture、mock server |
| `test-agent-app` | 唯一启动入口和唯一可部署后端服务包，不承载业务逻辑 |

## 构建方式

```bash
cd backend
mvn clean package -DskipTests
```

## 本地开发启动

### 环境要求

- **Java 21+**（项目使用 Java 21 编译，class file version 65.0）
- **Maven 3.9+**

检查 Java 版本：
```bash
java -version
# 必须是 21 或更高版本
```

如果本机默认 `java` 不是 21+，请显式指定：
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# 或使用 Java 25
export JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home
```

### 启动后端

```bash
# 使用 dev-backend-run.sh 脚本启动（推荐）
# 默认读取 .env.local，profile=local
tools/dev-backend-run.sh

# 或指定其他 profile
tools/dev-backend-run.sh --profile test

# 或直接使用 Maven
cd backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -pl test-agent-app
```

### 环境变量配置

首次运行前，复制环境变量模板：

```bash
cp .env.local.example .env.local
```

编辑 `.env.local` 修改本地数据库、OpenCode 等配置：

| 变量 | 说明 |
|------|------|
| `TEST_AGENT_LOCAL_DB_*` | 本地 PostgreSQL 连接信息 |
| `TEST_AGENT_REDIS_ENABLED` | 是否启用 Redis（本地可设为 false） |
| `TEST_AGENT_OPENCODE_BASE_URL` | OpenCode 服务地址 |
| `TEST_AGENT_MODEL_CATALOG_SOURCE` | 模型目录来源：`opencode` 保持旧代理，`bailian` 直连百炼 `/models`，`internal` 从数据库读取企业内模型。local 默认 `bailian`，test/prod 默认 `internal`。 |
| `MODELSTUDIO_API_KEY` | 外网百炼 Model Studio Coding Plan API Key；变量名可通过 `TEST_AGENT_BAILIAN_API_KEY_ENV` 改为其他环境变量名。 |
| `ICBC_OPENAI_AUTH_TOKEN` | 企业内 `icbc-openai` 访问 token；变量名可通过 `TEST_AGENT_ICBC_OPENAI_TOKEN_ENV` 改为其他环境变量名。 |
| `TEST_AGENT_BAILIAN_BASE_URL` | 外网百炼 OpenAI-compatible base URL，默认 `https://coding.dashscope.aliyuncs.com/v1`。 |
| `TEST_AGENT_ICBC_OPENAI_BASE_URL` | 企业内 OpenAI-compatible base URL，默认与 openclaw 企业 patch 中的 `icbc-openai` 地址一致。 |

验证后端启动成功：
```bash
curl http://127.0.0.1:8080/actuator/health
# 应返回 {"status":"UP",...}
```

多 Linux 服务器、opencode-manager、用户专属 opencode server 进程的部署与验收见 `docs/deployment/backend.md`。真实环境只读 smoke check 可从仓库根目录执行：

```bash
tools/verify-opencode-process-deployment.sh \
  --backend-url http://<backend-or-lb>:8080 \
  --manager-token <manager-control-token> \
  --auth-token <super-admin-user-jwt>
```

### 启动前端

```bash
cd frontend
npm run dev
# 访问 http://127.0.0.1:3000
```

### 常见问题

1. **`UnsupportedClassVersionError: class file version 65.0`**
   - 原因：Java 版本过低，项目需要 Java 21+
   - 解决：设置 `JAVA_HOME` 指向 Java 21+

2. **`Connection to 127.0.0.1:5432 refused`**
   - 原因：未设置 `SPRING_PROFILES_ACTIVE=local`，使用了默认的本地数据库配置
   - 解决：确保 `.env.local` 存在且设置了 `SPRING_PROFILES_ACTIVE=local`

3. **前端登录失败 `failed to fetch`**
   - 原因：后端未启动或端口不对
   - 解决：确认后端在 8080 端口运行

## 测试与校验

跨模块修改完成后，默认在 `backend` 目录执行：

```bash
mvn test
```

针对局部模块可先使用 `mvn -pl <module> -am test` 快速验证，但合并前仍应跑全量后端测试，确保 common、domain、API、persistence、runtime 和 app 装配没有破坏依赖边界。

## 部署与运行

镜像构建、生产/测试 profile、dotenv、连接池和外部依赖配置见 `docs/deployment/backend.md`。

## 后续 AI 编码指引

- 新增可部署入口只允许放在 `test-agent-app`。
- 新增业务文件前先列出现有合适工程；无合适工程时按业务边界新建 Maven module。
- `test-agent-app` 只放启动、装配、profile、migration 和 health 等运行入口，不放 Controller 或业务服务。
- HTTP/SSE/WebSocket 入口放在 `test-agent-api`，旧 `/api/...` URL 必须保留，新 URL 同步写入 `docs/api/http-api.md`。
- Workspace、文件、git/diff、应用版本工作区、个人工作区、agent、skill 管理业务放在 `test-agent-workspace-management`。
- 多 agent 运行时接口、`agentId` 选择、日志/指标包装和具体 agent 适配器放在 `test-agent-agent-runtime`。
- Session、Run、RunEvent、agent runtime 调用、Diff/revert、terminal 业务放在 `test-agent-opencode-runtime`。
- Model 目录与 opencode provider 同步逻辑放在 `test-agent-opencode-runtime`；企业内模型主数据端口放在 `test-agent-domain`，JDBC/Flyway 实现放在 `test-agent-persistence`。
- 用户、角色、权限等平台内部管理放在 `test-agent-system-management`。
- 应用配置、应用人员、代码库关联、应用工作空间模板和个人 SSH key 管理放在 `test-agent-configuration-management`；应用版本工作区运行编排放在 `test-agent-workspace-management`。
- 非 opencode 外部系统联动放在 `test-agent-integration`。
- 业务模块不要直接依赖 `test-agent-opencode-sdk-generated`，应通过 `test-agent-opencode-client`。
- 领域模型保持在 `test-agent-domain`，不要依赖 Spring Web 或持久化技术。
- 对外成功/错误响应使用 `test-agent-common` 的 `ApiResponse` 和 `ApiErrorResponse`。
- HTTP 入口 traceId 使用 `X-Trace-Id`，由 `test-agent-observability` 和 `test-agent-api` 协作生成或透传。
- 后端运行态使用 Log4j2 作为 SLF4J 实际绑定，默认控制台日志为 `key=value` 结构化格式并输出 traceId。
