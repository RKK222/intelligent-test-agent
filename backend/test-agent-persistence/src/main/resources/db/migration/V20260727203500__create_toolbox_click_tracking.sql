-- 工具点击明细永久保留；删除用户时仅匿名化 user_id。
create table toolbox_tool_click_events (
    event_id varchar(128) primary key,
    tool_id varchar(255) not null,
    source varchar(32) not null,
    user_id varchar(128),
    trace_id varchar(128) not null,
    counted boolean not null default false,
    clicked_at timestamp with time zone not null,
    constraint ck_toolbox_click_event_source check (source in ('IT_TOOLS', 'OMNI_TOOLS')),
    constraint fk_toolbox_click_event_user foreign key (user_id) references users(user_id) on delete set null
);

-- 累计投影只在首次有效计数时产生，目录中的零点击工具无需占行。
create table toolbox_tool_click_totals (
    tool_id varchar(255) primary key,
    click_count bigint not null,
    last_counted_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_toolbox_click_total_non_negative check (click_count >= 0)
);

-- 同一用户同一工具的最后有效计数时间，用于原子竞争 30 秒窗口。
create table toolbox_tool_user_click_states (
    tool_id varchar(255) not null,
    user_id varchar(128) not null,
    last_counted_at timestamp with time zone not null,
    primary key (tool_id, user_id),
    constraint fk_toolbox_click_state_user foreign key (user_id) references users(user_id) on delete cascade
);

create index idx_toolbox_click_events_tool_time
    on toolbox_tool_click_events(tool_id, clicked_at desc);
create index idx_toolbox_click_events_user_time
    on toolbox_tool_click_events(user_id, clicked_at desc);
create index idx_toolbox_click_totals_hot
    on toolbox_tool_click_totals(click_count desc, last_counted_at desc);

comment on table toolbox_tool_click_events is '工具盒子点击明细，永久保留并以event_id全局幂等';
comment on column toolbox_tool_click_events.source is '工具来源：IT_TOOLS/OMNI_TOOLS';
comment on column toolbox_tool_click_events.user_id is '点击用户ID；删除用户后置空以保留匿名审计明细';
comment on column toolbox_tool_click_events.counted is '本次点击是否赢得30秒窗口并计入累计';
comment on column toolbox_tool_click_events.clicked_at is '服务端生成的点击时间';

comment on table toolbox_tool_click_totals is '工具盒子每个稳定tool_id的累计点击投影';
comment on column toolbox_tool_click_totals.last_counted_at is '最近一次有效计数时间，用于热门排序';

comment on table toolbox_tool_user_click_states is '用户与工具最近有效计数状态，用于原子竞争30秒窗口';
comment on column toolbox_tool_user_click_states.last_counted_at is '该用户对该工具最近一次有效计数时间';
