import { describe, expect, it } from "vitest";
import { BackendApiError } from "@test-agent/backend-api";
import { formatAgentConfigError } from "../src/components/agentConfigErrors";

describe("formatAgentConfigError", () => {
  it("shows safe Git failure hint and trace id without leaking stderr or command", () => {
    const error = new BackendApiError(503, {
      success: false,
      code: "GIT_UNAVAILABLE",
      message: "Git 远端认证失败",
      traceId: "trace_git_failure",
      retryable: true,
      details: {
        gitFailureHint: "请检查当前用户保存的 SSH key 是否有目标仓库读取权限。",
        stderr: "Permission denied (publickey).",
        command: "git clone git@gitee.com:org/private.git /data/private"
      }
    });

    const message = formatAgentConfigError(error, "创建 Agent worktree失败");

    expect(message).toBe("创建 Agent worktree失败：请检查当前用户保存的 SSH key 是否有目标仓库读取权限。（traceId: trace_git_failure）");
    expect(message).not.toContain("Permission denied");
    expect(message).not.toContain("git clone");
    expect(message).not.toContain("/data/private");
  });

  it("keeps existing fallback behavior for non Git errors", () => {
    const error = new Error("目录不存在");

    expect(formatAgentConfigError(error, "加载 Agent 文件失败")).toBe("加载 Agent 文件失败：目录不存在");
  });

  it("shows merge conflict files from backend error details", () => {
    const error = new BackendApiError(409, {
      success: false,
      code: "CONFLICT",
      message: "Agent 配置 worktree 合并冲突",
      traceId: "trace_conflict",
      retryable: false,
      details: {
        conflictFiles: ["opencode/agents/review.md", "opencode/skills/pay/SKILL.md"]
      }
    });

    expect(formatAgentConfigError(error, "发布 Agent 配置失败"))
      .toBe("发布 Agent 配置失败：合并冲突，请先处理 opencode/agents/review.md、opencode/skills/pay/SKILL.md 后重试。");
  });
});
