-- 通用参数：引用资产根目录与规格驱动标准目录名称。
-- OPENCODE_REFERENCES_DIR 统一引用 SYS_DATA_ROOT_DIR，作为引用资产（如规格文档、参考素材）的根目录，
-- platform=all，运行态由 CommonParameterReferenceResolver 按当前/目标平台作为解析上下文展开 SYS_DATA_ROOT_DIR 引用
-- （SYS_DATA_ROOT_DIR 仅有 linux/windows/macos 平台行，无 all 行，复用 all 行引用平台参数的解析能力）。
-- 该参数为部署/初始化参数，editable=false，不允许前端修改，修改将影响系统运行。
-- REFERENCES_SDD_FOLDER_NAMES 为规格驱动（SDD）场景下识别规格目录的名称清单（逗号分隔，小写），
-- platform=all，editable=true，允许前端按团队约定调整目录名。
-- 注意：Flyway 默认把美元符紧跟大括号的文本当作占位符替换，会因找不到值而解析失败；
-- 因此 OPENCODE_REFERENCES_DIR 的值用 '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/references' 拼接，
-- 使 SQL 文本中不出现该占位符序列，DB 实际存储美元符加大括号包裹的 SYS_DATA_ROOT_DIR 字面量，
-- 由通用参数解析器在运行态按当前平台展开。
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
values
    ('param_opencode_references_dir_all', 'OPENCODE_REFERENCES_DIR', '引用资产根目录', '$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/references', 'all', false, current_timestamp, current_timestamp),
    ('param_references_sdd_folder_names_all', 'REFERENCES_SDD_FOLDER_NAMES', '规格驱动标准目录名称', 'docs,spec', 'all', true, current_timestamp, current_timestamp);
