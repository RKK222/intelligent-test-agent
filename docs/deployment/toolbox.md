# 工具盒子离线部署

## 交付边界

工具盒子是平台左侧活动栏中的登录用户入口，平台目录页和点击 API 受现有登录认证保护；具体工具静态页面不再执行工具级鉴权，由前端 Nginx 直接代理到独立工具节点。工具卡片始终打开具体深链接，不暴露 IT-Tools 或 OmniTools 的上游首页、分类门户、Logo、菜单、收藏、推荐、支持链接或统计器。

首版固定以下上游版本，禁止改用 `latest`：

- `IT-Tools 2024.10.22-7ca5933`，内部镜像 `test-agent/it-tools:2024.10.22-7ca5933-platform.1`，GPL-3.0。
- `OmniTools 0.6.0`，内部镜像 `test-agent/omni-tools:0.6.0-platform.1`，MIT。

版本化目录位于 `backend/test-agent-integration/src/main/resources/toolbox/catalog-v1.json`，当前 `catalogVersion` 为 `2026-07-27.it-tools-2024.10.22-7ca5933.omni-tools-0.6.0`，共 193 项：IT-Tools 85 项、OmniTools 108 项。源码原始统计的 195 项中，以下两项不满足企业 HTTP 离线环境，禁止提供入口：

- IT-Tools 摄像头录制依赖安全上下文和摄像头权限。
- OmniTools PDF Editor 实际嵌入 `react-editor.simplepdf.com`，不是离线实现。

完整修改源码、上游提交号、许可证、平台修改说明和第三方资源清单保存在 `toolbox-source/`。该目录不加入现有前端 pnpm workspace，也不得只交付编译产物而遗漏 GPL 对应源码。

## 访问拓扑

```text
Browser
  -> frontend Nginx
      -> /toolbox                         agent-web SPA
      -> /api/internal/platform/toolbox  test-agent-app
      -> /toolbox/apps/it-tools/*        toolbox node :18120
      -> /toolbox/apps/omni-tools/*      toolbox node :18121
```

外层 Nginx 在 SPA catch-all 前匹配两个工具前缀，并把前缀剥离后代理到工具容器。派生应用自身仍以完整浏览器前缀配置 Vite base 和 Router base，因此静态资源请求、浏览器历史和直接刷新深链接保持一致。精确套件根路径 `/toolbox/apps/it-tools` 与 `/toolbox/apps/omni-tools` 返回 `308 /toolbox`；派生应用根路径及未知路由也只能回到平台工具盒子或显示工具不可用页。

工具节点使用专用 Docker bridge，并关闭 `com.docker.network.bridge.enable_ip_masquerade`。不能使用 Docker 的 `--internal` 标志：它会同时阻断独立节点对宿主 `18120/18121` 的端口发布，使前端 Nginx 无法访问。容器只运行静态 Nginx，不注入平台密钥；浏览器侧 CSP 只允许同源、`blob:` 和 `data:`。独立节点还必须通过主机防火墙把 TCP 18120/18121 的来源限制为前端 Nginx 主机，并限制容器出站访问，不能只依赖 Docker 网络选项。Docker Desktop 的虚拟机网络实现可能不会按 Linux 生产主机的方式执行该 bridge 选项，因此 Mac 验收环境也不能把它视为出站隔离边界。

## 离线产物

联网构建机执行：

```bash
deploy/internal/package-release.sh --toolbox-only --output-dir /absolute/output
```

完整企业发布仍直接执行 `deploy/internal/package-release.sh`，工具产物会进入 `test-agent-internal-release.zip`。工具盒子产物包括：

- 两个 `linux/amd64` 镜像 tar 及各自 SHA-256。
- `test-agent-toolbox-source.tar.gz` 及 SHA-256，包含完整修改源码、许可证、补丁说明和本地化资源清单，不包含 `node_modules`、`dist` 或上游 `.git`。
- `toolbox-catalog-v1.json` 及 SHA-256。
- `toolbox.env.example`、`toolbox-docker.sh`、`diagnose-toolbox.sh` 和本部署说明。

IT-Tools Docker 构建显式锁定 Node 20.18.0、pnpm 8.15.3 和 Nginx 1.27.2；OmniTools 显式锁定 Node 20.18.0、`package-lock.json` 和 Nginx 1.27.2。两个 Dockerfile 的 Node/Nginx 基础镜像还锁定对应的 `linux/amd64` manifest digest，避免同名 tag 漂移。OmniTools 构建关闭 npm audit/fund 网络请求并使用 BuildKit npm 缓存，但依赖版本仍只由锁文件决定。上游 v0.6.0 的 npm 依赖审计目前仍报告既有漏洞，禁止对锁定源码直接运行 `npm audit fix`；升级必须作为独立上游版本评估，重新执行全部 193 路由和真实功能验收。

