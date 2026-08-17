# Session Log - mpghdwad

## 2026-08-11 - Windows 本地开发依赖盘点、安装与启动验证

### Why

- 用户要求探索项目、尝试在 Windows 本地启动，并自行补齐缺失依赖，软件优先安装到 `E:\software`。
- 当前 shell 的 `JAVA_HOME/PATH` 命中 Java 8，仓库没有 `.env.test/.env.local`，Docker、`uv`、`sqlite3`、PostgreSQL/Redis 客户端均不在 PATH；标准文档提到的 `restart-dev-services.ps1` 也不存在，仓库只有历史文件 `win-restart-dev-services-fixed-v4.ps1`。

### What

- 复用已有 `E:\software\jdk-21`、`E:\software\apache-maven-3.9.16`、Node 24、Go 1.26、Python 3.12 和 `E:\software\uv-0.12.3`，仅在命令进程内覆盖 Java、Maven、uv、Go 与 pytest 缓存/临时目录，没有修改机器级环境变量或 `.env*`。
- 从 Docker 官方固定构建地址下载 Docker Desktop 4.84.0（build 234817），官方 SHA-256 与 Authenticode 签名均校验通过；以 WSL 2、仅 Linux 容器方式安装到 `E:\software\DockerDesktop`，WSL 数据目录配置为 `E:\software\DockerDesktopData`。
- 从 SQLite 官方下载 3.53.4 Windows x64 工具，按官方 SHA3-256 校验后安装到 `E:\software\sqlite-3.53.4`。
- 安装前端 lockfile 依赖、workflow/runner Python lockfile 依赖和 Go module 依赖；Go 官方代理 IPv6 不可达时临时使用 `goproxy.cn`，依赖仍由 `go.sum` 校验。
- 启动前端 Vite 开发服务，`http://127.0.0.1:3000/` 返回 HTTP 200；服务日志位于 `.tmp/dev-services/frontend-standalone*.log`。
- Docker Desktop 许可确认后 engine 正常启动；`deploy/local/docker-compose.yml` 的 PostgreSQL 16（15432）、Redis 7.4.9（16379）和 MySQL 8.4（13306）均已启动并保持 healthy。
- 全新测试库完成 94 个 Flyway migration。Windows 默认通用参数 `SYS_DATA_ROOT_DIR=D:/data/.testagent` 在当前账户不可写，因此仅在本地测试容器数据库中将 Windows 值调整为 `E:/git/intelligent-test-agent/.tmp/dev-services/data`；未修改 migration、源码或 `.env*`。
- 已启动 Java 后端和 Go manager：后端 readiness/liveness 均为 HTTP 200/`UP`，manager 已连接后端 WebSocket 并成功应用下发配置。

### How

- 后端以 JDK 21 执行 `mvn clean package -DskipTests`，21 个 reactor module 全部 `SUCCESS`，可执行 JAR 成功生成。
- 前端 `corepack pnpm install --frozen-lockfile`、production build 和全 workspace typecheck 通过；Vitest 为 1891 passed / 1 skipped / 9 failed，失败包括 Windows 符号链接权限、路径分隔符、Markdown/canvas DOM 差异以及首次执行时缺少 sqlite3。安装 sqlite3 后定向复测为 132 passed / 4 failed，`spawn sqlite3 ENOENT` 已消失，剩余均为 Windows 路径归属/分隔符和符号链接权限差异。
- `workflow-service` 完成 `uv sync --frozen`；Docker 就绪后全量 pytest 110 项全部通过，仅有沙箱无法创建 `.pytest_cache` 的非功能性警告。
- `runner-controller` 完成 `uv sync --frozen`；将 pytest 临时目录移到仓库后为 63 passed / 18 failed，失败集中在 Linux 专用目录文件描述符、POSIX 权限和 `/data`、`/run` 路径语义。
- `opencode-manager` Windows 可执行文件构建成功并实际保持运行、完成 WebSocket 注册；全量 Go 测试在 Windows 因 POSIX 路径、0755 权限、符号链接权限及 Linux/Darwin 配置夹具失败，不是编译失败。
- 最终检查确认 3000、8080、13306、15432、16379 均在监听；前端 HTTP 200，后端 readiness HTTP 200，manager 到 8080 的连接为 ESTABLISHED。

### Result

