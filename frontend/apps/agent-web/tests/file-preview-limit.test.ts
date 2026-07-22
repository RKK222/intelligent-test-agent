import { describe, expect, it } from "vitest";
import { BackendApiError } from "@test-agent/backend-api";
import { formatPreviewBytes, progressivePreviewRequired } from "../src/components/fileProgressivePreview";

describe("file progressive preview", () => {
  it("maps the explicit backend marker to the progressive preview threshold", () => {
    const error = new BackendApiError(500, {
      success: false,
      code: "VALIDATION_ERROR",
      message: "文件超过预览大小限制",
      traceId: "trace_preview",
      details: {
        reason: "PREVIEW_TOO_LARGE",
        size: 7 * 1024 * 1024,
        maxPreviewBytes: 5 * 1024 * 1024
      }
    });

    expect(progressivePreviewRequired(error)).toEqual({
      size: 7 * 1024 * 1024,
      warningThresholdBytes: 5 * 1024 * 1024
    });
    expect(formatPreviewBytes(5 * 1024 * 1024)).toBe("5.0 MiB");
  });

  it("does not hide unrelated validation failures", () => {
    const error = new BackendApiError(500, {
      success: false,
      code: "VALIDATION_ERROR",
      message: "路径无效",
      traceId: "trace_other",
      details: {}
    });

    expect(progressivePreviewRequired(error)).toBeUndefined();
  });
});
