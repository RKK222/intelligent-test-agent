# test-agent-system-management

## 工程定位

系统内部管理业务模块，承载用户、角色、权限和外部 API 凭据等平台管理能力。

用户全局角色替换先通过领域 `ConversationContextStore.beginUserMutation` 建立临时 gate；事务真正提交后原子再次失效并释放 gate，事务回滚只撤回自己的 gate token。gate 覆盖数据库写入窗口，避免撤权期间签发新 token；模块不依赖 Redis 实现或 persistence。

## 当前状态

已完成用户认证相关的基础能力：

- **用户管理**：用户注册、组合查询、手工用户名修正、密码校验（BCrypt）；统一认证首次登录创建用户时在同一短事务内授予 `USER` 普通用户角色，TCDS 外部查询不占用数据库事务；角色管理支持显式多用户和按筛选快照全选的一次性事务更新。手工修正只更新唯一用户名，不修改统一认证号或授权，也不撤销现有 Token；TCDS 后续同步可覆盖该用户名。
- **认证服务**：用户登录（用户名+密码验证 -> 加载全局角色 -> Token 生成）、登出、Token 校验和刷新。
- **领域模型**：`User`、`UserLoginLog`、`Dictionary`、`UserRole`、`AuthPrincipal`、`TokenStore`。
- **测试造号与角色调整**：创建测试用户时使用事务同时写入用户和角色，调整角色时在同一事务内替换用户全局角色；当前测试管理入口由超级管理员直接操作，不包含普通用户审批通知流。
- **权限即时失效**：用户删除、停用或角色调整除失效运行上下文外，还批量撤销该用户全部平台Token及对应session marker；Python workflow下一次精确Token读取或最长30秒SSE复核即失败。
- **存量用户清理与补全**：超级管理员可按角色（含未分配角色）、组织、研发部门、部门和用户关键字组合检索，手工勾选后批量设置待保存角色；也可单个或批量删除没有会话、工作区、运行进程等受保护业务引用的账号，批量删除全有或全无并撤销登录 Token/运行上下文。已有业务数据的账号通过 TCDS 原位刷新姓名、研发部门和部门，保留原 `userId`、角色、应用成员和历史数据。TCDS 不返回应用成员关系，缺失应用仍由配置管理添加成员。
- **数据库 IDENTITY 运维**：查询/对齐/手动重启白名单表（users/user_roles/dictionaries/user_login_logs）的 identity 序列，修复序列落后于已有主键导致的新增冲突。
- **问题排查只读授权**：`SupportAccessApplicationService` 为实时 `SUPER_ADMIN` 签发绑定当前平台登录会话的 5–240 分钟授权，同一会话只保留一个有效 grant；目标用户只作为查询范围，不切换 actor。签发时间在派生到期时间前统一归一化到 PostgreSQL `timestamp` 的微秒精度，使 Redis payload 与关系库授权身份在 Linux 纳秒时钟下仍严格一致，不通过误差窗口放宽校验。当前工程没有权威工单数据源，服务为每轮新上下文生成唯一 `sai_` 排查单号，不再从历史授权循环回填；未来接入工单时通过建议响应的 `source=WORK_ORDER` 区分。签发、撤销、目标切换、每次读取和失败均先落审计，审计失败时正文不返回；每日清理一年以前的审计。
- **外部 API 凭据**：`ExternalApiCredentialApplicationService` 仅保存平台生成 Key 的 RSA-OAEP/SHA-256 密文，支持 scope、启停、reveal、立即轮换和删除；事务提交后由 `ExternalApiCredentialUpdateBroadcaster` 本机整表刷新并发布空载荷服务器广播。`ExternalApiCredentialRegistry` 启动严格加载、每 60 秒补偿重载、完整构建后原子替换，并按工具编码 O(1) 常量时间认证。

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

- `UserDomainService`：用户注册、密码校验，以及统一认证首次建号与普通用户角色的原子写入。
- `UserManagementApplicationService`：超级管理员测试用户组合查询、创建、手工用户名修正、单个/批量单角色调整、安全删除和 TCDS 存量信息同步；改名复用现有用户仓储保存能力并保留统一认证号、权限和业务关系；批量角色调整单次最多 5000 人，排除当前操作者并把关系型修改放在一个事务内，Token 在事务前后各按整批撤销一次，避免旧页面按用户重复扫描 Redis；外部查询完成后才开启短事务写入，删除通过领域端口清理账号附属数据并保护业务资产。
- `AuthApplicationService`：登录/登出/Token 刷新，调用 `UserRepository`、`TokenStore`、`UserLoginLogRepository`、`UserRoleRepository`、`DictionaryRepository`；登录时把 `ROLE` 字典值加载为 `AuthPrincipal.roles`。
- `SupportAccessApplicationService`：实时复核登录会话、用户状态和数据库角色，校验/轮换 Redis grant 摘要，并通过 `SupportAccessRepository` 保存授权和一年期审计；不接受共享激活暗号，不依赖 persistence 实现类。
- `ExternalApiCredentialApplicationService` / `ExternalApiCredentialRegistry`：API Key 生命周期、RSA 密文、不可变认证快照和跨 Java 刷新；业务层只依赖领域 Repository/广播端口，不依赖 persistence 实现。

## API 入口

认证 API 放在 `test-agent-api`，路径为 `/api/auth/`，通过 `AuthController` 暴露。问题排查只读 API 位于 `/api/internal/platform/system-management/support-access/**`；API Key 管理位于 `/api/internal/platform/system-management/api-keys/**`，两者均由 API 模块执行 `SUPER_ADMIN` 鉴权。

## 测试覆盖

- `SupportAccessApplicationServiceTest` 覆盖无共享暗号签发、每次生成唯一排查单号、登录会话绑定、Linux 纳秒时钟与 PostgreSQL 微秒持久化兼容、实时角色撤销、成功读取先审计和审计不可用时正文 fail-closed。
- `ExternalApiKeyGeneratorTest`、`ExternalApiCredentialApplicationServiceTest`、`ExternalApiCredentialRegistryTest`、`ExternalApiCredentialUpdateBroadcasterTest` 覆盖 Key 格式、无明文持久化、CRUD/轮换、不可变快照、启动失败、本机/远端刷新与空广播载荷。

## 后续 AI 编码指引

新增用户、角色、权限等平台内部管理业务时优先改这里；API 入口放在 `test-agent-api`。