- Java、前端、Go、Python 构建依赖已补齐；会话结束时前端 3000、后端 8080、Go manager 和三项 Compose 基础设施均在线，日志及 PID 文件位于 `.tmp/dev-services/`。
- 没有修改业务代码、API、事件、数据库结构、generated SDK、OpenCode 源码或 `.env.local`；无需同步稳定 API、事件或数据库文档。本条本机 session log 是唯一计划提交的仓库内容。
- 不直接执行 `win-restart-dev-services-fixed-v4.ps1`：该历史脚本与稳定文档文件名不一致，且包含兜底清理任意 `java.exe/node.exe/opencode.exe` 的宽范围逻辑；本次改用逐组件、可验证的启动方式，未修改脚本。
- 当前 Windows shell 同时包含 `PATH` 与 `Path` 两个环境项，直接 `Start-Process` 会报重复键；需要在子 PowerShell 进程中合并为单个 `Path` 后再启动后台进程。Maven/Go/uv 默认用户缓存路径在 Codex 沙箱不可写，需继续定向到仓库 `.tmp` 或使用非沙箱命令。
- 前端与 Go/runner 的剩余测试失败属于测试对 POSIX/浏览器环境的假设；若要求 Windows 全绿，需要单独处理跨平台兼容性。

## 2026-08-12 - 生成本机 local profile 与外部 Claude 模型配置

### Why

- 用户提供了本地 OpenAI-compatible 模型服务地址、账号和 API Key，要求按实际本地依赖生成 `.env.local`。

### What

- 按 `.env.local.example` 生成被 `.gitignore` 排除的根目录 `.env.local`，配置 JDK 21、本地 Compose PostgreSQL/Redis/XXL MySQL、前后端地址、稳定服务器身份、Go manager、OpenCode 二进制以及 external 模型目录。
- external provider 使用 `http://192.168.8.100:8090/v1` 和 Bearer Key；账号由上游 Key 绑定，不额外写入请求 Header。密钥仅保存在本机忽略文件和运行进程环境，未写入本日志、源码或提交。
- 由于当前机器连接该地址的 HTTP/HTTPS 均超时，未猜测具体 Claude 模型 ID，保持默认模型为空，待 `/v1/models` 可达后动态加载并在页面选择。

### How

- 校验 `.env.local` 必填键、无占位符且被 `.gitignore` 命中；使用 JDK 21 按 `local` profile 重启 Java 后端和 Go manager。
- 验证后端 readiness HTTP 200，前端 HTTP 200；后端 `8080`、XXL executor `9999`、XXL Admin `18080` 均监听，manager 到后端 `8080` 的 WebSocket TCP 连接已建立；PostgreSQL、Redis、XXL MySQL 三个 Compose 容器均为 healthy。

### Result

- 本地 local profile 与模型供应商配置已经落地并生效，未修改业务代码、API、事件、数据库结构、migration、generated SDK、OpenCode 源码或稳定文档。
- 模型服务网络连通仍是唯一阻塞；恢复到 `192.168.8.100:8090` 的路由/防火墙后，才能验证 `/v1/models`、实际模型 ID 和真实推理。

## 2026-08-12 - Windows local 一键启动入口

### Why

- 用户希望在 Windows 本地直接双击 BAT 启动项目，不再手动执行多条后端、manager、前端和 Compose 命令。

### What

- 新增根目录 `start-local.bat` 与 `restart-dev-services.ps1`，固定读取 `.env.local` 并启动本地 Compose、Java 后端、Go manager 和 Vite 前端。
- 支持默认完整构建、`--quick` 复用既有前后端构建产物、`--check` 只做环境检查；Workflow、Memory 与 LobeHub 保持独立启动。
- 进程停止同时校验 PID 文件、可执行文件和命令行；manager 使用已校验 PID 回收进程树，不按端口或进程名批量清理。

### How

- 使用 Windows PowerShell 5.1 解析器检查脚本语法，并执行 `start-local.bat --check`。
- 在正常桌面权限下执行 `start-local.bat --quick` 完成真实重启；后端 readiness 与前端均返回 HTTP 200，XXL Admin 返回预期的 401 鉴权响应。

### Result

- Windows 用户现在可双击 `start-local.bat` 完整启动项目；日志和 PID 文件位于 `.tmp/dev-services/`。
- 已同步研发流程、前端 README 和后端部署文档；不涉及 API、事件、数据库结构、性能、安全协议或对外兼容性变更。

