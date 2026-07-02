-- linux_server_id 语义从服务器 IPv4 调整为稳定服务器身份；当前未投产，不提供旧 IP 数据迁移。
alter table linux_servers alter column linux_server_id type varchar(128);
alter table backend_java_processes alter column linux_server_id type varchar(128);
alter table opencode_containers alter column linux_server_id type varchar(128);
alter table opencode_container_managers alter column linux_server_id type varchar(128);
alter table opencode_server_processes alter column linux_server_id type varchar(128);
alter table user_opencode_process_bindings alter column linux_server_id type varchar(128);

comment on column linux_servers.linux_server_id is 'Linux服务器稳定身份，优先来自TEST_AGENT_LINUX_SERVER_ID，缺失时使用Java主机名';
comment on column backend_java_processes.linux_server_id is '后端Java所属Linux服务器稳定身份';
comment on column opencode_containers.linux_server_id is 'opencode容器所属Linux服务器稳定身份';
comment on column opencode_container_managers.linux_server_id is 'opencode manager所属Linux服务器稳定身份';
comment on column opencode_server_processes.linux_server_id is '用户opencode进程所属Linux服务器稳定身份';
comment on column user_opencode_process_bindings.linux_server_id is '用户opencode进程绑定的Linux服务器稳定身份';
comment on column opencode_server_processes.base_url is '用户opencode server网络访问地址，host来自服务器advertised host，不要求等于linux_server_id';
