create table local_client_instance_replacements (
    replaced_client_instance_id varchar(128) primary key,
    replacement_client_instance_id varchar(128) not null,
    user_id varchar(128) not null references users(user_id) on delete cascade,
    replaced_at timestamp with time zone not null,
    constraint fk_local_client_replacement_old_owner
        foreign key(replaced_client_instance_id, user_id)
        references local_client_instances(client_instance_id, user_id),
    constraint fk_local_client_replacement_new_owner
        foreign key(replacement_client_instance_id, user_id)
        references local_client_instances(client_instance_id, user_id),
    constraint ck_local_client_replacement_distinct
        check(replaced_client_instance_id <> replacement_client_instance_id)
);

create index idx_local_client_instance_replacements_new
    on local_client_instance_replacements(replacement_client_instance_id, replaced_at desc);

comment on table local_client_instance_replacements is '重装后经真实工作区身份校验确认的本地客户端实例替换关系';
comment on column local_client_instance_replacements.replaced_client_instance_id is '保留历史外键但不再进入活动实例投影的旧实例';
comment on column local_client_instance_replacements.replacement_client_instance_id is '已接管旧实例全部工作区的新实例';
comment on column local_client_instance_replacements.replaced_at is '最后一个旧工作区完成安全接管的时间';
