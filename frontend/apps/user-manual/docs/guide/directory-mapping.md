---
aside: false
---

# 开发与测试目录设计

本页以标准工程目录文件为准，将开发已有目录与测试扩展合并为一棵可逐级展开的应用（服务群组）工程树，并用高区分度的蓝、绿、橙分别标识开发基线、测试新增和个人本地内容。树内同时标出目标逻辑目录、实际磁盘根、所属 Git 与分支边界；工程根是多个 worktree 的组合视图，不是一个新的 Git 仓库。

<DirectoryMapping />

## 目录设计原则

### 保留一个完整工程视图

研发和测试不是两棵互不相干的目录。测试目录沿用应用工程中的需求、设计、编码、业务 Git 和知识文档，再扩展测试智能体、四阶段 `spec`、测试资产及归档能力。

### `ai-agent` 定义怎样工作

`ai-agent` 是面向建设者的逻辑分类，不应原样复制到 OpenCode 原生目录。当前 Config 区域实际管理公共配置 Git 的 `opencode/{agents,skills}` 和应用 worktree 的 `.opencode/{agents,skills}`；普通用户在根级只看到 `agents`、`skills`。

- 可直接对话或需要独立上下文的测试设计、测试执行、测试分析落为 `.opencode/agents/<name>.md`，使用 `mode: subagent`。
- 测试对象识别、案例生成、脚本/数据/断言构造属于上层流程中的复用方法，落为 `.opencode/skills/<name>/SKILL.md`，不继续抽象为 `workagent`。
- 测试设计审核、测试执行审核需要隔离上下文和只读权限，适合独立审核 subagent；需要用户直接调用时出现在 `@` 列表，只允许上层调用时设置 `hidden: true`。
- `rules`、`template`、`eval` 放进对应 Skill 资源目录，或由 `AGENTS.md` / `opencode.jsonc` 的 `instructions` 引用，不能作为普通 `.md` 混放在 `.opencode/agents/**`。

OpenCode 会递归读取 `agents/**/*.md`，文件相对路径会成为 Agent 名称；例如 `.opencode/agents/test/review.md` 对应 `@test/review`。`mode: subagent` 和 `mode: all` 可进入 `@` 候选，`hidden: true` 会隐藏候选。因此 `workagent` 可以保留为业务口语，但不应成为第三种技术类型或目录发现规则。

### 物理目录和运行时叠加

- 公共管理员在 `${OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/<worktree>/opencode/` 工作，所属公共 AI Git 和管理员选择的工作分支不变；发布后平台同步运行目录 `${OPENCODE_PUBLIC_CONFIG_DIR}`，并作为每个用户 OpenCode 进程共同的 `OPENCODE_CONFIG_DIR`。
- 应用 Agent、Skill、`docs`、`archive` 位于 `${OPENCODE_PERSONAL_WORKTREE_ROOT}/<版本>/<用户>/<应用AI库>/<分支>/` 对应应用 AI Git worktree；原生 Agent/Skill 技术目录分别是 `.opencode/agents`、`.opencode/skills`。
- `spec` 使用同一应用 AI Git 派生的独立本地 worktree 和 `local/spec/<用户>` 本地分支，不设置 upstream。只有这样才能从机制上保证“可提交但不可随应用资产一起推送”。
- `git-repo-A`、`git-repo-B` 继续使用各自业务 Git 的个人 worktree。交给 OpenCode 的工作区通过组合挂载呈现这些目录，不改变任一来源的 `.git`、分支或发布权限。

### `spec` 保存当前需求事实

`spec/<需求项>/` 固定分为 `01-需求`、`02-设计`、`03-编码`、`04-测试`。测试阶段再分测试设计和测试执行，并同时支持流程测试与需求子条目。`spec` 只在个人本地 worktree 提交，不直接推送远端。

### `docs` 保存可复用测试资产

`docs` 由应用 AI Git 管理，保留文件给出的真实技术目录：应用架构、技术架构、功能模块、数据架构和部署架构。页面可按业务、功能、架构三个视角组织，但不改变磁盘目录名。

- 开发已有资产：工程概览、功能模块业务说明、功能文档、数据库 YAML 和业务知识。
- 测试新增资产：测试概述、场景测试说明书、测试设计文档、测试案例、数据实体和部署架构。
- 业务视角：场景测试说明书中的业务规则、场景图、核心要素和测试关注点。
- 功能视角：功能模块下的测试设计文档、测试案例和功能验证基线。
- 架构视角：应用架构、测试概述、数据实体、部署结构和非功能要求。

### `archive` 只接收已确认快照

完成评审的规格由受控流程复制到 `archive/<年月>/<需求项>/` 形成共享快照。它不是把本地 `spec` 分支直接推送远端，也不替代业务 Git 的正式交付。

## Git 与发布边界

1. 公共 AI Git 中的公共 Agent / Skills 由效能组和测试管理组建设，公共规约由测试管理组建设，统一由平台超级管理员发布。
2. 应用 AI Git 中的应用 Agent / Skills、应用规约、`docs/**` 和 `archive` 均由测试组建设，由应用管理员及以上角色发布。
3. `spec` 使用应用 AI Git 派生的本地个人分支，只允许本地提交，不设置远端跟踪。
4. `git-repo-A`、`git-repo-B` 等业务 Git 由开发团队建设，测试工作区只组合对应个人 worktree，不复制整库到 `spec` 或 `docs`。
5. 页面展示可以合并多个来源，但每个目录仍保留自己的 Git、分支、权限和发布规则。
