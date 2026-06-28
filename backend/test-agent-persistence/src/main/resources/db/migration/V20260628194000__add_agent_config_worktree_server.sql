alter table agent_config_worktrees add column linux_server_id varchar(128);

comment on column agent_config_worktrees.linux_server_id is 'Agent配置worktree所在Linux服务器ID；历史记录允许为空。';

create index idx_agent_config_worktrees_linux_server on agent_config_worktrees(linux_server_id, status, updated_at);
