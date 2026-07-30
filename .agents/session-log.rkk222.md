# Session Log - rkk222

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

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
