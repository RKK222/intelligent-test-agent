---
name: enterprise-troubleshooting
description: 仅用于 /Users/kaka/Desktop/intelligent-test-agent（TestAgent）已部署到企业内部环境后产生的现场运行问题，例如企业用户登录、工作区打不开、Agent/Run/用户进程不可用、跨服务器路由、Java/worker 节点异常或数据库状态不一致。不得把其它项目或无关扫描结果的部署节点带入本项目。企业内网默认按不可访问互联网处理；任何可达性结论必须从实际故障发起端或对应企业服务器验证，开发者本机、VPN、浏览器或公网环境的成功不能证明企业链路可达。开发者本机的启动、重启、端口、依赖、`.env.test` 或 `restart-dev-services.sh` 问题不使用本技能，应改用本地启动排查技能。企业现场需要定位某个用户或请求落在哪台服务器时，先用 DBeaver 连接项目数据库执行只读 SQL，拿到用户身份、当前 binding、用户 opencode 进程、工作区和 Java 节点证据；现场命令禁止使用 rg、jq、qgsql 以及任何命令行数据库客户端。
---

# 企业部署后问题排查

## 目标和边界

本技能只处理**已经部署到企业内部环境后**，在企业服务器或企业用户实际使用阶段出现的问题。开始排查前先判断运行目标：

- 目标是企业内已部署环境，问题发生在部署完成后的运行或使用阶段：继续使用本技能。
- 目标是开发者本机，或问题涉及本机启动/重启、`restart-dev-services.sh`、`.env.test`、`localhost`、本机端口、本地前后端/manager 进程或本地依赖：立即停止使用本技能，改用 `intelligent-test-agent-local-startup`；仅需通用本地服务重启时使用 `restart-services`。
- 目标环境不明确：先确认故障发生在开发者本机还是企业已部署服务器；在确认是企业部署后问题前，不执行本技能的 DBeaver 查询和现场日志流程。

本地启动问题即使表现为登录失败、工作区打不开、Agent 不可用或数据库连接失败，也不属于本技能。企业部署、升级或重启完成后在企业环境暴露出的运行故障仍属于本技能；尚未部署的打包、制品传输和安装实施问题不属于本技能。

## 项目隔离与节点证据

- 本技能只适用于 `/Users/kaka/Desktop/intelligent-test-agent`。`/Users/kaka/Desktop/testing-knowledge-graph`/TraceWeave 等其它项目有独立部署结构，不能共用节点表、扫描结果、目录或排障命令。
- `122.233.94.105`、`122.233.94.137`、`122.233.30.15`、`122.233.30.55` 属于其它项目的扫描数据，不是本项目节点；不得用它们定位 TestAgent 用户、工作区、Java、worker 或 Redis。
- 本项目的角色映射优先来自当前仓库 `deploy/internal/README.md`、`deploy/internal/MULTI-BACKEND.md`、`deploy/internal/SINGLE-BACKEND.md` 以及现场数据库/配置证据。文档基线为 `.20` Redis、`.4/.114` Java+worker、`.2` Nginx、`.147` PostgreSQL；如果现场给出的拓扑不同，先暂停并核对项目、版本和实际配置，不得默认为另一套项目结构。
- 任何扫描清单或附件都必须先确认来源项目和扫描根目录。来源不明、项目不一致或节点角色冲突时，只能标记“企业侧拓扑未验证”，不能执行远程命令或把该清单当作本项目事实。

使用本技能需要企业内 PostgreSQL 的 DBeaver 只读连接；终端仅使用系统已有的基础文本、日志和 HTTP 工具，不依赖 `rg`、`jq`、`qgsql` 或数据库 CLI。除非用户明确给出已经批准并验证可用的企业出口或内网镜像，否则企业环境一律按**无互联网、不能临时下载依赖或工具**处理。

## 企业 Docker 线程创建与权限基线

