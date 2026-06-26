# test-agent-configuration-management

## 工程定位

应用配置管理业务模块，承载应用定义只读消费、应用成员、应用与代码库关联、应用工作空间模板和个人 SSH key 管理。

## 边界

- 不接入运行态 Workspace / Session / Run。
- 不执行 clone、fetch 或启动会话；Git 目录读取只使用远端只读命令。
- 不创建应用版本工作区或个人 worktree；这些运行编排属于 `test-agent-workspace-management`。
- 版本库英文名称由本模块在新增/编辑时校验并统一小写保存；设置页创建工作空间时的初始版本工作区 clone/checkout 由 `test-agent-api` 委托 `test-agent-workspace-management` 执行。
- 不定义 HTTP Controller，API 入口放在 `test-agent-api`。
- 不实现 JDBC，持久化由 `test-agent-persistence` 通过领域 repository 端口提供。

## 依赖

### 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Context。

### 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence` 实现细节。
- `test-agent-opencode-sdk-generated`。

## 主要接口

- `ConfigurationManagementApplicationService`：配置管理编排服务。
- 代码库新增/编辑会校验 `englishName` 为 1 到 29 位英文字母、非空唯一，并统一小写；历史数据允许为空，但后续创建应用版本工作区会被 workspace-management 拒绝。
- `SshKeyEncryptionService`：包装 common 模块的 SSH 私钥 AES-GCM 加解密和 SHA-256 指纹生成能力，保持配置管理业务入口稳定。
- `CommonParameterManagementApplicationService`：通用参数管理编排服务，提供分页列表查询（可按平台过滤）与「仅修改 value」更新；不提供新增/删除，参数不存在抛 `NOT_FOUND`，空值抛 `VALIDATION_ERROR`。
- `ConfigurationManagementResponses`：对 API 层安全暴露的响应模型，不包含私钥明文或密文。

## 配置项

- `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY`：个人 SSH 私钥加密密钥，要求为 Base64 编码的 16/24/32 字节 AES key；未配置时 SSH key 新增和 Git SSH 读取会返回平台错误。
