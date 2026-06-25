alter table session_messages add column run_id varchar(128);
alter table session_messages add column agent_id varchar(64);
alter table session_messages add column remote_message_id varchar(128);
alter table session_messages add column parts_json text;
alter table session_messages add column tokens_input bigint;
alter table session_messages add column tokens_output bigint;
alter table session_messages add column tokens_reasoning bigint;
alter table session_messages add column tokens_cache_read bigint;
alter table session_messages add column tokens_cache_write bigint;
alter table session_messages add column cost_usd decimal(18, 8);
alter table session_messages add column updated_at timestamp;

update session_messages set updated_at = created_at where updated_at is null;

alter table session_messages
    add constraint fk_session_messages_run foreign key (run_id) references runs(run_id);

create index idx_session_messages_session_run on session_messages(session_id, run_id, created_at, id);
create index idx_session_messages_session_remote on session_messages(session_id, remote_message_id);

alter table runs add column tokens_input bigint;
alter table runs add column tokens_output bigint;
alter table runs add column tokens_reasoning bigint;
alter table runs add column tokens_cache_read bigint;
alter table runs add column tokens_cache_write bigint;
alter table runs add column cost_usd decimal(18, 8);

create index idx_runs_session_active_updated on runs(session_id, status, updated_at, id);
