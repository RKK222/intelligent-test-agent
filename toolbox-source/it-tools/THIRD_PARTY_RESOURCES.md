# 离线第三方运行资源

IT-Tools 的 ASCII 文本工具使用 `figlet@1.7.0` 随 npm 包发布的 289 个 `.flf` 字体。联网构建阶段由 `pnpm-lock.yaml` 中固定的完整性值安装依赖，再由 `scripts/copy-runtime-assets.mjs` 复制到 `dist/fonts/`；容器运行时不会访问上游字体地址。

| 能力 | 锁定来源/版本 | 本地运行路径 | 校验与许可证 |
| --- | --- | --- | --- |
| ASCII Figlet 字体 | `figlet@1.7.0` | `dist/fonts/*.flf` | `pnpm-lock.yaml` 完整性值 `sha512-gO8l3wvqo0V7wEFLXPbkX83b7MVjRrk1oRLfYlZXol8nEpb/ON9pcKLI4qpBv5YtOTfrINtqb7b40iYY2FTWFg==`；MIT 许可证见 `THIRD_PARTY_LICENSES/figlet-LICENSE.txt` |

其余 JavaScript 依赖同样由 `pnpm-lock.yaml` 固定。该目录保存应用完整修改源码和上游 GPL-3.0 许可证；`node_modules` 仅为构建缓存，不进入源码归档。
