# Session Log - huangzhenren

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

### 2026-08-09 - 将“原始输出”入口从对话框顶栏移动至底部状态栏（改为下载图标）

- Why:
  - 响应用户 UI 规范调整需求，将 Chat 面板顶栏文字按钮“原始输出”移除，并以“下载图标”形式下沉移动至底部状态栏（任务消耗/终态文字右侧）。
- What:
  - `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`:
    - 从 `figma-chat-header-left` 顶栏元素中移除 `figma-chat-header-btn--raw` 按钮。
    - 在底部 `figma-chat-footer` 容器末尾添加 `.figma-chat-status-raw-btn` 图标按钮，使用 `<Download :size="13" />`，保留 `aria-label="原始输出"` 与 `title`，绑定 `openRawOutput`。
    - 保持 `figma-chat-usage` 紧跟左侧上下文统计，单独设置 `.figma-chat-status-raw-btn` 为 `margin-left: auto`，使下载图标独立悬浮在底部状态栏最右侧边缘，并复用原原始输出图标颜色 `#6366f1` (hover `#4f46e5`)。
  - `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`: 更新测试查找原始输出按钮的选择逻辑。
  - `frontend/apps/agent-web/README.md`: 同步更新文档中对“原始输出”入口位置与展现形式的说明。
- How:
  - 调整组件模板与样式，并重跑 `npx vitest run apps/agent-web/tests/FigmaChatPanel.test.ts` 进行测试校验。
- Result:
  - 原始输出按钮成功从顶栏移至底部状态栏，改为简洁精致的下载图标，无遗留视觉冗余。

### 2026-08-09 - 将协作分享弹窗被分享人权限选择改造为开关切换（双侧标注“只读”与“可对话”）

- Why:
  - 响应用户交互需求，将“协作分享”弹窗（`SessionShareDialog.vue`）中被分享人的权限选择控件由复选框 `[ ] 只读` 改为开关切换（`el-switch`），开关左侧显示“只读”，右侧显示“可对话”。
- What:
  - `frontend/apps/agent-web/src/components/SessionShareDialog.vue`: 将被分享人列表项中的 checkbox `<input>` 替换为 `<el-switch>`，设置 `inactive-text="只读"`、`active-text="可对话"`，并添加对应的 Vue/Element-Plus deep 样式以保证切换文本与选定高亮效果。
  - `frontend/apps/agent-web/README.md`: 同步更新“协作分享弹窗”逐人 `canChat` 权限开关说明。
- How:
  - 修改模板与样式，并通过 `npx vitest run apps/agent-web/tests/session-share-management.test.ts apps/agent-web/tests/session-share-route.test.ts` 验证测试全部通过。
- Result:
  - 界面成功转换为符合要求的开关交互，两侧清晰展示“只读”与“可对话”，单测 100% 通过。

### 2026-08-09 - 修改分享会话页面他人消息背景色为 #C1B9F2

- Why:
  - 响应用户需求，把 `OTHER_MESSAGE_STYLE` 以及分享会话页面他人消息颜色修改为 `#C1B9F2`。
- What:
  - `frontend/packages/agent-chat/src/user-message-appearance.ts`: `OTHER_MESSAGE_STYLE` 中的 `backgroundColor` 从 `#9A8EDE` 改为 `#C1B9F2`。
  - `frontend/packages/agent-chat/tests/user-message-appearance.test.ts`: 更新单测断言中的颜色预期为 `#C1B9F2`。
  - `frontend/apps/agent-web/README.md` & `frontend/packages/agent-chat/README.md`: 同步更新相关模块 README 中关于他人消息背景色的文档说明。
- How:
  - 修改对应代码及测试、文档，并执行 `npx vitest run packages/agent-chat/tests/user-message-appearance.test.ts` 验证测试通过。
- Result:
  - 测试 100% 通过，相关代码与稳定文档同步更新完毕。

### 2026-08-08 - 落地多中心不规则蓝紫弥散雾环境光背景（无方向感、冷白清透低干扰）

- Why:
  - 响应用户精确的背景渐变需求规范：基于冷雾白 (`#F7F9FC`) 打造 7 个散落不规则中心、边缘极度柔和的半透明蓝紫雾气环境光，彻底消除方向感、偏绿偏青及彩虹色。
- What:
  - `globals.css`: 配置 `--ta-shell-canvas` 为 7 组错落分布的浅蓝系 (`#DEE9FF`, `#EAF2FF`, `#EEF3FF`) 与浅紫系 (`#E8E3FF`, `#DDD8FF`, `#F0EDFF`) 径向椭圆渐变，基底采用冷雾白 `#F7F9FC`；同步更新全局背景 token `--ta-bg`、`--ta-chrome`、`--ta-panel`、`--secondary` 与 `--background` 为 `#F7F9FC`。
  - `FigmaShell.test.ts`: 同步更新断言规则，校验 `#F7F9FC` 契约。
- How:
  - 基于低饱和多中心淡雾融色算法，并通过 `npx vitest run apps/agent-web/tests/FigmaShell.test.ts` 校验全套单测。
- Result:
  - 自动化单元测试 `FigmaShell.test.ts` (57 tests) 100% 通过；如同蓝色和紫色的半透明雾气随机漂浮在冷白背景中，界面干净清透、科技感强且低干扰。

### 2026-08-08 - 缩小对话框到任务栏的距离至 3px

- Why:
  - 响应用户界面布局优化需求：将输入对话框到底部任务栏/footer 的间距缩小为 3px。
- What:
  - `FigmaChatPanel.vue`: 将 `.figma-chat-composer` 容器的 bottom padding 由 `10px` 调整为 `3px` (`padding: 8px 10px 3px;`)。
- How:
  - 调整 `.figma-chat-composer` 内边距，使其下边界与常驻 footer（任务栏）的视觉距离精确保持在 3px。
- Result:
  - 输入对话框到底部任务栏/footer 的间距缩窄至 3px，布局更紧凑。

### 2026-08-08 - 修复左中右三大区域竖向滚动条独立的 hover 悬浮隔离（消除跨区域联动）

- Why:
  - 响应用户精确隔离要求：之前由于存在宽泛的选择器（如 `[class*="panel"]:hover` 或无前缀的 `:hover`），会导致悬浮到左侧工作空间时，右侧对话区的滚动条也被联动唤醒展示；用户明确要求只有鼠标直接悬浮到具体的某个区域（如工作空间），才展示对应区域的滚动条，其他区域保持隐形隔离。
- What:
  - `globals.css`: 彻底清理宽泛的 `[class*="panel"]` 及全级联 `:hover` 选择器。精准拆分为独立的区域限定规则：
    1. 左侧工作区 `.figma-panel-left:hover` 专有规则（**只亮左侧**）；
    2. 中间编辑器 `.figma-panel-center:hover` / `.dv-dockview:hover` 专有规则（**只亮中间**）；
    3. 右侧对话区 `.figma-chat-panel-wrapper:hover` / `.figma-panel-right:hover` 专有规则（**只亮右侧**）。
  - `FigmaChatPanel.vue`: 清除带有冒泡隐患的通用 `:hover >` 选择器，仅保留 `.figma-chat-panel:hover` 及 `.figma-chat-question-scroll:hover` 自身局部 hover。
- How:
  - 重写 CSS 作用域级联规则，消除共享父节点的属性模糊匹配，保证独立区域 Hover 互不影响。
- Result:
  - 鼠标悬浮至左侧工作空间时，仅工作空间显示滚动条；悬浮至右侧对话区时，仅对话区显示滚动条；三大区域互相隔离，绝无联动。

### 2026-08-08 - 修复工作空间与对话区域鼠标悬浮即刻绘制并显示竖向滚动条

- Why:
  - 响应用户最新操作问题反馈：原样式使用 `scrollbar-color: transparent` 触发了 macOS/Blink 的原生 overlay 滚动条逻辑，导致鼠标进入工作空间和对话区域悬浮时滚动条不显示，只有在发生真正的 `scroll` 交互时才会被拉起绘制；用户明确要求鼠标只要选入/悬浮到工作空间或对话区域，如果有滚动条就必须立即显现。
- What:
  - `globals.css`: **彻底移除原生 `scrollbar-color: transparent` 规则**，全面重构纯 Webkit 自定义滚动条。配置 `:hover::-webkit-scrollbar-thumb` 与专有区域选择器 `[class*="sidebar"]:hover ::-webkit-scrollbar-thumb`, `[class*="chat"]:hover ::-webkit-scrollbar-thumb` 等，让鼠标只要悬浮到左侧工作空间、右侧对话面板或中间编辑器等任意包含滚动条的区域，滚动条滑块（`rgba(140, 140, 140, 0.48)`）无需触发 `scroll` 事件即可**瞬间被内核强行绘制并立刻高亮显现**。
  - `FigmaChatPanel.vue`: 补充 `.figma-chat-panel:hover .figma-chat-question-scroll::-webkit-scrollbar-thumb` 等容器层 hover 规则，确保鼠标光标只要进入整个对话面板范畴，右侧对话消息记录的竖向滚动条便实时清晰呈现。
- How:
  - 解绑 overlay scrollbar 的逻辑判定，利用 CSS 伪类与 `:hover` 作用域级联提升权重，测试验证组件与交互契约。
- Result:
  - `FigmaChatPanel.test.ts` (154 tests) 全部通过。鼠标移动选入/悬浮到工作空间或对话区域后，竖向滚动条无需等待滚动即刻显现出来，未悬浮时恢复简洁无噪状态。

### 2026-08-08 - 批量生成测试案例定时选择面板改为从弹出框右壁向左滑出并平滑压缩列表

- Why:
  - 响应用户精确交互要求：定时选择抽屉必须限定在弹出框（Modal Dialog）内部，从弹出框的右边缘向左平滑滑出；弹出时绝不能占用或盖住底部的生成要求文本框与操作按钮；列表与搜索栏通过 `margin-right` 平滑向左收紧，确保全部操作区域正常可见。
- What:
  - `BatchTestCaseGenerationDialog.vue`: 
    1. **弹窗右壁定位**：将 `.batch-main-area` 设为 `position: relative; overflow: hidden`，作为抽屉的绝对定位容器；抽屉 `.batch-schedule-drawer` 设置为 `position: absolute; top:0; right:0; bottom:0; width: 380px`，贴靠弹出框内部右侧，动画从 `translateX(100%)` 平滑滑向 `translateX(0)`。
    2. **底部隔离与列表向左压缩**：Header 与 Footer 为弹出框的上下固定行，抽屉仅存在于中间主内容区，**底部的生成要求 textarea 与执行按钮 100% 保持独立全宽与可点击**。`batch-left-pane` 绑定 `:class="{ 'has-drawer': isDrawerOpen }"`，在抽屉展开时通过 `margin-right: 380px` 配合动画将搜索栏和列表平滑向左收紧。
- How:
  - 配置主内容区为相对定位基准，抽屉绝对定位从右壁滑出，配合左侧面板 margin-right 弹性平滑压缩，验证单元测试契约。
- Result:
  - `BatchTestCaseGenerationDialog.test.ts` (11 tests) 全部顺利通过。抽屉贴合弹出框右壁向左平滑滑出，列表平滑向左压缩，底部生成要求与按钮 100% 保持无遮挡。

### 2026-08-08 - 中间文件编辑器“预览”与“定位文件”图标替换及全彩主题升级（FileSearch文件预览/LocateFixed定位）

- Why:
  - 用户反馈原“预览”图标使用 `BookOpen` 会与系统左侧工具栏的“用户手册”图标重合，要求更换为更具独立操作代表语义的专有文件预览图标，并保留全彩主题。
- What:
  - `WorkbenchFooter.vue`: 
    1. **“预览”图标无冲突替换**：将“预览”图标更替换为**文档视图/检索预览图标 `<FileSearch />`**（📄🔍 专有代表文件视图与 Markdown 渲染预览，避开“用户手册”`<BookOpen />` 图标）；赋予海洋蓝/天空蓝主题色 (`#0284C7`) 及浅蓝 Hover/Active 状态 (`#E0F2FE`)。
    2. **“定位文件”图标替换**：使用**精确准心定位图标 `<LocateFixed />`**（🎯 语义直观表达在左侧文件树中精准定位高亮当前激活文件）；赋予琥珀金/橙色主题色 (`#D97706`) 及暖黄 Hover 状态 (`#FEF3C7`)。
- How:
  - 精确替换 semantic 图标，配置特定 icon class 与柔和 hover/active 底色，保留全部点击、状态与无障碍契约。
- Result:
  - `vue-tsc --noEmit` 零报错通过；“用户手册”与“文件预览”图标无重合冲突，全彩视觉清晰直观。


### 2026-08-08 - 优化顶栏全局切换轨道底色与应用/工作空间/版本下拉箭头消除留白

- Why:
  - 用户反馈顶栏中间“应用 / 工作空间 / 版本” 3 个选项所在轨道的底色需与周边顶栏画布一致（不能是白色），且应用名称与下拉箭头之间不应有被强行拉长的留白空间。
- What:
  - `FigmaShell.vue`: 将 `.figma-context-rail` 的 `background` 由纯白 `var(--ta-shell-surface, #fff)` 调整为与外围画布一致的浅雾蓝底色 `var(--ta-shell-canvas, #f0f4fa)`。
  - `FigmaShell.vue`: 将 `.figma-app-menu-name` 与 `.figma-context-menu-value` 的 `flex: 1 1 auto` 改为 `flex: 0 1 auto`，并将触发按钮的固定宽度强制拉长 `width: clamp(...)` 改为适应内容的 `max-width` 限制，使应用名称与 ChevronDown 下拉箭头自然贴合收紧，消除留白。
  - `FigmaShell.test.ts`: 同步更新 `figma-context-rail` 样式的匹配断言。
- How:
  - 调整 context-rail 容器 background 适配 `--ta-shell-canvas`，解绑按钮 flex 占据剩余空间的强制扩展，实现名称与箭头的紧凑水平对齐。
- Result:
  - 自动化单元测试 `FigmaShell.test.ts` (57 tests) 100% 全部通过。

### 2026-08-08 - 限定输入框顶部批量案例设计菜单仅在新建对话且未发送消息时展示

- Why:
  - 用户反馈输入框顶部的“批量案例设计”菜单只应在新建对话、且尚未发送任何消息时展示，发送消息后或从历史记录进入已有 Session 时不应展示。
- What:
  - `FigmaChatPanel.vue`: 新增 `showComposerTopMenu` computed 属性（定义为 `!props.currentSessionId && (props.messages ?? []).length === 0`），并在顶部的 `<button class="figma-chat-batch-entry">` 上添加 `v-if="showComposerTopMenu"` 门禁。
  - `FigmaChatPanel.test.ts`: 新增单元测试 `only displays top batch menu entry for a newly created session with no messages sent`，验证新建对话未发送消息时展示、已发送消息时隐藏、切换至已有历史 Session 时隐藏。
- How:
  - 通过 `currentSessionId` 区分是否为已存在的历史 Session（或已创建的 Session），通过 `messages.length` 区分当前对话是否已发送消息。在新建对话草稿状态下 `currentSessionId` 为空且 `messages` 数组为空，满足展示条件；发送消息或从历史列表切换 session 后条件失效，顶部批量案例设计入口按钮自动隐藏。
- Result:
  - `FigmaChatPanel.test.ts` 154 项测试全过（含新增测试）；`corepack pnpm test` 全量单元测试（1874 passed, 1 skipped）全过；`vue-tsc --noEmit` typecheck 校验通过。

### 2026-08-08 - 优化新建对话空白状态文案与当前宠物图标展示

- Why:
  - 用户需求要求将新建对话中的“等待任务输入”文案优化为“有什么可以帮你？”，添加辅助文案“描述你的目标或任务，智能体将协助完成。”，并按照设计规范样式并在标题左侧对齐展示当前选中的宠物图标。
- What:
  - `OpencodeTimeline.vue`: 重构 `oc-empty-state` 结构，包含 `oc-empty-state__container`、`oc-empty-state__header`（包含 `#empty-icon` 插槽与主标题）、`oc-empty-state__subtitle`（辅助文案“描述你的目标或任务，智能体将协助完成。”），并在下方增加“快捷命令：”提示前缀及 `@`（选择 Agent 或 上下文文件）、`#`（选择需求与测试资源）、`/`（技能与命令）快捷操作命令描述及 `empty-shortcuts` 插槽。
  - `timeline.css`: 精确落盘文案与布局规范：主标题 `PingFang SC` 16px/600(Semibold)/行高24px/颜色 `#1F2329`，辅助说明 `PingFang SC` 14px/400(Regular)/行高22px/颜色 `#8A8F99`，标题与说明间距 8px，快捷命令以徽章形式展示在辅助文案下方，文案最大宽度 340px (居于 320–360px 范围)，整体水平居中，图标在标题左侧垂直居中对齐。
  - `FigmaShell.vue`: 增加 `provide("activePetId", activePetId)`，使子组件能响应式获取当前选中的宠物。
  - `FigmaChatPanel.vue`: 注入 `activePetId`，并向 `OpencodeTimeline` 传递 `#empty-icon` 插槽（渲染 `<PetCompanionAvatar :pet-id="activePetId" />`）。
  - 测试套件: 在 `opencode-timeline.test.ts` 和 `FigmaChatPanel.test.ts` 中补齐空白状态主副标题、快捷指令描述及宠物图标渲染断言。
- How:
  - 采用 Vue `provide` / `inject` + Slot 模式，既保持 `agent-chat` 包与宠物资源的解耦，又能实时响应 `FigmaShell` 中宠物名册与每日轮换策略的切换。
- Result:
  - 自动化单元测试 `opencode-timeline.test.ts` (42 passed) 和 `FigmaChatPanel.test.ts` (153 passed) 全过，`npm run typecheck` 校验通过。

### 2026-08-08 - 优化用户消息操作栏（撤销重发、复制）悬浮于气泡下方且仅展示图标

- Why:
  - 用户反馈用户消息的气泡操作按钮（撤销重发、复制）需在鼠标悬浮时展示在气泡下方，且统一仅展示图标。
- What:
  - `UserMessageRow.vue`: 将“撤销重发”与“复制”按钮统一移入消息气泡 `oc-user-message__bubble` 下方的 `oc-user-message__actions` 容器中；“撤销重发”按钮改为仅展示 `<RotateCcw />` 图标按钮，保留 `title="撤销重发"` 与 `aria-label="撤销重发最后一条消息"` 辅助属性。
  - `rows.css`: 调整 `oc-user-message__bubble` 右侧 padding 为 `6px 12px`；`.oc-user-message__actions` 默认 `opacity: 0`，在 `.oc-user-message:hover` 及 `:focus-within` 时展示在气泡下方右侧；`.oc-user-message__resend` 统一使用 `20x20px` 图标按钮样式。
- How:
  - 检查类名保留 `.oc-user-message__resend` 以保证自动化测试选择器兼容。
- Result:
  - `FigmaChatPanel.test.ts` (151 passed, 1 skipped) 测试全过；`vue-tsc --noEmit` 校验通过。

### 2026-08-08 - 修改对话框输入卡片快捷入口文本为批量案例设计

- Why:
  - 用户要求将对话框右上角的批量生成入口按钮文本由“批量案例”修改为“批量案例设计”。
- What:
  - `FigmaChatPanel.vue`: 将按钮内的 `<span>批量案例</span>` 修改为 `<span>批量案例设计</span>`。
- How:
  - 检索确认现有测试依赖 `data-testid="batch-test-case-entry"` 定位，修改 span 文本不会破坏既有自动化测试。
- Result:
  - `npm run typecheck` (`vue-tsc --noEmit`) 校验通过。

### 2026-08-07 - 居中面板 drag handle / resize handle 垂直与水平分割线

- Why:
  - 用户反馈右侧对话面板与中间编辑器之间的拖拽分割线（resize handle）未处于最中间，指示线偏向右侧面板边缘。
- What:
  - `FigmaShell.vue`: 为 `.figma-chat-panel-wrapper` 补充 `position: relative`，并将 `.figma-chat-resize-handle` 调整为 `position: absolute; left: calc(-1 * var(--ta-shell-gap, 8px)); width: var(--ta-shell-gap, 8px); top: 0; bottom: 0;`；内部 `::before` 指示线由原本偏右（0.5px）修正为在 8px 间隔内水平与垂直精确居中（`left: 50%; top: 50%; transform: translate(-50%, -50%);`），`::after` 拖拽响应区域填满 8px 间隔。
  - `FigmaShell.vue`: 优化 `.figma-runtime-inventory-resize-handle::after` 指示线位置，从 `left: 2px; transform: translateY(-50%)` 修正为 `left: 50%; transform: translate(-50%, -50%)`。
  - `AgentSkillHub.vue`: 同步优化 `.hub-detail-resize-handle::after` 从 `left: 2px` 修正为 `left: 50%; transform: translate(-50%, -50%)` 居中。
- How:
  - 检查 DOM 与 CSS 发现：`.figma-chat-panel-wrapper` 的 8px `margin-left` 在面板外侧，而原 `.figma-chat-resize-handle` 宽度仅为 1px 并放置在 wrapper 最左侧（即 8px 间隔的最右边），导致内部 `::before` 偏向右侧面板。改成 absolute 填满 8px 间隔后，`left: 50%` 精确置于间隔中心 (4px)。
- Result:
  - `npm run typecheck` (`vue-tsc --noEmit`) 校验通过。拖拽分割线在中央与右侧面板间 8px 缝隙内完美居中呈现。

### 2026-07-31 - 优化工作台外围配色为中国工商银行 (ICBC) 企业级极简红白风格

- Why:
  - 用户反馈上一轮外围配色不够美观（“现在改了一轮，但感觉很丑，看看配色上怎么调整一下”），要求对照中国工商银行 (ICBC) 风格大屏/工作台视觉设计，重点重构外围配色，并保持对话区域样式不变。
- What:
  - 在 `globals.css` 新增隔离的外围 `--ta-shell-*` token，引入工行红 (`#C8161D`)、深红 (`#991B1B`) 和柔红背景 (`#FDF2F2`)，搭配复用现有弹框色值的浅雾蓝画布 (`#F0F4FA`)、纯白三栏底 (`#FFFFFF`)、发丝边框 (`#E5E7EB`)、主文字 (`#1F2937`) 和次要文字 (`#6B7280`)；通用、Element Plus、Monaco 和 `--ta-chat-*` token 保持原值。
  - `FigmaShell.vue` 让 36px 顶栏与栏间 8px 间隔共用浅雾蓝画布，活动栏融入画布，左侧文件区、中间编辑区和右侧 Agent 区分别使用纯白底、8px 圆角、发丝边框与轻阴影形成悬浮面板；工作区/Agent 目录加载前后与中间无文件预览态保持纯白，`FigmaEditorArea.vue` 仅将顶部 tabbar 和 tab 底色统一为纯白，并用 2px 工行红上沿标记当前文件。
  - 顶栏默认文字统一为纯黑；Logo 保持左侧并下移 2px，手册、运行态数量、应用和单字用户名恢复白色底板与细线框，控件组整体绝对定位在首行水平中间。单字用户名放大为 12px，打开/按下状态继续复用柔红底和工行红定位反馈，原点击与弹层逻辑未改。
  - 活动栏使用柔红选中底与 3px 工行红定位条，左右拖拽间隔保持画布底色，悬浮时使用同一红色反馈。
  - Logo 继续使用黑色品牌图形和中文标题；`FigmaChatPanel.vue` 与中间编辑器保持原样，未消费任何 `--ta-shell-*` 变量。
  - `FigmaShell.test.ts` 增加源码级边界回归，锁定 shell 核心色值、通用/对话 token 原值以及对话组件不得引用 shell token。
- How:
  - 提取 CloudStudio 参考页的核心布局与配色：同色顶栏/栏间画布、三块独立白色面板、深灰字、现有弹框浅雾蓝底、柔红选中态和工行红激活条。
  - 消除旧版灰青与红色的冲撞色，重设全套 shell 级 CSS 变量与 Vue scoped 降级值。
- Result:
  - 三栏纯白内容面与浅雾蓝间隔边界清晰并具有克制的悬浮层次，外围呈现工行金融企业级系统质感；对话区内部样式和逻辑未受任何干扰。
  - 已通过 `FigmaShell.test.ts` 53/53、前端 lint/typecheck、生产构建和本地真实页面检查；全量 Vitest 中 1739 项通过、1 项跳过，2 项 Mermaid 并发计时用例首次失败后以单 worker 复跑 21/21 通过，确认与本次纯样式改动无关。

### 2026-07-24 - 定稿超级管理员自定义定时执行时间设计

- Why:
  - 超级管理员需要在白天方便验证夜间任务的 XXL 分发、固定服务器路由、普通 Run 启动和状态展示，而普通用户仍应保持既有夜间容量规则。
- What:
  - 定稿 `NIGHT_WINDOW` 与 `ADMIN_CUSTOM` 双模式：普通用户继续选择 15 分钟夜间时段；超级管理员可用 1/3/5 分钟快捷项或北京时间日期时间输入选择下一分钟至未来 24 小时的任意分钟。
  - 自定义模式不占用 `NIGHT_EXECUTION_SLOT_CAPACITY`，使用 15 分钟重试宽限期；XXL 分发扫描改为每分钟，两种模式复用同一任务表、handler、租约/心跳/attemptId、Run 幂等和补偿链路。
  - 设计明确可选 `scheduleMode` 的 API 兼容、后端 `SUPER_ADMIN` 强校验、PostgreSQL 模式字段、MySQL Cron 更新、待执行展示、调整/取消和测试文档边界。
- How:
  - 对比“双模式扫描”“映射 15 分钟容量桶”“每任务动态 XXL Job”三种方案后选择最小且可真实覆盖生产链路的双模式；完成占位符、矛盾、范围和兼容性自检。
- Result:
  - 设计写入 `docs/superpowers/specs/2026-07-24-super-admin-custom-schedule-time-design.md`；本次仅冻结设计，不修改运行时代码、API、数据库、环境配置或 generated SDK，待用户审阅后再编写实施计划。

### 2026-07-23 - 上下文圆环展示占用等级

- Why:
  - 16px 会话上下文圆环此前只用固定紫色表示使用量，无法在接近或超过模型上下文上限时提供即时风险反馈。
- What:
  - 新增可测试的 `unknown/normal/warning/danger` 等级计算：未知上限为 unknown，0–59% 为 normal，60–79% 为 warning，80% 及以上（包括溢出）为 danger。
  - 触发按钮增加等级 class 与 `data-usage-level`，圆环保留 12 点起始、顺时针、16px/2px stroke 与既有弧长；normal/warning/danger 精确使用紫/橙/红色，未知上限只保留空轨道，hover/focus 随等级给出低透明背景，键盘 `focus-visible` 增加同色可见描边，并在 reduced-motion 下关闭颜色和弧长过渡。
  - 补齐指标/组件/Chromium mock E2E 断言，并同步前端与 agent-web README 的 60/80 阈值、未知及溢出语义。
- How:
  - 先执行测试先行 RED：等级函数缺失，组件缺少等级 class/data 属性；再以最小 helper、computed 状态和 CSS 状态实现至 GREEN。Chromium 场景断言 50% 圆环 `rgb(164, 13, 188)`、半周 dash offset 和键盘聚焦描边。
- Result:
  - 聚焦 Vitest 20/20 通过，Chromium 上下文 E2E 1/1 通过，`corepack pnpm lint`、`typecheck` 和 `build` 通过；全量 Vitest 仍只保留既有 `DirectoryRows.test.ts` 将实际 role=`radio` 的“上传”查询为 role=`button`（1 failed，1582 passed，1 skipped），未修改 file-explorer。
  - 未涉及 API、RunEvent、数据库、性能、安全、兼容性、环境配置或 generated SDK；`git diff --check` 通过。

### 2026-07-23 - 确认并完成 main 并发推送

- Why:
  - 本地 `main` 比 Gitee `origin/main` 超前 25 个提交，首次大对象推送没有即时返回明确退出码；并发重试在对象上传完成后收到 `incorrect old value provided`，需要区分真实内容冲突与远端引用已被另一推送更新。
- What:
  - 刷新远端引用并核对提交拓扑、未合并索引项和冲突标记；确认远端原本是本地直接祖先，不需要合并、变基或强制推送。
  - 推送后重新执行 fetch、提交计数与本地/远端哈希比对，确认第一次推送已把远端推进到本地 HEAD，第二次并发推送的拒绝属于旧值竞争。
- How:
  - 使用普通 `git push origin main:main`，未使用 force；以 `git rev-list --left-right --count main...origin/main`、`git rev-parse` 和 `git ls-remote` 交叉验证远端结果。
- Result:
  - 本地与远端 `main` 同步到 `afa2b61c827f2cb229d3d7e6f43858c265fe810f`，工作区无未解决冲突；未修改代码、API、事件、数据库、性能、安全、兼容性、环境配置或 generated SDK。

### 2026-07-22 - 新增企业 XXL-JOB 只读排查手册

- Why:
  - 企业双后台 XXL-JOB 管理页、SSO、Admin、executor、Redis 与共享 MySQL 缺少可直接复制到现场执行、能明确停止边界并避免主动重放或状态变更的统一排查入口。
- What:
  - 固化前端 `122.233.30.2`、后台 `122.233.30.4/122.233.30.114`、Redis `122.233.30.20`、MySQL `122.233.30.148` 拓扑，交付入口、前端 Nginx、后台 Java/Admin/executor 三个可独立携带的自包含 Bash 诊断脚本。
  - 新增受静态边界校验的 MySQL 只读检查 SQL、十五章企业排查手册、文档索引与临时夹具 verifier；三个现场脚本保留少量重复，以保证单文件复制后无需依赖共享库即可执行。
  - 最终审查进一步区分域名经企业入口到 `.2:80` 与 IP 直达 `.2:9996` 的双入口拓扑；入口脚本拒绝五个基础设施节点，Nginx 检查排除注释伪指令，普通低熵凭据仅报告 `SET/UNSET`，MySQL 静态边界拒绝文件写入、named lock 与用户变量赋值，三个脚本统一输出最终摘要。
- How:
  - 诊断严格限制为 DNS/HTTP readiness、有效 Nginx 配置读取、systemd/端口/进程/日志、TCP 可达与只读数据库查询；禁止 SSO 重放、Redis 票据或会话读取、任务触发、服务或容器生命周期变更、配置写入和 SQL DML。
  - URL query/fragment、认证头和常见敏感键在输出前统一脱敏；本条不记录任何现场凭据值或摘要。verifier 只使用临时 fake 命令、配置、日志与 SQL 夹具，不访问五个企业地址。
  - 最终审查按 RED→GREEN 增加九组负向/正向回归；证据 AWK 固定在 verifier 内，手册展示块只做逐字节合同校验，恶意 Markdown `system()` 文本不会被执行；SSO 仍只检查事故时既有被动证据。
- Result:
  - 三个诊断脚本与 verifier 的 Bash 语法检查、完整行为 verifier、AI 文档校验及手册 13 个 Bash 块语法检查均退出 `0`；危险操作、SQL 写操作/静态副作用与冲突标记扫描均无匹配，任务路径及全工作树 `git diff --check` 均退出 `0`。
  - 未修改运行时代码、HTTP API、RunEvent、数据库结构/Flyway、环境配置或 generated SDK；尚未在企业五台目标机器执行，现场网络、进程和数据状态仍需按手册由授权人员只读取证。

### 2026-07-22 - 升级 OpenCode 1.18.4 官方 baseline 与 Java SDK

- Why:
  - 项目运行时、源码审计快照和 generated Java SDK 仍基于 OpenCode 1.17.8；用户要求升级到最新稳定版、重新使用最新 OpenAPI Generator 校验影响，并明确企业 Linux 程序必须直接使用官方 `opencode-linux-x64-baseline.tar.gz`，不能从源码构建。
- What:
  - 将审计源码快照、plugin/SDK 依赖及本机 OpenCode 更新到 1.18.4；企业 worker 下载并校验官方 baseline 归档/二进制 SHA，启动器只执行官方程序，源码不参与二进制构建。保留 1.17.8 官方 baseline 回滚 image/programs。
  - OpenAPI Generator 固定为最新稳定版 7.24.0，重新同步 generator 工程和后端 generated SDK；规范由 150/339/175 个 paths/schemas/operations 增至 162/472/188，新增 13 个 operation、无删除，平台消费的 39 个 operation 契约未破坏。生成脚本统一清理生成器输出的行尾空格，generated 源码不手改。
  - 1.18.2+ 启动配置固定 `subagent_depth=2`，后端和前端补齐 root → child → grandchild 的 scope 归属与状态隔离；1.17.8 回滚启动器会删除旧版不支持的字段。离线运行时继续固定依赖、禁用自动安装，并保留 `includeUsage=false` 兼容要求。
  - 重写企业 worker/package 链路以固定官方 asset、大小、归档/程序 SHA、glibc 2.31、静态 tini/ripgrep 和 Node/Go 基础镜像；完整包同时携带 backend、frontend、programs、worker image，未操作企业服务器。
- How:
  - 对比 1.17.8 与 1.18.4 官方 `/doc`、release asset 和源码 tag `49c69c5ed3ccf706b61b3febb43c8aaff7f8325e`；官方 1.18.4 baseline 归档 SHA-256 为 `4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc`，程序 SHA-256 为 `6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5`。
  - 当前与回滚 worker 均通过断网 serve/health、Tool 链接、RELEASE 元数据、深度配置和优雅停止冒烟；launcher 5/5、前端嵌套 scope 21/21、升级相关后端 Linux JDK 定向 28/28 通过。此前健康本机 JDK 下 client/runtime reactor 为 773 项全通过。
  - 前端 lint、typecheck、生产 build 通过；全量 Vitest 为 1542 passed / 1 skipped / 1 failed，唯一失败仍是既有 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询。Linux/musl 全后端运行通过 runtime 前各模块和 runtime 702/706，3 项仅因 glibc PTY/`/bin/bash` 不存在失败，1 项既有 1 秒重试断言单独复跑通过；本机 JDK 的 `libattach.dylib`/`libinstrument.dylib` 代码签名异常不属于本次代码。
- Result:
  - 完整离线包为 `deploy/internal/dist-1.18.4/test-agent-internal-release.zip`，SHA-256 `1f7baecd9877aedc82ab45e167975f10680ccb6aeeffb08142e6451af1da98e1`；1.17.8 回滚 image tar SHA-256 为 `a2fdfc588f2d3166cc26f8f9fd61daa9e937487ec93c037cf8cd8841f9c5cf8d`。两者只在本机生成，未部署企业节点。
  - 已同步 OpenCode client/runtime/generated SDK、前端、内部部署、HTTP/SSE 索引、模块图与测试文档；平台 HTTP API、RunEvent wire、数据库/Flyway、关系型 SQL、鉴权和安全边界未变，没有修改 `.env.local`。本机外部 OpenCode 配置只补充兼容深度并保留原内容。
- Next:
  - 企业现场按文档同时替换 image/programs 并滚动重启用户 OpenCode 进程；如需恢复两个全量测试套件全绿，应另行修复既有 `DirectoryRows` 断言，并重装/修复本机 JDK 签名或在 glibc Linux JDK 环境执行后端全量测试。

### 2026-07-22 - 优化权限请求展示与待处理提醒

- Why:
  - 平台把 OpenCode `permission.asked` 只显示为 `external_directory + permission id`，遗漏原生 `patterns[]` 路径与中文说明；运行态摘要和历史铃铛又只识别 question，child Agent 待授权时无法定位。
- What:
  - 前端新增共享 permission 展示转换，对齐 OpenCode 1.17.8 的中文标题/14 类说明，优先保留去重后的 `patterns[]` 并兼容旧 `pattern/title/description`；Figma 卡和 RuntimeDock 统一警告图标、代码路径及“拒绝 / 始终允许 / 允许一次”，不展示内部 type/requestId。
  - `PermissionRequest.patterns`、运行态 `permissionCount` 和 `PERMISSION` attention 以可选/开放字符串方式向后兼容；历史入口统计 question+permission，任意 attention 的会话卡显示铃铛，pending permission 只在 sessionId 精确匹配的 child task 状态前显示可访问动态 Bell，回复后 reducer 清除。
  - 历史恢复把根 permission HTTP 快照限制在 root scope，保留 session tree child 请求；`run.snapshot.reset` 递归投影 root 交互的远端 Session ID 并保留 child scope。
  - 后端 Redis/legacy MyBatis 摘要支持 permission asked/replied。Redis 用类型化 SHA-256 key 的独立 Hash + order ZSET 保存最多 1024 个未决交互，字节计入 32 MiB/非快照预算，同 ID question/permission 独立、回复最新项后恢复更早项、终态清空；legacy SQL 按 Run seq 收敛，H2 与 PostgreSQL jsonb 均兼容真实 `sessionID -> requestID` 顺序且不误取嵌套 id。
- How:
  - TDD 覆盖用户提供的真实 external_directory 事件、多/旧 pattern、未知类型、三个动作、历史 root/child 恢复、并行 child 精确铃铛、旧摘要兼容、SSE 刷新、Redis 同/跨类型并发与容量、H2/真实 PostgreSQL 时钟回拨和请求 ID 提取；多轮只读代码审查发现的 legacy 无 ID、reset 投影、并发 attention、容量与 seq/JSON 方言边界均已补回归。
