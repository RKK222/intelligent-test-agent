# 前端规范

本规范适用于完全自研的 `frontend/` 工程，以及独立的 `frontend-opencode` Vue/Vite opencode 复刻工程。技术栈版本以各自 README 为单一来源；包职责与访问边界见 `docs/architecture/module-map.md` 和 `docs/architecture/dependency-rules.md`；前后端契约见 `docs/api/`。

## 基本原则

1. 先读 `AGENTS.md`、`docs/standards/frontend.md`、`docs/api/` 和目标 package README。
2. 只改与任务相关的最小范围，不顺手重构无关组件、样式或状态。
3. Web IDE 能力按 package 边界沉淀，避免把业务逻辑堆到页面入口。
4. 人工维护的复杂逻辑必须有中文注释，说明业务意图、边界和异常分支。

## 浏览器兼容性

1. 企业内浏览器最低基线固定为 Chromium 108。`agent-web` 生产构建必须保持 `build.target=chrome108` 与 `build.cssTarget=chrome108`，新增语法、DOM/CSS 能力或第三方资源升级都要按该基线验证，不能只以开发机最新 Chrome 为准。
2. 访问 `window.parent`、`window.opener`、`window.top` 或第三方页面暴露的 `$`、插件等跨窗口全局对象前，必须逐级做存在性检查；跨源或宿主页能力不确定时还必须捕获安全异常。禁止直接写出类似 `window.parent.$.adminTab` 的连续解引用。
3. iframe SSO 的成功、失败和过期页必须使用平台自有状态页向同源父页面发送明确 `postMessage`；不能让平台 SSO 异常落入依赖父页 jQuery/插件的第三方通用错误页，否则二次脚本异常会掩盖服务端首因。
4. 涉及旧内核、HTTP 安全上下文或 iframe/父子窗口交互的改动，至少补充对应能力缺失或父窗口不含预期全局对象的回归测试，并运行 Chromium 108 构建检查。

## API 访问

1. 只能通过 `packages/backend-api` 访问平台后端服务（当前由 `test-agent-app` 装配运行），不得直连 opencode server，不得在组件中直接拼接后端 URL。
2. Run、Diff 和 runtime 相关请求默认使用 `agentId=opencode` 的 `/api/internal/agent/{agentId}/...` URL；切换 agent 只能通过 `backend-api` 配置，不得在页面组件中手拼旧 runtime URL。
3. 工作区文件和 Agent 配置文件的目录列表、读取、写入、上传、复制和移动只能通过 `backend-api` 的文件 WebSocket route/ticket/RPC helper；页面组件不得回退到 HTTP 文件接口或自行拼接 WebSocket URL。超级管理员排查读取必须使用独立 support route/ticket/RPC 和内存 grant，不能复用普通连接或角色绕过；其文件树必须同时设置 `canWrite=false`、`canAttach=false`、`canDownload=false`。工作区拖动只允许可写纯 `WORKSPACE` 文件/目录作为源；只读、纯 `REFERENCE` 和 `MIXED` 条目不可拖。合法目录或根空白区为蓝色落点；当前父目录、自身、被拖目录的后代、文件行、纯引用目录和只读目录必须拒绝，带 `workspacePath` 的 `MIXED` 目录可作为工作区侧落点接收工作区条目。移动成功后 app 层必须先迁移整棵已打开子文件、活动/Diff/展开/请求路径，再按迁移后的 `workspacePath` 补齐祖先并逐层认领组合视图新稳定 ID；无快照的加载中 tab 必须在刷新新代次建立后再补读，最后刷新 Git Diff。反向移动撤销复用同一顺序。公共 Agent worktree/直接目录切换只能更新 `worktreeId/linuxServerId` 上下文，后续文件操作仍由 `backend-api` 申请 route 和 ticket。
4. API 请求、响应、错误类型必须与 `docs/api/http-api.md` 一致；新增或变更 API 必须同步 `docs/api/http-api.md` 和 `docs/architecture/module-map.md`。
5. 前端调试用原始报文查看器只能通过 `backend-api` 的可选 observer 捕获浏览器可访问的请求体和响应文本，不得记录 `Authorization`、Cookie 等敏感请求头，不得新增后端持久化或绕过平台后端直连 opencode；展示、筛选和下载按发生时间倒序派生时不得改变有界缓存的原始采集顺序。

