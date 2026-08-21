import {
  MIND_MAP_MAX_METADATA_BYTES,
  MIND_MAP_SHAPES,
  isMindMapNodeId,
  type MindMapMetadata,
  type MindMapNodeMetadata,
  type MindMapNodeStyle,
  type MindMapShape,
  type MindMapUnknownTlv
} from "./model";

const MAGIC = [0x4d, 0x4d, 0x01] as const;
const TYPE_NEXT_ID = 0x01;
const TYPE_NODE = 0x10;
const NODE_COLLAPSED = 0x01;
const NODE_TEXT_COLOR = 0x02;
const NODE_FILL_COLOR = 0x03;
const NODE_BORDER_COLOR = 0x04;
const NODE_BORDER_WIDTH = 0x05;
const NODE_SHAPE = 0x06;
const NODE_FONT_SIZE = 0x07;
const NODE_BOLD = 0x08;
const NODE_LINE_COLOR = 0x09;
const NODE_LINE_WIDTH = 0x0a;
const NODE_LINE_DASH = 0x0b;
const MAX_ENCODED_CHARS = Math.ceil(MIND_MAP_MAX_METADATA_BYTES * 4 / 3);
const BASE64URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

class ByteReader {
  private offset = 0;

  constructor(private readonly bytes: Uint8Array) {}

  get done(): boolean {
    return this.offset === this.bytes.length;
  }

  get remaining(): number {
    return this.bytes.length - this.offset;
  }

  readByte(): number {
    if (this.offset >= this.bytes.length) throw new Error("思维导图元数据已截断");
    return this.bytes[this.offset++]!;
  }

  /** TLV 长度与整数固定为最多五字节的无符号 LEB128，拒绝非最短表达。 */
  readUnsigned(): number {
    let value = 0;
    let multiplier = 1;
    for (let index = 0; index < 5; index += 1) {
      const byte = this.readByte();
      const payload = byte & 0x7f;
      value += payload * multiplier;
      if (!Number.isSafeInteger(value) || value > 0xffff_ffff) {
        throw new Error("思维导图元数据整数越界");
      }
      if ((byte & 0x80) === 0) {
        if (index > 0 && payload === 0) throw new Error("思维导图元数据整数不是最短表达");
        return value;
      }
      multiplier *= 0x80;
    }
    throw new Error("思维导图元数据整数超过五字节");
  }

  readBytes(length: number): Uint8Array {
    if (!Number.isInteger(length) || length < 0 || length > this.remaining) {
      throw new Error("思维导图元数据 TLV 长度非法");
    }
    const value = this.bytes.slice(this.offset, this.offset + length);
    this.offset += length;
    return value;
  }
}

function writeUnsigned(output: number[], value: number): void {
  if (!Number.isInteger(value) || value < 0 || value > 0xffff_ffff) {
    throw new Error("思维导图元数据整数越界");
  }
  let remaining = value;
  do {
    const payload = remaining % 0x80;
    remaining = Math.floor(remaining / 0x80);
    output.push(payload | (remaining > 0 ? 0x80 : 0));
  } while (remaining > 0);
}

function writeTlv(output: number[], type: number, value: Uint8Array | number[]): void {
  if (!Number.isInteger(type) || type <= 0 || type > 0xff) throw new Error("TLV 类型非法");
  output.push(type);
  writeUnsigned(output, value.length);
  output.push(...value);
}

function readTlv(reader: ByteReader): MindMapUnknownTlv {
  const type = reader.readByte();
  if (type === 0) throw new Error("TLV 类型非法");
  return { type, value: reader.readBytes(reader.readUnsigned()) };
}

function updateFnv1a(hash: number, byte: number): number {
  return Math.imul((hash ^ byte) >>> 0, 0x01000193) >>> 0;
}

function fnv1a(bytes: Uint8Array): number {
  let hash = 0x811c9dc5;
  for (const byte of bytes) hash = updateFnv1a(hash, byte);
  return hash >>> 0;
}

function appendChecksum(body: number[]): Uint8Array {
  const bytes = Uint8Array.from(body);
  const hash = fnv1a(bytes);
  return Uint8Array.from([
    ...bytes,
    hash & 0xff,
    (hash >>> 8) & 0xff,
    (hash >>> 16) & 0xff,
    (hash >>> 24) & 0xff
  ]);
}

function readUint32LittleEndian(bytes: Uint8Array, offset: number): number {
  return (
    (bytes[offset] ?? 0)
    | ((bytes[offset + 1] ?? 0) << 8)
    | ((bytes[offset + 2] ?? 0) << 16)
    | ((bytes[offset + 3] ?? 0) << 24)
  ) >>> 0;
}

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
    throw new Error("思维导图元数据 Base64URL 长度超过 1 MiB 限制");
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
      throw new Error("思维导图元数据 Base64URL 字符非法");
    }
    output[outputIndex++] = (first << 2) | (second >>> 4);
    if (remaining === 2) {
      if ((second & 0x0f) !== 0) throw new Error("思维导图元数据 Base64URL 尾位非法");
      continue;
    }
    output[outputIndex++] = ((second & 0x0f) << 4) | (third >>> 2);
    if (remaining === 3) {
      if ((third & 0x03) !== 0) throw new Error("思维导图元数据 Base64URL 尾位非法");
      continue;
    }
    output[outputIndex++] = ((third & 0x03) << 6) | fourth;
  }
  if (outputIndex !== output.length || output.length > MIND_MAP_MAX_METADATA_BYTES) {
    throw new Error("思维导图元数据解码长度超过 1 MiB 限制");
  }
  return output;
}