- Result:
  - 前端目标 Vitest 431 passed / 1 skipped，mock Playwright 桌面/移动 6 passed，lint、typecheck、生产 build 通过；全量 Vitest 的唯一失败仍是既有 `DirectoryRows.test.ts` 用 role=`button` 查询实际 role=`radio` 的“上传”，相关文件未修改。
  - 后端真实 Redis 9 项、H2 legacy 4 项、真实 PostgreSQL jsonb/Flyway 1 项及 domain/runtime/API 定向测试通过；20 模块 `mvn clean package -DskipTests` 成功。全量 `mvn test` 仍仅被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 H2 `TIMESTAMPTZ` setup 基线阻断，真实 PostgreSQL 完整 Flyway 链已通过。
  - 已同步 HTTP/SSE、模块图、测试场景和前后端模块 README/PACKAGE；新增字段均向后兼容，不新增 RunEvent wire name、reply 协议、数据库结构/Flyway、generated SDK、环境配置或鉴权变化。物理路径仅在已授权交互卡显示，不进入通知/日志；无未完成编码项。
- Next:
  - 单独修复既有 H2 migration 与 `DirectoryRows` 基线后可恢复两个全量套件全绿，本功能无需追加迁移。

### 2026-07-22 - 修复主子智能体切换滚动位置

- Why:
  - 主、子 Agent 共用 `.figma-chat-scroll`；切换到较短的子时间线时浏览器会压缩共享 `scrollTop`，返回主 Agent 后因此落到首条消息。
- What:
  - `FigmaChatPanel` 按 `root` / `subagent:<sessionId>` 保存组件内存快照，分别记录滚动位置、离开时是否在底部、固定大小的可见正文摘要和新内容提示状态；首次进入或离开时在底部均跟随最新底部，上滑视图恢复原位置。
  - 正文摘要只聚合 `opencodeTimelineState` 当前 scope 可见的消息身份、正文/text part 和流式可见长度，并缓存未变消息摘要；反馈、工具状态和其它子 Agent 输出不会推动当前视口。
  - 延迟滚动增加 scope、代次、viewport 归属和 pending restore 防护；wheel、touch、pointer 用户意图可接管恢复，布局 clamp、快速往返、根终态/history watcher 和跨 scope 残留意图不会覆盖目标快照。
  - 同步 `frontend/README.md`、`frontend/apps/agent-web/README.md`、`frontend/apps/agent-web/src/PACKAGE.md`，并新增独立滚动回归文件，避免混入同一工作树中并行的 permission/attention 测试改动。
- How:
  - TDD 覆盖主/子位置独立恢复、首次滚底、inactive scope 新正文、其它子 Agent 隔离、快速切换、真实会话重置、草稿首次取得 ID、浏览器 clamp、root/child pending restore、用户主动接管和跨 scope 用户意图清理，共 20 项；多轮只读任务审查最终无 Critical/Important finding。
  - `corepack pnpm test --run apps/agent-web/tests/FigmaChatPanel.test.ts` 为 134 passed / 1 skipped；滚动专项为 20 passed；`corepack pnpm lint`、`corepack pnpm typecheck`、agent-web 生产 build 和 `git diff --check` 通过。
- Result:
  - 主、子 Agent 在当前页面生命周期内各自保留阅读位置，后台并行输出和过期延迟回调不再改变当前视图；快照不持久化。
  - 前端全量 Vitest 为 1527 passed / 1 skipped / 1 failed；唯一失败是任务外既有 `DirectoryRows.test.ts` 将 role=`radio` 的“上传”按 role=`button` 查询，单独复跑稳定失败，相关文件本次无改动且近期 session log 已有记录。
  - 未修改公共组件接口、CSS、HTTP API、RunEvent、数据库、后端、安全、环境配置或 generated SDK；无新增依赖。
- Next:
  - 单独修复 `DirectoryRows.test.ts` 的可访问角色断言后恢复前端全量绿灯；本滚动功能无未完成实现项。

### 2026-07-21 - 引用配置自动写入外部目录权限

- Why:
  - 引用路径位于工作区外部时，OpenCode 会发出 `external_directory` 权限询问；仅写 `references` 会让已选引用仍被全局 `ask` 兜底拦截。
- What:
  - 引用 JSONC 补丁在同一正文中同时维护当前 alias 与 `permission.external_directory["{path}/*"] = "allow"`，同路径 `ask/deny` 强制覆盖，并确保精确 allow 位于最后一条匹配规则；合法的根权限和外部目录字符串简写展开后保留原 `*` 兜底，非法结构用中文校验错误整次拒绝。
  - 弹窗新增权限漂移状态：存量引用的权限缺失、冲突或被后置宽泛规则覆盖时，无需修改描述即可更新；保存仍只重读一次最新正文、生成一次补丁并写盘一次，成功后清除漂移状态。
  - 同步前端/agent-web README、组件 PACKAGE、引用配置用户手册和应用 worktree 测试说明；授权范围固定为当前所选根层 SDD 目录，不扫描其它引用、不写仓库级或全局 `* allow`。
- How:
  - 复用 `jsonc-parser` 的 JSONC 字段编辑与语法树，只在规则重排时移除精确属性正文并保留周围注释；TDD 覆盖空文件、新增/更新、简写、宽泛规则、同路径冲突、非法结构、CRLF、注释/未知字段、幂等、按钮漂移和并发保存 fencing。
- Result:
  - 两个目标测试文件 84 项通过；前端 typecheck、lint、用户手册及生产 build、`git diff --check` 通过。前端全量 Vitest 为 1484 passed / 1 skipped / 1 failed；唯一失败仍是既有 `DirectoryRows` 用 `button` 查询实际 `radio` 角色的“上传”，本轮开始前已复现且相关文件未修改。
  - 不涉及后端、HTTP API、RunEvent/SSE、数据库/SQL、generated SDK、环境配置、性能或鉴权模型；权限变化仅写入当前个人 worktree 的 OpenCode JSONC。工作树中并行的 manager/后端日志功能改动和既有 `.config` 删除未纳入本次范围。

### 2026-07-21 - 按用户和启动实例拆分 OpenCode 进程日志

- Why:
  - 用户 opencode server 日志原来只按端口写入 `{port}.log`；端口会变化、复用，同一端口的多次启动也会混写，难以按用户定位一次具体启动。
- What:
  - Java 公共启动链路新增可选 `unifiedAuthId`，优先读取用户仓储中的统一认证号，缺失时只允许从已校验的 `.../users/{unifiedAuthId}` session 路径派生；manager WebSocket `start` 透传该字段，协议版本保持 `opencode-manager.v1`。
  - Go manager 将单次启动日志改为 `{safeUnifiedAuthId}-{yyyyMMddTHHmmss.nnnnnnnnnZ}-{port}.log`，UTC 启动时间与 state `startedAt` 使用同一时刻，stdout/stderr 共同写入该文件；restart 保留用户身份但生成新文件。
  - 文件名对非安全 UTF-8 字节使用大写 `%HH`，超长身份使用有界前缀和完整 SHA-256；显式身份必须能由稳定 `users/{id}` session 路径验证，路径不稳定或身份不一致时都返回不含原始身份的通用错误。旧 state、旧 Java 和本地 CLI 继续兼容，无法派生身份时仍写 `{port}.log`，已有旧日志不迁移、不删除。
  - 同步 manager/runtime README、后端部署说明和安全脱敏边界；同时把 `tools/verify-ai-docs.sh` 中已过时的外部服务断言更新为包含正文已有的 `XXL MySQL`。
- How:
  - 复用 `OpencodeProcessStartupService` 公共启动程序和既有 manager socket gateway；统一认证号写入 manager 本地 state 供 restart 恢复，不新增业务旁路、HTTP 文件代理或数据库持久化。
  - Go 以 TDD 覆盖精确文件名、特殊字符、超长身份、session 路径校验、restart 新文件及旧 JSON；Java 覆盖仓储优先级、安全路径 fallback、assignment 下发、JSON 编解码和旧构造器兼容。
- Result:
  - `go test -count=1 ./...` 全量通过；Java 日志链路定向 64 项、runtime 全量 638 项和下游 `ManagerControlWebSocketHandlerTest` 5 项通过。runtime 全量首次在无关的 1 秒定时重试断言上瞬时失败，单测复跑 5 项和随后全量复跑均通过，未修改该无关测试或运行代码。
  - `tools/verify-ai-docs.sh`、`git diff --check` 和目标目录冲突标记扫描通过。文档已明确企业纯 Docker 路径、最近日志定位、旧文件兼容与文件名身份脱敏。
  - 不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、前端、generated SDK、环境变量或 `.env.local`；manager 自身 `manager.log/manager-error.log` 路径不变。真实企业 worker 仍需在部署新 Java/manager 后观察一次启动、同端口 restart 和日志采集/保留策略。

### 2026-07-21 - 同步 main 并保留两侧功能

- Why:
  - 本地 `main` 相对 `origin/main` 领先 9 个、落后 44 个提交，直接推送会被拒绝；本地 XXL-JOB/夜间执行改造与远端 Git 发布恢复、Nginx 精确路由、内置 RSA、用户安全删除等改动存在交叉，冲突处理不能整文件取舍。
- What:
  - 将 9 个本地提交逐个 rebase 到远端 `0fb851e15`，逐段合并会话日志、模块图、部署与安全文档；保留远端 `TEST_AGENT_NGINX_SERVER_ROUTES`、JAR 内置 RSA、Git 企业邮箱及用户 Token 批量撤销，同时保留本地 XXL Admin/executor、地址派生、嵌入页样式和夜间 attempt/租约/补偿迁移。
  - 合并 Redis Token 两侧能力时补齐批量撤销对应 SHA-256 session marker 的删除，并新增回归断言，避免删除用户后 XXL 会话 marker 残留。
  - 同步保留 HTTP/RunEvent、数据库、部署、安全、模块与测试文档；未修改真实 `.env.local`，也未手改 generated SDK 或 XXL 上游源码。
- How:
  - 以 `git range-diff` 确认原 9 个提交与重写后的 9 个提交一一对应；全局检查无冲突标记，推送前再次 fetch 后确认远端未前进且当前历史可普通 fast-forward。
  - 后端 19 模块跳过测试 clean package、XXL/真实 MySQL 31 项、夜间与 Run 96 项、内部路由 API 13 项、持久化 23 项、应用配置 14 项通过；前端 lint/typecheck、XXL 8 项、排除已记录 `DirectoryRows` 基线文件后的 1440 项（1 skipped）及生产构建通过；企业 Nginx、单后台和开发脚本校验通过。
- Result:
  - 远端与本地功能已在同一线性 `main` 历史中保留，无需 force push；已知 `DirectoryRows` 角色断言基线仍未扩大范围修改，XXL-JOB 3.4.2 vendored 上游源码/资源自带的空白格式继续原样保留。
  - 工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除始终保持未暂存，不纳入提交或推送。

### 2026-07-21 - 修复 Git 推送身份与提交进度终态

- Why:
  - 企业 SCM 拒绝平台生成的 `统一认证号@testagent.local` committer，导致应用 Agent 已在个人 worktree 本地提交、但投影到 feature 分支后 push 失败；前端随后按 clean worktree 刷新，文件从 Diff 消失且失败步骤仍显示 RUNNING。应用 workspace 仅本地提交成功时也因步骤终态只看 step 序号而持续转圈。
- What:
  - 平台 Git 单次提交身份改为 `统一认证号@mails.icbc`，匹配企业 SCM 已登记邮箱；继续只注入当前 Git 命令，不修改仓库或服务器全局配置。
  - 应用 Agent 在本地提交成功后先保留文件白名单和提交前 patch；远端发布失败时以“待推送”跨 5 秒 Diff 轮询保留，点击文件仍可查看差异，重新推送只重放 publish，不重复本地 commit。HTTP 失败与仅本地提交成功都会把当前进度步骤收敛为 FAILED/SUCCEEDED，不再残留 RUNNING。
  - 同步 common/workspace-management、HTTP API、agent-web README/PACKAGE，并新增真实 Git 身份断言和进度/失败重试组件回归。
- How:
  - 复用现有个人 worktree `commit -> publish` 两阶段协议、Agent 配置 operation progress 和定时 Diff 查询；没有新增恢复接口、第二套 Git 状态或裸 Git 推送路径。发布成功后清除待推送快照，同一路径产生新工作树改动或切换工作区时使旧快照失效。
- Result:
  - GitChangesPanel 40 项、common 真实 Git 9 项、workspace-management 聚焦 98 项通过；前端 lint、typecheck、agent-web 生产构建与后端 18 模块跳过测试打包通过，`git diff --check` 通过。
  - 前端全量 Vitest 为 1450 passed / 1 skipped / 1 failed；唯一失败是既有 `DirectoryRows` 测试用 `button` 查询实际 `radio` 角色的“上传”，单文件复跑稳定失败且相关文件未修改，本次未扩大范围处理。
  - 使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 与登录 CORS 正常、manager WebSocket 已连接。本次只改变 Git committer 邮箱规则和前端失败恢复状态，不新增或变更 HTTP/RunEvent/数据库/SQL/权限/generated SDK/环境配置。
- Pitfalls:
  - 修复部署前已经失败的操作没有当前页面内存中的待推送快照，但个人 HEAD 中的本地提交仍在；应使用原 `personalWorkspaceId` 和原文件白名单直接调用平台 `POST /personal-workspaces/{id}/publish` 恢复，不能再次调用 commit，也不能手工 `git push` 绕过版本目标、广播与 rollout。

### 2026-07-21 - 修复夜间补偿服务启动装配失败

- Why:
  - 夜间执行迁移后，`NightExecutionReconcileService` 同时保留生产构造器和包级测试构造器，却未明确 Spring 注入入口，fat JAR 启动时报 `No default constructor found`。
- What:
  - 为五参数生产构造器增加 `@Autowired`，保持用于稳定 attemptId 测试的六参数构造器为包级可见，不改补偿业务逻辑。
  - 新增真实 `AnnotationConfigApplicationContext` 回归，注册全部依赖后验证 Spring 能选择生产构造器并创建 Bean。
- How:
  - TDD 先确认新增测试稳定复现相同 `BeanCreationException`，再用同目录 `NightExecutionDispatchService` 已采用的多构造器装配模式完成最小修复。
- Result:
  - `NightExecutionReconcileServiceTest` 6 项和全部 `NightExecution*Test` 28 项通过；本地重启完成后后端 readiness、XXL Admin readiness 均为 `UP`，前端返回 200，日志出现新的 `Started TestAgentApplication`，XXL V4 migration、Admin 与 executor 正常启动且未再出现本次构造器异常。
  - 重启流程完成后端 20 模块 clean package 和前端生产构建；不涉及 API、事件、数据库、配置、安全或兼容性文档变更。工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除继续保持未暂存。

### 2026-07-21 - 夜间执行迁移至 XXL-JOB

- Why:
  - 公司夜间算力任务需要废弃应用内 `USER_PLAN` 扫描，统一由 XXL-JOB 每 15 分钟分发，并在 HTTP 超时、响应丢失、Java 崩溃和补偿重试下保证同一夜间任务只创建一个普通 Run。
- What:
  - 新增 XXL MySQL V4 注册 `opencode-runtime.night-execution-dispatch`，按到期时间最多扫描 500 条 `SCHEDULED`，固定服务器分组、每批 50、最多并发 8 台 Java；保留 5 分钟 `night-execution-reconcile`。
  - 新增精确内部接口 `/api/internal/platform/opencode-runtime/night-execution/internal-dispatch`，只传 `linuxServerId/taskIds`，复用 `BackendJavaRouteResolver`、`BackendHttpForwarder` 和普通 `startScheduledRun`；接口只等待 Run 受理，不等待执行终态。
  - 夜间任务增加 attempt、精确 backend owner、租约和版本字段；Run 锚点增加 Scheduled dispatch attempt/租约/受理标记。认领、续租、回退、失败和完成均以 `taskId + DISPATCHING + attemptId` fencing，Run 受理后夜间状态保持 `DISPATCHED`，实际结果继续由既有会话、Run 与 RunEvent SSE 展示。
  - 增加每分钟 in-flight 续租、本机 owner watchdog 和跨 Java 补偿；恢复 legacy Scheduled Run 前通过稳定消息 ID 探测远端受理状态，`ACCEPTED` 仅补标记、`NOT_ACCEPTED` 才提交、`UNKNOWN` 不重投，且通用 stale Run 扫描排除尚未确认交接的夜间锚点。
  - 删除旧 `ScheduledTaskRunner`、`ScheduledUserPlanService`、affinity、管理/诊断和启动配置；夜间创建、改期、取消不再维护 `USER_PLAN`。保留 XXL 公共 handler/context/result、Redis 全局锁、历史清理以及可空历史 `scheduled_task_run_id`。
  - 新增 PostgreSQL migration `V20260721134000` 和 MyBatis XML SQL；同步根/后端/模块 README、PACKAGE、HTTP API、RunEvent、数据库、部署、XXL 架构、安全和测试文档。公共夜间 API、前端待执行 Tab、DTO 与 SSE 保持兼容，`NIGHT_EXECUTION_SLOT_CAPACITY` 仍为通用参数默认 20。
- How:
  - 内部路径仅精确豁免普通用户鉴权，再由 Controller 对 `XXL-JOB-ACCESS-TOKEN` 做常量时间校验；跨服务器不传 prompt、附件或用户敏感内容，目标 Java 从共享数据库读取完整输入。
  - Run 接受锚点以稳定 `sessionId + clientRequestId` 和 Redis claim 幂等；分发采用至少一次传输，业务语义由数据库唯一锚点、attempt 租约与远端受理探测收敛为只创建一个 Run。
  - PostgreSQL 全部新增关系型业务 SQL 落在 MyBatis XML；没有修改 generated SDK 或真实 `.env.local`。工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除继续保持未暂存，不纳入提交。
- Result:
  - scheduler/runtime/XXL/API/persistence/app 组合定向套件通过（对应模块分别 2/97/3/16/20/14 项）；真实 PostgreSQL Docker 环境成功执行完整 Flyway 链并通过持久化集成测试；后端 20 模块 `clean package -DskipTests` 通过，`git diff --check` 通过。
  - 扩大到 H2 全量 `clean verify` 仍被已发布且未修改的 `V20260717173000__create_public_agent_config_rollouts.sql` 中 PostgreSQL `TIMESTAMPTZ`/局部表达式索引阻断（167 tests、0 failures、67 setup errors、17 skipped）；同一完整 migration 链已在真实 PostgreSQL 通过，未改写历史迁移以免破坏 checksum。
  - 本机未配置真实多 Java/XXL 网络故障环境，未执行三服务物理端到端；HTTP 超时、响应丢失、崩溃、租约与补偿边界由单元及持久化测试覆盖。远端消息长期不可判定时会安全延迟恢复而不重复发送，这是当前剩余的可用性权衡，无任务内未完成编码项。

### 2026-07-21 - 优化 XXL-JOB 定时任务管理页面头部样式

- Why:
  - 优化定时任务管理页面头部的高度、标题文案和刷新按钮布局，使其更加紧凑、直观且具备更好的视觉效果。
- What:
  - 去除定时任务管理页头部左上角的 kicker 文本 `SCHEDULER / XXL-JOB`，使标题仅保留 `定时任务管理`。
  - 将头部工具栏的 `min-height` 从 `58px` 减小至 `44px`，并收紧 padding 为 `6px 14px 6px 16px`。
  - 将“重新加载”按钮改为仅显示旋转刷新图标（去除文字，增加 `aria-label="重新加载"` 保证无障碍访问与测试兼容），并将按钮尺寸固定为 `32px * 32px` 的正方形。
- How:
  - 仅修改 `ScheduledTaskManagementPanel.vue`，并在 `xxl-job-management-panel.test.ts` 中继续使用带有 `aria-label` 的角色匹配，无需修改测试文件。
  - 执行 `corepack pnpm test xxl-job`、`corepack pnpm lint` 和 `corepack pnpm typecheck` 确认代码、类型定义和测试全部通过。
- Result:
  - 头部高度明显减小，左上角文案仅显示“定时任务管理”，“重新加载”为单图标按钮，交互和无障碍特征正常。

### 2026-07-21 - 修复 XXL-JOB 嵌入页样式并改为横向导航

- Why:
  - 根 `.gitignore` 的通用 `dist/` 规则误忽略 XXL-JOB 3.4.2 AdminLTE 发布资源，真实 Admin 请求核心 CSS/JS 返回 404，嵌入页因此退化为项目符号导航；平台还需要在不修改上游模板和 Java 源码的前提下提供紧凑横向导航。
- What:
  - 为上游 AdminLTE `dist` 增加精确例外并恢复 3.4.2 原版 `AdminLTE.min.css`、`_all-skins.min.css`、`adminlte.min.js`；integration 提供仅在 `test-agent-xxl-embedded` 根 class 下生效的嵌入态样式。
  - 前端 iframe 每次加载后幂等识别并装饰 XXL shell：六个菜单单行横排，窄屏横向滚动，当前账号改为只读文本；SSO 中转页、错误页和不可访问文档保持无操作，直接访问 Admin 仍保留完整原生布局。
  - 同步 upstream/integration、frontend/agent-web README、PACKAGE、XXL 架构与测试文档；HTTP API、RunEvent、数据库、权限和 SSO 协议不变。
- How:
  - TDD 先以真实 Admin 静态资源 404 和前端缺失装饰器复现失败，再补最小实现；三份上游资源的 SHA-256 与 XXL-JOB 3.4.2 官方文件一致，真实 Admin 与 Vite 代理均验证正确状态码、MIME 和非空内容。
  - integration 全量 36 项、应用打包、前端 lint/typecheck、87 个文件 1434 passed / 1 skipped、生产 build 均通过；无参数重启使用默认 `test` profile，8080/18080 readiness 为 200、9999 正常监听。
- Result:
  - 真实浏览器确认六个菜单同一水平线、选中态与原生页签切换正常、账号无下拉、窄屏可横向滚动；平台重新加载会重新签票并恢复装饰，票据未进入顶层 URL，控制台无 AdminLTE 404。直接访问 Admin 不注入平台样式。
  - 后端全量复跑仍仅在既有 persistence H2 基线被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 阻断；另一次运行态调度测试的并发时序抖动独立连续复跑 5 次通过，未改无关代码。
  - 未修改 `.env.local`、XXL 上游模板/Java、API、事件、数据库或安全协议；工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除保持未暂存，不纳入本次提交。

### 2026-07-21 - 自动派生 XXL-JOB 多后端节点地址

- Why:
  - executor 依赖静态 Admin 列表和显式注册地址时，新增 Linux 节点会迫使旧 Java 同步修改配置并重启；当前部署约束为每台 Linux 仅一个 Java，Admin 与同 JVM executor 固定配对，可直接复用平台已具备的 advertised host 解析。
- What:
  - integration 模块新增内部端点解析器：本机 Admin 固定派生为 loopback 地址，executor 注册地址从 `BackendInstanceIdentity.listenUrl()` 的 host 与 executor 端口生成；非法监听地址拒绝启动且错误不回显原始 URL。
  - executor 生命周期只探测本机 Admin readiness，恢复后只启动一次；删除 `adminAddresses`、`address`、`ip` 三个 executor 配置字段及对应三个环境变量，不保留预发布兼容入口。所有 Admin 继续共享 XXL MySQL，调度与注册地址均不引入 Linux 服务器亲和。
  - 同步本地示例、企业单/多后台模板、后端与 integration README，以及架构、部署、安全和测试文档；按用户明确接受的风险把现有本地 access token 默认值纳入基础配置，生产模板仍强制要求通过外部环境变量覆盖，文档与日志不重复明文。
- How:
  - TDD 先以缺失端点解析器的编译失败固化 IPv4、内部 DNS、context path 规整、非法地址脱敏、Admin 恢复和 Spring 实际装配预期，再完成实现；`mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am test` 通过，integration 36 项全过，真实 MySQL/Admin 测试验证第三节点加入时前两个注册地址无需重新注册仍被共享保留。
  - `mvn -f backend/pom.xml -pl test-agent-app -am -DskipTests package`、Compose 配置校验和无参数 `sh restart-dev-services.sh` 均通过；重启脚本使用默认 `test` profile，平台与 Admin readiness 均为 HTTP 200，同一 Java PID 监听 8080、18080、9999。
- Result:
  - 本地 MySQL 注册地址等于平台解析 host 加 executor 端口，当前启动区间无首次注册 `Connection refused` 或 `registry error`；新增 Linux 只需启动新 Java 并把普通 backend 与 XXL Admin 加入中央 Nginx upstream 后 reload，无需修改或重启旧 Java。
  - 后端全量 `mvn test` 仍仅在既有 persistence H2 基线被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 阻断（165 tests、76 errors、17 skipped），本次未扩大范围修改。
  - 未修改 `.env.local`、XXL 上游源码、HTTP API、RunEvent 或数据库结构；地址配置的破坏性删除为预发布阶段的明确收口。工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除保持未暂存，不纳入本次提交。

### 2026-07-21 - 消除 XXL executor 启动注册竞态

- Why:
  - 同 JVM 中 executor 的 `SmartInitializingSingleton` 回调早于异步 Admin 子上下文完成监听，首次注册会立即请求尚未启动的 `127.0.0.1:18080`，产生一次 `Connection refused`；XXL 上游约 30 秒后可自愈，但启动日志存在可避免的错误。
- What:
  - integration 模块用最小上游扩展覆盖 executor 自动初始化入口，新增独立 `SmartLifecycle` daemon 协调器；以 250 毫秒～5 秒退避探测配置列表中各 Admin 的 `/actuator/health/readiness`，任意一个返回 HTTP 200 后才启动 9999 和注册线程，同一进程最多启动一次。
  - 全部 Admin 不可用时平台 WebFlux/8080 和主 readiness 继续启动，executor 端口保持关闭并等待恢复；未修改 XXL 上游源码、现有环境变量、`.env.test`、API、事件、数据库或安全协议。
  - 同步后端/集成模块 README、模块图、XXL 架构、部署和测试文档；新增多 Admin readiness、非法地址/重定向、延迟/幂等启动、关闭和真实 Spring 装配回归。
- How:
  - TDD 先以缺失 readiness probe、延迟 executor 和生命周期协调器的编译失败固化预期，再完成实现；`mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am test` 通过，integration 31 项全过（含 MySQL 8.4、真实 Admin Tomcat/readiness）。
  - 无参数执行 `sh restart-dev-services.sh`，默认 `test` profile；日志顺序为平台 Netty 8080 → Admin Tomcat 18080/readiness → executor 9999/注册线程，最新启动区间无 `registry error`、`Connection refused` 或构造器错误，MySQL 注册行持续更新。
- Result:
  - 8080、18080、9999 与 MySQL 13306 均监听，平台/Admin readiness 均为 HTTP 200；executor 注册地址为 `http://127.0.0.1:9999`，不含 Linux 亲和信息。
  - 后端跳过测试 clean package 和前端生产构建由重启脚本通过。后端全量测试仍仅在既有 persistence H2 基线被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 阻断（165 tests、76 errors、17 skipped），XXL 模块已先通过。
  - 工作树原有 `application.yml` 本地配置修改及 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除保持未暂存、未纳入本次提交；本次不拉取、不 rebase、不推送远端。

### 2026-07-21 - 修复 XXL-JOB 后端启动失败

- Why:
  - XXL 上游依赖 JAR 根目录的 `application.properties` 被平台主 Spring Boot 上下文自动加载，可能以 MySQL/Hikari 通用配置污染平台 PostgreSQL/Druid；隔离后，后端又依次暴露两个多构造器组件未明确注入构造器，以及 Admin 子上下文继承平台 Redis readiness 成员而无法启动的问题。
- What:
  - 构建时把未修改的上游 `application.properties` 重定位到 `META-INF/xxl-job-admin-upstream/`，由 Admin launcher 显式低优先级加载；平台运行配置继续高优先级覆盖，上游 Freemarker、调度超时等默认项仍生效。
  - 为 `RedisXxlJobSsoTicketService` 与 `XxlJobScheduledTaskAdapter` 的公开生产构造器增加 `@Autowired`，保留包级测试构造器；新增真实 Spring 上下文装配回归，防止再次退回无参实例化。
  - Admin 子上下文把 readiness 固定为仅检查独立 MySQL `db`，不继承主应用 `readinessState,db,redis`；同步上游模块、集成模块、架构与测试文档。
- How:
  - TDD 分别复现两个组件的 `No default constructor found`，以及 Admin 子上下文的 `Health contributor 'redis' ... does not exist`，完成最小修复后定向复跑转绿。
  - `test-agent-xxl-job-integration` Reactor 全量通过，集成模块 25 项测试无失败；`test-agent-app` 跳过测试打包成功。无参数执行 `sh restart-dev-services.sh`，确认默认读取 `.env.test` 并使用 `test` profile。
- Result:
  - 同一 Java PID 已监听平台 Netty `8080`、XXL Admin Tomcat `18080` 和 executor `9999`；平台与 Admin readiness 均为 200。日志确认平台连接 PostgreSQL `15432`、XXL 连接 MySQL `13306`，未再出现本次启动的无参构造器、Redis health group 或 MySQL `3306` 误连；MySQL 中为 6 个任务、1 个 `test-agent-backend` 组，executor 注册时间持续刷新且地址不含 Linux 亲和信息。
  - 后端全量 `mvn test` 仍在未修改的 persistence 基线被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 与 H2 不兼容阻断（165 tests、76 errors、17 skipped）；本次按既定范围未修改该历史 migration。
  - 未修改 `.env.test`/`.env.local`、HTTP API、RunEvent、数据库结构、任务数据或安全协议；仅收紧 Spring 配置作用域和组件装配，兼容既有 XXL 行为。

### 2026-07-20 - 修复 XXL Admin 初始健康状态空时间

- Why:
  - 合入 main 前重新执行完整 XXL integration 测试时，`XxlJobAdminHealthIndicatorTest` 暴露 Admin 首次启动尝试前 `checkedAt=null` 被传入 Spring Boot 4 `Health.Builder`，导致健康查询抛出 `IllegalArgumentException`。
- What:
  - XXL Admin 为 DOWN 且尚未完成首次检查时省略 `checkedAt` detail；保留初始安全原因、后续检查时间、独立 DOWN/UP 状态以及不参与平台 readiness 的既有语义。
  - 回归测试显式要求初始健康查询不抛异常、返回 DOWN、保留“尚未启动”原因且不包含空 `checkedAt`。
- How:
  - TDD 先将运行时异常收敛为明确断言失败，再做空值条件化的最小修复；定向测试经历 RED→GREEN。
  - 重新执行 `mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am -q clean test`，退出码 0；前端 `corepack pnpm test` 为 87 个文件、1430 passed / 1 skipped。
- Result:
  - 初始 Admin health 可稳定返回 DOWN，不再因尚无检查时间抛异常；不涉及 API、事件、数据库、配置、权限或文档契约变化。

### 2026-07-20 - 将平台周期任务迁移至 XXL-JOB 3.4.2

- Why:
  - 平台周期任务需要统一改由 XXL-JOB 管理和可视化，同时保留 `USER_PLAN`、`executionAffinity` 及夜间一次性计划的既有调度语义；XXL executor 必须随所有 Java 进程注册且不绑定稳定 Linux 服务器。
  - XXL MySQL 需与平台 PostgreSQL 隔离，平台超级管理员通过同源 iframe 免登录进入 Admin，并在平台会话失效时同步失效 XXL 会话。
- What:
  - 新增未做业务改动的 `test-agent-xxl-job-admin-upstream`（固定 XXL-JOB 3.4.2、上游 commit、GPL-3.0 LICENSE 与升级说明）和平台扩展 `test-agent-xxl-job-integration`；每个平台 Java 进程保持 WebFlux 主上下文，同时运行独立 Servlet Admin 子上下文及 executor，Admin/MySQL 故障只上报独立 health DOWN 并指数退避重试。
  - 新增仅 `SUPER_ADMIN` 可申请的一次性 Redis SSO ticket、SHA-256 session marker、按平台用户 JIT upsert 的 XXL 账号与自定义 `LoginStore`；禁用原生登录/改密/用户写操作，iframe 使用隐藏表单 POST 和显式 ready `postMessage` 握手，设置 SAMEORIGIN/CSP、HttpOnly/Secure/SameSite cookie 并扩充日志脱敏。
  - MySQL Flyway 使用顶层独立 `classpath:xxl-job/db/migration`：V1 上游基础表无示例管理员，V2 增加平台用户/任务键，V3 初始化自动注册组 `test-agent-backend` 与六条周期任务；统一 handler 支持 `GLOBAL_MUTEX`、`ALLOW_OVERLAP`、锁续租、停止中断和错误收敛，参数中不含 `linuxServerId`/亲和字段。
  - 旧 runner 仅同步、扫描和执行 `USER_PLAN`，移除本地终态投影 retry ticker；旧管理 API 统一返回 `410 API_GONE`。前端管理页改为同源 XXL iframe；本地 Compose 增加 MySQL 8.4/13306，Vite/Nginx、启动脚本、企业离线包配置及 API/数据库/部署/安全/架构/测试文档同步更新，未修改 `.env.local`。
- How:
  - 以 TDD 覆盖 SSO 权限、一次消费/过期、marker 失效、JIT 幂等与改名、原生入口封禁、handler 参数/互斥/重叠/续租/停止/脱敏，以及前端签票、刷新重签、故障与登出；使用 MySQL 8.4、Redis 7 Testcontainers 验证全新/重复/并发 Flyway、六条任务、executor 组和独立 Admin HTTP 行为。
  - `mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am clean test` 通过，integration 22 项全过；`mvn -f backend/pom.xml -pl test-agent-app -am clean -DskipTests package` 通过并生成约 102 MB 单应用 JAR，检查确认包含独立 migration 与 SSO 模板。
  - 前端依次执行 lint、typecheck、test、build：87 个测试文件、1430 passed / 1 skipped，生产构建仅保留既有大 chunk 提示；Compose config、内部 Nginx 单/多后端、单机配置和开发脚本校验均通过。
  - 平台自研文件执行 `git diff --cached --check` 无错误；原样保存的 XXL 上游目录保留 3.4.2 自带尾随空格，因此未对该 vendored 目录做格式清洗，避免破坏与上游源码的直接比对。
- Result:
  - 周期任务已迁移到 XXL-JOB 管理面与所有 Java executor；执行器使用自动注册和轮询，不含 Linux 亲和。夜间一次性 `USER_PLAN` 继续按原 `executionAffinity` 执行，滚动发布期间两入口复用同一 Redis 锁键。
  - 后端全量 `mvn test` 仍在未改动的 persistence 基线被已发布 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 与 H2 不兼容阻断（165 tests、76 errors、17 skipped），已在干净 main 复现；Playwright 全量也仍被既有 `.agent-root-row` 隐藏基线阻断，XXL 相关 Vitest 均通过。
  - 未新增 RunEvent/SSE；涉及内部 HTTP API、独立 MySQL schema、Redis 会话 marker、GPL-3.0 上游交付和安全响应头。真实生产双 Java/共享 MySQL/网络与人工页面触发尚需按验收文档执行，本次未推送远端。
### 2026-07-19 - 优化会话列表样式与布局

- Why:
  - 用户的会话列表单项垂直高度偏大，限制了一屏内能展示的会话数量；且顶部的“会话列表”标题栏占用了空间，用户希望移除该标题栏，并将关闭按钮移至选项卡右侧以使布局更紧凑，同时希望抽屉宽度更宽。
- What:
  - 修改 `FigmaChatPanel.vue` 中会话卡片的 CSS 样式，重构了历史会话抽屉的 DOM 结构与样式，并增加了抽屉的最大宽度限制：
    - 移除了顶部的 `<header class="figma-chat-drawer-header">`。
    - 将带有 `aria-label="关闭会话列表抽屉"` 属性的关闭按钮移入选项卡面板 `.figma-chat-history-tabs` 中，并为其添加新的定位样式使其靠右对齐。
    - 微调了卡片 padding、margin 以及列表 gap 属性，进一步压缩垂直空隙。
    - 修改 `session-list-drawer.ts`，将抽屉最大宽度 `DRAWER_MAX_WIDTH` 从 360 像素提升至 400 像素，并同步更新了 `session-list-drawer.test.ts` 中对应的断言。
- How:
  - 将 `.figma-chat-history-list` 的 `gap` 从 `8px` 调整为 `6px`。
  - 将 `.figma-chat-history-card` 的 `padding` 从 `12px` 调整为 `8px 12px`，其内 icon 与 content 的 `gap` 从 `12px` 调整为 `8px`。
  - 将 `.figma-chat-history-card-title-row` 和 `.figma-chat-history-card-context` 的 `margin-bottom` 从 `4px` 调整为 `2px`。
  - 移除了历史抽屉标题，在 `figma-chat-history-tabs` 中追加 close 按钮，利用 `margin-left: auto` 和 `align-self: center` 样式使其在选项卡右侧居中对齐，并保持 hover 背景切换效果。
  - 修改 `session-list-drawer.ts` 中 `const DRAWER_MAX_WIDTH = 400;`，并调整对应单测中的 `width` 与计算出的 `left` 坐标值。
