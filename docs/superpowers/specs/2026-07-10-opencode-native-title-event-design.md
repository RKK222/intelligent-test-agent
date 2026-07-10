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
3. 推荐：复用首轮 Run 已经建立的同一条 OpenCode 事件流。终态后流仅保留仍有效的 root `session.updated`，不重新订阅，因此不存在标题事件空窗；下一轮、手动改名、归档或删除会主动关闭等待。这个连接不按标题生成耗时关闭，只有其会话生命周期事件才会关闭。

## 数据流

1. 平台创建首轮 root Run 后、提交 prompt 前，为 `(平台 session、远端 root session、Run)` 注册一个不可复用的监听代际 token；token 只存在承载该 Run 的既有后端事件订阅中，不新增跨 Java 的共享事件所有权。
2. Run 订阅有明确状态机：`ACTIVE → TITLE_WAIT → CLOSED`。`ACTIVE` 保持既有完整事件路由和终态处理；只有 root `run.succeeded` 到达、token 仍有效且平台标题仍为首条消息临时标题时才进入 `TITLE_WAIT` 并在终态 payload 标注 `platformSessionTitlePending=true`。`TITLE_WAIT` 在调用 `RunSessionScopeRouter` 前仅放行目标远端 root session 的 `session.updated`，丢弃消息、工具、child 和其它下一轮事件；`CLOSED` 不再消费远端流。
3. 收到有效非默认标题时，先复核 token 仍有效，再使用标题条件更新（期望值为首条消息临时标题）持久化。只有更新成功才追加带 `platformSessionTitleSynchronized=true` 的既有 `session.updated` RunEvent；更新失败也结束等待，不发布确认事件。
4. 当前页面在 `platformSessionTitlePending=true` 时继续订阅该 Run 的既有 SSE；收到确认标题、切换会话或开始下一轮才关闭。它立即更新标题和消息列表，不新增轮询或 HTTP 接口。离开页面后标题仍已持久化，下次会话/历史查询直接读取新标题。
5. 终态前原主流已经同步有效标题时立即注销 token，不标记 pending。成功同步、条件更新失败（用户已改名）、会话归档/删除时，原子注销 token；新 Run 只注销处于 `TITLE_WAIT` 的旧 token，`ACTIVE` Run 继续遵循既有互斥、取消和终态链路，不能被标题逻辑中断。
6. 标题等待阶段的远端连接暂时断开时，只有 token 仍有效才按既有运行时路由重连同一条 Run 事件订阅。上游 SSE 无 cursor/replay，因此重连成功后立即使用既有 session 路由读取远端 root session 的当前 title；读取到有效非默认标题时也走同一 token + 条件更新 + RunEvent 通知链路，弥补断线窗口。该补偿是状态读取，不调用 title agent；不依据生成时长结束等待。进程停止或用户显式关闭会话时按既有连接生命周期释放。

## 错误与兼容

- 原生 title 不产生有效更新且远端 session 当前 title 仍无效时，平台保留首条消息临时标题，不显示失败，也不生成猜测标题；等待不以固定时长断开。
- child、默认 `New session - …`、空白、未知、过期代际或绑定不一致的事件继续不能改平台标题。
- 已同步或用户手动修改的标题采用条件更新保护；更新失败时不发布确认事件，并注销该待命名 token。
- 新 Run 在提交 prompt 前使旧 token 失效，避免同一远端 root session 的下一轮事件落入旧 Run；旧页面会收到既有终态后的流关闭并清理待定订阅。
- 仍使用 `session.updated` wire name 和既有前端消费字段；只在终态 payload 增加可选 `platformSessionTitlePending`，旧客户端可忽略。

## 验证

- 单元测试：首轮注册、状态机 `ACTIVE/TITLE_WAIT/CLOSED`、终态后有效 root `session.updated`、默认标题忽略、child 忽略、过期 token 忽略、下一轮只注销 `TITLE_WAIT`、归档/删除注销、条件更新失败不发布事件、终态前已同步时不进入等待。
- 断线测试：标题等待连接重连后读取远端 session 当前 title，并以 token + 条件更新补发确认事件；远端仍为默认标题时继续等待。
- 事件/SSE 测试：Run 已成功后，当前页面保持的同一 Run SSE 能收到标题确认；终态前已同步则不保持待定订阅。
- 前端测试：`platformSessionTitlePending=true` 时 Run 终态不关闭 SSE；收到确认标题或切换会话时关闭；旧客户端不依赖新字段仍可正常结束展示。
- 本地真实验证：在 3000 新建首轮对话，等待 OpenCode 原生 `session.updated` 后，页面标题自动变为原生标题；不依赖固定的标题生成时长。
