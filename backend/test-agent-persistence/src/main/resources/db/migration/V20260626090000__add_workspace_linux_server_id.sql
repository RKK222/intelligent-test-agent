alter table workspaces add column linux_server_id varchar(128);

create index idx_workspaces_linux_server_id on workspaces(linux_server_id);
