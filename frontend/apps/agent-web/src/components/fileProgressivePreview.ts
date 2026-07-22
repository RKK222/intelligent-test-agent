import { BackendApiError } from "@test-agent/backend-api";

export type ProgressivePreviewRequired = {
  size: number;
  warningThresholdBytes: number;
};

/** 只识别后端明确标记的大文件整读错误，随后切换到可读完整文件的渐进分段协议。 */
export function progressivePreviewRequired(error: unknown): ProgressivePreviewRequired | undefined {
  if (!(error instanceof BackendApiError) || error.details.reason !== "PREVIEW_TOO_LARGE") {
    return undefined;
  }
  const size = Number(error.details.size);
  const warningThresholdBytes = Number(error.details.maxPreviewBytes);
  if (!Number.isFinite(size)
    || size < 0
    || !Number.isFinite(warningThresholdBytes)
    || warningThresholdBytes < 1) {
    return undefined;
  }
  return { size, warningThresholdBytes };
}

export function formatPreviewBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KiB", "MiB", "GiB", "TiB"];
  const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / (1024 ** unitIndex);
  return `${value >= 10 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[unitIndex]}`;
}
