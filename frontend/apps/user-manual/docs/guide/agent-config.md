# Agent 与 Skill 配置

左侧 Agent 配置树分为公共配置和应用配置。公共配置来自独立的公共 Git，应用配置来自应用 Git。公共配置使用公共个人 worktree；应用配置与 workspace 文件共用当前版本、当前用户的个人 worktree，只按目录和权限分开展示、提交。

## 公共配置

公共配置由超级管理员按服务器维护，包含平台统一的 Agent、Skill、模型和供应商配置。只有超级管理员可以创建公共 worktree、修改文件、暂存、提交和推送；应用管理员与普通用户只能只读查看允许展示的 `agents/` 和 `skills/` 内容。

公共仓库有本地修改时仍可浏览，但更新前必须由管理员明确确认是否放弃已跟踪修改。不要复制公共配置到业务工作区形成第二套来源。

## 应用工作区配置

应用配置位于当前版本个人 worktree 的 `.opencode/`，用于某个应用版本自己的 Agent、Skill、rules 和 templates。它不再创建独立的“应用配置 worktree”。只有应用管理员与超级管理员可以修改、暂存、提交和发布；普通用户保持只读。左侧 Agents 配置树按角色隐藏初始化和写入入口，内置编辑器以只读方式打开无权限文件；后端文件通道、Agent Git API 和个人 worktree 提交 API 都会再次校验 `.opencode/**` 权限。

创建配置包时，平台会一次生成 OpenCode 可直接识别的四个文件：

- `agents/<name>.md`：应用 Agent 模板，当前默认 `mode: primary`
- `skills/<name>/SKILL.md`
- `skills/<name>/rules/README.md`
- `skills/<name>/templates/README.md`

Skill 名称只使用小写字母、数字和短横线；`SKILL.md` 默认带有 `compatibility: opencode`。这些文件与 docs、spec 和普通 workspace 文件位于同一个人 worktree，但 Git 变更面板把 `.opencode/**` 单独归入“应用Agent”Tab，避免和 workspace 文件混在一棵差异树中。

在内置编辑器保存 `agents/*.md` 或 `skills/<name>/SKILL.md` 后，平台会自动重新加载当前 TestAgent 的 Agent 与 Skill 目录，右侧 Agent、`@` 和 `/` Skill 候选会直接更新，不需要刷新浏览器。若文件已经保存但运行态重新加载失败，页面会明确提示“文件已保存，运行态目录刷新失败”；文件修改不会因此丢失。

## 发布前检查

1. 确认当前选择的是公共配置还是应用配置，并确认自己具备对应角色。
2. 解决所有 Git 冲突。
3. 暂存本次确实要发布的配置文件。
4. 提交并等待页面明确显示推送成功。

公共配置推送成功后，平台会广播公共配置同步。应用配置先提交到当前用户的个人 `HEAD`，再按 `.opencode/**` 白名单投影到对应版本 feature worktree；推送成功后更新应用版本 HEAD 并广播版本同步通知。平台不会用发布结果覆盖其他用户个人 worktree 中尚未提交的修改。

## 常见问题

### 提示没有已初始化服务器

这通常是公共配置管理问题。联系超级管理员在“系统管理 → 配置管理 → TestAgent 公共配置管理”初始化目标服务器。

### 配置文件只读

确认当前查看的是公共配置还是应用配置，并检查当前用户角色和个人 worktree 状态。公共配置对非超级管理员只读，应用配置对非应用管理员只读；应用配置不存在单独的 worktree 切换入口。
