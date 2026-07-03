import { describe, expect, it, vi } from "vitest";
import { fireEvent, render } from "@testing-library/vue";
import MarkdownPreview from "../src/MarkdownPreview.vue";

vi.mock("mermaid", () => {
  return {
    default: {
      initialize: vi.fn(),
      render: vi.fn().mockResolvedValue({ svg: "<svg id='mock-preview-svg'>Mocked SVG</svg>" })
    }
  };
});

// 等待防抖（150ms）+ markdown-it/dompurify/highlight.js 动态 import 完成并完成 DOM 更新
const waitRender = () => new Promise((r) => setTimeout(r, 350));

describe("MarkdownPreview", () => {
  it("渲染标题与行内强调", async () => {
    const { container } = render(MarkdownPreview, {
      props: { content: "# 标题\n\n这是 **粗体** 文本" }
    });
    await waitRender();

    const body = container.querySelector(".markdown-body");
    expect(body?.querySelector("h1")?.textContent).toBe("标题");
    expect(body?.querySelector("strong")?.textContent).toBe("粗体");
  });

  it("对脚本与 javascript 链接做消毒", async () => {
    // 裸 <script> 被 markdown-it(html:false) 转义；javascript: 链接被 markdown-it 默认
    // validateLink 拦截，DOMPurify 再兜底，最终不应出现可执行节点
    const { container } = render(MarkdownPreview, {
      props: { content: "<script>alert(1)</" + "script>\n\n[x](javascript:alert(1))" }
    });
    await waitRender();

    const body = container.querySelector(".markdown-body");
    expect(body?.querySelector("script")).toBeNull();
    expect(body?.querySelector('a[href^="javascript:"]')).toBeNull();
  });

  it("正常链接保留并可点击", async () => {
    const { container } = render(MarkdownPreview, {
      props: { content: "[官网](https://example.com)" }
    });
    await waitRender();

    const anchor = container.querySelector(".markdown-body a");
    expect(anchor?.getAttribute("href")).toBe("https://example.com");
  });

  it("代码块经 highlight.js 高亮", async () => {
    const { container } = render(MarkdownPreview, {
      props: { content: "```ts\nconst a = 1;\n```" }
    });
    await waitRender();

    const code = container.querySelector(".markdown-body pre code");
    expect(code?.classList.contains("hljs")).toBe(true);
  });

  it("顶级块带源码行号 data-source-line，供滚动联动与序号对齐", async () => {
    const { container } = render(MarkdownPreview, {
      props: { content: "# 标题\n\n第一段\n\n第二段" }
    });
    await waitRender();

    const body = container.querySelector(".markdown-body");
    const blocks = body?.querySelectorAll<HTMLElement>("[data-source-line]");
    // h1=1, 第一段 p=3, 第二段 p=5
    const lines = Array.from(blocks ?? []).map((el) => el.getAttribute("data-source-line"));
    expect(lines).toContain("1");
    expect(lines).toContain("3");
    expect(lines).toContain("5");
  });

  it("空内容显示占位", async () => {
    const { findByText } = render(MarkdownPreview, { props: { content: "   " } });
    expect(await findByText("无内容")).toBeTruthy();
  });

  it("mermaid 在工作区预览里支持脚本与图表切换", async () => {
    const { container } = render(MarkdownPreview, {
      props: { content: "```mermaid\ngraph TD;\n  A-->B;\n```" }
    });
    await waitRender();

    const chartBtn = container.querySelector(".ta-mermaid-preview-btn");
    expect(chartBtn).toBeTruthy();
    expect(chartBtn?.textContent).toContain("图表");
    expect(container.querySelector("code.language-mermaid")?.textContent).toContain("graph TD;");

    if (chartBtn) {
      await fireEvent.click(chartBtn);
      await waitRender();
      expect(container.querySelector("#mock-preview-svg")).toBeTruthy();
      expect((container.querySelector(".ta-mermaid-script") as HTMLElement | null)?.hidden).toBe(true);
    }

    const scriptBtn = container.querySelector('[data-mermaid-mode="script"]');
    if (scriptBtn) {
      await fireEvent.click(scriptBtn);
      expect((container.querySelector(".ta-mermaid-script") as HTMLElement | null)?.hidden).toBe(false);
    }
  });
});
