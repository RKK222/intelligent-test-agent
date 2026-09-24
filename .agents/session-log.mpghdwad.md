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

## 2026-08-28 - TCDS 案例维护失败时不再弹出空白标签页

### Why

- `041-测试设计` 案例维护原先会在调用平台维护接口前立即打开 `about:blank` 占位；下游 `createGraphCase` 失败时虽然随后关闭，用户仍会看到新标签页被弹出。

### What

- 参考 `042-测试执行`，平台案例维护成功后先复用当前编辑器正文调用缓存接口，取得最终 `jumpUrl` 后才直接打开目标标签页；不再创建或替换 `about:blank`。
- `createGraphCase` 失败时保留案例维护弹窗与统一错误提示，缓存失败时显示既有缓存错误；两种失败都不调用 `window.open`。新增维护成功后继续导航及失败不导航的回归测试，并同步前端 README、agent-web README 和 HTTP API 交互说明。

### How

- 案例维护组包与弹窗定向 Vitest 2 个文件共 18 项通过；`agent-web` typecheck/lint 与 `git diff --check` 通过。
- `FigmaEditorArea.test.ts` 在导入任务外思维导图运行时时，因当前 `node_modules` 无法解析 `simple-mind-map/src/plugins/Drag.js` 而在收集阶段失败；该套件未执行测试，本次未修改对应依赖或编辑器入口组件。

### Result

- `createGraphCase` 成功且缓存接口返回 `jumpUrl` 后才直接打开目标页；成功路径不再出现空白 tab，任一前置调用失败也不会创建新标签页。
- 使用 `release`，不新增部署节点；不变更 HTTP API 路径/DTO、RunEvent/SSE、数据库、Flyway、后端、安全、环境配置、generated SDK 或 OpenCode 只读源码，未调用真实 TCDS。

## 2026-09-01 - 重新启用 Workspace 活动文件自动定位

### Why

- 用户在整体回退后明确要求重新提交首轮功能，但暂不处理 Agent 配置树，只保证 Workspace 项目文件树能跟随当前打开文件。

### What

- 重新启用编辑器活动 Workspace/Reference 文件变化时的自动定位：切回文件视图、展开工作空间与祖先目录，并在当前文件树实例内高亮滚动目标文件。
- 快速切换标签时继续使用定位代次校验，阻止旧异步请求覆盖新活动文件；Agent 标签由 `isAgentFilePath` 明确跳过，不新增 Agent 树展开或定位逻辑。
- 恢复文件树与工作台回归测试，并在保留当前 `release` 后续文档内容的前提下同步 agent-web README 和前端规范。

### How

- 定向 Vitest 运行 `figma-file-explorer.test.ts`、`workspaceViewState.test.ts`、`FigmaEditorArea.test.ts`，3 个文件、45 项全部通过。
- `@test-agent/agent-web` typecheck 与 lint 通过；执行任务文件差异检查，并用隔离索引避免提交现有 TCDS、生成声明、启动脚本和此前已暂存的 Windows 本地启动记录。

### Result

- 打开或切换 Workspace/Reference 文件后，左侧项目文件树会自动展开并选中当前文件；Agent 文件仍不触发 Workspace 文件树定位，也未实现 Agent 配置树自动展开。
- 使用 `release`，不新增部署节点；不涉及 HTTP API、RunEvent/SSE、WebSocket 契约、数据库、Flyway、后端、部署、安全、环境配置、generated SDK 或 OpenCode 只读源码。

## 2026-09-02 - 修复搜索打开 Workspace 文件未选中

### Why

- 用户反馈从文件搜索结果打开文件后，页面会切回文件树并展开对应目录，但目标文件行没有被选中。
- 搜索结果只提供 Workspace 物理相对路径，而组合文件树使用后端稳定节点 ID 判断活动行；目录懒加载完成后仍传原始路径，导致两种身份不相等。

### What

- 新增 Workspace 物理路径到已加载组合树稳定节点 ID 的精确映射，只匹配带 `workspacePath` 的文件节点，不会误选同展示路径的引用文件。
- 活动文件节点计算同时依赖懒加载后的目录缓存；搜索打开文件时，叶子目录返回后立即用稳定 ID 驱动 `is-active`，保留既有目录展开、滚动和快速切换代次保护。
- 增加路径映射单元测试和搜索打开后文件树活动行 E2E 断言，并同步 agent-web README 与前端规范；Agent 文件仍由原边界直接跳过。

