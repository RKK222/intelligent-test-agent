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
- 用户手册永久不收录游戏、小游戏或其它娱乐玩法，包括入口、配置、权限、操作步骤和截图；即使相关能力已经在产品中开放，也不得加入 `docs/guide/` 或每周新功能。
- `docs/guide/weekly-updates.md`：按自然周把当前分支已开放的新功能置顶汇总；每项从用户场景、使用前配置、操作入口、使用步骤、操作截图和权限/数据边界说明，并链接回稳定专题。截图统一放在 `docs/guide/images/weekly-updates/`，使用脱敏的真实组件状态和明确替代文本；同步到其它分支时必须按对应分支的真实能力增删内容。
- `docs/guide/feature-overview.md`：按真实工作台入口汇总文件与编辑器、通知、对话协作、长期记忆、Git、Agent/Skill、Hub、引用配置和帮助能力，并链接到各专题章节。
- `docs/guide/first-time-setup.md`：首次使用的角色、SSH、应用、工作空间和进程准备顺序；操作入口必须与当前权限和页面文案一致。
- `docs/guide/settings.md`：按普通用户与应用管理员权限说明设置弹窗中的 SSH、应用成员、版本库关联和工作空间操作；不展开超级管理员专属的用户管理流程。
- `docs/guide/workspace.md`：应用、版本与个人工作区的选择关系，文件操作、Git 提交与发布，以及测试设计/测试执行资料批量跳转到外部页面的稳定操作说明。
- `docs/guide/conversation.md`：主对话、上下文、批量子条目案例选择与会话创建进度、失败重试和主动结束批次、夜间执行时段、协作分享、待执行任务、会话锁定、宠物旁路和历史对话的稳定用户操作说明。
- `docs/guide/memory.md`：长期记忆的开放范围、自动学习和实际使用提示，以及个人/团队记忆、来源证据、暂停归档和 Skill 提案的稳定用户操作说明。
- `docs/guide/reference-config.md`：应用管理员在个人工作区初始化/同步/受控切换应用资产分支、主动核验各服务器实际 Git 指针、选择橙色 SDD 根目录、最小更新 JSONC 引用配置和处理错误的稳定操作说明；同时说明工作区文件树中的合并/非合并投影、蓝色引用来源、同名冲突、只读交互和局部告警，并明确已有进程只在下次启动或受管重启后获得引用目录环境。
- `docs/guide/directory-mapping.md`：以当前落地的公共 Git、应用 Git 和个人 worktree 为事实源，将开发与测试目录按真实层级合并为一棵可逐级展开的工程树；目录、Agent/workagent/Skill 名称、两套物理 Git、实现状态和职责都在该 Markdown 顶部的 `directoryMapping` frontmatter 中维护，`DirectoryMapping.vue` 只负责通用展示。正文同步说明公共配置仅超级管理员可写、应用配置仅应用管理员及以上可写、`docs/**` 所有应用成员可发布、`spec/**` 仅个人本地提交，以及从个人 `HEAD` 按白名单投影到应用 feature worktree 的发布流程。
- `docs/guide/faq.md`：把常见功能、权限问答和故障排查放在同一页，覆盖文件树、对话、长期记忆、Git、Agent/Skill、Hub、引用配置、定时任务和手册问答，并提供脱敏上报模板。
- `docs/.vitepress/`：导航、搜索、主题和构建输出配置。
- 产品行为发生变化时，应先同步对应章节，再调整上下文帮助入口。