- 用户已于 2026-08-31 明确要求后续企业内网 Docker 部署默认 `--privileged`。排障先只读核对目标容器的 `docker inspect -f '{{.HostConfig.Privileged}}' <已确认容器名>`；Linux 4.19 / Docker 18.09.7 下出现 `EPERM`、`can't start new thread` 或 bootstrap 线程创建失败时结合故障时间和日志判断，不默认归为内存不足，也不把所有启动故障都归为 seccomp。
- 对已要求修复/重部署的目标，复用企业部署脚本按此基线重建，不再要求用户为同一企业 privileged 策略重复批准或每个服务重复做 A/B；仅诊断请求仍保持只读，不擅自重启。镜像已导入且内容无变化时，优先提供带备份、幂等、精确匹配原启动行的 `sed` 命令，不要求重打包或重新传镜像。`docker restart` 不会改变创建参数。
- 重建仅限本次任务范围。不得修改 Docker daemon 全局设置、无关存量容器或普通本机开发容器；保留配置、数据和已有 UID/GID、最小挂载、ACL、密钥权限，并明确 privileged 放宽了宿主隔离，不能声称其它控制抵消了风险。不因此添加 host 网络、Docker socket 或宿主根目录挂载。
- 若首次数据库初始化失败后出现“数据目录非空”，把最早的线程错误与后续残留错误分开。先确认挂载源和是否存在业务数据，保留日志、停止目标后按用户授权备份恢复；禁止自动删库、清空目录、修改初始化完成标记或把失败目录直接当成可丢弃数据。
- `Privileged=true` 后仍失败，应根据新的首个错误继续定位，不反复追加提权参数。`IPv4 forwarding is disabled`、防火墙、数据库残留和网络依赖需分别处理，不能用 privileged 代替。
- 验收包括真实企业目标上的 `Privileged=true`、readiness 和实际调用方业务操作；现代 Mac 启动只能记为本机验证，不能写成企业 Docker 18.09 已修复。目标机运行命令不传 `--platform`，先用 `docker image inspect` 核对架构。权限边界以 `docs/standards/security.md` 的企业 Docker 运行权限基线为准。

## 网络与离线前提

企业现场的网络结论必须绑定到实际网络路径，不能把开发者本机的结果外推到企业环境：

- 开发者 Mac、个人浏览器、VPN、代理、堡垒机、SSH 隧道或公网环境执行成功，只能证明该发起端自己的链路可达；不能证明企业用户浏览器、Nginx、Java、worker、manager、用户 `opencode` 进程或模型网关之间可达。
- 先明确“谁访问谁”：故障发起端、目标主机/IP、端口、协议、路径、代理/`NO_PROXY`、DNS、TLS/SNI 和鉴权边界。随后在实际发起端所在企业主机、容器或等价网络命名空间执行验证；`localhost` 只代表执行命令的那台机器或容器。
- 页面可用性由企业用户入口验证；服务间调用从真实调用方所在节点验证；跨节点转发至少分别验证源 Java 到目标 Java、目标 Java 到本机 manager/用户进程。不能只在任意一台“方便登录”的服务器上执行一次 `curl`。
- 每条网络证据记录执行位置、时间、解析到的地址、目标端口、HTTP 状态或连接/TLS 错误。若没有权限进入实际发起端，只能标记“企业侧链路尚未验证”，给出应在何处执行的只读命令，不能据本机成功判定服务健康。

离线环境禁止把“去互联网取东西”当成排障步骤或修复方案：

- 不要求企业服务器访问 GitHub、公共 Maven/npm/pip 仓库、公共 CDN、在线安装脚本、升级检查、许可证站点或其它公网 API；不建议用 `apt`、`yum`、`brew`、`npm`、`pnpm`、`pip` 等临时联网安装缺失工具。
- 公网资料只能用于理解问题，不能作为企业现场可达性证据；给出的操作必须能依赖交付包内资源、企业服务器已有命令或用户明确确认可用的内网制品库/镜像执行。
- 发现运行时请求公网域名、远程拉取模型/插件/前端资源或动态下载依赖时，优先将其视为离线交付缺口、错误配置或未内置资源，保留域名、调用方和错误时间窗证据，不用“企业网络不通”掩盖产品依赖。

进入企业部署后排查后，把“用户在哪台服务器”作为第一事实，而不是先猜 IP、重启服务或清理数据。排查结果必须区分以下几类位置：

