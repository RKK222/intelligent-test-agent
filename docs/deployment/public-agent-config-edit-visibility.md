# 公共 Agent Skill 改动"已生效但看不到落盘"的原因与排查

本文解释一个企业环境下的现象：超级管理员在网页对话里让 Agent 更新了公共级 Skill，**技能当场生效**，
但在公共配置树、Git 变更面板或服务器文件系统上**看不到这次改动落到文件上**，也无法提交。

结论：这不是文件没被写入，而是**写入落点与平台文件树的读取落点不是同一份目录**。
用户进程默认读取服务器共享运行副本，而超级管理员的前端公共配置树读取的是本人的公共个人 worktree。

## 0. 结论速览

| 现象 | 原因 | 决定性证据 | 处置 |
|---|---|---|---|
| 技能立刻生效 | 对话改的是进程 `OPENCODE_CONFIG_DIR` 指向的真实目录，改完落盘即被进程读到 | `opencode_server_processes.config_path` + `readlink -f` | 无需处置 |
| 公共配置树里看不到 | 写入落在共享运行副本，而超级管理员的树根是本人公共 worktree | 上述软链解析到 `{PUBLIC_CONFIG_GIT_ROOT}/opencode` | 在平台通道重做一次并发布，见 §5 |
| 普通用户反而看得见 | 普通用户没有公共个人 worktree，全部回退读共享运行副本 | `agent_config_worktrees` 无该用户 PUBLIC 行 | 同上 |
| 改动迟早消失 | 共享运行副本只用于初始化与运行时同步，下次公共刷新/发布会 reset 掉未提交内容 | 公共刷新前会要求确认放弃共享副本修改 | 必须先备份文本 |

## 1. 适用范围

- 目标环境：企业内部已部署环境（本机 `test` 或本地开发环境不在本文范围）。
- 角色：以超级管理员复现；普通用户与应用管理员对公共级只有只读权限。
- 前提：`OPENCODE_PUBLIC_AGENT_GIT_URL` 已配置（非 `UNCONFIGURED`），该服务器已初始化公共配置仓库。

## 2. 机制：一个用户进程到底能读到哪些 Skill

### 2.1 进程环境（全部由 platform 派生，调用方不可覆盖）

| 变量 | 取值 | 来源 |
|---|---|---|
| `HOME` | `{OPENCODE_SESSION_DIR}/users/{统一认证号}` | `opencode-manager/internal/process/process.go:299` |
| `XDG_DATA_HOME` | 同上 | `:300` |
| `XDG_CACHE_HOME` | `{session}/.cache` | `:301` |
| `XDG_STATE_HOME` | `{session}/.local/state` | `:302` |
| `OPENCODE_CONFIG_DIR` | `{session}/.testagent-runtime/current-public-config`（受管软链） | `:304`、`UserOpencodeProcessAssignmentService:977` |

`sessionPath` 由 `{OPENCODE_SESSION_DIR}/users/{统一认证号}` 拼出（`UserOpencodeProcessAssignmentService:945`）。
企业 Linux 默认 `OPENCODE_SESSION_DIR=/data/.testagent/agent-opencode/.session/`，实际值以 `common_parameters` 为准。

### 2.2 受管软链的目标：默认指向共享运行副本

`configPath()` 的注释即产品语义："每个用户进程固定读取 session 下的 Git 外软链接；**默认指向共享公共目录**，
公共个人 worktree 保存时只切换本人链接，不改动其它进程。"

软链切到"本人公共个人 worktree"只有三个触发时机：

1. `POST /processes/me/initialize`：`SUPER_ADMIN` 进程健康检查成功后，幂等准备并自动加载本人公共个人 worktree。
2. `POST /public/runtime-reload`：即对话页小宠物的"Agent 配置更新"。
3. 平台受管启动/重启：同服存在 ACTIVE 且稳定命名的 `public-{userId}` worktree 时直接加载，失败回退共享配置。

平台受管启动只做一次同服查询与目录边界校验，**不做后台轮询、不 fetch**；因此"曾经初始化过"不等于"现在指向个人 worktree"。

### 2.3 四个可能的落点

