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
# 仅用于本地开发联调，不属于 Flyway migration（AGENTS.md 规则 14 禁止 Flyway 承载测试数据）。
# 用法：bash tools/seed-analytics-session-usage.sh
set -euo pipefail

CONTAINER="${TEST_AGENT_ANALYTICS_SESSION_DB_CONTAINER:-test-agent-postgres}"
DB_NAME="${TEST_AGENT_ANALYTICS_SESSION_DB_NAME:-test_agent}"
DB_USER="${TEST_AGENT_ANALYTICS_SESSION_DB_USER:-test_agent}"

command -v docker >/dev/null 2>&1 || {
  echo "未找到 docker 命令，请先安装 Docker。" >&2
  exit 1
}

docker inspect "${CONTAINER}" >/dev/null 2>&1 || {
  echo "容器 ${CONTAINER} 未运行，请先启动 deploy/local/docker-compose.yml 的 PostgreSQL。" >&2
  exit 1
}

echo "向 PostgreSQL ${CONTAINER}/${DB_NAME} 灌入「会话消息」示例数据..."

docker exec -i "${CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 <<'SQL'

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

-- 演示用户：3 个用户分布在 2 个机构 / 2 个研发部 / 3 个部门，便于验证筛选与分组。
-- password_hash 用明显的非 bcrypt 占位值，保证这些演示账号无法登录。
insert into users
    (user_id, unified_auth_id, username, password_hash, organization, rd_department, department, status, created_at, updated_at)
values
    ('demo_ana_usr_01', 'demo_ana_auth_01', '演示·张数据', 'demo-only-not-a-valid-password-hash',
     '北京分行', '数据研发部', '数据平台组', 'ACTIVE',
     now() at time zone 'Asia/Shanghai', now() at time zone 'Asia/Shanghai'),
    ('demo_ana_usr_02', 'demo_ana_auth_02', '演示·李前端', 'demo-only-not-a-valid-password-hash',
     '北京分行', '平台研发部', '前端工程组', 'ACTIVE',
     now() at time zone 'Asia/Shanghai', now() at time zone 'Asia/Shanghai'),
    ('demo_ana_usr_03', 'demo_ana_auth_03', '演示·王算法', 'demo-only-not-a-valid-password-hash',
     '上海分行', '数据研发部', '算法工程组', 'ACTIVE',
     now() at time zone 'Asia/Shanghai', now() at time zone 'Asia/Shanghai');

-- 会话：全部 MANUAL 来源、由演示用户创建；run_id 留空走 LEGACY_FULL 分支，
-- 人工 USER 消息按条计数，与真实会话口径一致。
with plan(session_id, title, owner_user_id, days_ago) as (
    values
        ('demo_ana_ses_01', '数据平台需求梳理',   'demo_ana_usr_01', 1),
        ('demo_ana_ses_02', 'SQL 性能排查',       'demo_ana_usr_01', 3),
        ('demo_ana_ses_03', '前端组件重构方案',   'demo_ana_usr_02', 2),
        ('demo_ana_ses_04', '样式回归问题定位',   'demo_ana_usr_02', 5),
        ('demo_ana_ses_05', '算法指标口径确认',   'demo_ana_usr_03', 7)
)
insert into sessions
    (session_id, workspace_id, title, status, trace_id, created_at, updated_at,
     source_type, created_by_user_id, runtime_kind)
select plan.session_id,
       'demo_ana_ws_01',
       plan.title,
       'ACTIVE',
       'demo_ana_trace_' || plan.session_id,
       (now() at time zone 'Asia/Shanghai') - (plan.days_ago || ' days')::interval,
       (now() at time zone 'Asia/Shanghai') - (plan.days_ago || ' days')::interval,
       'MANUAL',
       plan.owner_user_id,
       'SERVER_PROCESS'
from plan;

-- 用户消息：每个会话 N 条，按 3 分钟间隔递增；再按 days_ago 错开小时，
-- 避免所有演示会话的首次发送时间落在同一时刻。
with plan(session_id, user_id, message_count, days_ago) as (
    values
        ('demo_ana_ses_01', 'demo_ana_usr_01', 5, 1),
        ('demo_ana_ses_02', 'demo_ana_usr_01', 3, 3),
        ('demo_ana_ses_03', 'demo_ana_usr_02', 8, 2),
        ('demo_ana_ses_04', 'demo_ana_usr_02', 2, 5),
        ('demo_ana_ses_05', 'demo_ana_usr_03', 1, 7)
)
insert into session_messages
    (message_id, session_id, role, content, trace_id, created_at, sender_user_id, source_type)
select 'demo_ana_msg_' || plan.session_id || '_' || lpad(series::text, 2, '0'),
       plan.session_id,
       'USER',
       '演示用户消息 ' || series,
       'demo_ana_trace_' || plan.session_id,
       (now() at time zone 'Asia/Shanghai') - (plan.days_ago || ' days')::interval
           - (plan.days_ago || ' hours')::interval
           + (series * interval '3 minutes'),
       plan.user_id,
       'MANUAL'
from plan
cross join lateral generate_series(1, plan.message_count) as series;

-- 回显本次写入结果，便于人工核对
select u.username,
       s.title as session_title,
       count(*) as user_message_count,
       min(m.created_at) as first_message_at,
       max(m.created_at) as last_message_at
from session_messages m
join sessions s on s.session_id = m.session_id
join users u on u.user_id = m.sender_user_id
where m.message_id like 'demo_ana_msg_%'
group by u.username, s.title
order by user_message_count desc, session_title;

SQL

echo "「会话消息」示例数据已灌入：3 个演示用户 / 5 个会话 / 19 条用户消息。"
echo "刷新浏览器运营分析页的「会话消息」Tab 即可看到；导出 Excel 的「会话消息」Sheet 同步生效。"
