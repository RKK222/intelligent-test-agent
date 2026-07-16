import type { MermaidGraph, MermaidPosition } from "./model";
import type { MermaidSequenceDiagram } from "./sequence/model";

const MAGIC_AND_VERSION = 0xa1;
const FLAG_COORDINATES = 1 << 0;
const FLAG_PORTS = 1 << 1;
const FLAG_ROUTES = 1 << 2;
const FLAG_SEQUENCE = 1 << 3;
const KNOWN_FLAGS = FLAG_COORDINATES | FLAG_PORTS | FLAG_ROUTES | FLAG_SEQUENCE;
const BASE64URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
const MAX_DECODED_BYTES = 1024 * 1024;
const MAX_ENCODED_CHARS = Math.ceil(MAX_DECODED_BYTES * 4 / 3);
const COMPACT_MARKER_PREFIX = "%%@";
const COMPACT_CONTINUATION_PREFIX = "%%@+";
const COMPACT_LINE_PAYLOAD_LENGTH = 240;
const MAX_COMPACT_MARKER_LINES = Math.ceil(MAX_ENCODED_CHARS / COMPACT_LINE_PAYLOAD_LENGTH);
const MAX_ROUTE_POINTS = 4096;
const MAX_LEB128_BYTES = 5;
const MAX_UNSIGNED_32 = 0xffff_ffff;
const MAX_ABSOLUTE_COORDINATE = 10_000_000;
const COORDINATE_SCALE = 10;

type ScaledPosition = { x: number; y: number };

type CompactMarkerExtraction = {
  encoded: string | null;
  markerLineIndexes: Set<number>;
};

type DecodedFlowMetadata = {
  hasCoordinates: boolean;
  positions: MermaidPosition[];
  hasPorts: boolean;
  ports: Array<{ sourceHandle?: string; targetHandle?: string }>;
  hasRoutes: boolean;
  routes: Array<{ points: MermaidPosition[] } | undefined>;
};

class ByteReader {
  private offset = 0;
  private readonly bytes: Uint8Array;

  constructor(bytes: Uint8Array) {
    this.bytes = bytes;
  }

  readByte(): number {
    if (this.offset >= this.bytes.length) throw new Error("metadata 已截断");
    return this.bytes[this.offset++]!;
  }

  /** 协议限制单个 LEB128 最多五字节，并拒绝非最短表达，避免歧义和超大整数。 */
  readUnsigned(): number {
    let value = 0;
    let multiplier = 1;
    for (let index = 0; index < MAX_LEB128_BYTES; index += 1) {
      const byte = this.readByte();
      const payload = byte & 0x7f;
      value += payload * multiplier;
      if (!Number.isSafeInteger(value) || value > MAX_UNSIGNED_32) throw new Error("LEB128 超出范围");
      if ((byte & 0x80) === 0) {
        if (index > 0 && payload === 0) throw new Error("LEB128 不是最短表达");
        return value;
      }
      multiplier *= 0x80;
    }
    throw new Error("LEB128 超过五字节");
  }

  get done(): boolean {
    return this.offset === this.bytes.length;
  }
}

function topologySignatureForFlow(graph: MermaidGraph): string {
  return JSON.stringify([
    "flow",
    graph.kind,
    graph.direction,
    graph.nodes.map((node) => [node.id, node.type, node.text]),
    graph.edges.map((edge) => [edge.source, edge.target])
  ]);
}

function topologySignatureForSequence(diagram: MermaidSequenceDiagram): string {
  return JSON.stringify([
    "sequence",
    diagram.participants.map((participant) => [participant.id, participant.type, participant.text])
  ]);
}

function updateFnv1a(hash: number, byte: number): number {
  return Math.imul((hash ^ byte) >>> 0, 0x01000193) >>> 0;
}

/** 拓扑签名与二进制之间加入零字节，避免两个变长输入拼接后产生边界歧义。 */
function checksum(signature: string, body: Uint8Array): number {
  let hash = 0x811c9dc5;
  for (const byte of new TextEncoder().encode(signature)) hash = updateFnv1a(hash, byte);
  hash = updateFnv1a(hash, 0);
  for (const byte of body) hash = updateFnv1a(hash, byte);
  return hash >>> 0;
}