| 落点 | 物理路径 | 谁在前端读它 | Git 可见 | 生效范围 | 持久性 |
|---|---|---|---|---|---|
| **A 本人公共个人 worktree** | `{OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/public-{userId}/opencode` | 超级管理员：公共配置树传 `worktreeId` 读它 | 是 | 仅本人 | 随发布持久 |
| **B 服务器共享运行副本** | `{OPENCODE_PUBLIC_CONFIG_DIR}`（默认 `{PUBLIC_CONFIG_GIT_ROOT}/opencode`） | 无 worktree 的用户读它；超管的树**不读** | 是，但属受控同步区 | 该服务器上所有回退用户 | 下次刷新被 reset |
| **C 会话 HOME 内技能目录** | `{session}/.claude/skills`、`{session}/.agents/skills`、`{session}/.config/opencode/{skill,skills}`、`{session}/.opencode/{skill,skills}` | 都不读 | 否 | 仅本进程 | 目录不随重启清理，但换服务器即丢 |
| **D 远端技能 URL 缓存** | `{session}/.cache/opencode/skills` | 都不读 | 否 | 仅本人 | 缓存，非受管 |

A/B 是平台资产，C/D 是 opencode 原生扫描根。四个落点的扫描与优先级见
`opencode-source/opencode-1.18.4/packages/opencode/src/skill/index.ts:185-227` 与 `config/paths.ts:23-36`：
扫描顺序为 `Global.Path.config` → 工作区上溯 `.opencode` → `{HOME}/.opencode` → `OPENCODE_CONFIG_DIR`，
**公共配置目录最后扫描，同名 Skill 会覆盖前面的**。

### 2.4 前端公共树根是动态的

`AgentConfigPanel.vue:367` 对 `scope === "PUBLIC"` 传的是 `publicWorktree.value?.worktreeId`；
后端 `AgentConfigApplicationService.publicAgentRootForRead`（`:2644-2654`）在**传了 worktreeId 时读 A，
不传时读 `gitRoot`（即 B）**。写入路径 `publicAgentRootForWrite`（`:2656`）则一律要求本人 worktree。

## 3. 原因分析

### 3.1 为什么"立即生效"

对话里 Agent 用文件写入工具直接落盘，写的就是软链解析后的真实目录；进程随后读同一份文件，因此立刻生效。
这条路径**绕过了平台文件 WebSocket 与角色校验**，平台侧没有写入记录、没有审计，也不产生任何 Git 变更条目。

### 3.2 为什么"看不到落到文件上"（主因）

超级管理员的公共配置树在读 A，而进程软链默认指向 B。写入落在 B 时：

- 树里打开同名 `skills/<技能名>/SKILL.md` 仍是旧内容（读的是 A）；
- "变更 → 公共Agent"没有条目（用 A 的 Git 根做 diff）；
- 平台也没有任何入口能把 B 的改动提交出去 —— 产品定死 B"只用于初始化和运行时同步，**不接受前端直接写入**"
  （`docs/api/http-api.md`「Agent 配置管理 API」鉴权段）。

### 3.3 为什么别人看得见、别的服务器不一致

- 创建公共个人 worktree 与补偿任务都要求 `SUPER_ADMIN`（`AgentConfigController` 对 `/public/worktrees*`、
  `/public/worktrees/reconcile` 均 `requireRole(SUPER_ADMIN)`），**普通用户不存在个人 worktree**，
  因此普通用户全部回退读 B —— 他们看到的就是改后的新内容，只有超管自己看不到。
- B 是**每台 Java 各一份**的本地 clone。改动只在写入的那台服务器生效，其它服务器上的用户从未看到这次变化，
  表现为"同一个 Skill 在不同服务器行为不一致"。

### 3.4 为什么改动迟早会消失

B 的修改不进任何发布链路。下一次公共刷新（`POST /public/update`）会先把共享运行副本恢复并清理到目标 commit，
dirty 未确认时直接 409（`discardLocalChangesAllowed=true`）。在 B 上手工 `git commit` 同样保不住：
该目录会被 checkout/reset 到远端目标 commit。

## 4. 排查思路

全程只读；数据库查询在 DBeaver 执行，仅 `SELECT`。

### Step 1 拿用户与进程事实

```sql
select user_id, unified_auth_id, username, status
from users where unified_auth_id = '<统一认证号>';

select p.process_id, p.linux_server_id, p.container_id, p.port, p.pid, p.status,
       p.session_path, p.config_path, p.started_at
from user_opencode_process_bindings b
join opencode_server_processes p on p.process_id = b.process_id
where b.user_id = '<user_id>' and b.agent_id = 'opencode';
```

