# 包说明：com.enterprise.testagent.system.management

## 职责

系统内部管理业务边界，后续承载用户管理、角色、权限等平台管理功能。

## 不负责

- 不定义 HTTP Controller。
- 不承载 workspace、opencode runtime 或外部系统集成业务。

## 修改时必须同步更新

- `backend/test-agent-system-management/README.md`。
- `docs/api/http-api.md`，如果新增内部管理 API。
- `docs/standards/security.md`，如果涉及鉴权、权限或用户数据。
