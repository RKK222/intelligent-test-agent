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
3. 推荐：用户工作区级共享标题事件桥。首轮 Run 在发出 prompt 前注册一次待命名会话；该用户工作区只有一条 OpenCode 事件订阅，桥只处理已注册远端 root session 的 `session.updated`。收到有效原生标题后做条件更新、追加既有 RunEvent 并从待命名集合移除。桥在集合为空时关闭；连接异常时仅在集合仍非空时重连。不以墙钟超时关闭或代替标题。

## 数据流

1. 平台创建首轮 root Run 后、提交 prompt 前，为 `(用户、工作区、平台 session、远端 root session、Run)` 注册一个不可复用的监听代际 token。
2. 用户工作区级标题事件桥复用既有 OpenCode 事件入口；它只接受已注册远端 root session 的 `session.updated`，不处理消息、工具、下一轮 Run 或 child session 事件。
3. 原主 Run 流继续负责回答和 `run.succeeded`。终态 payload 在标题仍为临时标题时标注 `platformSessionTitlePending=true`；页面完成回答但继续订阅此 Run 的 SSE，直至收到确认标题、切换会话或发起下一轮。
4. 桥收到有效非默认标题时，先复核代际 token 仍有效，再使用标题条件更新（期望值为首条消息临时标题）持久化。只有更新成功才追加带 `platformSessionTitleSynchronized=true` 的既有 `session.updated` RunEvent。
5. 当前页面经仍保持的既有 Run SSE 收到该事件，立即更新标题和消息列表；不新增轮询或 HTTP 接口。离开页面后标题仍已持久化，下次会话/历史查询直接读取新标题。
6. 成功同步、条件更新失败（用户已改名或原主流已同步）、新 Run 开始、会话归档/删除时，原子注销对应 token。桥仅在所属用户工作区仍有待命名会话时保持连接；暂时断线会重连，不以生成时长结束监听。

## 错误与兼容

- 原生 title 不产生有效更新时，平台保留首条消息临时标题，不显示失败，也不生成猜测标题；桥不以固定时长断开。
- child、默认 `New session - …`、空白、未知、过期代际或绑定不一致的事件继续不能改平台标题。
- 已同步或用户手动修改的标题采用条件更新保护；更新失败时不发布确认事件，并注销该待命名 token。
- 新 Run 在提交 prompt 前使旧 token 失效，避免同一远端 root session 的下一轮事件落入旧 Run。
- 仍使用 `session.updated` wire name 和既有前端消费字段；只在终态 payload 增加可选 `platformSessionTitlePending`，旧客户端可忽略。

## 验证

- 单元测试：首轮注册、有效晚到 root `session.updated`、默认标题忽略、child 忽略、过期 token 忽略、下一轮注销、归档/删除注销、条件更新失败不发布事件。
- 事件/SSE 测试：Run 已成功后，当前页面保持的同一 Run SSE 能收到标题确认；终态前已同步则不保持待定订阅。
- 前端测试：`platformSessionTitlePending=true` 时 Run 终态不关闭 SSE；收到确认标题或切换会话时关闭；旧客户端不依赖新字段仍可正常结束展示。
- 本地真实验证：在 3000 新建首轮对话，等待 OpenCode 原生 `session.updated` 后，页面标题自动变为原生标题；不依赖固定的标题生成时长。