1. **用户 Agent 进程服务器**：以 `user_opencode_process_bindings` 的当前绑定和 `opencode_server_processes` 的进程记录为主证据。
2. **用户工作区服务器**：以 `workspaces.linux_server_id` 为主证据；个人工作区通过 `personal_workspaces.runtime_workspace_id` 关联到工作区。
3. **Java 后端服务器**：以 `backend_java_processes.linux_server_id`、`listen_url`、状态和心跳为证据，表示请求当前可能落到的 Java 节点。
4. **worker/manager 服务器**：以 `opencode_containers`、`opencode_container_managers` 和 manager 心跳为证据，表示用户进程实际由哪台 worker 管理。

`linux_server_id` 是稳定的服务器身份，不保证是 IP，也不能直接拼成访问地址。需要网络地址时单独查看 Java 的 `listen_url`、部署配置或服务器上的 `.serverhost`。当前快照可以被后续健康检查、重启或恢复覆盖；不能仅凭当前表行还原历史抢写者，必须结合操作表和同一时间窗日志。

## 强制工作流

### 1. 先收集最小定位信息

优先收集以下信息，不要求用户发送密码、token、Cookie、私钥或完整请求体：

- 统一认证号、平台用户名或 `user_id`，至少一个；
- 页面错误原文、发生时间和时区；
- `traceId`、`operationId`、`runId` 或 `sessionId`（如果页面或日志有）；
- 访问入口、涉及的功能和是否刚发生重启/迁移。

如果用户只给了模糊昵称，先在 DBeaver 用用户管理字段确认唯一用户，不要直接按模糊结果判断服务器。

### 2. 用 DBeaver 解析用户身份

数据库查询统一在 DBeaver 完成。优先使用只读连接或在 DBeaver 开启只读事务；只执行 `SELECT`/`WITH ... SELECT`，不执行 `UPDATE`、`DELETE`、`INSERT`、DDL、手工改 binding 或“修复数据”脚本。

将下面的占位符替换成一个明确值；不用的条件行删除，避免空值或宽泛条件命中多名用户：

```sql
select user_id,
       unified_auth_id,
       username,
       status,
       organization,
       rd_department,
       department,
       created_at,
       updated_at
from users
where unified_auth_id = '<统一认证号>'
-- or username = '<平台用户名>'
-- or user_id = '<user_id>'
;
```

先确认数据库连接的主机、数据库名和环境，再使用返回的唯一 `user_id` 或 `unified_auth_id` 做后续查询。若查不到用户，先核对 DBeaver 当前连接的环境和输入标识，不要据此判断“用户没有服务器”。

### 3. 立即查询用户当前 Agent 服务器

这条查询同时保留当前 binding 和该用户所有进程记录，避免只看 binding 指向的一行而漏掉旧进程候选。将 `'<统一认证号>'` 替换成已确认的唯一值：

```sql
select
    u.user_id,
    u.unified_auth_id,
    u.username,
    u.status as user_status,
    b.agent_id,
    b.status as binding_status,
    b.process_id as binding_process_id,
    b.linux_server_id as binding_linux_server_id,
    b.port as binding_port,
    b.trace_id as binding_trace_id,
    b.updated_at as binding_updated_at,
    p.process_id,
    p.linux_server_id as process_linux_server_id,
    p.container_id,
    p.port,
    p.pid,
    p.base_url,
    p.status as process_status,
    p.started_at,
    p.last_health_check_at,
    p.health_message,
    p.trace_id as process_trace_id,
    p.updated_at as process_updated_at,
    ls.name as server_name,
    ls.status as server_status,
    ls.last_heartbeat_at as server_last_heartbeat_at,
    c.container_name,
    c.status as container_status,
    cm.manager_id,
    cm.connection_status as manager_connection_status,
    cm.last_heartbeat_at as manager_last_heartbeat_at
from users u
left join user_opencode_process_bindings b
       on b.user_id = u.user_id
      and b.agent_id = 'opencode'
left join opencode_server_processes p
       on p.user_id = u.user_id
left join linux_servers ls
       on ls.linux_server_id = coalesce(p.linux_server_id, b.linux_server_id)
left join opencode_containers c
       on c.container_id = p.container_id
left join opencode_container_managers cm
       on cm.container_id = c.container_id
where u.unified_auth_id = '<统一认证号>'
order by p.updated_at desc nulls last;
```

