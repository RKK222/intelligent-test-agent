-- 为通用参数表新增 editable 列，标识是否允许在前端修改参数值。
-- true：允许在通用参数管理页面修改参数值；false：只读（部署/初始化参数，修改将影响系统正常运行）。
-- 纯加列带默认值 false，存量行自动为只读；仅放行运行时可调的两个参数。
alter table common_parameters
    add column editable boolean not null default false;

comment on column common_parameters.editable is
    '是否允许在前端修改参数值：true=可修改，false=只读（部署/初始化参数，修改将影响系统正常运行）';

-- 仅 OPENCODE_MANAGER_MAX_PROCESSES 与 OPENCODE_PUBLIC_AGENT_GIT_URL 允许前端修改。
update common_parameters
set editable = true
where parameter_english in ('OPENCODE_MANAGER_MAX_PROCESSES', 'OPENCODE_PUBLIC_AGENT_GIT_URL');
