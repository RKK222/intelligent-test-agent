# test-agent-model-gateway

## 工程定位

中立的企业模型接入模块。上游调用方是 `test-agent-api` 的 LobeHub 模型网关与既有 OpenCode 内部模型代理；
目录、探测、供应商密钥和每日用量通过 domain 端口取得或保存。

## 主要职责

- `ModelGatewayCatalogService` 只发布已启用 Provider、已启用模型且最近探测成功的能力，公开模型 ID 跨供应商
  唯一；解析后才把公开 ID 改写成上游 ID。
- `ModelCapabilityProbeService` 对九项能力使用固定最小样本和对应固定端点，统一覆盖可信 `Auth-Token`、UCID
  与 trace header；网络和非 2xx 只返回安全失败结果。
- `ModelGatewayForwardingService` 支持 JSON、SSE 与 transcription multipart，执行能力门禁、流式取消、分阶段
  timeout、固定上游错误、usage 有界提取和每日增量。连接超时为10秒；LobeHub响应头等待30秒，
  首个响应块与后续空闲仍分别限制为30秒和120秒。
- `OpenAiUpstreamSupport` 统一安全拼接 base URL、清除客户端同名可信 Header，以 `Auth-Token` 注入供应商
  Token，并注入 UCID/trace，
  以及过滤响应 hop-by-hop/sensitive header；既有 OpenCode proxy 复用它，但 Responses 转换仍留在原入口。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- Spring Context/WebFlux、Reactor 与 Jackson。

## 禁止依赖

- `test-agent-api`、`test-agent-persistence`、`test-agent-app`、generated SDK。
- Controller、Redis key、MyBatis mapper、供应商配置 UI 或 LobeHub fork Session/Workspace 逻辑。
- 保存 prompt、回答、UCID、原始错误、逐请求 trace 或配额状态。

违反 HTTP 协议边界时改 `test-agent-api`；目录管理用例改 `test-agent-configuration-management`；关系型或 Redis
实现改 `test-agent-persistence`；fork 的企业适配器改独立 LobeHub 仓库。

## 验证

```bash
mvn -q -DappLogDir=target/log -pl test-agent-model-gateway -am test
```

`ModelGatewayForwardingServiceTest` 覆盖九项能力门禁、JSON/SSE/multipart、错误脱敏和 usage；
上游错误正文被丢弃时还用 Netty 引用计数锁定 `DataBuffer` 已释放；`ModelCapabilityProbeServiceTest` 覆盖全部
探测端点；`OpenAiUpstreamSupportTest` 锁定 OpenCode 复用契约。
