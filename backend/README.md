# test-agent Backend

## 工程定位

基于 Maven multi-module 的单后端服务工程。只有 `test-agent-app` 负责产出可运行 Spring Boot 包，其余 `test-agent-*` 模块都是内部 library jar。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Maven 3.9+
- Spring WebFlux
- 独立 Spring MVC/Tomcat XXL Admin 子上下文
- Log4j2
- Micrometer
- Druid JDBC 连接池
- MyBatis XML mapper
- PostgreSQL（平台）与 MySQL 8.4（XXL-JOB）双数据库隔离
- OpenAPI Generator 生成的 opencode Java SDK

OpenCode 源码快照 `opencode-source/opencode-1.18.4/` 只用于审计和行为参考，严格禁止修改；平台适配必须通过 `test-agent-opencode-client`、运行时业务或其他本项目模块实现。

## 模块说明

| 模块 | 作用 |
|---|---|
| `test-agent-common` | 公共基础模型与工具 |
| `test-agent-domain` | 纯领域模型与状态机，包括 Run 运行数据面、会话 `QUESTION/PERMISSION` 待关注摘要、应用源码快照、Agent & Skill Hub 领域端口、opencode 用户进程管理拓扑模型和运营分析/反馈领域端口 |
| `test-agent-observability` | 日志、trace、指标等观测性封装 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK |
| `test-agent-opencode-client` | 业务侧 opencode client facade |
| `test-agent-agent-runtime` | 多 agent 运行时接口、registry、统一日志/指标包装和 opencode 适配器 |
| `test-agent-workspace-management` | Workspace、文件、超级管理员服务器目录选择、同服务器共享体验目录及实时资格校验、git/diff、对话 Tool 到当前个人 workspace 的安全映射与 Git 编排、设置页初始版本工作区创建、应用版本工作区、个人工作区、个人拉取成功后的单用户运行态重载登记、Agent & Skill Hub 快照/发布/引用/更新、应用引用资产库多服务器副本及按 generation 终止/重试、应用源码固定提交快照/副本/打开/清理、agent 和 skill 管理业务 |
| `test-agent-workspace-filesystem` | 服务端与本地客户端共享的安全文件内核：真实根锚定、相对路径、符号链接防逃逸、原子移动和分片读写。 |
| `test-agent-local-client-protocol` | `local-opencode-client.v1` 反向隧道帧、版本、分片和载荷契约。 |
| `test-agent-local-client` | Java 21 用户级麒麟 ARM64/aarch64 + glibc、Windows 10 x64 客户端，监管 loopback OpenCode、反向连接、文件 RPC、模型中继与同平台受签名发布单元的静默更新/回退。 |
| `test-agent-opencode-runtime` | Session、Run、RunEvent 编排、批量单项 Session 幂等创建、夜间异步执行和会话锁、Redis active/session scope 路由、含 question/permission 计数的用户级会话运行态摘要、每用户公共配置软链接/个人保存与发布 dispose、个人拉取应用 Agent 的当前用户持久化排空、opencode 进程启动环境与 manager 重连后的 ACTIVE 运行进程恢复、公共 Tool 用户作用域凭据、agent runtime 调用、Diff/revert、AI 回复反馈、ClickHouse 运营事件消费/回填/汇总/查询，以及 workspace/server-shell 共用的受控 PTY terminal 业务 |
| `test-agent-notification` | 通用用户站内通知生命周期、未读统计、事务提交后本机/跨 Java 实时变化、30 秒数据库校准和 90 天历史清理；首期由会话协作分享生产通知 |
| `test-agent-system-management` | 用户、角色、权限等系统内部管理业务，包括用户注册、登录认证、Token 管理、本地客户端 Key/发布策略，以及外部工具 API Key 生成、RSA 密文管理、JVM 注册表与跨 Java 刷新 |
| `test-agent-configuration-management` | 应用、应用成员、代码库英文名与关联、已初始化引用资产库及已有应用源码历史的英文名/类型冻结、应用工作空间、个人 SSH key、可审计通用参数配置管理，以及显式 JVM 内存参数的本机注册/诊断状态 |
| `test-agent-scheduler` | XXL adapter 复用的任务 handler/context/result、Redis 全局锁和旧运行记录清理；不再启动 PostgreSQL runner 或创建 `USER_PLAN` |
| `test-agent-integration` | 非 opencode 外部系统联动业务边界；承载版本化工具盒子、LobeHub、SkillHub 目录/显式上传/进度/下载联动，以及外部用户 SSH Key 查询与 TAEK1 加密封装 |
| `test-agent-model-gateway` | 中立企业模型目录、能力探测、OpenAI-compatible 流式代理、上游错误脱敏和每日用量聚合，并向既有 OpenCode 内部代理提供共享安全支持 |
| `test-agent-memory` | 通用长期记忆编排：个人/团队治理、官方风格 Mem0 REST、学习 Outbox、2 秒 fail-open 检索、证据引用和 Skill 提案；不保存聊天正文、不直连记忆库 |
| `test-agent-xxl-job-admin-upstream` | 原样保存 XXL-JOB Admin 3.4.2 源码/资源与 GPL-3.0 许可证，不承载平台补丁 |
| `test-agent-xxl-job-integration` | 独立 Servlet Admin 子上下文、MySQL Flyway、Admin readiness 就绪后延迟启动的 executor、周期任务 adapter、平台一次性 SSO、JIT 用户和 XXL health |
| `test-agent-api` | HTTP/SSE/WebSocket API 定义、DTO、鉴权、限流、traceId、按进程精确 Java->Java 聚合、通知中心分页/已读/用户级 SSE、本地客户端版本管理/更新入口、应用源码快照/持久化进度入口和统一异常入口 |
| `test-agent-persistence` | 持久化、MyBatis XML mapper、迁移、Redis/PostgreSQL/ClickHouse 访问，包括用户通知与分享有效性投影、外部 API 凭据/Scope、每服务器体验 Workspace 当前绑定、Redis Run manifest/Stream/snapshot/active 索引、应用源码 slot/snapshot/replica/operation/step/cleanup/recent、Agent & Skill Hub 制品与引用状态、opencode 用户进程管理、scheduler/夜间任务、引用资产、工具点击、AI 反馈，以及运营脱敏 outbox、ClickHouse 事实/汇总查询 |
| `test-agent-event` | 按 storage mode 分流的 RunEvent 追加、SSE、Redis/数据库回放，以及用户级运行态刷新所需的全局事件触发流 |
| `test-agent-test-support` | 测试支撑、fixture、mock server |
| `test-agent-app` | 唯一启动入口和唯一可部署后端服务包，不承载业务逻辑 |

