alter table analytics_trace_spans
    add column if not exists record_kind LowCardinality(String) after lane;

alter table analytics_trace_spans
    add column if not exists started_at Nullable(DateTime64(3, 'UTC')) after occurred_at;

alter table analytics_trace_spans
    add column if not exists tokens_cache_read UInt64 after tokens_reasoning;

alter table analytics_trace_spans
    add column if not exists tokens_cache_write UInt64 after tokens_cache_read;

alter table analytics_trace_spans
    add column if not exists ttft_ms Nullable(UInt64) after tokens_cache_write;

alter table analytics_trace_spans
    add column if not exists decode_ms Nullable(UInt64) after ttft_ms;
