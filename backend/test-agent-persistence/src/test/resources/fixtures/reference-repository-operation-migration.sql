insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
values ('usr_reference_migration', 'migration-001', 'reference-migration', 'hash', 'ACTIVE',
        timestamp '2026-07-18 00:00:00', timestamp '2026-07-18 00:00:00');

insert into code_repositories(
    repository_id, git_url, name, english_name, repository_type, deployment_mode, standard, created_at, updated_at)
values
    ('repo_migration_initializing', 'https://git.example.test/initializing.git', '初始化中资产库',
     'migration-initializing', 'APPLICATION_ASSET_REPOSITORY', 'EXTERNAL', false,
     timestamp '2026-07-18 00:00:00', timestamp '2026-07-18 00:00:00'),
    ('repo_migration_uninitialized', 'https://git.example.test/uninitialized.git', '未初始化资产库',
     'migration-uninitialized', 'APPLICATION_ASSET_REPOSITORY', 'EXTERNAL', false,
     timestamp '2026-07-18 00:00:00', timestamp '2026-07-18 00:00:00');

insert into reference_repository_states(
    repository_id, branch, target_commit_hash, generation, status, credential_user_id, trace_id,
    initialized_at, created_at, updated_at)
values
    ('repo_migration_initializing', 'main', 'commit-1', 1, 'INITIALIZING', 'usr_reference_migration',
     'trace_initializing', timestamp '2026-07-18 00:00:00', timestamp '2026-07-18 00:00:00',
     timestamp '2026-07-18 00:00:00'),
    ('repo_migration_uninitialized', null, null, 0, 'UNINITIALIZED', null,
     'trace_uninitialized', null, timestamp '2026-07-18 00:00:00', timestamp '2026-07-18 00:00:00');
