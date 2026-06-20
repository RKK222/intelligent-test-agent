import { describe, expect, it } from "vitest";
import { render } from "@testing-library/vue";
import MarkdownPreview from "../src/MarkdownPreview.vue";

// 等待防抖（150ms）+ marked/dompurify 动态 import 完成并完成 DOM 更新
const waitRender = () => new Promise((r) => setTimeout(r, 350));

describe("MarkdownPreview", () => {
  it("渲染标题与行内强调", async () => {
    const { container } = render(MarkdownPreview, {
      props: { content: "# 标题\n\n这是 **粗体** 文本" }
    });
    await waitRender();

    const body = container.querySelector(".md-body");
    expect(body?.querySelector("h1")?.textContent).toBe("标题");
    expect(body?.querySelector("strong")?.textContent).toBe("粗体");
  });

  it("对脚本与事件属性做消毒", async () => {
    const { container } = render(MarkdownPreview, {
      props: { content: "<script>alert(1)</" + "script>\n\n<a href=\"javascript:alert(1)\">x</a>" }
    });
    await waitRender();

    const body = container.querySelector(".md-body");
    expect(body?.querySelector("script")).toBeNull();
    const anchor = body?.querySelector("a");
    // javascript: 协议链接应被 DOMPurify 移除 href
    expect(anchor?.getAttribute("href")).toBeNull();
  });

  it("空内容显示占位", async () => {
    const { findByText } = render(MarkdownPreview, { props: { content: "   " } });
    expect(await findByText("无内容")).toBeTruthy();
  });
});
