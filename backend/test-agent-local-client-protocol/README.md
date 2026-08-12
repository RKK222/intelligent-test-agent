# test-agent-local-client-protocol

本模块定义后台 Java 与本地 OpenCode 客户端共同使用的反向 WebSocket 协议。协议版本固定为
`local-opencode-client.v1`；所有消息都通过 `LocalClientFrame` 封装，并携带请求、链路和连接换代字段。

文件与 HTTP 数据使用不超过 256 KiB 的原始字节分片，分片内容以 Base64 放入 JSON 帧。协议模块只定义
稳定线格式和校验，不依赖服务端领域模型，也不包含任何密钥持久化逻辑。
