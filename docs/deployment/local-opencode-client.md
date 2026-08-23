# 麒麟 ARM 本地 OpenCode 客户端逐机交付

## 范围、版本与停止条件

本交付仅支持麒麟 Linux aarch64/arm64 + glibc 的已登录用户，不支持 macOS、Windows、非 glibc 系统、开机未登录即运行或稳定 Shell 自更新。OpenCode 固定为 1.18.4。每个 release 使用北京时间 yyyyMMddHHmmss 的 14 位版本；releases/<RELEASE_VERSION>/ 中的 JAR、JDK、OpenCode、manifest 及其签名都是不可变制品，不能原地覆盖。

交付路径固定为：外网 Mac 构建和签名 → U 盘只转运生成包 → 企业内部中转机 ~/Desktop/mimoagent/0709 校验并 scp → .4、.114 后台配置 → .2 前端 Nginx 发布 → 麒麟用户安装与接入。中转机不得创建或使用 /data/0709；只有目标节点使用自己的 /data/0709 接收文件。

文中 <RELEASE_VERSION> 必须替换为同一个已批准的 14 位版本，例如 20260820183000；<部署账号> 必须替换为目标节点的实际 SSH 账号。任何一步命令非零退出、摘要不匹配、文件不存在、服务不健康或页面状态异常时，立即停止，不要继续下一台机器，也不要用 repair、outOfOrder、覆盖旧 release 或重新输入/传回 Client key 来绕过问题。

## 1. 外网 Mac：记录构建输入并生成签名交付包

**机器：外网 Mac（允许联网）**。以下命令在 /Users/huang/workspace/intelligent-test-agent-gitee 执行。构建使用当前工作树；git status --short 用于留存输入，不要求为空，但不得有未解决冲突，也不得清理、stash 或切换其他人的并行改动。

~~~bash
cd /Users/huang/workspace/intelligent-test-agent-gitee
git rev-parse HEAD
git status --short
test -z "$(git diff --name-only --diff-filter=U)"
~~~

成功条件：输出 HEAD 和工作树状态，最后一条无输出且退出码为 0。失败即停止构建。

**机器：外网 Mac（同一终端）**。固定本次版本、下载根、控制服务根、私钥和已审核的 ARM64 输入文件。package-release.sh 没有 --version 参数，版本只能通过 TEST_AGENT_LOCAL_CLIENT_VERSION 固定；不要把 --version 追加给该脚本。

~~~bash
export TEST_AGENT_LOCAL_CLIENT_VERSION=<RELEASE_VERSION>
export TEST_AGENT_LOCAL_CLIENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/
export TEST_AGENT_LOCAL_CLIENT_SERVER_URL=https://122.233.30.2
export TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY=/secure/local-client-signing-private.pem
export TEST_AGENT_LOCAL_CLIENT_JDK_LINUX_ARM64_GLIBC_ARCHIVE=/secure/input/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.9_10.tar.gz
export TEST_AGENT_LOCAL_CLIENT_OPENCODE_LINUX_ARM64_GLIBC_ARCHIVE=/secure/input/opencode-linux-arm64.tar.gz
export TEST_AGENT_LOCAL_CLIENT_PUBLIC_CONFIG_COMMIT=<PUBLIC_GIT_COMMIT>
export TEST_AGENT_LOCAL_CLIENT_PUBLIC_CAPABILITY_BUNDLE=/secure/input/public-capabilities-<PUBLIC_GIT_COMMIT>.tar.gz
test -r /secure/local-client-signing-private.pem
test -r /secure/input/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.9_10.tar.gz
test -r /secure/input/opencode-linux-arm64.tar.gz
test -r "${TEST_AGENT_LOCAL_CLIENT_PUBLIC_CAPABILITY_BUNDLE}"
~~~

