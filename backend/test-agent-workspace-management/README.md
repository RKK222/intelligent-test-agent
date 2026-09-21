# test-agent-workspace-management

## 系统管理员团队代码视图

`TeamWorkspaceApplicationService` 按授权范围列出应用、工作空间模板、版本、当前/历史人员及同版本多个个人 worktree；Git 状态区分 staged、unstaged、untracked，个人提交限定 `baseCommit..HEAD`，merge 标为 `SYNC_MERGE`，已发布提交还必须位于个人基线可达的版本目标范围，并同时匹配既有 SCM 校准姓名和统一认证号邮箱。祖先关系无法确认时失败关闭并返回“无法归属”。

`TeamWorkspaceExportService` 在现有 Java 节点内异步导出全部个人 worktree，全局最多四个、同源节点最多两个并发，明细通过数据库租约防重复领取。预检上限为 2 GiB/5 万个普通文件；`.git`、符号链接、特殊文件和敏感凭据不进入 shard，排除路径/原因只写 ZIP `manifest.json`，审计只保存路径摘要。源节点生成稳定 shard，控制面复用公共 Java 路由，文件内容只通过文件 WebSocket 到协调节点；成功子集生成 `PARTIAL_READY`，产物保留两小时。成员移除会主动取消相关任务并通知协调节点清理，离线协调节点由仅处理本节点任务的每分钟轮询兜底；取消、过期或其它实时撤权同样阻断下载并清理。

对应回归包括真实临时 Git 仓库、敏感文件规则、文件 WebSocket `TEAM_READ_ONLY` 权限与审计，以及 H2/固定 `.env.test` PostgreSQL 的团队关系、租约和迁移测试。

创建应用版本使用既有注入 Clock 取时；副本写入后同仓库状态回写的时间校验遵循数据库微秒舍入精度，避免纳秒时间与数据库读回值差异导致创建失败。`ManagedWorkspaceApplicationServiceTest` 使用固定纳秒时间和模拟数据库舍入覆盖完整创建及最近工作区登记。顶部与左下角创建入口均复用 `createVersion`。

Agent 配置权限补充：公共 Git 的 worktree 管理、暂存、提交和发布仍仅允许 `SUPER_ADMIN`；已登录用户可在服务层所有权校验通过后回退本人公共个人 worktree 的本地改动。应用 Agent 的暂存、提交和发布仍由 `APP_ADMIN`（含 `SUPER_ADMIN`）执行，普通成员仅可回退本人个人 worktree 中的应用 Agent 本地改动，不能指定共享 worktree。

Agent 配置分片上传支持浏览器目录选择携带的多层相对路径；应用服务在公共或应用 Agent 根内通过 `WorkspaceFileService.createDirectory` 递归创建缺失父目录，再启动上传会话，继续复用统一的根目录、符号链接、越界和重名保护。

应用工作空间模板列表只返回配置管理中 `enabled=true` 的非自动化模板；响应继续携带 `repositoryType` 和兼容字段 `standard`。自动化代码库不再使用工作空间模板/版本模型，而由 `(appId, repositoryId)` 唯一当前配置管理；旧模板、版本、副本和个人 worktree 仅保留追溯，不能进入主工作空间、recent、个人 worktree 或 Git 入口。

自动化引用同步使用独立的 generation/replica 状态和 `automation-reference.sync-requested` 广播，复用 `ReferenceRepositoryReplicaTaskDispatcher` 的有界后台队列与 generation fencing。同一应用、版本库和 generation 的重复唤醒合并，不同应用或版本库互不影响；HTTP 与广播线程都不等待 Git。共享配置代次保存可编辑 `referenceAlias`、分支、目录和描述；同一应用内当前/待激活自动化别名不得与其它版本库重复，并按 OpenCode 原生引用发现边界校验。副本固定落在 `OPENCODE_REFERENCES_DIR/automation/{appDigest}/{repositoryEnglishName}/{generation}`，不进入应用资产目录，也不创建个人 worktree。

- `AutomationWorkspaceReferenceCatalogService` 按用户、主工作空间、应用、版本库、配置 generation 和当前 Java 服务器解析共享只读副本，要求 JSONC 逻辑路径、目标 commit 和本机副本均可验证；展开当前代次时签发只保存哈希的标签租约，已打开标签可在管理员切换后继续读取旧代次。不可用项转为局部告警，普通工作树和其它引用继续可用。
- `ApplicationAutomationReferenceWorkspaceReconciliationService` 是自动化 JSONC 的唯一补丁与对账入口：工作区进入/显式刷新、管理员当前页面观察到 generation READY，以及真实 Run 派发都复用它。服务端重新解析应用当前 generation 和本机精确 READY 副本，只逐字段维护托管引用与精确只读权限；写入携带最近读取内容的 SHA-256，冲突后重新读取并重算一次，第二次冲突返回 `CONFLICT`，禁止覆盖用户并发编辑。
- `ApplicationAutomationReferenceRunLeaseService` 在 Run 可见副作用前调用上述唯一对账器，配置变化时完成运行态 reload，并固定本次确实 READY 的 generation；不可用单库只产生安全告警并跳过。Run 只持久化 generation 生命周期租约，绝不向消息、system prompt 或 OpenCode 上下文拼接自动化说明或路径。补偿器在 Run/标签租约均释放后退役旧 generation，并只删除本机受管共享副本。
- `ApplicationAutomationReferenceService` 以不可变 generation 管理每个应用自动化版本库的一套分支、任意层级目录、共享描述和固定目标提交。每个 generation、每台服务器只维护一个共享只读仓库副本；目录只是副本内逻辑选择。在线服务器全部 READY 后才以 CAS 激活，离线节点标记 `DEFERRED` 并由 `ApplicationAutomationReferenceReconciler` 恢复后补齐；更新副本建立新提交代次，Git 指针核验只读本地状态。
- 自动化共享副本的同步、核验和错误归因统一在引用根目录日志脱敏作用域内执行，Git 命令日志不得输出服务器物理路径；Agent/Skill 的本机快照周期对账只扫描仍启用的应用工作空间模板，已停用的旧自动化模板、版本和 worktree 只保留历史，不再被后台任务读取或投影。
- `WorkspaceViewApplicationService` 在组合根增加虚拟“自动化代码库”，只装载当前工作树 `.opencode/opencode.jsonc` 中由平台写入且应用、版本库、generation、目录和逻辑配置路径均可重新验证的自动化条目；使用 `AUTOMATION_ROOT/AUTOMATION_REFERENCE` 定位器提供目录、文本、分片和二进制只读读取。每次操作重新授权，不接受客户端物理路径；`.git`、符号链接、越界和全部写/Git/搜索/requirements 操作固定拒绝。
- 浏览器不再读取、解析或写入自动化 JSONC；它只通过 Agent 配置文件 WebSocket 的 `agent-config.automation-reference.reconcile` 请求后端权威对账。应用资产库仍保留前端 `patchReferenceConfig` 的既有最小补丁，二者不能互相复用实现或形成第二套自动化规则。
- 存量个人 runtime Workspace 若仍记录在应用目录而配置留在固定 `workspace/.opencode`，Agent 配置读取与组合树可兼容该受控子目录；首次引用保存会写入会话根的标准 `.opencode/opencode.jsonc`，使 OpenCode 从当前 cwd 原生加载同一份 references/permission。不会递归搜索，也不接受客户端物理路径。
- 应用工作区未创建可选的 `.opencode` 目录时，配置树按空目录返回、单文件读取按 `NOT_FOUND` 返回；真实工作区根目录缺失仍保持 `ROOT_UNAVAILABLE`。没有自动化引用的 Run 不创建空 `.opencode/opencode.jsonc`，只有实际需要写入托管引用时才通过既有条件写链路创建标准目录和配置文件。

## 工程定位

Workspace、文件管理、应用版本工作区、个人工作区、git/diff、agent 和 skill 管理业务模块。

## 主要职责

- `RequirementImportApplicationService` 重新查询 TCDS 授权目录和文档元数据，将受支持的 Office/文本内容转换为 Markdown，并只通过既有工作区文件服务在 `spec/` 下幂等写入；同一服务使用与导入一致的安全目录规范化返回父子条目“已导入/未导入”状态，批量状态查询只解析一次工作区元数据，各相对路径仍逐一经过公共文件服务校验，已导入项仍可覆盖重试。导入结果额外返回本批次去重后的 `spec/{父条目}` 相对展示路径，供前端定向刷新和展开，不返回物理根目录或文档路径；选择上限、下载容量、路径规范化、碰撞和逐文件失败都在服务端执行。

