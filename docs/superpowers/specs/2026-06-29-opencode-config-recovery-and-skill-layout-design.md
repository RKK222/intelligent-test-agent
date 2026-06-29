# OpenCode 配置恢复与技能目录整理设计

## 目标

修复公共配置仓库因误删或修改已跟踪文件后无法继续浏览、刷新和重新同步的问题，并把测试技能整理为 OpenCode 原生目录：

```text
opencode/
  agents/*.md
  skills/<skill-name>/SKILL.md
```

工作空间级配置只承载应用自己的技能包，普通工作空间文件树不重复展示 `.opencode`。

## 公共仓库恢复

公共仓库的 `initialized` 表示 Git 仓库、origin 和 `opencode/` 配置目录已经建立，不再与“工作树是否干净”混为一谈。工作树存在未提交修改时仍返回 `status=CONFLICT` 和提示，但文件树、Diff、提交等操作保持可用。

“更新公共配置”增加可选的 `discardLocalChanges` 参数。默认值为 `false`，继续保护未提交内容；用户在弹窗中明确勾选“放弃本地修改并从远端恢复”后，后端才允许把受控公共仓库重置到当前提交，再 fetch、切换分支和 fast-forward pull。未跟踪文件不主动删除，避免扩大破坏范围。

## 技能目录

保留 `opencode/agents/*.md`，因为 OpenCode 1.17 通过该目录注册主 Agent 和子 Agent。删除 `opencode/mimoagent-agents/` 包装层和 `opencode/skills/` 下的符号链接，把 18 个技能包的 `SKILL.md`、`rules/`、`templates/` 作为实际目录直接放在 `opencode/skills/<skill-name>/`。

## 工作空间技能

工作空间“+”只初始化：

```text
.opencode/skills/<skill-name>/
  SKILL.md
  rules/README.md
  templates/README.md
```

不再创建 `.opencode/agents/<skill-name>.md`。`README.md` 提供可直接编辑的规则和模板说明，避免空目录依赖 `.gitkeep`。

普通工作空间文件树只在根目录过滤 `.opencode`；Agent 配置区继续以 `.opencode` 为物理根，但界面从其子项开始展示，因此不会出现重复的 `.opencode` 节点。

## OpenCode 进程初始化

公共配置 CLI 加载与进程管理分开验证。当前公共配置可通过 `opencode agent list` 加载；页面初始化失败的直接原因是 manager WebSocket 断开。本次补充 WebSocket 入站异常日志，定位并修复导致连接结束的实际异常，再使用真实页面重新初始化进程。

## 验证

- 后端：公共仓库脏状态、显式恢复参数、Controller DTO 和 WebSocket 异常日志测试。
- 前端：工作空间技能包初始化、`.opencode` 根节点过滤和公共恢复确认测试。
- 配置：18 个实际技能包均有 `SKILL.md`，无符号链接和 `mimoagent-agents`，OpenCode 能加载 Agent/Skill。
- 运行：使用 `.env.test` 重启三服务，验证 health/readiness、manager 连接和页面进程初始化。

## 兼容性与安全

- `discardLocalChanges` 为可选字段，旧客户端默认不覆盖本地修改。
- 不修改 SSE 事件、数据库结构和 generated SDK。
- 文件操作继续走平台文件 WebSocket route/ticket/RPC。
- 不修改 `.env.test` 或 `.env.local`。