成功条件：四个 `test -r` 均返回 0，公共 commit 是已经审核发布的 40-64 位十六进制固定提交，能力包 manifest 中的
`sourceCommit` 与它完全一致。私钥只可留在该外网 Mac 的受控目录，绝不放入仓库、交付包、U 盘、日志或聊天。
平台首次升级且该 commit 尚无能力制品时，后端会从当前已检出的公共 Git HEAD 自动补建；超级管理员再通过
`GET /api/internal/platform/workspace-management/agent-config/public/client-capabilities/{bundleDigest}/artifact`
导出此处的完整包。禁止用空能力包或只写一个 commit 字符串代替实际基线。
公共能力包不包含 `opencode.jsonc` 或模型密钥；支持 `MANAGED_MODEL_CONFIG_V1` 的客户端在 WSS 注册后从服务端接收
无密钥 provider 配置，并只通过本机 loopback 模型中继访问平台。发布验收必须确认旧客户端仍可注册、新客户端的
`REGISTERED` 不泄露平台地址、统一认证号或上游密钥。

**机器：外网 Mac（同一终端）**。执行真实打包，使用独立输出目录，避免污染固定 deploy/internal/dist。

~~~bash
cd /Users/huang/workspace/intelligent-test-agent-gitee
deploy/internal/package-release.sh --local-client-only --no-zip --output-dir /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/catalog.json
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/catalog.json.sig
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.deb
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/releases/<RELEASE_VERSION>/manifest.json
~~~

成功条件：脚本退出 0，五个文件均存在，且不带版本号的麒麟 ARM64 下载别名与本次版本化 DEB 逐字节相同。脚本会对输入 JDK/OpenCode 摘要、release 签名及 catalog 发布顺序失败关闭。

**机器：外网 Mac（同一终端）**。把生成目录封装为 U 盘只转运的一个压缩包和一个摘要文件；包内顶层必须是 local-opencode-client/。

~~~bash
cd /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>
tar -czf /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz local-opencode-client
shasum -a 256 /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz > /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz.sha256
cat /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz.sha256
~~~

成功条件：只得到 test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz 及同名 .sha256，并记录显示的 SHA-256。失败即停止；不要把私钥或原始上游压缩包拷入 U 盘。

## 2. U 盘：仅转运已生成的两个文件

**机器：外网 Mac，然后是企业内部中转机**。U 盘只复制上一节生成的以下两个文件，不从 Mac 直接向企业目标机 scp：

~~~text
/Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz
/Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz.sha256
~~~

成功条件：中转机 U 盘挂载后的两个文件名与摘要文件中名称一致。缺任一文件即停止，返回外网 Mac 重新生成。

## 3. 企业内部中转机：校验并分发至目标节点

**机器：企业内部中转机**。从 U 盘把两个文件复制到固定目录 ~/Desktop/mimoagent/0709；不要创建 /data/0709。随后执行：

~~~bash
cd ~/Desktop/mimoagent/0709
sha256sum -c test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz.sha256
scp ~/Desktop/mimoagent/0709/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz <部署账号>@122.233.30.2:/data/0709/
scp ~/Desktop/mimoagent/0709/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz.sha256 <部署账号>@122.233.30.2:/data/0709/
~~~

成功条件：第一条输出 OK，两条 scp 均退出 0。该本地客户端制品仅需要发送到 .2 前端节点；.4、.114 后台节点在下一节只修改自己已有的 /data/testagent/config/backend.env。任何摘要或传输失败即停止，不要继续后台或前端配置。

## 4. 122.233.30.4 后台：配置版本发现与安全控制

**机器：122.233.30.4 后台**。编辑唯一生产配置文件，不要使用根目录 .env.local，也不要 source 含密钥的 dotenv 文件：

~~~bash
sudoedit /data/testagent/config/backend.env
~~~

在该文件中确认或加入以下精确键。TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64 是外网 Mac 签名私钥的匹配公钥 PEM 的单行 Base64，不是私钥，也不得填写 REPLACE_ 占位符。

~~~dotenv
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=<完整公钥_PEM_的单行_Base64>
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json
TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=false
TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2
~~~

**机器：122.233.30.4 后台（同一终端）**。只验证键和值是否存在，不回显公钥或任何其他敏感配置：

~~~bash
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=false' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2' /data/testagent/config/backend.env
sudo awk -F= '$1 == "TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64" && $2 != "" && $2 !~ /^REPLACE_/ { print "local-client signing public key configured" }' /data/testagent/config/backend.env
sudo systemctl restart test-agent-backend
sudo systemctl is-active --quiet test-agent-backend
curl -fsS http://127.0.0.1:8080/actuator/health
~~~

