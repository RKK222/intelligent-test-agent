// @vitest-environment jsdom

import { fireEvent, render } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import CustomMenuPage from "../src/components/CustomMenuPage.vue";

const menu = { id: "menu-quality-01", name: "质量看板", icon: "chart" as const, url: "https://tools.example.com/dashboard" };

afterEach(() => vi.restoreAllMocks());

describe("CustomMenuPage", () => {
  it("embeds the configured URL with a restricted iframe and offers a new-window fallback", async () => {
    const open = vi.spyOn(window, "open").mockImplementation(() => null);
    const view = render(CustomMenuPage, { props: { menu, pageActive: true } });
    const frame = view.getByTitle("质量看板") as HTMLIFrameElement;

    expect(frame.getAttribute("src")).toBe(menu.url);
    expect(frame.getAttribute("sandbox")).not.toContain("allow-top-navigation");
    await fireEvent.load(frame);
    expect(view.queryByText("正在加载")).toBeNull();
    await fireEvent.click(view.getByRole("button", { name: "在新窗口打开 质量看板" }));
    expect(open).toHaveBeenCalledWith(menu.url, "_blank", "noopener,noreferrer");
  });

  it("unloads the iframe while its tab is inactive", () => {
    const view = render(CustomMenuPage, { props: { menu, pageActive: false } });
    expect(view.queryByTitle("质量看板")).toBeNull();
  });
});
