export const MIND_MAP_MAX_NODES = 2_000;
export const MIND_MAP_MAX_DEPTH = 128;
export const MIND_MAP_MAX_LABEL_LENGTH = 4_096;
export const MIND_MAP_MAX_METADATA_BYTES = 1024 * 1024;
export const MIND_MAP_MAX_EDITABLE_BYTES = 5 * 1024 * 1024;
const MIND_MAP_MAX_NODE_NUMERIC_ID = 0xffff_fffe;

export const MIND_MAP_SHAPES = [
  "rectangle",
  "roundedRectangle",
  "ellipse",
  "diamond"
] as const;

export type MindMapShape = typeof MIND_MAP_SHAPES[number];
export type MindMapLineDash = "solid" | "dashed";

export interface MindMapNodeStyle {
  textColor?: string;
  fillColor?: string;
  borderColor?: string;
  borderWidth?: number;
  shape?: MindMapShape;
  fontSize?: number;
  bold?: boolean;
  lineColor?: string;
  lineWidth?: number;
  lineDash?: MindMapLineDash;
}

export interface MindMapUnknownTlv {
  type: number;
  value: Uint8Array;
}

export interface MindMapNodeMetadata {
  collapsed?: boolean;
  style?: MindMapNodeStyle;
  /** v1 中当前读取器不认识的节点属性只透传，绝不能下发给画布执行。 */
  unknownFields?: MindMapUnknownTlv[];
}

export interface MindMapMetadata {
  nextId: number;
  nodes: Record<string, MindMapNodeMetadata>;
  /** v1 中当前读取器不认识的图级字段按 TLV 原值保留。 */
  unknownFields: MindMapUnknownTlv[];
}

export interface MindMapNode extends MindMapNodeMetadata {
  id?: string;
  text: string;
  children: MindMapNode[];
}

export interface MindMapDocument {
  root: MindMapNode;
  nextId: number;
  lineEnding: "\n" | "\r\n";
  metadataPresent: boolean;
  unknownMetadataFields: MindMapUnknownTlv[];
}

export interface MindMapDocumentIssue {
  line?: number;
  code: string;
  message: string;
}

export interface MindMapDocumentStatus {
  canEdit: boolean;
  message?: string;
  issues: MindMapDocumentIssue[];
}

export interface MindMapParseResult {
  document: MindMapDocument | null;
  status: MindMapDocumentStatus;
}

export interface MindMapApplyResult {
  content: string;
  document: MindMapDocument;
}

/** 工作台只保存可序列化文本草稿，禁止把第三方实例或节点对象写入 Pinia。 */
export interface MindMapVisualDraft {
  kind: "mind-map";
  baseContent: string;
  content: string;
}

export interface MindMapFenceBlock {
  index: number;
  source: string;
  sourceStart: number;
  sourceEnd: number;
  sourceLine: number;
}

export interface MindMapCanvasSelection {
  id?: string;
  text: string;
  isRoot: boolean;
  collapsed: boolean;
  hasChildren: boolean;
  style: MindMapNodeStyle;
}

export function isMindMapNodeId(value: string | undefined): value is string {
  if (value === "root") return true;
  const match = value?.match(/^n([1-9]\d*)$/);
  if (!match) return false;
  const digits = match[1]!;
  const maximum = String(MIND_MAP_MAX_NODE_NUMERIC_ID);
  return digits.length < maximum.length
    || (digits.length === maximum.length && digits <= maximum);
}

/**
 * 预留当前所有待编号节点后，判断是否还能为指定数量的新节点分配持久化 ID。
 * nextId 只向前推进，删除节点不会释放编号，因此不能通过扫描空洞来扩容。
 */
export function canAllocateMindMapNodeIds(
  document: MindMapDocument,
  additionalNodes = 0
): boolean {
  if (
    !Number.isInteger(document.nextId)
    || document.nextId < 1
    || document.nextId > MIND_MAP_MAX_NODE_NUMERIC_ID + 1
    || !Number.isInteger(additionalNodes)
    || additionalNodes < 0
  ) {
    return false;
  }
  let maxNumericId = 0;
  let requiredIds = additionalNodes;
  const stack = [...document.root.children];
  while (stack.length > 0) {
    const node = stack.pop()!;
    if (!node.id) {
      requiredIds += 1;
    } else if (node.id.startsWith("n") && isMindMapNodeId(node.id)) {
      maxNumericId = Math.max(maxNumericId, Number(node.id.slice(1)));
    }
    stack.push(...node.children);
  }
  const nextId = Math.max(document.nextId, maxNumericId + 1);
  const availableIds = Math.max(0, MIND_MAP_MAX_NODE_NUMERIC_ID - nextId + 1);
  return requiredIds <= availableIds;
}
