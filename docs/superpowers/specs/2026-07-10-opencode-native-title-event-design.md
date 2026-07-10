# OpenCode 原生标题事件同步设计

## 背景

OpenCode 内置 `title` agent 会在主 Run 的 `run.succeeded` 之后异步发送 root `session.updated`。现有 `RunApplicationService` 在收到 Run 终态后关闭远端事件流，导致该标题事件无法进入平台；以临时会话再次调用 `title` agent 并按固定时长轮询，会把正确性建立在不稳定的耗时上。

## 目标与边界

- 仅以 OpenCode 原生 root `session.updated` 中的有效非默认标题为标题事实源。
- 标题更新后立即持久化平台 Session，并经已有 RunEvent SSE 推送给仍打开的页面。
- 不再创建临时远端 session、不再次调用 `title` agent、不依据生成超时决定标题。
- 保留 root/child 隔离、远端 session 绑定校验和用户手动改名不被覆盖的既有保护。
- 不新增 HTTP API、DTO、数据库表、Flyway migration 或 generated SDK 改动。

## 方案比较

1. 延长轮询兜底：实现最小，但仍依赖模型耗时，不能保证正确性；不采用。
2. 每个 Run 终态后无限保留原事件流：可以收到标题，但会把下一轮消息错归属到旧 Run，并可能永久占用连接；不采用。
3. 推荐：在 root Run 终态后切换为只消费该 root 会话的标题更新监听。监听只接受已由 scope router 确认的 `session.updated`，收到有效原生标题便持久化、追加既有 RunEvent 并结束；其它下一轮消息事件一律忽略。监听仅使用资源回收上限避免无事件时遗留连接，该上限不生成、不替代、也不修改标题。

## 数据流

1. 主 Run 流正常收到并落库 `run.succeeded`，页面立即显示回答完成。
2. 主流不再将标题生成作为终态后的轮询任务。
3. 同一远端事件流进入标题监听阶段，只路由目标 root session 的 `session.updated`。
4. 有效标题经过现有 `synchronizeRootSessionTitle` 校验并写入平台 Session。
5. 追加带 `platformSessionTitleSynchronized=true` 的既有 `session.updated` RunEvent；现有 Run SSE 与前端标题处理立即更新页面和消息列表。
6. 监听在成功同步、连接异常、用户切换后产生的新 Run、会话删除或资源回收时结束；其中资源回收不改变标题。

## 错误与兼容

- 原生 title 不产生有效更新时，平台保留首条消息临时标题，不显示失败，也不生成猜测标题。
- child、默认 `New session - …`、空白、未知或绑定不一致的事件继续不能改平台标题。
- 已同步或用户手动修改的标题采用既有条件更新保护，后续事件不能覆盖。
- 仍使用 `session.updated` wire name 和既有前端消费字段，旧客户端可忽略新增的内部监听行为。

## 验证

- 单元测试：Run 成功后仍能处理晚到 root `session.updated`；后续非标题事件不会写入旧 Run；无效标题不结束为成功同步。
- 事件/SSE 测试：晚到同步事件可在 Run 已成功后被同一 SSE 订阅接收。
- 本地真实验证：在 3000 新建首轮对话，等待 OpenCode 原生 `session.updated` 后，页面标题自动变为原生标题；不依赖固定的标题生成时长。
