# test-agent Backend

## 工程定位

基于 Maven multi-module 的单后端服务工程。只有 `test-agent-app` 负责产出可运行 Spring Boot 包，其余 `test-agent-*` 模块都是内部 library jar。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Maven 3.9+
- Spring WebFlux
- Log4j2
- Micrometer
- Druid JDBC 连接池
- OpenAPI Generator 生成的 opencode Java SDK

## 模块说明

| 模块 | 作用 |
|---|---|
| `test-agent-common` | 公共基础模型与工具 |
| `test-agent-domain` | 纯领域模型与状态机 |
| `test-agent-observability` | 日志、trace、指标等观测性封装 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK |
| `test-agent-opencode-client` | 业务侧 opencode client facade |
| `test-agent-workspace-management` | Workspace、文件、受控目录选择、git/diff、agent 和 skill 管理业务 |
| `test-agent-opencode-runtime` | Session、Run、RunEvent 编排、opencode runtime、Diff/revert 和 PTY terminal 业务 |
| `test-agent-system-management` | 用户、角色、权限等系统内部管理业务，包括用户注册、登录认证、Token 管理等 |
| `test-agent-integration` | 非 opencode 外部系统联动业务边界，目前为空骨架 |
| `test-agent-api` | HTTP/SSE/WebSocket API 定义、DTO、鉴权、限流、traceId 和统一异常入口 |
| `test-agent-persistence` | 持久化、迁移、Redis/PostgreSQL 访问 |
| `test-agent-event` | RunEvent、SSE、事件转换与回放 |
| `test-agent-test-support` | 测试支撑、fixture、mock server |
| `test-agent-app` | 唯一启动入口和唯一可部署后端服务包，不承载业务逻辑 |

## 构建方式

```bash
cd backend
mvn clean package -DskipTests
```

如果本机默认 `java` 不是 21，请显式指定 JDK 21：

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

## 测试与校验

跨模块修改完成后，默认在 `backend` 目录执行：

```bash
mvn test
```

针对局部模块可先使用 `mvn -pl <module> -am test` 快速验证，但合并前仍应跑全量后端测试，确保 common、domain、API、persistence、runtime 和 app 装配没有破坏依赖边界。

## 部署与运行

镜像构建、生产/测试 profile、dotenv、连接池和外部依赖配置见 `docs/deployment/backend.md`。

## 后续 AI 编码指引

- 新增可部署入口只允许放在 `test-agent-app`。
- 新增业务文件前先列出现有合适工程；无合适工程时按业务边界新建 Maven module。
- `test-agent-app` 只放启动、装配、profile、migration 和 health 等运行入口，不放 Controller 或业务服务。
- HTTP/SSE/WebSocket 入口放在 `test-agent-api`，旧 `/api/...` URL 必须保留，新 URL 同步写入 `docs/api/http-api.md`。
- Workspace、文件、git/diff、agent、skill 管理业务放在 `test-agent-workspace-management`。
- Session、Run、RunEvent、opencode runtime、Diff/revert、terminal 业务放在 `test-agent-opencode-runtime`。
- 用户、角色、权限等平台内部管理放在 `test-agent-system-management`。
- 非 opencode 外部系统联动放在 `test-agent-integration`。
- 业务模块不要直接依赖 `test-agent-opencode-sdk-generated`，应通过 `test-agent-opencode-client`。
- 领域模型保持在 `test-agent-domain`，不要依赖 Spring Web 或持久化技术。
- 对外成功/错误响应使用 `test-agent-common` 的 `ApiResponse` 和 `ApiErrorResponse`。
- HTTP 入口 traceId 使用 `X-Trace-Id`，由 `test-agent-observability` 和 `test-agent-api` 协作生成或透传。
- 后端运行态使用 Log4j2 作为 SLF4J 实际绑定，默认控制台日志为 `key=value` 结构化格式并输出 traceId。
