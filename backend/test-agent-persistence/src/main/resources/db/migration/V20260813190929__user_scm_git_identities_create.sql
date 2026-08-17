-- 企业 SCM 提交姓名独立于平台展示名保存，避免同名用户的平台数字后缀污染 Git 提交。
create table user_scm_git_identities (
    user_id varchar(128) primary key,
    git_name varchar(128) not null,
    source varchar(32) not null,
    evidence_commit varchar(64),
    evidence_at timestamp not null,
    verified_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_user_scm_git_identities_user foreign key (user_id) references users(user_id) on delete cascade,
    constraint ck_user_scm_git_identities_name check (length(trim(git_name)) between 1 and 128),
    constraint ck_user_scm_git_identities_source check (
        source in ('ACCEPTED_COMMIT_HISTORY', 'REMOTE_REJECTION')
    )
);
