# test-agent-configuration-management

## 工程定位

应用配置管理业务模块，承载应用定义只读消费、应用成员、应用与代码库关联、应用工作空间和个人 SSH key 管理。

## 边界

- 不接入运行态 Workspace / Session / Run。
- 不执行 clone、fetch 或启动会话；Git 目录读取只使用远端只读命令。
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
- `SshKeyEncryptionService`：个人 SSH 私钥 AES-GCM 加解密和 SHA-256 指纹生成。
- `ConfigurationManagementResponses`：对 API 层安全暴露的响应模型，不包含私钥明文或密文。

## 配置项

- `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY`：个人 SSH 私钥加密密钥，要求为 Base64 编码的 16/24/32 字节 AES key；未配置时 SSH key 新增和 Git SSH 读取会返回平台错误。
