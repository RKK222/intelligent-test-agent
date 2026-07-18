# frontend

## 工程定位

完全自研测试智能体 Web IDE 前端。`frontend/interaction-visual-demo` 只作为交互参考资料，不纳入 `pnpm-workspace.yaml` 构建；顶层 `frontend-opencode` 是 opencode IDE App 的 Vue/TypeScript/Vite 复刻交付物，作为独立工程单独安装、构建和验收，不纳入 `frontend/pnpm-workspace.yaml`。

公共或应用 Agent/Skill 发布进入存量 Session 排空期时，`/processes/me` 按当前用户返回 `messageSendAllowed=false` 和阻断原因。应用发布先把固定 feature commit 原生 merge 到各服务器相关个人 worktree；存在 dirty 或冲突时不覆盖个人内容，持久化 rollout 保持 retry，相关个人 worktree 全部包含目标 commit 后才登记 dispose 用户并进入排空。前端只在被阻断期间每 5 秒刷新状态，该用户旧 opencode target dispose 后下一轮立即恢复为 true。聊天面板禁用发送与新会话按钮、输入框展示排空提示，后端所有新 opencode 消息入口仍以同一持久化用户级门禁为准。

## 技术栈

- Vue 3
- Vite 8
- TypeScript 6
- Tailwind CSS 4
- vue-router
- Pinia
- @tanstack/vue-query
- dockview-vue（Dockview 官方 Vue 封装）
- Monaco Editor（原生 `monaco-editor`，按需懒加载）
- Vue Flow（`@vue-flow/core`，仅在 Mermaid 可视化编辑时懒加载）
- lucide-vue-next
- @vscode/codicons（仅文件浏览区使用）
- jsonc-parser 3.3.1（引用配置对 `.opencode/opencode.jsonc` 做保留注释的最小字段补丁）
- pnpm workspace

## workspace

```text
apps/agent-web
apps/user-manual
packages/backend-api
packages/event-stream-client
packages/workbench-shell
packages/file-explorer
packages/editor
packages/diff-viewer
packages/agent-chat
packages/terminal
packages/test-runner
packages/ui-kit
packages/shared-types
```

`apps/user-manual` 使用 VitePress 1.6 构建内置用户手册，输出到 `agent-web/public/help/` 并随主应用一起打包。手册使用浏览器本地全文索引，不依赖公网搜索、独立服务或数据库；`agent-web` 的 `dev` / `build` 会先自动构建手册。目录设计章节以标准工程目录为事实源，把开发已有与测试扩展合并为一棵可逐级展开的工程树；目录、Agent/workagent/Skill 名称、物理 Git、实现状态和职责统一在 `directory-mapping.md` 的 frontmatter 中维护，Vue 组件只负责展示。测试公共 Config 已存在的 Agent/workagent/Skill 使用真实名称并标记“已实现”，没有对应定义的规划项标记“未实现”并灰显；应用专属测试 Agent/workagent 归入测试设计、测试执行等具体活动，测试设计应用规约按测试对象类型展开；测试 Agent 下的公共规约和应用规约都属于测试范围，仅由 Git 标签区分测试公共与应用归属。`skills/` 以同级 `coding/`、`test/` 分别收口开发和测试 Skill。`docs/应用架构/` 合并开发应用关系与测试概述、应用场景说明书等场景测试资产，`docs/技术架构/` 只保留开发技术资产。目录名使用中性色，范围标签区分开发、测试、开发与测试、个人本地，Agent 形态标签区分 Agent/workagent，最右侧标签标明开发 AI Git、测试公共 AI Git、测试 AI Git 以及开发业务代码 Git。`agents/`、`skills/`、`docs/` 是多 Git 逻辑合并视图，不是新的物理仓库；页面同时说明 `spec`、稳定测试资产和建设责任，并只保留“整体目录”“内容与责任”两个视图。应用内 Help 固定章节清单必须同步注册该 Markdown，保证首页入口、内嵌页面和宠物问答使用同一内容；宠物问答读取原始 Markdown 时会剥离仅供页面渲染的 frontmatter，只使用用户可见正文。

