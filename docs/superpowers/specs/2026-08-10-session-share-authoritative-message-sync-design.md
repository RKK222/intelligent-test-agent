# 分享会话用户消息权威同步设计

## 背景与根因

分享会话中的任一参与方发送消息后，其他已打开页面必须立即看到同一条完整用户消息。现有 Redis 摘要模式会把本轮输入放入运行快照，但 legacy RunEvent SSE 的初始恢复明确排除了 user message，旧实现假设当前页面一定拥有发送端本地乐观消息。多人协作时非发送方没有该乐观消息，只能先收到 OpenCode 不带正文的 user envelope，于是生成空白气泡；该 envelope 缺少平台发送人归因时还会被误判为查看者自己。

## 设计决策

平台保存的 `session_messages` 是用户输入正文和实际发送人归因的权威来源。legacy RunEvent SSE 建连时，恢复服务先按精确 `sessionId + runId + USER` 读取本轮平台消息，立即投影一条既有 wire type `message.updated`；随后再接续 OpenCode assistant snapshot、durable RunEvent 和 live bus。Redis 摘要模式继续使用现有 input snapshot，不增加第二份投影。

平台输入投影使用 `remote_message_id` 作为运行时 message id，使后续 OpenCode user envelope/part 与同一条消息原地合并；同时携带平台 message id、正文、`senderUserId`、`senderUnifiedAuthId` 和 `sentBySharedUser`。前端仍通过分享参与人目录把 user id 补成姓名，不向 OpenCode 传递分享身份。

前端时间线对“正文为空且没有可见附件”的临时 user envelope 不渲染气泡。该 envelope 仍保留在 reducer 中，以便后续平台权威输入或 OpenCode part 按 message id 原地补齐，不影响 Run、Todo 和 assistant 事件的归属。

## 数据流与顺序

1. 发送方取得唯一活动 Run 准入，平台保存带 Run 和实际发送人归因的 USER `session_messages`。
2. 其他参与方从分享 runtime-state 得到 busy `runId`，订阅该 Run 的既有 RunEvent SSE。
3. legacy SSE 初始恢复先输出平台 USER `message.updated`；Redis SSE 首帧继续输出包含 input snapshot 的 `run.snapshot.reset`。
4. OpenCode 后续 user envelope、user part、assistant、思考、工具和终态事件按稳定 message/run id 合并，各方得到相同投影。
5. 断线重连重复执行权威输入恢复；前端 event/message 幂等逻辑避免重复气泡。

## 边界与兼容性

- 不新增 HTTP URL、请求头、DTO 字段或 RunEvent wire type。
- 不新增数据库列、索引或 Flyway migration；查询复用既有 `(session_id, run_id, created_at, id)` 索引。
- legacy 旧数据缺少 `remote_message_id` 时退回平台 message id，仍能显示正文；新 Run 使用稳定 dispatch id 与 OpenCode 合并。
- 平台输入恢复失败时记录既有安全日志并继续 RunEvent 流，不因辅助快照中断 assistant/终态事件。
- 不修改 OpenCode 源码或 generated SDK，不改变现有分享鉴权、停止权限和执行所属人语义。

## 测试

- 后端单元测试：OpenCode 不可用或尚无 user snapshot 时，legacy `recover` 仍先返回完整平台 USER 消息及发送人归因。
- MyBatis 集成测试：精确 `sessionId + runId` 只返回该 Run 的 USER 消息。
- 前端组件测试：空 user envelope 不产生空白气泡，正文到达后同一 message id 正常显示。
- 分享工作台 Playwright：B 已打开分享会话时，A 发起 Run 后 B 立即看到 A 的正文和姓名，且不存在空白 user bubble。
