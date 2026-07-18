alter table reference_repository_states
    add column operation_type varchar(32) default 'SYNCHRONIZE';

update reference_repository_states
set operation_type = case
    when status in ('INITIALIZING', 'UNINITIALIZED') then 'INITIALIZE'
    when status = 'VERIFYING' then 'VERIFY_POINTERS'
    else 'SYNCHRONIZE'
end;

alter table reference_repository_states
    alter column operation_type set not null;
alter table reference_repository_states
    add constraint chk_reference_repository_states_operation_type check (
        operation_type in ('INITIALIZE', 'SYNCHRONIZE', 'SWITCH_BRANCH', 'VERIFY_POINTERS'));

alter table reference_repository_replicas
    alter column branch drop not null;
alter table reference_repository_replicas
    add column verified_at timestamp;

comment on column reference_repository_states.branch is '当前代次期望落盘的目标分支，可通过受控切换变更';
comment on column reference_repository_states.target_commit_hash is '当前代次固定的远端目标提交或核验基准提交';
comment on column reference_repository_states.operation_type is '当前代次内部操作类型';
comment on column reference_repository_replicas.branch is '最近一次可信观察到的本机实际分支，未观察时为空';
comment on column reference_repository_replicas.current_commit_hash is '最近一次可信观察到的本机实际提交，未观察时为空';
comment on column reference_repository_replicas.verified_at is '最近一次主动核验实际指针的时间';
