-- Hub 取消引用采用两阶段状态：先在个人 worktree 删除，push 成功后再标记为已解除。
alter table agent_skill_hub_references drop constraint ck_hub_reference_status;
alter table agent_skill_hub_references
    add constraint ck_hub_reference_status
        check (status in ('ACTIVE', 'PENDING_PUSH', 'PENDING_REMOVE', 'UPDATE_CONFLICT'));

-- 解除后的引用元数据可以清理；其临时合并操作不再具有独立业务意义。
alter table agent_skill_hub_update_operations drop constraint fk_hub_update_reference;
alter table agent_skill_hub_update_operations
    add constraint fk_hub_update_reference foreign key (reference_id)
        references agent_skill_hub_references(reference_id) on delete cascade;
