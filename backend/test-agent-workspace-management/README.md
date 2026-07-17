# test-agent-workspace-management

## 工程定位

Workspace、文件管理、应用版本工作区、个人工作区、git/diff、agent 和 skill 管理业务模块。

## 主要职责

公共 Agent/Skill 的 `update`、`update-and-push`、`publish` 在任何远端 push 或共享运行副本切换前，先通过 `PublicAgentConfigRolloutCoordinator` 建立 `PREPARING` 持久化禁发任务；远端提交确认后才转为 `DRAINING` 并广播 `rolloutId`。push 回包不确定时会 fetch 验证远端是否已包含目标提交；发起 Java 退出时，同服务器补偿任务按远端事实恢复 PREPARING。每台服务器通过数据库租约认领同步任务，复用发起用户已加密保存的 SSH key 刷新 origin、fetch、checkout/reset 到明确 commit，再登记本机 manager 进程并确认同步，因此广播丢失、Java 重启、同服务器多 Java 或瞬时 Git 失败都不会提前解除门禁。公共个人 worktree 仍是管理员编辑事实源，共享仓库只作为各服务器运行时副本；显式“拉取”会先同步当前管理员的稳定个人 worktree，成功后才在 PREPARING 闸门内推进共享副本。

- 工作区注册、查询和分页。
- 工作区注册时记录 `linuxServerId`，并通过 `WorkspaceServerIdentity` 提供当前 Java 进程所属服务器和默认目录。
- 工作区内文件单层列表、受限相对路径搜索、UTF-8 内容读写、Base64 二进制新文件上传、普通文件跨目录复制/移动、普通文件或目录同目录重命名、文件状态、普通文件/目录树删除和路径越权拦截；目录删除不跟随符号链接，并拒绝工作区根目录和任意层级 `.git` 元数据；上传沿用单文件大小上限且不覆盖已有条目，复制/移动只处理普通文件并拒绝覆盖目标；搜索支持空关键字文件目录，并受数量、深度和超时上限保护。
- 文件 WebSocket ticket 创建前通过 `requireWorkspaceOnCurrentServer` 校验 workspace、当前后端和用户 opencode 进程同服务器；历史空服务器归属工作区在 root path 校验成功后回填当前服务器 ID。
- 普通前端不再传物理目录创建 Workspace；应用版本和个人工作区目录由后端按通用参数与业务 id 派生。超级管理员服务器工作空间选择器通过目标后端目录浏览能力从该后端 Java 进程运行目录开始浏览。
- `WorkspaceApplicationService` 同时实现领域 `TrustedWorkspaceResolver`：历史 `linux_server_id=null` 只有在当前节点能解析并访问真实 root 时才回填当前服务器。可信 root/server/status 变更先建立 Workspace mutation gate，关系型保存成功后用单个 Lua 原子再次失效并释放 gate，数据库失败只撤回自己的 gate token；托管个人/应用副本更新沿用同一规则，且仅在可信字段真正变化时执行，创建新 Workspace 不做无效清理。
- `ManagedConversationWorkspaceAccessAuthorizer` 实现运行上下文权限领域端口：应用版本/replica Workspace 必须属于已启用应用且当前用户为有效成员；个人 Workspace 还必须由当前用户拥有。`SUPER_ADMIN` 不旁路成员规则；找不到托管版本或个人映射的历史 Workspace 沿用 Session owner 与可信路径规则。
- Agent/Skill 配置管理：公共 Git 仍由 `SUPER_ADMIN` 独占并使用每位管理员的公共个人 worktree；服务器公共仓库“拉取”会先把远端公共分支合并到当前管理员在该服务器的稳定个人 worktree，再更新共享运行副本，避免文件树继续读取旧内容。个人 worktree 有未提交修改时默认拒绝拉取，显式确认放弃本地已跟踪修改后才允许 reset；合并冲突保留在个人 worktree 并沿用三方冲突处理。应用级 `.opencode/agents/**`、`.opencode/skills/**`（含 rules/templates）由 `APP_ADMIN` 管理，`SUPER_ADMIN` 继承该权限，普通成员只能读取。应用级配置不创建独立 Agent worktree，而是使用当前版本个人 workspace 的 Git 根；文件树、读取和写入统一走目标服务器文件 WebSocket ticket，暂存和文件回退沿用 AgentConfig 目录权限，提交/发布复用个人 `HEAD` 白名单投影；普通回退拒绝 unmerged 文件，冲突继续使用三方合并能力。创建应用技能包默认生成 OpenCode 可识别的 `skills/<name>/SKILL.md`、`rules/README.md`、`templates/README.md` 模板。
- 基于配置管理中的应用工作空间模板创建应用版本工作区，clone 指定分支并创建运行态 `Workspace`；设置页创建应用工作空间时会复用该能力同步创建初始版本工作区，并按 `workspace_create_operations` 记录“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”进度。设置页保存前的分支、目录树和新增目录操作均不落磁盘；只有保存接口会 clone/checkout。测试工作库在保存阶段强校验分支必须符合 `feature_testagent_yyyyMMdd`，`directoryPath` 必须是当前应用同名根目录的一级子目录；`directoryNew=true` 且 clone 后目标目录不存在时才创建该目录，不向 Git 提交空目录。新前端表单默认传入工作空间别名 `ai-test`；后端创建和重命名时按去首尾空白后的精确字符串校验同一应用内唯一，旧客户端不传别名时仍按目录末段兜底。
- 应用版本工作区根目录优先读取 `common_parameters.OPENCODE_APP_WORKSPACE_ROOT`，路径片段包含安全化版本号和代码库 `englishName`；历史代码库缺少英文名称时拒绝创建新的版本工作区。新建或显式修复的应用版本、服务器副本和托管运行态 `Workspace.rootPath` 入库保存 `appworkspace:<versionSegment>/<repositoryEnglishName>[/<templateDirectory>]` 逻辑路径；接口响应和 Git/文件/PTY/Run 执行前统一解析为当前服务器物理路径。内部部署模式版本库执行 clone/fetch/pull/push 前会按当前操作人统一认证号拼接 `ssh://{unifiedAuthId}@{gitUrl}` 并刷新 origin，公共配置“更新并推送”也必须在 fetch 前刷新共享仓库 origin，避免沿用上一位管理员的 SSH 用户；接管已有仓库时忽略 origin 中的 `ssh://任意用户@` 前缀后再比较数据库保存片段。公共仓库脏状态仍保持 `initialized=true/status=CONFLICT`，message 最多列出五个真实 Git 路径，不能把“文件待提交”误报成“目录未初始化”。旧 Unix/Windows 绝对路径只兼容读取，不批量迁移。
- 基于应用版本工作区副本创建个人 git worktree，根目录优先读取 `common_parameters.OPENCODE_PERSONAL_WORKTREE_ROOT`。OpenCode、编辑器和终端始终使用该个人 worktree；应用版本副本对普通成员只读。个人 worktree 的普通文件可以本地编辑、stage 和 commit；`spec/**` 对所有角色都只保留在个人分支（生成结果另由系统同步），不参与远程发布，服务端按规范化路径拒绝混选和 `./spec` 别名，`SUPER_ADMIN` 也不能豁免目录规则。前端“提交并推送”会先把全部选中文件提交到个人 HEAD，再将允许发布的非 spec 文件投影到应用 feature 分支。发布不会 merge 整个个人分支，完成后广播 `workspace.version.sync-requested`，在线用户手动刷新/同步，禁止自动覆盖脏工作树。旧同步接口的 `force` 同样不绕过本地提交、`spec/**` 禁推和 feature 分支保护。
- 最近使用工作区偏好分两套维度持久化：`user_global_workspace_preferences`（`app_id = NULL`，跨应用追踪「上次进入的应用 + 工作区」组合）和 `user_application_workspace_preferences`（`app_id = 非空`，按应用追踪）。`POST /workspaces/{workspaceId}/recent` 同时写两条，并在响应中通过 `resolveRecentWorkspaceResponse` 回填 `appId` / `versionId` / `applicationWorkspaceId`（与最近工作区接口共用同一反查链路：通过 `findVersionByRuntimeWorkspace` 找 `ApplicationWorkspaceVersion`，未命中再回退到 `findPersonalWorkspaceByRuntimeWorkspace` 取 appId；完全无主时三者均为 `null`）。`GET /recent-workspace` 与 `GET /applications/{appId}/recent-workspace` 同样使用该回填逻辑，便于重新登录或换电脑登录时还原「应用 + 模板 + 版本」上下文；登录/切应用只在应用级 recent 带 `versionId` 且当前用户该版本已有 `workspaceName=default`、带运行态 workspaceId 的个人工作区记录时加载工作区，无历史、无 `versionId` 或无 default 记录时只选择应用并保留左侧工作区切换入口，不创建、不修复 default 私人 worktree。
- 托管应用成员校验失败时统一返回带加载上下文的 `FORBIDDEN`：message 显示应用、版本和工作区类型/名称/ID，`details` 只包含 `loadingStage`、`appId`、`appName`、`versionId`、`version`、`applicationWorkspaceId`、`workspaceKind`、`workspaceName`、`workspaceId`、`personalWorkspaceId` 等安全业务字段，便于排查“切换应用失败”时实际加载的是哪个应用、版本和工作区。
- 通过 domain 广播端口发布/消费 `workspace.version.sync-requested`，并通过本机补偿器扫描缺失或落后的副本。
- 与文件相关的 git 操作、差异比对、agent/skill 文件管理优先进入本模块。
- 工作区 Git Diff 即使带工作区 pathspec，也使用 `git status --porcelain --untracked-files=all` 展开未跟踪目录中的每个文件，使接口文件数、前端数量角标和实际文件数一致；unmerged 状态保留 `rawStatus` 并返回 `status=conflict`。普通文件通过真实 stage/unstage API 操作 index；冲突支持逐文件处理、全部采用个人/远程版本和取消 merge。个人发布要求允许发布的文件先在个人 worktree 本地提交，再投影到应用 feature worktree；应用 Agent/Skill 同样先进入个人 HEAD，再只投影 `.opencode/**` 白名单，成功后更新版本/副本 HEAD 并广播 `workspace.version.sync-requested`。发布遇到个人 merge 状态、未提交文件或应用副本脏工作树时直接返回冲突，不创建个人发布 merge。只有远端 push 完成才返回 `remotePushed=true`，响应和错误 details 会携带当前 Git 阶段与已执行命令；传入 `operationId` 时复用 Agent 配置进度端口，在每条实际 Git 命令启动前发布当前 `command`，不通过轮询或额外 Git 查询获取进度。
- 所有平台用户触发的 Git commit 以及可能产生 commit 的 merge 都按当前用户注入命令级作者/提交者身份；身份不写入公共仓库或全局 Git 配置，平台没有邮箱字段时由 common Git 工具使用 `testagent.local` 保留域名生成稳定 email。

