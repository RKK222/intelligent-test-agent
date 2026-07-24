# XXL-JOB 集成测试与验收

## 自动化测试

后端至少覆盖：

- SSO 票据仅 `SUPER_ADMIN` 可签发、256 位随机、60 秒上限；Redis 5 Testcontainers 验证 Lua 原子一次消费和过期拒绝，确保不依赖 Redis 6.2 `GETDEL`。
- Redis 消费、JIT 和 XXL 登录异常必须落到 platform `503 unavailable` 状态页，不得渲染会访问父窗口 `adminTab` 的上游通用错误页；Cookie 默认 `Secure`，受控 HTTP 配置显式关闭时仍保持 `HttpOnly`、`SameSite=Lax` 和受限 Path。
- 平台 Token session digest marker 在登录、刷新、登出和过期后的写入/删除，以及 XXL `LoginStore` 的逐请求失效检查。
- JIT 用户按 `platform_user_id` 幂等、改名、稳定冲突后缀和无原生密码登录。
- 原生登录/改密/用户写入口禁用，用户列表只读保留。
- handler 参数、未知任务/策略、`GLOBAL_MUTEX`/`ALLOW_OVERLAP`、锁续租丢失、线程中断和异常脱敏。
- MySQL 8.4 全新 Flyway、重复启动、并发 migration、一个 executor 组、七个任务和无默认管理员；V5 后夜间分发任务必须启用且 Cron 为每分钟，路由/阻塞/过期/重试/全局锁策略保持 V4 契约。
- 每分钟扫描只读取已到 `slotStart` 且未过 `windowEnd` 的 `SCHEDULED`，不按 `NIGHT_WINDOW/ADMIN_CUSTOM` 模式过滤；单轮 500、目标分组 50、服务器并发 8，目标 Java 单批 Run 受理并发 4，接口不等待 Run 终态且没有专属队列。
- 普通用户缺失 `scheduleMode` 时保持标准夜间语义，伪造 `ADMIN_CUSTOM` 必须在创建任何 Session、幂等锁、会话锁或容量记录前返回 `FORBIDDEN`；超级管理员可创建白天完整分钟任务，边界为下一完整分钟至未来 24 小时，显示区间 1 分钟、重试窗口 15 分钟。
- `ADMIN_CUSTOM` 创建、改期、取消、Run 受理、永久失败和窗口过期均不得调用夜间容量预留/释放；角色被移除后仍可取消，但调整自定义任务必须返回 `FORBIDDEN`。
- 同一 task 的并发分发只能有一个 attempt 认领，租约续期/完成/恢复必须按 attempt fencing；HTTP 120 秒超时、响应丢失、Java 在 legacy Run 行与用户消息之间或远端已接收与 handoff 标记之间崩溃、租约过期和 5 分钟补偿均只命中同一 `sessionId + clientRequestId` Run 锚点。恢复必须按 remoteSessionId + dispatchMessageId 探测远端，已接收不重投、明确未接收才重投、UNKNOWN 不重投。
- Run 锚点受理后夜间任务保持 `DISPATCHED`，不受 Run 最终成功、失败或取消影响；锚点已创建但状态未回写时补偿可修复，owner Java 心跳在线时不得跨进程抢占，本机 watchdog 在 handle 消失后收敛，心跳消失后才允许跨进程恢复 `SCHEDULED`。
- 内部接口只接受固定目标和 1～50 个 taskId，必须验证标准 XXL token、常量时间比较、精确普通鉴权豁免、trace/防循环 header、同服务器多 JVM 的精确 backendProcessId 选择、目标不匹配和日志不含完整输入。
- 平台主应用保持 Reactive；Admin 使用独立 Servlet 端口；Admin 启动失败只上报 XXL health DOWN，不进入平台 readiness。
- Spring 单例初始化不得直接启动 executor；本机 Admin readiness 返回 HTTP 200 前，executor 端口和注册线程保持关闭，Admin 恢复后只启动一次。地址派生必须覆盖 IPv4、内部 DNS host、loopback Admin context path 规整，并以不回显原始监听 URL 的固定错误拒绝缺少 scheme/host 的地址；readiness 必须拒绝重定向和其它非 200 响应。
- Admin 子上下文即使继承到平台 `readinessState,db,redis` 配置，也必须覆盖为仅检查自身 MySQL，不能因未启用 Redis 而启动失败。
- 上游 `application.properties` 不会向平台主上下文暴露通用 Hikari/MySQL 数据源配置；重定位后的 Freemarker、调度超时等默认项仍由 Admin 子上下文加载，并由平台运行配置按优先级覆盖。
- 真实 Admin 必须以 HTTP 200 和正确 MIME 类型交付上游 `AdminLTE.min.css`、`_all-skins.min.css`、`adminlte.min.js` 及平台嵌入样式，防止通用 `dist/` 忽略规则再次造成不完整上游包。
- 带生产构造器和测试构造器的 Spring 组件必须通过真实应用上下文装配测试，确保生产构造器被明确选中且不会退回无参实例化。

