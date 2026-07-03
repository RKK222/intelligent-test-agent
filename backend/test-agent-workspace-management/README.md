# test-agent-workspace-management

## 工程定位

Workspace、文件管理、应用版本工作区、个人工作区、git/diff、agent 和 skill 管理业务模块。

## 主要职责

- 工作区注册、查询和分页。
- 工作区注册时记录 `linuxServerId`，并通过 `WorkspaceServerIdentity` 提供当前 Java 进程所属服务器和默认目录。
- 工作区内文件单层列表、UTF-8 内容读写、文件状态、普通文件删除和路径越权拦截。
- 文件 WebSocket ticket 创建前通过 `requireWorkspaceOnCurrentServer` 校验 workspace、当前后端和用户 opencode 进程同服务器；历史空服务器归属工作区在 root path 校验成功后回填当前服务器 ID。
- 普通前端不再传物理目录创建 Workspace；应用版本和个人工作区目录由后端按通用参数与业务 id 派生。超级管理员服务器工作空间选择器通过目标后端目录浏览能力从该后端 Java 进程运行目录开始浏览。
- Agent/Skill 配置管理：公共级读取 `OPENCODE_PUBLIC_AGENT_GIT_URL`、`OPENCODE_PUBLIC_CONFIG_GIT_ROOT`、`OPENCODE_PUBLIC_CONFIG_DIR`、`OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT`，文件树根为 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/`，`skills/` 下直接放实际技能包；工作空间级文件树根为 `{workspace.rootPath}/.opencode/`，可维护 `agents/` 与 `skills/` 技能包。支持只读浏览、SUPER_ADMIN 写入、Git diff/stage/commit/publish、公共配置更新、worktree 模式和 `agent-config.public-sync-requested` 广播同步。工作区 Git Diff 与 Agent 配置 Diff 共用 `GitWorkspaceService` 的 porcelain 解析和 diff 聚合；Git 写入发布共用 `GitPublishWorkflow` 统一执行 clean、fetch、pull --ff-only、merge、push、headCommit 和冲突后 merge abort。工作空间级 Agent Git diff 只扫描 `.opencode/` 下的 `agents/` 与 `skills/`，返回给 UI 时去掉 `.opencode/` 前缀，避免普通应用文件误进入 agents 分组。公共 Git 地址缺失或为 `UNCONFIGURED` 时，禁用原因会明确提示超级管理员到“系统管理 → 通用参数管理”配置该参数。公共配置初始化/更新会在 Git 根目录缺失或为空目录时 clone 到 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT`，并校验 `OPENCODE_PUBLIC_CONFIG_DIR` 存在且非空；已有仓库的未提交修改不再阻断只读文件树，更新默认拒绝覆盖，只有请求显式携带 `discardLocalChanges=true` 时才恢复已跟踪文件再拉取。公共 worktree 创建不再首次 clone，只允许在管理员选择的已初始化服务器上创建，并把 `worktreeId -> linuxServerId` 落库。公共 worktree 切换列表按 `scope=PUBLIC`、`linuxServerId` 和 `status=ACTIVE` 查询，并补充创建人 `userId/username`；后续 Agent/Skill 配置文件目录列表/读取/写入由 API 层按服务器归属签发文件 WebSocket ticket 执行，Git 操作仍按服务器归属路由。Git 命令失败会通过统一 Git 错误码返回安全归因和 `gitFailureHint`，worktree 合并冲突会返回安全的 `conflictFiles` 列表，便于前端提示认证、仓库、网络、分支或冲突文件问题。
- 基于配置管理中的应用工作空间模板创建应用版本工作区，clone 指定分支并创建运行态 `Workspace`；设置页创建应用工作空间时会复用该能力同步创建初始版本工作区，并按 `workspace_create_operations` 记录“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”进度。
- 应用版本工作区根目录优先读取 `common_parameters.OPENCODE_APP_WORKSPACE_ROOT`，路径片段包含安全化版本号和代码库 `englishName`；历史代码库缺少英文名称时拒绝创建新的版本工作区。新建或显式修复的应用版本、服务器副本和托管运行态 `Workspace.rootPath` 入库保存 `appworkspace:<versionSegment>/<repositoryEnglishName>[/<templateDirectory>]` 逻辑路径；接口响应和 Git/文件/PTY/Run 执行前统一解析为当前服务器物理路径。内部部署模式版本库执行 clone/fetch/pull/push 前会按当前操作人统一认证号拼接 `ssh://{unifiedAuthId}@{gitUrl}` 并刷新 origin，接管已有仓库时忽略 origin 中的 `ssh://任意用户@` 前缀后再比较数据库保存片段。旧 Unix/Windows 绝对路径只兼容读取，不批量迁移。
- 基于应用版本工作区副本创建个人 git worktree，根目录优先读取 `common_parameters.OPENCODE_PERSONAL_WORKTREE_ROOT`，记录最近使用工作区，并支持个人/应用目录差异、双向文件同步、版本工作区 `git pull --ff-only` 和跨服务器副本广播同步。默认个人空间使用 `{应用版本分支}_{userId}_default` 作为分支名和物理目录末段，物理路径形如 `{OPENCODE_PERSONAL_WORKTREE_ROOT}/{version}/{userId}/{repositoryEnglishName}/{应用版本分支}_{userId}_default/`，入库保存 `personalworktree:<versionSegment>/<userId>/<repositoryEnglishName>/<branch>[/<templateDirectory>]` 逻辑路径；后续自定义私人空间把末段 `default` 替换为自定义名称，二者共用同一套个人 worktree 创建、运行态 Workspace 保存和 recent 偏好写入流程。创建时同 JVM 内串行化；同名分支已存在时复用分支挂载 worktree，目标目录已存在且分支匹配时接管；如果同一分支登记在旧路径且新规范路径不存在，会先用 `git worktree move` 重挂载到新规范路径，避免显式创建或修复 default 私人工作区时反复报 worktree 创建冲突。创建个人 worktree、发布和同步前都会先确保当前服务器的应用版本副本可用；当前服务器无 READY 副本时按 `OPENCODE_APP_WORKSPACE_ROOT` 创建本机副本，禁止用历史版本主表旧绝对路径伪造 READY replica。个人工作区发布和同步到应用版本副本均复用 Git 原生 merge 能力：普通发布先同步应用副本并把应用分支合入个人分支；个人分支已有未完成 merge 且冲突已解决时不再重复 fetch/pull，直接提交 merge index、合回应用副本并 push。发布冲突会返回 `conflictFiles`；应用版本副本合并冲突会执行 merge abort，同步到应用版本会先确认副本干净并快进拉取后再复制文件、提交和 push。
- 最近使用工作区偏好分两套维度持久化：`user_global_workspace_preferences`（`app_id = NULL`，跨应用追踪「上次进入的应用 + 工作区」组合）和 `user_application_workspace_preferences`（`app_id = 非空`，按应用追踪）。`POST /workspaces/{workspaceId}/recent` 同时写两条，并在响应中通过 `resolveRecentWorkspaceResponse` 回填 `appId` / `versionId` / `applicationWorkspaceId`（与最近工作区接口共用同一反查链路：通过 `findVersionByRuntimeWorkspace` 找 `ApplicationWorkspaceVersion`，未命中再回退到 `findPersonalWorkspaceByRuntimeWorkspace` 取 appId；完全无主时三者均为 `null`）。`GET /recent-workspace` 与 `GET /applications/{appId}/recent-workspace` 同样使用该回填逻辑，便于重新登录或换电脑登录时还原「应用 + 模板 + 版本」上下文；登录/切应用只在应用级 recent 带 `versionId` 且当前用户该版本已有 `workspaceName=default`、带运行态 workspaceId 的个人工作区记录时加载工作区，无历史、无 `versionId` 或无 default 记录时只选择应用并保留左侧工作区切换入口，不创建、不修复 default 私人 worktree。
- 托管应用成员校验失败时统一返回带加载上下文的 `FORBIDDEN`：message 显示应用、版本和工作区类型/名称/ID，`details` 只包含 `loadingStage`、`appId`、`appName`、`versionId`、`version`、`applicationWorkspaceId`、`workspaceKind`、`workspaceName`、`workspaceId`、`personalWorkspaceId` 等安全业务字段，便于排查“切换应用失败”时实际加载的是哪个应用、版本和工作区。
- 通过 domain 广播端口发布/消费 `workspace.version.sync-requested`，并通过本机补偿器扫描缺失或落后的副本。
- 与文件相关的 git 操作、差异比对、agent/skill 文件管理优先进入本模块。
- 工作区 Git Diff 使用 `git status --porcelain --untracked-files=all` 展开未跟踪目录中的每个文件；unmerged 状态保留 `rawStatus` 并返回 `status=conflict`。普通文件通过真实 stage/unstage API 操作 index；冲突支持逐文件处理、全部采用个人/远程版本和取消 merge。个人发布先预览远程变化并校验 expected HEAD，再提交白名单；应用 pull 后立即同步版本/副本 commit。已有 merge 冲突解决后的继续发布不再重复拉远程。只有远端 push 完成才返回 `remotePushed=true`，响应和错误 details 会携带当前 Git 阶段与已执行命令；传入 `operationId` 时复用 Agent 配置进度端口，在每条实际 Git 命令启动前发布当前 `command`，不通过轮询或额外 Git 查询获取进度。

