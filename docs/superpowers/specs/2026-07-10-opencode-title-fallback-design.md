# OpenCode 会话标题兜底设计

## 目标

避免 OpenCode 内置 `title` agent 与快速完成的主 Run 并行执行时，右侧标题长期停留在 `New session - <timestamp>` 或首条消息临时标题。

## 方案

平台继续优先消费 OpenCode root `session.updated` 的有效标题，并明确忽略其默认时间戳标题。若首轮 root Run 成功时尚无平台确认标题，则在同一用户进程、同一 workspace 新建一个不映射到平台的临时远端 session，以原始首条用户消息调用 OpenCode 内置 `title` agent；轮询到其 assistant 文本后删除临时 session。

生成成功时通过数据库 compare-and-set 仅更新仍保持首条消息临时标题的 Session，并仅在条件更新成功后追加既有 `session.updated`，携带现有确认字段及 `platformSessionTitleFallback: true`。若原生标题或用户手动改名先完成、非首轮、非 opencode、失败或超时，均不覆盖现有标题。

## 边界

- 不调用第三方插件、不新增模型或自定义标题 prompt；只复用 OpenCode 原生 `title` agent。
- 不新增 HTTP API、SSE wire name、DTO、数据库/Flyway 或 generated SDK。
- `test-agent.opencode.session-title` 配置兜底开关、超时和轮询间隔，默认启用、5 秒、100 毫秒。

## 验证

单元测试覆盖：默认时间戳标题不同步、临时 session 的创建/调用/删除、原生标题已同步时不兜底、首轮成功后触发调度。随后重启本地三服务并用快速回复的新对话验证右侧最终更新为 AI 标题。
