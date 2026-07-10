alter table runs add column storage_mode varchar(32) not null default 'LEGACY_FULL';
alter table runs add column status_version bigint not null default 0;
alter table runs add column client_request_id varchar(128);
alter table runs add column producer_linux_server_id varchar(128);
alter table runs add column execution_node_id_snapshot varchar(128);
alter table runs add column opencode_process_id_snapshot varchar(128);
alter table runs add column root_remote_session_id varchar(128);
alter table runs add column dispatch_message_id varchar(128);
alter table runs add column assistant_summary_message_id varchar(128);
alter table runs add column terminal_source varchar(64);
alter table runs add column terminal_reason_code varchar(128);
alter table runs add column safe_error_message varchar(1024);
alter table runs add column remote_stop_confirmed boolean not null default false;
alter table runs add column last_event_seq bigint not null default 0;
alter table runs add column details_expires_at timestamp;
alter table runs add column diff_proposed_count integer not null default 0;
alter table runs add column diff_accepted_count integer not null default 0;
alter table runs add column diff_rejected_count integer not null default 0;
alter table runs add column last_remote_message_id varchar(128);
alter table runs add column last_remote_part_id varchar(128);

alter table runs add constraint chk_runs_status_version_non_negative check (status_version >= 0);
alter table runs add constraint chk_runs_last_event_seq_non_negative check (last_event_seq >= 0);
alter table runs add constraint chk_runs_diff_counts_non_negative check (
    diff_proposed_count >= 0 and diff_accepted_count >= 0 and diff_rejected_count >= 0
);

create unique index uk_runs_session_client_request
    on runs(session_id, client_request_id);
create index idx_runs_summary_server_status
    on runs(producer_linux_server_id, storage_mode, status, updated_at, id);

alter table session_messages add column content_kind varchar(32) not null default 'RAW_LEGACY';
alter table session_messages add column summary_key varchar(255);
alter table session_messages add column summary_version integer;
alter table session_messages add column summary_status varchar(32);

create unique index uk_session_messages_summary_key
    on session_messages(summary_key);

comment on column runs.storage_mode is 'Run创建时固定的存储模式：LEGACY_FULL或REDIS_SUMMARY';
comment on column runs.status_version is 'Run状态CAS版本';
comment on column runs.client_request_id is '客户端单次发送幂等请求号';
comment on column runs.producer_linux_server_id is 'Run生产Linux服务器快照';
comment on column runs.execution_node_id_snapshot is 'Run执行节点快照';
comment on column runs.opencode_process_id_snapshot is 'Run使用的opencode进程快照';
comment on column runs.root_remote_session_id is 'Run根远端会话ID';
comment on column runs.dispatch_message_id is '远端prompt_async稳定派发消息ID';
comment on column runs.assistant_summary_message_id is '终态助手摘要稳定平台消息ID';
comment on column runs.terminal_source is 'Run终态事实来源';
comment on column runs.terminal_reason_code is 'Run终态原因码';
comment on column runs.safe_error_message is '经脱敏后可安全展示的错误摘要';
comment on column runs.remote_stop_confirmed is '是否确认远端运行已停止';
comment on column runs.last_event_seq is 'Redis终态投影时最后durable事件序号';
comment on column runs.details_expires_at is 'Redis完整运行详情失效时间';
comment on column runs.diff_proposed_count is '本Run提议Diff次数';
comment on column runs.diff_accepted_count is '本Run接受Diff次数';
comment on column runs.diff_rejected_count is '本Run拒绝Diff次数';
comment on column runs.last_remote_message_id is '最后可见远端消息ID，用于低频Diff定位';
comment on column runs.last_remote_part_id is '最后可见远端part ID，用于低频Diff定位';
comment on column session_messages.content_kind is '消息内容形态：RAW_LEGACY或SUMMARY';
comment on column session_messages.summary_key is '终态摘要幂等键';
comment on column session_messages.summary_version is '确定性摘要规则版本';
comment on column session_messages.summary_status is '摘要状态：COMPLETE、PARTIAL或FALLBACK';