- Result:
  - 成功缩减了每个历史会话列表项的高度，移除了无用的标题栏，抽屉宽度调整为 400px，整体界面布局更加紧凑实用。
  - 运行单元测试 `vitest run FigmaChatPanel` (132 tests) 和 `vitest run session-list-drawer` (4 tests) 全部通过。
  - 前端 `typecheck` 验证无类型报错。
- Verification:
  - 在 `frontend` 目录下执行 `corepack pnpm test FigmaChatPanel` 和 `corepack pnpm --filter @test-agent/agent-web typecheck`。

### 2026-07-19 - 修复内存通用参数早于 Flyway 加载

- Why:
  - 项目通过最高优先级 `ApplicationRunner` 执行运行态 Flyway，而内存通用参数注册表原先在 Spring 单例装配完成时查库；新环境尚未执行参数种子 migration，因而以“数据库值缺失”阻止启动。
- What:
  - 移除 `CommonParameterMemoryRegistry` 的 `SmartInitializingSingleton` 启动回调，新增紧随 `DatabaseMigrationRunner` 的 `CommonParameterMemoryStartupRunner`，在 migration 完成后、scheduler 等默认业务 Runner 前继续执行严格加载。
  - 同步 app、configuration-management 与后端部署文档中的启动生命周期说明；严格失败语义、参数值、API、事件和数据库结构均不变。
- How:
  - TDD 先用缺失 runner 的编译失败固化预期，再验证新 runner 调用注册表、顺序为 `HIGHEST_PRECEDENCE + 1`，并确认注册表不再实现单例初始化回调。
- Result:
  - 聚焦回归测试和后端跳过测试打包通过；按 `.env.test` 重启后，日志确认 Flyway 成功应用 `V20260719210000`，随后加载 `NIGHT_EXECUTION_SLOT_CAPACITY/all`，readiness 为 `UP`。
  - 测试库参数为 `20/all/editable=true`。不涉及新增 API、RunEvent、数据库结构、环境配置、安全或兼容性变化；仅修正已有 migration 与内存加载的启动先后顺序。

### 2026-07-19 - 优化对话页会话列表交互

- Why:
  - 主对话顶部“对话 / 待执行任务”页签占用内容空间，历史会话选择后列表自动关闭，连续查找会话效率较低；原始输出页面缓存也需要明确的内存上限。
- What:
  - 主区域固定展示当前会话；顶部“消息列表”统一改为“会话列表”，会话与待执行任务迁入 Teleport 非模态抽屉。桌面抽屉贴在右侧对话栏左侧，窄屏退化为视口内覆盖，并随栏位、滚动与窗口尺寸更新。
  - 会话/任务选择继续复用既有回调且不关闭抽屉，当前会话增加选中态；抽屉可由再次点击“会话列表”入口、关闭按钮、Esc 或右侧栏收起关闭，每次重新打开默认进入会话页签，并补齐入口 `aria-expanded` 与 WAI-ARIA Tab 键盘交互。
  - 每个 Session 的原始输出内存缓存改为有界追加，只保留最新 2000 条；继续沿用既有 Session 隔离、脱敏、正文截断和刷新清空规则。
  - 同步 frontend、agent-web、agent-chat、模块地图和内置对话手册；更新 mock/real E2E 选择器与关键连续切换场景。
- How:
  - 新增纯函数 `resolveSessionListDrawerPlacement` 统一桌面/窄屏定位；`FigmaChatPanel` 仅增加内部可选 `panelVisible` 属性，现有 `select-session`、夜间任务事件和会话加载流程不变。
  - TDD 覆盖主页签移除、抽屉双页签/入口二次点击收起/关闭边界/当前项/键盘操作/窄屏定位，以及 2001 条原始输出保留最后 2000 条和顺序不变。
- Result:
  - `corepack pnpm lint`、`typecheck`、`test`、`build` 全部通过；Vitest 86 个文件共 1420 passed / 1 skipped。关键 Playwright 场景在 Chromium/mobile 4/4 通过，另两个抽屉保持开启后的精确选择器用例 2/2 通过。
  - 完整 mock E2E 已执行，但在本次未改动的 Agent/文件树基线用例中持续失败（`.agent-root-row` 隐藏、mock Agent 目录缺失）；手动停止时为 5 passed、15 failed、2 interrupted、178 did not run，退出码 130。本任务会话列表关键路径不受影响。
  - 不涉及 HTTP/SSE API、DTO、RunEvent、数据库、后端会话逻辑、权限、安全、环境配置、generated SDK 或新依赖；构建只保留既有大 chunk 提示。

### 2026-07-19 - 支持查询与手工刷新 Java 内存通用参数

- Why:
  - 多 Java 部署中，超级管理员缺少核对各 JVM 实际加载值及在广播遗漏、运行期读取失败后按数据库值定点恢复的能力；多数通用参数仍应保持按需直读数据库，不能扩大为通用缓存。
- What:
  - 新增显式 `CommonParameterMemoryEntry` 领域 SPI 与本机注册表，按英文名/平台唯一排序，启动严格加载，匹配广播或手工操作时查库刷新；运行期失败保留上一有效值并返回安全状态。夜间容量成为首个注册项，保留正整数校验、动态消费者和默认种子 `20/all/editable=true`，删除旧环境变量/yaml 入口。
  - 公共 Java 路由器增加 `backendProcessId` 精确选择；新增四个仅 `SUPER_ADMIN` 可用的查询/刷新接口，集群操作最多 500 个进程、并发 8、单进程 10 秒，同服务器多个 Java 独立返回，单进程离线统一 503，集群部分失败仍返回逐进程结果。
  - 通用参数页增加按需加载的 JVM 内存值抽屉与全部/单进程刷新；同步共享类型、backend-api、HTTP API、数据库/部署/安全/后端规范、模块图和前后端 README/PACKAGE。`event-stream.md` 经检查无需修改。
- How:
  - 跨 Java 请求复用 `BackendJavaRouteResolver` 与 `BackendHttpForwarder`，保留 routed header 防二次转发；手工刷新直接读取目标 JVM 的数据库参数，不写修改历史、不重复广播。响应与结构化日志不记录底层异常，`sourceValue`/`memoryValue` 纳入 JSON 日志脱敏。
  - TDD 覆盖注册键唯一性、排序、启动/运行失败语义、事件匹配、夜间容量缺失/空白/非正整数/溢出、精确路由、四个接口、超时/离线/部分失败、远端空响应以及前端懒加载、展示和刷新提示。
- Result:
  - API、配置管理和运行时相关 Maven 模块全量测试通过；聚焦回归通过，后端 18 模块 `mvn clean package -DskipTests` 成功。前端聚焦 Vitest 84 项及 shared-types、backend-api、agent-web typecheck 通过。
  - 扩大到 persistence 全量仍被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 与 H2 不兼容阻断（76 errors）；全量前端 test/build 又被同工作树并行、未纳入本提交的 Figma/AgentWorkbench 改动失败阻断，任务相关聚焦套件保持通过。
  - 涉及新增内部 HTTP API、一个生产必需通用参数种子 migration、最多 500×单进程响应的有界并发聚合和超级管理员诊断面；不新增 RunEvent/SSE、Redis 参数快照、业务表、generated SDK 或真实环境文件。仍建议在真实多 Java 环境人工验证同服务器多进程、离线恢复和广播后收敛。
- Verification:
  - `mvn -q -pl test-agent-api,test-agent-configuration-management,test-agent-opencode-runtime -am test`
  - 聚焦 `CommonParameterMemory*`、`NightExecution*`、路由、migration 与脱敏测试；`mvn clean package -DskipTests`
  - `corepack pnpm exec vitest run apps/agent-web/tests/general-param-management-panel.test.ts packages/backend-api/tests/backend-api.test.ts`
  - shared-types、backend-api、agent-web typecheck，`git diff --check`、冲突标记/旧配置引用扫描和全部 session log 近期条目回顾。
- Next:
  - 并行前端改动和既有 H2 migration 基线修复后重跑全量前端与 persistence；在真实多 Java 部署完成查询、全部/定点刷新及离线 503 人工验收。

### 2026-07-19 - 优化引用资产库多服务器同步耗时

- Why:
  - 引用资产 generation 建档后只发布 Redis 广播，而发布者会忽略自身事件；发起 Java 的本机副本因此常等到默认 60 秒补偿扫描才开始。瞬时 Git 失败的 5 秒退避也可能被同一扫描周期放大。
- What:
  - workspace-management 新增按 `repositoryId + generation` 去重的本机有界异步调度器，默认两个 worker、最多保留 256 个 key，支持立即和按 `nextRetryAt` 调度；队列饱和时拒绝 caller-runs，并由既有补偿扫描恢复。
  - 初始化、同步、分支切换和指针核验在广播其它 Java 的同时立即提交本机任务；Redis 消费者与 60 秒补偿器只负责排队，不再在线程内执行阻塞 Git。瞬时失败写入 `RETRY_WAIT` 后直接安排退避到期重试。
  - 已有可信、干净、同源仓库在实际分支和 HEAD 已等于固定目标时走无操作快速路径，跳过 fetch、提交解析、祖先校验和 reset；增加排队、租约认领、Git 和总体任务耗时的脱敏结构化日志。
  - 同步 workspace-management README/PACKAGE、模块图、后端部署和内部广播文档；未修改 HTTP API/DTO、广播 payload、RunEvent、数据库/MyBatis、前端、manager、generated SDK 或环境配置。
- How:
  - 保留数据库 generation、租约 token/CAS fencing、本机文件锁和 60 秒补偿扫描作为一致性边界；调度器只改变唤醒与线程承载，不改变脏仓库、origin 冲突、分支分叉或租约丢失的安全阻断规则。
  - TDD 覆盖立即执行、去重、延迟任务被更早唤醒替换、并发上限、容量拒绝、关闭、四类操作本机提交、广播 listener 非阻塞、首次 5 秒定向重试和已对齐 HEAD 无操作路径。
- Result:
  - workspace-management reactor 全量通过：common 87、domain 78、workspace-management 230 项；引用资产 Controller 定向 3 项通过，聚焦调度/服务测试 82 项通过。
  - `test-agent-api` 全量共运行 325 项，其中 324 项通过；唯一失败为并行未提交的通用参数内存化改动新增 `SensitiveDataMaskerTest.mask_commonParameterMemoryValues`，其测试和被测脱敏器均不在本次提交范围，已保留未暂存。
  - 本机没有真实双服务器环境；各节点即时进入 `PROCESSING` 和正常小仓库 3 秒内页面收敛仍需部署环境人工验收。
- Verification:
  - `mvn -pl test-agent-workspace-management -am test`
  - `mvn -pl test-agent-api -am test`（上述任务外单项失败）
  - `mvn -pl test-agent-api -am -Dtest='ReferenceRepositoryControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `git diff --check`、全部 session log 近期条目回顾和精确暂存差异审查。
- Next:
  - 在真实双服务器部署执行 generation 到 `PROCESSING/READY` 的耗时验收；通用参数内存化任务需单独修复其脱敏测试。

### 2026-07-19 - 修复引用文件定位到当前文件失效

- Why:
  - 编辑器页脚“定位到当前文件”和标签双击沿用普通工作区相对路径展开逻辑，但引用 tab 使用 `workspace-reference:` 合成身份，导致折叠的引用祖先目录无法展开和滚动。
- What:
  - 新增纯函数按组合文件树的稳定节点 ID 和“父目录 ID → 子节点”缓存反向恢复祖先链；缺失、不完整或循环链路失败关闭，不按展示路径猜测。
  - `AgentWorkbench` 对引用 tab 使用打开文件时保存的稳定叶子节点 ID，从根到叶展开祖先；普通工作区文件继续使用原相对路径逻辑，两类路径都等待展开完成后再高亮滚动。
  - 同步 agent-web 与 file-explorer README，明确合并引用、非合并引用及同名冲突的精确定位规则。
- How:
  - TDD 先增加合并引用、非合并别名根、同名工作区/引用和异常父链回归并确认 4 项失败，再实现纯函数；随后增加 `AgentWorkbench` 接入断言并确认失败，再完成稳定节点展开。
  - 保持 `WorkbenchFooter`、`FigmaEditorArea` 的事件签名和 `EditorTab` 数据结构不变，没有新增接口或全局定位服务。
- Result:
  - 引用文件可通过页脚按钮或标签双击精确展开到实际引用节点；同名冲突不会误选工作区副本，普通文件定位还消除了未等待异步目录展开就提前滚动的时序问题。
  - 不涉及 HTTP API、RunEvent、数据库、backend、OpenCode manager、generated SDK、依赖、安全或环境配置。
- Verification:
  - 定向 Vitest 3 个文件 31/31 通过；全量 Vitest 85 个文件 1406 passed / 1 skipped。
  - 全仓 `corepack pnpm typecheck`、`corepack pnpm lint`、生产 `corepack pnpm build` 和 `git diff --check` 通过；构建仅保留既有大 chunk 警告。
  - 首次把全量 Vitest 与 typecheck/lint 并行运行时，`opencode-timeline` 一项出现时序失败；该用例定向复跑通过，随后独立全量 Vitest 复跑全部通过。

### 2026-07-19 - Flowchart 快捷图形选中后立即收起菜单

- Why:
  - Flowchart 快捷建连选择备选图形后只发出了建连事件，没有清空当前快捷箭头状态，Teleport 备选菜单会继续遮挡画布。
- What:
  - 快捷图形选中时先清理菜单关闭/箭头悬停定时器并复位活动箭头，再沿用原有 `quickConnect` 数据创建节点和连线；四向快捷箭头、端口与建连规则不变。
  - 同步前端总览和 editor 包 README，明确备选图形选中后菜单立即收起。
- How:
  - TDD 先新增“选择快捷图形后立即关闭备选菜单”组件用例并确认旧实现失败；代码审查进一步覆盖节点/箭头离开、进入 Teleport 菜单再点击的真实路径，确保菜单和已离开节点的四向箭头同步收敛。
- Result:
  - MermaidVisualEditor 组件测试 121/121、前端全量 Vitest 1401 passed / 1 skipped、lint、typecheck、production build 和 `git diff --check` 通过；构建只保留既有大 chunk 警告。
  - 不涉及 API、RunEvent、数据库、性能、安全、依赖或环境配置。

### 2026-07-19 - 修复 scheduler PostgreSQL Map 别名导致的启动失败

- Why:
  - scheduler 与夜间任务仓储从 JDBC 迁移到 MyBatis `resultType="map"` 后使用未引用的驼峰 SQL 别名；PostgreSQL 将 `taskKey` 折叠为 `taskkey`，Java 读取 `row.get("taskKey")` 得到空值，启动同步代码注册任务时触发 `taskKey must not be null`。
- What:
  - `ScheduledTaskMapper.xml` 与 `NightExecutionTaskMapper.xml` 的全部驼峰 Map 别名改为双引号精确别名，覆盖任务、计划、运行记录、夜间任务和容量统计读取。
  - 两组 MyBatis 集成测试改用 H2 `DATABASE_TO_LOWER=true` 模拟 PostgreSQL 标识符折叠规则；同步 persistence README 的测试覆盖说明。
- How:
  - TDD 先在旧 Mapper 上复现 scheduler `taskKey` 和夜间任务 `taskId` NPE（4 个用例均失败），再做最小 XML 修复并复跑相同用例。
  - 未修改 migration、数据库结构、API、RunEvent、环境配置或 generated SDK。
- Result:
  - 两组定向集成测试 4/4 通过；后端 18 模块 `mvn clean package -Dmaven.test.skip=true` 成功。
  - 使用 `.env.test` / `test` profile 单独启动后端，Flyway 62 个 migration 校验成功、Redis 探测成功，`/actuator/health` 返回 `UP`，原始 NPE 未再出现。
  - persistence 全量测试仍被既有 `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（160 tests，76 errors），与本次别名修复无关；三服务脚本还因本机缺少 `go` 未启动 manager/frontend，本次仅验证后端。

### 2026-07-19 - 实施夜间异步执行任务

- Why:
  - 公司白天算力不足，需要用户在现有对话中预先提交北京时间 21:00 至次日 07:00 的一次性任务，并在 15 分钟容量时段内无人值守启动；已有会话待执行期间必须锁定普通对话，执行过程继续复用现有 Session/Run/RunEvent 展示。
- What:
  - scheduler 增加一次性 `USER_PLAN`、Linux 执行亲和、按运行 ID 分布式锁和有界 worker；既有 scheduler JDBC 仓储迁移为 MyBatis XML。夜间任务新增领域模型、Flyway/MyBatis 主表/会话锁/容量占位、创建/查询/改期/取消/失败卡关闭 API，以及 5 分钟恢复、同夜顺延、07:00 最终失败和 30 天清理。
  - 到期投递重新校验 owner、Session、Workspace 和权限，binding 迁移时重建亲和计划，通过公共 `UserOpencodeProcessAssignmentService.initialize` 启动进程，再调用既有 `RunApplicationService` 创建带 `SCHEDULED_TASK` 来源的 Run 和 USER 消息；手工发送、追加消息和归档在会话持锁期间由后端硬拦截。
  - 当前对话发送按钮左侧新增定时图标和 15 分钟时段选择；“对话 / 待执行任务”页签分页收齐全部任务，展示预览、计划时段、创建时间、状态和顺延次数，支持改期/内联确认取消。空白草稿事务内创建新 Session；任务启动后回到既有消息与 Run 展示，来源标签显示北京时间实际启动时间。
  - 创建请求纳入现有 opencode 后端路由并复用 32 MiB 请求体硬上限；容量竞争返回最新时段详情，前端立即重取选择器。同步 HTTP、事件、数据库、部署、安全、架构、前后端 README/PACKAGE 和内置用户手册；未新增 RunEvent 类型，也未修改 generated SDK 或真实环境配置。
- How:
  - 创建、改期、取消和终态迁移使用事务、条件更新、PostgreSQL advisory lock、时段原子占位及 `scheduledTaskRunId` fencing；完整 prompt/parts 只保存在业务表，响应、日志和 scheduler result 仅暴露安全预览，终态清除完整输入。
  - 列表使用 owner/status/slot 索引并由前端按后端最大 200 条逐页收齐；runner 的扫描批量、worker、队列和每时段容量均显式配置。`TEST_AGENT_NIGHT_EXECUTION_SLOT_CAPACITY` 缺失时应用继续启动，但夜间 API 失败关闭为 `NIGHT_EXECUTION_UNAVAILABLE`。
  - 完成前回顾 `.agents/session-log.md`、`.agents/session-log.huangzhenren.md` 和 `.agents/session-log.rkk222.md` 的近期条目，保留已提交的工作区目录移动、Mermaid 与引用资产成果；没有修改已发布的 `V20260717173000`。
- Result:
  - 后端夜间/runtime/scheduler/API/路由定向测试通过，`mvn -q clean package -DskipTests` 18 模块通过；前端全量 Vitest 85 文件为 1393 passed / 1 skipped，lint、typecheck、用户手册和生产 build 通过，桌面/移动夜间 Playwright 4/4 通过，构建仅保留既有大 chunk 提示。
  - 后端全量 `mvn test` 仍只在 persistence 被已发布 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `timestamptz` 与当前 H2 2.4 不兼容阻断（76 errors）；该问题已在前序日志和干净基线确认，生产 PostgreSQL 支持，本次按最小范围没有篡改历史 migration。
  - 涉及新增内部 HTTP API、兼容性 DTO 来源字段、两份 scheduler/夜间 Flyway 增量和运行时配置；不新增 SSE 事件、不改变鉴权角色、不输出敏感输入。生产启用前需配置正整数时段容量，并建议在真实多 Java/夜间窗口验证 binding 迁移、进程自动启动和算力容量。
- Verification:
  - `mvn -q -pl test-agent-opencode-runtime,test-agent-api -am -Dtest='NightExecution*Test,UserOpencodeBackendRoutingWebFilterTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -q -pl test-agent-persistence -am -Dtest='MyBatisNightExecutionTaskRepositoryIntegrationTest,MyBatisScheduledTaskRepositoryIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -q clean package -DskipTests`
  - `corepack pnpm test`、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`
  - `corepack pnpm e2e --grep "night task"`
  - `git diff --check`、全部 session log 近期条目回顾和最终只读差异审查。

### 2026-07-19 - 支持工作区文件和目录拖拽移动

- Why:
  - 工作区文件树原先只能拖动普通文件，目录不能整体移动，且组合引用树中的只读来源缺少完整的拖源/落点阻断和移动后的状态收敛。
- What:
  - 文件树允许可写纯 `WORKSPACE` 文件和目录作为拖源，保留浏览器原生拖影并提供抓取、半透明源行和蓝色合法落点；只读、纯 `REFERENCE`、整棵 `MIXED` 不可拖，纯引用目录、文件行、当前父目录、自身和后代目录吞掉非法落下，带 `workspacePath` 的 `MIXED` 目录仍可接收工作区条目。
  - app 层沿用 `workspace.move` 与反向移动撤销，移动后迁移已打开子文件 Tab、活动/Diff/请求路径和展开路径，按新 `workspacePath` 补齐祖先并逐层认领组合树的新稳定 ID，再刷新 Git Diff；无快照的加载中 Tab 在新刷新代次建立后补读。
  - 后端 `WorkspaceFileService.moveFile` 支持普通文件和非空目录的一次整体移动，拒绝根、符号链接/特殊文件、越界、冲突和目录自身后代。Linux 从 `/` 逐段固定目录句柄后通过 JNA 直接调用内核 `renameat2(RENAME_NOREPLACE)`，macOS 使用 `renameatx_np`，Windows 使用已核对的源条目/目标父目录句柄和 `SetFileInformationByHandle`；目标并发创建不覆盖，校验后路径替换失败关闭。
  - 同步前后端 README、workspace/file-explorer 包说明、前后端规范和文件 WebSocket 协议文档；RPC 名称、请求/响应 DTO 与错误码集合不变。
- How:
  - 按 TDD 覆盖文件/目录拖源和视觉状态、根/目录合法落点、引用与 `MIXED` 边界、非法落点冒泡阻断、拖拽清理、目录移动后的 Tab/展开/撤销收敛，以及非空目录、同路径、根/后代/冲突/符号链接/特殊文件/越界、路径替换竞态和目标并发创建。
  - 项目实际镜像 `eclipse-temurin:21-jre-alpine` 使用 musl 且不导出 `renameat2` 包装函数，因此无需修改镜像，直接调用同一 Linux 内核 syscall；已在该镜像完成普通移动与目标不覆盖 smoke。完成前回顾全部 `.agents/session-log*.md` 近期条目并只暂存本任务文件，保留并行夜间任务等未提交改动。
- Result:
  - 前端定向 Vitest 40 项、目录移动 Playwright Chromium/mobile 2 项、全量 Vitest 1383 passed / 1 skipped、lint、typecheck 和生产 build 通过；构建仅保留既有大 chunk 提示。
  - 后端 workspace 定向 26 项、workspace/API reactor 测试通过，`mvn clean package -DskipTests` 18 模块通过；全量 `mvn test` 仅被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 与当前 H2 不兼容阻断（persistence 76 errors），本次未修改该 migration。
  - 不涉及数据库、RunEvent SSE、generated SDK、跨服务器路由、鉴权模型或环境配置；新增 workspace 模块对既有版本 JNA 5.14.0 的直接编译依赖。macOS 与项目实际 Linux 镜像已运行验证；Windows 实现已编译并通过专项代码审查，但发布前仍建议在 Windows x64/arm64 验证普通/目录移动、目标冲突与 junction 竞态。
- Verification:
  - `corepack pnpm vitest run packages/file-explorer/tests/DirectoryRows.test.ts packages/file-explorer/tests/FileExplorer.test.ts apps/agent-web/tests/workspaceViewState.test.ts`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep 'workspace directory move'`
  - `corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm test`、`corepack pnpm build`
  - `mvn -q -pl test-agent-workspace-management -Dtest=WorkspaceFileServiceTest test`
  - `mvn -pl test-agent-api -am test`、`mvn test`、`mvn clean package -DskipTests`
  - 项目实际 Alpine JRE 镜像 Linux 安全移动 smoke、`git diff --check`、全部 session log 近期条目回顾和最终只读代码审查。
- Next:
  - 在真实 Windows x64/arm64 环境补充原生移动 smoke；既有 H2 migration 兼容问题另行修复，不属于本次拖拽移动范围。

### 2026-07-18 - 新增 Mermaid State Diagram 可视化编辑

- Why:
  - 现有 Mermaid 编辑器只支持 Flowchart 与 Sequence，无法安全编辑 State Diagram 的递归层级、并发区域和伪状态；直接复用 Flow 的扁平模型与重复边规则会破坏 State 语义。
- What:
  - 新增 `stateDiagram` / `stateDiagram-v2` 递归 Scope/Region 领域模型、自研 parser、规范化 serializer、严格校验、B1 紧凑 metadata 和分层 ELK 布局；支持开始/结束、普通状态与多行说明、自循环、标签转换、复合/嵌套状态、Choice、Fork/Join、并发 Region、Note、各层方向和限定直接样式。
  - 新增“概览 + 聚焦”State 画布：根层展示复合状态摘要，双击进入内部，面包屑返回，聚焦层同时展示全部并发 Region；支持元素点击/拖放创建、状态与转换就地编辑、拖线/端点重连、Note 属性和受限颜色编辑。
  - 把通用屏幕坐标拖线控制器抽象为可注入领域连接规则，Flow 保持原规则，State 允许同端点多转换并阻止跨 Scope/Region、伪状态非法方向和基数上限；外部包导出、Markdown 围栏并发保护、官方 Mermaid 双重校验与保存链路不变。
  - 扩展领域、布局、组件、Markdown 和 workbench Playwright 回归；同步前端总 README、editor README/PACKAGE、模块地图以及已批准的设计/执行计划。
- How:
  - 按 TDD 从解析/序列化/校验/metadata 开始，再实现递归布局、概览/聚焦画布和 Markdown 集成；无法安全映射的结构语法降级源码编辑，注释、复杂样式、class/accessibility 指令按 Scope 原样保留。
  - State 应用时先做完整领域校验，再生成规范源码并调用 Mermaid 11.16.0 官方 parser；任一步失败都保留草稿且不覆盖 Markdown。坐标与转换端口继续使用既有紧凑 envelope，损坏/重复 marker 原样保留且不制造第二个 marker。
  - 精确排除共享工作区中并行出现的 backend、scheduler、workspace、AgentWorkbench、file-explorer 等无关改动，只暂存 State 编辑器相关文件和本条记录。
- Result:
  - editor 全量 20 个测试文件、402 项通过；前端全量 Vitest 84 个文件通过（1383 passed / 1 skipped），13 个 workspace 的 lint/typecheck、用户手册和生产 build 通过。目标 workbench Playwright Chromium 1/1 通过，验证 Flowchart、Sequence、State 共用一次 workspace 保存链路。
  - 生产构建确认 `MermaidEditorDialog` 与 `elk.bundled` 仍为独立懒加载资源；只保留既有大 chunk 提示。未新增初始页面依赖，DOMPurify 边界未变。
  - 不涉及后端、HTTP API、RunEvent/广播、数据库、SQL、migration、generated SDK、环境配置、安全权限或外部包导出；Flowchart 与 Sequence 回归全部通过，无未完成编码项。
- Verification:
  - `corepack pnpm exec vitest run packages/editor/tests --reporter=dot`
  - `corepack pnpm test`、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --project=chromium -g "Markdown Mermaid Flowchart、Sequence 和 State"`
  - `git diff --check`、全部 session log 近期条目回顾和共享工作区暂存范围复核。
- Next:
  - 在真实浏览器使用超深嵌套、超长 Note/状态说明和大量并发 Region 做人工可用性验收；不影响本次交付范围。

### 2026-07-18 - 定稿夜间定时执行任务设计

- Why:
  - 白天算力不足，需要让用户在现有对话中提交一次性夜间任务，并在北京时间 21:00 至次日 07:00 的 15 分钟时段内无人值守执行。
- What:
  - 新增夜间任务设计规范，确认定时图标位于发送按钮左侧，取消“执行位置”选择；任务始终绑定当前对话，需要新会话时先使用现有新建对话入口。
  - 待执行任务采用当前对话锁定卡与“待执行任务”页签双重展示；同一 Session 只允许一个待执行任务，但其他会话仍可使用。
  - 明确全局时段硬容量、最空闲且尽量早的推荐、同夜窗口顺延、07:00 最终失败、进程自动启动、来源标识和失败卡关闭规则。
  - 技术设计复用 scheduler `USER_PLAN`、运行记录、Redis 锁和 handler；一次性正文放独立业务表，新增 MyBatis/Flyway、执行亲和和服务端内部 scheduled Run 入口，不使用 Cron 计划 payload 保存正文。
- How:
  - 结合现有 `FigmaChatPanel`/`AgentWorkbench`、Session/Run 来源字段、scheduler runner、用户进程公共启动与多 Java 路由约束，固定交互、API、状态机、数据安全、并发容量和测试验收边界。
  - 设计自检消除了“空 Session 最终失败即归档”与“失败状态可见”的冲突：最终失败先解除锁并保留可关闭卡片，关闭后才按是否为空决定归档。
- Result:
  - 本次仅新增设计文档和本条会话记录，未修改代码、API、RunEvent、数据库、环境配置或 generated SDK，也未触碰工作区内并行开发的已有修改。
  - 设计规划未来新增普通用户 HTTP API、来源 DTO 字段、Flyway/MyBatis 数据模型和 scheduler USER_PLAN 能力；待用户复核规范后再编写实施计划。
- Verification:
  - `git diff --check -- docs/superpowers/specs/2026-07-18-night-execution-task-design.md .agents/session-log.huangzhenren.md`
  - 规范占位符、内部一致性、范围和歧义自检；提交前回顾全部 `.agents/session-log*.md` 近期条目。
- Next:
  - 用户确认书面规范后，按 `superpowers:writing-plans` 生成可执行实施计划。

### 2026-07-18 - 增强 Mermaid SequenceDiagram 结构化编辑能力

- Why:
  - 既有 Sequence 编辑器只有参与者与平铺消息，无法安全表达控制片段、生命周期、激活、Note 和嵌套调用，也不能像 Flowchart 一样在画布、结构和属性之间完成可视化编辑。
- What:
  - 将 Sequence 领域模型升级为递归语句 AST，覆盖 8 类参与者、别名/box、10 种标准箭头、消息、Note、注释、显式与快捷激活、自调用、create/destroy、autonumber、多行文本，以及 `loop`、`alt/else`、`opt`、`par/and`、`critical/option`、`break`、`rect` 任意嵌套。
  - parser 改为 tokenizer + 显式容器栈，serializer 依据源码锚点、父容器和语义指纹最小差异回写；CRLF、缩进、大小写、旧紧凑坐标 metadata 与未修改快捷语法保持不变。半箭头、中央连接、Actor 菜单、标题/无障碍指令、`par_over`、分号串联和未知配置作为局部锁定纯文本无损保留。
  - 新增不可变命令/语义校验与确定性布局，统一处理重命名引用、跨分支移动、端点重绑、级联删除、分组连续性、分支生命周期与激活规则；`create` 保持全图唯一，互斥分支 `destroy` 状态隔离。
  - Sequence 使用单个专用 SVG 场景和“元素 / 结构 / 属性”右侧三标签；支持元素点击/拖放创建、生命线拖建消息、消息端点拖拽重绑、参与者横向拖序、结构树键盘/拖放、画布双向选中、就地编辑和带影响数的参与者级联删除确认。Vue Flow 仅复用视口能力。
  - 扩展 Markdown、组件、领域、命令、布局和 workbench mock E2E；将测试引导键同步到产品当前 `onboarding.v7`，恢复目标 Chromium/mobile 场景。同步设计、实施计划、前端总 README、editor README 与包级说明。
- How:
  - 按 TDD 分层补齐 parser/serializer、命令、布局、组件和保存链回归；最终复审发现互斥分支重复 `create` 漏检后，先用命令测试复现，再共享全图 `created` 集合并继续按分支克隆 `destroyed`，修复后复审无 P0/P1。
  - 精确排除同一工作树中并发出现的 backend、API/规范、AgentWorkbench、file-explorer、夜间执行设计等无关变更，只暂存本任务文件与本条记录。
- Result:
  - Sequence 定向 6 文件 89 项通过；前端全量 Vitest 81 个文件通过（1353 passed / 1 skipped），lint、13 个 workspace typecheck、用户手册与生产 build 通过；目标 workbench Playwright 在 Chromium/mobile 2/2 通过，构建仅保留既有大 chunk 提示。
  - 不涉及 HTTP API、RunEvent/广播、数据库、后端、manager、generated SDK、依赖版本、环境配置、安全权限或既有 Markdown 保存契约；高级语法首批按设计仅锁定保留，没有未完成的编码项。
- Verification:
  - `corepack pnpm exec vitest run packages/editor/tests/mermaid-sequence-domain.test.ts packages/editor/tests/mermaid-sequence-commands.test.ts packages/editor/tests/mermaid-sequence-layout.test.ts packages/editor/tests/SequenceVisualEditor.test.ts packages/editor/tests/MarkdownPreview.test.ts packages/editor/tests/mermaid-compact-metadata.test.ts --reporter=dot`
  - `corepack pnpm test`、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep "Markdown Mermaid Flowchart 和 Sequence"`
  - `git diff --check`、全部 session log 回顾、冲突标记扫描与最终只读代码复审。
- Next:
  - 在真实浏览器手工验证超深嵌套和超长多行文本的可用性；半箭头、中央连接、Actor 菜单等高级创建入口继续按既定后续范围评估。

### 2026-07-18 - 点击引用资产库展示真实同步进度

- Why:
  - 引用配置左侧已初始化资产库卡片实际调用 `synchronizeReferenceRepository`，但现有步骤弹层只绑定右侧 `/verify` 且只接受 `operation=VERIFY_POINTERS`，导致卡片同步期间页面没有可见进度。
- What:
  - 将核验专用弹层状态抽象为 `SYNCHRONIZE/VERIFY_POINTERS` 操作感知状态机；卡片在同步 POST 返回前打开“创建同步任务 → 各服务器同步 → 汇总同步结果”，右侧按钮继续展示只读核验步骤，两者不互相追加请求。
  - 同步模式逐服务器展示等待同步、同步中、已同步、同步失败、等待重试和离线延后；POST 失败可原地重试，活动任务可直接接管既有 generation，终态手动关闭后焦点回到触发卡片。
  - 弹层继续按仓库、operation、请求序号和 generation fencing，复用既有 2 秒 `/status` 轮询、父弹层锁定和焦点限制；未改后端接口或状态语义。
  - 同步前端工程/agent-web README、PACKAGE、引用配置用户手册以及方案/实施计划。
- How:
  - TDD 先增加 deferred 同步请求回归，确认原实现只因缺少 `资产库同步进度` 失败，再抽象进度状态、动态文案和触发焦点；补充同步请求失败重试、活动同步接管、未初始化兼容和卡片焦点恢复测试。
  - 全量校验首次并行执行时，`typecheck/lint` 同时写 VitePress `.temp` 引发临时模块竞争，agent-chat 异步 Markdown 用例也在资源竞争下超时；两项独立复跑及全量顺序复跑均通过，未修改无关代码。
- Result:
  - 引用配置组件测试 45/45 通过；前端全量 Vitest 79 个文件通过（1309 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过，构建仅保留既有大 chunk 提示。
  - 仅改变前端交互和稳定文档；不涉及 HTTP API、RunEvent/广播、数据库、后端、manager、generated SDK、环境配置或安全权限。Playwright 仍沿用上一条记录中的 onboarding v2/v7 既有基线，本次未扩大范围修改。
- Verification:
  - `corepack pnpm exec vitest run apps/agent-web/tests/reference-configuration-dialog.test.ts`
  - `corepack pnpm test`、`corepack pnpm typecheck`、`corepack pnpm lint`、`corepack pnpm build`
  - `git diff --check`、全部 session log 回顾和只读差异审查。
- Next:
  - 在真实多服务器环境点击左侧资产库卡片，人工确认长耗时同步的逐节点状态与后台实际 HEAD 收敛一致；Playwright 引导基线修复继续作为独立任务。

### 2026-07-18 - Service 日志切面改为仅在异常时打印

- Why:
  - `ServiceLoggingAspect` 对每个 `@Service` public 方法都打 `service_entry` + `service_exit(success)` 两条 INFO，正常调用产生大量噪声（如 `PublicAgentConfigRolloutService.claimPendingSync` 每 5 秒一条），单条 INFO 不携带结果或异常，诊断价值低。
- What:
  - 删除 `service_entry`、成功 `service_exit`、Mono `doOnSuccess`、Flux `service_stream_start/end`；仅保留异常时 ERROR 日志，并在其中补 `args` 字段（沿用 `argsSummary`，仅类型+轻量值，避免泄漏 prompt/token）。
  - 同步 `package-info.java` 与 `test-agent-api/README.md` 中该切面的描述。
- How:
  - 改 `logServiceCall`：正常返回静默；Mono/Flux 仅挂 `doOnError`；同步异常走 `catch` 记录后原样抛出。
  - `argsSummary` 改为仅在 `logError` 内惰性计算，去掉每次成功调用都算参数摘要的开销；`argsSummary`/`argSummary` 方法保留（有单测直接覆盖）。
