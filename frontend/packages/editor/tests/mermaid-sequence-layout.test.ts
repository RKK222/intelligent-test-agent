import { describe, expect, it } from "vitest";
import { layoutMermaidSequence } from "../src/mermaid/sequence/layout";
import { parseMermaidSequence } from "../src/mermaid/sequence/parser";

describe("Mermaid Sequence 专用布局", () => {
  it("计算生命线、消息、自调用和堆叠激活条", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
A->>+B: call
B->>+B: nested self call
B-->>-A: response`);
    const layout = layoutMermaidSequence(diagram);

    expect(layout.participants.map((participant) => participant.id)).toEqual(["A", "B"]);
    expect(layout.lifelines).toHaveLength(2);
    expect(layout.messages).toHaveLength(3);
    expect(layout.messages[1]?.selfCall).toBe(true);
    expect(layout.messages[1]?.points.length).toBeGreaterThanOrEqual(4);
    expect(layout.activations).toEqual(expect.arrayContaining([
      expect.objectContaining({ participantId: "B", depth: 0 })
    ]));
    const nestedActivation = layout.activations.find((activation) => activation.participantId === "B" && activation.depth === 1);
    expect((nestedActivation?.y ?? 0) + (nestedActivation?.height ?? 0)).toBe(layout.messages[2]?.y);
    expect(layout.width).toBeGreaterThan(400);
    expect(layout.height).toBeGreaterThan(250);
  });

  it("递归片段框包含其分支内容且同一输入布局确定", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
alt ok
  loop retry
    A->>B: request
  end
else fail
  par log
    Note right of B: record
  and alert
    B--)A: notify
  end
end`);
    const first = layoutMermaidSequence(diagram);
    const second = layoutMermaidSequence(diagram);

    expect(second).toEqual(first);
    expect(first.blocks.map((block) => block.blockType)).toEqual(["alt", "loop", "par"]);
    const alt = first.blocks.find((block) => block.blockType === "alt");
    const loop = first.blocks.find((block) => block.blockType === "loop");
    expect(alt && loop && alt.y < loop.y && alt.y + alt.height > loop.y + loop.height).toBe(true);
    expect(alt?.branches).toHaveLength(2);
  });

  it("create/destroy 截断对应生命线，长文本和 Note 扩展场景", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A as 入口
create participant B as 后台工作器
A->>B: 这是一个需要扩展参与者间距的很长调用消息
Note over A,B: 第一行<br/>第二行<br/>第三行
destroy B
B-xA: stop`);
    const layout = layoutMermaidSequence(diagram);
    const actor = layout.lifelines.find((lifeline) => lifeline.participantId === "A");
    const worker = layout.lifelines.find((lifeline) => lifeline.participantId === "B");
    const actorHeader = layout.participants.find((participant) => participant.id === "A");
    const workerHeader = layout.participants.find((participant) => participant.id === "B");

    expect(worker?.startY).toBeGreaterThan(actor?.startY ?? 0);
    expect(workerHeader?.y).toBeGreaterThan(actorHeader?.y ?? 0);
    expect(worker?.endY).toBeLessThan(actor?.endY ?? Number.MAX_SAFE_INTEGER);
    expect(layout.notes[0]?.height).toBeGreaterThan(50);
    expect(layout.width).toBeGreaterThan(500);
    expect(layout.destroys).toEqual([expect.objectContaining({ participantId: "B" })]);
  });

  it("格式化空行不占用时序场景行", () => {
    const compact = layoutMermaidSequence(parseMermaidSequence(`sequenceDiagram
participant A
participant B
A->>B: call`));
    const formatted = layoutMermaidSequence(parseMermaidSequence(`sequenceDiagram

participant A

participant B

A->>B: call
`));

    expect(formatted.rows).toHaveLength(compact.rows.length);
    expect(formatted.rows.some((row) => row.kind === "locked")).toBe(false);
    expect(formatted.height).toBe(compact.height);
  });

  it("多行消息不与下一消息重叠，深层片段宽度始终为正", () => {
    const multiline = layoutMermaidSequence(parseMermaidSequence(`sequenceDiagram
participant A
participant B
A->>B: 一<br/>二<br/>三<br/>四<br/>五<br/>六
B-->>A: next`));
    expect((multiline.messages[1]?.y ?? 0) - (multiline.messages[0]?.y ?? 0)).toBeGreaterThanOrEqual(88);

    const nestedSource = [
      "sequenceDiagram",
      "participant A",
      "participant B",
      ...Array.from({ length: 16 }, (_, index) => `${"  ".repeat(index)}loop L${index}`),
      `${"  ".repeat(16)}A->>B: inner`,
      ...Array.from({ length: 16 }, (_, index) => `${"  ".repeat(15 - index)}end`)
    ].join("\n");
    const nested = layoutMermaidSequence(parseMermaidSequence(nestedSource));
    expect(nested.blocks).toHaveLength(16);
    expect(nested.blocks.every((block) => block.width >= 120)).toBe(true);
  });

  it("autonumber 起始值/步长和 rect 颜色进入场景几何", () => {
    const layout = layoutMermaidSequence(parseMermaidSequence(`sequenceDiagram
autonumber 5 2
participant A
participant B
rect rgba(40, 80, 120, 0.12)
  A->>B: first
  B-->>A: second
end`));

    expect(layout.messages.map((message) => message.sequenceNumber)).toEqual([5, 7]);
    expect(layout.blocks[0]).toMatchObject({ blockType: "rect", label: "", color: "rgba(40, 80, 120, 0.12)" });
  });
});
