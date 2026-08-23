alter table local_client_instances
    add column public_capability_commit varchar(64),
    add column public_capability_digest varchar(64),
    add column public_capability_status varchar(32),
    add column public_capability_reported_at timestamptz;

create table local_client_public_capability_releases (
    source_commit varchar(64) primary key,
    bundle_digest varchar(64) not null unique,
    artifact_sha256 varchar(64),
    compatibility varchar(32) not null,
    error_code varchar(128),
    manifest_json text not null,
    change_summary_json text not null,
    agent_count integer not null,
    skill_count integer not null,
    tool_count integer not null,
    requires_restart boolean not null,
    artifact bytea,
    compressed_size bigint not null,
    uncompressed_size bigint not null,
    file_count integer not null,
    created_at timestamptz not null,
    constraint ck_local_client_public_capability_compatibility
        check (compatibility in ('AVAILABLE', 'SERVER_ONLY')),
    constraint ck_local_client_public_capability_artifact
        check ((compatibility = 'AVAILABLE' and artifact is not null and artifact_sha256 is not null and compressed_size > 0)
            or (compatibility = 'SERVER_ONLY' and artifact is null and artifact_sha256 is null)),
    constraint ck_local_client_public_capability_counts
        check (agent_count >= 0 and skill_count >= 0 and tool_count >= 0 and file_count >= 0 and uncompressed_size >= 0)
);

create table local_client_public_capability_states (
    client_instance_id varchar(128) primary key references local_client_instances(client_instance_id) on delete cascade,
    active_commit varchar(64),
    active_digest varchar(64),
    pending_commit varchar(64),
    pending_digest varchar(64),
    status varchar(32) not null,
    error_code varchar(128),
    reported_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_local_client_public_capability_state_status check (status in (
        'CURRENT', 'UPDATE_AVAILABLE', 'PENDING', 'DOWNLOADING', 'APPLYING', 'SUCCEEDED', 'FAILED', 'ROLLED_BACK'
    ))
);

create table local_client_public_capability_attempts (
    command_id varchar(128) primary key,
    client_instance_id varchar(128) not null references local_client_instances(client_instance_id) on delete restrict,
    user_id varchar(128) not null references users(user_id) on delete restrict,
    connection_generation bigint not null,
    target_commit varchar(64) not null,
    target_digest varchar(64) not null,
    status varchar(32) not null,
    error_code varchar(128),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    constraint ck_local_client_public_capability_attempt_generation check (connection_generation >= 0),
    constraint ck_local_client_public_capability_attempt_status check (status in (
        'PENDING', 'SENT', 'DOWNLOADING', 'APPLYING', 'SUCCEEDED', 'FAILED', 'ROLLED_BACK'
    )),
    constraint ck_local_client_public_capability_attempt_terminal check (
        (status in ('SUCCEEDED', 'FAILED', 'ROLLED_BACK') and completed_at is not null)
        or (status not in ('SUCCEEDED', 'FAILED', 'ROLLED_BACK') and completed_at is null)
    )
);

create index idx_local_client_public_capability_releases_created
    on local_client_public_capability_releases(created_at desc);
create index idx_local_client_public_capability_attempts_dispatch
    on local_client_public_capability_attempts(status, updated_at, command_id)
    where completed_at is null;
create unique index uk_local_client_public_capability_attempt_active
    on local_client_public_capability_attempts(client_instance_id, target_digest)
    where completed_at is null;

comment on table local_client_public_capability_releases is '公共 Agent/Skill/Tool 的内容寻址完整客户端能力包';
comment on table local_client_public_capability_states is '本地客户端已激活及待确认公共能力版本';
comment on table local_client_public_capability_attempts is '单实例、单摘要幂等的用户确认安装命令';

alter table user_notifications drop constraint ck_user_notifications_type;
alter table user_notifications add constraint ck_user_notifications_type check (
    type in (
        'SESSION_SHARED', 'AGENT_CONFIG_DISPOSE_PENDING', 'AGENT_CONFIG_DISPOSE_SUCCEEDED',
        'AGENT_CONFIG_DISPOSE_FAILED', 'AGENT_CONFIG_DISPOSE_SUPERSEDED',
        'LOCAL_CLIENT_UPDATE_AVAILABLE', 'LOCAL_CLIENT_PUBLIC_CAPABILITY_AVAILABLE'
    )
);

alter table user_notifications drop constraint ck_user_notifications_action_type;
alter table user_notifications add constraint ck_user_notifications_action_type check (
    action_type in (
        'SESSION_SHARE', 'NONE', 'RESTART_OWN_PROCESS', 'LOCAL_CLIENT_UPDATE',
        'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE'
    )
);
