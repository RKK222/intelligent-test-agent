-- 清理 V17 预置的 127.0.0.1 幽灵 Linux 服务器种子数据。
--
-- 背景：
-- - V17 曾为本地开发预置一整套 linux_server_id='127.0.0.1' 的种子（服务器/容器/管理进程/
--   用户进程/绑定），与当时硬编码的 TEST_AGENT_LINUX_SERVER_ID 默认值 127.0.0.1 配合。
-- - 后端改为启动时自动探测真实内网 IPv4（LinuxServerIpResolver），心跳自注册会写入真实 IP
--   的服务器行；旧的 127.0.0.1 种子不再被刷新，成为运行管理列表里的"幽灵服务器"，需清理。
-- - 本地开发指向 127.0.0.1:4096 本机 opencode server 的连通性由 local-direct 短路模式承载，
--   不依赖该种子行。
--
-- 约束：
-- - 外键无 on delete cascade，必须按子→父顺序删除。
-- - opencode_manager_backend_connections 无 linux_server_id 列，按其关联的 manager/backend
--   是否属于 127.0.0.1 服务器来定位待删连接行。
-- - 全部使用 delete where，重复执行不会报错。

delete from opencode_manager_backend_connections
 where manager_id in (select manager_id from opencode_container_managers where linux_server_id = '127.0.0.1')
    or backend_process_id in (select backend_process_id from backend_java_processes where linux_server_id = '127.0.0.1');

delete from user_opencode_process_bindings where linux_server_id = '127.0.0.1';

delete from opencode_server_processes where linux_server_id = '127.0.0.1';

delete from opencode_container_managers where linux_server_id = '127.0.0.1';

delete from opencode_containers where linux_server_id = '127.0.0.1';

delete from backend_java_processes where linux_server_id = '127.0.0.1';

delete from linux_servers where linux_server_id = '127.0.0.1';
