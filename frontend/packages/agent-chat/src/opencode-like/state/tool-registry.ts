import type { MessagePart } from "@test-agent/shared-types";
import { text } from "../../chat-utils";

export type ToolInfo = {
  title: string;
  subtitle?: string;
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
  if (tool === "skill") {
    return { title: "Skill", subtitle: path, family: "skill" };
  }
  if (tool === "bash") {
    return { title: "Bash", subtitle: commandValue(part), family: "shell" };
  }
  if (tool === "read") {
    return { title: "Read", subtitle: path, family: "context" };
  }
  if (tool === "list") {
    return { title: "List", subtitle: path, family: "context" };
  }
  if (tool === "glob") {
    return { title: "Glob", subtitle: text(part.input?.pattern), family: "context" };
  }
  if (tool === "grep") {
    return { title: "Grep", subtitle: text(part.input?.pattern) ?? path, family: "context" };
  }
  if (tool === "edit" || tool === "write") {
    return { title: tool === "edit" ? "Edit" : "Write", subtitle: path, family: "file" };
  }
  if (tool === "apply_patch") {
    return { title: "Apply patch", subtitle: path, family: "diff" };
  }
  if (tool === "webfetch" || tool === "web_fetch") {
    return { title: "Web fetch", subtitle: text(part.input?.url), family: "web" };
  }
  if (tool === "websearch" || tool === "web_search") {
    return { title: "Web search", subtitle: text(part.input?.query), family: "web" };
  }
  if (tool === "task") {
    return { title: "Task", subtitle: text(part.input?.description) ?? text(part.input?.prompt), family: "task" };
  }
  if (tool === "question") {
    return { title: "Question", subtitle: text(part.input?.question), family: "question" };
  }
  return { title: part.toolName, subtitle: path, family: "generic" };
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
