# Skill 结构规范

## 必需入口

每个技能只有一个必需入口：`SKILL.md`。

```yaml
---
name: example-skill
description: Example Skill（示例技能）。说明能力和触发场景，并写清与相邻技能的边界。
compatibility: opencode
metadata:
  display-name: Example Skill
  display-name-zh: 示例技能
  source: test-agent
  version: '1.0.0'
---
```

- `name` 使用小写字母、数字和单连字符，最长 64 个字符；
- 技能目录名与 `name` 完全一致；
- `description` 非空且不超过 1024 个字符，首句使用 `English（中文）。`；
- `metadata.display-name/display-name-zh` 分别保存英文和中文展示名，`metadata.source` 使用 `test-agent`；
- `compatibility` 为可选字段，TestAgent 创建的技能必须写上述展示 metadata；
- frontmatter 后必须有可执行的 Markdown 正文。

中文名称必填，英文展示名称选填；英文未提供时，用完整无声调拼音生成英文展示名称和 kebab-case 技术 ID。展示名称可以调整，但发布后不得仅为改中文展示而修改目录名或顶层 `name`。

## 渐进加载

1. 平台常驻读取 `name` 和 `description`；
2. 触发后读取 `SKILL.md` 正文；
3. 正文按当前任务明确指向具体资源，避免一次加载全部内容。

当 `SKILL.md` 接近 500 行或混合多个独立领域时，把长规则拆到 `rules/` 或 `references/`。在正文中写清何时读取哪个文件，不能只罗列文件名。

## 资源选择

| 目录 | 适用内容 | 不应包含 |
| --- | --- | --- |
| `rules/` | 必须遵守的领域规则、检查门禁 | 与任务无关的百科背景 |
| `references/` | 协议、术语、长背景和平台差异 | 必须每次执行的核心步骤 |
| `templates/` | 稳定输出骨架和可复制样例 | 未替换占位符的最终产物 |
| `scripts/` | 校验、转换、打包等确定性操作 | 密钥、环境专属地址和静默破坏逻辑 |
| `evals/` | 真实提示、预期行为和断言 | 只覆盖理想路径的演示数据 |

## 企业可移植性

- 默认使用相对路径，以 `SKILL.md` 所在目录解析技能资源；
- 使用标准库或明确声明依赖及版本，并说明离线安装方式；
- 不打包 `node_modules`、虚拟环境、构建缓存、日志和个人配置；
- 不写入账号、token、证书、内网地址、生产数据或个人绝对路径；
- 未授权时不发消息、不发布、不推送、不修改环境配置；
- 外部资料无法访问时保留可执行的降级路径，不伪造查询结果。

## 创作目录与发布边界

- 新技能只能先写入平台明确提供且属于当前用户的个人 worktree；普通应用 worktree 使用 `.opencode/skills/<skill-name>/`，公共 Agent 个人配置 worktree 的 `opencode/` 编辑根使用 `skills/<skill-name>/`；
- OpenCode 的共享运行配置根、进程 `OPENCODE_CONFIG_DIR`、当前已安装技能目录不属于创作目录，不能因为可写就直接修改；
- 要发布公共技能时，通过平台公共 Agent 编辑区的个人 worktree 查看 Diff、提交和发布，由 rollout 同步共享运行副本；不得把文件直接复制到共享目录冒充发布；
- 无法确认 worktree 用户归属时停止写入并请求用户切换到正确编辑上下文，不能选择一个“看起来像配置目录”的路径继续。

## 测试设计

最小测试集应覆盖典型场景、边界场景和近似但不应触发的场景。评价维度优先选择可观察结果：

- 是否读取必要输入和规则；
- 是否生成约定文件或结构；
- 是否拒绝越权或危险操作；
- 是否在缺少证据时停止或标记待确认；
- 是否避免触发相邻技能；
- 是否如实报告验证范围。
