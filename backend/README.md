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
| `test-agent-domain` | 纯领域模型与状态机，包括 Run 运行数据面、opencode 用户进程管理拓扑模型和运营分析/反馈领域端口 |
| `test-agent-observability` | 日志、trace、指标等观测性封装 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK |
| `test-agent-opencode-client` | 业务侧 opencode client facade |
| `test-agent-agent-runtime` | 多 agent 运行时接口、registry、统一日志/指标包装和 opencode 适配器 |
| `test-agent-workspace-management` | Workspace、文件、超级管理员服务器目录选择、git/diff、设置页初始版本工作区创建、应用版本工作区、个人工作区、agent 和 skill 管理业务 |
| `test-agent-opencode-runtime` | Session、Run、RunEvent 编排、Redis active/session scope 路由、用户级会话运行态摘要、agent runtime 调用、Diff/revert、AI 回复反馈、运营分析 rollup/query 和 PTY terminal 业务 |
| `test-agent-system-management` | 用户、角色、权限等系统内部管理业务，包括用户注册、登录认证、Token 管理等 |
| `test-agent-configuration-management` | 应用、应用成员、代码库英文名与关联、应用工作空间和个人 SSH key 配置管理 |
| `test-agent-scheduler` | 分布式定时任务框架，提供任务注册、Cron 调度、Redis 锁、运行记录、Cron 调整、手动触发和协作式停止管理服务，不包含具体业务任务 |
| `test-agent-integration` | 非 opencode 外部系统联动业务边界，目前为空骨架 |
| `test-agent-api` | HTTP/SSE/WebSocket API 定义、DTO、鉴权、限流、traceId 和统一异常入口 |
| `test-agent-persistence` | 持久化、MyBatis XML mapper、迁移、Redis/PostgreSQL 访问，包括 Redis Run manifest/Stream/snapshot/active 索引、opencode 用户进程管理表映射、AI 反馈表和运营分析 rollup 表 |
| `test-agent-event` | 按 storage mode 分流的 RunEvent 追加、SSE、Redis/数据库回放，以及用户级运行态刷新所需的全局事件触发流 |
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