OpenCode Observability 仍在现有模块边界内实现：`test-agent-domain` 只定义 Trace catalog/span/fact 与稳定进程代次端口，
`test-agent-opencode-runtime` 负责短期插件令牌、不可变归档和查询编排，`test-agent-persistence` 只向 ClickHouse 写目录与
元数据，并通过 MyBatis 把 opaque generation 写入 PostgreSQL 进程行；`test-agent-api` 提供插件/内部分片入口及
`SUPER_ADMIN` Trace API。Trace 目录和插件覆盖起点只以已关联平台 Run 的采集事实为准；无 Session/Run 的进程生命周期广播不进入目录，也不能提前冻结运行态指标切换点。正文不进入数据库、Redis、RunEvent 或日志。

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

`tools/dev-backend-run.sh` 和仓库根目录的 `restart-dev-services.sh` 启动后端 Java 进程时，会先把 Maven `target` 中的可执行 JAR 校验并复制为 `.tmp/dev-services/backend-runtime/` 下本次启动专属的不可变副本，再清空 JVM 的 HTTP/HTTPS/FTP/SOCKS 代理系统属性后运行；可执行 JAR 以 runtime scope 携带 PostgreSQL JDBC 驱动。这样企业打包或其它 Maven 构建即使覆盖 `target`，也不会破坏运行中 Spring Boot 对尚未加载类的读取。直接使用 Maven 或 IDEA 启动时，如果本机开启了全局 SOCKS/HTTP 代理，需要在 VM options 中显式清空同类 `-D*proxy*` 参数。

需要联调运营分析时，如果 dotenv 已启用 ClickHouse 并精确指向项目托管的本机
`127.0.0.1|localhost|[::1]:18123/testagent_analytics`，macOS/Linux 根目录重启脚本会自动启动固定版本
ClickHouse 26.3.17.56，并用 helper 生成的同一套凭据启动 Java；其它地址不会被接管。dotenv 未启用时可显式增加
`--with-clickhouse` 强制进入该模式。HTTP 只监听回环端口 `18123`，数据保存在版本化 Docker volume；随机密码和
Java JDBC 配置只写入 `.tmp/dev-services/clickhouse` 的 `0600` 文件，不修改 dotenv。Java 启动时会执行既有
ClickHouse schema migration。