成功条件：四个 grep 都回显精确行，awk 只回显配置成功文字，服务为 active，health 返回成功 JSON。任一失败即保持 .4 停止，不要继续 .114。

## 5. 122.233.30.114 后台：重复配置并确认健康

**机器：122.233.30.114 后台**。编辑该节点自己的配置文件：

~~~bash
sudoedit /data/testagent/config/backend.env
~~~

在该文件中确认或加入以下精确键；公钥值必须与 .4 和 DEB 内置的信任公钥配对：

~~~dotenv
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=<完整公钥_PEM_的单行_Base64>
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json
TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=false
TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2
~~~

**机器：122.233.30.114 后台（同一终端）**。执行校验和重启：

~~~bash
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=false' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2' /data/testagent/config/backend.env
sudo awk -F= '$1 == "TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64" && $2 != "" && $2 !~ /^REPLACE_/ { print "local-client signing public key configured" }' /data/testagent/config/backend.env
sudo systemctl restart test-agent-backend
sudo systemctl is-active --quiet test-agent-backend
curl -fsS http://127.0.0.1:8080/actuator/health
~~~

成功条件：与 .4 相同，所有命令返回成功。任一失败即停止，不要发布 .2 Nginx，也不要让用户开始 enroll。

## 6. 122.233.30.2 前端：解包、发布 Nginx 并同步 catalog

**机器：122.233.30.2 前端**。先校验收到的文件，再解包到独立 staging 目录。此步骤不会覆盖现有 immutable release：

~~~bash
cd /data/0709
sha256sum -c test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz.sha256
sudo install -d -m 0755 /data/testagent/dist
local_client_stage_dir="$(sudo mktemp -d /data/testagent/dist/.local-opencode-client-stage.XXXXXX)"
sudo tar -xzf /data/0709/test-agent-local-opencode-client_<RELEASE_VERSION>_arm64.tar.gz --no-same-owner -C "$local_client_stage_dir"
test -f "$local_client_stage_dir/local-opencode-client/catalog.json"
test -f "$local_client_stage_dir/local-opencode-client/catalog.json.sig"
test -f "$local_client_stage_dir/local-opencode-client/releases/<RELEASE_VERSION>/manifest.json"
test -f "$local_client_stage_dir/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.deb"
test -f "$local_client_stage_dir/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb"
cmp -s "$local_client_stage_dir/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.deb" "$local_client_stage_dir/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb"
~~~

成功条件：摘要输出 OK，五个文件均存在且两个 DEB 逐字节相同。失败即停止并保留现有 /data/testagent/dist/local-opencode-client/ 不变。

**机器：122.233.30.2 前端（同一终端）**。先放置 versioned release，若同版本已经存在则必须逐字节相同；然后才替换 catalog、stable manifest、启动器和 DEB。下面的 mv 仅移动由上一条 mktemp 创建的精确 staging 路径。

~~~bash
sudo install -d -m 0755 /data/testagent/dist/local-opencode-client/releases
if test -e /data/testagent/dist/local-opencode-client/releases/<RELEASE_VERSION>; then sudo diff -qr "$local_client_stage_dir/local-opencode-client/releases/<RELEASE_VERSION>" /data/testagent/dist/local-opencode-client/releases/<RELEASE_VERSION>; else sudo mv "$local_client_stage_dir/local-opencode-client/releases/<RELEASE_VERSION>" /data/testagent/dist/local-opencode-client/releases/<RELEASE_VERSION>; fi
sudo install -d -m 0755 /data/testagent/dist/local-opencode-client/stable
sudo install -m 0755 "$local_client_stage_dir/local-opencode-client/install.sh" /data/testagent/dist/local-opencode-client/install.sh
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/stable/manifest.json" /data/testagent/dist/local-opencode-client/stable/manifest.json
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/stable/manifest.json.sig" /data/testagent/dist/local-opencode-client/stable/manifest.json.sig
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.deb" /data/testagent/dist/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.deb
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb" /data/testagent/dist/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.deb
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/catalog.json.sig" /data/testagent/dist/local-opencode-client/catalog.json.sig
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/catalog.json" /data/testagent/dist/local-opencode-client/catalog.json
~~~