该配置通过 `-Dspring.profiles.active=guo` 读取 `test-agent-app/src/main/resources/application-guo.yml`，不依赖 shell 启动脚本或 `.env.local`。`guo` profile 已内置 Java 进程需要的数据库、Redis、opencode、manager token、模型来源和模型 key 配置；`TEST_AGENT_OPENCODE_BIN`、`TEST_AGENT_START_OPENCODE` 等只服务于根目录启动编排脚本，不属于 Java 进程配置。当前本地联调默认改用 `test` profile 和 `.env.test`；Windows 用户要连同一测试环境时，可在 PowerShell 中执行 `powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Profile test -EnvFile .env.test`，WSL/Git Bash 中继续使用 `./restart-dev-services.sh --profile test --env-file .env.test`。仅启动 Java 后端时，仍可在 IDEA/PowerShell 中显式导入 `.env.test` 的数据库、Redis、模型和 `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 等变量，并用 `-Dspring.profiles.active=test` 启动 Java 后端。

### 环境变量配置

首次运行前，复制环境变量模板：

```bash
cp .env.local.example .env.local
```

编辑 `.env.local` 修改本地数据库、OpenCode 等配置：

环境变量只用于部署期必须由运行环境注入的密钥、外部端点、进程身份、启动引导路径或资源容量。不要为了临时绕过配置、适配个人环境或规避 `common_parameters` / Spring 配置 / 数据库配置而随意新增环境变量；确需新增时必须先评估既有配置入口，并同步更新 `docs/standards/backend.md`、`docs/deployment/backend.md`、相关 README、启动脚本或 dotenv 示例以及配置绑定测试。

| 变量 | 说明 |
|------|------|
| `TEST_AGENT_ROOT` | 项目根目录，由启动脚本自动导出；通用参数路径可使用 `$TEST_AGENT_ROOT` 引用。 |
| `TESTAGENT` | 本地测试库历史兼容别名，启动脚本默认与 `TEST_AGENT_ROOT` 相同；仅用于展开既有 `$TESTAGENT/...` 通用参数路径。 |
| `TEST_AGENT_LOCAL_DB_*` | 本地 PostgreSQL 连接信息 |
| `TEST_AGENT_REDIS_HOST` / `TEST_AGENT_REDIS_PORT` / `TEST_AGENT_REDIS_PASSWORD` | Redis 连接信息，绑定到 Spring 标准 `spring.data.redis.*`；Redis 是系统必需依赖。 |
| `TEST_AGENT_REDIS_SUMMARY_ENABLED` / `TEST_AGENT_REDIS_SUMMARY_ROLLOUT_PERCENTAGE` | Redis summary 运行模式开关和稳定灰度比例，当前默认 `false/0`；Run 创建仍固定使用 `LEGACY_FULL`，在无原文 Run 锚点和终态摘要链路发布前不得开启生产灰度。 |
| `TEST_AGENT_SCHEDULER_ENABLED` | 是否启用定时任务后台扫描，默认 false；启用时使用同一 Redis。 |
| `TEST_AGENT_OPENCODE_BASE_URL` | 本地脚本判断是否启动 opencode-manager 和端口池的地址，不再作为 Java 固定 opencode node 配置。 |
| `TEST_AGENT_LINUX_SERVER_ID` | 稳定 Linux 服务器身份，可使用 `server-a`、`prod_01`、`10.1.2.3` 等 1-128 位标识；缺失时使用 Java 主机名。 |
| `TEST_AGENT_DEPLOYMENT_MODE` | 部署模式：`external`（外部部署，默认）或 `internal`（企业内部部署）。 |
| `TEST_AGENT_SERVER_ADVERTISED_HOST` | 当前 Java/用户 opencode server 对其它后端和浏览器可访问的主机地址；缺失时复用现有内网 IPv4 探测。 |
| `TEST_AGENT_MODEL_CATALOG_SOURCE` | 历史兼容项。前端对话框模型/供应商目录已统一走 opencode 原生 `/api/model`、`/api/provider`，不再从数据库模型目录读取。 |
| `EXTERNAL_API_KEY` | 外部 OpenAI-compatible API Key；变量名可通过 `TEST_AGENT_EXTERNAL_MODEL_API_KEY_ENV` 改为其他环境变量名。 |
| `MODELSTUDIO_API_KEY` | `TEST_AGENT_MODEL_CATALOG_SOURCE=bailian` 时使用的 Model Studio API Key；该模式使用代码内置 `modelstudio` provider 和 qwen/kimi 模型清单。 |
| `TEST_AGENT_INTERNAL_PROXY_API_KEY` | Java 内部模型代理鉴权 apikey；Java 校验 opencode 子进程请求，manager 启动用户 opencode server 时把同值注入子进程环境。 |
| `ICBC_OPENAI_AUTH_TOKEN` | 历史兼容项；新实现从数据库 `internal_model_proxy_settings` 明文读取全局 token，由前端“内部模型供应商”页面写入。 |
| `TEST_AGENT_EXTERNAL_MODEL_BASE_URL` | 外部 OpenAI-compatible base URL，例如 `https://api.deepseek.com`。旧 `TEST_AGENT_BAILIAN_BASE_URL` 仍作为兼容兜底。 |
| `TEST_AGENT_ICBC_OPENAI_BASE_URL` | 企业内 OpenAI-compatible base URL，默认与 openclaw 企业 patch 中的 `icbc-openai` 地址一致。 |
| `TEST_AGENT_ICBC_OPENAI_UCID_HEADER_NAME` | 历史兼容项；新实现固定由 opencode 配置把环境变量 `ICBC_UCID` 注入请求头 `ucid`。 |

`guo` profile 的 IDEA 启动路径已把上述本地 Java 运行参数写入 yml；继续使用 `tools/dev-backend-run.sh`、`restart-dev-services.sh --profile guo --env-file .env.local` 或 `restart-dev-services.ps1 -Profile guo -EnvFile .env.local` 时，`.env.local` 仍可覆盖 yml，便于本地联调脚本启动前后端和 opencode。根目录一键脚本不带参数时默认读取 `.env.test` 并启动 `test` profile，test profile 下默认启动本机 Go manager，即使 `.env.test` 中 `TEST_AGENT_OPENCODE_BASE_URL` 指向共享测试地址；停止 manager 时会清理其托管的用户 opencode 子进程和 state JSON，防止端口池残留进程导致下次初始化失败。生产和本地都不再配置 `OPENCODE_MANAGER_ID`，Go manager 会由容器名称和固定管理进程名 `opencode-manager` 派生内部 `managerId`。

