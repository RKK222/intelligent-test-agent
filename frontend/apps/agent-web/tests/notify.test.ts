import { beforeEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const elementOverridesSource = readFileSync(
  resolve(process.cwd(), "apps/agent-web/src/styles/element-overrides.css"),
  "utf8"
);

const { message, notification } = vi.hoisted(() => ({
  message: vi.fn(),
  notification: vi.fn()
}));

vi.mock("element-plus", () => ({
  ElMessage: message,
  ElNotification: notification
}));

import { notifySuccess } from "../src/components/notify";

describe("notify", () => {
  beforeEach(() => {
    message.mockReset();
    notification.mockReset();
  });

  it("uses the common top-message presentation for save feedback", () => {
    notifySuccess("引用配置已保存");

    expect(message).toHaveBeenCalledWith(expect.objectContaining({
      type: "success",
      customClass: "ta-top-message"
    }));
  });

  it("overrides Element Plus inline stacking so feedback stays above reference dialogs", () => {
    expect(elementOverridesSource).toMatch(
      /\.el-message\.ta-top-message\s*\{[^}]*z-index:\s*12050\s*!important;/s
    );
  });
});
