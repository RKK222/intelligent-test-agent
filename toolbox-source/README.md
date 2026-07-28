# 工具盒子上游源码区

本目录保存企业工具盒子首版的两套锁定、可审计、可重复构建的派生源码，不加入平台前端 pnpm workspace：

- `it-tools/`：IT-Tools `v2024.10.22-7ca5933`，GPL-3.0，中文派生平台镜像 `test-agent/it-tools:2024.10.22-7ca5933-platform.2`。
- `omni-tools/`：OmniTools `v0.6.0`，MIT，平台镜像 `test-agent/omni-tools:0.6.0-platform.1`。

两套应用都只暴露具体工具页：根路径和未知路由返回平台 `/toolbox`，上游首页、导航、Logo、收藏、相关推荐、支持链接、统计器与页脚均不作为可访问入口。浏览器侧 CSP 禁止访问外部 CDN/API；容器使用关闭 IP masquerade 的专用 bridge，生产工具节点还必须通过离线网络或宿主防火墙限制出站访问。

IT-Tools 派生应用沿用上游 `vue-i18n`，但运行时固定使用中文且不显示语言选择器；中英文 locale 叶子 key 必须保持一致。85 条目录路由的控件、校验、提示和说明文档受构建期可见英文审计保护，算法、协议、格式、单位、代码和生成内容等技术文本按精确白名单保留。

## 目录生成

目录以锁定源码为唯一输入，固定排除 HTTP 环境无法使用的摄像头录制工具：

```bash
python3 toolbox-source/scripts/generate_catalog.py \
  --it-tools toolbox-source/it-tools \
  --omni-tools toolbox-source/omni-tools \
  --output backend/test-agent-integration/src/main/resources/toolbox/catalog-v1.json
```

输出必须为 193 项：除 HTTP 安全上下文限制的摄像头录制外，还剔除运行时依赖外部托管 iframe 的 SimplePDF 编辑器。稳定 ID 与启动路径只由脚本生成，不允许人工调整顺序或临时改名。

## 构建与交付

```bash
deploy/internal/package-release.sh --toolbox-only --output-dir deploy/internal/dist
```

构建平台固定 `linux/amd64`，两套 Dockerfile 的 Node 与 Nginx 基础镜像同时锁定版本标签和对应的 `linux/amd64` manifest digest。完整企业发布包同时携带两个镜像 tar、逐文件 SHA-256、本目录完整修改源码、许可证、资源说明和目录 JSON。`node_modules/` 与 `dist/` 只是本机构建缓存，不进入 Git 或源码归档。

OmniTools 的 IMG.LY 模型资源可按固定版本重新获取并逐块校验：

```bash
python3 toolbox-source/scripts/fetch_imgly_runtime.py \
  --output toolbox-source/omni-tools/runtime-assets/imgly
```

## 本地统一入口验收

两个工具容器监听本机 `18120/18121` 后，平台 Vite 开发服务器会代理完整公开前缀。使用 `restart-dev-services.sh` 启动平台后可让全目录和真实功能冒烟直接经过 `http://127.0.0.1:3000`，具体命令见 `docs/deployment/toolbox.md`。路由脚本对 Apple Silicon 模拟 `linux/amd64` 镜像的首次加载采用 60 秒导航和 30 秒工具渲染窗口，断言内容、路由数量和非同源请求限制保持不变。