- Result:
  - `mvn -pl test-agent-api -am test -Dtest=ServiceLoggingAspectTest -Dsurefire.failIfNoSpecifiedTests=false`：4 passed / 0 failures，BUILD SUCCESS。
  - 仅日志行为变化，无 API/事件/数据库/兼容性影响；事件名 `service_exit status=error` 保留，兼容既有日志查询。
- Pitfalls:
  - 正常调用不再有任何日志；原先依赖 `service_entry` 作为调用频率心跳的观测需改用其他来源。traceId=unknown 的后台调用异常仍会记录。
- Verification:
  - `mvn -pl test-agent-api -am test -Dtest=ServiceLoggingAspectTest -Dsurefire.failIfNoSpecifiedTests=false`
- Next:
  - None。

### 2026-07-18 - 引用资产 Git 指针核验进度与服务器路径

- Why:
  - “刷新 Git 指针”需要等待多服务器核验，原页面只有按钮转圈，用户无法判断任务创建、各节点处理和汇总所处阶段；已选仓库也缺少服务器绝对目录，现场排查不便。
- What:
  - 引用资产库统一状态响应新增可空 `repositoryPath`，由当前平台解析后的 `OPENCODE_REFERENCES_DIR` 与已校验英文名拼接并规范化；参数缺失、历史非法名称或旧响应缺字段时前端显示“服务器路径暂不可用”，不阻断仓库列表。
  - “刷新 Git 指针”在 POST 返回前打开三阶段嵌套弹层，展示创建核验任务、逐服务器核验和汇总结果；逐节点映射等待、处理中、完成、阻塞、等待重试与离线延后，并展示目标分支/HEAD、就绪数量、安全错误和 traceId。
  - 弹层按仓库 ID、请求序号与核验 generation fencing；执行期间禁止关闭弹层和外层引用配置页、拦截 Escape 并限制焦点，终态由用户手动关闭后恢复到当前刷新按钮。POST 失败可重试，状态轮询临时失败沿用 2 秒自动重试。
  - 同步 HTTP API、workspace/API README 与 PACKAGE、前端/backend-api 说明、模块地图、安全规范和引用配置用户手册；未新增接口、事件、广播、数据库、manager 协议或环境配置。
- How:
  - 继续复用既有 `/verify`、`/status` 与状态轮询；后端只在业务响应组装时派生路径，并捕获参数/旧数据路径错误返回空，不记录物理路径。
  - TDD 覆盖路径规范化、缺参、历史非法英文名、Controller JSON、旧响应兼容、弹层先于请求完成出现、三阶段/逐服务器状态、成功/失败/离线、轮询暂时失败、重试、关闭限制和焦点恢复。
  - 调试中确认 Vue 更新仓库快照会替换弹层与按钮 DOM，测试必须断言当前节点；业务状态实际已正确推进，未用放宽状态机掩盖失败。
- Result:
  - 后端定向 reactor 通过：workspace 47 项、API 3 项；前端全量 Vitest 79 个文件通过（1305 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过，构建仅保留既有大 chunk 提示。
  - 引用组合树 Playwright 的 Chromium/mobile 场景均渲染出目标节点，但当前 `HEAD` 的首次引导已使用 `v7`，E2E 基线仍写入 `test-agent.onboarding.v2`，`el-tour` 遮罩拦截点击后两项超时；本次未扩大范围修改该既有基线。
  - 仅增加向后兼容的可空 HTTP 响应字段；路径只对既有 `APP_ADMIN` 接口可见且不进入日志/错误。未修改 RunEvent、内部广播、Flyway/MyBatis、generated SDK、manager 或 `.env.local`。
- Verification:
  - `mvn -pl test-agent-workspace-management,test-agent-api -am -Dtest='ReferenceRepositoryApplicationServiceTest,ReferenceRepositoryControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `corepack pnpm test`、`corepack pnpm typecheck`、`corepack pnpm lint`、`corepack pnpm build`
  - `corepack pnpm e2e --grep "workspace tree merges references with source colors and exposes non-merged aliases"`（被上述既有 v2/v7 引导基线阻断）
  - `git diff --check`、session log 回顾与只读差异审查。
- Next:
  - 在独立任务中把 Playwright 的首次引导抑制键从硬编码旧版本改为与产品版本同步，再恢复引用组合树 Chromium/mobile 定向绿灯；真实多服务器环境仍需按上一条 session log 的计划完成实际路径与 HEAD 人工验收。

### 2026-07-18 - 引用资产分支切换与服务器指针核验

- Why:
  - 应用管理员需要在引用资产库初始化后受控切换分支并同步所有服务器，同时在配置页查看每台服务器实际 Git branch/HEAD，确认多节点是否真正收敛。
- What:
  - 新增 `switch-branch` 与只读 `verify` 内部 API、`SWITCH_BRANCH/VERIFY_POINTERS` 操作类型、逐服务器 `online/matchesTarget/verifiedAt/syncedAt` 响应，以及 `operation_type/verified_at` Flyway 增量迁移和 MyBatis XML 映射。
  - 分支切换以远端 `ls-remote` 固定目标提交并 CAS 推进 generation；worker 写回实际读取的 branch/HEAD，核验只读本地 Git 元数据。活动任务和历史副本由广播、租约 fencing、在线/离线补偿继续收敛。
  - 配置弹窗增加分支选择和旧/新分支二次确认、目标指针与逐服务器实际指针/在线状态/匹配状态/同步及核验时间、完整 HEAD 复制和“刷新 Git 指针”。成功切换后刷新工作区引用组合树。
  - 前端以选择代次、后端 generation、同代次请求发起序号三层 fencing 丢弃迟到状态；READY 先通知工作区刷新，再独立加载弹窗目录。关闭/重开、失败后重新同步、模糊请求结果和多仓库 pending 均保留有限补偿语义。
  - 最终审查补出并修复 single-branch clone 切换分支缺陷：切换时显式 fetch `+refs/heads/<branch>:refs/remotes/origin/<branch>`，不存在的本地分支从已固定提交创建，不依赖旧 fetchspec 的 `--track`；已有目标本地分支仍执行可快进校验。
  - 同步更新 HTTP、事件、数据库、后端部署、模块 README/PACKAGE、前端包说明和用户手册。
- How:
  - API 与业务层继续要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），并复核应用关联和 `APPLICATION_ASSET_REPOSITORY` 类型；广播名称及仅含 `repositoryId/generation/traceId` 的安全 payload 保持不变，不进入 RunEvent SSE。
  - 数据库副本保留上一代实际指针快照，新 generation 不用目标值伪造实际值；`matchesTarget` 只对完整可信的 `READY` 观察为真。核验使用禁用 optional lock、untracked cache 和 fsmonitor 的只读 Git status。
  - TDD 先复现 single-branch 无共同历史分支切换失败、READY 目录请求期间关闭导致刷新丢失、同 generation 迟到回滚、被拒快照误消费 pending，以及重开首轮列表失败后补偿轮询停摆，再做最小修复；最终综合审查无 Critical/Important。
- Result:
  - 引用/Git/API/MyBatis/Flyway 定向 114 项通过，含真实 single-branch clone 切换到无共同历史分支；后端用户指定 Reactor 中除 persistence 外均成功，persistence 仍被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 与 H2 不兼容统一阻断（76 errors），本次引用 MyBatis/PostgreSQL/migration 用例均通过。
  - 前端全量 Vitest 77 个文件通过（1290 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过；构建仅有既有大 chunk 提示。manager `go test ./...` 的 6 个包通过。
  - 全量 Playwright 为 123 passed / 70 failed / 1 skipped；失败是近期日志已记录的 35 类 workbench 文件加载、Agent、Help、设置、模型和运行流旧基线在 Chromium/mobile 的成对失败，精确数量不能与此前提前停止的套件等同。本次引用组合树 Chromium/mobile 定向 2/2 通过。
  - 涉及新增内部 HTTP API、数据库兼容字段和既有内部广播消费语义；不修改广播 payload、RunEvent、generated SDK、`.env.local`、manager 协议或已运行 OpenCode 进程。显式 refspec 不经 shell，凭据仍只在发起/目标 worker 内使用。
- Verification:
  - `mvn -pl test-agent-api,test-agent-persistence -am -Dtest='GitWorkspaceServiceTest,GitWorkspaceServiceRealGitTest,ReferenceRepository*,MyBatisReferenceRepository*' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-common,test-agent-domain,test-agent-workspace-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-api -am test`（除上述既有 H2 migration 阻断外，其余目标模块成功）
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`、`corepack pnpm lint`
  - `corepack pnpm e2e`、`corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep 'workspace tree merges references'`
  - `go test ./...`、`git diff --check`、冲突标记扫描和三轮只读代码审查。
- Next:
  - 在真实双服务器环境完成分支切换、部分节点离线/恢复、逐服务器 HEAD 展示与引用组合树刷新人工验收；另行修复既有 H2 migration 和 workbench E2E 基线。

### 2026-07-18 - 在应用工作区文件树展示引用目录

- Why:
  - 引用配置保存后，应用工作区文件树还只能读取物理工作区，无法按 `merge` 展示引用资产，也无法区分只读引用内容。
- What:
  - 新增 `workspace.view.list/read` 文件 WebSocket 组合视图：`merge=true` 按 `sdd-folder-name` 合并到工作区同名一级目录，纯引用文件/目录标记为蓝色只读；工作区已有同名目录返回 `MIXED` 并保持普通颜色。`merge=false` 以参考别名作为只读一级目录。
  - 后端每次从最新 JSONC 重建挂载，只接受能反向验证到当前应用资产库、本机同 generation READY 副本和平台引用根目录的配置；拒绝物理路径伪造、`.git`、路径穿越与符号链接。单引用失败转为局部 warning，每层最多 1000 项。
  - 工作区目录从 `WORKSPACE` 变为 `MIXED` 时保持稳定 ID；前端刷新按目录深度逐层用新父节点返回的 locator 重放展开状态，并汇总、去重所有已加载目录的 warnings/truncated，恢复后清除旧告警。
  - 引用文件可只读打开、重试、复制逻辑路径和加入对话上下文；保存、重命名、删除、移动、粘贴及撤销入口按来源关闭，普通工作区写操作继续走原 RPC。
  - 加固文件通道权限：托管工作区在 route、ticket 和每条 RPC 都实时校验有效应用成员；非托管 Workspace 默认拒绝，仅 `SUPER_ADMIN` 服务器工作空间兼容入口放行。会话运行上下文对历史非托管 Workspace 的 Session owner 兼容规则不变。
  - 同步 HTTP/文件 WebSocket 协议、安全规范、后端模块 README/PACKAGE、前端包说明和用户手册。
- How:
  - 组合视图继续走平台文件 WebSocket route/ticket/RPC，不新增 Java 间 HTTP 文件代理；引用副本读取复用既有本机安全目录服务，不把物理路径返回浏览器。
  - 前端复用现有 FileExplorer，通过稳定节点 ID 作为缓存/展开键，并保留原 `workspace.list/read/write` 兼容调用。
  - TDD 覆盖 merge/alias、颜色与写入口、JSONC/READY/路径安全、稳定 ID、刷新 locator、告警恢复、鉴权及读失败重试；最终只读复审未发现 Critical/Important 并批准。
- Result:
  - 后端 workspace/runtime/api 聚焦测试 49 项通过；用户指定 Reactor 中 common/domain/workspace/runtime/api 全量均通过。persistence 仍被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 与 H2 不兼容统一阻断（76 errors），本次未改数据库或该 migration。
  - 前端全量 Vitest 77 个文件通过（1269 passed / 1 skipped），13 个项目 typecheck、用户手册和生产 build 通过；构建仅有既有大 chunk 提示。manager `go test ./...` 通过。
  - 新增引用树 Playwright 在 Chromium/mobile 2 项通过；三个既有工作区切换/未选择工作区场景在两项目共 6 项仍超时或找不到旧空态文案，本次没有扩大范围修复这些既有 E2E 基线。
  - 涉及文件 WebSocket 协议与权限安全边界；不新增 HTTP 端点、RunEvent、数据库表/migration、manager 协议或环境配置，不修改 generated SDK。组合视图为懒加载且每层限 1000 项；真实双服务器人工验收仍沿用上一条引用同步功能的待办。
- Verification:
  - `mvn -pl test-agent-workspace-management,test-agent-opencode-runtime,test-agent-api -am -Dtest='WorkspaceViewApplicationServiceTest,ManagedConversationWorkspaceAccessAuthorizerTest,WorkspaceFileRoutingServiceTest,WorkspaceFileSocketTicketServiceTest,WorkspaceFileWebSocketHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-common,test-agent-domain,test-agent-workspace-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-api -am test`（除上述既有 H2 migration 阻断外，其余目标模块成功）
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep 'workspace tree merges references'`
  - `go test ./...`
  - `git diff --check`、冲突标记扫描和最终只读代码复审。
- Next:
  - 单独修复既有 H2 migration 与 workbench E2E 工作区选择基线后重跑完整套件；在真实双服务器部署完成引用副本与组合文件树人工验收。

### 2026-07-18 - 增加引用配置与资产库多节点同步

- Why:
  - 应用管理员需要在个人工作区内初始化应用资产库、保证所有在线 Java 服务器副本收敛到同一远端提交，并把可选的首层 SDD 目录安全写入工作区 `.opencode/opencode.jsonc`。
- What:
  - 新增引用库状态/副本领域模型、MyBatis XML 仓储、Flyway `V20260718110000`、APP_ADMIN 内部 API 与 `reference-repository.sync-requested` 内部广播；状态机按固定远端分支 HEAD、代次、数据库租约和定时补偿完成多节点同步，离线节点延后补齐。
  - Git 副本初始化采用同级临时目录和原子改名；已有副本校验 origin、分支、干净状态和快进关系，拒绝未知目录、脏仓库、分叉、符号链接及路径穿越，不删除或强制覆盖冲突内容。
  - 前端仅在 APP_ADMIN/SUPER_ADMIN 的个人应用工作区显示引用配置入口；双栏弹窗覆盖分支选择、2 秒状态轮询、逐服务器错误、首层橙色目录选择及 JSONC 保存/更新。`jsonc-parser` 以最小补丁保留注释、尾逗号、未知字段，并阻止非法 JSONC、非对象 references、Git/字符串同名引用和路径冲突。
  - 新启动及平台管理重启的 OpenCode 进程由公共启动服务注入 `OPENCODE_REFERENCES_DIR`；manager 安全命令展示该非敏感变量，未改变 configUpdate 协议或既有进程。
  - 同步 HTTP API、事件、数据库、部署、模块 README/PACKAGE、manager/前端说明及用户手册；修正既有参数种子 migration 注释中的 Flyway 占位符字面量，使 PostgreSQL 全量迁移可解析，参数值语义未变。
- How:
  - 复用现有应用关联、分支查询、服务器广播、在线服务器快照、公共启动服务和平台文件 WebSocket route/ticket/RPC；凭据只在发起节点解密，广播不携带凭据、SSH key、文件内容且不进入 RunEvent SSE。
  - 代次 CAS、租约 token/fencing 与本机文件锁共同避免并发 worker 回写；临时网络错误指数退避，永久 Git 冲突等待管理员重新同步；目录树每层限制 1000 项。
- Result:
  - 引用功能后端定向 reactor 通过，相关 122 项测试全部通过（含真实 Git、安全路径、租约/补偿、MyBatis H2、Testcontainers PostgreSQL、权限 API、配置冻结、运行时注入和 Spring 装配）；`go test ./...` 通过。
  - 前端全量 Vitest 75 个文件通过（1247 passed / 1 skipped），typecheck、lint、用户手册和生产 build 通过。
  - 用户指定的后端全量命令在 persistence 被既有 `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（76 errors；引用库 PostgreSQL/MyBatis 用例本身通过）；前端 E2E 在既有 `workbench.spec.ts` Agent 树隐藏/`agents` 目录缺失处连续失败 14 项，随后停止，2 项中断、176 项未运行。本次未扩大范围修改这些基线问题。
  - 本机没有真实双服务器环境，离线恢复、跨 Java 租约互斥和副本收敛由测试覆盖，仍需在双服务器部署环境执行计划中的人工验收。
  - 涉及新增内部 HTTP API、内部广播、两张数据库表和兼容性配置字段；不修改 generated SDK、`.env.local`、OpenCode 源码或 manager 协议。既有运行进程不重启，引用目录在下次平台管理启动/重启后生效。
- Verification:
  - `mvn -pl test-agent-app -am '-Dtest=GitWorkspaceServiceRealGitTest,ReferenceRepository*,ConfigurationManagementApplicationServiceTest,ConfigurationManagementControllerTest,OpencodeProcessStartupServiceTest,RuntimeManagementCommandServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-persistence -am -Dtest=MyBatisReferenceRepositoryRepositoryIntegrationTest,MyBatisReferenceRepositoryPostgresqlIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `go test ./...`
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`、`corepack pnpm lint`
- Next:
  - 修复既有 H2 migration 兼容和 workbench E2E Agent 树基线后重跑完整套件；在真实双服务器环境完成离线恢复与 HEAD 一致性人工验收。

### 2026-07-18 - 新增引用资产通用参数

- Why:
  - 需要为引用资产（规格文档、参考素材等）提供统一根目录参数 `OPENCODE_REFERENCES_DIR`，并约定规格驱动（SDD）场景识别规格目录的名称清单参数 `REFERENCES_SDD_FOLDER_NAMES`，作为后续引用资产消费功能的配置基础。
- What:
  - 新增 Flyway migration `V20260718100000__seed_references_params.sql`，向 `common_parameters` 种子化两个 `all` 平台参数：
    - `OPENCODE_REFERENCES_DIR` = `${SYS_DATA_ROOT_DIR}/agent-opencode/references`，中文名「引用资产根目录」，`editable=false`（只读，不允许修改）。
    - `REFERENCES_SDD_FOLDER_NAMES` = `docs,spec`，中文名「规格驱动标准目录名称」，`editable=true`（可改）。
  - `OPENCODE_REFERENCES_DIR` 的值用 `'$' || '{SYS_DATA_ROOT_DIR}/...'` 拼接规避 Flyway `${...}` 占位符替换，复用 `all` 行引用平台参数 `SYS_DATA_ROOT_DIR` 的解析能力，与 `OPENCODE_SESSION_DIR` 等路径参数一致。
  - 同步 `CommonParameterSeedMigrationTest`（SQL 内容校验）、`MyBatisCommonParameterRepositoryIntegrationTest`（迁移后断言）、`docs/deployment/database.md`、`backend/test-agent-persistence/README.md`。
- How:
  - 沿用既有种子迁移模式：`all` 平台单行 + 显式 `editable` 列；`SYS_DATA_ROOT_DIR` 引用沿用 consolidate 迁移（V20260629230000）的字符串拼接写法。
  - 用户原始值 `docs,spce` 经确认改为 `docs,spec`（SDD 规格目录通用命名，`spce` 疑为笔误；该值 editable，可后续调整）。
- Result:
  - `CommonParameterSeedMigrationTest` 2 个方法通过（含新增 `referencesParamsSeedContainsAllPlatformDefaults`）；BUILD SUCCESS。
  - `MyBatisCommonParameterRepositoryIntegrationTest` 因预存在问题无法端到端验证（见 Pitfalls），新增断言按构造正确。
  - 未修改 API、RunEvent、DTO、前端、安全、环境配置或 generated SDK；无新增依赖。
- Pitfalls:
  - main HEAD 上跑全量 Flyway 的 H2 测试（含 `MyBatisCommonParameterRepositoryIntegrationTest`）被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `timestamptz` 类型阻断（H2 不识别，已用干净 HEAD 复跑确认预存在，提交 `7b0df66a9` 引入）；本次未修（AGENTS 规则 1 最小范围）。生产 PostgreSQL 支持 `timestamptz`，迁移可正常执行；待该 H2 兼容问题修复后，本迁移可在 H2 端到端验证。
  - 工作区存在前一会话（同一提交者，今日「工作空间创建只允许选择测试工作库」）未提交的前端改动；任务期间并发会话已将其提交为 `1fee2cc3f 修复工作空间版本库类型筛选`（含前端代码与前一会话 session log 条目）。本次改动与前端无关，未暂存、未触碰前端文件；本提交 session log diff 仅含本次新条目。
- Verification:
  - `mvn -f backend/pom.xml -pl test-agent-persistence -am test -Dtest=CommonParameterSeedMigrationTest` 通过。
  - 干净 HEAD（`git stash -u`）复跑确认 `timestamptz` 失败与本次改动无关。
- Next:
  - 后续若有引用资产消费方，按 `CommonParameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")` 读取根目录、按 `REFERENCES_SDD_FOLDER_NAMES` 逗号拆分识别规格目录。

### 2026-07-18 - 工作空间创建只允许选择测试工作库

- Why:
  - 应用工作空间创建下拉此前直接使用全部已关联版本库，应用代码库或应用资产库也会被展示并可能默认选中，与工作空间只能关联测试工作库的业务约束不一致。
- What:
  - “工作空间管理”的已关联版本库下拉改为只展示测试工作库，并在标签后提示“只能关联类型为测试工作库的版本库。”；“应用与版本库关联”页仍展示全部类型。
  - 默认版本库选择同步从过滤后的列表产生；没有已关联测试工作库时保持空选择且不读取分支。
  - 同步 agent-web README/PACKAGE、内置用户手册和组件回归测试。
- How:
  - 按 `repositoryType=TEST_WORK_REPOSITORY` 筛选；显式类型优先，只有类型缺失时才回退 `standard=true`，兼容历史数据但不让冲突旧字段覆盖新类型。
  - TDD 先确认混合类型下拉仍显示并默认读取应用代码库，再实现派生列表；将旧的非测试工作库创建用例替换为空选择回归。
- Result:
  - 前端全量 Vitest 72 个文件通过（1206 passed / 1 skipped）；用户手册、Vue TypeScript 检查和生产 Vite build 通过，构建仅保留既有大 chunk 提示；`git diff --check` 通过。
  - 未修改 API、RunEvent、DTO、数据库、后端、安全、环境配置或 generated SDK；无新增依赖。

### 2026-07-17 - 会话日志改为按提交者分文件

- Why:
  - 多人/多智能体同时修改单一 `.agents/session-log.md` 频繁冲突；实测两次读取间隔内顶部条目已变化，确认存在活跃并发写。按 `git config user.name` 分文件可消除并发写冲突。
- What:
  - 修改 `AGENTS.md` 规则 22/23 与完成标准、`docs/guides/self-checklist.md`、`docs/guides/ai-workflow.md`、`.opencode/skills/code-update-handoff/SKILL.md`。
  - 约定每位提交者写入 `.agents/session-log.{id}.md`；旧的共享 `.agents/session-log.md` 原地冻结为历史归档，仅回顾时阅读、不再续写。
- How:
  - `{id}` 取 `git config user.name`，转小写、连续非 `[a-z0-9]` 字符折叠为单个 `-` 并去首尾 `-`，结果为空时回退 `hostname -s`（本机为 `huangzhenren`）。
  - 旧档内容完全不动（不在顶部加横幅），冻结语义只由规则文档承载，避免与正在发生的并发写冲突。
  - 回顾范围扩展为全部 `.agents/session-log*.md` 近期条目。
- Result:
  - 各提交者只写自己的文件，消除并发写冲突；旧历史保留为只读归档；回顾覆盖所有 session-log 文件。
- Pitfalls:
  - 同一提交者在两台机器并发操作仍写同一文件（罕见）；两个不同提交者清洗后同名需手动区分；纯分析/问答会话不产生条目。
- Verification:
  - 未改代码，无单测；仅文档与约定变更。提交前已回顾旧档近期条目，与本次约定变更无冲突。
- Next:
  - None。

### 2026-07-22 - 文件编辑器打开文件时读取中状态改为醒目动画效果

- Why:
  - 用户希望在文件编辑器加载/读取文件内容时，将单调的“正在读取文件…”纯文本状态替换为一个更醒目、高级和精致的动画效果，从而提升交互过渡的视觉质感。
- What:
  - 引入 `@test-agent/ui-kit` 中的点阵 `Spinner` 组件。
  - 将 `AgentWorkbench.vue` 里的文件加载 `v-if="activeTab?.loadState === 'loading'"` 部分重构为卡片组件结构，使用毛玻璃效果背景 (`backdrop-blur-sm`)、浮层阴影 (`shadow-[0_12px_40px_-12px_rgba(0,0,0,0.12)]`)，并配以双层旋转/呼吸动画光环。
  - 对加载标题文本应用了漂亮的蓝紫色渐变 (`bg-gradient-to-r from-indigo-600 to-purple-600`)，增设闪烁的辅助加载状态指示器。
- How:
  - 修改 `AgentWorkbench.vue` 导入声明，增加引入 `Spinner` 组件。
  - 重新编排加载区域 HTML，结合 Tailwind 4 的 `animate-spin`、`animate-pulse` 等基础动画。
  - 在 `<style scoped>` 底部新增 `@keyframes file-load-card-in` 动画与 `.animate-file-load-card` 动效类，确保卡片淡入及微缩放，实现更柔和流畅的转场体验。
- Result:
  - 成功完成修改，前端 Vitest 和生产 Vite 编译构建 (`corepack pnpm build`) 顺利通过。
  - 未修改 API 契约、RunEvent 事件规范、DTO 模型、数据库表或后端 Java 代码，与已有系统功能无冲突。

### 2026-07-22 - 内部模型供应商关联可复用 Token

- Why:
  - 内部模型供应商原先共用旧单例 Token，无法按 Provider ID 在 Java 内存中解析不同凭据；平台只应记录外部取得的 Token，不应生成 Token 密钥。
- What:
  - 新增独立 Token 定义、SUPER_ADMIN 管理 API 和前端维护区；Token 使用数据库自增 `tokenId`，供应商可选择或复用同一 Token，启用时必须关联有效且非空的 Token。
  - Registry 通过一次联表快照同时构建不可变的 Provider 与按 Provider ID 索引的 Token 映射，代理单次请求从同一代快照解析地址和凭据；供应商或 Token 变更继续发布既有 `InternalModelProvidersUpdatedEvent`，跨 Java 全量刷新和手工刷新接口保持不变。
  - Flyway 将旧非空全局 Token 迁移为“默认 Token”并关联现有供应商，保留旧单例表用于滚动升级；旧顶层 `authToken/tokenConfigured` 兼容语义保留。Token 响应不返回密钥，前后端调试报文补充脱敏。
- How:
  - 关系型 SQL 全部落在 MyBatis XML；Token 密钥只校验非空并按外部原值记录，不 trim、不生成，改名或轮换值不改变 `tokenId`，被供应商引用时依靠业务冲突和外键 `RESTRICT` 阻止删除。
  - 后端定向 Reactor 50 项、前端定向 Vitest 92 项、13 个前端项目 typecheck、生产 build、隔离任务改动后的后端全量 `mvn clean package -DskipTests` 和 `git diff --check` 通过。
- Result:
  - 已覆盖两个 Provider 使用不同 Token、多个 Provider 复用 Token、轮换后刷新、缺失 Token 安全失败、旧请求兼容、鉴权、关联迁移、引用删除冲突、密钥草稿清理和原始报文脱敏。
  - 隔离全量 `mvn test` 中本任务涉及模块及 API 均通过，随后 persistence 的 67 个既有用例仍被 `V20260717173000__create_public_agent_config_rollouts.sql` 使用 H2 不识别的 `TIMESTAMPTZ` 阻断；近期 session log 已记录同一基线问题，本次未扩大范围修改。
  - 涉及新增内部 HTTP API、Flyway 表/外键、安全脱敏和运行时快照；不改变 RunEvent/既有刷新广播类型、不修改 generated SDK、环境配置或 Token 明文存储约定。发布时须先升级全部 Java 节点，再开放新页面的 Token 维护操作；混合版本期间不得配置不同 Provider Token。

### 2026-07-22 - 用户 OpenCode 跨服务器两级负载分配

- Why:
  - 多 Java 部署下，未绑定用户的 OpenCode 首次查询和初始化只在入口 Java 的本地容器中分配，导致请求长期集中到同一 Linux 服务器，无法利用其它服务器的空闲容量。
- What:
  - `BackendJavaRouteResolver` 新增首次分配选服：同轮读取 manager/backend Redis 快照，按容器最新心跳去重，将服务器全部已连接容器（含已满容器）的进程数汇总为负载，仅保留存在 READY、未满且与目标在线 Java 已连接容器的服务器，并按负载与服务器 ID 稳定选择。
  - 未绑定用户仅在精确的进程状态 GET 和初始化 POST 上执行全局选服；远端复用公共 Java 路由与 HTTP 转发，目标 Java 继续使用本地最空容器、原子预占和公共启动流程。ACTIVE binding 始终优先，转发失败不切换下一台服务器。
  - Redis 选服异常进入响应式统一异常链，返回 `RUNTIME_STATE_UNAVAILABLE`；同步更新后端总览、API/runtime README、HTTP API 与单/多后台部署说明。
- How:
  - TDD 覆盖服务器级负载汇总、满容器计数与资格隔离、断连/无容量排除、最新快照去重、稳定排序、当前 Java 放行、远端 GET/POST 转发、binding 优先、防循环、单次失败和 Redis 统一错误。
  - runtime 定向 74 项、API 路由定向 37 项通过；`mvn -f backend/pom.xml -pl test-agent-opencode-runtime,test-agent-api -am test` 的 16 个模块全部通过，其中 runtime 703 项、API 363 项均为 0 失败。
- Result:
  - 请求落到 Java A 时，若用户未绑定且 Java B 所在服务器负载更低并满足调度条件，请求会转发到 B 并由 B 完成创建；已有 ACTIVE binding 不随负载变化迁移。
  - 未新增 HTTP 路径或 DTO，不修改 RunEvent、数据库/Flyway、SQL、manager 协议、前端、Nginx、环境变量或安全契约；Redis 快照仍为最终一致，并发容量继续由目标服务器原子预占保证。
  - 本机无真实双 Linux 节点环境，负载反转、manager 启动唯一性和既有绑定驻留仍需部署环境人工验收；当前工作区其它进程生命周期/manager/前端改动均未纳入本次提交。

### 2026-07-22 - 修复用户绑定端口复用与无主进程展示

- Why:
  - 已有 ACTIVE binding 的进程不健康后会误走首次空闲端口分配，旧端口仍由 manager 托管时，数据库进程与 binding 被迁到新端口，旧进程因此在运行管理中显示为无主进程。
- What:
  - 已有 binding 恢复固定复用数据库中的服务器、容器和端口；仅 manager 明确返回 `PORT_CONFLICT` 或 `PORT_OUT_OF_RANGE` 时按原规则迁移。首次分配和迁移使用短事务、MyBatis `FOR UPDATE` 与条件更新原子预留，manager 调用在提交后执行；端口扫描补充 manager 实时占用端口。
  - manager 串行化进程生命周期命令，补充稳定错误分类、同端口同身份幂等启动、跨端口同 UCID 拒绝、外部监听识别、SIGKILL 后退出确认及心跳清理竞态保护；恢复请求通过可选 `bindingRecovery` 在容量已满时沿用既有绑定。
  - manager 心跳、Java DTO 与前端共享类型增加可选 `unifiedAuthId`、`managerStatus`；无主进程表与拓扑展示 UCID、PID 存活状态、“平台未登记”和“未执行 HTTP 健康检查”，并修复 `baseUrl` 列错位。未从启动命令解析 UCID，也不自动认领或清理存量无主进程。
- How:
  - TDD 覆盖原端口恢复、显式冲突迁移、STALE/超时/配置/容量错误保持绑定、预留后失败重试、同用户与不同用户并发、manager 幂等/身份唯一/外部监听/并发命令、旧响应兼容及无主进程表和拓扑回退。
  - 验证通过：manager `go test -race ./... -count=1`；runtime Maven reactor、API Controller 定向测试、PostgreSQL 锁集成测试 6 项；前端定向 Vitest 17 项与全 workspace typecheck；`git diff --check` 通过。
- Result:
  - 4104 不健康时先由公共停止流程确认退出，再继续在 4104 启动；只有明确端口冲突/越界才迁移到 4105，普通故障和并发跟随者不会创建第二个绑定端口。
  - 同步 runtime、manager、API/domain/persistence、frontend、HTTP API、事件流、安全及企业部署文档。未新增 HTTP 路径、SSE 事件或数据库结构，不修改环境配置和 generated SDK；可选字段兼容滚动升级，推荐按 manager、Java、前端顺序升级。
  - 存量重复/无主进程不自动处理；若触发身份唯一保护，仍需管理员根据 SUPER_ADMIN 运行管理页手工处置。全量前端/模拟 E2E 的既有 Mermaid 超时、DirectoryRows role 与工作区可见性基线失败不在本次范围，任务定向验证均通过。

### 2026-07-23 - 固定企业 worker 容器进程与文件句柄限制

- Why:
  - 企业 Docker 18.09 现场不能继续依赖宿主 daemon 的隐式 PID、`nofile` 和 `nproc` 默认值，需要所有 worker 节点使用一致且可核验的容器限制。
- What:
  - `opencode-worker-docker.sh` 的唯一 `docker run` 固定加入 `--pids-limit=8192`、`--ulimit nofile=262144:262144` 和 `--ulimit nproc=8192:8192`，不新增 `docker.env` 配置。
  - `verify-dev-scripts.sh` 增加三个精确命令参数断言；企业部署入口、单/多后台手册和后端排障文档补充重建、生效值检查与逐节点停止条件。
- How:
  - TDD 先确认测试因缺少 `--pids-limit=8192` 按预期失败，再加入最小脚本实现并恢复 GREEN。
  - 本机 Docker Desktop 29.6.1 在 arm64 上以 amd64 仿真运行 `test-agent-opencode-worker:1.18.4`；真实 HostConfig 显示 `PidsLimit=8192`、`nofile` soft/hard `262144`、`nproc` soft/hard `8192`，容器 `/proc/1/limits` 返回相同值。验证容器已停止并删除，临时目录移入废纸篓。
  - `tools/verify-dev-scripts.sh`、Bash 语法和 `git diff --check` 通过；企业 Docker 18.09 目标机仍需在逐节点重建后执行文档中的 `docker inspect` 与 `/proc/1/limits` 验收。
- Result:
  - worker、manager 及其 OpenCode 子进程不再依赖 Docker daemon 的文件句柄和用户进程默认值；业务最大 OpenCode 进程数、端口池、挂载和健康检查保持不变。
  - 脚本参数只在容器重建时生效；双后台必须先处理 `.4` 并验收，再处理 `.114`，任一节点失败时停止。未修改 API、RunEvent、数据库/Flyway、generated SDK、安全凭据或环境配置文件。

### 2026-07-23 - 修复测试库缺失的 Flyway 历史迁移

- Why:
  - 测试库曾执行 `V20260721134000__migrate_night_execution_to_xxl.sql`，仓库随后为兼容企业存量库有意将其改为 `V20260722130000`，导致本机 `test` profile 启动时报告已应用迁移无法在本地解析。
- What:
  - 对 `.env.test` 指向的 PostgreSQL 执行 Flyway `repair`，将旧版本标记为 `DELETE`；随后执行当前迁移链，使 `V20260722130000` 和 `V20260722180000` 正常落库。
- How:
  - dotenv 继续按 `KEY=VALUE` 文本解析，不执行文件内容、不修改 `.env.test`，也未手工更新 `flyway_schema_history`。
  - 修复前只读确认旧版本成功、当前版本未执行；Flyway 结果为删除标记 1 条、移除失败迁移 0 条、校验和对齐 0 条，迁移 2 条。
- Result:
  - 最终 Flyway 目标版本为 `20260722180000`，独立校验通过且无无效迁移；旧版本保留原成功记录和 repair 删除标记，当前版本保留成功记录。
  - 本次仅修复本机测试数据库外部状态并追加会话留痕；未修改业务代码、迁移文件、API、RunEvent、数据库结构、安全配置或环境配置文件。

### 2026-07-23 - 兼容企业 Redis 与 HTTP XXL 登录

- Why:
  - 企业固定 HTTP 入口的 SSO 登录返回 HTTP 200 的 `System Error` 页面，后台根因为 `RedisSystemException`；上游错误页随后访问缺失的父窗口 `adminTab`，又产生次生 JavaScript 异常。
- What:
  - 将 SSO ticket 一次消费从 Redis 6.2 的原生 `GETDEL` 改为兼容 Redis 5/6.0 的原子 Lua；ticket 消费、用户 JIT 和 XXL 登录任一运行时异常都返回平台 503 状态页，不再落入上游通用错误页。
  - XXL 会话 cookie 默认保持 `Secure`，增加显式企业 HTTP 开关；单/双后台模板及诊断脚本要求固定 HTTP 部署在每个 Java 节点设置 `TEST_AGENT_XXL_JOB_COOKIE_SECURE=false`。