export function normalizeMindMapColor(value: string): string {
  if (/^#[0-9a-f]{3}$/i.test(value)) {
    return `#${[...value.slice(1)].map((char) => `${char}${char}`).join("")}`.toUpperCase();
  }
  if (/^#[0-9a-f]{6}$/i.test(value)) return value.toUpperCase();
  throw new Error("思维导图颜色必须是三位或六位 HEX");
}

function colorBytes(value: string): number[] {
  const color = normalizeMindMapColor(value);
  return [1, 3, 5].map((offset) => Number.parseInt(color.slice(offset, offset + 2), 16));
}

function colorFromBytes(value: Uint8Array): string {
  if (value.length !== 3) throw new Error("思维导图颜色 TLV 长度非法");
  return `#${[...value].map((byte) => byte.toString(16).padStart(2, "0")).join("")}`.toUpperCase();
}

function singleByte(value: Uint8Array, label: string): number {
  if (value.length !== 1) throw new Error(`${label} TLV 长度非法`);
  return value[0]!;
}

function boundedInteger(value: number, min: number, max: number, label: string): number {
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`思维导图${label}超出 ${min}-${max} 范围`);
  }
  return value;
}

function encodeNodeStyle(output: number[], style: MindMapNodeStyle): void {
  if (style.textColor !== undefined) writeTlv(output, NODE_TEXT_COLOR, colorBytes(style.textColor));
  if (style.fillColor !== undefined) writeTlv(output, NODE_FILL_COLOR, colorBytes(style.fillColor));
  if (style.borderColor !== undefined) writeTlv(output, NODE_BORDER_COLOR, colorBytes(style.borderColor));
  if (style.borderWidth !== undefined) {
    writeTlv(output, NODE_BORDER_WIDTH, [boundedInteger(style.borderWidth, 0, 10, "边框宽度")]);
  }
  if (style.shape !== undefined) {
    const shapeIndex = MIND_MAP_SHAPES.indexOf(style.shape);
    if (shapeIndex < 0) throw new Error("思维导图形状不在白名单内");
    writeTlv(output, NODE_SHAPE, [shapeIndex + 1]);
  }
  if (style.fontSize !== undefined) {
    writeTlv(output, NODE_FONT_SIZE, [boundedInteger(style.fontSize, 10, 72, "字号")]);
  }
  if (style.bold === true) writeTlv(output, NODE_BOLD, [1]);
  if (style.bold !== undefined && typeof style.bold !== "boolean") throw new Error("思维导图粗体值非法");
  if (style.lineColor !== undefined) writeTlv(output, NODE_LINE_COLOR, colorBytes(style.lineColor));
  if (style.lineWidth !== undefined) {
    writeTlv(output, NODE_LINE_WIDTH, [boundedInteger(style.lineWidth, 1, 10, "分支线宽度")]);
  }
  if (style.lineDash === "dashed") writeTlv(output, NODE_LINE_DASH, [1]);
  if (style.lineDash !== undefined && style.lineDash !== "solid" && style.lineDash !== "dashed") {
    throw new Error("思维导图分支线型非法");
  }
}

function encodeNode(id: string, metadata: MindMapNodeMetadata): Uint8Array {
  if (!isMindMapNodeId(id)) throw new Error(`思维导图节点 ID 非法：${id}`);
  const idBytes = new TextEncoder().encode(id);
  const output: number[] = [];
  writeUnsigned(output, idBytes.length);
  output.push(...idBytes);
  if (metadata.collapsed === true) writeTlv(output, NODE_COLLAPSED, [1]);
  if (metadata.collapsed !== undefined && typeof metadata.collapsed !== "boolean") {
    throw new Error("思维导图折叠值非法");
  }
  if (metadata.style) encodeNodeStyle(output, metadata.style);
  for (const field of metadata.unknownFields ?? []) {
    if (field.type >= NODE_COLLAPSED && field.type <= NODE_LINE_DASH) {
      throw new Error("未知节点 TLV 与已知字段冲突");
    }
    writeTlv(output, field.type, field.value);
  }
  return Uint8Array.from(output);
}

