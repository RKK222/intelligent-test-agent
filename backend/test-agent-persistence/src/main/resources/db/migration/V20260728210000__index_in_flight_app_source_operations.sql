-- dispatcher 会按固定周期扫描未终态 operation；status 前导索引避免历史终态数据放大每实例恢复成本。
create index idx_app_source_operations_in_flight
    on app_source_operations(status, accepted_at, operation_id);