- How:
  - 按 TDD 完成失败与恢复场景，使用真实 Redis 5 和 MySQL 8.4 容器验证，并执行 XXL integration 全量测试、平台应用打包、企业配置及诊断脚本校验。
  - 后端全量测试仍被干净基线中的 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData` 阻塞；该用例已独立复现，相关代码与本次差异无关。
- Result:
  - 未变更 HTTP 路径/DTO、RunEvent、数据库/Flyway 或 XXL 上游源码；本次属于 Redis 兼容性与 HTTP cookie 安全配置变更。企业需发布新 JAR，并在所有固定 HTTP 后端节点显式关闭 cookie 的 `Secure` 属性。

### 2026-07-23 - 隔离 OpenCode 用户运行目录

- Why:
  - 用户 OpenCode 进程此前只设置 `XDG_DATA_HOME`，系统 HOME、cache、state 和 tmp 仍可能落到 manager 系统账号的共享目录，需要按统一认证号的稳定 `sessionPath` 完成目录级隔离。
- What:
  - Go manager 在合并调用方环境后强制派生并覆盖 `HOME`、`XDG_DATA_HOME`、`XDG_CACHE_HOME`、`XDG_STATE_HOME`、`TMPDIR` 和 `OPENCODE_CONFIG_DIR`；fork 前创建或校正 session、`.cache`、`.local/state`、`.tmp` 为 `0755` 普通物理目录，冲突或创建失败不拉起进程、不写成功 state。
  - 保留 Java 维护的公共配置软链接、legacy 端口目录 fallback、健康进程幂等复用和受管重启语义；`startCommand` 按固定顺序展示新增非敏感变量，原 API key 脱敏不变。
  - 企业 worker smoke 在断网容器内实际启动 OpenCode，核验 PID 1 环境、`/path` 和 data/cache/state/tmp 下的 `opencode` 普通子目录；同步 manager/runtime、部署、HTTP 和事件流文档。
- How:
  - TDD 覆盖保留变量不可覆盖、不同统一认证号路径分离、既有目录权限校正、文件/软链接冲突时 starter 未调用且 state 未写入、创建失败不泄露 sessionPath/统一认证号，以及 legacy/幂等兼容路径。
  - `cd opencode-manager && go test ./...`、`bash -n tools/verify-opencode-node-worker-image.sh`、`git diff --check` 和 `tools/verify-opencode-node-worker-image.sh test-agent-opencode-worker:internal` 均通过；镜像实际验证 OpenCode 1.18.4、glibc 2.31。
- Result:
  - 新启动或平台受管重启后的 Linux OpenCode 进程按统一认证号使用独立 HOME/XDG/TMP 目录，停止与重启不清理；同一 manager 系统账号下仍是目录级隔离，不引入独立 OS 用户。
  - 未新增 HTTP/WebSocket/DTO/RunEvent 字段、数据库或 Flyway 变更，不修改 Java 启动公共服务、generated SDK 或环境配置；存量 PID 需受管重启后生效。

### 2026-07-23 - 优化已关联版本库的备注展示为图标提示

- Why:
  - 创建工作空间时，"已关联版本库" 后方的文本备注占用多余空间，改为图标悬浮提示以简化 UI 布局。
- What:
  - 将 `SettingsAppWorkspacePanel.vue` 中 "已关联版本库" 后的文字备注 `ta-form-label-hint` 替换为由 `el-tooltip` 包装的 `el-icon` (使用 `InfoFilled` 图标)。
  - 更新对应的样式，删除弃用的 `.ta-form-label-hint` 并新增 `.ta-form-label-hint-icon` 样式类。
  - 同步修改 `settings-app-workspace-panel.test.ts` 中对应的单元测试，将文本存在性断言修改为对 `el-tooltip` 转换后 Title 的断言。
- How:
  - 在 `frontend` 目录运行 `corepack pnpm test tests/settings-app-workspace-panel.test.ts` 确认测试通过。
  - 运行 `corepack pnpm typecheck`、`corepack pnpm lint` 和 `corepack pnpm build` 确认项目通过类型检查、规范检查并打包成功。
- Result:
  - 版本库关联提示在界面上变为了信息图标，鼠标悬浮在图标上时展示原描述文本，使整体表单样式更为紧凑。
  - 未修改 API、事件、数据库或 security 相关内容。

### 2026-07-23 - 增加会话上下文查看

- Why:
  - 右侧会话栏缺少按当前模型查看上下文占用、最近消息用量和输入上下文构成的入口，用户需要参考 OpenCode 1.18.4 在不引入新后端契约的前提下补齐该能力。
- What:
  - 会话 footer 最左侧新增 16px 使用率圆环和精简悬浮卡；点击后在右侧栏 header/footer 之间展示会话、供应商、模型、根消息数、限制、使用率、总/输入/输出 Token及五类横向拆分，支持 Esc/按钮关闭、焦点返回、会话切换关闭和子 Agent 隐藏。
  - assistant `AgentMessage` 保留可选 `tokens/model`；历史映射与实时 reducer 恢复 input/output/reasoning/cache 和 provider/model，正文拆分仅在详情打开时按最近用量消息、input 与根消息数缓存。
  - backend-api 兼容 OpenCode V2 `limit.context/limit.output` 及 Provider `{ all: [...] }` envelope；同步 frontend、agent-web、agent-chat、shared-types、backend-api README。
- How:
  - TDD 覆盖最近有效根 assistant、cache/reasoning、模型切换、未知/超限、根/child 过滤、五类校准、历史/实时恢复、Provider/Model V2 映射、圆环位置、悬浮/详情、关闭焦点、会话/子 Agent 切换和任务消耗保留。
  - `corepack pnpm lint`、`corepack pnpm typecheck`、生产 build、目标 380 条 Vitest 和 Chromium mock E2E 均通过；全量 Vitest 为 1565 passed / 1 skipped / 1 failed，唯一失败仍是既有 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询，已单文件复现且与本次差异无关。
- Result:
  - 上下文限制始终跟随当前模型，未选择时回退最近 assistant 模型；用量取最近有效根 assistant 的五项总和，文本可超过 100% 且 SVG 封顶，系统提示和协议等不可还原开销归入“其他”。
  - 未新增或变更 HTTP API、RunEvent、数据库/Flyway、依赖、费用展示、安全凭据或环境配置；可选前端字段兼容旧历史和旧事件。

### 2026-07-23 - 应用工作空间支持启用状态

- Why:
  - 应用管理员需要保留已有工作空间配置和历史数据，但可将暂不使用的工作空间从工作台切换入口隐藏，且不能改变其它工作空间切换行为。
- What:
  - `application_workspaces` 新增默认 `true` 的 `enabled` 字段，领域模型、MyBatis XML、配置查询与 PATCH 响应同步透传；PATCH 支持部分更新 `workspaceName/enabled` 并兼容原重命名请求。
  - 设置页已有工作空间增加启用开关；workspace-management 模板列表和前端 footer 双重过滤显式停用项，关闭设置后刷新模板查询，但不清空当前已打开工作空间。
  - 同步 HTTP API、数据库、后端模块和前端包 README，并补充领域、服务、Controller、MyBatis、backend-api 和 Vue 组件测试。
- How:
  - 通过 TDD 先验证开关 API、设置交互和切换菜单过滤失败，再补实现；旧响应缺少 `enabled` 时前端按启用处理，存量数据库记录由默认值回填。
  - 后端相关定向 Maven 测试、前端 113 项定向 Vitest、全 workspace typecheck/lint、前端生产 build、后端 20 模块 `mvn clean package -DskipTests` 均通过。
- Result:
  - 停用仅影响工作空间切换模板展示，不删除或修改版本、个人工作区、运行态 Workspace、最近使用、会话和当前已打开状态；重新启用后可再次展示。
  - 变更了兼容性 HTTP DTO 和数据库结构，未新增事件，不涉及性能、安全、generated SDK 或环境配置。

### 2026-07-23 - 会话上下文详情改为左侧抽屉

- Why:
  - 会话上下文详情原先覆盖右侧会话栏全部内容，用户要求改为从会话内容区左侧弹出的局部抽屉，并允许再次点击 footer 圆环关闭。
- What:
  - `SessionContextUsage` 将详情容器收窄为最大 420px、从会话内容区左边缘滑入并增加右侧阴影；header、footer 和右侧剩余内容保持可见，字段与图例网格按抽屉自身宽度自动折行以兼容最小 240px 会话栏。
  - 圆环点击改为开关行为，第二次点击复用原关闭与焦点恢复逻辑；关闭按钮、Esc、会话切换和子 Agent 隐藏行为不变。
  - 触发器通过 `aria-haspopup/aria-controls` 关联详情 dialog；同步 frontend 与 agent-web README，并增加左侧抽屉语义、圆环二次点击关闭和窄栏重排回归测试。
- How:
  - TDD 先验证旧实现缺少左侧抽屉标识、第二次点击仍保持打开且 240px 会话栏内统计仍挤在同一行，再加入最小布局与 toggle 实现；目标组件测试 145 passed / 1 skipped，Chromium mock E2E 1 passed。
  - `corepack pnpm lint`、`corepack pnpm typecheck` 和生产 build 通过；全量 Vitest 为 1570 passed / 1 skipped / 1 failed，唯一失败仍是既有 `DirectoryRows.test.ts` 将 role=`radio` 的“上传”按 role=`button` 查询，与本次文件无关。
- Result:
  - 上下文数据、统计口径和详情字段不变，仅调整展示位置、尺寸、进入动效及触发器关闭交互。
  - 未修改 HTTP API、RunEvent、数据库/Flyway、依赖、安全配置、性能缓存或兼容字段，也未修改环境配置文件。

### 2026-07-23 - 修正上下文抽屉到对话栏外侧

- Why:
  - 上一版把“左侧抽屉”理解成从对话栏内部左边缘滑入，仍会覆盖消息内容；用户明确要求像会话列表一样展开到对话栏左侧，不能挡住对话。
- What:
  - `SessionContextUsage` 改为通过 body Teleport 和 fixed 定位展示，抽屉右边缘始终贴合对话栏左边缘；最大宽度 420px，左侧空间不足时只缩窄抽屉，不回退覆盖对话。
  - 抽屉在窗口缩放、页面滚动和对话栏尺寸变化时按实测矩形重新定位；上下文与会话列表抽屉双向互斥，隐藏对话栏时自动关闭，并保留圆环二次点击、关闭按钮、Esc、会话切换和焦点返回行为。
  - 同步 frontend、agent-web README，并补充严格外侧定位、零/极窄左侧空间、两个抽屉打开顺序、Teleport 生命周期及默认/最小会话栏宽度下不遮挡对话的回归测试。
- How:
  - TDD 先复现旧抽屉右边缘与对话栏左边缘相差 420px、外侧定位程序缺失和隐藏会话栏后浮层残留，再加入最小定位与生命周期实现。
  - 目标 Vitest 为 157 passed / 1 skipped，Chromium mock E2E 为 1 passed；`corepack pnpm lint`、`corepack pnpm typecheck`、生产 build 和 `git diff --check` 均通过。
  - 全量 Vitest 为 1578 passed / 1 skipped / 1 failed；唯一失败仍是既有 `DirectoryRows.test.ts` 将 role=`radio` 的“上传”按 role=`button` 查询，与本次差异无关。
- Result:
  - 上下文详情现在占用对话栏左侧的工作台空间，不再覆盖对话内容；统计数据、模型上限、图表和原有 footer 任务消耗行为不变。
  - 未修改 HTTP API、RunEvent、数据库/Flyway、依赖、安全配置、统计缓存或兼容字段，也未修改环境配置文件。

### 2026-07-23 - 修复会话上下文统计归零

- Why:
  - 平台 Session ID 与 OpenCode 根 scope ID 不同的真实消息被上下文统计错误过滤，圆环、提示和详情因此显示为 0；`payload.info` 的 scope 也没有随消息快照保留。
  - 详情拆分缓存此前以可见轮次数作为失效条件；新增或重分类 reasoning/tool-only root 消息不会改变轮次数，导致已打开抽屉继续展示旧拆分。
- What:
  - 上下文 root 过滤不再接收或比较平台 Session ID：显式 child scope 一律排除，标记缺失时仅按 scope 自身的 `sessionId/rootSessionId` 差异推断 child，未带 scope 的平台历史继续兼容。
  - 消息数改为可见对话轮次：root user 每条一次，连续 root assistant 原始消息只在出现非空 text/text part 后合并计一次；reasoning、tool、file、retry、step-only 不计数。最近有效 assistant 五项用量仍作为唯一 usage 来源，零/缺失后续 snapshot 不覆盖它。
  - 详情拆分缓存改用仅内部的 eligible root 原始消息数，而不是展示轮次数；保持仅在详情打开时计算，流式正文 delta 不会反复重算。
  - reducer 统一从 `payload.message`/`payload.info` 提取远端 session/root/parent scope，保留嵌套 cache、provider/model 和后续 part 更新前的完整快照；同一 assistant message 的后续全零 usage 不覆盖已收到的有效 usage，首包全零仍保持独立规范化值；同步两个前端 README 与 Vitest/Playwright 回归。
- How:
  - TDD RED：定向 Vitest 初始 6 个失败，准确暴露平台/远端 ID 错配、`payload.info` scope 丢失、child/无 scope 边界及可见轮次口径；独立审查再加入同 ID 非零→全零 `message.updated` 的 RED，确认有效 usage 被错误覆盖。最小修复后 87 个相关 Vitest 全绿，Chromium/mobile mock E2E 2/2 通过 DeepSeek `1,000,000` 与 `53,537 / 9,518 / 282 / 5%` fixture。
  - 最终审查先新增“抽屉已打开时新增不可见 root assistant、随后 root→child 重分类”组件 RED；旧 cache 返回 `工具0` 而非预期 `工具24`。最小修复后三个相关 Vitest 文件为 88 项全绿，Chromium/mobile mock E2E 仍为 2/2 通过。
  - `corepack pnpm lint`、串行 `corepack pnpm typecheck`、`corepack pnpm build` 与 `git diff --check` 通过；最终全量 Vitest 为 1590 passed / 1 skipped / 1 known unrelated failed，唯一失败仍是 `DirectoryRows.test.ts` 以 role=button 查询 role=radio 的“上传”。
- Result:
  - 修复只涉及前端展示/reducer投影，不新增或变更 API、RunEvent wire contract、数据库、依赖、安全配置或环境文件；平台与远端 ID 独立可兼容旧历史/旧事件。

### 2026-07-24 - 合并 featrue 与主线

- Why:
  - `featrue` 需要纳入最新 `main` 并推送远端；工作区开始时已处于将该分支 rebase 到 `origin/main` 的中断状态，需要保留双方已有成果并完成集成验证。
- What:
  - 完成七个功能提交到 `origin/main` 的 rebase；冲突处理中同时保留主线的模型/供应商 allowlist 过滤、应用工作空间配置与文件树交互，以及功能分支的 OpenCode V2 Provider `{ all: [...] }` 解包和嵌套模型限制映射。
  - backend-api Provider 目录先统一解包 V2 envelope，再应用平台 `enabled_providers` allowlist；对应测试同时覆盖主线过滤语义和 V2 响应结构。
- How:
  - 前端全量 Vitest 为 94 个文件通过、1604 passed / 1 skipped；backend-api 与应用工作空间相关定向 Vitest 115/115 通过，受影响后端领域、应用、MyBatis 和 Controller 定向 Maven 测试通过。
  - `corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build` 和 `git diff --check` 通过。
  - 后端全量 `mvn -q -f backend/pom.xml test` 仍命中 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData` 这一主线既有失败；服务与测试文件和 `origin/main` 无差异，未由本次合并引入。
- Result:
  - 功能分支与主线的目录过滤、V2 Provider 兼容和工作空间行为均被保留；没有额外新增 API、事件、数据库、安全或环境配置变更。
  - 完整后端测试的上述既有基线问题继续保留为已知风险，受本次集成影响的定向测试均通过。

### 2026-07-24 - 支持超级管理员精确分钟测试定时

- Why:
  - 夜间执行需要一条便于白天联调和功能验收的快速路径，同时必须保证普通用户、21:00–07:00 窗口、15 分钟容量时段和现有 Run 幂等语义不变。
- What:
  - 定时任务增加 `NIGHT_WINDOW/ADMIN_CUSTOM` 领域模式；后者仅允许当前 `SUPER_ADMIN` 选择下一完整分钟至未来 24 小时的时间，使用 1 分钟显示区间和 15 分钟重试窗口，不预留或释放夜间容量。创建和改期由后端认证主体强制校验，owner 不接受客户端指定；角色移除后可取消但不可改期。
  - PostgreSQL 增加非空默认的 `schedule_mode` 和枚举检查约束，MyBatis XML 完整读写；XXL MySQL V5 将既有分发任务改为每分钟 Cron，保持原有路由、阻塞、过期、全局互斥和零 XXL 重试策略。
  - 前端普通用户交互不变；超级管理员可在原定时面板切换“测试时间”，使用 1/3/5 分钟快捷值或北京时间 `datetime-local`；待执行 Tab、当前会话卡片和失败卡片以“测试定时 + 单个精确时间”展示。
- How:
  - TDD 覆盖默认模式、时间边界、权限前置且无副作用、创建/改期/取消/生命周期/补偿容量隔离、新旧 DTO 兼容、两种模式持久化与到期扫描、XXL 真实 MySQL migration、北京时间解析和前端双模式交互。
  - 相关后端回归通过：runtime 27 项、API 6 项、persistence 9 项、XXL MySQL Testcontainers 3 项；前端全量 Vitest 95 文件 1612 passed / 1 skipped，测试定时 Chromium/mobile E2E 2/2 通过，typecheck 和生产 build 通过；后端 20 模块生产打包通过。
  - 独立审查发现创建幂等重试在返回既有任务前误重新校验时间/容量；已改为“模式权限→只读幂等命中→新请求时间/容量校验→幂等锁→二次命中”，并补充原时间已过、夜间时段已满、自定义永久失败和 HTTP 伪造模式 403 回归；同步清理后端部署文档中残留的每 15 分钟描述。
  - 使用未修改的 `.env.test` 完整重启三服务；backend readiness 和前端 3000 均正常，真实 PostgreSQL 已迁移至 `V20260724143000`，XXL MySQL 已迁移至 V5，分发任务查询为 `0 0/1 * * * ? * / ROUND / DISCARD_LATER / DO_NOTHING / retry=0 / enabled`。
- Result:
  - 新模式复用原会话锁、XXL 分发、租约/心跳/attemptId、Run 唯一锚点和 RunEvent SSE，未增加专属队列、内部分发协议字段、RunEvent 类型或 generated SDK 改动。
  - 同步 HTTP API、事件流、数据库、XXL 架构、安全、测试与相关工程 README/PACKAGE；兼容旧请求、旧任务和缺失模式的旧前端响应。
  - 后端全量 `mvn test` 仍只被主线已知的 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData` 阻断；已独立稳定复现，该服务/测试与本次 diff 无关，本次不扩大范围修改。

### 2026-07-24 - 修复 Manager Unix 子进程停止误判

- Why:
  - Unix `OSStarter.Start` 启动 OpenCode 后没有调用 `Wait` 回收子进程；进程退出后以 zombie 保留 PID，`DefaultProcessAlive` 的 signal 0 检查因此持续判定存活，最终令运行管理停止命令返回 `OPENCODE_BAD_GATEWAY`。
- What:
  - Unix 启动器在成功 `Start` 后异步调用 `command.Wait()`，保持启动接口非阻塞，并让操作系统及时回收已退出子进程；Windows 启动路径不变。
  - 新增 Unix 回归测试，直接验证短命子进程退出后 PID 不再被判定为存活；同步更新 `opencode-manager/README.md`，并补充设计与实施计划文档。
- How:
  - TDD RED 在修复前稳定复现退出 PID 仍存活，加入异步回收后目标测试通过；临时停止用户授权的 4096 独立 `opencode web` 后运行 `go test ./...`，全部 Manager 包通过，随后恢复该进程。
  - 使用未修改的 `.env.test` 和 `test` profile 完整构建并重启 backend、manager、frontend；后端 health/readiness 为 UP、前端 3000 返回 200、Manager WebSocket 连接正常。
  - 真实运行管理链路将本机 4098 进程重启到 PID 74990、`RUNNING / HEALTHY` 后执行停止；Manager 在 50ms 内返回 `STOPPED`，4098 无监听、PID 消失、状态文件删除，页面回到 `STOPPED / NOT_RUNNING`，未再出现 `OPENCODE_BAD_GATEWAY`。
- Result:
  - 修复只涉及 Manager 的 Unix 子进程生命周期和对应测试/文档；未修改 HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK、权限、安全配置或环境文件，现有 Java 公共停止程序和错误转换保持不变。

### 2026-07-28 - 增加企业离线工具盒子

- Why:
  - 所有登录用户需要从左侧直接打开独立小工具，并在 HTTP、离线企业环境下仅暴露真正可运行的工具，同时记录热点点击。
- What:
  - 新增 `/toolbox` 沉浸式前端、193 项双语目录（IT-Tools 85 项、OmniTools 108 项）、搜索筛选、热门 Top 10、新标签直达及静默点击上报。
  - 新增目录与点击 API、30 秒计数窗口、`eventId` 幂等，以及永久明细、累计、用户状态三表；Flyway 建表并仅通过 MyBatis XML 读写业务 SQL。
  - 锁定并派生两套上游源码，移除品牌与门户，支持子路径深链；剔除摄像头和 SimplePDF，运行资源全部本地化，并为 HTTP 环境提供剪贴板降级。
  - 增加摘要锁定的 `linux/amd64` Docker 镜像、离线包、独立工具节点部署、Nginx 代理、只读容器、健康诊断和双镜像原子回滚。
- How:
  - 后端 PostgreSQL Testcontainers、H2、API、服务测试通过；前端全量 1636 项通过、1 项既有跳过，类型检查、生产构建及桌面/移动端 E2E 通过；两套派生应用的测试和构建通过。
  - 最终镜像通过 193 条路由断网加载、FFmpeg、Ghostscript、OCR、AI 抠图等真实功能冒烟，以及 SHA-256、许可证、完整源码包、失败保护和成功回滚验证。
  - 真实 CSP 验收发现并修复 ONNX `blob:` 动态模块和上游动态表达式所需的 `blob:`/`unsafe-eval`；外部脚本、连接、Worker、图片和媒体源仍被禁止。
- Result:
  - 最终离线目录是 193 项而非初始预期的 194 项：除 HTTP 下不可用的摄像头外，OmniTools SimplePDF 依赖外部 iframe，按离线入口约束一并剔除。
  - 最终离线包位于 `/private/tmp/test-agent-toolbox-release-final`；点击明细永久增长需纳入容量监控，工具节点首版仍为单点；不新增 SSE，不修改 OpenCode、generated SDK 或 `.env.local`。
  - 本机 Docker 的 Aliyun mirror 存在 manifest 异常，构建时使用 Daocloud registry 前缀但保持官方 `linux/amd64` digest；发布脚本会拒绝仅使用 tag 的基础镜像。

### 2026-07-28 - 部署工具盒子本地测试环境

- Why:
  - 用户日常通过 `restart-dev-services.sh` 启动本地环境；真实启动暴露 `ToolboxCatalogService` 存在两个构造器但生产构造器未显式注入，Spring context 因找不到默认构造器而失败。
  - 生产 Nginx 已有工具前缀代理，但 Vite 开发服务器没有对应规则，3000 入口会把具体工具深链交给平台 SPA，无法完成本地统一入口联调。
- What:
  - 为 `ToolboxCatalogService` 的生产构造器增加显式 Spring 注入，并增加最小 `ApplicationContextRunner` 回归，确保真实仓储构造器能创建服务并加载 193 项目录。
  - 增加 Vite 工具开发代理、套件根路径 `308 /toolbox` 守卫和配置单测；默认连接本机 `18120/18121`，支持命令行环境变量覆盖，不修改 `.env.test` 或 `.env.local`。
  - 将全目录冒烟的导航/渲染等待调整为 60/30 秒，以容纳 Apple Silicon 模拟锁定 `linux/amd64` 镜像的首次懒加载；同步后端、前端、源码区和部署文档。
- How:
  - TDD 先复现 Spring Bean 创建失败与 Vite 代理模块缺失，再分别修复；integration 模块相关 Maven 测试通过，前端全量 99 个测试文件为 1638 passed / 1 skipped，agent-web typecheck、生产构建、Node 语法和 AI 文档校验通过。
  - 从 `/private/tmp/test-agent-toolbox-release-final` 校验并加载两套离线镜像，固定容器 `test-agent-it-tools`、`test-agent-omni-tools` 均为 healthy、只读根文件系统和 `unless-stopped`；使用 JDK 21 和未修改的 `.env.test` 完整执行 `restart-dev-services.sh --profile test --env-file .env.test`。
  - 通过 3000 统一入口逐项加载 IT-Tools 85、OmniTools 108、合计 193 条深链且无非同源请求；ASCII、HTTP 复制降级、FFmpeg、Ghostscript、图片、QR、OCR 和 AI 抠图真实功能冒烟通过，CSP/COEP、根路径守卫和未登录 API 401 均已检查。
- Result:
  - 本地统一入口为 `http://127.0.0.1:3000/toolbox`，后端 readiness 为 `UP`，manager 已连接；工具点击既有 Flyway migration 已应用到本地 PostgreSQL。
  - 本次修复不新增或变更 HTTP/RunEvent 字段、数据库结构、MyBatis SQL、权限或 generated SDK；只补齐既有工具盒子的生产装配、本地代理、测试稳定性与文档。临时 4173 调试网关和 Playwright 快照已删除，无持久数据损失。

### 2026-07-28 - 优化工具盒子布局并固定 IT-Tools 中文版

- Why:
  - 工具盒子顶部 Hero 占用空间，分类下拉不便于连续筛选；搜索、来源和分类在长列表滚动后也会离开视口。
  - IT-Tools 虽有原生 `vue-i18n`，但工具页仍有大量英文控件和说明，需要在不恢复语言选择器的前提下固定为完整中文，并保证离线镜像不会误回退旧 tag。
- What:
  - 移除可见 Hero，仅保留屏幕阅读器标题；将“全部 + 13 类”改为常驻标签，数字随搜索词和来源实时联动，分类本身不影响计数，零结果保持既有选择语义；控制区绑定实际滚动容器吸顶，移动端标签单行横向滚动，来源与分类均补齐 `aria-pressed`。
  - 固定 IT-Tools 默认/回退语言为中文，补齐 85 条路由的标题、说明、控件、错误与帮助文本；日期输出显式使用 `zh-CN`，纯计算错误改用稳定错误码。构建门禁同时校验真实 `toolsByCategory`、路由元数据、共享 UI/复制运行时代码及 1347 个实际 locale key。
  - 重新生成 193 项目录的 IT-Tools 中文展示字段，`toolId/source/sourceVersion/category/categoryLabel/launchPath/catalogOrder`、数量和顺序不变；镜像升级并强制锁定为 `test-agent/it-tools:2024.10.22-7ca5933-platform.2`，部署、打包和诊断脚本拒绝 `latest`/旧版本且核对容器 `Config.Image`。
  - 两套容器 Nginx 增加公开子路径直连映射；同步前端、源码区和离线部署说明，OmniTools 继续使用 `0.6.0-platform.1`。
- How:
  - IT-Tools 全量 40 个测试文件、183 项通过，生产构建通过；中文审计为 85 路由、1347 个 key。前端 4 个测试文件、16 项通过，类型检查通过，桌面/移动端 Playwright 8 项通过；后端 `ToolboxCatalogContractTest` 和 193 项平台契约通过。
  - 最终两个 `linux/amd64` 镜像经 Nginx `-t`、只读根文件系统、`unless-stopped`、健康恢复和实际镜像标签检查；通过 3000 统一入口逐一加载 193 条深链且无非同源请求，ASCII、HTTP 复制降级、FFmpeg、Ghostscript、图片、QR、OCR 和 AI 抠图真实功能冒烟通过。
  - 最终离线制品位于 `/private/tmp/test-agent-toolbox-platform2-final.PF2cgu`：IT-Tools tar SHA-256 为 `62d994dad770b89e68d922ecd6380c54281a7cb9d090f58ac68a6059ab8a7749`，OmniTools tar 为 `8d2dcfed8b5868186fd5929ace96bd6fdbdbaff56fc4da516e0049f429125235`，完整修改源码为 `f5f79248f859727e5b72f12c85e9503a56b3728460f474e62008be87946fc536`，目录为 `cb12b1ed4f7d61ee64299d4c15794c9b2ea463e53423bbf330ab79b09de56c38`。
  - 使用 JDK 21 和未修改的 `.env.test` 完整执行 `restart-dev-services.sh --profile test --env-file .env.test`；独立复审最终为 Ready，无 Critical/Important，dotenv 引号兼容这一 Minor 也已修复并用真实容器回归。
- Result:
  - 本地测试入口为 `http://127.0.0.1:3000/toolbox`；后端 readiness 为 UP，两个工具容器均 healthy，直接深链、容器重启和统一入口代理正常。
  - 本次不新增或变更公共 HTTP API、RunEvent/SSE、数据库/Flyway/MyBatis SQL、工具点击/热门排序、权限、generated SDK 或 OpenCode 源码；未修改 `.env.local`。现有点击事件表永久增长与单工具节点风险保持原交付约束。

### 2026-07-28 - 整合远端 main 以发布工具盒子源码

- Why:
  - 工具盒子 Dockerfile 和中文化提交只存在于本地 `main`，远端同时已有独立的新提交；直接推送会因分叉失败，且禁止用强制推送覆盖远端成果。
- What:
  - 获取远端 `main` 后执行普通 merge，只在 `backend/README.md`、`docs/architecture/module-map.md` 和 `frontend/apps/agent-web/src/PACKAGE.md` 出现说明冲突。
  - 冲突处理同时保留工具盒子、应用源码多服务器物化、对话 Git Tool、个人拉取运行态重载和 manager 重连进程恢复说明；代码自动合并部分不做额外改写。
- How:
  - 合并后确认无冲突标记且 `git diff --cached --check` 通过；JDK 21 后端跳过测试完整打包、前端全 workspace 类型检查、100 个测试文件 1655 passed / 1 skipped，以及工具盒子 193 项平台契约均通过。
- Result:
  - 当前 `main` 同时包含远端既有成果和两份工具 Dockerfile/构建部署脚本，具备非强制推送条件；未使用 rebase、force push、stash 或工作树清理。

### 2026-07-28 - 完成应用源码快照综合验收

- Why:
  - 应用源码固定提交、多服务器物化、独立进度 WebSocket 和工作台源码模式已分阶段落地，需要统一稳定部署/测试文档、审计仓库边界，并完成后端与前端根级验证。
  - 后端根测试发现 AppSource 分支与远端主线自动合并后，同一 `ReferenceRepositoryReplicaTaskDispatcher` 测试 Bean 被语义重复注册，必须先定位根因再收尾。
  - 最终整功能审查继续发现并发终态聚合可能丢失最后一次收敛、retry 重放依赖动态 targets、跨关联应用进度授权过窄、生产步骤未形成完整可恢复时间线，需要在同一 AppSource 会话内完成唯一一轮修复。
  - 最终复审二次发现 retry 可被旧 FAILED replicas 提前终结、completion 成功后的 backup 删除失败会错误回滚新目录，以及并发 materialization 相同 operationId 会被后到请求的可变 slot/targets 破坏幂等，继续合并在本条内完成唯一二次闭环。
- What:
  - 补充应用源码本机挂载、容量、worker/租约、XXL V6 清理与监控说明，新增综合自动化和双服务器人工验收文档，并完善 backend-api 的 operationId、AbortSignal 和严格进度帧契约。
  - 审计功能提交 154 个唯一路径，确认未修改 OpenCode 快照、generated SDK、`.env*` 或工具盒子源码；新增关系型 SQL 只在 AppSource MyBatis XML/Flyway 和 XXL Flyway V6。
  - 删除 `ReferenceRepositoryContextTest` 中自动合并产生的第二条重复 import 和重复 `.withBean`，保留 `AppSourceRepositoryHistory`，不改变生产 Bean 图。
  - result recorder 统一采用 replica lease CAS 后锁 repository slot 的顺序聚合终态，并由 dispatcher 补偿 replica 已全终态的 stranded operation；READY 提升先失效旧 ACTIVE，避免 PostgreSQL 唯一约束冲突。
  - retry service/registrar 改用 route app、repository、actor、类型和 generation 的不可变身份幂等；进度授权按 repository 任一当前启用关联应用实时复核，并保留 PERSONAL owner/admin 边界，HTTP/WebSocket wire 不变。
  - 为每台服务器登记 13 个稳定步骤，新增 lease-fenced progress recorder、attempt reset、legacy `RETRY_QUEUED` 恢复、固定低敏摘要和终态步骤补齐；新增 status 前导扫描索引及对应 MyBatis SQL。
  - 二次闭环把 retry operation 的 SERVER steps 作为本次完成权威，result/recovery 与 MyBatis stranded 扫描都拒绝仍有 PENDING/RUNNING 目标的候选；DOWNLOAD/UPDATE 历史恢复语义不变。
  - materializer 将数据库 completion 成功固定为不可逆发布点，之后 backup 删除改为 best-effort；registrar 在仓库锁内先处理 existing，严格比对不可变身份并返回首请求 SERVER steps/replicas 冻结目标。
- How:
  - 稳定红测 1/1 复现 `BeanDefinitionOverrideException`；对比合并提交两个父分支确认各自只有一份 dispatcher，最小删除重复注册后单测、`test-agent-app -am` 和后端根全量依次转绿。
  - 后端根测试 fresh Surefire 为 351 suites / 2198 tests / 0 failures / 0 errors / 19 conditional skips；PostgreSQL AppSource 1/1、MySQL XXL 3/3 均实际运行且 0 skipped。
  - 前端定向 Vitest 9 files / 122、Chromium/mobile Playwright 22/22、根 Vitest 104 files / 1691 passed / 1 skipped、全 workspace typecheck 和生产 build 全部通过；AI 文档校验与 diff check 通过。
  - 最终波次按 TDD 先补服务/H2/PostgreSQL/API/WebSocket 红测，再实现最小修复；真实 PostgreSQL 持久层 2/2 和应用层三服务器 barrier 2/2 均运行且 0 skipped，跨模块定向 reactor 通过。
  - 最终波次后端根测试最终 `exit 0`，fresh Surefire 为 354 suites / 2225 tests / 0 failures / 0 errors / 19 conditional skips；诊断复跑遇到的既有 runtime scheduler 1 秒时序偶发失败，在同类隔离复跑 5/5 和最终根全量中均通过。前端 AppSource 定向仍为 9 files / 122，全 workspace typecheck 13/14 scope 通过；AI docs、SQL/Flyway、diff 和冲突标记校验通过。
  - 二次闭环严格 TDD：组合后端定向 10 类 71/71，真实 PostgreSQL 持久层 2/2、应用层 retry/并发 4/4 且 0 skipped；后端根 fresh Surefire 为 354 suites / 2234 tests / 0 failures / 0 errors / 19 conditional skips。前端 wire 未变，AppSource 9 files / 122 与 typecheck 13/14 scope 通过；未重复执行 Playwright。
- Result:
  - 应用源码快照形成从领域、数据库、物化恢复、API/独立进度 WebSocket、文件能力到清理运维的交付闭环；进度明确不产生 RunEvent/SSE，普通源码文件仍只走平台 Workspace 文件 WebSocket。
  - 最终波次不新增 API wire、RunEvent 或表字段，新增一条扫描索引 migration 和 MyBatis XML SQL；终态聚合严格串行、retry 重放稳定、跨应用权限实时收敛、终态步骤无在途残留且摘要低敏。
  - 二次闭环不新增 migration、表字段、JDBC SQL、HTTP/事件 wire 或前端代码；retry 离线目标不再提前终结，completion 后 cleanup 故障不再回滚已提交新目录，materialization 同 ID 并发只写一次并返回首请求冻结目标。
  - 未修改环境配置、generated SDK、OpenCode 源码和工具盒子。真实双 Java/双 Linux 的 Git/副本/磁盘清理仍需上线前按人工验收清单执行；前端大 chunk、jsdom Canvas 提示和既有 runtime scheduler 1 秒时序抖动为非本波次阻断项。

### 2026-07-29 - 收口应用源码 backup 接管清理与 operation 原子认领

- Why:
  - materializer 在数据库 completion 成功后允许 backup best-effort 清理失败，但旧 generation cleanup 遇到本机新 READY generation 持有共享根时会跳过全部 backup 回收，重复更新可能持续积累残留。
  - worker 先读取在途 operation 再认领副本，旧 claim CAS 未绑定精确 operation、非终态状态和同服务器可领取步骤；retry 并发终态化后，迟到 worker 仍可能把失败副本重新改为 `RUNNING`。
- What:
  - cleanup 在与发布共用的文件锁内始终回收名称匹配标准 UUID 的已完成发布 backup；新 generation READY 仍只阻止旧源码内容删除，不再阻止 backup 回收，且不会触碰当前 target、generation staging、其它仓库、非 UUID 或相似后缀。
  - `claimReplica` 增加 operationId，并在单条 MyBatis UPDATE 中原子校验 exact repository/generation/operation/server 和 operation `PENDING/RUNNING`。普通 `PENDING/FAILED/STALE` 副本还要求同服务器存在任一 `PENDING/RUNNING` 步骤；step code 不限以允许过期 lease 从任一中途步骤接管。过期 `RUNNING` 副本允许以自身旧 attempt 的任意状态 SERVER step 作为恢复锚点，认领后继续走统一时间线 reset。
  - 新增 H2 exact/终态/步骤门禁和过期 attempt 恢复测试、worker 未认领零副作用测试、PostgreSQL 双屏障竞态测试，以及 cleanup 重复 backup 与相似命名 fencing 测试；同步 domain、persistence、workspace-management README、领域模型和应用源码验收文档。
