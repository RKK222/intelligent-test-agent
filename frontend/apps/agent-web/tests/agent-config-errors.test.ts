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

  it("distinguishes a confirmed missing remote commit from an uncertain push result", () => {
    const retryNow = new BackendApiError(503, {
      success: false,
      code: "GIT_UNAVAILABLE",
      message: "Git 远端网络连接失败",
      traceId: "trace_retry_now",
      retryable: true,
      details: {
        gitFailureHint: "请检查 Git 网络连通性。",
        localCommitRetained: true,
        remoteCommitState: "NOT_REACHED",
        publishRecoveryAction: "RETRY_NOW"
      }
    });
    const waitForRecovery = new BackendApiError(503, {
      success: false,
      code: "GIT_UNAVAILABLE",
      message: "Git 远端网络连接失败",
      traceId: "trace_wait",
      retryable: true,
      details: {
        gitFailureHint: "请检查 Git 网络连通性。",
        localCommitRetained: true,
        remoteCommitState: "UNKNOWN",
        publishRecoveryAction: "WAIT_FOR_RECOVERY"
      }
    });

    expect(formatAgentConfigError(retryNow, "提交失败"))
      .toContain("已确认远端未包含本次提交；可直接点击“重新推送”");
    expect(formatAgentConfigError(waitForRecovery, "提交失败"))
      .toContain("远端是否收到仍无法确认；应用 Agent 正由后台补偿核验");
    expect(formatAgentConfigError(waitForRecovery, "提交失败")).toContain("trace_wait");
  });

  it("explains that a pre-push failure keeps the local commit", () => {
    const error = new BackendApiError(503, {
      success: false,
      code: "GIT_UNAVAILABLE",
      message: "Git 远端认证失败",
      traceId: "trace_pre_push",
      retryable: true,
      details: {
        gitFailureHint: "请检查 SSH key 权限。",
        localCommitRetained: true,
        remoteCommitState: "NOT_ATTEMPTED",
        publishRecoveryAction: "RETRY_NOW"
      }
    });

    expect(formatAgentConfigError(error, "提交失败"))
      .toContain("远端推送尚未开始；修复认证、权限、分支或网络问题后可直接点击“重新推送”");
  });
});