### How

- 定向 Vitest 运行 `workspaceViewState.test.ts`、`figma-file-explorer.test.ts`、`FigmaEditorArea.test.ts`，3 个文件、46 项全部通过；`@test-agent/agent-web` typecheck 与 lint 通过。
- 尝试运行搜索打开场景的单条 Chromium Playwright，但本机缺少 Playwright 1.61.0 对应的 `chromium_headless_shell-1228` 可执行文件，测试在浏览器启动前失败；没有下载外部浏览器，E2E 断言已保留待具备运行时后执行。
- 执行任务文件 `git diff --check` 并回顾全部 `.agents/session-log*.md` 近期记录；提交继续使用隔离索引，不纳入现有 TCDS、生成声明、启动脚本和此前暂存日志。

### Result

- 从搜索结果打开 Workspace 文件后，文件树不再只展开目录，而会选中并滚动到对应文件；同路径引用节点不会被误选。
- 使用 `release`，不新增部署节点；不涉及 HTTP API、RunEvent/SSE、WebSocket 契约、数据库、Flyway、后端、部署、安全、环境配置、generated SDK 或 OpenCode 只读源码。

## 2026-09-07 - 支持管理员重命名已有工作空间

### Why

- 后端 `PATCH /applications/{appId}/workspaces/{workspaceId}` 与前端 `updateApplicationWorkspace` 已支持应用管理员和超级管理员更新 `workspaceName`，但设置页“已有工作空间”列表缺少直接入口。

### What

- 在设置页“工作空间管理”的已有工作空间行新增“重命名”操作，弹窗预填当前名称；空名称、名称未变化或同一应用内名称重复时禁止提交。
- 重命名只提交 `workspaceName`，成功后刷新列表并通知工作台刷新工作空间目录；原有启停开关和不提供删除操作的边界保持不变。
- 增加成功重命名及重复名称拦截回归测试，并同步 agent-web README 与内置用户手册。

### How

- 复用既有 `updateApplicationWorkspace` PATCH API，没有新增接口、DTO 或后端逻辑。
- 定向 Vitest `settings-app-workspace-panel.test.ts` 共 22 项全部通过；`@test-agent/agent-web` typecheck 与 `git diff --check` 通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，并隔离保留工作区已有 TCDS 修改及两个未跟踪本地启动脚本。

### Result

- APP_ADMIN/SUPER_ADMIN 可在已有工作空间列表直接修改显示名称，工作台目录会同步刷新；版本库、分支、目录、版本和启停状态不变。
- 使用 `release`，不新增部署节点；不变更 HTTP API 契约、RunEvent/SSE、数据库、Flyway、后端、安全、环境配置、generated SDK 或 OpenCode 只读源码。

## 2026-09-08 - 工作空间名称改为行内编辑并回车确认

### Why
- 用户要求不增加独立重命名按钮；交互由双击弹窗编辑进一步调整为单击行内编辑、点击框外取消、回车弹窗确认。

### What
- 已有工作空间名称框单击进入编辑，失焦丢弃草稿并恢复原名称；有效新名称按回车后展示只读确认框，确认才调用 PATCH。取消确认放弃修改，确认框获得焦点时不丢弃草稿。
- 保留名称校验、PATCH 保存、目录刷新和启停逻辑，同步组件测试、agent-web README 与用户手册。

### How
- 定向 Vitest 22/22 通过，agent-web typecheck 通过；覆盖失焦取消、空白/未变化/重复名称、输入法回车、确认前不提交、取消确认与成功保存。
- 已回顾全部提交者会话日志近期记录；本次开始工作区 clean，在当前 release 上最小修改，不新建分支。

### Result
- 最终交互为单击编辑、失焦取消、回车弹窗确认；不变更 API、事件、数据库、部署、安全或兼容性契约。未做真实浏览器验收。

## 2026-09-08 - 工作空间管理目录树滚动条常显

### Why
- 目录树继承全局透明滑块，用户要求鼠标移开后仍可看到滚动条。

### What
- 仅在 SettingsAppWorkspacePanel 的目录树容器局部覆盖滑块背景色；保持按内容溢出滚动，不改变其他面板行为。
- 同步 agent-web README 行为说明及人工验收步骤。

