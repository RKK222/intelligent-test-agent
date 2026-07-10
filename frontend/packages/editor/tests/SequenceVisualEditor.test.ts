import { defineComponent } from "vue";
import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type { MermaidSequenceDiagram } from "../src/mermaid/sequence/model";
import {
  appendSequenceMessage,
  applySequenceFlowPositions,
  toSequenceFlowEdges,
  toSequenceFlowNodes
} from "../src/mermaid/sequence/visual-editor/vue-flow-adapter";

vi.mock("@vue-flow/core", () => ({
  VueFlow: defineComponent({
    props: ["nodes", "edges"],
    emits: ["nodeDragStop", "connect", "nodeClick"],
    template: `<div data-testid="sequence-flow-mock">
      <button data-testid="sequence-drag" @click="$emit('nodeDragStop', { node: { id: 'U', position: { x: 420, y: 90 } } })">drag</button>
      <button data-testid="sequence-connect" @click="$emit('connect', { source: 'S', target: 'U' })">connect</button>
      <button data-testid="sequence-select" @click="$emit('nodeClick', { node: { id: 'U' } })">select</button>
    </div>`
  }),
  Handle: defineComponent({ template: "<span />" }),
  BaseEdge: defineComponent({ template: "<path />" }),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  MarkerType: { ArrowClosed: "arrowclosed", Arrow: "arrow" }
}));

import SequenceVisualEditor from "../src/mermaid/sequence/visual-editor/SequenceVisualEditor.vue";

const sequence = (): MermaidSequenceDiagram => ({
  kind: "sequenceDiagram",
  participants: [
    { id: "U", text: "用户", type: "actor", position: { x: 80, y: 70 } },
    { id: "S", text: "服务", type: "participant", position: { x: 300, y: 70 } }
  ],
  messages: [
    { id: "message-1", source: "U", target: "S", text: "请求", type: "solid" },
    { id: "message-2", source: "S", target: "U", text: "响应", type: "dotted" }
  ],
  preservedLines: ["Note over U,S: 说明"]
});

describe("Sequence Vue Flow 适配", () => {
  it("参与者映射为 lifeline 节点，消息映射为带顺序的自定义边", () => {
    expect(toSequenceFlowNodes(sequence())).toMatchObject([
      { id: "U", type: "sequence-participant", data: { text: "用户", participantType: "actor" } },
      { id: "S", type: "sequence-participant", data: { text: "服务", participantType: "participant" } }
    ]);
    expect(toSequenceFlowEdges(sequence())).toMatchObject([
      { id: "message-1", type: "sequence-message", source: "U", target: "S", data: { order: 0, text: "请求" } },
      { id: "message-2", type: "sequence-message", source: "S", target: "U", data: { order: 1, text: "响应" } }
    ]);
  });

  it("拖拽参与者只更新领域模型副本", () => {
    const original = sequence();
    const updated = applySequenceFlowPositions(original, [{ id: "U", position: { x: 420, y: 90 } }]);

    expect(updated.participants[0]?.position).toEqual({ x: 420, y: 90 });
    expect(original.participants[0]?.position).toEqual({ x: 80, y: 70 });
  });

  it("Handle 连接在消息末尾追加且保持重复连接可表达", () => {
    const updated = appendSequenceMessage(sequence(), { source: "S", target: "U" });

    expect(updated.messages).toHaveLength(3);
    expect(updated.messages.at(-1)).toEqual({
      id: "message-3",
      source: "S",
      target: "U",
      text: "新消息",
      type: "solid"
    });
  });
});

describe("SequenceVisualEditor", () => {
  it("同步参与者拖拽坐标与 Handle 新增消息", async () => {
    const { getByTestId, emitted } = render(SequenceVisualEditor, { props: { modelValue: sequence() } });

    await fireEvent.click(getByTestId("sequence-drag"));
    await fireEvent.click(getByTestId("sequence-connect"));

    const updates = emitted()["update:modelValue"] as Array<[MermaidSequenceDiagram]>;
    expect(updates.some(([value]) => value.participants[0]?.position.x === 420)).toBe(true);
    expect(updates.some(([value]) => value.messages.length === 3)).toBe(true);
  });

  it("可修改参与者名称和类型，删除参与者时删除关联消息", async () => {
    const { getByTestId, getByLabelText, getByRole, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });

    await fireEvent.click(getByTestId("sequence-select"));
    await fireEvent.update(getByLabelText("参与者名称"), "访客");
    await fireEvent.update(getByLabelText("参与者类型"), "participant");
    await fireEvent.click(getByRole("button", { name: "删除参与者" }));

    const updates = emitted()["update:modelValue"] as Array<[MermaidSequenceDiagram]>;
    expect(updates.some(([value]) => value.participants[0]?.text === "访客")).toBe(true);
    expect(updates.some(([value]) => value.participants[0]?.type === "participant")).toBe(true);
    expect(updates.at(-1)?.[0].participants.map((participant) => participant.id)).toEqual(["S"]);
    expect(updates.at(-1)?.[0].messages).toEqual([]);
  });

  it("可修改消息标签、交换方向并调整消息顺序", async () => {
    const { getByLabelText, getAllByRole, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });

    await fireEvent.update(getByLabelText("消息 1 标签"), "登录");
    await fireEvent.click(getAllByRole("button", { name: "交换消息方向" })[0] as Element);
    await fireEvent.click(getAllByRole("button", { name: "下移消息" })[0] as Element);

    const updates = emitted()["update:modelValue"] as Array<[MermaidSequenceDiagram]>;
    expect(updates.some(([value]) => value.messages[0]?.text === "登录")).toBe(true);
    expect(updates.some(([value]) => value.messages[0]?.source === "S" && value.messages[0]?.target === "U")).toBe(true);
    expect(updates.at(-1)?.[0].messages.map((message) => message.id)).toEqual(["message-2", "message-1"]);
  });
});
