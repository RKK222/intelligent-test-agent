# test-agent-configuration-management

## 工程定位

应用配置管理业务模块，承载应用定义查询与超级管理员新建、应用成员、应用与代码库关联、应用工作空间模板和个人 SSH key 管理。

## 性能优化

### 目录加载优化（2026-06-29）

**问题：** 大型仓库加载目录列表超时

**解决方案：** 使用无blob克隆技术，只下载目录结构不下载文件内容

**技术细节：**
- 使用 `git clone --filter=blob:none` 参数，只下载 commit 和 tree 对象
- 结合 `--sparse` 稀疏检出，只检出目录结构
- 性能提升：数据传输量减少 > 99%，加载速度提升 10-100 倍
- 要求：Git 2.22+ 版本

**详细文档：** 见 `OPTIMIZATION.md`

### 远端目录树加载（2026-07-07）

设置页创建应用工作空间使用应用维度远端树接口读取目录和文件结构：

- `ConfigurationManagementApplicationService.listRepositoryTree(appId, repositoryId, branch, currentUserId)` 校验应用启用、代码库已关联到应用和当前用户 SSH key 后，调用 `GitRemoteService.listTree()`。
- 树接口通过 `git archive --remote` 解析 tar header 生成 `directory/file` 树，不使用 `GitCloneCacheService`，不会 clone/fetch 到本地磁盘。
- 测试工作库按 `ApplicationDefinition.appName` 过滤，只返回与当前应用同名的根目录及其子树；非测试工作库返回远端全量树。
- 旧 `listRepositoryDirectories()` 目录列表接口保留兼容。

## 边界

- 不接入运行态 Workspace / Session / Run。
- 不执行 clone、fetch 或启动会话；Git 目录和树读取只使用远端只读命令。
- 不创建应用版本工作区或个人 worktree；这些运行编排属于 `test-agent-workspace-management`。
- 版本库英文名称由本模块在新增/编辑时校验并统一小写保存；版本库类型读取通用字典 `REPOSITORY_TYPE`，新增时由类型派生旧 `standard` 兼容字段；版本库部署模式按每个代码库保存，内部模式只入库 `host[:port]/path`，分支/目录读取时用当前用户统一认证号动态拼接 `ssh://{unifiedAuthId}@`；设置页创建工作空间时的初始版本工作区 clone/checkout 由 `test-agent-api` 委托 `test-agent-workspace-management` 执行。
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
- `createApplication()` 校验应用 ID/名称非空和数据库字段长度，拒绝重复 ID，并通过 MyBatis 配置管理仓储写入默认启用的应用定义；API 层只允许 `SUPER_ADMIN` 调用。
- 代码库新增/编辑会校验 `englishName` 为字母、数字、连字符 1 到 128 位，首尾不能是连字符，非空唯一，并统一小写；内部模式创建时 `englishName` 为空会从 Git 路径派生（去掉 `.git`，`/` 替换为 `-`）。新增代码库优先使用 `repositoryType`，`TEST_WORK_REPOSITORY` 写旧 `standard=true`，应用代码库和应用资产库写 `standard=false`，旧客户端未传 `repositoryType` 时仍按 `standard` 推导类型；历史数据允许英文名为空，但后续创建应用版本工作区会被 workspace-management 拒绝。
- `listRepositoryTypes()` 从通用字典表返回版本库类型下拉选项，字典缺失会让新增请求返回统一 `VALIDATION_ERROR`，避免 API 和 DB 字典不一致；`repositoryDeploymentOptions()` 返回默认部署模式、内部 SSH 前缀和内外部模式选项，默认模式读取 `test-agent.deployment.mode`，不依赖 `test-agent-app` 配置类。
- `SshKeyEncryptionService`：包装 common 模块的 SSH 私钥 AES-256-GCM + RSA-OAEP/SHA-256 混合解密和 SHA-256 指纹生成能力，保持配置管理业务入口稳定。
- `listRepositoryTree()`：返回已关联版本库指定分支的远端目录/文件树，测试工作库只暴露当前应用同名目录子树。
- `CommonParameterManagementApplicationService`：通用参数管理编排服务，提供分页列表查询（可按平台过滤）与受控 value 更新；不提供新增/删除，参数不存在抛 `NOT_FOUND`，空值抛 `VALIDATION_ERROR`，只读参数（`editable=false`）抛 `VALIDATION_ERROR`「该通用参数为只读参数，修改后将影响系统正常运行」。前端只允许更新 `editable=true` 的通用参数（`OPENCODE_MANAGER_MAX_PROCESSES`、公共 Git 地址 `OPENCODE_PUBLIC_AGENT_GIT_URL`），其它通用参数为只读，必须通过部署配置、数据库迁移或对应初始化流程调整。公共 Git 地址不拆内部/外部两个参数；通用参数编辑弹窗复用 `repositoryDeploymentOptions()` 选项，内部模式展示当前用户 SSH 前缀但只保存 `host[:port]/path`，公共 Agent Git 操作由 workspace-management 按保存值形态决定是否拼接 SSH URL。
- `ConfigurationManagementApplicationService.removeMember` 在成员删除前建立 user mutation gate，不为 app→Session 反查数据库；gate 覆盖整个关系型写入窗口并阻断该用户签发/续期/路由，保存成功后原子再次失效并释放自己的 gate token，失败时只撤回自己的 token。该粗粒度策略会安全地同时失效该用户其它应用的上下文。`CommonParameterUpdateBroadcaster` 的本地和跨 Java 重载事件由 runtime 过滤三个可信路径参数并提升上下文全局代次。
- `RepositoryCommonParameterValues`：通用参数运行态读模型，每次读取都通过 Repository 从数据库获取最新值，按当前平台读取并展开 `${englishName}`、环境变量 `$NAME`、路径开头 `$HOME` 和 `~/`；消费方应使用 `resolvedValue` 而不是数据库原始值，不得把通用参数缓存在 JVM 或 Redis 中。`${NAME}` 在通用参数未命中时才回退环境变量。被引用参数按「解析上下文平台」查找（先该平台、再回退 `all`）；`all` 行由调用方以当前 JVM 平台或目标平台作为上下文，因此 `all` 参数也能引用平台参数（如 `SYS_DATA_ROOT_DIR` 仅有平台行、无 `all` 行）。`SYS_DATA_ROOT_DIR` 是系统数据根目录通用参数，macOS 默认值 `$HOME/.testagent` 也通过该解析链路展开。
- `ConfigurationManagementResponses`：对 API 层安全暴露的响应模型，不包含私钥明文或密文。

## 配置项

- `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH`：平台持久 PKCS8 PEM RSA 私钥路径。生产必须配置权限为 0600 的文件；共享同一数据库的全部 Java 必须使用同一私钥，否则用户 SSH key 会在跨节点或重启后无法解密。旧 `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY` 已作废。
