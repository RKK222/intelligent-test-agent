#!/usr/bin/env bash
# 本地开发造数脚本：向 ClickHouse 运营分析表灌入覆盖全部 7 个 Tab 的示例数据。
# 仅用于本地开发联调，不属于 Flyway migration（AGENTS.md 规则 14 禁止 Flyway 承载测试数据）。
#
# 数据量刻意超过网页默认分页阈值（pageSize/topN 默认 20），用于验证导出是否取全量：
#   用户运营 30 人、组织分析 30 个部门、能力使用 34 个能力、满意度 80 条、异常 Run 100 条。
# 用法：bash tools/seed-analytics-clickhouse.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ENV_FILE="${ROOT_DIR}/.tmp/dev-services/clickhouse/clickhouse-dev.env"

# 造数规模常量：改这里即可调整各 Tab 的数据量
USER_COUNT=30        # 活跃用户数（写入小时/日汇总及各事实表）
USER_DIM_COUNT=32    # 用户维度表总行数，末尾 2 个为 DISABLED 用户
DAY_COUNT=30         # 追溯天数
FEEDBACK_COUNT=80    # 满意度反馈条数
ACTIVITY_COUNT=150   # Run 事实条数：60 FAILED + 40 CANCELLED + 50 SUCCEEDED
CAPABILITY_COUNT=180 # 能力调用条数：AGENT 6 种 + SKILL 8 种 + TOOL 20 种，共 34 个能力

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
docker exec -i "${CONTAINER}" clickhouse-client --database "${DATABASE}" --multiquery \
  --param_user_count="${USER_COUNT}" \
  --param_user_dim_count="${USER_DIM_COUNT}" \
  --param_day_count="${DAY_COUNT}" \
  --param_feedback_count="${FEEDBACK_COUNT}" \
  --param_activity_count="${ACTIVITY_COUNT}" \
  --param_capability_count="${CAPABILITY_COUNT}" <<'SQL'

-- 清空旧示例数据，保证幂等
TRUNCATE TABLE IF EXISTS analytics_user_activity_hourly;
TRUNCATE TABLE IF EXISTS analytics_user_activity_daily;
TRUNCATE TABLE IF EXISTS analytics_feedback_facts;
TRUNCATE TABLE IF EXISTS analytics_activity_facts;
TRUNCATE TABLE IF EXISTS analytics_capability_facts;
TRUNCATE TABLE IF EXISTS analytics_user_dimensions;
TRUNCATE TABLE IF EXISTS analytics_rollup_watermarks;
TRUNCATE TABLE IF EXISTS analytics_run_duration_histogram_hourly;

-- 用户维度表：喂"用户数"卡片和筛选项下拉；末尾 2 个为 DISABLED，避免注册数与启用数完全相同
-- 生成规则：机构 6 种 × 研发部 4 种循环，部门按序号唯一（保证组织分析(按部门)行数超过 20）
INSERT INTO analytics_user_dimensions
    (user_id, username, organization, rd_department, department, status, attribution_mode, updated_at, version)
SELECT
    concat('u_', toString(1001 + number)) AS user_id,
    concat('用户', toString(number + 1)) AS username,
    arrayElement(['总行研发中心','北京分行','上海分行','广州分行','深圳分行','成都分行'], (number % 6) + 1) AS organization,
    arrayElement(['平台研发部','数据研发部','应用研发部','基础架构部'], (number % 4) + 1) AS rd_department,
    concat('业务', toString(number + 1), '组') AS department,
    if(number >= {user_count:UInt64}, 'DISABLED', 'ACTIVE') AS status,
    'EVENT_SNAPSHOT' AS attribution_mode,
    now() AS updated_at,
    1 AS version
FROM numbers({user_dim_count:UInt64});

