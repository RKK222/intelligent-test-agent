alter table internal_model_call_records
    add column last_token_ms bigint;

alter table internal_model_call_records
    add column output_token_count bigint;

alter table internal_model_call_records
    add constraint ck_internal_model_call_records_last_token
        check (last_token_ms is null
            or (first_token_ms is not null and last_token_ms >= first_token_ms));

alter table internal_model_call_records
    add constraint ck_internal_model_call_records_output_token_count
        check (output_token_count is null or output_token_count >= 0);

comment on column internal_model_call_records.last_token_ms is
    '最后一个有效模型输出相对请求开始的到达时刻（毫秒），用于计算 ITL/TPOT';
comment on column internal_model_call_records.output_token_count is
    '上游 usage 返回的准确输出 Token 数，不使用 SSE chunk 数替代';
