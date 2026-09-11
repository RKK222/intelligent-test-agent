#!/usr/bin/env bash
# 本地开发造数脚本：向 ClickHouse 运营分析表灌入覆盖全部 7 个 Tab 的示例数据。
# 仅用于本地开发联调，不属于 Flyway migration（AGENTS.md 规则 14 禁止 Flyway 承载测试数据）。
# 用法：bash tools/seed-analytics-clickhouse.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ENV_FILE="${ROOT_DIR}/.tmp/dev-services/clickhouse/clickhouse-dev.env"

[[ -f "${ENV_FILE}" ]] || {
  echo "未找到 ${ENV_FILE}，请先执行 tools/clickhouse-dev-services.sh restart。" >&2
  exit 1
}

CONTAINER="$(grep -E '^TEST_AGENT_CLICKHOUSE_DEV_CONTAINER=' "${ENV_FILE}" | cut -d= -f2-)"
DATABASE="$(grep -E '^TEST_AGENT_CLICKHOUSE_DEV_DATABASE=' "${ENV_FILE}" | cut -d= -f2-)"
[[ -n "${CONTAINER}" && -n "${DATABASE}" ]] || {
  echo "无法从 ${ENV_FILE} 读取容器名或库名。" >&2
  exit 1
}

docker inspect "${CONTAINER}" >/dev/null 2>&1 || {
  echo "容器 ${CONTAINER} 未运行，请先执行 tools/clickhouse-dev-services.sh start。" >&2
  exit 1
}

echo "向 ClickHouse ${DATABASE} 灌入运营分析示例数据..."

# 用 docker exec 管道执行多语句 SQL；clickhouse-client 默认 default 用户可在容器内本地连接。
docker exec -i "${CONTAINER}" clickhouse-client --database "${DATABASE}" --multiquery <<'SQL'

-- 清空旧示例数据，保证幂等
TRUNCATE TABLE IF EXISTS analytics_user_activity_hourly;
TRUNCATE TABLE IF EXISTS analytics_user_activity_daily;
TRUNCATE TABLE IF EXISTS analytics_feedback_facts;
TRUNCATE TABLE IF EXISTS analytics_activity_facts;
TRUNCATE TABLE IF EXISTS analytics_capability_facts;
TRUNCATE TABLE IF EXISTS analytics_user_dimensions;
TRUNCATE TABLE IF EXISTS analytics_rollup_watermarks;
TRUNCATE TABLE IF EXISTS analytics_run_duration_histogram_hourly;

-- 用户维度表：喂"用户数"卡片和筛选项下拉
INSERT INTO analytics_user_dimensions
    (user_id, username, organization, rd_department, department, status, attribution_mode, updated_at, version)
VALUES
    ('u_1001', '张伟',   '总行研发中心', '平台研发部', '前端工程组', 'ACTIVE', 'EVENT_SNAPSHOT', now(), 1),
    ('u_1002', '李娜',   '总行研发中心', '平台研发部', '后端工程组', 'ACTIVE', 'EVENT_SNAPSHOT', now(), 1),
    ('u_1003', '王强',   '北京分行',     '数据研发部', '数据平台组', 'ACTIVE', 'EVENT_SNAPSHOT', now(), 1),
    ('u_1004', '刘洋',   '北京分行',     '数据研发部', '算法工程组', 'ACTIVE', 'EVENT_SNAPSHOT', now(), 1),
    ('u_1005', '陈静',   '上海分行',     '应用研发部', '测试工程组', 'ACTIVE', 'EVENT_SNAPSHOT', now(), 1),
    ('u_1006', '赵磊',   '上海分行',     '应用研发部', '运维工程组', 'ACTIVE', 'EVENT_SNAPSHOT', now(), 1),
    ('u_1007', '孙芳',   '总行研发中心', '平台研发部', '前端工程组', 'DISABLED', 'EVENT_SNAPSHOT', now(), 1);

