# 包说明：com.enterprise.testagent.workspace

## 职责

Workspace 和文件管理业务包，负责工作区注册、查询、服务器目录选择、文件路径归一化、越权路径拒绝、UTF-8 文件读写、文件状态查询、工作区与引用资产的只读组合视图、设置页初始应用版本工作区创建进度、应用版本工作区和个人工作区运行编排、应用引用资产库多服务器副本，以及公共级/工作空间级 Agent 配置文件与 Git 发布编排。

## 不负责

- 不定义 HTTP Controller 或 API DTO。
- 不直接调用 opencode server 或 generated SDK。
- 不实现数据库 Repository。
- 不维护应用人员、代码库配置或 SSH key 配置 CRUD，这些属于 configuration-management。

## 主要程序清单

- `WorkspaceApplicationService`：工作区注册、分页查询、详情查询和文件服务编排。
- `WorkspaceFileService`：文件系统访问、root 归一化、大小限制和越权路径拦截。
- `WorkspaceDirectoryService`：列出目标后端服务器上的一层子目录，仅供超级管理员服务器工作空间选择器使用。
- `ManagedWorkspaceApplicationService`：应用成员校验、设置页工作空间模板 + 初始版本工作区创建、进度表更新、应用版本工作区 clone/接管、通用参数路径根目录读取、每服务器版本副本、目标 commit 广播同步、个人 git worktree、最近使用、diff、同步和版本工作区 git pull 编排；个人发布先本地提交，再按白名单从个人 HEAD 投影到应用 feature worktree 后提交、推送和广播，不合并个人分支。
- `AgentConfigApplicationService`：公共级/工作空间级 Agent 配置目录选择、读写、文件目标服务器归属查询、公共 worktree 切换列表、公共 Git 更新、worktree 创建、diff、stage/unstage、commit、publish、进度快照和公共配置广播同步；直接发布和 worktree 合并发布复用 `GitPublishWorkflow`。
- `ReferenceRepositoryApplicationService`：应用资产库列表、分支初始化/受控切换、generation 同步与只读实际指针核验、当前平台规范化绝对目录的可空展示、总体/服务器状态、单层安全目录树、广播消费、数据库租约 worker、Git 副本安全落盘和离线/恢复补偿编排。
- `ReferenceRepositoryReplicaReconciler`：默认 60 秒扫描数据库目标，恢复广播丢失、Java 重启和 `DEFERRED` 服务器重新上线。
- `ReferenceRepositoryResponses`：引用资产库可空服务器路径、总体目标、内部操作类型、逐服务器在线/实际指针/匹配状态和目录树业务响应模型。
- `WorkspaceViewApplicationService` 与 `WorkspaceView*` 模型：从最新 JSONC 建立可验证引用集合，按 `merge/sdd-folder-name` 生成稳定节点身份、来源、冲突、只读 locator 和局部 warning，并在读取时重新执行应用关联、本机 READY 副本、参数根目录和路径安全校验。
- `GitPublishWorkflow`：封装高风险 Git 发布写入流程，统一 clean、fetch、pull --ff-only、merge、冲突文件收集、merge abort、push 和 headCommit 返回。
- `AgentConfigResponses`、`AgentConfigProgressEvent`、`AgentConfigProgressSink`：Agent 配置 API 返回对象与 WebSocket 进度发布端口。
- `ManagedWorkspaceResponses`：应用版本工作区 API 使用的业务响应模型，由 API 层统一包装。
- `FileTreeEntryResponse`、`FileContentResponse`、`FileStatusResponse`：原始工作区文件业务返回模型，由 API 层包装；引用组合视图使用独立返回模型，避免把只读引用路径误当作可写 workspace path。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Context。
- Jackson Databind（JSONC 引用元数据解析）。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence` 实现细节。
- `test-agent-opencode-sdk-generated`。

## 修改时必须同步更新

- `backend/test-agent-workspace-management/README.md`。
- `docs/api/http-api.md`，如果文件或 workspace API 行为变化。
- `docs/api/event-stream.md`，如果长耗时工作区进度机制变化。
- `docs/standards/backend.md`，如果测试策略变化。