若联网构建机必须直连经批准的镜像代理，可用 `TEST_AGENT_TOOLBOX_NODE_BASE_IMAGE`、`TEST_AGENT_TOOLBOX_NGINX_BASE_IMAGE` 覆盖 registry 前缀，但值仍必须带上述同一 `linux/amd64` digest；禁止降级为仅 tag。发布脚本把这两个值作为 Docker build arg 传入，两套应用使用完全相同的基础层。

## 工具节点部署

1. 把两个镜像 tar、`.sha256`、部署脚本和 `toolbox.env.example` 放到工具节点，例如 `/data/testagent/dist` 与 `/data/testagent/deploy/internal`。
2. 复制示例为 `/data/testagent/config/toolbox.env`。独立节点把 `TEST_AGENT_TOOLBOX_BIND_ADDRESS` 设置为该节点供前端 Nginx 访问的私网 IP；同机 Nginx 才使用 `127.0.0.1`。
3. 配置主机防火墙，仅允许前端 Nginx 主机访问 18120/18121。
4. 执行部署和诊断：

```bash
TEST_AGENT_TOOLBOX_ENV_FILE=/data/testagent/config/toolbox.env \
  /data/testagent/deploy/internal/toolbox-docker.sh deploy
TEST_AGENT_TOOLBOX_ENV_FILE=/data/testagent/config/toolbox.env \
  /data/testagent/deploy/internal/diagnose-toolbox.sh
```

脚本先校验 tar SHA-256 和镜像 `amd64` 架构，再用无宿主端口的预检容器等待健康；两个候选镜像都通过后才替换固定容器：

- `test-agent-it-tools`：`18120:80`。
- `test-agent-omni-tools`：`18121:80`。

容器使用只读根文件系统、受限 tmpfs、`cap-drop ALL` 后最小 Nginx 能力、`no-new-privileges`、日志轮转、健康检查和 `unless-stopped`。运行参数兼容企业现有 Docker 18.09；脚本先加载并校验镜像架构，不依赖较新版本的 `docker run --pull`。状态命令为 `toolbox-docker.sh status`，停止命令为 `toolbox-docker.sh stop`。

## 前端 Nginx

前端机 `/data/testagent/config/nginx.env` 必须配置：

```dotenv
TEST_AGENT_NGINX_TOOLBOX_IT_TOOLS_UPSTREAM=<toolbox-private-ip>:18120
TEST_AGENT_NGINX_TOOLBOX_OMNI_TOOLS_UPSTREAM=<toolbox-private-ip>:18121
```

`configure-nginx.sh` 会要求并校验两个单一 `host:port` endpoint，不再默认回退到 `127.0.0.1`。`configure-single-deployment.sh frontend` 首次执行必须通过两个 `--toolbox-*-upstream` 参数提供地址，后续执行可从现有 `nginx.env` 保留；然后渲染 `nginx/gateway.conf.template`，执行 Nginx 配置检查后再 reload。两个 location 必须位于 SPA catch-all 前，不能把套件根路径代理到上游门户。

## 本地开发联调

本机加载并启动两个工具镜像后，继续按项目统一方式启动平台：

```bash
./restart-dev-services.sh --profile test --env-file .env.test
```

Vite 默认把 `/toolbox/apps/it-tools/*`、`/toolbox/apps/omni-tools/*` 分别代理到 `http://127.0.0.1:18120/18121`，并与生产 Nginx 一样剥离公开前缀和阻止套件根门户。因此本地统一入口是 `http://127.0.0.1:3000/toolbox`，不需要额外启动 Nginx。工具容器不在本机时，可在执行启动脚本前临时设置 `TEST_AGENT_TOOLBOX_IT_TOOLS_URL` 与 `TEST_AGENT_TOOLBOX_OMNI_TOOLS_URL`；不要为此修改含敏感信息的 `.env.local`。

Apple Silicon 会模拟首版锁定的 `linux/amd64` 镜像，首次打开个别懒加载工具会比生产 amd64 主机慢。全目录浏览器冒烟已为该冷启动场景保留 60 秒导航、30 秒工具渲染窗口；这不会改变生产请求超时。

## 上线顺序与回滚

固定上线顺序：

1. 工具节点校验 tar、加载镜像、预检并确认两个容器健康，尚不修改前端 Nginx。
2. 发布后端，Flyway 建表并确认目录/点击 API 正常。
3. 发布 agent-web 静态资源，渲染前端 Nginx，执行配置检查、深链接探测后 reload。
4. 逐项验证 `/toolbox`、热门区、点击上报、两套工具深链接和浏览器前进/后退。

