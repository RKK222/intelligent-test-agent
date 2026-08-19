import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import WorkbenchFooter from "../src/components/WorkbenchFooter.vue";
import FigmaEditorArea from "../src/components/FigmaEditorArea.vue";

describe("WorkbenchFooter Mermaid Edit Button", () => {
  it("renders mermaid edit button for .mmd file to the left of the locate button", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        writePath: "architecture/flow.mmd",
        showSave: true
      }
    });

    const editBtn = wrapper.find('[data-testid="footer-mermaid-edit"]');
    expect(editBtn.exists()).toBe(true);
    expect(editBtn.attributes("aria-label")).toBe("Mermaid 可视化编辑");

    const rightButtons = wrapper.find(".ta-workbench-footer-right").findAll("button");
    const editIndex = rightButtons.findIndex((btn) => btn.classes().includes("ta-workbench-footer-mermaid-edit"));
    const locateIndex = rightButtons.findIndex((btn) => btn.classes().includes("ta-workbench-footer-locate"));

    expect(editIndex).toBeGreaterThanOrEqual(0);
    expect(locateIndex).toBeGreaterThan(editIndex);

    await editBtn.trigger("click");
    expect(wrapper.emitted("open-mermaid-editor")).toHaveLength(1);
  });

  it("renders mermaid edit button for uppercase .MMD extension", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        writePath: "docs/DIAGRAM.MMD",
        showSave: true
      }
    });

    expect(wrapper.find('[data-testid="footer-mermaid-edit"]').exists()).toBe(true);
  });

  it("does not render mermaid edit button for non-mmd files", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        writePath: "docs/readme.md",
        showSave: true
      }
    });

    expect(wrapper.find('[data-testid="footer-mermaid-edit"]').exists()).toBe(false);
    expect(wrapper.find(".ta-workbench-footer-locate").exists()).toBe(true);
  });

  it("supports explicit showMermaidEditButton prop override", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        writePath: "docs/other.txt",
        showMermaidEditButton: true,
        showSave: true
      }
    });

    expect(wrapper.find('[data-testid="footer-mermaid-edit"]').exists()).toBe(true);
  });

  it("transfers open-mermaid-editor event through FigmaEditorArea", async () => {
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

    const editBtn = wrapper.find('[data-testid="footer-mermaid-edit"]');
    expect(editBtn.exists()).toBe(true);

    await editBtn.trigger("click");
    expect(wrapper.emitted("open-mermaid-editor")).toHaveLength(1);
  });
});
