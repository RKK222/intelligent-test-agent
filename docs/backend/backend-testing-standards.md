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
- Application service：使用 fake repository/facade 验证 workspace、session、run、cancel 编排和错误映射。
- File service：验证路径穿越拒绝、单层目录列表、UTF-8 读写和超大文件拒绝。
- Health/config：验证 local/prod properties binding、opencode node seed、Redis disabled/enabled health。

## 数据库测试

1. 新增表、字段、索引或约束必须有 migration 测试或集成验证。
2. 不允许只测实体类，不测 migration。
3. 唯一约束、外键约束、状态字段约束必须有失败场景。
4. 当前 persistence 集成测试使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Repository 映射、RunEvent append-only、增量读取和唯一约束。
5. `session_messages` 必须覆盖 save/find/page/count、业务 ID 唯一约束和按 session 分页排序。
6. 如果后续使用 PostgreSQL 专有能力，例如 JSONB、锁或 advisory lock，必须补充 Testcontainers 或等价 PostgreSQL 集成测试。
7. 测试环境 PostgreSQL 连通验证应启用 `test-agent-app` 的 `test` profile，并通过 `TEST_AGENT_TEST_DB_*` 环境变量注入主机、库名、账号和密码，禁止把真实凭据写入仓库配置或测试源码。
8. 数据库连接池使用 Druid，配置测试必须验证 `spring.datasource.druid.*` 能绑定为 Druid DataSource，且 Druid Web 控制台默认关闭。

## opencode client 测试

1. `OpencodeClientFacade` 优先通过 `OpencodeSdkGateway` stub 测试成功、超时、远端错误、取消、重试和 traceId 透传。
2. 事件转换测试必须覆盖已知 opencode raw type 和未知事件兜底。
3. 只有 `GeneratedOpencodeSdkGateway` 允许直接依赖 generated SDK；依赖边界需要用 `dependency:tree` 或 `rg "com\\.example\\.opencode\\.sdk"` 验证。
4. `startRun` 必须覆盖 `prompt_async` 成功、超时、5xx、连接失败、有限 retry 和 `X-Trace-Id` 透传。

## 本地集成脚本测试

1. 新增脚本必须支持 `--help`，并能在无密钥环境下运行帮助输出。
2. Compose 和脚本不得写入真实密钥；所有地址、端口、token 通过环境变量读取。
3. `tools/dev-health-check.sh` 必须覆盖 health 成功路径或本地服务未启动时的明确失败场景。

## 构建命令

后端基础构建命令：

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

有测试代码后，应优先运行目标模块测试，再运行全量必要测试。