- How:
  - TDD 先稳定复现旧 cleanup 留下多份 backup、终态 retry 后 claim 仍把副本改为 `RUNNING`，以及 operation 仍 RUNNING 但普通失败副本的服务器步骤全终态时仍可误领；再完成最小生产修改。
  - H2 AppSource mapper 20/20、worker/cleanup/materializer 26/26、真实 PostgreSQL mapper 3/3、三服务器收敛 4/4、retry recovery 6/6 均通过且 PostgreSQL/Testcontainers 为 0 skipped；SQL 约定、AI 文档、diff 和冲突标记检查通过。
  - 后端根测试前两次均只在既有 `RunRuntimeLossConvergenceSchedulerTest.keepsInMemoryRetryWhenNeitherDatabaseNorRedisAcceptedTerminal` 的 1 秒定时窗口失败（742 项中唯一失败），该用例单独重跑通过；最终在提交 `ae387b56b` 上重新执行后端根全量完整通过，fresh Surefire 为 354 suites / 2241 tests / 0 failures / 0 errors / 19 conditional skips，因此未扩大范围修改无关 scheduler。
- Result:
  - completion 后遗留 backup 可由后续旧 cleanup 安全接管，重复更新不再累积；并发终态化后的 stale worker 无法重新制造 `RUNNING` 副本，过期 `RUNNING` 的旧 attempt 恢复语义仍保留。
  - 独立复审确认 backup 命名围栏、operation/step 原子领取门禁及 H2/PostgreSQL 回归覆盖均闭环，最终结论为 `APPROVED`。
  - 未新增或变更 HTTP/进度 WebSocket/RunEvent wire、数据库表或 migration；生产关系型 SQL 仅修改 `AppSourceMapper.xml`，未新增 JDBC Repository SQL，PostgreSQL 竞态测试沿用 `JdbcClient` fixture 建立隔离数据。未修改 `.env*`、generated SDK、OpenCode 源码或工具盒子。

### 2026-07-29 - 整合远端主线并完成应用源码推送前复验

- Why:
  - 推送应用源码快照实现前，远端 `main` 已新增 12 个提交，本地已有交互式 rebase 停在 `frontend/apps/agent-web/components.d.ts` 冲突，必须保留双方组件声明并验证整合结果。
- What:
  - 在远端 `78534c1bb` 基线上继续既有 rebase；冲突文件同时保留远端 `ApplicationGitRefreshManagementPanel` 和本地 `AppSourceDialog/AppSourcePicker` 声明，其余 9 个应用源码提交自动重放完成，未使用 force push、skip、abort 或丢弃远端提交。
  - rebase 后 `main` 相对 `origin/main` 为 `0 behind / 17 ahead`，组件生成文件无冲突标记，工作树干净。
- How:
  - JDK 21 后端根全量为 355 suites / 2270 tests / 0 failures / 0 errors / 19 conditional skips；真实 PostgreSQL、MySQL/Testcontainers 场景随根全量执行。
  - 前端根 Vitest 为 104 files / 1698 passed / 1 skipped，全 workspace typecheck、生产 build 通过；应用源码相关 Chromium/mobile Playwright 22/22 通过。`tools/verify-ai-docs.sh`、`git diff --check` 和冲突标记检查通过。
- Result:
  - 当前 `main` 同时包含远端白盒分析、应用 Git 刷新等既有成果和完整应用源码快照实现，已具备普通非强制推送条件；本次整合未新增业务 API、事件、数据库、权限或环境配置变更。

### 2026-07-29 - 回退前端隐式 Tool 环境上下文注入

- Why:
  - 提交 `ad0bfdc000`、`dfd34a48f9`、`a3876d2395` 在普通用户 Prompt 中隐式注入应用、版本、需求子条目和强制 Tool 调用规则，并通过 `<env_context>` 在消息展示层隐藏；复核确认版本 ID 解析、模糊触发、定时任务不一致和选区正文回显均存在风险，用户要求完整回退。
- What:
  - 删除 `AgentWorkbench` 中版本格式化、环境前缀构造、子条目提取、Tool 强制规则、调试日志及发送链路注入，恢复直接使用原始用户 Prompt 与既有选区上下文序列化。
  - 删除 `agent-chat` 对 `<env_context>` 的展示层过滤，并逆向移除 `dfd34a48f9` 同次写入 `.agents/session-log.guojq.md` 的无关 Windows 软链接条目；保留三个提交之后主线的其它改动。
- How:
  - 按 `a3876d2395 → dfd34a48f9 → ad0bfdc000` 的逆序核对并应用最小反向补丁，确认相关函数、Tool 名称和标签无残留，`git diff --check` 通过。
  - 前端全 workspace typecheck、104 个 Vitest 文件（1698 passed / 1 skipped）及生产 build 均通过；测试仅有既有 jsdom Canvas 提示，构建仅有既有大 chunk 警告。
- Result:
  - 用户消息不再隐式携带应用/版本/子条目或自动 Tool 调用指令，历史展示也不再识别和隐藏 `<env_context>`；现有工作区附件与选区序列化链路保持不变。
  - 未修改 HTTP API、RunEvent/SSE、数据库/Flyway、后端、权限、安全、环境配置、generated SDK 或 OpenCode 源码；稳定文档中没有该隐式行为说明，因此无需同步 API/README。

### 2026-07-29 - 修复后端可执行包依赖与应用源码进度通道启动

- Why:
  - 本地 `test` profile 启动先因可执行 JAR 缺少 PostgreSQL JDBC 驱动失败；修复依赖后又依次暴露应用源码进度 WebSocket 多构造器未显式注入，以及启动脚本把单独 `*` CORS 配置拼成混合来源导致组件构造失败。
- What:
  - 将 `test-agent-app` 的 PostgreSQL 直接依赖恢复为 runtime scope，避免 Maven 依赖仲裁把 persistence 的运行时驱动降成 test scope 并从 Spring Boot JAR 排除。
  - 为 `AppSourceOperationWebSocketHandler` 的生产构造器显式添加 Spring 注入；仅对恰好单独配置的 `*` 接受任意格式合法的 canonical Origin，ticket 仍绑定实际来源，混合 wildcard 与显式来源继续失败关闭。
  - 根目录重启脚本在 CORS 值为单独 `*` 时不再追加动态前端 Origin，并增加隔离脚本回归；同步 backend/API/app README、HTTP/事件、安全与部署文档。
- How:
  - 先用缺少驱动的实际可执行 JAR、Spring 真实组件图和 wildcard Origin 单测分别复现三层故障，再做最小修复；`AppSourceApiContextTest` 与 `AppSourceOperationWebSocketHandlerTest` 8 项通过，`bash tools/verify-dev-scripts.sh` 通过，后端根目录 `mvn -q -DappLogDir=target/log package` 完整通过，产物确认包含 `postgresql-42.7.11.jar`。
  - 使用未修改的 `.env.test`、JDK 21 和 test profile 完整重启 backend、opencode-manager、frontend，后端 readiness 返回 HTTP 200 / `UP`，启动日志不再出现缺少驱动、无默认构造器、Origin 无效或 `Application run failed`。
- Result:
  - 本地后端及整套开发服务可正常启动。未变更 HTTP/WebSocket wire、RunEvent、数据库结构/Flyway、关系型 SQL、性能、generated SDK 或 OpenCode 源码，也未修改任何 `.env*`；单 `*` 仍限定受控本地/测试环境，生产必须使用显式来源。
  - macOS 下 Netty native DNS resolver 加载失败仍会记录既有非阻断 ERROR 并回退系统解析，当前不影响启动与 readiness。

### 2026-07-29 - 隔离聊天会话终态展示

- Why:
  - `FigmaChatPanel` 在组件级保存失败终态，切换 Session 复用组件时会把失败卡片和页脚泄漏到正常会话。
- What:
  - 已建立 Session 身份变化时清理本地失败、停止和完成标记；空草稿首次生成真实 Session ID 保持同一 Run 语义，返回失败历史会话仍按其持久化状态恢复。
- How:
  - TDD 红测准确复现跨会话泄漏，最小修复后组件测试 143 passed / 1 skipped；前端全量 104 files / 1699 passed / 1 skipped，全 workspace typecheck 和生产 build 通过。
- Result:
  - 跨会话错误展示已隔离；未修改 API、RunEvent、数据库、后端、安全、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-30 - 集成 LobeHub 企业通用问答入口与模型网关

- Why:
  - 平台需要以 LobeHub OSS `v2.2.11` 作为测试和代码分析以外的通用聊天/问答入口，复用既有平台认证与模型供应商，并按规范化后的 `department` 名称聚合部门 Workspace。
  - 企业现场完全离线且继续使用纯 HTTP，需要用一次性表单票据代替跨域读取 `sessionStorage`，并把浏览器会话、模型委托、Redis 前缀、独立数据库和对象存储边界固化为可验证部署合同。
- What:
  - 新增平台 SSO 换票、HMAC 服务兑换和委托撤销接口：票据与委托只在 Redis 保存 SHA-256 摘要，票据 60 秒且 Lua 原子消费，nonce 防重放，用户状态在签发及模型调用时重新校验；前端“通用问答”同步打开空白页后以隐藏表单 POST，票据不进入 URL、浏览器存储或日志。
  - 新建中立 `test-agent-model-gateway` 模块，复用既有供应商解析、密钥/UCID 注入、流式转发和错误脱敏；增加平台模型目录、能力探测、九类 OpenAI 兼容端点和按日聚合用量，现有 OpenCode proxy 契约保持不变。
  - 新增 `V20260730090000__add_lobehub_model_gateway.sql` 及 MyBatis XML，保存供应商模型目录、探测结果和每日聚合；Redis 继续使用 DB 0，但平台 SSO 固定 `test-agent:lobehub-sso:*`，LobeHub app 固定 `lobehub:app:*`。
  - 扩展企业离线封包、安装、systemd/Docker、Nginx、Redis ACL、ParadeDB、RustFS、版本锁、SBOM/审批资源和配置校验合同，支持全量及 LobeHub-only 包；同步模块 README、HTTP API、事件、架构、安全、数据库、前后端和部署文档。
- How:
  - 按 TDD 覆盖票据过期/重放、HMAC/nonce、部门归一化、委托轮换/撤销、动态模型目录、错误脱敏、SSE 中断及 Netty buffer 释放、MyBatis H2/PostgreSQL 和真实 Redis 原子消费/前缀隔离；关系型 SQL 全部落在 MyBatis XML，未新增 JDBC Repository SQL。
  - 后端最终执行带 Byte Buddy agent 的根目录 `mvn clean test` 完整通过；新增 PostgreSQL Testcontainers 2/2、Redis 5 集成 1/1 均 0 skipped，生产 `mvn package -DskipTests` 通过。首轮全量仅命中既有 `RunRuntimeLossConvergenceSchedulerTest` 一秒时序抖动，隔离 5/5 与最终 clean 全量均通过。
  - 前端全量为 105 files / 1715 passed / 1 skipped，workspace typecheck 和生产 build 通过；离线包合同测试、三份 Shell 语法、`git diff --check`、冲突标记和敏感文件范围检查通过。
- Result:
  - 当前仓库的平台侧认证、模型网关、前端入口、数据库和离线部署合同已闭环；API 新增且已同步文档，未新增 RunEvent/SSE 事件类型。性能数据只做按日聚合，不保存 prompt、回答、UCID、原始错误或逐请求 trace。
  - LobeHub fork 本体、部门 Workspace JIT、企业模型 UI/服务端适配、Windows 签名客户端和 Linux 真实沙箱不在本仓库，必须按版本/摘要合同外部构建并完成现场验收；在这些产物和目标环境 Flyway history 未核验前保持 `LOBEHUB_ENABLED=false`。
  - 纯 HTTP 的同网段窃听与会话劫持风险仍存在，现阶段仅由网络隔离、一次性票据、短会话、委托 scope 和轮换降低；TLS 仍是后续升级项。未修改 `.env.local`、generated SDK 或 OpenCode 上游源码。

### 2026-07-30 - 完成 LobeHub fork、本地启动与真实服务端介质

- Why:
  - 前一阶段只完成平台侧合同，独立 fork、本地开发启动、可追溯的真实服务端镜像与现场安装门禁仍未闭环；同时完整上线介质不得在缺少 Windows 企业签名和审批 Linux 客户端时被伪造。
- What:
  - 在独立 `/Users/huang/workspace/lobehub-platform` fork 基于上游 `v2.2.11` 实现平台票据登录、部门 Workspace JIT、企业模型适配、离线/私有资源策略和客户端执行禁用；最终提交为 `7d16863c88b8acbacda6d9ee15df0840749e0aaa`，本地标签为 `v2.2.11-platform.1`。
  - 增加 `build-lobehub-artifacts.sh`、真实镜像运行冒烟、安装器完整性/镜像 ID/AuthentiCode 门禁，并将平台版本锁定到该 fork 提交；构建实际 `linux/amd64` LobeHub、ParadeDB 17 和 RustFS 服务端阶段介质。
  - `restart-dev-services.sh` 增加显式 `--with-lobehub`，默认仍不启动；新增开发用 Compose/helper，生成 `0600` 临时配置、复用独立 Redis 前缀、运行 migration/app/scheduler，并精确清理 macOS screen 会话及其包装进程组。
- How:
  - fork 的 CLI 99 项、Desktop 235 项、平台 SSO/离线 44 项、用户/策略 56 项、OpenAPI 5 项、tRPC 24 项测试通过；CLI/Desktop/服务端生产构建、根 typecheck 和变更文件 lint 通过。Docker 8 GiB 内存下用固定 2 worker 完成 Next 生产构建。
  - 真实服务端介质在 `deploy/internal/dist-lobehub-server` 完成 SHA-256、OCI revision、PostgreSQL 17 migration、私有 RustFS bucket、应用 readiness、公网 workflow 403、内部 scheduler 鉴权与容器密钥隔离冒烟；完整封包因缺少签名客户端按预期失败关闭。
  - 使用未修改的 `.env.test` 实际执行 `restart-dev-services.sh --profile test --env-file .env.test --with-lobehub`；后端 readiness `UP`、前端 200、LobeHub 3210 固定回跳、ParadeDB/RustFS healthy。最终复跑开发脚本、构建器、安装器、封包合同测试、Shell 语法和 `git diff --check` 全部通过。
- Result:
  - 本地已可通过显式参数启动完整 LobeHub 开发环境；部署人员可依手册构建可追溯服务端阶段介质，并在客户端完备后构建完整离线包。未修改 `.env.local`、OpenCode 源码、generated SDK、既有 API/事件或数据库 migration。
  - 仍需外部完成企业 Authenticode Windows 客户端、审批的 Linux x86_64 客户端与目标内核沙箱边界验证，以及目标环境 Flyway history、DNS/反代/网络隔离、备份恢复和回滚演练；完整上线介质在此前继续失败关闭。fork 目前仅配置 `upstream`，尚需企业内部 Git 远程才能发布提交与标签。

### 2026-07-30 - 完成 LobeHub 现场验收与冷备份恢复闭环

- Why:
  - 服务端阶段介质此前只证明首次启动，现场仍缺少可执行的运行态验收命令和真实 ParadeDB/RustFS 冷备份恢复工具；配置 URL 也可能分别合法但交叉指向错误服务或凭据。
- What:
  - `lobehub-docker.sh` 新增 DB/Redis/S3/MC/app/平台同源及 URL 编码凭据交叉校验，并增加 `verify-deployment`，核对容器运行状态、restart policy、精确镜像、端口、PostgreSQL 17、Redis ACL、私有 bucket、HTTP 离线策略和容器密钥隔离。
  - 新增 `lobehub-backup.sh` 的 `create/verify/restore` 冷备份流程；三个容器必须由运维显式停止，Docker 状态不可验证即拒绝，归档只允许固定路径和普通文件/目录，归档及 checksum 固定 `0600`，恢复需显式确认并保留原数据 rollback。
  - 安装器和 LobeHub-only ZIP 纳入备份工具；部署手册补齐现场验收、冷备份、恢复、入口关闭和平台 PostgreSQL 独立备份边界。
- How:
  - TDD 覆盖配置错接/错凭据、URL 编码、缺少备份脚本、无确认恢复、容器运行、Docker daemon 不可验证、暴露 checksum、未知路径和符号链接；构建器、安装器、封包、开发 helper 与全部脚本语法回归通过。
  - 使用现有约 2.1 GiB `linux/amd64` 真实服务端介质两次完成 PostgreSQL/RustFS 证明数据写入、停机归档、移走原临时数据、恢复、二次 migration/启动和数据存续验证；最终冒烟 exit 0。
  - 本机只有 JDK 21，使用未修改的 `.env.test` 实际执行 `restart-dev-services.sh --profile test --env-file .env.test --skip-backend-build --skip-frontend-build --with-lobehub`；后端 readiness 200、前端 200、LobeHub 307 固定回平台，开发 ParadeDB/RustFS healthy。
- Result:
  - 服务端部署人员现在可按手册完成安装后验收和可回滚冷恢复；未新增 API、事件、数据库 migration/SQL、generated SDK 或 OpenCode 修改，也未修改 `.env*`。
  - 客户端审计确认当前 Desktop/CLI 仍依赖 fork 内部 `/oidc/*`，而企业运行配置未提供 `JWKS_KEY`；直接启用会重新引入原计划删除的 JWKS/授权服务器，非 OIDC 浏览器确认与设备令牌协议需另行确认。因此完整签名客户端、完整上线介质、企业 Git 远端和真实现场验收仍未完成，`LOBEHUB_ENABLED` 继续保持关闭。

### 2026-07-31 - 完成 LobeHub 非 OIDC 客户端认证与 v2.2.11-platform.3 交付复验

- Why:
  - 上一轮确认 Desktop/CLI 仍依赖 `/oidc/*`，与已确定的简化认证和完全离线边界冲突；同时 Redis wrapper 会在
    `REDIS_PREFIX` 后自动追加冒号，原配置的尾冒号会产生 `lobehub:app::` 双分隔 key。
  - fork、服务端介质、现场脚本和 `--with-lobehub` 需要在同一锁定提交上重新构建和运行验证，避免只更新合同而
    继续交付旧镜像。
- What:
  - 独立 `/Users/huang/workspace/lobehub-platform` fork 新增 Desktop/CLI 浏览器确认协议：五分钟 request/poll
    secret、用户验证码、HttpOnly pending cookie、CSRF、PKCE、一次性授权码和最长 24 小时 opaque Session；
    Redis 只保存摘要，Desktop 使用系统 safeStorage，CLI 使用 mode `0600` 凭据文件，模型委托不下发客户端。
  - Next middleware、tRPC 和 OpenAPI 统一校验 opaque Bearer 并复查用户状态；企业 Desktop/CLI 关闭 OIDC、JWT、
    API Key、refresh token 和自定义服务地址，退出时撤销 Session/模型委托且先清本机凭据。离线 `/oidc/*` 门禁
    调整到 Session 查询之前，使匿名请求直接返回 403 而不是被登录中间件改写为 307。
  - fork 发布提交为 `ccd0400fbe934ba929de637a315d25e969977c76`，标签为 `v2.2.11-platform.3`；平台版本锁、
    环境模板、测试夹具和文档统一升级到合同版本 2。`REDIS_PREFIX` 改为不带尾冒号的 `lobehub:app`，实际 key
    仍为 `lobehub:app:*`；现场 ACL 仅为客户端固定 Lua 状态机开放 `EVAL`，key/channel 边界不放宽。
  - 同步 HTTP API、事件、架构、安全、本地开发、离线部署和发布说明；开发脚本帮助明确默认关闭、显式启用及
    Redis 实际 key 规则。
- How:
  - fork 服务端认证/离线/中间件定向 72 项、真实 Redis 原子状态机 3 项、OpenAPI 7 项、tRPC 27 项、CLI 企业
    认证 21 项、Desktop 企业策略/认证 56 项通过；fork root typecheck、CLI bundle/man、Desktop typecheck 和
    production main/renderer build通过。CLI 上游全量命令同时包含需预装 `lh` 和登录后端的 E2E，并存在与本次
    认证无关的既有 type/test 失败，因此只把变更边界测试和可执行 bundle 作为本版本准入证据。
  - 从锁定 commit 重新构建约 2.2 GiB 的真实 `linux/amd64` 服务端阶段介质
    `deploy/internal/dist-lobehub-server`；全部 SHA-256、app OCI revision、三镜像 ID/架构通过。真实镜像演练完成
    ParadeDB 17 migration、Redis ACL/前缀、RustFS 私有桶、readiness、离线策略、scheduler、密钥隔离及冷备份/
    恢复；完整封包对缺少 Authenticode 证据按预期失败关闭。
  - `build/package/install/backup` 四组合同测试、开发脚本验证和 Shell 语法通过。使用未修改的 `.env.test` 执行
    `restart-dev-services.sh --profile test --env-file .env.test --skip-backend-build --skip-frontend-build --with-lobehub`，
    后端 8080、前端 3000、LobeHub 3210 启动；运行探针确认 0600 env、固定回跳、pending 202、未认证票据 401
    和匿名 OIDC 403。普通前端生产构建被工作树中无关的 `WorkflowChatView.vue` 缺失 TDesign style import 阻断，
    未修改或纳入该并行工作。
- Result:
  - 本地开发环境和服务端离线部署路径已经在 `v2.2.11-platform.3` 真实镜像上闭环，运行服务保留启动状态；
    本次平台提交不新增数据库 migration/MyBatis SQL，不改变既有平台 SSO/模型网关 wire，也不新增 RunEvent/SSE。
  - 完整企业 ZIP 仍必须取得企业 Authenticode 签名的 Windows x64 客户端、匹配签名证据和获批 Linux x86_64
    客户端；独立 fork 也仍缺企业内部 Git remote，目标 Linux/网络/DNS/反代/Flyway history 的现场验收不能由
    Apple Silicon Docker 仿真替代。在这些外部条件完成前保持 `LOBEHUB_ENABLED=false`，不得把服务端阶段目录
    标记为完整上线介质。未修改 `.env.local`、generated SDK 或 OpenCode 源码。

### 2026-07-31 - 补齐 LobeHub 企业客户端原生构建与介质准入

- Why:
  - 服务端阶段和 fork 已验证，但此前只要求外部“提供”Windows/Linux 客户端，没有锁定源码工具包、原生构建
    脚本、Linux 独立验收记录或三层统一校验；部署人员无法从现有仓库可复现地完成客户端介质，也容易把自报
    `Approved` 文本误当成真实审批。
- What:
  - 新增可复现客户端构建工具包、Windows PowerShell x64 构建/企业 Authenticode 签名脚本、Linux x86_64
    候选构建脚本和独立审批脚本；固定 Node `24.11.1`、Bun `1.3.2`、pnpm `10.33.0`、frozen lockfile 和 fork
    企业策略测试，不叠加 Cloud 源码。Windows 私钥只从证书存储使用，Linux 构建阶段只能产生 Candidate，不能
    自行产生 Approved。
  - 新增共享客户端制品合同；Windows 证据绑定 Valid 签名身份、摘要、版本、commit、x64 和执行禁用，Linux
    证据绑定客户端、目标 OS/内核、审批人及独立验收记录 SHA。验收记录强制登录、无公网依赖、运行期下载阻断、
    本地数据隔离和设备执行禁用通过，并关联真实变更单号。
  - `build-lobehub-artifacts.sh`、`package-release.sh` 与现场安装器复用同一门禁，完整介质新增 Linux 审批证据和
    验收记录；同步部署 README、文档索引、客户端构建手册、离线手册和安全标准。
- How:
  - TDD 覆盖 Pending、重复键、占位审批人、错误版本/commit/架构、客户端或验收记录篡改及失败验收结果；客户端
    合同、构建器、LobeHub-only ZIP、安装器、冷备份和 Shell 语法测试全部通过。使用 PowerShell `7.4` 官方
    运行时解析 Windows 脚本语法通过；服务端 `--validate-only` 仍通过，现有 server-only 目录被完整打包门禁按
    预期拒绝。
  - 从干净锁定 fork `ccd0400fbe934ba929de637a315d25e969977c76` 生成实际 53,451,706 字节客户端构建工具包，
    SHA-256 为 `e1c4bae1a315f246c3e4abe448a25e80d76186243661cb4e159fab284156fea9`；工作区制品和
    `~/Desktop/mimoagent/0709/lobehub-client-build-kit` 转运副本均校验 `OK`。
- Result:
  - 部署人员已有可校验、可复现的原生客户端构建与审批介质；未新增或修改 HTTP API、事件、数据库/Flyway、
    关系型 SQL、性能路径、fork 源码或本地环境文件。兼容性变化仅为完整离线包合同收紧，旧 server-only 阶段
    目录仍可做服务端演练但不能进入完整包。
  - 当前 Mac 没有企业 Authenticode 私钥，也不是原生 Linux x86_64 审批主机，因此尚未生成或伪造最终 Windows
    EXE、Linux Approved 客户端和完整企业 ZIP；这些外部结果、企业 Git remote 和目标现场验收仍是明确未完成项。

### 2026-07-31 - 生成 LobeHub fork 转运介质并补齐 server-only 离线定稿

- Why:
  - 已验证的约 2.2 GB server-only 目录在正式客户端返回后只能重新执行完整联网镜像构建，缺少不重拉镜像、
    不改原目录的离线定稿路径；独立 fork 也只有本机 checkout，没有可通过 U 盘导入企业 Git 的自包含介质。
- What:
  - 新增 `finalize-lobehub-artifacts.sh`：先验证 server-only 的精确 `SHA256SUMS`、版本锁、PostgreSQL 17、三张
    镜像 tag/ID、客户端未通过状态、执行禁用及普通文件边界，再复用共享 Windows/Linux 客户端合同，复制到新
    目录、写入成功状态并重建清单；兼容早期 platform.3 server-only 缺少 Linux 审批字段，原目录始终不修改。
  - 新增 `build-lobehub-fork-transfer.sh`：从干净、锁定的 `main` 和 annotated 内部 tag 创建只发布两个 ref 的
    自包含 Git Bundle，执行 fork 增量历史高置信凭据扫描、verify 与独立 clone，生成包内/包外双层 SHA-256、
    ref 清单和不携带 Git 配置/企业凭据的导入说明。
  - 新增两组 Shell 合同测试，并同步部署入口、架构、安全、客户端汇集、离线部署和独立 fork 企业导入手册。
- How:
  - TDD 先锁定旧 server-only 兼容、源目录不变、完整包二次准入、摘要篡改、Pending 审批、已通过状态、输出
    覆盖、输出与正式客户端/证据重叠、FIFO/特殊文件拒绝；Git 合成仓库覆盖最小 ref、annotated tag、独立
    clone、双层摘要、脏仓库、输出与版本锁重叠，以及“提交后删除但仍留在历史”的私钥拒绝。独立代码审查发现
    两处 `--force` 删除输入风险和历史凭据边界，红测复现并修复后复审无剩余 Critical/Important；新增测试与既有
    client contract、artifact builder、client kit、package、installer、backup 共八组通过。
  - 真实 fork `ccd0400fbe934ba929de637a315d25e969977c76` 生成
    `lobehub-fork-transfer-v2.2.11-platform.3.zip`（294,990,199 字节），SHA-256
    `a494d5a94b7db39fa584c2591b72fb01a1fa3bb61f426fbf59b93a2a4c2d0461`，已复制到
    `~/Desktop/mimoagent/0709/lobehub-fork-transfer`；从 ZIP 解包后的 Bundle 仅有 main/tag 两个 ref，并在无
    checkout 上下文中独立 clone、校验 branch/tag 解引用 commit 成功。
  - 真实 server-only 全部文件再次通过 SHA-256；`release.env` 与清单摘要分别为
    `1afe00deb9bc288bb5cf292fac7b3e197845fc51f7b2bd03c386a14bb3934c77`、
    `95c9ad0b4251fca29244a11cc3217bd911a1eb97bad1d8128a9a2afff469d038`，完整打包和缺客户端定稿均按预期失败，
    原目录摘要不变。
- Result:
  - 部署人员现在可以先转运/导入独立 fork，并在正式客户端返回后离线复用已冒烟的 server-only 介质；未修改
    HTTP API、RunEvent、数据库/Flyway/SQL、性能路径、fork 源码、`.env*`、generated SDK 或 OpenCode 源码。
  - 完整运行 ZIP 仍缺企业 Authenticode Windows x64 客户端及证据、原生 Linux x86_64 Approved 客户端与独立
    验收记录；企业 Git remote、目标 DNS/反代/网络/Flyway/备份回滚验收也仍需现场人员完成。上述外部结果完成
    前保持 `LOBEHUB_ENABLED=false`，不得把 server-only 或转运 Bundle 标记为完整上线交付。

### 2026-07-31 - 限制 LobeHub 开发监听并升级 platform.4 真实介质

- Why:
  - 真实执行根目录 `--with-lobehub` 后发现 fork 的 Next.js dev server 虽然使用 loopback 访问 URL，实际仍监听
    `*:3210`；这与本地开发合同及企业隔离边界不一致，也说明 platform.3 服务端、源码转运件和客户端构建工具包
    需要在同一修复提交上整体重建，不能继续交付旧摘要。
- What:
  - 独立 `/Users/huang/workspace/lobehub-platform` fork 的启动序列新增显式 `LOBEHUB_DEV_HOST` 处理，存在时向
    Next.js 传入 `-H`；平台开发 helper 固定生成 `LOBEHUB_DEV_HOST=127.0.0.1` 且继续保持临时 env mode `0600`。
    fork 发布提交为 `306dad5dc0968ed008f011d7fc07f12a606b21e1`，annotated tag 为
    `v2.2.11-platform.4`，平台版本锁、镜像 tag、测试夹具和稳定文档同步升级。
  - 从干净锁定 fork 重新构建约 2.2 GB 的真实 server-only 目录 `deploy/internal/dist-lobehub-server`；重新生成
    `.4` fork Git Bundle 转运 ZIP 和原生客户端构建工具包，并复制到固定
    `~/Desktop/mimoagent/0709/lobehub-fork-transfer`、`lobehub-client-build-kit` 目录。旧 `.3` 实物只保留在
    显式 archive 目录，禁止与当前版本锁混用。
- How:
  - TDD 先分别用 fork 单测和平台 Shell 合同复现缺少 hostname 参数/开发 env 的失败，再做最小修复。fork 检查
    36 项、Redis 原子状态机 3 项、数据库部门 Workspace/私有对象 110 项通过且完整 typecheck 通过；平台
    LobeHub/模型网关 Maven 定向测试 35 项、前端隐藏表单交接测试、开发脚本及 artifact/finalizer/client-kit/
    transfer/package/installer/backup 八组合同回归通过。
  - 使用未修改的 `.env.test`、JDK 21 真实执行完整
    `restart-dev-services.sh --profile test --env-file .env.test --with-lobehub`；后端 readiness 200、前端 200、
    LobeHub 307 固定回平台，`lsof` 确认只监听 `127.0.0.1:3210`。真实 `.4` 镜像演练完成 PostgreSQL 17
    migration、Redis ACL、私有 RustFS、readiness、离线/调度门禁、证明数据冷备恢复和二次部署验收。
  - fork 转运 ZIP 为 295,015,450 字节，SHA-256
    `e63e4cfa16ab7925d2298eb1e34312e362ec5237a361f4d1ffd5cc46c145dec7`；客户端构建工具包为
    53,454,987 字节，SHA-256 `080f0d214748458fcd9266a9ae7c60bd835a5107e9de895a0a61076fee6a825c`；
    server-only 的 `release.env` 与 `SHA256SUMS` 文件摘要分别为
    `788b869b4228c56d164ec87c378828e39885ffc17b33a107d730c1711fac6c45`、
    `ed1523efa8f4daaadc50b66a89dca47922c605fe9acdbe92ceb42b6c5550dc15`。
- Result:
  - 本地开发环境现可显式启动 LobeHub 且不暴露 wildcard 监听；部署人员已有同一 `.4` 锁定提交的真实服务端
    阶段介质、源码转运件、原生客户端构建工具包和可执行手册。本次不新增或改变 HTTP API、RunEvent、数据库/
    Flyway/MyBatis SQL、模型网关 wire、性能策略、generated SDK、OpenCode 源码或 `.env*`；安全变化仅为收紧
    本地监听，兼容性变化为当前 fork/介质版本整体升级到 `.4`。
  - 完整上线 ZIP 仍按设计失败关闭：尚需企业 Authenticode Windows x64 客户端及证据、原生 Linux x86_64
    Approved 客户端及独立验收记录、企业内部 Git remote 导入，以及目标现场 DNS/反代/网络、真实 Flyway
    history、备份恢复和回滚演练。Apple Silicon Docker 冒烟不能替代这些外部验收，完成前继续保持
    `LOBEHUB_ENABLED=false`。

### 2026-07-31 - 实现独立 Python 长程任务框架与代码变动影响分析

- Why:
  - 平台需要新增一套完全独立于 OpenCode/LobeHub 会话的固定流程长程任务入口；浏览器必须经 Nginx 同源直连
    Python，Java 只能复用认证权限、仓库/SSH 与模型网关能力，不能代理或持久化工作流业务。
  - 首期需要完整落地多仓库代码变动影响分析、可恢复执行、AG-UI 实时事件、局部重分析和 48 小时受控工作区，
    同时为后续场景保留注册扩展点但不能提前暴露未实现任务。
- What:
  - 新增 Python 3.12 `workflow-service`、`runner-controller`、`analysis-task`：AgentScope 白名单意图与综合、
    LangGraph 固定图/checkpoint、独立 PostgreSQL/Alembic、原生 AG-UI SSE、租约 Worker、版本化报告、追问/局部
    重分析以及到期清理；首版只注册 `code-change-impact-analysis`。
  - Java 仅新增 HMAC/nonce/session marker 保护的仓库授权、分支、一次性 checkout ticket、短期模型 grant 与超级
    管理员复核能力；模型网关接受 workflow grant，但未增加 conversation/message/task/run/report/event 对象或表。
  - 新增 TDesign `workflow-api-client`/`workflow-chat` 与 `/workflow-chat` 懒加载入口；Nginx `/workflow-api/`
    直达 Python。补齐固定 linux/amd64 三镜像、SBOM/许可证/摘要、纯 Docker 管理、Redis ACL、独立数据库及
    `DOCKER-USER` 受限网络交付合同。
  - 每任务一个非特权分析容器，源码只读，多智能体隔离 HOME/输出；Runner 使用目录 fd 与 `O_NOFOLLOW` 防止
    可写输出符号链接劫持，控制幂等缓存不挂载进容器。真实模型 grant 只经 `docker exec -i` stdin 进入容器内
    UID `10002` 的最小回环 relay；UID `10001` 的 Codex/OpenCode 仅持有本地 token。同任务智能体串行执行且每次
    执行后重启容器，Codex shell 环境白名单与 OpenCode `env -i` shell 阻断平台 grant 泄露及后台后代污染。
- How:
  - Python 全量 156 项（workflow 90、Runner 57、analysis-task 9）通过，`compileall` 与两份 `uv lock --check`
    通过；后端 21 模块 `mvn test` 全绿；前端
    108 个 Vitest 文件为 1726 passed / 1 skipped，前端全 workspace typecheck 和 agent-web production build
    通过。架构隔离、Nginx、离线包合同、Shell 语法和 `git diff --check` 通过。
  - 使用 OpenCode 1.18.4 真实二进制验证受控配置只暴露
    `test-agent-workflow/workflow-code-analysis`。本机尝试构建 analysis-task 时因已有工具镜像只含 OpenCode、
    缺少 Codex 而按设计失败关闭，未放宽 Dockerfile 或安全约束。
  - 在临时 Linux 容器中实测 relay 以 UID `10002` 持有 grant 时，UID `10001` 无法读取其 `/proc` 环境、内存、
    stdin 或发送信号，未授权 HTTP 返回 401，关闭 stdin 后端口退出；该 Docker 29.6.1 探针只证明当前隔离机制，
    不替代正式 Docker 18.09 门禁。
- Result:
  - 代码、HTTP/AG-UI 协议、模块/依赖/安全/数据库与企业离线部署文档已闭环；独立 Python 数据库不修改平台
    Flyway 历史，现有 OpenCode/LobeHub 会话、generated SDK 和 OpenCode 上游源码均未修改。
  - 正式发布仍必须在新工具镜像同时包含固定 Codex/OpenCode 后，于真实 Docker 18.09、真实模型网关、真实
    PostgreSQL/Redis 和至少两个跨应用仓库完成端到端验收；当前 Apple Silicon 的 Docker 29.6.1 不能替代该
    门禁。前端生产构建另保留工作流路由 chunk 约 2.86 MiB 的性能告警。

### 2026-07-31 - 收紧 LobeHub Redis、客户端证据与离线发布原子性并升级 platform.5

