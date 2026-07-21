# @test-agent/workbench-shell

## 工程定位

dockview-vue 工作台布局和跨面板 UI 状态包。

## 主要职责

- 渲染顶部栏和 dockview-vue 左/中/右/底 panel。
- 顶部栏、48px activity rail 和 Dockview 三栏使用 Figma Web IDE 风格的浅灰 chrome；左/右面板默认宽度分别贴近 196px/245px。
- 提供 `activity` slot 渲染工作台级图标入口；底部 Run/Terminal 通过 `bottomOpen`、`bottomHeight` 控制为覆盖式抽屉，默认不占首屏高度。
- 提供打开文件 tab、活动文件、Diff 选择、Agent 公共 worktree 和直接公共配置目录服务器记忆等 Pinia 状态，并提供 `resetWorkspaceView()` 在切换 Workspace 时清空旧文件视图。
- 文件 tab 可记录 `loading/loaded/error`、大文件渐进只读预览的总大小/已加载字节/下一偏移/快照/EOF、错误信息、是否已取得合法磁盘快照和用户内容修订代次；`updateTab(path, patch)` 只更新后台响应所属 tab，不切换活动文件或递增修订，`updateTabContent` 才递增修订，用于防止迟到读取结果抢回焦点或覆盖读取期间已经编辑并保存的正文。
- 提供 Git 变更面板演示数据：应用工作区文件模拟 `src/`、`tests/` 下的业务项目变更；应用级 Agent 配置模拟 opencode `agents/*.md` 与 `skills/<skill>/SKILL.md` 结构，不把公共级配置混入应用级列表。
- 保证固定布局区域有稳定尺寸。
- 保持顶部栏、panel tab、底部运行区和状态徽标的视觉稳定性；streaming、terminal warning 或 Diff 状态变化不得改变整体布局尺寸。
- 只承接工作台级 UI 状态，不承接 session、prompt、permission/question 或 terminal WebSocket 业务状态。

## 禁止事项

- 不调用后端 API。
- 不处理文件内容保存、Run 启动、公共 worktree 切换数据加载或 Diff 接受拒绝。
- 不订阅 RunEvent，不创建 terminal ticket。