-- 小时汇总表：喂使用总览/用户运营/组织分析/Token运营/趋势/漏斗/小时热力
-- 30 天 × 30 用户 = 900 行；bucketStart 按"上海本地时刻"构造，本地小时取 8/10/.../22 共 8 个时段，
-- 保证小时热力图各时段都有值，而不是全部堆在同一小时。
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
    -- 中间层：tup 已由下一层物化，这里按"上海本地时刻"算 bucketStart，避免同层别名前向引用。
    -- 本地小时 = 8 + ((用户序号 + 天序号) % 8) * 2，落在 08:00~22:00；对应 UTC 小时 0~14，不会跨 UTC 日。
    SELECT
        dayStart + INTERVAL (((tup.6 + day) % 8) * 2) HOUR AS bucketStart,
        tup.1 AS userId,
        tup.2 AS username,
        tup.3 AS organization,
        tup.4 AS rd_department,
        tup.5 AS department,
        (day % 7 + 3) AS messages,
        (day % 5 + 2) AS runs,
        (day % 4 + 1) AS succeeded,
        (day % 3) AS failed,
        (day % 2) AS cancelled,
        (day % 6) AS positives,
        (day % 4) AS negatives,
        (day % 3 + 1) AS diffs,
        (day % 2 + 1) AS accepted,
        (day * 120 + 800) AS tokens_in,
        (day * 80 + 400) AS tokens_out,
        (day * 30 + 100) AS tokens_reason,
        (day * 50 + 200) AS cache_read,
        (day * 20 + 80) AS cache_write,
        (day * 1500 + 3000) AS duration_ms
    FROM
    (
        -- 先展开用户元组（末位为用户序号，用于错开小时）
        SELECT
            toStartOfDay(now() - INTERVAL number DAY) AS dayStart,
            arrayJoin(arrayMap(i -> tuple(
                concat('u_', toString(1001 + i)),
                concat('用户', toString(i + 1)),
                arrayElement(['总行研发中心','北京分行','上海分行','广州分行','深圳分行','成都分行'], (i % 6) + 1),
                arrayElement(['平台研发部','数据研发部','应用研发部','基础架构部'], (i % 4) + 1),
                concat('业务', toString(i + 1), '组'),
                i
            ), range({user_count:UInt64}))) AS tup,
            number AS day
        FROM numbers({day_count:UInt64})
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

-- 反馈事实表：喂满意度 Tab，造 80 条（远超分页 20），满意/不满意各半
INSERT INTO analytics_feedback_facts
    (event_id, version, occurred_at, feedback_id, user_id, username, organization, rd_department, department,
     session_id, run_id, message_id, rating, reason_code, attribution_mode, ingested_at)
SELECT
    concat('fb_evt_', toString(number)) AS event_id,
    1 AS version,
    now() - INTERVAL (number % {day_count:UInt64}) DAY AS occurred_at,
    concat('fb_', toString(number)) AS feedback_id,
    users[(number % {user_count:UInt64}) + 1].1 AS user_id,
    users[(number % {user_count:UInt64}) + 1].2 AS username,
    users[(number % {user_count:UInt64}) + 1].3 AS organization,
    users[(number % {user_count:UInt64}) + 1].4 AS rd_department,
    users[(number % {user_count:UInt64}) + 1].5 AS department,
    concat('sess_', toString(number)) AS session_id,
    concat('run_', toString(number)) AS run_id,
    concat('msg_', toString(number)) AS message_id,
    if(number % 2 = 0, 'POSITIVE', 'NEGATIVE') AS rating,
    if(number % 2 = 0, null, arrayElement(['WRONG_ANSWER','NOT_HELPFUL','TOO_SLOW','TOO_VERBOSE','CODE_QUALITY_LOW','OTHER'], (number % 6) + 1)) AS reason_code,
    'EVENT_SNAPSHOT' AS attribution_mode,
    now() AS ingested_at
FROM
(
    SELECT
        arrayMap(i -> tuple(
            concat('u_', toString(1001 + i)),
            concat('用户', toString(i + 1)),
            arrayElement(['总行研发中心','北京分行','上海分行','广州分行','深圳分行','成都分行'], (i % 6) + 1),
            arrayElement(['平台研发部','数据研发部','应用研发部','基础架构部'], (i % 4) + 1),
            concat('业务', toString(i + 1), '组')
        ), range({user_count:UInt64})) AS users,
        number
    FROM numbers({feedback_count:UInt64})
) AS raw;

-- 活动事实表：喂异常 Run Tab（只取 FAILED/CANCELLED 共 100 条，远超分页 20）和 p95 耗时
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
    now() - INTERVAL (number % {day_count:UInt64}) DAY,
    users[(number % {user_count:UInt64}) + 1].1,
    users[(number % {user_count:UInt64}) + 1].2,
    users[(number % {user_count:UInt64}) + 1].3,
    users[(number % {user_count:UInt64}) + 1].4,
    users[(number % {user_count:UInt64}) + 1].5,
    concat('sess_', toString(number)),
    concat('run_', toString(number)),
    'ws_demo',
    'default-agent',
    'gpt-4o',
    multiIf(number < 60, 'FAILED', number < 100, 'CANCELLED', 'SUCCEEDED') AS status,
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
    SELECT
        arrayMap(i -> tuple(
            concat('u_', toString(1001 + i)),
            concat('用户', toString(i + 1)),
            arrayElement(['总行研发中心','北京分行','上海分行','广州分行','深圳分行','成都分行'], (i % 6) + 1),
            arrayElement(['平台研发部','数据研发部','应用研发部','基础架构部'], (i % 4) + 1),
            concat('业务', toString(i + 1), '组')
        ), range({user_count:UInt64})) AS users,
        number
    FROM numbers({activity_count:UInt64})
) AS raw;

