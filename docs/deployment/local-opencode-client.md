# 本地 OpenCode 客户端交付（麒麟正式 / Win10 候选）

## 范围、版本与停止条件

正式逐机流程当前覆盖麒麟 Linux aarch64/arm64 + glibc 的已登录用户；Windows 10 1809（build 17763）x64 已提供候选包生成能力，但完成企业 Authenticode 签名和真实 Win10 验收前不能作为正式下载包。两类平台都固定 OpenCode 1.18.4。每个 release 使用北京时间 yyyyMMddHHmmss 的 14 位版本；releases/<RELEASE_VERSION>/ 中的 JAR、JDK、OpenCode、manifest 及其签名都是不可变制品，不能原地覆盖。共享 catalog 中版本号跨平台全局唯一；每次确有客户端内容变化并生成升级 release 时，新版本必须严格大于最后已部署版本和当前分发目录中的所有版本；客户端组件未变化的整包重封继续复用原版本，不制造空升级。

当前企业入口固定为 `http://mimo.sdc.cs.icbc:9996`，因此客户端控制连接为同域 `ws://`，必须在客户端包和两台 Java 的 `backend.env` 中同时显式开启明文控制例外。该例外只适用于已批准的可信内网；入口升级 HTTPS 后，客户端 URL 改为 `https://...`，并把前后台明文开关同时恢复为 `false`。

交付路径固定为：外网 Mac 构建和签名 → U 盘只转运生成包 → 企业内部中转机 ~/Desktop/mimoagent/0709 校验并 scp → .4、.114 后台配置 → .2 前端 Nginx 发布 → 麒麟用户安装与接入。中转机不得创建或使用 /data/0709；只有目标节点使用自己的 /data/0709 接收文件。

文中 <RELEASE_VERSION> 必须替换为同一个已批准的 14 位版本，例如 20260820183000；<部署账号> 必须替换为目标节点的实际 SSH 账号。任何一步命令非零退出、摘要不匹配、文件不存在、服务不健康或页面状态异常时，立即停止，不要继续下一台机器，也不要用 repair、outOfOrder、覆盖旧 release 或重新输入/传回 Client key 来绕过问题。

## Win10 x64 候选包生成与正式闸门

Windows 候选使用同一个组织 RSA 发布密钥签署 catalog、manifest、JAR、JDK、OpenCode 和公共能力包；这只能证明平台制品完整性，不能替代 Windows 对 PE 文件的 Authenticode 校验。外网 Mac 可执行：

~~~bash
deploy/internal/package-local-opencode-client-windows.sh \
  --output-dir deploy/internal/dist/local-opencode-client \
  --version <RELEASE_VERSION> \
  --minimum-version <LAST_DEPLOYED_RELEASE_VERSION> \
  --download-base-url http://mimo.sdc.cs.icbc:9996/downloads/local-opencode-client/ \
  --server-url http://mimo.sdc.cs.icbc:9996 \
  --allow-insecure-control true \
  --signing-key .secure/local-client-signing-private.pem \
  --public-config-commit <PUBLIC_GIT_COMMIT> \
  --public-capability-bundle /secure/input/public-capabilities-<PUBLIC_GIT_COMMIT>.tar.gz
~~~

脚本固定校验 Temurin 21 Windows x64 与 OpenCode 1.18.4 Windows x64 baseline 上游 SHA-256，生成
`releases/<RELEASE_VERSION>/`、共享签名 catalog、`TestAgent-Local-Client-Win10-x64-<RELEASE_VERSION>-unsigned.zip`
和 `windows-x64-package-evidence.json`。ZIP 中 `TestAgent-Local-Client-Setup.exe` 负责最低 build 检查、签名 release 安装、
本机 enroll、当前用户任务计划和开始菜单；稳定启动器负责同平台更新、激活超时和自动回切。安装只写当前用户
`%APPDATA%` / `%LOCALAPPDATA%`，不要求管理员权限或系统 Java。

候选自检至少执行：

~~~bash
GO111MODULE=off go test ./deploy/internal/local-opencode-client/windows-launcher
deploy/internal/tests/local-opencode-client-windows-package-test.sh
unzip -tq deploy/internal/dist/local-opencode-client/TestAgent-Local-Client-Win10-x64-<RELEASE_VERSION>-unsigned.zip
~~~

