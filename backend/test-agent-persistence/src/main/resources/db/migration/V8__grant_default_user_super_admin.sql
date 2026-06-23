-- 为本地默认前端用户授予超级管理员角色，便于开发环境直接访问应用配置管理。
insert into user_roles(user_id, dict_id, created_at)
select u.user_id, d.dict_id, now()
from users u
join dictionaries d on d.dict_key = 'ROLE' and d.dict_value = 'SUPER_ADMIN'
where u.username = '888888888'
  and not exists (
      select 1
      from user_roles ur
      where ur.user_id = u.user_id
        and ur.dict_id = d.dict_id
  );
