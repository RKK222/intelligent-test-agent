# user-manual

## 工程定位

面向最终用户的内置操作手册。正文使用 Markdown 维护，通过 VitePress 构建为纯静态站点，并由 `agent-web` 以 `/help/` 路径同源嵌入。

手册不访问平台 API，不保存用户数据，也不依赖外部搜索服务。全文搜索使用 VitePress 本地索引，可用于企业内网和离线部署。

## 本地命令

```bash
cd frontend
corepack pnpm --filter @test-agent/user-manual dev
corepack pnpm --filter @test-agent/user-manual build
```

`build` 输出到 `frontend/apps/agent-web/public/help/`。该目录属于生成产物，由 `agent-web` 的 Vite 构建复制到最终 `dist/help/`，不提交 Git。

## 内容边界

- `docs/guide/`：用户可见的稳定操作说明，也是帮助中心宠物问答的事实来源。
- `docs/guide/first-time-setup.md`：首次使用的角色、SSH、应用、工作空间和进程准备顺序；操作入口必须与当前权限和页面文案一致。
- `docs/guide/directory-mapping.md`：以标准工程目录为事实源，将开发已有与测试扩展按真实层级合并为一棵可逐级展开、以蓝/绿/橙区分来源的工程树；树内同步标识物理目录、Git、worktree 与分支边界，页面只保留整体目录与内容责任两个视图，并说明 OpenCode 原生 Agent/Skill 映射和 `workagent` 取舍。
- `docs/.vitepress/`：导航、搜索、主题和构建输出配置。
- 产品行为发生变化时，应先同步对应章节，再调整上下文帮助入口。
