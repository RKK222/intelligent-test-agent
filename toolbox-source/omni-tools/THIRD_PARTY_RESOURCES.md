# 离线第三方运行资源

以下资源只在联网构建机获取，版本与校验值随修改源码交付；容器运行时不会访问这些来源。

| 能力 | 锁定来源/版本 | 本地路径 | SHA-256 / 校验方式 |
| --- | --- | --- | --- |
| Ghostscript WASM | `@jspawn/ghostscript-wasm@0.1.2` | `runtime-assets/ghostscript/gs-worker.wasm` | `2d093d358b088dfa678ad3edc1ccaddfbd5d42addecb452b4d16e5bd13db0232` |
| Tesseract 英文 | `tessdata_best` | `runtime-assets/tesseract/lang/eng.traineddata.gz` | `ed350f3752f81ee8f38769edc14d92d997dababe23b565c59879372cc46a2468` |
| Tesseract 简体中文 | `tessdata_best` | `runtime-assets/tesseract/lang/chi_sim.traineddata.gz` | `59388039851e4d1293d729c183fd8c1fa9bbbb959eed996e945024671e68c1d6` |
| Tesseract 繁体中文 | `tessdata_best` | `runtime-assets/tesseract/lang/chi_tra.traineddata.gz` | `67a4357a69810bf1596e801cd95299e073030cacb0899e78d9923d2fa1185704` |
| IMG.LY 抠图模型与 ONNX WASM | `@imgly/background-removal-data@1.7.0` | `runtime-assets/imgly/` | `resources.json` SHA-256 为 `365f54a52994efd52b3d3d3ba2318af68d280df19f214f2f1f1cc87368f7adfa`；26 个分片以文件名中的 SHA-256 逐块校验 |

FFmpeg `@ffmpeg/core`、Tesseract JS/core、PDF worker、ONNX runtime 和 IMG.LY JavaScript 包来自 `package-lock.json` 锁定的 npm 依赖，并由 `scripts/copy-runtime-assets.mjs` 复制到 `dist/runtime/`。许可证与依赖声明保留在完整源码和 lockfile 中。
