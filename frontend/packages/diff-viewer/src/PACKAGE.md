# 包说明：@test-agent/diff-viewer/src

## 职责

展示 Run/Session/VCS Diff，并暴露 Run 级接受/拒绝动作入口。

## 主要程序清单

- `DiffViewer.tsx`：Monaco Diff、来源切换、split/unified、动作栏、文件列表和 hunk 导航。
- `hunks.ts`：从 unified patch 提取 hunk 范围、循环导航和 hunk 到 `PromptPart` file context 的转换。
- `unifiedPatch.ts`：统一 diff patch 到 original/modified 文本的轻量转换。

## 允许依赖

- React。
- `@monaco-editor/react`。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- 直接调用后端 API。
- per-file 后端回滚实现。
- 直接发送 prompt 或订阅 RunEvent。

## 修改时必须同步更新

- `docs/api/backend-api.md`。
- `docs/frontend/frontend-backend-contract.md`。
- 本包 README 和测试。