Windows 开发人员若只需要 legacy guo profile，可直接使用已提交的 IDEA 运行配置 `TestAgentApplication guo`：

1. 用 IDEA 导入 `backend/pom.xml`。
2. 选择 Run Configuration `TestAgentApplication guo`。
3. 使用 JDK 21+ 启动。

该配置通过 `-Dspring.profiles.active=guo` 读取 `test-agent-app/src/main/resources/application-guo.yml`，不依赖 shell 启动脚本或 `.env.local`。`guo` profile 已内置 Java 进程需要的数据库、Redis、opencode、manager token、模型来源和模型 key 配置；`TEST_AGENT_OPENCODE_BIN`、`TEST_AGENT_START_OPENCODE` 等只服务于根目录启动编排脚本，不属于 Java 进程配置。当前本地联调默认改用 `test` profile 和 `.env.test`；Windows 用户要连同一测试环境时，可在 PowerShell 中执行 `powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Profile test -EnvFile .env.test`，WSL/Git Bash 中继续使用 `./restart-dev-services.sh --profile test --env-file .env.test`。仅启动 Java 后端时，仍可在 IDEA/PowerShell 中显式导入 `.env.test` 的数据库、Redis、模型和 `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 等变量，并用 `-Dspring.profiles.active=test` 启动 Java 后端。
`test` profile 会直接允许本机原生客户端使用 loopback 明文 WebSocket 联调，无需额外导出
`TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL`。其它 profile 默认仍拒绝明文；当前企业固定 HTTP 入口是已批准例外，
需要在客户端包与两台 Java 同时显式启用，入口升级 TLS 后恢复 HTTPS/WSS 默认值。

需要同时联调 LobeHub 时，macOS/Linux 从仓库根目录显式执行
`./restart-dev-services.sh --profile test --env-file .env.test --with-lobehub`。默认不启动 LobeHub；该模式从
同级 `../lobehub-platform` 启动独立 dev server，开发密钥只写入 `.tmp/dev-services/lobehub-dev.env`，不修改
`.env.local/.env.test`；fork 的 loopback scheduler 由同一 helper 独立启动和回收。显式模式还会在后端启动时
初始化同一本机回环 PostgreSQL 中的四项 LobeHub 公共参数并留下修改审计，配置项完成后才把
`LOBEHUB_ENABLED` 设为 `true`。平台数据库不是回环地址时会拒绝启动，防止误改共享库；未显式指定 owner 时
必须恰好能自动找到一名状态正常、部门非空的超级管理员，否则可在命令前设置
`TEST_AGENT_LOBEHUB_DEV_OWNER_UNIFIED_AUTH_ID=<统一认证号>` 明确本地 owner。owner 解析、参数审计或 LobeHub
启动失败时会补偿关闭入口，避免残留可点击但不可用的“通用问答”。

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
| `TEST_AGENT_DB_URL` / `TEST_AGENT_DB_USERNAME` / `TEST_AGENT_DB_PASSWORD` | 平台 PostgreSQL 连接信息；个人离线 Compose 默认使用 `127.0.0.1:15432/test_agent`。 |
| `TEST_AGENT_REDIS_HOST` / `TEST_AGENT_REDIS_PORT` / `TEST_AGENT_REDIS_PASSWORD` | Redis 连接信息，绑定到 Spring 标准 `spring.data.redis.*`；Redis 是系统必需依赖。 |
| `TEST_AGENT_REDIS_SUMMARY_ENABLED` / `TEST_AGENT_REDIS_SUMMARY_ROLLOUT_PERCENTAGE` | Redis summary 运行模式开关和按 userId 稳定哈希的灰度比例，默认 `false/0`；开启后仅影响携带有效 `contextToken + clientRequestId` 的新 Run，活动 Run 不切换模式，回滚时把比例调回 `0`。 |
| `TEST_AGENT_XXL_JOB_ENABLED` | 是否启动进程内 XXL Admin 与 executor；生产默认开启。 |
| `TEST_AGENT_XXL_JOB_MYSQL_*` | 独立 XXL MySQL JDBC URL、账号和密码；不得指向平台 PostgreSQL。 |
| `TEST_AGENT_XXL_JOB_ACCESS_TOKEN` | Admin 与所有 executor 共用的独立 access token。 |
| `TEST_AGENT_XXL_JOB_ADMIN_PORT` / `TEST_AGENT_XXL_JOB_EXECUTOR_PORT` | 当前 Java 的本机 Admin/executor 端口；每台 Linux 只部署一个 Java。 |
| `TEST_AGENT_XXL_JOB_COOKIE_SECURE` | XXL SSO Cookie 是否带 `Secure`，默认 `true`；只有受控内网固定 HTTP 入口才允许显式设为 `false`。 |
| `TEST_AGENT_OPENCODE_BASE_URL` | 本地脚本判断是否启动 opencode-manager 和端口池的地址，不再作为 Java 固定 opencode node 配置。 |
| `TEST_AGENT_LINUX_SERVER_ID` | 稳定 Linux 服务器身份，可使用 `server-a`、`prod_01`、`10.1.2.3` 等 1-128 位标识；缺失时使用 Java 主机名。 |
| `TEST_AGENT_DEPLOYMENT_MODE` | 部署模式：`external`（外部部署，默认）或 `internal`（企业内部部署）。 |
| `TEST_AGENT_TCDS_BASE_URL` | TCDS HTTP/HTTPS 基础地址；默认使用现场确认的企业局域网入口 `http://tcds-prod.sdc.icbc:9080`，其它环境可显式覆盖。 |
| `TEST_AGENT_AAM_BASE_URL` | AAM HTTP/HTTPS origin，默认 `http://zfw.sdc.cs.icbc`；禁止凭据、路径、query 和 fragment，固定 `/aam/checkLogin` 由适配器追加。所有 Java 节点必须保持一致。 |
| `TEST_AGENT_AAM_CONNECT_TIMEOUT` / `TEST_AGENT_AAM_REQUEST_TIMEOUT` / `TEST_AGENT_AAM_MAX_RESPONSE_BYTES` | AAM 验真的连接超时、请求超时和响应上限，默认 `3s/5s/65536`；不自动重试。 |
| `TEST_AGENT_SKILLHUB_ENABLED` | 是否启用 SkillHub 外部 Skill 目录、显式上传/进度、下载和定时对账；应用默认 `false`，本地研发保持关闭，企业 `backend.env` 显式启用后必须同时配置基础地址和访问密钥。 |
| `TEST_AGENT_SKILLHUB_BASE_URL` | SkillHub HTTP/HTTPS 基础地址；只作为部署期外部端点注入。 |
| `TEST_AGENT_SKILLHUB_ACCESS_KEY` | SkillHub 访问密钥；敏感值，仅从部署环境注入，禁止写入 Git/YAML 默认值、普通发布包和日志；企业目标机只保存在 `0600` 的敏感 `backend.env`。 |
| `TEST_AGENT_SERVER_ADVERTISED_HOST` | 当前 Java、XXL executor 和用户 opencode server 对其它节点可访问的主机地址；缺失时统一复用现有内网 IPv4 探测。 |
| `TEST_AGENT_MODEL_CATALOG_SOURCE` | 历史兼容项。前端对话框模型/供应商目录已统一走 opencode 原生 `/api/model`、`/api/provider`，不再从数据库模型目录读取。 |
| `EXTERNAL_API_KEY` | 外部 OpenAI-compatible API Key；变量名可通过 `TEST_AGENT_EXTERNAL_MODEL_API_KEY_ENV` 改为其他环境变量名。 |
| `MODELSTUDIO_API_KEY` | `TEST_AGENT_MODEL_CATALOG_SOURCE=bailian` 时使用的 Model Studio API Key；该模式使用代码内置 `modelstudio` provider 和 qwen/kimi 模型清单。 |
| `TEST_AGENT_INTERNAL_PROXY_API_KEY` | Java 内部模型代理鉴权 apikey；Java 校验 opencode 子进程请求，manager 启动用户 opencode server 时把同值注入子进程环境。该 Key 只用于 OpenCode → Java 的 `Authorization: Bearer`，不是企业 AI 上游供应商 Token；Java 向上游改用 `Auth-Token`，保证同一请求的 `ucid` 生效。`local` profile 未配置时按本次 JVM 启动生成临时随机值且不落盘；其它 profile 仍要求显式配置或使用自身受控测试默认值。 |
| `TEST_AGENT_LOBEHUB_HMAC_SECRET` | LobeHub 服务兑换/撤销共享 HMAC secret，至少 32 字节；不得进入公共参数或日志。 |
| `TEST_AGENT_LOBEHUB_CLIENT_ID` / `TEST_AGENT_LOBEHUB_TICKET_TTL` / `TEST_AGENT_LOBEHUB_GRANT_TTL` | 模型委托 client 与票据/委托生命周期；默认 `lobehub/60s/30d`。 |
| `TEST_AGENT_LOBEHUB_HMAC_CLOCK_SKEW` / `TEST_AGENT_LOBEHUB_NONCE_TTL` | 服务 HMAC 时钟偏差和 nonce 防重放窗口；默认 `60s/120s`。 |
| `TEST_AGENT_MODEL_GATEWAY_MULTIPART_DIRECTORY` / `TEST_AGENT_MODEL_GATEWAY_MAX_MULTIPART_PART_BYTES` | transcription 临时 part 隔离目录和单 part 上限；生产目录不得与平台/OpenCode 工作区重叠。 |
| `ENTERPRISE_OPENAI_AUTH_TOKEN` | 历史兼容项；新实现不读取该环境变量，外部 Token 由前端“内部模型供应商”页面记录到 `internal_model_tokens` 并按 Provider 关联。 |
| `TEST_AGENT_EXTERNAL_MODEL_BASE_URL` | 外部 OpenAI-compatible base URL，例如 `https://api.deepseek.com`。旧 `TEST_AGENT_BAILIAN_BASE_URL` 仍作为兼容兜底。 |
| `TEST_AGENT_ENTERPRISE_OPENAI_BASE_URL` | 企业内 OpenAI-compatible base URL，默认与 openclaw 企业 patch 中的 `enterprise-openai` 地址一致。 |
| `TEST_AGENT_ENTERPRISE_OPENAI_UCID_HEADER_NAME` | 历史兼容项；新实现固定由 opencode 配置把环境变量 `ENTERPRISE_UCID` 注入请求头 `ucid`。 |