公共 Agent/Skill 的 `update`、`update-and-push`、`publish` 在任何远端 push 或工作树修改前，先通过 `PublicAgentConfigRolloutCoordinator` 建立 `PREPARING` 持久化禁发任务；远端提交确认后才转为 `DRAINING` 并广播 `rolloutId`。push 回包不确定时会 fetch 验证远端是否已包含目标提交；发起 Java 退出时，同服务器补偿任务按远端事实恢复 PREPARING。每台服务器通过数据库租约认领同步任务，复用发起用户已加密保存的 SSH key 刷新 origin、fetch、checkout/reset 共享运行副本到明确 commit，再把同一 commit 原生 merge 到本机所有有效公共个人 worktree。已知目标分支的公共仓库 fetch 统一显式更新该分支的远端跟踪引用，不依赖存量 clone 的单分支 fetchspec，因此仓库默认分支与公共配置分支不同时仍能解析并同步目标提交。非重叠 staged/unstaged/untracked 内容保留；覆盖风险或冲突写入独立公共 worktree 补偿任务，不阻塞共享副本、其它用户或主 rollout。发布请求在远端提交确认、rollout 激活并广播后立即返回，不在 HTTP 请求线程认领或执行本机同步；本机和其它服务器均由广播消费者或 5 秒持久化补偿程序异步推进。公共个人 worktree 仍是管理员编辑事实源，共享仓库只作为各服务器运行时副本；公共“拉取”以远端分支 commit 为全服务器唯一目标，不再绑定某一服务器或只处理当前管理员。公共发布不会再推送长期个人分支的整段历史，而是把合并后的最终文件树投影为以当前远端提交为唯一父节点、由当前管理员企业身份签署的线性提交，避免旧的无效 committer 污染新发布。

应用 workspace/应用 Agent 发布在 feature 提交成功但 push 回包失败时，会重新查询远端是否已包含目标提交：确认未到达远端时回退 feature 临时提交并保留个人提交供重试；远端状态不确定时保留持久化 `PREPARING` 闸门和明确恢复动作。重试先检查 feature index 是否仍有 staged 内容，没有时跳过空提交并直接执行远端事实核验或 push，避免网络响应丢失后重复提交失败。错误 details 只返回 `localCommitRetained/remoteCommitState/publishRecoveryAction` 等稳定状态，不返回远端 URL、命令或 stderr。

工作区 Git Diff 以个人仓库本地 `refs/remotes/origin/{应用分支}` 跟踪引用到个人 `HEAD` 的完整提交树作为存量“已提交未推送”白名单事实；应用版本固定 `targetCommit` 仍只用于确认个人 HEAD 已包含应用基线。仅当两者都是个人 HEAD 祖先、没有待合入应用更新且不在 merge 中时执行；缺少本地跟踪引用时不以数据库 target 冒充远程事实，并返回不可判定。差异限定当前 workspace 目录并排除 `spec/**`，rename 展开为旧路径删除和新路径新增，copy 作为新增，`.opencode/**` 保留给前端分流到应用 Agent。正常无差异返回权威空数组；只读 Git 恢复异常返回 `null`，使新前端保留旧浏览器缓存兼容。重新推送直接复用 publish，不重复个人 commit。
- 应用源码远端树快照在一次业务调用内只解析一次分支提交，并用该固定提交列树，同时返回 `targetCommit/nodes`；空目录也保留提交供后续物化并发校验，原有 `listTree` 继续只返回节点。

公共 Agent/Skill 的 `update`、`update-and-push`、`publish` 在任何远端 push 或共享运行副本切换前，先通过 `PublicAgentConfigRolloutCoordinator` 建立 `PREPARING` 持久化禁发任务；远端提交确认后才转为 `DRAINING` 并广播 `rolloutId`。push 回包不确定时会 fetch 验证远端是否已包含目标提交；发起 Java 退出时，同服务器补偿任务按远端事实恢复 PREPARING。每台服务器通过数据库租约认领同步任务，复用发起用户已加密保存的 SSH key 刷新 origin、fetch、checkout/reset 到明确 commit，再登记本机 manager 进程并确认同步，因此广播丢失、Java 重启、同服务器多 Java 或瞬时 Git 失败都不会提前解除门禁。发布请求在远端提交确认、rollout 激活并广播后立即返回，不在 HTTP 请求线程认领或执行本机同步；本机和其它服务器均由广播消费者或 5 秒持久化补偿程序异步推进。公共个人 worktree 仍是管理员编辑事实源，共享仓库只作为各服务器运行时副本；显式“拉取”会先同步当前管理员的稳定个人 worktree，成功后才在 PREPARING 闸门内推进共享副本。公共发布不会再推送长期个人分支的整段历史，而是把合并后的最终文件树投影为以当前远端提交为唯一父节点、由当前管理员企业身份签署的线性提交，成功后再把个人分支和共享副本重置到该提交，避免旧的无效 committer 污染新发布。

