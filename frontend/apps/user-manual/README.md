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

- `docs/guide/`：用户可见的稳定操作说明，也是帮助中心宠物问答的事实来源。每个稳定章节至少包含一张与正文步骤对应的脱敏操作截图，截图必须使用明确替代文本，并随入口、页面文案或流程变化同步更新。
- 用户手册永久不收录游戏、小游戏或其它娱乐玩法，包括入口、配置、权限、操作步骤和截图；即使相关能力已经在产品中开放，也不得加入 `docs/guide/` 或每周新功能。
- `docs/guide/weekly-updates.md`：按自然周把已在当前交付版本开放的新功能置顶汇总；每项从用户场景、使用前配置、操作入口、使用步骤、操作截图和权限/数据边界说明，并链接回稳定专题。截图与稳定章节复用 `docs/guide/images/operations/` 中的脱敏真实组件状态，并使用明确替代文本；更新时不得写入尚未交付的能力。
- `docs/guide/feature-overview.md`：按真实工作台入口汇总文件与编辑器、通知、对话协作、本地 OpenCode 客户端、长期记忆、Git、Agent/Skill、Hub、引用配置和帮助能力，并链接到各专题章节；客户端和记忆的账号灰度边界必须与用户管理页面保持一致。
- `docs/guide/first-time-setup.md`：首次使用的角色、SSH、应用、工作空间和进程准备顺序；操作入口必须与当前权限和页面文案一致。
- `docs/guide/settings.md`：按普通用户与应用管理员权限说明设置弹窗中的 SSH、应用成员、版本库关联和工作空间操作；同时说明超级管理员在用户管理中独立维护客户端灰度与记忆灰度，以及各自的可见性和运行影响。
- `docs/guide/workspace.md`：应用、版本与个人工作区的选择关系，文件操作、Git 提交与发布，以及测试设计/测试执行资料批量跳转到外部页面的稳定操作说明。
- `docs/guide/conversation.md`：主对话、上下文、批量子条目案例选择与会话创建进度、失败重试和主动结束批次、夜间执行时段、协作分享、待执行任务、会话锁定、宠物旁路和历史对话的稳定用户操作说明。
- `docs/guide/memory.md`：长期记忆的开放范围、自动学习和实际使用提示，以及个人/团队记忆、来源证据、暂停归档和 Skill 提案的稳定用户操作说明。
- `docs/guide/reference-config.md`：统一说明应用资产库与自动化代码库的只读引用配置。应用资产侧覆盖初始化/同步/受控切换分支、服务器 Git 指针核验、蓝色 SDD 根目录和 JSONC 最小更新；自动化侧覆盖每个关联版本库唯一当前别名/分支、任意已有目录、共享描述、generation 同步和 JSONC 对账，并说明普通成员只读查看、组合文件树交互和局部告警。
- `docs/guide/directory-mapping.md`：以当前落地的公共 Git、应用 Git 和个人 worktree 为事实源，将开发与测试目录按真实层级合并为一棵可逐级展开的工程树；目录、Agent/workagent/Skill 名称、两套物理 Git、实现状态和职责都在该 Markdown 顶部的 `directoryMapping` frontmatter 中维护，`DirectoryMapping.vue` 只负责通用展示。正文同步说明公共配置仅超级管理员可写、应用配置仅应用管理员及以上可写、`docs/**` 所有应用成员可发布、`spec/**` 仅个人本地提交，以及从个人 `HEAD` 按白名单投影到应用 feature worktree 的发布流程。
- `docs/guide/faq.md`：把常见功能、权限问答和故障排查放在同一页，覆盖文件树、对话、长期记忆、Git、Agent/Skill、Hub、引用配置、定时任务和手册问答，并提供脱敏上报模板。
- `docs/.vitepress/`：导航、搜索、主题和构建输出配置。
- 产品行为发生变化时，应先同步对应章节，再调整上下文帮助入口。
