update public_agent_config_rollout_targets t
set process_pid = p.pid,
    process_started_at = p.started_at at time zone current_setting('TimeZone'),
    updated_at = current_timestamp
from opencode_server_processes p
where p.linux_server_id = t.linux_server_id
  and p.container_id = t.container_id
  and p.port = t.port
  and p.pid is not null
  and p.pid > 0
  and p.started_at is not null
  -- 建单后才启动的同端口进程一定是替换实例，绝不能把它回填成旧 target 的身份。
  and (p.started_at at time zone current_setting('TimeZone')) <= t.created_at
  and (t.process_pid is null or t.process_started_at is null);

comment on column public_agent_config_rollout_targets.process_started_at is
    'manager进程启动时间；升级存量target按数据库会话时区从旧timestamp列转换，无法回填时worker保持失败关闭';