企业 AI 上游自身支持两种供应商 Token 鉴权：`Authorization: Bearer <供应商关联 Token>` 可以完成接口鉴权，但同一请求携带的 `ucid` 不生效；`Auth-Token: <供应商关联 Token>` 才会同时让 `ucid` 生效。平台的真实代理、能力探测和可观测探活固定使用后者，不允许把内部代理 Bearer Key 与上游供应商 Token 混用。

`guo` profile 的 IDEA 启动路径已把上述本地 Java 运行参数写入 yml；继续使用 `tools/dev-backend-run.sh`、`restart-dev-services.sh --profile guo --env-file .env.local` 或 `restart-dev-services.ps1 -Profile guo -EnvFile .env.local` 时，`.env.local` 仍可覆盖 yml，便于本地联调脚本启动前后端和 opencode。根目录一键脚本不带参数时默认读取 `.env.test` 并启动 `test` profile；本仓库本机验收固定使用其中的 `192.168.8.100:15432/testagent_dev` PostgreSQL，不得主动换成本机库。只有 `TEST_AGENT_OPENCODE_BASE_URL` 指向本机或显式设置 `TEST_AGENT_START_OPENCODE_MANAGER=true` 时脚本才启动本机 Go manager，指向共享测试地址时跳过本机 manager 属于正常行为；此时所选工作区必须与用户的远端 Agent binding 位于同一 `linuxServerId`，且远端真实存在工作区根目录。停止已启动的 manager 时会清理其托管的用户 opencode 子进程和 state JSON，防止端口池残留进程导致下次初始化失败。每个稳定 `linuxServerId` 只部署一个 worker；生产和本地都不配置人工 `containerId/managerId`，Go manager 会从 `.serverid` 自动派生稳定 SHA-256 ID，hostname 只作为 `containerName` 展示。

