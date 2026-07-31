# LobeHub 企业客户端原生构建与审批

本文用于从平台锁定的 LobeHub fork 构建 Windows x64 和 Linux x86_64 客户端。客户端必须在对应原生系统
构建；Apple Silicon、Wine、QEMU 或 Docker 交叉构建结果不能进入正式介质。Windows 私钥只留在企业证书存储，
Linux 的构建人与审批人必须分离。工具不会生成虚假的签名或审批状态。

## 1. Mac 生成受控构建工具包

先确认同级 `../lobehub-platform` 是干净工作树且 HEAD 等于
`deploy/internal/lobehub/version.env` 的 `LOBEHUB_FORK_COMMIT`，然后执行：

```bash
deploy/internal/build-lobehub-client-kit.sh \
  --fork-dir /Users/huang/workspace/lobehub-platform \
  --output-dir /Users/huang/Desktop/mimoagent/0709/lobehub-client-build-kit
```

输出为 `lobehub-client-build-kit-v2.2.11-platform.5.zip` 及同名 `.sha256`。工具包只含锁定 commit 的
`git archive`、版本锁、构建脚本、验收模板和自身完整性清单；不含 `.git`、`node_modules`、签名私钥、已签名
客户端或预先通过的审批。把 ZIP 和 SHA 文件分别交给受控 Windows、Linux 构建机，解压前先校验外层 SHA，
解压后再校验 `BUILD_KIT_SHA256SUMS`。构建机允许联网获取锁文件中的依赖，但不得叠加 LobeHub Cloud 仓库或
替换源码、lockfile、Electron 运行时及构建脚本。

当前外网 Mac 已生成并校验上述 `.5` 真实工具包，ZIP 大小为 `53453544` 字节，SHA-256 为
`10fba3e98938252eb0ca7a3a40d0425d8f043ebe268ee267c2e019f3e2210ee1`；旧 `.3`/`.4` 工具包只作为归档，不能
继续用于 `.5` 客户端构建。

## 2. 固定构建环境

两台原生构建机均固定：

- Node.js `24.11.1`；
- Bun `1.3.2`；
- pnpm `10.33.0`（脚本通过 Corepack 显式调用）；
- 至少 8 GiB 可用内存和足够的临时磁盘；
- 构建前校验外层 ZIP、`BUILD_KIT_SHA256SUMS` 和 `SOURCE_SHA256SUMS`。

Windows 还需 Windows x64、Visual Studio Build Tools、Python、Windows 10/11 SDK 的 `signtool.exe`，以及
位于 `CurrentUser\\My` 或 `LocalMachine\\My`、带私钥和 Code Signing EKU 的企业 Authenticode 证书。证书
密码不得作为脚本参数传递。Linux 需目标企业发行版的 x86_64 主机、Python 3、`build-essential`、`file`、
`tar` 和 Electron native module 所需系统库。

两个构建脚本都会执行 `pnpm install --frozen-lockfile --node-linker=hoisted`，并在打包前运行 Desktop/CLI 的
企业策略、固定服务地址、登录/退出和命令面测试。当前源码强制 `CLIENT_EXECUTION_MODE=disabled`，不存在通过
环境变量或验收后切换为 true 的路径。

## 3. Windows x64 构建与 Authenticode

在 64 位 Windows PowerShell 中，从工具包根目录执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\Build-LobeHubWindowsClient.ps1 `
  -CertificateThumbprint '企业证书的40位SHA1指纹' `
  -TimestampUrl '企业批准的时间戳服务URL' `
  -OutputDirectory 'C:\lobehub-output'
```

`-TimestampUrl` 可在没有批准的时间戳服务时省略，但变更记录必须接受证书到期后的验证风险。脚本只用证书指纹
定位本机证书存储中的私钥，构建 NSIS x64 EXE，调用 `signtool.exe`，再以
`Get-AuthenticodeSignature` 确认状态为 `Valid` 且签名指纹完全一致。成功输出：

```text
lobehub-windows-x64.exe
windows-authenticode-verification.txt
```

证据同时绑定客户端 SHA-256、内部版本、fork commit、x64 架构和执行禁用状态。不得手工把 Unknown、NotSigned
或测试证书结果改成 Valid；证书链、吊销检查或私钥不可用时停止发布。

## 4. Linux x86_64 构建、验收与审批

在原生目标 Linux x86_64 构建机执行：

```bash
./scripts/build-lobehub-linux-client.sh \
  --output-dir /var/tmp/lobehub-client-output \
  --builder builder@example.internal
```

成功只会生成候选件和 `Pending` 构建证据：

```text
lobehub-linux-x86_64.tar.gz
linux-client-build-evidence.txt
```

候选件不能直接交给 Mac 汇集。由另一名审批人在批准的目标发行版/内核上安装或解包候选件，使用测试账号完成
以下检查，并从 `linux-client-acceptance-record.example` 复制一份独立记录填写：