### How
- agent-web typecheck 通过；工作空间面板定向 Vitest 22/22 通过；git diff --check 通过。
- 提交前回顾所有 session-log 近期条目，保留已有工作空间行内重命名能力；当前 release，不新建或切换分支。

### Result
- 无 API、事件、数据库、安全、部署节点或业务兼容性变更，无新增性能开销；未进行真实浏览器视觉验收或部署。

## 2026-09-08 - 补齐个人工作区搬迁失败安全诊断

### Why
- 企业源 .4 到目标 .114 搬迁反复失败；Git 合并/子模块和身份、副本检查正常，未跟踪 `.opencode` 链接曾命中拒绝，用户删除后仍失败。旧日志只记录 PlatformException/RELOCATION_CONFLICT，无法确认最新根因。

### What
- 增强 Worker 单次认领阶段和 Snapshot 细分阶段；日志新增受控 reason、stage、pathRef、本次 traceId 与 causeType，既有 safe_error_message 保存固定安全提示。文件问题仅保存相对路径 SHA-256，不记录原始路径/链接目标、异常消息、任意 details 或 stderr；源端事实校验补充固定原因码。
- 同步工程/模块 README、XXL 架构/测试、API/事件边界和数据库兼容说明；无 schema、调度、租约、重试、文件保护、路由、环境或部署节点变化。

### How
- workspace-management 及依赖模块定向 Maven：Diagnostics/Worker/SnapshotDiagnostics/TaskHandler 和三项 RealGit 方法，共 17 项通过；git diff --check 通过。
- 首次完整 RealGit 运行发现 Windows 无创建符号链接权限，以及 autocrlf 警告混入 diff；仅对后续测试进程固定 core.autocrlf=false，三项可执行 RealGit 全通过。链接安全用例未验收，未修改全局 Git 配置、系统权限或既有用例。
- 开始时 release 工作区 clean；提交前回顾全部 session-log 近期条目，未覆盖他人修改；仅提交本次相关文件。

### Result
- 诊断增强完成，未连接/部署企业服务器，也未修改企业数据库或用户文件；最新现场原因仍需部署新版本源 Java 后重试确定。目标恢复内部仍以 TRANSFER 表示，未知原因安全降级 UNCLASSIFIED。
- 需按既有发布流程更新源 .4 Java，不能仅更新 .114；既有错误说明在下一次真实失败时更新。未推送或发布，Windows 链接安全用例需在具备权限的环境补验。

## 2026-09-08 - 搬迁失败日志直接显示相对路径及文件名

### Why
- 用户明确要求日志直接输出文件路径和名称，避免现场逐个计算 pathRef 定位。

### What
- Snapshot 将单文件失败路径保留于私有异常上下文，Worker 重试日志新增 filePath，直接显示仓库相对路径及名称；不进入 API details、数据库安全消息或事件。双引号、反斜线及控制/格式字符转义，有界输出；无单文件定位时为 NONE。
- 同步工程/模块 README、XXL 架构和测试、HTTP API 边界、安全规范。保留 pathRef，不改变搬迁保护、重试、数据库结构、路由和部署节点。

### How
- Maven 定向运行 Diagnostics、Worker、SnapshotDiagnostics、TaskHandler 及三项 RealGit 方法，18 项通过；git diff --check 通过。测试仅在进程级固定 core.autocrlf=false。
- 回顾所有 session-log 近期条目，release 起始工作区 clean，未修改其他人员成果、环境配置或 OpenCode 源码。

### Result
- 文件名可直接在源端运维日志查看，外发日志需脱敏；没有部署或推送，现场仍需源 Java 使用新版本后重试确认根因。
- Windows 符号链接权限受限的既有真实链接测试本次未执行，保留后续目标环境补验要求。

## 2026-09-14 - 修复前端本地提交后无法重新推送

### Why

- 企业用户在前端选择“提交”而未立即推送后，个人 worktree 已经完成本地提交，Git 工作区变为 clean；刷新 Git Diff 后变更未暂存和已暂存均为空，页面因此失去后续推送入口。

- 用户随后反馈“重新推送”失败；桌面截图入口为 `127.0.0.1:3000`，需区分本机开发链路与企业 `.4` 现场，不能沿用企业归因。

### What