用户专属 opencode 进程的 session/config 路径来自数据库 `common_parameters`，不是 `.env.local`。opencode 原生 session 数据目录固定为 `{OPENCODE_SESSION_DIR}/users/{unifiedAuthId}`，Java 通过用户仓储解析统一认证号，并拒绝无法作为安全路径片段的统一认证号；旧 `{OPENCODE_SESSION_DIR}/{port}` 目录不自动合并，平台历史消息仍可展示，缺失的远端 session 会在下次提问前校验并重建绑定。新会话先保留页面首条消息的临时标题；首轮 Run 成功后，平台继续监听同一远端 root session 的原生 `title` agent 完成事件，收到后读取该远端 session 的最终标题，并只在标题不是默认时间戳且平台标题仍为首条消息临时标题时同步到页面。没有临时会话、二次 title 调用或基于超时的替代命名。系统级数据根目录通过 `SYS_DATA_ROOT_DIR` 维护，默认值为 macOS `$HOME/.testagent`、Linux `/data/.testagent`、Windows `D:/data/.testagent`；Java 后端启动时写入 `SYS_DATA_ROOT_DIR/.serverid` 和 `.serverhost`，分别表示稳定服务器身份和可访问主机地址，Go manager 在连接 Java 前按同一系统参数的平台默认路径读取这两个文件。每个 manager 只连接本服务器 Java；同一服务器允许运行多个 Java，多个 Java 共享同一个 `linuxServerId`，入口 Java 会通过 `BackendJavaRouteResolver` 优先选择与目标服务器 manager 已连接的 Java，其次选择同服务器最新心跳 Java，再通过 `BackendHttpForwarder` 透传到目标 Java，由目标 Java 控制本服务器 managers。是否已分配只以 `user_opencode_process_bindings` 的 ACTIVE 记录为准；`/processes/me` 状态查询在目标后端不可用时会返回已分配但健康不可确认的 `NOT_RUNNING + serviceAddress`，初始化、Run 和 runtime 代理仍必须由目标服务器执行，不做本机降级。所有强状态查询、健康探测、状态回写和 Redis opencode heartbeat 刷新都必须走 `OpencodeProcessStatusQueryService`：先确认平台进程记录是否存在，再通过目标 Java 的本机 manager health 归一为未启动、运行中或健康检查异常。未绑定用户的精确 `GET /api/internal/agent/opencode/processes/me` 和 `POST /api/internal/agent/opencode/processes/me/initialize` 会先由入口 Java 基于同一轮 Redis manager/backend 在线快照按服务器汇总 `currentProcesses`，只在存在 READY、未满且与所选 Java 保持 CONNECTED 的服务器间选择总进程数最少者；已有 ACTIVE binding 始终优先，远端失败不自动切换服务器。目标 Java 再按本实例已连接的健康容器视图选择进程数最少且有空闲端口的目标容器，并调用 `OpencodeProcessStartupService` 向该容器对应的 manager 下发携带用户 `sessionPath` 的 `start`；新进程 `baseUrl` 使用 `.serverhost` / `TEST_AGENT_SERVER_ADVERTISED_HOST`，不再由 `linuxServerId` 拼接。该公共启动服务会先保存候选进程，再复用公共状态查询服务确认本地 state/PID 和 opencode HTTP health，默认最多等待 manager command-timeout（10 秒）让 opencode HTTP 端点 ready，只有健康后才写入 `RUNNING`、ACTIVE binding、Redis heartbeat 和兼容 `ExecutionNode`。后续所有涉及 opencode server 启动、重启后拉起或端口复用的业务入口都必须调用这套公共启动程序，不得自行实现 start、状态回写或健康确认；所有涉及 opencode server 停止或停止后状态回写的业务入口都必须调用 `OpencodeProcessStopService`，不得自行实现 stop、停止成功判定或 `STOPPED` 回写。manager 使用通过 `configRequest/configUpdate` 同步的 `OPENCODE_PUBLIC_CONFIG_DIR`，该目录下的 `opencode.jsonc` 来自公共配置 Git 库，是模型和供应商事实源；企业部署必须保证运行用户的 `~/.config/opencode` 不维护模型或供应商，最多保留空 schema 配置，避免 OpenCode 合并全局配置污染公共目录。目录存在且非空的检查只在目标 manager 所在服务器执行。目录缺失、为空、非目录或不可读时，manager 返回 `OPENCODE_UNAVAILABLE`，错误消息包含目标服务器和 manager 实际检查的配置目录，并提示联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化；Java 仅映射为统一平台错误，不在本机提前检查。本地和生产都必须启动 Go manager，不再支持 `local-direct` 或 `gateway-mode=local` 绕过。