`agent_id` 固定为 `opencode`。`config_path` 是 `.testagent-runtime/current-public-config` 说明使用新受管软链模型
（还需 `readlink` 才知道指向 A 还是 B）；若直接等于公共配置目录，则是升级前的旧模型，直接读 B。

### Step 2 确认现场实际路径参数

```sql
select parameter_english, platform, parameter_value
from common_parameters
where parameter_english in ('OPENCODE_PUBLIC_AGENT_GIT_URL', 'OPENCODE_PUBLIC_CONFIG_GIT_ROOT',
 'OPENCODE_PUBLIC_CONFIG_DIR', 'OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT', 'OPENCODE_SESSION_DIR')
order by parameter_english, platform;
```

不要沿用默认值判断，现场可能被改过。

### Step 3 在该服务器上解析软链（决定性）

```bash
readlink -f {session_path}/.testagent-runtime/current-public-config
```

- 结果落在 `{OPENCODE_PUBLIC_CONFIG_DIR}` → 主因成立，进 Step 5；
- 结果落在 `{OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/public-{userId}/opencode` → 进 Step 6 排除清单。

### Step 4 在四个落点里找出这个 Skill 的实际文件

```bash
grep -rl "<技能名或目录名>" \
  {OPENCODE_PUBLIC_CONFIG_DIR}/skills \
  {OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/*/opencode/skills \
  {session_path} \
  2>/dev/null
```

### Step 5 看每份的 Git 状态，判定处置

```bash
git -C {OPENCODE_PUBLIC_CONFIG_DIR} status --porcelain        # B
git -C {OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/public-{userId} status --porcelain   # A
git -C {OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/public-{userId} log --oneline -3
```

| 命中 | Git 状态 | 判定 | 处置 |
|---|---|---|---|
| A | ` M` / `??` | 只是没提交 | 直接走 §5.2 提交发布 |
| A | 干净 | 已 commit 未 push | 看 `/public/diff` 的 `publishPending`，重新推送 |
| B | 任意 | 落点错，且会被 reset | 备份文本后在平台重做，见 §5.1 |
| C / D | — | 平台里根本不存在这份改动 | 只能在平台通道重做 |

### Step 6 若软链已指向 A 的排除清单

此时改动应在 A，前端树本应可见，按顺序排除：

1. 前端未刷新：重开目录、看"变更"面板是否已刷新（有写权限时面板可见期间会周期核验）。
2. 树选错：确认看的是"公共配置"树而不是"应用配置"树（应用级根是 `{workspace.rootPath}/.opencode/`）。
3. 目录名不是中文名：Skill 目录用英文技术标识（中文名会被转无声调拼音），例如"接口自动化测试"→
   `skills/jie-kou-zi-dong-hua-ce-shi/`。
4. 位置找错：Agent/Skill 资产出现在 Hub 里不等于出现在配置树；Hub 的"上传 Skill"是外部能力材料，不写个人 worktree。

### Step 7 确认上次发布是否真的完成

```sql
select rollout_id, status, commit_hash, failure_reason, created_at, completed_at
from public_agent_config_rollouts order by created_at desc limit 1;

select status, count(*) from public_agent_config_rollout_targets
where rollout_id = '<rollout_id>' group by status;
```

`DRAINING` 表示仍在排空（该范围用户被禁发新消息），`COMPLETED` 才算全量收敛；
卡住的目标可从 rollout 页面的 `pendingTargets` 定位用户，并复用运行管理接口停止或受管重启。

## 5. 修改点与提交动作

### 5.1 正确的修改落点

按优先级：

1. **直接在平台"公共配置"树里改**（推荐）：`skills/<技能名>/SKILL.md`，内容照抄 B 里的那一份。
   技能不存在时用"新建或上传公共配置"按模板创建。
2. **想保留"对话里改"的习惯**：先在公共树"更多操作 → 创建公共 worktree"，再点对话页小宠物的"Agent 配置更新"
   把本人软链切到 A；此后对话里的改动才会出现在树里并可提交。
3. **不要**把业务工作区或 C/D 目录当成第二套来源，也不要复制公共配置到业务工作区。

### 5.2 提交并发布

1. 前置：`SUPER_ADMIN`；`OPENCODE_PUBLIC_AGENT_GIT_URL` 已配置；目标服务器已初始化公共配置仓库
   （系统管理 → 配置管理 → TestAgent 公共配置管理）；本人公共 worktree 存在。
