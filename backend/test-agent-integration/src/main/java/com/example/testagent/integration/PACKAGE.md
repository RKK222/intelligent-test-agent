# 包说明：com.example.testagent.integration

## 职责

非 opencode 外部系统联动业务边界。

## 不负责

- 不定义 HTTP Controller。
- 不承载 workspace、系统内部管理或 opencode runtime 业务。

## 修改时必须同步更新

- `backend/test-agent-integration/README.md`。
- `docs/api/backend-api.md`，如果新增公开或内部集成 API。
- `docs/security/security-standards.md`，如果涉及外部凭据、鉴权或敏感数据。
