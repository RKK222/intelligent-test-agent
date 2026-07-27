# 平台修改说明

- Vite、Vue Router 与静态资源基址固定为 `/toolbox/apps/it-tools/`。
- 路由只保留目录中的具体工具；根路径和未知路径返回平台 `/toolbox`，摄像头录制不注册。
- 工具布局替换为约 40px 的平台轻顶栏，移除上游 Logo、Navbar、首页、分类入口、面包屑、收藏、相关推荐、支持链接、统计器和页脚。
- 移除 PWA 与 Plausible 运行时装配；CSP 不允许任何外部源。上游转换器使用动态表达式，因此脚本保留 `unsafe-eval`/`wasm-unsafe-eval`，并仅允许同源与 `blob:` 脚本。
- Figlet 289 个字体在构建期复制到镜像，运行时不访问 `unpkg.com`；锁定信息和许可证见 `THIRD_PARTY_RESOURCES.md`。
- 剪贴板增加 HTTP 环境下的 `execCommand('copy')` 降级实现。
- Nginx 支持具体工具深链接刷新、不可变资源缓存和 `/healthz` 健康检查。
