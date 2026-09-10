# 企业内多后台部署

当前代码的普通 HTTP、RunEvent SSE、用户 OpenCode 进程路由、内部模型代理和受控 WebSocket 支持两个或更多后台节点。页面首次查询 `/processes/me` 时不带服务器路由头，仍按 `least_conn` 进入任意 Java；用户未绑定时，入口 Java 会基于共享 Redis 快照选择进程总数最少且存在本地可用容器的服务器，必要时通过公共 Java→Java 转发器把状态或初始化请求单次转发到该服务器，目标 Java 再完成本地容器选择与原子端口预留。拿到 binding 中的 `linuxServerId` 后，前端仅在页面内存保存它，并给后续用户 OpenCode、会话、Run、SSE 和本地工作区请求增加 `X-Test-Agent-Linux-Server-Id`。Nginx 通过静态白名单把这些请求直接送到一机一 Java 的目标节点，避免正常链路中的 Java→Java 二次转发。

这个头只是首跳性能提示，不是权限或路由事实源。缺失、未知或过期时仍由 `least_conn` 和后端公共路由程序根据真实 binding 兜底；尚无 binding 的精确状态/初始化请求则重新按当轮集群负载选服，已有 binding 不因负载变化迁移；Nginx 在转发前删除外部传入的路由头和 `X-Test-Agent-Backend-Routed`，后端鉴权、Session 归属和运行上下文校验继续生效。workspace PTY 和 Agent 配置进度 ticket 响应沿用签发 Java 地址；当前 HTTP 现场的服务器终端 ticket POST 也复用同一静态路由表，响应再返回签发 Java 的直接 `ws://` 地址，因此 ticket 的签发和消费不会跨 JVM，也不依赖 sticky。

本文用两个后台举例：

| 角色 | 地址/身份 |
|---|---|
| 浏览器域名入口 | `http://mimo.sdc.cs.icbc:9996`，企业入口继续转发到实体 Nginx `:80` |
| 浏览器 IP 入口 | `http://122.233.30.2:9996`，直连实体 Nginx 新增监听 `:9996` |
| 前端实体 Nginx | `122.233.30.2:80` + `122.233.30.2:9996`，安装目录 `/data/apps/nginx` |
| 后台 A + worker A + 工具容器 A | `122.233.30.4` / `test-agent-backend-122-233-30-4` / `:18120,:18121` |
| 后台 B + worker B + 工具容器 B | `122.233.30.114` / `test-agent-backend-122-233-30-114` / `:18120,:18121` |
| Redis | `122.233.30.20:6379` |
| PostgreSQL | `122.233.30.147:5432/postgres` |
| XXL MySQL | `122.210.106.43:3306/xxl_job`（外部共享 MySQL，当前使用既有 `root` 账号） |
| 企业内部模型 | `ai-code.sdc.enterprise:9070` |

## 当前增量说明（2026-09-10 纳入客户端完整制品）

- 2026-09-10 现场截图确认：`.2` 下载目录和用户电脑客户端均为 `20260907093905`。上一包错误使用
  `20260901203844` 基线，前端部署在客户端校验阶段报该旧版 `manifest.json` 缺失，尚未解压新前端或 reload Nginx；
  日志中的 `Configuration installed` 只表示配置已经备份并安装，不代表前端部署成功。`.114` 已显示后台验证通过。
- 用户随后明确要求把客户端一起封入。本次保留 9 月 9 日同批后端 JAR/lib 和前端 tar 字节，将已验签的
  `20260909110624` 客户端完整制品标为 `included`；不再传入客户端 `reuse` 基线，也不重新生成版本。
  旧外层 `b819956d82c4792c797a0828c357126c9e79228117f1434373156ef79a50a0d4` 及后续只修正复用基线的
  `16207e60514f091b60aa6a4415ba9ae34abe06a295486cc51c5e54980cc47b06` 均由本次完整交付替代。
  已成功升级的后台无需重部署；新固定名完整包只需传至 `.2`，执行 `deploy-frontend-node.sh` 同时更新前端和客户端分发。

- 用户已确认企业实际部署的浏览器 CDP 修复包基线：内层 SHA-256 为
  `bbbe20688aa6a5541886b7afc383c6a8236468a08ea1c3a17cdcc00ef26224fa`，外层 SHA-256 为
  `f6af13005352fa1f64dad4a9bd9fe762f10094ae0c3f87e85c0eae0ee92574f2`。`7933e350…` 是未部署候选，
  不能作为增量基线；本轮交付包含该候选及其后的 `release` 变更。
- 本轮公共 `local_browser.ts` 先完成企业 360 CDP 连接兼容：Tool 在 OpenCode/Bun 内使用原生
  WebSocket 实现 Playwright 公开的 `ConnectOverCDPTransport`，避开 Node `ws` transport 已完成 HTTP 101 却不进入
  connected 的现场问题。CDP 版本响应返回的 WebSocket 继续限制为客户端 Relay 已确认的同一 `127.0.0.1` 随机端口。
- 企业现场随后确认 `mimo.sdc.cs.icbc:9996` 会跳转到不同 origin 的 AAM 登录页；旧 Tool 在目标请求发出前按安全策略阻断，
  却把生成的 `ERR_BLOCKED_BY_CLIENT` 错误页作为成功结果返回。企业使用方明确选择全量放行所有 HTTP(S) origin，当前 Tool
  不再申请站点授权，跨 origin 重定向、链接、JS 跳转和 popup 可继续；非 HTTP(S) 协议、内嵌凭据、提交、上传、下载及
  模型输出脱敏边界保持不变。
- 当前 `release` 同时包含已提交的麒麟个人 SSH 私钥本地文件选择：只调整前端设置页、新手引导和用户手册，继续复用既有
  浏览器端加密与 SSH Key 保存接口，不新增后端 API；文件选择器需要按 `Ctrl+H` 显示 `.ssh` 隐藏目录，不能选择 `.pub`。
- worker runtime、toolbox 为 `reuse`，本地客户端为 `included`。现有 programs 和签名公共能力依赖已经包含
  `playwright-core@1.61.0`；目标用户不需要系统 Node，也不在企业现场执行 npm。两台后台不因本修复重建或重启
  worker/manager；`.4` models 灰度不由标准包覆盖，CK/Mem0/BGE/pgvector 不重新部署、重启或同步。
- 内层 ZIP 的 `dist/local-opencode-client/` 只含 `20260909110624` 一个 release、安装器、普通用户包、签名清单、
  JDK、OpenCode 和离线公共能力，不带本机构建目录的历史 release。`.2` 分发版本从 `20260907093905` 更新到
  `20260909110624`；这是用户明确要求的完整客户端交付，包体因此约为 424 MiB。客户端 JDK、OpenCode 和初始公共能力
  摘要与现网 0907 版一致；客户端主模块源码没有新增功能，JAR 中的共享 `GitRemoteService` 已包含远端 archive
  commit/ref 回退方法，不能仅凭客户端目录无源码变化断言两个完整 JAR 相同。
- `.2` 部署成功且新 catalog/manifest 可下载后，超级管理员在客户端版本管理中同步 release/catalog。已有用户在本人
  麒麟终端执行 `"$HOME/.local/bin/test-agent-local-client" setup`，随后用同一路径 `--version` 检查为
  `20260909110624`；该安装流程保留凭据、实例 ID 和工作区，并复用签名校验通过的 JDK/OpenCode 缓存，无需 sudo。
  从网页重新下载、完整解压普通用户包再双击，也走相同的就地升级流程。
- 相对已部署的 360 CDP 包，本轮后端和前端还包含个人 SSH 私钥文件选择、SSH Key 变更后工作空间实时复检，以及标准应用
  工作空间“按分支新建版本”：只接受合法的 `feature_testagent_yyyyMMdd` 远程分支并从分支派生版本。创建版本请求新增可选
  `branch` 字段，旧版仅传 `version` 的调用保持兼容；响应、RunEvent/SSE、数据库和 Flyway 均未变化。AAM 地址继续为
  `http://tcds-prod.sdc.icbc/aam/onlyLogin/`。
- 当前版本菜单展开后直接提供“＋ 按分支新建版本”，同时保留左下角工作空间菜单入口；顶部不再显示重复的常驻入口。批量
  子条目生成不再依赖旧 Chromium 可能缺失的 `Array.prototype.at()`，Windows 路径仍能正确生成文件名。
- 新建版本副本回写按 PostgreSQL 微秒精度比较 `createdAt/updatedAt`，避免数据库时间四舍五入导致的
  `updatedAt must not be before createdAt` 误报；跨微秒的真实倒序继续拒绝。该修复不新增 API、事件或 Flyway migration。
- 应用代码库工作区首次创建会话时改按既有分类访问策略校验 `APP_SOURCE` 副本，避免它不在个人工作区关联表中而误报
  “Workspace 不存在”。远端 Git 服务拒绝以 commit 执行 archive 时，目录读取会受控回退到同一分支的完整 ref，随后仍以
  固定 commit 做物化一致性校验；Windows 清理只读 Git pack 文件也已兼容。
- 个人工作区跨服务器搬迁失败现在记录受控阶段、白名单原因和路径摘要，日志可辅助定位且不泄露绝对路径、文件名、凭据或 stderr。
  设置中的工作空间名称可原地编辑、回车确认保存，目录树滚动条始终可见；这些改动均不新增部署节点、API 或 Flyway migration。
- `deploy/internal/local_browser.ts` 是公共 Tool 的受控模板，平台包不会直接覆盖企业公共配置；企业公共配置 Git
  的已发布 commit 才是权威源。部署包到位后仍须把该模板同步为新的公共配置 commit，再由平台构建签名完整能力包，
  用户确认“更新公共能力”并等待 OpenCode 重启；未完成该发布链路时，旧 Tool 不会自动获得修复。
- PostgreSQL、XXL MySQL、ClickHouse migration 与已部署基线一致，本轮三套历史均不应新增记录；最终 persistence JAR
  仍须按下文核验，未知历史或 checksum 必须停止，不能用 `repair` 或 `outOfOrder` 绕过。
- Workflow/LobeHub、独立 memory 制品和 trace 继续 `disabled`；本轮没有新增部署节点、事件、数据库或客户端协议变更。

## 1. 正式拓扑

```text
浏览器 -> mimo.sdc.cs.icbc:9996 -> 企业入口 -> 122.233.30.2:80 Nginx
       `-> 122.233.30.2:9996 ----------------------^ 同一个 Nginx server 块
              无/未知头 |-> least_conn(Java A, Java B)
          server A 路由头 |-> 122.233.30.4:8080   Java A -> 本机 worker A/OpenCode A
          server B 路由头 `-> 122.233.30.114:8080 Java B -> 本机 worker B/OpenCode B

Java A <---------------- 互访 8080 ----------------> Java B
   |                                                     |
   +------------- 共享 PostgreSQL / Redis / XXL MySQL ----+
   |                                                     |
   `-> ai-code.sdc.enterprise:9070          ai-code.sdc.enterprise:9070 <-'

.2 Nginx -> .4:18120/.114:18120(backup)   IT-Tools
         `-> .4:18121/.114:18121(backup)   OmniTools
```

必须同时满足：

1. 所有 Java 使用同一版本 JAR 和 `backend/lib/`，共享同一 PostgreSQL 和 Redis。
2. 每台物理服务器使用唯一、长期稳定的 `TEST_AGENT_LINUX_SERVER_ID`。
3. 每台后台只运行一个本机 worker；worker 只连接本机 `.serverhost:8080`。
4. 各节点 `/data/testagent/data` 是本机目录，不做跨服务器共享挂载。
5. 所有后台之间能双向访问对方声明的 `TEST_AGENT_SERVER_ADVERTISED_HOST:8080`。
6. Nginx 能访问全部后台 `:8080`；每台 Java 宿主机都能访问 PostgreSQL、Redis 和 `ai-code.sdc.enterprise:9070`。
7. 所有后台启用相同的服务器广播 channel。
8. 每台后台都要初始化本服务器公共 OpenCode 配置。
9. 每台 Linux 只运行一个 Java，Admin 固定与同 JVM executor 配对；executor 注册地址复用平台 advertised host，必须可从全部 Admin 节点访问。XXL 路由不携带 `linuxServerId`。

不要照搬旧双后台文档中的 RunEvent Redis bus 开关；当前代码已经删除该参数。运行态和跨 Java 路由使用已有的 Redis 存储、服务器快照及公共转发程序。

## 2. 网络和端口

