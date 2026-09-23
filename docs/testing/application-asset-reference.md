# 应用资产共享引用验收

## 权限与配置

1. 管理员初始化并同步应用关联的资产库，选择总体和当前服务器均 READY、目录树标记可配置的目录，保存描述；共享列表应返回别名、相对目录、描述和版本。非目录、不可配置目录、路径穿越和版本过期均拒绝。
2. 普通有效应用成员打开“引用配置 → 应用资产库”，只能看到安全的共享配置；成员不持有资产库 Git 权限也能读取配置，但不能操作仓库 Git、保存或删除。移除应用成员后列表和工作区文件访问均拒绝。
3. 管理员删除配置后，成员重新进入工作区或显式刷新文件树，已删除引用和对应精确权限消失；无关 JSONC 字段、注释和用户权限保留。并发编辑同一 JSONC 时条件写重读重算一次，再冲突则返回 `CONFLICT`。

## 工作区与旧配置

1. 使用管理员和普通成员两个账号进入同应用各自个人工作区，刷新文件树，确认只读组合树显示同一已配置目录；成员可读取文件并将其加入对话，写、Git 和路径越界操作拒绝。发送真实 Run 前确认配置已对账，且成员无需资产库 SSH key。
2. 暂时使当前服务器资产副本不可用，确认成员配置列表仍可见，文件访问给出局部安全告警；副本恢复后重新刷新可读取。不要删除个人 JSONC 中已发布的共享声明。
3. 管理员切换资产库到含独有文件的新分支，等待两台服务器各自 READY；在两台服务器上的普通成员工作区分别显式刷新文件树，确认新文件出现并可读取。保持旧分支同路径文件的标签打开，刷新后正文必须更新或显示读取错误，不能继续展示旧缓存；随后各发起一次 Run，确认派发前 JSONC generation 已更新且 Run 使用新分支内容。
4. 升级前准备多个管理员个人 JSONC：相同别名且参数一致的目录自动发布一次；同别名目录或参数不一致的候选不发布并向管理员显示冲突；同文件中的其它合法条目继续发布。来源服务器离线时 `importPending` 保持为真，恢复后继续扫描；不能把待扫描报告成全部发布。

## 自动化检查

- 后端聚焦：`ApplicationAssetReferenceServiceTest`、`AssetReferenceWorkspaceJsoncReconcilerTest`、`ApplicationAssetReferenceControllerTest` 和既有 `ApplicationAutomationReferenceWorkspaceReconciliationServiceTest`。
- 前端聚焦：`reference-configuration-dialog.test.ts`、`reference-configuration-access.test.ts`、`backend-api.test.ts`，随后运行 workspace `typecheck` 和生产构建。
- PostgreSQL 在固定 `.env.test` 测试库以事务执行新 migration 并回滚，检查存量来源识别；交付包内同名 SQL 的 SHA-256 必须与源码一致。真实双账号验收需在部署该提交的环境执行。