function decodeNode(value: Uint8Array): { id: string; metadata: MindMapNodeMetadata } {
  const reader = new ByteReader(value);
  const id = new TextDecoder("utf-8", { fatal: true }).decode(reader.readBytes(reader.readUnsigned()));
  if (!isMindMapNodeId(id)) throw new Error(`思维导图节点 ID 非法：${id}`);
  const metadata: MindMapNodeMetadata = {};
  const style: MindMapNodeStyle = {};
  const unknownFields: MindMapUnknownTlv[] = [];
  const seen = new Set<number>();
  while (!reader.done) {
    const field = readTlv(reader);
    if (field.type <= NODE_LINE_DASH && seen.has(field.type)) throw new Error("思维导图节点属性重复");
    seen.add(field.type);
    switch (field.type) {
      case NODE_COLLAPSED:
        if (singleByte(field.value, "折叠") !== 1) throw new Error("思维导图折叠值非法");
        metadata.collapsed = true;
        break;
      case NODE_TEXT_COLOR:
        style.textColor = colorFromBytes(field.value);
        break;
      case NODE_FILL_COLOR:
        style.fillColor = colorFromBytes(field.value);
        break;
      case NODE_BORDER_COLOR:
        style.borderColor = colorFromBytes(field.value);
        break;
      case NODE_BORDER_WIDTH:
        style.borderWidth = boundedInteger(singleByte(field.value, "边框宽度"), 0, 10, "边框宽度");
        break;
      case NODE_SHAPE: {
        const shape = MIND_MAP_SHAPES[singleByte(field.value, "形状") - 1] as MindMapShape | undefined;
        if (!shape) throw new Error("思维导图形状不在白名单内");
        style.shape = shape;
        break;
      }
      case NODE_FONT_SIZE:
        style.fontSize = boundedInteger(singleByte(field.value, "字号"), 10, 72, "字号");
        break;
      case NODE_BOLD:
        if (singleByte(field.value, "粗体") !== 1) throw new Error("思维导图粗体值非法");
        style.bold = true;
        break;
      case NODE_LINE_COLOR:
        style.lineColor = colorFromBytes(field.value);
        break;
      case NODE_LINE_WIDTH:
        style.lineWidth = boundedInteger(singleByte(field.value, "分支线宽度"), 1, 10, "分支线宽度");
        break;
      case NODE_LINE_DASH:
        if (singleByte(field.value, "分支线型") !== 1) throw new Error("思维导图分支线型非法");
        style.lineDash = "dashed";
        break;
      default:
        unknownFields.push(field);
    }
  }
  if (Object.keys(style).length > 0) metadata.style = style;
  if (unknownFields.length > 0) metadata.unknownFields = unknownFields;
  return { id, metadata };
}

function compareNodeIds(left: string, right: string): number {
  if (left === "root") return right === "root" ? 0 : -1;
  if (right === "root") return 1;
  return Number(left.slice(1)) - Number(right.slice(1));
}

/** 编码只写非默认属性，保证同一模型产生稳定、无 padding 的 Base64URL。 */
export function encodeMindMapMetadata(metadata: MindMapMetadata): string {
  const output: number[] = [...MAGIC];
  const nextId: number[] = [];
  writeUnsigned(nextId, boundedInteger(metadata.nextId, 1, 0xffff_ffff, "nextId"));
  writeTlv(output, TYPE_NEXT_ID, nextId);
  for (const id of Object.keys(metadata.nodes).sort(compareNodeIds)) {
    writeTlv(output, TYPE_NODE, encodeNode(id, metadata.nodes[id]!));
  }
  for (const field of metadata.unknownFields) {
    if (field.type === TYPE_NEXT_ID || field.type === TYPE_NODE) {
      throw new Error("未知图级 TLV 与已知字段冲突");
    }
    writeTlv(output, field.type, field.value);
  }
  const bytes = appendChecksum(output);
  if (bytes.length > MIND_MAP_MAX_METADATA_BYTES) throw new Error("思维导图元数据超过 1 MiB 限制");
  return encodeBase64Url(bytes);
}

export function decodeMindMapMetadata(encoded: string): MindMapMetadata {
  const bytes = decodeBase64Url(encoded);
  if (bytes.length < MAGIC.length + 4) throw new Error("思维导图元数据已截断");
  const body = bytes.slice(0, -4);
  if (readUint32LittleEndian(bytes, bytes.length - 4) !== fnv1a(body)) {
    throw new Error("思维导图元数据 FNV-1a 校验失败");
  }
  if (!MAGIC.every((byte, index) => body[index] === byte)) {
    throw new Error("思维导图元数据二进制版本未知");
  }
  const reader = new ByteReader(body.slice(MAGIC.length));
  let nextId: number | undefined;
  const nodes: Record<string, MindMapNodeMetadata> = {};
  const unknownFields: MindMapUnknownTlv[] = [];
  while (!reader.done) {
    const field = readTlv(reader);
    if (field.type === TYPE_NEXT_ID) {
      if (nextId !== undefined) throw new Error("思维导图 nextId 重复");
      const valueReader = new ByteReader(field.value);
      nextId = valueReader.readUnsigned();
      if (!valueReader.done || nextId < 1) throw new Error("思维导图 nextId 非法");
      continue;
    }
    if (field.type === TYPE_NODE) {
      const decoded = decodeNode(field.value);
      if (nodes[decoded.id]) throw new Error(`思维导图节点属性重复：${decoded.id}`);
      nodes[decoded.id] = decoded.metadata;
      continue;
    }
    unknownFields.push(field);
  }
  if (nextId === undefined) throw new Error("思维导图元数据缺少 nextId");
  return { nextId, nodes, unknownFields };
}
