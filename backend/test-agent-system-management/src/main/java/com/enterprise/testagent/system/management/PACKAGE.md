# 包说明：com.enterprise.testagent.system.management

## 职责

系统内部管理业务边界，承载用户管理、角色、权限、存量账号安全删除、TCDS 用户信息原位同步，以及超级管理员短期只读排查授权和审计编排等平台管理功能。

`supportaccess.SupportAccessApplicationService` 只向实时角色仍为 `SUPER_ADMIN` 的当前登录会话签发 5–240 分钟授权；一次登录会话只保留一个当前授权，切换目标会轮换内存令牌并审计，撤权、过期、会话失效或角色变化立即拒绝后续读取。该服务不模拟目标用户身份，也不授予写入、下载、终端、Git、Agent 配置或批量导出能力。

## 不负责

- 不定义 HTTP Controller。
- 不承载 workspace、opencode runtime 或外部系统集成业务。

## 修改时必须同步更新

- `backend/test-agent-system-management/README.md`。
- `docs/api/http-api.md`，如果新增内部管理 API。
- `docs/standards/security.md`，如果涉及鉴权、权限或用户数据。
