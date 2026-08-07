alter table sessions add column batch_mode boolean not null default false;
alter table sessions add column batch_id varchar(128);
alter table sessions add column batch_item_request_id varchar(128);

alter table sessions add constraint ck_sessions_batch_attribution
    check (
        (batch_mode = false and batch_id is null and batch_item_request_id is null)
        or
        (batch_mode = true
            and created_by_user_id is not null
            and batch_id is not null and char_length(trim(batch_id)) > 0
            and batch_item_request_id is not null and char_length(trim(batch_item_request_id)) > 0)
    );

create unique index uk_sessions_batch_item_request
    on sessions(created_by_user_id, batch_item_request_id)
    where batch_mode = true;

create index idx_sessions_batch_created
    on sessions(batch_id, created_at)
    where batch_mode = true;

comment on column sessions.batch_mode is '是否由批量模式创建';
comment on column sessions.batch_id is '批量操作ID，仅批量模式有值';
comment on column sessions.batch_item_request_id is '批量条目幂等请求ID，仅批量模式有值';