## RunEvent SSE

1. 只能通过 `packages/event-stream-client` 订阅平台 `RunEvent SSE`，必须处理连接、断线、重连、`Last-Event-ID`、重复事件和取消订阅。
2. RunEvent SSE 默认使用 `agentId=opencode` 的 `/api/internal/agent/{agentId}/runs/{runId}/events` URL；旧 `/api/runs/{runId}/events` 只作为后端兼容入口。
3. 高频事件不得逐条触发重型渲染，必须合并、节流或按面板局部更新。
4. 事件类型和字段变更必须同步 `docs/api/event-stream.md`。SSE 契约以该文件为单一事实源。
5. 原始 SSE 调试回调只能保存浏览器 `EventSource` 暴露的 `MessageEvent.data`、事件名和 `lastEventId` 等前端可见字段；它不是完整 HTTP wire bytes，也不得替代 RunEvent 契约文档。
6. `run.snapshot.reset` 是 transient 恢复事件，不设置 SSE `id`。event client 不得从 payload `seq/eventId` 或 `snapshot.runtimeVersion` 推导 durable 游标；reducer 必须先清空当前 Run 运行投影，再按 `snapshot.events` 顺序重放，空/缺失/未知字段安全兼容。页面若维护 reducer 外的 Diff、通知或工具跟随状态，必须在 reset 时同步清理并重放，且不得重复触发用户通知。

## 组件与状态