- 普通应用 Workspace 在仅本地提交成功后保存个人 worktree、工作区、文件白名单、提交说明及 Diff 元数据到既有待推送状态。
- 应用 Agent 在仅本地提交成功后保存待发布文件白名单，继续复用既有“重新推送”流程；推送成功仍清理待推送状态，`spec/**` 仍只本地提交不发布。
- 新增回归测试，覆盖本地提交后显示“待推送”并通过“重新推送”发布原提交文件。

- 结合本机 `.tmp/dev-services/backend.log` 确认，两次重试已到达后端，在应用 feature 副本 `PREPARE_REMOTE` 的 `fetch origin` 阶段因 `ssh: Could not resolve hostname gitee.com: Name or service not known` 失败，归类 `NETWORK_UNAVAILABLE` / `GIT_UNAVAILABLE`，尚未进入这两次请求的投影、提交或 push。

- 继续核对地址来源：运行副本 `.git/config` 的 `remote.origin.url` 实际指向 Gitee；配置链路为“设置 → 版本库管理 → 版本库地址” → `code_repositories.git_url` → clone 后写入 origin。现有内部模式仅按操作人拼接 SSH 用户名前缀，已有副本继续校验 origin 与平台保存地址一致。

### How

- 目标分支为 `release`，本次不新增部署节点，不修改后端 API、事件、数据库、部署配置或 OpenCode 源码。
- 定向 Vitest `apps/agent-web/tests/git-changes-panel.test.ts`：59/59 通过。
- `@test-agent/agent-web` typecheck：通过；`git diff --check`：通过。
- 提交前回顾全部 `.agents/session-log*.md`，保留其它提交者的既有记录和工作区成果。

- 通过桌面截图 OCR、日志 trace 精确筛选、工作副本只读 Git 状态及脱敏 origin 主机核对定位；2026-09-14 10:48:26、10:48:42（UTC+8）的 trace 分别为 `trace_e9dbfe10982d476aafb1fd1ec9eb8f4e`、`trace_6abd7c4afbc44c7ca9c141e10851ff15`。10:58 本机 PowerShell DNS 已能解析该域名，但未验证后端 Git 子进程当前链路或重新执行发布。
- 同日志 10:34:23 的较早请求返回 `PUBLISHED` / `remotePushed=true`；其个人副本同步受本地文件阻挡的警告与 10:48 DNS 失败不同，不能据此断言本次待推送文件已经发布。

- 使用 `git remote get-url origin`、`git config --show-origin --get-regexp` 只读核对副本配置来源，未发现 Git URL 重写规则；审计版本库表单、领域对象、MyBatis mapper 及副本准备代码。未查询数据库当前行，未修改实际仓库地址。

### Result

- 仅本地提交后，前端不再依赖 clean 状态下重新读取 Git Diff，而是通过 sessionStorage 恢复待推送上下文，用户可刷新页面后点击“重新推送”完成发布。
- 当前未进行企业 `.4` 现场部署或真实浏览器验收，需按既有发布流程部署后在企业环境验证实际推送链路。
- 本次重试失败的直接原因是当时 Git/SSH 域名解析失败，并非前端入口未触发；DNS 失败的具体诱因仍未确认。确认目标仓库配置正确且本机实际后端链路恢复后可使用既有“重新推送”，无需重复提交、reset 或强推；此次只补充诊断，不修改业务代码、环境配置或 Git 配置，不执行 fetch/push，也不把本机结果外推为企业验收。
- 用户指出期望目标可能不是 Gitee；DNS 日志只证明访问现有 origin 失败，不能证明目标配置正确，需先核对应用关联与期望克隆地址。版本库 URL 创建后不可编辑，不能仅 `git remote set-url` 绕过平台记录或删除含本地提交的 worktree；具体地址修复方案需在确认目标后制定。

## 2026-09-14 - 按 Git 跟踪记录恢复存量已提交未推送文件

### Why

- 企业用户反馈 `F-BASE/workspace/docs/功能模块/测试案例_模板.md` 本地与远程不一致，但 Git Changes 的未暂存和已暂存列表都没有显示；浏览器 sessionStorage 只能恢复当前浏览器本次操作，不能覆盖存量提交、刷新会话或换浏览器后的状态。

### What

