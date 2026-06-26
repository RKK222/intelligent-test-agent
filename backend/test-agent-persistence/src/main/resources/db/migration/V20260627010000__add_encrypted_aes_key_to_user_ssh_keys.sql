-- 为 hybrid RSA+AES 加密方案新增 encrypted_aes_key 列。
-- 旧记录的 encrypted_aes_key 为 NULL，代码层拦截并提示用户重新添加 SSH key。
-- 使用时间戳版本，避免复用已落库的 V10 F-COSS seed migration 导致 Flyway checksum mismatch。
alter table user_ssh_keys
    add column if not exists encrypted_aes_key text;

comment on column user_ssh_keys.encrypted_aes_key is 'RSA加密后的临时AES密钥密文，用于前端混合加密SSH私钥';
