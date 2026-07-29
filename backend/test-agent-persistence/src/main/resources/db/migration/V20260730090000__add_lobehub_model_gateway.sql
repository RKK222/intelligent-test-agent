create table internal_model_provider_models (
    provider_id varchar(128) not null,
    model_id varchar(256) not null,
    upstream_model_id varchar(256) not null,
    display_name varchar(256) not null,
    context_limit bigint,
    enabled boolean not null default true,
    capability_chat boolean not null default false,
    capability_tools boolean not null default false,
    capability_vision boolean not null default false,
    capability_reasoning boolean not null default false,
    capability_embedding boolean not null default false,
    capability_rerank boolean not null default false,
    capability_image boolean not null default false,
    capability_speech boolean not null default false,
    capability_transcription boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint pk_internal_model_provider_models primary key (provider_id, model_id),
    constraint uk_internal_model_provider_models_public_id unique (model_id),
    constraint fk_internal_model_provider_models_provider
        foreign key (provider_id) references internal_model_providers(provider_id) on delete cascade,
    constraint ck_internal_model_provider_models_context
        check (context_limit is null or context_limit > 0)
);

comment on table internal_model_provider_models is '企业模型网关公开模型目录';
comment on column internal_model_provider_models.model_id is '对 LobeHub 等客户端公开的全局唯一模型 ID';
comment on column internal_model_provider_models.upstream_model_id is '供应商实际模型 ID，仅在网关内替换';

create index idx_internal_model_provider_models_enabled
    on internal_model_provider_models(enabled, provider_id, model_id);

create table internal_model_provider_model_probes (
    provider_id varchar(128) not null,
    model_id varchar(256) not null,
    capability varchar(32) not null,
    succeeded boolean not null,
    probed_at timestamp not null,
    constraint pk_internal_model_provider_model_probes
        primary key (provider_id, model_id, capability),
    constraint fk_internal_model_provider_model_probes_model
        foreign key (provider_id, model_id)
            references internal_model_provider_models(provider_id, model_id) on delete cascade,
    constraint ck_internal_model_provider_model_probes_capability check (
        capability in ('CHAT', 'TOOLS', 'VISION', 'REASONING', 'EMBEDDING',
                       'RERANK', 'IMAGE', 'SPEECH', 'TRANSCRIPTION')
    )
);

comment on table internal_model_provider_model_probes is '模型逐能力最近探测结果，不保存上游原始错误';

create table model_gateway_usage_daily (
    usage_date date not null,
    source_client varchar(64) not null,
    user_id varchar(128) not null,
    provider_id varchar(128) not null,
    model_id varchar(256) not null,
    endpoint varchar(64) not null,
    request_count bigint not null default 0,
    success_count bigint not null default 0,
    failure_count bigint not null default 0,
    input_tokens bigint not null default 0,
    output_tokens bigint not null default 0,
    total_tokens bigint not null default 0,
    duration_ms bigint not null default 0,
    updated_at timestamp not null,
    constraint pk_model_gateway_usage_daily primary key (
        usage_date, source_client, user_id, provider_id, model_id, endpoint
    ),
    constraint ck_model_gateway_usage_daily_non_negative check (
        request_count >= 0 and success_count >= 0 and failure_count >= 0
        and input_tokens >= 0 and output_tokens >= 0 and total_tokens >= 0
        and duration_ms >= 0
    )
);

comment on table model_gateway_usage_daily is '不含 prompt、回答、UCID、原始错误或持久 traceId 的每日模型用量聚合';

create index idx_model_gateway_usage_daily_user
    on model_gateway_usage_daily(user_id, usage_date);
create index idx_model_gateway_usage_daily_provider_model
    on model_gateway_usage_daily(provider_id, model_id, usage_date);

insert into common_parameters(
    parameter_id, parameter_english, parameter_chinese, parameter_value,
    platform, editable, created_at, updated_at
) values (
    'param_lobehub_enabled_all', 'LOBEHUB_ENABLED', 'LobeHub 通用问答入口开关', 'false',
    'all', true, current_timestamp, current_timestamp
);

insert into common_parameters(
    parameter_id, parameter_english, parameter_chinese, parameter_value,
    platform, editable, created_at, updated_at
) values (
    'param_lobehub_base_url_all', 'LOBEHUB_BASE_URL', 'LobeHub 固定访问基址', 'http://127.0.0.1:3210',
    'all', true, current_timestamp, current_timestamp
);

insert into common_parameters(
    parameter_id, parameter_english, parameter_chinese, parameter_value,
    platform, editable, created_at, updated_at
) values (
    'param_lobehub_email_domain_all', 'LOBEHUB_SSO_EMAIL_DOMAIN', 'LobeHub SSO 虚拟邮箱域', 'disabled.invalid',
    'all', true, current_timestamp, current_timestamp
);

insert into common_parameters(
    parameter_id, parameter_english, parameter_chinese, parameter_value,
    platform, editable, created_at, updated_at
) values (
    'param_lobehub_owner_auth_all', 'LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID', 'LobeHub 初始 Owner 统一认证号', 'NOT_CONFIGURED',
    'all', true, current_timestamp, current_timestamp
);