`agent-web` 每次加载 Vite 配置时按北京时间生成 `VyyyyMMdd.HHmmss` 构建版本，并以只读编译常量固化到 bundle；设置弹窗左侧导航底部展示该版本。普通刷新或静态服务重启不会改变版本，只有重新构建前端产物才会变化。

`packages/editor` 在 Markdown 预览中支持 Mermaid `flowchart`/`graph` 与 `sequenceDiagram` 可视化编辑。Flowchart 提供按“流程图 / 文档与显示”分组的 14 类共享 SVG 节点、轮廓分配的 8/12 个端口和不随画布缩放、可在视口边缘翻转的双列快捷建连菜单；节点无论是否选中都可直接从可见连接点拖出连线，选中节点的连接点外围继续用于移动节点，选中连线可拖动绿色端点更换起止锚点。选中节点还可通过四角外置手柄在 50%–300% 范围内等比缩放，实际节点、端口、ELK 包围盒和路由端点共用缩放后的尺寸；双击节点或连线可就地编辑文字与文字颜色，右侧属性栏可设置节点文字、填充、边框颜色和连线文字颜色。读取兼容旧语法，应用时统一规范化为现代 `ID@{ shape: <短名>, label: "<文本>" }`；颜色使用 Mermaid 原生 `style` / `linkStyle`，比例使用兼容旧版的紧凑私有 metadata。图模型、parser、serializer 与 Vue Flow 画布均留在 editor 包内；应用后只回写当前 Markdown fence，并继续复用工作台 dirty、Git Diff 与 workspace 文件保存链路。

工作台中间 Monaco 源码区默认按可视宽度自动换行。编辑器页脚“复制路径”只复制文件在目标服务器上的真实绝对路径；公共级/应用级 Agent tab 的 `agent-public:`、`agent-workspace:` 合成路径只用于前端身份和路由，不进入剪贴板。左侧个人工作区普通文件支持 Ctrl/Cmd+C/X/V/Z、右键复制/剪切/粘贴/撤销和拖放到目录或根目录；工作区标题与目录行的 `+` 统一按明确目标路径新建或上传一个或多个本机文件，文件/目录行尾 `−` 与 Delete/Del 键共用删除确认，目录删除会递归清理内容；拖放结束后清除目标高亮。文件操作弹框统一使用紧凑工作台面板样式。所有落盘和撤销操作继续走 backend-api 的目标后端文件 WebSocket route/ticket/RPC，只读应用版本副本不展示这些入口。

应用管理员或超级管理员在已选择应用和个人运行态工作区时，可从工作区切换按钮后的“引用配置”入口初始化、同步或经二次确认切换应用资产库分支，并在双栏弹窗选择后端标记的橙色 SDD 根目录。点击左侧已初始化资产库卡片会在同步 POST 返回前打开“创建同步任务、逐服务器同步、汇总同步结果”进度弹层；右侧“刷新 Git 指针”只发起只读核验，不会与同步操作互相追加请求。页面以目标分支/HEAD 对照每台服务器实际 branch/HEAD、在线状态、匹配结果、最近同步和最近核验时间；后端缺少兼容字段时显示“在线状态未知/未核验”，不根据指针自行推断可信状态。活动状态每 2 秒轮询；保存前重新读取当前个人工作区 `.opencode/opencode.jsonc`，再用 `jsonc-parser` 最小更新目标 `references` 节点，文件读写继续走 workspace 文件 WebSocket RPC。保存或分支切换完成后工作台刷新组合文件树：`merge=true` 按 `sdd-folder-name` 合并到工作区一级目录，纯引用节点显示蓝色、工作区已有同名目录保持普通颜色；`merge=false` 以参考别名显示只读一级目录。引用文件使用独立稳定身份和只读编辑 tab，不进入工作区搜索、Git 变更或 `requirements` 目录。保存引用配置后不重启用户进程：运行态空闲时调用 OpenCode 原生 `/global/dispose` 重建当前个人工作区实例，运行中则延迟到任务结束；应用资产库 Git 同步本身不触发个人配置重载。存量进程若缺少 `OPENCODE_REFERENCES_DIR`，仍需平台受管重启才能注入环境。

