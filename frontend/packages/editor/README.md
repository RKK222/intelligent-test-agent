# @test-agent/editor

## 工程定位

Monaco 文件编辑器包。

## 主要职责

- 按文件路径推断语言。
- 展示当前文件内容、脏状态、只读状态和保存按钮。
- 编辑器 tab、文件工具栏和 Monaco 容器使用 Figma Web IDE 风格的浅灰/白底紧凑 chrome，代码区默认 14px 字号、20px 行高。
- 从空状态首次打开文件时按需初始化 Monaco，并在后续文件切换时复用 editor 实例切换 model。
- 上报 Monaco 当前文本选区给 app 层，用于构造 Prompt file context。
- 保存动作通过回调交给 app 层调用 `backend-api`。
- 选区上下文只上报文件路径、语言、选区范围和文本片段；是否转换为 `PromptPart` 由 app 层负责。
- 编辑器容器需要保持稳定高度和最小宽度，避免保存反馈、只读状态或长文件名挤压 Monaco 区域。
- Markdown 预览：`.md` 文件在工具栏保存按钮左侧展示「预览」开关（眼睛图标），文件打开时默认不预览；开启后编辑器主体上下分屏，上为 Monaco 源码、下为渲染预览，中间为可拖拽 sash（20%~80%）。预览由 `MarkdownPreview.vue` 懒加载 `markdown-it`（转 HTML）+ `highlight.js`（代码高亮）+ `dompurify`（安全消毒）渲染，`github-markdown-css` 提供基础排版样式；这套库不进入首屏同步 bundle；切换文件时预览自动重置为关闭。

## 禁止事项

- 不启动 Run。
- 不直接调用后端。
- 不处理 Diff 接受拒绝。
- 不订阅 RunEvent，不直连 opencode server。