- Why:
  - 复核企业部署边界时发现 Agent Runtime 的直接 ioredis 连接绕过上游 `REDIS_PREFIX`，最小 ACL 下可能产生
    无前缀 key；Linux 最终审批也只绑定候选件和验收记录，未把原生构建人证据纳入完整介质。
  - server-only 定稿与 fork 转运虽然保护了显式输入路径，但仍需拒绝复制期间输入变化、协作发布并发和目标
    inode 被替换；现场还缺少可直接执行的最小 Redis ACL 创建程序及平台 HMAC/nonce 运行探针。
- What:
  - 独立 fork 为 Agent Runtime ioredis 增加 `lobehub:app:` 物理 key 前缀，返回业务 ID 时剥离前缀，并用游标
    `SCAN` 替代阻塞式 `KEYS`；发布提交为 `57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c`，annotated tag 为
    `v2.2.11-platform.5`。
  - 新增 `lobehub-redis-acl.sh`，从 `-@all` 开始显式授权运行命令，同时限制 key/channel；`check-redis` 证明
    前缀外 key/channel、Lua 越权和 `CONFIG/ACL/MODULE/FLUSHDB/KEYS/SCRIPT FLUSH` 均被拒绝。新增服务端
    `lobehub-platform-probe.mjs`，以固定撤销端点验证有效 HMAC 和同 nonce 重放 401，并纳入现场完整验收。
  - Linux 构建脚本强制稳定构建人身份，最终审批证据绑定构建证据 SHA 且构建人与审批人必须不同；构建、定稿、
    封包和安装统一要求 `linux-client-build-evidence.txt`。定稿与 fork 转运增加预存在父目录、相邻锁、复制前后
    摘要及输出 inode 复核；安装/封包拒绝符号链接及 FIFO/socket/device，并严格核对包内版本锁。
  - 平台版本锁、环境模板、测试夹具、部署入口、架构、安全、客户端构建、fork 转运和现场执行手册同步升级到
    `.5`，明确 ACL 管理员密码只进入 `REDISCLI_AUTH`、应用密码经 stdin 传给 `redis-cli -x`。
- How:
  - fork 定向 39 项、lint/check 和完整 typecheck 通过；平台 Redis ACL、HMAC 探针、客户端合同、构建工具包、
    artifact builder、定稿、fork 转运、封包、安装和冷备份合同测试全部通过。
  - 从干净锁定 fork 重建 2.2 GiB `linux/amd64` server-only 介质；应用镜像 ID 为
    `sha256:7f504fb3723402bd6b17165db02937964799beefc96ae22b34bd8ef60c5ce866`，`release.env` 与
    `SHA256SUMS` 文件摘要分别为 `3c94d96377fc192301be0851dbd3eeaf15965108eb20fa67e069e2ed9919cc03`、
    `39c17085322d130045bdbe94d969be9b32ca6abc758ce37b6597ec35a4c4dccf`。真实镜像烟测完成 PostgreSQL 17
    migration、显式 ACL/越权拒绝、HMAC nonce 重放、私有 RustFS、离线 scheduler、密钥隔离及冷备恢复。
  - `.5` fork 转运 ZIP 为 294,993,231 字节、SHA-256
    `2cbca71e90d0fa5925363c530538506e019227a56f0caeae8cf89e0d677843a2`；客户端工具包为 53,453,544 字节、
    SHA-256 `10fba3e98938252eb0ca7a3a40d0425d8f043ebe268ee267c2e019f3e2210ee1`，均复制到固定 0709 目录并校验。
  - 使用未修改的 `.env.test` 执行完整 `restart-dev-services.sh --profile test --env-file .env.test --with-lobehub`；
    后端 8080、前端 3000、LobeHub 3210 启动，真实平台 HMAC/nonce 探针通过，完整封包因缺正式客户端按预期
    失败关闭。
- Result:
  - 本地开发与服务端离线部署现使用同一 `.5` fork/镜像/手册，Redis 前缀和最小权限 ACL、平台服务身份以及
    原生 Linux 构建来源都形成可执行证据链。本次不新增或改变 HTTP API、RunEvent、数据库/Flyway/MyBatis SQL、
    模型网关 wire 或性能策略，未修改 `.env.local`、generated SDK 或 OpenCode 源码；兼容性仅收紧完整介质合同。
  - 完整上线 ZIP 仍按设计等待企业 Authenticode Windows 客户端、原生 Linux x86_64 客户端及双人审批证据；
    企业 Git remote、目标 Linux/Redis、真实平台 Flyway history、DNS/反代/网络隔离和备份回滚仍需现场验收。
    完成前保持 `LOBEHUB_ENABLED=false`，不得把 server-only 或 Bundle 宣称为完整上线交付。

### 2026-07-31 - 加固长程任务租约、取消与分析容器清理

- Why:
  - 对独立 Python 长程任务做交付前并发与清理审计时，发现 Worker 重启可能复用租约身份、取消与终态存在
    先读后写竞态、Runner 失败会遗留无到期时间的活动租约；分析容器还需要证明恢复/执行前身份约束、智能体
    输出互相隔离，并在私有目录拒绝宿主访问时仍能安全取消和到期删除。
  - fetch SSE 对瞬时断线没有续传重试，消费端错误结束后也未显式取消响应流；超级管理员切换 owner 时旧会话流
    可能发送迟到事件。
- What:
  - Worker 每进程使用逻辑 ID 加随机 owner token；运行终态改为单条 PostgreSQL 条件更新，原子校验 owner、
    租约期限、`RUNNING` 与未取消状态。丢失租约只撤销本 Worker 的模型 grant。取消对 `CANCELED` 幂等、拒绝
    改写其他终态，并将 Runner 删除结果持久收敛为 `EXPIRED/CLEANUP_FAILED` 供立即重试。
  - Runner 在创建路径前白名单校验不重复的 `codex/opencode`，每次执行与恢复时复核镜像、精确 bind、网络及
    资源/非特权约束；分析输出根不可列举，非当前智能体目录为 `000`。取消/到期清理先关闭活动状态和容器、
    淘汰排队请求，再用镜像内固定非跟随链接助手恢复分析 UID 私有目录权限，并执行不忽略错误的整树删除。
  - AG-UI fetch SSE 对网络、429、5xx 按 durable 游标重连，认证/协议/reducer 错误终止并取消底层流；owner
    切换前关闭旧连接。同步 Python/Runner/analysis-task、前端、API/AG-UI、安全和企业离线部署文档及打包合同。
- How:
  - Python 工作流、Runner 与 analysis-task 合并回归 183 项通过，`compileall` 通过；后端 21 模块
    `mvn test` 为 `BUILD SUCCESS`；前端 108 个测试文件 1730 passed / 1 skipped，串行 lint、typecheck 与
    production build 均通过。
  - 工作流架构隔离、离线包合同、Nginx 单/多后端路由与 `git diff --check` 通过；未修改依赖或 `uv.lock`，
    本机未安装 `uv`，因此本轮无法额外执行 `uv lock --check`。
- Result:
  - 取消不能再被迟到 Worker 终态覆盖；旧 Worker、排队分析、容器约束漂移、跨智能体输出读取和私有目录清理
    均有失败关闭与回归覆盖。未修改 Java 工作流边界、平台 Flyway/MyBatis SQL、generated SDK、OpenCode 源码、
    LobeHub 改动或 `.env*`，也没有创建分支。
  - 正式企业门禁仍未在本机满足：当前仅有 Docker `29.6.1/linux/arm64`，且现有工具镜像缺 Codex；仍须在真实
    Docker 18.09/linux/amd64、企业 Redis/PostgreSQL、真实模型网关和至少两个跨应用仓库执行端到端验收。
    WorkflowChat 路由生产 chunk 约 2.86 MiB 的既有性能告警仍保留。

### 2026-07-31 - 补齐分析容器重启复核、冻结 LFS 与范围补充闭环

- Why:
  - 继续按原方案审计时发现：智能体结束后的容器重启只复核基础非特权参数，未携带冻结 spec 复核镜像、挂载、
    网络和资源，也未拒绝重启后仍为停止态；Git LFS 使用了多余分支参数且只检查根 `.gitattributes`；局部重分析
    找不到候选时前端处于 `WAITING_INPUT`，但既没有可用输入卡，普通发送框也被禁用。最终复核还发现单项范围纠正
    会覆盖其余范围，SSE 新投影会被较晚的 POST 回包覆盖且终态/重连仍残留旧输入卡，以及重启复核和强制删除
    同时失败后两侧工作区仍可能遗留 `ACTIVE`。
- What:
  - Runner 将冻结 `AnalysisContainerSpec` 从 service 传到 analyzer executor，并在每次工具结束重启后复核运行态、
    镜像、精确 bind、网络、CPU/内存/PID/nofile/tmpfs 和非特权约束；漂移时删除精确容器并失败关闭。
  - LFS 从冻结提交读取根目录及嵌套的受跟踪 `.gitattributes`，在 detached `targetHead` 上按 `git lfs pull`
    的单 remote 语法完整拉取当前 ref。工作流前端在 `SCOPE_DISAMBIGUATION` 无候选或候选不准确时保留范围输入卡，
    通过 `structuredInput.scopeSelectors` 恢复原 run，并隔离候选按钮样式；逐项纠正时合并已解析项和其余待补充项。
  - 容器重启复核失败后强制删除；删除也失败时保留不可忽略的危险标记，Runner 本地状态立即到期并转为
    `CLEANUP_FAILED`，Worker 保留失败后同步收敛 PostgreSQL 租约和 durable 工作区事件，供两侧清理循环重试。
    控制库短暂失败最多重试三次；持续失败不终态化 run，停止心跳后由租约接管重放幂等流程。
  - 前端以 AG-UI 投影 revision 保护所有生命周期事件，输入请求或终态先于 POST 回包时不回退状态；实时 reducer
    与 Python 断线快照均在开始、完成或失败时清空失效输入，终态后不能再次提交旧恢复卡。
  - 同步 Runner、analysis-task、AG-UI、前端包、安全和企业离线验收文档；没有修改 Java 工作流边界、数据库结构、
    HTTP 路径、事件类型、依赖锁、generated SDK、OpenCode 源码或 `.env*`。
- How:
  - 按 TDD 先复现停止容器未拒绝、重启未携带冻结 spec、嵌套 LFS 未执行和无候选输入死路，再补实现；Python
    三模块 193 项、`compileall`、两份 `uv lock --check` 通过。
  - 前端工作流 12 项及全量 108 个文件 1733 passed / 1 skipped，workspace lint/typecheck 和 agent-web 生产构建
    通过；后端 21 模块 Maven 测试、工作流架构、Nginx 直连和离线打包合同校验通过。
- Result:
  - 本机可完成的代码、契约和自动化验证已闭环；没有覆盖当前并行的 LobeHub/工具箱未提交改动，也未创建分支。
  - 正式企业验收仍未完成：本机是 Docker `29.6.2/linux/arm64`，现有 `linux/amd64` 工具镜像只有 OpenCode、
    缺少 Codex；仍需真实 Docker 18.09、企业 Redis/PostgreSQL/模型网关和至少两个跨应用仓库端到端验收。
    WorkflowChat 路由约 2.86 MiB 的生产 chunk 告警继续作为性能风险保留。

### 2026-08-01 - 修复工作流开发路由误回退 SPA HTML

- Why:
  - 本地访问`/workflow-chat`时，Vite没有注册`/workflow-api/**`代理，请求被SPA fallback以
    `200 text/html`返回`index.html`；`workflow-api-client`随后直接执行JSON解析，页面暴露
    `Unexpected token '<'`。同时，本机独立Python 8090未监听时，Vite默认返回纯文本502，也缺少稳定诊断。
- What:
  - 新增`workflow-dev-proxy.ts`并接入Vite，开发态默认把工作流前缀直接转给
    `http://127.0.0.1:8090`，支持`TEST_AGENT_WORKFLOW_API_URL`覆盖，仍不经过Java。
  - `workflow-api-client`统一校验JSON成功信封，并拒绝JSON接口、Markdown下载和AG-UI SSE收到的
    HTML fallback；纯文本网关失败收敛为稳定`WorkflowApiError`。错误只携带HTTP状态和Content-Type，
    不回显响应正文、Bearer或原生JSON解析异常。
  - 同步前端README/包边界、模块图、前端规范和workflow HTTP边界文档；新增开发代理、HTML fallback、
    纯文本502、报告下载及SSE协议回归。
- How:
  - 按TDD先复现两个失败：Vite代理模块缺失，以及`200 text/html`抛出原生`SyntaxError`；扩展到报告下载
    和AG-UI SSE后再次确认旧实现错误接受HTML，再补实现。
  - 工作流前端定向20项、客户端/代理10项通过；全量前端109个测试文件为1738 passed / 1 skipped，
    agent-web typecheck、production build、AI文档门禁和`git diff --check`通过。
  - 用临时8090 JSON上游经真实运行中的Vite访问`/workflow-api/v1/me`，确认返回
    `200 application/json`与`data`信封；停止临时上游后真实路由返回502，不再回退HTML。
- Result:
  - 截图中的JSON解析错误已消除；开发态路由边界与生产Nginx一致。独立Python未启动时页面会明确显示
    `工作流服务暂不可用（HTTP 502）`，启动8090后请求可正常直达Python。
  - 本次不修改HTTP路径、AG-UI事件类型、Python/Java服务、数据库、依赖锁、`.env*`、generated SDK、
    OpenCode源码或LobeHub改动；仍需按`workflow-service/README.md`独立启动Python API/Worker/Runner以执行任务。

### 2026-08-01 - 固化本地工作流控制面启动设计

- Why:
  - 开发代理修复后真实暴露 Python 8090 未监听；进一步诊断确认本地还缺独立工作流数据库/角色、Redis 只读
    ACL、Java/Python 配对 HMAC、Runner 公钥和 API/Worker 进程，既有交付只有 Linux 企业部署链路。
  - 当前 macOS Docker Desktop 无法证明 Runner 要求的宿主机 `DOCKER-USER`/`iptables` 出站隔离，不能通过
    启动本机分析容器或伪造网络标签来消除 502。
- What:
  - 与用户确认采用“本地工作流控制面 + 可选外部 Linux Runner”：默认重启初始化 PostgreSQL、Redis ACL、
    临时密钥并启动 Python API/Worker，Runner 缺失不影响控制面就绪，分析 run 明确失败关闭。
  - 设计规定所有生成凭据只写 `.tmp/dev-services/workflow/`，不修改 `.env.test/.env.local`；保留
    `--without-workflow` 兼容开关，外部 Runner 配置必须成组提供且本地不读取其私钥。
- How:
  - 核对 workflow-service、runner-controller、analysis-task、数据库 bootstrap、Redis ACL 和分析网络脚本，确认
    生产安全约束与本机能力差异；完成占位符、矛盾、范围和歧义自检并形成书面规格。
- Result:
  - 设计已固化于 `docs/superpowers/specs/2026-08-01-workflow-local-control-plane-design.md`，等待用户书面复核后
    再进入实施计划和测试驱动实现。本次未修改 API、事件、数据库 migration、生产部署、`.env*` 或现有并行改动。

### 2026-08-01 - 修复 LobeHub 本地入口未启用并补齐失败关闭

- Why:
  - `restart-dev-services.sh --with-lobehub` 只生成平台 HMAC 并启动 fork，没有初始化平台数据库中的四项
    LobeHub 公共参数，导致已登录用户点击“通用问答”仍收到 `FORBIDDEN：通用问答功能未启用`。
  - 复核还发现 owner 解析或审计失败、fork readiness 失败时，既有 `LOBEHUB_ENABLED=true` 可能残留。
- What:
  - 新增仅在 `test/local` profile 且显式开发开关下装配的公共参数 bootstrap；严格拒绝非回环 PostgreSQL 和
    非固定 HTTP 回环聊天 origin，通过既有通用参数管理服务审计写入 base URL、虚拟邮箱域和唯一 owner。
  - 新增开发 owner 解析器：优先校验显式/已有 owner，否则只选择唯一的状态正常、部门非空超级管理员；零个或
    多个候选失败关闭。入口每次先关闭、配置完成后最后启用，异常时按当前值补偿关闭。
  - LobeHub helper 生成 bootstrap 配置但不修改 `.env.test/.env.local`；fork 启动失败时根重启脚本以仅关闭模式
    重启一次后端并留下审计。同步后端、前端、架构与开发流程文档。
- How:
  - TDD 先复现缺少 helper bootstrap、占位值未替换、非回环误写、IPv6 回环、owner 不唯一、启用审计失败和
    已启用状态 owner 失败，再补实现；`test-agent-app -am test`、`test-agent-integration -am test`、开发脚本
    校验与 Shell 语法均通过。
  - 使用未修改的 `.env.test` 和 JDK 21 执行完整 `restart-dev-services.sh --profile test --env-file .env.test
    --with-lobehub`；后端 readiness 为 UP，LobeHub 3210 返回 307 到固定 `/lobehub/launch`，临时 env 权限为
    0600。真实本地 PostgreSQL 中四项参数正确，启用审计顺序为 `true→false→true`。
- Result:
  - 本地显式 LobeHub 模式会自动开放可用入口，初始化、owner、审计或 fork 启动失败时不会保留已启用入口；
    默认重启仍不启动 LobeHub，prod/企业公共参数门禁不变。
  - 本次不新增/变更 HTTP API、事件或数据库结构/Flyway/MyBatis SQL，不修改 `.env*`、generated SDK、
    OpenCode 源码或企业离线介质；只改变本地开发启动行为和对应审计数据。

### 2026-08-07 - 现代化顶部上下文选择器与下拉菜单

- Why:
  - 顶部“应用 / 工作空间 / 版本”三个独立白底按钮视觉割裂，层级、品牌识别与菜单展开反馈不足；用户确认采用
    “精密上下文舱”方案，并要求把三个下拉菜单一并纳入优化。
- What:
  - 在 `FigmaShell.vue` 中将三个入口合并为 34px 高连续上下文舱，增加 11px 圆角、发丝边框、轻阴影、
    内部分隔线与 3px 工行红信号条；分段补齐悬停、键盘焦点、展开和禁用态。
  - 三个下拉菜单统一为 12px 圆角、双层轻阴影、6px 内边距、8px 项目圆角与柔红选中态；保留原菜单内容、
    数据来源、加载状态、禁用语义和切换回调。同步前端总 README、agent-web README 与包级说明。
- How:
  - 按 TDD 先新增上下文舱结构与视觉契约测试并确认失败，再补实现；`FigmaShell.test.ts` 57 项、前端全量
    115 个测试文件 1839 passed / 1 skipped，agent-web typecheck 与 production build 通过。
  - 使用真实本地页面完成 2048px 桌面端、880px 窄屏、下拉展开和键盘 Enter 验收：上下文舱高度 34px、
    圆角 11px，三个分段无重叠；菜单圆角 12px、双层阴影生效。构建的 chunk size 告警及 jsdom Canvas
    提示与改动前基线一致。
  - 收尾时前端全量测试在并行改动后的工作树上仍为 115 个文件 1840 passed / 1 skipped；一次 agent-web
    typecheck 曾被本次范围外 `AgentWorkbench.vue` 的瞬时未定义引用阻断，本次未代改该并行文件；随后同一工作树
    的 production build（含 `vue-tsc --noEmit`）通过。
- Result:
  - 顶部上下文切换形成连续、紧凑且可辨识的现代工作台控件，同时保持原功能与响应式布局兼容。
  - 本次不变更 API、RunEvent、数据库、性能路径、安全边界、`.env*`、generated SDK 或 OpenCode 源码；
    未覆盖工作区中并行存在的其他前后端改动。

### 2026-08-07 - 实现最后一条用户消息原生撤销重发

- Why:
  - 平台原有失败重试只在前端倒计时后取消 Run 并重新发送，没有调用 OpenCode `session.revert`，旧回答、工具结果、
    文件改动和模型上下文仍可能残留；定时任务也缺少可恢复的自动重发状态、次数和页面标识。
  - 原生供应商 `session.status=retry` 与撤销重发语义不同，必须由后端统一状态机处理人工入口、定时自动入口、
    跨 Java 路由、投递响应丢失和服务重启窗口。
- What:
  - agent-runtime/OpenCode 适配层新增读取可重放用户轮次、revert/unrevert 和稳定消息探测；保留 text/file/agent/
    subtask、模型、Agent 和 variant，并继续把 OpenCode 1.18.4 快照与 generated SDK 作为只读边界。
  - 新增 MyBatis XML/Flyway `run_resends` 状态机与会话锁，Redis 限时保存精确重放输入；手动 API 和根
    `session.error` 定时自动入口共用同一服务，自动次数最多 3 次、等待 1/2/4 分钟，人工重发不重置自动额度。
  - 替代消息受理后清理源 Run 的消息、工具事件、Redis 明细和 child scope，历史查询按已提交重发关系过滤；
    Run/消息/运行态增加可选 resend 元数据和 scheduled/started/failed 事件。前端增加资格按钮、倒计时锁、
    自动/手动标签及原子接管，删除旧 60 秒取消后重新 startRun 的逻辑。
  - 自动扫描复用已注册的每分钟夜间任务与公共 Java 路由/转发器。验证中修复两处恢复边界：WAITING 替代 Run
    停止必须在尚无生产路由时本机处理；预留替代 Run 启动只绕过自身重发锁，普通消息仍严格受锁保护。
- How:
  - JDK 21 完整后端 21 模块 `mvn test` 为 `BUILD SUCCESS`；重发 domain/runtime/API/persistence、调度注册、
    取消路由、预留 Run 与 PostgreSQL migration 集成测试均通过。前端全量 115 个测试文件为 1839 passed /
    1 skipped，typecheck 和 production build 通过。
  - 真实三服务 OpenCode E2E 验证旧 user/assistant、工具与平台明细消失、文件改动回退后由新轮次重建、新消息 ID
    生效，目标用例 1 passed；后端、manager、前端及本地 PostgreSQL/Redis/MySQL 最终均恢复健康。
  - 本地真实 PostgreSQL 已成功执行 `V20260807190000__create_run_resends.sql`，Flyway 记录成功；源码与 persistence
    JAR 内 migration SHA-256 同为 `ca044d9819c7259b62e29243e9d72a06a2f01a532f1803f37e117de1d2f5d83d`。
- Result:
  - 人工和定时任务均使用完整 OpenCode 原生回退语义；自动等待可停止、会话写入口受锁、重启与响应丢失窗口按
    稳定替代消息 ID 恢复，新增 PostgreSQL 状态表和事件不保存 prompt、模型回答或供应商响应正文。
  - HTTP API、RunEvent、数据库、安全、OpenCode 边界、测试场景、后端/前端 README/PACKAGE 与用户手册已同步；
    DTO/事件字段均为 additive，旧客户端缺失字段时继续按普通消息展示。未修改 `.env*`、generated SDK 或
    OpenCode 源码，也未创建分支。
  - 本机已执行的 migration 文件自此不可改写；企业发布前仍须逐一核对所有目标环境 `flyway_schema_history`、
    checksum 和已知历史升级路径，本机验证不能替代企业 PostgreSQL/双后台滚动升级验收。
### 2026-08-07 - 支持批量生成子条目测试案例

- Why:
  - 用户需要从对话输入区批量选择当前工作空间的需求子条目，分别创建独立立即执行会话或定时任务，并为后续运营统计记录批量来源；候选口径必须与输入 `#` 完全一致。
- What:
  - 输入卡悬浮或聚焦时显示批量入口，70vw × 70vh 弹层直接复用四阶段 `#` 候选，支持搜索、最多 50 项多选、可编辑要求、四路并发、逐项状态、部分失败继续和仅失败重试；局部文件上下文不修改当前输入、附件或 Session。
  - 立即模式新增幂等批量 Session API；定时模式扩展可选 `batchContext` 并复用公共时间选择器。`sessions` 新增批量标识与两个归因 ID、约束及索引，专用 MyBatis XML 使用 PostgreSQL 事务级 advisory lock 串行化同用户同条目创建。
  - 同步 HTTP、RunEvent 边界、数据库、模块图、前后端 README/PACKAGE 和用户手册；未新增工作空间 RPC、RunEvent 或运营报表。
- How:
  - 前端全量 118 个 Vitest 文件 1851 passed / 1 skipped，typecheck、production build 及聚焦 Chromium E2E 通过；E2E 覆盖两个子条目的独立 Session/Run、首次失败仅失败重试和两个独立 Session/Task。
  - 后端相关服务/API/MyBatis/真实 PostgreSQL 测试 27 项通过，企业基线及已知历史分叉 Flyway 测试 8 项通过，21 模块 `mvn clean package -DskipTests` 成功；迁移源码、persistence JAR 和最终应用 JAR 的 SHA-256 均为 `42ec1917deb16d96b910f81a0a4739487500453f90dc742e516822f800fdd6e3`。
- Result:
  - 批量项保持 `MANUAL` / `SCHEDULED_TASK` 原触发来源，同时可通过 `sessions.batch_mode` 统计；日志只记录安全批量 ID、状态、数量、traceId 和错误码，文件正文与用户要求不进入日志。
  - 所有 worktree 的候选 migration 已复核，本次 `V20260807230000` 高于原工作区未提交的 `V20260807190000`；共享或企业环境执行前仍必须核对真实 `flyway_schema_history`，未知版本或 checksum 时停止发布。
  - 目标 Playwright 全套在错误复用原工作区 3000 服务时产生伪失败；改用本 worktree 3001 后本功能 E2E 与三个重试回归通过。另有一个 HEAD 已存在的“只读历史 textarea 应 disabled”场景与基线模板不一致，本次未扩大范围修改。
  - 合并到 `codex/release-enterprise-20260801` 后，本功能立即执行、失败项重试和批量定时 E2E 通过；发布分支原有三条重发 E2E 仍断言旧的取消/重新 `startRun` 流程，而发布分支实现已改为原生 `createRunResend`，相关测试段落和重发处理函数均未被本次合并改动，需由重发功能后续单独校准。

### 2026-08-07 - 修复撤销重发边界误判与页面接管

- Why:
  - 首轮发送前签发的页面上下文不含远端 binding，首轮完成后继续复用该旧 token 会让重发校验误报“会话缺少可恢复的远端边界”，尽管权威 binding 和消息边界已经存在。
  - 重发 API 成功后页面仍把用户轮次绑定在已终态的源 Run，时间线因此优先展示“已完成”而没有运行状态 Dock；后到 `run.resend.started` 又按源 Run 删除了用户消息，造成页面空白或不刷新。
- What:
  - `ConversationContextApplicationService` 在 `peek/touch` 两侧校验无 binding 快照是否已被同节点权威 binding 淘汰；命中时失效旧 token 并返回既有 `CONVERSATION_CONTEXT_EXPIRED`，前端沿原幂等键重签重试。
  - agent-chat 新增内部 `run.resend.requested` 接管动作：接口返回后立即把源用户轮次、Todo owner 和状态投影切到预留替代 Run；`started` 只清理旧回答、工具、Todo、Diff、失败卡、流式 overlay 与 child scope，保留用户轮次、清除旧消息标识并等待新远端 ID 原位接管。
  - 替代 user message 合并时保留 `sourceType/sourceRefId/resend`，`started` 同步把重发标记收敛为 `DISPATCHED`，避免定时来源和等待倒计时残留。
- How:
  - TDD 先复现旧 token 边界误判、状态栏未运行、`started` 删除用户轮次和等待标记残留，再补后端与 reducer/Workbench 修复；后端 10 模块 790 tests、前端 118 文件 1851 passed / 1 skipped、15 项 workspace typecheck 和 production build 全部通过。
  - Chromium 页面回归验证点击后立即出现 running Dock，`started` 后旧回答消失但用户消息保持一条，替代远端 user ID 和新回答原位接管；本地后端、前端、OpenCode manager 与工作流服务重建重启后再次通过，readiness 为 `UP`。
- Result:
  - 用户现在可在无需刷新页面的情况下看到重发运行状态和新结果，原用户请求不会被回退事件误删；首轮后旧上下文会自动走一次安全重签，不再误报缺少远端边界。
  - 本次仅修正既有 API/RunEvent 的校验与前端投影，不新增或变更外部 API、事件 wire name、数据库/Flyway、SQL、权限或日志字段；已同步 HTTP 行为、后端/前端 README/PACKAGE 与用户手册，未修改 `.env*`、generated SDK 或 OpenCode 源码。

### 2026-08-07 - 修复重发等待阶段旧回答延迟隐藏

- Why:
  - 重发 API 返回后虽然用户轮次和状态栏已经接管到替代 Run，但旧 assistant、Todo 和 Diff 仍保留在时间线，直到后端发出 `run.resend.started` 才删除；回退或投递稍慢时，用户会持续看到上一轮回答，误以为页面没有刷新。
- What:
  - `opencode-like` 派生状态识别用户消息上的 `WAITING/REVERTING/REVERTED` 重发元数据，在等待阶段立即从页面隐藏源 Run 的回答、工具、Todo、Diff 和关联卡片，同时保留 reducer 原数据。
  - 原生回退开始前若收到 `run.resend.failed`，失败状态解除隐藏并恢复旧内容；收到 `started` 后仍沿用既有原子清理和替代消息 ID 接管，不改变后端状态机。
- How:
  - TDD 先确认单测仍投影 `assistant-part/diff-summary`、Chromium 页面仍找到旧回答，再补最小派生过滤；修复后等待阶段只投影用户消息和 running 工作状态，失败分支恢复旧回答、Todo 与 Diff。
  - 前端全量 118 个测试文件 1852 passed / 1 skipped，15 个 workspace typecheck、production build、agent-chat 68 项聚焦测试和 Chromium 重发页面回归均通过。
- Result:
  - 平台接受撤销重发请求后，旧回答与过程信息立即从页面消失，不再等待新回答完成；回退前失败仍可安全恢复原轮次，避免乐观隐藏导致内容丢失。
  - 本次不新增或变更 HTTP API、RunEvent wire name、DTO、数据库/Flyway、SQL、安全或后端实现；已同步 HTTP 行为、agent-web/agent-chat README/PACKAGE 和用户手册，未修改 `.env*`、generated SDK 或 OpenCode 源码。

### 2026-08-08 - 修复批量子条目重复发起并增加会话创建进度页

- Why:
  - 原批量弹层只在编排运行期间依赖父组件 `running` 禁用按钮；编排完成后会恢复原选择和提交按钮，再次点击会生成新的 `batchId/itemRequestId`，因此后端幂等约束会把它识别为合法新批次，无法阻止同一弹层内重复发起。
- What:
  - 批量弹层改为“选择配置 → 会话创建情况”两阶段，首次点击在本地同步锁定并移除可编辑表单；进度页逐项展示 Session、Run 或定时任务创建状态，不自动关闭，失败项支持单笔和批量重试，定时容量冲突只为冲突项重选时间。
  - 立即 Run 启动失败保留已创建 `sessionId`，重试继续复用原批次、条目和 Run 请求 ID；顶层校验拒绝会原样回到选择页。运行中禁止关闭，空闲时若仍有条目未创建 Session 则二次确认；确认关闭后显式清空编排身份，重新打开恢复空选择和默认要求。
- How:
  - TDD 先复现同一渲染帧重复提交、Run 失败丢失 Session、批次 reset 缺失、关闭无确认和旧 E2E 在同一弹层继续发起定时批次，再补状态门禁、父级确认和编排重置。
  - 前端全量 118 个 Vitest 文件 1860 passed / 1 skipped，15 个 workspace typecheck、production build 和目标 Chromium 批量 E2E 通过；E2E 覆盖立即失败单笔重试、主动关闭、重新打开全新定时批次及不同 `batchId`。最终复跑时 Playwright 1228 缓存被外部清理且 CDN 下载超时，改用不入库的临时配置指向本机 Google Chrome 取得同一用例 1 passed，临时文件已删除。
- Result:
  - 同一弹层生命周期只能受理一次初始批次，失败重试保持幂等；用户主动关闭才结束本批次，再次打开允许对同一子条目发起全新批次，符合新的产品边界。
  - 仅修改前端交互、测试和稳定文档，不变更 HTTP API、RunEvent、数据库/Flyway、后端、性能/安全契约、`.env*`、generated SDK 或 OpenCode 源码；生产构建仅保留既有 chunk-size 警告，Vitest 仅保留既有 jsdom Canvas 提示。

### 2026-08-08 - 优化批量定时选择与执行入口

- Why:
  - 批量弹层高度不足，首次定时选择没有独立关闭入口；定时区域打开后立即执行与定时执行同时存在，容易造成操作混淆。
- What:
  - 批量弹层从 `70vw × 70vh` 调整为 `70vw × 90vh`；定时选择标题区新增小型关闭图标，关闭后保留子条目与生成要求，同时清空未提交的时间及校验状态。
  - 定时选择打开时隐藏“选择定时”和“立刻执行”；只有已选时间且容量分配成功时，才在原主操作位显示“定时执行”。进度页、失败重试和批次幂等逻辑保持不变。
- How:
  - 按 TDD 先确认旧高度、缺少关闭入口及动作并存的失败，再补最小局部状态与模板条件；组件测试 9 项通过，目标 Chromium 批量 E2E 1 项通过。固定 Playwright 缓存缺失时使用未入库的临时配置指向本机 Chrome，验证后已删除。
  - 前端全量 118 个 Vitest 文件 1861 passed / 1 skipped，15 个 workspace typecheck 和 production build 均通过；全量首轮曾出现 Mermaid 懒加载用例波动，单独复跑及完整第二轮均通过。
- Result:
  - 首次批量定时操作现在只有一个明确的主执行入口，并可安全退出定时选择；已同步 frontend、agent-web、PACKAGE 和用户手册说明及浏览器回归。
  - 本次仅修改前端交互、测试和稳定文档，不变更 HTTP API、RunEvent、数据库/Flyway、后端、性能或安全契约，也未修改 `.env*`、generated SDK 或 OpenCode 源码。

### 2026-08-08 - 修复历史会话待答问题重复显示

- Why:
  - 历史 Session Tree 保留 OpenCode 远端根 sessionId，当前 pending 接口使用平台 sessionId；现有合并只按 sessionId 替换，使同一 `requestId` 暂存两份，后续 SSE 根会话投影后同时进入当前会话并显示重复问题卡片。
- What:
  - `replaceRootSessionInteractions` 改为用稳定 `requestId` 让实时根请求覆盖历史远端别名，同时保留不同请求 ID 的子 Agent 交互、接口失败时的历史降级和空实时快照的根 scope 清理语义。
  - agent-web 回归覆盖历史树、实时 pending 与 SSE ask 回放的完整重复链路，并同步历史交互校准说明。
- How:
  - TDD 红测稳定得到 1 failed / 100 passed，唯一多余项为 `que_1 / ses_remote_root`；最小修复后聚焦测试 101 passed。
  - 前端全量 118 个测试文件 1864 passed / 1 skipped，15 个 workspace typecheck 和 production build 通过；Vitest 仅有既有 jsdom Canvas 提示，构建仅有既有 plugin timing 与 chunk-size 警告。
- Result:
  - 从会话列表恢复待答问题时，同一请求只保留一张可交互卡片，子 Agent 请求与失败降级保持不变。
  - 本次不新增或变更 HTTP API、RunEvent wire name、DTO、数据库/Flyway、后端、性能、安全或兼容性契约，未修改 `.env*`、generated SDK 或 OpenCode 源码。

### 2026-08-08 - 批量定时执行智能推荐时间段

- Why:
  - 批量弹层打开定时选择后夜间时段默认全未选，用户需自行判断选几个时段、哪些排队最少；一批选较多子条目时单时段容量常不足以覆盖，易反复试错或触发容量不足提示。夜间时段接口已返回每个时段的 `reservedCount`（排队任务数）与 `capacity`，单任务已有“未满且待执行任务最少”推荐，批量场景缺少据此自动推荐并选中的能力。
- What:
  - 新增纯函数 `recommendBatchScheduleTimes`：按 `reservedCount` 升序、`slotStart` 升序贪心累加余量直到覆盖 `itemCount`，统一容量下“已排队最少”即“余量最多”，自然用最少时段覆盖全部子条目；总余量不足时返回全部可用时段交由既有容量提示兜底。
  - `BatchTestCaseGenerationDialog.vue` 新增 `scheduleAutoRecommended` 标记与 `recommendedNightTimes` 计算属性；打开定时选择、切回夜间模式、所选子条目数或时段数据变化（仍为自动推荐时）自动推荐并选中；手动改选、关闭、重置、切到测试时间模式后清空标记，保留用户手动选择不覆盖。
  - “定时执行”按钮上方在夜间自动推荐时显示“已根据选择的子条目数量智能推荐定时执行时间段”；总余量不足时按钮不出现并展示既有容量提示。进度页容量冲突重选区域与测试时间模式不参与自动推荐。
- How:
  - TDD 先补 `recommendBatchScheduleTimes` 单测（单时段可覆盖、多时段补齐、总余量不足返回全部、忽略已满/无时段/无子条目），再改组件并修正被自动推荐改变的两条组件测试与一条 workbench 批量定时 E2E。
  - `batch-test-case-generation.test.ts` 与 `BatchTestCaseGenerationDialog.test.ts` 共 19 项通过；前端全量 118 个 Vitest 文件 1870 passed / 1 skipped，15 个 workspace typecheck、lint、production build 与目标 Chromium 批量定时 E2E 均通过。
  - 全量 workbench E2E 首轮有 5 项失败（release-disabled/超管入口、聊天重试 3 项、历史切换只读），用 `git stash` 在干净 HEAD 复跑同样 5 项失败，确认与本改动无关的预存在失败。
