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
- MyBatis XML mapper
- OpenAPI Generator 生成的 opencode Java SDK

## 模块说明

| 模块 | 作用 |
|---|---|
| `test-agent-common` | 公共基础模型与工具 |
| `test-agent-domain` | 纯领域模型与状态机，包括 opencode 用户进程管理拓扑模型和运营分析/反馈领域端口 |
| `test-agent-observability` | 日志、trace、指标等观测性封装 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK |
| `test-agent-opencode-client` | 业务侧 opencode client facade |
| `test-agent-agent-runtime` | 多 agent 运行时接口、registry、统一日志/指标包装和 opencode 适配器 |
| `test-agent-workspace-management` | Workspace、文件、受控目录选择、git/diff、设置页初始版本工作区创建、应用版本工作区、个人工作区、agent 和 skill 管理业务 |
| `test-agent-opencode-runtime` | Session、Run、RunEvent 编排、agent runtime 调用、Diff/revert、AI 回复反馈、运营分析 rollup/query 和 PTY terminal 业务 |
| `test-agent-system-management` | 用户、角色、权限等系统内部管理业务，包括用户注册、登录认证、Token 管理等 |
| `test-agent-configuration-management` | 应用、应用成员、代码库英文名与关联、应用工作空间和个人 SSH key 配置管理 |
| `test-agent-scheduler` | 分布式定时任务框架，提供任务注册、Cron 调度、Redis 锁、运行记录、Cron 调整、手动触发和协作式停止管理服务，不包含具体业务任务 |
| `test-agent-integration` | 非 opencode 外部系统联动业务边界，目前为空骨架 |
| `test-agent-api` | HTTP/SSE/WebSocket API 定义、DTO、鉴权、限流、traceId 和统一异常入口 |
| `test-agent-persistence` | 持久化、MyBatis XML mapper、迁移、Redis/PostgreSQL 访问，包括 opencode 用户进程管理表映射、AI 反馈表和运营分析 rollup 表 |
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
tools/dev-backend-run.sh --profile guo

# 或直接使用 Maven
cd backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -pl test-agent-app
```

`tools/dev-backend-run.sh` 和仓库根目录的 `restart-dev-services.sh` 启动后端 Java 进程时会清空 JVM 的 HTTP/HTTPS/FTP/SOCKS 代理系统属性，避免本机系统代理影响 PostgreSQL JDBC 与 Redis 直连。直接使用 Maven 或 IDEA 启动时，如果本机开启了全局 SOCKS/HTTP 代理，需要在 VM options 中显式清空同类 `-D*proxy*` 参数。

Windows 开发人员若只需要 legacy guo profile，可直接使用已提交的 IDEA 运行配置 `TestAgentApplication guo`：

1. 用 IDEA 导入 `backend/pom.xml`。
2. 选择 Run Configuration `TestAgentApplication guo`。
3. 使用 JDK 21+ 启动。

该配置通过 `-Dspring.profiles.active=guo` 读取 `test-agent-app/src/main/resources/application-guo.yml`，不依赖 shell 启动脚本或 `.env.local`。`guo` profile 已内置 Java 进程需要的数据库、Redis、opencode、manager token、模型来源和模型 key 配置；`TEST_AGENT_OPENCODE_BIN`、`TEST_AGENT_START_OPENCODE` 等只服务于根目录启动编排脚本，不属于 Java 进程配置。当前本地联调默认改用 `test` profile 和 `.env.test`；Windows 用户要连同一测试环境时，可在 PowerShell 中执行 `powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Profile test -EnvFile .env.test`，WSL/Git Bash 中继续使用 `./restart-dev-services.sh --profile test --env-file .env.test`。仅启动 Java 后端时，仍可在 IDEA/PowerShell 中显式导入 `.env.test` 的数据库、Redis、模型、`TEST_AGENT_OPENCODE_MANAGER_TOKEN` 和 `.serverip` 路径等变量，并用 `-Dspring.profiles.active=test` 启动 Java 后端。

### 环境变量配置

首次运行前，复制环境变量模板：

```bash
cp .env.local.example .env.local
```

编辑 `.env.local` 修改本地数据库、OpenCode 等配置：

| 变量 | 说明 |
|------|------|
| `TEST_AGENT_ROOT` | 项目根目录，由启动脚本自动导出；通用参数路径可使用 `$TEST_AGENT_ROOT` 引用。 |
| `TEST_AGENT_LOCAL_DB_*` | 本地 PostgreSQL 连接信息 |
| `TEST_AGENT_REDIS_HOST` / `TEST_AGENT_REDIS_PORT` / `TEST_AGENT_REDIS_PASSWORD` | Redis 连接信息；Redis 是系统必需依赖。 |
| `TEST_AGENT_SCHEDULER_ENABLED` | 是否启用定时任务后台扫描，默认 false；启用时使用同一 Redis。 |
| `TEST_AGENT_OPENCODE_BASE_URL` | OpenCode 服务地址 |
| `TEST_AGENT_SERVER_IP_FILE` | Java 后端写给 Go manager 读取的服务器 IPv4 文件，默认 `/data/.testagent/.serverip`；一键脚本本地默认改写到 `.tmp/dev-services/.serverip`。 |
| `TEST_AGENT_MODEL_CATALOG_SOURCE` | 模型目录来源：`opencode` 保持旧代理，`bailian` 直连百炼 `/models`，`internal` 从数据库读取企业内模型。local 默认 `bailian`，test/prod 默认 `internal`。 |
| `MODELSTUDIO_API_KEY` | 外网百炼 Model Studio Coding Plan API Key；变量名可通过 `TEST_AGENT_BAILIAN_API_KEY_ENV` 改为其他环境变量名。 |
| `ICBC_OPENAI_AUTH_TOKEN` | 企业内 `icbc-openai` 访问 token；变量名可通过 `TEST_AGENT_ICBC_OPENAI_TOKEN_ENV` 改为其他环境变量名。 |
| `TEST_AGENT_BAILIAN_BASE_URL` | 外网百炼 OpenAI-compatible base URL，默认 `https://coding.dashscope.aliyuncs.com/v1`。 |
| `TEST_AGENT_ICBC_OPENAI_BASE_URL` | 企业内 OpenAI-compatible base URL，默认与 openclaw 企业 patch 中的 `icbc-openai` 地址一致。 |

