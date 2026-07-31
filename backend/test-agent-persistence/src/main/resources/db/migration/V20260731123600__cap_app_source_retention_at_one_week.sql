-- 已执行的 365 天扩容 migration 保持原字节，本次按产品边界把最终上限收紧为 7 天。
alter table app_source_snapshots drop constraint chk_app_source_snapshots_expiry;

alter table app_source_snapshots add constraint chk_app_source_snapshots_expiry check (
    expires_at >= accepted_at + interval '1 hour'
    and expires_at <= accepted_at + interval '168 hours'
    and mod(extract(epoch from (expires_at - accepted_at)), 3600) = 0);
