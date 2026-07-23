# test-agent-opencode-sdk-generated

当前源码由 OpenCode 1.18.4 官方 `/doc` 使用 OpenAPI Generator 7.24.0 生成，并由 `tools/generate-opencode-java-sdk.sh` 自动同步到本模块。

## 工程定位

opencode server OpenAPI spec 生成的 Java SDK 模块。该模块是原始 API client，不写平台业务逻辑。

## 技术栈

- Java 21 编译
- Spring WebClient generated client
- Reactor
- Jackson
- Jakarta annotations
- Maven library jar

## 主要职责

- 存放从 `tools/opencode-sdk-generator` 拷贝过来的 generated Java 源码。
- 提供 `com.example.opencode.sdk.ApiClient`、API 类和模型类。
- 作为 `test-agent-opencode-client` 的底层依赖。

## 允许依赖

- Spring WebFlux/WebClient。
- Reactor。
- Jackson。
- Jakarta annotations。

## 禁止依赖

- `test-agent-domain`。
- `test-agent-persistence`。
- `test-agent-app`。
- 任意平台业务模块。

## 后续 AI 编码指引

不要手工修改 `src/main/java/com/example/opencode/sdk/**` 下的 generated Java 源码。需要更新 SDK 时，先重新运行 `tools/generate-opencode-java-sdk.sh`，再把生成源码同步到本模块。
