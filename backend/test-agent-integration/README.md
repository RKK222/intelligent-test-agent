# test-agent-integration

## 工程定位

非 opencode 外部系统联动业务模块。

## 当前能力

- 从 `src/main/resources/toolbox/catalog-v1.json` 加载锁定 IT-Tools / OmniTools 的版本化离线目录，启动时校验 193 项的稳定 ID、深链接、双语字段、分类和顺序。
- `ToolboxCatalogService` 通过显式生产构造器注入点击仓储，合并累计点击投影，并按累计数、最后计数时间和目录顺序计算正点击 Top 10。
- 点击只信任当前登录用户、服务端时钟和 traceId；`eventId` 幂等、用户/工具 30 秒窗口竞争和累计原子更新由领域仓储端口完成。
- 无效、已剔除或不在当前目录的 `toolId` 返回统一 `NOT_FOUND`，不为离线不可用工具提供入口。
- `LobehubSsoApplicationService` 复用当前平台用户和会话，签发 32 字节、默认 60 秒且不越过会话的一次性票据；
  兑换时完成用户状态复核、部门 NFKC/空白/英文大小写摘要、虚拟邮箱、owner/admin/member 映射和 30 天模型
  委托轮换。
- 启用后会在落票据前校验固定聊天 origin、虚拟邮箱域和唯一 owner，拒绝 migration 占位值；ticket/grant/HMAC
  时限只能在安全上限内收紧，nonce TTL 不得短于 120 秒的完整重放窗口。
- `LobehubHmacAuthenticator` 对原始 body 的五行 canonical string 验证 HMAC-SHA256、时钟偏差和 nonce 防重放；
  只在签名通过后原子占用 nonce。
- `LobehubDevelopmentOwnerResolver` 只供显式 `test/local` 开发启动使用：优先校验显式或已有 owner；仍为占位值时，
  只从状态正常、部门非空的超级管理员中选择唯一候选，零个或多个候选均失败关闭。
- `WorkflowCapabilityHmacAuthenticator` / `WorkflowCapabilityApplicationService` 为Python workflow和Runner提供固定client/runner身份的HMAC防重放、平台session marker、当前用户/角色/应用成员/仓库复核、checkout票据与模型grant编排。Java只复用平台能力，不创建任何工作流业务对象；Runner兑换时才解密个人SSH Key，并用RSA-OAEP封装随机AES密钥、AES-256-GCM加密任意长度私钥的`TAEC1`信封重新封装，避免直接RSA加密真实OpenSSH私钥时超过明文上限。
- `ExternalUserSshKeyApplicationService` 只查询状态正常用户的现有 SSH Key，复用平台解密校验后立即交给 `ExternalSshKeyEnvelopeService`；后者按 TAEK1 使用 API Key、HKDF-SHA256、AES-256-GCM 和绑定 traceId 的 AAD 加密，私钥明文不离开方法局部。该服务同时保留生产构造器与包内可测试构造器，生产构造器必须显式标记为 Spring 注入入口，并由容器装配测试锁定，避免多构造器场景回退到不存在的无参构造器。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain` 中的工具点击仓储端口与用户标识。
- Spring context、WebFlux、Jackson 目录加载和 Spring Boot 配置绑定；不依赖 Web Controller。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。

## 后续 AI 编码指引

新增外部系统联动时先判断是否属于 opencode；opencode 相关放 `test-agent-opencode-runtime`，其他系统联动放本模块。

工具目录变更必须先修改两套锁定派生源码，再运行 `toolbox-source/scripts/generate_catalog.py` 和 `verify_platform_contract.py`；不能手工只改 JSON 数量或给需要公网/安全上下文的工具补入口。

## 验证

`ToolboxCatalogServiceTest` 验证目录与生产装配。

`RunnerPublicKeyEncryptionServiceTest` 覆盖超过RSA-OAEP明文上限的真实长度私钥可由版本化混合信封往返解密。
`ExternalSshKeyEnvelopeServiceTest` 固化 TAEK1 测试向量并覆盖错误 Key/AAD/密文篡改；`ExternalUserSshKeyApplicationServiceTest` 覆盖 scope、用户状态、404 收敛、旧格式 409 和无明文响应。
`LobehubSsoApplicationServiceTest` 覆盖停用/空部门、票据时限、同名部门规范化、角色、grant 轮换与用户实时
状态，以及启用但仍为占位配置时不持久化票据；`LobehubHmacAuthenticatorTest` 覆盖签名伪造、时钟边界与
溢出、nonce 重放及原始 body 绑定；`LobehubDevelopmentOwnerResolverTest` 覆盖唯一自动候选、显式 owner 和
多候选拒绝。

```bash
mvn -q -DappLogDir=target/log -pl test-agent-integration -am test
```