- 工作区注册、查询和分页；普通、最近和排查响应只返回 `workspace:{workspaceId}` 逻辑定位符且 `physicalRootPath=null`。单文件绝对路径只能在普通非分享、非排查、非体验、非源码快照会话中通过文件 WebSocket 即时解析；超级管理员目录选择器直接返回 `existingWorkspaceId`，不再依赖普通响应比对绝对路径。
- `RequirementImportApplicationService` 重新读取 TCDS 应用目录、父子条目和文档元数据，只接受子条目编号选择，并通过现有工作区文件服务幂等创建 `spec/{父条目}/01-需求` 至 `04-测试` 目录。应用目录只供页面输入建议，条目和导入查询接受任意非空应用名称或简称，并由 TCDS 返回实际查询结果。Word/Excel/PowerPoint/文本统一转换为 Markdown；Word 恢复轻量文本抽取，DOCX 只保留段落与表格文本，旧 DOC 使用现有 HWPF 文本提取，不再生成行内样式、列表层级或图片附件，避免大多数已压平 TCDS 文档被过度渲染后影响页面编辑性能。继续兼容 `.doc` 名称承载 DOCX、`.ppt` 名称承载 PPTX，以及 Word 扩展名实际返回 UTF-8/GB18030 文本；HTTP 200 返回的 HTML/JSON 错误包络会拒绝写入。单次限制 100 个子项、200 个文档、单个 Markdown 20 MiB、总下载量 200 MiB，单文档失败返回 `PARTIAL` 且保留其它成功文件；转换日志只记录扩展名、脱敏媒体类型和异常类别。
- 平台体验工作区由每台后端服务器各维护一套本地共享目录。`OPENCODE_EXPERIENCE_WORKSPACE_DIR` 默认解析为 `${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/experience`；标准开发重启和企业部署在 Java 启动前调用 `deploy/internal/ensure-experience-workspace-content.sh`，从随包独立模板仅补齐缺失的 README、`docs/` 七类稳定资料和 `spec/{需求项}/01-需求` 至 `04-测试` 的登录示例。无 HEAD 仓库只建立一个无 remote 基线提交；已有 HEAD 时不重置、不覆盖用户文件、不改提交历史，新补文件保留为本地未跟踪变更。绕过标准脚本直接启动 Java 时，`ExperienceWorkspaceStartupRunner` 仍保留目录、`.git` 和 README 的最小失败关闭兜底。服务器稳定 ID 与目录真实路径生成确定性 `wrk_exp_` Workspace ID，同服务器配置不变时幂等复用，换目录时切换当前绑定并保留旧 Workspace 供历史 Session/Run 外键使用。所有 `wrk_exp_` ID（含历史绑定）在 Agent 配置应用服务底层统一拒绝，不能借 HTTP、路由或文件 WebSocket 触达任意层级 `.opencode`/`.git`。普通文件列表不展示符号链接，直接路径逐段拒绝跟随；体验 Git Diff 也过滤任意层级且大小写不敏感的 `.git`/`.opencode` 与符号链接条目，并使用精确 pathspec 和有界 patch。该检查是平台接口护栏，不替代共享终端/Agent 所需的 OS 沙箱。
- `ExperienceWorkspaceApplicationService` 是体验访问的统一权威策略：任何用户不论是否加入应用、是否首次进入或角色，都可在 TestAgent READY 后随时进入；Workspace 仍必须是本服务器当前绑定、ACTIVE 且对应当前目录配置。打开、详情、Session 创建、Git 状态、文件 route/ticket/RPC 和运行上下文均复用该策略；参数或服务器绑定变化后旧 ID 立即失败关闭，应用成员变化不再撤权，普通工作区列表继续排除全部当前和历史 `wrk_exp_` 记录。普通文件可读写且继续保护根、`.git` 和受控配置路径。体验 Git 允许受控 stage、unstage、discard、冲突读取/解决/取消、完成 merge 和按指定路径本地 commit；commit 不夹带共享 index 中其它暂存文件。体验区不提供 personal pull、remote push 或发布入口，前端也永久隐藏 push。
- 应用引用资产库管理：只处理当前应用关联的 `APPLICATION_ASSET_REPOSITORY`，首次初始化固定远端 HEAD，后续同分支同步或管理员受控切换分支都以 generation 固定目标提交；按在线及历史 Linux 服务器创建副本目标。generation 建档后立即提交本机有界异步 worker，并通过 `reference-repository.sync-requested` 唤醒其它 Java；广播消费者只排队、不在 Redis listener 线程执行 Git，任务继续由数据库租约/CAS fencing、本机文件锁和 60 秒补偿扫描保护。瞬时失败按数据库退避时间定向重试；管理员可携带页面观察到的 generation 终止活动操作，事务内把未完成副本置为 `BLOCKED`、清除租约/退避，再通过 `reference-repository.cancel-requested` 中断各 Java 本机任务及其 Git 进程树，迟到 worker 仍由 generation/lease fencing 拒绝写回。终止后的重试创建新 generation；调度器拒绝、广播丢失或进程退出仍由补偿扫描恢复。离线副本进入 `DEFERRED` 并在恢复后补齐；新目录在同根临时目录校验后原子移动，已有目录必须干净且同源，同分支仅允许快进；实际分支与固定 HEAD 已一致时跳过 fetch/reset，跨分支则显式抓取目标 refspec，从固定提交创建不存在的本地分支，已有目标本地分支仍拒绝分叉。主动指针核验只读本地实际 branch、HEAD、origin 和工作树状态，实际快照与目标指针分别返回。目录树仅开放总体与当前服务器副本均 `READY` 的单层安全读取，并只把相对路径精确命中 `REFERENCES_SDD_FOLDER_NAMES` 的目录标记为可选。
- `spec` 与 `docs` 必须整体选择，但允许通过参数配置为根层 `spec` 或二级 `ai-agent/spec` 等精确相对路径；父目录只供展开，不会随子目录一起挂载，`spec` 内部版本目录和需求项目录仍只能浏览。
- 引用资产库列表和状态另返回可空的 `repositoryPath`：业务层只用当前平台 `OPENCODE_REFERENCES_DIR` 与可信英文名派生规范化绝对路径；参数缺失或历史非法名称不阻断仓库列表，也不把物理路径写入错误或日志。
- 应用源码快照编排：仅处理当前启用应用关联的 `APPLICATION_CODE_REPOSITORY`。所有目录、分支、树、物化、同 generation 重试、打开和最近选择入口都会实时复核有效成员与仓库关联；个人快照固定当前用户 READY OpenCode 进程所在服务器，团队快照冻结受理时在线后端服务器集合。物化先在事务外把分支解析为固定 commit 并校验 FILE/DIRECTORY/`.` 选择，再在事务内锁仓库；同一 `operationId` 的重放在仓库锁内先于 slot 的 expected/pending generation 校验，严格绑定首请求 app、repository、actor、类型、requestHash 和 source generation，并从已登记 SERVER steps/replicas 返回首请求冻结服务器，不能被第二次在线集合改变。全新请求的目标服务器 cleanup task 必须是第一条持久化写，随后才建立 pending generation、operation、replica 和每服务器固定 13 步时间线。提交后由本机有界 dispatcher 与低敏广播唤醒各服务器，dispatcher 启动时及默认每 5 秒既扫描本机可领取副本，也恢复满足 operation 自身终态门禁的 stranded 记录，因此队列拒绝、广播丢失、Java 重启或终态事务响应丢失不会永久挂起。数据库租约、generation fencing 和同根文件锁保证每服务器单执行；步骤写入先锁定并核对活租约，新 lease attempt 会重置整条稳定时间线，旧 owner 发现失租后立即停止，不能写后续步骤或副本结果。worker 使用当前操作人的同一临时 SSH 凭据完成浅克隆、冻结提交显式 fetch、checkout 与提交校验，再执行本地 `sparse-checkout --no-cone`，不递归子模块。配置根以下的物化、索引、打开和清理路径逐段执行 `NOFOLLOW_LINKS` 校验并在创建后复核，拒绝祖先/目标符号链接；删除 `.git` 后原子写入索引并替换目录。磁盘发布与数据库 READY 写回在同一文件锁回调内完成；数据库 completion 失败会恢复旧目录，completion 成功后即为不可逆发布点，backup 删除只作 best-effort，失败遗留由 cleanup 回收且不得回滚新目录。
- worker 读出当前 operation 后，副本 claim 仍会在数据库单条 CAS 中绑定该精确 operationId、未终态状态和同服务器可领取步骤；并发 retry 终态化会使迟到 claim 返回未认领。旧 attempt 已写完步骤但副本结果 CAS 丢失时，只有租约已过期的 `RUNNING` 副本可作为恢复锚点接管，随后沿用统一时间线 reset，不预写步骤或制造双事务窗口。
- 应用源码 generation 至少一台副本 READY 才提升；首台 READY 在同一槽位锁事务中先失效旧 ACTIVE 再激活新代，部分成功只开放 READY 服务器并立即到期旧 generation 的各服务器清理任务，全失败保留旧 active/expiry。所有 success/failure 与 stranded 恢复都在同一 repository slot 行锁下重读 operation、步骤和全体副本并串行写 snapshot/slot/operation 终态，三服务器最后两台并发不会漏收敛。普通 DOWNLOAD/UPDATE 可按全副本终态恢复历史 stranded；`RETRY_REPLICAS` 以本 operation 的 SERVER steps 为完成权威，只要任一目标仍为 `PENDING/RUNNING` 就不得聚合终态或补成失败/跳过。重试只重建同 generation 的失败/陈旧服务器步骤，不改变 commit、selection 或 expiry；同一 retry `operationId` 的重放身份仅由 route app、repository、actor、`RETRY_REPLICAS` 和 expected generation 这些客户端不可变事实决定，不依赖已变化的失败服务器集合；并发首次登记在仓库锁内使用相同身份收敛。`PERSONAL -> TEAM` 建立全新 generation，`TEAM -> PERSONAL` 拒绝。仓库列表按业务事实严格投影 `NOT_DOWNLOADED/DOWNLOADED_ACTIVE/DOWNLOADED_EXPIRED/PERSONAL_OCCUPIED` 四态，同时返回占用人的 userId、姓名和统一认证号、当前持久化操作/安全步骤摘要；没有 active snapshot 或 snapshot 已过期时，任一当前有效应用成员都得到 `manageable=true`，未过期 snapshot 仅 owner 或 `APP_ADMIN` 可管理；`openable` 则独立按当前用户进程服务器上的 READY 副本计算，API 层无需越过业务服务读取用户库。操作详情先确认代码库类型，再按该 repository 的任一当前启用关联应用复核有效成员；TEAM 成员可跨发起应用观察，PERSONAL 仍仅 owner 或同时满足上述成员条件的 `APP_ADMIN`。每个 generation/server 注册新的 `appsource:<repositoryEnglishName>` Runtime Workspace；打开、最近选择、文件 ticket 与每条文件 RPC 都按当前 slot generation、snapshot ACTIVE/未过期、READY replica、Workspace 行与 replica server 一致、应用关联和实时成员关系重新鉴权，`SUPER_ADMIN` 不旁路。首次打开尚未创建 Session 时，普通用户 Workspace 详情查询在个人/会话范围未命中后，只对同一权威鉴权明确分类为 `APP_SOURCE` 的 ACTIVE Workspace 回退工作区主表；其它 `STANDARD` 工作区仍返回 `NOT_FOUND`。授权器从同一次权威判断返回 `STANDARD/APP_SOURCE` 分类，供详情回退与 ticket 固定签发时复用；APP_SOURCE 票后续映射消失时不得降级为超级管理员非托管兼容访问。
- 应用源码索引固定为 `.testagent-appsource-index.json`，数据库 SHA-256 是权威值；缺失或损坏时从冻结 snapshot 原子修复。原始工作区列表、搜索、读取、分片读取、写入、上传、复制、移动、重命名和删除都隐藏或拒绝该保留路径。最近选择只在确定性的权限、资源不存在或生命周期冲突时删除失效偏好，Git、文件系统和内部故障会原样失败并保留偏好；显式清除也会先按当前服务器执行完整打开鉴权。每分钟 `workspace-management.app-source-cleanup` 只作低敏集群唤醒，各服务器按数据库 cleanup 租约删除到期源码、staging 和 backup，保留/修复索引并归档旧 Workspace；服务器离线时任务留待恢复，新 generation 的本机 READY 副本作为删除 fence，不能误删新源码。
- 应用源码保留期允许 1–168 整小时（最长 7 天），默认 48；当前未过期 generation 的 owner 或应用管理员可按首次受理时间调整总保留小时数。续期锁定仓库、slot 和全部 cleanup 行，拒绝 pending generation 与已经开始的清理，并在同一事务中更新 snapshot expiry、权威索引 SHA 和所有服务器的 `deleteAt/nextRetryAt`；磁盘索引在下次打开时按新 SHA 自动修复，不重新下载源码。
- cleanup 与发布共用同一文件锁；即使本机新 generation READY 持有共享根，旧 cleanup 也只会回收名称匹配标准 UUID 的已完成发布 backup，不会触碰当前 target、新 generation staging、其它仓库、非 UUID `.backup` 或 `.backup.tmp` 等相似名称，重复更新不会持续累积 backup。
- 工作区引用组合视图：读取当前工作区最新 `.opencode/opencode.jsonc`，只接受能反向验证到当前应用资产库、本机同 generation `READY` 副本、当前平台引用根目录和允许 SDD 根目录的本地引用对象。`merge=true` 按 `sdd-folder-name` 合并到工作区同名一级目录，工作区已有目录保持普通来源，纯引用后代标记只读引用来源；`merge=false` 以参考别名投影只读一级目录。文件同名不覆盖，节点使用稳定身份并携带冲突来源；单引用异常转为局部 warning，不阻断工作区内容，所有路径继续拒绝 `.git`、符号链接和 root 穿越。
- `merge=true` 的 `spec` 引用保留资产库内原始相对路径，既支持 `spec/I2026.../概要设计.md`，也支持 `spec/2610/需求用例.md` 与 `spec/2610/I2026.../概要设计.md` 并存；组合视图不识别或重排版本、需求项目录。
- 工作区注册时记录 `linuxServerId`，并通过 `WorkspaceServerIdentity` 提供当前 Java 进程所属服务器和默认目录。
- 个人工作区与当前用户 `opencode` ACTIVE binding 服务器不一致时，default 工作区进入请求只返回可重试 `CONFLICT`，不得直接改库后在旧服务器遗留未提交内容。XXL 每 30 分钟触发 `workspace-management.personal-workspace-relocation`，Redis 广播唤醒全部 Java；每台 Java 只认领源目录属于本服务器的记录。源端用 Git bundle + manifest 保存个人 `HEAD`（含仅本地提交）、staged、unstaged 和全部普通 untracked 文件（包括被 Git ignore 的文件），目标端经专用一次性 WebSocket 校验大小/SHA-256 并恢复相同 Git 状态；目标文件完成校验后才在同一事务内切换 `workspaces`、`personal_workspaces` 和搬迁状态，最后清理旧 worktree。活动 Run、未完成 merge、Git 子模块、源端并发变化、归档超过 2 GiB、超过 10,000 个 untracked 文件或不支持的 untracked 符号链接/特殊文件均失败关闭并安全重试，不通过 Java→Java HTTP 传文件。
- 工作区内原始文件单层列表、受限相对路径搜索、UTF-8 内容读写、分片二进制新文件上传、普通文件跨目录复制、普通文件或普通目录（包括非空目录）同工作区移动、普通文件或目录同目录重命名、文件状态、普通文件/目录树删除和路径越权拦截；所有以符号链接为末端或中间路径的请求逐段拒绝，搜索不跟随符号链接，父目录列表只保留链接名称兼容既有公共 Agent 视图，并拒绝工作区根目录和任意层级 `.git` 元数据删除。`workspace.move` 保持 `workspaceId/sourcePath/targetPath` 与成功 `null` 的既有 RPC 契约，以一次原子文件系统重命名整体移动普通文件或普通目录（包括非空目录），不递归拆分且不覆盖目标；同路径幂等成功，缺失源为 `NOT_FOUND`，目标已存在为 `CONFLICT`，根、特殊文件和目录自身后代目标为 `VALIDATION_ERROR`，符号链接或路径越界为 `FORBIDDEN`。移动前固定真实 root/source/目标父目录；Linux 从 `/` 逐段打开目录句柄并直接调用内核 `renameat2(RENAME_NOREPLACE)`，兼容 Alpine/musl 未导出包装函数；macOS 使用逐段目录句柄和 `renameatx_np(RENAME_EXCL | RENAME_NOFOLLOW_ANY)`；Windows 固定源条目和目标父目录句柄、核对最终路径后使用 `SetFileInformationByHandle` 且禁止替换。目标父目录替换或目标并发创建都在原子操作层失败关闭，其他平台缺少等价能力时拒绝移动。上传不设置应用层总大小上限，每片有界并先写同目录隐藏临时文件，声明大小校验成功后才以不覆盖方式发布；取消、连接关闭或失败立即清理，24 小时残留由后续上传尽力回收。UTF-8 一次性读取和文本编辑默认阈值为 5 MiB；大文件通过固定约 512 KiB、UTF-8 字符边界对齐的分段渐进只读预览，可读取到 EOF，文件大小或修改时间变化时停止混合拼接。搜索支持空关键字文件目录，并受数量、深度和超时上限保护。组合文件树只新增只读 list/read 视图，不改变这些原始写操作的物理工作区边界。
- 文件 WebSocket ticket 创建前通过 `requireWorkspaceOnCurrentServer` 校验 workspace、当前后端和用户 opencode 进程同服务器；历史空服务器归属工作区在 root path 校验成功后回填当前服务器 ID。
- 文件下载使用固定约 512 KiB 的 Base64 原始字节分段，支持任意二进制文件；普通工作区和引用组合视图分别复用安全路径与逻辑 locator 校验，后续分段回传首段大小/修改时间快照，文件变化时返回 `DOWNLOAD_CHANGED` 并停止混合拼接。
- 普通前端不再传物理目录创建 Workspace；应用版本和个人工作区目录由后端按通用参数与业务 id 派生。超级管理员服务器工作空间选择器通过目标后端目录浏览能力从该后端 Java 进程运行目录开始浏览。
- `WorkspaceApplicationService` 同时实现领域 `TrustedWorkspaceResolver`：历史 `linux_server_id=null` 只有在当前节点能解析并访问真实 root 时才回填当前服务器。可信 root/server/status 变更先建立 Workspace mutation gate，关系型保存成功后用单个 Lua 原子再次失效并释放 gate，数据库失败只撤回自己的 gate token；托管个人/应用副本更新沿用同一规则，且仅在可信字段真正变化时执行，创建新 Workspace 不做无效清理。
- `UserWorkspaceQueryService` 只通过领域 `UserWorkspaceQueryRepository` 分页读取或校验指定用户关联的 ACTIVE 工作区，供普通 Workspace 所有权校验和 system-management 受审排查入口复用；排查列表可在同一归因范围内按名称或 Workspace ID 过滤，不直接访问 SQL，也不授予文件写能力。
- `ManagedConversationWorkspaceAccessAuthorizer` 实现运行上下文权限领域端口：应用版本/replica Workspace 必须属于已启用应用且当前用户为有效成员；个人 Workspace 还必须由当前用户拥有。文件授权和 `STANDARD/APP_SOURCE` 分类由同一次 Repository 判断产生，避免先授权后另查类型的竞态。`SUPER_ADMIN` 不旁路托管成员规则；找不到托管版本或个人映射的历史 Workspace 在会话入口沿用 Session owner 与可信路径规则，在文件入口默认拒绝，仅签票时已明确为非 AppSource 的 `SUPER_ADMIN` 服务器工作空间兼容访问可放行。
- Agent/Skill 配置管理：公共 Git 仍由 `SUPER_ADMIN` 独占并使用每位管理员的公共个人 worktree；用户进程初始化成功后，`preparePublicWorktreeForInitializedProcess` 校验当前 Java 与进程 `linuxServerId` 一致，再复用稳定分支创建程序幂等准备同服 worktree。为补偿“进程先初始化、后补超级管理员权限”等历史窗口，每台 Java 启动 30 秒后、此后默认每 10 分钟扫描一次本服务器已有 ACTIVE OpenCode binding 但缺少 ACTIVE 公共个人 worktree 的 ACTIVE `SUPER_ADMIN`，单轮最多 50 人，并用服务器级 Redis 租约避免重复执行；管理员也可通过同一程序手工触发。补偿只从本机已初始化共享仓库 HEAD 创建 `public-{userId}`，不读取目标用户 SSH key、不访问远端 Git、不自动切换其当前运行配置；单人失败隔离并留到下一轮重试。公共仓库或 Git 条件未就绪时只返回附加降级结果，不改变已健康的进程状态，也不清理其它服务器上的存量 worktree。服务器公共仓库“拉取”会先把远端公共分支合并到当前管理员在该服务器的稳定个人 worktree，再更新共享运行副本，避免文件树继续读取旧内容。个人 worktree 有未提交修改时默认拒绝拉取，显式确认放弃本地已跟踪修改后才允许 reset；合并冲突保留在个人 worktree 并沿用三方冲突处理。脏状态错误会在安全 details 中返回 `repositoryKind/path/dirtyFiles/discardLocalChangesAllowed`，明确区分当前管理员个人 worktree 与服务器共享运行副本，前端可按真实服务器、绝对路径和文件提供定点恢复入口。公共 Diff 还在 porcelain clean 时比较个人与共享 HEAD 的祖先关系和文件树；本地提交后发布失败时返回 `publishPending=true`，页面重开后可直接重新发布；有真实未提交文件时仍优先走正常暂存/提交或回退流程。应用级 Diff/Git 作用域是全部 Git 可见的 `.opencode/**` 用户配置，由 `APP_ADMIN` 管理，`SUPER_ADMIN` 继承该权限，普通成员只能读取；agent、skill、command、plugin、tool、旧 mode 别名及辅助源码不在服务层枚举，运行依赖文件由 `.gitignore` 排除，但已跟踪或实际出现在 Git status 中的文件仍进入 Diff。应用级配置不创建独立 Agent worktree，而是使用当前版本个人 workspace 的 Git 根；公共/应用文件树、读取、写入、分片二进制上传、文件同目录改名、普通文件复制/移动和文件/目录树删除统一走目标服务器文件 WebSocket ticket。复制/移动复用 `WorkspaceFileService.copyFile/moveFile` 的不覆盖、路径、符号链接和目录后代保护，应用级同时校验源和目标仍位于 `.opencode/**`；公共写操作要求 `SUPER_ADMIN`，应用写操作要求 `APP_ADMIN`。公共与应用根都可按 OpenCode 模板创建 `agents/<英文技术标识>.md` 或 `skills/<英文技术标识>/SKILL.md`、rules、templates；英文名称不填时由前端按完整拼音生成技术标识。
- OpenCode 保持原生配置加载：每个用户进程的 `OPENCODE_CONFIG_DIR` 固定为 `{sessionPath}/.testagent-runtime/current-public-config` 受管软链接；受管启动/重启时，有效的同服 ACTIVE `public-{userId}` worktree 优先指向本人目录，否则回退本服务器 `OPENCODE_PUBLIC_CONFIG_DIR` 共享运行副本。启动期解析只查询当前用户记录并校验物理目录，不扫描其他用户、不执行 Git 命令。应用配置仍只读取当前个人 worktree 的 `.opencode`。公共管理员个人 worktree 和应用 feature 副本都是 Git 编辑/发布源，不是额外运行时覆盖层，本链路不修改 OpenCode 源码、不复制配置。超级管理员首次初始化在进程健康后幂等准备同服 worktree 并自动加载；保存公共 Agent/Skill/JSONC 时仍只切换和刷新本人，保存 `opencode/tool[s]/**/*.js|ts` 时因进程级 ESM 模块缓存改走公共受管重启，其他文件继续只 dispose 本人。该本地预览不会影响其他用户，公共本地提交也不新增影响。公共拉取或提交并推送后，工作区模块按发布前后提交树识别 Tool 脚本变化：普通配置沿用全服务器空闲排空与 dispose，Tool 脚本则在同一空闲闸门内恢复共享指针并经公共停止/启动/health 程序逐用户受管重启。应用普通文件与 Agent/Skill/Tool 推送统一把 feature 固定提交通过 `git merge --no-edit <targetCommit>` 合入各服务器相关个人 worktree：即使存在 staged、unstaged 或 untracked 内容也先交给 Git 原生合并，非重叠改动原样保留并完成合并；只有 Git 判定本地文件会被覆盖时才记录 `LOCAL_CHANGES` 待处理，真实冲突保留 `MERGE_HEAD` 与三方 index。应用 Agent/Skill/JSONC rollout 只有在相关个人 worktree 包含目标提交后才登记该用户进程、等待空闲并 dispose；应用 `.opencode/tool[s]/**/*.js|ts` 则在相同闸门内受管重启，两者都不切换公共指针；未收敛时保持持久化 retry。个人保存应用 Agent、Skill 目录定义或引用 JSONC 直接只热加载当前用户；保存 Tool JS/TS 只受管重启当前用户。忙碌 Run 结束后再执行对应操作。
- Agent Markdown 文件和 Skill 目录继续使用英文技术标识。Agent 配置目录列表仅对 `agents/*.md` 和 `skills/*/SKILL.md` 有界读取最多 64 KiB 且不跟随符号链接，优先解析 Skill `metadata.display-name/display-name-zh`，其次解析双语 `description`，旧配置最后回退中文 Markdown 标题；响应只增加可选 `displayName/displayNameEn` 供前端中文展示，原始 `path/name` 不变。
- 基于配置管理中的应用工作空间模板创建应用版本工作区，clone 指定分支并创建运行态 `Workspace`；设置页创建应用工作空间时会复用该能力同步创建初始版本工作区，并按 `workspace_create_operations` 记录“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”进度。`应用 + 代码库 + 分支 + 目录路径` 已有模板时不会重复创建：保存会按请求别名更新该模板，并在模板已停用时重新启用，再继续确保对应版本工作区存在。本次请求新插入模板后如果 Git、目录、运行态 Workspace 或版本持久化失败，且数据库复核该模板仍无任何版本，失败补偿会删除该新模板；命中既有模板或版本已经落库时必须保留，避免破坏历史配置、重试入口或并发成功结果。设置页保存前的分支、目录树和新增目录操作均不落磁盘；只有保存接口会 clone/checkout。新版本复用服务器既有 feature 仓库时，在校验工作空间目录前要求工作树干净并执行 `fetch + pull --ff-only`，避免远端目录树已显示新目录但本地旧 HEAD 仍报目录不存在；脏工作树、分叉或 Git 失败直接阻断，不 stash、reset 或覆盖内容，按固定 target commit 打开的既有副本不触发该隐式拉取。测试工作库在保存阶段强校验分支必须符合 `feature_testagent_yyyyMMdd`，`directoryPath` 必须是当前应用同名根目录的一级子目录；`directoryNew=true` 且 clone 后目标目录不存在时创建 `.gitkeep`，以当前操作人身份提交并推送，使其它服务器和个人 worktree 能稳定取得目录。新前端表单默认传入工作空间别名 `ai-test`；后端创建和重命名时按去首尾空白后的精确字符串校验同一应用内唯一，旧客户端不传别名时仍按目录末段兜底。
- 同一 `应用 + repositoryId + version + branch` 只对应一个物理 feature 仓库，不同应用工作空间版本只是该仓库下的目录视图；发布或新目录提交后会把同组历史 `application_workspace_versions.target_commit_hash` 收敛到同一提交，并把同一本机物理仓库的 replica 提交元数据同步收敛。工作台“拉取远程”为个人级：在当前 owner 的整棵个人 worktree 显式 fetch 并原生 merge 远端分支，应用 workspace 和应用 Agent 遵循同一 Git 规则。普通本地改动若不会被覆盖则保留并继续拉取；Git 判定会覆盖时才返回精确阻塞文件，不 stash/reset。该动作不更新共享 target/replica、不广播、不扫描其他成员，也不改变既有 stage/commit/push 流程；远端差异包含应用 Agent/Skill/JSONC 且当前用户进程正在运行时，后端登记 `PERSONAL_APPLICATION` 单用户持久化重载，只等待发起用户空闲后 dispose；进程未运行时不建任务，下次启动直接读取磁盘。旧版本级拉取入口固定拒绝。任一目录发布后，本机会对该仓库组每个用户的去重物理个人 worktree 立即尝试合并同一 target；应用 Agent rollout 只对真正加载受影响目录 `.opencode` 的运行态 dispose，不会误重启兄弟目录。个人默认 worktree 同样共享仓库，但运行态根目录必须严格等于模板目录；历史记录指向仓库根或旧绝对路径时会在用户进入时修复，目标目录仍不存在则明确返回冲突，禁止继续把整个仓库冒充该工作空间。创建新目录占位提交前会先 `fetch` 并 `pull --ff-only`，防止复用中的旧副本生成非快进 push。
- 应用 Git 范围查询、全量刷新和单分支刷新共用 `repositoryId + version + branch` 分组及授权程序；`SUPER_ADMIN` 保持启用/停用应用全量能力，`APP_ADMIN` 只读取和刷新启用且自己仍为有效成员的应用，伪造 appId 返回 `FORBIDDEN`。范围查询只返回已经形成至少一个实际 feature 分支组的应用，不展示尚未创建版本分支的空应用。执行时使用当前操作者 SSH Key 逐组 fetch，并仅在远端可快进时更新共享 feature、组内 target/replica 元数据。单分支操作只处理精确命中的物理组，不影响同应用其它分支；全量操作中单组脏工作树、分叉或 Git 失败不会阻断其它组，响应完整返回 `UPDATED/UP_TO_DATE/FAILED` 汇总。成功组复用既有广播、个人 worktree 原生 merge 和应用 Agent rollout；非重叠个人修改保留，覆盖风险或真实冲突继续进入既有待处理/三方解决链路，不 stash/reset。该控制面能力不依赖发起管理员的 OpenCode 进程。
- 应用版本工作区根目录优先读取 `common_parameters.OPENCODE_APP_WORKSPACE_ROOT`，路径片段包含安全化版本号和代码库 `englishName`；历史代码库缺少英文名称时拒绝创建新的版本工作区。新建或显式修复的应用版本、服务器副本和托管运行态 `Workspace.rootPath` 入库保存 `appworkspace:<versionSegment>/<repositoryEnglishName>[/<templateDirectory>]` 逻辑路径；Git/文件/PTY/Run 执行前在服务端解析为当前服务器物理路径，对外 Workspace 响应只返回 `workspace:{workspaceId}`。内部部署模式版本库执行 clone/fetch/pull/push 前会按当前操作人统一认证号拼接 `ssh://{unifiedAuthId}@{gitUrl}` 并刷新 origin，公共配置“更新并推送”也必须在 fetch 前刷新共享仓库 origin，避免沿用上一位管理员的 SSH 用户；接管已有仓库时忽略 origin 中的 `ssh://任意用户@` 前缀后再比较数据库保存片段。公共仓库脏状态仍保持 `initialized=true/status=CONFLICT`，message 最多列出五个真实 Git 路径，不能把“文件待提交”误报成“目录未初始化”。旧 Unix/Windows 绝对路径只兼容服务端读取，不批量迁移。
- 基于应用版本工作区副本创建个人 git worktree，根目录优先读取 `common_parameters.OPENCODE_PERSONAL_WORKTREE_ROOT`，分支固定为 `{featureBranch}_{userId}_{workspaceName}`。OpenCode、编辑器和终端始终使用该个人 worktree；应用版本副本对所有角色普通文件只读。个人 worktree 新建和 default 显式确保/修复只允许在 API 已校验的用户 READY 进程服务器执行；复用落后于固定 target 的历史个人分支时直接执行原生 merge，不以仓库任意 dirty/staged/untracked 状态作为前置阻塞。非重叠改动原样保留，Git 判定会被覆盖时只返回精确文件，已形成的真实冲突保留 `MERGE_HEAD` 和三方 index 并允许目录存在的工作区进入 Diff；目录仍不存在时继续拒绝把仓库根冒充工作区。default 记录的运行态 `linuxServerId` 与当前服务器不一致时不能复用，即使路径碰巧存在也要按当前服务器修复归属，并由现有 Workspace mutation gate 失效旧会话上下文。普通文件范围是个人 workspace 根下除 `.opencode/**` 独立 Agent 作用域外的项目文件，包括根 README、`docs/**`、`archive/**`、源码、测试和部署文件；它们可以本地编辑、stage 和 commit。`spec/**` 也显示在普通 Diff，但对所有角色都只保留在个人分支，不参与远程发布，服务端按规范化路径拒绝混选和 `./spec` 别名，`SUPER_ADMIN` 也不能豁免。发布还要求所选路径已进入个人 `HEAD`、当前没有未完成 merge 且确认后的 feature HEAD 未漂移；前端“提交并推送”会先把全部选中文件提交到个人 HEAD，再将允许发布的非 spec 文件投影到 feature，个人分支不 push。push 后本机立即、其他服务器收到 `workspace.version.sync-requested` 后把同一固定 feature commit 反向 merge 到相关个人 worktree；干净或只有非重叠 dirty/staged/untracked 内容时保留本地状态并自动更新，其他用户无需主动拉取；只有 Git 判定会覆盖本地文件时才保持待同步，真实冲突直接进入现有 Diff/三方处理。普通文件同步不 dispose。用户本地提交、回退、显式重新进入 default 个人工作区或点击应用 Agent 根节点的更新按钮都会重试此前待同步 commit；该按钮以空 `files` 列表调用 `sync-from-application`，表示合并整个固定提交，再按进程状态刷新当前用户运行态。旧客户端传入非空路径时仅校验路径格式，不会缩小 Git merge 范围；`force` 不再提供覆盖语义。
- Git 提交姓名与平台展示名分离：平台 `username` 仍可因同名追加数字，提交优先读取独立 SCM 姓名证据。push 若收到企业右控固定的“邮箱对应姓名不一致”拒绝，服务端逐项核对统一认证邮箱和本次实际姓名后记录右控返回姓名，把最终文件树或当前 HEAD 以正确身份重建，并仅重试一次；不使用删除末尾数字等猜测规则。应用普通文件、应用 Agent/Skill、公共 Agent/Skill 和新增目录占位发布均复用该规则，再次失败继续走原回滚、远端事实确认和 rollout 中止流程。
- 最近使用工作区偏好分两套维度持久化：`user_global_workspace_preferences`（`app_id = NULL`，跨应用追踪「上次进入的应用 + 工作区」组合）和 `user_application_workspace_preferences`（`app_id = 非空`，按应用追踪）。`POST /workspaces/{workspaceId}/recent` 同时写两条，并在响应中通过 `resolveRecentWorkspaceResponse` 回填 `appId` / `versionId` / `applicationWorkspaceId`（与最近工作区接口共用同一反查链路：通过 `findVersionByRuntimeWorkspace` 找 `ApplicationWorkspaceVersion`，未命中再回退到 `findPersonalWorkspaceByRuntimeWorkspace` 取 appId；完全无主时三者均为 `null`）。`GET /recent-workspace` 在映射到托管应用时还会复核应用启用状态和当前成员关系，撤权后返回空但不删除偏好、个人工作区或物理 worktree；非托管兼容工作区仍可返回。`GET /applications/{appId}/recent-workspace` 继续通过显式成员校验。两者便于重新登录或换电脑登录时还原「应用 + 模板 + 版本」上下文；登录/切应用只在应用级 recent 带 `versionId` 且当前用户该版本已有 `workspaceName=default`、带运行态 workspaceId 的个人工作区记录时加载工作区，无历史、无 `versionId` 或无 default 记录时只选择应用并保留左侧工作区切换入口，不创建、不修复 default 私人 worktree。
- 托管应用成员校验失败时统一返回带加载上下文的 `FORBIDDEN`：message 显示应用、版本和工作区类型/名称/ID，`details` 只包含 `loadingStage`、`appId`、`appName`、`versionId`、`version`、`applicationWorkspaceId`、`workspaceKind`、`workspaceName`、`workspaceId`、`personalWorkspaceId` 等安全业务字段，便于排查“切换应用失败”时实际加载的是哪个应用、版本和工作区。
- 通过 domain 广播端口发布/消费 `workspace.version.sync-requested`，并通过本机补偿器扫描缺失或落后的副本。
- 与文件相关的 git 操作、差异比对、agent/skill 文件管理优先进入本模块。
- `ConversationWorkspaceGitApplicationService` 把公共 OpenCode Tool 的远端 session 映射到平台 Session 和 owner 个人 workspace，并复用既有 diff/pull/stage/unstage/discard/commit/publish/conflict 服务；Tool 不接收任意 workspace ID。`ManagedWorkspaceGitPathPolicy` 由 HTTP 按钮和对话 Tool 共同复用，统一保护完整 `.opencode/**` 命名空间。
- 工作区 Git Diff 即使带工作区 pathspec，也使用 `git status --porcelain --untracked-files=all` 展开未跟踪目录中的每个文件，使接口文件数、前端数量角标和实际文件数一致；unmerged 状态保留 `rawStatus` 并返回 `status=conflict`。响应还返回 `mergeInProgress/applicationUpdatePending/applicationTargetCommit`；待同步且尚未进入 merge 时，`applicationUpdateBlockingFiles` 额外列出整个个人仓库的 staged/unstaged/untracked 阻塞文件，并按同仓库目录视图标注工作空间，避免当前 pathspec 隐藏兄弟目录变更。`pendingPublishFiles/pendingPublishCommitMessage` 则表示本地 origin 跟踪提交到个人 HEAD 的已提交未发布状态，与当前 status/index 分开。普通文件通过真实 stage/unstage API 操作 index；冲突支持逐文件处理、全部采用个人/远程版本、取消 merge，并在全部解决后通过专用完成接口提交完整 merge index。个人发布要求允许发布的文件先在个人 worktree 本地提交，再投影到应用 feature worktree；应用 Agent/Skill 同样先进入个人 HEAD，再只投影 `.opencode/**` 白名单，成功后更新版本/副本 HEAD、广播并触发固定提交反向 merge。只有远端 push 完成才返回 `remotePushed=true`，响应和错误 details 会携带当前 Git 阶段与已执行命令；传入 `operationId` 时复用 Agent 配置进度端口。
- 所有平台用户触发的 Git commit 以及可能产生 commit 的 merge 都必须显式传入当前用户身份，并注入命令级作者/提交者；提交与发布程序不再提供缺省身份兼容入口，也不依赖公共仓库或全局 Git 配置中的默认身份。平台没有邮箱字段时由 common Git 工具按统一认证号生成企业 SCM 已登记的 `mails.icbc` email，避免 invalid committer 拒绝。公共发布额外使用 `commit-tree` 从最终文件树生成单父提交，远端只新增当前管理员身份的提交，不要求企业 SCM 接受个人分支中的旧历史身份。
- `checkVersionGitAccess()` 在用户显式选择应用版本、创建或修复个人 worktree 前复用 `GitRemoteService.listBranches()`、当前用户唯一 SSH key 和内部仓库有效 URL 做只读预检。应用成员关系每次实时校验；同一 JVM 只对“用户 + 版本库 + 有效 URL 摘要 + SSH key ID/指纹”的成功结果缓存 10 分钟并合并同键并发探测，最多保留 4096 项。URL 或 key 身份变化会立即重检，失败结果不缓存；认证失败/仓库不可访问返回稳定的权限申请结果，缺少 SSH key 单独返回配置提示，网络和超时继续抛统一 Git 异常。缓存键和日志均不保存私钥明文。
- `workspace-management.git-access-inspection` 每两小时分页扫描 ACTIVE 应用成员可见的启用测试工作空间，并复用上述只读预检刷新“用户 + 应用工作空间”权限投影；最多 8 个虚拟线程并发，单仓库失败不阻断本轮其它候选。巡检要求成功缓存不早于本轮开始时间，避免权限撤销后继续复用选择接口的历史成功结果；已执行的远端只读探测只要失败，包括 SSH key 缺失、认证/仓库拒绝、网络、SSL/TLS 和超时，均写为 `INACCESSIBLE` 并返回对应安全原因。只有未形成有效探测结论的内部故障写为 `UNKNOWN`。任务完成服务器扫描后只广播空载荷事件，由各 Java 检查自己实际持有连接的本地客户端。

