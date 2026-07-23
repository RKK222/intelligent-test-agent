import type { PermissionRequest } from "@test-agent/shared-types";

export type PermissionPresentation = {
  title: string;
  description?: string;
  patterns: string[];
};

// 文案与仓库内固定版本 opencode 1.18.4 的 zh.ts 保持一致；未知权限不暴露内部类型。
const PERMISSION_DESCRIPTIONS: Readonly<Record<string, string>> = {
  read: "读取文件（匹配文件路径）",
  edit: "修改文件，包括编辑、写入、补丁和多重编辑",
  glob: "使用 glob 模式匹配文件",
  grep: "使用正则表达式搜索文件内容",
  list: "列出目录中的文件",
  bash: "运行 shell 命令",
  task: "启动子智能体",
  skill: "按名称加载技能",
  lsp: "运行语言服务器查询",
  todowrite: "更新待办列表",
  webfetch: "从 URL 获取内容",
  websearch: "搜索网页",
  external_directory: "访问项目目录之外的文件",
  doom_loop: "检测具有相同输入的重复工具调用"
};

/**
 * 把新旧权限请求统一投影成面向用户的展示模型，避免把 permission type/requestId 当作文案。
 */
export function permissionPresentation(request: PermissionRequest): PermissionPresentation {
  const arrayPatterns = normalizePatterns(request.patterns ?? []);
  const patterns = arrayPatterns.length > 0 ? arrayPatterns : normalizePatterns([request.pattern]);
  const title = normalizedText(request.title) ?? "需要权限";
  const description = normalizedText(request.description)
    ?? PERMISSION_DESCRIPTIONS[request.type.trim().toLowerCase()];
  return {
    title,
    ...(description ? { description } : {}),
    patterns
  };
}

function normalizePatterns(values: Array<string | undefined>): string[] {
  const seen = new Set<string>();
  const normalized: string[] = [];
  for (const value of values) {
    const pattern = normalizedText(value);
    if (!pattern || seen.has(pattern)) continue;
    seen.add(pattern);
    normalized.push(pattern);
  }
  return normalized;
}

function normalizedText(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}