正式发布前必须在受控 Windows 签名机上对 Setup 和稳定启动器完成企业 Authenticode 签名，回填签名证据并重新封装；随后在
真实 Windows 10 1809+ x64 普通用户环境验证首次安装、凭据接入、任务计划自启、托盘、工作区、OpenCode 1.18.4、断网重连、
同平台升级/回退、重复安装身份保持和卸载。当前 Nginx 正式别名仍只指向麒麟包，未签名 Windows ZIP 不得新增公开下载别名。
共享 catalog 可同时包含两类 release：麒麟启动器从新到旧选择 `linux/arm64`，Windows 只接受 `windows/x64`；服务端同样在
展示、通知、rollout 和 PREPARED 阶段执行平台隔离。现有全局策略一次只引用一个 release，另一平台需要用户覆盖或分批切换。

## 1. 外网 Mac：记录构建输入并生成签名交付包

**机器：外网 Mac（允许联网）**。以下命令在 /Users/huang/workspace/intelligent-test-agent-gitee 执行。构建使用当前工作树；git status --short 用于留存输入，不要求为空，但不得有未解决冲突，也不得清理、stash 或切换其他人的并行改动。

~~~bash
cd /Users/huang/workspace/intelligent-test-agent-gitee
git rev-parse HEAD
git status --short
test -z "$(git diff --name-only --diff-filter=U)"
~~~

成功条件：输出 HEAD 和工作树状态，最后一条无输出且退出码为 0。失败即停止构建。

**机器：外网 Mac（同一终端）**。固定本次版本、下载根、控制服务根、私钥和已审核的 ARM64 输入文件。`<RELEASE_VERSION>` 必须大于最后已部署客户端版本，不能复用或回填旧版本。package-release.sh 没有 --version 参数，版本只能通过 TEST_AGENT_LOCAL_CLIENT_VERSION 固定；不要把 --version 追加给该脚本。

~~~bash
export TEST_AGENT_LOCAL_CLIENT_VERSION=<RELEASE_VERSION>
export TEST_AGENT_LOCAL_CLIENT_MINIMUM_VERSION=<LAST_DEPLOYED_RELEASE_VERSION>
export TEST_AGENT_LOCAL_CLIENT_DOWNLOAD_BASE_URL=http://mimo.sdc.cs.icbc:9996/downloads/local-opencode-client/
export TEST_AGENT_LOCAL_CLIENT_SERVER_URL=http://mimo.sdc.cs.icbc:9996
export TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true
export TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY=/Users/kaka/Desktop/intelligent-test-agent/.secure/local-client-signing-private.pem
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
首次发布可不设置 `TEST_AGENT_LOCAL_CLIENT_MINIMUM_VERSION`；使用独立空输出目录制作升级包时必须把它设为最后已部署版本，
不能把“目录里没有旧 release”当作允许复用旧版本号。标准完整发布会再从组件状态传入同一下界。
平台首次升级且该 commit 尚无能力制品时，后端会从当前已检出的公共 Git HEAD 自动补建；超级管理员再通过
`GET /api/internal/platform/workspace-management/agent-config/public/client-capabilities/{bundleDigest}/artifact`
导出此处的完整包。禁止用空能力包或只写一个 commit 字符串代替实际基线。
公共能力包不包含 `opencode.jsonc` 或模型密钥；支持 `MANAGED_MODEL_CONFIG_V1` 的客户端在 WSS 注册后从服务端接收
无密钥 provider 配置，并只通过本机 loopback 模型中继访问平台。企业来源的 OpenCode provider 名称和模型来自当前
`OPENCODE_PUBLIC_CONFIG_DIR/opencode.jsonc`，`options.headers.X-Enterprise-Model-Provider` 必须精确对应数据库中已启用且
已配置 Token 的 `internal_model_providers.provider_id`，不能回退为历史默认 `enterprise-openai`。发布验收必须确认旧客户端
仍可注册、新客户端的 `REGISTERED` 不泄露平台地址、统一认证号或上游密钥。

**机器：外网 Mac（同一终端）**。执行真实打包，使用独立输出目录，避免污染固定 deploy/internal/dist。

~~~bash
cd /Users/huang/workspace/intelligent-test-agent-gitee
deploy/internal/package-release.sh --local-client-only --no-zip --output-dir /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/catalog.json
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/catalog.json.sig
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.tar.gz
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.tar.gz
test -f /Users/huang/workspace/intelligent-test-agent-gitee/deploy/internal/dist-local-client-<RELEASE_VERSION>/local-opencode-client/releases/<RELEASE_VERSION>/manifest.json
~~~