| 来源 | 目标 | 用途 |
|---|---|---|
| 企业浏览器网段 | `mimo.sdc.cs.icbc:9996`、`.2:9996` | 同一版本前端的域名和 IP 双入口 |
| `.2` | `.4:8080`、`.114:8080` | Nginx 负载均衡 |
| `.2` | `.4:18080`、`.114:18080` | 同源 XXL Admin 负载均衡 |
| `.2` | `.4:18120/18121`、`.114:18120/18121` | 工具静态页主用/备用代理 |
| 企业浏览器网段 | `.4:8080`、`.114:8080` | PTY、Workspace/Agent 文件和 Agent 配置进度 WebSocket 按 ticket 签发节点直连 |
| `.4` | `.114:8080` | Java A 转发到 Java B |
| `.114` | `.4:8080` | Java B 转发到 Java A |
| 每台 worker 容器 | 本机 Java `:8080` | manager WebSocket、内部模型代理 |
| 每台后台 | PostgreSQL、Redis、外部 XXL MySQL | 平台持久化、运行态与 XXL 独立调度库 |
| 每个 Admin | `.4:9999`、`.114:9999` | 调度所有 Java executor；仅可信内网开放 |
| 每台后台 | `ai-code.sdc.enterprise:9070` | 企业内部模型调用 |
| Java 后台 | 每台后台 `14096-15095` | 访问本机或目标服务器上的用户 OpenCode 进程；浏览器不直连这些端口 |

两个服务器可以重复使用 `14096-15095`，因为 IP 不同；同一台服务器的宿主机和容器端口必须同号。每台 worker 发布正好 1000 个端口，两台合计提供 2000 个端口坐标；当前全局 `OPENCODE_MANAGER_MAX_PROCESSES=30` 会分别热推到两台 manager，因此每台实际最多运行 30 个用户 OpenCode 进程。正式部署不使用 `--network host`、`19070` relay 或额外 model relay。域名和 IP 虽然都由浏览器访问 `9996`，但域名链路已有企业入口把 `9996` 转到实体 Nginx `80`；实体 Nginx 额外监听 `9996` 是为了让 IP 直连，不得删掉原来的 `listen 80`。

端口池容量不等于已经验证的并发承载能力。1000 个端口只表示 manager 可分配坐标上限；CPU、内存、Docker `PidsLimit=8192`、`nproc=8192`、文件句柄和外部模型容量仍需上线前压测，不能仅凭端口映射宣称容量达标。

两台后台的 Docker 18.09.7 必须在 `/etc/docker/daemon.json` 合并配置
`"userland-proxy": false` 并经维护窗口重启 Docker，否则为每个发布端口创建代理进程时会在
大端口池启动中途报 `iptables ... resource temporarily unavailable`。`.4` 重启 Docker 前先优雅停止
`postgres` 容器，重启后先恢复并验证 PostgreSQL `5432`，再启动 worker。
`opencode-worker-docker.sh` 会在删除现有 worker 容器前执行该前置校验。

部署前验证：

```bash
# 在 .2；如果已有非 Nginx 进程占用 9996，先停止并确认归属，不能直接覆盖。
ss -lntp | grep ':9996 ' || true

# 在 .4
curl -fsS http://122.233.30.114:8080/actuator/health
nc -vz 122.233.30.20 6379
nc -vz ai-code.sdc.enterprise 9070

# 在 .114
curl -fsS http://122.233.30.4:8080/actuator/health
nc -vz 122.233.30.20 6379
nc -vz ai-code.sdc.enterprise 9070
```

## 3. Mac 打包与分发

只构建一份版本，禁止两个后台分别打包。企业包以当前本地工作树为源码输入，允许明确要交付的未提交改动参与构建；打包前必须先合并已完成的相关分支、确认没有未解决冲突，并记录当前 HEAD 和工作树状态，不为打包擅自清理或切换源码：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
git rev-parse HEAD
git status --short
test -z "$(git diff --name-only --diff-filter=U)"
VITE_TEST_AGENT_API_BASE_URL="" \
  deploy/internal/package-release.sh --output-dir deploy/internal/dist
deploy/internal/package-release.sh --python-libs-only \
  --output-dir deploy/internal/dist
```

当前 release 默认不启用 LobeHub，命令中不得添加 `--with-lobehub`。默认包必须满足：组件清单中的 LobeHub 为 `disabled`、无 LobeHub 运行制品、前端无入口且深链接回到工作台。

空值是有意配置：前端统一使用同源相对 `/api`，所以从域名打开时请求域名，从 IP 打开时请求 IP。不得固定成其中任一 origin，否则另一个入口会重新产生跨域或名称解析问题。

发布脚本在输出目录保存组件指纹，只对三个大组件做增量判断：

- `worker runtime` 把 OpenCode Manager、OpenCode runtime、Codex MCP、Node/MCP SDK、bubblewrap、worker 镜像和 `test-agent-programs.tar.gz` 视为一个不可拆分单元；其中任一项变化就全部重建并进入 ZIP。
- `toolbox` 把 IT-Tools、OmniTools、完整修改源码和目录文件视为一个单元；其中任一项变化就全部重建并进入 ZIP。
- `local OpenCode client` 把客户端 JAR、麒麟 ARM64 用户包、JRE、OpenCode 归档、安装脚本和签名清单视为一个单元；客户端输入未变化时不再进入 ZIP。

Python 的 pandas、Excel、Word 和 JSON 第三方库是第三个、完全独立的交付单元，不进入内层 ZIP，也不改变 worker 指纹。库升级只重新生成 `test-agent-python-libs-py313-linux-amd64.tar.gz` 及校验文件，然后分别部署到两台后台。该归档由 worker 内的 Linux GNU tar 生成并以相同实现复核，禁止使用会写入并隐藏 `._*` AppleDouble/PAX 成员的 Mac 归档结果；目标机出现 `Unsafe or unexpected archive entry` 时停止部署并更换原始归档，不得忽略成员或重新计算 SHA。

首次构建、指纹状态丢失或组件变化时，清单为 `included`；未变化时为 `reuse`，内层 ZIP 不再重复携带该组件的大文件。必须持续使用同一个输出目录，或用 `--component-state-file <稳定路径>` 保存基线。新装机、扩容新节点、灾备恢复和状态不可信的交付必须加 `--include-all-components`；增量包只允许升级已有且组件健康的 `.4/.114/.2`，不能用于空机器。迁移到该机制后的第一次构建也应使用全量命令建立可信基线；部署成功后每台后台会把实际安装指纹写入 `/data/testagent/config/release-component-state.env`，`.2` 的客户端则按发布清单逐文件校验版本、签名和全部制品哈希，后续复用不会复制、备份或替换客户端目录：

若 `.4/.114` 的上一轮灰度已确认部署成功，但当时尚未登记组件状态，可用 `--worker-runtime-baseline-file` 重新登记门禁。baseline 同时固定上一轮源码提交、内层 release SHA-256 和 worker 指纹；封包时必须与本轮 worker 构建输入一致。节点部署仍会先检查 Manager/OpenCode/Codex、Tool runtime 和现有 worker 健康，全部通过后才补写 `/data/testagent/config/release-component-state.env`。该动作不加载镜像、不重启 manager/worker，也不替换 `.4` 与 `.114` 各自活动的模型清单。

```bash
deploy/internal/package-release.sh \
  --worker-runtime-baseline-file deploy/internal/release-baselines/20260813-qwen-gray.env \
  --local-client-baseline-file deploy/internal/release-baselines/20260818-local-client-entryfix.env \
  --output-dir deploy/internal/dist
```

```bash
VITE_TEST_AGENT_API_BASE_URL="" \
  deploy/internal/package-release.sh --include-all-components \
  --output-dir deploy/internal/dist
```

只查看本次组件计划而不构建可执行：

```bash
deploy/internal/package-release.sh --component-plan-only \
  --output-dir deploy/internal/dist
```

### 客户端是否进入平台包的强制判定

客户端托盘/启动器显示的是用户电脑正在运行的版本，`.2` 的 `stable/manifest.json` 表示当前下载分发版本；
两者可能不同，平台增量门禁校验后者。2026-09-10 已取得现场版本 `20260907093905`，manifest SHA-256 为
`8976c9327e99d4be2f3a7e852936dbd220f996d06bdd700abbb12998ea859ba3`，签名文件 SHA-256 为
`e057bd95100741909bb1ffec9288b4966f78ba3382196bcf2cfcc73eb5044bfd`。仅版本号变化也会改变签名清单摘要，
因此即使程序功能相同也必须使用真实分发基线；禁止恢复旧版本目录、关闭校验或修改现场组件状态来绕过。

前后端业务改动本身不需要重新签发客户端。每次企业打包前，先在 `.2` 留存**已实际部署**客户端的版本、签名清单摘要和组件状态；不能用 Mac 构建目录或未部署候选替代：

```bash
awk -F'"' '$2 == "version" { print "installedClientVersion=" $4; exit }' \
  /data/testagent/dist/local-opencode-client/stable/manifest.json
sha256sum \
  /data/testagent/dist/local-opencode-client/stable/manifest.json \
  /data/testagent/dist/local-opencode-client/stable/manifest.json.sig
awk -F= '$1 ~ /^TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT/ { print }' \
  /data/testagent/config/release-component-state.env
```

只有客户端 JAR/启动器、下载或控制域名、组织签名公钥、JDK/OpenCode 输入 SHA-256、公共能力发布 commit 或能力归档摘要发生变化，才递增 `TEST_AGENT_LOCAL_CLIENT_VERSION` 并允许客户端为 `included`。普通前端、后端、部署手册或客户端 README 改动都不是理由。

上述输入未变化时，以目标机已安装版本和摘要作为受控基线先执行 `--component-plan-only`。预期输出必须是 `local client component: reuse`；最终内层 `release-components.env` 必须为 `TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT=reuse`，且 ZIP 中不得存在 `dist/local-opencode-client/`。若计划显示 `included`，特别检查是否只是人为递增了版本号；立即停止，不得先打出约 400 MiB 的候选再解释。

客户端当前输入与目标机已安装摘要不一致时，也不得为了缩小包而手写组件状态、伪造 baseline 或强行标记 `reuse`。必须明确选择“按新输入签发并全量下发客户端”或“恢复与已部署版本相同的受控输入后重打平台包”。

用户明确要求将客户端完整制品纳入时，允许复用已经与当前代码匹配且验签通过的不可变客户端候选，将组件标为
`included`；仍只携带当前一个版本，不必为了重新封包递增版本。检查输入变化时同时核对打入 shaded JAR 的
`test-agent-common`、`test-agent-workspace-filesystem` 等共享依赖；现有源码指纹不能替代最终 JAR 的内容对比。

交付：

```text
deploy/internal/dist/test-agent-internal-release.zip
deploy/internal/dist/test-agent-internal-release.zip.sha256
deploy/internal/dist/test-agent-python-libs-py313-linux-amd64.tar.gz
deploy/internal/dist/test-agent-python-libs-py313-linux-amd64.tar.gz.sha256
```

内层 ZIP 重建后必须立即用本次内层 ZIP 和三台节点包重建固定名外层包；不能只校验历史外层包自身 SHA 后继续交付：

```bash
export TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY="$PWD/.secure/local-client-signing-public.pem"
deploy/internal/package-two-backend-complete.sh \
  --release-archive deploy/internal/dist/test-agent-internal-release.zip \
  --nodes-dir /path/to/prepared-node-packages \
  --output-dir deploy/internal/dist

inner_sha="$(shasum -a 256 deploy/internal/dist/test-agent-internal-release.zip | awk '{print $1}')"
embedded_sha="$(unzip -p deploy/internal/dist/test-agent-two-backend-complete.zip \
  test-agent-two-backend-complete/test-agent-internal-release.zip | shasum -a 256 | awk '{print $1}')"
test "${inner_sha}" = "${embedded_sha}"
```

最后一条必须返回 0，失败时停止分发并重新封装外层包。

后端是瘦启动 JAR + 外置依赖目录结构；Flyway SQL 实际位于
`dist/backend/lib/test-agent-persistence-0.1.0-SNAPSHOT.jar`，不是
`dist/backend/test-agent-app.jar`。打包脚本和外层封装脚本会逐项校验工具盒子企业 migration、
LobeHub 主/两条兼容 migration、公共 Agent rollout 纠错 migration、QA 历史兼容、通知处置类型和体验工作区
migration 的固定 SHA-256；后台部署还会
比较发布包与安装后 persistence JAR 的完整文件 SHA，防止旧解压目录或旧 `backend/lib` 被继续使用。

Mac 只负责构建；U 盘导入企业网后，中转机固定在 `~/Desktop/mimoagent/0709` 校验和分发，不得在中转机使用 `/data/0709`。`/data/0709` 只是 `.4/.114/.2` 目标服务器的接收目录。当前固定名外层包在中转机执行：

```bash
cd ~/Desktop/mimoagent/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -t test-agent-two-backend-complete.zip
```

然后由中转机逐台发往目标服务器。`/data/0709` 是既有接收目录，常规增量发布不再重复执行
`ssh ... install -m`；只有首次初始化且确认目录不存在时，才单独创建一次该目录。

```bash
for host in 122.233.30.4 122.233.30.114 122.233.30.2; do
  scp ~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip \
    ~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip.sha256 \
    "root@${host}:/data/0709/"
