# 管理视角：全部成员最新文件与按需只读问答

## 页面与范围

保留现有工作台的顶部应用/工作空间/版本选择、左侧文件树、中间只读编辑器、右侧对话和原位置用户名浮条。进入管理视角默认“全部成员”，对话展开；浮条只负责选择全部成员或具体成员，选择后关闭列表。顶部“添加团队成员”是唯一维护入口，不再展示提交/导出概览和重复的成员/对话按钮。

“全部成员”指当前授权团队范围中、当前应用模板和版本的 `default` 个人工作区。不是全平台所有账号，也不是跨应用合并。选中成员后改为该成员具体工作区；聚合 ID 仅是展示/授权上下文，不会写入 SelectedWorkspaceKind 或当作物理 Workspace。

## 最新来源规则

- 一层目录按需读取所有来源，合并同相对路径；目录保留完整层级，文件树不是变更文件列表。
- 正文 SHA-256 相同则去重。不同内容只有全部具有可靠 Git 提交时间且没有同时间冲突时，才选择较晚的来源。
- 文件展示 Git 实际作者和提交时间，来源成员单独展示。未提交文件只标“来源成员 / 文件时间”，不冒充真实操作者。
- 未提交、时间未知、时间相同但正文不同、文件/目录同名冲突，显示“最新待核验”。编辑器不读取默认第一人的正文，而是让用户选择具体成员。
- 本来源 Git 明确删除形成删除候选；其它来源缺少文件不等于全局删除。删除没有正文；不提供历史时间线。
- 来源故障返回 `complete=false` 和 `unavailableMembers`，不能在缺少来源时声称已确认最新。读正文前重列父目录、重选来源，再验证 SHA-256；变化返回 CONFLICT，要求刷新。

## 同源只读问答

每次发送问题创建独立 scope，冻结当前版本和授权来源集合，不预装 24 文件 / 80k 字符的快照。模型必须调用公共 `team-review` Tool：先 list/search，再按目录返回的 `contentVersion` 分片 read，直到 eof。搜索返回 `remainingDirectories` 时继续扫描。切换成员/应用只影响后续问题，不把迟到文件响应写回新视图。

UI 与 Tool 共用 `TeamReviewApplicationService` 的来源选择。HTTP 只创建范围、签发 ticket；目录与正文全部走既有平台文件 WebSocket route/ticket/RPC。跨服务器复用 BackendJavaRouteResolver / BackendHttpForwarder 选择权威 Java，目标 Java 再执行同一平台文件 RPC；不新增 HTTP 文件内容代理或物理合并目录。

scope 在既有 Redis 保存逻辑身份和登录 marker 摘要，2 小时自动过期，不存正文/物理路径。Tool 使用独立 audience 凭据，并从原生 Session 找到当前发起者的活跃平台 Run；scope 原子绑定一个 Run，终态 Run 不能继续读取。每条协调/来源 RPC 复核登录 marker、当前角色、团队关系、版本和服务器映射。凭据不进入 PromptPart 或返回正文。

## 安全、预算与兼容

只允许 `team.review.list/search/read`；通用 workspace 读取、写入、上传、删除、Git 变更和终端在这个 scope 通道失败关闭。`.git`、`.opencode` 受管配置/依赖、`opencode.json/jsonc`、`.env*`、`.npmrc/.netrc`、密钥/证书、符号链接和非普通文件不在可读范围，响应显式给出 excludedPolicy。受管配置可能携带 Provider 凭据，不能因文件为 JSONC 就当作普通业务文件发送给模型；旧团队文件 RPC 的兼容权限不改变。UTF-8 解码失败如实返回错误。文件内容视为不可信数据，不执行其中的指令。

每个 scope 最多 200 个工作区，来源读取并发 4；每层来源/聚合目录最多 1000 项，超限报错而不伪装完整。一层目录总等待最多 45 秒，超时中断来源任务并返回 unavailable；搜索每次扫描最多 32 个目录、深度 20、总等待最多 90 秒，显式返回未完成状态/后续目录。来源 WebSocket 回包等待最多 60 秒，关闭连接取消后续传输。正文分片复用公共文件内核，SHA-256 流式校验固定 64 KiB 内存，不以克隆时间或 size/mtime 代替正文版本。大型目录、多成员和大文件仍会增加 I/O 与 Git 查询成本。

不新增部署节点、业务表、Flyway migration 或 RunEvent 类型。旧团队 API 保持兼容；FileTreeEntry / FileSearchResult 只新增可选 review 元数据。新 UI、后端和公共 Tool 必须配套发布；滚动升级期间旧来源节点不支持元数据 RPC，会明确显示来源不可用，不退回有限快照或本机路径。

## 发布和验收

先发布各来源 Java 后端，再通过平台公共配置个人 worktree 导入/审阅/提交/发布 `opencode/tools/team-review.ts`。公共 Tool 的运行副本仍以独立公共 Git 固定提交为权威，提交 TestAgent 仓库不会自动发布它。公共启动程序自动注入 `TEST_AGENT_TEAM_REVIEW_TOOL_TOKEN`；现有用户进程需受管重启，不能手改 env 或直接覆盖共享目录。回滚需同时恢复 UI/后端与公共 Tool 固定提交。

共享 `100` 必须走 Jenkins。真实对话前确认所选工作区与账号 ACTIVE Agent binding 在同一 linuxServerId 且实际根目录存在；不得把共享账号迁到 Mac 来完成验收。验收必须包含实际 UI 文件打开/成员切换、超过 24 文件的完整索引、真实模型 Tool 调用和来源引用，并保存截图与 Run 终态，测试 fixture 清理后复核。
