-- 兼容历史库中 user_roles identity 序列落后于已有主键的情况。
-- 该表只使用数据库 surrogate id，对外业务仍以 user_id + dict_id 唯一约束为准。
alter table user_roles alter column id restart with 1000000;
