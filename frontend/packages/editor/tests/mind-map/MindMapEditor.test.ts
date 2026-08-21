import { defineComponent, h } from "vue";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render } from "@testing-library/vue";
import { parseMindMapMarkdown } from "../../src/mind-map/markdown";

const canvasCalls = vi.hoisted(() => ({
  addChild: vi.fn(),
  addSibling: vi.fn(),
  removeSelected: vi.fn(),
  setSelectedText: vi.fn(),
  setSelectedStyle: vi.fn(),
  restoreSelectedStyle: vi.fn(),
  toggleSelectedCollapse: vi.fn(),
  undo: vi.fn(),
  redo: vi.fn(),
  zoomIn: vi.fn(),
  zoomOut: vi.fn(),
  fit: vi.fn()
}));

vi.mock("../../src/mind-map/MindMapCanvas.vue", () => ({
  default: defineComponent({
    name: "MindMapCanvasStub",
    props: ["document", "readonly", "generationKey"],
    emits: ["change", "selection-change", "status"],
    setup(props, { emit, expose }) {
      expose(canvasCalls);
      return () => h("div", { "data-testid": "mind-map-canvas-stub" }, [
        h("button", {
          type: "button",
          onClick: () => emit("selection-change", {
            id: "n1",
            text: "A",
            isRoot: false,
            collapsed: false,
            hasChildren: true,
            style: {}
          })
        }, "选择普通节点"),
        h("button", {
          type: "button",
          onClick: () => emit("selection-change", {
            id: "root",
            text: "根",
            isRoot: true,
            collapsed: false,
            hasChildren: true,
            style: {}
          })
        }, "选择根节点"),
        h("button", {
          type: "button",
          onClick: () => emit("selection-change", undefined)
        }, "清除选择"),
        h("button", {
          type: "button",
          onClick: () => {
            const document = JSON.parse(JSON.stringify(props.document));
            document.root.children[0].text = "**非法富文本**";
            emit("change", document);
          }
        }, "生成非法草稿")
      ]);
    }
  })
}));

import MindMapEditor from "../../src/mind-map/MindMapEditor.vue";

function documentFixture() {
  return parseMindMapMarkdown("# 根\n\n- A\n  - B\n").document!;
}