1. `frontend/` 主 workspace 的 API 远端状态优先放在 `@tanstack/vue-query`，工作台级 UI 状态放在 Pinia store；`frontend-opencode` 同样使用 Pinia 承载 opencode parity 状态；单组件内部临时状态用 Vue 组合式 `ref`/`reactive` 保持。
2. 不把密钥、token 或敏感内容放入可持久化前端状态。
3. Dockview 面板恢复必须使用稳定 id，避免刷新后丢失上下文。
4. 当前事件流应优先按 `eventId` 去重，兼容旧事件时才回退 `runId + seq`；`seq=0` transient 文本事件不能因为相同 seq 被错误丢弃。
5. 普通工作区文件、工作区引用组合视图与公共级/应用级 Agent 配置文件的异步读取都必须显式建模 loading/loaded/error。普通文件以 workspace 上下文隔离；引用节点必须以服务端稳定 `id/locator` 隔离，展示 path 不能作为唯一缓存或 tab 身份，引用 tab 固定只读并保留内部打开定位；Agent 文件以 scope/workspace/worktree/server 上下文和合成 tab 路径隔离，应用级 tab 必须持久携带 feature workspace ID，重试与写入均从 tab 路由还原目标，不得回退当前个人 workspace；三者都必须校验同路径或同节点请求代次、tab 存在性和用户内容修订代次。后台响应只能更新所属 tab 且不得抢焦点，合法空文件不能与“尚未加载”混淆，读取期间发生过编辑的内容即使随后已保存/回退为 clean 也不得被旧磁盘响应覆盖。顶部 loaded tab 应使用缓存，loading 不重复发请求，error/旧版未标记 Agent tab 重新读取；重试必须按 tab 类型分发，Agent 合成路径不得传给 `workspace.read`，也不得作为页脚复制结果；复制路径必须使用服务端状态返回的真实配置根目录与 Agent 相对路径，引用 locator 不得退化为可写 workspace path。配置保存后的组合树刷新必须用 generation fence 丢弃迟到目录响应。Agent 上下文失效以及普通文件移动/改名导致旧路径失效时，必须把旧 loading 收敛为 loaded/error 或从新路径补读，禁止永久停留在 loading。切换 workspace 的批量循环必须固定起始上下文并在每个 `await` 边界中止旧任务。
6. 公共级或应用级 Agent 配置文件保存、新建、改名或删除成功后必须主动刷新 Git Changes 统计，不得依赖用户手工点击刷新；刷新触发只传递修订信号并复用 Git Changes 既有查询方法，不在编辑器保存或文件树变更链路复制 diff 请求。Agent 空目录必须通过 `.gitkeep` 形成可追踪文件，否则 Git 无法感知；应用级新建和删除路径必须限制在既有 `opencode.jsonc`、`agents/**`、`skills/**` Diff 白名单内。Agent 删除复用工作空间确认面板，目录必须明确递归影响范围并关闭其下已打开标签。进入变更面板时必须立即核验，持续轮询只允许在面板可见期间复用同一刷新程序，离开面板必须停止，避免后台长期扫描 Git。
7. 应用级与公共级 Agent 的单文件和“全部暂存”必须复用同一 Git index 更新程序；批量操作向对应既有 stage API 一次传入当前作用域全部未暂存路径，不得逐文件并发请求，也不得跨作用域混合路径。无写权限、存在未解决冲突或 index 更新进行中时必须禁用批量暂存。
8. 聊天组件的失败、停止和完成等本地终态必须按 Session 身份隔离；已有 Session 被替换或清空时立即清理旧终态，历史恢复只使用目标 Session 的消息与 Run 状态。空草稿首次落成真实 Session ID 属于同一轮 Run，不得误清理其终态。
9. 托管工作区的普通文件写权限必须来自个人 worktree 记录与运行态 Workspace ID 的精确匹配，不得按名称、路径或角色推断。版本、应用、服务器目录或历史 Session 切换时，个人 worktree 身份必须和目标 Workspace 在目录加载前原子更新；未匹配到个人记录时保持只读，禁止先清空身份再依赖刷新恢复。
10. 具备 `MANAGED/APP_SOURCE/EXPERIENCE` 等互斥工作区语义时，用户选择必须建立统一 intent/代次边界；进入体验等非应用工作区后，默认应用补选、成员目录刷新、进程 READY 重试和 focus recent 恢复不得仅因 `selectedAppId` 为空而切回应用。一次性风险说明只能在目标工作区成功打开后按用户记忆，失败尝试不得永久跳过；本地存储不可用时允许退化为重复提示，不得阻断实际能力。
11. 非模态的全局顶部反馈消息不得遮挡或拦截其下方编辑器、导航和工作台控件的指针事件；如果消息提供显式关闭按钮，只为该按钮恢复指针交互，并保留键盘可访问语义。
12. 应用内功能页 Tab 只能按用户在版本化 `sessionStorage` 中持久化稳定页面 ID、顺序和活动项，不得保存页面数据、表单正文、票据、密钥或临时授权。已访问页面需要在关闭前保留挂载状态时，必须显式向页面传递活动状态：失活即停止页面级轮询、动画和延迟任务，恢复时刷新权威远端数据；密钥、Token 和临时排查授权必须在失活边界立即清理，并以请求代次阻止迟到响应重新写入敏感内容。
13. 常驻挂载页面中的 ECharts 等依赖容器尺寸的组件必须继承页面活动状态，并在初始化、更新和 `resize` 前同时确认 DOM 仍挂载且宽高均大于 0；页面恢复可见后必须用最新状态补绘。不得对 `v-show` 隐藏后的零尺寸容器直接调用图表布局，以免第三方坐标系生成不可逆 transform。

## 包边界

包职责与依赖方向见 `docs/architecture/module-map.md`。核心红线：

1. `workbench-shell` 只负责布局和面板生命周期，不写业务请求。
2. `file-explorer` 负责文件树和文件状态，不直接保存编辑器内容。
3. `editor` 负责 Monaco 编辑体验，不直接启动智能体任务。
4. `diff-viewer` 负责变更预览和接受/拒绝，不直接调用 opencode server；Diff 接受/拒绝是 Run 级语义，当前文件按钮只作为选择和反馈，不承诺 per-file 后端回滚。
5. `agent-chat` 负责对话和卡片呈现，任务执行请求必须走 `backend-api`。
6. `test-runner` 负责测试运行视图，测试状态来源必须是后端 API 或 RunEvent SSE。
7. `terminal` 负责 ticket WebSocket 连接、输入、resize、关闭和输出渲染，不创建 ticket、不直连 opencode server。
8. 文件搜索只过滤已加载文件树的文件名，不在前端自行扫描工作区，也不绕过后端新增搜索能力。

