import { beforeEach, describe, expect, it, vi } from "vitest";

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

  it("keeps global save feedback above full-screen reference dialogs", () => {
    notifySuccess("引用配置已保存");

    expect(message).toHaveBeenCalledWith(expect.objectContaining({
      type: "success",
      customClass: "ta-top-message",
      zIndex: 12050
    }));
  });
});
