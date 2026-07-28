-- 应用源码快照的 repository slot、不可变代次、副本、操作步骤、延迟清理和最近选择。
create table app_source_repository_slots (
    repository_id varchar(128) primary key,
    active_generation bigint,
    pending_generation bigint,
    next_generation bigint not null default 1,
    latest_operation_id varchar(128),
    lock_version bigint not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_app_source_slots_repository
        foreign key (repository_id) references code_repositories(repository_id),
    constraint chk_app_source_slots_active_generation
        check (active_generation is null or active_generation >= 1),
    constraint chk_app_source_slots_pending_generation
        check (pending_generation is null or pending_generation >= 1),
    constraint chk_app_source_slots_distinct_generations
        check (active_generation is null or pending_generation is null or active_generation <> pending_generation),
    constraint chk_app_source_slots_next_generation
        check (next_generation >= 1
            and (active_generation is null or next_generation > active_generation)
            and (pending_generation is null or next_generation > pending_generation)),
    constraint chk_app_source_slots_lock_version check (lock_version >= 0)
);

create table app_source_snapshots (
    repository_id varchar(128) not null,
    generation bigint not null,
    repository_english_name varchar(128) not null,
    purpose varchar(16) not null,
    owner_user_id varchar(128),
    branch varchar(255) not null,
    target_commit varchar(128) not null,
    selected_paths_json jsonb not null,
    index_sha256 varchar(64),
    accepted_at timestamp not null,
    expires_at timestamp not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    primary key (repository_id, generation),
    constraint fk_app_source_snapshots_repository
        foreign key (repository_id) references code_repositories(repository_id),
    constraint fk_app_source_snapshots_owner
        foreign key (owner_user_id) references users(user_id),
    constraint chk_app_source_snapshots_generation check (generation >= 1),
    constraint chk_app_source_snapshots_purpose check (purpose in ('PERSONAL', 'TEAM')),
    constraint chk_app_source_snapshots_personal_owner
        check (purpose <> 'PERSONAL' or owner_user_id is not null),
    constraint chk_app_source_snapshots_status
        check (status in ('PENDING', 'ACTIVE', 'FAILED', 'EXPIRED', 'CLEANED')),
    constraint chk_app_source_snapshots_index_sha
        check (index_sha256 is null or index_sha256 ~ '^[0-9a-fA-F]{64}$'),
    constraint chk_app_source_snapshots_expiry check (
        expires_at >= accepted_at + interval '1 hour'
        and expires_at <= accepted_at + interval '72 hours'
        and mod(extract(epoch from (expires_at - accepted_at)), 3600) = 0)
);

create unique index uk_app_source_snapshots_active
    on app_source_snapshots(repository_id) where status = 'ACTIVE';
create index idx_app_source_snapshots_expiry
    on app_source_snapshots(status, expires_at, repository_id, generation);

create table app_source_replicas (
    repository_id varchar(128) not null,
    generation bigint not null,
    linux_server_id varchar(128) not null,
    runtime_workspace_id varchar(128),
    status varchar(32) not null,
    lease_owner varchar(128),
    lease_until timestamp,
    attempt_count integer not null default 0,
    next_retry_at timestamp,
    safe_error_code varchar(128),
    safe_error_message varchar(1000),
    created_at timestamp not null,
    updated_at timestamp not null,
    primary key (repository_id, generation, linux_server_id),
    constraint fk_app_source_replicas_snapshot
        foreign key (repository_id, generation)
        references app_source_snapshots(repository_id, generation),
    constraint fk_app_source_replicas_server
        foreign key (linux_server_id) references linux_servers(linux_server_id),
    constraint fk_app_source_replicas_workspace
        foreign key (runtime_workspace_id) references workspaces(workspace_id),
    constraint chk_app_source_replicas_generation check (generation >= 1),
    constraint chk_app_source_replicas_attempt_count check (attempt_count >= 0),
    constraint chk_app_source_replicas_status check (
        status in ('PENDING', 'RUNNING', 'READY', 'FAILED', 'STALE', 'CLEANUP_PENDING', 'CLEANED')),
    constraint chk_app_source_replicas_lease_pair check (
        (lease_owner is null and lease_until is null) or (lease_owner is not null and lease_until is not null))
);