## UI 与交互

1. 使用 Tailwind 和 `packages/ui-kit` 建立统一设计语言，工具按钮优先使用图标和 tooltip。
2. 工作台、编辑器、文件树、Diff、报告等固定格式区域必须有稳定尺寸和桌面视口约束。工具盒子这类长目录的检索控制区必须吸顶到实际滚动容器；分类标签允许换行，不能依赖窗口滚动或遮挡结果卡片。
3. loading、empty、error、retry、cancel 状态必须完整；文案面向测试智能体工作流，避免暴露内部实现细节。固定在输入区上方的长工作状态必须设置高度上限与独立纵向滚动，不能把输入区推出可视面板。
4. 通知列表必须用图标和可访问文本同时表达未读、已读与不可用状态，不得把“未读取即过期/失效”伪装成已读；已有站内触达时，复制链接等兼容入口应保持低强调。支持拉伸的弹框必须设置视口内最小/最大尺寸并让正文独立滚动，不能靠扩大弹框把操作按钮推出视口。
5. 超级管理员 rollout 历史不得默认铺开完整服务器表格和用户进程坐标；列表摘要必须优先展示使用者可识别的应用、工作空间、版本、分支、目标提交、状态和待处理数量，内部业务 ID 不得进入普通页面 DOM。历史详情与用户明细分别使用可键盘操作的两级折叠并默认收起；停止或重启等高风险操作只能出现在展开后的用户明细中，继续复用既有运行管理确认链路。Git 仓库/worktree 刷新与发布后的运行态 dispose/重启必须用文案明确区分，避免两个控制面看起来重复。

## 字体与字号

企业入口不得在运行时依赖 Google Fonts 等公网字体服务。前端全局排版统一使用操作系统字体栈：正文优先 `-apple-system / BlinkMacSystemFont / Segoe UI / PingFang SC / Microsoft YaHei / Noto Sans CJK SC`，代码与等宽内容优先 `SFMono-Regular / Menlo / Monaco / Consolas / Liberation Mono`。新增或调整组件样式时，应优先复用全局 token 和 `ui-kit` 组件尺寸，避免在业务组件中散落不一致的字号和字重；如确需自定义字体，必须作为经过许可和体积评估的离线制品随前端发布，并使用 `font-display: swap`，不能增加运行时公网请求。

文件浏览区例外：工作区文件树、搜索结果、变更列表和 Agent 配置树可使用 `--ta-tree-*` 局部 token 模拟 VS Code Workbench 信息密度，字体栈限定为系统 UI 字体，字号为 13px，行高为 22px。该例外只能用于文件浏览区，不得扩散到聊天正文、设置页、编辑器正文或普通表单。

| 场景 | 字号 | 字重 |
| --- | ---: | --- |
| 页面标题 | 30-36px | 700 |
| 一级标题（H1） | 28-32px | 700 |
| 二级标题（H2） | 24px | 600 |
| 三级标题（H3） | 20px | 600 |
| **正文（默认聊天内容）** | **16px** | 400 |
| 次要说明文字 | 14px | 400 |
| Caption | 12px | 400 |
| 按钮文字 | 14px | 500 |
| 输入框文字 | **16px** | 400 |
| 代码块 | 14px | 400（`Geist Mono`） |

## 样式与组件编码规范

### 必须遵守

