-- release 已执行自动化配置高版本、但尚未执行 dev 客户端版本管理低版本时，按主链相同结构前向补齐。
-- 本文件只由 DatabaseMigrationCompatibilityCustomizer 对命中历史加载，禁止与两个原始主 migration 同时执行。
alter table local_client_instances
    add column launcher_version varchar(32),
    add column self_update_capabilities text not null default '',
    add column self_update_supported boolean not null default false,
    add column last_update_status varchar(32),
    add column last_update_target_version varchar(14),
    add column last_update_at timestamp with time zone;

create sequence local_client_policy_revision_seq start with 1 increment by 1;

create table local_client_releases (
    version varchar(14) primary key,
    platform varchar(64) not null,
    architecture varchar(64) not null,
    launcher_version_min integer not null,
    launcher_version_max integer not null,
    protocol_version varchar(128) not null,
    manifest_url text not null,
    manifest_sha256 varchar(64) not null,
    manifest_signature text not null,
    compatible boolean not null,
    published_at timestamp with time zone not null,
    synced_at timestamp with time zone not null,
    constraint ck_local_client_releases_version check(version ~ '^[0-9]{14}$'),
    constraint ck_local_client_releases_launcher check(
        launcher_version_min > 0 and launcher_version_max >= launcher_version_min
    ),
    constraint ck_local_client_releases_manifest_sha check(manifest_sha256 ~ '^[0-9a-f]{64}$')
);

create table local_client_release_artifacts (
    version varchar(14) not null references local_client_releases(version) on delete restrict,
    artifact_kind varchar(32) not null,
    artifact_url text not null,
    artifact_size bigint not null,
    artifact_sha256 varchar(64) not null,
    artifact_signature text not null,
    primary key(version, artifact_kind),
    constraint ck_local_client_release_artifacts_kind check(
        artifact_kind in ('CLIENT_JAR', 'JDK', 'OPENCODE')
    ),
    constraint ck_local_client_release_artifacts_size check(artifact_size > 0),
    constraint ck_local_client_release_artifacts_sha check(artifact_sha256 ~ '^[0-9a-f]{64}$')
);

create table local_client_global_version_policy (
    policy_id varchar(16) primary key,
    target_version varchar(14) references local_client_releases(version) on delete restrict,
    revision bigint not null unique,
    updated_by varchar(128) not null references users(user_id) on delete restrict,
    updated_at timestamp with time zone not null,
    constraint ck_local_client_global_policy_id check(policy_id = 'GLOBAL'),
    constraint ck_local_client_global_policy_revision check(revision > 0)
);

create table local_client_user_version_policies (
    user_id varchar(128) primary key references users(user_id) on delete cascade,
    target_version varchar(14) references local_client_releases(version) on delete restrict,
    revision bigint not null unique,
    updated_by varchar(128) not null references users(user_id) on delete restrict,
    updated_at timestamp with time zone not null,
    constraint ck_local_client_user_policy_revision check(revision > 0)
);

create table local_client_version_policy_audits (
    audit_id varchar(128) primary key,
    policy_scope varchar(16) not null,
    user_id varchar(128) references users(user_id) on delete set null,
    previous_target_version varchar(14),
    target_version varchar(14),
    revision bigint not null unique,
    action varchar(32) not null,
    actor_user_id varchar(128) not null references users(user_id) on delete restrict,
    created_at timestamp with time zone not null,
    constraint ck_local_client_policy_audits_scope check(policy_scope in ('GLOBAL', 'USER')),
    constraint ck_local_client_policy_audits_action check(action in ('SET_GLOBAL', 'SET_USER', 'CLEAR_USER')),
    constraint ck_local_client_policy_audits_revision check(revision > 0)
);

create table local_client_update_rollouts (
    rollout_id varchar(128) primary key,
    rollout_scope varchar(32) not null,
    requested_user_id varchar(128) references users(user_id) on delete restrict,
    status varchar(32) not null,
    created_by varchar(128) not null references users(user_id) on delete restrict,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint ck_local_client_rollouts_scope check(rollout_scope in ('ALL_ONLINE', 'USER')),
    constraint ck_local_client_rollouts_target check(
        (rollout_scope = 'ALL_ONLINE' and requested_user_id is null)
        or (rollout_scope = 'USER' and requested_user_id is not null)
    ),
    constraint ck_local_client_rollouts_status check(status in ('RUNNING', 'COMPLETED', 'PARTIAL_FAILED'))
);