- `AgentConfigApplicationService.supersedePublicConfigRollout` 只处理公共发布纠错的 Git 编排：校验共享运行副本恢复风险、使用当前超级管理员 SSH key 解析远端修正分支 commit，再把精确旧 rolloutId、修正 commit 和必填原因交给 `PublicAgentConfigRolloutCoordinator` 原子替换并广播新 rollout。该模块不直接更新 rollout 表、不接受前端自报强停目标，也不控制 manager。

- 自动化代码库禁止进入应用工作空间模板/版本创建路径；应用级配置只允许选择任意已有分支和远端目录树中的任意已有目录，不接受 `directoryNew`、日期版本、运行态 Workspace 或个人 worktree。

## Agent & Skill Hub

- Skill 目录由两类来源组成：公共配置 Git 内置 Skill 与应用成功 push 共同归入 `PLATFORM`，外部 `/list` 只同步元数据的 Skill 归入 `SKILLHUB`；其中 `createTime/createdAt` 作为用户提交申请时间持久化，不等同于平台首次发现时间，审批完成时间因上游未提供独立字段而不推断。公共内置 Skill 直接展示且无需发布。外部目录由 Redis 分布式锁保护的定时任务完整对账，下架只隐藏发现入口并保留当前应用已有引用。
- 外部 ZIP 仅在预览、引用或更新时按需下载；应用服务把当前认证主体或文件 ticket 中的统一认证号作为新版文档必填 `userId` 交给 SkillHub，不接受浏览器自报身份。下载后复用既有内容寻址制品编码，并校验 20 MiB/256 文件、路径、重复项、UTF-8 和稳定 `name`；可将唯一外层目录及大小写不同的清单文件安全归一化为根 `SKILL.md`，但多清单或多根目录仍拒绝。外部 `name` 按 SkillHub 原值保存并作为技术 ID，只允许大小写字母、数字、点、下划线和短横线，首字符必须是字母或数字；不再额外限制为小写短横线格式。相同外部 ID+版本不同摘要拒绝覆盖。
- 外部 Skill 写入管理员个人 worktree 后，push 摘要未变则仍是外部引用并排除重复平台卡片；发生编辑则同一事务自动转为记录 `forkedFrom*` 的平台派生资产，初始为已推送未发布，仍需管理员显式发布。
- 超级管理员可通过平台 HTTP 入口显式调用 SkillHub `/upload`，必须同时提供 ZIP 和安全审查报告、目录结构、运行效果三张图片；应用服务校验阶段枚举、20 MiB ZIP、三个 5 MiB 图片、ZIP 安全路径及根 `SKILL.md`，并从当前认证主体补入新版文档必填的统一认证号 `userId`，前端不能覆盖。上传返回上游 `result` taskId，进度查询返回上游 `result.progress/message`；完成后由下一轮目录对账或手工 `/external/sync` 纳入能力库。应用 Git push 不自动上传 SkillHub，因为该流程没有三张必填图片。
- `AgentSkillHubApplicationService` 在应用 feature push 成功后从精确 Git commit 扫描 `.opencode/agents/*.md` 与完整 `.opencode/skills/{id}/**`，按同一物理仓库组逐工作空间目录生成不可变压缩快照；平台外部 push 由具备该应用刷新权限的管理员通过应用 Git 刷新发现，远端提交同步完成后立即执行同一组索引。Hub 不依赖用户个人 worktree 拉取，定时对账本机 READY 副本仅用于补偿漏记。
- push 与 publish 分离：全员可浏览 pushed 快照，显式发布固定当前修订和精确依赖；公共配置仓库只以平台内置只读资产展示。`reconcilePublicBuiltinSnapshots()` 默认启动 2 秒后、此后每 10 分钟用共享仓库现有 Git 身份 fetch 当前分支、读取 `origin/{branch}` 的精确提交并把元数据和内容寻址制品写入数据库，因此用户从其它本地 clone 直接 push 后无需打开 Hub 即可入库；任务只刷新远端引用，不 checkout/reset 运行工作树，认证暂不可用时回退已由公共 rollout 同步的本地 HEAD。Hub 列表、详情和正文查询不再读取 Git。
- Skill 目录持久化受控事项分类：一级固定为 `WORKER/TEST/CODE/OTHER`，二级固定为测试设计、测试数据构造、测试执行、测试分析和白盒分析。历史、新 push 与公共 Git Skill 默认 `OTHER`；应用 Skill 分类保存在资产表，公共 Skill 分类保存在独立逻辑资产表，后续 push/commit 都不覆盖超级管理员已经设置的分类。列表分类筛选同时覆盖两类内容。
- 引用递归物化已发布依赖到当前管理员个人 worktree，不自动 commit/push；`PENDING_PUSH` 只表示本地待推送，feature push 内容摘要吻合后才提升为 `ACTIVE`。取消引用先安全移除 worktree 文件并进入 `PENDING_REMOVE`，立即退出当前应用和消费者视图，push 确认远端路径消失后才正式删除引用记录；确认前重新引用会原位恢复该记录并重新物化最新发布修订。
- 更新使用 active/current/incoming 三方合并。任何冲突都会先持久化操作且保持工作树不变；全部解决后做 current 摘要乐观校验，再以文件备份和数据库事务收敛落盘。目录、详情和更新查询可绑定当前个人运行工作区；`referencedOnly` 提供当前应用可用引用资产库并排除 `PENDING_REMOVE`，详情返回已生效引用方，并仅向目标应用成员补充待推送引用方。

