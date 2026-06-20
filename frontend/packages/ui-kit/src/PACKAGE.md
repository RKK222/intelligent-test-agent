# 包说明：@test-agent/ui-kit/src

## 职责

提供无业务耦合的基础 UI 组件，并统一工作台基础控件的视觉状态。

## 主要程序清单

- `Button.vue`、`Badge.vue`、`Input.vue`、`Textarea.vue`、`Tabs.vue`、`Toast.vue`：消费全局 theme token，提供紧凑、可聚焦、可禁用的基础控件外观。
- `lib.ts`：样式合并工具。

## 允许依赖

- Vue 3。
- lucide-vue-next。
- class-variance-authority、clsx、tailwind-merge。

## 禁止依赖

- backend-api、event-stream-client、业务 feature packages。
- Agent/Model、permission/question、Diff、terminal 等业务状态或运行态语义。

## 修改时必须同步更新

- 本包 README。
- 受影响组件测试。
