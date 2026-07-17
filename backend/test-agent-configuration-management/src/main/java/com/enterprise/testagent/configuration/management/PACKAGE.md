# 包说明：com.enterprise.testagent.configuration.management

## 职责

应用配置管理业务包，负责应用定义只读查询、应用成员关系、代码库配置、版本库英文名称校验、已初始化引用资产库的英文名/类型冻结兼容、应用工作空间配置、个人 SSH key 加密保存，以及通过 Git 远端只读命令列分支和目录。

## 不负责

- 不提供 HTTP Controller 或 Web DTO。
- 不复用或修改运行态 Workspace / Session / Run。
- 不创建初始应用版本工作区；设置页创建工作空间时的 clone/checkout 和进度记录由 `test-agent-workspace-management` 与持久化进度端口完成。
- 不直接访问数据库实现类。
- 不调用 generated SDK 或 opencode server。
- 不编排引用资产副本、Git clone/fetch 或同步状态；这里只通过领域仓储端口只读判断是否已初始化。

## 修改时必须同步更新

- `backend/test-agent-configuration-management/README.md`。
- `docs/api/http-api.md`，如果 API 形态或错误码变化。
- `docs/deployment/database.md`，如果配置表结构变化。
- `docs/standards/security.md`，如果 SSH key 加密或 Git 私钥使用策略变化。