Run 运行数据面通过 domain `RunRuntimeStore` 与 persistence `RedisRunRuntimeStore` 隔离：单 Run manifest/input/双 Stream/snapshot/scope key 使用 `{runId}` hash tag，durable `events` Stream ID 为 `${seq}-0`，durable/transient 全事件 `runtime-events` Stream ID 为 `${runtimeVersion}-0`，snapshot 使用 Hash + order ZSET 保留当前物化状态。`REDIS_SUMMARY` 下每条事件不访问 PostgreSQL；终态事件 Lua 同时发布 versioned 关系型投影 outbox，启动和 5 秒恢复扫描在 ack 前复用服务器恢复索引补做幂等终态事务，覆盖 Java 在 Redis terminal append 与 PostgreSQL project 之间退出的窗口。SSE 首帧总是发送完整 `run.snapshot.reset`，然后由最短 5 秒的 Redis 安全扫描和本机 live bus 只唤醒按 `runtimeVersion` 分页读 Redis 尾流，live 事件仍即时唤醒但帧本身不直接输出；容量换代导致游标过旧时再次发送 reset。legacy 仍以 PostgreSQL 为事实源并保留旧轮询恢复。生产 Redis 的持久化、安全和容量要求见 `docs/deployment/backend.md`。

运行管理中的 Java 后端快照按 `backendProcessId` 写入 Redis，并按 `linuxServerId` 分组选择目标 Java；`linuxServerId` 表示稳定服务器身份，不再要求是 IP。Java 快照携带 Spring Boot build-info 构建时刻格式化的 `buildVersion`，manager 快照携带 Go linker 注入的同名字段，均使用北京时间 `VyyyyMMdd.HHmmss`；旧快照缺字段时为空，不使用启动时间补值。超级管理员在运行管理页重启/停止 opencode server 时，入口 Java 会先按统一 resolver 定位 `containerId` 所属服务器，目标不是当前 Java 或同服务器选中 Java 时转发到目标 Java，再由目标 Java 控制本服务器 manager；已有平台进程记录的重启先走公共停止服务，再用进程记录里的 `sessionPath` 走公共启动服务，无平台记录的无主端口才保留 manager `restart` fallback。公共配置管理页同样按稳定 `linuxServerId` 合并服务器视图。

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

