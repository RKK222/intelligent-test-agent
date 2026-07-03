import { describe, expect, it } from "vitest";
import { render } from "@testing-library/vue";
import MarkdownView from "../src/MarkdownView.vue";

// 等待防抖（150ms）+ markdown-it/dompurify/highlight.js 动态 import 完成并完成 DOM 更新
const waitRender = () => new Promise((r) => setTimeout(r, 400));

describe("MarkdownView", () => {
  it("渲染标题与行内强调", async () => {
    const { container } = render(MarkdownView, {
      props: { source: "# 标题\n\n这是 **粗体** 文本" }
    });
    await waitRender();

    const body = container.querySelector(".markdown-body");
    expect(body?.querySelector("h1")?.textContent).toBe("标题");
    expect(body?.querySelector("strong")?.textContent).toBe("粗体");
  });

  it("对脚本与 javascript 链接做消毒", async () => {
    const { container } = render(MarkdownView, {
      props: { source: "<script>alert(1)</" + "script>\n\n[x](javascript:alert(1))" }
    });
    await waitRender();

    const body = container.querySelector(".markdown-body");
    expect(body?.querySelector("script")).toBeNull();
    expect(body?.querySelector('a[href^="javascript:"]')).toBeNull();
  });

  it("正常链接保留并可点击", async () => {
    const { container } = render(MarkdownView, {
      props: { source: "[官网](https://example.com)" }
    });
    await waitRender();

    const anchor = container.querySelector(".markdown-body a");
    expect(anchor?.getAttribute("href")).toBe("https://example.com");
  });

  it("代码块经 highlight.js 高亮", async () => {
    const { container } = render(MarkdownView, {
      props: { source: "```ts\nconst a = 1;\n```" }
    });
    await waitRender();

    const code = container.querySelector(".markdown-body pre code");
    expect(code?.classList.contains("hljs")).toBe(true);
  });

  it("空内容显示占位", async () => {
    const { findByText } = render(MarkdownView, { props: { source: "   " } });
    expect(await findByText("无内容")).toBeTruthy();
  });

  it("支持覆盖首次渲染占位文案", () => {
    const { getByText } = render(MarkdownView, {
      props: { source: "正文", loadingText: "准备输出…" }
    });

    expect(getByText("准备输出…")).toBeTruthy();
  });
});
