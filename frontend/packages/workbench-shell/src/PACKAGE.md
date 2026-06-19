# 包说明：@test-agent/workbench-shell/src

## 职责

承载 Web IDE 工作台布局和工作台级状态。

## 主要程序清单

- `WorkbenchShell.tsx`：Dockview panel 壳和顶部状态栏。
- `workbenchStore.ts`：Zustand 工作台状态。

## 允许依赖

- React。
- Dockview。
- Zustand。
- `@test-agent/ui-kit`。

## 禁止依赖

- backend-api、event-stream-client、opencode server。

## 修改时必须同步更新

- `frontend/packages/workbench-shell/README.md`。
- `docs/frontend/frontend-architecture.md`。
