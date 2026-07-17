import { describe, expect, it } from "vitest";
import type { MermaidGraph, MermaidPosition } from "../src/mermaid/model";
import { extractMermaidCompactMarker } from "../src/mermaid/compact-metadata";
import { parseMermaidFlowchart } from "../src/mermaid/parser";
import { serializeMermaidGraph } from "../src/mermaid/serializer";
import { parseMermaidSequence } from "../src/mermaid/sequence/parser";
import { serializeMermaidSequence } from "../src/mermaid/sequence/serializer";

const COMPACT_MARKER_PATTERN = /^%%@[A-Za-z0-9_-]+$/;
const BASE64URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

function compactMarkers(source: string): string[] {
  return source.split("\n").filter((line) => /^%%@(?!\+)/.test(line));
}

function compactMarkerLines(source: string): string[] {
  return source.split("\n").filter((line) => line.startsWith("%%@"));
}

function compactMarkerPayload(source: string): string {
  return compactMarkerLines(source)
    .map((line) => line.startsWith("%%@+") ? line.slice(4) : line.slice(3))
    .join("");
}

function replaceCompactMarkerLines(source: string, replacement: string[]): string {
  const lines = source.split("\n");
  const first = lines.findIndex((line) => line.startsWith("%%@"));
  if (first < 0) return source;
  let end = first + 1;
  while (lines[end]?.startsWith("%%@+")) end += 1;
  return [...lines.slice(0, first), ...replacement, ...lines.slice(end)].join("\n");
}

function encodeBase64Url(bytes: readonly number[]): string {
  let result = "";
  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index]!;
    const second = bytes[index + 1];
    const third = bytes[index + 2];
    result += BASE64URL_ALPHABET[first >>> 2];
    result += BASE64URL_ALPHABET[((first & 3) << 4) | ((second ?? 0) >>> 4)];
    if (second !== undefined) result += BASE64URL_ALPHABET[((second & 15) << 2) | ((third ?? 0) >>> 6)];
    if (third !== undefined) result += BASE64URL_ALPHABET[third & 63];
  }
  return result;
}

function decodeBase64Url(value: string): number[] {
  const result: number[] = [];
  let buffer = 0;
  let bitCount = 0;
  for (const character of value) {
    buffer = (buffer << 6) | BASE64URL_ALPHABET.indexOf(character);
    bitCount += 6;
    if (bitCount >= 8) {
      bitCount -= 8;
      result.push((buffer >>> bitCount) & 0xff);
      buffer &= (1 << bitCount) - 1;
    }
  }
  return result;
}

function fnv1a(signature: string, body: readonly number[]): number {
  let hash = 0x811c9dc5;
  for (const byte of new TextEncoder().encode(signature)) {
    hash = Math.imul((hash ^ byte) >>> 0, 0x01000193) >>> 0;
  }
  hash = Math.imul(hash, 0x01000193) >>> 0;
  for (const byte of body) hash = Math.imul((hash ^ byte) >>> 0, 0x01000193) >>> 0;
  return hash >>> 0;
}

function markerForBody(body: readonly number[], signature: string): string {
  const hash = fnv1a(signature, body);
  return `%%@${encodeBase64Url([
    ...body,
    hash & 0xff,
    (hash >>> 8) & 0xff,
    (hash >>> 16) & 0xff,
    (hash >>> 24) & 0xff
  ])}`;
}

function unsignedLeb128(value: number): number[] {
  const result: number[] = [];
  let remaining = value;
  do {
    const payload = remaining % 128;
    remaining = Math.floor(remaining / 128);
    result.push(payload | (remaining > 0 ? 128 : 0));
  } while (remaining > 0);
  return result;
}

function flowSignature(graph: MermaidGraph): string {
  return JSON.stringify([
    "flow",
    graph.kind,
    graph.direction,
    graph.nodes.map((node) => [node.id, node.type, node.text]),
    graph.edges.map((edge) => [edge.source, edge.target])
  ]);
}

function flowSource(marker: string): string {
  return `flowchart LR
${marker}
A[开始]
B{检查}
A --> B`;
}

