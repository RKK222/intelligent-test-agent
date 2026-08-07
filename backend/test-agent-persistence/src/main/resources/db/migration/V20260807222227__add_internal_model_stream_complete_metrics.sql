-- 增加上游 SSE 流完成指标：从请求开始到收到 OpenAI 兼容 [DONE] 的耗时。
alter table internal_model_call_records
    add column stream_complete_ms bigint;

alter table internal_model_call_stats_hourly
    add column stream_complete_ms_sum bigint not null default 0;

alter table internal_model_call_stats_hourly
    add column stream_complete_ms_max bigint not null default 0;

alter table internal_model_call_stats_hourly
    add column stream_complete_count bigint not null default 0;

comment on column internal_model_call_records.stream_complete_ms is
    '从请求开始到收到 OpenAI 兼容 SSE [DONE] 的耗时（毫秒）；未完成或非流式响应为空。';
comment on column internal_model_call_stats_hourly.stream_complete_ms_sum is
    '已收到 [DONE] 的调用流完成耗时合计（毫秒）。';
comment on column internal_model_call_stats_hourly.stream_complete_ms_max is
    '已收到 [DONE] 的调用流完成最大耗时（毫秒）。';
comment on column internal_model_call_stats_hourly.stream_complete_count is
    '已收到 [DONE] 的调用数，用于计算准确平均值。';
