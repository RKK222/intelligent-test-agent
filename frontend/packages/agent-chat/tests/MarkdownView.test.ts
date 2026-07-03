import { describe, expect, it, vi } from "vitest";
import { fireEvent, render } from "@testing-library/vue";
import MarkdownView from "../src/MarkdownView.vue";

vi.mock("mermaid", () => {
  return {
    default: {
      initialize: vi.fn(),
      render: vi.fn().mockResolvedValue({ svg: "<svg id='mock-svg'>Mocked SVG</svg>" })
    }
  };
});

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

  it("空内容同步显示占位且不进入加载态", () => {
    const { getByText, queryByText } = render(MarkdownView, {
      props: { source: "   ", loadingText: "准备输出…" }
    });

    expect(getByText("无内容")).toBeTruthy();
    expect(queryByText("准备输出…")).toBeNull();
  });

  it("支持覆盖首次渲染占位文案", () => {
    const { getByText } = render(MarkdownView, {
      props: { source: "正文", loadingText: "准备输出…" }
    });

    expect(getByText("准备输出…")).toBeTruthy();
  });

  it("内容更新重新渲染期间保留已有 HTML，不回退到 loading 占位", async () => {
    const { getByText, queryByText, rerender } = render(MarkdownView, {
      props: { source: "第一版内容", loadingText: "准备输出…" }
    });
    await waitRender();

    expect(getByText("第一版内容")).toBeTruthy();

    await rerender({ source: "第二版内容", loadingText: "准备输出…" });

    expect(getByText("第一版内容")).toBeTruthy();
    expect(queryByText("准备输出…")).toBeNull();
  });

  it("mermaid 默认展示脚本，支持切换为图表", async () => {
    const { container } = render(MarkdownView, {
      props: { source: "```mermaid\ngraph TD;\n  A-->B;\n```" }
    });
    await waitRender();

    // 1. 未点击前展示脚本和切换按钮
    const btn = container.querySelector(".ta-mermaid-preview-btn");
    expect(btn).toBeTruthy();
    expect(btn?.textContent).toContain("图表");
    expect(container.querySelector("code.language-mermaid")?.textContent).toContain("graph TD;");

    // 2. 点击后显示渲染图表，脚本仍在 DOM 中便于切回
    if (btn) {
      await fireEvent.click(btn);
      await waitRender();
      expect(container.querySelector("#mock-svg")).toBeTruthy();
      expect((container.querySelector(".ta-mermaid-script") as HTMLElement | null)?.hidden).toBe(true);
    }
  });

  it("对于 `` 包裹的 .md 文件应用 ta-md-file class 且普通 inline code 没有", async () => {
    const { container } = render(MarkdownView, {
      props: { source: "这里有 `frontend-opencode/docs/README.md`，而这个 `package.json` 则不是 md" }
    });
    await waitRender();

    const body = container.querySelector(".markdown-body");
    const codeElements = body?.querySelectorAll("code");
    expect(codeElements?.length).toBe(2);

    const mdCode = Array.from(codeElements || []).find(el => el.textContent === "frontend-opencode/docs/README.md");
    const jsonCode = Array.from(codeElements || []).find(el => el.textContent === "package.json");

    expect(mdCode?.classList.contains("ta-md-file")).toBe(true);
    expect(jsonCode?.classList.contains("ta-md-file")).toBe(false);
  });
});