## 测试覆盖

- `WorkspaceApplicationServiceTest` 覆盖工作区创建、服务器归属、分页/详情查询、未找到错误和文件服务编排；API 映射测试覆盖普通、最近、排查和分享响应只暴露工作区逻辑定位符。
- `WorkspaceFileServiceTest` 覆盖 UTF-8 读写、受控二进制附件覆盖/容量/越界、跨多字节字符边界的完整渐进预览、预览期间文件变化栅栏、分片上传超过一次性读取阈值、上传分片顺序/声明大小/取消与临时文件清理、旧 Base64 上传兼容、普通文件与非空目录整体移动、同路径幂等、根/后代/符号链接/特殊文件/越界拒绝、符号链接父目录展示、搜索跳过和外部目录或 `.git` 别名防跟随、校验后工作区根祖先或目标父目录替换失败关闭、目标并发创建不覆盖、普通文件和目录同目录重命名、普通文件/目录树删除、工作区根与 `.git` 删除拒绝、目录列表排序与上限、相对路径/空关键字文件搜索、一次性读取阈值和 null 内容写入。
- `WorkspaceDirectoryServiceTest` 覆盖服务器工作空间选择器的默认目录、只返回子目录、排序、父目录、条目上限和缺失目录错误码。
- `GitPublishWorkflowTest` 覆盖直接发布、worktree 合并发布、冲突文件收集、merge abort、abort 失败保护，以及同步文件时先 clean/pull 再复制提交推送。
- `ManagedWorkspaceApplicationServiceTest` 覆盖应用成员校验及 `FORBIDDEN` 加载上下文、托管逻辑路径、个人 worktree 创建、Git diff、本地 origin 跟踪提交到 HEAD 的存量已提交未推送恢复、个人拉取 Agent 差异只登记当前用户 `PERSONAL_APPLICATION` 重载及进程未运行返回、个人 worktree 本地提交、体验 workspace 的 stage/commit/discard 且无 push、从个人 `HEAD` 按白名单投影并推送 feature、所有角色 spec 禁推、应用 Agent 发布后的版本 HEAD 更新、管理员远端 Git 刷新后的 Hub 索引、同仓库组逐目录 Hub 快照、feature 固定提交反向 merge、非重叠 dirty 改动继续合并并 dispose、Git 原生覆盖保护进入持久化 retry、同仓库兄弟目录的物理 worktree/replica 同步但不误 dispose、单仓库单工作空间兼容、真实冲突进入 Diff 并通过专用接口完成，以及应用副本只读 Git 操作及失败阶段命令透传。`GitWorkspaceServiceRealGitTest` 使用真实临时 Git 仓库验证体验仓库幂等初始化、无 remote、指定路径提交隔离，以及固定提交 merge、非重叠本地修改保留、覆盖阻塞文件识别、三方冲突、解决和提交完成。`tools/verify-experience-workspace-content.sh` 额外验证部署模板基线提交、缺失文件补齐、用户内容/index 保留和重复执行幂等性。
- `PersonalWorkspaceRelocationWorkerTest` 覆盖本机发现/认领/传输/清理、快照冲突退避和 `CLEANUP_PENDING` 只重试旧源清理；`PersonalWorkspaceRelocationTaskHandlerTest` 覆盖每分钟任务键、广播和本机执行；`PersonalWorkspaceSnapshotServiceRealGitTest` 使用真实 Git 公共副本验证仅存在于源端的个人提交以及 staged、unstaged、普通 untracked 和 ignored 文件在目标保持一致，且快照过程不修改源工作树；同时覆盖 Git 子模块和跟踪符号链接路径的失败保护。
- `WorkspaceGitAccessInspectionServiceTest` / `WorkspaceGitAccessInspectionTaskHandlerTest` 覆盖服务器工作空间的成功、明确拒绝、网络未知分类，以及两小时 Cron、脱敏计数和本地客户端广播。
- `AgentConfigApplicationServiceTest` 覆盖公共仓库初始化/更新、锁前不修改工作树、共享副本明确确认后恢复和定点清理、全服务器固定 commit、公共个人 worktree 冲突不阻塞主同步、clean worktree 的待发布提交识别、当前用户长期公共 worktree 的稳定命名与复用、历史非稳定命名 worktree 不再进入发布补偿且存量任务会安全终止、进程初始化后的同服幂等准备及仓库未初始化降级、缺失 worktree 定时补偿的默认周期/Redis 锁/本地无凭据创建、按服务器和创建人过滤、跨用户操作拒绝、公共/应用配置文件回退路径映射、Agent/Skill 双语展示名解析与历史标题回退、公共最终文件树生成干净线性提交后以 refspec 推送、冲突保留在个人 worktree、共享运行时副本同步与广播、纠错入口先解析远端 commit 再原子替换并广播新 rollout、push 成功后请求线程不认领本机同步且持久化补偿仍可执行，以及工作空间级 `.opencode/**` 配置读写、文件/目录删除、任意 Git 可见子路径 diff 和普通工作区路径排除。`GitWorkspaceServiceRealGitTest` 使用真实临时 Git 仓库验证污染个人历史不会成为发布提交祖先，并验证固定 commit 原生 merge 时非重叠 staged、unstaged、untracked 内容保持原状态。

