alter table public_agent_config_rollouts
    add column supersedes_rollout_id varchar(64),
    add column superseded_by_rollout_id varchar(64),
    add column supersede_reason varchar(1000);

alter table public_agent_config_rollouts
    add constraint fk_public_agent_config_rollouts_supersedes
        foreign key (supersedes_rollout_id)
        references public_agent_config_rollouts(rollout_id),
    add constraint fk_public_agent_config_rollouts_superseded_by
        foreign key (superseded_by_rollout_id)
        references public_agent_config_rollouts(rollout_id),
    add constraint ck_public_agent_config_rollouts_not_self_superseded
        check (supersedes_rollout_id is null or supersedes_rollout_id <> rollout_id),
    add constraint ck_public_agent_config_rollouts_not_self_superseding
        check (superseded_by_rollout_id is null or superseded_by_rollout_id <> rollout_id);

create unique index uk_public_agent_config_rollouts_supersedes
    on public_agent_config_rollouts (supersedes_rollout_id)
    where supersedes_rollout_id is not null;

create unique index uk_public_agent_config_rollouts_superseded_by
    on public_agent_config_rollouts (superseded_by_rollout_id)
    where superseded_by_rollout_id is not null;

comment on column public_agent_config_rollouts.supersedes_rollout_id is
    '纠错发布所替换的旧公共 rollout；非纠错发布为空';
comment on column public_agent_config_rollouts.superseded_by_rollout_id is
    '旧公共 rollout 被哪个纠错发布替换；仅 SUPERSEDED 终态使用';
comment on column public_agent_config_rollouts.supersede_reason is
    '超级管理员执行纠错替换时填写的审计原因';
comment on column public_agent_config_rollouts.status is
    'PREPARING/DRAINING 为活动态，COMPLETED/ABORTED/SUPERSEDED 为终态';

alter table public_agent_config_rollout_targets
    add column force_stop boolean not null default false;

comment on column public_agent_config_rollout_targets.force_stop is
    '纠错发布中与被替换发布未排空进程身份精确匹配的目标；跳过会话空闲等待并走统一停止服务';
