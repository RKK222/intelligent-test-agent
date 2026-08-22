create table if not exists analytics_trace_catalog (
    trace_id String,
    user_id String,
    username String,
    organization String,
    rd_department String,
    department String,
    runtime_kind LowCardinality(String),
    source LowCardinality(String),
    process_id String,
    client_instance_id String,
    backend_process_id String,
    linux_server_id String,
    session_id String,
    run_id String,
    agent_id String,
    status LowCardinality(String),
    archive_status LowCardinality(String),
    started_at DateTime64(3, 'UTC'),
    updated_at DateTime64(3, 'UTC'),
    coverage_start_at DateTime64(3, 'UTC'),
    complete_through UInt64,
    event_count UInt64,
    archived_bytes UInt64,
    dropped_count UInt64,
    pending_chunks UInt64,
    complete UInt8,
    redacted UInt8,
    version UInt64
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(started_at)
order by (trace_id);

create table if not exists analytics_trace_spans (
    trace_id String,
    event_id String,
    type LowCardinality(String),
    lane LowCardinality(String),
    occurred_at DateTime64(3, 'UTC'),
    global_sequence UInt64,
    session_sequence UInt64,
    session_id String,
    run_id String,
    turn_id String,
    step_id String,
    message_id String,
    call_id String,
    parent_id String,
    capability_kind LowCardinality(String),
    capability_name String,
    status LowCardinality(String),
    duration_ms UInt64,
    tokens_input UInt64,
    tokens_output UInt64,
    tokens_reasoning UInt64,
    source LowCardinality(String),
    version UInt64
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(occurred_at)
order by (trace_id, event_id);

create table if not exists analytics_plugin_capability_facts (
    event_id String,
    version UInt64,
    occurred_at DateTime64(3, 'UTC'),
    user_id String,
    username String,
    organization String,
    rd_department String,
    department String,
    session_id String,
    run_id String,
    call_id String,
    capability_type LowCardinality(String),
    capability_name String,
    status LowCardinality(String),
    duration_ms UInt64,
    source LowCardinality(String),
    ingested_at DateTime64(3, 'UTC')
)
engine = ReplacingMergeTree(version)
partition by toYYYYMM(occurred_at)
order by (event_id);
