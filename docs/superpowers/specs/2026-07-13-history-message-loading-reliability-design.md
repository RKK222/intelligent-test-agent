# 历史消息加载可靠性优化设计

## 背景与问题

用户从“消息列表”选择历史会话时，页面偶发长时间显示“正在加载消息列表…”。运行日志已经确认：平台历史消息数据库查询通常只需数十毫秒，延迟来自同一切换过程中并发发起的 permission、question、Todo 等 OpenCode 运行态查询。

这些查询都会通过 `UserOpencodeProcessAssignmentService.requireReadyProcess` 调用公共 `OpencodeProcessStatusQueryService`，向同一 manager WebSocket 连接发送强健康检查。当前 WebSocket 出站通道使用 unicast sink，多个 bounded-elastic 线程并发调用 `tryEmitNext` 时可能返回非成功结果；发送方忽略结果后，manager 实际未收到命令，而 Java 侧 pending command 仍等待到 10 秒超时。前端又把历史正文首屏 loading 与 permission/question 查询放在同一个 `Promise.all` 中，因此单个健康检查命令丢失会直接阻塞消息展示。

## 目标

1. manager 控制命令在并发发送时不得静默丢失。
2. 发送失败必须立即进入统一错误链路，不得保留一个只能等待超时的 pending command。
3. 历史消息数据库快照返回且工作区切换完成后即可展示，不等待 permission/question 实时校准。
4. 历史切换期间继续保持发送锁，直到 Session、运行态和关联 Run/Diff 投影安全完成，避免向错误 Session 启动 Run。
5. 不变更 HTTP API、RunEvent SSE、数据库、generated SDK 或环境配置契约。

## 方案比较

### 方案一：只缩短 manager command timeout

可以减少最坏等待时间，但命令仍会丢失，启动、停止、健康检查等控制操作仍可能失败。该方案只改变症状，不采用。

### 方案二：只让前端提前结束 loading

可以改善历史消息首屏，但 manager 控制面的并发可靠性缺陷仍存在。该方案不能覆盖根因，不单独采用。

### 方案三：后端发送可靠性与前端首屏解耦同时修复

后端保证控制命令可靠入队或明确失败；前端把持久化正文作为首屏条件，把实时交互快照作为后台增强。该方案同时覆盖根因和用户体验，采用此方案。

## 后端设计

### 并发安全发送边界

`ManagerConnectionRegistry` 继续负责按 `containerId` 保存 manager 连接，但注册的 `ManagerCommandSender` 不再直接忽略 `Sinks.Many.tryEmitNext` 的结果。

WebSocket handler 为每条连接提供一个并发安全的发送函数：

- 对同一 outbound sink 的发送进行串行化，确保同一时刻只有一个线程执行 emission。
- 检查 `tryEmitNext` 返回的 `EmitResult`。
- 成功时正常返回。
- sink 已终止、已取消或出现其他不可恢复结果时抛出统一运行时异常，由 `SocketOpencodeProcessManagerGateway.send` 取消已创建的 pending command，再由既有异常映射返回平台错误。
- 不使用无限自旋，不在业务线程上等待 WebSocket 消费者处理消息。

该边界同时覆盖 health、start、restart、stop 和配置广播等复用同一连接发送器的控制消息。

### Pending command 生命周期

`SocketOpencodeProcessManagerGateway` 保持“先创建 pending，再发送命令”的顺序，避免 manager 极快响应时先于 pending 注册。发送函数抛错时继续执行既有 `pendingCommands.cancel(commandId)`，因此失败不会留下等待 10 秒的悬挂 future。

### 错误处理与观测

发送失败沿用统一 `PlatformException`/错误响应链路，不记录 token、命令环境或用户输入。日志只记录 containerId、消息类型、traceId 和 emission 结果；不得输出控制消息完整正文。

## 前端设计

### 首屏与增强数据拆分

历史切换仍在点击时立即设置 `historyLoadingSessionId`，防止旧 Session 内容残留。请求并发保持不变，以减少总耗时，但完成条件拆分为：

1. 读取平台历史消息 `listSessionMessages(..., refresh=false)`。
2. 完成历史 Session 所属工作区校验与切换。
3. 使用数据库消息快照重置并渲染时间线。
4. 清除仅用于正文首屏的 loading 展示。
5. permission、question、Session Tree 和 Todo 完成后，以历史切换代次 guard 校验结果仍属于当前 Session，再增量覆盖交互 dock、完整消息树和 Todo。

permission/question 任一失败时继续保留既有降级语义：失败项返回 `null`，不使用空数组误清除历史回放数据。增强请求失败不重新打开首屏 loading。

### 发送锁

当前 `historyLoadingSessionId` 同时承担视觉 loading 和发送锁，不再适合提前清除。新增独立的历史切换锁状态：

- `historyLoadingSessionId` 仅控制正文首屏加载提示。
- 独立锁保存仍在完成完整历史投影的 Session id。
- `FigmaChatPanel` 的 `historyLoading` 只绑定视觉状态。
- `AgentWorkbench.handleSend` 和传给聊天面板的只读/提交阻断状态继续受独立锁保护。
- 切换代次失效、错误或 finally 均只能清理自己持有的锁，迟到请求不得释放后启动的切换锁。

这样正文可以尽早展示，同时保持现有“历史投影完成前不能发送”的安全约束。

## 测试设计

### 后端测试

在 `ManagerControlWebSocketHandlerTest` 增加并发出站回归场景：注册连接后从多个线程同时发送控制命令，验证所有命令都能进入 WebSocket outbound，且不存在静默丢失。

补充发送 sink 已终止场景，验证 sender 明确抛错；配合 `SocketOpencodeProcessManagerGatewayTest` 既有异常取消路径，确认 pending command 不等待 timeout。

### 前端测试

在 `workbench.spec.ts` 增加历史正文与交互快照分离场景：

- permission/question 请求保持 pending。
- 数据库消息请求返回后，历史正文可见且“正在加载消息列表…”消失。
- 完整历史投影完成前发送按钮仍禁用。
- permission/question 释放后正确恢复对应 dock，随后发送锁按既有投影完成条件释放。

保留连续快速切换测试，确认旧 Session 的增强结果不能覆盖新 Session。

## 文档同步

实现完成后同步：

- `frontend/apps/agent-web/README.md`：历史首屏与后台交互快照语义。
- `backend/test-agent-api/README.md`：manager WebSocket 出站并发可靠性边界。
- `docs/api/http-api.md`：只补充既有 permission/question 历史恢复调用的非阻塞首屏语义，不改变路径或 DTO。
- `.agents/session-log.md`：记录根因、修复方式和验证结果。

## 兼容性与风险

- API、DTO、RunEvent、数据库字段均不变，旧前端和旧后端调用方不受影响。
- manager 控制消息顺序仍按实际发送竞争顺序串行化，不引入跨命令业务排序保证。
- 前端提前展示的是已有 PostgreSQL 消息快照，后续 Session Tree 仍可能用更完整的 OpenCode/Redis 历史替换时间线；这是现有两阶段恢复语义的显式化。
- 主要风险是视觉 loading 与发送锁拆分后遗漏清理路径，必须通过错误、快速切换和增强请求延迟测试覆盖。

## 完成标准

1. 并发 manager 控制命令测试稳定通过，不能通过延长 timeout 达成。
2. 历史正文测试证明 permission/question pending 时正文仍可展示。
3. 同一场景下发送按钮在完整投影完成前保持禁用。
4. 后端目标测试、前端目标测试、前端 typecheck、相关模块构建通过。
5. 文档、自检、session log 和中文 git 提交完成。
