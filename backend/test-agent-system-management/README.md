# test-agent-system-management

## 工程定位

系统内部管理业务模块，承载用户、角色、权限等平台管理能力。

用户全局角色替换先通过领域 `ConversationContextStore.beginUserMutation` 建立临时 gate；事务真正提交后原子再次失效并释放 gate，事务回滚只撤回自己的 gate token。gate 覆盖数据库写入窗口，避免撤权期间签发新 token；模块不依赖 Redis 实现或 persistence。

## 当前状态

已完成用户认证相关的基础能力：

- **用户管理**：用户注册、查询、密码校验（BCrypt）。
- **认证服务**：用户登录（用户名+密码验证 -> 加载全局角色 -> Token 生成）、登出、Token 校验和刷新。
- **领域模型**：`User`、`UserLoginLog`、`Dictionary`、`UserRole`、`AuthPrincipal`、`TokenStore`。
- **测试造号与角色调整**：创建测试用户时使用事务同时写入用户和角色，调整角色时在同一事务内替换用户全局角色；当前测试管理入口由超级管理员直接操作，不包含普通用户审批通知流。
- **存量用户清理与补全**：超级管理员可单个或批量删除没有会话、工作区、运行进程等受保护业务引用的账号，批量删除全有或全无并撤销登录 Token/运行上下文；已有业务数据的账号通过 TCDS 原位刷新姓名、研发部门和部门，保留原 `userId`、角色、应用成员和历史数据。TCDS 不返回应用成员关系，缺失应用仍由配置管理添加成员。
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
- `UserManagementApplicationService`：超级管理员测试用户查询、创建、单角色调整、安全删除和 TCDS 存量信息同步；外部查询完成后才开启短事务写入，删除通过领域端口清理账号附属数据并保护业务资产。
- `AuthApplicationService`：登录/登出/Token 刷新，调用 `UserRepository`、`TokenStore`、`UserLoginLogRepository`、`UserRoleRepository`、`DictionaryRepository`；登录时把 `ROLE` 字典值加载为 `AuthPrincipal.roles`。

## API 入口

认证 API 放在 `test-agent-api`，路径为 `/api/auth/`，通过 `AuthController` 暴露。

## 后续 AI 编码指引

新增用户、角色、权限等平台内部管理业务时优先改这里；API 入口放在 `test-agent-api`。