前端至少覆盖：

- 超级管理员申请票据并用隐藏表单 POST 到 iframe，票据不进入 iframe URL。
- 非超级管理员不可见/403、刷新重新签票、票据过期、Admin 503 和平台会话失效状态；iframe 只有收到 Admin 登录成功页的同源 `ready` 握手才进入就绪态，普通 `load`/网关 502 不得误报成功。
- 平台登出后清空 iframe；原始 HTTP observer 对 ticket、token、cookie、password、secret 和 session digest 脱敏。
- 真实 XXL shell 的横向装饰必须幂等，账号为只读、菜单点击可滚入可视区；SSO 中转页、错误页和不可访问文档不得添加嵌入态 class，也不得改变现有 readiness 状态机。
- 普通用户完全不显示模式切换；超级管理员默认仍为夜间时段，可切换“测试时间”、使用 1/3/5 分钟快捷值或北京时间 `datetime-local`，提交前按当前时间重新校验。
- 自定义任务在当前会话卡和待执行任务页签显示“测试定时”和单个精确时间；旧响应缺少 `scheduleMode` 时按原夜间范围展示，角色移除后隐藏自定义任务调整入口但保留取消入口。

## 标准命令

```bash
mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am test
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -pl test-agent-app -am -DskipTests package

cd frontend
npm run lint
npm run typecheck
npm run test
npm run build

cd ..
docker compose -f deploy/local/docker-compose.yml config
bash tools/verify-internal-nginx-config.sh
bash tools/verify-internal-single-config.sh
bash tools/verify-internal-xxl-job-diagnostics.sh
bash tools/verify-dev-scripts.sh
```

Testcontainers 需要可用 Docker；Docker 不可用时相关 MySQL/Redis 测试会显式跳过，不能把跳过误报为已完成数据库与一次性票据验收。

`PlatformXxlSsoControllerTest` 必须覆盖平台 SSO 登录运行时异常：响应固定为 `503 + platform/xxl-sso-status + unavailable`，不能回退到上游 `framework/common/common.errorpage`。后者会在平台父页面没有 jQuery 时继续读取 `window.parent.$.adminTab`，浏览器控制台异常只是二次故障，不能替代服务端首因日志。

`verify-internal-xxl-job-diagnostics.sh` 完全使用临时夹具和假命令验证固定现场的只读脚本、SQL 与文档契约，不访问 `122.233.30.2`、`122.233.30.4`、`122.233.30.114`、`122.233.30.20` 或外部 MySQL `122.210.106.43`。文档变异夹具还会拒绝主动重放 SSO、触发任务，并用不执行命令的状态机按未被引号或反斜杠保护的换行、`;`、`&&`、`||` 和管道边界扫描全手册可执行代码块。解析 `sudo`/`env` 选项、空或带引号的 `KEY=value` 及 Docker/Compose/Podman 全局选项后，会拒绝读 Redis 票据/会话、执行 SQL DML、改写配置、重启/reload 服务和容器 `up/down/create/run/pause/unpause/scale` 等生命周期变更；无法安全解释的组合 wrapper 短选项、env split-string 或未知容器全局/子命令选项统一 fail closed，同时保留引号内文本与已知选项下只读 `ps` 命令。SQL 临时负向夹具还会拒绝 `INTO OUTFILE/DUMPFILE`、`GET_LOCK/RELEASE_LOCK/IS_FREE_LOCK/IS_USED_LOCK` 和用户变量赋值 `:=`，只做静态解析，不执行 SQL。证据扫描夹具覆盖绝对/相对 URL 的未脱敏 query 与 fragment。低熵 password/token/key 夹具还会断言只输出 `SET/UNSET`，不输出长度或无盐摘要；仅生产强随机 XXL access token 保留跨节点长度和 SHA-256 前缀。浏览器现场只能被动检查事故时已经保留的证据。

