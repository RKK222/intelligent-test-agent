/**
 * 将文本按关键字分割成片段数组，用于渲染高亮。
 * 匹配不区分大小写。
 *
 * @param text - 原始文本（如文件名）
 * @param keyword - 搜索关键字
 * @returns 片段数组，每个片段包含 text 和 match 属性
 *
 * @example
 * highlightKeyword("AgentConfig.vue", "config")
 * // 返回 [{text: "Agent", match: false}, {text: "Config", match: true}, {text: ".vue", match: false}]
 */
export function highlightKeyword(text: string, keyword: string): Array<{ text: string; match: boolean }> {
  if (!keyword || keyword.trim() === "") {
    return [{ text, match: false }];
  }

  const normalizedKeyword = keyword.trim().toLowerCase();
  const keywordLength = normalizedKeyword.length;
  const normalizedText = text.toLowerCase();
  const segments: Array<{ text: string; match: boolean }> = [];

  let lastIndex = 0;
  let index = normalizedText.indexOf(normalizedKeyword);

  while (index !== -1) {
    // 匹配前的非匹配部分
    if (index > lastIndex) {
      segments.push({ text: text.slice(lastIndex, index), match: false });
    }
    // 匹配部分（使用原始大小写，长度按 trim 后的关键字计算）
    segments.push({ text: text.slice(index, index + keywordLength), match: true });
    lastIndex = index + keywordLength;
    index = normalizedText.indexOf(normalizedKeyword, lastIndex);
  }

  // 剩余的非匹配部分
  if (lastIndex < text.length) {
    segments.push({ text: text.slice(lastIndex), match: false });
  }

  return segments;
}