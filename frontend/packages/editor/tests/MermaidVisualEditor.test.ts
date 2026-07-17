/// <reference types="vite/client" />
import { defineComponent, ref } from "vue";
import { fireEvent, render, within } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type { EdgeProps } from "@vue-flow/core";
import type { MermaidGraph, MermaidNodeType } from "../src/mermaid/model";
import {
  appendMermaidEdge,
  applyVueFlowPositions,
  canAppendMermaidEdge,
  updateMermaidEdge,
  toVueFlowEdges,
  toVueFlowNodes
} from "../src/mermaid/visual-editor/vue-flow-adapter";

vi.mock("@vue-flow/core", () => ({
  VueFlow: defineComponent({
    name: "VueFlow",
    props: ["nodes", "edges", "nodesConnectable", "connectionMode", "connectOnClick"],
    emits: ["nodeDragStop", "connect", "nodeClick", "edgeClick", "quick-connect-test", "paneClick"],
    setup(_, { expose }) {
      expose({
        screenToFlowCoordinate: ({ x, y }: { x: number; y: number }) => ({ x: x - 100, y: y - 50 })
      });
    },
    template: `<div data-testid="vue-flow-mock">
      <slot name="node-mermaid" v-for="node in (nodes || [])" :key="node.id" :id="node.id" :data="node.data" />
      <div
        v-for="edge in (edges || [])"
        :key="edge.id"
        :data-testid="'mock-flow-edge-' + edge.id"
        :data-edge-selected="String(Boolean(edge.selected))"
        :data-edge-z-index="String(edge.zIndex ?? 0)"
      >
        <slot
          name="edge-mermaid-edge"
          :id="edge.id"
          :source="edge.source"
          :target="edge.target"
          :source-node="{ id: edge.source }"
          :target-node="{ id: edge.target }"
          :type="edge.type"
          :source-x="10"
          :source-y="20"
          :target-x="100"
          :target-y="20"
          source-position="right"
          target-position="left"
          :source-handle-id="edge.sourceHandle"
          :target-handle-id="edge.targetHandle"
          :marker-end="edge.markerEnd"
          marker-start=""
          :style="edge.style || {}"
          :data="edge.data || {}"
          :events="{}"
          :selected="Boolean(edge.selected)"
        />
      </div>
      <button data-testid="mock-drag" @click="$emit('nodeDragStop', { node: { id: 'A', position: { x: 480, y: 260 } } })">drag</button>
      <button data-testid="mock-connect" @click="$emit('connect', { source: 'B', target: 'A' })">connect</button>
      <button data-testid="mock-select" @click="$emit('nodeClick', { node: { id: 'A' } })">select</button>
      <button data-testid="mock-edge-click" @click="$emit('edgeClick', { edge: { id: 'edge-1' } })">edge-click</button>
      <button data-testid="mock-pane-click" @click="$emit('paneClick')">pane-click</button>
      <button data-testid="mock-quick-connect" @click="$emit('quick-connect-test', { portId: 'target-5', position: 'right', shapeType: 'diamond' })">quick-connect</button>
    </div>`
  }),
  Handle: defineComponent({
    props: ["id", "type", "position", "style", "connectable"],
    template: `<span
      data-testid="handle"
      :data-handle-id="id"
      :data-handle-type="type"
      :data-position="position"
      :data-connectable="String(connectable)"
      :style="style"
    />`
  }),
  BaseEdge: defineComponent({
    name: "BaseEdge",
    props: ["path", "style", "markerEnd", "markerStart", "interactionWidth"],
    template: `<path :d="path" data-testid="base-edge" :style="style" />`
  }),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  MarkerType: { ArrowClosed: "arrowclosed" },
  ConnectionMode: { Loose: "loose" },
  getSmoothStepPath: vi.fn(() => ["M0 0 L1 1"])
}));

import MermaidVisualEditor from "../src/mermaid/visual-editor/MermaidVisualEditor.vue";
import MermaidFlowNode from "../src/mermaid/visual-editor/MermaidFlowNode.vue";
import MermaidFlowEdge from "../src/mermaid/visual-editor/MermaidFlowEdge.vue";
import MermaidInlineEditor from "../src/mermaid/visual-editor/MermaidInlineEditor.vue";
import flowNodeSource from "../src/mermaid/visual-editor/MermaidFlowNode.vue?raw";
import visualEditorSource from "../src/mermaid/visual-editor/MermaidVisualEditor.vue?raw";

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

const routedGraph = (): MermaidGraph => {
  const value = graph();
  value.edges[0]!.route = {
    points: [
      { x: 140, y: 96 },
      { x: 220, y: 96 },
      { x: 220, y: 120 },
      { x: 300, y: 120 }
    ]
  };
  return value;
};

function terminalPathDelta(path: string): { x: number; y: number } {
  const values = path.match(/-?\d+(?:\.\d+)?/g)?.map(Number) ?? [];
  const points = Array.from({ length: Math.floor(values.length / 2) }, (_, index) => ({
    x: values[index * 2]!,
    y: values[index * 2 + 1]!
  }));
  const previous = points.at(-2)!;
  const last = points.at(-1)!;
  return { x: last.x - previous.x, y: last.y - previous.y };
}

describe("Mermaid Vue Flow 适配", () => {
  it("把领域节点和边映射为 Vue Flow 元素", () => {
    const styled = graph();
    styled.nodes[0]!.scale = 1.5;
    styled.nodes[0]!.style = { textColor: "#112233", fillColor: "#AABBCC", strokeColor: "#445566" };
    styled.edges[0]!.style = { textColor: "#778899" };
    expect(toVueFlowNodes(styled)).toMatchObject([
      {
        id: "A",
        type: "mermaid",
        position: { x: 80, y: 70 },
        data: {
          text: "开始",
          nodeType: "rectangle",
          scale: 1.5,
          style: { textColor: "#112233", fillColor: "#AABBCC", strokeColor: "#445566" }
        }
      },
      { id: "B", type: "mermaid", position: { x: 300, y: 70 }, data: { text: "结束", nodeType: "diamond" } }
    ]);
    expect(toVueFlowEdges(styled)).toMatchObject([
      {
        id: "edge-1",
        source: "A",
        target: "B",
        label: "下一步",
        markerEnd: "arrowclosed",
        data: { textColor: "#778899" }
      }
    ]);
  });

  it("把自动布局路由深拷贝到自定义边 data", () => {
    const original = routedGraph();
    const edge = toVueFlowEdges(original)[0]!;

    expect(edge.data).toEqual({ routePoints: original.edges[0]!.route!.points });
    expect(edge.data?.routePoints).not.toBe(original.edges[0]!.route!.points);
  });

  it("把同一节点的多条入边和出边依次分配到三个端口", () => {
    const multiEdgeGraph = graph();
    multiEdgeGraph.edges = Array.from({ length: 4 }, (_, index) => ({
      id: `edge-${index + 1}`,
      source: "A",
      target: "B",
      label: "",
      relation: "arrow" as const
    }));

    const edges = toVueFlowEdges(multiEdgeGraph);

    expect(edges.map((edge) => edge.sourceHandle)).toEqual(["source-0", "source-1", "source-2", "source-0"]);
    expect(edges.map((edge) => edge.targetHandle)).toEqual(["target-0", "target-1", "target-2", "target-0"]);
  });

  it("只把 Vue Flow 拖拽坐标回写到领域模型副本", () => {
    const original = routedGraph();
    const updated = applyVueFlowPositions(original, [{ id: "A", position: { x: 480, y: 260 } }]);

    expect(updated.nodes[0]?.position).toEqual({ x: 480, y: 260 });
    expect(updated.edges[0]?.route).toBeUndefined();
    expect(original.edges[0]?.route).toBeDefined();
    expect(original.nodes[0]?.position).toEqual({ x: 80, y: 70 });
  });

  it("通过 Handle 连接创建不重复的领域边", () => {
    const updated = appendMermaidEdge(graph(), {
      source: "B",
      target: "A",
      sourceHandle: "target-2",
      targetHandle: "source-1"
    });

    expect(updated.edges.at(-1)).toEqual({
      id: "edge-2",
      source: "B",
      target: "A",
      sourceHandle: "target-2",
      targetHandle: "source-1",
      label: "",
      relation: "arrow"
    });
    expect(appendMermaidEdge(updated, {
      source: "B",
      target: "A",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    })).toEqual(updated);
  });

  it("固定端口优先于旧边的自动分配", () => {
    const fixed = graph();
    Object.assign(fixed.edges[0]!, { sourceHandle: "target-2", targetHandle: "source-1" });

    expect(toVueFlowEdges(fixed)[0]).toMatchObject({
      sourceHandle: "target-2",
      targetHandle: "source-1"
    });
  });

  it("拒绝重复有向边和同节点同端口，允许不同端口自环", () => {
    const base = graph();
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    })).toBe(false);
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "A",
      sourceHandle: "source-0",
      targetHandle: "source-0"
    })).toBe(false);
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "A",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    })).toBe(true);
  });

  it("重连时排除自身后允许在同节点对上换端口", () => {
    const base = graph(); // 已有 A->B edge-1
    // 排除 edge-1 后，A->B 换端口不再判为重复
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    }, "edge-1")).toBe(true);
    // 不排除时仍是重复
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    })).toBe(false);
    // 排除自身后仍拒绝其它重复边
    const dup = graph();
    dup.edges.push({ id: "edge-2", source: "A", target: "B", label: "", relation: "arrow" });
    expect(canAppendMermaidEdge(dup, {
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    }, "edge-1")).toBe(false);
  });

  it("updateMermaidEdge 只更新被拖动的一端", () => {
    const base = routedGraph(); // edge-1: A -> B，带自动布局派生路由
    const reconnectedTarget = updateMermaidEdge(base, "edge-1", "target", {
      source: "A", target: "B", sourceHandle: "source-0", targetHandle: "target-2"
    });
    expect(reconnectedTarget.edges[0]).toMatchObject({ source: "A", target: "B", targetHandle: "target-2" });
    expect(reconnectedTarget.edges[0]?.route).toBeUndefined();
    // source 端保持不变（原边无 sourceHandle）
    expect(reconnectedTarget.edges[0]?.sourceHandle).toBeUndefined();

    const reconnectedSource = updateMermaidEdge(base, "edge-1", "source", {
      source: "B", target: "B", sourceHandle: "source-1", targetHandle: "target-0"
    });
    expect(reconnectedSource.edges[0]).toMatchObject({ source: "B", sourceHandle: "source-1", target: "B" });
    expect(reconnectedSource.edges[0]?.route).toBeUndefined();
    // target 端保持不变
    expect(reconnectedSource.edges[0]?.targetHandle).toBeUndefined();
    // 原图未被修改
    expect(base.edges[0]?.targetHandle).toBeUndefined();
  });
});