function graphWithCompactMetadata(): MermaidGraph {
  const graph = parseMermaidFlowchart("flowchart LR\nA[开始] --> B{检查}");
  graph.nodes[0]!.position = { x: 10.04, y: 20.16 };
  graph.nodes[1]!.position = { x: 100.44, y: 120.66 };
  graph.edges[0]!.sourceHandle = "source-5";
  graph.edges[0]!.targetHandle = "target-0";
  graph.edges[0]!.route = {
    points: [
      { x: 10.04, y: 20.16 },
      { x: 10.04, y: 75.55 },
      { x: 100.44, y: 75.55 }
    ]
  };
  return graph;
}

function graphWithLargeCompactMetadata(): MermaidGraph {
  const nodeIds = Array.from({ length: 30 }, (_, index) => `N${index + 1}`);
  const graph = parseMermaidFlowchart([
    "flowchart LR",
    ...nodeIds.slice(1).map((target, index) => `${nodeIds[index]} --> ${target}`)
  ].join("\n"));
  graph.nodes.forEach((node, index) => {
    node.position = { x: 80 + index * 120, y: 70 + index * 40 };
  });
  graph.edges.forEach((edge, index) => {
    const source = graph.nodes[index]!.position;
    const target = graph.nodes[index + 1]!.position;
    const middleY = source.y + (target.y - source.y) / 2;
    edge.sourceHandle = `source-${index % 6}`;
    edge.targetHandle = `target-${(index + 2) % 6}`;
    edge.route = {
      points: [
        { ...source },
        { x: source.x, y: middleY },
        { x: target.x, y: middleY },
        { ...target }
      ]
    };
  });
  return graph;
}

function sequenceWithLargeCompactMetadata(participantCount = 80) {
  const diagram = parseMermaidSequence([
    "sequenceDiagram",
    ...Array.from({ length: participantCount }, (_, index) => `participant P${index + 1}`)
  ].join("\n"));
  diagram.participants.forEach((participant, index) => {
    participant.position = { x: 80 + index * 100, y: 70 + (index % 3) * 30 };
  });
  return diagram;
}

