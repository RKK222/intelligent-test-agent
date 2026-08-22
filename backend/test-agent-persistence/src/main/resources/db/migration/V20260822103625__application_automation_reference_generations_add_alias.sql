alter table application_automation_reference_generations
    add column reference_alias varchar(128);

update application_automation_reference_generations generation_row
set reference_alias = 'automation-' || coalesce(nullif(trim(repository.english_name), ''), repository.repository_id)
from code_repositories repository
where repository.repository_id = generation_row.repository_id;

alter table application_automation_reference_generations
    alter column reference_alias set not null;

alter table application_automation_reference_generations
    add constraint chk_application_automation_reference_generations_alias_length
        check (char_length(reference_alias) between 1 and 128);

comment on column application_automation_reference_generations.reference_alias
    is '应用共享的 OpenCode 自动化引用别名，切换配置时随不可变代次整体生效';