成功条件：脚本退出 0，五个文件均存在，且不带版本号的麒麟 ARM64 下载别名与本次版本化用户包逐字节相同。脚本会把组件状态中的已部署版本传给客户端打包入口，并同时扫描输出目录中的历史 release；本次版本不严格升高时会在下载和签名前停止。输入 JDK/OpenCode 摘要、release 签名及 catalog 发布顺序仍全部失败关闭。

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
TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true
TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2
~~~

**机器：122.233.30.4 后台（同一终端）**。只验证键和值是否存在，不回显公钥或任何其他敏感配置：

~~~bash
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true' /data/testagent/config/backend.env
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

在该文件中确认或加入以下精确键；公钥值必须与 .4 和用户包内安装脚本固化的信任公钥配对：

~~~dotenv
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=<完整公钥_PEM_的单行_Base64>
TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json
TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true
TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2
~~~

**机器：122.233.30.114 后台（同一终端）**。执行校验和重启：

~~~bash
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json' /data/testagent/config/backend.env
sudo grep -Fx 'TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true' /data/testagent/config/backend.env
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
test -f "$local_client_stage_dir/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.tar.gz"
test -f "$local_client_stage_dir/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.tar.gz"
cmp -s "$local_client_stage_dir/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.tar.gz" "$local_client_stage_dir/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.tar.gz"
~~~

成功条件：摘要输出 OK，五个文件均存在且两个用户包逐字节相同。失败即停止并保留现有 /data/testagent/dist/local-opencode-client/ 不变。

**机器：122.233.30.2 前端（同一终端）**。先放置 versioned release，若同版本已经存在则必须逐字节相同；然后才替换 catalog、stable manifest、启动器和用户包。下面的 mv 仅移动由上一条 mktemp 创建的精确 staging 路径。

~~~bash
sudo install -d -m 0755 /data/testagent/dist/local-opencode-client/releases
if test -e /data/testagent/dist/local-opencode-client/releases/<RELEASE_VERSION>; then sudo diff -qr "$local_client_stage_dir/local-opencode-client/releases/<RELEASE_VERSION>" /data/testagent/dist/local-opencode-client/releases/<RELEASE_VERSION>; else sudo mv "$local_client_stage_dir/local-opencode-client/releases/<RELEASE_VERSION>" /data/testagent/dist/local-opencode-client/releases/<RELEASE_VERSION>; fi
sudo install -d -m 0755 /data/testagent/dist/local-opencode-client/stable
sudo install -m 0755 "$local_client_stage_dir/local-opencode-client/install.sh" /data/testagent/dist/local-opencode-client/install.sh
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/stable/manifest.json" /data/testagent/dist/local-opencode-client/stable/manifest.json
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/stable/manifest.json.sig" /data/testagent/dist/local-opencode-client/stable/manifest.json.sig
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.tar.gz" /data/testagent/dist/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.tar.gz
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.tar.gz" /data/testagent/dist/local-opencode-client/TestAgent-Local-Client-Kylin-arm64.tar.gz
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/catalog.json.sig" /data/testagent/dist/local-opencode-client/catalog.json.sig
sudo install -m 0644 "$local_client_stage_dir/local-opencode-client/catalog.json" /data/testagent/dist/local-opencode-client/catalog.json
~~~

成功条件：若同版本存在，diff -qr 没有输出且退出 0；若不存在，release 目录移动成功。catalog.json 必须最后落盘，从而不会先公开指向不完整 release 的 catalog。任一失败即停止，不要执行 Nginx reload。

**机器：122.233.30.2 前端（同一终端）**。使用受控的实体 Nginx 配置，脚本会渲染、语法校验、确认 include 并 reload；不要改用 PATH 中可能读取其他主配置的 nginx：

~~~bash
sudo /data/testagent/deploy/internal/configure-nginx.sh --env-file /data/testagent/config/nginx.env --template /data/testagent/deploy/internal/nginx/gateway.conf.template
curl -fsSI http://127.0.0.1/downloads/local-opencode-client/catalog.json
curl -fsSI http://127.0.0.1/downloads/local-opencode-client/releases/<RELEASE_VERSION>/manifest.json
curl -fsSI http://127.0.0.1/downloads/local-opencode-client/test-agent-local-client_<RELEASE_VERSION>_arm64.tar.gz
curl -fsSIL http://127.0.0.1/downloads/local-opencode-client/installer
~~~

