export type ParsedPatch = {
  original: string;
  modified: string;
};

export function parseUnifiedPatch(patch: string): ParsedPatch {
  const original: string[] = [];
  const modified: string[] = [];
  for (const line of patch.split("\n")) {
    if (line.startsWith("---") || line.startsWith("+++") || line.startsWith("@@")) {
      continue;
    }
    if (line.startsWith("-")) {
      original.push(line.slice(1));
      continue;
    }
    if (line.startsWith("+")) {
      modified.push(line.slice(1));
      continue;
    }
    const content = line.startsWith(" ") ? line.slice(1) : line;
    original.push(content);
    modified.push(content);
  }
  return { original: original.join("\n"), modified: modified.join("\n") };
}