- `ReferenceRepositoryApplicationServiceTest`、`ReferenceRepositoryRealGitSafetyTest`、`ReferenceRepositoryReplicaTaskDispatcherTest`、`ReferenceRepositoryReplicaReconcilerTest` 覆盖分支一次性初始化、generation/CAS、活动代次终止/跨 Java 取消、本机等待任务取消与运行线程中断、本机即时/广播异步唤醒、有界并发与去重、按时退避、队列拒绝、租约丢失、离线 `DEFERRED`/恢复、本机文件锁、临时 clone + 原子移动、已对齐 HEAD 无操作快速路径、已有仓库脏状态/origin/分支/分叉保护、树路径穿越/`.git`/符号链接/1000 项上限和补偿器生命周期。
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

通用文件安全实现已经提取到 `test-agent-workspace-filesystem`，本模块依赖并复用该内核，不再拥有第二套
路径/符号链接/原子移动实现。本地 Workspace 的根注册和反向 RPC 编排属于 runtime/client；本模块只在
Workspace 查询响应中投影 runtime kind、实例 ID、在线状态和 capability。注销本地 Workspace 绝不删除
用户磁盘目录。用户主动撤销 Client key 后，普通用户工作区列表与详情通过既有凭据状态隐藏全部本地
Workspace；平台绑定和本地目录保留，凭据重新启用后恢复展示。