function appendChecksum(body: number[], signature: string): Uint8Array {
  const bodyBytes = Uint8Array.from(body);
  const hash = checksum(signature, bodyBytes);
  return Uint8Array.from([
    ...body,
    hash & 0xff,
    (hash >>> 8) & 0xff,
    (hash >>> 16) & 0xff,
    (hash >>> 24) & 0xff
  ]);
}

function readLittleEndianUint32(bytes: Uint8Array, offset: number): number {
  return (
    (bytes[offset] ?? 0) |
    ((bytes[offset + 1] ?? 0) << 8) |
    ((bytes[offset + 2] ?? 0) << 16) |
    ((bytes[offset + 3] ?? 0) << 24)
  ) >>> 0;
}

/** 浏览器原生能力之外不引入依赖；编码固定为无 padding 的 Base64URL。 */
function encodeBase64Url(bytes: Uint8Array): string {
  let result = "";
  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index]!;
    const second = bytes[index + 1];
    const third = bytes[index + 2];
    result += BASE64URL_ALPHABET[first >>> 2];
    result += BASE64URL_ALPHABET[((first & 0x03) << 4) | ((second ?? 0) >>> 4)];
    if (second !== undefined) {
      result += BASE64URL_ALPHABET[((second & 0x0f) << 2) | ((third ?? 0) >>> 6)];
    }
    if (third !== undefined) result += BASE64URL_ALPHABET[third & 0x3f];
  }
  return result;
}

function decodeBase64Url(value: string): Uint8Array {
  if (!value || value.length % 4 === 1 || value.length > MAX_ENCODED_CHARS) {
    throw new Error("Base64URL 长度非法");
  }
  const output = new Uint8Array(Math.floor(value.length * 3 / 4));
  let outputIndex = 0;
  for (let index = 0; index < value.length; index += 4) {
    const remaining = value.length - index;
    const first = BASE64URL_ALPHABET.indexOf(value[index]!);
    const second = BASE64URL_ALPHABET.indexOf(value[index + 1]!);
    const third = remaining > 2 ? BASE64URL_ALPHABET.indexOf(value[index + 2]!) : -1;
    const fourth = remaining > 3 ? BASE64URL_ALPHABET.indexOf(value[index + 3]!) : -1;
    if (first < 0 || second < 0 || (remaining > 2 && third < 0) || (remaining > 3 && fourth < 0)) {
      throw new Error("Base64URL 字符非法");
    }
    output[outputIndex++] = (first << 2) | (second >>> 4);
    if (remaining === 2) {
      if ((second & 0x0f) !== 0) throw new Error("Base64URL 尾位非法");
      continue;
    }
    output[outputIndex++] = ((second & 0x0f) << 4) | (third >>> 2);
    if (remaining === 3) {
      if ((third & 0x03) !== 0) throw new Error("Base64URL 尾位非法");
      continue;
    }
    output[outputIndex++] = ((third & 0x03) << 6) | fourth;
  }
  if (outputIndex !== output.length || output.length > MAX_DECODED_BYTES) {
    throw new Error("Base64URL 解码长度非法");
  }
  return output;
}

/** 短数据保持原单行格式；长数据只增加显式续行前缀，不改变 Base64URL 本体。 */
function formatCompactMarker(encoded: string): string[] {
  const chunks: string[] = [];
  for (let offset = 0; offset < encoded.length; offset += COMPACT_LINE_PAYLOAD_LENGTH) {
    chunks.push(encoded.slice(offset, offset + COMPACT_LINE_PAYLOAD_LENGTH));
  }
  return chunks.map((chunk, index) =>
    `${index === 0 ? COMPACT_MARKER_PREFIX : COMPACT_CONTINUATION_PREFIX}${chunk}`
  );
}

function writeUnsigned(output: number[], value: number): void {
  if (!Number.isInteger(value) || value < 0 || value > MAX_UNSIGNED_32) {
    throw new Error("无符号整数超出范围");
  }
  let remaining = value;
  do {
    const payload = remaining % 0x80;
    remaining = Math.floor(remaining / 0x80);
    output.push(payload | (remaining > 0 ? 0x80 : 0));
  } while (remaining > 0);
}

function zigZag(value: number): number {
  if (!Number.isInteger(value)) throw new Error("ZigZag 只接受整数");
  const encoded = value >= 0 ? value * 2 : (-value * 2) - 1;
  if (!Number.isSafeInteger(encoded) || encoded > MAX_UNSIGNED_32) throw new Error("ZigZag 超出范围");
  return encoded;
}