引用配置的已选仓库标题会在“刷新 Git 指针”左侧展示当前服务器规范化仓库路径；旧后端、缺少引用根参数或历史非法英文名时显示“服务器路径暂不可用”。同步与核验弹层都按仓库、真实 operation、generation 和请求序号隔离，执行期间锁定父弹层和键盘焦点，逐服务器显示等待、处理、重试、失败或离线延后，终态保留到用户手动关闭并把焦点恢复到原卡片或刷新按钮；2 秒状态轮询临时失败会自动继续。

## 本地命令

```bash
cd frontend
corepack pnpm install
corepack pnpm dev
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
corepack pnpm e2e
corepack pnpm e2e:real
```

完整前端检查也可以从仓库根目录执行：

```bash
tools/dev-frontend-check.sh
```

## 本地联调

推荐从仓库根目录使用一键脚本重启三服务，脚本默认读取 `.env.test` 并以 `test` profile 启动，按「后端 → opencode-manager → 前端」顺序逐个先 kill 原进程再启动；前端构建和 dev server 启动前会检查 `node_modules` 是否落后于 `pnpm-lock.yaml`、workspace 配置或各包 `package.json`，过期时自动执行 `corepack pnpm install --frozen-lockfile`；当 `TEST_AGENT_OPENCODE_BASE_URL` 是本地地址时默认启动 Go `opencode-manager`（由它派生 opencode 子进程，不再单独启动 `opencode serve`）：

工作台左侧 Agent 配置树展示公共级 `opencode/` 和工作空间级 `.opencode/` 配置根。超级管理员进入公共级时，前端在所选已初始化服务器上自动挂载当前用户稳定的 `public-{userId}` 分支/worktree；公共分支不包含应用版本，不能切换他人 worktree 或直接编辑共享运行副本。普通用户从共享运行副本只读查看。Git 变更面板按“应用工作空间 / 应用 Agents/Skills / 公共 Agents/Skills”切换作用域：应用工作空间承载普通文件、docs、spec；应用 Agent 精确包含 `.opencode/opencode.jsonc`、`agents/**` 与 `skills/**`；公共 Agent 包含公共仓库的 `agents/**` 与 `skills/**`。应用 workspace 与应用 Agent 只是同一个人 worktree 的两个 Diff 视图，公共 Agent 属于另一套 Git。Agent 文件保存成功立即刷新 Git Changes；应用个人 worktree 保存运行目录定义会在当前任务空闲后只热加载本人，公共保存不热加载，必须推送后才全局生效。

应用工作区暂存通过平台 API 操作真实 Git index，同时仍作为发布文件白名单；未暂存区可一次暂存全部普通文件，或经二次确认后丢弃全部已暂存和未暂存普通文件；已暂存区的一键回退只调用 unstage all，把全部已暂存普通文件移回未暂存区，不丢弃文件内容。后端普通发布会隔离历史 index。冲突期间批量暂存和丢弃全部改动入口禁用，已暂存区一键回退及普通文件逐个 stage/unstage 仍可使用，但按 Git 原生规则禁止提交；冲突文件可在 Monaco 三方合并编辑器中可靠选择结果、保存或取消整次 merge。Git 三个 Tab 直接在 `UNSTAGED/STAGED` 下展示文件，不再重复作用域标题和 worktree 说明；只暂存 `spec/**` 时只展示本地“提交”，混合暂存时会在操作前明确提示提交、推送和仅本地文件数。前端只有在后端明确返回 `remotePushed=true` 时才展示提交并推送成功；连续处理 workspace、应用 Agent、公共 Agent 时，进度页按本轮累计实际提交、远端推送及仅本地 spec 数量，并列出各作用域明细，三类差异全部清空后结束本轮。

