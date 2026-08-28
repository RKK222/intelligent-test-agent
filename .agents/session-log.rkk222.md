# Session Log - rkk222

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

### 2026-08-25 - 合并远程 release 并重建 AAM 与本地客户端企业增量包

### Why

- 用户要求重新拉取远程 `release`，以合并后的本地代码重建企业包；上一批候选尚未部署，不能继续作为现场基线。
- 远端新增 AAM 登录验真与平台会话安全，当前本地同时已有客户端跨版本依赖缓存、重连恢复和公共配置发布记录展示，均需进入同一批次。

### What

- 拉取并合并远端 `release`，整合 `687b9a324ca8aeb622eaaece40fa96616c53a07b` 的 AAM 安全改动与本地提交；合并提交为
  `289f5ae6081e79e548dd764c72f905f99b59d0ca`。冲突只发生在前后端 README/PACKAGE 和 `App.vue`，保留 AAM 回调清理、
  首屏品牌加载以及既有 SkillHub 契约，没有改写 OpenCode 源码或 generated SDK。
- 新企业客户端不可变版本为 `20260825204745`，公共能力继续锁定
  `81605f245d1512e1ab0dd73812391f6da7d008b5`，控制和下载域名继续为 `http://mimo.sdc.cs.icbc:9996`；客户端组件
  `included`，worker runtime/toolbox 为 `reuse`，LobeHub/memory 为 `disabled`。
- AAM 后端基址固定为 `http://zfw.sdc.cs.icbc`，前端登录入口为
  `http://zfw.sdc.cs.icbc/aam/onlyLogin/`；平台令牌改为后端验真后签发的不透明会话令牌，并从回调 URL 清除认证参数。
- 修正 `local-opencode-client-update-test.sh` 中已过时的缓存提示断言，使其与安装器和完整包测试统一使用“内容缓存”；
  该修正只影响测试，不改变客户端、前后端二进制或部署行为。

### How

- AAM 后端鉴权/会话定向测试、agent-web AAM/backend-api 130 项和类型检查通过；正式发布脚本完成后端、用户手册、前端、
  客户端签名分发、persistence/XXL migration 字节及组件指纹门禁。
- 前端全量 Vitest 153 个文件通过，2235 项通过、1 项条件跳过；本地客户端 Maven 下载器测试、麒麟普通用户完整包测试，
  以及稳定升级/回退/坏版本自动回滚和签名依赖缓存测试均通过。
- 更新测试首次失败是旧提示词断言，不是升级逻辑；使用执行跟踪定位到精确断言，按当前安装器稳定文案修正后单独复跑通过。

### Result

- 当前正式二进制基线为 `289f5ae6081e79e548dd764c72f905f99b59d0ca`，内层首次完整构建 SHA-256 为
  `a6cb0cfd98da4e82a4f5594f26db85eda38653e821b7ade33dba5e4b4069c6e6`；记录和测试修正提交后只允许用同批
  `--zip-only` 重封，不重新编译或混入其它二进制。
- 同批重封后的最终内层 SHA-256 为 `88694763fa53e2a1e2962283fea4ca7c9c4c8fc964662c0a2d63df2f2df709c8`，
  固定名外层包 SHA-256 为 `f8e725bcf5a5876a2e194e527ce76d73fd14a116d14940eb8acac77c7f84c25a`；外层嵌入
  的内层与独立内层逐字节一致。三份节点归档 `.4/.114/.2` 的 SHA-256 分别为
  `01a45de27b4d621319f130e4b604bf7b06eb165027d3aa6c165252630121cc17`、
  `848cd2fa809b2652cbdad9e970f4ec50aed11952cde4eff6a665bcd420e81f50`、
  `94530d48cc30e12627f694cb260bb80f482cf1aec78ad696fd209d385465642d`。
- 后端应用 JAR、persistence JAR、XXL integration JAR、前端 tar 和麒麟普通用户包 SHA-256 分别为
  `490b42b6dd2368839136b0f0318c6e8f6a7b7e161919370be12121ed8bebac36`、
  `70f9228de25a31180bb050a5be59c80db2a70343256709b3d44480c100713ef7`、
  `cff9cd51ed648ebc4ae11e6a2378f4b6e145ba48fc498dc03c025cff47f461fb`、
  `29d7f7ee5ad64ee8274a38899842290c17fe1405fa2ba541eff6bc91d50d65d6`、
  `6b573faca5b26288ad1d4384238b8b6c6f1b9016a75e0b2f0d8d6b45ca02e75b`；固定名包和校验文件已覆盖到
  `/Users/kaka/Desktop/mimoagent/0709/` 并再次通过 SHA-256 与 ZIP CRC 校验。
- 固定包、逐节点、Nginx 渲染/失败关闭和客户端签名分发门禁通过；本机 Docker CLI 在现存 Nginx 镜像的 `docker run`
  阶段无响应，因此真实容器 `nginx -t` 被终止并以无 Docker 路径复跑其余全部断言，企业目标机的真实 `nginx -t` 仍是现场闸门。
- 相对 2026-08-24 企业已部署数据库历史，PostgreSQL 仍只允许新增
  `V20260825091459__local_client_instance_replacements_create.sql`，源码 SHA-256
  `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`、Flyway checksum `749555545`；
  XXL MySQL 和 ClickHouse 不得新增 history，禁止 `repair`、`outOfOrder` 或手工修改历史表。
- 本机只完成构建与自动化校验，尚未执行企业 `.4 -> .114 -> .2` 部署、AAM 真实验真或麒麟普通用户清空后首次安装。

### 2026-08-25 - 基于当前 release 重建企业增量包并更新客户端重接入

### Why

- 上一批基于 `54aeccceec868647697e0834c62a32a34dae6020` 生成的企业候选包尚未部署，当前本地
  `release` 的运行代码已前进到 `db76b47228edc8c0aaab3d691d99b15d4a47d87b`，需要作废中间候选并从当前提交重新构建。
- 本轮客户端主程序、普通用户安装器和前端/后端实例展示均有实际变化，不能只重封旧二进制；worker、manager、
  toolbox 以及已独立部署的 CK/Mem0/BGE/pgvector 没有输入变化，不能重复打包或重启。

### What

- 使用正式发布入口重建后端、同源前端和组织密钥签名的麒麟 ARM64 普通用户客户端；新客户端不可变版本为
  `20260825162157`，下载与控制地址继续固定为 `http://mimo.sdc.cs.icbc:9996`，公共能力继续锁定
  `81605f245d1512e1ab0dd73812391f6da7d008b5`。
- 当前客户端支持服务端 key 轮换后的安全重新接入、桌面“启动/重连”入口和更早初始化文件日志；后端实例列表只展示
  仍有 Redis 实时连接的实例，前端能力来源文案统一为 `SkillMarket/MIMO`。
- 同批平台代码同时包含 `5a9c27bdf` 的历史本地工作区安全接管、同用户旧连接撤销和企业模型路由配置下发，以及
  `db76b4722` 的 SkillHub ZIP/图片一键上传、串行进度刷新、同步并刷新目录。前者修改平台 API/runtime/persistence 和
  前端工作台，没有修改客户端 JAR、协议源码或安装器指纹输入，因此继续复用尚未部署的同一签名客户端版本，不制造新版本。
- 组件清单为 worker runtime `reuse`、toolbox `reuse`、local client `included`、LobeHub/memory runtime
  `disabled`；最终平台 ZIP 只保留客户端 `20260825162157`，未携带本机历史 release。

### How

- 正式发布脚本完成 Spring Bean 构造器门禁、JDK 25 后端封装、VitePress/TypeScript/前端生产构建、客户端签名分发、
  persistence/XXL JAR migration 字节门禁和组件增量检查。
- JDK 25 Maven reactor 7 模块通过：本地客户端 98 项通过、1 项条件跳过，system-management 72 项通过；
  最新 runtime/API 工作区与模型路由定向测试通过；前端全量 Vitest 152 个文件通过，2221 项通过、1 项条件跳过。
  客户端普通用户安装与稳定升级/回滚两套 Shell
  验收、固定外层包和逐节点门禁、ZIP CRC 与内外层嵌套摘要检查均通过。
- 首次补跑 Maven 时系统默认 JDK 17 无法加载 Java 21 字节码，显式切换项目约定的本机 JDK 25 后同一测试范围完整通过；
  该问题未修改源码或制品。

### Result

- 正式二进制批次已验证；记录提交后只用同批已验证制品执行 `--zip-only` 重封内层，再重建固定名双后台外层包。
  最终内层 ZIP SHA-256 为 `250848229df7fbe73a84966fc5e983ba6c91d50b96bce305e28b8cdb05b90aa7`，
  外层固定名包为 `ab2cebf21e8624cfd47c77ed7c84050f34b18e1a93efb43ab0663ec775c8d2ac`；外层嵌入内层与独立内层逐字节一致。
- 后端应用 JAR、persistence JAR、XXL integration JAR、前端 tar 和麒麟普通用户包 SHA-256 分别为
  `b04da4c5d27d6c8e5c5909018eb59742b49122d119ff6d2f3bbf183d58c5c1d7`、
  `47ba584b644c00ad6366512e09770c104e60c0f12b376bb1f4c7c3078735a818`、
  `6c0fea31cb338e8c7cedb4d2cc45617485f4e4e58ea160fc6d1770082fee3312`、
  `b824e26153ee1907c7d3ffb61df3c02344f55880a2c988c9977b582588bb52be`、
  `dd2da4a61e26795cb16ebb2bee2c0989630b2e12626ea0a4950a9dfca9d38784`。
- 三份节点归档 `.4/.114/.2` 的 SHA-256 分别为
  `bedd9321dd89a4294453896085a81a8bd5ae6256112d63fe85b5d9293da660c8`、
  `f2de461015172943d4089269b93b5dd7fe63ee9f93e1ba5c5ddd7caf4922f1cd`、
  `fb434db3598ab5df0200817120565aab1aa8dd000f7c7bff01cf63ae3b91e942`；固定名外层包和校验文件已复制到
  `/Users/kaka/Desktop/mimoagent/0709/` 并再次通过 SHA-256 与 ZIP CRC 校验。
- 相对企业 2026-08-24 已部署数据库历史，本轮 PostgreSQL 只允许新增
  `V20260825091459__local_client_instance_replacements_create.sql`，源码 SHA-256 为
  `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`，Flyway checksum 为
  `749555545`；XXL MySQL 和 ClickHouse 本轮不得新增 history。现场历史不符、失败记录、未知更高版本或未知 checksum
  必须停止，禁止 `repair`、`outOfOrder` 或手工改表。
- 本次不新增部署节点，不修改 OpenCode 只读源码、generated SDK 或 `.env*`；业务 API 路径、RunEvent/SSE 和数据库
  migration 字节均未在本轮新改，企业真实 `.4 → .114 → .2` 部署及麒麟用户清空后首次安装仍待现场验收。

### 2026-08-25 - 将重复打包与签名问题固化到企业部署技能

### Why

- 企业发布主对话反复出现“最后构建包”和“最后成功部署包”混用、相同代码重复打包、客户端版本误触发大组件、签名私钥位置及用途混淆、SkillHub Access Key 被误认为客户端签名 key 等问题。
- 原企业部署技能覆盖了完整逐机流程，但上述高频判断分散在历史会话和部署文档中，后续打包仍需反复重新归纳。

### What

- 保留既有 `enterprise-offline-deploy` 作为唯一入口，新增按需参考，集中记录成功部署基线、重构建与 `--zip-only` 边界、组件计划和包体排查、内外层 SHA 一致性及未部署候选作废规则。
- 区分客户端 release 组织 RSA 密钥、Java JAR 内置 SSH 混合加密密钥、麒麟系统包签名和 SkillHub Access Key，并提供不回显私钥或业务 key 的 Mac/企业检查命令。
- 固化 worker/toolbox/client 增量门禁、`.4` models 灰度、已部署 CK/Mem0/BGE/pgvector、Workflow/LobeHub 默认关闭、TCDS/客户端域名和 Flyway 外置 persistence JAR 等现场边界。

### How

- 完整读取技能维护规范和原企业部署技能，对照当前部署文档、打包脚本及近期全部会话记录提炼已实际发生的问题。
- 使用技能 `quick_validate.py` 校验目录和 frontmatter，检查参考链接存在，并执行 `git diff --check`。

### Result

- 原企业部署技能现在会在重复打包、签名、包体异常、指纹或 Flyway 问题中按需读取同一技能目录下的参考，不会注册或触发第二个企业部署技能。
- 本次只修改技能说明和会话记录；不涉及业务代码、API、RunEvent、数据库/Flyway SQL、环境配置、generated SDK、OpenCode 源码、企业制品或服务重启。

### 2026-08-25 - 修复企业客户端归档并重建当前 release 增量包

### Why

- 用户要求基于当前 `release` 重新打企业增量包，且未变化内容不能重复携带；本机客户端构建目录同时保留上一版和当前版 release，原平台 ZIP 会把整个目录复制进去，导致历史 JDK/OpenCode 和用户包重复交付。
- 正式构建还暴露出相对 `--output-dir` 下运行时归档路径会在切换 staging 目录后解析错误，客户端构建直接找不到目标 `jdk.tar.gz`。

### What

- 客户端运行时归档先将输出文件规范为绝对路径，再进入 JDK/OpenCode staging 目录执行确定性打包。
- 企业内层 ZIP 只暂存当前客户端 release、当前用户包、stable 清单和安装器；若本机构建 catalog 含历史版本，则使用固定组织私钥生成并签署只含当前版本的 catalog，再用对应公钥验签，私钥不进入制品。
- 修正双后台 `START-HERE.md` 的现网 Flyway 基线：本轮只允许 PostgreSQL 新增 `20260825091459`，XXL/ClickHouse 不新增；外层封装增加文件名、SHA-256 和 Flyway checksum 三项文档同步门禁。
- 增加相对输出路径回归、历史客户端 release 泄漏回归和裁剪后 catalog 签名回归；部署 README 明确本机构建历史与企业交付快照的边界。

### How

- `bash -n` 覆盖两个打包脚本和两个回归脚本，`git diff --check` 通过。
- `tools/verify-internal-incremental-components.sh` 通过，确认历史版本不进入 ZIP、单版本 catalog 内容正确且签名可由组织公钥验证。
- `deploy/internal/tests/local-opencode-client-package-test.sh` 通过，覆盖相对输出、离线安装、签名发布、普通用户包与 bootstrap。
- `local-opencode-client-update-test.sh` 通过；SkillHub 后端 24/24、前端 89/89 通过；正式双后台包的 ZIP CRC、内嵌内层 SHA、Flyway persistence/XXL JAR、三份节点包、RSA、公钥、配置脱敏和 manager 日志兼容门禁全部通过。

### Result

- 首个候选包复核时发现外层 `START-HERE.md` 仍沿用 8 月 24 日 Flyway 基线，已明确作废且不得分发；修正文档和封包门禁后再生成最终 hash，避免包内追溯记录引用已作废候选。
- 最终包基于源码提交 `54aeccceec868647697e0834c62a32a34dae6020`：内层 SHA-256 `08f207124af09b2e262c70a797cae9390796a27e0b5fccf40fcbdada927892c7`，外层固定名包 SHA-256 `cf714cc9b3f8cf5a7ec0dfb7f1ffba891f904617b205f34641d80c30f5c9a763`，外层内嵌内层字节一致。
- 后端 JAR、前端 tar、麒麟用户包分别固定为 `4ad56a5862659ea1fb2a335a5678cee93b0de67d477ba74a76e62ace126c0886`、`f26ba562072feec1f36123ee9b6ca69d1942eb1449b791d0947e2d9fdea145a5`、`365a0f17ecd9afb27d83c096ebd4e86c40e7e576d461d71487697218093ba6b2`；三份节点包 `.2/.4/.114` 分别为 `aa69f1ed245dbae6163ba6bc8d55701743bc97a6cd70e1b5cfc01c263969cd73`、`2d12de8616a1ebc22b72960affe860c83fa6f7d5d850ac5e60a1087120749a88`、`dc7c82e72a3d4c7150b85d3d4068ef693d977c07a96de2286c1f2098c40504a4`。
- 组件清单为 worker runtime `reuse`、toolbox `reuse`、客户端 `included`、LobeHub/memory runtime `disabled`；平台 ZIP 从约 697 MiB 降回约 423 MiB，只含当前客户端 release。Flyway 当前待企业库执行的前向迁移仍为 `V20260825091459__local_client_instance_replacements_create.sql`，SHA-256 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`。
- 本次不新增部署节点，不修改业务 HTTP API、RunEvent/SSE、数据库结构、Flyway SQL、generated SDK、OpenCode 源码或 `.env*`；只收紧企业发布归档完整性与增量边界。

### 2026-08-25 - 稳定客户端下载入口并复用已验签依赖缓存

### Why

- 本地客户端全部离线时，头像菜单下载入口会随实例轮询的 `isFetching` 周期闪烁；在线时又缺少一个固定位置供用户手工重新下载最新安装包。
- 客户端每次发布都会重新生成 JDK/OpenCode 压缩包，文件时间戳导致内容摘要变化，安装器因此无法判断依赖其实没有变化并重复下载。
- 企业现场重复安装后看到两条 `local_client_instances` 记录并误判为重连冲突，需要明确历史实例记录与 Redis 实时连接状态的边界，同时验证安装更新不会改写已有凭据和实例身份。

### What

- 头像菜单下载入口改为只依赖“实例接口是否曾成功解析”和“是否存在在线客户端”，后台轮询不再让入口闪烁；个人设置固定提供“下载最新客户端包”。
- JDK/OpenCode 发布归档固定条目时间、顺序、所有者和 gzip header，使相同输入跨客户端版本得到相同 SHA-256。
- 稳定安装器优先从当前不可变 release 复用与新签名清单大小、摘要一致且通过当前公钥验签的 JDK、OpenCode 和公共能力包；校验不通过时仍走下载，不复用解压目录或未认证文件。
- 更新测试新增无系统 Java 场景，锁定未变化依赖不发起 HTTP 下载，并继续校验 `credentials.properties` 与 `state.json` 在重复安装和升级后保持不变。

### How

- 通过 FigmaShell/个人设置定向测试（75 项）、帮助中心测试（16 项）、agent-web 类型检查与生产构建、用户手册构建。
- 通过 `local-opencode-client-update-test.sh` 和 `local-opencode-client-package-test.sh`，覆盖确定性归档、签名缓存复用、自动回滚、稳定身份、用户包与 bootstrap。
- 按 `.env.test` 和 Java 25 执行真实重启；后端全模块构建成功，8080 health/readiness 与 3000 响应正常，但 Docker Desktop 将项目 ClickHouse 容器长期停在 `Created`，完整重启因此未通过，挂起的 restart/docker CLI 进程已清理。

### Result

- 下载入口的闪烁和手工下载缺口已在代码与自动化测试中修复；相同的签名依赖可跨客户端版本复用，客户端 JAR 等真正变化的制品仍会下载。
- 两条数据库实例记录不等于两个在线连接：旧实例需保留运行历史，在线状态以连接存储为准；本次未执行删除或修改企业数据库。
- 未新增 API、事件、数据库或部署节点；兼容旧客户端。企业现场重连仍需以目标机器的用户服务状态和日志确认，不能仅凭数据库行数判定完成。

### 2026-08-24 - 恢复麒麟 DEB 安装包下载并补齐桌面入口

### Why

- 上一轮为绕开企业麒麟“未签名软件包”阻止策略，把网页下载入口临时改成了 `install.sh`；用户明确要求普通用户下载后得到可双击安装的安装包，而不是 shell。
- 平台运行时 RSA 清单签名与麒麟安全中心的系统包签名属于两套信任链；前者不能让未签名 DEB 在阻止模式下通过。

### What

- 网页、Vite 开发下载路由和 Nginx `/installer` 统一恢复下载 `TestAgent-Local-Client-Kylin-arm64.deb`，界面文案明确为“安装包”。
- 提取 `build-local-opencode-client-deb.sh` 作为唯一 DEB 组装器，继续复用受控启动器，并新增系统应用菜单项与 512px 小兔子图标；`package-release.sh` 的客户端指纹包含该组装器。
- 部署、安全、前端和用户手册明确：生产 DEB 必须由企业麒麟软件管理平台或 UKey 加签，并在真实终端通过 `kylinsigntool -v`；没有企业私钥时不得用脚本绕过来源检查。

### How

- `local-opencode-client-package-test.sh` 两次通过，验证标准 DEB、启动器、桌面入口、图标、不可变签名运行时、catalog 与 bootstrap；FigmaShell 定向 66 项通过。
- 用户手册 VitePress build、agent-web TypeScript 检查与 production build 通过；独立 Vite 在 `http://127.0.0.1:3102` 启动，`/installer` 返回 302 到 DEB，最终响应为 `application/vnd.debian.binary-package`，下载后由 `file` 和 `ar` 识别为 Debian package。
- 基于现有 `20260824185203` 受控启动器生成待企业加签的 `/Users/kaka/Desktop/mimoagent/0709/TestAgent-Local-Client-Kylin-arm64.deb`，最终 SHA-256 为 `4925bcda64871eae283af72c87db3a7f57b169a19492036b372800cedde68558`。

### Result

- 普通用户下载入口不再返回 shell；安装后可从应用菜单启动首次 `setup`，不要求用户寻找命令。
- 本机生成的 DEB 结构与下载链路已验证，但尚未获得企业麒麟 UKey/签名服务加签，不能表述为已通过企业阻止模式；真实麒麟验签与双击安装仍是发布前闸门。
- 本次不新增 HTTP API、RunEvent/SSE、数据库、Flyway、部署节点或强制环境变量，不修改 `.env*`、generated SDK 或 OpenCode 只读源码。

### 2026-08-24 - 新增企业内用户服务器定位与无专用命令排查技能

### Why

- 企业内问题排查需要先确认用户当前绑定在哪台服务器，再判断工作区、Java 后端和 worker/manager 是否同节点；现场可用 DBeaver 直连 PostgreSQL，但终端没有 `rg`、`jq`、`qgsql`。

### What

- 新增项目技能 `enterprise-troubleshooting`，以 `users`、`user_opencode_process_bindings`、`opencode_server_processes`、`workspaces`、`backend_java_processes` 和 worker/manager 拓扑为证据源。
- 技能强制使用 DBeaver 只读 SQL，区分用户 Agent 进程服务器、工作区服务器、Java 请求节点和 worker/manager 服务器，并禁止命令行数据库客户端、`rg`、`jq`、`qgsql` 及未取证前的写库/重启。
- 增加 3 个排查场景 eval，覆盖用户服务器定位、工作区与进程服务器不一致、跨 Java/worker 路由异常。

### How

- 对照项目 Flyway 表结构和既有企业排查文档编写可复制 SQL；日志采集只保留 `grep`、`sed`、`find`、`journalctl`、`docker logs` 等基础工具，并要求按时间窗和脱敏字段取证。
- 运行技能格式校验、eval JSON 解析、`git diff --check`，并完成 3 个 with-skill 与 3 个 baseline 的轻量 smoke test。

### Result

- 技能文件和 eval 样例已落入 `.agents/skills/enterprise-troubleshooting/`，静态校验通过；本次不涉及业务代码、API、事件、数据库结构、环境配置或服务重启。

### 2026-08-19 - 修复 macOS screen 会话丢失环境变量与旧服务器进程绑定清理

### Why

- `restart-dev-services.sh` 启动后端 `screen` 会话时，`bash -lc` 登录 shell 会丢弃当前 shell 中的环境变量，导致 PostgreSQL 数据库配置解析为空字符串，Druid 启动失败；此外缺少 ClickHouse 动态密码注入导致服务反复崩溃。
- 用户前端登录 `usr_test_dev` 时，小宠物提示“TestAgent 进程不可用（172.20.10.2）”，点击重启提示“TestAgent 进程停止前无法确认当前实例”，原因为数据库中保留了此前热点/历史网络（`172.20.10.2`）上的孤儿进程绑定，而当前 manager 已在新 serverId（`kakadeMacBook-Pro.local`）上注册。

### What

- 修改 `restart-dev-services.sh`，在拉起 `screen` 前将当前 shell 的全量环境变量导出为快照文件 `${LOG_DIR}/backend-env.sh`，在 `screen` 内执行 `source` 后再启动 Java 进程，确保所有配置与动态生成的密码完整透传。
- 清理本地数据库中 `usr_test_dev` 在旧 `172.20.10.2` 服务器上的孤儿 `user_opencode_process_bindings`、`opencode_server_processes` 及历史失败操作记录，使前端能正常探测到当前本机的候选容器并重新进入初始化流程。

### How

- 使用 `export -p > "${LOG_DIR}/backend-env.sh"` 替代子 shell 零散 export，保持与启动脚本一致的环境上下文。
- 执行精准 SQL 清理 `usr_test_dev` 的陈旧绑定，不影响其他测试用户数据。

### Result

- 后端 Java 进程成功加载 `.env.test` 与 ClickHouse 环境变量，Flyway 109 项迁移全部校验通过，后端在 8080 端口健康启动。
- `usr_test_dev` 的旧绑定已清除，刷新页面后小宠物状态将切换为“需要初始化 TestAgent 进程”，点击“初始化进程”即可在当前主机容器上正常分配并拉起 OpenCode。


### Why

- 用户反馈在“引用配置 -> 自动化代码库”页面中存在以下问题：
  1. 后端查询远程目录树时使用 `git archive --remote`，由于 Gitee/GitLab 等远程托管平台默认关闭 `upload-archive` 导致请求超时（60s `GIT_TIMEOUT`），页面提示“当前分支没有可选的目录”；
  2. `activeTemplate` 计算属性存在回退兜底，导致用户选择新目录后表单错误渲染为“新增版本”（旧模板）而非“保存目录引用”，导致新选目录无法生成工作区引用且工作区完全看不到；
  3. 点击左侧自动化代码库卡片需要直接弹出三阶段拉取同步模态框（`RepositoryOperationProgressDialog`），而非仅展示小动画。

### What

- **后端 Git 目录树查询改造**：
  - 在 `test-agent-common` 的 `GitRemoteService` 中将树节点解析工具类与方法公开复用。
  - 在 `test-agent-configuration-management` 的 `GitCloneCacheService` 中新增 `listTree` 与 `parseLsTreeOutput`，基于浅克隆元数据缓存和 `git ls-tree -r -t FETCH_HEAD` 毫秒级解析目录与文件树结构。
  - 在 `ConfigurationManagementApplicationService.listRepositoryTree` 中切换为 `gitCloneCacheService.listTree`，彻底解决远程 Git 目录加载超时。
- **前端自动化代码库配置面板交互与状态修复**：
  - 在 `AutomationReferenceConfigurationPanel.vue` 中修复 `activeTemplate` 计算，严格校验 `repositoryId` 与 `selectedFolderPath` 匹配；
  - 选中新目录时正确展示“保存目录引用”按钮（调用 `createApplicationWorkspace`），并在创建成功后触发工作区与文件树刷新挂载；
  - 保持点击左侧版本库卡片时直接唤起三阶段同步拉取弹窗（`RepositoryOperationProgressDialog`：创建同步任务、各服务器同步、汇总同步结果）。
- **自动化测试补充**：
  - 新增 `GitCloneCacheServiceTest.java` 覆盖 `parseLsTreeOutput` 层级树解析；
  - 更新 `ConfigurationManagementApplicationServiceTest.java` 适配 `gitCloneCacheService.listTree`；
  - 更新 `automation-reference-configuration-panel.test.ts` 覆盖目录切换、新目录引用创建与点击卡片自动唤起三阶段拉取弹窗。

### How

- 修改 `GitRemoteService.java`、`GitCloneCacheService.java`、`ConfigurationManagementApplicationService.java` 及对应单测。
- 修改 `AutomationReferenceConfigurationPanel.vue`、`automation-reference-configuration-panel.test.ts`。
- 执行 Maven 测试（`test-agent-backend` 全量构建与单测通过）以及前端 Vitest / vue-tsc 检查（8 passed，0 错误）。

### Result

- 自动化版本库目录树加载在毫秒级内完成，彻底消除 60s 挂起超时；
- 点击左侧代码库卡片即刻弹出三阶段拉取进度弹窗；
- 用户选择新目录能够正确保存为应用工作区引用并在工作区组合文件树中正常展示与读取。


### 2026-08-19 - 自动化代码库引用面板交互与展示全面对齐应用资产库并支持前端缓存与加载等待反馈

### Why

- 用户要求自动化代码库引用面板的交互方式、视觉语言、面板划分和操作流完全与“应用资产库”（`ReferenceConfigurationDialog.vue`）保持一致，消除原先在布局、主题变量、深色色块、卡片结构和操作步骤上的明显差异；同时要求在分支拉取和目录读取时具备平滑的等待加载反馈，并在前端缓存已拉取过的分支与目录树，避免切换时的重复网络请求。

### What

- **交互范式与双栏布局完全对齐**：
  - **左侧版本库列表（`.reference-repository-column`）**：以代码库（Repository）为第一公民展示卡片（图标、名称、Git 地址、就绪服务器计数 `X/Y 台就绪`），卡片内嵌“切换分支”Popover 浮层。
  - **右侧顶部 44px 标题与状态栏（`.reference-selected-heading`）**：展示当前选中的代码库名称、目录副标题、服务器路径代码块（`<span>服务器路径</span><code>...</code>`）与带旋转动画的“刷新 Git 指针”按钮。
  - **服务器 Git 指针状态面板（`.reference-pointer-panel`）**：上方展示“目标 Git 指针”行（含 Commit Hash 与复制按钮），下方展示包含所有服务器副本状态的表格（`.reference-pointer-table`：服务器在线/离线、状态、实际分支、实际 HEAD、目标一致性徽标与同步/核验时间）。
  - **底部工作区（`.reference-ready-layout`）**：
    - **左栏 42% 目录树（`.reference-tree-panel`）**：标准可折叠目录树，包含层级缩进（`8 + depth * 16px`）、`ChevronDown`/`ChevronRight` 展开收起箭头、`Folder`/`File` 图标、以及目录点击高亮选择。
    - **右栏 58% 配置表单（`.reference-form-panel`）**：统一展示版本库名称、分支选择（带拉取动画）、引用目录（只读/占位符）、引用名称、版本日期、已配置模板的版本历史列表（支持设为当前版本及触发 `RepositoryOperationProgressDialog` 异步同步弹窗）与操作按钮。
- **自动同步与三阶段进度弹窗交互对齐**：
  - **点击代码库卡片自动同步**：管理员点击左侧已配置版本库卡片时，自动触发代码库版本同步，并弹出包含“1 创建同步任务、2 各服务器同步、3 汇总同步结果”的三阶段 `RepositoryOperationProgressDialog` 异步弹窗，在各服务器副本收敛前保持轮询，与应用资产库行为完全一致。
  - **刷新 Git 指针联动同步**：点击顶栏右侧“刷新 Git 指针”按钮时，自动触发当前版本的代码库同步与状态核验。
- **前端内存缓存**：在前端通过 `branchesByRepository` 和 `treeByBranchKey` 缓存已拉取的分支与目录树数据，同一应用与分支下切换无需重复请求网络。
- **全阶段加载动画**：在代码库加载、分支拉取（表单标签内嵌 Spinner + 文字）、目录读取（标题 Spinner + 居中 Spinner + 目录输入框 placeholder）、异步副本初始化和保存/新增按钮（内嵌 Spinner）提供平滑视觉反馈。

### How

- 重构 `frontend/apps/agent-web/src/components/AutomationReferenceConfigurationPanel.vue`，在 `selectRepository(repository, autoSync = true)` 和 `verifyPointers()` 中联动触发 `synchronizeVersion` 并挂载 `RepositoryOperationProgressDialog`，彻底消除独立创建模式的割裂流程，完全复用应用资产库的 DOM 结构、CSS 变量语义和组件规范。
- 补充 `frontend/apps/agent-web/tests/automation-reference-configuration-panel.test.ts` 中的加载动画、前端内存缓存以及点击代码库/刷新指针自动触发同步弹窗的单测。
- 执行前端测试套件（54 passed）及完整构建检查（`npm run build` 产物打包成功，0 错误）。

### Result

- 自动化代码库面板在色彩、层级、边框、字体、间距、表格、目录树与弹窗交互上与应用资产库达到 100% 视觉与体验一致；点击代码库卡片和刷新按钮均能自动触发三阶段同步弹窗，分支和目录具备前端缓存且加载时反馈清晰。
- 未改动任何后端 API、事件契约、数据库结构、安全规范或部署依赖。

### 2026-08-18 - 优化用户对话、被分享人对话与无边框智能体回答视觉色彩

### Why

- 用户反馈用户对话（本人）与被分享人/协作者对话的颜色过于接近、难以直观区分，且智能体输出不需要显式边框，要求完全通过纯色彩与轻量背景色进行高质感区分。

### What

- **用户本人对话（User Bubble）**：使用极清爽的无边框冰蓝色 `#E6F4FF`，展现清晰的“我的输入”质感。
- **被分享人 / 协作者对话（Other User Bubble）**：使用高辨识度无边框柔紫 `#F3E8FF`（`--ta-chat-other-user-bg`）与深紫协作者姓名高亮，与本人浅蓝对话形成鲜明且舒适的色彩区分。
- **智能体输出（Agent / Assistant Output）**：去除显式边框（`border: none`），采用高质感无边框柔灰/冷白卡片 `#F7F8FA`（`--ta-chat-answer-bg`）与柔和微阴影（`0 1px 3px rgba(0, 0, 0, 0.02)`），实现与彩色用户气泡自然契合的极简无边框三方视觉区分。
- 同步更新 `globals.css`、`tokens.css`、`parts.css`、`rows.css`、`user-message-appearance.ts`、`FigmaChatPanel.vue` 以及关联单测与工程 README 文档。

### How

- 修改 `globals.css` 中 `--ta-chat-user-bg`、`--ta-chat-other-user-bg`、`--ta-chat-answer-bg`、`--ta-chat-answer-border` (`transparent`) token 及 `user-message-appearance.ts` 渲染逻辑。
- 将 `parts.css`、`rows.css` 与 `FigmaChatPanel.vue` 中智能体输出气泡的边框规则调整为 `border: none`。
- 运行 Vitest 测试（`pnpm test`），确认单元测试全部通过。

### Result

- 对话界面三方均采用极简无边框设计，仅凭纯色彩体系（冰蓝、柔紫、柔灰/冷白）实现清晰、清爽且高质感的视觉区分。
- 未影响任何 API 契约、事件流、数据库结构、安全配置或后台服务。

### 2026-08-13 - 永久从用户手册排除游戏内容

### Why

- 用户明确要求游戏相关内容永远不得进入用户手册，不能只删除当前周更章节后依赖人工记忆。

### What

- 从每周新功能和功能总览中删除游戏入口、适用场景、配置、操作步骤与权限说明，并删除三张对应操作截图。
- 在用户手册内容边界中增加永久禁入规则；帮助中心测试扫描整本用户手册 Markdown，同时拦截中英文游戏关键词，防止后续在其它章节重新加入。
- 保留宠物问答、伙伴选择等非游戏说明，也不修改产品中的游戏实现或研发文档。

### How

- 审计 `frontend/apps/user-manual/docs/` 全部 Markdown 和周更图片资源，复用现有帮助中心测试覆盖手册事实源，没有新增独立校验链路。
- 执行帮助中心 Vitest、用户手册 VitePress 构建、agent-web 类型检查、运行页关键词扫描、截图引用与数量校验。

### Result

- 用户手册正文已无游戏内容；保留的每项周更工作功能仍有用户场景、使用前配置、操作步骤和至少一张截图，后续误加入游戏内容会被自动化测试阻断。
- 未变更 API、事件、数据库、性能、安全、兼容性、部署拓扑或环境配置。

### 2026-08-13 - 补齐自动化代码库工作空间点击入口

### Why

- 用户指出每周新功能只写了“找到自动化代码库分组”，没有说明入口位置和逐层点击方法，已有截图也未包含顶部工作空间按钮。

### What

- 在自动化代码库周更说明中增加“入口在哪”，写明顶部“应用 → 工作空间 → 版本”和文件树左下角双向箭头两个入口。
- 普通用户步骤细化到具体按钮和日期版本切换；管理员步骤细化到左下角设置、个人设置、版本库管理、应用关联、工作空间保存的逐层点击路径，并补充分组不显示时的排查方法。
- 用既有 Playwright 工作台场景重截完整页面，截图同时展示顶部入口、展开后的自动化代码库分组和左下角备用入口；帮助中心测试锁定关键入口文案。

### How

- 复用 `FigmaShell` 顶部工作空间选择、`WorkbenchFooter` 左下角双向箭头以及 `SettingsMenu`/现有管理面板的真实按钮文案，没有新增页面或入口。
- 执行自动化代码库相关 Playwright 场景、帮助中心 Vitest、VitePress 手册构建、agent-web 类型检查、图片引用和 `git diff --check`，并在运行中的 release 手册复核更新后正文。

### Result

- 用户现在可以直接按手册从工作台找到并进入自动化代码库，也能按管理员路径完成前置配置；未变更 API、事件、数据库、安全、性能或部署拓扑。

### 2026-08-13 - 每周新功能补充配置说明与操作截图

### Why

- 用户要求每周新功能除用户场景和操作步骤外，补充使用前配置，并加入可直接对照页面的操作截图，同时同步 release 与 dev 用户手册。

### What

- release 每周新功能的五项能力逐项补充普通用户与管理员配置边界，并加入平台体验、自动化工作空间、会话分享、资料多选和宠物游戏共 11 张脱敏截图。
- 帮助中心入口文案与问答上下文测试同步要求“使用前配置”，用户手册维护规范明确截图目录、真实组件状态和替代文本要求。

### How

- 复用项目既有 Playwright 工作台 mock 场景渲染真实前端组件并截图，截图生成后还原临时测试改动；执行帮助中心 Vitest、用户手册 VitePress 构建、agent-web 类型检查、图片引用检查和 `git diff --check`。
- 构建后的手册由 `127.0.0.1:3001/help/` 提供静态服务，并在应用内浏览器复核章节导航、配置正文、图片替代文本和页面布局。

### Result

- release 手册可按周查看新功能的适用场景、使用前配置、操作步骤、截图和权限边界；未变更 API、事件、数据库、安全策略或部署拓扑。
- 本地真实登录环境因 PostgreSQL `127.0.0.1:15432` 未启动而不可用，操作截图因此使用项目现有 E2E 数据渲染，不包含真实账号、仓库或客户数据。

### 2026-08-13 - 从 release 分支移除独立 Workflow 能力

### Why

- 用户确认独立 Workflow 能力永久下线，并要求所有长期分支同时删除代码、部署资产、测试和稳定文档；LobeHub 保持可选交付能力。

### What

- 删除 Python Workflow、Runner、分析任务工程，以及 Java capability/Redis 状态、前端路由与 workspace 包。
- 清理企业打包、Nginx、本地启动、API/事件/数据库/安全/模块文档中的 Workflow 契约；保留 release 当前业务改动和 LobeHub 资产。

### How

- 以 main 的清理提交作为删除清单，在 release 冲突中保留 release 最新非 Workflow 内容；同步修复部署测试夹具的完整 Flyway 资源装配，并恢复与排查授权相关、但被清理提交误带掉的会话所属校验。
- 执行静态残留扫描、前后端构建、定向测试和部署脚本门禁；LobeHub 资产单独盘点，确认未随 Workflow 删除。

### Result

- release 形成不含独立 Workflow、仍保留 72 个受控 LobeHub 资产的交付基线；未修改 `.env*`、generated SDK、OpenCode 只读源码或已执行 migration 字节。

### 2026-08-13 - 永久从用户手册排除游戏内容

### Why

- 用户明确要求游戏相关内容永远不得进入用户手册，不能只删除当前周更章节后依赖人工记忆。

### What

- 从每周新功能和功能总览中删除游戏入口、适用场景、配置、操作步骤与权限说明，并删除三张对应操作截图。
- 在用户手册内容边界中增加永久禁入规则；帮助中心测试扫描整本用户手册 Markdown，同时拦截中英文游戏关键词，防止后续在其它章节重新加入。
- 保留宠物问答、伙伴选择等非游戏说明，也不修改产品中的游戏实现或研发文档。

### How

- 审计 `frontend/apps/user-manual/docs/` 全部 Markdown 和周更图片资源，复用现有帮助中心测试覆盖手册事实源，没有新增独立校验链路。
- 执行帮助中心 Vitest、用户手册 VitePress 构建、agent-web 类型检查、运行页关键词扫描、截图引用与数量校验。

### Result

- 用户手册正文已无游戏内容；保留的每项周更工作功能仍有用户场景、使用前配置、操作步骤和至少一张截图，后续误加入游戏内容会被自动化测试阻断。
- 未变更 API、事件、数据库、性能、安全、兼容性、部署拓扑或环境配置。

### 2026-08-13 - 补齐自动化代码库工作空间点击入口

### Why

- 用户指出每周新功能只写了“找到自动化代码库分组”，没有说明入口位置和逐层点击方法，已有截图也未包含顶部工作空间按钮。

### What

- 在自动化代码库周更说明中增加“入口在哪”，写明顶部“应用 → 工作空间 → 版本”和文件树左下角双向箭头两个入口。
- 普通用户步骤细化到具体按钮和日期版本切换；管理员步骤细化到左下角设置、个人设置、版本库管理、应用关联、工作空间保存的逐层点击路径，并补充分组不显示时的排查方法。
- 用既有 Playwright 工作台场景重截完整页面，截图同时展示顶部入口、展开后的自动化代码库分组和左下角备用入口；帮助中心测试锁定关键入口文案。

### How

- 复用 `FigmaShell` 顶部工作空间选择、`WorkbenchFooter` 左下角双向箭头以及 `SettingsMenu`/现有管理面板的真实按钮文案，没有新增页面或入口。
- 执行自动化代码库相关 Playwright 场景、帮助中心 Vitest、VitePress 手册构建、agent-web 类型检查、图片引用和 `git diff --check`，并在运行中的 release 手册复核更新后正文。

### Result

- 用户现在可以直接按手册从工作台找到并进入自动化代码库，也能按管理员路径完成前置配置；未变更 API、事件、数据库、安全、性能或部署拓扑。

### 2026-08-13 - 每周新功能补充配置说明与操作截图

### Why

- 用户要求每周新功能除用户场景和操作步骤外，补充使用前配置，并加入可直接对照页面的操作截图，同时同步 release 与 dev 用户手册。

### What

- release 每周新功能的五项能力逐项补充普通用户与管理员配置边界，并加入平台体验、自动化工作空间、会话分享、资料多选和宠物游戏共 11 张脱敏截图；dev 额外补充长期记忆用户页与管理员配置页 2 张截图。
- 帮助中心入口文案与问答上下文测试同步要求“使用前配置”，用户手册维护规范明确截图目录、真实组件状态和替代文本要求。

### How

- 复用项目既有 Playwright 工作台 mock 场景渲染真实前端组件并截图，截图生成后还原临时测试改动；执行帮助中心 Vitest、用户手册 VitePress 构建、agent-web 类型检查、图片引用检查和 `git diff --check`。
- release 构建后的手册由 `127.0.0.1:3001/help/` 提供静态服务，并在应用内浏览器复核章节导航、配置正文、图片替代文本和页面布局；dev 同步执行帮助中心测试、手册构建和类型检查。

### Result

- release 与 dev 手册均可按周查看各自已开放新功能的适用场景、使用前配置、操作步骤、截图和权限边界；dev 保留独有的长期记忆说明，未变更 API、事件、数据库、安全策略或部署拓扑。
- 本地真实登录环境因 PostgreSQL `127.0.0.1:15432` 未启动而不可用，操作截图因此使用项目现有 E2E 数据渲染，不包含真实账号、仓库或客户数据。
- dev 长期记忆普通用户 Playwright 场景首次超时后重试通过；管理员完整场景在清空模型下拉的既有 `.el-select__clear` 定位处两次超时，专用截图路径在此前稳定状态通过。该问题不影响本次文档构建，但完整管理员场景未计为通过。

### 2026-08-13 - 按部署边界整合 release 到 dev

### Why

- 用户确认自动化代码库、宠物游戏和 E2E 数据等无需新增服务即可部署的能力保留在 release；Mem0、embedding 和本地 OpenCode client 等大功能先只进入 dev，并默认关闭。

### What

- dev 依次吸收 release 最新的自动化代码库、宠物随机难度和对话/工作空间 E2E 数据提交，并记录清理后的 release 为合并父节点。
- dev 保留既有 memory-service、embedding-service、本地客户端和 LobeHub 资产，独立 Workflow 继续保持删除状态。

### How

- 对三个 release 业务提交逐个 cherry-pick，以避免删除 Workflow 时的历史重命名检测误删 memory/embedding；确认树内容后使用 ours 合并记录 release 完整祖先关系。
- 通过精确残留扫描、分支祖先检查、默认开关检查、前后端构建和相关回归测试验证组合结果。

### Result

- release 可独立部署且无需启动 Mem0/embedding/client；dev 同时包含 release、Mem0 和 client 历史，LobeHub 与 memory 默认关闭。

### 2026-08-12 - 从主线移除独立 Workflow 能力

### Why

- 用户确认独立 Workflow/长程任务能力永久不再需要，并要求代码、部署、测试和稳定文档在所有分支中一并清理；LobeHub 仍需完整保留。

### What

- 删除根目录 Workflow、Runner、分析任务工程，Java capability/Redis 状态、前端长程任务路由与两个 workspace 包，同时移除本地启动、企业打包、Nginx、离线部署脚本及对应测试。
- 清理 HTTP/事件/数据库/安全/模块图、后端与前端 README/PACKAGE 中的旧契约；LobeHub 登录、模型网关、客户端、离线调度及其上游 `/api/workflows/*` 禁用规则保持不变。

### How

- JDK 25 下完成后端打包和定向测试；完成前端全量测试、类型检查、生产构建与预览冒烟，并执行开发脚本、内部 Nginx、增量包、双后台完整包和 AI 文档门禁。
- 全量 Maven 测试仅在本机 Docker 的 `mysql:8.4` Testcontainers 三次启动后无法建立 JDBC 连接处失败；以无测试全量打包和相关模块定向测试补充验证。

### Result

- 形成不含独立 Workflow、仍保留 LobeHub 的清理基线；未新增或修改平台数据库/Flyway/MyBatis SQL、generated SDK、OpenCode 源码或 `.env*`。

### 2026-08-08 - 优化超级管理员内部模型调用可观测页面布局与精细化调整

### Why

- 超级管理员反馈内部模型调用可观测页面进一步优化需求：1. `REQ & SR Trend` 折线图与旁边饼图/柱状图排版挤压，要求折线图单独全宽独立整行展示；2. 删除页首文案中的“；每个指标悬浮提示中的计算逻辑与判定保持不变。”；3. `By Provider` 替换为中文“按供应商”，且移除底部“按小时明细”数据表格；4. 页首“AIPerf & 业界指标英文缩写指南 (Metrics Glossary)”改为默认展开。

### What

- `REQ & SR Trend` 折线图卡片设为全宽独立整行展示（`grid-column: 1 / -1`），并提升容器高度，避免由于三栏排版导致折线图被挤压、图例与数值重叠。
- 删除了页首子标题提示文案中的“；每个指标悬浮提示中的计算逻辑与判定保持不变。”。
- 将“By Provider”替换为中文“按供应商”，彻底删除了页面底部的“按小时明细”表格及关联未使用的 `groupedHourlyStats` 计算属性。
- 页首“AIPerf & 业界指标英文缩写指南 (Metrics Glossary)”折叠卡片的 `showGlossary` 默认状态调整为 `true`（默认展开）。
- 同步更新 Vue 组件单元测试 `internal-model-observability-panel.test.ts`。

### How

- 修改 `InternalModelObservabilityPanel.vue` 模板与 CSS 样式，完成 `npx vitest` jsdom 测试与 `npx vue-tsc --noEmit` 类型校验。

### Result

- 前端单测和 `vue-tsc` 类型检查 100% 通过，界面折线图独占整行宽敞展示，各卡片布局与中文字样准确，默认展开 Glossary 手册。



### 2026-08-07 - 修复内部模型可观测首输出判定与探活超时归类

### Why

- 复核首 token 与流式超时指标后发现，SSE 注释/空事件会持续重置首事件超时，role-only 元数据块会被误计为首 token；首 token 标记与时间戳分开发布还存在并发窗口。
- 探活响应头已返回但正文读取超时时，原逻辑丢失 HTTP 状态并把底层 Netty/JDK 超时归为未知，无法区分上游首响应慢与首字节后的流式空闲。

### What

- 将首输出定义收敛为包含 `content`、`reasoning_content` 或工具输出字段的 SSE data；首个输出截止时间改为绝对 deadline，注释/空事件不能延后，首 token 时间戳使用原子占位值一次性发布。
- 扩展阻塞/Netty 超时识别，探活保存响应头后再读取正文，正文超时保留 HTTP 状态并归类为 `UPSTREAM_STREAM_IDLE_TIMEOUT`；同步前端、API、数据库和排障文档中的指标口径。

### How

- 新增 SSE 注释超时、role-only chunk、阻塞/Netty 超时和响应头后正文超时回归用例；使用 JDK 21 执行 API/runtime/persistence 定向 Maven 测试，并执行 agent-web typecheck 与 `git diff --check`。

### Result

- 43 项后端定向测试、agent-web 类型检查和差异检查通过；未修改 API/事件线格式或既有 migration 字节，未执行真实 PostgreSQL 基线升级或长驻应用启动验证。

### 2026-08-07 - 修正内部模型可观测首 token 与聚合口径

### Why

- 原实现把响应头到达时间 `firstByteMillis` 当作首 token，空/截断 SSE 在 2xx 正常 EOF 时可能记为成功；前端还用小时最大值展开伪造平均/P90/P95，探活数据也会混入业务统计。

### What

- 新增 `first_token_ms` 明细及小时 `sum/max/count` 聚合；代理只把首个非空且非 `[DONE]` 的 SSE data 记为首 token，并要求流同时有有效 chunk 和 `[DONE]` 才成功，空/缺少完成标记记为 `UPSTREAM_STREAM_INTERRUPTED`。
- 修正探活首字节取样位置，统计 API 支持 `source=USER_CALL|PROBE`，页面默认隔离探活；移除无法由小时聚合还原的 P90/P95/分布，改用准确平均/最大值。

### How

- 复用现有 `CallObservation`、MyBatis XML upsert 和小时聚合结构，增加 Flyway `V20260807203000__add_internal_model_first_token_metrics.sql`，补充 proxy 空流/缺 `[DONE]`、探活正文延迟、source 过滤与聚合断言；同步 API、数据库、模块、测试与排障文档。
- 使用 JDK 21 执行后端定向 Maven 测试（代理/探活/API/持久化）与 agent-web `vue-tsc` 类型检查，并检查主 release 工作区保持干净。

### Result

- 观测明细现在能区分响应头、首 token、空/截断流；小时统计不再把最大值冒充分布或分位数，用户调用指标默认不受探活污染。旧明细首 token 为空、旧小时行首 token 计数为 0，接口新增字段保持可选兼容。

### 2026-08-07 - 企业内部模型 API 调用可观测性

### Why

- 企业内部部署后 opencode/codex 调用企业模型端点总是失败，排查只能人工 grep manager 日志 + 手工 curl 代理端点（`EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md` 第 9 节），没有任何自动化调用记录、统计或主动探活。本次在 Java 内部模型代理链路插桩，落地调用可观测性。

### What

- 内部模型代理转发链路（opencode → `/internal-model-proxy/v1/**` → 企业端点）每次调用落结构化明细 `internal_model_call_records` 并小时聚合 `internal_model_call_stats_hourly`；`InternalModelCallOutcome` 按连接失败/三种超时/HTTP 错误/流中断/鉴权失败等 13 类分类，只记 traceId、耗时、状态码与异常类简名，不存请求/响应正文、Token 或密钥。
- 探活服务 `InternalModelProviderProbeService` 每 5 分钟对启用 provider 发 `max_tokens=1` 最小 chat 探测并维护逐 provider 探活状态（连续失败计数由 SQL 原子维护）；超管 API 提供明细分页、小时聚合、探活状态与手动触发探活。
- 前端系统管理新增「内部模型可观测」页：探活状态卡片 + 调用记录/聚合统计两个 tab。

### How

- 插桩在 `InternalModelProxyForwardingService`（`test-agent-api`）的响应式链路上：`validateAndExtractModel` 提取 model、首字节/首事件标记、responses 补偿分支 stream outcome、`Mono.firstWithSignal` 结果挂 `doOnError/doOnCancel/doFinally` 终态归类；观测绝不影响主链路（recorder 内 onErrorResume + doFinally try-catch 双保险）。`InternalModelCallRecorder`/`Classifier`/探活/查询服务放 `test-agent-opencode-runtime` 新包 `internalmodel.observability`，SQL 走 `test-agent-persistence` MyBatis XML 三段式。
- Flyway `V20260807130134` 建三张表；XXL `V10/V11` 注册探活与清理任务；四模块 `clean test` 共 1531 项通过，前端全仓 `typecheck` 通过。

### Result

- 排查从人工 grep 变为超管页面一键查询：先看调用记录按 `traceId/outcome` 定位失败分类，再按分类进对应层；页面「全部探活」可主动确认端点可达性。已同步 `docs/api/http-api.md`、`docs/deployment/database.md`、`docs/architecture/xxl-job-integration.md`、`docs/testing/xxl-job-integration.md`、opencode-runtime/persistence README 与 troubleshooting 引导。
- 提交未包含主工作区其他会话的 marketing 长图/pptx 未提交改动；`components.d.ts` 仅含本页触发的组件自动注册。后端新 SQL 均走 MyBatis XML，未改 generated SDK/OpenCode 源码/`.env*`。

### 2026-08-07 - 润色 MIMO 长图与邀请邮件文案

### Why

- 用户确认现有信息结构和事实亮点无需调整，希望将标题和共建、共享、扩展相关表述从“产品说明书/技术方案”改得更自然，减少“直接、复用、共建、引用、接入”等连续堆叠；同时要求开头的 Agent Team 表达更像真实工作方式。

### What

- 保留 Light 长图原有版式、公共 Agent、应用 Agent、接口与 UI 执行、4 小时以上长任务、50 个 JSP 入口菜单调用链路、已有对话定时等事实，只润色六个一级标题、共建/共享卡片标题和结尾 Banner。
- 将共建页改为“通用能力沉淀复用，应用能力按场景共建”，用“从真实业务场景出发”“将成熟方法封装为可执行能力”“共建成果持续沉淀，后续任务可直接复用”说明工作方式；共享页改为“一次沉淀、多处复用，共建成果跨项目共享”；扩展页改为“能力模块按需扩展，新测试场景快速落地”。
- 将开头的 Agent Team 改为“先由设计分析、案例生成和 Review 三个 Agent 协同完成测试设计”，邮件同步改为同一套自然表述，未改变信息结构和亮点内容。
- 根据后续反馈，进一步将首屏改为“测试人员可在真实项目工作区中调用现有 Agent，先完成设计分析、案例生成和 Review”，让主语和动作更自然，不再把工作区与 Agent 协同方式硬接。
- 根据后续反馈，将 Review 统一改为“案例审核”，并用“公共 Agent 与应用 Agent 按任务组队”“按任务组建测试设计 Agent Team”说明通用能力、应用场景与测试设计协同关系；同时将“长任务稳定执行”改为“长任务可持续处理，单次执行超过 4 小时”，与其他能力标题保持同一表达节奏。
- 根据最新反馈，将公共/应用 Agent 的关系进一步表达为“按需组建 Agent Team”，将各基地产品部作为共享去向；重构长任务区为“长任务可跨时段持续推进，约定时间自动接续”，合并 4 小时与 50 个 JSP 为一个真实验证卡片，并新增定时接续、已有对话上下文和过程追溯三个配套卡片。
- 根据版面反馈，将一级标题进一步收束为“公共与应用 Agent，按需组建 Agent Team”，避免 Agent 词组重复和英文断行；长任务区保留四卡结构，任务接续排在第二位，不再额外强调，删除底部四个小指标条。
- 根据最新版面要求，将长任务区四项能力改为同一行的四列卡片，卡片内部统一按“编号—能力图形—标题—说明”竖向排列，并将卡片间距收紧，保持第二项为普通能力卡。
- 根据后续版面反馈，继续压缩各章节固定高度，将 03—06 区块及收尾区的尾部留白控制在约 30–70px，画布由 8230px 收束为 7330px；保持章节标题、能力内容和既有顺序不变。
- 根据邮件补充要求，在工具简介中明确“MIMO 测试智能体工作台基于灵犀Code通用智能体底座建设”，并将 SkillHub 表述调整为“可快速复用 SkillHub 既有 Skill”。
- 根据邮件重写稿，将工具介绍调整为“专业 Agent Team、测试执行（接口与 UI）、应用代码白盒分析、晚间任务与长程任务”的工作视角；核心亮点收束为测试 Agent Team 与长任务定时接续，并将 JSP、SLB 等实践表述为可复用的应用能力。

### How

- 使用 Canvas Design 的 Light 视觉约束重新渲染 PNG，并对首屏、公共能力、共建、共享、扩展和收尾区域进行原尺寸裁切检查；针对共建圆形关系图和右侧标题增加排版断行，避免单字孤行。
- 使用无头浏览器检查画布横向宽度与页面一致，使用 FFmpeg 完整解码 PNG，并用文档校验脚本和 `git diff --check` 检查输出。

### Result

- 输出仍为 1500×8230 Light 长图，版式和既有能力亮点保持不变，文案更偏内部成果介绍；邮件结构和字数保持稳定，仅同步表达方式。
- 本次仅更新宣传图片、邮件文案和会话记录，不修改运行时代码、HTTP API、RunEvent、数据库/Flyway、关系型 SQL、安全配置、generated SDK、OpenCode 只读源码或 `.env*`。

### 2026-08-06 - 合并 Skill Hub 事项分类与公共快照持久化

### Why

- 用户要求将 `codex/skillhub-taxonomy` 已完成的事项分类、公共内容持久化和十分钟 Git 对账改造合入当前企业
  release 分支，并检查其它内容与分支合并状态。

### What

- 以非快进合并纳入三个功能提交：Skill Hub 增加 `WORKER/TEST/CODE/OTHER` 具体事项分类及超级管理员治理，公共
  Agent/Skill 快照、修订和压缩正文改为数据库持久化，公共 Git 默认对账间隔由 30 秒调整为 10 分钟。
- 合并唯一冲突位于本机 session log；完整保留 release 侧近期部署、角色和弹框修复记录以及功能分支的三条
  Skill Hub 记录，没有改写三份已执行 Flyway migration，也没有纳入无关营销素材。

### How

- JDK 25 下运行 Hub 应用服务、Controller 和 MyBatis 定向测试共 21 项通过；前端 Hub/backend-api 113 项通过；
  `test-agent-app -am package -DskipTests`、用户手册与 agent-web production build 均成功。
- 三份 migration SHA-256 分别为 `f59f641527fdabaf21393319cd70ed578c6f75a55decae4d8839bc2b561ac06d`、
  `1b2547cf466c09fe11a63b1f76e5e17ec1773e2187aa01e052288a9bb4861e75`、
  `19a0e5af5f361179ac3887d541c274f75f43f89a683ee8037a5e0391444a92bf`，与功能分支已验证字节一致。
- 按固定 JDK 25、`.env.test` 和 `test` profile 执行标准重启；脚本在停止服务前因缺少
  `WORKFLOW_DEV_REDIS_PASSWORD` 失败。未修改环境文件，8080/3000 仍由功能 worktree 的既有实例提供 `UP/200`，
  不能作为合并后 release 工作区的运行验证。

### Result

- 合并候选已通过定向测试和生产构建，包含 HTTP 分类响应/写接口、Flyway/MyBatis SQL、超级管理员权限与查询链路
  性能改造；不涉及 RunEvent、generated SDK、OpenCode 上游源码、跨服务器文件路由或环境配置。
- 页面“刷新目录”仍只读取数据库；外部 clone 直接 push 由启动后首次对账及默认十分钟周期兜底，当前没有显式的
  公共 Git 即时对账接口。合并后 release 三服务运行验证仍受本地工作流密钥缺项阻塞。

### 2026-08-06 - 生成 MIMO 产品介绍长图与共建邀请邮件

### Why

- 用户希望参考深色科技长图的叙事方式，为当前项目制作独立的产品介绍长图，并突出后续能力共建、共享和可扩展性；同时要求找到此前生成的动图并拟定配套邮件。
- 邮件可承载约 50 MB 素材，因此优先保证真实界面和中文文案清晰，不用过度压缩换取更小体积。

### What

- 新增 1500×8500 深色高清产品长图，按“持续推进、独立工作空间、子智能体协同、后台与夜间运行、能力资产化、共建共享扩展”组织叙事，并使用既有真实工作台录屏帧和 Hub 动态演示画面作为产品证据。
- 根据后续反馈新增 1500×8230 Light 版长图，并再次从测试人员工作视角重构：产品名称统一为“MIMO 测试智能体工作台”，前半段直接展示由设计分析、案例生成、Review 三个 Agent 组成 Agent Team 的测试设计能力，以及支持接口与 UI 的测试执行 Agent；同时展示 JSP 调用分析、SLB 排查、案例增量生成、异步文件处理 4 个应用场景 Agent 和 12 项已实现公共测试 Skill。后半段说明长任务、定时执行、场景共建、跨项目复用和 Agent/Skill/MCP/Tool/Docs 扩展路径。按用户批注明确不展示 SEAS 自动化未达标分析、DCM 问题提交、模拟问题排查和投产验证点检查。
- 新增“星轨共生”视觉哲学说明和邮件成稿；邮件区分当前已具备能力与后续场景共建方向，并保留正式访问地址、手册链接和联系人占位符，避免编造发布信息。
- Light 版视觉哲学由“开放蓝图”调整为“联动工场”，用“先运行、再生长”的信息顺序证明现有能力不是空壳；共建共享不再描述自建 Skill Hub，只保留“快速复用 SkillHub 既有 Skill”，并明确来源、版本和跨项目复用关系。
- 根据用户补充的组织身份，将 Light 长图品牌位、结尾邀请语和页脚统一为“杭州产品部”发起；邮件保持约 840 字符，工具介绍和三项亮点均以测试人员实际工作为主语，邀请对象统一为“各产品部”。
- 根据用户对标题可理解性和真实能力边界的反馈，将 01—06 及收尾标题全部改为“能力 + 直接收益”的直述表达；删除错误的“测试设计到执行全流程闭环”说法和流程箭头，改为“项目资料集中管理、多类测试任务分别开展”。按最新确认口径，测试执行 Agent 支持接口与 UI；测试设计明确为设计分析、案例生成、Review 三个 Agent 组成 Agent Team 协作。将“复杂任务不中断”替换为可验证的长任务能力：单次执行超过 4 小时，深度分析 50 个 JSP 入口菜单调用链路并完成复核；补充已有对话可在白天确认目标、晚上按约定时间定时执行。邮件工具简介和三项核心亮点同步更新。
- 找到并保留此前的 `docs/assets/marketing/ice-blue/00-capabilities-long-demo.gif`（720×2842、约 3.6 MB）及源录屏 `source-workbench-no-pet.gif`；本轮不改写已有动图。

### How

- 对照当前用户手册目录映射中的 `implementation: implemented` 标记、功能总览和前端工程说明核对公共 Agent/subagent/Skill 的真实数量与职责；使用 Canvas Design 的“联动工场”视觉体系完成排版，并逐段放大检查公共能力、测试任务链、共建复用和扩展区域。
- 通过无头 Chrome 渲染最终 PNG，使用 `file`、`sips` 和 FFmpeg 解码核对尺寸、格式与可读性；最终 PNG 约 6.8 MB，与既有动态演示合计约 10.4 MB，低于 50 MB 邮件素材预算。
- Light 版最终文件为 1500×8230、约 5.93 MiB；FFmpeg 完整解码通过，标题区、能力区、长任务与定时区、共建共享扩展区和收尾区原尺寸裁切目检通过，无头浏览器检查页面横向宽度与画布一致，未发现文字横向越界。

### Result

- 深色版与以“公共能力直接用、专业能力一起建”为主线的 Light 版长图、两套视觉哲学和邮件文案均已生成并完成视觉检查；对外发送建议优先使用 Light 版，正式发送前只需补充访问地址、手册链接和支持联系人。
- 本次仅新增宣传图片和文案，不修改运行时代码、HTTP API、RunEvent、数据库/Flyway、关系型 SQL、安全配置、generated SDK、OpenCode 只读源码或 `.env*`。

### 2026-08-03 - 以今早企业基线收紧 release 可选能力与 Flyway 门禁

### Why

- 用户确认今天早上企业现网部署包的精确源码提交为 `0352efa987219b9dde5c09e77b1eabfa719fc068`，并要求基于当前 release 重打双后台、前端和公共 Agent 配置交付物。
- 当前分支已经合入 Workflow、LobeHub 和后续公共配置 rollout 迁移，但本次 release 明确暂不启用前两项；其中 LobeHub 主 migration 版本低于现网已部署最高版本，不能直接依赖默认排序补跑。

### What

- 企业 release 默认构建关闭 Workflow/LobeHub：两项改为显式 opt-in，内层组件清单写入 `disabled`，不携带对应运行制品；前端构建期入口、登录回跳和深链接失败关闭，外层前端节点把 Workflow upstream 规范化为空并由 Nginx 返回显式 503。
- 打包程序同时校验 toolbox、LobeHub 主/前向兼容、公共配置 rollout 和 rollout 后 LobeHub 兼容五个 migration 在 persistence JAR 内的固定路径与 SHA-256；命名测试新增 rollout migration 不可变字节锁。
- 真实 PostgreSQL 集成测试按 `0352efa...` 的完整主 migration 上界 `V20260801104000` 构造现网历史，验证升级只选择 `V20260802173416` 前向兼容迁移，再执行 `V20260803133000`，不补跑低版本主 migration，也不启用 `outOfOrder`。
- 同步企业内层/双后台手册、数据库与前端部署文档、前端工程说明和脚本夹具；公共配置独立仓库仍为干净提交 `8b81dc4`，本次继续交付既有完整白名单替换包，排除其受跟踪的会话和 OAuth 运行态文件。

### How

- JDK 25 下运行 `FlywayMigrationNamingTest` 7 项和 `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 真实 PostgreSQL 7 项全部通过。
- 前端 typecheck 通过；发布开关与登录回跳 Vitest 7 项、超级管理员入口 Chromium 1 项通过；企业增量组件和固定名双后台封包 verifier 均通过，修改脚本 Bash 语法与 `git diff --check` 通过。
- 以代码提交 `d907d4f72` 执行 `package-release.sh --include-all-components`，全量重建 backend、frontend、programs、`linux/amd64` worker、IT-Tools、OmniTools 和 toolbox；worker 指纹为 `bf7b8e1d7c4e996c815a4c7dcf5ec163fe70707be385e0467cfa731170a0639a`，toolbox 指纹为 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`，两项均为 `included`，Workflow/LobeHub 均为 `disabled`。
- 内层 backend/frontend `--validate-only`、外层五项 Flyway JAR 字节门禁、内外层 SHA 一致性和前端节点空 Workflow upstream 校验通过；Mac 上 worker 的 Python/OpenCode/Codex MCP 契约通过，原生 sandbox E2E 按设计留给企业 `linux/amd64` 两台后台逐机执行。
- 公共 Agent 完整替换包继续使用企业集成底座叠加独立仓库提交 `8b81dc4`，其中 UI 执行 Agent/Tool 三个运行文件与该提交逐字节一致；包内 7 个 Agent、15 个 Skill、7 个 TypeScript Tool，无 Git、依赖目录、会话或 OAuth 运行态路径，SHA-256 为 `a29f0d3a4a49bad3476f8bb5bb9cf99616365569bbb9ad3cb8a6220537b7636d`。
- 提交前已回顾全部 `.agents/session-log*.md` 近期条目并确认无冲突标记；没有修改 `.env.local`、generated SDK 或 OpenCode 只读源码。

### Result

- 当前代码与打包门禁已经限定本次 release 不启用 Workflow/LobeHub，并覆盖今早精确企业基线的已知 Flyway 升级路径；全量内外层介质已经完成构建和预校验，本条追溯记录提交后只复用已验证二进制执行 `--zip-only` 重封，再重建外层包，最终摘要以交付目录配套 `.sha256` 为准。
- 本次未新增 HTTP/RunEvent wire；数据库 SQL 本身来自当前分支既有提交，本轮只新增不可变校验和精确基线升级测试。现场仍必须在停机前取得完整 `flyway_schema_history`；发现失败记录、未知 checksum、未知更高版本或分叉时停止发布，禁止 `repair`、`outOfOrder` 和手工改历史表。

### 2026-08-03 - 补充 OpenCode 进程分配冲突现场排查

### Why

- 现场连续出现 `OPENCODE_UNAVAILABLE: TestAgent 进程分配已变化，拒绝旧启动结果回写`，同时运行管理页偶发搜索不到用户、清缓存后恢复；现场不能使用 `rg` 或 `jd`，需要保留一套仅依赖基础系统工具的可执行排查口径。

### What

- 在后端部署文档新增该 CAS 冲突的历史 SQL、当前快照边界、步骤/耗时判读，以及基于 `journalctl`、`docker logs`、`grep`、`sed`、`find`、`curl` 和浏览器 Network 的采证流程。
- 数据库文档明确初始化操作行可在清缓存后继续查询，但进程与 binding 只保存当前快照；企业多后台手册增加统一入口，不复制完整流程。

### How

- 对照公共启动条件更新、强/弱状态查询、自动恢复、manager heartbeat、运行管理查询和 MyBatis CAS 字段确认排查结论；使用 `tools/verify-ai-docs.sh`、Markdown 链接检查和 `git diff --check` 校验文档。

### Result

- 现场无需 `rg`、`jd` 或 JSON 专用工具即可按 traceId、统一认证号和时间窗区分显式初始化、当前进程快照、自动恢复及管理命令；未修改代码、API、事件、数据库结构、Flyway、环境配置、generated SDK 或 OpenCode 源码。

### 2026-08-01 - 基于最新改造重打企业三节点与公共配置交付包

### Why

- 用户要求针对最近合入的动态 UI 执行地址、工作台界面和公共 UI 执行 Agent/Tool 改造，重新生成企业双后台、前端和公共 Agent 配置交付物，并给出企业内再次部署与验收口径。
- 上一版企业包早于 `V20260801093854`、`V20260801104000` 两条通用参数 migration 和公共配置提交 `8b81dc4`；继续使用旧包会缺少 `UITEST_BASE_URL` 数据库配置及 UI 执行 Tool 的动态读取链路。

### What

- 从主仓库代码基线 `a4679524b1b6803561b95a60d976cae690faeca8` 全量重建平台内层 ZIP，并用上一批已验证的 `.4/.114/.2` 敏感节点配置重新封装固定名三节点外层 ZIP；worker runtime 与 toolbox 指纹未变化，组件清单继续为 `reuse`。
- 以既有企业完整公共配置为底座，叠加已推送公共配置 `8b81dc4` 的 UI 执行 Agent/Tool 变更，保留白盒 Agent、`code_analysis` MCP、skill-creator、skill-optimizer 和工作区 Git Tool；固定名公共配置包包含 7 个 Agent、15 个 Skill、7 个 TypeScript Tool。
- 公共配置 Git 还跟踪顶层 `agents/**` 运行态会话和 OAuth access/refresh profile；本次交付按 `README.md + .gitignore + opencode/**` 白名单归档，明确排除这些运行态与认证文件。该独立仓库中的既有凭据和历史应另行清理、轮换，本次未修改或推送外部仓库。

### How

- 使用 JDK 25 和真实 PostgreSQL Testcontainers 执行 Flyway 命名/不可变字节、通用参数 seed、已知历史兼容升级及 MyBatis PostgreSQL 集成测试，共 18 项通过；前端 typecheck、用户手册与 Vite 生产构建通过。
- 执行企业 ZIP 元数据、增量组件、双后台完整包、自动节点部署、多后台节点、Nginx 和 AI 文档 7 组验证脚本；后端和前端部署脚本分别以 `--validate-only` 解压并验证当前内层包。
- 逐字节核对内层 persistence JAR 中 `V20260728160800`、`V20260731115520`、`V20260731123600`、`V20260801093854`、`V20260801104000` 与源码一致，确认外层嵌入的内层 ZIP 完全相同、应用 JAR 保留 RSA 私钥、公共包 CRC/禁带路径/敏感特征扫描通过；再以 OpenCode 1.18.4 实际启动并查询 Agent、Skill 和 Tool 清单，包内 7/15/7 项全部加载成功。

### Result

- 本机平台构建、真实 PostgreSQL 已知历史升级和离线部署脚本校验通过；最终平台 ZIP 在本条记录提交后重新封装并以交付目录中的 `.sha256` 为唯一校验值。公共配置包 SHA-256 为 `a29f0d3a4a49bad3476f8bb5bb9cf99616365569bbb9ad3cb8a6220537b7636d`。
- 当前包新增/包含数据库 Flyway 变更和内部窄字段配置 API，但不变更 RunEvent/SSE、generated SDK、OpenCode 上游源码或 `.env.local`；worker/manager、toolbox 和独立 Python 组件无需随本次增量包重装。
- 企业目标 PostgreSQL 的完整 `flyway_schema_history` 尚未取得，交付只完成本机构建与已知历史验证；现场必须先由 DBA 对照全量 history，任一失败、未知 checksum、未知更高版本或分叉都停止发布，禁止 `repair`、`outOfOrder` 或手改历史表。

### 2026-08-01 - 审计并合并工作台界面与独立 UI 执行能力

### Why

- 用户要求在合入主线前确认本轮没有改动 OpenCode 对话相关逻辑，同时明确小宠物默认收起、工作台界面调整和独立 UI 执行能力均属于应合入范围。

### What

- 将工作台工行配色、悬浮三栏、顶栏应用/工作空间/版本选择、手册选中态、小宠物默认收起，以及独立 UI 执行控制器、客户端、短效令牌和 OpenCode 进程环境注入一并纳入主线候选。
- 逐路径核对 `FigmaChatPanel.vue`、`useSideQuestionRun.ts` 和 `frontend/packages/agent-chat/**` 均无改动；未修改消息发送、Session/Run、RunEvent、Question/Permission 或对话状态归并逻辑，也未修改 `opencode-source` 快照。

### How

- 对完整合并差异执行文件清单、对话关键词、OpenCode 源码边界和冲突标记检查；保留主线与功能分支各自已有的会话记录。
- 运行 `FigmaShell.test.ts` 54 个组件用例、agent-web typecheck/生产构建与用户手册构建；使用 JDK 25 运行 UI 执行控制器、鉴权过滤器、客户端、短效令牌和 OpenCode 启动环境注入相关 38 个后端用例，并执行 AI 文档校验。
- 纳入并发提交的桌面回归收口后，再运行全量 Vitest（106 个文件，1742 通过、1 跳过）、全 workspace typecheck、生产构建和 131 项桌面 Chromium 回归；其中首项首次超时后按配置重试通过，随后单独复跑 1/1 通过。

### Result

- 合并候选的前后端定向测试、类型检查、生产构建和文档校验均通过；OpenCode 相关代码变化仅限独立 UI 执行所需的进程环境变量注入，不涉及对话链路。
- 并发提交只增加主面板可收缩约束、测试/桌面回归稳定性和 Playwright 产物忽略规则；`agent-chat` 仅调整测试等待方式，未修改其生产源码。
- 新增的是独立 UI 执行 HTTP 接口与短效鉴权能力；未变更 RunEvent、数据库/Flyway、关系型 SQL、generated SDK、OpenCode 源码或 `.env.local`。

### 2026-07-31 - 调整工作台顶栏上下文布局与默认版本

### Why

- 用户要求手册打开时保持红色选中态，顶栏所有元素在首行与面板间隔组成的视觉带内上下居中；应用、工作空间、版本三个按钮要位于左右邻近元素之间的正中，并在选定工作空间后默认唯一版本或最新版本。
- 用户同时反馈顶部和左下角版本切换都慢，需要区分新增 UI 开销、既有切换链路与本机性能压力。

### What

- `FigmaShell` 顶栏改为三列网格：Logo 左对齐，中间三个白底细框上下文按钮在 Logo 末端与右侧工具组起点之间保持左右等距，书本手册、透明底细框运行态摘要和单字头像依次固定在右侧；三组统一以 44px 视觉带的 `y=22px` 为中心线。
- 手册问号替换为书本线框图标，并接入 `helpCenterOpen`，弹框打开期间保持柔红底、深红图标和 3px 工行红定位标记。
- 顶部工作空间/版本直接复用 `appTemplatesWithVersions`、`handleLoadVersions` 和 `handleSelectVersion`；选定工作空间后，单版本直接选择该项，多版本复用后端 `version desc, updated_at desc` 的首项。左下角入口继续保留，对话逻辑未改。
- 同步工程 README、模块图、前端规范、包说明、首次引导和用户手册，明确顶部与左下角入口关系、默认版本和书本手册图标。

### How

- 组件测试覆盖右侧顺序、手册持续选中态、三列等距 CSS、单版本懒加载和多版本最新项；类型检查继续复用现有前端类型，没有新增协议或依赖。
- 浏览器实测中间组左右留白均为 `188.7265625px`、差值为 `0`，Logo/中间组/右侧组中心线均为 `y=22px`；选择“本地-测试”后顶部默认版本约 `0.37s` 显示，实际工作区和文件树约 `8.1s` 完成。
- 性能采样时 10 核机器负载均值约 `15.07/16.19/18.02`、CPU 仅 `4.48%` 空闲、物理内存仅余约 `99MB` 且压缩内存约 `7.7GB`；版本切换本身还串行复用 Git 权限校验、默认个人工作区准备、工作区读取、最近记录和目录加载，因此两处入口都会受同一链路和本机负载影响。

### Result

- `FigmaShell.test.ts` 54/54、agent-web typecheck 和用户手册 VitePress build 通过；`http://127.0.0.1:3002/` 真实工作台已验证默认版本、实际文件树切换、等距/居中和手册红色打开态。
- 不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、鉴权、安全配置、generated SDK、OpenCode 源码或 `.env*`。未对既有版本切换后台链路做性能重构；本轮仅诊断并改善顶部即时反馈。

### 2026-07-31 - 工作台进入页面默认收起小宠物

### Why

- 页面挂载时会恢复已保存的固定宠物为可见状态，导致用户每次进入工作台都直接看到展开的小宠物。

### What

- `FigmaShell` 挂载时保留固定和位置偏好，但不再将宠物状态直接恢复为 `idle`；每次进入页面从收起态开始，用户手动唤起后仍可恢复固定状态和保存位置。
- 增加固定偏好已保存时的组件回归，并同步 agent-web README 与 `src/PACKAGE.md` 的行为说明。

### How

- 复用既有 `loadRobotFixed`、`loadSavedRobotPosition` 和 `toggleRobotVisibility` 链路，只移除挂载阶段的可见状态恢复分支。
- 运行 `FigmaShell` 新增用例、agent-web typecheck、生产 build，并启动 `corepack pnpm --filter @test-agent/agent-web dev -- --host 127.0.0.1 --port 3001` 做 HTTP smoke。

### Result

- 新增行为用例通过；typecheck、生产构建和 `http://127.0.0.1:3001/` 返回 200。
- 完整 `FigmaShell` 套件期间工作区另有并发 ICBC 配色改动反复更新 `globals.css` 与其源码断言，出现 1 个无关配色断言失败；宠物行为用例单独通过。未修改 API、事件、数据库、安全配置、generated SDK 或 OpenCode 源码。

### 2026-07-30 - 基于最新主线重建三节点企业交付

### Why

- 上次企业包后又合入应用 Agent 实时权限/模式刷新和官方 Codex MCP 原生接口；前者涉及 Java 与前端，后者改变 programs/worker，旧包不能继续代表当前主线。

### What

- 基于 `c3f463f3e` 重新构建后端 JAR、前端、programs、`linux/amd64` worker、内层发布 ZIP 和三节点固定外层 ZIP；组件清单为 `worker runtime=included`、`toolbox=reuse`。
- 独立 Python 3.13 第三方库包和公共 Agent/Skill 完整替换包源码未变化，沿用现有固定名产物并用新 worker/当前白盒 Agent 再验证。

### How

- 后端封包、前端手册/typecheck/生产构建、Codex 官方 `codex/codex-reply` 契约、企业 Responses 路由、Python、归档卫生、组件指纹、三节点结构和 AI 文档校验通过；Mac arm64 按设计跳过 amd64 原生 namespace，要求两台企业 Linux 逐机补跑宿主白盒检查。
- 最终发布 ZIP 的 Flyway persistence JAR migration 字节仍为固定 SHA-256 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`；相对上次交付没有新增、删除或改写 migration。

### Result

- `.4/.114` 都必须更新 Java、programs 和 worker，并保持现有 toolbox；两台后台全部通过后再更新 `.2` 前端，最后发布公共 Agent/Skill 配置。
- 本地构建与包级验证完成，企业真实部署尚未执行；未修改 `.env*`、generated SDK 或 OpenCode 源码快照。

### 2026-07-30 - 基于附件指纹新提交重打企业增量包

### Why

- Python 干净归档生成后，主线又合入 `296887a0d`，其运行时变更仅位于前端附件 SHA-256 兼容链路，旧企业平台包不包含该提交。

### What

- 在归档修复提交 `e432a5514` 上重新构建发布后端 JAR和最新前端，并用既有三节点敏感配置重打固定名 `test-agent-internal-release.zip` 与 `test-agent-two-backend-complete.zip`。
- 组件计划及最终清单均为 `worker runtime=reuse`、`toolbox=reuse`；独立 Python 包仍沿用同批次干净归档，不进入平台 ZIP。

### How

- JDK 25 后端封包、前端手册/类型检查/生产构建、Flyway persistence JAR 字节门禁、后端和前端 `--validate-only`、外层 ZIP 结构/成员/校验和及 macOS 元数据扫描全部通过。
- 内层发布 SHA-256 为 `5ee0e8c6ffeaf2fe3da419cf2d1fe4fdb3e33e53b23235d5f972563181fee166`，固定外层 ZIP SHA-256 为 `2c1b103e991a9f30b29dd536425f65cc4bb64e06a823623638a31cf9f33e4b38`。

### Result

- 本次新代码只需在 `.2` 前端节点执行前端部署；`.4/.114` 不需要部署该平台包，不重启 Java、manager、worker 或 toolbox，也不执行 Flyway。Python 独立包仍需按原计划分别安装到尚未安装成功的后台。
- 未新增或修改业务 API、事件、数据库/Flyway SQL、安全配置、generated SDK、OpenCode 源码或 `.env*`；企业实际部署仍待现场执行。

### 2026-07-30 - 修复 Mac 企业归档隐藏元数据污染并重打 Python 依赖包

### Why

- `.4` 平台包部署、readiness、worker health 和 Codex 白盒均已通过，但独立 Python 包在目标 Linux 上被 `deploy-python-libs.sh` 拒绝：Mac `bsdtar` 写入并在本机列表中隐藏了 `._python-libs`、嵌套 AppleDouble 成员和 `LIBARCHIVE.xattr.com.apple.provenance` 扩展头；原 SHA 校验成功只说明污染包未被篡改，不能证明归档安全。

### What

- Python 依赖归档改为直接在 `linux/amd64` worker 镜像内用 GNU tar 生成，并用相同目标侧实现执行严格成员检查，出现非 `python-libs/**` 成员或 Mac/PAX 扩展头即停止发布。
- 新增公共 `archive-common.sh`，所有企业发布、双后台、节点配置、Redis、MySQL 和敏感上下文 TAR/ZIP 统一排除 macOS 自动生成的 `._*`、`.DS_Store`、`__MACOSX` 等元数据；保留合法点文件，bsdtar 禁止 xattr/ACL/file flags，ZIP 使用 `-X`，并清理可移除的交付文件 xattr。
- 新增 Linux GNU tar 归档卫生回归，并同步企业部署 README、双后台文档和既有包结构测试夹具。

### How

- 真实复现同一旧归档在 Mac 列表不可见、Linux GNU tar 可见 AppleDouble 和扩展头；修复后归档卫生、固定外层 ZIP、自动节点、增量组件、Redis、MySQL、AI 文档及全部相关 shell 语法回归通过。
- 使用锁定 wheel 从官方 PyPI 重建 Python 3.13/Linux amd64 包，功能 smoke 覆盖 pandas Excel 回读、openpyxl、XlsxWriter、python-docx、jsonschema 和 orjson；再按现场 `deploy-python-libs.sh --no-restart` 完整安装到临时根目录通过。
- 最终归档 SHA-256 为 `02cd29afc667af4a336a509df925d0c4adc110335ba12daf772dec15609ef77c`；Linux GNU tar 列出 3604 个成员，Mac 元数据成员与扩展头告警均为 0。

### Result

- 现场只需丢弃旧 Python tar/校验文件并将新 tar 与 `.sha256` 分别部署到 `.4/.114`；已成功部署的平台 Java、前端、worker runtime、manager 和 toolbox 无需重新打包或重启，Flyway 也不执行。
- 企业服务器实际 Python 侧车部署尚待现场执行。未修改业务 HTTP API、事件、数据库结构、Flyway SQL、关系型 SQL、安全策略、generated SDK、OpenCode 源码或 `.env*`。

### 2026-07-30 - 兼容内网 HTTP 附件内容指纹

### Why

- 聊天附件内容寻址直接依赖 `crypto.subtle.digest`；企业浏览器通过内网 HTTP 地址访问时可能没有 Web Crypto subtle，上传会被“当前浏览器不支持附件内容指纹计算”直接阻断。

### What

- 新增 `agent-web/src/utils/sha256.ts`：优先使用 Web Crypto SHA-256，subtle 缺失或执行失败时按 64 KiB 分块回退到项目已有的 node-forge；附件仍生成完全相同的十六进制哈希路径。
- `AgentWorkbench` 改接兼容入口；SSH 私钥指纹的 node-forge 路径复用同一摘要核心，删除两套纯 JS SHA-256 实现。同步 agent-web README、PACKAGE 与 agent-chat 包说明。

### How

- 回归覆盖原生 subtle、subtle 缺失、digest 拒绝和跨 64 KiB 分块边界；定向 13 项通过。全量 Vitest 在默认高并发下触发既有 Mermaid 5 秒用例负载超时，相关文件单跑 12/12 通过，限制 `--maxWorkers=4` 后全量 105 文件、1723 项通过、1 项既有跳过；前端 lint 和生产 build 通过。

### Result

- 使用 JDK 25、`.env.test` 重启三服务；后端 health/readiness `UP`，前端 3000 返回 200，CORS 正常，manager 与用户 OpenCode 进程最终 `HEALTHY`。未修改 OpenCode 源码、后端、HTTP API、RunEvent、数据库/Flyway、鉴权或 `.env*`。

### 2026-07-30 - 基于 Python 工具运行时重建企业双后台交付

### Why

- 十二点成功部署基线已具备 worker `731ab9d...` 与 toolbox `35447da...` 指纹；当前主干新增 Python 3.13 worker 和独立 Office/数据处理库后，worker 已真实变化，不能继续发布纯前后端复用包。
- 新部署器把 Python 库安装/校验脚本列为发布包必需文件，但双后台回归的模拟发布包未同步这两个文件，导致正式包内容正确时回归夹具仍在预校验阶段失败。

### What

- 复用十二点现场组件状态重新构建当前 HEAD 的 Java、外置依赖、生产前端、programs 和 `linux/amd64` worker；组件清单固定为 `worker runtime=included`、`toolbox=reuse`，不重复携带或部署 toolbox。
- 独立生成 `test-agent-python-libs-py313-linux-amd64.tar.gz`，不将 Python 第三方库塞入平台内外层 ZIP；两个后台先升级平台 worker，再通过独立脚本原子安装 Python 库并重启 worker。
- 补齐 `verify-internal-multi-backend-node.sh` 模拟发布包中的既有 `deploy-python-libs.sh` 与 `verify-python-libs.sh`，只修正测试夹具，不新增部署实现或平行入口。

### How

- 后端 JDK 25 封包、前端文档/typecheck/生产构建、worker Python/Codex 断网检查和独立 Python 库功能 smoke 通过；Python 库实际完成 pandas Excel 回读、XlsxWriter 写入、python-docx、jsonschema 与 orjson 验证。
- 增量组件、自动节点、固定外层 ZIP、双后台节点、OpenCode Tool runtime、开发脚本回归和发布 `--validate-only` 通过；Flyway persistence JAR migration 字节仍锁定 SHA-256 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`。
- 本地 backend readiness 为 `UP`，前端 3000 与既有 toolbox 18120/18121 可访问；包内确认存在 programs、worker 和 Python 部署脚本，且不存在 toolbox 制品或独立 Python 库归档。

### Result

- 本次两个后台节点必须更新 Java/programs/worker 并重启 worker，随后独立安装 Python 库；toolbox 继续复用十二点成功部署版本，不会重建、加载或重启。前端节点只更新前端。
- 企业 `.4/.114/.2` 尚未实际部署；必须按 `.4 → .114 → .2` 顺序执行，并在每个后台运行 native `linux/amd64` Codex namespace probe 后再继续下一节点。
- 当前主干未新增或改写 Flyway migration；本次夹具修复不涉及业务 HTTP API、RunEvent、数据库/SQL、鉴权、安全凭据、generated SDK、OpenCode 源码或 `.env*`。独立 Python 执行面保持只读挂载、无编译器、运行时禁止公网 pip。

### 2026-07-30 - 为智能体 worker 增加 Python 与独立 Office 数据处理库

### Why

- Agent 权限已允许 `bash`，但实际命令运行在隔离 worker 容器中；宿主服务器虽有 Python，旧 worker 镜像没有解释器，因此对话中会正确判断 `python3` 不可用。
- 通用脚本还需要 pandas、Excel、Word 和 JSON 处理能力，但这些带原生扩展的依赖不适合烘焙进基础 worker 或在企业运行时联网安装。

### What

- worker 在 Debian 11 bullseye/glibc 2.31 基线上从官方源码构建 Python `3.13.14`，提供 `python3`/`python`、pip、venv、curl、jq、zip/unzip；运行镜像清除 gcc/make，并设置 `PIP_NO_INDEX=1`、`PYTHONNOUSERSITE=1`。
- 新增独立 Python 库制品，固定 pandas `3.0.3`、openpyxl `3.1.5`、XlsxWriter `3.2.9`、python-docx `1.2.0`、jsonschema `4.26.0`、orjson `3.11.9` 及传递依赖；逐 wheel 锁定 SHA-256，只允许 Python 3.13 / Linux amd64 二进制 wheel。
- 新增独立打包、断网功能校验和目标机原子部署脚本；库目录通过 `PYTHONPATH` 只读挂载进 worker，库升级无需重建 worker 或重启 Java。同步单/双后台、manager、后端部署和安全文档。

### How

- Python 源码校验官方大小和 SHA 后在 `linux/amd64` 镜像中编译；独立库包同时保留 requirements、wheel 来源哈希、部署文件哈希和版本元数据，并真实执行 pandas Excel 回读、XlsxWriter 写入、python-docx 生成、标准 `json`、orjson 与 JSON Schema 校验。
- 华为 PyPI 镜像下载 numpy 时发生断流并被哈希构建门禁拒绝；最终制品改从官方 PyPI 下载同一组哈希锁 wheel。目标企业节点仍不需要网络，且部署强制校验外层 SHA 和包内全部文件。

### Result

- `linux/amd64` worker 镜像实际构建并通过 Python/Codex 断网验收及 OpenCode `1.18.4` 服务启动测试；确认 Python `3.13.14`、pip `26.1.2`、glibc `2.31`、无 gcc/make。独立库包实际构建并通过打包 smoke、归档解压、候选目录校验和 `--no-restart` 原子部署 smoke。
- 生成 worker tar、programs tar 和约 32 MiB 的独立 Python 库 tar；企业 `.4/.114` 尚未实际部署，native amd64 Codex namespace 验收仍需按现场脚本执行。
- 不涉及业务 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、generated SDK 或 OpenCode 源码；安全面新增受控 Python 执行能力与第三方库，只读挂载、无编译器、无公网 pip。未修改 `.env`/`.env.local`，只更新非敏感 `env.example`。

### 2026-07-30 - 修复聊天附件投递与重复落盘

### Why

- 聊天附件此前全部降级为工作区路径提示，导致 OpenCode 原生可读取的文本、代码和模型支持的媒体也无法稳定进入原生 `file` 链路；Slash Command 又会过滤降级文本 part，模型偶发找不到本轮附件。
- 每次上传都用请求 ID 生成新物理名，同一内容跨多轮对话会在 `.testagent/attachments` 累积多个副本，并增加模型误选历史同名文件的概率。

### What

- 前端保留 OpenCode 模型 `capabilities.input`，发送时让文本/代码走原生 `file`，图片、PDF、音频、视频仅在当前模型明确支持对应模态时走原生 `file`；Office、压缩包、未知二进制及不受支持的媒体继续作为 `workspace_attachment` 交给工作区工具。
- 聊天上传改为按内容 SHA-256 与扩展名生成稳定路径；目标存在且类型、大小一致时直接复用，内容变化生成新路径，不删除历史附件。Slash Command 会把本轮降级附件的精确 `workspacePath` 追加到 arguments，并明确禁止扫描附件目录或选择历史同名文件。
- 后端保留附件投递来源元数据并继续执行工作区根路径校验；同步前后端单测、模块 README、HTTP API 文档和用户手册。

### How

- 复用现有工作区分片上传、`fileStatus`、PromptPart 与 Run 转换链路，仅增加内容寻址、模型能力分流和命令参数补偿；未新增文件代理、数据库结构、RunEvent 或 generated SDK 改动。
- `opencode-source/opencode-1.18.4/` 只用于核对模型能力与 file part 契约；项目规范禁止修改 OpenCode 源码，本次该目录零改动。

### Result

- 前端 `lint`、`typecheck`、生产 `build` 通过；全量 Vitest 104 个文件、1720 项通过，1 项既有跳过。后端 `mvn -pl test-agent-opencode-runtime -am test` 共 755 项通过，零失败。
- 使用 JDK 25、`.env.test` 和 `--skip-frontend-build` 完成三服务重启；后端 health/readiness 为 `UP`，前端 3000 返回 200，CORS 预检正确，manager 连接并将用户 OpenCode 进程拉起为 `HEALTHY`。无头浏览器确认登录首屏非空且无 console/page error；因无登录态，未执行真实附件对话写入。
- 不涉及数据库/Flyway、RunEvent、鉴权、限流或密钥；HTTP 请求结构只增加可选 `source.deliveryMode` 与模型能力透传，旧客户端缺少标记时继续走原工作区工具路径。

### 2026-07-30 - 增加 OpenCode Tool 离线依赖部署闸门

### Why

- worker 镜像构建期虽会导入自定义 Tool 依赖，但后台部署入口没有核对 programs 归档和目标机落盘目录；包缺失或增量复用目录损坏会到 OpenCode 启动后才暴露。

### What

- 新增共享 `verify-opencode-tool-runtime.sh`，以既有 runtime package 为版本单一来源，校验 manifest、lockfile、6 个直接依赖的包元数据和入口文件；`@opencode-ai/plugin`、SDK、Effect、Zod 缺失即失败。
- 标准后台 `included` 包在解压前后校验，`reuse` 包在服务变更前校验现有目录；双后台 `--verify-only` 复用同一脚本。同步单/双后台手册、内部部署 README 和 OpenCode 升级文档。

### How

- programs 大归档只用一次 tar 流提取少量校验文件；专项回归覆盖归档/目录正向、缺 plugin、缺入口文件、错误 SDK 版本和双后台 `--validate-only` 失败关闭，并复跑自动节点、增量组件和固定双后台封装。

### Result

- 所有专项与部署回归退出 0，当前真实 `deploy/internal/dist/test-agent-programs.tar.gz` 通过；未重建企业 ZIP，未部署企业节点。未涉及 HTTP/RunEvent、数据库/Flyway、鉴权、安全凭据、generated SDK、OpenCode 源码或 `.env*`。

### 2026-07-30 - 重排智能测试汇报因果主线

### Why

- A7 虽然已压缩到 9 页并补足技术内容，但底座演进、架构、案例契约、Agent/Skill 和 `docs/` 仍像五个并列技术块，听众难以看出前后因果关系。

### What

- 保留 A6 的封面、测试智能体演进页和结束页，将目录改成“为什么换底座、新底座解决什么、设计执行怎样串起来、能力怎样越用越强”四个连续问题。
- 后五页统一以“一项测试任务”为主线：Dify 固定路径的限制 → 灵犀 Code 动态规划 → 四层底座承接任务 → 详细设计经统一案例契约进入执行 → Agent/Skill/Tool/Docs 分工 → 证据经复核晋级为共享知识或可执行方法。
- 生成 `docs/presentations/智能测试专题汇报（杭州产品部）技术逻辑版A8.pptx`，A7 保留用于对照；同步演示材料 README 和可重复生成脚本。

### How

- 复用 A6 母版与原演进页，使用 python-pptx 重新组织目录和五张技术页；技术名作为图中实现说明保留，每页标题和首句先回答一个业务问题。
- 执行 markitdown 内容检查、原稿基线 OOXML 校验，并用 Keynote 实际打开导出 9 页 PDF；逐页检查目录、任务流、箭头、文字换行、遮挡和溢出，修正 `docs/` 资产页小标题换行。

### Result

- A8 共 9 页，已删除推广、专班和非功能内容；“灵犀 Code（基于 OpenCode）”只在首次架构说明中解释，后续统一使用企业内部名称。
- 未修改业务代码、HTTP/RunEvent、数据库/Flyway、关系型 SQL、安全、环境配置、generated SDK 或 OpenCode 源码；本次只更新汇报文件、生成脚本和说明文档。

### 2026-07-30 - 按十二点成功部署基线重打纯前后端包

### Why

- 用户补充确认 12:01 的 toolbox 定向包已经部署成功，说明 11:48 日志中的“toolbox 指纹缺失”已是旧状态；继续按旧状态打包会重复携带约 373 MiB toolbox 制品。

### What

- 以成功部署后的 worker、toolbox 两项安装指纹作为标准 `--component-state-file` 输入，重新构建 Java、外置依赖和生产前端；组件计划及最终清单均为 `worker runtime=reuse`、`toolbox=reuse`。
- 固定名内外层 ZIP 不再包含 programs、worker 镜像、IT-Tools、OmniTools、toolbox 源码或目录，只保留当前前后端、部署脚本和三节点配置包；未修改任何部署 shell。

### How

- JDK 25 后端封包、前端 VitePress/typecheck/生产构建和 Flyway persistence JAR 字节门禁通过；增量组件、自动节点、固定外层、双后台节点、ZIP CRC、嵌入内层 SHA 及 `--validate-only` 均通过。
- 本地 backend readiness、前端 3000、两套既有 toolbox 健康端点和深层页面正常，证明本轮制品构建未破坏运行态。

### Result

- 新包只更新并重启 `.4/.114` Java、更新 `.2` 前端；部署入口会前后校验现有 worker/toolbox 指纹和健康状态，但不加载、重建或重启这些复用组件。
- 企业实际部署尚未执行；本次没有新增或改写 Flyway migration，也未变更业务代码、API、事件、数据库结构、安全配置、generated SDK、OpenCode 源码或 `.env*`。

### 2026-07-30 - 基于新合入应用源码交互重打企业定向包

### Why

- 当前主干在上一份 toolbox 定向包后合入应用源码分支选择与多版本库关联修复，用户要求重新打包；现场 `.4` 仍只有匹配的 worker 指纹、缺少 toolbox 指纹，不能退回纯增量包。

### What

- 以当前 HEAD 和工作树的实际前端输入重新构建 Java、外置依赖和生产前端，继续用标准 `--component-state-file` 表达现场基线；组件计划为 `worker runtime=reuse`、`toolbox=included`。
- 重新生成两套 `linux/amd64` toolbox 镜像、修改源码和目录文件，并用既有三节点配置包重建固定名双后台外层 ZIP；没有修改部署 shell、伪造现场状态或重复携带未变化的 worker/programs。
- 新合入代码没有新增或改写 Flyway migration，发布仍锁定企业 migration SHA-256 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`。

### How

- JDK 25 后端封包、前端 VitePress/typecheck/生产构建、两套 toolbox 镜像构建和 `linux/amd64` 架构检查通过；本机 backend readiness、前端、工具健康端点和深层页面均正常。
- 增量组件、自动节点、固定外层、双后台节点、Flyway persistence JAR 门禁、内外层 ZIP CRC、嵌入内层 SHA 一致性和发布 `--validate-only` 均通过。

### Result

- 本包会在 `.4/.114` 更新 Java、部署 toolbox 并写入 toolbox 指纹，在 `.2` 更新前端；worker runtime 只做指纹和健康校验，不加载镜像或重启。
- 企业实际部署尚未执行；`.114` 也必须已有同一 worker 指纹，否则应停止并改用携带 worker 的全量包。未变更业务 API、事件、数据库结构、Flyway SQL、安全配置、generated SDK、OpenCode 源码或 `.env*`。

### 2026-07-30 - 修复应用源码分支选择与多版本库关联交互

### Why

- 应用源码分支数据已返回，但 `ElSelectV2` 下拉没有显示在源码弹框之上；直接输入完整分支也无法提交选择，目录树保持旧分支。
- 应用设置页关联成功后仍保留已关联版本库作为候选和当前值，重复提交会被后端幂等处理，造成“一个应用只能关联一个版本库”的误解。

### What

- 将源码分支 Teleported popper 显式设为 `z-index: 3701`，高于源码弹框遮罩的 `3700`，并启用 `default-first-option`，支持输入检索后回车选中首个匹配分支。
- 应用关联下拉改为只展示当前应用尚未关联的版本库；关联成功或切换应用后清空旧选择，并明确提示应用与版本库为多对多关系。
- 同步 agent-web README 与应用源码验收手册；本机忽略的 `.env.test` 显式设置 `TEST_AGENT_BASE_URL=http://127.0.0.1:8080`，使 Vite 请求固定走回环地址。

### How

- 使用 `openai/whisper` 的 16 个真实分支和真实 Git tree 作为浏览器拦截 fixture：修复后 popper 为 `3701 > 3700`，命中节点为分支 `LI`；鼠标选择 `jongwook/large-v3-turbo` 和输入后回车选择 `jongwook/large-v3` 均发出对应 tree 请求并更新固定提交。
- 定向 Vitest 29/29、前端全量 Vitest 1716 passed / 1 skipped、agent-web typecheck 和 production build 均通过；只读检查本机 PostgreSQL，已有应用分别存在 3 条版本库关联，约束仅为 `(app_id, repository_id)`。
- 使用 JDK 25、`test` profile 和 `.env.test` 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端与登录 CORS 为 200，manager 无重复解码或重连错误。

### Result

- 应用源码分支下拉可见、可点击，精确输入后可回车选择，分支变化会刷新固定提交和文件树；一个应用可连续关联多个版本库，已关联项不会再进入候选或被重复提交。
- 未变更 HTTP/事件 wire、数据库/Flyway/SQL、权限、安全、generated SDK 或 OpenCode 源码；`.env.test` 为本机忽略配置，不进入提交，企业交付包尚未重建。

### 2026-07-30 - 交付九页智能测试技术专题汇报

### Why

- 既有 A6 原稿以能力进展、推广数据和非功能测试为主，需要压缩为 10 页以内，并基于当前项目补足 Dify 到灵犀 Code、现行架构、设计执行融合、Agent/Skill 和 `docs/` 资产融合细节。

### What

- 保留原稿封面、目录、测试智能体演进和结束页，删除推广、专班、非功能与资源规划页，生成 9 页技术版 PPT；内部汇报统一使用“灵犀 Code”，首次技术说明标注“基于 OpenCode”。
- 新增可重复生成脚本和演示材料说明；当前架构内容以 Java 多模块、Agent Runtime、用户专属灵犀 Code 进程、RunEvent SSE、文件 WebSocket、Redis/PostgreSQL 及 Agent & Skill Hub 为依据，规划态 `docs/` 融合与已实现能力分开标注。

### How

- 复用 A6 母版和原有演进页，使用 python-pptx 生成新增技术图；执行 markitdown 内容检查、原稿基线 OOXML 校验，并用 Keynote 实际打开导出 9 页 PDF 后逐页检查文字溢出、遮挡和目录换行。

### Result

- `docs/presentations/智能测试专题汇报（杭州产品部）技术版A7.pptx` 可正常打开和渲染，校验全部通过；同名副本已输出到原稿目录。未修改业务代码、HTTP/RunEvent、数据库/Flyway、SQL、安全、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-30 - 优化应用源码分支检索、整目录选择与超时处理

### Why

- 企业内代码库分支较多时，应用源码分支只能滚动选择；分支请求成功后仍被目录读取占用加载态并出现 30 秒 `request timeout`。
- 源码树一次展开并渲染全部后代，勾选目录时下级文件没有明确显示被覆盖，用户需要逐项勾选且大目录交互明显卡顿。

### What

- 为应用源码分支和目录读取分别设置 70 秒、130 秒局部超时，保持全局 30 秒默认值不变；分支列表完成后独立加载目录，目录超时不再锁住分支检索和切换。
- 分支控件改用可检索、虚拟滚动的 `ElSelectV2`；源码树默认只渲染当前层，按需展开目录，并在读取失败时提供重试。
- 勾选目录会以一个 `DIRECTORY` 路径包含全部后代并压缩已选子路径；被父目录覆盖的节点显示“已包含”，避免逐文件勾选和大规模 DOM 更新。
- 同步 agent-web、backend-api 包说明、应用源码测试手册和后端部署超时排查手册，补充 `git ls-remote`、`git archive --remote` 与 traceId 排查方法。

### How

- 前端根 Vitest 104 个文件为 1715 passed / 1 skipped；全 workspace typecheck、生产 build、应用源码 Chromium 关键场景 3/3 通过。
- 使用 JDK 25、未修改的 `.env.test` 和 `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端和登录 CORS 为 200，manager/opencode 健康恢复成功。

### Result

- 大量分支可输入检索，目录请求慢或超时时仍可切换分支；整目录一次勾选即可覆盖后代，大树初始和勾选渲染量显著收敛。
- 未变更 HTTP/事件 wire、数据库/Flyway/SQL、权限、安全、generated SDK、OpenCode 源码或环境配置；企业离线发布包尚未重建，现场升级前仍需按现有企业打包流程生成并验证新包。

### 2026-07-30 - 支持版本库类型安全编辑并置顶测试工作库

### Why
- 版本库类型创建时选错后设置页只读，无法在尚未产生下游数据时修正；旧 `standard` 布尔字段又无法区分应用代码库和应用资产库。

### What
- 版本库 PATCH 新增可选 `repositoryType`，显式三态类型优先于旧 `standard`，并继续由领域模型派生 `standard`；旧客户端省略新字段时保持兼容。
- 无类型专属历史时三种类型可互相切换；已有 `application_workspaces`、已初始化引用资产副本，或已有 app-source slot/snapshot/operation/cleanup 历史时，按原有分支、目录和磁盘身份返回 `CONFLICT`。工作空间历史查询新增在 MyBatis XML，未在存量 JDBC 实现继续添加 SQL。
- 前端编辑弹窗改为类型下拉并只提交 `repositoryType`；后端字典响应和前端容错排序都固定将“测试工作库”放在第一项。

### How
- 回归覆盖 3×3 显式类型转换、显式类型优先级、旧 `standard` 兼容、三类历史冻结、MyBatis `exists` 查询、Controller DTO、前端选项顺序和 API 请求体；同步 HTTP API、模块图和前后端包 README。
- JDK 21 目标 Maven reactor 测试通过：配置管理 32/32、Controller 17/17、持久层 12/12；前端定向 Vitest 104/104、全工作区 typecheck 和 production build 通过。一次接口扩展编译发现并补齐 workspace-management 测试 Fake 方法。

### Result
- 使用 JDK 25、`.env.test` 和 `test` profile 完整重启 backend、opencode-manager 和 frontend；health/readiness 为 `UP`，前端与 CORS 预检为 200，manager WebSocket 已连通且 OpenCode 达到 `HEALTHY`。
- 本次变更 HTTP 请求 DTO 和关系型查询 SQL，但不改数据库结构、Flyway、RunEvent/进度事件、权限/安全边界、环境配置、generated SDK 或 OpenCode 源码；无未完成编码项，企业交付包未在本任务中重建。

### 2026-07-29 - 修复新关联应用源码无法首次下载

### Why
- 新增应用代码库并建立应用关联后，仓库没有 active snapshot，后端却返回 `manageable=false`；前端下载入口据此拒绝打开管理弹窗，导致首次源码物化无法发起。

### What
- 修正应用源码仓库列表的 `manageable` 语义：没有 active snapshot 或 snapshot 已过期时，任一当前有效应用成员均可发起新 generation；未过期 snapshot 仍仅允许 owner 或 `APP_ADMIN` 管理。
- 补充未下载、已过期和他人有效个人占用三种回归断言，并同步 workspace-management 模块说明与 HTTP API 字段语义。

### How
- TDD 先以 2 个失败断言稳定复现，再实施单点业务修复；`AppSourceApplicationServiceTest` 27/27、workspace-management reactor 374/374、`AppSourceControllerTest` 6/6、前端定向 Vitest 11/11、agent-web typecheck 和 Chromium 应用源码工作台场景均通过。
- 按 `.env.test` / `test` profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端与 CORS 预检为 200，manager WebSocket 连通且 OpenCode 健康状态稳定为 `HEALTHY`。

### Result
- 本地代码与运行实例已恢复首次下载入口；未下载仓库继续按设计不出现在只展示可打开快照的紧凑选择器，但会出现在“下载应用源码”弹窗。
- 本次只调整既有 `manageable` 字段语义，不新增 HTTP/事件字段，不涉及数据库/Flyway、SQL、环境配置、性能、安全、generated SDK 或 OpenCode 源码；现有企业交付 ZIP 尚未重建，现场升级仍需用本提交重新打包部署。

### 2026-07-29 - 确认新关联应用源码首次下载被 manageable 门禁阻断

### Why
- 企业部署后新增应用代码库并建立应用关联，但“应用源码”入口仍找不到该仓库；用户要求先只读定位，不修改业务代码。

### What
- 确认应用源码列表只保留 `APPLICATION_CODE_REPOSITORY`；正确类型的新仓库因没有 active snapshot 被标记为 `NOT_DOWNLOADED`，紧凑入口按设计隐藏该状态。
- 确认后端把 `manageable` 写成 `occupied && (owner || appAdmin)`，导致从未下载或已过期仓库一律不可管理；前端“下载版本库”又要求至少一个 `manageable=true` 才打开管理弹窗，因此首次下载链路被完全阻断。

### How
- 沿 `AppSourcePicker -> AgentWorkbench -> AppSourceController -> AppSourceApplicationService -> ConfigurationManagementMapper.xml` 只读核对调用链，并反编译当前 `deploy/internal/dist/backend/lib/test-agent-workspace-management-0.1.0-SNAPSHOT.jar`、搜索当前前端 dist，确认交付物包含同一逻辑。
- 前端 `AppSourcePicker`/`AppSourceDialog` 定向 Vitest 11/11 通过；JDK 25 下 `AppSourceApplicationServiceTest` 27/27 通过，现有后端无快照用例未断言 `manageable`，所以没有发现前后端契约断裂。

### Result
- 根因已确认但未修复；现场可用仓库列表 API 或只读查询核对 `repository_type`。重复关联、刷新或重复部署当前包不能解除首次下载门禁；后续应补后端 `manageable` 规则及跨层回归测试。
- 本次不修改业务代码、HTTP/进度 WebSocket wire、数据库/Flyway、SQL、配置、部署产物、generated SDK 或 OpenCode 源码。

### 2026-07-29 - 固化空回答的公共工具构建失败判定

### Why
- 企业现场原始输出显示全部关键 HTTP/SSE 正文非空，但 assistant token 为 0、没有 message part，平台随后仍产生 `run.succeeded`；继续统称“空报文体”会误导到 Nginx 或模型正文。
- 精确用户 OpenCode 日志连续复现 `prompt_async failed` 和公共 `auto-call.ts` 的两条构建错误；另有 Java 模型代理 `400` 零字节、同机企业上游直连流式成功这一独立异常。

### What
- 更新企业空报文体手册，增加公共自定义工具构建失败的明确签名、逐用户投影不可直接修改、权威公共配置回退/修复发布、旧 Git `add --sparse` 告警隔离和恢复验收条件。
- 增加“Java 代理 400 零字节但 9070 直连正常”的分层判断，要求核对 provider API 根路径、数据库 token、Java 内存快照及 UCID/header，并以正式 Java 代理返回有效 SSE 复验。
- 加强诊断凭据处置：完整 Bearer/token 一旦进入原始输出、shell 历史或诊断文件，必须立即吊销/轮换并按企业审计要求清理，不得复用或回传。

### How
- 对照 OpenCode 1.18.4 只读源码确认配置目录的 `tool/tools` 脚本会被扫描并动态加载，单个工具构建失败可在主模型调用前中止整个提示；本地企业公共配置发布 ZIP 不含现场失败的 `auto-call.ts`，说明应从权威公共配置管理定位，而不是重打应用包或修改逐用户生成文件。
- 对照 Java 内部模型代理转发实现确认其会在 provider `base_url` 后追加 `/chat/completions`，且非 2xx 响应状态和正文按上游返回透传，因此直连上游成功不能证明正式代理配置正确。

### Result
- 本次只更新排查文档和会话记录，不修改运行代码、生产公共配置、环境文件、HTTP/RunEvent 协议、数据库/Flyway、SQL、generated SDK 或 OpenCode 源码；企业现场仍需完成公共工具回退/修复、凭据轮换和 provider 配置复验后才能确认恢复。

### 2026-07-29 - 固化企业空报文体逐层排查手册

### Why
- 企业双后台部署后仍出现“（空报文体）”，此前排查没有形成能区分正常空 GET、异常 HTTP 零字节响应、SSE 空 `data:` 和 Run 成功无 assistant 文本的统一现场执行单。

### What
- 新增 `deploy/internal/EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md`，按浏览器、`.2` Nginx、`.4/.114` Java、RunEvent SSE、用户 OpenCode 和企业模型代理逐层采证，并固化 package/JAR/lib/frontend SHA、traceId/runId、只读直连对比和重部署停止条件。
- 在部署总入口、单后台、多后台和 `docs/README.md` 增加索引；排查输出禁止携带 JWT、Cookie、内部代理 key、上游 token、UCID、未脱敏 HAR 和完整用户正文。

### How
- 复用现有原始输出捕获、统一 API 日志、SSE 日志、运行管理 buildVersion、manager 用户实例日志和固定外层包，不新增诊断 API、脚本或旁路。
- `tools/verify-ai-docs.sh`、Markdown 围栏/尾随空格检查和 `git diff --check` 通过；当前内外层 ZIP SHA 与嵌入内层 SHA 一致，包内 `test-agent-api/opencode-runtime/event` JAR 与本地 dist SHA 对比命令实测一致。

### Result
- 手册和逐机命令已完成文档校验；尚未连接企业 `.2/.4/.114`，现场空报文首因仍需按手册取得具体 `traceId/runId/status/bytes` 后判断，不声称故障已修复。
- 本次仅文档与会话记录，不变更运行代码、HTTP/RunEvent wire、数据库/Flyway、SQL、环境配置、generated SDK 或 OpenCode 源码；现有企业 ZIP 早于本手册生成，二进制未因此变化。

### 2026-07-29 - 基于新合入会话终态隔离重建企业全组件包

### Why
- 用户在上一版企业包之后合入聊天会话终态隔离代码，要求以当前主干重新打包；旧包不包含真实会话 ID 被替换或清空时同步清除上一会话停止、完成、失败展示状态的修复。

### What
- 以业务代码基线 `c9fa4b429` 重新构建 backend、frontend、programs、`linux/amd64` worker、IT-Tools、OmniTools 和 toolbox，并继续通过 `--include-all-components` 强制携带全部可选组件。
- 复用既有固定名双后台外层封包和节点敏感包规范化流程；本次新合入代码没有新增 Flyway migration、业务 API、RunEvent、关系型 SQL、企业部署配置、generated SDK 或 OpenCode 源码变更。

### How
- agent-web 定向 Vitest 143 项通过、1 项跳过，typecheck 与生产 build 通过；JDK 25 下 `WorkspaceFileWebSocketHandlerTest` 30/30 通过。
- 使用 `.env.test` / `test` profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端与 CORS 预检为 200，Flyway schema 为 `20260728210000` 且无待执行 migration，manager WebSocket 与 OpenCode 健康状态正常。
- 全组件封包、AI 文档、多后台节点、自动部署和开发脚本校验通过；应用 JAR、PostgreSQL 驱动及 Flyway `V20260727203500`、`V20260728160800`、`V20260728210000` 内容检查通过，内外层 ZIP 完整性与嵌入内层 SHA 一致性通过。

### Result
- 当前代码的全组件内层包和固定名双后台外层包预校验通过；本条日志提交后再以 `--zip-only --include-all-components` 重封内层和外层，最终 SHA-256 以交付结果为准。
- 本地三服务保持运行，页面企业参数仍按每台后台 `OPENCODE_MANAGER_MAX_PROCESSES=30` 交付；Mac 为 arm64，Codex 原生 namespace 沙箱仍须在 `.4/.114` 的 Linux/amd64 worker 上执行随包探针。

### 2026-07-29 - 重建 Flyway 兼容企业双后台全组件包并提升单机进程上限

### Why
- 用户要求基于当前代码重打企业完整包，必须携带本次 Flyway 兼容变更、规避早间本地启动暴露的 PostgreSQL 驱动与 WebSocket/CORS 装配问题，并确保 toolbox 不因本机增量指纹误判而缺包。
- 页面通用参数原值为 `OPENCODE_MANAGER_MAX_PROCESSES=20`；该值会分别热推到两台 manager，因此“两后台各增加 10 个”应改为全局值 `30`，不是扩展 1000 端口池或预创建进程。

### What
- 双后台稳定文档、企业打包技能和部署夹具统一为每台 worker 继续发布 `14096-15095` 共 1000 个端口坐标、每台 manager 实际上限 30；明确页面保存后同时热推两台在线 manager。
- 完整升级手册增加 Flyway 发布闸门：上线前只读核对 `flyway_schema_history`，覆盖企业顺序基线和旧测试库 `V20260727203500` 两类历史，首台 `.4` 验证 `V20260728160800`、`V20260728210000` 成功后才允许启动 `.114`；禁止 `outOfOrder`、`repair` 或手改历史表。
- 使用 `--include-all-components` 重新构建 backend、frontend、programs、`linux/amd64` worker、IT-Tools、OmniTools 和 toolbox，不复用旧 toolbox 组件计划；节点敏感包只从既有固定名外层包复用并由外层封包程序重新规范化。

### How
- JDK 25 定向运行 Flyway 两套真实 PostgreSQL 历史、migration 命名、通用参数热推和应用源码 WebSocket 测试；API 8 项、persistence 3 项、app 3 项通过。AI 文档、多后台节点、固定名完整包和开发脚本校验通过。
- 按 `.env.test` / `test` profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端和 CORS 预检为 200，当前日志没有 PostgreSQL 驱动缺失、构造器注入失败、Flyway 校验失败或应用启动失败，数据库 schema 已到 `20260728210000`。
- 完整打包后检查应用 JAR 内置 RSA、兼容装配类，persistence JAR 同时包含正式迁移、旧 checksum 兼容脚本和在途恢复索引，`backend/lib` 包含 PostgreSQL 42.7.11；内层与外层校验和、嵌入内层 SHA 一致性均通过。

### Result
- 全组件构建和固定名双后台外层包预校验通过；本条日志提交后再用 `--zip-only --include-all-components` 重封内层及外层，确保最终交付包包含本次文档和会话记录，最终 SHA-256 在交付结果中给出。
- 未新增或改写 Flyway migration、业务 API、RunEvent、关系型 SQL、环境配置、generated SDK 或 OpenCode 源码；Mac 为 arm64，Codex 原生 namespace 沙箱仍须在 `.4/.114` 的原生 Linux/amd64 worker 上执行随包探针。

### 2026-07-29 - 修复本地 worktree 文件树 wildcard Origin

### Why
- `.env.test` 使用单独的 `TEST_AGENT_CORS_ALLOWED_ORIGINS=*`；HTTP 和应用源码进度通道支持该测试配置，但平台文件 WebSocket 仍把 `*` 当普通字符串精确匹配，导致 route/ticket 成功后 upgrade 立即以 `FORBIDDEN origin denied` 关闭，页面只显示 worktree 文件树加载失败。

### What
- `WorkspaceFileWebSocketHandler` 仅在 CORS 恰好为单个 `*` 时接受任意格式合法的浏览器 Origin，并继续拒绝缺失、畸形来源；混合 wildcard 不获得通配能力，显式白名单行为不变。
- 新增 wildcard 成功、畸形 Origin 拒绝和混合 wildcard 不放宽回归；同步 API、Platform File WebSocket、安全规范及 API 模块测试说明。

### How
- 复用既有 `AppSourceWebSocketOrigin.canonicalize` 做 Origin 结构校验；TDD 红测先稳定得到 `FORBIDDEN origin denied`，修复后 `WorkspaceFileWebSocketHandlerTest` 30/30 通过。
- 用 JDK 25、未修改的 `.env.test` 和 test profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`、前端和 CORS 为 200、OpenCode 4104 为 `HEALTHY`，真实浏览器刷新后 worktree 根目录正常显示且无 console error。

### Result
- 小宠物健康但 worktree 文件树循环加载失败的问题已在真实本地三服务中恢复；未修改 `.env.test`、HTTP/事件字段、数据库/Flyway、SQL、generated SDK 或 OpenCode 源码。

### 2026-07-29 - 回退 guojq 对话应用版本上下文提交

### Why
- 用户要求仅撤回 guojq 于 2026-07-28 提交的“每次对话请求带应用和版本”代码，不能误回退同日合并提交带入的其他主干改动。

### What
- 反向应用 `dfd34a48f` 与 `a3876d239`，移除 `<env_context>` 应用/版本上下文扩展、子条目提取和对应的用户消息过滤；保留合并提交及后续公共工作树修复。
- 同步撤回这两个提交追加到 `.agents/session-log.guojq.md` 的 2026-07-28 条目。

### How
- 通过 `git revert --no-commit` 按逆序回退两个目标提交，复核暂存差异为 3 个文件且仅含目标提交反向内容；保留既有未提交的 `.agents/session-log.md`。
- 运行 agent-chat 定向 Vitest、agent-web typecheck 和 production build。

### Result
- 定向测试 7 项通过，agent-web 类型检查和生产构建通过；未涉及 API、事件、数据库、环境配置、安全或 generated SDK。

### 2026-07-29 - 复核工具盒子企业自动部署边界

### Why
- 用户反馈当前企业部署时 toolbox 不再自动部署，需要重新核对 Mac 封包决策、外层逐机入口和底层通用发布脚本的真实调用链。

### What
- 确认完整打包自 `9ae639fb6` 起默认按 Mac 输出目录的 `.release-component-state.env` 判断 `included/reuse`；相同指纹会生成 `reuse` 包并省略 toolbox 镜像，即使该 Mac 包尚未在 `.4/.114` 真正部署。
- toolbox 自动部署只位于外层 `deploy-backend-node.sh`；直接运行节点内的 `deploy-multi-backend-node.sh` 或通用 `deploy-internal-release.sh` 只处理平台/worker，不会启动工具容器。

### How
- 当前 `deploy/internal/dist` 的 16:21 外层包内清单为 `TEST_AGENT_RELEASE_TOOLBOX=included`，包含两张镜像 tar，且包内 `deploy-backend-node.sh` 与当前源码 SHA-256 相同；用同一目录执行 `--component-plan-only` 时下一包已变为 `reuse`。
- `tools/verify-internal-incremental-components.sh`、`tools/verify-internal-auto-node-deploy.sh` 和四个相关 Shell `bash -n` 通过。

### Result
- 当前 16:21 固定名外层包通过正确入口应自动部署 toolbox；后续默认重打包不会再次携带它。首次部署、目标状态不确定或之前全量包未落到两台后台时必须使用 `--include-all-components`，现场只能运行外层 `deploy-backend-node.sh`。
- 本次仅诊断和留痕，未修改部署脚本、业务代码、API、事件、数据库、SQL、环境配置、generated SDK 或 OpenCode 源码；尚未连接企业节点，现场结果仍需结合 `deploy-<本机IP>.log` 验证。

### 2026-07-28 - 补齐公共目录选服与初始化后精确重挂载

### Why
- 前一提交虽阻止查询失败/无 binding 时自动创建 worktree，但公共目录读取和来源展示仍会回退首台已初始化服务器；初始化成功且服务器 ID 不变时，前端也不会消费返回的 worktree ID 重新挂载。

### What
- 公共目录只有存在当前个人 worktree，或进程归属已成功解析且同服公共仓库已初始化时才请求；未解析、未分配或超级管理员 worktree 准备失败时，不再加载或展示其它服务器的共享直接目录。
- 初始化成功后把 `publicWorktreePreparation` 的精确 worktree/server 转成带修订号的挂载请求，清除不匹配的旧挂载、按返回 ID 重选并刷新目录；用户确认暂不处理手工切换的低概率并发覆盖问题。

### How
- 复用 `AgentWorkbench → FigmaFileExplorer → AgentConfigPanel` 既有组件链路和 `listPublicAgentWorktrees`，没有新增 HTTP API、后端服务或状态仓库；定向 Vitest 144 项、前端全量 1672 passed/1 skipped、lint、agent-web typecheck/生产 build、文档校验和 `git diff --check` 通过。

### Result
- 两项指定缺口已由回归测试覆盖；不清理任何存量 worktree/数据库数据，不涉及 API、RunEvent、数据库、SQL、generated SDK、安全或环境配置。`.env.test` 三服务重启时后端仍被既有 Flyway 分叉阻断：数据库已应用当前代码不存在的 `20260727203500`；未执行 repair 或修改历史表，前端现有 Vite 服务保持 HTTP 200。

### 2026-07-28 - 收口公共个人 worktree 初始化与刷新路由

### Why
- 上一版只让前端在已有进程绑定时跟随服务器，仍把查询失败与成功无 binding 混在一起；无 binding 会先创建任意服务器 worktree，且并发刷新中的旧响应可能覆盖最新服务器结果。

### What
- 进程归属只有查询明确成功才标记已解析；成功但无 binding 时不再自动创建公共个人 worktree，等待用户点击初始化。
- `SUPER_ADMIN` 初始化进程并通过公共健康检查后，由目标 Java 幂等准备同服 `public-{userId}` worktree；准备失败用 additive `publicWorktreePreparation` 单独提示，不推翻进程 `READY`。
- Agent 配置刷新统一使用代次隔离服务器、工作空间和 worktree 请求；迟到响应不再挂载旧服务器。存量 worktree 和数据库记录不清理，显式创建/切换入口保留。

### How
- 复用 `BackendJavaRouteResolver` / `BackendHttpForwarder` 既有初始化路由和 `AgentConfigApplicationService.createPublicWorktree`，未新增路由扫描、跨 Java 文件 HTTP 代理、数据库 SQL 或 Flyway migration。
- 前端相关 Vitest 128 项、agent-web typecheck、前端生产 build、后端 `AgentConfigApplicationServiceTest` 53 项和 `RuntimeControllerTest` 25 项、后端 20 模块 clean package、`git diff --check` 通过。

### Result
- 三条问题按原范围收口：查询错误不会误判未分配；点击初始化后进程和公共个人 worktree 同服；旧刷新结果不会覆盖最新路由。
- 按本地启动规范用未修改的 `.env.test` / `test` profile 实际重启，构建成功但后端被既有 Flyway 分叉阻断：测试库已应用本仓库不存在的 `20260727203500`。未执行 repair、未修改历史表；前端 3000 返回 200，后端 8080 未启动，真实端到端仍待数据库基线由集成人处理后复验。

### 2026-07-28 - 公共个人 worktree 自动跟随 OpenCode 进程服务器

### Why
- 公共个人 worktree 原先优先复用页面记忆或首个已初始化仓库服务器，与独立负载均衡的用户 OpenCode 进程可能跨服，导致个人配置热加载冲突。

### What
- 前端等待 `/processes/me` 归属查询完成；已有进程绑定时自动复用或创建同一 `linuxServerId` 的公共个人 worktree，没有绑定时才沿用旧回退规则。
- 其它服务器上的历史 worktree 和数据库记录保留，避免删除可能存在的未提交内容；手工创建和切换能力不变。

### How
- 复用现有页面内存路由 ID、公共仓库列表及 worktree 查询/创建接口，没有新增后端路由、数据库结构或迁移。
- Agent 配置面板和文件树定向测试 46 项、agent-web typecheck、前后端生产构建及 `git diff --check` 通过。

### Result
- 自动挂载不再在进程查询完成前抢先选服；当前进程服务器存在已初始化公共仓库时，公共个人配置与热加载进程保持同服。
- `.env.test` 三服务重启受既有 Flyway 历史分叉阻断：测试库已应用 `20260727203500`，当前仓库只保留改号后的 `20260728160800`；未执行 repair 或手工改库。前端 3000 仍为 200，后端 8080 当前未启动。

### 2026-07-28 - 阻断工具盒子上游不通的前端发布

### Why
- 企业部署后工具卡片能展示，但点击具体工具返回 Nginx 502；既有 `.2` 前端验收只核对 toolbox upstream 配置文本，没有真实访问四个工具端口或统一入口深链。

### What
- 复用现有 `deploy-multi-backend-node.sh` 前端验收，在 reload 后从 `.2` 逐一探测 `.4/.114:18120/18121` 的健康端点和真实工具路径，再请求本机 Nginx 两条统一入口并核对 CSP/COEP 响应头。
- 同步多后台、工具盒子部署文档和脚本契约回归；失败信息携带具体工具来源和 endpoint，便于区分容器、端口绑定与防火墙问题。

### How
- `bash -n`、`tools/verify-internal-multi-backend-node.sh`、`tools/verify-internal-nginx-config.sh`、`tools/verify-internal-auto-node-deploy.sh`、`tools/verify-internal-two-backend-complete-package.sh`、`tools/verify-ai-docs.sh` 和 `git diff --check` 通过。

### Result
- 后续 `.2` 发布会在浏览器验收前阻断 toolbox 502；本次未接入企业服务器，现场仍需按 `.4 → .114 → .2` 检查容器和跨机端口。未修改 API、事件、数据库/Flyway、SQL、业务权限、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-28 - 固化应用切换时的 Skill 目录查询上下文

### Why
- Command 查询在 query function 中闭包读取当前 workspaceId；应用切换期间旧请求可能使用新 workspace，造成 Skill 目录短暂缺失且刷新页面后才恢复。

### What
- `AgentWorkbench` 的 Command 查询改为与 Agent 查询一致的响应式 query key，并只使用 key 中固化的 workspaceId；补充源码契约回归，同步 agent-web README/PACKAGE。

### How
- TDD 先确认新增用例失败，修复后定向 Vitest 13 项、agent-web typecheck、生产 build、`git diff --check` 通过；JDK 25 下后端 20 模块跳过测试打包通过。
- 使用未修改的 `.env.test` 和 test profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端和 CORS 为 200、OpenCode 4104 收敛到 HEALTHY。

### Result
- 应用切换后的迟到 Command 响应只能写回原 workspace 缓存，不会读取或覆盖新应用的 Skill 列表；未变更 API、RunEvent、数据库/Flyway、权限、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-28 - 重建当日功能企业双后台完整包

### Why
- 上一版企业包早于当日应用 Git 刷新、公共 Agent 全局 rollout、两条 Flyway migration 和 Codex 只读白盒 MCP，单独替换 JAR 或前端会导致 Java、programs 与 worker 版本错配。
- 复核交付链时发现自动节点部署夹具仍要求首台 `.4` 检查尚未启动的 `.114`，与现行停机升级顺序不一致。

### What
- 以业务源码提交 `fe980baa5df03710b63eeca201c4eff657196dcc` 完整重建后端、前端、programs、`linux/amd64` worker 和内层发布 ZIP，并复用已校验的 `.4/.114/.2` 节点配置重封固定名外层包。
- 自动节点部署夹具改为验证 `.4 --skip-peer-check` 后再验证 `.114 --peer-host 122.233.30.4`；生产部署脚本和节点配置未修改。

### How
- `package-release.sh` 完整构建通过；JAR 内置 RSA、两条新 migration、Responses 适配器、白盒部署文件、内外层 ZIP 一致性和镜像 `linux/amd64` 均已校验。
- Codex 白盒 MCP 合同测试 4 项、OpenCode 1.18.4/glibc 2.31 worker 容器冒烟、AI 文档、自动节点、多后台、Nginx 和完整包 fixture 均通过；Mac 为 arm64，Codex 原生 namespace 沙箱按设计留待两台企业 Linux 节点执行随包探针。

### Result
- 固定名交付物为 `deploy/internal/dist/test-agent-two-backend-complete.zip` 及同名 `.sha256`，需按 `.4 → .114 → .2` 整包滚动替换；白盒功能启用前必须在 `.4/.114` 分别通过宿主探针。
- 本次只修改部署验收测试和本机追溯日志；未新增业务 API、RunEvent、数据库结构、生产 SQL、权限、环境配置、generated SDK 或 OpenCode 源码，既有部署与白盒稳定文档已覆盖现场操作。

### 2026-07-28 - 澄清公共个人 worktree 自动同步入口

### Why
- 复核公共全局刷新设计时，旧按服务器 pull 兼容路由和公共根节点的运行态重载按钮容易被误认为仍有个人 worktree Git 同步入口。

### What
- 在后端兼容 Controller 和 backend-api client 注明：当前前端没有调用 `/public/repositories/{linuxServerId}/pull`，`linuxServerId` 只保留旧路由形式，实际仍委托全局 rollout。
- 在公共 worktree 同步 worker 和 Agent 配置树按钮旁注明：个人 worktree Git 同步没有独立按钮，由“刷新公共 Agent Git”自动推进；“Agent 配置更新（公共）”只重载当前超管运行态。

### How
- 仅补代码注释，不删除兼容接口、不新增按钮、不改变 API、Git、权限或 dispose 行为。后端 API reactor 跳过测试打包、agent-web typecheck 和 `git diff --check` 通过。
- 使用 JDK 25、未修改的 `.env.test` 和 test profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端和 CORS 为 200、OpenCode 4104 收敛到 HEALTHY。

### Result
- 两类入口职责已在代码附近写清；现有稳定文档已经使用相同口径，无需再改。未涉及 RunEvent、数据库/Flyway、SQL、generated SDK、OpenCode 源码或环境配置。

### 2026-07-28 - 按当前功能更新用户手册与排查入口

### Why
- 现有手册缺少集中功能总览和按现象组织的排查流程，应用内 Help 也遗漏了已经存在的“引用配置”Markdown。

### What
- 新增功能总览，覆盖工作台、文件与 Mermaid、对话与定时任务、Git 助手、Agent/Skill Hub、应用资产引用和宠物帮助；首页与快速开始同步入口。
- 新增常见问题排查，覆盖文件树、发送门禁、Git、Agent/Skill、Hub、引用配置、定时任务和手册问答，并提供脱敏上报模板。
- VitePress 与应用内 Help 同步注册功能总览、引用配置和排查章节，宠物问答继续直接读取同一 Markdown。
- 按用户指定的 `op7418/humanizer-zh` 规则复查用户可见文案，删掉模板化开场、机械连接词和过度解释，保留按钮名、技术 ID、权限与生效规则。
- 常见功能问答和故障排查最终合并到 `faq.md`，删除独立排查页；静态导航、应用内 Help 和所有章节链接只保留“常见问题与排查”入口。合并页约 5300 字，宠物问答仅对该主题把上下文上限从 2800 调到 5600 字。

### How
- 根 workspace 定向 Vitest 11 项、user-manual 构建、agent-web typecheck 和生产 build 通过；VitePress preview 在 `127.0.0.1:3001/help/` 启动，新页面 HTTP 均为 200。
- 包内直接 `vitest` 不会加载根 jsdom 配置；DOM 用例应从 `frontend/` 执行根 workspace 的 Vitest 入口。
- `humanizer-zh` 已安装到本机 Codex skills 目录；润色后再次运行定向 Vitest 11 项和 agent-web 生产 build，预览页确认新文案已生效。
- 合并后定向 Vitest 11 项和 agent-web 生产 build 再次通过，`faq.html` 预览返回 200 且包含问答、排查和上报模板。VitePress preview 请求刚删除的旧静态页会因 ENOENT 退出，已按最新产物重启预览。

### Result
- 用户可从静态手册和应用内 Help 查看当前能力与排障路径，引用配置也可作为宠物问答事实来源；未修改 API、RunEvent、数据库/Flyway、SQL、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 展示个人拉取 merge 流程与 dispose 结果

### Why
- 用户需要在个人拉取前明确知道应用 Agent 也会更新且系统会直接执行 Git merge，并希望可关闭后续确认；拉取后还需看到更新文件和 dispose 结论。
- 应用 Agent 平台发布只能立即同步可安全合并的个人 worktree，本地 dirty/冲突用户保留为持久化待同步，不能假设推送瞬间所有用户都已收敛。

### What
- 新增 `PersonalWorkspacePullDialog.vue`，按“确认 → fetch/比较 → merge → 文件/Diff 刷新 → 运行态检查”展示；确认偏好按用户写入浏览器 localStorage，但每次仍展示过程和结果。
- 复用个人 `git-pull` 的 `changedFiles/agentConfigChanged` 与既有 `reloadReferenceRuntimeIfIdle`，结果区列出更新文件，并区分无需 dispose、已 dispose、等待 Session 空闲、进程未运行和 dispose 失败。

### How
- 定向 Vitest 3 文件 59 项、agent-web typecheck、前端全仓 lint 和生产 build 通过；JDK 25 后端 20 模块跳过测试打包成功。
- 使用未修改的 `.env.test` / test profile 完整重启 backend、manager、frontend；health/readiness 为 UP、前端和登录 CORS 为 200，manager WebSocket 与自动恢复的 OpenCode 4104 最终健康。

### Result
- 不再提示只跳过确认，不跳过拉取过程和结果；应用 workspace/Agent、提交推送、角色/目录权限和 dispose 时机均继续复用原程序。
- 未修改 HTTP/RunEvent wire、数据库/Flyway、SQL、后端业务代码、安全配置、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 公共 Agent 新增对话式工作区 Git 助手

### Why
- 工作区 Git 同时包含个人拉取、暂存/回退、个人提交、应用发布、冲突处理、目录角色和 Agent dispose 规则，普通用户仅靠页面提示仍难以判断下一步。
- 直接让 Agent 执行原生 `git` 会绕过平台 owner、`.opencode/**`、`spec/**`、发布同步和 dispose 约束，需要把既有平台能力安全地暴露给对话。

### What
- 公共 Agent 配置新增 `workspace-git` Tool 与 `workspace-git-assistant` Skill，支持状态、个人拉取、暂存/取消暂存、逐文件回退、个人提交、发布预览/发布和冲突处理，并用普通用户语言组织流程。
- 后端新增对话 Git 专用入口：由 OpenCode session 反查当前个人 workspace，不接受客户端 workspace ID；所有动作委托既有 `ManagedWorkspaceApplicationService`，HTTP 页面与 Tool 共用 `.opencode/**` 路径角色策略。
- 用户 OpenCode 启动时注入同节点平台地址和七天有效的专用签名凭据；凭据只能访问精确 Tool 入口，用户状态与角色每次调用实时校验，不能作为通用登录 Token。

### How
- 定向回归覆盖个人 workspace 归属、角色路径、操作映射、专用凭据签发/过期/禁用用户、API 认证豁免边界和 OpenCode 启动环境注入；相关 Maven 测试通过。
- 使用 JDK 25 与未修改的 `.env.test` 完整打包并重启 backend、manager、frontend；health/readiness 为 UP、前端和 CORS 为 200、无凭据 Tool 请求为 401，自动恢复的 4104 OpenCode 进程实际发现 `workspace-git` 且包含受控运行时凭据。

### Result
- 用户可以在对话中处理当前个人 workspace 文件，个人拉取和个人提交仍只影响本人；只有明确发布才进入原有共享 target、同步和 dispose 流程。
- 原有 owner、目录角色、`spec/**` 发布限制、应用同步、冲突和 dispose 逻辑未复制或放宽；未修改前端、数据库/Flyway、SQL、RunEvent、generated SDK、环境配置或 OpenCode 源码。

### 2026-07-27 - 公共 Agent 初始化与拉取操作固定展示

### Why
- 公共配置管理表格包含多个长路径列，按服务器的初始化/拉取操作落在最右侧并滑出首屏，超级管理员容易误以为页面只有刷新能力。

### What
- 复用既有公共仓库初始化、拉取 API，将操作列固定在表格右侧；未初始化行只显示“初始化”，已初始化行只显示“拉取更新”，并明确提示仅超级管理员可操作。
- 增加前端非超级管理员入口隔离测试和后端初始化/拉取 `SUPER_ADMIN` 强鉴权回归；同步 agent-web README。

### How
- 前端全量 Vitest 1634 项通过、1 项跳过，agent-web typecheck 与生产 build 通过；JDK 25 `AgentConfigControllerTest` 18 项通过，`git diff --check` 通过。

### Result
- 公共 Agent 初始化/拉取在“系统管理 → 配置管理 → TestAgent公共配置管理”中按服务器始终可见，`APP_ADMIN` 前后端均不可操作；未修改现有 Git 服务、API wire、事件、数据库、环境配置或 OpenCode 源码。

### 2026-07-27 - 录屏去除小宠物并重新合成主图

### Why
- 用户要求把网页录屏按宣传主图设计嵌入，并明确录制过程中不能出现小宠物。

### What
- 新增 `docs/assets/marketing/ice-blue/source-workbench-no-pet.gif`，在原 2894×1628 网页录屏左下角工具栏位置移除固定宠物，保留齿轮、Agents 和底部控件。
- 重新生成 `00-overview-with-demo.gif`，使用无宠物录屏嵌入冰蓝色主图，保留 16:9 原比例和宽幅网页展示区。

### How
- 用 FFmpeg 逐帧检测联系表与左下角局部帧，确认宠物固定在 x≈1–160、y≈1360–1530 区域；从同一侧栏干净背景复制局部区域进行修补，再以 4 FPS、15.25 秒重新合成主图。
- 使用 `file`、`ffprobe` 和首帧局部预览验证：源清理 GIF 与动态主图均为 61 帧、4 FPS、15.25 秒，主图为 1672×941。

### Result
- 无宠物录屏和无宠物动态主图已生成；未修改业务代码、API、RunEvent、数据库/Flyway、SQL、安全配置、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 交付冰蓝色宣传套图与动态主图

### Why
- 用户要求宣传材料改用参考图的高明度冰蓝白、宝石蓝和柔紫色系，并将“运营可视化”替换为“跨资产库引用”；Agent & Skill Hub 还需表达对话经验经 `skill-creator` 总结优化并固化为团队 Skill。

### What
- 新增 `docs/assets/marketing/ice-blue/`：包含主图空白模板、增强后的价值表达页和六张统一场景页，能力为独立工作空间、多任务并行、子智能体协同、后台与定时执行、跨资产库引用、Agent & Skill Hub。
- Hub 页保持与其他场景一致的三证明点密度，突出 `skill-creator` 总结优化、对话经验自动固化和团队持续复用；跨资产库页覆盖统一检索、引用到当前任务及来源追溯。
- 将用户提供的 2894×1628、4 FPS、15.25 秒网页录屏原比例嵌入主图，生成 `00-overview-with-demo.gif`，同时保留可替换录屏的空白 PNG。

### How
- 使用用户参考图作为严格配色与版式参考，通过内置图像生成生成八张静态素材；使用 FFmpeg 8.0.1 合成动态主图，并以 `file`、`ffprobe`、SHA-256 和首帧预览校验尺寸、时长、帧数及嵌入位置。

### Result
- 最终交付 8 张静态 PNG 和 1 张动态 GIF；动态主图为 1672×941、61 帧、4 FPS、15.25 秒，无拉伸或裁切。未修改业务代码、API、RunEvent、数据库/Flyway、SQL、安全配置、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 自动恢复服务重启前运行的 OpenCode 进程

### Why
- 本地重启脚本会按设计停止 manager 管理的 `opencode serve` 并清理 state，但 manager 重连后此前没有恢复入口，数据库仍有 ACTIVE binding 的用户也必须手工点击“启动进程”。
- 首轮端到端验证还发现前端强状态查询可能在新 manager state 为空时抢先把原 `RUNNING` 写成 `STOPPED`，仅在配置心跳后扫描数据库会丢失运行意图。

### What
- 新增 `OpencodeProcessAutoRecoveryService`：manager 注册且尚未开放命令路由时冻结同容器 `RUNNING/STARTING + ACTIVE binding` 候选，完整配置应用后的首个运行心跳再异步执行；`STOPPED/FAILED/UNHEALTHY`、非活跃 binding 和无主进程不恢复。
- 控制 WebSocket 延后到完整配置心跳才登记可用连接；恢复复用 `UserOpencodeProcessAssignmentService.initialize` 和公共 `OpencodeProcessStartupService`，保留原容器/端口，单进程失败重试一次，候选扫描异常不阻断 manager 或 readiness。
- 补充 runtime/API 单测，并同步后端模块 README、HTTP API、部署和 AI 重启流程文档。

### How
- JDK 25 定向测试通过：runtime 11 项（自动恢复 3 项、manager 应用服务 8 项），API WebSocket 6 项；`git diff --check` 通过。
- 三次真实 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 用于暴露并修复构造器注入和状态探测竞态；最终 Maven 20 模块打包成功，脚本停止旧 4104 进程后自动拉起 PID 75596，日志为 `candidates=1 recovered=1 failed=0`。
- 最终 backend readiness 为 `UP`、frontend HTTP 200、OpenCode `/global/health` 为 `healthy=true/version=1.18.4`、登录后 `/processes/me` 为 `READY/RUNNING/4104`，CORS 预检为 200。

### Result
- 服务重启后，重启前仍有 ACTIVE 运行意图的用户 OpenCode 会默认自动恢复，不再要求手工启动；显式停止和失败态保持不启动。未新增或变更 HTTP/WS wire、RunEvent、数据库/Flyway、SQL、权限、安全配置、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 修复应用整提交同步空文件校验

### Why
- 应用 Agent 更新按钮按既定契约发送 `files: []` 表示合并整个版本固定提交，但后端仍调用旧逐文件校验并返回“同步文件不能为空”，企业侧因此出现 `VALIDATION_ERROR`，Git merge 和文件树刷新都没有执行。

### What
- `syncApplicationToPersonal` 允许空或缺省 `files` 进入整提交 merge；旧客户端传入非空路径时仍校验安全格式，但路径不缩小 Git merge 范围。
- 增加空列表同步固定提交的服务回归，并同步 workspace-management README 与应用 worktree 测试文档。

### How
- TDD 先复现 `PlatformException: 同步文件不能为空`，修复后 `ManagedWorkspaceApplicationServiceTest` 60 项全绿；JDK 25 整仓跳过测试打包成功。
- 使用未修改的 `.env.test` 和 test profile 重启 backend、opencode-manager、frontend；backend health/readiness 为 UP、前端 3000 和登录 CORS 正常，manager 最终健康。

### Result
- 应用个人 worktree 即使没有可选择的本地文件，也能通过左侧更新按钮合入 feature 固定提交并刷新 Agent/Skill/Tool 文件树；dirty 和冲突保护保持不变。
- 未新增或修改 HTTP/WS 字段、RunEvent、数据库/Flyway、SQL、权限、安全配置、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 修复重启后模型与新增工作空间目录不刷新

### Why
- 服务重启窗口内模型/Provider 目录可能先失败或返回空数组，前端会把空结果保留到整页刷新；工作空间异步创建成功后只刷新设置页内部列表，关闭设置时的模板刷新又可能早于 operation 终态。

### What
- 模型和 Provider 查询仅在目录为空或失败时每 3 秒自动恢复，非空后停止短轮询，并在窗口聚焦时刷新。
- 工作空间 operation 成功后按 ID 去重上报目录变更，经设置组件链通知 `AgentWorkbench` 失效并重拉左下角模板查询。
- 补充设置事件链单测、工作空间成功通知单测和模型空目录自动恢复的桌面/移动 mock E2E，同步前端 README 与包说明。

### How
- 前端定向单测 32 项、模型恢复 Playwright 2 项、全量 Vitest 1628 passed / 1 skipped、全 workspace lint、agent-web typecheck 和生产 build 通过；独立启动 Vite 验证实例 `127.0.0.1:3001`，页面和现有后端 readiness 均返回 200。

### Result
- 两个目录都无需整页刷新即可在后端恢复或异步创建完成后自动出现；未修改 HTTP/WS wire、RunEvent、数据库/Flyway、SQL、权限、安全配置、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 新增宣传主图与六能力独立 GIF 占位图

### Why
- 现有 MIMO 测试智能体宣传主图只展示五项能力，缺少 Agent & Skill Hub；用户还需要六项能力各自拥有一张可替换演示动图的独立宣传图。

### What
- 新增 `docs/assets/marketing/00-overview-web-gif-placeholder.png` 主图，在保留品牌主标题的同时以左右窄栏展示六项能力，并为 2894×1628 网页录屏预留大幅 16:9 安全展示区。
- 新增 `01` 至 `06` 六张独立能力图，分别对应独立工作空间、多任务并行、子智能体协同、后台与定时执行、运营可视化、Agent & Skill Hub；每张均包含能力文案和单独的空白 GIF 展示区。

### How
- 使用原宣传图作为品牌与版式参考生成七张静态 PNG；读取用户网页 GIF 的 2894×1628 尺寸后，将主图改为大画面优先布局，并通过逐图预览、`file` 与 SHA-256 校验落盘文件。

### Result
- 七张成品尺寸均为 1672×941；主图包含六项能力和一个宽幅网页演示区，六张独立图各含一个 GIF 占位区。未修改业务代码、API、RunEvent、数据库/Flyway、SQL、安全配置、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 修复应用配置手动同步与停用工作空间复用

### Why
- 企业多用户场景中，应用 Agent 根节点更新此前只调用 OpenCode `global dispose`，没有把应用版本固定 feature commit 合入当前个人 worktree；因此个人 `HEAD` 落后时 Diff 会显示待同步提交，但 clean 工作树没有任何可提交或回退文件。
- 设置页按同一 `应用 + 代码库 + 分支 + 目录` 保存工作空间时，后端受位置唯一约束会直接复用旧模板，却不应用本次别名或启用状态；命中已停用模板时进度可成功，但新名称不出现且菜单仍不可见。

### What
- 应用 Agent 更新复用既有 `sync-from-application` 固定提交 merge，成功后刷新 Agent、文件树和 Diff；当前用户进程 READY 时再 dispose，未启动时保留已完成的 Git 同步并由下次启动加载。dirty 或真实冲突继续交给现有 Diff/三方合并处理。
- 同位置工作空间保存改为更新原 `workspaceId` 的别名并重新启用，再继续确保对应版本工作区存在；前端提前识别同位置模板，明确展示“保存更新”或“保存并重新启用”，且别名重复校验排除正在复用的模板。
- 同步 workspace-management、HTTP API、agent-web、前端工程/包和 feature 测试文档；未新增接口、事件、数据库字段、Flyway 或关系型 SQL。

### How
- 后端 TDD 覆盖同位置已停用模板重命名并重新启用；`ManagedWorkspaceApplicationServiceTest` 通过。前端设置面板 17 项通过，应用更新 mock Playwright 在 Chromium/mobile 2 项通过。
- 前端全量 Vitest 96 个文件为 1627 passed / 1 skipped，13 个 workspace typecheck、生产 build、后端全模块 `clean package -DskipTests`、AI 文档校验和 `git diff --check` 通过。
- 按 `.env.test` / `test` profile 和 JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端 HTTP 200、登录 CORS 正常，manager WebSocket 已连接且无重连/解码循环。

### Result
- clean 个人 worktree 即使只有 `applicationUpdatePending` 也可通过应用 Agent 更新按钮真正追平 feature 提交；不同用户文件树只在其个人分支仍有未合并提交、dirty 或冲突时继续合理分化。
- 已停用的同目录工作空间可在一次保存中按新名称恢复可见，不再出现“进度成功但工作空间没建出来”的假象。未修改 `.env.local`、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 按适用性判断合并批量测试规约

### Why
- 批量任务 129 行来源规约中，120 行启用内容先前按 79 个不同名称拆成 79 张规则卡；虽然来源没有遗漏，但普通 Agent 需要重复完成过多相邻或互斥判断，长程任务失败风险偏高。

### What
- 不增加 Agent 或 Skill；把现有 `test-design/rules/batch-job.md` 按“同一触发条件、同一次适用性判断、同一组互斥分支”从 79 张卡合并为 29 张，79 个启用来源名称仍逐项保留在“必须分析”“最低覆盖”“来源规则”中。
- 测试设计启用卡总数由 173 调整为 123；附件 215 行启用规约及 173 个按对象去重的来源名称仍全部保留。批量的 9 行性能/生产安全规约继续保存在既有 `DEFERRED` rules，当前生成和 Review 不加载。
- 测试设计 Skill 升至 4.6.0；生成 Agent 命中合并卡后必须落实卡内全部适用子检查，Review 独立检查子检查、互斥分支、Phase A 绑定和最低覆盖。更新批量相关 eval，并新增空文件互斥分支审核场景。

### How
- 从原附件重新统计批量区为 129 行、88 个不同来源名称；79 个启用名称在 29 张活动卡中遗漏 0，9 个暂缓名称在性能/生产安全文件中遗漏 0。活动规则卡共 123 张、暂缓卡 45 张，编号连续，15 条 eval JSON 可解析，`git diff --check` 通过。
- OpenCode 1.18.4 从工作区和解压后的企业包分别成功加载测试设计入口、生成和 Review Agent；Skill Creator 的通用校验器仍只因不接受 OpenCode 专用 `compatibility` frontmatter 失败，未据此改坏运行时已接受的配置。
- 公共配置提交为 `160cf53`；企业替换包包含 61 个 Git 跟踪文件，均与该提交逐字节一致，且无 Git 元数据、依赖、系统文件或运行配置。

### Result
- 固定名替换包 `deploy/internal/dist/test-agent-public-agents-skills.zip` 已更新，SHA256 为 `73d33bf80af0a8f3617013eadd3619f79b33247e1979b883297867d8200f4e96`。
- 未执行企业模型长耗时行为评测，企业内上传、发布和在线任务回归仍需现场完成；未修改业务代码、HTTP API、RunEvent、数据库/Flyway、SQL、权限、安全配置、环境配置、generated SDK 或 OpenCode 源码，也未推送远程。

### 2026-07-27 - 按附件逐项补全测试设计公共规约

### Why
- 上一版把附件 261 行压缩为 52 张启用卡和 21 张暂缓卡，虽然保留了大类意图，但多个不同触发条件、不同预期和不同最低覆盖被并入一张卡，导致启动时可见规则明显少于附件。

### What
- 不新增 Agent 或 Skill；在现有 `test-design/rules/` 中按“内容保留优先”重写五类对象规约。215 行功能与可靠性内容仅合并同一对象下同名同义的重复行为，形成 173 张启用卡：其它 7、异步 21、UI 39、批量 79、接口 27。
- 46 行混沌、性能、安全和生产安全内容同样只做同名同义合并，形成 45 张 `DEFERRED` 卡并保存在既有四个暂缓文件；当前生成和 Review 仍不加载。
- 每张卡保留触发条件、必须分析、最低覆盖、排除条件和来源定位；删除附件中重复的提示词示例与具体案例示范，但不删除独立规则。`policyManifest` 允许理由完全相同的非命中项用 `ruleIds` 紧凑登记，命中项仍逐条绑定 Phase A 和案例，避免规则恢复后扩大调用链。
- 测试设计 Skill 升至 4.5.0，Review 同步展开 `ruleId/ruleIds` 核对完整编号；更新 README、质量门禁和 eval，新增多批量规则不得再次合并的合同场景。

### How
- 对附件逐行校验：261 行、按对象计 218 个唯一规则全部映射，遗漏 0；最终 173 张启用卡 + 45 张暂缓卡正好覆盖 218 个对象内唯一规则，所有编号连续且 eval JSON、`git diff --check` 通过。
- 使用本机 OpenCode 1.18.4 从工作区和解压包分别加载测试设计入口、生成、Review Agent 及 8 个测试设计 Skill；包内 61 个文件与公共配置提交 `bcc5bc4` 逐字节一致，ZIP CRC、禁带内容和 SHA 校验通过。

### Result
- 公共配置已提交为 `bcc5bc4`；固定名企业替换包 `deploy/internal/dist/test-agent-public-agents-skills.zip` 已重打，SHA256 为 `141ad535057a4f01dd73c902f443e2e0d75d2878eaf958eb879981404f23a1da`。
- 未运行长耗时企业模型行为评测，企业内上传、发布和在线任务回归仍需现场执行。本次不涉及应用业务代码、HTTP API、RunEvent、数据库/Flyway、SQL、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 修复测试设计协同并暂缓非功能规约

### Why
- 上一轮复核定位三个 P1：Review 交接缺少材料与结构检查字段、公共规则未直接追溯到 Phase A 中间物、仅设计/仅案例/直接审核没有明确短路由；同时用户要求混沌、性能、安全和生产安全三板斧当前不参与案例设计。

### What
- 复用现有测试设计入口、生成、Review Agent 和 `test-design` Skill，不新增 Agent/Skill；用单一 `requestedDeliverable=FULL|DESIGN|CASES|REVIEW` 选择最短链路，并补齐 Review 所需输入。
- `policyManifest` 为每条命中规则增加 `artifactItemRefs`，形成“规则卡 → Phase A 中间物项 → Phase B 案例”追溯；Review 独立检查绑定缺口。
- 把混沌、性能、安全、生产安全三板斧分别保存为 `rules/` 下四份 `DEFERRED` 规约，从其它、异步、UI、批量、接口活动规则和接口方法模板中移除专项覆盖；当前启用功能规则为 52 条。
- 更新测试设计 Skill 至 4.4.0，并把 eval 扩充为 13 条，覆盖三种短路由、Phase A 追溯失败和非功能规约暂缓。

### How
- 规则编号、路由/交接字段、方法 Skill 归属、暂缓边界和 eval JSON 合同检查通过；`git diff --check` 通过。OpenCode 1.18.4 从公共配置及解压包实际加载 12 个 Skill，入口为 `all`，生成/Review 为隐藏 `subagent`。
- Codex `quick_validate.py` 与旧公共 skill validator 分别拒绝项目既有的 `compatibility: opencode` 和缺少 `metadata.source`，因此没有修改已被 OpenCode 1.18.4 接受的既有 frontmatter；改用真实运行时加载验证。
- 公共配置提交为 `93aeb43`；从该提交重打 `deploy/internal/dist/test-agent-public-agents-skills.zip`，61 个文件与提交逐字节一致，包含 5 个 Agent、12 个 Skill且无禁带内容；包内 README 已同步短路由和暂缓规约口径。

### Result
- 三个 P1 已修复，非功能规约已独立保存但当前生成和 Review 均不加载；企业替换包 SHA256 为 `0d531e6f9f07e3a6a115f473c6d5f47abd67a2dbb3fabcac6ae6c535aa8a3b22`。
- eval 资产及静态合同已验证，未运行长耗时企业模型行为评测；企业内上传、发布和在线任务回归仍需现场执行。未修改应用代码、HTTP API、RunEvent、数据库/Flyway、SQL、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 清理公共配置无用文档并重打包

### Why
- 用户要求直接删除公共 Agent/Skill 配置中的无用文档，避免一次性验收文件和空目录占位进入企业替换包。

### What
- 删除无运行入口的 `public-git-worktree-acceptance-20260717.md` 历史验收 Agent，以及生成脚本、报文和格式校验 Skill 下 5 个空目录 `.gitkeep`。
- 保留 `test-design/evals/evals.json` 作为规则与 Review 回归测试资产；保留所有仍被 Agent、Skill 或模板引用的规约和模板。

### How
- 运行引用闭包检查，确认剩余运行时文档孤立数为 0、占位文件为 0；OpenCode 1.18.4 实际加载 5 个 Agent 和 12 个 Skill。
- 公共配置提交为 `c339a34`；从该提交重打固定名企业包，57 个文件与提交逐字节一致，ZIP CRC、禁带内容及解压加载校验通过。

### Result
- 企业替换包不再包含历史验收 Agent 和空占位文件；新 SHA256 为 `feb0202cd2d0aacccb6153fe7d72b9ee07a8ea4816731ec323996166c0dfb97f`。
- 删除项可从上一公共配置提交 `c287897` 恢复；企业现场上传、发布和在线回归仍待执行。未修改业务 API、事件、数据库、环境配置或 OpenCode 源码。

### 2026-07-27 - 补齐公共规则卡独立审核能力

### Why
- 公共规约已转换为 71 个 Agent 可执行规则卡，但 Review 仍只接收生成阶段声明的命中/排除项，无法确定每个对象是否评估了完整编号集合，也缺少规则到实际案例的明确引用。

### What
- 复用现有 `test-design` Skill 和 `rules/quality-gate.md`，不创建职责重复的 Review Skill；增加公共规则卡完整性基线和专项审核流程。
- 生成阶段按对象输出完整 `evaluatedRules`，逐卡记录命中、不适用、互斥或缺证据决策；命中项记录材料证据、最低覆盖和 `caseRefs`。Review 独立复核全部编号并输出 `publicRuleCoverageVerdict`、`reviewedObjectRuleSets`。
- 审核模板新增“公共规约命中、最低覆盖与排除”行，评测集新增批量规则漏评、错误重跑分支和最低覆盖缺失场景；测试设计 Skill 升至 4.3.0。

### How
- 71 个规则编号连续性、Review 契约字段、Evals JSON 和 `git diff --check` 校验通过；OpenCode 1.18.4 实际加载入口、生成与 Review Agent，生成/审核 prompt 均包含新契约。
- 公共配置提交为 `c287897`；从该提交重打固定名企业替换包，63 个文件与提交逐字节一致，包含 6 个 Agent、12 个 Skill且无禁带内容，解压后实际加载 12 个包内 Skill。

### Result
- Review 不再只相信生成阶段的命中清单，能够拒绝编号集合不完整、规则漏判/误判、互斥冲突和关键最低覆盖缺失的设计。
- 新包 SHA256 为 `44f2bde7a8441511f3bec6ea710da5942b5336445d200cc3b2b8cd9fab6cc8c6`；企业内实际上传、发布和在线回归仍待现场执行。未修改业务 API、事件、数据库、环境配置或 OpenCode 源码。

### 2026-07-27 - 重写公共测试设计规约并生成替换包

### Why
- 用户提供的公共规约附件按原始表格统计为 261 行，其中批量任务 129 行包含对账、上游、下游章节中的大量重复项；原始“属性/分类/要点/思考过程”结构不适合普通 Agent 直接判断触发和互斥。

### What
- 在独立公共配置仓库的 `opencode/skills/test-design/rules/` 中重写业务改造-其它、异步任务、UI 界面、批量任务和接口 5 类对象规约，将 261 行去重归并为 71 个“触发条件/必须分析/最低覆盖/排除条件”规则卡。
- 同步测试设计 Skill、生成/审核 Agent、规约索引、质量门禁、案例模板和评测期望；规约正文只归属 `test-design` Skill 的 `rules/`，Agent 仅负责按对象路由、生成和审核。
- 基于公共配置提交 `c4ba4f0` 生成固定名企业替换包 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 SHA256 文件。

### How
- 对附件做逐行映射校验：其它 13、异步 28、UI 47、批量 129、接口 44，共 261 行全部映射到 71 个规则卡，遗漏 0；5 个规则文件均无原始表格或“属性”标记，每个规则卡具备触发、分析和最低覆盖字段。
- `git diff --check`、Evals JSON 解析、ZIP CRC、63 个打包文件与公共配置提交逐字节比对、禁带内容检查均通过；包内包含 6 个 Agent、12 个 Skill，不含 `opencode.jsonc`、工具、依赖和 Git 元数据。
- 使用 OpenCode 1.18.4 从解压包实际加载 12 个包内 Skill；测试设计入口为 `all`，生成和审核 Agent 为隐藏 `subagent`。ZIP SHA256 为 `bf42888c75ac300160baef18da7d2682d4f5e6e2148c971c0312a7d2811feae5`。

### Result
- 公共测试设计规约已转换为普通 Agent 可直接执行的表达并进入企业替换包；未修改后端/前端业务代码、HTTP API、事件、数据库/Flyway、SQL、性能实现、安全边界、环境配置、generated SDK 或 OpenCode 源码。
- 企业内实际上传、发布与在线任务回归仍需在现场完成；本地已完成替换包内容和 OpenCode 加载验证。

### 2026-07-27 - 修复异常中断对话重试失效

### Why
- 对话被 `session.status=error` 等异常状态打断时，聊天卡片已经进入失败态，但平台 Run 可能仍为运行中；手动“重试”此前直接启动新 Run，容易与旧 Run 冲突。刷新或重新进入历史失败会话后，内存中的上一轮草稿也已丢失，按钮只能提示重新输入。

### What
- 手动与自动重试复用同一准备流程：隔离旧 Run、best-effort 取消仍忙的 Run，再复用原用户消息轮次启动新 Run，避免旧事件覆盖和重复追加用户消息。
- 历史会话加载后从最后一条持久化 USER 消息恢复正文、附件/上下文 PromptPart 与 slash command；无有效用户请求时保持明确提示，不从 assistant 或失败卡内容猜测。
- 补充工具函数单测、当前页/异常仍忙/历史重开三类桌面与移动端 E2E，并同步 agent-web README 和 RunEvent 前端处理文档。

### How
- 复用既有 `prepareAutoRetryRun`、消息去重和 PromptPart 归一化逻辑，只在工作台编排层合并入口；保留手动重试重新统计耗时/token、自动重试延续原请求累计的既有口径。
- 全量前端 Vitest 为 1625 passed / 1 skipped，agent-web typecheck 和生产 build 通过；相关 Vitest 233 passed / 1 skipped，三类 E2E 在 Chromium 与 mobile 均通过。并行冷启动时普通重试用例曾在 Vite 首屏编译阶段超时，单 worker 顺序复跑 2/2 通过。

### Result
- 异常中断、旧 Run 尚未终态以及刷新后重新进入历史失败会话时，“重试”均能重放原请求；按 test profile 重启 backend、manager、frontend 后，health/readiness 为 `UP`、前端 HTTP 200、CORS 正常、manager WebSocket 已连接。
- 未变更 HTTP API、RunEvent wire、数据库/Flyway、关系型 SQL、权限/安全边界、环境配置、generated SDK 或 OpenCode 源码。

### 2026-07-27 - 重打 Agent Skill Hub 企业完整包

### Why
- `d4f762f7c` 已完成 Agent & Skill Hub 的后端 API、MyBatis/Flyway、文件 WebSocket 和前端 Hub 交付，上一版企业包不包含这些变更。

### What
- 基于当前 `main` 全量重建 Java、前端、programs、`linux/amd64` worker/manager 和内层发布 ZIP；复用经 SHA 校验的 `.4/.114/.2` 节点配置包重封固定名双后台完整包。
- 打包脚本、`deploy/internal/.env` 和现场配置均未修改；`codex/apple-design-preview` 独立实验分支未纳入。

### How
- JDK 25 下执行全量 `package-release.sh` 和 `package-two-backend-complete.sh`；内外层 SHA、ZIP、核心制品逐字节比对、Hub Controller、Hub Flyway 迁移、RSA 和 160 个依赖库校验通过。
- Worker 镜像校验为 `linux/amd64`、OpenCode 1.18.4，Manager 协议字段、双后台封包回归、AI 文档校验和 `git diff --check` 通过。

### Result
- Agent & Skill Hub 前后端及数据库迁移已进入固定名企业完整包；现场需按 `.4 → .114 → .2` 完整升级，不能只更新前端。
- 本次未修改 OpenCode 源码、打包脚本或环境配置；真实企业部署、Flyway 执行和 Hub 登录态验收仍待现场完成。

### 2026-07-26 - 重构 Agent & Skill Hub 顶部与右侧抽屉布局

### Why
- 响应用户需求：Agent & Skill Hub 顶部原有的深色 Hero 区域占据较多纵向空间，Agent/Skill 制品列表在未选中资产时默认只占 57% 宽度，且右侧详情面板缺少滑出与显式关闭交互；资产卡片与详情页需要更直观地标识“原创应用”。

### What
- 移除 `.hub-hero` 渐变深色背景，升级为与 Workbench 风格一致的明亮顶栏 `.hub-header`，保留核心 Branding、四个分类统计徽章和“刷新目录”按钮。
- 优化顶栏与侧边栏折叠按钮 `[>]` 的避界排布，增加左侧 54px 安全间距，并将原较大蓝色块图标替换为干净精简的 32px 蓝色线框图标，消解按钮重叠与突兀感。
- 将资产目录 `.hub-catalog` 调整为全宽响应式网格 (`repeat(auto-fill, minmax(280px, 1fr))`)，资产卡片铺满整个视图空间。
- 修改资产详情面板为右侧滑出抽屉 (`.hub-drawer` / `.hub-detail-panel`)，增加顶部 `X` 关闭按钮、底部固定 Drawer Footer 栏 (`.hub-drawer-footer`) 与遮罩层点击事件；增加 32px 滚动底边距，消除截断感，选中资产时顺滑划出，关闭时回到全屏网格。
- 在资产卡片底部与抽屉顶部新增显式“原创应用”信息展示（`sourceAppName` / `sourceWorkspaceName` 或 `平台内置`）。

### How
- 修改 `AgentSkillHub.vue` 的 DOM 结构、CSS 动画及 `loadAssets` 初始状态处理逻辑（未选中时保持 `selectedAsset` 为 `null`，点击卡片显式滑出抽屉）。
- 同步更新 `agent-skill-hub.test.ts` 单元测试，补充点击资产卡片触发展示详情面板的交互流程。
- 执行 Vitest 单测 `pnpm exec vitest run tests/agent-skill-hub.test.ts`，5 个测试全数通过。

### Result
- 视觉上提升了 Hub 界面空间利用率，资产卡片平铺更全，选中查看详情时右侧抽屉滑动展示，并清晰标识能力原创归属应用。
- 未改动后端 API 契约、RunEvent 结构、Flyway 或环境配置文件；提交信息使用中文。

### 2026-07-24 - 重打进程启动时间修复企业完整包

### Why
- `23d256975` 已完成 Java 与 opencode-manager 的进程启动时间权威源修复，但上一条记录仍明确标记企业完整包待构建，旧包不包含新的 `startedAt` 控制协议。

### What
- 基于当前 `main` 全量重建后端薄 JAR、前端、programs、`linux/amd64` worker/manager 和内层发布 ZIP，并复用经 SHA 校验的 `.4/.114/.2` 三台节点配置包重新封装固定名双后台完整包。
- 打包脚本继续保持与既有基线一致，未修改 `deploy/internal/.env` 或现场配置；独立且有未提交实验改动的 `codex/apple-design-preview` 不属于本次交付。

### How
- JDK 25 下执行 `package-release.sh` 全量构建，再执行 `package-two-backend-complete.sh`；内外层 SHA、ZIP、内嵌发布 ZIP一致性、四类核心制品逐字节比对、JAR 内置 RSA 和 160 个依赖库均通过。
- 额外确认 runtime JAR 包含 `startedAt` 字段，worker 内 manager 二进制包含同名协议字段，镜像为 `linux/amd64` 且 OpenCode 为 1.18.4；双后台封包回归、AI 文档校验和 `git diff --check` 通过。

### Result
- 进程启动时间修复已进入固定名企业完整包；现场必须同步升级 `.4/.114` 的 Java 与 worker/manager，再部署 `.2` 前端，不能只替换前端或只升级 Java。
- 本次未修改业务源码、HTTP API、RunEvent 类型、数据库/Flyway、SQL、generated SDK、OpenCode 源码或环境配置；真实企业 x86_64/Docker 现场部署与 rollout 复查仍待执行。

### 2026-07-24 - 修复跨进程启动时间双时间源与存量 rollout

### Why
- 应用 Agent/Skill rollout 在目标服务器持续报“目标用户进程身份尚未收敛”；现场核对确认 manager 与数据库的端口、PID 相同，但 `startedAt` 相差约 2.8 毫秒，重启仍会复现。

### What
- 在入口规范和后端规范中明确：运行态身份的事件时间必须来自事件发生方，opencode `startedAt` 以 manager state 为唯一权威值，禁止用 Java 收到启动回包后的 `Instant.now()` 补造。
- 补充时区/微秒精度、PID 复用、旧协议有界兼容及双时间源测试要求，并加入完成前自检项。
- manager 的 `start` 命令结果新增权威 `startedAt`；Java 公共启动程序按 PostgreSQL 微秒精度落库，旧 manager 回包缺字段时从其即时心跳按完整进程坐标读取同一 state，结果和实时 state 均缺失才在写库前失败关闭，不再制造第二个启动时间。
- 应用范围 rollout 对旧版本遗留数据增加有界收敛：仅在服务器、container、端口、用户、PID 全部一致且 DB/manager 启动时间偏差不超过一秒时，按 manager 时间建立 target；其他不一致继续失败关闭。
- 同步 runtime/manager README、manager WebSocket 协议和企业部署排障文档；未新增 HTTP API、数据库字段、Flyway、SQL 或 OpenCode 源码修改。

### How
- 对照 `OpencodeProcessStartupService`、manager 启动回包和 `PublicAgentConfigRolloutService` 的精确身份匹配，确认当前启动结果只返回 PID，Java 随后独立取时，微秒归一化无法消除两个时间源的真实偏差。
- 用现场 `14098` 的真实差值 `2.803824ms` 增加 rollout 回归，并覆盖大于一秒仍拒绝、manager 时间纳秒到数据库微秒归一化、旧 manager 心跳兼容、结果和实时 state 均缺失时失败关闭及 Go 命令结果透传。
- Java 定向 139 项通过；runtime reactor 全量执行 724 项，仅命中主线已知且与本次差异无关的 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData`；Go 全包编译和新增 process/control 定向用例通过，完整 Go 运行测试受本机非项目 `opencode web` 占用 `4096` 影响。
- `verify-ai-docs.sh`、`git diff --check` 通过；按 test profile 重建并重启 backend、manager、frontend，health/readiness 为 `UP`、前端/CORS 正常，manager WebSocket 已连接。

### Result
- 新构建同时升级 Java 与 manager 后，新启动进程永久使用同一权威时间；旧 manager 心跳兼容保证企业标准“先 Java、再 worker”窗口不使用 Java 观察时间，也不会仅因回包缺少新字段而失败。
- 当前 `.4` 现场同 PID 的 2.8ms 存量偏差可由新 Java 的 rollout 重试自动收敛，无需手改 rollout/target/process 数据或再次重启用户进程；企业现场尚待构建新包并滚动部署后复查 rollout 状态。

### 2026-07-24 - 重打精简版公共 Agent/Skill 独立包

### Why
- 用户要求将刚完成流程精简的公共 Agent 配置重新打成可交付 ZIP，替换仍包含旧 `test-design-analysis` 和 `api-execute-case` 的历史包。

### What
- 从公共配置干净提交 `8cac11e` 重新生成固定名 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 `.sha256`；只收录 README、`opencode/agents/**`、`opencode/skills/**`。
- 新包包含 6 个 Agent、12 个 Skill、62 个文件；测试设计只保留 orchestrator、generation、review 三个 Agent，旧分析 Agent、旧“测试设计执行”名称和已废弃资源均未进入包。

### How
- 复用既有 `git archive` 固定前缀出包方式；在调用 OpenCode 前先冷解压并逐文件对比公共配置 HEAD，避免 OpenCode 校验时自动生成 `.gitignore` 干扰目录一致性检查。
- ZIP CRC、SHA、允许/禁止路径、12/12 Skill 校验、6/6 Agent 与 12/12 包内 Skill 的 OpenCode 1.18.4 实际加载均通过。

### Result
- ZIP 大小 84,314 字节，SHA-256 为 `f2a9eabbde31f320f8f89610df2a50c4ccce77b121b4e9d7f9e25ea854d149cf`；未修改应用代码、稳定工程文档、API、RunEvent、数据库、环境配置、OpenCode 源码或 generated SDK，企业现场导入与发布待执行。

### 2026-07-24 - 精简公共测试设计生成与审核链路

### Why
- 用户确认旧四段式不再使用，并要求先调整一稿当前公共区案例设计流程，减少 Agent 召唤、规约复读和重复门禁；同时明确内部 Agent 不应命名为“测试设计执行”。

### What
- 当前公共配置删除独立 `test-design-analysis`，保留原技术 ID `test-design-generation` 并命名为“测试设计生成”，在单个 Task 内依次完成事实分析基线、Phase A、冻结/确认和 Phase B；独立 Review 保留。
- 入口改为只派发 generation/review 两个 Task，材料按 manifest/路径按需读取；用一次 `policyManifest` 和一次 `reviewPolicyManifest` 取代三阶段 `ruleUsage`。
- 删除无消费者的 `skill-map.md` 和重复对象规格卡模板，同步 Skill 4.0.0、质量门禁、输出模板、eval、公共 README 与 AGENTS。

### How
- 公共配置提示词由 4 个 Agent 共 526 行收敛为 3 个 Agent 共 384 行；自动模式子 Agent 调用由 3 次降为 2 次。
- `quick_validate.py`、eval JSON、资源引用、权限、冲突标记和 `git diff --check` 通过；OpenCode 1.18.4 实际解析 generation/review 为隐藏 subagent、入口为可见 all，并确认已删除 analysis 不可加载、`test-design` Skill 可发现。

### Result
- 公共配置提交 `8cac11e`（`精简测试设计生成与审核链路`）已生成，未推送；旧 `temp/opencode-config` 历史检出目录未修改。未涉及应用代码、API、RunEvent、数据库、环境配置、OpenCode 源码、性能或安全契约。

### 2026-07-24 - 审查公共区测试设计 Agent 流程重复

### Why
- 用户反馈公共区案例设计链路过长，需要定位重复的 Agent 调用、规约读取和检查项，并在不削弱质量门禁的前提下提出精简方向。

### What
- 确认当前自动流程为 orchestrator → analysis → generation → review，manual 模式还会再次调用 generation；四个 Agent 提示词共 526 行。
- 定位三类主要重复：工作区/输出路径/skill-map 被各阶段重复读取，对象规约在分析、生成、审核三次读取，generation 全量自检与 review 独立门禁及 orchestrator 状态门禁职责重叠。

### How
- 对照公共配置 `ee8e978` 的四个测试设计 Agent、`test-design` Skill、阶段规则、模板和提交 `a773af7`；区分实际子 Agent 调用与同一 generation 内的方法 Skill A/B 调用。

### Result
- 建议保持“事实分析 → Phase A → 冻结/确认 → Phase B → 独立审核”的语义顺序，但把 analysis 与 generation 合并为一个设计执行 Agent，入口只解析/派发，Review 改为最小规则按需读取；自动模式可由 3 次子 Agent 调用降至 2 次。此次仅完成审查，未修改公共配置、应用代码、API、事件、数据库或环境配置。

### 2026-07-24 - 修复工作状态详情弹层被 Dock 裁剪

### Why
- 工作状态行的“探索/技能/待办/命令行”按钮点击后会进入 `is-active`，但输入框上方 Dock 使用 `overflow-y: auto`，详情层向上展开时被 Dock 顶部裁掉。

### What
- `WorkStatusRow.vue` 为 Dock 内详情层增加视口固定定位和 resize/scroll 重定位；普通时间线详情仍沿用原有相对定位。
- 增加 Dock 弹层回归测试，并同步 `frontend/packages/agent-chat/README.md` 的交互说明。

### How
- 定向 `opencode-timeline` 与 `FigmaChatPanel` 测试共 183 项通过（1 项既有跳过）；前端全量 Vitest 为 1617 passed / 1 skipped。
- agent-web typecheck、生产 build、`git diff --check` 通过；已在 `http://127.0.0.1:3000/` 登录态点击“探索”确认详情可见，backend health/readiness 均为 UP。

### Result
- Dock 内事件详情不再被滚动边界裁剪；未涉及 API、RunEvent、数据库、环境配置、性能或安全契约。

### 2026-07-24 - 排查企业用户 Ctrl+S 保存不一致

- Why:
  - 企业内部不同用户反馈编辑器 Ctrl+S 行为不一致，需要区分快捷键事件、工作区权限和文件写入链路。
- What:
  - 确认普通文件无 `currentPersonalWorkspaceId` 时前端按只读打开；应用版本副本、引用文件、公共/应用 Agent 配置还受个人 worktree 与角色权限限制。
  - 发现 `CodeEditor` 只在 Monaco 首次创建且 `readonly=false` 时注册 Ctrl/Cmd+S，切换文件时仅更新 model/readOnly，不补注册命令；而 `AgentWorkbench` 对 Monaco 目标会跳过全局 keydown，因此“先打开只读文件、再切可编辑文件”可能出现能编辑但 Ctrl+S 无动作。
- How:
  - 对照 `AgentWorkbench`、`CodeEditor`、文件 WebSocket handler 与现有测试；未修改业务代码，未启动服务或运行测试。
- Result:
  - 根因候选已收敛为前端 Monaco 命令生命周期与用户工作区/权限状态两类；需用浏览器事件日志和 WebSocket `workspace.write` / `agent-config.write` 帧做最终归因。
- Next:
  - 用同浏览器对比“可用用户/异常用户、同一文件、同一应用版本”，记录 `readonly`、个人 worktree、键盘事件和 WS 错误 traceId。

### 2026-07-24 - 真实验收聊天附件上传与 Run 透传

- Why:
  - 上一轮只有组件、构建和无认证页面验证，尚未确认登录态工作区 RPC 落盘及真实 Run 请求的 file part。
- What:
  - 在已登录工作台上传 73 B 合成文本附件并发起只读任务；原始输出确认 Run 请求携带 `type=file/name/mimeType/content`，OpenCode 事件恢复出同名 file part 与 73 字符 source。
- How:
  - 文件实际落到个人 worktree 的 `.testagent/attachments/codex-attachment-check-20260724.txt`，根目录没有同名文件；上传前后 SHA-256 一致。验收后将测试文件移动到 `/tmp/codex-attachment-check-20260724.uploaded.txt` 保留可恢复副本。
- Result:
  - 附件选择、工作区专用目录、附件卡片、发送清空及智能体 Run 透传均通过真实链路验证；测试 Run 后续因当前模型 `opencode/hy3-free` 不存在而失败，与附件链路无关。
- Next:
  - 单独校正默认模型/模型目录映射后可补做模型回答终态；附件功能本身无需追加代码。

### 2026-07-24 - 接通聊天附件上传并隔离工作区目录

- Why:
  - “上传附件”此前只有前端占位弹窗；附件需要复用工作区上传能力并作为当前任务的 file PromptPart 交给智能体，且不能把文件直接写入个人工作区根目录。
- What:
  - `FigmaChatPanel` 增加原生多文件选择/拖拽、上传中门禁、附件列表、移除和附件发送事件；`AgentWorkbench` 复用现有工作区分片上传、进度遮罩、撤销栈/Git diff 刷新与 `fileToPromptAttachment`，文件固定保存到个人 worktree 的 `.testagent/attachments`，普通发送和夜间任务均携带完整 file parts。
  - 同步更新 agent-web README/PACKAGE 与 FigmaChatPanel 回归测试；未新增 HTTP API、SSE 事件、数据库、SQL、generated SDK 或环境配置。
- How:
  - `vue-tsc`、agent-web 生产构建、`git diff --check` 通过；FigmaChatPanel/prompt-context/night-execution 定向测试 148 passed / 1 skipped。
  - 复用已运行的 `@test-agent/agent-web` localhost Vite 服务，`http://127.0.0.1:3000/` 返回 200；无认证的 Playwright smoke 只进入登录页，因此真实登录后的文件 RPC 上传与 Run 请求未做端到端探针。
- Result:
  - 附件不再落到个人工作区根目录，发送链路已接通并保留原文件附件内容；代码级验证通过，认证态上传链路仍需在真实登录页面补一次验收。
- Next:
  - 在已登录的工作台中选择一个小文件，确认 `.testagent/attachments/<文件名>` 上传成功、发送请求包含 `parts[].type=file`，并检查普通工作区上传未受影响。

### 2026-07-24 - 限制内部模型代理请求体为 2 MiB

- Why:
  - 企业定时任务在模型请求超过 Spring 默认 256 KiB 聚合上限后被错误映射为 500，且全局放大 WebFlux codec 会扩大 JVM 内存风险。
- What:
  - 内部模型代理改为端点专用 `2 MiB` 有界聚合：已知 Content-Length 超限时立即拒绝，未知长度仍由 `DataBufferUtils.join` 强制上限；请求体以 `byte[]` 校验并转发，避免额外整包 String 副本。
  - 新增稳定 `413 PAYLOAD_TOO_LARGE` 及 `details.maxBytes=2097152`，同步 API 文档和 API 包说明，不记录或回显模型请求内容。
  - 复核后将代理密钥校验和供应商同代快照解析前置到请求体订阅之前；顶层 `model` 改用 Jackson 流式扫描，并继续消费完整文档以拒绝尾部畸形 JSON 或额外根值，避免构建完整 JSON 对象树。
- How:
  - 定向 15 项通过，覆盖 300 KiB 正常转发、超过 2 MiB 的定长与 chunked 请求统一 413、无效鉴权/供应商先于大小检查拒绝、完整 JSON 扫描、SSE 和首响应超时；全量 `mvn test` 在 720 项后因两个无关既有用例失败，独立复跑后定时重试用例通过，`OpencodeProcessConfigLinkServiceTest` 仍稳定失败。
  - JDK 25 下完整构建并按 `.env.test`/`test` profile 重启 backend、opencode-manager、frontend，backend readiness 为 UP、frontend 3000 启动成功。
  - 真实进程探针确认未认证超限请求返回 `401 UNAUTHENTICATED`；本地数据库没有启用且已绑定 Token 的内部供应商，因此已认证请求会在供应商前置检查返回 400，未为探针修改数据库，真实 413 分支由带有效供应商快照的 WebFlux 端到端测试覆盖。
- Result:
  - 模型代理不再受全局 256 KiB codec 限制，单请求 JVM 聚合边界固定为 2 MiB；无效调用不再进入请求体聚合，有效调用也不再为 model 校验构建整棵 JSON 树。未修改 OpenCode 源码、环境配置、RunEvent、数据库/Flyway、SQL 或 generated SDK。
- Next:
  - 企业发布后观察 413 数量和模型请求大小分布；`OpencodeProcessConfigLinkServiceTest` 的现有失败另行排查。

### 2026-07-24 - 推送公共配置 master 到 Gitee

- Why:
  - 用户明确要求把本地已验证的公共 OpenCode 配置推送到 `git@gitee.com:huangzhenren/opencodeconfig.git`。
- What:
  - 将公共配置 `master` 的 8 个本地提交推送到 `origin/master`，包含 Agent/Skill 权限与名称整改、测试设计/执行规约归属调整、远端合并和自定义 Tool 兼容修正。
- How:
  - 推送前 fetch 确认为 ahead 8 / behind 0；推送后再次 fetch 并通过 `git ls-remote` 核对本地 HEAD、远端跟踪分支和 Gitee 实际引用。
- Result:
  - 三者均为 `ee8e978ea0af17a54a8fe3fe3651623e6c3798c7`，Gitee `master` 已更新成功。
- Next:
  - 企业内部同步 ZIP 后，按公共配置发布流程拉取该提交并重启相关用户 OpenCode 进程。

### 2026-07-24 - 生成当前企业公共 OpenCode 配置包

- Why:
  - 用户需要把当前公共 OpenCode 配置同步到企业内部，并确认本地提交是否已进入 Gitee 公共配置仓库。
- What:
  - 生成 `deploy/internal/dist/opencode-public-config-enterprise-current-full.zip` 及 `.sha256`；保留企业基线 `1ad3d20` 的 `opencode.jsonc`，叠加公共配置提交 `ee8e978` 的 README、AGENTS、7 个 Agent、12 个 Skill 和 4 个 Tool。
- How:
  - ZIP 共 106 个条目、97,778 字节，逐文件与两个来源提交一致；CRC、SHA-256、允许路径和敏感文件名扫描通过，未包含根 `agents/**` 运行快照、Session、认证/模型文件、`.env`、Git 元数据、依赖目录或已删除的 `api-execute-case`。
  - 从 ZIP 独立冷解压后，OpenCode 1.18.4 实际加载 12/12 Skill、7/7 Agent 和 4/4 Tool。
- Result:
  - SHA-256 为 `0878a6832bfbcf6e8c7f46ee3ea0186a19114e82e0053132b2abd35d1ff28fef`。公共配置本地 `master` 相对 Gitee `origin/master` 为 ahead 8 / behind 0，本轮未推送。
- Next:
  - 将 ZIP 和 SHA 文件一并传入企业内部中转机 `~/Desktop/mimoagent/0709`，校验后通过企业公共配置发布流程同步两台后台并重启相关用户 OpenCode 进程。

### 2026-07-24 - 将接口执行共享内容归回对应 Skill

- Why:
  - 用户指出不独立触发或执行的 `api-execute-case` 不应作为独立 Skill，其规则和模板应由实际使用它们的 Skill 持有。
- What:
  - 删除 `api-execute-case`；新增 `test-execution` 公共能力包，将请求动作、真实执行证据、断言和输出路径放入 `rules/`。
  - 将接口自动化脚本模板迁回 `generate-api-automation-markdown/templates/`，同步更新测试执行 Agent、报文生成、脚本校验、工作区规则和公共说明文档的引用。
  - 拉取公共配置远端最新 11 个提交并以 merge 保留双方历史；修正远端新增 `rpc-call` 对不存在模块、旧参数名、Zod record 和返回值契约的使用，并在公共文档中固化设计规约、执行规约和产物模板的归属原则。
- How:
  - OpenCode 1.18.4 实际加载 12/12 个公共 Skill，确认 `test-execution` 存在且 `api-execute-case` 不再加载；7/7 个 Agent 和 `*`、`task`、`todowrite` 显式权限均正常。
  - 81 处规则/模板引用、frontmatter、冲突标记和 `git diff --check` 校验通过；合并后 4 个自定义 Tool 均由 OpenCode 实际导入，`rpc-call` 失败返回契约实跑通过。
  - 使用 JDK 25、`.env.test`、`test` profile 完整构建 20 个 Maven 模块并重启三项服务，health/readiness、前端 HTTP、登录 CORS 和 manager WebSocket 正常。启动脚本按项目约定跳过测试。
- Result:
  - 公共配置仓库提交为 `fcc02ad`（`归并测试执行公共规则`）和 merge 提交 `ee8e978`（`合并远端公共配置并修正规约归属`），未推送。未修改 OpenCode 源码、应用 API、RunEvent、数据库/Flyway、SQL、generated SDK 或环境配置。
- Next:
  - None。

### 2026-07-24 - 升级本机 OpenCode 并统一公共 Agent/Skill 规约

- Why:
  - 公共 Skill 的名称、用途、实际调用 Agent 和串联时机不够直观；OpenCode 1.18.4 的 Task 子 Session 权限派生还会对 `task`、`todowrite` 做同名显式规则检查，只有 `permission."*": allow` 时可能被追加 deny。
  - 用户要求把本机 OpenCode 升级到 1.18.4 后测试，并复核 `api-execute-case` 是否为空壳、测试分析/设计/执行 Agent 逻辑和全部名称整改结果。
- What:
  - 在公共配置 README 中逐项说明 12 个 Skill 的加载 Agent、调用时机、产出和设计/执行两条调用链；用户可见名称调整为“生成接口测试案例”“文本理解生成”“生成接口自动化脚本”“生成接口自动化报文”，并具体说明 `api-execute-case` 负责请求/数据准备、平台 API/DB 调用、响应/数据库断言和执行证据。
  - 7 个 `opencode/agents/*.md` 统一显式声明 `permission."*": allow`、`permission.task: allow`、`permission.todowrite: allow`；技术 ID 与既有案例文件方法后缀保持兼容。执行报文文件名统一为 `<案例名称>-接口自动化报文.md`，脚本格式校验规则与当前输出命名一致。
  - `api-execute-case` 保留并明确为不独立执行的“接口执行公共规约”：其 Skill、输出路径和脚本模板共 137 行，另有 8 个 Agent/Skill/文档消费者；删除会使脚本、报文和格式校验重复维护同一契约。
  - 测试设计去除未被全部方法支持的隐式 `phase=full`，固定显式 Phase A → 确认/冻结 → Phase B；manual 恢复必须携带上一轮 `previousPhaseAResult`。测试执行新增 `GENERATE_SCRIPT`、`GENERATE_MESSAGE`、`EXECUTE_API`、`VERIFY_DB` 四类 `requestedActions`，产物生成与真实执行状态分离。
  - 从官方 1.18.4 release 下载并校验 macOS arm64 资产后，把 `/Users/kaka/.opencode/bin/opencode` 升级到 1.18.4；保留原 1.17.7 二进制和项目 1.17.8 layered shim 备份，项目 `.tmp/dev-bin/opencode` 改为指向当前官方二进制。
- How:
  - 核对 OpenCode 官方 Permissions/Agents 文档与官方 v1.18.4 标签的 `subagent-permissions.ts`、`task.ts`：官方文档仍将 `*` 定义为通用规则，v1.18.4 子 Session 派生实现只额外按同名显式规则检查 `task`、`todowrite`，未发现第三个需要同类显式补齐的权限；父 Session deny 和 `external_directory` 规则继续按运行时继承。
  - YAML/frontmatter、12 个 Skill 的名称/归属、7 个方法 Phase A/B、73 处规约/模板引用、旧术语、冲突标记、eval JSON 和 `git diff --check` 通过。官方 1.18.4 CLI 实际加载 7/7 个公共 Agent，并将每个 Agent 的 `*`、`task`、`todowrite` 都解析为 allow；`debug skill` 实际发现全部 12/12 个公共 Skill。
  - 使用 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；backend health/readiness 为 `UP`、前端 HTTP 200、manager WebSocket 已连接且启动环境的 `OPENCODE_BIN` 指向 1.18.4。本轮重启后当前用户 4104 进程未被 manager 管理，因此未把该端口写成已通过的端到端聊天验证；配置加载与 Agent/Skill 目录验证由 1.18.4 CLI 完成。
- Result:
  - 公共配置仓库提交为 `622769c`（`明确公共技能职责并补齐子智能体权限`）和 `4832776`（`统一测试设计与执行智能体规约`）；均未推送远端。未修改 OpenCode 源码、应用 API、RunEvent、数据库/Flyway、SQL、generated SDK、环境配置、性能或安全边界。
- Next:
  - 下一次用户真实进入工作台并触发 OpenCode 进程初始化时，可再观察 manager 是否以 1.18.4 拉起对应端口；当前静态目录、CLI 运行时加载和平台三服务已验证。

### 2026-07-24 - 固化 OpenCode 源码只读边界

- Why: 用户明确要求本项目严格禁止修改 OpenCode 源码，并将约束补入项目相关文档。
- What: 新增 `docs/standards/opencode.md`，同步更新入口规范、文档索引、研发工作流、自检清单、架构地图、OpenCode 升级说明、后端/前端 README 以及 AI 文档校验器。
- How: 统一约束 `opencode-source/opencode-1.18.4/` 仅作只读审计与行为参考；平台适配必须落在本项目自身边界，OpenCode 升级只能整体替换干净上游快照。
- Result: `tools/verify-ai-docs.sh`、文档差异检查与关键约束断言通过；未修改 OpenCode 快照、API、事件、数据库、generated SDK 或环境配置。
- Next: None。

### 2026-07-24 - 重封企业模型白名单修复完整包

- Why:
  - 用户要求在企业模型白名单读取修复提交 `199b27431` 后再次生成 `.4/.114/.2` 固定名企业完整包。
- What:
  - 复用该提交前已构建并验证的最新后端薄 JAR 与 160 个外置依赖，重封内层发布 ZIP 和 `test-agent-two-backend-complete.zip`；前端运行代码、programs、worker 和部署脚本自上次完整包后未变，因此复用原制品与既有三节点受控配置包。
- How:
  - 后端 JAR SHA256 为 `a1248d44f8ae69b1a1d49587c3490cd7a1bba9b665c6a9772f961dd979e2d965`，两个变更模块 JAR 均为 10:17-10:18 新产物且字节码包含 `/config` 与兼容 `/global/config`；前端、programs、worker SHA 与上次完整包一致。
  - 固定名完整包、双后台节点、内置 RSA、敏感信息脱敏、manager 日志兼容、MySQL 分离和 AI 文档校验通过；worker 未变，本轮不重复触发已知不稳定的 Mac Docker Desktop amd64/qemu smoke。
- Result:
  - 最新后端模型白名单修复、当前部署脚本、文档和全部 `.agents/session-log*.md` 已进入重新封装的固定名外层包；端口池仍为每台 `14096-15095` 共 1000 个。
  - 未修改业务源码、API、RunEvent、数据库/Flyway、SQL、generated SDK 或现场环境配置；企业仍需按 `.4` → `.114` → `.2` 完成真实 x86_64/Docker 18.09 和业务验收。

### 2026-07-24 - 修复企业模型白名单读取有效配置

- Why:
  - 企业 `opencode.jsonc` 已正确配置 `enabled_providers`，但部署 OpenCode 1.18.4 后仍展示 OpenCode Zen；上一版前端虽然已经按 Provider 白名单过滤，却从 `/global/config` 取得白名单，而该接口不会合并 `OPENCODE_CONFIG_DIR` 下的企业配置。
- What:
  - 平台模型目录使用的配置 GET 改为代理 OpenCode `/config`，读取当前 workspace 合并后的有效配置；Agent 标准兼容路由仍保留 `/global/config`，配置写入语义不变。
  - 保持按 Provider ID 动态过滤，不固定模型数量：`enabled_providers` 中每个企业 Provider 下的全部模型均可展示，未列入白名单的 `opencode`/Zen Provider 及其模型全部隐藏。
  - 同步 runtime、API、前端工程和 `backend-api` 文档，并用 OpenCode 1.18.4 的实际 `{data, location}` 目录响应补充回归。
- How:
  - 使用 `test-agent-opencode-worker:internal` 和企业示例配置实测：`/global/config` 无企业白名单，`/config` 正确返回两个企业 Provider；OpenCode 原生目录仍返回 24 个模型（其中 22 个 Zen）和 3 个 Provider，应用白名单后 Zen 数为 0。
  - Java 25 下相关后端测试 52/52 通过；前端全量 Vitest 1561 passed / 1 skipped、全仓 lint 和生产 build 通过。按 `.env.test` 重启后 backend health/readiness 为 `UP`、前端 HTTP 200、登录 CORS 正常、manager WebSocket 已连接。
  - 全量后端测试仍有既有 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData` 失败；失败代码来自提交 `3ab793a8b` 的 Windows 目录复制降级，与本次修改文件和模型配置链路无交集，本次未扩大范围修复。
- Result:
  - 企业环境继续只需维护 `enabled_providers` Provider 白名单，不需要固定两个模型或额外模型白名单；部署本次新后端后，前端已提交的目录过滤会只展示企业 Provider 模型。
  - 已生成后端替换产物 `deploy/internal/dist/backend/test-agent-app.jar`，SHA-256 为 `a1248d44f8ae69b1a1d49587c3490cd7a1bba9b665c6a9772f961dd979e2d965`；未重封完整企业 ZIP，也未部署 `.4/.114` 企业节点。
  - 本次仅调整平台配置 GET 的响应语义并同步 HTTP API 文档；未变更 endpoint、配置写入、RunEvent、数据库/Flyway、SQL、generated SDK、环境配置、性能或安全边界。

### 2026-07-24 - 基于当前代码重打双后台企业完整包

- Why:
  - 用户要求基于当前代码重新生成 `.4/.114` 双后台与 `.2` 前端共用的企业离线完整包，并继续复用现有三台节点受控配置包。
- What:
  - 基于当前主线 `a9144d200` 全量重建后端薄 JAR 与外置依赖、前端、programs、`linux/amd64` worker 镜像 tar 和内层发布 ZIP；外层继续固定为 `test-agent-two-backend-complete.zip`，端口池保持每台 `14096-15095` 共 1000 个端口。
  - 外层封装复用 `.4/.114/.2` 既有节点包，只刷新当前平台发布物、稳定部署脚本、手册和会话日志，不读取或输出节点包中的密码、token 等敏感值。
- How:
  - `package-release.sh` 全量构建通过；运行目录 Git 忽略、双后台节点/RSA/敏感信息脱敏/manager 日志兼容和 AI 文档校验通过。组件 SHA256：JAR `5f62dd3e3645c233786538b14446c1e735088a5d1f57cb8c6c7a963431aba575`，前端 `160bb969bace141ffa140e58bd740224e9b3f67639facf346cdfddf3f4f8fcbd`，programs `e5bdba13d71505b3ebea99929e69f00f7a93325b10bb02ca7032341065a454bb`，worker tar `432fb291af953d881acae74685ee5bb75499bf074fd471f79417b0770b6eb55b`。
  - 本机 Docker Desktop 的 `linux/amd64` qemu 探针先后出现 Node `fetch` 卡住和 `signal 6`；当前镜像曾返回 OpenCode 1.18.4 健康 200，上一版镜像在同环境完整通过优雅停止，但当前镜像的整套运行态 smoke 未能在本机稳定复跑，因此该项按部分验证记录，不能表述为全通过。
- Result:
  - 当前代码、部署脚本和会话日志已进入固定名外层完整包；外层 ZIP CRC、内外 SHA、固定目录、三节点包结构、节点规范化、敏感信息脱敏和 MySQL 分离校验均通过。包自身 SHA 只写入配套 `.sha256` 和交付回复，避免递归写入包内日志。
  - 未修改 Java/前端/manager 业务源码、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK 或现场环境配置；企业目标机仍需按 `.4` → `.114` → `.2` 顺序完成真实 Docker 18.09、1000 端口映射和业务验收。

### 2026-07-24 - 重打最新公共 Agent/Skill 独立替换包

- Why:
  - 企业内已经部署上一版本公共配置，用户要求重新提供包含本地最新公共 Agent 与 Skill 的独立 ZIP，不重打整套应用。
- What:
  - 以公共主线提交 `d002a60` 为底座，在临时 worktree 合入已提交的公共 Skill 创建/优化变更 `07993d3`，保留六个测试 Agent 的全量权限和取消固定步数上限，同时加入 `skill-creator`、`skill-optimizer` 1.1.0 及 14 个 Skill 的双语来源信息。
  - 覆盖生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 `.sha256`；包内只含公共 README、`opencode/agents/**`、`opencode/skills/**`，未包含 provider 配置、`opencode.json(c)`、Git 元数据、依赖目录或四个未跟踪个人验收样例。
- How:
  - 复用历史固定名 `git archive` 出包方式；校验六个测试 Agent 均为 `permission == {"*": "allow"}` 且无 `steps`，14 个 Skill 全部通过公共 `skill-creator` 1.1.0 校验器。
  - ZIP CRC、允许路径、逐文件 tree 对比均通过；OpenCode 1.17.7 从独立解压目录发现 14/14 个 Skill，并逐个加载 7/7 个 Agent。
- Result:
  - ZIP 包含 7 个 Agent、14 个 Skill、69 个 Skill 文件，SHA-256 为 `40fb1613f9bee79b2b95f55147ae20e8f9967b289e43a6c6a574496a4bf4c8ae`。
  - 本次仅刷新公共配置替换包，不修改应用代码、稳定工程文档、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK、环境配置、性能或安全边界；企业现场只需校验并导入/发布新公共配置，无需重打应用包。
- Next:
  - 企业现场导入与发布待执行；发布后按平台既有公共配置排空/热加载流程验证 7 个 Agent 与 14 个 Skill。

### 2026-07-23 - 补齐 Agent/Skill 双语存量与创建后热加载

- Why:
  - 公共级和应用级 Agent/Skill 已能展示中文，但存量配置、创建/优化 Skill 的公共技能以及配置树新建后的运行态刷新口径不完整；用户要求英文目录和 OpenCode 原生识别保持不变，并让重新输入 `@`/`/` 能看到最新目录。
- What:
  - Agent 配置树的创建、上传、复制、移动、改名和删除事件新增当前个人 workspace/worktree/server 路由；命中 `opencode.jsonc`、`agents/*.md` 或 `skills/*/SKILL.md` 时复用编辑器保存已有的当前用户 dispose、busy 排队和 Agent/Command 重拉程序。默认新建 Agent 仍为 `primary`，不改变 `@` 只列 `subagent/all` 的原生规则。
  - 更新前端工程文档和用户手册，明确中文主展示、英文辅助展示、英文技术 ID、完整拼音回退、`source` 来源语义和重新输入候选行为。
  - 在四个已登记配置工作树补齐双语说明与 Skill `source`；公共 `skill-creator`/`skill-optimizer` 升级到 1.1.0，创建时生成英文目录/顶层 name 与双语 metadata，优化时保留已有真实 source。对应配置提交为 `375bb35`、`07993d3`、`b128f31`、`0fa45bb`；`public-usr_test_dev` 原有四个未跟踪用户样例保持未修改。
- How:
  - 复用 `refreshRuntimeCatalogAfterAgentConfigSave`，没有新增 OpenCode 代码、扫描器或第二套热加载路径；多文件 Skill 模板只选择 `SKILL.md` 触发一次刷新。存量本地 Git 配置继续兼容双语 description、Skill metadata 和旧的目录/名称回退。
- Result:
  - agent-web typecheck 通过；相关 Vitest 为 176 passed / 1 skipped；`AgentConfigApplicationServiceTest` 49/49 通过；两个公共技能自身校验通过，OpenCode 1.17.7 能发现当前 14 个相关 Skill、逐个发现历史 18 个 Skill，并加载当前/历史/应用 Agent。
  - Codex 通用 Skill validator 因不接受 OpenCode 合法的 `compatibility` 字段而不适用，本轮以项目校验器和 OpenCode 原生 debug 结果为准。
  - 按 `.env.test`、JDK 25 重启三服务，backend health/readiness 为 `UP`、前端 3000 为 HTTP 200、登录 CORS 正常、manager WebSocket 已连接。本次不涉及 HTTP API、RunEvent、数据库/Flyway、generated SDK、环境配置、鉴权或安全边界。
- Next:
  - None。

### 2026-07-23 - 优化服务器工作空间选择器窗口交互

- Why:
  - 超级管理员的服务器工作空间选择器尺寸固定，目录较多时处理空间不足；用户还要求像用户手册一样在普通 Chrome 标签页继续处理，并明确不能使用 `about:blank` 或在 URL 暴露服务器、本机目录。
- What:
  - 选择器新增视口内右下角拖拽/方向键缩放、页面内全屏与还原；新标签页改为真实工作台 URL `/?serverWorkspacePicker=1`，不传 popup 尺寸参数。
  - 当前服务器和目录通过同源 `sessionStorage` 交接，新标签页首次加载和刷新均自动恢复；浏览器策略阻止新标签页时保留原页面并明确提示。
  - 同步前端工程 README、app README、包级说明和模块地图；未使用晚于 Chromium 108 基线的 CSS 能力。
- How:
  - 定向 Vitest 5/5、`vue-tsc --noEmit` 和包含 VitePress 的生产 build 通过；生产构建继续使用项目既有 Chromium 108 target。
  - 按 `.env.test`、JDK 25 启动本地三服务，health/readiness 与前端 HTTP 正常；Playwright 真实 Chromium 验证缩放、全屏、真实 URL 新标签页、目录交接和刷新恢复。
- Result:
  - 地址栏只保留入口标记，不再出现 `about:blank`、服务器 ID 或本机目录；本次不涉及 HTTP API、RunEvent、数据库/Flyway、generated SDK、权限边界或环境配置。

### 2026-07-23 - 企业模型目录按运行配置隐藏 OpenCode Zen

- Why:
  - 企业包已经在 `opencode.jsonc` 配置 `enabled_providers`，但 OpenCode 1.18.4 原生 Model/Provider 目录仍可能返回 Zen 内置供应商，导致模型选择器同时展示公网 Zen 与企业内部模型。
- What:
  - 复用 `backend-api` 既有 runtime config、models 和 providers 请求；当运行配置存在非空 `enabled_providers` 时，按 Provider ID 同时过滤模型与供应商目录。Model/Provider 并发加载共用同一轮 config 请求，请求结束即清理，以兼顾配置热加载；白名单未配置或 config 暂时不可读时保留原生目录兼容。
  - 新增 Zen/企业模型混合目录过滤、未配置白名单兼容回归，并同步前端工程及 `backend-api` 包级文档。
- How:
  - `backend-api` 定向 Vitest 84/84、全量前端 Vitest 1556 passed / 1 skipped、全仓 lint、生产 build 均通过；按 `.env.test`、JDK 25 重启本地三服务后 health/readiness、前端 HTTP、登录 CORS 和 manager WebSocket 均正常。
  - 执行企业 `package-release.sh --frontend-only --no-zip`，确认编译资源包含 `enabled_providers` 过滤逻辑并生成前端离线归档。
- Result:
  - 企业配置继续沿用现有 `enabled_providers` 即可只展示企业白名单模型；未改 HTTP API、RunEvent、数据库/Flyway、generated SDK、环境配置、鉴权或安全边界。
  - 前端离线归档为 `deploy/internal/dist/test-agent-frontend-dist.tar.gz`，SHA-256 `313be3a8a466f41353e91ee1d1c6d9dcf367ec69a0517425c1eab17df53028ca`；本次只生成前端产物，未重封完整企业 ZIP，也未部署企业节点。

### 2026-07-23 - 收敛双后台为千端口并防护旧 Docker 代理耗尽

- Why:
  - `.4` 的 Docker 18.09.7 在默认 `userland-proxy` 下发布 `14096-16095` 时，到第 536 个端口报 `iptables ... fork/exec ... resource temporarily unavailable`；实测 Docker service/parent cgroup、内核 PID 和内存均未到上限，问题是旧 daemon 为每个端口创建代理时的瞬时进程风暴。
- What:
  - 双后台端口池改为 `14096-15095` 正好 1000 个端口，与 `OPENCODE_MANAGER_MAX_PROCESSES=1000` 一致；同步节点校验、封包、XXL 诊断、全量/多后台手册和 enterprise skill。
  - `opencode-worker-docker.sh` 在 Docker 18.09 启动千端口池前必须确认 daemon 已配置 `"userland-proxy": false`；不满足时在删除现有 worker 容器前失败，不再运行到一半才耗尽资源。
- How:
  - 为 worker 脚本扩展临时 fake Docker 回归，覆盖默认代理的失败停止点和禁用代理后的 1000 端口同号映射；执行开发脚本、双后台节点、完整包、XXL 诊断、AI 文档和 Shell 语法校验。
- Result:
  - 相关回归全部通过；Mac 固定名完整包已重封并从实际 `.4` 节点包确认 env、部署脚本和内层 worker 前置校验。现场仍需按维护窗口先优雅停止 `.4` PostgreSQL、禁用 Docker userland proxy、重启并恢复 PostgreSQL，再验证 worker 1000 端口；未清理任何数据或 manager state。

### 2026-07-23 - 迁移双后台 OpenCode 端口池到 14096-16095

- Why:
  - 企业 `.4` 后台启动 worker 时，旧端口池内的 `4118` 已被宿主机其它进程占用，Docker 因此报 `bind: address already in use`；用户明确要求两台后台不再使用旧端口，统一迁移到 `14096-16095`。
- What:
  - 复用现有双后台封包、节点部署和 worker 启动链路，把 `.4/.114` 节点包的 `OPENCODE_WORKER_PORT_START/END` 固定为 `14096/16095`，宿主机与容器仍保持同号映射。
  - 同步双后台部署校验、完整包校验、XXL 诊断提示、全量升级手册、多后台手册、部署入口和 enterprise skill；封包回归额外确认复用旧敏感节点包时，包内部署脚本和手册也会替换为当前版本。
  - 每台 worker 提供 2000 个端口坐标，但页面通用参数 `OPENCODE_MANAGER_MAX_PROCESSES` 仍固定为 `1000`，不未经压测直接翻倍单机进程容量。
- How:
  - 运行变更 Shell 语法和 `git diff --check`，执行多后台节点、固定名完整包、XXL 诊断及 AI 文档验证；从重封完整包中实际解出 `.4` 节点包，核对 env、脚本和 Docker 首尾端口验收逻辑。
- Result:
  - 双后台节点、完整包、XXL 诊断和 AI 文档回归全部通过；固定名完整包已在 Mac 重封并通过 SHA-256、ZIP CRC 和节点内容校验，企业现场仍需先在 `.4`、再在 `.114` 逐台原地改配置和重建 worker。
  - 不清理 `/data/testagent/data`、manager state 或用户数据；不修改 Java、HTTP API、RunEvent、数据库/Flyway、generated SDK、鉴权或 `.env.local`。

### 2026-07-23 - 固化 Redis Docker IPv4 转发前置检查

- Why:
  - 企业 `.20` 的 Redis 7 容器、本机 TCP 和 `0.0.0.0:6379` 均正常，但 `.4` 持续连接超时；抓包确认 SYN 已到 `.20` 且无 SYN-ACK，最终定位为 `net.ipv4.ip_forward=0` 阻断 Docker DNAT 转发，这类 Docker 网络问题现场已重复发生。
- What:
  - 复用现有 `deploy-redis.sh deploy/verify` 入口，在接触 Docker 前读取 `/proc/sys/net/ipv4/ip_forward` 并拒绝值不为 `1` 的宿主机，给出临时恢复和企业 sysctl 持久化提示，不自动修改系统网络策略。
  - 同步 Redis 离线手册、全量升级手册、企业部署入口、后端部署文档和 enterprise skill，固定“`.20` 本机验证后仍从 `.4/.114` 跨机验证”的停止点，并区分 `ip_forward`、`FORWARD/DOCKER-USER` 与上游 VLAN/ACL。
- How:
  - 扩展 Redis 部署 verifier，动态覆盖 `deploy/verify` 在 `ip_forward=0` 下都提前失败；离线包 verifier 和 AI 文档校验强制检查新脚本及手册内容。
- Result:
  - Shell 语法、Redis 部署脚本、Redis 离线包、AI 文档和 `git diff --check` 全部通过；未改 API、事件、数据库、环境配置或 Redis 数据，当前现场仍需由系统管理员把 `.20` 的转发值持久化并完成 `.4/.114` 跨机验证。

### 2026-07-23 - 修复 Redis 配置 bind mount 的 Linux UID 权限错误

- Why:
  - 现场绕过旧 Docker `--platform` 报错后，Redis 容器又因宿主机敏感配置保持 `0600`、容器内 `redis` 用户无法读取 bind mount，报 `can't open config file ... permission denied`。
- What:
  - 复用现有 `run_redis_container`，保留宿主机配置 `0600`，容器启动时先复制到容器临时文件、改为 `redis:redis` 所有权，再用镜像内 `/usr/bin/setpriv` 切换到 redis 用户；同步更新 Redis/全量手册、企业部署 skill 和包/脚本回归。
  - 现场可先把原配置复制为 `/data/testagent/redis/config/redis.conf`，设为 `0400`、`999:1000` 后用该 `--config-file` 原地恢复，不改原包文件权限。
- How:
  - Shell、部署/封包、AI 文档校验通过；真实 `0600` 配置 bind mount + Redis 5 双 DB RDB→Redis 7 AOF 转换、重启、key 核对、GETDEL 和停止均通过。
- Result:
  - 权限问题已在脚本层解决，不需要开放配置到 `0644`；本次按现场要求暂不重封或重新导入包，后续生成新 Redis/平台包时必须携带本次脚本版本。

### 2026-07-23 - 修复 Redis 离线部署对旧 Docker experimental platform 的依赖

- Why:
  - 企业 `.20` 在 Redis 5 RDB 转换阶段再次出现 `"--platform" is only supported on a Docker daemon with experimental features enabled`；现场为 `x86_64`，镜像已校验为 `linux/amd64`，运行期 `--platform` 属于重复约束并阻断旧 daemon。
- What:
  - 复用现有 `run_redis_container`，移除目标机 `docker run` 的 `--platform`，保留部署前镜像 OS/架构强校验；封包脚本在 Mac 拉取和构建阶段仍固定 `linux/amd64`。
  - 部署脚本、Redis 手册、全量手册、企业部署 skill 和回归测试同步固定旧 Docker 兼容规则；一并修正无密码 Redis 仍导出空 `REDISCLI_AUTH`、盘点遗漏 `/etc/redis/6379.conf` 的现场问题。
- How:
  - Shell 语法、Redis 部署/封包和 AI 文档校验通过；真实使用 Redis 5 双 DB RDB 运行修正后的脚本，完成 Redis 7 AOF 转换、重启、2 个 key 核对、GETDEL 和停止清理，全程未传运行期 `--platform`。
- Result:
  - 旧 Docker daemon 不再需要开启 experimental features；现场失败发生在容器创建前，已确认无同名容器、数据目录只有原 `dump.rdb`，可在替换脚本后原地重试。Redis 敏感包通过 `--zip-only` 保留原密码和镜像重封，最终哈希以配套 `.sha256` 为准。

### 2026-07-23 - 修复 XXL 平台 SSO 通用错误页二次异常

- Why:
  - 企业浏览器在 `/xxl-job-admin/platform-sso/login` 报 `window.parent.$.adminTab` 未定义；确认该脚本来自上游通用错误页，是平台 SSO 服务端异常后继续产生的二次前端异常，会掩盖真正首因。
- What:
  - `PlatformXxlSsoController` 统一接管运行时异常，记录不含票据、用户或令牌的结构化完整异常，并返回平台自有 `503 + unavailable` 状态页；补充 MockMvc 回归，覆盖票据消费等登录步骤异常时不再落入上游错误页。
  - 同步 XXL 集成 README、测试说明和企业排查手册；在前端编码规范固定 Chromium 108 最低基线，并要求跨窗口全局对象逐级判空、iframe SSO 统一使用平台状态页和 `postMessage`。
- How:
  - JDK 25 下运行 `mvn -pl test-agent-xxl-job-integration -am -DskipITs test`，37 个 XXL 集成测试通过，包含真实 Tomcat 子上下文和 MySQL 8.4 Testcontainers；`bash tools/verify-internal-xxl-job-diagnostics.sh` 与 `git diff --check` 通过。
  - 按 `.env.test` 和 `test` profile 重启本地三服务，health/readiness、前端 3000、登录 CORS 与 manager WebSocket 均正常。
- Result:
  - 平台 SSO 再发生服务端运行时异常时不再复现 `adminTab` 二次报错，并可用稳定日志标记回查首因；本次不修改上游源码，不涉及 API、RunEvent、数据库结构/SQL、generated SDK、性能或权限边界，安全上仅新增脱敏错误日志。

### 2026-07-23 - 修正 XXL 排查手册的外部 MySQL 拓扑

- Why:
  - 现网 XXL MySQL 已切换到外部 `122.210.106.43:3306/xxl_job`，但随后新增的排查手册和诊断脚本仍硬编码旧 `.148`，现场探测因此产生无效 TIMEOUT 结论。
- What:
  - 统一修正排查手册、入口/后台诊断脚本、专项 verifier、测试说明及排查设计/计划；明确外部 MySQL 不在平台节点部署容器，DBA 只从获准客户端执行只读 SQL。删除企业部署入口中“当前 XXL MySQL 由容器脚本管理”的冲突表述。
- How:
  - 复用既有三套只读诊断脚本和 `verify-internal-xxl-job-diagnostics.sh`，同步正常/错误地址夹具、证据文件名和安全负向契约；全仓稳定部署文档及工具扫描不再出现旧 `.148`。
- Result:
  - `bash tools/verify-internal-xxl-job-diagnostics.sh`、相关 Shell `bash -n`、`git diff --check` 均通过；不涉及 API、RunEvent、数据库结构/SQL、generated SDK、性能或凭据变更。

### 2026-07-22 - 原始输出倒序跟随与主思考面板滚动

- Why:
  - 用户要求原始输出按时间倒序且新记录默认可见，并修复只有主智能体思考面板无法向下查看的问题，同时保留子智能体既有聊天区滚动。
- What:
  - `FigmaChatPanel` 复用会话级有界缓存，按 `occurredAt` 倒序派生展示、筛选和下载；打开浮层或新增记录时回到顶部。`agent-chat` 只给主智能体使用的 `.oc-work-status-dock` 增加 `min(360px, 50vh)` 限高与纵向滚动，子智能体 inline 时间线不变。
  - 同步 frontend、agent-web、agent-chat、PACKAGE、前端规范和模块地图，并补充排序不变性、展示/下载顺序、实时置顶及 dock 样式回归。
- How:
  - 定向 Vitest 3 文件 181 passed / 1 skipped、全量 lint、两个目标包 typecheck、前端生产构建和 JDK 25 后端 20 模块跳过测试构建通过；按 `.env.test` 重启三服务，health/readiness、前端 3000、CORS、manager WebSocket 与 Vite 实际服务源码正常。
- Result:
  - 两项交互已实现且运行服务已加载最新代码；全量 Vitest 仍有任务外存量 `DirectoryRows` 用例把实际 `radio`“上传”按 `button` 查询而失败。Browser 插件因本机 `Cannot redefine property: process` 未完成自动化点击复验；不涉及 API、RunEvent、数据库、SQL、generated SDK、安全或兼容性契约。

### 2026-07-22 - 基于公共 Agent 发布修复重打四节点完整离线包

- Why:
  - 用户尚未部署上一包，要求按当前最新 `main` 重新打固定名企业完整包，并继续提供 `.147/.4/.114/.2` 的逐台执行和验证清单。
- What:
  - 从干净 worktree 的 `52b732848a6f89d3216e0deb074b6b2f94350b6c` 全量重建后端 JAR、同源前端、programs、Linux/amd64 worker 和 MySQL 8.4 镜像；复用上一未部署包中已校验的四节点敏感配置，避免数据库、Redis、MySQL 密码和 XXL token 漂移。
  - 固定名外层 ZIP 覆盖到 `deploy/internal/dist/` 与 `/Users/kaka/Desktop/qr-decode/out/`；包内继续保留 JAR 内置 RSA、顺序 Flyway `V20260722130000`、`.4` 首节点延后 peer 校验和四份小于 1 MiB 的节点包。
- How:
  - 运行 MySQL、多后台节点、完整包和 AI 文档回归；逐层校验外层/内层 SHA 与 CRC、四节点 SHA/大小、MySQL 大 ZIP 校验、RSA、Flyway、最新前端诊断文案和两个 Docker tar 结构。
- Result:
  - 外层 ZIP SHA256 `5c8770f43bd22c0a619b7dd1e8c4e2557b7505338adf786ba3f60275b6998af5`，内层 release `107f5a4a87187966ddbcd5476342859172504aa659aa170ec01ae719fc359c32`，JAR `fb4b56f8a6733bb7675d7a5536752eed1a65f14a7efec3bafe5959a924d332e4`，前端归档 `c442d60502c9e86e3192a80f2a3e85aa79fc9fe2041b06e0613534feb81affd4`。
  - Mac 构建和封装验证完成；企业现场尚未部署，必须按 `.147 MySQL -> 停两台旧 Java -> .4 -> .114 -> .2` 顺序执行并完成真实 systemd、Docker、Nginx 与浏览器验收。

### 2026-07-22 - 重打公共 Agent 排空优化双后台固定名包

- Why:
  - 用户要求基于当前最新代码重新生成企业双后台完整离线包；上一包基线为消息门禁 SQL 修复提交，尚未包含后续文件分片上传和公共 Agent 发布排空优化。
- What:
  - 从干净 worktree 的 `98866da441379aed145c4d05460739214726861b` 重建后端 JAR、用户手册和空 API base 同源前端，复用上一包已验证的 `.4/.114/.2` 受控配置并覆盖固定名完整包。
  - `86423b4e2..98866da44` 未修改 worker、programs 或部署脚本，因此复用已验证的 Linux/amd64 worker/programs；JAR 继续内置 RSA，节点 env 不配置外置 RSA 路径。
- How:
  - 运行 Nginx、单机配置、自动节点、多后台节点、固定名封装和 AI 文档回归；最终校验内外层 SHA/压缩完整性、构建产物逐字节一致、三节点 `--validate-only`、systemd 首装/升级和配置归档小于 1 MiB。
- Result:
  - 外层 ZIP SHA256 为 `58525ea01f83a4ac4b8aeed209b442cddd3d08f2372539ee2403051e1446b4cb`，内层发布 ZIP 为 `e1c222fe6412d16f4340534fef3a8a9a5ec6826d7b65ea06e034d200d042be56`，JAR 为 `601914150ba8e5fc5b3a03a0fee77849a75acd7bcdce95d9cc5a4e655a12939f`，前端归档为 `27743b611903974d03003e3117c1f282e59e86ad6bb8b109ca5b2952c5a8dd87`。
  - `.4/.114/.2` 节点配置包分别为 `22705/22703/20536` 字节；Mac 构建与封装验证完成，企业现场仍需按 `.4 -> .114 -> .2` 执行真实 systemd、Docker、Nginx 部署和业务验收。
  - 本次只更新交付记录；工作区原有 `.agents/skills/restart/SKILL.md` 修改保持未暂存、未覆盖。

### 2026-07-22 - 直连 ICBC personal OpenAI-compatible 行内模型配置

- Why:
  - 用户要求不修改业务代码，仅通过 OpenCode JSONC 和运行环境配置切换到 ICBC `personal/v1` OpenAI-compatible 接口；上游模型由令牌绑定。
- What:
  - 将本机当前及两个开发配置快照中的 `icbc-openai` provider 切换到 `/icbc/jdt/model/api/openai/personal/v1`，请求头改为原始 `Authorization`，模型配置仅作为 OpenCode 的 provider/model 路由标识。
  - 同步本机实际被 OpenCode 1.17.8 读取的 `~/.config/opencode/opencode.jsonc`；该版本临时实例验证表明其实际加载全局配置，而不是 manager 传入的 `OPENCODE_CONFIG_DIR` 配置目录。
  - 令牌未写入仓库、JSONC 或日志；JSONC 引用受管启动链路已有的 `TEST_AGENT_INTERNAL_PROXY_API_KEY` 环境变量。
- How:
  - 使用 OpenCode `debug config` 解析校验，并启动临时 4196 端口实例检查 `/api/provider` 和 `/api/model`；provider、路由模型和新基地址均已出现。
  - 按项目启动脚本尝试重启三服务，使用非敏感占位值验证 manager 启动链路；后端因 PostgreSQL 连接 `EOFException` 启动失败，未完成真实上游请求验证。
- Result:
  - 配置层已部分验证；真实 ICBC 调用仍需在数据库恢复后，将新令牌安全配置到 `TEST_AGENT_INTERNAL_PROXY_API_KEY` 并重启受管 OpenCode。用户提供的令牌已在会话中暴露，后续应先申请/轮换新令牌。

### 2026-07-22 - 取消六个测试 Agent 的固定迭代步数上限

- Why:
  - 测试设计和测试执行子智能体可能在业务工作未完成时达到 `steps` 上限，被 OpenCode 强制转为文本总结；Task 返回后主智能体会继续接手。
- What:
  - 删除测试设计四个 Agent、测试执行两个 Agent 的 `steps`，保留 `permission."*": allow` 和主智能体现有接手能力；按用户决定暂不增加程序级完成门禁。`test-design` 版本提升到 `3.8.4`，公共配置 README 同步。
  - 从公共配置提交 `d002a60` 重新生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 SHA，覆盖上一版替换包。
- How:
  - 只修改现有 Agent frontmatter，不新增代码或编排包装层；使用 OpenCode 1.17.7 检查六个解析结果均为 `steps=None`，并复验外部规约读取和 Skill 加载。
- Result:
  - 六个 Agent 不再因固定迭代步数被强制结束；模型仍可自行结束，用户仍可中断，平台请求和 Run 基础设施超时不变。
  - ZIP 与提交逐文件一致，包含 7 个 Agent、12 个 Skill（Skill 目录共 59 个文件），完整性、禁止路径和凭据特征扫描通过；SHA256 为 `e9adbd2f3b2f8ff0a6dd10a251c6fd4802b0544501708baa984745e942947121`。
  - 未修改 API、RunEvent、数据库、SQL、generated SDK、Java/前端代码或环境配置；企业现场替换和 worker 重启仍待执行。

### 2026-07-22 - 基于消息门禁 SQL 修复重打双后台同源完整包

- Why:
  - 企业 `.4`、`.114` 是同源克隆节点，用户要求同一缺陷两台一起处理，禁止分别使用不同版本 JAR 或依赖库。
- What:
  - 从修复提交 `86423b4e2` 全量重建后端、同源前端、programs 和 Linux/amd64 worker，复用两台后台及前端既有受控节点配置，覆盖固定名双后台完整包并同步到 `deploy/internal/dist/` 与 `/Users/kaka/Desktop/qr-decode/out/`。
- How:
  - JDK 25 下运行 `package-release.sh` 和 `package-two-backend-complete.sh`；校验外层/内层 ZIP、节点配置保留、两后台共享配置与 manager token 一致，并直接解包确认 mapper 只含 `pw.app_workspace_version_id`、不含 `pw.version_id`。
- Result:
  - 外层 SHA256 `f0fe2cc58f638122ac7c333511f65ab996ba4a0f239364ffbc127a50e5af4b37`，内层 release `4ce670d99d0a243a8dd72b19e571b2bf5c65d044bb47ae62bb28bc2e4c7574ae`，app JAR `159480cd0051eab9b6f1b57d29add3d246f5a51cd5f3fd760b222d309baf1f55`；Mac 构建验证完成，企业两节点滚动部署与 Docker IPv4 forwarding 修复仍待现场执行。

### 2026-07-22 - 落实初始化进程按钮缺失的 SQL 修复

- Why:
  - 本地三服务均在运行，但前端没有显示 TestAgent 进程初始化入口；后端日志显示 `/processes/me/message-gate` 因 `personal_workspaces` 不存在 `version_id` 列而失败。
- What:
  - 将 `PublicAgentConfigRolloutMapper.xml` 的个人工作空间关联改为 `pw.app_workspace_version_id = v.version_id`；新增 XML 绑定 SQL 回归断言，并同步 persistence README 的字段关系说明。
- How:
  - 复用既有 MyBatis mapper、用户状态查询和宠物初始化入口，不改 API、事件、数据库结构或环境配置；提交前回顾全部 session log，保留工作区原有的 `.agents/skills/restart/SKILL.md` 修改。
- Result:
  - 定向 MyBatis 测试 6/6、前端 FigmaShell/FigmaChatPanel 182 通过/1 跳过；JDK 25 下 18 模块生产构建和 `.env.test` 三服务重启成功，health/readiness、前端 3000、CORS、manager WebSocket 均正常；重启后无新增该 SQL 错误。
- Next:
  - None

### 2026-07-22 - 诊断企业后台升级日志中的 SQL 与停机心跳异常

- Why:
  - 企业节点执行离线部署后同时出现消息门禁 PostgreSQL 错误、停机阶段 `LettuceConnectionFactory has been STOPPED` 和 Docker IPv4 forwarding 告警，需要区分业务缺陷与部署收尾噪声。
- What:
  - 消息门禁 `findBlockingRolloutId` 误用不存在的 `personal_workspaces.version_id`，真实外键是 `app_workspace_version_id`；部署脚本主动停止旧服务后，Redis lifecycle 已停止但 5 秒心跳尚未销毁，晚到心跳才产生 Lettuce 错误。
- How:
  - 对照 V9 表结构、既有 `SessionHistoryMapper`、`PublicAgentConfigRolloutMapper.xml`、部署脚本 stop/start/health 顺序和心跳销毁时机；本地已有的字段修正及防回归测试并非本次创建，定向 6 项测试通过。
- Result:
  - 新 Java health/readiness、worker health 和 manager 配置均正常，但当前企业坏包的消息门禁仍可能阻断新消息；必须交付包含正确 SQL 的新 JAR。Lettuce 堆栈不是启动失败，Docker `ip_forward=0` 且无持久出站 unit 仍需网络侧处理。

### 2026-07-21 - 隔离公共与应用配置发布并异步收敛个人 worktree

- Why:
  - 企业双后台中一次应用 Agent 发布因个人 worktree 存在本地修改或合并冲突长期停在 `RETRY_WAIT`，旧的全局唯一活动 rollout 同时阻止无关公共配置拉取；直接初始化不经过该门禁，因此形成“初始化可更新、拉取持续报正在排空”的设计缺陷。
- What:
  - rollout 活动锁改为公共范围独立、应用范围按版本 ID 隔离；应用个人 worktree 未收敛时持久化为独立补偿任务，服务器共享副本仍可标记 `SYNCED` 并完成主 rollout，不再占用公共发布或其他应用版本。
  - 新增 worktree 认领、租约、重试、等待用户、同步和放弃状态；后台在用户提交/丢弃本地修改或解决冲突后自动合入固定目标提交，再只登记该用户旧进程的延迟 dispose。已完成应用 rollout 仍可处理后到 target，门禁只影响对应应用成员和对应用户。
  - 新增 PostgreSQL Flyway `V20260721213000`，SQL 全部落在 MyBatis XML；同步 runtime/persistence README、HTTP/事件语义、数据库/后端部署文档和应用 worktree 测试案例。
- How:
  - 复用既有 rollout coordinator、个人 worktree 原生 Git 合并、进程快照与 dispose 状态机；不扫描 Redis 私有快照、不新增跨服务器文件代理、不覆盖或重置用户本地修改，也不手工改存量 rollout 数据。
- Result:
  - 定向回归 `ManagedWorkspaceApplicationServiceTest` 57 项、`PublicAgentConfigRolloutServiceTest` 24 项、MyBatis rollout 仓储 5 项全部通过；相关 reactor 中 workspace、runtime 等业务模块全量通过，persistence 全套仍被旧迁移 `V20260717173000` 的 `timestamptz` 与 H2 不兼容基线阻断 76 项，与本次迁移无关。
  - JDK 25 下 18 模块生产打包、前端生产构建通过；使用 `.env.test` / `test` profile 启动 backend、opencode-manager、frontend，Flyway 已把真实 PostgreSQL 更新到 `20260721213000`，readiness 为 `UP`、前端返回 200。
  - 涉及数据库结构与发布/门禁兼容语义；未新增 HTTP API 或 RunEvent 类型，未修改 generated SDK、鉴权、安全边界或环境配置。真实企业双节点现场待两台 Java 同版部署后由定时任务自动接管原 `acr_8c2caacc30954278812ca915a4062b5b`。

### 2026-07-21 - 修正测试 Agent 为无 deny 的全量权限并重打替换包

- Why:
  - 上一版只放行 `external_directory` 和 `skill`，仍保留测试设计顶层 `"*": deny`、敏感文件读取限制及 Task 白名单，测试执行也保留 `ask`，不符合企业内部测试“无需限制任何 deny、直接全量放行”的明确要求。
- What:
  - 测试设计四个 Agent 与测试执行两个 Agent 的 permission 统一精简为唯一的 `"*": allow`，移除全部 `deny`、`ask` 和按工具/子智能体白名单；`test-design` 版本提升到 `3.8.3`，公共配置 README 同步真实权限口径。
  - 从公共配置提交 `f8a2ff6` 重新生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 SHA，覆盖上一版替换包。
- How:
  - 复用 OpenCode 原生通配权限，不新增运行时代码；使用 OpenCode 1.17.7 在临时业务目录分别验证六个 Agent 的外部规约读取与公共 Skill 加载，并检查解析结果包含通配 allow。
- Result:
  - 六份 YAML frontmatter 均精确解析为 `permission == {"*": "allow"}`；12 个 Skill 元数据和相对规约引用有效。
  - ZIP 包含 7 个 Agent、12 个 Skill（Skill 目录共 59 个文件），与提交逐文件一致，压缩完整性、禁止路径和凭据特征扫描通过；SHA256 为 `fb68054de0a73854bb0a3337ea6c7ca26667f02fdd54a56619a17117f8cb8dc5`。
  - 未修改 API、RunEvent、数据库、SQL、generated SDK、Java/前端代码或环境配置；全量权限会允许六个 Agent 使用所有工具、读取外部目录及原先被保护的环境/OpenCode 配置文件，仅适用于用户指定的企业内部测试环境。

### 2026-07-21 - 全量放行测试 Agent 外部目录与 Skill 并生成替换包

- Why:
  - 企业内除测试设计外，测试执行 Agent 也需要稳定读取公共 Skill 的 rules/templates；用户明确要求测试 Agent 的外部目录与 Skill 调用全量放行，并提供可批量替换的 ZIP。
- What:
  - 测试设计与测试执行共六个 Agent 统一为 `external_directory: allow`、`skill: allow`，Task 编排白名单保持不变；`test-design` 版本提升到 `3.8.2`，公共配置 README 同步。
  - 生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 SHA，只包含公共 README、`opencode/agents/**`、`opencode/skills/**`，不包含 `opencode.json(c)`、tools、node_modules、Git 元数据或 provider 配置。
- How:
  - 复用 OpenCode 原生 Agent permission 与公共配置 Git，不改运行时代码；从公共配置提交 `fd9fad5` 直接 `git archive`，避免把未提交文件或密钥带入包。
- Result:
  - 六个 Agent 真实读取外部 Skill 资源及加载任意公共 Skill 均 6/6 通过；12 个 Skill frontmatter 和相对 rules/templates 引用通过。
  - ZIP 包含 7 个 Agent 文件、59 个 Skill 文件，逐目录 `diff`、压缩完整性、禁入路径和凭据模式扫描通过；SHA256 为 `b116d03f01bd2f8d1d61748962e2b81b24dd225e825dfd65cafe85ee06db295e`。
  - 未修改 API、RunEvent、数据库、SQL、generated SDK、Java/前端代码或环境配置；全量放行扩大了六个 Agent 的外部文件读取和 Skill 调用范围。

### 2026-07-21 - 修复企业测试设计 Agent 读取公共规约权限

- Why:
  - 企业内案例设计时，测试设计主 Agent 及三个阶段 Agent 能加载 `test-design` Skill，但读取其 `rules/`、`templates/` 时被提示外部目录权限受限。
- What:
  - 公共配置仓库的四个测试设计 Agent 显式设置 `external_directory: allow`，保留 `.env` 与 `opencode.json(c)` 的 read deny；`test-design` 版本提升到 `3.8.1`，README 同步权限约束。
- How:
  - OpenCode 1.17.x 权限按最后匹配规则生效，Agent 自身的 `permission."*": deny` 会覆盖运行时为公共 Skill 目录生成的外部目录 allow；按用户确认对四个 Agent 全量放行 external directory。
- Result:
  - 四份 YAML frontmatter 解析通过；本机 OpenCode 1.17.7 在临时业务工作区中分别以四个 Agent 真实读取公共 `rules/workspace-layout.md`，4/4 通过。
  - 完整后端构建被工作区中并行未提交的 `WorkspaceFileService` 上传重构缺失符号阻断；未修改该任务外代码，改用既有 JAR 重启 backend、manager、frontend 成功。本次未改 API、RunEvent、数据库、SQL、generated SDK 或环境配置；全量 external directory 放行扩大了四个 Agent 的文件读取范围。

### 2026-07-21 - SCM 跳转改为 HTTPS 并补个人 worktree 回收指引

- Why:
  - SCM GMP 权限申请需要改用 HTTPS；同时用户确认移除应用成员后服务器个人 worktree 仍保留，需要明确安全回收方式。
- What:
  - 权限申请弹框及桌面/移动端回归统一改为 `https://scm-gmp.sdc.cs.icbc/icbc/gmp/index.jsp#@`。
  - 明确成员删除只撤销 `application_members`，保留个人工作区、运行态 Workspace、历史 Session 和物理 worktree；在后端部署文档增加按用户/应用只读定位、停止用户进程、检查 dirty 状态和使用无 `--force` 的 `git worktree remove` 回收磁盘步骤。
- How:
  - 复用现有 HTTPS 新窗口跳转及 default worktree 缺失修复能力；不在成员删除入口自动清理，因为该入口无法安全处理未提交内容、多服务器归属、活动进程和历史归属，也不建议现场删除数据库记录或直接 `rm -rf`。
- Result:
  - Git 权限 Playwright 桌面/移动端 2 项、agent-web typecheck 和生产构建通过；`.env.test` / `test` / JDK 25 重启三服务后 health/readiness、前端 3000、CORS 和 manager 日志正常。
  - 未新增或修改 HTTP/RunEvent/数据库/SQL/generated SDK/环境配置；只澄清成员删除既有语义及磁盘回收运维步骤。

### 2026-07-21 - 基于拉取后最新代码重打双后台固定名包

- Why:
  - 用户在拉取主分支最新代码后要求重新打包；打包期间又提交了版本库权限申请直达 SCM 的前端改动，因此需以最终最新提交重新生成企业离线交付物。
- What:
  - 后端从拉取后的 `0fb851e157ee2758662cd73b7fe964a724da0ae1` 隔离构建；确认后续 `80b250e6c03cf2605b86feca93ed497cea43b435` 只修改前端和文档后，从该最终提交重新构建用户手册及空 API base 的同源前端，覆盖固定名 `test-agent-two-backend-complete.zip` 及 SHA。
  - 复用 `.4/.114/.2` 三份受控节点配置；worker/programs 源码相对上一交付基线未变化，因此复用已验证的 Linux/amd64 产物。JAR 继续内置 RSA，节点 env 不配置外置 RSA 路径。
- How:
  - 运行 Nginx、单机配置、自动节点、多后台节点、固定名封装和 AI 文档回归；最终执行内外层 SHA/压缩完整性、构建产物逐字节比对、当前部署脚本同源、三节点 `--validate-only`、systemd 首装/升级及节点配置小于 1 MiB 校验。
- Result:
  - 外层 ZIP SHA256 为 `ecb5c84b6a77dfee89e1a2ff07100dacf69cdee84f4cb21409f938c0d455e59e`，内层发布 ZIP 为 `d4f7adac8ccf77dbf4411c7ab4df8d500ac4b8cd68f86fdd8b25824a2035b4ea`，JAR 为 `bb236df73f4116b3ff5d11aa9025616b7ffbee3ff59f0cb448b5d303124f6fb4`，前端归档为 `8cd225d94374e4c6c70b3c09843896cf280dfcec54a2f3fcf2c431b600fb722a`。
  - `.4/.114/.2` 节点配置包分别为 `22411/22411/20387` 字节；本地构建与封装验证完成，企业现场仍需按 `.4 -> .114 -> .2` 执行真实 systemd、Docker、Nginx 部署和验收。
  - 本次只更新交付记录，不修改 API、RunEvent、数据库/Flyway、generated SDK、环境配置或业务代码。

### 2026-07-21 - 版本库权限弹框直达 SCM GMP

- Why:
  - 版本库权限预检弹框原先只写“前往开发者门户”，用户无法从弹框直接进入企业 SCM 权限申请页面。
- What:
  - 无版本库读取权限时展示 SCM GMP 地址 `http://scm-gmp.sdc.cs.icbc/icbc/gmp/index.jsp#@`，将确认按钮改为“前往申请”并复用现有 `window.open(..., "_blank", "noopener,noreferrer")` 外链方式；取消后仍停留在当前工作区且不创建 worktree。
  - 同步 agent-web README/PACKAGE，并扩展桌面/移动端回归验证地址、按钮和安全新窗口参数。
- How:
  - 仅扩展既有 `ElMessageBox` 权限分支，不新增 API、路由或导航封装；同时复核现有应用成员可在设置页按人逻辑删除，且该平台成员权限与 SCM 仓库权限相互独立。
- Result:
  - Git 权限 Playwright 2 项、agent-web typecheck/生产构建、设置页成员管理 Vitest 15 项、成员服务 23 项及跨模块撤权 1 项通过。
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启三服务；health/readiness 为 UP、前端 3000、CORS 和 manager 日志正常。不涉及 HTTP/RunEvent/数据库/SQL/generated SDK/环境配置变更。

### 2026-07-21 - 应用版本选择前增加 Git 权限预检

- Why:
  - 用户选择应用版本时，原流程会直接创建或切换个人 worktree；若当前用户没有关联版本库权限，只能在后续 Git 操作失败后获知，且提示不够明确。
- What:
  - 新增版本 Git 访问预检接口，按当前用户的仓库地址和 SSH key 只读探测远端；认证失败或仓库不可访问返回申请版本库权限结果，缺少 SSH key 返回独立配置提示，网络及超时仍按统一 Git 异常处理。
  - 前端在版本选择产生任何 worktree 副作用前调用预检；无权限时弹框展示具体版本库名称并引导前往开发者门户申请，缺少 key 时引导至个人设置，校验通过后才沿用既有默认个人工作区流程。
  - 同步 workspace-management、API、backend-api、agent-web、HTTP API 与模块地图文档，并补齐后端服务、Controller、API 客户端及桌面/移动端交互回归。
- How:
  - 复用 `GitRemoteService.listBranches()`、当前用户唯一 SSH key、内部仓库有效 URL 和既有 Java 路由；不创建第二套 Git 命令、不返回仓库地址或密钥，也不改 generated SDK、事件或数据库。
- Result:
  - 后端聚焦 71 项、backend-api 78 项和 Playwright 桌面/移动端 2 项通过；backend-api/agent-web typecheck、agent-web 生产构建、后端完整跳过测试打包及 `git diff --check` 通过。
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常、manager WebSocket 已连接且当前日志无新错误。
  - 新增一个兼容性的只读内部 HTTP API；不涉及 RunEvent、数据库、SQL、环境配置或权限模型变更。每次显式选择版本会增加一次 Git 远端只读探测。

### 2026-07-21 - 强制企业 Git 提交显式传入身份

- Why:
  - 企业 SCM 会校验提交者邮箱；提交服务仍保留不传身份的兼容入口时，后续调用可能退回服务器 Git 默认配置并再次生成无法推送的提交。
- What:
  - 删除 `GitWorkspaceService` 和 `GitPublishWorkflow` 中所有缺省提交身份的兼容重载及空值回退，所有可能生成 commit 的提交、合并和发布入口统一要求非空 `GitCommitIdentity`。
  - 补充空身份失败关闭、身份透传及真实 Git 作者/提交者邮箱回归测试，并同步 common、workspace-management 模块稳定文档。
- How:
  - 继续复用现有 `GitCommitIdentity.forPlatformUser`，只对单次 Git 命令注入当前操作人身份，不修改仓库或全局 Git 配置；未新增 API、事件、数据库字段或迁移。
  - 定向 145 项、common/domain/workspace 全量 395 项测试及后端 18 模块跳过测试打包通过。
- Result:
  - 新代码无法再通过缺省入口创建使用服务器默认邮箱的提交；应用 Workspace/应用 Agent 旧失败提交不需要数据库迁移，升级后可由发布流程重新投影并生成正确身份的 feature 提交。
  - 企业存量公共 Agent 个人 worktree 若含尚未推送的 `@testagent.local` 提交，仍需逐仓库备份并重建提交后再发布；远端已拒绝的提交不在远端历史中，不需要强推或迁移远端数据。

### 2026-07-21 - 修复高行数文件 WebSocket 帧误关闭

- Why:
  - 文件 WebSocket 单帧上限只按上传 Base64 的 4/3 膨胀估算；文本保存经过 JSON 序列化后，换行或控制字符会进一步转义，导致仍在 1 MiB 业务上限内的高行数文件先被传输层关闭，前端只能看到 WebSocket 关闭。
- What:
  - 共享 WebSocket adapter 改为按文本 JSON 控制字符最坏 6 倍转义量加 64 KiB RPC envelope 配置单帧上限，同时覆盖 Base64 上传；UTF-8 或解码后文件的 1 MiB 默认业务限制保持不变。
  - 新增基于实际 Jackson 序列化结果和实际 Reactor Netty server spec 的控制字符、高行数文本回归测试，并同步 API 与模块稳定文档。
- How:
  - 继续复用现有 route/ticket/RPC、`WebSocketHandlerAdapter` 与 `WorkspaceFileService` 大小校验，没有新增文件 HTTP 代理、分片协议或前端旁路。
  - 定向 `TerminalWebSocketConfigTest,WorkspaceFileWebSocketHandlerTest` 19 项及 `test-agent-api -am` 全量 340 项测试通过，后端 18 模块跳过测试打包成功，`git diff --check` 通过。
- Result:
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常、manager WebSocket 已连接。
  - 未修改 API 字段、事件类型、数据库、generated SDK、环境配置或文件权限边界；默认全局 WebSocket 单帧上限由约 1.40 MiB 调整为约 6.06 MiB，文件业务上限仍为 1 MiB。

### 2026-07-21 - 应用与公共 Agent 支持全部暂存

- Why:
  - Git Changes 仅应用 workspace 有“全部暂存”，应用 Agent 与公共 Agent 仍需逐文件操作。
- What:
  - 两类 Agent 未暂存分组新增“全部暂存”，一次提交当前作用域全部未暂存路径；无权限、冲突或 index 更新中禁用，并补齐进行中防重复状态、组件回归和稳定文档。
- How:
  - 抽取并复用单文件暂存程序，继续调用既有 `stageWorkspaceAgentFiles` / `stagePublicAgentFiles` 批量 API，不新增后端接口、配置分支或跨作用域状态。
- Result:
  - GitChangesPanel 38 项、agent-web typecheck、用户手册与生产构建、后端 18 模块跳过测试打包通过；`.env.test` / `test` 重启后三服务 health/readiness、前端 3000、CORS 和 manager WebSocket 正常。
  - 前端全量 Vitest 为 1448 passed / 1 skipped / 1 failed；唯一失败是既有 `DirectoryRows` 用 `button` 查询实际 `radio` 角色的“上传”，单独复跑稳定复现，与本次改动无关，未扩大范围修复。

### 2026-07-21 - 重打包含用户安全删除的双后台固定名包

- Why:
  - 用户要求再次重打企业双后台完整包；最新业务提交新增用户安全删除与 TCDS 信息同步，需要替换上一版 `106f8b3dc` 构建物。
- What:
  - 从干净 worktree 的 `680a2a298` 重新构建后端 JAR、用户手册和前端，复用上一包已校验的 `.4/.114/.2` 受控配置，覆盖固定名 ZIP 与 SHA。
  - Nginx 配置继续使用 `TEST_AGENT_NGINX_SERVER_ROUTES` 精确路由，JAR 继续使用内置 RSA；worker/programs 源码未变化，复用已验证的 linux/amd64 交付物。
- How:
  - 运行 Nginx、单机配置、自动节点、多后台节点、固定名封装和 AI 文档回归；最终执行内外层 SHA/压缩完整性、三节点 `--validate-only`、systemd 首装/升级、脚本同源、JAR 内置 RSA 和配置大小校验。
- Result:
  - 外层 ZIP SHA256 为 `6aa61be641b734640ec518b4bfa1bcb20c6551356da0c72ee7c62076da91bb6c`，内层发布 ZIP 为 `f43369d4ef28bb81f3e649ec51cb6095c3f63dd38c9faa799ad7bf7847cb1b42`，JAR 为 `f50666006b268116b7e08ab029bbd869da1d0f94436ccc0c1242982cdabda435`。
  - `.4/.114/.2` 配置包分别为 `22406/22407/20380` 字节；本地验证完成，企业现场仍需按 `.4 -> .114 -> .2` 部署和验收。

### 2026-07-21 - 交付 Nginx 精确路由双后台固定名离线包

- Why:
  - 用户完成 Nginx 配置改造后，需要沿用既有 `.2/.4/.114` 现场参数，重新生成固定名完整离线包，并明确新配置生成、校验和逐机部署顺序。
- What:
  - 基于 `106f8b3dc` 隔离构建最新后端 JAR 与前端，完整包中的前端配置使用 `TEST_AGENT_NGINX_SERVER_ROUTES`，为 `.4/.114` 两个 `linuxServerId` 配置精确首跳路由。
  - 复用上一轮已校验的两台后台共享凭据和逐机身份，删除外置 RSA 配置；`.2` 不再携带旧 `TEST_AGENT_NGINX_TERMINAL_ROUTES`。
  - 固定名产物为 `/Users/kaka/Desktop/qr-decode/out/test-agent-two-backend-complete.zip` 及同名 `.sha256`；三份逐机配置包继续控制在 1 MiB 以内。
- How:
  - 后端和前端从当前提交实际执行 Maven/Vite 生产构建；worker/programs 对比 `3724ae37a..106f8b3dc` 无源码变化，因此复用已验证的 `linux/amd64` worker/programs 交付物。
  - 运行 Nginx、单机配置、自动节点初始化、多后台逐机和完整包封装回归；再对最终包执行内外层 SHA/压缩完整性、三节点 `--validate-only`、JAR 内置 RSA、当前部署脚本一致性和 Nginx 路由键检查。
- Result:
  - 最终包 SHA256 为 `9a7c3080d70c931f3204cd5644454c25b69e64c90334fc3e0fcf826f38e95ca2`；内层发布 ZIP SHA256 为 `cbe63aa1e0dfc1d17279fc7c52cd3125e4e89d68eb59ad70cd7c4b2bb567680d`。
  - `.4/.114/.2` 配置包分别为 `22407/22403/20375` 字节，均通过配置与发布物校验；尚未在企业现场执行真实 systemd、Docker 和 Nginx reload，必须按 `.4 -> .114 -> .2` 顺序部署并现场验收。

### 2026-07-21 - 重建包含 Agent 配置按钮对齐的固定名双后台包

- Why:
  - 用户要求再次重打企业双后台完整包；打包期间主分支新增 Agent 配置按钮对齐提交，需要以最新已提交代码重新构建，避免交付包遗漏该前端变更。
- What:
  - 从提交 `c15d288a89a7dc9a3dbf326ec7ce46a664d87193` 的临时干净 worktree 全量重建后端 JAR、同源前端、programs 和 Linux/amd64 worker，并复用三台既有受控配置覆盖固定名 `test-agent-two-backend-complete.zip` 及 SHA。
  - 外层结构和企业操作方式保持不变：一个 ZIP 内包含内层标准发布 ZIP 及 `.4/.114/.2` 三台节点包，节点包继续只含配置、逐机脚本和手册且均小于 `1 MiB`。
- How:
  - 实跑外层 ZIP 完整性与 SHA、内层发布 SHA、三节点 SHA、JAR 内置 RSA、worker `linux/amd64`、三节点 `--validate-only`、systemd 首装/升级和固定名重复覆盖回归。
- Result:
  - 新包位于 `/Users/kaka/Desktop/qr-decode/out/test-agent-two-backend-complete.zip`，约 `237 MiB`，SHA256 `a1515f0d389bed97d73bdb614080e5114f9d77be877ef674b4fb37069c06f348`；内层发布 SHA256 `f3b75328ab4667b30784024ff851e1a32f241a96ef70c9d6717e121297594e4f`，JAR SHA256 `66397b506b5239a5b91081ca68722d24a31167dbc1b7e2694a9db8e95bf274c5`。
  - 本机交付物与部署脚本已验证；企业三台服务器仍需按 `.4 -> .114 -> .2` 正式部署并完成浏览器验收。

### 2026-07-21 - 对齐 Agent 配置树与工作区操作按钮

- Why:
  - 用户指出统一新建/上传面板后，Agent 公共级和应用级的触发按钮仍未与应用工作区保持一致。
- What:
  - 公共级、应用级根入口由 `FilePlus2` 改为工作区同款 `Plus`，并对齐 20px 尺寸、4px 圆角、hover、focus 和过渡效果。
  - Agent 目录行新增/删除按钮对齐工作区 18px 规格、间距和交互反馈，删除按钮恢复一致的红色 hover 语义；组件测试锁定根入口使用 `Plus`。
- How:
  - 直接参照并复用 `FileExplorer.vue`、`DirectoryRows.vue` 现有按钮规格，只调整 Agent 配置组件，不新增按钮组件或全局样式。
- Result:
  - AgentConfigPanel 27 项、agent-web typecheck、用户手册与前端生产构建通过；`.env.test` / `test` profile 重启三服务后 health/readiness 为 UP、前端 3000 与 CORS 正常、manager WebSocket 已连接。
  - 应用内浏览器自动视觉检查仍受既有 `Cannot redefine property: process` 限制；图标由组件测试验证，CSS 值已与工作区源码逐项对齐。未修改 API、事件、数据库、安全边界、环境配置或依赖。

### 2026-07-21 - 统一公共与应用 Agent 新建上传能力

- Why:
  - 用户希望合并“新建文件”和“初始化 Agent/Skill”两个根入口，让公共 Agent 与应用 Agent 能力对齐；弹出面板和按钮需与应用工作区新建/上传一致，并说明普通条目与 Agent/Skill 模板的区别。
- What:
  - 公共级、应用级根统一为“新建或上传配置”，直接复用共享 `FileEntryCreateDialog` 样式，提供文件、文件夹、上传、Agent、Skill 五种操作；可写目录行提供文件、文件夹和上传。
  - 面板按当前选项说明：普通文件/文件夹只创建空白条目或整理素材，Agent 生成 `agents/<name>.md`，Skill 生成标准 `skills/<name>/` 配置包；公共/应用模板文案分别保留 public/application scope，英文名称不再逐字母加短横线。
  - 新增 `agent-config.upload` 文件 WebSocket RPC，复用既有 Base64、大小、不覆盖、重名和越界校验；应用上传在服务层限制为 `opencode.jsonc`、`agents/**`、`skills/**`。公共上传/改名要求 `SUPER_ADMIN`，应用上传/改名要求 `APP_ADMIN`（`SUPER_ADMIN` 继承）；普通用户界面隐藏入口且后端拒绝绕过调用。
  - 同步前后端 README/PACKAGE、HTTP 与文件 WebSocket 协议、安全规范和内置用户手册。
- How:
  - 复用共享文件面板、`WorkspaceFileService.uploadFile/renameFile`、Agent 配置 route/ticket/RPC 与既有 Git revision 刷新链路，没有新增 HTTP 文件代理或第二套模板/文件服务。
  - 前端定向 37 项、agent-web typecheck、用户手册和生产构建通过；`AgentConfigApplicationServiceTest` 47 项、`WorkspaceFileWebSocketHandlerTest` 17 项通过，后端 18 模块跳过测试打包成功，`git diff --check` 通过。
- Result:
  - 使用 `.env.test` / `test` profile / JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 和 manager WebSocket 正常。
  - 应用内浏览器自动视觉检查仍受既有运行时 `Cannot redefine property: process` 限制；共享面板实际复用、五种按钮、说明文案、上传事件与权限由组件/协议测试和真实服务启动验证。未修改数据库、migration、RunEvent、generated SDK、环境配置或依赖。

### 2026-07-21 - 基于最新提交重建固定名双后台完整包

- Why:
  - 用户要求重新打包，并继续只向企业内部导入一个固定名完整 ZIP 及其 SHA，随后按企业内部中转机、`.4`、`.114`、`.2` 的顺序逐步部署。
- What:
  - 从提交 `588097fc144b1770f8d1adcaa843fb090e6e6bfd` 的临时干净 worktree 全量重建后端 JAR、同源前端、外置 programs 和 Linux/amd64 worker；复用三台服务器既有受控配置重新封装固定名 `test-agent-two-backend-complete.zip`。
  - 包内固定根目录为 `test-agent-two-backend-complete/`，包含内层完整发布 ZIP、三台节点包及各自 SHA；节点包继续只包含配置、逐机脚本和手册，均小于 `1 MiB`，JAR 使用内置 RSA。
- How:
  - 外层 ZIP 完整性与 SHA、内层发布 SHA、三节点 SHA、JAR `BOOT-INF/classes/rsa-private.key`、worker `linux/amd64`、三节点 `--validate-only`、systemd 首装/升级和固定名封装回归全部实跑通过。
  - 构建与校验均在临时 worktree 完成，没有把工作区未提交内容带入发布包；企业现场只从中转机传输固定名 ZIP 和 SHA，不需要分别传内层发布包和节点包。
- Result:
  - 新包路径 `/Users/kaka/Desktop/qr-decode/out/test-agent-two-backend-complete.zip`，大小约 `237 MiB`，SHA256 为 `af926d32748c833ee2e641e38d8bb06cc2365afd51bbead8045a2bee4f545422`；内层发布 SHA256 为 `4b4710ab3714fced7115088226f88f9a1af02e9f9487d11c8cb60c546ba0deb6`，JAR SHA256 为 `d28495f87a4f0ec7759e5a0b80444bc5a05269ba703104eada14c2f0875ac624`。
  - 本机已验证发布物与脚本；企业三台服务器的正式部署和浏览器双后台业务验收仍需现场执行。

### 2026-07-21 - 应用 Agent 文件双击改名与只读权限复核

- Why:
  - 应用级 Agent/Skill 文件缺少与普通工作区一致的双击改名能力，同时需要确认公共级和应用级对无写权限用户确实保持只读。
- What:
  - 应用管理员与超级管理员可双击 `agents/**`、`skills/**` 文件名行内改名；成功后刷新父目录、同步已打开 Agent tab，并触发 Git Changes 重新统计。
  - 新增 `agent-config.rename` 文件 WebSocket RPC，后端仅允许 `APP_ADMIN`（`SUPER_ADMIN` 继承）操作应用级文件；公共级不开放改名。
  - 复核并补测普通用户：公共级和应用级文件均以只读 tab 打开，树中不进入改名输入，后端绕过界面调用仍返回 `FORBIDDEN`。
- How:
  - 复用普通文件树的双击/Enter/失焦/Esc 行内交互、`WorkspaceFileService.renameFile` 的同目录改名和路径安全校验，以及既有 Agent 配置 route/ticket/RPC，没有新增 HTTP 文件代理或平行文件服务。
- Result:
  - AgentConfigPanel 24 项、backend-api 定向契约、WorkspaceFileWebSocketHandler 14 项、AgentConfigApplicationService 46 项通过；agent-web typecheck、前端生产构建和 `git diff --check` 通过。
  - 使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常。
  - 仅新增文件 WebSocket RPC 操作，不涉及 RunEvent、数据库、性能、generated SDK、环境配置或新依赖；权限边界保持公共写 `SUPER_ADMIN`、应用写 `APP_ADMIN`。

### 2026-07-21 - 修复公共配置未知用户目标永久排空

- Why:
  - 企业双后台公共 Agent 发布中，两台服务器 Git 已 `SYNCED`，但 manager 进程与平台进程表因历史 PID 为空或启动时间微差无法精确映射用户；`user_id=null` target 在恢复公共链接时构造空 `UserId`，持续以 `userId must not be null` 重试并阻断后续发布。
- What:
  - 公共 rollout 在 dispose 前优先使用同一服务器、容器、端口、PID、启动时间完全匹配的 manager 实时快照 `sessionPath/configPath` 恢复共享配置链接；未知用户 target 不再依赖数据库用户绑定。
  - 旧 manager 缺路径时仅对已映射用户保留数据库精确身份兼容路径；路径缺失、越界或进程身份变化仍失败关闭。同步 runtime README 与企业后端部署说明。
- How:
  - 复用既有 manager heartbeat 快照和 `OpencodeProcessConfigLinkService`，不放宽 PID/启动时间比较、不按端口猜测用户、不新增 API、数据库字段、migration 或 manager 协议；新增未知用户成功排空与缺路径失败关闭回归。
- Result:
  - 定向 23 项和 runtime 模块全量 626 项测试通过；18 模块生产代码以 `-Dmaven.test.skip=true` 打包成功。标准 `-DskipTests` 仍被既有 `UserDomainService` 测试缺少 `ThirdPartyUserApiClient` 构造参数阻断，与本次改动无关。
  - 使用 `.env.test` / `test` profile 启动 backend、manager、frontend；health/readiness 为 UP、前端 3000 和 CORS 为 200、manager WebSocket 已连接并应用配置。

### 2026-07-21 - Agents 新建删除联动 Git Changes

- Why:
  - 公共级和应用级 Agents 配置树只能编辑既有文件，缺少新建文件、文件夹和删除入口；通过树操作落盘后还需要让既有 Git Changes 立即感知。
- What:
  - 抽取工作空间已有的新建与删除确认面板供 Agents 复用；公共级、应用级根与可写目录支持新建空文件、以 `.gitkeep` 表示空文件夹，并支持文件和目录树递归删除。
  - 新增平台文件 WebSocket `agent-config.delete`，公共级继续要求 `SUPER_ADMIN`，应用级要求 `APP_ADMIN`（`SUPER_ADMIN` 继承）；删除复用工作空间文件服务的根目录、`.git`、越界路径和符号链接保护。
  - 创建/删除成功后刷新对应目录并递增既有 Agent 配置修订号，触发 Git Changes 重新查询；删除同时关闭对应文件或目录下的已打开标签。应用级入口限制在 `opencode.jsonc`、`agents/**`、`skills/**` Diff 白名单内。
  - 同步前后端模块 README/PACKAGE、HTTP/事件流协议、安全/前端规范和内置用户手册。
- How:
  - 新建继续复用 `agent-config.write`，删除新增同一 route/ticket/RPC 通道内的操作，不增加 HTTP 文件代理、RunEvent 或第二套 Diff 状态；业务层直接复用 `WorkspaceFileService.deleteFile` 的安全递归语义。
- Result:
  - 前端 lint、typecheck、生产 build 和全量 Vitest 通过（86 files，1439 passed / 1 skipped）；首次全量中 1 个无关 `agent-chat` 时间敏感用例偶发失败，单独复跑及第二次全量均通过。
  - `AgentConfigApplicationServiceTest` 46 项、`WorkspaceFileWebSocketHandlerTest` 14 项通过；后端全量测试执行到既有 `test-agent-system-management` 测试编译错误后停止，其 `UserDomainService` 测试仍缺少新增的 `ThirdPartyUserApiClient` 构造参数，与本次改动无关。
  - JDK 25 下后端 18 模块跳过测试打包成功；使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend，health/readiness 为 UP、前端 3000 和 CORS GET 为 200、manager WebSocket 已连接。
  - 增加兼容性的 WebSocket RPC；未修改 RunEvent、数据库、migration、generated SDK、环境配置或依赖。删除沿用既有权限和路径安全边界，不引入新的跨服务器文件通道。

### 2026-07-21 - 应用配置初始化区分 Agent 与 Skill

- Why:
  - 应用级初始化此前总是同时生成 Agent 和 Skill，且名称转换对英文逐字符插入短横线，例如 `Payment Agent` 会得到 `p-a-y-m-e-n-t-a-g-e-n-t`。
- What:
  - 初始化弹窗新增 Agent/Skill 类型选择；Agent 只生成 OpenCode Markdown Agent 文件，Skill 单独生成 `SKILL.md`、rules 与 templates 资源模板。
  - 名称转换保留中文拼音分段，同时让连续英文和数字保持连续。
  - 同步 agent-web README 和内置手册，新增 Agent/Skill 分流、模板内容和英文名称回归。
- How:
  - 复用 `writeWorkspaceAgentFile` 和既有目录刷新，没有新增后端 API、模板服务或命名工具；Agent 模板按 OpenCode 规则由文件名决定名称，Skill 名称继续符合 `^[a-z0-9]+(-[a-z0-9]+)*$`。
- Result:
  - 前端全量 Vitest 86 个文件通过（1435 passed / 1 skipped），agent-web typecheck 和生产构建通过；JDK 25 下后端 18 模块打包成功。
  - 使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常、manager WebSocket 已连接。
  - 应用内浏览器自动视觉检查因运行时 `Cannot redefine property: process` 未执行；组件交互测试、构建和真实服务启动已覆盖本次交付。未修改 API、RunEvent、数据库、安全、性能、generated SDK 或环境配置。

### 2026-07-21 - 双后台完整包改为固定名称

- Why:
  - 用户希望后续每次只操作同一个完整包，并固定文件名，避免日期、`v2/v3` 导致中转机和三台服务器命令反复变化。
- What:
  - 新增 `package-two-backend-complete.sh`，把标准发布 ZIP 与三台节点包封成固定的 `test-agent-two-backend-complete.zip` 和配套 SHA，包内顶层也固定。
  - 同步企业部署 README、多后台手册和离线部署 Skill；新增隔离回归覆盖固定结构、SHA、敏感输出、重复无交互覆盖和禁止版本后缀。
- How:
  - 复用现有 `package-release.sh` 产出的内层 ZIP 及现有节点归档，不复制 Java、前端或 worker 构建逻辑；源交付物只读校验，节点包换入当前逐机脚本和手册后重新计算 SHA。
- Result:
  - 固定名封装回归、外层/内层/三节点 SHA、ZIP 完整性、固定目录、重复覆盖和 JAR 内 RSA 校验通过；当前固定包 SHA256 为 `5f0e544330dd0d749116bc81e4b9f076239d45162228b0d71d97e29c136053b5`。
  - 后续企业内部中转机只需接收固定名 ZIP 和 SHA，两者视为一套交付；节点配置与 JAR 内 RSA 仍在 ZIP 内按敏感交付物管理。

### 2026-07-21 - 新增公共技能创建与优化基础能力

- Why:
  - 用户需要在本机公共 Agent 配置区增加通用 `skill-creator` 和独立技能优化能力，打包后由用户导入企业内部环境。
- What:
  - 在公共个人 worktree 的 `opencode/skills/` 新增 `skill-creator`、`skill-optimizer`，包含 OpenCode 入口、按需参考、模板、无第三方依赖的离线校验脚本和 eval 样例；同步公共仓库 README 与 `opencode/AGENTS.md` 技能清单。
- How:
  - 复用既有 `skills/<name>/SKILL.md`、渐进加载和 `.skill` 打包约定，创建与优化职责分离；打包产物写入 `.tmp/enterprise-skill-packages/`，未修改或暂存公共 worktree 中既有热加载测试文件。
- Result:
  - 两项技能均通过自带校验、系统 `quick_validate.py`、eval JSON、敏感路径扫描、OpenCode `debug skill` 发现和归档解压复验；两个 `.skill` 包可供企业内部导入。
- Pitfalls:
  - 系统 Python 缺少 PyYAML，复用已有 `.tmp/skill-validate-venv` 完成系统校验；`package_skill.py` 需要从 skill-creator 根目录以 `python -m scripts.package_skill` 运行。
- Verification:
  - `python3 scripts/validate_skill.py <skill-dir>`；`quick_validate.py`；`OPENCODE_CONFIG_DIR=... opencode debug skill`；`unzip -t`、解压后二次校验；`git diff --check`。
- Next:
  - 用户将 `.skill` 包导入企业公共技能区后，可按企业模型与真实任务样本补充触发率和行为基准测试。

### 2026-07-21 - 小宠物入口单击重启已终止进程

- Why:
  - 已分配的 opencode 进程终止后，左侧活动栏宠物入口只会先唤出宠物，用户还要再次点击状态入口才能启动；用户希望一次点击完成启动，并在成功后显示宠物。
- What:
  - `FigmaShell` 在 `NEEDS_INITIALIZATION + NOT_RUNNING + initializable` 时把活动栏入口切换为直接启动，继续复用工作台已有初始化 mutation；READY 后只唤出浮动宠物，不自动打开状态卡或问答，失败后清理延迟唤出意图。
  - 增加组件成功/失败回归和桌面、移动端工作台 E2E；同步 frontend、agent-web、PACKAGE、模块图和快速开始手册。
- How:
  - 未新增 API、启动服务或旁路；前端仍调用既有 `/processes/me/initialize`，后端继续由公共 `OpencodeProcessStartupService` 完成 manager 与健康检查。
- Result:
  - agent-web typecheck、全量 Vitest（1431 passed / 1 skipped）、手册/生产构建通过；定向 Playwright Chromium/mobile 2 项通过。
  - 按 JDK 25、`.env.test`、test profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 为 200、CORS 正常、manager WebSocket 已连接。不涉及 API/RunEvent、数据库、性能、安全、兼容性、generated SDK、依赖或环境配置。

### 2026-07-20 - 企业部署人工复制改为无交互覆盖

- Why:
  - 企业服务器的 `cp` 被配置为交互式覆盖，逐个复制内层发布 ZIP、SHA 和节点包时反复询问，影响逐步部署操作。
- What:
  - 企业离线部署 Skill 约定现场人工复制明确交付文件时统一使用 `/bin/cp -f`，绕过 `alias cp='cp -i'`。
- How:
  - 只允许对逐条列明的文件执行无交互覆盖，禁止扩大为 `cp -rf` 目录覆盖；部署脚本原有配置备份行为不变。
- Result:
  - 后续逐台部署命令不再因已有同名发布文件反复询问，仍保留目标范围和回滚边界。

### 2026-07-20 - 记录企业中转机与逐步部署说明偏好

- Why:
  - 用户指出完整包已通过 U 盘进入企业内部中转机的 `Desktop/mimoagent/0709`，现场 `scp` 并非从 Mac 发起；同时明确以后每次部署都需要逐台、逐步的操作说明。
- What:
  - 更新企业离线部署 Skill，区分外部联网 Mac 与企业内部中转机，并把“中转机校验和传输 → `.4` → `.114` → `.2` → 业务验收”记录为现场说明顺序。
- How:
  - 要求每一步标明操作机器、绝对目录、完整命令、预期结果和失败停止条件；禁止用循环、“同上”或前后不一致的 `BASE/WORK` 假定目录压缩步骤。
- Result:
  - 后续用户已经说明交付物位于企业内部中转机时，从中转机 SHA256 校验和 `scp` 开始，不再误称“从 Mac scp”；仍保留 Mac 作为外部构建机的事实。

### 2026-07-20 - 修复企业同源部署 RunEvent SSE 地址构造

- Why:
  - 企业单/双入口前端按约定以显式空 `VITE_TEST_AGENT_API_BASE_URL` 构建，但 RunEvent client 仍调用 `new URL("/api/...")`；浏览器缺少绝对 origin 时会在发起请求前抛出 `Invalid URL`，表现为消息可发送、实时思考和回答缺失、历史记录最终完整。
- What:
  - RunEvent URL 改为先保留绝对或同源相对 path，仅在存在续传游标时用 `URLSearchParams` 追加 query；显式空 `baseUrl` 现在生成 `/api/internal/agent/.../events`，非空 base URL 和 `lastEventId` 语义不变。
  - 新增空 base URL 回归测试，并同步 event-stream-client README/PACKAGE、事件流 API 和模块图；未修改双后台生产 Java 解析、SSE 转发或 Nginx 路由。
- How:
  - 修复前回归用例稳定复现 `TypeError: Invalid URL`；修复后 event-stream-client 15 项测试、包级 typecheck 和前端全量 86 个测试文件通过（1429 passed / 1 skipped）。
  - 以空 `VITE_TEST_AGENT_API_BASE_URL` 完成 agent-web 生产构建，构建产物在 `http://127.0.0.1:4189/` 启动并返回 HTTP 200；保留既有 canvas 提示和大 chunk warning。
- Result:
  - 企业同源部署会真正向当前 Nginx origin 发起 RunEvent fetch SSE，不再在浏览器本地地址构造阶段反复报连接异常；双后台仍复用现有 producer Java 路由与 Java-to-Java SSE 转发。
  - 不涉及 RunEvent 类型、HTTP 路径、数据库、SQL/migration、generated SDK、依赖、鉴权或环境配置文件；企业现场仍需重新构建并部署包含本修复的前端产物后做真实双后台对话验收。

### 2026-07-20 - 修复双后台 manager 成功日志误判

- Why:
  - `.4` 现场容器 healthy、manager WebSocket 已连接且配置已经下发，但逐机 `--verify-only` 仍退出 1；实际日志为结构化 `event=manager_config_update status=applied`，脚本仍只匹配旧文本。
- What:
  - 标准发布与双后台逐机验证脚本改为同时识别当前结构化事件和旧版 `manager config update applied`，并同步企业部署 Skill、README、单/多后台手册。
  - 扩展双后台隔离回归，用假的 systemctl/curl/docker 真实执行 `--verify-only`，覆盖新旧两种成功日志。
- How:
  - 只调整日志成功判定，不修改 manager 协议、JAR、RSA、worker 镜像、配置或业务代码；重新封装完整发布 ZIP 和三份逐机配置包并更新各层 SHA256。
- Result:
  - Shell 语法、双后台逐机回归、最终三节点 `--validate-only`、ZIP/tar/SHA、JAR 内置 RSA 和 systemd 首装/升级验证通过；最终 JAR SHA256 保持 `08e4459c0c825682d2d2193d4fdd0c448602d6e816de8e64999503e0725c4ba2`。
  - `.4` 当前部署状态可判定成功；尚未在企业现场用修复脚本重新执行 `.4/.114 --verify-only`，前端 `.2` 仍应在两台后台验证通过后部署。

### 2026-07-20 - 基于现场配置交付双后台完整部署包

- Why:
  - 用户回传 `.2/.4/.114` 的轻量现场配置，要求直接生成使用 JAR 内置 RSA 的完整双后台包、逐机操作脚本和验收命令；`1 MiB` 只约束现场配置导出，不约束最终发布包。
- What:
  - 新增 `deploy-multi-backend-node.sh`，逐机校验并安装真实配置，复用标准后台/前端发布脚本，提供 `--validate-only`、正式部署和 `--verify-only`。
  - 现场修正包括：前端切换 `.4 + .114` multi upstream、`.4` 端口池补齐到 `4096-4115`、删除外置 RSA 路径和旧 `TEST_AGENT_BACKEND`；两台保留一致的共享凭据但使用不同稳定身份。
  - 修复标准部署脚本在 `pipefail + grep -q` 下可能把 worker 已成功下发配置误判为超时的问题；同步多后台手册和隔离回归。
- How:
  - 校验三份采集包 SHA，比较共享凭据但不打印值；完整发布 ZIP 与三份逐机配置包分层交付，逐机包不重复包含 JAR/镜像。
- Result:
  - 生成 `test-agent-two-backend-complete-20260720.zip`（约 `237 MiB`），外层 SHA、内层发布 SHA、三节点 SHA、JAR 内 RSA、systemd、Nginx 和逐机 validate-only 全部通过；尚未在三台企业服务器执行正式部署。

### 2026-07-20 - 增加双后台现场轻量配置采集包

- Why:
  - 用户最终要求只交回 `.2/.4/.114` 的现场配置，保留真实密码/token 以便生成无占位符部署脚本，但排除 JAR、RSA、日志等大文件，并把每台导出包控制在 `1 MiB` 内。
- What:
  - `deploy/internal/collect-multi-backend-context.sh` 以 `frontend/backend` 角色只读采集：后台为原始 `backend.env/docker.env`、身份文件和 systemd 有效 unit，前端为原始 `nginx.env`、主配置和 `test-agent.conf`。
  - 输出 `0600` 的 `SENSITIVE` tar.gz 与可搬移 SHA 文件，强制压缩包不超过 `1 MiB`；明确排除 JAR/lib、RSA、日志、Docker、programs、worker 镜像、业务数据和已部署前端。
- How:
  - dotenv 仅按文本读取、不 source；采集命令不调用 start/stop/restart。隔离回归验证原始密码/token 入包、禁止项不入包、无显式开关时拒绝、两种角色结构、SHA 和 `1 MiB` 超限删除行为。
- Result:
  - Shell 语法、配置采集回归、AI 文档和 diff 校验通过；独立脚本可直接复制到三台服务器，不要求重新传完整企业 ZIP。
  - 未修改 API、RunEvent、数据库、环境配置或 generated SDK；尚未在企业三台服务器执行采集或部署，配置包仍包含真实凭据，需按受控交付物处理。

### 2026-07-20 - 确认企业单后台回退实际生效时间

- Why:
  - 现场 Run 在历史记录中最终成功，但浏览器提示 RunEvent SSE 连接异常；此前曾短暂部署 `.4 + .114` 双 Java，随后关停 `.4`。
- What:
  - `.2` 的 `nginx.env` 与实体 `/data/apps/nginx` 的活动 `nginx -T` 均确认只包含 `.114:8080`；`.4:8080` 已拒绝连接，`.114` readiness 为 `UP`，因此当前静态 upstream 残留双后台已排除。
- How:
  - 使用 `/data/apps/nginx/sbin/nginx -p /data/apps/nginx/ -c /data/apps/nginx/conf/nginx.conf -T` 核对活动配置；PATH 中裸 `nginx -T` 会错误读取 `/root/conf/nginx.conf`，不能用于该现场。单后台前端/Nginx 部署实际完成于 16:06，而已采集故障 Run 在 15:40 发起。
- Result:
  - 15:40 的旧 Run 不能验证 16:06 后的单后台链路；需要浏览器硬刷新后用新 runId 复测。既有 Nginx access/error 默认路径未查到旧 runId，后续应先从实体 `nginx -T` 确认实际 `access_log/error_log`，再结合浏览器 SSE 的 Request URL、状态、Remote Address、耗时与 `.114` `api_stream_start/end` 定位；当前根因仍未确认。

### 2026-07-20 - 恢复 JAR 内置 RSA 并交付 20 端口单后台包

- Why:
  - 用户再次确认企业部署只能使用 JAR 内置 RSA；删除个人 SSH 配置后公共 Agent 已能拉取，但双后台下 RunEvent SSE 与 reference 仍必现异常，因此先回退到 `.114` 单后台，并把 OpenCode 端口池在原 10 个基础上再增加 10 个。
- What:
  - `RsaKeyService` 和 Spring 配置移除外置私钥路径构造与 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH`，固定读取 JAR `classpath:rsa-private.key`；打包脚本强制校验该资源存在，企业配置模板、Skill、安全和部署文档同步改回内置模式。
  - 单后台生成脚本只配置 `.114:8080`，实体 Nginx 同一 server 同时监听 `80`、`9996`；前端以空 `VITE_TEST_AGENT_API_BASE_URL` 使用同源 API，Java/OpenCode CORS 同时允许域名与 IP 的 `:9996` origin。
  - worker 端口池扩为 `4096-4115`，Docker 宿主机/容器同号映射 20 个端口；文档要求超级管理员同步把数据库通用参数 `OPENCODE_MANAGER_MAX_PROCESSES` 调为 `20`。补充 `.4` 停 worker、禁用 Java但保留数据的回退步骤。
- How:
  - 先抓取 `origin`、`github` 并将共同最新 `c539d018a` 合入本地 `main`；未新建分支。保留远程会话列表样式改动，再最小修改 RSA、单后台配置、worker 端口和相关稳定文档。
  - 定向 RSA WebCrypto OAEP-SHA256 测试通过；JDK 25 下 18 模块跳过测试打包并按 `.env.test`/`test` profile 真实重启 backend、manager、frontend，readiness 为 `UP`，日志确认从 `classpath:rsa-private.key` 加载。
  - 单后台配置、Nginx、systemd 首装/升级模拟、Shell 语法、ZIP/SHA、包内 JAR/脚本、Linux/amd64 worker、同源前端和离线部署 `--validate-only` 均通过；首次 Docker 构建从 `proxy.golang.org` 下载模块瞬时 EOF，切回 `goproxy.cn` 重试成功。
- Result:
  - 最终包 `deploy/internal/dist/test-agent-internal-release.zip` SHA256 为 `c9e41d912a37c486aa5d11ccb13f4a492bb552031fe21e93db015b0b16ca785e`；ZIP 与同名 `.sha256` 上传 `.2`、`.114` 的 `/data/0709/`，`.4` 不部署新包，只停服务并保留数据。
  - 公共 Agent 拉取已由现场确认恢复；RunEvent SSE 与 reference 的双后台根因仍未定位，不能称为已修复。单后台切换预计可排除跨后台路由/副本因素，但仍需现场按同一 runId 和 reference 同步重新验收；若仍失败再采集实际 SSE HTTP 状态、traceId 及 `.114` 日志。
  - 未变更 HTTP API、RunEvent 类型、数据库结构/SQL/migration、generated SDK 或依赖。内置私钥使交付 JAR/ZIP 成为敏感物；替换该资源会使数据库中既有 SSH 密文不可解密。

### 2026-07-20 - 收窄企业双后台 RSA、引用副本与 SSE 排障范围

- Why:
  - 企业双后台现场同时出现公共 Git 凭据 `RSA decryption failed`、新增 `.4` 引用资产指针核验失败和 RunEvent SSE 必现断流，需要区分部署数据、节点网络与代码问题。
- What:
  - 两台磁盘外置 RSA 私钥和当前 Java 进程公钥已由现场确认一致；仓库历史同时确认用户在 7 月 16 日明确要求企业包继续使用 JAR 内置 `rsa-private.key`，当前双后台 `backend.env` 的外置路径覆盖了这一既定模式。当前交付 JAR 仍包含自创建以来未变更的内置 RSA，两个节点部署的 JAR SHA 也一致。
  - `.4` 的引用根目录只有平台创建的 `.reference-repository-locks`，目标仓库目录尚不存在，不是残留坏仓库；修复凭据后应从前端仓库卡片触发同步，让后端临时 clone 后原子落位，不能用只读“刷新 Git 指针”代替同步。
- How:
  - 现场已验证 `.4/.114` 身份文件和 advertised host 正确、Java 均监听 `*:8080`、两台 Java 双向 readiness 为 `UP`、`.2` 可访问两台 readiness；Nginx 已加载两个 upstream，`/api/` 为 `proxy_buffering off` 且 `proxy_read_timeout=3600s`。
- Result:
  - `RSA decryption failed` 的直接原因是现场从约定的 JAR 内置 RSA 切换到了另一把外置 RSA；应从两台 `backend.env` 移除 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH` 并在维护窗口重启 Java，恢复相同 JAR 内置公钥。基础短连接网络和 Nginx SSE 参数已排除；RunEvent 必现断流尚未定位，仍需取得 `/runs/{runId}/events` 的实际 HTTP 状态、traceId、三入口流式 curl 结果及两台 Java 同一 runId 日志。企业模板/脚本当前仍会重新写入外置路径，后续需按用户既定模式修正并重打包，未授权前不修改业务代码或部署模板。

### 2026-07-20 - 生成域名/IP同端口双后台企业包

- Why:
  - 当前企业现场要求浏览器同时使用 `http://mimo.sdc.cs.icbc:9996` 与 `http://122.233.30.2:9996`，并把 Java/worker 从 `.114` 扩为 `.4 + .114`；既有前端固定域名后无法兼容 IP，Nginx 渲染也只能声明一个监听端口。
- What:
  - 前端 API 环境读取区分“显式空值”和“未配置”，空的 `VITE_TEST_AGENT_API_BASE_URL` 现在稳定表示当前页面同源 `/api`，不会让登录页回退到 `127.0.0.1:8080`；补充 backend-api 单测和包文档。
  - `configure-nginx.sh` 新增可选 `TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS`，同一个 server 块保留实体 `listen 80` 并增加 `listen 9996`，校验端口范围和重复值；多后台 upstream 固定 `.4:8080 + .114:8080`，实体配置继续复用已加载的 `/data/apps/nginx/conf/test-agent.conf`。
  - 多后台文档改为当前 HTTP 双入口完整执行单：两个浏览器 URL 都使用 9996，域名的既有企业入口内部仍可转发到实体 80；两台 Java/worker 同时放行两个 Origin、返回各自直接 `ws://...:8080`，后台就绪后再更新前端 Nginx。
- How:
  - 抓取 `origin` 和 `github` 后，两者仍在 `cc89296e0`，本地已包含全部远程代码，无新增提交需要合并。运行前端全量 Vitest（86 files，1428 passed / 1 skipped）、backend-api typecheck、Nginx 单/多后台渲染、单后台配置生成和最终 ZIP systemd 首装/升级模拟。
  - 第一次 worker 构建在 `goproxy.cn` 下载 Go 模块时瞬时 EOF；切换 `GOPROXY=https://proxy.golang.org,direct` 后完整打包成功。校验 ZIP/SHA、包内脚本文档、未夹带现场 env/私钥、前端未固化域名/IP、Linux/amd64 镜像和离线 Tool 依赖。
- Result:
  - 新包 `deploy/internal/dist/test-agent-internal-release.zip` SHA256 为 `7b6438cfadd7a4ef9073a518f979e06e0fdf73d9a036cf0f082f7bc379593d88`；同名 `.sha256` 需上传 `.2`、`.4`、`.114` 的 `/data/0709/`。
  - HTTP/WS 会明文传输登录信息和终端内容，且浏览器网段必须直达 `.4:8080`、`.114:8080`；本次没有替用户修改或重启内网服务器。未变更 HTTP API、RunEvent/SSE、数据库、SQL/migration、generated SDK 或依赖；新增部署环境字段为空时向后兼容。

### 2026-07-20 - 收口单后台现场问题与部署文档

- Why:
  - 当前单后台现场同时遇到 Docker 容器调用动态 Tool 地址超时、HTTP 域名解析/CORS、服务器终端不走 WSS、实体 Nginx 显式 include、服务重启后仍运行旧 JAR 等问题；既有文档仍混有旧 IP、WSS 和系统 Nginx 路径示例。
- What:
  - 单后台文档统一为浏览器 `http://mimo.sdc.cs.icbc:9996`、企业入口转发到 `.2:80`、Java/worker `.114`；补充 HTTP/WS 风险、精确 CORS/编译期地址、Docker bridge 源网段 `FORWARD + MASQUERADE` 持久规则、变更重启矩阵和 PID/JAR SHA 验证链。
  - 明确实体 Nginx 只显式加载 `/data/apps/nginx/conf/test-agent.conf`，当前应检查备份后复用该专用文件、监听保持 80；自动生成的 frontend/deploy/Nginx `.bak` 只是回滚备份。通用模板继续保持 WSS 安全默认，当前 HTTP 现场通过真实 env 显式覆盖。
- How:
  - 对照部署脚本、Nginx `-T` 现场输出、公共配置/模型和 worker 管理实现；抓取两个远程后均无待合入提交。运行 AI 文档、Shell 语法、单后台配置、Nginx 渲染、systemd 升级模拟、前后端交付脚本校验和完整 Mac 企业打包。
- Result:
  - 新 ZIP `deploy/internal/dist/test-agent-internal-release.zip` 包含修正文档与 `http://mimo.sdc.cs.icbc:9996` 前端，SHA256 为 `d9b93b614af2ba942dc9dcea8709bfb23a9d61c7eb4fe493634af8a5256b2842`；ZIP/SHA、包内路径、Linux/amd64 镜像和 Tool 基线依赖均通过。
  - 首次 Tool 探针从 worker 工作目录直接 import 因未经过运行时模块链接而失败；改从镜像实际 `/usr/local/lib/opencode-node` 复跑成功，确认不是依赖缺包。未变更 API、事件、数据库、依赖或安全默认，未在本机替用户操作内网服务器。

### 2026-07-20 - 修复企业 Nginx 显式 include 目录误判

- Why:
  - `.2` 前端部署时 Nginx 两次语法校验成功，但脚本随后提示未 include `/data/apps/nginx/conf/test-agent-gateway.conf`；现场主配置只显式加载同目录某个现有文件，旧探测逻辑却误以为同目录新建文件也会自动加载。
- What:
  - `configure-single-deployment.sh frontend` 复用实体 Nginx `-T`，在每个候选目录短暂创建仅含注释的探测 `.conf`，只有新文件确实出现在加载清单中才选择该目录；生效配置与主配置中的 `*.conf` include 均走相同验证。
  - 没有通配 include 时改为明确失败并要求增加专用目录，不再生成语法正确但永不生效的网关文件；同步单后台配置执行单和企业部署入口说明。
- How:
  - 扩展 `verify-internal-single-config.sh`：覆盖通配目录成功生成/安装网关，以及显式 include 单文件时拒绝同级目录的回归；运行 Shell 语法、配置生成回归、完整 HTTP 域名企业打包、ZIP/SHA 和前后端 `--validate-only`。
- Result:
  - 修复后的 HTTP 域名版 `deploy/internal/dist/test-agent-internal-release.zip` 构建成功，SHA256 为 `2a7e602eda32055679f5dfe616da4d3e02a7f3e1a07fb6a46ce2db3eaf8b77e1`；包内前端仍固定为 `http://mimo.sdc.cs.icbc:9996`。
  - 现场旧包无需重启 Java/worker即可修复：在现有通配 include 目录或新建的专用通配目录设置 `TEST_AGENT_NGINX_CONF_PATH`，重新执行前端部署。未变更 API、事件、数据库、依赖、终端权限或标准安全默认。

### 2026-07-20 - 生成 HTTP 企业域名版最终离线包

- Why:
  - 用户确认企业前端域名已由现有环境解析，但现场不采用 HTTPS，也不能由应用部署方直接调整企业 DNS；此前按 HTTPS/WSS 构建的前端包不符合最终入口。
- What:
  - 重新抓取并比较 `origin/main`、`github/main`，本地仍包含两个远程的全部代码；以 `http://mimo.sdc.cs.icbc:9996` 作为前端 API 基址重新构建完整企业离线 ZIP。
  - 标准仓库模板继续保持生产 WSS 安全默认；现场若必须使用服务器终端，需要在真实 `backend.env` 中清空公开 WSS 基址并显式设置 `TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true`，由浏览器直连签票 Java 的 `ws://122.233.30.114:8080`。
- How:
  - 运行单后台配置生成回归、Shell 语法检查、完整 `package-release.sh`、前后端交付脚本 `--validate-only`、ZIP/SHA 校验和不安全 WebSocket 显式开关单测。
  - 校验前端编译产物只包含 `http://mimo.sdc.cs.icbc:9996`，不含此前 HTTPS 域名或旧 IP API 基址；OpenCode 1.17.8 与 Tool 运行时依赖加载成功。
- Result:
  - 最终 HTTP 域名版 `deploy/internal/dist/test-agent-internal-release.zip` 构建成功，SHA256 为 `d3897116183e96828b2036c391bfac8db6b60238e9ddab0e9fa99dfca9438109`；ZIP 与同名 `.sha256` 需上传两台服务器的 `/data/0709/`。
  - HTTP/WS 会使登录凭证和终端内容在网络中明文传输；服务器终端还要求浏览器网段直达 `.114:8080`。未修改 API、事件、数据库、依赖或标准安全默认，仅生成站点专属前端产物并记录现场配置边界。

### 2026-07-20 - 生成企业域名终端版最终离线包

- Why:
  - 用户尚未实施 Nginx 域名/TLS 和 Docker 出网规则，需要基于最新代码与已提交的服务器终端默认参数重新生成最终企业包，并给出可从零执行的部署顺序和预期结果。
- What:
  - 重新抓取并比较 `origin/main`、`github/main`；两端均停留在 `cc89296e0`，本地 `main` 已包含全部远程提交并额外包含 `57a651251` 终端默认启用提交，无远程代码需要合并。
  - 以 `https://mimo.sdc.cs.icbc:9996` 作为生产前端 API 基址重新构建 Java、前端、OpenCode 1.17.8、Linux/amd64 worker、外置 programs 和完整离线 ZIP；未修改真实 `.env.local` 或服务器配置。
- How:
  - 运行企业单后台配置生成回归、开发脚本校验、Shell 语法检查和完整 `package-release.sh`；校验 ZIP SHA256、压缩结构、包内终端默认参数、前端编译域名、worker 镜像架构及 Tool 运行时依赖。
  - 前端产物确认含 `https://mimo.sdc.cs.icbc:9996` 且不含旧的 `http://122.233.30.2` API 基址；worker 内 `@opencode-ai/plugin`、SDK、Effect、Zod、node-pty 均可加载。
- Result:
  - 最终包 `deploy/internal/dist/test-agent-internal-release.zip` 构建成功，SHA256 为 `bf8b5174ee637eca2be29a96130fc4e0060a0ffb7065c2e1e857ac90569113cb`；同名 `.sha256` 需一并上传内网 `/data/0709/`。
  - 企业服务器仍需现场配置域名 DNS、Nginx 9996 TLS/WSS、后端精确 CORS 和 Docker FORWARD/MASQUERADE 后再启动验收；这些是环境操作，不写入仓库或交付包。未新增或变更 API、事件、数据库、SQL/migration、依赖或权限模型。

### 2026-07-20 - 企业交付模板默认启用服务器终端

- Why:
  - 企业内部署后签票接口返回“服务器终端未启用”；用户确认企业模板应直接启用，无需每次部署再手工把 `TEST_AGENT_SERVER_TERMINAL_ENABLED` 从 `false` 改成 `true`。
- What:
  - `deploy/internal/backend.env.example` 默认显式设置 `TEST_AGENT_SERVER_TERMINAL_ENABLED=true`，保留 `/data/testagent` 工作目录和强制 `wss://122.233.30.2` 公开地址；Spring 应用在未配置变量时的安全兜底仍为关闭。
  - 单机配置生成脚本新增终端启用值和 WSS 地址断言，避免后续模板回退；同步单/多后端完整配置、部署、安全和 HTTP API 文档。
- How:
  - 运行 Shell 语法检查，并在隔离临时目录实际执行 backend 配置生成，确认输出为 `true`、`/data/testagent` 和 WSS 地址；未改真实 `.env.local` 或企业服务器现有配置。
  - 完整运行 `deploy/internal/package-release.sh --output-dir deploy/internal/dist`，构建 Java、前端、Linux/amd64 worker 和最终离线 ZIP；对 ZIP 执行完整性、包内模板和 SHA256 校验。
- Result:
  - 新企业包 `deploy/internal/dist/test-agent-internal-release.zip` 构建成功，SHA256 为 `1ccb10ebf0781f3d3627e61d289968a68d40a1d5fd9726867de197e2362b20a2`，包内服务器终端配置已确认默认启用。
  - 现有企业服务器仍需用新包重新生成 `/data/testagent/config/backend.env`（或等价地改为 `true`）并重启 Java；Nginx TLS 与按 `linuxServerId` 的 WSS 精确路由仍是启用前提。未新增或变更 API 路径、RunEvent/SSE、数据库、SQL/migration、generated SDK、依赖或权限。

### 2026-07-20 - 合并最新远程并生成企业离线部署包

- Why:
  - 用户要求在保留公共/应用个人配置热加载与宠物入口改动的前提下合入最新远程代码，并重新生成可导入内网的企业全量部署包。
- What:
  - 将本地四个提交重放到 `origin/main` / `github/main` 共同基线 `9f8cb2b1b`，冲突处理同时保留远程夜间任务会话锁、会话列表等能力与本地用户级 dispose 闸门、七种宠物和 Agent 配置更新入口。
  - 合并后消除 `backend/README.md` 中自动产生的 `test-agent-opencode-runtime` 重复模块说明；未修改真实环境文件，部署包不包含 `ssh-rsa-private.key`、`backend.env`、`docker.env` 或 `.env.local`。
  - 重新构建 Linux/amd64 企业离线包 `deploy/internal/dist/test-agent-internal-release.zip`，SHA256 为 `a5b59c6b91b96a8d3e9153102909712de40e9830c6e91d2d4adb43a5165d13ef`。
- How:
  - 后端聚焦运行态/API/Redis 回归共 76 项通过；前端全量 Vitest 86 个文件 1427 passed / 1 skipped，工作区全量 typecheck 通过。首次前端测试与 Maven/typecheck 并发时有 3 项超时/异步等待抖动，单独复跑及随后全量独占复跑均通过。
  - 企业打包脚本完成 Java、前端、OpenCode 1.17.8、manager、Linux/amd64 Worker 和自定义 Tool 依赖构建；SHA256、`unzip -t`、Worker 镜像运行时检查及前后端 `--validate-only` 均通过。
- Result:
  - 最新企业离线包可上传到内网 `/data/0709/`，ZIP 与同名 `.sha256` 必须成对上传。包内示例配置不覆盖服务器 `/data/testagent/config/` 下的真实配置和持久私钥。
  - 本次收口不新增 API、RunEvent/SSE、数据库 migration、依赖、环境变量或鉴权语义；远程基线自带的既有 migration 仍由后端启动时按原流程执行。未在本机替用户重启内网服务。

### 2026-07-19 - 修复个人运行态重载的跨会话竞态

- Why:
  - 个人 Agent 配置热加载原先只看当前页面 Run，手动与自动入口使用不同锁；后端 `/global/dispose` 会释放当前用户全部 Workspace Instance，却没有覆盖宠物/手册旁路问答和 legacy 新消息入口，也缺少覆盖 OpenCode 超时重试的续租。
  - Redis Run 初始化在闸门拒绝后可能残留 `runtime-user` marker，误导运行态摘要跳过 legacy 活跃 Run；初始化脚本参数新增后也使既有 persistence 测试失配。
- What:
  - 新增 `UserRuntimeDisposeCoordinator`：在 `{userId}` slot 原子清理过期 active、确认空闲并申请 token 闸门，再复核用户全部 Session；两分钟租约每 30 秒按 token 续租，应用与公共个人重载共用该协调器。
  - 主 Run、宠物/手册旁路问答及 legacy sideQuestion/command/shell（含非默认 Agent）统一检查 dispose 闸门。新 Redis Run 在用户 slot Lua 内先检查闸门，再登记 `active:user` 并以随机 owner 建立 marker；拒绝发生在 Session、服务器、历史索引及 marker 写入前。单 Run `{runId}` 初始化 Lua 保持原 13 参数，不跨 Redis Cluster slot。
  - 前端以 `sessionRuntimeState.runningCount` 补齐用户级 busy，手动/自动重载共用响应式串行锁；公共重载不再依赖应用工作区选择。自动保存收到后端 `CONFLICT` 时保留 revision 和公共 worktree 目标，在用户空闲后或短延迟复核时重试。
  - 同步 runtime/persistence/agent-web README、persistence PACKAGE、HTTP API 和后端 Redis Lua 规范；没有新增 HTTP 接口，继续使用既有应用 `global/dispose` 与公共个人 `public/runtime-reload`。
- How:
  - Redis 用户闸门、active 索引和 marker 的脚本全部使用同一 `{userId}` hash tag；单 Run详情继续使用 `{runId}`，避免 Redis Cluster `CROSSSLOT`。闸门申请、续租和释放均以随机 token fencing，旧 owner 不能释放新租约。
  - 测试覆盖 marker 写入前拒绝、13 参数初始化契约、过期 active 清理、租约续期/丢失、全部新消息入口、非默认 Agent、公共工作区独立、用户级按钮 busy 和前端全量回归。
- Result:
  - persistence 定向 5 项通过；runtime 核心 125 项通过，非默认 Agent 加固后相关 49 项再次通过；后端 17/18 模块 app 打包与启动脚本 clean package 均成功。
  - 前端 typecheck、全量 Vitest 79 个文件（1323 passed / 1 skipped）和生产 build 通过；仅保留既有 canvas 提示与大 chunk warning。
  - 按 JDK 25、`.env.test`、test profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP，前端 3000 返回 200，manager WebSocket 已连接并应用配置。
  - 不涉及新 API 路径、RunEvent/SSE、数据库、SQL/migration、generated SDK、依赖或环境配置；兼容未接入用户闸门的旧 `RunRuntimeStore` 实现。无未完成事项。

### 2026-07-19 - 收紧宠物配置更新入口并统一左侧 Agent 操作布局

- Why:
  - 用户要求 Agent 配置更新只出现在小宠物对话页，宠物选择页不展示；左侧 Agent 区域仍保留入口，并希望公共/应用两行的刷新图标位置统一。
- What:
  - `FigmaShell.vue` 进入宠物选择页时清理运行态确认，配置更新操作与确认块仅在对话页渲染；新增选择页隐藏入口回归。
  - `AgentConfigPanel.vue` 将公共/应用根节点动作收进统一动作容器，公共“更多操作”与应用初始化按钮均置于刷新按钮之前，刷新图标固定为动作组最右侧；不改变权限门禁和事件 payload。
  - README、PACKAGE、模块图和 Agent 配置手册改为“Agent 配置更新”口径，明确选择页不展示且复用既有接口。
- How:
  - 继续复用 `AgentWorkbench` 已有的 `disposeGlobal()` 和 `reloadPublicPersonalAgentRuntime()`，没有新增接口、事件、后端代码或 API 文档契约。
  - 先回顾全部 `.agents/session-log*.md`，再执行组件测试、类型检查、用户手册和生产构建；初次 workspace filter typecheck 被 `temp/workspace` 缺少 node_modules 的重复包阻断，改在 agent-web 包目录直接执行后通过。
- Result:
  - FigmaShell/AgentConfigPanel 定向 64 项通过；合并宠物头像与偏好回归后 4 个文件共 79 项通过。
  - `frontend/apps/agent-web` 目录内 `vue-tsc --noEmit --pretty false`、VitePress 手册构建和 Vite 生产构建通过；既有大 chunk warning 保留。`http://127.0.0.1:4177/` 预览返回 HTTP 200。
  - 未做真实登录态点击验收；需要在有权限的工作台确认对话页按钮可见、选择页隐藏，并验证两类既有接口的实际返回。

### 2026-07-19 - 更换七种宠物并增加个人运行态重载入口

- Why:
  - 用户提供七张新宠物素材，要求替换工作台头像，同时需要分别验证应用个人和公共个人 OpenCode 配置的手动热加载。
- What:
  - 将新素材去除棋盘背景并转换为 512px RGBA 头像，替换旧五种图片角色，保留旧角色 ID 的兼容映射；默认宠物、名册底色和说明文档同步更新。
  - 旧五张素材文件保留在 assets/pets 目录但不再导入；在首次点击小宠物打开的旁路面板增加“Agent 配置更新”入口，公共和应用按钮先展示影响范围确认，再由工作台分别复用既有公共 runtime-reload 和应用 global dispose 接口，运行中任务禁用，公共请求携带当前用户 worktree/server。
- How:
  - 保留 `PetCompanionAvatar` 的进程状态光圈和 ready/异常状态映射不变；AgentConfigPanel 只发事件，AgentWorkbench 负责接口调用、运行态目录重新查询和反馈，避免配置面板直连运行时。
  - 更新 agent-web、前端模块图和用户手册，新增面板事件回归；未新增 API、事件、数据库、环境配置、依赖或 generated SDK。
- Result:
  - `vitest` 定向 3 个文件 31 项通过；agent-web `vue-tsc`、VitePress 用户手册构建、Vite 生产构建通过；构建产物预览 `http://127.0.0.1:4177/` 返回 HTTP 200。
  - 未执行真实登录后的按钮点击验收；需要在有权限且进程 READY 的工作台中首次点击宠物后分别确认两个按钮，观察确认提示、后端返回和运行态目录更新。

### 2026-07-19 - 补齐分支模型测试数据并修复公共个人热加载 500

- Why:
  - 用户要求同时准备可真实提交/推送的隔离 Git 数据、当前应用/公共个人 worktree 的本地 OpenCode 热加载数据，并给出可执行步骤与明确通过标准。实际验收公共个人保存入口时发现 `POST /agent-config/public/runtime-reload` 在 WebFlux 事件线程内调用 Reactor `block()`，软链接已经切换但接口返回 500，形成部分成功状态。
- What:
  - 扩展 `tools/create-workspace-branch-model-test-data.sh`：每次创建应用/公共两个本地 bare remote、已推送基线、发布就绪个人 worktree、clean/dirty/真实冲突和公共个人数据；README 生成可复制的安全 commit/push 命令，并断言个人分支和 `spec/**` 不进入远程 feature。
  - 脚本增加成对的可选真实 worktree 参数，只新增带唯一 tag 的 docs、archive、spec、应用/公共 Agent、Skill 与 rules 未提交样例；不覆盖同名文件、不提交、不推送真实 Gitee。本机已在 F-COSS 应用个人 worktree 和 `public-usr_test_dev` 造入 `20260719` R1 数据。
  - `docs/testing/application-worktree-feature-cases.md` 细化测试设计、隔离数据、Git/热加载/权限/rollout 案例和逐项通过标准；同步 API、API/runtime 模块 README。
  - `AgentConfigController.reloadPublicPersonalRuntime` 把本地同步重载与跨服务器转发统一调度到 `boundedElastic`，避免占用 WebFlux 事件线程；控制器回归明确拒绝在 non-blocking thread 调用同步业务端口。没有修改 OpenCode 原生代码、配置发现规则或 generated SDK。
- How:
  - 复用既有个人 worktree、feature 投影、原生 `git merge --no-edit`、公共受管软链接和 OpenCode `/global/dispose`，没有新增 Git 或 dispose 平行实现。隔离 fixture 的 `origin` 全部为 `.tmp` 下 bare path；真实个人数据保持未提交，供 UI 把 R1 改为 R2 后用 Command/Ctrl+S 验证。
  - 使用 fixture 实际完成应用个人 commit、选择性 feature push 和公共 `HEAD:main` push，确认远程包含 docs/archive/Agent/Skill/rules、不含 spec，也不存在个人分支。按 JDK 25、`.env.test`、test profile 完整重启，初始化当前用户 OpenCode 后真实调用修复后的公共 runtime reload。
- Result:
  - 后端真实 Git/workspace/runtime/API 相关 95 项通过；前端保存、Diff 与 Agent 配置 API 相关 41 项通过，agent-web typecheck 通过；脚本语法、两次生成、Git `fsck`、`git diff --check` 通过。
  - 真实 `runtime-reload` 返回 HTTP 200、`reloaded=true`，公共指针从共享目录切到 `public-usr_test_dev/opencode`；当前 OpenCode 在 4096 健康，应用个人和公共个人 Agent/Skill 的 R1 均可从同一 directory 查询，重启后的后端日志不再出现 Reactor blocking 错误。
  - backend readiness 为 UP、前端 3000 为 200、manager 无 decode/reconnect 循环。未推送真实应用/公共远程，未执行真实多用户或多服务器 rollout；R1 测试文件有意保留未提交，等待用户按文档完成 R1→R2 保存和选择性提交测试。
  - 本轮修复既有内部 HTTP 端点的线程调度，不改变 URL、DTO 或响应结构；不涉及 RunEvent/SSE、数据库、SQL、migration、环境文件、安全权限或 generated SDK。`boundedElastic` 只承接既有最长 10 秒的同步 dispose 等待，避免阻塞事件循环。

### 2026-07-19 - 细化工作区 Git、配置软链与保存热加载文档

- Why:
  - 用户确认实现符合预期，要求把应用普通文件范围、`spec/**` 约束、当前本地 `OPENCODE_CONFIG_DIR` 关系和 Ctrl/Cmd+S 保存语义写清，并整体复核分支、配置与 dispose 逻辑。
- What:
  - `docs/testing/application-worktree-feature-cases.md` 增加普通文件/应用 Agent Diff 边界、发布前置条件、公共分支选择语义、本地 session/受管软链实例、保存入口与热加载文件白名单，以及未初始化进程下应用配置和公共个人预览的不同结果。
  - 修正 `frontend/README.md` 和 `docs/api/http-api.md` 中“公共保存不热加载”的旧口径；同步 workspace README、agent-web PACKAGE 和部署文档。未修改实现代码、OpenCode 源码、API 契约、事件、数据库、环境文件或 generated SDK。
- How:
  - 对照 `ManagedWorkspaceApplicationService`、`PersonalAgentConfigRuntimeReloadService`、`PublicAgentConfigRolloutService`、`OpencodeProcessConfigLinkService`、`AgentWorkbench`、`agentFileLoad` 及其测试；确认无运行进程时应用 `.opencode` 下次 bootstrap 生效，但未推送公共个人预览需在进程 READY 后再次保存或正式推送。
- Result:
  - 后端相关 203 项、前端 44 项与 agent-web typecheck 通过；AI 文档校验、真实 Git fixture、读者问题契约和 `git diff --check` 通过。运行态 backend readiness UP、前端 200、OpenCode 4097 健康，manager `configPath` 与 `current-public-config` 软链一致且当前指向共享公共配置。

### 2026-07-19 - 公共个人配置固定指针与保存热加载

- Why:
  - 应用个人 `.opencode` 已能随个人 worktree 原生加载并在保存后 dispose 本人；公共个人 worktree 仍只能等推送后全局生效，无法在发布前只让当前超管调试。用户确认公共和应用个人的 Agent/Skill/JSONC 保存都应只热加载本人，推送后再按各自发布范围排空，同时禁止新增 OpenCode 四层配置解析或配置副本 runtime。
- What:
  - 每用户进程的 `OPENCODE_CONFIG_DIR` 固定为 `{sessionPath}/.testagent-runtime/current-public-config` 受管软链接：启动默认指向服务器公共共享副本；公共个人保存时原子切到本人 `public-{userId}` worktree 的 `opencode/` 并只调用本人 `/global/dispose`；公共发布排空时恢复共享指针后再 dispose。
  - 新增 `POST /agent-config/public/runtime-reload`，复用公共 worktree 的 owner、服务器和 Java 路由校验。应用个人保存继续直接 dispose 本人，不切换公共指针；应用 Agent/Skill 发布只对已包含固定 feature commit 的目标用户 dispose。
  - manager `start` 显式接收、保存和校验 `configPath`，重启保留该路径；健康旧进程路径与请求不一致时拒绝幂等复用。公共 rollout target 持久化查询补回 `config_scope`，确保 PUBLIC 才恢复共享指针、APPLICATION 不触碰指针。
  - 同步分支模型、配置加载、角色权限、保存/提交/推送影响、dispose 时机、API/manager 协议、部署和模块 README/PACKAGE；测试数据脚本生成了 clean、dirty、冲突和公共个人 fixture。
- How:
  - 复用 OpenCode 官方 `OPENCODE_CONFIG_DIR`、请求工作区 `.opencode` 和原生 `/global/dispose`；不复制配置、不创建应用 runtime、不修改 OpenCode 配置目录解析。软链接采用同目录临时链接加 rename，普通文件/目录占位、无权限或不支持软链接时明确失败，不删除未知内容、不降级复制。
  - 应用 `.opencode/node_modules` 仍由既有企业离线兼容层建立包级软链接，统一指向 programs 只读依赖；它不是公共 Git worktree 或配置 runtime。本轮未修改 `opencode-source` 或 `deploy/internal/opencode-node-compat.patch`。
- Result:
  - workspace/runtime/API 等 14 个相关后端模块测试全部通过；本轮运行时模块 595 项、API 311 项通过，新增 MyBatis scope 映射 4 项通过。扩大到 persistence 全量时仍被既有 `V20260717173000` 的 PostgreSQL `timestamptz` 与 H2 不兼容阻断（76 errors），本轮无 migration，未扩大范围改写已执行迁移。
  - `go test ./...`、前端全工作区 typecheck、定向 Vitest 5 项、`git diff --check` 与分支模型真实 Git fixture 通过。
  - 按 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；readiness 为 UP、前端 3000 返回 200、manager WebSocket 已连接并应用配置。随后通过本地测试账号和平台初始化 API 启动用户 OpenCode：4097 达到 `READY`，原生 `/global/health` 返回 200/`healthy=true`（1.17.7）；manager state 的 `configPath` 与实际 `OPENCODE_CONFIG_DIR` 均为用户 session 下的 `current-public-config`，该软链接当前指向服务器公共共享配置目录。
  - 新增一个内部 HTTP API 和 manager command 可选兼容字段；不新增 RunEvent/SSE、数据库结构、环境文件或 generated SDK 修改。旧版仍直接读取共享 `configPath` 的存量进程需要受管重启一次；不支持软链接的平台会显式失败。
- Verification:
  - `mvn -pl test-agent-api,test-agent-persistence -am test`（至 API 全部通过；persistence 仅既有 H2 migration 基线失败）
  - `go test ./...`
  - `corepack pnpm typecheck && corepack pnpm vitest run apps/agent-web/tests/agent-file-load.test.ts packages/backend-api/tests/agent-config-update.test.ts`
  - `tools/create-workspace-branch-model-test-data.sh`、`git diff --check`
  - `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`

### 2026-07-19 - 应用 feature 固定提交反向合并个人 worktree

- Why:
  - 上一版仅对应用 Agent 白名单做反向投影，普通 docs 推送后仍要求其他成员手动更新；用户确认应用普通文件与 Agent/Skill 都应从 feature 自动反向同步个人 worktree，冲突直接进入现有 Diff，同时公共配置仍保持既有分支与推送模型。
- What:
  - 应用 feature push、跨服务器版本广播、版本副本补偿和兼容 `sync-from-application` 统一按固定 `targetCommitHash` 对本服务器相关个人 worktree 执行原生 `git merge --no-edit <targetCommit>`。clean 时快进或 merge；dirty/staged/untracked 时不 stash/reset/覆盖并标记待同步；冲突保留 `MERGE_HEAD` 与三方 index。
  - 工作区 Diff 新增向后兼容的 `mergeInProgress/applicationUpdatePending/applicationTargetCommit`；全部冲突解决后新增 `POST /workspaces/{workspaceId}/git-conflict/complete` 提交完整 merge index，包含 `.opencode/**` 时继续要求 `APP_ADMIN`。前端在 workspace 与应用 Agent 作用域展示待同步、三方冲突和“完成合并/取消合并”。
  - 应用 Agent/Skill rollout 改为等待本服务器相关个人 worktree 全部包含固定提交后再登记目标用户并走既有全局 dispose；未收敛时保留持久化 retry。普通 docs 只做 Git 合并，不 dispose。
  - 保存时热加载边界收紧：应用个人 worktree 的 Agent/Skill 目录定义与 JSONC 只 dispose 当前用户供调试；公共 Agent/Skill 保存不 dispose，仍以公共分支推送后的全服务器 rollout 为生效边界。
  - 新增 `tools/create-workspace-branch-model-test-data.sh`，在 `.tmp` 生成公共个人、应用 feature、成功 merge、dirty 待同步和真实 `MERGE_HEAD` 冲突 fixture；重写分支模型测试文档并同步 HTTP、广播、模块和前端 README。
- How:
  - 复用 `ManagedWorkspaceApplicationService`、服务器版本广播、个人 worktree、`PublicAgentConfigRolloutCoordinator`、既有三方冲突编辑器和 OpenCode 原生 `/global/dispose`；没有引入应用配置覆盖层，不修改 OpenCode 源码、generated SDK、manager 协议、数据库或环境文件。
  - feature 发布仍只从个人 `HEAD` 定点投影所选非 `spec/**` 路径，个人分支不 push，`spec/**` 对所有角色继续仅本地。反向同步按完整固定 commit 保留 Git 历史，并在本地提交、回退、重新进入 default worktree、版本广播和副本补偿时重试。
- Result:
  - 后端定向真实 Git/workspace/API 共 75 项通过，前端 Agent 路由与 Git 面板 38 项通过，agent-web typecheck、AI 文档校验、脚本语法、`git diff --check` 和完整前后端生产构建通过。
  - 按 JDK 25、`.env.test`、`test` profile 重启 backend、opencode-manager、frontend；readiness 为 UP、前端 200、CORS 正常、manager WebSocket 已连接并应用配置。通过平台初始化默认测试用户后，受管 OpenCode 在 `127.0.0.1:4096` 达到 `READY`，原生 `/global/health` 返回 `healthy=true`、版本 1.17.7；启动日志仅有既有 macOS Netty DNS native fallback。
  - 新增一个内部 HTTP 完成合并入口和三个可选/默认兼容的 Diff 字段；不新增 RunEvent 或广播类型，不改变广播 payload，不涉及数据库/API 外网兼容、性能敏感全表扫描或凭据输出。真实多服务器人工发布仍需目标环境验收，当前由本机真实 Git fixture 与服务测试覆盖。
- Verification:
  - `mvn -pl test-agent-common,test-agent-workspace-management,test-agent-api -am -Dtest=GitWorkspaceServiceRealGitTest,ManagedWorkspaceApplicationServiceTest,ManagedWorkspaceControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `corepack pnpm vitest run apps/agent-web/tests/agent-file-load.test.ts apps/agent-web/tests/git-changes-panel.test.ts`
  - `corepack pnpm --filter @test-agent/agent-web typecheck`
  - `tools/create-workspace-branch-model-test-data.sh`、`tools/verify-ai-docs.sh`、`git diff --check`
  - `./restart-dev-services.sh --profile test --env-file .env.test`

### 2026-07-18 - 修复编辑器复制 Agent 合成路径

- Why:
  - 编辑器页脚把 `agent-workspace:<workspaceId>:::<encodedPath>` 合成 tab 路由当作相对路径拼到 workspace 根目录，剪贴板因此出现 `:::`、`%2F` 和无效绝对路径。
- What:
  - Agent 配置树从公共 worktree/服务端 `agentDirectory` 解析真实绝对路径并固化到 tab；页脚只复制这一条绝对路径，合成 path 继续仅用于身份与读写路由。
  - 同步 frontend、agent-web README/PACKAGE、前端规范和模块地图；未改 HTTP/WebSocket 契约、RunEvent、数据库、后端或 generated SDK。
- How:
  - 复用 `AgentConfigStatus.agentDirectory`、公共 `publicSource`、现有 `AgentFileLoadRequest` 和 `EditorTab`，补普通/Windows/公共 Agent/应用 Agent 回归；Vitest 必须从 frontend 根使用 `--config vitest.config.ts`，否则会缺少 jsdom。
- Result:
  - 定向 Vitest 4 文件 32 项、agent-web typecheck、用户手册与 agent-web 生产 build 通过；按 `.env.test`/`test` 重启三服务后 backend health/readiness UP、frontend 3000 为 200、CORS 与 manager WebSocket 正常。

### 2026-07-18 - 校正 Agent 分支模型与应用发布定向热加载

- Why:
  - 公共 Agent 原有 `public-{userId}` 编辑分支、推送 `master` 和跨服务器 rollout 模型已经正确；此前把公共个人、应用 feature 和应用个人 worktree 误当成 OpenCode 运行时覆盖层，复杂化了实现。
  - 应用普通 docs 与应用 Agent/Skill 的发布效果不同：docs 只应通知其他成员手动更新个人工作区，Agent 配置发布则需要在不覆盖个人调试改动的前提下同步并热加载。
- What:
  - 完整撤销 OpenCode 1.17.8 原生四层加载、三个新增启动环境变量及离线补丁内容；运行时保持原生模型：公共配置由 `OPENCODE_CONFIG_DIR` 加载，应用配置由当前个人工作区 `.opencode` 加载。`OPENCODE_REFERENCES_DIR` 引用能力保留。
  - 公共 Agent 流程不变。应用普通 docs 推送 feature 后继续广播版本更新，其他成员收到更新提示但个人 worktree 不自动覆盖，用户在“更新个人工作区”时同步。
  - 应用 Agent/Skill/`opencode.json(c)` 推送 feature 后，各服务器只把白名单精确投影到本机无 `.opencode` 脏改动的成员个人 worktree；使用 `git commit --only` 保留普通 docs/spec 的 staged/dirty 状态。存在个人配置改动的用户整组跳过并写失败审计，不 dispose。
  - 对成功同步的用户按精确 PID/启动时间登记 rollout target，等待运行空闲后定向调用 `/global/dispose`；端口复用或身份尚未收敛时重试，不能误排空或漏热加载。个人 Agent/Skill/引用 JSONC 保存仍只排空当前用户进程。
  - 应用 Agent Diff、暂存、提交和发布白名单包含 `.opencode/opencode.jsonc`；同步 workspace/runtime、HTTP API、部署、模块图和前端说明。
- How:
  - 复用现有 `PublicAgentConfigRolloutCoordinator`、版本同步广播、个人 worktree、`materializeCommitFiles`、workspace sync 审计和公共 rollout 排空状态机；没有新增平行 Git/dispose 实现，也没有修改 generated SDK、manager 协议或 `.env.local`。
  - 新增 Git 白名单查询与 `commit --only` 原语；应用发布按活跃成员和当前服务器个人 worktree 收敛，普通工作区内容不参与自动投影。
- Result:
  - 定向测试通过：真实 Git 9 项、workspace management 50 项、rollout/runtime 19 项、启动服务 12 项；前端全量 79 个文件通过（1313 passed / 1 skipped），全工作区 typecheck 通过。
  - 后端全量 `mvn test` 中本轮涉及模块及 API 均通过，最终仍被既有公共 rollout migration `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（persistence 76 errors）；已执行迁移未改写，避免真实测试库 Flyway checksum 冲突。
  - 按 `.env.test`/`test` profile 完整构建并重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 为 200、CORS 200、manager WebSocket 已连接。manager 使用标准 `/Users/kaka/.opencode/bin/opencode`，未运行自定义四层 OpenCode 源码。
  - 先前测试用应用资产引用关系和个人 `.opencode/opencode.jsonc` 保持删除状态；`.config` 当前仍以 `origin/master@3c89512` 为运行/更新事实源，`enterprise@1ad3d20` 只可评审后选择性合并，不能自动覆盖。
  - 未新增 HTTP 路径或 RunEvent/SSE；保留已执行的应用 rollout scope 数据库兼容迁移，无新 migration。安全上不覆盖个人配置脏改动，进程定向采用精确身份；性能开销仅发生在应用 Agent 发布时，按活跃成员及其本机 worktree 有界执行。

### 2026-07-18 - 更新公共 OpenCode 配置并重启引用功能环境

- Why:
  - 用户需要让最新引用功能代码使用更新后的公共 OpenCode 配置重新启动，并确认远程 `enterprise` 分支与当前配置的事实源关系。
- What:
  - 主项目已位于包含远程最新引用提交 `d1ba3f8c7` 的本地 `main@6e3124457`；公共配置仓库 `master` 从 `37c9ef8` 快进到远程最新 `3c89512`，OpenCode 原生 Agent 配置解析通过。
  - 公共配置 `enterprise@1ad3d20` 与 `master@3c89512` 从 `750c8e9` 分叉：`enterprise` 独有 1 个企业 provider 配置提交，`master` 独有 4 个 Agent/Skill 迭代提交。当前本地 test 环境以 `master` 为准；企业部署应保留 `enterprise` 的 provider 环境变量/内部代理配置，并单独同步 `master` 的新 Agent/Skill，不能用任一分支整树覆盖另一分支。
  - 对比发现远程 `master` 的 `opencode.jsonc` 存在硬编码 API key 候选，而 `enterprise` 使用环境变量引用；未记录凭据值、未擅自修改配置，后续若将 `master` 用于企业交付需先移除并轮换相关凭据。
- How:
  - 刷新主项目两个远端和公共配置远端，核对远端 HEAD、提交祖先关系、左右提交数、文件差异与工作区洁净状态；使用 `OPENCODE_CONFIG_DIR=... opencode agent list` 校验配置。
  - 按 JDK 25、`.env.test`、`test` profile 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`，由统一脚本构建并重启 backend、opencode-manager、frontend 和按需用户 OpenCode 进程。
- Result:
  - 后端 18 模块打包成功（测试按启动脚本跳过）；backend health/readiness 为 `UP`，前端 `127.0.0.1:3000` 返回 200，登录 CORS 预检通过，manager WebSocket 已连接且心跳正常。
  - 用户 OpenCode 进程已在 `4096` 启动，`/global/health` 返回 `healthy=true`、版本 1.17.7；启动初期旧 4097 状态探测失败已由新 4096 进程健康状态收敛。
  - 未修改业务代码、API、事件、数据库、环境文件、generated SDK 或稳定文档；主项目仍为 clean 且相对 `origin/main` ahead 1，公共配置 `master` 与 `origin/master` 对齐且 clean。

### 2026-07-18 - 调整小宠物默认与最大尺寸

- Why:
  - 用户希望小宠物默认更大，并允许更大的桌面展示尺寸。
- What:
  - 将无历史缩放偏好的默认值调整为 150%，上限调整为 250%；保留已有明确缩放偏好，并补充滑杆、边界、位置夹紧和持久化测试。
- How:
  - 复用现有 `normalizePetScale`、本地 `test-agent.pet-companion.v1` 和兼容旧版 Chromium 的普通 range input；尺寸仍通过根元素 width/height 计算，未修改 API、RunEvent、数据库或环境配置。
- Result:
  - 定向宠物偏好与 FigmaShell 测试 51 项通过；agent-web 类型检查、生产构建通过；Vite preview 在 `127.0.0.1:4176` 返回 HTTP 200。

### 2026-07-18 - 将设置引导切换到真实页签

- Why:
  - 用户反馈第 08 步锚定在左侧“版本库管理”入口，却混合展示多个页签的操作，无法按当前页面完成配置。
- What:
  - 首次引导 v7 将应用管理员路径拆为 08“版本库管理”、09“应用人员管理”、10“应用与版本库关联”、11“工作空间管理”，每步只说明当前真实页签；第 12 步进入手册。普通用户仍以 SSH 配置结束并进入第 08 步手册。
  - 引导通过设置面板的真实菜单/页签锚点自动切换页面，异步页签挂载后再定位，设置工作区页签增加引导锚点；测试与手册同步更新。
- How:
  - 复用现有 SettingsDialog、SettingsPanel、SettingsAppWorkspacePanel 和权限分支，只增加初始菜单/页签参数、真实 DOM target 和异步定位轮询；未修改 API、事件、数据库或 Java 代码。
- Result:
  - 定向 4 个前端测试文件 42 项通过；agent-web vue-tsc、用户手册构建和生产构建通过；test profile 三服务重启成功，backend health/readiness、前端首页和手册页面均返回 200/UP。
- Next:
  - 应用内浏览器视觉复核仍可能受既有运行时 `Cannot redefine property: process` 限制，需在可用登录会话中确认实际气泡几何位置。

### 2026-07-18 - 展开应用与版本库及工作区页签说明

- Why:
  - 用户反馈新手引导第 08、09 步只给出概括，无法按设置面板的具体页签和字段完成配置。
- What:
  - 第 08 步按“版本库管理”入口、“应用人员管理”和“应用与版本库关联”分别说明新增、成员、关联与解除；第 09 步展开测试工作库、分支、别名、目录树、保存进度和回工作台选择 workspace/version。
  - 用户手册同步按 Tab 1/2/3 和版本库字段补充部署模式、版本库类型、权限和常见空列表原因；增加引导滚动内容的小标题样式与字段代码样式。
- How:
  - 继续复用现有 SettingsRepositoryPanel、SettingsAppWorkspacePanel 的真实字段和权限边界，只调整引导/手册文案与回归断言，没有新增 API、事件、数据库或 Java 代码。
- Result:
  - 定向 4 个前端测试文件 41 项通过；agent-web vue-tsc、用户手册构建和生产构建通过；test profile 三服务重启成功，backend health/readiness 与前端/手册 HTTP 状态均正常。
- Next:
  - 应用内浏览器登录态视觉复核仍受既有运行时 `Cannot redefine property: process` 限制，需在可用浏览器会话中确认长文案滚动和实际气泡几何位置。

### 2026-07-18 - 提升设置拆分引导版本

- Why:
  - 已浏览过 v5 引导的用户不会重新看到拆分后的设置步骤。
- What:
  - 将首次引导和工作台抑制状态的本地存储版本从 v5 升到 v6，保持 SSH、应用与版本库、应用工作区三步流程对既有用户可见。
- Verification:
  - 定向测试 27 项、agent-web 类型检查和生产构建通过；test profile 三服务重启后 readiness、前端与手册页面返回正常。

### 2026-07-18 - 拆分设置引导并修复弹窗定位

- Why:
  - 用户反馈设置引导内容挤在一个气泡中且气泡因设置弹窗异步挂载落到左上角。
- What:
  - 应用管理员引导拆为 SSH 配置、应用与版本库配置、应用工作区配置三个步骤；普通用户只保留 SSH 步骤和手册入口。
  - 设置菜单项增加真实引导锚点；进入设置步骤后等待弹窗挂载并替换为真实 DOM target，避免无目标定位。
- How:
  - 复用现有 SettingsDialog、SettingsMenu 和权限计算，只增加菜单锚点、角色条件、目标刷新和回归覆盖。
- Result:
  - 定向测试 3 个文件 27 项通过；agent-web 类型检查、用户手册/agent-web 构建通过；test profile 三服务重启成功，readiness、前端和手册 HTTP 状态正常。

### 2026-07-18 - 补充设置引导的具体操作步骤

- Why:
  - 用户反馈设置步骤虽然打开了面板，但没有说明普通用户和应用管理员具体应该点击什么、保存后如何回到工作台。
- What:
  - 首次引导 v5 增加 SSH Key、应用成员、版本库关联、工作空间创建和 workspace/version 选择的短流程；设置手册增加“设置面板怎么用”总览。
- How:
  - 继续复用 SettingsDialog、SettingsMenu 和现有三类配置页面，只调整引导文案、滚动容器、localStorage 引导版本和文档测试断言。
- Result:
  - 定向测试 2 个文件 12 项通过；agent-web 类型检查、用户手册构建、agent-web 生产构建通过；test profile 三服务重启成功，readiness 与前端/手册 HTTP 状态均正常。

### 2026-07-18 - 让新手引导打开设置面板并细化三类配置

- Why:
  - 用户反馈设置步骤只说明齿轮按钮却没有展示真实设置面板，且用户配置、版本库配置、应用工作区配置的操作入口不够明确。
- What:
  - 首次引导 v4 在第 07 步自动打开 SettingsDialog 并锚定设置导航；手册与 FAQ 新增三类配置的入口映射和逐步操作，明确普通用户选 workspace/version 的后续动作。
- How:
  - 复用现有 settingsOpen、SettingsDialog、SettingsMenu、VitePress 和 HelpCenter 链路，仅增加引导事件、真实 DOM 锚点、文案与回归断言。
- Result:
  - 定向测试 3 个文件 26 项通过，agent-web vue-tsc/生产构建和用户手册构建通过；test profile 三服务已重启，后续复核 readiness 与前端 HTTP 状态。

### 2026-07-18 - 新手引导结束后再展示宠物进程面板

- Why:
  - 新手引导进行期间，`NEEDS_INITIALIZATION` 状态 watcher 会自动打开宠物进程面板，遮挡引导内容；用户希望引导结束后再展示。
- What:
  - FigmaShell 新增引导活动态门禁：引导中不自动弹出进程面板，开始引导时会清理已打开的面板，完成或关闭后恢复自动提示。
  - AgentWorkbench 按当前用户的 v4 引导本地记录初始化门禁，并复用 FirstLoginGuide 的 prepare/finish/dismiss 生命周期传递状态；同步前端 README 与 FigmaShell 回归测试。
- How:
  - 仅复用现有 `processStatusInteractionEnabled` watcher、`FirstLoginGuide` 生命周期和 `test-agent.onboarding.v4:{userId}` 本地存储；未修改 API、RunEvent、数据库、环境配置或安全逻辑。
- Result:
  - FigmaShell 定向测试 45 项通过；agent-web vue-tsc/生产构建和 test profile 三服务重启验证通过，readiness 与前端 HTTP 状态正常。

### 2026-07-18 - 统一服务器终端默认配色

- Why:
  - 用户希望服务器终端在不提权、不修改账号配置的前提下，默认区分提示符、目录/文件类型以及 `grep`、`git` 输出颜色。
- What:
  - 服务器 Bash 改为通过 jar 内置、运行时释放到随机临时文件的 rcfile 启动；提示符使用绿色用户/主机和蓝色当前目录，Linux 配置 `ls --color=auto`、macOS 配置 `ls -G`，两端均配置 `grep --color=auto`，Git 使用当前终端能力自动着色。
  - rcfile 最后兼容加载用户已有 `.bashrc`，但不写入用户主目录、系统 shell 配置或全局 Git 配置；服务器 PTY 仍继承启动 Java 的操作系统用户和权限，最小环境仅增加 `COLORTERM=truecolor` 与 `CLICOLOR=1`。
  - 补充 shell 命令、资源内容、非敏感环境和真实 Pty4J 进程回归；同步 runtime README、HTTP API、安全规范和部署说明。
- How:
  - 复用现有 `TerminalProcessFactory`、Pty4J、ticket、WebSocket、限流、超时和审计链路，没有新增 terminal service、shell 插件或权限；临时 rcfile 在 POSIX 系统使用 `0600`，进程退出时由 JVM 清理。
  - 自动化真实输入必须模拟人工速度；Playwright 瞬时逐字符输入会按预期触发既有限流，本次 E2E 使用 70ms 字符间隔验证，不放宽生产限流。
- Result:
  - `TerminalProcessFactoryTest` 6 项通过，`test-agent-app` reactor package 成功；按 `.env.test` / `test` profile / JDK 25 重启 backend、manager、frontend，health/readiness 为 UP、前端返回 200。
  - Playwright 真实登录、二次确认并连接服务器终端后，页面显示彩色提示符，命令返回 `kaka|truecolor|1`，`ls`、`grep`、`git` 别名存在；Java 和 shell 用户均为 `kaka`。未新增或变更 HTTP 路径、RunEvent/SSE、数据库、SQL、migration、generated SDK、依赖或环境配置。

### 2026-07-18 - 服务器终端改为继承 Java 运行用户并取消手工确认文本

- Why:
  - 用户明确不需要 root 或任何额外权限，希望本地和 Linux 都直接使用启动目标 Java 的操作系统用户；原先要求手工输入 `ROOT@linuxServerId` 且校验 UID=0，导致本地无法连接，也增加了不必要的操作步骤。
- What:
  - 服务器终端目标由 `server-root` 改为 `server-shell`，删除 effective UID=0 校验、`HOME=/root`/`USER=root` 等环境伪装；Pty4J 直接启动 `/bin/bash`，操作系统 UID/GID 天然继承 Java 进程，最小环境只写入 Java 用户对应的 `HOME/USER/LOGNAME` 和非敏感基础变量。
  - 前端改为“点击连接服务器终端 → 二次确认目标服务器 → 确认连接”，取消手工输入框；目标绑定值改为 `SERVER@linuxServerId`，取消确认以 `AbortError` 回到 idle，不显示伪失败。所有 root 文案和 API client 方法名同步改为服务器终端语义。
  - 正式环境仍默认关闭并强制 WSS 定向网关；本地 `test` profile 显式启用服务器终端、使用 Java `user.dir` 作为工作目录并允许直连签票 Java 的 `ws://`，未修改 `.env.test` 或 `.env.local`。
  - 同步 HTTP API、安全、部署、多后台、模块与前端包文档；保留 `SUPER_ADMIN`、目标 Java 精确路由、一次性 ticket、Origin、限流、active 租约、超时、清理和无命令正文审计。
- How:
  - 后端 terminal/API 定向测试 22 项通过；前端弹窗、terminal、backend-api 定向测试 77 项通过，13 个前端项目 typecheck、agent-web 生产 build、后端 app reactor package 通过。
  - 按 `.env.test` / `test` profile / JDK 25 重启 backend、opencode-manager 和 frontend，health/readiness 为 UP、前端 3000 返回 200、登录 CORS 和 manager WebSocket 正常。
- Result:
  - Playwright 真实登录后完成“选择服务器工作空间 → 服务器终端 → 二次确认 → WebSocket → 命令输入”，终端执行 `id -un` 写出的用户为 `kaka`，与实际 Java 进程用户一致；终端保持固定高度且可输入。验收截图保存在本机 `.tmp/server-terminal-java-user.png`。
  - 未新增 HTTP 路径、RunEvent/SSE、数据库字段、migration、SQL、generated SDK 或额外权限；`confirmationText` 字段形状保持不变，但确认值从旧 `ROOT@...` 改为 `SERVER@...`，旧前端需与后端同步升级。生产 Linux 仍需按目标 systemd 用户和真实 WSS 网关验收。

### 2026-07-18 - 迁移服务器终端入口并修复 xterm 高度反馈循环

- Why:
  - 用户要求把超级管理员服务器终端放进“选择服务器工作空间”弹窗，并反馈现有 xterm 会持续拉长且无法正常输入。
- What:
  - `ServerWorkspacePickerDialog` 新增绑定左侧当前服务器的“服务器终端”视图，保留 `ROOT@linuxServerId` 逐次确认；切服、返回目录或关闭弹窗都会卸载旧终端并清空确认。运行管理页删除重复入口。
  - `TerminalPanel` 改为有界 viewport + 绝对定位宿主；ResizeObserver 对相同尺寸去重并按动画帧合并 fit，WebSocket open 后同步 cols/rows 并聚焦 xterm。
- How:
  - 继续复用既有 root ticket API、terminal client、xterm/FitAddon 和后端 WSS/PTY 链路，没有新增 API、服务、ticket 类型或安全例外。
  - 前端全量 Vitest 79 files、1273 passed/1 skipped，terminal/agent-web typecheck 与 agent-web 生产 build 通过；`.env.test`/`test` 三服务重启后 health/readiness UP、前端 200、manager health 正常。
- Result:
  - 入口、确认、切服重置、重复 resize 去重、连接后聚焦与键盘 envelope 均有回归覆盖。应用内浏览器运行时因 `Cannot redefine property: process` 未完成登录态视觉点验；macOS 本机默认关闭 root 终端，真实 Linux root + WSS 命令执行仍需目标环境验收。

### 2026-07-18 - 补充设置权限内的新手路径与手册章节

- Why:
  - 用户希望把设置弹窗中普通用户和应用管理员相关的操作纳入新手引导与内置手册，同时不展开超级管理员专属用户管理。
- What:
  - 新增“设置与权限内操作”手册章节并注册到 VitePress、HelpCenter 和宠物问答；引导第 07 步改为说明个人 SSH Key、应用管理、版本库管理和工作空间入口。
  - 同步 FAQ、首次准备、快速开始、手册首页、前端 README/PACKAGE、模块地图，并修正设置角色可见性说明。
- How:
  - 复用现有 SettingsMenu/SettingsAppWorkspacePanel/SettingsRepositoryPanel 的真实权限和操作文案，仅增加手册注册、引导文案与回归断言，没有新增 API、事件或数据状态。
- Result:
  - 定向设置/引导/帮助中心测试 5 个文件 44 项通过；当前 app 的 vue-tsc、用户手册构建、agent-web 生产构建通过；test profile 三服务重启成功，readiness UP、前端 200。构建仍有既有大 chunk 提示。

### 2026-07-18 - 优化普通用户工作区与对话新手路径

- Why:
  - 用户反馈普通用户不知道应用入口、应用选中后工作区仍为空、对话如何建立，以及工作区小地球如何引入需求子条目。
- What:
  - 首次引导 v3 改为锚定真实应用下拉、workspace/version 切换、小地球、新建对话、宠物、设置和手册按钮；明确普通用户不能新建应用、必须选中 workspace/version、首条消息自动建对话。
  - 快速开始、工作区、对话、首次准备、FAQ 和手册首页补充四个入口、空白工作区排查、管理员边界、小地球引入和 `#` 子条目上下文流程；同步前端 README、PACKAGE 和模块地图。
- How:
  - 复用已有 UI、工作区切换、iframe、对话和手册链路，只增加 `data-onboarding` 锚点与文案，没有新增 API、事件或数据状态。
- Result:
  - 5 个前端相关测试文件 185 passed/1 skipped，agent-web typecheck、用户手册构建、agent-web 生产构建通过；test profile 三服务重启完成，backend readiness UP、frontend 200。真实登录态浏览器交互未代填账号，未做登录后视觉验收。

### 2026-07-18 - 发布公共 Mermaid 规约到远端 master

- Why:
  - 用户确认将公共测试设计 Agent 的 Mermaid 11.16.0 规约提交发布到远端主分支，使公共仓库包含该修复。
- What:
  - 将公共配置提交 `3c89512 统一 Mermaid 11.16.0 语法规约` 从现有 `public-usr_test_dev` 分支推送到 `origin/master`。
- How:
  - 推送前执行 `git fetch origin`，确认本地相对 `origin/master` 为 `1 ahead / 0 behind`，随后使用非强制 `git push origin HEAD:master`；最后通过 `git ls-remote` 和远端跟踪引用双重核对。
- Result:
  - 远端 `refs/heads/master` 已从 `37c9ef8` 快进到 `3c89512bae0c6fa681157e61fc4c62e4d8430ed8`，本地公共 worktree clean；未验证平台各节点的公共配置 rollout 状态。

### 2026-07-18 - 公共测试设计 Agent 统一 Mermaid 11.16.0 语法规约

- Why:
  - 公共测试设计 Agent 生成的场景图在节点 label 中直接写入 ASCII 双引号，导致项目使用的 Mermaid 11.16.0 无法解析，图表展示和可视化编辑同时失败。
- What:
  - 在公共 Agent 个人 worktree `public-usr_test_dev` 新增 `test-design/rules/mermaid.md`，集中约束 Mermaid 11.16.0 最低兼容基线、动态 label 转义、ASCII 节点 ID、subgraph 可视化编辑限制和写入/冻结前校验记录。
  - `test-design`、路径法、场景法 skills 以及 generation/review Agents 强制按需读取公共规约；质量门禁和 Phase A manifest 增加 `syntaxBaseline/staticCheck/parserCheck`，模板改为安全的带引号 label 写法，并新增包含 `用户点击"发起取证"` 的回归 eval。
  - 同步公共配置 `README.md` 和 `opencode/AGENTS.md`；公共配置提交为 `3c89512 统一 Mermaid 11.16.0 语法规约`。
- How:
  - 复用现有 test-design rules 加载链路，没有新增平行 Agent、Skill 或运行时代码；parser 不可用时只能记录 `UNAVAILABLE`，不得伪造通过。
  - 使用项目实际 Mermaid 11.16.0 官方 parser 校验公共规约示例、路径模板和场景模板；同时校验 19 个 Agent/Skill frontmatter、规则引用、eval JSON、冲突标记和 `git diff --check`。
- Result:
  - 三个 Mermaid 代码块均通过 11.16.0 解析，公共 Agent/Skill 配置结构校验通过；未修改 API、事件、数据库、前后端代码、环境配置或 generated SDK。
  - 公共配置提交保留在本地 `public-usr_test_dev` 分支，未推送或合并到远端 `master`。

### 2026-07-18 - 超级管理员服务器 root 终端

- Why:
  - 超级管理员需要在运行管理页直接进入当前部署 Linux 服务器，不希望维护额外 SSH 用户名、密码、独立 terminal service、分布式租约或另一套消息协议。
- What:
  - 复用现有 terminal service/ticket/store/WebSocket/限流/超时/清理/审计链路，新增 `server-root` 目标；HTTP POST 通过公共 `BackendJavaRouteResolver`/`BackendHttpForwarder` 路由到目标 Java，WebSocket 由 Nginx 按 `linuxServerId` 精确代理。
  - 进程适配改用 Pty4J，支持真实 resize；服务器终端固定 `/bin/bash`、`/data/testagent` 和最小环境，不继承 Java 密钥。功能默认关闭，仅 `SUPER_ADMIN`、严格确认 `ROOT@linuxServerId`、目标匹配且 Java effective UID 为 0 时签票，公开地址强制 `wss://`。
  - 运行管理服务器行新增 root 终端弹窗，复用改造后的 xterm.js `TerminalPanel`；同步 HTTP API、安全、部署、模块和前端包文档，以及企业 backend/nginx env、TLS 和定向路由渲染脚本。
- How:
  - 没有新增平行 service、Redis lease、数据库或 SSE；workspace 与 server-root ticket 只在目标 JVM 内存中短期保存。Nginx 使用 `TEST_AGENT_NGINX_TERMINAL_ROUTES=linuxServerId=host:port` 生成 exact location，TLS 由证书路径参数显式开启。
  - 后端 terminal/API 定向测试分别 25/16 passed；前端全量 Vitest 77 files、1271 passed/1 skipped，terminal/agent-web typecheck 与生产 build 通过；Nginx 单/多后端、TLS、定向 route 和单机配置脚本通过。
- Result:
  - `.env.test`/`test` profile 三服务真实重启成功，backend health/readiness UP、frontend 3000 返回 200、未认证 root ticket 返回统一 401；fat jar 已确认包含 Pty4J 和 Linux x86-64/aarch64 原生库。
  - macOS 本机不是 Linux root + HTTPS/WSS 企业环境，因此未实际执行 root 命令；生产启用前仍需在目标 Linux 以 root Java、真实 TLS 证书和 WSS 网关完成验收。未改数据库、事件、generated SDK 或 `.env.local`。

### 2026-07-18 - 公共 Agent Diff 面板持续感知磁盘变化

- Why:
  - 既有实现只在当前工作台保存成功后传递一次 revision；该信号被错过或变更来自其他本地 Git/磁盘操作时，已经打开的 Diff 面板会保留旧快照，仍需点击刷新按钮。
- What:
  - 进入“变更”面板时立即复用 `GitChangesPanel.refreshChanges()` 核验三个作用域；停留期间每 5 秒继续调用同一方法，切回文件树/搜索或组件卸载时立即清理定时器。
  - 保留 Agent 文件保存后的 revision 即时刷新，形成“保存立即刷新 + 可见期间兜底核验”两层机制，没有新增 API、事件或第二套 Diff 状态。
  - 同步 frontend、agent-web README/PACKAGE、前端规范和模块图。
- How:
  - TDD 先新增“进入立即刷新、5 秒后再次刷新、离开后停止”组件用例并确认旧实现失败，再扩展 `FigmaFileExplorer`；Git Changes 定向 41 项与 agent-web typecheck 通过。
  - Playwright 真实登录验证公共 Diff 请求在进入面板后新增并持续出现；切回文件树后超过 5 秒，请求计数保持 `5 -> 5`。
- Result:
  - JDK 25 下后端 18 模块打包成功，按 `.env.test`/`test` profile 重启 backend、opencode-manager、frontend；health/readiness UP、前端 3000 返回 200、CORS 正常，manager 无 decode/reconnect 错误。
  - 轮询只在变更面板可见期间执行，不在后台长期扫描 Git；不涉及 HTTP API、RunEvent、数据库、generated SDK、环境配置或安全凭据。
### 2026-07-21 - 固化企业多后台一键部署与扩容配置初始化

- Why:
  - 企业内三台机器已经完成外层包校验和解压，但逐条执行节点包解压、预校验、正式部署、后校验容易漏跑；现场曾出现命令快速结束且 systemd 时间未变化。后续还会增加全新后台，需要可复用的 env 初始化流程。
- What:
  - 完整外层包新增后台、前端无参数入口，从本机网卡识别 `122.233.30.x`，自动选择节点包并连续执行预校验、正式部署和后校验，完整输出写到 `/data/0709/deploy-<IP>.log`。
  - 新后台初始化脚本以包内 `.4` 真实节点配置为基线生成本机 `backend.env`、`docker.env`，只替换 advertised host 和稳定 server ID，不打印密码/token，节点配置归档继续限制在 1 MiB；前端登记脚本在新后台 readiness 通过后幂等追加 Nginx upstream 和 terminal route。
  - 逐机核心脚本从固定两地址扩展为同网段多后台，新增显式 peer 校验参数；前端校验、部署前 readiness 和 Nginx dump 校验按 `nginx.env` 全部后台动态执行，同时保留 `.4/.114` 种子节点约束。
  - 同步企业部署 README、完整多后台操作手册、外层包结构测试、多节点核心测试和一键入口隔离回归测试。
- How:
  - 外层入口只编排现有 `deploy-multi-backend-node.sh` 的三个模式，不复制 Java、Docker 或 Nginx 部署实现；共享函数按文本处理 dotenv，不 source 现场配置。新后台继承集群共享的 DB/Redis/manager/internal proxy 配置，RSA 仍只取 JAR 内 `BOOT-INF/classes/rsa-private.key`。
- Result:
  - 自动 IP/三阶段执行、新 `.115` 配置初始化、前端登记、扩展后的多后台校验、完整包结构和 AI 文档校验均通过；未改 API、事件、数据库、Java/前端业务代码、generated SDK 或 `.env.local`。
  - 本机无法真实连接企业 `.4/.114/.2`，systemd、Docker、Nginx 和跨机 readiness 的最终结果需在企业服务器运行新入口确认；脚本会以非零退出并保留完整日志，不会把未重启误报为成功。

### 2026-07-21 - Nginx 按用户绑定服务器精确首跳路由

- Why:
  - 一台 Linux 服务器严格对应一个 Java 时，用户会话请求仍先落到任意 Java、再由后端权威路由转发，产生了可避免的 Java→Java 二次转发。
- What:
  - 企业 Nginx 将 `TEST_AGENT_NGINX_TERMINAL_ROUTES` 泛化为 `TEST_AGENT_NGINX_SERVER_ROUTES`，为每个 `linuxServerId` 生成精确 HTTP/终端路由；目标 Java 为 primary，其余 Java 为 backup，未知或缺失路由头继续走 `least_conn`。
  - 固定外层离线包封装可复用旧前端节点包：只在临时副本中把旧终端路由键迁移为统一 server route 键，源敏感包保持不变，缺失、重复或新旧并存时拒绝交付。
  - 前端仅在内存保存 `/processes/me` 返回的 `linuxServerId`，为用户 OpenCode、Session、Run、工作空间和 RunEvent/运行态 SSE 动态注入 `X-Test-Agent-Linux-Server-Id`；登录、应用列表和系统管理等共享控制面请求保持普通负载均衡。
  - Nginx 在转发前清除路由提示头和外部 `X-Test-Agent-Backend-Routed`，后端保留 binding/contextToken 权威校验与 Java→Java 兜底；CORS、HTTP/SSE/安全规范及单机、多后台部署手册同步更新。
- How:
  - 配置生成器只接受静态白名单中的安全 server ID 和 backend endpoint，拒绝重复映射及新旧变量同时存在；故障切换仅允许连接错误/超时，未开启 `proxy_next_upstream non_idempotent`。
  - 五组企业部署/完整外层包回归、Shell 语法检查、前端 100 项定向测试、三个 TypeScript 项目 typecheck、lint/build、后端 API 主代码构建与 CORS 2 项测试均通过。
  - 使用 `.env.test` / `test` profile / JDK 25 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 返回 200，路由头 CORS 预检和未认证伪造头 401 契约通过。
- Result:
  - 固定拓扑下，绑定已解析后的会话请求可由 Nginx 直接进入目标 Java；旧前端、未知/过期/伪造提示仍由默认 upstream 和后端权威路由安全兜底。
  - 本机没有目标 Linux Nginx，实际 `nginx -t/-T` 与 primary 故障切换仍需在企业前端机验收；部署脚本会在替换配置前执行 `nginx -t`，生效后检查 `nginx -T`，失败自动回滚。
  - 后端全 reactor 测试仍被任务外既有 `UserManagementApplicationServiceTest` 的旧构造器调用阻断；前端宽泛测试曾遇到任务外 jsdom Canvas 未实现，相关定向测试、生产 build 与真实服务启动均已通过。
  - 未修改数据库、Flyway、RunEvent 类型、generated SDK、`.env.test` 或 `.env.local`。

### 2026-07-21 - 超级管理员安全删除用户并原位补全 TCDS 信息

- Why:
  - TCDS 用户资料接入后，旧降级账号需要清理；已有会话、工作区或进程的存量用户不能删除重建，否则会丢失原 `userId` 对应的业务关系。
- What:
  - 用户管理新增单个/批量物理删除和单个/批量 TCDS 同步。删除为全有或全无，禁止删除当前登录用户；会话、Run、工作区、进程、调度、夜间任务和配置操作等业务引用会阻断，角色、应用成员、登录日志、SSH key、偏好、反馈和统计等账号附属数据在同一事务清理。
  - 删除前后通过 Redis `SCAN` 撤销目标用户登录 Token，并复用 user mutation gate 失效运行上下文；全部关系型 SQL 位于 `UserDeletionMapper.xml`，未新增 JDBC SQL。
  - TCDS 批量同步以最多四路并发完成全部外部查询后再开启短事务，只刷新姓名、研发部门和部门，保留 `userId`、统一认证号、组织、角色、应用成员及历史数据。TCDS 不返回应用成员关系，缺失应用仍通过现有应用管理添加成员。
  - 设置页增加行内和批量删除/TCDS 同步、当前用户删除保护及明确操作说明；同步 HTTP API、安全、后端模块、前端包和测试文档。
- How:
  - 新增领域删除端口、MyBatis XML 实现、Redis Token 按用户撤销、SUPER_ADMIN Controller/API client/共享 DTO 和 Vue 交互；修复此前 TCDS 构造器变更后未同步的用户管理单测基线。
  - 后端用户管理、Controller、MyBatis、Redis 和 SQL 约束共 34 项定向测试通过；前端用户管理/API 84 项定向测试、三个相关 TypeScript 项目 typecheck、生产 build 和后端全 reactor 跳过测试打包通过。
- Result:
  - `.env.test` / `test` / JDK 25 三服务真实重启成功，backend health/readiness 为 UP、frontend 3000 返回 200、登录 CORS 和 manager WebSocket 正常。
  - 前端全量 Vitest 为 1446 passed / 1 skipped / 1 failed；唯一失败仍是任务外 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询，并伴随 jsdom Canvas 未实现，本次未修改文件浏览器代码。
  - 新增 HTTP API 和高权限删除安全边界；未修改数据库结构、Flyway migration、RunEvent、generated SDK、`.env.test` 或 `.env.local`。

### 2026-07-21 - 修复企业同源构建服务器终端地址解析

- Why:
  - 企业发布以空 `VITE_TEST_AGENT_API_BASE_URL` 构建同源 `/api`，超级管理员打开服务器终端时，前端把空 base 传给 `new URL(ticketUrl, baseUrl)`，浏览器因此报 `pty_ticket_failed: Failed to construct 'URL': Invalid base URL`。
- What:
  - 复用 terminal 包既有 `toWebSocketUrl`：base 非空时保持原解析逻辑；base 为空时直接使用绝对 `ws(s)://`，把绝对 `http(s)://` 转换为 `ws(s)://`，旧后端相对地址保留给浏览器按当前页面解析。
  - 新增空 base 下绝对 WSS ticket 和相对旧 ticket 两个回归用例，并同步 terminal README/PACKAGE 兼容性说明。
- How:
  - terminal/terminal-panel 定向 7 项、加入服务器工作区选择器后 9 项测试通过；terminal 与 agent-web typecheck、空 API base 生产构建通过。
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启三服务，health/readiness、前端 200 和 CORS 通过；真实登录超级管理员后打开服务器终端，PTY 状态到 `open`，执行 `printf 'codex-terminal-ok\n'` 得到同名输出，不再出现 `PTY_TICKET_FAILED`。
  - Nginx Shell/单多后台/完整包验证通过；最终完整包逐层校验外层 ZIP、内层 release 和三份节点包，确认新 `TEST_AGENT_NGINX_SERVER_ROUTES` 唯一、旧键为零、编译产物含空 base 分支，OpenCode Node worker 镜像验证通过。
- Result:
  - 最终交付物为 `deploy/internal/dist/test-agent-two-backend-complete.zip`，SHA256 `2845af8a43d65a67140846a62ab3155c81637cd23c8a7cf75e787e7188468ecd`；内层标准 release SHA256 为 `c78f0fabbe7f28b6ab7a0de8bc458be859584526a562b512fcd29ebebc098dcc`。
  - 前端全量 Vitest 唯一失败仍是任务外既有 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询；mock Playwright 用例在终端步骤前被当前上传策略隐藏 `notes.txt` 阻断，真实 PTY 浏览器链路已单独通过。
  - 未修改 HTTP API、RunEvent、数据库/Flyway、generated SDK、鉴权或安全契约；本次只修复前端 URL 兼容性并重建离线交付包。

### 2026-07-21 - 应用撤权后隐藏保留工作区

- Why:
  - 移除应用成员后不能直接删除可能含未提交内容的个人 worktree 和历史 Session，但继续在工作台展示旧文件、版本和运行上下文会造成“仍有权限”的误解，并暴露已撤权工作区信息。
- What:
  - 全局最近工作区在映射到托管应用时复核应用启用状态和有效成员关系；撤权、停用或删除后返回空，同时保留最近偏好、个人工作区记录和物理 worktree。非托管兼容工作区保持原语义。
  - 工作台在窗口重新聚焦和前台每 30 秒刷新成员应用目录；当前应用消失后废弃迟到的应用选择响应，清空文件树、编辑器、Diff、Session/Run 与工作区选择，再切换到仍有权限的首个应用或进入空态。
  - 历史 Session 继续作为只读记录保留；`GitChangesPanel` 兼容撤权空态或旧 mock 缺少 `files` 的 Diff 响应，避免异常阻断空态渲染。
  - 同步工作区模块、前端、HTTP API 和后端部署文档，明确“服务器数据保留、当前工作区不可见”的产品及人工磁盘回收语义。
- How:
  - 后端服务测试 56 项和跨模块撤权集成测试 1 项通过；前端 Git Changes 单测 40 项、撤权与普通切应用 Playwright 2 项、agent-web TypeScript 检查及生产构建通过。
  - 使用 `.env.test`、`test` profile 和 JDK 25 重启 backend、opencode-manager、frontend；backend health/readiness 为 UP，frontend 3000 返回 200。
- Result:
  - 撤权后工作空间不删除但不再可见或可重新进入，前台最长感知延迟为 30 秒，窗口重新聚焦会立即刷新；SCM 权限申请地址继续使用 HTTPS。
  - 仅收紧既有 `GET /workspace-management/recent-workspace` 的可见性响应语义；未新增 API、RunEvent、数据库/Flyway、SQL、generated SDK 或环境配置，兼容非托管历史工作区。
  - 共享工作区另有未提交的工作区大文件分片上传改动，本次未修改或暂存这些文件。

### 2026-07-21 - 工作区文件分片上传与渐进式完整预览

- Why:
  - 工作区和 Agent 配置通过单条 WebSocket Base64 消息上传或读取较大文件时，会触发帧大小/内存边界并关闭连接；用户要求上传不设置业务总大小上限，预览可继续读到完整内容，同时明确提示超大文件可能卡顿。
- What:
  - 文件 WebSocket 新增 begin/chunk/complete/abort 分片上传会话，浏览器默认按 256 KiB 顺序发送；服务端写同目录隐藏临时文件、校验声明大小后不覆盖发布，并在取消、失败、断连或残留超时后清理。前端工作区和 Agent 配置上传期间显示全局遮罩、当前文件及字节进度。
  - 5 MiB 仅作为一次性读取和文本编辑阈值；超过后切换为约 512 KiB、UTF-8 边界对齐的渐进只读预览。用户可“继续加载一段”或“加载全部（可能卡顿）”直至 EOF，界面持续显示进度及内存/Monaco 卡顿提醒；文件大小或修改时间变化时停止混合拼接。
  - 同一 WebSocket 连接的文件请求使用 `concatMap` 保序并把阻塞文件 I/O 调度到 `boundedElastic`，不同连接可由线程池并发处理；同步更新文件 RPC、部署参数、安全/前后端规范、模块说明和用户手册。
- How:
  - 后端工作区服务 51 项、文件 WebSocket/帧配置 23 项通过；全 Maven 流程中 workspace 242 项、API 模块及此前偶发的 runtime 调度测试均通过，随后 persistence 被既有 `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（76 errors），与本次文件链路无关。
  - 前端相关 4 个测试文件 112 项通过，用户手册、Vue TypeScript 与生产 build 通过；前端全量 1456 passed / 1 skipped，唯一稳定失败仍为任务外 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询，另一个异步用例单独复跑通过。
  - 使用 JDK 25、`.env.test` 和 `test` profile 重启 backend、opencode-manager、frontend；backend readiness 为 UP、frontend 3000 返回 200。
- Result:
  - 上传不再受应用层文件总大小限制，实际能力由浏览器、网络、磁盘和基础设施超时决定；大文件可分段预览到 EOF，但保持只读，文本编辑/保存仍受默认 5 MiB 安全阈值约束。
  - 仅扩展既有平台文件 WebSocket RPC 和前端交互；未新增 HTTP API、RunEvent 类型、数据库/Flyway、SQL、generated SDK 或环境配置文件，保留旧单帧上传操作用于兼容。
  - 并发出现的 Agent 配置 rollout/worktree claim 改动不属于本次任务，本次提交不暂存这些文件。

### 2026-07-22 - 公共 Agent 发布后台排空与脏副本诊断

- Why:
  - 公共 Agent 远端推送和 rollout 激活已完成后，HTTP 请求仍在“完成并广播更新”阶段同步认领本机 Git/进程排空任务，可能长期占住发布窗口；窗口执行中又禁止关闭。
  - 多服务器的共享运行副本彼此独立，单台服务器变脏时拉取只返回通用冲突，页面还把 `initialized=true + CONFLICT` 显示成“已初始化”，看不到具体文件或恢复建议。
- What:
  - 公共 `update`、`update-and-push`、`publish` 在远端事实确认、持久化 rollout 激活和低延迟广播后直接返回，不再从 HTTP 请求线程认领本机同步；本机及其它服务器统一由广播消费者或默认 5 秒数据库补偿任务继续同步、登记进程和排空。
  - Git 提交/推送进度窗口执行中可关闭，关闭不取消请求；第 5 步改为“创建后台同步任务”。
  - 共享仓库脏状态在拉取异常中复用 Git porcelain 路径并附排查建议；无法解析路径时明确提示在目标服务器执行 `git status --short`。系统管理将脏且已初始化的仓库显示为“存在本地变更”，失败后刷新服务器行，并提供带二次确认的“放弃本地变更并拉取”，只 reset 已跟踪文件，不影响个人公共 worktree、不删除未跟踪文件。
  - 同步更新工作区/前端 README、HTTP API、内部广播和后端部署文档。
- How:
  - 复用既有 `PublicAgentConfigRolloutCoordinator` 持久化状态、Redis 广播和定时补偿，没有新增线程池、状态机或 API；前端继续使用已有 `discardLocalChanges` 请求字段。
  - 后端定向 47 项、工作区模块 243 项（连同 common/domain reactor 均通过）；前端定向 48 项、全 workspace typecheck 和生产 build 通过。一次误带 `--` 的全量 Vitest 为 1456 passed / 1 skipped / 4 failed，失败均是既有 `DirectoryRows` role、时间线异步和 Mermaid/jsdom canvas 基线，与本次两个定向文件无关。
  - 使用 JDK 25、`.env.test`、`test` profile 重启 backend、opencode-manager、frontend；health/readiness UP、前端 3000 返回 200、登录 CORS 正常、manager WebSocket 已连接。
- Result:
  - 公共发布成功响应不再等待服务器排空，进度窗口可以安全隐藏；rollout 仍以数据库任务保证重启、丢广播和跨服务器恢复。
  - `.4` 与 `.114` 状态不一致时，页面会明确指出这是对应服务器共享运行副本的本地差异并列出文件；管理员可先核对再显式恢复。
  - 仅调整既有 HTTP 接口执行时序和错误信息，兼容现有请求/响应字段；未新增 RunEvent、数据库/Flyway、SQL、generated SDK、环境配置或凭据变更。
### 2026-07-22 - 企业双后台增加独立 XXL MySQL 并修正存量库迁移顺序

- Why:
  - 企业双后台交付需要把 XXL-JOB 使用的 MySQL 8.4 作为独立离线镜像部署到现有 PostgreSQL 服务器，并将初始化凭据与两台 Java 配置一次性安全生成、封装。
  - 企业环境上午已经部署过包含 `V20260721213000` 的旧包；新引入但尚未交付的 `V20260721134000` 会触发 Flyway out-of-order，不能按“新库”处理。
- What:
  - 新增 `.147` MySQL 节点配置模板、离线镜像导入/容器部署/验证脚本和自动识别本机 IP 的节点入口；完整包固定包含 `.147/.4/.114/.2` 四份节点包，但节点配置压缩包继续各自小于 1 MiB。
  - 打包脚本新增 `mysql:8.4` linux/amd64 拉取、架构校验和 Docker tar 导出；完整包封装前校验两台后台、前端 Admin upstream 和 MySQL 节点使用同一组应用密码/access token，不打印敏感值。
  - 两台后台强制校验 XXL 开关、`.147:3306`、Admin/executor 端口并在部署后验证本机 Admin；前端校验并探测每个 XXL Admin upstream。
  - 将尚未在企业交付的夜间迁移改为 `V20260722130000`，版本晚于已交付的 `V20260721213000`，存量库不需要开启 `SPRING_FLYWAY_OUT_OF_ORDER` 或手工修改 Flyway 历史。
  - 同步企业部署 README/多后台手册、数据库文档、模块说明和密钥交付安全规范。
- How:
  - dotenv 始终按文本解析，不执行 `source`；MySQL root/应用密码和 XXL access token 使用强随机值生成，只写入 `0600` 敏感节点配置。MySQL 数据固定在 `/data/testagent/mysql`，重复部署只重建容器、不删除数据目录。
  - 最终大包逐层验证发现 `unzip -Z1 | grep -q` 在 `pipefail` 下会因 SIGPIPE 误报 MySQL 镜像缺失；改为完整消费 ZIP 列表，并用镜像条目后 2000 个文件的回归包覆盖该边界。
  - 存量库升级必须先停两台旧 Java，因此固定首节点 `.4` 只做本机全量验证并延后 peer 探测；第二台 `.114` 反查 `.4`，前端再同时检查两台 Java/Admin，避免首节点服务已成功却因 `.114` 尚未启动返回 `verify_exit=1`。
  - 保留工作区中并行出现的 Agent 配置服务和前端管理面板改动，本次不暂存、不覆盖，也不混入干净构建来源。
- Result:
  - 本地 MySQL 8.4 amd64 容器由正式部署脚本启动并为 healthy，应用账号可连接；XXL Admin、Java readiness、前端均通过，本地 PostgreSQL 只记录 `20260722130000`，未启用 out-of-order。
  - 夜间任务持久化集成测试 6 项通过；MySQL、双后台节点、完整包、AI 文档校验均通过。最终外层包 SHA256 为 `442267b3b0ca388d2dd7ea6e1ccca5790ac84f2709ab9bc79432d1119c4dfdb7`，内层 release SHA256 为 `969430681caad50719c7e1ac41367e8e5d266d5582882f711993b7ad0acac2ae`。
  - 涉及企业部署配置、离线镜像、Flyway 版本兼容和密钥交付安全；未修改 HTTP API、RunEvent、generated SDK 或业务 SQL 内容。

### 2026-07-22 - 修复公共 Agent 发布污染历史、待发布恢复与脏 worktree 误诊

- Why:
  - 企业 `.114` 的公共 Agent 本地提交已成功，但个人分支历史含 `@testagent.local` 无效 committer，远端拒绝整段历史，页面仍停在广播阶段且重开后因 Git status clean 丢失重试入口。
  - `.4` 显式拉取实际命中了当前管理员个人 worktree 的 `opencode/opencode.jsonc` 未提交修改，但旧错误只说“Git 工作树存在未提交变更”，导致被误判为 `.114` 共享仓库异常。
- What:
  - 公共 publish 在合并远端后只投影最终文件树，以当前远端提交为唯一父节点和当前管理员企业身份生成线性提交；个人分支先 reset 到该干净提交再按分支 refspec 非强推，切断历史无效提交身份。
  - 公共 Diff 在 porcelain clean 时比较个人/共享 HEAD 与文件树，新增向后兼容的 `publishPending`；页面重开后可直接“重新推送”，不重复本地 commit。有真实未提交文件时仍优先走正常暂存、提交或回退。
  - 公共拉取脏状态返回 `repositoryKind/path/dirtyFiles/discardLocalChangesAllowed`，区分当前管理员个人 worktree 与共享运行副本；系统管理页面展示目标服务器、绝对路径和文件，并允许显式放弃已跟踪修改再拉取，其他管理员 worktree 与未跟踪文件不受影响。
  - 公共发布失败提示明确本地提交已保留、远端与其他服务器未更新；进度弹窗执行中可关闭，发布成功响应不再等待后台 rollout 排空。
- How:
  - 真实临时 Git 仓库验证污染提交不是新发布提交祖先、作者/提交者为企业邮箱、分支 refspec 可实际推送；common Git 43 项、Agent 配置服务 48 项、前端两个目标文件 48 项通过，TypeScript 全 workspace 检查和生产 build 通过。
  - 一次参数误传的前端全量测试为 1493 passed / 1 skipped / 1 failed；唯一失败是既有 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询，与本次文件无关。
  - 使用 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；health/readiness UP，前端和 CORS 返回 200，manager WebSocket 已连接并应用配置。
- Result:
  - 部署后 `.114` 的 clean 待发布个人提交会恢复“重新推送”入口，并由新 publish 自动消除历史无效提交身份；`.4` 会明确显示个人 worktree 下 `opencode/opencode.jsonc` 的真实脏状态，管理员可按是否保留选择提交或回退后拉取。
  - 仅扩展既有公共 Diff HTTP 响应字段并优化发布/拉取语义；未修改 RunEvent、数据库/Flyway、SQL、generated SDK、环境配置或凭据。

### 2026-07-22 - 企业平台与 MySQL 离线包拆分并补齐容器诊断

- Why:
  - 企业 `.147` 执行旧 MySQL 入口后没有可见 Docker 容器，旧脚本又丢弃 `docker run` 返回的容器 ID，普通 `docker ps` 无法展示已退出容器，现场缺少直接诊断信息。
  - MySQL 8.4 镜像与每次更新的平台 JAR、前端和 worker 绑定在同一个外层包，导致普通平台升级也要重复传输不变的大镜像。
- What:
  - 固定拆为平台 `test-agent-two-backend-complete.zip` 和 MySQL `test-agent-mysql-offline.zip` 两个包；平台包只含 `.4/.114/.2` 和平台 release，MySQL 包只含 amd64 镜像、`.147` 敏感配置及 MySQL 入口。
  - 默认 `package-release.sh` 只构建平台 release；MySQL 用 `--mysql-only` 单独导出，再由 `package-mysql-offline.sh` 无交互覆盖固定文件名。平台封装仍读取 `.147` 源节点包校验两台 Java 与 MySQL 应用密码一致，但不把它打入平台包。
  - MySQL 部署成功时输出容器 ID 和 `docker ps -a`，失败时输出容器状态、退出码和末尾 80 行日志；SELinux 启用时为独占数据目录添加私有标签，始终保留 `/data/testagent/mysql`。
  - 同步企业部署入口和多后台手册，明确首次传两组、后续普通平台升级只传平台包。
- How:
  - MySQL 部署、平台分包、MySQL 分包、自动节点入口、systemd 首装/升级及开发脚本回归通过；正式 MySQL 脚本在本地真实重建 `mysql:8.4` amd64 容器，应用账号连接通过且原数据目录保留。
  - 从提交 `51dca7772` 重新构建后逐层校验两个外层 ZIP、平台内层 release、JAR 内置 RSA、两个 amd64 镜像、节点 SHA 和每份节点配置小于 1 MiB。
- Result:
  - 平台包约 259 MiB，SHA256 `382fea44a0fedf46434954cb0decc75c3855aa89896e167b6bee083185057c92`；MySQL 包约 228 MiB，SHA256 `745845decef03bade44aa43b8c828c1f3076e6781104855caa0d5b400d0c3f10`。
  - 最终文件已写入 `deploy/internal/dist/` 和 `/Users/kaka/Desktop/qr-decode/out/`；未修改 HTTP API、RunEvent、数据库/Flyway、业务 SQL、generated SDK 或现场凭据。

### 2026-07-22 - 企业 XXL 调度切换到外部 MySQL

- Why:
  - 现场明确取消 `.147` MySQL 容器，改为两台 Java 直接连接既有外部 MySQL；继续交付容器镜像和 `.147` 节点包会造成误部署和额外传输。
- What:
  - 两台后台固定连接外部 `122.210.106.43:3306/xxl_job`，当前使用现场指定的既有高权限账号；JDBC 增加 `createDatabaseIfNotExist=true`，库存在后仍由 Admin 子上下文 Flyway 幂等初始化表和任务。
  - 平台外层封装不再读取或校验 `.147` 节点包，只校验 `.4/.114` 的外部 JDBC、账号密码和 access token 一致；当前 U 盘交付恢复为唯一平台 ZIP 与 SHA。
  - 同步单/多后台和企业入口文档；MySQL 容器脚本仅保留为其它隔离环境备用，不属于当前现场交付。
- How:
  - 平台封包、自动节点入口和开发脚本回归通过；从提交 `137b31a86` 完整重建 JAR、前端、programs 和 amd64 worker，逐层确认两份敏感节点配置、内层 release、JAR RSA、无 MySQL 镜像且节点包小于 1 MiB。
- Result:
  - 最终平台包约 259 MiB，SHA256 `92a41d85f6984252c1d02ee4bc49259416e1cf285c0acc807cc9d61f4b626492`，已写入 `deploy/internal/dist/` 和 `/Users/kaka/Desktop/qr-decode/out/`；旧固定名 MySQL 容器包已从两处输出目录移除。
  - Mac 到目标 3306 的 TCP connect 成功，但 MySQL 初始握手和 Docker 内只读登录在 5 秒内超时；外部账号、来源 IP 白名单和实际 MySQL readiness 必须在企业 `.4/.114` 网络继续验证，当前不能宣称远程登录已验证。

### 2026-07-23 - 修复 macOS 隧道接口导致 OpenCode 端口误冲突

- Why:
  - 昨日新增的 manager 外部监听探测会枚举所有本机 IPv4；macOS `utun` 点对点隧道地址 `198.18.0.1` 会接受任意端口连接，导致实际空闲的 4096–4105 全部被误报为 `PORT_CONFLICT`，默认用户无法初始化 TestAgent。
- What:
  - manager 的本机 IPv4 探测排除 `net.FlagPointToPoint` 接口，继续检查 loopback、物理网卡及其它活动非隧道接口；补充纯 flags 单测和 manager README 说明。
- How:
  - 修复前定向测试在 4096 复现 `port 4096 has an active TCP listener`；修复后 `go test ./internal/process -count=1` 与 `go test ./...` 全部通过。
  - 使用 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；默认用户真实初始化成功，OpenCode 1.17.7 监听 4104，平台返回 `READY/RUNNING`，`/global/health` 为 healthy 且 `/global/config` 返回 200。
- Result:
  - 开启当前 VPN/隧道网络时本地端口池可正常分配，同时保留真实宿主机监听冲突保护；backend readiness、frontend 和 CORS 均验证通过。
  - 未修改 HTTP API、RunEvent、数据库/Flyway、generated SDK、环境配置或安全契约；只影响 manager 本机端口可用性判断。
### 2026-07-23 - Redis 7.4.9 企业独立离线升级包

- Why:
  - 企业当前 Redis 5.0 在 XXL-JOB 服务不可用期间出现 `RedisSystemException: Error in execution`；现场决定不做 Redis 5 兼容代码改造，改为沿用本地 Redis 版本并单独升级企业 Redis。
  - Redis 是两台 Java 的共享必需依赖，升级必须与日常平台包解耦，并防止直接覆盖旧容器、误删原数据或出现“容器健康但 RDB 未加载”的假成功。
- What:
  - 本地 Compose Redis tag 从浮动 `7-alpine` 固定为实际运行版本 `7.4.9-alpine`；新增固定官方 amd64 manifest 的独立封包脚本，输出 `test-agent-redis-offline.zip`、SHA、Redis 镜像 tar、强随机密码配置和双后台连接片段。
  - 新增 Redis 配置、容器部署/健康检查脚本及验证脚本；部署固定 `noeviction`、RDB + AOF everysec、protected mode 和密码，拒绝无 `--replace-existing` 的同名容器替换，永不删除数据目录。
  - 新增完整企业升级手册，覆盖 `.4/.114` 停写、Redis 5 只读盘点、最终 RDB 与备份、新目录恢复、密码合并、分步恢复及回滚；说明本包未自带企业 TLS/独立 ACL 用户，不能宣称满足完整生产 TLS 基线。
- How:
  - 首次 Redis 5 RDB 冒烟发现 Redis 7 在配置 `appendonly yes` 且没有 AOF 时会健康启动但不加载旧 RDB；部署脚本据此增加“先关闭 AOF 加载复制的 RDB → 动态生成 Redis 7 multipart AOF → 重启 → 核对所有 DB key 总数”程序。
  - shell 语法、封包结构/权限/密码一致性、危险数据删除静态检查均通过；最终离线 tar 真实加载为 linux/amd64，临时容器通过认证、Redis 7.4.9、PING、SET/GETDEL 验证。
  - 使用官方 Redis 5.0.14 amd64 临时容器在 DB0/DB1 写入两种数据并生成 RDB，最终包成功转换、重启和读取两个 key；全部临时容器与目录已清理，原本地 `test-agent-redis` 保持 running。
- Result:
  - 最终敏感包位于 `deploy/internal/dist/test-agent-redis-offline.zip`，权限 `0600`，SHA256 为 `ee0d6a7d8103c617970fbc0daa66951a0310d64c8c7d13f8c2e74cfa26dff6e2`。
  - 未修改 Java/前端业务代码、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK 或现有 `.env`；企业现场尚未执行，实际 Redis 主机、数据目录、服务形态、备份可恢复性和防火墙需按手册先确认。

### 2026-07-23 - 基于当前代码重建 OpenCode 1.18.4 企业双后台完整包

- Why:
  - Redis 独立升级包完成后，需要从当前代码重新生成后台、前台、programs、manager/worker 和固定 `.4/.114/.2` 双后台部署包，并确保 OpenCode 1.18.4 升级与新 Redis 凭据一起交付。
- What:
  - 以同源空 `VITE_TEST_AGENT_API_BASE_URL` 完整构建后台 JAR、前端静态包、`test-agent-programs.tar.gz`、linux/amd64 worker 镜像、标准 release ZIP 和双后台外层完整包；manager 构建号为 `V20260723.124040`。
  - `.4`、`.114` 节点 `backend.env` 已与独立 Redis 7.4.9 包的 host、port、64 位密码一致；两台仍共享相同 manager token，并继续连接外部 `122.210.106.43` MySQL，平台包不含 MySQL 镜像。
  - 最终平台包和 Redis 包连同 SHA 已复制到 `/Users/kaka/Desktop/qr-decode/out/`；输出目录中的敏感外层包和 Redis 包均保持 `0600`。
- How:
  - GitHub release 与 Go proxy 下载出现 SSL/EOF 波动时，先并行下载 OpenCode 官方归档到本机临时缓存，按固定大小和 SHA-256 校验后通过支持 Range 的临时 HTTP 服务供 Docker 构建；Docker 内仍再次校验归档 SHA、二进制 SHA 和 `--version`。
  - 启动器 5 项测试和 worker 运行态验证通过，覆盖 OpenCode 1.18.4、glibc 2.31、断网 Tool 依赖、`subagent_depth=2` 与优雅停止；封包、节点部署、systemd、Nginx、Shell、AI 文档验证及外层/内层/节点深度校验通过。
- Result:
  - 最终平台外层包 `test-agent-two-backend-complete.zip` 为约 351 MiB，SHA256 `2cbd4bda1f9662c92995b6ca6f1b8a07dc45e2f34547a433cc8f75e141e1124d`；内层 release SHA256 `750240e2950a93ea18e01213e2c75b06af5914db5618912f8f3aa6379ffa1d2b`。
  - programs SHA256 `26482d883c73dbdd8088dca01965795a92720507058e4695080168c83a74addd`，worker tar SHA256 `aa83c8ee8704cfa162280986d1258f25a7d9f087bfb06a35d5d5b7d6af572c33`；OpenCode 官方归档与二进制 SHA 仍分别为 `4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc`、`6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5`。
  - 本次未修改 Java/前端/manager 源码、API、RunEvent、数据库/Flyway、SQL、generated SDK 或 `.env`；企业真实 systemd、Docker、Nginx、外部 MySQL 和跨机网络仍需按 `.4 -> .114 -> .2` 顺序现场验收，OpenCode 既有用户进程需在升级后重启。

### 2026-07-23 - 合并远端 Redis/XXL 登录修复

- Why:
  - `main` 在执行 `git pull --rebase origin` 时停在冲突状态，需要把远端 `9dc1ced8d` 的 Redis 5/HTTP XXL 登录兼容修复与本地五个提交安全合并。
- What:
  - 完成已有交互式 rebase，将本地提交重放到 `origin/main`；解决 XXL 排查手册、集成模块 README 和 `PlatformXxlSsoController` 冲突。
  - 冲突结果同时保留 Redis Lua 原子消费与完整异常日志、SSO 自有 503 状态页、`adminTab` 次生异常说明、外部 `122.210.106.43` MySQL 拓扑和固定 HTTP 入口两节点 `COOKIE_SECURE=false` 约束。
- How:
  - 使用 `git range-diff` 核对五个本地提交均被保留；JDK 25 下运行 `PlatformXxlSsoControllerTest` 4 项，另运行 manager 端口、XXL 诊断、Redis 部署/封包及 AI 文档验证。
- Result:
  - `main` 已基于 `origin/main`，远端/本地计数为 `0/5`，工作区无冲突；全部相关验证通过。未推送远端，未修改 `.env`、API、RunEvent、数据库/Flyway、SQL 或 generated SDK。

### 2026-07-23 - 企业双后台扩至每节点 1000 个 OpenCode 端口并携带会话日志重打包

- Why:
  - 用户要求以 2026-07-22 17:00 后的全部代码、架构和中间件变化重新生成企业包，并明确两台 worker 均直接发布 1000 个 OpenCode 端口；随后补充要求把全部 `.agents/session-log*.md` 一并纳入交付基线。
- What:
  - 当前 `.4 + .114` 双后台节点端口池由 `4096-4115` 调整为 `4096-5095`，逐机预校验和部署后校验同步检查新末端口；多后台手册明确页面全局 `OPENCODE_MANAGER_MAX_PROCESSES` 必须改为 `1000`，并说明 1000 个端口不等价于已验证 1000 进程承载能力。
  - `package-release.sh` 在内层发布 ZIP 的 `.agents/` 下纳入当前仓库全部 `session-log*.md`，并复用既有封装函数增加 `--zip-only`，用于完整二进制构建通过后只重封当前脚本与会话日志；缺少 backend/frontend/programs/worker 任一制品时拒绝生成部分包。
  - 外层完整包封装逐一校验当前仓库的会话日志均已进入内层 ZIP；复用旧现场节点包时只在临时副本补齐 Cookie、大文件和 `4096-5095` 端口池等非密钥字段，不改源敏感包或输出密钥；worker 启动脚本缺省端口上限也同步为 `5095`。
  - 同步双后台、XXL 诊断、完整包和 AI 文档回归。
- How:
  - 使用空 `VITE_TEST_AGENT_API_BASE_URL` 全量重建后端 JAR、前端、programs、linux/amd64 worker 和内层发布 ZIP；manager 构建号为 `V20260723.133043`，OpenCode 1.18.4 官方归档大小、归档 SHA、二进制 SHA 和版本校验通过。
  - 启动器 5 项测试、worker 断网运行态、systemd 首装/升级、双后台节点、完整包、XXL 只读诊断、Shell 语法和 AI 文档校验通过；JAR SHA256 为 `9ac6ce8432b79a86eeab853e68bd9a3ac74a282f3552db2681a57f192f7fd8b7`，programs SHA256 为 `b78df8437165b8dbac5113cc295ba827e7604ab0bedd1561c5078965eeefb5cf`，worker tar SHA256 为 `a7c7dbfc09e0efdeaae6fefab8c1c7edd2ce0cea6c8acd5d394de1a9fa64ba05`。
- Result:
  - 本次会话日志已通过 `--zip-only` 进入重封的内层 ZIP，并复用两台后台既有受控密钥生成 1000 端口节点包和固定名外层完整包；最终 SHA 只在交付回复和配套 `.sha256` 文件中给出，避免把包自身哈希递归写入包内日志。
  - 未修改 Java/前端/manager 业务源码、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK 或现场 `.env`；企业真实防火墙、Docker 1000 端口映射耗时和 1000 进程资源承载仍需现场验证。

### 2026-07-23 - 固化中转机目录并补齐 Redis 与平台全量手册

- Why:
  - 企业中转机的真实交付目录始终是 `~/Desktop/mimoagent/0709`，之前回复又误写为目标服务器的 `/data/0709`，现有稳定文档和企业部署 skill 没有把这个边界固化得足够清楚。
- What:
  - 在企业部署 README、多后台手册、Redis 离线手册和项目部署 skill 中统一约定：中转机只使用 `~/Desktop/mimoagent/0709`，`.20/.4/.114/.2` 目标服务器才使用 `/data/0709`。
  - 新增 `deploy/internal/FULL-UPGRADE-RUNBOOK.md`，按中转机 → Redis 5 盘点/备份/升级 → `.4` → `.114` → `.2` → 页面配置/验收/回滚给出单文件全量命令。
  - 部署 skill 同步清理旧的 20 端口和 `TEST_AGENT_BACKEND` 示例，固定当前双后台 `4096-5095`、页面 `OPENCODE_MANAGER_MAX_PROCESSES=1000` 和 `.4 → .114 → .2` 顺序。
  - Redis 封包脚本新增 `--zip-only`，只更新已有敏感包中的手册/脚本，保留已与平台节点包匹配的 Redis 密码和 amd64 镜像。
- How:
  - 复用现有 `REDIS-OFFLINE.md`、`MULTI-BACKEND.md` 的数据备份、节点一键部署、验证与回滚命令，新手册只负责把两条已有流程按当前现场拓扑串联，不新增部署实现路径。
  - `tools/verify-ai-docs.sh` 增加新手册存在性、中转机目录、1000 进程参数的静态回归，防止后续再退回错误路径或旧容量。
  - Redis 封包回归覆盖重封前后密码不变、手册路径已更新、镜像 SHA 仍有效和固定文件无交互覆盖。
- Result:
  - 完整手册和目录边界已进入项目稳定文档；它们会随当前会话日志通过 `--zip-only` 重封进企业平台发布包。本次不修改 API、RunEvent、数据库/Flyway、业务代码、现场配置或密钥。

### 2026-07-23 - 工作空间与 Agents 文件树支持右键批量文件操作

- Why:
  - 左侧工作空间和 Agents 树原先依赖双击进入重命名，缺少符合桌面文件管理习惯的右键入口、Ctrl/Cmd 多选、批量删除和多文件拖动；用户同时要求补齐复制、剪切、粘贴。
- What:
  - 两棵树移除双击重命名，统一复用右键菜单；Ctrl/Cmd+单击维护多选，右键支持删除、复制、剪切、粘贴，拖动任一已选项会整体移动。工作空间允许文件/目录剪切和移动，复制继续限制为普通文件；Agents 多选范围为文件，目录作为粘贴和拖放目标。
  - 工作空间批量操作逐项复用既有文件 WebSocket、树缓存、Tab、撤销栈和 Git Diff 刷新链路；Agents 新增 `agent-config.copy`、`agent-config.move` WebSocket RPC，后端复用 `WorkspaceFileService` 的路径安全校验、同名拒绝和复制/移动实现，保留公共级 `SUPER_ADMIN`、应用级 `APP_ADMIN` 及应用白名单约束。
  - 新增共享右键菜单壳和多项删除确认；同步 frontend/backend 模块 README、PACKAGE、用户手册及 HTTP/事件流协议文档。
- How:
  - 前端定向 134 项通过，lint、全 workspace typecheck、生产 build 通过；全量 Vitest 为 1550 passed / 1 skipped，两个 Mermaid 测试在并行运行时波动失败，单 worker 独立复跑 21 项全部通过。
  - JDK 25 下 Agent 配置应用服务与 WebSocket handler 定向 70 项通过；使用 `.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend，health/readiness、前端 3000 和登录 CORS 均通过。
- Result:
  - 工作空间和 Agents 文件树已具备右键重命名及多选删除/剪切/粘贴/拖动，普通文件可复制；批量操作采用顺序执行并明确报告部分成功，不提供跨作用域剪贴板。
  - 新增两个 Agent 文件 WebSocket op；未新增 HTTP 路径、SSE 事件、数据库/Flyway、SQL、依赖、generated SDK 或环境配置变更，现有权限和兼容路径保持不变。

### 2026-07-23 - 修复文件树无操作目录的空白右键菜单

- Why:
  - 工作空间组合目录 `docs` 不可重命名/删除且剪贴板为空时，右键处理仍挂载共享菜单壳，但全部菜单项都被权限条件隐藏，页面因此显示一条类似空输入框的白色长条；Agents 目录存在同类隐患。
- What:
  - 工作空间和 Agents 树在打开菜单前统一判断是否至少存在一项可执行操作；没有改名、删除、复制/剪切、粘贴、撤销或添加到对话等入口时只阻止浏览器原生菜单，不再渲染空菜单壳。
  - 保留组合目录已有的新建/上传行尾入口；当剪贴板可用时仍可右键粘贴，普通可写文件的完整右键菜单不变。
- How:
  - 两个定向 Vitest 文件 54 项通过，前端全 workspace lint 和 typecheck 通过。
  - 使用 `.env.test`/`test` profile 已运行的真实页面复现修复前空菜单；热更新后右键 `docs` 不再出现 `role=menu`，右键 `README.md` 仍显示重命名、删除、添加到对话、复制和剪切。
- Result:
  - 截图中的空白长条已消失，工作空间和 Agents 两棵树均有回归保护。未修改 API、WebSocket op、RunEvent、数据库/Flyway、权限、安全、环境配置或 generated SDK。

### 2026-07-23 - 固化 OpenCode 运行文件 Git 忽略规则

- Why:
  - OpenCode 1.18.4 首次启动会在公共配置目录生成 `package.json`、`package-lock.json` 等依赖元数据；上游只在 `.gitignore` 不存在时写一次规则，双后台中已有但不完整的规则会让公共配置管理持续误报本地变更，`git reset --hard` 又不会清理未跟踪文件。
- What:
  - 新增统一 `opencode-runtime.gitignore` 和幂等补齐脚本；企业后台升级时修复已初始化公共仓库，未初始化节点不提前建目录。
  - 官方启动器在创建 package/lockfile 与模块链接前复用同一清单，worker image/programs 和发布 ZIP 都强制携带该清单；保留已有规则，不删除文件或取消跟踪。
  - 同步企业部署 README、后端扩容/排障文档和 OpenCode 1.18.4 升级说明。
- How:
  - 启动器 Node 测试 6 项、运行文件忽略与 `agents/skills` Git 可见性脚本、双后台节点校验、AI 文档校验、Shell 语法和 `git diff --check` 通过；轻量 `--zip-only` 封装确认新增脚本与清单进入内层 release ZIP。
  - 在外网 Mac 真实构建 `linux/amd64` worker 并导出 programs，断网镜像验证通过 OpenCode 1.18.4、glibc 2.31、Tool 依赖/加载、两处配置目录忽略规则及优雅停止。
- Result:
  - 后续升级已有后台会立即补齐规则；新增后台在公共仓库初始化后的第一次 OpenCode 启动前补齐，不再因运行依赖元数据误报脏仓库。本地 worker 镜像已重建并验证，但未导出正式 worker tar 或完整企业包；未修改 HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK、现场配置或凭据。

### 2026-07-23 - Agent 与 Skill 中文展示并保留英文技术标识

- Why:
  - 公共级和应用级 Agent/Skill 的物理文件名、目录名及运行态候选长期只显示英文；平台已经可以维护双语名称，需要让用户优先看到中文，同时保持 OpenCode、Git 和既有调用标识兼容。
  - 新建表单原先只有一个名称字段且依赖不完整的手写拼音表，中文字符超出映射后会丢失；用户要求英文名选填、留空按完整拼音生成，并同步主 Agent、`@` 子智能体和 `/` Skill 候选。
- What:
  - Agent/Skill 新建表单改为中文名称必填、英文名称选填；引入 `pinyin-pro` 生成完整无声调拼音并规范成小写短横线技术标识，Agent 文件与 Skill 目录继续保持英文。Skill 模板写入 `metadata.display-name/display-name-zh`，`source: test-agent` 继续仅表示平台模板来源；Agent 使用双语 `description`，不扩展 OpenCode Agent metadata，也不修改 OpenCode。
  - Agent 配置列表为 `agents/*.md` 和 `skills/*/SKILL.md` 有界解析双语展示名，并逐段拒绝符号链接越界读取；响应增加可选 `displayName/displayNameEn`。配置树显示中文主名称、英文辅助名称，但所有读取、改名、Git 操作仍使用原始 `path/name`。
  - 对话区主 Agent、`@` Agent 与 `/` Skill 候选同步改为中文主名称、英文辅助说明，插入和提交仍使用稳定英文技术 ID；用户手册、前后端模块 README、共享类型和文件 WebSocket 协议文档同步更新。
- How:
  - 前端 agent-web 类型检查、相关 Vitest 250 项通过（1 项既有跳过），用户手册与 agent-web 生产构建通过；JDK 21 下 workspace-management 及依赖模块共 413 项测试通过。
  - 使用 JDK 25、`.env.test`、`test` profile 完整打包并重启 backend、opencode-manager、frontend；backend health/readiness 为 UP，frontend 3000 返回 200，登录 CORS 正常，manager WebSocket 已连接且日志无重连/解码错误。
- Result:
  - 例如中文“接口自动化测试”且英文留空时，实际 Skill 目录为 `skills/jie-kou-zi-dong-hua-ce-shi/`；用户在配置树和运行态候选看到中文主名称，OpenCode 仍识别英文目录、顶层 `name` 和技术 ID。
  - 仅扩展既有 `FileTreeEntryResponse` 可选响应字段并同步事件流协议文档；未新增 HTTP 路径、RunEvent/SSE 类型、数据库/Flyway、SQL、安全权限或环境配置变更，未修改 generated SDK 和 OpenCode 源码。

### 2026-07-24 - 基于当前 main 重打企业内层发布包

- Why:
  - 用户要求基于本地代码重新生成企业交付物；当前 `main` HEAD 为 `5edf896a341de8202019d858fba4faab0f2de7fb`，旧 `dist` 制品不能直接视为当前代码构建结果。
- What:
  - 重新构建后端 JAR、前端静态包、OpenCode 1.18.4 `linux/amd64` worker 镜像 tar、外置 programs 及标准内层发布 ZIP；前端使用命令行临时的空 `VITE_TEST_AGENT_API_BASE_URL`，未修改 `deploy/internal/.env`。
  - 发布 ZIP 保留仓库全部 `.agents/session-log*.md`，并包含 `deploy/internal/` 部署脚本、OpenCode 运行文件 Git 忽略清单和 JAR 内置 RSA 私钥资源。
- How:
  - 首次执行因当前 shell 的 JDK 17 不支持 Maven `--release 21` 失败；显式切换 `/usr/libexec/java_home -v 25` 后使用 `deploy/internal/package-release.sh --output-dir deploy/internal/dist` 全量完成。随后执行 `--zip-only` 重封会话日志。
  - 校验内层 ZIP SHA256、`unzip -t`、JAR manifest/外置依赖/RSA 私钥、前端相对 API、programs 内容和 worker `linux/amd64` 架构；运行 `tools/verify-internal-two-backend-complete-package.sh` 通过。
- Result:
  - 标准内层企业发布包已基于当前 HEAD 生成并可交付；本次未生成外层双后台完整包，因为工作区没有三台节点配置包，避免复用旧敏感节点包造成配置与代码批次不一致。
  - 未修改 Java/前端/manager 业务源码、HTTP API、RunEvent、数据库/Flyway、SQL、generated SDK、OpenCode 源码或环境配置；企业现场 Docker 端口映射、服务启动和浏览器验收仍需按部署手册执行。

### 2026-07-24 - 收紧企业包源码与内外层一致性

- Why:
  - 重打内层发布 ZIP 后，输出目录中的固定名双后台外层包仍可能保留旧内层 ZIP；旧外层自身 ZIP/SHA 校验可以通过，但并不代表它属于本次发布。
  - 企业打包入口此前允许从脏工作树运行，本地未提交文件可能被后端、前端或 Docker 构建上下文带入制品，无法仅凭最终文件名判断源码边界。
- What:
  - 新增共用打包源保护：内层和外层脚本默认拒绝已暂存、未暂存及未跟踪改动，只允许从干净 Git 提交生成企业包；内层 ZIP 顶层新增 `SOURCE-COMMIT`，外层封装要求其与当前脚本工作树 `HEAD` 一致。
  - 内层 ZIP 每次生成后自动核对输出目录中的固定名外层包；内嵌 ZIP 不匹配、外层损坏或 SHA 缺失/无效时，将旧 ZIP/SHA 移入 `.stale-complete-bundles/<SHA>/`，避免继续出现在交付根目录。
  - 外层封装落盘前按字节校验内嵌内层 ZIP 与所选发布 ZIP 一致，落盘后再校验固定名外层 SHA；同步企业部署 README、多后台手册、全量执行手册和离线部署 skill。
- How:
  - 扩展 `tools/verify-internal-two-backend-complete-package.sh`，使用临时 Git fixture 验证干净源放行/脏源拒绝，并覆盖旧外层隔离、`SOURCE-COMMIT`、内嵌 ZIP 身份、固定名覆盖、节点配置归一化和敏感值不出日志。
  - Shell 语法、`git diff --check` 和双后台封包回归通过；在当前含用户本地前端改动的工作区直接运行 `package-release.sh --zip-only` 会按预期在写制品前拒绝。
- Result:
  - 后续正式企业包必须从目标提交的干净 clone 或 detached worktree 构建；本地开发改动不会被静默打入包，旧外层包也不会继续伪装成本次交付物。
  - 仅修改打包脚本、回归测试和部署文档；未修改业务代码、HTTP API、RunEvent、数据库/Flyway、SQL、性能、安全协议、generated SDK、OpenCode 源码或环境配置。

### 2026-07-24 - 修复聊天附件大文件、重复上传与 Excel 媒体报错

- Why:
  - 聊天附件上传后又被浏览器读回并内联到 Run 请求，602K 文件会放大请求体；Excel/Word 等 MIME 被 OpenCode 当作模型原生媒体后，供应商返回 `functionality not supported`。
  - 附件固定使用原文件名写入 `.testagent/attachments`，新建对话再次上传同名文件会命中已存在错误。
- What:
  - 继续复用既有工作区文件 WebSocket 分片上传，将聊天附件以请求 ID 前缀生成唯一物理名，仍保存在个人 worktree 的 `.testagent/attachments`，不写个人工作区根目录；PromptPart 保留原始展示名。
  - 工作区已上传附件只提交相对路径、原始文件名、MIME 与 `workspace_attachment` 来源，不再携带正文或 `data:` URL；后端校验路径位于当前 Workspace 后，转换为提示智能体使用工作区工具读取的 text part，平台历史消息仍保留原 file part 展示附件 chip。
  - 补充 602K Excel 路径-only、同名唯一落盘和后端不发送原生媒体 part 的回归测试，并同步 runtime、agent-web、agent-chat 与 HTTP API 文档。
- How:
  - 前端两个定向 Vitest 文件 94 项通过，agent-web typecheck 与生产 build 通过；后端 `RunApplicationServiceTest` 69 项通过，`git diff --check` 通过。
  - 使用未修改的 `.env.test` 和 `test` profile 重启 backend、opencode-manager、frontend，health/readiness 为 UP、前端 3000 返回 200、Manager 连接正常。
  - 真实创建平台会话并用已上传 Excel 路径启动 Run，请求体仅 557 bytes，后端下发两个 text part、无 file/data URL，Run `run_95bbaecd1391419598a1cf753f6c9ec3` 最终 `SUCCEEDED`。
- Result:
  - 大文件不再进入聊天 HTTP 请求，同名附件可在新旧对话重复上传，Excel 等非模型媒体文件改由智能体通过工作区工具读取；附件目录边界和既有工作区上传能力保持不变。
  - 仅调整既有 Run `parts` 适配语义和前端上传命名；未新增 HTTP 路径、RunEvent 类型、数据库/Flyway、SQL、权限、环境配置、依赖或 generated SDK，也未修改 OpenCode 源码。

### 2026-07-24 - 按当前本地代码恢复企业打包口径

- Why:
  - 用户明确要求企业包继续以当前本地代码为准，允许本地改动参与构建，并要求不要修改现有打包脚本；此前“只允许干净提交”的保护方案不再适用。
- What:
  - 完整撤回 `7ba0f7708` 对内层/外层打包脚本、回归脚本和新增 guard 的修改，恢复既有打包实现；部署 README、多后台手册、全量执行手册和离线部署 skill 改为记录当前 HEAD/工作树、允许明确本地改动，并要求每次内层重建后手工校验外层内嵌 ZIP 的 SHA。
  - 合并 `feature/frontend-beautify` 的领域模型文档和 `feat/optimize-sse-performance` 分支；后者的事件去重与 `v-memo` 已由当前主线演进实现，冲突保留主线版本。`codex/apple-design-preview` 仍有未提交文件且包含独立实验功能，本次不触碰、不合并。
  - 修正最新附件提交遗漏的一条弹窗文案断言，使测试与“智能体按工作区路径读取”的当前实现一致，不改变运行时代码。
- How:
  - 合并前用 `git cherry`、三方 diff 和 `merge-tree` 检查独有提交；领域模型索引冲突保留 XXL 与领域模型两个入口，SSE 冲突确认主线已有更完整实现后选择主线。
  - `runtime-reducer` 与 `FigmaChatPanel` 定向 Vitest 为 204 passed / 1 skipped；AI 文档校验和 `git diff --check` 通过。
  - 使用 JDK 25、未修改的 `deploy/internal/.env` 和命令行空 `VITE_TEST_AGENT_API_BASE_URL` 全量构建后端、前端、programs、worker 与内层 ZIP；双后台固定名封包回归、内外层 SHA 关联、4 类核心制品逐字节比对、JAR/RSA/160 个依赖和 worker `linux/amd64` / OpenCode 1.18.4 校验通过。
- Result:
  - 当前 `main` 保留本地附件/Ctrl+S 等最新提交并纳入后续企业包，打包脚本与 `0094e264c` 后的既有实现一致；后续通过构建后 SHA/结构校验防止误交付旧外层包。
  - 固定名 `test-agent-two-backend-complete.zip` 及 SHA 已基于当前本地代码和 `.4/.114/.2` 节点包重新生成；最终会话日志通过 `--zip-only` 进入内层后，外层按同一节点包再次覆盖封装。
  - 文档和合并涉及既有领域模型说明与前端实现历史，不新增本次 HTTP API、RunEvent、数据库/Flyway、SQL、安全配置、generated SDK、OpenCode 源码或环境配置变更。

### 2026-07-24 - 隔离本地后端运行 JAR 与 Maven 构建产物

- Why:
  - 本地后端从 `backend/test-agent-app/target` 直接运行 Spring Boot 可执行 JAR；企业打包在服务运行期间重新生成同一路径后，Boot 的按需类加载读到不一致归档，连续出现 MyBatis、Reactor、runtime 等无关类的 `ClassNotFoundException`，所有消息继而进入超时重试。
- What:
  - 新增统一运行 JAR 暂存脚本，启动前把已完成构建的 JAR 校验并复制到 `.tmp/dev-services/backend-runtime/` 的唯一文件；根目录重启脚本与单后端启动脚本只运行该不可变副本。
  - 根目录脚本兼容发现并停止旧 `target` JAR 与新运行副本进程，只在旧后端停止后清理历史副本；同步启动脚本回归、后端 README 和 AI 工作流说明。
- How:
  - 隔离重编领域模块确认缺失 class 是受干扰的半成品而非源码错误；开发脚本回归模拟覆盖 source JAR，验证已暂存副本仍完整，且 Java 启动命令不再引用 Maven `target`。
  - JDK 25 下完整构建并按 `.env.test`/`test` profile 重启 backend、opencode-manager、frontend；随后保持 JVM 运行再次执行 Maven package，`target` 更新时间变化而运行副本哈希不变，readiness 保持 `UP`，新启动线后无缺类异常或 provider header timeout。
  - 复核现有固定名企业完整包：外层 SHA/ZIP 完整性、内外层发布 ZIP SHA 一致、三台节点包清单及双后台封包回归均通过；本地启动隔离不改变企业 systemd/worker/Nginx 运行方式。
- Result:
  - 后续本地企业打包可以覆盖 Maven 构建目录而不破坏已经运行的后端；并发构建自身若互相清理 `target` 仍可能让当次构建失败，但失败会发生在停服务前，重新执行即可。
  - 未修改业务 Java、HTTP API、RunEvent、数据库/Flyway、SQL、权限、安全配置、generated SDK、OpenCode 源码或环境配置；当前企业完整包仍可按 `.4 → .114 → .2` 顺序部署并做现场验收。

### 2026-07-26 - 实现 Agent & Skill Hub 发布、引用与更新闭环

- Why:
  - 用户需要在工作台左侧增加统一 Hub，让全员浏览所有应用已 push 的 Agent/Skill，由应用管理员发布、引用、修改和确认上游更新，同时能查看当前应用引用了什么以及每个资产被哪些应用引用。
- What:
  - push 成功后从精确 Git commit 索引 `.opencode/agents` 和完整 Skill 目录，以 SHA-256 内容寻址 + GZIP 不可变制品固化每次快照；显式 publish 锁定当前修订和精确依赖。
  - 引用和取消引用实现两阶段语义：`PENDING_PUSH → ACTIVE`、`ACTIVE → PENDING_REMOVE → 删除`；只有远程 push 内容匹配后才改变有效引用状态，并阻止已有 Agent 文件或 Skill 根目录被覆盖。
  - 上游新发布版使用 active/current/incoming 三方合并；冲突先持久化且不改工作树，用户解决完后再一次性落盘。
  - 新增 AgentHub + SkillHub 统一能力市场视图，提供发现/Agents/Skills/当前应用/待更新分类、概览指标、卡片目录、显式图标+文字状态、有效引用应用数和详情引用方清单；待更新角标和当前应用统计按选中个人工作区隔离。
  - 新增两个 Flyway migration、MyBatis XML mapper、HTTP 元数据 API、文件 WebSocket ticket/RPC 操作和可重复演示 fixture，同步 API、事件流、数据库、安全、模块图及前后端 README。
- How:
  - Hub 服务定向单测 7 项、MyBatis 集成 2 项、Flyway 命名 2 项、文件 ticket 7 项、Git 合并 2 项全部通过；前端 Hub 交互 5 项、typecheck 和生产 build 通过。
  - 演示数据构造了多应用 Agent/Skill、未发布/已发布/待推送/已生效/取消待推送等状态和独立个人 worktree；Playwright 真实页面验证了 Hub 概览、当前应用两条引用及 F-COSS/Hub 演示应用引用方列表。
  - 使用 JDK 25、未修改的 `.env.test` 和 `test` profile 完整重启 backend、opencode-manager、frontend；backend readiness 和前端 3000 均正常。
- Result:
  - Agent & Skill Hub 四项核心需求、AgentHub 合并、引用状态修正、取消引用、当前应用引用库与引用方可见性已形成完整闭环。
  - 变更涉及 additive HTTP/WS 协议、关系型数据库/Flyway/MyBatis、应用成员与制品路径安全边界；不新增 RunEvent 类型，不修改 generated SDK、OpenCode 源码、环境配置或业务远程仓库。

### 2026-07-26 - 修复 Hub 取消引用、分类状态与列表竞态

- Why:
  - `PENDING_REMOVE` 仍被当前应用过滤、有效引用计数和消费者查询纳入，取消后卡片继续出现；服务又拒绝所有已有记录，导致取消后无法重新引用。
  - 分类页直接展示目标应用的引用状态，Skill 卡片把“取消待推送”误当作资产状态；首次全量目录请求迟到时还会覆盖用户已经切换到的“当前应用”结果。
- What:
  - MyBatis 当前应用过滤、有效引用计数和消费者投影统一排除 `PENDING_REMOVE`；引用记录更新补齐目标路径和别名。取消记录仍等待远端删除确认，但用户侧立即解除，`referenced=false`。
  - 再次引用会复用同一 `PENDING_REMOVE` 记录，保留 active 修订和创建审计字段，重新物化最新发布快照并转为 `PENDING_PUSH`。
  - 前端按板块拆分状态：发现/Agents/Skills 只展示资产发布状态、原创应用和归属工作区，当前应用才展示引用状态；加入请求代次 fencing，丢弃分类、工作区或搜索条件切换后的迟到响应。
- How:
  - Hub 服务 9 项与 MyBatis 集成 2 项定向测试通过；前端 Hub 7 项、全量 96 文件 1624 passed / 1 skipped、typecheck 和生产 build 通过。
  - 扩大后端回归时 workspace-management 模块通过；persistence 全模块 192 项中 64 项被既有 H2 无法识别 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `timestamptz` 阻断，Hub 自身 H2/MyBatis 用例不受影响。
  - 使用未修改的 `.env.test` 和 test profile 重启三服务；真实页面刷新前后“当前应用”均为 1 条，取消 Skill 只在 Skill 目录显示“已发布 / 原创应用 / 归属工作区”，详情重新提供“引用到当前应用”。
- Result:
  - 取消引用立即退出当前应用和消费者视图，确认前可重新引用；Skill/Agent 分类不再混入应用引用状态，刷新与快速切换不会再被旧请求覆盖。
  - 变更调整既有 HTTP 响应语义和 MyBatis SQL，不改 API 结构、RunEvent、数据库结构/Flyway、安全权限、generated SDK、OpenCode 源码或环境配置；同步更新 HTTP API、安全规范及前后端模块 README。

### 2026-07-27 - 修复企业同源 Hub 文件 WebSocket 地址

- Why:
  - 企业前端以空 `VITE_TEST_AGENT_API_BASE_URL` 构建时，Hub 文件 ticket 返回 `/api/...` 相对路径；浏览器 `WebSocket` 构造器要求绝对地址，点击 Skill 因此无法展示 `SKILL.md`。
- What:
  - `backend-api` 的共用 WebSocket URL 归一化在 API base 为空时按当前页面 origin 补全地址，并把 `http/https` 转换为 `ws/wss`；已有绝对目标 Java 地址语义保持不变。
  - 增加企业同源 Hub 文件回归，真实执行 ticket、WebSocket RPC 和 Markdown 内容返回；同步 backend-api、HTTP API、前端部署及单后台故障排查文档。
- How:
  - TDD 先稳定复现传给 WebSocket 的仍是 `/api/...`，修复后全量前端 96 个测试文件为 1626 passed / 1 skipped，backend-api typecheck 和空 API base 的 agent-web 生产构建通过。
  - 使用未修改的 `.env.test` 和 test profile 构建并启动；后端冷启动超过脚本 90 秒等待后自行达到 readiness `UP`，随后单独启动前端。Playwright 真实页面点击 Hub 演示 Skill，`SKILL.md` 正文正常展示，控制台没有 WebSocket 构造或文件连接错误。
- Result:
  - 企业同源入口现在会连接 `ws://当前入口/api/...`，HTTPS 自动使用 `wss://`，无需把入口地址硬编码进环境配置；部署现场需要重新构建并替换前端静态包。
  - 未修改 HTTP/WS wire 字段、RunEvent、数据库/Flyway、SQL、权限、安全配置、generated SDK、OpenCode 源码或环境文件。

### 2026-07-27 - 复核公共测试设计 Agent/Skill 协同契约

- Why:
  - 公共测试规约已按对象类型落入 `test-design/rules/`，需要再次确认入口、生成、方法 Skill、Phase A/B 和独立 Review 的协同是否形成可执行闭环。
- What:
  - 只读复核公共配置仓库中的三个测试设计 Agent、八个测试设计 Skill、71 张对象规则卡、阶段/追溯/审核规则和八条 eval；未修改公共配置正文。
  - 确认 OpenCode 1.18.4 能加载 1 个可见入口和 2 个隐藏子 Agent，方法 Skill 均归属 generation，规则版本和编号连续；同时识别出 Review 入参缺少其审核所需的 `sourceManifest/materialsRead/structuralGateCheck` 等信息、规则卡缺少到 Phase A 项的直接追溯、只生成单类产物及直接 Review 尚无显式路由、五类公共规则卡与其余对象规约的完整编号契约边界不清等待修正项。
- How:
  - 执行 Agent/Skill frontmatter、版本、规则编号、引用和 OpenCode 实际加载校验；逐项对照 orchestrator 的交接字段、generation 返回契约、Phase A/B 不变量、Review 质量门禁及 eval 覆盖。
- Result:
  - 基础发现、角色归属、规则数量和完整链路加载正常，批量任务规则为 17 条、五类公共规则共 71 条，不存在 129 条；当前风险集中在阶段间数据契约和请求路由，尚未修改或重新打包。
  - 本次不涉及 HTTP API、RunEvent、数据库/Flyway、SQL、性能、安全、generated SDK、OpenCode 源码或环境配置。

### 2026-07-27 - 修复应用 OpenCode 配置隐藏变更和 feature 同步阻塞

- Why:
  - 应用个人 worktree 的 `.opencode/tools/**` 已产生 Git 变更，但普通 workspace Diff 会排除整个 `.opencode`，应用 Agent Diff 又只允许 JSONC、agents、skills、tools 子目录，未知目录会两边都不可见；Git 仍把这些文件视为脏状态，因此 feature 固定提交反向合并持续提示“请先提交或回退当前个人变更”。
  - OpenCode 1.18.4 除 agent、skill、tool 外还原生识别 command、plugin、旧 mode 单复数别名，自定义配置也可引用 `lib/**` 等辅助源码，继续枚举子目录会反复产生同类遗漏。
- What:
  - 应用配置 Diff、stage/unstage/discard、上传、复制和移动统一以安全的 `.opencode/**` 命名空间为边界；后端只从 Git status 中接受真实位于该命名空间的路径，前端仅归一化后端返回值，不再维护第二份子目录白名单。
  - 配置树对普通用户展示全部用户维护目录，只隐藏根部运行依赖噪声；运行生成的 node_modules、package/lockfile 仍由 `.gitignore` 排除，一旦已跟踪或实际出现在 Git status 中则必须进入 Diff。
  - 工作区文件 WebSocket 把完整 `.opencode/**` 设为 APP_ADMIN 保护范围，封堵 command/plugin/tool 或辅助源码目录的权限旁路；同步前后端、workspace-management、HTTP API、安全、部署和测试文档。
- How:
  - 只读审计 `opencode-source/opencode-1.18.4` 的配置加载器确认 agent(s)、skill(s)、command(s)、plugin(s)、tool(s)、mode(s) 和 JSON/JSONC 路径，未修改上游快照。
  - TDD 先让 commands、lib、tools 和 package.json 的配置树/Diff/stage/上传用例失败，再实现命名空间边界；前端目标 74 项、全量 1626 passed / 1 skipped、lint、typecheck 和生产 build 通过，后端 `AgentConfigApplicationServiceTest` 49 项、`WorkspaceFileWebSocketHandlerTest` 22 项及相关模块测试通过，AI 文档校验与完整后端跳过测试打包通过。
  - 扩大后端测试在 725 项时仍被主线已知的 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData` 单项失败阻断；该失败已在 `session-log.huangzhenren.md` 记录且独立稳定复现，本次未修改对应服务/测试。使用未修改的 `.env.test` 和 test profile 重启三服务，backend health/readiness 为 UP、前端 3000 为 200、CORS 与 manager 连接正常。
- Result:
  - 所有 Git 可见 `.opencode/**` 变更都会出现在应用 Agent Diff，可被提交或回退，不再因新增 OpenCode 目录类型形成不可见脏状态并阻塞 feature 同步；普通工作区文件仍不会误入应用配置 Diff。
  - 未新增 HTTP/WS 字段或 RunEvent 类型，不涉及数据库/Flyway、SQL、性能、generated SDK、OpenCode 源码或环境配置；既有 API 行为范围扩大且权限边界同步收紧。

### 2026-07-27 - 固化企业 XXL-JOB MySQL 配置说明

- Why:
  - 现场 XXL-JOB 外部 MySQL 密码已完成纳管轮换，但此前只更新了敏感三节点交付包，仓库配置模板和部署文档没有明确说明配置落点与特殊字符处理，容易造成“包内已更新、源文件看不到”的误解。
- What:
  - 更新 `deploy/internal/backend.env.example`，明确当前 `122.210.106.43:3306/xxl_job`、`root` 现场配置和敏感密码不入 Git 的边界。
  - 更新企业部署 README、多后台/单后台手册和 `docs/deployment/database.md`，说明 `.4/.114` 敏感 `backend.env` 的密码来源、`=/@/*` 特殊字符的 dotenv 落盘规则，以及不回显密码的现场校验命令。
  - 保持真实密码只存在于受控企业节点包和目标服务器配置，不新增 HTTP/API、事件、数据库结构或脚本变更。
- How:
  - 先回顾全部会话日志近期条目；对模板、部署手册和数据库说明做定点修改，再运行文档/包校验并重新封装内外层企业包。
- Result:
  - 配置模板、文档、敏感节点包和交付校验口径一致；后续可从 `backend.env.example` 与 `MULTI-BACKEND.md` 直接定位配置位置，不再依赖 ZIP 内部临时目录。

### 2026-07-27 - 修复同仓库应用工作空间同步与历史个人路径

- Why:
  - 同一应用、版本、分支和 Git 仓库下的多个应用工作空间实际只是不同目录视图，但版本表中的 `targetCommitHash` 按目录记录且仅更新当前记录，导致一个目录发布后另一个目录及其个人 worktree 仍停留在旧提交。
  - 新增空目录只创建服务器目录而未进入 Git，跨服务器克隆后目录消失；历史个人空间又会在预期子目录缺失时回退到整个仓库，造成不同用户看到不同文件树。分支与目录异步请求还可能被迟到响应覆盖，页面表现为“没有分支”。
  - feature 同步只展示当前目录的 Git Diff，但 Git 合并会被同仓库其他目录的未提交文件阻塞，因此页面只提示待同步而看不到真实阻塞文件。
- What:
  - 将同应用、仓库、版本、分支的 target commit 作为仓库组状态统一扇出，读取旧的不一致记录时按最近更新时间自动收敛；新增目录写入并推送 `.gitkeep`，确保跨服务器可克隆。
  - 个人空间创建和历史复用严格绑定预期仓库根、工作空间子目录与 runtime 路径；打开前在干净 worktree 上合并仓库组 target，缺目录时明确报错，不再回退到仓库根目录。
  - Git Diff 保持当前目录文件列表，同时新增仓库级同步阻塞文件及所属兄弟工作空间信息；前端直接展示实际阻塞路径。工作空间设置页为分支和目录请求增加代次隔离、空结果/错误提示，并修复确认弹窗受无关 loading 状态影响的问题。
- How:
  - 后端 `GitWorkspaceServiceRealGitTest`、`ManagedWorkspaceApplicationServiceTest`、`PersonalAgentConfigRuntimeReloadServiceTest`、`ManagedWorkspaceControllerTest`、`AgentConfigControllerTest` 共 109 项通过；前端相关 5 个测试文件 148 项通过，最终并发修正定向 18 项通过，agent-web typecheck 与 `git diff --check` 通过。
  - 使用 JDK 25、未修改的 `.env.test` 和 test profile 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`，后端 health/readiness 为 UP、前端 3000 返回 200、CORS 预检为 200，manager WebSocket 已连接且 OpenCode 健康探测最终为 HEALTHY。
- Result:
  - 已发布内容会沿同一物理仓库的全部目录视图传播；历史错误 repo-root 个人空间会在再次进入时修复到正确子目录。个人未提交内容仍保持私有且不会被覆盖，但所有阻塞文件现在可见，清理后即可继续合并，不再形成无文件可处理的永久阻塞。
  - HTTP Diff 响应仅增加可选兼容字段；未新增 RunEvent、数据库结构/Flyway/SQL、安全配置、generated SDK、OpenCode 源码或环境配置变更。

### 2026-07-27 - 补齐仓库组即时同步与双拓扑验证

- Why:
  - 上一轮已统一同仓库目标提交，但应用 Agent rollout 仍只枚举发布源目录的个人记录；兄弟目录的 clean 物理 worktree 可能要等后续进入或补偿才更新，同路径 replica 元数据也可能短暂保留旧 commit。
  - 需要同时确认“同 Git 仓库多目录”和“一应用一 Git 仓库一工作空间”两种拓扑，并防止兄弟目录同步误触发 Agent dispose。
- What:
  - feature 固定提交反向同步改为按仓库组枚举当前服务器个人 worktree，按规范化 repoRoot 去重并优先发布源记录；兄弟目录同步目标提交，但不进入发布源 Agent rollout 的用户 dispose 或待处理列表。
  - 同物理仓库的本机 replica 仅在其工作空间目录真实存在时同步标记 READY；无效历史个人/replica 路径会记录警告并跳过，不再拖垮其他有效目录的 rollout。
  - 新工作空间 `.gitkeep` 提交前增加 `fetch` 和 `pull --ff-only`，避免复用旧应用副本时生成非快进 push。扩展可重复 Git fixture，加入 F-GCMS-PSN 共享仓库双目录、兄弟目录 dirty、新目录占位和单仓库单工作空间 Agent R2。
- How:
  - JDK 25 下后端相关 135 项通过，包含真实 Git merge/冲突、工作空间服务、应用/公共 Agent rollout 和 Controller；前端 5 个相关文件 148 项及 agent-web typecheck 通过。
  - `tools/create-workspace-branch-model-test-data.sh` 通过 `bash -n` 和全部真实 Git 断言；生成的本地 bare remote 不访问业务 Gitee。后端 20 模块跳过测试生产打包成功。
  - 使用未修改的 `.env.test` 和 test profile 完整重启 backend、opencode-manager、frontend；backend readiness 为 UP，前端 3000 返回 200。
- Result:
  - 共享仓库中任一目录发布后，clean 兄弟物理 worktree 立即合并同一 target；dirty/staged/untracked 仍保留用户内容并等待提交或回退后重试。只有实际加载发布源 `.opencode` 的运行态被 dispose，兄弟目录不会被误重启。
  - 仓库组只有一个工作空间时退化为既有单记录路径，未引入额外目录、Git 分支或运行态副作用。本次未新增/alter HTTP API、RunEvent、数据库/Flyway/SQL、安全权限、generated SDK、OpenCode 源码或环境配置。

### 2026-07-27 - 修正应用工作空间独立拉取与 Agent 安全更新

- Why:
  - 应用工作空间原有拉取入口依赖个人暂存状态，和约定的“远端更新独立于提交推送”不一致；标题栏同时放置拉取与刷新也造成操作按钮过多。
  - 拉取远端可能包含应用 `.opencode` 配置，需要和普通文件共用同一固定提交同步，同时遵守现有应用 Agent 空闲闸门，不能影响公共 Agent 或覆盖 dirty 个人 worktree。
- What:
  - 应用版本拉取改为受控副本显式 fetch、固定远端 tracking commit、仅允许 fast-forward，再更新同仓库组 target/replica 并安全同步个人 worktree；dirty、staged、untracked 和冲突 worktree 保留为待同步。
  - 远端差异命中应用 JSON/Agent/Skill 配置时，在共享副本切换前建立现有 APPLICATION rollout，收敛后只对受影响且空闲的应用运行态 dispose；公共 Agent 不参与。
  - 前端将“刷新文件树”和独立“拉取远程”收进工作空间标题栏同一个“…”菜单；Git Changes 的暂存、提交、白名单投影和推送流程保持原样，并继续展示仓库级待同步阻塞路径。
- How:
  - JDK 25 下 `ManagedWorkspaceApplicationServiceTest` 64 项、`ManagedWorkspaceControllerTest` 16 项通过；workspace/API 扩大测试共执行 730 项，仅命中主线已知且无关的 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData` 单项失败。
  - 前端相关 3 个测试文件 140 项通过，agent-web typecheck 与生产 build 通过；fixture 脚本通过 `bash -n`，`git diff --check` 和冲突标记检查通过。
  - 使用未修改的 `.env.test` 和 test profile 完整重启 backend、opencode-manager、frontend；backend health/readiness 为 UP、前端 3000 和 CORS 预检为 200，manager WebSocket 已连接且 OpenCode health 为 HEALTHY。
- Result:
  - 用户可从一个紧凑的“…”菜单选择本地刷新或独立拉取；拉取无需 stage，不创建提交、不推送，push 权限、目录白名单和既有 worktree 管理边界均未扩大。
  - 本次复用既有 HTTP 路径和内部同步事件，没有新增 API/RunEvent 类型、数据库/Flyway/SQL、性能或安全配置变更，也未修改 generated SDK、OpenCode 源码或环境文件。

### 2026-07-27 - 将拉取远程收敛为个人 workspace 操作

- Why:
  - 版本级拉取会更新共享 target 并同步应用内其他成员，与用户对“谁点击、谁生效”的理解冲突；成功提示也容易让人误以为一次点击会影响全应用用户。
- What:
  - 新增个人工作区 `git-pull`：只允许 owner 在自己的物理 worktree fetch/merge 远端，不更新共享 target/replica、不广播、不扫描其他成员；旧版本级入口固定返回 `VALIDATION_ERROR`，滚动升级期间收到旧拉取广播也直接忽略。
  - 当前个人 worktree 有 unstaged、staged、untracked 或未完成 merge 时拒绝拉取，Diff 展示“无法更新到远程最新提交 / 请先提交或回退下列文件”，同仓库兄弟目录折叠为“当前应用的其它 workspace / Agent 配置”。
  - 拉取包含应用 Agent/Skill/JSONC 时只由当前页面等待本人任务空闲后 dispose；提交并推送仍保留既有共享 target、多用户同步和 APPLICATION rollout 流程。
  - “刷新文件树”和“拉取远程”继续共用 workspace 标题栏“…”菜单；成功反馈明确“本次只更新你的个人 workspace，其他用户不受影响”。
- How:
  - 后端服务、Controller 和路由测试覆盖 owner、脏状态、Agent 变化、共享版本不变、零广播以及旧版本级入口无副作用；前端组件/API 测试覆盖个人 ID 路由和新手提示，并执行 typecheck、生产 build、静态差异检查。
  - 同步 workspace-management、API、shared-types、backend-api、agent-web、HTTP API、事件流和集成测试文档；使用 JDK 25 与未修改的 `.env.test` 按 test profile 重启实际服务并检查健康状态。
- Result:
  - 拉取远程变为严格按人、按个人 worktree 生效；其它用户、共享版本、推送权限和目录权限均不改变。个人拉取不产生 RunEvent 或服务器广播，Agent 变化也只热加载点击者本人。
  - 新增一个向后兼容的 HTTP 响应类型和个人拉取端点；未涉及数据库/Flyway/SQL、generated SDK、OpenCode 源码、环境配置、性能或权限扩大。

### 2026-07-27 - 企业包构建固定当前 HEAD

### Why

- 打包过程中本地 `main` 曾在构建输入之后前进，导致已生成的内包与当前 HEAD 不一致；直接复用该包存在交付旧代码的风险。

### What

- 本次企业包以最终稳定的 `29889f38d8c5` 为源码输入重新完整构建，并重新封装固定名称的企业三节点包。
- 形成打包校验约束：构建前记录 HEAD，构建后再次比较 HEAD；不一致时不得继续分发。

### How

- 重新执行 `deploy/internal/package-release.sh --output-dir deploy/internal/dist`，再执行 `deploy/internal/package-two-backend-complete.sh`。
- 校验内外层 ZIP SHA256、内嵌发布包一致性、四类发布产物字节一致、敏感节点 XXL-JOB MySQL 配置、worker `linux/amd64`、OpenCode `1.18.4`、固定脚本哈希、企业包验收脚本和 AI 文档校验。

### Result

- 内包 SHA256 为 `0526628f7d02ade564b2c6da56d2249076be8a72344a9b078c2576c15293c3cc`，外层企业包 SHA256 为 `02416716053bd01c65734f00a248862c473be04949e3ddf096e90635876c8638`；工作区无新增未提交改动，打包脚本未修改。

### 2026-07-27 - 个人拉取统一使用原生 Git 合并

### Why

- 个人“拉取远程”原本只要整棵 worktree 有任何 unstaged、staged 或 untracked 内容就拒绝，比 Git 原生 merge 更严格，也让用户误以为应用 Agent 是独立拉取的。

### What

- 移除个人拉取前的“任意 dirty 即阻止”检查；应用 workspace 和应用 Agent 统一调用现有 `git merge --no-edit <remoteCommit>`，不重叠本地改动原样保留，只在 Git 确认会覆盖时阻止。
- Git 执行器新增 `LOCAL_CHANGES` 归因和标准 stderr 阻塞路径提取；业务层与失败后实时 status 取交集，前端只展示真正挡住拉取的文件。不自动 stash、reset、commit 或 push。
- 成功提示和公共 Workspace Git Assistant Skill 明确：拉取更新点击者在当前应用的整棵个人 worktree，包括同分支其它 workspace 目录和应用 Agent；其他用户和独立公共 Agent 仓库不受影响。

### How

- 真实临时 Git 仓库验证不重叠 dirty 文件在 merge 后保留，重叠 dirty 文件返回精确 `gitBlockingFiles` 且 HEAD、内容和 merge 状态不变；common 定向 24 项、个人拉取业务 68 项通过。
- 目录权限、对话 Tool owner、应用/公共 Agent 配置、当前用户空闲 dispose 和公共 rollout 回归分别 54、33、19 项通过；前端拉取/Diff 55 项、typecheck 与生产 build 通过。
- 使用 JDK 25、未修改的 `.env.test` 和 test profile 重启 backend、opencode-manager、frontend；backend health/readiness 均为 `UP`，前端与登录 CORS 返回 200，manager WebSocket 已连接，OpenCode 1.18.4 health 为 healthy。

### Result

- 个人拉取现在与 Git 原生能力一致；应用 Agent 更新成功后仍只等待点击者空闲再 dispose，没有启动应用级或公共 rollout。
- 未修改应用工作区/应用 Agent/公共 Agent 的角色与目录权限、提交/推送白名单、共享 target、跨用户同步或 dispose 规则。无 API 结构、RunEvent、数据库/Flyway/SQL、generated SDK、OpenCode 源码或环境配置变更。

### 2026-07-27 - 后台应用更新统一为原生 Git 合并

### Why

- 应用发布后的后台 rollout 仍在 merge 前要求整个物理个人 worktree clean，导致同仓库中不相关的 spec 或应用 Agent 本地改动阻塞更新；这与已落地的个人“拉取远程”原生 Git 语义不一致。

### What

- 移除后台 feature commit 反向同步的整仓 clean 前置检查，直接执行现有 `git merge --no-edit <targetCommit>`；非重叠 staged、unstaged、untracked 内容保留并完成合并。
- Git 明确返回 `gitFailureType=LOCAL_CHANGES` 时才持久化为 `LOCAL_CHANGES` 待处理；真实冲突继续保留 `MERGE_HEAD` 与三方 index。只有已包含目标提交的用户进入既有应用级 dispose，待处理用户不 dispose。
- 补充后台 rollout 回归并同步 workspace-management README、HTTP API、事件流、集成测试矩阵与 agent-web README，明确手动拉取和后台同步使用同一 Git 原生判断。

### How

- JDK 25 下真实 Git 与工作区服务定向测试 82 项通过；工作区权限/路径/对话 Tool 74 项、公共 Agent rollout 与 dispose 协调器 30 项通过；后端 20 模块生产打包成功。
- 使用未修改的 `.env.test` 和 test profile 完整重启 backend、opencode-manager、frontend；backend health/readiness 为 `UP`，前端 3000 与登录 CORS 返回 200，manager WebSocket 已连接且 OpenCode health 为 `HEALTHY`。

### Result

- 别人推送后，本地存在不重叠 spec 或应用 Agent 改动的用户也会自动合并，并按既有规则只 dispose 该用户；只有 Git 判定会覆盖文件或发生真实冲突时才等待用户处理。
- 未改变应用工作区、应用 Agent、公共 Agent 的角色/目录权限、stage/commit/push 白名单、共享 target、跨用户范围或 dispose 时机；未新增或变更 API 结构、RunEvent、数据库/Flyway/SQL、安全配置、generated SDK、OpenCode 源码或环境文件。

### 2026-07-27 - 收敛工作区自动同步说明并续接个人拉取待重载

### Why

- 稳定文档和真实 Git fixture 仍有“任意 dirty worktree 都待同步”的旧描述，与已经落地的原生 merge 行为不一致，容易让用户误以为其他人每次都必须手动拉取。
- 个人拉取包含应用 Agent 更新且本人 Session 忙碌时，待 dispose 状态只保存在当前页面内存；刷新或关闭页面会丢失后续空闲重载机会。

### What

- 统一 README、HTTP API、事件流、模块图和测试设计：普通文件推送后自动尝试合并到相关个人 worktree；干净或仅有非重叠 dirty/staged/untracked 内容都会更新，只有覆盖风险或真实冲突才待处理，普通文件同步不 dispose。
- 调整真实 Git fixture，让非重叠 dirty worktree 在 staged、unstaged、untracked 状态下实际执行 merge 并断言目标提交已合入；同步应用 Agent 的完整 Git 可见 `.opencode/**` 目录口径。
- 个人拉取应用 Agent 已写盘但等待空闲时，按 userId 在浏览器本地存储待重载标记；刷新或重新进入后继续复用既有用户忙碌检测和 `/global/dispose`，成功或确认进程未运行后清理标记。未新增后台接口或 Java 分支。

### How

- `tools/create-workspace-branch-model-test-data.sh` 通过 shell 语法与真实 Git 断言；`tools/verify-ai-docs.sh`、agent-web typecheck、全仓 lint、前端生产 build 均通过。
- 前端 97 个测试文件执行完成，1639 项通过、1 项跳过；个人拉取、文件树、Git Changes、Agent 配置和 backend-api 定向 6 个文件共 96 项通过。
- 使用 JDK 25、未修改的 `.env.test` 和 test profile 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`；后端 health/readiness 为 UP、前端 3000 与 CORS 预检返回 200，manager WebSocket 已连接且 OpenCode health 为 HEALTHY。

### Result

- 普通文件推送不要求其他用户主动拉取，也不触发 dispose；平台自动尝试更新，无冲突就完成，有覆盖风险或冲突才由对应用户处理后重试。
- 应用工作区、应用 Agent、公共 Agent 的用户角色、目录权限、stage/commit/push 白名单、共享 target、跨用户同步范围和 dispose 规则未改变。按约定未处理仅可通过直接后台接口触发、页面没有入口的权限审计项。
- 未变更 HTTP wire、RunEvent 类型、数据库/Flyway/SQL、安全配置、generated SDK、OpenCode 源码或环境文件。浏览器禁用本地存储时仍退化为原有的当前页面内存续接能力。

### 2026-07-28 - 重建内部企业双后台交付包

### Why

- 最近的个人拉取、后台原生 merge 和待重载续接调整需要进入新的企业内完整包；历史外层包只能复用三台节点的受控敏感配置，不能继续作为代码交付物。
- 内层 ZIP 与外层内嵌 ZIP 必须来自同一次当前源码封装，避免只校验旧外层 SHA 而实际交付旧代码。

### What

- 从干净 `main` 的运行时代码提交 `8dc46d4036e9317914ec9a7bdbfa4e5c7ac762e4` 全量重建后端 JAR、同源前端、外置 programs 和 `linux/amd64` worker 镜像；再用已验真的 `.4/.114/.2` 节点配置包重建固定名双后台外层包。
- 本记录随提交进入内层 ZIP 后，仅复用已通过检查的二进制制品执行 `package-release.sh --zip-only`，随后再次运行 `package-two-backend-complete.sh`，保证最终包包含本次追溯记录且运行时代码没有二次漂移。

### How

- AI 文档、开发脚本及内部自动部署、多后台、Nginx、systemd、配置采集、MySQL、Redis、XXL-JOB 等部署 fixture 校验通过；前端 lint/typecheck 和 97 个测试文件通过，1639 项通过、1 项跳过。
- 后端全量测试执行 734 项，733 项通过；唯一失败仍是主线已知的 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData`，与本次打包及最近工作区改动无关，未隐瞒或扩大范围修改。
- worker 真实容器冒烟通过：`linux/amd64`、glibc 2.31、OpenCode 1.18.4、断网 Tool 运行、用户目录隔离和优雅退出均符合基线；JAR 内置 RSA、外置 lib manifest、必需产物、三台节点包、无 MySQL 镜像混入及 ZIP 完整性均通过。

### Result

- 最终交付物固定为 `deploy/internal/dist/test-agent-two-backend-complete.zip` 及同名 `.sha256`；当前内层 ZIP 与外层内嵌 ZIP 的 SHA256 必须相等后才可交付。
- 本次未修改业务代码、API、RunEvent、数据库/Flyway/SQL、权限、安全配置、generated SDK、OpenCode 源码或环境文件；只新增本机交付追溯记录并重新生成被 Git 忽略的企业制品。

### 2026-07-28 - 个人拉取 Agent 重载改由后台单用户排空

### Why

- 个人“拉取远程”包含应用 Agent 更新时，浏览器 localStorage 只能记住当前页面的待 dispose 状态，不能保证关页、换浏览器或后端重启后继续处理；同时必须保持该动作只影响点击者，不能复用应用级成员广播。

### What

- 新增 `PERSONAL_APPLICATION` rollout 范围：Git merge 成功且当前用户进程运行时，只登记发起用户所在服务器和本人进程，不枚举应用成员、不做后台 Git 同步、不占用 PUBLIC/APPLICATION 发布锁。
- 单用户任务复用既有 server/target 数据库租约、manager 进程身份核验、Session 空闲检测、用户消息门禁和 `/global/dispose`；进程未运行时不创建任务，下次启动直接读取最新个人 worktree。
- 个人拉取响应新增可选 `runtimeReloadStatus/runtimeReloadId`；前端移除待 dispose 的 localStorage 标记，按后台登记结果展示，仍保留滚动升级时旧后端缺失字段的当前页面兼容路径。
- Flyway 只扩展 rollout scope CHECK 约束；MyBatis 门禁 SQL 仅以 `initiated_by_user_id = 当前用户` 命中个人任务。同步 HTTP/事件/数据库/部署/测试文档、模块 README、前端 README/PACKAGE 和用户手册。

### How

- 后端相关 121 项定向测试通过；前端 3 个定向文件 102 项通过，agent-web typecheck 通过；全前端 lint/typecheck/build 通过，全量 Vitest 为 97 文件、1639 passed / 1 skipped。
- 后端 20 模块生产打包通过；全量 `mvn test` 执行 738 项，唯一失败仍是已知基线 `OpencodeProcessConfigLinkServiceTest.rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData`，本次相关模块在失败前及独立定向回归均通过。
- 使用未修改的 `.env.test` 和 test profile、JDK 25 完整重启 backend、opencode-manager、frontend；Flyway 成功应用 `V20260728100000`，backend health/readiness 为 `UP`，前端 3000 返回 200。`tools/verify-ai-docs.sh` 与 `git diff --check` 通过。

### Result

- 个人拉取应用 Agent 后的 dispose 现在由后台持久化续接，页面生命周期不再参与；只阻止和 dispose 发起者本人，其他用户、共享版本 target、公共 Agent 和应用级 rollout 均不受影响。
- 原有应用 workspace、应用 Agent、公共 Agent 的角色与目录权限、stage/commit/push 白名单、普通文件自动同步和 dispose 时机未修改。HTTP 仅 additive 字段；无 RunEvent/SSE、新文件代理、generated SDK、OpenCode 源码、安全或环境配置变更。

### 2026-07-28 - 收敛个人拉取边界并修复公共重载重复提示

### Why

- 个人应用拉取的后台单用户 rollout 只按历史 binding 查进程，失效绑定仍可能误命中；公共 Agent 手动重载成功后又会在保存收尾阶段重复消费同一待办，冲突轮询还会每秒重复弹出 dispose 提示。
- 后端全量测试被 H2 无法解析的新 PostgreSQL 方言 migration、依赖生产开发数据的旧 fixture 和缺失的新上下文依赖阻断；配置软链接异常路径还存在递归删除普通目录的危险回退。

### What

- 个人 rollout 只接受 `ACTIVE` binding，并核对 binding 与运行进程的服务器、端口、用户和 RUNNING 状态；仍只处理发起用户，不枚举成员、不广播、不扩大目录权限。
- 公共 Agent 手动重载成功后立即按 worktree/server/保存代次消费同一待办，后续目录刷新失败也不重复 dispose；冲突后的空闲轮询改为静默，仅首次显示等待提示，新一代保存不会被旧请求清掉。
- 配置软链接创建失败改为失败关闭，普通文件或目录占用受管路径统一返回冲突，不再删除后复制。
- H2 仓储测试固定到最后兼容 migration 基线，新增字段和历史数据用测试 fixture 补齐；PostgreSQL 专用 migration 继续由 Testcontainers 与真实启动验证。补齐应用上下文新增 dispatcher mock。
- 同步 runtime、persistence、agent-web README/PACKAGE 与应用 worktree 测试矩阵。

### How

- JDK 25 下后端完整 `mvn test` 的 20 个模块全部通过；持久层 192 项为 0 失败/0 异常、18 项原有环境条件跳过，应用模块 36 项为 0 失败/0 异常、1 项原有 fixture 跳过。生产 `mvn clean package -Dmaven.test.skip=true` 通过。
- 前端全仓 lint/typecheck/test/build 通过，Vitest 为 97 个文件、1640 项通过、1 项跳过；公共重载相关 3 个定向文件 93 项通过。`tools/verify-ai-docs.sh` 与 `git diff --check` 通过。
- 使用未修改的 `.env.test`、test profile 和 JDK 25 完整重启 backend、opencode-manager、frontend；backend health/readiness 为 `UP`，前端与 CORS 预检返回 200，manager WebSocket 已连接且 OpenCode `/global/config` 返回成功。

### Result

- 公共 Agent 的同一次保存/手动重载只触发一次 dispose，忙碌冲突只提示一次；个人拉取只会登记当前用户的有效运行进程。
- 未改变应用 workspace、应用 Agent、公共 Agent 的角色与目录权限、stage/commit/push 白名单、共享 target、普通文件同步或既有 dispose 范围。无 HTTP/RunEvent、生产数据库结构或 migration、generated SDK、OpenCode 源码和环境配置变更；测试-only H2 fixture 不进入生产 Flyway。

### 2026-07-28 - 新增超级管理员应用 Git 刷新

### Why

- 应用版本级 Git 拉取入口已停用，现有工作区“拉取远程”只更新当前用户，超级管理员缺少按应用刷新全部物理 feature 仓库组的页面入口。
- 公共配置初始化与应用 Git 刷新属于不同生命周期；公共配置已初始化后应继续只显示“拉取更新”，不能借此替代应用仓库刷新。

### What

- 新增仅 `SUPER_ADMIN` 可调用的应用 Git 刷新接口和“系统管理 → 配置管理 → 应用 Git 刷新”页面，按代码仓库、版本和分支归并物理 feature 仓库组，逐组返回更新、跳过、失败、个人 worktree 合并及应用 Agent 重载统计。
- 每组复用既有共享 target/服务器 replica 快进更新、原生 worktree 安全合并与应用 Agent rollout；任一组失败不阻断其他组，存在脏共享仓库、分支漂移、非快进或个人文件覆盖风险时不 stash、reset 或覆盖用户改动。
- 保留个人“拉取远程”和公共配置初始化/拉取现有语义；已停用的旧版本级拉取接口不恢复。同步 HTTP API、事件说明、安全、模块图、前后端 README/PACKAGE、测试矩阵、共享类型和用户手册。

### How

- JDK 25 下后端完整 `mvn test` 的 20 个模块全部通过；定向 workspace service 73 项、controller 18 项均通过。
- 前端全仓 lint、typecheck、test、build 通过，Vitest 为 97 个文件、1644 项通过、1 项跳过；`tools/verify-ai-docs.sh` 与 `git diff --check` 通过。
- 使用未修改的 `.env.test`、test profile 和 JDK 25 完整重启 backend、opencode-manager、frontend；backend health/readiness 为 `UP`，前端与 CORS 预检返回 200。通过真实浏览器以超级管理员登录，确认新页面列出 3 个应用和对应刷新按钮；为避免改动现有测试仓库，未实际触发刷新。

### Result

- 超级管理员现在可从独立页面按应用刷新所有相关 feature 仓库组，并安全收敛相关个人 worktree 与应用 Agent 配置；普通管理员和普通用户无权调用。
- HTTP API 为 additive 新增；未新增或变更 RunEvent/SSE、数据库/Flyway/SQL、generated SDK、OpenCode 源码或环境配置。安全面新增强制超级管理员鉴权，兼容性上不改变个人拉取和公共配置既有入口。

### 2026-07-28 - 展示应用 Git 刷新的工作空间与分支范围

### Why

- 不同工作空间版本可以绑定不同的实际 feature 分支；原页面只在执行后显示分组结果，超级管理员刷新前无法确认本次会覆盖哪些工作空间、版本和分支。

### What

- 新增强 `SUPER_ADMIN` 鉴权的应用 Git 刷新范围查询和单分支组刷新接口，复用实际刷新使用的 `repositoryId + version + branch` 分组程序；单分支请求必须精确命中三字段，只处理该组及其关联 worktree。
- “应用 Git 刷新”页面新增“工作空间 / 版本 / 分支”列，每个物理组提供“刷新该分支”，应用行保留“刷新全部分支”；两类确认框分别说明精确范围。
- 同步 HTTP API、事件说明、安全规范、模块图、测试矩阵、相关前后端 README/PACKAGE、共享类型和用户手册。

### How

- 后端 `ManagedWorkspaceApplicationServiceTest` 在 JDK 25 下 75 项通过，覆盖只 fetch 目标分支且只合并该组关联 worktree。最新 Controller 定向测试和全仓测试被同一工作区并行开发中的公共 Agent 配置发布测试编译错误及模块接口不一致阻断；未修改这些无关文件规避失败。
- 本功能前端定向 99 项测试、lint、typecheck、生产构建通过。全仓 Vitest 共 1643 项通过、1 项跳过、5 项失败；失败位于 Mermaid 编辑器和并行修改的公共配置客户端，不涉及应用 Git 刷新。
- 前一批次已使用未修改的 `.env.test`、test profile 和 JDK 25 完整重启并确认 backend readiness 为 `UP`、前端与 CORS 预检返回 200，真实页面确认 F-COSS 的两个工作空间与实际分支展示正确。本批单分支按钮由组件测试验证；因并行后端主代码当前无法编译，未把最新后端重启到运行态，也未实际刷新仓库。

### Result

- 超级管理员现在能在执行前核对应用下所有工作空间、版本与实际分支，并按需单独刷新一个分支或刷新整个应用；预览和两类执行使用同一分组来源。
- HTTP API 为 additive 新增；未新增或变更 RunEvent/SSE、数据库/Flyway/SQL、generated SDK、OpenCode 源码或环境配置。

### 2026-07-28 - 统一公共 Agent 全局 Git 刷新并修正 skill-creator 写入边界

### Why

- 公共 Agent 拉取在共享运行副本脏、跨服务器排空和个人 worktree 有本地改动时语义不一致；重复点击还可能在全局锁拒绝前先清理本地目录。
- 对话调用 skill-creator 时曾把技能直接写入共享运行目录，导致个人 Diff、个人远端分支和其他服务器均看不到。

### What

- 公共 Agent 改为单一全局刷新：先取得全局 rollout 锁，再解析所选远端分支的固定 commit；各服务器共享副本只 reset 到该 commit，脏副本必须由超级管理员明确确认后才 reset/clean。
- 每台服务器在后台把同一 commit 原生 merge 到本机全部有效公共个人 worktree，保留 staged、unstaged 和 untracked；单个 worktree 冲突进入持久化补偿，不阻断其他 worktree、共享副本或服务器排空。
- 新增 rollout 查询 API 和持久化 worktree 状态，前端全局禁用重复刷新并展示每台服务器同步、排空、个人 worktree 进度、重试次数和 `lastError`；旧按服务器 pull API 仅保留兼容入口并委托同一全局语义。
- skill-creator 1.2.0 强制先验证平台提供的当前用户个人 worktree，只在其 `skills/` 或 `.opencode/skills/` 写草稿；拒绝 `/data/**/.config/opencode`、共享运行副本、安装目录及无法证明身份的路径，并引导用户经公共 Agent Diff/提交/发布流程上线。
- 同步 HTTP API、事件轮询说明、数据库、安全、测试矩阵、模块 README/PACKAGE、共享类型和用户手册；新增 Flyway `V20260728160000`。
- 补齐前一应用 Git 提交中 `ManagedWorkspaceControllerTest` 使用 `Map.of` 所缺的 `java.util.Map` import，使当前提交可独立完成 API testCompile；不改变业务行为。

### How

- JDK 25 下后端完整 `mvn test` 的 20 个模块全部通过；真实 Git 测试覆盖 staged/unstaged/untracked 保留、锁先于清理、共享副本确认清理和个人冲突补偿。
- 前端 lint、typecheck、97 个测试文件（1648 passed / 1 skipped）和生产 build 全部通过；skill-creator 校验与 `tools/verify-ai-docs.sh`、`git diff --check` 通过。
- 使用未修改的 `.env.test` 和 test profile 完整重启 backend、opencode-manager、frontend；Flyway 实际应用新 migration，backend readiness 为 `UP`、前端 3000 返回 200，manager 启动后 OpenCode 4104 收敛到 `HEALTHY`。

### Result

- 公共 Agent 现在与应用全局刷新保持同一批次语义，但个人 worktree 使用不覆盖本地内容的原生 merge，共享运行副本只允许明确确认后的固定 commit 覆盖；任何“放弃本地变更”都不会发生在全局锁之前。
- HTTP API 与共享类型为 additive 变更；新增 PostgreSQL migration，无 RunEvent/SSE 类型、generated SDK、OpenCode 源码、环境配置或凭据变更。skill-creator 修复位于公共配置个人 worktree 的独立 Git 仓库，将单独提交。

### 2026-07-28 - 接入 Codex 只读白盒分析 MCP

### Why

- 测试人员需要在应用对话中对白盒代码做证据化分析，同时必须固定当前 workspace、阻止写文件、越界读取和命令联网，并复用现有内部模型。
- 企业现场仍是 Linux 4.19、Docker 18.09.7、x86_64；Codex 0.145.0 的 Linux 精细权限依赖 bubblewrap namespace，不能等部署后才发现宿主内核或 Docker 能力不兼容。

### What

- worker/programs 固定打包官方 Codex CLI 0.145.0 Linux amd64 musl、同标签官方静态 bubblewrap、Apache-2.0 LICENSE/NOTICE、bubblewrap COPYING 和 SHA-256 元数据；MCP SDK 精确锁定 1.29.0。官方 Codex 主归档不包含运行时 bubblewrap，必须另外携带同一 release 的 `bwrap-x86_64-unknown-linux-musl.tar.gz`。
- 新增安全 stdio MCP 门面，只暴露 `whitebox_analyze` 与 `whitebox_reply`，固定 cwd、模型、approval=never、只读权限和开发者指令；隔离临时 CODEX_HOME，只接受本进程 threadId，失败/取消/超时回收子进程，审计日志只保留 traceId、耗时、状态和稳定错误码。
- 内部模型代理新增 `/v1/responses` 流式子集，把 Codex 纯文本 message、function tool/call/output 转为现有 `/chat/completions`，再输出文本、工具参数、usage、完成或脱敏失败事件；图片、文件、内置 Web 工具与 reasoning 失败关闭，原 `/chat/completions` 不变。
- 提供应用 MCP JSONC 样例和 `whitebox-code-analyst` Agent；用户选择该 Agent 后直接对话，工具自动调用。功能仍受既有应用 workspace 成员鉴权约束，超级管理员不旁路成员校验；应用代码库引用、挂载、同步和 ManagedWorkspace/Git 刷新链路不在本次范围。
- 新增目标机预检：Linux/x86_64、kernel >=4.19、Docker >=18.09、linux/amd64 镜像、glibc 2.31、Codex/bwrap 摘要与真实 namespace/读/拒写/越界拒读/断网/Git 不变/续写必须全部通过，失败时禁止启用应用 MCP。

### How

- Responses 适配器 6 项、代理 Controller 13 项、转发服务 4 项定向测试通过；受管 workspace 成员鉴权与超级管理员成员撤销回归通过；后端相关模块主代码生产打包通过。
- MCP 契约 4 项通过，覆盖安全工具列表、固定参数、未知 thread、配置失败关闭、日志脱敏、失败/取消/超时回收；`tools/verify-ai-docs.sh`、脚本语法、lockfile 版本与 `git diff --check` 通过。
- `package-release.sh --opencode-only --no-save --no-zip` 成功构建 linux/amd64 worker 并自动完成 Codex 版本、摘要、License、原始/门面工具列表、配置失败关闭与 MCP 契约检查；最终 programs 包位于 `/tmp/test-agent-codex-release-check/test-agent-programs.tar.gz`，本次 SHA-256 为 `e5ec2975784da2a27de02ee3081090ec253770f8d96ec4a65f09ad97c29390e8`。
- 提交后再以当前提交执行完整 `package-release.sh`，后端 JAR、前端生产包、859 MiB worker 镜像 tar、181 MiB programs 和 573 MiB 企业 ZIP 全部生成成功；`/tmp/test-agent-codex-full-release/test-agent-internal-release.zip` SHA-256 为 `712da6650567666e46df172c75fe8e0c4116c4dc095f58a6c43192cfa8a434a4`。
- 当前构建机是 Apple Silicon、Docker Server aarch64 24.0.2；amd64 仿真无法创建 bubblewrap 嵌套 namespace，因此 native 沙箱 E2E 被明确跳过。现有 OpenCode worker 容器已启动并监听，但仿真内 Node health 探针不退出，完整旧 worker smoke 未完成；容器已清理。

### Result

- 本地实现、定向测试、镜像构建和离线 programs 封装完成；新增 HTTP API 为内部 additive 端点，无 RunEvent/SSE 类型、数据库/Flyway/SQL、前端业务接口、generated SDK 或 OpenCode 源码修改。
- 真实企业节点验收尚未完成。发布前必须在每台原生 Linux 4.19 / Docker 18.09.7 x86_64 worker 节点运行 `deploy/internal/check-codex-whitebox-host.sh test-agent-opencode-worker:internal`；只有完整 E2E 通过后才能给应用启用 MCP/Agent 配置。

### 2026-07-28 - 修复企业 Docker 18.09 白盒宿主预检

### Why

- 企业 `.114` 节点首次执行白盒宿主预检时，Docker `18.09.7` 的小版本 `09` 被 Bash 算术表达式按八进制解释，脚本在能力检查前退出。
- worker 镜像提供 `/bin/true` 但不提供 `/usr/bin/true`，基础 bubblewrap 探针硬编码后者会产生伪失败，不能据此判断宿主 namespace 不兼容。

### What

- Docker 与 Linux kernel 的主、次版本字段在数字校验后统一按十进制转换，兼容 `18.09.7`、`4.19.09` 等带前导零的企业版本格式。
- bubblewrap 基础探针改为镜像内真实存在的 `/bin/true`；稳定部署文档同步说明这两个兼容边界。
- 在既有 worker 镜像验收脚本中增加伪 `uname`/`docker` 回归，模拟企业 Docker `18.09.7` 和 kernel `4.19.09`，同时禁止重新引入 `/usr/bin/true`。

### How

- `bash -n deploy/internal/check-codex-whitebox-host.sh tools/verify-codex-whitebox-worker-image.sh` 与 `git diff --check` 通过。
- `tools/verify-codex-whitebox-worker-image.sh test-agent-opencode-worker:internal` 通过 Codex 0.145.0、bubblewrap 摘要、原始/门面工具列表、配置失败关闭和 MCP 契约 4 项检查；新增 `18.09.7` 回归通过。Apple Silicon 构建机仍按设计跳过原生 namespace E2E。

### Result

- 宿主预检现在可在企业 Docker 18.09.7 上进入真实能力探针，不再因版本解析或不存在的 `true` 路径产生伪失败。
- 真实企业节点 namespace、只读、拒写、越界拒读、断网和续写验收仍未完成；必须在 `.114` 原生 Linux/x86_64 节点用修复后的脚本重跑并全部返回 0 后，才能继续 worker 部署。无 API、事件、数据库、性能、安全策略、运行时镜像、generated SDK、OpenCode 源码或环境配置变更。

### 2026-07-28 - 修复白盒续写 E2E 伪模型误判

### Why

- `.114` 原生 Linux 节点已通过 bubblewrap 基础探针，但续写验收期望 `follow-up observed`、实际返回 `command observed`。
- 续写请求正确保留了第一轮 `function_call_output`；本地伪模型却优先把任意历史工具输出识别成第一轮命令完成包，因此产生测试自身的伪失败，不是 Node、MCP SDK 或 Codex thread 恢复失败。

### What

- 复用既有 `probe-codex-whitebox-e2e.mjs`，用唯一 `SCENARIO_REPLY` 标记当前续写轮次，并在历史工具输出判定前优先处理；同时断言第一轮提示和工具输出仍存在，继续证明上下文被保留。
- 给同一探针增加无需 namespace 的 `--verify-routing` 自检，既有 worker 镜像验证脚本在所有构建机上强制执行，覆盖“历史工具输出 + 新续写提示”的回归。
- 同步 Codex 白盒部署文档；未新增平行探针、门面或运行时接口。

### How

- `node --check tools/probe-codex-whitebox-e2e.mjs`、相关 Shell `bash -n` 与 `git diff --check` 通过；把当前脚本只读挂载进旧镜像执行 `--verify-routing` 输出 `whitebox-e2e-routing:reply-priority-ok`。
- `deploy/internal/package-release.sh --output-dir deploy/internal/dist --opencode-only --no-zip` 成功重建 linux/amd64 worker 和 programs；自动检查再次通过 Codex 0.145.0、Node/MCP 工具列表、配置失败关闭、MCP 契约 4 项和续写路由自检。Apple Silicon 仍按设计跳过 native namespace E2E。

### Result

- 企业探针现在能正确区分“上一轮工具输出”和“本轮续写提示”，不会把已成功恢复的 thread 误报为续写失败。
- 真实 `.114` 节点尚未用新 worker 镜像重跑完整 E2E；新包加载后仍须先执行宿主检查，只有最终输出 `Codex whitebox host compatible` 才能继续部署。无 API、事件、数据库、性能、安全策略、生产门面、generated SDK、OpenCode 源码或环境配置变更。

### 2026-07-28 - 合并最新主线并重打企业离线包与独立工具箱

### Why

- 企业现场需要基于当日最终代码重新生成可直接替换的双后端离线包，并把工具盒子作为独立部署单元交付。
- 打包期间远端连续加入应用源码重试恢复、工具盒子布局和应用切换技能目录竞态修复；旧制品不能继续沿用，必须以最终提交重新组合并校验。

### What

- 将本地 9 个既有提交 rebase 到最新远端主线，冲突处理同时保留应用源码多服务器物化、工具盒子、超级管理员按应用/分支 Git 刷新、公共 Agent 全局刷新和 Codex 白盒宿主兼容修复；最终打包基线为 `cb7525eab`。
- 生成平台内部发布包与包含 `.4`、`.114`、`.2` 三份既有节点配置的双后端外层包；Node.js 22.23.1、MCP SDK 1.29.0、Codex 0.145.0 与 bubblewrap 均继续封装在 worker 镜像中。
- 另生成 `deploy/internal/dist-toolbox` 独立工具箱交付目录。当前稳定架构是一套原子部署单元下的两张固定 `linux/amd64` 镜像：IT-Tools `2024.10.22-7ca5933-platform.2` 与 OmniTools `0.6.0-platform.1`，不嵌入 backend/worker 进程。

### How

- 合并后后端相关 Maven 模块测试、前端全量类型检查/1662 passed + 1 skipped/生产构建、IT-Tools 中文 UI 审计与 9 项测试通过；最终新增竞态修复定向 13 项通过，应用源码重试恢复 3 组集成测试退出码 0。
- worker 构建期通过 Node/MCP 依赖导入、白盒工具列表、续写路由、失败关闭、MCP 契约 4 项和 bwrap 摘要检查；Apple Silicon 构建机按设计跳过原生 namespace E2E。
- 两张工具镜像以只读根文件系统、关闭外网 masquerade 的专用 bridge 和 `unless-stopped` 实际部署为 healthy；193 条深链逐项加载且无非同源请求，ASCII、HTTP 剪贴板降级、FFmpeg、Ghostscript、图片、QR、OCR 和 AI 抠图真实功能冒烟通过。
- 内外 ZIP 逐项 SHA-256、`unzip -tq`、节点结构/脱敏/覆盖校验通过，外层嵌入内部 ZIP 的摘要完全一致。

### Result

- 最终平台内部包 `deploy/internal/dist/test-agent-internal-release.zip` SHA-256 为 `0404fb2ebbb9c73c57b64ef81c521982b0bee03cc4d872e5a76b34328d9b3e12`；双后端完整包 `deploy/internal/dist/test-agent-two-backend-complete.zip` 为 `c90e7cf10cd86c0ad18d440ed6a74f161610f1ff483987182674d699d6cc6add`。
- 独立工具箱 IT-Tools tar SHA-256 为 `93b8d1436cffa5470330cf499c102cc020203c5ba144563dcf6ad9c422cac9e3`，OmniTools tar 为 `680b575afbe7acc6dcc6af5d5659664765e4dbd89a80dab2a430014ef4cde7f5`，修改源码为 `616752bbf58e1f572803ee192f17a361ffc373788a2988778fa0aa524640976a`，目录为 `cb12b1ed4f7d61ee64299d4c15794c9b2ea463e53423bbf330ab79b09de56c38`。
- 本次仅处理合并、冲突说明与制品，没有新增 API、事件、数据库、SQL、安全策略或环境配置；最新主线自身包含既有 Flyway/API 能力，部署顺序仍须先工具节点、再后端、最后前端 Nginx。
- `.114` 曾按用户反馈通过修复后的宿主预检，但最终新 worker 仍要求 `.4` 与 `.114` 各自执行同一原生 Linux/x86_64 白盒检查；任何节点未输出 `Codex whitebox host compatible` 时不得启用白盒 MCP。

### 2026-07-28 - 工具箱改为双后台共置并重封企业包

### Why

- 企业现场不再提供独立工具节点，IT-Tools 与 OmniTools 需要直接运行在 `.4`、`.114` 两台后台机器，并由 `.2` 在其中一台不可用时切到另一台。
- 旧 Nginx 渲染只接受单个工具 endpoint，旧逐机配置包也没有后台本机 `toolbox.env`，不能直接用于该拓扑。

### What

- 复用现有工具镜像、部署脚本和 Nginx 模板，把两个工具 upstream 扩展为兼容旧单地址的逗号列表；首项 `.4` 为主用，后续 `.114` 渲染为 `backup`。
- 双后台完整包封装时为 `.4`、`.114` 节点包分别生成绑定本机 IP 的 `toolbox.env`，并把 `.2` 的两套工具 upstream 固定为 `.4/.114`；内层发布包必须同时包含两个镜像 tar/SHA 和部署/诊断脚本。
- 部署文档改为 `.4 -> .114 -> .2`：两台后台各自加载同一对镜像并通过健康诊断，最后才让 `.2` reload 双 upstream Nginx；不再需要第三台工具服务器。

### How

- `configure-nginx.sh` 使用现有 dotenv、trim 和逐行模板渲染流程生成主/备 server 指令，没有新增平行配置器；旧单地址仍可使用。
- Nginx、逐机多后台校验和完整外层包三个脚本测试通过，覆盖缺失地址失败关闭、单地址兼容、双地址主备、节点 `toolbox.env`、前端固定 upstream、制品结构、敏感配置不输出和固定名覆盖。
- Shell 语法、`git diff --check` 通过；企业完整 ZIP 在本次记录纳入内层发布包后重新封装并校验。

### Result

- `.4` 和 `.114` 各运行 `test-agent-it-tools:18120`、`test-agent-omni-tools:18121`，`.2` 只承担统一入口和主备代理；工具镜像内容及 193 项离线功能口径没有改变。
- MCP 依赖的 Node.js 22.23.1 与 MCP SDK 1.29.0 仍在 worker 镜像内，不依赖后台宿主额外安装 Node。
- 本次只修改部署脚本、Nginx 配置、离线封装和部署文档；无 API、事件、数据库、SQL、generated SDK、OpenCode 源码或业务安全边界变更。企业节点仍需分别通过白盒宿主检查后才能启用 MCP。

### 2026-07-28 - 企业发布按运行组件指纹增量封装

### Why

- 后续日常版本若工具箱、Codex MCP 和 OpenCode Manager 均未更新，不应在每个企业包中重复携带数百 MiB 镜像/programs；任一组件真实变化时又必须自动恢复完整交付，不能靠操作人手工删 tar。
- 工具箱已经改为 `.4/.114` 双后台共置，正常后台部署入口需要同时处理工具容器，不能继续依赖部署 Java 后再执行一段独立手工命令。

### What

- `package-release.sh` 新增持久化内容指纹和组件清单：OpenCode Manager、OpenCode runtime、Codex MCP、Node/MCP SDK、bubblewrap、programs 与 worker 镜像作为一个原子 `worker runtime`；IT-Tools、OmniTools、修改源码与目录作为一个 `toolbox`。首次、变化或 `--include-all-components` 时标记 `included` 并构建/入包，未变化时标记 `reuse` 并省略对应大制品。
- `--zip-only` 校验当前制品指纹并保持同一发布批次已有的组件选择，避免仅补会话日志时误删刚构建但尚未部署的全量组件；`--component-plan-only` 可只查看决策，`--component-state-file` 可把构建基线放到稳定路径。
- 内外包校验和 `.4/.114` 部署脚本同步识别组件清单。全量部署成功后在各后台记录实际安装指纹；增量复用前必须同时满足目标指纹一致、Manager/OpenCode/Codex 文件齐全、worker 健康和两套工具容器诊断通过，缺失或不一致时在替换平台前失败。
- `deploy-backend-node.sh` 在工具箱 `included` 时自动提取、部署和诊断，在 `reuse` 时只校验并复用；稳定部署文档删除 `.4/.114` 的重复手工工具箱步骤，并明确新装、扩容、灾备及机制迁移首包必须全量。

### How

- 新增增量组件夹具，覆盖首次全量、同批 `zip-only` 保持选择、下一发布无变化省略、仅 worker runtime 变化、清单和制品指纹戳；扩充外层包、双后台、自动节点测试，覆盖省略大文件、自动工具部署、目标安装指纹记录及错配拒绝。
- `tools/verify-internal-incremental-components.sh`、`tools/verify-internal-two-backend-complete-package.sh`、`tools/verify-internal-multi-backend-node.sh`、`tools/verify-internal-auto-node-deploy.sh`、`tools/verify-internal-nginx-config.sh`、`tools/verify-dev-scripts.sh`、`tools/verify-ai-docs.sh`、相关 Shell `bash -n` 与 `git diff --check` 均通过。
- 真实仓库执行 `package-release.sh --component-plan-only`，因本机尚无新机制状态基线，worker runtime 和 toolbox 均正确计划为 `included`；本批只实现并验证脚本，没有重新构建大型 Docker 镜像或最终企业 ZIP。

### Result

- 日常正常打包会自动跳过未更新的工具箱和包含 OpenCode Manager/Codex MCP/Node 的 worker runtime，大组件有任何输入变化时自动整组打回；`.4/.114` 使用同一后台命令完成平台与工具箱部署。
- 兼容无组件清单的历史全量包；迁移后的第一次正式交付必须使用 `--include-all-components` 并在两台后台成功部署，之后才能使用 `reuse` 增量包。
- 未修改 HTTP API、RunEvent/SSE、数据库/Flyway/SQL、generated SDK、OpenCode 上游源码或 `.env.local`；安全与兼容性变化仅限离线包组件完整性和目标指纹失败关闭。

### 2026-07-28 - 修复企业首次工具箱发布的迁移顺序与旧 Docker PID 目录

### Why

- `.4` 用最新企业包启动时，平台 PostgreSQL 已应用 `V20260728160000`，新合入但版本较早的工具点击迁移 `V20260727203500` 被 Flyway 判定为未按顺序，Java 持续退出且 8080 不监听。
- 两个工具镜像在企业 Docker 18.09 的只读根文件系统中启动时，Alpine `/var/run -> /run` 软链接没有被 `/var/run` tmpfs 正确覆盖，Nginx 无法写入 PID 文件。

### What

- 工具点击迁移在首次企业稳定交付前调整为 `V20260728160800`，排到当前已发布最高平台迁移之后；生产继续使用默认顺序 Flyway，不开启 `outOfOrder`，不修改 `flyway_schema_history`。
- 真实 PostgreSQL 工具点击集成测试先迁移到企业存量基线 `V20260728160000`，再按默认配置升级到新迁移，固定现场升级路径；H2 定向测试和数据库文档同步新版本。
- 工具箱容器把 PID tmpfs 从软链接路径 `/var/run` 改为真实目录 `/run`；平台契约校验同时要求 `/run` 且拒绝回退 `/var/run`，部署文档补充 Docker 18.09 原因。

### How

- `mvn -pl test-agent-persistence -am clean test -Dtest=FlywayMigrationNamingTest,MyBatisToolboxClickRepositoryIntegrationTest,MyBatisToolboxClickRepositoryPostgresqlIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`：7 项通过，真实 PostgreSQL 存量升级成功。
- `python3 toolbox-source/scripts/verify_platform_contract.py --root .`、四组企业封包/逐机脚本校验、`bash -n deploy/internal/toolbox-docker.sh` 和 `git diff --check` 通过。
- IT-Tools 与 OmniTools 两张锁定的 `linux/amd64` 镜像分别使用只读根文件系统和 `/run` tmpfs 真实启动，`/run/nginx.pid` 可写且容器保持运行。

### Result

- 当前提交是重新生成企业内层发布 ZIP 和固定名双后台外层包的修复基线；制品 SHA-256 在提交后的真实打包与内外嵌套校验完成后交付。
- 未修改 HTTP API、RunEvent/SSE、业务 MyBatis SQL、权限、密钥、generated SDK、OpenCode 上游源码或环境配置；数据库影响仅为尚未进入企业稳定库的新增迁移版本调整，表结构内容不变。

### 2026-07-28 - 固化多人 Flyway 迁移发布门禁

### Why

- 14 位时间戳只能避免多人创建相同版本，不能防止较小版本在较大版本已经部署后才合并；企业现场已重复出现 Flyway validate 拒绝启动。

### What

- 在 `AGENTS.md`、数据库稳定文档和 persistence 模块说明中明确：开发时间戳只是候选版本，发布集成人必须同时对照目标库最高历史和本次全部新迁移统一排序。
- 尚未进入共享库的迁移可在合并前改号；已进入任何共享或稳定库的迁移保持不可变。发现倒序或环境分叉时停止发布，禁止使用 `outOfOrder`、`repair` 或手改 `flyway_schema_history` 掩盖。

### How

- 正式打包前要求用真实数据库先迁移到已部署最高版本，再以默认 Flyway 配置升级到当前 HEAD；空库全量建库不能替代该升级路径。
- 继续复用既有 `FlywayMigrationNamingTest` 做文件名和唯一性检查，不新增平行迁移框架。
- 默认 JDK 17 首次运行无法加载已有 JDK 21 测试字节码；切换 JDK 25 并执行 `mvn -pl test-agent-persistence -am clean test -Dtest=FlywayMigrationNamingTest -Dsurefire.failIfNoSpecifiedTests=false` 后 2 项通过，`git diff --check` 通过。

### Result

- 多人 Flyway 变更从“各自生成时间戳”升级为“发布前统一编排 + 生产基线升级验证”的强制门禁；本次仅修改工程规范和文档，不新增或改写 migration、SQL、API、事件、环境配置或生产数据。

### 2026-07-28 - 重打公共 Agent/Skill 完整替换包并公共启用白盒分析

### Why

- 用户确认白盒 MCP 注册应进入公共 `opencode.jsonc`，并要求把今天更新的公共 `skill-creator` 与需要替换的全部公共 Agent/Skill/Tool 一起打包。
- 用户要求对外白盒 Agent 不出现 Codex 字样；旧文档仍写按应用启用和旧 MCP 名称，与最终公共启用口径不一致。

### What

- 公共个人配置分支提交 `b6247bd`、`e771bed`：注册中性名称 `code_analysis` MCP，新增 `whitebox-code-analyst`，并保证 Agent 描述、正文和工具权限名称均不含 Codex 字样；保留底层程序路径和模型环境变量的技术契约。
- 以当前公共 `master` 提交 `b67700a` 为唯一底座，在一次性 detached worktree 只叠加 `skill-creator 1.2.0`、`skill-optimizer 1.1.0`、白盒配置和统一 `metadata.source: test-agent`，没有带入个人 worktree 的历史验收样例；归档集成提交为 `ca6e38c`。
- 覆盖生成固定名 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及 `.sha256`，包内包含完整公共 `opencode.jsonc`、6 个 Agent、15 个 Skill、6 个 Tool 和说明，共 653 个受 Git 跟踪文件。
- 同步公共配置模板、白盒部署说明、企业部署入口和安全规范：当前公共配置会为所有使用该配置的应用展示白盒分析 Agent，实际代码访问仍必须通过当前应用成员和 workspace 鉴权，超级管理员不旁路成员校验。

### How

- 公共 `skill-creator`/`skill-optimizer` 及包内全部 15 个 Skill 通过 1.2.0 校验器；ZIP CRC、归档文件树与集成提交逐文件一致、禁止路径、历史验收样例、必需文件、Agent 中性名称、JSONC 模型参数和 SHA-256 校验全部通过。
- OpenCode 1.18.4 从解压目录发现全部 15 个包内 Skill，成功加载 `whitebox-code-analyst` 和 `code_analysis` MCP；解析后的 Agent 不含 Codex 字样。运行时同时合并本机全局 Skill，因此校验按包内来源路径判断，不误用运行时总数。
- `tools/test-codex-whitebox-mcp.mjs` 使用当前离线 programs runtime 执行 4/4 通过，覆盖两工具契约、固定 workspace/模型/审批策略、thread 所有权、失败关闭、日志脱敏和子客户端回收。

### Result

- 公共完整替换包 SHA-256 为 `5797954135302200d9e6fe4667ec2095059d76bfa7f656ad181185a6a32ac591`；企业现场应先部署配套 Java/programs/worker 并逐节点通过 Linux 宿主预检，再经公共 Agent 个人 worktree 导入、查看 Diff、提交和发布，禁止直接覆盖共享运行目录。
- 本次没有修改 HTTP API、RunEvent/SSE、数据库/Flyway/SQL、generated SDK、OpenCode 上游源码、环境配置或密钥；权限模型未改变，但功能可见范围由原计划的逐应用配置调整为公共配置覆盖的全部应用。

### 2026-07-28 - 应用 Git 刷新只展示已有 feature 分支的应用

### Why

- 超级管理员“应用 Git 刷新”页面此前会列出尚未创建任何工作空间版本分支的空应用；这些应用没有可刷新的物理 feature 仓库组，展示后只有空状态，和按钮的执行语义不一致。

### What

- 应用 Git 刷新范围服务继续复用既有 `repositoryId + version + branch` 分组程序，并过滤 `groups` 为空的应用；有实际分支的启用、停用应用仍然保留。
- 前端对旧后端返回的数据增加同口径兼容过滤，并把空态改为“暂无已创建 feature 分支的应用”。
- 同步 workspace-management、agent-web、用户手册、HTTP API 和集成测试设计，并补充前后端回归用例。

### How

- `ManagedWorkspaceApplicationServiceTest` 相关 76 项通过；前端定向 10 项、全量 100 个测试文件 1673 passed / 1 skipped，workspace 类型检查、生产构建、AI 文档检查和 `git diff --check` 通过。
- 使用 JDK 25 执行 `restart-dev-services.sh` 时，后端与前端生产包均构建成功；真实后端启动被本机数据库已执行但当前工作树未解析的 migration `20260727203500` 按 Flyway 校验拒绝，未使用 `repair`、`outOfOrder` 或手工修改历史表绕过。

### Result

- 超级管理员刷新页面和范围 API 现在都只返回确实存在 feature 分支组、能够执行刷新的应用；刷新、合并个人 worktree 和 Agent rollout 的既有执行逻辑不变。
- 本次只收窄既有 HTTP 查询响应集合，不修改路径、DTO、RunEvent、数据库、SQL、权限、安全、generated SDK、OpenCode 源码或环境配置；真实页面启动受现有本机 Flyway 历史分叉阻断，自动化与生产构建已完整通过。

### 2026-07-29 - 公共个人配置在受管启动时自动恢复

### Why

- 公共个人 worktree 与用户 OpenCode 进程虽然在同服创建，但既有启动程序每次都会把受管软链接重置到共享副本，导致重启后个人预览消失；界面还把公共和应用“Agent 配置更新”都描述成简单重载，无法说明未提交内容和应用 feature 合并语义。

### What

- 公共启动程序在每次受管启动/重启前解析当前用户、当前服务器、`ACTIVE` 且稳定命名的 `public-{userId}` worktree，校验受管根与物理 `opencode/` 后直接切换链接；无有效目录或解析失败时回退共享配置，不阻断进程启动。
- 首次初始化因健康检查后才创建公共个人 worktree，创建完成后自动激活；若启动前已经加载同一路径则跳过重复 `/global/dispose`。全程不提交、stash、reset、clean 或删除 staged、unstaged、untracked 内容。
- 左侧 Agents 公共/应用根按钮和小宠物确认框同步说明：公共只加载本人 worktree 并在后续启动自动恢复；应用先安全合入当前应用 feature 固定提交，再刷新本人运行态。同步后端、前端、用户手册、HTTP、部署和测试文档。

### How

- 自动选择仅在受管 start/restart 路径执行一次同服本人状态过滤查询和少量文件系统检查；不增加轮询、定时任务、前端请求、Git fetch/pull 或 Git 状态扫描。失败日志使用结构化事件，只记录服务器、用户、worktree 标识与异常类型，不输出路径或异常正文。
- 相关后端 86 项测试通过；前端全量 Vitest 为 1673 passed / 1 skipped，workspace typecheck、生产 build、AI 文档检查和 `git diff --check` 通过。完整 `mvn test` 的前 19 个模块通过，最后仅被既有 `ReferenceRepositoryContextTest` 重复注册 bean 失败阻断。
- JDK 25 下真实启动的后端生产构建通过；readiness 被本地数据库已执行但当前代码未解析的 migration `20260727203500` 拒绝，未使用 `repair`、`outOfOrder`、手工修改历史表或环境文件绕过。

### Result

- 有效公共个人配置现在会跨受管启动/重启保留；公共按钮仍用于进程运行期间立即加载新修改，应用按钮保持“合入 feature 后刷新本人”的原语义，均不影响其他用户。
- 未新增或变更 HTTP DTO、RunEvent、数据库/Flyway/SQL、权限、generated SDK、OpenCode 上游源码或环境配置；运行期开销只增加启动时一次窄查询和路径检查，真实页面联调仍受本机 Flyway 历史分叉阻断。

### 2026-07-29 - 修复后端运行包缺失 PostgreSQL 驱动

### Why

- 合并 `origin/main` 并重新构建后，后端在 Druid 初始化阶段因 `ClassNotFoundException: org.postgresql.Driver` 退出；`test-agent-app` 新增的直接 `test` scope 声明覆盖了 persistence 模块传递的 `runtime` 依赖，导致测试编译和 Maven 打包成功但可执行 JAR 不含驱动。

### What

- 把 `test-agent-app` 对 `org.postgresql:postgresql` 的直接依赖恢复为 `runtime`，继续复用既有驱动版本和 persistence 数据访问链路，不新增依赖版本、配置项或业务实现。

### How

- JDK 25 下执行 `restart-dev-services.sh --profile test --env-file .env.test`，后端 20 模块打包、opencode-manager 构建、前端类型检查和生产构建通过；不可变运行 JAR 已确认包含 `BOOT-INF/lib/postgresql-42.7.11.jar`，启动日志不再出现驱动缺失。
- 只读核对 `.env.test` 数据库的 Flyway 历史，确认其已按 installed rank 依次执行 `V20260728160000`、旧 `V20260727203500` 和 `V20260728103000`，而当前代码只解析改名后的 `V20260728160800`；未执行 `repair`、`outOfOrder`、历史表修改或环境文件替换。

### Result

- PostgreSQL 驱动已重新进入后端生产运行包；本次不修改 HTTP API、RunEvent、数据库结构/SQL、权限、安全、generated SDK、OpenCode 源码或环境配置。
- 三服务仍未启动完成：后端被既有 Flyway 历史分叉 `Detected applied migration not resolved locally: 20260727203500` 拒绝，需先确定共享测试库与企业基线的显式兼容方案，不能以运行参数绕过。

### 2026-07-29 - 兼容工具盒子分叉迁移历史并恢复三服务

### Why

- 本机测试库已经执行旧工具盒子 `V20260727203500`，当前企业顺序链只保留改名后的 `V20260728160800`，Spring Boot 自动 Flyway 在业务 Runner 前校验并拒绝启动。
- 旧 `DatabaseMigrationRunner` 与 Boot 自动 Flyway 重复执行 migration，且 Java 与 `application-test.yml` 都启用了乱序模式，不符合已部署 migration 不可变和默认顺序升级规则。

### What

- 用 Spring Boot `FlywayConfigurationCustomizer` 替换重复的 ApplicationRunner 迁移器；在唯一 Flyway Bean 校验前读取已应用 history，仅命中旧工具盒子版本时追加隔离 compatibility location。
- 旧脚本按原始字节和 checksum 保存在 `db/migration-compat/toolbox`；当前 `V20260728160800` 的建表和索引改为幂等，使旧历史与企业顺序基线都收敛到同一结构。
- 删除 Java 和 test profile 的 `outOfOrder`，并同步 app/persistence/backend README、包说明、数据库与后端部署文档和后端数据规范。

### How

- 真实 Spring Boot Flyway 初始化 + PostgreSQL 16 Testcontainers 覆盖企业 `V20260728160000` 基线和旧版本已应用历史；旧脚本 SHA-256 固定为 `1bb00e2aec40e1eaf286e5351e474413fc5dba860b2bbe3a9b5b48c8ef615ec6`。
- 迁移命名、H2/PostgreSQL 工具点击 Repository、Boot 双历史和内存参数 Runner 共 11 项测试通过；JDK 25 后端 20 模块生产打包通过。
- 使用未修改的 `.env.test` 和 test profile 完整重启 backend、opencode-manager、frontend；health/readiness 均为 `UP`，前端 3000 与登录 CORS 正常，manager WebSocket 已连接并恢复用户进程。

### Result

- 本机旧 history 在 Boot validate 前成功解析，按默认顺序执行 `V20260728160800` 和后续待执行 migration 后启动完成；未清空数据库，未执行 `repair`、乱序迁移或手工修改 `flyway_schema_history`。
- 未变更 HTTP API、RunEvent/SSE、权限、安全、generated SDK、OpenCode 源码或环境配置；只调整数据库迁移兼容装配、工具盒子幂等 migration、测试和稳定文档。
- 用户把本地提交 mixed reset 到 `origin/main` 后仍保留了大量既有未提交改动；本次提交只暂存迁移兼容相关内容，其余工作树改动不覆盖、不丢弃。

### 2026-07-29 - 恢复 mixed reset 内容并完成整体复验

### Why

- 默认 mixed reset 把 reset 前 16 个本地提交展开为 72 个未提交文件；这些文件包含公共个人配置、跨服 worktree、企业双后台与增量打包、工具盒子验收和前端兼容改动，需要在不丢失历史成果的前提下重新提交。

### What

- 对照 reflog、原提交链和全部近期会话日志确认 72 个文件的来源；保留既有模块边界、公共路由程序和文档，不新增平行 API、服务或临时兼容实现。
- 将恢复内容与已提交的 Flyway 双历史兼容实现组合复核；API、共享类型、安全、部署与用户手册仍保持同步，未修改 `.env*`、generated SDK 或 OpenCode 上游源码。

### How

- JDK 25 下执行 19 模块 Maven 定向 reactor，workspace 130、runtime 32、API 25、persistence 8、Spring Boot/PostgreSQL Flyway 2，共 197 项测试通过。
- agent-web 5 个定向 Vitest 文件 206 项通过，`vue-tsc` 类型检查通过；5 组企业部署验证脚本、Shell/Python 语法、工具盒子 193 项平台契约、`git diff --check` 和冲突标记检查通过。
- 当前 backend health/readiness、frontend HTTP 和 backend/manager/frontend 三个 screen 进程在提交前再次复核。

### Result

- mixed reset 展开的既有成果已具备重新提交条件，没有发现测试失败、冲突标记、敏感环境文件或未记录的数据库绕过；本次不重写历史提交，也不推送远程。

### 2026-07-29 - 白盒分析 MCP 默认切换企业 DeepSeek

### Why

- 用户要求公共白盒分析 MCP 默认使用企业内部 DeepSeek，现有公共 JSONC 示例和完整替换包仍使用 `qwen-prod / Qwen3.6-27B / 131072`。

### What

- 公共 MCP 默认配置统一改为 Java 代理路由键 `deepseek-prod`、模型 `DeepSeek-V4-Flash-W8A8` 和真实上下文窗口 `65536`；明确 `deepseek-prod` 不能与 OpenCode provider key `enterprise-deepseek` 混用。
- 同步白盒 MCP 契约测试夹具、部署说明和安全规范；不修改代理地址、密钥、用户 UCID、MCP 工具、Agent 权限或 `approval=never` 边界。
- 以既有完整公共替换包为底座重打 `deploy/internal/dist/test-agent-public-agents-skills.zip`，只同步包内 `opencode/opencode.jsonc` 和 README 的默认模型说明，其余 Agent、Skill、Tool 与材料保持原样。

### How

- 使用离线 programs runtime 执行 `tools/test-codex-whitebox-mcp.mjs`，4/4 通过；ZIP CRC、包内三项 DeepSeek 配置、README 说明和 SHA-256 校验通过。
- 新包归档提交为 `f61d3bf060e54d387d12ab71e3630b884184f176`，SHA-256 为 `2065ec162f3d419ae38442aefb15e28fb2c502f93fd79cb963690b25aea1472d`。

### Result

- 企业导入公共完整替换包后，`code_analysis` MCP 默认通过既有 Java 内部模型代理调用企业 DeepSeek；模型切换仍由公共 JSONC 配置表达，没有新增硬编码 provider 分支或平行代理链路。
- 本次未修改 HTTP API、RunEvent/SSE、数据库/Flyway/SQL、generated SDK、OpenCode 上游源码、环境配置或密钥。

### 2026-07-29 - 固化 Flyway 企业交付闸门

### Why

- 为兼容本地旧 `V20260727203500` 而把企业已执行的 `V20260728160800` 改成幂等 SQL，导致企业 checksum 从 `-1966404877` 不匹配为 `-74327385`；既有“已执行 migration 不可改”经验未转化为打包和测试强制门禁。

### What

- 同步 `AGENTS.md`、研发工作流、自检清单、后端数据规范、数据库说明、企业多后台手册与完整升级执行单，明确主 migration 原始字节、四类已知历史和未知 checksum 失败关闭。
- 更新 `enterprise-offline-deploy` 技能，将目标库 history 留存、真实 PostgreSQL 存量升级、历史 SHA-256 锁定和最终 JAR 内 migration 校验设为企业打包前强制步骤。

### How

- 工具盒子主 migration 锁定 Flyway checksum `-1966404877` 和 SHA-256 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`；`-74327385` 只允许作为隔离兼容变体。
- 文档门禁要求空库、企业已执行基线、旧版本和误发幂等变体均经真实 PostgreSQL 验证，打包后用 `unzip -p ... | shasum -a 256` 再校验实际 JAR 资源。

### Result

- 今后 Flyway 企业交付不再以空库或本地启动成功代替存量升级；已执行字节、源码测试与包内资源形成三层校验。禁止 `repair`、`outOfOrder`、手工改历史表或新建平行迁移器的边界已同步到企业部署技能。

### 2026-07-29 - 修复企业工具盒子 Flyway checksum 分叉

### Why

- 企业 `.4` 后台启动日志确认 `V20260728160800` 已执行 checksum 为 `-1966404877`，当前包却携带 checksum `-74327385` 的幂等改写版本，Flyway 因已发布 migration 字节被改动而拒绝启动。

### What

- 将主目录 `V20260728160800` 恢复为企业已执行的原始字节，并把误发幂等版本按原始字节隔离到专用 compatibility location；两份文件分别锁定 SHA-256 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2` 和 `e8b21da5fb7a8c286b86ded9c0d12691c7e8b6e5d170dc16c5ff76a044bdcdc8`。
- 扩展唯一 `FlywayConfigurationCustomizer`：按 history 中的版本和 checksum 选择原企业版本、旧版本或误发幂等版本；未知 checksum 不兼容、不绕过，继续交由 Flyway 失败关闭。
- 增加 migration 字节锁定测试和真实 PostgreSQL 四类历史验证，不使用 `repair`、`outOfOrder` 或生产 history 改写。

### How

- JDK 25 下定向执行 `FlywayMigrationNamingTest` 4 项和 `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 5 项，全部通过；完整后端测试首轮仅命中已知的 `RunRuntimeLossConvergenceSchedulerTest` 一秒时序抖动，后续完整重跑未产生新的 Surefire 失败报告。
- 使用未修改的 `.env.test` 完整重启 backend、opencode-manager、frontend；本机同时存在旧版本与 checksum `-74327385` 的真实 history，Flyway 成功校验 76 个 migration，health/readiness 为 `UP`，前端 3000 返回 200，manager 后续健康探测稳定。

### Result

- 企业原始 checksum `-1966404877`、旧版本历史和误发 checksum `-74327385` 均有明确且字节精确的兼容路径；任何未知分叉仍会阻止启动，避免掩盖生产历史问题。
- 本次未变更 HTTP API、RunEvent/SSE、数据库结构、性能策略、安全边界、generated SDK、OpenCode 上游源码或 `.env*`；变更仅涉及 Flyway 兼容装配、历史 SQL 归位、测试和会话记录。

### 2026-07-29 - 阻止企业节点继续加载旧 persistence JAR

### Why

- `.4` 二次部署日志仍显示数据库 checksum `-1966404877`、本地解析 `-74327385`；本机最终外层包内 migration 已核验为 `777a96...51f2`，说明企业运行目录实际加载了旧 `test-agent-persistence` JAR，而既有门禁只检查外层 ZIP 和瘦 `test-agent-app.jar`，没有证明外置 `backend/lib` 已替换。

### What

- 扩展现有 Mac 打包、固定外层封装、标准后台部署和节点复验入口，统一要求恰好一个 `test-agent-persistence-*.jar`，锁定工具盒子企业 migration SHA-256 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`。
- 标准部署在启动 Java 前同时校验发布 ZIP 内 migration、安装后 migration、发布与安装 persistence JAR 完整 SHA；旧包、旧解压内容或未替换的 `backend/lib` 均直接失败。
- 修正企业部署技能和数据库/部署文档：Flyway SQL 位于外置 persistence JAR，不在 PropertiesLauncher 使用的瘦 app JAR 中。

### How

- Shell 语法、diff 检查、AI 文档、自动节点部署、双后台节点和固定外层包验证通过；后两项新增错误 migration 反例，确认错误 persistence JAR 会在启动前被拒绝。
- JDK 25 后端独立封包实际输出 `Packaged persistence JAR Flyway migration verified`；使用未修改的 `.env.test` 重启 backend、manager、frontend，Flyway 校验 76 个 migration，health/readiness 为 `UP`、前端 3000 为 200、登录 CORS 正常，manager 探测恢复稳定健康。

### Result

- 新部署链路不再把“外层 ZIP SHA 正确”误当成“运行目录 Flyway 资源已更新”；现场仍需用外层包 SHA 和安装后 migration SHA 区分旧 U 盘包、旧解压目录或未替换运行目录，禁止修改数据库 history。
- 本次只调整离线打包/部署校验、测试、技能和稳定文档；未变更 API、事件、数据库结构、性能策略、安全契约、generated SDK、OpenCode 源码或环境配置。

### 2026-07-29 - 增加仅外层换肤的工作台视觉预览

### Why

- 用户希望降低工作台的 IDE 感，但明确要求不调整现有整体布局，尤其不改变右侧 Agent 对话的尺寸、结构和样式，仅比较外层配色。

### What

- 在既有 `frontend/interaction-visual-demo` 参考目录新增独立 `cloud-workbench.html`，严格保留 36px 顶栏、48px 活动栏、262px 左栏、中间编辑区和 450px 对话区。
- 将页面底色、顶栏、活动栏、左侧外壳、选中态和外层分隔线收口为单独 shell token，提供“云白工行红 / 纯雪白 / 鼠尾草灰”三套对照；编辑器和对话区使用固定 inner token 与字面颜色，不参与主题切换。
- 补充视觉参考目录 README 和前端总览说明；该页面不加入 pnpm workspace、不调用后端，也不进入生产构建。

### How

- 对照 `FigmaShell.vue`、`FigmaFileExplorer.vue` 和 `FigmaChatPanel.vue` 的现有尺寸与关键样式制作静态预览，没有新增第二套生产组件或修改正式 Vue 页面。
- 使用本地 HTTP server 在 `http://127.0.0.1:4173/cloud-workbench.html` 启动，并通过 Playwright 在 1440×980 视口依次截图检查三套主题；控制台 0 error / 0 warning。
- 三套主题下重复读取对话根元素计算样式，均保持 `450px`、`rgb(255, 255, 255)` 背景、`rgb(51, 51, 51)` 文字和同一字体栈。

### Result

- 已形成只验证外层换肤的可交互 HTML 预览，默认云白工行红方案；不会把对话区改造成新的卡片或气泡体系。
- 未修改生产前端代码、HTTP API、RunEvent/SSE、数据库/Flyway、性能、安全、兼容性、generated SDK、OpenCode 源码或环境配置。

### 2026-07-30 - 补齐企业增量封包的 persistence 测试夹具

### Why

- 当前 HEAD 相对上次全量企业包只需要更新 Java 后端，worker runtime 与 toolbox 指纹均未变化；增量回归首次经过新的 Flyway 包内门禁时，旧夹具因没有构造外置 `backend/lib/test-agent-persistence` JAR 而失败。

### What

- 复用现有多后台部署测试的主 migration 夹具，在增量组件回归中构造标准 persistence JAR；不放宽打包门禁，不新增部署路径，也不修改业务代码。
- 使用既有组件状态生成 `worker runtime=reuse`、`toolbox=reuse` 的增量内外层包，未携带 programs、worker 镜像或 toolbox 镜像大制品。

### How

- `AppSourceApplicationServiceTest` 27 项、增量组件回归、自动节点部署回归、最终 ZIP/SHA/嵌套 SHA、组件清单、缺省大制品和包内 Flyway SHA 校验通过。
- 使用 JDK 25、未修改的 `.env.test` 与 `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端与登录 CORS 返回 200，manager 探测恢复为稳定健康。

### Result

- 增量包仅替换 Java 后端及标准小型发布内容；现场复用并前后诊断现有 manager/worker 与 toolbox，不重载镜像、不重建或主动重启这些容器。目标机组件指纹缺失、不一致或健康失败时会在替换 Java 前停止，必须改用全量包。
- 未新增或改写 Flyway migration，未变更生产前端、HTTP API、RunEvent/SSE、数据库结构、性能、安全、generated SDK、OpenCode 源码或环境配置。

### 2026-07-30 - 为企业节点重建全量组件基线包

### Why

- `.4` 使用增量包时在正式替换 Java 前被 toolbox 指纹门禁拒绝，证明现场尚未建立与当前组件指纹一致的可信全量部署基线；不能手写状态文件或强制跳过。

### What

- 基于当前干净 HEAD 强制生成 `worker runtime=included`、`toolbox=included` 的完整企业发布，重新携带 programs、manager/OpenCode/Codex、worker linux/amd64 镜像、IT-Tools、OmniTools 和固定节点包。
- 继续复用同一套 `package-release.sh`、固定外层封装和节点部署入口，没有为本次现场状态新增临时参数或平行部署脚本。

### How

- Java、生产前端、manager linux/amd64、worker 镜像和两个 toolbox 镜像构建通过；Codex 白盒 MCP 4 项契约通过，Mac ARM 环境仅按既有规则跳过原生 amd64 sandbox。
- 最终发布通过 Flyway persistence JAR 字节门禁、内外层 SHA 一致性、双后台节点、固定外层包、首次安装和已有 systemd 升级模拟。

### Result

- 新全量包可在 `.4`、`.114` 重新部署并写入 worker/toolbox 组件指纹，之后相同指纹版本才能安全使用增量包；企业实际部署仍需按 `.4 → .114 → .2` 顺序完成并验证。
- 本次未修改业务代码、生产部署逻辑、API、事件、数据库结构、Flyway SQL、安全配置、generated SDK、OpenCode 源码或 `.env*`。

### 2026-07-30 - 基于全量组件基线重建业务增量包

### Why

- 当前 `main` 在上次全量基线后新增 Java、前端和 HTTP API 业务变更，但 worker runtime 与 toolbox 构建输入没有变化；用户要求只交付需要更新的内容，避免再次传输约 1GB 全量组件。

### What

- 继续使用标准 `package-release.sh` 自动组件计划，生成 `worker runtime=reuse`、`toolbox=reuse` 的业务增量发布，只携带 Java、外置依赖、前端和部署脚本。
- 重新使用固定三节点配置包封装 `test-agent-two-backend-complete.zip`；没有手改 shell、强制跳过指纹门禁或把历史大组件重新塞入增量 ZIP。

### How

- JDK 25 后端封包、前端 typecheck/生产构建、Flyway persistence JAR 字节门禁、增量组件清单、自动节点部署、双后台节点、固定外层包和最终 ZIP validate-only 均通过。
- 通用空目录首次安装模拟因增量包缺少现场 `docker.env` 和组件基线而按设计失败；增量专用回归确认只有安装状态中的 worker/toolbox 指纹与清单完全一致且组件健康时才允许继续。

### Result

- 本包不会加载或重启未变化的 worker/toolbox 大组件；部署会替换并重启 Java，前端节点需要同步更新。
- 只有 `.4/.114` 已成功部署上一批全量基线并保留匹配的 `/data/testagent/config/release-component-state.env` 时，本增量包才不会再出现组件指纹错误；状态缺失或不一致必须停止并重新部署全量包，不能伪造状态。
- 本次打包未新增代码、API、事件、数据库结构、Flyway SQL、安全配置、generated SDK、OpenCode 源码或 `.env*` 修改。

### 2026-07-30 - 确认现场缺少 toolbox 全量基线指纹

### Why

- `.4` 新回传日志显示 worker 指纹完全匹配，但 toolbox 指纹查询无输出；随后纯增量包再次在替换 Java 前被 toolbox 门禁拒绝。

### What

- 对照最终 ZIP、现场输出和既有部署入口，确认外层传输 SHA、内层 Flyway 字节和 toolbox 容器健康均正常；问题不是 ZIP 损坏，而是安装状态未记录 toolbox 全量部署成功。
- 未修改部署脚本；复用现有 `--component-state-file` 参数按现场实际状态重新计算组件计划。

### How

- 成功的全量 `deploy-backend-node.sh` 会在 worker 基线之后部署、诊断 toolbox 并原子合并写入 toolbox 指纹；现场只存在 worker 指纹，证明上一批全量流程没有完成该步骤。
- 以现场状态执行 `--component-plan-only`，结果为 `worker runtime=reuse`、`toolbox=included`，说明下一份正确交付应是业务 Java/前端加 toolbox，而不是纯增量或再次携带 worker 的全量包。

### Result

- 当前纯增量包不能继续部署；本次失败发生在替换 Java 前，没有改变 `.4` 的 Java、worker 或 toolbox。
- 当前固定部署日志会被下一次入口的 `tee` 覆盖，因此仅凭现有文件不能区分上一批全量包是未执行还是中途失败；后续必须以两项安装指纹均存在作为增量发布硬前提。
- 本次仅完成诊断，未重打交付包，未修改业务代码、API、事件、数据库/Flyway、环境配置或部署逻辑。

### 2026-07-30 - 按现场状态生成 toolbox 定向修复包

### Why

- `.4` 已有匹配的 worker runtime 指纹但缺少 toolbox 指纹；纯增量包不能继续，全量包又会重复携带无需更新的 worker/programs。

### What

- 复用标准 `--component-state-file` 参数表达现场已安装状态，生成 `worker runtime=reuse`、`toolbox=included` 的定向发布；同时携带当前 Java、外置依赖、前端、IT-Tools、OmniTools、toolbox 修改源码和 193 项目录。
- 使用既有三节点配置包重新封装固定名外层 ZIP，没有修改或新增部署脚本，也没有伪造企业服务器状态文件。

### How

- JDK 25 后端封包、前端 typecheck/生产构建、两套 `linux/amd64` toolbox 镜像构建和 tar 校验通过；本机运行容器使用同一镜像 ID且均为 healthy，两个健康端点和深链返回 200。
- 增量组件、自动节点、固定外层、双后台节点、Flyway persistence JAR 字节门禁和最终 ZIP validate-only 均通过；清单确认包含全部 toolbox 制品且不包含 programs/worker 镜像。

### Result

- 新包会在 `.4/.114` 更新 Java、部署并诊断 toolbox、写入缺失的 toolbox 指纹，同时复用且不重启现有 worker runtime；`.2` 更新前端。
- 企业实际部署仍需按 `.4 → .114 → .2` 顺序执行；`.4` 成功后必须确认两项指纹同时存在，再继续下一台。
- 本次未修改业务代码、API、事件、数据库结构、Flyway SQL、安全配置、generated SDK、OpenCode 源码或 `.env*`。

### 2026-07-30 - 固化测试工作库目录选择规则到用户手册

### Why

- 用户根据目录树截图排查工作空间无法选择问题，确认当前应用为 `F-APIP`，但仓库根目录使用了 `F-APIP.SUPPORT`，且候选路径存在三级目录。

### What

- 更新用户手册的设置、首次使用、工作区和 FAQ 章节，明确测试工作库只能选择 `应用名称/一级子目录`。
- 补充应用名称大小写、连字符和点号必须完全匹配的规则，并加入 `F-APIP/f-apip-support`、`F-APIP.SUPPORT/workspace`、`F-APIP/f-apip-support/workspace` 对照示例。
- 增加目录可展开但节点不可点击时的排查项，并同步说明测试工作库分支格式要求。

### How

- 依据前端目录节点选择条件和后端工作空间创建校验，采用最小范围 Markdown 修改；未修改业务代码、API、数据库、环境配置或生成产物。
- 执行 `git diff --check`。
- 执行 `corepack pnpm --filter @test-agent/user-manual build`，VitePress 构建成功。

### Result

- 手册已能直接解释本次目录命名问题及正确目录示例，构建产物生成流程通过。

## 2026-07-30 - 测试资产质量评估与变更响应度课题口径

### Why

- 新员工一周实战课题原仅聚焦 AI 案例可用性，需要纳入生产代码变更与测试资产是否同步响应的质量视角。

### What

- 将课题定位调整为“面向开发变更的测试资产质量评估与优化”。
- 明确“代码已变、关联资产未新增/修改/人工确认”时生成关注项，不自动判定为资产缺陷。
- 最终交付仍为现有 `agent-web` 内的资产评估与优化页、质量看板，以及评估/关联/统计 API、数据模型和变更响应演示链路；不新建 Agent。

### How

- 一周 MVP 复用源码快照的 `generation/targetCommit`、代码证据和已有或导入的变更文件清单，不在运行快照恢复 `.git`，不开发完整版本 Diff 引擎。
- 风险由测试人员处置为无需调整、更新已有、补充或重新生成，并留存变更、资产版本和处置证据。

### Result

- 10 页开题 PPT 已按新口径重命名并重新生成，保留一周实战范围和两个网页的交付形态；未修改实际 API、数据库、事件或业务代码。

## 2026-07-30 - 测试资产课题收敛为 Agent 与 Skills

### Why

- 用户取消质量看板、网页和配套服务建设，要求一周实战聚焦能真实运行的 Agent 与 Skills。

### What

- 最终交付收敛为 1 个 `Test Asset Quality Optimizer` 主 Agent，以及变更影响分析、资产变更关联、测试资产质量评估、测试资产优化 4 个 Skill。
- 产物收敛为质量评估报告、优化后测试案例和处理记录，全部写入现有测试工作区。

### How

- 复用平台已有的 Agent/Skill 发现、对话运行、文件编辑与版本、Hub 发布和引用能力，不新建页面、专用 API 或数据模型。
- “可用”按平台可发现与调用、真实模型可运行、结论有代码证据、优化资产可落盘复核、30 组样例可回归验收。

### Result

- 开题 PPT 由 10 页压缩为 9 页，已删除看板交付并调整为 Agent/Skills 运行、产物和真实样例验收主线；未修改实际 Agent/Skill 配置、API、数据库、事件或业务代码。

## 2026-07-30 - 修复应用 Agent 越权可见与模式目录滞留

### Why

- 应用 Agent 配置为 `primary` 后，非应用成员仍可能借旧 workspaceId 读取并选择该应用的运行态 Agent；个人拉取把 Agent 从 `primary` 改为 `subagent` 后，后台 dispose 虽成功，页面仍保留拉取前的 Agent 目录缓存，导致底部选择器和 `@` 候选同时出现。

### What

- Workspace 级 OpenCode 运行态目标解析在认证用户场景复用 `ConversationWorkspaceAccessAuthorizer`，先校验实时应用成员关系或个人工作区 owner，再访问用户进程和应用 `.opencode` 目录。
- 工作台跟踪个人拉取返回的 `runtimeReloadId`；配置消息门禁恢复后统一刷新 Agent 与 Command 目录，并覆盖后台任务在首次 5 秒轮询前已完成的窗口。
- 同步 runtime、agent-web、用户手册、HTTP API 与模块边界说明，补充后端越权拒绝和前端目录刷新回归测试。

### How

- JDK 25 下 `test-agent-opencode-runtime -am` 模块回归 756 项通过；前端相关 Vitest 3 files / 113 tests、全 workspace typecheck 和生产 build 通过。
- 使用未修改的 `.env.test` 与 `test` profile 完整重启 backend、opencode-manager、frontend；后端 readiness 为 `UP`，前端 `http://127.0.0.1:3000` 返回 HTTP 200。

### Result

- 非应用成员不能再读取或选择应用 Agent；应用成员拉取 `primary -> subagent` 后，后台重载完成即从底部主 Agent 选择器移除，并保留在 `@` 子 Agent 候选。
- 未新增或变更 HTTP wire、RunEvent、数据库/Flyway、性能逻辑、环境配置、generated SDK 或 OpenCode 源码；现有错误格式与 static-token 本地兼容链路保持不变。

## 2026-07-30 - 恢复官方 Codex MCP 原生接口

### Why

- 用户确认此前的固定 workspace、安全工具门面和参数裁剪并非官方 Codex MCP 要求，要求全部恢复为官方原生行为；同时保留企业 DeepSeek 默认路由和夜间任务永不询问权限的明确需求。

### What

- 删除自定义 MCP 门面及其契约测试，启动器改为生成官方 `config.toml` 后直接执行 Codex `0.145.0` 的 `mcp-server --strict-config`；恢复官方 `codex`、`codex-reply` 工具名及 cwd、模型、配置、sandbox、审批和指令参数。
- 管理员级 requirements 仅保留 `allowed_approval_policies = ["never"]`；公共 MCP 默认使用 `deepseek-prod`、`DeepSeek-V4-Flash-W8A8` 和 `262144` 上下文，白盒 Agent 显式请求官方 `approval-policy=never`、默认 `sandbox=read-only`。
- 同步 worker 离线构建、镜像探针、Linux 宿主检查、公共 Agent/JSONC 整体包和部署/安全/OpenCode 文档，明确官方 MCP 不再提供固定 cwd、workspace 外拒读、工具级断网、threadId 白名单或日志重写保证。

### How

- 对照 Codex `0.145.0` 官方源码和实际 `tools/list` 契约；用本地伪 Responses 服务验证企业请求地址、Bearer、供应商/ucid header、模型、流式正文、threadId 与续写。
- 最终 launcher 覆盖到已验证 worker 镜像后，`tools/verify-codex-whitebox-worker-image.sh test-agent-opencode-worker:codex-native-smoke` 通过；Apple Silicon 按设计跳过 amd64 nested namespace，只在企业 Linux/amd64 宿主执行原生 read-only E2E。
- shell/Node/JSONC/diff/ZIP 完整性和 SHA-256 检查通过。完整干净 worker 构建已尝试，但 Debian 镜像源 TLS、连接和软件包下载超时导致未完成，未发现 Codex 构建错误。

### Result

- MCP 协议恢复为官方原生服务，企业层只保留离线固定版本、DeepSeek Responses 配置和 approval `never`；公共 Agent 名称仍不含 Codex。
- 未修改 OpenCode 源码快照、HTTP API、RunEvent、数据库/Flyway、generated SDK 或环境配置。发布前仍需在目标 Linux 4.19 / Docker 18.09.7 / x86_64 节点完成宿主探针，并在网络稳定后重跑完整离线 worker 构建。

## 2026-07-31 - 诊断企业附件报错与交互回复提前终态

### Why

- 企业原始输出中，一次携带 Java、Excel 和 Markdown 附件的 Slash Command 在启动后约 0.6 秒以通用“TestAgent 服务响应异常”失败；另一次确认生成测试报文的 Run 在没有产出文件时显示执行不完。

### What

- 附件失败为平台适配缺陷：原生工作区路径附件被转换成含 `type/path/contextType`、但缺少必填 `text` 的 OpenCode `FileSource`；OpenCode 1.18.4 同时拒绝额外的 `contextType`。该非法 `source` 被直接写入 `/session/{sessionID}/command` 的 file part，符合远端请求在消息生成前立即失败的日志时序。Excel 按设计降级为工作区工具路径，不是本次直接故障点。
- “执行不完”的主因也是平台缺陷：`interaction_reply_reconcile` 只凭最新 assistant 的 `finish=stop` 就把当前 active Run 写成成功，没有等待 root session idle，也没有排除工具调用后的中间 assistant 轮次。现场事件显示 `run.succeeded` 后约 119ms，同一 Run 又收到 root `busy` 并创建下一条 assistant 消息，且全部 `session.diff` 为空，任务实际未完成。
- 前端进一步放大了第二个现象：终态后的 `session.status=busy` 会覆盖 reducer 状态，`isRuntimeBusy` 又优先采用 chat busy，因此已收到 `SUCCEEDED` 仍可能继续显示运行中。

### How

- 对照两份企业原始输出的 RunEvent 时间线、请求 PromptPart、OpenCode 1.18.4 `FilePartInput/FileSource` schema、后端 Run 转换/交互回复补偿逻辑和前端运行态 reducer。
- 定向运行 `RunApplicationServiceTest` 的原生附件与交互回复补偿用例，2 项通过；现有附件用例只断言 URL/mime/filename，交互补偿用例把任意最新 `finish=stop` 直接视为最终消息，未覆盖上述真实时序。
- 定向运行前端 `follow-up-queue` 与 `runtime-reducer` 测试，2 个文件、68 项通过；现有用例明确允许 `runStatus=SUCCEEDED` 与 `chatStatus=RUNNING` 时保持 busy，缺少同一 Run 晚到状态的身份/终态保护。

### Result

- 两个现象均判定为本项目 bug，当前只完成诊断，没有修改业务代码或宣称修复。第一个问题若需对企业现场做最后的 HTTP 状态闭环，应按 trace `trace_ms88ltcbkkwpk3r18ui` 核对后台 `startCommand` 的下游状态，预期为 OpenCode 请求校验类 4xx。
- 本次未变更 HTTP API、RunEvent 契约、数据库/Flyway、关系型 SQL、性能、安全、环境配置、generated SDK 或 OpenCode 只读源码；工作区既有 `file-explorer` 修改保持未暂存、未纳入本次记录提交。

## 2026-07-31 - 修复原生与工具型附件投递契约

### Why

- 原生上传附件被平台组装成缺少必填 `text`、同时携带平台扩展字段的 OpenCode `FileSource`，导致 `/session/{sessionID}/command` 在生成消息前校验失败；修复还必须保持不支持原生读取的附件继续通过工作区工具路径投递。

### What

- 工作区路径或 URL 形式的原生附件继续发送 OpenCode `file` part，但省略可选 `source`；有内联内容时只发送 OpenCode 允许的 `type/path/text`，不再把 `contextType`、`deliveryMode`、行号等平台元数据透传到下游。
- 不支持原生读取的附件继续转成内部文本 part，并在 Slash Command 参数中追加精确工作区路径；内部 `source` 仅用于平台分流和用户消息历史，进入 OpenCode `TextPartInput` 前按既有边界丢弃。
- 为原生路径、内联内容、非原生工具路径、混合 Slash Command 和最终 generated SDK 请求体补充回归测试；同步 runtime/client README、HTTP API 和 OpenCode 升级契约文档。

### How

- JDK 25 下执行 `mvn -f backend/pom.xml -pl test-agent-opencode-client,test-agent-agent-runtime,test-agent-opencode-runtime -am test`，相关 reactor 全部通过，其中 opencode-runtime 756 项、opencode-client 67 项、agent-runtime 8 项通过。
- `tools/verify-ai-docs.sh` 与 `git diff --check` 通过；使用未修改的 `.env.test` 按 `test` profile 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`，backend、manager、frontend 均重启成功。
- 后端 health/readiness 为 `UP`，前端 `http://127.0.0.1:3000` 返回 200，登录 CORS 正常；Manager 已连接，用户 OpenCode 进程恢复为 `HEALTHY`。

### Result

- 原生附件不再生成非法 `FileSource`，非原生附件仍可由工作区工具按精确路径读取；平台历史仍保留原始附件元数据，现有路由和 Slash Command 语义兼容。
- 本次未修改第二个“执行不完”问题的补偿终态或前端 busy 逻辑；该问题仅完成解释，后续需独立修复。
- 未新增或变更 HTTP wire、RunEvent、数据库/Flyway、关系型 SQL、性能、安全、环境配置、generated SDK 或 OpenCode 只读源码；工作区内并行的前端文件浏览/下载改动未暂存、未纳入本次提交。

## 2026-07-31 - 新增工作区下载与统一切换入口

### Why

- 用户需要在测试工作区文件树中直接下载文件或文件夹，并将应用工作区与测试工作区切换收敛到同一个入口。

### What

- 文件树文件/文件夹行增加悬停下载按钮；单文件按原文件名下载，文件夹递归读取后在浏览器生成带北京时间 `yyyyMMdd-HHmmss` 时间戳的 ZIP。
- `AgentWorkbench` 复用现有 workspace / workspace-view 文件 WebSocket 及大文件 `read.chunk` 读取，下载过程中禁用同一节点重复点击，并在切换工作区时废弃旧下载结果。
- `WorkbenchFooter` 将应用工作区级联菜单和测试工作区切换入口合并；源码快照模式也在同一菜单提供返回应用工作区和切换测试工作区。

### How

- 新增无外部依赖的 UTF-8 ZIP 生成器，补充文件夹名、中文文件名、时间戳和单文件 Blob 测试。
- 文件树组件新增 `downloadEntry` 内部 Vue 事件及下载状态透传测试；前端定向测试 4 文件 53 项通过，根前端测试 106 文件 1729 passed / 1 skipped，agent-web typecheck 和生产构建通过。
- 使用 test profile 重启 backend、opencode-manager、frontend；health/readiness 为 `UP`、前端 3000 返回 200，并用真实页面验证文件下载、文件夹 ZIP 解压（12 个文件）及测试工作区入口打开。

### Result

- 前端交互已实现并运行验证；未新增或变更 HTTP API、平台文件 WebSocket/RunEvent wire、数据库/Flyway、关系型 SQL、性能、安全、环境配置、generated SDK 或 OpenCode 源码。
- 下载内容受现有文件 WebSocket UTF-8 文本读取契约约束；二进制文件的原始字节下载仍需后续扩展二进制读取协议，当前未宣称已覆盖该场景。

## 2026-07-31 - 基于最新代码重建企业三节点增量包

### Why

- 本地主线在上一企业包之后新增原生附件投递修复、工作区下载与统一切换入口，需要把当前代码重新交付到企业双后台和前端节点，同时避免重复携带未变化的 worker、toolbox、Python 和公共 Agent。

### What

- 以干净工作树提交 `f325c14c17536b061eae935b2c623bd3aca53992` 为输入重新构建后端、前端、内层标准发布 ZIP 和固定名三节点外层 ZIP；相对上一包没有新增或改写 Flyway migration。
- 组件计划保持 `worker runtime=reuse`，指纹 `aa452daf700adfbabf01f1052f8eb8274daf3c7a0e95cd12152b1e39f1708000`；`toolbox=reuse`，指纹 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`。
- 新内层 `test-agent-internal-release.zip` SHA-256 为 `3359570c5f89bb918edbd13d1903e948410a6dc093d2b92eccf65191e1c68203`；新外层 `test-agent-two-backend-complete.zip` SHA-256 为 `313f378e8266d010ed046be2950a053edb49abfbadf6b3b59f232284d94478c3`，外层嵌入内层与当前内层摘要完全一致。

### How

- 执行标准 `deploy/internal/package-release.sh --output-dir deploy/internal/dist` 和 `package-two-backend-complete.sh`；后端企业 JAR、前端 typecheck/生产构建、发布 ZIP backend/frontend `--validate-only`、ZIP/TAR 归档卫生、内外层摘要、增量组件、三节点结构和自动部署入口回归全部通过。
- 包内 `test-agent-persistence` 的正式工具盒子 migration SHA-256 为固定值 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`。
- JDK 25 后端相关 reactor 全部通过，其中 opencode-runtime 756 项、opencode-client 67 项、agent-runtime 8 项；本次前端目标 4 文件 53 项通过。前端全量同时执行为 103 files / 1725 passed / 1 skipped，但 editor/Mermaid/FigmaChatPanel 有 4 项既有 DOM/超时波动，未影响目标测试、typecheck 或生产构建。

### Result

- 当前可交付外层固定名 ZIP 及 SHA 文件已生成并同步到 `deploy/internal/dist/0731/`；本次只需滚动更新 `.4`、`.114` 两台 Java 后端和 `.2` 前端，worker/toolbox/Python/公共 Agent 不需要随本包重装。
- 未修改业务代码、API、RunEvent、数据库结构、Flyway SQL、关系型 SQL、环境配置、generated SDK 或 OpenCode 源码；企业现场仍需按 `.4 → .114 → .2` 执行，首台失败立即停止。

## 2026-07-31 - 收口工作区下载完整性、二进制与重名风险

### Why

- 初版文件夹下载在 `workspace.view.list` 截断或局部引用告警时仍可能生成不完整 ZIP；下载继续使用 UTF-8 文本读取，无法覆盖图片、Office 等二进制文件；组合视图同展示路径冲突时还可能产生重复 ZIP 条目。

### What

- 平台文件 WebSocket 新增 `workspace.read.binary.chunk` 与 `workspace.view.read.binary.chunk`，后端按约 512 KiB 返回 Base64 原始字节；后续分段回传首段大小/修改时间，文件变化返回 `DOWNLOAD_CHANGED`。每段继续复核 ticket、成员、工作区安全路径或引用 locator，并拒绝平台保留索引。
- 前端单文件和目录 ZIP 都改用原始字节分段；目录每层统一走 `workspace.view.list`，任一 `truncated=true` 或 warning 立即中止。组合冲突或逻辑路径重复时，归档统一改为 `workspace/**` 与 `references/<alias>/**` 来源分区，并再次校验最终路径唯一。
- 同步 workspace-management、API、backend-api、shared-types、agent-web 及 HTTP/WebSocket/模块文档；新协议为 additive 扩展，新前端必须在所有目标 Java 节点升级后再启用。

### How

- JDK 25 下后端三类定向测试通过，覆盖任意二进制字节、文件变化、保留索引、引用 locator 和 WebSocket 每条 RPC 重新鉴权；前端下载/backend-api 两文件 100 项通过，agent-web 类型检查和 development build 通过。
- 使用未修改的 `.env.test` 与 `test` profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端和登录 CORS 正常，用户 OpenCode 进程最终持续 `HEALTHY`。
- 真实 Chromium 验证统一按钮同时展示应用工作空间和“切换测试工作区”，版本子菜单可展开；实际下载 `README.md` 与 `spec-20260731-110557.zip`，ZIP 21 个文件完整性通过，Python 按 `0x0800` UTF-8 标志解析中文文件名正确。

### Result

- 已关闭目录静默不完整、二进制转码失败和组合来源重名覆盖三项风险；未新增 HTTP 文件代理、RunEvent、数据库/Flyway、关系型 SQL、环境配置、generated SDK 或 OpenCode 源码修改。
- 仍保留后续可单独处理的风险：浏览器会在内存中汇总整个 ZIP 且使用 ZIP32、切换工作区只废弃结果而不取消在途请求、悬停下载按钮的键盘可达性不足、空目录不会写入 ZIP。

## 2026-07-31 - 修复交互回复提前终态与迟到 busy 竞态

### Why

- 企业现场在 question 回复后出现中间 assistant `finish=stop`，平台补偿逻辑随即把 Run 写成成功；约 119ms 后同一 root session 又进入 busy 并继续生成消息，导致任务未真正结束却提前终态，前端又被迟到的 busy/retry 覆盖为运行中。

### What

- 后端终态补偿改为绑定最初的精确 runId，并复用既有 dispatch user 锚点、父子轮次筛选和有界分页；只有该轮最新 assistant 为 `finish=stop`、精确 Run 仍为 RUNNING，且 OpenCode `/session/status` 已不再包含 root session 时才允许成功收敛，状态缺失、格式异常、轮次冲突或 root busy/retry 均失败关闭。
- Redis summary 路径按精确 manifest 条件接管 owner lease，以 fenced transient/terminal append 收敛终态；legacy 路径改为按 runId CAS，不再通过 Session 的“最新 active Run”误完成后续新 Run。
- 前端 reducer 以 runId 保存的终态为不可逆事实：同一 Run 终态后的迟到 busy/retry 被忽略，不同 Run 的新 busy 仍正常生效。
- 同步 runtime、agent-chat、前端总 README、HTTP/RunEvent 与 OpenCode 1.18.4 升级契约文档；没有修改 wire 格式。

### How

- TDD 先在旧实现稳定复现后端 3 项和前端 2 项竞态失败，再补充 root busy、同 Session 新 Run、同 Run 迟到 busy/retry 及新 Run busy 回归。
- JDK 25 下执行 `mvn -pl test-agent-opencode-runtime -am test`，runtime 模块 758 项及依赖 reactor 全部通过；前端全量 106 个测试文件为 1737 passed / 1 skipped，agent-chat typecheck 和生产 build 通过；`tools/verify-ai-docs.sh`、`git diff --check` 通过。
- 使用未修改的 `.env.test` 按 test profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端 3000 返回 200，登录 CORS 正常，manager 稳定连接且用户 OpenCode 进程持续 `HEALTHY`。

### Result

- 中间 `finish=stop` 不再越过 root busy 提前结束 Run，旧回复轮询也不会误完成同 Session 的新 Run；前端不会再把同一 Run 的已知终态翻回运行中。
- 本次没有新增或变更 HTTP/RunEvent wire、数据库/Flyway、关系型 SQL、鉴权、安全策略、环境配置、generated SDK 或 OpenCode 只读源码；仅在低频终态补偿探测中增加一次受控 `/session/status` 查询。

## 2026-07-31 - 基于本地最新代码重建企业三节点包

### Why

- 用户明确要求不再只按远程主线，而是把本地已经完成并提交的工作区二进制下载和交互回复终态竞态修复一起打入企业包。

### What

- 以干净本地主线提交 `df725d514307f2a46a824b827d25741874a8ed0d` 为输入，重新构建后端、前端、内层标准发布 ZIP 和固定名三节点外层 ZIP。
- 本次没有 Flyway、worker runtime、toolbox、Python 或公共 Agent 变更；组件清单保持 worker `reuse`，指纹 `aa452daf700adfbabf01f1052f8eb8274daf3c7a0e95cd12152b1e39f1708000`，toolbox `reuse`，指纹 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`。
- 新内层 `test-agent-internal-release.zip` SHA-256 为 `86960b3a04f2cb1546dc9a04cd8e2edc029d3889e7f347f4e7c4d0385e65c913`；新外层 `test-agent-two-backend-complete.zip` SHA-256 为 `49bea24f87a48c0526ce605cfcecfa6b07dd0c908492c33c64faf07ec043cd24`，外层嵌入内层与当前内层摘要完全一致。

### How

- 执行标准 `package-release.sh` 与 `package-two-backend-complete.sh`；后端企业 JAR、前端 typecheck/生产构建、内外层 SHA、ZIP/TAR 归档卫生、backend/frontend `--validate-only`、固定名三节点结构和 Flyway persistence JAR 门禁全部通过。
- 包内正式工具盒子 migration SHA-256 仍为 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`，相对上一企业包没有新增或改写 migration。
- JDK 25 定向后端测试共 161 项通过：workspace 文件/视图 57、Run 终态 73、文件 WebSocket 31；前端下载、backend-api 和 runtime reducer 三文件 165 项通过。`tools/verify-ai-docs.sh` 与 `git diff --check` 通过。

### Result

- 固定名外层 ZIP/SHA 已同步到 `deploy/internal/dist/0731/`；本包要求 `.4`、`.114` 两台 Java 全部升级后再升级 `.2` 前端，不能只升级其中一台后台，因为新增文件 WebSocket RPC 需要每个目标 Java 都支持。
- worker、manager、toolbox、Python 和公共 Agent 无需随本包重新部署或重启；未修改数据库结构、Flyway SQL、关系型 SQL、环境配置、generated SDK 或 OpenCode 源码。

## 2026-07-31 - 纠正应用代码库与测试工作空间切换入口

### Why

- 上一版误把 `SUPER_ADMIN` 服务器工作空间能力收进应用工作空间菜单，并将其标成“切换测试工作区”；真正需要合并的是“应用代码库”和当前应用的“测试工作空间”，服务器读取与 Terminal 必须保持独立。

### What

- `WorkbenchFooter` 的统一按钮菜单改为展示“应用代码库”和“测试工作空间”：托管模式继续展示测试工作空间/版本，并从同一菜单打开应用代码库；源码快照模式显示代码库当前态并可切回测试工作空间。
- 恢复独立的 `ServerCog`“切换服务器工作空间”按钮，继续只由 `showServerWorkspaceSwitch` 控制；Terminal 入口及实现未修改。源码 E2E 的 19 个旧独立入口点击统一改走新菜单。
- 同步 agent-web README 与包级说明，明确应用级切换、服务器工作空间和 Terminal 的边界。

### How

- `WorkbenchFooter`/源码能力 Vitest 2 文件 26 项通过，agent-web typecheck 与 development build 通过；应用源码 Chromium 相关 16 个场景全部通过，覆盖源码打开、返回测试工作空间、并发 authority、重连和 Diff 保存。
- 使用未修改的 `.env.test`、JDK 25、test profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端 3000 与登录 CORS 正常，manager 最终持续 `HEALTHY`。
- 真实 Python Playwright 登录后确认菜单内容为“应用代码库 / 测试工作空间 / wrtest / 本地-测试”；应用代码库弹窗可打开，独立服务器按钮可打开“选择服务器工作空间”，服务器工具和“运行与终端”入口仍可见。

### Result

- 应用代码库与应用测试工作空间现在共用一个按钮；超级管理员服务器工作空间和 Terminal 保持原入口、权限与行为。本次未变更 HTTP/WebSocket/RunEvent、后端、数据库/Flyway、关系型 SQL、安全策略、环境配置、generated SDK 或 OpenCode 源码。

## 2026-07-31 - 应用代码库在统一菜单直列并直接打开

### Why

- 用户进一步明确“应用代码库”不能先进入通用源码选择弹窗，而应在统一应用级菜单中直接列出具体版本库，点击对应版本库后立即打开；测试工作空间现有工作空间行与悬浮版本交互保持不变。

### What

- `WorkbenchFooter` 在菜单展开时刷新当前应用源码状态，将已下载版本库直接列在“应用代码库”分区下；可用项点击后复用既有精确 repository/generation `openAppSource` 链路，不再经过 picker，并高亮当前源码版本库。
- `NOT_DOWNLOADED` 版本库继续隐藏在直接打开列表之外；已下载但当前服务器没有 READY 副本的版本库保留可见并禁用，展示服务端安全原因。“管理”入口继续承接首次下载、更新和其它不能直接打开的流程，避免交互调整丢失源码物化能力。
- `FigmaFileExplorer` 与 `AgentWorkbench` 仅增加现有列表状态和直接打开事件透传；测试工作空间模板、版本悬浮子菜单、独立服务器工作空间按钮及“运行与终端”逻辑未修改。同步 agent-web README 与包级说明。

### How

- `WorkbenchFooter` Vitest 17/17 通过，覆盖菜单加载事件、具体仓库直开、管理入口、未下载隐藏、不可打开禁用和源码当前态；agent-web typecheck 与生产 build 通过。
- 应用源码相关 Chromium Playwright 16/16 通过，其中 mock 后端的真实浏览器菜单点击指定仓库后直接进入源码快照且不打开 picker，其余首次下载、重连、切应用竞态和源码能力场景继续通过。
- 使用未修改的 `.env.test`、JDK 25、test profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端 3000、登录 CORS 和 manager WebSocket 正常，用户 OpenCode 进程最终持续 `HEALTHY`。
- 真实 Python Playwright 确认 F-COSS 菜单按“应用代码库 / 测试工作空间”分区；本机当前没有已下载源码，空态与“管理”正常。“本地-测试”仍可悬浮展开版本子菜单，服务器按钮保持菜单外独立，“运行与终端”入口仍存在。

### Result

- 已下载应用代码库现在直接列在统一菜单下并点击即打开；测试工作空间保持原交互，服务器和 Terminal 边界不变。本次未变更 HTTP/WebSocket/RunEvent、后端、数据库/Flyway、关系型 SQL、安全策略、环境配置、generated SDK 或 OpenCode 源码。

## 2026-07-31 - 未下载应用代码库灰色直达管理页面

### Why

- 用户要求未下载的开发代码库也在统一菜单中显示为灰色可点击项，点击后直接打开该代码库的管理页面；测试工作空间交互不变。

### What

- `WorkbenchFooter` 不再过滤 `NOT_DOWNLOADED` 版本库；未下载项使用灰色样式和独立无障碍标签，加载完成后保持可点击，已下载可用项仍直接打开源码，已下载但不可用项仍禁用。
- 未下载项点击事件经 `FigmaFileExplorer` 透传至 `AgentWorkbench`，复用既有源码管理弹窗与版本库选择逻辑，打开后自动选中对应版本库；标题右侧“管理”入口继续打开全部版本库管理页。
- 测试工作空间行前增加试管图标，用于和代码库行的代码图标区分；工作空间行、悬浮版本菜单、独立服务器工作空间按钮和 Terminal 的交互均未修改。同步 agent-web README 与包级说明。

### How

- `WorkbenchFooter` Vitest 17/17、agent-web typecheck、生产 build 均通过；应用源码 Chromium 主流程 1/1 通过，覆盖灰色未下载项点击后进入并选中对应管理项，随后首次下载流程继续正常。
- 使用已运行的本地 test profile 服务和前端 HMR 做真实 Playwright 验证：F-COSS 的 `springbootDemo` 显示为灰色、非禁用，点击后打开“下载应用源码”管理页并显示“当前配置 · springbootDemo”；`wrtest` 与“本地-测试”两行均显示试管图标，且测试工作空间交互仍可用。

### Result

- 未下载应用代码库现在灰色可点击并直达自身管理页面；已下载代码库直开和测试工作空间交互保持不变。本次未变更 HTTP/WebSocket/RunEvent、后端、数据库/Flyway、关系型 SQL、安全策略、环境配置、generated SDK 或 OpenCode 源码。

## 2026-07-31 - 应用源码保留期上限调整为一周并支持前端直接修改

### Why

- 应用源码快照原来只允许保留 1–72 小时，用户需要最高一周，并希望既能临时用 SQL 调整已有 generation，也能在源码管理前端直接设置。

### What

- 领域与数据库最终上限改为 168 小时、默认仍为 48 小时；新增保留期 PATCH API，按原始 `acceptedAt` 计算总保留时长，并以 expected generation、owner/应用管理员权限和未过期 ACTIVE 状态做门禁。
- 续期事务锁定代码库、slot 和同 generation cleanup 行，CAS 同步更新 snapshot `expires_at/index_sha256` 与全部 `delete_at/next_retry_at`；前端第 1 步显示当前总保留小时数并直接调整，物化页也使用服务端 `maxRetentionHours`。
- 已在本地 test 库执行的 `V20260731115520` 365 天 migration 恢复并冻结原 checksum `1426353675`，新增 `V20260731123600` 把最终约束收紧到一周，未执行 `repair` 或修改历史表；同步 HTTP、事件、数据库、领域、测试和模块文档。

### How

- JDK 25 后端 20 模块全量 `mvn test` 为 BUILD SUCCESS；新增两段 migration 后，H2、PostgreSQL 16、已部署基线兼容与重试恢复定向 35 项通过。前端类型检查和生产 build 通过，保留期/backend-api 定向 21 项通过。
- 前端全量独占重跑 1738 passed / 1 skipped / 1 个任务外 Figma 5 秒超时，失败用例独立重跑通过；首次高负载并发全量运行产生的 16 个分散超时未用于功能结论。
- 参数化 PostgreSQL 临时 SQL 在 PostgreSQL 16 实际执行，验证两台 cleanup 同步及 canonical index SHA 与 JSON SHA-256 完全一致。test profile 三服务重启成功，health/readiness 为 `UP`、前端与登录 CORS 正常、manager 最终 `HEALTHY`；真实 history 为 `20260731115520|1426353675|true`、`20260731123600|104581879|true`。

### Result

- 新物化和当前未过期 generation 都可在 1–168 整小时内设置总保留期，前端、API、数据库约束、索引摘要和清理调度保持一致。新增 API 和 Flyway/MyBatis SQL；未修改 RunEvent wire、安全策略、环境配置、generated SDK 或 OpenCode 源码。

## 2026-07-31 - 基于 main 重建含 Flyway 更新的企业三节点包

### Why

- 用户要求以当前本地 `main` 重新生成企业三节点包，并特别关注两条应用源码保留期 Flyway 在企业存量库升级时的风险。
- 打包期间共享工作目录被另一任务切到 `codex/ui-icbc-shell-theme` 并出现未提交 UI 修改；为避免把并行改动或中间文件误混入交付，最终构建改用固定在 `main@014bbb1af7a8d65acc5536952741956a7f94c371` 的隔离 worktree。

### What

- 重新构建后端、前端、内层标准发布 ZIP 和固定名三节点外层 ZIP；最终内层 SHA-256 为 `e2af88b5a192830ebcf4e1858c86f39e1b0c1df5f771d40219ef6e86d97810e1`，外层 SHA-256 为 `bcb1960f09d8481d3c10ee251439ba6567fce86da752e9c5b6bbf5bf57dff4b7`，外层内嵌内层摘要完全一致。
- 本次 worker runtime/manager 与 toolbox 都是 `reuse`：指纹分别为 `aa452daf700adfbabf01f1052f8eb8274daf3c7a0e95cd12152b1e39f1708000`、`35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`；Python 和公共 Agent 无变化，不随包重部署。
- 包含 `V20260731115520` 和 `V20260731123600` 两条新 migration。源码与最终 persistence JAR 的 SHA-256 分别锁定为 `b88b285257025919ca496d247afcfc60ecc24733373639830473294f6dec1bd2`、`e6c3143c0d301119a3cc71164145ec09c0828b552a34b2238934af7a8a73ff7e`；工具盒子正式 migration 仍为 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`。

### How

- JDK 25 + PostgreSQL 16/Testcontainers 重新执行 Flyway 命名/固定字节、应用源码完整生产 migration、企业基线与四类已知 toolbox 历史升级，共 12 项通过；当前源码记录的 Flyway checksum 为 `20260731115520/1426353675` 和 `20260731123600/104581879`。
- 前端保留期/菜单/backend-api 定向 3 文件 38 项通过；企业后端和前端 `--validate-only`、三节点外层脚本、内外 ZIP 完整性、SHA、归档卫生和包内 migration 字节全部通过。
- 最终固定名外层 ZIP/SHA 已同步到 `deploy/internal/dist/0731/`；隔离 worktree 与敏感节点临时解压目录已删除，未触碰并行 UI 分支的未提交文件。

### Result

- 企业包已构建并在 Mac 侧完成可重复校验，但因尚未取得目标企业 PostgreSQL 的完整 `flyway_schema_history`，部署准入仍待现场只读盘点。任一未知 checksum、失败记录、未知更高版本，或已执行 `20260731115520` 但存在 `expires_at > accepted_at + 168 hours` 的快照，都必须停止首台发布，禁止 `repair`、`outOfOrder` 或手工修改 history。
- 现场必须按 `.4 → .114 → .2`：首台 Java 触发 migration 后复查 history、约束和日志，无异常才继续第二台；两台 Java 必须同批升级，worker/manager/toolbox/Python/公共 Agent 无需重启。
## 2026-07-31 - 集成独立 uitest6 单次 UI 测试执行子智能体

### Why

- 用户需要在当前平台对话中唤起 UI 测试执行子智能体，把测试设计产出的一行 `案例名称 | 测试步骤 | 测试数据 | 预期结果` 交给独立 `uitest6` 平台执行一次；`测试步骤`是唯一操作流程，同时要求 `uitest6` 不进入当前仓库并继续独立运行。

### What

- 当前平台新增受控 `ui_test_execute` Tool、`test-execution-ui` 公共子智能体、同节点 Java 桥接、用户作用域专用 Token 和 `uitest6` integration client；外部服务 Token 只驻留 Java，Tool 只允许四列案例和服务端派生幂等键，提交一次后轮询同一 `executionId`。
- `uitest6` 在独立 GitHub 仓库的 `wr` 分支新增带 Bearer 鉴权、幂等提交、状态查询和 executionId 绑定报告的 additive API，复用原有 BrowserUse session/runner；未改变原 `/api/agent/run`。提交 `4a0bdfbb` 已推送到 `origin/wr`。
- 公共 OpenCode 配置基于最新远端 `master` 扩展现有 Test Execution 编排，新增 UI 子智能体和 Tool；保留原接口执行、脚本、报文和数据库校验规则。提交 `e98de0c` 已推送到公共配置远端 `master`。
- 同步 integration、API、opencode-runtime 模块说明，以及 HTTP、RunEvent、部署、安全和对话验收文档；没有新增数据库/Flyway、关系型 SQL、前端协议或 OpenCode 源码修改。

### How

- JDK 25 定向 Java 测试覆盖外部四列请求、Bearer/trace、错误映射、专用 Token、进程环境注入、Controller 和鉴权过滤，共同命令退出 0；uitest6 契约测试 5 项、Ruff 和 compileall 通过；Bun Tool 冒烟确认一次 POST、一次 GET 后返回 `SUCCEEDED`，OpenCode debug 确认子智能体只可调用 `ui_test_execute`。
- 相关后端全量 reactor 测试运行到无关 `test-agent-xxl-job-integration` 时，Testcontainers MySQL 两次超过 JDBC 就绪窗口并重复重试，人工中止为 exit 130；本次定向测试已独立通过，未把该环境故障计作功能通过。
- 使用未修改的 `.env.test`、JDK 25 和 test profile 完整重启 backend、opencode-manager、frontend；health/readiness 为 `UP`、前端 3000 与登录 CORS 正常，新桥接无专用凭据时返回统一 401，manager 最终持续 `HEALTHY`。

### Result

- 对话可直接 `@test-execution-ui`，或由 Test Execution 按一行一次 Task 派发；四列整体传递，不把案例名称、测试数据或预期结果扩写成额外操作步骤。当前仓库只包含桥接与公共配置模板，没有包含、打包或提交 `uitest6` 源码。
- 真实浏览器端到端执行尚未验证：本地 test 环境未配置可用的独立 `uitest6` 地址、服务 Token、目标站点及其模型/浏览器运行条件；上线前仍需按文档在两端配置同一 `UITEST6_INTEGRATION_TOKEN` 并执行一条真实四列案例验收。

## 2026-07-31 - 补充 UI 执行被测环境门禁并完成百度真实自动化

### Why

- 用户进一步明确 UI 执行输入应为“案例 + 被测系统环境”，环境可由用户直接输入或由父 Agent 从用户指定路径读取；没有环境时必须中断，不能调用 Tool 或使用默认地址。
- 首次百度真实运行中，执行智能体误把内容输入 `#chat-textarea`，BrowserUse Judge 已判失败，但 uitest6 集成层仍按智能体自报结果返回成功，需要消除该假阳性后再复测。

### What

- 当前平台的 Java command、桥接 DTO、外部请求、公共 Tool schema 和 UI 子智能体规约统一增加必填 `testEnvironment`；空白环境在 Tool 之前和 Java 边界均失败，四列案例继续整体结构化传递，只有测试步骤作为操作流程。
- 独立 uitest6 `wr` 分支把 `test_environment` 传入 BrowserUse 任务，并在集成适配层读取 BrowserUse Judge；Judge 明确失败时强制终态失败并返回 failure reason，未启用 Judge 的旧运行保持兼容。环境提交 `28360add`、Judge 修正 `b5bdfc10` 均已推送 `origin/wr`。
- 公共 OpenCode 配置的父编排、UI 子智能体、Tool 和说明同步环境门禁，提交 `9bd9562` 已推送公共配置远端 `master`。
- 为本机用户预览，通过平台 Agent 配置 file-ws route/ticket/RPC 把上述已发布 Agent 和 Tool 精确写入公共个人 worktree；未直接修改运行目录。个人热加载被两条 2026-07-10/11 遗留、等待 QUESTION 的 RUNNING Session 正常阻断，未擅自取消历史 Run。

### How

- 当前平台 JDK 25 定向测试 6 项通过，覆盖环境序列化、空白环境拒绝及“不调用外部 client”；`tools/verify-ai-docs.sh`、`git diff --check` 以及仓内模板和公共远端文件逐字比对通过。
- uitest6 契约测试 6 项、Ruff 和 compileall 通过；用真实 Chrome 访问百度生产环境，明确限定传统搜索框 `input#kw`，写入 `OpenAI` 后两次读取 value，BrowserUse Judge 判定成功。执行 `uiexec_abeaf1aa3243426183f588680d90eafc` 返回 `SUCCEEDED / success=true / errors=[]`，5 步、46.20 秒并生成报告。
- 使用未修改的 `.env.test` 和瞬时 UI 平台环境变量重启当前 backend、manager、frontend；独立 uitest6 在 `127.0.0.1:7788` 启动。未修改 `.env.local`。

### Result

- 缺少被测系统环境时，UI 执行链路会中断且不创建外部自动化；提供环境和一行四列案例时，独立 uitest6 已完成百度搜索框输入的真实正向自动化，Judge 失败也不再可能被集成接口误报为成功。
- 当前用户对话入口的最后一次真实派发尚未完成：公共个人运行态因两条遗留 RUNNING Session 无法 dispose；共享公共运行仓库另有 4 个仅本地、未被远端引用的提交，不能用全局 reset 覆盖。后续需先由用户确认是否取消这两条遗留 Run，并为共享仓库本地提交选择保留方式，再执行热加载/全局 rollout 和对话验收。
- 本次没有新增数据库/Flyway、关系型 SQL、RunEvent/SSE、前端协议或 OpenCode 源码变更；uitest6 源码仍只存在独立仓库，不进入当前项目。

## 2026-07-31 - 收口工行外围主题并同步桌面回归门槛

### Why

- 用户要求以当前项目代码为准完成外围色系收口，保持对话区域和隐藏的退出入口不变；项目没有移动端产品内容，后续验证不应把移动端视口纳入交付门槛。

### What

- 保留现有工行风格外围主题与黑色 Logo，给 `FigmaShell` 主卡片补充可收缩约束，避免工具盒子把工作区撑出视口；对话 DOM 和样式未改。
- Playwright 改为单桌面 Chromium 项目，修正当前路由、会话、模型、工具盒子、历史、附件、Mermaid、日期选择等真实规格与现行实现的断言/fixture；锁定退出菜单项继续隐藏。
- 同步前端规范、应用/包 README、Vitest/Playwright 配置和异步测试稳定性说明；未加入移动端内容。

### How

- `corepack pnpm e2e`：131 passed（单桌面 Chromium，1 worker）。
- `corepack pnpm test`：106 个测试文件通过，1742 passed、1 skipped。
- `corepack pnpm typecheck`：全 workspace 通过。
- `corepack pnpm build`：文档与 agent-web 生产构建通过；仅保留既有 Canvas 与大 chunk 非阻断警告。
- 按用户最新要求未执行手动点击或截图。

### Result

- 色系外围、测试和稳定文档已同步，桌面回归及 Vitest、类型检查、生产构建均通过。
- 未涉及 HTTP API、事件/SSE、数据库/Flyway、关系型 SQL、性能、安全、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-01 - UI 执行改为通过启动配置直连独立平台

### Why

- 用户确认 `UITEST6_INTEGRATION_TOKEN` 不是 uitest6 原有能力，要求删除新增的长期 Token 和当前项目 Java 转发，改为 UI 子智能体的 Tool 直接调用独立 UI 平台 IP；平台地址只从启动配置注入，案例输入仍保持“被测系统环境 + 四列案例”，缺少被测环境时继续中断。

### What

- 公共 `ui_test_execute` Tool 改读 `UITEST6_BASE_URL`，直接调用 uitest6 的 `/api/integration/v1/ui-executions`，完成 snake_case 请求、终态轮询、结果归一和外部错误 URL/控制字符脱敏；Agent/Tool 不接收或保存平台地址，不再发送 Authorization。
- 当前项目删除 UI Java Controller、integration client/DTO/settings、专用 Token 服务、鉴权白名单、错误码和 Spring 配置；本地启动脚本把 `UITEST6_BASE_URL` 注入 manager，企业 worker 从 `docker.env` 透传，OpenCode 子进程按既有 manager 环境继承机制获得该值。
- 独立 uitest6 `wr` 分支删除新加的 Bearer 鉴权和 Token 配置，保留既有幂等单次执行 API、被测环境门禁和 Judge 失败收敛；同步当前仓库、公共 Agent 仓库和 uitest6 的 README/API/部署/安全/对话验收文档。uitest6 源码仍未进入当前仓库。

### How

- 当前项目相关 Maven package、`ApiTokenWebFilterTest`、`OpencodeProcessStartupServiceTest`、`tools/verify-dev-scripts.sh`、Shell 语法和 diff 检查通过；uitest6 6 项契约测试、Ruff、compileall 通过；公共 Tool Bun 构建及无 Authorization、snake_case 传输、错误 URL 脱敏的 mock 冒烟通过。
- 使用 JDK 25 和瞬时 `UITEST6_BASE_URL=http://127.0.0.1:7788` 完整重启 backend、manager、OpenCode 与 frontend；readiness 为 UP，OpenCode 子进程环境只读核验得到同一 URL。真实 OpenCode 对话由 `test-execution-ui` 调用新 Tool，执行 `uiexec_0bdaac756654423b8dcfec72918a8c27`，证明请求直接到达 uitest6 且无 Java/Token；当前终态为 FAILED，因为 uitest6 默认模型网关返回 502。运行态切换既有备用模型成功，但备用配置缺少模型 API key，执行 `uiexec_af3c50eb5cf24bfd8da25b26bd0029ba` 同样失败。

### Result

- 集成边界已简化为 `UI 子智能体 → Tool → 独立 uitest6 IP`；当前 Java 不再承担 UI 执行协议或凭据。未配置 `UITEST6_BASE_URL` 时 Tool 在创建外部执行前中断；未提供被测系统环境时 Agent/Tool 的原门禁保持不变。
- 本批真实对话直连已验证，但百度正向浏览器结果未在当前批次重现，阻塞点是独立 uitest6 的现有模型运行配置，不是本次直连协议。此前执行 `uiexec_abeaf1aa3243426183f588680d90eafc` 的百度 `SUCCEEDED` 证据仍有效；模型恢复或平台运维修正默认配置后需再做一次正向复测。
- 未修改数据库/Flyway、关系型 SQL、RunEvent/SSE、前端协议、generated SDK 或 OpenCode 源码；没有修改 `.env.local/.env.test`，实际 UI 平台 IP 仍需由部署方写入对应启动 dotenv 或企业 `docker.env`。

## 2026-08-01 - 首页 Logo 收口为初版耳机图形与暗红实色

### Why

- 用户否决了 M 形与高饱和红方案，确认保留最初的耳机/拱形轮廓，并最终选择渐变中上部的暗红实色作为完整图形填充。

### What

- `agent-web` 顶栏和 favicon 改用用户确认轮廓清理后的透明 PNG；图形全填充低饱和暗红 `#7f1e2b`，中文品牌字标保持黑色，英文副标题使用同系深红。
- 复用 `FigmaShell` 既有图片引用位与 shell token，移除旧 SVG 资源；同步应用 README、包说明、前端规范和模块图。

### How

- 从用户提供的初版图形提取透明轮廓并生成高分辨率 PNG 与 favicon，只调整色彩，不改动图形识别结构；真实首页通过 Playwright 读取最新 PNG、28px 尺寸和字标计算颜色。
- 品牌顶栏定向 Vitest 通过；`agent-web` 生产构建通过，构建包含类型检查；backend readiness 为 `UP`，前端 3000 返回 200。整套 `FigmaShell` 测试另有运行态资源面板 600px/520px 宽度断言失败，属于同工作区并行改动，未纳入本次范围。

### Result

- 用户确认采用暗红实色版本；未修改 API、事件/SSE、数据库/Flyway、性能、安全、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-01 - 扩展左下 Hub 为 Agent / Skill / MCP / Tool

### Why

- 用户明确左侧底部 `Boxes` 按钮代表的 Hub 应与顶部资源口径一致，包含 Agent、Skill、MCP、Tool；同时要求展开详情可拉伸和全屏。

### What

- Hub 保留 Agent/Skill 的远端资产发布、引用和更新流程，新增 MCP/Tool 运行态只读页签与详情，直接复用工作台已加载的 MCP status 和完整 `/experimental/tool` 目录，不新增服务端资产类型。
- Hub 详情与顶部运行态资源详情都支持左边缘拖拽、方向键调宽和页面内全屏；顶部摘要把 Tool 独立于 MCP 展示，并保留 Plugin。
- 同步前端 README、应用/包说明、模块图和用户手册，锁定左下入口的四类语义、只读边界和交互方式。

### How

- `agent-web` 类型检查通过；Hub 与顶栏两个定向 Vitest 文件 64/64 通过；`agent-web` 与用户手册生产构建通过。
- 前端根 Vitest 为 105 个测试文件通过、1 个失败（1744 passed / 1 skipped）；唯一失败是同工作区既有 `AppSourceDialog.test.ts` 仍查找已被其它改动移除的“当前源码总保留小时数”输入框，与本次 Hub 文件和行为无关。
- 使用未修改的 `.env.test`、JDK 25 和 test profile 重启本地服务；启动脚本的首次 readiness 等待提前超时，但进程随后就绪，health/readiness 为 `UP`、前端 3000 和登录 CORS 正常、manager 最终 `HEALTHY`。
- 真实 Chromium 登录后关闭首次引导，验证 Hub 四个页签；详情宽度从 640px 调整到 656px，全屏面板覆盖 1440×900 视口。

### Result

- 左下 Hub 的产品含义已统一为 Agent / Skill / MCP / Tool，运行态 MCP/Tool 不会被误包装成可发布资产；详情拉伸和全屏在单测与真实页面均通过。
- 未修改 HTTP API、事件/SSE、数据库/Flyway、关系型 SQL、权限、安全、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-01 - 顶部工作空间菜单纳入应用代码库

### Why

- 用户希望把左下角已有的应用代码库入口同步到顶部“工作空间”菜单，并用图标区分开发代码库和测试工作空间；尚未在设置中拉取的开发代码库需要灰显但可点击进入管理。

### What

- 顶部菜单复用 `AgentWorkbench` 已有的应用代码库列表、刷新、打开、管理和返回托管工作区处理器，按“应用代码库 / 测试工作空间”分组，并分别使用代码与烧瓶图标。
- 已拉取且可打开的代码库直接进入源码工作区；`NOT_DOWNLOADED` 项灰显但点击后进入既有下载/管理弹框；刷新期间和已下载但当前不可用的副本保持禁用。源码模式下顶部版本按钮只读显示“源码快照”。
- 补充 `FigmaShell` 与左下角入口的联合回归测试，并同步 agent-web README 和用户手册工作空间章节。

### How

- `corepack pnpm exec vitest run apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/WorkbenchFooter.test.ts`：74/74 通过。
- `corepack pnpm --filter @test-agent/agent-web typecheck` 与生产 `build` 通过，用户手册预构建同步通过；构建仅有既有大 chunk 非阻断警告。
- 在独立端口启动 Vite，`http://127.0.0.1:3013/` 返回 200 和真实首页 HTML。

### Result

- 顶部与左下角现在共享同一套应用源码/测试工作空间交互，不新增 API、数据库、RunEvent/SSE 或 OpenCode 调用，也未修改对话逻辑、环境配置和 generated SDK。

## 2026-08-01 - UI 平台地址改为实时通用参数并补变量名搜索

### Why

- 用户要求 UI 执行平台地址可由超级管理员在线修改，下一次 UI 自动化调用即时生效且不重启 OpenCode；不增加凭据，由 Nginx/受信任网络控制访问。
- 用户进一步要求产品和配置名不再带 `6`，移除页面上的即时生效/`UNCONFIGURED` 说明文案，并在通用参数页增加“变量名”搜索框。

### What

- 新增 `UITEST_BASE_URL/all` 数据库直读服务和窄字段配置 API；Tool 每次先用既有 `TEST_AGENT_PLATFORM_BASE_URL` 读取 `{configured, baseUrl}`，再直连独立 UI 平台。Java 不代理 UI 请求，两段请求都不增加凭据；公共 Nginx 对配置接口精确返回 404，相邻 Java 路径仍由既有 API Token 过滤器保护。
- 通用参数列表增加忽略大小写的变量名包含过滤；前端增加“变量名”搜索/重置，UI 参数使用独立地址 placeholder，并移除额外运行机制提示。公共 Agent、部署模板、HTTP/事件/数据库/安全/验收文档统一使用不带版本号的 UI 平台口径，报告地址非空时要求 Agent 返回可点击 Markdown 链接。
- 早期种子 migration 已在本地执行，保留其原始字节；新增后续 migration 把参数和审计引用迁移为 `UITEST_BASE_URL`。两条已执行 migration 的 SHA-256 分别锁定为 `aa08c1cedc64bd0b8dd230227f9dcb7a0ef6a33572473d8a14795f5f6b93e6e5`、`0e306671eda36a9bb8881cf3d85b4e87b5373e00770dcd6503693b11d008e45c`。
- 独立 UI 仓库修正当前 browser-use Ollama 适配器的 `host/ollama_options` 参数，并让启动/执行异常的实际原因进入安全长度受限的终态结果；源码仍位于当前项目之外。

### How

- 后端配置/管理/API/migration 定向 Maven 测试通过；前端通用参数组件 12/12、类型检查和生产构建通过；公共 Tool Bun 构建、Nginx 渲染验证、Shell 语法、migration 源码与 persistence JAR 字节对比均通过。独立 UI 本次相关 pytest 8/8、compileall 和 Ruff F 级检查通过；该仓库整文件运行仍有 2 个与本次无关的旧 `task_id/get_info` 断言失败。
- 使用未修改的 `.env.test` 执行 `./restart-dev-services.sh --profile test --env-file .env.test`，backend readiness 为 `UP`、前端 3000 返回 200、OpenCode 监听 4104；真实 PostgreSQL 顺序执行两条 UI 参数 migration。
- 从用户可见 `test-execution-agent` 发起百度四列案例，子 Agent 只创建一次 `uiexec_7123f836f2254600b7d00dac2bc7f267`，环境和四列内容完整到达，后续只轮询同一 ID，报告接口返回 200。链路取得 `FAILED` 真实终态：本地 `qwen3:1.7b` 在已输入 `OpenAI` 后继续漂移，生成无效选择器 `[index>50]` 并被 BrowserUse Judge 判失败；没有自动重试或绕过平台。
- 验收后停止临时 UI 进程并确认 7788 无监听；把本地测试参数恢复为 `UNCONFIGURED`，配置 API 立即返回 `configured=false/baseUrl=null`，前后 OpenCode PID 均为 14817，证明无需重启即可读取新值。

### Result

- 公共 Agent 仓库 `master` 已推送 `8b81dc4`，独立 UI 仓库 `wr` 已推送 `9248294b`；当前仓库不包含独立 UI 项目源码。
- 新链路已完成真实对话、动态配置、单次提交、终态失败原因和可点击报告的端到端验证；本次百度案例本身未通过，原因在独立 UI 平台的小模型执行/判定质量，不能表述为正向案例成功。
- 新增一个内部 HTTP 配置接口、一个列表 query 参数和两条 Flyway migration；不新增 RunEvent/SSE、关系型业务 SQL、generated SDK、OpenCode 源码或凭据。企业发布前仍需对照目标库 `flyway_schema_history` 做完整基线升级验证。

## 2026-08-03 - 修复应用工作空间初始版本失败遗留模板

### Why

- 设置页创建应用工作空间会先保存 `application_workspaces`，再准备 Git 目录、运行态 Workspace 和初始版本；后续失败只记录 operation，历史上会留下“模板存在但没有版本”的全库脏数据。

### What

- 同步和异步入口复用同一创建程序；仅当模板由本次请求新插入、初始版本创建失败且数据库复核仍无版本时补偿删除。既有模板或版本已经落库时保留，避免误删历史配置、重试入口或并发成功结果。
- 新增 PostgreSQL 运维脚本全库审计和受控清理历史孤立模板，默认只读；执行删除必须停后端、备份、确认候选数量，并排除近期运行任务、个人工作区和 Hub 引用。脚本不伪造版本，也不自动删除 operation、Git 目录或运行态 Workspace。
- 同步 workspace 模块 README、HTTP API、数据库和测试设计文档；没有新增 Flyway、数据库结构、DTO、事件或前端协议。

### How

- JDK 25 下 `ManagedWorkspaceApplicationServiceTest` 79/79 通过，覆盖新模板无版本删除、既有模板保留、版本已持久化后失败保留；全库 SQL 在 `.env.test` PostgreSQL 只读模式执行成功且未执行 DELETE。
- 当前工作树的并行 `PublicAgentConfigRolloutMapper.xml` 一度存在未完成 XML，真实启动改用干净 HEAD 加本次服务补丁隔离打包；20 模块构建成功后以 `.env.test`/`test` 恢复三服务，backend health/readiness 为 `UP`、前端和 CORS 正常、manager 最终 `HEALTHY`。

### Result

- 新失败不再产生只有模板没有版本的记录，历史数据可按全库 SQL 在维护窗口审计和清理；F-APIP 同类 `PREPARING_REPOSITORY / 应用工作区目录不存在` 记录属于该补偿范围。
- 未修改 `.env*`、OpenCode 源码或 generated SDK；运维 SQL 是显式人工修复脚本，不是运行时 JDBC SQL 或自动业务数据 migration。

## 2026-08-03 - 增加公共 Agent 卡死发布的强制终止替换能力

### Why

- 公共 Agent 提交中的无效 `description` 会让 OpenCode `/session/status` 持续返回 `ConfigInvalidError`，使旧发布目标永久停在 `RETRY_WAIT`；后续公共提交又被唯一活动 rollout 门禁阻止，无法通过普通排空自行恢复。
- 企业现场已有一个 `DRAINING` 发布在两台服务器各剩两个目标，用户明确要求跳过会话检查并强制终止这些旧进程，再让修正提交接管发布。

### What

- 新增仅 `SUPER_ADMIN` 可用的“强制终止并替换发布”接口和前端操作：用精确旧 rolloutId 做 CAS，把旧任务置为 `SUPERSEDED`、清空旧租约并原子创建新 `DRAINING` 任务；替换原因和双向 rollout 关系持久化审计。
- 新任务先在原覆盖服务器同步远端修正 commit；只有与旧未排空目标的用户、服务器、容器、端口、PID、manager 启动时间完全匹配的新目标才由 MyBatis SQL 派生 `force_stop=true`。这类目标跳过 `/session/status`，复用 `OpencodeProcessStopService` 做 tracked owned-stop；manager 先 TERM，超时后 SIGKILL，并在 health 确认不可达后写 `DISPOSED`。其它目标继续走普通空闲排空。
- 新增 Flyway 迁移、真实 PostgreSQL/MyBatis 集成测试、后端/前端专项测试，并同步 API、事件、数据库、安全、模块图及各相关模块 README；没有新增 RunEvent/SSE、generated SDK 或 OpenCode 源码修改。

### How

- JDK 25 下 API、workspace、runtime、persistence 定向 Maven 测试通过；PostgreSQL 16 Testcontainers 执行完整当前源码 Flyway 链并验证原子替换、门禁和 `force_stop` 映射。前端 backend-api/管理面板 16 项 Vitest、三个包 typecheck、前后端生产构建均通过，`git diff --check` 通过。
- 最终应用 JAR 内新 migration 与源码 SHA-256 均为 `8b3cbad538f856d5daa06d15f118554ecefb2380a249287cdfe291eb71199022`。
- 按 `.env.test`/`test` profile 执行整套重启时，构建成功，但本地存量测试库含已执行且当前分支未解析的 migration `20260802173416`，Flyway 校验失败并在 90 秒 readiness 等待后退出；未使用 `repair`、`outOfOrder` 或手工改历史表绕过。

### Result

- 代码路径已实现并通过专项/构建验证；企业现场四个目标尚未被本机操作，需先部署本提交和 migration，再由超级管理员对旧 rollout 执行一次替换动作才会强制终止。
- 运行验证为部分完成：真实整套服务启动受本地历史 migration 分叉阻塞；企业内外网不通，因此目标库 `flyway_schema_history`、存量基线升级和现场四目标最终收敛仍须在企业部署前后核验。

## 2026-08-03 - 运行管理增加用户进程批量重启

### Why

- 企业公共配置异常曾使多名用户 opencode 进程同时停在不可健康状态，超级管理员只能逐行重启；需要在现有运行管理页提供受控的批量入口，并明确保留逐项结果。

### What

- 用户进程表增加逐行选择和“全选本页可重启进程”，只允许选择后端明确返回 `restartable=true` 且容器、端口完整的进程；健康进程不可选，查询或翻页会清空旧选择。
- 增加二次确认和“批量重启（N）”按钮，串行复用既有 `restartOpencodeRuntimeManagedProcess(containerId, port)`；单项失败不阻断后续项，完成后汇总成功/失败并仅保留失败项勾选。
- 同步 agent-web README/PACKAGE、backend-api README 和 HTTP API 运行管理说明；没有新增后端批量接口、DTO、事件或数据库变更。

### How

- 组件测试覆盖非超级管理员不可见、确认框、健康进程不可选、批量全成功和部分失败继续执行；运行管理组件与 backend-api 组合定向为 2 files / 110 tests 全部通过，agent-web typecheck 和生产 build 通过。
- 按 `.env.test` / `test` profile 执行标准整套重启：后端、manager、前端均完成构建，但后端仍因本地数据库已执行且当前分支未解析的 migration `20260802173416` 触发 Flyway validate 失败；未执行 `repair`、`outOfOrder` 或手工修改历史表。现存前端开发服务 `127.0.0.1:3000` 返回 200，后端 8080 未启动。

### Result

- 超级管理员可以在运行管理页一次选择多个异常/未运行用户进程并逐个重启；每项仍独立经过原有鉴权、跨服务器路由、公共停止/启动和健康检查链路，失败项可直接再次处理。
- 代码、专项测试、类型检查和生产构建已验证；真实点击到 manager 的端到端验证受本地 Flyway 历史分叉阻塞，因此运行验证为部分完成。未修改 `.env*`、OpenCode 源码、generated SDK、RunEvent/SSE 或数据库结构。
## 2026-08-02 - 本地重启被工作流权限与 Flyway 历史阻塞

### Why

- 用户要求基于当前本地代码重启开发环境，需要确认 `.env.test`/`test` profile 下的真实启动状态。

### What

- 未修改源码、migration、`.env.test` 或 `.env.local`。默认重启和兼容三服务重启均使用 JDK 25、`.env.test`。
- 默认启动在工作流 PostgreSQL bootstrap 阶段失败：`bootstrap-workflow.sql:21` 的 `CREATE ROLE` 返回当前连接用户没有 `CREATEROLE` 权限。
- 使用项目已有 `--without-workflow` 继续重启后端、opencode-manager、前端；Maven 后端构建成功，但后端启动时 Flyway 校验失败，提示已解析但数据库未执行 `20260730090000`，数据库已有更晚 migration。

### How

- 执行：`./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`，随后执行同命令追加 `--without-workflow`。
- 按规范未使用 `outOfOrder`、`repair`、忽略 migration 或手工修改 `flyway_schema_history` 掩盖历史分叉；读取 `.tmp/dev-services/workflow/workflow-prepare.log` 和 `.tmp/dev-services/backend.log` 定位原因。
- 脚本失败后停止了遗留的 manager/frontend screen 会话，避免留下后端未启动而前端/manager仍在运行的半启动状态。

### Result

- 当前工作区干净；8080、3000、8090 均无监听，也没有残留 restart/backend/manager/frontend 进程。
- 后续要恢复完整本地环境，需先由具备权限的数据库管理员处理工作流 bootstrap 角色权限，并按目标库 `flyway_schema_history` 基线解决 `20260730090000` 的历史分叉；不可通过临时替代环境文件规避。

## 2026-08-02 - 核对 LobeHub fork 并复验本地重启阻塞

### Why

- 用户询问 `huangzhenren/lobehub` 是否为当前本地重启所需源码，并要求继续基于本地代码恢复开发环境。

### What

- 确认默认 `.env.test` 重启不依赖 LobeHub fork；只有显式 `--with-lobehub` 才需要 sibling `../lobehub-platform`。远程 fork 的 `main` 与 `v2.2.11-platform.5` 均指向锁定提交 `57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c`，但 sibling 目录当前不存在，未擅自克隆。
- 使用现有 `deploy/internal/workflow/bootstrap-workflow.sql`，以本机 Docker PostgreSQL 管理员角色完成 `test_agent_workflow` 本地数据库/最小权限角色 bootstrap；未修改 `.env.test`、`.env.local`、源码或 migration，未向业务账号授予 `CREATEROLE`。

### How

- 用显式 workflow runtime/migration URL 和 `.env.test` 的 Redis `127.0.0.1:16379` 执行 `./tools/workflow-dev-services.sh prepare`，exit 0。
- 按 JDK 25 和项目默认命令执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`；后端 21 模块构建成功，但 readiness 仍因 `V20260730090000` 已解析未执行且数据库已有更晚 migration 而失败。
- 未使用 `outOfOrder`、`repair`、ignore migration、手工改 `flyway_schema_history` 或重命名候选 migration；精确停止 4104 端口残留 opencode，并确认失败后无 backend、manager、workflow、frontend 或 opencode 残留进程。

### Result

- 工作流权限阻塞已解决；当前唯一启动阻塞是平台 PostgreSQL 的 Flyway 历史分叉，LobeHub 源码不是修复路径。
- 若要继续恢复服务，需要保留数据并由集成人确认该候选 migration 是否已进入共享/稳定库，或先明确授权重建本地测试库；在此之前不能诚实宣称整套服务已启动。

## 2026-08-02 - 修复 macOS 工作流 PID 误判并完成隔离库重启验证

### Why

- 继续验证本地完整重启时，工作流 API/Worker 实际已启动，但 macOS `ps` 展示的是 Python.app 启动器路径，helper 仅按 venv `bin/python` 路径匹配，误报 Worker 启动失败。
- 原 `.env.test` 指向的 `testagent` 数据库仍存在 `V20260730090000` 已解析但未执行、后续 migration 已执行的历史分叉；不能用 `outOfOrder`、`repair` 或手改 Flyway 历史掩盖。

### What

- `tools/workflow-dev-services.sh` 新增 Python 符号链接解析、解释器自报基础路径和 framework root 兼容匹配，并补充中文注释。
- `tools/workflow-dev-services-test.sh` 将 fake venv Python 改为真实文件加符号链接，覆盖回归路径；`docs/guides/ai-workflow.md` 补充 macOS 启动校验说明。
- 新建非破坏性的本地数据库 `testagent_restart_20260802` 作为本次验证目标；未修改 `.env.test`、`.env.local`、migration、OpenCode 源码或 generated SDK，原 `testagent` 数据库保留。

### How

- 以 JDK 25、`test` profile、`.env.test` 和一次性进程环境变量覆盖 Druid datasource 指向隔离库，执行完整 `restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`；在 detached screen 中运行以避免当前 Codex exec 回收工作流子进程。
- 后端构建 21 个 Maven module 成功；隔离库 80 条 Flyway 成功记录，包含 `20260730090000`、`20260801104000` 和四张模型网关相关平台表。后端 readiness、工作流 ready、前端 HTTP 200、manager screen 均通过，用户 OpenCode 端口未按默认策略自动启动。
- `tools/verify-dev-scripts.sh` 通过；其中 PowerShell 解析因当前 macOS 无 `pwsh/powershell` 跳过。`git diff --check` 通过。

### Result

- 当前本地代码已在隔离库上完整运行 backend、workflow API/Worker、opencode-manager 和 frontend；原 `.env.test` 默认数据库的 Flyway 历史分叉仍未修复，因此不宣称默认数据库路径已完成。
- LobeHub fork 仍非默认重启依赖，只有显式 `--with-lobehub` 才需要 `../lobehub-platform`；本次未克隆或启动该源码。

## 2026-08-02 - 修复默认库完整重启并启动 Workflow/LobeHub

### Why

- 用户按 JDK 25、`test` profile 和未修改的 `.env.test` 执行标准重启仍报错，并要求检查 Shell、启动独立
  LobeHub fork，同时确认 Workflow 的实际使用方式。
- 真实链路依次暴露四个问题：Workflow 重复要求业务库账号执行管理员 bootstrap、默认平台库缺失较早
  LobeHub migration、仓库临时 Corepack shim 不接受 `pnpm@version` 参数，以及 Workflow 子进程会随启动终端
  退出；登录后又确认 Python 只接受 ISO 时间，无法解析平台 Redis 中 Jackson 写入的 Unix 秒。

### What

- Workflow helper 先用稳定 owner/runtime 密钥验证既有独立数据库角色，只有首次建库才要求
  `CREATEROLE/CREATEDB`；API/Worker 在 macOS 由两个独立 Screen 会话托管，仍用 PID 与完整命令校验安全停止。
- Workflow Token 认证兼容平台 Unix 秒和历史 ISO 两种 `AuthPrincipal.issuedAt/expiresAt`，统一转为 UTC 校验。
- 对已执行 `V20260801093854`、却漏掉 `V20260730090000` 的已知平台库分叉，复用唯一 Flyway 兼容装配，
  隐藏无法顺序执行的旧候选并加载高版本 `V20260802173416` 补偿；未启用 `outOfOrder/repair`，两份 migration
  均增加 SHA-256 锁定测试。
- LobeHub helper 改用兼容临时 shim 的 `corepack pnpm` 并核对 fork `packageManager` 版本；生成 dotenv 在读取/
  写入前拒绝软链接，使用同目录 0600 临时文件原子替换。同步 app/persistence/workflow README、数据库、Workflow
  API 和本地启动文档。

### How

- LobeHub fork 克隆到仓库同级 `/Users/kaka/Desktop/lobehub-platform`，锁定干净提交
  `57ccf8ffa3f2ec982e1622bed408ad24dfe8d22c`（`v2.2.11-platform.5`）；未修改 fork 源码。
- Flyway 命名/SHA 测试 6 项和真实 PostgreSQL 兼容测试 6 项通过；Workflow 非 PostgreSQL 测试 92 项、真实
  PostgreSQL 测试 12 项通过；Workflow/LobeHub helper 与 `tools/verify-dev-scripts.sh` 通过。完整 Python 首轮因
  Testcontainers Ryuk 被 Docker 提前移除统一报 404，随后禁用 Ryuk 分组重跑全部通过。
- 使用显式本地 owner `DEV_888888888` 真实执行 `restart-dev-services.sh --profile test --env-file .env.test
  --with-lobehub`；后端、前端、Workflow、manager、ParadeDB、RustFS 和 LobeHub 就绪。真实平台登录 Token 成功
  访问 Workflow `/me`/`definitions`，LobeHub 一次性票据 43 字符并完成 302 消费及 Session Cookie 签发。

### Result

- 默认平台库已顺序应用兼容 migration，三张模型网关表存在，LobeHub 四个参数初始化为本地 origin、邮箱域、
  owner 并启用。当前 8080、3000、8090、3210 均可用，Workflow API/Worker 在重启命令退出后持续运行。
- 本地仅启动 Workflow 控制面，不启动 macOS Analysis Runner；没有合规外部 Linux Runner 时页面可用，但提交
  代码影响分析会明确返回 Runner 不可用，不会创建本机分析容器。
- 未修改 `.env.test/.env.local`、generated SDK 或 OpenCode 源码；未新增 RunEvent。HTTP 路径不变，Workflow
  认证只增加平台现有 Redis 序列化格式兼容；数据库变更仅为已知历史分叉的隔离高版本补偿。

## 2026-08-02 - 修复 LobeHub 新标签页票据未投递

### Why

- 已登录用户点击工作台“通用问答”后，平台能够签发一次性票据，但新标签页没有继续请求 LobeHub；直接访问
  固定 `/lobehub/launch` 则能完成 SSO，说明故障位于前端空白标签页交接而非账号、后端或 LobeHub 服务。

### What

- `launchLobehubInNewTab` 在同步打开 `about:blank` 后保留该标签自身的 `document`，切断 `opener`，再把隐藏
  票据表单创建在弹窗 document 中并以 `_self` 提交，不再从平台页面依赖命名窗口查找。
- 补充单测锁定表单所属 document 和 `_self` 目标，并同步前端根 README 与 agent-web README。

### How

- 定向 Vitest 3 项、agent-web typecheck/lint、`git diff --check` 通过。
- 使用 Playwright 真实登录 `888888888` 后点击工作台入口，确认浏览器保持平台页并新建第二个页面，票据消费
  返回 302，最终到达 `http://127.0.0.1:3210/onboarding`。

### Result

- LobeHub 新标签页入口已恢复；现有 8080、3000、8090、3210 服务保持运行。
- 未修改 HTTP API、事件、数据库、依赖锁、`.env*`、generated SDK、OpenCode 源码或独立 LobeHub fork。

## 2026-08-02 - 生成开发测试协同知识沉淀方法论汇报页

### Why

- 需要将“开发测试协同的知识沉淀方法论”图稿转为可直接用于领导汇报的 16:9 PowerPoint 单页，并保留测试实际案例的后续补充区域。

### What

- 新增单页 `docs/presentations/开发测试协同知识沉淀方法论.pptx` 与对应视觉参考图 `docs/presentations/assets/开发测试协同知识沉淀方法论.png`。
- 新增可重复执行的 `tools/pptx/build-knowledge-methodology-slide.js`，并在 `docs/presentations/README.md` 记录图稿用途、可编辑范围、参考图与重建命令。

### How

- 使用 PptxGenJS 按 16:9 画布原生生成标题、目录、文件夹图标、树形线条、图例和右侧留白案例区；视觉参考图不嵌入 PPT。左侧为目录、右侧为留白案例区，开发整理资产为蓝色字体，其余资产为黑色。
- 已运行 PPTX 结构校验、内容提取和 macOS Quick Look 缩略图渲染检查。环境缺少 LibreOffice.app，因此未能执行 LibreOffice PDF 渲染，但 Quick Look 的 PPTX 缩略图与源图一致。

### Result

- 生成的 PPTX 为自包含单页，标题、目录、图例、线条和留白区域均可编辑，结构校验通过；未修改 API、事件、数据库、安全、环境配置或业务代码。

## 2026-08-02 - 修复 LobeHub 平台票据跨 origin 被拒绝

### Why

- 用户在真实 Chrome 中从平台点击“通用问答”后，新标签页停留在
  `/api/auth/platform/consume`，页面返回 `INVALID_ORIGIN`；LobeHub 日志确认 Better Auth 以 403 拒绝
  `http://127.0.0.1:3000` 发起的一次性票据表单 POST。

### What

- LobeHub 本地开发 helper 生成运行环境时，将聊天自身 origin 和 `TEST_AGENT_FRONTEND_URL` 对应的平台
  前端 origin 一并写入 `AUTH_TRUSTED_ORIGINS`；保留 Better Auth 配置覆盖默认值时所需的聊天自身来源。
- helper 行为测试锁定两个可信 origin，并同步本地研发流程与 LobeHub 部署文档；未修改独立 LobeHub fork、
  `.env.test/.env.local`、HTTP API、事件、数据库、generated SDK 或 OpenCode 源码。

### How

- `tools/verify-dev-scripts.sh` 与 `git diff --check` 通过。
- 使用 JDK 25、`test` profile、未修改的 `.env.test` 和 `--with-lobehub` 完整重启 backend、Workflow
  API/Worker、opencode-manager、frontend、ParadeDB、RustFS、LobeHub 与 scheduler。
- 在用户现有 Chrome 平台页刷新后点击“通用问答”，真实票据消费返回 302、平台兑换接口返回 200。

### Result

- 新标签页成功到达 `http://127.0.0.1:3210/onboarding`，不再停留在 `INVALID_ORIGIN`；8080、3000、
  8090 和 3210 均已由完整重启脚本拉起。

## 2026-08-02 - 核对 LobeHub 在线模板授权与离线预置方案

### Why

- LobeHub 本地离线模式的 onboarding 页面无法加载推荐 Agent，用户要求切换在线模式，并确认后续企业离线
  交付应如何携带预置模板。

### What

- 确认推荐模板由 LobeHub 前端经 `market.agent.getOnboardingFull` 请求在线 Marketplace；企业离线策略会按
  设计拒绝全部 `market.*` 路由，fork 内当前没有可替代 Marketplace 的完整 Agent 模板目录。
- 当前进程以 `PLATFORM_SSO_ENABLED=0`、`LOBEHUB_ENTERPRISE_OFFLINE=0` 临时切换为独立在线开发模式，未修改
  `.env.test/.env.local`、生成 dotenv 或源码；离线 scheduler 未启动。
- 明确离线预置模板应作为版本化、可校验的完整 Agent 定义和本地静态资源提交到锁定 LobeHub fork，并复用
  现有 Agent 创建服务实现本地安装；构建制品需补充模板来源、版本、SHA-256、许可证和审批清单。

### How

- 检查 onboarding hook、Market tRPC、Marketplace 安装服务、企业离线路由门禁、平台 SSO 配置约束，以及
  `build-lobehub-artifacts.sh`、`package-release.sh` 和离线部署文档的制品契约。
- 运行检查确认 backend readiness 为 `UP`、frontend 为 HTTP 200、LobeHub 独立在线入口为 302 登录跳转；
  已登录 Chrome 页面能够进入 onboarding，但在线 Marketplace 返回 `Unauthorized`。

### Result

- 在线 LobeHub 进程已启动，但推荐模板仍未加载成功：当前没有 Market OIDC token，也没有已注册的
  `MARKET_TRUSTED_CLIENT_ID/SECRET`；下一步需由用户完成 Marketplace 授权或提供受控 trusted-client 配置。
- 平台 SSO 适配当前显式要求企业离线模式，因此本次在线启动是直接访问 LobeHub 的临时运行态；再次执行标准
  `restart-dev-services.sh --with-lobehub` 会恢复离线模式。离线模板方案尚未编码实现，也未改动 API、事件、
  数据库、Flyway、generated SDK 或 OpenCode 源码。

## 2026-08-02 - 修复代码变动影响分析内部请求被代理劫持

### Why

- 已登录页面能够进入 Workflow，但 Python 调 Java 的平台共享能力请求继承了宿主机代理环境，回环地址被代理
  转发并返回 502，导致用户身份、仓库和会话加载失败，页面显示“平台共享能力调用失败”。

### What

- Workflow 到 Java、Worker 到 Runner、Runner 到 Java 的三个固定内部 HTTP 客户端统一关闭 HTTPX 环境代理
  继承，保留现有超时、签名和依赖注入边界；补充默认客户端回归测试。
- 同步 Workflow、Runner、企业离线部署和安全规范，明确内部签名请求必须直达部署配置地址；不修改本地 Runner
  启动策略，macOS 仍只启动控制面，分析执行继续要求合规 Linux Runner。

### How

- Workflow 全量 105 项、Runner 全量 80 项和 `tools/verify-workflow-architecture.sh` 通过；Workflow PostgreSQL
  测试使用 `TESTCONTAINERS_RYUK_DISABLED=true` 避开本机 Docker 提前移除 Ryuk 的既有问题。
- 使用 JDK 25、`test` profile、未修改的 `.env.test` 完整执行
  `restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`；backend、frontend、Workflow
  API/Worker 和 opencode-manager 均正常启动，健康与 readiness 为 UP。
- 在用户现有已登录 Chrome 页面刷新 `/workflow-chat`，确认身份 `DEV_888888888`、两条最近任务和场景标题可见，
  且不再出现共享能力错误；Workflow 日志确认 `/me`、`/repositories`、`/conversations` 均返回 200。

### Result

- “代码变动影响分析”场景加载已恢复，内部 HMAC 和票据请求不再受宿主机 HTTP(S)/SOCKS 代理环境影响。
- 本机 8091 按设计无 Runner 监听，因此未执行真实代码分析任务；这不影响页面与控制面加载，但正式分析仍需
  配置合规 Linux Runner。未修改 API 路径、事件、数据库、依赖锁、`.env*`、generated SDK 或 OpenCode 源码。

## 2026-08-03 - 初始化 Bytebase 测试仓库并修复影响分析结构化入口

### Why

- 用户要求把私有 Gitee `wrui233/bytebase-java` 配置为应用代码库并执行真实页面验证。原页面的新对话没有
  直接展示结构化输入卡，手工发送“开始代码变动影响分析”会先调用尚未初始化的意图模型并返回 500。
- 真实浏览器复测又发现：从失败任务新建对话时，reactive 投影保留旧 `FAILED/runId` 等可选字段，导致新对话
  仍显示失败且输入卡被遮蔽。

### What

- 在本地平台创建 `Bytebase Java / bytebase-java` 应用代码库，记录 ID
  `repo_fe8dba411c2b4a0a8e3af5726e879bb2`，SSH 地址为 `git@gitee.com:wrui233/bytebase-java.git`，类型为
  `APPLICATION_CODE_REPOSITORY`，并关联到 `F-COSS / app_fcoss`；该仓库当前只有 `main`。
- 新建空对话直接展示仓库、分支、模式与智能体输入卡；仅当注册表中只有一个工作流且请求携带
  `structuredInput` 时确定性路由，不再重复调用意图模型，自然语言分类和真实代码分析仍保持模型边界。
- 初始 AG-UI 投影显式清空所有可选运行字段，修复失败任务切换到新对话后的状态残留；补充前后端回归并同步
  Workflow API、模块图、前端规范和相关 README。

### How

- HTTPS `git ls-remote` 因私有仓库认证失败，平台/本机 SSH 成功读取 `main`，远端 HEAD 为
  `e87c82adf3900ae50eec5d0d00bc48edae84018d`。真实 `/repositories/{id}/branches` 返回 200 并展示
  `main（默认）`。
- Workflow 全量 106 项通过；本机 Docker 24.0.2 的 Ryuk 容器会被提前移除，使用
  `TESTCONTAINERS_RYUK_DISABLED=true` 后 PostgreSQL 集成 12 项及全量均通过。`workflow-chat` 14 项、全仓
  TypeScript/Vue typecheck 通过。前端全量另有一项既有 `AppSourceDialog` 保留时长输入断言失败，与本次文件无关。
- 使用 JDK 25 和未修改的 `.env.test` 完整重启；8080 health/readiness、3000、8090 health/ready 与 CORS 预检
  通过。真实 Chrome 提交 Bytebase `main` 后，仓库权限复核成功，结构化消息 POST 200，任务
  `run_043b0442686e509804727c7895b39197` 进入 Worker。

### Result

- 页面入口、仓库初始化、SSH 分支读取、结构化提交及失败任务后新建对话均已验证；当前浏览器保留
  Bytebase Java / `main` 的可操作输入页。
- 真实任务在“冻结提交并准备隔离工作区”按设计失败：本机是 `linux/arm64` Docker 且 8091 无合规
  Linux Runner，不能绕过宿主 `linux/amd64`、`DOCKER-USER/iptables` 等边界。平台内部模型 provider/token/model
  也尚未初始化，因此尚未生成最终影响报告；仓库只有 `main`，后续还需有实际变更分支才有非空 diff。
- 未修改 `.env*`、数据库结构/Flyway、RunEvent 类型、HTTP 路径、generated SDK 或 OpenCode 源码；HTTP 请求
  结构保持兼容，仅补充唯一固定工作流的结构化路由语义。并行 LobeHub 脚本和文档改动未纳入本次范围。

## 2026-08-03 - 接入真实 Community Agent 目录与离线快照

### Why

- LobeHub 是通用工作与生活能力入口，既有测试领域自造模板方向不符合产品定位；用户要求在线展示 Community
  当前已有、可选择的内容，并让企业离线包能够携带经过选择和审计的真实社区 Agent。
- 原在线 onboarding 还要求单独 Marketplace 授权；自动 M2M 接入初版沿用 GET query 交换 `clientSecret`，
  会让凭据进入 Next.js 访问日志，不能作为安全交付。

### What

- 独立 fork 在 `bf73f5f2c1e7f3309ecc1eb874ef58ca587b3a04` 锁定为
  `v2.2.11-platform.7`：在线 onboarding 自动建立短期 Marketplace M2M 会话并读取实时 Community，凭据改由
  tRPC mutation 的 POST body 交换；用户不再执行 Community OAuth。在线安装仍保持 Community fork 语义。
- 离线模式从 fork 内版本化快照读取，当前冻结 13 个通用类别、118 个官方且已验证 Agent 及 118 个本地头像；
  安装直接创建本地 Agent，不访问 Marketplace、创建 Community 组织或上报事件。选择清单默认同步精选目录，
  额外条目使用 Community 真实 identifier，不允许手工编造生成 JSON。
- 平台本地启动增加 `--lobehub-mode online|offline`；在线模式关闭平台 SSO/企业离线网络策略和不兼容的平台入口，
  默认离线行为保持不变。企业构建器新增快照来源、许可证、自包含依赖、头像集合和 SHA-256 校验，并把元数据
  写入 `approved-resources.json` 与 `LICENSES.txt`。

### How

- 快照同步器只调用 Community 公开只读接口，有界重试，只接收官方、已验证、配置完整且没有在线 Plugin /
  Knowledge Base 依赖的 Agent；记录 Community 页面、作者、官方 `lobehub/lobe-chat-agents` 仓库及 MIT 许可证。
- fork 相关 Community 测试 14 项、tRPC 上下文 28 项、Marketplace/MCP 回归 44 项通过，`bun run check --type`
  与定向 ESLint 通过。平台快照防篡改、构建、客户端契约/工具包、定稿、安装、备份、探针、发布和开发脚本测试
  全部通过。
- 使用 JDK 25、未修改的 `.env.test` 和 `--lobehub-mode online` 完整构建后重启一次，并在安全修正后再次重启；
  8080、3000、8090 为 HTTP 200，3210 正常跳转。真实 Community 接口返回 200，浏览器渲染 13 类/118 项，
  “战略顾问”选择后“继续 (1)”启用；未点击继续，未产生 Community fork 写操作。新日志不含 `clientSecret`
  或 GET `registerM2MToken`。

### Result

- 本地在线模式可自动加载并选择 Community 当前真实目录；离线发布可从同一社区内容生成可审计、自包含快照。
  后续新增内容先在 Community 选择 identifier，更新选择清单并重新运行 `bun run community:snapshot`、测试、
  版本提交和企业打包；贡献新社区内容仍走官方 `lobehub/lobe-chat-agents` 仓库审核。
- 本次不修改平台 HTTP API、事件、数据库/Flyway/MyBatis SQL、generated SDK、OpenCode 源码或 `.env*`。
  `.7` 完整企业服务端/客户端介质和 fork 转运 ZIP 尚未构建；既有 `.5` 介质仅为历史证据，不能复用或改名。

## 2026-08-03 - 复原规范驱动测试智能体单页 PPT

### Why

- 用户提供现有汇报页截图，要求保持工行红白风格与三栏结构，并按当前平台重新表达公共 Agent、应用 Agent、
  `spec/docs` 资产和 SDD 能力栈。

### What

- 新增可编辑单页 `智能研发规范驱动测试智能体落地.pptx` 及可重复生成脚本
  `tools/pptx/build-spec-driven-agent-slide.js`。
- 左侧按真实配置边界拆为公共 `opencode/`、应用 `.opencode/` 与 workspace 下直接挂载的 `spec/`、`docs/`；
  删除中间内容目录层和旧知识库目录。
- 右侧能力栈保留 SOP、Skill、Rule、Spec、Template、Docs，并新增独立的 MCP、Tools 层；同步演示文稿 README。

### How

- 复用仓库既有 PptxGenJS 目录树、图标栅格化和原生形状绘制方式，所有主体文字、框线、目录、流程和能力栈
  均可在 PowerPoint 中继续编辑。
- 运行生成脚本、PPTX Office 结构校验、`markitdown` 内容提取和 macOS Quick Look 2000px 实际渲染；首轮
  发现两处文字裁切后调整字号并重新执行全部校验。环境没有 LibreOffice.app，视觉校验使用 Quick Look 完成。

### Result

- 单页内容与用户要求一致，最终结构校验通过、文本完整、渲染无已知截断或重叠。未修改 API、事件、数据库、
  性能、安全、环境配置、generated SDK、OpenCode 源码或业务代码，也未创建分支。

## 2026-08-03 - 修复 Linux Runner 并完成真实代码影响分析闭环

### Why

- Bytebase 与 Spring Boot 应用仓库已经能在页面选择，但 macOS 本机没有满足 `linux/amd64`、Docker Socket、
  `DOCKER-USER/iptables` 隔离要求的 Runner；后续真实任务又依次暴露 Docker API 版本钉死、长 SSH 私钥无法
  直接 RSA 加密、模型请求继承系统代理、4K 上下文截断、模型虚构证据路径和 AgentScope 工具结构化失败。
- Lima 虚拟机重启会清空 iptables，Runner 容器若自动恢复可能先于出站策略；本地 8B 模型长上下文冷启动还会
  超过平台模型网关原有30秒响应头窗口，产生一次无意义重试。

### What

- 初始化独立 Lima/QEMU `linux/amd64` Runner 环境并通过根 Docker 运行隔离容器；Runner 启动改为先验证并
  恢复分析网络、容器固定 `restart=no`，网络校验输出精确缺失规则。Runner镜像取消旧 Docker API 版本钉死。
- checkout 私钥改为 `TAEC1` 混合信封：Java 用 RSA-OAEP-SHA256封装随机AES密钥，Python以AES-256-GCM解密
  任意长度OpenSSH私钥；保留失败关闭和格式/篡改测试。离线打包同时修复 numeric UID chown、Syft tmpfs、
  macOS扩展属性和不依赖在线Dockerfile syntax frontend的问题。
- 代码分析任务在模型前确定性读取冻结提交diff，把有界统计、文件清单和patch摘录纳入提示；输出schema和
  后置校验强制证据使用真实仓库别名与存在路径，非空diff不能返回空证据。模型relay首个Responses请求显式
  要求工具调用，并修复模型网关把工具schema中的`image_url`误判为视觉输入。
- Workflow 服务端AgentScope使用独立模型网关路由、`trust_env=false`流式客户端和显式连接释放；结构化结果
  优先走OpenAI-compatible `response_format + JSON Schema`、温度0并关闭Qwen思考，未实现该能力的供应商才
  回退工具调用。综合提示明确已成功执行的代码智能体，缺失摘要由代码证据确定性回填。
- 平台模型网关对Workflow长上下文请求把响应头冷启动窗口扩为120秒，LobeHub交互调用仍保持30秒；首块和
  相邻块空闲边界不变。同步模型网关、Workflow、Runner、analysis-task、离线部署和HTTP API稳定文档。
- 真实验收发现Java通用API日志仍会记录checkout ticket路径参数和Runner加密私钥信封；日志脱敏现已覆盖
  `ticketId/grantId/grant/encryptedPrivateKey`等Workflow凭据字段，并只保留固定凭据路由形状。

### How

- 本机创建 `qwen3-workflow:8b`（`num_ctx=16384`）并把四个Workflow公开模型ID映射到该本地上游；12个
  CHAT/TOOLS/REASONING探针通过。Lima重启实测证明Runner不会自动启动，启动脚本能在缺失iptables链时恢复
  白名单策略后再启动Runner，健康/ready与`restart=no`均通过。
- 真实 Spring Boot `master..feature_testagent_20260630` 单智能体任务
  `run_417ef9c1d3f9c2ef5782db8f0195a5e8` 最终为 `SUCCEEDED`，报告
  `report_8b1fea140f2b46be81b5b68d579bc6d1` 精确引用4个实际新增文件；独立综合探针再次返回4条证据且不再误称
  “未执行代码分析”。
- 从提交`3d4fe0370e51`构建正式离线介质`V20260803.102851`，外层SHA-256为
  `84e9b4360e09932838965bc80dd5c24ff09229ba06b65fa993194fdbd71888b0`，内层文件逐项校验通过；Lima已加载并
  通过正式部署脚本切换到Runner镜像`d78bfa98a478...`和analysis镜像`2ba792706664...`，Docker API自动协商为
  client 1.41/server 1.55且不再设置`DOCKER_API_VERSION`。
- 正式镜像页面验收任务`run_726dc810c081196e7168f98cadd43f56`与Codex分析器均为`SUCCEEDED`，报告
  `report_d7e374c14e464530b77350de3aed190b`含4条真实路径且未声称“未执行代码分析”。日志脱敏定向48项通过，
  随后再次完整打包Java后端并重启实际服务。
- Workflow非PostgreSQL 98项、PostgreSQL 12项（本机Docker需`TESTCONTAINERS_RYUK_DISABLED=true`）、
  analysis-task 14项、Runner 81项、Java模型网关全模块依赖测试、TAEC1定向测试、Ruff、离线包合同、开发启动
  合同、网络和架构校验全部通过；JDK25后端完整打包并用未修改的`.env.test`重启，8080/3000/8090及独立
  Linux Runner的8091入口均返回HTTP 200。

### Result

- Linux Runner、代码智能体、平台模型网关、AgentScope综合和报告落库已形成真实可运行闭环；Ollama只作为
  当前离线测试环境的平台模型上游，工作流和Runner仍使用短期grant经Java网关访问，没有直连旁路。
- 正式Runner在Lima重启后仍固定`restart=no`，启动程序先验证或恢复出站策略再启动；8080/3000/8090/8091
  最终均返回HTTP 200，API访问日志不再保留Workflow一次性凭据或加密私钥信封。
- 本次未新增HTTP路径或事件类型，未修改数据库/Flyway/MyBatis SQL、generated SDK、OpenCode源码或`.env*`；
  模型网关仅按可信调用来源调整性能窗口，LobeHub行为兼容不变。Lima与模型目录属于本地运行环境状态，不作为
  生产migration；正式交付仍需由离线包在目标企业Linux主机复核硬件容量、真实模型和网络地址。

## 2026-08-03 - 合并 main 并修复 release 迁移分叉启动

### Why

- 用户要求把本地 `main` 合并到企业 release 分支，同时明确后续企业部署暂不部署 LobeHub 和 Workflow。
- release 数据库已经执行公共 Agent 发布迁移 `20260803133000` 时，如果尚未执行 main 的 LobeHub 基础迁移
  `20260730090000` 或旧补偿 `20260802173416`，原补偿版本低于当前 schema 版本，会被 Flyway 拒绝。

### What

- 将本地 `main` 合并到 `codex/release-enterprise-20260801`，冲突文档同时保留公共配置强制重启能力与
  LobeHub/Workflow 的现有说明。
- 复用唯一的 `DatabaseMigrationCompatibilityCustomizer`，新增隔离兼容迁移
  `V20260803141754__backfill_lobehub_model_gateway_after_rollout.sql`：仅对已经执行
  `20260803133000`、但缺少旧补偿的已部署分支选择高版本补偿；已执行旧补偿的数据库继续加载旧 location
  校验，正常/空库继续执行 main migration。没有启用 `outOfOrder`、`repair` 或修改 Flyway 历史。
- 同步 app/persistence README、包说明和数据库部署文档；企业部署决策为不安装、不启动 LobeHub 与
  Workflow。本次仅完成本地验证，没有制作或部署企业介质。

### How

- PostgreSQL 兼容集成测试 7 项、Flyway 命名与字节锁测试 6 项通过；前端定向 8 个测试文件共 150 项通过。
- 使用 JDK 25 和未修改的 `.env.test` 执行 `./restart-dev-services.sh --profile test --env-file .env.test`，
  完整 Maven 21 模块与前端生产构建通过；8080 后端、3000 前端、8090 Workflow 健康检查返回 200，
  3210 LobeHub 未监听。Workflow 仅因标准本地启动流程运行，不代表将纳入企业部署。
- 本地历史保留 `20260802173416` 并新增 `20260803133000`，符合旧补偿分支；新兼容 SQL 源码与运行 JAR 内
  SHA-256 均为 `b73b06fb14f407979646df32a8342603ab957c2f4812a4013ab9635cdfdcce64`。

### Result

- 合并后的 release 分支可在已知 main 分叉和“release rollout 已执行、旧 LobeHub 补偿缺失”两类历史上
  选择合法迁移路径，本地服务已完整启动并验证。
- 企业包和目标服务器部署尚未执行；交付前仍须读取目标库真实 `flyway_schema_history`，覆盖对应基线升级，
  并核对最终企业 persistence JAR 内 migration 字节。部署时继续排除 LobeHub 和 Workflow。

## 2026-08-03 - 运行管理批量选择重启与关闭用户 OpenCode

### Why

- 既有批量重启藏在页面底部用户查询区，只能选择当前查询页中的异常进程；“容器 / 管理进程”标题区域没有
  入口，也没有批量关闭能力，无法直接按 manager 在线事实选择需要处理的用户 OpenCode。

### What

- 在“容器 / 管理进程”标题右侧新增“全选有主进程”“批量重启 OpenCode”“批量关闭 OpenCode”；展开容器后，
  每条有主进程可单独勾选，按钮实时显示已选数量。
- 可选目标严格来自 overview 中 `ownership=BOUND` 且 `containerId + port` 完整的 manager 有主进程；
  `UNBOUND` 无主进程没有复选框，不会被批量操作误处理。
- 批量重启和关闭串行复用既有单进程 restart/stop API、跨 Java 路由及公共停止/启动与 health 确认；二次确认
  后单项失败不阻断后续项，完成时汇总结果并只保留失败项选择。底部用户查询保留单项重启，移除重复批量入口。
- 同步 agent-web README/PACKAGE、backend-api README 和 HTTP API 运行管理交互说明。

### How

- `runtime-management-settings.test.ts` 覆盖按钮位置、逐项选择、全选、跳过无主进程、批量重启、批量关闭、
  单项失败继续执行和失败身份汇总；与 backend-api 测试合计 112 项通过，agent-web TypeScript 检查通过。
- agent-web 用户手册与生产构建通过；使用 JDK 25、未修改的 `.env.test` 执行
  `./restart-dev-services.sh --profile test --env-file .env.test --skip-backend-build --without-workflow`，
  本地后端 readiness 为 `UP`，前端 `http://127.0.0.1:3000` 返回 HTTP 200。

### Result

- 超级管理员现在可以在用户可见的容器进程区域精确选择一个、多个或全部有主用户 OpenCode，再批量重启或
  关闭；未选择时两个批量按钮禁用，无主进程仍保留单项处理边界。
- 本次未新增或修改 HTTP 路径、DTO、RunEvent/SSE、数据库/Flyway/SQL、权限模型、环境配置、generated SDK
  或 OpenCode 源码；仅复用既有高权限单进程控制接口。企业包和企业部署未在本任务中执行。

## 2026-08-03 - 修复 OpenCode 重启初始化被并发健康查询回写打断

### Why

- 现场多次初始化在 `HEALTH_CHECKING` 或 `SAVING_CANDIDATE` 报
  `TestAgent 进程分配已变化，拒绝旧启动结果回写`；trace 证明普通模型/Provider 或运行管理强查询会在公共启动
  程序持有 `STARTING` 候选期间先把同一进程写成 `RUNNING`，导致启动最终 CAS 失败并误执行精确停止补偿。
- 系统管理页相同用户关键字重复查询时 Query Key 不变，可能继续显示旧空结果；清浏览器缓存只会重新触发请求，
  没有修复数据库或进程状态。

### What

- `OpencodeProcessStatusQueryService.query(processId)` 遇到 `STARTING` 时只返回
  `STALE + STARTING/CHECK_SKIPPED`，不调用 manager、不写数据库或 Redis；启动程序自己的只读快照健康确认、
  停止确认和自动恢复路径保持原行为。
- `OpencodeProcessStartupService` 最终 CAS 失败后仅在数据库记录仍是相同 process/user/server/container/port、
  PID、manager 权威 `startedAt`、session/config 和创建时间的 `RUNNING` 实例时幂等收口；任一身份变化仍按真实
  冲突精确补偿，不能吞掉 PID 复用或新生命周期。
- 工作台开始初始化时取消在途模型/Provider 查询，并在 operation 为 `RUNNING` 期间暂停目录恢复；运行管理页
  相同关键字和首页条件再次点击时显式 refetch。同步 runtime、agent-web 和部署排查文档。

### How

- JDK 25 下后端定向 44 项通过；`test-agent-opencode-runtime -am` 全量依赖测试通过，并在最终身份校验加强后
  重新执行定向 44 项通过。新增测试覆盖 STARTING 普通查询零 manager/零写入、同实例提前 RUNNING 幂等收口，
  以及相同 PID 但不同 manager `startedAt` 仍失败关闭。
- 前端两个定向测试文件 114 项、agent-web typecheck 和生产 build 通过；真实 Vite 开发服务在
  `http://127.0.0.1:5173/` 启动并返回 HTTP 200。

### Result

- 普通页面/目录请求不再夺走 STARTING 状态所有权，同一实例的迟到最终确认也不会误杀刚启动的 OpenCode；
  真正的分配或生命周期变化仍保持原有失败关闭和精确补偿。
- 本次未新增或修改 HTTP 路径、DTO、事件、数据库/Flyway/SQL、权限、安全配置、环境文件、generated SDK 或
  OpenCode 源码；前端目录恢复仅在本页面发起的初始化 operation 运行期间暂停。

## 2026-08-03 - 固化深层应用 Tool 离线依赖祖先链接

### Why

- `.114` 节点用户“谢伟 1”的 OpenCode 14117 在加载深层个人 worktree 的
  `.opencode/tools/server-file-upload.ts` 时无法解析 `@opencode-ai/plugin`；随 programs 交付的模块存在，
  但既有启动器只给公共配置和进程当前目录的 `.opencode` 建链接，没有覆盖深层 workspace 的 Node 祖先路径。
- 现场已通过在共享工作区根目录建立四个依赖软链接临时恢复，需要将同一动作固化到正式启动流程。

### What

- OpenCode 官方程序启动器在每次 `serve` 前，把 `@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod`
  非覆盖式链接到进程工作目录的 `node_modules`；该目录是个人 worktree 的共同祖先。并发启动沿用既有
  `EEXIST` 幂等处理，现场已有文件或目录不覆盖。
- 单测新增深层 `personalworktree/.../.opencode/tools` 的真实模块导入探测和祖先现有依赖保留断言；worker
  镜像验收脚本新增共享工作区祖先链接检查。同步企业部署、OpenCode 升级和空报文排障文档，澄清超级管理员
  较少出现是工作区使用路径或历史链接差异，不是角色鉴权差异。

### How

- `node --test tools/test-opencode-official-launcher.mjs` 6 项通过，深层工具探测输出 `IMPORT_OK`；启动器语法、
  worker 验收脚本语法、runtime Git ignore 和 Tool runtime 部署门禁均通过。
- 使用 JDK 25 和未修改的 `.env.test` 执行
  `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`，21 模块后端打包成功；
  backend health/readiness 为 `UP`、前端 3000 返回 200、登录 CORS 正常，manager 最终健康探测为 `HEALTHY`。

### Result

- 新 worker/programs 部署并重启用户 OpenCode 后，会自动重建现场临时恢复所需的祖先软链接，无需逐个个人
  worktree 处理。本次未构建或部署企业离线包，`.114` 仍由用户已执行的临时链接维持恢复状态。
- 未修改 HTTP API、RunEvent/SSE、数据库/Flyway/SQL、权限、安全配置、性能策略、环境文件、generated SDK
  或 OpenCode 上游源码；没有创建分支。

## 2026-08-03 - 限制通用问答与长程任务活动栏入口

### Why

- 工作台活动栏的“通用问答”和“长程任务工作台”按钮原先对所有登录用户展示，需要仅让超级管理员看到。

### What

- 两个按钮复用 `AgentWorkbench` 既有 `isSuperAdmin` 角色判断控制显隐，没有新增平行鉴权逻辑。
- 同步 frontend 与 agent-web README，明确仅收紧活动栏按钮，既有登录保护路由和服务端权限保持不变。

### How

- Playwright 覆盖 `SUPER_ADMIN` 可见、`APP_ADMIN` 与 `USER` 不可见，定向 3 项通过。
- agent-web 用户手册、TypeScript 检查和生产构建通过，`git diff --check` 通过。

### Result

- 只有超级管理员能在工作台活动栏看到通用问答和长程任务工作台两个入口。
- 未修改 API、事件、数据库、性能、安全、兼容性、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-03 - 修复 SSH 密钥长密文日志脱敏栈溢出

### Why

- 企业环境下午包部署后，保存个人 SSH Key 经 X-WEB/Nginx 返回 502，Nginx 记录 Java 上游在响应头前提前断开，
  后端没有该请求的 `api_entry` 或 traceId；下午提交把 `encryptedPrivateKey` 加入通用敏感字段后，原有 Java
  正则会对约 2 KB 以上密文递归压栈并触发 `StackOverflowError`，异常恰好发生在 Controller 入口日志之前。

### What

- 通用敏感字段 JSON 脱敏正则改用占有量词，以无回溯方式扫描长字符串，同时保留转义引号处理和既有掩码语义。
- 新增 16 KiB `encryptedPrivateKey` 回归测试，并同步 API 模块 README 与安全规范，要求长密文日志脱敏不能中断
  业务请求。

### How

- JShell 精确复现旧表达式在 2 KB 密文上 `StackOverflowError`，修复后的表达式覆盖至 100 KB 仍正确脱敏。
- JDK 21 下定向日志/脱敏测试 49 项通过；`mvn -pl test-agent-api -am test` 的 18 个 reactor 模块全部通过。
- JDK 25 下先按完整本地启动命令验证，因本机缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 在启动前停止；未改环境文件，
  改用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build --without-workflow`
  完成 21 模块后端打包和实际重启，后端 health/readiness 为 `UP`、前端 3000 返回 200、登录 CORS 正常。

### Result

- SSH Key 长加密信封不再在请求入口日志脱敏阶段压垮处理线程，正常请求可继续进入鉴权和 Controller，并生成
  traceId；企业现场仍需重新构建后端产物并依次更新 `.4`、`.114` 节点，本次未执行企业包构建或现场部署。
- 未修改 HTTP API/DTO、事件、数据库/Flyway/SQL、权限模型、环境配置、前端、worker、manager、generated SDK
  或 OpenCode 源码；完整 workflow 本地启动仍受缺少开发密钥限制，与本次修复无关。

## 2026-08-04 - 恢复普通用户公共 Agent 只读浏览

### Why

- 公共 Agent 文件读取契约允许任意登录用户只读，但前端公共目录加载被误绑到当前用户 OpenCode 进程服务器；
  无公共 Git 写权限且尚未分配进程的用户因此看不到共享公共配置文件。

### What

- `AgentConfigPanel` 对无公共写权限用户复用既有已初始化公共仓库选服，直接读取共享公共副本，不创建个人
  worktree，也不依赖 `/processes/me` 是否已有 binding；超级管理员个人 worktree 的同服门禁保持不变。
- 新增“无写权限、无进程 binding”回归，验证目录请求不携带 worktree、显式路由到已初始化服务器，并以
  `readonly=true` 打开文件。同步 agent-web README/PACKAGE 和用户手册。

### How

- Agent 配置面板定向 38 项通过；前端 workspace lint 通过；排除既有失败的 `AppSourceDialog.test.ts` 后，
  全量前端 1778 passed / 1 skipped；agent-web 用户手册与生产构建通过，`git diff --check` 通过。
- 完整前端测试仍有一个与本次无关且可单独复现的 `AppSourceDialog` 保留期输入测试失败；Mermaid 首轮偶发失败
  单独重跑已通过。使用 Vite 实际启动 `http://127.0.0.1:4175/` 并确认 HTTP 200。
- 以昨晚已成功部署的组件状态为基线重新构建企业前端，定向 38 项、typecheck、生产构建、候选静态服务
  `http://127.0.0.1:4176/`、内外层 SHA/ZIP、Flyway 固定字节和双后台包门禁均通过；worker 与 toolbox
  指纹未变化，增量包均为 `reuse`，Workflow/LobeHub 保持关闭。

### Result

- 普通成员无需公共 Git 仓库权限或个人 TestAgent 进程即可查看公共 Agent/Skill 文件，所有写入、Git 操作和
  超级管理员个人 worktree 权限边界不变。
- 本次未修改 HTTP API/DTO、RunEvent/SSE、数据库/Flyway/SQL、性能或安全协议、环境配置、generated SDK、
  OpenCode 源码或依赖；未处理无关的 `AppSourceDialog` 测试失败，也未创建分支。
- Mac 端企业增量包已重新生成，只有前端制品变化，后端 JAR 与昨晚包 SHA 一致；企业现场尚未执行本包部署，
  部署前仍须核对两台后台的组件状态指纹，任一不一致即停止并改用包含对应组件的包。

## 2026-08-04 - 阻断未初始化用户工作空间跨服务器落盘

### Why

- 多 Java 部署中，应用版本创建已经要求当前用户 TestAgent 进程 READY，但个人工作区新建与 default 显式
  ensure/修复漏了同一守卫；未形成 ACTIVE binding 时请求会留在入口 Java，存在工作区先落到一台服务器、
  后续进程再按负载分配到另一台服务器的风险。
- default 修复判断只比较分支与路径，修复运行态记录时还沿用旧 `linuxServerId`；服务器归属不一致但本机路径
  碰巧存在时可能继续复用错误记录。前端点击版本或提交新增版本也会直接进入创建链路，没有先提示初始化。

### What

- `ManagedWorkspaceController` 的个人工作区新建与 default ensure 在调用业务服务前统一复用
  `UserOpencodeProcessAssignmentService.requireReadyProcess`；未初始化或进程不健康返回现有
  `OPENCODE_UNAVAILABLE`，不新增 API 或错误码。
- default 复用增加当前 `WorkspaceServerIdentity` 与运行态 `linuxServerId` 一致校验；修复时写入当前服务器身份，
  并复用既有 Workspace mutation gate 失效旧会话上下文。
- 工作台在选择具体版本或提交新增版本前检查进程 READY；未就绪弹确认框，用户确认后复用既有初始化进度弹窗，
  初始化完成后重新执行原操作，首次点击不发 Git 预检、版本创建或 default ensure 请求。
- 同步 API、workspace-management、agent-web README、HTTP API 和安全规范。

### How

- JDK 25 下 `ManagedWorkspaceApplicationServiceTest` 80 项、`ManagedWorkspaceControllerTest` 23 项通过；新增覆盖
  两个写入口的 READY 守卫、服务器归属修复和上下文失效。
- agent-web typecheck/lint、生产 build、AI 文档校验通过；Playwright Chromium 新增场景 1 项通过，验证初始化前
  零 Git/default 请求、确认初始化后必须重新选择才继续。
- 默认完整启动先按 `.env.test` 执行，因缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 在启动前停止，未修改环境文件；
  随后使用脚本正式支持的 `--without-workflow` 启动 backend、opencode-manager、frontend，health/readiness 为
  `UP`、前端 3000 返回 200、CORS 正常，manager 最终健康为 `HEALTHY`。

### Result

- 新用户不能再在未初始化时创建或修复个人工作区；已有 default 记录在显式 ensure 时会以当前绑定服务器为准
  收敛，因此同一入口不会继续产生“工作空间与 Agent 不在同一服务器”的新错配。
- 本次未迁移或删除历史用户数据（现场风险用户已由用户处理），未新增/修改 DTO、RunEvent/SSE、数据库/Flyway/
  SQL、性能策略、环境配置、generated SDK 或 OpenCode 源码；未构建或部署企业离线包，也未创建分支。

## 2026-08-04 - 二次复核收紧工作区初始化提示边界

### Why

- 二次检查发现工作区动作的初始化确认虽然已覆盖正常未初始化状态，但在进程状态尚未返回、明确
  `UNAVAILABLE`，或强状态为 READY 而弱健康未通过时仍可能误展示“初始化进程”按钮。
- 新增应用版本在初始化确认之前会先失效当前会话交互，不符合“用户确认前不产生前置副作用”的边界。

### What

- 工作台只在后端明确返回 `NEEDS_INITIALIZATION && initializable=true` 时显示初始化/启动确认框；状态查询中只提示
  等待，明确不可初始化或健康未通过时显示不可用告警并刷新状态，不发初始化、Git 预检、版本创建或 default
  personal workspace 请求。
- 新增版本的会话交互失效移动到进程就绪检查之后；补充不可初始化浏览器回归，并显式锁定个人工作区新建与
  default ensure 两个 HTTP 入口必须按 ACTIVE binding 路由到目标 Java。
- 同步 agent-web README 与 HTTP API 文档中的初始化状态机说明。

### How

- JDK 25 下 `ManagedWorkspaceApplicationServiceTest` 80 项通过；`ManagedWorkspaceControllerTest` 与
  `UserOpencodeBackendRoutingWebFilterTest` 合计 59 项通过。
- Playwright Chromium 两个初始化交互场景通过；agent-web typecheck、lint、生产 build、AI 文档校验和
  `git diff --check` 通过，构建仅保留既有大 chunk 提示。
- 运行中的 backend readiness 为 `UP`，前端 `http://127.0.0.1:3000` 返回 200，backend、frontend 和
  opencode-manager 进程均存活。

### Result

- 初始化提示现在不会把“检查中/不可初始化/健康异常”误当成可恢复初始化；可初始化状态仍先征得用户确认，且
  首次动作不会落盘工作区。
- 本次未新增或修改 HTTP 路径/DTO、RunEvent/SSE、数据库/Flyway/SQL、性能或安全协议、环境配置、generated
  SDK、OpenCode 源码或依赖；未构建或部署企业离线包，也未创建分支。

## 2026-08-04 - 修复个人 worktree 切换后文件操作按钮消失

### Why

- `switchWorkspace` 会先清空 `currentPersonalWorkspaceId`，普通版本切换又要等目录加载结束后才恢复；历史 Session
  和服务器目录切换则没有恢复该身份。文件树以该 ID 判定托管 Workspace 是否可写，因此新增、上传和删除入口
  会暂时或持续消失，刷新后由 recent/default 个人工作区恢复链重新写入才出现。
- 问题由 `7f2a4dd6d` 的切换清理和 `05117acd1` 的历史工作区切换路径埋下，`57f61b9d8` 开始按可写状态隐藏
  文件操作按钮后显性暴露；后续 7 月 28 日工作区重构保留了该行为。

### What

- 为 Workspace 切换增加个人 worktree 上下文参数，在清空旧状态后、激活目标 Workspace 和加载文件树前同步
  写入个人 ID/分支；版本选择、新增版本和应用 recent 切换直接传递已知身份，不再事后恢复。
- 历史 Session 和服务器目录入口复用既有个人工作区列表，按 `versionId` 查询并以运行态 Workspace ID 精确
  匹配；匹配不到时仍保持只读，不按名称、路径或角色放宽权限。
- 补充纯函数单测和 Playwright 回归，验证历史 Session 切到另一应用的个人 worktree 后不刷新即可看到根新增
  和文件删除入口；同步 frontend、agent-web、包说明、前端规范和模块图。

### How

- `app-source-workspace` 定向 Vitest 11 项、agent-web typecheck、历史切换相关 Playwright 12 项和生产构建通过；
  构建只保留既有大 chunk 告警。
- 使用 JDK 25、`.env.test` 和 `test` profile 重启；默认启动因缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 在启动前停止，
  未修改环境文件，随后用脚本支持的 `--without-workflow` 重启 backend、opencode-manager、frontend。后端
  readiness 为 `UP`、前端 3000 返回 200、CORS 正常，manager 最终把 OpenCode 4104 收敛为 `HEALTHY`。

### Result

- 个人 worktree 在版本、应用、服务器目录和历史 Session 切换后立即保持可写，新增/删除按钮不再依赖刷新；
  应用 feature 共享副本仍按原权限模型只读。
- 本次未新增或修改 API/DTO、RunEvent/SSE、数据库/Flyway/SQL、性能或安全协议、环境配置、generated SDK、
  OpenCode 源码或依赖；未构建或部署企业离线包，也未创建分支。

## 2026-08-04 - 增加个人工作区跨服务器自动搬迁

### Why

- READY 进程绑定和初始化提示已阻断正常入口继续产生新错配，但历史数据、手工调整、服务器故障切换或
  混合版本窗口仍可能使个人 Workspace 与当前 ACTIVE OpenCode binding 落在不同 Java 服务器。
- 仅修改数据库服务器归属会把源 worktree 中未 push 提交、暂存、未暂存和未跟踪文件遗留在旧服务器，
  因此需要可重试、先文件后数据库的两阶段搬迁。

### What

- 新增 XXL 全局互斥任务 `workspace-management.personal-workspace-relocation`，默认每分钟执行；Redis 广播唤醒全部 Java，
  各节点只扫描和认领源 worktree 属于本服务器的记录，广播失败仍由下一轮调度补偿。
- 源端用 Git bundle 和受控 ZIP manifest 保留个人 HEAD（含未 push 本地提交）、staged、unstaged 及全部普通未跟踪文件
  （包括 Git ignored 文件）；目标端通过精确 Java 路由的一次性 WebSocket 分片接收，校验大小、SHA-256、安全路径与
  Git HEAD/index/worktree/untracked 状态后，才在事务内切换 `workspaces`、`personal_workspaces` 和搬迁状态，最后清理旧 worktree。
- 新增 PostgreSQL `personal_workspace_relocations` 搬迁状态表与 MyBatis XML mapper，以 lease、fencing、事实快照、指数退避和
  `CLEANUP_PENDING` 支持宕机恢复；正在运行的 Run 会阻断搬迁。未合并冲突、Git 子模块、符号链接/特殊未跟踪项、
  超过 2 GiB 或 10000 个未跟踪文件均失败关闭并保留源目录。
- 内部 HTTP 只签发搬迁 ticket，文件字节不经 Java→Java HTTP 代理；ticket 绑定搬迁/源/目标、60 秒且单次消费，
  通过 `X-Test-Agent-Relocation-Ticket` 握手头传递而不放入 URL。普通用户只看到可重试错误，不暴露 workspaceId 或物理路径。

### How

- 定向后端回归通过：API 15 项、PostgreSQL/MyBatis/Flyway 11 项、XXL MySQL Testcontainers 3 项；最终修改后的
  workspace 专项 8 项通过，其中真实 Git 覆盖本地提交、staged/unstaged、普通 untracked、ignored、子模块、
  跟踪符号链接逃逸和清理幂等。工作台初始化交互 Playwright 2 项通过。
- JDK 25 下 `mvn -pl test-agent-app -am -DskipTests package` 成功；用未修改的 `.env.test` 执行
  `./restart-dev-services.sh --profile test --env-file .env.test --without-workflow`，backend readiness `UP`、frontend 3000 返回 200。
- 本地 PostgreSQL 已执行 `20260804123000`，XXL MySQL 已执行 V7；任务唯一、启用且 Cron 为每分钟，
  2026-08-04 13:17 最新执行 `handle_code=200`。两份 migration 在源码、模块 classes 和最终 app JAR 内 SHA-256 一致。
- `tools/verify-ai-docs.sh`、`git diff --check` 和冲突标记检查通过；同步更新 API、数据库、XXL、安全、模块图和多后端排查/统计 SQL 文档。

### Result

- 该修复不限于 `f-base`；全部启用应用中的个人工作区都会持续收敛到当前 ACTIVE binding 服务器。目标恢复或校验失败时
  不切数据库；数据库切换后源清理失败则保持 `CLEANUP_PENDING` 继续补偿，不再重复搬迁。
- 本次新增内部 HTTP/WebSocket 边界、PostgreSQL Flyway/MyBatis SQL 和 XXL MySQL migration；未修改 RunEvent/SSE、
  generated SDK、OpenCode 源码或 `.env*`，未新建分支。未构建/部署企业离线介质；产线上线前仍必须核对每套
  `flyway_schema_history` 与 checksum，两台 Java 同版本升级后再启用调度。

## 2026-08-04 - 复核企业搬迁 ticket 签发链路

### Why

- 用户要求复查企业内部 ticket 签发机制，重点确认之前因 Origin 白名单过严和多 Java 负载均衡造成的误拒绝是否重现。

### What

- 只做代码、企业配置和运行链路审查，未修改业务实现。确认 HTTP 签票使用两节点共享的
  `TEST_AGENT_XXL_JOB_ACCESS_TOKEN`，签票与 WebSocket 都复用同一个精确 `backend.listenUrl`，不会经
  `least_conn` 落到另一台 JVM；60 秒 ticket 只约束握手前消费，上传仍使用独立 30 分钟超时。
- 发现一个尚未修复的企业阻断：源 Java 固定发送 `Origin: https://test-agent.internal`，但全局高优先级
  `CorsWebFilter` 会在搬迁 WebSocket handler 前执行，企业白名单只包含平台域名/IP，因此真实企业握手会在
  ticket 校验前被 CORS 403。现有 handler 单测使用伪 Session，没有装配全局过滤器；本地 `.env.test` 使用 `*`
  又会掩盖该问题。

### How

- 对照 `RuntimeSecurityConfig`、搬迁 gateway/store/handler、`BackendHttpForwarder`、`BackendJavaRouteResolver`、
  双后台配置与打包校验脚本；Spring 7.0.8 `CorsConfiguration` 以企业白名单检查固定内部 Origin 的结果为 `null`。
- 本地运行实例因 `.env.test` 的通配 Origin 可完成 101 upgrade，并在 handler 内按无效 ticket 返回 `FORBIDDEN`，
  证明 CORS 过滤器确实参与 WebSocket 握手。JDK 25 下签票、handler、CORS、API 精确豁免和公共转发定向测试
  合计 21 项通过，但这些测试尚未覆盖“企业白名单 + 真实 WebSocket upgrade”组合。

### Result

- 当前企业搬迁通道不能标记为可交付；需先为精确搬迁 WebSocket 路径配置固定内部 Origin（不得放宽全局白名单），
  并补真实过滤链/upgrade 回归后再做双 Java 验收。本次没有修改 API、事件、数据库、环境配置、generated SDK、
  OpenCode 源码或业务代码，也未构建/部署企业离线介质。

## 2026-08-04 - 修复企业搬迁 WebSocket CORS 签发链路

### Why

- 企业显式 CORS 白名单不包含 Java 搬迁客户端固定使用的 `https://test-agent.internal`，全局 `CorsWebFilter`
  会在 WebSocket handler 消费一次性 ticket 前返回 403，导致跨服务器个人工作区搬迁无法开始。

### What

- 在既有全局 CORS source 中仅为个人工作区搬迁的精确 WebSocket 路径注册专用配置，只允许固定内部 Origin
  和 GET；普通浏览器白名单、相邻路径和子路径均不继承该例外，企业配置不得加入内部 Origin 或改成 `*`。
- 将固定 Origin 提升为与精确 HTTP/WebSocket 路径并列的协议常量，gateway、ticket store、过滤器和测试共用，
  避免签发端、握手端与前置安全过滤器配置漂移。
- 同步 API、安全、后端部署、多后台部署和 API 模块 README，明确生产配置边界和回归覆盖。

### How

- 先补企业白名单场景回归并确认修改前 4 项中 2 项失败；修复后同组 4 项通过。最终签票、handler、CORS、
  API 精确豁免和公共转发定向测试合计 23 项通过；`mvn -q -pl test-agent-api -am test` 全量通过，
  `mvn -q -pl test-agent-app -am -DskipTests package` 成功。
- 使用 JDK 25 和未修改的 `.env.test` 执行 `./restart-dev-services.sh --profile test --env-file .env.test
  --without-workflow`，backend readiness 为 `UP`、frontend 3000 返回 200。真实精确路径握手中，内部 Origin
  返回 101 并由 handler 对故意构造的无效 ticket 返回 `FORBIDDEN`；浏览器 Origin 在 handler 前返回 403。
- `tools/verify-ai-docs.sh` 与 `git diff --check` 通过。

### Result

- 企业固定内部 Origin 可以到达一次性 ticket 校验，浏览器不能借用该例外；签票仍绑定同一目标 Java、源服务器、
  60 秒一次消费和共享 `XXL-JOB-ACCESS-TOKEN`，没有放宽全局浏览器 CORS。
- 本次只修改精确路径的安全配置与复用常量；未修改 API/DTO、事件、数据库/Flyway/SQL、性能参数、环境文件、
  generated SDK 或 OpenCode 源码。未构建或部署企业离线介质；上线时两台 Java 必须使用同一新 JAR，且本分支
  既有搬迁 migration 仍须按企业 Flyway 历史/checksum 门禁验证。

## 2026-08-04 - 基于昨晚现网重新封装个人工作区搬迁企业包

### Why

- 用户确认早上 09:08 生成的企业包尚未部署，企业现网仍以昨晚已部署提交
  `1e6df22fab43edba6b5eb3d75f2c6a085eaf4ec7` 为基线；本轮不能把早上的包误当作已执行数据库基线。
- 当前代码已新增个人工作区跨服务器自动搬迁及企业 WebSocket CORS 修复，需要用最新 HEAD 重建前后端，
  同时继续关闭 Workflow 和 LobeHub，并按昨晚已安装组件指纹复用 worker/toolbox。

### What

- 基于当前 `codex/release-enterprise-20260801` HEAD 重新构建 backend、frontend 和双 Java 完整交付包；
  worker runtime 复用 `bf7b8e1d7c4e996c815a4c7dcf5ec163fe70707be385e0467cfa731170a0639a`，
  toolbox 复用 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`，
  Workflow/LobeHub 保持禁用，公共 Agent 配置未变。
- 本包包含 PostgreSQL `V20260804123000__create_personal_workspace_relocations.sql` 和 XXL MySQL
  `V7__register_personal_workspace_relocation_task.sql`；前者新增搬迁状态表，后者注册并启用每分钟搬迁任务。
- 终检发现交付手册仍把更早 `0352efa...` 写成当前现网基线，已同步修正 `deploy/internal/README.md`、
  `deploy/internal/MULTI-BACKEND.md` 和 `docs/deployment/database.md`：当前基线改为昨晚 `1e6df22...`，
  并分别明确 PostgreSQL 与 XXL MySQL 的发布前历史门禁，避免现场误判早上 09:08 包已经执行。

### How

- 后端定向回归覆盖 PostgreSQL/MySQL Testcontainers、Flyway 命名与兼容装配、MyBatis 搬迁状态、
  搬迁 HTTP/WebSocket、worker 和真实 Git 快照；前端 Agent 配置/工作区 49 项测试与 typecheck 通过。
- 当前 backend/frontend 构建成功；内部包和双后台外层包均通过 SHA-256、`unzip -t`、后端/前端
  `--validate-only`，本地 backend readiness 为 `UP`、frontend 3000 返回 200。
- 修正文档后执行 `tools/verify-ai-docs.sh` 与 `git diff --check`，再重新封装内外层固定名 ZIP；包内
  `START-HERE.md` 必须显示昨晚 `1e6df22...` 基线，不再出现“今早现网部署包”的错误表述。
- PostgreSQL migration 在源码、候选模块 JAR 和最终内层包中的 SHA-256 均为
  `f41a9aaab637f4b196f63cb7d37ef58cf0b15c9521abd1050c9929c6ce27b212`；XXL V7 均为
  `be1705cac272b9c4e89c43136f0125132c2afc4bbc3525322678cd02fb2c5305`。

### Result

- Mac 侧已完成最新代码候选介质构建和本地验证，早上 09:08 的旧包不得再部署；本次没有改动环境文件，
  没有把 Workflow、LobeHub、worker 或 toolbox 重新打入增量包。
- 尚未取得企业 PostgreSQL 与 XXL MySQL 的真实 `flyway_schema_history`，因此企业执行仍以数据库门禁为前提：
  PostgreSQL 应保留昨晚已执行历史且尚无 `20260804123000`，XXL MySQL 应为 V1-V6 成功且尚无 V7；
  出现未知 checksum、失败记录、版本倒序或环境分叉必须停止，禁止 `repair`、`outOfOrder` 或手改历史表。

## 2026-08-04 - 合成六项能力动态长图

### Why

- 用户提供独立工作空间、多任务并行、子智能体协同、后台与定时执行、跨资产库引用、Agent & Skill Hub
  六段 GIF，希望合成为带顶部整体介绍的动态长图，并将文件控制在 20 MB 以内。

### What

- 新增 `docs/assets/marketing/ice-blue/00-capabilities-long-demo.gif`，顶部复用既有价值主张介绍图，下面按
  `01/06` 至 `06/06` 顺序纵向排列并同步循环六段能力演示。
- 成品为 720×2842、124 帧、约 6 FPS、20.67 秒，大小 3,794,461 字节，并保留无限循环标记。

### How

- 使用 FFmpeg 将六段不同帧率和时长的 GIF 统一为 6 FPS，以最长的 20.67 秒为循环周期；采用 64 色全局
  调色板和 Bayer 抖动压缩，保持长图文字与界面状态可辨认。
- 完整解码成品，并检查首帧、中间帧、尺寸、帧数、时长、SHA-256 和 `NETSCAPE2.0` 循环扩展。

### Result

- 动态长图已生成且完整可播放，文件大小约 3.62 MiB，明显低于 20 MB 上限。
- 本次仅新增宣传素材并更新本机 session log；未修改业务代码、README、API、事件、数据库/Flyway/SQL、
  性能、安全、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-04 - 将个人工作区搬迁调整为每 30 分钟并保留 V7 历史

### Why

- 上一轮企业包尚未部署，用户希望重新打包前直接把个人工作区搬迁从每分钟改为每 30 分钟。
- 本机共享 XXL MySQL 已执行 V7；按 Flyway 不可变规则不能改写 V7，否则本地、共享或企业分叉环境会出现
  checksum 校验失败。企业现网虽然尚无 V7，也应走同一条可回归的 V7 → V8 升级链路。

### What

- 保留 `V7__register_personal_workspace_relocation_task.sql` 原始字节和 SHA-256
  `be1705cac272b9c4e89c43136f0125132c2afc4bbc3525322678cd02fb2c5305`；新增不可变 V8，只把该任务
  `schedule_conf` 更新为 `0 0/30 * * * ? *`、清零 `trigger_next_time`，不改变启停和执行策略。
- handler 默认 Cron 同步改为每 30 分钟；MySQL 8.4 回归新增已执行 V7 再升级 V8 的场景，并同步 workspace、
  XXL、数据库、架构、测试、HTTP API 和企业多后台部署手册。

### How

- JDK 25 下定向 handler/MySQL 测试 5 项通过；workspace 与 XXL 相关 reactor 完整测试共 637 项通过，
  MySQL/Redis Testcontainers 均实际执行、无跳过；企业 XXL 只读诊断脚本通过。
- 使用未修改的 `.env.test` 和 `--without-workflow` 重建并重启真实 backend、manager、frontend；默认启动因
  workflow 开发 Redis 密码缺失在构建前失败，按本轮 release 明确禁用 workflow 的口径关闭该组件后成功。
- 真实本地 XXL MySQL 从 V7 成功执行 V8，history 中 V7/V8 均为成功，任务唯一行最终为
  `0 0/30 * * * ? *` 且 `trigger_status=1`；backend health/readiness、frontend 3000、登录 CORS 和 manager
  WebSocket 均正常。

### Result

- 当前代码和数据库升级链路已通过运行验证，可作为本轮重新封装企业包的源码提交；企业首次启动将先执行
  V7、随即执行 V8，scheduler 启动后的有效频率直接为每 30 分钟，不需要现场再手工 update 任务表。
- 未修改 API 路径/DTO/事件、PostgreSQL migration、环境文件、generated SDK 或 OpenCode 源码；调度频率
  降低后数据库与广播负载相应下降。正式企业执行仍须先取得两套完整 `flyway_schema_history`：PostgreSQL
  应尚无 `20260804123000`，XXL MySQL 应为 V1-V6 成功且尚无 V7/V8；未知 checksum、失败记录或分叉必须停发。

## 2026-08-04 - 新增企业最近进程日志采集器

### Why

- 企业现场需要在没有 `rg`、`jd`/JSON 专用查看工具的 Linux 上采集最近几天进程日志，经中转机回传后定位
  Java、Docker、OpenCode 子进程和 Nginx 可能存在的缺陷；既有配置上下文采集器明确不采集日志，不能复用为
  同一用途。

### What

- 新增独立 `deploy/internal/collect-recent-process-logs.sh`，默认采集最近 3 天、可限制为 `1..14` 天；自动或显式
  按 backend/frontend 角色采集 systemd/journald、Test Agent 容器、受管 OpenCode 技术故障摘录、Nginx 日志和
  无命令行参数的宿主机快照，不重启服务、不读数据库、不读取 dotenv 或 manager process state。
- 每来源限制 20000 行、受管进程最多 40 个近期文件、归档最大 64 MiB；敏感字段先于长行截断统一删除，受管
  日志原文件名以 SHA-256 摘要替代，归档和校验文件固定为 mode `0600`。包内生成诊断关键词计数和少量首条
  证据，但明确不把关键词命中自动定性为缺陷。
- 新增可执行夹具 `tools/verify-internal-process-log-collector.sh`，并同步企业部署 README、文档索引、安全规范和
  AI 文档门禁。

### How

- 复用既有企业采集器的只读、显式敏感材料确认、安全路径、SHA-256、固定输出目录和归档上限做法；脚本只用
  Bash、`awk`、`grep`、`sed`、`find`、`tar` 等常见工具。夹具覆盖 3 天窗口、14 天上限、显式确认、日志轮转
  排除、凭据/prompt/query/统一认证号脱敏、禁止配置/state/binary 入包、诊断分类、权限和 SHA 校验。
- `bash tools/verify-internal-process-log-collector.sh`、`bash tools/verify-ai-docs.sh`、Shell 语法和
  `git diff --check` 均通过；生成的独立交付脚本 SHA-256 为
  `af522bcaeaa9c5e430d948752196f8262dbb7882dec02aa708b5dbc949e5bb61`。

### Result

- Mac 侧脚本、测试、文档和可经 U 盘转入 `~/Desktop/mimoagent/0709` 的脚本/SHA 文件对已准备并校验；本次不
  涉及 API、事件、数据库/Flyway、业务代码、环境配置、generated SDK 或 OpenCode 源码。
- 当前 Mac 没有中转机 SSH 配置，也没有挂载可写 U 盘，因此尚未实际进入企业中转机、未在 `.4/.114/.2`
  采集真实日志，暂时不能给出现场 bug 结论；后续取得中转连接或挂载介质后继续传输、逐节点采集和时间线分析。

## 2026-08-04 - 自动关闭十五天未使用的用户 OpenCode 进程

### Why

- 用户要求每天自动关闭超过十五天没有 Run 活动的用户 OpenCode 进程，释放长期占用的本机端口和进程资源；
  登录行为不作为使用依据，所有 Run 类型都计入活动时间。
- 没有 Run 的用户必须以 manager 记录的实际 `started_at` 为回退时间；边界采用严格早于十五天，且清理不能
  中断仍有活动运行的用户。
- 后续确认北京时间 02:00 清理还必须保护执行窗口仍有效的前一晚遗留任务和当天待投递任务；原候选 SQL 对
  每个进程在 SELECT/WHERE 重复执行相关 Run 聚合，5 万 Run、100 候选的 PostgreSQL 夹具约需 2.63 秒。

### What

- 新增 MyBatis XML 闲置候选查询，以 ACTIVE `opencode` binding 关联当前 `RUNNING/UNHEALTHY` 进程，最近活动
  时间取进程 `started_at` 与该用户全部 Run `updated_at` 的较晚值，并支持用户空闲闸门内按 processId 重检。
- 新增本机清理服务，复用 `UserRuntimeDisposeCoordinator`、公共 manager 容器归属解析和
  `OpencodeProcessStopService`；忙碌、扫描后状态变化、非本机 owner 和单进程失败均安全跳过，不删除 ACTIVE
  binding，用户下次使用时仍走既有公共启动程序恢复进程。
- 新增 XXL V9 不可变迁移和广播型 handler，每天北京时间 02:00 触发，任务 key 为
  `opencode-runtime.inactive-user-process-cleanup`，并同步 runtime、persistence、XXL、数据库、架构和测试文档。
- 清理服务复用夜间窗口的 `Asia/Shanghai` 时区，查询排除 `window_end > now` 且 `slot_start` 早于北京时间次日
  00:00 的 `SCHEDULED/DISPATCHING` 任务；扫描后在用户闸门内再次按相同边界复核。过期、次日和终态任务不保护。
- 候选 MyBatis XML 改为先收窄本服务器进程，再分别按 `runs.triggered_by_user_id` 和
  `sessions.created_by_user_id + runs.session_id` 现有索引聚合直接归属/legacy Run，只计算一次最近活动时间；
  待执行任务复用既有 owner/status/slot 索引，没有新增或改写 Flyway。

### How

- 初版定向新增测试 14 项通过；补充任务保护后，相关 Reactor 全量回归继续成功，其中 runtime 770 项、
  persistence 252 项（18 项按既有外部条件跳过），覆盖跨夜遗留、当天待投递、过期、次日、终态任务以及扫描后
  新建任务；XXL Job MySQL 8.4 与 Redis/PostgreSQL Testcontainers 均实际运行，AI 文档门禁和
  `git diff --check` 通过。
- 真实 PostgreSQL 16 用 5 万 Run、100 候选执行 `EXPLAIN ANALYZE`，新查询为 20.655 ms；Run 侧只出现现有
  用户/Session 索引扫描，没有原来每候选两次扫描 5 万行的结构。21 模块 `mvn clean package -DskipTests` 成功。
- 使用 JDK 25、未修改的 `.env.test` 和 `test` profile 完整打包并重启 backend、manager、frontend；默认启动
  因缺少可选 workflow Redis 密码在构建前失败，随后用项目支持的 `--without-workflow` 启动核心服务成功。
- backend health/readiness、frontend 3000、登录 CORS 和 manager WebSocket 均正常；本地 XXL MySQL 从 V8
  成功执行 V9，任务启用且 Cron 为 `0 0 2 * * ? *`。源码与最终启动 JAR 内 V9 SHA-256 均为
  `1d2e78716f3ffc33993de2c2b160fb48f6c8b6e9b71a7b592e4beaf6943a45e3`。

### Result

- 十五天未使用且当天没有有效待投递任务的用户进程自动关闭能力已完成真实运行验证；保留 ACTIVE binding，
  用户无需手工恢复数据，下次使用仍由公共启动程序拉起。未修改对话/Run/Session 编排、HTTP API/DTO、对外事件、
  PostgreSQL 结构、环境文件、generated SDK 或 OpenCode 源码，新增 Redis 广播仅携带空 payload 和内部 traceId。
- V9 已在本机共享 XXL MySQL 执行，后续不得修改其字节。企业发布前仍须取得目标环境完整
  `flyway_schema_history` 并核对 V1-V8 checksum/成功状态；未知 checksum、失败记录、版本倒序或分叉必须停发，
  禁止 `repair`、`outOfOrder` 或手工修改历史表。

## 2026-08-04 - 恢复晚间任务运行时长展示

### Why

- “任务消耗”的 Token 可从持久化 `step-finish` part 恢复，但时长只保存在浏览器 `chatStartedAt` 内存中；任务在
  页面关闭期间执行或切回历史会话后，就只显示 Token、不显示运行时长。
- Run 时间以 ISO Instant 存储；北京时间与 UTC 相差八小时属于展示换算，直接计算两个 Instant 的差值不会受
  时区影响。

### What

- 复用现有已鉴权 `backend-api.getRun()` 和 Run `createdAt/updatedAt`，不新增接口：晚间任务运行中从
  `createdAt` 恢复实时计时，成功、失败或取消后用 `updatedAt - createdAt` 锁定时长。
- runtime-state 摘要首次接管页面内晚间 Run 时补读一次完整 Run；终态 RunEvent 使用事件 `occurredAt` 更新
  `updatedAt`。普通手动任务继续沿用原累计计时语义。
- 单元测试覆盖运行中、终态、手动任务和异常时间顺序；浏览器用例验证历史晚间任务同时显示
  `12m 34s` 与 `1.2k tokens`，并同步 agent-web README/PACKAGE。

### How

- agent-web typecheck、工具单测 98 项、晚间任务 Playwright Chromium 用例和生产 build 通过；真实 Vite 前端
  已在 `http://127.0.0.1:4178/` 启动并返回 HTTP 200。
- 前端全量单测 112 个文件中 111 个通过，共 1792 passed / 1 skipped；唯一失败为既有
  `AppSourceDialog` 日期敏感夹具，其固定 `expiresAt=2026-08-01` 已早于当前日期 2026-08-04，定向复跑稳定失败，
  与本次修改文件和晚间任务链路无关。

### Result

- 晚间任务在运行中、刷新/历史切回以及页面关闭期间完成后均可展示运行时长，不再出现只有 Token 的情况。
- 未修改 API/DTO、RunEvent 类型、数据库/Flyway/SQL、后端、安全、环境配置、generated SDK 或 OpenCode 源码；
  无新增依赖。全量前端仍保留上述 1 项过期日期夹具风险，本次按最小范围未改动无关测试。

## 2026-08-04 - 修复统一认证新用户空角色并增强用户管理筛选

### Why

- 企业内统一认证首次登录只写入 `users`，没有同步写入 `user_roles`，导致新用户在超级管理员页面显示空角色，
  认证主体也没有普通用户权限；存量空角色用户需要由管理员按组织和部门定位后人工处理。

### What

- 统一认证首次建号在短数据库事务内同时写入用户和 `USER` 角色；TCDS 不可用时仍沿用降级建号，但同样授予
  最小普通用户角色。已存在用户不在登录时静默改权，默认角色字典缺失或角色写入失败时不留下无角色新用户。
- 用户管理列表新增按角色（含“未分配角色”）、组织、研发部门、部门和关键字组合分页检索；关系型查询通过新增
  MyBatis XML mapper 实现。页面支持勾选当前页用户、批量设置待保存角色，再复用现有保存入口逐用户提交。
- 角色保存继续复用既有 Token 撤销和上下文清理，相关用户需重新登录；没有新增自动迁移、定时任务或服务端
  批量写接口。同步后端各模块 README/PACKAGE、HTTP API、模块图、安全规范和前端包说明。

### How

- 后端定向测试 30 项通过，覆盖首次建号默认角色、失败回滚、存量用户不改权、组合筛选和未分配角色查询；
  前端用户管理与 backend-api 定向测试 104 项通过，三个相关包 typecheck 通过。
- JDK 25 下后端 21 模块 `mvn clean package -DskipTests` 成功，前端 production build 成功；使用未修改的
  `.env.test` 和 `--without-workflow` 重启 backend、manager、frontend，health/readiness 为 `UP`、前端 3000
  返回 200、CORS 正常、manager WebSocket 重新连接且 OpenCode health 为 `HEALTHY`。

### Result

- 后续统一认证新用户会直接获得普通用户权限；历史空角色用户可由超级管理员筛选、勾选、批量设置并保存，
  保存后重新登录即可使用新权限。该处理是登录时即时赋权加页面一次性人工治理，不需要定时任务。
- 本次仅增加 GET 用户列表的可选查询参数，旧请求保持兼容；未修改事件、数据库结构/Flyway、环境配置、
  generated SDK 或 OpenCode 源码。默认本地 Workflow 重启因既有开发 Redis 密码缺失未执行，不计入本次验证。

## 2026-08-04 - 基于今晚代码重新封装企业增量包

### Why

- 用户确认 17:20 生成的上一版本已经部署；本机该固定名交付包内最后一条发布相关记录对应
  `cec4ccf13769d9084c7d02efc158b021afe23c23`，因此本轮以该提交及已执行数据库历史为现场基线，
  不再沿用此前“个人工作区搬迁包尚未部署”的假设。
- 今晚代码新增闲置进程关闭、用户治理、晚间任务时长展示和企业日志采集能力，需要重建前后端；
  worker runtime/toolbox 源码指纹没有变化，Workflow 与 LobeHub 继续禁用。

### What

- 当前代码相对已部署基线新增 XXL MySQL V9：每天北京时间 02:00 广播关闭严格超过 15 天无 Run 活动、
  且没有活动 Run 或当天有效待投递任务的用户 OpenCode 进程；关闭保留 ACTIVE binding，用户再次使用时
  仍由公共启动程序恢复。PostgreSQL 没有新增 migration。
- 同步交付统一认证新用户默认 `USER` 角色、用户管理组合筛选/当前页批量设置、晚间任务历史时长恢复、
  当天待投递任务保护和闲置候选查询优化，以及只读脱敏的企业最近进程日志采集脚本。
- 修正 `deploy/internal/README.md`、`deploy/internal/MULTI-BACKEND.md`、`docs/deployment/backend.md` 和
  `docs/deployment/database.md`：当前已部署基线改为 `cec4ccf13...`，PostgreSQL 应已有
  `20260804123000`，XXL MySQL 应已有 V1-V8 且尚无 V9；第一台新 Java 只允许新增 V9。
- 企业组件清单保持 worker runtime/toolbox `reuse`，指纹分别为
  `bf7b8e1d7c4e996c815a4c7dcf5ec163fe70707be385e0467cfa731170a0639a` 和
  `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`；Workflow/LobeHub 均为 `disabled`。

### How

- 后端定向回归覆盖闲置进程服务/handler、PostgreSQL MyBatis 候选与用户筛选、用户领域/API 和 MySQL
  V8→V9 Flyway；真实 PostgreSQL/MySQL Testcontainers 均执行成功。前端用户管理、backend-api 和
  workbench-utils 202 项通过，shared-types/backend-api/agent-web typecheck 与 production build 通过。
- `verify-internal-process-log-collector.sh`、`verify-ai-docs.sh`、`git diff --check` 通过；候选内外层 ZIP
  通过 SHA-256、`unzip -t`、嵌套内层 SHA、后端/前端 `--validate-only`、RSA 和组件清单校验。
- 最终候选 ZIP 中 PostgreSQL `20260804123000` 仍为
  `f41a9aaab637f4b196f63cb7d37ef58cf0b15c9521abd1050c9929c6ce27b212`；XXL V7/V8/V9 分别为
  `be1705cac272b9c4e89c43136f0125132c2afc4bbc3525322678cd02fb2c5305`、
  `f4919a2f6ce224ecf50b347f2d438ad746753d9bbb8856a3403adf963f031bf2`、
  `1d2e78716f3ffc33993de2c2b160fb48f6c8b6e9b71a7b592e4beaf6943a45e3`，与源码一致。
- 首次本地重启因继承的 Java 不支持 release 21，在停止旧服务前失败；按 `restart-services` 约定显式设置
  JDK 25 后，以未修改的 `.env.test` 和 `--without-workflow` 重启 backend、manager、frontend 成功。
  readiness 为 `UP`、frontend 3000 返回 200、manager WebSocket 已连接；真实本地 XXL MySQL 的 V7/V8/V9
  均成功，搬迁任务为 `0 0/30 * * * ? *`，闲置进程关闭任务为 `0 0 2 * * ? *`，两条均启用。

### Result

- Mac 侧今晚代码的候选企业增量介质已完成构建和运行验证；公共 Agent 配置、worker、toolbox 未变化，
  本轮没有修改 `.env*`、RunEvent/SSE、generated SDK 或 OpenCode 源码。
- 企业实际部署尚未执行；用户确认的已部署提交只作为包基线证据，现场仍必须读取 PostgreSQL 与 XXL MySQL
  两套完整 `flyway_schema_history`。PostgreSQL 未知 checksum/失败/更高版本，或 XXL MySQL 不是 V1-V8
  全成功且 V9 缺失时必须停止，禁止 `repair`、`outOfOrder` 或手工修改历史表。

## 2026-08-05 - 优化批量角色保存并支持全选检索结果

### Why

- 用户反馈企业部署后的用户管理页保存角色非常慢，且表头复选框只能选择当前页，无法一次处理全部筛选用户。
- 排查确认旧页面对每名改权用户顺序调用一次单人接口；单人服务又在事务前后分别执行按用户 Token 撤销，
  `RedisTokenStore` 每次都扫描全部 Token。N 名用户因此产生 N 个 HTTP 请求和约 `2N` 次 Redis Token 扫描。

### What

- 新增 `PUT /api/internal/platform/system-management/users/batch-roles`，支持显式携带多名用户的不同目标角色，
  或按关键字、角色、组织、研发部门、部门筛选快照由服务端解析全部匹配用户；单次上限 5000。
- 批量角色关系在一个事务内全有或全无地替换，当前操作者取认证主体：按筛选全选时在 MyBatis SQL 中排除，
  显式包含当前账号时整批拒绝。user mutation gate 仍逐用户保留，Token 撤销改为事务前后各整批一次。
- 用户管理页保留表头当前页选择，新增“选择全部检索结果”；角色保存统一调用一次批量接口。全部结果模式只影响
  角色修改，批量删除和 TCDS 同步仍只处理当前页勾选行。当前登录账号的角色下拉被禁用。
- 同步 API、安全、模块图以及 backend/frontend 各模块 README/PACKAGE；只新增 MyBatis XML 查询，未改表结构、
  Flyway、事件、generated SDK、OpenCode 源码或 `.env*`。

### How

- 后端应用服务最新 18 项、Controller 10 项、MyBatis 查询 3 项通过，覆盖一次整批 Token 撤销、筛选全选、
  当前操作者排除、5000 上限和 SQL limit；前端用户管理与 backend-api 106 项通过，三个相关包 typecheck 通过。
- JDK 25 下后端 21 模块 `mvn clean package -DskipTests`、前端 production build、`verify-ai-docs.sh` 和
  `git diff --check` 通过。
- 默认 `.env.test` 重启先被既有 Workflow 开发密钥缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 拦截；未修改环境文件，
  改用脚本正式支持的 `--without-workflow` 重启 backend、manager、frontend。health/readiness 为 `UP`、前端
  3000 返回 200、CORS 正常，新批量路径已注册，manager WebSocket 已连接并恢复 `HEALTHY`。

### Result

- 页面保存 N 名用户时从 N 个 HTTP 请求降为 1 个，Redis Token 全量扫描从约 `2N` 次降为 2 次；没有虚构
  现场耗时数据，实际时延仍取决于企业 Redis、数据库和目标用户数量。
- 超管现在可按权限、组织和部门筛选后选择全部检索结果并手工赋权；目标用户旧 Token 立即失效，需要重新登录。
  本地 Workflow 未纳入本次运行验证，企业部署需同时更新后端与前端，无数据库迁移。

## 2026-08-05 - 修复应用 Agent & Skill Hub 远端同步后快照滞后

### Why

- 应用 feature 已由平台外部 push，并通过超级管理员应用 Git 刷新同步到本机后，版本 target 和 replica 会更新，
  但成功链路没有调用既有 Hub push 索引器，导致新增或删除的 Agent/Skill 仍展示旧快照。
- 同一物理 feature 仓库承载多个工作空间目录时，平台内发布链路也只索引触发发布的目录，兄弟目录可能滞后。

### What

- 新增仓库组 Hub 索引收敛程序：复用本机 READY replica 和既有 `AgentSkillHubPushIndexer`，按精确 commit
  为组内每个有效工作空间目录分别生成快照，单目录失败仍保持 push 成功并由定时对账补偿。
- 个人工作区发布、应用 Agent 配置发布以及超级管理员远端 Git 刷新统一调用该程序；Hub 不依赖用户个人
  worktree 执行拉取。平台外部 push 仍需管理员 Git 刷新让平台发现远端新 commit。
- 同步工作区模块 README 和 HTTP API 行为说明；未新增或变更接口、事件、数据库、环境配置或 generated SDK。

### How

- `ManagedWorkspaceApplicationServiceTest` 80 项通过，新增覆盖管理员远端刷新触发 Hub，以及同仓库组两个目录
  都按同一提交索引；`AgentSkillHubApplicationServiceTest` 9 项通过。
- JDK 25 下 `mvn -pl test-agent-app -am -DskipTests package` 成功，20 个后端模块完成打包；
  `git diff --check` 通过。
- 按 `.env.test` 默认链路尝试重启，启动脚本在停止旧服务前因工作流密钥文件缺少
  `WORKFLOW_DEV_REDIS_PASSWORD` 失败；未修改环境文件或切换旧 profile。此前运行的旧后端仍为 `UP`，
  但不包含本次改动，不能作为本次运行验证。

### Result

- 平台已完成 push 或管理员已同步远端 commit 后，Hub 会立即按仓库组反映新增和删除状态，无需用户个人拉取。
- 代码与打包验证完成；真实服务重启验证仍受本机 `.env.test` 工作流密钥缺失阻塞。

## 2026-08-05 - 在公共配置排空页定位并关闭单个阻塞用户

### Why

- 公共 Agent/Skill rollout 状态原先只返回服务器级排空计数和聚合 `lastError`，超级管理员无法判断具体是哪名
  用户、哪个进程和什么原因阻塞，也只能切到运行管理页另行查找。
- 项目已经具备按 `containerId + port` 停止单个受管 OpenCode 的公共链路，本次不应再新增一套停止接口。

### What

- `GET /agent-config/public/rollout` 的服务器状态新增 additive 可选 `pendingTargets`，MyBatis 按服务器返回最多
  200 个未进入 `DISPOSED/ABANDONED` 的目标，关联内部 `userId/username`，并携带进程身份、状态、重试、
  `lastError` 与 `forceStop`；不返回统一认证号、Session 内容或凭据。
- 系统管理排空页在服务器汇总行下展示未排空用户和原因，并提供“关闭该用户 OpenCode”。按钮二次确认后直接
  复用 `BackendApiClient.stopOpencodeRuntimeManagedProcess(containerId, port)`，后端继续经过运行管理路由、
  `RuntimeManagementCommandService` 和 `OpencodeProcessStopService` 的代次/PID/停止后健康确认；不修改 target
  `forceStop`，worker 在后续轮询中自行确认目标收敛。
- 同步 domain/persistence/API/frontend/shared-types README、HTTP API 和安全规范；没有新增数据库表、Flyway、
  RunEvent/SSE、依赖、generated SDK 或 OpenCode 源码，也没有修改 `.env*`。

### How

- JDK 25 下组合执行 `AgentConfigControllerTest`、`MyBatisPublicAgentConfigRolloutRepositoryTest` 和真实
  PostgreSQL 16/Testcontainers 集成测试，共 33 项通过；实库用例确认 username 关联与新旧批次
  `forceStop` 身份判定不变。
- agent-web 定向组件测试 12 项、typecheck 和 production build 通过；前端全量为 111/112 个文件通过，
  1798 passed / 1 skipped，唯一失败仍是既有 `AppSourceDialog` 固定过期时间夹具找不到保留期输入框，
  与本次排空页面无关。
- JDK 25 下 20 模块 `mvn -pl test-agent-app -am -DskipTests package`、`verify-ai-docs.sh` 和
  `git diff --check` 通过。按默认 `.env.test`/`test` profile 尝试真实重启时，脚本在停止旧服务前因工作流
  密钥文件缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 失败；未改环境文件或切换旧 profile。用户随后明确要求不启动
  Workflow/LobeHub：先安全停止旧版多 Worker screen、精确 PID、LobeHub 应用和开发容器，再用
  `--without-workflow` 重建并重启 backend、manager、frontend。health/readiness 均为 `UP`，前端 3000 和
  CORS 正常，manager WebSocket 已连接且 OpenCode 最终为 `HEALTHY`；8090/3210 均无监听，最终只保留三服务
  screen，frontend 启动变量也明确为 Workflow/LobeHub disabled。

### Result

- 超级管理员现在可以直接在排空页看到具体阻塞用户、个人错误和进程坐标，并复用已有停止能力只关闭该目标
  OpenCode；服务器聚合计数仍保持完整，诊断明细有界且兼容旧客户端/旧后端。
- 代码、真实 PostgreSQL、前端构建、定向行为和更新后端的本地三服务运行均已验证；Workflow/LobeHub 按用户
  要求保持停止，本次没有改写其密钥或环境配置。

## 2026-08-05 - 支持超级管理员手工修正用户名

### Why

- 企业内部署后，存量用户可能需要修正展示用户名；原用户管理页只能改角色、同步 TCDS 或删除，不能在保留
  `userId`、统一认证号、权限和历史关系的前提下手工改名。

### What

- 新增 `PUT /api/internal/platform/system-management/users/{userId}/username`，只允许 `SUPER_ADMIN` 提交
  最长 128 字符的唯一用户名；复用既有 `UserRepository.save`，不新增 SQL 或 Flyway migration。
- `User.renameUsername` 保留统一认证号、密码、组织部门、状态及创建时间；用户名不是权限边界，保存时不撤销
  Token，当前登录显示名到下次登录刷新，本地密码登录改用新用户名，后续 TCDS 同步仍可覆盖手工值。
- 用户管理列表增加“修改用户名”入口和上述覆盖/登录提示；统一认证号继续只读。同步更新 API、安全、模块和
  前后端包 README，并补齐应用服务、Controller、API 客户端和页面回归测试。

### How

- JDK 25 下应用服务 20 项、Controller 11 项测试通过；前端组件/API 客户端定向 108 项通过，shared-types、
  backend-api、agent-web typecheck 通过；后端 21 模块完整打包、前端 production build、AI 文档门禁和
  `git diff --check` 通过。
- 默认 `.env.test` 重启先被缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 阻断；使用 `--without-workflow` 时发现 8080
  已由 `intelligent-test-agent-support-access` 工作树占用，未停止该进程。随后把本次 JAR 隔离启动在 18081，
  XXL 端口改为 28080/19999，前端隔离启动在 4177；health/readiness 均为 UP，前端 200，改名 PUT CORS 预检
  通过，匿名请求命中新增 Controller 并返回 401。首轮隔离进程验证后已优雅停止；最终为便于页面验收，保留
  `test-agent-username-edit-backend/frontend` 两个隔离 screen，后端 18081 显式关闭 XXL 与服务广播，前端 4177
  只指向该后端，避免与另一工作树的 8080/3000 服务和调度注册冲突。

### Result

- 超级管理员可直接修正用户名，无需删除重建用户；统一认证号和权限保持不变，TCDS 覆盖风险在页面明确提示。
- 本次涉及新增高权限 HTTP API 和登录名兼容语义，不涉及事件、数据库结构、性能链路、环境文件、依赖、
  generated SDK 或 OpenCode 源码；企业升级需同时替换后端和前端交付物，无数据库迁移步骤。
## 2026-08-05 - 增加超级管理员限时只读问题排查访问

### Why

- 内网问题排查需要超级管理员查看任意目标用户归因的会话、工作区和文件，但原有 `SUPER_ADMIN` 普通接口既缺少
  明确目标范围、限时授权和逐次审计，也曾存在未托管文件工作区的管理员旁路。
- 共享“激活暗号”无法提供个人问责或可靠撤销，因此最终安全边界改为实时超级管理员角色、当前登录会话和限时
  随机授权 Token；隐藏快捷键只负责展示入口，不参与鉴权，也不存在暗号专用限流规则。

### What

- 新增排查授权、目标切换、用户会话/消息树、关联工作区、只读文件 WebSocket ticket 和一年期审计 API；授权
  5–240 分钟、同一登录会话只保留一个当前授权，Token 只驻留前端内存且服务端仅保存摘要。
- 目标资源按用户创建/触发/发送归因的活动会话，以及活动个人工作区和这些会话引用的活动工作区统一查询；普通
  Session/Workspace/文件入口同步补齐对象归属检查，并移除超级管理员未托管工作区文件旁路。
- 排查文件 WebSocket 每个 RPC 重新校验登录会话、实时角色、授权、目标用户、工作区和目标 Java，只开放
  list/search/read/chunk/binary-chunk，过滤 `.opencode`，拒绝写入、上传、下载、Git、终端和 Agent 配置。
- 系统管理页用 `Ctrl/Cmd+Alt+Shift+D` 显示排查面板，支持工单号、原因、时长、只读确认、目标用户切换、
  会话恢复元数据、只读文件预览和审计查询；同步 API、事件边界、数据库、安全、前端、模块图及模块 README。
- 新增 Flyway `V20260805132000__create_support_access_audit.sql`、MyBatis mapper 和 Redis 当前授权索引；没有修改
  generated SDK、OpenCode 源码或 `.env*`。

### How

- JDK 25 下后端 21 模块 `mvn test` 全量通过；新增授权服务、API、WebSocket、Session 归属和 PostgreSQL/MyBatis
  集成测试通过。最终补充 Spring 注入构造器后，授权服务与 CORS 定向测试再次通过，并以真实应用启动验证装配。
- 前端相关 121 项通过，workspace typecheck 和 production build 通过；全量 1801 passed / 1 skipped，唯一失败是
  `AppSourceDialog` 固定 `2026-08-01` 过期时间夹具，已在未修改的原工作区复现，属于日期相关既有基线问题。
- 打包 JAR 内 persistence 子 JAR 与源码构建物 SHA-256 一致，新 migration 资源与源码 SHA-256 均为
  `54cea9a84948f8e4cee14d630772b8ee0668c2a7e5fc897ede5e792a15edd761`。
- 新 worktree 缺被忽略的 `.env.test`，未复制或改写配置，改用原工作区现有测试 env 绝对路径。默认 Workflow
  初始化因数据库账号缺 `CREATEROLE/ADMIN OPTION` 失败，随后使用脚本正式支持的 `--without-workflow` 启动。

### Result

- backend、opencode-manager、frontend 已由新 worktree 构建物运行；backend health/readiness 为 `UP`，前端
  3000 返回 200，CORS 预检包含 `x-support-access-grant`，新 Flyway 已在本地 PostgreSQL 成功应用。
- 启动检查发现并修复了 `SupportAccessApplicationService` 多构造器缺少 `@Autowired` 的真实装配失败；同时停止并
  清理了占用 8080 的原工作区旧后端，最终监听进程的 JAR 路径明确属于新 worktree。
- 本地 Workflow 未纳入运行验证，原因是现有测试数据库角色权限不足；这不影响本次排查入口三服务验证。manager
  对历史 4104 端口仍会记录 `PROCESS_NOT_MANAGED`，属于本机旧进程绑定状态，不是本次排查授权链路错误。

## 2026-08-05 - 改为工作台三击 Shift 并恢复本地 OpenCode

### Why

- 用户本地验证时发现原组合键只有进入系统管理组件后才生效，活动栏入口距离当前操作区较远；同时用户绑定的
  4104 端口在服务重启后未被 manager 接管，平台返回 `TestAgent 进程不可用，请先初始化`。
- 新 worktree 默认把 `TESTAGENT` 指向自身目录，而本地数据库的 macOS `SYS_DATA_ROOT_DIR` 通过
  `$TESTAGENT/.testagent` 展开，导致公共 Agent 配置源解析到空的新 worktree 目录，正式初始化失败。

### What

- 将排查入口手势改为工作台任意位置 1 秒内连续按 3 次 Shift；只对当前 `SUPER_ADMIN` 计数，长按 repeat 不计，
  其它按键或超时重置。触发后直接切到系统管理的问题排查页，但仍需工单、原因、时长和只读确认才能签发授权。
- 手势检测沉淀为轻量前端工具，`AgentWorkbench` 负责全局监听和页面切换，系统管理包装层以一次性请求传递并在
  消费后清零；原组件级 Ctrl/Cmd+Alt+Shift+D 监听移除。
- 同步前端总览、agent-web README、模块图和安全规范；没有变更 HTTP API、事件、数据库、Flyway、后端、
  generated SDK、OpenCode 源码或 `.env*`。

### How

- 新增快捷键超时、打断、repeat 和显式 reset 测试；与系统管理组件测试组合共 16 项通过，agent-web typecheck
  和 `VITE_TEST_AGENT_API_BASE_URL=''` 的 Chromium 108 生产构建通过。
- 使用未修改的主工作区 `.env.test`，按脚本 `--without-workflow` 重启 backend、manager、frontend；启动时显式
  保留 worktree 代码根，同时让 `TESTAGENT`/`SYS_DATA_ROOT_DIR` 复用主工作区已有测试运行数据，再通过平台
  `/processes/me/initialize` 正式初始化，不手工伪造进程或数据库状态。
- 项目 Playwright 真实登录 `SUPER_ADMIN`：初始排查标题数量为 0，未点击系统管理直接三击 Shift 后标题可见，
  页面无 JavaScript 异常；截图保存在临时目录，不纳入仓库。

### Result

- backend health/readiness 均为 `UP`，前端 3000 返回 200；OpenCode 1.18.4 在 4104 监听，原生 health 为
  `healthy=true`，平台状态为 `READY/RUNNING`，manager 连续健康检查返回 `HEALTHY`。
- 三击 Shift 已通过组件、构建和真实浏览器验证。Workflow/LobeHub 仍按此前本地三服务范围保持不启动；首次默认
  Workflow 准备仍因测试数据库账号无 `CREATEROLE/ADMIN OPTION` 失败，未修改权限或环境文件绕过。

## 2026-08-05 - 自动回填当前超级管理员最近已入库工单

### Why

- 排查授权与审计已经把工单号写入数据库，但重新打开排查面板时工单输入框仍为空，需要重复录入。
- 直接复用审计列表第一条不可靠：审计查询本身会新增一条没有工单号的 `AUDIT_LIST` 事件，因此需要从授权表按
  当前 actor 精确读取最近一次工单，且不能恢复旧授权、原因或目标用户。

### What

- 在既有 `SupportAccessRepository` / `SupportAccessMapper.xml` 增加按当前 actor、签发时间倒序读取最近工单号的
  MyBatis 查询，复用已有 `support_access_grants(actor_user_id, issued_at desc)` 索引；没有新增表或 migration。
- 新增 `GET /api/internal/platform/system-management/support-access/grants/recent-incident`，只接受当前登录身份，
  服务层继续实时复核 `SUPER_ADMIN`，不接受前端 actor 参数，响应只含可空 `incidentId`。
- 排查面板挂载时读取建议值，只有输入框仍为空且登录用户未变化时才回填；失败保持可手工输入。原因、时长、
  只读确认、目标用户和 grant 均不恢复。
- 同步 HTTP API、数据库、模块图、backend/frontend 模块 README/PACKAGE 和共享类型说明。

### How

- JDK 25 下 `SupportAccessApplicationServiceTest` 5 项、`SupportAccessRepositoryIntegrationTest` 2 项通过；
  backend-api、快捷键、系统管理和新面板定向测试合计 116 项通过，两个相关前端包 typecheck 通过。
- 后端 20 模块 `mvn -pl test-agent-app -am package -DskipTests` 与空 API base 的 agent-web production build 通过。
  前端全量误触发运行时为 1807 passed / 1 skipped，唯一失败仍是既有 `AppSourceDialog` 固定过期日期夹具，
  与本次改动无关；本次定向文件随后精确重跑通过。
- 使用未修改的主工作区 `.env.test` 和既有测试数据根，以 `--without-workflow` 重启 backend、manager、frontend。
  真实浏览器登录 `888888888` 后首次接口返回 `incidentId=null`；随后通过页面签发并立即撤销本地测试工单
  `INC-LOCAL-PREFILL-20260805`，切走再回到排查面板后工单自动回填，原因为空且确认框未勾选。

### Result

- 当前超级管理员有历史授权记录时，新排查表单会自动带出自己最近一次已入库工单；没有记录或查询失败时保持
  空白，不会跨管理员取值或静默恢复旧授权上下文。
- backend readiness 为 `UP`、前端运行于 3000；本地测试库保留上述正常授权/撤销审计记录。未修改 `.env*`、
  OpenCode 源码、generated SDK、RunEvent 或数据库结构；Workflow/LobeHub 继续保持不启动。

## 2026-08-05 - 提高三击 Shift 排查入口触发可靠性

### Why

- 用户实际三击多次仍没有反应。复查发现全局监听器注册在冒泡阶段，页面中输入、重命名和弹窗
  控件调用 `stopPropagation()` 时顶层收不到 Shift；首次到第三次限制在 1 秒内也对人工操作偏紧。

### What

- 复用既有 `createSupportAccessShortcut` 检测器，把排查手势从通用窗口快捷键中拆出，改为 `window`
  捕获阶段监听；保留 Ctrl/Cmd+S 的原冒泡阶段和可编辑控件边界。
- 三次 Shift 总窗口放宽到 2 秒，兼容 `key/code` 中的 `Shift`、`ShiftLeft`、`ShiftRight`，
  仍忽略长按 repeat，并在其它按键或超时时重置。
- 同步前端总览、agent-web README 和安全规范；明确 iframe 键盘事件不会跨文档冒泡。

### How

- `support-access-shortcut` 与系统管理定向 Vitest 共 18 项通过；agent-web typecheck 和
  `VITE_TEST_AGENT_API_BASE_URL=''` 生产构建通过。
- JDK 25 下复用未修改的主工作区 `.env.test` 和测试数据根，以 `--without-workflow` 重启
  backend、opencode-manager 和 frontend；backend readiness 为 `UP`，前端 3000 返回 200。
- Playwright 真实登录 `SUPER_ADMIN`，焦点保持在聊天输入框并为该控件显式增加
  `keydown.stopPropagation()`，以 650ms 间隔三击 Shift；页面从排查标题数 0 切换为 1。

### Result

- 主页面内即使焦点控件阻止冒泡，人工速度三击 Shift 也能打开“问题排查只读访问”；工单自动回填仍
  保持为 `INC-LOCAL-PREFILL-20260805`，没有恢复旧授权、原因或确认状态。
- 未修改 HTTP API、RunEvent、数据库/Flyway、鉴权、限流、generated SDK、OpenCode 源码或 `.env*`；
  iframe 内获得焦点时仍需先点击平台主页面，再触发全局手势。

## 2026-08-05 - 补齐归档会话、旧正文恢复与只读用户视角

### Why

- 排查面板默认只查 `ACTIVE` 会话，用户删除或隐藏后的 `ARCHIVED` 会话无法按需查看；部分旧会话只有
  `session_messages` 原文，Redis、摘要或 OpenCode 空快照会导致页面显示空助手消息。
- 目标工作区绑定的 Java 后端可能已离线或 Redis 路由快照暂时不可用，旧实现仍尝试访问不可达 OpenCode，容易
  等待超时；面板自己的逐条 JSON/文本渲染也与用户首页展示不同，难以复现用户实际看到的问题。

### What

- 会话查询默认继续只返回 `ACTIVE`；超级管理员显式勾选“包含已归档会话”后才合并 `ARCHIVED`，仍排除
  `SIDE_QUESTION`。归档只表示从用户正常列表删除或隐藏，数据没有物理删除。
- 历史恢复增加有界旧 `session_messages` 兜底并标记 `LEGACY`；Redis/OpenCode 空结果不再截断兜底。目标工作区
  后端不是明确 `ONLINE` 时跳过 OpenCode，仅读取持久化历史，避免不可达服务器超时。
- 排查会话复用首页的 Session-tree reducer 与 `OpencodeTimeline`，能按首页方式显示 message part、工具、Todo 和
  子 Agent 投影；发送区保持禁用，不切换管理员身份。工作区列表展示在线/离线/未绑定/未知，非在线项禁止读取。
- 同步 HTTP API、安全规范、后端 runtime/persistence/domain/API 与前端 agent-web/backend-api/shared-types 的
  README/PACKAGE；没有新增事件类型、数据库结构或 Flyway migration，也没有修改 ticket/RPC 协议。

### How

- JDK 25 定向后端回归：`MyBatisSessionHistoryRepositoryIntegrationTest` 6 项、
  `RunMessageRecoveryServiceTest` 20 项、`SupportAccessDtosTest` 1 项，合计 27 项通过。
- 前端排查面板与 backend-api 定向回归 103 项通过；workspace typecheck 和 agent-web production build 通过，
  构建仅保留既有大 chunk 提示。
- 使用未修改的主工作区 `.env.test`，以 `--without-workflow` 重启 backend、opencode-manager、frontend；backend
  readiness 为 `UP`、前端 3000 返回 200、manager 对本机 OpenCode 连续返回 `HEALTHY`。
- Playwright 真实页面验证三击 Shift、已入库工单自动回填、目标用户切换、归档筛选和离线工作区禁用；打开绑定
  `192.168.100.165` 离线后端的旧会话约 3.6 秒（含 CLI 启动开销），页面显示“历史原文（旧存储）”以及非空用户/
  助手正文。

### Result

- 排查会话的正文展示与用户首页使用同一投影/时间线，空助手 envelope 不再作为正文渲染；离线目标可以直接查看
  已持久化历史，不再为了不可达 OpenCode 长时间等待。
- 这不是完整身份冒充或完整首页壳切换：管理员身份与所有写入口仍保持隔离，工作区文件仍使用排查面板的只读布局。
  Workflow/LobeHub 继续按本次本地三服务范围保持不启动；未修改 `.env*`、OpenCode 源码或 generated SDK。

## 2026-08-05 - 恢复工单自动回填并补充会话诊断上下文

### Why

- 排查组件可能先于当前登录用户资料挂载，原先只在 `onMounted` 查询最近工单；当 SUPER_ADMIN 身份稍后才到达时，
  查询会被直接跳过且不再重试，因此页面偶发不再自动填写已入库工单号。
- 排查页虽然能恢复用户会话正文，但没有直接展示业务 Session ID 和事件 Trace ID，定位后台日志仍需跨页面查找；
  页面字体、间距和强调色也没有完全对齐平台工作台。

### What

- 以当前 SUPER_ADMIN 用户 ID 作为回填触发键，身份异步到达或管理员切换时重新查询本人最近一次已入库工单；自动
  请求不覆盖人工输入，并增加显式“带入最近工单”入口和回填状态提示。
- 会话快照 HTTP DTO 保留 RunEvent 原始 `traceId`，shared-types 按可选字段接收以兼容旧后端滚动发布；排查上下文
  展示并可复制 Session ID、最近 Trace ID，存在多条 Trace 时可展开查看全部唯一值。
- 排查页统一使用平台 sans/mono 字体变量、平台红强调色和更紧凑的卡片/列表层级；会话正文继续复用首页
  `OpencodeTimeline`，没有引入管理员身份冒充或写入口。
- 同步 HTTP API、安全规范、后端 API README/PACKAGE 以及前端 agent-web/shared-types README/PACKAGE。

### How

- 前端排查面板与 backend-api 定向回归 104 项通过；workspace typecheck 和 agent-web production build 通过，
  仅保留既有大 chunk 告警。后端 `RuntimeDtosCompatibilityTest`、`RuntimeControllerTest` 通过。
- 使用未修改的主工作区 `.env.test` 和既有测试数据根，以 `--without-workflow` 重启 backend、opencode-manager、
  frontend；backend readiness 为 `UP`，前端运行于 3000。
- Playwright 真实页面完成三击 Shift、工单 `132` 自动回填、限时授权、目标用户选择和旧会话打开；确认页面展示真实
  Session ID、Trace ID、旧正文，并验证复制操作后主动撤销授权。

### Result

- 异步登录资料不再造成工单自动回填丢失；人工已填写工单不会被后台建议覆盖，查询失败仍可继续手工填写。
- Session/Trace 诊断信息已进入只读用户视角，便于直接关联服务日志；Trace 字段是向后兼容的 HTTP 响应扩展，
  未修改 RunEvent、数据库/Flyway、ticket/RPC、限流、`.env*`、OpenCode 源码或 generated SDK。
- 浏览器控制台仍有两条既有 `agent-config/public/diff` 400，与本次排查页请求无关；Workflow/LobeHub 继续不启动。

## 2026-08-05 - 将目标用户选择收拢到顶部并强化排查标识

### Why

- 目标用户列表占用左侧宽度，挤压会话列表和用户首页视角；管理员需要的是可输入姓名、用户 ID 或统一认证号的
  单一选择入口，而不是长期展开的侧栏。
- Session ID 和 Trace ID 已随会话快照返回，但原展示位于滚动正文内、字号和层级过弱，真实页面容易被误认为
  只有用户正文、没有排查标识。

### What

- 移除目标用户左侧栏，把目标选择放入顶部授权状态条，复用 Element Plus 远程可搜索下拉和已有用户查询接口；
  选择后仍调用既有 `selectSupportAccessTarget` 审计链路，没有增加平行接口或绕过授权。
- 搜索请求增加序号防止旧响应覆盖新关键字结果，并保留当前已选用户选项；真实浏览器验收时发现下拉展开事件会
  用空关键字覆盖输入搜索，已删除该重复请求入口。
- 会话诊断区改为滚动容器顶部的 sticky 排查条，使用“会话 SESSION ID”“最近 TRACE ID”明确标识并提高字号、
  对比度；会话、工作区和用户视角获得完整横向空间。
- 同步 agent-web README、组件 PACKAGE 和组件回归测试。

### How

- `SupportAccessPanel` 与 backend-api 定向 Vitest 共 104 项通过；前端 15 个工作区 typecheck 和 agent-web
  production build 通过，仅保留既有大 chunk 告警。
- Playwright 真实页面完成远程搜索、目标选择、旧会话打开和排查标识核对，确认左侧用户栏消失、顶部下拉只返回
  匹配用户，真实 Session/Trace 值均可见；测试授权随后主动撤销。
- 使用 JDK 25、未修改的主工作区 `.env.test` 和既有测试数据根，以 `--without-workflow` 重启 backend、manager、
  frontend；backend/readiness 均为 `UP`，前端 3000 返回 200，CORS 正常，manager 初始拉起后连续 `HEALTHY`。

### Result

- 目标用户选择不再占用左右布局，管理员可在顶部下拉直接输入检索；会话和用户视角的横向空间更充足。
- Session/Trace 是始终可辨识的排查信息，不再依赖管理员从普通用户正文中寻找。
- 未修改 HTTP API、RunEvent、数据库/Flyway、ticket/RPC、限流、鉴权、`.env*`、OpenCode 源码或 generated SDK；
  Workflow/LobeHub 继续不启动。

## 2026-08-05 - 修复排查工单刷新、复选框与诊断区收缩

### Why

- 最近工单只在 actor 首次到达时查询；问题排查面板保持挂载后再次三击 Shift 不会触发刷新，撤销后表单还保留
  上一轮工单、原因和只读确认，容易把数据库真实值未变化误判为前端缓存。
- 授权表单的通用 `input` 样式同时作用于 checkbox，把复选框撑成普通输入框高度并造成文字错位。
- 真实长会话中，正文滚动容器的 flex 子项允许收缩，吸顶排查标识被压到约 3px，DOM 中虽有 Session/Trace，
  视觉上却不可见。

### What

- 系统管理为每次问题排查手势生成激活代次；无有效授权时，重复触发、撤销或到期均禁用浏览器缓存重新查询
  当前管理员最近一条已入库工单，并清空上一轮原因、时长和只读确认；人工填写的新工单仍不被自动请求覆盖。
- 页面按钮和提示明确说明“重新读取最近工单”读取的是数据库最近一条授权记录；数据库最新值仍为 `132` 时继续
  显示 `132`，不伪造递增工单。
- 通用输入框样式排除 checkbox，授权确认与归档筛选都使用固定 14×14 复选框和两列网格对齐；排查标题、诊断区、
  时间线和只读输入区设为不可收缩，保留诊断区 sticky 行为。
- 同步 agent-web/backend-api README/PACKAGE 与定向回归测试；没有改变 HTTP 路径或响应结构。

### How

- agent-web 排查面板、系统管理和 backend-api 定向 Vitest 3 个文件 118 项通过；前端 workspace typecheck 与
  agent-web production build 通过，仅保留既有大 chunk 告警。
- 使用 JDK 25、未修改的主工作区 `.env.test` 和既有数据根，以 `--without-workflow` 重启 backend、manager、
  frontend；backend health/readiness 为 `UP`，前端 3000 返回 200，登录 CORS 正常，manager WebSocket 已连接。
- 真实浏览器确认三击 Shift 可打开入口、复选框与文字对齐；创建短时本地只读授权并打开长会话后，排查标识完整
  显示 `ses_0031744a5bb445c8b77357a26cb52eb3` 与最近 Trace，随后主动撤销并确认原因/勾选已清空。

### Result

- 工单会按真实数据库状态重新读取，重复触发不再复用组件首次挂载时的前端值；本地库最新工单确为 `132`，因此
  刷新后保持 `132` 是预期审计语义。
- 复选框对齐和长会话 Session/Trace 排查标识均已在真实页面验证；本地验收只新增正常的授权、选人、查看和撤销
  审计记录。
- 未修改 API 契约、RunEvent、数据库/Flyway、ticket/RPC、限流、鉴权、`.env*`、OpenCode 源码或 generated SDK；
  Workflow/LobeHub 继续不启动。

## 2026-08-05 - 为问题排查生成唯一单号

### Why

- 仓库内没有权威业务工单表或外部工单服务；原“最近工单”实际读取的是上一条排查授权记录，导致最新值为 `132`
  时重复激活、撤销和重新读取都会继续得到 `132`，形成自引用，不能标识新一轮排查。

### What

- 新增 `sai_` 加 32 位小写十六进制 UUID 的排查单号生成器；SUPER_ADMIN 每次请求建议值都会获得新号码。
- 新增规范接口 `GET /api/platform/support-access/grants/incident-suggestion`，响应增加可选 `source`；保留
  `/grants/recent-incident` 作为兼容别名，但不再查询历史授权。删除 Repository、MyBatis Mapper/XML 中已无用途的
  “最近工单”查询，没有新增或修改数据库结构。
- 前端将字段改为只读“排查单号”，三击 Shift 重复激活、撤销、到期和“生成新排查单号”都会请求新值；并用请求
  代次隔离迟到响应，生成失败时禁止提交授权。同步 backend-api/shared-types、模块 README、HTTP API、数据库和安全文档。

### How

- 后端定向测试覆盖 ID 格式、权限和每次生成不同号码；common、system-management、persistence、api 相关测试均通过。
- 前端排查面板、系统管理和 backend-api 定向 Vitest 3 个文件 118 项通过；workspace typecheck 与 agent-web
  production build 通过，仅保留既有大 chunk 告警。
- 使用 JDK 25、未修改的主工作区 `.env.test` 和既有测试数据根，以 `--without-workflow` 重启 backend、manager、
  frontend；backend health/readiness 为 `UP`，前端 3000 返回 200。
- 真实浏览器确认字段为只读，手动生成与再次三击 Shift 均得到不同且符合 `sai_[0-9a-f]{32}` 的排查单号。

### Result

- 新一轮排查不再循环复用历史授权中的 `132`；每次激活或重建都有可审计的唯一排查单号。
- HTTP 新增规范路径并保留旧路径兼容，`source` 为向后兼容的响应扩展；未修改 RunEvent、数据库/Flyway、
  ticket/RPC、限流、`.env*`、OpenCode 源码或 generated SDK。仓库未来接入真实工单源时可返回 `WORK_ORDER` 来源。

## 2026-08-05 - 合并问题排查能力并补齐企业打包迁移门禁

### Why

- 企业 release 分支需要纳入 `codex/support-access-admin` 已完成的超级管理员只读问题排查能力，并重新生成今晚的
  企业离线部署包。
- 本轮新增 PostgreSQL Flyway migration；既有企业数据库已经执行到工具箱点击统计版本，打包与部署必须显式区分
  已部署基线和本次新增版本，避免 Flyway 版本倒序、checksum 漂移或双后端同时迁移。

### What

- 将 `codex/support-access-admin` 合并到 `codex/release-enterprise-20260801`，保留双方 session log，业务代码无冲突。
- 纳入 SUPER_ADMIN 限时只读排查授权、唯一 `sai_` 排查单号、目标用户会话/工作区/文件只读视角、授权撤销与完整
  审计；令牌仅在页面内存保留，Redis/PostgreSQL 只持久化摘要和审计元数据。
- 企业 release、双后端完整包和安装校验脚本增加
  `V20260805132000__create_support_access_audit.sql` 的固定 SHA-256 门禁，并同步企业部署与数据库文档：上线前
  PostgreSQL 必须已有 `20260804123000` 且没有 `20260805132000`，首台新 Java 启动后只允许新增该版本；
  XXL-JOB MySQL 必须由 V1-V8 升至 V9。
- Workflow/LobeHub 继续保持 release 禁用；公共 Agent 配置、worker、toolbox 和 OpenCode 源码边界未因本次合并修改。

### How

- 企业包脚本通过 `bash -n`、`git diff --check`、AI 文档校验、完整包契约、增量组件契约和多后端节点契约测试。
- 前端 support access、调度管理、用户管理、backend-api 和文件浏览器共 146 项 Vitest 通过；15 个工作区
  typecheck 与 agent-web production build 通过，仅保留既有大 chunk 告警。
- JDK 25 下执行 `mvn -pl test-agent-app -am test`，20 个 reactor module 全部成功；API 481 项、persistence
  257 项（18 项按环境跳过）、app 60 项（1 项 fixture 跳过）通过。真实 MySQL 8.4 验证 V1→V9 及重复启动，
  真实 PostgreSQL 已知历史兼容升级测试 7 项全部通过。

### Result

- 问题排查能力及其 API、数据库、安全和前端文档已进入企业 release 分支，迁移文件源码 SHA-256 固定为
  `54cea9a84948f8e4cee14d630772b8ee0668c2a7e5fc897ede5e792a15edd761`。
- 企业部署仍必须先停全部旧 Java，再按 `.4 → .114 → .2` 顺序放量；禁止 `repair`、`outOfOrder` 或手工改写
  `flyway_schema_history`。未修改 `.env*`、generated SDK 或 OpenCode 上游源码。

## 2026-08-06 - 修复企业部署运行态日志噪声与长连接稳定性

### Why

- 企业三台节点日志中，进程尚未初始化时前端持续轮询模型目录，产生重复 503 和双层异常栈；运行态 SSE 空闲一小时
  后会被中间网络设备断开；OpenCode 还会对工作区 `.opencode` 尝试联网安装依赖。
- 原现场日志采集脚本既向旧 Docker 传递不兼容的 `--since 3d`，又按文件修改时间整份统计，导致当前日志文件里的
  历史故障与正常 `timeoutMs` 被误报为本次问题。

### What

- 模型/Provider 恢复轮询增加“已认证、OpenCode 进程 READY、当前无运行中操作”门禁；未初始化时不再制造 503。
- 只把精确的“请先初始化 TestAgent 进程”前置条件降为单条 WARN 并去重，其他同码故障仍保留 ERROR 和异常栈。
- 运行态 SSE 每 25 秒发送标准注释心跳，不增加业务事件、游标或前端状态变更。
- 官方 OpenCode 启动器删除无效的禁用安装环境变量，启动前递归发现已存在的 `.opencode` 并按不覆盖原则补齐
  离线 package、lockfile 与 `node_modules` 链接；保留自定义元数据由交付方保证离线完整的既有合同。
- 日志采集器兼容 BSD/GNU date 和旧 Docker 的小时格式，按 OpenCode/manager/Nginx 行内时间过滤，并收紧 manager
  异常统计正则；同步后端、前端、API、事件流和企业部署文档。

### How

- 后端定向 30 项、前端定向 114 项、启动器 6 项和日志采集器契约测试通过；后端打包、前端 workspace typecheck、
  agent-web production build、AI 文档门禁及 `git diff --check` 通过。
- 使用 JDK 25 和未修改的 `.env.test` 执行完整开发构建；本机缺少 `WORKFLOW_DEV_REDIS_PASSWORD`，因此使用启动脚本
  官方开关 `--without-workflow`。标准 8080 端口被另一工作区占用且未擅自停止，当前分支改在 18081 独立启动，
  manager 已完成鉴权和配置应用，frontend 3000 指向该后端；backend readiness 为 UP、前端返回 200。

### Result

- 代码侧可修问题已闭环，未处理用户明确排除的企业千问模型问题和已经修复的历史问题；没有修改 API 路径/DTO、
  RunEvent 业务类型、数据库/Flyway/MyBatis SQL、鉴权、`.env*`、generated SDK 或 OpenCode 上游源码。
- 企业现场仍需发布完整离线包并逐台重启 worker/服务；`.4` 的 systemd 启用和 `.114` 的 14118 残留端口清理属于
  服务器操作，本机未执行。启动器仅处理进程启动前已存在的 `.opencode`，运行后新建目录在下次受管重启时补齐；
  扫描会增加与工作区目录数量相关的少量启动 I/O，但跳过构建、依赖、Git 目录且不覆盖用户文件。

## 2026-08-06 - 将工作区依赖补齐移出用户启动并修复日期漂移测试

### Why

- 企业 worker 原由每个用户 `opencode serve` 启动前递归扫描整棵工作区，目录越多首次启动 I/O 越明显；进程运行后
  新建的 `.opencode` 又只能等下次受管重启补齐。
- `AppSourceDialog` 的续期用例写死 `2026-08-01` 到期日；当前真实日期推进到到期日之后，组件按设计隐藏第一步续期
  控件，但测试仍强行获取该输入框，形成时间漂移失败。

### What

- 用户启动器只处理固定配置目录和共同祖先依赖链接，不再递归扫描；worker entrypoint 启动唯一后台维护器并独立
  监督 manager。维护器开机只注册监听和 60 秒定时任务，不立即递归扫描；存量目录由周期任务补齐，新建
  `.opencode` 在递归文件事件可用时即时补齐，事件不可用时由下一周期收敛。
- 定时扫描禁止重叠，目录写入继续坚持非覆盖式 package/lockfile/依赖链接；事件路径同时校验逻辑路径、最终目录
  和物理根目录，拒绝经软链接父目录越出受管工作区。
- 前端续期用例固定在快照尚未过期的 `2026-07-30`，并在每个用例后恢复 mock；组件的真实到期判断未修改。
- 同步企业内部部署、OpenCode 1.18.4 升级和 App Source 测试说明，没有修改 OpenCode 上游快照。

### How

- launcher/entrypoint 9 项进程与文件系统回归、离线 Tool runtime 与 Git ignore 两项合同、Shell/Node 语法、
  `git diff --check` 和 AI 文档门禁通过。
- 前端全量 114 个测试文件为 1817 passed / 1 skipped，15 个 workspace typecheck 与 agent-web production build
  通过，仅保留既有大 chunk 告警。
- 按 JDK 25、`.env.test`/`test` profile 尝试真实重启，但启动脚本在停服务前因缺少
  `WORKFLOW_DEV_REDIS_PASSWORD` 失败；未改换环境文件或伪造密钥。8080/3000 仍由另一 worktree 的既有服务提供
  `UP/200`，不能作为本次代码的运行验证。

### Result

- 用户 OpenCode 首次启动不再承担工作区递归扫描；新增工作区通常即时补齐，文件事件不可用时最长等待约 60 秒。
- 未变更 HTTP API、RunEvent、数据库/Flyway/MyBatis SQL、鉴权、依赖锁、`.env*`、generated SDK 或 OpenCode
  上游源码。企业生效仍需重打完整离线包并重建/重启 worker；本机真实项目重启因既有 `.env.test` 缺项未验证。

## 2026-08-06 - 修复批量修改应用管理员角色返回 400

### Why

- 用户管理页显式修改一名或多名用户角色时只提交 `assignments`，后端请求 DTO 却使用不可缺省的原始布尔字段
  `allMatching`，导致 JSON 在进入角色业务逻辑前反序列化失败并返回 `Failed to read HTTP message`。

### What

- 前端显式角色模式固定提交 `allMatching: false`，共享类型改为稳定判别联合；后端将该字段改为可空包装类型，
  并把旧前端缺省或提交 `null` 的请求兼容为 `false`，保证企业前后端滚动升级期间仍可保存角色。
- 补充旧请求不带 `allMatching` 的 Controller 回归，并同步 HTTP API 与前后端模块说明；未修改角色事务、Token
  撤销、数据库、事件、鉴权或批量性能策略。

### How

- 修复前回归稳定复现相同 400；修复后 `UserManagementControllerTest` 12 项通过，前端全量 114 个测试文件为
  1817 passed / 1 skipped，三个相关包 typecheck、后端 20 模块打包、agent-web production build、AI 文档门禁
  和 `git diff --check` 通过。
- 按 JDK 25、`.env.test`/`test` profile 执行默认真实重启，脚本在停止服务前因缺少
  `WORKFLOW_DEV_REDIS_PASSWORD` 失败；未切换环境文件，也未停止另一工作树正在运行的服务。

### Result

- 修改用户为 `APP_ADMIN` 的显式批量请求不再返回 400；旧静态前端和新前端均可被新后端接受。企业生效需同时
  更新后端与前端交付物，无数据库迁移或一次性数据处理步骤；真实本地三服务运行验证仍受既有密钥缺项阻塞。

## 2026-08-06 - 修复新建应用弹框偏离页面中心

### Why

- 设置页“新建应用”是嵌套 Element Plus Dialog，已挂载到 body，但没有沿用外层设置和版本库弹框的
  `align-center` 约定，实际打开时会偏离视口中心。

### What

- 为现有“新建应用”Dialog 补充 `align-center`，不新增组件、样式覆盖或 API；组件测试增加居中属性回归断言。
- 同步 agent-web README，明确嵌套弹框按视口正中对齐。

### How

- 定向 Vitest 19 项与 agent-web typecheck 通过；当前源码 Vite 启动于 `http://127.0.0.1:3001`。
- Playwright 使用真实 Element Plus 渲染，在 1440×900 视口测得弹框 460×281、中心点 `(720, 450)`。
- 默认 `.env.test` 三服务重启在停服务前被既有工作流密钥缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 阻断，未修改
  环境文件或切换其它 profile。

### Result

- 新建应用弹框已稳定显示在页面正中；未修改 API、事件、数据库、性能、安全、兼容性、依赖或环境配置。
## 2026-08-06 - Skill Hub 按具体事项分类并开放超级管理员治理

### Why

- Skill Hub 原目录只有 Agent/Skill 资产类型，无法按日常工作、测试和代码分析等实际事项检索；用户新推送的
  Skill 也缺少统一待分类入口，需要由超级管理员在平台页面集中治理。

### What

- 复用既有 Hub 逻辑资产增加 `WORKER/TEST/CODE/OTHER` 一级分类；测试细分测试设计、测试数据构造、测试执行、
  测试分析，代码固定白盒分析，Worker 与 Other 当前无二级事项。历史、新 push 和平台内置 Skill 默认
  `OTHER/null`，后续 push 只更新不可变修订，不覆盖人工分类。
- 新增 Skill 分类分页筛选和 `PUT /assets/{assetId}/classification`；HTTP 边界只允许 `SUPER_ADMIN`，拒绝
  应用管理员、Agent、内置 Skill 和非法枚举组合。前端 Skill 页增加两级筛选、卡片标签和超级管理员详情编辑器，
  旧后端缺字段时兼容显示为“其他”。
- `V20260806143000__classify_skill_hub_assets.sql` 增加分类、最近分类管理员/时间、组合约束和筛选索引；分类 SQL
  位于 MyBatis XML。同步 Hub 各模块 README、HTTP API、模块图、数据库、安全及前端包说明。

### How

- 后端应用服务、Controller、MyBatis/H2 集成定向测试共 15 项通过，Flyway migration 命名/顺序 8 项通过；
  JDK 25 下 21 模块 `mvn clean package -Dmaven.test.skip=true` 成功。
- 前端 Hub 与 backend-api 定向 112 项通过，agent-web/backend-api typecheck 和 agent-web production build 通过；
  一次误触发全量前端测试为 1819 passed / 1 skipped / 1 failed，唯一失败仍是既有 `AppSourceDialog` 保留时长
  aria-label 断言，与本次文件无重叠。
- 使用未修改的主工作区 `.env.test` 从独立 worktree 启动。首次 workflow 锁定依赖准备因 pythonhost 网络超时失败，
  随后使用项目官方 `--without-workflow` 开关完成 backend、opencode-manager、frontend 重启；清理原工作区占用
  8080 的旧 backend 后，确认监听 JAR 来自本 worktree，health/readiness 为 `UP`、前端 3000 与登录 CORS 正常；
  最终运行 JAR 内 migration 字节 SHA-256 与源码一致。

### Result

- 真实 PostgreSQL 从 `20260805132000` 成功升级到 `20260806143000`，migration 源码 SHA-256 为
  `f59f641527fdabaf21393319cd70ed578c6f75a55decae4d8839bc2b561ac06d`；manager 日志未发现目标解码或重连错误。
- 本次变更涉及向后兼容 HTTP 响应扩展、受控分类写接口、Flyway/MyBatis SQL、超级管理员权限和分类索引；不涉及
  RunEvent、OpenCode 源码、generated SDK、环境配置或跨服务器文件路由。Workflow/LobeHub 未启动；正式交付前
  仍须按数据库规范核对全部目标环境 migration 历史和并行候选版本，禁止改写已在共享/稳定环境执行的 migration。

## 2026-08-06 - 定时持久化公共 Skill Hub 并移除查询链路 Git 扫描

### Why

- 公共 Agent/Skill 原先在每次 Hub 列表、详情和正文请求中重新扫描 Git，页面并发加载目录时出现秒级延迟；公共
  内容又可能由用户在其它本地 clone 直接 push，仅依赖平台页面操作触发索引会漏记。
- 应用推送内容已经写入 Hub 资产、修订和压缩制品表，但公共 Git Skill 仍是虚拟只读项，不能沿用超级管理员分类。

### What

- 新增 30 秒公共快照定时对账：使用共享仓库现有 Git 身份 fetch 当前分支，只刷新 `origin` 引用而不修改运行
  工作树；按远端精确 commit 扫描完整 Agent/Skill，元数据和当前提交写表，正文复用内容寻址 GZIP artifact。
  远端认证暂不可用时回退本地 HEAD；多 Java 通过事务锁和 commit compare-and-set 防止旧副本回退目录。
- Hub 列表、详情和正文全部改为数据库读取，保留历史公共 revision；公共 Skill 首次入库默认 `OTHER`，独立分类表
  以稳定 assetId 跨 commit 保留超级管理员分类，公共 Agent 仍不可分类。
- 新增 `V20260806190000__persist_public_skill_hub_snapshots.sql` 和
  `V20260806190500__classify_public_skill_hub_snapshots.sql`，同步 domain/repository、MyBatis XML、HTTP/数据库/模块
  文档及前端公共 Skill 分类入口。两份 migration 已在本机共享测试库执行，后续禁止改写原始字节。

### How

- 后端分类、定时远端 push 发现、无查询 Git、旧副本隔离和公共分类跨 commit 回归通过；完整相关 Maven 测试为
  common 96、domain 93、observability 6、scheduler 8、workspace 396、persistence 260（18 项按环境跳过），全部
  0 失败。前端 Hub 12 项通过，agent-web 类型检查与开发构建通过。
- 使用未修改的主工作区 `.env.test`、JDK 25 和官方 `--without-workflow` 开关，从独立 worktree 重新打包并启动
  backend、opencode-manager、frontend；health/readiness 为 `UP`，前端 3000 返回 200，实际 Java 运行本 worktree
  的不可变 JAR。真实 PostgreSQL 16 从 `20260806190000` 升到 `20260806190500` 成功。
- 最终 JAR 内两份 migration 与源码 SHA-256 一致：`190000` 为
  `1b2547cf466c09fe11a63b1f76e5e17ec1773e2187aa01e052288a9bb4861e75`，`190500` 为
  `19a0e5af5f361179ac3887d541c274f75f43f89a683ee8037a5e0391444a92bf`。

### Result

- 本机数据库当前公共目录为 6 个 Agent、12 个 Skill，18 条公共 revision，12 条公共 Skill 分类均为默认
  `OTHER`；数据库 current commit、本地 HEAD 与 `origin/master` 都是
  `8b81dc4d9e343e9d6a999cff816d8c3dbfedf923`。
- Hub 热查询实测由原逐请求 Git 扫描的秒级下降到约 5–21ms；首次并发冷查询约 79–117ms。此次涉及数据库、HTTP
  分类行为和性能，不改 RunEvent、OpenCode 源码、generated SDK、环境配置或跨服务器文件路由。正式交付前仍须
  对照全部目标环境 Flyway 历史与并行候选版本，验证每套已知 PostgreSQL 基线升级。

## 2026-08-06 - 降低公共 Skill Hub Git 对账频率

### Why

- 实际公共仓库 `git fetch` 通常耗时 2.2～4.4 秒，偶发达到 6.7～13.6 秒；每 30 秒执行会产生不必要的网络、
  SSH 和日志开销。公共快照查询已经完全走数据库，外部本地 push 允许接受更长的兜底发现时间。

### What

- 复用 `AgentSkillHubApplicationService.reconcilePublicBuiltinSnapshots()` 既有 fixed-delay 调度，只把默认间隔从
  `PT30S` 调整为 `PT10M`，启动后首次 2 秒对账和可覆盖配置键保持不变。
- 新增反射测试锁定默认调度表达式，并同步 workspace-management README 与 HTTP API 稳定文档。

### How

- Hub 后端定向测试 21 项通过，前端 Hub/backend-api 113 项通过；JDK 25 下 `test-agent-app -am package
  -DskipTests` 和 agent-web production build 均成功，`git diff --check` 通过。
- 核对 `git cherry` 与祖先关系，确认 taxonomy 两个功能提交尚未进入 `codex/release-enterprise-20260801`；当前
  8080/3000 运行实例仍来自本功能 worktree，但运行 JAR 早于本次间隔修改，未为避免误切 release 而重启。

### Result

- 新构建默认在每轮对账结束后等待 10 分钟再执行下一轮，不会发生同一调度方法重叠。
- 页面“刷新目录”只重新查询数据库，不会手工执行公共 Git 对账；系统管理公共仓库更新会触发 rollout，但当前也
  没有直接调用 Hub 对账。外部 clone push 的最坏发现时间仍接近 10 分钟，合并后如需即时对账应另设显式入口。

## 2026-08-06 - 恢复工作区 API 的 /data 物理路径

### Why

- 工作区列表和详情切换到用户范围查询服务后，直接返回数据库中的 `personalworktree:` 逻辑路径；小地球
  外部页面仍把 `rootPath` 当作可访问目录传递，因此不再能定位到原来的 `/data/...` 个人 worktree。

### What

- `UserWorkspaceQueryService` 在完成用户归属过滤后，复用 `ManagedWorkspacePathResolver` 解析列表和详情中的托管逻辑路径；
  历史绝对路径保持原有兼容行为。
- 新增个人 worktree 列表/详情回归测试，并同步 workspace-management README 和 HTTP API 文档的 `rootPath` 响应语义。

### How

- JDK 25 下定向 Maven 测试通过，前端小地球相关 Vitest 通过，21 模块后端 `clean package` 成功，`git diff --check` 通过。
- 默认重启受缺失 workflow 密码和另一 worktree 占用 8080 影响；未停止对方进程，改用 18081 隔离启动本次构建，
  Spring 上下文启动成功且 `/actuator/health` 返回 `UP`。

### Result

- 工作区 API 恢复返回 `/data/...` 物理绝对路径，小地球可继续将该参数交给同服务器外部页面。
- 本次只修正现有 HTTP 响应值，不新增 API/事件/数据库变更，不涉及性能、安全、OpenCode 源码、generated SDK 或环境配置。

## 2026-08-06 - 加固企业发布包 Flyway 门禁并重建交付物

### Why

- 当前交付分支新增了 Skill Hub 分类与公共快照持久化的三份 PostgreSQL migration，原企业打包和部署脚本
  尚未锁定它们的 JAR 内资源路径与原始字节，存在误发、漏发或 migration 被后续改写时仍继续发布的风险。

### What

- 复用现有 release Flyway 校验链，在单包、双后端整包和正式部署入口锁定
  `V20260806143000` / `V20260806190000` / `V20260806190500` 三份资源及 SHA-256，并同步脚本契约测试和部署文档。
- 扩展现有 PostgreSQL Testcontainers 兼容性测试，明确覆盖企业 `cec4ccf` 已部署基线第一次启动停在
  `20260804123000`，第二次启动升级至当前 HEAD 的完整路径。
- 保持 Workflow 与 LobeHub 禁用；单机配置渲染器对三个可选 HMAC 变量采用“已有值原样保留，未配置则空值”，
  避免禁用能力的 `REPLACE_` 模板占位符阻断配置重建和 `--validate-only`。

### How

- JDK 25 下 PostgreSQL 16 真实数据库兼容测试 8 项全部通过，包括已部署基线→当前 HEAD；MySQL XXL-Job
  migration 测试 4 项全部通过；三组企业脚本契约测试、AI 文档校验、Shell 语法与 `git diff --check` 通过。
- 在 macOS arm64 使用正式 `package-release.sh` 重建 backend、frontend 与 linux/amd64 Worker 镜像；首次 Worker
  `go mod download` 因 `goproxy.cn` EOF 中止，保持同一输入重试后完整通过，未跳过官方 Codex 契约或 Flyway 校验。

### Result

- 当前企业包将 Worker 运行时作为 `included` 交付，Toolbox 继续 `reuse`，Workflow/LobeHub 不启用；打包与
  部署前会逐份拒绝三份新 migration 的缺失或字节偏差。
- 本次只修改企业打包/部署脚本、兼容性测试和稳定部署文档；不新增生产 API、事件、migration 或环境配置，
  不修改 OpenCode 源码和 generated SDK。生产仍必须先导出 PostgreSQL/MySQL `flyway_schema_history`，未知版本、checksum 或
  历史分叉必须停止发布，严禁 `repair` / `outOfOrder` / 手改历史表。

## 2026-08-06 - 恢复 Web 的 OpenCode TUI 原生命令兼容层

### Why

- 工作台把所有 `/xxx` 统一交给 Skill/Command 解析后，`/compact`、`/new`、`/clear` 等 OpenCode TUI
  内置命令会被吞掉或误发为普通命令；即使页面已有按钮，也不应移除用户熟悉的原生输入能力。

### What

- 输入 `/` 的候选固定按“技能、OpenCode 原生能力、项目命令”展示，原生命令及别名优先于同名项目命令；
  恢复 `/compact`、`/new`、`/clear`、`/undo`、`/redo`、`/sessions`、`/details`、`/thinking` 等
  OpenCode 1.18.4 TUI 能力，并保留 `/summarize`、`/resume`、`/continue`、`/quit`、`/q` 等别名。
- 本地显示命令复用现有模型、会话、帮助、导出与时间线组件；Session 命令复用 compact/revert/unrevert/share
  API，`/init` 继续走原生 session command；`!command` 复用既有 shell API。工具详情和 reasoning 显隐改为
  时间线受控展示，不改写 reducer 消息状态。
- 同步 agent-web、agent-chat、backend-api README、HTTP API 与模块图；OpenCode 上游快照保持只读。

### How

- 新增命令解析、别名、分区顺序、本地显示切换、原生 API payload 与 shell 分发回归；聚焦 348 项通过，
  前端全量 114 个测试文件 1827 passed / 1 skipped，三个相关包 typecheck 与生产 build 通过。
- 真实 Vite 实例启动于 `http://127.0.0.1:4174/`，首页返回 HTTP 200；`git diff --check` 通过。

### Result

- Skill 仍优先展示，Web 不再丢失 OpenCode TUI 的 slash/alias 输入路径；已有按钮与原生命令并存。
- 未新增或修改 HTTP 路径、RunEvent、数据库、性能策略、安全权限、依赖、环境配置、generated SDK 或
  OpenCode 源码；`/connect`、`/models` 继续受企业 Provider 白名单约束，`/themes` 保持统一企业主题说明。

## 2026-08-06 - 收口工作区物理路径响应与前端消费边界

### Why

- 工作区数据库为跨服务器搬迁保存 `personalworktree:` 等逻辑路径是正确设计，但普通 Workspace 查询曾绕过
  统一响应解析，导致小地球和复制路径拿到逻辑前缀或相对路径；前端又存在直接兜底复制相对路径的问题。

### What

- `ManagedWorkspacePathResolver` 新增严格响应解析：逻辑前缀和历史绝对路径正常解析，未纳管相对路径失败关闭；
  存量 Git/文件/PTY/Run 执行解析保持兼容。普通、排查、文件 WebSocket 创建及托管 Workspace 响应统一增加
  `physicalRootPath`，兼容 `rootPath` 返回同一物理绝对路径。
- agent-web 新增统一物理路径守卫；复制路径只接受绝对 Agent 路径或“物理工作区根 + 安全相对文件”，小地球
  缺少物理根时禁用，不再消费逻辑前缀、内部 tab route、相对 Agent 路径或 `..` 越界路径。
- 同步 API、domain、workspace-management、api、agent-web、shared-types README/PACKAGE 与模块图。

### How

- 后端 20 条定向用例通过，覆盖物理响应契约、相对路径失败关闭、用户工作区查询和个人 worktree 搬迁；
  另有 73 条 Workspace service/controller/WebSocket 兼容回归通过。前端 3 个测试文件 38 条通过，
  agent-web typecheck 与生产 build 通过；JDK 25 后端完整 package 通过。
- 因 8080 被并行 worktree 占用，使用项目 `dev-backend-run.sh`、原 `.env.test` 和 test profile 在 18081
  隔离启动，health/readiness 均为 UP，Workspace API 未认证访问按预期返回 401；前端生产 preview 在 4178
  返回 200，验证后均已关闭，未干扰既有 8080/3000 服务。

### Result

- 数据库存储语义与昨天的跨服务器个人 worktree 搬迁不变；对外物理动作从类型字段到组件入口均失败关闭，
  不再由进程当前目录或前端字符串拼接制造“看似绝对”的错误路径。
- 本次为 additive HTTP 响应字段，旧 `rootPath` 保持物理值兼容；未变更 HTTP 路径、RunEvent、数据库、
  Flyway/MyBatis SQL、性能策略、安全权限、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-06 - 修复 OpenCode Manager 被 SIGKILL 后无诊断及维护器资源回归

### Why

- 企业 worker 在运行一段时间后只留下 entrypoint `wait` 位置的 `Killed`，该现象可确定 manager 收到
  `SIGKILL`/退出 137，但旧日志无法区分容器 OOM、宿主机 OOM、人工 `kill -9` 或 Docker 强杀。
- 上一版为工作区 `.opencode` 自动补齐引入了常驻 Node 递归监听器，在大型共享工作区可能长期占用并放大
  内存和文件句柄，且会与 manager 共享 worker 资源。

### What

- 移除 launcher 中常驻递归 `fs.watch` 和常驻 Node 维护进程；entrypoint 先启动 manager，首轮等待 60 秒，
  之后每轮仅启动一次短生命周期扫描，结束即释放资源。扫描逐目录处理 `.opencode`，不再累计全部路径。
- 保留新建工作区自动补齐：新 `.opencode` 在下一轮扫描收敛；旧内部命令名保留单次扫描兼容别名，避免
  programs 与 worker 临时错配时直接失败。
- manager 非正常退出时记录 `event=opencode_manager_exited`、退出码、信号和 cgroup OOM 计数/增量；只有
  OOM 增量上升或 Docker `OOMKilled=true` 才据此确认容器 OOM。
- 同步企业部署、内部发布和 OpenCode 1.18.4 升级文档，补充 137/SIGKILL 的现场取证与处理边界。

### How

- launcher/entrypoint 进程级回归 9 项通过，覆盖不开机扫描、manager 先启动、周期单次扫描、信号清理、
  新旧内部命令和 137/OOM 结构化诊断；Shell/Node 语法、runtime deploy、Git ignore、AI 文档和 diff 门禁通过。
- 在 macOS arm64 上实际重建 `linux/amd64` worker 镜像并通过官方 OpenCode 1.18.4 基线、Python、Codex MCP
  协议 smoke；镜像内复核周期命令存在且常驻 `watch(` 不存在。Codex 原生 sandbox 按门禁要求留待原生
  linux/amd64 worker 验证。

### Result

- 新 worker 不再常驻递归 Node watcher，并能在 manager 再次被强杀时留下判定 OOM 与否的证据；旧现场只有
  `Killed`，因此不能倒推确认当时一定是 OOM，企业 Docker 18.09/linux/amd64 仍需做长时间运行验收。
- 未修改 API、事件、数据库/Flyway/MyBatis SQL、安全权限、环境配置、generated SDK 或 OpenCode 上游源码；
  programs 与 worker 镜像必须作为同一完整企业包配对发布。

## 2026-08-06 - 审核并稳定 Worker 启动顺序回归

### Why

- 企业包重打前复核 manager/短生命周期维护器进程测试时，完整测试曾因 2 秒异步等待窗口过窄出现一次
  manager PID 文件超时；同一用例连续运行 5 次均通过，其中一次总耗时已超过 2 秒。

### What

- 只将测试辅助函数等待文件生成和进程退出的默认超时从 2 秒放宽到 5 秒，不修改 worker 生产启动顺序、
  60 秒维护周期、退出诊断或任何运行参数。

### How

- 连续 5 次执行 manager 启动顺序用例确认生产逻辑稳定，再用完整 launcher/entrypoint 回归和企业门禁复验。

### Result

- 消除繁忙机器或并行测试调度引起的假失败；生产代码、API、事件、数据库/Flyway、安全、环境配置和
  OpenCode 上游源码均未变化。

## 2026-08-06 - 区分首次数据库升级与已部署包故障重部署

### Why

- 重打 manager 故障修复包时发现，企业手册仍只描述从 `cec4ccf...` 首次升级，要求四条 PostgreSQL
  migration 和 XXL V9 部署前不存在；上一企业包已经部署的现场会因此被错误阻断。

### What

- 企业 README、多后台手册和数据库文档统一增加两条明确路径：首次升级只允许新增固定四条 PostgreSQL
  migration 与 XXL V9；已部署上一包的故障重部署要求这些版本全部成功且 checksum 不变，不得新增 history。

### How

- 对照 `ab6e46936..HEAD` 确认没有新增或修改 PostgreSQL/XXL migration；保留未知 checksum、部分历史、
  失败记录、`repair`、`outOfOrder` 和手改历史表的停止发布门禁。

### Result

- 当前 manager 修复包的数据库验收不会误套首次升级条件；只调整稳定部署说明，不改 API、事件、SQL、
  运行配置、generated SDK 或 OpenCode 上游源码。

## 2026-08-06 - 阻断容器重启后的 Manager PID 复用自杀

### Why

- 移除常驻 watcher 后企业 worker 仍约两分钟重启，最后一行变为 entrypoint 第 103 行、PID 12 被 Killed；
  该行仍是 `wait`，数字 12 是 manager PID，不是错误码。
- manager state 持久化到宿主机，但 worker 重启会结束 PID namespace 内全部用户子进程；旧 state 继续保留
  数值 PID 后，PID 可能复用成新 entrypoint/manager。后台迟到 `stopOwned` 命中旧 UCID+PID 后会让 manager
  先向自己 TERM、等待自身退出超时，再向自己 KILL，形成固定周期重启。

### What

- `opencode-manager run` 在连接 Java 前复用现有 FileStore List/Delete 清除上一容器世代的全部进程 state，
  输出 `event=manager_previous_generation_state_clear`；既有 Java `OpencodeProcessAutoRecoveryService` 继续按
  持久 binding 恢复仍有运行意图的用户实例。
- 停止路径增加最终控制面保护：state PID 等于容器 PID 1、entrypoint 父 PID 或 manager 当前 PID 时，只删除
  失效 state 并返回幂等停止，绝不发送 TERM/KILL；普通与 `stopOwned` 共用同一 `stopRecord`。
- 同步 manager README、企业后台排障和内部发布说明，纠正“容器重启后继续识别 PID state”的旧描述。

### How

- 新增真实 helper supervisor 子进程测试，预置 PID 12 state 后验证连接后台前删除、审计日志和正常 TERM；
  `stopOwned` 回归覆盖 PID 1/父 PID/manager PID，断言 TERM/KILL 均为 0。Go 全量测试、`-race` 和 vet 通过。
- JDK 25 下现有 Java 自动恢复测试 3 项通过；launcher/entrypoint 9 项、runtime/Git ignore/AI 文档门禁通过。
- 实际重建 linux/amd64 worker 镜像并通过 OpenCode 1.18.4、Codex/Python smoke；镜像内预置 PID 12 state 的
  真实 manager 运行探针输出 `removedCount=1`，state 消失且进程可正常 TERM。

### Result

- 容器世代 PID 复用不再能让 manager/entrypoint 自杀；上一轮只按资源方向处理并不完整，本轮补上了与现场
  两分钟周期和 PID 12 一致的根因路径。
- 未修改 Java API/事件、数据库/Flyway/MyBatis SQL、环境配置、generated SDK 或 OpenCode 上游源码；Mac
  仅生成了本地验证镜像和 `--no-save/--no-zip` 临时 programs，正式内外层企业交付包仍需按标准流程重建，
  真实 Docker 18.09/linux-amd64 节点仍需完成超过原故障窗口的长稳验收。

## 2026-08-06 - 固定企业 DeepSeek 模型展示优先级

### Why

- 企业公共 JSONC 调整 `enabled_providers` 顺序后，模型选择面板仍可能先显示千问；该字段在前端只转为
  Provider 白名单，OpenCode 原生 `/api/model` 则按模型发布时间降序，不按名称字母或白名单顺序排序。

### What

- 复用 `FigmaChatPanel` 现有模型聚合路径，仅对 `enterprise-deepseek` 和 `enterprise-qwen` 施加稳定展示优先级，
  固定 DeepSeek 在千问之前；其他 Provider 和同优先级模型继续保持 OpenCode 原生目录顺序。
- 新增组件回归，故意输入千问在前的原生目录，验证“上新推荐”和 Provider 分组都先显示 DeepSeek；同步
  `agent-web` README 的模型目录行为。

### How

- 定向 `FigmaChatPanel` 测试 146 passed / 1 skipped，`agent-web` typecheck 和 production build 通过；独立 Vite
  实例在 `http://127.0.0.1:3001/` 启动并返回 HTTP 200。

### Result

- 企业模型排序不再依赖 JSONC 对象插入顺序或缺失 `release_date` 时的并列顺序；不改变用户已保存的模型选择。
- 未修改 HTTP API、RunEvent、数据库/Flyway、后端、环境配置、generated SDK 或 OpenCode 上游源码；企业现场
  仍需重新构建并部署前端交付物，单纯保存 JSONC 不会获得该页面排序修复。

## 2026-08-06 - 修复部署后 OpenCode 原生命令被发送门禁拦截

### Why

- 原生命令虽然已进入前端构建产物，但 `submit()` 在命令解析前统一检查普通消息发送门禁；会话只读、运行中、
  进程未就绪、历史加载或上下文超限时，命令会静默返回。输入框同时被这些状态禁用，导致 `/` 候选也无法打开。

### What

- `FigmaChatPanel` 改为先识别并分发 OpenCode 原生命令，再检查普通消息发送门禁；普通文本和 `!shell` 仍沿用
  原有门禁，远程 Session 命令继续由 `AgentWorkbench` 校验会话身份、只读状态和运行态。
- 输入框在普通消息不可发送时保持可编辑，发送按钮仅对已识别的原生命令放行；保留技能、原生能力、项目命令的
  既有展示顺序。补充进程未初始化、只读会话和夜间任务状态回归，并同步 `agent-web` README。

### How

- 定向组件/工具测试 247 passed / 1 skipped，前端全量测试 1834 passed / 1 skipped；`agent-web` typecheck、
  production build、AI 文档门禁和 `git diff --check` 通过。
- 在当前本地后端返回进程不可用的真实页面中用 Chrome 验证输入框可编辑，输入 `/` 后出现 17 个 OpenCode
  原生命令；生产预览启动于 `http://127.0.0.1:4174/` 并返回 HTTP 200。

### Result

- `/help`、`/new`、`/clear` 等原生能力不再因普通消息门禁失效，普通消息的进程、权限、历史和上下文保护保持不变。
- 未修改 HTTP API、RunEvent、数据库/Flyway、后端、环境配置、generated SDK 或 OpenCode 上游源码；需重新构建
  并部署前端交付物后企业环境才会获得修复。

## 2026-08-06 - 改为由 JSONC 控制模型目录顺序

### Why

- 用户明确要求模型展示顺序不能写死 DeepSeek/Qwen；既有 `enabled_providers` 已经是企业 Provider 白名单，适合
  同时承担可配置的 Provider 分组顺序，避免再增加一套配置或在组件中识别具体厂商 ID。

### What

- `backend-api` 将运行配置 `enabled_providers` 的首次出现位置保留为顺序映射，Model/Provider 两个原生目录在
  过滤白名单后按该顺序稳定排列；同一 Provider 内继续保持 OpenCode 原生返回顺序，未配置或读取失败时不排序。
- 删除 `FigmaChatPanel` 中 `enterprise-deepseek` / `enterprise-qwen` 的写死优先级及其组件测试，改由公共目录客户端
  统一控制；示例 JSONC 调整为 `enterprise-deepseek` 在前，并同步前端、包边界和 HTTP 行为文档。

### How

- 回归用例故意让原生 Model/Provider 目录返回千问在前、配置返回 DeepSeek 在前，验证两个目录均按配置排列；
  `backend-api` 102 passed，模型面板 145 passed / 1 skipped，两个 package typecheck 均通过。
- `agent-web` production build 通过；开发实例在 `http://127.0.0.1:4175/` 返回 HTTP 200；示例 JSONC 可解析，
  `git diff --check` 在提交前通过。

### Result

- 企业现场只需把 `enabled_providers` 写为 `["enterprise-deepseek", "enterprise-qwen"]` 即可优先展示 DeepSeek；
  反转数组即可反转展示顺序，不改变 `model` / `small_model` 默认模型或用户已有本地选择。
- 未新增或变更 HTTP 路径、RunEvent、数据库/Flyway、后端路由、安全、环境变量、generated SDK 或 OpenCode 源码；
  需要部署新前端并让用户 OpenCode 进程重新读取公共 JSONC 后生效。

## 2026-08-06 - 回退模型目录自定义排序

### Why

- 用户确认不应让 `enabled_providers` 同时承担白名单和展示顺序；该字段在 OpenCode 1.18.4 的正式语义只有启用范围，
  平台按数组顺序重排会改变 `/api/model` 原生目录和“上新推荐”含义。
- 进一步验证发现本地 JSONC 的模型 `release_date` 虽被旧配置 schema 和 `/config` 接受，但 1.18.4 的 v1→v2
  配置迁移不传递该字段，平台实际使用的 `/api/model` 对两个企业模型均返回 `time.released=0`。

### What

- `backend-api` 恢复原有 Provider `Set` 白名单：Model/Provider 目录只过滤，不再按 `enabled_providers` 数组排序；
  保持 `FigmaChatPanel` 中没有 DeepSeek/Qwen 厂商硬编码。
- 回归用例把配置顺序设为“千问、DeepSeek”、原生模型顺序设为“DeepSeek、千问”，验证过滤后模型原生顺序不变；
  企业示例恢复原白名单顺序，不保留经真实运行证明无效的 `release_date`。
- 同步 HTTP、前端总览、agent-web 和 backend-api 包文档，记录 OpenCode 1.18.4 V2 目录限制。

### How

- `backend-api` 103 passed，模型面板 146 passed / 1 skipped，两个 package typecheck 和 `agent-web` production build
  通过；Vite 新实例在 `http://127.0.0.1:4176/` 返回 HTTP 200，示例 JSONC 解析通过。
- 使用本机官方 OpenCode 1.18.4 和隔离 `OPENCODE_CONFIG_DIR` 启动真实服务：`/config`、旧 `/provider` 可见配置日期，
  但 `/api/model` 中 Qwen/DeepSeek 的 `time.released` 都是 `0`；临时服务和目录已清理。

### Result

- 提交 `f65ea2474` 引入的公共配置数组排序被后续实现回退，目录恢复原生顺序；更早的厂商硬编码也未重新引入。
- 当前固定 OpenCode 1.18.4 下，单靠 `enabled_providers` 或本地 JSONC `release_date` 不能可靠指定 DeepSeek 优先；
  本次按用户要求只完成相关代码回退，不新增替代排序策略。
- 未新增或变更 HTTP 路径、RunEvent、数据库/Flyway、后端路由、安全、环境变量、generated SDK 或 OpenCode 源码。
- 并行的 slash/compact 修复提交先吸收了本节记录；对应回退实现与文档继续由独立后续提交承载，避免混入其它任务代码。

## 2026-08-06 - 修复 models 候选点击与 compact 长请求

### Why

- `/clear` 已能执行，说明原生命令分发链路整体有效；`/models` 的候选点击继续冒泡到 window 下拉关闭器，导致模型
  面板在同一次点击中刚打开就关闭。
- `/compact` 会同步等待模型生成摘要，但 client 沿用全局 30 秒超时且等待期间没有反馈，企业模型耗时稍长时会被
  浏览器中止或表现为“点了没反应”。

### What

- slash 候选点击阻止冒泡，保留技能、原生能力和项目命令既有分区与键盘行为；`/models` 可稳定打开模型面板。
- compact 发起后立即显示“正在压缩上下文”，并仅为既有 `compactSession` 设置与企业 Nginx 一致的一小时局部
  超时；继续复用平台 Session 映射和 OpenCode summarize 路由，不绕过后端。
- 新增组件、client 超时和真实工作台浏览器回归；同步 `agent-web`、`backend-api` README/PACKAGE。

### How

- 定向 Vitest 350 passed / 1 skipped，前端全量 1835 passed / 1 skipped；`agent-web` typecheck、production build、
  AI 文档门禁和 `git diff --check` 通过。
- 默认 Playwright 浏览器未安装，改用本机 Google Chrome 跑同一条工作台用例，1 passed；真实开发页面确认
  `/models` 面板可见，`/compact` 在无 Session 时能到达父处理器并显示明确提示。前端 3000、后端 health 均为 200。

### Result

- `/clear` 原有可用行为保持不变，`/models` 不再瞬时关闭，`/compact` 在长摘要期间有可见进度且不会被 30 秒
  默认超时提前中止。
- 未修改 HTTP 路径/响应、RunEvent、数据库/Flyway、后端、环境配置、generated SDK 或 OpenCode 上游源码；企业
  现场仍需重新构建并部署前端交付物，当前已部署包不会自动获得该修复。

## 2026-08-06 - 收敛企业原生命令并补齐会话重命名

### Why

- 用户明确只保留 `/sessions|continue`、`/new|clear`、`/models`、`/compact|summarize` 和 `/rename`；此前完整
  OpenCode TUI 命令目录会继续截获 `/help`、`/share`、`/details` 等平台不需要的名称。
- `/rename` 尚未进入 Web 原生命令链，需要复用平台已有 Session 更新能力完成真实改名。

### What

- 原生命令类型、候选目录、别名解析和父级处理器统一缩减为五组白名单；移除旧显示、导出、撤销、分享、主题等
  本地处理分支，Skills 与运行态项目命令分区保持不变。
- `/rename` 使用既有 `updateSessionMutation` 和 Session PATCH 接口，校验非空标题、处理取消与只读状态；标题属于
  Session 元数据，因此允许在 Run 进行期间修改，`/compact` 仍要求 Run 空闲。
- 同步 agent-web README，并新增精确目录、别名、被移除名称、动态项目命令、模型、压缩和重命名浏览器回归。

### How

- 聚焦 Vitest 247 passed / 1 skipped，前端全量 115 个测试文件 1835 passed / 1 skipped；agent-web typecheck 和
  production build 通过，AI 文档门禁与 `git diff --check` 通过。
- 默认 Playwright Chromium 未安装，临时使用本机 Google Chrome 执行同一工作台端到端用例，1 passed；临时配置
  已删除。运行中的前端 3000 与后端 `/actuator/health` 均返回 HTTP 200，后端状态为 UP。

### Result

- 企业原生候选现在只展示五组命令；`/continue`、`/clear`、`/summarize` 保留为指定别名，`/resume` 等旧别名不再
  保留；`/rename` 可更新当前会话标题。
- 未新增或变更 HTTP 路径/响应、RunEvent、数据库/Flyway、后端、安全、环境配置、generated SDK 或 OpenCode
  上游源码；企业现场需要重新构建并部署前端交付物后生效。

## 2026-08-06 - 支持企业全局 OpenCode 模型元数据快照

### Why

- 企业交付另有一份 `opencode-models.json`，但 OpenCode 1.18.4 不会按该文件名从公共 Agent 配置目录自动发现；
  上游实际只通过 `OPENCODE_MODELS_PATH` 读取 models.dev 兼容快照。
- 显式路径文件损坏时，上游会回退内置目录。企业双后台若静默显示不同模型目录，排障和验收都无法确认实际来源。

### What

- worker 管理脚本自动发现宿主机 `/data/testagent/config/opencode-models.json`，或接受
  `TEST_AGENT_OPENCODE_MODELS_FILE` 显式绝对路径；文件只读挂载到容器并设置 `OPENCODE_MODELS_PATH`。
- entrypoint 在 manager 启动前用 `jq` 校验 JSON 根对象并记录 SHA-256，缺失、不可读或格式错误时失败关闭；
  manager 启动的全部用户 OpenCode 子进程继承同一目录路径。
- 同步 worker 模板、Manager/企业部署/OpenCode 1.18.4 文档和脚本回归，明确该文件不属于公共 Agent Git，
  不得包含 provider token、UCID 或内部代理密钥；两台后台必须使用相同 SHA，替换后逐台重启 worker。

### How

- `tools/verify-dev-scripts.sh` 覆盖全局目录 env 和只读 bind mount 参数，开发/部署脚本回归通过；两个 worker
  脚本 `bash -n`、AI 文档门禁和 `git diff --check` 通过。
- 当前前端/backend-api 聚焦回归为 249 passed / 1 skipped，两个 package typecheck 通过；正式 worker 镜像仍由
  随后的企业封包流程重建并执行镜像 smoke。

### Result

- 全局模型元数据有了独立、可审计、双后台一致的加载入口，不再错误依赖 `OPENCODE_CONFIG_DIR` 或用户 HOME。
- 当前仓库、本机 Desktop/Downloads/Documents 和既有节点包均未找到实际 `opencode-models.json`，因此代码与包只
  交付加载能力，不伪造企业模型数据；现场必须把受控原文件复制到 `.4/.114` 固定路径后再重启 worker。
- 未修改 HTTP API、RunEvent、数据库/Flyway、Java 后端、generated SDK、OpenCode 上游源码或 `.env*`；新增的是
  worker 运行配置能力和稳定部署说明。

## 2026-08-07 - 释放 Monaco 模型并限制页面会话缓存

### Why

- 用户反馈工作台长期开启后内存上升并变卡；只读分析确认 CodeEditor 切换文件和卸载时没有销毁全局 Monaco model，
  页面原始输出缓存也只有单会话条数上限，没有会话总数淘汰。
- 用户明确本轮只修复上述两个问题：会话历史每批保留 20 条并渐进加载；WebSocket 生命周期和空闲动画/轮询暂不改。

### What

- CodeEditor 复用现有 URI 模型机制，增加 editor 包内引用计数；切换文件、清空路径或组件卸载时释放引用，最后一个
  同路径编辑器离开后调用 `model.dispose()`，避免已关闭文件继续驻留 Monaco 全局注册表。
- 会话列表继续复用既有“显示更多”分页链路，把首批和后续页大小从 30 收敛为 20；原始输出页面缓存增加最近
  20 个 Session 的 LRU 上限，超限淘汰关联 Run 映射，认证切换或会话删除时同步清理。
- 同步 frontend、agent-web、editor README/PACKAGE 及模型、分页、原始输出边界测试；未修改问题 3 的 WebSocket
  客户端和问题 4 的 Spinner、后台轮询代码。

### How

- Monaco 模型切换、清空、卸载和共享引用，20 会话 LRU 与既有渐进“显示更多”回归均通过；前端全量测试为
  115 个文件、1839 passed / 1 skipped，editor/agent-web typecheck 和 agent-web production build 通过。
- 运行中的 Vite 页面 `http://127.0.0.1:3000` 返回 200；Python Playwright 使用本机 Google Chrome headless 等待
  `networkidle` 后进入登录页，标题为 `TestAgent IDE` 且无 console error。构建仅保留既有大 chunk 警告。

### Result

- 已关闭打开文件产生的 Monaco model 保留链，并把会话历史请求窗口和页面原始输出会话缓存统一限制为 20。
- 未新增或变更 HTTP API、RunEvent、数据库/Flyway、后端、环境配置、generated SDK 或 OpenCode 上游源码；并行的
  runtime-state 终态校准、3001 Playwright 配置等工作区改动未纳入本次实现或提交。

## 2026-08-07 - 修复隔夜任务结束后无法继续追问

### Why

- 页面休眠或断网期间可能错过单 Run 的终态事件；用户级 runtime-state 摘要虽已移除结束的 Run，工作台仍保留
  本地 `RUNNING` 状态，导致白天返回时持续显示停止按钮并锁住发送。
- 首轮校准后继续发现 history session-tree/messages 可能晚于权威 Run 详情恢复一份同轮 `RUNNING` 投影，形成
  “输入框可编辑、仍展示禁用停止按钮、无法发送”的分裂状态。

### What

- runtime-state 新快照不再包含当前 busy Run 时，按精确 `runId` 有限重试读取权威 Run 详情，并复用既有
  `run.succeeded/run.failed/run.cancelled` 终态投影解除发送锁；并发查询按 Run 去重，切换 Session/Run 后丢弃迟到结果。
- 以 `generatedAt >= run.updatedAt` 作为校准前提，较旧快照不能误结束刚启动的新 Run；隔夜校准不补发桌面通知。
- 运行态归并改为让当前 Run 的权威终态覆盖同轮残留 chat busy；新 Run 请求和重试在 HTTP/runtime-state 接管前
  使用既有 pending request 标识继续保持启动锁，不把上一轮终态误用于新一轮。
- 新增真实 Chromium 回归，覆盖详情首次 503 后恢复继续追问、旧快照保持新 Run 运行中，并保留既有 runtime-state
  接管和 outage fallback 用例；历史恢复增加“session-tree 残留 RUNNING、Run 详情已成功”场景。

### How

- `agent-web` typecheck 和 production build 通过；当前工作区 3001 Vite 服务返回 200。
- `follow-up-queue` Vitest 6 条、Playwright runtime-state/历史终态相关 8 条及普通成功终态 1 条用例全部通过；
  3000 被另一并行工作树占用，验证使用临时 3001 配置，配置已删除。

### Result

- 夜间任务在页面外结束后，白天收到最新运行态即可自动收敛真实终态并继续追问；瞬时详情查询失败、历史 busy
  快照晚到都不会继续保留禁用的停止按钮。
- 仅修改前端状态恢复、测试和稳定 README；未变更 HTTP/RunEvent 契约、数据库、后端、安全、环境配置、
  generated SDK 或 OpenCode 上游源码。

## 2026-08-07 - 固化企业 OpenCode 模型目录并增加失败关闭校验

### Why

- 企业现场把只有 Provider/Model 名称和发布日期的 JSON 作为 `OPENCODE_MODELS_PATH` 后，OpenCode 1.18.4
  models.dev 插件直接读取缺失的 `limit`，导致用户实例 `/config`、`/vcs/status` 等接口持续 502；删除宿主文件
  不能改变运行容器已有的只读挂载和子进程缓存。
- 现场公共 `opencode.jsonc` 已把默认/小模型调整为 DeepSeek，Qwen/DeepSeek 上下文分别为
  `200000`/`262144`，但仓库样例和模型目录尚未形成同一可持续交付基线。

### What

- 新增随包 `deploy/internal/opencode-models.json`，固定两个企业 Provider 的 OpenCode 1.18.4 必填元数据；更新
  公共 `opencode.jsonc.example` 的默认模型、上下文、Qwen 首包超时，并直接纳入 `code_analysis` MCP。
- 从 worker 入口原有根对象检查提取共享 `validate-opencode-models.sh`：宿主重建脚本在删除当前容器前校验，
  镜像入口在 manager 启动前复验；Mac 打包额外检查公共 JSONC 与目录的模型、能力、上下文及 MCP 一致性。
  `opencode-worker-docker.sh validate-models` 提供只读现场入口，宿主没有 `jq` 时复用待启动镜像内校验器，
  不删除或重启当前 worker。
- 同步单/多后台执行单、企业 README、HTTP 样例、OpenCode 1.18.4 和 Codex MCP 部署文档；明确目录不含 token、
  UCID 或代理密钥，公共 JSONC 与全局元数据仍是两层独立输入。

### How

- `tools/verify-dev-scripts.sh` 覆盖缺能力/`limit` 的旧目录拒绝、Docker 调用前失败、无 `jq` 宿主只读校验和
  公共配置上下文漂移；Shell 语法、JSON 解析、`git diff --check` 与组件指纹计划通过。
- 使用本机 OpenCode 1.18.4 隔离启动，`/config`、`/provider` 均返回 200，并确认默认 DeepSeek、Qwen
  `200000`、DeepSeek/MCP `262144`；未向企业模型代理发送真实推理请求。
- 实际重建 `linux/amd64` worker 镜像（未导出新企业包），镜像内校验器可执行、固定目录通过且 OpenCode
  输出 `1.18.4`；Apple Silicon 不替代企业 Linux 原生 Codex sandbox 验收。

### Result

- 后续企业包以仓库内同一对公共 JSONC/模型目录为事实源，结构不完整或两份上下文漂移会在替换 worker 或 Mac
  打包前失败，不再把仍可工作的当前容器先删除。
- 本次不变更平台 HTTP 路径/响应、RunEvent、Java、前端、数据库/Flyway、鉴权、generated SDK、OpenCode
  上游源码或 `.env*`；尚未重新生成内外层企业交付 ZIP，真实企业代理推理和两台 Linux 节点部署留待下次打包验收。
## 2026-08-07 - 修复企业环境排查授权选择用户立即 401

### Why

- 企业 Linux/JDK 时钟可产生纳秒精度 `Instant`；问题排查签发把同一个到期时间分别写入 Redis payload 和
  PostgreSQL `timestamp`，后者只保留微秒。授权读取又要求数据库与 Redis 的 `expiresAt` 精确相等，因此数据库
  往返后的微秒值会与 Redis 纳秒值不一致，签发虽成功，第一次选择用户便被误判为“排查授权无效或已失效”。

### What

- `SupportAccessApplicationService` 在派生到期时间前把唯一权威签发时间归一化到微秒，再将同一值写入 Redis、
  PostgreSQL 和响应；授权 ID、会话摘要、到期时间的严格一致校验保持不变，没有引入有效期容差或本机降级。
- 新增纳秒固定时钟回归，模拟 PostgreSQL 微秒持久化往返，覆盖签发后选择目标用户仍能通过授权。
- 同步 system-management README、HTTP API、安全规范、数据库部署说明和多后端故障表，明确所有 Java 节点必须
  使用同一修复版本，不用 sticky、手改 Redis 或放宽鉴权规避。

### How

- TDD 先运行 `SupportAccessApplicationServiceTest`：修复前新增用例稳定抛出“排查授权无效或已失效”，修复后
  6 项通过；随后 `mvn -pl test-agent-system-management -am test` 的 common/domain/system-management 共
  228 项通过，SupportAccess DTO 与 Repository 集成测试各 1 项通过，21 模块跳过测试的真实 JAR 打包成功。
- 使用 JDK 25、未修改的主工作区 `.env.test` 和既有测试数据根，以 `--without-workflow` 启动本 worktree。
  首次检查发现 `8080` 被另一个 worktree 的 18 小时旧后端占用，脚本误把旧 health 当成新进程成功；精确停止
  该旧 screen/Java 后重新启动，确认 PID 23062 的 JAR 路径属于本 worktree，health/readiness 为 UP、前端 3000
  返回 200、登录 CORS 正常。
- 真实浏览器完成登录、三击 Shift、签发、检索并选择目标用户，成功返回 3 个会话和 1 个工作区且无 401；随后
  主动撤销授权。控制台只有既有 `ElTour` Vue 告警，没有排查接口错误。

### Result

- 企业环境即时 401 的时间精度根因已在签发源头修复，严格安全语义不变；本地代码、持久化映射、真实服务和页面
  主路径均验证通过。
- 本地 manager WebSocket 已连接，但共享测试库残留的 4104 进程记录仍触发重复 `PROCESS_NOT_MANAGED`，当前用户
  OpenCode 路径未完全健康；该状态不影响本次已验证的排查授权选择用户接口。
- 本 worktree 数据库日志提示测试库版本 `20260806190500` 高于本分支最新 migration `20260805132000`；本提交应
  合入当前企业发布分支后再构建并同时更新全部 Java 节点，不能直接把该旧分支 JAR 作为企业完整交付包。
- 未修改 API 路径/DTO、RunEvent、数据库结构/Flyway/MyBatis SQL、ticket/RPC、限流、Nginx、Redis key、`.env*`、
  OpenCode 源码或 generated SDK；仅修复安全相关时间兼容行为和对应文档。

## 2026-08-07 - 合并排查授权精度修复并重建企业双后台包

### Why

- 用户要求梳理昨晚 20:00 至今的提交和问题，并以当前发布分支为源码输入重新生成企业离线部署介质；其中排查
  授权时间精度修复仍停留在基于旧提交的独立分支，不能直接把旧分支 JAR 当作当前完整交付。
- 企业宿主明确不提供 `rg`、`jq`；新增模型目录校验虽然已有 Docker fallback，稳定手册仍直接调用依赖 `jq` 的
  底层脚本，需要在封包前收口为无宿主 `jq` 的只读入口。

### What

- 将模型目录失败关闭批次提交到发布分支，并扩展既有 `opencode-worker-docker.sh` 增加 `validate-models` 动作；
  宿主无 `jq` 时复用待启动 worker 镜像内的同一校验器，动作本身不删除或重启当前容器。单/双后台手册同步改用
  该入口。
- 把独立分支的排查授权微秒归一化提交 cherry-pick 到当前发布分支，只合入该最小提交；会话日志冲突保留两边
  完整记录，后端、API、数据库说明和安全规范自动合并。
- 使用现有节点敏感配置包重建固定名内层 `test-agent-internal-release.zip` 和外层
  `test-agent-two-backend-complete.zip`；worker runtime 因模型校验指纹变化完整纳入。用户确认本介质用于现有
  `.4/.114/.2` 升级后，toolbox 保持 `reuse`，不再重复携带指纹未变的镜像、源码和目录；Workflow/LobeHub
  继续禁用。

### How

- `tools/verify-dev-scripts.sh`、相关 Shell 语法、JSON/JSONC 一致性和无 `jq` Docker fallback 回归通过；JDK 25
  执行 `mvn -pl test-agent-system-management -am clean test`，common 96、domain 94、system-management 41，
  共 231 项通过。默认 JDK 17 的首次尝试在加载 Java 21 class 时退出，未计为代码测试结果。
- 完整企业打包重新编译后端和前端，最终 persistence JAR 内 9 个受控主/兼容 migration SHA 全部匹配；
  `linux/amd64` worker 的 OpenCode 1.18.4、Codex CLI 0.145.0、Python 3.13.14、官方 MCP 路由/reply 冒烟通过，
  Docker tar 可重新 load 且镜像架构为 `linux/amd64`。
- 内外层 SHA 配对、ZIP 完整性、外层内嵌 ZIP 与当前内层 SHA 相等、发布包 `--validate-only`、固定名完整包、自动
  节点部署和多后台节点合同回归均通过；本日志提交后按组件指纹状态用 `--zip-only` 重封内层并重建外层，保持
  worker `included`、toolbox `reuse`。该增量包要求现网已有匹配 toolbox；新机或灾备恢复应另行生成全组件包。
- 以上一企业包提交 `ab6e46936`（2026-08-06 15:40）以及用户所述 17～18 点部署时段内的候选提交
  `99517c9b1`（17:13）、`b3f4e9aed`（18:01）分别对比当前 HEAD，PostgreSQL 主/兼容 migration 与 XXL
  migration 均无新增、删除或字节修改。当前三份 8 月 6 日 migration SHA 仍为 `f59f6415...`、
  `1b2547cf...`、`19a0e5af...`；现场仍须从真实 `flyway_schema_history` 证明三条均已成功且 checksum 不变。

### Result

- 当前发布分支已同时包含前端内存治理、隔夜终态校准、排查授权时间精度和完整企业模型目录失败关闭能力；本次
  相对昨日下午 17～18 点已部署包没有新增或改写 PostgreSQL/MySQL migration，不修改 `.env*`、generated SDK
  或 OpenCode 源码。若现场三条 8 月 6 日 migration 已全部成功，本次重部署不应新增 Flyway history；缺失、
  失败、部分执行、未知版本或 checksum 不一致时必须停止，禁止 `repair`、`outOfOrder` 或手改历史表。
- Apple Silicon 不能替代企业两台原生 `linux/amd64` worker 的 Codex sandbox E2E；企业真实 PostgreSQL history、
  模型代理推理、双后台滚动部署、前端浏览器业务验收和节点资源/网络状态仍必须按执行单现场验证，任一 Flyway
  未知 checksum 或首台 Java 校验失败时停止后续节点。

## 2026-08-07 - 修复内部模型流式可观测口径并收拢 BI 看板

### Why

- 旧实现可能把注释、心跳、畸形 data 或只有 role/usage 的 SSE 事件误当首 token，也会被这些事件不断
  延长超时；既有指标只有首 token 和端到端耗时，不能直接回答 curl 中“首输出到完整流”是否
  卡顿，原非流式探活也不能验证模型流完整性。

### What

- 真实代理与供应商探活共用单次解析的 OpenAI-compatible SSE 观测器；只认可展示文本、推理、拒答、工具/
  function call 片段为真实输出，注释、空事件、心跳、元数据、非对象与畸形 data 不计时且不刷新输出截止时间。
- 新增 `streamCompleteMillis` 明细和小时 sum/max/count 聚合，只在先收到真实输出、再收到 `[DONE]` 时记录；
  探活改为真实 SSE，2xx 非 SSE、空流、纯元数据或缺少 `[DONE]` 均失败收敛。
- 增加 Flyway migration 和 PostgreSQL/H2 MyBatis 映射、聚合与升级回归；前端改为顶部统一筛选、中部聚合指标、底部
  调用明细的 BI 布局，Trace ID 作为首列，三类耗时前置并加宽，时间列放最后。

### How

- 共享 SSE 观测、探活、代理/控制器、H2 持久化定向回归共 27 项通过；真实 PostgreSQL 16 Testcontainers 从
  `20260807203000` 基线升级到 HEAD，验证新列、Flyway 成功记录和 `ON CONFLICT` 聚合。
- agent-web typecheck 与 production build 通过；`git diff --check` 和 mock server Python 语法校验通过。构建仅保留
  既有大 chunk 警告。

### Result

- 现在可同时比较端到端、首 token 和流完成耗时，能区分首输出慢、后续输出/上游收尾慢与下游写出慢；
  统一筛选同时作用于聚合和明细。
- 新增的 API 响应字段保持可选，没有新增 HTTP 路径或事件类型；数据库只新增递增 migration，旧数据按 `NULL/0`
  兼容。不记录请求/响应正文，未修改 `.env*`、generated SDK 或 OpenCode 上游源码。

## 2026-08-07 - 修复可观测看板重叠、首屏图表与探活装配

### Why

- 聚合区和调用明细同页后仍继承固定高度 flex 收缩规则，内容超过视口时两个区块被压缩并向外溢出，形成视觉
  重叠；首批统计数据又与 `v-if` 图表容器同时出现，默认 pre-flush watcher 会在 DOM 挂载前尝试初始化 ECharts。
- 使用当前 JAR 做真实重启时发现探活服务保留测试专用构造器后，生产构造器未显式标注，Spring 无法选择构造器，
  应用启动失败；同时需要通过内部代理 Mock 造数验证企业供应商调用的真实统计链路。

### What

- BI 页面改为最外层统一滚动，聚合与明细区块禁止参与固定高度收缩；统计 watcher 改为 post-flush，并在
  `nextTick` 后初始化/重绘四个 ECharts 图表。
- 为 `InternalModelProviderProbeService` 的生产构造器增加 `@Autowired`，并新增 ApplicationContextRunner 回归，
  锁定存在测试构造器时 Spring 仍能成功装配生产 Bean。
- 本地新增独立 `local-mock-observability` 供应商和 `local-observability-mock-model` 模型，通过 19071 Mock 与真实
  内部代理链路写入 18 条成功、HTTP 失败、流中断和探活记录；Mock 最终恢复为健康 SSE 模式，未修改 `.env*`。

### How

- JDK 25 执行 `InternalModelProviderProbeServiceTest`，7 项通过；agent-web typecheck 与 production build 通过，
  构建仅保留既有大 chunk 警告。
- 使用未修改的 `.env.test` 以 `--without-workflow` 重启 backend、opencode-manager 和 frontend；backend health/
  readiness 为 UP，前端 3000 返回 200，CORS 预检正常。工作流初始化因本机 PostgreSQL 用户缺少
  `CREATEROLE/CREATEDB` 权限而按显式开关跳过。
- 真实 Chromium 在 1440×900 下验证聚合区底部 1545、明细区顶部 1569，四个图表实例及 Canvas 均存在，页面
  外层滚动正常、无区块重叠且控制台无报错。

### Result

- 统计看板、图表和明细在同一滚动页面稳定分区显示；服务可正常启动，Mock 数据可直接覆盖成功、首 token、流完成、
  HTTP 失败与流中断口径。
- 本次跟进不新增或变更 HTTP API、RunEvent、数据库结构/Flyway/MyBatis SQL、安全边界或环境配置；同步更新
  agent-web README。19071 Mock 属于显式本地验证进程，后续不需要造数时可停止。

## 2026-08-07 - 补全可观测指标口径说明并修正 QPS

### Why

- 看板已经展示请求量、成功率和三类耗时，但使用者无法从页面直接判断统计范围、哪些调用参与平均值，以及空值
  代表什么；页头原说明夹杂底层传输术语，不利于业务人员理解。
- QPS 原先只计算首末有记录小时之间的间隔，漏掉最后一个小时，连续两个小时的数据会被错误地只除以一小时。

### What

- 新增可复用的指标说明标签，为总览、四张图表、供应商卡片、小时统计表和明细耗时列的全部数值指标增加问号
  提示；说明只表达业务含义、统计范围、计算分母和空值规则，并同时支持鼠标悬停与键盘聚焦。
- 页头改成面向使用者的说明，明确默认查看最近 24 小时用户调用，以及模型未开始回答或未正常结束时显示空值。
- QPS 改为包含首尾小时的完整覆盖时段；只有一个有记录小时仍按一小时计算，并明确它是平均负载而非瞬时峰值。

### How

- 新增组件定向测试，覆盖所有指标均有说明、说明中不出现底层实现术语、鼠标与键盘均可触发，以及两个小时各
  180 次调用折算为 0.05 QPS；测试 1 项通过。
- agent-web 类型检查和生产构建通过，构建只保留既有大文件提示。真实 Chromium 验证 41 个说明入口、4 张图表、
  键盘聚焦提示可见且聚合区与明细区无重叠。
- 使用未修改的 `.env.test` 以 `--without-workflow` 重启 backend、opencode-manager 和 frontend；backend health/
  readiness 为 UP，前端 3000 返回 200，CORS 预检正常，manager WebSocket 正常连接。

### Result

- 使用者可以直接从每个指标旁的问号理解“统计了什么、怎么算、什么情况不参与”，不需要了解采集和存储细节；
  QPS 能正确反映所选时间段的平均请求负载。
- 本次仅调整前端展示和派生计算，不修改 HTTP API、RunEvent、数据库结构/Flyway/MyBatis SQL、安全边界、环境配置、
  generated SDK 或 OpenCode 上游源码；同步更新 agent-web README。工作流仍因本机 PostgreSQL 用户权限不足而跳过，
  不影响本功能验证。

## 2026-08-08 - 统一可观测指标口径、结果大类与用户来源展示

### Why

- 看板仍使用部分非标准或含义不清的指标名称，结果原因直接暴露 13 个底层枚举，不适合运营查看；来源列只显示
  “用户”，无法直接定位实际调用人。
- 复核统计口径时发现原平均请求速率会把尚未发生的当前小时剩余时间计入分母；实机验证又发现可观测 API 被用户
  OpenCode binding 路由误转到离线旧服务器，导致页面请求在到达 Controller 前返回 503。

### What

- 页面统一使用请求数、请求成功率/错误率、端到端请求延迟、RPS、Time to First Token（TTFT）等通用名称；全部
  指标说明改为面向使用者的含义、分母和空值规则。均值继续按 sum/count 加权计算，不从小时均值再次平均，也不
  展示无法由现有聚合还原的 P90/P95。
- 查询范围改为当前小时加之前 23 个小时段，并以实际加载/刷新时刻收口；RPS 使用总请求数除以该真实窗口秒数。
  小时趋势补零，短耗时保留毫秒，成功率和错误率在页面上严格互补。
- 将 13 个精确结果归并为成功、请求或配置问题、上游服务异常、调用方中断、其他异常五类；新增可选
  `outcomeGroup` 明细筛选并用 MyBatis `IN` 查询，原 `outcome` 精确筛选保持兼容且优先。明细同时保留大类和具体
  原因，避免丢失排障信息；API 文档补充说明精确结果综合鉴权/配置/校验、失败阶段、HTTP 状态、流结束状态和调用方
  断开判断，并非只依赖错误码。
- 明细首列展示 Trace ID，三类时长前置并加宽，时间列移到最后；看板在上、明细在下且统一外层滚动。用户调用在
  “来源 / 用户 ID”列直接展示 `ucid`，探活显示“探活”，历史缺失值显示“未知用户”。
- 可观测路径明确排除用户 OpenCode binding 路由，因为其读取共享统计；同步更新 HTTP API、模块图、模块/包
  README 和本地验证说明。

### How

- JDK 25 定向执行结果映射、MyBatis 聚合、Controller 和用户后端路由 45 项测试，全部通过；前端面板与 API client
  105 项测试通过，agent-web 与 backend-api 类型检查通过，`git diff --check` 通过。
- 使用未修改的主工作区 `.env.test`，以 `--without-workflow` 完成后端打包并重启 backend、manager、frontend；
  readiness 为 UP，前端 3000 返回 200。真实 Chromium 验证可观测三类接口均为 200、控制台无错误、16 条 mock
  用户调用显示用户 ID，聚合区在明细区上方且没有重叠。

### Result

- 看板名称、说明、结果分类、统计窗口和用户来源展示已统一，精确原因仍可用于排障；离线旧用户绑定不再影响共享
  可观测查询。
- 新增的 `outcomeGroup` 为向后兼容的可选查询参数；未新增事件或数据库结构，未修改 migration、鉴权、安全策略、
  `.env*`、generated SDK 或 OpenCode 源码。工作流仍因本机 PostgreSQL 用户缺少创建角色权限而显式跳过，不影响
  本功能实机验证。

## 2026-08-08 - 修复模型调用监控趋势图图例与右侧 Y 轴名称重叠问题

### Why

- 在模型调用监控页面中，双 Y 轴趋势图（REQ 与 Success Rate %）的图例放置在右上角（`top: 0, right: 8`），与右侧 Y 轴标题（`name: "SR %"`）在渲染位置上发生重叠，导致图例图标与文字在右上角堆叠显示。

### What

- 修改 [InternalModelObservabilityPanel.vue](file:///Users/kaka/Desktop/intelligent-test-agent/frontend/apps/agent-web/src/components/system/InternalModelObservabilityPanel.vue) 中 ECharts 趋势图配置：将图例改为顶部居中对齐（`top: 0, left: "center"`）并增加 `itemGap: 16`，将 `grid.right` 边距由 `16` 调整为 `44`，确保右侧 Y 轴刻度标签和名称有足够展示空间。
- 将聚合指标图表的卡片表头统一改为中文：`请求数与成功率趋势`（或 `请求数趋势`）、`调用结果分布`、`失败原因分类`、`供应商请求量对比`。
- 参考 NVIDIA GenAI Perf / AI Perf Metrics Reference 标准，在前端面板页首内置“AIPerf & 业界指标英文缩写指南 (Glossary)”卡片，点击“参照 NVIDIA AIPerf 性能指标规范定义 ↗”可直接唤起离线规范弹窗。
- 在缩写指南与离线标准文档 [docs/standards/metrics-glossary.md](file:///Users/kaka/Desktop/intelligent-test-agent/docs/standards/metrics-glossary.md) 中添加 [NVIDIA GenAI Perf / AI Perf Metrics Reference](https://docs.nvidia.com/aiperf/dev/reference/ai-perf-metrics-reference) 官方链接，并将尚未提取 Token 粒度时间戳计算的 `ITL / TPOT` 用醒目的橙色徽章 `<span class="ta-imob-orange-badge">（暂未计算）</span>` 显式标注。
- 在筛选栏与调用明细区增加“按人 (用户 `filterUcid` Select/Input)”与“按时间 (自定义起止时间段 `el-date-picker` Datetimerange)”选择器，支持快捷选择或输入用户 ID 筛选，并支持自定义精确定时检索。
- 消除筛选栏尾部过多空白：移除操作按钮组的 `margin-left: auto` 强行右对齐，使所有筛选下拉框、时间选择器与 `[刷新]` `[探活]` 按钮以 8px 间距紧密连续左对齐排列，并在筛选区与操作区之间加入极细垂直分割线（`ta-imob-filter-vdivider`），排版紧凑连贯。

### How

- 执行 `npx vitest run --environment jsdom tests/internal-model-observability-panel.test.ts`，测试 100% 通过。
- 更新 [docs/README.md](file:///Users/kaka/Desktop/intelligent-test-agent/docs/README.md) 同步索引 `docs/standards/metrics-glossary.md`。

### Result

- 趋势图图例居中展示，所有聚合图表表头均采用清晰中文展示。
- 点击“NVIDIA AIPerf 规范 ↗”胶囊按钮可直接弹出离线对照指南；`ITL / TPOT` 带有明显的橙色“暂未计算”状态标识。
- 时间选择器合二为一，筛选控件与操作按钮紧凑连贯排列，彻底消除右侧大片空白；整体外观符合高端监控工具设计标准。

## 2026-08-08 - 优化内部模型可观测面板筛选吸顶条宽度对齐与留白布局

### Why

- 内部模型可观测面板 `InternalModelObservabilityPanel.vue` 的筛选吸顶条 `.ta-imob-sticky-bar` 使用了 `-16px` 的负外边距，导致其左右两端超出了上、下方卡片和表格的宽度边界；筛选条下方原叠加了 60px 的空旷留白与重复分割线；筛选下拉框宽度偏窄（95px ~ 115px）且操作按钮靠左导致右侧尾部留白过多。

### What

- 移除 `.ta-imob-sticky-bar` 的 `-16px` 负外边距，恢复与上下卡片/图表/明细表格 100% 宽度一致对齐；增加 `border: 1px solid #e5e7eb` 与 `border-radius: 8px` 圆角卡片样式及轻量阴影。
- 调整筛选栏分割线 `.ta-imob-filter-vdivider` 的边距（`margin: 0 4px 0 auto`），将操作按钮组（“刷新”、“探活”）自动推至筛选吸顶栏最右侧，形成双端分布布局，彻底消除右侧大面积无效留白。
- 适当加长各筛选下拉框宽度：按用户 `155px`、供应商 `145px`、结果分类 `145px`、来源 `125px`，让文本显示更加清晰舒展。
- 清理 `.ta-imob-records-section` 的冗余 `padding-top` 和 `border-top`，将 `ta-imob-combined` 间距收敛为标准的 `16px`。

### How

- 修改 `frontend/apps/agent-web/src/components/system/InternalModelObservabilityPanel.vue` 中的对应 CSS 规则。

### Result

- 筛选吸顶条宽度与上下卡片及表格完全齐平，双端分布布局与加宽后的选择框清晰舒展，筛选条下方的边距过渡自然平滑。

## 2026-08-08 - 排查本地 OpenCode 无法启动

### Why

- 当前仓库按 `test` / `.env.test` 启动后，前端访问用户 OpenCode 失败，需要确认是 OpenCode、manager 还是后端启动链路故障。

### What

- 发现当前仓库本次后端启动失败，根因是 `8080` 被另一个 `.claude/worktrees/model-observability` worktree 的旧后端进程占用。
- 当前根目录的 `opencode-manager` 虽然已启动并连接了 `127.0.0.1:8080`，但没有托管的 OpenCode 子进程；对 `4104` 的健康检查持续返回 `PROCESS_NOT_MANAGED`。

### How

- 只读检查进程、端口、screen 会话、manager 状态目录和服务日志；确认当前后端日志明确报 `Port 8080 was already in use`。
- 核对 `restart-dev-services.sh` 的 `backend_pids` 只匹配当前根目录的 `.tmp/dev-services/backend-runtime`，不会自动回收其他 worktree 的后端进程。
- 未停止 PID 78329 所属的其他 worktree 服务，避免误杀用户正在使用的并行工作树。

### Result

- 本地 OpenCode 起不来的直接原因不是 OpenCode 二进制或配置，而是当前仓库后端未真正启动，manager 误连到了旧 worktree 后端；`/actuator/health` 的 200 也来自该旧进程，不能作为当前根目录后端已启动的依据。
- 后续清理或停止旧 worktree 的 8080 服务后，再按项目标准 `test` 启动链重启并复核 manager 与 OpenCode 端口。

## 2026-08-08 - 兼容并行 worktree Flyway 历史并恢复本地启动

### Why

- 用户确认重启后，清理旧 worktree 后端仍发现当前 `.env.test` 数据库已执行内部模型可观测旧版本
  `20260807130134`、`20260807203000`、`20260807222227`，而当前分支只保留重编号版本，Flyway 因 unresolved/applied
  与低版本撤销重发候选缺失而拒绝启动。
- 默认 Workflow 准备还因本机密钥文件缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 失败；前端配置已禁用 Workflow，因此本次按
  脚本支持的 `--without-workflow` 恢复 Java、manager 和前端三服务。

### What

- 复用现有 `DatabaseMigrationCompatibilityCustomizer` 和主 migration 资源过滤器，增加内部模型旧三版本的原始字节隔离
  解析，并过滤对应的三个重编号主目录资源；部分旧历史、部分新历史、新旧混用均 fail-closed。
- 当 `V20260807190000__create_run_resends.sql` 缺失但后续版本已执行时，分别提供批量归因前
  `V20260807229999` 与批量归因/当前内部模型后 `V20260808143303` 两条顺序补偿路径；若某条补偿版本已经落库，后续启动
  固定沿用同一路径，主 migration 与补偿同时存在或两个补偿同时存在时拒绝启动。
- 增加真实 PostgreSQL 升级与二次启动回归，覆盖批量归因前后两条路径；同步 `docs/deployment/database.md` 的选择规则、
  SHA-256 和禁止 `repair`/`outOfOrder` 约束。

### How

- 内部模型旧三条兼容 SQL 与旧 worktree 字节一致，SHA-256 分别为
  `f214dfd0d4f26de830452d9f4121bc938cf031e4867555d5248e159d99377084`、
  `de7188e3ba5d01148a655dbc238783cf7881abf168bd7b6e422c9f2fa118a5c3`、
  `46f0a8e687f59c037a7e02cb1f9ba3db4893633ba20edd67d4ae75f0b6fd0d9e`；两个撤销重发补偿 SQL 与主候选字节一致，
  SHA-256 为 `ca044d9819c7259b62e29243e9d72a06a2f01a532f1803f37e117de1d2f5d83d`。
- JDK 25 下执行 `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest`，11 项全部通过；最终应用 JAR 的
  persistence 嵌套 JAR 已确认包含五个 compatibility SQL。
- 使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build --without-workflow` 全量打包并重启；
  最终再次重启验证已落库的 `20260807229999` 可持续解析，未执行 `repair`、未启用 `outOfOrder`、未手工改 history。

### Result

- 当前根目录后端 runtime JAR 正常监听 8080，health/readiness 均为 UP；前端 3000 返回 200，登录 CORS 正常，
  `opencode-manager` 已连接后端 WebSocket 且无重连循环。数据库通过 Flyway 正常新增 `20260807229999` 和
  `20260807230000`，旧三条 checksum 保持不变。
- 未修改 `.env*`、HTTP API、RunEvent、generated SDK 或 OpenCode 源码；未跟踪的 `demo/` 未修改且不纳入提交。
- Workflow 尚未启动，若后续需要该控制面，必须先补齐本机 `WORKFLOW_DEV_REDIS_PASSWORD`。

## 2026-08-09 - 允许当前 Flyway 迁移链断点续跑并补充多人协作门禁

### Why

- 上一轮兼容提交 `7195257b5` 为防止内部模型新旧迁移链混用，误把“当前三条 migration 只执行了一部分”也判为
  非法历史；Flyway 逐版本提交时，进程正常中断在 `V20260808143300` 或 `V20260808143301` 后会因此无法重启续跑。
- 多人功能分支各自创建时间戳只能降低同号概率，不能保证合并顺序和部署顺序，需要把最终版本分配移到串行集成门禁。

### What

- 删除当前内部模型 migration 部分执行即拒绝启动的判断；部分旧历史和新旧版本混用仍保持 fail-closed，完整旧历史继续
  使用字节级兼容目录，当前主迁移链则按默认顺序执行剩余版本。
- 新增两个真实 PostgreSQL 回归场景，分别从只完成 `V20260808143300` 和只完成 `V20260808143301` 的 history 重启，
  验证最终执行到 `V20260808143302`、不加载旧兼容目录且 `outOfOrder=false`。
- 数据库规范补充候选/最终版本两阶段流程：功能分支使用独享临时数据库或 schema，合入时由单一集成人或 merge queue
  串行分配最终版本，CI 以目标库 history 和版本/文件/SHA-256 清单锁定已冻结 migration；可选全局单调序号但不能用
  UUID、repeatable migration、`outOfOrder` 或 `repair` 替代顺序治理。

### How

- JDK 25 下运行 `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest`，13 项全部通过；运行
  `FlywayMigrationNamingTest`，8 项全部通过。
- 使用未修改的 `.env.test` 与 `--without-workflow` 全量打包并重启 backend、opencode-manager、frontend；backend health/
  readiness 为 UP，前端 3000 与 CORS 正常，manager 将用户 OpenCode 拉起到 4104，`/global/config` 返回 200。
- 最终运行 JAR 中旧内部模型三份兼容 SQL 与两份撤销重发补偿 SQL 的 SHA-256 均与数据库文档锁定值一致；未执行
  `repair`、未打开 `outOfOrder`、未手工修改 history。

### Result

- 当前主迁移链可从合法中断点恢复，不再因上一轮过严保护影响其他开发者重启；未知旧分叉、部分旧链和新旧混合链仍会
  被明确阻断，避免静默污染数据库历史。
- 本次不新增或改写 migration SQL，不变更 HTTP API、RunEvent、数据库结构、MyBatis SQL、安全边界、`.env*`、
  generated SDK 或 OpenCode 源码；未跟踪 `demo/` 保持不变且不纳入提交。Workflow 仍因缺少本机密钥而显式跳过。

## 2026-08-09 - 校正个人本地数据库下的 Flyway 合并规则

### Why

- 前一条记录和数据库文档曾建议“每个 worktree 临时库、合并期自动编号、CI 清单和 merge queue”，但当前项目并未实现
  这些能力；实际没有共享开发数据库或中央协调系统，每个开发者在需要保留的个人本地数据库上执行 migration。
- 两个尚未推送的本地分支无法感知对方版本，合并期重新编号会使已经执行旧编号的个人库与源码不一致，因此该建议不能
  作为当前协作规则。

### What

- `AGENTS.md`、persistence README 和数据库部署文档统一改为当前真实流程：开发时间戳只能区分候选，并行分支在合并时由
  集成人收集全部 migration 与相关个人本地库/目标库 history，统一设计主链和兼容路径。
- migration 只要在任何需要保留的个人本地库或目标库执行，就冻结版本、文件名和原始字节；只有从未执行的候选，或数据库
  所有者明确同意废弃并重建个人库的历史，才能在合并时重命名或重排。
- 已执行历史无法直接汇成严格递增主链时，继续复用现有 `DatabaseMigrationCompatibilityCustomizer`、隔离 compatibility
  location 和更高版本前向 migration，并用真实 PostgreSQL 覆盖每套需保留历史；不引入不存在的中央编号、CI 或 merge queue。

### How

- 对照当前自检、命名测试、兼容装配和上一轮真实本地分叉，删除文档中四条未实现能力，并统一替换“未进共享库即可改号”
  的表述；`git diff --check` 校验通过。

### Result

- 后续合并不会再假设个人本地数据库可随意重建，也不会把自动编号描述成现有能力；版本冲突只能在合并期按已知真实 history
  显式兼容，数据库所有者明确选择重建是唯一可跳过该历史的例外。
- 本次仅修改规范、模块 README、数据库部署文档和本机会话记录，不修改代码、migration SQL、API、事件、数据库结构、
  `.env*`、generated SDK 或 OpenCode 源码。

## 2026-08-09 - 在独立 worktree 建设 QA Agent 长期记忆 V1

### Why

- 需要让 QA Agent 跨会话复用测试人员稳定的工作习惯，同时把个人记忆、Application 团队记忆、项目业务知识、原始聊天、
  静态画像和可发布 Skill 明确分层，避免把聊天镜像或项目事实误当成用户画像。
- V1 明确使用自托管 Mem0 与本地 CPU Embedding，并为未来企业 Embedding 保留 Provider/新集合迁移边界；既有对话必须在
  默认空白名单、记忆依赖超时或故障时继续运行。

### What

- 从 `93d1a8610` 创建独立 worktree `/Users/kaka/Desktop/intelligent-test-agent-memory-v1` 和分支
  `codex/qa-agent-memory-v1`；按六个批次增加 `test-agent-memory` 领域模块、MyBatis/Flyway 治理表、Mem0 2.0.3 服务、
  固定 BGE 512 维离线模型、学习 Outbox、mfg 授权、检索注入、团队审核、Skill 提案、用户/管理 API 和记忆中心页面。
- 原始聊天仍由现有 OpenCode Session/恢复链路保存；学习 worker 只瞬时读取当前根 Run，Mem0 自定义 history manager 不保存
  messages。项目知识不进入记忆系统，画像仅为有效记忆汇总视图，Skill 仍走既有文件 WebSocket、Git 和发布流程。
- 新增 opt-in `deploy/dev/memory-compose.yml` 与 `tools/memory-dev-services.sh`：生成两个 `0600` 运行文件、隔离 pgvector
  密码、关闭运行期模型联网/telemetry、保留数据卷，并以带鉴权 readiness 的 `UP + rawMessageCount=0` 作为启动门禁。
  实跑发现繁忙 Docker Desktop 的 CPU 模型加载会晚于普通 health 窗口，已移除 `compose --wait`，改由鉴权 readiness 最多
  等待 7 分钟，并补充行为回归。
- `restart-dev-services.sh --with-memory` 在构建成功后才替换标准端口服务；修复跨 worktree 的完整 screen 会话关闭、Java
  后端进程发现和本 worktree 不可变运行 JAR 归属校验，避免把旧 worktree 的 8080 readiness 误报成当前启动成功。
- 平台 migration `V20260809120000__create_qa_memory_governance.sql` 已执行并锁定，SHA-256 为
  `b2ae5639284208be8bc09952d9143c3dd0d8a2bf649b6601aed4225e586af18a`；源码、persistence 嵌套 JAR 与运行应用 JAR
  字节一致。未修改 `.env*`、generated SDK、OpenCode 源码或 RunEvent SSE。

### How

- Python 合同/单元测试为 14 passed / 1 skipped；真实 memory-service + pgvector + BGE 完成中文 add/search/update/history/
  delete、进程重启恢复、512 维集合和 `rawMessageCount=0` smoke，合成数据已清理。容器最终为 UID/GID 10004、只读根、
  `cap_drop=ALL`、`no-new-privileges`，pgvector 为 0.8.1。
- JDK 25 下记忆相关定向反应堆通过：memory 24、model gateway 17、integration 38、MyBatis 真实 PostgreSQL 空库 migration
  1 项等均通过；显式临时库完成后已删除，保留的 `.env.test` 数据库由 Flyway 正常升级至 `20260809120000`，白名单为 0。
- 前端全量为 1886 passed / 1 skipped，agent-web typecheck 与生产 build 通过；真实 Chromium 验证个人/团队/Skill 三个 Tab、
  evidence drawer、历史 Run 记忆徽标、系统管理、暗色、键盘与窄屏页面。
- 精确全量 Maven 命令未通过：第三次运行在前 17 个模块成功（其中 memory 24/24）后，既有
  `DefaultXxlJobAdminContextLauncherTest` 的 `mysql:8.4` Testcontainers 三次都未在 120 秒内完成冷启动，业务断言前报
  `ContainerLaunchException`，因此 API/persistence/app 被 Maven 跳过；该类独立复核也在同一容器启动阶段失败。此前
  `XxlJobMysqlMigrationTest` 4/4、readiness 2/2 及受影响定向测试已通过，不能据此把全量结果记为成功。
- `tools/memory-dev-services-test.sh`、`tools/verify-dev-scripts.sh`、`tools/verify-ai-docs.sh`、Compose config、migration/JAR
  哈希及 `git diff --check` 通过；使用未修改的绝对路径 `.env.test` 执行计划中的 JDK 25 完整重启命令成功。

### Result

- 当前独立 worktree 的 backend `8080`、frontend `3000`、manager、memory-service `18888` 和 pgvector `15433` 均在运行；
  backend readiness、前端 HTTP、manager WebSocket、Docker health 和记忆鉴权 readiness 正常，原始 messages 数为 0。
- 既有用户仍可立即测试普通对话；记忆白名单默认空，不会自动学习或注入。测试记忆能力前需由超级管理员加入用户白名单，
  并设置固定内部 CHAT 模型或使用已通过 CHAT 探测的内部 Run 模型。
- 实现与记忆链路已按定向、真实数据面、真实 PostgreSQL 和页面完成验证，但后端全量仍受 Docker/MySQL Testcontainers
  冷启动超时阻断；此外未使用真实登录凭据执行“两用户、两 Application”的跨会话端到端验收，仍需用户在灰度白名单中验收。
- 最终保持六个约定的中文提交，不推送、不创建 PR、不合并回发布分支。

## 2026-08-09 - 修正记忆 worktree 的 OpenCode 本地运行数据路径

### Why

- 按记忆部署文档从独立 worktree 启动后，后端与 manager 默认把 `TEST_AGENT_ROOT`/`SYS_DATA_ROOT_DIR` 指向
  `intelligent-test-agent-memory-v1/.testagent`；该目录为空，而用户公共配置与 session 仍保存在主工作区，正式初始化报
  `公共 Agent 配置源目录不可用`，4104 未被 manager 管理。

### What

- 不修改启动脚本、`.env.test`、OpenCode 源码或用户 Agent 配置；部署文档改为在独立 worktree 启动前显式复用主工作区的
  `TEST_AGENT_ROOT`、兼容别名 `TESTAGENT` 和 `SYS_DATA_ROOT_DIR`，继续使用启动脚本现有可覆盖能力。

### How

- 使用 JDK 25、主工作区绝对路径 `.env.test` 和 `--with-memory --without-workflow` 从记忆 worktree 重启；脚本确认运行数据根为
  `/Users/kaka/Desktop/intelligent-test-agent/.testagent`，backend、manager、frontend、memory-service 与 pgvector 均正常启动。
- 后端日志确认公共配置 Git 根已解析回主工作区，原有用户公共 worktree 的未提交/未跟踪改动保持原状，未执行清理、回退或合并。

### Result

- 原“公共 Agent 配置源目录不可用”启动阻断已消除；当前用户进程仍为 `STOPPED`，因页面登录态失效尚未执行新的已认证
  `/processes/me/initialize`，所以 4104 的最终 `RUNNING` 与 `/global/config` 200 闭环仍待用户重新登录后复测。

## 2026-08-09 - 为工作台活动栏补齐稳定 URI

### Why

- 记忆中心已有 `/memories` 深链接，但工作台、控制台、能力库和设置仍主要依赖组件内状态；切换后地址栏无法表达当前页面，
  刷新、登录回跳和浏览器前进/后退也不能统一恢复。

### What

- 复用既有 `WorkbenchView`、vue-router 和 `toolbox-navigation.ts` 状态机，统一提供 `/workbench`、`/toolbox`、
  `/memories`、`/system`、`/hub`、`/settings`；旧根路径 `/` 兼容跳转到 `/workbench`。
- 控制台与能力库纳入既有沉浸式路由权威保护，后台 Diff/SSE 更新不能切走当前页面；设置活动栏入口支持深链接，关闭普通入口
  返回原页面，直接访问时安全回到工作台。同步登录回跳白名单、404 首页动作、稳定文档和路由回归。

### How

- `toolbox-navigation.test.ts` 与 `login-redirect.test.ts` 共 10 项通过；`@test-agent/agent-web` typecheck 和 production build 通过，
  构建仅保留既有大 chunk 提示；`tools/verify-ai-docs.sh`、`git diff --check` 通过。
- Chromium 定向回归分别验证全部活动栏 URI/根跳转/设置深链/历史恢复、工具箱布局恢复、记忆中心布局恢复，共 3 项通过；
  当前 `127.0.0.1:3000` 开发服务的 6 个页面 URI 均返回 HTTP 200。

### Result

- 左侧页面级入口现在都可复制、刷新和通过浏览器历史恢复；旧 `/` 入口保持兼容，不新增页面组件或第二套路由状态。
- 本次不变更 HTTP API、RunEvent、DTO、数据库/Flyway、后端、性能或安全策略，未修改 `.env*`、generated SDK 或 OpenCode
  源码；工作树中同期存在的 `MemoryAdminPanel.vue` 及其测试修改保持未暂存，不纳入本次提交。

## 2026-08-09 - 将记忆管理员用户与模型配置改为可搜索选择

### Why

- 记忆白名单要求管理员手填平台 `userId`，固定抽取模型也要求手填模型 ID，容易输错且无法判断对象是否真实存在、是否可用。
- “当前 Run 内部模型回退”和常驻展示的 revision、collection version、归一化属于实现术语，管理员难以理解开启条件、失败影响
  以及这些技术字段的用途。

### What

- `MemoryAdminPanel.vue` 复用现有平台用户目录，添加白名单改为按姓名、用户 ID 或统一认证号远程搜索，只提交选中用户的真实
  `userId`，并过滤停用用户和已在白名单中的用户。
- 固定抽取模型复用现有内部模型供应商目录，只展示供应商启用、凭据可用、模型启用且实际探测到 `CHAT` 能力的模型；目录临时
  不可用时保留既有配置，避免读取失败把当前策略清空。
- 将回退开关解释为“固定模型不可用时，使用当前任务的内部模型”，明确外部模型不会参与、条件不满足只影响异步记忆提取而不
  影响当前测试任务；Embedding 版本、集合与归一化默认折叠，并逐项补充升级/排障用途说明。
- 同步 agent-web README/PACKAGE 与前端总 README；工作期间并行的稳定路由提交 `9d6e469cd` 推进了 HEAD，并已包含两处
  README 说明，本提交保留其成果，只纳入剩余组件、测试、PACKAGE 与本日志。

### How

- `memory-admin-panel.test.ts` 新增内部模型选择、平台用户远程搜索/真实 ID 提交和技术信息按需展开回归，定向 3/3 通过；
  agent-web typecheck、production build 通过。
- 前端全量首轮与生产构建并行时，既有 Markdown/Mermaid 懒加载 4 项超时；该文件独立 12/12 通过，取消并行后全量稳定为
  122 个文件、1888 passed / 1 skipped。
- 使用 JDK 25、未修改的主工作区绝对路径 `.env.test`、共享 `TEST_AGENT_ROOT/TESTAGENT/SYS_DATA_ROOT_DIR` 和
  `--with-memory --without-workflow` 从独立 worktree 完整重启；backend health/readiness、frontend 3000、CORS、memory-service、
  pgvector 均正常，鉴权 readiness 保持 `rawMessageCount=0`。
- 真实登录页验证模型下拉加载 4 个 CHAT 探测成功模型，用户搜索“88”返回姓名、统一认证号与真实 ID；未确认保存或添加，生产
  配置和白名单未发生变化。技术信息展开内容和页面视觉已检查。

### Result

- 管理员不再记忆或手填用户/模型 ID，页面直接约束到系统当前可选对象；备用模型失败边界与向量技术字段用途可在页面内读懂。
- 重新登录触发受管初始化后，先前待验证的用户 OpenCode 进程已由 manager 在 4104 启动，后续健康检查均为 `HEALTHY`，
  `/global/config` 返回 200；manager 曾因后端连接切换断开一次，10 秒后自动恢复且没有重连循环。
- 本次不变更 HTTP API、RunEvent、共享 DTO、数据库/Flyway、后端服务、权限模型或安全边界，未修改 `.env*`、generated SDK、
  OpenCode 源码，也未推送、创建 PR 或合并分支。

## 2026-08-10 - 通用化长期记忆并增加多节点与 CPU Embedding 热备

### Why

- 原实现绑定 QA 任务分类、自定义抽取提示词和单节点本地向量模型，无法直接复用 Mem0 原生记忆能力，也无法满足企业无
  Embedding、Mem0 横向扩容和模型故障切换要求。
- 记忆证据只展示摘要，缺少原始 Session 标题、ID 与所有者访问入口；既有离线包也没有独立记忆库、CPU Embedding 和多副本
  Mem0 的可审计交付物。

### What

- 将服务锁定到 `mem0ai==2.0.17`，学习只调用一次原生 `add(messages, infer=true)` 且不传自定义 prompt；删除 QA 分类、候选、
  自定义置信度和语义过滤，Java 统一改用 `/api/internal/platform/memory/v1/**`，旧 `/qa-memory/v1/**` 明确返回
  `410 API_GONE`。
- 新增独立 pgvector/Alembic 控制面、三副本无状态 Mem0/VIP、稳定 `logicalMemoryId`、双 profile 隔离 collection、advisory lock、
  幂等 outbox 投影与 RRF 去重；企业向量不可用时使用 CPU profile，双 profile 都失败时保持 2 秒 fail-open。
- 将固定 revision 的 `BAAI/bge-small-zh-v1.5` 拆成独立 OpenAI-compatible CPU 服务，提供批量 embedding、模型/类型校验、
  L2 归一化、仅 query 加前缀、有界并发和 API Key 鉴权；Mem0 到 Java 模型网关使用带 nonce 的 HMAC。
- 证据补齐 `sessionId/sessionTitle/transcriptAvailable/runId` 并复用 `/s/{sessionId}` 所有者权限；团队记忆保持手工提交和
  APP_ADMIN 审核。同步前端记忆中心、管理员 profile/积压展示、HTTP/API/事件/数据库/部署/安全/测试文档。
- 新增开发集群、真实浏览器 E2E 场景、并发/故障/存储审计脚本，以及 linux/amd64 Mem0、CPU BGE、pgvector、Nginx 离线镜像、
  SHA256、SPDX SBOM、许可证、模型身份和 Alembic 交付清单；不改已执行 QA migration 的字节和 checksum。

### How

- Python：记忆服务 25 passed / 1 integration skipped，Embedding 4 passed；另用真实 PostgreSQL/pgvector 跑集成测试 1 passed。
- Java：JDK 25 下 model-gateway 13、memory 26、API 9 项通过；另用临时真实 PostgreSQL 跑 MyBatis/Flyway 集成测试 1 passed。
  更宽 Maven reactor 到无关 `test-agent-xxl-job-integration` 时，被 Docker/QEMU 下 MySQL 8.4 启动超时阻断。
- 前端：workspace typecheck、production build 和全量 Vitest 通过（122 files，1891 passed / 1 skipped）；记忆定向 3 files / 10 tests
  通过，Playwright 能发现 4 个真实记忆场景，但当前没有企业地址、账号和节点控制 hook，未伪造企业浏览器验收结果。
- 真实启动本地独立 pgvector、CPU BGE、Nginx VIP 和三个 Mem0 副本：CPU 批量结果均为 512 维且范数约 1；逐台停止副本时 VIP
  连续可用，恢复后三副本健康；存储审计确认版本单调、无投影积压/原始对话字段、只读容器文件系统和日志 canary 泄漏。
- `memory-dev-services-test.sh`、离线包静态测试、AI 文档校验、shell 语法、`git diff --check` 与全部离线 SHA256/SBOM/镜像架构/
  模型身份/Alembic head 校验通过。最终完整离线包位于 `/private/tmp/testagent-memory-offline-final.ZtHLJ4/memory`，约 1.8G。

### Result

- 通用记忆、多节点共享存储、CPU Embedding 单 profile/双集合热备、证据回链和离线交付代码已实现并在本机真实数据面验证。
- 尚未完成企业真实 `.2 → .4/.114 → Mem0 VIP → 记忆库 → Java 模型网关 → 企业模型/CPU BGE` 浏览器验收和批准容量压测；
  发布白名单不能据此开启，必须在拿到目标环境参数后执行脚本中的全量门禁。
- 未修改 `.env*`、generated SDK、OpenCode 源码或工作区中同期的 Figma/Git 面板与聊天重发改动；旧 migration checksum 保持
  `b2ae5639284208be8bc09952d9143c3dd0d8a2bf649b6601aed4225e586af18a`，新增前向 migration SHA-256 为
  `2740ff6d4a97c5b8a4c438586f55d58078c3cfce93b06e4efeb6b77b039c66c3`。

## 2026-08-10 - 扩充通用记忆多节点端到端发布门禁

### Why

- 原真实浏览器套件只覆盖四个主场景，故障编排主要验证存量记忆召回，尚未证明只剩一个 Mem0 副本、单个 Java 节点或企业
  Embedding 中断时仍能从浏览器完成新记忆学习，也缺少治理版本、旧 API、原始对话权限、投影积压和并发隔离的完整验收。

### What

- 将真实 Playwright 套件扩为七个场景：原生学习与跨会话/团队 ACL、个人记忆新增编辑提升暂停归档、故障中学习与同 ID 召回、
  投影积压可见、双 profile fail-open、投影恢复、逐 actor 基线召回与两阶段并发；浏览器状态文件以 `0600` 保存非敏感 ID，供后续故障阶段复用。
- 多节点脚本按“Mem0 仅余一副本、Java 节点逐台、企业 Embedding、CPU、双 profile、扩缩容”顺序执行真实浏览器学习/召回，
  并新增静态编排回归锁定 17 个阶段、节点控制 hook、状态传递和最终门禁摘要。
- 数据面审计增加 Alembic head、逻辑版本/history、投影版本/outbox、原生操作幂等、collection 内逻辑 ID 唯一、向量维度、容器非
  root/只读/capability/tmpfs、原始对话 canary 与密钥日志泄漏检查；同步部署、测试场景和前端测试说明。

### How

- 记忆定向 Vitest 为 3 files / 10 tests，workspace 15 项 typecheck 通过；Playwright 可发现七个真实场景，严格 TypeScript 编译通过。
- `memory-cluster-e2e-test.sh`、`memory-dev-services-test.sh`、shell 语法、AI 文档校验和 `git diff --check` 通过；项目未提供可执行
  eslint 命令，因此没有把 eslint 记为已运行成功。
- 本地真实独立 pgvector、CPU BGE、Nginx VIP 和三个 Mem0 副本保持健康，增强后的 `--audit` 对当前数据面全部通过，包括
  collection 512 维、无重复逻辑记忆、无投影积压、容器安全和原始对话/密钥 canary 检查。

### Result

- 发布门禁现在能从浏览器证明故障期间仍可学习、恢复后不二次抽取并补齐相同 `logicalMemoryId`，同时覆盖治理、授权、性能、
  幂等、存储和安全边界；没有新增或变更生产 API、RunEvent、数据库 migration、SQL 或运行时实现。
- 企业真实 `.2 → .4/.114 → Mem0 VIP → 记忆库 → Java 模型网关 → 企业模型/CPU BGE` 七场景及批准容量 p99 仍未运行；当前缺少
  目标 URL、测试账号和节点/模型控制 hook，不能据本地验证开启企业记忆白名单。
- 未修改 `.env*`、generated SDK 或 OpenCode 源码；工作区中同期的 Figma/Git 面板和聊天回归改动继续保持未暂存。

## 2026-08-10 - 补齐通用记忆功能、非功能与易用性测试

### Why

- 既有发布门禁已经覆盖原生学习、团队批准、故障降级和单轮并发，但仍缺少跨 Application 隔离、个人范围变化后的实际召回、
  团队拒绝、浏览器登录态越权、真实管理写链和基础易用性；单轮 p99 样本也不足以支撑批准容量，重启既有副本不能证明扩容。

### What

- 扩展真实 Playwright `full`：创建主/隔离两个 Application，验证个人/团队 Application 记忆不越界，同一个人记忆在编辑后应用内
  命中、提升全局后跨应用命中、暂停/归档后不再注入；增加团队带原因拒绝及拒绝后不召回。
- 普通成员除页面无审核/原文入口外，还在真实浏览器登录态直接请求他人个人记忆 GET/PATCH、团队 review 和管理 health，锁定
  `403/404` 权限边界；超级管理员从真实页面检查全部 profile/死信/白名单并用当前值完成一次版本化策略保存。
- 增加 640 CSS px（等效 1280px 屏幕 200% 放大）、Reduced Motion、Tab/Enter 页签与 Escape 详情回归；组件测试补齐团队拒绝原因、
  列表/管理加载失败重试、HTML-like 文本不执行和 2000 字输入上限。
- 并发场景增加 `--rounds`（1–20），首轮并行学习、后续纯召回，聚合全部样本 p50/p95/p99/max、每轮请求发散和 Run/Session 唯一性；
  企业故障门禁新增 `TEST_AGENT_MEM0_SCALE_OUT_CMD/TEST_AGENT_MEM0_SCALE_IN_CMD`，要求真正增加并移除无状态副本。
- 同步 agent-web README、部署准入和对话场景文档，并明确在途精确故障、记忆库/VIP 切换、24 小时耐久、浏览器矩阵和读屏仍需专项证据。

### How

- `corepack pnpm exec vitest run apps/agent-web/tests/memory-center.test.ts apps/agent-web/tests/memory-admin-panel.test.ts packages/backend-api/tests/qa-memory.test.ts`：
  3 files / 14 tests 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过。
- `TEST_AGENT_RUN_MEMORY_E2E=0 corepack pnpm exec playwright test --config playwright.real.config.ts apps/agent-web/tests/memory.real-spec.ts
  --project chromium --workers 1` 成功编译并发现 9 个真实场景，因未提供真实环境开关而按设计 9 skipped；`bash -n`、
  `tools/memory-cluster-e2e-test.sh` 和 `git diff --check` 通过。
- 当前 worktree 前端以 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 4317` 启动，`/memories` 返回
  HTTP 200 和 `TestAgent IDE` 页面骨架。

### Result

- 当前方案声明的功能主链、权限、短时容量/扩缩容和基础易用性门禁已显著补齐；没有变更生产 API、RunEvent、DTO、数据库/Flyway、
  运行时、安全实现或兼容接口，只修改测试、编排和稳定文档。
- 企业真实链路、批准容量和新增专项非功能项仍未运行，缺少目标 URL、账号、模型与停启/扩缩容 hook，不能据本地结果声称完全覆盖或
  开启白名单；未修改 `.env*`、generated SDK、OpenCode 源码及同期 Figma/Git/聊天未暂存改动。

## 2026-08-10 - 修复通用记忆一致性、授权与降级写入边界

### Why

- 重新审查发现跨 Java 节点对同一记忆并发修改时，平台治理状态与 Mem0 操作缺少统一事务/行锁顺序；原生学习也可能让同一个
  `mem0_memory_id` 生成多条治理记录。创建失败补偿还复用了 ADD operationId，存在被幂等层误判为原操作的风险。
- 团队记忆普通编辑错误携带 `TEAM_APPLICATION` 作为范围迁移，Mem0 会按“只允许个人范围调整”拒绝；正文服务降级时，前端又可能
  把截断的 `displaySummary` 当完整正文编辑或提交。成员退出 Application 后仍可凭提案创建人身份读取 Skill 草稿。

### What

- 记忆编辑、暂停、个人范围提升、归档和团队审核统一增加 Spring 事务与 `SELECT ... FOR UPDATE`；事务内先写未提交治理状态，再执行
  同 operationId 可重放的 Mem0 操作，外部失败回滚平台状态。归档/拒绝删除后不再读取已删除正文，创建补偿改用独立 DELETE operationId。
- 普通编辑不再发送 scope/applicationId；`contentAvailable=false` 时 API 的 `content` 固定为空，前端禁用编辑和团队提交，只把
  `displaySummary` 用于展示。Skill 提案列表改为始终要求当前有效 Application 成员关系。
- 新增 `V20260810090000__enforce_qa_memory_identity.sql`，为非空 `qa_memories.mem0_memory_id` 建唯一约束；原生学习使用 MyBatis
  PostgreSQL `ON CONFLICT DO NOTHING` 原子选出胜者，失败方重读胜者并追加当前 Run 的安全证据。同步 HTTP、数据库、记忆部署及
  前后端模块说明和回归测试。

### How

- JDK 25 下 `test-agent-memory` reactor、H2 MyBatis/Flyway、`QaMemoryControllerTest` 定向测试均通过；临时真实 PostgreSQL 16
  从 `20260809230000` 已部署基线升级到 HEAD 的集成测试通过且没有 skip。应用 reactor 打包成功。
- memory-service 为 25 passed / 1 skipped，Embedding 为 4 passed；记忆开发/集群静态门禁通过。前端记忆定向 2 文件 13 项、
  agent-web typecheck 和 production build 通过，构建只保留既有大 chunk 提示；`git diff --check` 通过。
- 未修改的主工作区 `.env.test` 已把本地平台 PostgreSQL 升级到 `20260810090000`，源码、persistence JAR、应用 JAR及实际运行 JAR
  中 migration SHA-256 均为 `619f886b093c80c1e1f71569c5c44309fa4f8184dd2791c0cf1955beb77c9af3`，该文件自此不可改写。
- 使用共享 `TEST_AGENT_ROOT/TESTAGENT/SYS_DATA_ROOT_DIR` 从 memory worktree 重启 backend、manager、frontend；health/readiness、
  前端 3000、登录 CORS 和 manager WebSocket 正常。既有三副本 Mem0、CPU BGE、pgvector 的鉴权 status 为 UP，`rawMessageCount=0`。

### Result

- 七处审查问题已按现有 repository、MyBatis、Mem0 operation 幂等和成员关系程序收口，没有新增第二套一致性或授权实现；HTTP 路径、
  RunEvent、generated SDK、OpenCode 源码和 `.env*` 未修改，降级响应继续通过既有 `contentAvailable` 字段兼容识别。
- 默认完整重启的 workflow bootstrap 被本地 PostgreSQL 账号缺少 `CREATEROLE/ADMIN OPTION` 阻断，随后按官方
  `--without-workflow` 路径完成本次相关服务重启，workflow 未重启。重复执行 `--with-memory` 时 Docker BuildKit 在 0/0 阶段停滞，
  中止前没有替换现有容器；其后独立 status 证明现有全部记忆容器健康。本次没有完成企业真实多节点浏览器验收或批准容量压测。
- 工作树中同期的 Figma/Git 面板六个未提交文件保持未暂存，未覆盖或纳入本次修改。

## 2026-08-09 - 清理本机 Docker 旧资源并下调内存上限

### Why

- 本机 16 GiB 内存长期卡顿；排查确认 Docker Desktop VM 以 8092 MiB 启动，宿主机只剩约 145 MiB 空闲，Swap 一度使用
  约 10.3 GiB，同时遗留多个已退出的 TestAgent 开发容器、旧镜像和空闲 BuildKit 容器。

### What

- 删除可选 AMD64 工具箱容器与镜像、已退出的 Temporal/LobeHub 开发容器及其镜像、旧 memory-service/pgvector 镜像；
  保留所有数据库和对象存储 volume，并保留当前后端实际使用的 PostgreSQL、Redis、MySQL 和 memory-service。
- 将本机 Docker Desktop 设置 `/Users/kaka/Library/Group Containers/group.com.docker/settings.json` 的 `memoryMiB` 从 8092
  调整为 5120；该文件位于仓库外，未修改项目 `.env*`。
- 重启 Docker 后恢复 memory compose；空闲的 `mimoagent-builder` 未自动重启，后续构建会按需重新启动。

### How

- 通过容器架构、端口连接、restart policy 和实时 CPU/内存快照确认删除边界；显式按容器/镜像 ID 清理，没有使用全局
  volume prune 或删除其他项目数据。
- Docker Desktop 4.20.1 首次重启因旧 VM 退出错误弹出 `virtualization.framework ... %!w(<nil>)`；终止失败的 message-box
  后第二次启动成功，实际进程参数确认包含 `--memoryMiB 5120`。
- 验证 backend health/readiness、前端 3000、登录 CORS、manager 4104、memory-service 与全部恢复容器健康。

### Result

- Docker VM 上限已降为 5 GiB，BuildKit 常驻约 752 MiB 已释放；系统内存压力指标由约 15% 提升到 44%，Swap 使用量由
  约 10.3 GiB 降至约 4.95 GiB。
- TestAgent、memory-service 和 Sub2API 均恢复健康；未修改代码、API、RunEvent、数据库结构、migration、安全配置、
  generated SDK 或 OpenCode 源码。

## 2026-08-09 - 新增历史对话置顶与取消置顶

### Why

- release worktree 的 Session DTO、`pinned` 字段和 PATCH 更新接口已经存在，但当前 `FigmaChatPanel` 会话列表没有操作入口，
  用户级 MyBatis 历史查询也只按更新时间排序，导致写入置顶状态后无法稳定出现在分页列表前部。

### What

- 会话列表卡片新增独立、可访问的置顶/取消置顶按钮，请求中显示 Spinner 并阻止重复操作；`AgentWorkbench` 复用既有
  `updateSession` mutation，保留列表已有 `workspaceContext`，即时更新本地投影，并在加载过后续页时回到第一页对齐分页。
- `SessionHistoryMapper.xml` 的用户历史与工作区历史统一改为 `pinned desc, updated_at desc, id desc`；前端投影也按同一分组
  契约排序，兼容滚动发布期间仍按旧顺序返回的后端。没有新增字段、索引或 Flyway migration。
- 补充 MyBatis 集成测试、排序单测、组件交互单测和 Chromium PATCH 往返用例；同步 HTTP API、数据库、测试场景、用户手册
  以及 API/runtime/persistence/frontend 模块 README/PACKAGE。

### How

- `MyBatisSessionHistoryRepositoryIntegrationTest` 6 项通过；`workbench-utils.test.ts` 与 `FigmaChatPanel.test.ts` 合计
  256 passed / 1 skipped；隔离端口 Chromium 置顶往返 1 项通过。
- `corepack pnpm build` 通过；`mvn -pl test-agent-app -am -DskipTests package` 的 20 模块聚合打包通过，persistence JAR 中两条
  查询均确认包含置顶优先排序。
- Playwright 默认 `3000` 端口当时由 `intelligent-test-agent-memory-v1` worktree 占用，`reuseExistingServer` 会误用其旧页面；
  本次未停止其它 worktree，而是在 `3011` 隔离启动 release 页面完成验证。

### Result

- 用户现在可以在会话列表中置顶或取消置顶；置顶组始终位于普通组之前，两组内部按最后更新时间倒序，分页边界会在更新后
  重新对齐服务端权威结果。
- release 前端已用 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3011` 启动在
  `http://127.0.0.1:3011/` 并返回 200。未修改 `.env*`、OpenCode 源码、generated SDK、RunEvent、鉴权或安全契约。

## 2026-08-10 - 补齐置顶之外的普通对话端到端回归

### Why

- 置顶功能首轮 E2E 只把普通会话作为排序参照，没有验证置顶操作期间其它会话能否继续切换、当前态和正文是否串线。
- 新增图钉按钮后，旧用例按会话标题模糊查找按钮会同时命中卡片主按钮和图钉；扩大回归还发现三条重试用例仍等待 8 月 7 日前
  的“取消旧 Run + 新建 Run”接口，以及只读会话用例仍要求禁用输入框，均已落后于当前稳定契约。

### What

- `workbench.spec.ts` 新增统一 `historySessionButton`，历史切换限定 `.figma-chat-history-card-main`；置顶按钮继续按独立可访问名称定位。
- 扩展置顶往返用例：置顶目标后切到普通会话，验证 `aria-current` 和正文隔离；普通会话保持选中时取消目标置顶，再切回目标确认内容不丢失。
- 三条失败重试用例改为验证当前 `/resends` 契约：终态远端用户轮次可撤销重发，仍运行的轮次不取消、不替换，重新打开的失败历史按
  `remoteMessageId + sourceRunId` 重发。只读历史改为验证普通消息发送禁用、输入框保留原生命令能力。
- `docs/testing/conversation-scenes.md` 同步普通会话隔离、撤销重发和稳定选择器约定。

### How

- production preview 的置顶、普通会话隔离、标题、历史恢复、竞态、工作区切换和只读核心集合 9/9 通过；更新后的三条重试用例 3/3 通过。
- 项目官方 localhost Playwright 模式下，普通对话生命周期、历史恢复、跨会话竞态、认证变化、失败重试/撤销重发、只读降级、夜间任务
  会话隔离和原生命令共 39/39 通过。
- 原生 Python Playwright 在 1440×900 视口独立复核键盘 Enter 置顶、唯一 PATCH、主按钮/图钉可访问名称、普通会话
  `aria-current=true` 与正文隔离；所有脚本声明的 API route 均命中，无未处理请求，helper 自动停止隔离服务。
- `FigmaChatPanel.test.ts` 与 `workbench-utils.test.ts` 共 256 passed / 1 skipped；agent-web production build 通过，`git diff --check` 通过。

### Result

- 置顶/取消置顶不会隐式切换其它会话，也不会污染其它会话的选中态、正文或发送目标；普通对话的主要恢复、重试和竞态链路已有可重复回归证据。
- 本次只修改 E2E、测试说明和本机会话记录，不修改产品代码、API、RunEvent、数据库、migration、安全、`.env*`、generated SDK 或
  OpenCode 源码。production build 仍有既有大 chunk 警告，本次未扩大到性能拆包。

## 2026-08-10 - 解除外部 API 与 QA Memory 同号迁移冲突

### Why

- 企业增量打包前盘点本机所有需保留 PostgreSQL history，发现个人持久库 `testagent` 已执行
  `V20260809120000__create_qa_memory_governance.sql`，checksum 为 `311175224`；release 分支原本把尚未执行的外部 API
  凭据 migration 也编号为 `20260809120000`，直接打包会在该历史上触发 Flyway 校验失败，并阻断以后 QA Memory 正序合并。

### What

- 将从未在需保留数据库执行的外部 API 候选 migration 调整为 `V20260809110000`，SQL 字节和 SHA-256
  `356f2cf9127fb514c614ccb8fc77473e6269f6e1e5cd373b0750d2f207009d53` 不变。
- 复用唯一 `DatabaseMigrationCompatibilityCustomizer`：对已执行 QA Memory `20260809120000/311175224` 加载
  SHA-256 为 `b2ae5639284208be8bc09952d9143c3dd0d8a2bf649b6601aed4225e586af18a` 的原始字节兼容资源，过滤更低版本外部 API
  主 migration，并加载字节相同的 `V20260810110000` 前向 migration；未知 checksum、主/前向混用和孤立前向历史均失败关闭。
- Mac 内层打包、双后台外层封装和现场安装脚本新增这批撤销重发、批量会话、内部模型、QA Memory 与外部 API migration
  的 JAR 内 SHA-256 门禁；同步 persistence README、后端部署和数据库文档。

### How

- `mvn clean test -pl test-agent-app -am -Dtest=DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest,FlywayMigrationNamingTest`
  在真实 PostgreSQL 16 上通过 15 套历史，命名/字节锁 9 项通过；覆盖企业基线、既有兼容分叉、逐版本恢复、QA Memory
  同号历史、重复启动及未知 checksum 失败关闭，全程 `outOfOrder=false`。
- 外部 API MyBatis 集成与命名/字节锁共 12 项通过；三个发布脚本 `bash -n` 通过。现有健康 MySQL 8.4 实例只读确认
  XXL `V1-V11` 全成功且 V10/V11 两项任务启用；新 Testcontainers MySQL 因 Docker Desktop InnoDB 初始化超过容器等待时间，
  未执行到 SQL，失败原因不是 migration。

### Result

- 当前 release 主链可从企业基线顺序执行外部 API `20260809110000`；已执行 QA Memory 的个人历史改走
  `20260810110000` 前向路径，不需要也不允许 `repair`、`outOfOrder` 或手工修改 history。
- 未修改已执行 migration、API、事件、DTO、安全配置、`.env*`、generated SDK 或 OpenCode 源码；未新建分支。

## 2026-08-10 - 校准增量发布手册与 XXL Flyway 成品门禁

### Why

- 首次重打后的外层包复核发现 `START-HERE.md` 仍以更早企业包为基线，错误声称 PostgreSQL 本轮不新增版本、XXL MySQL
  只到 V9；若照此执行会把本次正常的六条 PostgreSQL 主迁移和 XXL V10/V11 当作异常历史。
- 发布脚本已锁定 persistence JAR 内的 PostgreSQL migration，但尚未自动锁定 XXL integration JAR 内 V10/V11 的最终字节。

### What

- 将多后台发布手册与内部部署入口更新为上一轮已部署提交 `1d4a7652f115404d0dfef8e8a0a599dfc8f25d0d` 基线，明确
  `.4` 只允许新增 PostgreSQL `20260807190000`、`20260807230000`、`20260808143300-302`、`20260809110000`
  及 XXL V10/V11，`.114` 共享数据库启动只做 validate；列出兼容版本停止条件、文件 SHA、XXL checksum 和四项任务频率。
- 内层打包、外层封装、现场安装三条脚本增加 XXL integration JAR 定位、V10/V11 资源 SHA-256 校验和发布/安装完整 JAR
  一致性检查；当前增量组件说明同步为 worker runtime 与 toolbox 均 `reuse`。

### How

- 三条脚本 `bash -n` 与 `git diff --check` 通过；最终包需重新从本提交干净快照构建，并由脚本实际打印 PostgreSQL、XXL
  migration 字节校验结果后方可覆盖中转文件。

### Result

- 包内操作说明与本轮数据库增量保持一致；XXL V10/V11 不再只靠源码或本地数据库记录证明，发布 ZIP 和安装目录中的实际 JAR
  都会失败关闭。未修改 migration SQL、API、事件、配置或 OpenCode 源码。

## 2026-08-10 - 修复企业包外部 SSH Key 服务启动装配

### Why

- 首轮企业增量包在 `.4` 启动时，PostgreSQL 已成功校验 92 条 migration 并确认当前版本
  `20260809110000` 无需迁移，但 Spring 随后创建 `ExternalSshKeyEnvelopeService` 失败：类同时存在生产构造器和包内测试构造器，
  Spring 7 未找到明确注入入口后回退到不存在的无参构造器。

### What

- 为 `ExternalSshKeyEnvelopeService(ObjectMapper)` 生产构造器显式增加 `@Autowired`，保留包内三参数构造器供固定随机数和时钟的
  TAEK1 测试复用，不新增无参构造器，也不改变加密协议或业务行为。
- 增加 `ApplicationContextRunner` 装配回归，直接验证真实 Spring 容器可使用 `ObjectMapper` 构造服务；同步模块 README 的多构造器约束。

### How

- JDK 25 下运行 `ExternalSshKeyEnvelopeServiceTest` 3 项通过；随后运行 `test-agent-integration` 及其依赖模块全量测试，
  common 97 项、domain 99 项、integration 44 项，共 240 项全部通过。
- 初次使用终端默认 JDK 17 时在编译阶段报“不支持发行版本 21”，切换仓库约定的 JDK 21+ 后测试通过，该错误未进入业务代码。

### Result

- 已消除企业后端启动时 `ExternalSshKeyEnvelopeService.<init>()` 的无参构造异常；原失败包应撤回并从本提交的干净 release 快照重打。
- 未修改 Flyway migration、API、事件、数据库结构、安全配置、`.env*`、generated SDK 或 OpenCode 源码；企业 PostgreSQL
  已到 `20260809110000`，重新部署只应校验现有 migration，禁止 `repair`、`outOfOrder` 或手工改 history。

## 2026-08-10 - 修复外部 API 凭据服务装配并增加发布门禁

### Why

- 第二次企业启动已越过 `ExternalSshKeyEnvelopeService`，但随后在 `ExternalApiCredentialApplicationService` 上再次出现
  `No default constructor found`；两次 Flyway 均成功校验 92 条 migration，当前版本仍为 `20260809110000`，数据库不是根因。
- 仅逐个修复现场首先暴露的 Bean 无法阻止同类问题继续串行出现，需要在源码与企业打包入口增加全量结构审计。

### What

- 为 `ExternalApiCredentialApplicationService` 和同一调用链中的 `ExternalApiCredentialUpdateBroadcaster` 生产构造器显式增加
  `@Autowired`，保留包内测试构造器，不改变凭据生成、加密、缓存或广播行为。
- 两个服务均增加 `ApplicationContextRunner` 真实 Spring 容器装配回归；`test-agent-app` 新增
  `SpringBeanConstructorWiringTest`，扫描全部生产 Spring Bean，要求多构造器 Bean 必须有无参构造器或显式注入构造器。
- `package-release.sh` 在后端打包前强制运行该全局审计，失败时终止企业包生成；同步 app、system-management 与内部部署 README。

### How

- 修复前全局审计稳定只检出上述两个 Bean；修复后两个上下文测试与全局审计共 9 项通过。
- JDK 25 下 system-management/integration 依赖链全量测试通过：common 97、domain 99、system-management 56、integration 44，
  共 296 项；发布脚本 `bash -n` 与 `git diff --check` 通过。
- 按 `.env.test`、`test` profile 且 `--without-workflow` 执行完整本地重启时，21 模块后端构建成功；运行启动被本机保留库已经执行、
  但当前 release 未解析的 `20260809230000`、`20260810090000` 阻断。未执行 `repair`、未改 history，也未启用 LobeHub。

### Result

- 第二次现场异常及全仓当前同类构造器歧义均已消除，并由企业打包门禁持续阻止回归；需从本提交干净快照重新生成并验证发布包。
- 当前 release 明确保持 Workflow、LobeHub 为 `disabled`。未修改 migration SQL、API、事件、数据库结构、安全配置、`.env*`、
  generated SDK 或 OpenCode 源码；企业库仍只允许对既有 `20260809110000` 历史做严格校验。

## 2026-08-10 - 确认公共 Agent 发布门禁导致消息按钮禁用

### Why

- 企业环境部署后 OpenCode 正常、输入框可编辑，但“发送”和“新建”按钮同时置灰；需要区分前端构建故障、OpenCode 故障与平台发布保护。

### What

- 复核前端共用禁用条件及后端 `/processes/me/message-gate`：公共 Agent/Skill 发布处于排空或仍有用户目标待处理时，
  `messageSendAllowed=false` 会同时禁用发送和新建，避免旧进程在公共配置切换期间继续接收消息。
- 用户更新公共 Agent 后按钮立即恢复，现场行为与 rollout 完成后解除消息门禁一致；本次未修改业务代码，也无需重新打包或部署。

### How

- 后端 `PublicAgentConfigRolloutServiceTest` 35 项通过。
- 从 `frontend` 根目录使用仓库 Vitest 配置运行测试，120 个测试文件通过，1884 项通过、1 项跳过；此前从子包直接执行导致
  `document is not defined`，原因是绕过了根目录 jsdom 配置，不是产品回归。
- 本地按 `.env.test`、`test` profile、`--without-workflow` 重启时，21 模块编译成功，但保留库已执行而当前 release 未解析的
  `20260809230000`、`20260810090000` 仍触发 Flyway 校验阻断；未执行 `repair`、未修改 history，Workflow/LobeHub 均未启用。

### Result

- 企业现场功能已随公共 Agent 更新恢复；根因范围收敛为公共配置 rollout 消息门禁，而非前端编译或 OpenCode 运行异常。
- 未修改 API、事件、数据库、性能、安全、环境配置、generated SDK 或 OpenCode 源码；本地服务因 Flyway 历史不兼容未启动。

## 2026-08-10 - 企业增量发布完成并固化交付校验边界

### Why

- 本轮企业增量部署经历两次后端启动装配失败和一次公共 Agent rollout 消息门禁现象；现场最终验收完成后，需要固化实际部署源码、
  交付包与关键内嵌资源的 SHA-256，避免后续排障把失败包、最终包或本地合并后的新代码混为同一版本。

### What

- 最终部署源码提交为 `8a6955f8da40e8da4ae5caeb247e7eb782aa672b`。
- 外层 `test-agent-two-backend-complete.zip` SHA-256 为
  `afe10e7ad6f9d2846fbe81e0fa80336ddfe2455b4783ad0b142d1970316fd3c6`；仓库 dist 与
  `/Users/kaka/Desktop/mimoagent/0709` 中转副本一致。
- 内层 `test-agent-internal-release.zip` SHA-256 为
  `da9c840b5bd4b71d78892e29e23d5ecfd5aaa193a0d46d327cd717a90e4c1a18`，且外层 ZIP 内嵌副本逐字节一致。
- 关键资源 SHA-256：`test-agent-app.jar` 为
  `28c2bf250536c2f327e8e3ac5c6d4068d79529ed271592c7dedfb69e63d5fa44`，persistence JAR 为
  `5f7c45d5363491a63364d1db96005579bbfa62856f07fde6d50308c7a3eb9a45`，包内 `deploy/internal/opencode-models.json` 为
  `edfa12f1a95da0954f72303e52934efea088b6f64cd834e8447f6e670e88bf86`。
- 外部 API 主 migration `V20260809110000__create_external_api_credentials.sql` 的最终 persistence JAR 内 SHA-256 为
  `356f2cf9127fb514c614ccb8fc77473e6269f6e1e5cd373b0750d2f207009d53`。

### How

- 首次失败为 `ExternalSshKeyEnvelopeService.<init>()` 无默认构造器，修复提交 `d5b4072ac`；第二次失败为
  `ExternalApiCredentialApplicationService.<init>()` 无默认构造器，同时补齐 broadcaster 并增加全量 Bean 构造器发布审计，
  最终修复提交 `8a6955f8d`。
- 两次现场日志均显示 Flyway 成功校验 92 条 migration、schema 当前版本 `20260809110000` 且无需迁移，证明数据库不是这两次
  Spring 装配失败的根因；全程未执行 `repair`、`outOfOrder` 或手工修改 history。
- 部署后“发送/新建”同时置灰由公共 Agent rollout 的 `messageSendAllowed=false` 门禁触发；更新公共 Agent、rollout 收敛后恢复，
  OpenCode 和前端构建本身正常。

### Result

- 用户确认本轮 `.4/.114` 后台与 `.2` 前端企业部署结束；本轮发布基线以以上源码与制品 SHA-256 为准，Workflow、LobeHub 未启用。
- 后续本地合并远程代码产生的新 HEAD 不代表企业已部署版本；排查现场问题时必须先对照本条 hash，再判断是否需要重打包。

## 2026-08-10 - 合并远程 release 并兼容本地 QA Memory 历史

### Why

- 企业部署结束后需要合并当前 release 的远程更新并本地重启；本地保留库已经执行
  `20260809120000`、`20260809230000`、`20260810090000`，而远程会话分享主 migration
  `20260809170000/01` 低于当前最高版本，直接启动会触发 Flyway 倒序阻断。

### What

- 拉取并合并远程同名分支 `origin/codex/release-enterprise-20260801` 的
  `de80b263cc663a79460189e8d908524e274c4613`；合并提交为
  `ea0f4446e40d089a6c72b45a70655b23d477051a`，没有合并 `origin/main`。
- 复用唯一 `DatabaseMigrationCompatibilityCustomizer`：精确校验 QA Memory 扩展 history/checksum，加载原始字节兼容资源，
  过滤两份低版本会话分享主 migration，再以 `20260810110000/01/02` 顺序补齐外部 API、会话分享和代操作归属结构；
  未新增第二套迁移器，未启用 `outOfOrder`，未执行 `repair`，未修改 `flyway_schema_history`。
- QA Memory 扩展原始资源 SHA-256 为 `2740ff6d4a97c5b8a4c438586f55d58078c3cfce93b06e4efeb6b77b039c66c3`、
  `619f886b093c80c1e1f71569c5c44309fa4f8184dd2791c0cf1955beb77c9af3`；会话分享主/前向资源逐字节一致，SHA-256
  分别为 `b0b04355fcfe64f3d22d8a8ff297fa62a30db9d97bf6bf82968588f5da72d0c9`、
  `dfb5d65b474416c28ec6131e95c7b9e7f744f9d2903c0bc4fcd0065632a4eee5`。三套企业打包/部署脚本均增加这些资源的 JAR SHA 门禁。

### How

- JDK 25 下真实 PostgreSQL Flyway 历史升级测试 18 项、迁移 SHA 锁定测试 9 项、Spring Bean 构造器门禁 1 项全部通过；
  会话分享 service/controller/MyBatis 定向测试共 20 项通过。
- 前端全仓 typecheck、lint、production build 通过；Vitest 123 个文件、1900 项通过，1 项按设计跳过。
- `bash -n` 校验三套企业脚本通过；四份新兼容资源与历史分支/主 migration 的 `cmp` 逐字节校验通过。
- 使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build --without-workflow`
  完成三服务重启。真实本地库从 `20260810090000` 顺序执行三条前向 migration，到达 `20260810110002`；
  history 中低版本 `20260809110000`、`20260809170000/01` 均未混入，活动 Run 重复会话预检为 0。

### Result

- Backend readiness、Frontend、OpenCode `/global/config` 均返回 HTTP 200；Manager WebSocket 已连接，端口 4104 的 OpenCode
  从未纳管状态经公共启动程序拉起后达到 `HEALTHY`。
- Workflow 与 LobeHub 进程均未启动，前端对应能力开关均为 `false`；数据库既有 `LOBEHUB_ENABLED=true` 未擅自改写，
  因未使用 `--with-lobehub` 且前端入口关闭，本次本地启动不启用 LobeHub。
- 当前启动仅有一条 macOS Netty 原生 DNS provider 缺失的 fallback 日志；应用仍使用系统 DNS 且 readiness 为 UP，
  未发现新的 Spring/Flyway 启动异常。未修改 `.env.test`、generated SDK 或 OpenCode 源码。

## 2026-08-10 - 修复取消置顶后的会话排序位置

### Why

- 历史会话仅切换 `pinned` 时，应用服务仍把 `updatedAt` 刷新为当前时间；旧会话取消置顶后因此被排到普通组最前，
  无法回到置顶前按最后活动时间确定的位置。

### What

- `SessionApplicationService` 将 `updatedAt` 继续作为普通会话组的稳定排序锚点：纯置顶/取消置顶保留原值，只有标题实际变化时刷新。
- 增加服务层置顶往返时间戳回归和 MyBatis 取消置顶排序回归，并修正同一查询测试中未体现“置顶优先”的旧断言。
- 同步 runtime、API、persistence、前端、用户手册、HTTP API、数据库语义和会话场景测试说明。

### How

- `SessionApplicationServiceTest` 17 项通过；`MyBatisSessionHistoryRepositoryIntegrationTest` 8 项通过。
- `workbench-utils.test.ts` 与 `FigmaChatPanel.test.ts` 共 258 项通过、1 项按设计跳过；Chromium Playwright 置顶/取消置顶场景 1 项通过，
  覆盖目标会话恢复原位置、普通会话选中态与正文不被置顶操作串改。
- 后端 `mvn -pl test-agent-app -am -DskipTests package` 的 20 模块构建、前端 agent-web production build（含用户手册）均通过。
- 使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build --without-workflow` 重新构建并启动 release 三服务；
  Backend liveness/readiness 均为 `UP`，Frontend `http://127.0.0.1:3000/` 返回 HTTP 200。

### Result

- 取消置顶后，会话按置顶前的 `updatedAt` 回到普通组原位置；置顶目标以外的会话顺序、当前选中态和正文保持隔离。
- HTTP URL、请求/响应 DTO 和事件契约不变；未新增 SQL、Flyway migration 或数据库字段，不涉及安全、环境配置、generated SDK
  或 OpenCode 源码。排序仍复用既有索引与查询，未增加分页查询或网络请求，向后兼容旧客户端。

## 2026-08-10 - 兼容企业模型 finish_reason 收尾并修正 FR 误报

### Why

- 企业环境用户 `001177621` 在 `.114` 完成对话，但可观测 FR 为 100%，明细全部归为“上游服务异常（流中断）”。
- 现场原始输出证明 `run_dacd40b0051746c8aa3269b6b3b48212` 有完整助手正文、`finish=stop`、`step-finish reason=stop`、`run.succeeded` 和最终 idle；代码却只把字面 `[DONE]` 当作正常收尾，与已有指标词汇中“`[DONE]` 或 `finish_reason`”的口径不一致。

### What

- 扩展既有 `InternalModelSseStreamObserver`，在单次 JSON 解析中同时识别真实输出和正常收尾信号：字面 `[DONE]` 或 `choices[*].finish_reason` 非空。
- 真实代理和探活统一复用新信号；仍要求至少一个真实模型输出，所以空流、仅元数据、仅正文后无收尾信号 EOF 仍记 `UPSTREAM_STREAM_INTERRUPTED`。
- 新增 `finish-reason-eof` 本地 mock 模式，并同步 runtime/API/数据库/事件流/前端类型与本地验证文档。页面指标注释仍使用“模型正常结束回答”的用户语言，不暴露 SSE 收尾细节。

### How

- JDK 25 下运行 `InternalModelSseStreamObserverTest`、`InternalModelProviderProbeServiceTest`、`InternalModelProxyForwardingServiceTest`，runtime 12 项、API 13 项共 25 项通过。
- `python3 -m py_compile tools/mock-model-server.py` 通过；实际启动 `finish-reason-eof` mock 并用 `curl -N` 确认输出有效 content、`finish_reason=stop` 后直接 EOF，不含 `[DONE]`。
- 首次按 `.env.test` 启动被本机 workflow 密钥缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 阻断；不修改环境文件，改用脚本显式 `--without-workflow` 模式完成 21 模块构建和三服务重启。Backend health/readiness 均 `UP`，Frontend 为 HTTP 200，CORS 预检返回正确 Origin，manager WebSocket 已连接且受管 OpenCode 进程最终 `HEALTHY`。

### Result

- 企业网关在有效回答后用非空 `finish_reason` 收尾并直接 EOF 时，用户调用与探活均记 `SUCCESS`，SCT 记录收尾信号到达耗时，不再把完成对话误算进 FR。
- 未变更 HTTP URL、DTO、结果枚举、RunEvent 字段、SQL、Flyway migration 或数据库结构；不涉及安全、`.env*`、generated SDK 或 OpenCode 源码。旧的历史误分类记录不回填，新版部署后的新调用按修正口径统计。

## 2026-08-10 - 基于当前 release HEAD 重打企业增量包并校准 Flyway 基线

### Why

- 上一轮企业部署已结束，需要仅以当前本地 release 工作树重新生成增量发布物，并保留已部署节点配置。
- 当前包包含新的会话取消置顶排序修复和企业模型 `finish_reason` 收尾兼容；企业库已知部署版本为 `20260809110000`，打包前必须确认主链与本地已知 Flyway 分叉均可前向升级。
- 首轮外层验包发现随包 `START-HERE.md` 仍把更早的 `20260806190500/V9` 当作当前基线，会把已部署的
  `20260809110000/V10/V11` 误判为本轮新增，必须先校准稳定部署文档再重新生成发布物。

### What

- 业务代码基线固定为分支 `codex/release-enterprise-20260801` 的提交 `c6577cdac11737ffa68e9fdef47273acefd7c9de`，未切换、拉取或清理工作树。
- 将 `deploy/internal/MULTI-BACKEND.md`、内部部署 README 和数据库文档统一更新为上一轮已部署源码
  `8a6955f8da40e8da4ae5caeb247e7eb782aa672b`、PostgreSQL `20260809110000`、XXL V1-V11 基线；本轮企业主链
  只允许新增会话 `20260809170000/01`，XXL 不新增 history。
- 最终发布物从包含上述文档修正的干净提交 `a15ea941c316f2cfc0ffe13e2f65480a4c79c54f` 构建。
- 外层继续复用上一版已经校验的 `.4`、`.114`、`.2` 节点包，只替换本次重新生成的内层发布物。
- 组件清单保持 worker runtime、toolbox 为 `reuse`，workflow、LobeHub 为 `disabled`；通用运维脚本仍随包保留，但不包含或启用对应运行时组件。
- 首轮内层 `ae3ec04ef9d3248ba796b47d86d95c1a118c8d9e8aa1af723e053592a37f7bd3`、外层
  `8243fdcb6f13930db0c5189269cb6cb71639c05e31bccedecad230db71d65a8d` 因包含旧基线说明已判定废弃，不得进入中转机或企业服务器。
- 最终发布物 SHA-256：内层 `79de4085fb2450955806212e98afc59a2370ec954abffed029c32dac0c3ed460`，外层
  `d07b96af32820afcba1513905d13d4276bc4780c5baf2264c0599c9447aa792f`，app JAR
  `953910ac99659fb9a86fb0a551b316c2e93a0ed17a1a1945fe062734243afb01`，persistence JAR
  `06cfd20f4464524067ef0ca5dade53c16b63605e5be7680bfe2a18f4ceeabec0`，XXL integration JAR
  `068ef8c619e944b8f9c44e4432e8765d55fc904b0a43aad54d5f8f26b977033d`，前端归档
  `59f27125d71bc8105275d443b31559c1c9ce1c018d70837465c7cc7fb68afebc`，`opencode-models.json`
  `edfa12f1a95da0954f72303e52934efea088b6f64cd834e8447f6e670e88bf86`。

### How

- JDK 25 下执行真实 PostgreSQL Flyway 兼容集成测试：`DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 18 项、`FlywayMigrationNamingTest` 9 项、`SpringBeanConstructorWiringTest` 1 项，全部通过且无跳过；没有使用 `repair`、`outOfOrder` 或手工修改历史表。
- 首轮执行 `deploy/internal/package-release.sh`，Spring 装配校验、20 模块后端构建、agent-web `vue-tsc` 与 Vite 生产构建通过；脚本在 JAR 生成和 ZIP 输入阶段两次逐项校验全部受控 Flyway migration 字节。
- 首轮执行 `deploy/internal/package-two-backend-complete.sh --nodes-dir <上一版已校验节点目录>`；三台节点包 checksum、内外层记录 checksum、两个 ZIP 完整性均通过，外层内嵌 ZIP 与内层包 `cmp` 完全一致；随后人工读取包内操作手册时发现基线过期并停止交付。
- 文档修正提交后重新执行内层与外层打包；最终外层内嵌 ZIP 与最终内层包逐字节一致，内外层部署手册和三台
  节点包内 `MULTI-BACKEND.md` 均与源码一致，且包含 `8a6955f8d`、`20260809170000/01`，不再包含旧基线
  `1d4a7652f`。三台节点包 checksum、组件清单、两个 ZIP 完整性与记录 hash 再次全部通过。
- 解包复核 `deploy/internal/opencode-models.json` 存在且 hash 不变；最终组件清单为 worker runtime/toolbox `reuse`、
  workflow/LobeHub `disabled`。
- 提交前回顾全部 `.agents/session-log*.md`，未发现冲突或残留合并标记，未修改 `.env*`。

### Result

- 最终企业增量包已从干净提交 `a15ea941c` 重新生成并通过本地构建、真实 PostgreSQL 已知历史升级、Flyway
  资源验签、部署手册基线和逐层压缩包完整性校验；发布物位于 `deploy/internal/dist/`，首轮旧 hash 不得使用。
- 本次未部署企业服务器，也未取得企业库完整 `flyway_schema_history` 导出。部署首台后端前仍须导出并比对全部 `version/script/checksum/success`；发现未知 checksum、失败记录或版本分叉时必须停止，不能用 `repair`、`outOfOrder` 或手工改表绕过。
- 本次只修改稳定部署文档与发布记录，无 API、事件、数据库 SQL、性能或安全实现变更；没有修改 generated SDK 或 OpenCode 源码。

## 2026-08-10 - 补充近期功能用户手册与简短宣传

### Why

- 近期已上线会话协作分享、会话置顶、批量子条目案例设计、撤销重发和测试资料多选跳转，但内置用户手册尚未说明协作分享与多选跳转，用户难以仅凭按钮理解权限和操作边界。
- 需要同时提供一份可直接用于群公告或邮件的简短功能介绍，且只宣传用户能够实际使用的能力。

### What

- 在内置用户手册的功能总览、对话、工作区和常见问题章节补充协作分享、只读/可对话权限、分享失效、并发互斥、定时任务边界，以及测试设计/测试执行资料多选跳转和子条目编号传递说明。
- 更新 `frontend/apps/user-manual/README.md` 的章节边界；新增 `docs/assets/marketing/mimo-recent-features-announcement.md`，以克制、事实导向的内部通知口吻介绍四组近期功能，并在 `docs/README.md` 增加宣传素材索引。
- 同步修正 `frontend/README.md` 与 `frontend/apps/agent-web/README.md` 中仍把分享页写成只读 transcript 的过期说明，使工程文档与现有 Session Share 行为一致。

### How

- 对照 2026-08-07 至 2026-08-10 的实际功能提交、工作台按钮文案和分享权限实现核对操作路径；没有把后台修复、管理端工程项或尚未启用能力写入宣传稿。
- `corepack pnpm --filter @test-agent/user-manual build` 通过；前端全量 Vitest 123 个测试文件通过，1900 项通过、1 项按设计跳过。
- `tools/verify-ai-docs.sh` 与 `git diff --check` 通过；变更文件未发现冲突标记，宣传稿未命中本次约束的生硬宣传用语。
- VitePress 手册以 `corepack pnpm --filter @test-agent/user-manual dev` 启动在 `http://127.0.0.1:3001/help/`，新增页面返回 HTTP 200，最终 HTML 包含协作分享、多选跳转和对应 FAQ 标题。

### Result

- 用户现在可以从系统内置手册查到近期功能的入口、步骤、权限和失败处理；宣传稿可直接复制后使用。
- 本次只修改文档与宣传文字，不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、性能、安全、兼容性实现、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-10 - 合并 release 最新代码到 QA Memory 分支并补齐双向 Flyway 兼容

### Why

- 用户要求切到 `codex/qa-agent-memory-v1`，把 `codex/release-enterprise-20260801` 的最新已提交代码合并进来；release 本地分支已在远端最新提交之上继续推进，合并期间又从 `a63919013` 前进到最终源提交 `5b2d66ed3`，mem 原始提交为 `a79b1122a`。
- 两条分支分别执行过 QA Memory 扩展 migration 和会话分享/外部 API migration。简单接受任一侧的兼容器会让另一套真实 PostgreSQL 历史在 Flyway 严格递增规则下无法升级，必须在合并时显式覆盖双向历史。

### What

- 在独立 worktree `/Users/kaka/Desktop/intelligent-test-agent-memory-v1` 执行 `--no-commit --no-ff` 合并；首轮 12 个文本冲突逐一保留双方语义并形成 `c50bc9e78`，提交前发现 release 新增共享会话修复后继续合并 `5b2d66ed3` 并处理 2 个增量冲突。覆盖会话分享与记忆中心、系统管理 API Key 与 Memory 面板、路由/文档、发布脚本和两侧会话日志；主 release worktree 的既有未提交修改保持不动。
- 复用唯一 `DatabaseMigrationCompatibilityCustomizer` 和既有资源过滤器：允许 QA Memory 主链与会话分享主链合法共存；QA 历史缺少会话分享时继续走现有高版本前向迁移；release 历史已执行会话分享但缺少低版本 QA migration 时，过滤三条低版本主资源并执行新的 `V20260810173117__qa_memories_create_governance_after_session_share.sql`。
- 新前向 migration 按原三条 QA migration 的顺序合并为一次事务，SHA-256 为 `44ea89c0ea5b9edb7fc5cbfb682e540b251f0c106b1d3c2762576d04ade6f984`；发布脚本增加源码和最终 JAR 资源锁定。没有新增第二套迁移器，没有启用 `repair`、`outOfOrder`，没有修改任何已执行 migration 字节或 `flyway_schema_history`。
- 同步更新 app、persistence README、数据库部署文档和发布脚本，说明两种已部署历史的升级方向、版本与校验值。合并前把 mem worktree 既有 6 个未提交前端文件存入安全 stash，本次提交不纳入；合并提交后再原样恢复到工作树。

### How

- JDK 21 下真实 PostgreSQL 16 兼容矩阵 `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 19 项、`FlywayMigrationNamingTest` 9 项全部通过；合入 `5b2d66ed3` 后，JDK 25 下 `RunResendApplicationServiceTest`、`OpencodeRuntimeApplicationServiceTest`、`SessionApplicationServiceTest` 和 `SessionShareControllerTest` 共 62 项通过，最终 `mvn clean package -DskipTests` 的 22 模块构建通过，源码与 persistence JAR 内新 migration 的 SHA-256 完全一致。
- 最终 agent-web typecheck 和 production build 通过；前端全量限制 4 workers 后为 126 个测试文件通过、1920 passed / 1 skipped。首轮无并发限制时仅 Mermaid 懒加载文件 5 项超时，该文件独立 12/12 通过，限制并发后的两次全量均稳定通过。
- `bash -n deploy/internal/package-release.sh`、`tools/verify-ai-docs.sh`、暂存/未暂存 `git diff --check` 和冲突标记扫描通过；提交前回顾全部 `.agents/session-log*.md`，未发现本次暂存内容覆盖其他开发者未完成事项，也未暂存 `.env*` 或 `opencode-source/`。
- JDK 25 下使用主工作区只读 `.env.test`、共享 `TEST_AGENT_ROOT/TESTAGENT/SYS_DATA_ROOT_DIR` 和 `--with-memory --without-workflow` 从 mem worktree 完整重启；22 模块重新构建，backend health/readiness 为 `UP`，frontend 返回 200，CORS 返回正确 Origin，manager WebSocket 已连接，三副本 Memory/CPU BGE/pgvector readiness 通过且 `rawMessageCount=0`。

### Result

- release 最终源提交 `5b2d66ed3` 和 mem 能力已通过连续 merge 提交完成集成，双向已部署数据库历史都有真实 PostgreSQL 升级证据；API、事件、安全与前端能力沿用两侧既有契约，合并修复只新增数据库兼容资源和对应装配，不修改 generated SDK、OpenCode 源码或环境文件。
- 当前平台服务运行于 mem worktree；用户 OpenCode 4104 进程数据库状态为无需自动恢复、manager 暂未托管，需用户保持/重新建立登录态后走既有认证初始化入口恢复，不影响 backend/frontend/Memory readiness。本次不推送远端。

## 2026-08-10 - 修复共享会话撤回编辑、终态收敛与上下文压缩同步

### Why

- 会话协作中普通参与者也能对自己发送的最后一条消息执行撤回重发，不符合“只有分享人/会话所属人可撤回”的产品边界；既有入口点击后立即调用后端，用户没有修改上一条消息的机会。
- 所属人撤回结束后，分享 runtime-state 直接清空本地 active Run，绕过精确终态对账，其他参与者持续显示“思考中”直到刷新。
- compact 只改变 OpenCode 远端消息，没有推进平台可观察修订，分享 SSE 不会通知其他参与者；compaction 标记后的内部续写摘要又被当作普通助手回答直接展示，风格和语义都不清晰。

### What

- 人工撤回重发收紧为仅会话所属人；停止 Run 继续允许所属人或该 Run 实际发送人。前端点击“撤销重发”后先把上一条文本装入受控 composer，可编辑或取消，发送失败保留草稿；API additive 接受可选 `editedPrompt`，服务端从可信远端轮次恢复原 part，只替换文本并保留附件、Agent、模型、variant 等其它结构。
- 分享 runtime-state 不再在 active Run 消失时提前清空本地 Run，而是复用精确 Run 详情终态对账；新增 additive `sessionUpdatedAt` 内容修订锚点，compact 远端成功后推进平台 Session 修订并触发分享 SSE，其他参与者自动刷新消息投影。
- 将 compaction 标记与紧邻的内部续写摘要合并为默认折叠的标准 disclosure“上下文已压缩”，展开后说明它不是新回答，并在展示层把固定英文摘要字段映射为中文；原始消息和协议内容不改写。
- 同步 runtime/API/frontend/agent-chat/backend-api README/PACKAGE、HTTP API、RunEvent、安全、OpenCode 规范、对话测试场景和内置用户手册。

### How

- JDK 25 下 `RunResendApplicationServiceTest`、`OpencodeRuntimeApplicationServiceTest`、`SessionApplicationServiceTest` 共 58 项通过；`SessionShareControllerTest` 4 项通过。
- 前端相关 Vitest 3 个文件 196 项通过、1 项按设计跳过；agent-web、agent-chat、event-stream-client typecheck 均通过；Chromium Playwright 6 项通过，覆盖非所属人无入口、编辑重发、历史失败重发、分享 Run 终态自动收敛和 compact 修订自动刷新。
- agent-web production build（含 `user-manual` VitePress build）、`tools/verify-ai-docs.sh` 与 `git diff --check` 通过，仅保留既有大 chunk 提示。提交前回顾全部 `.agents/session-log*.md`，未发现与本次文件重叠的未完成事项或残留合并标记。
- 首次按 `.env.test` 启动被本机 workflow 缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 拦截；未修改环境文件，改用脚本现成 `--without-workflow` 模式完成 21 模块构建和 backend/opencode-manager/frontend 三服务重启。Backend health/readiness 为 `UP`，Frontend 返回 HTTP 200，CORS 正确，manager WebSocket 已连接且受管 OpenCode 最终 `HEALTHY`。

### Result

- 只有会话所属人能撤回并修改上一条消息后重发；分享成员不再因自己是源消息发送人获得该权限。分享页面无需刷新即可退出旧 Run 的“思考中”并看到压缩后的摘要。
- HTTP URL 和 RunEvent wire name 不变，只新增可选请求字段与分享 SSE data 字段，旧客户端不传/忽略时继续兼容；修改文本只进入既有有限 TTL Redis 精确重放输入，不进入控制表、事件、审计或日志。
- 未新增 SQL、Flyway migration、数据库字段或索引，不修改 `.env*`、generated SDK 或 OpenCode 只读源码；新增的分享 SSE Session 修订读取复用既有单会话查询，不引入前端轮询。

## 2026-08-10 - 为上下文压缩增加持续动效与完成反馈

### Why

- `/compact` 只在开始和结束时弹出短暂消息；OpenCode summarize 与消息刷新耗时较长时，中间没有常驻反馈，用户无法判断压缩是否仍在进行、何时完成。

### What

- `AgentWorkbench` 维护 compact 专用的 `running/success` 短生命周期状态：请求和当前会话消息刷新期间保持运行态，成功后保留勾选完成态 2.6 秒，失败立即收起并继续复用既有错误提示。
- `FigmaChatPanel` 在输入框上方增加常驻状态条；进行中用三条上下文线局部收拢动画表达压缩，完成后原位切换勾选和明确文案。运行期间阻止普通发送、新建按钮和定时提交，避免与 Session 压缩并发。
- 动画仅在 compact 状态节点存在时作用于局部 `transform/opacity`，没有恢复曾导致全树样式重算的全局继承动画；`prefers-reduced-motion: reduce` 下禁用动画和过渡。
- 同步 agent-web README、内置用户手册和对话场景测试说明。

### How

- `FigmaChatPanel.test.ts` 157 项通过、1 项按设计跳过；agent-web typecheck 通过；agent-web production build（含用户手册 VitePress build）通过，仅保留既有大 chunk 提示。
- Chromium Playwright 原生命令场景通过，实际断言运行态节点、三条动画线、编译后 keyframes、完成态和既有成功提示；`tools/verify-ai-docs.sh`、`git diff --check` 通过。
- 首次浏览器回归误复用 3000 端口上 `intelligent-test-agent-memory-v1` 的旧 Vite；切回当前仓库后用同一用例复测通过。首次启动又发现旧 worktree 后端 PID 9927 占用 8080 且不响应 `SIGTERM`，精确停止该旧 screen/进程后，复用已构建 JAR，按 `.env.test`、`test` profile、`--without-workflow` 启动当前仓库三服务。
- Backend health/readiness 均为 `UP`，Frontend 3000 返回 HTTP 200，登录 CORS 正确，当前监听 PID 的启动路径均属于本仓库；manager WebSocket 已连接。提交前回顾全部 `.agents/session-log*.md`，确认未覆盖其它开发者成果或残留合并标记。
- 提交后 3000/8080 被 `intelligent-test-agent-memory-v1` 的另一组开发进程重新接管；未中断该 worktree，改在 4177 启动当前仓库 `agent-web`（进程 cwd 为本仓库），HTTP 返回 200，作为最终前端运行验收。标准端口的后端健康结果不再计入当前分支最终运行状态。

### Result

- 用户执行 `/compact` 后会持续看到压缩动效；压缩和消息刷新完成时状态条明确切换为完成态，无需通过刷新或猜测判断进度。
- 未修改 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码；仅增加前端局部状态、交互门禁、样式、测试和稳定文档，旧客户端与后端兼容性不变。

## 2026-08-10 - 修复分享发送人撤回编辑与历史回答生成态

### Why

- 用户进一步明确分享会话中的消息实际发送人也应能撤回、修改并重发自己的最后一条消息；上一轮将人工撤回收紧为仅会话所属人的产品判断已不符合最新需求。
- resends API 返回替代 Run 后，reducer 只迁移 Run 归属而保留旧文本；权威 user 事件若携带编辑后的新文本，既有按文本相等归并又可能失败，导致旧文本继续显示或追加重复气泡。
- OpenCode/历史快照中的 assistant text part 可能在 Run 终态后仍保留 `running`。此前组件只看 part 状态，旧回答在新一轮已可发送甚至已完成后仍持续显示“生成中”。

### What

- 后端人工撤回授权调整为“会话所属人，或源 Run 的实际消息发送人”；分享发送人还必须通过既有 `canChat` 校验，其它成员继续 `FORBIDDEN`，不能改写他人消息。HTTP 路径、请求/响应 DTO 和 RunEvent wire name 均未改变。
- 前端分享入口复用权威 `Run.messageSenderUserId` 与当前 actor 判定。resends 返回后把 `editedPrompt` 交给既有 reducer，立即替换原用户气泡和首个 text part、保留附件等其它 part；`run.resend.started` 后的权威 user 事件按 replacement Run 原位接管，即使 assistant 先到或新旧文本不同也不重复。
- assistant text 时间线行显式携带“是否为当前 busy 轮次”；只有该值为真且 part 自身为 `running/pending` 时展示轻量“生成中”，历史轮次和终态 Run 一律按最终 Markdown 渲染。
- 同步 API、RunEvent、安全、OpenCode 规范、backend/frontend/agent-chat README/PACKAGE、测试场景和内置用户手册。

### How

- JDK 25 下 `RunResendApplicationServiceTest` 6 项通过，覆盖所属人、分享源消息发送人和无关成员；后端 `test-agent-api,test-agent-opencode-runtime -am -DskipTests package` 的 18 模块 reactor 构建成功。
- agent-chat 全量 10 个测试文件、178 项通过；其中 reducer 覆盖编辑文本即时替换、附件保留、assistant 先到后的 replacement Run 归并，时间线覆盖历史轮次和终态残留 `running`。agent-chat 与 agent-web typecheck 均通过。
- Chromium Playwright 5 项通过，覆盖分享发送人编辑重发、分享 active Run 消失后的终态对账和“生成中”收口、即时/历史/manual resend；agent-web production build（含 VitePress 用户手册）通过，仅保留既有大 chunk 提示。
- `tools/verify-ai-docs.sh`、`git diff --check` 和本次改动文件的冲突标记扫描通过。提交前回顾全部 `.agents/session-log*.md`，确认未覆盖其他开发者成果或残留合并标记。

### Result

- 持有对话权限的分享成员现在可以撤回并修改自己发送的最后一条消息；会话所属人仍可操作，其它成员不能修改别人的消息。发送后页面立即显示新文本，后续权威事件不再恢复旧文本或产生双气泡。
- 分享会话无需刷新即可在旧轮次或终态时移除回答卡中的“生成中”；新 Run 的工作状态仍按真实 busy 状态正常展示。
- 本次变更涉及既有 HTTP 接口的鉴权行为放宽和前端状态投影；不新增 URL、DTO/事件字段、SQL、Flyway migration、数据库结构、轮询或持久缓存，不修改 `.env*`、generated SDK 或 OpenCode 只读源码。修改文本仍只进入既有有限 TTL Redis 精确重放输入，不写控制表、事件、审计或日志。

## 2026-08-10 - 压缩结果收为图标并调浅分享协作者气泡

### Why

- 上下文压缩完成后仍以整行 disclosure 展示，在短对话中反复占据大块空间，与已完成思考状态和搜索事件的图标语言不一致。
- 分享工作台中其他参与者的 `#9A8EDE` 紫色气泡饱和且偏深，视觉权重高于助手正文。

### What

- `CompactionSummaryRow` 复用 `OcIconButton` 和历史完成态的 28px 图标尺寸，默认只展示 `Minimize2` 小图标；点击后图标保留，并在下方展开压缩方式、触发原因、语义说明和已翻译的续写摘要。
- 压缩标记与紧邻摘要的既有投影、固定英文字段中文映射及原始协议内容保持不变；只替换展示壳和可访问的展开/收起语义。
- 新增聊天主题 token `--ta-chat-other-user-bg`，默认值为低饱和浅紫 `#DED9F6`；其他参与者气泡通过该 token 展示，自己的 `#B2EDDF` 薄荷绿保持不变。
- 同步 agent-web、agent-chat README，以及组件、归因和分享 Playwright 回归断言。

### How

- 定向 Vitest 2 个文件 159 项通过、1 项按设计跳过；前端全量 Vitest 123 个文件 1904 项通过、1 项跳过；全 workspace typecheck 和 agent-web production build 均通过，构建仅保留既有大 chunk 提示。
- Chromium Playwright 两个分享场景通过，覆盖浅紫色 computed style、compact 修订同步、默认图标态和点击展开详情；保留 trace 截图并人工检查收起、展开和两种气泡颜色的实际布局。
- 按 `.env.test`、JDK 25 和 `--without-workflow` 重启三服务；backend health/readiness 为 `UP`，frontend 返回 200，CORS 正确，manager WebSocket 已连接，监听路径均属于当前仓库。
- 提交前回顾全部 `.agents/session-log*.md`。工作区同时存在另一组后端撤销重发 Controller/API 文档/测试改动，与本次文件无重叠，未修改、暂存或回滚。

### Result

- 压缩完成态现在与思考/搜索过程使用同一轻量图标层级，只有用户主动点击时才展开详情；分享协作者气泡明显变浅且仍能与自己的消息区分。
- 本次仅修改前端展示、主题 token、测试和稳定 README；不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、性能链路、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码，旧消息和旧后端兼容性不变。

## 2026-08-10 - 修复分享成员撤销重发的生产服务装配

### Why

- 分享成员 `wr` 在可对话分享会话中撤销并修改自己的最后一条消息后，提交固定失败为 `RUNTIME_STATE_UNAVAILABLE: 会话分享服务未配置`；会话所属人不携带分享头，因此未触发同一故障。
- 根因是 `RunResendController` 的双参测试兼容构造器被标记为 `@Autowired`，生产 Spring 装配始终绕过包含 `SessionCollaborationShareService` 的完整构造器，导致分享请求进入控制器时 `shareService` 为 null。

### What

- 将生产 `@Autowired` 移到包含分享服务的三参构造器，保留双参构造器仅供不涉及分享头的既有单元测试兼容；分享成员仍先走既有 `requireAccess(..., requireCanChat=true)`，未放宽权限或绕过实际发送人校验。
- 新增 `RunResendControllerSessionShareTest`，用真实 `ApplicationContextRunner` 构造器选择和预注册业务单例锁定生产装配，并验证分享头在进入异步业务链前解析为代操作上下文。
- 同步 `test-agent-api` README、HTTP API 对应测试和会话测试场景说明。

### How

- TDD 红灯在修复前精确复现“会话分享服务未配置”；修复后 JDK 25 定向测试 1 项通过，18 模块 Maven reactor 全部成功。扩大到 `test-agent-api -am` 的全测试时，相关 runtime/API 批次均通过，最终仅既有 `XxlJobMysqlMigrationTest` 因本机 Docker 中 MySQL 8.4 三次初始化超过连接窗口报错，随后停止无关剩余批次。
- 按 `.env.test`、JDK 25 和 `--without-workflow` 完成 21 模块构建并重启 backend、opencode-manager、frontend；health/readiness 为 `UP`、CORS 正确、manager WebSocket 已连接，8080/3000 监听进程与 cwd 均属于当前仓库。
- 真实登录分享成员 `wr`，进入“重新完成案例设计”，撤销“共享测试”并改为“仅答复 OK”后提交成功；POST `/resends` 返回成功，新 Run `run_8448ba49d6cb4f04932bcab55c584ab5` 最终 `SUCCEEDED`，成员页显示新文本和回复 `OK`，思考态与停止按钮正常收敛。
- 提交前回顾全部 `.agents/session-log*.md`，确认近期其它工作没有覆盖本次 Controller/API 文档/测试文件；冲突标记与空白校验通过。

### Result

- 被分享成员现在可以正常撤销、修改并重发自己最后一条消息，不再要求刷新或报“会话分享服务未配置”；分享授权、`canChat`、源消息实际发送人和会话边界仍由原有服务端规则复验。
- 本次修复只更正既有 Controller 的 Spring 装配并增加回归测试/文档，不新增或变更 HTTP URL、请求/响应 DTO、RunEvent、数据库/Flyway、关系型 SQL、性能链路或安全策略；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

## 2026-08-10 - 将上下文压缩恢复为清晰的独立记录

### Why

- 上一版把压缩完成态收成了没有文字的 28px 图标；它虽然与历史思考图标同尺寸，但在实际时间线中位置和含义都不明显，用户容易误以为压缩按钮消失。
- 用户进一步明确压缩必须与思考入口分离，可用独立分隔行或独立按钮展示，并在点击后查看具体摘要。

### What

- `CompactionSummaryRow` 改为独立的带文字按钮，自动和手动压缩分别显示“上下文已自动压缩”“上下文已手动压缩”，右侧细分隔线标明压缩发生的时间线位置；不复用或并入思考按钮。
- 按钮保留压缩图标、展开箭头、键盘焦点和 `aria-expanded/aria-controls`；点击后继续在下方展示压缩方式、触发原因、中文字段映射、内部续写摘要和“不是新的回答”说明。
- 同步 agent-chat、agent-web、用户手册和原生 Part/组件/分享会话回归断言；消息投影和协议原文保持不变。

### How

- 定向 Vitest 2 个文件 179 项通过、1 项跳过；前端全量 Vitest 123 个文件 1904 项通过、1 项跳过；共享会话 compact 修订 Chromium Playwright 1 项通过。
- 全 workspace lint、typecheck 按近期日志记录的 VitePress 临时目录竞争要求串行复跑并通过；agent-web production build 和 `tools/verify-ai-docs.sh` 通过，构建仅保留既有大 chunk 提示。
- 按 `.env.test`、JDK 25、`--without-workflow` 重启 backend、opencode-manager、frontend；health/readiness 为 `UP`，前端 3000 返回 200，CORS 正确，manager 受管 OpenCode health 为 `HEALTHY`。浏览器接管本地 URL 被产品安全策略阻止，未绕过该限制，因此本次没有新增真实页面截图证据。
- 提交前回顾全部 `.agents/session-log*.md`，确认前序分享撤回装配修复已独立提交，当前暂存范围不覆盖其它开发者成果；冲突标记和空白校验通过。

### Result

- 压缩完成后会在发生位置显示一条可直接识别、可点击的独立记录，不再像图标消失，也不会和思考状态共用按钮；详情仍默认折叠并按需展开。
- 本次仅修改前端展示、测试和稳定文档；不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、性能链路、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码，旧消息与旧后端兼容性不变。

## 2026-08-10 - 压缩摘要仅在独立按钮内按需展示

### Why

- 本地真实压缩消息经脱敏只读核对后，摘要 part 顺序为 `step-start → reasoning → text → step-finish`；时间线原先在 reasoning 处清空待配对状态，导致 text 摘要被当作普通助手正文平铺，即使压缩按钮处于收起态也仍可见。
- OpenCode 还可能在 compaction 标记与摘要之间插入无正文或仅含系统 part 的 user envelope。用户要求时间线不显示“上下文压缩中”，只在摘要完成后保留唯一一条默认收起的压缩结果，点击后再展示摘要本身。

### What

- 抽取与 `UserMessageRow` 共用的用户消息可见性判定；compaction 待配对状态可跨不可见 user envelope 和摘要前的 reasoning part，遇到真实用户输入才终止，避免误吞下一轮正常回答。
- `createTimelineRows` 过滤没有完整摘要或摘要 text 仍为 `pending/running` 的 compaction 行；压缩进行态继续只由输入区上方既有动效反馈，时间线在摘要落稳后才显示唯一结果。
- `CompactionSummaryRow` 默认收起，结果行仅保留独立文字按钮、分隔线、展开箭头和固定字段中文映射；展开面板只渲染摘要本身，不再显示额外说明或平铺副本。
- 新增真实 part 顺序、不可见系统包络、进行态不入时间线、下一轮隔离、默认收起和分享无刷新同步回归，并同步 agent-chat、agent-web、用户手册与会话测试文档。

### How

- 使用本地 PostgreSQL 对含 `Objective` 的近期消息只读查询 message 角色、时间和 part 类型，不输出摘要正文，确认真实结构含 reasoning；未修改数据库。
- agent-chat 与 `FigmaChatPanel` 完整批次 11 个测试文件、340 项通过、1 项按设计跳过；agent-chat、agent-web 类型检查通过。补充“进行态不入时间线”后定向 5 项通过，冷重启后的 Chromium 分享 compact E2E 1 项通过。
- 首次标准重启仍被既有 Workflow 密钥缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 拦截；未修改环境文件，改用脚本现成 `--without-workflow` 模式完成 21 模块后端构建和 backend/opencode-manager/frontend 冷重启。最终调整后再次冷重启，health/readiness 为 `UP`、3000 返回 200、受管 OpenCode 为 `HEALTHY`。
- 应用内浏览器接管 localhost 被产品安全策略阻止，未绕过；真实浏览器行为由 Playwright Chromium E2E 验证。提交前回顾全部 `.agents/session-log*.md`，并执行 production build、AI 文档校验、冲突标记扫描和 `git diff --check`。

### Result

- 压缩进行态不再在时间线显示第二条记录；摘要完成后只出现一条默认收起的结果按钮，不再平铺 Objective 等正文，点击后才在按钮下方显示中文字段摘要。
- 本次仅修改前端投影、展示、测试和稳定文档；不涉及 HTTP API、RunEvent wire、DTO、数据库/Flyway、关系型 SQL、性能链路、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码，旧消息与旧后端保持兼容。

## 2026-08-10 - 恢复分享成员的新建对话入口

### Why

- 分享工作台通过 `fixedSession` 隐藏了“新建对话”按钮，被分享成员只能停留在所属人的固定会话，无法直接开始自己的任务。

### What

- 固定分享会话保留“新建对话”按钮，并将提示明确为“退出分享并新建对话”；点击后返回当前用户自己的普通工作台，而不是在分享路由内重置并继续绑定所属人的 Session。
- 新增组件与分享工作台 E2E 回归，同步 agent-web 工程说明、内置用户手册和会话测试场景。

### How

- `FigmaChatPanel.test.ts` 定向运行 158 项通过、1 项按设计跳过；agent-web typecheck 通过；分享只读工作台 Chromium Playwright 场景 1 项通过。
- 使用本地账号 `wr` 进入真实分享链接，确认按钮可见；点击后 URL 返回 `/`、固定分享标识消失、本人工作台的新建对话入口保留。当前 frontend 3000 返回 200，backend health/readiness 为 `UP`。
- `git diff --check` 通过；提交前回顾全部 `.agents/session-log*.md`，并保留同期上下文压缩改动，不回滚或混入本次提交。

### Result

- 被分享成员无需刷新或退出登录，即可从分享页直接进入自己的空白对话。
- 本次仅调整前端路由交互、测试和稳定文档；不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、性能、安全、`.env*`、generated SDK 或 OpenCode 只读源码，普通工作台行为保持兼容。

## 2026-08-10 - 稳定分享双用户专项 E2E

### Why

- 分享专项 E2E 中，会话历史主按钮与置顶按钮具有相同的可访问名称，导致严格定位偶发匹配两个元素；空 envelope 到平台正文的验证依赖固定延时，在 WebKit 下存在时序抖动；Firefox 中通知层可能短暂遮挡抽屉关闭按钮。

### What

- 复用既有 `historySessionButton` 精确定位三个分享历史入口，并沿用既有强制点击方式关闭被通知层遮挡的抽屉。
- 扩展现有 RunEvent fetch stream 测试辅助器，支持手动释放事件批次并记录已发事件；先确认空 envelope 不展示，再释放所属人的权威正文，避免用浏览器定时器猜测顺序。

### How

- 使用 JDK 25、`.env.test` 和 `--without-workflow` 重启 backend、manager、frontend；health/readiness 均为 `UP`，frontend 返回 200，CORS 正确，manager WebSocket 已连接且受管 OpenCode 为 `HEALTHY`。
- 三个问题场景在 Chromium、Firefox、WebKit 共 9 项通过；完整 `e2e:session-share` 33 项全部通过且无重试；agent-web typecheck 与 `git diff --check` 通过。
- 用户明确聚焦分享双用户查看后，中止了 150 项通用 E2E；中止前发现的两个分享定位问题均已修复，另一个通用用例仅因中止而停止，不作为失败结论。
- 提交前回顾全部 `.agents/session-log*.md`，保留同期压缩摘要与用户消息展示改动，不回退也不纳入本次提交。

### Result

- 分享所属人和被分享人两个视角的专项流程在三种浏览器中稳定通过，权威用户消息顺序不再依赖固定等待时间。
- 本次仅修改 E2E 测试及测试辅助器；不涉及生产代码、HTTP API、RunEvent wire、DTO、数据库/Flyway、关系型 SQL、性能链路、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-10 - 修复 wr 本地 OpenCode 失效绑定

### Why

- wr 的 `ACTIVE` 用户进程绑定仍指向已离线的旧服务器 `192.168.100.115:4097`，对应进程已为 `FAILED`；当前本机稳定身份是 `kakadeMacBook-Pro.local`，因此状态与初始化请求在后端路由阶段返回 `OPENCODE_UNAVAILABLE`，没有进入本机 manager。

### What

- 在本地 `.env.test` 测试库中精确删除 wr/opencode 这一条旧 assignment，保留旧失败进程记录及其他用户绑定；随后以 wr 当前登录态调用正式“初始化进程”入口，由公共分配、预留和启动链路重新创建本机进程。

### How

- 删除前锁定并核对旧 binding 的 user/process/server/port/status，删除后确认 wr 绑定数为 0；未修改其他用户、旧进程记录或环境配置。
- 正式初始化后，manager 在 4096 返回 `STARTED`，公共健康等待从短暂 `UNHEALTHY` 收敛为 `HEALTHY`；`/global/health` 返回 `healthy=true`、版本 `1.18.4`，`/global/config` 返回成功，端口 4096 由新 opencode PID 监听。
- 数据库最终状态为 wr 新 binding `ACTIVE`、进程 `RUNNING`，服务器 `kakadeMacBook-Pro.local`、端口 4096；页面显示“TestAgent 进程可用”。提交前已回顾全部 `.agents/session-log*.md`。

### Result

- wr 的 OpenCode 已在本机重新分配并健康启动，可继续正常对话。
- 本次是本地测试库的定点运行态修复，仅更新会话记录；未修改生产代码、HTTP API、RunEvent、数据库结构/Flyway、SQL mapper、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-10 - 增加 TTFT 箱线图与并排看板布局

### Why

- 单看平均 TTFT 和最大 TTFT 无法说明多数调用集中在哪个等待区间，也容易被少数慢请求影响判断；用户要求用竖向箱线图展示整体分布，并把调用结果分布、供应商请求量上下排列后放在箱线图旁边。

### What

- 新增受 `SUPER_ADMIN` 保护的 `GET /api/internal/platform/opencode-runtime/internal-model-observability/ttft-distribution`，沿用供应商、结果大类、来源和时间筛选，返回样本数、最小值、P25、中位数、P75、最大值。
- 持久层通过 MyBatis XML 直接基于 `first_token_ms` 明细使用 PostgreSQL `percentile_cont` 计算分位数；只统计确实收到首个模型输出的调用，空样本返回 0 和空值，不从小时均值反推。
- 前端新增竖向 ECharts 箱线图和说人话的说明/悬浮提示；调用结果分布与供应商请求量在左侧上下排列，箱线图在右侧占据对应整列，失败原因分类移到下方，窄屏自动改为单列。
- 同步 domain、persistence、runtime、API、backend-api、shared-types、agent-web 的 README，以及 HTTP API、数据库、指标词汇表和本地测试说明。

### How

- H2 与真实 PostgreSQL 持久层测试共 10 项通过，覆盖 100/200/300/400 等样本的连续分位数和空样本；Controller 测试 3 项通过。前端组件与 API 客户端测试 109 项通过，agent-web/backend-api 类型检查和 agent-web production build 通过。
- 使用 `.env.test`、JDK 25 和脚本现成的 `--without-workflow` 模式完成 21 模块构建并启动 backend、opencode-manager、frontend；标准 Workflow 模式因本机未配置 `WORKFLOW_DEV_REDIS_PASSWORD` 被预检拦截，未修改环境文件。
- 仅在本地测试库插入 7 条 `local-ttft-boxplot` 模拟调用，TTFT 为 120、180、240、320、450、700、1200ms；实际页面显示中间 50% 为 210–575ms、中位数 320ms，并通过应用内浏览器确认左右布局、竖向箱体和明细/聚合无重叠。
- 提交前回顾全部 `.agents/session-log*.md`，并执行差异、冲突标记和空白校验；未覆盖同期 agent-chat 样式与 Playwright 临时文件。

### Result

- 看板现在既能查看 TTFT 的总体区间，又能在同一视野比较调用结果和供应商请求量；箱线图五个位置均来自当前筛选范围的真实调用明细。
- 新增只读 HTTP API 和一次受 31 天最大查询窗口约束的数据库聚合查询；没有数据库结构/Flyway、RunEvent、安全策略或 OpenCode 兼容边界变更，未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

## 2026-08-10 - 降低上下文压缩结果与摘要字号

### Why

- 压缩结果按钮视觉层级高于正常回答正文。源码原先使用 11px，但 agent-web 未分层的全局 `button` 规则把最终计算值覆盖为 14px/500；仅调整 token 数值无法在真实页面生效。
- 展开摘要同时受到 `MarkdownView` 和全局标题、代码样式影响，正文、标题与代码需要按压缩辅助信息范围定向降级。

### What

- 新增 agent-chat 的 9px `--oc-text-2xs` 微型状态 token；压缩结果按钮最终固定为 9px/400、22px 高，图标、箭头、间距和内边距同步缩小。
- 压缩摘要正文固定为 10px，标题为 1.08em，代码和表格为 0.92em；使用压缩组件作用域内的必要优先级覆盖，避免影响普通回答 Markdown。
- 同步 agent-chat README，记录结果按钮和摘要的稳定视觉层级。

### How

- 定向 `FigmaChatPanel` 压缩摘要用例 1 项通过（其余 158 项按筛选跳过），agent-chat typecheck、agent-web production build、AI 文档校验和 `git diff --check` 通过。
- Playwright CLI 在当前仓库真实 Vite 样式下读取最终计算值：按钮 9px/400、22px，摘要正文 10px、标题 10.8px、代码 9.2px。
- 发现 3000 端口由兄弟 worktree `intelligent-test-agent-notification-center` 占用，未停止或改动对方进程；当前仓库改用 3001 独立启动并返回 200，后端 8080 保持健康。

### Result

- 上下文压缩结果和展开摘要都明显低于正常回答正文层级，且不再被 agent-web 全局按钮、标题或代码字号覆盖。
- 本次仅修改 agent-chat 样式 token、组件样式和稳定 README；不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、性能链路、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-11 - 增加 ITL/TPOT 明细与箱线图统计

### Why

- 用户要求按业界口径补齐 ITL/TPOT，并和 TTFT 一样用竖向箱线图展示；SSE 数据块不等于 Token，不能拿 chunk 数近似，否则企业供应商分块策略会直接污染指标。

### What

- 代理在每次流式调用中记录首个与最后一个有效模型输出到达时刻，并读取上游 `usage.completion_tokens`（兼容 `usage.output_tokens`）的准确输出 Token 数；单次 ITL/TPOT 为 `(最后输出时刻-首个输出时刻)/(输出 Token 数-1)`，只纳入至少 2 个输出 Token 的可靠样本，收尾信号等待不计入。
- `internal_model_call_records` 通过 `V20260810234154__internal_model_call_records_add_token_latency_inputs.sql` 增加可空 `last_token_ms/output_token_count` 与非负、时序约束；既有历史记录保持空值。MyBatis XML 新增 ITL 五数分布查询，返回最小值、P25、中位数、P75、最大值。
- 新增受 `SUPER_ADMIN` 保护的只读 `GET /api/internal/platform/opencode-runtime/internal-model-observability/itl-distribution`；明细增加 ITL/TPOT 列。看板左侧调用结果与供应商图上下排列，右侧 TTFT 与 ITL/TPOT 两个竖向箱线图上下排列，窄屏改为单列。
- mock 模型成功流改为两段有效输出并返回准确 usage；同步 domain/runtime/persistence/agent-web README、HTTP API、数据库、指标词汇表和本地验证指南。

### How

- 前端组件与 API 客户端定向回归 110 项通过；JDK 25 下后端持久层、SSE observer、代理转发和 Controller 定向测试通过。H2、真实 PostgreSQL、完整迁移兼容测试此前均通过，源码与最终 Boot JAR 内新 migration SHA-256 一致。
- 使用隔离 PostgreSQL 和 18081 后端启动，readiness 为 `UP`，Flyway 从空库依次执行 95 个 migration 并到达 `20260810234154`；插入 7 条仅用于验证的 mock 明细后，真实 API 返回 ITL 最小值 35ms、P25 50ms、中位数 65ms、P75 90ms、最大值 130ms。
- 应用内浏览器在独立 5173 前端验收：四张图表两列、每列上下排列且程序化检查无重叠；明细 ITL 与箱线图数值一致。临时前端、18081 后端和隔离数据库容器均已停止并清理，未写入 Flyway 演示数据。
- 标准 8080 被兄弟 worktree 的旧后端占用且其数据库含当前分支未知 migration，未停止对方进程、未执行 Flyway repair；agent-web production build 另被同期未提交的 `AgentWorkbench.vue` 类型错误拦截，本次相关 Vitest 已通过，且页面由 Vite 实际编译运行。提交前回顾全部 `.agents/session-log*.md`，不纳入同期重发、agent-chat 与其他前端改动。

### Result

- 看板现可同时查看 TTFT 与 ITL/TPOT 的可靠五数分布，逐条明细也能解释单次 Token 输出节奏；没有准确 usage 的调用明确显示为空，不伪造统计。
- 新增一个只读 HTTP API 和两个可空数据库字段；无 RunEvent 变更，既有超管鉴权、31 天查询上限、30 天明细保留、安全脱敏和旧记录兼容边界不变。未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

## 2026-08-11 - 修复分享会话撤回重发的实时同步与状态污染

### Why

- 分享成员 wr 将“仅答复 OK”撤回并改为“仅答复 123”后，所属人页面仍显示旧正文和“思考中”，只有刷新才恢复；恢复时还会把历史 `hy3-free` 失败卡误挂到当前已成功回答，并曾出现重复用户气泡和发送人归属被所属人 OpenCode 覆盖。

### What

- 重发清理在同一 PostgreSQL 事务中删除源 Run 明细并推进 Session 修订时间；`run.resend.*` 事件补充真实 requester 身份，不再把所属人进程当成分享发送人。
- 分享页与所属人普通工作台在替代 Run 活跃期也会读取权威正文，同一替代 Run 只允许一次刷新，期间实时事件缓冲后重放，避免旧正文、重复气泡和迟到事件。
- reducer 在后续 `run.created/run.started` 到达时清除上一轮失败卡；聊天面板以当前 Run 明确终态优先于全会话历史错误，避免成功后误提示重试。
- 同步 domain/runtime/persistence/frontend README、RunEvent 文档与 HTTP API 实现说明；未改 OpenCode 只读源码、generated SDK、`.env*` 或数据库结构。

### How

- JDK 25 下后端重发服务与 MyBatis 集成定向测试通过；前端 runtime reducer、工作台和聊天面板定向回归 335 passed / 1 skipped，agent-chat 与 agent-web typecheck 通过。
- 会话分享 Playwright E2E 在 Chromium/Firefox/WebKit 共 39/39 通过；真实 wr/888888888 双用户按“仅答复 OK → 撤回改为仅答复 123”验证，新代码下双端都只有一条新 prompt 和一条 123，无重试卡、无思考中，所属人端正确显示发送人 wr。
- 使用 `.env.test` / `test` profile、JDK 25 和 `--without-workflow` 重启 backend、opencode-manager、frontend；共享测试库已执行一个当前 checkout 缺失但在通知中心 checkout 存在的 migration，启动时仅以临时 filesystem location 挂载字节完全一致的原文，未执行 repair、未改历史表。Workflow 因本机未配置 `WORKFLOW_DEV_REDIS_PASSWORD` 按现成脚本跳过。

### Result

- 撤回修改重发现在不需要刷新即可在双端收敛到新正文和当前终态，分享发送人配色/权限保持正确，历史不可用模型错误不再污染后续成功轮次。
- 前端 `http://127.0.0.1:3000` 返回 200，后端 `http://127.0.0.1:8080/actuator/health/readiness` 为 `UP`；wr 页面输入后发送按钮可用，OpenCode 已恢复可用。
- RunEvent payload 仅新增可向后兼容的 requester 字段；无新 HTTP URL、无数据库/Flyway 变更，无新 SQL，现有鉴权、性能与跨服务器路由边界不变。提交前已回顾全部 `.agents/session-log*.md`。

## 2026-08-11 - 修复可观测明细只能查看 20 条

### Why

- 明细接口已经返回完整记录总数，但前端误用当前页数组长度作为分页总数；默认页大小为 20 时，分页组件始终认为只有一页，无法继续查看后续记录。

### What

- 明细分页改用服务端 `PageResponse.total`，查询缓存键同时纳入页大小，确保翻页和切换每页条数都会读取正确数据。
- 保留默认每页 20 条，新增 50/100 条选项；切换每页条数时回到第 1 页，避免原页码超出新范围。
- 补充分页组件回归测试，并同步 `agent-web` 稳定 README。

### How

- 定向 Vitest 2/2 通过，覆盖总数 41、请求第 2 页以及切换到每页 50 条后重置第 1 页。
- Vite development production bundle 构建通过；实际 Vite 服务在 `127.0.0.1:5174` 启动并返回 HTTP 200。
- `agent-web` 类型检查中，本次测试代码没有新增错误；完整检查仍被同期未提交的 `packages/agent-chat/src/runtime-reducer.ts` 既有 `existing` 可能为空错误阻断，未越界修改该文件。

### Result

- 可观测明细现在可以按服务端完整总数翻页，并可按 20/50/100 条切换页大小，不再只能看到首批 20 条。
- 本次仅调整前端分页状态、测试和稳定文档；不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

## 2026-08-11 - 可观测时长统一使用秒

### Why

- 可观测页面此前对短耗时显示毫秒、长耗时显示秒，同一看板存在两种单位；用户要求统一用秒（s）计算和展示。

### What

- 复用既有时长格式化入口，把明细、Overview、供应商汇总和箱线图摘要统一换算为秒；ITL/TPOT 单次值直接按秒计算。
- TTFT 与 ITL/TPOT 箱线图的数据、纵轴、刻度和悬浮提示统一使用秒，而非只替换显示文字。
- 保留 API 的 `*Millis` 原始字段，避免破坏既有客户端；同步 agent-web README 和指标词汇表。

### How

- 定向 Vitest 2/2 通过，覆盖页面不再出现毫秒单位，并断言 TTFT、ITL/TPOT 箱线图实际输入为秒值。
- Vite development 模式构建成功；最新代码重新启动在 `127.0.0.1:5174` 并返回 HTTP 200。
- `agent-web` 完整类型检查仍被同期未提交的 `AgentWorkbench.vue.triggeredByUserId` 和 `agent-chat/runtime-reducer.ts` 空值错误阻断；本次文件未新增类型错误，未越界修改并行工作。

### Result

- 可观测页面所有时长均以秒（s）呈现，短延迟保留最多毫秒级小数精度，非零极短值不会误显示为 0。
- 本次仅修改前端单位换算、图表、测试和稳定文档；不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、安全策略、`.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

## 2026-08-11 - 修复压缩分享会话重发重复与越权

### Why

- 分享会话已压缩上下文时，成员 `wr` 撤回并改发问题后，拥有者 `888888888` 会看到一条正确的成员消息和一条错误归属给自己的重复消息。
- 会话拥有者还能撤回成员 `wr` 实际发送的问题，越过了“仅实际发送人可修改”的权限边界。

### What

- `agent-chat` 统一按平台消息 ID、远端消息 ID 等稳定别名合并历史消息与晚到实时事件；命中持久化消息时保留平台身份和可信发送人归属。
- 前端撤回入口与后端重发服务统一校验最后一条源消息的实际发送人；会话拥有者不再天然拥有成员消息的修改权，分享成员仍需具备 `canChat` 权限。
- 补充压缩历史、助手事件先到、成员事件晚到、拥有者越权和成员正常重发等回归用例，并同步 API、事件流、安全、OpenCode、测试场景、前端包和用户手册文档。

### How

- 后端定向测试通过：`RunResendApplicationServiceTest` 7/7、`RunResendControllerSessionShareTest` 1/1。
- 前端完整 Vitest 通过 1917 项、跳过 1 项；最终改动后定向 Vitest 176/176、`agent-chat` 与 `agent-web` 类型检查、生产构建均通过。
- Playwright 三浏览器相关场景 9/9 通过；最终改动后 Chromium 两个关键场景 2/2 通过。
- 使用 JDK 25 和 `.env.test` 完成 21 个 Maven module 打包；因本机缺少 Workflow Redis 密码，按既有兼容方式以 `--without-workflow` 启动。共享测试库已有当前分支缺失的 Flyway 版本，启动时只读挂载字节一致的原始 migration（SHA-256 `4592eb72a69179ca91febe43278ce8ed70fe02979f7b5c0f7366004048510ca9`），未 repair、未修改历史表或环境文件。
- 后端健康与就绪、前端 HTTP、CORS 预检和 manager WebSocket/进程健康均通过；提交前已回顾全部 `.agents/session-log*.md` 近期记录并确认无合并标记。

### Result

- 压缩分享会话中，成员改发后的问题只保留一条且归属成员，助手答复正常显示；拥有者看不到撤回入口，直接调用接口也会被拒绝。
- 本次收紧既有重发接口鉴权并修复 RunEvent 合并行为，不新增 API/事件字段，不涉及数据库结构、Flyway 变更、关系型 SQL、性能路径、generated SDK、OpenCode 只读源码或 `.env*`，也未新建分支。
- 后端、前端和 manager 当前分别运行于 `127.0.0.1:8080`、`127.0.0.1:3000` 和本地 manager 端口；Workflow 因本机缺少密钥未启动。

## 2026-08-11 - 补全分享重发归因与 compact 边界修复

### Why

- 上一轮交付遗漏了三条真实链路：预留替代 Run 的 wr 归因会在执行阶段再次被所属人覆盖，USER `session_messages` 仍按执行所属人落库，迟到的 compact Part 又可能用不同 message alias 被追加到“仅答复 123”之后。
- 因此前虽有定向测试和一次重启，用户按同一真实场景仍能看到 `仅答复 123 / 123` 落进 compact 边界，并可能由所属人取得错误的回撤入口；上一条日志的完成结论不完整。

### What

- 预留重发 Run 在真正执行时保留重发状态机已确认的发送人，并让 USER 消息投影复用同一归因；新替代 Run 与 `session_messages` 均写为 `wr / shared=true`。
- 手工重发的替代 Run 以当前实际请求者为发送人；历史污染记录在授权、Run DTO、消息 DTO 和前端展示中优先使用共享重发审计 requester，所属人不再继承成员问题的修改权。
- `agent-chat` 对 `message.part.updated/delta` 增加稳定 `partId` 跨 message alias 原位合并；迟到 compact Part 只更新原历史位置，不再插入替代轮次。
- 同步 runtime、API、agent-web、agent-chat README/PACKAGE、HTTP API、安全和会话测试说明；没有新增 URL、DTO 字段、RunEvent 类型、数据库结构或 SQL。

### How

- JDK 25 下 runtime/API 定向回归 93/93 通过；前端全量 Vitest 123 个文件、1918 passed / 1 skipped，compact/所属人关键 Chromium E2E 2/2，agent-web production build 通过。
- 使用 `.env.test`、JDK 25、字节一致的既有 Flyway compatibility location 和 `--without-workflow` 重新构建并重启 backend、opencode-manager、frontend；后端 health/readiness、前端、manager 与所属人 OpenCode 端口均健康。
- 真实 wr 分享页重新执行“仅答复 OK → 回撤改为仅答复 123”：实时完成后连续观察 12 秒，compact 始终只有 1 个且始终在替代轮次之前，OK 消失，最终答复为 123；刷新后 wr 保留自己的回撤入口。
- PostgreSQL 与所属人 API 复核新替代 Run `run_144d915982fb40d3835f4cbb7a3fbbf9`：Run、USER 消息和 resend requester 均为 wr/shared；所属人读取到的最新消息发送人同样为 wr。

### Result

- `compact → 仅答复 123 → 123` 的历史边界在实时事件和刷新恢复两条路径上保持稳定，不再把替代问题或答案包进 compact，也不再产生旧 OK 或第二条远端别名气泡。
- wr 的问题归属和回撤权限保持给 wr，888888888 不再获得该消息的回撤入口；历史已污染记录无需改库即可按 resend 审计兼容展示和鉴权。
- Workflow 因 `.env.test` 未配置 `WORKFLOW_DEV_REDIS_PASSWORD` 未启动；核心 backend、frontend、manager 与 OpenCode 已验证运行。未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

## 2026-08-11 - 增加超级管理员公共个人 worktree 定时补偿

### Why

- 企业用户在 OpenCode 进程初始化后才取得 `SUPER_ADMIN` 角色时，不会再触发初始化链路中的公共个人 worktree 准备，导致数据库和服务器均缺少 `public-{userId}`，公共 Agent 页面无法进入且刷新按钮灰显；现场已发现不止一个用户存在相同历史窗口。

### What

- 每台 Java 启动 30 秒后、此后默认每 10 分钟有界查询本服务器具有 ACTIVE OpenCode binding、用户状态为 ACTIVE、当前角色为 `SUPER_ADMIN` 且缺少同服 ACTIVE 稳定公共个人 worktree 的用户，单轮最多 50 人。
- 补偿与新增 `SUPER_ADMIN` 手工接口复用同一服务器级 Redis 租约和既有稳定 worktree 创建程序；单个用户失败隔离，后续轮次继续重试。
- 后台补偿只基于本机已初始化共享仓库 HEAD 创建 `public-{userId}`，不读取或冒用目标用户 SSH key、不访问远端 Git、不自动切换其当前运行配置。
- 候选关系 SQL 只落在 `AgentConfigMapper.xml`，没有数据库结构或 Flyway migration；同步 domain、persistence、workspace、HTTP API 和包级稳定文档。

### How

- JDK 25 下定向执行 `MyBatisAgentConfigRepositoryIntegrationTest`、`AgentConfigApplicationServiceTest`、`AgentConfigControllerTest`，覆盖角色/binding/服务器/稳定分支筛选、历史 worktree 不阻断、默认周期、Redis 锁、本地无凭据创建和接口鉴权，三模块 Maven reactor 全部通过。
- `mvn clean package -Dmaven.test.skip=true` 的 21 模块构建成功；使用 `.env.test` 与 `--without-workflow` 重启后，当前运行 JAR 已核对包含补偿方法和 MyBatis SQL，后端 readiness 为 `UP`，首次定时任务日志为 `COMPLETED`、候选数 0。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，保留共享测试库既有 Flyway compatibility 处理，不执行 repair、不修改历史表或环境文件。

### Result

- 后续角色补授等漏建场景最多等待一个补偿周期即可自动创建稳定公共个人 worktree；运维也可按目标服务器手工立即触发并获得逐用户安全结果摘要。
- 新接口仅 `SUPER_ADMIN` 可调用并复用公共后端路由；无 RunEvent、数据库字段、Flyway、OpenCode 源码、generated SDK、前端或 `.env*` 变更，也未新建分支。

## 2026-08-11 - 补测普通、分享、手工 compact 与子 Agent 对话

### Why

- 用户要求把“单纯手工 compact、无 compact 的普通对话并分享、普通未分享对话、调用子 Agent 对话”拆成独立真实场景补测，确认上一轮 compact 分享重发修复没有掩盖其它链路。

### What

- 使用 `888888888` 与 `wr` 新建专用真实会话，分别验证普通未分享问答、普通问答后分享并由 wr 继续对话、手工 `/compact`、父 Agent 通过 `task` 调用 `explore` 子 Agent。
- 本次未修改业务代码、API、事件、数据库、配置或稳定文档；仅记录真实验证结论。

### How

- 发现此前另一次无 compatibility location 的启动使后端因缺失已执行 Flyway `20260810170000` 而退出；使用 JDK 25、`.env.test`、字节一致的既有 Flyway compatibility location、`--without-workflow` 重新构建并重启，21 个 Maven module 打包成功，后端 readiness 为 `UP`、前端返回 200。
- 普通未分享会话精确得到 `NORMAL_UNSHARED_OK_20260811` 且无 compaction；普通分享会话精确得到 `SHARE_NO_COMPACT_BASE_OK_20260811`，分享给 wr 并授予可对话权限后，wr 精确得到 `SHARE_WR_REPLY_OK_20260811`，所属人实时同步看到 wr 问答，双端 compaction 数均为 0。
- 子 Agent 会话出现可点击 `Explore` 卡片并完成；进入 child timeline 可见精确回复 `SUB_AGENT_CHILD_OK_20260811`、无可见聊天输入框，主时间线精确回复 `SUB_AGENT_PARENT_OK_20260811`。
- 定向自动化通过：Playwright 的分享管理与原生 compact 命令 2/2；Vitest 的子 Agent 时间线 1/1。

### Result

- 普通未分享、无 compact 分享双端对话、真实子 Agent 三条链路通过。
- 单纯手工 `/compact` 未通过：干净成功会话 `ses_a6564b19614545d1aca603dc466b0eb4` 先显示“正在压缩上下文”，约 30 秒后显示“压缩上下文失败”；后端 `compactSession` 在 30128ms 返回 `OPENCODE_TIMEOUT`。另一个独立会话也复现同类 30 秒超时，排除单条历史污染。
- mock Playwright 的 compact 交互通过，但真实 OpenCode compact 超时，说明尚有运行时/模型链路问题未解决；不得把手工 compact 报为通过。Workflow 仍因 `.env.test` 未配置 `WORKFLOW_DEV_REDIS_PASSWORD` 未启动。

## 2026-08-11 - 新建工作空间前安全同步复用仓库

### Why

- 应用工作空间远端目录树通过远端 Git 读取，能够看到其他提交已新增的一级子目录；保存时服务器却会复用本机旧 feature 仓库，未追平远端就校验目录，因而误报“应用工作区目录不存在”。

### What

- 新建应用工作空间版本复用既有仓库时，先校验工作树干净，再执行 `fetch + pull --ff-only`，追平后才检查目标一级子目录。
- 脏工作树、远端分叉或 Git 失败直接阻断，不自动 stash、reset 或覆盖文件；新 clone 不重复 pull，按固定 target commit 打开的既有副本不触发隐式同步。
- 增加“远端拉取后目录出现”和“脏仓库拉取前拒绝”回归测试，并同步工作空间管理模块 README。

### How

- JDK 21 下 `ManagedWorkspaceApplicationServiceTest` 82/82 通过，工作空间管理模块 31 个测试类全量 Maven reactor 测试通过；JDK 25 下 `mvn clean package -Dmaven.test.skip=true` 的 21 个后端模块全部构建成功。
- 使用 `.env.test` 和 `--without-workflow` 启动；共享测试库已有当前分支缺失的 Flyway `20260810170000`，只读复用既有原始 compatibility migration，并复核 SHA-256 为 `4592eb72a69179ca91febe43278ce8ed70fe02979f7b5c0f7366004048510ca9`，未 repair、未修改历史表或环境文件。
- 后端 health/readiness、前端 HTTP、登录 CORS 和 manager 管理的 OpenCode health 均通过，且没有遗留重启进程。

### Result

- 用户从远端目录树选择已提交的应用一级子目录后，即使目标服务器保留旧 feature 仓库，新建工作空间也会先安全追平远端再继续创建；本地有修改时返回明确冲突并保留现场。
- 本次不改变 API/DTO/RunEvent，不涉及数据库结构、Flyway 文件、关系型 SQL、generated SDK、OpenCode 只读源码或 `.env*`；仅低频新建流程增加一次增量 fetch，既有副本兼容行为保持不变，也未新建分支。
- backend、frontend、opencode-manager 当前已运行；Workflow 因 `.env.test` 未配置 `WORKFLOW_DEV_REDIS_PASSWORD` 未启动。

## 2026-08-11 - 修复分享撤回重发刷新跳动

### Why

- 分享参与者撤回重发时，分享 revision 可能先于替代 USER 消息持久化到达；此前 revision 会立即整会话刷新并消耗一次性刷新机会，得到旧快照后 `run.resend.started` 无法再补拉，B 只能手工刷新才看到新消息。
- 整会话刷新会清空并重建时间线、切换历史加载状态且触发滚动到底部，所以 A 撤回后 B 刷新成新消息的一瞬间，A/B 两个页面都会产生可见跳动。

### What

- `agent-chat` 新增 `run.resend.user.synchronized` 原位同步动作；即使观察端错过 scheduled/requested 事件，`run.resend.started` 也会把源 USER 留作替代 Run 锚点，权威 USER 到达后只替换该气泡并迁移 Todo 归属，不重置其余时间线。
- `AgentWorkbench` 对活跃撤回重发改为按 replacementRunId 轻量补拉权威 USER，复用同一进行中请求并在 revision 早于持久化时进行最多 6 次、每次间隔 250ms 的短重试；revision 与 started 都可触发补偿，成功后有界去重。
- 活跃重发不再调用整会话 `switchSession`，因此不请求会话树、不显示历史加载状态、不改变滚动位置；没有活跃 Run 的 compact revision 仍保留原有权威整会话刷新。
- 增加 reducer 乱序回归和 A/B 分享 revision 先到、USER 后落库的 Playwright 回归，并同步 agent-web、agent-chat 包说明和会话场景测试文档。

### How

- TDD 先确认新 reducer 用例因 started 后丢失源 USER 而失败，再完成实现；`agent-chat` 全量 Vitest 10 个文件、186/186 通过，agent-web 滚动回归 20/20 通过，两个前端包 typecheck 和 agent-web production build 通过。
- 六条相关 Chromium E2E 6/6 通过，覆盖所属人 compacted resend、分享成员编辑重发、compact 摘要刷新、revision/持久化竞态原位同步、所属人权威消息同步和共享 actor/子 Agent/历史增强保留；竞态用例同时断言会话树请求不增加、历史加载不出现且 scrollTop 不变。
- 使用 JDK 25、`.env.test`、既有字节一致 Flyway compatibility location 和 `--without-workflow` 重启，21 个 Maven module 构建成功；backend readiness、frontend 与 opencode-manager 均健康，未修改环境文件或 Flyway 历史。
- 在真实 `888888888`/`wr` 同一分享会话中，A 将 `仅答复 478` 撤回并改为 `仅答复 LIVE_RESEND_INPLACE_20260811_1408`；B 不刷新即看到新问题和最终答案。100ms 连续采样期间 A/B 均未出现历史加载，旧问题消失、B USER 总数保持 5，双端最终 USER 共用平台消息 ID `msg_abbf1a8c8550455992a3b7af91c642c3`。

### Result

- 分享撤回重发现在能在 revision 与持久化乱序下实时收敛；B 无需刷新，A/B 不再通过整会话重载切换新消息，自动化验证滚动位置保持不变。
- 本次没有新增或变更 API、DTO、RunEvent 协议、数据库、Flyway、SQL、安全规则、generated SDK 或 OpenCode 只读源码；性能影响仅限活跃撤回竞态时最多 6 次单页消息补拉，成功即停止。
- backend、frontend、opencode-manager 当前已运行；Workflow 因 `.env.test` 未配置 `WORKFLOW_DEV_REDIS_PASSWORD` 未启动，未新建分支。

## 2026-08-11 - 重启本地服务并确认跨服务器个人工作区根因

### Why

- 用户在分享页返回“新建对话”后，`wr` 首次发送报“工作空间与 agent 不在同一服务器”，并怀疑刚切换网络后本地服务仍沿用旧状态；同时需要确认企业多后台部署是否会遇到同类问题。

### What

- 本次未修改业务代码、API、事件、数据库、环境文件或稳定文档；仅完成三服务冷重启和只读诊断。
- 重启后确认 `wr` 的唯一 ACTIVE 个人工作区 `wrk_6e620f37a7a648f6a3e34293789fa86a` 仍绑定旧 `linuxServerId=192.168.100.115`，物理根路径属于 `/Users/rina/...`；当前 ACTIVE Agent binding 为 `kakadeMacBook-Pro.local:4096`，因此不是刷新或本次网络切换可以安全回绑的同机目录。
- 旧服务器最新后端心跳停在 2026-07-02，且没有个人工作区搬迁记录；自动搬迁只能由仍持有源 worktree 的源服务器发现、导出并传输，源服务器离线时不能在目标机伪造或强制改绑。

### How

- 使用 JDK 25、`.env.test`、`test` profile 和 `--without-workflow` 执行完整构建；21 个 Maven 模块和 agent-web production build 均成功。首次启动因共享测试库已执行但当前 checkout 缺失 `20260810170000` 而失败，未执行 Flyway repair 或改历史表。
- 复用既有临时 compatibility location，并确认其中 migration 与通知中心 checkout 原文 SHA-256 同为 `4592eb72a69179ca91febe43278ce8ed70fe02979f7b5c0f7366004048510ca9`；随后复用刚构建的产物完成 backend、opencode-manager、frontend 冷重启。
- 后端 health/readiness 均为 `UP`，前端返回 HTTP 200，登录 CORS 正确；manager WebSocket 已连接，受管 4096/4104 OpenCode 均为 `HEALTHY`，无遗留重启脚本进程。

### Result

- 本地核心三服务当前可用；Workflow 因本机缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 按官方 `--without-workflow` 模式保持未启动。
- 当前 `wr` 旧工作区的跨服务器冲突仍会保留，正确恢复方式是让旧 `192.168.100.115` 以原稳定身份和原磁盘重新上线完成搬迁，或在当前服务器新建个人工作区；不能通过重启、Flyway 操作或直接改 `linux_server_id` 绕过。
- 企业多后台架构已提供稳定服务器身份、Java 间路由和个人 worktree 搬迁，但必须固定且唯一配置 `TEST_AGENT_LINUX_SERVER_ID`、配置可达的 `TEST_AGENT_SERVER_ADVERTISED_HOST`、保留与身份绑定的持久化本地盘，并在源服务器下线前完成排空/搬迁；当前前端仍会在搬迁窗口误把异服 recent 工作区当作可发送，这是需另行修复的产品缺陷。
## 2026-08-10 - 新增工作台通用通知中心并接入会话分享

### Why

- 被分享人此前需要手工复制分享链接并输入地址，工作台缺少可实时发现、可追溯且不依赖外部渠道的通用站内通知入口。
- 首期需要把会话分享生命周期接入通知，同时保持“成功访问分享后才已读”、服务端受控跳转、跨 Java 低敏广播和既有“分享给我”入口兼容。

### What

- 新增 `test-agent-notification` 模块与通知领域端口，提供创建/去重/更新/失效/已读、未读统计、事务提交后本机及跨节点变化、25 秒心跳、30 秒数据库校准和 90 天清理；HTTP/SSE 与 MyBatis 实现分别位于 API、persistence 模块。
- 新增 `user_notifications` 及 migration `V20260810170000__user_notifications_create_notification_center.sql`，只保存受控 `SESSION_SHARE + shareId` 和安全标题/摘要快照；回填仅覆盖仍有效分享，并且只有严格晚于成员 `shared_at` 的成功读取审计才回填已读。migration 已在本机保留 PostgreSQL 执行，源码、persistence JAR、应用嵌套 JAR SHA-256 均冻结为 `4592eb72a69179ca91febe43278ce8ed70fe02979f7b5c0f7366004048510ca9`。
- 分享新增/重新加入/重新激活创建新通知，普通权限或有效期保存更新现有通知，移除/撤销/归档失效；通过通知、“分享给我”或旧链接鉴权成功后统一幂等已读，已读同步失败只记录低敏告警，不阻断已获授权的访问。
- 前端顶栏新增工行红通知铃铛、角标和约 400px 面板，覆盖全部/未读、分页、加载/空态/错误/失效、键盘、Esc、点击外部关闭与焦点返回；有效分享在新标签页打开 `/s/{shareId}`，复制链接保留为备用。登录身份或工作台模式切换会使旧分页请求失效并清空旧快照，避免跨用户短暂回显。
- 同步 shared-types、backend-api、event-stream-client 以及 HTTP、事件流、数据库、安全、模块依赖和前后端 README；未修改 `.env*`、generated SDK 或 OpenCode 源码。

### How

- 后端通知/runtime/API/MyBatis 定向测试通过：notification 7 项、runtime 24 项、API 3 项、persistence 12 项；真实 PostgreSQL 通知仓储/回填 1 项和全部已知 Flyway history 升级 18 项通过。`mvn clean package -DskipTests` 通过，migration 三处成品 SHA 一致。
- 完整 `mvn clean package` 两次均被本机 Docker Desktop 冷启动/资源时序阻断：一次 MySQL Testcontainers 超过 120 秒，另一次 PostgreSQL 启动与既有并发模型用例超时；运行到的 persistence 129 项没有断言失败。没有通过延长业务断言、`repair` 或 `outOfOrder` 掩盖该环境失败。
- 最终前端全量 Vitest 为 124 文件、1907 passed / 1 skipped；15 个 workspace typecheck、production build 通过，Chromium/Firefox/WebKit 分享专项 27/27 通过。构建仅保留既有 jsdom Canvas 提示和大 chunk 警告。
- 原计划启动命令读取原工作区 `.env.test` 后，Workflow 初始化因数据库账号缺少 `CREATEROLE/CREATEDB` 被环境阻断；未修改环境或数据库权限。随后使用同一脚本 `--without-workflow` 从新 worktree 启动 backend、frontend、manager，health/readiness 均为 `UP`，前端 3000 返回 200，通知未登录为 401，CORS 与 manager WebSocket 正常。
- 提交前回顾全部 `.agents/session-log*.md`，确认未改写已执行 migration、未覆盖其它提交者成果，也未发现冲突标记。

### Result

- 工作台通知中心及会话分享首期链路已实现并由模块、真实 PostgreSQL、前端单测、三浏览器 E2E、生产构建和本地运行态共同验证；广播异常时由初始快照与 30 秒数据库回源恢复，通知不携带任意 URL、分享正文或凭据。
- 当前可验证地址为 `http://127.0.0.1:3000/`，后端为 `http://127.0.0.1:8080/`。本地没有可用的文档示例登录账号，因此未在真实本地库执行登录后的双用户手工流程；该流程已由三浏览器 E2E 覆盖。Workflow 仍因本地数据库管理权限不足未启动，核心通知服务不依赖 Workflow。
- 当前 backend、frontend、opencode-manager 均由 `/Users/kaka/Desktop/intelligent-test-agent-notification-center` 运行；后端 health/readiness 与前端均为 HTTP 200，通知未认证请求正确返回 401，登录 CORS 预检正确。
- manager 在重启窗口重新连接后无持续断线或解码循环；运行中后端已确认加载通知 mapper 与完整 Flyway 主链。端口 4104 的存量未受管健康探测仍返回 `PROCESS_NOT_MANAGED`，不影响本次三服务健康与通知接口。
- Workflow 因 `/Users/kaka/Desktop/intelligent-test-agent/.env.test` 未配置 `WORKFLOW_DEV_REDIS_PASSWORD`，按仓库官方 `--without-workflow` 模式保持未启动；未自动推送。

## 2026-08-11 - 修复通知中心 worktree 的 OpenCode 启动失败

### Why

- 通知中心 worktree 重启后，用户初始化 TestAgent 进程在 `STARTING_PROCESS` 阶段持续返回 `OPENCODE_UNAVAILABLE / 公共 Agent 配置源目录不可用`，manager 未收到 `start` 命令。
- 数据库 macOS 通用参数 `SYS_DATA_ROOT_DIR` 的历史值为 `$TESTAGENT/.testagent`；仅在启动命令设置 `SYS_DATA_ROOT_DIR` 仍不足以改变 Java 的 `$TESTAGENT` 引用解析，脚本默认把兼容变量 `TESTAGENT` 指向当前通知中心 worktree，导致公共配置被解析到只有服务器标识的新空目录。

### What

- 未修改业务代码、`.env.test`、数据库参数、generated SDK 或 OpenCode 源码；从通知中心 worktree 重启时显式设置 `TESTAGENT=/Users/kaka/Desktop/intelligent-test-agent` 与 `SYS_DATA_ROOT_DIR=/Users/kaka/Desktop/intelligent-test-agent/.testagent`，继续复用原测试环境的持久化 OpenCode session 和公共配置。
- 保持 `TEST_AGENT_ROOT=/Users/kaka/Desktop/intelligent-test-agent-notification-center`，因此运行代码和构建产物仍来自通知中心 worktree，仅持久化数据根复用原测试环境。
- 将独立 worktree 的完整启动命令、双变量原因和 Workflow 条件写入 `.agents/skills/restart/SKILL.md` 与 `docs/guides/ai-workflow.md`，不再只依赖会话日志交接。

### How

- 先以数据库初始化进度、backend trace 和 manager 日志交叉确认失败发生在 `OpencodeProcessConfigLinkService.switchToShared`，并核对新 worktree 的公共配置目录缺失、原数据根下 `opencode.jsonc/agents/skills/tools` 完整。
- 使用 JDK 25、`test` profile、原 `.env.test`、`--skip-backend-build --skip-frontend-build --without-workflow` 重新启动 backend、opencode-manager 和 frontend；随后在真实本地工作台以用户 `888888888` 再次点击“启动进程”。
- manager 对 4104 执行 `start` 返回 `STARTED`，公共状态查询在进程短暂预热后连续返回 `HEALTHY`；backend readiness、frontend、OpenCode `/global/health` 和 `/global/config` 均为 HTTP 200，4104 由 PID 54658 实际监听。

### Result

- 工作台显示“TestAgent 进程可用”，地址 `kakadeMacBook-Pro.local / 127.0.0.1:4104`；文件树恢复，运行态加载到 20 个 Skill 和 1 个 MCP，输入框和对话操作重新可用。
- backend、frontend、opencode-manager 三个 screen 会话保持运行；Workflow 仍因 `.env.test` 缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 按既有 `--without-workflow` 模式未启动。
- 后续从该通知中心 worktree 重启并复用原本机测试数据时，必须同时保留 `TESTAGENT` 和 `SYS_DATA_ROOT_DIR` 两个显式值，不能只设置后者。

## 2026-08-11 - 将通知中心分支合并回 release 并切换运行态

### Why

- 用户要求把 `codex/user-notification-center` 的完整内容合并回当前 release，并从 release 工作区重新启动，确保后续验证不再依赖功能 worktree。

### What

- 在 `/Users/kaka/Desktop/intelligent-test-agent` 的 `codex/release-enterprise-20260801` 上以非快进方式合入通知中心提交 `2b8b497b4f6700c47a57014eb1b290bb6d1e4c6c`，生成 merge 提交 `7035e1e1f19339ef53ba60480cc0a920888c16eb`，无冲突；合并后功能分支是 release 的祖先且两者树内容一致。
- 合并范围共 77 个文件、4200 行新增和 24 行删除，包含通知领域模块、MyBatis/Flyway 持久化、分享生命周期、HTTP/SSE、前端通知铃铛、测试和稳定文档；独立 worktree 的 `TESTAGENT`/`SYS_DATA_ROOT_DIR` 启动要求已经写入 `.agents/skills/restart/SKILL.md` 与 `docs/guides/ai-workflow.md`。
- 未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未重命名、重排或改写已执行 migration。

### How

- 合并前确认 release 和功能 worktree 均干净、release 原提交为功能分支祖先，并检查差异不含环境文件或受保护源码；提交前再次回顾全部 `.agents/session-log*.md`，未发现冲突或残留合并标记。
- 从 release 使用 JDK 25、`test` profile、主工作区 `.env.test` 和 `--without-workflow` 完整构建：后端 22 个 Maven 模块 `clean package -Dmaven.test.skip=true` 成功，agent-web production build 成功；功能树在合并前已通过通知/分享后端定向测试、真实 PostgreSQL 18 套历史升级、前端 1926 passed / 1 skipped、15 项 typecheck 和三浏览器分享 E2E 42/42。
- 首次重启虽然 readiness 返回 200，但监听 8080 的仍是功能 worktree 旧 JAR；按 PID 和实际命令路径确认后，仅停止该旧 screen/进程组，再复用已构建 release 产物重启，并二次核对监听进程路径，避免把其它 worktree 的健康响应误当作当前分支启动成功。

### Result

- backend、frontend、opencode-manager 均已由 `/Users/kaka/Desktop/intelligent-test-agent` 运行；后端 readiness 与前端为 HTTP 200，通知未认证请求为 401，OpenCode 4096/4104 `/global/config` 均为 200，且没有遗留重启脚本进程。
- Workflow 因 `.env.test` 未配置 `WORKFLOW_DEV_REDIS_PASSWORD`，按既有 `--without-workflow` 模式未启动；本次未推送远端。

## 2026-08-11 - 基于最新 release 重建企业增量包并补齐 Flyway 成品门禁

### Why

- 用户要求只基于当前本地最新 `codex/release-enterprise-20260801` 重新打企业增量包；开始时分支正在把本地通知中心提交 rebase 到远程 release，不能从冲突中间态构建。
- rebase 后当前主链同时包含通知中心 `20260810170000` 与 ITL/TPOT `20260810234154`，两份 migration 已进入本机需保留 PostgreSQL history，但企业内层、外层和安装复验脚本尚未锁定它们的最终 JAR 字节。

### What

- 完成 release rebase，保留远程会话重发、工作空间等最新修复和本地通知中心 4 个提交；冲突中同时保留通知实时 E2E、历史重发归因回归及双方稳定文档，没有选择整侧覆盖。
- `package-release.sh`、`package-two-backend-complete.sh`、`deploy-internal-release.sh` 新增通知中心和 Token 延迟输入 migration 的固定 SHA-256 门禁；`FlywayMigrationNamingTest` 同步冻结源码字节。
- 企业 README、多后台执行单和数据库文档改为支持三种明确历史：从 `20260809110000` 首次增加四条、已部署会话分享后只增加后两条、四条都成功后的故障重部署不新增 history；任何部分、倒序、未知 checksum 或兼容链混入都停止。
- 使用上一完整包中 checksum 通过的 `.4/.114/.2` 节点配置包，只重建当前 release 的后端、前端和内层发布物；worker runtime/toolbox 指纹未变并标记 `reuse`，Workflow/LobeHub 保持 `disabled`，未携带对应大制品。

### How

- 本机保留 PostgreSQL 只读确认 `20260810170000/-933121365`、`20260810234154/-1179183001` 均成功；真实 PostgreSQL 通知仓储 1/1、已知历史升级 18/18、migration 字节冻结 10/10、Spring Bean 装配 1/1 全部通过，默认 `outOfOrder=false`，未执行 repair 或改 history。
- rebase 后 agent-web typecheck 通过，通知中心/FigmaShell Vitest 61/61、两条冲突相关 Chromium 分享 E2E 2/2 通过；正式打包再次通过后端装配、21 模块构建、前端 `vue-tsc` 和 Vite production build。
- 首轮内外层 ZIP、三台节点包、ZIP CRC、内外层逐字节一致、组件清单及两份新 migration 的 persistence JAR 字节均通过；稳定组件 SHA-256：app JAR `4a80d55a2c0f054a0a44b8210b04b324a8aad554fe68aac32976b5f18c986c9b`，persistence JAR `00044a3d29bc971bdcea19454bf069dfd014599a4e2e7b35eaf06355a4812a10`，XXL integration JAR `d30f9f3c70bd870fb953a01a6d994a6880039fd467c2f3fee6ec41207eaf92f7`，前端归档 `70f212850cb367f07276f0a8d94f8cf4fe6ed9ae902cbfe5e6d78099a5214a10`，`opencode-models.json` `edfa12f1a95da0954f72303e52934efea088b6f64cd834e8447f6e670e88bf86`。

### Result

- 最终包由提交 `20deb0c6e` 的当前 release 内容重封：内层 `test-agent-internal-release.zip` SHA-256 为 `453a98163e53707bd34933e4078ebf6f78dbbec1ddf80c13a1f5e2e25d10db9a`，外层 `test-agent-two-backend-complete.zip` 为 `a7a50459e53c3e6cb623465cc1f36091c66b946b704ef06b9b974aaa311e0a5b`；外层内嵌内层与仓库 dist 逐字节一致。
- 最终独立解包复验确认外层/内层 SHA 文件、两层 ZIP CRC、三台节点包 checksum、组件清单、`opencode-models.json`、部署手册、session log 和两份新 Flyway JAR 资源全部通过；固定名发布物位于 `deploy/internal/dist/`。
- 本条最终 hash 是制品生成后的仓库追溯记录，不再据此重封 ZIP，否则 ZIP 自身 hash 会再次变化；包内已包含前一提交中的完整发布过程记录。
- 本次未修改 migration SQL、生产 API/DTO/RunEvent、环境文件、generated SDK 或 OpenCode 只读源码；企业目标库完整 `flyway_schema_history` 仍必须在部署前取得，未取得前不把包描述为已获现场部署准入。
## 2026-08-11 - 将最新 release 合入记忆分支并补齐迁移兼容

### Why

- 用户要求以本地最新 `codex/release-enterprise-20260801` 为基线重新合并记忆分支，随后再回合 release；最新 release 相比记忆分支已合入点新增 28 个提交、168 个文件，并加入版本更高的 `20260810234154` migration。
- 已部署最新 release 的数据库若仍加载原记忆兼容版本 `20260810173117`，会在默认 `outOfOrder=false` 下拒绝升级，必须为该已执行历史增加严格递增的前向兼容路径。

### What

- 将 release 提交 `380f942343013d41fd46931fa5a8f6288d0d7988` 合入 `codex/qa-agent-memory-v1`；三个文档冲突同时保留通知中心、消息层穿透和最新重发权限语义，没有整侧覆盖业务代码。
- 新增前向兼容 migration `V20260811170050__qa_memories_create_governance_after_token_latency_inputs.sql`，仅在分享历史与 `20260810234154` 已执行、且旧记忆兼容版本未执行时选择；原 `20260810173117` 与新版本 SQL 字节一致，SHA-256 均为 `44ea89c0ea5b9edb7fc5cbfb682e540b251f0c106b1d3c2762576d04ade6f984`。兼容装配拒绝旧/新路径混用，不启用 `outOfOrder`、`repair` 或历史表改写。
- 修复 `MyBatisUserNotificationRepositoryPostgresqlIntegrationTest` 使用固定过期时间导致 2026-08-11 后必然失败的问题：仅将测试通知有效期改为相对当前时钟的一天后，确定性审计时间保持不变；同步 persistence/app README、数据库部署文档和发布包 migration 字节门禁。

### How

- 真实 PostgreSQL 已知历史升级 20/20、Flyway 命名与字节冻结 10/10 通过；通知 PostgreSQL 用例首次准确暴露固定时间失效，修复后单测 1/1 通过。后端相关 notification/workspace/runtime/API 定向测试通过，JDK 25 下 23 模块 `mvn clean package -DskipTests` 成功。
- 前端全量 Vitest 127 文件为 1944 passed / 1 skipped，agent-web typecheck 与含用户手册的 production build 通过；通知实时打开、已读和失效 Chromium E2E 1/1 通过。发布脚本与重启脚本 `bash -n`、AI 文档校验、暂存差异空白检查及两层 JAR migration 字节校验均通过。
- 合并前将用户原有 6 个未提交前端文件安全存入 stash，未把它们纳入本次提交；提交后将恢复为未暂存状态。提交前回顾全部 `.agents/session-log*.md`，未发现需要覆盖的并行成果；未修改 `.env*`、generated SDK 或 OpenCode 只读源码。

### Result

- 记忆分支现兼容“尚未执行 token-latency migration 的 release 历史”和“已执行最新 release migration 的历史”两条严格递增升级链；API、RunEvent 与已有 migration 字节保持兼容。
- 本条只记录 release 到 mem 的集成和验证；mem 回合 release、用户未提交改动恢复以及最终独立前后端启动由同一任务后续步骤完成。

## 2026-08-11 - 将记忆能力回合 release 并切换独立前后端运行态

### Why

- 用户要求在记忆分支吸收最新 release 后，再把完整结果回合 `codex/release-enterprise-20260801`，并使用独立的前、后端进程启动最终 release 代码。

### What

- release 先保持在已核对的本地最新提交 `380f942343013d41fd46931fa5a8f6288d0d7988`（远端 `ee182e9d31b87db164205d8a35c0d8104e4f928c` 为其祖先），再以非快进方式合入 mem 提交 `c8cd006dca5f7b2ff88e346bb8694eb692652972`，生成 merge 提交 `4d5186104bf1643558fd235a4e7a1c02bcd7c2ad`。
- 记忆 worktree 中用户原有的 6 个未提交前端文件已从安全 stash 无冲突恢复，并保持未暂存、未提交；恢复后定向 Vitest 204 passed / 1 skipped、agent-web typecheck 和 6 条 Chromium 工作台回归全部通过，较早的原始备份 stash 继续保留。
- 最终 release 后端独立监听 `18081`，前端独立监听 `3100`；OpenCode manager 作为后端配套进程连接 `18081`，新受管实例在 `4097/4098` 健康运行。Workflow、LobeHub 和可选 QA Memory 数据面未随本次“独立前后端”启动。

### How

- 主工作区用 JDK 25 对最终 release 树执行 23 模块 `mvn clean package -DskipTests`，全部成功；后端从主工作区新生成的可执行 JAR 启动，前端从主工作区 agent-web 的 Vite 入口以显式 `--port 3100` 启动。
- 后端第一次启动因本机 CORS 已是单独通配值、启动命令又追加具体 Origin 而被既有安全校验拒绝；保持通配值原样重启后成功，未修改 `.env.test`。前端通用 `PORT` 被项目 dev 入口固定端口覆盖，改用同一 Vite 入口的显式端口参数后在 3100 成功就绪。
- 旧 manager 退出后遗留的 4096/4104 两个 PPID=1 进程阻塞端口；确认新 manager 已在 4097/4098 建立健康替代实例后，仅终止这两个旧孤儿。最终 8080/3000/4096/4104 均释放，18081/3100/4097/4098 按预期监听。

### Result

- 后端 health/readiness 均为 `UP`，前端 HTTP 200，3100 CORS 预检成功，通知与记忆平台入口未登录均为 401；manager WebSocket 已连接 18081，4097/4098 `/global/config` 均为 HTTP 200。
- 三个独立 screen 为 `test-agent-release-backend-independent`、`test-agent-release-frontend-independent`、`test-agent-release-opencode-manager-independent`；进程日志位于 `.tmp/dev-services-release-independent/`，业务后端日志仍按既有配置写入 `backend/logs/`。
- 本次未推送远端，未修改环境文件、generated SDK 或 OpenCode 只读源码；数据库只执行 Flyway validate，当前保留库已在 `20260810234154` 且无需新迁移。

## 2026-08-11 - 将共享会话撤回重发消息改为后端提交后同步

### Why

- 共享会话撤回重发原先由前端在 Session revision 或 `run.resend.started` 到达后执行最多 6 次、每次间隔 250ms 的消息刷新；信号可能早于平台消息持久化，实时效果依赖本地计时器和数据库竞争结果。
- 当前会话消息同步应由后端提交边界驱动，前端只消费已提交事实，不能猜测落库时机。

### What

- 新增 `SessionMessageRealtimeHub`：在后端事实提交成功后发布只含 `sessionId/runId/traceId/occurredAt` 的安全变化信号，本机直接 fan-out，并复用现有 `ServerBroadcastPublisher` 唤醒其它 Java；不广播 prompt、回答或工具输出。
- `RunResendExecutionService` 仅在源 Run 清理、重发状态 CAS、锁/输入清理和 `run.resend.started` 事实均成功后发布变化；`SessionShareController` 将该信号合入既有分享 runtime SSE，1 秒检查继续作为鉴权和丢信号恢复兜底。
- `AgentWorkbench` 移除 6 次延时重试，收到后端状态或 RunEvent 信号后只调用一次消息分页接口并指定 `refresh=false`，原位替换替代 USER，不重载历史树、不清空时间线或改变滚动位置。
- 同步 runtime/API/agent-chat README、HTTP API、事件流和会话场景测试文档；未新增 URL、DTO、外部 SSE 事件名、数据库字段或 migration。

### How

- JDK 25 Maven 定向回归通过：`SessionMessageRealtimeHubTest` 2 项、`RunResendExecutionServiceTest` 4 项、`SessionShareControllerTest` 5 项，共 11 项；覆盖提交/回滚、跨 Java 过滤、CAS 失败不广播和后端信号即时唤醒 SSE。
- `corepack pnpm --filter @test-agent/agent-web typecheck` 通过；Chromium 用例 `session share synchronizes an edited resend after the backend commits its message change` 1/1 通过，并断言仅请求一次 `refresh=false`、不加载历史树且滚动位置不变。
- 使用 JDK 25、`test` profile、`.env.test` 和 `--without-workflow` 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build --without-workflow`：23 个后端模块完整打包成功，Spring 装配、后端 readiness `8080` 和前端 `3000` 均正常；未修改环境文件。

### Result

- 共享会话撤回重发的权威 USER 现在由后端提交后信号驱动同步，不再受前端重试窗口和落库竞争影响；跨节点广播失败时仍由周期检查与 SSE 重连恢复。
- 本次不涉及数据库结构、SQL、外部 API/DTO/RunEvent wire schema、性能敏感正文广播或鉴权放宽；未修改 generated SDK、OpenCode 源码和用户已有的通知/弹框/工作空间未提交改动。

## 2026-08-11 - 弱化分享链接并增强通知已读辨识

### Why

- 对话分享已有工作台站内通知主链路，分享者不应再被大块链接输入框引导到手工复制；备用链接仅应作容灾入口。
- 分享成员较多时弹框需要按屏幕空间调整；通知列表需通过图标直接区分已读、未读和失效状态。

### What

- `SessionShareDialog.vue` 移除突出的完整 URL 输入区，仅在分享成功后于底部提供低强调“复制备用分享链接”；复用既有剪贴板工具和分享地址派生逻辑。
- 分享弹框支持右下角双向拉伸，并以视口最小/最大高度、body 内滚动和移动端仅纵向拉伸保证可用性。
- `UserNotificationCenter.vue` 使用闭合信封/展开信封/禁用图标区分未读、已读、失效，同步增加状态 `aria-label`、隐藏文字和稳定的测试属性；“失效但从未访问”不伪装成已读。
- 同步 agent-web README/PACKAGE、前端规范、模块图、会话场景测试文档和用户手册。

### How

- 通知组件定向 Vitest 3/3 通过；Chromium、Firefox、WebKit 会话分享专项 E2E 6/6 通过，覆盖弹框拉伸、备用链接弱化和通知未读→已读→失效表现。
- 前端全量 `lint`、`typecheck`、production `build` 通过；Vitest 127 个文件、1944 passed / 1 skipped。用户手册构建也通过。
- 用 JDK 25、`test` profile、根目录 `.env.test` 与 `--without-workflow` 从当前 release 工作区重启；后端 readiness 8080、前端 3000 均为 HTTP 200，通知接口未登录返回 401，OpenCode 4097/4098 `/global/config` 均为 200。启动时发现并收敛一个不持有子进程的重复 manager，最终仅保留一套健康进程树。

### Result

- 分享默认通过站内通知到达，备用 URL 退居次要操作；分享弹框可拉伸，通知已读/未读/失效在视觉与读屏语义上都可辨识。
- 纯前端交互与文档调整，无 HTTP API、SSE 事件、DTO、数据库、SQL、鉴权或性能热路径变更；未修改 `.env*`、generated SDK 或 OpenCode 源码。并行工作区修改保持未暂存、未纳入本次提交。

## 2026-08-11 - 修复应用源码管理位置与首次打开详情

### Why

- 应用级工作空间菜单点击应用代码库“管理”后，紧凑列表仍沿用旧工作区入口的左下角固定定位，与入口迁到顶部后的交互不一致。
- 应用源码 `open` 已创建并返回 Runtime Workspace，但首次打开尚未产生 Session 引用；普通工作区详情查询只查个人或已有会话引用范围，随后请求详情时误报 `Workspace 不存在`。

### What

- `AppSourcePicker.vue` 改为固定在顶部上下文舱下方并水平居中，保持原有紧凑列表和管理流程不变。
- `UserWorkspaceQueryService` 保留原个人/会话详情查询优先级；未命中时复用现有 `ConversationWorkspaceAccessAuthorizer` 的权威分类，仅允许已通过完整成员、generation、expiry、READY replica 与服务器绑定校验的 `APP_SOURCE` ACTIVE Workspace 回退主表，`STANDARD` 继续返回 `NOT_FOUND`。
- 增加首次打开 APP_SOURCE 成功和 STANDARD 不越权两条后端单测，并在 Chromium 应用源码工作台用例中锁定弹框顶部位置；同步 workspace-management README、HTTP API、agent-web PACKAGE 和应用源码测试文档。

### How

- JDK 25 Maven 定向回归 `UserWorkspaceQueryServiceTest,AppSourceWorkspaceAccessTest` 通过；`test-agent-api -am package -DskipTests` 与启动脚本执行的 23 模块 `mvn clean package -Dmaven.test.skip=true` 均成功。
- AppSourcePicker Vitest 3/3、agent-web typecheck、production build 通过；Chromium 应用源码快照完整工作台用例 1/1 通过，并验证弹框纵坐标位于顶部区间。
- 按 `test` profile、根目录 `.env.test` 与 `--without-workflow` 启动当前代码：后端 readiness 8080、前端 3000、CORS 和 OpenCode 4097/4098 正常。既有独立发布栈继续占用 18080，导致新栈可选 XXL Job 管理进程端口冲突、聚合 health 为 DOWN；未擅自停止并行栈。

### Result

- 应用源码管理列表现在位于页面上方；首次打开已授权源码快照不再因尚无 Session 引用而被普通详情接口误判不存在，同时未扩大其它 Workspace 的读取范围。
- HTTP URL、请求/响应字段和事件 wire 均未变；无数据库、SQL、migration、性能热路径或安全边界放宽，不修改 `.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-11 - 修复分享会话撤回重发乱序消息归并

### Why

- 企业部署的原始输出显示，分享会话含 compact 历史时，OpenCode 原生 `message.removed` 可能先于 durable `run.resend.started` 到达；旧 reducer 会提前删除已经交给替代 Run 的 USER 锚点。
- 后续不含正文的 USER envelope 被忽略，紧随其后的 USER text part 因找不到稳定归属而误挂到上一条 assistant，权威消息刷新后页面又出现正确用户气泡，形成一瞬间错位和重复。

### What

- `runtime-reducer.ts` 在 removed 命中已绑定 replacement Run 的 USER 时，仅清除旧平台/远端消息别名并保留原位锚点；`run.resend.started` 复用同一别名迁移程序。
- USER text part 在 role envelope 之前到达时，优先按 `replacementRunId` 接回未绑定的重发 USER，再回退文本匹配，禁止合入上一轮 assistant。
- 新增包含 compact 历史、源 USER/ASSISTANT 删除早于 started、空 envelope、part 提前及平台权威同步的 reducer 回归；同步 `agent-chat/src/PACKAGE.md` 稳定说明。

### How

- `runtime-reducer.test.ts` 74/74 通过，`agent-chat` typecheck 通过；完整包测试为 186 passed / 1 个既有 `MarkdownView` 标题断言失败，本次未修改 Markdown 路径。
- agent-web production build 通过；Chromium 的分享 + compact + 撤回重发 + 延迟原生事件回归 1/1 通过。
- 使用 JDK 25、`test` profile、`.env.test` 与 `--without-workflow` 重启本地服务，后端 readiness 与前端均为 HTTP 200；真实 `Test message` 页面确认 `仅答复123` 仅出现一次，位于 USER 气泡且不在 assistant Markdown 中。

### Result

- transient 删除与 durable started 任意交错时，替代用户问题都保持单一、原位归并，不再短暂落入上一条助手回复或在权威刷新后重复。
- 仅调整前端 RunEvent 投影和测试文档；没有变更 HTTP API、RunEvent wire schema、DTO、数据库、SQL、migration、鉴权、环境文件、generated SDK 或 OpenCode 只读源码。企业环境需重新构建并部署前端制品后生效。

## 2026-08-11 - 通知中心默认展示未读

### Why

- 用户要求打开站内通知时优先处理未读，“全部”作为后续查历史的次要筛选。

### What

- 复用既有 `UserNotificationFilter` 与同一分页请求，将 `UserNotificationCenter`、`FigmaShell`、`AgentWorkbench` 的默认值统一为 `UNREAD`，首次请求直接携带 `unreadOnly=true`。
- 筛选标签调整为“未读 / 全部”；未读处理完后保持空态，用户切到第二个标签才查看已读和失效历史。
- 同步 frontend/agent-web README 与用户手册，不新增组件、API 或本地筛选分支。

### How

- `UserNotificationCenter.test.ts` 3/3 通过，agent-web `vue-tsc` 通过；Chromium、Firefox、WebKit 通知专项 3/3 通过，覆盖默认 `unreadOnly=true`、标签顺序与切换“全部”后的已读展示。
- agent-web 含用户手册的 production build 通过；仅保留既有超大 chunk 提示。
- 使用 JDK 25、`test` profile、根目录 `.env.test` 和 `--without-workflow` 重启。前一独立 release 后端仍占用 18080/9999，导致当前 8080 的聚合 health 为 503；明确终止该旧独立 Screen 后再次重启，最终 health/readiness/liveness、前端均为 200，4097/4098 OpenCode 均为 200，且仅保留当前 backend/manager/frontend 三个 Screen。

### Result

- 通知面板现在默认只显示未读，“全部”排在后面；筛选切换继续复用后端分页能力。
- 纯前端默认交互变更，无 HTTP API、SSE 事件、DTO、数据库、SQL、鉴权、性能热路径或向后兼容契约变更；未修改 `.env*`、generated SDK 或 OpenCode 源码。

## 2026-08-11 - 恢复任务完成后的耗时展示

### Why

- `TaskUsage` 仍保留并由工作台提供当前或终态 `duration`，但 `FigmaChatPanel` 后来的展示条件只读取 `totalDuration` 或 tokens；兼容调用方只带锁定 `duration` 时，任务结束后整行耗时会消失。

### What

- 任务消耗 footer 继续优先展示累计 `totalDuration`，缺失时复用既有 `duration` 作为兼容回退；展示条件同步识别该字段，不新增计时状态、接口或工具方法。
- 将原有静态终态用例收紧为仅传 `duration`，断言任务结束后仍显示静态标记和耗时；同步 agent-web README 的兼容说明。

### How

- TDD 红灯确认旧实现会隐藏仅有 `duration` 的任务消耗，修复后定向用例通过；完整 `FigmaChatPanel` 测试 159 passed / 1 skipped，agent-web typecheck 与 production build 通过。
- 以 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 5173 --strictPort` 启动实际前端目标，`http://127.0.0.1:5173/` 返回 HTTP 200；提交前已回顾全部 `.agents/session-log*.md`，并保留工作区中其它未提交后端修改不纳入本次提交。

### Result

- 已完成任务即使只提供锁定 `duration`，也会继续显示耗时；同时保留 `totalDuration / tokens` 现有主路径和累计语义。
- 纯前端兼容性修复；未变更 HTTP API、RunEvent、DTO、数据库、SQL、migration、性能或安全边界，未修改 `.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-11 - 运营分析趋势与小时热力随视口适配

### Why

- 运营分析总览的趋势项最小宽度和小时热力 24 列方格都使用固定像素，导致宽屏留下大量空白、窄屏无法按内容区平滑缩放。

### What

- 趋势图复用现有时间点数据和原生 CSS Grid，按实际点数生成弹性列；少量数据填满内容区，高密度数据保留最小列宽及局部横向滚动。
- 小时热力继续保持 24 小时语义列，方格随内容宽度缩放并保持正方形，2K/4K 下单格最大 64px，避免无限放大；同步 agent-web README 和组件回归测试。

### How

- 定向 Vitest 3/3 通过；使用真实 Vite + Chromium 在 640、1440、2560px 三档视口测量，页面无整页横向溢出，趋势稳定 7 列，热力稳定 24 列，方格约为 22、53、64px 且宽高一致。
- 正式 Vite 入口运行在 `http://127.0.0.1:4179/`，根页面和运营分析组件模块均返回 HTTP 200。完整 agent-web production build 被同一工作树中并行修改的 `InternalModelObservabilityPanel.vue` 未定义符号阻断，本次未修改该文件。

### Result

- 趋势和小时热力已按实际可用宽度适配常见窄屏、桌面和 2K/4K 视口；无 HTTP API、事件、DTO、数据库、SQL、migration、安全或环境配置变更，未修改 generated SDK 和 OpenCode 只读源码。

## 2026-08-11 - 内部模型时延按厂商对比并补全 ITL Overview

### Why

- TTFT 与 ITL/TPOT 原先把不同模型厂商合成一个箱体，无法看出厂商差异；ITL/TPOT 又沿用了秒单位，且新指标没有出现在 Overview。
- 明细列表默认只展示 20 条，必须明确保证聚合指标和图表不会拿当前页数据计算。

### What

- 两张竖向箱线图复用既有分布接口，按当前全量统计中的 Provider 分别查询并绘制，一个厂商一个箱体；TTFT 保持秒，ITL/TPOT 在明细、Overview、箱线图、坐标轴和提示中统一使用毫秒。
- 分布响应兼容性新增 `averageMillis`，MyBatis XML 直接对全量合格明细计算平均值；Overview 新增 Avg ITL/TPOT 与 Max ITL/TPOT，不读取当前页明细。
- 前端回归加入两个厂商和不同分布，并在当前页只有 1 条时断言 Overview 仍展示后端 540 次全量聚合；测试指南补充全量翻页和独立 Python 连续分位数复算方法。

### How

- 前端定向 Vitest 2/2、agent-web typecheck、development Vite build 通过；后端 domain/API/H2 persistence 定向测试 13/13 通过，JDK 25 全后端 package 通过，AI 文档校验通过。
- PostgreSQL Testcontainers 用例在本机等待 Docker 容器期间持续无输出，人工终止，未把该项记作通过；同一 `avg`/`percentile_cont` SQL 已由 H2 定向测试和后端编译覆盖，生产 PostgreSQL 用例保留新增平均值断言。
- 正式 Vite 入口运行于 `http://127.0.0.1:5174/` 并返回 200；Playwright 未登录访问会跳转企业统一认证，当前机器访问内网站点返回 502，因此无法取得真实登录态看板截图。

### Result

- 看板现在能直接比较每个模型厂商的 TTFT 与 ITL/TPOT 分布，并在 Overview 查看全量可靠 ITL 的平均值与最大值；明细分页只影响列表。
- API 仅新增可忽略的响应字段，无 URL、事件或数据库结构变化；未新增 migration，未修改 `.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-11 - 修复共享会话撤回重发需等待模型流才同步

### Why

- 重发创建阶段只预留替代 Run 和 Redis 输入，平台 USER 直到模型执行入口才落库；共享变化信号又在 `run.resend.started` 后发布，观察方只能等思考或流式输出出现后才看到修改后的问题。
- 取消或明确投递失败后没有对应的已提交消息通知，观察方也无法可靠恢复源 USER/ASSISTANT；跨节点迟到通知还可能覆盖较新的恢复状态。

### What

- 重发预约事务内原子创建替代 Run、持久化实际发送人归因的 USER、写入 scheduled 事实并推进 Session 修订；事务提交后立即广播 `sessionId/sourceRunId/replacementRunId/changeType/revision`，模型执行只按稳定远端消息号复用投影。
- 分享 runtime SSE additive 暴露不含正文的 `messageChange`；新增按 Session + Run 精确读取替代 USER 和完整源轮次的两个鉴权 HTTP 接口。取消或明确失败删除未投递替代 USER、推进修订并发送 `RESEND_RESTORED`。
- 前端按 replacement Run 原位同步 USER，恢复时按 source Run 原位还原 USER/ASSISTANT；按 Session 维护修订水位并在异步响应返回时二次校验，拒绝迟到旧通知。同步更新 API、事件、后端模块、前端和测试文档。

### How

- JDK 25 下受影响后端模块从干净构建通过 147 项测试，最终增量复跑仍为 runtime 110、API 32、persistence 5 全通过；前端相关 Vitest 207 项、全仓 typecheck 通过。
- Chromium 共享场景覆盖 12 轮长历史、模型未启动时即时同步、取消恢复、无历史 loading/滚动跳变及迟到旧修订，连续 3/3 通过。
- 首次按 `.env.test` 启动被既有工作流密钥文件缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 阻断；按项目规范在本次不验证 Workflow 的前提下使用 `--without-workflow` 重启。后端 health/readiness 为 UP、前端 3000 返回 200、CORS 正常，manager WebSocket 与 4097/4098 OpenCode 恢复健康且无解码/重连循环错误。
- 提交前已回顾全部 `.agents/session-log*.md`，未发现冲突或合并标记；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

### Result

- 共享参与者在重发事务提交后即可看到修改后的 USER，不再依赖模型思考或流式输出；取消和明确失败会恢复原轮次，重复及乱序通知不会回滚较新界面状态。
- HTTP/SSE 仅做 additive 扩展并复用既有分享鉴权；未修改数据库结构或 Flyway migration，新增查询通过 MyBatis XML 使用现有 Run 索引，不在通知中传播消息正文。

## 2026-08-12 - 修复撤回重发运行中重复用户消息

### Why

- 现场原始输出证明，预约阶段合成的替代 USER `message.updated` 会比 `run.resend.started` 早约 23 秒到达；此时前端替代轮次锚点仍保留源消息别名，旧归并条件无法命中并追加了第二条 USER。
- 运行结束后的后端历史只返回一条 USER，确认问题发生在发送方和共享接收方复用的前端实时投影，不是数据库重复落库。

### What

- USER 事件在 `run.resend.started` 前即可按 `replacementRunId` 复用已迁移的替代轮次锚点；权威 USER 同步时统一折叠同一替代 Run 下的候选消息，并迁移 Todo owner 和快照映射。
- 未携带消息类型的迟到 part 仍只允许命中无远端消息号的锚点，避免 ASSISTANT part 误挂到 USER。
- 新增贴合现场事件顺序的 reducer 回归，以及发送方、共享接收方在模型开始前和运行完成后的单 USER 断言；同步包 README 和对话场景测试文档。

### How

- 相关 Vitest 3 个文件 209 项通过，Chromium 重发/共享同步 Playwright 2 项通过，全前端 typecheck、lint 和 `git diff --check` 通过。
- JDK 25 后端 23 个模块 `mvn clean package -Dmaven.test.skip=true` 通过；使用 `.env.test` 和 `--without-workflow` 启动实际服务，后端 health/readiness 为 UP、前端 3000 返回 200、CORS 正常，manager 与 4097/4098 OpenCode 进程恢复健康。
- 首次完整启动仍被本机 Workflow 密钥缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 阻断，本次功能不涉及 Workflow，故按项目文档排除该服务；提交前已回顾全部 `.agents/session-log*.md`，未发现与本次文件范围冲突。

### Result

- 发送方和共享接收方在撤回重发预约、模型思考、流式输出及完成后均只保留一个替代 USER 气泡。
- 本次仅修正前端投影兼容逻辑；无 HTTP API、RunEvent 线协议、DTO、数据库、SQL、migration、鉴权、安全或环境配置变更，未修改 generated SDK 和 OpenCode 只读源码。

## 2026-08-12 - 基于当前本地 release 重新生成企业增量包

### Why

- 用户要求按当前工作区代码重新打包；本地 `codex/release-enterprise-20260801` 的源码 HEAD 为 `f10754e01ab8f846a8aa2430214bb39f4795623b`，相对上一成品新增共享会话重发实时同步、重复消息修复、通知筛选、运营图表和内部模型时延等已提交改动。
- 当前本地分支相对远程为 ahead 10 / behind 32；本次按“当前代码”语义不拉取、不切换，工作区无未提交源码和冲突。

### What

- 重新构建后端、前端和内层发布 ZIP，并使用上一完整包中 checksum 通过的 `.4/.114/.2` 节点配置包重新封装固定名外层包。
- worker runtime 指纹 `50f56c54991bd7d5b3926fcb8442655b3ca1371a56ec6165a9ec19626f672fb1`、toolbox 指纹 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040` 均未变化，按 `reuse` 不重复携带大制品；Workflow、LobeHub 保持 `disabled`。
- 当前新增提交没有增加或改写 Flyway SQL；成品仍完整携带并冻结上一交付已验证的企业主链与兼容资源。

### How

- JDK 25 下后端从 `clean` 开始运行受影响测试：工作区查询 3、runtime 35、API 32、persistence 27、真实 PostgreSQL Flyway 已知历史升级 18、Spring 装配 1，全部通过且无跳过；Flyway 字节冻结 10 项包含在 persistence 结果中。
- 首次未带 `clean` 的测试受另一个分支遗留 `target/classes/MemoryLearningEvidenceMapper.xml` 干扰，并在并行 worktree 占用 Docker 时出现 PostgreSQL 容器启动超时；确认源码不存在该 Mapper/Row 后清理当前 reactor target，待并行任务结束后完整重跑通过，未为构建残留修改业务代码。
- 前端 15 个 workspace typecheck 全部通过；受影响 Vitest 7 个文件 376 passed / 1 skipped；正式打包再次通过 Spring 装配、21 模块后端构建、`vue-tsc` 和 Vite production build。
- 独立解包复验通过外层/内层 SHA 文件、两层 ZIP CRC、三台节点包 checksum、外层内嵌内层逐字节一致、组件清单、`opencode-models.json`、稳定组件逐字节一致及最终 persistence JAR 的受保护 migration SHA。

### Result

- 内层 `test-agent-internal-release.zip` SHA-256 为 `99f34a5652d5972dd4dbc1e9384026d1a1a78a8cc4bb1caa702a6c99cc000df5`；外层 `test-agent-two-backend-complete.zip` 为 `e6b9133c023355e220e5b190ebf33c5d3be447d570c6231e944228fddeb60d56`。
- 稳定组件 SHA-256：app JAR `83fa5c91cbedbfc82ec7a856bb93ca3be449c0234238cb742672f7dea3be0491`，persistence JAR `444536695c6707263cf8b1962721273e107f40a6fcffbf9b23b9be44343916be`，XXL integration JAR `fd4acf7b5d1660dcbaf20a86c8b9bc85d5fde5e02615b1921071b8af418037f6`，前端归档 `f4dd64aea469a241cd4cd17ac9abe6ffb86313076ce238a1c259b14b00c35b58`，`opencode-models.json` `edfa12f1a95da0954f72303e52934efea088b6f64cd834e8447f6e670e88bf86`。
- 本次任务没有修改生产代码、API、事件、数据库、migration、配置模板、`.env*`、generated SDK 或 OpenCode 只读源码；仅新增发布追溯记录。目标企业库完整 `flyway_schema_history` 仍需在现场部署前取得并通过准入判断。
- 本条记录在最终制品生成后提交，不再据此重封 ZIP，否则归档内 session log 与 ZIP hash 会形成递归变化；成品源码内容对应 `f10754e01`。

## 2026-08-12 - 应用源码快照支持从顶部直切测试工作空间

### Why

- 顶部“工作空间”菜单在源码快照中已经列出测试工作空间，但 `AgentWorkbench` 仍以 APP_SOURCE 禁止版本选择为由拒绝事件，用户只能先点击左下角“返回应用工作区”再切换。
- 企业现场确认“管理”弹层仍在左下角是包含该修复的前端未发布成功，不是企业浏览器缓存或样式覆盖。

### What

- 顶部选择既有测试工作空间改为“离开源码快照”的显式导航，复用既有 managed intent、Git 权限预检、默认个人工作区、recent 清理和 `switchWorkspace` 流程；源码模式下新增应用版本仍保持禁用。
- Chromium 场景改为在源码文件编辑后直接操作顶部菜单，并断言 Git 访问检查、默认个人工作区解析、源码 recent 清理及测试工作区能力恢复。
- 同步 agent-web README、包说明和应用源码验收文档；现场截图中的 Hub 更新角标 409 另行定位为源码 Workspace ID 被传给仅接受个人测试工作区的统计接口，异常已被前端捕获，不阻断本次管理或切换流程，但能力库角标/当前应用更新页会受影响，本次只按用户要求完成影响诊断，未扩展修复范围。

### How

- 定向 Vitest 15 passed；Chromium 应用源码工作台场景 1 passed；前端全量 127 个测试文件、1951 passed / 1 skipped；全仓 typecheck 和 production build 通过，`git diff --check` 通过。
- 按 `.env.test` 完整重启被本机工作流密钥缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 阻断；未修改或替换环境文件。已有 test profile 服务继续健康：backend health/readiness 200 UP、frontend 3000 返回 200、CORS 正常，manager 当前 4097/4098 health 均为 HEALTHY。
- 提交前回顾全部 `.agents/session-log*.md`，未发现与本次文件范围冲突；未修改 `.env*`、API、RunEvent、数据库、SQL、migration、generated SDK 或 OpenCode 只读源码，也未新建分支。

### Result

- 用户现在可从应用代码库源码快照顶部直接选择测试工作空间，不再依赖左下角返回动作；管理弹层顶部定位沿用前一提交，企业环境需成功发布包含该提交的前端制品后生效。
- 本次是纯前端导航逻辑和测试/文档变更，向后兼容；没有新增接口、事件或持久化结构。

## 2026-08-12 - 按最新代码更新内置用户手册

### Why

- 现有手册已覆盖近期的会话分享通知和上下文压缩，但没有独立说明已经上线的长期记忆中心；任务耗时、共享撤回重发同步和源码快照切回测试工作空间也缺少最新用户口径。
- 用户要求以当前代码为事实重新更新系统手册，并保持稳健、易理解的表达。

### What

- 新增长期记忆专题，说明开放范围、自动学习、实际使用标识、个人/团队记忆、证据、暂停归档和 Skill 提案，并同步注册到 VitePress 导航、应用内 Help 与宠物问答。
- 更新首页、功能总览、快速开始、对话、工作空间和常见问题，补齐通知中心、任务完成耗时、共享重发原位同步及源码快照顶部直切测试工作空间。
- 同步 frontend、agent-web、user-manual 的 README/PACKAGE，并为新增 Help 主题补充回归测试。

### How

- 逐项对照 `MemoryCenter.vue`、`FigmaChatPanel.vue`、`AgentWorkbench.vue`、帮助中心注册和相关测试，只写用户能看到且当前代码已经实现的入口与边界。
- `@test-agent/user-manual` 构建、agent-web typecheck/production build、Help/MemoryCenter 20 项 Vitest 和 `tools/verify-ai-docs.sh` 全部通过；手册开发服务运行于 `http://127.0.0.1:3001/help/`，构建产物确认包含新章节、导航和关键文案。
- 提交前回顾全部 `.agents/session-log*.md`，并保留工作区中并行出现的运营分析代码、API 文档和 README 改动，不纳入本次提交。

### Result

- 用户可从系统手册导航直接查到长期记忆完整操作，也能通过内嵌 Help 和宠物问答使用同一份说明；原有章节已与当前工作台行为对齐。
- 本次不新增或变更 HTTP API、RunEvent、DTO、数据库、SQL、migration、鉴权、安全和性能逻辑；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

## 2026-08-12 - 扩大运营分析趋势范围并补齐热力数据

### Why

- 运营分析前端默认只查询最近 7 天，后端趋势与小时热力又只返回存在 rollup 数据的桶，导致趋势天数少、无活动日期断档，热力图也只出现零散格点。

### What

- 前后端默认查询范围统一扩大为最近 30 天，前端新增 7/30/90/180 天快捷范围并保留自定义时间。
- 趋势查询在服务层补齐范围内的零值时间桶；小时热力固定返回周一至周日、每天 0–23 时的 168 个格点，峰值排行仍只包含真实活动时段。
- 同步 runtime、agent-web README 与 HTTP API 文档，并补充后端服务和前端组件回归测试。

### How

- 后端 `AnalyticsQueryServiceTest` 6 项、前端运营分析组件 4 项、agent-web typecheck 和 production build 全部通过；JDK 25 下完整后端 clean package 成功。
- 使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend；因本机缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 按启动规范使用 `--without-workflow`，backend health/readiness、frontend 3000、登录 CORS 和 manager 健康检查均通过。
- 提交前回顾全部 `.agents/session-log*.md`，保留并行的用户手册修改，不纳入本次提交；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

### Result

- 运营分析默认即可看到约一个月的完整趋势轴，用户可一键扩大到 90/180 天；无活动日期显示为零，热力图始终保持完整 7×24 坐标。
- 本次仅调整既有查询接口的默认范围和返回补零行为，不新增 URL、DTO 字段、RunEvent 或数据库/SQL/migration；最多 500 点、日粒度最多 180 天等既有限制保持不变，向后兼容。

## 2026-08-12 - 撤销误合入的 Mem0 长期记忆能力

### Why

- `4d5186104` 合入的 Mem0 长期记忆属于误合并，用户要求撤销或保证启动完全忽略，同时明确不能丢失 huangzhenren 后续提交和近期部署成果。
- 已执行的 QA Memory/Flyway 历史可能存在于个人库和企业库，不能通过删除、改名或改写 migration 来回退，否则会破坏既有 `flyway_schema_history` 和后续升级兼容。

### What

- 撤销 Java 记忆模块、领域模型、治理 API、前端记忆中心与管理页、Mem0/Embedding 独立服务、开发编排、离线制品和发布入口；平台启动脚本不再识别、探测或启动这些服务。
- 保留工作台、工具箱、系统管理、Hub、设置等稳定路由，以及体验工作区、应用 Git/进程重启通知、发布校验和近期用户手册/运营分析等后续成果。
- QA Memory 主 migration、兼容副本、checksum 校验、既有表和数据全部保留，只作为已执行数据库历史兼容，不再被业务运行链读取或写入；同步后端、前端、API、部署、数据库和用户手册文档。

### How

- `tools/verify-dev-scripts.sh`、`tools/verify-ai-docs.sh`、变更脚本 `bash -n`、前端 workspace typecheck 与 production build 通过；后端定向 Reactor 21 个模块构建成功，相关 108 项测试通过。
- 前端全量 Vitest 为 1950 passed / 1 skipped / 1 个既有 Help 文案断言失败；Chromium 稳定路由场景受当前体验引导弹层和根路径基线行为影响未通过，均未扩展修改无关功能。
- 真实 PostgreSQL 兼容测试 23 项中 9 项通过、14 项被共享工作区并行新增但未提交的 `V20260812144051__common_parameters_default_experience_workspace.sql` 阻断，统一原因为缺少 `${SYS_DATA_ROOT_DIR}` Flyway placeholder；本次未修改任何已跟踪 migration，也未把该并行文件纳入提交。
- 从提交态独立 worktree 使用 JDK 25 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build --without-workflow`：22 个后端模块 `clean package` 成功，backend health/readiness 为 UP、frontend 3000 返回 200、登录 CORS 为 200，manager WebSocket 已连接；进程与启动脚本均无 Mem0/Embedding/pgvector。未排除 Workflow 的首次启动被本机 PostgreSQL 初始化权限阻断，本次功能不涉及该服务。

### Result

- Mem0 运行能力、入口与部署物已从本次提交范围撤销；历史 Flyway 资源和数据保持可恢复、可升级，后续代码及近期部署内容不随误合并回退消失。
- 提交前已回顾全部 `.agents/session-log*.md`；共享工作区中并行出现的体验工作区、Git 提交和 migration 改动完整保留在未暂存区，不纳入本次提交。未修改 `.env*`、generated SDK 或 OpenCode 只读源码，也未新建分支。

## 2026-08-12 - 平台体验区改为全员常驻并提供无 push 的本地 Git

### Why

- 用户最终确认体验区不再限制是否有应用或是否首次进入；每个后端服务器只维护本机一套共享目录，用户使用其 OpenCode 进程所在服务器的目录，后续修改不做跨服务器同步或启动重置。
- 体验目录必须在每次后端启动时幂等保证存在 `.git` 和一个初始文件；Git 可暂存、回退、解决冲突和 commit，但平台不得提供 push。

### What

- 移除应用成员资格门禁并增加所有用户可见的常驻“平台体验”入口；加入应用不再退出体验，显式切换应用时才关闭体验文件连接。
- 新增启动 Runner 和通用本地仓库初始化：默认目录为 `${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/experience`，缺仓库时 `git init -b main` 并提交 `README.md`，已有 HEAD、文件和用户改动不覆盖、不重置、不创建 remote。
- 体验区复用既有 Git diff、stage/unstage、discard 和冲突程序，新增仅提交指定路径的本地 commit API；前端隐藏 push/发布入口，后端不新增体验 push 接口，也不进入个人 worktree publish 链路。
- 新增 `V20260812144051__common_parameters_default_experience_workspace.sql`，只把历史 `UNCONFIGURED` 默认值迁移为服务器本地目录，保留管理员自定义值；同步 HTTP API、事件流、数据库、部署、安全、模块图和各模块 README/PACKAGE。

### How

- 后端定向 Reactor 回归通过：真实 Git 17、workspace 服务 97、API 25、启动 Runner 1、迁移种子 7；H2 种子回归 8/8。真实 PostgreSQL 体验仓储 3/3，Flyway 当前主链、已执行旧体验历史和未知 checksum 失败关闭 3/3。
- 前端 agent-web typecheck 通过；backend-api/体验状态/文件树/Git 面板 Vitest 180/180；独立端口 Chromium 验证“已有应用用户随时进入”和体验文件/对话/本地 Git 无 push 两个场景 2/2。
- 使用 JDK 25、`.env.test` / `test` profile 完整构建并重启两次；因 `.env.test` 缺少 `WORKFLOW_DEV_REDIS_PASSWORD`，按研发规范显式使用 `--without-workflow`，未修改环境文件。backend health/readiness、frontend 3000、登录 CORS 和 manager WebSocket 均通过。
- 实际目录已验证 `.git`、`README.md`、`main` 和初始提交存在，remote 数为 0、状态 clean；第二次启动前后 HEAD 与 README SHA-256 不变。提交前回顾全部 `.agents/session-log*.md`，未发现冲突或残留合并标记。

### Result

- 任意已登录用户可随时进入其 OpenCode 所在服务器的共享体验区；两台服务器按同一默认内容各自初始化，后续独立共享和修改。
- 平台层面支持本地 Git commit 及除 push/发布外的既有 Git 操作。该边界不是 OS shell 沙箱：若用户通过开放的终端自行配置 remote，需由企业网络/主机策略继续约束外连。
- 本次新增向后兼容 HTTP API 和前向 Flyway 数据迁移，不新增 RunEvent 类型或表结构，不修改 `.env*`、generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 修复体验区误跳回与重复提示

### Why

- 用户反馈体验区的“刷新文件树”菜单点击后不收起，新建文件双击或 Git 暂存期间又经常跳回原应用工作区；首次成功体验后再次进入仍重复展示“暂不体验”。
- 根因是体验工作区主动清空 `selectedAppId` 后，成员应用 focus/定时刷新和源码 recent 恢复仍会把它误判为“未选择应用”，触发默认应用补选并覆盖体验选择。

### What

- 体验入口在用户点击时立即推进应用选择代次并失效旧源码 intent；体验等待期和活动期阻断默认应用补选、进程 READY 托管重试与 focus 源码恢复，迟到响应不能再切回 `MANAGED`。
- 体验 Workspace 真正成功打开后才按用户写入浏览器本地知晓标记；后续从顶栏用户手册左侧的烧瓶图标直接打开，应用下拉移除重复入口，不再显示含“暂不体验”的说明弹窗，失败尝试和本地存储不可用仍安全退化。
- 文件树“…”中的刷新与拉取先关闭原生 `details` 再判断是否发请求；刷新已经进行时也能点击收起，但不会重复刷新。
- 同步前端工程 README、agent-web README/PACKAGE、前端规范和模块图；未修改 HTTP API、RunEvent、数据库、SQL、migration、安全边界或后端业务代码。

### How

- 定向 Vitest 覆盖体验状态、文件树与顶栏入口；Chromium 工作台回归覆盖 focus 刷新保持体验选择、首次成功后的直接重入、顶栏图标入口和刷新菜单收起；agent-web typecheck 与 production build 均通过。
- 在真实本地页面验证：刷新菜单收起、双击 `测试.md` 保持平台体验、unstage/stage 后仍在体验区、切回 F-COSS 后再次进入无弹窗；服务重启后重新登录复查同样直接进入。
- 按 JDK 25、`.env.test`、`test` profile 完整构建并重启；首次被缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 拦截后按规范使用 `--without-workflow`，backend health/readiness、frontend 3000、登录 CORS 和 manager WebSocket 均通过。提交前回顾全部 `.agents/session-log*.md` 近期记录，未发现冲突或残留合并标记。

### Result

- 体验选择现在由显式 intent 保护，不再因文件或 Git 操作恰逢后台刷新而跳回传统工作区；首次成功进入后只保留常驻直接入口。
- 当前服务运行于 `http://127.0.0.1:8080` 和 `http://127.0.0.1:3000`，可直接复验；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，未新建分支或推送远端。

## 2026-08-12 - 返回测试工作区时自动恢复默认版本

### Why

- 用户从应用源码或体验工作区回到测试工作区后，应用名和工作空间可以恢复，但 recent 缺少 `versionId`、没有已有 default worktree 或旧工作区详情未回填版本归属时，顶栏仍停在“请选择版本”，必须手工再选一次。

### What

- 非托管工作区返回时优先复用 per-app recent；没有可进入 recent 时，仅在返回语义下读取默认启用测试工作空间和后端排序首版本，并复用既有 Git 权限预检、`ensureDefaultPersonalWorkspace` 与托管切换流程。
- `ensure-default` 已返回的应用/模板/版本上下文用于兼容补齐旧工作区详情缺失字段；应用切换时同步清理旧版本缓存，避免异步恢复后迟到清空新 `versionId`。普通首次选择应用仍保持无 recent 的空态约定。
- Chromium 回归覆盖体验区撤出和源码区统一返回入口在无 recent 版本时自动落到 `2024年1月`，并断言 Git 预检与默认个人 worktree 请求均只走既有链路。

### How

- agent-web `vue-tsc`、两个定向 Chromium 场景和 `git diff --check` 通过；JDK 25 下以 `.env.test` 完整构建并重启 backend、opencode-manager、frontend。首次启动因缺少 `WORKFLOW_DEV_REDIS_PASSWORD` 被阻断，按规范显式使用 `--without-workflow` 重试。
- backend health/readiness 均为 UP，frontend 3000 返回 200，登录 CORS 预检返回允许源，manager WebSocket 已连接且最终健康检查为 `HEALTHY`。

### Result

- 从体验区或应用源码返回测试工作区不再要求重新选择版本；有 recent 时仍保持原选择，没有可进入 recent 时自动进入默认首版本。
- 本次仅修改前端状态编排、回归测试和稳定说明；不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、性能、安全或环境配置，未修改 generated SDK、OpenCode 只读源码，也未新建分支。

## 2026-08-12 - 体验烧瓶入口支持再次点击撤出

### Why

- 用户希望顶栏烧瓶按钮直接承担进入和撤出体验区，不再需要打开应用下拉后选择传统工作区。

### What

- 进入体验区前暂存当前应用；体验中烧瓶按钮改为“退出平台体验”，再次点击复用既有应用选择、文件连接关闭和切换代次恢复原应用工作区。
- 原应用已不可用时按全局 recent 或首个可用应用降级；用户没有任何应用时清理体验 Workspace 状态并回到“未选择应用”普通空态，仍可再次点击进入。
- 同步 frontend、agent-web、包级说明和前端规范；补充组件状态、恢复原应用和无应用退出后再次进入的回归覆盖。

### How

- 定向 Vitest 通过 2 个测试文件、64 个测试；定向 Chromium 通过应用源码返回、有应用体验切换和无应用体验切换 3 个场景；agent-web `vue-tsc` 和 `git diff --check` 通过。
- 在真实本地页面登录验证“进入平台体验 → 退出平台体验”，再次点击后恢复 F-COSS / 本地-测试 / 20260618；backend 与 frontend 沿用当前已启动的本地测试服务。
- 补充复核全量 Vitest 的失败项后，本次体验测试与两个 Markdown 测试定向复跑均通过；仍有 1 个任务外 `help-center.test.ts` 既有文案精确引号断言失败（正文是“超级管理员专属的用户管理”，测试要求中文引号），本次未修改用户手册或扩展修复范围。

### Result

- 烧瓶入口现在是可逆双态开关，退出不会再依赖应用下拉；体验区既有本地 Git 和无 push 边界不变。
- 本次不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、性能、安全或环境配置，未修改 generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 企业增量包补齐最新 Flyway 制品闸门

### Why

- 用户要求基于上一已部署版本重新打企业增量包；当前 release 已加入 QA 历史顺序补偿、通知处置类型和体验工作区 migration，但内层打包、外层封装及节点安装脚本尚未全部锁定这些最新资源，存在包内 SQL 缺失或旧字节未被发布流程提前拦截的风险。

### What

- 三层发布脚本统一复用既有 JAR resource 校验程序，新增 QA 主链/两条 release 补偿、通知处置类型、旧体验候选 compatibility location、体验前向结构与默认目录 migration 的固定 SHA-256 校验。
- `FlywayMigrationNamingTest` 同步锁定主目录 QA 三条资源、通知处置和体验新旧三条资源；没有改写任何 migration 文件。
- 更新企业 README 和双后台手册，将上一已部署包记录为业务源码 `f10754e01ab8f846a8aa2430214bb39f4795623b`、内层 ZIP SHA-256 `99f34a5652d5972dd4dbc1e9384026d1a1a78a8cc4bb1caa702a6c99cc000df5`，并明确从 `20260810234154` 只允许连续新增 `20260811170050`、`20260811213000`、`20260812104911`、`20260812144051`。

### How

- Shell 语法、`git diff --check`、Flyway 字节锁和通用参数 migration 测试通过；真实 PostgreSQL `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 完整历史矩阵通过。
- 体验工作区、通知、运营分析和后台 API 定向前端回归 192/192 通过，用户手册与 agent-web production build 成功；Spring Bean 构造器发布扫描通过。
- 组件计划确认 worker runtime 指纹 `50f56c54991bd7d5b3926fcb8442655b3ca1371a56ec6165a9ec19626f672fb1`、toolbox 指纹 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040` 均为 `reuse`；Workflow 和 LobeHub 保持关闭。

### Result

- 企业增量发布现在会在构建、外层封装和安装三处拒绝最新 Flyway 资源缺失或字节不一致；QA Memory/Mem0 运行功能仍已撤销，保留的 QA migration 仅用于不可变历史兼容。
- 以提交 `48745138b57d504831a8b0727c87e133f5b4269a` 完成最终封包：内层 `test-agent-internal-release.zip` SHA-256 为 `eb5d2607551628c100b36b0f14ad66f7ce0bd91fe48ee81616858bae8a8b9ab3`，固定三节点外层 `test-agent-two-backend-complete.zip` SHA-256 为 `6f3bfcf92bba6147594bf5f3d19f28fb637e30296d48bb38a236d26de32f6e75`；两份 checksum 校验和 ZIP 完整性检查通过，外层内嵌内层字节一致。
- 应用 JAR、前端归档、persistence JAR 与 `models.json` 的 SHA-256 分别为 `c879445f0cd5692bc320fb2565ae29234bbee148a5ab1d4be4b802eb52f12298`、`e26819798f7af496f534daf4cfe121aee9cacda1c83c5c67ba50012802620265`、`9bb5538bd5846e74ee1874e1937d628580aa1507c6070b58d6d7344ed2b97e3b`、`edfa12f1a95da0954f72303e52934efea088b6f64cd834e8447f6e670e88bf86`；worker/toolbox 复用上一已部署版本，Workflow/LobeHub 继续关闭。
- 本次不修改 `.env*`、generated SDK、OpenCode 只读源码或业务 migration；未新建分支。打包期间并行出现的未提交 `PetMiniGames.vue` 弹珠游戏改动晚于本次制品构建，已原样保留在工作区且未纳入发布包、暂存区或提交。

## 2026-08-12 - 宠物游乐舱新增桌面弹球

### Why

- 用户要求在当前项目的既有小游戏中再增加一个桌面弹球，并保持原有宠物浮层入口与权限边界。

### What

- 直接扩展 `PetMiniGames.vue` 既有纯前端游戏容器，新增带木框、黄铜导轨、纸张计分牌的桌面弹球；支持按住蓄力、松开发射、左右挡板、三枚弹珠、三组碰撞计分、暂停和重开。
- 弹球物理、蓄力与按键状态只保存在组件内存，切换游戏、关闭浮层或卸载组件时清理计时器；继续复用 `SUPER_ADMIN` 小游戏入口，不新增活动栏按钮、接口或持久化。
- `PetMiniGames.test.ts` 增加蓄力发射、弹珠位移、挡板键盘与暂停回归；`workbench.spec.ts` 将弹球纳入五游戏连续切换的 Chromium 场景。同步 frontend、agent-web README 与包级说明。

### How

- `PetMiniGames` 定向 Vitest 7/7 通过；独立 3017 端口 Chromium 工作台回归 1/1 通过，并用真实 390px 宠物浮层截图检查球台、状态栏和控制区无溢出或遮挡。
- agent-web `vue-tsc`、用户手册与 production build 通过；构建仅保留既有大 chunk 警告。最终前端以 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3018` 启动，HTTP 返回 200。
- 提交前回顾全部 `.agents/session-log*.md`，确认上一条增量包记录已明确保留本次并行弹球改动；任务外自动生成差异 `frontend/apps/agent-web/components.d.ts` 未纳入本次提交。

### Result

- 宠物游乐舱现提供俄罗斯方块、扫雷、数独、贪吃蛇和桌面弹球五款游戏，弹球同时支持鼠标/触控指针与键盘操作。
- 本次不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、后端、性能、安全或环境配置；未修改 generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 桌面弹球升级为完整规则球台

### Why

- 用户反馈首版桌面弹球玩法过于简单，希望补充真实弹球台常见机关、目标循环和风险收益规则，而不是只增加装饰元素。

### What

- 继续复用 `PetMiniGames.vue` 的既有游戏入口、生命周期清理和权限边界，将单球状态扩展为逐球独立坐标、速度和碰撞冷却的多球物理；新增甜区技能发射、A/B/C 翻滚灯与挡板移灯、旋转门、三枚落靶、左右弹射三角、内外道和任务球门。
- 按“点亮 A/B/C → 清空落靶 → 命中球门”的循环解锁三球多球模式，并补充最高五倍倍率、限时碰撞连击、9 秒救球、球末奖励分、15000 分奖励球、晃台警告与三次连续操作触发 TILT；暂停、切换游戏、关闭浮层和组件卸载继续释放物理及蓄力计时器。
- 重做木框、黄铜、纸质记分牌和局内状态条视觉，在 390px 宠物浮层内展示任务、倍率、连击、救球、场上球数和完整控制区；同步 frontend、agent-web README 与包级说明，并补充规则单测和 Chromium 工作台交互回归。

### How

- `PetMiniGames` 定向 Vitest 7/7、agent-web `vue-tsc`、用户手册与 production build 通过；独立 3022 端口 Chromium 工作台回归 1/1 通过。
- 使用 390px 真实浏览器浮层截图检查 A/B/C、三组碰撞器、落靶、旋转门、球门、弹射器、挡板、发射杆和五键控制区，无溢出或遮挡；最终前端以 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3021` 启动，HTTP 返回 200。
- 提交前再次回顾全部 `.agents/session-log*.md`，确认未覆盖并行成果；任务外自动生成差异 `frontend/apps/agent-web/components.d.ts` 保持未暂存。

### Result

- 桌面弹球已形成可重复推进的技能发射、翻滚灯、落靶、多球和球末奖励流程，指针与空格、Z/左键、斜杠/右键、N、P 键均可操作；复杂规则具有自动化断言，不依赖后端或服务端状态。
- 本次不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、后端、性能、安全、兼容性或环境配置；未修改 generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 校准桌面弹球下坡重力

### Why

- 用户实玩后反馈弹珠没有重力；轨迹回归确认旧参数下静止球前 160ms 只下落约 2.5px，视觉上接近漂浮。

### What

- 继续复用 `PetMiniGames.vue` 既有 16ms 物理循环，将 316×420 球台的下坡重力从 178 调整为 680，并同步重标定最高速度、蓄力发射初速、自动救球回射和多球散射速度，避免只增强重力后破坏技能发射、救球或多球玩法。
- 组件测试新增两段连续 160ms 的下落加速断言，并验证自动救球能越过发射导轨、三球多球均向上散开；Chromium 工作台回归直接读取弹珠渲染纵坐标，确认越过最高点后 200ms 内明显回落。
- frontend、agent-web README 与包级说明同步明确球台坡度重力；没有新增组件、物理引擎、接口或持久化路径。

### How

- 旧参数先由定向 Vitest 稳定复现 RED（首段下落 2.496px，小于 5px）；修正后 `PetMiniGames` 7/7、agent-web `vue-tsc`、3025 端口 Chromium 工作台回归 1/1 和 production build 均通过。
- E2E 与 production build 并发时会争用用户手册 `.vitepress/.temp` 并产生临时模块缺失，因此最终按顺序重跑并全部通过；构建仅保留既有大 chunk 提示。
- 最新前端以 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3024` 启动，HTTP/原生 Playwright 页面探测返回 200。

### Result

- 弹珠现在有肉眼可见的持续向下加速度，弱发射会快速回落，正常蓄力可越过顶部，自动救球和多球仍能重新进入上半场。
- 本次不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、后端、安全或环境配置；仅调整前端本地物理参数，不影响服务端性能与兼容性。未修改 generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 修复弹珠反复掉回发射槽

### Why

- 用户实玩发现弹珠总会掉回最开始的发射槽，无法持续停留在主球台；复核确认弹珠没有“是否仍在发射槽”的身份，顶部边界只给较弱横向速度，且主球台到发射槽之间缺少单向门。

### What

- 继续扩展既有 `PinballBall` 和 16ms 物理循环，为每颗球增加仅在内存维护的 `inLaunchLane` 状态；首次到达发射槽顶部时复用顶部碰撞分支模拟弧形回转导轨，明确把球送至右侧竖直导轨左侧并切换为已离槽。
- 已离槽弹珠在顶部试图向右穿入发射槽时，由单向门边界反射回主球台；同时将最低发射速度提高到短按也能越过增强后的坡度重力，蓄力幅度仍参与技能区判定。
- 单测覆盖首次出槽和单向门反射，Chromium 回归使用 100ms 短按并读取实际渲染横坐标，验证弹珠位于发射导轨左侧；同步 frontend、agent-web README 和包级说明。

### How

- 首轮新增状态断言稳定失败为 `inLaunchLane=undefined`；实现后组件定向回归通过。首轮 Chromium 又准确暴露 100ms 短按初速不足，弹珠横坐标仍为约 279px；提高最低发射速度后，同一浏览器用例通过且主球台横坐标小于 270px。
- 最终 `PetMiniGames` Vitest 7/7、agent-web `vue-tsc`、3027 端口 Chromium 工作台回归 1/1 和 production build 均通过；构建仅保留既有大 chunk 提示。
- 最新前端以 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3028` 启动，HTTP/原生 Playwright 页面探测返回 200。

### Result

- 弹珠现在从发射槽到达顶部后会可靠进入主球台，离槽后不能从上方重新掉回发射槽；正常底部漏球、自动救球、三球多球和下坡重力规则保持不变。
- 本次不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、后端、安全或环境配置；仅调整前端本地物理状态，不影响服务端性能与兼容性。未修改 generated SDK、OpenCode 只读源码，也未新建分支或推送远端。
## 2026-08-12 - 按正确方向将最新 release 合入 mem0

### Why

- 用户澄清本次集成方向应为 `release → mem0`；此前把 mem0 合入 release 的本地操作方向相反。
- 远端 release 已包含错误方向的合并提交 `4d5186104` 和其后的记录提交 `605899a4a`，同时又新增了 10 个业务提交，不能直接丢弃后续成果，也不能在未获授权时强推改写远端历史。

### What

- 先生成可恢复 bundle，再以错误合并前的 release `380f94234` 为基线，完整重放后续 10 个 release 提交；得到不包含 mem0 祖先 `c8cd006dc` 的纯净本地 release `f10754e01`。
- 将该纯净 release 以非快进方式合入 `codex/qa-agent-memory-v1`（mem0）；唯一文本冲突位于本提交者会话记录，业务代码、API、前后端和稳定文档均自动合并，并保留双方有效记录。
- 合并带入 release 最新的共享会话重发即时同步、通知中心、内部模型时延分布、工作区查询等代码及其 HTTP/SSE、模块、前端和测试文档；没有新增数据库结构或 Flyway migration。

### How

- 祖先与内容校验确认：纯净 release 含原基线及其后 10 个提交、mem0 不再是其祖先；错误历史与重放后历史的文件级 name-status 完全一致，安全 bundle 位于主工作区 `.tmp/git-safety/`。
- JDK 25 定向后端测试在 21 模块通过（domain 1、workspace 3、runtime 35、memory 33、API 36、persistence 23），全后端 23 模块 `mvn clean package -DskipTests` 通过；AI 文档门禁通过。
- 前端全仓 typecheck 和 production build 通过；完整 Vitest 为 1950 passed / 1 skipped / 1 个 editor 并发偶发失败，失败文件单独复跑 9/9 通过。Chromium 定向场景中通知实时同步、共享撤回重发、手工重发 3 项通过。
- 另有 2 个存量 Chromium 用例稳定失败：mem0 首页已固定为 `/workbench` 但旧分享用例仍断言 `/`；固定 CHAT 下拉未声明 `clearable` 但旧记忆用例点击清除按钮。相关路由、组件和断言在合并前 mem0 `c8cd006dc` 已同时存在，本次未越界修复。
- 使用主工作区只读 `.env.test`、JDK 25、共享 `TEST_AGENT_ROOT/TESTAGENT/SYS_DATA_ROOT_DIR`，从 mem0 工作树以 `--with-memory --without-workflow` 独立启动 backend、frontend 和 opencode-manager。backend health/readiness、frontend 3000、CORS、4097/4098 OpenCode 及 Memory VIP/CPU BGE/pgvector 均通过，Memory `rawMessageCount=0`。
- 合并前属于用户的 6 个未提交前端文件通过独立 safety stash 原样恢复并继续保持未暂存；恢复后相关 Vitest 为 204 passed / 1 skipped，agent-web typecheck 和 4 个 Chromium 原生重发/只读命令场景均通过。

### Result

- 本地 release 已恢复为纯净 release 主线，最新 release 已按正确方向集成到 mem0；实际运行服务均来自 mem0 工作树。
- 用户原有 6 个工作区改动未进入合并提交，已在最新 release 基线上恢复，可继续开发。
- 远端 release 仍含错误方向历史，修正它需要明确授权后执行受保护的 `--force-with-lease`，本次没有推送或改写远端；Workflow 因本机缺少其独立 Redis 密钥未启动。
- 未修改 `.env*`、generated SDK 或 OpenCode 只读源码；API/SSE 仅包含 release 已文档化的 additive 兼容扩展，数据库结构、安全边界和 migration 无变化。
## 2026-08-12 - 新增 ARM64 本地 OpenCode 客户端与内网 HTTP 分发

### Why

- 需要允许用户通过独立 client key 将多个本地 ARM64 客户端接入平台，在本地用户权限内监管一个 OpenCode 1.18.4 进程并注册多个本地工作空间；浏览器仍只访问后台 Java，后台不能直接扫描用户磁盘。
- 企业环境需要由 Nginx 通过内网明文 HTTP 分发 macOS Apple Silicon、麒麟 ARM 客户端，客户端安装时必须校验签名和固定版本依赖的 SHA-256。

### What

- 从提交 `18864a51b` 新建 `/Users/kaka/Desktop/intelligent-test-agent-local-opencode-client` worktree 和 `codex/local-opencode-client` 分支；主 worktree 的未提交后端、前端改动未复制、清理或暂存。
- 新增 `test-agent-local-client-protocol`、`test-agent-workspace-filesystem`、`test-agent-local-client` 三个 Maven 模块，实现 `local-opencode-client.v1` 反向 WSS 协议、连接 generation fencing、心跳/取消/背压、OpenCode HTTP/SSE 与文件 RPC、本地模型中继、loopback 进程监管及稳定实例身份。
- 后台新增单用户 client key 的创建/复制/轮换/撤销、客户端实例与连接路由、短期模型 grant、本地工作空间注册和文件 ticket；运行、会话、夜间任务及 OpenCode 路由增加向后兼容的 `RuntimeKind`/实例目标并禁止离线时回退服务端实例。关系型持久化全部使用 MyBatis XML，并新增 `V20260811210453__local_client_credentials_create_runtime.sql`。
- 前端设置页增加密钥和实例管理、本地工作空间注册；头像菜单同时展示服务端和本地 OpenCode，按 capability 关闭本地首版不支持的终端、Git、Agent 配置、附件和协作入口。
- 新增固定版本 Temurin JRE 21、OpenCode 1.18.4 的 ARM64 打包与签名清单、无 root 安装脚本、LaunchAgent/systemd user 服务及 Nginx `/downloads/local-opencode-client/` 配置；同步工程/模块 README、HTTP API、事件协议、数据库、安全、架构及部署文档。

### How

- 后端相关定向测试、客户端真实 supervisor 测试、跨 Java 精确路由、文件系统安全、生命周期、夜间任务、密钥/脱敏与兼容测试通过；修正 H2 夹具后持久层回归 297 passed / 19 skipped。真实 PostgreSQL 覆盖已知 Flyway 历史的 20 项升级测试通过，源码、持久层 JAR 和最终 Spring Boot JAR 内 migration SHA-256 均为 `b4ae9ca6d8dbe04ebe058ab7b01841e30c2880231e858b6233e3571d62848970`。
- `test-agent-app -am -DskipTests package` 和客户端 shaded JAR 构建通过，`java -jar ... --version` 输出 `test-agent-local-client 0.1.0`；真实 OpenCode 1.18.4 在 Apple Silicon Mac 上完成 loopback 启停、端口冲突、PID/启动时间和重启恢复验证。
- 前端全量 Vitest 127 files、1946 passed / 1 skipped，workspace typecheck、lint、production build 通过；分发脚本完成签名、哈希和本机 HTTP 安装测试，Nginx 配置通过 `nginx -t` 及隐藏文件、目录索引、缓存策略反例校验。
- 完整 Maven 回归确认三个基线问题与本次无关：H2 模型表夹具缺 `embedding_dimension`、PostgreSQL 模型用量并发用例在全量负载下 20 秒超时、分享用例固定 8 月 9 日过期时间；两个既有 MySQL 8.4 Testcontainers 场景在本机启动超过 120 秒。提交前已回顾全部 `.agents/session-log*.md` 并确认没有覆盖近期成果。

### Result

- 代码、协议、API、数据库、前端和内网 HTTP 分发主链路已实现并在 Apple Silicon 与真实 PostgreSQL 上部分验证；未修改 `.env*`、generated SDK 或 `opencode-source/opencode-1.18.4`，未 push。
- 当前 worktree 没有独立 `.env.test`，且主 worktree 的 8080/3000 服务正在使用，未擅自停止或覆盖，因此没有在该 worktree 启动完整后台/前端/客户端三服务链路。缺少真实 ARM 麒麟设备，麒麟安装、systemd user、认证连接、文件 CRUD 和聊天修改文件的最终验收仍需在目标机执行，不能声明完整可交付。

## 2026-08-12 - 整理 release、mem0 与 client 为 dev

### Why

- 用户要求直接推送 release，并把 release、mem0 和本地 OpenCode client 汇总为单一 `dev` 分支；`dev` 的 Workflow 与 LobeHub 必须默认关闭。
- release、mem0 和 client 已形成三套提交历史及 Flyway 顺序，主、mem0 工作树还分别存在用户未提交文件，整理时不能覆盖或混入提交。

### What

- 将 `codex/release-enterprise-20260801` 直接推送到 `origin`，以其最新提交创建独立 `dev` worktree；通过非快进合并保留 mem0 与 client 的完整祖先关系，并恢复 release 曾撤销的 Mem0 实现、整合 client 实际差异。
- 修正合并产生的 `SessionApplicationService` 重复方法和 `WorkspaceFileServiceTest` 嵌套语法/共享文件内核错误码断言；没有修改 OpenCode 只读源码或 generated SDK。
- `restart-dev-services.sh` 将 Workflow 与 LobeHub 都设为默认关闭，分别只在显式 `--with-workflow`、`--with-lobehub` 时启用；Mem0 数据面保持既有 `--with-memory` 显式启用。脚本校验和后端/部署/AI 工作流文档同步更新。
- 实际保留库启动发现 release 已执行到 `V20260812144051`、但缺少 client 的低版本 `V20260811210453`。复用唯一 `DatabaseMigrationCompatibilityCustomizer`，新增隔离的 `V20260812202425` 前向 migration；不启用 `outOfOrder`、不执行 `repair`、不改历史表，并用 SHA-256 测试锁定两条路径。

### How

- 后端完整 26 模块 `mvn package -DskipTests` 通过；Workspace 文件服务 40 项通过，client/Mem0/API 定向测试 48 passed / 1 skipped。新增迁移后 `FlywayMigrationNamingTest` 12/12、真实 PostgreSQL `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 24/24 通过。
- 前端 agent-web typecheck、覆盖 release/client/mem0 交集的 7 个 Vitest 文件 255 passed / 1 skipped、9910 模块 production build 均通过；`tools/verify-dev-scripts.sh` 全部通过。
- 使用 JDK 25、主工作区只读 `.env.test` 和共享数据根，从 dev 工作树执行 `./restart-dev-services.sh --profile test --env-file /Users/kaka/Desktop/intelligent-test-agent/.env.test --skip-frontend-build`，未传 Workflow、LobeHub 或 Mem0 启用参数。实际 PostgreSQL 从 `20260812144051` 正序应用到 `20260812202425`；backend health/readiness、frontend 3000、CORS 与 manager WebSocket/4098 OpenCode health 均通过。
- 最终远端核对期间 release 又新增 `80e5e4ff9` 历史对话耗时修复；将该提交及 release 中被早期 mem0 内容恢复覆盖的黄金矿工/弹球最新实现继续合入 dev。相关 Vitest 117/117、agent-web typecheck、9913 模块 production build 通过；历史切换 Chromium 首轮因页面冷启动未出现会话按钮而超时，自动重试通过，预热后关闭重试单跑 1/1 通过。

### Result

- `dev` 同时包含 release、mem0、client 的提交历史与功能，默认启动不包含 Workflow、LobeHub 或 Mem0 数据面，按需通过显式参数开启；release 已同步 `origin`。
- 本次新增数据库兼容装配与前向 migration，不新增另一套迁移器；API、RunEvent、安全协议均只承接三个来源分支已有的向后兼容扩展。稳定文档已同步。
- 主工作树 `frontend/apps/agent-web/components.d.ts` 和 mem0 工作树 6 个用户未提交文件保持未暂存且未进入 dev；未修改任何 `.env*`。真实 ARM 麒麟设备上的 client 最终验收仍是 client 原分支的既有外部验证项，不影响本次分支整理与本机启动结论。

## 2026-08-12 - 恢复历史对话任务耗时

### Why

- 上一轮只补了任务消耗组件对 `duration` 的兼容显示，但历史会话切换会主动清空耗时，随后取得关联 Run 详情时没有恢复；历史 `step-finish` 又能恢复 Token，因此用户仍然只看到 Token。

### What

- 复用 `switchSession` 为恢复 Run 状态与 Diff 已经发起的 `getRun(runId)` 请求，以同一条终态 Run 记录的 `createdAt/updatedAt` 恢复最近一轮已完成任务的锁定耗时，没有增加接口或网络请求。
- 抽取 `completedRunDurationMs` 统一终态 Run 耗时校验，并让既有晚间任务耗时投影复用；活动状态、无效时间和逆序时间不参与历史恢复。
- 扩展既有历史切换 Playwright 用例，同时构造 `step-finish` Token 与一分钟终态 Run，断言页脚共同显示 `1m 0s` 和 `1.2k tokens`；同步 agent-web README。

### How

- 修复前同一 Chromium 用例稳定得到 `1.2k tokens` 且缺少 `1m 0s`；修复后定向 Playwright 1/1 通过。
- `workbench-utils` Vitest 106/106、`FigmaChatPanel` Vitest 159/159（另 1 skipped）、agent-web `vue-tsc` 与 production build 均通过；构建仅保留既有大 chunk 警告。
- 前端以 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 5173 --strictPort` 启动，HTTP 返回 200。提交前回顾全部 `.agents/session-log*.md`，任务外配置管理、migration、设置页及自动生成差异均保持未暂存。

### Result

- 切回包含关联终态 Run 的历史会话后，任务消耗页脚会恢复最近一轮任务耗时，并与历史 Token 同时展示。
- 本次不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、后端、性能、安全或环境配置；未修改 generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 重构桌面弹球并新增黄金矿工

### Why

- 用户认为已有弹球目标感和操作反馈仍不够有趣，并要求在宠物游乐舱增加可完整游玩的黄金矿工。

### What

- 继续复用 `PetMiniGames.vue` 的既有入口、键盘路由、弹球物理和生命周期，将弹球改为深蓝信号台视觉，以“四座 M/I/M/O 中继 → 三枚放大器 → 红色核心 Jackpot”组织目标；进度跨弹珠保留，动态奖池由碰撞器、旋转门、弹射器与挡板命中持续充能，核心命中触发三球信号风暴或多球 Super Jackpot。
- 挡板碰撞增加带冷却的主动向上抽射和击中波纹，保留坡度重力、弧形出槽、单向门、技能发射、救球、连击、倍率、奖励球、晃台和 TILT；既有“离槽后不能重新掉回发射槽”规则继续由 Chromium 场景覆盖。
- 新增独立 `PetGoldMinerGame.vue`：45 秒摆钩关卡、方向下钩、圆形命中检测、按重量回收、金块/钻石/岩石/钱袋价值、两枚炸药、目标进度、成功/失败结算和递增关卡；切换到其它游戏时暂停计时但保留局内状态。六款游戏入口、键盘操作、组件测试、工作台 E2E 和 agent-web README 同步更新。

### How

- `PetGoldMinerGame` 与 `PetMiniGames` 定向 Vitest 2 个文件、11 项全部通过；Chromium 六游戏工作台场景 1/1 通过，实际验证中央大金块抓取后结算 ¥520、弹珠离开发射槽进入主球台、重力下落、挡板和 TILT。
- agent-web 用户手册预构建、`vue-tsc` 与 production build 全部通过，仅保留既有大 chunk 警告；最新前端以 `corepack pnpm --dir frontend --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3024` 启动。
- 应用内浏览器因本地 URL 安全策略未能补充截图，未绕过限制；最终可执行行为由项目 Playwright Chromium 场景验证。提交前回顾全部 `.agents/session-log*.md` 近期记录，并隔离任务外配置管理、migration、设置页和自动生成差异。

### Result

- 宠物游乐舱现有六款游戏；弹球的任务进度、主动挡板反馈和可增长奖池形成更明确的追分循环，黄金矿工具有计时、选择目标、重量风险、炸药取舍和连续关卡，不是静态演示。
- 本次不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、后端、性能、安全或环境配置；未修改 `.env*`、generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 补充对话与工作空间可见 E2E 测试数据

### Why

- 本地真实页面缺少可重复验收的应用版本库、需求/详细设计材料，以及普通、Skill、Agent、permission、subagent 等历史对话数据；现有 mock 场景不能直接用于应用和工作空间页面联调。

### What

- 新增显式启用的 `ConversationWorkspaceE2eDataFixtureTest` 与根目录造数脚本，复用既有 Repository 和 Redis Run 数据面，为 `usr_test_dev` 幂等写入 1 个应用、Spring PetClinic/Playwright/Vue Core 3 个公开版本库、应用版本/个人工作空间和 7 类 `[E2E]` 历史会话。
- 新增本地轻量工作空间模板，包含订单退款需求、详细设计、CSV 案例、示例 Skill 和示例 Agent；公开版本库只保存地址，不下载远端源码，演示数据不进入 Flyway。
- 同步 `test-agent-app` README 与对话场景测试文档，明确执行命令、覆盖范围及模拟 permission 不能回复真实 OpenCode 内存请求的边界。

### How

- 造数脚本在 `.env.test` 指向的真实 PostgreSQL/Redis 上重复执行，均为 1/1 passed；固定 ID 保持 1 个 E2E 应用、3 个 E2E 版本库和 7 个会话，工作空间记录同步写入当前稳定服务器 ID，验证幂等性。`tools/verify-ai-docs.sh`、脚本语法、差异空白与冲突标记检查通过。
- JDK 25 完整后端 `mvn clean package -Dmaven.test.skip=true` 成功。当前工作树按 `.env.test`、`test` profile 和 `--without-workflow` 启动时，Flyway 因测试库已执行但当前工作树未解析的 `V20260812202425` 失败关闭；遵循数据库规则未执行 `repair`、未改历史表，也未伪造兼容迁移。
- 使用当前 `.env.test` 恢复 `/Users/kaka/Desktop/intelligent-test-agent-dev` 的既有构建，后端健康与就绪探针均为 `UP`、前端返回 200。通过应用内浏览器实测应用/工作空间/版本上下文、全部 7 个 `[E2E]` 会话、普通消息、等待授权卡片、subagent 卡片及子会话结论均可见；文件树因该用户专属 OpenCode 进程未启动而未完成在线读取，未为造数任务额外启动进程。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目，任务外配置管理、migration、设置页、宠物游戏和自动生成文件差异保持原有暂存或未暂存状态，不纳入本次提交。

### Result

- 可重复造数能力及测试数据已实现并通过真实存储与主要页面链路验证；需求、详细设计、CSV、Skill、Agent 等文件已复制到本地 Git 工作空间并由夹具断言存在。仅工作空间文件树的在线读取因专属 OpenCode 进程未运行而属于部分验证；当前工作树自身的完整启动仍需数据库所有者恢复 `V20260812202425` 原始迁移或提供明确兼容方案。
- 本次未新增或变更 HTTP API、RunEvent 类型、数据库结构、生产 SQL、安全或性能契约；只写入本地测试数据，未修改 `.env*`、generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 为六款宠物游戏加入公平随机难度

### Why

- 用户要求重新审查游戏是否合理好玩，并让难度随机提升；复核还发现俄罗斯方块纯随机可能长期缺块，数独继续删提示会出现多解但界面只认固定答案。

### What

- 俄罗斯方块改用七袋洗牌，方块与贪吃蛇每次阶段升级随机加 1–2 档速度；扫雷继续随机 10–18 雷并保持首击九宫安全，数独改为同一唯一解题面上的 30–42 个提示五档。
- 桌面弹球每局 1–3 档起步，显示实际重力、动态技能发射甜区和 5–9 秒救球窗口；三球信号风暴结束后随机加 1–2 档。黄金矿工加入重载、急速和短班三种随机矿况，风险提高时同步补偿矿价，跨层升档但重试本层保持规则。
- 六款游戏统一显示本局随机档，最高限制为 5；同步前端与 agent-web README、组件回归和桌面 Chromium 串联场景。

### How

- `PetMiniGames` 与 `PetGoldMinerGame` 定向 Vitest 2 个文件 12/12、agent-web `vue-tsc`、Chromium 六游戏工作台回归 1/1、`git diff --check` 均通过；跟踪截图复核弹球六格状态条和矿工矿况条在 316px 面板内完整可读。
- 全量 Vitest 曾运行到 124 个文件通过、1 个文件失败；唯一剩余失败是任务外已修改用户手册正文与 `help-center.test.ts` 的“超级管理员专属的用户管理”引号断言不一致，本次未越界修改。
- 最新前端按 `corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3024 --strictPort` 启动。提交前回顾全部 `.agents/session-log*.md` 近期记录，并隔离配置管理、migration、E2E 造数、设置页及自动生成差异。

### Result

- 随机难度现在有明确触发点、可见规则、风险收益补偿和五档上限；方块缺块与数独多解两类不公平随机已消除。
- 本次不涉及 HTTP API、RunEvent、DTO、数据库、SQL、migration、后端、性能、安全或兼容性契约；未修改 `.env*`、generated SDK、OpenCode 只读源码，也未新建分支或推送远端。

## 2026-08-12 - 新增自动化代码库版本库类型

### Why

- 配置管理需要新增“自动化代码库”，允许应用从任意已有分支和目录创建工作空间，并由管理员显式选择 `yyyyMMdd` 版本；测试工作库的标准分支、一级目录和新增目录规则必须保持不变。

### What

- 新增 `AUTOMATION_CODE_REPOSITORY` 领域枚举和系统字典，旧 `standard` 固定派生为 `false`；版本库 API、配置管理类型冻结和既有 DTO 继续复用，不新增路由或字段。
- 设置页只把测试工作库和自动化代码库列为工作空间候选；自动化代码库支持任意分支、任意已有目录和日期版本，不显示也不接受测试工作库专属的 `directoryNew=true`。应用源码、应用资产和 Workflow 仍由既有精确类型守卫排除自动化代码库。
- 同步配置管理、Workspace、持久化、前端包 README，以及 HTTP API、数据库、模块地图和用户手册。

### How

- 后端领域、配置管理、API、Workspace、H2/MyBatis 和 Flyway 命名测试通过；Workspace 定向回归 91/91、持久化回归 5/5、Flyway 命名 11/11，真实 PostgreSQL 兼容迁移 23/23，完整后端 `mvn -q clean package -DskipTests` 成功。
- 前端设置页和 backend-api 定向 Vitest 3 个文件共 147 项通过，workspace typecheck、production build 和用户手册 build 成功；最终应用 JAR 内 migration 与源码 SHA-256 均为 `250c2761c9717cca6e689019a9a91f0cc66d52a33baa662b294e41b1d1745554`。
- 原计划 migration `V20260812195822` 从未执行；发现 `.env.test` 本地库已执行另一开发分支的 `V20260812202425` 后，按迁移规则在执行前将本任务版本顺延为 `V20260812204207`。当前分支缺少已执行的 `V20260812202425` 原始 migration，未执行 `repair`、未改历史表，也未停止现有服务做已知会失败的重启。

### Result

- 自动化代码库的配置、工作空间规则、迁移、测试和文档已实现并完成代码级、构建级及真实 PostgreSQL 兼容验证；实际 HTTP/UI 创建流程因本地 Flyway 历史分叉仍未运行，交付状态为部分运行验证。
- 本次只对既有 HTTP 响应增量增加类型编码，不变更 DTO 字段、RunEvent、表结构、性能或安全契约；未修改 `.env*`、generated SDK、OpenCode 只读源码，也未新建分支或推送远端。
## 2026-08-13 - 固化 dev 与 release 后续开发边界

### Why

- 用户明确后续影响部署、需要新增节点或运行服务的大功能统一在 `dev` 开发，`release` 只维护 Bug 和现有拓扑可承载的小功能，需要把这项长期决策写入 AI 必读规范。

### What

- 在 `AGENTS.md`、研发工作流和完成前自检清单中统一定义 `dev`、`release`、`main` 的职责、判定条件和合并门禁；边界不清时默认选择 `dev`。
- 明确 release 修复必须同步回 dev，从 dev 提升到 release 只能按已批准功能选择性合入，禁止整体带入未交付节点、服务、migration 或配置。
- 更新文档索引，使后续开发者在进入编码流程前即可看到分支策略。

### How

- 复用现有 `AGENTS.md`、`docs/guides/ai-workflow.md` 和 `docs/guides/self-checklist.md` 作为规范单一入口，没有新增重复的分支说明文件。
- 运行 AI 文档校验、差异空白与冲突标记检查，并回顾全部 `.agents/session-log*.md` 近期记录。

### Result

- 后续开发具备明确的分流规则：部署演进和新节点功能进入 dev，release 只承载兼容当前部署的修复与小功能，main 保持稳定基线。
- 本次只修改研发流程文档，不涉及代码、API、事件、数据库、性能、安全、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-13 - 修复自动化代码库首页分组与工作区刷新

### Why

- 自动化代码库创建成功后仍混在“测试工作空间”分组，无法像应用代码库一样独立识别；顶部选择器又提前乐观显示目标版本，同版本重选时直接返回，导致顶部已变化而左侧目录没有实时刷新。

### What

- 工作空间模板响应增量返回关联版本库 `repositoryType`，前端顶部和左下角入口分别展示“应用代码库 / 自动化代码库 / 测试工作空间”；旧后端缺少该字段时继续归入原测试工作空间分组。
- 顶部工作空间和版本显示改为以父层实际完成的选择为事实源；重选当前版本时重新读取 Workspace 快照，并主动刷新左侧组合目录和 Git diff。
- 同步 workspace、shared-types、agent-web README/PACKAGE，以及 HTTP API、模块地图和用户手册；未新增路由、DTO、数据库字段或事件类型。

### How

- 后端 `ManagedWorkspaceApplicationServiceTest` 92/92，前端 FigmaShell/WorkbenchFooter 定向 Vitest 78/78，两个 Chromium 工作台场景各 1/1，全 workspace typecheck、production build 和 22 模块跳过测试打包均通过。
- 使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 在 JDK 25 下重启 backend、manager 和 frontend；backend health 为 UP，前端返回 200。真实测试账号 API 返回自动化模板类型，真实 Chromium 页面可见独立分组，重选 `ai-test` 后捕获到新的 `workspace.view.list` 请求且左侧目录已渲染。

### Result

- 自动化代码库在首页两个工作空间入口均独立展示，顶部上下文与左侧文件树在实际切换完成后同步更新，同版本重选也会重新拉取目录。
- 本次只增量扩展既有 HTTP 响应并修复前端状态同步；不涉及 RunEvent、数据库、migration、性能、安全、环境配置、generated SDK 或 OpenCode 源码，未新建分支或推送远端。

## 2026-08-13 - 修复 VPN 下 OpenCode 间歇证书校验失败

### Why

- macOS 已启用本地 VPN/系统代理时，Java 的数据库与 Redis 直连和 manager 的 VPN 端口识别已有隔离，但 OpenCode 使用的 Bun 子进程没有继承系统 HTTPS 代理，仍可能经 TUN 假地址访问模型服务并间歇报 `unknown certificate verification error`。

### What

- 本地启动脚本在没有显式 `HTTPS_PROXY`/`https_proxy` 时自动读取 macOS 静态 HTTPS 代理，必要时回退到可承载 CONNECT 的 HTTP 代理，并仅传给 opencode-manager 及其 OpenCode 子进程；显式环境变量优先，`TEST_AGENT_OPENCODE_USE_SYSTEM_PROXY=false` 可关闭自动探测。
- 保持 Maven、前端构建和 Java 后端不继承该代理，不关闭或放宽 TLS 证书校验；同步研发启动文档。另为既有自动化代码库提交补齐一处测试模板 `repositoryType`，恢复 release 前端类型构建。

### How

- 通过 Bash 语法、差异空白检查，JDK 25 后端 22 模块跳过测试完整打包，以及 agent-web 的 Vue 类型检查和 Vite 生产构建。
- 以 `test` profile 重启 release，验证 backend health/readiness、frontend、CORS 和 manager/OpenCode 进程；进程环境确认代理只进入 manager/OpenCode，Java 后端未继承。
- 在真实用户 OpenCode 端口连续发起 3 次 `mimo-v2.5-free` 对话，3/3 返回预期内容，新日志未再出现证书校验错误。

### Result

- VPN 开启状态下，本轮真实模型对话未复现 `unknown certificate verification error`；仍观察到上游连接偶发 `socket connection was closed unexpectedly`，SDK 重试后主对话成功，属于 VPN 节点或外部链路的剩余稳定性风险。
- 本次未修改 `.env*`、API、RunEvent、数据库、migration、generated SDK 或 OpenCode 只读源码，也未降低安全校验。

## 2026-08-13 - 用户手册新增每周新功能板块

### Why

- 用户需要从使用者视角按周了解最新功能的适用场景、入口和操作方法，而不是继续从功能总览和多个专题中自行拼接信息。
- 当前 `release` 已开放平台体验、自动化代码库、会话通知协作、测试资料批量跳转和宠物新游戏；周更内容必须严格以交付分支事实为准，不能混入仅在 `dev` 的长期记忆等能力。

### What

- 新增“每周新功能”稳定章节，并注册到 VitePress 顶部导航、侧栏、手册首页和应用内 Help；首期按 2026-08-10 至 2026-08-16 汇总五类用户场景、步骤和权限/数据边界。
- `help-center.ts` 直接复用同一 Markdown 作为宠物问答资料；`release` 以 5600 字有界上下文锁定不包含长期记忆，回合 `dev` 后保留长期记忆并将周更上下文调整为 7000 字。
- 同步用户手册、frontend 和 agent-web README/PACKAGE；功能总览校正宠物游戏入口仅超级管理员可见，以及当前六款游戏名称。

### How

- `corepack pnpm exec vitest run apps/agent-web/tests/help-center.test.ts --reporter=verbose`：12/12 通过。
- `corepack pnpm --filter @test-agent/agent-web typecheck` 与 `corepack pnpm --filter @test-agent/user-manual build` 通过。
- 实际以 `corepack pnpm --filter @test-agent/user-manual dev` 启动手册，`/help/`、周更页及三个关联专题均返回 HTTP 200，生成 HTML 包含导航、场景、操作步骤和正确内部链接。

### Result

- 用户可从手册首页、顶部导航、侧栏或应用内 Help 直接打开“每周新功能”，按“想做什么”快速定位本周能力，再进入稳定专题查看完整规则。
- `release` 周更保持当前交付能力，`dev` 周更额外说明按账号开放的长期记忆，分支同步没有覆盖 `dev` 专属章节。
- 本次不新增 API、RunEvent、数据库、migration、部署节点、强制配置或安全权限；未修改 `.env*`、generated SDK 或 OpenCode 源码。

## 2026-08-13 - 同步稳定用户手册操作截图到 dev

### Why

- release 已补齐稳定用户手册截图，dev 需要同步相同质量要求，同时保留本分支专属的长期记忆章节和周更内容。

### What

- 同步 12 个 release 章节的操作截图和图片存在性守护测试；将周更图片统一迁入 `images/operations/`。
- 保留 dev 长期记忆能力的两张原有截图，并为独立的 `memory.md` 稳定章节补充记忆中心操作图。

### How

- 解决 README、功能总览和会话日志冲突时保留 dev 的长期记忆说明；图片目录冲突按 Git 提示迁入统一操作图目录。
- 帮助中心测试 15/15、agent-web typecheck、VitePress build 和 `git diff --check` 通过；3002 端口的 13/13 个 dev 章节页面均渲染至少一张操作图，并继续整本拦截游戏内容。

### Result

- dev 与 release 的通用手册截图保持同步，dev 专属长期记忆内容没有被覆盖；本次不涉及 API、事件、数据库、部署或安全契约。

## 2026-08-13 - 同步 release 前端组件类型声明

### Why

- release 已新增黄金矿工组件并不再通过自动导入使用 Element Plus Drawer，自动生成的 Vue 全局组件声明需要与当前源码扫描结果一致。

### What

- 从 `components.d.ts` 移除未自动导入的 `ElDrawer` 全局声明，新增 `PetGoldMinerGame` 组件声明；显式导入 Drawer 的现有页面不受影响。

### How

- 核对 `PetMiniGames.vue` 对黄金矿工组件的显式引用，以及系统参数页面对 `ElDrawer` 的显式导入；提交前回顾全部 `.agents/session-log*.md` 近期条目并执行差异空白检查。

### Result

- Vue 组件类型声明与 release 当前源码一致；本次不涉及 API、RunEvent、数据库、部署、安全、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-13 - 兼容 release 读取已执行的本地客户端迁移历史

### Why

- `.env.test` 的共享 PostgreSQL 已由 dev 执行 `V20260812202425`，而 release 按产品边界不包含本地客户端主链，Flyway 因无法解析已执行版本而拒绝启动。

### What

- 在唯一 `DatabaseMigrationCompatibilityCustomizer` 中仅对 history 已存在 `V20260812202425` 的数据库加载原始字节隔离目录；release 空库和正常升级不扫描、不执行该 SQL，也不装配本地客户端 API 或服务。
- 锁定隔离 SQL 的 SHA-256，补充真实 PostgreSQL 已执行路径与未知 checksum 失败关闭用例，并同步 app README 和数据库部署文档。

### How

- 与 dev 已执行资源逐字节比对，源码和 persistence JAR SHA-256 均为 `168cbf7bf3c1a062c8fd38057cd32726804ab8bf00ced1dff39d5c2837c53026`；Flyway 字节锁定测试 12/12、AI 文档校验和 22 模块跳过测试打包通过。
- 真实 PostgreSQL Testcontainers 套件因本机 amd64 PostgreSQL 冷启动超过日志等待窗口报容器启动超时，容器随后自行 ready；改由当前 `.env.test` 的真实已执行 history 启动 release 作为运行验证，不使用 `outOfOrder`、`repair` 或手工修改历史表。

### Result

- release 可校验共享开发库中的客户端历史，但仍不发布客户端运行能力；本次只扩展数据库历史兼容装配和文档，不新增 API、RunEvent、业务表迁移或环境配置。

## 2026-08-13 - 收敛 main、release、dev 分支边界

### Why

- 用户要求统一去掉分支名中不一致的 `codex/` 前缀，并把可独立部署的自动化代码库、宠物游戏和 E2E 造数保留在 release，把 Mem0 与本地客户端等大功能收敛到 dev；独立 Workflow 从所有保留分支永久删除。

### What

- release 保留独立部署基线和可选 LobeHub，默认不启动 LobeHub；dev 合入 release、Mem0 与本地客户端，Mem0/LobeHub 默认均关闭；main、release、dev 和本地客户端分支均删除独立 Workflow 的代码、部署资产、测试与稳定文档。
- 提交 Vue 自动组件声明，并为 release 增加仅解析共享库既有客户端 migration 历史的隔离兼容装配；空 release 数据库不会执行客户端 migration，也不发布客户端服务。
- 已合入 release/dev 的临时功能分支按提交祖先或等价正式提交核对后收敛；仍有本地工作树改动的 Apple 预览和 Mem0 工作区保留，且不覆盖其未提交内容。

### How

- release 前端类型检查、生产构建、定向测试，后端完整打包、Flyway 字节锁和 AI 文档校验通过；真实 `.env.test` 已执行历史由 release 成功校验，backend health/readiness、frontend 和 manager 均健康。
- 逐分支检查独立 Workflow 路径和标识，检查 release/dev 的祖先关系、等价功能提交和 LobeHub 资产；Mem0 的 6 个本地文件先用专用 stash 保护，快进到 dev 后恢复，并手工合并唯一同文件冲突。

### Result

- 三条长期分支职责明确：main 为干净基线，release 为无需 Mem0/客户端附加服务即可部署的交付线，dev 为大功能集成线；release 当前本地服务已启动。
- 本次未修改 `.env*`、generated SDK、OpenCode 只读源码或 Flyway 已执行字节；除既有 migration 历史兼容外，不新增 API、RunEvent、数据库结构、安全或性能契约。

## 2026-08-13 - 固化 dev 与 release 后续开发边界

### Why

- 用户明确后续影响部署、需要新增节点或运行服务的大功能统一在 `dev` 开发，`release` 只维护 Bug 和现有拓扑可承载的小功能，需要把这项长期决策写入 AI 必读规范。

### What

- 在 `AGENTS.md`、研发工作流和完成前自检清单中统一定义 `dev`、`release`、`main` 的职责、判定条件和合并门禁；边界不清时默认选择 `dev`。
- 明确 release 修复必须同步回 dev，从 dev 提升到 release 只能按已批准功能选择性合入，禁止整体带入未交付节点、服务、migration 或配置。
- 更新文档索引，使后续开发者在进入编码流程前即可看到分支策略。

### How

- 复用现有 `AGENTS.md`、`docs/guides/ai-workflow.md` 和 `docs/guides/self-checklist.md` 作为规范单一入口，没有新增重复的分支说明文件。
- 运行 AI 文档校验、差异空白与冲突标记检查，并回顾全部 `.agents/session-log*.md` 近期记录。

### Result

- 后续开发具备明确的分流规则：部署演进和新节点功能进入 dev，release 只承载兼容当前部署的修复与小功能，main 保持稳定基线。
- 本次只修改研发流程文档，不涉及代码、API、事件、数据库、性能、安全、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-13 - 修复自动化代码库首页分组与工作区刷新

### Why

- 自动化代码库创建成功后仍混在“测试工作空间”分组，无法像应用代码库一样独立识别；顶部选择器又提前乐观显示目标版本，同版本重选时直接返回，导致顶部已变化而左侧目录没有实时刷新。

### What

- 工作空间模板响应增量返回关联版本库 `repositoryType`，前端顶部和左下角入口分别展示“应用代码库 / 自动化代码库 / 测试工作空间”；旧后端缺少该字段时继续归入原测试工作空间分组。
- 顶部工作空间和版本显示改为以父层实际完成的选择为事实源；重选当前版本时重新读取 Workspace 快照，并主动刷新左侧组合目录和 Git diff。
- 同步 workspace、shared-types、agent-web README/PACKAGE，以及 HTTP API、模块地图和用户手册；未新增路由、DTO、数据库字段或事件类型。

### How

- 后端 `ManagedWorkspaceApplicationServiceTest` 92/92，前端 FigmaShell/WorkbenchFooter 定向 Vitest 78/78，两个 Chromium 工作台场景各 1/1，全 workspace typecheck、production build 和 22 模块跳过测试打包均通过。
- 使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 在 JDK 25 下重启 backend、manager 和 frontend；backend health 为 UP，前端返回 200。真实测试账号 API 返回自动化模板类型，真实 Chromium 页面可见独立分组，重选 `ai-test` 后捕获到新的 `workspace.view.list` 请求且左侧目录已渲染。

### Result

- 自动化代码库在首页两个工作空间入口均独立展示，顶部上下文与左侧文件树在实际切换完成后同步更新，同版本重选也会重新拉取目录。
- 本次只增量扩展既有 HTTP 响应并修复前端状态同步；不涉及 RunEvent、数据库、migration、性能、安全、环境配置、generated SDK 或 OpenCode 源码，未新建分支或推送远端。

## 2026-08-13 - 修复 VPN 下 OpenCode 间歇证书校验失败

### Why

- macOS 已启用本地 VPN/系统代理时，Java 的数据库与 Redis 直连和 manager 的 VPN 端口识别已有隔离，但 OpenCode 使用的 Bun 子进程没有继承系统 HTTPS 代理，仍可能经 TUN 假地址访问模型服务并间歇报 `unknown certificate verification error`。

### What

- 本地启动脚本在没有显式 `HTTPS_PROXY`/`https_proxy` 时自动读取 macOS 静态 HTTPS 代理，必要时回退到可承载 CONNECT 的 HTTP 代理，并仅传给 opencode-manager 及其 OpenCode 子进程；显式环境变量优先，`TEST_AGENT_OPENCODE_USE_SYSTEM_PROXY=false` 可关闭自动探测。
- 保持 Maven、前端构建和 Java 后端不继承该代理，不关闭或放宽 TLS 证书校验；同步研发启动文档。另为既有自动化代码库提交补齐一处测试模板 `repositoryType`，恢复 release 前端类型构建。

### How

- 通过 Bash 语法、差异空白检查，JDK 25 后端 22 模块跳过测试完整打包，以及 agent-web 的 Vue 类型检查和 Vite 生产构建。
- 以 `test` profile 重启 release，验证 backend health/readiness、frontend、CORS 和 manager/OpenCode 进程；进程环境确认代理只进入 manager/OpenCode，Java 后端未继承。
- 在真实用户 OpenCode 端口连续发起 3 次 `mimo-v2.5-free` 对话，3/3 返回预期内容，新日志未再出现证书校验错误。

### Result

- VPN 开启状态下，本轮真实模型对话未复现 `unknown certificate verification error`；仍观察到上游连接偶发 `socket connection was closed unexpectedly`，SDK 重试后主对话成功，属于 VPN 节点或外部链路的剩余稳定性风险。
- 本次未修改 `.env*`、API、RunEvent、数据库、migration、generated SDK 或 OpenCode 只读源码，也未降低安全校验。

## 2026-08-13 - 缓存工作空间 Git 权限成功结果

### Why

- 工作空间每次切换版本都会重新执行远端 `git ls-remote --heads` 权限探测；在 Gitee 等外部链路上单次通常需要数秒，导致已验证版本库的重复切换明显变慢。

### What

- 在工作空间应用服务中增加进程内 Git 权限成功缓存和并发单飞：同一用户、版本库、有效仓库地址及 SSH 密钥身份的成功结果复用 10 分钟，失败结果不缓存，容量上限为 4096 条。
- 每次请求仍实时校验平台成员关系并重新读取版本库和 SSH 密钥元数据；成员撤销、仓库地址变化或平台 SSH 密钥记录变化立即绕过旧缓存。缓存键只保留仓库地址摘要和密钥 ID/指纹，不保存私钥明文。
- 同步 workspace README、HTTP API、模块地图和安全规范；未新增或修改路由、DTO、事件、数据库字段或 migration。

### How

- JDK 25 下定向运行工作空间服务测试 97/97，通过 599 秒命中、600 秒过期、仓库地址和密钥变化、平台成员撤销、失败不缓存及并发成功探测合并等覆盖。
- AI 文档校验、差异空白检查、后端 22 模块完整打包、前端类型检查和生产构建均通过；使用 `./restart-dev-services.sh --profile test --env-file .env.test` 重启 release 本地服务，backend readiness 为 UP，frontend 返回 200。
- 对真实 Bytebase 自动化代码库权限接口计时：冷请求 2.355 秒，紧随其后的缓存请求 0.029 秒，均返回 `accessible=true`。

### Result

- 同一自动化代码库在 10 分钟内重复切换不再反复等待远端权限探测；真实 clone/fetch/push 仍由 Git 远端实时鉴权，远端单独撤销权限时预检最迟在缓存到期后的下一次请求体现。
- 页面级自动化可进入 F-COSS/ai-test，但测试账号的用户 OpenCode 文件路由曾返回既有 503，因此本轮切换性能以真实 HTTP 权限预检计时为准；release 修复后续仍需按分支规范同步回 dev。

## 2026-08-13 - 重打 release 企业包并补齐最新 Flyway 制品锁

### Why

- 用户要求基于当前 `release` 重新生成企业双后台固定包；相对上一轮制品，release 已新增自动化代码库字典 migration，并增加仅用于校验 dev 已执行本地客户端历史的 compatibility resource。
- 既有打包、外层封装和节点安装脚本尚未锁定这两条最新资源，不能仅依赖编译成功判断最终企业 JAR 字节正确。

### What

- 复用三套脚本既有 `verify_release_flyway_resource`，锁定 `V20260812202425` compatibility resource 和正常企业主链 `V20260812204207` 的路径与 SHA-256；同步 `FlywayMigrationNamingTest`。
- 更新多后台部署说明：从已知企业基线首次升级只允许连续新增五条 migration，正常企业历史不得出现只供共享开发库兼容的本地客户端版本。
- 本轮初次只核对了 `opencode-models.json` 的 JSON 键顺序，误判 Qwen 为真实目录首项；后续按 OpenCode 1.18.4 的 `release_date` 倒序实现复查确认旧快照实际仍是 DeepSeek 优先，旧结论由下一条灰度修正记录取代。

### How

- Bash 语法、AI 文档、差异空白检查、Flyway 字节锁和自动化代码库字典集成测试通过；真实 PostgreSQL 全部已知历史兼容矩阵通过，未使用 `outOfOrder`、`repair` 或手工改历史表。
- 最终内外层 ZIP、组件清单、包内 migration/models 字节与 SHA-256 在封包后补记到本条 Result。

### Result

- 企业包源码提交为 `193cc1d5daf95d40d52ea3ce0075698309e633e8`；内层包 SHA-256 为 `16681980542e2e23e4b8fd8ce3f752a8c41af0dfda14e2a0ce42d0c9a6216cd8`，外层双后台完整包 SHA-256 为 `69127cd4472cbe92f0f0db6d14ce052fadd417f6bd24aaacb81822f49a395e7a`，外层内嵌包与独立内层包字节一致。
- 后端 app JAR、persistence JAR、前端 tar 和旧 `opencode-models.json` 的 SHA-256 分别为 `319d335e87416f45945dab44ecfd7e1e31dafa8aae395a6cc7a7efc8e7157a55`、`ef6c42d698927f44d6c3228b9076e17bf39de3b63d1b45d6067d3d8dc5c83af5`、`33651d154531a72f375c31d09ef2ef612490ac8b6512ae29a3c4f67c5d80fcb0` 和 `edfa12f1a95da0954f72303e52934efea088b6f64cd834e8447f6e670e88bf86`；旧模型文件虽把 Qwen 键写在前面，但其日期早于 DeepSeek，外层包 `69127cd4...` 不满足 `.4` 的 Qwen 目录优先灰度，已由后续新包替代。
- Worker runtime 与 toolbox 命中已验证复用指纹，LobeHub 为 disabled，Workflow 无运行制品；外层完整包及校验文件已复制到 `/Users/kaka/Desktop/mimoagent/0709/`，`shasum -a 256 -c` 与 `unzip -tq` 均通过。
- 当前不修改任何 migration 字节，不新增 API、RunEvent、服务、节点、端口、强制配置、generated SDK 或 OpenCode 源码。

## 2026-08-13 - 修正 `.4` 千问优先灰度并重封企业包

### Why

- OpenCode 1.18.4 的 `/api/model` 按 models.dev `release_date` 倒序返回；旧快照中 Qwen 为 `2026-08-05`、DeepSeek 为 `2026-08-06`，因此 JSON 键顺序不能让 Qwen 优先。
- 灰度边界明确为只替换 `.4` 的活动模型文件并重启其 worker；`.114` 必须保留现网模型文件和 worker，不能因发布门禁改变 worker 指纹或阻断旧快照正常重启。

### What

- 将本次快照的 Qwen 排序日期调整为 `2026-08-07`，高于 DeepSeek 的 `2026-08-06`；公共 `opencode.jsonc` 的默认模型、小模型和 `code_analysis` 仍保持 DeepSeek，不扩大为默认模型切换。
- 新增只在 Mac 封包阶段调用的 `verify-opencode-model-priority.sh`，以正反用例锁定 Qwen 时间优先级；worker 常规 `validate-opencode-models.sh` 保持原字节和原指纹，允许 `.114` 灰度保留旧快照。
- 同步企业部署、双后台和 OpenCode 1.18.4 文档，明确 `.4/.114` 分别留存模型 SHA、只在 `.4` 复制新文件和重启 worker。

### How

- Bash 语法、开发脚本总门禁、AI 文档、差异空白、模型结构/公共配置一致性、Qwen 优先正例及 DeepSeek 优先反例均通过；组件规划确认 worker runtime 指纹仍为 `50f56c54...` 且模式为 `reuse`。
- 尝试用只读 OpenCode 1.18.4 源码直接启动 `/api/model`，因上游快照缺少 `babel-plugin-jsx-dom-expressions` 未能运行；未联网补依赖或修改只读源码。排序结论直接核对上游 models.dev 日期转换和 Catalog 倒序实现，并由封包门禁覆盖。
- 复用上一轮已通过前后端编译及真实 PostgreSQL 全历史矩阵的 app/persistence/frontend 制品，以修正提交 `57e211de48a5507fb8d1689e1c8f86fd96563032` 重新封装内外层包；所有 Flyway 资源重新通过包内 SHA 锁，不修改 migration 字节。

### Result

- 新 `opencode-models.json` SHA-256 为 `6a510be17a7b0616f128fad130773c3fb6ad7a3d4d7881ec59f2873e17cbc44c`；包内 Qwen/DeepSeek 日期分别为 `2026-08-07`、`2026-08-06`。
- 内层 ZIP SHA-256 为 `7af9c20e57a809258b0acd4672189b5ca875dde3f1a7208f770414a57d234b53`，外层双后台完整包 SHA-256 为 `95b3dc3b3bd03059aba059b845b6407b86db0616e5c4614681aeca972c48ccd7`，外层内嵌 ZIP 与独立内层 ZIP 字节一致；中转目录副本通过 SHA 和 `unzip -tq`。
- app JAR、persistence JAR 和前端 tar 仍为 `319d335e...`、`ef6c42d6...`、`33651d15...`；数据库和前端代码相对上一轮包没有变化。Worker runtime 与 toolbox 均为 `reuse`，LobeHub disabled，Workflow 无运行制品。
- 现场执行必须先部署 `.4` Java 并确认 Flyway，再仅在 `.4` 备份/替换模型文件和重启 worker；`.114` 部署 Java 后只核对模型 SHA 未变化，最后部署 `.2` 前端。

## 2026-08-13 - 用户手册新增每周新功能板块

### Why

- 用户需要从使用者视角按周了解最新功能的适用场景、入口和操作方法，而不是继续从功能总览和多个专题中自行拼接信息。
- 当前 `release` 已开放平台体验、自动化代码库、会话通知协作、测试资料批量跳转和宠物新游戏；周更内容必须严格以交付分支事实为准，不能混入仅在 `dev` 的长期记忆等能力。

### What

- 新增“每周新功能”稳定章节，并注册到 VitePress 顶部导航、侧栏、手册首页和应用内 Help；首期按 2026-08-10 至 2026-08-16 汇总五类用户场景、步骤和权限/数据边界。
- `help-center.ts` 直接复用同一 Markdown 作为宠物问答资料，并为多场景周更页使用有界的 5600 字上下文；定向测试锁定同源 URL、用户场景、release 安全边界及不包含长期记忆。
- 同步用户手册、frontend 和 agent-web README/PACKAGE；功能总览校正宠物游戏入口仅超级管理员可见，以及当前六款游戏名称。

### How

- `corepack pnpm exec vitest run apps/agent-web/tests/help-center.test.ts --reporter=verbose`：12/12 通过。
- `corepack pnpm --filter @test-agent/agent-web typecheck` 与 `corepack pnpm --filter @test-agent/user-manual build` 通过。
- 实际以 `corepack pnpm --filter @test-agent/user-manual dev` 启动手册，`/help/`、周更页及三个关联专题均返回 HTTP 200，生成 HTML 包含导航、场景、操作步骤和正确内部链接。

### Result

- 用户可从手册首页、顶部导航、侧栏或应用内 Help 直接打开“每周新功能”，按“想做什么”快速定位本周能力，再进入稳定专题查看完整规则。
- 本次不新增 API、RunEvent、数据库、migration、部署节点、强制配置或安全权限；未修改 `.env*`、generated SDK 或 OpenCode 源码。

## 2026-08-13 - 安全内置 TCDS 需求导入并收敛工作区路径暴露

### Why

- 旧“小地球”依赖仓库外 iframe/9900 服务并在 URL 中传递物理根路径、用户和后端地址；安全扫描同时发现普通 Workspace 响应、文件 WebSocket 握手及框架参数错误存在路径或请求信息暴露风险。
- TCDS 基础地址需由部署环境注入，不在代码维护主机白名单；浏览器不得获得 TCDS token、文档签名 URL或参与目标物理路径计算。

### What

- 新增登录守卫下的同源 `/workspace-requirement-import` 页面和精确 `origin/source` 的 iframe 协议；新增需求应用/子条目 HTTP API，以及 `workspace.requirement-import` 文件 RPC。服务端以登录主体重新查询授权应用、条目和文档，生成受控 `spec/` 目录并转换 Word、Excel、PowerPoint、文本和 Markdown。
- 抽象公共 `TcdsGateway`，统一存量用户查询与新导入能力；`test-agent.third-party-api.base-url` 改为必填 `${TEST_AGENT_TCDS_BASE_URL}`，限定 HTTP/HTTPS、连接/请求超时、三次重定向、单文件/总量和文档数上限，不记录或返回 token、签名 URL 与正文。
- 普通、最近和支持访问的 Workspace 响应改用 `workspace:{workspaceId}` 逻辑标识并清空物理路径；绝对路径复制改为用户点击后逐文件 `workspace.resolve-physical-path` RPC。文件 ticket 增加服务端统一认证上下文，upgrade 前预检、upgrade 时原子消费，无效/过期/复用统一脱敏 401；参数异常统一映射安全错误。
- 同步 HTTP/文件事件、安全、部署、模块地图、模块 README/PACKAGE、环境变量示例、内部部署脚本、用户手册和专项安全复测记录；未修改个人 `.env.test`、数据库、Flyway、RunEvent、generated SDK 或 OpenCode 源码。

### How

- 后端计划内模块全量测试通过；TCDS 配置/网关专项 8 项、导入/转换/ticket/filter/RPC/错误专项 57 项通过，22 模块 `mvn clean package -DskipTests` 成功。
- 前端全量测试 1942 passed / 1 skipped，typecheck 和 production build 通过；部署脚本 Bash 语法、差异空白和旧 9900/外部 iframe 调用扫描通过。
- 以显式 `TEST_AGENT_TCDS_BASE_URL` 和 `.env.test` 执行真实重启；前端 `127.0.0.1:3000` 返回 200，但后端因本机 PostgreSQL `127.0.0.1:15432` 未运行而失败，未为绕过阻塞修改环境配置。

### Result

- 内置需求导入、安全文件写入和路径收敛已实现并通过自动化测试与构建；新增必填部署变量和 `rootPath` 语义要求前后端及部署配置同批发布、同批回滚。
- 真实 TCDS 查询、目录生成、重复覆盖、部分失败、文件树刷新及原扫描 HTTP/WS 请求重放尚未验证，须在测试 PostgreSQL 恢复后补跑；旧 9900 服务本轮未停用。

## 2026-08-13 - 修复中文 Skill 推送成功后未进入 SkillHub

### Why

- 企业 F-SLB 的中文 Skill 已成功推送 feature 分支，但 SkillHub 未展示；现网定时对账日志持续出现 `git show <commit>:"<中文转义路径>"` 文件不存在。
- `GitWorkspaceService.listFilesAtCommit()` 使用换行格式读取 `git ls-tree`，Git 默认 quotepath 会把非 ASCII 路径转换为带引号的 C 风格展示文本，后续 blob 读取把该展示文本误当成真实路径。

### What

- 复用既有固定提交枚举入口，将 `git ls-tree` 改为 `-z` NUL 分隔并按 UTF-8 原样解析，不新增 Hub 索引器、补偿任务、API、数据库或配置。
- 在真实临时 Git 仓库提交 `SLB快速检索环境应用所有端口策略/SKILL.md`，回归验证中文路径原样枚举和固定提交 blob 读取；同步 common README 与包说明。

### How

- JDK 25 下 `GitWorkspaceServiceRealGitTest` 18/18、common 全量 102/102、`AgentSkillHubApplicationServiceTest` 14/14 通过。
- `test-agent-workspace-management` 及上游六模块跳过测试打包成功；`git diff --check` 通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；`GitWorkspaceService.java` 中并行存在 SCM 身份改动，本次只暂存 NUL 路径枚举修复，避免夹带他人工作。

### Result

- 中文及含空格的固定提交路径不会再被 Git 展示转义污染，发布即时索引和既有每 2 分钟本机快照对账均可正常生成 SkillHub 快照。
- 本次不改变 HTTP API、RunEvent、数据库、migration、性能边界、安全权限、环境配置、generated SDK 或 OpenCode 源码；企业现场需部署包含该修复的新后端，已成功推送的 Skill 无需再次 push。

## 2026-08-13 - 修复中文 Skill 推送成功后未进入 SkillHub

### Why

- 企业 F-SLB 的中文 Skill 已成功推送 feature 分支，但 SkillHub 未展示；现网定时对账日志持续出现 `git show <commit>:"<中文转义路径>"` 文件不存在。
- `GitWorkspaceService.listFilesAtCommit()` 使用换行格式读取 `git ls-tree`，Git 默认 quotepath 会把非 ASCII 路径转换为带引号的 C 风格展示文本，后续 blob 读取把该展示文本误当成真实路径。

### What

- 复用既有固定提交枚举入口，将 `git ls-tree` 改为 `-z` NUL 分隔并按 UTF-8 原样解析，不新增 Hub 索引器、补偿任务、API、数据库或配置。
- 在真实临时 Git 仓库提交 `SLB快速检索环境应用所有端口策略/SKILL.md`，回归验证中文路径原样枚举和固定提交 blob 读取；同步 common README 与包说明。

### How

- JDK 25 下 `GitWorkspaceServiceRealGitTest` 18/18、common 全量 102/102、`AgentSkillHubApplicationServiceTest` 14/14 通过。
- `test-agent-workspace-management` 及上游六模块跳过测试打包成功；`git diff --check` 通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；`GitWorkspaceService.java` 中并行存在 SCM 身份改动，本次只暂存 NUL 路径枚举修复，避免夹带他人工作。

### Result

- 中文及含空格的固定提交路径不会再被 Git 展示转义污染，发布即时索引和既有每 2 分钟本机快照对账均可正常生成 SkillHub 快照。
- 本次不改变 HTTP API、RunEvent、数据库、migration、性能边界、安全权限、环境配置、generated SDK 或 OpenCode 源码；企业现场需部署包含该修复的新后端，已成功推送的 Skill 无需再次 push。

## 2026-08-13 - 回归修复 Workspace 运行态标识

### Why

- `workspaceId` 经多轮语义调整后，需要重新检查运行态 Workspace、应用模板、应用版本和个人 worktree 标识是否混用；物理根路径从普通响应移除后，也要确认文件与 Diff 路由没有退回绝对路径。
- 复查发现前端缓存用 `versionId + applicationWorkspaceId + linuxServerId` 辅助去重，会误删同版本、同模板、同服务器下不同运行态 `workspaceId` 的个人 worktree；普通 Diff 未传运行态 ID，且非法绝对 Diff 路径仍有回退原值的入口。

### What

- Workspace 缓存只按精确运行态 `workspaceId` 替换，保留同版本的 default 与自定义个人 worktree；新增前缀回归锁定 `wrk_ / awp_ / awv_ / psw_` 四类 ID 边界。
- 普通托管编辑器和 Diff 按精确 `workspaceId` 开放逐文件物理路径解析，分享、支持、体验、源码快照、引用和 Agent 文件继续失败关闭。
- 工具事件、实时/历史 Diff 与 Run 详情统一过滤绝对 Unix/Windows、URI 和越界路径，移除归一化失败后回退原始宿主机路径的分支；同步安全复测记录。

### How

- 后端 Workspace/runtime/API 相关 19 模块测试通过，API 模块 565 项通过；ID 前缀专项 2/2 通过。22 模块本地启动前打包成功。
- 前端全量 124 文件、1949 passed / 1 skipped，类型检查和 production build 通过；Workspace 定向 Chromium 9/9 通过，覆盖普通文件、切换竞态、最近个人 worktree、源码快照、体验空间和 Diff。
- 全套 Chromium 运行到 103 项通过时，另有既有 `/workbench` 路由断言与首次引导弹窗遮挡失败，未将整套计为通过。`.env.test` 真实启动时前端 3000 返回 200，后端仍因 PostgreSQL `127.0.0.1:15432` 拒绝连接失败。

### Result

- 运行态文件、会话、Run、最近偏好、需求导入与前端缓存继续以 `Workspace.workspaceId` 为唯一主键；模板/版本/个人记录 ID 只承担各自领域语义，不再替代运行态标识。
- 本次不新增 API、RunEvent、数据库、migration、部署变量或运行服务，未修改 `.env*`、generated SDK 或 OpenCode 源码；真实登录与 TCDS 端到端复测仍受测试 PostgreSQL 未运行阻塞。

## 2026-08-13 - 用随包模板补齐体验工作区内容

### Why

- 体验工作区只有最小 README，用户无法直接体验标准 `docs/` 稳定资料和 TCDS `spec/` 需求至测试过程目录。
- 示例正文不应硬编码在 Java 服务中，升级存量目录也不能覆盖用户已有文件、提交历史或 Git index。

### What

- 新增 `deploy/internal/experience-workspace-template/` 独立文件模板，覆盖七类 `docs/` 资料与当前 TCDS 导入实现一致的 `spec/{父条目编号-名称}/01-需求` 至 `04-测试` 虚构登录示例。
- 新增 `ensure-experience-workspace-content.sh`：逐文件仅补缺失项，拒绝符号链接/非普通目标；无 HEAD 时只提交模板路径，已有 HEAD 时新文件保留为未跟踪变更。
- 本地重启与企业部署共用该脚本；发布 ZIP 强制携带脚本和模板，企业节点以 systemd Java 运行用户创建内容。Java 仅保留目录、Git 和 README 的最小直接启动兜底。

### How

- `tools/verify-experience-workspace-content.sh` 验证新仓库单基线提交、无 remote、不夹带用户暂存文件，以及存量仓库只补缺失文件、不覆盖 README、不改 HEAD/index 和重复执行幂等。
- `tools/verify-dev-scripts.sh`、`tools/verify-internal-multi-backend-node.sh`、`tools/verify-internal-incremental-components.sh`、Shell 语法和 `git diff --check` 均通过；JDK 25 后端 22 模块跳过测试完整打包成功。
- 按 `.env.test`/`test` profile 执行真实重启，模板补齐入口成功执行；后端因本机 PostgreSQL `127.0.0.1:15432` 拒绝连接未进入 readiness，未替换 `.env.test` 或绕过依赖。

### Result

- 体验内容改为可直接维护的随包文件，标准部署/重启自动补缺失项且保留存量用户内容；不新增服务、端口、中间件、强制配置或数据库 migration。
- 本次只同步既有体验 API 的初始化行为说明，不变更 HTTP/RunEvent/DTO 契约、安全权限、generated SDK、`.env*` 或 OpenCode 源码；真实三服务健康验收仍受本机 PostgreSQL 未运行阻塞。

## 2026-08-13 - 补齐稳定用户手册操作截图

### Why

- 每周新功能已经带有操作截图，但其余稳定手册章节仍是纯文字，用户无法直接对照页面入口、按钮和配置状态完成操作。
- 截图质量要求需要成为整本手册的持续约束，不能只依赖编写周更时人工记忆。

### What

- 将 8 张周更截图迁入可跨章节复用的 `images/operations/`，再从真实 Vue 组件的脱敏 E2E 状态生成 8 张入口、设置、初始化、Agent、引用配置、目录和帮助中心截图。
- 为帮助中心注册的 12 个 release 章节逐章补图；稳定章节至少 1 张，引用配置、工作区、首次准备和对话等多步骤章节按场景使用 2 张，周更继续保留 8 张。
- 在帮助中心测试中新增逐章节图片存在性校验，并保留整本手册永久排除游戏内容的扫描；用户手册 README 固化截图目录、替代文本和同步维护要求。

### How

- 截图来源 E2E 首轮 6/6 通过，帮助中心截图最终复跑 1/1 通过；图片均使用模拟业务数据，不包含真实 SSH Key 或其它凭据。
- `help-center.test.ts` 14/14、agent-web typecheck、VitePress build 和 `git diff --check` 通过；3001 端口的 release 手册中 12/12 章节均渲染至少一张图片，全部构建后图片资源 HTTP 返回成功。
- 提交前已回顾全部 `.agents/session-log*.md` 近期记录；工作区同时存在 SCM Git 身份等未完成改动，本次只暂存用户手册、对应前端守护测试和本条会话记录。

### Result

- release 内置用户手册的所有稳定章节与每周新功能均已图文结合，自动化代码库、设置、进程、引用配置等关键路径可直接对照入口和配置界面操作。
- 本次只修改静态手册、图片和测试，不涉及 HTTP API、RunEvent、数据库、migration、性能、安全权限、兼容性、环境配置、generated SDK 或 OpenCode 源码；游戏相关文字、入口、配置和截图仍永久禁止进入手册。

## 2026-08-13 - 校准企业 SCM Git 提交姓名并补偿存量用户

### Why

- F-SLB 某用户的平台展示名因同名追加数字，Git 提交被企业右控以“邮箱对应姓名不一致”拒绝；不能通过删除末尾数字猜测 SCM 姓名，也不能依赖现场 token、psql 或 jq。
- SSH Key 新增和存量用户都需要从已有可信证据校准独立 Git 姓名，并控制仓库历史扫描对磁盘和数据库的影响。

### What

- 新增独立 `user_scm_git_identities` 证据表和 MyBatis XML 仓储；右控拒绝证据优先于已接受提交历史，平台 `users.username` 保持原语义。
- Git push 严格解析固定右控报文，逐项核对统一认证邮箱和本次提交姓名后保存期望姓名、重建提交并只重试一次；覆盖应用普通文件、目录占位、应用/公共 Agent 与 Skill 发布，不做尾号猜测。
- SSH Key 保存后通过有界单线程队列异步定向扫描；XXL 每天 04:10 触发全量补偿，每仓库只读取一次本地 `origin` 跟踪历史（最多 50,000 条），用户按 500 条游标分页并批量 upsert，不执行 fetch 或远端调用。

### How

- JDK 25 定向回归通过：真实 Git 25 项、身份解析 2 项、配置/补偿任务 34 项、H2 migration/MyBatis 与 Flyway 字节锁 14 项；22 模块跳过测试完整打包成功，应用 JAR 可解压。
- 本机真实 PostgreSQL 从既有 `20260812204207` 历史成功执行 `V20260813190929`，Flyway 记录 success，表 8 列及约束可见；migration SHA-256 `fd434d47...` 已锁定，源码、persistence JAR 与应用内嵌 JAR 字节一致。
- Testcontainers PostgreSQL/MySQL 用例因本机 Docker socket 不可用被跳过；真实启动在 migration 成功后受既有必填 `TEST_AGENT_TCDS_BASE_URL` 未配置阻塞，未修改 `.env.test` 绕过。

### Result

- release 已具备独立 SCM Git 姓名、右控自校准单次重试、SSH Key 即时异步补偿和存量定时复核；错误详情与日志不暴露姓名、邮箱、统一认证号或右控原文。
- 本次新增 PostgreSQL 表和 XXL 任务，不变更 HTTP URL、DTO、RunEvent、部署节点、端口、强制环境变量、generated SDK 或 OpenCode 源码；完整 MySQL migration 与应用健康启动仍需在有 Docker/TCDS 配置的环境复验，release 修复后仍需同步回 dev。

## 2026-08-13 - 修复个人 worktree 发布进度连接误报失败

### Why

- 个人 worktree 提交并推送时，实时进度 WebSocket 在 3 秒内建连失败会先把第 3 步标成 `FAILED`，但发布 HTTP 请求仍继续执行，随后页面又回到 `RUNNING`，造成用户误判和重复提交风险。

### What

- `GitChangesPanel` 将 `WEBSOCKET_ERROR` 仅作为实时进度不可用状态，不再写入 Git 发布失败；弹框提示“暂时无法显示实时进度，提交仍在执行，请勿重复操作，等待最终结果”。
- 保持 `commitPersonalWorkspace`、`publishPersonalWorkspace` 及后端暂存、提交、投影、拉取、推送流程不变；最终成功或失败继续以发布 HTTP 响应为准，HTTP 收敛后清理降级提示。
- 补充组件回归测试，锁定进度连接先失败时发布 HTTP 仍只调用一次、步骤保持运行态且成功响应正常收敛。

### How

- `corepack pnpm exec vitest run apps/agent-web/tests/git-changes-panel.test.ts --reporter=verbose` 通过，42/42。
- `corepack pnpm typecheck` 通过；`git diff --check` 通过。
- 本机 Vite 前端继续运行于 `http://127.0.0.1:3000`，已确认实际服务模块包含新提示与降级状态；后端 readiness 为 `UP`。

### Result

- 实时进度通道故障不再伪装成业务失败，也不会取消或重复发起提交推送；真实后端失败仍沿用原错误响应和失败步骤展示。
- 本次仅修改前端组件、测试、README 和本会话记录，不变更 HTTP API、RunEvent、数据库、部署、安全权限、公共 Agent 业务语义、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-13 - 修正企业 AI 上游鉴权请求头

### Why

- 企业现场直接调用 AI 网关时，`Auth-Token: <供应商关联 Token>` 可以成功返回流式响应；部署代码却按 OpenAI 常见方式发送 `Authorization: Bearer <token>`，造成实际代理调用与已验证协议不一致。
- 现场进一步确认企业上游实际支持两种供应商鉴权：Bearer 可以完成鉴权但 `ucid` 不生效，只有 `Auth-Token` 会让同一请求的 `ucid` 生效；文档必须与这一区别保持一致。

### What

- 复用 `OpenAiUpstreamSupport` 统一把真实代理、LobeHub 模型网关和能力探测的上游鉴权改为 `Auth-Token` 原值，并在覆盖可信请求头前同时删除客户端传入的 `Authorization` 与 `Auth-Token`。
- 将内部模型定时/手工探活切换到相同的 `Auth-Token` 协议；OpenCode 子进程访问 Java 内部代理的 `Authorization: Bearer ${TEST_AGENT_INTERNAL_PROXY_API_KEY}` 保持不变。
- 补充代理、模型网关、能力探测和真实本地 HTTP 探活回归，并同步后端/模块 README、HTTP API、安全规范、可观测测试指南与企业单/多后台部署、空响应排障文档；所有相关说明都明确上游 Bearer 与 `Auth-Token` 的 `ucid` 语义差异。

### How

- JDK 25 下 5 个请求头定向测试通过，其中 `InternalModelProviderProbeServiceTest` 使用真实本地 HTTP Server 验证上游收到 `Auth-Token` 且未收到 `Authorization`。
- 受影响的 `test-agent-model-gateway`、`test-agent-opencode-runtime`、`test-agent-api` 三模块全量测试通过；上游依赖跳过测试安装及相关模块打包成功，`git diff --check` 通过。
- 文档补充后复跑 `OpenAiUpstreamSupportTest`，并通过 `tools/verify-ai-docs.sh`；文档守护脚本锁定两种上游鉴权与 `ucid` 生效差异，避免后续再次混写。
- 整个 `-am` 测试在任务外 `test-agent-xxl-job-integration` 的既有匿名测试内部类加载问题处失败，报错为 `NoClassDefFoundError`，未将其误报为通过。

### Result

- Java 到企业 AI 网关现统一发送 `Auth-Token: <供应商关联 Token>`，确保 `ucid` 在企业上游生效；内部代理 Bearer 只保留在 OpenCode → Java 边界，`ucid` 与 traceId 注入链路、流式请求体、响应处理和可观测记录逻辑未改。
- 本次不变更 HTTP 路径、DTO、RunEvent、数据库、migration、端口、部署拓扑、强制环境变量、generated SDK 或 OpenCode 源码；企业节点需要重新打包并部署后端应用才能生效。

## 2026-08-14 - 修复提交推送进度误报并完成四类 Git 端到端验收

### Why

- 个人及公共 worktree 的提交推送弹框会出现先失败再转圈、成功后步骤仍为 `PENDING`、远端分支/commit 无证据等问题；用户切换工作空间或 Diff Tab 时还可能让进行中的操作误用新上下文。
- 公共发布会把日期型或手工命名的历史 worktree 纳入补偿，造成远端已成功但 rollout 长期 `PENDING`，需要同时验证公共 Agent 对现有工作区和 dispose 的真实影响。

### What

- 前端在操作开始时冻结 workspace、个人/公共 worktree、分支、暂存文件和作用域；结果按单次操作统计，推送类操作只有确认远端成功才显示成功，并展示后端返回的远端分支与 commit。
- Agent 配置进度 WebSocket 与现有文件/应用源码通道对齐显式 `*` 的 CORS 语义，一次性 ticket 仍为必需；无 `operationId` 的握手失败只降级实时进度，真实业务失败仍按 operationId 终止。
- 后端为个人工作区发布响应补充 `remoteBranch`，公共操作持久化 branch；公共 rollout 跳过不再可复用的旧命名 worktree，并把既有相应任务安全标记为 `ABANDONED/WORKTREE_NO_LONGER_REUSABLE`。

### How

- 定向后端 158 项、进度 WebSocket 2 项和前端 Git 面板 48 项通过；前端全量 typecheck、production build，以及后端 22 模块 `clean package` 通过。
- 使用内网 GitLab 私有仓库实际验证：测试个人本地提交 `24565915e8847736e66d15195841021d26aa6d34` 且远端不前进；测试应用 feature 推送 `f3f6760477aa7dcf59c0c1780bd46b1deb0c773a`；自动化个人本地提交 `9a45b196ab4d31a994fb57c95469026abaef3f03` 且远端不前进。
- 从 `http://127.0.0.1:3000` 一步式提交并推送公共 Agent 到 `d2c941e50854cba7be43bddf516dfc5af2246321`；远端、共享副本、稳定个人 worktree 一致，rollout `acr_da1a7ff67b7a4be0b73dbd81ba7d13a5` 完成，dispose=1、pending=0、worktreePending=0。

### Result

- 四类 Git 链路均由真实平台和隔离远端完成验收；进度弹框不再先失败后转圈，发布成功会给出可独立核验的远端分支与 commit，切换 Tab 不会改变正在执行的目标。
- 本地 test 数据库的公共 Agent Git 参数临时指向内网专用验收仓库；未修改 `.env*`、OpenCode 源码、generated SDK、数据库结构或 migration。本次只有 additive `remoteBranch` DTO 兼容扩展，不改变 RunEvent；release 修复后仍需按长期分支策略同步回 dev。

## 2026-08-14 - TCDS 需求导入页恢复原始 Vue 紧凑样式

### Why

- 用户要求去掉需求导入页无用装饰，视觉恢复到提供的 `indexNew.vue` 初始风格，同时保持全选和多选功能不变。

### What

- `RequirementImportView.vue` 去掉内嵌页重复的眉题、大标题、胶囊计数、圆角卡片、双列子项和装饰动效，恢复白底、顶部紧凑筛选、单列目录与底部蓝色“生成”按钮。
- 保留既有同源上下文、筛选、全选当前结果、父项批量选择、子项多选、100 项上限和导入回调；新增组件测试锁定全选、半选与父项重新全选状态。
- 同步 `frontend/apps/agent-web/README.md` 的稳定页面说明。

### How

- 专项 Vitest 3/3、agent-web typecheck 和 production build 通过；构建只保留既有大 chunk 提示。
- 在 `http://127.0.0.1:3017/workspace-requirement-import` 启动真实 Vite 页面并用浏览器 mock 数据核对：选择两个子项后计数为 2、生成按钮可用、父项和全选均为半选；视觉截图确认页面为原始 Vue 风格的白底紧凑单列表格。
- 提交前回顾全部 `.agents/session-log*.md` 并隔离工作树中其他未提交改动；未修改 `.env*`、API、RunEvent、数据库、部署、安全、generated SDK 或 OpenCode 源码，也未新建分支。

### Result

- TCDS 需求导入页装饰已收敛，全选、多选和导入业务行为保持兼容；真实前端已启动验证并在核对后停止。

## 2026-08-14 - 固化 TCDS 内网地址与后台 toolId 契约

### Why

- 小地球需求导入从旧 iframe 改为平台后台调用后，需要确认现场 `http://tcds-prod.sdc.icbc:9080` 与 `toolId: 66f36bfa5c1c6105572b0118880261d6` 是否覆盖全部 TCDS 后台接口。
- 现有统一网关已经复用固定 header，但昨天的安全收敛把地址改成必填环境变量，仓库模板仍是占位地址，且测试没有逐条锁定 header。

### What

- `application.yml` 将现场局域网地址作为默认值并保留 `TEST_AGENT_TCDS_BASE_URL` 覆盖；本地与企业 `backend.env` 模板同步同一地址。
- 不新增 TCDS 客户端或并行请求路径，继续复用 `TcdsHttpGateway` 并收敛统一请求构造器；登录、用户、应用、子条目、文档元数据和 TCDS 同源文档请求统一携带精确 `toolId`，重定向到跨域对象存储后不透传该 header。
- 同步后端与 integration README、HTTP API、部署、安全和安全扫描复核文档；浏览器仍只访问平台同源 API，不接触 TCDS token、header 或下载地址。

### How

- JDK 25 定向 Maven reactor 通过：TCDS 网关/装配 9 项、应用配置绑定 15 项，0 失败；后端 22 模块跳过测试完整打包成功。
- 按 `.env.test`/`test` profile 运行标准重启，后端 health/readiness 为 `UP`，前端 200，CORS 正确，manager 最终 health 为 `HEALTHY`。
- `tcds-prod.sdc.icbc:9080` TCP 连通；携带精确 `toolId` 的无真实用户只读 HTTP 探针被上游直接断开，未返回 HTTP 状态。

### Result

- release 当前后台默认地址、全部固定 TCDS JSON 接口和同源文档请求 header 已满足现场契约，跨域对象存储不泄露 header，环境覆盖保持兼容；未新增 API、DTO、RunEvent、数据库、migration、服务、端口或强制配置。
- 有效登录用户下的真实授权目录、重复覆盖、部分失败和文件树刷新仍需企业会话验收；release 修复后仍需按长期分支策略同步回 dev。

## 2026-08-14 - 补全 Git 推送不确定状态恢复与多权限 UI 端到端验收

### Why

- workspace 与公共 Agent 的“一步提交并推送”会在 Git/SSH 超时、HTTP 回包丢失或进度 WebSocket 迟到时出现本地已提交但页面仍转圈、步骤停在 `PENDING`、远端事实不明且刷新后无法继续的问题。
- 平台同时存在普通 workspace、应用 Agent、公共 Agent、自动化仓库、`spec/**` 本地资产、多用户远端并发与不同 Git 权限，不能把单条成功链路作为验收结论。

### What

- workspace 与应用 Agent 在本地提交成功后保存当前 Tab 的待推送逻辑上下文，刷新后可复用原文件白名单幂等“重新推送”，不重复创建本地提交；用户可只清除浏览器提醒，已完成的本地提交不回退。公共 Agent 继续以后端 `publishPending` 为权威事实。
- 应用 feature 发布改用 index 与 HEAD 的真实差异判断是否需要 commit；push 异常后按远端已包含、确认未包含、无法确认三态收敛，分别继续成功、回退 feature 临时提交并允许立即重试、或保留 PREPARING 闸门交给后台核验。错误只返回稳定恢复状态与 traceId，不向页面暴露 URL、命令或 stderr。
- Git 超时现在终止顶层 Git 及其 SSH 等后代进程。拉取失败弹框展示错误码、脱敏提示和 traceId；延迟进度事件不能覆盖 HTTP 终态。同步更新 common/workspace/frontend README、HTTP API、安全规范和应用 worktree 测试说明。

### How

- 定向通过 Git 执行器 7 项、真实 Git 20 项、应用发布服务 101 项，以及前端 Git/错误恢复 4 个文件 168 项；前端 typecheck、production build 与后端 22 模块跳过测试完整打包通过。指定后端 reactor 19 个模块全量测试成功，其中 workspace 448 项、API 567 项，全部 0 failure/0 error。
- 从 `http://127.0.0.1:3000` 完成 UI 级真实验收：自动化个人 worktree 和测试 workspace `spec/**` 只建立本地提交；普通 workspace 与应用 Agent 推送到 feature；公共 Agent 推送、共享同步与 rollout target `DISPOSED`；另一提交者推进远端后的非冲突合并、add/add 冲突、取消合并、采用远端并完成 merge；普通用户应用过滤与公共管理入口隐藏。
- 使用拒绝连接地址验证确定性断网错误，页面返回 `GIT_UNAVAILABLE`、脱敏 `gitFailureHint` 和 traceId；Gitee SSH 偶发断连后重试成功。复现并修复 Git 超时遗留 SSH 子进程；最终无残留 Git/SSH 发布进程。测试仓库 URL、原 feature/个人/public 分支、物理 worktree 目录和数据库 target commit 已恢复，端到端证据保留在被测仓库本地 `codex-e2e-*` 分支。
- Docker 恢复后按 `.env.test`/`test` profile/JDK 25 用最终产物重启 backend、manager 与 frontend；health/readiness 为 `UP`、3000 返回 200、登录 CORS 正确、manager WebSocket 已连接且进程 health 最终 `HEALTHY`。重新登录 UI 后三个 Git 作用域均为 0，无待推送、冲突或无效 commit 提示。

### Result

- `release` 上四类 Git 链路、多人冲突、权限隔离、网络失败、刷新恢复和 dispose 已完成真实 UI 端到端验证；网络抖动不再把“本地提交成功”伪装成全失败，也不会因盲目重试重复 commit。
- 本次不新增 HTTP URL、DTO 或 RunEvent 类型，不改数据库结构/migration、部署拓扑、`.env*`、generated SDK 或 OpenCode 源码；只扩充既有错误 details 的稳定可选字段与前端展示。Gitee SSH 仍可能受外部网络偶发断连影响，但页面保留可重试入口和管理员可关联的 traceId。

## 2026-08-14 - 合入案例远程维护并统一 TCDS toolId

### Why

- 远端 `release` 的案例维护功能新增 `getTaskTypes` 与 `createGraphCase` 两条 TCDS 请求，但前者没有携带现场要求的 `toolId`，两者还各自硬编码了不含 `:9080` 的生产地址。
- 合并远端提交时需保留本地 `release` 已有的体验工作区、会话消息和个人进程重启客户端能力，不能让自动合并静默删除既有 API。

### What

- 新增包内 `TcdsHttpRequestFactory`，由 `TcdsHttpGateway` 与 `TcdsCaseMaintenanceService` 共同复用部署地址、HTTP 超时和同源 `toolId: 66f36bfa5c1c6105572b0118880261d6` 注入；任务类型与案例维护均通过 `${TEST_AGENT_TCDS_BASE_URL:http://tcds-prod.sdc.icbc:9080}` 访问。
- 案例维护服务改为 Spring 显式装配并复用统一 `HttpClient`；测试分别锁定 GET、POST 和校验失败前的任务类型请求都携带精确 header 与 `:9080` 端口。
- 语义合并远端案例维护弹窗、Markdown 解析、平台 API 与日志脱敏能力，并恢复自动合并丢失的体验工作区打开/关闭/提交、Run 消息查询和个人进程重启 6 项既有前端客户端方法。
- 同步 integration/API/前端 README 与 PACKAGE，以及 HTTP API、部署、安全和模块图；未修改 `.env*`、RunEvent、数据库、migration、generated SDK 或 OpenCode 源码。

### How

- JDK 25 定向 Maven reactor 通过：TCDS 网关/案例维护/装配 16 项、TCDS Controller 与 API 日志 25 项、应用配置绑定 15 项，共 56 项，0 失败。
- 前端案例维护、编辑器入口和 backend-api 定向 Vitest 137/137，通过全 workspace typecheck、用户手册与 agent-web production build；构建仅保留既有大 chunk 提示。
- `tools/verify-ai-docs.sh`、`git diff --check` 和冲突标记复核通过；提交前已回顾全部 `.agents/session-log*.md` 近期记录。

### Result

- 案例远程维护的全部 TCDS 后台调用现在与需求导入链路共用同一地址和同源 header 规则；浏览器仍只调用平台 API，跨域对象存储不会收到内部 `toolId`。
- 这是现有部署拓扑内的 `release` 修复，HTTP API 为新增平台入口，既有 API/DTO/事件和客户端调用保持兼容；真实 TCDS 业务权限与数据写入仍需在有效企业会话中验收，并按分支策略同步回 `dev`。

## 2026-08-14 - 补齐企业包 Flyway/TCDS 门禁并修复 SCM 姓名游标

### Why

- 本轮 `release` 相对上一企业包新增 PostgreSQL `V20260813190929` 和 XXL MySQL `V12`，但内层打包、外层封装和目标机安装后校验仍只锁定到 `20260812204207` / V11，旧依赖 JAR 可能漏检。
- SCM Git 姓名补偿的首屏游标 SQL 使用空参数 OR 表达式，真实 PostgreSQL 无法推断参数类型；原 H2 回归未覆盖该数据库差异。
- 两台企业后台节点包必须统一使用现场 TCDS 地址 `http://tcds-prod.sdc.icbc:9080`，不能仅校验配置非空。

### What

- 三层企业发布脚本新增 PostgreSQL `20260813190929` 与 XXL V12 的文件名/SHA-256 字节锁，外层封装在临时节点副本中写入并复核精确 TCDS 地址，逐机部署脚本也拒绝其它地址。
- `UserScmGitIdentityMapper.xml` 复用现有 MyBatis 动态游标模式，首屏不生成 `user_id > afterUserId`，后续页才绑定非空游标；真实 PostgreSQL 测试同时锁定首屏与末页。
- 企业多后台手册更新上一包基线、两套 Flyway 允许增量、checksum、停止条件和 V12 任务验收；持久层 README 同步 PostgreSQL 空游标兼容说明。

### How

- 将本机仅用于 Testcontainers 的 `postgres:16-alpine`、`mysql:8.4` 切换为 arm64 原生镜像，避免 amd64 仿真超过容器启动等待窗口；企业 worker linux/amd64 制品不受影响。
- JDK 25 下真实 PostgreSQL 兼容矩阵 25/25、SCM MyBatis PostgreSQL 1/1、Flyway 文件命名/字节锁 13/13 通过；真实 MySQL 8.4 空库、V8→V12 与并发初始化 4/4 通过。
- 四个 Shell 脚本 `bash -n`、`git diff --check` 通过；未修改 migration 原始字节、`.env*`、generated SDK 或 OpenCode 源码。

### Result

- 企业包从构建、外层封装到安装后都会拒绝缺失或字节不匹配的两条新 migration，两台后台节点包的 TCDS 地址固定一致。
- 每日 04:10 的 SCM Git 姓名补偿首次扫描不再因 PostgreSQL 空参数类型推断失败；数据库变更仍只有已提交的新增表与 V12 任务，本次没有新增 migration。

## 2026-08-14 - 记录 TCDS/SCM 大版本企业增量包

### Why

- 用户要求以当前本地 `release` 重新打企业包，本轮相对上一包包含需求导入、TCDS 案例维护、Git 发布恢复、SCM 姓名校准等较大改动，需要记录可追溯源码、制品哈希和逐机验收基线。

### What

- 制品源码提交固定为 `ad37bfe7675eca62f9b9a2d4d4518bb90889384c`；内层发布 ZIP SHA-256 为 `34b5b8d82d17acd78ea100684d0c68b6261dcc713f87b90b770f5b08a047c102`，外层完整包 SHA-256 为 `2fef802357ad060b715ef0d36f8f93022c8e702ba20bb6eee7a7a84dcf61129c`。
- 后端 app JAR 为 `6aa6b398205d965bef02e08878f3e152ca216548a3fe8a744dffa6961a020047`，persistence JAR 为 `0eba3f1b6f1b3372398d07ba8975f122f1c840cd8c73b1423c2382188f0ad8e3`，XXL integration JAR 为 `aa6d2df6f7821e6b3b44ff9ea0b13613060bc255a02f6ce93def5b1700eaa366`，前端归档为 `23e6bb2d6bc6eff6ffbb21accaf678bf04b6761ab36995b136750363b13588ca`。
- models 快照 SHA-256 保持 `6a510be17a7b0616f128fad130773c3fb6ad7a3d4d7881ec59f2873e17cbc44c`；Qwen 日期 `2026-08-07` 高于 DeepSeek `2026-08-06`，继续仅按既有灰度要求在 `.4` 安装，`.114` 保留现网模型文件。

### How

- JDK 25 完成后端构建，agent-web production build 成功；worker runtime 指纹 `50f56c...`、toolbox 指纹 `35447d...` 均判定 `reuse`，LobeHub 标记 `disabled`，Workflow 运行制品不存在。
- 内层 `--validate-only`、内外层 ZIP SHA、外层内嵌内层 SHA、全部新旧 Flyway 资源、RSA 资源、节点包结构均通过；两台后台节点包均确认 `TEST_AGENT_TCDS_BASE_URL=http://tcds-prod.sdc.icbc:9080` 和相同 CORS，未输出任何密钥。
- 外层包复制到 `/Users/kaka/Desktop/mimoagent/0709/` 后重新执行 SHA 校验通过；用最终构建产物重启本地 backend、manager、frontend，不启用 LobeHub，health/readiness 为 `UP`、前端 200、manager WebSocket 已连接且进程 health 收敛为 `HEALTHY`。

### Result

- 可交付文件为固定名 `test-agent-two-backend-complete.zip` 与 `.sha256`，大小约 128 MiB；企业内从中转机 `~/Desktop/mimoagent/0709` 校验并按 `.4 → .114 → .2` 顺序部署。
- 数据库只允许从上一包 PostgreSQL `20260812204207` / XXL V11 基线分别新增 `20260813190929/-297528120` 与 V12/`-211900485`；任何失败、未知 checksum 或未知更高版本均停止，不使用 repair/outOfOrder/手改历史表。

## 2026-08-14 - 基于已部署灰度包恢复 worker reuse 指纹门禁

### Why

- 新一轮平台增量包在后台配置安装后、Java 停止前被 worker runtime 指纹门禁拦截；上一轮 `.4` 模型灰度已经成功，manager/worker 实际不需要加载镜像或重启。
- 原 `reuse` 只接受目标机已有状态文件中的精确指纹，无法处理“runtime 已先部署成功、组件状态门禁后引入或记录缺失”的存量节点；直接跳过门禁会失去既有安全检查。

### What

- `package-release.sh` 新增显式 `--worker-runtime-baseline-file`，baseline 固定上一轮源码提交、内层 release SHA-256 和 worker 指纹；封包只在 baseline 指纹与本轮构建输入完全相同时保留 `reuse`，仍不携带 programs/worker 镜像。
- `deploy-internal-release.sh` 在普通目标指纹不匹配时，只接受格式完整的随包已部署 baseline；先复用既有 Manager/OpenCode/Codex 与 Tool runtime 检查并只读确认 worker 容器健康，全部通过后才原子补写组件状态，再继续原 `reuse` 流程。
- 增加 `release-baselines/20260813-qwen-gray.env`，记录已部署灰度源码 `57e211de...`、内层 release `7af9c20e...` 和 worker 指纹 `50f56c...`；`deploy-backend-node.sh` 后续写 toolbox 指纹时保留这些审计字段。
- 同步企业 README 与双后台部署手册，并增加封包、旧门禁严格失败、可信 baseline 健康后恢复和自动节点状态保留回归。

### How

- 在独立 detached worktree 对已部署源码 `57e211de48a5507fb8d1689e1c8f86fd96563032` 执行组件计划，重新计算 worker 指纹为 `50f56c54991bd7d5b3926fcb8442655b3ca1371a56ec6165a9ec19626f672fb1`，与当前 release 完全一致。
- `verify-internal-incremental-components.sh`、`verify-internal-multi-backend-node.sh`、`verify-internal-auto-node-deploy.sh`、`verify-internal-two-backend-complete-package.sh`、`verify-ai-docs.sh`、相关 Shell `bash -n` 和 `git diff --check` 均通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，确认上一灰度、当前失败包、Flyway 和模型灰度边界一致；未修改 `.env*`、OpenCode 源码、generated SDK 或 migration 字节。

### Result

- 指纹门禁仍默认严格失败；只有本轮显式携带的已部署 baseline 可恢复缺失/旧状态，并且恢复前必须通过真实 runtime 与容器健康检查。
- 恢复动作只更新 `/data/testagent/config/release-component-state.env`，不执行 `docker load`、不重启 manager/worker、不替换活动的 `opencode-models.json`；`.4` 的 Qwen 灰度和 `.114` 的现网模型继续分别保留。
- 本次只修改发布脚本、回归、文档和非敏感基线元数据，不涉及 API、事件、数据库结构、Flyway SQL、性能、安全边界或部署拓扑变化。
- 最终企业包源码提交为 `731b2f1ae9f78aa0d4ef71af3727515f756ebeed`；内层 ZIP SHA-256 为 `35687eb1035faa217fee1ad6e54f11cc35f66bd6fcdf898b186ef63b1c0fdbea`，外层完整包为 `1734a0f30eff5eb78a6da3e6c4a09005161daee636481de0a135539838216700`。
- 后端 app JAR 为 `8767313d4a96f7332efd1481e2ecd01833e3f508b9c498aeaabc2641eb15ebbf`，persistence JAR 为 `aa502bceb9457b22b1abfa24f328b6b33a9b8922aa5a958f02d8dbc28f1b3c2b`，XXL integration JAR 为 `e2aacd9f6221bf68abdebac2125dc15d872ed559d10280bad943e03218f7390d`，前端归档为 `9679040a4f98cefee02252f3928568987eb2528098317346f4cdaa06224471a9`。
- 最终独立校验确认外层 checksum、两层 ZIP、外层内嵌内层逐字节一致、`--validate-only`、全部受保护 Flyway 资源、两台 TCDS 精确地址和 worker 制品缺失门禁均通过；发布清单为 worker/toolbox `reuse`、LobeHub `disabled`。

## 2026-08-14 - 修复 TCDS 需求导入筛选布局与历史文档转换

### Why

- 同源需求导入 iframe 的版本、应用仍是不可输入检索的原生选择框，初次条目请求较慢时还会持续禁用；三列筛选器和条目目录缺少收缩边界，在窄 iframe 中会横向溢出。
- TCDS 历史文档可能出现 `.doc/.ppt` 文件名与实际 DOCX/PPTX 容器不一致，现有转换器严格按扩展名选择解析器，导致每次导入稳定返回“TCDS 文档转换失败”。

### What

- 版本和应用改为复用 Element Plus 的可检索 `el-select`；应用目录加载完成即释放控件，迟到条目请求继续由既有 sequence 防竞态。筛选区改为可收缩网格，搜索框、目录、父子条目统一限制在 iframe 宽度内。
- Word 和 PowerPoint 转换按实际容器依次尝试 OOXML/旧格式解析，Word 历史纯文本再走受控文本兜底；下载响应的 content type 传入转换器，Office 文件遇到 HTML/JSON 错误页时明确拒绝，日志只保留格式、脱敏媒体类型和异常类别。
- 同步 workspace 与前端模块 README、扫描复测文档；未修改 `.env*`、HTTP API、RunEvent、数据库、migration、generated SDK 或 OpenCode 源码。

### How

- 前端定向 Vitest 5/5、agent-web typecheck 和 production build 通过；真实 iframe 验证版本可输入检索，筛选区与条目目录 `scrollWidth == clientWidth`，搜索框右边界未越出容器。
- JDK 25 下文档转换与导入服务定向 Maven 测试 9/9，通过完整后端跳过测试打包；使用根目录 `.env.test` / `test` profile 启动 release worktree 的 backend、manager、frontend，8080 health/readiness 为 `UP`，3000 页面与登录 CORS 正常。
- 本地测试账号无法取得企业 TCDS 授权应用，因此真实企业文档下载未执行；兼容路径由实际 DOCX/PPTX 二进制伪装旧扩展名及服务级工作区写入测试覆盖。

### Result

- 版本和应用可输入筛选，条目区域不再超出 iframe；历史 Office 文件不再仅因扩展名与真实容器不一致而转换失败，错误页也不会被误写为 Markdown。
- 这是现有部署拓扑内的 `release` 修复，不影响公共 Agent、既有 Git diff/提交推送链路或工作区 ID 语义；真实 TCDS 授权数据仍需用户在企业会话中最终验收，并按分支策略同步回 `dev`。

## 2026-08-14 - 重新打包 TCDS 需求导入修复企业包

### Why

- 用户要求基于当前本地 `release` 重新打企业包；相对上一制品，源码新增 `9b1151ed0` 的 TCDS 需求导入筛选布局与历史 Office 文档转换修复。

### What

- 制品源码提交固定为 `9b1151ed05b0e2acbbbd8ac8ecf08eda646a5bc7`；内层发布 ZIP SHA-256 为 `76a15ab3e7f0a3f3c0e1033216150c89774516cc3df3073c00eba8b36b74224c`，外层完整包为 `c69a1d629ff09772edfbcca9fc329d840812e98650d496cc08d7f4d0ec32677a`。
- 后端 app JAR 为 `721e4f02d60d1c703dbd6699b0d5d7454280fb5ce1eddb35509e1476a1f4c0ae`，persistence JAR 为 `18ec9098e26bf80d3b85ad93aa653eab6943bad0560104b9da9dac2a1b88a11c`，XXL integration JAR 为 `5057bad68fd9e7ef5f68cdc869b486a20047527fe5b92ae26fff7e8730cc5901`，前端归档为 `662636c468ac3fa5ae00599dae6a20d430101b7a0b75608a4ad26cee2eacb0d1`。

### How

- JDK 25 后端构建、Spring 构造器装配、前端用户手册、`vue-tsc` 与 Vite production build 通过；需求导入后端定向测试 9/9、前端全量 Vitest 1980 passed / 1 skipped。
- worker runtime 指纹继续为 `50f56c...`、toolbox 指纹继续为 `35447d...`，两者均为 `reuse`；内层包未携带 worker/programs 制品，LobeHub 为 `disabled`。
- 内外层 ZIP、外层内嵌内层逐字节一致、内层 `--validate-only`、最终 JAR 全部受保护 Flyway 资源和两台 TCDS 精确地址均验证通过；未修改任何 migration 字节。

### Result

- 固定名外层 ZIP 与 SHA 文件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/` 并再次通过校验，部署仍按 `.4 → .114 → .2`，不加载或重启 manager/worker，也不替换两台活动的 `opencode-models.json`。
- 本轮没有新增 API、事件、数据库结构、Flyway SQL、配置项或部署拓扑；真实企业 TCDS 授权数据和历史 Office 文件仍需现场业务验收。

## 2026-08-14 - 恢复 TCDS 已导入条目状态展示

### Why

- 旧版需求导入页会展示父子条目的“已导入/未导入”状态，但内置同源页面迁移时只保留了重复选择和覆盖导入语义，状态提示丢失，用户无法区分已落盘目录与待导入条目。

### What

- 新增受既有文件 WebSocket ticket、逐 RPC 鉴权和工作区写权限保护的 `workspace.requirement-import-items`，后端按导入使用的同一套路径规范化规则检查父目录和子条目 `01-需求` 目录是否存在。
- 导入页恢复父子条目“已导入/未导入”标记；已导入项不禁用，继续允许覆盖更新和部分失败重试。状态查询不返回物理路径、文档 URL 或 TCDS token。
- 同步 API、事件流、workspace、backend-api 和 agent-web 文档；未修改数据库、Flyway、RunEvent、环境配置、公共 Agent 或 Git diff/提交推送流程。

### How

- 前端需求导入定向 Vitest 6/6、agent-web typecheck 和 production build 通过；仅含本次暂存内容的独立快照后端 workspace/API 定向测试合计 46/46 通过，覆盖规范化目录状态、合法 RPC、共享会话拒绝和敏感字段不返回。
- JDK 25 下完成 22 模块跳过测试打包，并用主工作区 `.env.test` / `test` profile 启动 release worktree；8080 health/readiness 为 `UP`，3000 页面返回 200，manager 未出现解码、重连或致命错误。

### Result

- 已恢复旧版可见状态，同时保持旧版允许重新选择的行为；用户可明确判断条目是否已导入，并继续执行覆盖导入。
- 本地账号无法取得企业 TCDS 授权目录，真实企业目录的 UI 状态仍需在用户企业会话中验收；release 工作区中另一批尚未提交的 Word 图片附件改动未纳入本次提交。

## 2026-08-14 - 完善 Word 结构化 Markdown 转换

### Why

- TCDS Word 导入仅通过 `paragraph.getText()` 抽取文本，用户实际产物的标题、段落、列表和表格被压成超长行，内嵌图片也没有附件引用。
- 旧 `folderManager` 也主要是文本抽取，只能保留 DOC/DOCX 容器兼容思路，不能继续沿用转换质量。

### What

- 新增 `WordToMarkdownRenderer`：DOCX 按正文元素原顺序渲染 Heading/大纲标题、Run 加粗/斜体/删除线、分层有序/无序列表、受控超链接、Markdown 表格、硬换行、分隔线和内嵌图片；旧 DOC 由 HWPF 尽力保留标题、列表、基础字符样式和可提取图片。
- 图片写入 Markdown 同级 `{文档名}.assets/image-NNN.{ext}`，并通过现有工作区安全路径校验的模块内部二进制写入入口幂等覆盖；Markdown 与附件合计继续受 20 MiB 单文档上限约束。
- 按用户最终决策保留 Word 扩展名实际返回文本时的兼容导入；可解析 DOCX/DOC 容器始终优先走结构化渲染。同步 workspace README、HTTP API 语义和安全复测文档。

### How

- JDK 25 定向转换/导入/文件服务测试 51/51 通过，覆盖标题与大纲级别、行内样式、安全/不安全链接、分层列表、表格、图片引用与字节、覆盖写入、路径穿越和容量限制；受影响 reactor 干净全量 77 个套件/622 项全部通过。
- 用户进一步明确“Word 文件名实际返回压平文本时不能报错”后，补充 `.doc/.docx`、UTF-8/GB18030、`text/plain`/`application/octet-stream`/`application/msword` 的转换器与完整导入服务回归；定向 14/14、workspace-management 受影响 reactor 684/684 通过，纯文本回退不生成图片附件。
- 22 模块 `mvn clean package -Dmaven.test.skip=true` 成功。使用根目录 `.env.test` / `test` profile 启动 release worktree；期间两次被主工作区并行重启抢占 8080，等待并行任务结束后重新启动并等待 20 秒，health/readiness 均为 `UP`、3000 为 200、登录 CORS 正常、manager 无重连循环。
- UI 级登录、工作台和同源导入 iframe 打开成功；当前本机 TCDS 返回“服务暂不可用”，因此未执行真实企业 Word 下载与导入。

### Result

- 原始 DOCX 不再是文本抽取，可保留上述 Word 结构；图片通过安全工作区文件服务写盘并使用相对 Markdown 引用。上游只提供已压平文本时继续可导入，但已丢失的 Word 样式无法从纯文本还原。
- 结构化渲染前后使用的是同一条“OOXML → 旧 DOC → 可读文本”回退链；本轮无需修改生产转换逻辑，只用回归测试锁定旧链路可导入的压平文本不会因发布新渲染器而退化。
- 本次不新增 HTTP/RPC/RunEvent 接口、数据库/Flyway、部署变量或依赖，不修改 `.env*`、generated SDK 或 OpenCode 源码；公共 Agent、工作区 ID 及 Git diff/提交/推送链路未变更。

## 2026-08-14 - 合并远程 release 的 TCDS 案例日志增强

### Why

- 用户要求拉取远程 `release` 后重新打企业包；远程新增提交 `9ee0ff643078d93b298060b0bf553f83073ab967`，本地同时保留需求导入状态、Word 结构化转换和纯文本兼容修复。
- 远程 TCDS 实现基于旧地址装配，直接合并会重新写死生产地址并绕过当前统一 `TcdsHttpRequestFactory`，与企业 `:9080` 地址契约冲突。

### What

- 保留统一 `TEST_AGENT_TCDS_BASE_URL`、共享 `toolId` 注入和所有 profile 实时请求语义，吸收远程确认的 10 项 `name/value -> taskType` 精确映射、仅提交勾选案例及 `createGraphCase` 请求/响应脱敏诊断日志。
- 日志不复制固定 `toolId` 常量；请求正文只保留有界长度和 SHA-256 短摘要，响应 `data` 只保留类型、数量和摘要，非法、空或超限正文不记录原文。
- 同步 integration、前端、HTTP API 和安全文档；未修改数据库、Flyway、RunEvent、环境配置、部署拓扑、generated SDK 或 OpenCode 源码。

### How

- JDK 25 下 TCDS 配置/服务与需求文档转换/导入定向测试 27/27 通过；前端案例维护定向 Vitest 16/16、全 workspace typecheck、用户手册和 agent-web production build 通过。
- 冲突标记、`git diff --check` 均通过；提交前回顾全部 `.agents/session-log*.md` 近期记录，确认继续保留 `.4` Qwen 灰度、`.114` 现网模型及 worker/manager 不重启边界。
- 对比上一企业包源码 `9b1151ed05b0e2acbbbd8ac8ecf08eda646a5bc7`，Flyway migration 文件无变化。

### Result

- 当前 `release` 同时包含远程 TCDS 日志/任务类型增强和本地三项需求导入修复，TCDS 地址仍由企业部署配置统一控制。
- 真实企业 TCDS 任务类型、案例维护和 Office 下载仍需现场验收；本地自动化没有替代企业网络验证。

## 2026-08-14 - 打包远程合并后的 release 企业包

### Why

- 用户要求拉取远程 `release` 并基于合并后的最新代码重新打包，上一轮企业包已经部署完成，本轮继续使用增量组件边界。

### What

- 制品源码提交固定为 `d9d5da7e7e95a41ff2813e06636feac093930cbd`；内层发布 ZIP SHA-256 为 `168533721fd28a466811f2a92b9b2ccb1a66eb6d22ea8c09b339be96cb0b50ab`，外层完整包为 `69d09bc0907d4053a3b73dbf21760cdce6122c35e2d5996bc8cbb9a3a5f201f6`。
- 后端 app JAR 为 `e232a87f6302b3ebdb9d0f0a9f5758e1a6e5b528b45572e2c8e559ab20ab3863`，persistence JAR 为 `43d34ddadfe724441b3d9899cae00dbb4004ba54aa368dc3b6958d24e56db666`，XXL integration JAR 为 `f5e5bb0443f922a3aaa79ac23f2f1845bede542a12c827213f44a2e25034134d`，前端归档为 `179894a24cf8273c27cd3f584566765b828ea7e58a555a39af996daa01e30490`。

### How

- 显式以 `VITE_TEST_AGENT_WORKFLOW_ENABLED=false`、`VITE_TEST_AGENT_LOBEHUB_ENABLED=false` 构建；worker 指纹仍为 `50f56c54991bd7d5b3926fcb8442655b3ca1371a56ec6165a9ec19626f672fb1`，toolbox 指纹仍为 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`，两者均为 `reuse`。
- 内外层 ZIP、外层内嵌内层逐字节一致、内层 `--validate-only`、最终 JAR 受保护 Flyway 资源、两台 TCDS `http://tcds-prod.sdc.icbc:9080` 和 worker/programs 制品缺失门禁均通过。
- 提交前再次确认全部 `.agents/session-log*.md` 近期记录未与本轮发布边界冲突；本轮没有 migration 文件变化。

### Result

- 固定名外层 ZIP 与 SHA 文件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/`，并再次通过 `sha256sum -c` 与 `unzip -t`。
- 部署顺序仍为 `.4 -> .114 -> .2`；不加载或重启 manager/worker，不替换 `.4` Qwen 灰度或 `.114` 现网 `opencode-models.json`。

## 2026-08-14 - 回退 Word 结构化转换并补生成蒙版

### Why

- 用户现场反馈导入并生成 Word Markdown 后工作台明显变卡；样本文档 `/Users/kaka/Desktop/qr-decode/out/需求子条目S20260629-001347设计文档.md` 只有 49 行但最长单行约 1450 字符，确认 TCDS 返回的多数内容本身已压平，结构化渲染和图片附件不能还原已丢失格式，反而增加转换与编辑器渲染开销。
- 生成期间页面只有按钮禁用，没有明确的等待反馈，用户容易重复操作或误判为卡死。

### What

- 删除独立 `WordToMarkdownRenderer`、图片附件导出及 workspace 模块内部二进制写入入口，恢复 DOCX 按段落/表格文本输出和旧 DOC 的 HWPF 文本提取；继续保留 `.doc` 名称承载 DOCX、Word 扩展名实际返回 UTF-8/GB18030 纯文本、跨 Office 容器、错误包络拒绝、路径/容量限制和覆盖导入兼容性。
- 需求导入页在生成期间增加覆盖 iframe 内容区的状态蒙版、轻量旋转动画和明确提示，同时阻断重复交互；为减少动态效果的系统偏好保留降级样式。
- 同步 workspace、agent-web README、HTTP API 和安全复测说明；未修改 `.env*`、HTTP/RPC/RunEvent 契约、数据库、Flyway、公共 Agent、工作区 ID 或 Git diff/提交推送链路。

### How

- JDK 21 下转换/导入/文件服务定向测试 50/50，通过 workspace-management 受影响 reactor 453/453；后端相关模块 package 和完整 22 模块启动前打包成功。
- agent-web typecheck、需求导入定向 Vitest 7/7 和 production build 通过，覆盖蒙版显示、重复交互阻断和完成后自动关闭。
- 使用主工作区 `.env.test`、`test` profile 和 JDK 25 启动当前 release worktree；8080 health/readiness 均为 `UP`、3000 返回 200、登录 CORS 正常，manager WebSocket 已连接且无解码或重连循环。浏览器确认受保护导入路由会正确跳转登录页，本机无企业登录态，未冒用身份执行真实 TCDS 导入。

### Result

- Word 导入恢复到结构化渲染改造前的轻量兼容语义，不会因文档名为 DOC/DOCX 但内容已压平而拒绝导入；不再生成图片附件或额外样式转换结果，降低现场压平长文本的处理负担。
- 生成过程现在有清晰蒙版动画，完成或失败后由既有 `finally` 自动解除。真实企业 TCDS 数据的体感和最终导入结果仍需用户在现有企业登录会话中复测。

## 2026-08-14 - 收敛小地球导入页打开与条目加载卡顿

### Why

- 用户反馈 Word 转换回退后页面仍卡，且打开“小地球”本身也会卡顿。运行时确认 iframe 仍通过主 SPA 路由启动，生产入口会额外预加载 Element Plus、Vue Query 和工作台共享依赖；条目状态查询还会为每个父子条目重复解析工作区元数据。
- 生成蒙版使用 `backdrop-filter: blur(2px)`，长文档生成期间会增加不必要的 GPU 合成开销。

### What

- 新增 `/workspace-requirement-import/` 独立 Vite HTML 入口，只挂载需求导入 Vue 页面和 `backend-api`；父工作台固定加载该同源地址，旧无尾斜杠主路由仅做兼容跳转。独立入口复用同一 `sessionStorage` 登录态，401 通过精确同源消息交给父工作台统一处理。
- 版本、应用改为原生可输入候选框，移除导入页对 Element Plus 的运行时依赖；生产入口不再预加载 Element Plus、Vue Query、Monaco 或 `AgentWorkbench`。生成蒙版保留旋转动画与操作阻断，但移除背景模糊。
- `WorkspaceApplicationService.fileStatuses` 一次解析工作区元数据并批量返回状态；`RequirementImportApplicationService` 预先生成受控父子相对目录后一次调用，各路径仍逐一经过公共文件服务的越界和符号链接校验。
- 同步 agent-web/workspace README、HTTP API、模块图和安全复测说明；未修改 `.env*`、HTTP/WebSocket/RunEvent 契约、数据库、Flyway、公共 Agent、Workspace ID 或 Git diff/提交推送链路。

### How

- 前端需求导入与文件树定向 Vitest 25/25、agent-web typecheck 和 production build 通过；独立入口构建资源约为页面脚本 8.6 KiB、接口客户端 65 KiB、Vue 运行时 127 KiB，不含 Element Plus、Vue Query、Monaco 和工作台主包。
- JDK 25 下 `RequirementImportApplicationServiceTest` 与 `WorkspaceApplicationServiceTest` 15/15 通过；22 模块 `mvn clean package -Dmaven.test.skip=true` 成功，`git diff --check` 通过。
- 使用主工作区 `.env.test`、`test` profile 和 JDK 25 启动当前 release worktree；8080 health/readiness 为 `UP`、3000 返回 200、登录 CORS 正确，manager WebSocket 已连接且进程健康为 `HEALTHY`。
- 真实登录页面热启动点击到弹窗出现约 0.4 秒；iframe 主区、筛选区、条目区均满足 `scrollWidth == clientWidth`，搜索框未越界。当前本机 TCDS 约 1.7 秒返回服务不可用，未执行真实授权目录和文档导入。

### Result

- 小地球不再在 iframe 内重复启动完整工作台，打开后先渲染可交互页面，再异步等待 TCDS；大量条目加载不再重复读取工作区元数据，生成阶段也不再使用背景模糊。
- 真实企业授权列表、生成蒙版和最终导入仍需用户在企业网络可用的登录会话中复测；当前本机只完成同源 UI、布局、构建产物、服务健康和自动化回归验证。

## 2026-08-14 - 重打需求导入性能优化企业包

### Why

- 用户要求基于当前 `release` 再次生成企业完整包；当前分支已经包含 Word 转换回退和 TCDS 需求导入独立轻量入口优化。

### What

- 制品源码提交固定为 `c8b6762fafcc61734d31826a377abdd29c120ccc`；内层发布 ZIP SHA-256 为 `54c137f003cec4002128e070572c084809efef3678da03d0da2aa6d1421f92de`，外层完整包为 `e6fa8205347f591331a994eb1b274fbc7ad0b7387030a382f5a9e3969ab0b934`。
- 后端 app JAR 为 `bb8433e6d37f36d27d37337a5a31eb9b46543dee6f9a3e392fcc5108e0ec22db`，persistence JAR 为 `f7b0c40687e5cc561a979c3e53662eb17260d0e771277b61ccb16f53a6b0f1dd`，XXL integration JAR 为 `ced90daf6ba37673830a84346725ee332d959865cb3d84a9b3895d1bd2949ea6`，前端归档为 `237b440068b3b8753770680595e4e7cc1705e8fcb33fee2a325ae170ac8d4e7d`。

### How

- 显式以 `VITE_TEST_AGENT_WORKFLOW_ENABLED=false`、`VITE_TEST_AGENT_LOBEHUB_ENABLED=false` 构建；worker 指纹仍为 `50f56c54991bd7d5b3926fcb8442655b3ca1371a56ec6165a9ec19626f672fb1`，toolbox 指纹仍为 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`，两者均为 `reuse`。
- 内外层 ZIP、外层内嵌内层逐字节一致、内层 `--validate-only`、最终 JAR 受保护 Flyway 资源、两台 TCDS 精确地址、worker 大制品缺失门禁和 `frontend/workspace-requirement-import/index.html` 独立入口均通过。
- 对比上一包源码 `d9d5da7e7e95a41ff2813e06636feac093930cbd` 没有 migration 或部署脚本变化；提交前回顾全部 `.agents/session-log*.md` 近期记录。

### Result

- 固定名外层 ZIP 与 SHA 文件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/`，并再次通过 `sha256sum -c` 与 `unzip -t`。
- 部署顺序仍为 `.4 -> .114 -> .2`；不加载或重启 manager/worker，不替换 `.4` Qwen 灰度或 `.114` 现网 `opencode-models.json`，数据库只允许 validate 且 history 零新增。

## 2026-08-14 - 同步 dev 并完成基础端到端验收

### Why

- 用户要求先合入最新 `release`，准备 dev 所需数据库与记忆服务，再启动 dev 代码并实际验证对话和文件附件。

### What

- 拉取 `origin/release` 并合入当前 `dev`，合并提交为 `5bb9601935bebc6f33b3980333a801f7bc1c4a01`，无冲突。
- 核验既有平台 PostgreSQL 的 Flyway 历史；为避免覆盖原有记忆库，初始化新的 dev 专用 pgvector 卷 `test-agent-memory-dev-pgvector-v2-20260814`，完成 Alembic 升级并保留旧卷。
- 以 JDK 25、`.env.test` 和 `test` profile 启动 backend、opencode-manager、frontend 及记忆服务；未修改 `.env*` 或业务源码。

### How

- 使用 `./restart-dev-services.sh --profile test --env-file .env.test --with-memory` 构建并启动当前 dev；后端 `http://127.0.0.1:8080/actuator/health` 返回 `UP`，记忆 API 与 embedding 服务的鉴权就绪检查通过。
- 通过真实浏览器登录工作台，第一轮要求仅返回标识文本，界面显示 `DEV_CHAT_OK_20260814` 且 Run 为 `SUCCEEDED`；上传 27 B 文本附件后，第二轮要求读取附件，界面返回 `DEV_ATTACHMENT_OK_20260814` 且 Run 为 `SUCCEEDED`。

### Result

- dev 已在 `http://127.0.0.1:3000` 运行，基础对话与聊天附件到工作区、模型读取附件的完整链路均已实际验证。
- 本次无 API、RunEvent、数据库结构、安全、generated SDK 或 OpenCode 源码变更；仅新增本机运行态数据库卷和被清理的浏览器验收产物。浏览器运行态资源轮询仍有既有 400 控制台记录，但未阻断本次两次 Run 成功。

## 2026-08-15 - 补齐 dev ClickHouse 并核验本地客户端形态

### Why

- 前一轮 dev 验收只启动了 PostgreSQL、Redis、XXL MySQL 和 Memory，遗漏了已经进入 `dev` 的运营分析 ClickHouse；用户同时确认“客户端”是安装在用户电脑上的本地客户端，而不是运营分析 Web 页面。

### What

- 新增 opt-in 的 `tools/clickhouse-dev-services.sh` 和 `restart-dev-services.sh --with-clickhouse`：固定使用 ClickHouse 26.3.17.56，只监听 `127.0.0.1:18123`，保留版本化数据卷，随机凭据和 Java JDBC dotenv 只写入 `.tmp/dev-services/clickhouse` 的 `0600` 文件，不修改 `.env.test`。
- 修复启用 ClickHouse 后 PostgreSQL 普通 mapper 与 `clickHouseSqlSessionFactory` 的装配歧义：普通 mapper 显式绑定主 `sqlSessionFactory`，并增加双工厂上下文回归测试；同步 backend、persistence、部署、ClickHouse 和 AI 工作流文档。
- 核验现有本地客户端为 Java 21 用户级后台服务：macOS 由 LaunchAgent、ARM Linux 由 user systemd 托管，浏览器“个人设置”负责 client key、在线实例、OpenCode 启停和本地工作区注册；当前仓库没有已经生成的正式签名分发目录，本机页面也没有已认证客户端实例。

### How

- ClickHouse helper Bash 语法、dev 脚本全量静态校验和 `git diff --check` 通过；双 MyBatis 工厂定向测试 1/1 通过。真实启动完成 schema migration，14 个分析表/物化视图可见，运营分析 `overview/filter-options/funnel/hourly-heatmap/timeseries` 五个浏览器请求均返回 200。
- 本地客户端 reactor 116 项通过、1 项按环境跳过，其中客户端模块 7 项通过、1 项跳过；制品签名、SHA-256、darwin-arm64 安装器和伪造清单拒绝测试通过。正式分发仍需仓库外签名私钥、真实上游归档以及 Apple Silicon/麒麟 ARM 实机验收。
- 全数据面首次稳定性检查遇到 Docker Desktop 4.20.1 `dockerd` 空指针 panic，导致所有容器和后端退出；恢复 Docker 后停止本次启动的 Memory 数据面，只保留核心 PostgreSQL/Redis/XXL MySQL 与 ClickHouse，再次启动 backend、manager、frontend 成功并持续通过 readiness。该问题不是应用 OOM，系统日志明确记录 dockerd panic。

### Result

- dev 当前运行于 `http://127.0.0.1:3000`，后端 readiness 为 `UP`，ClickHouse 26.3.17.56 及运营分析查询链路可用；Memory 本轮为规避旧 Docker daemon panic 已停止，数据卷保留。
- 本地客户端能力和打包链路存在，但当前形态不是带窗口的桌面 GUI，也没有可直接下发的正式签名包或已连接实例；不能把自动化安装器测试表述为本地客户端真实端到端交付完成。

## 2026-08-15 - 修复记忆证据原始对话入口与结构化消息展示

### Why

- 记忆证据中的 `/s/{sessionId}` 与后续协作分享 `/s/{shareId}` 共用路由后，普通 `ses_` 会话 ID 被分享入口接管并跳回工作台，导致“打开原始对话”看不到对应原文；链接也在当前记忆页内跳转。
- 用户提供的真实会话有 7 条消息，全部包含结构化 parts，其中 5 条 assistant 消息的 `content` 为空；旧只读页只渲染 `content`，恢复路由后仍会漏掉过程正文。

### What

- 记忆证据链接增加 `target="_blank"` 与 `noopener noreferrer`；`/s/{id}` 按强类型前缀分流，`ses_` 直接进入所属人只读 transcript，`shr_` 继续走既有分享访问解析和完整分享工作台，分享所属人重定向语义不变。
- 只读 transcript 复用 `messagesFromSessionMessages` 和 OpenCode 时间线渲染 `content` 与结构化 parts，不再生成空白 assistant 卡片；非所属人仍由既有 Session/messages 鉴权阻止读取。
- 更新 agent-web README/PACKAGE、前端规范和记忆 QA 文档，并补充路由、链接属性、结构化 parts、分享隔离与真实记忆弹窗回归。

### How

- 定向 Vitest 2 文件 10/10、agent-web typecheck、记忆 Chromium 回归和分享隔离 Chromium 回归均通过；agent-web 用户手册与 production build 通过，仅保留既有大 chunk 提示。
- 使用本地真实账号与用户给出的 `ses_08771dbe04a24c66b989c71a2cee2e24` 验证：关联 Run 为 `SUCCEEDED`，Session/messages 接口均返回 200，只读页保持精确 URL、标题与时间线加载成功，未出现分享无效页或空消息页。
- 本地服务依赖恢复后 backend health/readiness 为 `UP`、前端 `http://127.0.0.1:3000` 返回 200；提交前回顾全部 `.agents/session-log*.md` 近期条目并检查冲突标记。

### Result

- 记忆中心点击“打开原始对话”会保留当前页面并在新标签页展示所属人的完整只读时间线；`shr_` 分享链接继续保持原权限和工作台行为。
- 纯前端路由、渲染与文档变更；未修改 API、RunEvent、数据库/Flyway、部署变量、`.env*`、generated SDK 或 OpenCode 源码，也未新建分支。

## 2026-08-17 - 记忆中心按用户灰度开放

### Why

- 用户要求记忆功能默认不对全员展示，只允许超级管理员指定的少量用户看到入口并打开页面；未授权用户即使输入 `/memories` 也不能进入。

### What

- 复用既有 `qa_memory_whitelist`、`GET /memory/v1/availability` 和超级管理员白名单管理接口作为唯一事实源，没有新增第二套灰度配置。
- 前端新增按登录 Token 隔离的记忆访问状态，工作台仅向已授权用户显示“记忆”入口；`/memories` 在挂载页面前强制刷新服务端授权，查询失败、未授权或授权被撤销时均返回工作台。
- 系统管理将“用户白名单”统一调整为“灰度用户”，明确名单同时控制页面入口、记忆学习和检索；同步前后端 README、HTTP API、部署 QA 文档和用户手册，并更新真实 Chromium 管理页截图。

### How

- 前端灰度 Store、记忆中心和管理面板定向 Vitest 16/16，agent-web typecheck、用户手册与生产 build 均通过；Chromium 覆盖已授权访问、未授权隐藏入口/直达拦截和超级管理员增删灰度用户 3/3 通过。
- JDK 25 下 `QaMemoryApplicationServiceTest` 12/12、`QaMemoryControllerTest` 5/5 通过，受影响 22 模块 reactor 为 `BUILD SUCCESS`；`git diff --check` 通过。
- 首次按 `.env.test`、`test` profile 启动时，外部 PostgreSQL 与 Redis 均返回 `NoRouteToHostException`；按用户要求改用项目本地 PostgreSQL 16 和 Redis 7.4.9，通过 `.tmp/dev-services` 下 `0600` 的一次性 dotenv 覆盖连接并关闭无需联调的远端 XXL Admin，未修改 `.env*`。
- 本地 PostgreSQL 完成全部 107 条 Flyway 历史校验并补齐 70 条迁移；真实 `health/readiness` 均为 `UP`、前端 3000 返回 200、登录 CORS 正常。超级管理员真实 API 增删 `usr_test_dev` 灰度名单并完成清理；本次运行未启动 Memory 数据面且总开关为 false，因此真实 availability 保持 false，授权为 true 的页面路径由 Chromium 自动化覆盖。

### Result

- 页面可见性与后端学习/检索共用同一用户灰度名单，超级管理员仍是唯一名单维护者；授权结果按用户隔离并在页面聚焦时复核，无法确认时失败关闭。
- 仅复用既有 HTTP API 和数据库结构；未变更 API/RunEvent/DTO、数据库/Flyway、部署拓扑、generated SDK 或 OpenCode 源码。平台当前以本地 PostgreSQL/Redis 运行在 `http://127.0.0.1:3000`，Memory 数据面仍保持停止，避免为页面灰度验证额外启动大模型容器。

## 2026-08-17 - dev 依赖迁移到 192.168.8.100

### Why

- 用户要求本机后续只启动前端和后端，PostgreSQL、Redis、XXL MySQL、ClickHouse 与 OpenCode 全部使用局域网服务器 `192.168.8.100`，同时保留 TUN 公网代理并让该网段直连。

### What

- 在 Ubuntu 服务器补齐 Docker/Compose、JDK 21、Maven、Go、Node/pnpm、OpenCode、Git/SSH 和常用诊断工具；复制本机 SSH 身份与 known_hosts，验证 Gitee/GitHub SSH 和仓库 fetch 可用。服务器 dev worktree 保持可向 `origin/dev` 快进。
- 将本机 PostgreSQL 16 的 `testagent` 数据恢复到服务器隔离库 `testagent_dev`，保留服务器原库和同步前数据卷；服务器持续运行 PostgreSQL、Redis、XXL MySQL、ClickHouse 26.3.17.56，以及 OpenCode 所需的后台 Java 控制面和 manager，远端前端保持停止。OpenCode 端口池按用户初始化按需拉起，不预置无主进程。
- 本机忽略文件 `.env.test` 已改为访问服务器依赖并关闭本机 OpenCode/manager；本机旧 `test-agent-*` 数据容器和 memory mock 已停止，只保留 backend/frontend。Clash Verge/Mihomo 的持久配置和运行配置增加 `192.168.8.0/24` DIRECT 与 TUN route exclusion，TUN 继续启用。

### How

- PostgreSQL 恢复后逐条核对 108 条成功 Flyway history、0 条失败；本机端到端校验得到 PostgreSQL `testagent_dev:108`、Redis `PONG`、ClickHouse `testagent_analytics / 26.3.17.56`、XXL MySQL 端口可达。
- 服务器 PostgreSQL/Redis/XXL MySQL/ClickHouse 容器健康，远端控制后端 readiness 为 `UP`，manager WebSocket 已连接，服务器 3000 端口停止；本机后端 readiness 为 `UP`、前端 3000 返回 200，进程清单仅有 `test-agent-backend` 和 `test-agent-frontend`。
- 当前工作区另有未提交的受保护 Agent 开发代码，其中 `ProtectedAgentFileGrantService` 的双构造器未指定 Spring 注入构造器；为避免覆盖在途改动，本轮运行使用当前已提交 `a7316a344` 的干净后端 JAR 验证环境，未修改该代码。

### Result

- dev 运行拓扑已切换为“本机 frontend/backend → `192.168.8.100` 全部数据与 OpenCode 依赖”，本机与服务器均已实际启动/探测；服务器数据卷、同步 dump 和同步前备份保留。
- 本轮只修改忽略的 `.env.test` 和仓库外的 Clash/服务器环境，未修改 API、RunEvent、数据库结构/Flyway、generated SDK 或 OpenCode 源码。当前 `origin/dev` 仍落后本机已提交 dev 63 个提交，服务器 worktree 落后本机 2 个已提交变更；相关提交推到 origin 后服务器可正常 fast-forward pull。

## 2026-08-17 - 配置通知改为用户易懂文案

### Why

- 通知中心把配置更新结果表述为“Agent 配置已生效”和“已应用到 TestAgent 进程”，暴露了 Agent、dispose、rollout 和进程等内部概念，普通用户难以理解；已有通知又把标题和正文快照保存在数据库中，只改后端无法改善历史记录。

### What

- 后端四种配置通知统一改为“正在更新、更新成功、更新失败、本次更新已结束”的用户文案，失败提示改为重启智能体。
- 前端按受控通知类型统一展示同一套标题、正文、状态和动作名称，因此数据库中的旧通知也会立即显示新文案；未知通知与会话分享继续使用原始安全展示逻辑。
- 同步通知模块 README、agent-web README 和用户手册，并补齐后端四态文案及前端旧记录兼容回归。

### How

- JDK 25 下 `UserNotificationApplicationServiceTest` 9/9 通过；前端全量 Vitest 130 个文件、2008 passed / 1 skipped，agent-web typecheck 通过。
- `restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 完成 26 模块后端构建和本地服务重启；最终后端 health/readiness 均为 `UP`，前端 3000 返回 200，登录 CORS 预检正常。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录并保留工作区中本地客户端、受保护 Agent 和客户端下载入口等并行未提交改动；只暂存本次通知文案相关文件。

### Result

- 新旧配置通知都会使用普通用户可理解的中文表达，不修改通知类型、受控动作、已读规则或实时刷新机制。
- 本次不变更 HTTP API、RunEvent、DTO、数据库/Flyway、性能、安全、部署拓扑、`.env*`、generated SDK 或 OpenCode 源码；未新建分支。

## 2026-08-17 - 本地客户端托盘、受保护 Agent 与页面下载入口

### Why

- 用户要求在 Apple Silicon macOS 和麒麟 ARM 本地客户端补齐低开销托盘，并通过服务器运行受保护 Agent/Skill、受限访问用户授权的本地目录；随后指定在右上角头像菜单的 OpenCode 实例与重启入口之间直接提供客户端下载。
- 受保护提示词和 Skill 正文一旦完整下载到用户机器便无法防读防改，因此需要保留在服务器执行，只把不可变修订句柄和摘要提供给网页，把本地目录访问收敛到已有 WSS 文件 RPC。

### What

- 本地客户端复用 Web 端宠物资产实现 SystemTray，展示在线状态，并提供打开网页、重连、查看/下载受限客户端日志、会话进度和正常退出；launchd/systemd 只在异常退出时恢复。托盘只读取既有连接快照，状态未变化不重绘，不增加健康探测。
- Hub 已发布 Agent 以 `protected:{revisionId}` 进入本地工作区 Agent 目录；运行时固定 Agent/Skill revision 与 SHA-256，在服务器 OpenCode 隔离目录执行，禁用原生本地文件工具，并通过单 Run、单用户、单 Workspace、单客户端 generation 的短期 MCP grant 调用本地 WSS 文件工具。Agent/Skill 正文不写入客户端配置目录。
- 右上角头像菜单在 OpenCode 实例列表下方增加“下载本地客户端”；生产复用既有 Nginx `/downloads/local-opencode-client/`，dev server 只读提供忽略的签名制品目录，非法/隐藏路径直接 404，JRE/OpenCode 大制品不进入前端 bundle。
- 同步 runtime、API、local-client、workspace 模块 README/PACKAGE，以及 HTTP API、RunEvent、架构、部署、安全、前端 README 和用户手册；没有数据库/Flyway、generated SDK 或 OpenCode 源码修改。

### How

- 后端受影响链路定向测试共 34 项通过；本地客户端单元测试 11 项通过、1 项按桌面条件跳过；正式签名分发包完成清单签名、五项制品 SHA-256、JAR 托盘资源和安装器校验，版本为 `0.1.0-dev-e2e`。
- `FigmaShell.test.ts` 60/60、agent-web typecheck 和 development build 通过；dev 下载路由实际返回 200，HTTP 下载脚本与分发源 SHA-256 同为 `470a11a9fcc9f5bc8210cfbf81723308239ddbad00f0fc7280aa382a0ab400f2`，隐藏路径返回 404。
- 按最新 `.env.test` / `test` profile 使用 JDK 25 完成 26 模块后端构建并重启；backend readiness 与 frontend 3000 均通过。真实 Chromium 登录后确认入口位于指定位置，点击下载得到 `test-agent-local-client-install.sh`，下载文件摘要一致。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，保留当前 dev 已提交的记忆灰度、局域网依赖迁移和通知文案成果，未覆盖 release 工作树中尚未提交的内部模型观测改动。

### Result

- macOS/Kylin 共用托盘、受保护远程 Agent 文件链路和网页下载入口已实现并通过自动化、构建、签名制品、真实启动与 macOS Chromium 下载验证；页面与客户端状态采集没有新增后台轮询压力。
- 当前签名使用本轮临时 dev 私钥，分发目录被 Git 忽略，只用于本机自测；上线仍需换企业私钥重新打包，并在真实 Apple Silicon 与 ARM64 glibc 麒麟机完成安装、WSS、聊天/文件写入和全部托盘动作验收。真实麒麟 ARM 未验证前只能标记为部分验证。

## 2026-08-17 - 拉取 release 并重打 TCDS 完整类型名称企业包

### Why

- 用户要求拉取远程 `release` 最新代码并重新打企业包；远程本轮新增 TCDS 案例类型按接口完整名称选择与提交，替代固定短名称映射。

### What

- 制品源码提交固定为 `95a7a93859550fdfbf309e14a5cdea1220cf282e`；内层发布 ZIP SHA-256 为 `9bdc77679cde348cb3b0fb220b677be062f8e193cceabe471da6e0c722c8c04d`，外层完整包为 `cd0ab69614d68347c9c30d7915f010d0c1955b9b14157cb38eeb773612d85cf0`。
- 后端 app JAR 为 `ec251edbeec24924f522fa85154b0fb8f7199d152ec95fbb63f6f1db4ebb95cb`，persistence JAR 为 `fd79cb19c7599da0beb6462e15a9cc993cf35924a95827775340bd4d4af90871`，XXL integration JAR 为 `66e5162e84d8dfc253aaeb4da7a622a9455197b6768c05be70f7a281eb62a8c0`，前端归档为 `f7dfcd08b2d92954928d7cf5f5bf23141bed874f6fbad44b4c266fca9d3de18e`。
- TCDS 案例类型现在直接使用 `getTaskTypes.data.subItemTypes[].name` 的完整名称，例如 `准入测试任务,功能测试任务`；继续校验空值、名称含逗号、名称或值重复等异常响应。

### How

- JDK 25 下后端 TCDS 定向测试 13/13、前端 TCDS 定向 Vitest 16/16 通过；正式构建显式关闭 workflow/LobeHub，worker 与 toolbox 继续按既有指纹 `reuse`。
- 外层 ZIP 完整性、内嵌内层逐字节一致、三份节点归档 SHA、内层 `--validate-only`、前端独立需求导入入口、两台后端 TCDS 精确地址和 worker 大制品缺失门禁均通过。
- 对比上一包源码 `c8b6762fafcc61734d31826a377abdd29c120ccc` 没有 migration 或 `deploy/internal` 变化；最终 JAR 内受保护 Flyway 资源与已验证源码一致。提交前已回顾全部 `.agents/session-log*.md` 近期记录。

### Result

- 固定名外层 ZIP 与 SHA 文件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/`，并再次通过 `sha256sum -c` 与 `unzip -t`。
- 部署顺序仍为 `.4 -> .114 -> .2`；不加载或重启 manager/worker，不替换 `.4` Qwen 灰度或 `.114` 现网 `opencode-models.json`，数据库预期 Flyway history 零新增。
- 真实企业 TCDS 创建图案例时的完整 `taskType` 请求值与脱敏日志仍需现场验收。

## 2026-08-17 - 恢复小地球应用与版本自由选择

### Why

- 小地球把“当前月份前后 3 个月”从快捷候选误做成硬校验，目录外版本无法查询；原生 `datalist` 还会按当前值过滤，导致部分应用看起来只有一个版本或无法切换应用。
- 新页面又要求应用精确命中 TCDS 返回目录，并把目录外父页面应用回退为第一项；旧页面会保留父页面值，后端条目接口本身也只要求非空应用和版本。

### What

- 版本改为轻量可输入下拉框，前后 3 个月仅作快捷建议，任意非空版本都可查询；应用切换时不重置版本。
- 应用改为轻量可搜索下拉框，TCDS 应用目录只作建议；任意非空名称或简称、父页面传入的目录外值以及目录为空时的手工输入都可继续查询，最终结果由 TCDS 决定。
- 点击或聚焦应用框始终先展开全部建议，只有继续键入时才过滤；同步前后端模块说明、HTTP API 和安全复测记录，纠正“授权应用”这一未经接口契约确认的表述。

### How

- 定向运行需求导入页与父工作台小地球测试，2 个文件 30 项全部通过；agent-web 类型检查包含在生产构建中并通过，独立入口成功生成。
- 在当前 release 工作树启动 3000 前端，用 Playwright 模拟应用目录后做真实 UI 操作：目录外父页面值保留、7 个版本建议可展开、目录外应用仍可展开全部建议，手工输入 `CUSTOM-APP` 和 `2099-special-version` 均越过前端限制进入查询。
- 使用 `.env.test`、`test` profile 和 JDK 25 执行完整重启；22 模块后端打包成功，但 PostgreSQL 返回 `NoRouteToHost`，8080 readiness 超时。未替换环境文件，3000 当前继续运行本次 release 源码。

### Result

- 小地球不再按月份范围或应用目录限制输入，应用和版本均可自由选择/输入；空值仍在前端拒绝，TCDS token、文档地址和工作区写入安全边界不变。
- 本次不改变 HTTP/WebSocket 请求结构、RunEvent、数据库、Flyway、部署配置、公共 Agent、Workspace ID 或 Git diff/提交推送链路。真实 TCDS 条目结果和完整导入仍需在 PostgreSQL 与企业网络恢复后复测。

## 2026-08-17 - 重打小地球自由选择企业包

### Why

- 用户要求再次重打企业包；当前本地 `release` 已包含提交 `def19cea5` 的小地球应用与版本自由选择修复，该提交尚未进入上一份 `95a7a9385` 制品。

### What

- 制品源码提交固定为 `def19cea51096a2ab4109b04b15972094f5af310`；内层发布 ZIP SHA-256 为 `804fb683e3c8971727de411442d87344484ef6edbc315a76ee207a45f1f09406`，外层完整包为 `b04e4892d3220f28ebc575c42d8f8e099c79770715bbfbe7f3124899187256af`。
- 后端 app JAR 为 `ba9a1eba7f5c27528923d7d2a9a9b1f7d7c15904905b62ad4e5e8dbd495074ae`，persistence JAR 为 `3bae650c8fa7c07c39f8a4878b35a48d23a96d9a161c353125be04050c6b895e`，XXL integration JAR 为 `4bf2c59f8c13e2662e30751361531fded0b9cf6fc5c4ce8ba7c6875b7256e481`，前端归档为 `95780f16b958632bbce10e5e0642670cbc8e2e39b4e83a33c9a0039b982e30e7`。
- 相比上一包新增小地球应用/版本可自由输入与搜索：月份范围和 TCDS 应用目录只作为候选建议，不再作为前端硬限制。

### How

- JDK 25 下后端 `RequirementImportApplicationServiceTest` 7/7、前端需求导入与文件树定向 Vitest 30/30 通过；正式生产构建成功并生成独立 `workspace-requirement-import` 入口。
- 显式关闭 workflow/LobeHub；worker runtime 指纹 `50f56c54991bd7d5b3926fcb8442655b3ca1371a56ec6165a9ec19626f672fb1`、toolbox 指纹 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040` 均继续 `reuse`。
- 外层 ZIP、内嵌内层逐字节一致、三份节点包 SHA、内层 `--validate-only`、最终 JAR 受保护 Flyway 资源、两台 TCDS 精确地址和 worker 大制品缺失门禁均通过；对比上一包源码 `95a7a93859550fdfbf309e14a5cdea1220cf282e` 没有 migration 或 `deploy/internal` 变化。
- 提交前已回顾全部 `.agents/session-log*.md` 近期记录，未发现会覆盖或丢弃其他提交者成果的冲突。

### Result

- 固定名外层 ZIP 与 SHA 文件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/`，并再次通过 `sha256sum -c`、`unzip -t` 和内外层逐字节比较。
- 部署顺序仍为 `.4 -> .114 -> .2`；不加载或重启 manager/worker，不替换 `.4` Qwen 灰度或 `.114` 现网 `opencode-models.json`，数据库预期 Flyway history 零新增。
- 真实企业 TCDS 的目录外应用、目录外版本查询及完整文档导入仍需现场验收。

## 2026-08-17 - 优化小地球组合选择与子条目筛选

### Why

- 版本和应用输入框复用已提交值作为编辑值，用户开始键入时会追加在原文字后，只能先手工删除；应用建议也容易因当前值被过滤，产生“应用被限制”的误解。
- 子条目原筛选只支持单段包含匹配，缺少仅看已选和快速复原，在多父条目、大量子项场景下操作成本较高。

### What

- 版本输入草稿与已提交版本分离；版本、应用聚焦时自动全选原值，既可直接键入替换，也可点击下拉项，Esc 恢复已提交值。版本下拉列出当前值及当前月份前后 3 个月，应用展开时列出 TCDS 返回的全部项目，任意非空输入仍可查询。
- 子条目筛选支持父/子编号和名称的空格分隔多关键词匹配；父条目命中时保留该父项下全部符合项，子项命中时只展示匹配子项，并新增“仅看已选”和“清空筛选”，筛选切换不清空选择。
- 同步 agent-web README 与安全复测文档，并把需求导入页定向测试增至 13 项。

### How

- 定向 Vitest 覆盖需求导入页和父工作台小地球，2 个文件 31/31 通过；`corepack pnpm build` 完成用户手册、`vue-tsc` 与生产构建，`tools/verify-ai-docs.sh` 通过。
- 在当前 `release` 工作树以 3001 端口启动前端，用 Playwright 模拟 TCDS 应用和文件 WebSocket：验证版本/应用无需删除即可直接替换、应用完整列表可直接切换、多关键词/仅看已选/清空筛选保留选择；760px 宽度下页面、筛选区、条目区和工具栏均无横向溢出。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；工作树中另一批内部模型观测改动保持未暂存、未覆盖。

### Result

- 小地球的版本和应用现在是可输入、可直接选择的组合框，已有值不再妨碍修改；子条目筛选在多人、多父条目和大量子项场景下更容易定位及复核选择。
- 本次只改前端交互、定向测试和说明文档，不变更 HTTP/WebSocket 契约、RunEvent、数据库、Flyway、安全边界、公共 Agent、Workspace ID 或 Git diff/提交推送链路；真实企业 TCDS 返回数据仍需现场联调。

## 2026-08-17 - 增加模型 Output TPS 并修复可观测筛选

### Why

- 内部模型可观测页缺少每秒输出 Token 数及公开性能参照；自定义日期在只选完开始时间时会把中间态当成完整区间；UCID 只在前端当前页过滤，导致过滤后的分页总量不变、目标用户在其它页时误显示为空。

### What

- 复用 PostgreSQL `internal_model_call_records` 既有 `first_token_ms/last_token_ms/output_token_count`，新增 Output TPS 平均值与五数概括 API；单条按 `(output_token_count-1)*1000/(last_token_ms-first_token_ms)` 计算后再聚合，不新增表、列、migration、服务或 ClickHouse 依赖。
- 页面增加单次 TPS、Overview 平均/P50、按厂商箱线图，以及 2026-08-17 核验的 Artificial Analysis Qwen3.6 27B 与 DeepSeek V4 Flash 公开实测/同类中位数；明确公开 Token 归一化、硬件、量化和并发差异，只作方向性参照。
- 自定义时间仅在两个端点完整、可解析且开始早于结束时应用，刷新时保留固定自定义窗口；UCID 作为可选参数进入 MyBatis 列表与 count 的共享条件，按忽略大小写的字面子串匹配。
- 同步 domain/runtime/persistence/API/backend-api/agent-web README、HTTP API、指标口径和本地验证文档；未修改 `.env*`、RunEvent、数据库结构、generated SDK 或 OpenCode 源码。

### How

- 后端定向测试通过：Controller 5/5，H2/MyBatis 9/9，真实 PostgreSQL/Testcontainers 5/5；JDK 25 下 20 模块编译及 22 模块跳过测试打包均成功。
- 前端可观测面板与 backend-api 定向 Vitest 121/121，agent-web `vue-tsc` 通过；一次误触发的全量 Vitest 为 1989 passed / 1 skipped / 3 failed，其中两项本任务测试随后修正转绿，另一项为未改动的 `MarkdownView.test.ts` Canvas/Markdown 环境基线失败。
- 使用 release 工作树代码和项目根 `.env.test`/`test` profile 完整重启；backend health/readiness 为 UP，前端 3000 返回 200，登录 CORS 正常。`.env.test` 禁用本地 manager，启动脚本按配置跳过该进程。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目；并行存在的需求导入、安全文档和 Playwright 快照改动不纳入本次暂存或提交。

### Result

- 可观测页面现可按全量 PG 明细准确查看 Output TPS 并与公开参考做有边界说明的对照；自定义日期首击不再跳掉，用户筛选后的明细与总页数保持一致。
- 功能位于现有 release 部署拓扑内，API 为加法兼容变更；无新部署节点、必需配置、数据库迁移或安全数据暴露。release 修复尚未同步到当前有并行未提交改动的 dev 工作树，后续应按提交哈希安全 cherry-pick。

## 2026-08-17 - 需求导入完成后定向刷新文件树

### Why

- 小地球导入成功后原链路立即关闭弹窗并刷新文件树，不能保证新目录已加载和可见；若恢复整棵 `spec` 的历史展开状态，又会产生大量文件 RPC 并加重页面卡顿。

### What

- `workspace.requirement-import` 结果新增加法字段 `workspaceRelativeDisplayPaths`，只返回后端按可信 TCDS 父条目规范化得到的 `spec/{父条目}` 工作区相对展示路径，不返回物理根路径或文档地址。
- `SUCCEEDED/PARTIAL` 后 iframe 保持生成蒙版，父工作台先只刷新根节点，再复用 `expandPathToFile(path, true)` 逐层加载并展开 `spec` 和本批次父条目；不恢复其它历史展开目录，不递归展开父条目下的子树。
- 父工作台完成后以精确同源消息确认：`SUCCEEDED` 再关闭弹窗，`PARTIAL` 解除蒙版并保留失败明细；刷新失败保留弹窗并提示用户手动刷新。父端再次校验路径必须是两段 `spec/...` 相对目录。
- 同步 workspace/API/shared-types/backend-api/agent-web README、HTTP API、事件流和安全复测说明；未修改 RunEvent、数据库、Flyway、部署变量、公共 Agent、Workspace ID 或 Git 提交推送链路。

### How

- JDK 25 下运行 `RequirementImportApplicationServiceTest` 与 `WorkspaceFileWebSocketHandlerTest`，共 45/45 通过；22 模块跳过测试打包成功。
- 需求导入页与文件树组件定向 Vitest 2 个文件 33/33 通过；全 workspace `corepack pnpm typecheck` 和 agent-web production build 通过，仅保留既有大 chunk 提示；`tools/verify-ai-docs.sh`、`git diff --check` 通过。
- 使用当前 `release` 工作树代码和项目根 `.env.test`/`test` profile 完整重启；backend health/readiness 为 UP，前端 3000 返回 200，登录 CORS 正常。`.env.test` 禁用本地 manager，启动脚本按配置跳过该进程。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目，确认没有覆盖模型 TPS、TCDS 完整任务类型、体验工作区或公共 Agent/Git 链路成果。

### Result

- 导入结果写入完成与文件树可见完成现在是两个明确阶段；页面只请求根目录、`spec` 和本次父条目目录，既自动定位本批次内容，又不会递归展开整个 `spec`。
- WebSocket 返回字段为向后兼容的加法变化；旧前端可忽略，新前端在字段缺失时仍可刷新根节点。真实企业 TCDS 文档导入仍需在企业网络现场复测。

## 2026-08-17 - 合并远程 release 并重打组合功能企业包

### Why

- 用户要求拉取远程 `release` 后重新打包；远程新增案例维护 sticky 表头选择框重叠修复，本地同时已有小地球组合筛选、模型 Output TPS/UCID 查询和导入后定向刷新文件树三个尚未进入上一包的提交。

### What

- 将远程 `326c76501246b8c0322e5abd8669a85613cb9bad` 无冲突合入本地 `release`，制品源码提交固定为 `cd40311d9f7d983093bfaa2b0623ee73cc726095`。
- 内层发布 ZIP SHA-256 为 `dfa865684ee619060a6a5297e4e77c14b417848eea1ac8368e339ddc25008ab7`，外层完整包为 `c1a352615505478a221694ebd8c2fc15b85f6e96fd9d148b7df8dab4f10710ad`。
- 后端 app JAR 为 `6912079dc337caa94ef453b3a635f314f0ac06f5034fb0b1797bee56678e3463`，persistence JAR 为 `d7ac106c040b69151e630a92eba2c30b484f855feeafa0e24fe79dd1594bce75`，XXL integration JAR 为 `2e26aa6fd84c5aba4cbd381143a60fecb3a23fc397474b4aa87c43932e1db923`，前端归档为 `5d550ab5ab6d69770225d70e5d6a14902026362d4c50ef9c3509b29e19c6fee3`。

### How

- 合并后后端组合回归 64/64 通过，其中真实 PostgreSQL/Testcontainers 5/5；前端需求导入、文件树、模型可观测、案例维护与 backend-api 共 170/170 通过。
- 正式构建完成 agent-web 类型检查、用户手册和生产包；显式关闭 workflow/LobeHub，worker runtime 与 toolbox 继续按既有指纹 `reuse`。
- 外层 ZIP、内嵌内层逐字节一致、三份节点包 SHA、内层 `--validate-only`、独立需求导入入口、两台 TCDS 精确地址、worker 大制品缺失门禁及最终 JAR 全部受保护 Flyway 资源校验通过。
- 对比上一包源码没有 migration、`deploy/internal`、依赖锁或 worker/runtime 变化；提交前再次回顾全部 `.agents/session-log*.md` 近期记录。

### Result

- 固定名外层 ZIP 与 SHA 文件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/`，并通过最终 `sha256sum -c`、`unzip -t` 和内外层逐字节比较。
- 部署顺序仍为 `.4 -> .114 -> .2`；不加载或重启 manager/worker，不替换 `.4` Qwen 灰度或 `.114` 现网 `opencode-models.json`，数据库预期 Flyway history 零新增。
- 案例表头滚动视觉、小地球真实 TCDS 导入、定向文件树展开及生产可观测 TPS/UCID 数据仍需企业现场验收。

## 2026-08-17 - 修复可观测日期弹层并提供离线对标说明

### Why

- 自定义时间的日期面板默认 Teleport 到 `body`，父级 Popover 会把第一次日期点击误判为外部点击而关闭；Artificial Analysis 方法和数据源在企业内网无法访问；调用结果分布和供应商请求量对比被压在半宽列中且高度不足。

### What

- 保留现有时间筛选状态和完整区间校验，只把嵌套 `el-date-picker` 设置为不 Teleport，并阻止内部点击冒泡；父级 Popover 仍保持原生点击外部关闭行为。
- 方法说明和每个模型的数据源入口改为页面内离线弹窗，固化核验日期、实测值、同类中位数、方法边界和外网可选追溯链接。
- 调用结果分布与供应商请求量对比改为各自独占整行，桌面端图表高 340px、窄屏 300px；同步 agent-web README 和本地验收文档。

### How

- 可观测组件定向 Vitest 3/3、agent-web `vue-tsc`、用户手册与 Chrome 108 目标生产构建全部通过。
- 在 release 前端 3001 端口用真实 Chromium 和受控 API 响应点击验证：只选第一个日期后父弹层与日期面板继续显示且明细请求数不变；方法说明和数据源离线弹窗均打开；两张业务图实测均为 `1218 x 340px`，控制台无错误。
- 按 `.env.test`/`test` profile 执行完整重启，22 模块跳过测试打包成功；随后 release 后端因 Redis 连接被关闭而启动失败，且 8080 readiness 来自并行启动的主工作区 JAR，未把该结果记为 release 后端启动成功。release Vite 仍在 3001 独立运行供页面验收。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；未修改 `.env*`、HTTP API、RunEvent、数据库、Flyway、generated SDK 或 OpenCode 源码。

### Result

- 自定义日期首击不再关闭；公开方法和数据无需企业外网即可查看；两张业务图具备整行可读空间。
- 本次为现有 release 拓扑内的前端交互修复，向后兼容；尚未同步到并行开发中的 dev 工作树，也未重新生成企业发布包。
## 2026-08-17 - 本地客户端改为双击安装并展示 OpenCode 健康度

### Why

- 用户不应在网页下载 Shell 脚本后再进入终端安装；Apple Silicon macOS 和 ARM 麒麟都需要可直接双击的原生安装包。
- 客户端完成注册后，原下载位置应该变为本地 OpenCode 健康度，而不是继续诱导重复下载。

### What

- 分发程序新增 `TestAgent-Local-Client-macOS-arm64.pkg` 和 `TestAgent-Local-Client-Kylin-arm64.deb`；两包都内置 JRE、OpenCode、客户端 JAR 和宠物图标，macOS 安装 LaunchAgent，麒麟安装 systemd user unit 与桌面入口。原 `install.sh` 仅作 CLI 兜底。
- 原生包首次启动使用 Swing 向导填写平台地址、网页地址和客户端密钥，密钥只写入用户私有 `0600` 文件；麒麟尚无图形会话时正常退出，避免 systemd 反复重启。
- 页面的单一 `/installer` 入口按 User-Agent 返回 macOS PKG 或麒麟 DEB；平台一旦存在 `LOCAL_CLIENT` 注册实例，原位置改为健康、部分健康、异常或离线状态。状态复用现有 endpoint 数据，没有新增轮询。
- Nginx、增量/完整发布门禁、配置示例、前后端 README、用户手册、本地客户端部署与安全文档已同步。

### How

- JDK 25 下本地客户端 reactor 共 122 项测试通过、1 项桌面条件跳过，其中客户端模块 13 项通过；首次配置覆盖私有文件权限与 HTTP 安全拒绝。
- `FigmaShell.test.ts` 61/61、agent-web typecheck、用户手册和生产构建通过；安装包测试、真实 Nginx 语法流程、增量组件与双后台部署夹具均通过。
- 最新 dev 制品版本为 `0.1.0-dev-native`，PKG SHA-256 为 `484d4878801bf2e9c03783bc549ec6cb091beaca30a9789561da0db4cce1ee6f`，DEB 为 `1bd03d9ef1d67ab41152218d20427cdee4562a519a223d90a054398884433bb9`；实测 Vite 路由返回正确 302、MIME 和下载文件名。
- 按最新 `.env.test`/`test` 配置完成 26 模块后端构建并启动 backend/frontend；随后 `192.168.8.100` PostgreSQL/MySQL 网段返回 `NoRouteToHost`，readiness 为 DOWN，页面登录因此返回服务器内部错误。

### Result

- macOS 和麒麟的下载、原生包内容、自启配置与页面健康度切换逻辑已实现并通过自动化和本机制品校验；不变更 HTTP API、RunEvent、数据库/Flyway、generated SDK 或 OpenCode 只读源码。
- 当前 macOS dev PKG 因本机没有 Developer ID 证书而未签名/公证；打包脚本已支持应用签名、安装器签名与 notary profile，正式交付仍需注入企业凭据。真实 ARM 麒麟双击安装、托盘、WSS 和聊天/文件端到端尚未验证；页面真实健康状态交互待数据库网络恢复后补验。

## 2026-08-17 - 恢复开发用户服务端 OpenCode 实例

### Why

- 默认开发用户仍绑定已退出的 `kakadeMacBook-Pro.local:4098` 历史进程，平台按稳定绑定失败关闭，无法自动降级或迁移到在线服务器。
- `dev-192-168-8-100` 的 Java 与 manager 虽在线，但公共配置仓库误停留在只有 README 的 `main` 分支，缺少 `opencode/opencode.jsonc`，无法承载新的用户进程。

### What

- 通过现有公共配置管理 API 将服务器公共配置仓库初始化到包含 OpenCode 配置的 `master` 分支，状态恢复为 `READY`。
- 确认历史 PID、4098 监听均不存在且没有 Run 快照引用后，先将两张进程表完整备份到本机 `.tmp`，再按运维文档在单事务内精确删除该用户唯一的废弃 binding/process。
- 通过现有用户进程初始化 API 重新分配，生成进程 `ocp_3d436d6dc9de45a995b4e416efc666a9`，固定到 `dev-192-168-8-100:4096`。

### How

- 从浏览器实际使用的本地 Java 调用状态与实例列表 API，均返回 `READY/RUNNING`、`online=true`、`healthy=true`，路由目标为 `192.168.8.100:4096`。
- 服务器 manager state、数据库与 `ps/ss` 一致记录 PID `936040` 和权威启动时间；`/global/health`、`/global/config` 均返回 HTTP 200，后续 manager health 持续为 `HEALTHY`。
- 收尾前回顾全部 `.agents/session-log*.md` 近期记录；保留工作区中新出现的 PowerPoint 临时文件，不暂存、不删除、不覆盖。

### Result

- 服务端 OpenCode 已恢复可用，前端通过本地 Java 的跨节点路由可准确访问服务器实例，不会回退到旧 Mac 进程。
- 本次仅执行开发环境公共配置、当前快照数据与运行进程修复；没有修改 HTTP API、事件、数据库结构/Flyway、代码、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-17 - 打包 `.134 + .160` 运营与记忆中间件离线制品

### Why

- dev 的运营分析、原生本地客户端和通用记忆准备进入企业试部署；新增的 `.134/.160` 是 PostgreSQL 克隆机，必须保留现有 `5432` 和残留数据，同时企业宿主机明确没有 `psql`、`jq`、`rg`。
- Apple Silicon 本机已有同 tag arm64 ClickHouse，原 `docker pull --platform` 会发生本地镜像架构污染并在 Docker Hub registry 校验阶段 EOF，不能据此产出可部署 tar。

### What

- ClickHouse 26.3.17.56 改为按固定多架构 index digest 通过 buildx 直接导出 linux/amd64 archive，并从 archive config 强制校验 OS/架构；打包支持受控内网试部署显式账号密码，默认仍使用随机 64 位十六进制密码。自定义用户补充 loopback 白名单，使目标机本机 verify 与远端 `.4/.114` 白名单同时成立。
- 部署文档固定首次收敛拓扑：`.134:8123` 运行 ClickHouse；`.160` 用全新 `/data/testagent/memory/postgres-v1` 和 `15433/18889/18888/18989` 共置 pgvector、单 Mem0 副本、VIP、CPU BGE，原克隆 PG 进程、`5432` 和数据目录不删除。企业命令不依赖宿主 `psql/jq/rg`，数据库校验使用容器内 `psql`。
- `backend.env.example` 补齐默认关闭的通用记忆 URL、service key、HMAC、超时、检索和学习参数；客户端分发本身不新增中间件镜像。

### How

- ClickHouse 脚本/夹具校验和 AI 文档校验通过；最终 ZIP SHA-256 为 `84406aa69a932e7a900e9d5323c41af15dd2dcc8fd7a70cd84acfcd3388c3819`。包内 tar 重新 load 后以 amd64 仿真启动，实际完成版本查询、自定义用户认证和 `testagent_analytics` 建库。
- 记忆四个提交绑定版最终 tar 全量 `SHA256SUMS` 通过，`release.env` revision 为 `eed95c039c76c5ed0a6f7eaa27edb2edbda4b7ef`；SHA-256 分别为 memory-service `98f3c8b0...cf80`、embedding `2190a366...e1ff`、pgvector `01802c80...ce06`、VIP Nginx `7886e103...ca63`。从最终 tar 重新 load 后依次启动 pgvector、CPU BGE、Alembic、Mem0、VIP，验证数据库认证 `select 1`、BGE 固定 revision/512 维/归一化、Alembic head、Mem0/VIP `rawMessageCount=0`，随后按角色停止临时容器。
- 当前 Mac 到 `.134/.160` 的 22 端口在服务端 SSH banner 前主动断开；5432 与其它探测端口只能建立 TCP 后无应用响应，不能替代企业中转机/堡垒机上的目标宿主预检，因此未远程写入服务器。

### Result

- 外网 Mac 的 ClickHouse ZIP 与记忆四镜像目录已具备可传输、校验和离线启动条件；无运行时依赖下载。企业现场仍必须先执行只读端口、目录、架构、Docker 和容量预检，任一目标端口被占用或新数据目录非空即停止，不能清理克隆 PG。
- 本次只改变部署脚本、配置模板、测试和稳定部署文档；不修改 HTTP API、RunEvent、平台 PostgreSQL/Flyway、generated SDK、OpenCode 源码或 `.env*`，未新建分支、未推送远端。真实 `.134/.160` 部署与企业网络跨机验收尚未完成。

## 2026-08-17 - 修复 macOS PKG 安装超时与 App 错误重定位

### Why

- 无签名 PKG 经手工放行后仍在 `postinstall` 阶段等待 600 秒并以 `PKInstallErrorDomain Code=112` 失败；安装日志同时显示 App 被历史用户目录安装记录重定位到 `~/Applications`，而系统 LaunchAgent 固定访问 `/Applications`。

### What

- macOS PKG 使用 `pkgbuild --analyze` 生成 component plist，并显式关闭 `BundleIsRelocatable`，保证系统安装固定落到 `/Applications`。
- `postinstall` 只执行一次带标准输入隔离的 `launchctl bootstrap`，移除可能长期阻塞 PackageKit 的 `kickstart/open`；从用户目录版升级时精确移除同 label 的旧用户 LaunchAgent，不删除配置、工作空间或用户目录版本制品。
- 安装脚本在非根目标卷验证时跳过当前桌面会话操作；制品测试新增重定位、阻塞命令和目标卷保护断言，同步更新本地客户端部署文档。

### How

- 从 `/var/log/install.log` 复核失败脚本被 PackageKit 在 600 秒超时后终止；运行 `bash -n`、`shellcheck` 和 `deploy/internal/tests/local-opencode-client-package-test.sh` 均通过。
- 使用真实 Apple Silicon JRE、OpenCode 1.18.4 和客户端 JAR 重建 `0.1.0-dev-native-pkgfix`，展开后的 `PackageInfo` 为 `<relocate/>`，postinstall 不含 `kickstart/open`；修复版 PKG SHA-256 为 `fae60d7ba17fb19412f5d303627ca6e87c4bc061125e8a4c50d6ad422ba7ca72`。
- 无密码 sudo 无法在隔离 APFS 卷执行 root-auth PKG，真实 `/Applications` 安装仍需用户在系统 Installer 中输入管理员密码；当前机器已切回用户目录 LaunchAgent，Java PID `60264` 运行正常。

### Result

- 代码层面的 600 秒超时和 App/LaunchAgent 路径分裂已修复并通过真实制品结构验证；修复包位于 `~/Downloads/TestAgent-Local-Client-macOS-arm64-pkgfix.pkg`，仍是无 Developer ID 签名的开发包，不能冒充正式交付。
- 不变更 HTTP API、事件、数据库/Flyway、客户端协议、安全凭据、generated SDK 或 OpenCode 只读源码；实际系统级安装成功状态尚待一次管理员授权的 Installer 验证。

## 2026-08-17 - 纠正企业 ClickHouse 与记忆节点拓扑

### Why

- 现场进一步明确 ClickHouse 应共置到原平台 PostgreSQL 节点，两台新增克隆服务器都属于记忆链路；其中 `.134` 承载记忆数据库，`.160` 承载 Mem0，上一版 `.134:8123 + .160` 全记忆共置命令不能继续使用。

### What

- ClickHouse 改为部署到原平台 PG 节点 `122.233.30.147:8123`，固定使用独立 `/data/testagent/clickhouse`，保留平台 PG 的 `5432`、进程和全部数据。
- 记忆数据面改为 `.134:15433` 独立 pgvector 与 `.160:18889/18888/18989` Mem0/VIP/CPU BGE；BGE 与 Mem0 共置以避免和数据库争抢 CPU/内存，并增加 `.160` 的 CPU、内存、磁盘预检门禁。
- 企业中转机、`.134`、`.160` 的完整分发和分角色启动命令写入稳定文档；部署命令继续复用现有 `memory-docker.sh`，没有新增平行脚本。离线包测试锁定 `.147` ClickHouse、`.134` 记忆数据库和 `.160` Mem0 拓扑。

### How

- `tools/verify-ai-docs.sh`、`deploy/internal/tests/memory-offline-package-test.sh`、`tools/verify-internal-clickhouse-deploy.sh`、`tools/verify-internal-clickhouse-package.sh` 和 `git diff --check` 全部通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，没有覆盖其他提交者成果；当前修正提交将作为后续重建记忆离线包的 revision。

### Result

- 源码侧部署拓扑、具体命令与回归门禁已纠正；上一版 `.134` 部署 ClickHouse、`.160` 启动记忆数据库的命令明确作废。
- 本阶段不修改 HTTP API、RunEvent、数据库结构/Flyway、generated SDK、OpenCode 源码或 `.env*`。最终 linux/amd64 制品仍需按本提交重建并做容器启动验证后才能交付；企业真实节点尚未写入。

## 2026-08-17 - 修复记忆离线角色首次启动校验竞态

### Why

- 从拓扑修正后的最终 tar 启动 `.134` 数据库角色时，`start-db` 刚返回就执行 `verify-db`，PostgreSQL 尚未监听，现场执行单会因正常冷启动竞态失败；BGE、Mem0 和 VIP 同样存在首次启动紧接单次 readiness 的风险。

### What

- 继续复用现有 `memory-docker.sh`，只把四类单次校验改为有界等待：数据库 120 秒、BGE 180 秒、Mem0/VIP 120 秒；超时明确失败，数据库和 BGE附带对应容器末尾日志，不做无限重试或联网下载。
- 稳定部署文档同步标明等待上限，离线包测试锁定三类超时错误，避免后续退回首次冷启动竞态。

### How

- `bash -n deploy/internal/memory-docker.sh`、`shellcheck deploy/internal/memory-docker.sh`、`deploy/internal/tests/memory-offline-package-test.sh`、`tools/verify-ai-docs.sh` 和 `git diff --check` 全部通过。
- 当前修正提交将作为再次重建记忆离线包的 revision；重建后必须从最终 tar 重新跑完 pgvector、BGE、Alembic、Mem0 与 VIP。

### Result

- 现场首次冷启动不再需要操作者猜测等待时间或手工重试，超过明确上限仍失败关闭；不改变镜像、端口、数据目录、API、事件、数据库结构/Flyway、安全密钥格式或 `.env*`。

## 2026-08-17 - 收口 ClickHouse 本机验证的 Docker DNAT 差异

### Why

- 最终 ClickHouse linux/amd64 tar 在 Docker Desktop 仿真启动成功，但宿主经映射端口访问时来源被改写为网桥地址，原部署脚本的回环 HTTP 验证触发用户 IP 白名单 403；不能为通过本机验证而放宽 ClickHouse 网络白名单。

### What

- 部署脚本继续复用同一容器和用户配置，把本机版本、认证、建库和数据库存在性检查改为容器内 `clickhouse-client`；`.4/.114` 到 `.147:8123` 的跨机 HTTP 验证继续保留，网络白名单不扩大。
- ClickHouse 稳定部署文档和脚本回归测试同步更新，明确宿主不需要安装 ClickHouse 客户端，也不依赖 Docker DNAT 保留回环来源。

### How

- `bash -n`、`shellcheck`、`tools/verify-internal-clickhouse-deploy.sh`、`tools/verify-internal-clickhouse-package.sh`、`tools/verify-ai-docs.sh` 和 `git diff --check` 全部通过。
- 从修正前最终 tar 运行的同一固定镜像已通过容器内 `ck` 用户认证，返回 `26.3.17.56 / testagent_analytics`；脚本修正提交后仍需重新封装最终 ZIP。

### Result

- ClickHouse 本机校验不再受 Docker 网桥来源地址影响，远端访问权限仍只开放给 `.4/.114`；不修改分析 API、ClickHouse DDL、平台 PostgreSQL/Flyway、事件或业务代码。

## 2026-08-17 - 完成修正拓扑的最终中间件离线制品验证

### Why

- 节点拓扑和两处首次启动验证问题修正后，必须重建内嵌部署文档/脚本的最终归档，并从归档重新加载实际 linux/amd64 镜像，不能继续沿用修正前的 ZIP、目录或本机 tag。

### What

- 最终 ClickHouse ZIP 固定 `.147` 共置拓扑，用户名为 `ck`，密码为大小写、数字和 URL-safe 特殊字符组合；明文只存在受控 `0600` 包内配置和本次交付，不写入仓库文档或 session log。
- 最终记忆目录固定 `.134` 记忆 pgvector 与 `.160` BGE/Mem0/VIP，内嵌带有界 readiness 的最新脚本和部署文档；`release.env` revision 为 `d2affff0532ef334695d3bd0d74ddff3366adc92`。

### How

- ClickHouse ZIP SHA-256 为 `721d1ce9b345bc3d7b0e78906f5d15d7cdf49ae73af88e98ae31e6706585e0e5`，大小约 251 MiB；从最终 tar 以 amd64 仿真启动，容器内 `ck` 认证、`26.3.17.56` 版本和 `testagent_analytics` 建库/当前库查询通过。
- 记忆四个 tar 的 SHA-256：BGE `f8f50edc...ab77`、VIP `23fb20e1...6034`、Mem0 `147e4f46...b73b`、pgvector `9226e55e...961b`，四份 archive report 均为 `linux/amd64`，完整 `SHA256SUMS` 通过。
- 从最终记忆 tar 依次启动 pgvector、BGE、Alembic、Mem0、VIP；首次数据库有界等待实际生效，BGE 固定 revision/512 维、Alembic `20260809_01`、Mem0/VIP `rawMessageCount=0` 全部通过，随后停止并删除本次精确临时容器。
- 最终执行 AI 文档、记忆离线包、ClickHouse 部署/封包四组校验与 `git diff --check` 全部通过；提交前再次回顾全部 `.agents/session-log*.md` 近期记录，保留未跟踪的 PowerPoint 临时文件，不暂存、不删除。

### Result

- 外网 Mac 上的最终 ClickHouse ZIP 和记忆四镜像目录已具备离线传输、完整性校验与启动条件；无运行时依赖下载，企业宿主不需要 `psql`、`jq`、`rg`。
- 本次不修改 HTTP API、RunEvent、平台数据库结构/Flyway、generated SDK、OpenCode 源码或 `.env*`。企业真实 `.147/.134/.160` 仍未写入；现场必须先做只读资源、端口、目录、防火墙和跨机连通预检，失败即停止。

## 2026-08-17 - 将 macOS PKG 修复版切换到本地下载页面

### Why

- PKG 修复提交完成后，本地开发页面仍从 `deploy/internal/dist/local-opencode-client/` 返回旧 SHA，用户无法从页面取得已修复安装包。

### What

- 将 `0.1.0-dev-native-pkgfix` 的 PKG、DEB、`install.sh`、签名清单和版本化 JAR/JRE/OpenCode 作为完整目录原子切换到本地开发分发路径；旧目录保留为 `deploy/internal/dist/local-opencode-client.bak.20260817154044`。

### How

- 切换前验证版本化制品 `SHA256SUMS` 和 `manifest.json.sig`；切换后从 `http://127.0.0.1:3000/downloads/local-opencode-client/installer` 实际下载，PKG SHA-256 为 `fae60d7ba17fb19412f5d303627ca6e87c4bc061125e8a4c50d6ad422ba7ca72`，与页面清单一致且清单签名验证通过。

### Result

- 本地页面现已返回修复版，用户可以重新下载并执行系统 Installer；现有用户目录客户端保持 PID `60264` 运行，未重启 Java、worker、manager 或数据库。
- 本次只切换本机忽略跟踪的开发制品目录，没有上传企业 `.2` 的 `/data/testagent/dist/local-opencode-client/`，也没有修改 API、事件、数据库/Flyway、配置文件或源码。

## 2026-08-17 - 兼容企业 Docker 18.09 的中间件 privileged 启动

### Why

- `.147` 修复 IPv4 forwarding 与 Docker NAT 链后，ClickHouse 已能创建容器，但 26.3.17.56 在解析 `UTC` 时因旧 Docker seccomp/runc 对 `/usr/share/zoneinfo` 相关新系统调用返回 `EPERM` 而重启。
- pgvector、Bookworm Python Mem0/BGE 与 Alpine 3.20 VIP 使用同一批 Docker 18.09 企业宿主，也存在相同系统调用兼容风险；现场负责人明确批准这些新增中间件统一使用 `--privileged`。

### What

- 继续复用 `deploy-clickhouse.sh` 和 `memory-docker.sh`，分别给 ClickHouse，以及记忆 pgvector、Alembic、Mem0、BGE、VIP 五类入口增加 `--privileged`；不修改 daemon，也不改变平台 PG 或克隆机遗留 PG 容器。
- 更新 ClickHouse、通用记忆、企业部署入口和安全规范，明确 privileged 会显著放宽设备、capability、seccomp/AppArmor 隔离；保留非 root、只读根、受限 tmpfs 等参数，但不把它们表述为等价沙箱。
- 回归测试锁定六类启动入口都带 privileged。用户明确不重新拷贝大型镜像包，现场改为备份并原地修改已解压脚本，继续复用已校验的镜像 tar、配置和数据目录。

### How

- `bash -n`、ClickHouse 部署/封包测试、memory 离线包测试、AI 文档校验和 `git diff --check` 通过；本机缺少 `shellcheck` 命令，该项未执行。
- 对提交前旧版脚本以管道实际执行文档中的 `sed` 替换：ClickHouse 精确生成一个 privileged 入口，memory 精确生成五个入口，两份结果均通过 `bash -n`。
- memory 包的 `SHA256SUMS` 同时覆盖部署脚本；原地修改命令会备份原清单、只重算并替换 `memory-docker.sh` 唯一条目，再全量校验其余镜像和文件，避免后续 `load-*` 因脚本 SHA 变化失败或跳过镜像完整性检查。
- 在 Docker 24.0.2 上用现有最终 linux/amd64 镜像实际执行 privileged smoke：ClickHouse 返回 `UTC / 26.3.17.56`，pgvector 返回 PostgreSQL `16.12`，Mem0/BGE 完成时区路径解析和线程创建，VIP 返回 Nginx `1.27.2`。Mac 为 arm64，以上通过仿真完成，不能替代企业 Docker 18.09 实机验收。

### Result

- 仓库后续重新封装时会自动带上 privileged 启动参数；本次没有重建或替换 `deploy/internal/dist` 中的大型离线镜像制品。
- 不修改 HTTP API、RunEvent、数据库结构/Flyway、generated SDK、OpenCode 源码、凭据或 `.env*`。企业 `.147/.134/.160` 的原地脚本修改和真实启动仍由现场按逐机命令执行，任一 readiness 失败即停止后续节点。

## 2026-08-17 - 修复本地客户端首次入口与 client key 查询 500

### Why

- macOS 原生包安装成功后，`Info.plist` 与 JVM 都启用了后台应用模式，尚未配置的 Swing 首次向导也被隐藏，用户从 Dock 和窗口都找不到程序入口。
- 已存在 client key 的用户进入个人设置时，MyBatis 将 `javaType="long"` 解析为包装类型 `Long`，无法调用 `LocalClientCredentialRow` 的 primitive `long` record 构造器，导致凭据查询返回内部服务器错误。

### What

- 首次配置判断提前到 AWT 初始化之前：原生包缺少配置或 key 时保留普通可见 App，配置完成后的后续启动仍使用菜单栏模式；PKG 不再用静态 `LSUIElement` 隐藏所有启动阶段。
- 凭据 XML 构造器改用 MyBatis primitive alias `_long`，新增 H2/MyBatis 集成测试锁定 `BIGINT -> long` 两条查询路径。
- 头像下载入口补充“应用程序 → 首次配置 → 顶部菜单栏兔子图标”提示，原生包结构测试锁定中文应用名和首次入口可见性；同步客户端 README 与安装部署文档。

### How

- 后端定向测试：凭据 MyBatis 1/1、首次向导 3/3；前端 `FigmaShell` 61/61 与 agent-web typecheck 通过；完整本地客户端打包、清单签名、SHA-256、PKG/DEB 展开及兜底安装测试通过。
- 新页面制品为 `0.1.1-dev-entryfix`，实际 HTTP 下载 PKG SHA-256 `a0b72efdaff463b7f9da5ceff85df0720c594c60f976ec6b1933c90fff691de7`，与签名清单一致；旧目录保留为 `deploy/internal/dist/local-opencode-client.bak.202608171712-entryfix`。
- 按 `.env.test` / `test` profile 重启，backend readiness、frontend、CORS 和下载路由通过；真实页面登录后个人设置显示掩码 key、版本 1、ACTIVE，后端连续记录凭据 GET success。新 PKG 展开后用独立临时用户目录实启，macOS 返回 `visible=true` 且窗口标题为“配置 TestAgent 本地客户端”。

### Result

- Key 页面 500 已在真实服务修复；重新下载并安装新 PKG 后，首次向导存在可见应用入口，完成配置后入口转为菜单栏兔子图标。
- 本地 test profile 启动日志确认 PostgreSQL 为 `192.168.8.100:15432/testagent_dev`，XXL MySQL 为 `192.168.8.100:13306/xxl_job`。本次未修改 `.env*`、HTTP API、事件或数据库结构/Flyway，也未修改 generated SDK/OpenCode 源码。
- 开发 PKG 仍无 Apple Developer ID 签名，只完成项目清单签名与哈希校验；Gatekeeper 风险保持为既有开发约束，不能声明企业正式签名交付。

## 2026-08-17 - 修复企业 ClickHouse 进程与数据目录用户冲突

### Why

- 企业 `.147` 上 ClickHouse 为兼容 Docker 18.09 已使用 `--privileged`，现场临时加入 `CLICKHOUSE_RUN_AS_ROOT=1` 后，进程有效用户变成 root，而既有数据目录仍属于镜像内 clickhouse 用户（UID 101）。
- ClickHouse 因进程用户与数据所有者不一致报 Code 430 `MISMATCHING_USERS_FOR_PROCESS_AND_DATA`；该问题与 PostgreSQL 容器端口映射无关。

### What

- 保留经用户批准的 `--privileged`，明确禁止再设置 `CLICKHOUSE_RUN_AS_ROOT=1`；ClickHouse 继续以镜像内 UID/GID 101 运行。
- 部署时将安装后的 `clickhouse-users.xml` 设置为 `101:101` 和 `0600`，使受保护的凭据配置与 ClickHouse 进程用户一致；数据、日志目录继续由 UID/GID 101 持有。
- 部署与离线封包回归测试锁定配置所有者修复和 root 环境变量禁用，并同步 ClickHouse 企业部署手册与安全规范中的现场原地修复命令和 Code 430 排查说明。

### How

- `bash -n`、ClickHouse 部署/封包测试和 `git diff --check` 通过。
- 在 Docker 24.0.2 上以最终 linux/amd64 镜像实际执行 `--privileged` 且不设置 root 环境变量的 smoke；ClickHouse 查询返回 `26.3.17.56 UTC`，容器为 running/privileged，`/proc/1/status` 的 UID/GID 均为 101；精确临时容器和卷已清理。
- 企业 `.134` 的记忆 PostgreSQL 已通过本机 verify（PostgreSQL 16.12、`testagent_memory`）；`.160` 仅完成四类镜像加载，配置文件尚未放置，且 `.160 -> .134:15433` 仍超时，因此记忆服务尚未启动验收。

### Result

- 仓库脚本已避免再次制造 root/UID 101 冲突，企业 `.147` 可直接原地修改现有小脚本，不需要重拷大型镜像包或清空 ClickHouse 数据。
- 企业 `.147` 尚需现场执行原地修复、重建精确容器并完成 verify；`.160` 还需补齐配置和跨机网络后才能启动。不得把本机验证表述为企业实机完成。
- 本次不修改 HTTP API、RunEvent、平台数据库结构/Flyway、generated SDK、OpenCode 源码、凭据值或 `.env*`。

## 2026-08-17 - 统一用户治理入口并补齐分支整合兼容项

### Why

- 超级管理员把用户加入客户端灰度后，头像菜单的下载按钮会先出现再被本地实例健康状态替换；账号、角色、记忆灰度和客户端灰度又分散在多个页面，操作容易错位。
- 合并前门禁发现 dev 在历史冲突处理中漏掉 backend-api 的体验提交、按 Run 读消息、个人进程重启方法，以及 Flyway 本地客户端/运营 outbox 兼容装配；这些 release/dev 已有能力不能随最终合并丢失。

### What

- 将账号查询/新增/删除、用户名、角色、TCDS 同步、记忆灰度和客户端灰度统一到独立“系统管理 → 用户管理”页；设置弹窗移除重复用户入口，记忆能力页只保留平台级模型、存储、队列和抽取策略配置。
- 下载入口与已注册本地客户端健康状态改为并列显示，健康刷新不再覆盖下载按钮；移除独立本地客户端灰度面板及对应重复测试。
- 恢复 backend-api 四组已存在的方法与类型导入，并恢复 Flyway 对已执行本地客户端前向历史及 ClickHouse 运营 outbox 主链/前向分叉的严格兼容装配；未改任何已执行 migration 字节。
- 同步 agent-web、frontend、用户手册、HTTP API、本地客户端架构与部署文档。

### How

- 前端定向 5 个文件 114/114、backend-api 117/117、全量 Vitest 2016 passed / 1 skipped、agent-web typecheck 和生产构建通过。
- 后端客户端灰度应用/API/MyBatis/Flyway 命名共 24 项通过；真实 PostgreSQL 的 25 套 Spring Boot Flyway 历史升级测试全部通过，未使用 `outOfOrder`、`repair` 或手改历史表。
- `V20260817193414__local_client_rollout_users_create.sql` 保持已执行原字节，SHA-256 仍为 `88e870b4afc746522f2fc2a67ba3a2098fd6844e8b6ab99325ea6c7921ae5cba`。

### Result

- 用户治理和两类灰度现在使用同一用户事实源，记忆配置保存一次后对所有已开放用户生效；客户端下载 capability 仍默认失败关闭，但已开放用户不会因实例健康查询而丢失入口。
- 当前 dev 变更已完成代码、文档和数据库兼容门禁，等待与最新 release 做最终语义合并和实际服务重启验证；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，未推送远端。

## 2026-08-17 - 合并 dev 到 release 并保留近期部署演进

### Why

- 用户要求把 dev 整体合并到 release、解决已有冲突并切换主工作区，同时明确要求复核近期部署记录，不能在合并中丢失 release 的企业打包、迁移兼容、需求导入和可观测改动。

### What

- 以 release 的 `3ca040972` 为第一父提交合并 dev 的 `4fec97db9`；冲突处理中保留 release 较新的需求导入定向刷新、TCDS 统一请求、SCM Git 姓名补偿和模型 TTFT/ITL/Output TPS 筛选，同时并入 dev 的本地客户端、通用记忆与 ClickHouse 分析链路。
- 修复合并后部署夹具遗漏：单节点配置渲染现在保留并校验 ClickHouse 连接与密码，同时清除禁用记忆能力时的密钥占位符；增量包和双后台完整包夹具纳入 PostgreSQL 专用 migration、ClickHouse DDL、全部兼容 migration 与两套 XXL V12 历史。
- 双后台完整包测试不再绑定过期交付提交号，改为逐字比较包内 `START-HERE.md` 与当前 `MULTI-BACKEND.md`，避免最近 release 部署记录更新后门禁自身变成陈旧断言。

### How

- 相对合并前 release 执行部署路径删除审计，没有删除 `deploy/internal`、`docs/deployment`、`tools`、PostgreSQL/XXL migration；三个正式打包/部署脚本继续同时锁定分析 outbox、PostgreSQL trigger、ClickHouse DDL、本地客户端已执行兼容资源和 SCM Git 姓名 migration。
- 前端全量 Vitest `2020 passed / 1 skipped`、agent-web typecheck、用户手册与生产构建通过；后端 24 模块应用聚合打包通过。
- 真实 PostgreSQL Flyway 兼容矩阵 25/25 通过且无跳过；灰度应用/API/MyBatis/Flyway 命名测试同时纳入 reactor。`V20260817193414__local_client_rollout_users_create.sql` SHA-256 保持 `88e870b4afc746522f2fc2a67ba3a2098fd6844e8b6ab99325ea6c7921ae5cba`。
- `verify-dev-scripts.sh`（避开本机既有 19070 mock 进程改用 19071）、`verify-internal-multi-backend-node.sh`、`verify-internal-incremental-components.sh`、`verify-internal-two-backend-complete-package.sh`、Shell 语法与 `git diff --check` 全部通过。

### Result

- release 的近期部署成果与 dev 新能力已形成同一可构建、可迁移、可封包的合并树；未修改 `.env*`、generated SDK、OpenCode 只读源码或任何已执行 migration 字节。
- 企业真实 ARM 麒麟、本轮企业节点发布和中间件现场状态没有在本机重做，不能据此宣称企业环境已部署；下一步是完成 merge commit、切换主工作区到 release 并按 `.env.test` 实际重启验证。

## 2026-08-18 - 修复 release 灰度入口与两套部署迁移历史

### Why

- 实际页面验证发现客户端灰度用户的下载按钮仍会闪现后消失：旧 Java 持有服务端 OpenCode 连接时，实例列表请求会被转发到旧节点并丢掉新增 capability，健康轮询随后覆盖当前 Java 的灰度判断。
- release 合并后的真实重启又暴露两处部署历史遗漏：服务器 PostgreSQL 已执行 analytics release 后前向 migration，而 XXL MySQL 已按 release 路径执行 SCM V12 与 V13；合并树分别漏掉原字节兼容资源和 XXL V12 分叉兼容装配，不能靠改名、repair 或删除另一侧任务处理。

### What

- 新增当前 Java 直接判定的 `GET /api/internal/platform/local-opencode-client/download-access/me`；前端独立每 5 秒查询该资格，下载入口不再依赖可被跨 Java 转发的 OpenCode 实例响应。旧响应 capability 继续保留兼容，但不作为入口事实源。
- 恢复 PostgreSQL analytics-after-release 两份已执行原字节资源及真实历史回归；恢复 XXL `XxlJobMigrationCompatibilityCustomizer`、analytics V12 隔离资源、SCM V12 主链和 V13 前向迁移，三个企业打包/部署脚本重新锁定全部 SHA-256。
- 增量包夹具改为复制完整 `xxl-job/db`，确保 compatibility location 不会在 zip-only 门禁中再次遗漏；同步 HTTP API、本地客户端、XXL 架构、数据库、安全、测试和模块 README。

### How

- 真实 PostgreSQL 27 套 Spring Boot Flyway 历史升级全部通过；analytics 前向资源 SHA-256 分别为 `bd286b1d992e6ff715393fb39bbb47a7d44dfe425c3b4ea6571f62e74eed0eb1`、`399e8db352ded3f12d5b5a91fe8a07f6242a9afc07caa8c28589614a43dc50e`。
- 服务器 XXL MySQL 只读核对确认 V12 为 SCM checksum `-211900485`、V13 为 `-1179215824`，两类任务各一条；恢复资源 SHA-256 分别为 analytics V12 `70878c4544d5d8c030b1edf59406a320ceec68f86bd763d366a80d5d4ed005f0`、SCM V12 `2ef19bbbffb56131981f4f99f7d58d5b1d9f25715b0e76dc0cfd44b80b196739`、V13 `d7627696bcabc9f170f7709e298b46e28ba306a38f2251572c99b6b8175ff96a`。
- 后端资格控制器 3/3、前端全量 Vitest 2020 passed / 1 skipped、agent-web typecheck/用户手册/production build、26 模块跳过测试打包、增量包/多后台节点/双后台完整包门禁、Shell 语法与 `git diff --check` 通过。
- 按 `.env.test` / test profile 完整重启后，平台 health 为 UP、XXL readiness 为 200、前端为 200；真实页面中灰度开启后下载入口跨两个以上刷新周期仍可见，随后恢复测试用户原有非灰度状态，接口返回 `allowed=false` 且按钮消失。

### Result

- release 近期 SCM、ClickHouse/analytics、企业封包和本地客户端灰度能力同时保留；当前服务连接服务器 PostgreSQL `192.168.8.100:15432/testagent_dev` 与 XXL MySQL `192.168.8.100:13306/xxl_job`，没有修改 `.env*`、数据库 history、generated SDK 或 OpenCode 源码，也没有推送远端。
- 本机 MySQL 8.4 Testcontainers 容器内部已 ready，但 Docker Desktop 暴露端口在 JDBC 握手阶段持续无响应，因此本轮 7 条空库/analytics V12/SCM V12/未知 checksum/并发自动化未取得结果并已终止；真实服务器 SCM V12 → V13 路径已验证，另一套 analytics V12 自动化仍以历史测试和原字节锁定为证，不能表述为本轮完整重跑。

## 2026-08-18 - 按已部署中间件拓扑重打 release 企业增量包

### Why

- 用户确认企业 ClickHouse、Mem0、CPU BGE 与独立 pgvector 已部署完成，要求基于当前本地 `release` 重新封包；平台包不应重复携带或重启这些数据面镜像，但两台 Java 必须指向已确认的站点地址。
- 上一版 `.4/.114` 敏感节点包早于这些集成配置，直接覆盖 `backend.env` 会丢失现场 ClickHouse 密码、Mem0 service key 与模型网关 HMAC；当前新增的本地客户端/analytics compatibility migration 也尚未全部进入最终 JAR 三层字节门禁。

### What

- 双后台封装在临时节点副本中固定启用 ClickHouse `122.233.30.147:8123/testagent_analytics` 和 Mem0 VIP `122.233.30.160:18888`，保持 backfill/旧汇总清理关闭；CK/Mem0/BGE/pgvector 镜像仍不进入平台包。
- 复用既有 dotenv 读取、替换与逐机部署入口：旧节点包缺少三项运行密钥时只写继承标记，部署前从目标机已安装 `backend.env` 原样带入；键缺失、重复或长度不足会在覆盖配置和重启 Java 前失败，日志不输出密钥。
- 内层构建、外层封装和目标机安装同时新增本地客户端主迁移、客户端灰度迁移、release 本地客户端兼容迁移和两份 analytics-after-release 兼容迁移的 SHA-256 校验；未修改任何 migration 文件字节。
- 更新双后台手册与配置夹具，新增密钥继承成功/短值失败回归；`.4` 既有 Qwen 优先模型灰度随 worker runtime 复用保持不变。

### How

- `FlywayMigrationNamingTest` 15/15、运行密钥继承测试、增量组件门禁、双后台完整包门禁和多后台逐机门禁全部通过；相关 Shell `bash -n`、`git diff --check` 通过。
- 正式后端薄 JAR、152 MiB 外置依赖和同源生产前端从当前工作树重建成功；新增 PostgreSQL/ClickHouse/XXL 资源均从最终 persistence/XXL JAR 逐项核对 SHA-256。
- 本轮无企业签名私钥，默认全量命令在本地客户端签名步骤按设计停止；确认 `f5cfa6d77` 后客户端二进制源码无变化，并从既有 `0.1.1-dev-entryfix` 分发目录提取内嵌公钥验证 manifest 签名及 PKG/DEB 哈希后，以 `--zip-only` 复用该已验证制品，没有生成假签名。
- 组件计划为 worker runtime `reuse`（`50f56c...2fb1`）和 toolbox `reuse`（`35447d...5040`）；最终包清单为 LobeHub `disabled`、memory 离线组件 `disabled`、本地客户端分发 `included`。外层内嵌内层逐字节一致，节点配置、前端入口、组件缺席项和新增 Flyway 资源独立解包复验通过。

### Result

- 固定名平台 ZIP 仅更新 Java、前端、部署脚本和新引入的本地客户端分发，不加载或重启 manager/worker/toolbox，也不重复交付已部署的 ClickHouse/Mem0/BGE/pgvector 镜像。
- 现场发布前必须先导出平台 PostgreSQL 与 XXL MySQL 完整 `flyway_schema_history`，并确认 `.4/.114` 已安装配置各有三项真实运行密钥；任一未知 checksum、失败记录或密钥缺失都停止，禁止 `repair`、`outOfOrder`、手改 history 或写入本地开发密钥。
- 本次未修改 `.env*`、HTTP API、RunEvent、业务数据库结构、generated SDK 或 OpenCode 只读源码；企业 `.4/.114/.2` 尚未执行本轮包，真实启动和功能验收仍以逐机发布结果为准。

## 2026-08-18 - 为用户灰度开关补充即时保存确认与结果反馈

### Why

- 超级管理员在统一用户管理页点击记忆灰度或客户端灰度开关时会立即调用接口保存，但原成功提示没有明确“已保存”，失败只落在页面提示区，也缺少防误触确认，用户难以判断开关是否已持久化。

### What

- 两类灰度开关复用现有 `ElMessageBox`，提交前明确提示“确认后将立即保存”；确认成功后弹出带用户名、灰度类型和开关结果的可关闭“已保存”消息。
- 取消确认不发请求；接口失败时弹出可关闭“保存失败”消息，并通过显式受控状态回滚保证开关视觉状态恢复到服务端原值。
- 同步 agent-web README 与包说明，并扩展既有用户管理组件测试覆盖两类确认、成功消息、取消回滚和失败回滚。

### How

- 定向 Vitest `settings-user-management-panel.test.ts`、`scheduler-management-panel.test.ts` 共 31/31 通过；agent-web 用户手册、TypeScript 检查和 production build 成功，仅保留既有大 chunk 警告。
- 使用真实本地前后端和超级管理员账号进入“系统管理 → 用户管理”，点击测试用户的记忆灰度开关后确认弹框包含“确认后将立即保存”，点击取消后开关保持 `false`，未改变服务器灰度数据；前端与后端 readiness 均为 200/UP。

### Result

- 灰度开关仍使用既有 API 即时保存，不新增接口、状态容器或重复组件；管理员现在能在提交前确认，并从成功/失败弹框明确判断保存结果。
- 不涉及 HTTP API、RunEvent、数据库、Flyway、部署、性能或安全契约；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，未推送远端。

## 2026-08-18 - 修复企业已部署基线上的 Flyway 低版本阻塞

### Why

- `.4` 用上一轮完整包升级 Java 时，包校验、worker runtime 复用和旧 Java 停止均成功，但新 Java 被 Flyway 拒绝启动：企业库已经执行 `20260812204207` 及之后的 release/SCM migration，兼容装配仍暴露更低且未执行的本地客户端前向版本 `20260812202425`。
- 该问题与 ClickHouse、Mem0、BGE、pgvector、manager 或 worker 健康无关；必须为已越过旧前向版本的企业历史提供更高版本路径，不能开启 `outOfOrder`、执行 `repair` 或修改 `flyway_schema_history`。

### What

- 新增企业已部署基线专用前向 migration `V20260818094330__local_client_credentials_create_runtime_after_enterprise_release.sql`，结构 SQL 与旧前向路径一致；`DatabaseMigrationCompatibilityCustomizer` 依据实际 history 在主链、旧前向和企业高版本前向三条互斥路径中选择，检测到多路径或未知 checksum 时继续失败关闭。
- 后端构建、内层发布、外层完整包和目标机安装四层门禁都锁定新资源 SHA-256 `6d390354ddb9794c1f3730f09f1dd806ea74628f20fa6ea2857c1dee6774d25c`；同步后端、持久化、数据库和多后台部署说明。
- 本轮仍只更新 Java、前端和部署资源；复用现有 worker runtime、manager、toolbox 与已签名本地客户端制品，不重复交付或重启已经部署的 ClickHouse、Mem0、BGE、pgvector 数据面。

### How

- `FlywayMigrationNamingTest` 15/15 通过；真实 PostgreSQL `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 29/29 通过，覆盖企业 SCM 基线首次升级、二次启动重校验、旧前向已执行历史和未知企业版本 checksum 拒绝。
- 正式后端构建、正式前端类型检查/用户手册/production build、增量组件门禁、多后台节点门禁、双后台完整包门禁、相关 Shell `bash -n` 与 `git diff --check` 均通过；前端只保留既有大 chunk 警告。

### Result

- 当前 release 已能在附件对应的企业历史上按默认 Flyway 顺序执行 `20260818094330`，随后继续执行更高的客户端灰度 migration；旧主链、旧前向或企业高前向任一已执行历史都保留原字节复验。
- 不涉及 HTTP API、RunEvent、业务逻辑、性能或安全契约；未修改 `.env*`、generated SDK、OpenCode 只读源码、任何已执行 migration 字节或数据库 history。最终企业包需完成重组和逐层哈希验证后再交付，`.4/.114/.2` 真实启动仍以现场逐机验收为准。

## 2026-08-18 - 修复企业客户端下载跳转丢失外部端口

### Why

- 最新 release 现场通过企业域名 `mimo.sdc.cs.icbc:9996` 打开安装器时，Nginx 把相对 `return 302` 序列化为不含 `:9996` 的绝对 URL，浏览器随后访问默认 80 端口并得到 503；同一制品从 `.2:9996` 回环访问可正常 302 后下载 200，已排除文件缺失。
- 同轮现场证据还确认运营事实与小时/日汇总已写入 ClickHouse，但首次历史回填关闭且用户维度未就绪；记忆 CHAT 下拉对应的内部模型目录为空；消息颜色源码和当前活动前端 chunk 均为既定的本人消息 `#B2EDDF`，不属于代码回退。

### What

- 在企业网关与独立下载 server 中关闭 Nginx 绝对重定向，使安装器、目录规范化及其它站内跳转返回相对 `Location`，由浏览器保留外部 `host:port`。
- 扩展 Nginx 配置验证脚本，锁定两个 server 都生成 `absolute_redirect off;`；同步单/多后台部署说明和本地客户端部署、验收文档。
- 未改变消息颜色、运营查询口径、模型目录数据或环境配置；后三项分别保留现有正确实现，并通过现场处置/排查命令完成后续定位。

### How

- `tools/verify-internal-nginx-config.sh` 通过，且使用本机已有 `nginx:1.27.2-alpine3.20` 镜像完成真实 `nginx -t`。
- 消息颜色定向 Vitest 2 个文件 64/64 通过；`tools/verify-ai-docs.sh`、Shell 语法、`git diff --check` 和本次改动文件冲突标记扫描通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，未发现与本次 Nginx、测试和文档修改冲突的未完成事项。

### Result

- 新包部署并 reload Nginx 后，企业域名 `:9996` 的安装器跳转会保留原入口端口，不再因跳到默认端口返回 503；真实企业节点仍需按文档用 Mac/Linux User-Agent 跟随跳转验收 200。
- 本次只修复现有部署脚本，不新增节点、服务、端口或强制依赖，符合 `release` 维护边界；不涉及 HTTP API、RunEvent、数据库结构、Flyway、性能或 generated SDK，未修改 `.env*` 或 OpenCode 只读源码。

## 2026-08-18 - 统一用户消息浅蓝色并补齐记忆 CHAT 模型配置入口

### Why

- 用户确认本人消息和共享会话中的其他用户消息都应使用截图浅蓝色；现有实现仍把共享他人消息显示为浅紫色。
- 企业库已有 Provider 与 Token，但公开模型目录为空；后端虽已有目录保存和能力探测 API，系统管理页面没有入口，导致记忆配置 CHAT 下拉为空且只能靠浏览器控制台手工调用。

### What

- 将本人及共享会话其他用户气泡统一为截图实测颜色 `#EAF3FD`，保留发送人姓名、归因和操作权限逻辑。
- 在“内部模型供应商”页面增加公开模型目录编辑，支持公开/上游模型 ID、显示名、九项声明能力、上下文上限、Embedding 维度和启停；“保存并探测 CHAT”复用既有覆盖保存和真探测 API，在旧探测结果被清空后立即探测启用 CHAT 模型并反馈部分失败。
- 记忆 CHAT 目录为空时增加产品内入口，直接切换到内部模型供应商页面；同步 agent-web/agent-chat README、包说明和组件回归测试。

### How

- 定向 Vitest 4 个文件 74/74 通过，覆盖本人/共享消息颜色、模型目录保存与 CHAT 探测、记忆空目录跳转和全局主题事实源。
- `agent-web` 类型检查、lint 和 production build 均通过；最终 bundle 同时包含 `#EAF3FD` 和“保存并探测 CHAT”，仅保留既有大 chunk 提示。
- `git diff --check` 与旧颜色残留扫描通过；提交前回顾全部 `.agents/session-log*.md` 近期记录，未发现与本次前端改动冲突的未完成事项。

### Result

- 发布新版前端后，两类用户消息颜色一致；超级管理员可全程在页面内完成 CHAT 模型初始化，探测成功后回到记忆能力页面即可选择，无需执行 SQL 或浏览器控制台脚本。
- 未新增或修改 HTTP API、RunEvent、数据库、Flyway、部署拓扑、强制配置、安全边界、generated SDK 或 OpenCode 只读源码；未修改 `.env*`。

## 2026-08-18 - 按历史修复记录恢复共享他人消息浅紫色

### Why

- 用户澄清截图浅蓝色只用于本人消息；共享对话中的他人消息此前只是把偏深紫色调浅，不应与本人消息统一为同色。
- 历史提交 `457e83994` 和本日志既有记录确认最终口径为低饱和浅紫 `#DED9F6`，上一轮统一浅蓝属于范围理解错误。

### What

- 本人消息继续使用截图浅蓝 `#EAF3FD`；共享会话中的其他用户消息恢复语义 token `--ta-chat-other-user-bg`，默认值为 `#DED9F6`。
- 保留发送人归因、姓名展示、无边框样式和操作权限逻辑；同步 agent-web、agent-chat README，并补充全局主题 token 断言。

### How

- 前端全量 Vitest 130 个文件通过，2024 passed / 1 skipped；`agent-chat` 类型检查和 `agent-web` production build 通过，仅保留既有大 chunk 警告。
- 构建产物同时包含 `#EAF3FD` 与 `#DED9F6`；本地前端继续运行于 `127.0.0.1:3000` 并返回 HTTP 200。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，确认本次恢复值与历史最终修复一致，未覆盖其它开发者成果。

### Result

- 本人消息与共享他人消息重新保持可辨识的浅蓝/浅紫两色，不再误把共享他人气泡统一成浅蓝。
- 不涉及 HTTP API、RunEvent、数据库、Flyway、部署拓扑、性能、安全、环境配置、generated SDK 或 OpenCode 只读源码。

## 2026-08-18 - 将 ClickHouse 存量回填纳入双后台部署

### Why

- 企业现场 ClickHouse 已持续产生实时行为事实，但用户维度只有 1 行，运营页面仍显示 0；现有 Java 历史回填具备全量维度补齐、稳定事实 ID、源/目标计数校验、集群锁和 PG cutover 幂等能力，却被完整包与节点门禁固定为 `BACKFILL_ENABLED=false`，发布流程从未触发。
- 历史回填需要访问生产 PostgreSQL/ClickHouse，不能在 Mac 打包时执行，但应由首个生产后端节点的部署升级步骤自动完成，不能继续依赖发布后的手工 SQL 和人工改配置。

### What

- 新增 `run-analytics-clickhouse-backfill.sh`：只把已安装 `backend.env` 的回填开关临时改为 `true`，重启并等待现有 Java Runner 输出完成日志；成功后原子恢复落盘开关为 `false`，失败、超时或中断则恢复开关、重启普通服务并让部署失败。脚本强制 ClickHouse 启用、清理旧汇总关闭、UTC start 非空且键唯一，全程不 `source` 或打印现场密钥。
- 标准后台部署新增显式 `--run-analytics-backfill`；双后台逐机入口仅为固定首节点 `.4` 默认传入，`.114` 和扩容节点不执行。故障重部署复用既有 `analytics-v1` cutover 与集群锁幂等跳过或重试，紧急恢复才允许显式跳过。
- 发布 ZIP、完整双后台包和节点测试增加脚本存在性及编排门禁；同步 ClickHouse、多后台、后端配置和数据库部署说明，明确打包只封装能力、真实回填发生在 `.4` 部署阶段，旧 PostgreSQL 汇总清理仍保留为验收后的独立不可逆步骤。

### How

- 新增 Shell 行为测试覆盖成功只重启一次、完成后开关恢复、Java 启动失败自动回滚并恢复普通服务、重复键拒绝和密钥不泄漏；双后台逐机门禁、固定名完整包门禁、无参数节点编排和 AI 文档校验均通过。
- 相关部署/打包/测试脚本 `bash -n`、`git diff --check` 和冲突标记扫描通过；既有 `AnalyticsClickHouseBackfillServiceTest` 4/4 通过，继续验证已复用 Java 回填服务的计数校验与幂等语义。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，保留当前 release 已完成的下载跳转、消息配色和记忆 CHAT 配置成果，未覆盖其它开发者成果。

### Result

- 重新打完整平台包并按 `.4 → .114 → .2` 部署时，`.4` 会自动补齐存量用户维度和历史行为，只有 Java 内部源/目标计数验证并记录 `VERIFIED` 后才允许发布继续；后续发布会快速幂等跳过。
- 本机仅完成模拟部署和 Java 单测，尚未在企业 `.4/.114/.2` 真实执行或验证页面数据；必须用包含本次脚本的新包重部署后，以 `.4` journal 完成日志、落盘开关 `false`、CK 维度/事实计数及运营页面共同验收。
- 本次不新增节点、服务、端口、强制运行依赖、HTTP API、RunEvent 或数据库结构，不修改 Flyway、`.env*`、generated SDK 或 OpenCode 只读源码；旧汇总清理仍未自动执行。

## 2026-08-18 - 同步 ClickHouse 部署回填到 dev 并记录既有门禁漂移

### Why

- release 的部署修复按长期分支规则 cherry-pick 回 dev 后，需要确认新增回填编排没有被分支差异破坏。

### What

- dev 已包含与 release 相同的回填脚本、标准部署参数、`.4` 单节点自动编排、包门禁和文档；未修改业务代码或生产配置。
- 复核发现 dev 父提交已有两处测试夹具漂移：多后台节点夹具没有为模板中的 CK/Mem0 `REPLACE_` 值提供替换输入，完整包夹具没有装入其打包门禁已要求的 QA memory migration。

### How

- dev 上新增 `verify-internal-analytics-backfill-deploy.sh`、无参数节点编排和 AI 文档校验通过，工作区干净。
- `verify-internal-multi-backend-node.sh` 在进入本次新增逻辑前即报 `Rendered configuration still contains REPLACE_ placeholders`；`verify-internal-two-backend-complete-package.sh` 在进入本次新增脚本门禁前即报缺少 `V20260809120000__create_qa_memory_governance.sql`。通过 `git show bbf6d0fee...` 确认对应父提交已经同时具备失败门禁和缺失夹具，非本次 cherry-pick 引入。

### Result

- 本次部署回填修复已同步到 dev，专项行为不回退；release 上的逐机和完整包门禁均通过。
- dev 两项既有企业包夹具漂移未在本次顺手修复，后续应单独对齐 dev 的配置渲染与 migration 夹具后再把这两项作为 dev 全绿门禁；不得把当前失败误判为真实包可以缺少密钥或 migration。

## 2026-08-18 - 合并本地 dev 到 release 并重建企业交付输入

### Why

- 用户明确要求把当前本地 `dev` 合并到 `release` 后重新打企业包；两分支从既有合并点后分别承载了等价的下载跳转、模型配置、消息配色和 ClickHouse 回填提交，`dev` 另有部署门禁漂移记录，需要形成真实双父合并历史而不能只按提交标题判断。

### What

- 在干净的 `release` 上以 `--no-ff` 合并 `dev@ce2f968aa`，生成双父提交 `578dcdf4a`；冲突只涉及本机 session log 和 agent-web README。日志保留两边全部条目，README 保留 release 更新的 UCID/Output TPS 可观测口径和独立用户灰度管理，同时保留 dev 已进入 release 的模型目录、消息配色和 ClickHouse 回填能力。
- 合并后的生产 tree 除追加 dev 的 session log 条目外与合并前 release 一致；企业高版本本地客户端补偿、两份 analytics 前向资源、XXL V12/V13 分叉兼容、TCDS 固定域名、`.4` Qwen 灰度和 CK/Mem0 配置均未回退。
- 继续复用既有 `DatabaseMigrationCompatibilityCustomizer`、ClickHouse Java 回填服务和企业打包/逐机入口，没有新增并行迁移器、部署节点、服务、端口、强制依赖或重复代码路径。

### How

- 真实 PostgreSQL Flyway 历史矩阵 29/29、Flyway 命名 15/15、ClickHouse 回填 Java 服务 4/4 通过；最终后端构建逐项核对 persistence/XXL JAR 内全部受控 migration SHA-256。
- 前端合并相关 4 个文件 74/74、首轮并发全量中超时的 3 个 Markdown 文件隔离复跑 30/30、agent-chat/agent-web typecheck、用户手册和 production build 通过，仅保留既有大 chunk 警告。
- ClickHouse 回填脚本行为、开发脚本（mock 端口改用未占用的 `19071`）、Nginx、增量组件、多后台节点、固定名双后台完整包门禁与相关 Shell 语法全部通过。

### Result

- 当前 `release` 已真实包含本地 `dev` 全部提交历史，合并冲突没有覆盖 release 已部署的 Flyway、前端、TCDS、运营分析和灰度成果；工作区未修改 `.env*`、generated SDK、OpenCode 只读源码或任何已执行 migration 字节。
- 正式后端与前端二进制已经从合并提交构建；下一步只需用既有 worker/toolbox 指纹、已签名本地客户端制品和三台已验证节点配置重组内外层 ZIP。企业 `.4/.114/.2` 真实发布与 ClickHouse 首次存量回填仍需现场验收。

## 2026-08-18 - 修复固定 action 路由 ID 误判与加入应用弹窗偏移

### Why

- 本地客户端路由在生产装配态把 `/sessions/` 和 `/workspaces/` 后的任意路径段都当作资源 ID，导致 `batch-items`、`runtime-state` 和 `experience` 分别进入 Session/Workspace ID 校验并报无效。原回归测试未装配本地客户端路由，没有复现生产条件。
- 顶栏右侧工具组使用 `transform` 对齐，其内的 `position: fixed` 加入应用弹层因此改以该工具组为定位上下文，表现为弹窗缩到右上角。

### What

- 路径段只有以 `ses_` / `wrk_` 开头时才参与本地客户端资源路由；其余固定 action 继续走用户 binding 和正常 Controller 路由。批量 Session 创建同时纳入 body 的 `workspaceId` 检查，保留本地客户端工作区精确路由能力。
- 回归测试统一按生产方式装配本地客户端路由，覆盖 `/sessions/batch-items`、`/sessions/runtime-state`、`/sessions/runtime-state/events` 和 `/workspaces/experience/open`，并保留普通 `ses_` / `wrk_` 资源路径用例。
- 加入应用弹层通过 Vue `Teleport` 挂载到 `body`，测试从真实 `document.body` 检查弹层和保存交互；同步 API 模块与 agent-web README。

### How

- JDK 21 定向 Maven 回归 `UserOpencodeBackendRoutingWebFilterTest,BatchSessionControllerTest,ExperienceWorkspaceControllerTest` 共 49 项全部通过。
- `FigmaShell.test.ts` 62 项全部通过，`@test-agent/agent-web` typecheck 通过，`git diff --check` 通过。一次误传 Vitest 参数触发全量测试，暴露 5 个与本次无关的既有 Mermaid/jsdom canvas 失败，本次目标文件隔离复跑全绿。
- 用 JDK 21 执行 `./restart-dev-services.sh --profile test --env-file .env.test` 完成后端全量构建、前端生产构建与本地重启；后端 readiness 为 `UP`，前端 `/workbench` 返回 200。

### Result

- 四条固定 action 在生产装配态不再被误判为资源 ID，正常 `ses_xxx` / `wrk_xxx` 路径仍保持原精确路由；加入应用弹窗不再受顶栏 transform 约束。
- 未变更 HTTP 路径、DTO、RunEvent/SSE 事件契约、数据库、Flyway、部署拓扑、性能模型、安全边界、环境配置、generated SDK 或 OpenCode 只读源码；工作区已有的部署脚本改动未纳入本次修复。

## 2026-08-18 - 本地客户端纳入企业增量组件门禁

### Why

- 用户指出 release 最新企业包仍约 500 MiB，而 13:00 左右以后没有部署过的新代码并未修改本地客户端。根因是 worker runtime 和 toolbox 已支持指纹复用，但 `dist/local-opencode-client/` 仍被完整包无条件构建、复制和部署，单项压缩后约 417 MiB。
- 不能仅从 ZIP 删除客户端：`.2` 必须证明现场既有 PKG、DEB、JRE、OpenCode、客户端 JAR、安装脚本和签名清单与已部署版本完全一致，才能更新前端和 reload Nginx。

### What

- 将本地客户端作为第三个独立增量组件，指纹覆盖客户端/协议源码、POM、封装与安装脚本、固定 OpenCode 许可证、原生图标及版本/上游 SHA/签名身份配置；新增 `included/reuse` 清单字段和持久化版本、manifest、signature、install SHA-256。
- 新增 `20260818-local-client-entryfix.env`，固定 13:00 前已部署客户端 `0.1.1-dev-entryfix` 的源码提交、当前输入指纹和制品哈希。旧完整包中的同一客户端已用新校验器逐文件验证，未把 14:50 以后仅打包未部署的后端/前端当成现场基线。
- 新增不依赖 `jq`、`rg` 或网络的客户端分发校验器。`reuse` 包不再携带、复制、备份或替换客户端目录；`.2` 在切换 deploy/internal、静态前端和 Nginx 前校验现有 `/data/testagent/dist/local-opencode-client`，任一文件漂移即停止部署。`included` 包仍先完整验真再原子替换。
- 同步本地客户端、前端、多后台和企业打包文档；增量夹具覆盖全量加入、下一轮省略、显式 baseline 恢复、篡改拒绝和 worker-only 更新不带客户端。

### How

- Shell 语法、`git diff --check`、增量组件门禁、双后台逐机门禁和本地客户端原生包/签名/安装器测试通过；客户端篡改夹具会稳定失败。
- JDK 25 下真实 PostgreSQL `DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 29/29、`FlywayMigrationNamingTest` 15/15 通过，0 跳过；未使用 `outOfOrder`、`repair` 或手工修改 history。
- 从 `release@4098f8fe5` 最新业务代码重新编译后端和前端，production build 成功；发布 JAR 内全部受控 PostgreSQL/ClickHouse/XXL migration 字节门禁通过。首次增量内层 ZIP 为 154115982 bytes，worker/toolbox/client 均为 `reuse`、LobeHub/独立 memory 包为 `disabled`，且不存在 `dist/local-opencode-client/` 条目。

### Result

- 后端和前端包含约 13:00 后尚未部署的 release 变更；已部署且未变化的 worker、toolbox 和本地客户端不再重复进入 ZIP，内层包从上一轮约 564 MiB 外层中的 417 MiB 客户端重复内容收敛为约 147 MiB。
- 本次修复不变更 API、RunEvent、数据库结构、Flyway 字节、运行拓扑、manager/worker 进程、模型清单、环境配置、generated SDK 或 OpenCode 只读源码。真实企业 `.4/.114/.2` 部署仍需按新包执行并完成 ClickHouse 首次存量回填、Flyway 启动和客户端 reuse 校验验收。

## 2026-08-18 - 修复 ClickHouse 回填日志门禁丢失完成信号

### Why

- 企业 `.4` 首次回填期间，数据库已写入 `analytics-v1=VERIFIED`，Java 也输出 `sourceEvents=18532,targetFacts=18532`，但部署终端只显示 `Starting idempotent ClickHouse analytics backfill`，中间无进度。
- 现有检查每 3 秒重复查询“启动 cursor 之后最近 400 行”；逐日 hourly/daily 汇总会产生大量日志，固定行数窗口存在把完成信号挤掉的丢检风险，且无法让现场判断当前汇总日期。

### What

- 扩展现有 `journal_after_start` / `wait_for_backfill`：每轮从上次 `--show-cursor` 继续增量读取，取消 `-n 400`，仍只以原 Java 完成/失败日志作为门禁，不新增数据库旁路。
- 每轮只向部署终端输出最新一条 `Analytics hourly/daily rollup rebuilt`，成功时显示捕获到的完成日志。
- 更新 ClickHouse 企业部署手册，并把既有行为验证器接入 `tools/verify-dev-scripts.sh`。

### How

- Shell 行为回归模拟两轮 cursor：第一轮输出 hourly 进度，第二轮在完成日志后追加 450 行启动噪声，仍必须识别成功；同时继续覆盖失败回滚、开关恢复、重复键拒绝和密钥不泄漏。
- `tools/verify-internal-analytics-backfill-deploy.sh`、`bash -n`、`git diff --check` 通过。`tools/verify-dev-scripts.sh` 首轮因本机周一遗留的 mock 进程占用 `19070` 且返回空响应而在任务外冒烟失败；不停止该用户进程，改用空闲 `19071` 完整复跑后全部通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，未发现与本次部署脚本修复冲突；工作区起始为干净 `release`。

### Result

- 新企业包中的 `.4` 回填门禁会持久推进 cursor 并显示当前汇总进度，完成日志不再依赖固定 400 行窗口。
- 不修改 ClickHouse/PostgreSQL 数据、Flyway、HTTP API、RunEvent、服务启停顺序、开关语义、环境配置、manager/worker、generated SDK 或 OpenCode 只读源码；已在现场成功的本轮回填不需重做，修复只在后续重打包部署时生效。

## 2026-08-18 - 补齐记忆固定 CPU Embedding 页面内探测闭环

### Why

- 记忆策略保存会同时校验固定 CHAT 与只读的 `memory-bge-small-zh-v1.5` CPU Embedding；内部模型供应商页面却只有 CHAT 探测入口，管理员即使已保存并探测 CHAT，仍会因固定 CPU 模型未通过 `EMBEDDING` 探测收到泛化的“企业模型不存在或未通过探测”。
- 模型目录覆盖保存会清空该 Provider 的全部旧探测结果，单独新增 EMBEDDING 按钮若不恢复既有状态，还会导致同一 Provider 的 CHAT/EMBEDDING 探测相互清空。

### What

- `InternalModelProviderPanel` 复用既有目录覆盖和通用能力探测 API，增加“保存并探测 EMBEDDING”；指定目标能力时重新探测保存前已成功且仍声明的其它能力，状态标签不再只按 CHAT 判断。
- `MemoryAdminPanel` 在目录请求成功且固定 CPU BGE 未通过 EMBEDDING 探测时提前显示阻断说明、禁用保存，并提供跳转到内部模型供应商页的产品内入口。
- 同步前端工程/应用/包说明、模块图和记忆企业部署文档，明确 CPU 模型需点击“保存并探测 EMBEDDING”；HTTP API、DTO、数据库和部署拓扑未变化。

### How

- 定向 Vitest 两个文件 13/13 通过，覆盖 CHAT、EMBEDDING、目录覆盖后的既有能力重探测，以及固定 CPU 未探测时的保存阻断和跳转。
- 前端全 workspace `typecheck`、`lint`、production build 和 `git diff --check` 通过；构建仅保留既有大 chunk 提示。JDK 25 后端 26 模块跳过测试构建成功。
- 使用 `.env.test` / `test` profile 连接 `192.168.8.100:15432/testagent_dev` 重启后端和前端，readiness 为 UP；真实浏览器确认两项探测按钮可见、固定 CPU 阻断文案与禁用保存生效、跳转闭环可用且控制台无错误。验收未点击真实探测按钮，没有修改 `.100` 模型目录或探测状态。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，保留当前 release 已有模型目录、记忆灰度和企业打包成果，未发现冲突或残留合并标记。

### Result

- 超级管理员可只通过页面完成记忆依赖的 CHAT/EMBEDDING 配置；固定 CPU BGE 未就绪时不再等到保存请求才暴露泛化错误。
- 本次不修改 `.env*`、HTTP API、RunEvent、数据库/Flyway、服务端校验、安全边界、部署拓扑、generated SDK 或 OpenCode 只读源码；企业离线内外层 ZIP 尚未因本次前端修复重新构建，现场升级前仍需重新打包并按既有门禁验收。

## 2026-08-18 - 内部模型可观测页面布局优化

### Why

- 内部模型可观测页面（`InternalModelObservabilityPanel.vue`）原先存在布局失衡问题：17 项 Overview 概览指标无卡片包裹集中平铺，缺乏视觉层次；图表区域全单列纵向串联堆叠，桌面端纵向拉得过长且屏幕右侧大量空白；吸顶筛选条分割线右侧带 `margin-left: auto` 导致按钮远离下拉框，整体呈现“有时候过于紧凑，有时候又过于松散”。

### What

- 重构 Overview 概览指标视觉卡片，统一背景（`#f8fafc`）、微细边框（`#f1f5f9`）、圆角与 Hover 效果，将字号调至 `20px`，呈现清晰精致的仪表盘小组件风貌。
- 在桌面端（`@media (min-width: 1024px)`）启用双列网格布局，并将对齐方式设为 `align-items: start`，解决左侧单张卡片（如“调用结果分布”）因 `stretch` 被强行垂直拉高变形、圆环图悬浮在大片空白中间的问题。
- 将 `.ta-imob-chart-empty` 的最小高度由 `260px` 优化精简为 `120px`，使无数据状态下的箱线图卡片紧凑得当，不再无故挤占空间。
- 调整“供应商请求量对比”与“失败原因分类”卡片渲染逻辑，移除数据为 0 时的 `v-if` 条件隐藏，补充统一的精美空态提示（“当前筛选范围暂无供应商对比数据”与“当前筛选范围无异常调用记录”），确保卡片框架常驻且布局结构对称。
- 重构“失败原因分类”图表为**竖向柱状图**（`xAxis` 为异常类型，`yAxis` 为次数），并按**不同供应商（`providerId`）分组显示与图例区分**。
- 更新底部指标网格标题为 `按供应商统计 (聚合明细)`，明确聚合数据与供应商维度的对应关系。
- 保持所有组件事件、逻辑、单元测试 DOM selector 选择器名称 strictly 兼容不变。

### How

- 前端定向 Vitest `npx vitest run --environment jsdom tests/internal-model-observability-panel.test.ts` 3/3 100% 测试通过。
- 全量 TypeScript 类型检查 `npx vue-tsc --noEmit --pretty false` 0 错误通过。
- 提交前按 `docs/guides/self-checklist.md` 和 `.agents/session-log*.md` 复核，确认本次纯 CSS/布局优化未影响任何 API 逻辑或数据校验。

### Result

- 内部模型可观测页面视觉体验得到显著提升，Overview 卡片层次丰富，桌面端大屏下图表左右并排显示，布局紧凑匀称、无冗余空白。
- 未修改任何 HTTP API、DTO、数据库结构、Flyway migration、环境配置文件（`.env*`）、generated SDK 或 OpenCode 源码。

## 2026-08-18 - 固定 `.100` 验收并排查账号工作区服务器归属

### Why

- 用户要求后续本机验收固定使用 `.100` PostgreSQL、停止主动跨长期分支同步，并处理 `usr_test_dev` 对话报“工作空间与 agent 不在同一服务器”的问题。
- 该账号 ACTIVE Agent binding 已位于 `dev-192-168-8-100`，8 个个人 runtime workspace 却仍记录为本机或空服务器；6 条自动搬迁已连续 12～13 次进入 `RETRY_WAIT`。

### What

- 更新 `AGENTS.md`、AI 工作流、自检清单、后端 README 和 restart skill：默认 `test` 验收固定读取 `.env.test` 的 `192.168.8.100:15432/testagent_dev`；不主动切本机库或跨长期分支同步；进入对话前同时校验工作区/Agent 同服和目标物理根目录可用。
- 按用户明确授权，在 `.100` 单事务把 `usr_test_dev` 的 8 个个人 runtime workspace 归属改为当前 ACTIVE binding 的 `dev-192-168-8-100`，并将 6 条旧搬迁记录标记为 `CANCELLED`；事务先校验唯一 binding 和 runtime workspace 未被其它个人工作区共享。
- 同步修正本机 Codex 启动 skill；该文件位于仓库外，不随本提交进入 Git。

### How

- 完整执行 JDK 25 的 `./restart-dev-services.sh --profile test --env-file .env.test`，后端 26 模块和前端/用户手册 production build 成功，后端 readiness 为 `UP`、前端 3000 返回 200、登录 CORS 预检正常；因 `.env.test` 指向远端 OpenCode，本机 manager 按设计跳过。
- 浏览器真实提交“只回复验收通过”的 Run：同服 `CONFLICT` 已消失，但远端返回 `VALIDATION_ERROR: 工作区根路径不可用`。通过 `.100` 服务器终端只读核对，应用公共仓库存在于 `/data/.testagent/agent-opencode/workspace/appworkspace/20260813/codex-e2e-test-work-20260813`，个人分支、个人 worktree 和数据库旧基线提交 `5c0c2591...` 均不存在。
- 回顾全部 `.agents/session-log*.md` 近期条目，未发现与本次文档和环境决策冲突；未修改 `.env*`、代码、HTTP API、RunEvent、数据库结构/Flyway、generated SDK 或 OpenCode 源码。

### Result

- `.100` 中账号工作区服务器归属元数据已经统一，旧自动搬迁不再继续重试；但真实对话尚未恢复，因为 `.100` 缺少个人物理 worktree。仅改 `linux_server_id` 不能视为搬迁成功。
- 下一步需要用户明确选择远端重建基线：保留本机旧 worktree 不覆盖，在 `.100` 以当前应用提交新建个人分支/worktree，或提供需要保留的本机个人提交并完成真实搬迁；完成后必须再次真实发送 Run 验收。

## 2026-08-18 - 修复企业 ClickHouse 回填已完成仍卡住

### Why

- `.4` 已完成 `analytics-v1` 回填后，企业部署仍卡在 `Starting idempotent ClickHouse analytics backfill`。旧入口只消费本次 systemd journal cursor；企业旧 systemd 漏掉瞬时完成行时，即使 Java 已从 PostgreSQL cutover 幂等返回 `VERIFIED`，Shell 仍会等待到超时。

### What

- `run-analytics-clickhouse-backfill.sh` 新增本机 `0600` 完成状态，只接受固定版本、`analytics-v1` 和 `VERIFIED`；后续平台包命中状态时直接跳过 Runner 和额外 Java 重启。
- 兼容旧包已成功但尚无状态文件的节点：仅从 Java `logs/backend.log` 认领带 `verified=true` 的完成行并补登状态；首次执行同时消费 journal cursor 与本次滚动日志字节偏移，避免再次漏信号。
- 未知、重复或符号链接状态失败关闭；`verified=false` 不得生成状态。失败、超时和中断仍先恢复持久开关为 `false` 并重启普通 Java。
- 同步 ClickHouse 企业部署、多后台和部署入口文档，补充旧包卡住时的 `Ctrl+C` 恢复与无 `rg/jq/psql` 验收命令。

### How

- `tools/verify-internal-analytics-backfill-deploy.sh` 覆盖首次成功、状态跳过、旧日志认领、journal 丢行时的文件兜底、`verified=false` 拒绝、失败回滚、重复配置、未知状态和密钥不泄漏。
- 部署脚本语法及行为、双后台入口、完整平台包、增量组件和开发脚本回归全部通过；开发脚本首次使用默认 `19070` 时被本机已有 mock 服务占用，改用空闲 `19079` 完整复跑通过。
- 拉取并核对 `origin/release`，修复前本地与远端均为 `c39e039bcdf74c375e9e1fbd7593ed871c07c65b`；提交前回顾全部 `.agents/session-log*.md`，未发现冲突或残留合并标记。

### Result

- 已完成的 `.4` 不会再次同步 ClickHouse；新包首次部署会从旧成功日志补登状态，此后增量发布只做普通平台升级，不再额外重启 Java。
- 不修改 Java、HTTP API、RunEvent、数据库结构或 Flyway migration，不触碰 CK/PG 数据、manager、worker runtime、toolbox、generated SDK、`.env*` 或 OpenCode 只读源码。

## 2026-08-18 - 修复固定 CPU BGE 无法通过平台 EMBEDDING 探测

### Why

- 企业内部按稳定文档配置固定 CPU BGE 后，页面 EMBEDDING 探测仍失败。Java 探测遵循企业模型契约发送 `Auth-Token`，而自研 BGE 服务只接受 Bearer；同时 BGE 强制要求 `X-Embedding-Input-Type`，Java 固定样本探测又未发送该头，因此现有版本按正确模型 ID、Token 和 Base URL 也无法成功。

### What

- CPU BGE 认证同时兼容运维 `Authorization: Bearer` 与平台 `Auth-Token`，两者复用同一个 `embedding.env` API key；缺失、错误或同一请求同时携带两种凭据均失败关闭，Token 不进入日志或响应。
- Java EMBEDDING 能力探测固定发送 `X-Embedding-Input-Type: query`，继续复用公共 `Auth-Token`、UCID、trace 和上游错误脱敏程序，不按 Provider ID、模型 ID 或 Base URL 写特殊 Bearer 分支。
- 补充 Python/Java 安全回归并修正一条仍期待 Bearer 的陈旧网关测试；同步模型网关、CPU BGE、HTTP API 与记忆部署文档。

### How

- `embedding-service` 全量 pytest 5/5 通过，覆盖 Bearer、平台 `Auth-Token`、错误 Token、双认证头拒绝、缺少输入类型和固定模型校验。
- JDK 25 下 `mvn -q -DappLogDir=target/log -pl test-agent-model-gateway -am test` 通过；模型网关模块 19/19，通过测试确认探测 URL、`Auth-Token` 和固定 `query` 头。
- 使用根目录 `.env.test` 的 `192.168.8.100:15432/testagent_dev` 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`；后端 26 模块构建成功、readiness 为 `UP`、前端 3000 返回 200，Java 与 `.100` PostgreSQL 建立真实连接。

### Result

- 固定 CPU BGE 与平台模型目录的认证和向量语义契约已对齐；升级 BGE 镜像与 Java 后，正确配置的 `memory-bge-small-zh-v1.5` 可进入真实 EMBEDDING 探测。
- 本次不修改响应 DTO、RunEvent、数据库/Flyway、部署拓扑、环境变量、generated SDK 或 OpenCode 只读源码。企业 `.160` BGE 镜像、双后台 Java 和离线内外层 ZIP 尚未重建/部署，现场真实探测仍需按企业包流程完成最终验收。

## 2026-08-18 - 重建企业平台增量包与独立 BGE 节点包

### Why

- CPU BGE 认证契约和 Java EMBEDDING 探测均已修复，企业交付必须同时更新双后台 Java 与 `.160` BGE；BGE 大镜像不得并入前后台完整包。

### What

- 以运行时代码提交 `ffb1af54cfde78bd315387caf91ce27eb9d1282c` 重建平台内层 ZIP和固定双后台外层 ZIP；复用既有 `.4/.114/.2` 节点包，组件清单继续把 worker runtime、toolbox、本地客户端标为 `reuse`，memory/BGE 标为 `disabled`。
- 单独构建当前 `linux/amd64` BGE 镜像，并只封装 BGE 镜像、平台报告、SBOM、模型身份、Embedding 源码锁、配置模板和部署脚本；独立 ZIP 不含 Mem0、pgvector 或 memory VIP 镜像，平台外层 ZIP 也不含 BGE 镜像。

### How

- BGE pytest 5/5、JDK 25 模型网关 reactor、记忆离线包门禁、ClickHouse 完成门禁和增量组件门禁通过；前端 production build、后端打包、内层后台/前端 `validate-only`、完整外层包验证和全部 JAR Flyway 字节门禁通过。
- BGE 包内模型身份固定为 `BAAI/bge-small-zh-v1.5`、revision `7999e1d3359715c523056ef9478215996d62a620`、512 维、归一化；Docker archive 实际平台核对为 `linux/amd64`。
- 平台内层 SHA-256 为 `3bbe9691a18c1e558d31612797b67c6e1a1eac805a13f992ee599a0dfc6e463c`，外层为 `6e9bb51c4fb4bf0d5cd7949ae84ff6e30544e9a2c933f642107d89f55681b476`，独立 BGE ZIP 为 `5c2c44bbea9700ac70847f290c6d9e996927fbb0ba3feee7ce8f8e4685a47f01`，其中 BGE 镜像 tar 为 `65252a183e6108844c3813f368fa3d763b8d55ff5f0347df4433427efc833dd7`。

### Result

- Mac 交付物已分别落到 `/Users/kaka/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip` 与 `test-agent-bge-node.zip`，均有同名 `.sha256`；两条包边界已解包验证。
- 本轮未新增或修改数据库/Flyway、RunEvent、部署拓扑、manager、worker、toolbox、本地客户端或 `.env*`。现场仍需依次升级 `.160` BGE、`.4/.114` Java 和 `.2` 前端并完成真实 EMBEDDING 探测；打包时已有的模型网关 README 未提交说明保持原样，未纳入本次暂存。

## 2026-08-19 - 自动化代码库改为应用级只读 Reference

### Why

- 自动化仓库原先和测试工作空间处于同一切换维度，会创建个人 worktree，也无法按应用统一激活版本；用户要求复用应用资产库的 Reference 边界，并进一步把目录/版本配置从“工作空间管理”移到工作台“引用配置”。

### What

- 新增 `automation_workspace_active_versions` 及 MyBatis XML 仓储，首版本自动激活、存量选择最新 ACTIVE、后续由管理员显式切换；共享副本继续复用 `application_workspace_version_replicas`，历史个人 worktree 和会话保留但退出正常选择、创建与 Git 入口。
- 组合文件视图新增自动化根和逻辑定位器，逐次校验应用成员、仓库类型、激活版本、副本与安全相对路径；只开放浏览、读取、分片、下载和加入对话，Run 出站上下文追加应用级只读引用说明且不写用户消息。
- 工作台“引用配置”新增“自动化代码库”页签，管理员可选择任意分支、任意已有目录、引用名称和日期版本，并维护当前版本及启停；普通成员只读查看。设置页“工作空间管理”仅保留测试工作空间，两处复用统一目录树组件但维持各自选择约束。
- 同步后端各模块、frontend/agent-web/user-manual README/PACKAGE，以及 HTTP API、事件/文件 RPC、数据库、模块图和专项测试说明；未修改 OpenCode 源码、generated SDK 或 `.env*`。

### How

- 后端 14 个相关测试类共 362 项通过，0 failure / 0 error / 0 skipped，覆盖激活、目录视图、文件 RPC、Run 上下文、权限与响应脱敏；26 模块跳过测试的完整 Maven package 成功。
- 前端本轮 4 个定向文件 76/76，通过后又对轮询收口用例 3/3 复验；agent-web typecheck、用户手册和 agent-web production build 均通过。此前全量 Vitest 暴露的两个设置页定位断言已修正并由定向用例覆盖，但修正后未再次执行全量前端套件。
- 根目录 `.env.test` 的 PostgreSQL `192.168.8.100:15432/testagent_dev` 已真实执行 migration 并稳定启动到 `20260819125704`；JDK 25 下重启 `test` profile 后 health/readiness 为 UP、前端 3000 返回 200、登录 CORS 正常，远端 OpenCode 配置下本机 manager 按设计跳过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录并执行 `git diff --check`；明确排除工作区原有的模型网关 README 与本地客户端打包脚本改动。

### Result

- 自动化仓库不再作为主工作空间或个人 worktree；用户从组合文件树读取应用当前版本，管理员从“引用配置”选择具体目录和维护版本，应用代码库及既有文档引用保持原入口和状态。
- 用户明确自行进行页面验收，因此本轮未代替用户执行登录后的真实目录选择、版本切换和 Run 端到端操作；此前后端完整套件仍有 3 个与本功能无关的日期/fixture/Testcontainers 基线失败，未作为本次功能通过项或顺手修改。

## 2026-08-19 - 补齐自动化代码库多服务器同步交互

### Why

- 首版自动化只读 Reference 只有共享副本广播和单服务器副本状态，没有像应用资产库一样展示“创建同步任务 → 各服务器同步 → 汇总同步结果”，管理员无法确认各在线服务器是否真正收敛。

### What

- 自动化版本新增管理员同步与状态查询 API，复用 `application_workspace_version_replicas`、现有版本同步广播和后端 heartbeat 投影当前在线服务器的 `PENDING/PROCESSING/READY/BLOCKED`，响应只含逻辑版本、branch/commit 和服务器状态；自动化广播只维护共享副本，不再触碰保留的历史个人 worktree。
- 抽取资产库原有三阶段弹层为共享 `RepositoryOperationProgressDialog`，自动化首版本创建、新增版本、激活版本和版本行“同步”均展示真实逐服务器进度、已就绪数、失败重试和 traceId；活动期父级关闭按钮、Escape 与焦点范围同步受控。
- 同步 HTTP API、内部广播说明、后端/前端模块 README 和用户手册；没有新增数据库表、migration、服务、环境变量、个人 worktree 或物理路径响应。

### How

- JDK 25 下 workspace-management 业务测试 104/104、API Controller 测试 28/28 通过；前端相关 3 个测试文件 172/172 通过，user-manual、vue-tsc 和 agent-web production build 成功，`git diff --check` 通过。
- 使用根目录 `.env.test` / `test` profile 完整重启，26 模块后端 package 和前端 build 成功；PostgreSQL `192.168.8.100:15432/testagent_dev` 的 110 条 Flyway migration checksum 校验通过且 schema 已是最新，后端 health/readiness 为 UP、前端 3000 返回 200。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，保留并排除任务外的模型网关 README 与本地客户端打包脚本改动，未发现冲突或残留合并标记。

### Result

- 自动化代码库配置管理现在与应用资产库共用同一套同步进度交互，同时继续遵守应用级只读 Reference、共享副本和无个人 worktree 的边界；真实服务已启动供用户自行执行登录后的业务验收。

## 2026-08-19 - 修复 TCDS 应用完整简称筛选失效

### Why

- 需求导入页应用输入框键入 `f-bas` 时能正确过滤，但补齐最后一个字符成为目录中的完整简称 `f-base` 后，精确命中旁路会误把本轮输入当成既有选择值，导致建议列表恢复全量，表现为筛选不生效。

### What

- 复用现有 `applicationSearchActive` 区分“聚焦展开”和“用户正在输入”，移除输入期的精确命中旁路；用户键入完整应用名称、简称或组合标签时继续按当前文本过滤，聚焦既有选择时仍展示全量建议。
- 新增 `f-bas → f-base` 回归用例并同步 agent-web README；未修改后端、HTTP/WebSocket API、RunEvent、数据库、Flyway、部署、安全、公共 Agent 或 Git diff/提交推送链路。

### How

- 需求导入页定向 Vitest 14/14、agent-web typecheck、用户手册和 agent-web production build 均通过；JDK 25 下 26 模块后端跳过测试 package 也在重启流程中成功。
- 3000 端口保留的当前 Vite 页面通过 Playwright mock TCDS 应用目录完成 UI 级验证：先输入 `f-bas` 再键入 `e`，输入值为 `f-base` 且建议始终只有目标一项。
- 按规范使用根目录 `.env.test` / `test` profile 重启，但固定 PostgreSQL `192.168.8.100:15432/testagent_dev` 当前连接超时，后端未能启动；未切换本机数据库或其它 dotenv，前端 3000 仍可访问。

### Result

- 完整输入应用简称后筛选结果不再重置，最后一个字符与前缀输入的行为一致。
- 本次代码与 UI 行为已验证；后端真实启动和企业 TCDS 联调因共享测试数据库不可达未完成，需要数据库网络恢复后按同一 `.env.test` 命令复验。

## 2026-08-19 - 修复麒麟本地客户端软件包安装失败

### Why

- 麒麟 ARM 用户双击页面下载的 DEB 后，软件中心只提示“软件包操作异常，无法安装或移除软件包”。旧包由 macOS `ar/tar` 手工拼装，并硬依赖 `systemd`、携带跨用户执行 `systemctl/loginctl/runuser` 的维护脚本；在不预装 `systemd` 的 ARM64 Debian 环境中可稳定复现为“已解包未配置”，与现场现象一致。

### What

- 麒麟 DEB 改由 Linux ARM64 容器内的 `dpkg-deb --build --root-owner-group` 生成标准包，移除 `systemd` 硬依赖和全部 maintainer scripts。
- 继续打包 systemd user unit，并用包拥有的 `/etc/systemd/user/default.target.wants/` 软链接保留登录自启与 `journalctl --user` 日志入口；当前会话仍可从应用菜单启动并完成首次配置。
- 本地客户端增量指纹升级并纳入 DEB builder image；补充旧版 `0.1.1` 安装失败后由 `0.1.2` 原地升级修复的真实 `dpkg` 回归，同步模块 README、环境样例与部署文档。

### How

- `deploy/internal/tests/local-opencode-client-package-test.sh` 通过：校验标准 DEB members、无维护脚本、systemd unit/启用软链接，并在 ARM64 Debian 容器中复现旧包失败后安装新版、查询 installed 状态和卸载。
- `tools/verify-internal-incremental-components.sh`、相关脚本 `bash -n`、`git diff --check` 均通过；实际开发包 `TestAgent-Local-Client-Kylin-arm64.deb` 在 ARM64 容器中再次完成 `dpkg -i/remove`。
- 当前 3000 页面用麒麟 UA 访问下载入口返回 302 到 DEB，直链返回 200、MIME 为 `application/vnd.debian.binary-package`、大小 83097016 bytes；开发包 SHA-256 为 `0eac8a57568cd8c47416a0bbbaaaf2938fc723eb8ee434e28acc6170995bc18c`。

### Result

- release 分支的麒麟图形安装包已消除可复现的软件中心依赖/维护脚本失败路径，旧的未配置 `0.1.1` 可被更高版本 `0.1.2` 直接覆盖修复，页面现已提供该开发验证包。
- 本次不修改 API、RunEvent、数据库/Flyway、OpenCode 源码或运行时轮询策略；移除安装期特权脚本降低安装风险且不增加客户端性能开销。真实麒麟桌面软件中心的最终双击安装仍需用户重新下载后确认；正式企业发布还必须使用正式签名材料重新产出制品。

## 2026-08-19 - 修复自动化代码库同步阻塞与多仓库状态串用

### Why

- 用户提供的 `trace_4b29e2e380314405af853f760d6885a3` 不在当前本地日志，原远端 PostgreSQL 也已不可达；通过现有实现和本地真实页面复现确认，同步 HTTP 在线程内执行 Git clone/fetch，慢仓库会让弹窗长期停在“创建同步任务”。
- 自动化引用页面把同步中状态、激活版本和服务器 Git 指针当成全局状态复用，切换两个仓库时会串用；版本历史按钮又缺少 `type=button`，会额外提交新增版本表单。窄窗口下目录区还会把版本配置区压缩到约 33px。

### What

- 新增模块内 `ManagedWorkspaceReplicaTaskDispatcher` 有界后台队列：同步入口和广播消费者先保存本机 `SYNCING` 占位并立即返回，clone/fetch 后台执行；同一 `versionId` 重复唤醒合并，不同目录配置独立，失败只落稳定脱敏文案。
- 自动化引用前端按每个仓库自己的激活版本读取同步状态、就绪数、Git 指针和版本标签；同步不再全局锁住其它仓库。版本同步、激活和启停按钮显式使用 `type=button`，窄窗口目录与配置区按比例分行且独立滚动。
- 同步 workspace-management/agent-web README、HTTP API 和内部事件说明。API 路径、请求和响应结构未变；没有新增数据库结构或 Flyway，也没有创建个人 worktree。

### How

- JDK 25 下 `ManagedWorkspaceApplicationServiceTest` 105/105、调度器 1/1、`ManagedWorkspaceControllerTest` 28/28 通过；前端全量 Vitest 132 个文件、2042 passed / 1 skipped，agent-web 类型检查和 production build 成功。
- 用户明确授权后把忽略提交的 `.env.test` 中 PostgreSQL、Redis、ClickHouse、XXL-Job MySQL 全部切到本机容器；四个容器均启动，PostgreSQL 历史库 109 条 Flyway 记录、0 失败，`automation_workspace_active_versions` migration 在源码和最终应用嵌套 JAR 的 SHA-256 均为 `a331d8b09575fae38b61471fca719af628ac898c01be82fc369c510de2772928`。
- 使用最终代码重启 `test` profile；backend health/readiness 为 UP、frontend 3000 为 200、CORS 正常。浏览器真实验证 A 的 `20260820/feature_image` 同步约 5 秒收敛到 1/1 并激活，B 保持 `20260819/master`；切到 B 后弹窗、版本历史和 Git 指针不含 A 状态，工作台同时展示两个自动化引用并成功读取 `Main.groovy`。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录并执行 `git diff --check`；保留并排除任务外的 `backend/test-agent-model-gateway/README.md`。

### Result

- 自动化代码库同步请求不再被慢 Git 阻塞，两个仓库的同步与激活状态相互隔离；刷新后的组合文件树展示激活版本，目录选择和版本操作在窄窗口也可用。
- 当前本地 manager 已连接，但验收账号仍绑定历史容器身份，因此聊天区提示需初始化 TestAgent；这不影响本次已完成的自动化引用同步、切换、目录展示和文件读取 E2E，未通过修改服务器归属元数据规避该历史状态。

## 2026-08-20 - 修复批量案例部分失败后无法关闭

### Why

- 批量生成案例遇到单文件超过 80,000 字符等准备失败时，失败项不会创建 Session；用户关闭进度页会触发二次确认，但批量弹层固定为 `z-index: 2200`，Element Plus `MessageBox` 默认 Teleport 到 `body` 且层级较低，确认框被遮住，表现为窗口无法关闭。

### What

- 继续复用现有 `ElMessageBox.confirm`，为批量关闭确认增加专属 `modalClass`，并用全局样式把确认遮罩固定到 `z-index: 2300`；不改变运行中禁止关闭、未创建 Session 才确认以及确认后重置批次的既有逻辑。
- 回归测试锁定专属覆盖层类；用户手册补充批量资料沿用的前端容量规则：单文件 80,000、单子条目合计 120,000、生成要求 20,000 个字符，均不是模型 Token 限制。

### How

- 前端全量 Vitest 132 个文件通过，2042 passed / 1 skipped；最新代码定向复跑 4 个批量相关文件，182 passed / 1 skipped。全 workspace typecheck、用户手册及 agent-web production build 通过，并确认产物 CSS 包含专属覆盖层的 `z-index:2300!important`。
- JDK 25 下根目录重启脚本完成 26 模块 Maven package 和最新前端 build；前端 `http://127.0.0.1:3000` 返回 200。后端因当前 `.env.test` 的 ClickHouse `testagent` 账号认证失败而未启动，未修改环境文件或切换 dotenv 绕过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；工作区同期存在其他开发者持续修改的自动化 Reference 后端与前端文件，本次只暂存批量关闭相关代码、测试、手册和本日志，不覆盖或夹带并发成果。

### Result

- 批量案例部分失败且执行已结束时，关闭操作会在批量弹层上方展示可点击的二次确认；选择“仍然关闭”可正常结束批次，取消仍保留进度页。
- 纯前端交互修复，不涉及 HTTP API、RunEvent、数据库/Flyway、部署拓扑、性能、安全、generated SDK、OpenCode 源码或环境配置；真实后端联调受 ClickHouse 认证阻塞，当前为部分运行验证。

## 2026-08-20 - 收敛 dev 与 release 分支选择规则

### Why

- 用户明确长期分支只以“是否需要新增部署节点”为判据：仅新增部署节点时使用 `dev`，其它改动使用 `release`；原规范把大功能、服务、容器、中间件、强制配置、Flyway migration 和部署流程变化也默认归入 `dev`，与新决策冲突。

### What

- 在 `AGENTS.md`、研发工作流入口、详细工作流和完成前自检清单中统一规则：交付必须新增独立部署节点才进入 `dev`；没有明确新增部署节点需求时进入 `release`。
- 明确功能规模、跨模块范围、API/事件/数据库风险，以及现有节点内新增服务、进程、容器、中间件、端口、配置、migration 或调整部署流程均不单独触发 `dev`，但仍执行对应部署、升级、回滚、兼容性和真实环境验证门禁。

### How

- 检索并移除稳定规范中“大功能默认 dev”“release 只接受 Bug/小功能”“Flyway 或强制配置默认 dev”等冲突表述；保留长期分支禁止自动切换、跨分支仅显式同步和 `dev` 功能选择性提升的既有安全门禁。
- 回顾全部 `.agents/session-log*.md` 近期记录，确认本次只修改分支规范和本日志，不覆盖或夹带工作区已有的前后端并行改动。

### Result

- 后续任务默认在 `release` 开发；只有现有部署节点无法承载且交付前提是新增节点时，才使用 `dev`。本次纯文档变更，不涉及 API、RunEvent、数据库/Flyway、部署拓扑、性能、安全、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-20 - 修复应用资产库 Git 超时后无法终止或重试

### Why

- 引用资产库 Git 命令超时后，副本会进入 `RETRY_WAIT`，但仓库总体仍是活动状态；原进度弹窗只允许关闭或重试终态任务，管理员既不能终止旧任务，也不能立即发起新一代重试。
- 调度器只会取消尚未执行的延迟任务，运行中的 Git 线程被中断时也没有显式清理 Git/SSH 子进程，存在后台任务继续占用资源和回写旧结果的风险。

### What

- 新增应用资产库终止接口和 `expectedGeneration` 栅栏：同一事务把活动仓库置为 `FAILED`，把同代待处理、执行中和等待重试副本置为 `BLOCKED`；跨节点广播终止请求，本机调度器可取消延迟任务或中断运行线程。
- Git 执行器在中断时终止 Git 及后代进程、恢复线程中断标记并返回稳定脱敏错误；旧代工作即使晚返回，也会被 generation/lease 条件更新拒绝。
- 进度弹窗为活动任务增加二次确认的“终止”，并在 `RETRY_WAIT` 提供“重试”；直接重试会先终止旧代，再按原操作类型启动新一代同步或指针校验。初始化、同步、切分支和校验均统一进入进度弹窗。
- 同步后端、API、前端包说明、模块图、数据库、安全规范、事件说明和用户手册。

### How

- 在仅包含本次暂存差异的隔离 worktree 验证：前端 2 个 Vitest 文件 170/170、backend-api 与 agent-web typecheck；后端 Git 执行器 8/8、workspace 服务与调度器 59/59、persistence 13/13、API 3/3，全部通过。
- JDK 25 下隔离版本 24 模块跳过测试 package 成功；使用根目录 `.env.test` / `test` profile 启动，临时关闭当前认证失败的 ClickHouse 后，后端 `18081` health/readiness 均为 `UP`，前端 `3001` 返回 200，CORS 预检通过。未修改任何 `.env*` 文件。
- 主工作区按标准重启时被同期未暂存的 Agent 配置改动形成的 Spring Bean 循环依赖阻断；隔离版本没有该循环，说明不属于本次提交。提交前回顾全部 `.agents/session-log*.md` 近期记录，并只暂存本次资产库修复。

### Result

- 应用管理员和超级管理员可终止超时或仍在运行的资产库操作，并从等待重试状态立即重启新一代任务；数据库栅栏是最终一致性保障，跨节点中断为尽力而为的资源回收。
- 新增一个向后兼容的内部 HTTP API 和内部服务器广播，不新增 RunEvent/SSE 类型；不涉及数据库结构、Flyway migration、部署拓扑或新的强制配置。终止接口沿用应用管理员鉴权，只返回稳定错误，不暴露 Git stderr、路径或凭据。

## 2026-08-20 - 实现 SkillHub 双来源能力库

### Why

- Skill 能力库需要同时展示 SkillHub 接口目录和用户在平台 push 的资产；外部条目下架后不得继续出现在发现入口，但应用已经引用的历史内容必须可见、可移除。
- SkillHub 请求需要固定认证头，下载接口必须使用受控枚举 `channel=3`；外部 Skill 被管理员拉到个人 worktree 后，原样 push 与修改后 push 需要有不同的来源归属。

### What

- 新增 `SkillHubGateway` 和 HTTP 适配器，目录同步只保存元数据，预览、引用或更新时才下载 ZIP；固定认证头、`SkillHubDownloadChannel.PLATFORM(3)`、连接/请求超时、响应容量、ZIP 路径/数量/UTF-8/根 `SKILL.md`/稳定名称校验均在后端收口，密钥只从环境注入。
- Agent & Skill Hub 增加 `PLATFORM/SKILLHUB` 来源、来源可用性、外部精确版本和派生谱系；远端下架只隐藏发现入口并保留当前应用引用。外部内容原样 push 时引用仍指向外部资产，编辑后 push 在同一 MyBatis 事务中生成未发布的平台派生资产并记录 `forkedFrom*`。
- 前端 Skill 页增加“全部 / 接口文档 / 平台更新”筛选，外部卡片打开不下载，显式预览才物化；下架引用禁止新增/更新但允许移除。同步 HTTP API、事件无新增说明、数据库、部署、安全、模块 README/PACKAGE 和前端类型。

### How

- JDK 25 下 SkillHub persistence/service/API/integration、Flyway 命名和 SQL 约束定向测试全部通过；覆盖目录元数据、下架保留引用、同 ID+版本内容冲突、原样保持来源、修改后平台分叉和 ZIP 路径穿越。
- 前端 `agent-skill-hub` 与 `backend-api` 定向 Vitest 135/135，通过 backend-api 与 agent-web 类型检查；`git diff --check` 和明文密钥模式检查通过。
- 相关模块全量 Maven 回归运行 347 项，在任务外既有基线处失败：模型网关 H2 fixture 缺 `embedding_dimension`，会话分享固定日期已变为 `EXPIRED`；两项与本次文件无关，未顺手修改。
- 按本地启动技能检查后，当前根目录 `.env.test` 指向 `127.0.0.1:15432/test_agent`，与项目固定验收库 `192.168.8.100:15432/testagent_dev` 冲突；遵守规范未修改 `.env.test`、未切换数据库、未执行真实 PostgreSQL migration 或整站重启。

### Result

- 双来源目录、按需物化、下架语义和 push 来源归属已经实现并完成代码级定向验证；新增向后兼容的内部 HTTP 查询参数和物化/手工同步接口、一个 Flyway migration 和部署期可选 SkillHub 配置，不新增部署节点、RunEvent/SSE 类型或 OpenCode 源码修改。
- 真实 PostgreSQL 升级、SkillHub 现场接口联调和整站运行仍未验证；恢复符合仓库约定的 `.env.test` 后需要按 `./restart-dev-services.sh --profile test --env-file .env.test` 复验。

## 2026-08-20 - 修复 Tool TS 更新后 OpenCode 缓存未失效与批量重启进度丢失

### Why

- OpenCode 1.18.4 会在进程内缓存 `tool/tools/**/*.ts/js` 模块；仅调用 `/global/dispose` 可能留下空报文体，而完整受管重启后恢复。
- 公共/应用 Tool 发布缺少面向受影响用户的持久排空、目录巡检和补偿入口；超级管理员批量重启刷新页面后也会丢失客户端游标。

### What

- 公共和业务工作区 Tool 本地保存改为只受管重启当前用户；纯本地 commit 不触发额外用户，公共 push 覆盖全部运行用户，应用 push 只覆盖对应应用版本用户。
- PUBLIC/APPLICATION rollout 持久化配置范围与应用版本，按用户独立等待空闲、受管重启并核对 manager 新代次和 `/experimental/tool/ids` 非空目录；既有 5 秒 drain 调度同时承担失败补偿，一个用户失败不阻断后续目标。
- 超管公共配置页新增“应用更新配置”、未重启用户明细和单用户立即受管重启；运行管理批量操作把目标、游标、PID/startedAt、成功/失败写入当前超管的 localStorage，刷新后核对并继续。
- 批量开始或刷新恢复时自动展开目标容器，在每名用户姓名后以灰/蓝/绿/红圆点直接投影等待、处理中、成功和失败，悬停查看具体状态或错误，不增加表格列；“应用更新配置”改为无 rollout 时也常驻说明单用户受管重启入口。
- 本机启动脚本优先从稳定发布清单解包并校验 OpenCode 1.18.4，避免误用用户目录中的其它版本；同步 HTTP API、runtime/workspace、agent-web 和 backend-api 稳定说明。

### How

- JDK 25 下完整 26 模块 `mvn clean package -Dmaven.test.skip=true` 成功；相关 workspace 172/172、runtime 46/46、persistence 9/9、API 27/27 全通过。前端定向 Vitest 49/49，backend-api 与 agent-web typecheck 通过。
- 本次进度圆点与常驻入口补充 Vitest 40/40、agent-web typecheck 通过；JDK 25、`.env.test`、test profile 和 ClickHouse 完整重启成功，26 模块后端与前端生产构建通过。原生 Python Playwright 使用 Chrome 真实登录超管，浏览器层拦截展示数据验证空状态、单用户按钮、8px 进度圆点、无新增列和完成态，证据保存在 `output/e2e-tool-rollout/09-*` 至 `12-*` 与脱敏 JSON 报告中，不纳入提交。
- 使用根目录 `.env.test`、`test` profile 和 `--with-clickhouse` 启动完整环境；后端 readiness UP、前端 3000、ClickHouse 26.3.17.56，OpenCode `/global/health` 返回 `healthy=true, version=1.18.4`。
- 原生 Python Playwright 登录真实超管页面，真实接口返回 200；在浏览器层仅拦截应用 rollout/重启验收数据，验证未重启用户、人工重启、批量进度刷新恢复及清理。另对当前验收账号执行真实受管重启，PID `18714 -> 26379`、startedAt 更新，重启前后 Tool 目录均非空；截图和 JSON 报告保存在 `output/e2e-tool-rollout/`，不纳入代码提交。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；工作区同期存在其他开发者的 SkillHub、自动化引用和文档改动，本次仅暂存 Tool 重载、rollout、管理进度、相关测试文档与本日志。

### Result

- Tool 模块更新不再依赖用户手工重启；本地保存只影响本人，公共/应用发布按实际影响范围排空并持续补偿，失败按目标隔离。
- 超管能查看应用发布尚未重启/dispose 的用户并人工处理；批量重启刷新后可恢复进度并继续。新增一个向后兼容的内部查询 API，不新增 RunEvent、数据库/Flyway、部署节点、强制环境变量或安全权限，不修改 `.env*`、generated SDK、OpenCode 源码，也未创建分支。

## 2026-08-20 - 统一自动化代码库与应用资产库只读 Reference 流程

### Why

- 自动化配置弹窗把仓库点选、Git 拉取、版本/分支刷新、目录选择和应用混在一起，用户无法判断哪一步会生效；两个自动化仓库切换时还会串用表单状态。
- 引用写入历史 `workspace/.opencode/opencode.jsonc` 后，OpenCode 从工作树根启动时不会加载；自动化引用另由 Java 拼接运行上下文和权限，未复用应用资产库的 JSONC Reference 流程，且环境根目录尾部斜线会让精确 `external_directory` 规则失效。
- 888888888 用户选择应用资产库时误走普通文件 RPC，受保护的 `.opencode` 配置无法读取或保存。

### What

- 自动化代码库改为“选择仓库 → 选择已有版本/分支 → 点选引用目录 → 应用到当前工作树”的显式流程：点选仓库只加载元数据，不触发同步；A/B 仓库状态隔离；只有“更新当前版本副本”才展示真实同步进度。
- 自动化与应用资产引用统一通过 Agent Config WebSocket RPC 读写当前工作树的 `opencode.jsonc`，使用 `{env:OPENCODE_APP_WORKSPACE_ROOT}`、精确版本目录和 `permission.external_directory`；删除 Java 侧自动化 Run 上下文拼接器。
- 工作区组合文件树只展示当前 JSONC 已引用的自动化目录，并使用携带配置 ID、版本 ID 和逻辑路径的只读 locator；保存后立即按选定目录生效。读取兼容历史 `workspace/.opencode`，写入时提升到 OpenCode 会话 cwd 下的标准 `.opencode`。
- OpenCode 启动时复用公共参数注入应用版本根目录，并规范化引用根路径，消除尾部分隔符造成的双斜线和权限匹配失败；应用资产库的控制面请求不再错误依赖个人 TestAgent 路由。

### How

- 后端定向测试：workspace 86/86、runtime 26/26；受影响三模块完整 Maven reactor 通过，API 608 项通过；完整 26 模块跳过测试打包并随启动脚本成功。
- 前端引用配置定向 Vitest 59/59、全量 Vitest 2054 passed / 1 skipped、全 workspace typecheck、agent-web 与用户手册 production build 通过。
- 使用根目录 `.env.test` 的本地 PostgreSQL `127.0.0.1:15432/test_agent`、Redis、XXL MySQL 和 Docker ClickHouse 启动 `test` profile；Flyway 校验 110 条历史并确认源码与打包 JAR 中 108 个 migration 字节一致。
- 真实浏览器以 888888888 验收应用资产 `docs` 选择、自动化 A/B 独立切换、A 的 `master / 20260819 / src` 应用和组合文件树；新对话读取 `Main.groovy` 返回 `Matthew`，Run 为 `SUCCEEDED`，未出现目录权限申请。日志确认精确 external_directory 规则判定为 allow。

### Result

- 用户只需点选仓库、版本和目录后“应用到当前工作树”；仓库点选/分支目录刷新不再伪装成 Git 拉取，只有显式更新副本才同步。应用资产库重新可选，自动化引用与应用资产库使用同一 JSONC 配置和权限流程。
- 本次跟进不新增数据库结构、Flyway、RunEvent 或部署节点；既有激活版本内部 API 保持兼容。未修改 `.env*`、generated SDK 或 OpenCode 源码，未创建分支。

## 2026-08-20 - 移除文件树引用不可用提示栏

### Why

- 组合文件树顶部的“引用不可用 + 刷新”提示栏与目录树状态重复，且按钮无法修复已经失效的引用配置，用户要求直接移除。
- 当前 F-COSS 工作树仍配置 `docs-appdocs`，但应用已不再关联英文名为 `appdocs` 的资产库，因此服务端实时归属校验返回“引用资产库不存在”。

### What

- 删除工作台到文件树的引用告警聚合、属性传递、提示栏视图和样式；引用不可用时仅跳过对应目录，不影响工作区和其它可用引用。
- 删除不再使用的前端告警聚合 helper 与对应测试，补充提示栏不会重新出现的源码断言。
- 同步 agent-web README、引用配置用户手册和周更新说明；未自动修改用户工作树里的旧 `docs-appdocs` 配置。

### How

- 定向 Vitest 2 个文件 35/35 通过；agent-web production build（含用户手册构建和 `vue-tsc`）通过。
- 本地 `test` 后端 health/readiness 均为 UP，现有 Vite 前端 `http://127.0.0.1:3000` 返回 200，并持续加载本次源码改动。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；未纳入工作区既有未跟踪验收产物 `output/e2e-tool-rollout/`。

### Result

- 文件树不再展示引用不可用提示栏和无效刷新按钮；失效引用的根因已明确为工作区 JSONC 中的旧资产库标识，而非当前共享副本同步失败。
- 不涉及 API、事件、数据库、Flyway、部署、性能、安全、generated SDK、OpenCode 源码或环境配置；未创建分支。

## 2026-08-21 - 隐藏普通用户模型选择并增加超级管理员三次 Ctrl 解锁

### Why

- 普通用户不应在对话工作台切换模型；超级管理员仍需保留一个默认隐藏、主动触发后才可用的运维入口。

### What

- 复用现有三次 Shift 快捷键状态机，抽象为可配置的三次修饰键手势，新增三次独立 Ctrl 解锁模型入口。
- `AgentWorkbench` 仅在非分享态 `SUPER_ADMIN` 完成手势后向 `FigmaChatPanel` 开放模型按钮；普通用户、分享成员以及未解锁的超级管理员均不渲染按钮，`/models` 原生命令也同步受门禁保护。
- 增加快捷键、面板、普通用户、分享态和超级管理员解锁测试，并同步用户手册、agent-web README、src 包说明和前端访问边界。

### How

- 目标 Vitest：2056 passed / 1 skipped；`@test-agent/agent-web` typecheck、production build、用户手册 VitePress build 和 `git diff --check` 通过。
- Chromium 缺失后补齐 Playwright 浏览器；模型相关 E2E 中普通用户隐藏、目录恢复和分享态用例通过。另有既有工作区自动化引用对账改动导致的 Run 请求前置超时，普通 `new runs use one in-memory conversation context` 用例也可独立复现，未将该无关问题混入本次修复。

### Result

- 普通用户始终看不到模型选择入口；超级管理员连续独立按三次 Ctrl 后可显示并切换，权限离开或进入分享态时会立即收回入口且需重新解锁。
- 不涉及 HTTP API、RunEvent、数据库、Flyway、部署、性能、安全契约、generated SDK 或 OpenCode 源码；未修改环境配置，未创建分支。

## 2026-08-21 - 模型选择入口支持三次 Ctrl 循环显隐

### Why

- 超级管理员完成三次 Ctrl 后原来只能单向显示模型入口，无法用同一隐藏手势再次收起；用户要求每组三次 Ctrl 在显示与隐藏之间循环切换。

### What

- 继续复用 `createTripleKeyShortcut("Control")` 的三击计数和自动重置能力，仅把工作台触发结果改为对模型入口可见状态取反，不新增监听器或快捷键实现。
- 第二组三击会同时收起已打开的模型弹层和 `/models` 原生命令，第三组三击可再次显示；普通用户和分享态继续始终不可用。
- 补充连续两组三击、超级管理员显示/隐藏/再次显示、模型弹层收起及 `/models` 同步门禁的 Chromium E2E，并同步前端 README、agent-web README/PACKAGE 和用户手册。

### How

- `support-access-shortcut.test.ts` 6/6 通过，覆盖同一 Control 状态机连续触发两轮三击。
- Chromium 聚焦 E2E 3/3 通过，覆盖分享态、普通用户和超级管理员循环显隐；重启服务后复跑仍为 3/3。
- 全前端 `corepack pnpm typecheck`、`corepack pnpm build` 通过；使用 JDK 25、根目录 `.env.test` 和 `test` profile 执行完整重启，最终 backend health/readiness 为 UP、前端 3000 返回 200、登录 CORS 预检通过。

### Result

- 非分享态超级管理员现在每连续独立按三次 Ctrl 都会切换模型入口显隐，隐藏时按钮、弹层和 `/models` 同步收回；模型选择值本身保持不变。
- 不涉及 HTTP API、RunEvent、数据库、Flyway、部署节点、性能、安全契约、generated SDK 或 OpenCode 源码；未修改环境配置，未创建分支。

## 2026-08-21 - 自动化引用重构为应用版本库级共享只读代次

### Why

- 前一版仍以自动化工作空间模板和版本为配置维度，同一应用、同一版本库可能保留多套分支和目录；页面状态、服务器副本与工作树 JSONC 存在多套事实源，首次选择还容易误触发拉取或命中旧分支目录。
- 目标交互应与应用资产库保持同一状态机：一个应用可关联多个自动化版本库，每个版本库只有一个当前分支、目录和描述；服务器维护共享只读副本，用户工作树只通过 `.opencode/opencode.jsonc` 引用当前逻辑代次。

### What

- 新增 `(app_id, repository_id)` 唯一的自动化引用状态、不可变 generation、服务器副本和 Run 租约模型；MyBatis XML 持久化，前向 Flyway migration 从历史多模板/多版本数据收敛最新有效配置，旧模板、版本、副本和个人 worktree 保留但退出正常入口。
- 保存配置时固定 `merge=false`，解析目标分支 HEAD 后同步共享完整仓库副本；在线节点 READY 后以 CAS 激活，离线节点 DEFERRED 后补。目录仅为副本内逻辑选择，不重复 clone；核验只读实际分支、HEAD 和工作树状态。
- 新增应用级列表、保存、同步、核验、终止、状态和远端目录 API；组合文件树 locator 改为应用、版本库、generation 和逻辑相对路径，并继续拒绝 `.git`、符号链接、路径穿越、Git/requirements 及全部写操作。
- JSONC 保持唯一运行时事实源：每个应用自动化版本库只保留一条托管 reference 和精确 `permission.external_directory`，清理同库历史 workspace/version 引用；Java 仅处理副本、安全、文件 RPC 和租约，不向 Run 提示词或消息注入引用信息。
- 自动化配置页复用应用资产库的仓库卡片、READY/FAILED、目标 Git 指针、服务器状态、同步/核验/终止/重试和保存反馈；唯一差异为允许选择任意已有目录。点击仓库和切换分支只更新草稿，保存后才同步并整体生效；description 可编辑且首次自动生成默认值。
- 同步 HTTP API、事件/文件 RPC、数据库、部署、模块图、测试说明、后端/前端 README 和用户手册；删除旧 active-version 正常入口和持久化实现，未修改已执行的 `V20260819125704` 字节、generated SDK 或 OpenCode 源码。

### How

- 前端定向引用测试 23/23、全量 Vitest 2054 passed / 1 skipped、全 workspace typecheck、agent-web 与用户手册 production build 通过。
- JDK 25 下完整 26 模块 `mvn clean test` 为 `BUILD SUCCESS`；persistence 351 项（20 skip）、app 89 项（2 skip）及其它模块全部 0 failure / 0 error。新增真实 PostgreSQL 历史迁移用例验证旧多模板、多分支、多版本、多副本收敛到 `(appId, repositoryId)` 唯一配置；本机 PostgreSQL、Redis、XXL MySQL 和 ClickHouse 集成测试通过。
- 使用根目录 `.env.test`、`test` profile、稳定服务器标识 `kakadeMacBook-Pro.local` 和 `--with-clickhouse` 启动真实后端、manager、前端；readiness UP、工作台 200。真实浏览器以 888888888 验收应用资产库、自动化 A/B 两个仓库、分支草稿、任意目录、Git 指针、服务器 READY、描述、保存按钮和公共 Agent 区域。
- F-COSS 当前工作树 JSONC 对账为 A generation 3 `feature_image/resources`、B generation 1 `master/css` 两条引用及精确只读权限；组合文件树只展示两个当前目录。对话实际读取 generation 3 的 `labels.properties` 得到 `app.title=Our App`，未出现外部目录权限申请；免费模型前期在工作区和旧代次间搜索属于模型路径发现表现，不是 Java 隐式注入或权限失败。
- 提交前回顾全部 `.agents/session-log*.md`，执行 `git diff --check` 并保留其他开发者的公共 Agent、模型入口和 Tool rollout 成果；用户本地修改的 `tools/clickhouse-dev-services.sh` 与未跟踪 `output/e2e-tool-rollout/` 不纳入提交。

### Result

- 自动化引用现在以应用和版本库为唯一配置维度，管理员只需选择版本库、分支、目录并保存；所有成员刷新、重新进入或创建新 Run 前由工作树 JSONC 对账到当前共享代次，运行中的任务继续受旧代次租约保护。
- API 与数据库为增量兼容变更，无新增 RunEvent 或部署节点；共享副本仍为平台层只读边界，不提供 OS 只读挂载。历史离线服务器副本和个人 worktree 不删除，但不能从正常入口继续创建、选择或执行 Git 操作。

## 2026-08-21 - 本地 ClickHouse 启动复用已有固定镜像

### Why

- 本地已经存在项目固定版本 ClickHouse 镜像时，`pull` 和 `restart` 仍访问外部 registry；离线、代理不可用或 registry 抖动时会无意义阻断本地开发环境启动。

### What

- 在既有 `pull_image()` 中复用 `TEST_AGENT_CLICKHOUSE_DEV_IMAGE`，先通过 `docker image inspect` 检查本地固定镜像；命中后直接返回，未命中时继续执行原有 `docker pull`。
- 保留 `start()` 的容器重建、数据卷保留和 readiness 核验逻辑，并同步脚本内置帮助，使 `pull/restart` 的说明与“确保镜像本地可用”语义一致。

### How

- 执行 Shell 语法检查、开发脚本校验、真实 `pull` 缓存命中和 ClickHouse 鉴权状态检查；确认已有固定镜像不访问 registry，运行中容器仍可用。
- 提交前回顾全部 `.agents/session-log*.md`；未纳入未跟踪的 `output/e2e-tool-rollout/` 本机验收产物。

### Result

- 本地已有固定 ClickHouse 镜像时可直接完成 `pull/restart` 的镜像准备阶段；首次使用或镜像缺失时仍按原流程拉取，不改变版本固定、容器所有权、数据卷、安全配置或部署拓扑。

## 2026-08-21 - 忽略本机 E2E 验收产物

### Why

- `output/e2e-tool-rollout/` 只保存本机浏览器验收截图和 JSON 报告，不是源代码、测试 fixture 或发布资产；每次验收都会产生新的未跟踪文件。

### What

- 在现有 `output/playwright/` 忽略规则旁新增 `output/e2e-tool-rollout/`，保留本地证据文件但不进入 Git 提交。

### How

- 使用 `git check-ignore -v` 验证目录及文件命中仓库规则，并执行 `git diff --check`；未删除或改写任何 E2E 产物。

### Result

- 后续本机 E2E 运行结果不会污染 Git 状态，也不会被误提交；需要交付证据时仍可从本地目录查看。

## 2026-08-21 - 清理自动化旧分支并区分只读引用颜色

### Why

- 用户要求在拉取远端最新 `release` 后复核旧自动化工作空间/激活版本逻辑是否真正退出，并确认自动化文件能由对话直接读取。
- 自动化只读引用此前继续复用应用资产引用的蓝色语义，文件树和配置页无法直观看出来源差异。

### What

- 审计确认旧 active-version Controller、Service、Mapper、Run 上下文注入器及个人自动化 worktree 正常入口已删除；移除测试工作空间版本创建流程中三处因入口前置拒绝自动化仓库而永远不可达的二次判断，并清理 `backend-api` 包说明中已不存在的 `activateAutomationWorkspaceVersion` 方法名。
- 保留已执行且 checksum 冻结的 `V20260819125704`、旧表迁移 fixture，以及阻止历史自动化工作区继续被选择、同步或执行 Git 的兼容守卫；这些属于升级输入和安全边界，不是当前配置事实源。
- 自动化文件树、仓库卡片和目录选择改用紫色，应用资产合并引用继续使用蓝色；补充来源语义类单元测试，并同步安全规范、前端包说明、用户手册和 README。

### How

- 全前端 Vitest 134 个文件通过：2070 passed、1 skipped；`@test-agent/agent-web` production build（含用户手册和 `vue-tsc`）成功。后端 workspace 定向回归 147/147 通过，覆盖应用自动化配置、Run generation 租约、组合文件树、历史工作区鉴权和测试工作区版本流程。
- 使用 JDK 25、根目录 `.env.test`、`test` profile、`kakadeMacBook-Pro.local` 和 ClickHouse 完整重启；后端 readiness 为 UP、前端返回 200、PostgreSQL/Redis/XXL MySQL/ClickHouse 容器与 manager 正常运行。
- 888888888 真实工作台显示两个紫色自动化目录；未把文件加入对话上下文，直接要求读取 A 仓库 generation 3 的 `labels.properties`。OpenCode 日志记录目标 `read` 与 `external_directory=allow`，无授权弹窗；确认 Run 返回 `app.title=Our App` 并以 `SUCCEEDED` 结束。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，执行旧符号/端点扫描、冻结 migration SHA-256 对照、`git diff --check` 和服务健康检查；未覆盖其它开发者成果。

### Result

- 当前运行时只以 `(appId, repositoryId)` application automation reference generation 和各成员工作树 JSONC 为事实源；Java 不再向 Run 注入引用说明或物理路径。
- 自动化来源使用紫色，应用资产 docs 来源保持蓝色。对话读权限和实际内容读取已通过真实端到端验证。
- 本次不新增 API、RunEvent、数据库结构、Flyway 或部署节点；未修改 `.env*`、generated SDK、OpenCode 源码或冻结 migration，未创建新分支。

## 2026-08-21 - 合并 dev 并复核自动化引用最终口径

### Why

- 用户明确要求先把远端最新改动纳入 `dev`，再把 `dev` 合并进 `release`；自动化版本库仍以本次对话最终确定的“应用 + 版本库”共享只读引用为准，不能被旧 workspace/version 实现覆盖。

### What

- 将 `origin/dev` 合入本地 `dev` 后再合入 `release`；冲突解决保留自动化共享只读引用、JSONC 唯一运行时事实源和紫色来源语义，同时纳入 `dev` 的工作台多功能页标签与 Markdown 思维导图能力。
- 修正合并后受控系统页签测试和用户手册精确文案，并把自动化 JSONC 清理、组合文件树定位器的维护说明统一为 `(appId, repositoryId, generation)`，不再使用旧 workspace/template 身份口径。

### How

- 自动化后端定向 Maven reactor 构建成功；真实 PostgreSQL 历史迁移、MyBatis XML 持久化及完整 Flyway 多历史兼容测试共 33 条通过。
- 前端全量 Vitest 143 个文件通过，2161 passed、1 skipped；用户手册和 agent-web 生产构建成功。提交前回顾全部 `.agents/session-log*.md` 近期记录，扫描未解决冲突并执行 `git diff --check`。
- 使用 JDK 25、根目录 `.env.test`、`test` profile 与 ClickHouse 完整重启；backend readiness 为 UP，frontend `127.0.0.1:3000` 可访问，opencode-manager 已重新启动。
- 浏览器以 888888888 验收应用资产库可选、自动化 A/B 卡片、目标与逐服务器 Git 指针、任意目录树和描述字段；点选仓库只切换查看，没有启动同步。工作树只显示 A/B 两条托管引用，自动化根计算色为 `rgb(109, 40, 217)`，`labels.properties` 展示 `app.title=Our App`，Monaco 输入 textarea 为只读；公共 Agent 树也正常加载且控制台没有前端 error。
- 首轮启动因本机 `hostname` 返回 `bogon`，与本地持久库中 888888888 的稳定 binding `kakadeMacBook-Pro.local` 不一致，真实返回 `OPENCODE_UNAVAILABLE`。未改数据库归属，而是显式使用已有稳定 server ID 和 `127.0.0.1` advertised host 重启，随后进程、工作版本、应用资产和自动化树全部恢复；后续本机验收不能依赖易变 hostname。

### Result

- `release` 同时包含最新 `dev` 能力和本次自动化引用最终实现，冲突文件中没有重新引入个人自动化 worktree、Java Run 上下文注入或按 workspace/version 选择的正常入口。
- 本地数据库确认 `app_fcoss` 仅有两个 `(appId, repositoryId)` 当前状态，分别为 `feature_image/resources` 和 `master/css`，旧 automation application workspace 启用数为 0；个人工作树 JSONC 也只有这两条自动化引用和两条精确外部目录权限。
- 审计保留一项需后续明确的安全/交互口径：新展开目录只接受当前 generation，但已打开只读标签为满足“切换后继续读旧内容”会直接解析任意仍为 READY 的历史 generation；这与稳定安全文档写的“当前或 Run 租约保护”不完全一致。若严格拒绝伪造历史 locator，需要新增标签级服务端租约或接受切换后旧标签失效，不能只靠现有无状态 locator 同时满足两者。
- 本次合并没有新增部署节点；保留已有 API、数据库迁移和安全边界，未修改 `.env*`、generated SDK、OpenCode 只读源码或用户未跟踪的 `.reasonix/`。

## 2026-08-22 - 以 dev 客户端为基线完成自动化引用合并与真实验收

### Why

- 用户要求重新拉取远端并把最新 `dev` 合入 `release`：客户端核心逻辑以 `dev` 为准，`release` 的客户端增量继续叠加；自动化版本库必须保留 `release` 已确认的“应用 + 版本库”共享只读引用模型。
- 本地持久 PostgreSQL 已执行自动化引用 migration，但尚未执行版本号更低的两条客户端版本管理 migration，不能通过改名、repair、outOfOrder 或重建数据库掩盖真实历史。

### What

- 合并 `origin/dev@011f48d18`，保留麒麟 ARM64 注册、认证、签名版本与静默更新客户端主链，并叠加 `release` 的托盘、状态、重连和增量交付；自动化继续使用 `(appId, repositoryId, generation)`、共享只读副本和 JSONC 唯一运行时事实源。
- 为已部署自动化 migration 之后的本地客户端版本管理历史增加受 checksum 严格约束的 compatibility location 和更高版本前向 migration；没有修改已执行 migration 的文件名或字节。
- 自动化副本 Git 命令对引用物理根路径统一脱敏；Agent/Skill 后台投影跳过已禁用的历史自动化 workspace，旧个人 worktree 仍只作为历史数据保留，不再进入正常读取、选择、同步或 Git 扫描入口。
- 修复登录后公共个人 worktree 尚未挂载时 Git 变更面板发送空 `worktreeId` 的瞬态 400；现在先保持公共 Agent 空态，身份到达后复用既有 watcher 刷新。

### How

- 后端全量 Maven 测试 26 个模块全部成功：app 93（2 skipped）、persistence 358（20 skipped）、workspace 485、API 627，零失败；兼容迁移定向测试 31/31、命名测试 15/15，真实 PostgreSQL 从已部署自动化历史只执行新的前向 migration。
- 前端全量 Vitest 147/147 文件通过，2194 passed、1 skipped；用户手册、`vue-tsc` 与 agent-web 生产构建成功。客户端打包/更新和内部 nginx、增量、多后端、开发脚本验证均通过。
- 使用 `.env.test`、JDK 25、`test` profile、PostgreSQL/Redis/XXL MySQL/ClickHouse 和真实前后端启动。888888888 浏览器端验证应用资产库可选、自动化 A/B 独立配置、任意目录、Git 指针核验、公共 Agent 树及紫色自动化来源；页面最终 0 个 console error。
- 真实对话 Run `run_55e8f2b7e675487eaf580afec9fb28b4` 未手工加入文件上下文，直接读取自动化 B 的 `default.css` 并正确返回 `font-size: 16px`、`h1-h6: #333333`，无外部目录授权弹窗。新启动后的指针核验日志仅出现 `<redacted-local-path>`，没有自动化副本物理根路径，也没有历史 automation worktree 后台 Git 扫描。

### Result

- 远端 `dev`、当前 `MERGE_HEAD` 均为 `011f48d18`，远端 `release` 为 `8385dbea9`；本次冲突解决满足“客户端以 dev 为基线、自动化以 release 为准”。
- 自动化用户流程稳定为选择版本库、选择分支、选择任意已有目录、按需修改描述后保存生效；点选仓库和刷新指针不会拉取，只有保存配置或“更新副本”创建同步任务。
- 本次涉及既有客户端/API/数据库兼容和日志脱敏，不新增部署节点，不修改 `.env*`、generated SDK、OpenCode 源码或已执行 migration，不触碰用户未跟踪的 `.reasonix/`。

## 2026-08-22 - 修复自动化引用运行竞态、历史标签租约和页面回归

### Why

- 多轮合并后的自动化主模型已经是 `(appId, repositoryId)` 共享只读引用，但审计发现新 Run 的 JSONC 对账、generation 生命周期、历史标签读取和页面重试仍有竞态或回归缺口；重复刷新文件树还会持续新增 12 小时标签租约。
- 用户要求按最终口径修复、使用本地真实数据平面完成端到端验收，并确认旧个人 worktree/active-version 正常入口已经退出；本次只做本地提交，不推送。

### What

- Run 在持久锚点和 owner fencing 建立后才锁定精确自动化 generation，并在所有终态/启动失败路径释放；派发前通过既有工作区文件 RPC 对账 `.opencode/opencode.jsonc` 并走空闲 reload，不向提示词、用户消息或 OpenCode 出站上下文注入引用说明或物理路径。单库副本不可用时删除该库 JSONC 托管引用、返回安全局部告警并继续主 Run；同一 `clientRequestId` 幂等重试不再触碰工作树配置。
- 新增服务端历史只读标签租约，令牌绑定用户、工作区、应用、版本库和 generation，数据库仅保存 SHA-256；generation 退役与 Run/标签租约使用同一行锁串行化，无租约的旧代次才会退役并安全清理本机共享副本。文件树重建对同一有效租约做有界进程内复用，避免每次刷新写库。
- 自动化定位器、WebSocket RPC 和前端共享类型携带标签租约；继续拒绝伪造 locator、路径穿越、符号链接、`.git`、搜索、requirements、标准 Git 和全部写操作。旧 active-version 生产 Controller/Service/Mapper 保持删除，仅保留冻结 migration 和新模型迁移输入；历史个人 worktree 只保留兼容只读守卫。
- 前端对自动化列表兼容数组及滚动升级残留的 `{data: []}`，修复应用资产库选择回归；自动化 `RETRY_WAIT` 与资产库一致，先终止旧 generation 再精确重试。两类页面复用同一操作状态函数；自动化文件树和仓库/目录提示使用紫色，应用资产 docs 保持蓝色。
- 更新 HTTP API、事件/文件 RPC、数据库、安全、测试说明、各后端模块 README、前端 README/PACKAGE 和共享类型说明，并把旧自动化个人 worktree 的真实记录明确标为已退出的历史追溯。

### How

- 后端完整 26 模块 `mvn test` 通过；追加修复后定向通过 `AutomationWorkspaceReferenceCatalogServiceTest` 5/5、`RunApplicationServiceTest#redisSummaryExistingSessionStartsAndCancelsWithoutLegacyWrites` 1/1，以及 H2/真实 PostgreSQL 的 generation 退役、Run/标签租约互斥和历史升级用例。
- 前端全量 Vitest 148 个文件通过：2198 passed、1 skipped；用户手册、`vue-tsc` 与 agent-web production build 通过。两次 JDK 25 完整后端打包成功，最终源码、persistence JAR 和应用嵌套 JAR 的新 migration 字节一致。
- 根目录 `.env.test` 的持久 PostgreSQL 已执行 `20260821113000` 与 `20260822075000` 且均为 success；本地 PostgreSQL、Redis、XXL MySQL、ClickHouse、backend、opencode-manager 和 frontend 全部启动，`dev-health-check` 与 readiness 通过。
- 888888888 真实浏览器验收应用资产库可选、自动化 A/B 各一套当前配置、任意目录、Git 指针、紫色来源、只读打开 B 仓库 `default.css` 并加入对话上下文（18 行/226 字）；公共 Agent Hub 正常展示 14 个 Agent，浏览器 0 error。后端重启后连续刷新工作台 3 次，未过期标签租约保持 `36 → 36`。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，扫描旧符号/端点、冲突标记与 `git diff --check`；未改 `.env*`、generated SDK、OpenCode 源码或冻结 migration，未触碰未跟踪 `.reasonix/`。

### Result

- 自动化引用事实源、页面、共享副本和 Run 生命周期现按最终模型收敛：一个应用可有多个自动化版本库，每个版本库只有一个当前分支、目录和描述；成员共同读取服务器共享副本，个人工作树只保存 JSONC 逻辑引用。
- 切换后新目录和新 Run 使用当前 generation，已打开标签依赖服务端租约继续读取旧 generation，运行中任务依赖 Run 租约保持原状态；旧代次无租约后自动退役。企业旧模板通过前向 migration 自动归并，历史文件和会话不自动删除。
- 本次增加兼容 API 字段与新的标签租约表，不新增部署节点；平台只读边界保持应用层约束，不增加 OS 只读挂载。服务正在 `127.0.0.1:8080`、`127.0.0.1:3000` 和 `127.0.0.1:18123` 运行；按用户要求仅本地提交、不推送。

## 2026-08-22 - 修复引用配置反馈、资产配色和自动化别名

### Why

- 应用资产库与自动化代码库保存成功反馈被全屏引用配置弹层遮挡；资产页面缺少与自动化紫色相区分的来源配色；自动化别名仍是只读固定值。

### What

- 全局工作台消息使用最终 DOM 样式提高到引用配置弹层之上，资产与自动化保存反馈都复用同一修复；应用资产库仓库卡片、图标和可选 SDD 目录统一为蓝色，自动化继续使用紫色。最初仅传递 Element Plus Message `zIndex` 参数的实现无效，已在用户复测后纠正并删除该误导参数。
- 自动化别名进入配置草稿、API、不可变 generation 与 MyBatis XML 持久化；管理员可编辑，普通成员只读，同一应用内校验唯一，旧客户端缺省时仍生成 `automation-{englishName}`。
- 新增 `V20260822103625__application_automation_reference_generations_add_alias.sql` 前向迁移并回填存量别名；本地持久 PostgreSQL 已执行，SHA-256 固定为 `0313c4153a77cf0bb6311e2c12320454a993c41db556685876d6881a0726fdce`。HTTP API、事件边界、数据库、部署、模块 README 和用户手册同步更新。

### How

- 后端自动化服务/API 22 项、H2 MyBatis 4 项和真实 Testcontainers PostgreSQL 历史升级 1 项通过；JDK 25 完整 26 模块生产打包成功。
- 前端定向引用反馈/配置弹层 52 项、类型检查和生产构建通过；全量 Vitest 149/149 文件通过，2201 passed、1 skipped。用户复测发现 Message 仍被遮挡后，进一步在已启动的生产页面用真实 Chromium 验证 Element Plus 内联 `z-index: 2001` 被最终样式覆盖为 `12050`，高于引用弹层的 `11000`；`git diff --check` 通过。
- 使用 `.env.test` 与本地 ClickHouse helper 启动真实 backend、opencode-manager、frontend；health/readiness 为 UP，前端 3000 返回 200，Flyway 当前版本为 `20260822103625`。应用内浏览器新会话无 888888888 登录态，因此未自行输入密码，页面保存交互由组件测试覆盖。

### Result

- 两类引用保存反馈的最终浏览器层级高于配置弹层；应用资产为蓝色、自动化为紫色；管理员修改自动化别名并保存后，别名随应用级共享 generation 和 JSONC 对账生效。使用 `.env.test`、`test` profile 和本地 ClickHouse helper 重启，backend health/readiness 为 UP，frontend 返回 200，manager 对用户 OpenCode 进程核验为 HEALTHY。
- 本次变更涉及兼容 API 字段和 PostgreSQL migration，不新增事件 payload、Run 上下文、部署节点、性能通路或额外权限；未修改 `.env*`、generated SDK、OpenCode 源码和未跟踪 `.reasonix/`。

## 2026-08-22 - 修复本地客户端实例 MyBatis 原始布尔映射

### Why

- macOS 客户端 enroll 后，后台读取 `local_client_instances.self_update_supported` 时，MyBatis 将 `boolean` 别名解析为 `Boolean`，无法调用 `LocalClientInstanceRow` 的原始类型 `boolean` 构造器，WebSocket 随即关闭并显示“客户端内部错误”。

### What

- 将 `LocalClientMapper.xml` 的 `self_update_supported` 构造映射改为项目既有的 `_boolean` 原始类型别名。
- 新增 `MyBatisLocalClientInstanceRepositoryIntegrationTest`，覆盖按实例、按用户和全量查询，并同步 persistence README。

### How

- 修复前测试稳定复现生产同款 `NoSuchMethodException`；修复后聚焦测试通过，完整 persistence reactor 共 360 项通过、20 项按既有条件跳过。
- 使用 JDK 25、根目录 `.env.test`、`test` profile 和本地 ClickHouse helper 重新构建并重启 backend、opencode-manager 与 frontend，客户端 PID 46962 自动恢复到后端 8080 的 `ESTABLISHED` 连接。

### Result

- backend health/readiness 为 UP、frontend 3000 返回 200、CORS 正常；后台记录 `local_client_connected ... generation=2`，未再出现 `LocalClientInstanceRow` 映射异常。
- 不涉及 API、事件、数据库结构/Flyway、性能、安全、环境文件、generated SDK 或 OpenCode 源码；未新增部署节点。

## 2026-08-22 - 统一自动化引用 JSONC 服务端对账链路

### Why

- 自动化引用同时存在前端 JSONC 补丁和 Java Run 派发推导，形成两套事实来源；普通发送、批量和夜间任务创建还会提前写工作树，容易覆盖用户并发编辑并制造不必要的 reload 时序。

### What

- 自动化 JSONC 只保留后端 `ApplicationAutomationReferenceWorkspaceReconciliationService` 一套实现：按应用当前 READY generation 与本机精确 READY 副本重算托管引用、清理同库历史身份和无用权限，并返回本次可租用代次与局部告警。前端只调用文件 WebSocket `agent-config.automation-reference.reconcile`，不再读取、解析或提交自动化 JSONC；应用资产库原有 TypeScript `patchReferenceConfig` 保持独立。
- 工作区进入、显式刷新文件树和配置同步 READY 触发对账；普通发送、命令、重发、批量提交与定时任务创建不再预写。普通和定时 Run 真正派发前仍由后端同一入口对账、按需 reload 并建立 generation 租约，不向消息、提示词或 OpenCode 上下文注入引用说明。
- 工作区文件内核增加基于存在性与 SHA-256 的条件写，普通写与条件写共享 JVM 分片锁和跨 Java 进程文件锁；冲突后只重新读取重算一次，第二次仍冲突返回 `CONFLICT`，避免覆盖用户编辑。同步更新文件 RPC、HTTP/事件边界、模块图、自动化验收说明及前后端 README/PACKAGE。

### How

- 自动化对账、JSONC、文件 CAS、Run 派发与文件 WebSocket 聚焦测试通过；前端全量 Vitest 149 个文件通过（2194 passed、1 skipped），类型检查和 production build 通过。
- JDK 25 下 `mvn -pl test-agent-workspace-management,test-agent-opencode-runtime,test-agent-api -am test` 最终 22 模块全部通过；首次完整运行仅旁路流式测试出现一次 2 秒时序波动，单测复跑与第二次完整 reactor 均通过。
- 使用根目录 `.env.test`、`test` profile 与 `--with-clickhouse` 完整重启 backend、opencode-manager 和 frontend；health/readiness 为 UP、前端 3000 返回 200、CORS 正常、manager 将 OpenCode 核验为 HEALTHY。浏览器为全新未登录会话，因此本轮未冒充 888888888 重做对话读取；此前同环境已真实打开并加入过自动化文件，本轮代码/组件测试覆盖改造后的读取和派发契约。

### Result

- `.opencode/opencode.jsonc` 继续是 OpenCode 唯一运行时事实源，自动化只由 Java 对账，浏览器不再拥有第二套自动化补丁算法；应用资产库既有前端补丁行为不变。单库副本不可用只移除/跳过该托管引用并给出安全局部告警，主工作区和其它引用继续可用。
- 本次新增内部文件 WebSocket 操作和条件写并发保护，不新增 HTTP API、RunEvent 类型、数据库结构/Flyway、部署节点或强制环境变量；未修改 `.env*`、generated SDK、OpenCode 只读源码、未跟踪 `.reasonix/` 和并行的本地客户端持久化修复。公共 Tool 真实远程 push → rollout → 多用户 reload 全链路未执行，也不作为本次已验证项。

## 2026-08-22 - 本地工作区支持客户端原生目录选择并修复网页选择

### Why

- 个人设置注册本地工作区时，网页目录浏览把单击直接解释为进入下一级，用户无法选中列表中的目录；浏览器自身也不能直接唤起客户端机器上的受控目录选择器。
- 需要在不新增本地 HTTP 端口或后端文件代理的前提下，让已认证客户端打开 macOS 原生目录弹窗，同时保留无图形桌面、旧客户端和弹窗失败时的网页兜底。

### What

- 复用既有 `file-ws-route → directory-picker ticket → /file/ws → FILE_REQUEST` 链路，新增受限操作 `directory.pick`；客户端在虚拟操作线程中切到 AWT EventQueue，macOS 使用原生 `FileDialog`，其它图形桌面使用 `JFileChooser`。同一客户端只允许一个弹窗，取消返回 `cancelled=true`，选中结果继续执行真实路径、目录权限、符号链接和文件系统身份校验。
- 前端新增“客户端选择”和“网页浏览”两个明确入口；原生选择最长等待 10 分钟并在结束后关闭一次性文件 WebSocket。网页兜底改为单击选中、双击进入目录，底部明确显示当前/所选路径。
- 同步本地客户端 README、架构文档、HTTP/文件 WebSocket 契约、Agent Web README 和 backend-api PACKAGE；未修改 `.env*`、generated SDK、OpenCode 源码、数据库或 Flyway。

### How

- JDK 25 下客户端选择/取消/路径校验测试 4/4、API 文件 WebSocket 原生选择转发定向测试通过；`test-agent-local-client,test-agent-api -am test` 完整 reactor 通过并生成 12 MiB Shade JAR。
- 前端全量 Vitest 149 个文件通过（2197 passed、1 skipped）；Agent Web `vue-tsc` 与 production build 通过。全 workspace typecheck 被当前 HEAD 已存在的 backend-api 测试缺少自动化 `alias` 字段阻塞，本次目录选择文件的构建与运行测试不受影响。
- 使用根目录 `.env.test`、`test` profile、JDK 25 和 ClickHouse helper 重启 backend、opencode-manager、frontend；readiness 为 UP、前端 3000 可访问。基于 root 所有的既有安装复制用户可写的 `/Users/kaka/Applications/TestAgent Local Client Dev.app`，替换为最终 JAR 后启动；追加的 Swing 取消清理通过客户端完整 reactor 复测，实例 `lci_c8d77417e5a0462db2edbf8d4a433445` 最终以 generation 5 成功连接。

### Result

- 浏览器不读取本机文件系统，所有目录结果仍由在线实例 owner 和 connection generation fencing；客户端选中后只回传通过校验的规范化绝对路径。网页兜底现在能够直接选择目录，不再只能逐层下钻。
- 当前客户端、后台和前端均已运行。浏览器现有 TestAgent 标签登录态已过期，未读取浏览器存储或冒充用户登录，因此原生弹窗最后一次人工点击需由用户登录后执行；协议、客户端、API 和页面链路分别由自动化测试覆盖。
- 本次增加兼容的内部文件 WebSocket 操作，不新增 HTTP 路由、RunEvent/SSE、数据库、部署节点、强制配置、额外端口或新的文件代理；未触碰未跟踪 `.reasonix/`。

## 2026-08-22 - 客户端托盘直接注册工作区并修复网页 500

### Why

- 用户明确要求像 Codex 一样直接从 macOS 客户端选择并注册本地工作区，不再从网页远程触发客户端目录弹窗；客户端在线时网页也不应继续展示下载入口。
- 网页兜底注册调用 `POST /api/internal/platform/workspace-management/local-workspaces` 时，Controller 在 `reactor-http-nio` 线程内进入同步文件隧道并执行 `block()`，真实日志因此返回 500。

### What

- 托盘新增“选择并注册工作区…”，仅在线时可用；复用现有 macOS 原生目录选择器，按目录名生成工作区名称，并通过当前已认证、带 generation fencing 的客户端 WebSocket 发送 `WORKSPACE_REGISTER`。服务端复用 `LocalWorkspaceApplicationService.create` 完成路径校验、目录根注册和既有补偿，不信任客户端传入用户身份。
- 协议新增同 requestId 的 `WORKSPACE_REGISTERED` 成功响应；服务端异步执行创建，避免 WebSocket `concatMap` 等待工作区创建时阻塞同连接上的 `FILE_RESPONSE`。断连、超时和服务端错误会精确结束对应客户端请求，托盘成功通知不暴露绝对路径。
- 删除上一轮网页触发 `directory.pick` 的实现，网页设置只保留明确标注的兜底注册；在线客户端存在时隐藏“下载本地客户端”，全部离线且允许下载时重新显示。
- `LocalWorkspaceController` 的创建和删除把完整同步业务放到 `boundedElastic`，修复 WebFlux 事件循环阻塞 500；HTTP 路径、DTO 和权限契约保持不变。
- 同步客户端/API/协议、前端、HTTP/文件 WebSocket、架构、安全和用户手册文档。

### How

- JDK 25 下后端相关 61 项通过：API 49 项，客户端协议 7 项，客户端托盘/文件 RPC 5 项；新增测试证明 Controller 不在事件循环阻塞，以及工作区请求在文件 RPC 回包前不会形成 WebSocket 入站死锁。
- 前端定向 Vitest 190 项、`@test-agent/shared-types` 和 `@test-agent/agent-web` typecheck 通过；全 workspace typecheck 仍被既有 backend-api 测试第 903 行缺少自动化 `alias` 字段阻塞，与本次文件无关。
- JDK 25 完整后端跳过测试打包和前端 production build 通过；首次标准启动被本机已有 ClickHouse 密码不一致阻塞，改用项目 `--with-clickhouse` helper 后成功。因 `.env.test` 按安全默认未开启明文客户端控制，最终只对本次进程注入 `TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true`，未修改环境文件。
- 最终 backend readiness 为 UP、frontend 3000 返回 200；12 MiB 客户端 JAR 与 `/Users/kaka/Applications/TestAgent Local Client Dev.app` 内 JAR SHA-256 均为 `0403ac9ca1b0a03a0ab119534f70883ad01b73d9216cef212c1096c1768008e8`，客户端 PID 96925 以 generation 9 完成认证并在线。

### Result

- macOS 用户现在可直接点击托盘小兔子 → “选择并注册工作区…” → 选择目录完成注册，网页无需参与；网页仍保留旧客户端或无图形环境的手工兜底，在线时不显示重复下载入口。
- 500 根因已由线程模型修复并有 WebTestClient 回归覆盖。当前服务和客户端均运行；server manager 同时占用 4096，导致客户端自动启动本地 OpenCode 的一次健康告警，但不影响客户端在线和工作区注册，本地 OpenCode 端点自身返回 200。
- 本次变更内部客户端 WebSocket 帧和既有 HTTP 实现，不新增 HTTP 路径、RunEvent/SSE、数据库/Flyway、SQL、部署节点、性能通路或额外权限；未修改 `.env*`、generated SDK、OpenCode 源码和未跟踪 `.reasonix/`。

## 2026-08-22 - 默认重启自动对齐本地 ClickHouse 凭据

### Why

- `.env.test` 已启用并指向项目托管的本机 ClickHouse，但默认重启只加载其中的旧账号；本地容器实际使用 `.tmp/dev-services/clickhouse` 中 helper 管理的另一套账号和密码，导致后端 schema migrator 以 ClickHouse `Code: 516` 启动失败。
- 只有人工追加 `--with-clickhouse` 才会加载正确凭据，所以下次恢复默认命令仍会复现。

### What

- 扩展既有 `restart-dev-services.sh`：dotenv 明确启用 ClickHouse 且 JDBC 地址精确匹配 helper 管理的回环端口和数据库时，自动进入原有 `with_clickhouse` 路径；其它本机端口、数据库和远端地址不接管，显式 `--with-clickhouse` 继续可强制启用。
- 复用 `tools/clickhouse-dev-services.sh` 的 prepare/pull/start 和 Java-safe dotenv，不新增凭据生成或容器管理实现；补充开发脚本回归，锁定旧账号覆盖和远端不接管行为。
- 同步后端 README、本地研发流程、后端部署说明和 ClickHouse 运营分析开发说明；未修改 `.env.test`。

### How

- `./tools/verify-dev-scripts.sh` 完整通过，新增模拟场景验证默认命令自动启动托管容器并让 Java 使用 `testagent_analytics`，远端 ClickHouse 不产生 Docker 调用。
- 使用 JDK 25 执行不带 `--with-clickhouse` 的 `./restart-dev-services.sh --profile test --env-file .env.test`，后端 26 模块跳过测试打包、前端 production build、ClickHouse 鉴权 readiness、backend readiness 和 frontend 启动全部成功。
- 最终 backend health/readiness 为 UP、frontend 3000 返回 200、CORS 返回本机 origin，ClickHouse 26.3.17.56 在 `127.0.0.1:18123` 认证成功；最近后端日志没有 `Code: 516/AUTHENTICATION_FAILED`。

### Result

- 当前 `.env.test` 下可直接使用默认重启命令，不再要求人工记住 `--with-clickhouse`，helper 运行凭据会在启动 Java 前自动覆盖旧 ClickHouse 账号。
- 本次只改变本地 Bash 开发启动行为和文档，不涉及 HTTP/API、RunEvent/SSE、数据库/Flyway、生产 ClickHouse 专机、权限、安全协议、generated SDK 或 OpenCode 源码，也未新增部署节点。

## 2026-08-22 - 修复客户端工作区重复注册与网页深链

### Why

- 客户端再次选择已经注册过的同一目录时，服务端先生成新的 Workspace ID，再命中 `uk_local_client_workspace_root` 唯一约束，事务回滚并让托盘只显示“客户端内部错误”。
- 托盘“打开网页”原先只打开工作台首页，没有携带刚注册的逻辑 Workspace ID；网页因此无法自动切换到客户端本地目录。

### What

- 本地工作区注册在客户端实例行锁内按 `userId + clientInstanceId + rootDigest` 查重；重复选择复用既有 Workspace，并重新下发 `workspace.registerRoot` 恢复客户端重启后可能丢失的内存映射。新增查询和锁均落在既有 MyBatis mapper/repository，不新增 JDBC SQL。
- 托盘注册成功后记录最近工作区并自动打开 `/workbench?localWorkspaceId=<workspaceId>`；“打开网页”复用该深链。地址栏只传逻辑 ID，不传本机绝对路径，浏览器打开失败也不会把已经提交的注册误报为失败。
- Agent Web 解析并鉴权查询深链中的本地 Workspace，复用既有工作区文件 WebSocket 加载目录；本地模式显示工作区名和“本地目录”，隐藏/阻断无效的 Git 变更、发布和版本菜单。
- 同步本地客户端 README、客户端架构文档、前端总览和 Agent Web README。

### How

- JDK 25 下 `LocalClientTrayTest` 3 项、`LocalWorkspaceApplicationServiceTest` 1 项通过；相关后端 24 模块跳过测试打包通过，并在完整 26 模块开发重启中再次构建成功。
- Agent Web 类型检查、production build 通过；`app-source-workspace`、`FigmaShell`、`WorkbenchFooter` 定向 Vitest 共 98 项通过。
- 使用 `.env.test`、`test` profile 和 JDK 25 完整重启 backend、opencode-manager、frontend；本机明文回环联调开关只对当前进程临时注入，未修改环境文件。backend readiness 为 UP、frontend 3000 返回 200。
- 新客户端 JAR 与 `/Users/kaka/Applications/TestAgent Local Client Dev.app` 内 JAR SHA-256 均为 `d1dbff76835b83b3e5dc3c5cea0ee5bfc0b7223f2c103bb214c3d28df74102c3`；客户端 PID 7134 以 generation 10 完成认证。真实重复调用注册接口成功复用 `wrk_b6daaf37eaec4939bc91bafa8eef6b5e`，返回 `LOCAL_CLIENT / online=true`。

### Result

- 同一目录可安全重复选择，客户端注册成功后会直接打开并选中对应网页工作区；网页通过既有文件 WebSocket 展示和操作本地目录，不依赖应用/版本下拉框。
- 当前服务和新客户端均已运行；macOS 原生目录弹窗及浏览器可见结果仍需用户最后点击一次确认。
- 本次不新增或变更 HTTP 路径、请求/响应 DTO、RunEvent/SSE、数据库结构、Flyway、部署节点、强制配置或额外权限；仅新增存量表查询/行锁和兼容前端状态。未修改 `.env*`、generated SDK、OpenCode 只读源码或未跟踪 `.reasonix/`。

## 2026-08-22 - 修复本地工作区登录后文件树加载超时

### Why

- 客户端注册并打开本地工作区深链后，登录和工作区切换均已成功，但文件树仍调用服务端托管工作区使用的 `workspace.view.list` 组合视图操作；本地客户端只实现普通目录 `workspace.list`，因此页面等待后显示“请求超时”。

### What

- `AgentWorkbench` 对 `LOCAL_CLIENT` 复用体验区已有的普通目录转换链路，通过 `api.listFiles` 发送 `workspace.list`，不再调用本地客户端不支持的组合引用视图。
- 增加前端源码回归断言，并同步前端总览、Agent Web README 和本地客户端架构说明；未扩展文件 WebSocket 协议或增加第二套目录实现。

### How

- Agent Web 工作区源码测试 14/14、类型检查和 production build 通过，构建仅保留既有 chunk 提示。
- 在用户现有 Chrome 登录态中真实打开 `/workbench?localWorkspaceId=wrk_b6daaf37eaec4939bc91bafa8eef6b5e`：路由、ticket 和文件 WebSocket 建立成功，根目录展示 `.agents`、`backend`、`frontend`、`docs` 等项目内容；继续展开 `backend` 后展示各 Maven 模块和文件，页面不再出现“请求超时”。

### Result

- macOS 托盘选择并注册工作区后，即使先经过登录页，工作台也能选中本地工作区并加载、展开真实本地目录。
- 本次仅修正前端对既有文件 WebSocket 操作的选择，不涉及 HTTP API、RunEvent/SSE、数据库、Flyway、部署节点、性能或安全协议；未修改 `.env*`、generated SDK、OpenCode 只读源码或未跟踪 `.reasonix/`。

## 2026-08-22 - OpenCode 插件化运营事实与集中式 Trace

### Why

- 运营分析长期把 OpenCode Tool 的 before/after 事件拆开推导，导致实际调用 `test-design` Skill 的用户仍显示 0；同时缺少像 DeepSeek Harness 一样可回放 Agent、子 Agent、模型和工具全过程的追加式轨迹。
- 服务端与本地客户端都必须在不修改 OpenCode 1.18.4 源码、不写入 RunEvent/SSE、不中断对话的前提下采集，并把本地未确认数据低优先级续传到服务器权威归档。

### What

- 新增同一份 OpenCode Observability V1 插件，由服务端 official launcher 和本地 Java 监管器强制注入；插件按 `callID + tool + args` 关联 before/after，Skill 只取 `args.name/metadata.name`，记录 system/context/message/reasoning/Tool/Skill/子 Session、序号、父子关系、token、耗时和异常，并在落队列前脱敏凭据、secret/env 和二进制正文。
- 新增专用限时令牌、稳定进程 generation、独立 telemetry HTTP 入口、Trace 分片归档入口和本地 WSS 帧。令牌 TTL 可配置（默认 7 天、范围 1 分钟至 30 天），同时由当前数据库 generation 隔离旧进程。服务器以不可变 gzip NDJSON 分片与 manifest 保存权威正文，ClickHouse 只保存目录、span 和插件能力事实；插件事实与旧 RunEvent 事实按 `eventId/runId/callId` 反连接去重，不回填历史。
- 本地客户端新增随机 loopback token relay、未确认 spool、SHA-256/ACK/水位续传、generation fencing、单在途与空闲 3 秒调度；对话、模型和文件帧优先，Trace 只在高优先队列为空时以默认 1 MiB/s 上传，队列/磁盘压力只降级 Trace 并标记不完整。
- 新增仅 `SUPER_ADMIN` 可见且后端强制鉴权的独立 Trace 页面和四个管理 API，提供三泳道、筛选、父子折叠、Payload/Result/Timing/Source 检查器及单条 gzip 下载；正文查看、下载和失败尝试均记录不含正文/路径的安全审计。
- 新增 PostgreSQL `V20260822201811` generation migration 和 ClickHouse `V20260822174420` 目录 migration；前者已执行字节 SHA-256 固定为 `033a70045a188d3f322868efc83bddebd8b4b86afe18f7b0f943632eb8fa1865` 并有测试锁。同步 HTTP、事件、数据库、ClickHouse、OpenCode 升级、本地客户端、安全、模块和前端文档。

### How

- JDK 25 下受影响后端 24 模块完整 reactor 最终 `BUILD SUCCESS`：API 635 条、persistence 364 条（20 条按既有外部依赖条件跳过）等均 0 failure/error；新增 Trace 后端权限/失败审计 3/3、专用令牌签发/过期/generation 隔离 4/4、真实 PostgreSQL 兼容升级和 migration 字节锁 17/17 通过。
- 插件与 launcher Node 测试 17/17、前端 Trace/运营/登录路由 12/12、agent-web typecheck 与 production build、`verify-dev-scripts.sh`、麒麟 ARM64 本地客户端离线封包测试及相关 shell 语法均通过。
- 插件热路径 20,000 次基准：增量 p99 约 0.0015 ms、RSS 增量约 11.5 MiB、热路径文件 I/O 和网络操作均为 0，低于 2 ms/32 MiB 门槛。
- 使用 `.env.test`、`test` profile、ClickHouse 完整重启 backend/manager/frontend；最终 backend health/readiness 为 UP、frontend 3000 为 200、ClickHouse 鉴权查询成功。真实 server OpenCode 执行 `test-design-generation` 后，Trace `trc_a14c3311d665c03b91dc6e595c50170a` 为 `COMPLETED/ARCHIVED`、570 事件、0 dropped，ClickHouse 显示 `SKILL test-design SUCCEEDED calls=1 users=1`、`TOOL read calls=2 users=1`；115500 字节下载 gzip 校验和 DOWNLOAD SUCCESS 审计通过。

### Result

- Skill 使用不再依赖 title 或丢失 before 身份；运行态运营事实和完整 Trace 已走 OpenCode 插件独立链路，服务器保存最终权威副本，本地只保留未 ACK 临时 spool，采集/上传故障不会阻塞聊天。
- 当前本机旧 Local Client Dev 与 manager 同时监听 4096 的环境冲突仍使新的平台 Run 命中旧客户端进程；因此最新真实实证使用 manager 的局域网地址完成，平台 Run 相关性和本地客户端物理断网续传以单元/集成测试验证，尚未在消除该端口冲突后重新做整条平台 Run 实测。
- 已完成插件 hook p99/RSS/I/O 基准，但尚未完成真实模型多轮关闭/开启对照所需的 CPU、磁盘 IOPS、首 token 与整轮 p95 ≤3% 统计，不能把性能验收写成全部通过。
- 未修改 `.env*`、generated SDK、OpenCode 只读源码或部署节点；未纳入工作区既有文件 WebSocket、本地工作区下拉、SSE transport、进程陈旧 PID/端口探测和 `.reasonix/` 等无关改动。

## 2026-08-22 - 修复失败卡片误走撤回重发

### Why

- 单人和协作对话的失败卡片都复用了消息气泡的“撤回重发”事件，点击“重试”会进入编辑旧消息、替代 Run 和权限校验流程，与失败请求直接重新执行的产品语义不符。

### What

- 将 `FigmaChatPanel` 的失败卡片 `retry` 与用户消息气泡 `resend` 拆成独立事件；`AgentWorkbench` 为失败卡片读取最后一条根用户问题，并通过现有普通发送链路创建新 Run，不取消旧 Run、不调用 resends 接口、不替换历史消息。
- 保留现有消息气泡撤回重发和底部运行面板行为，本批次不扩展范围；在前端总览与 Agent Web README 中记录后续边界：消息气泡撤回重发仅限协作对话实际发送人，普通对话没有失败卡片时不提供其它重试入口。
- 增加单人失败、协作失败、仍运行状态异常和重开历史失败会话回归，并保留协作消息气泡撤回重发回归，锁定两个入口不再串线。

### How

- `FigmaChatPanel.test.ts` 定向 Vitest 160 passed / 1 skipped；工作台 Chromium 定向 E2E 5/5 通过。
- `@test-agent/agent-web` production build（含 `vue-tsc --noEmit`）通过；构建仅保留既有大 chunk 与静态/动态 import 提示。
- 当前已有 frontend `127.0.0.1:3000` 与 backend `127.0.0.1:8080` 继续运行，页面和 health 均返回 HTTP 200；提交前回顾全部 `.agents/session-log*.md` 近期记录，并仅暂存本次相关差异。

### Result

- 失败卡片“重试”在单人和协作对话中都创建普通新 Run，旧用户消息和旧 Run 保持不变；消息气泡撤回重发仍走原有替代 Run 流程。
- 本次仅改变前端事件分流与调用选择，不新增或变更 HTTP API、DTO、RunEvent/SSE、数据库、Flyway、部署节点、性能或安全边界；未修改 `.env*`、generated SDK、OpenCode 只读源码或工作区其它未完成改动。

## 2026-08-23 - 完成本地/服务器工作区、对话与 Agent 端到端闭环

### Why

- macOS 客户端注册本地工作区后，网页不能稳定恢复或切回服务器工作区；本地目录虽可浏览，普通对话仍会因 Java 25 的 h2c 请求卡住或旧 Run 入口误分配服务器进程而失败。
- 完成态消息刷新和 Run 持久化目标还可能把已经在客户端执行成功的会话显示成服务器运行，导致历史展示与真实执行位置不一致。

### What

- 顶部工作空间菜单直接列出已注册本地工作区，支持客户端/服务器双向切换；本地选择写入既有全局最近工作区偏好，刷新、重新登录和客户端再次打开网页后恢复上次选择。LOCAL_CLIENT 文件树和目录下载复用普通 `workspace.list/read`，切换时关闭旧文件 WebSocket。
- 新增本地工作区 recent API，复用 `UserWorkspacePreference`；文件 ticket 的同步 MyBatis/Redis 路由移到 `boundedElastic`，避免占用 WebFlux event-loop。
- 本地 OpenCode loopback 固定 HTTP/1.1；generated gateway 移除可绕过隧道的无参构造。旧版 Run 和完成态快照复用 Spring 管理的 `AgentRuntimeTargetResolver`，按 Session 冻结的 LOCAL_CLIENT 目标路由，并把真实 runtime kind/客户端实例写回 Run。
- 同步 local-client、opencode-client、opencode-runtime、前端、用户手册与 HTTP API 说明；未修改 `.env*`、generated SDK 或 OpenCode 只读源码。

### How

- 定向 Maven 10 个测试类共执行 118 项，其中 117 项通过、1 项按平台条件跳过；前端 3 个测试文件 200 项通过，agent-web `vue-tsc + vite build` 通过。
- 使用 `.env.test` / `test` profile 重建并重启 backend、frontend、manager；安装并运行 `/Users/kaka/Applications/TestAgent Local Client Dev.app`。最终 backend health 为 UP、frontend 为 HTTP 200，服务器 4096 与客户端 4106 的 OpenCode 1.18.4 health 均为 true，客户端实例 `lci_c8d77417e5a0462db2edbf8d4a433445` 在线。
- 真实验证本地 `wrk_16dfcef54a8b4e48a354ce0946a04fb2` 与服务器 `wrk_0be73a3431a34f179e96f18d3f314dff` 往返切换和 recent 恢复；两端文件 route/ticket/WebSocket 均能列出各自根目录。
- 本地/服务器普通对话分别以 `LOCAL_CHAT_E2E_OK`、`SERVER_CHAT_E2E_OK` 成功结束；本地/服务器 Agent `task` 子调用均为 completed，并分别返回 `LOCAL_AGENT_E2E_OK`、`SERVER_AGENT_E2E_OK`。Run API 最终显示真实 `LOCAL_CLIENT` 或 `SERVER_PROCESS`。

### Result

- 服务器和客户端的工作区展示、选择持久化、普通对话及 Agent 子任务调用形成真实端到端闭环，当前服务与客户端保持运行，可直接由用户继续页面验收。
- 浏览器自动可见点击复核未完成：已有 Chrome 页面处于登录页，控制插件两次 DOM 快照超时；未在未确认情况下代填浏览器密码。HTTP、WebSocket、真实模型/Agent 执行和前端组件/构建均已验证。
- 新增一个 recent HTTP 路径但不改变既有 DTO；无 RunEvent/SSE、数据库结构、Flyway、部署节点、强制配置或安全权限变化。

## 2026-08-23 - 复核 OpenCode 1.18.4 可观测性、DSH Trace 与企业封包

### Why

- 需要按八组门禁重新审核插件化运营事实和集中式 Trace，统一服务端/客户端 OpenCode 1.18.4，并把 Trace 保留在系统控制台内；目录行与事件行要更紧凑且禁止渐变。
- 既有结论没有区分真实服务端测试设计 Agent、真实本地普通 Agent 与本地实际 `test-design`，也缺少最终 linux/amd64 Worker、固定数据集运营回归和切换后旧来源事实的精确证据。

### What

- 插件严格按 1.18.4 hook/事件 fixture 采集 system/messages transform、流式 assistant/reasoning、step/token/cost、Tool/Skill before/after/error 和子 Session；Skill 名只取 `args.name/metadata.name`，失败 Tool 由 `message.part.updated state=error` 闭合。ClickHouse 新增 DSH Span 指标、唯一 call 状态、插件切换物化视图和查询层 cutoff，正文继续只落服务器 gzip NDJSON。
- Trace 改为控制台内独立 `SUPER_ADMIN` 菜单：左侧基础目录常驻，点选后原位展开 DSH 三泳道；Duration 切换等宽/真实耗时，Turns/Calls 分别折叠，Timing 展示 TTFT、Decode、cost 和五类 token。基础行 40px、事件行 28px，全部纯色且测试禁止任何 gradient。
- 服务端和本地启动/封包固定验证 OpenCode 1.18.4；企业脚本封装同一插件、四条 ClickHouse migration 摘要、持久卷/告警/双节点路由说明，并补充 USTC TLS 失败时只在联网封包机显式改用 Debian 官方镜像的安全回退。
- 完成八组 Markdown 验收清单并明确剩余门禁；外部研究目录只保留 Markdown 和四张 `.drawio`，不生成 Word、证据卡或阅读路线。

### How

- 固定数据集在基线 `473d48dfc` 与当前代码分别执行 `AnalyticsQueryServiceTest` 7/7，旧字段规范化 SHA-256 都为 `ff77d42d...e012`；再执行运营/ClickHouse 定向测试 16/16、前端 16/16、插件真实 1.18.4 fixture 16/16、归档/权限/本地协议定向后端 28/28，typecheck 通过。
- 真实服务端 Run `run_6d73a62460254293b8fa1847bb3c271f` 归档 3,413 事件、696,361 bytes、0 dropped；唯一 Skill 事实为 `test-design` 成功 14/失败 1、用户 1，事件/调用去重均为 0。下载 gzip SHA-256 为 `fbe2b2e8...67941`，关闭客户端后仍可查看下载，审计只含 VIEW/DOWNLOAD 元数据。
- ClickHouse 运行表正文/路径列计数为 0；16 条 cutoff 后发生的旧来源行均在切换 migration 安装前写入，查询层屏蔽。migration 安装后新增旧来源 Agent/Skill/Tool 行为 0。
- 插件 20,000 次基准增量 p99 0.0017 ms、RSS 10,321,920 bytes、热路径 I/O/网络为 0。使用 `.env.test` 重启后 backend readiness、frontend、ClickHouse、服务端 4096 与本地 4106 均正常，两端 OpenCode 都为 1.18.4。
- 正式输出 backend JAR、frontend tar、Kylin ARM64 本地包及 linux/amd64 Worker；Worker 独立离线探针验证 OpenCode 1.18.4、glibc 2.31，镜像内插件 SHA 与仓库/客户端一致。Worker tar SHA-256 为 `482868e7...def3`。

### Result

- 代码、接口、事件、ClickHouse、归档安全、低优先级本地上传、控制台 Trace 和企业依赖主要链路已实现并按当前环境部分验收；OpenCode 只读源码、`.env*` 和 generated SDK 未修改，也未新增部署节点。
- 八组最终验收仍不能标记为全部完成：本地 Run `run_e0a1d5f3245f4a53ad16a267e5cf1672` 已冻结为 `LOCAL_CLIENT`，但本地 `/agent` 没有 `test-design-orchestrator`，证明离线包尚缺受管公共 Agent/Skill 配置分发；另缺真实模型关闭/开启 p95 对照、运行态归档 I/O 故障注入、两台真实企业后端演练、目标企业历史 PostgreSQL 升级和真实麒麟 ARM64 无公网安装/回滚。
- 本次提交只纳入可观测性、DSH、版本固定和企业封包相关改动；保留工作区菜单检索、本地切换优化、`.reasonix/` 和 `node_modules/` 等其它未完成内容不提交。

## 2026-08-23 - 统一并压缩本地与服务器工作区菜单

### Why

- 本地工作区注册后菜单渲染较慢，服务器测试工作区长期显示“工作空间加载中”；本地与服务器工作区被“返回服务器工作区”分隔，且工作区多时缺少检索。
- Agent 下拉同时出现 OpenCode 内置 Agent 与公共配置仓库发布的测试 Agent，需要确认数量来源，避免把配置数据问题误判为前端重复渲染。

### What

- 顶部工作区菜单统一展示本地工作区、应用代码库与服务器测试工作区，移除“返回服务器工作区”伪选项；所有条目压缩为单行并增加名称、分支和来源检索。
- 本地工作区先完成选择与标题渲染，再在后台加载目录；修正禁用的服务器模板查询仍处于 pending 时被误判为加载中的问题，并保留应用上下文，允许本地与服务器工作区直接往返。
- 复核 Agent 目录来源：当前运行时合并 7 个 OpenCode 内置 Agent 与 14 个已发布受保护 Agent，其中公共配置解析会把目录项统一转换为 primary；本批次只记录原因，未删除或隐藏已发布配置。

### How

- `FigmaShell` 与工作区源码定向 Vitest 2 个文件 78 项通过；agent-web production build（含 `vue-tsc`）通过，`git diff --check` 通过。
- 通过真实 Chromium 登录本地页面验证统一菜单、单行 34px 条目、名称检索、无返回伪选项和无持续加载；刷新后本地标题约 0.4 秒恢复，本地到可用服务器工作区约 0.37 秒，服务器返回本地约 0.04 秒，控制台无错误。

### Result

- 本地与服务器工作区现在在同一紧凑可检索列表中直接选择，永久加载属于前端状态判断缺陷而非 Mac 性能问题，已完成真实页面验证。
- F-COSS 部分服务器版本仍因现有 Git 远端预检返回 `GIT_UNAVAILABLE`，Agent 数量仍取决于公共配置仓库的已发布内容及 mode/hidden 元数据治理；两者未在本批次扩大范围处理。
- 本次仅修改前端交互、状态选择、测试和稳定文档；不涉及 HTTP API、RunEvent/SSE、数据库、Flyway、部署节点、性能协议、安全权限、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-23 - 修正 Trace 轨迹点击与控制台入口

### Why

- Trace 三泳道中的短条只有很小的可见区域，实际不易点击且选中反馈可能落在视口外；工作台最左 activity rail 与系统控制台同时存在 Trace 入口，图标也与其它运营能力重复。

### What

- 将三泳道短条改为具备完整泳道高度命中区、键盘焦点和选中状态的真实按钮；点击后同步选中事件、重置检查器到摘要页并把对应明细行滚入视口。
- 系统控制台菜单、功能页标签和 Trace 空状态统一使用 Waypoints 图标；删除工作台最左 activity rail 的重复 Trace 入口及遗留样式，Trace 继续只在控制台内展开。
- 保持紧凑行高和 Input/Model/Tools 纯色，不引入渐变；同步前端总览、agent-web 包说明和交互回归测试。

### How

- 前端全量 Vitest 150 个文件通过，2201 passed / 1 skipped；agent-web `vue-tsc` 类型检查通过。
- 使用 `.env.test`、`test` profile 和 ClickHouse 完成生产构建及真实服务重启；backend readiness 为 UP、frontend 返回 200，启动脚本确认 OpenCode manager 固定为 1.18.4 和 ClickHouse 正常。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，`git diff --check` 通过；未纳入 `.reasonix/`、根目录 `node_modules/` 等无关内容。

### Result

- Trace 总览短条现在可点击、可键盘操作，并能明确定位到对应明细；入口只保留在系统控制台，图标与其它菜单能力可区分。
- 本次仅涉及前端交互、图标、测试和稳定文档，不变更 HTTP API、RunEvent/SSE、数据库、ClickHouse schema、部署协议、安全权限、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-23 - 对齐 DSH 语义轨迹与范围框选

### Why

- 真实 Trace 把 OpenCode 流式 delta、状态和语义记录全部绘入同一总览，并为短事件强设最小宽度，导致数千条事件叠成连续色带；DSH 实际展示的是归一化语义记录，并支持在三泳道上拖拽框选范围。
- 用户要求保持紧凑纯色样式，同时达到 DSH 的清晰分段、耗时提示和范围选择效果。

### What

- 新增只用于页面展示的语义投影：保留 system/user/context、Assistant Step、Tool 终态及未结束调用；将最终文本/reasoning 合入 Assistant Step，去重重复 system prompt，不再把原始 delta/状态逐条绘入总览和明细。原始事件仍完整保留在服务器归档及单条下载中。
- 等宽模式改为按语义记录严格连续分段，真实耗时模式按 span 展开；MODEL 使用相邻两个纯色色块表达 TTFT 与 Decode，悬停显示起止时间和 Total/TTFT/Decode，不使用渐变。
- 三泳道支持水平拖拽范围框、跨泳道蓝色边界、窗外记录降权、指针捕获和拖拽后 click 隔离；双击或 Esc 清除范围，普通条带点击仍选中并滚动到明细。
- 指针只在真实拖动超过阈值后捕获，修复范围框选上线后单个色块无法点击的问题；检查器按 System/User/Context、Assistant、Tool/Skill 分别提供 `Preview`、`Output`、`Result`，并从归一化正文、reasoning、工具状态和原始事件逐级回退，不再统一显示“没有返回字段”。
- 不完整 Trace 会聚合尚未终止的 Assistant delta、Tool 状态和生命周期事件，避免目录可见但详情空白；`status=INCOMPLETE` 在 ClickHouse 查询中以 `complete=0` 为准。Trace 目录和事件检查器均可独立折叠，收起后中间轨迹占用释放空间，并保留 32px 恢复栏。

### How

- 直接检查本机 DSH `127.0.0.1:3080` 的真实页面交互，确认等宽语义分段、Duration span、深色耗时提示和拖拽范围的重叠筛选语义。
- 用真实归档 `trc_fd13b13f9caa2fcd5b85aedea13407a0` 验证：3,413 条原始事件投影为 35 条语义记录，同泳道重叠为 0；拖拽范围选中 9 条、窗外 26 条；MODEL 两个纯色阶段及 `TTFT 527 ms / Decode 1083 ms` 提示正常。
- 前端最终全量 Vitest 150 个文件通过，2202 passed / 1 skipped；Trace 定向 3/3、agent-web `vue-tsc` 和生产构建通过。ClickHouse 集成测试 4/4 通过，覆盖 `ACTIVE + ARCHIVED + complete=false` 仍可由“不完整”筛选命中。
- 使用真实归档验证不完整生命周期 Trace 可显示 `6 records / 167 raw events`，单色块直接点击与范围框选后再次点击均保持选中；Tool Result 返回真实 Skill 参数/结果，Assistant Output 返回 reasoning。两侧折叠交互由组件测试校验折叠类、恢复入口和展开回切。
- 使用 JDK 25、`.env.test`、`test` profile 和 ClickHouse 做最终重启时，构建与 ClickHouse 连通成功，但工作区另一组未提交的公共能力包功能先因多构造器缺少注入标记失败；临时只为验证补标记后，又被已经执行但源码字节发生变化的 `V20260823104611` checksum 不一致拦截。按 Flyway 规则未执行 repair、未改测试库历史，临时标记已撤销，因此本轮最新工作区不能宣称完整启动通过。

### Result

- Trace 总览从传输事件堆叠改为可读的 DSH 语义轨迹，范围框选、直接点选、类型化详情、不完整轨迹和两侧折叠均已实现，同时保留完整原始归档证据。
- 本次变更了既有 Trace 查询的 `INCOMPLETE` 过滤语义，但未变更 HTTP 字段、RunEvent/SSE、数据库或 ClickHouse schema、部署协议、安全权限、`.env*`、generated SDK 或 OpenCode 只读源码；未纳入 `.reasonix/`、根目录 `node_modules/` 和并行公共能力包改动。最终真实服务重启仍受上述并行 migration checksum 冲突阻塞。

## 2026-08-23 - 撤销 Client key 后隐藏本地实例与工作区

### Why

- 用户主动撤销本地客户端凭据后，客户端虽然断连，但稳定实例和本地工作区仍继续出现在页面，无法区分“临时离线”和“主动关闭”。

### What

- 本地实例查询仅在当前用户凭据为 `ACTIVE` 时返回数据；工作区列表和详情统一要求本地工作区 owner 仍有有效凭据，活动 Session 也不能绕过该可见性门禁。
- 撤销成功后沿用设置页的工作区目录变更事件，立即移除前端缓存和当前本地工作区快照、关闭文件 WebSocket，并显示“记录与目录仍保留”的反馈。
- 轮换和普通断连继续展示离线记录；主动撤销只隐藏投影，不删除稳定实例、平台绑定或用户磁盘目录，重新启用后可恢复。
- 同步 API、system-management、workspace-management、本地客户端架构和 agent-web 稳定说明；未修改 OpenCode 源码。

### How

- 后端定向测试 `LocalClientInstanceApplicationServiceTest` 与 `MyBatisSessionHistoryRepositoryIntegrationTest` 共 12 项通过；前端设置/事件链 3 个文件 39 项通过，agent-web typecheck 和生产 build 通过。
- JDK 25 下后端 26 模块 `mvn clean package -DskipTests` 通过；使用 `.env.test` / `test` profile 完整重启 backend、frontend、ClickHouse 和 OpenCode manager，readiness 为 `UP`、前端与 CORS 为 200、OpenCode 1.18.4 health 持续为 `HEALTHY`。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录并执行 `git diff --check`；只暂存本次语义修改，不纳入工作区并行公共能力包、Trace、`.reasonix/` 或 `node_modules/` 改动。

### Result

- 主动撤销现在会立即隐藏本地 OpenCode 实例和工作区，同时保留可恢复的数据与本地目录；临时离线仍按原设计可见。
- 既有 HTTP 路径和 DTO 不变；不涉及 RunEvent/SSE、数据库结构、Flyway、部署节点、性能模型、权限协议、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-23 - 本地客户端公共 Agent、Skill、Tool 完整能力包

### Why

- 本地客户端此前只能使用本机已有配置，平台公共 Git 已发布的 Agent、Skill、Tool 不会进入本地 OpenCode；安装后也没有完整包基线、用户确认更新、失败回滚或公共受保护 Agent 去重。
- 功能上线时公共仓库可能长期没有新提交，但页面不能因此没有公共版本；当前公共 HEAD 必须被补建并展示。相同能力文件的新 commit 也必须保留独立可追溯版本，不能被摘要唯一约束吞掉。

### What

- 公共发布链新增完整能力包构建：只导出 `agents/skills/tools` 白名单，排除敏感配置、Git、缓存和原始 `node_modules`；依赖以现有 lock 与离线目录为事实源，递归接受纯 JS/WASM，拒绝未声明、未锁定、安装脚本和原生扩展。失败落 `SERVER_ONLY`，不阻断服务器公共发布。
- manifest 记录 commit、文件清单、数量、兼容范围、依赖与变更摘要；`contentDigest` 表示文件内容，`bundleDigest=sha256(sourceCommit + "\\n" + contentDigest)`。启动补偿器在 Redis 全局锁内为尚无记录的当前公共 HEAD 补建包；客户端继续兼容首版文件摘要型基线。
- 新增公共能力 release/state/attempt 关系表、实例当前版本字段、MyBatis XML 仓储、`PUBLIC_CAPABILITY_SYNC_V1` 协议、256 KiB 拉取分片、generation fencing、断线重连和幂等终态。网页或托盘只通知并等待用户确认，不自动升级；离线确认在重连后继续。
- 客户端将候选包安全解压到不可变版本目录并原子切换 `OPENCODE_CONFIG_DIR`；Agent/Skill 变化调用 dispose，Tool/依赖变化重启本地 OpenCode；健康与 Agent/Skill/Tool 目录校验失败自动回滚。Tool 保持当前用户权限，不提权、不现场 npm 下载。
- 设置页和托盘展示当前/待更新 commit、摘要、Agent/Skill/Tool 数量与重启提示；在线时隐藏下载按钮。已激活本地公共包后过滤 `PUBLIC_GIT` 受保护 Agent 重复项，应用 Hub 保持现有逻辑。补充当前实例状态/用户确认/制品下载 API 和 HTTP、事件、数据库、部署、架构及模块 README。
- 修复本地历史 Session 使用 `lci_*` 稳定实例 ID 时的运行路由兼容：统一映射到 `node_local_*`，Run 与 SSE 继续按当前连接代次定位客户端；没有新增 RunEvent 类型。

### How

- JDK 25 下相关后端 24 模块完整 reactor `BUILD SUCCESS`，API 644 项、persistence 366 项（20 项按既有外部条件跳过），公共构建/降级、Store、新旧摘要兼容、Updater、Coordinator、协议、路由和 Controller 定向用例全部通过。真实 `.env.test` PostgreSQL 公共能力仓储测试 1/1 通过。
- 两条已执行 migration 保持不可变：`V20260823104611` SHA-256 `79efa7be...bf342d`、Flyway checksum `236715365`；`V20260823123757` SHA-256 `3df529b5...ad2258`、checksum `-1442353574`。源码、persistence JAR 与最终应用嵌套 JAR 字节一致，目标数据库两条记录均 `success=true`。
- 前端设置页定向 Vitest 7/7、agent-web lint 与 production build 通过；workspace 全量 typecheck 仅保留无关既有失败 `frontend/packages/backend-api/tests/backend-api.test.ts:933` 缺少 `alias`。麒麟 ARM64 完整包测试、稳定 launcher 静默更新/降级/自动回滚测试和开发脚本校验通过。
- 使用临时 JSON-RPC 与确定性模型 fixture 做真实 Mac E2E：本地工作区 `wrk_284a4e8c20354168b1261cf1679cfec9` 的 Run `run_0895be7a9f68476e8437a7a5cd945558` 成功；本地 OpenCode 实际加载公共 Agent `zhi-fu-ce-shi`、Skill `ce-shi-ji-neng` 并调用公共 Tool `rpc-call` 得到 `UP`。浏览器验证本地/服务器工作区统一切换、检索、目录、历史证据和公共 Agent 去重。
- 最终用 `.env.test` / `test` profile 重启 backend、manager、frontend、ClickHouse 和 Mac 客户端。readiness/liveness 为 UP，3000、服务器 OpenCode 4096、本地 OpenCode 4106 均监听；实例 `lci_c8d77417e5a0462db2edbf8d4a433445` 已重连到 generation 144。数据库显示当前 commit `d2c941e5...6321`、状态 `CURRENT`，对应 `AVAILABLE` 完整包包含 14 Agent / 16 Skill / 6 Tool。

### Result

- 本地客户端现在可以在本机 OpenCode 中使用平台公共 Agent、Skill、Tool；当前公共提交即使没有后续更新也会初始化和展示，后续兼容版本由用户确认后原子更新，失败可回滚，服务器工作区行为保持不变。
- 实现、Mac 基线与真实公共能力调用已经验证；尚未在真实 Mac 运行态制造第二个公共 commit 完成“收到通知→托盘/网页确认→WSS 下载切换”整圈，当前该段由协议/Coordinator/Updater/前端自动化覆盖。真实麒麟 ARM64 无公网安装仍属于企业现场发布门禁，脚本封包测试不能替代硬件验收。
- 本次未新增部署节点，未修改 `.env*`、generated SDK 或 OpenCode 只读源码；提交前回顾全部 `.agents/session-log*.md` 近期记录，保留其它开发者成果，并排除 `.reasonix/` 与根目录 `node_modules/`。

## 2026-08-23 - 超管关闭后隐藏服务端 OpenCode 投影

### Why

- 超级管理员在运行管理中明确关闭有主用户的服务端 OpenCode 后，用户头像状态和服务器工作区仍继续显示，无法区分主动关闭与普通离线；头像菜单重启还需继续保持“只重启服务端 OpenCode”的既有语义。

### What

- 超管 stop 仅在公共停止程序完成 manager stop 与停止后 health 确认后，将精确匹配的 ACTIVE binding 以现有数据库 CAS 切为 INACTIVE；用户实例接口省略服务端投影，工作区查询隐藏服务端工作区但保留本地客户端工作区。普通健康失败、短暂离线和自动闲置停止不切换 binding。
- 超管 restart 与头像“重启服务端 OpenCode”允许按 INACTIVE binding 路由回原服务器，先恢复 ACTIVE 再复用公共启动程序；启动失败只补偿本次恢复的 binding。前端隐藏服务端状态并清理当前服务端工作区缓存/文件连接，重启入口始终保留。
- 同步 runtime、persistence、agent-web README/PACKAGE 与 HTTP API；复用既有 binding status 和 MyBatis SQL，没有新增表、migration、HTTP 路径、DTO 或 RunEvent。

### How

- JDK 25 下 runtime/API/persistence 定向后端用例共 137 项通过，前端 `FigmaShell` 与运行管理定向 86 项通过；agent-web typecheck、生产 build 和后端 26 模块 `mvn -q -DskipTests package` 均通过。
- 使用根目录 `.env.test`、`test` profile 和 JDK 25 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`；backend liveness/readiness 为 UP，frontend 3000 返回 200，登录 CORS 预检返回正确 allow headers，manager WebSocket 已连接且服务端 OpenCode health 达到 HEALTHY。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录并执行 `git diff --check`；只纳入本次投影与重启语义改动，不纳入 `.reasonix/`、根目录 `node_modules/` 等既存未跟踪目录。

### Result

- 超管明确关闭后，用户前端不再展示服务端 OpenCode 状态和服务器工作区；本地客户端状态/工作区不受影响。头像菜单仍明确为“重启服务端 OpenCode”，可恢复同一原服务器实例。
- 未修改 `.env*`、generated SDK、OpenCode 只读源码、部署节点、数据库结构、安全权限或性能协议；当前服务已完整启动并通过健康检查。

## 2026-08-23 - 修复公共能力发布基线并重建 Mac 测试客户端

### Why

- 用户发布 `wrui0823` 公共 subagent 后，服务器 OpenCode 已识别但本地客户端没有待安装能力；发布 commit `458ae2c1` 被标记为 `SERVER_ONLY`。
- 排查确认 Agent Markdown 合法且 `mode: subagent` 不影响能力包收集，阻断来自公共 Tool 依赖清单未继承以及本机可移植依赖字节版本漂移。

### What

- 在公共配置 Git 恢复并跟踪 `opencode/tools/package.json`，锁定 `@opencode-ai/plugin@1.18.4`，随后补充 `private=true`；公共仓库已推送提交 `f362cb7d` 和 `4d908037`。
- 使用平台现有 `opencode-node-runtime.package.json/lock` 执行 `npm ci --ignore-scripts`，把本地能力包生成服务切换到受控 1.18.4 依赖目录；未修改 `.env.test`，也未放宽 `SERVER_ONLY` 安全校验。
- 新公共提交 `4d908037` 已生成 `AVAILABLE` 完整包，含 17 Agent、18 Skill、10 Tool；`wrui0823.md` 和 `@opencode-ai/plugin` 字节均在包内，客户端实例进入 `UPDATE_AVAILABLE`，仍等待用户确认安装。
- 生成本地 Mac 测试包 `TestAgent-Mac-Test-20260823192454.tar.gz`，包含客户端 JAR、OpenCode 1.18.4、公共能力完整包和固定 JDK 25 的启动脚本；该制品位于 `.tmp/local-client-build/`，不作为生产签名 PKG/麒麟 DEB。

### How

- 后端、manager、frontend 与 ClickHouse 使用 JDK 25、`.env.test` 和既有构建制品完成重启，readiness 与 3000 页面正常；能力包数据库记录为 `AVAILABLE`，摘要 `9f41b777...deae54`。
- `mvn -pl test-agent-local-client -am test` 通过：local-client 91 项（1 项既有平台条件跳过），上游依赖模块 120 项全部通过。
- 实际能力包 SHA-256 为 `cfd486f0...f5bb4`，Mac 测试归档 SHA-256 为 `0fb4cc00...b776d`；客户端 `--version` 返回 `20260823192454`，内置 OpenCode 返回 `1.18.4`。

### Result

- 公共 subagent 已被完整打包并产生用户确认更新状态；它不会出现在 primary Agent 选择器中，但安装后应由本地 `/agent` 目录以 `mode=subagent` 返回。
- 当前 4106 仍保持旧能力版本，未发生自动安装，符合用户确认策略；真实“确认更新 → WSS 分片安装 → 4106 出现 wrui0823”留给用户从托盘或网页执行。
- 本次未修改平台源代码、API、RunEvent、数据库结构、Flyway、`.env*`、generated SDK 或 OpenCode 只读源码；根工作树其它未提交改动保持原状。

## 2026-08-23 - 补发在线客户端公共能力更新入口

### Why

- 网页实例状态已显示 `UPDATE_AVAILABLE`，但 Mac 托盘仍显示当前版本。客户端在能力包生成前完成版本上报时，服务端后续定时收敛只刷新网页通知，没有补发 `PUBLIC_CAPABILITY_AVAILABLE`，导致托盘错过更新入口。

### What

- `LocalClientPublicCapabilityCoordinator` 在每轮待更新状态收敛时，除幂等刷新用户通知外，也向当前持有 Java 上该实例的在线 generation 补发能力可用帧。
- 对连接查询与发送之间的断线/换代竞争按正常状态处理，等待重连上报或下一调度周期继续收敛；其它平台异常仍正常抛出。
- 新增回归测试，覆盖“客户端先在线、能力包后生成”时托盘消息仍能送达；同步 runtime 模块 README。

### How

- JDK 25 执行 `mvn -pl test-agent-opencode-runtime -am -Dtest=LocalClientPublicCapabilityCoordinatorTest -Dsurefire.failIfNoSpecifiedTests=false test`，4 项全部通过，reactor `BUILD SUCCESS`。
- 当前 Mac 客户端状态文件已实际收到 `4d9080373845` 待更新帧，记录 Agent 17 / Skill 18 / Tool 10；本机 backend readiness 为 UP、前端 3000 可访问。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录并执行目标差异检查，未纳入工作树中的工作区 Git 权限巡检等并行改动。

### Result

- 用户可直接在小兔子托盘点击“更新公共能力”，不再必须进入网页个人设置；网页入口继续作为同一确认操作的备用入口。
- 本次只修改服务端 WebSocket 通知补偿逻辑、测试和文档，不变更协议帧、HTTP API、RunEvent、数据库、Flyway、客户端制品、权限、部署节点、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-23 - 增加工作空间 Git 权限定时巡检与禁用提示

### Why

- 服务器应用工作空间和本地客户端工作空间的 Git 读取权限可能在创建后被撤销，现有选择器仍允许用户进入，直到后续 Git 操作才暴露失败；需要每两小时主动复核，并把确定失效项保留在列表中置灰、说明原因。

### What

- 新增 `workspace-management.git-access-inspection` XXL 任务，Cron 为 `0 0 0/2 * * ? *`：全局执行者分页并发复用服务器版本库只读预检，随后广播空载荷事件；各 Java 只检查自己实际持有连接且声明 `WORKSPACE_GIT_ACCESS_V1` 的本地客户端。
- 新增应用工作空间与本地工作空间两张用户级 Git 巡检投影表及 MyBatis XML 仓储；状态区分 `ACCESSIBLE/INACCESSIBLE/UNKNOWN`。只有 SSH key 缺失、认证失败、仓库不可读等确定结论写为 `INACCESSIBLE`，网络、超时、离线和旧客户端保持可选。
- Workspace/template API 以 additive 字段返回状态、固定原因、脱敏说明和检查时间；顶部本地/服务器选择器与底部应用工作空间级联菜单对 `INACCESSIBLE` 项置灰、阻止版本选择/新增并直接展示原因。页面存续时每两小时刷新列表投影。
- 同步 domain、workspace、runtime、local-client、persistence、API、XXL、前端模块 README/PACKAGE，以及 HTTP API、事件、数据库、架构和测试说明。

### How

- JDK 25 下相关后端 38 项通过，包含本地 Git 分类、服务器/本地巡检编排、Flyway 命名、根目录 `.env.test` 真实 PostgreSQL MyBatis 集成和 MySQL Testcontainers XXL migration，全部 0 failure / 0 skip；前端 FigmaShell/WorkbenchFooter 87 项通过，agent-web typecheck 通过。
- 执行完整 26 模块后端 package、用户手册与 agent-web production build，并按 `.env.test` / `test` profile 重启 backend、frontend、ClickHouse 与 OpenCode manager；liveness/readiness 为 `UP`，3000 返回 200，登录 CORS 正常，manager WebSocket 已连接且 OpenCode health 最终为 HEALTHY。
- PostgreSQL migration `V20260823191023` 已在固定验收库执行，Flyway checksum 为 `664897016`，原始 SHA-256 为 `12cfe3bbaa4b0d562f2dca2a69290180c81d42aca79b1ff4aaf6ad5cf32419e2`，文件名、版本和字节已冻结。提交前回顾全部 `.agents/session-log*.md` 近期记录并执行 `git diff --check`。

### Result

- Git 权限明确失效的服务器与本地工作空间仍可见但不可选，并显示固定脱敏原因；瞬态故障、离线客户端和滚动升级旧节点不会被误禁用，权限恢复后下一轮巡检会清除灰态。
- 本次新增 additive HTTP DTO 字段、低敏服务器广播、本地客户端 capability、PostgreSQL migration 与 XXL MySQL migration；不新增部署节点，不修改 `.env*`、generated SDK 或 OpenCode 只读源码。当前服务已完整启动并通过健康检查，无未完成项。

## 2026-08-23 - 收敛客户端灰度可见性并补齐头像本地操作

### Why

- 客户端灰度原先只约束下载入口，关闭后本地实例、工作区和个人设置仍可见；同时用户头像只提供服务端 OpenCode 重启，缺少语义明确的本地重启与 Client Key 撤销入口。
- 超级管理员关闭服务端 OpenCode 必须与本地 OpenCode 完全隔离；灰度隐藏当前本地工作区时，需要优先回到进入本地前的服务器工作区，而不是启动、停止或切换任何 OpenCode 进程。

### What

- 客户端灰度统一控制客户端下载、客户端实例状态、本地工作区、个人设置客户端区及头像客户端操作的可见性；关闭只隐藏投影，不撤销 Client Key、不关闭控制 WSS、不停止本地 OpenCode。
- 头像菜单新增逐实例“重启本地 OpenCode”和“撤销 Client Key”，既有入口明确保持“重启服务端 OpenCode”；本地重启复用现有客户端命令，撤销复用现有凭据接口与工作区清理事件。
- 灰度隐藏已选本地工作区时，依次恢复进入本地前的可用服务器工作区、当前应用最近服务器工作区，否则清空选择；服务端实例已被超管关闭时不自动选择服务器工作区或启动服务端 OpenCode。
- 后端实例与工作区查询加入灰度可见性门禁，并补充“服务端关闭保留本地投影、灰度关闭保留服务端投影”的回归测试；同步 API、架构、数据库、安全、后端模块、前端及用户手册稳定说明。

### How

- 前端全量 Vitest 150 个文件通过，2208 passed / 1 skipped；agent-web typecheck 与 production build 通过。后端 system-management、API、persistence 定向 reactor 共 23 项通过，JDK 25 构建成功。
- 使用根目录 `.env.test`、`test` profile 和 JDK 25 完整重启 backend、frontend、ClickHouse 与 OpenCode manager；liveness/readiness 为 `UP`，前端 3000 返回 200，本地 OpenCode 4106 在灰度关闭前后保持同一 PID 监听。
- 已登录浏览器实测灰度开启时头像同时显示本地重启、服务端重启和 Client Key 撤销；关闭后本地实例、本地工作区和个人设置客户端区消失，只保留服务端实例与服务端重启，测试账号随后恢复到验收前的灰度开启状态。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录并执行 `git diff --check`；精确隔离工作树中并行的工作区 Git 权限巡检、`.reasonix/` 与根目录 `node_modules/` 改动。

### Result

- 客户端灰度现在只表达“客户端功能是否可见”，不会改变客户端、Client Key 或本地 OpenCode 的真实运行状态；服务端关闭、服务端重启和本地重启的语义与影响范围彼此独立。
- 既有 HTTP 路径和 DTO 保持兼容，仅扩展现有灰度接口语义；不涉及 RunEvent/SSE、数据库结构、Flyway、部署节点、性能模型、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-23 - 简化托盘公共能力提示并展示更新进度

### Why

- 托盘用 `A17 S18 T10` 和 commit 展示完整包总数，普通用户无法理解；点击更新后也缺少持续状态和明确完成反馈。

### What

- 托盘公共能力项改为“有更新、更新中、已是最新、更新失败点击重试”四种动作状态，不再展示 commit 和 Agent/Skill/Tool 数量。
- `PENDING/DOWNLOADING/APPLYING` 期间禁用重复点击；`FAILED/ROLLED_BACK` 恢复重试入口。
- 更新成功或失败时发送 macOS 系统通知；确认弹窗只保留变更类型、本地 OpenCode 是否重启及本机权限提示。

### How

- 复用 `LocalClientPublicCapabilityStore.State` 现有状态和托盘 2 秒内存快照刷新，没有新增轮询、协议帧或持久化字段。
- JDK 25 定向执行托盘、能力 Store 与 Updater 测试共 13 项通过；最终托盘用例 4 项复跑通过，Maven reactor 均为 `BUILD SUCCESS`。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录并执行目标差异检查；未纳入并行遗留的 Git 失败分类补充改动。

### Result

- 用户点击更新后可直接从小兔子菜单看到更新中/完成/失败状态，成功或失败还会收到系统通知，不必进入个人设置。
- 本次只调整本地客户端托盘展示、测试和模块文档，不变更 HTTP API、RunEvent、WebSocket 协议、数据库、Flyway、权限模型、部署节点、`.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-23 - 修复 Git 巡检 SSL 失败误归因与旧缓存复用

### Why

- 手工执行工作空间 Git 巡检后，“F-COSS 移动端”仍未置灰。真实 `git ls-remote` 已返回 LibreSSL `SSL_ERROR_SYSCALL`，但失败分类器未识别该 SSL/TLS 文案，最终被持久化为 `UNKNOWN`；前端按既定契约只禁用 `INACCESSIBLE`，因此表现为仍可选择。
- 调度巡检还可能复用本轮开始前由页面查询产生的成功缓存，导致权限刚失效时本轮未实际探测远端。

### What

- Git 失败分类器补充 LibreSSL、GnuTLS、Schannel、TLS 握手和证书校验关键字，统一归为 `NETWORK_UNAVAILABLE`。
- 明确状态语义：只要远端 Git 探针已完成且失败，无论认证、仓库权限、网络、DNS、SSL/TLS、超时或其它 Git 错误，均写为 `INACCESSIBLE` 并置灰；只有旧客户端、离线、路由变化、RPC 异常或探针未形成有效结论时才写 `UNKNOWN`。
- 调度巡检要求成功结果不早于本轮开始时间，不再复用旧成功缓存；同一轮并发探测结果仍可安全复用。
- 同步 common、domain、local-client、runtime、workspace、frontend README 及 HTTP API、XXL 集成和数据库说明；未改变 DTO 结构、事件载荷或数据库结构。

### How

- JDK 25 定向执行失败分类、本地探针、workspace 巡检和任务处理测试；最终 workspace 服务 104 项通过，相关补充批次 115 项及任务处理批次 3 项均通过。
- 前端按根目录仓库脚本执行全量 Vitest，150 个测试文件通过，2208 passed / 1 skipped；一次绕过仓库配置的子包 Vitest 命令因缺少 DOM 环境失败，已按正确脚本复跑确认并非功能回归。
- 使用根目录 `.env.test` / `test` profile 完整构建并重启 backend、frontend、ClickHouse 与 OpenCode manager；backend health/readiness 为 UP，前端 3000 返回 200，manager 与 OpenCode 健康。
- 真实调用 XXL executor 手工执行巡检后，固定验收库中“F-COSS 移动端”相关用户投影均更新为 `INACCESSIBLE / NETWORK_UNAVAILABLE`，脱敏原因明确为 Git 远端网络或 SSL/TLS 连接失败。

### Result

- “F-COSS 移动端”现在会按真实不可访问结论置灰且不可选；`UNKNOWN` 只保留给没有完成有效 Git 探测的情形。
- 本次不新增部署节点，不变更 HTTP API 结构、RunEvent、WebSocket 协议、数据库、Flyway、权限模型、`.env*`、generated SDK 或 OpenCode 只读源码；未登录浏览器会话，因此没有冒用凭据做页面点击，UI 行为由既有禁用组件测试与真实巡检投影共同验证。

## 2026-08-23 - 收口企业离线发布迁移与部署门禁

### Why

- 以 2026-08-18 上一企业包为基线重新审计当前 `release` 时，真实 PostgreSQL 历史升级测试暴露自动化引用旧基线缺少上一版已存在的 `workspaces` 表；前端 API 测试请求遗漏新必填 `alias`，多个部署验收夹具也没有跟上 Node lock、OpenCode 观测插件、客户端签名公钥、ClickHouse/Memory 密钥继承和现行文件路由安全边界。

### What

- 只补齐测试和发布验收夹具：自动化引用历史库增加 `workspaces` 基线；前端自动化引用请求增加 `alias`；多后台与自动节点夹具补齐 Node lock、观测插件和已部署组件密钥字段；单配置夹具补齐客户端公钥与 ClickHouse 配置。
- 用户 OpenCode 场景改用负数测试主键、现行超级管理员限时只读排查路由和数据库权威 backend server，场景间释放 manager 端口；旧远端脏 binding 按公共路由规则验收 `503 OPENCODE_UNAVAILABLE`，明确禁止本机降级。

### How

- 真实 PostgreSQL Flyway 兼容链 31/31、XXL MySQL Testcontainers 7/7、ClickHouse 集成 4/4 通过；相关持久化定向 23/23 通过。后端 25 个 reactor 模块全绿，应用模块 94 项中因同一进程注入 `.env.test` 导致两个默认配置隔离断言受环境污染，随后在干净进程复跑 20/20 通过。
- 前端全量 Vitest 150 个文件通过，2208 passed / 1 skipped；修正夹具后 backend-api 123/123、全 workspace lint、typecheck、生产 build 通过。
- 客户端单壳 DEB、签名发布、安装引导、静默升级/降级/自动回滚与凭据复用通过；多后台、自动节点、Nginx、ClickHouse、MySQL、Redis、XXL、归档卫生、日志采集、单配置和 OpenCode 用户进程门禁均复跑通过。

### Result

- 未修改任何已发布 PostgreSQL/ClickHouse/XXL migration 字节，只修复验收基线；不新增 API、事件、数据库结构、部署节点、生产配置或依赖，不修改 `.env*`、generated SDK 和 OpenCode 只读源码。
- 企业公共能力权威库固定为 `4d9080373845ffece1d6d055a3b042ad383a5aab`，现有能力包含 17 个 Agent、18 个 Skill、10 个 Tool 和 22 个裁剪后的离线 Node 依赖，未包含密钥、`opencode.jsonc`、`.git` 或原始仓库 `node_modules`。
- 正式整包仍需存量客户端签名私钥；本机只有公钥 SHA-256 `6305689946819f97ecb8a3112bff8eb18386a8011207b31975c188860bea500f`，不得用临时密钥轮换后冒充可升级交付。

## 2026-08-23 - 固定组织客户端签名密钥并修复四制品发布门禁

### Why

- 企业客户端仅有两台安装，用户明确接受本轮全量替换，因此允许执行一次签名密钥轮换；后续仍需使用同一组织私钥，避免每次打包都重新找旧密钥。
- 新客户端已把公共 Agent/Skill/Tool 离线能力作为第 4 类 `PUBLIC_CAPABILITIES` 制品，但总发布验收脚本仍硬编码只允许 3 类，导致正确生成的客户端分发目录在 `--zip-only` 阶段被误拒绝。

### What

- 组织 RSA 私钥、公钥固定保存在当前仓库工作树 `.secure/`，目录与 PEM 已通过本地 `.git/info/exclude` 和既有 `*.pem` 规则排除，不提交 Git，也不得进入发布归档；组织公钥 DER SHA-256 为 `6d294535e5bf4c2a0ea2ba3ae8b1fc9502444de7639df3607d5e360846c9ca62`。
- 客户端分发验收改为精确要求 `CLIENT_JAR`、`JDK`、`OPENCODE`、`PUBLIC_CAPABILITIES` 四类制品，并逐项核对路径、大小和 SHA-256。
- 增量发布夹具补齐公共能力制品，并新增篡改该制品后必须验收失败的负向回归；部署 README 同步记录四制品门禁。
- 外层双后台封装必须显式读取匹配的组织 PEM 公钥，在临时副本中写入 `.4/.114` 同一版本管理公钥；缺少公钥、格式错误或两台最终值不一致时拒绝打包，私钥不被读取或封装。

### How

- `bash -n`、`git diff --check` 通过。
- `tools/verify-internal-incremental-components.sh` 通过，覆盖全量、复封、正常增量、已部署 baseline 复用及组件单独更新。
- `deploy/internal/tests/local-opencode-client-package-test.sh` 通过，覆盖麒麟 ARM64 单壳 DEB、不可变签名 release、catalog、公共能力离线制品和安装引导。
- `tools/verify-internal-two-backend-complete-package.sh` 通过，新增覆盖旧节点包自动注入同一组织公钥以及未提供公钥必须失败，并继续覆盖 Flyway、敏感配置脱敏、固定名覆盖和增量组件复用。

### Result

- 四制品发布门禁修复并完成定向运行验证；不变更 HTTP API、事件、数据库、Flyway、部署节点、前后端业务代码、generated SDK 或 OpenCode 只读源码。
- 复用 2026-08-18 节点配置时发现旧包尚无客户端版本管理公钥，现已由外层封装门禁补齐，避免覆盖现场 `backend.env` 后新客户端 release 无法验签。
- 本轮客户端需在两台已安装机器上全量卸载/替换一次；之后只要固定组织私钥未丢失，即可继续正常签名升级。
- 最终内层 ZIP SHA-256 为 `a13f0cc83850d5a1eb6eecfb37caa378e3d8cf67ebe277371b1e66390ef62397`，外层固定名双后台包 SHA-256 为 `99875cd9cb2dadfafb95e0219480a40617f4e769e00d9a8ec5360b41c9bb072f`；客户端版本为 `20260823213628`，manifest SHA-256 为 `982c55bea7c1f760bf9f036c4dd10aaf7449dbcd1b9ec7480cf97074a3e1b18e`，公共配置提交为 `4d9080373845ffece1d6d055a3b042ad383a5aab`。
- 最终内外层 SHA/结构/嵌套一致性、147 份 Flyway SQL 的源码与 JAR 字节、四制品客户端、公钥签名、TCDS 固定域名、Qwen 优先级、离线 Node 依赖、私钥不入包及 Linux GNU tar 归档卫生均通过；目标企业 PostgreSQL 与 XXL MySQL 完整 `flyway_schema_history` 尚未取得，正式部署仍须先通过该现场门禁。

## 2026-08-24 - 修复 OpenCode 恢复超容量断连并增加容量预警

### Why

- `.114` 在 100 个运行进程后，保留 ACTIVE binding 的旧用户可通过 `bindingRecovery=true` 原端口恢复到 101；Java 把真实运行数误当成调度上限校验并拒绝 101/100 心跳，造成 manager WebSocket 持续断开和 TestAgent 不可用。
- 需要把闲置进程关闭阈值从 15 天改为 10 天，并在单容器容量达到 80% 时用既有通知中心提醒全部有效超级管理员，为扩容评审留出时间。

### What

- `OpencodeContainer` 允许实际 `currentProcesses` 在端口池范围内暂时超过 `maxProcesses`，可调度容量钳制为 0；首次分配仍只选择未满容器，恢复路径继续固定原 binding。
- 新增 `OpencodeCapacityNotificationService` 和 `OPENCODE_CAPACITY_WARNING/NONE` 通知：80% 预警、70% 以下失效、稳定去重、全部 ACTIVE 超管分页投递、Java 重启首份高位/恢复态快照幂等收敛，通知异常 fail-open。
- 闲置关闭阈值调整为 10 天；新增 PostgreSQL 通知类型 migration 和 XXL MySQL 默认任务名称前向 migration，保留既有 V9 原始字节。
- 前端通知中心只展示容量预警并走通用已读，不解释内部容器目标；同步 runtime、notification、persistence、前端、API、数据库、XXL 与多后台部署文档，并固化扩节点判据。

### How

- JDK 25 定向后端测试覆盖领域容量、候选过滤、manager 101/100 心跳、80/70 滞回、Java 重启收敛、全超管分页、通知幂等/失效和十天清理；相关批次全部通过。
- PostgreSQL Testcontainers 3/3、XXL MySQL Testcontainers 7/7 通过；通知单元测试 14/14、最终 runtime 定向 24/24 通过。
- 前端通知组件 Vitest 9/9、agent-web typecheck、生产 build 和 Chromium 容量通知已读 E2E 1/1 通过；后端 `test-agent-app` reactor 跳过测试打包成功，`git diff --check` 通过。

### Result

- 101/100 真实状态不会再关闭 manager 控制连接，容器继续可观测但不会接收新用户；新增用户在 `.114` 满而 `.4` 未满时会去 `.4`，全部候选满时仍拒绝。
- 80% 只触发评审：十天清理后仍持续高位、无安全提上限余量、预测满载早于交付周期加两周、全节点同时高位或 N-1 规划容量不足时进入新增节点计划；新增节点不会自动迁移 `.114` 的存量 binding。
- 新通知枚举不能由旧 Java 解析，本版企业上线必须维护窗口先停 `.4/.114` 两台旧 Java，再以同版本依次启动；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，未新建分支。
- 本地只完成代码、真实存储测试和应用/前端构建，未生成外网 Mac 企业完整包，也未部署 `.4/.114/.2`；目标 PostgreSQL 与 XXL MySQL `flyway_schema_history` 仍需在正式打包部署前收集并验证。

## 2026-08-24 - 配置企业 SkillHub 并隔离本地启动

### Why

- 企业部署需要按接口文档固定使用 SkillHub 测试环境，并在两台后台节点启用；Access Key 属于敏感配置，不能写入 Git 或普通发布包。
- 本地研发可能无法访问企业 SkillHub，不能让默认研发启动或企业远端短时故障被同步网络请求阻断。

### What

- 企业 `backend.env` 模板、单后台渲染、双后台封装与节点部署链统一补齐 SkillHub 开关、测试环境地址、超时及同步周期；两台节点的 Access Key 必须一致，只能从已安装的敏感 `backend.env` 继承，缺失或过短时在 Java 重启前失败。
- 保持应用配置的本地默认开关关闭；补充网关测试，锁定关闭时无需企业配置、启用且远端不可达时构造过程不发起网络请求。企业同步失败继续由定时任务记录脱敏事件并周期重试。
- 同步企业部署说明、完整升级手册、后端部署文档及安全规范，明确首次配置、脱敏预检、故障语义和手工同步入口。

### How

- `SkillHubHttpGatewayTest` 3/3 通过；单后台配置、双后台整包、多后台节点、自动节点和运行时敏感配置继承共 5 组 Shell 验收脚本通过，相关脚本 `bash -n`、`git diff --check` 和明文密钥扫描通过。
- 本机 JDK 25 已确认；当前 `.env.test` 指向 `127.0.0.1:15432/test_agent`，不符合项目固定的 `192.168.8.100:15432/testagent_dev` 验收库要求，因此未违规切换数据库或重启真实本地服务。

### Result

- 本地未配置 SkillHub 时默认关闭，不影响启动；企业环境具备合法地址和密钥后，即使 SkillHub 暂不可达也不阻断 Java 启动/readiness，只影响目录同步并自动重试。启用但缺少合法地址或密钥仍按安全门禁拒绝启动。
- 本次不新增部署节点，不变更 HTTP API、RunEvent、WebSocket、数据库、Flyway、性能模型、generated SDK、OpenCode 只读源码或 `.env*`；只调整企业部署配置、安全门禁、文档和回归测试。尚未生成新企业包，也未部署 `.4/.114/.2`。

## 2026-08-24 - 兼容 SkillHub 现场目录技术标识

### Why

- 内网 `/list` 已成功返回 185 条真实目录，但手工同步报 `SKILLHUB_UNAVAILABLE / SkillHub 目录条目标识无效`；现场条目包含 `SLB_ENV_DEEPCHECK`、`bin-file_compare`、`threadSafe-Refactor` 等合法名称。
- 现有应用服务额外写死小写短横线规则，误拒绝其中 14 条；项目其它 Hub 路径已允许大小写字母、数字、点、下划线和短横线。

### What

- 把外部目录校验与既有 Hub 技术 ID 校验合并为同一 `Pattern`，保留 SkillHub 原始 `name`，允许安全的大写、下划线、点和短横线；斜杠、空白和其它路径危险字符仍失败关闭。
- 网关测试改用现场字段形态，锁定字符串版本 `"0"`、ISO Offset 时间和 `url`、图片、审批记录等附加字段兼容；应用服务测试锁定三类现场名称原样同步及危险名称拒绝。
- 同步 integration/workspace README、HTTP API 与安全规范，明确未知附加字段忽略和技术 ID 边界。

### How

- 用 `jq` 核验用户提供的现场 JSON：185 条、全部符合安全技术 ID、14 条不符合旧小写短横线规则、名称和 ID+版本均无重复。
- JDK 25 定向测试 `AgentSkillHubApplicationServiceTest,SkillHubHttpGatewayTest` 通过；`test-agent-workspace-management,test-agent-integration` 两个相关模块及上游 reactor 全量测试通过。
- `git diff --check`、冲突标记和明文 SkillHub 密钥扫描通过。

### Result

- 现场 185 条目录在重新部署后可通过业务校验，原始名称不会被 slug/改名；远端下架隐藏和已引用保留语义不变。
- 不变更 HTTP 路径/DTO、RunEvent、WebSocket、数据库、Flyway、部署节点、generated SDK、OpenCode 只读源码或 `.env*`。
- 当前 `.env.test` 仍指向 `127.0.0.1:15432`，不符合固定验收库要求，未修改环境文件或启动真实服务；企业包尚未重建部署，`/download/{id}?channel=3` 也尚待内网真实 ZIP 验证。

## 2026-08-24 - 修复企业 Trace 布局与伪轨迹目录

### Why

- 企业控制台可用宽度小于研发视口时，Trace 三栏仍带固定最小宽度，导致目录标题换行、轨迹与检查器错位；用户还要求左右面板都可手工拉伸。
- ClickHouse 目录有大量无 `runId`、`sessionId=unknown` 的 OpenCode 进程广播，前端又把缺失 Agent 回退成 `opencode`、把所有未完成状态显示为“不完整”，造成重复伪记录和状态误解。

### What

- Trace 三栏改为控制台容器内的响应式网格，取消页面级固定最小宽度；目录与检查器保留独立折叠，并增加左右分隔线拖动、键盘微调和双击复位。紧凑纯色 DSH 三泳道、时间框选和色块直选保持不变。
- 目录只分页展示已关联平台 `runId` 且 Session 有效的 Trace；已知 opaque Trace ID 的精确读取不变。OpenCode 1.18.4 插件不再把无 Session 的进程广播入队，`chat.message.input.agent` 作为 Agent 权威名称，缺失值显示为“未识别 Agent”。
- `complete=true` 但仍有 pending 的历史记录按“待上传”处理，不计入完整率/完整水位；新增 ClickHouse 前向 migration，只允许已关联 Run 的插件目录冻结旧 RunEvent 能力事实切换点。三类企业封包/部署脚本锁定 migration 文件名和 SHA-256。

### How

- 插件真实 1.18.4 fixture 18/18、`TraceArchiveServiceTest` 8/8、ClickHouse Testcontainers 4/4、TraceView 定向测试、agent-web 全量测试/类型检查/生产构建均通过；企业脚本语法、双后台完整包结构门禁和麒麟 ARM64 本地客户端包测试通过。
- 使用仓库当前 `.env.test` 和 ClickHouse 完整重启，backend readiness、前端 3000、manager OpenCode 1.18.4、ClickHouse 正常；最终应用 JAR 内新 migration SHA-256 为 `7e0ae2c427a1256268d682a9be2be48b1c839b240ca05ec857e0e934d1448f9f`。
- 真实浏览器在 1366px 将三栏 `232/698/280` 拖到 `260/646/304`，在 1100px 将 `218/466/260` 拖到 `246/414/284`，两档横向溢出均为 0。目录原始 89 条中 70 条无 Run、37 条 unknown Session；过滤后页面显示 19 条，伪 `opencode` Agent 为 0。

### Result

- 企业控制台 Trace 可在较窄视口正确排版，左右栏可折叠和手工拉伸；目录不再被进程启动/重连广播刷出大量 `opencode/不完整`，进行中、待上传、丢弃不完整和完整状态分开显示。
- 不修改 OpenCode 只读源码，不改变 RunEvent/SSE/聊天链路，不新增部署节点、PostgreSQL/Redis 字段或正文存储位置；涉及 ClickHouse 前向 migration、运营覆盖口径、前端兼容性与企业封包校验。
- 当前 `.env.test` 实际连接 `127.0.0.1:15432/test_agent`，不是规范指定的 `192.168.8.100:15432/testagent_dev`；未修改受保护环境文件，因此规定 PostgreSQL、企业 `.4/.114/.2` 现场安装与浏览器缓存复验仍未完成。历史伪目录保留而未物理删除。

## 2026-08-24 - 修复企业本地客户端域名接入与用户级安装

### Why

- 上一版本客户端运行时下载成功，但接入阶段固定访问 `https://122.233.30.2`；现场只提供 `http://mimo.sdc.cs.icbc:9996`，因此 WSS 连接到 443 端口时直接 `Connection refused`。
- 两名麒麟用户均无超级管理员权限，不能依赖 `dpkg -i` 把启动器安装到 `/usr/bin`；Mac 生成 DEB 时还会把扩展属性写入 PAX header，麒麟双击安装会出现不兼容告警。

### What

- 客户端封包增加显式 `allow-insecure-control` 契约：默认继续只接受 HTTPS/WSS，当前受控企业内网只有在客户端和两台 Java 同时设置 `true` 时才允许 HTTP/WS；下载与控制入口统一为 `mimo.sdc.cs.icbc:9996`。
- DEB 数据包复用统一归档元数据清理，去掉 macOS `LIBARCHIVE.xattr`/`SCHILY.xattr` PAX header；普通用户可把 DEB 中的稳定启动器提取到 `~/.local/bin`，生成的 user systemd 与桌面入口会自动使用该路径。
- 单后台、双后台节点封装、CORS、部署模板、配置示例、安全规范和本地客户端部署说明同步更新；本地客户端组件指纹 schema 升级，确保 URL/明文开关变化会触发重打。

### How

- `local-opencode-client-package-test.sh` 覆盖 HTTP 未显式批准时拒绝、域名 HTTP 配置、DEB 无扩展属性、用户级 systemd/桌面入口；`local-opencode-client-update-test.sh` 覆盖稳定启动器更新/回退/自动回切。
- 单后台配置、双后台完整包结构、归档卫生门禁全部通过；变更脚本语法、`git diff --check` 和冲突标记检查通过。
- 首轮构建复核发现容量预警 PostgreSQL migration 与清理描述 XXL migration 虽已进入 JAR，但旧封包清单未锁定；继续审计还发现上一轮已部署的应用自动化、本地客户端公共能力、工作空间 Git 巡检等 8 条 PostgreSQL migration 与 XXL V14 未纳入三层清单。现已在内包构建、双后台外包和目标机安装三层全部补齐文件名及固定 SHA-256 门禁，并把部署手册基线更新为上一轮 `f5c88d8ca` / `a13f0cc8...`。
- 增量组件计划确认 backend、frontend、worker runtime 与 local client 需要重建，toolbox 指纹未变并复用；独立 ClickHouse、Mem0、BGE、pgvector、LobeHub 制品不随本轮前后台包重复携带。

### Result

- 客户端生成配置将使用 `http://mimo.sdc.cs.icbc:9996` 与 `allowInsecureControl=true`，旧 runtime 已下载但未 enroll 的用户只需替换用户级启动器后重新 enroll，无需 sudo 或删除既有 runtime。
- 以发布提交 `aa868fab9c38f86d042fbda1074bec9078d787f6` 完成增量企业包：内层 SHA-256 为 `4a7c5112fff4c6e25b8e9148167c6ddca599419c8eccf3631c7a3e65813e1f8a`，外层 SHA-256 为 `be61452d99e17a19bcc2e776644a53d1b7e991e2640a3502b09fe729d2e7ee1e`，外层嵌套内层字节一致。
- 客户端版本为 `20260824124347`，manifest SHA-256 为 `24a190614334a1733e257b7b14b89d96cc824a27462c5c525caa3d0dd06f7336`；DEB 为 arm64、无 macOS xattr PAX header。为严格落实“没变动的不打包”，重建签名目录时仅保留这一个当前 release，不再携带上一版本 JDK/OpenCode/公共能力副本，内层包由约 1.2 GiB 降为 916 MiB。
- 前端生产编译、worker 镜像与 Codex/OpenCode/Tool runtime、客户端签名分发、内外层 Flyway 固定 SHA、最终目标机 `--validate-only`、外层 `unzip -tq`、单客户端 release 目录与三节点域名/TCDS/ClickHouse/Mem0 配置均通过。
- 本轮包含 backend、frontend、确有变化的 worker runtime 和 local client；toolbox 为 `reuse`，独立 ClickHouse/Mem0/BGE/pgvector、Python libs 与 LobeHub 未进入包。目标 PostgreSQL、XXL MySQL、ClickHouse history 核验和 `.4/.114/.2` 现场部署仍待执行，禁止用 Flyway `repair` 或 `outOfOrder` 绕过。

## 2026-08-24 - 修复企业客户端在空版本策略下反复断连

### Why

- 企业现场日志显示客户端每次完成 `REGISTERED` 后约 0.1–0.2 秒即断开并重连；DBeaver 只读核验确认三个版本管理表均存在，但全局策略和该用户策略均为空。
- 服务端在没有有效目标版本时仍发送 `revision=0` 的空 `VERSION_POLICY`，已发布客户端按协议拒绝该帧并主动关闭连接，继而造成网页状态红点、重启按钮置灰和下载入口短暂闪现。

### What

- 服务端没有全局或用户版本策略时不再发送空版本策略帧，并使旧的本地客户端升级通知按“策略已满足”失效；配置了有效目标版本时保持原有检查和升级行为。
- 新客户端严格兼容历史空策略哨兵，只接受 `revision=0 + targetVersion=null + direction=SAME + force=false` 的完整组合；其它非法版本策略仍拒绝。
- 客户端断连日志增加脱敏的根因码，版本策略错误记录为 `VERSION_POLICY_INVALID`，其它异常只记录根异常类型，不输出凭据或服务端消息。
- 同步本地客户端、协议、runtime README，以及事件协议和本地客户端部署排障文档；明确空策略是合法初始状态，禁止为恢复连接伪造策略或修改 Flyway 历史。

### How

- 定向测试 `LocalClientVersionCheckTest` 与 `LocalClientUpdateCoordinatorTest` 共 30 项通过；相关 Maven reactor 全量测试中本地客户端 94 项（1 skipped）、OpenCode Runtime 978 项全部通过。
- 相关模块打包成功，并生成 `backend/test-agent-local-client/target/test-agent-local-client.jar`，SHA-256 为 `c676faacead741d615f5e723c17c33678f8f77af98cb8ca32c8d8cd763efbe70`。
- 使用 JDK 25、仓库现有 `.env.test` 和 `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP，前端 3000 返回 200，登录 CORS 正常，manager WebSocket 已连接且 OpenCode 健康。

### Result

- 空版本策略不再使已发布客户端进入注册成功后立即断连的重试环；现场无需插入版本策略数据，部署同一后端修复到 `.4/.114` 后旧客户端即可恢复连接。
- 本次只调整既有 WebSocket 版本策略的兼容语义和日志，不变更 HTTP API、RunEvent 类型、数据库、Flyway、部署节点、权限、generated SDK、OpenCode 只读源码或 `.env*`。
- 本机代码与运行验证完成；尚未生成新的企业双后台增量包，也未在 `.4/.114/.2` 现场部署或复验。

## 2026-08-24 - 修复运行拓扑图零尺寸 resize 控制台异常

### Why

- 企业控制台出现 `Cannot read properties of null (reading '0')`，堆栈由 ECharts `resize` 进入 view 坐标系 transform 复制；运行管理功能页通过 `v-show` 常驻挂载，失活后的拓扑容器宽高为 0，导致 ECharts 6 生成不可逆矩阵。

### What

- `RuntimeManagementPanel` 向拓扑图传递既有 `pageActive`；`RuntimeTopologyGraph` 在初始化、异步加载完成后的实例创建、更新和 resize 前统一校验页面活动、DOM 仍连接且容器宽高大于 0，恢复可见后按最新 overview 补绘。
- 增加组件生命周期回归测试，覆盖失活页不初始化、宽度为 0、高度为 0、恢复可见和再次失活的 resize 门禁；同步前端 README、包说明、前端规范和模块图。

### How

- `runtime-topology-graph.test.ts` 4/4、agent-web TypeScript 检查和 production build 通过；`git diff --check` 通过。
- 现有本地 Vite 前端在 `http://127.0.0.1:3000` 返回 200，临时浏览器访问登录页无控制台错误；浏览器没有登录态，因此未进入真实运行管理页面，现场交互验证为部分验证。

### Result

- 功能页隐藏或拓扑容器零尺寸时不再调用 ECharts view 坐标系布局，恢复后仍显示最新拓扑；不改变拓扑交互、overview 数据或缩放能力。
- 本次不涉及 HTTP API、RunEvent、WebSocket、数据库、Flyway、部署节点、性能模型、安全、环境配置、generated SDK 或 OpenCode 只读源码；工作区并行存在的本地客户端改动未纳入本次提交。

## 2026-08-24 - 同步企业公共配置并重打麒麟客户端

### Why

- 企业麒麟客户端仍使用带 Codex 联调项的旧公共能力基线，用户提供 `testagent.7z` 作为企业 Agent/Skill/Tool 权威内容，并要求同步 Gitee `master` 后重新打包。
- 客户端 Swing 对话框仍使用默认 Metal 风格和 Java 信息图标，在麒麟桌面呈现明显的老旧系统观感。

### What

- 客户端增加统一桌面主题：macOS 使用系统 Aqua，麒麟/Linux 优先使用 JDK 自带 Nimbus，并统一字体、TestAgent 红色主色和控件间距；会话进度与能力确认移除遗留信息/警告图标。
- 公共能力构建器把 `.py` companion 纳入敏感文本扫描并补充回归测试。按用户明确要求，企业归档的 `tcds-enter-case-query.ts` 保持原字节，包括 `/usr/local/bin/python3` 依赖，不做行为改写。
- 企业归档同步到 Gitee `master` 最终提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`；删除旧 Codex/个人联调 Agent/Skill/Tool，保留 8 Agent、16 Skill、8 Tool，并补平台离线构建必需的 `tools/package.json` 锁定声明。
- 生成签名客户端 release `20260824155652`，公共能力摘要为 `a1aa92a93883cb99589bb8a9d0d53a0c0e057bb69d65598e11ac74f9e8a1cf71`；JAR、能力包和 DEB 的版本化副本放入 `~/Desktop/mimoagent/0709`。文档明确能力包是与 JAR 并列签名的 sidecar，存量实例只替换 JAR 不会改变已激活公共版本。

### How

- JDK 25 按 Java 21 release 运行客户端 reactor：本地客户端 96 项通过、1 项跳过；能力构建器 6 项通过。`local-opencode-client-package-test.sh` 与 `local-opencode-client-update-test.sh` 均通过，覆盖不可变签名 release、DEB、能力 sidecar 下载、升级/降级和自动回滚。
- 实际构建能力包并检查 manifest、路径与原生文件：8/16/8，3735 个归档条目，无 Codex、`opencode.jsonc`、AGENTS.md 或原生扩展；包内 TCDS 文件与上传归档逐字节一致。
- 新 release 的 manifest 及四个 artifact 签名全部通过稳定公钥验证；JAR 实际执行输出版本 `20260824155652` 并包含 `LocalClientDesktopTheme.class`。已回顾全部 session log 近期条目，并隔离工作区同时出现的 SkillHub 未提交改动。

### Result

- Gitee `master`、本机公共运行 clone 和新客户端首次安装基线均指向企业配置 `81605f245d...`，旧 Codex 联调项不再进入新能力包；新装使用完整 release 初始化，存量客户端仍按产品约束由用户确认公共能力更新。
- 不新增部署节点，不变更 HTTP API、RunEvent、数据库、Flyway、OpenCode 源码、generated SDK 或 `.env*`。样式自动化测试和 Mac Aqua 路径已通过，真实麒麟 Nimbus 视觉仍需企业桌面人工复验；TCDS Tool 继续要求目标机存在 `/usr/local/bin/python3`。

## 2026-08-24 - 补齐 SkillHub 上传和进度查询接口

### Why

- 企业 SkillHub 接口文档除 `/list`、`/download/{id}` 外还明确提供 `/upload` 和 `/upload/progress`；平台此前未实现这两条链路，用户要求严格按文档补齐请求字段和 response。

### What

- 扩展 `SkillHubGateway` 领域端口和 HTTP 适配器：所有请求继续携带环境注入的 `X-Skill-Access-Key`；上传固定发送 `source/phase/file/safetyReportPic/directoryStructurePic/runningEffectPic` 六个 multipart 字段，响应读取文档 `result` taskId，进度读取 `result.progress/message`。
- 新增 `SUPER_ADMIN` 平台入口 `POST /agent-skill-hub/external/upload` 和 `GET /agent-skill-hub/external/upload/progress`；平台统一 `ApiResponse.data` 分别直接承载 taskId 字符串和 `progress/message`，不添加接口文档外业务字段。
- 上传在 API 与业务层限制 ZIP 20 MiB、三张图片各 5 MiB，校验 `phase=00..06`、文件名、ZIP 路径/数量/重复项/根 `SKILL.md`/UTF-8/技术 ID；Git push 不隐式上传，进度 100 后由手工 `/external/sync` 或下一轮目录对账纳入能力库。
- 下载适配器同步按文档校验 `application/octet-stream`、附件文件名和 `Content-Length`，继续固定补充渠道枚举 `PLATFORM(3)`。同步 integration/workspace/backend README、HTTP API、事件、后端、安全和部署文档。

### How

- JDK 25 定向运行 `SkillHubHttpGatewayTest`、`AgentSkillHubApplicationServiceTest`、`AgentSkillHubControllerTest`，最终 28 项通过、0 失败、0 错误；覆盖六字段 multipart、认证头、文档 response、`taskId` 查询、权限和 ZIP 校验。
- 相关 Reactor 全量执行 432 个测试类、2818 项，Surefire 报告为 0 失败、0 错误；`git diff --check` 通过，并已回顾全部 `.agents/session-log*.md` 近期条目，未覆盖并行客户端交付成果。
- 未用当前 `.env.test` 重启：其验收数据库仍为 `127.0.0.1:15432/test_agent`，不符合仓库强制的 `192.168.8.100:15432/testagent_dev`，且直接 source 在第 38 行遇到未转义 `&`；未修改环境文件，也未停止或替换已有本地服务。

### Result

- SkillHub 文档提供的四类能力现均有后端适配；超级管理员可以显式上传、按文档轮询到 100/-1，再同步目录查看新能力。
- 本次新增 HTTP API 并收紧外部下载响应校验，未新增 RunEvent/SSE/WebSocket 类型、数据库、SQL、Flyway、部署节点或强制配置；未修改 `.env*`、generated SDK、OpenCode 只读源码，也未把访问密钥写入源码或日志。企业真实 SkillHub 上传仍需部署后用内网文件联调。

## 2026-08-24 - 远端开发环境启动基础中间件与 Mem0/BGE

### Why

- 用户要求进入 `192.168.8.100` 的 `/home/abc/intelligent-test-agent-dev` 开发副本，启动项目所需中间件，并补充启动 Mem0 与 BGE。

### What

- 通过项目脚本启动 PostgreSQL、Redis、XXL-Job MySQL、ClickHouse，以及 Mem0 的 pgvector PostgreSQL、BGE embedding、Mem0 三个无状态副本和本地负载均衡器。
- 远端实际监听端口为基础中间件 `15432/16379/13306/18123`，Mem0/BGE 为 `15433/18989/18888`；Mem0 migration `20260809_01` 成功执行，初始 `rawMessageCount=0`。
- 仅调整远端运行态：ClickHouse 生成的 users 配置文件补充容器可读权限，XXL-Job 专用账号密码与远端 `.env.test` 配置对齐；未修改项目源码、`.env` 文件或 OpenCode 源码。

### How

- 标准 `memory-dev-services.sh restart` 首次构建在 Hugging Face snapshot 下载阶段失败；使用临时 Dockerfile 注入 `HF_HUB_DISABLE_XET=1` 并以 host 网络重建 BGE 镜像，模型文件校验和镜像导出均通过，随后删除临时 Dockerfile。
- `tools/memory-dev-services.sh status` 返回 memory PostgreSQL、BGE readiness 和 Mem0 VIP 全部 `OK`；ClickHouse status 返回版本 `26.3.17.56` 和数据库就绪。十个相关容器状态均为 running/healthy（一次性 migration 容器按预期 `Exited (0)`）。
- 七个端口均通过本机 TCP 连通性检查；Mem0/BGE 的直接 HTTP readiness 端点需要服务认证，使用项目 status 脚本的内部鉴权检查作为就绪证据。

### Result

- 远端基础中间件和 Mem0/BGE 已启动并稳定运行；本次未启动 backend、frontend 或 opencode-manager，因为用户范围是中间件。
- 远端 runtime 生成文件和容器状态已落地，但未产生本地源码改动、API/事件/数据库结构变更或企业部署包变更。

## 2026-08-24 - 明确客户端与长期记忆的账号灰度边界

### Why

- 用户手册虽然分别提到客户端和记忆的开放条件，但功能总览、周更和常见问题没有统一说明两项能力均为按账号灰度，容易把客户端开放误解为同时开通记忆，或误解关闭客户端灰度会停止本地运行。

### What

- 更新每周新功能、功能总览、设置、长期记忆和常见问题：超级管理员在“系统管理 → 用户管理”分别控制“客户端灰度”和“记忆灰度”；记忆还依赖平台全局记忆配置可用。
- 补全本地客户端从个人设置创建 Client Key、头像下载、托盘注册目录到顶部选择本地工作区的操作步骤和入口截图；注明灰度只控制网页入口与投影，不自动安装或启动，关闭后也不会停止客户端、重启本地 OpenCode 或撤销 Key。
- 新增帮助中心回归断言，锁定两项开关独立、记忆全局前置条件、客户端关闭边界、周更灰度文案和无游戏内容/章节截图约束。

### How

- 复用现有的 `settings-personal.png` 脱敏操作截图和帮助中心文档扫描机制，没有新增界面、API 或配置。
- `corepack pnpm exec vitest run apps/agent-web/tests/help-center.test.ts`（16 passed）、`corepack pnpm --filter @test-agent/user-manual build`、`corepack pnpm --filter @test-agent/agent-web typecheck` 全部通过；VitePress 实际页面复核了设置章节的 Markdown 层级。

### Result

- 客户端与长期记忆的可见性、配置入口和关闭后的影响已按当前实现同步到用户手册；不涉及 HTTP API、RunEvent/SSE、数据库、Flyway、部署、性能、安全策略、环境配置、generated SDK 或 OpenCode 源码。

## 2026-08-24 - 基于已提交 release 重打企业增量包并排除 Trace 在途改动

### Why

- 用户要求基于最新代码重新打企业包，并在发现并行 Trace 开发尚未提交后明确要求本轮不包含 Trace。
- 上一轮临时 ZIP 继承了 worker `included` 标记，且主工作区在构建期间出现并行未提交文件，不能继续作为最终交付物。

### What

- 最终构建源固定为已提交的 release HEAD `4577a2c2ebd0cb9f6dde2a3fb9dc146aaaf3e800`；通过 detached 临时 worktree 构建，完整保留主工作区 Trace 在途改动但不将其编入后端、前端或部署脚本。
- 增量包包含 backend、frontend 和签名本地客户端 `20260824155652`；客户端分发目录只保留这一版。worker runtime 与 toolbox 均为 `reuse`，LobeHub、memory 及独立 ClickHouse/Mem0/BGE/pgvector/Python 包未重复装入。
- 三节点配置继续使用 TCDS `http://tcds-prod.sdc.icbc:9080`、SkillHub `http://ai-code.sdc.icbc/icbc/skill` 和前端附加端口 `9996`；SkillHub access key 仅保留“从目标机已安装 backend.env 继承”的占位标记，没有把密钥写入包。

### How

- 后端正式构建、前端 VitePress/TypeScript/production build、内层 `--validate-only`、全部受保护 PostgreSQL/ClickHouse/XXL Flyway 资源 checksum、客户端签名分发、三节点配置、内外层 ZIP 完整性与嵌套 SHA 均通过。
- 额外从临时 HEAD 源码、后端 `TraceController.class` 和前端产物三层确认在途 `spans/records` Trace 接口及客户端调用不存在；worker/toolbox/memory/LobeHub 归档不存在，旧客户端 `20260824124347` 不存在。
- 最终内层 SHA-256 为 `15b77c3e860fbe3ea7f36e02b94841dc11719a46fba336ec4caccc525b7e3b8c`，外层固定名包 SHA-256 为 `6d33ba0df6fc7af0d9aea3cc72519ca3a323e457f8cf0dd84023f33e63897ec4`；已复制到 `~/Desktop/mimoagent/0709` 并复验 SHA。

### Result

- 本轮企业增量包严格对应 release 提交 `4577a2c2e`，不含主工作区未提交的 Trace 优化；相对上一企业包没有新增 Flyway 文件。目标库若已执行到本包既有最新版本，部署后 history 不应新增记录；若上一轮在迁移完成前失败，则仍会补执行其尚未落库的既有版本，必须以前后完整 history 对比为准。
- 目标 `.4/.114/.2` 尚未实际部署；部署前必须确认 `.4/.114` 已安装 worker 指纹 `877cea1827a6f55b994f4a82f0934d72ca1361fb12b9872ce77e45430073be33`，并各自已有长度至少 16 的 `TEST_AGENT_SKILLHUB_ACCESS_KEY`，禁止伪造指纹或用 Flyway `repair/outOfOrder` 绕过门禁。

## 2026-08-24 - 修复大 Trace 详情首屏加载缓慢

### Why

- Trace 详情原先串行分页读取整条 gzip NDJSON 正文后才渲染；实库中的测试设计 Trace 有 27,234 条原始事件，其中绝大多数是 OpenCode 1.18.4 流式传输事件，导致点开时等待和浏览器内存开销随原始事件数增长。

### What

- 新增 `GET /api/internal/platform/traces/{traceId}/spans`，只从 ClickHouse 返回 DSH 语义 Span；过滤流式 delta、payload fragment 和已闭合 Tool before，保留未闭合 Tool 的 before。
- 新增 `GET /api/internal/platform/traces/{traceId}/records/{eventId}`，选中事件后才从冻结归档节点读取正文；Assistant 按 `messageId` 汇聚，完成态 Tool/Skill 只读取同 `callId` 的 before/after，未闭合 before 继续读取 1.18.4 terminal event 作为错误/取消兜底。
- Trace 前端改为语义 Span 首屏、正文异步懒加载，并对快速切换事件增加 generation/error 竞态保护；无语义 Span 的历史或未闭合生命周期 Trace 继续回退原 `/events`，不牺牲不完整 Trace 可查看能力。
- 同步 API、事件流、前后端模块 README、ClickHouse 与企业部署说明；不新增 migration、镜像、端口、依赖或部署节点，未修改 OpenCode 1.18.4 只读源码。

### How

- JDK 25 下 `TraceArchiveServiceTest` 8 项、`TraceControllerTest` 6 项通过；真实 ClickHouse Testcontainers 集成用例 1 项通过。前端 `TraceView.test.ts` 3 项、typecheck 和 production build 通过。
- 使用 `./restart-dev-services.sh --profile test --env-file .env.test --with-clickhouse` 完整重建并启动 backend、frontend、ClickHouse 与 OpenCode manager 1.18.4；readiness 均成功。
- 真实超级管理员页面点击 27,234 raw events 的 Trace，约 409 ms 显示 62 条语义记录；`/spans` 响应约 87 ms / 50 KiB。Tool 正文按需请求约 449 ms / 3.1 KiB；不完整 3-event Trace 约 408 ms 可查看。浏览器无新增 error。

### Result

- 正常 Trace 的首屏耗时不再与全部流式事件和归档正文大小线性绑定，事件 Payload/Result 仍按类型显示且正文继续只存在服务器归档。
- 当前仓库 `.env.test` 实际指向本机 `127.0.0.1:15432/test_agent`，本次按用户要求使用该文件完成真实启动但未修改它；这不等同于仓库清单要求的 `.100/testagent_dev` 企业验收，后者仍需环境所有者恢复规定配置后单独执行。
- 以已提交的 release `a3db388e0` 重建企业增量包：内层 `test-agent-internal-release.zip` SHA-256 为 `933e936e2188ec4eac405fd03fcdd21808aa8a6dce1ff8300c64e2415153a9de`，固定名双后台外层包为 `c3b43b79d17f3f98a47d367465da138bb45583ab0ac06c3cada9298d0bdbea4e`；外层内嵌内层逐字节一致，两层 ZIP CRC、受保护 PostgreSQL/ClickHouse/XXL migration、客户端签名分发和三节点配置门禁均通过。
- 发布产物中的 `test-agent-api` JAR 已用 `javap` 确认包含 `/spans`、`/records/{eventId}` 与 `globalSequence`，前端 production JS 同时包含对应请求；外层包及 checksum 已复制到 `~/Desktop/mimoagent/0709/` 并复验一致。本条是制品生成后的追溯记录，不再据此递归重封 ZIP。

## 2026-08-24 - 修复 SkillHub 预览 503 并展示创建人

### Why

- 企业测试页面点击 SkillHub Skill“预览内容”时，平台 `POST .../materialize` 返回 503；该问题出现在下载响应头按文档收紧之后，企业反向代理的 ZIP MIME 或 chunked 传输差异会在正文安全校验前被误拒绝。
- SkillHub `/list` 文档及现场真实 JSON 已提供 `contributor`，后端也已保存为 `externalContributor`，但能力库卡片和详情没有展示。

### What

- `/download/{id}?channel=3` 和 `X-Skill-Access-Key` 请求协议保持不变；下载适配器继续接受文档标准 `application/octet-stream`，同时兼容 `application/zip`、chunked 传输以及代理省略的 `Content-Disposition/Content-Length`。正文仍限制 20 MiB，后续 ZIP、路径、文件数、根 `SKILL.md` 和稳定名称校验不放宽。
- 上游 401/404/5xx 继续统一为 `SKILLHUB_UNAVAILABLE`，但返回安全中文原因和 `details.upstreamStatus`；不回显上游 URL、请求头、密钥或正文。
- SkillHub Skill 卡片和详情展示“创建人”，取 `externalContributor` 并清理首尾换行，空值显示“未提供”。同步 integration、agent-web README、HTTP API 与后端规范。

### How

- JDK 25 定向 `SkillHubHttpGatewayTest` 6/6 通过，新增 ZIP MIME/chunked 和脱敏 401 状态测试；后端 common/domain/integration reactor 共 277 项测试通过，0 失败、0 错误。
- Agent Skill Hub 前端组件 14/14 通过，`agent-web` 类型检查和 production build 通过；24 模块后端 `-DskipTests package` 成功，最终应用 JAR 内 integration 类已用 `javap` 确认包含新的响应兼容与状态分类方法。
- 本机到 `http://ai-code.sdc.icbc` 的探测返回 `status=000/Empty reply`，无法执行企业真实下载；没有把本机网络失败冒充企业验收。提交前已回顾全部 `.agents/session-log*.md` 近期记录，未发现与本次八个代码/文档文件冲突的并行成果。

### Result

- 代码侧已消除因企业代理下载传输元数据差异导致的 503，并让真实上游非 200 在下一次现场复验时可直接区分；创建人可在外部 Skill 卡片和详情看到。
- 企业真实 `materialize` 尚需重打包并在 `.4/.114` 两个 Java 节点部署后复验；若仍失败，应按响应 `upstreamStatus` 和两节点 `backend.env`/日志判断凭据、DNS或上游状态。
- 本次是向后兼容的错误详情增强和前端展示，不新增 HTTP 路径、RunEvent/SSE、数据库、SQL、Flyway、部署节点或强制配置；未修改 `.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-24 - SkillHub 创建人联合平台用户姓名

### Why

- SkillHub `/list` 的 `contributor` 是统一认证号，能力库直接展示该 ID 不便于识别创建人；用户要求联合平台用户表显示姓名。

### What

- Hub 列表 MyBatis 查询按清理后的 `external_contributor = users.unified_auth_id` 左连接 `users.username`，API 新增可空字段 `externalContributorName`；不把易变姓名复制到 Hub 资产表，也不新增 SQL 或 Flyway migration。
- 前端卡片和详情优先显示平台用户名，悬停展示原统一认证号；关联不到用户时回退 contributor ID，两者都没有时显示“未提供”。同步共享类型、HTTP API、persistence 与 agent-web README。
- SkillHub 同步入口统一清理 contributor 首尾空白，兼容现场 ID 带换行的数据。

### How

- JDK 25 下 `SkillHubHttpGatewayTest` 6 项、`AgentSkillHubApplicationServiceTest` 20 项、`MyBatisAgentSkillHubRepositoryIntegrationTest` 8 项全部通过；集成测试覆盖统一认证号关联用户名。
- 前端全量 Vitest 150 个文件通过，2213 passed / 1 skipped；`agent-web` 类型检查和 production build 通过。JDK 25 后端 26 模块 `-DskipTests package` 成功；`git diff --check` 通过，提交前已回顾全部 `.agents/session-log*.md` 近期记录。
- 未重启现有本地服务以避免打断并行使用；只读检查确认 backend health/readiness 为 UP，frontend 3000 返回 200。新改动由自动化测试和类型检查验证，运行中的旧进程尚未加载本提交。

### Result

- SkillHub 创建人现在按统一认证号动态解析为平台用户名，并保持缺失用户/旧数据的兼容展示。
- 本次仅向现有资产响应增加可选字段并调整单次列表 SQL；不新增 HTTP 路径、RunEvent/SSE、数据库结构、migration、部署节点或强制配置，不修改 `.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-24 - 客户端统一使用离线现代桌面主题

### Why

- 企业部署的 `20260824155652` 已解决旧 Metal 观感，但底层仍按平台选择 macOS Aqua 或 JDK Nimbus；用户明确要求客户端不要再使用 Java/系统原生主题。
- 企业现网 `20260824155652` 相对上一版 `20260824124347` 的范围容易与之后生成但未部署的本机 release 混淆，需要固定真实企业版本差异和下一版客户端边界。

### What

- 复用既有 `LocalClientDesktopTheme` 统一入口，删除 Aqua/Nimbus/Metal 选择逻辑；macOS 与麒麟/Linux 统一使用 shaded JAR 内置的 FlatLaf 3.7.2，并增加白色卡片、轻边框、12px 圆角、紧凑滚动条和 TestAgent 红色默认操作按钮 token。
- FlatLaf 是纯 Java、Apache-2.0 依赖，由外网 Maven 构建后进入 fat JAR；企业目标机不联网安装 UI 包。同步客户端模块 README 和本地客户端部署验收说明。
- 生成独立签名客户端 release `20260824180254`，继续使用企业公共 Git `81605f245d1512e1ab0dd73812391f6da7d008b5` 的既有完整能力包，不改变 8 Agent / 16 Skill / 8 Tool。

### How

- JDK 25 按 Java 21 release 运行 `mvn -pl test-agent-local-client -am test`：客户端 96 项通过、1 项按桌面条件跳过，上游 common/protocol 共 121 项通过；`mvn ... -DskipTests package` 成功。
- 实际 shaded JAR 包含 `LocalClientDesktopTheme.class` 与 `com/formdev/flatlaf/FlatLightLaf.class`，`java -jar ... --version` 输出 `20260824180254`；manifest、客户端 JAR和公共能力包三项签名均通过稳定公钥验证。
- U 盘转运包为 `~/Desktop/mimoagent/0709/test-agent-local-opencode-client_20260824180254_arm64.tar.gz`，SHA-256 为 `53cf358538920b7f971cd802f82f7448d840142435b2ef3383aae34452720f77`；JAR 为 `30f2c627218aa5138ef66f9e2afa85016c88015c502362e6cdefbb4ce5494f82`，DEB 为 `2f03d1be04818381ca491cd8555e5a5dbd4ab8b33230dc1e57d17ad41564f8cf`。

### Result

- 新客户端正常路径不再依赖操作系统或 JDK 原生 LookAndFeel，且新增依赖已完整离线打入签名 release；真实麒麟桌面视觉仍需发布该独立客户端包后人工复验。
- 本次不变更 HTTP API、RunEvent/SSE、数据库、Flyway、后端/worker、OpenCode 只读源码、generated SDK 或 `.env*`。构建期间并行 SkillHub 改动已由其所有者提交，本次提交只纳入主题、依赖、测试、文档与本条会话记录。

## 2026-08-24 - 客户端取消红色主色并改用用户级安装脚本

### Why

- 用户明确不希望新 FlatLaf 客户端继续使用红色主操作；企业新用户从网页下载 DEB 后，麒麟图形软件安装器因没有匹配的企业系统包信任链而拒绝安装，但手工提取启动器可正常运行。
- 现有发布已经提供会校验签名清单和制品的 `install.sh`，不应继续要求普通用户通过系统软件安装器安装只有稳定启动器的 DEB。

### What

- 客户端焦点、默认按钮及交互态由红色改为深蓝灰；继续复用随 shaded JAR 离线交付的 FlatLaf 3.7.2，不恢复 Java 或系统原生外观。
- 网页直接下载 `install.sh` 并固定文件名为 `test-agent-local-client-install.sh`；Nginx 与 Vite 的兼容 `/installer` 入口也改为重定向脚本。版本化 DEB 继续保留为受控离线提取载体，不再表述为可由未配置信任策略的图形软件安装器直接安装。
- 安装脚本在成功初始化/接入后原子自安装到 `~/.local/bin/test-agent-local-client`，user systemd 和桌面入口只引用该用户级启动器，不需要 `/usr/bin` 或 sudo。同步客户端、前端、部署、安全和用户手册说明。
- 生成新的不可变签名客户端 release `20260824183732`，继续使用企业公共 Git `81605f245d1512e1ab0dd73812391f6da7d008b5` 的 8 Agent / 16 Skill / 8 Tool 完整能力包。

### How

- `local-opencode-client-package-test.sh` 通过，覆盖下载脚本直接自安装、非符号链接用户启动器、systemd/桌面入口、Nginx 双入口重定向和 DEB 离线载体；`FigmaShell.test.ts` 定向测试和 agent-web production build 通过。
- Maven 首次被默认 Java 17 与已有 Java 21 class 拦截；固定本机 JDK 25 后按 Java 21 release 重跑，common 110、protocol 11、client 96 项通过，client 1 项按桌面条件跳过。
- 本机独立 Vite `http://127.0.0.1:3101` 实测 `install.sh` 返回 200、下载文件名正确，兼容 `/installer` 返回 302 并指向脚本；生成分发通过离线 verifier，manifest、客户端 JAR和公共能力包签名均验证成功。
- U 盘转运包为 `~/Desktop/mimoagent/0709/test-agent-local-opencode-client_20260824183732_arm64.tar.gz`，SHA-256 为 `dfbe70371dcf2c752db93be9dd49e3605384540aed7cfe6c65fcc847ebc44b37`；JAR 为 `ccdb0de50ab6568adfa671b4beabdc37eb2c88068b4f6e9e8d4cb8658511b26b`，DEB 为 `a34f75ecf487a9f3c6aee0eced33824ac67f7630c41f2ca6a253bc62afee77d0`，安装脚本为 `6fa0edfa20d408e906fb853ff49d7f3610fd65515962b1bc172eb8ed85c4deeb`。

### Result

- 普通用户下载后执行 `sh ~/下载/test-agent-local-client-install.sh` 即可完成用户级初始化、接入和常驻服务安装，不再触发麒麟软件中心的未签名 DEB 拒绝；这不是伪造或绕过系统签名，若未来必须由图形软件安装器安装，仍需企业提供并预置信任的 APT/debsig 密钥与策略。
- 本次不新增 HTTP API、RunEvent/SSE、数据库、Flyway、部署节点或强制环境变量，不修改 `.env*`、generated SDK 或 OpenCode 只读源码。真实麒麟桌面视觉与新用户首次输入 Client key 仍需在发布该客户端和前端/Nginx入口后人工复验。

## 2026-08-24 - 恢复公共 Skill 目录并调整 skillmarket 文案

### Why

- 双来源能力库改造曾在公共快照查询中硬编码只保留 `AGENT`，导致已经入库的公共 Git Skill 只能作为 Agent 依赖解析，无法在独立 Skill 页面展示。
- 外部来源筛选和卡片仍显示“接口文档”，用户要求面向界面的来源名称改为 `skillmarket`。

### What

- `AgentSkillHubApplicationService.publicBuiltinSnapshots` 移除 `AGENT` 硬过滤，继续复用已有 `type/category/subcategory/keyword` 条件，使公共 Agent 与公共 Skill 都按调用方类型从同一数据库快照返回。
- 公共 Skill 仍归入兼容的 `PLATFORM` 来源并保持只读、全局生效；外部来源只把前端展示名改为 `skillmarket`，内部 `SKILLHUB` 枚举、HTTP 参数和下载/同步协议不变。
- 补充公共快照 Skill 列表、详情和正文读取回归，并同步 workspace-management、agent-web、backend-api 与 HTTP API 稳定文档。

### How

- JDK 25 定向运行 `AgentSkillHubApplicationServiceTest`，20/20 通过；workspace-management Maven reactor 打包成功。
- 前端能力库组件测试 14/14 通过，`agent-web` 类型检查通过；独立 Vite 在 `http://127.0.0.1:3001/hub` 返回 HTTP 200 后停止。随后按 `.env.test` / `test` profile 完成 26 模块后端和前端生产构建并重启标准服务，backend health/readiness 为 `UP`、frontend 3000 返回 200、登录 CORS 正常，manager 最终恢复 `HEALTHY`。
- `git diff --check` 通过；提交前回顾全部 `.agents/session-log*.md` 近期记录，并隔离工作区中并行的本地客户端安装与主题改动。

### Result

- 能力库 Skill 页面重新展示公共 Git Skill；skillmarket 筛选仍发送 `source=SKILLHUB`，不会造成 API 或旧客户端不兼容。
- 本机公共 Git 快照对账仍记录既有分叉：数据库提交 `6fe30ab...` 与本地提交 `81605f2...` 不构成快进，因此本次展示的是数据库已索引公共 Skill；后续公共仓库新增/删除仍需先处理该 Git 历史分叉才能进入最新快照。
- 本次不新增 HTTP 路径、RunEvent/SSE、数据库、SQL、Flyway、部署节点或强制配置，不修改 `.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-24 - 重打最新 release 企业增量包并补齐客户端首次安装配置

### Why

- 用户要求基于当前最新 `release` 重新打企业增量包，并明确要在麒麟普通用户机器上清空既有用户级客户端后，从企业域名重新下载完成首次安装验收。
- 初次封包发现客户端目录仍保留两个历史 release，导致内层 ZIP 约 966 MiB；最终节点包复核又发现旧节点配置没有固定携带客户端 catalog 下载地址、路径和可信 Nginx 地址，部署后可能影响版本同步和首次接入验收。

### What

- 最终增量包包含当前已提交的 Trace 首屏语义 Span/正文懒加载、思维导图特殊字符、SkillHub 预览与创建人展示、公共 Skill 恢复，以及客户端 FlatLaf 蓝灰主题和无 sudo 用户级安装脚本。
- 客户端生成不可变版本 `20260824185203`，企业公共能力继续固定提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`，为 8 Agent / 16 Skill / 8 Tool；分发目录和 catalog 只保留本轮版本，不携带 `20260824155652/20260824170822`。
- 复用 `package-two-backend-complete.sh` 既有配置归一函数，为 `.4/.114` 节点包补齐 catalog 下载地址、`catalog.json` 路径和可信 `.2` Nginx 地址；`deploy-multi-backend-node.sh` 增加对应失败关闭校验，并同步多后台部署文档。

### How

- 后端正式 Maven 构建、Spring Bean 构造器门禁、前端 VitePress/TypeScript/production build、所有受保护 PostgreSQL/ClickHouse/XXL Flyway 最终 JAR 字节校验和内层 `--validate-only` 均通过；本轮相对上一企业包没有新增 migration。
- `local-opencode-client-package-test.sh` 通过，覆盖空安装根下载、签名校验、运行时安装、接入、用户启动器、user systemd 和桌面入口；最终节点包同时验证 `.4/.114` 的 TCDS、SkillHub、catalog 和 trusted proxy 配置。
- worker runtime 与 toolbox 均为 `reuse`，LobeHub/memory 为 `disabled`，没有重复携带 worker/programs、工具箱、独立 ClickHouse/Mem0/BGE/pgvector/Python 制品；内外层 ZIP CRC、嵌套 SHA 和 staging 目录复制后 SHA 均通过。

### Result

- 内层 `test-agent-internal-release.zip` SHA-256 为 `8ffdc3d5ebfc951d7d40d54fa711ee73c8aede41f239c8b0feb79a210bb877b1`；固定外层 `test-agent-two-backend-complete.zip` SHA-256 为 `e438921189deff29e5ed136dff418827f1a4b9482392453f5d28cac9f4bcecaa`，已复制到 `~/Desktop/mimoagent/0709` 并复验。
- 企业 `.4/.114/.2` 尚未执行实际部署，客户端真实麒麟桌面、企业域名下载、首次输入 Client Key、在线状态及本地工作区会话仍需现场验收；工作区并行出现的配置管理和前端设置未提交改动没有进入本包，也未被本次提交暂存。

## 2026-08-24 - 修复应用版本目录刷新与工作空间别名重置

### Why

- 应用版本关联的目录树复用了最长一小时的 Git 浅缓存，远端分支已经更新时仍可能只显示旧目录；保存应用版本工作空间后，前端还会把用户填写的别名强制重置为 `ai-test`。

### What

- 配置管理为设置页目录树增加显式远端刷新入口：每次查询都在原有受控临时 Git 仓库内重新浅拉取所选分支并读取最新树；其它只读调用继续复用原有缓存策略。
- 应用版本工作空间保存成功后保留当前输入的自定义别名，不再写回默认值；补充后端真实本地 Git 回归和前端组件回归，并同步配置管理、agent-web、backend-api 与 HTTP API 文档。

### How

- JDK 21 定向后端 34 项通过，配置管理模块及依赖全量 323 项通过；前端设置组件 20 项通过，agent-web 类型检查通过。
- 按 `.env.test` / `test` profile 使用 JDK 25 完成 26 模块后端和前端生产构建并重启标准服务；backend health/readiness 为 `UP`、frontend 3000 返回 200、登录 CORS 正常，manager 最终恢复 `HEALTHY`。
- `git diff --check` 通过；提交前回顾全部 `.agents/session-log*.md` 近期记录，并隔离工作区中的版本弹窗、页脚和生成声明等并行改动。

### Result

- 设置页再次加载目录树时会读取远端所选分支的最新提交，无需等待一小时或手工删除缓存；保存后工作空间名称保持用户填写值。
- HTTP 路径和响应结构不变，不涉及 RunEvent/SSE、数据库、SQL、Flyway、部署节点、强制配置、generated SDK、OpenCode 只读源码或 `.env*`。

## 2026-08-24 - 补齐顶部新增工作空间版本入口

### Why

- 工作台左下角已经可以新增应用工作空间版本，但顶部版本菜单只能选择既有版本，用户在主要操作路径中找不到同等能力。

### What

- 提取 `CreateWorkspaceVersionDialog.vue`，让顶部版本菜单和左下角两级菜单复用同一日期/分支表单；非标准仓库的分支加载增加请求代次隔离，避免关闭或切换工作空间后的迟到响应污染下一次打开。
- 顶部版本菜单新增“新增版本”入口，提交继续进入 `AgentWorkbench.handleCreateVersion`，复用既有进程检查、版本创建、缓存失效、默认个人 worktree 准备和版本切换链路。
- 补充共享弹窗、顶部入口组件测试和 Chromium 工作台回归，并同步前端总览、包说明、前端规范、模块图、HTTP API 说明和内置用户手册。

### How

- agent-web 定向 Vitest 3 个文件 89 项通过；此前误触发的 frontend 全量 Vitest 151 个文件通过，2216 passed / 1 skipped；agent-web 类型检查通过。
- Chromium 定向 Playwright 1 项通过；前端生产构建（用户手册与 agent-web）通过，只有既有大 chunk/动态导入提示；`git diff --check` 通过。
- 使用 `corepack pnpm exec vite --mode localhost --host 127.0.0.1 --port 4188` 启动真实前端，`http://127.0.0.1:4188/` 返回 HTTP 200。

### Result

- 选择测试工作空间后，顶部版本菜单即使暂无版本也会显示“新增版本”，与左下角入口使用完全相同的创建行为。
- 本次不新增或变更 HTTP API、RunEvent/SSE、数据库、SQL、Flyway、依赖、部署节点、安全协议或兼容性契约；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，未新建分支。

## 2026-08-24 - 修复顶部新增版本弹窗被上下文舱裁切

### Why

- 顶部“新增版本”已能触发共享弹窗，但顶栏通过 `transform` 做视觉居中，Element Plus 弹窗默认留在该定位上下文时，fixed 遮罩和弹窗被限制在顶部上下文舱内，页面只显示一条灰色遮罩而看不到弹窗主体。

### What

- 复用项目其它顶栏弹窗既有的 `append-to-body` 方案，让 `CreateWorkspaceVersionDialog` 统一 Teleport 到 `body`；顶部与左下角仍共用同一组件和 `AgentWorkbench.handleCreateVersion` 业务链路。
- Chromium 回归新增弹窗所属遮罩必须直属 `body`、起点为视口左上角且宽高等于完整 viewport 的断言；同步 agent-web README 与 PACKAGE 说明这一层级约束。

### How

- 新断言在修复前稳定失败，表现为 `body` 下没有可见的目标遮罩；最小增加 `append-to-body` 后转绿。
- agent-web 定向 Vitest 3 个文件 89 项通过，类型检查通过；顶部与左下角新增版本两项 Chromium 回归通过；前端生产构建和 `git diff --check` 通过。
- 标准本地前端继续运行在 `http://127.0.0.1:3000/` 并返回 HTTP 200；构建仅有既有大 chunk 与动态导入提示。

### Result

- 顶部点击“新增版本”后，遮罩覆盖完整视口且弹窗正常居中显示，不再被 34px 顶部上下文舱裁切；左下角入口行为保持不变。
- 本次仅修复前端挂载层级，不新增或变更 HTTP API、RunEvent/SSE、数据库、SQL、Flyway、依赖、部署、性能模型、安全协议或兼容性契约。

## 2026-08-24 - 改为麒麟普通用户双击客户端包

### Why

- 企业麒麟普通用户没有 sudo，标准 DEB 必须写 `/usr` 和 `/var/lib/dpkg`，即使补签名也无法由无管理员权限账号自行安装；现场已验证 `dpkg -i` 会被权限门禁拒绝。
- 网页下载需要提供真正可双击启动的用户包，而不是再次下载 shell 或要求用户绕过系统包管理。

### What

- 删除本地客户端 DEB 构建与发布入口，新增静态 Linux ARM64 Go 启动器和 `tar.gz` 用户包；用户完整解压后双击 `TestAgent-Local-Client`，启动器校验随包安装脚本与图标摘要，并在麒麟 UKUI 或常见 Linux 桌面终端中复用现有 `setup`。
- 现有安装脚本继续负责运行时签名校验、Client Key 接入、`~/.local` / `~/.config` 安装和 user systemd 常驻；桌面入口补齐小兔子图标，不写系统目录、不调用 sudo/dpkg。
- 网页、Vite、Nginx、企业发布 ZIP、增量指纹、部署校验和用户文档统一改为 `TestAgent-Local-Client-Kylin-arm64.tar.gz`，发布门禁固定六个归档条目并拒绝符号链接、越界或安装脚本不一致；旧 DEB 不再进入新分发目录。

### How

- Go 启动器单元测试和 `go vet` 通过；本地客户端正式打包测试通过，覆盖 ARM64 ELF、归档执行权限、用户级安装、签名运行时、桌面入口和无 DEB 残留。
- 企业增量组件、双后台节点和真实 Nginx 容器配置三组校验通过；前端定向 Vitest 74/74、VitePress + agent-web TypeScript/production build 通过，仅保留既有大 chunk 提示。
- 版本 `20260824185203` 用户包在 ARM64 Debian 容器实际执行 `--self-check` 成功；本地 `http://127.0.0.1:3000/downloads/local-opencode-client/installer` 下载文件与 `~/Desktop/mimoagent/0709/TestAgent-Local-Client-Kylin-arm64.tar.gz` SHA-256 均为 `a451debfe405cb0cf969ae5986d817682403764ccf9371d07f3ebaaffc336f81`。

### Result

- 新用户不再接触 DEB：下载、完整解压、双击原生启动器即可在当前账号下安装并接入；命令行兜底仅为解压目录中的 `./TestAgent-Local-Client`，同样不需要 sudo。
- 本次不新增 HTTP API、RunEvent/SSE、数据库、Flyway、部署节点或强制环境变量，不修改 `.env*`、generated SDK 或 OpenCode 只读源码。真实麒麟文件管理器双击、首次 Client Key 输入和 user systemd 在线状态仍需在企业桌面完成最终人工验收。

## 2026-08-24 - 修复工作台首次进入白屏

### Why

- 重新部署后浏览器冷缓存进入 `/workbench` 时，Vue Router 会等待约 4.1 MB 的异步工作台脚本，根组件此前只渲染 `RouterView`，首导航完成前保持纯白。
- 页面入口还同步引用 Google Fonts，企业网络首次请求可能阻塞字体获取并放大冷启动等待。

### What

- 根组件在 `router.isReady()` 前展示复用现有 Logo 的品牌加载态，首导航成功或失败后均退出兜底并交回正常路由；动效支持 `prefers-reduced-motion`。
- 删除运行时 Google Fonts 和预连接，统一改用 macOS、Windows、中文系统字体与本机等宽字体栈；同步 frontend、agent-web、PACKAGE 和前端规范说明。
- 新增 App 入口回归，覆盖首导航等待、成功切换以及失败时不永久停留加载态。

### How

- App 定向 Vitest 2/2、agent-web 类型检查、VitePress + agent-web production build 和 `git diff --check` 均通过。
- 使用 production preview 与 Playwright 实测：人为延迟工作台脚本 1.5 秒时，品牌加载态约 97 ms 出现、工作台约 2.0 秒接管且兜底移除；无人工延迟的浏览器冷缓存约 290 ms 进入工作台，两次 Google Fonts 请求均为 0。

### Result

- 首次路由等待期间不再白屏，也不再依赖公共字体服务；工作台大包仍约 4.1 MB（gzip 约 1.28 MB），拆包属于后续性能批次，本次未扩大范围。
- 本次不新增或变更 HTTP API、RunEvent/SSE、数据库、SQL、Flyway、部署节点、安全协议或兼容性契约；未修改 `.env*`、generated SDK 或 OpenCode 只读源码，未新建分支。

## 2026-08-24 - 基于 release 最新代码重打企业增量包

### Why

- 上一轮企业包之后，`release` 已新增工作空间目录缓存刷新、顶部新增版本入口与弹窗层级、麒麟普通用户客户端包、客户端下载恢复和工作台首次进入白屏等修复，需要基于当前本地 HEAD `8b3cd26cae08c4ec05dfc06e23f6202576e1d1e7` 重新交付。
- 现场已经部署 worker、manager、模型目录、工具盒子和独立中间件，本轮必须继续使用组件指纹做增量封装，不能把未变化的大组件和历史客户端版本重复带入。

### What

- 复用 `package-release.sh`、`package-local-opencode-client.sh` 和 `package-two-backend-complete.sh` 现有正式入口，重建后端、前端和组织密钥签名的麒麟 ARM64 用户包；客户端固定版本为 `20260824215812`，首次能力基线仍锁定公共 Git commit `81605f245d1512e1ab0dd73812391f6da7d008b5`（8 Agent / 16 Skill / 8 Tool）。
- 组件清单确认 worker runtime `reuse`、toolbox `reuse`、LobeHub `disabled`、memory `disabled`、local client `included`；新内层 ZIP 不含 programs、worker 镜像、toolbox、memory、LobeHub 或 DEB，客户端 releases 只保留 `20260824215812`。
- 复用上一轮已部署成功的 `.4/.114/.2` 节点包重建外层固定名交付物，并重新注入当前组织签名公钥；两台后台的 TCDS、SkillHub、客户端 catalog 直连 `.2:80` 配置和前端多后台 `9996` 监听门禁均通过。

### How

- 后端正式 Maven 打包、Spring Bean 构造器门禁和前端 VitePress / TypeScript / production build 通过；配置管理定向测试 `ConfigurationManagementApplicationServiceTest,GitCloneCacheServiceTest` 共 17 项通过，前端 6 个目标文件 119 项通过。
- 客户端打包回归通过，覆盖空安装根目录下载、签名校验、JDK/运行时安装、接入、用户级启动器/systemd/桌面入口；正式分发 manifest SHA-256 为 `eb7a4f608470a2f3d239101526b9dfdce259074c159f4ad5cad08303ae7b5cd4`，用户包 SHA-256 为 `516b37866e6414b42e82078908678c567bb9df81a8f30cb6ff980407ab145180`。
- 相对上一企业包没有新增或改写 PostgreSQL、ClickHouse、XXL Flyway migration；最终 persistence JAR、内层 ZIP、解包验证和外层封装均逐项通过受保护 migration 字节门禁，工具盒子正式 migration 仍为 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`。
- 内外 ZIP CRC 通过，外层内嵌 ZIP 与当前内层 SHA-256 同为 `236f80b9920e622f48b9c9187cb6c8406ab689b391fac3da5f5a4f9a5cd41403`；第一次直接从 agent-web 包目录调用 Vitest 因绕过根配置统一报 `document is not defined`，改用根 `vitest.config.ts` 后全部通过，该次错误命令不作为代码失败。

### Result

- 最终外层包 `/Users/kaka/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip`，SHA-256 为 `f7f12fcb0a2898db02096590d36429c35302d5e5f1c012cd6c978b498d8b7bc8`；配套 `.sha256` 已覆盖并自校验为 OK。内层包为 `deploy/internal/dist/test-agent-internal-release.zip`，SHA-256 为 `236f80b9920e622f48b9c9187cb6c8406ab689b391fac3da5f5a4f9a5cd41403`。
- 后端 JAR SHA-256 为 `60470d47e3f8aea2feaf68830831ce3bfb98aac7d3e03bf1147f046093b05f87`，persistence JAR 为 `5dc089057c28a600ee01a2e065a3853653feb8519e21052650848867bc599d5d`，前端 tar 为 `b1221d4936b2bf149fc983be15f75d97a7f948dfc8b3b5b0aa2964133af98704`。
- 固定组织私钥/公钥和公共能力基线只保存在 Git 忽略的 `.secure/`，未进入 Git 或发布 ZIP；构建产物同样未提交。真实企业 `.4 → .114 → .2` 部署和麒麟文件管理器双击验收仍需现场执行，本轮未推送远程。

## 2026-08-25 - 收紧企业部署后排查技能边界

### Why

- `enterprise-troubleshooting` 原触发描述覆盖所有登录、工作区、Agent 和数据库问题，容易把开发者本机启动失败误导到企业数据库与服务器归属取证流程。

### What

- 将技能明确限定为 TestAgent 已部署到企业内部环境后的现场运行故障；开发者本机启动、重启、端口、依赖、`.env.test` 和 `restart-dev-services.sh` 问题改由 `intelligent-test-agent-local-startup` 或通用 `restart-services` 处理。
- 在正文增加运行目标入口判断，明确环境不清时先确认本机或企业服务器，本地问题不执行 DBeaver、binding 或企业现场日志流程；补充一个本地启动负向 eval。
- 将不受当前 Codex 技能 schema 支持的 `compatibility` 顶层字段移入正文，保留企业现场工具约束并使技能通过标准校验。

### How

- 使用 Skill Creator 的 `quick_validate.py` 验证技能结构，通过 `python3 -m json.tool` 验证 eval JSON，并执行 `git diff --check`。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目，未发现与本次两个技能文件冲突的未完成事项；工作区既有未跟踪目录不纳入提交。

### Result

- 企业部署后故障继续使用原有 DBeaver 只读 SQL 和现场日志证据链；开发者本机启动问题不再触发该技能。
- 本次仅修改技能说明、eval 和本机会话记录，不涉及业务代码、API、事件、数据库、Flyway、性能、安全契约、部署拓扑、环境配置、generated SDK 或 OpenCode 源码；无需启动项目服务。

## 2026-08-25 - 基于 release 最新提交同步用户手册

### Why

- `release` 最新提交已增加从工作台创建既有测试工作空间的新版本的入口，并将本地客户端切换为麒麟 ARM64 普通用户包；用户手册需要给出用户可执行的入口、前置条件、限制和排障方式。
- 用户要求手册保持图文并茂，并明确不写入游戏内容。

### What

- 在每周更新顶部补充“新增版本”和“麒麟 ARM64 客户端普通用户安装”两项说明，覆盖顶部与左下角入口、标准/非标准工作空间配置、Git 限制、账号灰度、Client Key、解压与启动步骤。
- 同步 FAQ 的 DEB 安装包和新增版本排障说明；在功能总览补充 SkillHub 创建人展示规则。
- 复用现有脱敏工作台截图展示版本选择入口和个人设置入口，并补充相应的可访问替代文字；未加入游戏内容。

### How

- 定向执行 `corepack pnpm exec vitest run apps/agent-web/tests/help-center.test.ts`，16 项通过；执行 `corepack pnpm --filter @test-agent/user-manual build` 和 `corepack pnpm --filter @test-agent/agent-web typecheck`，均通过。
- 在已运行的 `http://127.0.0.1:3000/help/` 中核验周报、功能总览和 FAQ 的新增文案与图片均已渲染；提交前回顾全部 `.agents/session-log*.md` 近期条目。

### Result

- 用户可以从手册找到新增版本和客户端用户包的准确入口、配置要求与失败处理方式；客户端和记忆功能的灰度边界继续明确，文档变更不涉及 API、事件、数据库、性能、安全、部署或环境配置。

## 2026-08-25 - 修复本地客户端重复安装与重装后工作区失联

### Why

- 稳定安装器的 `setup` 只在没有 `current` 时下载 release，重复双击不会切到新版，且 `systemctl enable --now` 不会重启已运行的旧进程。
- 客户端状态目录受启动环境影响时可能生成新实例 ID；平台工作区只按旧实例 ID 查重，使同一机器重装后的新客户端无法访问原工作区，旧离线实例还进入健康分母。

### What

- 重复 `setup` 改为重新校验签名 catalog、原子安装/切换最新 release，并显式 restart user systemd 服务；所有 Java 入口固定传入配置和状态目录，保留凭据与 `state.json` 稳定实例 ID。
- 工作区重新注册改为按用户串行化。新客户端重新选择目录后，只有旧实例离线且 `rootDigest + fileSystemIdentity` 唯一命中历史绑定时才保留 workspaceId 接管；同步迁移 Session 和 `SCHEDULED` 夜间任务目标。
- 新增 `V20260825091459__local_client_instance_replacements_create.sql` 保存替换谱系。旧实例全部工作区接管后从用户活动实例投影排除，但历史实例、Run 和已投递任务外键保留；旧实例真实重连会清除替换标记。

### How

- `LocalWorkspaceApplicationServiceTest` 2/2；`FlywayMigrationNamingTest` 与 MyBatis 实例/工作区/Session/夜间任务集成测试 19/19；安装包和更新状态机两套 shell 验收均通过。
- 使用 JDK 25、根目录 `.env.test` 和 `test` profile 执行 `restart-dev-services.sh`，后端 readiness、前端 3000、manager 和 ClickHouse 均启动成功；真实 PostgreSQL 已成功执行 `20260825091459`。
- migration 在源码、classes、persistence JAR 和 app JAR 中 SHA-256 均为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`。本机固定测试库只有一个活动客户端实例，无重复行可清理。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；保留并排除工作区已有托盘弹层代码、缓存、日志目录和 node_modules 改动。

### Result

- 新版安装包可安全覆盖安装并立即运行新版，不会因普通重装生成第二实例；已产生新 ID 的历史安装可在重新选择同一目录后安全恢复原工作区和会话。
- 本次不新增部署节点、不修改 HTTP API、RunEvent/SSE、generated SDK、OpenCode 源码或 `.env*`；新增一条前向 Flyway 结构 migration，并同步架构、部署、模块与数据库说明。

## 2026-08-25 - 修正本地客户端 Key 截图并补齐本地目录使用说明

### Why

- 原手册把 SSH Key 页面误用为客户端 Client Key 截图，不能帮助用户确认实际入口；客户端注册本地目录后的托盘操作、网页兜底和工作台打开路径也不完整。

### What

- 用当前“本地 OpenCode 客户端”界面重新生成脱敏 Client Key 截图，并新增网页兜底目录选择截图；删除周报中错误的客户端截图引用。
- 在每周更新、设置、工作区和 FAQ 中写明默认托盘“选择并注册工作区…”、目录选择、自动打开失败时的“打开网页”、顶部“工作空间 → 本地工作区”以及无托盘时的网页兜底路径。

### How

- 复用 `SettingsPersonalPanel` 的真实 Client Key、客户端状态和目录选择组件生成截图；Client Key 只以掩码展示，目录为示例路径。
- 执行 `corepack pnpm exec vitest run apps/agent-web/tests/help-center.test.ts`（16 项通过）和 `corepack pnpm --filter @test-agent/user-manual build`；在已运行的本地帮助中心逐页确认周报、设置和工作区文字与两张截图均渲染。

### Result

- 用户可从正确的 Client Key 页面创建并安全保存密钥，再通过托盘或网页兜底注册本地目录，并在工作台重新打开；不涉及 API、事件、数据库、性能、安全契约、部署或环境配置，未加入游戏内容。

## 2026-08-25 - 统一客户端托盘点击弹层主题

### Why

- 工作区选择、进度和确认窗口已经使用 FlatLaf，但托盘仍由平台原生 AWT `PopupMenu` 渲染，无法消费客户端主题，在 macOS 和麒麟桌面上会呈现老旧系统菜单样式。

### What

- 新增 `LocalClientTrayPopup`，复用现有桌面主题、小兔子资源、运行时快照和八项托盘动作，以白色圆角卡片、状态标题、轻边框、分组线和 toolbar hover 统一展示。
- `SystemTray` 只保留图标和点击事件；鼠标事件与标准 `ActionEvent` 共享防抖，弹层按顶部/底部托盘及多屏可用区域定位，支持失焦和 Esc 收起。无透明窗口或窗口裁剪支持时仅降级为不透明或直角卡片，不影响托盘动作。
- 保持打开网页、注册工作区、重连、公共能力更新、日志、进度和退出的既有业务处理不变，同步模块 README 和本地客户端架构文档。

### How

- 定向执行 `LocalClientTrayTest` 与 `LocalClientDesktopThemeTest`，8 项通过；执行本地客户端 Maven reactor 全量测试，219 项通过、1 项桌面条件跳过；跳过测试打包、`git diff --check` 和 shaded JAR 内容校验通过。
- 使用最终 shaded JAR 和真实 `LocalClientTrayPopup` 做 JShell 图形预览，确认卡片、状态、分组、按钮 hover 与失焦收起；将同一 JAR 替换到本地运行目录后启动真实客户端，日志显示托盘成功创建，客户端连接代次稳定在 258，并保持到 8080 和 4106 的连接。
- 本模块真正可执行的 shaded 产物是固定名 `target/test-agent-local-client.jar`；版本化的 `test-agent-local-client-0.1.0-SNAPSHOT.jar` 保留后端主类 manifest，不能用于启动客户端。最终运行 JAR 与 shaded 产物 SHA-256 均为 `077aa467e4d86e31d46d45151bc5f0f3e0d8a8417d313b5c88eb5614a217ab08`。
- 重启验证时发现 `screen -X quit` 不一定终止既有 login/Java 叶子进程；按精确 PID 清理遗留实例后，仅保留一份最终客户端，避免重复注册干扰验证。

### Result

- 托盘点击不再使用类似旧系统的 AWT 原生菜单，已与工作区选择窗口统一为 FlatLaf 卡片风格；最终客户端正在 `test-agent-local-client-ui-82d123ac6` screen 会话中运行。
- 本次不新增部署节点，不修改 HTTP API、RunEvent/SSE、文件 WebSocket、数据库、Flyway、性能或安全契约、环境配置、generated SDK 和 OpenCode 只读源码。受本机刘海区域遮挡影响，未取得状态栏图标直接点击截图；同一实际弹层组件的图形预览和最终客户端进程分别完成验证，麒麟真机视觉仍需现场复核。

## 2026-08-25 - 按新版 SkillHub 文档补齐统一认证号

### Why

- 新版 SkillHub 文档明确 `/upload` 和 `/download/{id}` 都必填 `userId`，语义为用户统一认证号；此前下载只带固定 `channel=3`，现场因此出现 `/list` 成功而下载返回 401。

### What

- SkillHub 上传在既有六个业务字段之外，由后端补入当前登录主体的统一认证号；平台 multipart 继续拒绝浏览器提交或覆盖 `userId`。
- 外部 Skill 预览、引用和更新触发下载时，统一调用 `/download/{id}?channel=3&userId=<统一认证号>`；HTTP 入口使用 `AuthPrincipal.unifiedAuthId`，文件 WebSocket 入口使用 ticket 已冻结的同一字段。
- 对统一认证号增加非空、长度和控制字符校验，并保持 URL、统一认证号、访问密钥和上游正文不进入日志或错误响应；同步 API、领域、工作区、integration 与安全规范文档。

### How

- 定向执行 `SkillHubHttpGatewayTest`、`AgentSkillHubApplicationServiceTest`、`AgentSkillHubControllerTest`，共 32 项全部通过，覆盖上传第七字段、下载 URL 编码、固定 `channel=3`、当前认证主体透传和客户端身份伪造拒绝。
- 执行 `mvn -pl test-agent-app -am -DskipTests package`，24 模块应用打包成功；`git diff --check` 通过。
- 尝试运行 `mvn -pl test-agent-api -am test`；SkillHub 所在及其前置业务模块均通过，但套件在既有 XXL-Job Testcontainers 的 Docker/Ryuk 启动检查中阻塞，线程栈确认停在 Docker HTTP 容器启动，人工中止后 API 模块未进入该轮全量执行，API 已由上述定向测试覆盖。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，未发现与本次 SkillHub 文件冲突的并行成果；保留并排除工作区既有 `.reasonix/`、Vite cache 和 `node_modules/`。

### Result

- 代码和文档已对齐新版 `userId` 契约，同时保留接口提供方此前明确的企业平台渠道 `channel=3`；需将新应用包部署到企业测试环境后再做真实 SkillHub 下载验证。
- 本次不新增部署节点，不修改浏览器 HTTP 路径/请求字段、RunEvent/SSE、数据库、SQL、Flyway、性能模型、环境配置、generated SDK 或 OpenCode 只读源码；安全边界收紧为服务端可信身份派生。

## 2026-08-25 - 基于最新 release 重建企业增量包并锁定客户端替换迁移

### Why

- 用户确认上一轮企业包尚未部署，要求基于当前本地 `release` 最新代码重新封装；企业现网已部署 worker、manager、工具盒子和独立 CK/mem0/BGE/pgvector 节点，未变化的大组件不能重复进入增量包。
- 当前代码新增 `V20260825091459__local_client_instance_replacements_create.sql`，正式打包、外层封装和目标机安装脚本原先尚未锁定该 migration 字节，存在 persistence JAR 携带错误版本却未被拒绝的发布风险。

### What

- 在 `package-release.sh`、`package-two-backend-complete.sh` 和 `deploy-internal-release.sh` 中锁定客户端实例替换 migration 的资源名与 SHA-256 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`；对应负向测试改为复制完整 persistence JAR 后只篡改该 migration，确保缺失和字节不一致都会被拒绝。
- 基于当前 `release` 工作树重建后端、前端和组织密钥签名的麒麟 ARM64 普通用户客户端；客户端版本为 `20260825103533`，公共配置继续锁定 `81605f245d1512e1ab0dd73812391f6da7d008b5`（8 Agent / 16 Skill / 8 Tool），下载与服务地址使用 `http://mimo.sdc.cs.icbc:9996`。
- 组件清单确认 worker runtime `reuse`、toolbox `reuse`、LobeHub `disabled`、memory runtime `disabled`、local client `included`；新客户端 release 目录只含 `20260825103533`，旧客户端版本未重复封装。两台后台继续携带 TCDS `http://tcds-prod.sdc.icbc:9080`、既有 CK/mem0/SkillHub 配置，密钥只做非空门禁且未输出。

### How

- Shell 语法、企业外层包和多后台节点两个负向门禁测试通过；内外 ZIP CRC、内嵌内层 ZIP SHA、三份节点包 SHA、persistence JAR migration 字节、组件指纹和客户端签名清单全部通过。
- 客户端离线安装/升级/自动回滚两套 shell 验收通过；前端帮助中心 16/16；后端客户端托盘、工作区接管、SkillHub 上传下载和 MyBatis 实例替换 7 类定向测试全部通过。首次后端测试因 shell 使用 Java 17、现有测试类为 Java 21 字节码而未执行，切换本机 JDK 25 后同一命令完整通过。
- 最终内层 ZIP SHA-256 为 `197b6a7eddb7f952fab235eb9fdfcf751443033f442291ee7b1fe62fe44b2463`；外层固定名包 SHA-256 为 `2abe82a7bb1a03d4c1d43fc3c8cb2eb083e0fb1af80cbf9d4bb23af88e21a9b6`。后端应用 JAR、persistence JAR、前端 tar 和麒麟用户包分别为 `00811815965034ba2e32315db997671d8e0b9589c54fc4d8930462cb73ea2d10`、`b7c6be7b7cfa925b2620b3f891f287bb450093314c923ad75cdd7cfdf73e191b`、`80fd5f5882391608529ae1472db7171986a7afc420be638c1ac2010706928c67`、`59ec4449cedb317f92a1996b24cb28d7d7fb4ff362b66baccbbb6e3b43711531`。

### Result

- 增量包只重建并携带本轮需要更新的平台代码、前后端制品和已变化的客户端完整离线单元；不会重启或替换现有 worker/manager、工具盒子、模型清单、CK、mem0、BGE、pgvector。
- 本次门禁修改不新增部署节点，不改变 API、RunEvent/SSE、数据库结构或安全契约；数据库结构变化来自已提交的客户端实例替换 migration。真实企业部署、Flyway 现场历史核对和麒麟真机从清空用户目录开始的安装验收仍待执行；未推送远端。

## 2026-08-25 - 修复企业 SkillHub 预览 ZIP 结构误判

### Why

- 企业内部实测中，SkillHub 目录能正常展示，但点击预览后返回 `VALIDATION_ERROR`，提示下载包根目录必须包含 `SKILL.md`。
- `/list` 只返回目录元数据，不返回 ZIP 条目；原实现却把下载包限定为根目录精确大写 `SKILL.md`，会误拒 SkillHub 外包唯一目录或返回 `skill.md` 的有效能力包。

### What

- 下载物化新增安全归一化：大小写不敏感识别唯一 `SKILL.md`，当所有文件位于同一能力目录时去掉共同前缀，并将清单规范为根 `SKILL.md`。
- 保留原有压缩体/解压大小、文件数、重复路径和路径穿越校验；多个清单、多根目录和额外根文件继续失败关闭。
- `/upload` 因为会原样将 ZIP 交给 SkillHub，仍严格执行接口文档的根目录大写 `SKILL.md`，不复用下载兼容逻辑。
- 前端能力来源按用户指定从 `skillmarket` 统一改为 `SkilMarket`，同步工程 README、包说明、HTTP API 与安全/后端规范。

### How

- `AgentSkillHubApplicationServiceTest` 24 项全部通过，新增单一外层目录+小写清单成功物化、多根目录拒绝、多清单拒绝、上传仍严格要求根清单四类回归。
- `agent-skill-hub.test.ts` 14 项通过；`agent-web` typecheck、用户手册构建和前端生产构建均成功。
- JDK 25 下执行 `mvn -pl test-agent-app -am -DskipTests package`，24 模块 Spring Boot 应用打包成功；`git diff --check` 通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，未发现与本次 SkillHub 服务、测试、前端组件和文档冲突的并行成果；工作区既有 `.reasonix/`、Vite cache、`node_modules/` 及结束时出现的本地客户端/工作台未暂存改动均保留原状，不纳入本次提交。

### Result

- 代码已支持 SkillHub 下载 ZIP 的唯一包目录和清单大小写差异，同时不放宽路径与多能力混包安全边界。
- 本次不新增部署节点，不修改 HTTP 路径/DTO、RunEvent/SSE、数据库、SQL、Flyway、性能模型、环境配置、generated SDK 或 OpenCode 只读源码；只调整物化行为并保留安全校验。真实企业环境还需部署新后端/前端制品后用原条目再次预览验收。

## 2026-08-25 - 新增近期功能 Light 宣传海报

### Why

- 用户需要为近期能力制作一张图文并茂的宣传海报，并指定使用 light 视觉风格。
- 首版画面因网格、技术标签和小截图过多，显得像说明板而非营销海报；用户明确反馈视觉效果不佳，要求重新设计。
- 用户随后明确最终内容只保留本地客户端（灰度）、长期记忆（灰度），并重点强调与智能研发门户 `Skill Market` 的打通。
- 用户认为三模块版的文字信息仍偏少，要求增加能说明能力边界和使用路径的内容。

### What

- 新增并重绘 `docs/assets/marketing/mimo-recent-features-poster.png`：最终稿采用 1800×2400 的轻量产品画册风格，以“能力，不止在一个工作台里。”为主张，使用留白、三张高辨识度功能卡片和真实产品截图组织视觉。
- 最终三项内容为“本地客户端 / 灰度开放”“长期记忆 / 灰度开放”“打通智能研发门户 Skill Market”；第三项最大化呈现 MIMO 能力库、Skill Market 与智能研发门户之间的发现、预览、引用、更新关系。
- 在不改变主视觉的前提下补充“本地执行 → 经验沉淀 → 企业复用”路径、本地托盘注册、记忆治理/证据、以及 Skill Market 元数据按需预览/引用/更新说明。
- 新增并重写 `docs/assets/marketing/mimo-recent-features-poster-philosophy.md`，以“好事发生”取代首版“流动的能力”，明确拒绝密集网格与功能清单式构图。

### How

- 复用用户手册中的长期记忆和本地客户端脱敏截图；Skill Market 使用“MIMO 能力库 → Skill Market → 智能研发门户”的桥接卡片，表达可发现、按需预览、引用与更新的实际边界。
- 使用 canvas-design 规范先编写设计哲学，再用 Pillow 渲染；根据用户反馈多次收敛为有信息密度但保有留白与主张的布局，使用 `python3 .tmp/render_mimo_features_poster_v3.py` 完成最终渲染并视觉检查新增文字、标签、截图不再互相遮挡。

### Result

- 最终 Light 海报已生成并通过视觉检查，尺寸为 1800×2400，PNG 可正常读取；本次仅新增宣传素材与设计说明，不涉及 API、事件、数据库、部署、性能、安全、环境配置或生成 SDK。

## 2026-08-25 - 统一能力来源文案、接通 SkillHub 上传并确认分类批处理路径

### Why

- 能力库来源文案把 `SkillMarket` 误写成 `SkilMarket`，平台来源“平台更新”也需要统一改为产品名 `MIMO`。
- SkillHub 已同步数百个 Skill，逐条由超级管理员在页面分类成本过高，需要支持先导出目录元数据、审核映射后批量更新。
- 当前来源按钮重复点击仍会重新请求目录，且平台虽已有 SkillHub 上传、进度和同步后端入口，前端没有一键上传链路。

### What

- 将能力库来源按钮和卡片统一展示为 `SkillMarket/MIMO`，内部 `SKILLHUB/PLATFORM` 枚举及 API 参数保持不变；同步组件测试、agent-web/package/backend-api README 与 HTTP API 文档。
- 同一来源重复点击直接返回；超级管理员可一次选择 ZIP 与三张图片，提交后自动以响应结束为起点每秒查询进度，100 秒查询窗口到期可继续查询，成功后立即同步并刷新 SkillMarket 目录。
- backend-api 新增 multipart 上传、进度和目录同步客户端；`FormData` 不设置 JSON `Content-Type`，由浏览器生成 multipart boundary。
- 确认外部 Skill 分类保存在 `agent_skill_hub_assets.skill_category/skill_subcategory`，可按 `asset_id` 通过 PostgreSQL 事务批量更新；分类组合仍受现有数据库约束限制。

### How

- 复用现有 `AgentSkillHub.vue` 来源筛选与卡片渲染，没有新增组件、接口或状态路径。
- 复用后端既有 `/external/upload`、`/external/upload/progress`、`/external/sync`，没有新增服务端 API；轮询用串行 `setTimeout`，避免慢请求重叠。
- `agent-skill-hub.test.ts` 与 `backend-api.test.ts` 共 139 项通过，两个包的 TypeScript 检查及 agent-web 完整生产构建通过；Vite 实际启动在 `http://127.0.0.1:3002/`，`/hub` 返回 HTTP 200。
- 分类处理采用“只读导出 SkillHub 元数据 → 生成逐资产映射 → 事务校验和更新”的流程，本轮未执行任何数据库更新。

### Result

- 能力库来源文案与产品命名一致；上传只需点击一次，进度、失败、软超时和目录同步均在同一交互链路反馈。接口、事件和数据库结构均未变化。
- 待用户导出 SkillHub 元数据后，再生成可审计的精确分类映射和更新 SQL；不会用未经确认的宽泛关键词直接修改全库。

## 2026-08-25 - 修复企业本地客户端失效重接入与历史实例展示

### Why

- 企业现场日志确认 WSS 已到达两台 Java，但服务端轮换 Client key 后，存量客户端持续以旧摘要认证并收到
  `UNAUTHENTICATED`；客户端已经写入 `re-enrollment-required`，重复下载/安装却因凭据文件仍存在而跳过 enroll。
- 版本升级留下的离线实例仍进入用户投影，头像菜单重复提供安装包；用户主动正常退出后，systemd
  `Restart=on-failure` 会尊重退出，但缺少可发现的手工启动/重连入口。客户端日志属性又在桌面 Logger 初始化后才设置，
  现场因此缺少预期 `client.log`。

### What

- `instances/me` 只投影仍有 Redis 短 TTL 实时连接的实例，离线历史继续保留数据库外键和审计；在线但 OpenCode
  不健康的实例仍返回，保留受控重启能力。
- 稳定安装器新增 `ensure_enrolled` 与 `start`：`setup/start` 识别安全的重新接入标记，成功 enroll 后清除标记并
  重启 user service；桌面入口改为“Test Agent 本地客户端”启动/重连，主动退出仍不自动拉起。
- 日志目录和 Logback 属性提前到任何桌面主题/Logger 初始化之前；头像菜单删除客户端下载入口，个人设置保留唯一下载；
  同步后端/前端 README、架构、HTTP API、部署手册、用户包 README 和内置用户手册。

### How

- system-management 全模块依赖链测试通过；本地客户端全模块 98 项通过、1 项既有条件跳过；前端定向 Vitest
  75 项、agent-web typecheck、前后端完整生产构建均通过。
- 本地客户端签名用户包与稳定升级两套 Shell 回归通过，覆盖失效标记触发 enroll、标记清除、桌面 `start` 恢复
  user service、无标记不重复 enroll、签名制品与稳定身份复用。
- 按 `.env.test` / `test` / JDK 25 尝试完整重启；构建成功，但本机 Docker 的 ClickHouse 容器停留在 `Created`，
  `docker run -d` 持续不返回。已终止精确悬挂进程并移除本次创建但未运行的容器，旧服务未被启动脚本切换。

### Result

- 代码级、模块级、签名安装包和完整构建已验证；新代码的本地运行级启动因 Docker 阻塞未验证，企业环境仍需发布新
  后端/前端/本地客户端完整 release 后，使用真实麒麟用户完成重新接入、唯一在线实例和 `client.log` 验收。
- 本次不新增部署节点，不改数据库结构、SQL、Flyway、RunEvent/SSE、generated SDK、OpenCode 源码或环境配置；
  HTTP 路径/DTO 字段保持兼容，仅收紧实例列表投影。企业 `local_client_releases` 仍需在制品发布后同步签名 catalog。

## 2026-08-25 - 修复历史本地工作区接管与企业模型路由

### Why

- 企业现场证据确认用户主键就是 `001177621`，旧工作区仍绑定已离线实例 `lci_ca417...`，而新实例
  `lci_be6f...` 已在线；原最近工作区接口只保存偏好，既不恢复客户端重启后丢失的根映射，也不把历史绑定安全接管到新实例。
- 最新失败请求把 `enterprise-openai` 作为 `X-Enterprise-Model-Provider` 发给 Java，但企业库实际启用的是
  `qwen-prod/deepseek-prod`；公共 `opencode.jsonc` 已提供 `enterprise-qwen/enterprise-deepseek` 到上述路由 ID 的精确映射。

### What

- 最近本地工作区接口改为激活入口：先路由到原绑定或用户唯一在线客户端，使用保存路径执行真实目录校验；同实例恢复
  根映射，旧实例离线时按完全一致的 `rootDigest + fileSystemIdentity` 保留 workspaceId 并接管 Session/待投递夜间任务。
- 客户端注册复用 `users` 行锁串行化同一用户连接，在新 route/grant 发布后撤销其它实例的 route/grant 和物理连接；
  撤销查询通过独立 MyBatis XML 按用户包含替换历史读取，不做全表扫描。
- 新增受管模型配置生成器：企业来源读取当前公共 `opencode.jsonc`，按 `X-Enterprise-Model-Provider` 映射并由运行时
  Provider 注册表过滤未启用或缺 Token 的条目；下发前把地址和 key 强制改写为 loopback 环境变量并移除 UCID、服务端地址及密钥。
- 前端在切换本地 Workspace 前先调用激活接口并采用返回的新实例身份；同步 API、架构、部署和模块 README。

### How

- JDK 25 下定向 Maven 覆盖 runtime/API/persistence 共 11 项，全部通过；前端工作区测试 15 项和 agent-web typecheck 通过。
- `restart-dev-services.sh --profile test --env-file .env.test` 完成 26 模块后端打包、manager 构建和前端生产构建，随后仍因
  Docker Desktop 的 ClickHouse `docker run` 无返回而终止。使用同一暂存 JAR、`.env.test` 生成环境并仅在一次性命令关闭
  不可用的 ClickHouse 后，主服务在 18081 启动，readiness 为 UP、CORS 预检 200；总 health 因旧服务占用 XXL-JOB 18080 为 DOWN，临时进程已停止。
- 提交前执行 `git diff --check`、冲突文件检查并回顾全部 `.agents/session-log*.md` 近期条目；不纳入并行的 SkillHub
  `frontend/packages/backend-api/src/index.ts` 修改及 `.reasonix/`、Vite cache、根 `node_modules/`。

### Result

- 代码和定向运行级验证表明：历史工作区只有完成真实目录身份校验才会转绑；新认证连接会成为该用户唯一实时实例；
  企业客户端不再硬编码不存在的 `enterprise-openai`，而采用公共配置与数据库运行快照一致的路由 ID。
- 本次不新增部署节点，不改数据库结构、Flyway、RunEvent/SSE、generated SDK、OpenCode 源码或 `.env*`；既有 HTTP 路径和
  DTO 形状保持兼容，新增一条 MyBatis XML 查询。企业现场仍需部署新后端/前端/客户端制品后，用 `001177621` 完成真实接管和对话复测。

## 2026-08-25 - 公共配置新增存量案例推荐子智能体

### Why

- 用户要求在独立公共 config 仓库新增存量案例推荐 subagent，并明确以本周同步的 `testagent.7z` 中有效 `opencode/` 配置为准；远程仓库额外的顶层历史会话等内容不作为实现依据。

### What

- 在 `temp/opencode-config` 新增隐藏的 `stock-case-recommendation`，由测试设计入口通过 `STOCK_CASES` 短路调用；审核意图优先，普通文件路径或任意斜杠文本不误触发。
- 新增最小权限 `asset_case_list` 工具，通过既有 `http_call.py` 查询 `/tcds/cases/by-menu`；返回结构化完整性状态，并拒绝 HTTP/业务错误、分页残片、重复或缺字段案例以及超过安全输出上限的结果。
- 未采用附件的 `write-file.ts`：运行时已有受 `edit` 权限保护的内置 `write`，附件实现既重复能力，也不能可靠阻止绝对路径、路径穿越和符号链接越界。
- 同步 `opencode/AGENTS.md`、测试设计 skill 元数据 4.7.0 和三条路由 eval；生成/审核 `policyManifest` 继续保持 4.6.0，以兼容等待恢复的既有设计会话。工具文件使用 `asset_case_list.ts`，确保 OpenCode 1.18.x 按文件名注册的工具 ID 与 prompt/权限一致。

### How

- 运行 OpenCode `debug agent` 确认子 Agent 为 hidden subagent，`asset_case_list/write` 可用且 `bash/task` 禁用；`debug startup` 成功。
- 使用 Bun 本地模拟资产接口验证成功、空结果、HTTP 错误、分页不完整、重复编号、非法服务配置和 45 KiB 输出门禁；skill 离线校验、eval JSON、frontmatter/引用和 `git diff --check` 通过。

### Result

- 公共 config 仓库 `master` 已提交 `9d0abe0`（`新增存量案例推荐子智能体`），未推送远端；远程额外顶层文件未清理、未继承到本次改动。
- 主项目不新增部署节点，不修改 API、RunEvent/SSE、数据库、Flyway、性能、安全协议、环境文件、generated SDK 或只读 OpenCode 源码；真实企业资产服务仍需在配置发布后做一次端到端推荐验收。

## 2026-08-25 - 同步 SkillMarket 上传用户手册

### Why

- release 已交付超级管理员一次上传 Skill ZIP 与三张审查图片、自动查询进度并刷新 SkillMarket 目录；内置手册尚未说明该入口、权限、材料和超时处理。

### What

- 在 Agent 与 Skill 配置、功能总览、每周新功能和常见问题补充 SkillMarket 上传流程、四项必需材料、超级管理员边界、外部材料脱敏要求、100 秒后继续查询及目录同步失败后的处理。
- 帮助中心回归测试覆盖新增专题、周报和 FAQ；周报保留本周摘要，并把完整步骤置于历史内容之后，以维持既有宠物问答上下文截断边界。

### How

- 依据 `AgentSkillHub.vue` 的真实按钮、阶段、文件类型、进度和同步状态，以及 `agent-skill-hub.test.ts` 的回归契约编写；未生成或伪造截图。
- `corepack pnpm vitest run apps/agent-web/tests/help-center.test.ts`（17 passed）、`corepack pnpm --filter @test-agent/user-manual build`、`corepack pnpm --filter @test-agent/agent-web typecheck`、操作截图引用检查和 `git diff --check` 均通过。

### Result

- 手册已同步当前 release 的 SkillMarket 上传能力；仍需管理员补拍“能力库 → Skill → 上传 Skill”弹窗的已脱敏真实截图后补入对应章节。
- 本次只修改手册与帮助中心测试，不涉及 API、事件、数据库、性能、安全实现、兼容性、环境配置、OpenCode 源码或部署节点。

## 2026-08-25 - Agent 配置树增加单文档快捷提交发布

### Why

- 公共级和应用级 Agent 配置原先只能切到 Git Changes 后手工寻找、暂存、填写说明并发布，单文档修改路径较长；Skill 还需要按整个目录作为同一提交单元。

### What

- 公共/应用 Agent 树为普通文件增加行内 Git 按钮，Skill 只在 `skills/{skillName}` 一级目录显示一次；点击后切换到既有变更面板，按真实 Diff 自动暂存精确文件或 Skill 目录内全部变更，再弹出提交信息框。
- 快捷入口复用既有公共个人 worktree 发布、应用个人 worktree 提交及 feature 投影发布程序；冲突、待重新推送状态或同作用域无关 staged 文件会阻断操作，取消弹框不擅自撤销已完成的暂存。
- 补充配置树事件、变更面板路径映射与组件回归测试，并同步 agent-web README/PACKAGE。

### How

- 前端全量 Vitest 152 个文件通过，2226 passed / 1 skipped；agent-web typecheck 和 production build 通过，`git diff --check` 通过。
- `restart-dev-services.sh --profile test --env-file .env.test` 完成后端 26 模块与前端构建，但再次卡在 Docker Desktop 的 ClickHouse `docker run`；在脚本停止旧服务前中止卡住进程，随后仅重启本次变更的前端，`127.0.0.1:3000` 可访问，未变更的后端 health 为 `UP`。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目；不纳入 `.reasonix/`、Vite cache 和根 `node_modules/` 等既有未跟踪内容。

### Result

- 可从公共或应用 Agent 配置树直接完成目标文档/Skill 的 stage、提交和远端推送，同时避免把此前暂存的无关文件带入提交。
- 本次仅修改前端交互和稳定文档，不新增或变更 HTTP API、RunEvent/SSE、数据库、Flyway、部署节点、性能模型、安全协议、环境配置、generated SDK 或只读 OpenCode 源码；整栈自动重启仍受本机 Docker ClickHouse 创建卡死影响。

## 2026-08-25 - 快捷推送成功保留工作区并同步本地公共配置

### Why

- 用户要求先把当前本地公共 config 区收敛到远程一致，并调整行内快捷提交：推送成功后继续停留在工作区，只有失败才沿用原先切到 Diff 的排障行为。

### What

- 将受管公共个人 worktree `.testagent/agent-opencode/.configdev/public-usr_test_dev` 的未提交状态保存到 `stash@{0}`（`codex-sync-public-config-20260825`），再把分支精确重置到 `origin/master`；未触碰独立的 `temp/opencode-config` 及其未推送提交。
- `GitChangesPanel` 继续常驻并复用既有暂存、提交和发布方法，但快捷提交框与后续进度框通过 Teleport 脱离隐藏的 Diff 根节点；成功不再切 Tab，暂存、提交或推送失败才向 `FigmaFileExplorer` 发事件并进入 Diff。
- 更新成功/失败组件回归和 agent-web README/PACKAGE，明确新的 Tab 收敛行为。

### How

- 初次同步完成时，公共个人 worktree 本地 HEAD 与 `origin/master` 均校验为 `81605f245d1512e1ab0dd73812391f6da7d008b5`，工作树和 index 干净；旧本地提交仍可由 reflog 找回，36 个文件的原 staged 状态保存在 stash。
- 定向 Vitest 2 个文件 76/76；前端全量 Vitest 152 个文件通过，2227 passed / 1 skipped；agent-web typecheck、用户手册及 agent-web production build、`git diff --check` 均通过。
- 仅重启本次修改的真实前端 screen；`127.0.0.1:3000` 可访问，后端 health/readiness 为 `UP`，登录 CORS 预检 200。临时浏览器会话只能到登录页，未使用或传输账号凭据，因此未在真实登录态制造配置变更做推送验证。
- 提交前复核发现用户随后在已有登录页面创建 `opencode/agents/wr.md` 并用提交信息 `ceshi` 实测快捷发布；后端完成本地提交，但远程 `master` 仍为 `81605f2`，个人 worktree 现为待重推提交 `e9d2366`、`publishPending=true`。该状态属于用户刚产生的失败恢复现场，未再次重置或覆盖。

### Result

- 受管公共 config 已按要求先与远程 `master` 完全一致；随后用户真实失败测试产生的新待重推提交被完整保留。快捷推送成功时底层保持工作区，失败时自动进入 Diff 并保留既有错误与恢复信息。
- 本次不新增 API、事件协议、数据库、Flyway、部署节点、依赖、性能模型、安全边界、环境配置、generated SDK 或 OpenCode 源码；真实远程副作用由既有 Git 发布接口负责，回归使用 mock 覆盖成功/失败状态收敛。

## 2026-08-25 - 删除 Agent 文档后自动进入快捷提交

### Why

- 行内快捷 Git 入口只能在文件仍存在时点击；普通 Agent 文件或整个 Skill 被删除后，树中不再有入口，用户仍需手工切到 Diff 才能提交删除记录。

### What

- 单个普通 Agent 文件删除成功后自动发起该文件的快捷提交；删除 `skills/{skillName}` 一级目录时按整个 Skill 发起同一流程，由既有 Git Diff 暂存真实删除记录。快捷提交会等待删除 revision 刷新入队，并在首次目标 Diff 仍为空时自动再刷新一次，避免并发刷新误报无变更。
- 删除 RPC 失败时不发起提交；Ctrl/Cmd 多文件批量删除继续保留在 Diff 统一处理，避免拆成多个提交弹框。
- 同步 Agent 配置面板测试、agent-web README 与 PACKAGE；未修改提交、推送 API 或失败切换 Diff 的既有收敛逻辑。

### How

- 前端全量 Vitest 152 个文件通过，2229 passed / 1 skipped；agent-web typecheck、用户手册及 agent-web production build、`git diff --check` 均通过；新增回归模拟删除 revision 与快捷刷新并发、首轮 Diff 为空而下一轮出现删除记录。
- 仅重启本次涉及的真实前端 screen；`127.0.0.1:3000` 返回 200，后端 health/readiness 为 `UP`，登录 CORS 预检为 200。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目；受管公共个人 worktree 中用户当前 `opencode/agents/wr.md` 删除状态保持不变，未替用户暂存、重置或提交。

### Result

- 以后从配置树删除单个普通文件或整个一级 Skill，删除完成即弹出原有提交信息框；成功仍留在工作区，暂存、提交或推送失败仍进入 Diff。
- 本次仅修改前端交互、测试与稳定文档，不新增或变更 HTTP API、RunEvent/SSE、数据库、Flyway、部署节点、依赖、性能模型、安全协议、环境配置、generated SDK 或只读 OpenCode 源码。

## 2026-08-25 - 本地客户端跨版本制品缓存与工作区重连恢复

### Why

- 企业用户重复安装或客户端自更新时，即使只有客户端 JAR 变化，仍可能重新下载 manifest 中全部运行制品；手工安装的既有复用又依赖旧 release 目录继续存在。
- 客户端重连后最近本地工作区需要用户重新选择，多个离线工作区还会因当前选择叠加 active 样式而呈现不同颜色。

### What

- 稳定安装器与 Java 自更新统一使用 `<installRoot>/artifact-cache/<KIND>/<SHA-256>/artifact{,.sig}` 内容缓存；每次命中重新校验类型、大小、摘要和当前发布公钥签名，缺失、摘要变化或验签失败只下载并原子修复对应项。旧 release 仅作为第一次升级的迁移来源，新 release 继续保持完整不可变副本且不使用硬链接。
- 客户端完成 `REGISTERED` 后异步恢复全局最近使用的本地工作区：重新核验真实路径摘要和文件系统身份，在同一实例恢复根映射，或由当前唯一在线实例接管离线旧绑定；失败只记录告警，不断开已认证连接。
- 工作空间菜单为所有离线本地工作区统一禁用颜色，离线当前项不再叠加红色 active 样式；同步 local client、runtime、API、前端和企业部署说明。

### How

- JDK 25 下运行 `mvn -pl test-agent-local-client -am -Dtest=LocalClientReleaseDownloaderTest -Dsurefire.failIfNoSpecifiedTests=false test`，14 项通过，覆盖只下载变化 JAR、单项缓存损坏只下载该项和旧 release 首次迁移。
- `bash deploy/internal/tests/local-opencode-client-package-test.sh` 通过，真实删除旧 release 后仅请求新 JAR，破坏 JDK 缓存后仅请求 JDK，修复后重复安装不请求 release 制品；`sh -n`、`bash -n` 和 `git diff --check` 通过。
- 本轮前序定向验证中，`FigmaShell.test.ts` 67 项通过；工作区恢复 service/handler 6 项通过。最终按 `.env.test` / `test` profile 重跑整栈启动，26 模块后端 package、用户手册和 agent-web production build 均成功；随后 Docker Desktop 创建 ClickHouse 容器超过 60 秒仍停在 `Created`，已只终止本次重启链路的精确 PID。脚本在停止旧服务前被阻断，因此原后端 `127.0.0.1:8080` health/readiness 仍为 `UP`、前端 `127.0.0.1:3000` 返回 200、登录 CORS 预检为 200，但进程仍运行上一份 immutable JAR，新构建未完成整栈切换。

### Result

- 只有客户端 JAR 变化时，制品层只下载新 JAR；未变化的 JDK、OpenCode 和公共能力包可跨版本、跨旧 release 删除继续复用，单项缓存失效不会触发全量下载。
- 最近本地工作区在客户端重连后默认可用，离线列表视觉统一。未新增部署节点、HTTP API、RunEvent/SSE、数据库、SQL 或 Flyway；未修改环境文件、generated SDK 或只读 OpenCode 源码。整栈后端启动仍受本机 Docker ClickHouse 卡住影响，属于未完成的运行态验证风险。

## 2026-08-25 - 收起公共配置发布明细并补充应用业务标识

### Why

- 公共配置管理页把 rollout 历史、服务器和用户进程一次性全部展开，信息密度过高；应用发布只显示内部版本键，管理员无法直接判断具体应用、工作空间和版本，也容易误以为与相邻的“应用 Git 刷新”重复。

### What

- 公共 rollout 的用户进程明细改为默认收起；应用 rollout 采用“历史记录 → 用户进程”两级原生折叠，摘要直接展示应用、工作空间、版本、分支、提交、状态、服务器数和待处理用户数，内部版本键不再渲染到页面。
- 复用既有 `listApplicationGitRefreshScopes()` 数据完成业务名称映射，不新增接口；映射失败时保留分支与提交摘要并显示诊断提示，2 秒活动 rollout 轮询不重复请求范围数据。
- 将应用区明确命名为“应用 Agent / Tool 运行态更新”：相邻“应用 Git 刷新”负责仓库与 worktree 同步，本区只处理发布后的用户进程 dispose / 受管重启。公共和应用用户明细复用同一折叠组件，危险操作只在展开后可见。
- 同步 agent-web、前端总览、模块图和前端交互规范，并补充默认折叠、业务标识和既有停止/重启 API 复用的组件回归。

### How

- `scheduler-management-panel.test.ts` 23/23 通过，agent-web `vue-tsc` 类型检查和 production build 通过；真实浏览器分别检查历史收起、历史展开和用户展开三种状态，确认业务摘要可见、内部版本键不进入 DOM、控制台无错误。
- 按 `.env.test` / `test` profile 执行整栈重启，26 模块后端 package 与前端 production build 成功；启动再次卡在 Docker Desktop 的 ClickHouse 容器创建（状态停在 `Created`），脚本在停止旧服务前中止。原后端 `127.0.0.1:8080` health 为 `UP`，前端 `127.0.0.1:3000` 返回 200，ClickHouse `18123` 未就绪。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目，未发现与本次前端折叠和应用名称映射冲突的并行成果；不纳入 `.reasonix/`、Vite cache 和根 `node_modules/`。

### Result

- 管理员无需理解内部版本键即可定位具体应用、工作空间和版本；页面默认保持紧凑，需要排障时再逐层展开服务器和用户进程。
- 本次仅修改前端展示、测试与稳定文档，不新增或变更 HTTP API、RunEvent/SSE、数据库、Flyway、部署节点、依赖、安全协议、环境配置、generated SDK 或只读 OpenCode 源码。整栈进程未切换到新构建，运行态验证由现有前后端进程与真实浏览器页面完成，Docker ClickHouse 卡住仍是本机启动风险。

## 2026-08-26 - 修复非 Git 本地目录与全部历史工作区重连恢复

### Why

- 企业新版客户端仍把普通非 Git 目录巡检为 `INACCESSIBLE`，工作台因此禁用目录；旧版本或重装产生不同实例 ID 后，自动重连又只恢复全局最近一个工作区，其余真实存在的历史目录继续显示离线。

### What

- 客户端把非 Git 目录改为 `UNKNOWN + NOT_GIT_REPOSITORY`，服务端列表同时兼容归一化旧客户端已经保存的错误 `INACCESSIBLE` 投影，不要求现场修数据。
- 新客户端完成 `REGISTERED` 后按“最近项优先、其余历史绑定按更新时间”扫描该用户全部实例，每个目录独立校验真实路径摘要与文件系统身份；有效目录保留 workspaceId 并转绑到当前唯一在线实例，单项失败在独立事务回滚后跳过，不阻塞其它目录。
- 工作台检测到在线本地端点但仍持有离线或旧版非 Git 投影时，随既有 5 秒端点探测短时刷新工作区列表，恢复后停止，单次连接最多尝试两分钟。
- 客户端签名打包入口新增版本下界：新 release 必须严格高于整包组件状态中的已部署版本及输出目录中的全部已有版本；平台重封且客户端组件未变化时继续复用原版本，不制造空升级。

### How

- JDK 21 定向 Maven reactor 覆盖 local-client、workspace-management、opencode-runtime、API 四类测试共 20 项，23 个依赖模块构建成功；更大范围测试已先通过 common 至 memory 等 20 个模块，后在无关 XXL Testcontainers 启动 Ryuk 时被本机 Docker 卡住并人工中止。
- 前端重连刷新 Vitest 3 项通过；agent-web typecheck 只报另一个 agent 在途的 `custom-menu-settings-panel.test.ts` 两处类型错误，本次客户端文件没有新增诊断。
- 本地客户端 fat JAR 跳过测试重新打包成功，并以 JDK 21 实际执行 `java -jar ... --version`，返回 `test-agent-local-client 0.1.0-dev`；未重启整栈，避免覆盖同工作树另一个 agent 正在进行的前端改动。
- 麒麟客户端完整分发 Shell 测试覆盖已有目录版本倒退、组件状态相同版本复用拒绝、签名 catalog、安装和升级链路并通过；三个相关发布脚本也通过 `bash -n`。
- 已回顾全部 `.agents/session-log*.md` 近期条目并执行 `git diff --check`；本次提交仅暂存客户端修复、对应测试/文档及本日志，隔离自定义菜单、Playwright 产物、`.reasonix/`、Vite cache 和 `node_modules/`。

### Result

- release 分支不新增部署节点；非 Git 本地目录可作为普通工作区使用，历史版本留下的多个真实目录会在当前唯一在线客户端上自动验真恢复，缺失或身份变化目录保持不可用且不会拖累其它目录。
- 下一次实际包含客户端变化的企业包必须使用高于现网 `20260825204745` 的新 14 位版本；本次只落实代码与发布门禁，尚未生成或部署新的组织密钥签名企业制品。
- 未新增 HTTP API、RunEvent/SSE、数据库、SQL、Flyway 或依赖，不修改环境配置、generated SDK 或只读 OpenCode 源码；真实麒麟企业包部署后的旧实例批量接管仍需现场验收。

## 2026-08-26 - 支持用户自定义左侧菜单与功能页 Tab

### Why

- 用户需要在左侧活动栏维护常用页面入口：填写菜单名、选择内置图标和 URL，点击后像能力库、控制台一样在应用内打开独立 Tab。
- 最终交互要求配置入口直接使用 `+`，并始终位于能力库和全部已配置自定义菜单之后。

### What

- 新增用户级自定义菜单模型、8 个受控图标、配置弹窗和受限 iframe 页面；最多 10 项，名称最长 12 个字符，URL 仅允许无凭据的 HTTP(S) 地址或同源根路径，并提供刷新和新窗口兜底。
- 能力库之后按配置顺序渲染自定义入口，`+` 固定为菜单区最后一项；配置按 `userId` 写入版本化 `localStorage`。自定义页面使用 `/custom/:menuId`，复用既有功能页 Tab 的去重、排序、关闭、浏览器历史和 `sessionStorage` 恢复；配置删除后同步关闭对应 Tab，删除活动项时回到相邻页或工作台。
- 自定义 iframe 在 Tab 失活时卸载、重新激活时重载，避免后台页面持续轮询或动画；活动栏自定义项增加可滚动布局。同步前端总览、agent-web README/PACKAGE、前端规范和模块地图。

### How

- agent-web typecheck 通过；相关 Vitest 8 个文件 54/54，通过后全量 Vitest 157/157 个文件、2246 passed / 1 skipped；新增 Playwright 自定义菜单流程 1/1 通过，并断言 `+` 位于自定义菜单之后。
- agent-web production build 通过；真实登录页面完成配置弹窗、活动栏、自定义 Tab、同源 iframe 与新窗口工具栏视觉检查，控制台无新增错误，检查后清除了本次创建的浏览器菜单偏好。
- 按 JDK 25、`.env.test`、`profile=test` 执行整栈重启，26 模块后端 package 和前端 production build 成功，但 Docker Desktop 的 ClickHouse `docker run` 持续不返回；在脚本停止旧服务前精确终止本次重启进程。原后端 health/readiness 为 `UP`、前端 `127.0.0.1:3000` 返回 200、登录 CORS 预检 200，未留下重启进程。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目并执行差异检查，隔离 `.playwright-cli/`、`.reasonix/`、Vite cache 和根 `node_modules/` 等未跟踪内容。

### Result

- 登录用户可通过活动栏末尾 `+` 配置、编辑、排序和删除自定义入口，并在同一应用内的多 Tab 工作区打开页面；刷新后只恢复仍有对应配置的自定义 Tab。
- 本次仅修改前端交互、本地浏览器持久化、测试与稳定文档，不新增或变更 HTTP API、RunEvent/SSE、数据库、SQL、Flyway、依赖、部署节点、强制配置、generated SDK 或只读 OpenCode 源码。整栈进程未切换到新构建，Docker ClickHouse 卡住仍是本机启动风险；当前 Vite 服务加载工作树源码并保持可访问。

## 2026-08-26 - 基于 release HEAD 重打企业增量包

### Why

- 上一候选企业包尚未作为本轮现场基线，`release` 又新增了本地工作区恢复和自定义左侧菜单提交，需要按当前 `64ca87e80c607589314bae90c96d0e892643e255` 重新构建，而不是只改外层 ZIP。

### What

- 重新构建 Java、前端静态资源和组织密钥签名的麒麟 ARM64 客户端；客户端使用严格递增不可变版本 `20260826095116`，继续固定企业入口 `http://mimo.sdc.cs.icbc:9996` 和已审核公共能力提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`。
- 组件指纹判定 worker runtime 与 toolbox 均为 `reuse`，客户端为 `included`；不重新打入或重启 manager/worker、CK、Mem0、BGE、pgvector、LobeHub、Workflow 和 trace 专项制品，也不覆盖 `.4` 的 Qwen 优先模型灰度配置。
- PostgreSQL 与 XXL migration 相对上一候选包均无新增；相对 2026-08-24 企业基线仍只有 `V20260825091459__local_client_instance_replacements_create.sql` 待执行，源码和最终 persistence JAR 资源 SHA-256 均为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`。

### How

- 后端 `LocalClientConnectionWebSocketHandlerWorkspaceTest`、`LocalGitAccessCheckerTest`、`LocalWorkspaceApplicationServiceTest`、`WorkspaceApplicationServiceTest` 定向 Maven reactor 通过；前端 7 个相关 Vitest 文件 30/30、全 workspace typecheck 通过。
- `local-opencode-client-package-test.sh` 与 `local-opencode-client-update-test.sh` 通过；正式发布脚本完成 Spring Bean 构造器门禁、后端打包、前端 production build、客户端签名/catalog/用户包校验，以及最终 JAR 内全部 PostgreSQL、XXL 和 ClickHouse migration 字节校验。
- 构建前回顾全部 `.agents/session-log*.md` 近期条目；保留并排除 `.reasonix/`、Vite cache 和根 `node_modules/`，未拉取、合并或推送远程。

### Result

- 最终内包 SHA-256 为 `8bf9e87214a64989fc64244eafee5767d906eb4d3548404e4855dad783f57f34`，固定外层包 SHA-256 为 `6d39d994432a15411c127ce8d3488766bd4ff80d6d0c5323ee4b8d1b1ec062f9`；外层嵌入内包摘要逐字一致，三台节点包 checksum、ZIP CRC、客户端 RSA 签名、AAM/TCDS/路由配置和 Nginx 双后台渲染均已通过。
- 正式外层包与摘要已写入 `/Users/kaka/Desktop/mimoagent/0709/` 并再次执行 `sha256sum -c` 通过；企业现场仍须按 `.4 → .114 → .2` 灰度执行并核对 Flyway history，未把本机构建等同于现场部署成功。
- 本次打包不修改业务代码、API、事件、SQL、migration、环境文件、generated SDK 或 OpenCode 只读源码；目标分支仍为不新增部署节点的 `release`。

## 2026-08-26 - 工作区文件树支持 Shift 连续选择

### Why

- 工作区文件树原有 Ctrl/Cmd 增量多选，但大量连续文件和目录只能逐项点击；用户需要先确定首项，再用 Shift 从上到下连续选择并复用既有右键批量操作。

### What

- `FileExplorer` 根组件新增页面内选择锚点，递归 `DirectoryRows` 按当前真实可见顺序展开范围：展开目录后代参与、折叠目录后代不参与，文件和目录都可进入范围，只读引用、自动化引用和混合目录继续跳过。
- 普通点击、Ctrl/Cmd 点击、右键单项收敛和拖动单项都会更新锚点；Shift 单击替换为连续范围，Ctrl/Cmd+Shift 单击可把范围合并到已有选择。右键删除、剪切、拖动和仅文件复制继续复用原事件链路；父目录与后代同时选中时仍只提交顶层目标。
- 同步 file-explorer README、前端总览和工作区用户手册，并补充跨展开目录、折叠后代、只读跳过、右键操作和普通点击建立锚点的组件回归。

### How

- 文件树定向 Vitest 2 个文件 40/40；前端全量 Vitest 157/157 个文件、2249 passed / 1 skipped；全 workspace typecheck 和 agent-web production build 通过，`git diff --check` 无异常。
- 独立 Vite 实例启动于 `127.0.0.1:15174`；新增 Playwright 工作台端到端用例，以浏览器事件展开目录、Shift 连选 5 个可见文件/目录并右键删除，确认菜单按 4 个顶层条目收敛、目录参与时不显示复制，并记录到 4 次 `workspace.delete`，用例 1/1 通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期条目，确认本次只暂存 Shift 选择实现、测试、稳定文档和本日志，不纳入同工作树已有用户手册修改、`.reasonix/`、Vite cache 或根 `node_modules/`。

### Result

- 用户可先普通点击一个可写文件或目录，再按住 Shift 点击另一项，连续选中两点之间当前可见的可写条目并直接右键批量操作；原 Ctrl/Cmd 多选保持兼容，连续选择到批量删除的浏览器链路已完成端到端验证。
- 本次仅修改前端交互、测试和稳定文档，不新增或变更 HTTP API、WebSocket 文件协议、RunEvent/SSE、数据库、Flyway、依赖、部署节点、安全边界、环境配置、generated SDK 或只读 OpenCode 源码。

## 2026-08-26 - 同步 release 内置用户手册

### Why

- `release` 自上次手册同步后已交付自定义左侧菜单、Agent/Skill 行内快捷提交、客户端重连恢复历史工作区和工作区文件树 Shift 连续选择；用户手册需要给出真实入口、权限与数据边界。

### What

- 更新设置、Agent/Skill、工作区、功能总览、每周新功能和 FAQ；补充自定义菜单仅存当前账号当前浏览器、快捷提交的冲突/暂存门禁、客户端恢复的真实目录校验，以及 Shift 连续选择的可见范围和只读跳过规则。
- 更新帮助中心回归，继续校验灰度、完整周更正文、图片存在性及游戏内容排除。

### How

- 在 `release` HEAD `b650381d95cb18706e2f12c46f72a06351633d29` 执行 `corepack pnpm exec vitest run apps/agent-web/tests/help-center.test.ts`（17/17）、`corepack pnpm --filter @test-agent/user-manual build`、`corepack pnpm --filter @test-agent/agent-web typecheck` 和 `git diff --check`，均通过。
- 已回顾全部 `.agents/session-log*.md` 近期记录；仅暂存手册、帮助中心测试和本日志，排除 `.reasonix/`、VitePress cache 与根 `node_modules/`。

### Result

- 未新增或变更 API、RunEvent/SSE、数据库、Flyway、性能、安全策略、环境配置、generated SDK 或 OpenCode 只读源码。所有稳定章节仍有现有脱敏操作截图并由测试校验引用；新流程尚缺自定义菜单、快捷提交和 Shift 选择的真实截图，需由人工补拍后补入。

## 2026-08-26 - 调整存量案例与测试设计合流

### Why

- 用户进一步明确目标链路：输入“生成 S 子条目测试案例，存量案例参考目录：aa/bb/cc”后，目录原样作为 TCDS 入参，完整存量案例先写入工作区 `docs`，再与当前详细设计和既有资产共同参与正常案例设计。
- 前一版把存量案例作为 `STOCK_CASES` 独立短路，并让子 Agent 读取设计、评分和输出推荐文件，会绕过 Phase A/Review，也把案例来源与设计方法耦合成两套判断。

### What

- 独立公共 config 仓库保留技术 ID `stock-case-recommendation`，但职责收敛为 TCDS 材料准备：每个 I/S 只查一次，参考目录整体传入，完整七列结果写入 `workspace/docs/存量案例-<S>-<timestamp>.md`，不读详细设计、不评分、不写 `041-测试设计`。
- 删除 `STOCK_CASES` 交付路由；存量目录成为 `FULL` 的可选输入。Generation 先按当前需求、详细设计和适用的 `docs` 资产完成并冻结 Phase A，之后再按新增 `stock-case-reuse.md` 做 `REUSE_AS_IS / ADAPT / REJECT / NEW`，最终继续独立 Review。
- 增加材料 SHA-256、I/S/manifest/摘要校验、当前设计优先、跨方法语义去重和内部来源追溯；`isUpdate`、`aiAiCase` 仅保留原值，不推断新旧或质量。同步公共 README、AGENTS、Generation/Review 契约、质量门禁和 22 条 eval。

### How

- OpenCode 1.15.10 使用仓库 `opencode/` 作为 `OPENCODE_CONFIG_DIR`，`debug config`、`debug agent stock-case-recommendation` 和 `debug agent test-design-orchestrator` 均成功；Skill 实际加载为 4.8.0。
- Bun 构建工具成功；本地模拟 TCDS 验证路径 `aa/bb/cc` 原样传递、正常结果带 SHA-256、空列表继续、部分结果返回 `PARTIAL_RESULT`；normalize/重复编号/HTTP 输出解析/时间戳辅助逻辑通过。
- 22 条 eval JSON、25 个 Agent/Skill frontmatter、test-design 规则/模板引用、Markdown fence、过期 `STOCK_CASES` 扫描和 `git diff --check` 均通过；提交前已回顾全部 `.agents/session-log*.md` 近期记录。

### Result

- 公共 config `master` 已提交 `7a29ca4`（`调整存量案例与测试设计合流`），工作树干净，未推送远端；上一提交 `9d0abe0` 仍保留历史，本提交修正其短路设计。
- 主项目仍在不新增部署节点的 `release`；本次只记录独立配置仓库交付，不修改应用 API、RunEvent/SSE、数据库、Flyway、环境配置、generated SDK 或只读 OpenCode 源码。真实企业 TCDS 与完整模型链路仍需配置发布后做现场端到端验收。

## 2026-08-26 - 基于企业压缩包重打存量案例配置

### Why

- 用户要求以昨日提供的 `/Users/kaka/Downloads/光学文件接收/testagent.7z` 为唯一基线重新打包已更新文件，不能用公共远程仓库整树替换，因为远端还含企业当前不使用的内容。

### What

- 原包 SHA-256 为 `c9f9ebbe20d3eee96eb696dacdf51f07190d02db117fb78ca143510078ec3b0a`，内嵌配置仓库为干净的 `feature_config@988b4b4`。逐文件确认 11 个既有目标与公共配置功能改造前 `81605f2` 完全一致，因此只覆盖 11 个修改文件并新增 3 个存量案例文件；原包未包含的远程根 `README.md` 和其它远程内容均未加入。
- 在内嵌企业仓库提交 `5e589ce8f5af0a2fed4e9d5ea742fb3b920705ea`（`【feature_config】新增存量案例与测试设计合流`，带 Change-Id），保持工作树干净。Mac 上仅临时修正企业 commit-msg hook 的 Perl 路径以执行原逻辑，提交后恢复，hook SHA-256 仍为 `0e03afe18aff5511474349853c48a8370ec9b80d64e9cce735a9ae957f29ec16`。
- 生成 `/Users/kaka/Downloads/光学文件接收/testagent-updated.7z` 和同目录 SHA 文件；保留原 `testagent.7z` 未覆盖。

### How

- 新包使用原包相同的 7z solid LZMA2:20 参数；`7zz t` 和重新解压通过，共 315 个目录、959 个文件，内嵌 Git `fsck` 通过且工作树干净。
- 重新解压后核对 14 个覆盖文件与公共配置 `7a29ca4` 对应文件逐字节一致；22 条 eval JSON、25 个 Agent/Skill frontmatter、规则/模板引用、过期 `STOCK_CASES` 扫描、Bun Tool 构建和 OpenCode `debug config` 均通过。
- 新包 SHA-256 为 `b83e755abdb1753172c97224db486a48a014e0fc829b35a11425c2f0652717fb`，配套 `.sha256` 执行校验为 `OK`。

### Result

- 本次交付是独立公共配置 7z，不是平台前后台完整发布 ZIP；不涉及 Java、worker、前端、`backend.env`、`docker.env`、数据库、Flyway、API、RunEvent/SSE、环境文件、generated SDK 或只读 OpenCode 源码。
- 包已在 Mac 生成并校验，尚未推送公共远端、导入企业中转机或执行平台公共配置发布；企业导入后仍需用真实 TCDS 完成一次“目录查询 → docs 材料 → Phase A/B → Review”端到端验收。

## 2026-08-26 - 临时 Agent 文件删除不触发快捷提交

### Why

- 公共或应用 Agent 区中新建但从未提交过的临时文件被删除后，Git 不会留下删除记录；原快捷提交流程仍立即展示弹框，随后提示“没有可提交的 Git 变更”，与真实 Git 状态不一致。

### What

- Agent 单文件和一级 Skill 删除请求增加 `DELETE` 来源标记；常驻 `GitChangesPanel` 对该来源先在后台复用既有真实 Diff 刷新和目标匹配，确认存在 `D` 记录后才展示弹框并继续原 stage/提交/推送链路。
- 连续两次真实 Diff 刷新后仍无目标时静默结束，不弹框、不 stage、不触发失败跳转；真实删除的权限、冲突、待推送提交、无关 staged 文件和 stage 失败仍沿用既有错误弹框与 Diff 处理。
- 同步 agent-web README、包级说明、组件回归，并新增工作台 Playwright E2E：浏览器真实点击新建公共临时文件、删除和确认，校验至少两次公共 Diff 均为空、没有 stage、没有提交弹框且中心区不跳到空 Diff。

### How

- Agent 配置与 Git Changes 定向 Vitest 2 个文件 97/97；前端全量 Vitest 157/157 个文件、2250 passed / 1 skipped；agent-web typecheck 和 production build 通过。
- Playwright 工作台端到端用例 `deleting a newly created untracked public Agent file does not open quick commit or Diff` 1/1 通过，临时文件仅存在于测试 mock 内并在删除确认后清理。
- 按 `.env.test` + Java 25 执行标准整套重启：后端 26 模块打包和 opencode-manager 构建通过，但 Docker Desktop 在启动托管 ClickHouse 的 `docker run` 长时间无响应；脚本尚未停旧服务即被终止。随后只重启本次变更的 `test-agent-frontend`，前端 `3000`、既有后端 health/readiness、登录 CORS 均验证通过。

### Result

- 临时未跟踪 Agent 文件删除现在静默收敛；已跟踪文件和 Skill 的真实删除仍会自动暂存并进入原快捷提交，成功留在工作区、真实失败才跳 Diff。
- 本次仅修改前端交互、测试和稳定文档，不新增或变更 HTTP API、WebSocket 文件协议、RunEvent/SSE、数据库、Flyway、性能、安全策略、部署节点、环境配置、generated SDK 或只读 OpenCode 源码。Docker Desktop 仍存在长期挂起的环境问题，未把整套重启报告为成功。

## 2026-08-26 - Agent 跟踪删除支持弹框内取消

### Why

- 已提交过的 Agent 文件或一级 Skill 删除后会被快捷提交自动暂存；原弹框只有“取消”，只能关闭弹框并保留删除，用户需要再进入 Diff 手工回退，和删除场景的即时反悔预期不一致。

### What

- 删除来源的快捷提交弹框新增“取消删除”，直接复用 `GitChangesPanel.discardAgentFiles` 的 staged/unstaged 共用回退，一次恢复 Git index 与工作树；普通“取消”继续只关闭弹框并保留暂存。
- 回退期间锁定关闭、提交和重复回退；成功后关闭弹框、清空 Diff，`FigmaFileExplorer` 复用 `AgentConfigPanel.refreshAll` 恢复目录树，`AgentWorkbench` 归一化公共 Diff 的 `opencode/` 路径后重读已打开标签；失败保留弹框并显示原因。
- 同步前端根/应用/包说明、用户手册、前端规范和模块图；组件测试覆盖公共 staged 删除回退，Playwright 覆盖真实点击删除、取消删除、文件树/正文恢复和 Diff 清空，并保留临时未跟踪删除不弹框的回归。

### How

- GitChangesPanel 定向测试 58/58；前端全量 Vitest 157/157 个文件、2251 passed / 1 skipped；agent-web typecheck、用户手册 VitePress build 和生产 Vite build 均通过。
- Playwright 两条删除端到端用例 2/2 通过：临时未跟踪文件删除无弹框，跟踪文件删除后“取消删除”恢复文件并保持文件树活动态。
- 根 `corepack pnpm --filter` 验收入口会被任务外忽略目录 `temp/opencode-config/tools/package.json` 的非法 JSON 扫描失败阻断；未修改该文件，改用 `frontend/node_modules/.bin` 中同版本 Vitest、Vue TSC、Playwright 和 Vite 直接完成全部验证。
- 当前 `.env.test` 服务继续运行：后端 health/readiness 为 UP，前端 `127.0.0.1:3000` 返回 200，登录 CORS 正常；清理了先前测试遗留的 15174 辅助 Vite，标准 3000/8080 服务未中断。提交前已回顾全部 `.agents/session-log*.md` 近期记录。

### Result

- 已跟踪 Agent 文件和一级 Skill 删除可在快捷提交弹框内直接反悔；临时未跟踪文件仍静默删除，多文件批量删除仍由 Diff 统一处理。
- 本次只修改前端交互、测试和稳定文档；不新增或变更 HTTP API、WebSocket 文件协议、RunEvent/SSE、数据库、Flyway、性能、安全、部署节点、环境配置、generated SDK 或只读 OpenCode 源码。

## 2026-08-26 - 基于当前 release 重打企业离线包

### Why

- 用户要求基于当前本地代码重新打企业包；本轮不拉取远端，也不把未变化的 worker、toolbox、manager、CK、Mem0、BGE 或 pgvector 重新纳入更新范围。
- 上一轮生成的企业包尚未收到现场部署完成确认，因此本次不能按构建机组件状态省略其中尚未落地的本地客户端，需继续保留同一客户端版本供企业用户清空后重新下载安装。

### What

- 发布业务代码基线为本地 `release@eabbc26cb55a249ff377907ab0d40a448dcc7e53`；相对上一候选包新增工作区文件树 Shift 连续选择、临时 Agent 文件删除提示修正、已跟踪 Agent/Skill 删除弹框内取消删除，以及对应测试、用户手册和工程文档。
- 使用企业固定 AAM 地址 `http://zfw.sdc.cs.icbc/aam/onlyLogin/` 和相对后端 API 重建前端；使用 JDK 25 重建后端，并由打包脚本逐项核对持久化、XXL 和 ClickHouse migration 字节。
- 本地客户端代码没有变化，继续保留版本 `20260826095116`、企业域名 `http://mimo.sdc.cs.icbc:9996`、固定组织签名公钥和公共能力配置提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`；不重新生成客户端版本。

### How

- 在 `frontend` 执行全量 Vitest：157/157 个测试文件通过，2251 passed / 1 skipped；全 workspace `typecheck` 通过，生产 Vite 构建通过。
- `package-release.sh --backend-only` 生成后端 JAR，SHA-256 为 `ac0528e7338e051303f87ee807696ce6a2a6c69b5a0ae9785c07b02dce5006c0`；`package-release.sh --frontend-only` 生成前端归档，SHA-256 为 `517c8acf48397df002d3b2e7fc846b5e6cc685d13af2d1b52f94c600f0e688d5`。
- 内层发布 ZIP SHA-256 为 `39879e539928f209aa7b4303c5f77bdd0187d65ebfd9c7aaed155dfe3bf72730`；外层三节点完整包 SHA-256 为 `b627e6172e860f13068f0764f6bc2e5d03d0f1f60c9b383c66d88c474ab459c2`。`.4`、`.114`、`.2` 节点包 SHA-256 依次为 `b5ab6236054b3b900ccf50fa48207296f5cc3075dc976eb8da986701aea76aed`、`4d8b8ad561d79272f6840b2644378017abb37768014e151429b139c97d074c01`、`22e632e312943047b1b145129d451f4d2864e9d08d4ec4b100ed83e9dd740291`。
- ZIP 完整性、内外层和三节点 checksum 均通过；组件清单确认 worker/toolbox 为 `reuse`、LobeHub/memory 为 `disabled`、客户端为 `included`。节点包只含部署脚本和节点配置，不含 worker、manager、models 或 trace 制品；TCDS/AAM 地址正确，两个后端节点的 SkillHub access key 均非空且未输出密钥值。
- 客户端离线发行版完整性门禁和 RSA SHA-256 签名验证通过；组织公钥 DER SHA-256 为 `6d294535e5bf4c2a0ea2ba3ae8b1fc9502444de7639df3607d5e360846c9ca62`。前端产物包含正确 AAM 地址且不含三台服务器硬编码 API 地址。
- `V20260825091459__local_client_instance_replacements_create.sql` 源码与最终持久化 JAR 内字节 SHA-256 均为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`；没有新增或修改 migration。Nginx 单/多后端模板、reload 和错误门禁通过，本机缺少预置 `nginx:1.27.2-alpine3.20` 镜像，因此真实 `nginx -t` 按脚本设计跳过，需由目标机部署脚本执行。
- 已再次回顾全部 `.agents/session-log*.md` 近期记录，未发现冲突或会覆盖的并行成果。

### Result

- 最终包已复制到 `/Users/kaka/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip`，同目录 SHA 文件回读校验为 `OK`；固定文件约 423 MiB。
- 本次不新增部署节点，不修改 API、RunEvent/SSE、数据库结构、Flyway 文件、环境配置、generated SDK 或只读 OpenCode 源码；尚未在企业目标机部署。

## 2026-08-26 - 确认上一企业包已部署并切换为纯平台增量

### Why

- 用户补充确认本轮 423 MiB 候选之前的上一轮外层包已在 `.4/.114/.2` 全部部署并完成验证；当前成功部署基线是 `release@7152a4340`、内层 SHA-256 `8bf9e87214a64989fc64244eafee5767d906eb4d3548404e4855dad783f57f34`、外层 SHA-256 `6d39d994432a15411c127ce8d3488766bd4ff80d6d0c5323ee4b8d1b1ec062f9`。
- 因错误按“上一候选尚未部署”保留客户端而生成的 423 MiB 外层候选 `b627e617...` 没有成为现场基线，现明确作废并由本轮纯平台增量包替代。
- 客户端 `20260826095116`、worker runtime 和 toolbox 已随上一轮完成安装与门禁登记，本轮不能再次携带其大文件或触发重装、重启。

### What

- 保持当前本地 `release` 业务代码不变，不拉取远端；重新按现场成功基线计算组件计划。
- 组件计划确认 worker runtime `reuse`（`877cea18...be33`）、toolbox `reuse`（`35447da0...5040`）、local client `reuse`（`1bc1bb3b...d4c6`）；客户端版本和四项摘要只保留在发布清单中用于目标机校验，不再进入内层 ZIP。
- `.4` Qwen 优先灰度、`.114` 既有模型快照、manager、CK、Mem0、BGE、pgvector、Workflow/LobeHub 关闭状态均保持不动。

### How

- 使用与上一轮一致的客户端版本、域名、组织公钥、JDK/OpenCode 摘要和公共能力 commit 执行 `--component-plan-only`，三个大组件均稳定判定为 `reuse`。
- 已回顾全部 `.agents/session-log*.md` 近期记录，未发现冲突或需要纳入的其它未提交成果；后续将正式重建前后台基本发布单元并重建内外层 ZIP。

### Result

- 现场部署基线已更正；正在生成不含客户端、worker 和 toolbox 大制品的纯平台增量包，最终哈希和体积将在封包校验后补记。

## 2026-08-26 - 完成纯平台增量企业包

### Why

- 上一轮企业包已在 `.4/.114/.2` 部署并验证，本轮只需交付成功基线之后变化的前后台代码；客户端、worker runtime、toolbox、manager 和独立中间件不得重复打包或重启。
- 先前因部署基线判断错误生成的 423 MiB 候选 `b627e617...` 已明确作废，不能再用于现场部署。

### What

- 以本地 `release@e279035d1` 为封包输入，业务代码基线仍为 `eabbc26cb`；正式重建后端、前端和双后端三节点外层包。
- 组件清单保持 worker runtime `reuse`（`877cea1827a6f55b994f4a82f0934d72ca1361fb12b9872ce77e45430073be33`）、toolbox `reuse`（`35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`）、local client `reuse`（`1bc1bb3bd8ef72691292a7c9f5931e003b4b67196aa78702ca4d39e63f24d4c6`）；客户端版本继续为 `20260826095116`，仅在清单中保留摘要，不含客户端文件。
- LobeHub 和 memory 保持 `disabled`；`.4/.114` 的 models、worker/manager、CK、Mem0、BGE、pgvector 及独立 `testagent-updated.7z` 配置包均不在本轮平台包更新范围。

### How

- JDK 25 后端正式构建、持久化/XXL/ClickHouse migration 资源校验、VitePress 用户手册构建、`vue-tsc` 和前端生产 Vite 构建均通过；封包完成后再次校验外层、内层和三节点归档 checksum 及压缩完整性。
- 最终内层发布 ZIP SHA-256 为 `68dcd49397b77213a02e95dc30d08aecd59acf15bed5ab847b51f4ce34c1ba68`；外层完整包 SHA-256 为 `7cb038210e46d33a92b3e20c98c5591b930a7d9f6d980a127464fae26ca64618`。
- `.4`、`.114`、`.2` 节点归档 SHA-256 依次为 `91f17102e6d65830e8c23a659dab3386715ca311e17722774ecfaee0ccf9ed9c`、`3c8d0cdbc81a39f89e270a1d6b2c1c3495090be0314b2cf97c0496483e61f8e1`、`d4c6288c831286e3e4353692179181cc627ff43477c6b235830d28ccd1c90772`；三个节点包只含配置和部署脚本。
- 内层 ZIP 只含本轮前端归档、后端 JAR/lib 和部署资料，已确认不存在 `dist/local-opencode-client/`、worker programs、LobeHub、memory 或 trace 运行时载荷；节点配置中的 TCDS 为 `http://tcds-prod.sdc.icbc:9080`、AAM 为 `http://zfw.sdc.cs.icbc`，两个后端 SkillHub access key 均非空且未输出密钥值。
- 本轮没有新增或修改 Flyway migration。已部署的 `V20260825091459__local_client_instance_replacements_create.sql` 源码与最终持久化 JAR 内字节 SHA-256 均为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`；现场只核验已有成功历史记录和 checksum `749555545`，不得 `repair`、重跑或改历史表。
- 交付文件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip`，同目录 SHA 文件回读和 `unzip -tq` 均通过，体积约 148 MiB。提交前再次回顾全部 `.agents/session-log*.md` 近期记录，未发现冲突或会覆盖的并行成果。

### Result

- 纯平台增量包已生成并完成本机运行构建与归档校验；目标企业服务器尚未执行本轮部署。
- 本次不新增部署节点，不修改 API、RunEvent/SSE、数据库结构、Flyway 文件、环境配置、generated SDK 或只读 OpenCode 源码；本条记录提交本身不再触发重新封包，因此记录提交不会出现在上述 ZIP 内。

## 2026-08-26 - 同步 release 用户手册的删除恢复说明

### Why

- `release` 已交付已跟踪 Agent/Skill 删除后的“取消删除”恢复能力；专题与 FAQ 已随功能提交更新，但本周功能汇总和功能总览尚未覆盖该用户可见入口。

### What

- 在本周“每周新功能”增加删除恢复场景、权限与 Git 跟踪前提、准确操作步骤和多文件/冲突边界，并在功能总览补充恢复并清除 Diff 的行为。
- 增加 Help Center 回归断言，锁定周报与总览中的“取消删除”说明。

### How

- 在 `release@f2b2a10fa00c72e612e93919ccdf440e3a600110` 完成只读门禁，确认没有冲突或用户手册范围外未提交改动；回顾全部 `.agents/session-log*.md` 近期记录后修改。
- `corepack pnpm test -- apps/agent-web/tests/help-center.test.ts` 通过（157 文件、2251 passed、1 skipped）；VitePress build、agent-web typecheck 和 `git diff --check` 通过。

### Result

- 用户可从本周汇总、功能总览、Agent/Skill 专题和 FAQ 获得一致的删除恢复说明。复用的配置树截图仅示意入口；仍需人工补拍“提交并推送 Agent 文档 → 取消删除”弹框的脱敏真实截图。
- 本次仅更新用户手册与帮助中心测试，不涉及 API、事件、数据库、性能、安全、兼容性、部署、环境配置、generated SDK 或 OpenCode 只读源码。

## 2026-08-26 - 将应用运行态更新拆为独立页签

### Why

- 超管公共 Agent 配置页内嵌“应用 Agent / Tool 运行态更新”后，应用发布历史会持续占用页面空间，无法把整个应用区域收起，也混淆了公共 Agent 发布、用户进程 dispose / 重启与应用 Git 刷新的职责。

### What

- 在超管配置菜单中把“应用运行态更新”拆为独立页签，并固定放在“TestAgent公共配置管理”和“应用 Git 刷新”之间；应用管理员仍只显示原有应用 Git 页签。
- 复用既有公共配置组件、应用 rollout 查询、服务器/用户进程折叠明细及停止/重启操作，通过 `public`、`application-runtime` 两种视图隔离渲染、首次加载和活动 rollout 轮询，不新增接口或重复业务组件。
- 同步前端总览、agent-web README、源码包说明、模块图和前端交互规范，并补充页签顺序、视图隔离、应用历史与空状态回归。

### How

- 在 `frontend` 执行 `corepack pnpm test -- apps/agent-web/tests/scheduler-management-panel.test.ts`，实际全量 157 个测试文件通过，2252 passed / 1 skipped；agent-web `typecheck` 和 production build 通过，`git diff --check` 通过。
- 按 `.env.test` / `test` profile 执行 `./restart-dev-services.sh --profile test --env-file .env.test`，26 模块后端 package 和前端生产构建成功；依赖启动阶段仍卡在 Docker Desktop 创建 ClickHouse 容器，容器停在 `Created`，脚本在停止旧服务前中止。随后用项目既有参数单独重启 agent-web，Vite 在 `127.0.0.1:3000` 就绪并返回 200；原后端 health/readiness 保持 `UP`，登录 CORS 预检为 200。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，确认本次修改承接既有应用运行态命名、业务标识与折叠交互，没有覆盖其它开发者成果或残留合并标记。

### Result

- 公共 Agent 页只加载和展示公共配置发布；应用运行态页只在进入时加载应用范围与 rollout，并仅在该视图存在活动任务时轮询。管理员可在独立页面查看发布历史并 dispose / 重启全员，不再挤占公共配置区域。
- 本次不新增部署节点，不修改 HTTP API、RunEvent/SSE、数据库、SQL、Flyway、环境配置、generated SDK 或只读 OpenCode 源码；相关前端已实际重启验证，整栈切换仍受本机 Docker ClickHouse 启动卡住影响。

## 2026-08-26 - 增加版本库检索并优化应用管理加载

### Why

- 版本库管理原先只读取前 100 条后在页面展示，缺少面向完整结果集的检索能力；应用管理每次进入都会先请求全量应用定义，即使工作台已经明确选中当前应用。

### What

- 配置管理版本库分页 API 增加可选 `keyword`，通过既有领域仓储、应用服务和 MyBatis XML 按版本库 ID、中文名、英文名、Git 地址做大小写不敏感 LIKE，并返回过滤后的总数；空关键字保持旧分页语义。
- 版本库管理增加服务端检索输入、回车/清空/按钮操作、结果计数和空状态；检索只刷新列表，不重复读取类型字典和部署选项。
- 设置链路把工作台当前应用定义透传到应用管理，首次进入直接选择并加载该应用上下文；仅在没有当前应用或用户展开选择器时读取全量应用，另用独立加载锁覆盖快速展开竞态。
- 同步 HTTP API、模块图、后端三个模块 README、前端两个包说明、agent-web README 和内置用户手册。

### How

- Java 25 定向 Maven 测试通过：配置应用服务 33 项、Controller 18 项、MyBatis 集成 6 项；增强后的集成测试分别覆盖四个检索字段、大小写与过滤总数。
- 前端三个定向文件 160/160 通过；全量 Vitest 157/157 文件通过，2255 passed / 1 skipped；`backend-api` 与 `agent-web` 类型检查通过。
- `./restart-dev-services.sh --profile test --env-file .env.test` 完成后端 26 模块 package、VitePress 和 agent-web production build；依赖启动阶段仍被本机 Docker Desktop 的 ClickHouse start API 卡住，容器保持 `Created`，脚本在停止旧服务前中止并清理本轮 CLI 进程。原 8080 health/readiness 与 3000 页面仍返回 200，但属于重启前进程，不能作为新代码运行验收。
- `git diff --check`、改动文件冲突标记扫描和 `tools/verify-ai-docs.sh` 通过；提交前已回顾全部 `.agents/session-log*.md` 近期记录，未发现覆盖并行成果的冲突。

### Result

- 代码、文档、自动化测试和生产构建均完成；真实整栈切换仍受本机 Docker/ClickHouse 启动阻塞，因此运行验收为部分验证。
- 本次使用 `release`，不新增部署节点；API 仅增加可选查询参数，旧客户端兼容。未新增事件、数据库结构、Flyway、环境配置、generated SDK 或 OpenCode 源码；关系型查询继续只落 MyBatis XML。应用管理减少一次常规全量应用请求，权限与敏感信息边界不变。

## 2026-08-26 - 修复工作区 Markdown 本地图片上传与预览

### Why

- 浏览器只选择一个本机 Markdown 时不能自动扫描其同目录图片，现有预览又会把 `./图片.png` 请求到前端站点，导致工作区文档中的本地图片无法展示。
- 用户需要一次多选 Markdown 与图片，且文档引用 4 张但实际只上传 3 张时仍正常上传和预览已有图片，缺失图片不能阻断。

### What

- 工作区新建/上传面板保留多文件选择，并新增目录选择器；目录文件按 `webkitRelativePath` 保留内部层级，普通多选可按 Markdown 唯一图片 basename 对齐引用子目录。
- 新增 Markdown token 图片引用扫描和上传计划；缺失图片只进入非阻断提示，已选择文件继续通过既有文件 WebSocket 分片 RPC 上传。
- Markdown 相对图片不再请求前端静态站点，由工作台通过普通/引用视图二进制分片 RPC 读取为可回收 object URL；读取失败显示“图片未上传”占位，正文和其它图片保持可用。
- 同步 editor、file-explorer、agent-web、前端规范和用户手册；补充“4 张引用只选 3 张”、目录层级、中文相对路径、外链与缺图占位回归。

### How

- 定向 Vitest 4 个文件共 54 项全部通过；editor、file-explorer、agent-web 三个包的 `vue-tsc` 类型检查通过；agent-web production build（含 VitePress）通过，`git diff --check` 通过。
- 启动当前代码的 agent-web，因既有 3000 端口被占用自动运行在 `http://127.0.0.1:3001/`，页面入口返回完整 HTML。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，确认本次前端文件上传与预览改动不覆盖已提交的应用管理、运行态或企业包成果。

### Result

- 用户可一次多选 `.md` 和现有图片，或选择整个目录；少传图片不会失败，已上传图片正常显示，缺失项可稍后补传。
- 本次使用 `release`，不新增部署节点，不修改 HTTP API、RunEvent/SSE、数据库、Flyway、环境配置、generated SDK 或只读 OpenCode 源码；文件读取与上传继续遵守既有 WebSocket route/ticket/RPC 边界。

## 2026-08-26 - 工作区 Markdown 本地图片端到端验证

### Why

- 用户要求对 Markdown 本地图片修复进行真实端到端验证，重点覆盖一次多选 `.md` 与图片、文档引用 4 张但只上传 3 张时不阻断的行为。

### What

- 在 `.env.test` / `test` profile 环境中使用账号 `usr_test_dev` 已绑定的本机个人 worktree，通过真实 Chromium 页面一次选择 1 个 Markdown 和 3 个 SVG；测试 Markdown 声明 4 张相对图片，第四张故意不提供。
- 打开上传后的 Markdown 并切到整体预览，核对三张已上传图片和一项缺失占位；随后从文件树执行“撤销上一步”，清理本次上传的全部文件。
- 对照用户提供的 `SLB策略发布重构总体方案.md`，确认其包含 4 个相对 PNG 引用，而附件所在目录当前没有同级或三级内图片文件。

### How

- 后端 health/readiness、前端 3000 页面和 CORS 预检均返回 200；重启脚本完成 26 模块后端 package、用户手册和 agent-web production build，但可选 ClickHouse 的 Docker CLI 仍卡住，本轮仅终止精确的 CLI/脚本进程，已启动后端继续以 JDK 25 和 `test` profile 运行。
- Playwright 使用本机 Chrome 执行真实 UI：提示精确为“已上传 4 个文件，缺少 1 张图片”；三张图片均以 `blob:http://localhost:3000/...` 加载完成，尺寸为 320×120；第四张显示“图片未上传：./e2e-four.svg”。
- 图片文件未产生相对 HTTP 请求，浏览器 request failure、console error 和 page error 均为 0；最终通过 UI 撤销确认个人 worktree 中测试文件为 0，临时 session/workspace 数据库记录为 0，仓库原有工作区未残留测试改动。

### Result

- 多文件选择、缺图非阻断、工作区二进制读取、object URL 预览、缺图占位和 UI 清理链路均通过真实端到端验证；截图保留在 `/tmp/test-agent/markdown-image-e2e/artifacts/03-markdown-preview.png`。
- 本轮只补充验证记录，不修改业务代码、API、事件、数据库结构、Flyway、环境配置、generated SDK 或 OpenCode 源码；Docker Desktop 的 ClickHouse 启动卡顿仍是本机可选分析服务风险，不影响本次工作区文件链路。

## 2026-08-26 - 版本库检索与应用管理端到端验证

### Why

- 用户要求对 `aa7b3562b` 的版本库检索和应用管理加载优化执行真实端到端验证，并确认应用选择器仍可按需读取全量应用且没有影响其它功能。

### What

- 在 detached 临时 worktree 中固定构建并运行 `aa7b3562b`，避免 `release` 后续 Markdown 图片提交和主工作区状态影响验证结论；后端使用该提交构建的不可变 JAR、JDK 25、`.env.test` 与 `test` profile。
- 真实 Chromium 中确认工作台当前应用为 F-COSS：首次打开和再次打开应用管理都默认显示 F-COSS、只读取该应用 3 类上下文且全量应用请求均为 0；主动展开选择器时只发起 1 次全量请求，并展示 F-COSS、F-WRAPP 两项。
- 版本库管理从 11 条完整列表检索“自动”得到 4 条、用大小写转换后的英文关键字 `WRHEMER` 得到 1 条，清空后恢复 11 条；同时验证编辑弹窗开关和应用人员、关联、工作空间三个页签。
- 实际 API 另覆盖版本库 ID、中文名、英文名大小写、Git 地址和空关键字，过滤总数与全量数据本地复算一致。

### How

- 精确提交的 agent-web production build 成功；全量 Vitest 157 个文件通过，2255 passed / 1 skipped。
- Java 25 Maven reactor `BUILD SUCCESS`：配置应用服务 33 项、Controller 18 项、MyBatis 集成 6 项全部通过。
- 真实浏览器目标链路的 page error、console error、request failure 和 HTTP 4xx/5xx 均为 0；截图保存在 `.tmp/e2e-validation-aa7b3562b/`。
- 设置 Mock E2E 5 项中 4 项通过；“无角色用户看不到应用管理”这一旧断言在目标提交及其父提交 `e0d8d474c` 上均同样失败，且两版本 `SettingsMenu` 一致，因此判定为存量测试与现有产品行为失配，不是本次回归。

### Result

- 版本库检索、当前应用默认选择、进入时避免全量加载、展开后仍可全量选择及相邻设置操作均通过真实端到端验证，目标修改未发现功能回归。
- Docker Desktop 的 ClickHouse start API 仍卡住且容器保持 `Created`；为完成目标业务 E2E，仅在本次运行进程中临时关闭分析模块，backend health/readiness 为 `UP`。因此目标功能已验证，包含 ClickHouse 分析能力的完整整栈启动仍属于未验证限制。
- 本轮只新增验证记录，不修改业务代码、API、事件、数据库、Flyway、环境文件、generated SDK 或只读 OpenCode 源码。

## 2026-08-26 - 基于当前 release 重建企业平台增量包

### Why

- 用户要求基于当前本地代码重新打包；上一份 148 MiB 纯平台包只完成构建、尚未确认为新的现场部署基线，因此本轮继续以最后成功部署的 `release@7152a4340` 为更新点和 Flyway 比较基线。
- 当前 `release` 已新增版本库检索、应用管理按当前应用加载、应用运行态独立页签和工作区 Markdown 本地图片上传/预览，必须正式重建前后端，不能只重封历史二进制。

### What

- 以干净工作树 `release@9d46ac7a60f68e4793b1cf9a11978ffc31b0deb7` 为源码输入，不拉取、合并或切换分支；重建后端、用户手册、前端、内层发布 ZIP 和固定名双后台外层包。
- 客户端 `20260826095116`、worker runtime、toolbox 的输入均未变化，组件指纹分别继续为 `1bc1bb3bd8ef72691292a7c9f5931e003b4b67196aa78702ca4d39e63f24d4c6`、`877cea1827a6f55b994f4a82f0934d72ca1361fb12b9872ce77e45430073be33`、`35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`，全部按 `reuse` 交付；LobeHub/memory 保持 `disabled`。
- `.4/.114` 的 models、manager/worker、CK、Mem0、BGE、pgvector、独立案例配置包和 trace 运行时载荷均不在本轮更新范围。

### How

- JDK 25 正式发布入口完成 Spring Bean 构造器门禁、后端 Maven 封装、VitePress、`vue-tsc`、前端生产 Vite 构建和全部持久化/XXL/ClickHouse migration 资源校验；当前功能提交此前已完成全量 Vitest、定向 Maven 与真实浏览器端到端验证。
- 最终内层 ZIP SHA-256 为 `e8fd3d1b22f20df03574c3a3e62a2cc74f5cff00b0f280190e090dadce91bd64`；外层固定包 SHA-256 为 `1a3e0ed14d2ab79229d045db93920fe418d00a68b2707113c07765a8a378fe12`，外层内嵌内层与独立内层逐字节一致。
- `.4`、`.114`、`.2` 节点归档 SHA-256 依次为 `8c5f18848f4e7549b755cca0f48101454c677c78946fdf3ddb2f832b03adf6a2`、`7fa88d9936e1bbdeb66bdde8bcd6059c2482d8d85e373521cdfca0f507e5f873`、`bd6615f64089260aa973724f0efa2b3e2d3b7169d414a7e9a6d42d691cf6547b`；所有 checksum 和 ZIP CRC 均通过。
- 后端应用 JAR、persistence JAR、XXL integration JAR 和前端归档 SHA-256 分别为 `8598c25a43adafa991085f717f9e0baedaaa3edbbea426eff83a1e713ff59d76`、`3e5817160f4b0482c4e1ddce5b5d9af3c58534dbd6547b2ef186a3ff6ca11089`、`b4a6fc090b026a439b42d4eb41aed3717e1c6883ce3acd027302cb376c9c6a14`、`dad06ad0e6d741e8c990e883e3be97e121da632d77cb0bd1216427bdbb6435e7`。
- 内层清单确认不含 `dist/local-opencode-client/`、worker programs、toolbox 镜像、LobeHub、memory 或 trace 运行时归档；节点配置的 TCDS 为 `http://tcds-prod.sdc.icbc:9080`、AAM 为 `http://zfw.sdc.cs.icbc`。两个后台的 SkillHub access key 均使用“从目标机已安装 `backend.env` 继承”的标记，包内不携带或回显真实 key；部署入口会在覆盖配置前校验唯一性和长度。
- 客户端既有分发重新通过版本、manifest、签名、安装脚本和用户包校验；组织私钥有效且与固定公钥 DER SHA-256 `6d294535e5bf4c2a0ea2ba3ae8b1fc9502444de7639df3607d5e360846c9ca62` 匹配，私钥仍只在 Git 忽略目录中。
- 相对成功部署基线没有新增、删除或修改任何 PostgreSQL、XXL 或 ClickHouse migration。`V20260825091459__local_client_instance_replacements_create.sql` 源码和最终 persistence JAR 内资源 SHA-256 均为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`；现场只验证已有 `checksum=749555545/success=true` 记录和 history 总数不变。
- 提交前再次回顾全部 `.agents/session-log*.md` 近期记录，未发现冲突、残留合并标记或会覆盖的并行成果。

### Result

- 固定交付件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip` 和同名 `.sha256`，回读校验为 `OK`，体积约 148 MiB。
- 本机构建和归档验证完成，企业 `.4 → .114 → .2` 尚未执行本轮部署。包不新增部署节点，不新增数据库/Flyway，不修改环境文件、generated SDK 或只读 OpenCode 源码；本条追溯记录提交本身不再进入上述 ZIP。

## 2026-08-27 - 同步 release 用户手册并修复上传事件测试

### Why

- 上次用户手册同步后，`release` 已交付版本库关键字检索与当前应用优先加载、工作区 Markdown 图片上传/预览，以及超级管理员独立“应用运行态更新”页签，周更、总览和 FAQ 尚未完整对齐。
- `DirectoryRows` 上传测试仍按旧的单参数 `requestUpload(directory)` 断言，实际组件契约已扩展为 `requestUpload(directory, mode)`，导致自动化门禁失败。

### What

- 更新内置用户手册的功能总览、设置专题、每周新功能和 FAQ，补齐适用角色、前置条件、准确入口、操作边界与人工补拍截图清单；未生成或伪造截图。
- 增加帮助中心回归断言，并压缩周更新增说明，确保 7,000 字符的宠物问答上下文继续包含既有客户端、记忆等关键内容。
- 复用现有上传事件契约，只把 `DirectoryRows` 测试期望更新为 `['docs', 'files']`，不修改组件实现或新增并行上传路径。

### How

- 定向 Vitest：`DirectoryRows.test.ts` 27/27、`help-center.test.ts` 17/17 通过；全量前端 Vitest 158 个文件通过，2264 passed / 1 skipped。
- user-manual VitePress build、agent-web typecheck、图片引用与替代文字检查、手册正文娱乐内容门禁、工作区与暂存区 `git diff --check` 均通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录，确认只暂存本次手册、帮助中心/文件树测试和本日志，不夹带其它成果。

### Result

- 用户手册已覆盖三项最新 release 能力，帮助中心上下文保持有界且可回答既有周更问题；上传测试与当前双参数事件契约一致。
- 本次不新增部署节点，不修改产品代码、API、事件、数据库、Flyway、性能、安全、环境配置、generated SDK 或只读 OpenCode 源码；缺少的三类新流程真实截图已明确留作人工补拍。

## 2026-08-27 - 固化企业离线环境的网络取证边界

### Why

- 企业现场排障容易把开发者本机、VPN 或浏览器访问成功外推为企业服务器链路可达，同时给完全离线的企业内网建议公网下载工具或依赖，导致结论与实际故障路径不一致。

### What

- 更新 `enterprise-troubleshooting` 技能：企业环境默认按无互联网处理，可达性必须从实际故障发起端、对应企业节点、容器或等价网络命名空间验证；开发者本机成功不再作为企业侧网络证据。
- 明确禁止把 GitHub、公共依赖仓库、CDN、在线安装或升级作为现场排障步骤；运行时公网依赖应记录为离线交付缺口、错误配置或未内置资源。
- 新增两条行为评测样例，分别覆盖“本机 `curl` 成功但企业 worker 链路未知”和“企业离线环境缺工具时要求在线安装”的误判场景。

### How

- 复用现有企业技能的 DBeaver 只读定位、服务器归属和时间窗日志流程，只补充网络证据来源、离线资源边界以及交付结论字段，没有新建平行排障技能。
- `quick_validate.py`、eval JSON 解析和 `git diff --check` 均通过；提交前已回顾全部 `.agents/session-log*.md` 近期记录，未发现与本次技能文件冲突的成果。

### Result

- 后续企业排障会明确区分“本机可达”和“企业真实调用路径可达”，无法进入实际发起端时必须保留“企业侧链路尚未验证”的结论。
- 本次只修改项目内技能、评测样例和会话记录，不涉及产品代码、API、事件、数据库、Flyway、性能、安全协议、环境配置、generated SDK 或只读 OpenCode 源码；无需启动业务服务。

## 2026-08-27 - 修复企业离线客户端能力目录启动超时

### Why

- 企业用户第二次输入 Client key 已接入成功，但客户端在公共能力激活验证时持续等待 `/experimental/tool/ids` 约 30 秒后回滚并退出，导致 user systemd 反复拉起，托盘和网页连接都没有机会初始化。
- OpenCode 1.18.4 会同时扫描用户全局、旧配置和受管配置目录；目录缺少 `node_modules` 时会启动后台 npm 依赖检查。企业现场不能访问互联网，公网 registry 超时会阻塞插件与 Tool 目录加载。

### What

- 在既有 `OpencodeProcessSupervisor` 受管启动链路强制设置 `npm_config_offline=true`，并清除父进程可能遗留的大小写变体相反值；继续固定 `OPENCODE_DISABLE_MODELS_FETCH=true`。
- 保留进程 health、`/experimental/tool/ids`、`/agent`、`/command` 验收和失败回滚，不跳过真正缺失的公共能力包依赖。
- 补充监管器回归测试，并同步本地客户端模块说明、架构和企业逐机交付文档。

### How

- `mvn -pl test-agent-local-client -am test` 通过：依赖链 224 项测试通过，1 项需要显式真实 OpenCode 可执行文件的既有测试跳过。
- `mvn -pl test-agent-local-client -am -DskipTests package`、正式 shaded JAR `--version` 和字节码调用检查通过。
- `deploy/internal/tests/local-opencode-client-package-test.sh` 通过，覆盖麒麟 ARM64 用户包、不可变签名 release、catalog、安装和制品缓存契约；`git diff --check` 通过。
- 复用已验证的 JDK/OpenCode 1.18.4/提交 `81605f...` 完整能力包离线输入，生成签名 release `20260827142656`；逐项 RSA 验签、分发校验和 JAR 版本/字节码检查通过。U 盘转运包写入 `/Users/kaka/Desktop/mimoagent/0709/`，SHA-256 为 `06b341c908fd3ec743a90f0d287dc0dbfeac15564aef89b2c3a57ad7f9ad658a`。

### Result

- 受管 OpenCode 的后台依赖检查只能使用本机缓存或签名能力包，非受管目录依赖缺失会快速失败，不再因公网连接超时阻塞 Tool/插件目录。
- 新的完整签名客户端 release 和两个 U 盘转运文件已生成；企业 `.2` 发布、真实麒麟 ARM64 用户覆盖安装及托盘/WSS/网页在线验证仍是现场发布闸门。
- 本次使用 `release`，不新增部署节点，不修改 API、事件、数据库、Flyway、环境文件、generated SDK 或只读 OpenCode 源码。

## 2026-08-27 - 修复小地球应用检索选中失效

### Why

- TCDS 需求导入页的应用建议筛选已大小写不敏感，但输入完成时仍按大小写严格解析；点击候选还会先触发输入框失焦并卸载下拉项，导致小写或模糊检索后无法选中，只有严格输入大写全称才会生效。

### What

- 复用既有应用目录和筛选文本，将应用全称、简称、完整标签的精确解析改为大小写不敏感，并只在唯一模糊命中时自动规范为目录中的应用简称；多条候选继续要求用户明确选择。
- 候选按钮在 `mousedown` 阶段阻止输入框先失焦，保证真实浏览器的后续 click 能进入既有 `selectApplication` 链路；同步 agent-web README 和需求导入页面回归测试。

### How

- 定向 Vitest 16/16、agent-web typecheck、前端 production build 和 JDK 25 后端 26 模块跳过测试打包均通过。
- Playwright 真实浏览器验证 `f-bat` 点击候选回填 `批处理应用（F-BATCH）` 并发送 `appShortName=F-BATCH`，`f-base` 按 Enter 回填并发送 `F-BASE`；运行中 Vite 的转换源码已包含本次逻辑，前后端健康检查均为 200。
- 根重启脚本在本机 ClickHouse `docker run` 阶段再次阻塞，按既有风险只终止本轮精确脚本/CLI 进程，未停止原有后端和前端；未修改 `.env.test` 或切换数据库。

### Result

- 小地球应用检索支持小写精确输入、唯一模糊输入和模糊检索后点击候选，并始终向后端传递目录中的规范应用简称；任意目录外非空值兼容行为保留。
- 本次仅修改前端交互、测试与稳定说明，不涉及 API、RunEvent/SSE、数据库、Flyway、部署节点、性能、安全、generated SDK 或 OpenCode 只读源码。

## 2026-08-27 - 增强 XXL 任务详情日志和完成备注

### Why

- 企业批量/定时任务只能从 XXL 列表看到笼统成功或失败，无法直接判断任务名称、运行标识、是否真正进入业务处理、耗时和低敏业务结果；全局锁竞争跳过还容易被误解为业务处理成功。

### What

- 统一 XXL handler 为每轮生成并贯穿 `taskRunId/traceId`，记录任务名称、任务 key、并发策略、锁处理、开始/结束时间、耗时、`processed` 和 `ScheduledTaskResult` 低敏聚合结果。
- `SKIPPED_LOCK_HELD` 明确输出 `processed=false` 和“未执行（全局锁被其他节点持有）”；失败只记录稳定错误码和安全说明，原始 XXL 参数、凭据及第三方异常 message 不进入日志。
- 完成备注对可变内容执行 HTML 转义并限制长度；同步 scheduler/XXL 模块说明、架构和测试文档，新增真实 XXL Core 上下文日志测试。

### How

- JDK 25 定向运行 adapter、统一 handler、Spring 装配、任务注册表和保留期任务测试：scheduler 3 项、XXL integration 10 项全部通过，Maven reactor 为 `BUILD SUCCESS`。
- 尝试运行 `mvn -pl test-agent-xxl-job-integration -am -DskipITs test`：前置 common/domain/observability/scheduler 测试分别通过 110、111、6、8 项；随后 Testcontainers/Ryuk 启动一直停在 Docker `Created` 状态，终止本次精确 Maven 进程，因此依赖容器的完整套件没有结论。
- 按本地启动规范使用 JDK 25 和根目录 `.env.test` 执行 `./restart-dev-services.sh --profile test --env-file .env.test`：后端 26 个 Maven 模块打包、VitePress、`vue-tsc` 和 Vite 生产构建均通过；托管 ClickHouse 同样停在 Docker `Created` 状态，已仅终止本次启动进程。原有 8080/3000 服务健康，但未加载本次新 JAR，故新代码运行态尚未验证。
- `git diff --check` 通过；提交前回顾全部 `.agents/session-log*.md` 近期记录，未发现与 scheduler/XXL 当前改动冲突或残留合并标记。

### Result

- XXL 管理页能够区分业务成功、全局锁跳过和失败，并以 traceId 关联平台日志；聚合结果和完成备注保持低敏、定长和 HTML 安全。
- 本次使用 `release`，不新增部署节点，不修改 HTTP API、RunEvent/SSE、数据库、SQL、Flyway、环境配置、generated SDK 或只读 OpenCode 源码。代码、定向测试和前后端构建已验证；新产物运行态及依赖 Docker 的完整 XXL integration 套件仍需在 Docker 正常环境补验。

## 2026-08-27 - 记录当前 release 企业增量包

### Why

- 用户确认上一轮企业包已经完整部署并验收，要求只基于当前本地 `release` 重新生成增量企业包；本轮实际新增客户端离线启动修复、TCDS 应用选择修复和 XXL 任务详情日志，不能重复纳入未变更的 worker、toolbox、ClickHouse、memory 或 trace 运行制品。
- 已部署 PostgreSQL 基线包含 `V20260825091459__local_client_instance_replacements_create.sql`，本轮没有新增或修改 Flyway，必须保持源码、最终 persistence JAR 与企业已执行版本字节一致。

### What

- 以提交 `c3118d6db871c961f162f3fba00dff769d60c1f8` 为产品代码输入，生成客户端不可变版本 `20260827150207`，并复用已验签的 JDK、OpenCode 1.18.4 和公共能力包离线输入；客户端分发地址固定为 `http://mimo.sdc.cs.icbc:9996`。
- release component manifest 固定 worker 为 `reuse`（指纹 `877cea1827a6f55b994f4a82f0934d72ca1361fb12b9872ce77e45430073be33`）、toolbox 为 `reuse`（指纹 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040`），LobeHub/memory 为 `disabled`，客户端为 `included`；因此部署脚本只校验存量 worker/toolbox 指纹，不替换或重启 manager/worker。
- 生成三节点外层包 `test-agent-two-backend-complete.zip`，保留 `.4` 和 `.114` 的 `TEST_AGENT_SKILLHUB_ACCESS_KEY=__PRESERVE_FROM_INSTALLED_BACKEND_ENV__`，TCDS 固定为 `http://tcds-prod.sdc.icbc:9080`，AAM 固定为 `http://zfw.sdc.cs.icbc`。

### How

- 首次客户端构建仅配置了规范化 JDK SHA，脚本因未指定本地归档而校验了上游原始包并失败；改为显式传入上一已验签 release 的本地 `jdk.tar.gz` 和 `opencode.tar.gz`，分别复核 SHA-256 `9c03294370119d0703e6c3b4fcbcfb42b147f997aaba769ad5839c6a5c8441a2`、`4d33b499b4b78971d1ea86c24624500379b1d1ed8308647087f820bdb38e13da` 后正式构建，没有放宽校验或修改代码。
- `package-release.sh` 完成后端 26 模块、VitePress、`vue-tsc`、Vite production build、客户端用户包和内层 ZIP 构建；客户端分发脚本验证通过，catalog、manifest、client JAR、JDK、OpenCode 和公共能力包六项 RSA 签名逐项为 `Verified OK`。
- 新建独立临时目录解压外层和内层 ZIP，逐项验证 ZIP CRC、内层 `cmp`、三个节点归档 SHA、组件 manifest、节点域名和密钥保留标记；禁带清单未发现 worker、programs、toolbox、ClickHouse、memory 或 trace 运行归档。
- persistence JAR 内 `V20260825091459...` 与源码 `cmp` 一致，SHA-256 均为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`；应用 JAR 包含 `BOOT-INF/classes/rsa-private.key`。XXL 非容器定向测试中 scheduler 3 项、XXL integration 10 项全部通过；依赖 Docker/Testcontainers 的完整套件仍停在 Ryuk `Created`，本轮没有把它误报为通过。

### Result

- 最终外层包 SHA-256 为 `003718cdd9c10b388ce3481fb2d8488f41767e31fa2316d7b1dadfd4d10f51e6`，内层包为 `3111564b61a4cf4aed40102723048621b203192cc3ae1af95382458699265285`；`.4`、`.114`、`.2` 节点包依次为 `c8ccfc709b2fc7a67c1e2df980604682aa62488d891d83063b2d992e37f23f3e`、`5adb3b3bc0fcd45d05c74d70235e4a2e8911ef39f431006469bde78366b0c716`、`06bf7ce6c6f44723750d701d3aa5f493364c041f0b2efe2ec91b3d09b0dce498`。
- 最终应用 JAR、persistence JAR、XXL integration JAR、前端归档 SHA-256 分别为 `c325df59112518e0ac5eede1f0335f6bc223fcbaa283b9895bbac637febba373`、`1e1030b8bc938445db723060fd90eb870a56582ad0ba182ef710fe8944845b40`、`a9cfdedbb59b369e6a9962850264ac7e9fddd2861af96bdd5b811d85b0e2eb9e`、`b6fdbde8f881d8ee0b73ec51e58cdcda047b2cb1f2a8c04679aa1fd07c2b6488`。
- 固定交付件已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/`，回读 SHA 和 ZIP CRC 均通过，体积约 423 MiB。企业 `.4 → .114 → .2` 尚未执行本轮部署；本条发布追溯提交晚于打包输入，不进入上述 ZIP。

## 2026-08-27 - 补齐企业客户端首次接入失败诊断

### Why

- 企业麒麟用户双击客户端并输入统一认证号与 Client key 后进程以 exit code 1 退出；现场 `client.log` 只有两次桌面主题初始化记录，登记短进程的未捕获异常只写入即将关闭的终端，无法区分凭据、入口链路、后端拒绝或协议错误。
- 原登记探针还把所有平台 `ERROR` 都折叠为认证失败，会把版本、实例归属和后端内部错误误导为 Key 问题；企业内网不能通过反复索取 Key 或临时联网安装工具绕过取证。

### What

- `LocalClientMain` 顶层统一捕获命令异常，由 `LocalClientFailureReporter` 将固定命令、错误类别、稳定错误码和根异常类型写入持久 `client.log`，终端同步显示中文行动建议和日志绝对路径；异常消息、服务端正文/details、统一认证号和 Client key 均不记录。
- 首次登记分别归类 `AUTHENTICATION_REJECTED`、`PLATFORM_CONNECTION_FAILED`、`PLATFORM_REJECTED` 和 `PLATFORM_PROTOCOL_INVALID`；`UNAUTHENTICATED/RATE_LIMITED` 继续统一，不泄露 Key 是否存在，非认证平台错误只保留受限格式的稳定 code。
- 同步客户端模块说明和企业逐机交付文档，明确从真实用户机验证入口链路，并按同一时间窗检查 `.4/.114` 后端日志；不使用开发者本机连通性替代企业证据。

### How

- 本地客户端 Maven reactor 全量测试通过：common 110、protocol 11、local client 107，共 228 项通过、1 项真实 OpenCode 条件跳过；跳过测试打包和正式 shaded JAR `--version` 通过。
- 在隔离配置/状态目录以交互终端连接不可达地址，真实输出平台连接中文提示并生成 `local_client_command_failed ... rootFailureType=ClosedChannelException`；日志未包含假统一认证号/Key，认证失败前未生成凭据文件。
- `deploy/internal/tests/local-opencode-client-package-test.sh` 通过；生成并逐项 RSA 验签客户端 release `20260827165255`，JAR SHA-256 为 `cb174afa9d67da64c9805650b58041f3b612e4e71f7eba0b943cb6618ca36ce3`，用户包为 `4d04c7f5a396be4460eb239bf52c71254406f4c3ad38fb01312e5ea8b11d7bb2`。JDK、OpenCode 和公共能力包与上一已验签 release 逐字节一致。

### Result

- 新的 U 盘转运包已写入 `/Users/kaka/Desktop/mimoagent/0709/test-agent-local-opencode-client_20260827165255_arm64.tar.gz` 及同名 `.sha256`，回读校验通过，外层 SHA-256 为 `2eb0c3b3d919716245bcdb40e0ade49256ff2a43548d7bd1a5986d9adbcf2e46`。
- 代码和候选包已验证，但企业 `.2` 尚未发布该 catalog，真实麒麟用户也尚未用新版复测；现有 16:21/16:24 故障的具体类别仍需从两台企业后端同一时间窗日志确认，不能将可观测性修复表述为已修复现场认证或网络根因。
- 本次使用 `release`，不新增部署节点，不修改 HTTP API、RunEvent/SSE、数据库、Flyway、环境配置、generated SDK 或只读 OpenCode 源码。

## 2026-08-27 - 持久化 SkillMarket 用户提交时间

### Why

- SkillMarket `/list` 已返回 `createTime`，语义是用户提交申请时间；integration 网关也已解析到领域模型，但外部目录全量同步写入 `agent_skill_hub_assets` 时丢弃了该值，统计明细只能错误地使用平台首次同步时间。
- 当前上游和平台表没有独立审批完成时间，不能用 `updateTime`、发布或同步时间伪造。

### What

- 为 `agent_skill_hub_assets` 新增可空 `external_created_at`，MyBatis 全量目录 upsert 写入 `/list.createTime/createdAt`；上游本轮省略字段时保留已同步值，原 `created_at` 继续表示平台首次发现时间。
- 增加 `V20260827183737__agent_skill_hub_assets_add_external_created_at.sql`，不回填历史值、不新增审批字段；同步 persistence、integration、workspace-management README、HTTP API 和数据库部署文档。
- H2 Hub 集成测试同时锁定外部提交时间、本地首次同步时间和缺字段保留行为；既有网关测试继续锁定现场 `createTime` UTC 解析。

### How

- JDK 25 定向运行 `MyBatisAgentSkillHubRepositoryIntegrationTest` 与 `SkillHubHttpGatewayTest`，两组 Maven reactor 均通过；后端 26 模块 `mvn clean package -Dmaven.test.skip=true` 全部构建成功。
- 新 migration 源码、persistence JAR 与最终应用嵌套 persistence JAR 的 SHA-256 均为 `d032d0a50c59a719f056654880424f5525a843ea96512ac7d95c7d4c36027362`。
- 按本地启动规范使用 `.env.test` 启动，后端因该文件当前配置的 PostgreSQL `127.0.0.1:15432` 拒绝连接而退出；未切换数据库、未修改 dotenv，真实 PostgreSQL migration 和 health 未验证。

### Result

- 下一轮 10 分钟 SkillMarket 全量对账可将用户提交时间写入独立列，报表审批完成时间继续明确留空；旧 Java 忽略新增可空列，回滚可保留结构。
- 本次使用 `release`，不新增部署节点，不改变 HTTP DTO、RunEvent/SSE、性能或安全协议，不修改环境文件、generated SDK 或只读 OpenCode 源码。企业 `postgres` 上线前仍需检查完整 `flyway_schema_history` 并完成存量升级和最终包字节校验。

## 2026-08-27 - 记录客户端诊断与 SkillMarket 时间增量企业包

### Why

- 用户要求基于当前本地 `release` 重新打包；相对上一平台包输入 `c3118d6d...`，新增企业客户端首次接入失败诊断和 SkillMarket 用户提交时间持久化，上一轮已部署的 worker、toolbox、ClickHouse、Mem0、BGE、pgvector、manager、模型灰度及 trace 制品不应重复交付。
- 企业 PostgreSQL 当前已部署基线为 `V20260825091459`、Flyway checksum `749555545`，本轮新增 `V20260827183737__agent_skill_hub_assets_add_external_created_at.sql`，必须覆盖真实基线升级、空库迁移和最终 JAR 字节一致性，不能只测 H2 或空库。

### What

- 以产品代码提交 `07b9d451aab5473a4c4886feec5dda6dc3e88991` 为输入生成三节点增量包；后端、前端和客户端纳入，worker/toolbox 固定 `reuse`，LobeHub/memory 固定 `disabled`，CK/Mem0/BGE/pgvector/trace 不纳入，manager Docker 和 `.4` 的 Qwen 模型灰度配置不变。
- `.4/.114` 节点配置继续固定 TCDS `http://tcds-prod.sdc.icbc:9080`、AAM `http://zfw.sdc.cs.icbc`，SkillHub key 使用 `__PRESERVE_FROM_INSTALLED_BACKEND_ENV__` 从已安装配置继承。
- 客户端使用固定组织 RSA 密钥生成不可变版本 `20260827185946`，分发域名保持 `http://mimo.sdc.cs.icbc:9996`；公共能力包仍来自提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`。

### How

- `mvn -pl test-agent-local-client -am test` 通过：common 110、protocol 11、local client 107，共 228 项通过、1 项真实 OpenCode 条件跳过；SkillMarket 网关 6 项、MyBatis repository 8 项定向测试通过。正式打包完成后端 26 模块、VitePress、`vue-tsc` 和 Vite production build。
- 根目录 `.env.test` 当前 PostgreSQL 不可达，未修改 dotenv 或切库；改用一次性 `postgres:16-alpine` 真实 PostgreSQL，先以已部署 persistence JAR 从空库迁移到 121 条/`V20260825091459`，再以当前 JAR 只新增 1 条到 122 条/`V20260827183737`，同时验证当前 JAR 从空库直接执行 122 条。新版本 Flyway checksum 为 `-976579670`，迁移列为可空 `timestamp without time zone`。
- 新 migration 源码与最终 persistence JAR 内字节 `cmp` 一致，SHA-256 均为 `d032d0a50c59a719f056654880424f5525a843ea96512ac7d95c7d4c36027362`；旧基线源码/JAR SHA-256 继续为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`。
- 首次完整打包沿用了已单独生成但未部署的客户端版本 `20260827165255`，发现它与此前同版本候选具有不同 JAR SHA，违反不可变版本门禁，故该完整包未交付；重新分配 `20260827185946` 后构建并逐项验证 catalog、manifest、client JAR、JDK、OpenCode、公共能力包六份 RSA 签名。
- 在全新临时目录独立解开最终外层和内层 ZIP，验证 ZIP CRC、内层 `cmp`、三节点 SHA、组件 manifest、配置域名/密钥继承标记及禁带清单；worker、programs、toolbox、ClickHouse、memory 和 trace 运行归档命中数为 0。固定交付目录回读 SHA 和 ZIP CRC 均通过。

### Result

- 首次候选外层 SHA-256 `47648844bf05673d11b6817b8af0a7e7a71167664f859521f0fa39768745f4b4`、内层 SHA-256 `bdfbb6b78b73d7f78fd8139c512b543bda20f2c3994c8cf99b3285ece913e926` 后续因随包 `START-HERE.md` 仍沿用旧 Flyway 增量说明而作废，没有作为最终交付件；修正后的摘要记录在后续“补齐当前 SkillMarket Flyway 发布门禁”条目。
- 该作废候选的 `.4`、`.114`、`.2` 节点包 SHA-256 分别为 `514dfbf73cab9003b630c3e7e3fddbc9081b4bfb273366c87b2e2d0df747ec77`、`e7e6dc117e3ce48db9b45257df3f1b3ade250083c4317c01b0413d50a6a1fe67`、`4caaf9275de2c0cabc0eae676364cdd081e12ca8ef09461c218dfa142651fd8d`，不得用于现场校验。
- 应用 JAR、persistence JAR、XXL JAR、前端归档 SHA-256 分别为 `bec7a3107a5641783750ad63fa4fe571b6a02e1d0499bdd361cf2b18f1f57323`、`c0ec4e509804e403499ac71a38444b7285ed64b7bf06b7803ffa9b58d7c963cf`、`007baa8d23b808eec5aed6294ecb113361287df4bf3a6c2c3ca46aeedc80d8c3`、`3f750b66c03c2252292cc626b00470280329f81a113e3e7ef58615ae3fb34a66`。
- 本地构建和离线包校验已完成；企业 `.4 → .114 → .2` 尚未执行本轮部署和业务验收。本条发布追溯提交晚于产品打包输入，不进入上述 ZIP。

## 2026-08-27 - 补齐当前 SkillMarket Flyway 发布门禁

### Why

- 最终 JAR 已包含并验证 `V20260827183737`，但外层包的 `START-HERE.md` 仍把已部署的 `V20260825091459` 写成未部署候选，并声称本轮只新增旧版本；现场按该说明验收会错误判断数据库历史。
- 三个发布/部署脚本的固定 migration 字节门禁也只覆盖到 `V20260825091459`，新 migration 虽经本轮人工解包校验，却没有成为后续每次打包和安装的自动门禁。

### What

- 复用三个脚本既有 `verify_release_flyway_migrations_jar`，追加 `V20260827183737__agent_skill_hub_assets_add_external_created_at.sql` 及固定 SHA-256；没有新增第二套迁移器、校验脚本或旁路发布流程。
- 外层封装的手册同步门禁同时锁定新 migration 文件名、SHA-256 和 Flyway checksum `-976579670`，缺少任一标记直接拒绝生成外层包。
- 修正多后台部署手册：`V20260825091459 / 749555545` 是已部署基线，本轮 `.4` 只允许新增 `V20260827183737 / -976579670`，`.114` 只做 validate，并验收可空 `external_created_at` 列。

### How

- `bash -n deploy/internal/package-release.sh deploy/internal/deploy-internal-release.sh deploy/internal/package-two-backend-complete.sh` 和 `git diff --check` 通过；首次执行外层封装时，旧基线 checksum 的手册措辞未命中精确固定标记，门禁按设计在写出候选前失败关闭，随后把语义相同的说明改为门禁固定短语再重试。
- 变更沿用既有常量、JAR 资源字节校验和 `START-HERE.md` 生成路径；正式内外层重建及独立解包校验将在本次发布追溯提交后重新执行。

### Result

- 后续从当前分支生成的内层包、目标机安装结果和外层包都会自动拒绝缺少或字节不一致的 `V20260827183737`；随包操作手册与本轮真实 PostgreSQL 121 → 122 升级结论一致。
- 修正提交后复用同批已验证 Java、前端和客户端制品执行 `--zip-only`，正式内层门禁明确输出新 migration 的固定 SHA；外层封装同步通过新旧 Flyway 文件名、SHA 和 checksum 手册门禁，随后在全新临时目录再次完成外层/内层 ZIP CRC、嵌套 `cmp`、三节点 checksum、配置、禁带清单、客户端六项 RSA 验签和源码/JAR 字节校验。
- 最终固定交付件 `/Users/kaka/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip` 约 423 MiB，外层 SHA-256 `e3cf7e6195f3dfd405fec09af25c79c759fd7ab5eeabf2781c79305999156ef6`，内层 SHA-256 `c7671c4674c670df7c08362a4777b98884f2d13b41d06867b34059a8fcec6240`；`.4/.114/.2` 节点包依次为 `68c00ad2c59e295978c5c13e707c93c6ee35aeee9c17dcc2e71d54a478855007`、`3e50665d39fb75f5eb36f6cfe36d9893b2d7a0b7e5fef9e0a11ba3f72c72a7b3`、`2df3f4cb58c279269215a95429772a12782710ba52ec03f45249b2ef608b3c0f`；固定目录回读 SHA 和 ZIP CRC 通过。
- 最终包的业务二进制仍来自 `07b9d451aab5473a4c4886feec5dda6dc3e88991`，发布脚本与手册包含到 `ea83e451a`；客户端版本/签名摘要、应用 JAR、persistence JAR、XXL JAR 和前端归档均未因重封改变。企业 `.4 → .114 → .2` 部署与业务验收仍未执行。
- 本条最终摘要提交晚于固定包生成，不进入该 ZIP；它只在仓库中追溯最终摘要，避免为把自身哈希写回包内而形成循环重封。
- 本次只修改企业发布脚本、部署手册和会话记录，不改变业务 API、RunEvent/SSE、数据库 SQL 字节、运行时兼容策略、环境配置、generated SDK、OpenCode 只读源码或部署拓扑。

## 2026-08-27 - 修复个人仓库无关变更阻塞版本切换

### Why

- 企业 F-FPA 用户切换应用版本时，个人仓库中兄弟工作区的 `spec/**`、`.opencode/**` 等无关 staged/unstaged/untracked 变更触发统一 `CONFLICT`；版本进入入口在 Git merge 前错误要求整个仓库完全干净，与既有原生合并语义不一致。

### What

- default/custom 个人 worktree 新建、接管或目录修复追赶固定 target 时，直接复用原生 `mergeCommit`；非重叠本地状态原样保留，`LOCAL_CHANGES` 只返回 Git 识别出的精确阻塞文件，真实冲突和已有 merge 保留现场后进入既有 Diff。
- 增加 traceId 结构化合并结果日志和五类服务回归，目录仍缺失时继续拒绝回退到仓库根；同步 workspace-management README、HTTP API 和用户手册。

### How

- `ManagedWorkspaceApplicationServiceTest` 107 项通过；`GitWorkspaceServiceRealGitTest` 和 workspace-management Maven reactor 全量测试通过；JDK 21 后端 26 模块 `mvn clean package -DskipTests` 成功。
- JDK 25 按 `.env.test` 执行完整重启；首次发现该文件已配置的本地 PostgreSQL `127.0.0.1:15432/test_agent` 未启动，使用仓库既有 `tools/dev-local-up.sh --redis` 拉起 PostgreSQL、MySQL、Redis 后重试，后端 readiness、前端和 OpenCode manager 均启动成功。未修改 dotenv 或切换数据库。

### Result

- 代码、真实 Git 行为、模块测试、完整打包和本机运行启动均已验证；本地后端 `8080`、前端 `3000` 及配套依赖保持运行，企业 F-FPA Java 节点部署及受影响用户仓库复测尚未执行。
- 本次使用 `release`，不新增部署节点，不修改路由、DTO、事件、数据库、Flyway、环境配置、generated SDK 或只读 OpenCode 源码；并行出现的 local-client 工作树改动未纳入本次提交。

## 2026-08-27 - 新增 Win10 x64 本地客户端候选安装包

### Why

- 现有本地客户端只有银河麒麟 ARM64 正式包，用户要求先生成 Windows 10 版本，同时不能破坏共享发布目录下既有麒麟安装与升级链路。

### What

- 本地客户端 Java 运行时新增 Windows/x64 平台识别、AppData 路径、`.exe` 布局和跨平台下载自检；服务端注册、目录查询与更新编排接受 Windows/x64，并阻止不同平台版本相互投放。
- 新增 Go 编写的每用户 Windows Setup/稳定启动器和离线打包脚本，固定 Temurin 21、OpenCode 1.18.4、应用 JAR及组织 RSA 签名；安装流程支持清单验签、安全解包、首次注册、计划任务、开始菜单、后台运行、自更新超时回滚。
- 麒麟安装脚本改为从共享 catalog 选择最新兼容的 `linux/arm64` 版本，忽略更高版本号的 Windows 条目；同步后端、模块、内部部署、架构、事件和部署文档。

### How

- 相关 Maven reactor 共 1542 passed / 1 skipped；Windows launcher 的 Go test、host/Windows go vet、Windows 交叉编译、Windows 离线打包回归及既有麒麟打包回归全部通过，Shell 语法、ZIP CRC、PE 类型、JAR 版本、JDK 布局、清单与四项制品 RSA 验签均通过。
- 实际候选版本为 `20260827204600`，ZIP SHA-256 为 `52d31ce402f004d902ca44a9f56b4bc91181f6ca2a591512f825d0e7b9fadb72`；证据文件明确记录 `authenticodeSigned=false` 和 `CANDIDATE_ONLY`。

### Result

- 已在本机生成可移交 Win10 x64 测试的候选 ZIP，但因当前 macOS 环境不能执行 Windows PE，尚未完成 Win10 1809+ 真机安装、首次注册、升级、回滚和卸载验收，也尚未做 Authenticode 签名；因此未发布正式下载别名，不能表述为正式交付包。
- 本次使用 `release`，不新增部署节点，不变更 HTTP API 结构、RunEvent 类型、数据库、SQL 或 Flyway；只扩展既有注册/发布协议的平台取值与安全校验，未修改 `.env*`、generated SDK 或 OpenCode 只读源码。

## 2026-08-27 - 新增并修复大数据公共测试案例规约

### Why

- 需要把“大数据测试指引”的测试原则、投产前 checklist 和第一章公共测试案例接入现有公共 Test Design 规约；初稿把所有大数据卡统一加载，并新增了“纯注册变更不强制消费/日终”的推断，既不能明确区分 BDP、BDSP、出湖，也与原材料的 Hudi 完整链路要求不一致。

### What

- 在个人公共配置 worktree `.testagent/agent-opencode/.configdev/public-usr_test_dev` 新增 `big-data-common-cases.md`，保留 21 张规则卡，并为每卡增加 `COMMON`、`BDP`、`BDSP`、`OUTBOUND` 适用域和显式平台标题。
- Hudi 表结构、视图、移行或修数变更统一要求“注册/启动 → 实时消费 → 日终作业”完整验证；执行条件不足时记为 `MISSING_EVIDENCE`，不得直接排除消费或日终。
- Test Design Skill 升级到 `4.8.0`；生成和 Review Agent、对象目录、规约索引、质量门禁及 manifest 同步增加 `domainScopes`、逐领域 `loadEvidence`、`expectedRuleIds` 和 `domainScopeVerdict`，只评估命中领域的规则集合。新增 BDP、BDSP、普通 CSV 和 Hudi 回归评估样例。

### How

- 用本机 Ruby 断言 21 个编号连续、每卡六字段完整，且 BDP、BDSP、出湖分域集合与索引一致；JSON 校验 19 条 eval ID 唯一，并锁定 Hudi 不得降级为静态/启动验证。
- 使用 OpenCode `1.18.18` 的 `debug skill`、`debug agent test-design-generation` 和 `debug agent test-design-review` 在个人公共配置目录完成原生加载，提交后复跑通过；共享运行副本 `.config` 保持 clean。

### Result

- 公共配置个人分支 `public-usr_test_dev` 包含初始提交 `8a97231` 和修复提交 `a089368`；修复后工作树 clean，未推送 Gitee、未执行平台发布，因此共享运行配置尚未生效。
- 本次不新增部署节点，不涉及 HTTP API、RunEvent/SSE、数据库、Flyway、性能实现、安全协议、环境配置、generated SDK 或只读 OpenCode 源码；根仓库其它本地客户端改动未纳入本次提交。

## 2026-08-27 - 完善麒麟与 Windows 本地客户端诊断日志

### Why

- 本地客户端发生安装、接入、连接、自更新或 OpenCode 启动问题时，原日志缺少统一启动会话、阶段、耗时和请求关联字段；麒麟与 Windows 启动器也没有同口径的持久诊断轨迹，现场难以快速定位故障阶段。

### What

- Java 客户端为每次 JVM 生成 `trace_client_*` 会话，在启动配置、接入、WSS、工作区注册、生命周期、自更新、公共能力、模型中继和 OpenCode 监管路径增加受控事件、requestId/traceId、generation、状态、耗时及根异常类型；日志模板对线程和消息做 CRLF 编码。
- 麒麟 Shell 与 Windows Go 启动器统一写 state `logs/launcher.log`，记录平台检查、签名制品校验、release 准备/切换、运行时自检、接入、自启、进程退出、更新激活和回退，5 MiB 轮转为 `launcher-1.log`；Windows 额外保留不跟随符号链接的安全失败摘要。
- 托盘日志导出纳入 client/launcher 日志并继续排除 `opencode.log`；统一禁止记录统一认证号、Client key/token、认证头、服务端正文、异常 message、prompt、请求正文或工作区文件内容。同步模块、架构、内部部署和现场排障文档。

### How

- 本地客户端 Maven reactor 231 passed / 1 skipped；Windows launcher 的 Go test、host/Windows go vet、Windows x64 交叉编译通过；麒麟完整打包回归与 Windows 候选打包回归均通过，覆盖日志生成、0600 权限、凭据不落日志、轮转、脱敏和符号链接拒绝。
- 最终从 ZIP 回读验证 JAR 版本、诊断类、Log4j2 session 模板、Setup/稳定启动器 PE x64 类型、共享 catalog、manifest 和四类制品 RSA 签名。最终候选版本 `20260827212132`，ZIP SHA-256 为 `5288c7b618cab16372314a9ccba2fb867ff6cdd7c24737378aa60e8675973415`，证据状态为 `CANDIDATE_ONLY` 且 `authenticodeSigned=false`。

### Result

- 代码、跨平台构建和离线打包链路已验证；最终 Win10 候选 ZIP 可移交真机测试。当前 macOS 不能执行 Windows PE，仍未完成企业 Authenticode 签名和 Win10 1809+ 真机安装/接入/升级/回退/卸载验收；本次也未在真实麒麟 ARM 用户机部署验证。
- 本次使用 `release`，不新增部署节点，不变更 HTTP API、DTO、RunEvent/SSE、数据库、SQL、Flyway、环境配置、generated SDK 或只读 OpenCode 源码；未修改 `.env*`，未发布企业正式下载别名。

## 2026-08-27 - 基于当前 release 工作树重打企业增量包

### Why

- 用户要求基于当前代码重新打企业包；上一轮候选包未部署，企业已部署基线仍为 `release@7152a4340`，因此不能把未部署候选错误登记成现场基线。
- 当前工作树还包含 4 份用户手册诊断说明改动，需要参与前端/VitePress 构建，但不应由打包任务擅自提交或清理。

### What

- 以 `release@363e1b6babe291018800e76e0cb2c1034cfc230f` 和当前工作树为输入重编后台、前端、用户手册及固定组织密钥签名的麒麟 ARM64 客户端，客户端版本为 `20260827222702`；企业公共能力继续固定已发布提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`。
- 组件计划只包含本轮变化的本地客户端；worker runtime 指纹 `877cea1827a6f55b994f4a82f0934d72ca1361fb12b9872ce77e45430073be33` 与 toolbox 指纹 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040` 均复用，LobeHub、memory、独立 ClickHouse/CK、BGE、pgvector、trace 和 Windows 候选二进制不进入正式平台包。
- 最终固定名外层包 SHA-256 为 `3d1d30a29ab96c5e268fe4fec93c17552eebe003462db2a22e88c5ada166d770`，内层包为 `62d2db9bce215f239c3c79d0385159e1c091448b4e38c2d30ad878e7ec0788b6`；已复制到 `/Users/kaka/Desktop/mimoagent/0709`。

### How

- 固定组织私钥自检通过，私钥推导公钥与仓库外固定公钥 DER 指纹均为 `6d294535e5bf4c2a0ea2ba3ae8b1fc9502444de7639df3607d5e360846c9ca62`。
- 受影响的 16 模块 Maven reactor 测试通过，麒麟与 Windows 客户端打包回归通过；正式构建中的 VitePress、`vue-tsc` 和 Vite production build 通过。
- 使用全新临时目录独立验证外层/内层 ZIP CRC、嵌套内层逐字节一致、三节点归档及 SHA、麒麟客户端 catalog/manifest/制品 RSA 签名、内置应用 RSA 私钥和组件排除门禁。最终 App、persistence、XXL、前端 SHA-256 分别为 `24426de896e845c572109b549a029cd66f4352812dda428737b358e1ea488eaa`、`b303d4fe03e18d6f4d0e246a3948c74aedc5dd1c9ca98389e0831ef45060cafb`、`91a52d56f1b3891d5fa1c3f8305345e96da4a8eb92901b7a34817b17fabbc26b`、`8b74d567ce99a94b25b6395cf6f9338f35d27a817f5998eec56c561ddae2dd40`。
- Flyway 相对企业已部署 `V20260825091459` 仍只有待执行的 `V20260827183737__agent_skill_hub_assets_add_external_created_at.sql`；最终 persistence JAR 内两条 SQL 均与源码字节一致，新 migration SHA-256 为 `d032d0a50c59a719f056654880424f5525a843ea96512ac7d95c7d4c36027362`。

### Result

- 企业增量包已完成本机构建与独立验收，可移交企业中转机；尚未在 `.4/.114/.2` 部署，Flyway 只能由 `.4` 首节点执行，`.114` 必须在前者成功后以 validate-only 接入。
- Windows 仍为 `CANDIDATE_ONLY`，未完成 Authenticode 和 Win10 真机验证，不随正式客户端目录发布。4 份用户手册改动已进入本次前端产物，但继续保留为用户未提交改动；本日志提交不纳入这些文件，也未推送远程。

## 2026-08-28 - 从大数据规约修复提交重打公共配置完整替换包

### Why

- 用户要求打包公共 Config Git 库；已有固定名企业完整替换包仍是 8 月 1 日旧制品，未包含本次 BDP/BDSP 分域和 Hudi 完整链路修复。

### What

- 以干净个人公共配置分支 `public-usr_test_dev` 的 `a089368c31f10732b43431c36f59153ba00d6659` 为唯一源，重新生成 `deploy/internal/dist/test-agent-public-agents-skills.zip` 及同名 `.sha256`。
- 延续既有完整替换包白名单，只收录根 `README.md` / `.gitignore` 和全部 `opencode/**`；不收录 Git 元数据、`node_modules`、缓存、个人 Agent Session/Workspace 或验收样例。

### How

- ZIP CRC 通过；归档 120 个文件，包含 8 个 Agent、16 个 Skill 和 10 个 Tool。解压结果与目标 Git 提交的同白名单归档逐文件字节一致。
- 禁带路径扫描通过；包内规约复核 BDP、BDSP、Hudi“注册/启动 → 实时消费 → 日终作业”、Skill 分域路由和 `spec-index.md` 文件路由均通过，且不再包含“不强制消费”表述。

### Result

- 新包 SHA-256 为 `4c43a37187256f984e4059e1b0b57cc5846bfd9f6477490fe15fbec74779efeb`，可经“系统管理 → 配置管理 → opencode 公共配置管理”导入个人 worktree、查看 Diff、提交并发布；禁止直接覆盖共享运行目录。
- 源分支仍未推送 Gitee、未执行平台发布，因此共享运行配置尚未生效。本次只更新忽略的离线制品和本会话日志，不涉及 API、RunEvent/SSE、数据库、Flyway、性能、安全协议、环境配置、generated SDK 或 OpenCode 只读源码。

## 2026-08-28 - 生成 Win10 x64 单机测试候选包

### Why

- 用户要求单独验证 Win10 客户端，需要基于当前 `release` 源码重新生成可完整解压、独立安装的测试 ZIP，并继续沿用组织签名、公用能力和企业 HTTP 入口。

### What

- 以 `release@5a5f128624b55e98030a5481ca56954431846da0` 为源码输入，客户端版本为 `20260828090513`；公共能力固定已发布提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`，控制与下载入口仍为 `http://mimo.sdc.cs.icbc:9996`。
- 最终候选为 `TestAgent-Local-Client-Win10-x64-20260828090513-unsigned.zip`，SHA-256 为 `046c2024fdae4e702564d541c8db86cec40cd4cb36debf2514990b7b3e66275c`；证据保持 `CANDIDATE_ONLY`、`authenticodeSigned=false` 和最低 Windows build `17763`。
- 首次为减少下载而从上一签名 release 反向重建源 ZIP，文件正文一致但归档权限元数据改变，导致 JDK/OpenCode 摘要漂移；该 `20260828090159` 候选未交付，已移入忽略的 `superseded/20260828090159`，最终候选改用脚本锁定的官方原始归档重建。

### How

- 组织私钥自检通过，私钥推导公钥与固定公钥 DER 指纹均为 `6d294535e5bf4c2a0ea2ba3ae8b1fc9502444de7639df3607d5e360846c9ca62`；最终 catalog、manifest 和 `CLIENT_JAR/JDK/OPENCODE/PUBLIC_CAPABILITIES` 四类制品均从最终 ZIP 回读并通过大小、SHA-256 和 RSA 验签。
- 最终 JDK 与 OpenCode 摘要分别为 `5f46abd53a4b5b6dc9b982c70542bb62c6c7b0640a2c9b0b219c26385918aa5a`、`a4eb19eb4bf2e2c94ecb4858e1192821696a80d807296c0d5a3c640f7e88c628`，与上一可信 Win10 release 完全相同；Setup 和稳定启动器均为 Windows x64 PE，JAR manifest 版本和诊断类存在。
- Windows launcher Go test、Windows 离线打包回归、host/Windows go vet 通过；本地客户端 Maven reactor 共 231 passed / 1 skipped。ZIP CRC、固定别名逐字节一致、组织私钥不入包和中间候选不入 catalog 均通过。

### Result

- Win10 x64 单机测试候选已完成 Mac 侧构建与反向验收，可移交 Windows 10 1809+ x64 真机测试；当前尚未完成 Authenticode 和真机首次安装、接入、计划任务、OpenCode、升级/回退及卸载验收，不能作为正式发布包。
- 本轮只生成忽略的候选制品并更新本会话日志，不修改产品代码、文档、API、RunEvent/SSE、数据库、Flyway、安全协议、环境配置、generated SDK 或 OpenCode 只读源码；工作区原有 4 份用户手册改动未纳入本次提交。

## 2026-08-28 - 公共 Agent 发布后并行排空用户进程

### Why

- 公共 Agent 发布完成 Git 同步后，原定时 worker 每轮只认领并串行 dispose 一个用户 OpenCode 进程，用户数量增加时全局 rollout 收敛时间按人数线性增长。

### What

- `PublicAgentConfigRolloutService` 改为每台 Java 默认认领并有界并行排空最多 8 个不同 OpenCode 进程，复用 Reactor `boundedElastic` 承载既有阻塞式 runtime 调用；并发度配置限制在 `1..32`。
- 继续复用现有进程 `ProcessKey`，同一精确服务器/容器/端口/PID/启动时间命中的多个公共、应用或个人目标仍在组内串行，避免对同一 OpenCode 实例重入 dispose 或重启。
- 新增不同进程确实重叠 dispose、同一进程保持串行的单元测试，并同步 runtime README 与应用工作树测试说明。

### How

- `PublicAgentConfigRolloutServiceTest` 定向 43 项通过；runtime 依赖链完整 Maven 回归共 1,357 项通过、0 失败；JDK 25 后端 26 模块 clean package 和前端 VitePress、`vue-tsc`、Vite production build 均成功。
- 按 `.env.test` 执行完整重启时，当前文件实际仍指向未运行的回环 PostgreSQL `127.0.0.1:15432/test_agent`，后端因连接拒绝退出；这与仓库验收规范声明的 `.100/testagent_dev` 不一致。本次未修改 dotenv、未启动本机数据库，也未用其它环境替代，重启等待脚本和半启动进程均已清理。

### Result

- 并行 dispose 行为、同进程安全边界、完整编译与自动化回归均已验证；真实 Spring Boot readiness 因当前 `.env.test` 外部状态未完成，不能表述为完整运行验收通过。
- 本次使用 `release`，不新增部署节点；不变更 HTTP API、DTO、RunEvent/SSE、数据库、SQL、Flyway、鉴权、安全协议、generated SDK 或 OpenCode 只读源码。工作区原有 4 份用户手册改动继续保留且不纳入本次提交。

## 2026-08-28 - 修复应用缺少 OpenCode 配置目录时对话失败

### Why

- 企业现场多个应用的个人工作区真实存在，但应用尚未创建可选的 `.opencode` 目录；Run 启动前的自动化引用对账读取 `opencode.jsonc` 时，把缺少配置目录误报成 `VALIDATION_ERROR / ROOT_UNAVAILABLE`，导致对话在接口已触发后仍失败。

### What

- `AgentConfigApplicationService` 区分“真实工作区根目录缺失”和“可选 Agent 配置目录缺失”：前者继续返回 `ROOT_UNAVAILABLE`，后者在单文件/分片读取时返回既有 `NOT_FOUND`，配置树仍按空目录处理。
- 自动化引用 JSONC 对账在原配置为空且应用没有托管自动化引用时保持空状态，不为普通 Run 创建无业务内容的 `.opencode/opencode.jsonc`；存在就绪引用时仍复用既有条件写链路创建标准配置。
- 新增缺目录、缺真实根目录、无引用不落盘和有引用正常创建的回归测试，并同步 workspace-management README。

### How

- 三组定向测试与 `test-agent-workspace-management` 依赖链全量测试均通过；`git diff --check` 通过。JDK 25 下 26 模块 `mvn clean package -Dmaven.test.skip=true` 构建成功。
- 按根目录 `.env.test` / `test` profile 执行项目启动，当前环境实际连接未运行的回环 PostgreSQL `127.0.0.1:15432/test_agent`，后端因连接拒绝退出；未修改 dotenv、未切换数据库，启动脚本和半启动进程均已清理。

### Result

- 代码层已阻断“没有 `.opencode` 就无法发起对话”的误报，同时保留真实工作区丢失的诊断和实际自动化引用的配置创建能力；企业环境仍需部署新后端后用原问题应用复验。
- 本次使用 `release`，不新增部署节点，不变更 HTTP API、DTO、RunEvent/SSE、数据库、SQL、Flyway、鉴权、安全协议、generated SDK、环境文件或只读 OpenCode 源码。工作区并行存在的用户手册与 help-center 测试改动保持未暂存，不纳入本次提交。

## 2026-08-28 - 基于当前 release 工作树重打企业增量包

### Why

- 用户要求基于当前代码重新打包；上一候选尚未部署，企业成功部署基线仍为 `release@7152a4340`，不能把本机构建状态误记成现场状态。
- 当前 `release@1904d985fad1d917d314cc074b5cec52afaaebf0` 之后需要交付公共 Agent 发布后有界并行排空、应用缺少可选 `.opencode` 目录时仍可对话，以及工作树中 5 个未提交的帮助中心/用户手册改动。

### What

- 完整重编后台、VitePress 用户手册和前端生产资源；worker runtime 指纹 `877cea1827a6f55b994f4a82f0934d72ca1361fb12b9872ce77e45430073be33`、toolbox 指纹 `35447da08f477dd02e458e4344be9bd870452dba12ba6db32d250c56e9f15040` 均为 `reuse`，不携带或重启 manager/worker、工具箱、CK、Mem0、BGE、pgvector、LobeHub、trace、Python 库或独立 ClickHouse 制品。
- 客户端源码和配置相对上一候选未变，不伪造新版本；企业仍未部署该候选，因此正式包继续包含组织密钥签名的麒麟 ARM64 客户端 `20260827222702`，固定企业入口 `http://mimo.sdc.cs.icbc:9996`，公共能力仍锁定已发布提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`。Win10 `20260828090513` 仍是未做 Authenticode 的独立候选，不进入正式 catalog 或平台包。
- 最终固定名外层包 SHA-256 为 `17cceffa620678c354948c6dbb7b4690e38464669a8f7dfdff51323399776477`，内层包为 `08531ab7270a6a3c2fc3dfde70a9d21fc34fe17986f89f531c1dccfd9627da92`；已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/`。

### How

- 固定组织私钥自检通过，私钥推导公钥与固定公钥 DER 指纹均为 `6d294535e5bf4c2a0ea2ba3ae8b1fc9502444de7639df3607d5e360846c9ca62`。14 模块 Maven reactor 全部成功，`test-agent-opencode-runtime` 988 项通过；正式后端打包、Flyway 资源门禁、VitePress、`vue-tsc` 和 Vite production build 均通过。
- 前端根级全量 Vitest 为 158 个文件，`2264 passed / 1 skipped / 2 failed`；两处失败均来自当前未提交手册新增段落使内置帮助 6700 字符提示截断，旧断言期待的后段文字不再进入 prompt。该问题不影响 production build，但仍是部署后帮助问答待验收风险；本次打包任务没有擅自修改或提交这 5 个现有文件。
- 首次封装发现共享本机构建目录的 catalog 被 Win10 测试候选追加为最高版本，而 `stable` 仍是麒麟版本，正式门禁正确拒绝混合。改用隔离临时目录从上一可信内层包恢复单版本麒麟分发，再装入本轮新前后台；最终正式 catalog 只含 `20260827222702`，catalog、manifest 和客户端制品 RSA 验签通过。
- 全新临时目录独立验证外层/内层 ZIP CRC、嵌套内层逐字节一致、三节点归档 SHA、组件排除、TCDS `http://tcds-prod.sdc.icbc:9080`、AAM、企业域名、两后台 SkillHub key 非空和麒麟客户端签名。最终 App、persistence、XXL、前端 SHA-256 分别为 `2141bcf8b1ae55766b7272ccd2fae29bb8612d0784b71d63d01ff773fbb331c2`、`8f800ec2630cddd7edd57aa310e7bd96f0cd7d683bd36e5ce845d70d7e618e9c`、`38c28a32046496ffc0d29fac1856a73a343da1964bbd8728852b4f5de0978092`、`06d290634e62241921915570d7aa95aa9783a8b13762b774c2edce5876441f0c`。
- 本轮没有新增或修改 Flyway SQL；相对企业已部署 `V20260825091459` 仍只允许 `.4` 首节点新增 `V20260827183737__agent_skill_hub_assets_add_external_created_at.sql`，checksum `-976579670`、源码和最终 persistence JAR 内 SHA-256 均为 `d032d0a50c59a719f056654880424f5525a843ea96512ac7d95c7d4c36027362`。`V20260825091459` 最终 JAR 字节 SHA-256 仍为 `6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3`。

### Result

- 企业增量包已完成本机构建、生产编译、签名和独立归档校验，可移交中转机；尚未在 `.4/.114/.2` 执行部署，Flyway 只能先由 `.4` 升级，确认成功后再部署 `.114` 和 `.2`。
- 本次不修改产品代码、API、DTO、RunEvent/SSE、数据库 SQL、环境文件、generated SDK 或 OpenCode 只读源码；只提交本发布记录。5 个现有前端/手册改动继续留在工作树且已进入构建产物，不推送远程。

## 2026-08-28 - 更正企业部署基线并重打纯平台增量包

### Why

- 用户补充确认 8 月 27 日晚间外层包 `3d1d30a29ab96c5e268fe4fec93c17552eebe003462db2a22e88c5ada166d770` 已完整部署；因此麒麟客户端 `20260827222702` 和 `V20260827183737` 已属于企业现场基线，不能再次按 `include` 或待执行 migration 处理。
- 上一条记录的 423 MiB 候选外层包 `17cceffa620678c354948c6dbb7b4690e38464669a8f7dfdff51323399776477`、内层包 `08531ab7270a6a3c2fc3dfde70a9d21fc34fe17986f89f531c1dccfd9627da92` 基于错误现场假设生成，现明确作废，禁止部署。

### What

- 仍以业务源码 `release@1904d985fad1d917d314cc074b5cec52afaaebf0` 和 5 个现有未提交帮助中心/用户手册文件为构建输入，只交付新后台和前端产物。
- worker runtime、toolbox、本地客户端均改为 `reuse`；客户端继续复用已部署版本 `20260827222702`，包内不携带客户端、manager/worker、toolbox、CK、Mem0、BGE、pgvector、LobeHub、trace、Python 库或独立 ClickHouse 载荷。
- 最终固定名外层包 SHA-256 为 `c6027fb7ba234e3a7f69c1868259a449e9d1622c1982b6221e6a5465d243c165`，内层包为 `c129c263ab038785dadcbfee564ac7bfa9cbe4571526dd987dcbb5fa6d3b3287`，大小 148 MiB；已覆盖到 `/Users/kaka/Desktop/mimoagent/0709/`。

### How

- 在隔离目录重新生成内层包，组件清单验证 worker runtime、toolbox、本地客户端全部为 `reuse`，LobeHub 和 memory 为 `disabled`；`dist/` 只含后台与前端产物。
- 使用全新临时目录独立验证外层/内层 ZIP CRC、嵌套内层逐字节一致、三节点归档 SHA、TCDS、AAM、双入口 CORS、两后台 SkillHub key 非空和 `.2:9996` Nginx 配置；固定交付目录再次通过 SHA-256 与 ZIP CRC 校验。
- 最终 App、persistence、XXL、前端 SHA-256 分别为 `2141bcf8b1ae55766b7272ccd2fae29bb8612d0784b71d63d01ff773fbb331c2`、`8f800ec2630cddd7edd57aa310e7bd96f0cd7d683bd36e5ce845d70d7e618e9c`、`38c28a32046496ffc0d29fac1856a73a343da1964bbd8728852b4f5de0978092`、`06d290634e62241921915570d7aa95aa9783a8b13762b774c2edce5876441f0c`。
- 本轮没有新增或修改 Flyway SQL；最终 persistence JAR 内 `V20260825091459` 和 `V20260827183737` 与源码字节一致。部署当前包不得新增 PostgreSQL Flyway 历史行，只需确认已部署的 `20260827183737` checksum 为 `-976579670`。

### Result

- 纯平台增量包已完成本机构建和独立验收，可按 `.4 -> .114 -> .2` 更新前后台；客户端和 worker/toolbox 均不安装、不覆盖、不重启。
- Maven、VitePress、`vue-tsc` 和 Vite production build 均通过；前端根级 Vitest 仍为 `2264 passed / 1 skipped / 2 failed`，失败来自 5 个未提交手册改动使 6700 字符帮助提示截断旧断言。该风险未在打包任务中擅自修改，5 个文件继续留在工作树但已进入前端产物。本次仅提交更正后的发布记录，不推送远程。

## 2026-08-28 - 应用资产库 spec 按应用整体引用

### Why

- 两个应用资产库的 `spec` 结构不同：一类在根层直接放 `I2026...` 需求项目录，另一类先分版本目录，并把需求用例直接放在版本目录、设计资料放在其下的需求项目录。需要明确整体引用后不扁平化、不重排，同时继续保持应用隔离。

### What

- 复用现有根层 SDD 目录选择、`merge=true` 组合视图和当前应用资产库校验；没有新增版本号或需求号识别逻辑，也没有修改 JSONC、HTTP API、DTO、数据库或 OpenCode。
- 引用配置目录树补充当前应用、`spec` 整体选择、原层级保留和资产只读说明；用户手册、agent-web README 与 workspace-management README 同步两类目录示例和检索提示。
- 后端测试新增 `spec/I2026.../概要设计.md`、`spec/2610/需求用例.md`、`spec/2610/I2026.../概要设计.md` 的原路径只读覆盖，并锁定只有根层 `docs/spec` 可选；前端测试锁定保存整个 `spec` 时既有 `docs` 引用和权限不被覆盖。现有跨应用拒绝测试继续随原测试类回归，不增加重复对称用例。

### How

- JDK 25 定向 Maven 共 75 项通过，引用配置 Vitest 51 项通过；agent-web typecheck、VitePress 用户手册构建和 Vite production build 均成功，`git diff --check` 通过。
- 按本地启动技能使用根目录 `.env.test` 和 `test` profile 执行完整重启，26 模块后端 package、opencode-manager build 和前端 production build 均成功；后端 readiness 因当前 `.env.test` 实际指向未运行的 `127.0.0.1:15432` PostgreSQL 而失败。未修改 dotenv、未切换数据库，启动脚本和半启动进程已清理。

### Result

- 两类 `spec` 结构在代码级均按原相对路径合入当前工作区，资产后代保持只读，工作区原内容仍可写；跨应用访问继续由既有门禁拒绝。
- 自动化回归、类型检查和构建已验证；两个真实应用的页面/服务端到端验收因当前 `.env.test` 数据库配置阻塞而未完成，不能表述为完整运行验收通过。工作区原有 5 个帮助中心/用户手册改动继续保留且不纳入本次提交。

## 2026-08-28 - 同步 release 内置用户手册

### Why

- `release` 已交付本地客户端受限诊断日志导出，以及公共 Agent 发布后的进程有界并行排空；内置手册需要说明入口、账号灰度、安全边界和等待策略。

### What

- 更新功能总览、设置、周更和 FAQ：说明托盘“查看日志 / 下载日志”、ZIP 的脱敏范围，以及 `SUPER_ADMIN` 在“TestAgent公共配置管理”查看 Git 同步与排空状态的操作。
- 保持客户端灰度与记忆灰度的既有独立说明；新增手册断言覆盖诊断入口与公共 Agent 的跨进程并行、同进程串行边界。缺少真实截图的两个新流程均已明确标注人工补拍入口。

### How

- 新增文字一度把受限帮助问答上下文中的旧 FAQ/周更内容挤出；未调整上下文长度或新增实现，而是把新增 FAQ/周更专题放在既有问答覆盖段之后，继续复用同一 Markdown 来源和现有断言。
- 前端 Vitest 全套通过（158 文件，2267 通过、1 跳过）；VitePress 用户手册构建和 agent-web `vue-tsc` 类型检查通过，图片引用与 `git diff --check` 通过。

### Result

- 用户可在离线手册中安全导出客户端排查材料；超级管理员可按实际页面状态等待公共 Agent 发布后的旧进程收敛，运行中的会话不会因文案建议而被强行中断。
- 本次仅更新用户手册与帮助中心测试，不涉及 API、事件、数据库、性能、安全协议、环境配置、generated SDK 或 OpenCode 源码；目标分支为 `release`，不新增部署节点。

## 2026-08-28 - 基于当前 release 重打纯平台企业增量包

### Why

- 用户要求基于当前代码重新打包；最后确认成功部署的现场基线仍是 `release@363e1b6babe291018800e76e0cb2c1034cfc230f`、外层 SHA-256 `3d1d30a29ab96c5e268fe4fec93c17552eebe003462db2a22e88c5ada166d770`。
- 上一纯平台候选外层 `c6027fb7ba234e3a7f69c1868259a449e9d1622c1982b6221e6a5465d243c165` 未收到部署成功确认，现由本轮候选替代，不登记为现场基线。

### What

- 构建输入为干净工作树 `release@bfaba3e630992e595719f0ee1aa3f321acc47d4d`；相对现场基线包含公共 Agent 发布后有界并行排空、缺少可选 `.opencode` 目录时仍可对话、TCDS 案例维护失败不弹空白标签且成功后直接跳转、应用资产库整体引用 `spec` 目录，以及同步后的内置用户手册。
- worker runtime、toolbox、本地客户端均为 `reuse`，LobeHub 和 memory 组件为 `disabled`；不携带客户端、manager/worker、工具箱、CK、Mem0、BGE、pgvector、trace、Python 库或独立 ClickHouse 载荷，不修改 `.4` 现有模型灰度。
- 客户端继续复用已部署版本 `20260827222702`、企业域名和公共能力提交 `81605f245d1512e1ab0dd73812391f6da7d008b5`；规范化 JDK/OpenCode 摘要按既有已验签制品固定，组织签名公钥 DER SHA-256 仍为 `6d294535e5bf4c2a0ea2ba3ae8b1fc9502444de7639df3607d5e360846c9ca62`。
- 最终固定名外层包 SHA-256 为 `417246c6fdef6fc06a3d1f97a8c3f6d026699a6f37001c4712b618f2dfccc0ab`，内层包为 `7f6d56c9144d4ff81da0dfc222a427f993eb1b15699540867db907837e5a09df`，大小 148 MiB；已覆盖 `/Users/kaka/Desktop/mimoagent/0709/`。

### How

- 发布脚本的 Spring Bean 构造器装配门禁和 JDK 25 后端构建通过；`ReferenceRepositoryApplicationServiceTest`、`WorkspaceViewApplicationServiceTest` 定向 Maven 通过。VitePress、`vue-tsc`、Vite production build 通过；前端本轮三组定向 79 项通过，全量 158 文件为 2269 passed / 1 skipped。
- 全新临时目录独立验证外层/内层 ZIP CRC、外嵌内层逐字节一致、三节点归档及 SHA、组件排除、TCDS `http://tcds-prod.sdc.icbc:9080`、AAM、双入口 CORS、两后台 SkillHub key 非空和 `.2:9996` Nginx 配置；固定交付目录再次通过 SHA 与 ZIP CRC 校验。
- 最终 App、persistence、XXL、前端 SHA-256 分别为 `beee2aaa6c759c8df895b146a68287b8835436f87385f8aa5eaffaeeab8a14f8`、`d8621e6878376af5d97d6a2d0df1307f35be4eab387119a7e1b9cd1441c54310`、`cfa92f879218f60025692c8ac12438e0b48e07450c307d910b8b18267de5d845`、`7d09bef21f0fa4dbdc593152828ed35431ff29ca63cc7211e1b64fec4e8e5986`。
- 本轮没有新增或修改 PostgreSQL、XXL MySQL 或 ClickHouse migration；最终 persistence JAR 内 `V20260825091459` 和 `V20260827183737` 与源码字节一致，部署后不应新增 Flyway history。

### Result

- 本轮纯平台增量包已完成 Mac 侧构建与独立验收，可按 `.4 -> .114 -> .2` 部署；客户端、manager/worker、toolbox 和独立数据面均不重装、不重启、不重复同步。
- 本次打包没有修改产品代码或稳定文档；仅追加本机发布追溯并提交，不推送远程。企业三台服务器和浏览器业务验收尚未执行，不能把本候选登记为已部署基线。

## 2026-08-28 - 应用资产库 spec 双应用真实端到端验收

### Why

- 用户要求对应用资产库整体引用 `spec` 做真实端到端验收，并明确本机 Redis、XXL MySQL 可直接使用 `192.168.8.100` 上的 TestAgent 依赖，不建立 SSH 或本机端口转发。
- 100 上原 TestAgent Redis/MySQL 容器已停止，同端口当时由仅绑定 loopback 的 MockCenter 容器占用；远端 Jenkins 同时占用 `18081`，不能继续沿用旧测试端口。

### What

- 100 上 TestAgent 自有 Redis/MySQL 改为分别监听 `192.168.8.100:16379/13306` 并保持健康；MockCenter 依赖恢复为 `127.0.0.1:16379/13306`，两套容器互不串用。TestAgent compose 原文件已留远端备份。
- 本机 `.env.test` 继续使用固定 PostgreSQL `192.168.8.100:15432/testagent_dev`，Redis 与 XXL MySQL 改为直连 100；远端当前后端包部署到临时验收端口 `18082`，避免 Jenkins `18081`，本机与远端 Java、manager、frontend 均启动成功。
- 为 `F-COSS/appdocs` 和 `E2E 开源项目质量验证/e2e-vue-core` 临时准备图 1、图 2 目录并完成浏览器验收；结束后恢复代码库 URL、版本提交、个人工作区和最近工作区数据，删除本轮新建的个人工作区记录，临时物理目录与远端 fixture 移入可恢复 Trash。直连依赖 compose 与正在运行的 TestAgent 基础服务按用户要求保留。

### How

- Chromium 真实登录 `888888888`，分别从“引用配置”选择并保存整个 `docs/spec`；断言只有根层 `docs/spec` 可配置，内部 `I2026...` 与 `2610` 只能浏览。组合文件树实际读取应用 1 根层 `I20260623-0170/概要设计.md`，以及应用 2 `2610/` 直放需求用例和 `2610/I20260825-9999/概要设计.md`；引用文件键入探针无效，工作区原文件仍为可写节点，两个 JSONC 均保留 `custom.keep` 和并存的 `docs/spec` 引用。
- 两个方向的跨应用真实 tree 请求均返回 `400 VALIDATION_ERROR / 引用资产库未关联当前应用`，没有新增对称自动化用例；现有拒绝用例与目录树、组合视图定向 Maven 共 3 项通过。引用配置 Vitest 51/51 通过；本轮启动前后端全量跳测构建、用户手册/类型检查/Vite build 均通过。
- 验收截图保留在 `frontend/.tmp/spec-e2e-app{1,2}-{reference-dialog,file-tree}.png`；浏览器验收脚本保留为忽略文件 `frontend/.tmp/spec-e2e-acceptance.mjs`。本地与远端 readiness 均为 `UP`，本地前端返回 200，100 上 TestAgent Redis/MySQL 与 MockCenter Redis/MySQL 四个容器均为 healthy。

### Result

- `spec` 按应用独立、原层级整体合并的两种真实结构验收通过；应用 2 的需求用例确认直接位于 `2610/`，Agent 可同时读取版本级用例和下级需求项目录设计资料。跨应用边界仍由现有实现拒绝。
- 本轮未修改产品代码、HTTP API、DTO、事件、数据库结构、Flyway、generated SDK 或 OpenCode 只读源码；未新建分支。远端 `18082` 运行的是本机当前构建包，远端 compose 和临时验收 env/JAR 均有备份，后续若回收该验收节点需显式恢复对应备份。

## 2026-08-28 - 增加 192.168.8.100 Jenkins release 发布编排

### Why

- 用户要求登录 `192.168.8.100`，参考已配置的 `precisiontesttool-release` 完成当前项目 Jenkins 发布；现有
  `abc` 工作树有未提交的 `deploy/local/docker-compose.yml` 改动，不能被 Jenkins 清理或覆盖。
- 当前测试 PostgreSQL 的 Flyway history 停在 `V20260812144051`，当前 `release` 仍有后续 migration；仅编译
  成功不能证明可以从现场历史升级。

### What

- 新增根 `Jenkinsfile`，固定私有 GitLab `wrui/intelligent-test-agent` 的 `release` 分支、SCM 凭据 ID、禁并发、
  150 分钟超时、30 次保留、`DEPLOY/ROLLBACK` 参数和不可变 `release-{build}-{commit}` 标签。
- 新增 `deploy/local/jenkins-release.sh`，复用 Maven reactor、`FlywayMigrationNamingTest`、pnpm workspace、
  `test-agent-app` JAR 和 agent-web production build；生成带源码、JAR、Nginx、Compose 与逐文件 SHA-256 的发布目录。
- 正式接管前从当前 `test-agent-postgres` 做逻辑复制，以独立临时 PostgreSQL 库和 Redis 启动本次 JAR，完成真实
  migration/readiness 验证；成功后才通过 root 固定副本 `jenkins-host-control.sh` 精确停止 `18082/3000` 的旧
  Java/Vite，保留 `4096` OpenCode、数据根和原工作树。
- 新增同源 API/XXL 代理与 SPA fallback Nginx 配置、发布契约自检和 `deploy/local/README.md`，并在文档索引登记。

### How

- `tools/verify-jenkins-release.sh`、`tools/verify-ai-docs.sh`、三份 Shell `bash -n` 与 `git diff --check` 通过。
- 提交前回顾全部 `.agents/session-log*.md` 近期记录；本次只新增 Jenkins/本地部署文件、文档索引和本日志，未触碰
  远端 `abc` 工作树的两处未提交文件、`.env*`、generated SDK 或 OpenCode 只读源码。

### Result

- 仓库侧 Jenkins 发布契约已实现并通过静态自检；下一步仍需在 `192.168.8.100` 创建 GitLab 镜像与只读 Deploy
  Key、安装最小 sudo 白名单、注册 Jenkins 任务并执行真实 `DEPLOY`。在远端构建与运行验证成功前不能称为发布完成。
- 本轮不新增部署节点，不变更 HTTP API、RunEvent/SSE、数据库结构或 migration 文件；会使用现有测试库做受控
  前向升级，因此真实发布必须保留升级前后完整 Flyway history，失败时停止而不执行 `repair/outOfOrder`。
