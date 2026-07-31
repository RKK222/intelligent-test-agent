-- 应用源码单次物化或续期最长保留 365 天，仍要求相对首次受理时间为整小时。
alter table app_source_snapshots drop constraint chk_app_source_snapshots_expiry;

alter table app_source_snapshots add constraint chk_app_source_snapshots_expiry check (
    expires_at >= accepted_at + interval '1 hour'
    and expires_at <= accepted_at + interval '8760 hours'
    and mod(extract(epoch from (expires_at - accepted_at)), 3600) = 0);
