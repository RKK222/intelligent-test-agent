import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import RuntimeDock from "../src/RuntimeDock.vue";

describe("RuntimeDock", () => {
  it("shares the native permission presentation and decision order", async () => {
    const onReplyPermission = vi.fn();
    const externalPath = "/Users/huang/.testagent/agent-opencode/references/*";
    const rendered = render(RuntimeDock, {
      props: {
        permissions: [{
          requestId: "per_internal",
          sessionId: "ses_root",
          type: "external_directory",
          patterns: [externalPath],
          createdAt: "2026-07-21T23:50:18.335217Z"
        }],
        questions: [],
        onReplyPermission
      } as any
    });

    expect(rendered.getByText("需要权限")).toBeTruthy();
    expect(rendered.getByText("访问项目目录之外的文件")).toBeTruthy();
    expect(rendered.getByText(externalPath).tagName).toBe("CODE");
    expect(rendered.queryByText("external_directory")).toBeNull();
    expect(rendered.queryByText("per_internal")).toBeNull();
    expect(rendered.getAllByRole("button").map((button) => button.textContent)).toEqual([
      "拒绝",
      "始终允许",
      "允许一次"
    ]);

    await fireEvent.click(rendered.getByRole("button", { name: "拒绝" }));
    await fireEvent.click(rendered.getByRole("button", { name: "始终允许" }));
    await fireEvent.click(rendered.getByRole("button", { name: "允许一次" }));

    expect(onReplyPermission.mock.calls).toEqual([
      ["per_internal", "reject"],
      ["per_internal", "always"],
      ["per_internal", "once"]
    ]);
  });
});
