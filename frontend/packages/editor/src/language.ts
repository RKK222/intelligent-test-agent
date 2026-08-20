/** 独立思维导图使用专属视图，必须在普通 Markdown/纯文本判断之前识别。 */
export function isMindMapPath(path: string): boolean {
  return /\.mind$/i.test(path.trim());
}

export function languageFromPath(path: string) {
  const extension = path.split(".").pop()?.toLowerCase();
  switch (extension) {
    case "ts":
    case "tsx":
      return "typescript";
    case "js":
    case "jsx":
    case "mjs":
      return "javascript";
    case "py":
      return "python";
    case "json":
      return "json";
    case "yml":
    case "yaml":
      return "yaml";
    case "md":
      return "markdown";
    case "css":
      return "css";
    case "html":
      return "html";
    default:
      return "plaintext";
  }
}