describe("MermaidFlowNode", () => {
  it.each([
    ["TD"] as const,
    ["TB"] as const,
    ["BT"] as const,
    ["LR"] as const,
    ["RL"] as const
  ])("在 %s 方向渲染矩形多连接点", (direction_tuple) => {
    const direction = direction_tuple[0] as MermaidGraph["direction"];
    const { getAllByTestId } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "开始", nodeType: "rectangle", direction }
      }
    });

    const handles = getAllByTestId("handle") as HTMLElement[];
    const targetHandles = handles.filter((handle) => handle.dataset.handleId?.startsWith("target-"));
    const sourceHandles = handles.filter((handle) => handle.dataset.handleId?.startsWith("source-"));

    // 矩形: 4顶点 + 每边2个 = 12个端口，target-0~5, source-0~5
    expect(targetHandles).toHaveLength(6);
    expect(sourceHandles).toHaveLength(6);
    expect(targetHandles.map((handle) => handle.dataset.handleId)).toEqual(
      ["target-0", "target-1", "target-2", "target-3", "target-4", "target-5"]
    );
    expect(sourceHandles.map((handle) => handle.dataset.handleId)).toEqual(
      ["source-0", "source-1", "source-2", "source-3", "source-4", "source-5"]
    );
    expect(handles.every((handle) => handle.dataset.handleType === "source")).toBe(true);
    expect(handles.every((handle) => handle.dataset.connectable === "false")).toBe(true);
  });

  it.each([
    ["stadium", 120, 52],
    ["rectangle", 120, 52],
    ["rounded", 120, 52],
    ["subroutine", 128, 52],
    ["database", 132, 68],
    ["circle", 92, 92],
    ["diamond", 150, 88],
    ["hexagon", 140, 72],
    ["parallelogram", 140, 64],
    ["trapezoid", 140, 64],
    ["double-circle", 100, 100],
    ["text", 100, 44],
    ["doc", 132, 68],
    ["docs", 140, 76]
  ] as const)("用共享 SVG 和尺寸渲染 %s 节点", (nodeType, width, height) => {
    const { container } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "节点", nodeType: nodeType as MermaidNodeType, direction: "LR" }
      }
    });

    const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
    expect(root.style.width).toBe(`${width}px`);
    expect(root.style.height).toBe(`${height}px`);
    expect(root.querySelector(`svg[data-mermaid-shape="${nodeType}"]`)).toBeTruthy();
  });

  it("按比例渲染实际尺寸，并把文字、填充和边框色传给共享轮廓", () => {
    const { container } = render(MermaidFlowNode, {
      props: {
        id: "A",
        selected: true,
        data: {
          text: "节点",
          nodeType: "rectangle",
          direction: "LR",
          scale: 1.5,
          style: { textColor: "#112233", fillColor: "#AABBCC", strokeColor: "#445566" }
        }
      }
    });

    const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
    const shape = root.querySelector<HTMLElement>("[data-mermaid-shape]")!;
    expect(root.style.width).toBe("180px");
    expect(root.style.height).toBe("78px");
    expect(root.style.color).toBe("rgb(17, 34, 51)");
    expect(shape.style.getPropertyValue("--ta-mermaid-fill")).toBe("#AABBCC");
    expect(shape.style.getPropertyValue("--ta-mermaid-stroke")).toBe("#445566");
    expect(shape.classList.contains("is-selected")).toBe(true);
  });

  it("选中节点显示四个外置缩放点，按下时发出角和 pointerId", () => {
    const { container, emitted } = render(MermaidFlowNode, {
      props: {
        id: "A",
        selected: true,
        resizeEnabled: true,
        data: { text: "节点", nodeType: "rectangle", direction: "LR" }
      }
    });
    const handles = container.querySelectorAll<HTMLElement>(".ta-mermaid-resize-handle");
    expect(handles).toHaveLength(4);
    expect(Array.from(handles).map((handle) => handle.dataset.resizeCorner)).toEqual(["nw", "ne", "sw", "se"]);

    const event = new MouseEvent("pointerdown", { bubbles: true, cancelable: true, button: 0 });
    Object.defineProperty(event, "pointerId", { value: 9 });
    handles[3]!.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    const resizeCalls = emitted().resizeStart as Array<[{ nodeId: string; corner: string; pointerId: number }]>;
    expect(resizeCalls[0]?.[0]).toEqual({ nodeId: "A", corner: "se", pointerId: 9 });
  });

  it("缩放被禁用或节点未选中时不显示缩放点", () => {
    for (const props of [
      { selected: false, resizeEnabled: true },
      { selected: true, resizeEnabled: false }
    ]) {
      const rendered = render(MermaidFlowNode, {
        props: {
          id: "A",
          ...props,
          data: { text: "节点", nodeType: "rectangle", direction: "LR" }
        }
      });
      expect(rendered.container.querySelectorAll(".ta-mermaid-resize-handle")).toHaveLength(0);
      rendered.unmount();
    }
  });

  it("双击节点主体发出就地编辑请求，双击连接点不触发", async () => {
    const { container, emitted } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "节点", nodeType: "rectangle", direction: "LR" }
      }
    });
    const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
    await fireEvent.dblClick(root, { clientX: 120, clientY: 80 });
    const editCalls = emitted().editRequest as Array<[{ nodeId: string; clientX: number; clientY: number }]>;
    expect(editCalls[0]?.[0]).toEqual({ nodeId: "A", clientX: 120, clientY: 80 });

    await fireEvent.dblClick(container.querySelector<HTMLElement>("[data-mermaid-handle]")!);
    expect(emitted().editRequest).toHaveLength(1);
  });

  it.each([
    ["subroutine", 2],
    ["database", 2],
    ["double-circle", 2],
    ["docs", 3],
    ["text", 0]
  ] as const)("让 %s SVG 包含 %s 个可见轮廓层", (nodeType, expectedLayers) => {
    const { container } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "节点", nodeType: nodeType as MermaidNodeType, direction: "LR" }
      }
    });
    const shape = container.querySelector(`svg[data-mermaid-shape="${nodeType}"]`)!;
    expect(shape.querySelectorAll("[data-mermaid-shape-layer]")).toHaveLength(expectedLayers);
  });

  it.each([
    ["TD"] as const,
    ["BT"] as const,
    ["LR"] as const,
    ["RL"] as const
  ])("让 %s 方向判断节点渲染12个端口", (direction_tuple) => {
    const direction = direction_tuple[0] as MermaidGraph["direction"];
    const { getAllByTestId } = render(MermaidFlowNode, {
      props: {
        id: "D",
        data: { text: "判断", nodeType: "diamond", direction }
      }
    });

    const handles = getAllByTestId("handle") as HTMLElement[];
    const targetHandles = handles.filter((handle) => handle.dataset.handleId?.startsWith("target-"));
    const sourceHandles = handles.filter((handle) => handle.dataset.handleId?.startsWith("source-"));

    // 菱形：4顶点 + 每条斜边上2个 = 12个端口，target-0~5, source-0~5
    expect(targetHandles).toHaveLength(6);
    expect(sourceHandles).toHaveLength(6);
    expect(handles.every((handle) => handle.dataset.handleType === "source")).toBe(true);
  });

  it("用水平 SVG 多边形绘制判断节点", () => {
    const { container } = render(MermaidFlowNode, {
      props: { id: "D", data: { text: "条件判断", nodeType: "diamond", direction: "LR" } }
    });

    expect(flowNodeSource).not.toContain("rotate(45deg)");
    expect(flowNodeSource).not.toContain("rotate(-45deg)");
    expect(visualEditorSource).not.toContain("rotate(45deg)");
    expect(container.querySelector('svg[data-mermaid-shape="diamond"] polygon')).toBeTruthy();
  });

  it("通用端口默认隐藏并在节点悬浮时显示", () => {
    expect(flowNodeSource).toContain("opacity: 0");
    expect(flowNodeSource).toContain(".ta-mermaid-flow-node:hover");
  });

  it("快捷箭头按方向选中该边上的连接点作为起始点", async () => {
    vi.useFakeTimers();
    try {
      const { container, emitted } = render(MermaidFlowNode, {
        props: {
          id: "A",
          data: { text: "开始", nodeType: "rectangle", direction: "LR" }
        }
      });
      // 鼠标悬浮节点才显示快捷箭头（与是否选中无关）
      await fireEvent.mouseEnter(container.querySelector("[data-mermaid-node-id]")!);
      vi.advanceTimersByTime(300);
      await Promise.resolve();

      // 矩形长边 5 个点，短边 3 个点，每边都有一个点在最中间；
      // 现在应取各边上最接近中点的端口，使起始点落在箭头所在边上。
      const cases = [
        { cls: "is-top", dir: "top", expectedPort: "source-2" },
        { cls: "is-bottom", dir: "bottom", expectedPort: "target-4" },
        { cls: "is-left", dir: "left", expectedPort: "target-5" },
        { cls: "is-right", dir: "right", expectedPort: "source-5" }
      ];
      for (const item of cases) {
        const arrow = container.querySelector<HTMLElement>(
          `.ta-mermaid-quick-connector-wrapper.${item.cls} .ta-mermaid-quick-arrow`
        )!;
        await fireEvent.mouseEnter(arrow);
        vi.advanceTimersByTime(200);
        await Promise.resolve();
        const button = document.body.querySelector<HTMLElement>(".ta-mermaid-quick-menu button");
        expect(button).toBeTruthy();
        await fireEvent.click(button!);
      }

      const payloads = emitted().quickConnect as Array<
        [{ portId: string; position: string; shapeType: string }]
      >;
      expect(payloads).toHaveLength(4);
      const portByDir = new Map(payloads.map(([payload]) => [payload.position, payload.portId]));
      expect(portByDir.get("top")).toBe("source-2");
      expect(portByDir.get("bottom")).toBe("target-4");
      expect(portByDir.get("left")).toBe("target-5");
      expect(portByDir.get("right")).toBe("source-5");
    } finally {
      vi.useRealTimers();
    }
  });

  it("点击快捷箭头不会误触发端口连线拖拽", async () => {
    vi.useFakeTimers();
    try {
      const { container, emitted } = render(MermaidFlowNode, {
        props: { id: "A", data: { text: "开始", nodeType: "rectangle", direction: "LR" } }
      });
      const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
      await fireEvent.mouseEnter(root);
      vi.advanceTimersByTime(300);
      await Promise.resolve();

      await fireEvent.mouseEnter(container.querySelector<HTMLElement>(".ta-mermaid-quick-arrow")!);
      vi.advanceTimersByTime(200);
      await Promise.resolve();

      const button = document.body.querySelector<HTMLElement>(".ta-mermaid-quick-menu button")!;
      // pointerdown 冒泡到箭头时被 @pointerdown.stop 拦截，不会发起端口连线拖拽
      await fireEvent.pointerDown(button);
      expect(emitted().connectionStart ?? []).toHaveLength(0);

      // click 仍能正常触发快捷建连
      await fireEvent.click(button);
      expect(emitted().quickConnect).toHaveLength(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it("快捷建连使用包含十四种中文名称的双列菜单", async () => {
    vi.useFakeTimers();
    try {
      const { container } = render(MermaidFlowNode, {
        props: { id: "A", data: { text: "开始", nodeType: "rectangle", direction: "LR" } }
      });
      await fireEvent.mouseEnter(container.querySelector("[data-mermaid-node-id]")!);
      vi.advanceTimersByTime(300);
      await Promise.resolve();

      await fireEvent.mouseEnter(container.querySelector<HTMLElement>(".ta-mermaid-quick-arrow")!);
      vi.advanceTimersByTime(200);
      await Promise.resolve();

      const menu = document.body.querySelector<HTMLElement>(".ta-mermaid-quick-menu")!;
      const buttons = within(menu).getAllByRole("button");
      expect(menu.classList.contains("is-two-column")).toBe(true);
      expect(buttons.map((button) => button.textContent?.trim())).toEqual([
        "开始/结束", "普通处理步骤", "圆角处理节点", "子程序", "数据库", "连接点", "条件判断",
        "准备步骤", "输入或输出", "人工处理", "终止节点", "文本块", "文档", "多文档"
      ]);
      expect(menu.querySelector('svg[data-mermaid-shape="text"] [data-mermaid-shape-thumbnail]')).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it("快捷菜单使用屏幕空间浮层并在视口边缘自动翻转", async () => {
    vi.useFakeTimers();
    try {
      const { container } = render(MermaidFlowNode, {
        props: { id: "A", data: { text: "开始", nodeType: "rectangle", direction: "LR" } }
      });
      const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
      await fireEvent.mouseEnter(root);
      vi.advanceTimersByTime(300);
      await Promise.resolve();

      const cases = [
        { direction: "top", expected: "bottom", rect: { left: 490, right: 510, top: 4, bottom: 24 } },
        { direction: "bottom", expected: "top", rect: { left: 490, right: 510, top: 744, bottom: 764 } },
        { direction: "left", expected: "right", rect: { left: 4, right: 24, top: 374, bottom: 394 } },
        { direction: "right", expected: "left", rect: { left: 1000, right: 1020, top: 374, bottom: 394 } }
      ];
      for (const item of cases) {
        const arrow = container.querySelector<HTMLElement>(
          `.ta-mermaid-quick-connector-wrapper.is-${item.direction} .ta-mermaid-quick-arrow`
        )!;
        Object.defineProperty(arrow, "getBoundingClientRect", {
          configurable: true,
          value: () => ({
            ...item.rect,
            width: item.rect.right - item.rect.left,
            height: item.rect.bottom - item.rect.top,
            x: item.rect.left,
            y: item.rect.top,
            toJSON: () => ({})
          })
        });

        await fireEvent.mouseEnter(arrow);
        vi.advanceTimersByTime(200);
        await Promise.resolve();
        const menu = document.body.querySelector<HTMLElement>(".ta-mermaid-quick-menu")!;
        expect(menu).toBeTruthy();
        expect(container.contains(menu)).toBe(false);
        expect(menu.classList.contains(`is-placement-${item.expected}`)).toBe(true);
        expect(menu.style.position).toBe("fixed");
      }
      expect(flowNodeSource).toContain('<Teleport to="body">');
    } finally {
      vi.useRealTimers();
    }
  });

  it("从快捷箭头移回节点后只关闭菜单并保持四向箭头可见", async () => {
    vi.useFakeTimers();
    try {
      const { container } = render(MermaidFlowNode, {
        props: { id: "A", data: { text: "开始", nodeType: "rectangle", direction: "LR" } }
      });
      const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
      await fireEvent.mouseEnter(root);
      vi.advanceTimersByTime(300);
      await Promise.resolve();

      const arrow = container.querySelector<HTMLElement>(".ta-mermaid-quick-arrow")!;
      await fireEvent.mouseEnter(arrow);
      vi.advanceTimersByTime(200);
      await Promise.resolve();
      expect(document.body.querySelector(".ta-mermaid-quick-menu")).toBeTruthy();

      await fireEvent.mouseLeave(arrow);
      vi.advanceTimersByTime(200);
      await Promise.resolve();

      expect(document.body.querySelector(".ta-mermaid-quick-menu")).toBeNull();
      expect(container.querySelectorAll(".ta-mermaid-quick-connector-wrapper")).toHaveLength(4);
    } finally {
      vi.useRealTimers();
    }
  });

  it("键盘聚焦节点后可以发现并聚焦四向快捷按钮", async () => {
    const { container } = render(MermaidFlowNode, {
      props: { id: "A", data: { text: "开始", nodeType: "rectangle", direction: "LR" } }
    });
    const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;

    expect(root.tabIndex).toBe(0);
    await fireEvent.focus(root);

    const arrows = container.querySelectorAll<HTMLButtonElement>(".ta-mermaid-quick-arrow");
    expect(arrows).toHaveLength(4);
    expect(Array.from(arrows).every((arrow) => arrow.type === "button")).toBe(true);
    expect(Array.from(arrows).map((arrow) => arrow.getAttribute("aria-label"))).toEqual([
      "上方快捷建连", "下方快捷建连", "左侧快捷建连", "右侧快捷建连"
    ]);
  });

  it("节点根元素在 18px 内命中任一端口并发起拖线", () => {
    const { container, getAllByTestId, emitted } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "开始", nodeType: "rectangle", direction: "LR" }
      }
    });
    const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
    const handles = getAllByTestId("handle") as HTMLElement[];
    // 矩形现有 12 个端口
    expect(handles).toHaveLength(12);
    handles.forEach((handle, index) => {
      Object.defineProperty(handle, "getBoundingClientRect", {
        configurable: true,
        value: () => ({ left: index * 30, top: 0, right: index * 30 + 16, bottom: 16, width: 16, height: 16 })
      });
      const event = new MouseEvent("pointerdown", {
        bubbles: true,
        cancelable: true,
        button: 0,
        clientX: index * 30 + 8,
        clientY: 8
      });
      Object.defineProperty(event, "pointerId", { value: index + 1 });
      root.dispatchEvent(event);
    });

    expect(emitted().connectionStart).toHaveLength(12);
    const starts = emitted().connectionStart as Array<[{ handleId: string }]>;
    // 12 个端口依次触发 connectionStart
    expect(starts.map(([start]) => start.handleId)).toHaveLength(12);

    const mouseDown = new MouseEvent("mousedown", {
      bubbles: true,
      cancelable: true,
      button: 0,
      clientX: 8,
      clientY: 8
    });
    root.dispatchEvent(mouseDown);
    expect(mouseDown.defaultPrevented).toBe(true);
  });

  it.each([
    "stadium", "rectangle", "rounded", "subroutine", "database", "circle", "diamond",
    "hexagon", "parallelogram", "trapezoid", "double-circle", "text", "doc", "docs"
  ] as const)("%s 节点无论是否选中，直接按住 Handle 均可发起拖线", (nodeType) => {
    for (const selected of [false, true]) {
      const { container, emitted, unmount } = render(MermaidFlowNode, {
        props: {
          id: "A",
          data: { text: "节点", nodeType, direction: "LR" },
          selected
        }
      });
      const handle = container.querySelector<HTMLElement>('[data-testid="handle"]')!;
      Object.defineProperty(handle, "getBoundingClientRect", {
        configurable: true,
        value: () => ({ left: 0, top: 0, right: 16, bottom: 16, width: 16, height: 16 })
      });
      const event = new MouseEvent("pointerdown", {
        bubbles: true,
        cancelable: true,
        button: 0,
        clientX: 8,
        clientY: 8
      });
      Object.defineProperty(event, "pointerId", { value: 7 });

      handle.dispatchEvent(event);

      expect(event.defaultPrevented).toBe(true);
      expect(emitted().connectionStart).toHaveLength(1);
      expect((emitted().connectionStart as Array<[{ handleId: string }]>)[0]?.[0].handleId)
        .toBe(handle.dataset.handleId);
      unmount();
    }
  });

  it("选中节点只在直接 Handle 上阻止节点拖动，18px 外围仍交给节点移动", () => {
    const { container, getAllByTestId } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "节点", nodeType: "rectangle", direction: "LR" },
        selected: true
      }
    });
    const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
    const handle = getAllByTestId("handle")[0] as HTMLElement;
    Object.defineProperty(handle, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 0, top: 0, right: 16, bottom: 16, width: 16, height: 16 })
    });

    const directMouseDown = new MouseEvent("mousedown", {
      bubbles: true,
      cancelable: true,
      button: 0,
      clientX: 8,
      clientY: 8
    });
    handle.dispatchEvent(directMouseDown);
    expect(directMouseDown.defaultPrevented).toBe(true);

    const expandedMouseDown = new MouseEvent("mousedown", {
      bubbles: true,
      cancelable: true,
      button: 0,
      clientX: 25,
      clientY: 8
    });
    root.dispatchEvent(expandedMouseDown);
    expect(expandedMouseDown.defaultPrevented).toBe(false);
  });

  it("拖线时区分起点、当前目标和有效或无效吸附状态", () => {
    const { container } = render(MermaidFlowNode, {
      props: {
        id: "B",
        data: { text: "结束", nodeType: "rectangle", direction: "LR" },
        connectionSourceHandleId: "source-0",
        isConnectionTarget: true,
        snappedHandleId: "target-1",
        connectionStatus: "invalid"
      }
    });

    expect(container.querySelector(".is-connection-source")).toBeTruthy();
    expect(container.querySelector(".is-connection-target")).toBeTruthy();
    expect(container.querySelector(".is-snapped-invalid")).toBeTruthy();
  });
});

