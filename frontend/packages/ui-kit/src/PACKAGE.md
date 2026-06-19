# 包说明：@test-agent/ui-kit/src

## 职责

提供无业务耦合的基础 UI 组件。

## 主要程序清单

- `Button.tsx`、`Badge.tsx`、`Input.tsx`、`Tabs.tsx`、`Toast.tsx`。
- `lib.ts`：样式合并工具。

## 允许依赖

- React。
- lucide-react。
- class-variance-authority、clsx、tailwind-merge。

## 禁止依赖

- backend-api、event-stream-client、业务 feature packages。

## 修改时必须同步更新

- 本包 README。
- 受影响组件测试。
