# 包说明：com.enterprise.testagent.integration

## 职责

非 opencode 外部系统联动业务边界，包含外部用户 SSH Key 查询和 TAEK1 加密响应；私钥明文只允许停留在方法局部，不进入 Controller DTO、日志或事件。

## 不负责

- 不定义 HTTP Controller。
- 不承载 workspace、系统内部管理或 opencode runtime 业务。

## 修改时必须同步更新

- `backend/test-agent-integration/README.md`。
- `docs/api/http-api.md`，如果新增公开或内部集成 API。
- `docs/standards/security.md`，如果涉及外部凭据、鉴权或敏感数据。
