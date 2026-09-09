# 重复打包、签名与部署常见问题

本参考只在企业包重复构建、包体异常、客户端签名/安装、增量组件或现场部署门禁问题中读取。它记录本项目已经实际出现过、容易重复误判的发布问题。

## 1. 先确定真正的发布基线

“上一个包”“上一轮”“最新代码”可能分别指最后构建、最后传入内网、最后部署成功或当前本地 HEAD，不能混用。

每次打包前先记录四组事实：

1. 最后成功部署的源码 commit、部署时间、内层 ZIP SHA-256、外层 ZIP SHA-256。
2. `.4/.114/.2` 实际部署状态；客户端、worker、toolbox、模型灰度和独立数据面是否完成。
3. 当前本地分支、HEAD 和工作树；只有用户明确要求拉远程时才 fetch/pull，不能把“当前代码”解释成自动拉取。
4. 最后成功部署 commit 到当前工作树的 Git 变更，以及不在主仓库 Git 内的公共能力 commit、签名输入和节点敏感配置变化。

只把“最后成功部署”作为更新点和 Flyway 的比较基线。仅打好但尚未部署的候选包不成为新基线；后续又有代码更新时，旧候选直接标记为未部署/被替代，最终只交付最新候选，不逐个部署中间包。

外网 Mac 的基础检查：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
git branch --show-current
git rev-parse HEAD
git status --short
test -z "$(git diff --name-only --diff-filter=U)"
git merge-base --is-ancestor <最后成功部署的源码commit> HEAD
git log --oneline <最后成功部署的源码commit>..HEAD
git diff --name-status <最后成功部署的源码commit>..HEAD
```

最后一条 diff 还不包括未提交文件、公共配置独立仓库、外部客户端 runtime 和敏感节点配置，必须单独盘点。若无法取得最后成功部署基线，可以构建候选，但不得声称它是精确增量、Flyway 可直接升级或列出“相对现网”的完整更新点。

发布说明里的新功能必须来自上述差异和会话记录。没有证据证明某个菜单在已部署版消失过时，不写“恢复菜单”；改成可验证的实际变化，例如“固定提供下载入口”或“轮询期间不再闪烁”。

## 2. 判断是重新构建还是只重封装

使用以下决策：

| 情况 | 动作 |
| --- | --- |
| 源码、构建配置、公共能力 commit 或签名输入有变化 | 从当前工作树重新构建内层 ZIP，再重建外层 ZIP |
| 同一批已完成构建和验证，只补会话日志/交付追溯 | 可用 `--zip-only` 重封同批内层 ZIP，然后必须重建外层 ZIP |
| 上一候选未部署，之后又有代码变化 | 作废旧候选，只从最新输入构建一次 |
| 当前输入与最后候选完全相同，用户只是确认是否需要再打 | 先说明没有新变化；除非用户仍明确要求可重复制品，否则不制造无意义新版本 |
| 只改独立 BGE、CK、Mem0、pgvector 或其它独立节点 | 使用对应独立包，不把它伪装成前后台平台包变化 |

`--zip-only` 不是“跳过编译后把新源码塞进旧包”。它只允许复用同一批次已经按当前指纹生成并验证的二进制。代码变化后必须走正式构建。

每次先查看组件计划：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh --component-plan-only --output-dir deploy/internal/dist
```

当前打包契约中，后端、前端和部署脚本属于每次平台 release 的基本发布单元；指纹增量主要控制 `worker runtime`、`toolbox` 和 `local OpenCode client` 三个大组件。不能把“组件 reuse”夸大成 ZIP 内每一个未变化文件都被逐文件删除；例如 persistence JAR 必须作为完整依赖单元携带全部 Flyway 资源。

## 3. 为什么包又变大了

不要只看总大小猜原因。必须检查最终内层 ZIP，而不是可能保留历史文件的 `deploy/internal/dist` 构建目录：

```bash
unzip -p deploy/internal/dist/test-agent-internal-release.zip \
  deploy/internal/release-components.env
unzip -l deploy/internal/dist/test-agent-internal-release.zip | \
  awk 'NR > 3 {print $1, $4}' | sort -n | tail -20
unzip -Z1 deploy/internal/dist/test-agent-internal-release.zip | \
  grep '^dist/local-opencode-client/releases/' | \
  awk -F/ '{print $4}' | sort -u
```

判定规则：

