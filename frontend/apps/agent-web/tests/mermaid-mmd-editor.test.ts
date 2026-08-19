import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it } from "vitest";
import {
  MermaidEditorDialog,
  MermaidPreviewDialog,
  parseMermaidDiagram,
  serializeMermaidDiagram,
  type MermaidEditableDiagram
} from "@test-agent/editor";

describe("Mermaid MMD Editor Integration", () => {
  beforeEach(() => {
    if (typeof window.ResizeObserver === "undefined") {
      window.ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
      } as any;
    }
  });

  it("parses and serializes flowchart diagram correctly", () => {
    const source = `flowchart TD
  A[Start] --> B[End]`;
    const diagram = parseMermaidDiagram(source);
    expect(diagram.kind).toBe("flowchart");
    const serialized = serializeMermaidDiagram(diagram);
    expect(serialized).toContain("Start");
    expect(serialized).toContain("End");
    expect(serialized).toContain("A --> B");
  });

  it("parses and serializes sequenceDiagram correctly", () => {
    const source = `sequenceDiagram
  Alice->>Bob: Hello`;
    const diagram = parseMermaidDiagram(source);
    expect(diagram.kind).toBe("sequenceDiagram");
    const serialized = serializeMermaidDiagram(diagram);
    expect(serialized).toContain("Alice->>Bob: Hello");
  });

  it("parses and serializes stateDiagram correctly", () => {
    const source = `stateDiagram-v2
  [*] --> State1
  State1 --> [*]`;
    const diagram = parseMermaidDiagram(source);
    expect(diagram.kind).toBe("stateDiagram");
    const serialized = serializeMermaidDiagram(diagram);
    expect(serialized).toContain("State1");
  });

  it("renders MermaidEditorDialog with customizable title, subtitle and apply label for mmd file", async () => {
    const source = `flowchart TD
  A[Start] --> B[End]`;
    const diagram = parseMermaidDiagram(source);

    const wrapper = mount(MermaidEditorDialog, {
      props: {
        model: diagram,
        title: "Mermaid 可视化编辑",
        subtitle: "拖动图结构并保存后，修改会回写到当前 mmd 文件。",
        applyLabel: "保存到文件"
      },
      global: {
        stubs: {
          Teleport: true,
          MermaidVisualEditor: {
            template: '<div class="mock-flowchart-editor"></div>'
          }
        }
      }
    });

    expect(wrapper.text()).toContain("Mermaid 可视化编辑");
    expect(wrapper.text()).toContain("拖动图结构并保存后，修改会回写到当前 mmd 文件。");

    const applyButton = wrapper.find("button.is-primary");
    expect(applyButton.text()).toBe("保存到文件");

    await applyButton.trigger("click");
    expect(wrapper.emitted("apply")).toHaveLength(1);
    const appliedDiagram = wrapper.emitted("apply")![0][0] as MermaidEditableDiagram;
    expect(appliedDiagram.kind).toBe("flowchart");
  });

  it("renders error alert in MermaidEditorDialog when parsing fails", () => {
    const wrapper = mount(MermaidEditorDialog, {
      props: {
        error: "语法错误: 不支持的图类型",
        title: "Mermaid 可视化编辑"
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    });

    expect(wrapper.find(".ta-mermaid-dialog__error").text()).toContain("无法进行可视化编辑");
    expect(wrapper.find(".ta-mermaid-dialog__error").text()).toContain("语法错误: 不支持的图类型");
  });

  it("renders MermaidPreviewDialog and supports closing and switching to edit", async () => {
    const source = `flowchart TD
  A --> B`;

    const wrapper = mount(MermaidPreviewDialog, {
      props: {
        code: source,
        title: "Mermaid 图表预览 - flow.mmd"
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    });

    expect(wrapper.text()).toContain("Mermaid 图表预览 - flow.mmd");

    const editButton = wrapper.find(".ta-mermaid-preview-btn-edit");
    expect(editButton.exists()).toBe(true);
    await editButton.trigger("click");
    expect(wrapper.emitted("edit")).toHaveLength(1);

    const closeButton = wrapper.find(".ta-mermaid-preview-btn-close");
    await closeButton.trigger("click");
    expect(wrapper.emitted("close")).toHaveLength(1);
  });

  it("renders empty error state in MermaidPreviewDialog when code is blank", () => {
    const wrapper = mount(MermaidPreviewDialog, {
      props: {
        code: "   ",
        title: "Mermaid 图表预览"
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    });

    expect(wrapper.find(".ta-mermaid-error").text()).toContain("内容为空，无法渲染图表");
  });
});
