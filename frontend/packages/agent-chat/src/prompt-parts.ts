import type { PromptPart } from "@test-agent/shared-types";

export type ComposerAttachment = {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  part: Extract<PromptPart, { type: "file" }>;
};

export async function fileToPromptAttachment(file: File): Promise<ComposerAttachment> {
  const mimeType = file.type || "application/octet-stream";
  const basePart = {
    type: "file" as const,
    name: file.name,
    mimeType
  };
  const part: Extract<PromptPart, { type: "file" }> = isInlineTextFile(file)
    ? { ...basePart, content: await file.text() }
    : { ...basePart, url: await fileToDataUrl(file, mimeType) };

  return {
    id: `${file.name}:${file.size}:${file.lastModified}`,
    name: file.name,
    mimeType,
    size: file.size,
    part
  };
}

export function buildComposerPromptParts(prompt: string, attachments: ComposerAttachment[] = []): PromptPart[] {
  const parts: PromptPart[] = [];
  const trimmed = prompt.trim();
  if (trimmed) {
    parts.push({ type: "text", text: trimmed });
  }
  parts.push(...attachments.map((attachment) => attachment.part));
  return parts;
}

function isInlineTextFile(file: File) {
  if (file.type.startsWith("text/")) {
    return true;
  }
  return /\.(md|markdown|txt|json|yaml|yml|xml|csv|ts|tsx|js|jsx|java|py|go|rs|css|html)$/i.test(file.name);
}

async function fileToDataUrl(file: File, mimeType: string) {
  const bytes = new Uint8Array(await file.arrayBuffer());
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return `data:${mimeType};base64,${btoa(binary)}`;
}
