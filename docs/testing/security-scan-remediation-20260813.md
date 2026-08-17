# 2026-08-13 安全扫描整改与复测

## 已整改

- 小地球固定加载登录守卫下的同源独立入口 `/workspace-requirement-import/`，父子窗口同时校验精确 `origin/source`，`postMessage` 使用 `window.location.origin`，URL 不再携带用户 ID、后端 IP、物理根路径或 token。
- 浏览器只查询本项目 `/api/v1/requirement-import/**`，写入只走现有文件 WebSocket route/ticket/RPC。服务端重新查询 TCDS 授权、名称、文档类型与 URL，不接受浏览器提供的路径或下载地址。
- TCDS 默认使用现场确认的企业局域网入口 `http://tcds-prod.sdc.icbc:9080`，允许 `TEST_AGENT_TCDS_BASE_URL` 覆盖；固定接口和 TCDS 同源文档请求统一携带现场 `toolId` header，重定向到跨域对象存储后不透传该 header。TCDS 文档地址限定 HTTP/HTTPS、10 秒连接、30 秒请求、最多 3 次重定向、20 MiB 单文件和 200 MiB 单次总量。正式日志不记录 token、文档 URL、签名参数或正文。
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

## 2026-08-14 TCDS 现场契约复核

- 应用默认地址与本地、企业部署模板已统一为 `http://tcds-prod.sdc.icbc:9080`，仍允许 `TEST_AGENT_TCDS_BASE_URL` 覆盖；浏览器继续只请求平台同源 API。
- JDK 25 下 `TcdsHttpGatewayTest` 与 `TcdsIntegrationConfigTest` 共 9 项、`TestAgentRuntimePropertiesBindingTest` 15 项通过；网关测试逐条断言登录、应用、子条目、兜底文档元数据、用户查询及 TCDS 同源文档均携带精确 `toolId`，并验证跨域对象存储请求不泄露该 header。
- 使用 `.env.test`/`test` profile 完整重启成功，后端 health/readiness 为 `UP`，前端 200，登录 CORS 正确，manager 最终 health 为 `HEALTHY`。
- 本机到 `tcds-prod.sdc.icbc:9080` 的 TCP 连接成功；无真实统一认证号的只读 HTTP 探针被上游直接断开，未取得 HTTP 状态，因此真实授权目录和文档导入仍需在有效登录会话中验收，不能用伪用户探针替代。

## 2026-08-14 TCDS 案例维护请求头复核

- 案例维护新增的 `GET /task/getTaskTypes` 与 `POST /graphDesign/createGraphCase` 已移除各自硬编码地址，统一通过 `TcdsHttpRequestFactory` 解析 `${TEST_AGENT_TCDS_BASE_URL:http://tcds-prod.sdc.icbc:9080}`，并在同源请求中注入精确 `toolId: 66f36bfa5c1c6105572b0118880261d6`。
- `TcdsCaseMaintenanceServiceTest` 锁定任务类型查询、案例写入和写入前实时校验三条调用路径的地址与 header；与原网关、装配、Controller、日志脱敏和应用配置测试合计 56 项通过。前端案例维护、编辑器入口与 backend-api 定向测试 137 项通过，全 workspace typecheck 和 production build 通过。
- 浏览器仍只访问平台同源 `/api/internal/platform/integration/tcds/**`；案例四列正文不进入 API 访问日志，跨域对象存储下载不继承 `toolId`。真实任务类型和案例写入需要有效企业登录主体，自动化测试未向生产 TCDS 写入数据。

## 2026-08-14 TCDS 导入交互与文档兼容复核

- 版本、应用改为项目内可控的可输入选择器，避免原生 `datalist` 按当前值过滤后误隐藏其它选项；聚焦输入框自动选中原值，用户可直接键入替换或点击下拉项，不需要先删除旧文字。版本下拉列出当前值和当前月份前后 3 个月建议，任意非空 TCDS 版本都可查询，切换应用不重置版本；TCDS 应用目录也只作搜索和快捷建议，展开时列出全部返回项，任意非空应用名称或简称都可查询，父页面传入的目录外应用保持原值。子条目筛选支持父/子编号与名称的多关键词匹配、仅看已选和清空筛选，筛选过程中保留选择。应用目录加载完成后立即解除筛选禁用，条目请求未完成时仍可切换，旧请求继续由请求代次隔离。筛选区使用 `minmax(0, ...)` 网格，输入框、目录和父子行均限制为 iframe 宽度。
- 文档转换兼容 TCDS 历史数据中 `.doc` 名称承载 DOCX 内容、`.ppt` 名称承载 PPTX 内容以及 Word 扩展名返回的文本内容；HTML/JSON 错误包络即使 HTTP 状态为 200 也拒绝写入。失败日志不记录文件名、地址、签名参数、token 或正文。
- `RequirementDocumentConverterTest` 与 `RequirementImportApplicationServiceTest` 回归覆盖 DOCX 字节使用 `.doc` 元数据，以及 `.doc/.docx` 名称实际返回 UTF-8/GB18030 纯文本、`text/plain`/`application/octet-stream`/`application/msword` 媒体类型的兼容路径；服务层完成可信重查、转换与工作区写入，纯文本回退不生成附件。`requirement-import-view.test.ts` 13 项通过，包含目录外父页面应用/版本、直接输入替换、完整建议展开、多关键词子条目筛选、仅看已选和清空筛选。真实 TCDS 文档下载仍需在有效企业登录会话中复测。
- `SUCCEEDED/PARTIAL` 结果只新增后端规范化的 `workspaceRelativeDisplayPaths=[spec/{父条目}]`，不返回物理路径或让前端拼接条目名称。父工作台刷新根节点后只逐层加载本批次父条目并等待完成，避免重放整棵 `spec` 导致大量文件 RPC；刷新失败保留导入弹窗并提示手工刷新。

