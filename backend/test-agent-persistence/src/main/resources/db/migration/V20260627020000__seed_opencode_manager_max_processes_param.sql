-- 通用参数：opencode manager 最大并发进程数（全局，platform=all）。
-- manager 连接后端时由后端下发该值并热更新（按自身端口池 clamp），前端修改后经 WS 控制面广播给所有 manager。
-- 在此之前该上限只能通过 env OPENCODE_MANAGER_MAX_PROCESSES 启动时设定，迁移将其纳入在线可调的通用参数。
insert into common_parameters(parameter_id, parameter_english, parameter_chinese, parameter_value, platform, created_at, updated_at)
values ('param_opencode_manager_max_processes', 'OPENCODE_MANAGER_MAX_PROCESSES', 'opencode manager 最大进程数', '8', 'all', current_timestamp, current_timestamp);