用户专属 opencode 进程的 session/config 路径来自数据库 `common_parameters`，不是 `.env.local`。opencode 原生 session 数据目录固定为 `{OPENCODE_SESSION_DIR}/users/{unifiedAuthId}`，Java 通过用户仓储解析统一认证号，并拒绝无法作为安全路径片段的统一认证号；旧 `{OPENCODE_SESSION_DIR}/{port}` 目录不自动合并，平台历史消息仍可展示，缺失的远端 session 会在下次提问前校验并重建绑定。新会话先保留页面首条消息的临时标题；首轮 Run 成功后，平台继续监听同一远端 root session 的原生 `title` agent 完成事件，收到后读取该远端 session 的最终标题，并只在标题不是默认时间戳且平台标题仍为首条消息临时标题时同步到页面。没有临时会话、二次 title 调用或基于超时的替代命名。系统级数据根目录通过 `SYS_DATA_ROOT_DIR` 维护，默认值为 macOS `$HOME/.testagent`、Linux `/data/.testagent`、Windows `D:/data/.testagent`；Java 后端启动时写入 `SYS_DATA_ROOT_DIR/.serverid` 和 `.serverhost`，分别表示稳定服务器身份和可访问主机地址，Go manager 在连接 Java 前按同一系统参数的平台默认路径读取这两个文件。每个 manager 只连接本服务器 Java；同一服务器允许运行多个 Java，多个 Java 共享同一个 `linuxServerId`，入口 Java 会通过 `BackendJavaRouteResolver` 优先选择与目标服务器 manager 已连接的 Java，其次选择同服务器最新心跳 Java，再通过 `BackendHttpForwarder` 透传到目标 Java，由目标 Java 控制本服务器 managers。是否已分配只以 `user_opencode_process_bindings` 的 ACTIVE 记录为准；`/processes/me` 状态查询在目标后端不可用时会返回已分配但健康不可确认的 `NOT_RUNNING + serviceAddress`，初始化、Run 和 runtime 代理仍必须由目标服务器执行，不做本机降级。所有强状态查询、健康探测、状态回写和 Redis opencode heartbeat 刷新都必须走 `OpencodeProcessStatusQueryService`：先确认平台进程记录是否存在，再通过目标 Java 的本机 manager health 归一为未启动、运行中或健康检查异常。点击初始化且没有远端路由时，Java 后端按本实例已连接的健康容器视图选择进程数最少且有空闲端口的目标容器，再调用 `OpencodeProcessStartupService` 向该容器对应的 manager 下发携带用户 `sessionPath` 的 `start`；新进程 `baseUrl` 使用 `.serverhost` / `TEST_AGENT_SERVER_ADVERTISED_HOST`，不再由 `linuxServerId` 拼接。该公共启动服务会先保存候选进程，再复用公共状态查询服务确认本地 state/PID 和 opencode HTTP health，默认最多等待 manager command-timeout（10 秒）让 opencode HTTP 端点 ready，只有健康后才写入 `RUNNING`、ACTIVE binding、Redis heartbeat 和兼容 `ExecutionNode`。后续所有涉及 opencode server 启动、重启后拉起或端口复用的业务入口都必须调用这套公共启动程序，不得自行实现 start、状态回写或健康确认；所有涉及 opencode server 停止或停止后状态回写的业务入口都必须调用 `OpencodeProcessStopService`，不得自行实现 stop、停止成功判定或 `STOPPED` 回写。manager 使用通过 `configRequest/configUpdate` 同步的 `OPENCODE_PUBLIC_CONFIG_DIR`，该目录下的 `opencode.jsonc` 来自公共配置 Git 库，是模型和供应商事实源；企业部署必须保证运行用户的 `~/.config/opencode` 不维护模型或供应商，最多保留空 schema 配置，避免 OpenCode 合并全局配置污染公共目录。目录存在且非空的检查只在目标 manager 所在服务器执行。目录缺失、为空、非目录或不可读时，manager 返回 `OPENCODE_UNAVAILABLE`，错误消息包含目标服务器和 manager 实际检查的配置目录，并提示联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化；Java 仅映射为统一平台错误，不在本机提前检查。本地和生产都必须启动 Go manager，不再支持 `local-direct` 或 `gateway-mode=local` 绕过。

Run 运行数据面通过 domain `RunRuntimeStore` 与 persistence `RedisRunRuntimeStore` 隔离：单 Run manifest/input/双 Stream/snapshot/scope key 使用 `{runId}` hash tag，durable `events` Stream ID 为 `${seq}-0`，durable/transient 全事件 `runtime-events` Stream ID 为 `${runtimeVersion}-0`，snapshot 使用 Hash + order ZSET 保留当前物化状态。`REDIS_SUMMARY` 下每条事件不访问 PostgreSQL；SSE 首帧总是发送完整 `run.snapshot.reset`，然后由最短 5 秒的 Redis 安全扫描和本机 live bus 只唤醒按 `runtimeVersion` 分页读 Redis 尾流，live 事件仍即时唤醒但帧本身不直接输出；容量换代导致游标过旧时再次发送 reset。legacy 仍以 PostgreSQL 为事实源并保留旧轮询恢复。生产 Redis 的持久化、安全和容量要求见 `docs/deployment/backend.md`。

