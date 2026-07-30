import type { ModelInfo, PromptPart } from "@test-agent/shared-types";

const WORKSPACE_ATTACHMENT_CONTEXT_TYPE = "workspace_attachment";
const NATIVE_DELIVERY_MODE = "native";

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

/**
 * 已上传到工作区的聊天附件只传递路径元数据，禁止再次读取浏览器 File 并内联到 Run 请求。
 * 后端会把 workspace_attachment 转成工具可读的工作区引用，避免大文件请求和模型媒体类型限制。
 */
export function workspaceFileToPromptAttachment(file: File, workspacePath: string): ComposerAttachment {
  const mimeType = file.type || "application/octet-stream";
  return {
    id: `workspace:${workspacePath}`,
    name: file.name,
    mimeType,
    size: file.size,
    part: {
      type: "file",
      path: workspacePath,
      name: file.name,
      mimeType,
      source: {
        contextType: WORKSPACE_ATTACHMENT_CONTEXT_TYPE
      }
    }
  };
}

/**
 * 根据当前模型的输入模态决定聊天上传附件是否交给 OpenCode 原生 file part。
 * 文本统一声明为 text/plain 触发 OpenCode Read；Office、压缩包和未知二进制继续走工作区工具。
 */
export function routeWorkspaceAttachmentsForModel(
  attachments: ComposerAttachment[],
  model: ModelInfo | undefined
): ComposerAttachment[] {
  return attachments.map((attachment) => {
    if (attachment.part.source?.contextType !== WORKSPACE_ATTACHMENT_CONTEXT_TYPE) {
      return attachment;
    }
    const nativeMime = nativeAttachmentMime(attachment, model);
    const source = { ...attachment.part.source };
    delete source.deliveryMode;
    if (nativeMime) {
      source.deliveryMode = NATIVE_DELIVERY_MODE;
    }
    return {
      ...attachment,
      part: {
        ...attachment.part,
        mimeType: nativeMime ?? attachment.mimeType,
        source
      }
    };
  });
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

function nativeAttachmentMime(attachment: ComposerAttachment, model: ModelInfo | undefined): string | undefined {
  if (isInlineTextFile(attachment)) {
    return "text/plain";
  }
  const mime = attachment.mimeType.toLowerCase();
  const input = model?.capabilities?.input;
  if (mime.startsWith("image/") && input?.image === true) return attachment.mimeType;
  if (mime.startsWith("audio/") && input?.audio === true) return attachment.mimeType;
  if (mime.startsWith("video/") && input?.video === true) return attachment.mimeType;
  if (mime === "application/pdf" && input?.pdf === true) return attachment.mimeType;
  return undefined;
}

function isInlineTextFile(file: Pick<File, "name" | "type"> | Pick<ComposerAttachment, "name" | "mimeType">) {
  const mimeType = "type" in file ? file.type : file.mimeType;
  if (mimeType.startsWith("text/")) {
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