- Result:
  - 批量定时打开即按所选子条目数与排队任务数自动选中可覆盖时段并在按钮上方提示推荐来源；用户手动改选后保留其选择，关闭重开按最新数量重新推荐。
  - 仅修改前端交互、测试与稳定文档（frontend README、新增设计文档），不变更 HTTP API、RunEvent、数据库/Flyway、后端、权限、安全、性能路径、`.env*`、generated SDK 或 OpenCode 源码。

### 2026-08-08 - 合并内部模型可观测并校准 Flyway 迁移顺序

- Why:
  - 当前发布分支本地领先 26 个提交、远端领先 19 个提交；远端新增的三条内部模型可观测迁移版本低于本机已经成功执行的 `V20260807190000` 与 `V20260807230000`，直接合并会在保持 `outOfOrder=false` 时阻断从当前基线升级。
  - 用户明确确认远端三条候选迁移从未在任何环境执行，因此可在首次集成前重新编号；已执行迁移及其 checksum 必须保持不变。
- What:
  - 合并远端内部模型调用记录、聚合、探活、保留任务、管理 API 与前端面板，并解决 shared-types 包说明的唯一内容冲突，保留本地批量/重发与远端可观测两侧说明。
  - 将三条未执行迁移依次编号为 `V20260808143300`、`V20260808143301`、`V20260808143302`；SQL 字节保持不变，SHA-256 分别为 `f214dfd0d4f26de830452d9f4121bc938cf031e4867555d5248e159d99377084`、`de7188e3ba5d01148a655dbc238783cf7881abf168bd7b6e422c9f2fa118a5c3`、`46f0a8e687f59c037a7e02cb1f9ba3db4893633ba20edd67d4ae75f0b6fd0d9e`；同步数据库文档与迁移集成测试。
  - 新增真实 PostgreSQL 当前基线升级用例，覆盖 `V20260807230000 → V20260808143302` 且保持 `outOfOrder=false`；同时修复开发脚本校验对新版 macOS 自带 `/usr/bin/jq` 的错误假设，以及本地模型 mock 在监听前被反向 DNS 阻塞的问题。
  - 根据提交前独立审查补齐观测闭环：转发前未知供应商/模型统一使用 `unknown`，分块超限异步失败补记 `REQUEST_INVALID`，探活状态写入真实端到端耗时；开发脚本的单一 EXIT cleanup 同时回收 mock PID 与临时目录。
- How:
  - 专项迁移测试先稳定复现三条低版本迁移无法应用，再在重新编号后 1 项通过；后端 21 模块 `mvn test` 全部成功，迁移兼容测试类 9 项通过，`mvn package -DskipTests` 成功。
  - 前端全量 119 个 Vitest 文件 1872 passed / 1 skipped，workspace typecheck 与 production build 通过；`tools/verify-dev-scripts.sh` 在两处环境差异修复后完整通过。
  - 三条迁移在源码、persistence JAR 和最终应用 JAR 中的 SHA-256 一致；既有 `V20260807190000` 与 `V20260807230000` 源码哈希仍分别为 `ca044d9819c7259b62e29243e9d72a06a2f01a532f1803f37e117de1d2f5d83d`、`42ec1917deb16d96b910f81a0a4739487500453f90dc742e516822f800fdd6e3`。
  - 审查问题先由 3 项聚焦回归稳定复现，再全部转绿；真实 PostgreSQL Testcontainers 2 项通过，其中失败记录经 Spring 事务代理同时写入明细与小时聚合；修复后受影响依赖链 19 模块全量测试成功（API 503、persistence 272 / skipped 18），21 模块跳过测试打包再次成功；自定义 `TMPDIR` 回归确认脚本不再遗留 `test-agent-dev-scripts.*` 临时目录。
- Result:
  - 当前本地数据库历史可在不启用 `outOfOrder`、不执行 `repair`、不改写已执行 migration 的前提下顺序升级；内部模型可观测的 HTTP API、数据库表/字段及前端类型均为 additive，转发前失败、分块超限与探活耗时也能完整入库，并已同步 API、数据库、架构、测试及模块 README/PACKAGE 文档。
  - 未修改 `.env*`、generated SDK 或 OpenCode 源码，也未新建分支。企业发布前仍须逐一核对每个目标环境的 `flyway_schema_history`、checksum 和已知历史升级路径；本次确认与真实 PostgreSQL 测试不能替代目标企业库验收。

### 2026-08-08 - 调整对话消息底色、卡片边框与Markdown md文件字体颜色

- Why:
  - 响应用户需求，将对话中用户消息底色设为 `#B2EDDF`，智能体回复底色设为 `#FFFDF7` 并取消边框，子智能体调用卡片边框设为 `border: 1px solid #EEECE6`，智能体输出中的 `.md` 文件字体颜色修改为 `#05B1A9`。
- What:
  - 用户消息：在 `frontend/apps/agent-web/src/styles/globals.css` 中将 `--ta-chat-user-bg` 修改为 `#B2EDDF`，在 `tokens.css` 中将 `--oc-user-bg` 的回退值修改为 `#B2EDDF`。
  - 智能体回复：在 `globals.css` 中将 `--ta-chat-answer-bg` 修改为 `#FFFDF7`，`--ta-chat-answer-border` 修改为 `transparent`；在 `FigmaChatPanel.vue` 的 `.figma-chat-text-bubble` 中设置 `background: #FFFDF7; border: none;`；在 `parts.css` 的 `.oc-text-part` 中设置 `background: var(--ta-chat-answer-bg, #FFFDF7); border: none;`。
  - 子智能体卡片：在 `frontend/packages/agent-chat/src/opencode-like/styles/tools.css` 的 `.oc-subagent-card` 和 `FigmaChatPanel.vue` 的 `.figma-chat-task-panel` 中将边框修改为 `border: 1px solid #EEECE6`。
  - Markdown md文件颜色：在 `frontend/packages/agent-chat/src/MarkdownView.vue` 的 `.markdown-body :deep(code.ta-md-file)` 中将 `color` 从 `#00ceb9` 修改为 `#05B1A9`。
  - 同步更新 `frontend/apps/agent-web/tests/FigmaShell.test.ts` 中的断言以匹配 `#B2EDDF`。
- How:
  - 修改后运行 `FigmaShell.test.ts`（57 项）、`opencode-timeline.test.ts`（41 项）及 `MarkdownView.test.ts`（9 项），全部测试通过。
  - 运行 `corepack pnpm typecheck` 校验前端全部 workspace 类型检查通过。
- Result:
  - 对话中用户消息底色为 `#B2EDDF`，智能体回复底色为 `#FFFDF7` 且无边框，子智能体卡片边框为 `1px solid #EEECE6`，`.md` 文件高亮文本颜色为 `#05B1A9`。未新建 git 分支，未修改 `.env*` 等环境配置、后端 API 或 OpenCode 源码。

### 2026-08-08 - 批量生成测试案例定时选择面板改为右侧弹出抽屉

- Why:
  - 响应 UI 交互变更要求：将“批量生成子条目测试案例”弹层（`BatchTestCaseGenerationDialog.vue`）中底部的“选择定时执行时间”面板从底部内联折叠弹出修改为从弹层右侧平滑滑出（右侧侧边抽屉）。
- What:
  - 在 `BatchTestCaseGenerationDialog.vue` 中，将原本内联在底部表格与页脚之间的 `.batch-schedule-panel`（选择定时与容量冲突重试两处）重构成右侧定位的抽屉组件 `.batch-schedule-drawer`。
  - 为右侧抽屉设计了 Header（含标题、说明及关闭按钮 `X`）、Body（包裹 `ExecutionTimePicker`，并将夜间时段 grid 布局优化为 3 列适配抽屉宽度）与 Drawer Foot（展示分配说明与容量警告）。
  - 添加 `.batch-drawer-slide` 右侧平滑滑入滑出过渡动画 (`translateX(100%)` -> `translateX(0)`），并保留 `data-testid="batch-schedule-panel"` 与 `data-testid="batch-retry-schedule"` 以保证既有单测与 E2E 校验契约。
- How:
  - 修改后运行 `BatchTestCaseGenerationDialog.test.ts`（11 项全通过）。
  - 运行 `corepack pnpm --filter @test-agent/agent-web typecheck` 类型检查通过。
- Result:
  - 批量生成弹层中点击“选择定时”时，定时选择面板优雅地从弹层右侧平滑滑出，夜间时段以 3 列紧凑美观呈现，关闭时平滑滑回右侧。未新建 git 分支，未修改 `.env*` 等环境配置、后端 API 或 OpenCode 源码。

### 2026-08-08 - 活动栏资源库入口更名为能力库

- Why:
  - 左侧活动栏 Agent/Skill/MCP/Tool Hub 入口可见文字与 tooltip 为“资源库”，与该 Hub 弹层“共享能力中心”“探索全部能力”语义不一致，偏泛且易与工作区文件资源混淆。
- What:
  - `AgentWorkbench.vue` 活动栏 Hub 入口可见文字与 tooltip 由“资源库”改为“能力库”；`aria-label="Agent、Skill、MCP 与 Tool Hub"` 保持不变。
- How:
  - 全仓 grep 确认“资源库”仅出现在该入口两处（title + span），docs/测试/README 均无；typecheck 通过，提交 `fe91019aa`。
  - 过程中发现仓库被另一并行会话持续推进（定时抽屉重构、滚动条、文件名搜索修复等），`BatchTestCaseGenerationDialog.vue` 被他人重构并已修复 `activeRequest?.scheduleMode` nullable。中途曾未先 `git status` 即 `git add` 整个文件，误把他人已提交重构与我的改动混提交（`815028df7`），已 `reset --soft HEAD~1` 回退并将该文件恢复到 HEAD，最终只提交资源库改名。
- Result:
  - 活动栏 Hub 入口现名“能力库”，与弹层“共享能力中心”语义对齐。
  - 教训：多会话/多终端并行仓库中，提交前必须先 `git status`/`git log` 确认工作区与 HEAD 状态，避免覆盖或回退他人已提交成果。

### 2026-08-08 - 修复工作空间文件名搜索误报

- Why:
  - 工作区搜索曾以完整路径匹配；父目录名称命中关键字时，文件名未命中的文件也会错误显示。
- What:
  - `file-explorer` 新增并复用只匹配文件名的最终过滤：服务端结果在组件展示边界过滤，本地回退也只过滤已加载物理工作区文件名。
  - 增加服务端路径误报和本地回退路径误报两条回归覆盖，并同步包 README 的搜索边界说明。
- How:
  - TDD RED：聚焦 Vitest 14 个用例中 2 个失败，分别显示“贷款申请.md”仍被渲染和本地回退额外返回该文件；最小修复后 GREEN 为 14/14。
  - 最终审查补充 `AgentConfig.vue` 与 `" CONFIG "` 的公开 `filterLoadedFiles` 行为覆盖，并断言纯空白关键字返回空数组；正确实现无需生产代码调整，聚焦回归最终为 15/15，原 RED/GREEN 14/14 事实保持不变。
  - `@test-agent/file-explorer` typecheck、前端全量测试、workspace typecheck 与 production build 均以退出码 0 完成。
  - 任务五个文件在并行会话提交 `2e2de18f6` 时被一并收入；按明确例外保留该混合提交，不 amend、reset、revert 或改动其中任务外文件。
- Result:
  - 文件搜索面板不再展示仅因父目录路径命中而误报的文件；API、事件、数据库和安全边界均未变更。
  - 最终审查同时锁定大小写不敏感、首尾空白归一化和纯空白短路这三个公开搜索边界。

### 2026-08-09 - 新增外部 API Key 认证与 SSH Key 查询

- Why:
  - 平台需要向受信外部系统提供独立认证的 API，首版按统一认证号查询状态正常用户的 SSH 私钥；同时需要仅超级管理员可用的工具凭据管理、多 Java 内存快照与跨节点刷新能力。
- What:
  - 新增 `/api/external/v1/users/{unifiedAuthId}/ssh-key` 和 `/api/internal/platform/system-management/api-keys` 管理接口；实现工具编码、scope、启停、分页、新建、按需查看、轮换和删除，并增加系统管理“API Key 管理”面板。
  - API Key 使用安全随机格式生成，以 RSA-OAEP/SHA-256 密文持久化；认证注册表启动时严格整表加载、原子替换不可变快照、常量时间比较，并复用 `external-api-credential.refresh-requested` Redis 广播和 60 秒补偿刷新。SSH 私钥使用 HKDF-SHA256 与 AES-256-GCM 封装为 TAEK1 响应，旧 Bearer Token 和静态 Token 均不能旁路外部认证。
  - 新增候选 Flyway `V20260809120000__create_external_api_credentials.sql` 与 MyBatis XML 持久化；迁移高于当前最高 `V20260808143302`，源码、persistence JAR、最终应用 JAR 的 SHA-256 均为 `356f2cf9127fb514c614ccb8fc77473e6269f6e1e5cd373b0750d2f207009d53`。
  - 同步外部 API/HTTP API/事件流、安全、数据库、后端部署、架构模块、各后端模块与前端包说明及用户手册；刷新广播明确为内部事件，不新增 RunEvent/SSE。
- How:
  - 后端 21 个 Maven 模块 `mvn test` 全部成功，外部凭据、认证过滤器、TAEK1 固定向量/篡改、MyBatis H2 PostgreSQL 模式等专项测试通过；`mvn clean package -DskipTests` 成功。
  - 前端 15 个 workspace typecheck、120 个 Vitest 文件（1882 passed / 1 skipped）与 production build 通过；仅有既有 jsdom Canvas 提示和大分块警告。独立只读安全复核确认明文 Key 不进入 TanStack Query/Mutation 缓存，组件卸载后的晚到响应不会读取或回写 Key。
  - 提交前回顾全部 `.agents/session-log*.md`，确认既有已执行迁移未被改写；未修改 `.env*`、generated SDK 或 OpenCode 源码，也未新建分支。
- Result:
  - 外部调用方可使用工具编码与 API Key 经 O(1) 内存认证后获取加密 SSH 私钥，超级管理员可完整管理凭据；API、数据库和安全契约均为新增，旧内部 API 与认证路径保持兼容。
  - 本机没有可用 Docker/Podman、PostgreSQL、共享 Redis，未能执行真实 PostgreSQL 的空库及各目标历史升级，也未能启动双 Java 实例验证跨节点收敛；这两项及目标环境 `flyway_schema_history`/checksum 核对仍是发布前阻断验收，禁止用当前 H2 与单 JVM 测试替代。

### 2026-08-09 - 统一 Flyway SQL 文件命名为时间戳、表名与描述

- Why:
  - 后续 Flyway SQL 需要从文件名直接识别创建时间、主要目标表和变更目的；多表 migration 还需要统一主表选择规则。
- What:
  - 将 V18 之后新增 migration 的命名规则统一为 `VyyyyMMddHHmmss__table_name_description.sql`，多表时按 SQL 实际变更顺序取第一张表。
  - 同步入口规范、后端规范、数据库部署说明、持久化模块 README/PACKAGE 和完成前自检，并补充单表、多表示例及历史文件不可改名边界。
- How:
  - 检索稳定文档中的旧 `VyyyyMMddHHmmss__description.sql` 规则并逐处更新；未修改或重命名任何现有 migration，也未触碰并行任务的未跟踪文件。
  - 回顾全部 `.agents/session-log*.md` 近期记录，执行规则残留检索和 `git diff --check`。
- Result:
  - 稳定文档已统一采用“时间戳 + 表名 + 描述”规则；不涉及运行时代码、SQL、HTTP API、RunEvent、数据库结构、性能、安全、环境配置、generated SDK 或 OpenCode 源码。

### 2026-08-09 - 实现会话协作分享与被分享人代操作

- Why:
  - 平台需要让会话所属人通过唯一分享链接邀请最多 50 名现有用户，在最长 7 天内查看或代所属人对话；平台必须保留真实操作人归因，同时 OpenCode、工作区、Git/SSH 与进程执行身份始终保持为会话所属人。
  - 分享范围内还需统一约束并发发送、停止、撤回重发、定时任务、文件与终端访问、实时消息可见性，以及分享过期、取消、移除和降权后的行为。
- What:
  - 新增 `session_shares`、`session_share_memberships`、`session_share_audit_events`，实现单会话永久复用一个 256 位随机分享标识、乐观锁版本、成员软状态历史、候选用户搜索、分享管理、被分享列表、访问解析和单会话 runtime SSE；扩展消息、Run、重发与夜间任务的实际操作人、统一认证号及代操作归因。
  - 引入独立 `DelegatedOperationContext`，逐请求验证 actor、会话、工作区、资源、有效期和 `canChat`，执行身份显式保持所属人；只读成员仅可查看，代操作成员按所属人权限上限执行，分享管理及平台级操作仍仅限所属人。普通历史与普通 runtime 查询不因代操作归因获得额外访问权。
  - Run 准入同时使用 `runs.active_session_id` 唯一约束与 Redis 原子占用，忙碌时统一返回 `409 SESSION_BUSY`；停止、最后一条消息撤回重发和定时任务按所属人、实际发送人/创建人规则鉴权，分享失效后已授权定时任务继续按快照执行。
  - 文件访问继续复用 route → ticket → WebSocket RPC，分享 ticket 绑定 actor、所属人、session、workspace、版本、权限与到期时间并逐条重验；敏感操作审计不记录正文、Token、终端输入或明文路径，路径仅记录 SHA-256 摘要。OpenCode 源码和 generated SDK 均未修改。
  - 前端新增分享管理弹窗、“分享给我”列表和 `/s/{shareId}` 完整工作台；所属人跳回普通历史会话，被分享人固定在授权 session/workspace。消息按实际发送人稳定着色，别人消息显示姓名；运行期间所有参与方禁用再次发送，并按权限隐藏或禁用越界入口。
  - 新增迁移 `V20260809170000__session_shares_create_collaboration_share.sql` 与 `V20260809170001__session_messages_add_delegated_attribution.sql`，SHA-256 分别为 `b0b04355fcfe64f3d22d8a8ff297fa62a30db9d97bf6bf82968588f5da72d0c9`、`dfb5d65b474416c28ec6131e95c7b9e7f744f9d2903c0bc4fcd0065632a4eee5`；源码、persistence JAR 和最终应用 JAR 字节一致。
- How:
  - 后端依赖链与应用测试全部通过：persistence 282 项（18 skipped）、API 534 项、app 62 项（1 个既有 skip）；app 测试覆盖 9 条真实 PostgreSQL/Flyway 已知历史升级，最终应用 JAR 构建成功。并发准入、归因、权限、定时任务、路由和迁移兼容均有回归覆盖。
  - 前端全量 123 个 Vitest 文件为 1896 passed / 1 skipped，15 个 workspace 类型检查与 production build 通过；Chromium、Firefox、WebKit 会话分享 E2E 共 15 项全部通过，构建只保留既有大 chunk 警告。
  - `git diff --check`、变更文件冲突标记扫描、禁改目录与 `.env*` 扫描通过；提交前回顾全部 `.agents/session-log*.md`，确认未覆盖其他提交者成果或改写既有 migration。
- Result:
  - 会话分享、多人实时协作和“被分享人替所属人操作、平台记录真实 actor”的完整链路已经落地；HTTP API、RunEvent/runtime SSE、数据库、审计安全与兼容文档同步更新，旧客户端通过可选字段保持兼容。
  - 发布必须先核对目标环境 `flyway_schema_history`、checksum 和存量重复活动 Run，完成数据库迁移并升级全部后端节点后再发布前端；已启动 Run 在分享失效时不自动取消，分享失效后的授权快照定时任务仍会继续执行，这是确认后的业务语义。

### 2026-08-09 - 修复 ExternalSshKeyEnvelopeService 多构造器导致 Spring 启动失败

- Why:
  - `ExternalSshKeyEnvelopeService` 同时声明生产单参构造器与测试用包级三参构造器，二者均未标注 `@Autowired`。Spring 在“多构造器且无注入提示”时会回退查找无参构造器，该类无无参构造器，应用启动抛 `NoSuchMethodException: <init>()` / “No default constructor found”，连带 `ExternalUserSshKeyApplicationService`、`ExternalUserSshKeyController` 装配失败。
- What:
  - 在生产单参构造器上补 `@Autowired` 并加一行中文注释说明多构造器场景必须显式标注；保留包级三参构造器供测试注入确定性 `SecureRandom`/`Clock`。
- How:
  - 复用 `RunInactiveExpiryCoordinator` 既有写法；`mvn -pl test-agent-integration test` 跑 `ExternalSshKeyEnvelopeServiceTest`(2)、`ExternalUserSshKeyApplicationServiceTest`(3) 全绿；扫描后端 stereotype bean 确认同问题仅此一例，其余“多构造器”命中均为方法名误匹配的假阳性。
- Result:
  - 仅改 `ExternalSshKeyEnvelopeService.java` 一处；不涉及 HTTP API、RunEvent、数据库结构、generated SDK、OpenCode 源码或环境配置；启动期 `ExternalSshKeyEnvelopeService` -> `ExternalUserSshKeyApplicationService` -> `ExternalUserSshKeyController` 装配链路恢复。

### 2026-08-09 - 修复 ExternalApiCredential 两服务同类多构造器启动失败（修正前次扫描结论）

- Why:
  - 前一条记录称“同问题仅此一例”结论有误：当时校验用的 `grep '^\s*(public|protected|private)\s+[A-Z]'` 要求显式访问修饰符，漏掉包级私有测试构造器，把 `ExternalApiCredentialApplicationService` 等真实命中误判为假阳性。重启后应用在 `externalApiCredentialController` -> `externalApiCredentialApplicationService` 再次抛 “No default constructor found”。
  - 同一根因：`ExternalApiCredentialApplicationService`（public 4 参 -> 包级 6 参，无无参构造器）、`ExternalApiCredentialUpdateBroadcaster`（public 3 参 -> 包级 4 参，无无参构造器）均为“生产多参构造器 + 包级测试构造器、无无参构造器、无 @Autowired”的 Pattern B，启动必失败。
- What:
  - 在两者的 public 生产构造器上补 `@Autowired` + 中文注释（与 `ExternalSshKeyEnvelopeService` 一致）。
  - 用 Java 感知脚本重扫全后端 stereotype bean：Pattern B（多构造器、无 @Autowired、无无参构造器、启动必失败）目前为 0；Pattern A（多构造器但有无参构造器，且无参构造器委托真实生产依赖，如 `this(new SecureRandom())`、`this(Clock.systemUTC(), ...)`）共 9 个：`ExternalApiKeyGenerator`、`TerminalTicketStore`、`RunConversationSummarizer`、`AppSourceIndexManager`、`AppSourceGitMaterializer`、`WorkspaceFileSocketTicketStore`、`BackendSseForwarder`、`AppSourceOperationTicketStore`、`AgentConfigOperationTicketStore`，Spring 经无参回退落到正确构造器，按最小改动原则不动。
- How:
  - `mvn -pl test-agent-system-management test` 跑 `ExternalApiCredentialApplicationServiceTest`(4)、`ExternalApiCredentialUpdateBroadcasterTest`(2)、`ExternalApiKeyGeneratorTest`(1)、`ExternalApiCredentialRegistryTest`(6) 共 13 项全绿，编译通过。
- Result:
  - 仅改 `ExternalApiCredentialApplicationService.java`、`ExternalApiCredentialUpdateBroadcaster.java` 两处；不涉及 HTTP API、RunEvent、数据库结构、generated SDK、OpenCode 源码或环境配置；Pattern B 启动失败链已全部消除。

### 2026-08-09 - 固定分享消息气泡为两色且移除边框

- Why:
  - 会话分享初版按实际发送人哈希生成多种气泡颜色，范围也会覆盖普通会话中带归因的消息；产品要求只保留两种颜色，并始终以当前查看者区分自己与其他人。
- What:
  - `resolveUserMessageAppearance` 删除哈希色相逻辑：自己的消息固定使用 `#B2EDDF`，所有其他人的消息统一使用 `#9A8EDE`，两者均返回 `border: none`。
  - 姓名规则保持不变：自己的消息不显示姓名，其他人显示姓名；缺少发送人归因的历史消息按自己发送处理，使用绿色且无边框。
  - 分享工作台 E2E 从“颜色不同”收紧为精确断言两个浏览器计算色值和 `border-style: none`；同步 agent-web、agent-chat README 以及设计/实施计划文档。
- How:
  - TDD 先将两个单元测试改为固定色值与无边框断言，确认旧实现 2/2 按预期失败；最小修改解析函数后定向测试 2/2 通过。
  - 前端全量 123 个 Vitest 文件为 1896 passed / 1 skipped，15 个 workspace 类型检查与 production build 通过；Chromium、Firefox、WebKit 分享回归 15/15 通过，仅保留既有 jsdom Canvas 提示和大 chunk 警告。
- Result:
  - 用户看到的消息气泡现只有两种：自己绿色、其他人统一紫色，且都没有边框；不涉及 HTTP API、RunEvent、数据库、性能、安全、环境配置、generated SDK 或 OpenCode 源码，也未新建分支。

### 2026-08-09 - 修复分享成员无法签发会话运行上下文

- Why:
  - 被分享用户发送消息前签发 RunContext 时，`ConversationContextController` 忽略了分享头，直接用真实 actor 调用只允许会话所属人的运行上下文服务，因此返回 `FORBIDDEN: 无权为该会话创建运行上下文`。
- What:
  - 运行上下文入口新增可选 `X-Test-Agent-Session-Share` 解析；分享请求保留真实 `AuthPrincipal`，先校验 actor 的有效成员、`canChat` 与精确 Session，再显式使用 `executionOwnerUserId` 签发所属人的进程上下文。
  - 多构造器场景显式标注生产构造器 `@Autowired`；新增 Controller 回归测试，并同步 API 模块说明、HTTP API、事件流与会话场景测试文档。
- How:
  - TDD 红灯阶段新增分享成员用例，确认生产 Controller 缺少分享上下文依赖时 3 项测试中 1 项按预期失败；实现后定向测试 3/3 通过。
  - 完整 `test-agent-api` 测试 485 项全部通过；`test-agent-app` 连同依赖执行跳过测试的 Maven package 成功；`git diff --check` 通过。
  - 提交前回顾全部 `.agents/session-log*.md`，未修改 `.env*`、Flyway migration、generated SDK 或 OpenCode 源码，也未新建分支。
- Result:
  - 有对话权限的分享成员可按“真实 actor 鉴权、所属人执行”的既定模型取得 RunContext，原会话所属人的普通签发路径保持兼容。
  - 本次修正既有 HTTP 请求头语义与安全校验，不新增 URL、请求/响应字段、SSE 事件或数据库结构，不涉及性能与迁移发布风险。

### 2026-08-09 - 分享给我的会话改为新标签页打开

- Why:
  - 被分享人从当前工作台的会话列表直接进入分享链接时，原实现复用当前标签页执行路由切换，可能短暂沿用普通工作台的初始化状态并提示 OpenCode 进程不可用；刷新独立分享路由后才能正常进入。
- What:
  - `AgentWorkbench` 保留会话列表组件的选择事件，在父组件通过 `router.resolve` 生成 `/s/{shareId}`，并以 `_blank`、`noopener,noreferrer` 打开独立浏览器标签页；当前普通工作台 URL 和状态保持不变。
  - 分享管理 Playwright 场景新增对新标签调用参数、目标路由和原标签不跳转的精确断言；同步 agent-web README 与会话场景测试文档。
- How:
  - TDD 红灯确认旧实现未调用新标签入口；最小修改后 Chromium 定向用例 1/1 通过，Chromium/Firefox/WebKit 分享回归 15/15 通过，agent-web 类型检查和 production build 通过。
  - 并行颜色调整在收尾期间提交了共享工作区，代码、测试和稳定文档已随前序提交 `810c76e89` 一并落库；本条独立记录该行为修复，未重写或回滚并行提交。
  - 提交前回顾全部 `.agents/session-log*.md`；未修改 `.env*`、HTTP API、RunEvent、数据库、generated SDK 或 OpenCode 源码，也未新建分支。
- Result:
  - “分享给我”中的有效会话现在从独立标签页进入，原工作台继续保留；失效分享项仍保持禁用。
  - 本次仅调整前端导航与测试，不涉及后端、性能或安全边界变更。

### 2026-08-09 - 修复分享工作台模型目录为空

- Why:
  - 分享专用客户端会携带分享头，但模型和 Provider 目录请求未携带 `workspaceId`；后端按分享安全边界拒绝未绑定精确 Session/Workspace 的请求，因此被分享人只能看到已选模型文本，候选列表显示为空。
- What:
  - 分享模式从 `SessionShareAccess.workspaceId` 取得固定工作区，将其同时传给模型、Provider 与 Provider 白名单配置请求，并纳入 Vue Query 缓存键；普通工作台继续使用原有无工作区目录请求。
  - Playwright mock 按真实后端规则拒绝缺少或越界工作区的分享目录请求，新增被分享人加载所属人工作区目录并实际选择模型的回归场景；同步 agent-web README 与会话场景测试文档。
- How:
  - TDD 红灯确认旧实现无法展示所属人 Provider；修复后 Chromium 模型相关场景 4/4、Chromium/Firefox/WebKit 分享模型场景 3/3 通过，agent-web 类型检查和 production build 通过。
  - 并行权限开关任务在收尾期间提交了共享工作区，代码、测试和稳定文档已随前序提交 `3eebd21e3` 一并落库；本条独立记录该缺陷修复，未重写或回滚并行提交。
  - 提交前回顾全部 `.agents/session-log*.md`；未修改 `.env*`、HTTP API、RunEvent、数据库、generated SDK 或 OpenCode 源码，也未新建分支。
- Result:
  - 被分享人进入分享会话后可加载会话所属人固定工作区的可用模型并完成选择，同时继续由后端执行精确 Workspace 校验。
  - 本次仅修复前端请求范围与缓存隔离，不放宽权限，不涉及数据库、性能或兼容性协议变更。

### 2026-08-09 - 夜间执行来源标识调整为全彩闹钟图标、微调 Header 布局与新增已分享状态勋章

- Why:
  - 响应 UI 需求，将顶栏对话 Header 与左侧会话历史列表中“夜间执行”来源标识由纯文字/单色图标徽章替换为无文字的全彩闹钟图标；按图示将 Header 全彩闹钟图标调整至对话标题前，“分享”按钮调整至最右侧，并缩小“分享”与右侧栏折叠按钮之间的空暇间距；为“我的会话”列表中已分享的会话增加“已分享”状态显示，并用底色区分是否已过期（蓝底未过期，灰底已过期）。
- What:
  - 新增 `FullColorAlarmClockIcon.vue` SVG 矢量全彩闹钟组件（采用蓝靛色表盘、金色铃铛与渐变高光）。
  - 在 `FigmaChatPanel.vue` 中将 Header 与历史卡片中的“夜间执行”文字与旧图标替换为 `<FullColorAlarmClockIcon>`，保留 hover title 提示。
  - 调整 Header DOM 布局：全彩闹钟图标移至标题 `<h2 class="figma-chat-title">` 前面，`分享` 按钮移至 `会话列表` 按钮右侧；修改 `.figma-chat-header` 右侧 padding（由 `56px` 缩减为 `36px`），保持紧凑统一的 8px 间距。
  - 在 `FigmaChatPanel.vue` 与 `AgentWorkbench.vue` 历史列表中为已分享的会话新增 `.figma-chat-history-card-share-badge`：“已分享”未过期渲染为蓝底蓝字（`.figma-chat-history-card-share-badge--active`），已过期渲染为灰底灰字（`.figma-chat-history-card-share-badge--expired`）。
- How:
  - 在 `FigmaChatPanel.test.ts` 中新增对已分享（蓝底未过期/灰底已过期）勋章的单元测试；执行 `vue-tsc` 类型检查与 `vitest` 定向测试 (155 passed)，确认无类型错误和测试断言问题。
  - 提交前回顾全部 `.agents/session-log*.md`；未修改 `.env*`、HTTP API、RunEvent、数据库、generated SDK 或 OpenCode 源码，也未新建分支。
- Result:
  - 顶栏 Header 标识已移至标题前方，分享按钮位于最右侧且间距缩窄；历史列表中对已分享会话清晰展示“已分享”状态，并以蓝底（未过期）/灰底（已过期）正确区分。

### 2026-08-10 - 修复分享成员代操作 Run 的历史切换实时投影覆盖

- Why:
  - 被分享成员发起 Run 后，会话所属人从历史列表进入同一会话时虽然运行态摘要已显示运行中，但迟到的消息页、session-tree 和旧 Run 详情会覆盖先到的 `run.snapshot.reset`，导致思考状态、停止按钮和工具事件短暂消失或双方不同步。
- What:
  - `AgentWorkbench` 在历史会话切换窗口缓存当前活动 Run 的 snapshot/尾流，并在消息页与 session-tree 两次基线替换后无通知重放；替代 Run 会清理旧缓存，新 snapshot 会压缩此前缓存。
  - 旧历史 Run 详情不得抢占已由 runtime-state 接管的其它 busy Run，Diff 以当前实时投影为基线合并，避免回退到旧快照。
  - 新增所属人打开分享成员活动 Run、历史增强延迟返回的 Playwright 回归，并同步 agent-web、RunEvent 与会话测试文档。
- How:
  - TDD 红灯确认延迟历史增强返回后思考文本被覆盖；修复后该场景在 Chromium、Firefox、WebKit 3/3 通过，邻近运行态场景 5/5 通过。
  - agent-web 类型检查与 production build 通过；前端全量 Vitest 123 个文件通过，1897 passed / 1 skipped。完整分享套件另暴露已有权限控件定位与气泡颜色期望不一致，均与本次运行态修复无关，未扩大范围处理。
  - 提交前回顾全部 `.agents/session-log*.md`，并执行差异、冲突标记与空白校验；未修改 `.env*`、HTTP API、RunEvent wire schema、数据库、generated SDK 或 OpenCode 源码，也未新建分支。
- Result:
  - 所属人与被分享成员现在会在历史加载全过程保持同一活动 Run 的思考、停止权限、工具事件、Todo 与 Diff 投影，不再被迟到的历史基线回滚。
  - 只调整前端事件消费时序，既有后端鉴权、分享代操作模型和旧客户端协议保持兼容；缓存仅存在于单次历史切换窗口。

### 2026-08-10 - 修复分享会话用户消息空气泡与实时正文不同步

- Why:
  - A 在分享会话发送消息后，B 会先收到 OpenCode 不含正文的 user envelope；legacy RunEvent SSE 又假定发送方本地已有乐观用户消息并排除 USER 恢复，导致非发送方看到空绿色气泡，正文不能随活动 Run 立即同步。
- What:
  - `SessionMessageRepository` 新增按 `sessionId + runId + USER` 读取平台权威输入的端口，生产实现通过 MyBatis XML 复用既有组合索引精确查询，不新增 JDBC SQL、字段、索引或 Flyway migration。
  - legacy `RunMessageRecoveryService` 在每次 RunEvent SSE 建连时先发布平台 USER `message.updated`，再接续 assistant-only OpenCode 快照；事件保留平台 ID、远端 ID、正文和实际发送人归因，即使 OpenCode 暂不可用也先同步用户输入。
  - 前端保留空 envelope 供 reducer 原位归并，但 `UserMessageRow` 在正文和可见上下文都为空时不渲染；其他人气泡恢复需求指定的 `#9A8EDE`，自己的消息继续为 `#B2EDDF`，两者无边框。
  - 新增后端恢复与 MyBatis 集成测试、agent-chat 组件测试、A→B 分享工作台三浏览器回归；同时修正分享管理 E2E 对 Element Plus 隐藏开关输入的过期定位器，并同步模块 README、RunEvent 文档、会话测试说明和已执行计划。
- How:
  - TDD 红灯分别确认旧恢复在 OpenCode 不可用时返回空流、持久层缺少精确查询、空 envelope 会渲染气泡；实现后定向测试转绿。
  - 后端相关模块全回归通过：`test-agent-opencode-runtime` 822 项、`test-agent-persistence` 283 项（18 项外部 Redis 条件用例跳过），连同依赖模块 Maven reactor 全部成功。
  - 前端全量 Vitest 123 个文件通过，1898 passed / 1 skipped；会话分享 Chromium/Firefox/WebKit 24/24 通过；全 workspace 类型检查串行通过，agent-web production build 通过。并行运行类型检查和构建时曾因 VitePress `.temp` 竞争失败一次，串行复跑已通过。
- Result:
  - 分享会话任一参与方发现活动 Run 后，都以平台消息作为用户正文和发送人权威源；空远端 envelope 不再生成气泡，后到/重连事件按稳定 ID 原位合并且不会重复。
  - 不新增 HTTP URL、DTO 字段或 RunEvent wire type；既有 `message.updated` 只补充恢复顺序和已有 additive 字段。每次 legacy SSE 建连增加一次走现有索引的单行查询；分享鉴权、执行所属人、安全日志和旧客户端兼容边界不变。未修改 `.env*`、generated SDK 或 OpenCode 源码，也未新建分支。


