# test-agent-observability

## 工程定位

观测性支撑模块，为单后端服务提供日志、trace、指标等公共封装。

## 技术栈

- Java 21
- Spring Boot Actuator
- SLF4J API
- Micrometer
- Maven library jar

## 主要职责

- TraceId 传播和日志字段约定。
- Micrometer 指标命名和公共 meter 封装。
- 后续 OpenTelemetry 接入点。

## 已有能力

- `TraceConstants`：统一 `X-Trace-Id`、WebExchange attribute 和 Reactor context key。
- `TraceIdSupport`：合法 traceId 透传，缺失或非法 traceId 生成新值。
- `TraceLogContext`：统一 traceId 的 SLF4J MDC key、写入、恢复和清理逻辑。

## 允许依赖

- `test-agent-common`。
- SLF4J API。
- Spring Boot Actuator。
- Micrometer。

## 禁止依赖

- generated SDK。
- Persistence 实现。
- App Controller 或业务 application service。

## 后续 AI 编码指引

新增指标、trace 标签、日志 MDC 或观测性拦截器时改这里；不要把具体 Workspace/Run 业务流程写进本模块。
