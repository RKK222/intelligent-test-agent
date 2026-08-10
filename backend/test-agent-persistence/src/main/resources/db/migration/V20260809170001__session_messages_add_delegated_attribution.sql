-- 分享会话中的代操作始终以会话所属人执行；以下字段只记录真实操作者，不改变 OpenCode 身份。
alter table session_messages add column sender_unified_auth_id varchar(255);
alter table session_messages add column sent_by_shared_user boolean not null default false;

alter table runs add column message_sender_user_id varchar(128);
alter table runs add column message_sender_unified_auth_id varchar(255);
alter table runs add column message_sent_by_shared_user boolean not null default false;
alter table runs add column active_session_id varchar(128);

alter table night_execution_tasks add column creator_user_id varchar(128);
alter table night_execution_tasks add column creator_unified_auth_id varchar(255);
alter table night_execution_tasks add column created_by_shared_user boolean not null default false;
alter table night_execution_tasks add column share_id_snapshot varchar(68);
alter table night_execution_tasks add column share_version_snapshot bigint;
alter table night_execution_tasks add column share_expires_at_snapshot timestamp with time zone;
alter table night_execution_tasks add column can_chat_snapshot boolean;

alter table run_resends add column requester_user_id varchar(128);
alter table run_resends add column requester_unified_auth_id varchar(255);
alter table run_resends add column requested_by_shared_user boolean not null default false;

-- 存量数据的触发人/所属人即真实操作者；统一认证号从当前用户目录补齐。
update session_messages sm
set sender_unified_auth_id = u.unified_auth_id
from users u
where sm.sender_user_id = u.user_id
  and sm.sender_unified_auth_id is null;

update runs r
set message_sender_user_id = r.triggered_by_user_id,
    message_sender_unified_auth_id = u.unified_auth_id
from users u
where r.triggered_by_user_id = u.user_id
  and r.message_sender_user_id is null;

update night_execution_tasks task
set creator_user_id = task.owner_user_id,
    creator_unified_auth_id = u.unified_auth_id
from users u
where task.owner_user_id = u.user_id
  and task.creator_user_id is null;

update run_resends resend
set requester_user_id = resend.owner_user_id,
    requester_unified_auth_id = u.unified_auth_id
from users u
where resend.owner_user_id = u.user_id
  and resend.requester_user_id is null;

-- 若存量库同一会话已有多个活动 Run，此更新或后续唯一索引会失败，阻止带病发布。
update runs
set active_session_id = session_id
where status in ('PENDING', 'RUNNING', 'CANCELLING');

alter table runs
    add constraint fk_runs_message_sender foreign key (message_sender_user_id) references users(user_id);
alter table runs
    add constraint fk_runs_active_session foreign key (active_session_id) references sessions(session_id);
alter table night_execution_tasks
    add constraint fk_night_execution_creator foreign key (creator_user_id) references users(user_id);
alter table night_execution_tasks
    add constraint fk_night_execution_share_snapshot foreign key (share_id_snapshot) references session_shares(share_id);
alter table run_resends
    add constraint fk_run_resends_requester foreign key (requester_user_id) references users(user_id);

create unique index uk_runs_active_session on runs(active_session_id);
create index idx_session_messages_shared_sender
    on session_messages(session_id, sent_by_shared_user, sender_user_id, created_at);
create index idx_runs_message_sender on runs(message_sender_user_id, created_at);
create index idx_night_execution_creator_status
    on night_execution_tasks(creator_user_id, status, slot_start);

comment on column session_messages.sender_unified_auth_id is '消息实际发送人的统一认证号快照';
comment on column session_messages.sent_by_shared_user is '消息是否由会话被分享人代所属人发送';
comment on column runs.triggered_by_user_id is 'Run执行所属人；分享代操作时仍为会话所属人';
comment on column runs.message_sender_user_id is '触发本轮消息的实际平台用户';
comment on column runs.message_sender_unified_auth_id is '触发本轮消息用户的统一认证号快照';
comment on column runs.message_sent_by_shared_user is '本轮消息是否由会话被分享人代发';
comment on column runs.active_session_id is '活动Run占用的会话ID；终态必须清空并由唯一索引裁决并发';
comment on column night_execution_tasks.owner_user_id is '定时任务执行所属人；分享代操作时为会话所属人';
comment on column night_execution_tasks.creator_user_id is '定时任务实际创建人';
comment on column night_execution_tasks.creator_unified_auth_id is '定时任务实际创建人的统一认证号快照';
comment on column night_execution_tasks.created_by_shared_user is '任务是否由会话被分享人代创建';
comment on column night_execution_tasks.share_id_snapshot is '创建任务时的分享ID授权快照';
comment on column night_execution_tasks.share_version_snapshot is '创建任务时的分享乐观锁版本快照';
comment on column night_execution_tasks.share_expires_at_snapshot is '创建任务时的分享失效时间快照';
comment on column night_execution_tasks.can_chat_snapshot is '创建任务时是否具有可对话权限';
comment on column run_resends.owner_user_id is '撤回重发的执行所属人';
comment on column run_resends.requester_user_id is '撤回重发的实际发起人';
comment on column run_resends.requester_unified_auth_id is '撤回重发发起人的统一认证号快照';
comment on column run_resends.requested_by_shared_user is '撤回重发是否由会话被分享人代发起';