## 2026-08-14 Word 轻量转换回退复核

- 用户反馈导入后生成的 Markdown 以压平文本为主，结构化 Word 渲染及图片附件对实际可读性收益有限，并可能放大长行文件在工作台编辑器中的渲染开销，因此删除独立 `WordToMarkdownRenderer` 和内部二进制附件写入路径。
- DOCX 恢复按段落与表格文本输出，旧 DOC 恢复 HWPF 文本提取；保留跨容器兼容、UTF-8/GB18030 文本回退、HTML/JSON 错误包络拒绝、路径规范化、覆盖导入和 20 MiB Markdown 上限。
- 转换器和完整导入服务回归继续锁定 `.doc` 名称承载 DOCX、`.doc/.docx` 名称实际返回纯文本，以及其它 Word/Excel/PowerPoint/文本原有转换路径。真实 TCDS 文档仍需有效企业会话最终复测。

## Workspace ID 全链路回归

- 运行态 `Workspace.workspaceId` 继续固定使用 `wrk_`，与应用模板 `applicationWorkspaceId=awp_`、应用版本 `versionId=awv_`、个人工作区 `personalWorkspaceId=psw_` 分离。前端缓存、文件 WebSocket、会话/Run、最近工作区和需求导入均只把运行态 `workspaceId` 当作文件与会话路由主键；模板和版本 ID 只用于菜单归属及高亮。
- 修复同一应用版本、同一模板、同一服务器下多个个人 worktree 被 `versionId + applicationWorkspaceId + linuxServerId` 误判为同一缓存项的问题。缓存现在只按精确 `workspaceId` 替换，同版本的 `default` 与自定义 worktree 可以同时保留。
- 普通托管工作区的编辑器和 Diff 重新传递精确运行态 `workspaceId`，用户点击时通过 `workspace.resolve-physical-path` 解析单文件绝对路径；分享会话、支持访问、体验空间、源码快照、引用文件和 Agent 文件均不开放该解析入口。
- 取消物理根路径下发后，工具事件中的绝对 Unix/Windows 路径、URI 形态和越界路径一律失败关闭，不能被当作工作区相对路径发送到文件 WebSocket；后端生成的可信相对 Diff 路径仍正常刷新文件树和编辑器。
- `mvn -pl test-agent-workspace-management,test-agent-opencode-runtime,test-agent-api -am test`：19 个相关模块通过，`test-agent-api` 565 项通过；覆盖 Workspace、个人 worktree、最近偏好、文件 route/ticket/RPC、会话/Run、分享、支持、体验和需求导入调用链。
- `corepack pnpm test`：124 个测试文件、1949 项通过、1 项按既有条件跳过；`corepack pnpm typecheck` 与 `corepack pnpm build` 通过。9 条 Workspace 定向 Playwright 用例全部通过，覆盖普通文件、切换竞态、最近个人 worktree、源码快照、体验空间和 Diff；全套 Playwright 另有与本改动无关的既有路由断言和引导弹窗遮挡失败，未把整套浏览器测试记为通过。

## 2026-08-14 TCDS 导入页面性能复核

- `/workspace-requirement-import/` 改为 Vite 独立 HTML 入口，主路由只兼容跳转，不再让 iframe 重复启动工作台。生产入口不再预加载 Element Plus、Monaco 或 `AgentWorkbench`，只加载 Vue、平台接口客户端和导入页资源。
- 版本与应用使用页面内轻量可输入下拉框，避免原生候选过滤问题，也不为三个筛选控件引入 Element Plus 全量 JS/CSS；筛选区继续使用受宽度约束的网格。生成蒙版保留旋转动画和重复操作阻断，但移除背景模糊，避免长文档生成期间额外占用 GPU。
- `workspace.requirement-import-items` 批量检查父子目录状态：一次取得工作区元数据，各相对路径仍逐一经过公共文件服务的越界与符号链接校验，避免大量条目重复查询工作区。
- 3000 端口真实登录页面热启动点击到弹窗出现约 0.4 秒，iframe 首屏可立即交互；本机 TCDS 请求仍约 1.7 秒后返回服务不可用，该网络等待不阻塞弹窗渲染，真实授权条目列表仍需企业网络可用时复测。