## 2026-08-12 - 切换本机 Codex/OpenAI 模型配置

### Why

- 用户提供新的 Codex/OpenAI-compatible Key 与 Base URL，要求替换此前本机 Claude 供应商配置。

### What

- 仅修改被 Git 忽略的 `.env.local`：外部供应商改为 `codex-openai` / `Codex / OpenAI`，Base URL 使用用户提供的 `/v1` 地址，并替换 `EXTERNAL_API_KEY`；密钥未写入源码、日志或提交。
- 保持默认模型为空，由服务可达后的 `/models` 目录动态提供真实模型 ID，避免猜测模型名。

### How

- 校验外部供应商配置键完整、Base URL 精确匹配、Key 格式有效且 `.env.local` 被 `.gitignore` 排除。
- 按 JDK 21 执行 `start-local.bat --quick`，重启 Compose、后端、manager 与前端；后端 readiness 和前端均为 HTTP 200，manager 正常运行。

### Result

- 新 Codex/OpenAI 配置已由本地进程继承；携带密钥访问局域网 `/models` 的主动验证未获执行授权，因此上游模型目录和推理仍未实测。
- 不涉及业务代码、API、事件、数据库结构、migration、generated SDK 或 OpenCode 源码变更。

## 2026-08-12 - 定位本地默认账号登录失败

### Why

- 登录页自动填充 `888888888 / 123456`，但用户使用该明文密码登录仍返回“用户名或密码错误”。

### What

- 后端日志确认失败发生在按用户名查询阶段，尚未进入 BCrypt 密码校验。
- 只读查询本地 PostgreSQL 后确认 `users` 表为 0 条记录，`888888888` 不存在；前端自动填充值不是当前数据库中已初始化的真实账号。
- 正式 V5 migration 只创建用户/认证表和角色字典，V8 只为已存在的默认用户授权；历史 V17 测试 fixture 不属于生产 Flyway，且其密码字段仅为测试占位值。

### How

- 对照 `LoginView.vue`、`AuthApplicationService`、`UserDomainService`、用户 migration 与稳定数据库文档，并通过本地 Compose PostgreSQL 执行只读计数查询。

### Result

- 当前本地库没有可用登录账密；登录接口接收明文密码并由后端做 BCrypt 校验，不能把数据库哈希作为密码输入。
- 诊断阶段未修改数据库；随后按用户明确要求完成本地账号初始化，结果见下条记录。

## 2026-08-12 - 初始化本地超级管理员并恢复工作台加载

### Why

- 用户要求创建 `888888888 / 123456` 最高权限账号；登录成功后又遇到 `WorkbenchView.vue` 动态模块加载失败。

### What

- 在本地 Compose PostgreSQL 的 `test_agent` 数据库中创建/修复 `888888888` 用户，状态设为 `ACTIVE`，使用 Spring Security BCrypt 保存密码哈希，并只授予 `SUPER_ADMIN`。
- 定位到登录跳转后 Vite 8 在首次优化依赖时以 Windows 异常码退出；删除可自动重建的 `frontend/apps/agent-web/node_modules/.vite` 优化缓存并按 JDK 21 快速重启服务。

### How

- 用户写入与角色替换在单个 PostgreSQL 事务中完成；调用 `POST /api/auth/login` 使用明文密码验证成功，返回 `SUPER_ADMIN` 且 Token 正常签发。
- 重启后连续两次请求 `/src/views/WorkbenchView.vue` 均返回 HTTP 200，`/@vite/client` 返回 200，Vite 完成依赖优化后前端启动进程仍然存活。

### Result

- 本机可用 `888888888 / 123456` 登录并拥有最高权限；刷新旧浏览器页面后可重新加载工作台。
- 仅修改本机开发数据库和可重建缓存，不涉及业务源码、API、事件、数据库结构、migration、generated SDK、OpenCode 源码或环境配置。

## 2026-08-12 - 041 测试设计案例维护与缓存跳转

### Why

- `041-测试设计` 文件标签的“缓存并跳转”需要先从当前编辑器 Markdown 生成案例列表，让用户为每条案例选择一个或多个任务类型，维护到生产 TCDS 后继续原缓存跳转。
- 生产 TCDS 地址为 HTTP 内网服务，浏览器直连会违反前端访问边界，并存在 CORS 与 HTTPS mixed-content 风险。

### What

