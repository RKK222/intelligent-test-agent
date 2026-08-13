# 2026-08-13 安全扫描整改与复测

## 已整改

- 小地球固定加载登录守卫下的同源 `/workspace-requirement-import`，父子窗口同时校验精确 `origin/source`，`postMessage` 使用 `window.location.origin`，URL 不再携带用户 ID、后端 IP、物理根路径或 token。
- 浏览器只查询本项目 `/api/v1/requirement-import/**`，写入只走现有文件 WebSocket route/ticket/RPC。服务端重新查询 TCDS 授权、名称、文档类型与 URL，不接受浏览器提供的路径或下载地址。
- `TEST_AGENT_TCDS_BASE_URL` 为必填部署变量；固定接口相对解析，TCDS 文档地址限定 HTTP/HTTPS、10 秒连接、30 秒请求、最多 3 次重定向、20 MiB 单文件和 200 MiB 单次总量。正式日志不记录 token、文档 URL、签名参数或正文。
- 普通、最近及支持访问 Workspace 响应不再返回物理根目录；`rootPath=workspace:{workspaceId}`、`physicalRootPath=null`。复制绝对路径改为点击后单文件 RPC，目录选择器由专用响应标记 `existingWorkspaceId`。
- 文件 WebSocket upgrade 前非消费式预检 ticket，实际 upgrade 时原子消费；缺失、过期、复用或缺少 Origin 统一返回脱敏 401。框架参数异常统一为 `VALIDATION_ERROR / 请求参数无效`，不回显 Host、IP、请求头或原始异常。

## 受控例外与扫描说明

- 文件 WebSocket URL 是必要协议入口，但只承载 60 秒一次性 ticket；ticket 服务端存储、预检不消费、upgrade 原子消费，且不写入正式日志。扫描器仅凭 URL 中出现 `ticket` 报警时按该控制链复测。
- 内部 WebSocket route 和 Java 路由是跨服务器文件能力的既有受控协议，不是浏览器可覆盖的任意代理地址。
- 用户会话标题属于用户业务内容，不用于文件路径、主机选择、日志模板或鉴权。仅因标题可控产生的静态告警需结合具体输出点复测。
- 超级管理员目录选择器仍按职责展示目标服务器绝对目录；它使用专用角色入口和一次性文件 ticket，不通过普通 Workspace 响应扩散。

## 复测命令与结果

- `cd backend && mvn -pl test-agent-integration,test-agent-workspace-management,test-agent-api,test-agent-system-management -am test`：通过。相关模块及依赖测试全部成功；依赖 Docker 的既有集成测试按原有条件跳过。
- TCDS 配置与网关专项测试：8 项通过，覆盖部署变量缺失、非法、合法注入，统一基础地址、超时、重定向与非 HTTP/HTTPS 下载地址。
- 需求导入、文档转换、文件 ticket/filter/RPC、统一异常专项测试：57 项通过，覆盖目录生成、路径碰撞、部分失败、ticket 过期/复用、只读空间和错误脱敏。
- `cd backend && mvn clean package -DskipTests`：22 个模块打包通过。
- `cd frontend && corepack pnpm test`：1942 项通过、1 项按既有条件跳过；`corepack pnpm typecheck` 与 `corepack pnpm build` 均通过，构建仅保留既有 chunk 体积告警。
- `bash -n deploy/internal/deploy-multi-backend-node.sh` 与 `git diff --check`：通过。
- 使用 `TEST_AGENT_TCDS_BASE_URL=<部署地址> ./restart-dev-services.sh --profile test --env-file .env.test --skip-backend-build --skip-frontend-build` 做真实启动时，前端可在 `http://127.0.0.1:3000` 返回 200；后端因本机测试 PostgreSQL `127.0.0.1:15432` 未运行而启动失败。未修改 `.env.test`，也未停用旧 9900 服务。

受上述数据库阻塞影响，本轮未能在真实登录会话重放普通 Workspace HTTP 响应、未授权 WebSocket upgrade 和完整 TCDS 导入请求；对应控制链已由上述单元/组件测试覆盖，待测试数据库恢复后仍需执行真实查询、重复覆盖、部分失败、文件树刷新及原扫描请求重放。
