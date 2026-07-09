create index if not exists idx_sessions_user_active_updated
    on sessions(created_by_user_id, status, updated_at, id);

create index if not exists idx_runs_session_trigger_user
    on runs(session_id, triggered_by_user_id);

create index if not exists idx_session_messages_session_sender
    on session_messages(session_id, sender_user_id);