`guo` profile 的 IDEA 启动路径已把上述本地 Java 运行参数写入 yml；继续使用 `tools/dev-backend-run.sh`、`restart-dev-services.sh --profile guo --env-file .env.local` 或 `restart-dev-services.ps1 -Profile guo -EnvFile .env.local` 时，`.env.local` 仍可覆盖 yml，便于本地联调脚本启动前后端和 opencode。根目录一键脚本不带参数时默认读取 `.env.test` 并启动 `test` profile；停止 manager 时会清理其托管的用户 opencode 子进程和 state JSON，防止端口池残留进程导致下次初始化失败。

用户专属 opencode 进程的 session/config 路径来自数据库 `common_parameters`，不是 `.env.local`。点击初始化时，Java 后端先按当前后端已连接的健康容器视图选择进程数最少且有空闲端口的目标容器，再向该容器对应的 manager 下发 `start`；manager 使用已通过 `configUpdate` 同步的 `OPENCODE_PUBLIC_CONFIG_DIR`。目录存在且非空的检查只在目标 manager 所在服务器执行。目录缺失、为空、非目录或不可读时，manager 返回 `OPENCODE_UNAVAILABLE`，并明确提示超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化；Java 仅映射为统一平台错误，不在本机提前检查。

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
- Workspace、文件、git/diff、设置页初始版本工作区创建、应用版本工作区、个人工作区、agent、skill 管理业务放在 `test-agent-workspace-management`。
- 多 agent 运行时接口、`agentId` 选择、日志/指标包装和具体 agent 适配器放在 `test-agent-agent-runtime`。
- Session、Run、RunEvent、agent runtime 调用、Diff/revert、terminal 业务放在 `test-agent-opencode-runtime`。
- Model 目录与 opencode provider 同步逻辑放在 `test-agent-opencode-runtime`；企业内模型主数据端口放在 `test-agent-domain`，MyBatis/Flyway 实现放在 `test-agent-persistence`。
- 新增或修改关系型数据库 SQL 必须放在 `test-agent-persistence` 的 MyBatis XML mapper 中；存量 `Jdbc*Repository` 只保留迁移窗口，不承接新 SQL。
- 用户、角色、权限等平台内部管理放在 `test-agent-system-management`。
- 应用配置、应用人员、代码库英文名与关联、应用工作空间模板和个人 SSH key 管理放在 `test-agent-configuration-management`；应用版本工作区运行编排和工作空间创建进度放在 `test-agent-workspace-management`。
- 通用分布式定时任务框架和超级管理员定时任务管理服务放在 `test-agent-scheduler`；具体业务任务实现放回所属业务模块，通过 `ScheduledTaskHandler` Bean 注册，并在长循环中检查 `ScheduledTaskContext` 的停止请求。
- 非 opencode 外部系统联动放在 `test-agent-integration`。
- 业务模块不要直接依赖 `test-agent-opencode-sdk-generated`，应通过 `test-agent-opencode-client`。
- 领域模型保持在 `test-agent-domain`，不要依赖 Spring Web 或持久化技术。
- 对外成功/错误响应使用 `test-agent-common` 的 `ApiResponse` 和 `ApiErrorResponse`。
- HTTP 入口 traceId 使用 `X-Trace-Id`，由 `test-agent-observability` 和 `test-agent-api` 协作生成或透传。
- 后端运行态使用 Log4j2 作为 SLF4J 实际绑定，默认控制台日志为 `key=value` 结构化格式并输出 traceId。
