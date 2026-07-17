# 包说明：@test-agent/workbench-shell/src

## 职责

承载 Web IDE 工作台布局和工作台级状态，保证多面板运行态下的固定区域尺寸稳定。

## 主要程序清单

- `WorkbenchShell.vue`：dockview-vue panel 壳、顶部状态栏、底部运行区容器和固定尺寸布局约束。
- `DockPanel.ts`：dockview 面板渲染器，按 `params.slot` 渲染对应插槽内容。
- `workbenchStore.ts`：Pinia 工作台状态，包括打开文件 tab、Agent public/workspace worktree、公共直接配置目录服务器记忆，以及 Git 变更面板的应用工作区与应用级 Agent/Skill mock 数据；文件 tab 保存加载三态、错误、稳定快照身份和用户内容修订代次，`updateTab` 允许后台响应更新自己的 tab 而不改变当前活动文件或用户修订代次。

## 允许依赖

- Vue 3。
- dockview-vue。
- Pinia。
- `@test-agent/ui-kit`。

## 禁止依赖

- backend-api、event-stream-client、opencode server。
- session、prompt、permission/question、terminal WebSocket 等业务运行态。

## 修改时必须同步更新

- `frontend/packages/workbench-shell/README.md`。
- `docs/architecture/module-map.md`。