成功条件：若同版本存在，diff -qr 没有输出且退出 0；若不存在，release 目录移动成功。catalog.json 必须最后落盘，从而不会先公开指向不完整 release 的 catalog。任一失败即停止，不要执行 Nginx reload。

**机器：122.233.30.2 前端（同一终端）**。使用受控的实体 Nginx 配置，脚本会渲染、语法校验、确认 include 并 reload；不要改用 PATH 中可能读取其他主配置的 nginx：

~~~bash
sudo /data/testagent/deploy/internal/configure-nginx.sh --env-file /data/testagent/config/nginx.env --template /data/testagent/deploy/internal/nginx/gateway.conf.template
curl -fsSI http://127.0.0.1/downloads/local-opencode-client/catalog.json
curl -fsSI http://127.0.0.1/downloads/local-opencode-client/releases/<RELEASE_VERSION>/manifest.json
curl -fsSI http://127.0.0.1/downloads/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.deb
curl -fsSIL http://127.0.0.1/downloads/local-opencode-client/installer
~~~

成功条件：配置脚本打印已安装的 gateway，前三个 curl 返回 200，`/installer` 只重定向并最终下载 `TestAgent-Local-Client-Kylin-arm64.deb`。配置中 /downloads/local-opencode-client/ 必须映射到 /data/testagent/dist/local-opencode-client/；下载服务只暴露制品，不暴露 API 或目录索引。失败即停止，脚本会恢复旧 Nginx 配置。

**机器：平台网页（超级管理员）**。在 Nginx 三个 URL 都返回 200 后，进入本地客户端版本管理页面，执行“同步 release/catalog”。成功条件：页面显示 <RELEASE_VERSION> 的已同步 release，且 platform=linux、architecture=arm64、签名校验成功。同步失败或版本不兼容时停止，不能创建 rollout。

**机器：122.233.30.2 前端（完成后）**。仅在上述验证成功后清理本次精确 staging 目录：

~~~bash
case "$local_client_stage_dir" in
  /data/testagent/dist/.local-opencode-client-stage.*) sudo rm -rf "$local_client_stage_dir" ;;
  *) echo "Refusing unsafe cleanup: $local_client_stage_dir" >&2; exit 1 ;;
esac
~~~

成功条件：local_client_stage_dir 是本节 mktemp 生成的 /data/testagent/dist/.local-opencode-client-stage.* 目录。若变量为空、路径不匹配或不确定，停止并人工检查，不要扩大删除范围。

## 7. 麒麟 ARM 用户节点：管理员安装与普通用户 enroll

**机器：麒麟 ARM 用户节点，管理员账户**。只下载并安装本次精确版本的 DEB；不要把 Client key 放到该命令、环境变量或安装日志中。

~~~bash
curl -fS --proto '=http' http://122.233.30.2/downloads/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.deb -o /var/tmp/test-agent-local-client_<RELEASE_VERSION>_arm64.deb
dpkg-deb -f /var/tmp/test-agent-local-client_<RELEASE_VERSION>_arm64.deb Package Version Architecture
sudo dpkg -i /var/tmp/test-agent-local-client_<RELEASE_VERSION>_arm64.deb
test -x /usr/bin/test-agent-local-client
/usr/bin/test-agent-local-client --version
~~~

成功条件：dpkg-deb 输出 Package: test-agent-local-client、Version: <RELEASE_VERSION>、Architecture: arm64，dpkg -i 成功，最后一条显示 release=not-installed（首次安装尚未由用户下载 runtime）或已有受控 release。非 ARM64/aarch64 或非 glibc 设备必须在启动器检查失败时停止。

**机器：同一麒麟节点的普通登录用户，不能使用 sudo**。在真实交互终端执行：

~~~bash
test-agent-local-client enroll
systemctl --user is-active --quiet test-agent-local-opencode-client.service
systemctl --user status test-agent-local-opencode-client.service --no-pager
journalctl --user -u test-agent-local-opencode-client.service --since '5 minutes ago' --no-pager
test-agent-local-client --version
~~~