1. **基础 UI 必须优先使用 `packages/ui-kit`**：Button、Input、Badge、Tabs、Toast、Dialog、Tooltip、Select 等通用组件不得在业务页面重复实现。
2. **重复出现的 UI 必须抽组件**：相同 class 组合、DOM 结构或状态样式出现 2 次以上，应抽取为组件、variant 或公共布局。
3. **组件 class 合并必须统一使用 `cn`**：不允许手动字符串拼接 class；`cn` 内部应统一组合 `clsx` 和 `tailwind-merge`。
4. **基础组件变体必须使用 `cva` 管理**：`variant`、`size`、`state` 等稳定变体应集中定义，不得散落在业务页面。
5. **业务页面应组合组件，不应堆 Tailwind**：页面可以写布局 class，但不应重复编写复杂组件样式。
6. **颜色必须优先使用语义 token**：优先使用 `primary`、`secondary`、`muted`、`accent`、`destructive`、`background`、`foreground`、`border` 等语义 token；不得随意写死品牌色、灰阶或十六进制颜色。
7. **尺寸必须遵循统一体系**：基础组件应统一使用 `sm`、`md`、`lg`、`icon` 等尺寸语义；不得随意使用任意尺寸值，除非是 Monaco、Dockview、Terminal 等第三方集成需要。
8. **`ui-kit` 和业务组件必须分层**：通用基础组件放入 `ui-kit`；Web IDE / 工作台专用组件不得混入 `ui-kit`，应放入独立工作台组件层或业务模块。
9. **状态 UI 必须统一组件化**：Loading、Empty、Error、Disabled、Selected、Active 等通用状态不得在各页面重复实现。
10. **图标按钮必须统一封装**：所有图标按钮应使用统一组件，并提供可访问名称。
11. **复杂业务组件不得滥用 `cva`**：`cva` 只用于稳定、有限的组件变体；复杂业务状态应通过拆分组件和清晰的状态逻辑处理。
12. **长列表中不得进行复杂 class 计算**：虚拟列表、消息流、日志流、文件列表等高频渲染场景中，应避免重复执行复杂 `cn` 或变体计算。
13. **Tailwind class 顺序必须保持一致**：应使用统一排序规则或格式化工具，避免 class 顺序混乱。
14. **组件 API 不得过度暴露内部 class**：优先通过 props、variant、slot 和组合组件扩展；不得随意增加大量 `xxxClass` 属性。
15. **新增样式前必须先检查是否已有组件或 variant**：已存在的样式能力不得重复实现；可复用能力应沉淀到 `ui-kit`、工作台组件层或公共工具中。
16. **工作台 shell 与内容区主题必须隔离**：顶部栏、活动栏、栏间画布、三栏面板边框/圆角/阴影和左侧文件区只消费 `--ta-shell-*`；顶栏与栏间间隔使用同一浅雾蓝画布色，左/中/右三栏及栏顶保持纯白独立面板，工作区/Agent 目录加载态和中间无文件预览态不得透出外围画布色。顶栏非品牌文字默认保持纯黑，Logo 直接使用用户确认的初版耳机/拱形品牌图形 PNG，保留原图轮廓和比例，图形使用低饱和暗红实色 #7f1e2b，中文品牌字标使用黑色，英文副标题使用深红以呼应图形，并按“36px 首行 + 8px 面板间隔”的视觉带上下居中：Logo 左对齐；应用、工作空间、版本使用白底细框，并在 Logo 末端与右侧工具组起点之间保持左右等距；平台体验烧瓶图标和书本手册入口使用相邻的透明无框样式，运行态摘要使用透明底细框，三者与保留白底细框的单字头像依次固定在右侧，单字用户名使用 12px。体验区与手册打开态必须保持与活动栏一致的柔红选中底、深红图标和工行红定位反馈；体验烧瓶必须同时作为进入/退出开关，体验中再次点击要撤出并恢复进入前的应用，无应用时回到普通空态。顶部工作空间/版本必须复用左下角选择器的数据源、版本加载和切换回调；两处“新增版本”必须复用同一个工作台弹窗组件并统一进入 `AgentWorkbench.handleCreateVersion`，不得复制接口、权限、进程检查、缓存失效或工作区切换链路。顶部选定工作空间后，单版本默认选中该项，多版本默认选中后端倒序返回的最新项。对话继续消费 `--ta-chat-*`，编辑器继续使用既有 Monaco/Dockview token；调整外围色系时不得通过重映射通用 token 间接改变消息、输入框、时间线或编辑器内容区。
17. **通用记忆治理色只表达语义**：记忆中心允许个人蓝、团队青、候选琥珀、冲突红四个固定语义色，但普通表面、边框、正文和字体仍必须复用工作台 token。证据 rail 是该页面唯一标志性视觉；所有授权查看者可见 Session 标题/ID，只有 `transcriptAvailable=true` 才展示原对话链接，链接必须在新标签页打开所属人只读 transcript，不得复用或伪装成分享工作台。前端只能经 `backend-api` 读取治理 DTO 和真实 Run usage，不得直连 memory-service、复制聊天正文、伪造“已参考”徽标或扩展 RunEvent。
18. **活动栏页面必须有稳定 URI 和独立 Tab 语义**：工作台、工具箱、记忆、控制台和能力库必须分别使用 `/workbench`、`/toolbox`、`/memories`、`/system`、`/hub` 命名路由，用户自定义页面使用 `/custom/:menuId`，控制台非默认页使用受控的 `/system?section=<key>`，设置使用 `/settings`；根路径 `/` 只保留为 `/workbench` 兼容跳转。工作台不进入功能页 Tab，切回时只隐藏功能页并恢复工作台面板快照；其它活动栏页面和控制台二级页按稳定页面 ID 去重打开，关闭活动项优先选择右邻、否则左邻，关闭最后一项返回工作台。路由是当前功能页的权威来源，刷新、登录回跳和浏览器前进/后退必须切换或补开目标 Tab，但不得清空其它已打开页面；后台 Diff、SSE 或缓存刷新不得改写激活页面。自定义菜单定义必须按用户隔离，只保存名称、受控内置图标键和已校验的 HTTP(S)/同源根路径 URL，不得在 URL 中接受凭据或票据；嵌入页必须限制导航能力，失活时卸载 iframe，并提供外部页面拒绝嵌入时的新窗口兜底。

