# 包说明：@test-agent/editor/src

## 职责

封装 Monaco 文件编辑体验。

## 主要程序清单

- `CodeEditor.tsx`：Monaco 编辑器和保存反馈。
- `language.ts`：路径到 Monaco language 映射。

## 允许依赖

- React。
- `@monaco-editor/react`。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- backend-api、event-stream-client、opencode server。

## 修改时必须同步更新

- 本包 README 和测试。
