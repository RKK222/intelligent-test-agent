# 包说明：com.enterprise.testagent.integration

## 职责

非 opencode 外部系统联动业务边界，包含 AAM 登录 Token 验真、外部用户 SSH Key 查询、TAEK1 加密响应、TCDS 任务类型实时查询和受控案例维护，以及外部 SkillHub 目录/ZIP 下载。AAM 适配器只调用配置 origin 下固定的 `/aam/checkLogin`，仅把数值 `code=200` 解释为成功，并对连接、请求时间和响应大小失败关闭；用户号、Token、URL 与上游正文不得进入日志或异常。SkillHub 认证头和 `PLATFORM(3)` 下载渠道在适配层固定，领域层只看到稳定目录记录与原始下载包；密钥和正文不进入日志。私钥明文只允许停留在方法局部，不进入 Controller DTO、日志或事件。TCDS 任务类型业务名称由实时 `name` 删除末尾“测试任务”得到，案例提交前按当前结果再次校验，不维护固定类型表。全部 TCDS 能力复用统一部署基础地址、HTTP client 和同源 `toolId` 请求构造器，案例固定业务字段由服务端维护，不接受浏览器覆盖。

## 不负责

- 不定义 HTTP Controller。
- 不承载 workspace、系统内部管理或 opencode runtime 业务。

## 修改时必须同步更新

- `backend/test-agent-integration/README.md`。
- `docs/api/http-api.md`，如果新增公开或内部集成 API。
- `docs/standards/security.md`，如果涉及外部凭据、鉴权或敏感数据。
