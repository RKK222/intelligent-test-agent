# 包说明：@test-agent/diff-viewer/src

## 职责

展示 Run Diff 并暴露接受/拒绝动作入口。

## 主要程序清单

- `DiffViewer.tsx`：Monaco Diff 和动作栏。
- `unifiedPatch.ts`：统一 diff patch 到 original/modified 文本的轻量转换。

## 允许依赖

- React。
- `@monaco-editor/react`。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- 直接调用后端 API。
- per-file 后端回滚实现。

## 修改时必须同步更新

- `docs/api/backend-api.md`。
- `docs/frontend/frontend-backend-contract.md`。
- 本包 README 和测试。
