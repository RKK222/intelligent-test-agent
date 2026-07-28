-- 历史 V17 本地开发拓扑 fixture：仅用于验证后续清理 migration，不进入生产 Flyway。
insert into users (
    user_id, unified_auth_id, username, password_hash,
    status, created_at, updated_at
) values (
    'usr_test_dev', 'auth_test_dev', '888888888', 'hash',
    'ACTIVE', now(), now()
);

insert into linux_servers (
    linux_server_id, name, status, capacity_summary_json,
    last_heartbeat_at, trace_id, created_at, updated_at
) values (
    '127.0.0.1', 'local-opencode-host', 'READY', '{}',
    now(), 'trace_seed_local_opencode_machine', now(), now()
);

insert into opencode_containers (
    container_id, linux_server_id, container_name,
    port_start, port_end, max_processes, current_processes,
    status, last_heartbeat_at, trace_id, created_at, updated_at
) values (
    'ctr_local_4096', '127.0.0.1', 'local-opencode',
    4096, 4096, 1, 1,
    'READY', now(), 'trace_seed_local_opencode_machine', now(), now()
);

insert into opencode_container_managers (
    manager_id, container_id, linux_server_id, protocol_version,
    connection_status, capabilities_json, last_heartbeat_at,
    trace_id, created_at, updated_at
) values (
    'mgr_local_4096', 'ctr_local_4096', '127.0.0.1', 'opencode-manager.v1',
    'CONNECTED', '{}', now(),
    'trace_seed_local_opencode_machine', now(), now()
);

insert into opencode_server_processes (
    process_id, user_id, linux_server_id, container_id, port, pid, base_url,
    status, session_path, config_path, started_at, last_health_check_at,
    health_message, trace_id, created_at, updated_at
) values (
    'ocp_local_user_dev', 'usr_test_dev', '127.0.0.1', 'ctr_local_4096',
    4096, null, 'http://127.0.0.1:4096',
    'RUNNING', '/data/opencode/session/4096', '/data/opencode/.config/opencode/',
    now(), now(), 'seeded by test fixture',
    'trace_seed_local_opencode_machine', now(), now()
);

insert into user_opencode_process_bindings (
    user_id, agent_id, process_id, linux_server_id, port,
    status, trace_id, created_at, updated_at
) values (
    'usr_test_dev', 'opencode', 'ocp_local_user_dev', '127.0.0.1', 4096,
    'ACTIVE', 'trace_seed_local_opencode_machine', now(), now()
);
