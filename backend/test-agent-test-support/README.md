# test-agent-test-support

## 工程定位

测试支撑模块，供单元测试、集成测试和后续端到端测试复用。

## 技术栈

- Java 21
- Spring Boot Test optional
- Maven library jar

## 主要职责

- 测试 fixture。
- mock opencode server 支撑。
- 测试数据构造器。
- 集成测试公共断言工具。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Boot Test。

## 禁止依赖

- 生产运行时代码反向依赖本模块。
- App 主流程。
- 真实外部服务调用。

## 后续 AI 编码指引

新增测试夹具、mock server、测试辅助 builder 时改这里。不要把生产代码逻辑放进本模块。
