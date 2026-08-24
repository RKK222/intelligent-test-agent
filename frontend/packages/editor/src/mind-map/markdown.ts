import { decodeMindMapMetadata, encodeMindMapMetadata } from "./compact-metadata";
import {
  MIND_MAP_MAX_DEPTH,
  MIND_MAP_MAX_EDITABLE_BYTES,
  MIND_MAP_MAX_LABEL_LENGTH,
  MIND_MAP_MAX_NODES,
  canAllocateMindMapNodeIds,
  isMindMapNodeId,
  type MindMapApplyResult,
  type MindMapDocument,
  type MindMapDocumentIssue,
  type MindMapMetadata,
  type MindMapNode,
  type MindMapParseResult
} from "./model";

const ROOT_ID_COMMENT = /^\s*<!--\s*mm:id=([^\s<>]+)\s*-->\s*$/;
const NODE_ID_COMMENT = /^(.*?)\s+<!--\s*mm:id=([^\s<>]+)\s*-->\s*$/;
const METADATA_COMMENT = /^\s*<!--mm:v([^:]+):([A-Za-z0-9_-]+)-->\s*$/;

function issue(line: number | undefined, code: string, message: string): MindMapDocumentIssue {
  return {
    line,
    code,
    message: line === undefined ? message : `第 ${line} 行：${message}`
  };
}

/**
 * 节点文字始终以 richText=false 下发，因此 HTML、Markdown、SQL 等特殊字符只按
 * 字面文本显示。这里只拒绝无法安全映射到单行列表的空白、换行和内部保留注释。
 */
function isSupportedNodeText(value: string): boolean {
  return value.trim().length > 0
    && !/[\r\n]/.test(value)
    && !/<!--\s*mm:/i.test(value);
}

function nodeCountAndDepth(root: MindMapNode): { count: number; depth: number } {
  let count = 0;
  let depth = 0;
  const stack = [{ node: root, level: 0 }];
  while (stack.length > 0) {
    const current = stack.pop()!;
    count += 1;
    depth = Math.max(depth, current.level);
    for (let index = current.node.children.length - 1; index >= 0; index -= 1) {
      stack.push({ node: current.node.children[index]!, level: current.level + 1 });
    }
  }
  return { count, depth };
}

function walkNodes(root: MindMapNode): MindMapNode[] {
  const nodes: MindMapNode[] = [];
  const stack = [root];
  while (stack.length > 0) {
    const node = stack.pop()!;
    nodes.push(node);
    for (let index = node.children.length - 1; index >= 0; index -= 1) {
      stack.push(node.children[index]!);
    }
  }
  return nodes;
}

function cloneNode(node: MindMapNode): MindMapNode {
  return {
    id: node.id,
    text: node.text,
    children: node.children.map(cloneNode),
    collapsed: node.collapsed,
    style: node.style ? { ...node.style } : undefined,
    unknownFields: node.unknownFields?.map((field) => ({
      type: field.type,
      value: field.value.slice()
    }))
  };
}

function metadataFromNodes(document: MindMapDocument, root: MindMapNode, nextId: number): MindMapMetadata {
  const nodes: MindMapMetadata["nodes"] = {};
  for (const node of walkNodes(root)) {
    if (!node.id) continue;
    const metadata = {
      collapsed: node.collapsed,
      style: node.style ? { ...node.style } : undefined,
      unknownFields: node.unknownFields?.map((field) => ({
        type: field.type,
        value: field.value.slice()
      }))
    };
    if (metadata.collapsed || metadata.style || metadata.unknownFields?.length) {
      nodes[node.id] = metadata;
    }
  }
  return {
    nextId,
    nodes,
    unknownFields: document.unknownMetadataFields.map((field) => ({
      type: field.type,
      value: field.value.slice()
    }))
  };
}

