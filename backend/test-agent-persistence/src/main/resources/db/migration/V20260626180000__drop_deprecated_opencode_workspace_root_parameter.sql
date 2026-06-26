-- 删除废弃参数 OPENCODE_WORKSPACE_ROOT：生产代码无消费方，
-- 子目录参数 OPENCODE_APP_WORKSPACE_ROOT / OPENCODE_PERSONAL_WORKTREE_ROOT 已在 common_parameters 独立维护全路径。
delete from common_parameters where parameter_english = 'OPENCODE_WORKSPACE_ROOT';