describe("MermaidFlowEdge", () => {
  const edgeProps = (selected = false) =>
    ({
      id: "edge-1",
      source: "A",
      target: "B",
      sourceNode: { id: "A" },
      targetNode: { id: "B" },
      type: "mermaid-edge",
      sourceX: 10,
      sourceY: 20,
      sourcePosition: "right",
      targetX: 100,
      targetY: 20,
      targetPosition: "left",
      sourceHandleId: "source-0",
      targetHandleId: "target-1",
      markerEnd: "url(#ta-mermaid-preview-arrow)",
      markerStart: "",
      style: {},
      data: {},
      events: {},
      selected
    }) as unknown as EdgeProps;

  it("未选中不显示端点圆圈，选中后显示两个", () => {
    const idle = render(MermaidFlowEdge, { props: edgeProps(false) });
    expect(idle.container.querySelectorAll(".ta-mermaid-edge-handle")).toHaveLength(0);

    const active = render(MermaidFlowEdge, { props: edgeProps(true) });
    const handles = active.container.querySelectorAll(".ta-mermaid-edge-handle");
    expect(handles).toHaveLength(2);
    expect(Array.from(handles).every((handle) => handle.getAttribute("pointer-events") === "all")).toBe(true);
  });

  it("有文字时在边中点渲染标签，无文字不渲染", () => {
    const withLabel = render(MermaidFlowEdge, {
      props: { ...edgeProps(true), label: "下一步" } as unknown as EdgeProps
    });
    const labelEl = withLabel.container.querySelector(".ta-mermaid-edge-label");
    expect(labelEl).toBeTruthy();
    expect(labelEl?.textContent).toContain("下一步");

    const withoutLabel = render(MermaidFlowEdge, { props: edgeProps(true) });
    expect(withoutLabel.container.querySelector(".ta-mermaid-edge-label")).toBeNull();
  });

  it("使用领域边样式渲染标签文字颜色并保留白色光晕", () => {
    const { container } = render(MermaidFlowEdge, {
      props: {
        ...edgeProps(),
        label: "下一步",
        data: { textColor: "#123456" }
      } as unknown as EdgeProps
    });
    const label = container.querySelector<SVGTextElement>(".ta-mermaid-edge-label")!;
    expect(label.style.fill).toBe("rgb(18, 52, 86)");
    expect(label.classList.contains("ta-mermaid-edge-label")).toBe(true);
  });

  it("空连线也渲染可双击命中的透明路径并发出编辑请求", async () => {
    const { container, emitted } = render(MermaidFlowEdge, { props: edgeProps() });
    const hitbox = container.querySelector<SVGPathElement>(".ta-mermaid-edge-edit-hitbox")!;
    expect(hitbox).toBeTruthy();
    await fireEvent.dblClick(hitbox, { clientX: 210, clientY: 120 });
    const editCalls = emitted().editRequest as Array<[{ edgeId: string; clientX: number; clientY: number }]>;
    expect(editCalls[0]?.[0]).toEqual({ edgeId: "edge-1", clientX: 210, clientY: 120 });
  });

  it("优先按自动布局路由绘制圆角正交 path，并按路径长度定位标签", () => {
    const { getByTestId, container } = render(MermaidFlowEdge, {
      props: {
        ...edgeProps(),
        targetY: 80,
        label: "路径标签",
        data: {
          routePoints: [
            { x: 10, y: 20 },
            { x: 50, y: 20 },
            { x: 50, y: 80 },
            { x: 100, y: 80 }
          ]
        }
      } as unknown as EdgeProps
    });

    expect(getByTestId("base-edge").getAttribute("d")).toContain("Q");
    const label = container.querySelector(".ta-mermaid-edge-label");
    expect(label?.getAttribute("x")).toBe("50");
    expect(label?.getAttribute("y")).toBe("55");
  });

  it("节点实际尺寸与 ELK 估算有轻微偏差时仍用正交适配段连接端口", () => {
    const { getByTestId } = render(MermaidFlowEdge, {
      props: {
        ...edgeProps(),
        sourceY: 20,
        targetX: 100,
        targetY: 80,
        data: {
          routePoints: [
            { x: 12, y: 22 },
            { x: 50, y: 22 },
            { x: 50, y: 78 },
            { x: 98, y: 78 }
          ]
        }
      } as unknown as EdgeProps
    });

    const path = getByTestId("base-edge").getAttribute("d");
    expect(path).toContain("Q");
    expect(path).not.toBe("M0 0 L1 1");
  });

  it("顶部目标 Handle 与 ELK 端点偏移时，箭头末段仍向下进入节点", () => {
    const { getByTestId } = render(MermaidFlowEdge, {
      props: {
        ...edgeProps(),
        sourceX: 507.6,
        sourceY: 303.3,
        sourcePosition: "bottom",
        targetX: 519,
        targetY: 406,
        targetPosition: "top",
        data: {
          routePoints: [
            { x: 507.6, y: 303.3 },
            { x: 507.6, y: 318 },
            { x: 520, y: 318 },
            { x: 520, y: 414 }
          ]
        }
      } as unknown as EdgeProps
    });

    const path = getByTestId("base-edge").getAttribute("d") ?? "";
    expect(terminalPathDelta(path)).toEqual({ x: 0, y: expect.any(Number) });
    expect(terminalPathDelta(path).y).toBeGreaterThan(0);
  });

  it("没有有效路由时保留 SmoothStep 兼容回退", () => {
    const { getByTestId } = render(MermaidFlowEdge, { props: edgeProps() });
    expect(getByTestId("base-edge").getAttribute("d")).toBe("M0 0 L1 1");
  });

  it("两点直线路由没有安全内部轨道时回退 SmoothStep", () => {
    const { getByTestId } = render(MermaidFlowEdge, {
      props: {
        ...edgeProps(),
        data: { routePoints: [{ x: 10, y: 20 }, { x: 100, y: 20 }] }
      } as unknown as EdgeProps
    });

    expect(getByTestId("base-edge").getAttribute("d")).toBe("M0 0 L1 1");
  });

  it("按下端点圆圈发出重连起点（带固定端信息）", async () => {
    const { container, emitted } = render(MermaidFlowEdge, { props: edgeProps(true) });
    const handles = container.querySelectorAll<HTMLElement>(".ta-mermaid-edge-handle");
    // 第一个圆圈在 sourceX（source 端），第二个在 targetX（target 端）
    await fireEvent.pointerDown(handles[1]!); // 拖动 target 端 -> 固定 source
    let calls = emitted().reconnectStart as Array<[Record<string, unknown>]> | undefined;
    expect(calls!.at(-1)![0]).toMatchObject({
      edgeId: "edge-1",
      end: "target",
      fixedNodeId: "A",
      fixedHandleId: "source-0",
      fixedPosition: "right"
    });

    await fireEvent.pointerDown(handles[0]!); // 拖动 source 端 -> 固定 target
    calls = emitted().reconnectStart as Array<[Record<string, unknown>]> | undefined;
    expect(calls!.at(-1)![0]).toMatchObject({
      edgeId: "edge-1",
      end: "source",
      fixedNodeId: "B",
      fixedHandleId: "target-1",
      fixedPosition: "left"
    });
  });
});

