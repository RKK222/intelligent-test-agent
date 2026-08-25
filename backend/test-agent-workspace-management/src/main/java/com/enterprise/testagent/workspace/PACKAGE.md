# 包说明：com.enterprise.testagent.workspace

## 职责

Git 提交身份通过 `ScmGitIdentityResolver` 优先读取独立 SCM 姓名证据；右控明确返回姓名不一致时只在邮箱和本次实际姓名完全匹配后重建提交并重推一次，不对平台同名数字后缀做字符串猜测。

Workspace 和文件管理业务包，负责工作区注册、当前用户及受控排查目标用户的只读查询、服务器目录选择、文件路径归一化、越权路径拒绝、UTF-8 文件读写、文件状态查询、工作区与引用资产的只读组合视图、设置页初始应用版本工作区创建进度、应用版本工作区和个人工作区运行编排、每两小时的服务器/本地客户端 Git 权限巡检、应用引用资产库多服务器副本、应用源码快照的固定提交物化/打开/清理，以及公共级/工作空间级 Agent 配置文件与 Git 发布编排。

## 不负责

- 不定义 HTTP Controller 或 API DTO。
- 不直接调用 opencode server 或 generated SDK。
- 不实现数据库 Repository。
- 不维护应用人员、代码库配置或 SSH key 配置 CRUD，这些属于 configuration-management。

## 主要程序清单

