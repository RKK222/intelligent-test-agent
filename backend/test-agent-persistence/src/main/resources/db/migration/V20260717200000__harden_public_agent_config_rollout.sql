alter table public_agent_config_rollouts
    add column initiated_by_user_id varchar(128);

alter table public_agent_config_rollout_targets
    add column user_id varchar(128),
    add column lease_token varchar(64);

update public_agent_config_rollout_targets t
set user_id = p.user_id
from opencode_server_processes p
where p.linux_server_id = t.linux_server_id
  and p.container_id = t.container_id
  and p.port = t.port
  and t.user_id is null;

create index idx_public_agent_config_rollout_targets_user
    on public_agent_config_rollout_targets (rollout_id, user_id, status);

comment on column public_agent_config_rollouts.initiated_by_user_id is
    '发起公共配置发布的用户ID；各服务器用该用户已加密保存的SSH密钥同步同一远端提交';
comment on column public_agent_config_rollout_targets.user_id is
    '登记目标时快照的用户ID；用于目标dispose后立即按用户解除消息门禁';
comment on column public_agent_config_rollout_targets.lease_token is
    '本次PROCESSING认领令牌；过期worker不得覆盖新认领者结果';