create table local_client_update_attempts (
    command_id varchar(128) primary key,
    rollout_id varchar(128) not null references local_client_update_rollouts(rollout_id) on delete restrict,
    client_instance_id varchar(128) not null references local_client_instances(client_instance_id) on delete restrict,
    user_id varchar(128) not null references users(user_id) on delete restrict,
    connection_generation bigint not null,
    policy_revision bigint not null,
    current_version varchar(64) not null,
    target_version varchar(14) not null references local_client_releases(version) on delete restrict,
    direction varchar(16) not null,
    status varchar(32) not null,
    release_digest varchar(64),
    error_code varchar(128),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint uk_local_client_update_attempt unique(rollout_id, client_instance_id),
    constraint ck_local_client_update_attempt_generation check(connection_generation > 0),
    constraint ck_local_client_update_attempt_revision check(policy_revision > 0),
    constraint ck_local_client_update_attempt_direction check(direction in ('UPDATE', 'ROLLBACK')),
    constraint ck_local_client_update_attempt_status check(status in (
        'PENDING', 'SENT', 'PREPARING', 'PREPARED', 'APPLYING',
        'SUCCEEDED', 'FAILED', 'CANCELLED', 'AUTO_ROLLED_BACK'
    )),
    constraint ck_local_client_update_attempt_digest check(
        release_digest is null or release_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_local_client_update_attempt_terminal check(
        (status in ('SUCCEEDED', 'FAILED', 'CANCELLED', 'AUTO_ROLLED_BACK') and completed_at is not null)
        or (status not in ('SUCCEEDED', 'FAILED', 'CANCELLED', 'AUTO_ROLLED_BACK') and completed_at is null)
    )
);

create index idx_local_client_releases_synced on local_client_releases(synced_at desc, version desc);
create index idx_local_client_user_policies_target on local_client_user_version_policies(target_version, user_id);
create index idx_local_client_rollouts_created on local_client_update_rollouts(created_at desc);
create index idx_local_client_attempts_rollout on local_client_update_attempts(rollout_id, created_at, command_id);
create index idx_local_client_attempts_dispatch on local_client_update_attempts(status, updated_at)
    where status in ('PENDING', 'SENT', 'PREPARING', 'PREPARED', 'APPLYING');

comment on table local_client_releases is '从可信 Nginx catalog 同步并验签的不可变本地客户端发布版本';
comment on table local_client_release_artifacts is '同一客户端发布单元锁定的 JAR、JDK 和 OpenCode 制品';
comment on table local_client_global_version_policy is '平台全局本地客户端目标版本';
comment on table local_client_user_version_policies is '用户级版本覆盖；target_version 为空表示已清除覆盖的修订 tombstone';
comment on table local_client_version_policy_audits is '全局和用户版本策略变更审计';
comment on table local_client_update_rollouts is '管理员立即更新操作的在线实例快照';
comment on table local_client_update_attempts is '按 commandId 和 generation fencing 的单实例静默更新状态机';
comment on column local_client_instances.launcher_version is '稳定 Shell 启动器协议版本；旧客户端为空';
comment on column local_client_instances.self_update_capabilities is '逗号分隔的已校验客户端能力集合';
comment on column local_client_instances.self_update_supported is '是否声明 SELF_UPDATE_V1 且启动器版本有效';

alter table user_notifications drop constraint ck_user_notifications_type;
alter table user_notifications add constraint ck_user_notifications_type check (
    type in (
        'SESSION_SHARED',
        'AGENT_CONFIG_DISPOSE_PENDING',
        'AGENT_CONFIG_DISPOSE_SUCCEEDED',
        'AGENT_CONFIG_DISPOSE_FAILED',
        'AGENT_CONFIG_DISPOSE_SUPERSEDED',
        'LOCAL_CLIENT_UPDATE_AVAILABLE'
    )
);

alter table user_notifications drop constraint ck_user_notifications_action_type;
alter table user_notifications add constraint ck_user_notifications_action_type check (
    action_type in ('SESSION_SHARE', 'NONE', 'RESTART_OWN_PROCESS', 'LOCAL_CLIENT_UPDATE')
);

alter table local_client_credentials
    add column revealed_at timestamp with time zone;

-- 存量密钥可能已经被复制过，升级后必须失败关闭，不能重新暴露明文。
update local_client_credentials
set revealed_at = updated_at
where revealed_at is null;

comment on column local_client_credentials.revealed_at is
    '当前凭据版本首次且唯一一次返回明文的权威时间；空值表示尚可显示';
