# frontend

## 工程定位

完全自研测试智能体 Web IDE 前端。生产工作台的外围 shell 使用纯白、浅雾蓝与工行红配色：36px 顶部外层和三栏之间的 8px 间隔复用现有弹框常见的浅雾蓝画布，活动栏融入画布；左侧文件区、中间编辑区和右侧 Agent 区作为带 8px 圆角、发丝边框和轻阴影的纯白悬浮面板，三栏顶部、工作区/Agent 目录加载态以及中间无文件预览态也保持纯白。外围导航与选中态消费隔离的 `--ta-shell-*` token。Logo 直接使用已确认的初版耳机/拱形品牌图形 PNG，保留原图轮廓和比例，图形使用低饱和暗红实色 #7f1e2b，中文品牌字标使用黑色，英文副标题使用深红以呼应图形。中间编辑器与右侧 Agent 对话的内部样式继续消费原有变量，不跟随 shell 变色。`frontend/interaction-visual-demo` 只作为交互与视觉参考资料，不纳入 `pnpm-workspace.yaml` 构建；其中 `cloud-workbench.html` 保留当前布局并对照“云白工行红 / 纯雪白 / 鼠尾草灰”三套外层配色。顶层 `frontend-opencode` 是 opencode IDE App 的 Vue/TypeScript/Vite 复刻交付物，作为独立工程单独安装、构建和验收，不纳入 `frontend/pnpm-workspace.yaml`。

顶部按“36px 首行 + 8px 面板间隔”的 44px 视觉带统一上下居中：Logo 左对齐；应用、工作空间、版本三个入口在 Logo 末端与右侧工具组起点之间的网格列正中合并为 34px 高的连续“上下文舱”，共享纯白表面、11px 圆角、发丝边框与轻阴影，内部分隔线表达三层工作上下文，左侧 3px 工行红信号条提供品牌定位，左右留白相等。各分段保留原宽度、菜单、加载和切换回调；悬停只提升当前分段，键盘焦点与展开态使用柔红背景和工行红反馈。三个下拉菜单统一为 12px 圆角、双层轻阴影、8px 项目圆角和柔红选中态，不改变菜单内容、分组、禁用语义或定位逻辑。书本手册、透明底细框的 Agent/Skill/MCP/Tool/Plugin 数量摘要和单字用户名依次固定在右侧。资源摘要详情可从左边缘拖拽调宽并进入页面内全屏。手册默认透明无框，弹框打开期间保持与最左活动栏一致的柔红底、深红图标和工行红定位标记；单字用户名为 12px。顶部工作空间/版本与左下角保留入口复用同一数据和切换回调；顶部选定工作空间后，单版本默认选中该项，多版本默认选中最新项，不改变对话逻辑。

全局顶部反馈消息只用于告知结果，不拦截其下方编辑器和工作台控件的鼠标操作；消息关闭按钮保持可点击，避免保存或运行态刷新提示打断连续编辑。

超级管理员可在工作台任意位置于 2 秒内连续按 3 次 Shift，直接临时打开系统管理的问题排查入口；手势在捕获阶段监听，即使焦点控件阻止键盘事件冒泡也能触发。该手势不是鉴权凭据，不存在部署侧激活暗号。排查授权令牌只保存在页面内存，目标选择和切换逐次审计；页面严格关闭文件写入、加入对话、下载、终端、Git、Agent 配置和批量导出能力。

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
- `@tdesign-vue-next/chat` 0.6.0（仅独立 `/workflow-chat` 路由懒加载）
- pnpm workspace

## workspace