- 平台浏览器确认登录、退出和会话撤销成功；
- 断开公网但保留批准内网后，聊天和问答可用且无公网依赖；
- Marketplace、Connector、在线更新和运行期下载均被拒绝；
- 终端、shell、代码 Agent、stdio MCP、浏览器控制和设备执行在 UI/API/深链/Labs 中均不可开启；
- 两个本地 OS 账号的应用数据、会话和凭据互不可见；
- 验收记录关联真实内部审批人和变更单号，不使用 `REPLACE_ME`、`TBD` 或 `Pending`。

只有所有结果通过后，审批人在同一目标 Linux 主机运行：

```bash
./scripts/approve-lobehub-linux-client.sh \
  --client /var/tmp/lobehub-client-output/lobehub-linux-x86_64.tar.gz \
  --build-evidence /var/tmp/lobehub-client-output/linux-client-build-evidence.txt \
  --acceptance-record /path/to/linux-client-acceptance-record.txt \
  --approver reviewer@example.internal \
  --confirm-device-execution-disabled \
  --output-evidence /var/tmp/lobehub-client-output/linux-client-verification.txt
```

审批脚本会重算候选件、构建证据和验收记录 SHA，拒绝归档路径穿越，确认包内存在 x86-64 ELF，要求构建人与
审批人是大小写无关比较后仍不同的稳定身份，并把构建证据摘要、实际 OS、内核、审批人、版本和 commit 写入
最终证据。它不会替审批人执行人工登录、多账号隔离或断公网测试。正式回传四个 Linux 文件：

```text
lobehub-linux-x86_64.tar.gz
linux-client-build-evidence.txt                 # 正式打包准入文件
linux-client-verification.txt
linux-client-acceptance-record.txt
```

## 5. Mac 汇集与完整介质准入

通过受控通道把 Windows 两个文件、Linux 四个文件回收到外网 Mac；先按交接单核对来源和传输摘要。若已经用
`build-lobehub-artifacts.sh --server-only` 生成并完成真实运行时冒烟，可在不访问网络、不调用 Docker、也不
重建约 2.2 GB 镜像 tar 的前提下定稿新目录：

```bash
deploy/internal/finalize-lobehub-artifacts.sh \
  --server-artifact-dir /absolute/path/to/lobehub-server-only \
  --output-dir /absolute/path/to/lobehub-release-artifacts \
  --windows-client /path/to/lobehub-windows-x64.exe \
  --windows-signature-evidence /path/to/windows-authenticode-verification.txt \
  --linux-client /path/to/lobehub-linux-x86_64.tar.gz \
  --linux-build-evidence /path/to/linux-client-build-evidence.txt \
  --linux-approval-evidence /path/to/linux-client-verification.txt \
  --linux-acceptance-record /path/to/linux-client-acceptance-record.txt
```

定稿脚本先核对 server-only 的完整 `SHA256SUMS`、版本锁、三张镜像 tag/ID、PostgreSQL 17 和执行禁用状态，
然后用共享客户端契约验证正式签名/审批文件；输入目录保持不变，输出目录重新生成精确校验和。输出父目录必须
预先存在；工具使用相邻锁并在复制前后复核服务端清单、六项客户端/证据摘要和输出 inode，检测到输入变化或
并发替换时失败关闭。当前
`v2.2.11-platform.5` server-only 实物必须记录 `LOBEHUB_LINUX_CLIENT_APPROVED=false`；定稿器对早期构建器
缺失该字段的兼容不能把状态提升为已批准。只有全部客户端门禁通过后，输出才会记录 Windows/Linux 为
`true`。磁盘必须为新完整目录预留至少
server-only 目录大小及 ZIP 打包余量。

没有可复用的 server-only 目录时，才执行一次完整构件构建；`build-lobehub-artifacts.sh` 的客户端参数必须
全部提供：

```bash
deploy/internal/build-lobehub-artifacts.sh \
  --windows-client /path/to/lobehub-windows-x64.exe \
  --windows-signature-evidence /path/to/windows-authenticode-verification.txt \
  --linux-client /path/to/lobehub-linux-x86_64.tar.gz \
  --linux-build-evidence /path/to/linux-client-build-evidence.txt \
  --linux-approval-evidence /path/to/linux-client-verification.txt \
  --linux-acceptance-record /path/to/linux-client-acceptance-record.txt \
  ...其余已批准 digest 镜像参数
```

汇集脚本、`package-release.sh` 和现场 `install-lobehub-offline.sh` 会分别重复相同门禁：文件必须为非空普通文件，
Windows 必须 Authenticode Valid，Linux 必须 Approved，签名/构建/审批身份不能是占位值，两端客户端、Linux
构建证据与验收记录、
内部版本、fork commit、架构和执行禁用状态必须全部匹配。任一检查失败时只能保留服务端阶段介质，不能宣称完整
企业介质完成。定稿成功后仍必须执行：

```bash
TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/package-release.sh --lobehub-only
```
