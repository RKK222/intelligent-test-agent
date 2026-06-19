alter table sessions
    add column opencode_session_id varchar(128);

alter table sessions
    add column opencode_execution_node_id varchar(128);

alter table sessions
    add constraint fk_sessions_opencode_execution_node
        foreign key (opencode_execution_node_id) references execution_nodes(execution_node_id);

alter table sessions
    add constraint chk_sessions_opencode_mapping
        check (
            (opencode_session_id is null and opencode_execution_node_id is null)
            or (opencode_session_id is not null and opencode_execution_node_id is not null)
        );

create unique index uk_sessions_opencode_session_id on sessions(opencode_session_id);
create index idx_sessions_opencode_execution_node on sessions(opencode_execution_node_id);