function applyDecodedMetadata(root: MindMapNode, metadata: MindMapMetadata, issues: MindMapDocumentIssue[]): void {
  const byId = new Map<string, MindMapNode>();
  let maxNumericId = 0;
  for (const node of walkNodes(root)) {
    if (!node.id) {
      issues.push(issue(undefined, "missing-id", "已有元数据时所有节点都必须包含 ID"));
      continue;
    }
    if (!isMindMapNodeId(node.id) || (node === root && node.id !== "root") || (node !== root && node.id === "root")) {
      issues.push(issue(undefined, "invalid-id", `节点 ID 非法：${node.id}`));
      continue;
    }
    if (byId.has(node.id)) {
      issues.push(issue(undefined, "duplicate-id", `节点 ID 重复：${node.id}`));
      continue;
    }
    byId.set(node.id, node);
    if (node.id.startsWith("n")) maxNumericId = Math.max(maxNumericId, Number(node.id.slice(1)));
  }
  if (metadata.nextId <= maxNumericId) {
    issues.push(issue(undefined, "invalid-next-id", "元数据 nextId 必须大于所有已有节点编号"));
  }
  for (const [id, nodeMetadata] of Object.entries(metadata.nodes)) {
    const node = byId.get(id);
    if (!node) {
      issues.push(issue(undefined, "orphan-metadata", `元数据引用了不存在的节点：${id}`));
      continue;
    }
    node.collapsed = nodeMetadata.collapsed;
    node.style = nodeMetadata.style ? { ...nodeMetadata.style } : undefined;
    node.unknownFields = nodeMetadata.unknownFields?.map((field) => ({
      type: field.type,
      value: field.value.slice()
    }));
  }
}

function validateIdsWithoutMetadata(root: MindMapNode, issues: MindMapDocumentIssue[]): number {
  const ids = new Set<string>();
  let maxNumericId = 0;
  for (const node of walkNodes(root)) {
    if (!node.id) continue;
    const valid = isMindMapNodeId(node.id)
      && (node === root ? node.id === "root" : node.id !== "root");
    if (!valid) {
      issues.push(issue(undefined, "invalid-id", `节点 ID 非法：${node.id}`));
      continue;
    }
    if (ids.has(node.id)) issues.push(issue(undefined, "duplicate-id", `节点 ID 重复：${node.id}`));
    ids.add(node.id);
    if (node.id.startsWith("n")) maxNumericId = Math.max(maxNumericId, Number(node.id.slice(1)));
  }
  return maxNumericId;
}

/**
 * 解析器只接受可无歧义映射的普通文本层级列表；错误时尽量保留已经识别的树用于安全预览。
 */
