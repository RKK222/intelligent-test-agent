---
name: enterprise-troubleshooting
description: 仅用于 TestAgent 已部署到企业内部环境后产生的现场运行问题，例如企业用户登录、工作区打不开、Agent/Run/用户进程不可用、跨服务器路由、Java/worker 节点异常或数据库状态不一致。开发者本机的启动、重启、端口、依赖、`.env.test` 或 `restart-dev-services.sh` 问题不使用本技能，应改用本地启动排查技能。企业现场需要定位某个用户或请求落在哪台服务器时，先用 DBeaver 连接项目数据库执行只读 SQL，拿到用户身份、当前 binding、用户 opencode 进程、工作区和 Java 节点证据；现场命令禁止使用 rg、jq、qgsql 以及任何命令行数据库客户端。
---

# 企业部署后问题排查

## 目标和边界

本技能只处理**已经部署到企业内部环境后**，在企业服务器或企业用户实际使用阶段出现的问题。开始排查前先判断运行目标：

- 目标是企业内已部署环境，问题发生在部署完成后的运行或使用阶段：继续使用本技能。
- 目标是开发者本机，或问题涉及本机启动/重启、`restart-dev-services.sh`、`.env.test`、`localhost`、本机端口、本地前后端/manager 进程或本地依赖：立即停止使用本技能，改用 `intelligent-test-agent-local-startup`；仅需通用本地服务重启时使用 `restart-services`。
- 目标环境不明确：先确认故障发生在开发者本机还是企业已部署服务器；在确认是企业部署后问题前，不执行本技能的 DBeaver 查询和现场日志流程。

本地启动问题即使表现为登录失败、工作区打不开、Agent 不可用或数据库连接失败，也不属于本技能。企业部署、升级或重启完成后在企业环境暴露出的运行故障仍属于本技能；尚未部署的打包、制品传输和安装实施问题不属于本技能。

使用本技能需要企业内 PostgreSQL 的 DBeaver 只读连接；终端仅使用系统已有的基础文本、日志和 HTTP 工具，不依赖 `rg`、`jq`、`qgsql` 或数据库 CLI。

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

数据库定位到目标 `linux_server_id` 后，才到对应服务器采集日志。终端排查允许使用系统已有的基础工具，例如 `find`、`grep`、`sed`、`awk`、`cut`、`sort`、`head`、`tail`、`wc`、`journalctl`、`docker logs` 和 `curl`。日志命令必须限定服务、文件和时间窗，优先使用 `grep -n -C` 保留上下文。

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
6. 已确认事实、尚未确认的假设、下一条只读证据。

典型判定：

- ACTIVE binding、进程 RUNNING、服务器和 manager 心跳新鲜：优先排查请求路由、HTTP 健康检查或前端状态，不要先重新分配进程；
- binding 与进程服务器不一致，或工作区服务器与 binding 不一致：先排查迁移/旧快照/跨 Java 路由，不能手工改库；
- 没有 ACTIVE binding 但有用户进程：视为无主或未完成绑定，查初始化操作和日志，不能直接认领；
- `linux_servers` 或 manager 心跳过期：确认目标机器和 worker 状态，再判断是否为节点离线；
- DBeaver 查不到用户或查不到任何关联记录：先核对连接环境、用户标识和时间窗，不要直接下结论。

只有在证据完整且用户明确授权后，才讨论重启、迁移或数据修复；任何写库或部署动作必须单独说明影响、回滚和审批边界。
