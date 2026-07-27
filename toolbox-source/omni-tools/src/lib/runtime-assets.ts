const runtimeBase = `${import.meta.env.BASE_URL}runtime/`;

export function runtimeAsset(path: string): string {
  return `${runtimeBase}${path.replace(/^\//, '')}`;
}

/** 所有 FFmpeg 核心文件由构建脚本复制到同源目录，运行时禁止 CDN 回退。 */
export function ffmpegLoadOptions() {
  return {
    coreURL: runtimeAsset('ffmpeg/ffmpeg-core.js'),
    wasmURL: runtimeAsset('ffmpeg/ffmpeg-core.wasm')
  };
}

export const tesseractRuntime = {
  workerPath: runtimeAsset('tesseract/worker.min.js'),
  corePath: runtimeAsset('tesseract/core'),
  langPath: runtimeAsset('tesseract/lang'),
  gzip: true
};

// IMG.LY 使用 URL 构造器读取资源清单，因此这里必须提供带 origin 的绝对同源地址。
export const backgroundRemovalPublicPath = new URL(
  runtimeAsset('imgly/'),
  window.location.origin
).toString();
