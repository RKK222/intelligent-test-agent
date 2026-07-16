# 包说明：com.enterprise.testagent.observability

## 职责

观测性包，提供 traceId、日志字段、Micrometer 指标和后续 OpenTelemetry 接入边界。

## 不负责

- 不承载具体业务流程。
- 不访问数据库和 opencode server。
- 不定义 Controller。

## 主要程序清单

- `package-info.java`：说明 observability 包是日志、trace 和指标边界。
- `TraceConstants`：traceId 相关 header、attribute 和 Reactor context key。
- `TraceIdSupport`：traceId 生成、校验和入站值解析工具。
- `TraceLogContext`：traceId 的 SLF4J MDC 写入、恢复和清理工具。
- 后续可新增指标命名、meter 封装和观测性配置。

## 允许依赖

- `test-agent-common`。
- SLF4J API。
- Spring Boot Actuator。
- Micrometer。

## 禁止依赖

- generated SDK。
- persistence 实现。
- app Controller 或业务 application service。

## 上游调用方

- `test-agent-app` 入口和过滤器。
- opencode client facade。
- persistence 和 event 中的关键流程。

## 下游依赖

- `test-agent-common`。
- SLF4J API、Micrometer 和日志框架。

## 测试位置

- observability 模块单元测试。
- traceId 传播、日志字段、指标注册测试。

## 修改时必须同步更新

- `backend/test-agent-observability/README.md`。
- `docs/standards/backend.md`。
- `docs/standards/security.md`，如果涉及日志脱敏。
