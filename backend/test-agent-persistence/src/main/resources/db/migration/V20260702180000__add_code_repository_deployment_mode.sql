alter table code_repositories
    add column deployment_mode varchar(16) not null default 'EXTERNAL';

alter table code_repositories
    alter column english_name type varchar(128);

comment on column code_repositories.deployment_mode is '版本库部署模式：EXTERNAL保存完整Git地址，INTERNAL仅保存不含统一认证号的SCM地址片段';
comment on column code_repositories.english_name is '版本库英文名称，非空时唯一，按小写保存，允许字母数字和连字符';