成功条件：配置脚本打印已安装的 gateway，前三个 curl 返回 200，`/installer` 只重定向并最终下载为
`TestAgent-Local-Client-Kylin-arm64.tar.gz`。配置中 /downloads/local-opencode-client/ 必须映射到
/data/testagent/dist/local-opencode-client/；下载服务只暴露制品，不暴露 API 或目录索引。失败即停止，脚本会恢复旧 Nginx 配置。

**机器：平台网页（超级管理员）**。在 Nginx 三个 URL 都返回 200 后，进入本地客户端版本管理页面，执行“同步 release/catalog”。成功条件：页面显示 <RELEASE_VERSION> 的已同步 release，且 platform=linux、architecture=arm64、签名校验成功。同步失败或版本不兼容时停止，不能创建 rollout。

**机器：122.233.30.2 前端（完成后）**。仅在上述验证成功后清理本次精确 staging 目录：

~~~bash
case "$local_client_stage_dir" in
  /data/testagent/dist/.local-opencode-client-stage.*) sudo rm -rf "$local_client_stage_dir" ;;
  *) echo "Refusing unsafe cleanup: $local_client_stage_dir" >&2; exit 1 ;;
esac
~~~

成功条件：local_client_stage_dir 是本节 mktemp 生成的 /data/testagent/dist/.local-opencode-client-stage.* 目录。若变量为空、路径不匹配或不确定，停止并人工检查，不要扩大删除范围。

## 7. 麒麟 ARM 用户节点：普通用户安装与 enroll

**机器：真实麒麟 ARM 用户节点，普通账号**。从网页下载并完整解压用户包，不运行 `dpkg`，也不需要 sudo：

~~~bash
client_package="$HOME/下载/TestAgent-Local-Client-Kylin-arm64.tar.gz"
file "$client_package"
tar -tzf "$client_package"
~~~

成功条件：`file` 识别为 gzip compressed data，归档中只有 `TestAgent-Local-Client/` 下的原生启动器、README 和
`resources/`。不要直接在压缩包预览窗口内运行；完整解压后双击 `TestAgent-Local-Client`。文件管理器若禁止双击
可执行文件，可在解压目录打开终端执行 `./TestAgent-Local-Client`，该命令不需要 sudo。

原生启动器会校验包内安装脚本和图标摘要，在可见终端中执行首次 `setup`，随后把客户端、运行时、配置、桌面入口和
user systemd 服务安装到当前账号的 `~/.local` / `~/.config`。运行时 JAR、JDK、OpenCode 和公共能力基线仍按平台 RSA
签名清单逐项验证；只在本机输入统一认证号和 Client key，不要把 Key 放到命令、环境变量、URL、截图或日志中。
用户包不会写 `/usr` 或 `/var/lib/dpkg`，因此不再生成或交付 DEB。

同一普通用户再次双击新版安装包时仍执行 `setup`：启动器必须重新下载并验证签名 catalog，安装缺失的最新不可变
release，原子切换 `current`，保留原 `credentials.properties`、`state.json`、工作区根映射和 OpenCode 数据，并对
已有 user systemd service 执行 restart。若客户端此前因服务端拒绝旧 Key 写入 `state/re-enrollment-required`，
重复安装必须先在本机重新执行 enroll，成功后删除标记再重启；不能因旧 `credentials.properties` 仍存在而跳过。
验收时在重复安装前后记录 `state.json` 内的 `clientInstanceId`、
`credentials.properties` 摘要和下方 `--version` 输出；前两者必须不变，版本必须更新到 catalog 最新值。
JDK、OpenCode 和公共能力归档未变化时，打包结果保持确定性摘要。稳定安装器和客户端自更新共用
`<installRoot>/artifact-cache/<KIND>/<SHA-256>/artifact{,.sig}` 内容缓存，不再把某个旧 release 是否保留作为命中条件。
每次命中都按新 manifest 重新校验类型、大小、SHA-256，并用当前内置发布公钥验签；缺失、摘要变化或验签失败时只下载
对应制品并原子修复该缓存项。已有旧 release 仅作为首次升级填充共享缓存的兼容来源。新 release 仍生成完整独立副本，
不能跨目录硬链接或跳过签名。因而仅客户端 JAR 变化时，除 catalog/manifest 及其签名等控制元数据外，制品层只下载新 JAR。
发布前应对比上一 release 与新 release manifest：输入未变化时 `JDK`、`OPENCODE` 和 `PUBLIC_CAPABILITIES` 的
`sha256` 必须分别一致；不一致表示缓存必然无法命中，应停止发布并排查归档确定性。安装回归必须同时覆盖两种情况：
删除旧 release 后升级仍只请求实际变化的制品；单项缓存损坏只重新请求该项；再次安装同一 release 时除 catalog 外不再请求 release 制品。
若实例 ID 变化，应先检查安装脚本与 Java 进程是否
使用同一 `TEST_AGENT_LOCAL_CLIENT_CONFIG_DIR/STATE_DIR`，不得通过伪造数据库实例归属代替修复。

