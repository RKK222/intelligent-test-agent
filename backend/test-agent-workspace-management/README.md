# test-agent-workspace-management

## 工程定位

Workspace、文件管理、应用版本工作区、个人工作区、git/diff、agent 和 skill 管理业务模块。

## 主要职责

- 工作区注册、查询和分页。
- 工作区注册时记录 `linuxServerId`，并通过 `WorkspaceServerIdentity` 提供当前 Java 进程所属服务器和默认目录。
- 工作区内文件单层列表、UTF-8 内容读写、文件状态、普通文件删除和路径越权拦截。
- 文件 WebSocket ticket 创建前通过 `requireWorkspaceOnCurrentServer` 校验 workspace、当前后端和用户 opencode 进程同服务器；历史空服务器归属工作区在 root path 校验成功后回填当前服务器 ID。
- 受控浏览 `test-agent.workspace-picker.allowed-roots` 内的本机目录，供前端选择新的 Workspace 根目录；服务器工作空间选择器通过目标后端目录浏览能力从该后端 Java 进程运行目录开始浏览。
- 公共目录（`test-agent.public-directory.path` 指定固定根目录）的列表/读取/写入，所有登录用户只读，SUPER_ADMIN 可写。
- Agent/Skill 配置管理：公共级读取 `OPENCODE_PUBLIC_AGENT_GIT_URL`、`OPENCODE_PUBLIC_CONFIG_GIT_ROOT`、`OPENCODE_PUBLIC_CONFIG_DIR`、`OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT`，文件树根为 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/`，`skills/` 下直接放实际技能包；工作空间级文件树根为 `{workspace.rootPath}/.opencode/`，可维护 `agents/` 与 `skills/` 技能包。支持只读浏览、SUPER_ADMIN 写入、Git diff/stage/commit/publish、公共配置更新、worktree 模式和 `agent-config.public-sync-requested` 广播同步。工作空间级 Git diff 只扫描 `.opencode/` 下的 `agents/` 与 `skills/`，返回给 UI 时去掉 `.opencode/` 前缀，避免普通应用文件误进入 agents 分组。公共 Git 地址缺失或为 `UNCONFIGURED` 时，禁用原因会明确提示超级管理员到“系统管理 → 通用参数管理”配置该参数。公共配置初始化/更新会在 Git 根目录缺失或为空目录时 clone 到 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT`，并校验 `OPENCODE_PUBLIC_CONFIG_DIR` 存在且非空；已有仓库的未提交修改不再阻断只读文件树，更新默认拒绝覆盖，只有请求显式携带 `discardLocalChanges=true` 时才恢复已跟踪文件再拉取。公共 worktree 创建不再首次 clone，只允许在管理员选择的已初始化服务器上创建，并把 `worktreeId -> linuxServerId` 落库。公共 worktree 切换列表按 `scope=PUBLIC`、`linuxServerId` 和 `status=ACTIVE` 查询，并补充创建人 `userId/username`；后续 Agent/Skill 配置文件目录列表/读取/写入由 API 层按服务器归属签发文件 WebSocket ticket 执行，Git 操作仍按服务器归属路由。Git 命令失败会通过统一 Git 错误码返回安全归因和 `gitFailureHint`，便于前端提示认证、仓库、网络、分支或 worktree 冲突问题。
- 基于配置管理中的应用工作空间模板创建应用版本工作区，clone 指定分支并创建运行态 `Workspace`；设置页创建应用工作空间时会复用该能力同步创建初始版本工作区，并按 `workspace_create_operations` 记录“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”进度。
- 应用版本工作区根目录优先读取 `common_parameters.OPENCODE_APP_WORKSPACE_ROOT`，路径片段包含安全化版本号和代码库 `englishName`；历史代码库缺少英文名称时拒绝创建新的版本工作区。
- 基于应用版本工作区副本创建个人 git worktree，根目录优先读取 `common_parameters.OPENCODE_PERSONAL_WORKTREE_ROOT`，记录最近使用工作区，并支持个人/应用目录差异、双向文件同步、版本工作区 `git pull --ff-only` 和跨服务器副本广播同步。默认个人空间使用 `{应用版本分支}_{userId}_default` 作为分支名和物理目录末段，路径形如 `{OPENCODE_PERSONAL_WORKTREE_ROOT}/{version}/{userId}/{repositoryEnglishName}/{应用版本分支}_{userId}_default/`；后续自定义私人空间把末段 `default` 替换为自定义名称。创建时同 JVM 内串行化；同名分支已存在时复用分支挂载 worktree，目标目录已存在且分支匹配时接管；如果同一分支登记在旧路径且新规范路径不存在，会先用 `git worktree move` 重挂载到新规范路径，避免切换版本或 recent 恢复时反复报 worktree 创建冲突。
- 最近使用工作区偏好分两套维度持久化：`user_global_workspace_preferences`（`app_id = NULL`，跨应用追踪「上次进入的应用 + 工作区」组合）和 `user_application_workspace_preferences`（`app_id = 非空`，按应用追踪）。`POST /workspaces/{workspaceId}/recent` 同时写两条，并在响应中通过 `resolveRecentWorkspaceResponse` 回填 `appId` / `versionId` / `applicationWorkspaceId`（与最近工作区接口共用同一反查链路：通过 `findVersionByRuntimeWorkspace` 找 `ApplicationWorkspaceVersion`，未命中再回退到 `findPersonalWorkspaceByRuntimeWorkspace` 取 appId；完全无主时三者均为 `null`），让前端在「切会话」「兜底选择首模板首版本」等非 recent 直接命中的路径里也能立即拿到当前版本与模板。`GET /recent-workspace` 与 `GET /applications/{appId}/recent-workspace` 同样使用该回填逻辑，便于重新登录或换电脑登录时直接还原「应用 + 模板 + 版本」上下文，让左下角"切换工作空间"按钮立刻显示当前工作区名 + 版本号；其他接口依旧只返回运行态 Workspace，避免在响应里重复写出托管应用信息。
- 通过 domain 广播端口发布/消费 `workspace.version.sync-requested`，并通过本机补偿器扫描缺失或落后的副本。
- 与文件相关的 git 操作、差异比对、agent/skill 文件管理优先进入本模块。