describe("MindMapEditor", () => {
  it("无选择时禁用节点操作，选择普通节点后开放新增、删除和折叠", async () => {
    const { getByRole } = render(MindMapEditor, { props: { document: documentFixture() } });

    expect((getByRole("button", { name: "新增同级节点" }) as HTMLButtonElement).disabled).toBe(true);
    expect((getByRole("button", { name: "新增子节点" }) as HTMLButtonElement).disabled).toBe(true);
    expect((getByRole("button", { name: "删除节点" }) as HTMLButtonElement).disabled).toBe(true);

    await fireEvent.click(getByRole("button", { name: "选择普通节点" }));
    await fireEvent.click(getByRole("button", { name: "新增同级节点" }));
    await fireEvent.click(getByRole("button", { name: "新增子节点" }));
    await fireEvent.click(getByRole("button", { name: "删除节点" }));
    await fireEvent.click(getByRole("button", { name: "收起节点" }));

    expect(canvasCalls.addSibling).toHaveBeenCalledOnce();
    expect(canvasCalls.addChild).toHaveBeenCalledOnce();
    expect(canvasCalls.removeSelected).toHaveBeenCalledOnce();
    expect(canvasCalls.toggleSelectedCollapse).toHaveBeenCalledOnce();
  });

  it("根节点禁止删除和新增同级，但允许新增子节点", async () => {
    const { getByRole } = render(MindMapEditor, { props: { document: documentFixture() } });
    await fireEvent.click(getByRole("button", { name: "选择根节点" }));

    expect((getByRole("button", { name: "新增同级节点" }) as HTMLButtonElement).disabled).toBe(true);
    expect((getByRole("button", { name: "删除节点" }) as HTMLButtonElement).disabled).toBe(true);
    expect((getByRole("button", { name: "新增子节点" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("节点编号空间耗尽时禁用新增并说明原因", async () => {
    const exhausted = parseMindMapMarkdown(
      "# 根\n\n<!-- mm:id=root -->\n\n- A <!-- mm:id=n4294967294 -->\n"
    ).document!;
    const { getByRole } = render(MindMapEditor, { props: { document: exhausted } });

    await fireEvent.click(getByRole("button", { name: "选择普通节点" }));

    for (const name of ["新增同级节点", "新增子节点"] as const) {
      const button = getByRole("button", { name }) as HTMLButtonElement;
      expect(button.disabled).toBe(true);
      expect(button.title).toContain("节点编号空间已耗尽");
    }
  });

  it("支持改名、常用样式和恢复默认样式", async () => {
    const { getByRole, getByLabelText } = render(MindMapEditor, {
      props: { document: documentFixture() }
    });
    await fireEvent.click(getByRole("button", { name: "选择普通节点" }));

    await fireEvent.update(getByLabelText("节点文字"), "新名称");
    await fireEvent.change(getByLabelText("节点文字"));
    await fireEvent.update(getByLabelText("填充颜色"), "#123456");
    await fireEvent.click(getByLabelText("粗体"));
    await fireEvent.click(getByRole("button", { name: "恢复默认样式" }));

    expect(canvasCalls.setSelectedText).toHaveBeenCalledWith("新名称");
    expect(canvasCalls.setSelectedStyle).toHaveBeenCalledWith({ fillColor: "#123456" });
    expect(canvasCalls.setSelectedStyle).toHaveBeenCalledWith({ bold: true });
    expect(canvasCalls.restoreSelectedStyle).toHaveBeenCalledOnce();
  });

  it("工具栏提供撤销、重做、缩放和适配", async () => {
    const { getByRole } = render(MindMapEditor, { props: { document: documentFixture() } });

    for (const [name, spy] of [
      ["撤销", canvasCalls.undo],
      ["重做", canvasCalls.redo],
      ["放大", canvasCalls.zoomIn],
      ["缩小", canvasCalls.zoomOut],
      ["适应画布", canvasCalls.fit]
    ] as const) {
      await fireEvent.click(getByRole("button", { name }));
      expect(spy).toHaveBeenCalledOnce();
    }
  });

  it("应用时补齐 ID 和元数据并二次解析校验，取消不产生应用结果", async () => {
    const { emitted, getByRole } = render(MindMapEditor, {
      props: { document: documentFixture() }
    });

    await fireEvent.click(getByRole("button", { name: "应用思维导图" }));
    expect(emitted().apply).toHaveLength(1);
    const result = (emitted().apply as Array<[{ content: string }]>)[0]![0];
    expect(result.content).toContain("<!-- mm:id=root -->");
    expect(result.content).toMatch(/<!--mm:v1:[A-Za-z0-9_-]+-->/);

    await fireEvent.click(getByRole("button", { name: "取消编辑" }));
    expect(emitted().cancel).toHaveLength(1);
    expect(emitted().apply).toHaveLength(1);
  });

  it("二次校验失败时不应用并显示安全中文错误", async () => {
    const { emitted, getByRole, findByRole } = render(MindMapEditor, {
      props: { document: documentFixture() }
    });
    await fireEvent.click(getByRole("button", { name: "生成非法草稿" }));
    await fireEvent.click(getByRole("button", { name: "应用思维导图" }));

    expect((await findByRole("alert")).textContent).toMatch(/普通文本|第 \d+ 行/);
    expect(emitted().apply).toBeUndefined();
  });

  it("只读模式阻断编辑和应用", async () => {
    const { getByRole } = render(MindMapEditor, {
      props: { document: documentFixture(), readonly: true }
    });
    await fireEvent.click(getByRole("button", { name: "选择普通节点" }));

    expect((getByRole("button", { name: "新增子节点" }) as HTMLButtonElement).disabled).toBe(true);
    expect((getByRole("button", { name: "撤销" }) as HTMLButtonElement).disabled).toBe(true);
    expect((getByRole("button", { name: "应用思维导图" }) as HTMLButtonElement).disabled).toBe(true);
  });
});
