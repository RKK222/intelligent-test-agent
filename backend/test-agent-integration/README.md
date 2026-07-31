# test-agent-integration

## 工程定位

非 opencode 外部系统联动业务模块。

## 当前能力

- `UiTestExecutionClient` 把单行四列案例结构化提交到独立 `uitest6` 平台，并用 `requestId` 复用外部幂等语义；提交和状态查询都是短 HTTP 请求，不在 Java 内持有浏览器执行线程。
- `UiTestExecutionSettings` 只从 Java 配置读取外部地址和 Bearer Token；未配置时失败关闭，Token 不进入 OpenCode Tool、响应或日志。
- 从 `src/main/resources/toolbox/catalog-v1.json` 加载锁定 IT-Tools / OmniTools 的版本化离线目录，启动时校验 193 项的稳定 ID、深链接、双语字段、分类和顺序。
- `ToolboxCatalogService` 通过显式生产构造器注入点击仓储，合并累计点击投影，并按累计数、最后计数时间和目录顺序计算正点击 Top 10。
- 点击只信任当前登录用户、服务端时钟和 traceId；`eventId` 幂等、用户/工具 30 秒窗口竞争和累计原子更新由领域仓储端口完成。
- 无效、已剔除或不在当前目录的 `toolId` 返回统一 `NOT_FOUND`，不为离线不可用工具提供入口。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain` 中的工具点击仓储端口与用户标识。
- Spring context、WebFlux 和 Jackson。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。

## 后续 AI 编码指引

新增外部系统联动时先判断是否属于 opencode；opencode 相关放 `test-agent-opencode-runtime`，其他系统联动放本模块。

工具目录变更必须先修改两套锁定派生源码，再运行 `toolbox-source/scripts/generate_catalog.py` 和 `verify_platform_contract.py`；不能手工只改 JSON 数量或给需要公网/安全上下文的工具补入口。

## 验证

`UiTestExecutionClientTest` 使用本机临时 HTTP server 锁定四列字段、Bearer Token、traceId、状态映射和安全错误；`ToolboxCatalogServiceTest` 继续验证目录与生产装配。

```bash
mvn -q -DappLogDir=target/log -pl test-agent-integration -am test
```
