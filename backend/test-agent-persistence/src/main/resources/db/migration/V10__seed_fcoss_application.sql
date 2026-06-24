-- 开发期种子数据：F-COSS 应用及其工作空间模板、版本、用户成员关系。
-- 仅当 V5/V8 默认开发用户（用户名 888888888）存在时才插入，避免在没有初始化用户的环境下执行失败。
-- 全部使用 on conflict do nothing / not exists 保护，重复执行迁移不会破坏数据。
-- 业务上：F-COSS（app_fcoss）→ 工作空间模板（F-COSS 主服务 awp_fcoss_main）→ 两个 yyyyMMdd 版本（20260620 / 20260701）。
-- ID 前缀必须与领域校验一致：wrk_（WorkspaceId）/ awp_（ApplicationWorkspaceId）/ repo_（CodeRepositoryId）。

insert into applications (app_id, app_name, enabled, created_at, updated_at)
select 'app_fcoss', 'F-COSS', true, now(), now()
where exists (select 1 from users where username = '888888888')
  and not exists (select 1 from applications where app_id = 'app_fcoss');

-- 代码库：F-COSS 主仓库，标记为标准库（分支名由 createVersion 自动生成 feature_testagent_<yyyymmdd>）。
insert into code_repositories (repository_id, git_url, name, standard, created_at, updated_at)
select 'repo_fcoss_main', 'https://git.example.com/icbc/f-coss.git', 'F-COSS 主仓库', true, now(), now()
where not exists (select 1 from code_repositories where repository_id = 'repo_fcoss_main');

-- 应用与代码库多对多关联：F-COSS 关联 F-COSS 主仓库
insert into application_repository_links (app_id, repository_id, created_at)
select 'app_fcoss', 'repo_fcoss_main', now()
where exists (select 1 from applications where app_id = 'app_fcoss')
  and exists (select 1 from code_repositories where repository_id = 'repo_fcoss_main')
  and not exists (
      select 1 from application_repository_links
      where app_id = 'app_fcoss' and repository_id = 'repo_fcoss_main'
  );

-- 应用工作空间模板：默认 main 分支的 src/main 目录
-- application_workspaces.workspace_id 被领域 ApplicationWorkspaceId 校验为 awp_ 前缀。
insert into application_workspaces (workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at)
select 'awp_fcoss_main', 'app_fcoss', 'repo_fcoss_main', 'main', 'src/main', 'F-COSS 主服务', now(), now()
where exists (select 1 from applications where app_id = 'app_fcoss')
  and not exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_main');

-- 应用成员：本地默认开发用户 888888888 加入 F-COSS，便于平台直连 listApplications 看到该应用
insert into application_members (app_id, user_id, created_at, updated_at)
select 'app_fcoss', u.user_id, now(), now()
from users u
where u.username = '888888888'
  and exists (select 1 from applications where app_id = 'app_fcoss')
  and not exists (
      select 1 from application_members am
      where am.app_id = 'app_fcoss' and am.user_id = u.user_id and am.deleted_at is null
  );

-- 运行态工作空间：每个版本绑定一个 workspaces 行，根目录占位由托管工作区服务在切到该版本时刷新
-- workspace_id 必须以 wrk_ 开头（领域 WorkspaceId 校验），否则 JdbcWorkspaceRepository 反序列化会失败。
insert into workspaces (workspace_id, name, root_path, status, trace_id, created_at, updated_at)
select 'wrk_fcoss_20260620', 'F-COSS 主服务-20260620', '/tmp/test-agent/fcoss/20260620', 'ACTIVE', 'trace_seed_fcoss_20260620', now(), now()
where not exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_20260620');

insert into workspaces (workspace_id, name, root_path, status, trace_id, created_at, updated_at)
select 'wrk_fcoss_20260701', 'F-COSS 主服务-20260701', '/tmp/test-agent/fcoss/20260701', 'ACTIVE', 'trace_seed_fcoss_20260701', now(), now()
where not exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_20260701');

-- 应用版本工作区：与运行态 workspaces 一一对应；状态 ACTIVE
insert into application_workspace_versions (
    version_id, application_workspace_id, app_id, repository_id, version, branch,
    repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
    status, created_at, updated_at
)
select 'awv_fcoss_20260620', 'awp_fcoss_main', 'app_fcoss', 'repo_fcoss_main', '20260620', 'feature_testagent_20260620',
       '/tmp/test-agent/fcoss/20260620', '/tmp/test-agent/fcoss/20260620/src/main', 'wrk_fcoss_20260620',
       (select user_id from users where username = '888888888'),
       'ACTIVE', now(), now()
where exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_main')
  and exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_20260620')
  and not exists (select 1 from application_workspace_versions where version_id = 'awv_fcoss_20260620');

insert into application_workspace_versions (
    version_id, application_workspace_id, app_id, repository_id, version, branch,
    repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
    status, created_at, updated_at
)
select 'awv_fcoss_20260701', 'awp_fcoss_main', 'app_fcoss', 'repo_fcoss_main', '20260701', 'feature_testagent_20260701',
       '/tmp/test-agent/fcoss/20260701', '/tmp/test-agent/fcoss/20260701/src/main', 'wrk_fcoss_20260701',
       (select user_id from users where username = '888888888'),
       'ACTIVE', now(), now()
where exists (select 1 from application_workspaces where workspace_id = 'awp_fcoss_main')
  and exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_20260701')
  and not exists (select 1 from application_workspace_versions where version_id = 'awv_fcoss_20260701');

-- 用户的应用级最近使用偏好：默认指向最新版本（20260701），首次进入工作台时直接落到该版本
insert into user_application_workspace_preferences (user_id, app_id, workspace_id, updated_at)
select u.user_id, 'app_fcoss', 'wrk_fcoss_20260701', now()
from users u
where u.username = '888888888'
  and exists (select 1 from applications where app_id = 'app_fcoss')
  and exists (select 1 from workspaces where workspace_id = 'wrk_fcoss_20260701')
  and not exists (
      select 1 from user_application_workspace_preferences
      where user_id = u.user_id and app_id = 'app_fcoss'
  );