## 性能

### 首屏与加载

1. 首屏只加载工作台启动必需数据；大列表、大文件树、长日志和长报告必须分页、虚拟化或懒加载。
2. 非首屏能力按需加载（报告详情、Skill Studio、截图预览）；避免启动阶段并发请求无关接口。
3. Vite/路由/package 拆分必须控制首屏 bundle；Monaco 编辑器和 Monaco Diff 必须懒加载，不进入首屏同步 bundle。

### SSE 渲染

1. 高频事件批量合并、节流或局部状态更新；断开 SSE 时释放订阅、定时器和缓存引用。
2. `Last-Event-ID` 恢复不能导致重复渲染不可幂等 UI；日志类输出必须限制 DOM 节点数量，必要时用虚拟列表。

### 工作台与编辑器

1. Dockview 面板恢复不能阻塞首屏交互；Monaco 编辑器和 Diff 组件按需加载。
2. 大文件由后端一次性读取阈值统一判定；收到 `PREVIEW_TOO_LARGE` 时切换到 IDE 风格的渐进只读预览，先显示首段，再提供“继续加载一段”和“加载全部”入口。完整预览不得设置硬上限，但加载全部前必须提示内存占用和 Monaco 卡顿风险；追加分段应增量更新 Monaco 模型，不能每次重建完整正文。Diff 展示避免一次性渲染超大变更；面板切换时不得重复初始化重型实例。
3. 中间 Monaco 源码区默认按可视宽度自动换行；文件树复制/剪切/粘贴/撤销、删除、拖动和上传必须在 `canWrite=false` 时同时隐藏并在事件处理层阻断。“加入对话”与下载必须分别由 `canAttach`、`canDownload` 控制，严格只读场景不能仅依赖 `canWrite=false`。组合视图还必须执行节点级能力：纯引用文件/目录阻断所有变更，混合目录只能通过后端返回的 `workspacePath` 向工作区侧新增、上传、粘贴或拖入，不能重命名、删除或移动整棵混合目录；聚焦或右键纯引用文件也不能把其逻辑父路径当作粘贴目标。工作空间和 Agents 的根或目标目录 `+` 必须复用 `file-explorer` 共享新建面板并展示目标路径，Agents 关闭上传选项；文件/目录删除必须由行尾 `−` 或 Delete/Del 键进入同一确认弹框，目录递归删除要明确提示影响范围；拖放结束必须清理全部目标高亮，撤销历史不得跨个人 worktree 保留。
4. 独立 `.mind` 文件必须先于普通 Markdown/纯文本识别，文件树使用现有 `Tree` 图标，中栏默认只读画布且不得创建 Monaco；SimpleMindMap 及插件只能在实际预览或编辑时异步加载。只读、引用、渐进式大文件或不可信元数据不得进入编辑态；第三方实例不得写入 Pinia，tab 只保存可序列化文本草稿，草稿必须计入 dirty、关闭确认和后台刷新保护。

