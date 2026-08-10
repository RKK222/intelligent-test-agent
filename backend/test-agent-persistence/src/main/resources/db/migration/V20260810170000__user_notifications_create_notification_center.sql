-- 通用用户站内通知；正文只保存安全展示快照，动作只能使用服务端受控类型和目标 ID。
create table user_notifications (
    id bigserial primary key,
    notification_id varchar(96) not null,
    recipient_user_id varchar(64) not null,
    type varchar(64) not null,
    actor_user_id varchar(64),
    title varchar(200) not null,
    body varchar(500) not null,
    action_type varchar(64) not null,
    action_target_id varchar(128) not null,
    dedup_key varchar(256) not null,
    status varchar(32) not null,
    invalidation_reason varchar(64),
    expires_at timestamptz,
    read_at timestamptz,
    invalidated_at timestamptz,
    trace_id varchar(128) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_user_notifications_notification_id unique (notification_id),
    constraint uk_user_notifications_dedup_key unique (dedup_key),
    constraint fk_user_notifications_recipient foreign key (recipient_user_id)
        references users(user_id) on delete cascade,
    constraint fk_user_notifications_actor foreign key (actor_user_id)
        references users(user_id) on delete set null,
    constraint ck_user_notifications_type check (type in ('SESSION_SHARED')),
    constraint ck_user_notifications_action_type check (action_type in ('SESSION_SHARE')),
    constraint ck_user_notifications_status check (status in ('ACTIVE', 'INVALIDATED')),
    constraint ck_user_notifications_invalidation check (
        (status = 'ACTIVE' and invalidated_at is null)
        or (status = 'INVALIDATED' and invalidation_reason is not null and invalidated_at is not null)
    )
);

create index idx_user_notifications_recipient_created
    on user_notifications(recipient_user_id, created_at, id);
create index idx_user_notifications_recipient_state_expiry
    on user_notifications(recipient_user_id, status, read_at, expires_at);
create index idx_user_notifications_action_target
    on user_notifications(action_type, action_target_id, recipient_user_id, status);

-- 只回填当前仍可访问的分享。成功读取审计必须晚于本次成员授权，避免旧代际误判已读。
insert into user_notifications(
    notification_id,
    recipient_user_id,
    type,
    actor_user_id,
    title,
    body,
    action_type,
    action_target_id,
    dedup_key,
    status,
    invalidation_reason,
    expires_at,
    read_at,
    invalidated_at,
    trace_id,
    created_at,
    updated_at
)
select
    'ntf_legacy_' || cast(member.id as varchar(32)),
    member.user_id,
    'SESSION_SHARED',
    share.owner_user_id,
    substring(owner.username || ' 向你分享了对话' from 1 for 200),
    substring(session_scope.title || ' · ' || case when member.can_chat then '可对话' else '只读' end from 1 for 500),
    'SESSION_SHARE',
    share.share_id,
    'SESSION_SHARE:LEGACY:' || share.share_id || ':' || member.user_id,
    'ACTIVE',
    null,
    share.expires_at,
    (
        select max(audit.occurred_at)
        from session_share_audit_events audit
        where audit.share_id = share.share_id
          and audit.actor_user_id = member.user_id
          and audit.action = 'READ_ACCESS_GRANTED'
          and audit.outcome = 'SUCCESS'
          and audit.occurred_at > member.shared_at
    ),
    null,
    substring(coalesce(share.trace_id, 'trace_notification_backfill') from 1 for 128),
    member.shared_at,
    case when member.updated_at > share.updated_at then member.updated_at else share.updated_at end
from session_share_memberships member
join session_shares share on share.share_id = member.share_id
join sessions session_scope on session_scope.session_id = share.session_id
join users owner on owner.user_id = share.owner_user_id
where member.status = 'ACTIVE'
  and share.status = 'ACTIVE'
  and share.expires_at > current_timestamp
  and session_scope.status = 'ACTIVE'
  and owner.status = 'ACTIVE';

comment on table user_notifications is '用户站内通知；保留 90 天，动作仅允许受控类型和目标 ID';
comment on column user_notifications.body is '安全展示摘要；禁止消息正文、文件路径、Token 和第三方原始错误';
comment on column user_notifications.action_target_id is '由 action_type 解释的内部目标 ID，禁止保存任意 URL';