enroll 会交互提示统一认证号和隐藏 Client key；只在用户本机输入，绝不把 Key 作为命令行参数、环境变量、URL、截图、日志或聊天内容回传。成功条件：命令显示“本地客户端重新接入成功”或“本地客户端接入认证成功”，user systemd service 为 active，日志没有认证或 WSS 连接失败，版本显示已安装 release。首次 enroll 的短连接得到 REGISTERED 后才写入 0700 配置目录与 0600 credentials.properties；普通重启、更新、回退和自动回切均复用它们。

**机器：同一普通用户的已登录平台页面**。打开个人设置中的本地客户端实例列表，确认该实例 online=true、connectionGeneration 为正数，并能看到当前版本和 SELF_UPDATE_V1 能力。此项与本机 active user service 一起证明 WSS 已建立；若任一项失败，停止 rollout，先检查 .2 的 Nginx 下载/HTTPS-WSS 路由、.4/.114 的 TRUSTED_PROXY_ADDRESSES 和后台健康日志。不得要求用户重新把已经输入的 Key 发给任何运维人员。

## 8. 数据库与真实现场闸门

本手册的文件、Nginx、首次 enroll 和 WSS/实例检查不替代数据库准入。发布前仍必须按照 [database.md](database.md) 对每套已知真实 PostgreSQL 历史留存 flyway_schema_history 的 version/checksum/success，验证升级到当前 HEAD，并检查两条固定 migration 在源码、构建输出、发布 ZIP 和安装后 backend/lib/test-agent-persistence-*.jar 中的字节。未完成真实 PostgreSQL 升级或真实麒麟 ARM64 glibc 全链路测试时，它们必须继续标记为发布闸门，不能表述为已通过。

Observability 现场验收还必须确认 DEB 的受控 release 包含共享插件，客户端注册 capability
`OPENCODE_OBSERVABILITY_V1`，对话期间 WSS 没有 Trace 上传，空闲 3 秒后才出现单在途分片。断网后 spool 保留，重连按
服务器 watermark 续传；只有匹配 ACK 后文件才删除。空间不足时客户端必须继续对话并报告 degraded/incomplete。不得通过
清理用户 spool、调高超过 1 MiB/s 的速率或降低 3 秒空闲门槛来使验收表面通过。

新版客户端发布单元必须内置与明确公共 Git commit 对应的完整 `public-capabilities.tar.gz`。执行
`package-local-opencode-client.sh` 时 `--public-config-commit` 和 `--public-capability-bundle` 都是必填项；脚本读取包内
`public-capabilities/manifest.json` 校验 commit，并把该制品、摘要和签名作为 release 的
`PUBLIC_CAPABILITIES` artifact。禁止复制本机 `.testagent`、公共仓库原始 `node_modules` 或在目标机执行 npm 下载。

首次启动从安装 release 自动初始化该基线。后续公共版本只生成完整包并发送通知；用户必须在托盘或网页确认，平台不能
自动确认。Agent/Skill-only 变化热加载，Tool/依赖变化重启本地 OpenCode；Tool 始终使用当前登录用户权限，不提权。
候选包经安全解压、文件/内容摘要、目录接口和 OpenCode 健康校验后才原子切换，失败回到上一不可变版本。公共版本
`SERVER_ONLY` 时不下发，客户端继续使用上一 `AVAILABLE` 版本。

能力包 manifest 的 `contentDigest` 只表示文件内容，安装版本的 `bundleDigest` 固定按
`sha256(sourceCommit + "\n" + contentDigest)` 计算。同一内容的新公共提交仍必须生成独立完整包并显示该提交哈希；
首版客户端已经安装的文件摘要型 `bundleDigest` 继续兼容读取，但新发布包不得再使用旧算法。

Mac 人工验收可以直接构建 shaded JAR，但首次基线行为仍需构造安装目录
`<installRoot>/releases/<clientVersion>/public-capabilities.tar.gz`；在线更新则由 WSS 分片下发，不从下载 URL 获取。
验收必须同时检查 `/agent`、Skill command、`/experimental/tool/ids`、真实对话 Tool 调用、重启后版本保留、公共 Agent
不重复以及服务器工作区行为未变化，不能用服务器受保护 Agent 的执行结果冒充 `runtimeKind=LOCAL_CLIENT`。
