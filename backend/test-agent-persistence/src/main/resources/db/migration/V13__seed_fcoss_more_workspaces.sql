-- 开发期种子数据扩展：在 F-COSS 应用下追加几个工作空间模板 + 初始版本，
-- 给前端「+新增版本」/ 工作空间选择器提供更多可选项。
-- 仅当 V10 的 app_fcoss / repo_fcoss_main / 888888888 用户都存在时才插入。
-- 全部使用 where not exists 保护，重复执行迁移不会破坏数据。

-- 工作空间模板 1：F-COSS 移动端（mobile 分支，src/mobile 目录）
insert into application_workspaces (workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at)
select 'awp_fcoss_mobile', 'app_fcoss', 'repo_fcoss_main', 'mobile', 'src/mobile', 'F-COSS 移动端', now(), now()
where exists (select 1 from applications where app_id = 'app_fcoss')
  and exists (select 1 from code_repositories where repository_id = 'repo_fcoss_main')
  and not exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_mobile');

-- 工作空间模板 2：F-COSS 数据同步（sync 分支，sync 目录）
insert into application_workspaces (workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at)
select 'awp_fcoss_sync', 'app_fcoss', 'repo_fcoss_main', 'sync', 'sync', 'F-COSS 数据同步', now(), now()
where exists (select 1 from applications where app_id = 'app_fcoss')
  and exists (select 1 from code_repositories where repository_id = 'repo_fcoss_main')
  and not exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_sync');

-- 工作空间模板 3：F-COSS 报表（report 分支，reports 目录）
insert into application_workspaces (workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at)
select 'awp_fcoss_report', 'app_fcoss', 'repo_fcoss_main', 'report', 'reports', 'F-COSS 报表', now(), now()
where exists (select 1 from applications where app_id = 'app_fcoss')
  and exists (select 1 from code_repositories where repository_id = 'repo_fcoss_main')
  and not exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_report');

-- 运行态工作空间：每个模板一个初始版本（yyyyMMdd，与 V10 保持一致）
insert into workspaces (workspace_id, name, root_path, status, trace_id, created_at, updated_at)
select 'wrk_fcoss_mobile_20260705', 'F-COSS 移动端-20260705', '/tmp/test-agent/fcoss/mobile/20260705', 'ACTIVE', 'trace_seed_fcoss_mobile_20260705', now(), now()
where not exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_mobile_20260705');

insert into workspaces (workspace_id, name, root_path, status, trace_id, created_at, updated_at)
select 'wrk_fcoss_sync_20260710', 'F-COSS 数据同步-20260710', '/tmp/test-agent/fcoss/sync/20260710', 'ACTIVE', 'trace_seed_fcoss_sync_20260710', now(), now()
where not exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_sync_20260710');

insert into workspaces (workspace_id, name, root_path, status, trace_id, created_at, updated_at)
select 'wrk_fcoss_report_20260715', 'F-COSS 报表-20260715', '/tmp/test-agent/fcoss/report/20260715', 'ACTIVE', 'trace_seed_fcoss_report_20260715', now(), now()
where not exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_report_20260715');

-- 应用版本工作区：与运行态 workspaces 一一对应
insert into application_workspace_versions (
    version_id, application_workspace_id, app_id, repository_id, version, branch,
    repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
    status, created_at, updated_at
)
select 'awv_fcoss_mobile_20260705', 'awp_fcoss_mobile', 'app_fcoss', 'repo_fcoss_main', '20260705', 'feature_testagent_20260705',
       '/tmp/test-agent/fcoss/mobile/20260705', '/tmp/test-agent/fcoss/mobile/20260705/src/mobile', 'wrk_fcoss_mobile_20260705',
       (select user_id from users where username = '888888888'),
       'ACTIVE', now(), now()
where exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_mobile')
  and exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_mobile_20260705')
  and not exists (select 1 from application_workspace_versions where version_id = 'awv_fcoss_mobile_20260705');

insert into application_workspace_versions (
    version_id, application_workspace_id, app_id, repository_id, version, branch,
    repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
    status, created_at, updated_at
)
select 'awv_fcoss_sync_20260710', 'awp_fcoss_sync', 'app_fcoss', 'repo_fcoss_main', '20260710', 'feature_testagent_20260710',
       '/tmp/test-agent/fcoss/sync/20260710', '/tmp/test-agent/fcoss/sync/20260710/sync', 'wrk_fcoss_sync_20260710',
       (select user_id from users where username = '888888888'),
       'ACTIVE', now(), now()
where exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_sync')
  and exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_sync_20260710')
  and not exists (select 1 from application_workspace_versions where version_id = 'awv_fcoss_sync_20260710');

insert into application_workspace_versions (
    version_id, application_workspace_id, app_id, repository_id, version, branch,
    repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
    status, created_at, updated_at
)
select 'awv_fcoss_report_20260715', 'awp_fcoss_report', 'app_fcoss', 'repo_fcoss_main', '20260715', 'feature_testagent_20260715',
       '/tmp/test-agent/fcoss/report/20260715', '/tmp/test-agent/fcoss/report/20260715/reports', 'wrk_fcoss_report_20260715',
       (select user_id from users where username = '888888888'),
       'ACTIVE', now(), now()
where exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_report')
  and exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_report_20260715')
  and not exists (select 1 from application_workspace_versions where version_id = 'awv_fcoss_report_20260715');
