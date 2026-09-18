## 2026-09-16 - 后端 AAM 基址由 zfw.sdc.cs.icbc 改为 tcds-prod.sdc.icbc

### Why

- 用户要求把 `TEST_AGENT_AAM_BASE_URL` 的主机从 `http://zfw.sdc.cs.icbc` 改为 `http://tcds-prod.sdc.icbc`（不带端口）。
- 前端生产/开发环境的 AAM 登录入口早在本 `release` 已包含的远端提交 `c2775c280` 中改为
  `http://tcds-prod.sdc.icbc/aam/onlyLogin/`；当时 `aamLogin.js` 里的旧地址被有意保留为兜底常量（详见 2026-09-02 条目），
  于是出现了「前端登录在 tcds-prod、后端验真在 zfw」的跨主机不一致。本轮按用户明确要求把后端基址与前端入口统一到同一 origin。
- 该值不是构建期 `--env-file` 可覆盖的普通变量：它由 `package-two-backend-complete.sh` 的
  `normalize_backend_node_archive()` 硬编码写入节点归档的 `config/backend.env`，且打包侧与部署侧各有一处精确断言，
  必须成对修改，否则现场部署会被门禁拦下。

### What

- 打包写入与断言：`deploy/internal/package-two-backend-complete.sh`（写入值、`grep -Fxq` 断言各 1 处）。
- 部署断言：`deploy/internal/deploy-multi-backend-node.sh` 的 `require_exact_value`。
- 后端默认值与模板：`backend/test-agent-app/src/main/resources/application.yml`、
  `deploy/internal/backend.env.example`。
- 前端兜底常量与其单测：`frontend/apps/agent-web/src/utils/aamLogin.js`、`tests/aam-auth-callback.test.ts`。
  前端 `.env.production` / `.env.development` 原本已是新值，未改动；`.env.localhost` 保持 `127.0.0.1` 不变。
- 文档：`backend/README.md`、`docs/deployment/backend.md`、`docs/deployment/frontend.md`、
  `deploy/internal/README.md`、`deploy/internal/MULTI-BACKEND.md`（节点配置示例 2 处）。
- `TEST_AGENT_TCDS_BASE_URL=http://tcds-prod.sdc.icbc:9080` 及其它主机（`mimo.sdc.cs.icbc`、`ai-code.sdc.icbc`）未改动；
  AAM 与 TCDS 现在同主机、不同端口（AAM 走 80）。

### How

- 全仓扫描确认 `zfw.sdc.cs.icbc` 仅剩 3 处 gitignore 的构建产物（`frontend/apps/agent-web/dist/`、
  `deploy/internal/dist/frontend/`、`backend/test-agent-app/target/classes/`），均由构建或打包过程重新生成，未手工改写。
- 前端 `vitest run apps/agent-web/tests/aam-auth-callback.test.ts` 通过（5 tests）。
- `corepack pnpm --filter @test-agent/agent-web build` 成功；新产物中 `tcds-prod.sdc.icbc/aam/onlyLogin/` 出现 7 处，
  已无任何 `zfw.sdc.cs.icbc` 残留，证明 dist 中的旧值确实来自 `aamLogin.js` 兜底常量而非环境文件。
- 两个 shell 脚本 `bash -n` 通过；`git diff` 核对 11 个文件 13 处改动全部为本次地址替换。
- 复核 `replace_or_append_env_value` 用 `awk 'index($0, key "=") == 1'` 按行首精确匹配整行替换，
  不会误伤同为 `tcds-prod.sdc.icbc` 主机的 TCDS 行。

### Result

- 代码、脚本、节点配置模板与文档中的 AAM 基址已统一为 `http://tcds-prod.sdc.icbc`，打包与部署两侧断言值一致。
- 现场部署时会由 `backup_and_install` 用包内新 `config/backend.env` 覆盖 `/data/testagent/config/backend.env`
  （旧文件按既有流程备份），因此现场 Java 下次升级后即改为访问新 origin。
- **未验证项**：没有真实连通性验证 `http://tcds-prod.sdc.icbc` 的 80 端口确实提供 AAM 的 `/aam/checkLogin`。
  若该验真接口只存在于 `zfw` 而 `tcds-prod` 80 端口只提供登录页，登录会返回 503（`EXTERNAL_API_UNAVAILABLE`），
  现场升级前需与 AAM 侧确认。
- 本次不新增部署节点，不涉及 HTTP API、DTO、RunEvent/SSE、数据库、SQL、Flyway、generated SDK 或 OpenCode 只读源码；
  未修改 `.env.local`。

## 2026-09-18 - 本地工作空间开放聊天附件（capability 开放 + 本地运行时降级路径投递）

### Why

- 用户反馈：在本地工作空间对话时输入框的「上传附件」按钮不可用，问是否为功能限制；随后明确要求开放限制并完成改造，
  让本地工作空间也能正常上传聊天附件。
