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
-- - 外键无 on delete cascade，必须严格按 子→父 依赖顺序逐层删除：
--     opencode_manager_backend_connections (引用 manager / backend_process)
--       → user_opencode_process_bindings (引用 process)
--       → opencode_server_processes (引用 container)         ← 必须先于删 container
--       → opencode_container_managers (引用 container)        ← 必须先于删 container
--       → opencode_containers
--       → backend_java_processes
--       → linux_servers
-- - 待删容器集合物化成临时表 _loopback_containers，作为"删进程/删管理器/删容器"的共同锚点，
--   保证引用 loopback 容器的进程一定先被删除，避免删容器时触发 fk_opencode_server_processes_container
--   外键冲突（历史库可能存在 linux_server_id 非 127.0.0.1 但 container_id 仍指向 loopback 容器
--   的脏进程，例如 container_id='ctr_local_opencode' 这类历史命名，单凭 linux_server_id 判定会漏删）。
-- - opencode_manager_backend_connections 无 linux_server_id 列，按其关联的 manager/backend
--   是否属于 loopback 体系来定位待删连接行。
-- - 全部使用 delete where，重复执行不会报错。

-- 0. 物化 loopback 容器 ID 集合：直接属于 127.0.0.1 服务器的容器。
create temporary table _loopback_containers(container_id text) on commit drop;
insert into _loopback_containers(container_id)
select container_id from opencode_containers where linux_server_id = '127.0.0.1';

-- 1. 管理器↔后端连接（引用 manager / backend_process，叶子节点先删）
delete from opencode_manager_backend_connections
 where manager_id in (
           select manager_id from opencode_container_managers
            where linux_server_id = '127.0.0.1'
               or container_id in (select container_id from _loopback_containers)
       )
    or backend_process_id in (select backend_process_id from backend_java_processes where linux_server_id = '127.0.0.1');

-- 2. 用户↔进程绑定（引用 process，先于删进程）
delete from user_opencode_process_bindings
 where linux_server_id = '127.0.0.1'
    or process_id in (
           select process_id from opencode_server_processes
            where linux_server_id = '127.0.0.1'
               or container_id in (select container_id from _loopback_containers)
       );

-- 3. opencode server 进程（引用 container —— 必须在删 container 之前删，否则触发
--    fk_opencode_server_processes_container 外键冲突）
delete from opencode_server_processes
 where linux_server_id = '127.0.0.1'
    or container_id in (select container_id from _loopback_containers);

-- 4. 容器管理器（引用 container —— 必须在删 container 之前删）
delete from opencode_container_managers
 where linux_server_id = '127.0.0.1'
    or container_id in (select container_id from _loopback_containers);

-- 5. 容器
delete from opencode_containers
 where linux_server_id = '127.0.0.1'
    or container_id in (select container_id from _loopback_containers);

-- 6. 后端 Java 进程
delete from backend_java_processes where linux_server_id = '127.0.0.1';

-- 7. Linux 服务器（父表，最后删）
delete from linux_servers where linux_server_id = '127.0.0.1';