平台数据库由 Spring Boot 唯一 Flyway Bean 按默认顺序迁移。工具盒子已知历史包括旧 `V20260727203500`、企业正式 `V20260728160800/-1966404877` 和当前版本的 `-74327385` 幂等误发变体；分支合并产生的 QA Memory、体验工作区和本地客户端低版本缺口也由同一装配按已执行 version/checksum 选择隔离兼容资源或更高前向 migration。本地客户端对尚未越过 `20260812202425` 的旧 release history 使用原前向版本，对已经执行自动化代码库或 SCM 等更高版本的企业 history 使用 `20260818094330` 企业前向版本。空库和正常主链只解析原始 migration，未知 checksum 或主/前向路径混用失败关闭，不使用 `outOfOrder`、`repair` 或手工历史表修改。

## 后续 AI 编码指引

- 新增可部署入口只允许放在 `test-agent-app`。
- 新增业务文件前先列出现有合适工程；无合适工程时按业务边界新建 Maven module。
- `test-agent-app` 只放启动、装配、profile、migration 和 health 等运行入口，不放 Controller 或业务服务。
- HTTP/SSE/WebSocket 入口放在 `test-agent-api`，旧 `/api/...` URL 默认保留，明确作废的入口除外；新 URL 同步写入 `docs/api/http-api.md`。
- Workspace、文件、git/diff、设置页初始版本工作区创建、应用版本工作区、个人工作区、应用引用资产库副本、应用源码固定提交物化/打开/清理、agent、skill 管理业务放在 `test-agent-workspace-management`。
- Workspace 与 Agent 配置的新文件上传统一走平台文件 WebSocket 的 begin/chunk/complete/abort 分片会话：应用层不限制文件总大小，单片默认 256 KiB。UTF-8 一次性读取和文本编辑默认阈值为 5 MiB，超出后使用约 512 KiB 分段的渐进只读预览，可按需读取到 EOF；上传完成前只写隐藏临时文件，连接关闭、取消或失败必须清理，不得新增 HTTP 文件代理或整文件内存缓冲。
- 工作区 `workspace.move` 保持既有文件 WebSocket RPC 契约并整体移动普通文件或非空目录；Linux 通过 JNA 直接调用内核 `renameat2(RENAME_NOREPLACE)`（兼容 Alpine/musl 未导出包装函数），macOS 调用 `renameatx_np(RENAME_EXCL | RENAME_NOFOLLOW_ANY)`，Windows 使用源条目句柄与目标父目录句柄的 `SetFileInformationByHandle`。三者都执行一次不覆盖的原子重命名并阻断校验后的路径替换竞态，缺少等价原子能力的平台失败关闭。
- 多 agent 运行时接口、`agentId` 选择、日志/指标包装和具体 agent 适配器放在 `test-agent-agent-runtime`。
- Session、Run、RunEvent、夜间任务提交/投递/补偿、agent runtime 调用、Diff/revert、terminal 业务放在 `test-agent-opencode-runtime`。
- 通用站内通知生命周期、未读口径、用户级实时变化和清理任务放在 `test-agent-notification`；具体业务模块只作为通知生产者，HTTP/SSE DTO 和 MyBatis/Flyway 分别放在 `test-agent-api`、`test-agent-persistence`。
- Model/Provider 目录始终由 opencode 配置文件决定；内部模型代理和 `<think>` 流式转换放在 `test-agent-opencode-runtime` / `test-agent-api`，内部供应商、可复用 Token 定义及关联端口放在 `test-agent-domain`，管理编排放在 `test-agent-configuration-management`，MyBatis/Flyway 实现放在 `test-agent-persistence`。Token 值来自外部系统，平台只记录，不新增生成能力。
- 新增或修改关系型数据库 SQL 必须放在 `test-agent-persistence` 的 MyBatis XML mapper 中；存量 `Jdbc*Repository` 只保留迁移窗口，不承接新 SQL。
- 涉及 opencode-manager 路由、Java 到 manager 控制、用户 opencode 进程服务器归属、运行管理 `containerId` 路由、Agent 配置或文件 WebSocket 目标后端选择时，必须复用 `BackendJavaRouteResolver`、`BackendHttpForwarder` 和目标 Java 的 `OpencodeProcessManagerGateway` 公共链路；禁止新增自写 Redis 快照扫描、Java->Java HTTP 转发、防循环 header、本机降级或本地绕过。涉及 opencode server 启动、停止或状态查询时，分别复用 `OpencodeProcessStartupService`、`OpencodeProcessStopService` 和 `OpencodeProcessStatusQueryService`。
- 用户、角色、权限等平台内部管理放在 `test-agent-system-management`。
- 应用配置、应用人员、代码库英文名与关联、应用工作空间模板和个人 SSH key 管理放在 `test-agent-configuration-management`；应用版本工作区运行编排和工作空间创建进度放在 `test-agent-workspace-management`。
- 周期任务的 XXL Admin、executor、MySQL Flyway、iframe SSO 与统一 handler 适配放在 `test-agent-xxl-job-integration`；XXL executor 本身不携带稳定 Linux 亲和。`test-agent-scheduler` 只提供 `ScheduledTaskHandler`、context/result、Redis 锁和历史清理能力。定时任务直接保存在 `night_execution_tasks`，支持标准夜间窗口与仅 `SUPER_ADMIN` 可用的未来 24 小时精确分钟测试模式；XXL 每分钟扫描并通过公共 Java 路由转发到任务创建时固定的目标服务器。目标 Java 复用普通 Run 受理链路，不建立夜间专属队列，自定义模式不占夜间容量。
- 非 opencode 外部系统联动放在 `test-agent-integration`。独立 UI 平台的单次 UI 执行由 OpenCode Tool 使用超级管理员维护的地址直连，不经过 Java 后端；每次执行的被测系统环境仍由用户输入或父 Agent 从用户指定路径读取后显式传入，平台不配置默认被测环境。
- 工具盒子目录固定从 `test-agent-integration` 的版本化 classpath JSON 读取，当前离线口径为 193 项；点击明细、累计和用户 30 秒窗口通过 `test-agent-domain` 端口与 `test-agent-persistence` MyBatis XML 实现，API 层只做登录主体、traceId 和 DTO 转换。
- 业务模块不要直接依赖 `test-agent-opencode-sdk-generated`，应通过 `test-agent-opencode-client`。
- 领域模型保持在 `test-agent-domain`，不要依赖 Spring Web 或持久化技术。
- 对外成功/错误响应使用 `test-agent-common` 的 `ApiResponse` 和 `ApiErrorResponse`。
- HTTP 入口 traceId 使用 `X-Trace-Id`，由 `test-agent-observability` 和 `test-agent-api` 协作生成或透传。
- 后端运行态使用 Log4j2 作为 SLF4J 实际绑定，默认控制台日志为 `key=value` 结构化格式并输出 traceId；运行文件日志写入 `logs/backend.log`，SSE 相关日志额外写入 `logs/sse.log`，`ERROR` 及以上日志额外写入 `logs/error.log`。