```text
apps/agent-web
apps/user-manual
packages/backend-api
packages/event-stream-client
packages/workflow-api-client
packages/workflow-chat
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

`/workflow-chat` 是与 OpenCode/LobeHub 对话隔离的长程任务入口。只有构建期 `VITE_TEST_AGENT_WORKFLOW_ENABLED=true` 且用户为超级管理员时才展示活动栏按钮并允许深链接；缺失或其它值一律关闭。当前 release 企业包注入 `false`。启用后的新建空对话直接展示仓库/分支/模式/智能体输入卡，不要求先调用意图模型换取表单。`packages/workflow-api-client` 直接访问同源 Python `/workflow-api/v1/**` 并使用带 Authorization/`Last-Event-ID` 的 fetch SSE；`packages/workflow-chat` 提供 TDesign Chat、结构化输入、进度、取消、报告版本和局部重分析。两包不依赖 `backend-api`、`event-stream-client` 或现有 `agent-chat` 状态。

本地Vite把`/workflow-api/**`直接代理到独立Python，默认目标为`http://127.0.0.1:8090`，可用`TEST_AGENT_WORKFLOW_API_URL`覆盖；Python仍按`workflow-service/README.md`独立启动，不经过Java。代理未配置或Python未启动时，客户端只展示脱敏的路由/服务诊断，不解析或回显SPA HTML正文。

`packages/editor` 在 Markdown 预览中支持 Mermaid `flowchart`/`graph`、`sequenceDiagram` 与 `stateDiagram`/`stateDiagram-v2` 可视化编辑。Flowchart 提供按“流程图 / 文档与显示”分组的 14 类共享 SVG 节点、轮廓分配的 8/12 个端口和不随画布缩放、可在视口边缘翻转的双列快捷建连菜单，选中备选图形后菜单立即收起；节点无论是否选中都可直接从可见连接点拖出连线，选中节点的连接点外围继续用于移动节点，选中连线可拖动绿色端点更换起止锚点。选中节点还可通过四角外置手柄在 50%–300% 范围内等比缩放，实际节点、端口、ELK 包围盒和路由端点共用缩放后的尺寸；双击节点或连线可就地编辑文字与文字颜色，右侧属性栏可设置节点文字、填充、边框颜色和连线文字颜色。Sequence 使用递归 AST、专用时序场景和“元素 / 结构 / 属性”单侧栏，支持参与者、消息、Note、生命周期和常用组合片段任意嵌套。State 使用递归 Scope/Region 模型和“概览 + 聚焦”画布，支持复合/嵌套状态、并发 Region、开始/结束、Choice、Fork/Join、Note、各层方向、标签转换、自循环、状态说明与限定直接样式；同一聚焦层展示全部并发 Region。三类图各自维护 parser、serializer、校验和布局，按连接规则复用画布拖线能力；应用后只回写当前 Markdown fence，并继续复用工作台 dirty、Git Diff 与 workspace 文件保存链路。

工作台中间 Monaco 源码区默认按可视宽度自动换行。编辑器页脚“复制路径”只复制文件在目标服务器上的真实绝对路径；公共级/应用级 Agent tab 的 `agent-public:`、`agent-workspace:` 合成路径只用于前端身份和路由，不进入剪贴板。左侧个人工作区普通文件支持 Ctrl/Cmd+C/X/V/Z、右键复制/剪切/粘贴/撤销和拖放到目录或根目录；工作区标题与目录行的 `+` 统一按明确目标路径新建或上传一个或多个本机文件，文件/目录行尾 `−` 与 Delete/Del 键共用删除确认，目录删除会递归清理内容；拖放结束后清除目标高亮。文件操作弹框统一使用紧凑工作台面板样式。所有落盘和撤销操作继续走 backend-api 的目标后端文件 WebSocket route/ticket/RPC，只读应用版本副本不展示这些入口。
工作台中间 Monaco 源码区默认按可视宽度自动换行。左侧个人工作区普通文件支持 Ctrl/Cmd+C/X/V/Z、右键复制/剪切/粘贴/撤销和拖放到目录或根目录；仅可写纯 `WORKSPACE` 文件和目录可作为拖动源，源行提供抓取/半透明反馈；只读、纯 `REFERENCE` 和 `MIXED` 条目不可拖。合法目录和根空白区显示蓝色落点；当前父目录、自身、被拖目录的后代、文件行、纯引用目录和只读目录不接受拖入，带 `workspacePath` 的 `MIXED` 目录可作为工作区侧落点接收工作区条目。工作区标题与目录行的 `+` 统一按明确目标路径新建或上传一个或多个本机文件，文件/目录行尾 `−` 与 Delete/Del 键共用删除确认，目录删除会递归清理内容；拖放结束后清除目标高亮。移动成功会迁移已打开子文件 tab 与展开路径、刷新组合树和 Git Diff，并支持既有反向移动撤销。文件操作弹框统一使用紧凑工作台面板样式。所有落盘和撤销操作继续走 backend-api 的目标后端文件 WebSocket route/ticket/RPC，只读应用版本副本不展示这些入口。

托管 Workspace 切换时，工作台将个人 worktree 身份与目标运行态 Workspace 一起在文件树加载前更新；历史 Session 和服务器目录入口按 `versionId` 复用已有个人工作区列表，并以运行态 Workspace ID 精确匹配。因此个人 worktree 不需要刷新页面即可保持新增、上传和删除能力，无法匹配个人记录的版本副本仍保持只读。

工作区和 Agent 配置上传直接传递浏览器 `File/Blob`，backend-api 只对当前分片调用 `Blob.slice()` 和 Base64 编码，同一连接按 begin/chunk/complete 顺序发送，不把整个文件读入前端内存，也不设置前端总大小上限。上传期间全局紧凑遮罩阻断重复操作，展示当前文件、文件数、字节进度和总百分比，成功或失败后关闭。读取超过后端一次性预览阈值的 UTF-8 文件时，中间区域切换为 IDE 风格的渐进只读预览：先显示约 512 KiB 首段，提供“继续加载一段”和“加载全部（可能卡顿）”，可读取到文件末尾；警告条持续展示已加载字节/总字节和完整加载的内存、Monaco 卡顿风险。新增分段通过 Monaco 尾部增量 edit 追加，不反复 setValue 重建已有正文；上传、移动、改名、删除和 Git 操作不受一次性预览阈值影响。

应用管理员或超级管理员在已选择应用和个人运行态工作区时，可从工作区切换按钮后的“引用配置”入口初始化、同步或经二次确认切换应用资产库分支，并在双栏弹窗选择后端标记的橙色 SDD 根目录。点击左侧已初始化资产库卡片会在同步 POST 返回前打开“创建同步任务、逐服务器同步、汇总同步结果”进度弹层；右侧“刷新 Git 指针”只发起只读核验，不会与同步操作互相追加请求。页面以目标分支/HEAD 对照每台服务器实际 branch/HEAD、在线状态、匹配结果、最近同步和最近核验时间；后端缺少兼容字段时显示“在线状态未知/未核验”，不根据指针自行推断可信状态。活动状态每 2 秒轮询；保存前重新读取当前个人工作区 `.opencode/opencode.jsonc`，再用 `jsonc-parser` 在同一正文中最小更新目标 `references.{alias}` 与 `permission.external_directory` 的所选目录精确 `allow`，文件读写继续走 workspace 文件 WebSocket RPC。权限缺失、冲突或被后置宽泛规则覆盖时，即使引用字段未变也可直接“更新”；不会写仓库级或全局 `* allow`。保存或分支切换完成后工作台刷新组合文件树：`merge=true` 按 `sdd-folder-name` 合并到工作区一级目录，纯引用节点显示蓝色、工作区已有同名目录保持普通颜色；`merge=false` 以参考别名显示只读一级目录。引用文件使用独立稳定身份和只读编辑 tab，不进入工作区搜索、Git 变更或 `requirements` 目录。保存引用配置后不重启用户进程：运行态空闲时调用 OpenCode 原生 `/global/dispose` 重建当前个人工作区实例，运行中则延迟到任务结束；应用资产库 Git 同步本身不触发个人配置重载。存量进程若缺少 `OPENCODE_REFERENCES_DIR`，仍需平台受管重启才能注入环境。

引用配置的已选仓库标题会在“刷新 Git 指针”左侧展示当前服务器规范化仓库路径；旧后端、缺少引用根参数或历史非法英文名时显示“服务器路径暂不可用”。同步与核验弹层都按仓库、真实 operation、generation 和请求序号隔离，执行期间锁定父弹层和键盘焦点，逐服务器显示等待、处理、重试、失败或离线延后，终态保留到用户手动关闭并把焦点恢复到原卡片或刷新按钮；2 秒状态轮询临时失败会自动继续。

超级管理员服务器工作空间选择器支持视口内缩放、页面内全屏/还原，也可像用户手册一样通过真实应用 URL 打开普通浏览器新标签页；URL 只保留入口标记，当前服务器和目录通过同源 `sessionStorage` 交接，不写入地址栏或浏览历史。

### Agent / Skill / MCP / Tool Hub

`apps/agent-web` 的 activity rail 在代码/监控入口下方提供统一 Agent / Skill / MCP / Tool Hub。Agent 与 Skill 延续能力市场结构，包含远端能力概览、发现/分类目录、当前应用引用库、待更新收件箱、能力卡片和引用应用清单；Skill 目录额外按日常工作（Worker）、测试（Test）、代码（Code）和其他筛选，测试细分测试设计、测试数据构造、测试执行、测试分析，代码细分白盒分析。用户推送默认进入“其他”，只有超级管理员可在详情页调整分类。MCP 与 Tool 复用顶部已加载的运行态目录，只读展示连接状态、工具标识和说明，不引入发布或引用语义。Hub 详情从左边缘拖拽调宽，支持页面内全屏。所有用户可读取远端精确快照，应用管理员可发布、引用、取消并重新引用，以及确认三方合并冲突；取消关系会立即退出应用引用库。`packages/backend-api` 统一承载 Hub HTTP 与平台文件 WebSocket 调用，`packages/shared-types` 保存兼容 DTO。

活动栏页面级入口使用稳定 URI：工作台 `/workbench`、工具箱 `/toolbox`、记忆 `/memories`、超级管理员控制台 `/system`、能力库 `/hub`，左下角设置弹窗使用 `/settings`。历史根路径 `/` 只作兼容入口并跳转到 `/workbench`；浏览器刷新、前进/后退和登录回跳均以命名路由恢复对应页面，工具箱、记忆、控制台与能力库继续共用沉浸式布局快照。通用问答 `/lobehub/launch` 和长程任务 `/workflow-chat` 保留既有发布开关与独立页面边界。

### 通用长期记忆中心

`/memories` 是受登录保护的沉浸式路由，活动栏 `BrainCircuit` 入口与 `/toolbox`、`/system`、`/hub` 共用布局快照和浏览器前进/后退恢复。页面固定分为“我的记忆、团队记忆、Skill 提案”：个人记忆可选择全局或当前 Application 范围；团队记忆只能由用户从自己已生效的个人记忆手工提交，并由当前 Application 的 `APP_ADMIN` 审核，不从对话自动产生。Skill 提案审核通过后才生成可编辑 `SKILL.md`，实际文件、Git 和发布仍进入既有 Hub 流程。证据只保留 `sessionId`、`sessionTitle`、`runId` 和 `transcriptAvailable` 等安全引用，不复制对话正文；所有有权查看记忆的人都能看到对话标题和 ID，只有 Session 所有者才能打开 `/s/{sessionId}` 阅读完整原始对话。白名单未开放时页面显示无侵入空态，既有对话保持原行为。

成功 Run 的完成摘要通过批量 HTTP 恢复实际注入记录，仅在记录非空时显示“参考了 N 条记忆”；不修改 RunEvent SSE，也不根据检索候选猜测使用情况。学习链路把当前会话的 USER/ASSISTANT 消息作为一次请求上下文，使用 Mem0 原生 `add(messages, infer=true)` 且不传自定义提示词；平台和 Mem0 独立库都不新建对话副本。系统管理“记忆能力”集中显示 Mem0 节点、固定 CHAT 模型、可空的企业 Embedding profile、固定 CPU BGE profile、双集合投影状态、积压数和灰度白名单。白名单通过平台用户目录搜索选择。页面复用 `--ta-shell-*` 主题变量，个人蓝、团队青、候选琥珀和冲突红只承担记忆治理语义；健康卡和配置区按嵌入面板宽度自动换列，在暗色、窄屏、Reduced Motion 和中栏布局下不产生横向滚动。

应用源码快照的仓库、分支、目录树、物化、保留期调整、重试、打开、最近选择和持久化操作查询统一由 `packages/backend-api` 调用平台 workspace-management API；`listAppSourceTree` 保持节点数组语义，新的 `getAppSourceTreeSnapshot` 在同一 URL 上请求 `includeCommit=true` 并返回 `{targetCommit,nodes}`，调用方把该固定提交直接作为物化 `expectedTreeCommit`。选择项使用 `{path,type}`，下载状态固定为 `NOT_DOWNLOADED/DOWNLOADED_ACTIVE/DOWNLOADED_EXPIRED/PERSONAL_OCCUPIED`。物化进度使用独立的一次性 ticket WebSocket；client 单次连接不自行重连，意外 `error/close` 会向调用方报告安全失败，工作台按有界退避串行执行“数据库 snapshot → 新 ticket → 新 WebSocket”。主动关闭只停止观察，不取消后台任务。保留期调整是同步 PATCH，不创建进度连接。`packages/shared-types` 保存严格判别的安全 DTO/envelope：成功帧必须完整包含 operation/operationId/traceId，失败帧必须包含 `FAILED` 与安全错误；runtime validator 拒绝畸形消息，业务回调异常不会被二次包装为消息格式错误。RunEvent union 不增加应用源码事件。

`apps/agent-web` 在工作空间切换入口右侧提供应用源码列表：紧凑入口只显示可打开或曾下载的副本，`NOT_DOWNLOADED` 只在四步管理弹窗第 1 步出现；用户必须从当前应用全部关联版本库中显式选择后，才加载其分支、固定提交目录树和运行中 operation。第 1 步允许 owner/应用管理员直接调整当前未过期 generation 自首次受理起的总保留小时数；第 2–4 步完成 exact set、TEAM/PERSONAL、默认 48 小时且按后端返回值限制为最长 168 小时（7 天）的保留期，以及逐服务器安全步骤展示；已有 TEAM generation 不能降级为 PERSONAL。源码打开后使用显式 `APP_SOURCE` 工作区语义和后端返回的逻辑 `workspaceId/generation`，续期成功会同步当前上下文的 `expiresAt`。文件读取、保存、创建、复制、移动、上传、改名、删除及撤销继续走平台 Workspace 文件 WebSocket；前端不保存或推导物理路径。源码工作区保留 Session/Run、OpenCode、终端和普通文件写入，但编辑器/Run Diff 页脚、宠物应用配置重载、Hub mutation、应用 Agent 保存发布与版本选择统一隐藏并在 handler 再次拒绝。recent 源码选择在刷新或窗口聚焦时重新 `open` 校验；只有结构化 `FORBIDDEN/NOT_FOUND/CONFLICT` 或 current source 的空 recent 会清理并回退，网络、超时及 5xx 保留当前源码与 recent，并展示可重试提示。

源码工作台的 managed/source 切换现共用一套完整 selection authority，repository 列表刷新不会抢占当前 open intent；任一 lazy child 发现 commit 漂移都会失效整棵树直到真实 root 重载。进度观察的每条 socket 使用独立 connection epoch；当前连接一旦失败会先作废 epoch，使 250ms 退避期和 replacement snapshot 等待期的所有旧帧失效，无有效 operation frame 的连续断线仍保持 250ms/500ms/1s 指数退避；关闭弹窗可立即中止 CONNECTING socket，不触发重连或后台 cancel。Run Diff 只放行 source 中的普通文件保存，PUBLIC/WORKSPACE Agent 保存在组件和父 handler 双门禁；Hub、配置重载、新增版本和个人 Git pull 弹窗会在能力失效时立即收敛。

### 工具盒子

`apps/agent-web` 在“编辑器”后为所有登录用户提供工具盒子活动栏入口和受保护 `/toolbox` 路由。工具盒子进入时保存并隐藏左右面板和底部抽屉，退出、浏览器前进/后退时精确恢复原布局；页面移除占空间的顶部 Hero，只保留屏幕阅读器标题，并从 `packages/backend-api` 读取 193 项离线目录。搜索、来源和 14 个固定分类标签组成吸顶控制区；分类数字随搜索词和来源实时联动，不受当前分类选择影响，移动端标签保持单行横向滚动。热门区仍展示正点击 Top 10。每张卡片是带 `target="_blank"`、`rel="noopener noreferrer"` 的原生具体工具链接，普通点击和中键会异步上报，失败不阻断打开或显示干扰提示。本地 Vite 默认把两个工具前缀分别代理到 `127.0.0.1:18120/18121` 并剥离公开前缀，套件根路径返回 `/toolbox`，因此使用 `restart-dev-services.sh` 时直接访问 3000 端口即可联调具体工具。

### LobeHub 通用问答

当前 release 企业包设置 `VITE_TEST_AGENT_LOBEHUB_ENABLED=false`；入口、登录回跳和直接路由均关闭。后续只有在独立 LobeHub 制品通过准入并重新构建启用版前端后，以下交接链路才生效。

工作台 activity rail 的“通用问答”仅对超级管理员展示，只承担安全登录交接，不嵌入或复制 LobeHub UI。点击处理器同步创建带
`noopener` 语义的空白标签，再由 `packages/backend-api` 使用现有 Bearer Token 申请一次性票据，并向后端返回的
固定 `consumeUrl` 在空白标签自身创建隐藏表单并用 `_self` POST；ticket 不进入 URL、Pinia、router、Web Storage
或原始交换日志，也不依赖切断 `opener` 后的命名窗口查找。

独立聊天域名回到平台固定 `/lobehub/launch` 时，router 先复用现有登录保护；已有平台登录态会在当前标签自动
换票，未登录则完成统一认证后恢复固定路由。前端不接收 return URL，也不尝试读取跨域 LobeHub Cookie。

本地完整联调从仓库根目录显式执行
`./restart-dev-services.sh --profile test --env-file .env.test --with-lobehub`，聊天服务位于
`http://127.0.0.1:3210`。默认重启命令不启动、不探测也不停止 LobeHub；独立 fork 默认位于同级
`../lobehub-platform`，运行密钥只写入 `.tmp/dev-services/lobehub-dev.env`，本地定时任务由受保护的 loopback
scheduler 触发。显式模式仅允许连接回环平台 PostgreSQL，并通过后端现有审计入口自动替换本地公共参数占位值、
最后启用入口；若可用超级管理员不是唯一候选，启动前设置
`TEST_AGENT_LOBEHUB_DEV_OWNER_UNIFIED_AUTH_ID=<统一认证号>`。初始化或聊天服务启动失败会自动审计并关闭入口。

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
corepack pnpm e2e:session-share
corepack pnpm e2e:real
```

Vitest 全量回归统一限制为最多 4 个 worker，并使用 20 秒单测超时，避免 Mermaid、Monaco 与多组件异步测试在高并发机器上因 CPU/计时器争抢产生随机假失败；直接执行默认 `corepack pnpm test` 即可使用这套稳定配置。

Playwright 常规全量回归覆盖桌面 Chromium（项目没有移动端产品内容），统一单 worker 顺序执行，并将单例上限设为 60 秒、断言等待设为 10 秒。会话协作分享使用独立 `playwright.session-share.config.ts` 在 Chromium、Firefox 和 WebKit 固定验证分享管理、只读/代操作工作台、运行互斥与失效路由。工作台 E2E 会加载 Monaco、Mermaid 与内嵌手册，避免额外视口把非产品范围纳入交付门槛。

完整前端检查也可以从仓库根目录执行：

```bash
tools/dev-frontend-check.sh
```

## 本地联调

定时任务管理页使用同源 XXL iframe。Vite dev server 固定代理 `/xxl-job-admin/`，目标读取 `TEST_AGENT_XXL_JOB_ADMIN_URL`，未配置时使用 `http://127.0.0.1:18080`；平台 API 仍走 `VITE_TEST_AGENT_API_BASE_URL`。页面进入和手工刷新都会重新申请一次性票据并用隐藏表单 POST，票据不会出现在地址栏。真实 XXL shell 加载后，前端幂等启用仅嵌入态生效的横向导航样式；SSO 中转页、错误页和直接访问 Admin 保持原样。

推荐从仓库根目录使用一键脚本重启三服务，脚本默认读取 `.env.test` 并以 `test` profile 启动，按「后端 → opencode-manager → 前端」顺序逐个先 kill 原进程再启动；前端构建和 dev server 启动前会检查 `node_modules` 是否落后于 `pnpm-lock.yaml`、workspace 配置或各包 `package.json`，过期时自动执行 `corepack pnpm install --frozen-lockfile`；当 `TEST_AGENT_OPENCODE_BASE_URL` 是本地地址时默认启动 Go `opencode-manager`（由它派生 opencode 子进程，不再单独启动 `opencode serve`）：

工作台左侧 Agent 配置树展示公共级 `opencode/` 和工作空间级 `.opencode/` 配置根。公共配置自动挂载必须先取得当前用户 OpenCode 进程的成功归属结果；查询未完成、失败或成功但未分配时不回退到记忆值或首台服务器，也不浏览该服务器的共享直接目录。`SUPER_ADMIN` 初始化进程成功后，前端按后端返回的同服 `worktreeId/linuxServerId` 重新挂载当前用户稳定的 `public-{userId}` 分支/worktree，即使服务器 ID 没变化也不沿用旧 worktree；worktree 准备失败时同样不降级展示共享直接目录。公共分支不包含应用版本，不能切换他人 worktree 或直接编辑共享运行副本。普通用户在进程已明确归属时从同服共享运行副本只读查看。Git 变更面板按“应用工作空间 / 应用 Agent / 公共 Agent”切换作用域：应用工作空间承载根 README、`docs/**`、`archive/**`、源码、测试、部署文件和仅本地 `spec/**` 等普通项目文件；应用 Agent 展示个人 worktree 中全部 Git 可见的 `.opencode/**` 用户配置，包括 JSON/JSONC、agent、skill、command、plugin、tool 及辅助源码，不在前端重复维护子目录白名单；公共 Agent 展示公共配置仓库的 Git 可见内容。应用 workspace 与应用 Agent 只是同一个人 worktree 的两个 Diff 视图，公共 Agent 属于另一套 Git。运行依赖生成的 `node_modules`、package/lockfile 等由部署维护的 `.gitignore` 排除；一旦已被 Git 跟踪或实际出现在 Git status 中，仍必须在 Diff 展示并可提交或回退，不能形成不可见脏状态。配置文件保存成功立即刷新 Git Changes，并按既有运行态命中规则热加载当前用户；公共变更额外先把本人固定配置软链接切到公共个人 worktree。

公共个人配置在 OpenCode 受管启动/重启时自动恢复：后端只做一次当前用户、当前服务器、ACTIVE 稳定 worktree 查询和物理目录校验，不轮询、不执行 Git 命令；有效时进程直接加载本人目录，无效时回退共享副本。该过程不会 stash、reset、clean 或删除公共个人 worktree 中的 staged、unstaged、untracked 内容。

应用工作区暂存通过平台 API 操作真实 Git index，同时仍作为发布文件白名单；未暂存区可一次暂存全部普通文件，或经二次确认后丢弃全部已暂存和未暂存普通文件；已暂存区的一键回退只调用 unstage all，把全部已暂存普通文件移回未暂存区，不丢弃文件内容。后端普通发布会隔离历史 index。冲突期间批量暂存和丢弃全部改动入口禁用，已暂存区一键回退及普通文件逐个 stage/unstage 仍可使用，但按 Git 原生规则禁止提交；冲突文件可在 Monaco 三方合并编辑器中可靠选择结果、保存或取消整次 merge。Git 三个 Tab 直接在 `UNSTAGED/STAGED` 下展示文件，不再重复作用域标题和 worktree 说明；只暂存 `spec/**` 时只展示本地“提交”，混合暂存时会在操作前明确提示提交、推送和仅本地文件数。前端只有在后端明确返回 `remotePushed=true` 时才展示提交并推送成功；连续处理 workspace、应用 Agent、公共 Agent 时，进度页按本轮累计实际提交、远端推送及仅本地 spec 数量，并列出各作用域明细，三类差异全部清空后结束本轮。

当前权限口径补充：公共 Git 只有 `SUPER_ADMIN` 可写；应用级整个 `.opencode/**` 命名空间由 `APP_ADMIN` 管理，普通成员只读；所有托管应用操作仍要求有效应用成员，`SUPER_ADMIN` 不旁路成员校验。成员应用目录刷新发现当前应用已撤权时，工作台隐藏保留在服务器上的个人工作区并清空文件树、编辑器、Diff 和当前运行上下文，历史 Session 记录仅保留追溯与只读展示。应用 feature 副本对所有角色普通文件只读，个人 worktree 普通文件可写；“本地提交”只提交个人分支，“提交并推送”再把非 `spec/**` 选中路径投影并 push feature。push 后各服务器把同一固定 commit 自动 merge 到相关个人 worktree，其他用户不必主动拉取：干净或只有非重叠 dirty/staged/untracked 改动时都会保留本地内容并完成更新；只有 Git 判定会覆盖本地文件时才提示待同步，真实冲突进入三方编辑器，全部解决后需点击“完成合并”。左侧应用 Agent 的更新按钮也会显式重试整个固定提交合并，成功后刷新文件树/Diff，并在当前用户进程就绪时 dispose；它不是仅刷新运行态缓存。普通 workspace 文件的自动更新和个人拉取都不 dispose；应用配置发布 rollout 在相关个人 worktree 收敛后只 dispose 目标用户，个人拉取包含应用配置时则由后端 `PERSONAL_APPLICATION` 任务只登记点击者并持久化等待空闲。公共或应用个人运行态配置保存都只热加载当前用户：公共保存先把本人有效公共配置软链接切到公共个人 worktree，应用保存继续由请求工作区 `.opencode` 原生加载。`spec/**` 对任何角色都只做本地提交。

Agents 配置树的公共级、应用级根统一复用工作空间 `FileEntryCreateDialog` 的新建/上传样式：根入口合并普通文件、文件夹、上传、Agent 模板和 Skill 模板，目录入口提供普通文件、文件夹和上传；面板会解释普通条目不会生成 OpenCode 模板，而 Agent/Skill 会落到标准路径。模板要求中文名称，英文名称选填；英文留空时按完整中文无声调拼音生成英文技术 ID，磁盘上的 Agent 文件名、Skill 目录名及 OpenCode 顶层 `name` 始终保持英文。文件与目录行复用 `FileEntryDeleteDialog` 确认递归删除，新建文件夹写入 `.gitkeep` 让 Git Changes 感知空目录。应用级创建、上传、改名、复制、移动和删除允许整个安全的 `.opencode/**` 相对路径，根目录和越界路径仍由文件服务拒绝；成功后沿用 Agent 配置修订号刷新现有 Diff 查询，并在删除时关闭受影响的已打开标签。具备对应写权限的用户通过右键菜单对公共级或应用级文件行内改名，双击不再触发改名；Agent 文件支持 Ctrl/Cmd+单击多选、右键批量删除/复制/剪切/粘贴和整体拖动。操作继续复用目标后端文件 WebSocket 服务；公共级要求 `SUPER_ADMIN`，应用级要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），无权限用户保持只读。

文件页进入时默认展开工作空间并把收起的 `Agents` 固定在面板底部，减少文件树被上下分屏压缩；用户展开 `Agents` 后仍可拖拽分隔线调整两区高度。

```bash
./restart-dev-services.sh
```

工具镜像已在本机以 `18120/18121` 启动时，上述命令会让 `http://127.0.0.1:3000/toolbox/apps/...` 直接经过 Vite 代理访问具体工具。若容器位于其他地址，可在启动命令前临时设置 `TEST_AGENT_TOOLBOX_IT_TOOLS_URL` 和 `TEST_AGENT_TOOLBOX_OMNI_TOOLS_URL`；无需也不应为此修改 `.env.local`。

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

首次引导和内置手册共同说明四个普通用户入口：顶部应用/工作空间/版本选择（左下角入口继续保留）、工作区小地球引入需求子条目，以及首条消息自动建立对话；设置章节另外说明普通用户个人 SSH Key，以及应用管理员可见的成员、版本库关联和工作空间操作；只选应用不会加载文件树。

右侧主对话在 TestAgent 进程 ready 且工作区可用时允许直接输入首条消息，发送时才创建 Session，不要求先点击“新建对话”；宠物旁路问答仍要求已有真实主 Session。顶部书本图标打开同源嵌入的用户手册，初始化状态卡可直接定位到对应章节；帮助中心问宠物时复用既有旁路 Run，并把当前 Markdown 章节作为限定资料，不新增后端 API。工作台提供寻迹鹿、巡查红熊猫、深夜龙、星探狐、稳态熊猫、工具浣熊和夜巡猫七种图片宠物，用户可在统一浮层选择按本地日期轮换、每日随机或固定角色，并通过宠物大小滑杆调整 75%–250% 的显示比例（默认 150%），偏好只保存在浏览器本地；旧版变色龙、鸭嘴兽、猫头鹰、吞噬怪、鲨鱼以及原五种伙伴 ID 会映射到对应新角色。已分配的 opencode 进程终止时，活动栏宠物入口直接复用既有初始化动作启动进程，并在 READY 后只唤出浮动宠物，无需再次点击宠物或状态卡。Agent 配置面板的公共级/应用级根节点分别提供个人运行态重载按钮；公共按钮携带当前用户 worktree/server，应用按钮先合并 feature 固定提交，再只刷新当前用户运行态，运行中任务时禁用。点击宠物后以对话为主体；配置更新按钮只在对话页出现，选择页只负责伙伴和大小设置。标题栏的小手柄按钮可进入纯前端俄罗斯方块、扫雷、数独和贪吃蛇；四个入口使用紧凑 2×2 小卡片排布，不新增独立活动栏按钮、后端 API 或服务端持久化状态。

```bash
tools/dev-runnable-loop-check.sh
```

真实三服务联调 E2E 单独执行，不进入默认 `pnpm e2e`：

```bash
tools/dev-phase11-real-e2e.sh --start-services
```

该脚本默认复用已运行的 opencode server 和 `test-agent-app`；传入 `--start-services` 时会启动本地 Postgres、opencode server 和后端，前端由 `frontend/playwright.real.config.ts` 的 Playwright `webServer` 管理。服务日志保留在 `.tmp/phase11-real-e2e/`，脚本不会打印 `.env.local` 或 `.env.test` 中的敏感值。本机 Docker daemon 未运行时，脚本会在启动 Postgres 前直接失败并提示先启动 Docker Desktop，或手动启动后端后不带 `--start-services` 重试。

`frontend-opencode` 独立联调见 `frontend-opencode/README.md`，默认通过 Vite proxy 把 `/api` 转发到 `test-agent-app`。

应用 Agent 与公共 Agent 的 Git 未暂存分组提供“全部暂存”，分别复用既有批量 stage API，一次处理当前作用域的全部未暂存文件；无写权限、存在未解决冲突或 Git index 更新进行中时入口禁用。

## 访问边界

- 前端不得直连 opencode server。
- HTTP 请求只能通过 `packages/backend-api`；Run/Diff/runtime 默认使用 `agentId=opencode` 的 `/api/internal/agent/{agentId}/...` 后端 URL。
- 独立工作流是唯一受控例外：`/workflow-chat` 只能通过 `packages/workflow-api-client` 同源访问 Python `/workflow-api/v1/**`，不经过 Java；其原生 AG-UI 也不进入平台 RunEvent。
- Model/Provider 目录由 `packages/backend-api` 读取用户 opencode 实例级 `/config` 合并有效配置；`enabled_providers` 非空时只按 Provider ID 同时过滤两类目录，企业包只展示企业白名单 Provider 下的全部模型，不再混入 OpenCode Zen，且不改变两个目录的原生顺序。OpenCode 1.18.4 的本地 JSONC 模型 `release_date` 不会迁移到平台使用的 V2 `/api/model` 发布时间字段，前端不得据此推断可配置排序。未声明白名单或配置暂时不可读时保持原生目录兼容。只有当前用户进程健康状态为 READY 且初始化 operation 不在运行时才发起目录查询；尚未初始化或进程不可用时不轮询 `/models`、`/providers`。READY 后若服务重启导致目录为空或请求失败，每 3 秒自动恢复查询，目录非空后停止短轮询，窗口重新聚焦也会刷新。
- 设置页异步创建或重新启用应用工作空间后，以 operation 成功终态为准通知工作台失效模板目录；左下角工作空间选择器会自动拉取新目录，不依赖用户关闭设置时的过早刷新或整页刷新。
- 工作台首次 `/processes/me` 不携带服务器路由头；响应包含 binding `linuxServerId` 后只在当前页面内存保存，后续用户 OpenCode、Session、Run、SSE 和本地工作区请求通过 backend-api/event-stream-client 携带 `X-Test-Agent-Linux-Server-Id`。退出、切换用户和刷新必须清空，禁止持久化；登录、用户管理、应用列表及其它共享控制面继续普通负载均衡。该头只优化 Nginx 首跳，不能替代后端归属校验。
- 定时异步执行仍在当前对话交互：发送按钮左侧定时图标默认选择后端返回的北京时间 15 分钟夜间时段；仅 `SUPER_ADMIN` 可切换到“测试时间”，使用未来 24 小时内的北京时间完整分钟或 1/3/5 分钟快捷值，允许白天执行且不占夜间容量。右侧栏“会话列表”浮层内的“待执行任务”页签分页收齐当前用户全部 `SCHEDULED/DISPATCHING` 任务并展示创建时间，自定义任务显示“测试定时”和单个精确时间；旧响应缺少模式时按夜间范围兼容。主对话不再切换视图，当前 Session 有待执行任务时禁用普通发送但不禁用新建对话。工作台每 30 秒和窗口 focus 刷新任务，容量冲突时立即重取时段；任务投递后不引入独立执行视图，继续复用既有 Session、RunEvent SSE，并在来源标签显示北京时间实际启动时间。
- 新建 Session 或切换到可交互历史 Session 后，通过 `backend-api.getRunContext(sessionId)` 获取一次会话运行上下文，并只缓存在当前页面内存；同一 Session 的后续 Run 复用该 token。每次发送生成稳定 `clientRequestId`，若后端返回 `CONVERSATION_CONTEXT_REQUIRED` 或 `CONVERSATION_CONTEXT_EXPIRED`，清除该 Session 缓存、重新签发并只重试一次，重试复用原 `clientRequestId`。退出登录、认证用户变化或页面刷新后清空全部上下文，禁止写入 localStorage、sessionStorage、IndexedDB 或持久化 store。
- 历史切换与 Run 启动都使用页面交互代次 fencing：每个异步返回点重新校验认证 token、Session、Workspace 和发起代次，迟到的 Session/workspace/context/Run/active-run 结果不得覆盖当前会话或建立 SSE。历史 Session 的工作区、上下文和正文尚未恢复完成时，发送按钮与父层发送入口都会硬阻断，避免仍以旧 Session 启动 Run。`startRun` 出现超时或 5xx 时，如果同一发起上下文已由 runtime-state 接管 busy Run，则不生成本地 `run.request.failed`；响应带 `clientRequestId` 时优先精确匹配，旧响应缺失时才使用同 Session 与同交互代次的兼容判断。
- 普通工作区文件树、搜索、对话文件卡片与顶部 tab 共用 workspace 文件加载器；公共级/应用级 Agent 配置树与顶部 tab 共用独立 Agent 文件加载器。一次性读取分别通过平台文件 WebSocket `workspace.read` / `agent-config.read` 完成，不要求出现 Fetch/XHR；明确收到 `details.reason=PREVIEW_TOO_LARGE` 后才切换为 `workspace.read.chunk` / `workspace.view.read.chunk` / `agent-config.read.chunk` 渐进只读预览，其余错误仍走 error/retry。两类 tab 都显式区分 loading/loaded/error/progressive-preview、稳定磁盘快照与用户内容修订代次；每个后续分段携带首次响应的 size/lastModifiedMillis，文件变化时停止拼接。workspace 或 Agent scope/workspace/worktree/server 上下文、同路径请求代次、tab 存在性和修订代次共同丢弃迟到响应。合法空文件为 loaded，首次 loading 不挂载空 Monaco；渐进预览取得首段后才挂载只读 Monaco，刷新失败或读取期间发生过编辑（含随后已保存/回退 clean）时保留已有内容；Agent 顶部 loaded tab 使用缓存，error/旧版未标记 tab 重新读取，重试不得误发 `workspace.read`。
- 企业内浏览器基线按 Chromium 108 兼容，`agent-web` 生产构建显式使用 `build.target=chrome108` 和 `build.cssTarget=chrome108`；涉及 Web Crypto、Clipboard 等安全上下文 API 时必须先做能力检测，内网 HTTP 访问不能直接假设这些 API 可用。个人 SSH key 加密优先使用 Web Crypto，HTTP 内网下 `crypto.subtle` 不可用时使用 node-forge 纯 JS AES-GCM + RSA-OAEP/SHA-256 回退，仍禁止明文提交。
- 小宠物拖动在 Chromium 108 下使用 `window` 捕获阶段接收 `pointermove/up/cancel`，避免编辑器或工作台子组件停止事件冒泡后中断拖动；仍不依赖 pointer capture。
- RunEvent SSE 和用户级/分享运行态 fetch SSE 只能通过 `packages/event-stream-client`。单 Run SSE 的应用层身份固定为标量 `(runId, sessionId, token)`，同一 Run 的对象投影不得重建连接；终态先建立 500ms hold 再更新状态，标题待定继续复用原连接，legacy 终态反馈恢复按 runId 合并为一条、最多 3 轮的兼容链。连接内部的 durable 游标、事件去重和 transport reconnect 仍由公共 client 维护。用户级 runtime-state SSE 是运行恢复主入口，使用 `/api/internal/platform/opencode-runtime/sessions/runtime-state/events` 携带 Bearer Token，按 1/2/5/10/30 秒退避重连；连接期间不并行查询 runtime-state HTTP，也不做 1.5 秒 active-run 热轮询。摘要里的非终态 `runId/runStatus` 直接接管 RunEvent SSE；只有流不可用时，当前 Session 才执行一次 `backend-api.getActiveRun(sessionId)` fallback，短连接反复收到首帧后立即断开仍视为同一故障，连接稳定保持 5 秒后才允许后续新故障再次 fallback。页面休眠或断网期间若错过当前 Run 的终态事件，而更新后的 runtime-state 摘要已不再包含该 busy Run，Workbench 会按精确 `runId` 读取 Run 详情并通过统一终态投影解除发送锁；摘要生成时间早于 Run 更新时间时不执行该校准，避免启动竞态。分享运行态同样在 active Run 消失时按精确详情收敛终态，并以 additive `sessionUpdatedAt` 监听 compact 等消息修订、主动刷新当前消息投影。精确 Run 已是终态时，该权威事实还会覆盖 session-tree/messages 残留的同轮 busy 投影，避免展示无法点击的停止按钮；只有显式的新 Run 请求或重发仍在等待响应时继续保持发送锁。收到 `run.snapshot.reset` 时，event client 只投递事件且不推进 durable 游标；agent-chat reducer 保留平台持久消息、清空当前 Run 实时投影并按 snapshot 顺序重放，Workbench 同步清空独立 Diff/实时跟随状态。运行中点击新建对话只清空当前视图和关闭当前 RunEvent SSE，不调用 cancel/abort；前端只把 RunEvent 应用到当前订阅且仍为页面活动态的 Run。`session.status.retry` 会在右侧时间线展示供应商重试原因和倒计时，但归零不取消 Run、不调用 `startRun`、也不伪造终态；撤销重发只由后端 resends 状态机驱动，入口仅对会话所属人展示，点击后先编辑上一条文本再发送可选 `editedPrompt`。同一 `runId` 已收到 `run.succeeded/run.failed/run.cancelled` 后，乱序到达的 `session.status.busy/retry` 不得覆盖终态，不同新 Run 的 busy 仍正常生效；新 Run 请求和后到成功/取消终态会清理上一轮 `run.failed` 失败卡与 SSE 连接错误提示，避免旧 `Streaming response failed` 覆盖后续轮次。
- workflow AG-UI 只能通过 `workflow-api-client`；连接先应用 `STATE_SNAPSHOT/MESSAGES_SNAPSHOT`，再按 durable id 重放，重复事件与 toolCall 幂等，未知事件安全忽略。离开路由或认证变化必须取消 fetch、释放重连定时器并清空仅内存连接状态。
- 聊天面板的失败、停止和完成标记按当前 Session 隔离；从已建立会话切换到另一会话或空白新对话时清理组件本地终态，返回历史会话时只根据该会话恢复出的消息和 Run 状态重建。空草稿首次落成真实 Session 仍属于同一轮 Run，不触发该清理。
- 右侧 Agent 面板的“原始输出”只展示当前页面生命周期内，前端捕获的浏览器与平台后端 HTTP 请求/响应正文和 RunEvent SSE `MessageEvent.data`。HTTP 与 SSE 共用 `prepareRawOutputBody` 安全边界，在写入按 Session 划分的页面缓存前递归脱敏所有层级、大小写不敏感的 `contextToken`、XXL SSO ticket、token、cookie、password、secret 与 session digest，再执行长度截断；每个 Session 仅保留最新 2000 条，页面最多保留最近访问的 20 个 Session，超限按最久未访问顺序淘汰，认证切换或会话删除时同步清理。浮层展示、筛选和下载统一按 `occurredAt` 时间倒序派生，打开浮层或收到新记录时默认回到顶部显示最新内容，但不改变底层采集顺序。它不记录 opencode server 原始事件、不落库，刷新或换浏览器后不保留。
- 右侧会话 footer 最左侧在真实根 Session 建立后显示 16px 上下文使用率圆环，悬浮只展示使用率、当前模型总上下文和已使用量；点击后参照会话列表效果，在对话栏外侧左边打开详情抽屉且不遮挡对话，再次点击圆环可关闭。圆环按使用率 0–59% 显示 normal 紫色、60–79% 显示 warning 橙色、80% 及以上显示 danger 红色；未知模型上限只显示空轨道，超过 100% 的文本使用率保持原值但 SVG 满环并为 danger。平台 Session ID 仅用于抽屉生命周期与缓存隔离，不能同 OpenCode message scope ID 比较；card、显式 child scope，以及未显式标记但 `sessionId/rootSessionId` 不同的 scope 不进入根统计，未带 scope 的平台历史仍兼容保留。抽屉展示会话、供应商、模型、可见对话消息数、限制及总/输入/输出 Token：每条 root user 消息计一次，连续 root assistant 原始消息只在出现非空 text/text part 后合并计为一次，reasoning/tool/file/retry/step-only 不计数。用量取最近一条有效根 assistant 快照的 input/output/reasoning/cache read/cache write 之和，后续全零/缺失 snapshot 不覆盖它；限制始终跟随当前所选模型，五类堆叠条以同一 root 过滤并校准到该快照 input。`payload.info` 的消息、模型、tokens 与远端 scope 由 reducer 保留，子 Agent 视图隐藏该入口。该能力只复用模型目录、历史消息和 RunEvent 数据，不新增 API、事件或持久化字段。
- `apps/agent-web` 负责组合页面；业务能力必须沉淀到对应 package。
- 应用管理的已有工作空间支持启用/停用；工作空间切换菜单只显示启用项，旧响应缺少 `enabled` 时按启用兼容，停用当前打开项不会强制退出或清理现有状态。
- opencode Web App 复刻以运行态能力为范围，交互行为参考 `opencode-source/opencode-1.18.4/packages/app`；OpenCode 源码快照严格只读，禁止通过修改快照实现前端适配；顶层 `frontend-opencode` 承载 Vue/Vite 复刻工程，opencode `packages/web` 官网/文档/公网分享轮询不进入默认边界。
- 当前已接入 backend-api runtime 方法、夜间任务时段/创建/查询/改期/取消/关闭、Agent/Provider/Model 运行态选择（底部 Agent 下拉按 opencode `local.agent.list()` 过滤为 primary+all 且排除 subagent/hidden，输入框 `@agent` 候选按 prompt autocomplete 过滤为 subagent+all 且排除 primary/hidden，当前用户 opencode 健康状态 ready 后自动刷新运行态目录）、右上角成员应用切换、应用版本工作区/个人工作区切换与同步、超级管理员服务器工作空间选择、用户级 session history 远端搜索/分页（默认 30 条、显示应用/工作区/版本、按更新时间倒序、不拼接本地伪历史，历史按钮的运行中与 question/permission 待处理数字使用用户级摘要，不受已加载分页范围影响）、历史会话完整消息渲染与所属应用/工作区不可切换时的只读态、按成功主 Run 提供的整轮满意/不满意反馈（每轮使用 `runId` 独立定位，成功历史 Run 永久保留入口，无 assistant part 也可评价，失败/取消/子 Agent 不展示，历史状态和反馈每批最多恢复 100 个 Run）、message part reducer、active run 恢复入口、permission/question dock（permission 对齐 OpenCode 1.17.8 中文说明，优先展示完整 `patterns[]`，不展示内部 type/requestId，子智能体待授权时在对应 task 状态前显示铃铛）、Todo、工作区上下文附件（Monaco 选区、文件树文件、编辑器 Tab 文件添加到对话，输入框上方预览/删除/清空，发送时前端结构化拼接 prompt，并按字符数拦截超长内容）、上传附件前端弹窗样式（后台上传暂未接入）、busy follow-up 队列、输入法组合输入阶段 Enter 防误发、后台每 10 秒调用弱健康接口检测当前用户 opencode 进程健康，弱健康不健康时复查 `/processes/me`，MCP/LSP 状态 5 分钟刷新一次且 VCS 状态保持 30 秒刷新、Monaco 选区上下文、统一进入可恢复 Run 的 slash command palette 与参数表单补全、`@` context picker、Run/Session/VCS Diff 来源切换、Diff hunk 导航与懒加载 editor、运行中实时追踪写文件工具变更、MCP/LSP/VCS 状态摘要、顶部应用切换左侧 Agent/Skill/MCP/Tool/Plugin 已加载数量摘要和可拉伸/全屏详情弹层、左下角 Agent/Skill/MCP/Tool Hub、左下角设置模态（应用与工作空间管理含版本库内外部部署模式、版本库英文名、创建工作空间进度轮询、个人 SSH key、用户管理查询/创建测试用户和超管直接调整角色；无应用配置权限时显示角色提示）、仅 `SUPER_ADMIN` 可见的系统管理入口（XXL 同源 iframe 定时任务管理 + 运行管理 + 配置管理中的公共仓库维护和应用 Git 全量安全刷新 + 通用参数 JVM 内存值按需查询/刷新 + 运营分析，运行管理展示最新 CPU/内存并在点击容器或后端 Java 进程后用 ECharts 展示 Redis 48 小时指标趋势；运营分析展示用户漏斗、使用强度、Run 结果、满意度、Diff 采纳、token 强度、趋势、热力、排行、明细和 CSV 导出且不展示费用字段）、`/s/[shareId]` 协作分享工作台和受控 PTY terminal panel；per-file/per-message 回滚和真实三服务联调 E2E 仍按后续批次推进。
- 输入卡悬浮或输入框聚焦时显示批量生成入口；70vw × 90vh 弹层直接复用输入 `#` 的四阶段子条目候选，最多选择 50 项。打开定时选择后隐藏立即执行入口，选中有效时间后才在原主操作位显示“定时执行”；夜间模式下前端按所选子条目数量与各夜间时段排队任务数自动推荐并选中可覆盖总量的时间段，并在“定时执行”按钮上方提示“已根据选择的子条目数量智能推荐定时执行时间段”，用户手动改选后保留其选择不再覆盖，关闭或重新打开后按最新子条目数重新推荐；定时面板的关闭图标可返回立即模式，并丢弃尚未提交的时间选择。提交后同步切换为不可编辑的会话创建进度页，避免同一批次重复发起；每项用局部文件上下文独立创建 Session/Run 或定时任务，最多四路并发，当前 Session、输入正文和上下文附件均不改变。进度页不会自动关闭，失败项可单笔或批量复用原批次身份重试；存在未创建 Session 的条目时关闭需确认，确认关闭后重开会恢复空选择和默认要求。定时批量支持多个夜间时段或超级管理员未来 24 小时内的多个分钟时间点。
- 右侧对话面板在当前页面生命周期内分别记忆主 Agent 与各子 Agent 的阅读位置。首次进入某个视图时滚到最新底部；离开时仍在底部的视图返回后继续跟随最新正文，已上滑的视图返回后恢复原位置，并在该视图正文有新增时显示“查看新内容”。其它子 Agent 的并行输出不会移动当前视口或触发当前视图提示；这些快照不写入持久化存储。
- 运行管理的后端 Java 进程表格和趋势图会展示服务器 CPU/load/内存/swap/磁盘、Java 进程 CPU/RSS/FD、JVM heap/non-heap/direct/mapped、GC、线程等可空字段；旧后端缺失新增字段时继续显示 `-` 或使用旧字段回退，趋势图保留断点。
- 运行管理无主进程明细展示可空 UCID 和 manager PID 状态；无平台记录时固定显示“平台未登记”和“未执行 HTTP 健康检查”，`baseUrl` 保持独立列，拓扑缺新字段时回退 `-`。这些字段只来自 `SUPER_ADMIN` overview，前端不解析启动命令中的 UCID，也不自动认领、停止或改绑无主进程。

- 用户级 Session History 复用既有 Session PATCH 能力支持置顶/取消置顶；列表按置顶组优先、组内更新时间倒序展示，置顶发生在已加载后续页时回到第一页重新对齐服务端分页。

## UI 与主题边界

- 全局 theme token、Figma Web IDE 风格 activity rail、Dockview/Monaco 视觉适配、滚动条、panel chrome 和轻量动画由 `apps/agent-web/src/styles/globals.css` 承载；包内组件只消费这些 token，不在业务组件里复制整套主题。
- `packages/ui-kit` 只提供 Button、Badge、Input、Tabs 等无业务状态基础控件；运行态选择、permission/question、terminal 和 Diff 语义仍放在对应 feature package。
- 面板、toolbar、terminal、Diff、Agent timeline 和文件树必须保持稳定尺寸。Agent timeline 主路径使用 `packages/agent-chat/src/opencode-like` 的 `.oc-*` 时间线，把 reasoning 思考过程、上下文工具组、Skill/Tool 调用、文件引用、Diff 摘要与最终回答分块展示并保留独立滚动区域；`session.status.retry` 这类上游等待重试/限额状态必须转成 runtime retry 行展示，可按前端首次收到事件的时间显示固定 60 秒倒计时，但倒计时不产生任何 Run 副作用，不能停留在普通“思考中”；右侧 Agent 对话框继续消费全局 `--ta-*`/`--ta-chat-*` token，避免 hover、streaming 文本、warning、hunk 导航或状态徽标导致布局跳动。
- 旧 `.figma-chat-*` 气泡消息循环、`AgentCard`/`TimelineCard` 和 `MessageParts` 旧 part 组件只作为作废兼容代码保留；新增对话展示能力必须落在 `packages/agent-chat/src/opencode-like`。
- 真实三服务 E2E 尚无最新通过记录；当前只能认为 mock E2E 和单元测试覆盖了主流程，不能把真实联调标记为完成。