- 只读核对目标个人 worktree：个人 `HEAD=07f1c85f0f2fb857b9faad55afbacf3a064e4ce6`，本地 `refs/remotes/origin/feature_testagent_20260812=7be834b4286c173212c1e74a13df3bd89c759f86`；目标文件在两提交树之间为 `M`，但工作树和 index 对该文件均无状态，因此原页面只读 `git status` 必然漏掉。
- `GitWorkspaceService` 增加本地 origin 跟踪提交读取、HEAD 提交说明读取和 name-status 解析；不执行 fetch，不访问网络，也不使用数据库 target 冒充远程状态。
- 工作区 Git Diff 在应用 target 与本地 origin 跟踪提交均已进入个人 HEAD、无待合入更新且不在 merge 中时，返回 origin 跟踪提交到个人 HEAD 的 `pendingPublishFiles/pendingPublishCommitMessage`；目录范围、`spec/**` 排除和 rename/copy 投影保持受控。
- 前端把后端返回的待推送文件分流到“应用工作空间”或“应用 Agent”，显示“待推送”并复用 publish 接口重新推送，不重复本地 commit；权威空数组清理旧浏览器快照，字段缺失或 `null` 时兼容旧后端和只读恢复失败。
- 同步 workspace-management README、frontend README、HTTP API、共享类型和应用 worktree 验收案例。

### How

- 目标分支为 `release`，不新增部署节点，不切换或新建分支；只修改 Git 状态投影、前端恢复入口、相关测试与稳定文档。
- 按用户要求未运行 Maven、Vitest、typecheck、构建、服务重启或真实推送，由用户自行验证；仅静态回顾差异与全部 `.agents/session-log*.md` 近期条目。
- 两个根目录 OCR 临时文件 `chi_sim.traineddata`、`eng.traineddata` 与本次无关，不纳入提交。

### Result

- clean 的工作树/index 不再等同于“没有待推送文件”；只要本地已知的 origin 跟踪提交落后于个人 HEAD，当前工作区内的已提交差异就能重新出现在“待推送”列表。
- 本地 origin 跟踪引用只是最近一次 fetch/push 后的 Git 快照，不保证等于实时远程；引用缺失或无法安全判定时后端返回不可判定，不误清理前端兼容记录。
- API 仅向响应增加可选字段，兼容旧前端；不涉及事件、数据库、Flyway、部署拓扑、性能模型、安全权限、环境配置、generated SDK 或 OpenCode 源码。未推送远程。

## 2026-09-16 - Agent 配置树支持上传目录

### Why
- 用户希望参考工作空间目录加号的“上传目录”能力，在 Agent 配置树的可写文件夹中上传整个目录，并保留目录内部层级。

### What
- Agent 配置树的可写目录创建弹框显式开放“上传目录”，复用浏览器 `webkitdirectory` 选择器和工作空间上传相对路径规则：去掉最外层所选目录名，保留内部文件层级；普通多文件上传行为不变。
- 公共 Agent 与应用 Agent 的分片上传在 begin 前通过统一 `WorkspaceFileService.createDirectory` 创建缺失的安全父目录，继续复用根目录锚定、符号链接和越界校验。
- 补充前后端定向测试，并同步 agent-web、file-explorer、workspace-management README/PACKAGE 及 HTTP API 语义说明；浏览器不能表达纯空目录，因此空目录不会被单独创建。

### How
- agent-web 定向 Vitest 41/41 通过，agent-web typecheck 通过；workspace-management 新增的公共/应用 Agent 嵌套父目录测试 2/2 通过；`git diff --check` 通过。
- 完整 `AgentConfigApplicationServiceTest` 曾受 Windows 既有符号链接权限与 `SecureWorkspaceMover winError=87` 影响，改为定向验证本次新增能力；未修改相关环境、搬移实现或既有测试。
- 目标分支为 `release`，不新增部署节点，不新建或切换分支；提交前回顾全部 `.agents/session-log*.md` 近期条目，并保留工作区原有 OCR 数据文件，不纳入提交。

### Result
- Agent 可写文件夹加号菜单现可上传目录；嵌套文件会在安全创建父目录后按原层级上传。
- 不新增 API 路径、DTO、事件、数据库或 Flyway 变更，不涉及部署拓扑、性能模型或权限放宽；目录上传为向后兼容的新增 UI 能力，路径安全仍由统一文件服务保证。

## 2026-09-17 - 优化问题排查会话标识与工作区检索