-- 能力事实表：喂能力使用 Tab，AGENT 6 种 + SKILL 8 种 + TOOL 20 种，共 34 个能力（远超分页 20）
INSERT INTO analytics_capability_facts
    (event_id, version, occurred_at, user_id, username, organization, rd_department, department,
     run_id, scope_id, call_id, capability_type, capability_name, status, attribution_mode, ingested_at)
SELECT
    concat('cap_evt_', toString(number)),
    1,
    now() - INTERVAL (number % {day_count:UInt64}) DAY,
    users[(number % {user_count:UInt64}) + 1].1,
    users[(number % {user_count:UInt64}) + 1].2,
    users[(number % {user_count:UInt64}) + 1].3,
    users[(number % {user_count:UInt64}) + 1].4,
    users[(number % {user_count:UInt64}) + 1].5,
    concat('run_cap_', toString(number)),
    'root',
    concat('call_', toString(number)),
    cap_types[(number % 3) + 1] AS capability_type,
    multiIf(
        capability_type = 'AGENT', agents[(number % length(agents)) + 1],
        capability_type = 'SKILL', skills[(number % length(skills)) + 1],
        tools[(number % length(tools)) + 1]) AS capability_name,
    statuses[(number % 3) + 1] AS status,
    'EVENT_SNAPSHOT',
    now()
FROM
(
    SELECT
        arrayMap(i -> tuple(
            concat('u_', toString(1001 + i)),
            concat('用户', toString(i + 1)),
            arrayElement(['总行研发中心','北京分行','上海分行','广州分行','深圳分行','成都分行'], (i % 6) + 1),
            arrayElement(['平台研发部','数据研发部','应用研发部','基础架构部'], (i % 4) + 1),
            concat('业务', toString(i + 1), '组')
        ), range({user_count:UInt64})) AS users,
        ['AGENT','SKILL','TOOL'] AS cap_types,
        ['default','code-review-agent','explore-agent','doc-agent','test-agent','release-agent'] AS agents,
        ['frontend-design','dynamic-ui','test-runner','sql-review','code-audit','perf-tuning','api-design','log-triage'] AS skills,
        arrayMap(j -> concat('tool_', toString(j + 1)), range(20)) AS tools,
        ['STARTED','SUCCEEDED','FAILED'] AS statuses,
        number
    FROM numbers({capability_count:UInt64})
) AS raw;

-- 水位表：让"暂无统计时间"变成"最新 · 时间"
INSERT INTO analytics_rollup_watermarks
    (job_name, watermark_at, generated_at, status, message, coverage_start, coverage_end, attribution_mode, version)
VALUES
    ('analytics-rollup', now(), now(), 'FRESH', '统计已更新', now() - INTERVAL 30 DAY, now(), 'EVENT_SNAPSHOT', 1);

SQL

echo "运营分析示例数据已灌入，覆盖使用总览/用户运营/Token运营/能力使用/组织分析/满意度/异常Run 共 7 个 Tab。"
echo "造数规模：用户 ${USER_COUNT} 人、部门 ${USER_COUNT} 个、能力 34 个、满意度 ${FEEDBACK_COUNT} 条、异常 Run 100 条（均超过网页分页 20）。"
echo "刷新浏览器运营分析页即可看到数据。"
