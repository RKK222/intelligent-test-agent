# 后端测试规范

测试要求遵循“改什么补什么测试”。

## 通用要求

1. 新增行为必须有对应测试，除非是纯文档或纯空骨架。
2. 修改已有行为必须补回归测试。
3. 修复 bug 必须先能复现问题，再验证修复。
4. 测试名称应描述业务场景和预期结果。
5. 测试数据优先放在 `test-agent-test-support`。

## 分层测试

- Controller/API：验证参数校验、状态码、统一错误格式、traceId、鉴权和限流。
- Domain：验证状态机、领域规则、值对象约束和边界条件。
- Persistence：验证 Repository 映射、唯一约束、事务、Flyway migration。
- Event/SSE：验证事件类型、seq 单调递增、Last-Event-ID 续传、断线重连。
- Opencode client facade：使用 mock opencode server 验证错误转换、超时、重试和事件映射。
- Observability：验证 traceId 传播、日志字段、关键指标注册。

## 数据库测试

1. 新增表、字段、索引或约束必须有 migration 测试或集成验证。
2. 不允许只测实体类，不测 migration。
3. 唯一约束、外键约束、状态字段约束必须有失败场景。
4. 当前 persistence 集成测试使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Repository 映射、RunEvent append-only、增量读取和唯一约束。
5. 如果后续使用 PostgreSQL 专有能力，例如 JSONB、锁或 advisory lock，必须补充 Testcontainers 或等价 PostgreSQL 集成测试。
6. 测试环境 PostgreSQL 连通验证应启用 `test-agent-app` 的 `test` profile，并通过 `TEST_AGENT_TEST_DB_*` 环境变量注入主机、库名、账号和密码，禁止把真实凭据写入仓库配置或测试源码。
7. 数据库连接池使用 Druid，配置测试必须验证 `spring.datasource.druid.*` 能绑定为 Druid DataSource，且 Druid Web 控制台默认关闭。

## opencode client 测试

1. `OpencodeClientFacade` 优先通过 `OpencodeSdkGateway` stub 测试成功、超时、远端错误、取消、重试和 traceId 透传。
2. 事件转换测试必须覆盖已知 opencode raw type 和未知事件兜底。
3. 只有 `GeneratedOpencodeSdkGateway` 允许直接依赖 generated SDK；依赖边界需要用 `dependency:tree` 或 `rg "com\\.example\\.opencode\\.sdk"` 验证。

## 构建命令

后端基础构建命令：

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

有测试代码后，应优先运行目标模块测试，再运行全量必要测试。