-- 小时汇总表：喂使用总览/用户运营/组织分析/Token运营/趋势/漏斗
-- 取最近 30 天每天一个 bucket，5 个活跃用户，含 login/session/message/run/token/diff/feedback 全字段
INSERT INTO analytics_user_activity_hourly
    (bucket_start, activity_date, user_id, username, organization, rd_department, department,
     workspace_id, agent_id, model_id,
     login_count, session_count, active_session_count, empty_session_count, continuous_session_count,
     user_message_count, assistant_message_count,
     run_count, succeeded_run_count, failed_run_count, cancelled_run_count, active_termination_count, valid_interaction_count,
     positive_feedback_count, negative_feedback_count,
     diff_proposed_count, diff_accepted_count, diff_rejected_count,
     tokens_input, tokens_output, tokens_reasoning, tokens_cache_read, tokens_cache_write, tokens_total,
     duration_total_ms, duration_run_count, first_activity_at, last_activity_at, version)
SELECT
    bucketStart,
    toDate(bucketStart) AS activity_date,
    userId,
    username,
    organization,
    rd_department,
    department,
    'ws_demo' AS workspace_id,
    'default-agent' AS agent_id,
    'gpt-4o' AS model_id,
    1 AS login_count,
    2 AS session_count,
    2 AS active_session_count,
    0 AS empty_session_count,
    1 AS continuous_session_count,
    messages,
    messages AS assistant_message_count,
    runs,
    succeeded,
    failed,
    cancelled,
    runs AS active_termination_count,
    runs AS valid_interaction_count,
    positives,
    negatives,
    diffs,
    accepted,
    diffs - accepted AS rejected,
    tokens_in,
    tokens_out,
    tokens_reason,
    cache_read,
    cache_write,
    tokens_in + tokens_out + tokens_reason AS tokens_total,
    duration_ms,
    runs,
    bucketStart,
    bucketStart + INTERVAL 1 HOUR,
    1
FROM
(
    -- 中间层：tup 已由下一层物化，这里按“上海本地时刻”算 bucketStart，避免同层别名前向引用。
    -- 每个用户有各自基准小时（9/11/13/15/17），再按天偏移 0~6 小时，保证小时热力图各时段都有值。
    SELECT
        dayStart + INTERVAL (tup.6 - 8 + (number % 4) * 2) HOUR AS bucketStart,
        tup.1 AS userId,
        tup.2 AS username,
        tup.3 AS organization,
        tup.4 AS rd_department,
        tup.5 AS department,
        (number % 7 + 3) AS messages,
        (number % 5 + 2) AS runs,
        (number % 4 + 1) AS succeeded,
        (number % 3) AS failed,
        (number % 2) AS cancelled,
        (number % 6) AS positives,
        (number % 4) AS negatives,
        (number % 3 + 1) AS diffs,
        (number % 2 + 1) AS accepted,
        (number * 120 + 800) AS tokens_in,
        (number * 80 + 400) AS tokens_out,
        (number * 30 + 100) AS tokens_reason,
        (number * 50 + 200) AS cache_read,
        (number * 20 + 80) AS cache_write,
        (number * 1500 + 3000) AS duration_ms
    FROM
    (
        -- 30 天 × 5 用户，先展开用户元组（末位为基准本地小时）
        SELECT
            toStartOfDay(now() - INTERVAL number DAY) AS dayStart,
            arrayJoin([('u_1001','张伟','总行研发中心','平台研发部','前端工程组', 9),
                       ('u_1002','李娜','总行研发中心','平台研发部','后端工程组', 11),
                       ('u_1003','王强','北京分行','数据研发部','数据平台组', 13),
                       ('u_1004','刘洋','北京分行','数据研发部','算法工程组', 15),
                       ('u_1005','陈静','上海分行','应用研发部','测试工程组', 17)]) AS tup,
            number
        FROM numbers(30)
    )
) AS raw
WHERE messages > 0;

-- 日汇总表：从小时表聚合，保证组织分析/Token daily 与总览一致
INSERT INTO analytics_user_activity_daily
    (bucket_start, activity_date, user_id, username, organization, rd_department, department,
     workspace_id, agent_id, model_id,
     login_count, session_count, active_session_count, empty_session_count, continuous_session_count,
     user_message_count, assistant_message_count,
     run_count, succeeded_run_count, failed_run_count, cancelled_run_count, active_termination_count, valid_interaction_count,
     positive_feedback_count, negative_feedback_count,
     diff_proposed_count, diff_accepted_count, diff_rejected_count,
     tokens_input, tokens_output, tokens_reasoning, tokens_cache_read, tokens_cache_write, tokens_total,
     duration_total_ms, duration_run_count, first_activity_at, last_activity_at, version)