function unZigZag(value: number): number {
  return value % 2 === 0 ? value / 2 : -((value + 1) / 2);
}

function scaledCoordinate(value: number): number | null {
  if (!Number.isFinite(value) || Math.abs(value) > MAX_ABSOLUTE_COORDINATE) return null;
  const scaled = Math.round(value * COORDINATE_SCALE);
  return Number.isSafeInteger(scaled) ? scaled : null;
}

function scaledPosition(position: MermaidPosition): ScaledPosition | null {
  const x = scaledCoordinate(position.x);
  const y = scaledCoordinate(position.y);
  return x === null || y === null ? null : { x, y };
}

function positionFromScaled(position: ScaledPosition): MermaidPosition {
  return { x: position.x / COORDINATE_SCALE, y: position.y / COORDINATE_SCALE };
}

function portCode(handle: string | undefined): number {
  const match = handle?.match(/^(source|target)-([0-5])$/);
  if (!match) return 0;
  const index = Number(match[2]);
  return match[1] === "source" ? index + 1 : index + 7;
}

function handleFromPortCode(code: number): string | undefined {
  if (code === 0) return undefined;
  if (code >= 1 && code <= 6) return `source-${code - 1}`;
  if (code >= 7 && code <= 12) return `target-${code - 7}`;
  throw new Error("端口 nibble 非法");
}

function encodeCoordinates(output: number[], positions: ScaledPosition[]): void {
  let previous = { x: 0, y: 0 };
  for (const position of positions) {
    writeUnsigned(output, zigZag(position.x - previous.x));
    writeUnsigned(output, zigZag(position.y - previous.y));
    previous = position;
  }
}

function decodeCoordinates(reader: ByteReader, count: number): ScaledPosition[] {
  const positions: ScaledPosition[] = [];
  let previous = { x: 0, y: 0 };
  for (let index = 0; index < count; index += 1) {
    const position = {
      x: previous.x + unZigZag(reader.readUnsigned()),
      y: previous.y + unZigZag(reader.readUnsigned())
    };
    if (
      Math.abs(position.x) > MAX_ABSOLUTE_COORDINATE * COORDINATE_SCALE ||
      Math.abs(position.y) > MAX_ABSOLUTE_COORDINATE * COORDINATE_SCALE
    ) throw new Error("坐标超出范围");
    positions.push(position);
    previous = position;
  }
  return positions;
}

function scaledOrthogonalRoute(points: MermaidPosition[]): ScaledPosition[] | null {
  if (points.length < 2 || points.length > MAX_ROUTE_POINTS) return null;
  const scaled = points.map(scaledPosition);
  if (scaled.some((point) => point === null)) return null;
  const valid = (scaled as ScaledPosition[]).slice(1).every((point, index) => {
    const previous = (scaled as ScaledPosition[])[index]!;
    const changesX = point.x !== previous.x;
    const changesY = point.y !== previous.y;
    return changesX !== changesY;
  });
  return valid ? scaled as ScaledPosition[] : null;
}

function encodeRoutes(
  output: number[],
  routes: Array<ScaledPosition[] | null>,
  sourcePositions: ScaledPosition[]
): void {
  routes.forEach((points, edgeIndex) => {
    if (!points) {
      writeUnsigned(output, 0);
      return;
    }
    writeUnsigned(output, points.length);
    const source = sourcePositions[edgeIndex]!;
    const first = points[0]!;
    writeUnsigned(output, zigZag(first.x - source.x));
    writeUnsigned(output, zigZag(first.y - source.y));
    for (let pointIndex = 1; pointIndex < points.length; pointIndex += 1) {
      const previous = points[pointIndex - 1]!;
      const point = points[pointIndex]!;
      const axis = point.x !== previous.x ? 0 : 1;
      const delta = axis === 0 ? point.x - previous.x : point.y - previous.y;
      writeUnsigned(output, zigZag(delta) * 2 + axis);
    }
  });
}