判读顺序：

- `binding_status = 'ACTIVE'` 时，优先记录 `binding_linux_server_id`、`binding_process_id` 和 `binding_port`；
- 进程行中 `process_linux_server_id`、`process_status`、`pid`、`base_url` 和 `last_health_check_at` 用于确认当前进程事实；
- `server_status`、`server_last_heartbeat_at`、`container_status` 和 manager 心跳用于判断目标节点是否仍在线；
- binding 与 process 的服务器、端口或状态不一致时，先标记为“归属/快照不一致”，不要直接改库或重启。

### 4. 对照工作区和 Java 节点

用户能查到 Agent 进程，不代表当前工作区和请求入口一定在同一台服务器。需要工作区问题时执行：

```sql
select
    u.unified_auth_id,
    pw.personal_workspace_id,
    pw.workspace_name,
    pw.runtime_workspace_id,
    pw.status as personal_workspace_status,
    w.status as workspace_status,
    w.linux_server_id as workspace_linux_server_id,
    b.linux_server_id as binding_linux_server_id,
    pw.branch,
    pw.repo_root_path,
    pw.workspace_root_path,
    pw.updated_at as personal_workspace_updated_at,
    w.updated_at as workspace_updated_at
from personal_workspaces pw
join users u
  on u.user_id = pw.user_id
join workspaces w
  on w.workspace_id = pw.runtime_workspace_id
left join user_opencode_process_bindings b
  on b.user_id = pw.user_id
 and b.agent_id = 'opencode'
 and b.status = 'ACTIVE'
where u.unified_auth_id = '<统一认证号>'
order by pw.updated_at desc;
```

工作区服务器和当前 binding 服务器不一致时，继续查迁移记录、目标目录和请求实际路由；不要只改 `workspaces.linux_server_id` 制造“同服”。

若问题表现为“请求落错 Java 节点”“跨节点转发失败”或“用户进程明明健康但页面不可用”，再查用户涉及服务器上的 Java/manager 拓扑：

```sql
select
    ls.linux_server_id,
    ls.name as server_name,
    ls.status as server_status,
    ls.last_heartbeat_at as server_last_heartbeat_at,
    bp.backend_process_id,
    bp.listen_url,
    bp.status as backend_status,
    bp.started_at as backend_started_at,
    bp.last_heartbeat_at as backend_last_heartbeat_at,
    c.container_id,
    c.container_name,
    c.status as container_status,
    cm.manager_id,
    cm.connection_status as manager_connection_status,
    mbc.status as manager_backend_connection_status,
    mbc.last_heartbeat_at as manager_backend_last_heartbeat_at
from linux_servers ls
left join backend_java_processes bp
       on bp.linux_server_id = ls.linux_server_id
left join opencode_containers c
       on c.linux_server_id = ls.linux_server_id
left join opencode_container_managers cm
       on cm.container_id = c.container_id
left join opencode_manager_backend_connections mbc
       on mbc.manager_id = cm.manager_id
where ls.linux_server_id in (
    select b.linux_server_id
    from users u
    join user_opencode_process_bindings b on b.user_id = u.user_id
    where u.unified_auth_id = '<统一认证号>'
      and b.agent_id = 'opencode'
    union
    select p.linux_server_id
    from users u
    join opencode_server_processes p on p.user_id = u.user_id
    where u.unified_auth_id = '<统一认证号>'
)
order by ls.linux_server_id, bp.updated_at desc nulls last, c.updated_at desc nulls last;
```

### 5. 有初始化/启动失败时查历史操作

当前进程快照会变化；如果有发生时间或 `traceId`，在 DBeaver 查 `opencode_process_start_operations`。时间窗至少覆盖故障前后各 5 分钟，并保留 `RUNNING`、`SUCCEEDED` 和 `FAILED`：

