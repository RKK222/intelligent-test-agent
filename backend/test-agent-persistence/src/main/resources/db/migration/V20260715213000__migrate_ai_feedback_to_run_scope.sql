-- 将可关联 Run 的历史消息反馈迁移为整轮反馈；无法关联的旧记录继续保留消息口径。
update ai_message_feedbacks f
set run_id = (
    select sm.run_id
    from session_messages sm
    where sm.message_id = f.message_id
)
where f.run_id is null
  and f.message_id is not null
  and exists (
      select 1 from session_messages sm
      where sm.message_id = f.message_id
        and sm.run_id is not null
  );

-- 同一用户、同一 Run 只保留最后更新的一条，避免新增唯一约束失败。
delete from ai_message_feedbacks f
where f.run_id is not null
  and exists (
      select 1
      from ai_message_feedbacks newer
      where newer.user_id = f.user_id
        and newer.run_id = f.run_id
        and (
            newer.updated_at > f.updated_at
            or (newer.updated_at = f.updated_at and newer.id > f.id)
        )
  );

alter table ai_message_feedbacks alter column message_id drop not null;
alter table ai_message_feedbacks add constraint uk_ai_feedback_user_run unique (user_id, run_id);
alter table ai_message_feedbacks add constraint chk_ai_feedback_reference
    check (run_id is not null or message_id is not null);

comment on table ai_message_feedbacks is 'AI整轮回复满意度反馈表，兼容历史消息反馈';
comment on column ai_message_feedbacks.run_id is '新反馈的业务定位Run ID';
comment on column ai_message_feedbacks.message_id is '可空的历史消息反馈来源ID';
