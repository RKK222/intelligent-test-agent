create table if not exists analytics_ingestion_events (
    event_id String,
    version UInt64,
    source_type LowCardinality(String),
    aggregate_id String,
    occurred_at DateTime64(3, 'UTC'),
    payload_json String,
    ingested_at DateTime64(3, 'UTC')
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(occurred_at)
order by (event_id)
ttl occurred_at + interval 2 year delete;

create table if not exists analytics_activity_facts (
    event_id String,
    version UInt64,
    source_type LowCardinality(String),
    aggregate_id String,
    occurred_at DateTime64(3, 'UTC'),
    user_id String,
    username String,
    organization String,
    rd_department String,
    department String,
    session_id String,
    run_id String,
    workspace_id String,
    agent_id String,
    model_id String,
    status LowCardinality(String),
    login_count Int64,
    session_count Int64,
    active_session_count Int64,
    empty_session_count Int64,
    continuous_session_count Int64,
    user_message_count Int64,
    assistant_message_count Int64,
    run_count Int64,
    succeeded_run_count Int64,
    failed_run_count Int64,
    cancelled_run_count Int64,
    active_termination_count Int64,
    valid_interaction_count Int64,
    positive_feedback_count Int64,
    negative_feedback_count Int64,
    diff_proposed_count Int64,
    diff_accepted_count Int64,
    diff_rejected_count Int64,
    tokens_input Int64,
    tokens_output Int64,
    tokens_reasoning Int64,
    tokens_cache_read Int64,
    tokens_cache_write Int64,
    duration_ms Int64,
    attribution_mode LowCardinality(String),
    ingested_at DateTime64(3, 'UTC')
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(occurred_at)
order by (event_id)
ttl occurred_at + interval 2 year delete;

create materialized view if not exists analytics_activity_facts_mv
to analytics_activity_facts
as select
    event_id,
    version,
    source_type,
    aggregate_id,
    occurred_at,
    JSONExtractString(payload_json, 'userId') as user_id,
    JSONExtractString(payload_json, 'username') as username,
    JSONExtractString(payload_json, 'organization') as organization,
    JSONExtractString(payload_json, 'rdDepartment') as rd_department,
    JSONExtractString(payload_json, 'department') as department,
    JSONExtractString(payload_json, 'sessionId') as session_id,
    JSONExtractString(payload_json, 'runId') as run_id,
    JSONExtractString(payload_json, 'workspaceId') as workspace_id,
    JSONExtractString(payload_json, 'agentId') as agent_id,
    JSONExtractString(payload_json, 'modelId') as model_id,
    JSONExtractString(payload_json, 'status') as status,
    JSONExtractInt(payload_json, 'loginCount') as login_count,
    JSONExtractInt(payload_json, 'sessionCount') as session_count,
    JSONExtractInt(payload_json, 'activeSessionCount') as active_session_count,
    JSONExtractInt(payload_json, 'emptySessionCount') as empty_session_count,
    JSONExtractInt(payload_json, 'continuousSessionCount') as continuous_session_count,
    JSONExtractInt(payload_json, 'userMessageCount') as user_message_count,
    JSONExtractInt(payload_json, 'assistantMessageCount') as assistant_message_count,
    JSONExtractInt(payload_json, 'runCount') as run_count,
    JSONExtractInt(payload_json, 'succeededRunCount') as succeeded_run_count,
    JSONExtractInt(payload_json, 'failedRunCount') as failed_run_count,
    JSONExtractInt(payload_json, 'cancelledRunCount') as cancelled_run_count,
    JSONExtractInt(payload_json, 'activeTerminationCount') as active_termination_count,
    JSONExtractInt(payload_json, 'validInteractionCount') as valid_interaction_count,
    if(JSONExtractString(payload_json, 'rating') = 'POSITIVE', 1, 0) as positive_feedback_count,
    if(JSONExtractString(payload_json, 'rating') = 'NEGATIVE', 1, 0) as negative_feedback_count,
    JSONExtractInt(payload_json, 'diffProposedCount') as diff_proposed_count,
    JSONExtractInt(payload_json, 'diffAcceptedCount') as diff_accepted_count,
    JSONExtractInt(payload_json, 'diffRejectedCount') as diff_rejected_count,
    JSONExtractInt(payload_json, 'tokensInput') as tokens_input,
    JSONExtractInt(payload_json, 'tokensOutput') as tokens_output,
    JSONExtractInt(payload_json, 'tokensReasoning') as tokens_reasoning,
    JSONExtractInt(payload_json, 'tokensCacheRead') as tokens_cache_read,
    JSONExtractInt(payload_json, 'tokensCacheWrite') as tokens_cache_write,
    JSONExtractInt(payload_json, 'durationMs') as duration_ms,
    ifNull(nullIf(JSONExtractString(payload_json, 'attributionMode'), ''), 'EVENT_SNAPSHOT') as attribution_mode,
    ingested_at
from analytics_ingestion_events
where JSONExtractString(payload_json, 'eventType') != 'USER_DIMENSION';

create table if not exists analytics_user_dimensions (
    user_id String,
    username String,
    organization String,
    rd_department String,
    department String,
    status LowCardinality(String),
    attribution_mode LowCardinality(String),
    updated_at DateTime64(3, 'UTC'),
    version UInt64
)
engine = ReplacingMergeTree(version)
order by (user_id);

create materialized view if not exists analytics_user_dimensions_mv
to analytics_user_dimensions
as select
    JSONExtractString(payload_json, 'userId') as user_id,
    JSONExtractString(payload_json, 'username') as username,
    JSONExtractString(payload_json, 'organization') as organization,
    JSONExtractString(payload_json, 'rdDepartment') as rd_department,
    JSONExtractString(payload_json, 'department') as department,
    JSONExtractString(payload_json, 'status') as status,
    ifNull(nullIf(JSONExtractString(payload_json, 'attributionMode'), ''), 'EVENT_SNAPSHOT') as attribution_mode,
    occurred_at as updated_at,
    version
from analytics_ingestion_events
where JSONExtractString(payload_json, 'eventType') = 'USER_DIMENSION';

create table if not exists analytics_capability_facts (
    event_id String,
    version UInt64,
    occurred_at DateTime64(3, 'UTC'),
    user_id String,
    username String,
    organization String,
    rd_department String,
    department String,
    run_id String,
    scope_id String,
    call_id String,
    capability_type LowCardinality(String),
    capability_name String,
    status LowCardinality(String),
    attribution_mode LowCardinality(String),
    ingested_at DateTime64(3, 'UTC')
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(occurred_at)
order by (event_id)
ttl occurred_at + interval 2 year delete;

create materialized view if not exists analytics_capability_facts_mv
to analytics_capability_facts
as select
    if(JSONExtractString(payload_json, 'eventType') in (
           'RUN_STARTED', 'RUN_TERMINAL', 'RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED',
           'TOOL_STARTED', 'TOOL_FINISHED', 'SESSION_CHILD_DISCOVERED'),
       concat('capability:', JSONExtractString(payload_json, 'runId'), ':',
              if(JSONExtractString(payload_json, 'eventType') = 'SESSION_CHILD_DISCOVERED',
                 ifNull(nullIf(JSONExtractString(payload_json, 'parentSessionId'), ''), 'root'),
                 ifNull(nullIf(JSONExtractString(payload_json, 'sessionId'), ''), 'root')), ':',
              if(JSONExtractString(payload_json, 'eventType') in (
                     'RUN_STARTED', 'RUN_TERMINAL', 'RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED'),
                 'main',
                 if(JSONExtractString(payload_json, 'eventType') = 'SESSION_CHILD_DISCOVERED',
                    ifNull(nullIf(JSONExtractString(payload_json, 'taskCallId'), ''), event_id),
                    ifNull(nullIf(JSONExtractString(payload_json, 'callId'), ''), event_id)))),
       event_id) as event_id,
    version * 10 + if(JSONExtractString(payload_json, 'eventType') in (
        'RUN_TERMINAL', 'RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED', 'TOOL_FINISHED'), 1, 0) as version,
    occurred_at,
    JSONExtractString(payload_json, 'userId') as user_id,
    JSONExtractString(payload_json, 'username') as username,
    JSONExtractString(payload_json, 'organization') as organization,
    JSONExtractString(payload_json, 'rdDepartment') as rd_department,
    JSONExtractString(payload_json, 'department') as department,
    JSONExtractString(payload_json, 'runId') as run_id,
    if(JSONExtractString(payload_json, 'eventType') = 'SESSION_CHILD_DISCOVERED',
       ifNull(nullIf(JSONExtractString(payload_json, 'parentSessionId'), ''), 'root'),
       ifNull(nullIf(JSONExtractString(payload_json, 'sessionId'), ''), 'root')) as scope_id,
    if(JSONExtractString(payload_json, 'eventType') = 'SESSION_CHILD_DISCOVERED',
       ifNull(nullIf(JSONExtractString(payload_json, 'taskCallId'), ''), event_id),
       ifNull(nullIf(JSONExtractString(payload_json, 'callId'), ''), 'main')) as call_id,
    multiIf(
        JSONExtractString(payload_json, 'eventType') in (
            'RUN_STARTED', 'RUN_TERMINAL', 'RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED'), 'AGENT',
        JSONExtractString(payload_json, 'eventType') = 'SESSION_CHILD_DISCOVERED', 'AGENT',
        lowerUTF8(JSONExtractString(payload_json, 'toolName')) = 'skill', 'SKILL',
        'TOOL') as capability_type,
    multiIf(
        JSONExtractString(payload_json, 'eventType') in (
            'RUN_STARTED', 'RUN_TERMINAL', 'RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED'),
            ifNull(nullIf(JSONExtractString(payload_json, 'capabilityName'), ''), 'default'),
        JSONExtractString(payload_json, 'eventType') = 'SESSION_CHILD_DISCOVERED',
            ifNull(nullIf(JSONExtractString(payload_json, 'agentName'), ''), 'child-agent'),
        lowerUTF8(JSONExtractString(payload_json, 'toolName')) = 'skill',
            if(startsWith(JSONExtractString(payload_json, 'title'), 'Loaded skill: '),
               substring(JSONExtractString(payload_json, 'title'), length('Loaded skill: ') + 1),
               ifNull(nullIf(JSONExtractString(payload_json, 'title'), ''), 'skill')),
        JSONExtractString(payload_json, 'toolName')) as capability_name,
    multiIf(
        JSONExtractString(payload_json, 'eventType') in (
            'RUN_STARTED', 'TOOL_STARTED', 'SESSION_CHILD_DISCOVERED'), 'STARTED',
        upperUTF8(JSONExtractString(payload_json, 'status')) in ('FAILED', 'ERROR'), 'FAILED',
        JSONExtractString(payload_json, 'eventType') in ('RUN_FAILED'), 'FAILED',
        'SUCCEEDED') as status,
    ifNull(nullIf(JSONExtractString(payload_json, 'attributionMode'), ''), 'EVENT_SNAPSHOT') as attribution_mode,
    ingested_at
from analytics_ingestion_events
where JSONExtractString(payload_json, 'eventType') in (
    'RUN_STARTED', 'RUN_TERMINAL', 'RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED',
    'SESSION_CHILD_DISCOVERED')
or (JSONExtractString(payload_json, 'eventType') in ('TOOL_STARTED', 'TOOL_FINISHED')
    and lowerUTF8(JSONExtractString(payload_json, 'toolName')) != 'task');

create table if not exists analytics_feedback_facts (
    event_id String,
    version UInt64,
    occurred_at DateTime64(3, 'UTC'),
    feedback_id String,
    user_id String,
    username String,
    organization String,
    rd_department String,
    department String,
    session_id String,
    run_id String,
    message_id String,
    rating LowCardinality(String),
    reason_code LowCardinality(String),
    attribution_mode LowCardinality(String),
    ingested_at DateTime64(3, 'UTC')
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(occurred_at)
order by (event_id)
ttl occurred_at + interval 2 year delete;

create materialized view if not exists analytics_feedback_facts_mv
to analytics_feedback_facts
as select
    event_id,
    version,
    occurred_at,
    JSONExtractString(payload_json, 'feedbackId') as feedback_id,
    JSONExtractString(payload_json, 'userId') as user_id,
    JSONExtractString(payload_json, 'username') as username,
    JSONExtractString(payload_json, 'organization') as organization,
    JSONExtractString(payload_json, 'rdDepartment') as rd_department,
    JSONExtractString(payload_json, 'department') as department,
    JSONExtractString(payload_json, 'sessionId') as session_id,
    JSONExtractString(payload_json, 'runId') as run_id,
    JSONExtractString(payload_json, 'messageId') as message_id,
    JSONExtractString(payload_json, 'rating') as rating,
    JSONExtractString(payload_json, 'reasonCode') as reason_code,
    ifNull(nullIf(JSONExtractString(payload_json, 'attributionMode'), ''), 'EVENT_SNAPSHOT') as attribution_mode,
    ingested_at
from analytics_ingestion_events
where JSONExtractString(payload_json, 'eventType') = 'FEEDBACK';

create table if not exists analytics_user_activity_hourly (
    bucket_start DateTime64(3, 'UTC'),
    activity_date Date,
    user_id String,
    username String,
    organization String,
    rd_department String,
    department String,
    workspace_id String,
    agent_id String,
    model_id String,
    login_count Int64,
    session_count Int64,
    active_session_count Int64,
    empty_session_count Int64,
    continuous_session_count Int64,
    user_message_count Int64,
    assistant_message_count Int64,
    run_count Int64,
    succeeded_run_count Int64,
    failed_run_count Int64,
    cancelled_run_count Int64,
    active_termination_count Int64,
    valid_interaction_count Int64,
    positive_feedback_count Int64,
    negative_feedback_count Int64,
    diff_proposed_count Int64,
    diff_accepted_count Int64,
    diff_rejected_count Int64,
    tokens_input Int64,
    tokens_output Int64,
    tokens_reasoning Int64,
    tokens_cache_read Int64,
    tokens_cache_write Int64,
    tokens_total Int64,
    duration_total_ms Int64,
    duration_run_count Int64,
    first_activity_at DateTime64(3, 'UTC'),
    last_activity_at DateTime64(3, 'UTC'),
    version UInt64
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(bucket_start)
order by (bucket_start, user_id, workspace_id, agent_id, model_id);

create table if not exists analytics_user_activity_daily as analytics_user_activity_hourly
engine = ReplacingMergeTree(version)
partition by toYYYYMM(activity_date)
order by (activity_date, user_id, workspace_id, agent_id, model_id);

create table if not exists analytics_run_duration_histogram_hourly (
    bucket_start DateTime64(3, 'UTC'),
    organization String,
    rd_department String,
    department String,
    workspace_id String,
    agent_id String,
    model_id String,
    le_ms Int64,
    run_count Int64,
    version UInt64
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(bucket_start)
order by (bucket_start, organization, rd_department, department, workspace_id, agent_id, model_id, le_ms);

create table if not exists analytics_rollup_watermarks (
    job_name String,
    watermark_at DateTime64(3, 'UTC'),
    generated_at DateTime64(3, 'UTC'),
    status LowCardinality(String),
    message String,
    coverage_start Nullable(DateTime64(3, 'UTC')),
    coverage_end Nullable(DateTime64(3, 'UTC')),
    attribution_mode LowCardinality(String),
    version UInt64
)
engine = ReplacingMergeTree(version)
order by (job_name);

create table if not exists analytics_schema_history (
    version String,
    description String,
    script String,
    checksum String,
    installed_on DateTime64(3, 'UTC'),
    success UInt8
)
engine = ReplacingMergeTree(installed_on)
order by (version);