export function parseMindMapMarkdown(source: string): MindMapParseResult {
  const issues: MindMapDocumentIssue[] = [];
  const lineEnding: "\n" | "\r\n" = source.includes("\r\n") ? "\r\n" : "\n";
  if (new TextEncoder().encode(source).length > MIND_MAP_MAX_EDITABLE_BYTES) {
    issues.push(issue(undefined, "file-too-large", "思维导图超过 5 MiB 可编辑限制"));
  }
  if (source.includes("\t")) {
    const tabLine = source.slice(0, source.indexOf("\t")).split(/\r?\n/).length;
    issues.push(issue(tabLine, "tab", "不支持 Tab 缩进"));
  }
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  let root: MindMapNode | null = null;
  let rootLine = 0;
  let rootIdSeen = false;
  let listStarted = false;
  let indentUnit: 2 | 4 | undefined;
  let previousDepth = 0;
  let nodeCount = 0;
  let nodeLimitReported = false;
  let depthLimitReported = false;
  const stack: MindMapNode[] = [];
  const metadataMarkers: Array<{ line: number; version: string; payload: string }> = [];
  let firstMetadataLine: number | undefined;

  for (let index = 0; index < lines.length; index += 1) {
    const lineNumber = index + 1;
    const line = lines[index] ?? "";
    if (line.trim() === "") continue;

    const metadataMatch = line.match(METADATA_COMMENT);
    if (metadataMatch) {
      metadataMarkers.push({ line: lineNumber, version: metadataMatch[1]!, payload: metadataMatch[2]! });
      firstMetadataLine ??= lineNumber;
      continue;
    }
    if (firstMetadataLine !== undefined) {
      issues.push(issue(lineNumber, "content-after-metadata", "元数据注释之后不能再出现正文"));
    }

    const heading = line.match(/^#\s+(.+?)\s*$/);
    if (heading) {
      if (root) {
        issues.push(issue(lineNumber, "multiple-root", "只能有一个 H1 根标题"));
        continue;
      }
      const text = heading[1]!.trim();
      if (!isSupportedNodeText(text)) {
        issues.push(issue(lineNumber, "invalid-root", "根标题必须是非空单行文本，且不能包含保留的 mm 注释"));
      }
      if (text.length > MIND_MAP_MAX_LABEL_LENGTH) {
        issues.push(issue(lineNumber, "label-too-long", `节点文字不能超过 ${MIND_MAP_MAX_LABEL_LENGTH} 字符`));
      }
      root = { text, children: [] };
      rootLine = lineNumber;
      nodeCount = 1;
      continue;
    }

    const rootIdMatch = line.match(ROOT_ID_COMMENT);
    if (rootIdMatch && root && !listStarted && !rootIdSeen) {
      root.id = rootIdMatch[1]!;
      rootIdSeen = true;
      continue;
    }

    if (/^\s*\d+[.)]\s+/.test(line)) {
      issues.push(issue(lineNumber, "ordered-list", "不支持有序列表"));
      continue;
    }

    const item = line.match(/^( *)([-*+])\s+(.+?)\s*$/);
    if (!item) {
      issues.push(issue(lineNumber, "unsupported-line", "只支持 H1、无序列表、节点 ID 和元数据注释"));
      continue;
    }
    if (!root) {
      issues.push(issue(lineNumber, "missing-root", "列表之前必须有一个 H1 根标题"));
      continue;
    }
    listStarted = true;
    const spaces = item[1]!.length;
    if (spaces > 0 && indentUnit === undefined) {
      if (spaces === 2 || spaces === 4) indentUnit = spaces;
      else issues.push(issue(lineNumber, "invalid-indent", "每层缩进只能使用 2 或 4 个空格"));
    }
    const unit = indentUnit ?? 2;
    if (spaces % unit !== 0) {
      issues.push(issue(lineNumber, "invalid-indent", `缩进必须是 ${unit} 个空格的整数倍`));
    }
    let depth = Math.floor(spaces / unit);
    if (depth > previousDepth + 1) {
      issues.push(issue(lineNumber, "skipped-depth", "列表层级不能跳级"));
      depth = previousDepth + 1;
    }
    const withinDepthLimit = depth + 1 <= MIND_MAP_MAX_DEPTH;
    if (!withinDepthLimit && !depthLimitReported) {
      issues.push(issue(lineNumber, "depth-limit", `思维导图最多 ${MIND_MAP_MAX_DEPTH} 层`));
      depthLimitReported = true;
    }

    let text = item[3]!.trim();
    let id: string | undefined;
    const idMatch = text.match(NODE_ID_COMMENT);
    if (idMatch) {
      text = idMatch[1]!.trim();
      id = idMatch[2]!;
    }
    if (!isSupportedNodeText(text)) {
      issues.push(issue(lineNumber, "invalid-text", "节点必须是非空单行文本，且不能包含保留的 mm 注释"));
    }
    if (text.length > MIND_MAP_MAX_LABEL_LENGTH) {
      issues.push(issue(lineNumber, "label-too-long", `节点文字不能超过 ${MIND_MAP_MAX_LABEL_LENGTH} 字符`));
    }
    nodeCount += 1;
    const withinNodeLimit = nodeCount <= MIND_MAP_MAX_NODES;
    if (!withinNodeLimit && !nodeLimitReported) {
      issues.push(issue(lineNumber, "node-limit", `思维导图最多 ${MIND_MAP_MAX_NODES} 个节点`));
      nodeLimitReported = true;
    }
    // 超限源码仍继续扫描元数据和后续语法，但不再扩张交给第三方的安全预览树。
    if (!withinDepthLimit || !withinNodeLimit) {
      previousDepth = depth;
      continue;
    }
    const node: MindMapNode = { id, text, children: [] };
    if (depth === 0) {
      root.children.push(node);
    } else {
      const parent = stack[depth - 1];
      if (!parent) {
        issues.push(issue(lineNumber, "missing-parent", "列表节点缺少上级节点"));
        root.children.push(node);
        depth = 0;
      } else {
        parent.children.push(node);
      }
    }
    stack[depth] = node;
    stack.length = depth + 1;
    previousDepth = depth;
  }

  if (!root) {
    issues.push(issue(undefined, "missing-root", "思维导图缺少非空 H1 根标题"));
    return {
      document: null,
      status: { canEdit: false, message: issues[0]?.message, issues }
    };
  }
  if (root.children.length === 0) {
    issues.push(issue(rootLine, "missing-list", "根标题之后至少需要一个无序列表节点"));
  }

  let metadata: MindMapMetadata | undefined;
  const metadataPresent = metadataMarkers.length > 0;
  if (metadataMarkers.length > 1) {
    issues.push(issue(metadataMarkers[1]!.line, "multiple-metadata", "只能有一个思维导图元数据注释"));
  }
  if (metadataMarkers.length === 1) {
    const marker = metadataMarkers[0]!;
    if (marker.version !== "1") {
      issues.push(issue(marker.line, "unknown-version", `不支持思维导图元数据版本 v${marker.version}`));
    } else {
      try {
        metadata = decodeMindMapMetadata(marker.payload);
      } catch (error) {
        issues.push(issue(marker.line, "invalid-metadata", error instanceof Error ? error.message : "思维导图元数据损坏"));
      }
    }
  }

  let nextId: number;
  if (metadata) {
    applyDecodedMetadata(root, metadata, issues);
    nextId = metadata.nextId;
  } else {
    nextId = validateIdsWithoutMetadata(root, issues) + 1;
  }

  const document: MindMapDocument = {
    root,
    nextId: Math.max(1, nextId),
    lineEnding,
    metadataPresent,
    unknownMetadataFields: metadata?.unknownFields.map((field) => ({
      type: field.type,
      value: field.value.slice()
    })) ?? []
  };
  if (!canAllocateMindMapNodeIds(document)) {
    issues.push(issue(undefined, "id-exhausted", "思维导图节点 ID 空间已耗尽，无法补齐待编号节点"));
  }
  return {
    document,
    status: {
      canEdit: issues.length === 0,
      message: issues[0]?.message,
      issues
    }
  };
}