function decodeRoutes(
  reader: ByteReader,
  edgeCount: number,
  sourcePositions: ScaledPosition[]
): Array<{ points: MermaidPosition[] } | undefined> {
  const routes: Array<{ points: MermaidPosition[] } | undefined> = [];
  for (let edgeIndex = 0; edgeIndex < edgeCount; edgeIndex += 1) {
    const pointCount = reader.readUnsigned();
    if (pointCount === 0) {
      routes.push(undefined);
      continue;
    }
    if (pointCount < 2 || pointCount > MAX_ROUTE_POINTS) throw new Error("路由点数量非法");
    const source = sourcePositions[edgeIndex]!;
    const first = {
      x: source.x + unZigZag(reader.readUnsigned()),
      y: source.y + unZigZag(reader.readUnsigned())
    };
    const points = [first];
    for (let pointIndex = 1; pointIndex < pointCount; pointIndex += 1) {
      const packed = reader.readUnsigned();
      const axis = packed % 2;
      const delta = unZigZag(Math.floor(packed / 2));
      if (delta === 0) throw new Error("路由包含零长度线段");
      const previous = points[pointIndex - 1]!;
      points.push(axis === 0
        ? { x: previous.x + delta, y: previous.y }
        : { x: previous.x, y: previous.y + delta });
    }
    if (points.some((point) =>
      Math.abs(point.x) > MAX_ABSOLUTE_COORDINATE * COORDINATE_SCALE ||
      Math.abs(point.y) > MAX_ABSOLUTE_COORDINATE * COORDINATE_SCALE
    )) throw new Error("路由坐标超出范围");
    routes.push({ points: points.map(positionFromScaled) });
  }
  return routes;
}

function verifyEnvelope(encoded: string, signature: string): Uint8Array {
  const bytes = decodeBase64Url(encoded);
  if (bytes.length < 8) throw new Error("metadata 已截断");
  const body = bytes.subarray(0, bytes.length - 4);
  const expected = readLittleEndianUint32(bytes, bytes.length - 4);
  if (checksum(signature, body) !== expected) throw new Error("metadata 校验失败");
  return body;
}

/**
 * 提取至多一个逻辑 marker：首行后只拼接紧邻的 `%%@+` 续行。多个首行、孤立续行或
 * 累计超限都视为冲突并原样保留，避免损坏数据覆盖 Markdown。
 */
export function extractMermaidCompactMarker(lines: readonly string[]): CompactMarkerExtraction {
  const markerLineIndexes = new Set<number>();
  let startCount = 0;
  let encodedCandidate: string | null = null;
  let hasOrphanContinuation = false;
  let hasInvalidChunk = false;
  // 冲突 marker 无需记录无限索引；合法单块最多只有该上限，超出后源码行仍会由 parser 普通保留。
  const trackMarkerLine = (index: number) => {
    if (markerLineIndexes.size < MAX_COMPACT_MARKER_LINES) markerLineIndexes.add(index);
  };
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]!;
    if (line.startsWith(COMPACT_CONTINUATION_PREFIX)) {
      trackMarkerLine(index);
      hasOrphanContinuation = true;
      encodedCandidate = null;
      continue;
    }
    if (!line.startsWith(COMPACT_MARKER_PREFIX)) continue;

    trackMarkerLine(index);
    startCount += 1;
    // 第二个首行已经足以判定冲突，后续只做长度扫描，不再复制或拼接潜在的超大 payload。
    if (startCount > 1) encodedCandidate = null;
    const firstChunkLength = line.length - COMPACT_MARKER_PREFIX.length;
    let chunkCount = 1;
    let encodedLength = firstChunkLength;
    let hasContinuation = false;
    let invalidCandidate = firstChunkLength === 0 || encodedLength > MAX_ENCODED_CHARS;
    const shouldCollect = startCount === 1 && !hasOrphanContinuation && !hasInvalidChunk && !invalidCandidate;
    const chunks = shouldCollect ? [line.slice(COMPACT_MARKER_PREFIX.length)] : [];
    while (lines[index + 1]?.startsWith(COMPACT_CONTINUATION_PREFIX)) {
      index += 1;
      trackMarkerLine(index);
      hasContinuation = true;
      const chunkLine = lines[index]!;
      const chunkLength = chunkLine.length - COMPACT_CONTINUATION_PREFIX.length;
      chunkCount += 1;
      encodedLength += chunkLength;
      if (
        chunkLength === 0 ||
        firstChunkLength > COMPACT_LINE_PAYLOAD_LENGTH ||
        chunkLength > COMPACT_LINE_PAYLOAD_LENGTH ||
        chunkCount > MAX_COMPACT_MARKER_LINES ||
        encodedLength > MAX_ENCODED_CHARS
      ) invalidCandidate = true;
      if (shouldCollect && !invalidCandidate) chunks.push(chunkLine.slice(COMPACT_CONTINUATION_PREFIX.length));
    }
    if (hasContinuation && firstChunkLength > COMPACT_LINE_PAYLOAD_LENGTH) invalidCandidate = true;
    if (invalidCandidate) {
      hasInvalidChunk = true;
      encodedCandidate = null;
      continue;
    }
    if (shouldCollect) encodedCandidate = chunks.join("");
  }
  return {
    encoded: startCount === 1 && !hasOrphanContinuation && !hasInvalidChunk ? encodedCandidate : null,
    markerLineIndexes
  };
}