- 追查后确认是两处**有意关闭**而非 bug：① 本地工作区/实例 capability 里 `attachments=false`，前端据此禁用入口；
  ② `RunApplicationService.startRun` 用 `LocalRuntimeCapabilityGuard` 对任何 `workspace_attachment` part 直接 fail-closed，
  防止伪造请求绕过前端。两层都关，所以即使前端放开也会被服务端拒绝。
- 关掉的真实技术原因只有一条：本地工作区在**用户机器**上，服务端 JVM 既不能把客户端盘符（Windows `E:\`）、UNC 或
  其它平台形态的路径解析成本机 `file://` URL，也不能把文件正文内联进 prompt——本地隧道
  `LocalClientOpencodeWebClientTransport` 的 `MAX_REQUEST_BODY_BYTES` 只有 1 MiB。但这两点只否掉"服务端构造 file URL / 内联正文"，
  并不否定"投递工作区相对路径、由客户端 OpenCode 自己读"这条路。
- 本地工作区分片上传 RPC 本身早已具备：`LocalClientFileRpcHandler` 已支持 `workspace.upload.begin/chunk/complete`，
  `LOCAL_WORKSPACE_OPERATIONS` 也已包含这些操作，因此**不需要新增上传协议**。

### What

- capability 开放（3 处 + 1 处已为 true 未改）：`WorkspaceApplicationService.localCapabilities()`、
  `LocalWorkspaceApplicationService`、`LocalClientInstanceApplicationService` 的 `attachments` 由 `false` 改为 `true`。
- 服务端守卫移除：`RunApplicationService` 删除 `startRun` 里针对本地工作区 `workspace_attachment` 的
  `requireWorkspaceSupported` 拦截，同时删除已无用的 `LocalRuntimeCapabilityGuard` 字段、`configureLocalRuntimeCapabilityGuard` setter 与 import
  （该类仍被 agentConfig / terminal / collaboration 入口使用，未删除）。
- 投递形态按运行时降级：`toAgentPromptParts/toAgentPromptPart/toAgentFilePart` 增加 `RuntimeKind` 参数，
  新增 `usesWorkspacePathDelivery(part, runtimeKind)`——`LOCAL_CLIENT` 时**无条件**走工作区相对路径 text part，
  忽略前端的 `deliveryMode=native`；服务端运行时保持原 native `file://` 分流不变。
- 文案与注释对齐新语义：`AgentWorkbench.vue` 3 处提示、`FigmaChatPanel.vue` tooltip 与 prop 注释、
  `WorkspaceFileWebSocketHandler` 局部注释由"本地工作区首版不支持"改为中性表述。
- 文档同步：`docs/api/http-api.md`（capabilities + Run parts 两节）、`docs/architecture/local-opencode-client.md`、
  `docs/deployment/local-opencode-client.md`（新增 Windows 验收项）、`frontend/README.md`、
  `backend/test-agent-opencode-runtime/README.md`、`frontend/apps/user-manual/docs/guide/conversation.md`。
- 新增单测 `RunApplicationServiceTest.localRuntimeDowngradesNativeWorkspaceAttachmentToWorkspacePath`。

### How

- 先确认前端 capability 解码是通用的（`backend-api` 只按字段透传，agent-web 用 `selectedAttachmentsEnabled` 驱动 UI），
  所以只需改数据源，**前端无逻辑改动**，只改文案。
- 用 `mvn -pl test-agent-opencode-runtime -am -Dtest=RunApplicationServiceTest test` 先跑定向测试，再跑
  `mvn -pl test-agent-opencode-runtime,test-agent-workspace-management,test-agent-system-management -am test` 全量，
  两次均 `BUILD SUCCESS`（opencode-runtime 79 项、system-management 78 项，0 失败）。
- 全仓核对 `"attachments"` 的 capability 写入点，确认没有遗漏的第 3 份本地 capability 表；
  确认 `LocalRuntimeCapabilityGuardTest` 用的是 mock Repository 且守卫本身对本地工作区无条件 fail-closed，
  因此改动 capability 值不会影响该测试语义。
- 复核 `git branch --show-current` 为 `release`，符合 AGENTS.md 第 1 条（不新增部署节点）。

### Result

- 本地工作空间对话现在可上传聊天附件；文件经既有工作区分片上传 RPC 落到本地目录 `.testagent/attachments`，
  下一轮 Run 只投递受控工作区相对路径，由客户端 OpenCode 用自己的 Read 工具读取，图片/PDF 仍由该工具按原生附件投递给模型。
- 本地隧道不再有附件相关的请求体风险：服务端不构造客户端 `file://`、不内联正文，`deliveryMode=native` 在 `LOCAL_CLIENT` 下被强制忽略。
- 不涉及数据库/Flyway、RunEvent/SSE wire 格式、generated SDK、OpenCode 只读源码与环境配置；`parts` 请求体字段无变更，
  旧客户端与旧后端仍可忽略新增来源元数据。
- **未验证项**：未做 Windows 客户端真机验证（`E:\` 盘符路径下客户端 OpenCode 用相对路径读取附件），
  已把该项写入 `docs/deployment/local-opencode-client.md` 的现场验收清单。