create index idx_app_source_replicas_claim
    on app_source_replicas(linux_server_id, status, next_retry_at, lease_until, updated_at);

create table app_source_operations (
    operation_id varchar(128) primary key,
    app_id varchar(128) not null,
    repository_id varchar(128) not null,
    source_generation bigint,
    target_generation bigint not null,
    actor_user_id varchar(128) not null,
    operation_type varchar(32) not null,
    request_hash varchar(128) not null,
    status varchar(32) not null,
    trace_id varchar(128) not null,
    accepted_at timestamp not null,
    completed_at timestamp,
    constraint fk_app_source_operations_app foreign key (app_id) references applications(app_id),
    constraint fk_app_source_operations_repository
        foreign key (repository_id) references code_repositories(repository_id),
    constraint fk_app_source_operations_actor foreign key (actor_user_id) references users(user_id),
    constraint chk_app_source_operations_source_generation
        check (source_generation is null or source_generation >= 1),
    constraint chk_app_source_operations_target_generation check (target_generation >= 1),
    constraint chk_app_source_operations_type check (operation_type in (
        'DOWNLOAD', 'UPDATE', 'SWITCH_BRANCH', 'CHANGE_SELECTION', 'PROMOTE_TO_TEAM',
        'RETRY_REPLICAS', 'CLEANUP')),
    constraint chk_app_source_operations_status check (
        status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL_FAILED', 'FAILED')),
    constraint chk_app_source_operations_completed check (
        (status in ('PENDING', 'RUNNING') and completed_at is null)
        or (status in ('SUCCEEDED', 'PARTIAL_FAILED', 'FAILED') and completed_at is not null))
);

create index idx_app_source_operations_repository_latest
    on app_source_operations(repository_id, accepted_at desc, operation_id desc);
create index idx_app_source_operations_request_hash
    on app_source_operations(repository_id, request_hash, accepted_at desc);

create table app_source_operation_steps (
    step_id varchar(128) primary key,
    operation_id varchar(128) not null,
    scope varchar(16) not null,
    linux_server_id varchar(128),
    step_code varchar(64) not null,
    sequence integer not null,
    status varchar(32) not null,
    safe_summary varchar(1000),
    started_at timestamp,
    completed_at timestamp,
    updated_at timestamp not null,
    constraint fk_app_source_steps_operation
        foreign key (operation_id) references app_source_operations(operation_id) on delete cascade,
    constraint fk_app_source_steps_server
        foreign key (linux_server_id) references linux_servers(linux_server_id),
    constraint chk_app_source_steps_scope check (scope in ('GLOBAL', 'SERVER')),
    constraint chk_app_source_steps_scope_server check (
        (scope = 'GLOBAL' and linux_server_id is null)
        or (scope = 'SERVER' and linux_server_id is not null)),
    constraint chk_app_source_steps_sequence check (sequence >= 0),
    constraint chk_app_source_steps_status
        check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED'))
);

create unique index uk_app_source_steps_global
    on app_source_operation_steps(operation_id, step_code) where scope = 'GLOBAL';
create unique index uk_app_source_steps_server
    on app_source_operation_steps(operation_id, linux_server_id, step_code) where scope = 'SERVER';
create index idx_app_source_steps_operation_order
    on app_source_operation_steps(operation_id, sequence, step_id);

create table app_source_cleanup_tasks (
    cleanup_task_id varchar(128) primary key,
    operation_id varchar(128) not null,
    repository_id varchar(128) not null,
    generation bigint not null,
    linux_server_id varchar(128) not null,
    delete_at timestamp not null,
    status varchar(32) not null,
    lease_owner varchar(128),
    lease_until timestamp,
    attempt_count integer not null default 0,
    next_retry_at timestamp,
    safe_error_code varchar(128),
    safe_error_message varchar(1000),
    trace_id varchar(128) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_app_source_cleanup_target unique (repository_id, generation, linux_server_id),
    constraint fk_app_source_cleanup_operation
        foreign key (operation_id) references app_source_operations(operation_id)
        deferrable initially deferred,
    constraint fk_app_source_cleanup_snapshot
        foreign key (repository_id, generation)
        references app_source_snapshots(repository_id, generation)
        deferrable initially deferred,
    constraint fk_app_source_cleanup_server
        foreign key (linux_server_id) references linux_servers(linux_server_id),
    constraint chk_app_source_cleanup_generation check (generation >= 1),
    constraint chk_app_source_cleanup_attempt_count check (attempt_count >= 0),
    constraint chk_app_source_cleanup_status check (
        status in ('PENDING', 'RUNNING', 'RETRY_WAIT', 'CLEANED', 'SUPERSEDED')),
    constraint chk_app_source_cleanup_lease_pair check (
        (lease_owner is null and lease_until is null) or (lease_owner is not null and lease_until is not null))
);

create index idx_app_source_cleanup_due
    on app_source_cleanup_tasks(linux_server_id, status, delete_at, next_retry_at, lease_until);
create index idx_app_source_cleanup_generation
    on app_source_cleanup_tasks(repository_id, generation, linux_server_id);

create table app_source_recent_selections (
    user_id varchar(128) primary key,
    app_id varchar(128) not null,
    repository_id varchar(128) not null,
    generation bigint not null,
    updated_at timestamp not null,
    constraint fk_app_source_recent_user foreign key (user_id) references users(user_id) on delete cascade,
    constraint fk_app_source_recent_app foreign key (app_id) references applications(app_id),
    constraint fk_app_source_recent_snapshot
        foreign key (repository_id, generation)
        references app_source_snapshots(repository_id, generation),
    constraint chk_app_source_recent_generation check (generation >= 1)
);

create index idx_app_source_recent_repository
    on app_source_recent_selections(repository_id, generation);

comment on table app_source_repository_slots is '应用源码代码库代次分配槽位';
comment on column app_source_repository_slots.repository_id is '代码库业务ID，每库唯一';
comment on column app_source_repository_slots.active_generation is '当前生效快照代次';
comment on column app_source_repository_slots.pending_generation is '正在物化的快照代次';
comment on column app_source_repository_slots.next_generation is '下一次分配的快照代次';
comment on column app_source_repository_slots.latest_operation_id is '最近一次操作业务ID';
comment on column app_source_repository_slots.lock_version is '乐观锁版本';
comment on column app_source_repository_slots.created_at is '创建时间';
comment on column app_source_repository_slots.updated_at is '更新时间';

comment on table app_source_snapshots is '应用源码不可变选择快照';
comment on column app_source_snapshots.repository_id is '代码库业务ID';
comment on column app_source_snapshots.generation is '快照代次及worker fencing代次';
comment on column app_source_snapshots.repository_english_name is '快照接受时冻结的代码库英文名';
comment on column app_source_snapshots.purpose is '用途：PERSONAL或TEAM';
comment on column app_source_snapshots.owner_user_id is '个人快照所有者，团队快照可为空';
comment on column app_source_snapshots.branch is '冻结的远端分支';
comment on column app_source_snapshots.target_commit is '冻结的远端提交';
comment on column app_source_snapshots.selected_paths_json is '结构化选择路径JSONB数组';
comment on column app_source_snapshots.index_sha256 is '物化索引文件SHA-256';
comment on column app_source_snapshots.accepted_at is '服务端接受请求的权威时间';
comment on column app_source_snapshots.expires_at is 'acceptedAt加保留小时后的绝对到期时间';
comment on column app_source_snapshots.status is '快照状态';
comment on column app_source_snapshots.created_at is '创建时间';
comment on column app_source_snapshots.updated_at is '状态更新时间';

comment on table app_source_replicas is '应用源码单服务器物化副本';
comment on column app_source_replicas.repository_id is '代码库业务ID';
comment on column app_source_replicas.generation is '快照代次';
comment on column app_source_replicas.linux_server_id is '稳定服务器ID';
comment on column app_source_replicas.runtime_workspace_id is '物化后运行态Workspace ID';
comment on column app_source_replicas.status is '副本状态';
comment on column app_source_replicas.lease_owner is '副本任务租约owner';
comment on column app_source_replicas.lease_until is '副本任务绝对租约到期时间';
comment on column app_source_replicas.attempt_count is '尝试次数';
comment on column app_source_replicas.next_retry_at is '下次允许重试时间';
comment on column app_source_replicas.safe_error_code is '安全错误码';
comment on column app_source_replicas.safe_error_message is '脱敏错误说明';
comment on column app_source_replicas.created_at is '创建时间';
comment on column app_source_replicas.updated_at is '更新时间';

comment on table app_source_operations is '应用源码用户操作';
comment on column app_source_operations.operation_id is '全局唯一操作ID';
comment on column app_source_operations.app_id is '应用ID';
comment on column app_source_operations.repository_id is '代码库业务ID';
comment on column app_source_operations.source_generation is '可空来源代次';
comment on column app_source_operations.target_generation is '目标代次';
comment on column app_source_operations.actor_user_id is '发起用户ID';
comment on column app_source_operations.operation_type is '操作类型';
comment on column app_source_operations.request_hash is '服务端规范化请求摘要';
comment on column app_source_operations.status is '操作状态';
comment on column app_source_operations.trace_id is '链路traceId';
comment on column app_source_operations.accepted_at is '服务端接受时间';
comment on column app_source_operations.completed_at is '操作完成时间';

comment on table app_source_operation_steps is '应用源码全局及服务器步骤';
comment on column app_source_operation_steps.step_id is '步骤业务ID';
comment on column app_source_operation_steps.operation_id is '所属操作ID';
comment on column app_source_operation_steps.scope is 'GLOBAL或SERVER作用域';
comment on column app_source_operation_steps.linux_server_id is '服务器步骤的稳定服务器ID';
comment on column app_source_operation_steps.step_code is '稳定步骤编码';
comment on column app_source_operation_steps.sequence is '展示和执行顺序';
comment on column app_source_operation_steps.status is '步骤状态';
comment on column app_source_operation_steps.safe_summary is '脱敏步骤摘要';
comment on column app_source_operation_steps.started_at is '开始时间';
comment on column app_source_operation_steps.completed_at is '完成时间';
comment on column app_source_operation_steps.updated_at is '更新时间';

comment on table app_source_cleanup_tasks is '应用源码单服务器延迟清理任务';
comment on column app_source_cleanup_tasks.cleanup_task_id is '清理任务业务ID';
comment on column app_source_cleanup_tasks.operation_id is '创建该清理任务的操作ID';
comment on column app_source_cleanup_tasks.repository_id is '代码库业务ID';
comment on column app_source_cleanup_tasks.generation is '待删除快照代次';
comment on column app_source_cleanup_tasks.linux_server_id is '目标稳定服务器ID';
comment on column app_source_cleanup_tasks.delete_at is '绝对删除时间';
comment on column app_source_cleanup_tasks.status is '清理状态';
comment on column app_source_cleanup_tasks.lease_owner is '清理worker租约owner';
comment on column app_source_cleanup_tasks.lease_until is '清理worker绝对租约到期时间';
comment on column app_source_cleanup_tasks.attempt_count is '清理尝试次数';
comment on column app_source_cleanup_tasks.next_retry_at is '下次允许重试时间';
comment on column app_source_cleanup_tasks.safe_error_code is '安全错误码';
comment on column app_source_cleanup_tasks.safe_error_message is '脱敏错误说明';
comment on column app_source_cleanup_tasks.trace_id is '链路traceId';
comment on column app_source_cleanup_tasks.created_at is '创建时间';
comment on column app_source_cleanup_tasks.updated_at is '更新时间';

comment on table app_source_recent_selections is '用户最近打开的应用源码快照';
comment on column app_source_recent_selections.user_id is '用户ID，每用户唯一';
comment on column app_source_recent_selections.app_id is '应用ID';
comment on column app_source_recent_selections.repository_id is '代码库业务ID';
comment on column app_source_recent_selections.generation is '最近选择的快照代次';
comment on column app_source_recent_selections.updated_at is '最近选择时间';

-- Flyway 会把美元符紧跟大括号识别成占位符，使用字符串拼接保存精确占位符字面量。
insert into common_parameters(
    parameter_id, parameter_english, parameter_chinese, parameter_value,
    platform, editable, created_at, updated_at
) values (
    'param_opencode_app_source_root_all',
    'OPENCODE_APP_SOURCE_ROOT',
    'opencode应用源码快照根目录',
    '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/workspace/appsource/',
    'all',
    false,
    current_timestamp,
    current_timestamp
);
