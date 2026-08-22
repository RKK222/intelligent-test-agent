alter table opencode_server_processes
    add column observability_generation varchar(128);

comment on column opencode_server_processes.observability_generation is
    '当前OpenCode进程Observability启动代次；与会被状态查询更新的业务trace_id分离';
