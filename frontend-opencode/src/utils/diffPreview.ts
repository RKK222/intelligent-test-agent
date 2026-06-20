export interface DiffEditorContent {
  path: string;
  language: string;
  original: string;
  modified: string;
}

const languageByExtension: Record<string, string> = {
  css: "css",
  go: "go",
  html: "html",
  java: "java",
  js: "javascript",
  json: "json",
  jsx: "javascript",
  kt: "kotlin",
  md: "markdown",
  py: "python",
  rs: "rust",
  sh: "shell",
  ts: "typescript",
  tsx: "typescript",
  vue: "html",
  yaml: "yaml",
  yml: "yaml"
};

export function buildDiffEditorContent(path: string, patch: string): DiffEditorContent {
  const original: string[] = [];
  const modified: string[] = [];

  for (const line of patch.split("\n")) {
    if (line.startsWith("@@") || line.startsWith("diff --git") || line.startsWith("index ") || line.startsWith("\\ No newline")) {
      continue;
    }
    if (line.startsWith("--- ") || line.startsWith("+++ ")) {
      continue;
    }
    if (line.startsWith("+")) {
      modified.push(line.slice(1));
      continue;
    }
    if (line.startsWith("-")) {
      original.push(line.slice(1));
      continue;
    }
    const context = line.startsWith(" ") ? line.slice(1) : line;
    original.push(context);
    modified.push(context);
  }

  return {
    path,
    language: languageForPath(path),
    original: original.join("\n"),
    modified: modified.join("\n")
  };
}

export function languageForPath(path: string) {
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  return languageByExtension[ext] ?? "plaintext";
}
