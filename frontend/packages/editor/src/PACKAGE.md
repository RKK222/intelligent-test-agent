# 包说明：@test-agent/editor/src

## 职责

封装 Monaco 文件编辑体验，并向 app 层暴露 prompt 选区上下文所需的受控信息。

## 主要程序清单

- `CodeEditor.vue`：Monaco 编辑器、保存反馈、只读/脏状态展示和当前选区上报回调；Markdown 文件的预览开关已上提到调用方（典型为 `FigmaEditorArea` tab 表头），组件本身只接受 `showPreview` 受控 prop 并在下方追加 `MarkdownPreview` 分屏，不再自带切换 UI，避免出现"两处入口不同步"。具备 `ResizeObserver` 布局监听与分屏/切换文件时的 `editor.layout()` 布局重计算，并在源码宿主有实际宽高时显式传入尺寸，防止预览分屏或容器尺寸变更时 Monaco DOM 坍塌白屏；在非只读文件下通过 `editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyS, …)` 拦截浏览器的「保存网页」行为，向调用方 emit `save`；调用方再按脏文件 / livePreview / 已保存中等条件决定是否真正落盘。
- `monaco-env.ts`：Monaco Web Worker 配置（懒加载）。
- `language.ts`：路径到 Monaco language 映射。

## 允许依赖

- Vue 3。
- `monaco-editor`（原生）。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- backend-api、event-stream-client、opencode server。
- Run 启动、Diff 落盘动作或 `PromptPart` 提交。

## 修改时必须同步更新

- 本包 README 和测试。