export function containsMermaidCompactMarker(lines: readonly string[]): boolean {
  return lines.some((line) => line.startsWith("%%@"));
}

/** 把 Flow 私有状态编码成至多一个紧凑 marker；存在冲突 marker 时保持只读，不制造重复。 */
export function serializeMermaidCompactFlow(graph: MermaidGraph): string[] {
  // 已保留的损坏/重复新 marker 必须阻止写入第二个 marker，否则下次解析会失去旧格式回退。
  if (containsMermaidCompactMarker(graph.preservedLines)) return [];

  const positions = graph.nodes.map((node) => scaledPosition(node.position));
  const hasValidCoordinates = positions.every((position) => position !== null);
  const hasCoordinates = hasValidCoordinates && (positions as ScaledPosition[]).some(
    (position) => position.x !== 0 || position.y !== 0
  );
  const portBytes = graph.edges.map((edge) => portCode(edge.sourceHandle) | (portCode(edge.targetHandle) << 4));
  const hasPorts = portBytes.some((value) => value !== 0);
  const nodePositionsById = new Map(
    graph.nodes.flatMap((node, index) => positions[index] ? [[node.id, positions[index]!] as const] : [])
  );
  const nodeIds = new Set(graph.nodes.map((node) => node.id));
  const routes = graph.edges.map((edge) => scaledOrthogonalRoute(edge.route?.points ?? []));
  // 坐标 flag 缺失时，解码后的节点坐标按协议为零；此时首路由点也必须相对零点编码。
  const routeSources = graph.edges.map((edge) =>
    hasCoordinates ? nodePositionsById.get(edge.source) ?? { x: 0, y: 0 } : { x: 0, y: 0 }
  );
  const encodableRoutes = routes.map((route, index) => nodeIds.has(graph.edges[index]!.source) ? route : null);
  const hasRoutes = encodableRoutes.some((route) => route !== null);
  if (!hasCoordinates && !hasPorts && !hasRoutes) return [];

  const flags =
    (hasCoordinates ? FLAG_COORDINATES : 0) |
    (hasPorts ? FLAG_PORTS : 0) |
    (hasRoutes ? FLAG_ROUTES : 0);
  const body = [MAGIC_AND_VERSION, flags];
  writeUnsigned(body, graph.nodes.length);
  writeUnsigned(body, graph.edges.length);
  if (hasCoordinates) encodeCoordinates(body, positions as ScaledPosition[]);
  if (hasPorts) body.push(...portBytes);
  if (hasRoutes) encodeRoutes(body, encodableRoutes, routeSources);
  const bytes = appendChecksum(body, topologySignatureForFlow(graph));
  return bytes.length <= MAX_DECODED_BYTES ? formatCompactMarker(encodeBase64Url(bytes)) : [];
}