### 请求与缓存

1. 相同资源避免重复请求；请求必须支持取消或过期保护；页面切换时释放无用请求和订阅。
2. 缓存必须有失效策略；`@tanstack/vue-query` 的 query key 必须稳定。

### Bundle

1. 功能包按 Web IDE 能力拆分；大型依赖必须评估包体影响。
2. `ui-kit` 不引入与现有设计系统冲突的重量级依赖；`shared-types` 只放类型和轻量常量。

## 测试

在 `frontend/` 目录执行（本机 pnpm 可能不在 PATH，统一通过 Corepack 调用）：

```bash
corepack pnpm install
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
corepack pnpm e2e
corepack pnpm e2e:real
```

### 测试范围

至少覆盖：

1. `backend-api` client 的请求、响应、错误、超时、取消和默认/自定义 agent URL。
2. `event-stream-client` 的 RunEvent SSE 连接、agent URL、重连、去重和断点恢复。
3. `workbench-shell` 的面板注册、布局恢复和关闭行为。
4. `file-explorer` 的文件树展示、搜索、文件状态和打开文件。
5. `editor` 的文件加载、编辑、保存、只读状态和错误状态。
6. `diff-viewer` 的 Diff 展示、接受、拒绝和结果反馈。
7. `agent-chat` 的消息发送、实时输出、用户气泡、reasoning/text 分离、任务分解、Skill/Tool 分类展示、sticky scroll、TimelineCard 折叠卡片和默认展开规则。
8. `test-runner` 的启动、取消、重试和状态变化。

### 改动对应测试

- 改 API client：补请求、响应、错误、超时、取消、鉴权头和 agentId URL 测试。
- 改 RunEvent SSE：补 agent-scoped URL、连接、断线、`Last-Event-ID`、重复事件、乱序事件和取消订阅测试。
- 改工作台/文件树/编辑器/Diff/对话/测试面板：按对应交互场景补回归测试。
- 改超级管理员共享控制面：组件测试必须覆盖角色可见性、确认交互、部分失败结果展示；backend-api 测试还要断言请求不误带用户进程服务器路由头。

### Mock 原则

1. 前端测试 mock `test-agent-app` API，不 mock 内部组件行为；组件测试优先从用户交互出发。
2. 事件流测试必须模拟多事件、断线、重连、重复事件和最后事件 id。
3. E2E mock 必须使用 `docs/api/http-api.md` 中记录的后端 DTO 字段（例如文件列表使用 `directory` 而不是前端展示态 `type`）。
4. 真实 E2E 必须通过 `backend-api` 和平台 WebSocket/SSE 入口验证，不得让前端或测试代码直连 opencode 公网 share API。
5. `frontend-opencode` 的测试属于 opencode Vue/Vite 复刻工程验收，但不能替代 `frontend/` 主 workspace 的 Vitest、mock E2E 或 real E2E；`opencode-source/` 下的测试仍只作为参考。
6. `frontend/playwright.real.config.ts` 只匹配 `*.real-spec.ts`，`corepack pnpm e2e:real` 必须配合真实 `test-agent-app`、前端和 opencode server 使用，不能用 mock E2E 替代；`tools/dev-phase11-real-e2e.sh` 是主 `frontend/` 真实三服务验收入口。
7. `frontend-opencode/playwright.real.config.ts` 只匹配 `tests/e2e-real/*.real-spec.ts`，`cd frontend-opencode && corepack pnpm e2e:real` 是 opencode Vue/Vite 复刻工程自己的真实三服务 smoke 验收入口；该测试仍只能访问平台 `/api`、RunEvent SSE 和平台 PTY WebSocket。

## 完成标准

前端任务完成前必须说明：跑了哪些前端测试命令、覆盖了哪些交互场景、是否影响 API 或事件流文档、是否影响性能/安全/兼容性、哪些 README 已同步。