```sql
select
    o.operation_id,
    o.status,
    o.current_step,
    o.error_code,
    o.error_message,
    o.process_id,
    o.service_address,
    o.trace_id,
    round(extract(epoch from (o.updated_at - o.created_at)) * 1000)::bigint as duration_ms,
    o.created_at,
    o.updated_at
from opencode_process_start_operations o
where o.requested_by_user_id = (
    select u.user_id
    from users u
    where u.unified_auth_id = '<统一认证号>'
)
  and o.created_at between timestamp '<开始时间>' and timestamp '<结束时间>'
order by o.created_at;
```

如果只知道 traceId，直接按 `trace_id` 查询；如果当前 binding 已经恢复正常，也不要跳过历史操作，因为成功结果可能覆盖了故障快照。

## 终端日志排查规则

数据库定位到目标 `linux_server_id` 后，才到对应服务器采集日志。终端排查允许使用系统已有的基础工具，例如 `find`、`grep`、`sed`、`awk`、`cut`、`sort`、`head`、`tail`、`wc`、`journalctl`、`docker logs` 和 `curl`。日志命令必须限定服务、文件和时间窗，优先使用 `grep -n -C` 保留上下文。`curl` 只用于企业内已知地址，并且必须从真实调用方所在节点或容器执行；不得用开发者本机 `curl` 代替，也不得用它访问公网下载资源或探测“互联网是否恢复”。

严格禁止：

- `rg`；
- `jq`；
- `qgsql`；
- 任何命令行数据库客户端（包括可能误写成 `psql` 的 PostgreSQL CLI）；
- 为了“看起来修复”而执行数据库写操作、清理快照、手工改服务器归属或先重启再取证。

JSON 日志或 manager state 不用 JSON 专用工具解析；使用精确关键字配合 `grep`/`sed` 查看必要字段。示例：

```bash
journalctl -u test-agent-backend \
  --since '2026-08-24 10:00:00' \
  --until '2026-08-24 10:10:00' \
  --no-pager -o short-iso | \
grep -n -C 20 -E 'trace_[A-Za-z0-9]+|OPENCODE_UNAVAILABLE|进程分配已变化|健康检查'
```

用户进程日志按数据库拿到的端口、统一认证号脱敏后定位；worker 使用 `docker logs --since ... --until ...`，不要把完整 token、Cookie、prompt、私钥、工作区敏感路径或整段请求体带回聊天。

## 证据判定和交付格式

排查结论按“证据 → 判断 → 下一步”输出，至少包含：

1. 用户标识（统一认证号只保留必要部分，必要时脱敏）；
2. **用户当前 Agent 服务器**：`linux_server_id`、服务器名、binding/process 状态、端口、容器/manager、最近心跳；
3. **工作区服务器**：工作区 ID、`workspace_linux_server_id`、与 binding 是否一致；
4. **Java 请求节点**：`backend_process_id`、`listen_url` 主机、状态和心跳；
5. 使用的数据库环境、查询时间窗、trace/operation ID；
6. 网络验证的实际执行位置、源和目标；如果只做过开发者本机验证，明确标记为“不构成企业侧可达证据”；
7. 已确认事实、尚未确认的假设、下一条只读证据。

典型判定：

- ACTIVE binding、进程 RUNNING、服务器和 manager 心跳新鲜：优先排查请求路由、HTTP 健康检查或前端状态，不要先重新分配进程；
- binding 与进程服务器不一致，或工作区服务器与 binding 不一致：先排查迁移/旧快照/跨 Java 路由，不能手工改库；
- 没有 ACTIVE binding 但有用户进程：视为无主或未完成绑定，查初始化操作和日志，不能直接认领；
- `linux_servers` 或 manager 心跳过期：确认目标机器和 worker 状态，再判断是否为节点离线；
- DBeaver 查不到用户或查不到任何关联记录：先核对连接环境、用户标识和时间窗，不要直接下结论。

只有在证据完整且用户明确授权后，才讨论重启、迁移或数据修复；任何写库或部署动作必须单独说明影响、回滚和审批边界。
