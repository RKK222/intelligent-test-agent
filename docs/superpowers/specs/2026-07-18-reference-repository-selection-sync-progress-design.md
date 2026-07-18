# 引用资产库选择同步进度设计

## 背景

引用配置左侧的已初始化资产库卡片在点击后会调用既有 `synchronizeReferenceRepository`：解析当前分支远端 HEAD，并让在线服务器副本收敛到固定提交。现有步骤弹层只绑定右上角“刷新 Git 指针”的 `verifyReferenceRepositoryPointers`，且只接受 `operation=VERIFY_POINTERS`，因此卡片触发的同步没有可见步骤，用户只能等待右侧状态变化。

## 决策

复用同一个进度弹层承载两类操作，但按真实操作分别展示，不改变请求语义：

- 点击已初始化资产库卡片：立即打开“同步资产库”进度，继续调用既有 `/synchronize`，步骤为“创建同步任务 → 各服务器同步 → 汇总同步结果”。
- 点击右上角“刷新 Git 指针”：保留“只读核验”进度，继续调用既有 `/verify`，步骤为“创建核验任务 → 各服务器核验 → 汇总核验结果”。
- 选择未初始化资产库仍只选中，不打开同步进度；初始化和切换分支保持现有专用交互。
- 卡片同步完成后继续按原顺序刷新目录树和工作区引用；不会额外发起 `/verify`，避免增加 generation 和等待时间。

不采用“同步完成后自动再核验”，因为同步 worker 已返回逐服务器实际 branch/HEAD，第二个只读 generation 既重复又会延长操作。不采用把卡片改为只读核验，因为这会破坏“选中即同步远端最新提交”的既有业务语义。

## 前端状态与数据流

把现有仅面向核验的进度状态抽象为操作进度，至少记录：

- `repositoryId`、前端请求序号；
- `operation`：`SYNCHRONIZE` 或 `VERIFY_POINTERS`；
- 请求阶段、服务端接受后的 generation 和安全错误。

卡片点击时先建立 `SYNCHRONIZE` 进度状态，再调用 `/synchronize`；右上角按钮建立 `VERIFY_POINTERS` 状态，再调用 `/verify`。弹层只消费同仓库、同操作且 generation 不低于本次接受代次的状态，沿用现有请求序号、选择代次和后端 generation fencing，旧轮询结果不得污染当前步骤。

逐服务器状态继续映射 `PENDING/PROCESSING/READY/BLOCKED/RETRY_WAIT/DEFERRED`。同步模式的说明使用“等待同步、同步中、已同步、同步失败、等待重试、离线延后”；核验模式保留现有文案。总体 `READY/FAILED` 分别结束第三步，活动状态继续每 2 秒读取 `/status`。

## 关闭、错误与兼容

- 请求或服务器操作活动期间禁止关闭步骤弹层和外层引用配置页，焦点限制在步骤弹层内；终态保留到用户手动关闭，并把焦点恢复到触发来源：卡片同步返回当前资产库卡片，核验返回右上角刷新按钮。
- POST 失败在弹层内显示安全错误和 `traceId`，允许原地重试同类操作；状态轮询临时失败继续自动重试。
- 如果点击的资产库已经处于活动状态，不重复 POST，沿用现有状态轮询并展示与后端 `operation` 对应的进度；无法可靠绑定新 generation 时不把旧终态误判为本次完成。
- 不新增或修改 HTTP 路径、请求体、后端状态、RunEvent、内部广播、数据库、manager 协议或环境配置。

## 测试

在 `reference-configuration-dialog.test.ts` 先增加失败回归，再实现最小修改：

- 点击已初始化资产库卡片后，在 `/synchronize` Promise 完成前立即出现同步进度弹层。
- 服务端接受后展示同步专用三阶段和逐服务器状态；轮询到 `READY/FAILED` 后保留结果。
- 同步请求失败可重试，关闭后焦点回到当前资产库卡片。
- 卡片同步不调用 `/verify`，右上角核验仍不调用 `/synchronize`，两类文案和 generation fencing 互不污染。
- 未初始化资产库、活动状态接管、切换仓库、关闭限制和 Escape/Tab 行为保持兼容。

完成后运行引用配置定向 Vitest、前端全量测试、typecheck、lint、build 和 `git diff --check`，并同步 agent-web README/PACKAGE 与引用配置用户手册。
