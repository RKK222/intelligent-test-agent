alter table local_client_credentials
    add column revealed_at timestamp with time zone;

-- 存量密钥可能已经被复制过，升级后必须失败关闭，不能重新暴露明文。
update local_client_credentials
set revealed_at = updated_at
where revealed_at is null;

comment on column local_client_credentials.revealed_at is
    '当前凭据版本首次且唯一一次返回明文的权威时间；空值表示尚可显示';
