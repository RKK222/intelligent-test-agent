-- 清理 opencode_manager_backend_connections 中引用不存在 backend_process_id 的孤立行。
-- 每次后端重启会生成新的 bjp_xxx ID，旧进程记录可能已不存在但连接行残留，
-- 导致 FK 约束冲突阻断启动。
delete from opencode_manager_backend_connections
where backend_process_id not in (
    select backend_process_id from backend_java_processes
);
