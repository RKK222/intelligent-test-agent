# 包说明：com.enterprise.testagent.configuration.management

## 职责

应用配置管理业务包，负责应用定义只读查询、应用成员关系、代码库配置、版本库英文名称校验、已初始化引用资产库的英文名/类型冻结兼容、应用工作空间配置、个人 SSH key 加密保存、通用参数审计更新和跨实例刷新触发，以及通过 Git 远端只读命令列分支和目录。SSH Key 保存后通过有界单线程队列触发 SCM Git 姓名定向校准；每日 XXL 补偿任务按“每仓库一次有界本地日志扫描 + SSH Key 用户游标分页批量比对”检查存量和已有姓名，不执行 fetch。`CommonParameterMemoryRegistry` 只管理显式注册的 JVM 内存参数，负责严格启动加载、匹配广播、本机全量手工刷新和失败保留；普通参数继续直读数据库。`NIGHT_EXECUTION_SLOT_CAPACITY` 只允许保存正整数，非法值在写库前拒绝。`UiTestPlatformConfigurationService` 对 `UITEST_BASE_URL/all` 执行请求时数据库直读和 HTTP/HTTPS 安全校验，不建立运行态缓存。

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