用户从托盘主动“退出”时，user systemd 的 `Restart=on-failure` 会尊重正常退出，不自动拉起。需要恢复时从应用菜单
打开“Test Agent 本地客户端”，其桌面入口执行稳定启动器 `start`，重新安装/刷新 user service 并发起连接；若检测到
上述失效标记，则先交互式重新接入。也可在当前用户终端执行：

~~~bash
"$HOME/.local/bin/test-agent-local-client" start
~~~

**机器：同一普通用户的真实交互终端，仅用于验收状态**。应用菜单完成首次接入后执行：

~~~bash
systemctl --user is-active --quiet test-agent-local-opencode-client.service
systemctl --user status test-agent-local-opencode-client.service --no-pager
journalctl --user -u test-agent-local-opencode-client.service --since '5 minutes ago' --no-pager
"$HOME/.local/bin/test-agent-local-client" --version
~~~

成功条件：user systemd service 为 active，日志没有认证或 WSS 连接失败，版本显示已安装 release。首次 enroll 的
短连接得到 REGISTERED 后才写入 0700 配置目录与 0600 credentials.properties；普通重启、更新、回退和自动回切均复用它们。
若输入 Client key 后安装退出，先按终端显示的日志绝对路径查看 `client.log`：新版会写入
`local_client_command_failed`，并用 `AUTHENTICATION_REJECTED`、`PLATFORM_CONNECTION_FAILED`、
`PLATFORM_REJECTED` 或 `PLATFORM_PROTOCOL_INVALID` 区分凭据/限流、用户机到入口链路、平台稳定错误码和协议版本问题。
该日志不会记录统一认证号、Client key、服务端错误正文或 details。`PLATFORM_CONNECTION_FAILED` 必须从真实用户机验证
到 `mimo.sdc.cs.icbc:9996` 的链路，并检查 Nginx Upgrade 路由；`PLATFORM_REJECTED` 应按同一时间窗和其中的稳定错误码
检查 `.4/.114` 后端日志，不能反复索取 Key 或用开发者本机连通代替企业侧证据。

### 客户端日志定位

