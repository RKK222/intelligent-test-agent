create table reference_repository_states (
    repository_id varchar(128) primary key,
    branch varchar(255),
    target_commit_hash varchar(128),
    generation bigint not null default 0,
    status varchar(32) not null,
    credential_user_id varchar(128),
    trace_id varchar(128) not null,
    last_error varchar(1000),
    initialized_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_reference_repository_states_repository
        foreign key (repository_id) references code_repositories(repository_id),
    constraint fk_reference_repository_states_credential_user
        foreign key (credential_user_id) references users(user_id),
    constraint chk_reference_repository_states_generation check (generation >= 0),
    constraint chk_reference_repository_states_status check (
        status in ('UNINITIALIZED', 'INITIALIZING', 'VERIFYING', 'SYNCHRONIZING', 'READY', 'FAILED'))
);

create table reference_repository_replicas (
    repository_id varchar(128) not null,
    linux_server_id varchar(128) not null,
    generation bigint not null,
    status varchar(32) not null,
    branch varchar(255) not null,
    current_commit_hash varchar(128),
    retry_count integer not null default 0,
    next_retry_at timestamp,
    lease_token varchar(128),
    lease_until timestamp,
    last_error varchar(1000),
    synced_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    primary key (repository_id, linux_server_id),
    constraint fk_reference_repository_replicas_state
        foreign key (repository_id) references reference_repository_states(repository_id) on delete cascade,
    constraint chk_reference_repository_replicas_generation check (generation >= 0),
    constraint chk_reference_repository_replicas_retry_count check (retry_count >= 0),
    constraint chk_reference_repository_replicas_status check (
        status in ('PENDING', 'PROCESSING', 'READY', 'RETRY_WAIT', 'BLOCKED', 'DEFERRED'))
);

create index idx_reference_repository_replicas_claim
    on reference_repository_replicas(linux_server_id, status, next_retry_at, lease_until, updated_at);
create index idx_reference_repository_replicas_generation
    on reference_repository_replicas(repository_id, generation, status);

comment on table reference_repository_states is '引用资产库总体同步状态';
comment on column reference_repository_states.repository_id is '代码库业务ID，同时作为引用资产库状态主键';
comment on column reference_repository_states.branch is '首次初始化后固定的远端分支';
comment on column reference_repository_states.target_commit_hash is '当前代次固定的远端目标提交';
comment on column reference_repository_states.generation is '同步代次，用于隔离过期worker';
comment on column reference_repository_states.status is '总体状态';
comment on column reference_repository_states.credential_user_id is '本代次Git操作使用的凭据用户';
comment on column reference_repository_states.trace_id is '最近一次操作traceId';
comment on column reference_repository_states.last_error is '最近一次安全错误说明';
comment on column reference_repository_states.initialized_at is '首次完成初始化请求时间';
comment on column reference_repository_states.created_at is '创建时间';
comment on column reference_repository_states.updated_at is '更新时间';

comment on table reference_repository_replicas is '引用资产库每Linux服务器副本任务';
comment on column reference_repository_replicas.repository_id is '引用资产库代码库业务ID';
comment on column reference_repository_replicas.linux_server_id is '稳定Linux服务器ID';
comment on column reference_repository_replicas.generation is '副本目标代次';
comment on column reference_repository_replicas.status is '副本状态';
comment on column reference_repository_replicas.branch is '副本目标分支';
comment on column reference_repository_replicas.current_commit_hash is '本机实际提交';
comment on column reference_repository_replicas.retry_count is '临时错误重试次数';
comment on column reference_repository_replicas.next_retry_at is '下次允许认领时间';
comment on column reference_repository_replicas.lease_token is '数据库租约fencing token';
comment on column reference_repository_replicas.lease_until is '数据库租约到期时间';
comment on column reference_repository_replicas.last_error is '最近一次安全错误说明';
comment on column reference_repository_replicas.synced_at is '最近同步成功时间';
comment on column reference_repository_replicas.created_at is '创建时间';
comment on column reference_repository_replicas.updated_at is '更新时间';