- `worker runtime=reuse`：包中不应有 programs 和 worker 镜像，部署时也不应 `docker load`、重建或重启 manager/worker。
- `toolbox=reuse`：包中不应有 IT-Tools/OmniTools 镜像及 toolbox source。
- `local client=reuse`：包中不应有 `dist/local-opencode-client/`。
- `local client=included`：最终平台 ZIP 只能有当前一个 release 和当前版本用户包；本机构建输出可以保留历史 release，但历史 JDK/OpenCode、用户包和多版本 catalog 不得进入平台 ZIP。

客户端版本号本身属于组件指纹。没有客户端 JAR、启动器、下载/控制域名、签名公钥、JDK/OpenCode 输入或公共能力基线变化时，不要仅因为平台重新打包就递增 `TEST_AGENT_LOCAL_CLIENT_VERSION`，否则会把未变化客户端错误标记为 `included`。若新客户端候选已经生成但未部署，之后客户端内容再次变化，使用新的不可变版本，并确保最终 ZIP 排除未部署的中间版本。

同一 JDK/OpenCode 输入跨客户端版本应保持相同 SHA-256。版本变化导致这两个摘要变化时，先检查归档时间戳、条目顺序、属主和 gzip header，不要让用户重复下载同一运行时。

### 客户端体积异常的强制停线门禁

“当前只改前后端”时，`local client=included` 是异常信号，不是可以忽略的正常现象。生成外层包前必须完成下面的判定；不能先签发一个新客户端版本、看到包体变大后再解释：

1. 从 `.2` 的**实际安装目录**留存当前客户端版本、manifest/签名摘要和组件状态，而不是读取 Mac `dist/` 或未部署候选：

   ```bash
   awk -F'"' '$2 == "version" { print "installedClientVersion=" $4; exit }' \
     /data/testagent/dist/local-opencode-client/stable/manifest.json
   sha256sum \
     /data/testagent/dist/local-opencode-client/stable/manifest.json \
     /data/testagent/dist/local-opencode-client/stable/manifest.json.sig
   awk -F= '$1 ~ /^TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT/ { print }' \
     /data/testagent/config/release-component-state.env
   ```

2. 对比最后成功部署的客户端输入：客户端 JAR/启动器源码、下载与控制域名、组织签名公钥、JDK/OpenCode 原始输入 SHA-256、公共能力发布 commit/归档摘要。普通前端页面、后端业务代码、发布手册或客户端 README 的改动不构成重新签发客户端的理由。
3. 上述输入均未变化时，保留目标机已安装客户端的版本与五项摘要，先执行 `--component-plan-only`；预期必须是 `local client component: reuse`，最终 `release-components.env` 也必须为 `TEST_AGENT_RELEASE_LOCAL_OPENCODE_CLIENT=reuse`，且 ZIP 中不得存在 `dist/local-opencode-client/`。
4. 如果计划意外显示 `included`，立即停止打包并找出哪个输入发生变化；特别检查是否只是人为递增了 `TEST_AGENT_LOCAL_CLIENT_VERSION`。禁止为了得到较小包而手改 `.2` 的 `release-component-state.env`、伪造 baseline，或把不匹配的客户端版本标为 `reuse`。实际安装版本与构建输入不一致时，必须明确选择“重新签发并全量下发客户端”或“恢复已部署客户端的受控输入后再打平台包”。

这种门禁避免把约 200 MiB JDK 和约 60 MiB OpenCode 运行时随纯前后端发布重复传输，也避免客户端签名、下载地址或公共能力真的变化时被错误跳过。

## 4. 内外层包总是成对重建

内层 `test-agent-internal-release.zip` 每次变化后，固定名外层包必须重新生成。不能拿历史外层包仅因为它自己的 `.sha256` 仍通过就继续分发。

Mac 最终至少验证：

```bash
shasum -a 256 deploy/internal/dist/test-agent-internal-release.zip
shasum -a 256 deploy/internal/dist/test-agent-two-backend-complete.zip
unzip -p deploy/internal/dist/test-agent-two-backend-complete.zip \
  test-agent-two-backend-complete/test-agent-internal-release.zip \
  > /tmp/test-agent-embedded-release.zip
shasum -a 256 deploy/internal/dist/test-agent-internal-release.zip \
  /tmp/test-agent-embedded-release.zip
unzip -tq deploy/internal/dist/test-agent-two-backend-complete.zip
```