### Why
- 用户要求在“问题排查只读访问”页面的用户首页视角中，选择会话后在 Session ID 下补充 Workspace ID，并让同级工作区列表支持按 ID 搜索。
- 工作区列表采用后端分页，不能只过滤前端当前加载的 30 条，否则会漏掉其它分页中的目标工作区。

### What
- 会话排查标识新增 Workspace ID 展示与复制，继续复用既有只读诊断样式和剪贴板逻辑。
- 工作区页签新增名称或 Workspace ID 搜索框；HTTP 列表接口增加可选 `q`，MyBatis XML 在数据库分页前对工作区名称和 ID 做不区分大小写的模糊过滤，列表与总数使用同一条件。
- backend-api client 支持新的对象参数，同时保留旧 `(page, size)` 数字调用兼容；同步前后端、模块 README、HTTP API 文档及定向测试。

### How
- 前端定向 Vitest 2 个文件共 133 项通过；agent-web 与 backend-api typecheck 通过。
- persistence PostgreSQL 集成测试 13 项通过；test-agent-api 及依赖模块编译通过；新增 workspace-management 查询透传测试 1 项通过。
- 完整 `UserWorkspaceQueryServiceTest` 仍有两个既有 Windows 路径断言失败：期望 `/data/...`，实际被解析为 `E:\data\...`；与本次查询参数透传无关，故对新增能力采用单项定向验证。
- 目标分支为 `release`，不新增部署节点、不新建或切换分支；提交前回顾全部 `.agents/session-log*.md` 近期条目，并保留根目录两个 OCR 临时文件不纳入提交。

### Result
- 超级管理员排查页面可直接复制会话关联的 Workspace ID，并能跨分页按工作区名称或 ID 定位目标。
- `q` 为可选增量参数，不传时保持原分页行为；查询仍限定在目标用户归因范围内，继续复用既有 grant、审计和只读安全边界。
- 不涉及 RunEvent/SSE、数据库结构、Flyway、部署拓扑、环境配置、generated SDK 或 OpenCode 源码；模糊查询在分页前执行，未新增索引。
## 2026-09-23 - 工作空间与 Agent 文件夹增加清空并保留目录选项

### Why
- 单个文件夹的原删除确认只能递归删除整个目录，用户需要删除其全部文件及子文件夹，同时保留选中的目录本身。

### What
- 共用删除弹窗增加默认不勾选的“清空文件夹（保留当前文件夹）”，工作空间与公共/应用 Agent 树分别沿既有文件 WebSocket route/ticket/RPC 和授权链路传递 `keepDirectory=true`。
- 文件服务验证目标是非根目录且为目录，清空前扫描内部 `.git`（含大小写变体），遍历期间继续拒绝新增 `.git`；不跟随符号链接，只删除目标目录下的内容。前端保留目标目录节点、清理后代缓存及失效标签，并刷新 Git Diff。
- 同步文件树、Agent Web、工作空间模块 README/PACKAGE 与 `docs/api/http-api.md`、`docs/api/event-stream.md`，增补前后端行为及参数映射测试。

### How
- 前端定向 Vitest 2 个文件 71 项通过；`@test-agent/file-explorer`、`@test-agent/agent-web` typecheck 通过。
- 后端定向 Maven reactor 测试：`WorkspaceFileServiceTest` 新增 2 项与 `WorkspaceFileWebSocketHandlerTest` 40 项通过，`BUILD SUCCESS`；`git diff --check` 通过。
- 此前运行完整 `WorkspaceFileServiceTest` 出现 10 项既有 Windows 环境限制（符号链接权限、`SecureWorkspaceMover` winError=87），本次新增测试均未在失败列表，未修改无关环境或移动实现。提交前回顾全部 `.agents/session-log*.md` 近期条目，保留根目录原有两个 OCR 数据文件不暂存。

### Result
- 单个文件夹可选择清空并保留目录；不勾选及旧 RPC 请求继续原递归删除语义，多选删除不变。
- 不新增部署节点、HTTP API 路径、RunEvent/SSE、数据库/Flyway 或环境配置；新增可选 RPC 字段向后兼容，既有权限、路径和 `.git` 防护沿用并补强清空预检。大目录清空为逐项文件操作，遇并发修改或 I/O 失败可能部分完成，未宣称事务回滚。

## 2026-09-23 - 修复清空文件夹可能误删当前目录