SELECT
    min(bucket_start) AS bucket_start,
    activity_date,
    user_id, username, organization, rd_department, department,
    workspace_id, agent_id, model_id,
    sum(login_count), sum(session_count), sum(active_session_count), sum(empty_session_count), sum(continuous_session_count),
    sum(user_message_count), sum(assistant_message_count),
    sum(run_count), sum(succeeded_run_count), sum(failed_run_count), sum(cancelled_run_count), sum(active_termination_count), sum(valid_interaction_count),
    sum(positive_feedback_count), sum(negative_feedback_count),
    sum(diff_proposed_count), sum(diff_accepted_count), sum(diff_rejected_count),
    sum(tokens_input), sum(tokens_output), sum(tokens_reasoning), sum(tokens_cache_read), sum(tokens_cache_write), sum(tokens_total),
    sum(duration_total_ms), sum(duration_run_count), min(first_activity_at), max(last_activity_at), 1
FROM analytics_user_activity_hourly
GROUP BY activity_date, user_id, username, organization, rd_department, department, workspace_id, agent_id, model_id;

-- 反馈事实表：喂满意度 Tab，12 条，满意/不满意各半
INSERT INTO analytics_feedback_facts
    (event_id, version, occurred_at, feedback_id, user_id, username, organization, rd_department, department,
     session_id, run_id, message_id, rating, reason_code, attribution_mode, ingested_at)
SELECT
    concat('fb_evt_', toString(number)) AS event_id,
    1 AS version,
    now() - INTERVAL number DAY AS occurred_at,
    concat('fb_', toString(number)) AS feedback_id,
    users[number % 4 + 1].1 AS user_id,
    users[number % 4 + 1].2 AS username,
    users[number % 4 + 1].3 AS organization,
    users[number % 4 + 1].4 AS rd_department,
    users[number % 4 + 1].5 AS department,
    concat('sess_', toString(number)) AS session_id,
    concat('run_', toString(number)) AS run_id,
    concat('msg_', toString(number)) AS message_id,
    if(number % 2 = 0, 'POSITIVE', 'NEGATIVE') AS rating,
    if(number % 2 = 0, null, arrayElement(['WRONG_ANSWER','NOT_HELPFUL','TOO_SLOW','TOO_VERBOSE','CODE_QUALITY_LOW','OTHER'], (number % 6) + 1)) AS reason_code,
    'EVENT_SNAPSHOT' AS attribution_mode,
    now() AS ingested_at
FROM
(
    SELECT [('u_1001','张伟','总行研发中心','平台研发部','前端工程组'),
            ('u_1002','李娜','总行研发中心','平台研发部','后端工程组'),
            ('u_1003','王强','北京分行','数据研发部','数据平台组'),
            ('u_1004','刘洋','北京分行','数据研发部','算法工程组')] AS users,
    number
FROM numbers(12)
) AS raw;

-- 活动事实表：喂异常 Run Tab 和 p95 耗时，15 条 FAILED + 8 条 CANCELLED + 20 条 SUCCEEDED
INSERT INTO analytics_activity_facts
    (event_id, version, source_type, aggregate_id, occurred_at,
     user_id, username, organization, rd_department, department,
     session_id, run_id, workspace_id, agent_id, model_id, status,
     login_count, session_count, active_session_count, empty_session_count, continuous_session_count,
     user_message_count, assistant_message_count,
     run_count, succeeded_run_count, failed_run_count, cancelled_run_count, active_termination_count, valid_interaction_count,
     positive_feedback_count, negative_feedback_count,
     diff_proposed_count, diff_accepted_count, diff_rejected_count,
     tokens_input, tokens_output, tokens_reasoning, tokens_cache_read, tokens_cache_write,
     duration_ms, attribution_mode, ingested_at)