- 新增案例维护弹窗和 Markdown 四列表格解析，支持逐条多选、勾选后批量赋值、未选择校验；同一案例选择任务类型 `2`、`3` 时展开为两个 `caseList` 对象。
- `041` 使用当前编辑器内存内容打开弹窗并在维护成功后继续缓存跳转；`042-测试执行` 保持原逻辑。
- 新增受认证的 `POST /api/internal/platform/integration/tcds/test-cases`，由后端固定生产地址、`toolId`、`method=文本理解生成法` 和其余业务字段，并从 `AuthPrincipal.unifiedAuthId` 获取 `userId`。
- TCDS 客户端禁用重定向，设置连接/请求超时，将响应读取限制为 512 KiB；API 访问日志只记录 `itemNo/caseCount`，不记录案例正文。

### How

- 前端四个目标测试文件 123 项通过，覆盖 Markdown 解析、任务类型展开、弹窗批量操作、`041/042` 图标与 backend-api 调用；agent-web `vue-tsc` 和 Vite 生产构建通过。
- 后端 `TcdsCaseMaintenanceServiceTest` 4 项通过；`TcdsCaseMaintenanceControllerTest` 与 `ApiLoggingAspectTest` 共 24 项通过。
- 真实页面使用示例 `041` Markdown 验证解析出 11 条案例及弹窗交互；未点击确定，因此未向生产 TCDS 写入数据。

### Result

- 用户可在 `041` 当前编辑内容上选择或批量设置任务类型，全部选择后由平台后端维护生产 TCDS，成功后沿用原缓存与跳转流程。
- 已同步前后端 README/PACKAGE、HTTP API、事件流无变更说明、模块图和安全规范。
- 新增 HTTP API 与外部 HTTP 调用；未变更 RunEvent、数据库、Flyway、WebSocket、generated SDK、OpenCode 源码或环境配置。生产 TCDS 的真实写入仍需用户重启服务后自行验收。

## 2026-08-13 - 修正 TCDS 多任务类型报文

### Why

- 用户确认同一案例的多个任务类型不能拆成多个 `caseList` 对象，也不能发送 `subItemTypes.value` 数字编码；TCDS 要求单个 `taskType` 字符串以英文逗号连接业务名称，例如 `准入,功能测试`。

### What

- 下拉仍展示内网任务类型接口的完整 `subItemTypes.name`，提交时通过显式映射转换为 Excel 接口文档规定的十个 TCDS 业务名称。
- 每个 Markdown 案例只生成一个 `caseList` 对象，多选结果去重后按选择顺序使用英文逗号连接。
- 前后端改为逐项校验业务名称；拒绝数字编码、未知名称和中文逗号，避免非法任务类型进入生产 TCDS。

### How

- 前端组包、弹窗和 backend-api 目标测试 117 项通过，agent-web `vue-tsc` 类型检查通过。
- 后端 `TcdsCaseMaintenanceServiceTest` 4 项、`TcdsCaseMaintenanceControllerTest` 2 项串行通过；并发启动两个 Maven reactor 曾因共享 `target` 竞争导致测试类暂时不存在，串行复跑确认代码与断言通过。
- 重新核对 `mimo维护案例接口.xlsx` 的 `taskType` 说明，确认合法值为 `自定义/安全/业务风险防控/功能测试/验收/准入/灰度/投产验证/非功能性/验收准入`。

### Result

- 选择准入和功能测试后，请求中只有一个案例对象，字段为 `"taskType":"准入,功能测试"`。
- 已同步前端 README、模块 README、HTTP API 和安全规范。该修改纠正上一提交中尚未生产验收的新 API 请求契约；未变更 URL、响应、RunEvent、数据库、Flyway、WebSocket、generated SDK、OpenCode 源码或环境配置，未重启服务，也未向生产 TCDS 写入数据。

## 2026-08-13 - 修复 local profile 初始化缺少内部模型代理 Key

### Why

- 本机后端以 `local` profile 启动且 `.env.local` 未配置 `TEST_AGENT_INTERNAL_PROXY_API_KEY`，用户初始化 TestAgent 时在公共启动程序组装子进程环境阶段失败，返回 `OPENCODE_UNAVAILABLE`。
- 该 Key 只用于同一 Java 与其创建的用户 OpenCode 子进程之间的内部代理鉴权，本地开发不应要求把固定敏感值写入 dotenv；生产仍需显式配置并失败关闭。

### What

