# 包说明：com.enterprise.testagent.testsupport

## 职责

测试支撑包，为单元测试、集成测试和端到端测试提供 fixture、mock server、测试数据构造器和公共断言。

## 不负责

- 不提供生产运行时代码。
- 不被生产模块依赖。
- 不调用真实外部服务。

## 主要程序清单

- `package-info.java`：说明 testsupport 包是测试 fixture 和集成测试支撑边界。
- 后续可新增 mock opencode server、测试 builder、fixture、断言工具和测试容器配置。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Boot Test。
- 测试相关 mock 和断言库。

## 禁止依赖

- 生产运行时代码反向依赖本模块。
- App 主流程。
- 真实外部服务调用。

## 上游调用方

- 后端单元测试。
- 后端集成测试。
- 未来端到端测试。

## 下游依赖

- 测试框架。
- mock server。
- 可选 Testcontainers。

## 测试位置

- 本模块自身工具测试。
- 使用本模块的各业务模块测试。

## 修改时必须同步更新

- `backend/test-agent-test-support/README.md`。
- `docs/standards/backend.md`。
- 受影响测试模块的 README 或 PACKAGE.md。

