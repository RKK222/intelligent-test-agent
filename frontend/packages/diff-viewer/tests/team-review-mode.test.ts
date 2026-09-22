import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import DiffViewer, { diffEditorReadOnly, diffReviewShowsRunActions } from "../src/DiffViewer.vue";

describe("team review diff mode", () => {
  it("keeps Monaco available while hiding run and save actions", () => {
    expect(diffEditorReadOnly("vcs", true, "team")).toBe(true);
    expect(diffEditorReadOnly("run", true, "team")).toBe(true);
    expect(diffEditorReadOnly("vcs", true, "default")).toBe(false);
    expect(diffReviewShowsRunActions("run", "team")).toBe(false);
    expect(diffReviewShowsRunActions("run", "default")).toBe(true);
  });

  it("renders a read-only review label instead of accept, reject or quote actions", () => {
    const wrapper = mount(DiffViewer, {
      props: {
        files: [],
        reviewMode: "team",
        writable: false
      }
    });
    expect(wrapper.text()).toContain("只读审阅");
    expect(wrapper.text()).not.toContain("接受全部");
    expect(wrapper.text()).not.toContain("拒绝全部");
    expect(wrapper.text()).not.toContain("保存");
    wrapper.unmount();
  });
});
