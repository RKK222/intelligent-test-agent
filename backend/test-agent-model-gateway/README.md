# test-agent-model-gateway

## 工程定位

中立的企业模型接入模块。上游调用方是 `test-agent-api` 的 LobeHub、Memory 模型网关与既有 OpenCode 内部模型代理；
目录、探测、供应商密钥和每日用量通过 domain 端口取得或保存。

## 主要职责

- `ModelGatewayCatalogService` 只发布已启用 Provider、已启用模型且最近探测成功的能力，公开模型 ID 跨供应商
  唯一；解析后才把公开 ID 改写成上游 ID。
- `ModelCapabilityProbeService` 对九项能力使用固定最小样本和对应固定端点，统一覆盖可信 `Auth-Token`、UCID
  与 trace header；EMBEDDING 还固定发送 `X-Embedding-Input-Type: query`，兼容内部 CPU BGE 的显式向量语义；
  网络和非 2xx 只返回安全失败结果。
- `ModelGatewayForwardingService` 支持 JSON、SSE 与 transcription multipart，执行能力门禁、流式取消、分阶段
  timeout、固定上游错误、usage 有界提取和每日增量。连接超时为10秒；交互式LobeHub响应头等待30秒，
  Memory 抽取请求允许120秒冷启动；首个响应块与后续空闲仍分别限制为30秒和120秒。
- `OpenAiUpstreamSupport` 统一安全拼接 base URL、清除客户端同名可信 Header，以 `Auth-Token` 注入供应商 Token，
  并注入 UCID/trace；企业上游的 `Authorization: Bearer` 模式只能完成鉴权、不会让 UCID 生效，
  因此平台调用固定使用 `Auth-Token`，
  以及过滤响应 hop-by-hop/sensitive header；既有 OpenCode proxy 复用它，但 Responses 转换仍留在原入口。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- Spring Context/WebFlux、Reactor 与 Jackson。

## 禁止依赖

- `test-agent-api`、`test-agent-persistence`、`test-agent-app`、generated SDK。
- Controller、Redis key、MyBatis mapper、供应商配置 UI 或 LobeHub fork Session/Workspace 逻辑。
- 保存 prompt、回答、UCID、原始错误、逐请求 trace 或配额状态。

## 已知风险：上游鉴权模式尚未显式建模

当前 Provider 目录只保存 Token 关联，没有 `authMode`；Java 模型网关按企业模型既有契约统一使用
`Auth-Token`，以保证企业上游同时识别 UCID。内部 CPU BGE 为接入同一目录同时兼容运维 Bearer 与平台
`Auth-Token`，但这不代表所有 OpenAI-compatible Provider 都支持 `Auth-Token`：后续接入只接受
`Authorization: Bearer` 的第三方服务时，探测和正式调用都可能失败。

短期不得根据 Provider ID、模型 ID 或 Base URL 在 Java 中写死 Bearer 特判。长期演进应在 Provider 配置中
显式增加 `AUTH_TOKEN/BEARER` 等受控鉴权模式，由现有 `OpenAiUpstreamSupport` 与
`ModelGatewayForwardingService` 统一选择上游请求头，并补齐数据库/API/管理页面兼容升级与密钥脱敏测试。

违反 HTTP 协议边界时改 `test-agent-api`；目录管理用例改 `test-agent-configuration-management`；关系型或 Redis
实现改 `test-agent-persistence`；fork 的企业适配器改独立 LobeHub 仓库。

## 验证

```bash
mvn -q -DappLogDir=target/log -pl test-agent-model-gateway -am test
```

`ModelGatewayForwardingServiceTest` 覆盖九项能力门禁、JSON/SSE/multipart、错误脱敏和 usage；
上游错误正文被丢弃时还用 Netty 引用计数锁定 `DataBuffer` 已释放；`ModelCapabilityProbeServiceTest` 覆盖全部
探测端点；`OpenAiUpstreamSupportTest` 锁定 OpenCode 复用契约。

Memory 调用只接受 `/chat/completions` 与 `/embeddings` 上的集群 HMAC，不再签发短期 `mfg_` grant。API 层必须验证 body SHA-256、client/user/run/session/operation、timestamp、nonce、capability、签名和 embedding `query/document` 类型，并把 nonce 交 Redis 原子防重放；CHAT 只允许管理设置中的固定模型，Embedding 只允许企业 profile 或固定 CPU profile。调用身份固定标记为 `memory`，HMAC 不适用于模型目录、音频或其它端点。
