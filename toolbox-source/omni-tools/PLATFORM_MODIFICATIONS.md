# 平台修改说明

- Vite 与 React BrowserRouter 基址固定为 `/toolbox/apps/omni-tools/`。
- 路由只保留目录中的具体工具；根路径、分类路径和未知路径返回平台 `/toolbox`。
- 剔除依赖 `react-editor.simplepdf.com` 的 PDF Editor；离线目录不提供失效入口。
- 工具布局替换为约 40px 的平台轻顶栏，移除上游 Logo、Navbar、首页、分类入口、面包屑、收藏、相关推荐和页脚。
- 移除运行时 Iconify 网络图标依赖；CSP 不允许任何外部源。IMG.LY/ONNX 的动态模块、图像解码器及上游表单表达式要求脚本保留 `blob:`、`unsafe-eval` 与 `wasm-unsafe-eval`，`connect-src` 保留 QR 生成所需的 `data:`。
- FFmpeg core、Ghostscript WASM、Tesseract worker/core/中英文语言包和 IMG.LY `isnet_fp16` 模型全部同源交付。
- Monaco Editor 使用 npm 包与 Vite 本地 Worker，不再加载 jsDelivr loader。
- 两个图片压缩工具关闭 `browser-image-compression` 的默认 CDN Worker，改用已打包的主线程实现。
- 图片编辑器关闭后端翻译服务，使用依赖包内置文案，避免运行时请求外部翻译 API。
- OCR 语言选择限制为已随包交付的英文、简体中文和繁体中文，禁止静默回退 CDN。
- 抠图固定使用 CPU + `isnet_fp16`，避免运行时选择未交付模型；资源分片按官方 SHA-256 校验。
- 文本剪贴板增加 HTTP 降级；二进制剪贴板在不安全上下文隐藏不可用操作，避免直接访问缺失 API。Nginx 增加 COOP/COEP、深链接刷新和健康检查。