describe("MermaidInlineEditor", () => {
  it("非法 HEX 不更新草稿，合法三位 HEX 规范化后可按 Enter 提交", async () => {
    const { getByLabelText, getByRole, emitted } = render(MermaidInlineEditor, {
      props: { kind: "node", text: "开始", position: { left: "8px", top: "8px" } }
    });
    await fireEvent.update(getByLabelText("文字颜色"), "red");
    expect(getByRole("alert").textContent).toContain("#RGB");
    await fireEvent.update(getByLabelText("文字颜色"), "#abc");
    await fireEvent.update(getByLabelText("节点文字"), "处理");
    await fireEvent.keyDown(getByLabelText("节点文字"), { key: "Enter", ctrlKey: true });

    const commitCalls = emitted().commit as Array<[{ text: string; textColor?: string }]>;
    expect(commitCalls[0]?.[0]).toEqual({ text: "处理", textColor: "#AABBCC" });
  });

  it("恢复默认颜色后提交 undefined", async () => {
    const { getByRole, emitted } = render(MermaidInlineEditor, {
      props: {
        kind: "edge",
        text: "通过",
        textColor: "#123456",
        position: { left: "8px", top: "8px" }
      }
    });
    await fireEvent.click(getByRole("button", { name: "恢复默认连线文字颜色" }));
    await fireEvent.click(getByRole("button", { name: "完成编辑" }));
    const commitCalls = emitted().commit as Array<[{ text: string; textColor?: string }]>;
    expect(commitCalls[0]?.[0]).toEqual({ text: "通过", textColor: undefined });
  });

  it("支持多行文字并可以通过 Ctrl+Enter 提交", async () => {
    const { getByLabelText, emitted } = render(MermaidInlineEditor, {
      props: { kind: "node", text: "第一行\n第二行", position: { left: "8px", top: "8px" } }
    });
    const textarea = getByLabelText("节点文字") as HTMLTextAreaElement;
    expect(textarea.value).toBe("第一行\n第二行");
    await fireEvent.update(textarea, "新第一行\n新第二行");
    // 普通 Enter 不提交
    await fireEvent.keyDown(textarea, { key: "Enter" });
    expect(emitted().commit).toBeUndefined();
    // Ctrl+Enter 提交
    await fireEvent.keyDown(textarea, { key: "Enter", ctrlKey: true });
    const commitCalls = emitted().commit as Array<[{ text: string; textColor?: string }]>;
    expect(commitCalls[0]?.[0]).toEqual({ text: "新第一行\n新第二行", textColor: undefined });
  });
});