## 测试覆盖

- `WorkspaceApplicationServiceTest` 覆盖工作区创建、服务器归属、分页/详情查询、未找到错误和文件服务编排。
- `WorkspaceFileServiceTest` 覆盖 UTF-8 读写、普通文件删除、目录删除拒绝、路径穿越拒绝、目录列表排序与上限、文件大小限制和 null 内容写入。
- `WorkspaceDirectoryServiceTest` 覆盖服务器工作空间选择器的默认目录、只返回子目录、排序、父目录、条目上限和缺失目录错误码。
- `GitPublishWorkflowTest` 覆盖直接发布、worktree 合并发布、冲突文件收集、merge abort、abort 失败保护，以及同步文件时先 clean/pull 再复制提交推送。
- `ManagedWorkspaceApplicationServiceTest` 覆盖应用成员校验及 `FORBIDDEN` 加载上下文、标准库分支校验、设置页初始版本工作区创建、应用版本工作区创建、内部版本库按当前统一认证号拼接 Git URL 并刷新 origin、通用参数根目录、托管逻辑路径入库与物理路径响应、代码库英文名路径片段、服务器副本记录、目标 commit、广播发布、`git pull`、运行态 Workspace 关联、最近使用记录、私人 worktree 新命名规则、Git diff 路径解码、未跟踪文件级 patch、staged/unstaged patch 聚合、真实 stage/unstage 与冲突路径拒绝、单文件 discard、个人 worktree 推送（在应用版本副本上把个人分支 merge 进特性分支后再推送特性分支，含个人 merge 冲突留在个人 worktree、应用副本合并冲突 abort、冲突解决后继续发布不再重复拉远程、失败阶段命令透传）、已提交但后续失败的 clean worktree 重试、旧应用副本路径自愈、无本机副本时创建本机副本而不是使用 legacy 绝对路径、sync-to-application 先拉取后复制提交、`yyyy年M月` 版本格式（`sanitizeVersionForBranchAndPath` 转 `yyyy-MM` 派生分支/路径）和非法版本格式拒绝。
- `AgentConfigApplicationServiceTest` 覆盖公共 Git 地址未配置禁用、公共更新/初始化 clone 与广播、脏仓库保持可浏览、默认拒绝覆盖和显式恢复已跟踪修改、公共 worktree 未初始化时拒绝 clone、worktree 名称拼接日期、服务器归属保存、公共 worktree 切换列表按服务器/状态过滤并返回创建人、Agent 配置文件服务器归属查询、公共/工作空间级 diff patch 聚合、公共/工作空间 worktree publish 冲突文件返回与不推送，以及工作空间级 `.opencode/` 根下 `agents/` 与 `skills/` 技能包读写和 diff 过滤。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Context。
- SLF4J API。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。
- `test-agent-persistence` 实现类。

## 后续 AI 编码指引

新增与 workspace、文件、应用版本工作区、个人工作区、git、agent 或 skill 管理相关的业务逻辑时优先改这里；HTTP 入口只放在 `test-agent-api`。