/** 完整校验后原子应用 Flow 私有状态；任一字段异常都返回 false 且不修改图模型。 */
export function applyMermaidCompactFlow(graph: MermaidGraph, encoded: string): boolean {
  try {
    const body = verifyEnvelope(encoded, topologySignatureForFlow(graph));
    const reader = new ByteReader(body);
    if (reader.readByte() !== MAGIC_AND_VERSION) throw new Error("metadata 版本不支持");
    const flags = reader.readByte();
    if ((flags & ~KNOWN_FLAGS) !== 0 || (flags & FLAG_SEQUENCE) !== 0) throw new Error("Flow flags 非法");
    if ((flags & (FLAG_COORDINATES | FLAG_PORTS | FLAG_ROUTES)) === 0) throw new Error("metadata 内容为空");
    const entityCount = reader.readUnsigned();
    const edgeCount = reader.readUnsigned();
    if (entityCount !== graph.nodes.length || edgeCount !== graph.edges.length) throw new Error("拓扑数量不匹配");

    const hasCoordinates = (flags & FLAG_COORDINATES) !== 0;
    const scaledPositions = hasCoordinates
      ? decodeCoordinates(reader, entityCount)
      : graph.nodes.map(() => ({ x: 0, y: 0 }));
    const hasPorts = (flags & FLAG_PORTS) !== 0;
    const ports: DecodedFlowMetadata["ports"] = [];
    for (let index = 0; index < edgeCount; index += 1) {
      const packed = hasPorts ? reader.readByte() : 0;
      ports.push({
        sourceHandle: handleFromPortCode(packed & 0x0f),
        targetHandle: handleFromPortCode(packed >>> 4)
      });
    }
    const nodePositionsById = new Map(graph.nodes.map((node, index) => [node.id, scaledPositions[index]!]));
    const routeSources = graph.edges.map((edge) => {
      const position = nodePositionsById.get(edge.source);
      if (!position) throw new Error("路由源节点不存在");
      return position;
    });
    const hasRoutes = (flags & FLAG_ROUTES) !== 0;
    const routes = hasRoutes
      ? decodeRoutes(reader, edgeCount, routeSources)
      : graph.edges.map(() => undefined);
    if (!reader.done) throw new Error("metadata 尾部存在多余内容");

    const decoded: DecodedFlowMetadata = {
      hasCoordinates,
      positions: scaledPositions.map(positionFromScaled),
      hasPorts,
      ports,
      hasRoutes,
      routes
    };
    graph.nodes.forEach((node, index) => {
      node.position = { ...decoded.positions[index]! };
    });
    graph.edges.forEach((edge, index) => {
      delete edge.sourceHandle;
      delete edge.targetHandle;
      delete edge.route;
      const port = decoded.ports[index]!;
      if (decoded.hasPorts && port.sourceHandle) edge.sourceHandle = port.sourceHandle;
      if (decoded.hasPorts && port.targetHandle) edge.targetHandle = port.targetHandle;
      const route = decoded.routes[index];
      if (decoded.hasRoutes && route) edge.route = { points: route.points.map((point) => ({ ...point })) };
    });
    return true;
  } catch {
    return false;
  }
}

/** Sequence 只编码参与者坐标，并设置类型 bit 防止被 Flow 解码器误用。 */
export function serializeMermaidCompactSequence(diagram: MermaidSequenceDiagram): string[] {
  if (containsMermaidCompactMarker(diagram.preservedLines)) return [];
  const positions = diagram.participants.map((participant) => scaledPosition(participant.position));
  if (
    !positions.every((position) => position !== null) ||
    !(positions as ScaledPosition[]).some((position) => position.x !== 0 || position.y !== 0)
  ) return [];
  const body = [MAGIC_AND_VERSION, FLAG_COORDINATES | FLAG_SEQUENCE];
  writeUnsigned(body, diagram.participants.length);
  writeUnsigned(body, 0);
  encodeCoordinates(body, positions as ScaledPosition[]);
  const bytes = appendChecksum(body, topologySignatureForSequence(diagram));
  return bytes.length <= MAX_DECODED_BYTES ? formatCompactMarker(encodeBase64Url(bytes)) : [];
}

/** 校验并原子应用 Sequence 参与者坐标；消息不属于布局拓扑签名。 */
export function applyMermaidCompactSequence(diagram: MermaidSequenceDiagram, encoded: string): boolean {
  try {
    const body = verifyEnvelope(encoded, topologySignatureForSequence(diagram));
    const reader = new ByteReader(body);
    if (reader.readByte() !== MAGIC_AND_VERSION) throw new Error("metadata 版本不支持");
    const flags = reader.readByte();
    if (
      (flags & ~KNOWN_FLAGS) !== 0 ||
      (flags & FLAG_SEQUENCE) === 0 ||
      (flags & FLAG_COORDINATES) === 0 ||
      (flags & (FLAG_PORTS | FLAG_ROUTES)) !== 0
    ) throw new Error("Sequence flags 非法");
    const entityCount = reader.readUnsigned();
    const edgeCount = reader.readUnsigned();
    if (entityCount !== diagram.participants.length || edgeCount !== 0) throw new Error("Sequence 拓扑数量不匹配");
    const positions = decodeCoordinates(reader, entityCount);
    if (!reader.done) throw new Error("metadata 尾部存在多余内容");
    diagram.participants.forEach((participant, index) => {
      participant.position = positionFromScaled(positions[index]!);
    });
    return true;
  } catch {
    return false;
  }
}
