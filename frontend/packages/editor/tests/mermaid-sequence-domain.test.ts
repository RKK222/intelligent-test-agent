import { describe, expect, it } from "vitest";
import { parseMermaidDiagram, serializeMermaidDiagram } from "../src/mermaid/diagram";
import { autoLayoutMermaidSequence } from "../src/mermaid/sequence/layout";
import { parseMermaidSequence } from "../src/mermaid/sequence/parser";
import { serializeMermaidSequence } from "../src/mermaid/sequence/serializer";

describe("Mermaid sequenceDiagram 领域层", () => {
  it("解析 participant/actor、有序消息、坐标 metadata 并保留复杂语句", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
%% editor-layout:
%% {
%%   "U": { "x": 80, "y": 70 },
%%   "S": { "x": 320, "y": 70 }
%% }
actor U as 用户
participant S as 服务
U->>S: 请求
S-->>U: 响应
Note over U,S: 保留说明`);

    expect(diagram.kind).toBe("sequenceDiagram");
    expect(diagram.participants).toEqual([
      { id: "U", text: "用户", type: "actor", position: { x: 80, y: 70 } },
      { id: "S", text: "服务", type: "participant", position: { x: 320, y: 70 } }
    ]);
    expect(diagram.messages).toEqual([
      { id: "message-1", source: "U", target: "S", text: "请求", type: "solid" },
      { id: "message-2", source: "S", target: "U", text: "响应", type: "dotted" }
    ]);
    expect(diagram.preservedLines).toEqual(["Note over U,S: 保留说明"]);
  });

  it("从消息补齐隐式参与者并支持四种常见箭头", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
A->>B: 同步
B-->>A: 虚线响应
A->B: 开放箭头
B-->A: 虚线开放箭头`);

    expect(diagram.participants.map((participant) => participant.id)).toEqual(["A", "B"]);
    expect(diagram.messages.map((message) => message.type)).toEqual([
      "solid",
      "dotted",
      "open",
      "dotted-open"
    ]);
  });

  it("序列化结果通过 Mermaid 官方 parser 且保持消息顺序和未知语句", async () => {
    const source = `sequenceDiagram
participant A as 浏览器
participant B as 服务端
A->>B: 创建
B-->>A: 完成
activate B`;
    const serialized = serializeMermaidSequence(parseMermaidSequence(source));
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(serialized.indexOf("A->>B: 创建")).toBeLessThan(serialized.indexOf("B-->>A: 完成"));
    expect(serialized).toContain("activate B");
    expect(serializeMermaidSequence(parseMermaidSequence(serialized))).toBe(serialized);
  });

  it("自动布局参与者并保持输入模型不变", () => {
    const diagram = parseMermaidSequence("sequenceDiagram\nparticipant A\nparticipant B\nparticipant C");
    const laidOut = autoLayoutMermaidSequence(diagram);

    expect(laidOut).not.toBe(diagram);
    expect(laidOut.participants.map((participant) => participant.position)).toEqual([
      { x: 80, y: 70 },
      { x: 300, y: 70 },
      { x: 520, y: 70 }
    ]);
    expect(diagram.participants.every((participant) => participant.position.x === 0)).toBe(true);
  });

  it("通用入口按图头分发 flowchart 与 sequenceDiagram", () => {
    expect(parseMermaidDiagram("flowchart TD\nA --> B").kind).toBe("flowchart");
    expect(parseMermaidDiagram("sequenceDiagram\nA->>B: hi").kind).toBe("sequenceDiagram");
    expect(serializeMermaidDiagram(parseMermaidDiagram("sequenceDiagram\nA->>B: hi"))).toContain(
      "A->>B: hi"
    );
    expect(() => parseMermaidDiagram("classDiagram\nA <|-- B")).toThrow(
      "仅支持 flowchart、graph 或 sequenceDiagram"
    );
  });

  it("loop/alt 等复杂控制块整体保留且内部消息不参与可视化", () => {
    const serialized = serializeMermaidSequence(parseMermaidSequence(`sequenceDiagram
participant A
participant B
loop 重试
  A->>B: 重试请求
end
A->>B: 最终请求`));

    expect(serialized).toContain("loop 重试\n  A->>B: 重试请求\nend");
    expect(serialized.indexOf("loop 重试")).toBeLessThan(serialized.indexOf("A->>B: 最终请求"));
    const roundTrip = parseMermaidSequence(serialized);
    expect(roundTrip.messages).toHaveLength(1);
    expect(roundTrip.messages[0]?.text).toBe("最终请求");
  });
});
