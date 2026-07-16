import type { MessagePart } from "@test-agent/shared-types";
import { getToolInfo, normalizeToolName } from "./tool-registry";

export type WorkStatusEventDescriptor = {
  key: string;
  label: string;
};

const EXPLORE_TOOLS = new Set(["read", "list", "glob", "grep"]);
const EDIT_TOOLS = new Set(["edit", "str_replace", "multi_edit"]);
const WRITE_TOOLS = new Set(["write", "create_file"]);
const WEB_TOOLS = new Set(["webfetch", "web_fetch", "websearch", "web_search"]);
const TODO_TOOLS = new Set(["todowrite", "todo_write"]);

/**
 * 把普通工具归入稳定的工作状态类别；未知工具按工具名独立成组，避免丢失事件。
 */
export function workStatusEventDescriptor(
  part: Extract<MessagePart, { type: "tool" }>
): WorkStatusEventDescriptor {
  const tool = normalizeToolName(part);
  if (EXPLORE_TOOLS.has(tool)) return { key: "explore", label: "探索" };
  if (tool === "skill") return { key: "skill", label: "技能" };
  if (tool === "bash") return { key: "shell", label: "命令行" };
  if (EDIT_TOOLS.has(tool)) return { key: "edit", label: "编辑" };
  if (WRITE_TOOLS.has(tool)) return { key: "write", label: "写入" };
  if (tool === "apply_patch") return { key: "patch", label: "补丁" };
  if (WEB_TOOLS.has(tool)) return { key: "web", label: "网页" };
  if (TODO_TOOLS.has(tool)) return { key: "todo", label: "待办" };
  return { key: `other:${tool}`, label: getToolInfo(part).title || part.toolName };
}