`AgentSkillHubApplicationService` 同时实现受保护运行的只读定义端口：目录只返回用户可见的公共内置或
已发布应用 Agent 的不可变修订 ID、名称和 SHA-256；运行解析只接受当前已发布修订，并按依赖表冻结精确
Skill 修订。公共内置 Agent 没有发布依赖表，因此只从同一公共 Git commit 的数据库快照中选择 `AGENT.md`
按完整技术 ID 明确引用的 Skill；禁止读取当前工作树或跨 commit 拼接正文。制品只在服务器解压，Agent
文本进入服务器 system prompt，Skill 文本只通过服务器只读资源按需加载，二进制附件仅由制品摘要
审计。该端口不得把 `AGENT.md`、`SKILL.md` 或其它正文放入列表 DTO 或本地客户端配置目录。

## 公共客户端能力包

公共 Git 正式发布复用 `PublicClientCapabilityPackageService` 从共享副本已 checkout 的精确 commit 生成客户端完整
能力包。`PublicClientCapabilityPackageBuilder` 只导出 `opencode/agents|skills|tools` 白名单，排除 AGENTS.md、
`opencode.jsonc`、密钥、地址、Git、缓存和原始 `node_modules`；Tool 静态 import 必须能在平台锁文件及离线
node_modules 中递归解析为纯 JS/WASM。构建不运行 npm。兼容失败持久化 `SERVER_ONLY` 并允许服务器 rollout 继续，
持久化异常只记录安全日志，不能把服务器发布伪报失败。Tool 的 Python companion 文件虽然不计入 OpenCode Tool 数量，
仍作为文本执行同一密钥和固定地址检查，不能借非 JS/TS 后缀绕过能力包敏感内容门禁。

