create or replace function analytics_enqueue_event()
returns trigger
language plpgsql
as $$
declare
    row_json jsonb;
    actual_user_id varchar(128);
    snapshot_username varchar(128);
    snapshot_organization varchar(255);
    snapshot_rd_department varchar(255);
    snapshot_department varchar(255);
    analytics_event_id varchar(256);
    analytics_event_version bigint := coalesce((row_json ->> 'status_version')::bigint, 1);
    analytics_occurred_at timestamptz;
    analytics_payload jsonb;
begin
    row_json := case when tg_op = 'DELETE' then to_jsonb(old) else to_jsonb(new) end;
    if tg_table_name = 'users' then
        actual_user_id := row_json ->> 'user_id';
        analytics_event_id := 'user-dimension:' || actual_user_id;
        analytics_event_version := greatest(1, (extract(epoch from clock_timestamp()) * 1000000)::bigint);
        analytics_occurred_at := clock_timestamp();
        analytics_payload := jsonb_build_object(
            'eventType', 'USER_DIMENSION',
            'status', case when tg_op = 'DELETE' then 'DELETED' else row_json ->> 'status' end
        );
    elsif tg_table_name = 'sessions' then
        actual_user_id := new.created_by_user_id;
        analytics_event_id := 'session-state:' || new.session_id;
        analytics_event_version := greatest(1, (extract(epoch from new.created_at) * 1000)::bigint);
        analytics_occurred_at := new.created_at;
        analytics_payload := jsonb_build_object(
            'eventType', 'SESSION_STATE', 'sessionId', new.session_id,
            'workspaceId', new.workspace_id, 'sessionCount', 1,
            'activeSessionCount', 0, 'emptySessionCount', 1, 'continuousSessionCount', 0
        );
    elsif tg_table_name = 'user_login_logs' then
        if new.login_result <> 'SUCCESS' then
            return new;
        end if;
        actual_user_id := new.user_id;
        analytics_event_id := 'login:' || new.log_id;
        analytics_occurred_at := new.login_at;
        analytics_payload := jsonb_build_object('eventType', 'LOGIN', 'loginCount', 1);
    elsif tg_table_name = 'session_messages' then
        actual_user_id := coalesce(row_json ->> 'sender_user_id', (
            select s.created_by_user_id from sessions s where s.session_id = new.session_id
        ));
        analytics_event_id := 'message:' || new.message_id;
        analytics_occurred_at := new.created_at;
        analytics_payload := jsonb_build_object(
            'eventType', 'MESSAGE',
            'sessionId', new.session_id,
            'runId', row_json ->> 'run_id',
            'userMessageCount', case when upper(new.role) = 'USER'
                and coalesce((select r.storage_mode from runs r where r.run_id=new.run_id), 'LEGACY_FULL') != 'REDIS_SUMMARY'
                then 1 else 0 end,
            'assistantMessageCount', case when upper(new.role) = 'ASSISTANT'
                and coalesce((select r.storage_mode from runs r where r.run_id=new.run_id), 'LEGACY_FULL') != 'REDIS_SUMMARY'
                then 1 else 0 end
        );
        insert into analytics_event_outbox(
            event_id, event_version, source_type, aggregate_id, occurred_at,
            payload_json, available_at, created_at
        )
        select
            'session-state:' || s.session_id,
            greatest(1, (extract(epoch from new.created_at) * 1000)::bigint),
            'SESSION_STATE', s.session_id, s.created_at,
            (jsonb_build_object(
                'eventType', 'SESSION_STATE', 'sessionId', s.session_id, 'workspaceId', s.workspace_id,
                'sessionCount', 1, 'activeSessionCount', 1, 'emptySessionCount', 0,
                'continuousSessionCount', case when (
                    select count(*) from session_messages sm where sm.session_id=s.session_id and sm.role='USER'
                ) >= 2 then 1 else 0 end,
                'userId', coalesce(new.sender_user_id, s.created_by_user_id),
                'username', su.username, 'organization', su.organization,
                'rdDepartment', su.rd_department, 'department', su.department,
                'attributionMode', 'EVENT_SNAPSHOT'
            ))::text,
            clock_timestamp(), clock_timestamp()
        from sessions s
        left join users su on su.user_id=coalesce(new.sender_user_id, s.created_by_user_id)
        where s.session_id=new.session_id
        on conflict(event_id,event_version) do nothing;
    elsif tg_table_name = 'runs' then
        actual_user_id := coalesce(row_json ->> 'message_sender_user_id', row_json ->> 'triggered_by_user_id');
        analytics_occurred_at := case
            when tg_op = 'INSERT' then new.created_at
            else new.updated_at
        end;
        if tg_op = 'INSERT' then
            analytics_event_id := 'run-started:' || new.run_id;
            analytics_payload := jsonb_build_object(
                'eventType', 'RUN_STARTED', 'runId', new.run_id, 'sessionId', new.session_id,
                'workspaceId', row_json ->> 'workspace_id', 'agentId', row_json ->> 'agent_id',
                'modelId', row_json ->> 'model_id',
                'capabilityType', 'AGENT', 'capabilityName', coalesce(row_json ->> 'agent_id', 'default'),
                'capabilityStatus', 'STARTED', 'runCount', 1,
                'userMessageCount', case when row_json ->> 'storage_mode' = 'REDIS_SUMMARY' then 1 else 0 end
            );
        elsif upper(new.status) in ('SUCCEEDED', 'FAILED', 'CANCELLED')
                and upper(coalesce(old.status, '')) not in ('SUCCEEDED', 'FAILED', 'CANCELLED') then
            analytics_event_id := 'run-terminal:' || new.run_id;
            analytics_payload := jsonb_build_object(
                'eventType', 'RUN_TERMINAL', 'runId', new.run_id, 'sessionId', new.session_id,
                'workspaceId', row_json ->> 'workspace_id', 'agentId', row_json ->> 'agent_id',
                'modelId', row_json ->> 'model_id',
                'capabilityName', coalesce(row_json ->> 'agent_id', 'default'),
                'status', new.status,
                'succeededRunCount', case when upper(new.status) = 'SUCCEEDED' then 1 else 0 end,
                'failedRunCount', case when upper(new.status) = 'FAILED' then 1 else 0 end,
                'cancelledRunCount', case when upper(new.status) = 'CANCELLED' then 1 else 0 end,
                'activeTerminationCount', case when upper(new.status) = 'CANCELLED' then 1 else 0 end,
                'validInteractionCount', 1,
                'tokensInput', coalesce((row_json ->> 'tokens_input')::bigint, 0),
                'tokensOutput', coalesce((row_json ->> 'tokens_output')::bigint, 0),
                'tokensReasoning', coalesce((row_json ->> 'tokens_reasoning')::bigint, 0),
                'tokensCacheRead', coalesce((row_json ->> 'tokens_cache_read')::bigint, 0),
                'tokensCacheWrite', coalesce((row_json ->> 'tokens_cache_write')::bigint, 0),
                'assistantMessageCount', case when row_json ->> 'storage_mode' = 'REDIS_SUMMARY' then 1 else 0 end,
                'durationMs', greatest(0, (extract(epoch from (new.updated_at - new.created_at)) * 1000)::bigint)
            );
        else
            return new;
        end if;
    elsif tg_table_name = 'run_events' then
        if new.type not in (
            'tool.started', 'tool.finished', 'session.child.discovered',
            'diff.proposed', 'diff.accepted', 'diff.rejected'
        ) then
            return new;
        end if;
        select coalesce(r.message_sender_user_id, r.triggered_by_user_id)
          into actual_user_id from runs r where r.run_id = new.run_id;
        analytics_event_id := 'run-event:' || new.event_id;
        analytics_event_version := greatest(1, (extract(epoch from new.occurred_at) * 1000)::bigint);
        analytics_occurred_at := new.occurred_at;
        analytics_payload := jsonb_strip_nulls(jsonb_build_object(
            'eventType', upper(replace(new.type, '.', '_')), 'runId', new.run_id,
            'sessionId', row_json ->> 'session_id',
            'parentSessionId', row_json ->> 'parent_session_id',
            'taskCallId', row_json ->> 'task_call_id',
            'isChildSession', coalesce((row_json ->> 'is_child_session')::boolean, false),
            'toolName', coalesce(new.payload_json::jsonb ->> 'tool', new.payload_json::jsonb ->> 'toolName'),
            'callId', coalesce(new.payload_json::jsonb ->> 'callID', new.payload_json::jsonb ->> 'callId'),
            'title', case when new.type in ('tool.started', 'tool.finished')
                    and lower(coalesce(new.payload_json::jsonb ->> 'tool', new.payload_json::jsonb ->> 'toolName')) = 'skill'
                then new.payload_json::jsonb ->> 'title' end,
            'agentName', case when new.type = 'session.child.discovered' then
                coalesce(new.payload_json::jsonb ->> 'agentName', new.payload_json::jsonb ->> 'agent') end,
            'diffProposedCount', case when new.type = 'diff.proposed' then 1 else 0 end,
            'diffAcceptedCount', case when new.type = 'diff.accepted' then 1 else 0 end,
            'diffRejectedCount', case when new.type = 'diff.rejected' then 1 else 0 end
        ));
    elsif tg_table_name = 'ai_message_feedbacks' then
        actual_user_id := new.user_id;
        analytics_event_id := 'feedback:' || new.feedback_id;
        analytics_event_version := greatest(1, (extract(epoch from new.updated_at) * 1000)::bigint);
        analytics_occurred_at := new.updated_at;
        analytics_payload := jsonb_build_object(
            'eventType', 'FEEDBACK', 'feedbackId', new.feedback_id, 'sessionId', new.session_id,
            'runId', new.run_id, 'messageId', new.message_id, 'rating', new.rating,
            'reasonCode', new.reason_code
        );
    else
        return new;
    end if;

    if tg_table_name = 'users' then
        snapshot_username := row_json ->> 'username';
        snapshot_organization := row_json ->> 'organization';
        snapshot_rd_department := row_json ->> 'rd_department';
        snapshot_department := row_json ->> 'department';
    else
        select u.username, u.organization, u.rd_department, u.department
          into snapshot_username, snapshot_organization, snapshot_rd_department, snapshot_department
          from users u where u.user_id = actual_user_id;
    end if;

    analytics_payload := analytics_payload || jsonb_build_object(
        'userId', actual_user_id,
        'username', snapshot_username,
        'organization', snapshot_organization,
        'rdDepartment', snapshot_rd_department,
        'department', snapshot_department,
        'attributionMode', 'EVENT_SNAPSHOT'
    );

    insert into analytics_event_outbox(
        event_id, event_version, source_type, aggregate_id, occurred_at,
        payload_json, available_at, created_at
    ) values (
        analytics_event_id, analytics_event_version, upper(tg_table_name),
        coalesce(row_json ->> 'run_id', row_json ->> 'message_id', row_json ->> 'log_id',
                 row_json ->> 'feedback_id', row_json ->> 'user_id', row_json ->> 'session_id',
                 row_json ->> 'event_id', analytics_event_id),
        analytics_occurred_at, analytics_payload::text, clock_timestamp(), clock_timestamp()
    ) on conflict (event_id, event_version) do nothing;
    return new;
end;
$$;

create trigger trg_analytics_user_dimension_outbox
after insert or update or delete on users for each row execute function analytics_enqueue_event();
create trigger trg_analytics_login_outbox
after insert on user_login_logs for each row execute function analytics_enqueue_event();
create trigger trg_analytics_session_outbox
after insert on sessions for each row execute function analytics_enqueue_event();
create trigger trg_analytics_message_outbox
after insert on session_messages for each row execute function analytics_enqueue_event();
create trigger trg_analytics_run_outbox
after insert or update on runs for each row execute function analytics_enqueue_event();
create trigger trg_analytics_run_event_outbox
after insert on run_events for each row execute function analytics_enqueue_event();
create trigger trg_analytics_feedback_outbox
after insert or update on ai_message_feedbacks for each row execute function analytics_enqueue_event();
