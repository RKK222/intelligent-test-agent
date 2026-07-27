// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import ToolboxPanel from "../src/components/ToolboxPanel.vue";

const api = {
  getToolboxCatalog: vi.fn(),
  recordToolboxClick: vi.fn()
};

describe("ToolboxPanel", () => {
  beforeEach(() => {
    api.getToolboxCatalog.mockResolvedValue({
      catalogVersion: "catalog-v1",
      total: 3,
      hotLimit: 10,
      tools: [
        tool({
          toolId: "it-tools.hash-text",
          source: "IT_TOOLS",
          sourceName: "IT-Tools",
          nameZh: "文本哈希",
          nameEn: "Hash text",
          descriptionZh: "计算文本摘要",
          category: "SECURITY",
          categoryLabel: "安全与加密",
          keywords: ["sha256", "摘要"],
          launchPath: "/toolbox/apps/it-tools/hash-text",
          clickCount: 5,
          hotRank: 2
        }),
        tool({
          toolId: "omni-tools.crypto.hash-text",
          source: "OMNI_TOOLS",
          sourceName: "OmniTools",
          nameZh: "哈希生成器",
          nameEn: "Hash Generator",
          descriptionZh: "生成多种哈希值",
          category: "SECURITY",
          categoryLabel: "安全与加密",
          keywords: ["digest", "校验"],
          launchPath: "/toolbox/apps/omni-tools/crypto/hash-text",
          clickCount: 5,
          hotRank: 1
        }),
        tool({
          toolId: "omni-tools.text.word-counter",
          source: "OMNI_TOOLS",
          sourceName: "OmniTools",
          nameZh: "字数统计",
          nameEn: "Word Counter",
          descriptionZh: "统计字词和字符",
          category: "TEXT",
          categoryLabel: "文本处理",
          keywords: ["words", "字符"],
          launchPath: "/toolbox/apps/omni-tools/text/word-counter",
          clickCount: 0,
          hotRank: null
        })
      ]
    });
    api.recordToolboxClick.mockResolvedValue({
      toolId: "omni-tools.text.word-counter",
      clickCount: 6,
      recorded: true,
      incremented: true
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("searches Chinese English and keywords, then filters source and fixed category", async () => {
    const view = renderPanel();
    await view.findByTestId("tool-link-it-tools.hash-text");

    const search = view.getByPlaceholderText("搜索中文、英文或关键词");
    await fireEvent.update(search, "sha256");
    expect(view.getByTestId("tool-link-it-tools.hash-text")).toBeTruthy();
    expect(view.queryByTestId("tool-link-omni-tools.crypto.hash-text")).toBeNull();

    await fireEvent.update(search, "Word Counter");
    expect(view.getByTestId("tool-link-omni-tools.text.word-counter")).toBeTruthy();

    await fireEvent.update(search, "");
    await fireEvent.click(view.getByRole("button", { name: "仅看 IT-Tools" }));
    expect(view.getByTestId("tool-link-it-tools.hash-text")).toBeTruthy();
    expect(view.queryByTestId("tool-link-omni-tools.text.word-counter")).toBeNull();

    await fireEvent.click(view.getByRole("button", { name: "查看全部来源" }));
    await fireEvent.update(view.getByLabelText("工具分类"), "TEXT");
    expect(view.getByTestId("tool-link-omni-tools.text.word-counter")).toBeTruthy();
    expect(view.queryByTestId("tool-link-it-tools.hash-text")).toBeNull();
  });

  it("uses native new-tab links and reports left, keyboard-compatible and middle clicks", async () => {
    const view = renderPanel();
    const link = await view.findByTestId("tool-link-omni-tools.text.word-counter");

    expect(link.getAttribute("href")).toBe("/toolbox/apps/omni-tools/text/word-counter");
    expect(link.getAttribute("target")).toBe("_blank");
    expect(link.getAttribute("rel")).toBe("noopener noreferrer");

    const leftClick = new MouseEvent("click", { bubbles: true, cancelable: true, button: 0 });
    expect(link.dispatchEvent(leftClick)).toBe(true);
    await waitFor(() => expect(api.recordToolboxClick).toHaveBeenCalledTimes(1));

    const middleClick = new MouseEvent("auxclick", { bubbles: true, cancelable: true, button: 1 });
    expect(link.dispatchEvent(middleClick)).toBe(true);
    await waitFor(() => expect(api.recordToolboxClick).toHaveBeenCalledTimes(2));
    expect(api.recordToolboxClick.mock.calls[0]?.[0]).toBe("omni-tools.text.word-counter");
    expect(api.recordToolboxClick.mock.calls[0]?.[1]).toMatch(/^tbx_/);
  });

  it("updates click count and hot ranking after success without surfacing reporting failures", async () => {
    const view = renderPanel();
    const link = await view.findByTestId("tool-link-omni-tools.text.word-counter");

    await fireEvent.click(link);
    await waitFor(() => expect(view.getByTestId("tool-count-omni-tools.text.word-counter").textContent).toContain("6"));
    expect(view.getByTestId("hot-tool-omni-tools.text.word-counter").textContent).toContain("#1");

    api.recordToolboxClick.mockRejectedValueOnce(new Error("offline"));
    expect(link.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, button: 0 }))).toBe(true);
    await waitFor(() => expect(api.recordToolboxClick).toHaveBeenCalledTimes(2));
    expect(view.queryByRole("alert")).toBeNull();
  });

  it("does not move a tied tool for a window-suppressed response or lower a newer local count", async () => {
    api.recordToolboxClick.mockResolvedValueOnce({
      toolId: "it-tools.hash-text",
      clickCount: 4,
      recorded: true,
      incremented: false
    });
    const view = renderPanel();
    const link = await view.findByTestId("tool-link-it-tools.hash-text");

    await fireEvent.click(link);
    await waitFor(() => expect(api.recordToolboxClick).toHaveBeenCalledTimes(1));

    expect(view.getByTestId("tool-count-it-tools.hash-text").textContent).toContain("5");
    expect(view.getByTestId("hot-tool-omni-tools.crypto.hash-text").textContent).toContain("#1");
  });

  it("keeps an explicit hot empty state when the catalog has no clicks", async () => {
    api.getToolboxCatalog.mockResolvedValueOnce({
      catalogVersion: "catalog-empty",
      total: 1,
      hotLimit: 10,
      tools: [tool({ toolId: "it-tools.empty", launchPath: "/toolbox/apps/it-tools/empty" })]
    });

    const view = renderPanel();
    await view.findByTestId("tool-link-it-tools.empty");

    expect(view.getByRole("heading", { name: "热门工具" })).toBeTruthy();
    expect(view.getByText("还没有累计点击，打开任一工具后会生成热门排行")).toBeTruthy();
  });

  it("shows a retry state when loading fails and recovers without leaving the page", async () => {
    api.getToolboxCatalog.mockRejectedValueOnce(new Error("offline"));
    const view = renderPanel();

    expect((await view.findByRole("alert")).textContent).toContain("工具目录暂时不可用，请稍后重试");
    await fireEvent.click(view.getByRole("button", { name: "重新加载" }));

    expect(await view.findByTestId("tool-link-it-tools.hash-text")).toBeTruthy();
    expect(view.queryByRole("alert")).toBeNull();
    expect(api.getToolboxCatalog).toHaveBeenCalledTimes(2);
  });
});

function renderPanel() {
  return render(ToolboxPanel, {
    global: { provide: { api } }
  });
}

function tool(overrides: Record<string, unknown>) {
  return {
    toolId: "tool",
    source: "IT_TOOLS",
    sourceName: "IT-Tools",
    sourceVersion: "test",
    nameZh: "工具",
    nameEn: "Tool",
    descriptionZh: "说明",
    category: "OTHER",
    categoryLabel: "其他",
    keywords: ["tool"],
    launchPath: "/toolbox/apps/it-tools/tool",
    clickCount: 0,
    hotRank: null,
    ...overrides
  };
}
