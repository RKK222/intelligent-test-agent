# 包说明：@test-agent/editor/src

## 职责

封装 Monaco 文件编辑体验，并向 app 层暴露 Phase 11 prompt 选区上下文所需的受控信息。

## 主要程序清单

- `CodeEditor.vue`：Monaco 编辑器、保存反馈、只读/脏状态展示和当前选区上报回调。
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
