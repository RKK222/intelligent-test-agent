import { defineComponent } from "vue";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, within } from "@testing-library/vue";
import MarkdownPreview from "../src/MarkdownPreview.vue";

const mermaidParse = vi.hoisted(() => vi.fn().mockResolvedValue(true));

vi.mock("mermaid", () => {
  return {
    default: {
      initialize: vi.fn(),
      parse: mermaidParse,
      render: vi.fn().mockResolvedValue({ svg: "<svg id='mock-preview-svg'>Mocked SVG</svg>" })
    }
  };
});

vi.mock("@vue-flow/core", () => ({
  VueFlow: defineComponent({
    props: ["nodes", "edges"],
    emits: ["nodeClick", "nodeDragStop", "connect"],
    template: `<div data-testid="vue-flow-mock">
      <slot name="node-mermaid" v-for="node in (nodes || [])" :key="node.id" :id="node.id" :data="node.data" />
      <button data-testid="mock-select-a" @click="$emit('nodeClick', { node: { id: 'A' } })">select A</button>
    </div>`
  }),
  Handle: defineComponent({ template: "<span />" }),
  BaseEdge: defineComponent({ template: "<path />" }),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  MarkerType: { ArrowClosed: "arrowclosed", Arrow: "arrow" },
  ConnectionMode: { Loose: "loose" },
  getSmoothStepPath: vi.fn(() => ["M0 0 L1 1"])
}));

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

  it("可视化编辑节点后只回写当前 Mermaid block", async () => {
    const content = `# 设计

\`\`\`mermaid
flowchart TD
A[开始] --> B[结束]
classDef important fill:red
\`\`\`

正文`;
    const { container, emitted, findByRole, getByTestId, getByLabelText } = render(MarkdownPreview, {
      props: { content }
    });
    await waitRender();

    const visualButton = container.querySelector('[data-mermaid-mode="visual"]');
    expect(visualButton?.textContent).toContain("可视化编辑");
    await fireEvent.click(visualButton as Element);
    // 全量并发时 Mermaid/Vue Flow 懒模块首次转换可能超过 Testing Library 默认 1 秒。
    const dialog = await findByRole("dialog", { name: "Mermaid 可视化编辑" }, { timeout: 5000 });
    expect(dialog).toBeTruthy();

    await fireEvent.click(within(dialog).getByTestId("mock-select-a"));
    await fireEvent.dblClick(dialog.querySelector('[data-mermaid-node-id="A"]')!);
    
    const inlineEditor = document.body.querySelector('.ta-mermaid-inline-editor') as HTMLElement;
    expect(inlineEditor).toBeTruthy();
    await fireEvent.update(within(inlineEditor).getByLabelText("节点文字"), "准备");
    await fireEvent.keyDown(within(inlineEditor).getByLabelText("节点文字"), { key: "Enter", ctrlKey: true });
    await fireEvent.click(within(dialog).getByRole("button", { name: "应用到 Markdown" }));

    await vi.waitFor(() => expect(emitted().change).toBeTruthy());
    const changes = emitted().change as Array<[string]>;
    expect(changes).toHaveLength(1);
    expect(changes[0]?.[0]).toContain('A@{ shape: rect, label: "准备" }');
    expect(changes[0]?.[0]).toContain("classDef important fill:red");
    expect(changes[0]?.[0]).toContain("# 设计");
    expect(changes[0]?.[0]).toContain("正文");
  });

  it("官方 parser 拒绝语法时显示原因且不修改 Markdown", async () => {
    mermaidParse.mockRejectedValueOnce(new Error("Mermaid Syntax Error"));
    const { container, emitted, findByText } = render(MarkdownPreview, {
      props: { content: "```mermaid\nflowchart TD\nA -->> B\n```" }
    });
    await waitRender();

    await fireEvent.click(container.querySelector('[data-mermaid-mode="visual"]') as Element);

    expect(await findByText("无法进行可视化编辑", {}, { timeout: 5000 })).toBeTruthy();
    expect(await findByText(/Mermaid Syntax Error/, {}, { timeout: 5000 })).toBeTruthy();
    expect(emitted().change).toBeUndefined();
  });

  it("打开编辑器后 block 被外部刷新时拒绝覆盖新内容", async () => {
    const original = "```mermaid\nflowchart TD\nA --> B\n```";
    const refreshed = "```mermaid\nflowchart TD\nA --> B\nstyle A fill:red\n```";
    const { container, emitted, findByRole, rerender, findByText } = render(MarkdownPreview, {
      props: { content: original }
    });
    await waitRender();

    await fireEvent.click(container.querySelector('[data-mermaid-mode="visual"]') as Element);
    await findByRole("dialog", { name: "Mermaid 可视化编辑" });
    await rerender({ content: refreshed });
    await fireEvent.click(await findByRole("button", { name: "应用到 Markdown" }));

    expect(await findByText(/代码块已发生变化/)).toBeTruthy();
    expect(emitted().change).toBeUndefined();
  });

  it("sequenceDiagram 可编辑有序消息并保留复杂语句", async () => {
    const content = `# 时序

\`\`\`mermaid
sequenceDiagram
actor U as 用户
participant S as 服务
U->>S: 请求
Note over U,S: 保留说明
\`\`\``;
    const { container, emitted, findByRole, getByLabelText } = render(MarkdownPreview, {
      props: { content }
    });
    await waitRender();

    await fireEvent.click(container.querySelector('[data-mermaid-mode="visual"]') as Element);
    await findByRole("dialog", { name: "Mermaid 可视化编辑" });
    await fireEvent.update(getByLabelText("消息 1 标签"), "登录请求");
    await fireEvent.click(await findByRole("button", { name: "应用到 Markdown" }));

    await vi.waitFor(() => expect(emitted().change).toBeTruthy());
    const markdown = (emitted().change as Array<[string]>)[0]?.[0] ?? "";
    expect(markdown).toContain("sequenceDiagram");
    expect(markdown).toContain("U->>S: 登录请求");
    expect(markdown).toContain("Note over U,S: 保留说明");
  });
});