当前权限口径补充：公共 Git 只有 `SUPER_ADMIN` 可写；应用级 `.opencode/opencode.jsonc`、`.opencode/agents/**`、`.opencode/skills/**`（含 rules/templates）由 `APP_ADMIN` 管理，普通成员只读；所有托管应用操作仍要求有效应用成员，`SUPER_ADMIN` 不旁路成员校验。应用 feature 副本对所有角色普通文件只读，个人 worktree 普通文件可写；“本地提交”只提交个人分支，“提交并推送”再把非 `spec/**` 选中路径投影并 push feature。push 后各服务器把同一固定 commit 自动 merge 到相关个人 worktree：clean 时立即更新，dirty 时面板提示待同步，真实冲突进入三方编辑器，全部解决后需点击“完成合并”。docs 不触发 dispose；应用 Agent/Skill rollout 在相关个人 worktree 收敛后全局 dispose。`spec/**` 对任何角色都只做本地提交。

文件页进入时默认展开工作空间并把收起的 `Agents` 固定在面板底部，减少文件树被上下分屏压缩；用户展开 `Agents` 后仍可拖拽分隔线调整两区高度。

```bash
./restart-dev-services.sh
```

Windows PowerShell 直接使用同名入口：

```powershell
powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Profile test -EnvFile .env.test
```

连接 `guo` 或其他个人调试环境时显式覆盖 profile 和 dotenv：

```bash
TEST_AGENT_BASE_URL=http://192.168.100.115:8080 \
TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000 \
./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build
```

Windows PowerShell 下使用 `$env:` 写入额外变量；若 `-EnvFile` 指定的 dotenv 已有同名键，以 dotenv 加载结果为准，与 Bash 脚本一致：

```powershell
$env:TEST_AGENT_BASE_URL = "http://192.168.100.115:8080"
$env:TEST_AGENT_FRONTEND_URL = "http://192.168.100.115:3000"
powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Profile guo -EnvFile .env.local -SkipFrontendBuild
```

脚本会从 `TEST_AGENT_FRONTEND_URL` 推导前端监听 host/port，并把 `TEST_AGENT_BASE_URL` 注入为 Vite 的 `VITE_TEST_AGENT_API_BASE_URL`；未显式设置 `TEST_AGENT_BASE_URL` 时，会使用自动探测到的后端内网地址（例如 `http://192.168.100.115:8080`），避免局域网访问前端时浏览器仍请求 `127.0.0.1`。需要指定固定入口时，可在启动前设置 `TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000` 和 `TEST_AGENT_BASE_URL=http://192.168.100.115:8080`，后端 CORS 未显式配置时会自动包含该前端 origin。

本机存在多个 opencode 版本时，在当前使用的 dotenv（默认 `.env.test`，或显式 `--env-file` 指定的文件）里指定 `TEST_AGENT_OPENCODE_BIN`，避免 PATH 命中旧版本。例如：

```bash
TEST_AGENT_OPENCODE_BIN=${HOME}/.opencode/bin/opencode
```

分别启动三个服务：

```bash
cd backend
mvn spring-boot:run -pl test-agent-app -Dspring-boot.run.profiles=local
```

```bash
opencode serve --hostname 127.0.0.1 --port 4096 --cors http://localhost:3000
```

```bash
cd frontend
corepack pnpm dev
```

服务启动后可在仓库根目录执行：

首次引导和内置手册共同说明四个普通用户入口：顶部应用下拉、左下角 workspace/version 选择、工作区小地球引入需求子条目，以及首条消息自动建立对话；设置章节另外说明普通用户个人 SSH Key，以及应用管理员可见的成员、版本库关联和工作空间操作；只选应用不会加载文件树。

右侧主对话在 TestAgent 进程 ready 且工作区可用时允许直接输入首条消息，发送时才创建 Session，不要求先点击“新建对话”；宠物旁路问答仍要求已有真实主 Session。顶部问号打开同源嵌入的用户手册，初始化状态卡可直接定位到对应章节；帮助中心问宠物时复用既有旁路 Run，并把当前 Markdown 章节作为限定资料，不新增后端 API。工作台提供侦探犬、雷达兔、星探狐、巡检小鸟和数据刺猬五种图片宠物，用户可在统一浮层选择按本地日期轮换、每日随机或固定角色，并通过宠物大小滑杆调整 75%–150% 的显示比例，偏好只保存在浏览器本地；旧版变色龙、鸭嘴兽、猫、猫头鹰、吞噬怪和鲨鱼的本地选择会映射到对应新角色。点击宠物后以对话为主体，标题栏的小手柄按钮可进入纯前端俄罗斯方块、扫雷、数独和贪吃蛇；四个入口使用紧凑 2×2 小卡片排布，不新增独立活动栏按钮、后端 API 或服务端持久化状态。

