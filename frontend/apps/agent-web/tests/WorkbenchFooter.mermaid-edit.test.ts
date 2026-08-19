import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import WorkbenchFooter from "../src/components/WorkbenchFooter.vue";
import FigmaEditorArea from "../src/components/FigmaEditorArea.vue";

describe("WorkbenchFooter Mermaid Edit and Preview Buttons", () => {
  it("renders mermaid preview and edit buttons for .mmd file to the left of the locate button", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        writePath: "architecture/flow.mmd",
        showSave: true
      }
    });

    const previewBtn = wrapper.find('[data-testid="footer-mermaid-preview"]');
    const editBtn = wrapper.find('[data-testid="footer-mermaid-edit"]');

    expect(previewBtn.exists()).toBe(true);
    expect(previewBtn.attributes("aria-label")).toBe("Mermaid 图表预览");
    expect(editBtn.exists()).toBe(true);
    expect(editBtn.attributes("aria-label")).toBe("Mermaid 可视化编辑");

    const rightButtons = wrapper.find(".ta-workbench-footer-right").findAll("button");
    const previewIndex = rightButtons.findIndex((btn) => btn.classes().includes("ta-workbench-footer-mermaid-preview"));
    const editIndex = rightButtons.findIndex((btn) => btn.classes().includes("ta-workbench-footer-mermaid-edit"));
    const locateIndex = rightButtons.findIndex((btn) => btn.classes().includes("ta-workbench-footer-locate"));

    expect(previewIndex).toBeGreaterThanOrEqual(0);
    expect(editIndex).toBeGreaterThan(previewIndex);
    expect(locateIndex).toBeGreaterThan(editIndex);

    await previewBtn.trigger("click");
    expect(wrapper.emitted("open-mermaid-preview")).toHaveLength(1);

    await editBtn.trigger("click");
    expect(wrapper.emitted("open-mermaid-editor")).toHaveLength(1);
  });

  it("renders mermaid preview and edit buttons for uppercase .MMD extension", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        writePath: "docs/DIAGRAM.MMD",
        showSave: true
      }
    });

    expect(wrapper.find('[data-testid="footer-mermaid-preview"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="footer-mermaid-edit"]').exists()).toBe(true);
  });

  it("does not render mermaid preview and edit buttons for non-mmd files", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        writePath: "docs/readme.md",
        showSave: true
      }
    });

    expect(wrapper.find('[data-testid="footer-mermaid-preview"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="footer-mermaid-edit"]').exists()).toBe(false);
    expect(wrapper.find(".ta-workbench-footer-locate").exists()).toBe(true);
  });

  it("supports explicit showMermaidPreviewButton and showMermaidEditButton prop override", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        writePath: "docs/other.txt",
        showMermaidPreviewButton: true,
        showMermaidEditButton: true,
        showSave: true
      }
    });

    expect(wrapper.find('[data-testid="footer-mermaid-preview"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="footer-mermaid-edit"]').exists()).toBe(true);
  });

  it("transfers open-mermaid-preview and open-mermaid-editor events through FigmaEditorArea", async () => {
    const wrapper = mount(FigmaEditorArea, {
      props: {
        tabs: [
          {
            id: "tab_1",
            title: "workflow.mmd",
            path: "docs/workflow.mmd",
            content: "flowchart TD\n  A --> B",
            savedContent: "flowchart TD\n  A --> B"
          }
        ],
        activePath: "docs/workflow.mmd",
        writePath: "docs/workflow.mmd"
      }
    });

    const previewBtn = wrapper.find('[data-testid="footer-mermaid-preview"]');
    const editBtn = wrapper.find('[data-testid="footer-mermaid-edit"]');
    expect(previewBtn.exists()).toBe(true);
    expect(editBtn.exists()).toBe(true);

    await previewBtn.trigger("click");
    expect(wrapper.emitted("open-mermaid-preview")).toHaveLength(1);

    await editBtn.trigger("click");
    expect(wrapper.emitted("open-mermaid-editor")).toHaveLength(1);
  });
});
