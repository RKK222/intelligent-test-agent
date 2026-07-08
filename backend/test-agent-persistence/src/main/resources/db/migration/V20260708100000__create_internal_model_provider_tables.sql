create table if not exists internal_model_providers (
    provider_id varchar(128) primary key,
    name varchar(256) not null,
    base_url text not null,
    enabled boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

comment on table internal_model_providers is '企业内部模型供应商配置';
comment on column internal_model_providers.provider_id is '供应商标识，对应 opencode 配置中的 provider key';
comment on column internal_model_providers.name is '供应商展示名称';
comment on column internal_model_providers.base_url is '内部供应商 OpenAI-compatible base URL';
comment on column internal_model_providers.enabled is '是否启用';
comment on column internal_model_providers.sort_order is '排序号';

create table if not exists internal_model_proxy_settings (
    setting_id varchar(64) primary key,
    icbc_openai_auth_token text,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint ck_internal_model_proxy_settings_singleton check (setting_id = 'default')
);

comment on table internal_model_proxy_settings is '企业内部模型代理全局设置';
comment on column internal_model_proxy_settings.icbc_openai_auth_token is '内部模型接口鉴权 token，按需求明文保存';
