# 包说明：@test-agent/editor/src

## 职责

封装 Monaco 文件编辑体验，并向 app 层暴露 prompt 选区上下文所需的受控信息。

## 主要程序清单

- `CodeEditor.vue`：Monaco 编辑器、保存反馈、只读/脏状态展示、无文件极简空态和当前选区上报回调；Markdown 文件的预览已改为 Monaco + markdown-it 模式（Monaco 组件负责编辑，markdown-it 负责分屏渲染），组件本身只接受 `showPreview` 和 `previewMode` 受控 prop 并在下方追加 `MarkdownPreview` 分屏。具备 `ResizeObserver` 布局监听与分屏/切换文件时的 `editor.layout()` 布局重计算，并在源码宿主有实际宽高时显式传入尺寸；Monaco 按需加载期间会丢弃过期文件模型，防止预览分屏或容器尺寸变更时 Monaco DOM 坍塌白屏；在非只读文件下通过 `editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyS, …)` 拦截浏览器的「保存网页」行为，向调用方 emit `save`；调用方再按脏文件 / livePreview / 已保存中等条件决定是否真正落盘。
- `MarkdownPreview.vue`：Markdown 安全渲染、Mermaid 脚本/图表切换和可视化编辑流程编排；只定位当前 fence、调用官方 parser、懒加载编辑对话框，并将完整 Markdown 通过 `change` 交回 `CodeEditor`；渲染依赖失败时展示原文纯文本，避免已读取的文件内容变成空白。
- `mermaid/model.ts`、`parser.ts`、`serializer.ts`、`metadata.ts`、`layout.ts`：Flowchart/graph 强类型模型、双向转换、unknown line 保留与稳定布局 metadata。
- `mermaid/sequence/`：Sequence diagram 的 participant/actor、有序 message 模型、parser、serializer、布局和 Vue Flow 适配；复杂控制语句暂不编辑但必须保留。
- `mermaid/visual-editor/`：通用可视化对话框和 Flowchart Vue Flow 画布；Sequence 画布位于 `mermaid/sequence/visual-editor/`，消息使用按顺序错层的自定义边避免重叠。
- `monaco-env.ts`：Monaco Web Worker 配置（懒加载）。
- `language.ts`：路径到 Monaco language 映射。

## 允许依赖

- Vue 3。
- `monaco-editor`（原生）。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。
- `@vue-flow/core`（仅 Mermaid 可视化编辑异步 chunk 使用）。

## 禁止依赖

- backend-api、event-stream-client、opencode server。
- Run 启动、Diff 落盘动作或 `PromptPart` 提交。

## 修改时必须同步更新

- 本包 README 和测试。
