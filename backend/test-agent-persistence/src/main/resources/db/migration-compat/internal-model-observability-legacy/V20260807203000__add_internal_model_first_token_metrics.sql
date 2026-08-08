-- 修正内部模型可观测性首 token 指标：首字节（响应头）与首 token（首个有效 SSE data）分开记录。
alter table internal_model_call_records
    add column first_token_ms bigint;

alter table internal_model_call_stats_hourly
    add column first_token_ms_sum bigint not null default 0;

alter table internal_model_call_stats_hourly
    add column first_token_ms_max bigint not null default 0;

alter table internal_model_call_stats_hourly
    add column first_token_count bigint not null default 0;

comment on column internal_model_call_records.first_token_ms is
    '流式响应首个非空且非 [DONE] SSE data 的相对耗时（毫秒）；非流式或未收到有效 chunk 时为空。';
comment on column internal_model_call_stats_hourly.first_token_ms_sum is
    '已收到首 token 的调用首 token 耗时合计（毫秒）。';
comment on column internal_model_call_stats_hourly.first_token_ms_max is
    '已收到首 token 的调用首 token 最大耗时（毫秒）。';
comment on column internal_model_call_stats_hourly.first_token_count is
    '已收到首 token 的调用数，用于计算准确平均值。';