- 新增 `application-local.yml`：仅在 `local` profile 且没有显式环境值时使用 Spring `random.uuid` 生成本次 JVM 生命周期内的临时 Key；显式环境值优先，默认/生产 profile 不获得该兜底。
- 增加配置绑定回归，覆盖 local 自动生成、local 显式覆盖和默认 profile 不生成三条边界。
- 同步 app README、后端 README 和后端部署文档，说明随机值不落盘、重启轮换及生产配置要求。

### How

- JDK 21 下运行 `TestAgentRuntimePropertiesBindingTest`，17 项全部通过；执行 22 模块 `-DskipTests package` 成功。
- 使用 Windows 本地重启脚本以 `local` profile 重启后端、manager 和前端；后端 readiness 与前端均为 HTTP 200。
- 使用本地测试账号登录并调用 `POST /api/internal/agent/opencode/processes/me/initialize`，返回 `success=true`、`status=READY`、端口 `4096`，新日志不再出现“内部模型代理 apiKey 未配置”。

### Result

- `.env.local` 缺少内部代理 Key 时，本地 TestAgent 初始化可正常完成；临时 Key 不写入文件或日志，生产行为不变。
- 不涉及 HTTP API/DTO、RunEvent、数据库、Flyway、性能、generated SDK 或 OpenCode 源码变更；安全影响限于缩小到 local profile 的启动期随机鉴权默认值。

## 2026-08-13 - 对齐 getCacheByKey 的 Markdown 案例解析

### Why

- `041-测试设计` 的“缓存并跳转”此前只读取首个固定四列表格，不能覆盖 TCDS `getCacheByKey` 已支持的八列表格和 `## 测试案例 N` 分段文本。

### What

- 前端从当前编辑器内存正文生成弹窗案例时，按“四列表格、八列表格、分段文本”的既有 TCDS 优先级解析；高优先级格式存在时不混入低优先级格式。
- 四列和八列格式读取全部匹配表格并补齐 `[AI]` 前缀；分段文本提取案例名称、测试步骤、测试数据，并将接口返回验证与数据库验证组合为预期结果。

### How

- `test-case-maintenance.test.ts` 8 项通过，覆盖四列表格多段、八列表格映射、分段文本、格式优先级和空高优先级格式不降级。
- agent-web `vue-tsc --noEmit --pretty false` 类型检查通过。

### Result

- 按钮仍使用当前编辑器内容打开原弹窗，提交和跳转链路不变；仅扩展弹窗数据转换兼容性。
- 已同步前端根 README 和 agent-web README；不涉及 HTTP API、RunEvent、数据库、Flyway、WebSocket、安全边界、generated SDK、OpenCode 源码或环境配置。

## 2026-08-13 - TCDS 任务类型改为内网实时查询

### Why

- `041-测试设计` 案例维护弹窗此前使用 `getTaskTypes.data.subItemTypes` 的前端固定快照，准备进入内网测试后需要读取生产 TCDS 当前任务类型。

### What

- 新增登录用户平台接口 `GET /api/internal/platform/integration/tcds/task-types`，由 `test-agent-integration` 使用无请求体 GET 调用固定生产地址 `http://tcds-prod.sdc.icbc/task/getTaskTypes`。
- 后端只返回经过数量、长度、控制字符和重复值校验的 `subItemTypes.name/value`；禁止重定向并保留 10 秒连接超时、30 秒请求超时和 512 KiB 响应上限，异常统一收敛为 `EXTERNAL_API_UNAVAILABLE`。
- 前端移除任务类型选项快照，每次打开弹窗实时加载；加载中禁止选择和确认，失败显示错误与重试且不降级。已确认的十个 `value -> createGraphCase.taskType` 业务名称映射和后端白名单继续保留。

### How

- Vitest 定向运行 3 个文件共 124 项全部通过；agent-web `vue-tsc` 和 backend-api `tsc` 类型检查通过。
- 后端测试补充 GET 地址/方法/无 body、字段提取、非法与重复响应、认证和匿名拒绝覆盖；沙箱外离线 Maven 定向 reactor 构建成功，`TcdsCaseMaintenanceServiceTest` 6 项、`TcdsCaseMaintenanceControllerTest` 4 项全部通过。

### Result

