# test-agent-workspace-management

## 工程定位

Workspace、文件管理、应用版本工作区、个人工作区、git/diff、agent 和 skill 管理业务模块。

## 主要职责

公共 Agent/Skill 的 `update`、`update-and-push`、`publish` 在任何远端 push 或共享运行副本切换前，先通过 `PublicAgentConfigRolloutCoordinator` 建立 `PREPARING` 持久化禁发任务；远端提交确认后才转为 `DRAINING` 并广播 `rolloutId`。push 回包不确定时会 fetch 验证远端是否已包含目标提交；发起 Java 退出时，同服务器补偿任务按远端事实恢复 PREPARING。每台服务器通过数据库租约认领同步任务，复用发起用户已加密保存的 SSH key 刷新 origin、fetch、checkout/reset 到明确 commit，再登记本机 manager 进程并确认同步，因此广播丢失、Java 重启、同服务器多 Java 或瞬时 Git 失败都不会提前解除门禁。发布请求在远端提交确认、rollout 激活并广播后立即返回，不在 HTTP 请求线程认领或执行本机同步；本机和其它服务器均由广播消费者或 5 秒持久化补偿程序异步推进。公共个人 worktree 仍是管理员编辑事实源，共享仓库只作为各服务器运行时副本；显式“拉取”会先同步当前管理员的稳定个人 worktree，成功后才在 PREPARING 闸门内推进共享副本。公共发布不会再推送长期个人分支的整段历史，而是把合并后的最终文件树投影为以当前远端提交为唯一父节点、由当前管理员企业身份签署的线性提交，成功后再把个人分支和共享副本重置到该提交，避免旧的无效 committer 污染新发布。