SELECT
    concat('act_evt_', toString(number)),
    1,
    'RUN',
    concat('run_', toString(number)),
    now() - INTERVAL (number % 25) DAY,
    users[(number % 5) + 1].1,
    users[(number % 5) + 1].2,
    users[(number % 5) + 1].3,
    users[(number % 5) + 1].4,
    users[(number % 5) + 1].5,
    concat('sess_', toString(number)),
    concat('run_', toString(number)),
    'ws_demo',
    'default-agent',
    'gpt-4o',
    multiIf(number < 15, 'FAILED', number < 23, 'CANCELLED', 'SUCCEEDED') AS status,
    0,0,0,0,0,
    0,0,
    1,
    if(status = 'SUCCEEDED', 1, 0),
    if(status = 'FAILED', 1, 0),
    if(status = 'CANCELLED', 1, 0),
    0,0,
    0,0,0,0,0,
    (number * 50 + 200),
    (number * 30 + 100),
    (number * 10 + 50),
    (number * 20 + 80),
    (number * 8 + 30),
    (number * 2000 + 5000),
    'EVENT_SNAPSHOT',
    now()
FROM
(
    SELECT [('u_1001','张伟','总行研发中心','平台研发部','前端工程组'),
            ('u_1002','李娜','总行研发中心','平台研发部','后端工程组'),
            ('u_1003','王强','北京分行','数据研发部','数据平台组'),
            ('u_1004','刘洋','北京分行','数据研发部','算法工程组'),
            ('u_1005','陈静','上海分行','应用研发部','测试工程组')] AS users,
           number
    FROM numbers(43)
) AS raw;

-- 能力事实表：喂能力使用 Tab，AGENT/SKILL/TOOL 各几个，含 STARTED/SUCCEEDED/FAILED
INSERT INTO analytics_capability_facts
    (event_id, version, occurred_at, user_id, username, organization, rd_department, department,
     run_id, scope_id, call_id, capability_type, capability_name, status, attribution_mode, ingested_at)
SELECT
    concat('cap_evt_', toString(number)),
    1,
    now() - INTERVAL (number % 20) DAY,
    users[(number % 4) + 1].1,
    users[(number % 4) + 1].2,
    users[(number % 4) + 1].3,
    users[(number % 4) + 1].4,
    users[(number % 4) + 1].5,
    concat('run_cap_', toString(number)),
    'root',
    concat('call_', toString(number)),
    cap_types[(number % 3) + 1] AS capability_type,
    multiIf(capability_type = 'AGENT', cap_names_agent[(number % 2) + 1],
            capability_type = 'SKILL', cap_names_skill[(number % 2) + 1],
            cap_names_tool[(number % 4) + 1]) AS capability_name,
    statuses[(number % 3) + 1] AS status,
    'EVENT_SNAPSHOT',
    now()
FROM
(
    SELECT [('u_1001','张伟','总行研发中心','平台研发部','前端工程组'),
            ('u_1002','李娜','总行研发中心','平台研发部','后端工程组'),
            ('u_1003','王强','北京分行','数据研发部','数据平台组'),
            ('u_1004','刘洋','北京分行','数据研发部','算法工程组')] AS users,
           ['AGENT','SKILL','TOOL'] AS cap_types,
           ['default','code-review-agent'] AS cap_names_agent,
           ['frontend-design','dynamic-ui'] AS cap_names_skill,
           ['bash','read','grep','edit'] AS cap_names_tool,
           ['STARTED','SUCCEEDED','FAILED'] AS statuses,
           number
    FROM numbers(30)
) AS raw;

-- 水位表：让"暂无统计时间"变成"最新 · 时间"
INSERT INTO analytics_rollup_watermarks
    (job_name, watermark_at, generated_at, status, message, coverage_start, coverage_end, attribution_mode, version)
VALUES
    ('analytics-rollup', now(), now(), 'FRESH', '统计已更新', now() - INTERVAL 30 DAY, now(), 'EVENT_SNAPSHOT', 1);

SQL

echo "运营分析示例数据已灌入，覆盖使用总览/用户运营/Token运营/能力使用/组织分析/满意度/异常Run 共 7 个 Tab。"
echo "刷新浏览器运营分析页即可看到数据。"
