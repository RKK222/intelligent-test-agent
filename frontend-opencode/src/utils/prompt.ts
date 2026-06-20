import type { PromptPart } from "@test-agent/shared-types";

export type PromptFileInput = {
  id?: string;
  path?: string;
  name?: string;
  mimeType?: string;
  content?: string;
  url?: string;
  source?: { start?: number; end?: number; text?: string };
};

export type PromptBuildInput = {
  text?: string;
  files?: PromptFileInput[];
  images?: PromptFileInput[];
  agents?: Array<{ agentId: string; name?: string }>;
  references?: Array<{ id: string; label: string; uri?: string; metadata?: Record<string, unknown> }>;
  agent?: string;
  model?: string;
  variant?: string;
};

export function buildPromptParts(input: PromptBuildInput): PromptPart[] {
  const parts: PromptPart[] = [];
  const text = input.text?.trim();
  if (text) {
    parts.push({ type: "text", text });
  }
  for (const file of input.files ?? []) {
    parts.push(compactFilePart(file));
  }
  for (const image of input.images ?? []) {
    parts.push(compactFilePart(image));
  }
  for (const agent of input.agents ?? []) {
    if (agent.agentId.trim()) {
      parts.push({ type: "agent", agentId: agent.agentId, name: agent.name });
    }
  }
  for (const reference of input.references ?? []) {
    if (reference.id.trim() && reference.label.trim()) {
      parts.push({
        type: "reference",
        id: reference.id,
        label: reference.label,
        uri: reference.uri,
        metadata: reference.metadata
      });
    }
  }
  return parts;
}

export function promptPreviewTitle(text: string, fallback = "New session") {
  const line = text
    .split(/\r?\n/)
    .map((item) => item.trim())
    .find(Boolean);
  if (!line) {
    return fallback;
  }
  return line.length > 72 ? `${line.slice(0, 69)}...` : line;
}

function compactFilePart(file: PromptFileInput): PromptPart {
  return {
    type: "file",
    path: file.path,
    name: file.name,
    mimeType: file.mimeType,
    content: file.content,
    url: file.url,
    source: file.source
  };
}