describe("Mermaid 紧凑 metadata", () => {
  it("Flow 与 Sequence golden vector 固定协议版本、字节顺序和确定性输出", () => {
    const flowMarker = compactMarkers(serializeMermaidGraph(graphWithCompactMetadata()))[0];
    const sequence = parseMermaidSequence(`sequenceDiagram
actor U as 用户
participant S as 服务
U->>S: 请求`);
    sequence.participants[0]!.position = { x: 80.04, y: 70.05 };
    sequence.participants[1]!.position = { x: 300.06, y: 70.04 };

    expect(flowMarker).toBe("%%@oQcCAcgBlAOQDtoPdgMAAKkRoBzMtUdz");
    expect(compactMarkers(serializeMermaidSequence(sequence))[0]).toBe("%%@oQkCAMAM-gqyIgEq7THF");
    expect(compactMarkers(serializeMermaidGraph(graphWithCompactMetadata()))[0]).toBe(flowMarker);
  });

  it("Flow 坐标、端口和正交路由通过一个 Base64URL 注释按 0.1px 精度往返", () => {
    const serialized = serializeMermaidGraph(graphWithCompactMetadata());
    const markers = compactMarkers(serialized);

    expect(markers).toHaveLength(1);
    expect(markers[0]).toMatch(COMPACT_MARKER_PATTERN);
    expect(serialized).not.toContain("editor-layout");
    expect(serialized).not.toContain("editor-edge-ports");
    expect(serialized).not.toContain("editor-edge-routes");

    const reparsed = parseMermaidFlowchart(serialized);
    expect(reparsed.nodes.map((node) => node.position)).toEqual([
      { x: 10, y: 20.2 },
      { x: 100.4, y: 120.7 }
    ]);
    expect(reparsed.edges[0]).toMatchObject({
      sourceHandle: "source-5",
      targetHandle: "target-0",
      route: {
        points: [
          { x: 10, y: 20.2 },
          { x: 10, y: 75.6 },
          { x: 100.4, y: 75.6 }
        ]
      }
    });
  });

  it("较大 Flow metadata 按 240 字符 payload 分行并完整往返", () => {
    const graph = graphWithLargeCompactMetadata();
    const serialized = serializeMermaidGraph(graph);
    const markerLines = serialized.split("\n").filter((line) => line.startsWith("%%@"));

    expect(markerLines.length).toBeGreaterThan(1);
    expect(markerLines[0]).toMatch(/^%%@[A-Za-z0-9_-]{1,240}$/);
    expect(markerLines.slice(1).every((line) => /^%%@\+[A-Za-z0-9_-]{1,240}$/.test(line))).toBe(true);
    expect(markerLines.every((line) => line.length <= 244)).toBe(true);
    expect(markerLines.slice(0, -1).every((line) => line.slice(line.startsWith("%%@+") ? 4 : 3).length === 240))
      .toBe(true);
    expect(markerLines.reduce((length, line) => length + line.length, 0))
      .toBe(compactMarkerPayload(serialized).length + 3 + (markerLines.length - 1) * 4);

    const reparsed = parseMermaidFlowchart(serialized);
    expect(reparsed.nodes.map((node) => node.position)).toEqual(graph.nodes.map((node) => node.position));
    expect(reparsed.edges.map((edge) => edge.route)).toEqual(graph.edges.map((edge) => edge.route));
    expect(serializeMermaidGraph(reparsed)).toBe(serialized);
  });

  it("拒绝超过 240 字符的多行分段，但继续读取历史长单行", () => {
    const serialized = serializeMermaidGraph(graphWithLargeCompactMetadata());
    const payload = compactMarkerPayload(serialized);
    const withLegacyLayout = (markerLines: string[]) => replaceCompactMarkerLines(serialized, [
      ...markerLines,
      "%% editor-layout:",
      '%% {"N1":{"x":5,"y":6}}'
    ]);
    const invalidWraps = [
      [`%%@${payload.slice(0, 241)}`, `%%@+${payload.slice(241)}`],
      [`%%@${payload.slice(0, 240)}`, `%%@+${payload.slice(240, 481)}`, `%%@+${payload.slice(481)}`]
    ];

    for (const markerLines of invalidWraps) {
      const parsed = parseMermaidFlowchart(withLegacyLayout(markerLines));
      expect(parsed.nodes[0]!.position).toEqual({ x: 5, y: 6 });
      expect(parsed.preservedLines).toEqual(expect.arrayContaining(markerLines));
    }

    const historicalSingleLine = replaceCompactMarkerLines(serialized, [`%%@${payload}`]);
    expect(parseMermaidFlowchart(historicalSingleLine).nodes[0]!.position).toEqual({ x: 80, y: 70 });
  });

  it("拼接前拒绝累计超限或分段数异常的多行 marker", () => {
    const maxEncodedChars = Math.ceil(1024 * 1024 * 4 / 3);
    const maxMarkerLines = Math.ceil(maxEncodedChars / 240);
    const overLimitChunks: string[] = [];
    for (let remaining = maxEncodedChars + 1; remaining > 0; remaining -= 240) {
      overLimitChunks.push("A".repeat(Math.min(240, remaining)));
    }
    const overLimitLines = [
      `%%@${overLimitChunks[0]}`,
      ...overLimitChunks.slice(1).map((chunk) => `%%@+${chunk}`)
    ];
    const tooManyLines = ["%%@A", ...Array.from({ length: maxMarkerLines }, () => "%%@+A")];

    expect(extractMermaidCompactMarker(overLimitLines).encoded).toBeNull();
    expect(extractMermaidCompactMarker(tooManyLines).encoded).toBeNull();
  });

  it("接受短续段输入，并在下一次保存时恢复规范分段", () => {
    const graph = graphWithLargeCompactMetadata();
    const canonical = serializeMermaidGraph(graph);
    const payload = compactMarkerPayload(canonical);
    const chunks = [payload.slice(0, 240), payload.slice(240, 360)];
    for (let offset = 360; offset < payload.length; offset += 200) {
      chunks.push(payload.slice(offset, offset + 200));
    }
    const irregular = replaceCompactMarkerLines(canonical, [
      `%%@${chunks[0]}`,
      ...chunks.slice(1).map((chunk) => `%%@+${chunk}`)
    ]);

    const parsed = parseMermaidFlowchart(irregular);
    expect(parsed.nodes.map((node) => node.position)).toEqual(graph.nodes.map((node) => node.position));
    expect(compactMarkerLines(serializeMermaidGraph(parsed))).toEqual(compactMarkerLines(canonical));
  });

  it("损坏、缺失、重复、乱序或冲突的分段全部保留并回退旧布局", () => {
    const serialized = serializeMermaidGraph(graphWithLargeCompactMetadata());
    const canonicalLines = compactMarkerLines(serialized);
    const damagedHashLines = canonicalLines.map((line, index) => index === canonicalLines.length - 1
      ? line.replace(/.$/, line.endsWith("A") ? "B" : "A")
      : line);
    const invalidVariants = [
      [canonicalLines[0]!, canonicalLines[0]!, ...canonicalLines.slice(1)],
      [...canonicalLines, ...canonicalLines],
      canonicalLines.slice(1),
      [canonicalLines[0]!, ...canonicalLines.slice(2)],
      [canonicalLines[0]!, canonicalLines[1]!, canonicalLines[1]!, ...canonicalLines.slice(2)],
      [canonicalLines[0]!, canonicalLines[2]!, canonicalLines[1]!, ...canonicalLines.slice(3)],
      [canonicalLines[0]!, "%%@+", ...canonicalLines.slice(1)],
      [canonicalLines[0]!, canonicalLines[1]!.replace(/.$/, "*"), ...canonicalLines.slice(2)],
      damagedHashLines,
      [...canonicalLines, "%% marker separator", "%%@+A"]
    ];

    for (const markerLines of invalidVariants) {
      const source = replaceCompactMarkerLines(serialized, [
        ...markerLines,
        "%% editor-layout:",
        '%% {"N1":{"x":5,"y":6}}'
      ]);
      const parsed = parseMermaidFlowchart(source);
      expect(parsed.nodes[0]!.position).toEqual({ x: 5, y: 6 });
      expect(parsed.preservedLines).toEqual(expect.arrayContaining(markerLines));

      const reparsed = parseMermaidFlowchart(serializeMermaidGraph(parsed));
      expect(reparsed.nodes[0]!.position).toEqual({ x: 5, y: 6 });
      expect(reparsed.preservedLines).toEqual(expect.arrayContaining(markerLines));
    }
  });

  it("多行 marker 在拓扑变化后完整保留并回退旧布局", () => {
    const serialized = serializeMermaidGraph(graphWithLargeCompactMetadata());
    const markerLines = compactMarkerLines(serialized);
    const withLegacy = replaceCompactMarkerLines(serialized, [
      ...markerLines,
      "%% editor-layout:",
      '%% {"N1":{"x":5,"y":6}}'
    ]).replace(
      'N30@{ shape: rect, label: "N30" }',
      'N30@{ shape: rect, label: "复核" }'
    );

    const parsed = parseMermaidFlowchart(withLegacy);
    expect(parsed.nodes[0]!.position).toEqual({ x: 5, y: 6 });
    expect(parsed.preservedLines).toEqual(expect.arrayContaining(markerLines));
  });

  it.each([
    ["空行", ""],
    ["节点声明", 'N1["N1"]']
  ])("被%s打断的续行在再次保存后仍保持失败安全", (_name, separator) => {
    const serialized = serializeMermaidGraph(graphWithLargeCompactMetadata());
    const markerLines = compactMarkerLines(serialized);
    const interrupted = replaceCompactMarkerLines(serialized, [
      markerLines[0]!,
      separator,
      ...markerLines.slice(1),
      "%% editor-layout:",
      '%% {"N1":{"x":5,"y":6}}'
    ]);

    const parsed = parseMermaidFlowchart(interrupted);
    expect(parsed.nodes[0]!.position).toEqual({ x: 5, y: 6 });

    const saved = serializeMermaidGraph(parsed);
    const reparsed = parseMermaidFlowchart(saved);
    expect(reparsed.nodes[0]!.position).toEqual({ x: 5, y: 6 });
    expect(reparsed.preservedLines).toEqual(expect.arrayContaining(markerLines));
  });

  it.each([
    ["空行", ""],
    ["参与者声明", "participant P1"]
  ])("Sequence 被%s打断的续行在保存后也不会意外生效", (_name, separator) => {
    const serialized = serializeMermaidSequence(sequenceWithLargeCompactMetadata());
    const markerLines = compactMarkerLines(serialized);
    const interrupted = replaceCompactMarkerLines(serialized, [
      markerLines[0]!,
      separator,
      ...markerLines.slice(1),
      "%% editor-layout:",
      '%% {"P1":{"x":5,"y":6}}'
    ]);

    const parsed = parseMermaidSequence(interrupted);
    expect(parsed.participants[0]!.position).toEqual({ x: 5, y: 6 });

    const reparsed = parseMermaidSequence(serializeMermaidSequence(parsed));
    expect(reparsed.participants[0]!.position).toEqual({ x: 5, y: 6 });
    expect(reparsed.preservedLines).toEqual(expect.arrayContaining(markerLines));
  });

  it("Sequence 参与者坐标通过同一单行格式往返", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
actor U as 用户
participant S as 服务
U->>S: 请求`);
    diagram.participants[0]!.position = { x: 80.04, y: 70.05 };
    diagram.participants[1]!.position = { x: 300.06, y: 70.04 };

    const serialized = serializeMermaidSequence(diagram);
    const markers = compactMarkers(serialized);

    expect(markers).toHaveLength(1);
    expect(markers[0]).toMatch(COMPACT_MARKER_PATTERN);
    expect(serialized).not.toContain("editor-layout");
    expect(parseMermaidSequence(serialized).participants.map((participant) => participant.position)).toEqual([
      { x: 80, y: 70.1 },
      { x: 300.1, y: 70 }
    ]);
  });

  it("较大 Sequence metadata 复用同一多行格式并完整往返", () => {
    const diagram = sequenceWithLargeCompactMetadata(180);
    const serialized = serializeMermaidSequence(diagram);
    const markerLines = serialized.split("\n").filter((line) => line.startsWith("%%@"));

    expect(compactMarkerPayload(serialized)).toHaveLength(972);
    expect(markerLines).toHaveLength(5);
    expect(markerLines[0]).toMatch(/^%%@[A-Za-z0-9_-]{240}$/);
    expect(markerLines.slice(1).every((line) => /^%%@\+[A-Za-z0-9_-]{1,240}$/.test(line))).toBe(true);
    expect(parseMermaidSequence(serialized).participants.map((participant) => participant.position))
      .toEqual(diagram.participants.map((participant) => participant.position));
    expect(serializeMermaidSequence(parseMermaidSequence(serialized))).toBe(serialized);
  });

  it("240 个 payload 字符保持单行，第 241 个字符起确定性换行", () => {
    const atBoundary = serializeMermaidSequence(sequenceWithLargeCompactMetadata(43));
    const overBoundary = serializeMermaidSequence(sequenceWithLargeCompactMetadata(44));

    expect(compactMarkerPayload(atBoundary)).toHaveLength(240);
    expect(compactMarkerLines(atBoundary).map((line) => line.length)).toEqual([243]);
    expect(compactMarkerPayload(overBoundary)).toHaveLength(246);
    expect(compactMarkerLines(overBoundary).map((line) => line.length)).toEqual([243, 10]);
  });

  it("有效旧坐标和端口在保存时自动迁移，损坏旧注释仍原样保留", () => {
    const legacy = parseMermaidFlowchart(`flowchart LR
%% editor-layout:
%% {"A":{"x":80,"y":70},"B":{"x":300,"y":70}}
%% editor-edge-ports:
%% [{"source":"A","target":"B","sourceHandle":"source-0","targetHandle":"target-3"}]
A --> B`);
    const migrated = serializeMermaidGraph(legacy);

    expect(compactMarkers(migrated)).toHaveLength(1);
    expect(migrated).not.toContain("editor-layout");
    expect(migrated).not.toContain("editor-edge-ports");
    expect(parseMermaidFlowchart(migrated).edges[0]).toMatchObject({
      sourceHandle: "source-0",
      targetHandle: "target-3"
    });

    const damaged = parseMermaidFlowchart(`flowchart TD
%% editor-layout:
%% not-json
A --> B`);
    expect(serializeMermaidGraph(damaged)).toContain("%% editor-layout:\n%% not-json");

    const legacySequence = parseMermaidSequence(`sequenceDiagram
%% editor-layout:
%% {"U":{"x":80,"y":70},"S":{"x":300,"y":70}}
actor U as 用户
participant S as 服务
U->>S: 请求`);
    const migratedSequence = serializeMermaidSequence(legacySequence);
    expect(compactMarkers(migratedSequence)).toHaveLength(1);
    expect(migratedSequence).not.toContain("editor-layout");
    expect(parseMermaidSequence(migratedSequence).participants.map((participant) => participant.position)).toEqual([
      { x: 80, y: 70 },
      { x: 300, y: 70 }
    ]);
  });

  it("没有非零坐标、固定端口或有效路由时不输出私有注释", () => {
    expect(serializeMermaidGraph(parseMermaidFlowchart("flowchart TD\nA --> B"))).not.toContain("%%@");
    expect(serializeMermaidSequence(parseMermaidSequence("sequenceDiagram\nA->>B: hi"))).not.toContain("%%@");
  });

  it("代表图的私有注释只有一行且不超过展开 JSON 的 10%", () => {
    const graph = parseMermaidFlowchart(`flowchart TD
A --> B
A --> C
B --> D
C --> D
D --> E
B --> F
C --> G
F --> E
G --> E`);
    graph.nodes.forEach((node, index) => {
      node.position = { x: 80 + (index % 3) * 220.1, y: 70 + Math.floor(index / 3) * 180.1 };
    });
    graph.edges.forEach((edge, index) => {
      const source = graph.nodes.find((node) => node.id === edge.source)!.position;
      const target = graph.nodes.find((node) => node.id === edge.target)!.position;
      edge.sourceHandle = `source-${index % 6}`;
      edge.targetHandle = `target-${(index + 2) % 6}`;
      const middleY = source.y + (target.y - source.y) / 2;
      const points: MermaidPosition[] = [
        { ...source },
        { x: source.x, y: middleY },
        { x: target.x, y: middleY },
        { ...target }
      ];
      if (index < 4) {
        points.splice(2, 0, { x: (source.x + target.x) / 2, y: middleY });
      }
      edge.route = { points };
    });

    const legacyLayout = Object.fromEntries(graph.nodes.map((node) => [node.id, node.position]));
    const legacyPorts = graph.edges.map(({ source, target, sourceHandle, targetHandle }) => ({
      source,
      target,
      sourceHandle,
      targetHandle
    }));
    const legacyRoutes = graph.edges.map(({ source, target, route }, edgeIndex) => ({
      edgeIndex,
      source,
      target,
      points: route!.points
    }));
    const legacyLength = [legacyLayout, legacyPorts, legacyRoutes]
      .map((value) => JSON.stringify(value, null, 2))
      .join("\n").length;
    const serialized = serializeMermaidGraph(graph);
    const markers = compactMarkers(serialized);

    expect(graph.edges.reduce((count, edge) => count + edge.route!.points.length, 0)).toBe(40);
    expect(markers).toHaveLength(1);
    expect(compactMarkerLines(serialized)).toHaveLength(1);
    expect(markers[0]!.length).toBeLessThanOrEqual(legacyLength * 0.1);
  });

  it("Flow 与 Sequence 的紧凑多行注释均通过 Mermaid 官方 parser", async () => {
    const flow = serializeMermaidGraph(graphWithLargeCompactMetadata());
    const serializedSequence = serializeMermaidSequence(sequenceWithLargeCompactMetadata());
    const mermaid = (await import("mermaid")).default;

    expect(compactMarkerLines(flow).length).toBeGreaterThan(1);
    expect(compactMarkerLines(serializedSequence).length).toBeGreaterThan(1);
    await expect(mermaid.parse(flow)).resolves.toBeTruthy();
    await expect(mermaid.parse(serializedSequence)).resolves.toBeTruthy();
  });

  it("hash 错误、截断、非法 Base64URL 和拓扑变化均不应用且原样保留", () => {
    const valid = compactMarkers(serializeMermaidGraph(graphWithCompactMetadata()))[0]!;
    const validBytes = decodeBase64Url(valid.slice(3));
    const damagedHash = [...validBytes];
    damagedHash[damagedHash.length - 1] = damagedHash.at(-1)! ^ 1;
    const invalidMarkers = [
      `%%@${encodeBase64Url(damagedHash)}`,
      `%%@${encodeBase64Url(validBytes.slice(0, -2))}`,
      "%%@bad*base64"
    ];

    for (const marker of invalidMarkers) {
      const parsed = parseMermaidFlowchart(flowSource(marker));
      expect(parsed.nodes.every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
      expect(parsed.edges[0]!.route).toBeUndefined();
      expect(parsed.preservedLines).toContain(marker);
      expect(compactMarkers(serializeMermaidGraph(parsed))).toEqual([marker]);
    }

    const topologyChanged = flowSource(valid).replace("B{检查}", "B{复核}");
    const parsed = parseMermaidFlowchart(topologyChanged);
    expect(parsed.nodes.every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
    expect(parsed.preservedLines).toContain(valid);
    expect(compactMarkers(serializeMermaidGraph(parsed))).toEqual([valid]);
  });

  it("边标签变化保留路由，而节点文字和方向变化由拓扑 hash 拒绝", () => {
    const serialized = serializeMermaidGraph(graphWithCompactMetadata());
    const labelChanged = parseMermaidFlowchart(serialized.replace("A --> B", "A -->|仅改标签| B"));
    expect(labelChanged.edges[0]!.route?.points).toHaveLength(3);

    const nodeChanged = parseMermaidFlowchart(serialized.replace(
      'A@{ shape: rect, label: "开始" }',
      'A@{ shape: rect, label: "新开始" }'
    ));
    expect(nodeChanged.edges[0]!.route).toBeUndefined();
    expect(nodeChanged.preservedLines).toEqual(expect.arrayContaining(compactMarkers(serialized)));

    const directionChanged = parseMermaidFlowchart(serialized.replace("flowchart LR", "flowchart RL"));
    expect(directionChanged.edges[0]!.route).toBeUndefined();
    expect(directionChanged.preservedLines).toEqual(expect.arrayContaining(compactMarkers(serialized)));
  });

  it("重复或损坏新 marker 时保留全部原文，并继续使用和保留旧格式回退", () => {
    const validMarker = compactMarkers(serializeMermaidGraph(graphWithCompactMetadata()))[0]!;
    const duplicateSource = `flowchart LR
${validMarker}
${validMarker}
%% editor-layout:
%% {"A":{"x":5,"y":6},"B":{"x":30,"y":6}}
A[开始]
B{检查}
A --> B`;
    const duplicate = parseMermaidFlowchart(duplicateSource);
    expect(duplicate.nodes.map((node) => node.position)).toEqual([{ x: 5, y: 6 }, { x: 30, y: 6 }]);
    const duplicateSaved = serializeMermaidGraph(duplicate);
    expect(compactMarkers(duplicateSaved)).toEqual([validMarker, validMarker]);
    expect(duplicateSaved).toContain("%% editor-layout:");

    const damagedMarker = "%%@bad*base64";
    const damagedSource = `flowchart LR
${damagedMarker}
%% editor-layout:
%% {"A":{"x":8,"y":7},"B":{"x":32,"y":7}}
%% editor-edge-ports:
%% [{"source":"A","target":"B","sourceHandle":"source-0","targetHandle":"target-3"}]
A --> B`;
    const damaged = parseMermaidFlowchart(damagedSource);
    expect(damaged.nodes.map((node) => node.position)).toEqual([{ x: 8, y: 7 }, { x: 32, y: 7 }]);
    expect(damaged.edges[0]).toMatchObject({ sourceHandle: "source-0", targetHandle: "target-3" });
    const damagedSaved = serializeMermaidGraph(damaged);
    expect(compactMarkers(damagedSaved)).toEqual([damagedMarker]);
    expect(damagedSaved).toContain("%% editor-layout:");
    expect(damagedSaved).toContain("%% editor-edge-ports:");
  });

  it("Sequence 重复 marker 同样回退并保留旧坐标", () => {
    const diagram = parseMermaidSequence("sequenceDiagram\nactor U as 用户\nparticipant S as 服务\nU->>S: 请求");
    diagram.participants[0]!.position = { x: 80, y: 70 };
    diagram.participants[1]!.position = { x: 300, y: 70 };
    const marker = compactMarkers(serializeMermaidSequence(diagram))[0]!;
    const source = `sequenceDiagram
${marker}
${marker}
%% editor-layout:
%% {"U":{"x":5,"y":6},"S":{"x":25,"y":6}}
actor U as 用户
participant S as 服务
U->>S: 请求`;

    const parsed = parseMermaidSequence(source);
    expect(parsed.participants.map((participant) => participant.position)).toEqual([
      { x: 5, y: 6 },
      { x: 25, y: 6 }
    ]);
    const saved = serializeMermaidSequence(parsed);
    expect(compactMarkers(saved)).toEqual([marker, marker]);
    expect(saved).toContain("%% editor-layout:");
  });

  it("有效新格式优先于旧格式，损坏旧注释仍被保留", () => {
    const serialized = serializeMermaidGraph(graphWithCompactMetadata());
    const source = serialized.replace(
      "flowchart LR\n",
      "flowchart LR\n%% editor-layout:\n%% not-json\n"
    );
    const parsed = parseMermaidFlowchart(source);

    expect(parsed.nodes.map((node) => node.position)).toEqual([
      { x: 10, y: 20.2 },
      { x: 100.4, y: 120.7 }
    ]);
    expect(parsed.preservedLines).toEqual(expect.arrayContaining(["%% editor-layout:", "%% not-json"]));
    const saved = serializeMermaidGraph(parsed);
    expect(compactMarkers(saved)).toHaveLength(1);
    expect(saved).toContain("%% editor-layout:\n%% not-json");
  });

  it("严格拒绝多余 EOF、非法类型/端口、五字节以上 LEB128、超界坐标和 4097 个路由点", () => {
    const topology = parseMermaidFlowchart("flowchart LR\nA[开始]\nB{检查}\nA --> B");
    const signature = flowSignature(topology);
    const overLimitCoordinate = 100_000_001;
    const malformedMarkers = [
      markerForBody([0xa1, 0x09, 2, 0], signature),
      markerForBody([0xa1, 0x02, 2, 1, 0xdd], signature),
      markerForBody([0xa1, 0x01, 0x80, 0x80, 0x80, 0x80, 0x80, 0], signature),
      markerForBody([
        0xa1,
        0x01,
        2,
        1,
        ...unsignedLeb128(overLimitCoordinate * 2),
        0
      ], signature),
      markerForBody([0xa1, 0x04, 2, 1, ...unsignedLeb128(4097)], signature)
    ];
    const validMarker = compactMarkers(serializeMermaidGraph(graphWithCompactMetadata()))[0]!;
    const validBytes = decodeBase64Url(validMarker.slice(3));
    const bodyWithExtraEof = [...validBytes.slice(0, -4), 0];
    malformedMarkers.push(markerForBody(bodyWithExtraEof, signature));

    for (const marker of malformedMarkers) {
      const parsed = parseMermaidFlowchart(flowSource(marker));
      expect(parsed.preservedLines).toContain(marker);
      expect(parsed.nodes.every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
      expect(parsed.edges[0]!.route).toBeUndefined();
    }
  });

  it("拒绝超过 1 MiB 的解码候选且不吞掉原始注释", () => {
    const oversized = `%%@${"A".repeat(Math.ceil(1024 * 1024 * 4 / 3) + 1)}`;
    const parsed = parseMermaidFlowchart(flowSource(oversized));

    expect(parsed.preservedLines).toContain(oversized);
    expect(parsed.nodes.every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
  });

  it("斜线或超过 4096 点的派生路由不进入紧凑 metadata", () => {
    const diagonal = graphWithCompactMetadata();
    diagonal.edges[0]!.route = { points: [{ x: 10, y: 20 }, { x: 100, y: 75 }] };
    expect(parseMermaidFlowchart(serializeMermaidGraph(diagonal)).edges[0]!.route).toBeUndefined();

    const tooMany = graphWithCompactMetadata();
    tooMany.edges[0]!.route = {
      points: Array.from({ length: 4097 }, (_, index) => ({ x: index, y: 0 }))
    };
    expect(parseMermaidFlowchart(serializeMermaidGraph(tooMany)).edges[0]!.route).toBeUndefined();
  });

  it("坐标整体无法编码时，独立有效路由仍按解码后的零坐标源点正确往返", () => {
    const graph = graphWithCompactMetadata();
    graph.nodes[1]!.position.x = Number.NaN;
    graph.edges[0]!.route = {
      points: [{ x: 10, y: 20 }, { x: 10, y: 80 }, { x: 100, y: 80 }]
    };

    const reparsed = parseMermaidFlowchart(serializeMermaidGraph(graph));
    expect(reparsed.nodes.map((node) => node.position)).toEqual([{ x: 0, y: 0 }, { x: 0, y: 0 }]);
    expect(reparsed.edges[0]!.route?.points).toEqual([
      { x: 10, y: 20 },
      { x: 10, y: 80 },
      { x: 100, y: 80 }
    ]);
  });
});
