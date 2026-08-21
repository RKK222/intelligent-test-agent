create table applications (
    app_id varchar(128) primary key
);

create table code_repositories (
    repository_id varchar(128) primary key,
    name varchar(255) not null,
    repository_type varchar(64) not null
);

create table users (
    user_id varchar(128) primary key
);

create table application_workspaces (
    workspace_id varchar(128) primary key,
    app_id varchar(128) not null,
    repository_id varchar(128) not null,
    directory_path varchar(1024) not null,
    enabled boolean not null,
    updated_at timestamp not null
);

create table application_workspace_versions (
    version_id varchar(128) primary key,
    application_workspace_id varchar(128) not null,
    branch varchar(255) not null,
    target_commit_hash varchar(128),
    created_by_user_id varchar(128) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table automation_workspace_active_versions (
    application_workspace_id varchar(128) primary key,
    version_id varchar(128) not null,
    updated_at timestamp not null
);

create table application_workspace_version_replicas (
    version_id varchar(128) not null,
    linux_server_id varchar(128) not null,
    primary key (version_id, linux_server_id)
);

create table runs (
    run_id varchar(128) primary key
);

insert into applications(app_id) values ('app_alpha'), ('app_beta');
insert into code_repositories(repository_id, name, repository_type) values
    ('repo_automation', '自动化代码库', 'AUTOMATION_CODE_REPOSITORY'),
    ('repo_code', '应用代码库', 'APPLICATION_CODE_REPOSITORY');
insert into users(user_id) values ('usr_admin');

insert into application_workspaces(
    workspace_id, app_id, repository_id, directory_path, enabled, updated_at
) values
    ('aws_alpha_old', 'app_alpha', 'repo_automation', 'legacy/tests', true, timestamp '2026-08-19 09:00:00'),
    ('aws_alpha_new', 'app_alpha', 'repo_automation', 'src/test', true, timestamp '2026-08-20 09:00:00'),
    ('aws_beta', 'app_beta', 'repo_automation', 'integration', true, timestamp '2026-08-20 10:00:00'),
    ('aws_code', 'app_alpha', 'repo_code', 'workspace', true, timestamp '2026-08-20 11:00:00');

insert into application_workspace_versions(
    version_id, application_workspace_id, branch, target_commit_hash,
    created_by_user_id, status, created_at, updated_at
) values
    ('awv_alpha_old', 'aws_alpha_old', 'main', 'commit-old', 'usr_admin', 'ACTIVE',
     timestamp '2026-08-19 09:00:00', timestamp '2026-08-19 09:00:00'),
    ('awv_alpha_main', 'aws_alpha_new', 'main', 'commit-main', 'usr_admin', 'ACTIVE',
     timestamp '2026-08-20 09:00:00', timestamp '2026-08-20 09:00:00'),
    ('awv_alpha_feature', 'aws_alpha_new', 'feature/e2e', 'commit-feature', 'usr_admin', 'ACTIVE',
     timestamp '2026-08-21 09:00:00', timestamp '2026-08-21 09:00:00'),
    ('awv_beta_release', 'aws_beta', 'release', 'commit-release', 'usr_admin', 'ACTIVE',
     timestamp '2026-08-20 10:00:00', timestamp '2026-08-20 10:00:00');

insert into automation_workspace_active_versions(application_workspace_id, version_id, updated_at) values
    ('aws_alpha_old', 'awv_alpha_old', timestamp '2026-08-19 09:30:00'),
    ('aws_alpha_new', 'awv_alpha_feature', timestamp '2026-08-21 09:30:00'),
    ('aws_beta', 'awv_beta_release', timestamp '2026-08-20 10:30:00');

insert into application_workspace_version_replicas(version_id, linux_server_id) values
    ('awv_alpha_old', 'server-a'),
    ('awv_alpha_feature', 'server-a'),
    ('awv_alpha_feature', 'server-b'),
    ('awv_beta_release', 'server-a');
