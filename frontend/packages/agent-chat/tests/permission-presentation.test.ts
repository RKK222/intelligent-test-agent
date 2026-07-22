import { describe, expect, it } from "vitest";
import { permissionPresentation } from "../src/permission-presentation";

const descriptions = {
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
} as const;

describe("permissionPresentation", () => {
  it.each(Object.entries(descriptions))("uses the opencode 1.18.4 Chinese description for %s", (type, description) => {
    expect(permissionPresentation({
      requestId: `perm_${type}`,
      sessionId: "ses_root",
      type,
      createdAt: "2026-07-21T23:50:18.335217Z"
    })).toMatchObject({ title: "需要权限", description });
  });

  it("prefers explicit copy and patterns[] while trimming and deduplicating in source order", () => {
    expect(permissionPresentation({
      requestId: "perm_explicit",
      sessionId: "ses_root",
      type: "read",
      title: "  自定义标题  ",
      description: "  自定义说明  ",
      patterns: [" src/** ", "", "src/**", "tests/**"],
      pattern: "legacy/**",
      createdAt: "2026-07-21T23:50:18.335217Z"
    })).toEqual({
      title: "自定义标题",
      description: "自定义说明",
      patterns: ["src/**", "tests/**"]
    });
  });
});