function validateEditableTree(root: MindMapNode): void {
  const { count, depth } = nodeCountAndDepth(root);
  if (count > MIND_MAP_MAX_NODES) throw new Error(`思维导图最多 ${MIND_MAP_MAX_NODES} 个节点`);
  if (depth > MIND_MAP_MAX_DEPTH) throw new Error(`思维导图最多 ${MIND_MAP_MAX_DEPTH} 层`);
  for (const node of walkNodes(root)) {
    if (node.text.length > MIND_MAP_MAX_LABEL_LENGTH || !isSupportedNodeText(node.text)) {
      throw new Error("节点必须是不超过 4096 字符的非空单行文本，且不能包含保留的 mm 注释");
    }
  }
}

/** 应用操作会深拷贝模型、补齐 ID 并生成唯一元数据注释，打开或取消不会调用此函数。 */
export function prepareMindMapDocument(document: MindMapDocument): MindMapApplyResult {
  if (!canAllocateMindMapNodeIds(document)) {
    throw new Error("思维导图节点 ID 空间已耗尽，无法补齐待编号节点");
  }
  const root = cloneNode(document.root);
  validateEditableTree(root);
  const used = new Set<string>();
  let maxNumericId = 0;
  for (const node of walkNodes(root)) {
    if (!node.id) continue;
    if (!isMindMapNodeId(node.id) || (node === root ? node.id !== "root" : node.id === "root")) {
      throw new Error(`节点 ID 非法：${node.id}`);
    }
    if (used.has(node.id)) throw new Error(`节点 ID 重复：${node.id}`);
    used.add(node.id);
    if (node.id.startsWith("n")) maxNumericId = Math.max(maxNumericId, Number(node.id.slice(1)));
  }
  root.id ??= "root";
  used.add("root");
  let nextId = Math.max(document.nextId, maxNumericId + 1, 1);
  for (const node of walkNodes(root).slice(1)) {
    if (node.id) continue;
    while (used.has(`n${nextId}`)) nextId += 1;
    node.id = `n${nextId}`;
    used.add(node.id);
    nextId += 1;
  }

  const rawMetadata = metadataFromNodes(document, root, nextId);
  // 通过 codec 自身完成颜色归一化和所有属性边界复核，再把规范值写回结果模型。
  const payload = encodeMindMapMetadata(rawMetadata);
  const metadata = decodeMindMapMetadata(payload);
  for (const node of walkNodes(root)) {
    if (!node.id) continue;
    const nodeMetadata = metadata.nodes[node.id];
    node.collapsed = nodeMetadata?.collapsed;
    node.style = nodeMetadata?.style ? { ...nodeMetadata.style } : undefined;
    node.unknownFields = nodeMetadata?.unknownFields?.map((field) => ({
      type: field.type,
      value: field.value.slice()
    }));
  }

  const lines = [`# ${root.text}`, "", "<!-- mm:id=root -->", ""];
  const appendChildren = (children: MindMapNode[], depth: number) => {
    for (const node of children) {
      lines.push(`${"  ".repeat(depth)}- ${node.text} <!-- mm:id=${node.id} -->`);
      appendChildren(node.children, depth + 1);
    }
  };
  appendChildren(root.children, 0);
  lines.push("", `<!--mm:v1:${payload}-->`, "");
  const normalizedDocument: MindMapDocument = {
    root,
    nextId,
    lineEnding: document.lineEnding,
    metadataPresent: true,
    unknownMetadataFields: metadata.unknownFields.map((field) => ({
      type: field.type,
      value: field.value.slice()
    }))
  };
  return {
    content: lines.join(document.lineEnding),
    document: normalizedDocument
  };
}
