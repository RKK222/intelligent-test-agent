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
- `docs/guide/directory-mapping.md`：以标准工程目录为事实源，将开发已有与测试扩展按真实层级合并为一棵可逐级展开的工程树；目录、Agent/workagent/Skill 名称、物理 Git、实现状态和职责都在该 Markdown 顶部的 `directoryMapping` frontmatter 中维护，`DirectoryMapping.vue` 只负责通用展示。测试公共 Config 已存在的 Agent/workagent/Skill 使用真实名称并标记“已实现”，没有对应定义的规划项标记“未实现”并灰显；应用专属测试 Agent/workagent 归入测试设计、测试执行等具体活动，测试设计应用规约按测试对象类型展开，`skills/` 以同级 `coding/`、`test/` 分别收口开发和测试 Skill。`docs/应用架构/` 合并开发应用关系与测试概述、应用场景说明书等场景测试资产，`docs/技术架构/` 只保留开发技术资产。测试 Agent 下的公共规约和应用规约都使用“测试”范围标签，仅以 Git 标签区分测试公共与应用归属。`agents/`、`skills/`、`docs/` 明确展示开发 AI Git、测试公共 AI Git、测试 AI Git 和开发业务代码 Git 的合并关系；页面保留整体目录与内容责任两个视图。
- `docs/.vitepress/`：导航、搜索、主题和构建输出配置。
- 产品行为发生变化时，应先同步对应章节，再调整上下文帮助入口。
