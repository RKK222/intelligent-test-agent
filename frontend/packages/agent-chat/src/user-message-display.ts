const CONTEXT_PROMPT_PREFIX = "用户问题：";
const CONTEXT_PROMPT_MARKER = "以下是用户添加的工作区上下文：";

// 后端持久化的 user prompt 可能包含前端拼接的工作区上下文；消息气泡只展示用户原始提问。
export function displayTextFromUserPrompt(text: string): string {
  const normalized = text.replace(/\r\n/g, "\n");
  const prefixedPattern = new RegExp(
    `^${escapeRegExp(CONTEXT_PROMPT_PREFIX)}\\s*([\\s\\S]*?)\\s*${escapeRegExp(CONTEXT_PROMPT_MARKER)}\\s*[\\s\\S]*$`
  );
  const prefixedMatch = normalized.match(prefixedPattern);
  if (prefixedMatch) {
    return prefixedMatch[1]?.trim() || "附件上下文";
  }

  const markerIndex = normalized.indexOf(`\n${CONTEXT_PROMPT_MARKER}`);
  if (markerIndex >= 0) {
    const question = normalized.slice(0, markerIndex).trim();
    return question || "附件上下文";
  }

  return text;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
