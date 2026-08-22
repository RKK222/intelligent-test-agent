-- 插件首次产生 Trace 目录后，coverage_start_at 成为运行态能力事实的全局切换点。
-- 切换点之前保留历史 RunEvent 口径；切换点及之后只允许 OPENCODE_PLUGIN 事实。
drop table if exists analytics_capability_facts_mv;

create materialized view analytics_capability_facts_mv
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
where (
    JSONExtractString(payload_json, 'eventType') in (
        'RUN_STARTED', 'RUN_TERMINAL', 'RUN_SUCCEEDED', 'RUN_FAILED', 'RUN_CANCELLED',
        'SESSION_CHILD_DISCOVERED')
    or (JSONExtractString(payload_json, 'eventType') in ('TOOL_STARTED', 'TOOL_FINISHED')
        and lowerUTF8(JSONExtractString(payload_json, 'toolName')) != 'task')
)
and occurred_at < coalesce(
    (select minOrNull(coverage_start_at)
     from analytics_trace_catalog final
     where source = 'OPENCODE_PLUGIN'),
    toDateTime64('2299-12-31 23:59:59.999', 3, 'UTC'));