- 内网部署后，浏览器只访问平台 API，Java 所在网络实时访问 TCDS；`createGraphCase` 地址、报文、固定 `toolId` 和成功后缓存跳转均未改变。
- 已同步前后端 README、包说明、HTTP API、事件文档、模块图和安全规范；新增只读 HTTP API，不涉及数据库、Flyway、RunEvent、SSE、WebSocket、性能模型、generated SDK、OpenCode 源码或环境配置。本次按用户指令未重启服务、未调用真实 TCDS、未执行 Git 命令。

## 2026-08-14 - TCDS 任务类型取消固定映射

### Why

- `createGraphCase.taskType` 不再按代码内十类表映射；权威关系改为实时 `getTaskTypes` 返回的 `name` 删除末尾精确后缀“测试任务”。
- 前端展示仍必须保留接口返回的完整名称，只有提交给案例维护接口的业务名称不携带该后缀。

### What

- 前端组包函数接收本次弹窗实时加载的 `name/value`：下拉继续展示完整 `name`，确认时按所选 `value` 查找对应项并删除末尾“测试任务”，多选结果使用英文逗号连接；删除固定 `TASK_TYPE_REQUEST_NAMES`。
- 后端删除固定十类白名单和 DTO 固定枚举正则；提交 `createGraphCase` 前重新调用实时 `getTaskTypes`，按同一后缀规则生成当前允许集合并校验浏览器报文。
- 上游名称缺少后缀、删除后为空或转换后重名时拒绝；客户端篡改或类型已下线时不调用 `createGraphCase`。
- 同步前后端 README/PACKAGE、HTTP API、模块图和安全规范，明确界面展示与上游提交的名称差异。

### How

- 前端案例组包与弹窗测试 14 项通过，包含“展示完整后缀”和接口新增动态类型转换；backend-api TCDS 专项测试 2 项通过。
- 后端 `TcdsCaseMaintenanceServiceTest` 7 项通过，`TcdsCaseMaintenanceControllerTest` 与 `ApiLoggingAspectTest` 合计 25 项通过。
- agent-web 与 backend-api 全量类型检查被当前工作区已有的 backend-api 方法缺失阻断；报错集中在 `openExperienceWorkspace`、`commitExperienceWorkspace`、`restartMyOpencodeProcess`、`get/listSession...ForRun` 和 `closeWorkspaceFileSocket`，本次 TCDS 专项测试未失败。

### Result

- 例如下拉展示“功能测试任务”，最终 `createGraphCase.taskType` 传“功能”；“准入测试任务/功能测试任务”多选传 `准入,功能`。
- 浏览器 POST 路径和结构、`createGraphCase` 报文结构、固定 `toolId` 与成功后缓存跳转保持不变；提交阶段增加一次实时任务类型 GET。
- 不涉及 RunEvent、SSE、WebSocket、数据库、Flyway、性能模型、generated SDK、OpenCode 源码或环境配置；未重启服务、未调用真实 TCDS、不会推送远端。

## 2026-08-14 - TCDS 案例维护只提交勾选案例

### Why

- 案例维护弹窗的勾选状态此前只用于批量赋任务类型，点击确认仍校验并提交全部 Markdown 案例，导致未勾选案例缺少类型时也会阻止提交。
- 内网日志中的 `FORBIDDEN / 无权限` 来自另一用户调用 `AgentConfigController.publicDiff`，与案例维护无关；`test-cases` 的实际失败是 TCDS `createGraphCase` 返回非零业务码后映射的 `CONFLICT / 业务异常`。

### What

- 确认时仅收集已勾选案例；没有勾选时提示“请选择案例”，已勾选案例中存在空类型时提示“请选择案例类型”。
- 未勾选案例不参与类型校验，也不进入发往后端的 `caseList`；组包函数保留空类型防御性校验并统一提示文案。
- 同步更新前端根 README 和 agent-web README，明确勾选、校验与提交范围。

### How

- 定向运行 `TestCaseMaintenanceDialog.test.ts` 和 `test-case-maintenance.test.ts`，2 个文件共 16 项测试全部通过。
- agent-web 全量 `vue-tsc` 仍被工作区已有的 backend-api 方法缺失阻断，错误集中在 `closeWorkspaceFileSocket`、`get/listSession...ForRun`、`open/commitExperienceWorkspace` 和 `restartMyOpencodeProcess`，没有新增指向本次修改的错误。

### Result

