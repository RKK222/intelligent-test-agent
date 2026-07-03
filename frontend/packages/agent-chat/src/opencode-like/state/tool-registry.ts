import type { MessagePart } from "@test-agent/shared-types";
import { text } from "../../chat-utils";

export type ToolInfo = {
  title: string;
  subtitle?: string;
  fullSubtitle?: string;
  family: "context" | "shell" | "file" | "diff" | "web" | "task" | "question" | "skill" | "generic";
};

const CONTEXT_TOOLS = new Set(["read", "list", "glob", "grep"]);

export function normalizeToolName(part: Extract<MessagePart, { type: "tool" }>): string {
  return part.toolName.trim().toLowerCase();
}

export function isContextTool(part: Extract<MessagePart, { type: "tool" }>): boolean {
  const status = part.status.toLowerCase();
  return CONTEXT_TOOLS.has(normalizeToolName(part)) && status !== "error" && status !== "failed";
}

export function getToolInfo(part: Extract<MessagePart, { type: "tool" }>): ToolInfo {
  const tool = normalizeToolName(part);
  const path = toolPath(part);
  const displayPath = formatDisplayPath(path);
  if (tool === "skill") {
    return { title: "skill", subtitle: displayPath, fullSubtitle: path, family: "skill" };
  }
  if (tool === "bash") {
    return { title: "bash", subtitle: commandValue(part), family: "shell" };
  }
  if (tool === "read") {
    return { title: "read", subtitle: displayPath, fullSubtitle: path, family: "context" };
  }
  if (tool === "list") {
    return { title: "list", subtitle: displayPath, fullSubtitle: path, family: "context" };
  }
  if (tool === "glob") {
    return { title: "glob", subtitle: text(part.input?.pattern), family: "context" };
  }
  if (tool === "grep") {
    const pattern = text(part.input?.pattern);
    return { title: "grep", subtitle: pattern ?? displayPath, fullSubtitle: pattern ? path : undefined, family: "context" };
  }
  if (tool === "edit" || tool === "write") {
    return { title: tool, subtitle: displayPath, fullSubtitle: path, family: "file" };
  }
  if (tool === "apply_patch") {
    return { title: "apply patch", subtitle: displayPath, fullSubtitle: path, family: "diff" };
  }
  if (tool === "webfetch" || tool === "web_fetch") {
    return { title: "web fetch", subtitle: text(part.input?.url), family: "web" };
  }
  if (tool === "websearch" || tool === "web_search") {
    return { title: "web search", subtitle: text(part.input?.query), family: "web" };
  }
  if (tool === "task") {
    return { title: "task", subtitle: text(part.input?.description) ?? text(part.input?.prompt), family: "task" };
  }
  if (tool === "question") {
    return { title: "question", subtitle: text(part.input?.question), family: "question" };
  }
  return { title: part.toolName, subtitle: displayPath, fullSubtitle: path, family: "generic" };
}

export function toolPath(part: Extract<MessagePart, { type: "tool" }>): string | undefined {
  return (
    text(part.input?.filePath) ??
    text(part.input?.filepath) ??
    text(part.input?.path) ??
    text(part.metadata?.filePath) ??
    text(part.metadata?.filepath) ??
    text(part.metadata?.path) ??
    outputPath(part.output)
  );
}

export function commandValue(part: Extract<MessagePart, { type: "tool" }>): string | undefined {
  return text(part.input?.command) ?? text(part.input?.cmd);
}

function outputPath(output: unknown): string | undefined {
  if (typeof output !== "string") {
    return undefined;
  }
  const match = /<path>(.+?)<\/path>/s.exec(output);
  return match?.[1]?.trim();
}

export function formatDisplayPath(path: string | undefined): string | undefined {
  if (!path) {
    return undefined;
  }
  let normalized = path.replace(/\\/g, "/");
  const wasAbsolute = normalized.startsWith("/");

  // 1. Strip absolute path prefix
  normalized = normalized.replace(/^.*\/intelligent-test-agent\//, "");
  // 2. Strip test-workspaces/F-COSS/workspace/ or test-workspaces/F-COSS/
  normalized = normalized.replace(/^test-workspaces\/[^/]+\/(workspace\/)?/, "");
  // 3. Strip F-COSS/workspace/ or similar starting prefix
  normalized = normalized.replace(/^[^/]+\/workspace\//, "");

  const personalWorktreeMarker = "workspace/personalworktree/";
  const markerIndex = normalized.indexOf(personalWorktreeMarker);
  if (markerIndex >= 0) {
    const tail = normalized.slice(markerIndex + personalWorktreeMarker.length);
    const segments = tail.split("/").filter(Boolean);
    const branchIndex = segments.findIndex((segment) => segment.startsWith("feature_"));
    if (branchIndex >= 0 && branchIndex + 1 < segments.length) {
      normalized = segments.slice(branchIndex + 1).join("/");
    }
  }

  const segments = normalized.split("/").filter(Boolean);
  if (segments.length === 0) {
    return normalized;
  }
  if (segments.length === 1) {
    return segments[0];
  }

  const last2 = segments.slice(-2).join("/");
  if (last2.length <= 32) {
    if (segments.length > 2) {
      return `.../${last2}`;
    }
    return wasAbsolute ? `/${last2}` : last2;
  }

  const last1 = segments.at(-1)!;
  return segments.length > 1 ? `.../${last1}` : last1;
}