麒麟默认日志目录为 `~/.local/state/testagent/local-opencode-client/logs/`，Windows 默认目录为
`%LOCALAPPDATA%\TestAgent\local-opencode-client\state\logs\`；若部署时显式覆盖
`TEST_AGENT_LOCAL_CLIENT_STATE_DIR`，则统一查看该目录下的 `logs/`。排查顺序如下：

1. 先看 `launcher.log`，按 `session`、`stage` 和 `event` 判断失败发生在平台检查、签名制品下载/校验、release 切换、
   Java/OpenCode 自检、enroll、自启还是更新激活；达到 5 MiB 后上一份为 `launcher-1.log`。
2. 再看 `client.log`，用 `session=trace_client_*`、`requestId`、`traceId`、generation、`failureCode`、`rootFailureType` 和
   `durationMs` 对齐 `.4/.114` 后台同一时间窗。Windows 安装器早期失败还可先看 `windows-launcher-error.log` 的阶段摘要。
3. `opencode.log` 可能包含工作区上下文，不进入托盘日志包；只有在用户授权且确有必要时在本机查看，不应作为普通排障附件。

麒麟最近日志可执行：

~~~bash
tail -n 200 "$HOME/.local/state/testagent/local-opencode-client/logs/launcher.log"
tail -n 200 "$HOME/.local/state/testagent/local-opencode-client/logs/client.log"
~~~

Windows PowerShell 最近日志可执行：

~~~powershell
Get-Content "$env:LOCALAPPDATA\TestAgent\local-opencode-client\state\logs\launcher.log" -Tail 200
Get-Content "$env:LOCALAPPDATA\TestAgent\local-opencode-client\state\logs\client.log" -Tail 200
~~~

日志只记录受控元数据，不记录统一认证号、Client key/token、认证头、服务端响应正文、异常 message、prompt、请求正文或
工作区文件内容。若日志中出现 `REDACTED`，应使用相邻事件的安全错误码和 traceId 排查，禁止要求用户补发原始敏感值。

客户端完成重连认证后，平台会优先恢复全局最近项，再扫描该用户包含已替换实例在内的全部历史本地工作区，逐个核验真实
路径摘要与文件系统身份并恢复客户端根映射，不要求用户在页面重复选择。若历史版本已经生成了新实例 ID，同一自动恢复流程会在
当前唯一在线客户端上保留原 workspaceId 接管每个有效绑定；某个目录已删除或身份变化时只跳过该项，不影响其它目录恢复。
目录已经移动时再从客户端托盘重新选择原目录。
旧实例在线、身份不一致、同目录存在多个历史 Workspace 或该用户出现多个在线 route 时必须先停止并人工消除歧义。

**机器：同一普通用户的已登录平台页面**。打开个人设置中的本地客户端实例列表，确认有且只有一个实例，且
online=true、connectionGeneration 为正数，并能看到当前版本和 SELF_UPDATE_V1 能力。新实例完成认证后，后台会在同一用户
行锁内删除其它实例的 Redis route、模型 grant 并关闭物理连接；设置页只展示这一条实时连接，不展示离线历史。此项与本机
active user service 一起证明 WS 已建立；若任一项失败，停止 rollout，先检查域名 `:9996` 的 Nginx 下载/API/Upgrade 路由、
.4/.114 的明文控制开关与 TRUSTED_PROXY_ADDRESSES，以及后台健康日志。不得要求用户重新把已经输入的 Key 发给任何运维人员。

全新企业环境可以尚未设置本地客户端全局/个人目标版本；这时版本策略表为空属于合法状态，客户端必须保持在线，
不能为了通过接入验收写入假策略或修改 `flyway_schema_history`。若日志表现为反复 `REGISTERED` 后立即断线，先确认
服务端未把 `targetVersion=null/policyRevision=0` 下发为 `VERSION_POLICY`；新版客户端日志会以安全
`failureCode=VERSION_POLICY_INVALID` 标识旧协议错误，不输出异常正文。

## 8. 数据库与真实现场闸门

本手册的文件、Nginx、首次 enroll 和 WSS/实例检查不替代数据库准入。发布前仍必须按照 [database.md](database.md) 对每套已知真实 PostgreSQL 历史留存 flyway_schema_history 的 version/checksum/success，验证升级到当前 HEAD，并检查两条固定 migration 在源码、构建输出、发布 ZIP 和安装后 backend/lib/test-agent-persistence-*.jar 中的字节。未完成真实 PostgreSQL 升级或真实麒麟 ARM64 glibc 全链路测试时，它们必须继续标记为发布闸门，不能表述为已通过。

Observability 现场验收还必须确认用户包引用的受控 release 包含共享插件，客户端注册 capability
`OPENCODE_OBSERVABILITY_V1`，对话期间 WSS 没有 Trace 上传，空闲 3 秒后才出现单在途分片。断网后 spool 保留，重连按
服务器 watermark 续传；只有匹配 ACK 后文件才删除。空间不足时客户端必须继续对话并报告 degraded/incomplete。不得通过
清理用户 spool、调高超过 1 MiB/s 的速率或降低 3 秒空闲门槛来使验收表面通过。

新版客户端发布单元必须内置与明确公共 Git commit 对应的完整 `public-capabilities.tar.gz`。执行
`package-local-opencode-client.sh` 时 `--public-config-commit` 和 `--public-capability-bundle` 都是必填项；脚本读取包内
`public-capabilities/manifest.json` 校验 commit，并把该制品、摘要和签名作为 release 的
`PUBLIC_CAPABILITIES` artifact。禁止复制本机 `.testagent`、公共仓库原始 `node_modules` 或在目标机执行 npm 下载。

客户端启动受管 OpenCode 时固定设置 `npm_config_offline=true` 和 `OPENCODE_DISABLE_MODELS_FETCH=true`，目标机无需也不允许
访问 npm registry 或 models.dev。公共能力包必须在外网构建阶段携带完整依赖闭包；离线设置只让用户全局或历史配置目录的
缺失依赖快速失败，不会放宽 `/experimental/tool/ids`、`/agent`、`/command` 验收。若现场日志持续出现
`local_opencode_catalog_check_failed` 约 30 秒超时、`public_capability_activation_validation_failed` 后反复重启，先核对
客户端版本是否包含该离线运行约束及 release 是否携带完整能力包，不要重新索取或传递已经接入成功的 Client key。

本地浏览器 Tool 上线时，在权威公共配置 Git 的 `tools/local_browser.ts` 使用仓库
`deploy/internal/local_browser.ts` 同步模板并形成明确提交，再由现有公共能力包流程构建、签名和下发；禁止直接修改客户端
不可变能力目录。外网构建的 programs 必须包含锁定的 `playwright-core@1.61.0`，内网目标机不下载 Chromium，也不执行 npm。
目标麒麟 ARM64 用户桌面必须已安装企业 360 浏览器；托盘“浏览器设置与自检”自动发现失败时由用户选择浏览器可执行文件。

真实现场发布闸门必须以同一普通用户完成：启动本地 OpenCode，调用 Tool 打开测试站点并确认出现逐 origin 授权；验证语义点击、
输入、多标签、最多 4 个 Session、跨 origin 阻断，以及提交/上传/下载逐次确认；截图和下载分别落入当前工作区
`browser-artifacts/<session-id>/`、`browser-downloads/<session-id>/`。同时确认浏览器使用独立 profile 且窗口可见，退出客户端后
受管浏览器停止。Mac 开发机的类型检查和 relay 测试不能替代麒麟 ARM64 + 企业 360 的这道闸门。

首次启动从安装 release 自动初始化该基线。后续公共版本只生成完整包并发送通知；用户必须在托盘或网页确认，平台不能
自动确认。Agent/Skill-only 变化热加载，Tool/依赖变化重启本地 OpenCode；Tool 始终使用当前登录用户权限，不提权。
候选包经安全解压、文件/内容摘要、目录接口和 OpenCode 健康校验后才原子切换，失败回到上一不可变版本。公共版本
`SERVER_ONLY` 时不下发，客户端继续使用上一 `AVAILABLE` 版本。

`public-capabilities.tar.gz` 是 release 内与 JAR 并列、分别签名的 sidecar 制品，不打进 JAR classpath。现场只覆盖
`test-agent-local-client.jar` 不会清空或替换 `state/public-capabilities` 的当前版本，也不能用于验证公共配置更新；新装必须
发布完整 release，存量实例必须由平台生成“公共能力有更新”并由用户在托盘或网页确认。禁止直接覆盖不可变 release 中
的 JAR 或能力包，否则 manifest 摘要和签名会失效。

客户端 Swing 窗口统一使用 shaded JAR 内置的 FlatLaf 3.7.2，不再读取 macOS Aqua、JDK Nimbus/Metal 或麒麟桌面主题；
目标机不需要也禁止现场下载 UI 依赖。发布验收除签名与版本外，还应确认 JAR 包含
`com/formdev/flatlaf/FlatLightLaf.class`，并在真实麒麟桌面检查首次配置、目录选择、会话进度和公共能力确认窗口。

能力包 manifest 的 `contentDigest` 只表示文件内容，安装版本的 `bundleDigest` 固定按
`sha256(sourceCommit + "\n" + contentDigest)` 计算。同一内容的新公共提交仍必须生成独立完整包并显示该提交哈希；
首版客户端已经安装的文件摘要型 `bundleDigest` 继续兼容读取，但新发布包不得再使用旧算法。

Mac 人工验收可以直接构建 shaded JAR，但首次基线行为仍需构造安装目录
`<installRoot>/releases/<clientVersion>/public-capabilities.tar.gz`；在线更新则由 WSS 分片下发，不从下载 URL 获取。
验收必须同时检查 `/agent`、Skill command、`/experimental/tool/ids`、真实对话 Tool 调用、重启后版本保留、公共 Agent
不重复以及服务器工作区行为未变化，不能用服务器受保护 Agent 的执行结果冒充 `runtimeKind=LOCAL_CLIENT`。
