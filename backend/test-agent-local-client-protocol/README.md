# test-agent-local-client-protocol

本模块定义后台 Java 与本地 OpenCode 客户端共同使用的反向 WebSocket 协议。协议版本固定为
`local-opencode-client.v1`；所有消息都通过 `LocalClientFrame` 封装，并携带请求、链路和连接换代字段。

文件与 HTTP 数据使用不超过 256 KiB 的原始字节分片，分片内容以 Base64 放入 JSON 帧。协议模块只定义
稳定线格式和校验，不依赖服务端领域模型，也不包含任何密钥持久化逻辑。

版本帧方向固定：客户端发送 `VERSION_CHECK`、`UPDATE_PREPARED`、`UPDATE_STATUS`；服务端发送
`VERSION_POLICY`、`UPDATE_COMMAND`、`UPDATE_APPLY`、`UPDATE_CANCEL`、`UPDATE_STATUS_ACK`。`REGISTER` 保留旧八字段
`clientKey/clientInstanceId/clientName/platform/architecture/clientVersion/opencodeVersion/reportedAddresses`，并以可选
字段追加 `unifiedAuthId`、`launcherVersion`、`capabilities`；只有恰好旧八字段且版本为 `0.1.0` 的客户端允许省略统一
认证号，新协议缺失或空白值失败关闭。
帧不含 Key、下载 URL 或签名原文；`UPDATE_*` 以 `commandId + clientInstanceId` 幂等，并受 connection generation
fencing 约束。只有同时携带 launcher 版本并声明 `SELF_UPDATE_V1` 的客户端才可双向使用版本帧。客户端持久化终态
result marker 并在重连后重报；服务端完成幂等持久化才返回精确匹配原命令 generation/status 的 ACK，客户端收到匹配
ACK 后清 marker。deadline 失败可由迟到 `SUCCEEDED/AUTO_ROLLED_BACK` 条件纠正；其它持久化终态冲突不返回 ACK。
目标版本大于当前为 `UPDATE`、小于为 `ROLLBACK`、相同为 `SAME`。
