alter table sessions
    add column pinned boolean not null default false;

create index idx_sessions_active_pinned_updated on sessions(status, pinned, updated_at, id);
create index idx_sessions_workspace_active_pinned_updated on sessions(workspace_id, status, pinned, updated_at, id);
