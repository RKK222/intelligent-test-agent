import type { MermaidBlock } from "./model";

const MERMAID_FENCE = /^ {0,3}(`{3,}|~{3,})[^\S\r\n]*mermaid(?:[^\S\r\n]+[^\r\n]*)?\r?\n([\s\S]*?)^ {0,3}\1[^\S\r\n]*(?:\r?\n|$)/gim;

/** 扫描 Mermaid fences 并记录内容区间，不重复解析 Markdown 其他块。 */
export function findMermaidBlocks(markdown: string): MermaidBlock[] {
  const blocks: MermaidBlock[] = [];
  for (const match of markdown.matchAll(MERMAID_FENCE)) {
    const fullMatch = match[0];
    const source = match[2] ?? "";
    const openingEnd = fullMatch.indexOf("\n") + 1;
    const sourceStart = (match.index ?? 0) + openingEnd;
    blocks.push({
      index: blocks.length,
      source,
      sourceStart,
      sourceEnd: sourceStart + source.length
    });
  }
  return blocks;
}

/**
 * 只替换指定 fence 内容；expectedSource 用于阻止覆盖打开对话框后由 Agent 刷新的内容。
 */
export function replaceMermaidBlock(
  markdown: string,
  index: number,
  source: string,
  expectedSource?: string
): string {
  const block = findMermaidBlocks(markdown)[index];
  if (!block) throw new Error("找不到要更新的 Mermaid 代码块");
  if (expectedSource !== undefined && block.source !== expectedSource) {
    throw new Error("Mermaid 代码块已发生变化，请关闭后重新打开");
  }
  const normalized = source.endsWith("\n") ? source : `${source}\n`;
  return `${markdown.slice(0, block.sourceStart)}${normalized}${markdown.slice(block.sourceEnd)}`;
}
