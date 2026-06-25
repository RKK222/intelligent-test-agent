-- 清理 opencode 进程管理表中的脏数据，并添加 CHECK 约束确保 ID 前缀正确。
-- 这些约束与领域对象构造器中的校验一致，避免读取时因格式不符导致查询失败。
--
-- 各 ID 格式要求：
-- - linux_server_id: IPv4 地址格式（如 127.0.0.1）
-- - backend_process_id: bjp_ 前缀
-- - container_id: 非空文本（无固定前缀）
-- - manager_id: mgr_ 前缀
-- - process_id: ocp_ 前缀

-- 清理不符合前缀规则的记录
delete from opencode_manager_backend_connections
where manager_id not like 'mgr_%'
   or backend_process_id not like 'bjp_%';

delete from opencode_container_managers
where manager_id not like 'mgr_%';

delete from opencode_server_processes
where process_id not like 'ocp_%';

delete from user_opencode_process_bindings
where process_id not like 'ocp_%';

delete from backend_java_processes
where backend_process_id not like 'bjp_%';

-- 注意：linux_servers.linux_server_id 要求是 IPv4 地址格式，由领域对象校验
-- 注意：opencode_containers.container_id 只要求非空，无固定前缀要求

-- 添加 CHECK 约束（PostgreSQL 语法）
alter table backend_java_processes
    add constraint chk_backend_java_processes_id_prefix check (backend_process_id like 'bjp_%');

alter table opencode_container_managers
    add constraint chk_opencode_container_managers_id_prefix check (manager_id like 'mgr_%');

alter table opencode_server_processes
    add constraint chk_opencode_server_processes_id_prefix check (process_id like 'ocp_%');