- 例如 10 条案例只勾选 2 条时，只校验并提交这 2 条；另外 8 条无需选择类型，也不会发送给后端。
- 不变更 HTTP API、DTO、事件、数据库、Flyway、性能、安全、兼容性、环境配置或 OpenCode 源码；未重启服务、未调用真实 TCDS、不会推送远程。

## 2026-08-14 - 增加 TCDS 案例维护上游脱敏日志

### Why

- 内网排查只能看到任务类型查询成功，无法确认 `createGraphCase` 是否发出、HTTP 是否返回以及 TCDS 的业务码和消息。
- 案例四列正文、统一认证号、固定 `toolId` 和上游 `data` 都不能原样进入平台日志。

### What

- `createGraphCase` 调用前后新增 `tcds_create_graph_case_request/response` 结构化日志并携带 `traceId`、`itemNo` 和 HTTP/业务结果。
- 请求日志把 `userId` 固定为 `[REDACTED]`，不记录 `toolId`；案例 `name/step/data/expect/dataDependencies` 只保留字符数、字节数和 SHA-256 短摘要，最多预览 20 条且单条日志不超过 8 KiB。
- 响应日志保留 HTTP 状态、业务 `code` 和经控制字符、常见凭据及长数字身份脱敏后的有界 `msg`；`data` 只保留类型、数量和摘要，空、非法或超限响应只记录安全状态。
- 同步 integration README、HTTP API 和安全规范，补充请求、响应及异常正文不泄漏测试。

### How

- 定向运行 `TcdsCaseMaintenanceServiceTest`，10 项全部通过；Maven reactor 明确 `BUILD SUCCESS`。
- 执行 `git diff --check`、目标文件冲突标记扫描并回顾全部 `.agents/session-log*.md` 近期条目，未发现与本次 TCDS integration 修改冲突。

### Result

- 重启后可按 `event=tcds_create_graph_case_request|tcds_create_graph_case_response` 和同一 `traceId` 判断请求是否已发出、HTTP 状态、TCDS `code/msg` 及响应格式状态，同时日志不保存认证号、案例正文、固定工具标识或上游 `data` 原文。
- 不修改 HTTP API/DTO、RunEvent、数据库、Flyway、性能模型、环境配置、generated SDK 或 OpenCode 源码；未重启服务、未调用真实 TCDS、不会推送远程。

## 2026-08-14 - local profile 模拟 TCDS 任务类型

### Why

- 本机无法访问内网 `getTaskTypes`，需要使用已上传接口样例中的 `data.subItemTypes` 联调案例维护弹窗，同时不能改变内网和生产环境的真实调用行为。

### What

- `TcdsCaseMaintenanceService` 仅在 Spring 激活 `local` profile 时返回样例中的 10 项任务类型，并记录 `source=local-mock`；其它 profile 继续请求真实 TCDS。
- 本地模拟只替代 `getTaskTypes`，提交阶段仍校验模拟类型并真实调用 `createGraphCase`；未修改 `.env`、`application-local.yml` 或其它环境配置。
- 同步 integration README、HTTP API 和安全规范，明确 local-only 边界。

### How

- 定向运行 `TcdsCaseMaintenanceServiceTest`，12 项全部通过，覆盖 10 项名称/值及顺序、零任务类型上游调用和本地提交仍调用真实 `createGraphCase`；Maven reactor 为 `BUILD SUCCESS`。
- 执行目标差异空白和冲突标记检查，并回顾全部 `.agents/session-log*.md` 近期条目，未发现与本次修改冲突。

### Result

- 使用 `local` profile 启动后，弹窗可直接加载上传样例中的 10 项任务类型；使用 `test`、生产或其它 profile 时仍访问真实 `getTaskTypes`。
- 不变更浏览器 HTTP API/DTO、RunEvent、数据库、Flyway、部署拓扑、性能模型、generated SDK 或 OpenCode 源码；未重启服务、未调用真实 TCDS、不会推送远程。

## 2026-08-17 - TCDS 案例类型改用接口完整名称

### Why

- `createGraphCase.caseList[].taskType` 不再接受去掉“测试任务”后缀的简称或固定对应关系，需要直接使用本次 `getTaskTypes.data.subItemTypes` 返回的完整 `name`。

### What