manifest 同时记录文件级 `contentDigest` 和提交绑定的 `bundleDigest`；后者按
`sha256(sourceCommit + "\n" + contentDigest)` 计算。即使两个公共 commit 的 Agent/Skill/Tool 文件完全一致，也会
保留两个可追溯版本，不会因数据库摘要唯一约束让新 commit 的客户端兼容状态变成空值；内容未变时变更摘要仍全部为
`false`，不会要求重启本地 OpenCode。

`PublicClientCapabilityBootstrapReconciler` 处理功能上线前已经存在、但尚无能力包记录的公共 Git HEAD：启动后只读
当前已检出的共享副本，不 fetch、不提交、不推送，在 Redis 全局锁内为该 commit 补建首个完整包。后续扫描命中
同一 commit 时幂等跳过，因此客户端安装基线和平台“当前公共版本”不会停留在空值。

`PublicClientCapabilityPackageBuilderTest` 覆盖确定性完整包、提交绑定版本身份、白名单/JS/TS/Python 敏感文件排除、未声明依赖和原生扩展拒绝；
`PublicClientCapabilityBootstrapReconcilerTest` 覆盖历史 HEAD 首次补建与未配置跳过；发布测试还必须验证首次版本、
无变化版本、Agent/Skill 热加载摘要与 Tool/依赖重启摘要。

## 个人工作区搬迁失败诊断

`PersonalWorkspaceRelocationDiagnostics` 统一过滤原因白名单和阶段枚举；Worker 为每次认领保存调用栈内进度，Snapshot 细分 Git 捕获、未跟踪扫描、bundle、归档及末次校验。重试日志保留 `errorCode/errorType` 并新增 `stage/reason/pathRef/filePath/traceId/causeType`，既有 `safe_error_message` 保存阶段、原因、路径指纹及固定中文提示。禁止记录异常原文、任意 details、stderr、文件内容或链接目标。

`pathRef` 为仓库相对路径（Git 返回的 `/` 分隔格式、UTF-8、无末尾换行）的 SHA-256；没有单文件定位时为 `NONE`。仅符号链接/特殊条目拒绝和单文件归档内容变化附带此指纹；全量快照前后不一致不伪造具体文件。未知原因用 `UNCLASSIFIED`，结合日志原因类型排查。错误码和重试/清理状态机不变，无新 API、事件或 schema。

定向测试：`mvn -f backend/pom.xml -pl test-agent-workspace-management -am "-Dtest=PersonalWorkspaceRelocationWorkerTest,PersonalWorkspaceRelocationDiagnosticsTest,PersonalWorkspaceSnapshotDiagnosticsTest,PersonalWorkspaceSnapshotServiceRealGitTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`。

## 对话代码知识源码基线

应用源码物化在删除 staging 的 `.git`、写入权威索引之后，从同一份已校验内容复制一份 generation
专属知识基线，再分别原子发布知识基线和可编辑 `APP_SOURCE` 目录。知识基线位于同一受控根的隐藏同级目录，
不再次 clone，也不作为 Workspace 暴露；发布完成后移除写权限。可编辑目录发布或数据库 READY 回写失败时，
物化器同步恢复上一份知识基线，避免磁盘证据与副本状态分叉。

`CodeSourceQueryService` 是 Agent 查询源码的唯一业务入口。每次 `list/search/read` 都重新校验版本库类型、
当前 ACTIVE 且未过期的 snapshot、slot generation、本机 READY replica、ACTIVE Runtime Workspace、现有应用
成员权限、逻辑根和权威索引；请求结束前再做一次完整校验。源码路径只接受仓库内相对路径，逐段拒绝符号链接
和平台索引。搜索限制深度、文件数、单文件大小、时长和返回数；读取最多 400 行并返回完整文件 SHA-256。
文件或目录在读取期间发生变化时返回 `SOURCE_CHANGED_DURING_READ`，撤权、过期或 generation 切换不返回旧结果。

旧 generation 没有知识基线时，context 返回原 snapshot 的 commit、generation、已选目录、
`available=false` 和 `OPEN_APP_SOURCE_PREPARATION`，实际读取仍返回 `SOURCE_BASELINE_UNAVAILABLE`；必须由现有源码准备流程建立新 generation；
禁止把用户已经修改的 `APP_SOURCE` 目录追认为固定提交原文。清理任务只删除自身 generation 的知识基线及其
staging/backup，不能删除更新 generation。定向回归由 `AppSourceGitMaterializerTest`、
`AppSourceCleanupWorkerTest` 和 `CodeSourceQueryServiceTest` 覆盖。
