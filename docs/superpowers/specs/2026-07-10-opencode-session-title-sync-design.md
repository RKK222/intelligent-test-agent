# OpenCode 会话标题同步设计

## 目标

复用 OpenCode 1.17 已有的首条消息自动标题能力，把 root OpenCode session 发出的 `session.updated` 标题同步到平台 Session，并在当前 Web 工作台立即显示新标题。

## 现状与根因

OpenCode 会在首条用户消息后由内置 `title` agent 生成标题，并发送 `session.updated`。平台已将该原生事件映射为 `RunEventType.SESSION_UPDATED`，但 `RunApplicationService` 只持久化/广播 RunEvent，没有更新平台 `Session.title`。前端 SSE client 的已知事件列表也没有订阅 `session.updated`，工作台只使用创建 Session 时从首条输入截取的临时标题。

## 设计

后端在 `RunApplicationService` 处理流式事件时，仅当 `RunSessionScopeRouter` 已确认事件属于 root session（`isChildSession=false` 且 `sessionId/rootSessionId` 均为当前 root OpenCode session）才读取标题。读取顺序为 `payload.info.title`，再兼容 `payload.rawPayload.properties.info.title`。标题非空时复用现有 `Session.updateTitleAndPinned` 与 `SessionRepository.save` 更新对应平台 Session；child、未知归属或空白标题不得覆盖 root 平台 Session 标题。RunEvent 原有持久化和实时发布语义保持不变。

前端在 `event-stream-client` 的已知 SSE 事件中加入 `session.updated`。`AgentWorkbench` 收到 root session 标题事件后，更新当前 Session 和已加载历史列表，并使 sessions 查询失效以便其它视图刷新；payload 缺少或空白标题时忽略。

## 兼容性与边界

- 不新增 API、DTO、数据库字段或 Flyway migration；复用既有 RunEvent 与 Session 持久化接口。
- 不修改 generated SDK，不接入第三方插件，也不改变 OpenCode title agent 的生成策略。
- 只消费 root OpenCode session 的标题，避免子 Agent 会话影响主会话名称。
- 旧 OpenCode 或旧事件缺少 `info.title` 时保持平台首条输入标题，兼容已有用户数据。

## 验证

- 后端定向测试：经 `RunSessionScopeRouter` 路由后的 root `session.updated.info.title` 更新平台 Session；child、空标题及 raw 包装以外的无归属事件不更新。
- 前端定向测试：SSE client 订阅 `session.updated`；标题提取兼容标准与 raw 包装 payload。
- 前端 typecheck 与后端模块测试通过后，使用 `.env.test` 启动三服务，在页面首轮对话后确认历史列表和当前会话标题变为 OpenCode 自动生成标题。
