-- 添加 macOS 平台支持
-- 1. 修改 common_parameters 表的约束，添加 macos 平台
ALTER TABLE common_parameters DROP CONSTRAINT IF EXISTS ck_common_parameters_platform;
ALTER TABLE common_parameters ADD CONSTRAINT ck_common_parameters_platform
    CHECK (platform IN ('windows', 'linux', 'macos', 'all'));