运行管理中的 Java 后端快照按 `backendProcessId` 写入 Redis，并按 `linuxServerId` 分组选择目标 Java；`linuxServerId` 表示稳定服务器身份，不再要求是 IP。超级管理员在运行管理页重启/停止 opencode server 时，入口 Java 会先按统一 resolver 定位 `containerId` 所属服务器，目标不是当前 Java 或同服务器选中 Java 时转发到目标 Java，再由目标 Java 控制本服务器 manager；已有平台进程记录的重启先走公共停止服务，再用进程记录里的 `sessionPath` 走公共启动服务，无平台记录的无主端口才保留 manager `restart` fallback。公共配置管理页同样按稳定 `linuxServerId` 合并服务器视图。

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
- HTTP/SSE/WebSocket 入口放在 `test-agent-api`，旧 `/api/...` URL 默认保留，明确作废的入口除外；新 URL 同步写入 `docs/api/http-api.md`。
- Workspace、文件、git/diff、设置页初始版本工作区创建、应用版本工作区、个人工作区、agent、skill 管理业务放在 `test-agent-workspace-management`。
- 多 agent 运行时接口、`agentId` 选择、日志/指标包装和具体 agent 适配器放在 `test-agent-agent-runtime`。
- Session、Run、RunEvent、agent runtime 调用、Diff/revert、terminal 业务放在 `test-agent-opencode-runtime`。
- Model/Provider 目录始终由 opencode 配置文件决定；内部模型代理和 `<think>` 流式转换放在 `test-agent-opencode-runtime` / `test-agent-api`，内部供应商地址和 token 端口放在 `test-agent-domain`，MyBatis/Flyway 实现放在 `test-agent-persistence`。
- 新增或修改关系型数据库 SQL 必须放在 `test-agent-persistence` 的 MyBatis XML mapper 中；存量 `Jdbc*Repository` 只保留迁移窗口，不承接新 SQL。
- 涉及 opencode-manager 路由、Java 到 manager 控制、用户 opencode 进程服务器归属、运行管理 `containerId` 路由、Agent 配置或文件 WebSocket 目标后端选择时，必须复用 `BackendJavaRouteResolver`、`BackendHttpForwarder` 和目标 Java 的 `OpencodeProcessManagerGateway` 公共链路；禁止新增自写 Redis 快照扫描、Java->Java HTTP 转发、防循环 header、本机降级或本地绕过。涉及 opencode server 启动、停止或状态查询时，分别复用 `OpencodeProcessStartupService`、`OpencodeProcessStopService` 和 `OpencodeProcessStatusQueryService`。
- 用户、角色、权限等平台内部管理放在 `test-agent-system-management`。
- 应用配置、应用人员、代码库英文名与关联、应用工作空间模板和个人 SSH key 管理放在 `test-agent-configuration-management`；应用版本工作区运行编排和工作空间创建进度放在 `test-agent-workspace-management`。
- 通用分布式定时任务框架和超级管理员定时任务管理服务放在 `test-agent-scheduler`；具体业务任务实现放回所属业务模块，通过 `ScheduledTaskHandler` Bean 注册，并在长循环中检查 `ScheduledTaskContext` 的停止请求。
- 非 opencode 外部系统联动放在 `test-agent-integration`。
- 业务模块不要直接依赖 `test-agent-opencode-sdk-generated`，应通过 `test-agent-opencode-client`。
- 领域模型保持在 `test-agent-domain`，不要依赖 Spring Web 或持久化技术。
- 对外成功/错误响应使用 `test-agent-common` 的 `ApiResponse` 和 `ApiErrorResponse`。
- HTTP 入口 traceId 使用 `X-Trace-Id`，由 `test-agent-observability` 和 `test-agent-api` 协作生成或透传。
- 后端运行态使用 Log4j2 作为 SLF4J 实际绑定，默认控制台日志为 `key=value` 结构化格式并输出 traceId；运行文件日志写入 `logs/backend.log`，SSE 相关日志额外写入 `logs/sse.log`，`ERROR` 及以上日志额外写入 `logs/error.log`。