内层文件与外层内嵌文件摘要必须完全相同。交付目录只保留固定名外层 ZIP 和它的 SHA 文件；旧候选移出交付目录，避免 U 盘拿错。

## 5. 三套签名/密钥不能混用

### 5.1 本地客户端 release 组织 RSA 密钥

它签署 `catalog.json`、manifest、客户端 JAR、JDK、OpenCode 和公共能力包。固定私钥只在外网 Mac 的：

```text
/Users/kaka/Desktop/intelligent-test-agent/.secure/local-client-signing-private.pem
```

匹配公钥为：

```text
/Users/kaka/Desktop/intelligent-test-agent/.secure/local-client-signing-public.pem
```

私钥不在企业服务器上找，不进入 Git、U 盘、普通发布 ZIP、日志或聊天。`.secure/` 放在仓库工作目录下只是为了固定本机路径；当前通过 `.git/info/exclude` 本机忽略，不会自动分发给其他 clone。

外网 Mac 文件权限检查和配对验证：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
test -s .secure/local-client-signing-private.pem
test -s .secure/local-client-signing-public.pem
chmod 600 .secure/local-client-signing-private.pem
git check-ignore -v .secure/local-client-signing-private.pem
openssl pkey -in .secure/local-client-signing-private.pem -check -noout
openssl pkey -in .secure/local-client-signing-private.pem -pubout \
  -out /tmp/test-agent-derived-signing-public.pem
