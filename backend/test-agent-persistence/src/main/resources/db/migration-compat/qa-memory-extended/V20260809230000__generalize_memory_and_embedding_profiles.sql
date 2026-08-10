-- 前向扩展：保留已执行 QA migration 的文件、版本和 checksum，不重命名遗留物理表。
alter table internal_model_provider_models
    add column embedding_dimension integer;

alter table internal_model_provider_models
    add constraint ck_internal_model_embedding_dimension
        check (embedding_dimension is null or embedding_dimension > 0);

alter table qa_memory_settings
    add column primary_embedding_model_id varchar(255);

alter table qa_memory_settings
    add column cpu_embedding_model_id varchar(255) not null
        default 'memory-bge-small-zh-v1.5';

comment on column internal_model_provider_models.embedding_dimension is
    'Embedding 能力的输出维度；不同维度和模型必须使用独立 collection';
comment on column qa_memory_settings.primary_embedding_model_id is
    '可空企业 Embedding 公开模型 ID；为空时仅使用 CPU BGE';
comment on column qa_memory_settings.cpu_embedding_model_id is
    '固定 CPU BGE 服务在 Java 模型目录中的公开模型 ID';
