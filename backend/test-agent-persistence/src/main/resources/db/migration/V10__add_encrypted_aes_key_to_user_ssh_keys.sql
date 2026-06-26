-- 为 hybrid RSA+AES 加密方案新增 encrypted_aes_key 列
-- 旧记录的 encrypted_aes_key 为 NULL，代码层拦截并提示用户重新添加 SSH key
alter table user_ssh_keys
    add column encrypted_aes_key text;