describe("MermaidVisualEditor", () => {
  it("节点图形库使用三列紧凑布局压缩纵向高度", () => {
    expect(visualEditorSource).toContain(
      ".ta-mermaid-palette__grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 5px; }"
    );
    expect(visualEditorSource).toContain(
      ".ta-mermaid-inspector button.ta-mermaid-palette__item { display: flex; min-width: 0; min-height: 30px; padding: 1px 3px;"
    );
    expect(visualEditorSource).toContain(
      ".ta-mermaid-palette__preview { position: relative; display: grid; width: 70px; height: 26px;"
    );
    expect(visualEditorSource).toContain(
      "[data-mermaid-shape=\"double-circle\"] { left: 18px; top: -4px; width: 34px; height: 34px; }"
    );
  });

  it("按流程图和文档与显示分组提供十四种节点图形", () => {
    const { getByRole, queryByRole } = render(MermaidVisualEditor, { props: { modelValue: graph() } });

    expect(getByRole("heading", { name: "流程图" })).toBeTruthy();
    expect(getByRole("heading", { name: "文档与显示" })).toBeTruthy();
    for (const label of [
      "开始/结束", "普通处理步骤", "圆角处理节点", "子程序", "数据库", "连接点", "条件判断",
      "准备步骤", "输入或输出", "人工处理", "终止节点", "文本块", "文档", "多文档"
    ]) {
      const accessibleName = `添加${label}${label.endsWith("节点") ? "" : "节点"}`;
      expect(getByRole("button", { name: accessibleName }).textContent?.trim()).toBe(label);
    }
    expect(queryByRole("button", { name: "新增节点" })).toBeNull();
    expect(queryByRole("button", { name: "新增连线" })).toBeNull();
    expect(queryByRole("heading", { name: "连线" })).toBeNull();
  });

  it.each([
    ["stadium", "开始/结束"],
    ["rectangle", "普通处理步骤"],
    ["rounded", "圆角处理节点"],
    ["subroutine", "子程序"],
    ["database", "数据库"],
    ["circle", "连接点"],
    ["diamond", "条件判断"],
    ["hexagon", "准备步骤"],
    ["parallelogram", "输入或输出"],
    ["trapezoid", "人工处理"],
    ["double-circle", "终止节点"],
    ["text", "文本块"],
    ["doc", "文档"],
    ["docs", "多文档"]
  ] as const)("点击图形库按钮创建 %s 节点", async (type, label) => {
    const { getByRole, emitted } = render(MermaidVisualEditor, { props: { modelValue: graph() } });
    const accessibleName = `添加${label}${label.endsWith("节点") ? "" : "节点"}`;

    await fireEvent.click(getByRole("button", { name: accessibleName }));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].nodes.at(-1)).toMatchObject({
      id: "N3",
      text: "新节点",
      type
    });
  });

  it("创建节点后在图形库下方立即显示当前节点属性", async () => {
    const EditorHost = defineComponent({
      components: { MermaidVisualEditor },
      setup() {
        return { model: ref(graph()) };
      },
      template: `<MermaidVisualEditor v-model="model" />`
    });
    const { getByLabelText, getByRole, getByText } = render(EditorHost);

    await fireEvent.click(getByRole("button", { name: "添加圆角处理节点" }));

    expect(getByText("节点ID：N3")).toBeTruthy();
    expect((getByLabelText("节点类型") as HTMLSelectElement).value).toBe("rounded");
    expect(within(getByLabelText("节点类型")).getAllByRole("option").map((option) => option.textContent)).toEqual([
      "开始/结束", "普通处理步骤", "圆角处理节点", "子程序", "数据库", "连接点", "条件判断",
      "准备步骤", "输入或输出", "人工处理", "终止节点", "文本块", "文档", "多文档"
    ]);
  });

  it("属性栏显示缩放比例和实际尺寸，并可恢复默认尺寸", async () => {
    const model = routedGraph();
    model.nodes[0]!.scale = 1.5;
    const { getByTestId, getByText, getByRole, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: model }
    });

    await fireEvent.click(getByTestId("mock-select"));
    expect(getByText("150%")).toBeTruthy();
    expect(getByText("180 × 78 px")).toBeTruthy();
    await fireEvent.click(getByRole("button", { name: "恢复默认尺寸" }));

    const updated = (emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0];
    expect(updated?.nodes[0]?.scale).toBeUndefined();
    expect(updated?.edges[0]?.route).toBeUndefined();
  });

  it("双击节点打开草稿浮层，完成时一次提交文字和文字颜色", async () => {
    const { container, getByLabelText, getByRole, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: routedGraph() }
    });
    await fireEvent.dblClick(container.querySelector<HTMLElement>('[data-mermaid-node-id="A"]')!, {
      clientX: 240,
      clientY: 160
    });

    const dialog = within(getByRole("dialog", { name: "编辑节点" }));
    const text = dialog.getByLabelText("节点文字") as HTMLInputElement;
    expect(text.value).toBe("开始");
    await fireEvent.update(text, "准备");
    await fireEvent.update(dialog.getByLabelText("文字颜色"), "#abc");
    expect(emitted()["update:modelValue"]).toBeUndefined();
    await fireEvent.click(dialog.getByRole("button", { name: "完成编辑" }));

    const updated = (emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0];
    expect(updated?.nodes[0]).toMatchObject({ text: "准备", style: { textColor: "#AABBCC" } });
    expect(updated?.edges[0]?.route).toBeUndefined();
  });

  it("Esc 或取消丢弃节点浮层草稿", async () => {
    const { container, getByLabelText, emitted, queryByLabelText } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });
    await fireEvent.dblClick(container.querySelector<HTMLElement>('[data-mermaid-node-id="A"]')!);
    await fireEvent.update(getByLabelText("节点文字"), "不保存");
    await fireEvent.keyDown(getByLabelText("节点文字"), { key: "Escape" });

    expect(queryByLabelText("节点文字")).toBeNull();
    expect(emitted()["update:modelValue"]).toBeUndefined();
  });

  it("双击空连线可新增文字和颜色，修改样式时保留路由", async () => {
    const model = routedGraph();
    model.edges[0]!.label = "";
    const { container, getByLabelText, getByRole, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: model }
    });
    await fireEvent.dblClick(container.querySelector<SVGPathElement>(".ta-mermaid-edge-edit-hitbox")!, {
      clientX: 260,
      clientY: 180
    });
    const dialog = within(getByRole("dialog", { name: "编辑连线" }));
    await fireEvent.update(dialog.getByLabelText("连线文字"), "通过");
    await fireEvent.update(dialog.getByLabelText("连线文字颜色"), "#123456");
    await fireEvent.click(dialog.getByRole("button", { name: "完成编辑" }));

    const updated = (emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0];
    expect(updated?.edges[0]).toMatchObject({ label: "通过", style: { textColor: "#123456" } });
    expect(updated?.edges[0]?.route?.points).toEqual(model.edges[0]!.route!.points);
  });

  it("右侧节点和连线属性同步设置颜色，文本块禁用表面颜色", async () => {
    const NodeHost = defineComponent({
      components: { MermaidVisualEditor },
      setup() { return { model: ref(graph()) }; },
      template: `<MermaidVisualEditor v-model="model" /><pre data-testid="node-style-json">{{ JSON.stringify(model.nodes[0].style) }}</pre>`
    });
    const nodeView = render(NodeHost);
    await fireEvent.click(nodeView.getByTestId("mock-select"));
    await fireEvent.dblClick(nodeView.container.querySelector<HTMLElement>('[data-mermaid-node-id="A"]')!);
    await fireEvent.update(nodeView.getByLabelText("文字颜色"), "#112233");
    await fireEvent.click(nodeView.getByRole("button", { name: "完成编辑" }));
    await fireEvent.update(nodeView.getByLabelText("填充颜色"), "#aabbcc");
    await fireEvent.update(nodeView.getByLabelText("边框颜色"), "#445566");
    expect(JSON.parse(nodeView.getByTestId("node-style-json").textContent ?? "{}" )).toEqual({
      textColor: "#112233",
      fillColor: "#AABBCC",
      strokeColor: "#445566"
    });
    nodeView.unmount();

    const textModel = graph();
    textModel.nodes[0]!.type = "text";
    const textView = render(MermaidVisualEditor, { props: { modelValue: textModel } });
    await fireEvent.click(textView.getByTestId("mock-select"));
    expect((textView.getByLabelText("填充颜色") as HTMLInputElement).disabled).toBe(true);
    expect((textView.getByLabelText("边框颜色") as HTMLInputElement).disabled).toBe(true);
    expect(textView.getByText("文本块没有可见填充或边框")).toBeTruthy();
    textView.unmount();

    const EdgeHost = defineComponent({
      components: { MermaidVisualEditor },
      setup() { return { model: ref(routedGraph()) }; },
      template: `<MermaidVisualEditor v-model="model" /><pre data-testid="edge-json">{{ JSON.stringify(model.edges[0]) }}</pre>`
    });
    const edgeView = render(EdgeHost);
    await fireEvent.click(edgeView.getByTestId("mock-edge-click"));
    await fireEvent.update(edgeView.getByLabelText("连线文字颜色"), "#778899");
    const edgeUpdate = JSON.parse(edgeView.getByTestId("edge-json").textContent ?? "{}");
    expect(edgeUpdate.style).toEqual({ textColor: "#778899" });
    expect(edgeUpdate.route).toBeDefined();
  });

  it("手工输入六位 HEX 时三位前缀不会被提前扩展", async () => {
    const NodeHost = defineComponent({
      components: { MermaidVisualEditor },
      setup() { return { model: ref(graph()) }; },
      template: `<MermaidVisualEditor v-model="model" />`
    });
    const view = render(NodeHost);
    await fireEvent.click(view.getByTestId("mock-select"));
    await fireEvent.dblClick(view.container.querySelector<HTMLElement>('[data-mermaid-node-id="A"]')!);
    const input = view.getByLabelText("文字颜色") as HTMLInputElement;

    await fireEvent.update(input, "#123");
    expect(input.value).toBe("#123");
    await fireEvent.update(input, "#123456");
    expect(input.value).toBe("#123456");
  });

  it("把节点类型拖到画布落点后创建节点", async () => {
    const { getByLabelText, emitted } = render(MermaidVisualEditor, { props: { modelValue: graph() } });
    const dataTransfer = {
      dropEffect: "none",
      effectAllowed: "all",
      getData: vi.fn(() => "circle"),
      setData: vi.fn()
    };

    const dropEvent = new Event("drop", { bubbles: true, cancelable: true });
    Object.defineProperties(dropEvent, {
      clientX: { value: 420 },
      clientY: { value: 260 },
      dataTransfer: { value: dataTransfer }
    });
    fireEvent(getByLabelText("Mermaid 可视化画布"), dropEvent);

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].nodes.at(-1)).toMatchObject({
      id: "N3",
      text: "新节点",
      type: "circle",
      position: { x: 320, y: 210 }
    });
  });

  it("接收 Vue Flow 拖拽结束事件并上报新坐标", async () => {
    const { getByTestId, emitted } = render(MermaidVisualEditor, { props: { modelValue: routedGraph() } });

    await fireEvent.click(getByTestId("mock-drag"));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].nodes[0]?.position).toEqual({ x: 480, y: 260 });
    expect(updates.at(-1)?.[0].edges[0]?.route).toBeUndefined();
  });

  it("修改节点几何或图方向时清除陈旧路由", async () => {
    const { getByTestId, getByLabelText, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: routedGraph() }
    });

    await fireEvent.click(getByTestId("mock-select"));
    await fireEvent.update(getByLabelText("节点类型"), "database");
    await fireEvent.update(getByLabelText("图方向"), "TD");

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.every(([value]) => value.edges[0]?.route === undefined)).toBe(true);
  });

  it("切换节点类型时迁移关联端口并清除旧路由", async () => {
    const model = routedGraph();
    model.edges[0]!.sourceHandle = "target-5";
    model.edges[0]!.targetHandle = "target-0";
    const { getByTestId, getByLabelText, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: model }
    });

    await fireEvent.click(getByTestId("mock-select"));
    await fireEvent.update(getByLabelText("节点类型"), "database");

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].nodes[0]?.type).toBe("database");
    expect(updates.at(-1)?.[0].edges[0]).toMatchObject({
      sourceHandle: "target-3",
      targetHandle: "target-0"
    });
    expect(updates.at(-1)?.[0].edges[0]?.route).toBeUndefined();
  });

  it("改名和换形时保留节点比例与自定义颜色", async () => {
    const model = graph();
    model.nodes[0]!.scale = 1.5;
    model.nodes[0]!.style = { textColor: "#112233", fillColor: "#AABBCC", strokeColor: "#445566" };
    const EditorHost = defineComponent({
      components: { MermaidVisualEditor },
      setup() { return { model: ref(model) }; },
      template: `<MermaidVisualEditor v-model="model" /><pre data-testid="node-json">{{ JSON.stringify(model.nodes[0]) }}</pre>`
    });
    const rendered = render(EditorHost);
    await fireEvent.click(rendered.getByTestId("mock-select"));
    await fireEvent.dblClick(rendered.container.querySelector<HTMLElement>('[data-mermaid-node-id="A"]')!);
    await fireEvent.update(rendered.getByLabelText("节点文字"), "准备");
    await fireEvent.keyDown(rendered.getByLabelText("节点文字"), { key: "Enter", ctrlKey: true });
    await fireEvent.update(rendered.getByLabelText("节点类型"), "rounded");

    const node = JSON.parse(rendered.getByTestId("node-json").textContent ?? "{}");
    expect(node).toMatchObject({
      text: "准备",
      type: "rounded",
      scale: 1.5,
      style: { textColor: "#112233", fillColor: "#AABBCC", strokeColor: "#445566" }
    });
  });

  it("只修改连线文字时保留自动布局路由", async () => {
    const { getByTestId, getByLabelText, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: routedGraph() }
    });

    await fireEvent.click(getByTestId("mock-edge-click"));
    await fireEvent.update(getByLabelText("连线文字"), "保留轨道");

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].edges[0]?.route?.points).toEqual(routedGraph().edges[0]!.route!.points);
  });

  it("关闭 Vue Flow 原生连接并使用不拦截鼠标的临时 SVG", () => {
    expect(visualEditorSource).toContain(":nodes-connectable=\"false\"");
    expect(visualEditorSource).toContain(":connect-on-click=\"false\"");
    expect(visualEditorSource).toContain(":connection-mode=\"ConnectionMode.Loose\"");
    expect(visualEditorSource).not.toContain("@connect=\"onConnect\"");
    expect(visualEditorSource).toContain("ta-mermaid-connection-preview");
    expect(visualEditorSource).toContain("pointer-events: none");
  });

  it("可选择节点并修改名称、类型和删除关联边", async () => {
    const { getByTestId, getByLabelText, getByRole, container, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    await fireEvent.click(getByTestId("mock-select"));
    await fireEvent.dblClick(container.querySelector<HTMLElement>('[data-mermaid-node-id="A"]')!);
    await fireEvent.update(getByLabelText("节点文字"), "准备");
    await fireEvent.keyDown(getByLabelText("节点文字"), { key: "Enter", ctrlKey: true });
    await fireEvent.update(getByLabelText("节点类型"), "rounded");
    await fireEvent.click(getByRole("button", { name: "删除节点" }));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.some(([value]) => value.nodes[0]?.text === "准备")).toBe(true);
    expect(updates.some(([value]) => value.nodes[0]?.type === "rounded")).toBe(true);
    expect(updates.at(-1)?.[0].nodes.map((node) => node.id)).toEqual(["B"]);
    expect(updates.at(-1)?.[0].edges).toEqual([]);
  });

  it("支持通过连接点快捷创建并连接新节点", async () => {
    const { getByTestId, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    // 选中节点 A
    await fireEvent.click(getByTestId("mock-select"));

    // 触发快捷连接，朝右侧新建一个 diamond 节点
    await fireEvent.click(getByTestId("mock-quick-connect"));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    const lastUpdate = updates.at(-1)?.[0];
    expect(lastUpdate).toBeTruthy();

    // 校验新建的节点 N3 类型为 diamond，且坐标 x += 190
    expect(lastUpdate!.nodes.at(-1)).toMatchObject({
      id: "N3",
      text: "新节点",
      type: "diamond",
      position: { x: 270, y: 70 } // A: x: 80, y: 70 => 80+190=270, 70
    });

    // 校验新建的边连接：起始点固定为被选中节点 A（即使传入的 portId 为 target-* 也朝外），
    // 起点端口落在矩形右侧（target-5），目标端口落在新建 diamond 朝向起点的左侧（source-1）。
    expect(lastUpdate!.edges.at(-1)).toMatchObject({
      source: "A",
      target: "N3",
      sourceHandle: "target-5",
      targetHandle: "source-1"
    });
  });

  it("快捷建连的箭头方向始终从被选中节点指向新节点", async () => {
    // 即便快捷箭头选中的端口是 target-*，起始点也必须是被选中节点，箭头朝外指向新节点。
    const { getByTestId, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    await fireEvent.click(getByTestId("mock-select"));
    await fireEvent.click(getByTestId("mock-quick-connect"));

    const lastUpdate = (emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0];
    const edge = lastUpdate!.edges.at(-1);
    expect(edge).toMatchObject({ source: "A", target: "N3" });
    // 起点端口随被选中节点形状取右侧中点端口；目标端口随新节点形状取左侧端口。
    expect(edge!.sourceHandle).toBe("target-5");
    expect(edge!.targetHandle).toBe("source-1");
  });

  it("鼠标悬浮节点显示四向快捷箭头，离开后隐藏（与选中无关）", async () => {
    vi.useFakeTimers();
    try {
      const { container } = render(MermaidVisualEditor, {
        props: { modelValue: graph() }
      });
      const arrowsOf = (nodeId: string) =>
        container.querySelectorAll(`[data-mermaid-node-id="${nodeId}"] .ta-mermaid-quick-connector-wrapper`).length;
      const node = (id: string) => container.querySelector<HTMLElement>(`[data-mermaid-node-id="${id}"]`)!;

      // 未悬浮、也未选中：不显示箭头
      expect(arrowsOf("A")).toBe(0);

      // 悬浮 A：四周出现 4 个半透明快捷箭头（防误触，延迟 300ms 显示）
      await fireEvent.mouseEnter(node("A"));
      expect(arrowsOf("A")).toBe(0);

      vi.advanceTimersByTime(300);
      await Promise.resolve();
      expect(arrowsOf("A")).toBe(4);

      // 离开 A：箭头隐藏
      await fireEvent.mouseLeave(node("A"));
      expect(arrowsOf("A")).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it("点击空白画布取消选中节点", async () => {
    const { getByTestId, queryByText } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    await fireEvent.click(getByTestId("mock-select")); // 选中 A，属性面板显示 A
    expect(queryByText("选择画布中的节点或连线后编辑。")).toBeNull();

    await fireEvent.click(getByTestId("mock-pane-click")); // 点击空白画布取消选中
    expect(queryByText("选择画布中的节点或连线后编辑。")).toBeTruthy();
  });

  it("选中连线后可新增、修改并删除连线文字", async () => {
    const { getByTestId, getByLabelText, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    // 选中 edge-1（A->B）
    await fireEvent.click(getByTestId("mock-edge-click"));

    // 新增文字
    await fireEvent.update(getByLabelText("连线文字"), "是");
    expect((emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0].edges[0]?.label).toBe("是");

    // 修改文字
    await fireEvent.update(getByLabelText("连线文字"), "下一步");
    expect((emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0].edges[0]?.label).toBe("下一步");

    // 删除文字（清空）
    await fireEvent.update(getByLabelText("连线文字"), "");
    expect((emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0].edges[0]?.label).toBe("");
  });

  it("选中连线后可删除连线", async () => {
    const { getByTestId, getByRole, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    // 选中 edge-1（A->B）
    await fireEvent.click(getByTestId("mock-edge-click"));

    // 点击删除连线按钮
    await fireEvent.click(getByRole("button", { name: "删除连线" }));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].edges).toEqual([]);
  });

  it("点击空白画布同时取消选中的连线", async () => {
    const { getByTestId, queryByLabelText, queryByText } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    await fireEvent.click(getByTestId("mock-edge-click"));
    expect(queryByLabelText("连线文字")).toBeTruthy();

    await fireEvent.click(getByTestId("mock-pane-click"));
    expect(queryByLabelText("连线文字")).toBeNull();
    expect(queryByText("选择画布中的节点或连线后编辑。")).toBeTruthy();
  });

  it("选中连线后显示可重锚端点并提升图层，取消选中后恢复", async () => {
    const { getByTestId, container } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });
    const flowEdge = getByTestId("mock-flow-edge-edge-1");
    expect(flowEdge.dataset.edgeSelected).toBe("false");
    expect(flowEdge.dataset.edgeZIndex).toBe("0");
    expect(container.querySelectorAll(".ta-mermaid-edge-handle")).toHaveLength(0);

    await fireEvent.click(getByTestId("mock-edge-click"));
    expect(flowEdge.dataset.edgeSelected).toBe("true");
    expect(flowEdge.dataset.edgeZIndex).toBe("1001");
    expect(container.querySelectorAll(".ta-mermaid-edge-handle")).toHaveLength(2);

    await fireEvent.click(getByTestId("mock-pane-click"));
    expect(flowEdge.dataset.edgeSelected).toBe("false");
    expect(flowEdge.dataset.edgeZIndex).toBe("0");
    expect(container.querySelectorAll(".ta-mermaid-edge-handle")).toHaveLength(0);
  });

  it("修改连线文字后保持连线选中与可重锚状态", async () => {
    const EditorHost = defineComponent({
      components: { MermaidVisualEditor },
      setup() {
        return { model: ref(graph()) };
      },
      template: `<MermaidVisualEditor v-model="model" />`
    });
    const { getByTestId, getByLabelText, container } = render(EditorHost);

    await fireEvent.click(getByTestId("mock-edge-click"));
    await fireEvent.update(getByLabelText("连线文字"), "更新后的连线");

    expect(getByTestId("mock-flow-edge-edge-1").dataset.edgeSelected).toBe("true");
    expect(container.querySelectorAll(".ta-mermaid-edge-handle")).toHaveLength(2);
  });

  it("快捷图形从被悬浮节点（而非被选中节点）发出连线", async () => {
    vi.useFakeTimers();
    try {
      const { getByTestId, container, emitted } = render(MermaidVisualEditor, {
        props: { modelValue: graph() }
      });

      // 选中 A，但悬浮 B
      await fireEvent.click(getByTestId("mock-select")); // selectedNodeId = A

      // 悬浮 B → B 的快捷箭头显示
      await fireEvent.mouseEnter(container.querySelector<HTMLElement>('[data-mermaid-node-id="B"]')!);
      vi.advanceTimersByTime(300);
      await Promise.resolve();

      await fireEvent.mouseEnter(container.querySelector<HTMLElement>(
        '[data-mermaid-node-id="B"] .ta-mermaid-quick-arrow'
      )!);
      vi.advanceTimersByTime(200);
      await Promise.resolve();

      // 点击 B 的第一个快捷形状按钮（开始/结束）
      const bBtn = document.body.querySelector<HTMLElement>(".ta-mermaid-quick-menu button")!;
      await fireEvent.click(bBtn);

      // 新节点 N3 应该连接到 B，而不是 A
      const lastEdge = (emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0].edges.at(-1);
      expect(lastEdge).toMatchObject({ source: "B", target: "N3" });
    } finally {
      vi.useRealTimers();
    }
  });
});
