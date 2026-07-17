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
- `docs/guide/reference-config.md`：应用管理员在个人工作区初始化/同步应用资产库、选择橙色 SDD 根目录、最小更新 JSONC 引用配置和处理错误的稳定操作说明，并明确已有进程只在下次启动或受管重启后获得引用目录环境。
- `docs/guide/directory-mapping.md`：以当前落地的公共 Git、应用 Git 和个人 worktree 为事实源，将开发与测试目录按真实层级合并为一棵可逐级展开的工程树；目录、Agent/workagent/Skill 名称、两套物理 Git、实现状态和职责都在该 Markdown 顶部的 `directoryMapping` frontmatter 中维护，`DirectoryMapping.vue` 只负责通用展示。正文同步说明公共配置仅超级管理员可写、应用配置仅应用管理员及以上可写、`docs/**` 所有应用成员可发布、`spec/**` 仅个人本地提交，以及从个人 `HEAD` 按白名单投影到应用 feature worktree 的发布流程。
- `docs/.vitepress/`：导航、搜索、主题和构建输出配置。
- 产品行为发生变化时，应先同步对应章节，再调整上下文帮助入口。