### Why
- 用户复查发现勾选“清空文件夹”后目标目录仍被删除。上一条日志把 `delete + keepDirectory=true` 认定为向后兼容是错误的：旧服务端会忽略未知字段并执行递归删除；旧本地客户端也忽略该字段。尚未获取用户现场版本组合，不能确认现场的唯一原因。

### What
- 工作空间及公共/应用 Agent 清空改为独立 `workspace.clear-directory` / `agent-config.clear-directory` 文件 WebSocket 操作；服务端复用既有授权、目录校验和保留目录的文件服务。旧 `delete` 行为与已发布前端兼容分支保留，但新前端不再用可选标记表达清空。
- 本地客户端新增独立清空操作；服务端拒绝本地工作区旧格式 `workspace.delete + keepDirectory=true`，防止旧本地客户端忽略字段误删目录。旧服务端、旧本地客户端不识别新操作时必须报错，不做删除降级。
- 补充前端操作名、后端映射与未知操作、本地目录存续测试，更新 HTTP API、文件 WebSocket 事件契约和相关模块 README。

### How
- `corepack pnpm exec vitest run packages/backend-api/tests/backend-api.test.ts packages/file-explorer/tests/DirectoryRows.test.ts apps/agent-web/tests/agent-config-panel.test.ts`：3 文件 200 项通过；修改后重跑 backend-api：128 项通过。backend-api、agent-web typecheck 通过。
- `mvn -pl test-agent-api -am '-Dtest=WorkspaceFileServiceTest#serviceClearsDirectoryButKeepsTheSelectedFolder+serviceRejectsClearingFileRootAndNestedGitWithoutPartialDeletion,WorkspaceFileWebSocketHandlerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：工作区服务 2 项、handler 42 项通过。
- 本地客户端新的隔离定向用例通过，编译通过；完整 `LocalClientFileRpcHandlerTest` 的 3 个既有测试在 Windows 因 POSIX 文件权限与根目录文件系统身份限制失败，非本次逻辑回归。`git diff --check` 通过，提交前回顾各 `.agents/session-log*.md` 近期记录，两个未跟踪 OCR 数据文件不暂存。

### Result
- 清空操作只在支持保留目录语义的后端/本地客户端上执行；混合版本优先报错而不是把清空变成递归删除。目标环境尚未部署验证，已删除的历史目录不能由本修复自动恢复；发布需同步更新前后端，本地工作区还需更新本地客户端。
- 不新增部署节点或 HTTP 路径，不涉及 RunEvent SSE、数据库/Flyway、性能模型或环境配置；文件 WebSocket RPC 契约新增操作名，权限不放宽。大目录清空仍可能在 I/O 异常或并发修改时部分完成。

## 2026-09-23 - 清空文件夹 WebSocket 操作报不支持的本地运行版本排查

### Why
- 用户在已提交独立清空 RPC 后仍收到 `VALIDATION_ERROR: 不支持的文件 WebSocket 操作`；不能回退为旧 `delete + keepDirectory`，否则旧后端会递归删除当前目录。

### What
- 确认当前 `release` 源码已包含 `workspace.clear-directory` 与 `agent-config.clear-directory`，但本地 8080 Java 进程运行的应用 JAR 构建于 9 月 17 日；同一工作空间的文件与 Agent 配置 route 日志均指向本机 8080，且 `runtimeKind=SERVER_PROCESS`。
- 按正在运行的 `local` profile 与已有 `.env.local` 重新构建并重启本机后端/前端，未改环境配置；Windows 脚本重编 opencode-manager 因 Go 代理依赖下载超时而跳过启动，随后使用现有二进制与原本的 manager 环境参数恢复其进程。保留了手动启动的 opencode serve。

### How
- 后端 `mvn clean package -Dmaven.test.skip=true` 构建成功；检查可运行 JAR 内嵌的 `test-agent-api` class 确含两种新操作，`/actuator/health` 状态 UP、前端 HTTP 200、manager 进程存活。
- 定向运行 `WorkspaceFileServiceTest` 清空测试 2 项和 `WorkspaceFileWebSocketHandlerTest` 42 项，全部通过；未对用户真实目录执行破坏性端到端测试。`git diff --check` 通过。
- 提交前回顾各 `.agents/session-log*.md` 近期记录，保留两个既有未跟踪 OCR 数据文件，未纳入暂存。

### Result
- 本机处理文件 WebSocket 请求的 Java 已升级到含新 RPC 的 JAR；请刷新页面后重试，仍需由用户实际操作验证 UI。远端环境及本地客户端安装包未在本次更新或验证。
- 未修改业务代码、稳定 API/事件文档、数据库、配置或部署拓扑；旧服务遇新操作继续失败关闭以保护目录。Windows 脚本当前先停服务再构建，且 Go 依赖不可达时不会自动恢复 manager，后续使用该脚本需注意。

## 2026-09-24 - 移除独立接口测试报文 Skill 并统一脚本生成入口

### Why

- 公共执行链仍把 `GENERATE_MESSAGE` 分流到单独的报文生成 Skill，和现有接口自动化 Markdown 模板生成流程重复；用户明确要求保留旧动作入口，但改由现有脚本生成 Skill 承接。

### What

- 删除独立报文生成 Skill 目录与公共清单、执行 Agent/Skill、输出路径、用户手册目录映射和工作台测试中的引用；公共 Skill 数量由 22 调整为 21。
- `GENERATE_MESSAGE` 作为兼容动作与 `GENERATE_SCRIPT` 共用接口身份参考解析、`generate-api-automation-markdown` 渲染和格式校验，正式产物统一为包含请求报文的 `<案例名称>-接口自动化脚本.md`，不再定义独立报文文件或结果字段。

### How

- 核对 `release` 分支，本次不新增部署节点；检索全仓旧 Skill 名称、目录映射 ID 和旧结果字段，确认无活动引用。
- 原提交被回退后，在当前 release 分支重新应用 472b17816；session log 冲突保留现有记录与本任务记录，不覆盖无关的 components.d.ts。
- 运行接口自动化 Markdown renderer/verifier 的 8 个单元测试、目录映射 YAML/ID 校验、用户手册 VitePress 构建和 `git diff --check`，均通过。公共 Skill 校验器对生成 Skill 通过；对既有 `test-execution` 提示 `metadata.source` 非 `test-agent`，该 frontmatter 未因本次删除而更改。
- 提交前回顾所有提交者的近期 session log，仅纳入本次相关文件；保留工作区其它未提交产物及未跟踪 OCR 数据。

### Result

- 报文生成请求继续可通过兼容动作路由，但生成的是带请求报文的完整接口自动化脚本，不另写报文文件；真实接口/数据库执行约束不变。
- 不涉及 HTTP API、事件、数据库、性能或安全边界改动；兼容层保留旧动作名，消费者若依赖已删除的独立报文文件/字段需迁移到 `generatedFiles` 中的脚本路径。未做真实平台执行或浏览器端到端验收。

## 2026-09-24 - 合并新提交后恢复接口报文兼容动作路由

### Why
- `bbb98c399` 增加请求结构校验和同步 Skill 时，把已删除的 `generate-test-messages` 引用、独立报文文件和旧结果字段带回了公共执行链；需要保留新校验能力并恢复 `afc2aa4bb` 的统一脚本产物约定。

### What
- 仅调整入口/接口执行 Agent、公共执行 Skill 与输出路径，使 `GENERATE_MESSAGE` 继续作为兼容动作进入 `generate-api-automation-markdown`，产出包含请求报文的接口自动化脚本；移除被覆盖恢复的旧 Skill 调用、独立报文文件和 `executionMessageFiles` 引用。
- 保留新提交的 `reqParamStruct` 严格白名单、确定性模板校验、临时目录清理及新 `sync-integrated-api-request-structure`；同步公共配置 README 的实际 22 个 Skill 及归属。

### How
- 比较 `afc2aa4bb..bbb98c399` 的公共配置差异，回顾各提交者近期 session log，逐项检查旧引用与新约束；只恢复被覆盖的路由语义，不整体回退新提交。
- 运行 `generate-api-automation-markdown`、`sync-integrated-api-request-structure`、`resolve-api-automation-references` 三组 Python unittest（12、22、31 项均通过），再校验旧目录/旧引用、Skill 数量与 `git diff --check`；提交后另生成仅包含本次新增修改文件的 ZIP，删除文件另列目录。

### Result
- 兼容入口统一生成脚本，不再定义独立报文产物；无 HTTP API、事件、数据库、性能或安全接口变化，不新增部署节点。用户若依赖旧报文文件/字段，仍需迁移到 `generatedFiles` 的脚本路径；尚未做真实平台端到端验收或企业公共配置发布。