2. 暂存：目标 Skill 的**一级目录**行尾 Git 图标"提交并推送"（普通文件为文件行尾）；或
   "变更 → 公共Agent"Tab 逐项暂存。快捷入口只处理这一个提交单元，存在冲突、待重新推送或同作用域已暂存内容时会拒绝。
3. 提交并推送：填写提交信息后走平台发布链路。
   `POST /public/publish` 或 `/public/update-and-push`：fetch → merge `origin/{公共分支}` →
   投影为以远端当前提交为唯一父节点的线性提交 → push 前先落持久化禁发任务（`DRAINING` 硬闸门）→ push。
4. 同步与排空（平台异步完成）：各服务器把共享运行副本 checkout/reset 到目标 commit，个人 worktree 执行
   `git merge --no-edit <targetCommit>`（不 stash、不 reset）；旧实例逐个 dispose，若涉及
   `.opencode/tool[s]/**/*.js|ts` 则走受管重启。
5. 校验：rollout 终态 `COMPLETED`；必要时在目标服务器上确认 A 与 B 都是目标 commit，且树里新内容可见。

### 5.3 红线

- **保存、暂存、本地 commit 都不等于发布**；只有 push 成功且 rollout 收敛才对其他用户生效。
- **不要**在共享运行副本 B 上手工 `git commit` / `push`，也**不要**手工改 B：会被平台 reset 掉，
  或造成 `AWAITING_USER` 补偿与跨服务器状态分叉。
- 执行公共刷新前若 B 有未提交内容，平台会要求确认放弃；**确认前必须先备份文本**。
- 非 `SUPER_ADMIN` 没有任何通道能写公共级；这类改动只能由超管在平台里落地。

## 6. 可选的产品改进点（需按 `docs/guides/ai-workflow.md` 流程评估，不属本次排查结论）

现状是"默认读共享副本 + 对话写入绕过平台"，因此**默认状态下的对话写入天然不可提交**。可考虑的收敛方向：

| 方向 | 涉及位置 |
|---|---|
| `SUPER_ADMIN` 受管启动时也尝试准备本人公共 worktree，让默认指向 A | `OpencodeProcessStartupService` / `UserOpencodeProcessAssignmentService` 的配置路径解析 |
| 软链指向 B 且检测到 B 存在公共 `agents/skills` 脏改动时，向超管提示"存在无法提交的公共改动" | `AgentConfigApplicationService` 的 public diff 组装 + 配置树提示 |
| 前端公共树在无 worktree 时明确标注"当前为只读共享副本" | `AgentConfigPanel.vue` 公共作用域状态渲染 |

任何一项都会改变公共配置的运行态语义，务必同步更新 `docs/api/http-api.md`、用户手册的 Agent 与 Skill 配置章节和本文。

## 7. 附：关键坐标

- 参数：`OPENCODE_PUBLIC_AGENT_GIT_URL`、`OPENCODE_PUBLIC_CONFIG_GIT_ROOT`、`OPENCODE_PUBLIC_CONFIG_DIR`、
  `OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT`、`OPENCODE_SESSION_DIR`（均以 `common_parameters` 为准）。
- 表：`agent_config_worktrees`（`scope=PUBLIC/WORKSPACE`，含 `root_path`/`status`/`linux_server_id`）、
  `agent_config_operations`、`opencode_server_processes`（`session_path`/`config_path`）、
  `user_opencode_process_bindings`、`public_agent_config_rollouts`、`public_agent_config_rollout_servers`、
  `public_agent_config_rollout_targets`。
- 代码：`OpencodeProcessConfigLinkService:41`（软链路径）、`UserOpencodeProcessAssignmentService:945/977`
  （session 与 config 路径）、`AgentConfigApplicationService:2644/2656/2861`（读写根与参数解析）、
  `AgentConfigApplicationService:929/888`（个人 worktree 解析与热加载）、`AgentConfigPanel.vue:367`（前端树根）、
  `opencode-manager/internal/process/process.go:299-304`（进程环境）、
  `opencode-source/opencode-1.18.4/packages/opencode/src/skill/index.ts:185-227` 与 `config/paths.ts:23-36`（技能扫描）。
- 文档：`docs/api/http-api.md`「Agent 配置管理 API」、`frontend/apps/user-manual/docs/guide/agent-config.md`。