入口夹具使用正常浏览器网段地址，并逐一拒绝五个已知基础设施地址及 `ip` 缺失/失败/空结果；错误机器的 fake curl 调用哨兵必须不存在。Nginx 夹具拒绝全注释和单指令注释配置。三个脚本正常结束均断言最终 PASS/FAIL 摘要，误用与关键前提仍返回 `2`。证据扫描程序由 verifier 持有固定受审副本，手册展示块只做逐字节等值校验；恶意 `system()` Markdown 夹具必须被合同拒绝且哨兵文件不存在。

## 多节点人工验收

1. 在两台 Linux 各启动一个 Java，使用相同 Admin/executor 端口并共用 PostgreSQL、Redis 和 XXL MySQL。
2. 确认两个 executor 都自动注册到 `test-agent-backend`，注册地址不含 `linuxServerId` 或任何稳定 Linux 亲和字段。
3. 从平台系统管理页进入 iframe，确认首次 JIT 创建账号，刷新页面会重新签发票据但复用同一 XXL 用户；菜单处于同一横向导航栏、当前项为浅蓝选中态、右侧账号不可展开，窄屏可单行横向滚动且浏览器控制台无 AdminLTE 404。
4. 手动触发 `GLOBAL_MUTEX` 任务并制造跨节点重叠，确认只有一个 handler 真正执行，另一条日志为 `SKIPPED_LOCK_HELD`。
5. 临时用版本 SQL 新增或在测试库配置 `ALLOW_OVERLAP` 任务，确认 `ROUND` 可把相邻触发分配到不同 Java，单节点仍按 `DISCARD_LATER` 串行。
6. 从 XXL 页面发起停止，确认 handler 在线程中断后观察到 `ScheduledTaskContext.stopRequested()`。
7. 停止 MySQL 或配置错误凭据，确认平台主 API/readiness 仍可用、`xxlJobAdmin` health 为 DOWN，随后能按指数退避恢复。
8. 分别创建标准夜间任务和超级管理员白天 `ADMIN_CUSTOM` 任务，确认都不写 PostgreSQL `USER_PLAN`，XXL `opencode-runtime.night-execution-dispatch` 每分钟到点后按任务固化服务器调用目标 Java；自定义任务不改变 `night_execution_slot_reservations`。停止 XXL 后任务仍可提交，恢复后数据库扫描补发。
9. 启动两个共享 XXL MySQL 的 Java，确认两个 advertised host 的 executor 都出现在同一执行器组；再新增第三节点并只 reload 中央 Nginx，前两个 Java 不修改配置、不重启，执行器组最终出现三个注册地址。
10. 分别制造响应丢失、Java 在 legacy Run 行落库后/用户消息前退出、远端 prompt 已接收但本地 handoff 标记前退出、远端探测 UNKNOWN、锚点来源 ID 冲突、租约过期且 owner 在线/离线、07:00 窗口结束且本机 handle 仍在；确认补偿只接受当前任务锚点、没有第二个 Run，远端已接收时不会重投，且不会先写 `FAILED` 后由旧调用创建 Run。通用 stale legacy 扫描不得处理带 attempt 且未受理的 Scheduled 锚点。

单节点启动日志还必须确认：平台 Netty 可以先于 Admin 就绪；本机 Admin Tomcat/readiness 完成后才出现 executor 启动和 `ExecutorRegistryThread` 启动；同一次启动区间不得出现指向尚未监听本机 Admin 的 `registry error` / `Connection refused`。MySQL 中注册地址必须等于平台 advertised host 加 executor 端口。

## 数据与日志检查

- `xxl_job_user` 不存在默认管理员；JIT 行具有唯一 `platform_user_id` 和 SHA-256 session digest，不保存平台原始 Token。
- `xxl_job_info` 恰好七条且 `platform_task_key` 唯一；所有任务为 `ROUND + DISCARD_LATER + DO_NOTHING + retry=0`，夜间分发为每分钟 Cron `0 0/1 * * * ? *`。
- 旧 PostgreSQL scheduler 历史仍存在，但应用内没有 runner，不再产生新的 `CRON`、`MANUAL` 或 `USER_PLAN`；旧夜间 `PENDING/RUNNING/STOPPING USER_PLAN` 在短暂停机升级后均为 `SKIPPED`。
- URL、访问日志、应用日志和错误响应不得出现票据、Cookie、Token、MySQL 密码或完整 executor 参数中的敏感载荷。
- XXL 日志保留 30 天；PostgreSQL 已结束 scheduler 历史仍按 7 天清理。
