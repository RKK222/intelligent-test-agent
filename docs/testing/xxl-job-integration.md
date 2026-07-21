# XXL-JOB 集成测试与验收

## 自动化测试

后端至少覆盖：

- SSO 票据仅 `SUPER_ADMIN` 可签发、256 位随机、60 秒上限、Redis `GETDEL` 一次消费和过期拒绝。
- 平台 Token session digest marker 在登录、刷新、登出和过期后的写入/删除，以及 XXL `LoginStore` 的逐请求失效检查。
- JIT 用户按 `platform_user_id` 幂等、改名、稳定冲突后缀和无原生密码登录。
- 原生登录/改密/用户写入口禁用，用户列表只读保留。
- handler 参数、未知任务/策略、`GLOBAL_MUTEX`/`ALLOW_OVERLAP`、锁续租丢失、线程中断和异常脱敏。
- MySQL 8.4 全新 Flyway、重复启动、并发 migration、一个 executor 组、六个任务和无默认管理员。
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
bash tools/verify-dev-scripts.sh
```

Testcontainers 需要可用 Docker；Docker 不可用时相关 MySQL/Redis 测试会显式跳过，不能把跳过误报为已完成数据库与一次性票据验收。

## 多节点人工验收

1. 在两台 Linux 各启动一个 Java，使用相同 Admin/executor 端口并共用 PostgreSQL、Redis 和 XXL MySQL。
2. 确认两个 executor 都自动注册到 `test-agent-backend`，注册地址不含 `linuxServerId` 或任何稳定 Linux 亲和字段。
3. 从平台系统管理页进入 iframe，确认首次 JIT 创建账号，刷新页面会重新签发票据但复用同一 XXL 用户；六个菜单处于同一横向导航栏、当前项为浅蓝选中态、右侧账号不可展开，窄屏可单行横向滚动且浏览器控制台无 AdminLTE 404。
4. 手动触发 `GLOBAL_MUTEX` 任务并制造跨节点重叠，确认只有一个 handler 真正执行，另一条日志为 `SKIPPED_LOCK_HELD`。
5. 临时用版本 SQL 新增或在测试库配置 `ALLOW_OVERLAP` 任务，确认 `ROUND` 可把相邻触发分配到不同 Java，单节点仍按 `DISCARD_LATER` 串行。
6. 从 XXL 页面发起停止，确认 handler 在线程中断后观察到 `ScheduledTaskContext.stopRequested()`。
7. 停止 MySQL 或配置错误凭据，确认平台主 API/readiness 仍可用、`xxlJobAdmin` health 为 DOWN，随后能按指数退避恢复。
8. 创建一个夜间一次性任务，确认它仍写入 PostgreSQL `USER_PLAN`、按原 `executionAffinity` 执行，没有进入 XXL MySQL。
9. 启动两个共享 XXL MySQL 的 Java，确认两个 advertised host 的 executor 都出现在同一执行器组；再新增第三节点并只 reload 中央 Nginx，前两个 Java 不修改配置、不重启，执行器组最终出现三个注册地址。

单节点启动日志还必须确认：平台 Netty 可以先于 Admin 就绪；本机 Admin Tomcat/readiness 完成后才出现 executor 启动和 `ExecutorRegistryThread` 启动；同一次启动区间不得出现指向尚未监听本机 Admin 的 `registry error` / `Connection refused`。MySQL 中注册地址必须等于平台 advertised host 加 executor 端口。

## 数据与日志检查

- `xxl_job_user` 不存在默认管理员；JIT 行具有唯一 `platform_user_id` 和 SHA-256 session digest，不保存平台原始 Token。
- `xxl_job_info` 首批恰好六条且 `platform_task_key` 唯一；所有任务为 `ROUND + DISCARD_LATER + DO_NOTHING + retry=0`。
- 旧 PostgreSQL 周期任务和历史仍存在，但旧 runner 不再产生新的 `CRON`/`MANUAL` 执行。
- URL、访问日志、应用日志和错误响应不得出现票据、Cookie、Token、MySQL 密码或完整 executor 参数中的敏感载荷。
- XXL 日志保留 30 天；PostgreSQL 已结束 scheduler 历史仍按 7 天清理。
