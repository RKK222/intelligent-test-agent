CREATE TABLE external_api_credentials (
    credential_id VARCHAR(64) PRIMARY KEY,
    tool_code VARCHAR(64) NOT NULL UNIQUE,
    tool_name VARCHAR(128) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    api_key_fingerprint CHAR(64) NOT NULL,
    key_hint VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_external_api_tool_code_nonblank CHECK (TRIM(tool_code) <> ''),
    CONSTRAINT chk_external_api_tool_name_nonblank CHECK (TRIM(tool_name) <> ''),
    CONSTRAINT chk_external_api_key_ciphertext_nonblank CHECK (TRIM(encrypted_api_key) <> '')
);

CREATE TABLE external_api_credential_scopes (
    credential_id VARCHAR(64) NOT NULL,
    scope_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (credential_id, scope_code),
    CONSTRAINT fk_external_api_scope_credential
        FOREIGN KEY (credential_id)
        REFERENCES external_api_credentials (credential_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_external_api_credentials_enabled_tool_code
    ON external_api_credentials (enabled, tool_code);

COMMENT ON TABLE external_api_credentials IS '外部系统调用平台 API 的工具凭据';
COMMENT ON COLUMN external_api_credentials.credential_id IS '外部 API 凭据稳定 ID';
COMMENT ON COLUMN external_api_credentials.tool_code IS '调用工具稳定编码，创建后不可修改';
COMMENT ON COLUMN external_api_credentials.tool_name IS '工具展示名称';
COMMENT ON COLUMN external_api_credentials.encrypted_api_key IS 'RSA-OAEP/SHA-256 加密的 API Key 密文';
COMMENT ON COLUMN external_api_credentials.api_key_fingerprint IS 'API Key 的 SHA-256 十六进制指纹';
COMMENT ON COLUMN external_api_credentials.key_hint IS '管理列表展示的 API Key 掩码提示';
COMMENT ON COLUMN external_api_credentials.enabled IS '凭据是否允许认证';
COMMENT ON COLUMN external_api_credentials.created_at IS '创建时间';
COMMENT ON COLUMN external_api_credentials.updated_at IS '最后更新时间';

COMMENT ON TABLE external_api_credential_scopes IS '外部 API 凭据被授予的最小权限集合';
COMMENT ON COLUMN external_api_credential_scopes.credential_id IS '关联的外部 API 凭据 ID';
COMMENT ON COLUMN external_api_credential_scopes.scope_code IS '稳定 scope 编码';
