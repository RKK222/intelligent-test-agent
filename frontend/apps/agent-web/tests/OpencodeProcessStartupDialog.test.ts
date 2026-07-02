import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import OpencodeProcessStartupDialog from "../src/components/OpencodeProcessStartupDialog.vue";

describe("OpencodeProcessStartupDialog", () => {
  it("shows failed startup step, clear failure reason, and trace id", () => {
    const wrapper = mount(OpencodeProcessStartupDialog, {
      props: {
        open: true,
        actionLabel: "启动进程",
        operation: {
          operationId: "opi_1234567890abcdef",
          status: "FAILED",
          currentStep: "HEALTH_CHECKING",
          errorCode: "OPENCODE_UNAVAILABLE",
          errorMessage: "启动后 10 秒内未通过健康检查：connection refused",
          traceId: "trace_1234567890abcdef",
          steps: [
            { code: "VALIDATING_REQUEST", name: "校验请求", status: "SUCCEEDED" },
            { code: "HEALTH_CHECKING", name: "健康检查", status: "FAILED" }
          ]
        }
      }
    });

    expect(wrapper.text()).toContain("启动进程");
    expect(wrapper.text()).toContain("健康检查");
    expect(wrapper.text()).toContain("OPENCODE_UNAVAILABLE");
    expect(wrapper.text()).toContain("启动后 10 秒内未通过健康检查：connection refused");
    expect(wrapper.text()).toContain("trace_1234567890abcdef");
    expect(wrapper.find(".ta-process-startup-step.is-failed").exists()).toBe(true);
  });
});
