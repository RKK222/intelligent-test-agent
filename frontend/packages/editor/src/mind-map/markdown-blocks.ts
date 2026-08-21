import type { MindMapFenceBlock } from "./model";

interface MarkdownLine {
  start: number;
  contentEnd: number;
  nextStart: number;
  text: string;
}

function readLine(markdown: string, start: number): MarkdownLine {
  const newline = markdown.indexOf("\n", start);
  if (newline < 0) {
    return { start, contentEnd: markdown.length, nextStart: markdown.length, text: markdown.slice(start) };
  }
  const contentEnd = newline > start && markdown[newline - 1] === "\r" ? newline - 1 : newline;
  return {
    start,
    contentEnd,
    nextStart: newline + 1,
    text: markdown.slice(start, contentEnd)
  };
}

function parseOpeningFence(line: string): { marker: string; isMind: boolean } | undefined {
  const match = line.match(/^ {0,3}(`{3,}|~{3,})([^\r\n]*)$/);
  if (!match) return undefined;
  const marker = match[1]!;
  const info = match[2]!.trim();
  if (marker[0] === "`" && info.includes("`")) return undefined;
  return { marker, isMind: info.split(/\s+/)[0] === "mind" };
}

function isClosingFence(line: string, openingMarker: string): boolean {
  const match = line.match(/^ {0,3}(`{3,}|~{3,})[\t ]*$/);
  return Boolean(
    match
    && match[1]![0] === openingMarker[0]
    && match[1]!.length >= openingMarker.length
  );
}

/** 精确扫描 mind fence，只记录内容区间，避免修改同文档的其它围栏。 */
export function findMindMapBlocks(markdown: string): MindMapFenceBlock[] {
  const blocks: MindMapFenceBlock[] = [];
  let offset = 0;
  while (offset < markdown.length) {
    const openingLine = readLine(markdown, offset);
    const opening = parseOpeningFence(openingLine.text);
    if (!opening || openingLine.nextStart === openingLine.start) {
      offset = openingLine.nextStart;
      continue;
    }
    const sourceStart = openingLine.nextStart;
    let cursor = sourceStart;
    let sourceEnd = markdown.length;
    let nextOffset = markdown.length;
    while (cursor < markdown.length) {
      const line = readLine(markdown, cursor);
      if (isClosingFence(line.text, opening.marker)) {
        sourceEnd = line.start;
        nextOffset = line.nextStart;
        break;
      }
      if (line.nextStart === cursor) break;
      cursor = line.nextStart;
    }
    if (opening.isMind) {
      blocks.push({
        index: blocks.length,
        source: markdown.slice(sourceStart, sourceEnd),
        sourceStart,
        sourceEnd,
        sourceLine: markdown.slice(0, sourceStart).split(/\r?\n/).length
      });
    }
    offset = nextOffset;
  }
  return blocks;
}

/** expectedSource 是打开编辑器时的原文快照，用于拒绝覆盖 Agent 的并发刷新。 */
export function replaceMindMapBlock(
  markdown: string,
  index: number,
  source: string,
  expectedSource?: string
): string {
  const block = findMindMapBlocks(markdown)[index];
  if (!block) throw new Error(`找不到第 ${index + 1} 个思维导图代码块`);
  if (expectedSource !== undefined && block.source !== expectedSource) {
    throw new Error(`第 ${index + 1} 个思维导图代码块已发生变化，请关闭后重新打开`);
  }
  const lineEnding = block.source.includes("\r\n") ? "\r\n" : "\n";
  const normalizedSource = source.replace(/\r?\n/g, lineEnding);
  const normalized = normalizedSource.endsWith(lineEnding)
    ? normalizedSource
    : `${normalizedSource}${lineEnding}`;
  return `${markdown.slice(0, block.sourceStart)}${normalized}${markdown.slice(block.sourceEnd)}`;
}