## 测试覆盖

- `WorkspaceApplicationServiceTest` 覆盖工作区创建、服务器归属、分页/详情查询、未找到错误和文件服务编排。
- `WorkspaceFileServiceTest` 覆盖 UTF-8 读写、Base64 二进制上传、普通文件复制/移动、普通文件和目录同目录重命名、目标冲突、普通文件/目录树删除、工作区根与 `.git` 删除拒绝、路径穿越拒绝、目录列表排序与上限、相对路径/空关键字文件搜索、文件大小限制和 null 内容写入。
- `WorkspaceDirectoryServiceTest` 覆盖服务器工作空间选择器的默认目录、只返回子目录、排序、父目录、条目上限和缺失目录错误码。
- `GitPublishWorkflowTest` 覆盖直接发布、worktree 合并发布、冲突文件收集、merge abort、abort 失败保护，以及同步文件时先 clean/pull 再复制提交推送。
- `ManagedWorkspaceApplicationServiceTest` 覆盖应用成员校验及 `FORBIDDEN` 加载上下文、托管逻辑路径、个人 worktree 创建、Git diff、个人 worktree 本地提交、从个人 `HEAD` 按白名单投影并推送 feature、所有角色 spec 禁推、应用 Agent 发布后的版本 HEAD 更新和广播、应用副本只读 Git 操作及失败阶段命令透传；同步接口覆盖仅使用已提交文件的兼容路径。
- `AgentConfigApplicationServiceTest` 覆盖公共仓库初始化/更新、显式拉取先合并当前管理员稳定个人 worktree 且脏 worktree 不更新共享副本、当前用户长期公共 worktree 的稳定命名与复用、按服务器和创建人过滤、跨用户操作拒绝、公共/应用 Agent 文件回退路径映射、公共个人分支合并远端后以 refspec 推送到公共分支、冲突保留在个人 worktree、共享运行时副本同步与广播，以及工作空间级 Agent/Skill 配置读写和 diff。

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
