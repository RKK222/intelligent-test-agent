alter table application_workspaces
    add column enabled boolean not null default true;

comment on column application_workspaces.enabled is '是否在工作空间切换入口中启用';
