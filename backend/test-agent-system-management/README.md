# test-agent-system-management

## 工程定位

系统内部管理业务模块，承载用户、角色、权限等平台管理能力。

## 当前状态

已完成用户认证相关的基础能力：

- **用户管理**：用户注册、查询、密码校验（BCrypt）。
- **认证服务**：用户登录（用户名+密码验证 -> 加载全局角色 -> Token 生成）、登出、Token 校验和刷新。
- **领域模型**：`User`、`UserLoginLog`、`Dictionary`、`UserRole`、`AuthPrincipal`、`TokenStore`。
- **测试造号与角色调整**：创建测试用户时使用事务同时写入用户和角色，调整角色时在同一事务内替换用户全局角色；当前测试管理入口由超级管理员直接操作，不包含普通用户审批通知流。
- **数据库 IDENTITY 运维**：查询/对齐/手动重启白名单表（users/user_roles/dictionaries/user_login_logs）的 identity 序列，修复序列落后于已有主键导致的新增冲突。

## 依赖

### 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Context、Spring TX（服务 Bean 与事务边界）。

### 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence`。
- `test-agent-opencode-sdk-generated`。

## 主要接口

- `UserDomainService`：用户注册、密码校验。
- `UserManagementApplicationService`：超级管理员测试用户查询、创建和单角色调整，调用用户、角色和字典领域仓储。
- `AuthApplicationService`：登录/登出/Token 刷新，调用 `UserRepository`、`TokenStore`、`UserLoginLogRepository`、`UserRoleRepository`、`DictionaryRepository`；登录时把 `ROLE` 字典值加载为 `AuthPrincipal.roles`。

## API 入口

认证 API 放在 `test-agent-api`，路径为 `/api/auth/`，通过 `AuthController` 暴露。

## 后续 AI 编码指引

新增用户、角色、权限等平台内部管理业务时优先改这里；API 入口放在 `test-agent-api`。