```bash
tools/dev-runnable-loop-check.sh
```

真实三服务联调 E2E 单独执行，不进入默认 `pnpm e2e`：

```bash
tools/dev-phase11-real-e2e.sh --start-services
```

该脚本默认复用已运行的 opencode server 和 `test-agent-app`；传入 `--start-services` 时会启动本地 Postgres、opencode server 和后端，前端由 `frontend/playwright.real.config.ts` 的 Playwright `webServer` 管理。服务日志保留在 `.tmp/phase11-real-e2e/`，脚本不会打印 `.env.local` 或 `.env.test` 中的敏感值。本机 Docker daemon 未运行时，脚本会在启动 Postgres 前直接失败并提示先启动 Docker Desktop，或手动启动后端后不带 `--start-services` 重试。

`frontend-opencode` 独立联调见 `frontend-opencode/README.md`，默认通过 Vite proxy 把 `/api` 转发到 `test-agent-app`。

## 访问边界

- 前端不得直连 opencode server。
- HTTP 请求只能通过 `packages/backend-api`；Run/Diff/runtime 默认使用 `agentId=opencode` 的 `/api/internal/agent/{agentId}/...` 后端 URL。
- 新建 Session 或切换到可交互历史 Session 后，通过 `backend-api.getRunContext(sessionId)` 获取一次会话运行上下文，并只缓存在当前页面内存；同一 Session 的后续 Run 复用该 token。每次发送生成稳定 `clientRequestId`，若后端返回 `CONVERSATION_CONTEXT_REQUIRED` 或 `CONVERSATION_CONTEXT_EXPIRED`，清除该 Session 缓存、重新签发并只重试一次，重试复用原 `clientRequestId`。退出登录、认证用户变化或页面刷新后清空全部上下文，禁止写入 localStorage、sessionStorage、IndexedDB 或持久化 store。
- 历史切换与 Run 启动都使用页面交互代次 fencing：每个异步返回点重新校验认证 token、Session、Workspace 和发起代次，迟到的 Session/workspace/context/Run/active-run 结果不得覆盖当前会话或建立 SSE。历史 Session 的工作区、上下文和正文尚未恢复完成时，发送按钮与父层发送入口都会硬阻断，避免仍以旧 Session 启动 Run。`startRun` 出现超时或 5xx 时，如果同一发起上下文已由 runtime-state 接管 busy Run，则不生成本地 `run.request.failed`；响应带 `clientRequestId` 时优先精确匹配，旧响应缺失时才使用同 Session 与同交互代次的兼容判断。
- 普通工作区文件树、搜索、对话文件卡片与顶部 tab 共用 workspace 文件加载器；公共级/应用级 Agent 配置树与顶部 tab 共用独立 Agent 文件加载器。读取分别通过平台文件 WebSocket `workspace.read` / `agent-config.read` frame 完成，不要求出现 Fetch/XHR。两类 tab 都显式区分 loading/loaded/error、稳定磁盘快照与用户内容修订代次；workspace 或 Agent scope/workspace/worktree/server 上下文、同路径请求代次、tab 存在性和修订代次共同丢弃迟到响应。合法空文件为 loaded，首次 loading 不挂载空 Monaco，刷新失败或读取期间发生过编辑（含随后已保存/回退 clean）时保留已有内容；Agent 顶部 loaded tab 使用缓存，error/旧版未标记 tab 重新读取，重试不得误发 `workspace.read`。
- 企业内浏览器基线按 Chromium 108 兼容，`agent-web` 生产构建显式使用 `build.target=chrome108` 和 `build.cssTarget=chrome108`；涉及 Web Crypto、Clipboard 等安全上下文 API 时必须先做能力检测，内网 HTTP 访问不能直接假设这些 API 可用。个人 SSH key 加密优先使用 Web Crypto，HTTP 内网下 `crypto.subtle` 不可用时使用 node-forge 纯 JS AES-GCM + RSA-OAEP/SHA-256 回退，仍禁止明文提交。
- 小宠物拖动在 Chromium 108 下使用 `window` 捕获阶段接收 `pointermove/up/cancel`，避免编辑器或工作台子组件停止事件冒泡后中断拖动；仍不依赖 pointer capture。
- RunEvent SSE 和用户级运行态 fetch SSE 只能通过 `packages/event-stream-client`。单 Run SSE 的应用层身份固定为标量 `(runId, sessionId, token)`，同一 Run 的对象投影不得重建连接；终态先建立 500ms hold 再更新状态，标题待定继续复用原连接，legacy 终态反馈恢复按 runId 合并为一条、最多 3 轮的兼容链。连接内部的 durable 游标、事件去重和 transport reconnect 仍由公共 client 维护。用户级 runtime-state SSE 是运行恢复主入口，使用 `/api/internal/platform/opencode-runtime/sessions/runtime-state/events` 携带 Bearer Token，按 1/2/5/10/30 秒退避重连；连接期间不并行查询 runtime-state HTTP，也不做 1.5 秒 active-run 热轮询。摘要里的非终态 `runId/runStatus` 直接接管 RunEvent SSE；只有流不可用时，当前 Session 才执行一次 `backend-api.getActiveRun(sessionId)` fallback，短连接反复收到首帧后立即断开仍视为同一故障，连接稳定保持 5 秒后才允许后续新故障再次 fallback。收到 `run.snapshot.reset` 时，event client 只投递事件且不推进 durable 游标；agent-chat reducer 保留平台持久消息、清空当前 Run 实时投影并按 snapshot 顺序重放，Workbench 同步清空独立 Diff/实时跟随状态。运行中点击新建对话只清空当前视图和关闭当前 RunEvent SSE，不调用 cancel/abort；前端只把 RunEvent 应用到当前订阅且仍为页面活动态的 Run。`session.status.retry` 会在右侧时间线展示原因和 60 秒倒计时，等待期间仍视为运行中，第 1/2 次到期后用最近一次 Run 草稿自动重试，第 3 次后本地兜底为失败；新 Run 请求和后到成功/取消终态会清理上一轮 `run.failed` 失败卡与 SSE 连接错误提示，避免旧 `Streaming response failed` 覆盖后续轮次。
- 右侧 Agent 面板的“原始输出”只展示当前页面生命周期内，前端捕获的浏览器与平台后端 HTTP 请求/响应正文和 RunEvent SSE `MessageEvent.data`。HTTP 与 SSE 共用 `prepareRawOutputBody` 安全边界，在写入按 Session 划分的页面缓存前递归把所有层级、大小写不敏感的 `contextToken` 替换为 `[REDACTED]`，再执行长度截断；它不记录 opencode server 原始事件、不落库，刷新或换浏览器后不保留。
- `apps/agent-web` 负责组合页面；业务能力必须沉淀到对应 package。
- opencode Web App 复刻以运行态能力为范围，交互行为参考 `opencode-source/opencode-1.17.8/packages/app`；顶层 `frontend-opencode` 承载 Vue/Vite 复刻工程，opencode `packages/web` 官网/文档/公网分享轮询不进入默认边界。
- 当前已接入 backend-api runtime 方法、Agent/Provider/Model 运行态选择（底部 Agent 下拉按 opencode `local.agent.list()` 过滤为 primary+all 且排除 subagent/hidden，输入框 `@agent` 候选按 prompt autocomplete 过滤为 subagent+all 且排除 primary/hidden，当前用户 opencode 健康状态 ready 后自动刷新运行态目录）、右上角成员应用切换、应用版本工作区/个人工作区切换与同步、超级管理员服务器工作空间选择、用户级 session history 远端搜索/分页（默认 30 条、显示应用/工作区/版本、按更新时间倒序、不拼接本地伪历史，历史按钮数字只统计第一页 30 条中的未完成会话）、历史会话完整消息渲染与所属应用/工作区不可切换时的只读态、按成功主 Run 提供的整轮满意/不满意反馈（每轮使用 `runId` 独立定位，成功历史 Run 永久保留入口，无 assistant part 也可评价，失败/取消/子 Agent 不展示，历史状态和反馈每批最多恢复 100 个 Run）、message part reducer、active run 恢复入口、permission/question dock、Todo、工作区上下文附件（Monaco 选区、文件树文件、编辑器 Tab 文件添加到对话，输入框上方预览/删除/清空，发送时前端结构化拼接 prompt，并按字符数拦截超长内容）、上传附件前端弹窗样式（后台上传暂未接入）、busy follow-up 队列、输入法组合输入阶段 Enter 防误发、后台每 10 秒调用弱健康接口检测当前用户 opencode 进程健康，弱健康不健康时复查 `/processes/me`，MCP/LSP 状态 5 分钟刷新一次且 VCS 状态保持 30 秒刷新、Monaco 选区上下文、统一进入可恢复 Run 的 slash command palette 与参数表单补全、`@` context picker、Run/Session/VCS Diff 来源切换、Diff hunk 导航与懒加载 editor、运行中实时追踪写文件工具变更、MCP/LSP/VCS 状态摘要、顶部应用切换左侧 Agent/Skill/MCP/Plugin 已加载数量摘要和详情弹层、左下角设置模态（应用与工作空间管理含版本库内外部部署模式、版本库英文名、创建工作空间进度轮询、个人 SSH key、用户管理查询/创建测试用户和超管直接调整角色；无应用配置权限时显示角色提示）、仅 `SUPER_ADMIN` 可见的系统管理入口（定时任务管理 + 运行管理 + 运营分析，运行管理展示最新 CPU/内存并在点击容器或后端 Java 进程后用 ECharts 展示 Redis 48 小时指标趋势；运营分析展示用户漏斗、使用强度、Run 结果、满意度、Diff 采纳、token 强度、趋势、热力、排行、明细和 CSV 导出且不展示费用字段）、`/s/[sessionId]` 只读 transcript 和受控 PTY terminal panel；公开 share 授权、per-file/per-message 回滚和真实三服务联调 E2E 仍按后续批次推进。
- 运行管理的后端 Java 进程表格和趋势图会展示服务器 CPU/load/内存/swap/磁盘、Java 进程 CPU/RSS/FD、JVM heap/non-heap/direct/mapped、GC、线程等可空字段；旧后端缺失新增字段时继续显示 `-` 或使用旧字段回退，趋势图保留断点。