- 工作区注册、查询和分页。
- 应用引用资产库管理：只处理当前应用关联的 `APPLICATION_ASSET_REPOSITORY`，首次初始化固定远端 HEAD，后续同分支同步或管理员受控切换分支都以 generation 固定目标提交；按在线及历史 Linux 服务器创建副本目标。generation 建档后立即提交本机有界异步 worker，并通过 `reference-repository.sync-requested` 唤醒其它 Java；广播消费者只排队、不在 Redis listener 线程执行 Git，任务继续由数据库租约/CAS fencing、本机文件锁和 60 秒补偿扫描保护。瞬时失败按数据库退避时间定向重试，调度器拒绝或进程退出仍由补偿扫描恢复。离线副本进入 `DEFERRED` 并在恢复后补齐；新目录在同根临时目录校验后原子移动，已有目录必须干净且同源，同分支仅允许快进；实际分支与固定 HEAD 已一致时跳过 fetch/reset，跨分支则显式抓取目标 refspec，从固定提交创建不存在的本地分支，已有目标本地分支仍拒绝分叉。主动指针核验只读本地实际 branch、HEAD、origin 和工作树状态，实际快照与目标指针分别返回。目录树仅开放总体与当前服务器副本均 `READY` 的单层安全读取，并只把根层命中 `REFERENCES_SDD_FOLDER_NAMES` 的目录标记为可选。
- 引用资产库列表和状态另返回可空的 `repositoryPath`：业务层只用当前平台 `OPENCODE_REFERENCES_DIR` 与可信英文名派生规范化绝对路径；参数缺失或历史非法名称不阻断仓库列表，也不把物理路径写入错误或日志。
- 工作区引用组合视图：读取当前工作区最新 `.opencode/opencode.jsonc`，只接受能反向验证到当前应用资产库、本机同 generation `READY` 副本、当前平台引用根目录和允许 SDD 根目录的本地引用对象。`merge=true` 按 `sdd-folder-name` 合并到工作区同名一级目录，工作区已有目录保持普通来源，纯引用后代标记只读引用来源；`merge=false` 以参考别名投影只读一级目录。文件同名不覆盖，节点使用稳定身份并携带冲突来源；单引用异常转为局部 warning，不阻断工作区内容，所有路径继续拒绝 `.git`、符号链接和 root 穿越。
- 工作区注册时记录 `linuxServerId`，并通过 `WorkspaceServerIdentity` 提供当前 Java 进程所属服务器和默认目录。
- 工作区内原始文件单层列表、受限相对路径搜索、UTF-8 内容读写、分片二进制新文件上传、普通文件跨目录复制、普通文件或普通目录（包括非空目录）同工作区移动、普通文件或目录同目录重命名、文件状态、普通文件/目录树删除和路径越权拦截；目录删除不跟随符号链接，并拒绝工作区根目录和任意层级 `.git` 元数据。`workspace.move` 保持 `workspaceId/sourcePath/targetPath` 与成功 `null` 的既有 RPC 契约，以一次原子文件系统重命名整体移动普通文件或普通目录（包括非空目录），不递归拆分且不覆盖目标；同路径幂等成功，缺失源为 `NOT_FOUND`，目标已存在为 `CONFLICT`，根、符号链接/特殊文件、目录自身后代目标为 `VALIDATION_ERROR`，路径越界为 `FORBIDDEN`。移动前固定真实 root/source/目标父目录；Linux 从 `/` 逐段打开目录句柄并直接调用内核 `renameat2(RENAME_NOREPLACE)`，兼容 Alpine/musl 未导出包装函数；macOS 使用逐段目录句柄和 `renameatx_np(RENAME_EXCL | RENAME_NOFOLLOW_ANY)`；Windows 固定源条目和目标父目录句柄、核对最终路径后使用 `SetFileInformationByHandle` 且禁止替换。目标父目录替换或目标并发创建都在原子操作层失败关闭，其他平台缺少等价能力时拒绝移动。上传不设置应用层总大小上限，每片有界并先写同目录隐藏临时文件，声明大小校验成功后才以不覆盖方式发布；取消、连接关闭或失败立即清理，24 小时残留由后续上传尽力回收。UTF-8 一次性读取和文本编辑默认阈值为 5 MiB；大文件通过固定约 512 KiB、UTF-8 字符边界对齐的分段渐进只读预览，可读取到 EOF，文件大小或修改时间变化时停止混合拼接。搜索支持空关键字文件目录，并受数量、深度和超时上限保护。组合文件树只新增只读 list/read 视图，不改变这些原始写操作的物理工作区边界。
- 文件 WebSocket ticket 创建前通过 `requireWorkspaceOnCurrentServer` 校验 workspace、当前后端和用户 opencode 进程同服务器；历史空服务器归属工作区在 root path 校验成功后回填当前服务器 ID。
- 普通前端不再传物理目录创建 Workspace；应用版本和个人工作区目录由后端按通用参数与业务 id 派生。超级管理员服务器工作空间选择器通过目标后端目录浏览能力从该后端 Java 进程运行目录开始浏览。
- `WorkspaceApplicationService` 同时实现领域 `TrustedWorkspaceResolver`：历史 `linux_server_id=null` 只有在当前节点能解析并访问真实 root 时才回填当前服务器。可信 root/server/status 变更先建立 Workspace mutation gate，关系型保存成功后用单个 Lua 原子再次失效并释放 gate，数据库失败只撤回自己的 gate token；托管个人/应用副本更新沿用同一规则，且仅在可信字段真正变化时执行，创建新 Workspace 不做无效清理。
- `ManagedConversationWorkspaceAccessAuthorizer` 实现运行上下文权限领域端口：应用版本/replica Workspace 必须属于已启用应用且当前用户为有效成员；个人 Workspace 还必须由当前用户拥有。`SUPER_ADMIN` 不旁路托管成员规则；找不到托管版本或个人映射的历史 Workspace 在会话入口沿用 Session owner 与可信路径规则，在文件入口默认拒绝，仅显式标记的 `SUPER_ADMIN` 服务器工作空间兼容访问可放行。
- Agent/Skill 配置管理：公共 Git 仍由 `SUPER_ADMIN` 独占并使用每位管理员的公共个人 worktree；服务器公共仓库“拉取”会先把远端公共分支合并到当前管理员在该服务器的稳定个人 worktree，再更新共享运行副本，避免文件树继续读取旧内容。个人 worktree 有未提交修改时默认拒绝拉取，显式确认放弃本地已跟踪修改后才允许 reset；合并冲突保留在个人 worktree 并沿用三方冲突处理。脏状态错误会在安全 details 中返回 `repositoryKind/path/dirtyFiles/discardLocalChangesAllowed`，明确区分当前管理员个人 worktree 与服务器共享运行副本，前端可按真实服务器、绝对路径和文件提供定点恢复入口。公共 Diff 还在 porcelain clean 时比较个人与共享 HEAD 的祖先关系和文件树；本地提交后发布失败时返回 `publishPending=true`，页面重开后可直接重新发布；有真实未提交文件时仍优先走正常暂存/提交或回退流程。应用级 Diff/Git 白名单精确包含 `.opencode/opencode.jsonc`、`.opencode/agents/**`、`.opencode/skills/**`（含 rules/templates），由 `APP_ADMIN` 管理，`SUPER_ADMIN` 继承该权限，普通成员只能读取；`.opencode/package.json` 等其它文件不进入该作用域。应用级配置不创建独立 Agent worktree，而是使用当前版本个人 workspace 的 Git 根；公共/应用文件树、读取、写入、分片二进制上传、文件同目录改名、普通文件复制/移动和文件/目录树删除统一走目标服务器文件 WebSocket ticket。复制/移动复用 `WorkspaceFileService.copyFile/moveFile` 的不覆盖、路径、符号链接和目录后代保护，应用级同时校验源和目标 Diff 白名单；公共写操作要求 `SUPER_ADMIN`，应用写操作要求 `APP_ADMIN`。公共与应用根都可按 OpenCode 模板创建 `agents/<英文技术标识>.md` 或 `skills/<英文技术标识>/SKILL.md`、rules、templates；英文名称不填时由前端按完整拼音生成技术标识。
- OpenCode 保持原生配置加载：每个用户进程的 `OPENCODE_CONFIG_DIR` 固定为 `{sessionPath}/.testagent-runtime/current-public-config` 受管软链接，默认指向本服务器 `OPENCODE_PUBLIC_CONFIG_DIR` 共享运行副本；应用配置仍只读取当前个人 worktree 的 `.opencode`。公共管理员个人 worktree 和应用 feature 副本都是 Git 编辑/发布源，不是额外运行时覆盖层，本链路不修改 OpenCode 源码、不复制配置。超级管理员保存公共 Agent/Skill 目录定义或 JSONC 后，工作区服务校验本人 worktree 与服务器，再由运行时服务把本人软链接原子切到该 worktree 并只 dispose 本人，供推送前调试；公共本地提交不新增影响。公共推送后沿用全服务器共享副本固定提交同步和全机进程排空，每个目标 dispose 前先把指针恢复到共享副本。应用普通文件与 Agent/Skill 推送统一把 feature 固定提交通过 `git merge --no-edit <targetCommit>` 合入各服务器相关个人 worktree：clean 时快进或生成 merge commit，任意 dirty/staged/untracked 时保留本地内容并显示待同步，冲突时保留 `MERGE_HEAD` 与三方 index。应用 Agent/Skill rollout 只有在相关个人 worktree全部包含目标提交后才登记用户进程、等待空闲并 dispose，且不切换公共指针；未收敛时保持持久化 retry。个人保存应用 Agent、Skill 目录定义或引用 JSONC 直接只热加载当前用户，忙碌 Run 结束后执行。
- Agent Markdown 文件和 Skill 目录继续使用英文技术标识。Agent 配置目录列表仅对 `agents/*.md` 和 `skills/*/SKILL.md` 有界读取最多 64 KiB 且不跟随符号链接，优先解析 Skill `metadata.display-name/display-name-zh`，其次解析双语 `description`，旧配置最后回退中文 Markdown 标题；响应只增加可选 `displayName/displayNameEn` 供前端中文展示，原始 `path/name` 不变。
- 基于配置管理中的应用工作空间模板创建应用版本工作区，clone 指定分支并创建运行态 `Workspace`；设置页创建应用工作空间时会复用该能力同步创建初始版本工作区，并按 `workspace_create_operations` 记录“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”进度。设置页保存前的分支、目录树和新增目录操作均不落磁盘；只有保存接口会 clone/checkout。测试工作库在保存阶段强校验分支必须符合 `feature_testagent_yyyyMMdd`，`directoryPath` 必须是当前应用同名根目录的一级子目录；`directoryNew=true` 且 clone 后目标目录不存在时才创建该目录，不向 Git 提交空目录。新前端表单默认传入工作空间别名 `ai-test`；后端创建和重命名时按去首尾空白后的精确字符串校验同一应用内唯一，旧客户端不传别名时仍按目录末段兜底。
- 应用版本工作区根目录优先读取 `common_parameters.OPENCODE_APP_WORKSPACE_ROOT`，路径片段包含安全化版本号和代码库 `englishName`；历史代码库缺少英文名称时拒绝创建新的版本工作区。新建或显式修复的应用版本、服务器副本和托管运行态 `Workspace.rootPath` 入库保存 `appworkspace:<versionSegment>/<repositoryEnglishName>[/<templateDirectory>]` 逻辑路径；接口响应和 Git/文件/PTY/Run 执行前统一解析为当前服务器物理路径。内部部署模式版本库执行 clone/fetch/pull/push 前会按当前操作人统一认证号拼接 `ssh://{unifiedAuthId}@{gitUrl}` 并刷新 origin，公共配置“更新并推送”也必须在 fetch 前刷新共享仓库 origin，避免沿用上一位管理员的 SSH 用户；接管已有仓库时忽略 origin 中的 `ssh://任意用户@` 前缀后再比较数据库保存片段。公共仓库脏状态仍保持 `initialized=true/status=CONFLICT`，message 最多列出五个真实 Git 路径，不能把“文件待提交”误报成“目录未初始化”。旧 Unix/Windows 绝对路径只兼容读取，不批量迁移。
- 基于应用版本工作区副本创建个人 git worktree，根目录优先读取 `common_parameters.OPENCODE_PERSONAL_WORKTREE_ROOT`，分支固定为 `{featureBranch}_{userId}_{workspaceName}`。OpenCode、编辑器和终端始终使用该个人 worktree；应用版本副本对所有角色普通文件只读。普通文件范围是个人 workspace 根下除 `.opencode/**` 独立 Agent 作用域外的项目文件，包括根 README、`docs/**`、`archive/**`、源码、测试和部署文件；它们可以本地编辑、stage 和 commit。`spec/**` 也显示在普通 Diff，但对所有角色都只保留在个人分支，不参与远程发布，服务端按规范化路径拒绝混选和 `./spec` 别名，`SUPER_ADMIN` 也不能豁免。发布还要求所选路径已进入个人 `HEAD`、当前没有未完成 merge 且确认后的 feature HEAD 未漂移；前端“提交并推送”会先把全部选中文件提交到个人 HEAD，再将允许发布的非 spec 文件投影到 feature，个人分支不 push。push 后本机立即、其他服务器收到 `workspace.version.sync-requested` 后把同一固定 feature commit 反向 merge 到相关个人 worktree；不覆盖 dirty 内容，真实冲突直接进入现有 Diff/三方处理。用户本地提交、回退或显式重新进入 default 个人工作区会重试此前待同步 commit；旧 `sync-from-application` 也只尝试整个固定 commit merge，`force` 不再提供覆盖语义。
- 最近使用工作区偏好分两套维度持久化：`user_global_workspace_preferences`（`app_id = NULL`，跨应用追踪「上次进入的应用 + 工作区」组合）和 `user_application_workspace_preferences`（`app_id = 非空`，按应用追踪）。`POST /workspaces/{workspaceId}/recent` 同时写两条，并在响应中通过 `resolveRecentWorkspaceResponse` 回填 `appId` / `versionId` / `applicationWorkspaceId`（与最近工作区接口共用同一反查链路：通过 `findVersionByRuntimeWorkspace` 找 `ApplicationWorkspaceVersion`，未命中再回退到 `findPersonalWorkspaceByRuntimeWorkspace` 取 appId；完全无主时三者均为 `null`）。`GET /recent-workspace` 在映射到托管应用时还会复核应用启用状态和当前成员关系，撤权后返回空但不删除偏好、个人工作区或物理 worktree；非托管兼容工作区仍可返回。`GET /applications/{appId}/recent-workspace` 继续通过显式成员校验。两者便于重新登录或换电脑登录时还原「应用 + 模板 + 版本」上下文；登录/切应用只在应用级 recent 带 `versionId` 且当前用户该版本已有 `workspaceName=default`、带运行态 workspaceId 的个人工作区记录时加载工作区，无历史、无 `versionId` 或无 default 记录时只选择应用并保留左侧工作区切换入口，不创建、不修复 default 私人 worktree。
- 托管应用成员校验失败时统一返回带加载上下文的 `FORBIDDEN`：message 显示应用、版本和工作区类型/名称/ID，`details` 只包含 `loadingStage`、`appId`、`appName`、`versionId`、`version`、`applicationWorkspaceId`、`workspaceKind`、`workspaceName`、`workspaceId`、`personalWorkspaceId` 等安全业务字段，便于排查“切换应用失败”时实际加载的是哪个应用、版本和工作区。
- 通过 domain 广播端口发布/消费 `workspace.version.sync-requested`，并通过本机补偿器扫描缺失或落后的副本。
- 与文件相关的 git 操作、差异比对、agent/skill 文件管理优先进入本模块。
- 工作区 Git Diff 即使带工作区 pathspec，也使用 `git status --porcelain --untracked-files=all` 展开未跟踪目录中的每个文件，使接口文件数、前端数量角标和实际文件数一致；unmerged 状态保留 `rawStatus` 并返回 `status=conflict`。响应还返回 `mergeInProgress/applicationUpdatePending/applicationTargetCommit`，让个人 worktree 在 dirty 跳过、真实冲突或冲突已解决待完成 merge 时展示准确状态。普通文件通过真实 stage/unstage API 操作 index；冲突支持逐文件处理、全部采用个人/远程版本、取消 merge，并在全部解决后通过专用完成接口提交完整 merge index。个人发布要求允许发布的文件先在个人 worktree 本地提交，再投影到应用 feature worktree；应用 Agent/Skill 同样先进入个人 HEAD，再只投影 `.opencode/**` 白名单，成功后更新版本/副本 HEAD、广播并触发固定提交反向 merge。只有远端 push 完成才返回 `remotePushed=true`，响应和错误 details 会携带当前 Git 阶段与已执行命令；传入 `operationId` 时复用 Agent 配置进度端口。
- 所有平台用户触发的 Git commit 以及可能产生 commit 的 merge 都必须显式传入当前用户身份，并注入命令级作者/提交者；提交与发布程序不再提供缺省身份兼容入口，也不依赖公共仓库或全局 Git 配置中的默认身份。平台没有邮箱字段时由 common Git 工具按统一认证号生成企业 SCM 已登记的 `mails.icbc` email，避免 invalid committer 拒绝。公共发布额外使用 `commit-tree` 从最终文件树生成单父提交，远端只新增当前管理员身份的提交，不要求企业 SCM 接受个人分支中的旧历史身份。
- `checkVersionGitAccess()` 在用户显式选择应用版本、创建或修复个人 worktree 前复用 `GitRemoteService.listBranches()`、当前用户唯一 SSH key 和内部仓库有效 URL 做只读预检。认证失败/仓库不可访问返回稳定的权限申请结果，缺少 SSH key 单独返回配置提示；网络和超时继续抛统一 Git 异常。

