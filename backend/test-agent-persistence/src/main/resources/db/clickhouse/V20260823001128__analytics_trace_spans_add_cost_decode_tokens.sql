alter table analytics_trace_spans
    add column if not exists tokens_total UInt64 after tokens_cache_write;

alter table analytics_trace_spans
    add column if not exists decode_tokens UInt64 after decode_ms;

alter table analytics_trace_spans
    add column if not exists cost Nullable(Float64) after decode_tokens;

alter table analytics_trace_spans
    add column if not exists finish_reason LowCardinality(String) after cost;