工具部署前只有在两套当前容器都存在时才会成对保存 rollback 标签；仅存在一套容器会在替换前失败，首次部署则清除历史遗留的 rollback 标签。自动或人工回滚都要求两套镜像齐全且两个恢复容器均健康，任一失败会停止两者，禁止返回半套成功。人工回滚执行 `toolbox-docker.sh rollback`。前端切流失败时恢复上一版 agent-web/Nginx 配置，不能让 Nginx 指向未健康的新工具节点。数据库表和新增 API 均为向后兼容新增，回滚前端/工具镜像时无需回退 Flyway。

## 安全与可观测性

- 平台 `/toolbox` 和两个目录/点击 API 沿用登录认证，不校验角色；工具静态路径不增加认证，不得承载平台 Token 或用户业务数据。
- 点击时间、用户和 traceId 由服务端取得；客户端只提交最长 128 字符的 `eventId`。
- 点击明细永久保留。数据库监控必须加入 `toolbox_tool_click_events` 行数、表/索引容量和增长速率；首版没有清理任务。
- 用户删除时，历史事件 `user_id` 置空，用户/工具 30 秒窗口状态级联删除，累计值保留。
- 两套工具 CSP 不允许任何外部源：Worker、图片、媒体和连接仅按功能开放同源、`blob:`、`data:`。脚本仅允许同源与 `blob:`；因上游转换器、IMG.LY/ONNX 动态模块和图像解码器使用动态表达式，保留 `unsafe-eval`/`wasm-unsafe-eval`。OmniTools 额外返回 COOP/COEP 以支持本地 FFmpeg/Worker。
- 构建产物断网验收必须拦截所有非同源请求，并逐一加载目录的 193 条路径。Figlet、FFmpeg、Ghostscript、OCR 中/英文语言包、背景移除模型、Monaco Worker 和 Iconify 均为本地资源；OmniTools 图片压缩关闭会通过 `importScripts` 访问 CDN 的默认 Worker，图片编辑器关闭在线翻译服务。

## 验收命令

```bash
python3 toolbox-source/scripts/generate_catalog.py \
  --it-tools toolbox-source/it-tools \
  --omni-tools toolbox-source/omni-tools \
  --output /tmp/toolbox-catalog-v1.json
cmp /tmp/toolbox-catalog-v1.json backend/test-agent-integration/src/main/resources/toolbox/catalog-v1.json
python3 toolbox-source/scripts/verify_platform_contract.py
node toolbox-source/scripts/smoke_toolbox_routes.mjs
node toolbox-source/scripts/smoke_toolbox_features.mjs
bash -n deploy/internal/toolbox-docker.sh
bash -n deploy/internal/diagnose-toolbox.sh
bash -n deploy/internal/package-release.sh
tools/verify-internal-nginx-config.sh
```

两个 Node 冒烟脚本运行前要按 `toolbox-source/README.md` 启动已构建的本地预览服务。路由冒烟必须得到 IT-Tools 85、OmniTools 108、合计 193，且非同源请求数为零。真实功能冒烟覆盖 ASCII 字体、HTTP 文本复制降级、HTTP 二进制复制隐藏、两种图片压缩、图片编辑器、FFmpeg 音频处理、Ghostscript PDF、OCR 和 AI 抠图；仅看到页面可以打开不能替代这些功能验收。

已通过 `restart-dev-services.sh` 启动 3000 统一入口时，可直接验证真实本地链路：

```bash
node toolbox-source/scripts/smoke_toolbox_routes.mjs \
  --it-origin http://127.0.0.1:3000/toolbox/apps/it-tools/ \
  --omni-origin http://127.0.0.1:3000/toolbox/apps/omni-tools/
TEST_AGENT_TOOLBOX_IT_ORIGIN=http://127.0.0.1:3000 \
TEST_AGENT_TOOLBOX_OMNI_ORIGIN=http://127.0.0.1:3000 \
  node toolbox-source/scripts/smoke_toolbox_features.mjs
```

最终镜像启动后还必须让真实功能冒烟经过容器 Nginx，以验证 CSP 响应头（包括 QR 工具所需的 `connect-src data:`）：

```bash
TEST_AGENT_TOOLBOX_IT_ORIGIN=http://127.0.0.1:18120 \
TEST_AGENT_TOOLBOX_OMNI_ORIGIN=http://127.0.0.1:18121 \
  node toolbox-source/scripts/smoke_toolbox_features.mjs
```