## 测试覆盖

- `WorkspaceApplicationServiceTest` 覆盖工作区创建、服务器归属、分页/详情查询、未找到错误和文件服务编排。
- `WorkspaceFileServiceTest` 覆盖 UTF-8 读写、普通文件删除、目录删除拒绝、路径穿越拒绝、目录列表排序与上限、文件大小限制和 null 内容写入。
- `WorkspaceDirectoryServiceTest` 覆盖默认根目录、只返回子目录、排序、父目录边界、越权和缺失目录错误码。
- `PublicDirectoryServiceTest` 覆盖未配置/不存在根目录时 list/read/write 返回 `NOT_FOUND`，以及配置正常时委托给 `WorkspaceFileService` 的 list/read/write 行为。
- `ManagedWorkspaceApplicationServiceTest` 覆盖应用成员校验、标准库分支校验、设置页初始版本工作区创建、应用版本工作区创建、通用参数根目录、代码库英文名路径片段、服务器副本记录、目标 commit、广播发布、`git pull`、运行态 Workspace 关联、最近使用记录、私人 worktree 新命名规则、Git diff 路径解码、单文件 discard、个人 worktree 推送（在应用版本副本上把个人分支 merge 进特性分支后再推送特性分支，含合并冲突返回 `CONFLICT` 与冲突文件列表）、`yyyy年M月` 版本格式（`sanitizeVersionForBranchAndPath` 转 `yyyy-MM` 派生分支/路径）和非法版本格式拒绝。
- `AgentConfigApplicationServiceTest` 覆盖公共 Git 地址未配置禁用、公共更新/初始化 clone 与广播、脏仓库保持可浏览、默认拒绝覆盖和显式恢复已跟踪修改、公共 worktree 未初始化时拒绝 clone、worktree 名称拼接日期、服务器归属保存、公共 worktree 切换列表按服务器/状态过滤并返回创建人、Agent 配置文件服务器归属查询，以及工作空间级 `.opencode/` 根下 `agents/` 与 `skills/` 技能包读写和 diff 过滤。

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