done
```

平台 zip 和校验文件只复制到：

```text
122.233.30.2:/data/0709/
122.233.30.4:/data/0709/
122.233.30.114:/data/0709/
```

三台目标机都先校验并解压外层包；随后才可校验其中的内层包：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
sha256sum -c test-agent-internal-release.zip.sha256
unzip -t test-agent-internal-release.zip
```

如果需要先按现有现场生成无占位符的逐机配置和操作脚本，只需把独立采集脚本复制到三台机器，
不需要上传或解压完整发布包：

```bash
chmod 0700 /data/0709/collect-multi-backend-context.sh
```

然后在 `.2` 使用 `frontend` 角色，在 `.4`、`.114` 使用 `backend` 角色，并显式传入
`--include-sensitive`。该模式只采集原始 env、后台身份/systemd unit 和 Nginx 配置，保留其中的密码、
token，但不采集 JAR/RSA、日志、Docker、programs、worker 镜像、业务数据或前端文件；输出权限为
`0600`，且强制不超过 `1 MiB`。完整命令和内容清单见
[企业内部署文档入口](README.md#现场配置轻量敏感采集)。采集不会改变配置或重启服务。

## 4. 每个后台的 backend.env

以下两份都是可整文件替换的完整配置。两台机器必须把 PostgreSQL 密码、manager token、内部代理 key、XXL MySQL 密码、XXL access token 和 SkillHub Access Key 这 6 个同名 `REPLACE_...` 替换为同一组现场值。本次现场纳管密码已更新到交付包内 `.4/.114` 的敏感 `backend.env`，不在本文或 Git 模板中明文记录。密码包含 `=`、`@`、`*` 等特殊字符，必须作为 dotenv 原值写入，不能 `source` 文件或通过命令行传递。模板按 Redis 无密码、平台 API token 为空填写；如果现网这两项非空，必须保留现网值。替换前先备份原文件：

```bash
install -d -m 0755 /data/testagent/config
cp -a /data/testagent/config/backend.env \
  /data/testagent/config/backend.env.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null || true
```

两台 Java 共享数据库，必须部署同一交付 JAR，并统一使用其中的 `BOOT-INF/classes/rsa-private.key`。两台 `backend.env` 都不得配置 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH`；JAR SHA 或内置 RSA 不同时禁止同时接入流量。

后台 A `.4` 的 `/data/testagent/config/backend.env` 全文：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4
SYS_DATA_ROOT_DIR=/data/testagent/data
TEST_AGENT_OBSERVABILITY_TRACE_ARCHIVE_ROOT=/data/testagent/data/agent-observability/traces
TEST_AGENT_OBSERVABILITY_TRACE_WARNING_FREE_BYTES=1073741824
TEST_AGENT_OBSERVABILITY_TOKEN_TTL=7d

TEST_AGENT_DB_URL=jdbc:postgresql://122.233.30.147:5432/postgres
TEST_AGENT_DB_USERNAME=postgres
TEST_AGENT_DB_PASSWORD=REPLACE_PRODUCTION_DB_PASSWORD
TEST_AGENT_DB_DRIVER_CLASS_NAME=org.postgresql.Driver

TEST_AGENT_XXL_JOB_ENABLED=true
TEST_AGENT_XXL_JOB_MYSQL_URL=jdbc:mysql://122.210.106.43:3306/xxl_job?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
TEST_AGENT_XXL_JOB_MYSQL_USERNAME=root
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=REPLACE_XXL_JOB_MYSQL_PASSWORD
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=REPLACE_XXL_JOB_ACCESS_TOKEN
TEST_AGENT_XXL_JOB_ADMIN_PORT=18080
TEST_AGENT_XXL_JOB_EXECUTOR_PORT=9999
TEST_AGENT_XXL_JOB_COOKIE_SECURE=false

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=
TEST_AGENT_REDIS_TIMEOUT=1s

TEST_AGENT_AAM_BASE_URL=http://zfw.sdc.cs.icbc
TEST_AGENT_AAM_CONNECT_TIMEOUT=3s
TEST_AGENT_AAM_REQUEST_TIMEOUT=5s
TEST_AGENT_AAM_MAX_RESPONSE_BYTES=65536

TEST_AGENT_SKILLHUB_ENABLED=true
TEST_AGENT_SKILLHUB_BASE_URL=http://ai-code.sdc.icbc/icbc/skill
TEST_AGENT_SKILLHUB_ACCESS_KEY=REPLACE_SKILLHUB_ACCESS_KEY
TEST_AGENT_SKILLHUB_CONNECT_TIMEOUT=10s
TEST_AGENT_SKILLHUB_REQUEST_TIMEOUT=30s
TEST_AGENT_SKILLHUB_SYNC_INITIAL_DELAY=10s
TEST_AGENT_SKILLHUB_SYNC_DELAY=10m

TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=REPLACE_ORGANIZATION_LOCAL_CLIENT_SIGNING_PUBLIC_KEY_BASE64
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json
TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true
TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996
TEST_AGENT_API_TOKEN=
TEST_AGENT_OPENCODE_MANAGER_TOKEN=REPLACE_MANAGER_TOKEN
TEST_AGENT_INTERNAL_PROXY_API_KEY=REPLACE_INTERNAL_PROXY_API_KEY
TEST_AGENT_MODEL_CATALOG_SOURCE=internal

TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_SERVER_BROADCAST_CHANNEL=test-agent:server-broadcast

TEST_AGENT_DB_POOL_INITIAL_SIZE=1
TEST_AGENT_DB_POOL_MIN_IDLE=1
TEST_AGENT_DB_POOL_MAX_ACTIVE=10
TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
TEST_AGENT_DB_POOL_TEST_ON_BORROW=true

TEST_AGENT_RATE_LIMIT_ENABLED=false
TEST_AGENT_RATE_LIMIT_CAPACITY=120
TEST_AGENT_RATE_LIMIT_WINDOW=1m
TEST_AGENT_REDIS_SUMMARY_ENABLED=false
TEST_AGENT_REDIS_SUMMARY_ROLLOUT_PERCENTAGE=0
TEST_AGENT_LEGACY_RUN_WITHOUT_CONTEXT_ENABLED=true
TEST_AGENT_MAX_PREVIEW_BYTES=5242880
TEST_AGENT_UPLOAD_CHUNK_BYTES=262144
TEST_AGENT_MAX_DIRECTORY_ENTRIES=1000

TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=5s
TEST_AGENT_BACKEND_STALE_AFTER=10s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100

TEST_AGENT_SERVER_TERMINAL_ENABLED=true
TEST_AGENT_SERVER_TERMINAL_WORKING_DIRECTORY=/data/testagent
TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=
TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true

```

后台 B `.114` 的 `/data/testagent/config/backend.env` 全文：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-114
SYS_DATA_ROOT_DIR=/data/testagent/data
TEST_AGENT_OBSERVABILITY_TRACE_ARCHIVE_ROOT=/data/testagent/data/agent-observability/traces
TEST_AGENT_OBSERVABILITY_TRACE_WARNING_FREE_BYTES=1073741824
TEST_AGENT_OBSERVABILITY_TOKEN_TTL=7d

TEST_AGENT_DB_URL=jdbc:postgresql://122.233.30.147:5432/postgres
TEST_AGENT_DB_USERNAME=postgres
TEST_AGENT_DB_PASSWORD=REPLACE_PRODUCTION_DB_PASSWORD
TEST_AGENT_DB_DRIVER_CLASS_NAME=org.postgresql.Driver

TEST_AGENT_XXL_JOB_ENABLED=true
TEST_AGENT_XXL_JOB_MYSQL_URL=jdbc:mysql://122.210.106.43:3306/xxl_job?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
TEST_AGENT_XXL_JOB_MYSQL_USERNAME=root
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=REPLACE_XXL_JOB_MYSQL_PASSWORD
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=REPLACE_XXL_JOB_ACCESS_TOKEN
TEST_AGENT_XXL_JOB_ADMIN_PORT=18080
TEST_AGENT_XXL_JOB_EXECUTOR_PORT=9999
TEST_AGENT_XXL_JOB_COOKIE_SECURE=false

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=
TEST_AGENT_REDIS_TIMEOUT=1s

TEST_AGENT_AAM_BASE_URL=http://zfw.sdc.cs.icbc
TEST_AGENT_AAM_CONNECT_TIMEOUT=3s
TEST_AGENT_AAM_REQUEST_TIMEOUT=5s
TEST_AGENT_AAM_MAX_RESPONSE_BYTES=65536

TEST_AGENT_SKILLHUB_ENABLED=true
TEST_AGENT_SKILLHUB_BASE_URL=http://ai-code.sdc.icbc/icbc/skill
TEST_AGENT_SKILLHUB_ACCESS_KEY=REPLACE_SKILLHUB_ACCESS_KEY
TEST_AGENT_SKILLHUB_CONNECT_TIMEOUT=10s
TEST_AGENT_SKILLHUB_REQUEST_TIMEOUT=30s
TEST_AGENT_SKILLHUB_SYNC_INITIAL_DELAY=10s
TEST_AGENT_SKILLHUB_SYNC_DELAY=10m

TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=REPLACE_ORGANIZATION_LOCAL_CLIENT_SIGNING_PUBLIC_KEY_BASE64
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json
TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true
TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996
TEST_AGENT_API_TOKEN=
TEST_AGENT_OPENCODE_MANAGER_TOKEN=REPLACE_MANAGER_TOKEN
TEST_AGENT_INTERNAL_PROXY_API_KEY=REPLACE_INTERNAL_PROXY_API_KEY
TEST_AGENT_MODEL_CATALOG_SOURCE=internal

TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_SERVER_BROADCAST_CHANNEL=test-agent:server-broadcast

TEST_AGENT_DB_POOL_INITIAL_SIZE=1
TEST_AGENT_DB_POOL_MIN_IDLE=1
TEST_AGENT_DB_POOL_MAX_ACTIVE=10
TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
TEST_AGENT_DB_POOL_TEST_ON_BORROW=true

TEST_AGENT_RATE_LIMIT_ENABLED=false
TEST_AGENT_RATE_LIMIT_CAPACITY=120
TEST_AGENT_RATE_LIMIT_WINDOW=1m
TEST_AGENT_REDIS_SUMMARY_ENABLED=false
TEST_AGENT_REDIS_SUMMARY_ROLLOUT_PERCENTAGE=0
TEST_AGENT_LEGACY_RUN_WITHOUT_CONTEXT_ENABLED=true
TEST_AGENT_MAX_PREVIEW_BYTES=5242880
TEST_AGENT_UPLOAD_CHUNK_BYTES=262144
TEST_AGENT_MAX_DIRECTORY_ENTRIES=1000

TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=5s
TEST_AGENT_BACKEND_STALE_AFTER=10s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100

TEST_AGENT_SERVER_TERMINAL_ENABLED=true
TEST_AGENT_SERVER_TERMINAL_WORKING_DIRECTORY=/data/testagent
TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=
TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true

```

`TEST_AGENT_MAX_PREVIEW_BYTES=5242880` 是一次性 UTF-8 读取和可编辑阈值，不会截断完整渐进预览，也不限制分片上传总大小；`TEST_AGENT_UPLOAD_CHUNK_BYTES=262144` 只控制每条上传分片的解码内存与 frame 大小。两台后端必须保持一致。个人工作区搬迁使用的 `https://test-agent.internal` 只由 Java 在精确内部 WebSocket 路径处理，不得加入上述 `TEST_AGENT_CORS_ALLOWED_ORIGINS`，也不得把企业白名单改成 `*`。

两台机器的 Trace 目录都是各自现有数据卷中的本地持久化目录，不是共享目录。首次成功归档会冻结 owner 节点，后续正文查看和下载由公共 Java 路由转发到该节点。正文和目录不设 TTL；必须分别监控 `testagent.trace.archive.disk.usable.bytes`、`write.failures`、`backlog` 和 `last.success.epoch.seconds`，不得通过删除未确认数据消除告警。

保存后，两台都执行以下检查；命令必须无输出：

```bash
grep -n 'REPLACE_' /data/testagent/config/backend.env
```

集群内 manager token 和内部代理 key 使用同一组值，降低滚动部署误配风险；本机 `backend.env` 的 manager token 必须与本机 `docker.env` 完全一致。

## 5. 每个后台的 docker.env

`.4` 和 `.114` 都使用下面这份完整 `/data/testagent/config/docker.env`；只替换 `REPLACE_MANAGER_TOKEN`，并确保与本机 `backend.env` 完全相同：

```dotenv
TEST_AGENT_BASE_DIR=/data/testagent

TEST_AGENT_OPENCODE_MANAGER_TOKEN=REPLACE_MANAGER_TOKEN
TEST_AGENT_DATA_ROOT=/data/testagent/data
TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs
TEST_AGENT_PYTHON_LIBS_ROOT=/data/testagent/python-libs
TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:internal
# 文件存在时脚本也会自动发现该固定路径；显式配置可让缺文件直接失败。
TEST_AGENT_OPENCODE_MODELS_FILE=/data/testagent/config/opencode-models.json

VITE_TEST_AGENT_API_BASE_URL=

OPENCODE_WORKER_BACKEND_PORT=8080
OPENCODE_WORKER_PORT_START=14096
OPENCODE_WORKER_PORT_END=15095

OPENCODE_ALLOWED_CORS=http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996
OPENCODE_MANAGER_HEARTBEAT_INTERVAL=5s
OPENCODE_MANAGER_RECONNECT_INTERVAL=10s

OPENCODE_VERSION=1.18.4
OPENCODE_RELEASE_COMMIT=49c69c5ed3ccf706b61b3febb43c8aaff7f8325e
OPENCODE_ASSET_NAME=opencode-linux-x64-baseline.tar.gz
OPENCODE_ASSET_SIZE=59265643
OPENCODE_ASSET_SHA256=4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc
OPENCODE_BINARY_SHA256=6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5
OPENCODE_RELEASE_BASE_URL=https://github.com/anomalyco/opencode/releases/download
GO_IMAGE=golang@sha256:e87b2a5f6df2dff71ea330d55d54f4979eb380ae58a7e3aabc9d53121243e689
NODE_IMAGE=node@sha256:b042c6d46a90773b82ea3f95b05457ea93ee127a73b1b47ad5ebbb1a08ec3df8

NPM_REGISTRY=https://registry.npmmirror.com
COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
GOPROXY=https://goproxy.cn,direct
DEBIAN_MIRROR=https://mirrors.ustc.edu.cn/debian
DEBIAN_SECURITY_MIRROR=https://mirrors.ustc.edu.cn/debian-security

TEST_AGENT_IMAGE_OUTPUT_DIR=/data/testagent/dist
```

上述 Debian 地址只用于有公网封包机的构建阶段，不会进入企业断网运行链路。若封包机到 USTC 出现 TLS 握手失败，必须显式改用
`DEBIAN_MIRROR=https://deb.debian.org/debian` 和
`DEBIAN_SECURITY_MIRROR=https://security.debian.org/debian-security` 后重新执行完整封包；不得在 Dockerfile 中临时跳过证书校验、
删除依赖或把构建期下载推迟到企业节点。两台后台必须使用同一份已经通过断网校验的 worker image tar。

当前 worker 不读取旧的 `TEST_AGENT_BACKEND`，而是读取本机 Java 写出的 `.serverhost` 并结合 `OPENCODE_WORKER_BACKEND_PORT` 建立 manager WebSocket，所以不要恢复旧变量。Nginx 使用前端服务器独立的 `nginx.env`，不在每台 worker 的 `docker.env` 中维护 upstream。

两台 worker 都重建并连接后，超级管理员必须在“系统管理 → 配置管理 → 通用参数管理”把全局 `OPENCODE_MANAGER_MAX_PROCESSES` 从 `20` 改为 `30`。保存后 Java 会向两台在线 manager 热推；manager 按本机 1000 个端口池裁剪并立即回报心跳。运行管理页两台 manager 的 `portStart/portEnd/maxProcesses` 应分别显示 `14096/15095/30`。若参数仍为 `20`，每台仍只能新建 20 个进程；端口池映射数量不会自动改变实际进程上限。

每台 Java 启动后，本机身份文件分别应为：

```text
# .4
/data/testagent/data/.serverid   = test-agent-backend-122-233-30-4
/data/testagent/data/.serverhost = 122.233.30.4

# .114
/data/testagent/data/.serverid   = test-agent-backend-122-233-30-114
/data/testagent/data/.serverhost = 122.233.30.114
```

## 6. 多后台 Nginx

在前端 `.2` 创建 `/data/testagent/config/nginx.env`：

```dotenv
TEST_AGENT_NGINX_MODE=multi
TEST_AGENT_NGINX_BACKENDS=122.233.30.4:8080,122.233.30.114:8080
TEST_AGENT_NGINX_SERVER_ROUTES=test-agent-backend-122-233-30-4=122.233.30.4:8080,test-agent-backend-122-233-30-114=122.233.30.114:8080
TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS=9996
TEST_AGENT_NGINX_TLS_ENABLED=false
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_NGINX_CONF_PATH=/data/apps/nginx/conf/test-agent.conf
TEST_AGENT_NGINX_BIN=/data/apps/nginx/sbin/nginx
TEST_AGENT_NGINX_PREFIX=/data/apps/nginx
TEST_AGENT_NGINX_MAIN_CONF=/data/apps/nginx/conf/nginx.conf
TEST_AGENT_NGINX_RELOAD_MODE=binary
```

`TEST_AGENT_NGINX_SERVER_ROUTES` 是 `linuxServerId -> Java endpoint` 的一对一静态白名单。每个 ID 和 endpoint 都只能出现一次，而且 endpoint 必须已列在 `TEST_AGENT_NGINX_BACKENDS`；脚本会拒绝非法值，绝不会把客户端头直接拼成地址。扩容或变更 binding 对应服务器时，必须同步更新这张表。

`TEST_AGENT_NGINX_CONF_PATH` 必须是当前 Nginx 主配置实际 include 的 `.conf` 文件。前端部署脚本统一调用 [configure-nginx.sh](configure-nginx.sh)，由同一个 [gateway.conf.template](nginx/gateway.conf.template) 生成平台与 XXL 两组 `least_conn` upstream、逐节点故障参数、WebSocket Upgrade、SSE 禁缓冲和 `/xxl-job-admin/` 同源代理；配置失败自动恢复旧文件。

两台 `backend.env` 必须使用相同 MySQL URL/账号密码/access token。Java 自动用本机 loopback Admin 做启动门控，并从 `TEST_AGENT_SERVER_ADVERTISED_HOST`（缺失时自动探测内网 IPv4）派生 executor 注册地址（`.4:9999`、`.114:9999`）。Admin/MySQL 故障不影响平台 readiness，但必须在部署验收中单独检查 `xxlJobAdmin` health 和六条初始化任务。

`TEST_AGENT_NGINX_XXL_JOB_ADMINS` 只维护中央 Nginx upstream，不会进入 Java。新增第三台 Linux 时，启动新 Java/worker，把它同时加入 `TEST_AGENT_NGINX_BACKENDS` 和 `TEST_AGENT_NGINX_XXL_JOB_ADMINS`，再无停机 reload Nginx；已有两台 Java 不修改配置、不重启。

`TEST_AGENT_NGINX_CONF_PATH` 使用已经由主配置显式 include 的 `/data/apps/nginx/conf/test-agent.conf`，不要另建一个未加载的同级文件。前端部署脚本统一调用 [configure-nginx.sh](configure-nginx.sh)，由同一个 [gateway.conf.template](nginx/gateway.conf.template) 生成：

- 缺失或未知 ID 使用原 `least_conn` upstream；已知 ID 使用对应服务器的专用 upstream。
- 专用 upstream 以对应 Java 为 primary，其余 Java 为 backup；只在无法连接或连接超时时尝试 backup。
- 普通 HTTP、SSE 和服务器终端 WebSocket 共用一张静态路由表。
- 发往 Java 前删除 `X-Test-Agent-Linux-Server-Id` 和外部伪造的 `X-Test-Agent-Backend-Routed`。
- `proxy_next_upstream` 只包含 `error timeout`，不启用 `non_idempotent`，避免已发送的 Session/Run POST 被重复执行。
- 继续生成 `listen 80`、`listen 9996`、WebSocket Upgrade、SSE 禁缓冲和长连接超时。
- 站内跳转返回相对 `Location`，企业入口映射到本机其它端口时保留浏览器原始 `host:port`。

配置失败时脚本会恢复旧文件，不再手工维护另一份多节点 Nginx 配置。普通共享控制面 API 不携带路由头，继续使用 `least_conn`；只有用户 OpenCode、会话、Run、SSE 和本地工作区请求采用精确首跳。一次性 WebSocket ticket 仍是 JVM 内存状态，但路由已经闭合：

- PTY ticket 请求按用户进程归属转发，响应返回实际签发 Java 的绝对 WebSocket 地址。
- Workspace/Agent 配置文件 route 返回目标 Java `baseUrl`，浏览器在目标 Java 申请 ticket 并连接同一 Java。
- Agent 配置进度 ticket 返回当前签发 Java 的绝对 WebSocket 地址；跨节点进度由既有服务器广播汇入。

因此不需要 `ip_hash`、cookie sticky、共享 ticket 或 Java 间文件/WebSocket 代理。当前现场明确不启用 HTTPS，两台 Java 都以空的公开 WSS 基址和 `ALLOW_INSECURE=true` 返回本节点 `ws://.4:8080` 或 `ws://.114:8080`；浏览器网段必须能访问两个 Java `:8080`，两台 Java 和 worker 的 Origin 白名单也都必须同时包含域名和 IP 两个 `:9996` origin。HTTP/WS 会明文传输登录信息和终端内容，只能在已接受风险的可信内网使用。

### 6.1 已有 `nginx.env` 升级

旧变量 `TEST_AGENT_NGINX_TERMINAL_ROUTES` 只作为一次升级兼容入口；新包必须改成统一变量，不能同时保留两个键。右侧映射值不变：

```bash
cp -a /data/testagent/config/nginx.env \
  /data/testagent/config/nginx.env.bak.$(date +%Y%m%d%H%M%S)
sed -i 's/^TEST_AGENT_NGINX_TERMINAL_ROUTES=/TEST_AGENT_NGINX_SERVER_ROUTES=/' \
  /data/testagent/config/nginx.env

bash /data/testagent/deploy/internal/configure-nginx.sh \
  --env-file /data/testagent/config/nginx.env \
  --validate-only
```

预期输出 `backend count: 2`、`server route count: 2`。正式安装必须使用本次发布包中的前端部署入口；它会更新前端和部署脚本、渲染候选配置、执行实体 Nginx `-t/-T` 并 reload，失败自动回滚：

若 Mac 重新封装时复用旧节点包，`package-two-backend-complete.sh` 只会在外层包的临时副本中处理固定站点配置：前端路由键迁移为 `TEST_AGENT_NGINX_SERVER_ROUTES`，写入 `.4/.114` 两组工具 upstream；两个后台写入本轮组织客户端签名公钥、版本 catalog 下载地址/路径和可信 Nginx 地址，补齐 HTTP Cookie、大文件预览/分片参数、固定 worker 端口池、SkillHub 测试环境地址和启用开关，以及已部署 ClickHouse `122.233.30.147:8123`、Mem0 VIP `122.233.30.160:18888` 的地址和启用开关，并分别生成绑定本机 IP 的 `toolbox.env`。客户端私钥始终只留在 Mac，不进入节点包。CK/Mem0/BGE/pgvector 镜像不进入平台增量包，也不会重启这些数据面容器。源敏感节点包和其中的密码/token 不会被修改或输出；旧节点包缺少 SkillHub Access Key、ClickHouse 密码、Mem0 service key 或模型网关 HMAC 时，包内只写目标机继承标记，`deploy-backend-node.sh` 在覆盖配置前从本机已安装 `backend.env` 原样继承，缺失、重复或长度不合格会先失败。同一键重复定义时封装直接失败，不能继续交付。

```bash
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip
```

## 7. 部署与启动顺序

### 7.1 使用逐机配置包

后续 U 盘只交付平台包 `test-agent-two-backend-complete.zip` 及 SHA，发到 `.4/.114/.2`。顶层目录
固定为 `test-agent-two-backend-complete/`，不添加日期、`v2`、`v3`。外部 MySQL 不部署容器、镜像或
`.147` 节点包。

各服务器上传并校验本机对应的外层 ZIP 后，用无参数入口执行。入口脚本从本机网卡识别 IP，自动选择并
校验节点包、解压节点配置，然后连续完成 `--validate-only`、正式部署和 `--verify-only`。任一步失败都会
返回非零；Java、Docker、Nginx 和最终校验输出统一写入 `/data/0709/deploy-<本机IP>.log`，不会再出现
只执行了一个空 `bash`、但实际服务没有重启的情况。

部署前先从 `.4`、`.114` 确认 `122.210.106.43:3306` 可达。两台敏感 `backend.env` 固定使用同一个
外部 JDBC 地址、`root` 账号和现场密码；URL 启用 `createDatabaseIfNotExist=true`，因此账号有建库权限时
会自动创建 `xxl_job` 空库，随后 Admin 子上下文 Flyway 幂等创建表、执行器组和任务。密码不得打印或
另行写入命令行。

本轮 CK/Mem0/BGE/pgvector 已提前部署，平台升级前还必须在 `.4`、`.114` 分别确认现有
`/data/testagent/config/backend.env` 已各包含一条 ClickHouse 密码、Mem0 service key 和模型网关 HMAC；只检查键数量和空值，禁止回显值。增量入口会继承这三项敏感值，再写入固定地址和开关：

```bash
grep -c '^TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD=' /data/testagent/config/backend.env
grep -c '^TEST_AGENT_MEMORY_SERVICE_API_KEY=' /data/testagent/config/backend.env
grep -c '^TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET=' /data/testagent/config/backend.env
grep -E '^TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD=$|^TEST_AGENT_MEMORY_SERVICE_API_KEY=$|^TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET=$' /data/testagent/config/backend.env
nc -vz 122.233.30.147 8123
nc -vz 122.233.30.160 18888
```

前三条必须分别输出 `1`，第四条无输出，两个端口都连通；否则停止部署并先按
`deploy/internal/CLICKHOUSE-ANALYTICS.md`、`docs/deployment/qa-memory.md` 对齐已部署数据面的真实密钥，不能把占位值或本地开发密钥写进企业配置。

两台后台更新后分别执行以下脱敏校验：

```bash
# 122.233.30.4
grep -c '^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=' /data/testagent/config/backend.env
grep -E '^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=.*REPLACE_|^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=$' /data/testagent/config/backend.env

# 122.233.30.114
grep -c '^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=' /data/testagent/config/backend.env
grep -E '^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=.*REPLACE_|^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=$' /data/testagent/config/backend.env
```

两台第一条都应输出 `1`，第二条均无输出；不要使用 `grep` 直接回显密码。

企业现网上一轮已完成 2026-08-24 平台、XXL、ClickHouse 迁移以及
`V20260825091459` 客户端实例替换迁移。部署前仍必须分别由数据库管理员导出
平台 PostgreSQL 与 XXL MySQL 的完整历史，不能只留最近 20 条：

```sql
select installed_rank, version, description, type, script, checksum, installed_on, success
from flyway_schema_history
order by installed_rank;
```

上一轮已成功部署时，PostgreSQL 正常现网路径必须满足所有记录 `success=true`，且至少已成功执行并保持 checksum 的
主链版本包括 `20260818094330`、`20260819125704`、`20260820153926`、`20260820182024`、
`20260820202529`、`20260821113000`、`20260822075000`、`20260822103625`、`20260822201811`、
`20260823104611`、`20260823123757`、`20260823191023`、`20260824100444`、`20260825091459`、
`20260827183737`。已部署的
`V20260825091459__local_client_instance_replacements_create.sql` 的 Flyway checksum 固定为 `749555545` 并必须保持，
源码 SHA-256 固定为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`；已部署的
`V20260827183737__agent_skill_hub_assets_add_external_created_at.sql` 的 Flyway checksum 固定为 `-976579670`，
源码 SHA-256 固定为 `d032d0a50c59a719f056654880424f5525a843ea96512ac7d95c7d4c36027362`。第一台 `.4` 新 Java 从这套已部署
history 启动时，本轮 PostgreSQL 不应新增 history。
若现场缺少上述上一轮版本，必须先逐条核对真实部署记录，不能直接套用本节增量结论。出现失败记录、未知更高版本或
未知 checksum 都必须停止发布。正常企业历史不得出现只用于已登记并行
开发历史的 `20260809120000`、`20260809210000`、`20260809230000`、`20260810090000`、
`20260810110000` 至 `20260810110002`、`20260810173117` 或 `20260812202425`；其中 `20260809210000`
仅用于已执行旧体验候选的个人库，`20260812202425` 仅用于已经由 dev 执行本地客户端迁移的共享开发库。
发现这些版本或未知 checksum 时必须核对原始历史和兼容路径，禁止用 `repair`、`outOfOrder` 或手工改表规避。

虽然 LobeHub 服务和页面入口继续关闭，既有兼容 migration 创建的平台模型目录/聚合表和四个默认禁用参数仍必须
保留，这是数据库兼容要求，不代表启用服务。

XXL MySQL 使用独立的 `flyway_schema_history`。上一轮部署完成后的准入历史应为已登记的 V12 分支以及 V13、V14
全部成功且 checksum 不变。V10 的 Flyway checksum 为 `1539433813`、文件 SHA-256 为
`665b22835a9871828fcaceca2941d1ca83de248698fde76f3380b12bec49fb47`，V11 的 Flyway checksum 为
`-1863356225`、文件 SHA-256 为 `03e7054a56daac14bd1cb62fd2302c7752c5d93ba88f255ad8d10f7320736236`；
并行历史中存在两条已执行过且字节不可改写的 V12：ClickHouse 运营入库的 SHA-256 为
`70878c4544d5d8c030b1edf59406a320ceec68f86bd763d366a80d5d4ed005f0`，SCM Git 姓名补偿的 SHA-256 为
`2ef19bbbffb56131981f4f99f7d58d5b1d9f25715b0e76dc0cfd44b80b196739`。打包必须同时锁定两份历史资源，运行时按数据库已执行
V12 选择兼容 location 并由更高版本前向 migration 补齐另一任务。失败记录、未知 checksum、未知更高版本或未登记的 V12
分叉都必须停止发布。V13 文件 SHA-256 为
`d7627696bcabc9f170f7709e298b46e28ba306a38f2251572c99b6b8175ff96a`，V14 文件 SHA-256 为
`551d90547b21440b614a40502c852b303b22a41d93ab4060acc962b7ae411718`。已部署的
`V20260824100401__xxl_job_info_update_inactive_cleanup_description.sql`，源码 SHA-256 固定为
`4eda1bf4168f097f83357d097714cc66d83156f7a2e88d3dd60adc56c218be3a`；它只把平台初始化的闲置清理任务说明从十五天改为十天，
且属于上一轮已部署基线，本轮 XXL MySQL 不应新增 history。

`V20260728160800__create_toolbox_click_tracking.sql` 的现网 checksum 仍必须为 `-1966404877`；只有已登记的
早期测试/过渡历史才允许旧 `V20260727203500` 或 `-74327385` 幂等变体；现网历史中的
`V20260728210000__index_in_flight_app_source_operations.sql` 也必须保留且为 `success=true`。任一失败记录、未知 checksum、
未知更高版本、缺少上述已部署基线版本或其它历史分叉都必须停止发布；不得启用 Flyway `outOfOrder`、执行
`repair` 或手工修改历史表。必须先只部署 `.4`，确认 readiness 正常，并按部署前基线确认 PostgreSQL、XXL MySQL 和
ClickHouse `analytics_schema_history` 三边都不新增记录；同时确认 `agent_skill_hub_assets.external_created_at` 仍为可空
`timestamp without time zone`；
随后确认搬迁任务仍为每 30 分钟、闲置进程关闭任务为每日 02:00、内部模型探活为每 5 分钟、可观测数据清理为每日 03:30、
ClickHouse 入库为每分钟、SCM Git 姓名补偿为每日 04:10、工作空间 Git 权限巡检为每两小时，再部署 `.114`。共享数据库上 `.114` 启动只允许
validate，不应再新增 history。`.4` 日志出现
`FlywayValidateException`、`ClassNotFoundException: org.postgresql.Driver` 或 `Application run failed` 时不得继续滚动。

外部 MySQL 端口验证通过后，在 `.4` 执行：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-backend-node.sh
```

`.4` 是固定 ClickHouse 历史回填节点。这个无参数入口在常规平台升级和健康检查通过后，先检查
`/data/testagent/config/analytics-clickhouse-backfill.state`；已登记 `analytics-v1=VERIFIED` 时直接跳过 Runner
和额外 Java 重启。旧包已经成功但没有状态文件时，只从 `/data/testagent/logs/backend.log` 接受 Java 的
`verified=true` 完成行并自动补登；只有两者都不存在时才临时开启 Java 回填 Runner，等待源/目标计数校验，
并同时从 journal cursor 与本次滚动日志字节偏移识别 `ClickHouse 运营回填完成`。成功后以 `0600` 原子写状态并
把落盘开关恢复为 `false`；失败时先恢复普通服务，再让部署返回非零，因此不得继续 `.114` 或 `.2`。
这一步不要求服务器安装 `psql`、`jq` 或 `rg`，也不会在命令行输出 ClickHouse/PG 密码。

`deploy-backend-node.sh` 会读取内层组件清单：工具箱为 `included` 时自动提取镜像 tar、安装本机 `toolbox.env`、部署并诊断；为 `reuse` 时不提取、不加载镜像，先核对目标机安装指纹，再在平台升级前后诊断现有工具容器，指纹不一致或任一容器不健康都会停止。无需再手工执行工具箱部署命令。

worker runtime 为 `included` 时会同步替换 programs、OpenCode Manager 和 worker 镜像；为 `reuse` 时不携带这些大制品，部署前必须确认目标机安装指纹一致、现有 Manager/OpenCode/Codex 文件齐全且 worker 容器健康，否则立即停止。

后台入口还会调用 `verify-opencode-tool-runtime.sh`：`included` 包先核对 `test-agent-programs.tar.gz`，解压后再核对 `/data/testagent/programs/opencode`；`reuse` 包直接核对现有安装目录。校验覆盖 runtime `package.json`、lockfile、全部固定直接依赖的包元数据和入口文件，尤其要求 `@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 同名同版本存在。缺包、空文件、未锁定或版本不符都会在替换 Java、加载镜像或重启 worker 前停止，禁止在企业内临时执行 `npm install` 补齐。

升级前应先停止 `.4`、`.114` 的旧 Java。`.4` 是固定首节点，入口会完整验证本机 Java、XXL Admin、
worker、RSA 和身份文件，但把 peer 探测延后；随后 `.114` 会反查 `.4`，最后 `.2` 会同时检查两个
Java 和两个 XXL Admin，因此不会再因首节点等待尚未启动的 peer 而产生 `verify_exit=1`。

再在 `.114` 执行：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-backend-node.sh
```

`.114` 使用同一个自动处理逻辑：`included` 自动部署本机工具容器和 worker runtime，`reuse` 只复用并验证已有组件；不需要额外手工解压或执行工具箱脚本。

两台后台全部通过后，最后在 `.2` 执行：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-frontend-node.sh
```

前端入口会在 reload 后同时验证两台 Java、两台 XXL Admin、`.4/.114` 上四个工具端口，
并通过 `127.0.0.1` 请求 IT-Tools 与 OmniTools 的统一入口深链。只要 `.2` 到任一工具端口被防火墙阻断、
容器未运行或 Nginx 仍使用旧配置，脚本就会失败并指出具体 upstream，不再等到浏览器点击后才暴露 502。

客户端清单为 `reuse` 时，`.2` 不接收 `dist/local-opencode-client/`，而是在任何前端目录切换和 Nginx
reload 前校验现有 `/data/testagent/dist/local-opencode-client/`。版本、清单、签名、安装脚本、用户包、
JRE 或 OpenCode 归档任一 SHA-256 不一致都会停止部署；该校验只使用 `bash`、`awk` 和 `sha256sum`。

正式部署必须由 `root` 执行。平台外层包内已有完整平台发布 ZIP，三台应用服务器不再另外复制内层 ZIP
或逐机包；`.147` 不再参与本次部署。
后台节点包包含真实数据库密码和 token，权限与传输方式按敏感交付物处理；RSA 只使用发布 JAR 内的
`BOOT-INF/classes/rsa-private.key`，不会生成 RSA env 或外置私钥路径。

当前 manager 配置下发成功日志为 `event=manager_config_update status=applied`。逐机验证脚本同时兼容
旧版 `manager config update applied`；不能只按旧文本判断当前结构化日志失败。

### 7.2 后续增加一台全新后台

把平台外层 ZIP 和 SHA 上传到新后台并解压。初始化脚本从新服务器网卡自动取
`122.233.30.x`，以包内 `.4` 的真实配置为基线，只替换本机 `TEST_AGENT_SERVER_ADVERTISED_HOST` 和
`TEST_AGENT_LINUX_SERVER_ID`；`docker.env` 沿用集群相同 manager token，整个过程不打印密码或 token，
生成的节点配置包仍强制不超过 `1 MiB`：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash init-backend-node-config.sh
bash deploy-backend-node.sh
```

这一步自动初始化新后台的两个文件：

```text
backend.env：复用数据库、Redis、CORS、manager token、内部代理 key，只改本机 IP 和稳定 server ID
docker.env：复用数据目录、programs、worker 镜像、端口池、独立 UI 平台地址及与 backend.env 相同的 manager token
```

新后台 readiness 通过后，在前端 `.2` 的已解压外层目录登记一次新后台 IP，再重新运行前端入口。
远端后台 IP 不能从前端本机网卡推断，所以这是扩容中唯一需要手工填写的 IP；脚本会先访问新后台
readiness，再把 backend 和统一 server route 同步追加到打包的 `nginx.env`，重复登记不会产生重复项：

```bash
cd /data/0709/test-agent-two-backend-complete
bash register-backend-on-frontend.sh 122.233.30.新后台末段
bash deploy-frontend-node.sh
```

如果以后再加后台，每台新后台重复上面的初始化和后台部署；最后在 `.2` 对每个新 IP 登记一次，再只
执行一次 `deploy-frontend-node.sh`。

### 7.3 使用完整发布包中的标准脚本

先部署后台 A `.4`：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-release.sh \
  > /tmp/deploy-internal-release.sh
bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.4 \
  --skip-frontend \
  --validate-only
bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.4 \
  --skip-frontend \
  --run-analytics-backfill

/data/testagent/deploy/internal/deploy-python-libs.sh \
  --archive /data/0709/test-agent-python-libs-py313-linux-amd64.tar.gz \
  --checksum /data/0709/test-agent-python-libs-py313-linux-amd64.tar.gz.sha256 \
  --env-file /data/testagent/config/docker.env
```

再部署后台 B `.114`：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-release.sh \
  > /tmp/deploy-internal-release.sh
bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.114 \
  --skip-frontend \
  --validate-only
bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.114 \
  --skip-frontend

/data/testagent/deploy/internal/deploy-python-libs.sh \
  --archive /data/0709/test-agent-python-libs-py313-linux-amd64.tar.gz \
  --checksum /data/0709/test-agent-python-libs-py313-linux-amd64.tar.gz.sha256 \
  --env-file /data/testagent/config/docker.env
```

两台后台都通过 health/readiness、身份文件和 worker 检查后，最后在前端 `.2` 部署一次，避免 Nginx 提前把流量分到尚未就绪的 `.4`：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-frontend.sh \
  > /tmp/deploy-internal-frontend.sh
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip
```

首次部署时先让两台 Java 都通过 health/readiness 并写对身份文件，再分别启动本机 worker。已有环境升级用户绑定端口复用版本时，先逐台更新并重启 worker/manager，再滚动更新 Java，全部后端就绪后最后部署一次前端；不要在同一用户初始化期间跨节点切换版本。每台已有节点按：

```text
替换 worker 镜像/programs -> 重启 manager 并确认 stopOwned capability
-> 替换 JAR/lib -> 重启 Java -> 检查 health/readiness 与 .serverid/.serverhost
```

混合版本遇到未知命令或启动错误时必须保留原 binding，只报错、不迁移端口。不得清理 `/data/testagent/data`，也不要把 A 的数据目录复制覆盖到 B；存量无主进程由超级管理员在运行管理核对 UCID/PID/端口后手工处理。

每台后台部署脚本都会校验已有 systemd unit 的 JAR/env 指向；`systemctl stop` 后若 `8080` 仍被同一路径的旧 `test-agent-app.jar` 占用，会安全终止该遗留进程，其他程序占用则拒绝误杀。新 Java health/readiness 通过后还会核对 systemd `MainPID` 正是 `8080` 监听者，避免滚动升级时误连旧手工进程。

## 8. 公共配置和模型

`opencode-models.json` 与公共 Agent 配置是两层不同输入。发布 ZIP 已固定携带 [opencode-models.json](opencode-models.json)，它使用 models.dev `api.json` 兼容结构，作为模型元数据快照放到后台，不能放到公共 Git 的 `opencode/` 目录，也不能只放 `.2`。OpenCode 1.18.4 的 `/api/model` 按其中 `release_date` 倒序返回；本次 `.4` 灰度使用的快照把 Qwen 排序日期设为 `2026-08-07`，高于 DeepSeek 的 `2026-08-06`，JSON 键顺序本身不参与排序。

只有本轮发布范围明确包含模型清单变更时，才在 `.4` 和 `.114` 分别执行下面的替换；若执行已批准的 `.4` 单节点灰度，只在 `.4` 执行替换与 worker 重启，`.114` 仅记录现网 SHA 并保持文件和 worker 不变。本轮 Playwright 投影修复包不包含模型变更，`.4/.114` 只在部署前后执行
`sha256sum /data/testagent/config/opencode-models.json` 并确认各自摘要没有变化，禁止执行下面的 `install` 和额外 worker 重启：

```bash
# 在 .4 和 .114 分别执行；使用本次已安装的同一发布 ZIP 内容。
cd /data/testagent/deploy/internal
TEST_AGENT_OPENCODE_MODELS_FILE=/data/testagent/deploy/internal/opencode-models.json \
  ./opencode-worker-docker.sh \
  --env-file /data/testagent/config/docker.env validate-models
install -m 0644 /data/testagent/deploy/internal/opencode-models.json \
  /data/testagent/config/opencode-models.json
TEST_AGENT_OPENCODE_MODELS_FILE=/data/testagent/config/opencode-models.json \
  ./opencode-worker-docker.sh \
  --env-file /data/testagent/config/docker.env validate-models
sha256sum /data/testagent/config/opencode-models.json

./opencode-worker-docker.sh --env-file /data/testagent/config/docker.env restart
docker logs --tail 100 test-agent-opencode-worker | \
  grep 'event=opencode_models_catalog_validated'
docker inspect test-agent-opencode-worker --format '{{range .Config.Env}}{{println .}}{{end}}' | \
  grep '^OPENCODE_MODELS_PATH=/etc/test-agent/opencode-models.json$'
```

明确发布同一模型清单时，`.4` 与 `.114` 的 `sha256sum` 必须完全一致；`.4` 单节点灰度或本轮明确保持既有灰度时，必须保留两个已登记 SHA，且不得在 `.114` 执行复制或额外 worker 重启。`validate-models` 在宿主机没有 `jq` 时会使用已经导入的 worker 镜像执行同一结构校验；它只读文件，不删除或重启现有容器。worker 对文件执行只读 bind mount；宿主脚本会在删除当前容器前校验 Provider/Model ID、能力布尔值、`release_date`、正数 `limit` 和可选模态，镜像入口在 manager 启动前再次执行同一校验；Mac 封包另由 `verify-opencode-model-priority.sh` 拒绝本次快照优先级倒置，但该文件不计入 worker 指纹。这样 `.4` 新快照能被发布门禁锁定，同时 `.114` 保留的旧快照不会被新部署脚本阻断后续正常 worker 重启。manager 子进程继承该环境，所以重启目标 worker 后新恢复的全部用户 OpenCode 进程统一读取；仅重启 Java、刷新页面或调用公共配置热加载均不足以替换这份全局快照。文件只允许模型元数据，不得写 provider token、UCID、Authorization 或平台内部代理 key。

超级管理员进入“系统管理 → 配置管理 → opencode 公共配置管理”，分别初始化：

```text
test-agent-backend-122-233-30-4
test-agent-backend-122-233-30-114
```

两个服务器使用同一版本的 [opencode.jsonc.example](opencode.jsonc.example)：默认模型和小模型均为 `enterprise-deepseek/DeepSeek-V4-Flash-W8A8`，DeepSeek 上下文为 `262144`，Qwen 上下文为 `200000`；完整样例已包含 `code_analysis` MCP，MCP 上下文同样为 `262144`。

在任意一台已收到交付包的后台导出完整 JSONC，分别粘贴到两个 `linuxServerId` 的公共配置编辑器；两个节点内容必须一致：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/opencode.jsonc.example \
  >/tmp/opencode.jsonc
sed -n '1,220p' /tmp/opencode.jsonc
```

JSONC 中三个 `{env:...}` 引用必须原样保留，由各用户所属 Java 动态注入，不要替换成固定 UCID 或固定代理地址。

```text
enterprise-qwen/Qwen3.6-27B                 -> qwen-prod
enterprise-deepseek/DeepSeek-V4-Flash-W8A8 -> deepseek-prod
```

共享数据库的“内部模型供应商”页面完整填写如下；只需把两个 token 替换为现场已有值：

| Provider ID | 名称 | Base URL | Token | 启用 | 排序 |
|---|---|---|---|---|---:|
| `qwen-prod` | `企业通义` | `http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1` | `REPLACE_QWEN_UPSTREAM_TOKEN` | 是 | `1` |
| `deepseek-prod` | `企业 DeepSeek` | `http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1` | `REPLACE_DEEPSEEK_UPSTREAM_TOKEN` | 是 | `2` |

公共配置必须包含 `includeUsage=false`，避免 OpenCode 1.18.4 默认添加企业内部接口不支持的 `stream_options.include_usage`。供应商地址、启用状态和上游 token 来自共享数据库：`qwen-prod`、`deepseek-prod` 均启用，`baseUrl` 为 `http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1`。公共配置工作树位于各后台本机，因此数据库已经配置供应商并不等于另一台服务器已经初始化公共配置。

`enterprise-qwen` / `enterprise-deepseek` 是 OpenCode provider key；`qwen-prod` / `deepseek-prod` 是数据库和 `X-Enterprise-Model-Provider` 使用的 Java 路由键，不能混用。企业上游的 `Authorization: Bearer <供应商关联 Token>` 虽可鉴权，但 `ucid` 不生效；只有 `Auth-Token: <供应商关联 Token>` 会让同一请求的 `ucid` 生效。所以上游 Token 只在共享数据库维护，并由 Java 固定以 `Auth-Token: <token>` 注入。OpenCode 到 Java 内部代理仍使用独立的 Bearer 代理 Key，该 Key 只校验 Java 代理调用方，不会转发为上游供应商鉴权。用户 UCID 由拥有该用户进程的 Java 从用户表读取并逐进程注入，不使用全局 UCID env 文件。

变更生效规则：

1. 修改某台服务器公共 `opencode.jsonc` 后，只重启该服务器上已有的用户 OpenCode 进程；新进程直接读取。
2. 修改共享数据库供应商或 token 后点击“刷新 Java 内存”；广播启用时所有 Java 会分别重载同一数据库快照。
3. 在两台后台分别查询 refresh-status，必须都包含 `qwen-prod`、`deepseek-prod` 且 `tokenConfigured=true`；广播失败时逐台重启 Java 重新加载。
4. 只重启 Java 不会让已经运行的用户 OpenCode 重新读取公共配置或重新注入 UCID，涉及进程配置时仍要重启对应用户进程。

公共 `tools/*.ts` 随各服务器公共配置仓库更新，项目专用 Tool 位于工作区 `.opencode/tools/*.ts`。企业 programs 已离线内置 `@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 及传递依赖；仅更新 Tool 文件时，在每台目标服务器同步公共仓库后重启该节点相关用户 OpenCode 进程。Tool 新增基线外第三方 import 时，必须重新打完整企业包，并在每台后台同时更新 programs、worker 镜像和 worker，不能只向一台服务器复制 `node_modules`。

## 9. 集群验收

两台后台分别执行：

```bash
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost

cd /data/testagent/deploy/internal
./opencode-worker-docker.sh --env-file /data/testagent/config/docker.env status
docker logs --tail 200 test-agent-opencode-worker | \
  egrep 'config update applied|websocket|serverhost|serverid|OPENCODE_UNAVAILABLE'
docker inspect --format 'PidsLimit={{.HostConfig.PidsLimit}} Ulimits={{json .HostConfig.Ulimits}}' \
  test-agent-opencode-worker
docker exec test-agent-opencode-worker \
  sh -lc "grep -E 'Max processes|Max open files' /proc/1/limits"
```

两台后台都必须显示 `PidsLimit=8192`，`Ulimits` 包含 `nofile` soft/hard `262144` 和 `nproc` soft/hard `8192`，容器 `/proc/1/limits` 显示最大打开文件数 `262144`、最大用户进程数 `8192`；`docker port` 还必须同时列出 `14096/tcp` 和 `15095/tcp`。脚本升级只对重建后的容器生效，因此已有环境按 `.4`、`.114` 顺序逐台执行 worker `restart` 和上述检查；当前节点任一检查失败时立即停止，不操作下一节点。

然后验收：

1. 在 `.2` 执行下面的 Nginx 检查。预期配置只由已加载的 `test-agent.conf` 承载，同时出现两个监听端口、默认 `least_conn`、两组服务器专用 upstream、静态 `map`、primary/backup，以及两种入站路由头清理；不得出现 `non_idempotent`：

   ```bash
   /data/apps/nginx/sbin/nginx -p /data/apps/nginx/ \
     -c /data/apps/nginx/conf/nginx.conf -t
   /data/apps/nginx/sbin/nginx -p /data/apps/nginx/ \
     -c /data/apps/nginx/conf/nginx.conf -T \
     > /tmp/test-agent-nginx-effective.conf 2>&1
    grep -E 'configuration file /data/apps/nginx/conf/test-agent.conf|listen (80|9996);|least_conn|map \$http_x_test_agent_linux_server_id|test_agent_server_route_[01]|server 122\.233\.30\.(4|114):8080|backup|proxy_set_header X-Test-Agent-(Linux-Server-Id|Backend-Routed) ""' \
      /tmp/test-agent-nginx-effective.conf
    if grep -q 'proxy_next_upstream.*non_idempotent' /tmp/test-agent-nginx-effective.conf; then
      echo '错误：禁止为非幂等请求启用重试' >&2
      exit 1
    fi
    ss -lntp | grep -E ':(80|9996)[[:space:]]'
   ```

2. 从实际浏览器网段分别执行 `curl -fsS http://mimo.sdc.cs.icbc:9996/health` 和 `curl -fsS http://122.233.30.2:9996/health`，两条都返回 `ok`；浏览器 Network 中 API 都是当前地址下的相对 `/api/...`，不能固定请求另一个 origin。
3. 登录后打开浏览器开发者工具的 Network，刷新页面并按时间排序：第一次 `GET /api/internal/agent/opencode/processes/me` 不应携带 `X-Test-Agent-Linux-Server-Id`；未绑定用户可能先进入任一 Java、再由后端转发到当前进程总数最少的可初始化服务器，因此两台 Java 都可能出现同一 traceId，但只有目标 Java 执行本地状态逻辑。响应给出 `linuxServerId` 后，进程健康、Session、Run、permission/question、工作区请求和两个 fetch SSE 请求应携带该值。用户管理、应用列表、登录和共享查询不应携带它；刷新后仍应重新从第一次 `/processes/me` 获取，不能从 localStorage/sessionStorage 恢复。
4. 用已登录浏览器中复制的 JWT 对两个静态映射做可追踪验证。下面先验证绑定在 `.4` 的用户；把 `REPLACE_LOGIN_JWT` 替换为该用户当前 token：

   ```bash
   ACCESS_TOKEN='REPLACE_LOGIN_JWT'
   TRACE_ID="trace_ngroute_a_$(date +%Y%m%d%H%M%S)"
   curl -fsS -D /tmp/ngroute-a.headers \
     'http://122.233.30.2:9996/api/internal/agent/opencode/processes/me' \
     -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H 'X-Test-Agent-Linux-Server-Id: test-agent-backend-122-233-30-4' \
     -H "X-Trace-Id: ${TRACE_ID}" \
     -o /tmp/ngroute-a.json
   grep -i '^X-Trace-Id:' /tmp/ngroute-a.headers
   grep -F "${TRACE_ID}" /tmp/ngroute-a.json

   # 分别在 .4 和 .114 执行；正常精确首跳只应在 .4 出现 api_entry。
   journalctl -u test-agent-backend --since '-5 min' --no-pager | \
     grep -F "${TRACE_ID}"
   ```

   对绑定在 `.114` 的用户把路由 ID 改为 `test-agent-backend-122-233-30-114` 重复一次，日志只应在 `.114` 出现。再分别删除路由头、改成 `unknown-server`，请求仍应成功并按 `least_conn` 进入任一健康 Java。用与真实 binding 不一致的已知 ID 重试时，首跳 Java 会根据真实 binding 转发到正确 Java，响应契约不变；这证明头是提示而不是事实源。
5. 做故障兜底演练前停止所有业务写入，只使用上面的只读 `/processes/me`。在维护窗口停止 `.4` Java 后，继续带 `.4` 路由 ID 请求：Nginx 应连接 `.114` backup，最终返回平台 JSON，`data.status=UNAVAILABLE` 或统一 `OPENCODE_UNAVAILABLE`，不能返回 Nginx HTML 502。演练后立即恢复并检查 readiness：

   ```bash
   # 在 .4
   systemctl stop test-agent-backend

   # 在 .2 或浏览器网段，重复上面的 curl；检查 HTTP 正文和 Content-Type。
   curl -sS -D /tmp/ngroute-failover.headers \
     'http://122.233.30.2:9996/api/internal/agent/opencode/processes/me' \
     -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H 'X-Test-Agent-Linux-Server-Id: test-agent-backend-122-233-30-4' \
     -o /tmp/ngroute-failover.json
   grep -i '^Content-Type:.*application/json' /tmp/ngroute-failover.headers
   sed -n '1,5p' /tmp/ngroute-failover.json

   # 在 .4
   systemctl start test-agent-backend
   curl -fsS http://127.0.0.1:8080/actuator/health/readiness
   ```

   不要用创建 Session/Run 的 POST 做断网演练。配置没有 `non_idempotent`，Nginx 不会在请求已经发往 primary 后重放非幂等请求；只有建立连接失败或超时才可能选择 backup。
6. 运行管理中出现两个不同 `linuxServerId` 的 Java、manager 和容器，连接均在线。
7. 从 `.4` curl `.114:8080/actuator/health`，从 `.114` curl `.4:8080/actuator/health`。
8. 两个服务器都能初始化用户进程，动态端口的 `/global/health`、`/api/provider`、`/api/model` 正常。
9. 做两级调度验收：先让 `.114` 的进程总数低于 `.4`，直接向 `.4` 对一个未绑定用户调用初始化，确认 binding 落到 `.114` 且只有 `.114` manager 收到 start；反转负载后让新用户落到 `.4`；再次请求已有 binding 用户，确认仍留在原服务器。
10. 分别用 Qwen 和 DeepSeek 验证普通正文、think/reasoning、工具调用、持续 SSE 和 `[DONE]`。
11. 两台后台都确认 9070 直连；正式链路中没有监听 19070 的 relay。
12. 分别打开终端、Workspace/Agent 文件编辑和 Agent 配置 Git 进度，浏览器 WebSocket URL 应直连 ticket 响应中的 `.4:8080` 或 `.114:8080`，连接不出现 ticket 无效。

两个入口是不同浏览器 origin，Cookie、`localStorage` 和登录态不会天然共享；首次分别打开时需要各自登录。这不影响同一套后台数据和会话路由。

模型验收不能只在其中一台执行。在 `.4` 和 `.114` 分别用本机 `127.0.0.1:8080` 执行 [单后台文档的两条 Java 代理 curl](SINGLE-BACKEND.md#8-验收)，分别验证 `qwen-prod + Qwen3.6-27B` 和 `deepseek-prod + DeepSeek-V4-Flash-W8A8`。两台都应持续返回单层 `data:`、正确的 `reasoning_content` 和单层 `[DONE]`；这样才能同时覆盖每台 Java 的内存快照、内部代理 key、UCID 转发和本机 9070 出站网络。

## 10. 个人工作区与 Agent 跨服务器自动搬迁

本版本新增 XXL 全局任务 `workspace-management.personal-workspace-relocation`，最终使用 `0 0/30 * * * ? *` 每 30 分钟触发一次。入口 executor 先通过 Redis 广播唤醒全部 Java；每台 Java 只扫描并认领 `workspaces.linux_server_id` 等于本机稳定服务器 ID、而用户当前 ACTIVE `opencode` binding 已在另一台服务器的个人工作区。该规则对所有应用生效，不按 `f-base`、应用名或用户白名单过滤。

搬迁不是直接改库。源端先生成可移植 Git 快照，覆盖本地 HEAD（包括尚未 push 的本地提交）、暂存区、未暂存修改和全部普通未跟踪文件（包括被 Git ignore 的文件）；随后通过精确目标 Java 的一次性内部 WebSocket 分片传输。目标端校验归档大小、SHA-256、Git HEAD/index/worktree/untracked 状态和目标目录，再在同一事务中更新 `workspaces.root_path/linux_server_id`、`personal_workspaces.repo_root_path/workspace_root_path/base_commit`，最后由源端删除旧 worktree。目标未恢复或校验未通过时不会切数据库；数据库已切换但源目录清理失败时保持 `CLEANUP_PENDING`，后续只重试旧源清理，不会被新的错配发现覆盖。

有 `PENDING`、`RUNNING` 或 `CANCELLING` Run 的工作区不会进入搬迁。存在未解决 Git merge 冲突、Git 子模块、超过 2 GiB、超过 10000 个未跟踪文件，或未跟踪项包含符号链接/特殊文件时也不会冒险迁移，而是记录脱敏错误并指数退避重试，源目录不会删除。Git ignored 普通文件同样进入快照并计入上述个数/容量上限；如 `node_modules` 或构建产物使快照超限，需先清理可重建内容后等待重试。Redis 广播只用于低延迟唤醒，不携带用户、工作区 ID 或路径；广播失败仍由下一次 XXL 调度补偿。

企业环境 `SYS_DATA_ROOT_DIR=/data/testagent/data` 时，个人仓库物理路径为：

```text
/data/testagent/data/agent-opencode/workspace/personalworktree/{versionSegment}/{userId}/{repositoryEnglishName}/{branch}
```

工作区物理路径再追加 `application_workspaces.directory_path`。数据库新记录保存 `personalworktree:` 逻辑路径；现场以 `common_parameters.OPENCODE_PERSONAL_WORKTREE_ROOT` 解析结果为权威，不要只按应用名猜目录。下面的 PostgreSQL 查询会同时给出逻辑路径和按当前 Linux 参数解析出的精确物理路径，默认覆盖全部应用；只定位 `f-base` 时可临时取消最后的应用过滤注释。

先统计受影响用户和个人工作区数量：

```sql
with mismatch as (
    select distinct pw.personal_workspace_id, pw.user_id
    from personal_workspaces pw
    join workspaces w
      on w.workspace_id = pw.runtime_workspace_id
    join user_opencode_process_bindings b
      on b.user_id = pw.user_id
     and b.agent_id = 'opencode'
     and b.status = 'ACTIVE'
    where pw.status = 'ACTIVE'
      and w.status = 'ACTIVE'
      and w.linux_server_id is not null
      and b.linux_server_id is not null
      and w.linux_server_id <> b.linux_server_id
)
select count(*) as affected_personal_workspace_count,
       count(distinct user_id) as affected_user_count
from mismatch;
```

再列出源/目标服务器、准确路径和是否被运行中任务阻塞；本查询不输出 `workspaceId`：

```sql
with roots as (
    select replace(personal_root.parameter_value,
                   '${SYS_DATA_ROOT_DIR}', data_root.parameter_value) as personal_root
    from common_parameters personal_root
    join common_parameters data_root
      on data_root.parameter_english = 'SYS_DATA_ROOT_DIR'
     and data_root.platform = 'linux'
    where personal_root.parameter_english = 'OPENCODE_PERSONAL_WORKTREE_ROOT'
      and personal_root.platform = 'all'
)
select u.unified_auth_id,
       a.app_id,
       a.app_name,
       pw.workspace_name,
       pw.branch,
       w.linux_server_id as source_linux_server_id,
       b.linux_server_id as target_linux_server_id,
       pw.repo_root_path as source_repo_logical_path,
       pw.workspace_root_path as source_workspace_logical_path,
       case
           when pw.repo_root_path like 'personalworktree:%' then
               rtrim(roots.personal_root, '/') || '/' ||
               substring(pw.repo_root_path from char_length('personalworktree:') + 1)
           else pw.repo_root_path
       end as source_repo_physical_path,
       case
           when pw.workspace_root_path like 'personalworktree:%' then
               rtrim(roots.personal_root, '/') || '/' ||
               substring(pw.workspace_root_path from char_length('personalworktree:') + 1)
           else pw.workspace_root_path
       end as source_workspace_physical_path,
       not exists (
           select 1
           from runs r
           where r.workspace_id = pw.runtime_workspace_id
             and r.status in ('PENDING', 'RUNNING', 'CANCELLING')
       ) as no_active_run
from personal_workspaces pw
join users u on u.user_id = pw.user_id
join applications a on a.app_id = pw.app_id
join workspaces w on w.workspace_id = pw.runtime_workspace_id
join user_opencode_process_bindings b
  on b.user_id = pw.user_id
 and b.agent_id = 'opencode'
 and b.status = 'ACTIVE'
cross join roots
where pw.status = 'ACTIVE'
  and w.status = 'ACTIVE'
  and w.linux_server_id is not null
  and b.linux_server_id is not null
  and w.linux_server_id <> b.linux_server_id
-- and u.unified_auth_id = '555047824'
-- and lower(a.app_name) = 'f-base'
order by u.unified_auth_id, a.app_name, pw.workspace_name;
```

查看搬迁状态和等待人工处理的安全错误：

```sql
select status, count(*)
from personal_workspace_relocations
group by status
order by status;

select relocation_id,
       source_linux_server_id,
       target_linux_server_id,
       status,
       attempt_count,
       next_retry_at,
       lease_until,
       safe_error_code,
       safe_error_message,
       updated_at
from personal_workspace_relocations
where status not in ('SUCCEEDED', 'CANCELLED')
order by updated_at;
```

在 XXL MySQL 核对任务已注册并启用：

```sql
select platform_task_key,
       schedule_type,
       schedule_conf,
       trigger_status,
       executor_route_strategy,
       executor_block_strategy,
       executor_fail_retry_count
from xxl_job_info
where platform_task_key = 'workspace-management.personal-workspace-relocation';
```

个人工作区搬迁表和 V7/V8 已随现网基线执行，本轮不重复新增。上线仍应在维护窗口先停止两台旧 Java，
将两台 JAR 和 `backend/lib/` 都替换为同一版本，再依次启动；如果只能滚动升级，应先停用本轮新增的
`opencode-runtime.inactive-user-process-cleanup`，确认所有 Java 都包含新 handler 后再启用。禁止通过
手工复制目录后直接 update `linux_server_id`，也禁止删除搬迁状态行来跳过源端清理。

清理一轮后不能承诺数据库从此永远不会短暂出现错配：正常初始化和修复链路已不再把工作空间直接切到另一台 Agent 而丢下源文件，已有 ACTIVE binding 也不会因负载变化自动迁移；但人工改库、混合版本、正式迁移 binding 或未来新增的迁移入口仍可能暂时形成新错配。该周期任务提供持续收敛，安全目标是“可发现、可恢复、先搬文件、后改库”，不是假设错配永不再发生。

首次初始化前端仍先弹确认框；如果工作区正处于服务器归属修复/搬迁，普通用户只看到“工作区与 Agent 服务器归属正在调整，请稍后重试”一类安全提示，不显示 `workspaceId`、服务器路径或内部搬迁 ID。日志可按 `event=personal_workspace_relocation_succeeded`、`event=personal_workspace_relocation_retry` 和同一 traceId 对齐，但不要记录归档内容或文件清单。

### XXL 生产任务验收

XXL 的 `opencode-runtime.inactive-user-process-cleanup` 默认每天北京时间 02:00 执行；V9 原始任务定义保持不变，后续 migration 只把仍使用默认值的任务名称更新为“十天未使用用户 OpenCode 进程关闭”。
XXL 只取得全局锁并广播空 payload；每个 Java 只处理本机实际持有 manager 连接的用户进程。候选必须严格
超过 10 天没有任何来源 Run 活动，且没有活动 Run、当天仍有效的待投递任务或扫描后新增活动；关闭后保留
ACTIVE binding，用户再次使用时由公共启动程序按原归属恢复。任务不能以登录时间替代 Run 活动时间，也不能
由入口 Java 直接控制远端 manager。

### 容量预警与新增节点判定

manager 每 5 秒上报单容器 `currentProcesses/maxProcesses`。已有 ACTIVE binding 恢复可能产生 101/100 一类超容量状态；Java 接受该真实心跳、保持 manager WebSocket，并把该容器可调度容量钳制为 0，因此不会继续接收首次分配。同一容器首次达到 80% 时，平台通过既有通知中心提醒全部有效超级管理员；回落到 70% 以下后关闭该轮预警，之后再次越过 80% 才重新提醒。Java 重启后的首份高位或恢复态心跳会做一次幂等收敛，避免 Redis 旧快照漏掉提醒或失效。80% 是容量评审触发线，不是自动调高 `OPENCODE_MANAGER_MAX_PROCESSES` 或自动创建节点的指令。

收到预警后按以下顺序判断：

1. 等最近一次北京时间 02:00 的十天闲置清理完成，再从运行管理页记录各节点业务峰值、CPU、内存、PID/FD 和磁盘指标；如果清理后回落到 70% 以下，本轮不扩容。
2. 若节点仍在 80% 以上，只有在真实压测证明 CPU、内存、PID/FD、端口和外部模型容量均有足够余量时，才评估提高全局 `OPENCODE_MANAGER_MAX_PROCESSES`；端口池有空位不能代替承载能力验证。
3. 满足任一条件就进入新增节点计划：十天清理后仍持续触发 80% 预警且资源余量不足以安全提高上限；按最近增长速度预测“达到 100% 的时间”短于节点采购、部署、验收周期加两周缓冲；全部现有节点同时达到 80%；或计划峰值已经超过“任意一台节点退出后，其余节点已验证安全容量之和的 80%”。最后一项只用于容量准备，不代表现有固定 binding 会自动故障迁移。

首次分配的新用户只从 `currentProcesses < maxProcesses` 的在线候选中选择，并优先选择当前进程数较少的容器；若 `.114` 已满而 `.4` 未满，会分配到 `.4`，所有候选都满时才返回容量不足。已有 ACTIVE binding 的恢复仍固定原服务器和原端口，并显式使用 `bindingRecovery=true` 绕过新分配容量过滤。因此新增节点只会承接后续首次分配，不能自动削减 `.114` 的 101 个存量绑定；要给热点节点降载，必须先依赖十天闲置关闭，或另行执行带工作空间搬迁、binding 代次 CAS 和回滚方案的受控迁移，禁止直接改库。

第一台 `.4` 启动后在 XXL MySQL 验收：

```sql
select version, description, checksum, success
from flyway_schema_history
where version in ('7', '8', '9', '10', '11', '12', '13', '14', '20260824100401')
order by installed_rank;

select platform_task_key, job_desc, schedule_conf, trigger_status
from xxl_job_info
where platform_task_key in (
    'workspace-management.personal-workspace-relocation',
    'opencode-runtime.inactive-user-process-cleanup',
    'opencode-runtime.internal-model-probe',
    'opencode-runtime.internal-model-observability-retention',
    'opencode-runtime.analytics-ingestion',
    'configuration-management.scm-git-name-sync'
)
order by platform_task_key;
```

预期 V7-V9 保持原 checksum，V10/V11 保持既有 checksum，已执行 V12 字节与对应兼容分支一致，V13/V14 和 `20260824100401` 成功；闲置任务仍使用默认名称时应为“十天未使用用户 OpenCode 进程关闭”，管理员已自定义名称时保持自定义值；
搬迁任务为 `0 0/30 * * * ? *`，闲置进程关闭为 `0 0 2 * * ? *`，内部模型探活为
`0 */5 * * * ? *`，可观测数据清理为 `0 30 3 * * ? *`，ClickHouse 入库为 `0 * * * * ? *`，SCM Git 姓名补偿为
`0 10 4 * * ? *`，六条均 `trigger_status=1`。任一条件不满足时
保持 `.114` 和 `.2` 未部署。

## 11. 故障定位与回滚

浏览器原始输出出现“（空报文体）”、HTTP 响应正文为零字节、SSE 空 `data:` 或 Run 成功但没有 assistant 文本时，先按 [空报文体排查手册](EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md) 保全 `traceId/runId` 并逐层对比；不要先重启、重发 Run 或切换用户 binding。

| 现象 | 排查顺序 |
|---|---|
| 已知路由 ID 仍命中错误节点 | 检查浏览器请求头、`TEST_AGENT_NGINX_SERVER_ROUTES`、`nginx -T` 中的 `map` 和专用 upstream；确认映射 endpoint 与该服务器唯一 Java 一致。binding 已变化但静态表未变时先修正表并重新部署前端。 |
| Nginx 返回 502 | `.2` 分别 curl 两个 health；检查专用 upstream 的 primary/backup、`nginx -t` 和后台防火墙。两台 Java 都不可达时才允许出现 Nginx 502。 |
| 请求头被伪造或使用旧 binding | 后端必须仍按真实 binding、Session 和运行上下文校验并转发；若伪造头能绕过权限或归属校验，立即回滚本次 Nginx 配置并按安全缺陷处理。 |
| 域名正常但 IP `:9996` 拒绝连接 | 检查渲染结果是否同时包含 `listen 80;` 和 `listen 9996;`，再检查 `.2` 防火墙是否放行 TCP 9996；不要删除域名链路仍依赖的 `listen 80`。 |
| 一个入口的 API 请求跑到另一个入口或 `127.0.0.1` | 前端不是以显式空的 `VITE_TEST_AGENT_API_BASE_URL` 构建；重新部署本次双入口包，不能只改服务器 `docker.env`。 |
| WebSocket 报 ticket 无效或浏览器连接超时 | 检查 ticket 响应是否为目标 Java 的绝对 `ws://<后台>:8080/...`，再从浏览器网段检查两个 `:8080` 可达性和两台 Java 的 Origin 白名单；不增加 sticky。 |
| 部署提示 systemd/8080 不匹配 | 执行 `systemctl show test-agent-backend -p ExecStart -p EnvironmentFiles -p MainPID` 和 `lsof -nP -iTCP:8080 -sTCP:LISTEN`；脚本只自动清理同一路径交付 JAR，其他进程必须人工确认。 |
| 运行管理只显示一个服务器 | 检查两台是否共享同一 Redis、ID 是否唯一、heartbeat 日志和广播 channel 是否一致。 |
| 初始化报“进程分配已变化，拒绝旧启动结果回写”，或清缓存后运行管理用户重新出现 | 先按[不依赖 `rg`/`jd` 的用户进程冲突排查流程](../../docs/deployment/backend.md#opencode-process-assignment-conflict-troubleshooting)保留 operationId/traceId，查询操作历史与当前快照，再用 `journalctl`、`docker logs`、`grep`、`sed`、`find` 和浏览器 Network 对齐并发写入来源；不要先重启或直接修改 binding。 |
| worker 连接到错误 Java | 检查本机 `.serverhost`、两份 env 的数据根目录和本机 manager token；禁止复制另一节点身份文件。 |
| 跨节点请求失败 | 两台互相 curl `advertised-host:8080`；检查 Redis 快照、目标 Java health 和日志中的 traceId。 |
| 排查授权签发成功，但选择用户立即返回 `401 排查授权无效或已失效` | 先确认所有 Java 节点都已部署包含“排查授权时间微秒归一化”的同一版本，并共享同一 Redis 与 PostgreSQL；旧版本在 Linux/JDK 纳秒时钟下可能因 PostgreSQL `timestamp` 微秒截断而误判 Redis/数据库授权身份不一致。升级全部 Java 节点后重新签发授权，不增加 sticky、不放宽鉴权，也不修改 Redis 中的授权值。 |
| 某一台模型不通 | 先在故障节点直接调用本机 Java 代理并读取 4xx 正文，再在该 Java 宿主机检查 9070；共享数据库配置不能替代每台宿主机的内存刷新和网络可达性。 |
| 某一台报“供应商未启用或不存在” | 对比两台 refresh-status；确认广播开启且 channel 相同，必要时在故障节点重启 Java 重载数据库。 |
| 某一台模型不显示 | 初始化该 `linuxServerId` 的公共配置，确认包含 `includeUsage=false`，并重启该服务器上的用户 OpenCode 进程。共享数据库不会复制本机公共配置工作树。 |
| 只有某些旧用户 400 | 重启这些用户所属节点上的 OpenCode 进程，让其重新读取公共配置并由所属 Java 重新注入内部代理地址、key 和 UCID。 |
| 两台都连接 9070 超时 | 确认 OpenCode `baseURL` 指向同节点 Java `:8080`；只允许 Java 宿主机出站访问 9070，不恢复 19070 relay、host network 或 OpenCode 直连 9070。 |
| 后台下线后已有用户不可用 | 用户 binding 不会自动迁移；恢复所属服务器，或按正式迁移流程停止并重新分配，不能由入口 Java 本机降级。 |

滚动回滚时一次只回滚一台，恢复该节点旧 JAR、`backend/lib/`、programs 和 worker 镜像，并按“Java → 身份文件 → worker”启动。不要删除共享数据库/Redis数据，也不要清空任一节点 `/data/testagent/data`。