- 前端移除固定 `value -> requestName` 映射，按实时选项的 `value` 查找完整 `name`；多选仍按选择顺序去重并用英文逗号连接。
- 后端移除固定十项映射，提交前重新查询实时任务类型并按完整名称白名单校验，最终将完整名称原样写入 `createGraphCase.taskType`。
- 前后端均拒绝空名称、名称中的中英文逗号、重复 `value` 和重复 `name`，避免多选分隔符歧义；同步前后端 README、HTTP API 和安全规范。

### How

- 前端案例组包与弹窗定向测试 2 个文件、16 项全部通过。
- 后端 `TcdsCaseMaintenanceServiceTest` 10 项全部通过；沙箱内 Maven 因用户缓存 JAR 关闭时触发 `AccessDeniedException`，在正常文件权限下复跑后 reactor `BUILD SUCCESS`。
- 执行 `git diff --check` 并回顾全部 `.agents/session-log*.md` 近期记录；提交仅包含本次 TCDS 契约相关文件和本日志，不纳入工作区其它改动。

### Result

- 例如接口返回并选择“准入测试任务”和“功能测试任务”时，最终报文为 `"taskType":"准入测试任务,功能测试任务"`；接口后续新增的合法类型也无需修改固定映射。
- HTTP 路径和 DTO 结构不变，但前后端必须同步升级；不涉及 RunEvent、SSE、WebSocket、数据库、Flyway、部署拓扑、性能模型、generated SDK、OpenCode 源码或环境配置，未重启服务、未调用真实 TCDS、不会推送远程。

## 2026-08-17 - 合并 release 最新 TCDS 统一请求架构

### Why

- 拉取 `origin/release` 的 34 个提交后，本地完整任务类型修改与上游 TCDS 统一基础地址、共享请求构造器改造在服务、测试及文档中产生冲突。

### What

- 保留上游 `TEST_AGENT_TCDS_BASE_URL`、`TcdsHttpRequestFactory` 和所有同源请求自动注入固定 `toolId` 的实现。
- 删除合并中重新带回的十项简称映射，继续按本次 `getTaskTypes` 返回的完整 `name` 校验并原样写入 `createGraphCase.taskType`。
- 合并后测试同时覆盖配置化 `:9080` 地址、固定 `toolId`、动态新增任务类型、重复/分隔符校验和完整名称多选报文；README、HTTP API 与安全规范同步采用两侧合并后的契约。

### How

- 前端案例组包与弹窗定向测试 2 个文件、16 项全部通过。
- 后端 `TcdsCaseMaintenanceServiceTest`、`TcdsHttpGatewayTest`、`TcdsIntegrationConfigTest` 共 19 项全部通过，Maven reactor `BUILD SUCCESS`。
- 清理 7 个冲突文件中的全部合并标记并执行差异空白检查；工作区原有 `.gitignore`、本地配置、生成文件和备份文件不纳入冲突解决。

### Result

- TCDS 案例维护兼容上游统一请求架构，最终报文仍为 `"taskType":"准入测试任务,功能测试任务"`，不会退回简称。
- 本次合并引入的其它上游 API、数据库、部署与前端改动保持远端内容；冲突解决本身不新增 API、事件、数据库、性能或部署变化，未重启服务、未调用真实 TCDS、不会推送远程。

## 2026-08-17 - 修复案例维护表头选择框重叠

### Why

- 案例维护表格纵向滚动时，数据行的 Element Plus 选择框与 sticky 表头处于相同绘制层级，第一行选择框会穿透表头并与原有全选框重叠。

### What

- 将固定定位和层级从单个表头单元格提升到整个 `thead`，通过更高层级和独立 stacking context 统一覆盖数据行控件；保留原有全选框、逐行选择和提交逻辑。
- 同步 agent-web README，明确滚动时表头仅显示原有全选框。

### How

- 案例维护组件定向 Vitest 2 个文件、16 项全部通过；agent-web `vue-tsc` 类型检查通过；`git diff --check` 通过。
- 尝试通过浏览器技能连接本地页面做滚动视觉复测，但浏览器运行组件初始化时报本机路径不存在，未改用项目外浏览器方案。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，未发现冲突；工作区原有 restart skill、`.gitignore`、生成声明和本地配置改动不纳入本次提交。

### Result

- 数据行选择框不会再滚入表头区域，表头继续保留唯一的全选框。
- 不涉及 API、RunEvent、数据库、Flyway、部署、性能、安全、generated SDK、OpenCode 源码或环境配置；未重启服务、未推送远程。自动视觉验证仍需在浏览器连接恢复后或由用户重启页面后复测。
