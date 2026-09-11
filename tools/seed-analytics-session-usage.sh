#!/usr/bin/env bash
# 本地开发造数脚本：向运营分析「会话消息」Tab 灌入多用户示例数据。
#
# 背景：「会话消息」的统计口径依赖平台业务库的 storage_mode / source_type / 人员归属链，
# ClickHouse 事实表没有这些字段，因此该 Tab（以及导出里的「会话消息」Sheet）只读
# PostgreSQL。ClickHouse 侧的示例数据见 tools/seed-analytics-clickhouse.sh。
#
# 目标库是 deploy/local/docker-compose.yml 起的本地 PostgreSQL 容器，与 .env.local 的
# TEST_AGENT_DB_URL（默认 127.0.0.1:15432/test_agent）一致。
#
# 默认规模刻意超过网页单页 20 条，便于验证服务端分页与导出全量：
#   8 个演示用户 × 48 个会话，每会话 1-5 条用户消息，合计 144 条 USER 消息、48 条统计行。
#
# 仅用于本地开发联调，不属于 Flyway migration（AGENTS.md 规则 14 禁止 Flyway 承载测试数据）。
# 用法：bash tools/seed-analytics-session-usage.sh
set -euo pipefail

CONTAINER="${TEST_AGENT_ANALYTICS_SESSION_DB_CONTAINER:-test-agent-postgres}"
DB_NAME="${TEST_AGENT_ANALYTICS_SESSION_DB_NAME:-test_agent}"
DB_USER="${TEST_AGENT_ANALYTICS_SESSION_DB_USER:-test_agent}"
# 造数规模：改这里即可调整「会话消息」的数据量
DEMO_USER_COUNT="${TEST_AGENT_ANALYTICS_SESSION_DEMO_USERS:-8}"
DEMO_SESSION_COUNT="${TEST_AGENT_ANALYTICS_SESSION_DEMO_SESSIONS:-48}"

command -v docker >/dev/null 2>&1 || {
  echo "未找到 docker 命令，请先安装 Docker。" >&2
  exit 1
}

docker inspect "${CONTAINER}" >/dev/null 2>&1 || {
  echo "容器 ${CONTAINER} 未运行，请先启动 deploy/local/docker-compose.yml 的 PostgreSQL。" >&2
  exit 1
}

echo "向 PostgreSQL ${CONTAINER}/${DB_NAME} 灌入「会话消息」示例数据..."

docker exec -i "${CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 \
  -v user_count="${DEMO_USER_COUNT}" \
  -v session_count="${DEMO_SESSION_COUNT}" <<'SQL'

-- 幂等：先清理本脚本上一次写入的演示数据（统一 demo_ana_ 前缀，不影响真实数据）
delete from session_messages where message_id like 'demo_ana_msg_%';
delete from sessions where session_id like 'demo_ana_ses_%';
delete from users where user_id like 'demo_ana_usr_%';
delete from workspaces where workspace_id = 'demo_ana_ws_01';

-- 演示工作区：sessions.workspace_id 有外键约束，必须指向真实工作区行；
-- 该工作区不绑定任何用户，不会出现在真实用户的工作区列表里。
insert into workspaces (workspace_id, name, root_path, status, trace_id, created_at, updated_at)
values ('demo_ana_ws_01', '演示工作区（运营分析造数）', '/tmp/demo-analytics-workspace', 'ACTIVE',
        'demo_ana_trace_ws', now() at time zone 'Asia/Shanghai', now() at time zone 'Asia/Shanghai');

-- 演示用户：机构 3 种 × 研发部 2 种 × 部门 4 种循环，便于验证级联筛选。
-- password_hash 用明显的非 bcrypt 占位值，保证这些演示账号无法登录。
insert into users
    (user_id, unified_auth_id, username, password_hash, organization, rd_department, department, status, created_at, updated_at)
select 'demo_ana_usr_' || lpad(series::text, 2, '0'),
       'demo_ana_auth_' || lpad(series::text, 2, '0'),
       '演示用户' || lpad(series::text, 2, '0'),
       'demo-only-not-a-valid-password-hash',
       (array['北京分行', '上海分行', '广州分行'])[(series % 3) + 1],
       (array['数据研发部', '平台研发部'])[(series % 2) + 1],
       (array['数据平台组', '前端工程组', '算法工程组', '效能平台组'])[(series % 4) + 1],
       'ACTIVE',
       now() at time zone 'Asia/Shanghai',
       now() at time zone 'Asia/Shanghai'
from generate_series(1, :user_count) as series;

-- 会话：全部 MANUAL 来源；run_id 留空走 LEGACY_FULL 分支，人工 USER 消息按条计数。
-- 创建时间按 天/小时 双维度错开，避免所有演示行落在同一时刻。
insert into sessions
    (session_id, workspace_id, title, status, trace_id, created_at, updated_at,
     source_type, created_by_user_id, runtime_kind)
select 'demo_ana_ses_' || lpad(series::text, 3, '0'),
       'demo_ana_ws_01',
       '演示会话 ' || lpad(series::text, 3, '0') || ' · '
           || (array['需求梳理', '性能排查', '方案评审', '问题定位', '口径确认'])[(series % 5) + 1],
       'ACTIVE',
       'demo_ana_trace_ses_' || lpad(series::text, 3, '0'),
       (now() at time zone 'Asia/Shanghai') - ((series % 20 + 1) || ' days')::interval - ((series % 8) || ' hours')::interval,
       (now() at time zone 'Asia/Shanghai') - ((series % 20 + 1) || ' days')::interval - ((series % 8) || ' hours')::interval,
       'MANUAL',
       'demo_ana_usr_' || lpad(((series % :user_count) + 1)::text, 2, '0'),
       'SERVER_PROCESS'
from generate_series(1, :session_count) as series;

-- 用户消息：每个会话 1-5 条，按 3 分钟递增，让统计行的用户消息数与首次/末次发送时间都有差异。
insert into session_messages
    (message_id, session_id, role, content, trace_id, created_at, sender_user_id, source_type)
select 'demo_ana_msg_' || lpad(series::text, 3, '0') || '_' || lpad(step::text, 2, '0'),
       'demo_ana_ses_' || lpad(series::text, 3, '0'),
       'USER',
       '演示用户消息 ' || step,
       'demo_ana_trace_ses_' || lpad(series::text, 3, '0'),
       (now() at time zone 'Asia/Shanghai') - ((series % 20 + 1) || ' days')::interval - ((series % 8) || ' hours')::interval
           + (step * interval '3 minutes'),
       'demo_ana_usr_' || lpad(((series % :user_count) + 1)::text, 2, '0'),
       'MANUAL'
from generate_series(1, :session_count) as series
cross join lateral generate_series(1, (series % 5) + 1) as step;

-- 回显本次写入规模，便于人工核对
select (select count(*) from users where user_id like 'demo_ana_usr_%') as demo_users,
       (select count(*) from sessions where session_id like 'demo_ana_ses_%') as demo_sessions,
       (select count(*) from session_messages where message_id like 'demo_ana_msg_%') as demo_messages;

select u.username,
       count(distinct m.session_id) as session_rows,
       count(*) as user_message_count
from session_messages m
join users u on u.user_id = m.sender_user_id
where m.message_id like 'demo_ana_msg_%'
group by u.username
order by user_message_count desc, u.username;

SQL

echo "「会话消息」示例数据已灌入：${DEMO_USER_COUNT} 个演示用户 / ${DEMO_SESSION_COUNT} 个会话。"
echo "刷新浏览器运营分析页的「会话消息」Tab 即可看到分页；导出 Excel 的「会话消息」Sheet 同步取全量。"
