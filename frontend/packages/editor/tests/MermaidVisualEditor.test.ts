import { defineComponent } from "vue";
import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type { MermaidGraph } from "../src/mermaid/model";
import {
  appendMermaidEdge,
  applyVueFlowPositions,
  toVueFlowEdges,
  toVueFlowNodes
} from "../src/mermaid/visual-editor/vue-flow-adapter";

vi.mock("@vue-flow/core", () => ({
  VueFlow: defineComponent({
    name: "VueFlow",
    props: ["nodes", "edges"],
    emits: ["nodeDragStop", "connect", "nodeClick"],
    template: `<div data-testid="vue-flow-mock">
      <button data-testid="mock-drag" @click="$emit('nodeDragStop', { node: { id: 'A', position: { x: 480, y: 260 } } })">drag</button>
      <button data-testid="mock-connect" @click="$emit('connect', { source: 'B', target: 'A' })">connect</button>
      <button data-testid="mock-select" @click="$emit('nodeClick', { node: { id: 'A' } })">select</button>
    </div>`
  }),
  Handle: defineComponent({ template: "<span />" }),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  MarkerType: { ArrowClosed: "arrowclosed" }
}));

import MermaidVisualEditor from "../src/mermaid/visual-editor/MermaidVisualEditor.vue";

const graph = (): MermaidGraph => ({
  kind: "flowchart",
  direction: "LR",
  nodes: [
    { id: "A", text: "开始", type: "rectangle", position: { x: 80, y: 70 } },
    { id: "B", text: "结束", type: "diamond", position: { x: 300, y: 70 } }
  ],
  edges: [{ id: "edge-1", source: "A", target: "B", label: "下一步", relation: "arrow" }],
  preservedLines: ["classDef important fill:red"]
});

describe("Mermaid Vue Flow 适配", () => {
  it("把领域节点和边映射为 Vue Flow 元素", () => {
    expect(toVueFlowNodes(graph())).toMatchObject([
      { id: "A", type: "mermaid", position: { x: 80, y: 70 }, data: { text: "开始", nodeType: "rectangle" } },
      { id: "B", type: "mermaid", position: { x: 300, y: 70 }, data: { text: "结束", nodeType: "diamond" } }
    ]);
    expect(toVueFlowEdges(graph())).toMatchObject([
      { id: "edge-1", source: "A", target: "B", label: "下一步", markerEnd: "arrowclosed" }
    ]);
  });

  it("只把 Vue Flow 拖拽坐标回写到领域模型副本", () => {
    const original = graph();
    const updated = applyVueFlowPositions(original, [{ id: "A", position: { x: 480, y: 260 } }]);

    expect(updated.nodes[0]?.position).toEqual({ x: 480, y: 260 });
    expect(original.nodes[0]?.position).toEqual({ x: 80, y: 70 });
  });

  it("通过 Handle 连接创建不重复的领域边", () => {
    const updated = appendMermaidEdge(graph(), { source: "B", target: "A" });

    expect(updated.edges.at(-1)).toEqual({
      id: "edge-2",
      source: "B",
      target: "A",
      label: "",
      relation: "arrow"
    });
    expect(appendMermaidEdge(updated, { source: "B", target: "A" })).toEqual(updated);
  });
});

describe("MermaidVisualEditor", () => {
  it("接收 Vue Flow 拖拽结束事件并上报新坐标", async () => {
    const { getByTestId, emitted } = render(MermaidVisualEditor, { props: { modelValue: graph() } });

    await fireEvent.click(getByTestId("mock-drag"));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].nodes[0]?.position).toEqual({ x: 480, y: 260 });
  });

  it("接收 Vue Flow connect 事件并新增连线", async () => {
    const { getByTestId, emitted } = render(MermaidVisualEditor, { props: { modelValue: graph() } });

    await fireEvent.click(getByTestId("mock-connect"));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].edges).toHaveLength(2);
    expect(updates.at(-1)?.[0].edges.at(-1)).toMatchObject({ source: "B", target: "A" });
  });

  it("可选择节点并修改名称、类型和删除关联边", async () => {
    const { getByTestId, getByLabelText, getByRole, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    await fireEvent.click(getByTestId("mock-select"));
    await fireEvent.update(getByLabelText("节点名称"), "准备");
    await fireEvent.update(getByLabelText("节点类型"), "rounded");
    await fireEvent.click(getByRole("button", { name: "删除节点" }));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.some(([value]) => value.nodes[0]?.text === "准备")).toBe(true);
    expect(updates.some(([value]) => value.nodes[0]?.type === "rounded")).toBe(true);
    expect(updates.at(-1)?.[0].nodes.map((node) => node.id)).toEqual(["B"]);
    expect(updates.at(-1)?.[0].edges).toEqual([]);
  });
});