## 测试覆盖

- `WorkspaceApplicationServiceTest` 覆盖工作区创建、服务器归属、分页/详情查询、未找到错误和文件服务编排。
- `WorkspaceFileServiceTest` 覆盖 UTF-8 读写、跨多字节字符边界的完整渐进预览、预览期间文件变化栅栏、分片上传超过一次性读取阈值、上传分片顺序/声明大小/取消与临时文件清理、旧 Base64 上传兼容、普通文件与非空目录整体移动、同路径幂等、根/后代/符号链接/特殊文件/越界拒绝、校验后工作区根祖先或目标父目录替换失败关闭、目标并发创建不覆盖、普通文件和目录同目录重命名、普通文件/目录树删除、工作区根与 `.git` 删除拒绝、目录列表排序与上限、相对路径/空关键字文件搜索、一次性读取阈值和 null 内容写入。
- `WorkspaceDirectoryServiceTest` 覆盖服务器工作空间选择器的默认目录、只返回子目录、排序、父目录、条目上限和缺失目录错误码。
- `GitPublishWorkflowTest` 覆盖直接发布、worktree 合并发布、冲突文件收集、merge abort、abort 失败保护，以及同步文件时先 clean/pull 再复制提交推送。
- `ManagedWorkspaceApplicationServiceTest` 覆盖应用成员校验及 `FORBIDDEN` 加载上下文、托管逻辑路径、个人 worktree 创建、Git diff、个人 worktree 本地提交、从个人 `HEAD` 按白名单投影并推送 feature、所有角色 spec 禁推、应用 Agent 发布后的版本 HEAD 更新、feature 固定提交反向 merge、dirty worktree 保持 rollout retry、真实冲突进入 Diff 并通过专用接口完成，以及应用副本只读 Git 操作及失败阶段命令透传。`GitWorkspaceServiceRealGitTest` 使用真实临时 Git 仓库验证固定提交 merge、三方冲突、解决和提交完成。
- `AgentConfigApplicationServiceTest` 覆盖公共仓库初始化/更新、显式拉取先合并当前管理员稳定个人 worktree且脏 worktree 不更新共享副本、脏副本类型/路径/文件诊断、clean worktree 的待发布提交识别、当前用户长期公共 worktree 的稳定命名与复用、按服务器和创建人过滤、跨用户操作拒绝、公共/应用 Agent 文件回退路径映射、Agent/Skill 双语展示名解析与历史标题回退、公共最终文件树生成干净线性提交后以 refspec 推送、冲突保留在个人 worktree、共享运行时副本同步与广播、push 成功后请求线程不认领本机同步且持久化补偿仍可执行，以及工作空间级 Agent/Skill 配置读写、文件/目录删除和 diff。`GitWorkspaceServiceRealGitTest` 使用真实临时 Git 仓库验证污染个人历史不会成为发布提交祖先，并验证远端先产生新提交后个人 worktree 与共享运行副本均能同步到同一提交。

