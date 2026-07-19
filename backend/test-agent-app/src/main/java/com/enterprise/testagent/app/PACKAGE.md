# 包说明：com.enterprise.testagent.app

## 职责

唯一 Spring Boot 应用入口包，只承载启动、运行装配、profile 配置、migration、health 和日志等可部署服务入口能力。

## 不负责

- 不定义 HTTP/SSE/WebSocket 协议入口。
- 不保存请求/响应 DTO、WebFilter 或 WebSocket handler。
- 不承载 workspace、session、run、runtime、terminal、web 等业务包。
- 不直接调用 generated SDK。
- 不直接实现文件、会话、运行、终端或外部系统联动业务。

## 主要程序清单

- `TestAgentApplication`：Spring Boot 启动类，扫描 `com.enterprise.testagent` 下的后端组件。
- `config.TestAgentRuntimeProperties`：运行时配置绑定。
- `config.DatabaseMigrationRunner`：运行态 Flyway migration 入口。
- `config.RuntimeJsonConfig`：应用运行态共享 Jackson ObjectMapper 配置。
- `config.RedisHealthIndicator`：Redis 必需依赖健康检查。

## 允许依赖

- `test-agent-api`。
- `test-agent-system-management`、`test-agent-integration`。
- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- `test-agent-persistence`、`test-agent-event`、`test-agent-opencode-client`，仅限运行装配、migration、health 和 seed。
- Flyway Core 和 PostgreSQL database support。
- Spring Boot WebFlux、Security、Actuator、Validation、Log4j2。

## 禁止依赖

- `test-agent-opencode-sdk-generated`。
- API 协议实现和业务流程实现。
- workspace、session、run、runtime、terminal、web 等业务源码包。

## 上游调用方

- Java 进程启动脚本、Docker、运维健康检查。

## 下游依赖

- API 模块提供的 HTTP/SSE/WebSocket 入口。
- 业务模块提供的工作区、opencode 运行态、系统管理和外部集成能力。
- scheduler 模块提供的后台调度框架，应用默认开启扫描，可通过配置显式关闭。
- night-execution 由 opencode-runtime 提供业务实现；应用层仅绑定 scheduler USER_PLAN 并发参数，15 分钟全局容量由数据库通用参数通过显式内存 SPI 在 Spring 单例装配完成时严格加载。
- persistence、event、observability 和 opencode-client 提供的基础运行能力。

## 测试位置

- `backend/test-agent-app/src/test/java` 只保留启动装配、profile、health、migration 和模块边界测试。
- opencode health 测试必须验证整体 UP/DOWN 聚合和错误详情脱敏；Redis health 测试必须覆盖必需依赖不可达场景。
- profile 配置测试必须覆盖 test/prod 外部服务注入，不允许把真实密钥或固定主机写死在代码。
- API 与业务行为测试迁移到对应模块。

## 修改时必须同步更新

- `backend/test-agent-app/README.md`。
- `backend/README.md`。
- `docs/architecture/dependency-rules.md`，如果依赖边界变化。
- `docs/standards/security.md`，如果涉及运行态安全配置。