- `WorkspaceApplicationService`：工作区注册、分页查询、详情查询和文件服务编排。
- `UserWorkspaceQueryService`：按明确目标用户列出其 ACTIVE 个人工作区及其历史会话引用的 ACTIVE 工作区；只返回逻辑身份，不暴露物理目录。
- `WorkspaceFileService`：文件系统访问、root 归一化、一次性读写阈值、渐进预览和越权路径拦截；`WorkspaceFileUpload` 管理有界分片上传会话。
- `Utf8FilePreviewReader`、`FilePreviewChunkResponse`：以固定约 512 KiB 内存按 UTF-8 字符边界读取大文件，返回字节偏移、EOF、大小和修改时间快照，允许安全渐进到文件末尾。
- `BinaryFileChunkReader`、`FileBinaryChunkResponse`：以固定约 512 KiB 内存按原始字节读取 Base64 下载分段，返回偏移、EOF、大小和修改时间快照，文件变化时拒绝继续拼接。
- `WorkspaceDirectoryService`：列出目标后端服务器上的一层子目录，仅供超级管理员服务器工作空间选择器使用。
- `ExperienceWorkspaceApplicationService`：解析本服务器体验目录、建立稳定 Workspace 身份与实时访问边界；直接启动 Java 时仅兜底初始化目录、Git 与 README，标准 `docs/spec` 内容由随部署包交付的 Shell 模板补齐。
- `ManagedWorkspaceApplicationService`：应用成员校验、版本选择前按当前用户身份执行 Git 远端只读访问预检、设置页工作空间模板 + 初始版本工作区创建、进度表更新、应用版本工作区 clone/接管、通用参数路径根目录读取、每服务器版本副本、目标 commit 广播同步、个人 git worktree、最近使用、diff、同步和版本工作区 git pull 编排；个人发布先本地提交，再按白名单从个人 HEAD 投影到应用 feature worktree 后提交、推送和广播，不合并个人分支。
- `WorkspaceGitAccessInspectionService` / `WorkspaceGitAccessInspectionTaskHandler`：XXL 每两小时扫描服务器应用工作空间的当前用户 Git 远端只读权限，把确定失效、可访问或暂时未知结果持久化为安全投影，并广播本地客户端巡检唤醒；网络异常和旧客户端能力缺失保持 `UNKNOWN`，不误置灰。
- `AgentConfigApplicationService`：公共级/工作空间级 Agent 配置目录选择、读写、文件目标服务器归属查询、公共 worktree 切换列表、公共 Git 更新、worktree 创建、diff、stage/unstage、commit、publish、进度快照和公共配置广播同步；还在服务器级 Redis 租约下定时补偿已有 ACTIVE OpenCode binding 但缺失稳定公共个人 worktree 的超级管理员，补偿只复用本机共享仓库，不读取用户 SSH key 或切换运行态；直接发布和 worktree 合并发布复用 `GitPublishWorkflow`。
- `AgentSkillHubApplicationService` / `AgentSkillHubResponses`：组合平台 push 与 SkillHub 接口目录；定时同步只保存外部元数据，预览、引用和更新时以当前认证主体统一认证号调用新版下载接口并校验、物化 ZIP，外部上传同样由服务端补入统一认证号；远端下架后隐藏发现入口但保留当前应用引用，push 时按内容摘要原子保持外部身份或转成可追溯的平台派生资产。
- `ReferenceRepositoryApplicationService`：应用资产库列表、分支初始化/受控切换、generation 同步与只读实际指针核验、携带 expected generation 的管理员终止、当前平台规范化绝对目录的可空展示、总体/服务器状态、单层安全目录树、本机/广播/补偿/重试/取消唤醒、数据库租约 worker、Git 副本安全落盘和离线/恢复补偿编排。
- `ReferenceRepositoryReplicaTaskDispatcher`：以仓库 generation 去重的本机有界异步调度器，默认两个 worker、最多 256 个 key，支持立即和按退避时刻执行，并能取消等待任务或中断运行线程，避免 HTTP 与 Redis listener 线程承载阻塞 Git。
- `ReferenceRepositoryReplicaReconciler`：默认 60 秒扫描数据库目标，恢复广播丢失、Java 重启和 `DEFERRED` 服务器重新上线。
- `ReferenceRepositoryResponses`：引用资产库可空服务器路径、总体目标、内部操作类型、逐服务器在线/实际指针/匹配状态和目录树业务响应模型。
- `AppSourceApplicationService`：应用源码仓库/分支/目录树查询、固定 commit 物化受理、当前 generation 保留期调整、同 generation 重试、操作进度授权、打开与最近选择编排；续期以仓库/slot/cleanup 锁串行同步 snapshot expiry、索引 SHA 和清理计划，清理开始后拒绝。retry 重放先按客户端不可变身份返回原 operation，TEAM 进度按 repository 任一当前启用关联应用成员授权，PERSONAL 保留 owner/成员管理员边界。所有入口实时复核应用、成员、关联、仓库类型与生命周期权限，最近选择只对确定性失效删除偏好，显式清除先执行完整打开鉴权。
- `AppSourceMaterializationRegistrar`、`AppSourceReplicaRetryRegistrar`、`AppSourceReplicaResultRecorder`：在 Spring 事务中实现仓库行锁、同 operationId 先于可变 slot 校验的严格身份重放与首请求冻结目标恢复、cleanup 第一写、generation/operation/replica/固定 13 步建档、不可变 retry 身份收敛、租约结果 fencing，以及槽位锁下串行的 active 提升、旧代到期、全失败/部分失败和历史 stranded 恢复；retry 只有自身 SERVER steps 全终态才允许聚合。
- `DefaultAppSourceReplicaTaskDispatcher`、`AppSourceReplicaWorker`、`AppSourceReplicaProgressRecorder`、`AppSourceReplicaStepCatalog`、`AppSourceGitAccessResolver`：本机总容量有界且按 repository/generation/server 去重的异步派发、低敏集群唤醒、启动及默认 5 秒副本/stranded 数据库补偿扫描、当前服务器数据库租约、精确 operation 绑定、活租约下稳定时间线 reset/推进/终态化，以及只在 Git 命令期使用的操作人 SSH 身份；旧步骤使用固定泛化低敏摘要兼容收敛。
- `AppSourceGitMaterializer`、`AppSourceIndexManager`、`AppSourceWorkspaceOpener`：浅克隆、冻结 SHA 显式 fetch、no-cone sparse checkout、固定 commit、全程同一临时 SSH 凭据、特殊路径/`.`/符号链接/子模块边界、配置根下逐段 `NOFOLLOW_LINKS` 校验、无 `.git` 原子目录发布、数据库 completion 前失败磁盘回滚、completion 后 backup best-effort 清理、权威 SHA 索引修复，以及当前 generation/server Runtime Workspace 打开。
- `AppSourceCleanupTaskHandler`、`AppSourceCleanupWorker`、`AppSourceCleanupResultRecorder`：XXL 每分钟全局唤醒、本机 cleanup 租约、同根文件锁、逐段不跟随链接的路径复核、generation fence、保留/修复索引、Workspace 归档与失败退避。
- `WorkspaceViewApplicationService` 与 `WorkspaceView*` 模型：从最新 JSONC 建立可验证引用集合，按 `merge/sdd-folder-name` 生成稳定节点身份、来源、冲突、只读 locator 和局部 warning，并在读取时重新执行应用关联、本机 READY 副本、参数根目录和路径安全校验。
- `GitPublishWorkflow`：封装高风险 Git 发布写入流程，统一 clean、fetch、pull --ff-only、merge、冲突文件收集、merge abort、push 和 headCommit 返回；可能生成 commit 的发布入口必须显式传入非空当前用户 Git 身份。
- `AgentConfigResponses`、`AgentConfigProgressEvent`、`AgentConfigProgressSink`：Agent 配置 API 返回对象与 WebSocket 进度发布端口。
- `ManagedWorkspaceResponses`：应用版本工作区 API 使用的业务响应模型，由 API 层统一包装。
- `FileTreeEntryResponse`、`FileContentResponse`、`FilePreviewChunkResponse`、`FileBinaryChunkResponse`、`FileStatusResponse`：原始工作区文件业务返回模型，由 API 层包装；引用组合视图使用独立返回模型，避免把只读引用路径误当作可写 workspace path。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-scheduler`（只消费 `ScheduledTaskHandler` 契约）。
- Spring Context。
- Spring Transactions。
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
