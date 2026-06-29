-- 通用参数：系统数据根目录。
-- 该参数作为跨模块统一的数据根目录来源，按当前平台读取；macOS 保留 $HOME 字面值，由通用参数解析器在运行态展开。
insert into common_parameters(parameter_id, parameter_english, parameter_chinese, parameter_value, platform, created_at, updated_at)
values
    ('param_sys_data_root_dir_macos', 'SYS_DATA_ROOT_DIR', '系统数据根目录', '$HOME/.testagent', 'macos', current_timestamp, current_timestamp),
    ('param_sys_data_root_dir_linux', 'SYS_DATA_ROOT_DIR', '系统数据根目录', '/data/.testagent', 'linux', current_timestamp, current_timestamp),
    ('param_sys_data_root_dir_windows', 'SYS_DATA_ROOT_DIR', '系统数据根目录', 'D:/data/.testagent', 'windows', current_timestamp, current_timestamp);