## UI 与主题边界

- 全局 theme token、Figma Web IDE 风格 activity rail、Dockview/Monaco 视觉适配、滚动条、panel chrome 和轻量动画由 `apps/agent-web/src/styles/globals.css` 承载；包内组件只消费这些 token，不在业务组件里复制整套主题。
- `packages/ui-kit` 只提供 Button、Badge、Input、Tabs 等无业务状态基础控件；运行态选择、permission/question、terminal 和 Diff 语义仍放在对应 feature package。
- 面板、toolbar、terminal、Diff、Agent timeline 和文件树必须保持稳定尺寸。Agent timeline 主路径使用 `packages/agent-chat/src/opencode-like` 的 `.oc-*` 时间线，把 reasoning 思考过程、上下文工具组、Skill/Tool 调用、文件引用、Diff 摘要与最终回答分块展示并保留独立滚动区域；`session.status.retry` 这类上游等待重试/限额状态必须转成 runtime retry 行展示，按前端首次收到事件的时间显示固定 60 秒倒计时，不能停留在普通“思考中”；右侧 Agent 对话框继续消费全局 `--ta-*`/`--ta-chat-*` token，避免 hover、streaming 文本、warning、hunk 导航或状态徽标导致布局跳动。
- 旧 `.figma-chat-*` 气泡消息循环、`AgentCard`/`TimelineCard` 和 `MessageParts` 旧 part 组件只作为作废兼容代码保留；新增对话展示能力必须落在 `packages/agent-chat/src/opencode-like`。
- 真实三服务 E2E 尚无最新通过记录；当前只能认为 mock E2E 和单元测试覆盖了主流程，不能把真实联调标记为完成。
