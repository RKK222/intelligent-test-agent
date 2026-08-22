create table application_automation_reference_read_leases (
    token_hash varchar(64) not null,
    user_id varchar(128) not null,
    workspace_id varchar(128) not null,
    app_id varchar(128) not null,
    repository_id varchar(128) not null,
    generation bigint not null,
    expires_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    primary key (token_hash),
    constraint fk_application_automation_reference_read_leases_generation
        foreign key (app_id, repository_id, generation)
        references application_automation_reference_generations(app_id, repository_id, generation) on delete cascade
);

create index idx_application_automation_reference_read_leases_generation
    on application_automation_reference_read_leases(app_id, repository_id, generation, expires_at);
create index idx_application_automation_reference_read_leases_expiry
    on application_automation_reference_read_leases(expires_at);

comment on table application_automation_reference_read_leases is '服务端签发的历史自动化只读标签租约，不保存物理路径或明文令牌';
comment on column application_automation_reference_read_leases.token_hash is '只读标签令牌 SHA-256，不持久化浏览器持有的明文令牌';