openssl pkey -pubin -in /tmp/test-agent-derived-signing-public.pem -outform DER | shasum -a 256
openssl pkey -pubin -in .secure/local-client-signing-public.pem -outform DER | shasum -a 256
rm -f /tmp/test-agent-derived-signing-public.pem
```

最后两个摘要必须相同。打包终端显式设置：

```bash
export TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY="$PWD/.secure/local-client-signing-private.pem"
export TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY="$PWD/.secure/local-client-signing-public.pem"
```

已安装客户端信任原组织公钥时，缺失私钥不能换临时密钥继续做兼容升级。选择只有两个：从授权保管人安全下发同一私钥到另一台外网构建机，或明确轮换组织密钥并让全部已安装用户执行一次全量替换。当前只有少量用户且用户明确同意清空重装时可以轮换；否则停止。任何人只要具备同一源码和受控组织私钥都能打兼容升级包，但把私钥放在本机 Git 忽略目录并不等于团队密钥分发方案；长期应使用受控签名机/签名服务或安全凭据下发。

### 5.2 Java JAR 内置 RSA 私钥

`BOOT-INF/classes/rsa-private.key` 用于平台 SSH key 混合加密兼容，不是客户端 release 签名私钥。多后台必须部署同一 JAR；随意替换它会让数据库里既有 SSH key 密文无法解密。排查客户端签名时不得生成或替换这把密钥。

### 5.3 麒麟系统软件包签名

麒麟软件管理/UKey 的系统包签名与客户端 manifest RSA 签名不是一回事。当前正式客户端是普通用户解压运行的 `TestAgent-Local-Client-Kylin-arm64.tar.gz`，只写 `~/.local`、`~/.config`，不需要 sudo，也不交付 DEB。若未来重新交付 DEB，manifest 签名不能使 DEB 通过麒麟“未签名软件包”阻止策略，必须另走企业系统包签名。

### 5.4 SkillHub Access Key

`TEST_AGENT_SKILLHUB_ACCESS_KEY` 也不是签名 key。它只保存在两台后台的敏感 `/data/testagent/config/backend.env`，Java 的 `SkillHubHttpGateway` 自动把它放入 `X-Skill-Access-Key` 请求头；不要写进 Nginx、前端、`docker.env`、普通发布包或手工 curl 历史。

启动报缺失时在 `.4`、`.114` 分别用受控编辑器补现网同一个值，再做不回显内容的检查：

```bash
grep -c '^TEST_AGENT_SKILLHUB_ACCESS_KEY=' /data/testagent/config/backend.env
awk -F= '$1=="TEST_AGENT_SKILLHUB_ACCESS_KEY" {print length(substr($0,index($0,"=")+1))>=16 ? "SET" : "INVALID"}' /data/testagent/config/backend.env
```

预期依次为 `1` 和 `SET`。不要要求用户把真实 key 发到聊天。

## 6. 增量门禁和已部署组件

目标机 `/data/testagent/config/release-component-state.env` 记录“实际安装成功”的 worker/toolbox 指纹；Mac 构建目录的状态只能说明制品生成情况，不能冒充现场已部署状态。

- 指纹缺失或不一致时先确认上一全量/定向部署是否真正完成。不能手写状态绕过。
- 若上一轮 release 已确认成功，但指纹门禁晚于实际部署，可使用仓库已有受控 baseline 文件；它必须固定上一轮源码 commit、内层 SHA 和组件指纹，并在目标机完成 runtime/容器健康验证后登记。
- `worker runtime=reuse` 表示 manager Docker、OpenCode runtime、programs 和 worker 都不动；不要因为日志写了 `reuse` 就主动重启。
- CK、Mem0、BGE、pgvector 已独立部署时，平台包只携带 Java 对它们的地址/密钥配置，不再同步数据、不重新打它们的镜像、不重启它们。部署前只做连通性和 readiness 验证。
- `.4` 单节点 `opencode-models.json` 灰度已经成功时，除非用户明确结束灰度，不把灰度文件同步到 `.114`；标准平台发布也不能把“公共模型文件存在于仓库”误解为两台都要重启 worker。
- release 默认关闭 Workflow/LobeHub 时，包和验收清单都要明确 `disabled`，不能因为当前代码仓库存在相关模块就自动部署。

## 7. Flyway 为什么总在启动时失败

常见原因不是 SQL 语法，而是比较基线错误、已执行 migration 字节被改、合并后时间戳倒序，或企业运行目录仍加载旧 `backend/lib/test-agent-persistence-*.jar`。

强制边界：

- 14 位时间戳不能代替发布编排；以目标库全部 `installed_rank/version/checksum/success` 为准。
- 已在任何要保留的库执行的 migration 连注释和空白都不能改，也不能重命名。
- 只测空库、只看 `test-agent-app.jar`、只验证外层 ZIP SHA 都不够。必须验证企业基线到 HEAD，并核对发布 ZIP 和安装目录中的外置 persistence JAR。
- 首台 `.4` 出现未知 checksum、失败记录、未知更高版本或 validate 异常时立即停止，不继续 `.114/.2`；不使用 `repair`、`outOfOrder` 或手工改 history。

即使本轮没有新增 migration，完整后端依赖仍会携带历史 Flyway SQL；这不是误打包，而是 JAR 原子完整性要求。判断数据库变化看“最后成功部署 commit 到当前 HEAD 是否新增 migration”和 JAR 内固定字节，不按 ZIP 中是否存在旧 SQL 判断。

## 8. 域名、下载和离线依赖

- TCDS 企业地址固定核对为 `http://tcds-prod.sdc.icbc:9080`；打包前检查两台 `backend.env`，不要从本地 dotenv 推断。
- 当前企业前端为 `http://mimo.sdc.cs.icbc:9996`，客户端下载根和控制服务根都必须基于该域名构建；不能先用 IP 生成包、部署后再只改 Nginx 期待客户端内嵌地址变化。
- 客户端 `curl` 拒绝连接时，先在 `.2` 本机验证 Nginx、catalog、manifest 和 installer，再从用户机验证域名解析与 `:9996`；后端健康不代表客户端下载入口已发布。
- 企业不联网，Node/MCP/插件依赖、客户端 JDK/OpenCode 和公共能力包必须在 Mac 完成封装。目标机不得执行 `npm install`、`pnpm install` 或在线下载。

## 9. 交付前的最小闭环

最终回复必须明确给出：

1. 最后成功部署基线和本次源码输入。
2. 相对该基线的真实更新点，以及明确“没有变化”的组件。
3. 内外层 SHA、外层内嵌内层 SHA 一致性。
4. `release-components.env` 中 worker/toolbox/local client 的 `included/reuse/disabled`。
5. 当前客户端版本、catalog/manifest/用户包 SHA 和签名公钥配对结论；私钥绝不输出。
6. 新增/待执行 Flyway、已验证的目标历史、包内/安装后 persistence JAR 校验点。
7. `.4 → .114 → .2` 的逐机命令、每步停止条件和浏览器功能验收。
8. 独立数据面、manager、灰度 models 和客户端是否需要动作；没有变化时明确“不重启/不重装/不重复同步”。
9. 企业命令只使用 `grep`、`awk`、`sed`、`find`、`sha256sum` 等基础工具，不使用 `rg`、`jq`。
