-- 本地开发种子：为默认开发用户 usr_test_dev（用户名 888888888）预置一个
-- "本地 opencode 机器"（Linux 服务器 + 容器 + 管理进程）和用户进程绑定，
-- 让前端登录后能直接看到 READY 状态，baseUrl 指向本机 127.0.0.1:4096。
--
-- 约束：
-- - Linux 服务器 ID 必须是 IPv4 格式（领域校验），127.0.0.1 与默认 TEST_AGENT_LINUX_SERVER_ID 一致。
-- - 容器端口范围 4096-4096 固定一个端口，max_processes=1，current_processes=1 表示该进程已占满。
-- - manager_id 必须以 mgr_ 开头，process_id 必须以 ocp_ 开头。
-- - 用户绑定表唯一约束 (user_id, agent_id)='(usr_test_dev, opencode)'。
-- - 依赖 V14 表结构、V5 默认开发用户。
-- - OpencodeManagerBackendConnection 行的 backend_process_id 形如 bjp_xxx，由后端
--   BackendJavaProcessLifecycleService 在启动心跳时根据本实例 ID 写入；migration 不写死。
-- - 全部插入使用 where not exists / where exists 保护，重复执行不会破坏数据。

insert into linux_servers (
    linux_server_id, name, status, capacity_summary_json,
    last_heartbeat_at, trace_id, created_at, updated_at
)
select '127.0.0.1', 'local-opencode-host', 'READY', '{}',
       now(), 'trace_seed_local_opencode_machine', now(), now()
where not exists (
    select 1 from linux_servers where linux_server_id = '127.0.0.1'
);

insert into opencode_containers (
    container_id, linux_server_id, container_name,
    port_start, port_end, max_processes, current_processes,
    status, last_heartbeat_at, trace_id, created_at, updated_at
)
select 'ctr_local_4096', '127.0.0.1', 'local-opencode',
       4096, 4096, 1, 1,
       'READY', now(), 'trace_seed_local_opencode_machine', now(), now()
where not exists (
    select 1 from opencode_containers where container_id = 'ctr_local_4096'
);

insert into opencode_container_managers (
    manager_id, container_id, linux_server_id, protocol_version,
    connection_status, capabilities_json, last_heartbeat_at,
    trace_id, created_at, updated_at
)
select 'mgr_local_4096', 'ctr_local_4096', '127.0.0.1', 'opencode-manager.v1',
       'CONNECTED', '{}',
       now(), 'trace_seed_local_opencode_machine', now(), now()
where not exists (
    select 1 from opencode_container_managers where manager_id = 'mgr_local_4096'
);

-- 用户专属 opencode 进程：直接指向本机已运行的 opencode server（127.0.0.1:4096）。
-- baseUrl 必须等于 'http://' || linux_server_id || ':' || port，端口 4096 与本地 opencode server 监听端口一致。
insert into opencode_server_processes (
    process_id, user_id, linux_server_id, container_id, port, pid, base_url,
    status, session_path, config_path, started_at, last_health_check_at,
    health_message, trace_id, created_at, updated_at
)
select 'ocp_local_user_dev', 'usr_test_dev', '127.0.0.1', 'ctr_local_4096',
       4096, null, 'http://127.0.0.1:4096',
       'RUNNING', '/data/opencode/session/4096', '/data/opencode/.config/opencode/',
       now(), now(),
       'seeded by V17', 'trace_seed_local_opencode_machine', now(), now()
where exists (select 1 from users where user_id = 'usr_test_dev')
  and not exists (
      select 1 from opencode_server_processes where process_id = 'ocp_local_user_dev'
  );

-- 当前用户到 opencode agent 的绑定。
insert into user_opencode_process_bindings (
    user_id, agent_id, process_id, linux_server_id, port,
    status, trace_id, created_at, updated_at
)
select 'usr_test_dev', 'opencode', 'ocp_local_user_dev', '127.0.0.1', 4096,
       'ACTIVE', 'trace_seed_local_opencode_machine', now(), now()
where exists (select 1 from users where user_id = 'usr_test_dev')
  and not exists (
      select 1 from user_opencode_process_bindings
      where user_id = 'usr_test_dev' and agent_id = 'opencode'
  );
