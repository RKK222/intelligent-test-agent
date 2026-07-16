import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import OcCopyButton from "../src/opencode-like/components/primitives/OcCopyButton.vue";

const secureContextDescriptor = Object.getOwnPropertyDescriptor(window, "isSecureContext");
const clipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, "clipboard");
const execCommandDescriptor = Object.getOwnPropertyDescriptor(document, "execCommand");

function restoreProperty(target: object, key: PropertyKey, descriptor?: PropertyDescriptor) {
  if (descriptor) Object.defineProperty(target, key, descriptor);
  else Reflect.deleteProperty(target, key);
}

describe("OcCopyButton", () => {
  afterEach(() => {
    restoreProperty(window, "isSecureContext", secureContextDescriptor);
    restoreProperty(navigator, "clipboard", clipboardDescriptor);
    restoreProperty(document, "execCommand", execCommandDescriptor);
    document.querySelectorAll("textarea[aria-hidden='true']").forEach((element) => element.remove());
    vi.restoreAllMocks();
  });

  it("falls back to execCommand for an HTTP enterprise entry", async () => {
    Object.defineProperty(window, "isSecureContext", { configurable: true, value: false });
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: undefined });
    const execCommand = vi.fn().mockReturnValue(true);
    Object.defineProperty(document, "execCommand", { configurable: true, value: execCommand });
    const wrapper = mount(OcCopyButton, { props: { value: "企业内复制内容" } });

    await wrapper.get("button").trigger("click");
    await vi.waitFor(() => expect(wrapper.get("button").attributes("aria-label")).toBe("已复制"));

    expect(execCommand).toHaveBeenCalledWith("copy");
    expect(document.querySelector("textarea[aria-hidden='true']")).toBeNull();
  });

  it("uses the same fallback when the async Clipboard API rejects permission", async () => {
    Object.defineProperty(window, "isSecureContext", { configurable: true, value: true });
    const writeText = vi.fn().mockRejectedValue(new Error("permission denied"));
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });
    const execCommand = vi.fn().mockReturnValue(true);
    Object.defineProperty(document, "execCommand", { configurable: true, value: execCommand });
    const wrapper = mount(OcCopyButton, { props: { value: "retry with fallback" } });

    await wrapper.get("button").trigger("click");
    await vi.waitFor(() => expect(wrapper.get("button").attributes("aria-label")).toBe("已复制"));

    expect(writeText).toHaveBeenCalledWith("retry with fallback");
    expect(execCommand).toHaveBeenCalledWith("copy");
  });
});
