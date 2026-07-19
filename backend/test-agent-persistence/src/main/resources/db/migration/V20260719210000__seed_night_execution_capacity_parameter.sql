-- 夜间任务每个北京时间 15 分钟启动时段的全局任务数上限。
-- 该参数允许超级管理员在线修改，运行时经现有通用参数广播刷新各 Java 实例内存。
insert into common_parameters(
    parameter_id,
    parameter_english,
    parameter_chinese,
    parameter_value,
    platform,
    editable,
    created_at,
    updated_at
)
values (
    'param_night_execution_slot_capacity_all',
    'NIGHT_EXECUTION_SLOT_CAPACITY',
    '夜间任务每时段任务上限',
    '20',
    'all',
    true,
    current_timestamp,
    current_timestamp
);