- `ReferenceRepositoryApplicationServiceTest`、`ReferenceRepositoryRealGitSafetyTest`、`ReferenceRepositoryReplicaTaskDispatcherTest`、`ReferenceRepositoryReplicaReconcilerTest` 覆盖分支一次性初始化、generation/CAS、本机即时/广播异步唤醒、有界并发与去重、按时退避、队列拒绝、租约丢失、离线 `DEFERRED`/恢复、本机文件锁、临时 clone + 原子移动、已对齐 HEAD 无操作快速路径、已有仓库脏状态/origin/分支/分叉保护、树路径穿越/`.git`/符号链接/1000 项上限和补偿器生命周期。
- 其中 `ReferenceRepositoryApplicationServiceTest` 还覆盖服务器展示路径规范化、引用根参数缺失和历史非法英文名兼容。
- `WorkspaceViewApplicationServiceTest` 覆盖 JSONC 注释/尾逗号/缺失/非法/超限、Git 引用对象拒绝、merge true/false、递归目录归并、文件及类型冲突并列、稳定 ID、归并后 1000 项上限、personal/version/replica 映射、逐次 READY 校验、UTF-8 只读、路径穿越/`.git`/符号链接和安全 warning。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Context。
- Jackson Databind（仅用于服务端安全解析工作区 JSONC 引用元数据）。
- SLF4J API。
- JNA（用于 Linux/macOS/Windows 目录句柄相对、不覆盖的原子工作区移动）。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。
- `test-agent-persistence` 实现类。

## 后续 AI 编码指引

新增与 workspace、文件、应用版本工作区、个人工作区、git、agent 或 skill 管理相关的业务逻辑时优先改这里；HTTP 入口只放在 `test-agent-api`。
