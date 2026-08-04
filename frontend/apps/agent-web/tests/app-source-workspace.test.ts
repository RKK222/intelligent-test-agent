import { describe, expect, it } from "vitest";
import { BackendApiError } from "@test-agent/backend-api";
import {
  appSourceIntentAuthorityMatches,
  appSourcePurposeUpdateAllowed,
  appSourceRecoveryFailureInvalidatesRecent,
  appSourceWorkspaceCapabilities,
  appSourceProgressBelongsToObservation,
  appSourceTreeAuthorityMatches,
  claimAppSourceTerminalOperation,
  diffFileCanWrite,
  ordinaryWorkspaceCanWrite,
  personalWorkspaceRuntimeContext,
  sourceContextFromOpen
} from "../src/components/app-source-workspace";
import agentWorkbenchSource from "../src/components/AgentWorkbench.vue?raw";
import diffViewerSource from "../../../packages/diff-viewer/src/DiffViewer.vue?raw";

describe("app source workspace state", () => {
  it("keeps ordinary workspace capabilities while blocking Git and application Agent publication", () => {
    expect(appSourceWorkspaceCapabilities("APP_SOURCE")).toEqual({
      canWriteWorkspaceFiles: true,
      canUseSessionsAndRuns: true,
      canUseTerminal: true,
      canUseGitPublication: false,
      canPublishApplicationAgentConfig: false,
      canSelectApplicationVersion: false
    });
  });

  it("stores only logical source identity and never accepts a physical path", () => {
    const context = sourceContextFromOpen({
      appId: "app-demo",
      repositoryId: "repo-code",
      generation: 8,
      purpose: "TEAM",
      workspaceId: "wrk-source",
      linuxServerId: "linux-a",
      expiresAt: "2026-07-30T00:00:00Z"
    });

    expect(context).toEqual({
      appId: "app-demo",
      repositoryId: "repo-code",
      generation: 8,
      purpose: "TEAM",
      workspaceId: "wrk-source",
      linuxServerId: "linux-a",
      expiresAt: "2026-07-30T00:00:00Z"
    });
    expect(context).not.toHaveProperty("rootPath");
  });

  it("derives ordinary file writes from explicit workspace kind instead of personal worktree identity", () => {
    expect(ordinaryWorkspaceCanWrite("APP_SOURCE", undefined, "wrk-source")).toBe(true);
    expect(ordinaryWorkspaceCanWrite("MANAGED", "pws-personal", "wrk-personal")).toBe(true);
    expect(ordinaryWorkspaceCanWrite("MANAGED", undefined, "wrk-feature-readonly")).toBe(false);
    expect(ordinaryWorkspaceCanWrite("APP_SOURCE", undefined, undefined)).toBe(false);
  });

  it("restores personal worktree identity only from an exact runtime workspace match", () => {
    const personalWorkspace = {
      personalWorkspaceId: "psw-history",
      versionId: "awv-history",
      appId: "app-history",
      applicationWorkspaceId: "awp-history",
      workspaceName: "default",
      branch: "feature_history_usr_admin_default",
      repoRootPath: "/mock/history",
      workspaceRootPath: "/mock/history",
      runtimeWorkspace: {
        workspaceId: "wrk-history-personal",
        name: "history personal",
        rootPath: "/mock/history",
        status: "ACTIVE",
        linuxServerId: "linux-a",
        createdAt: "2026-08-04T00:00:00Z",
        updatedAt: "2026-08-04T00:00:00Z"
      },
      baseCommit: "commit-history",
      status: "ACTIVE",
      createdAt: "2026-08-04T00:00:00Z",
      updatedAt: "2026-08-04T00:00:00Z"
    };

    expect(personalWorkspaceRuntimeContext("wrk-history-personal", [personalWorkspace])).toEqual({
      personalWorkspaceId: "psw-history",
      personalWorkspaceBranch: "feature_history_usr_admin_default"
    });
    expect(personalWorkspaceRuntimeContext("wrk-shared-feature", [personalWorkspace])).toBeUndefined();
    expect(personalWorkspaceRuntimeContext(undefined, [personalWorkspace])).toBeUndefined();
  });

  it("double-gates Diff saves by workspace kind, file scope, and managed roles", () => {
    const source = {
      workspaceKind: "APP_SOURCE" as const,
      ordinaryWorkspaceWritable: true,
      isSuperAdmin: true,
      isAppAdmin: true
    };
    expect(diffFileCanWrite({ ...source, agentScope: null })).toBe(true);
    expect(diffFileCanWrite({ ...source, agentScope: "PUBLIC" })).toBe(false);
    expect(diffFileCanWrite({ ...source, agentScope: "WORKSPACE" })).toBe(false);

    const managed = {
      workspaceKind: "MANAGED" as const,
      ordinaryWorkspaceWritable: false
    };
    expect(diffFileCanWrite({ ...managed, agentScope: "PUBLIC", isSuperAdmin: true, isAppAdmin: true })).toBe(true);
    expect(diffFileCanWrite({ ...managed, agentScope: "PUBLIC", isSuperAdmin: false, isAppAdmin: true })).toBe(false);
    expect(diffFileCanWrite({ ...managed, agentScope: "WORKSPACE", isSuperAdmin: false, isAppAdmin: true })).toBe(true);
    expect(diffFileCanWrite({ ...managed, agentScope: "WORKSPACE", isSuperAdmin: false, isAppAdmin: false })).toBe(false);

    expect(agentWorkbenchSource).toContain(':writable="canSaveSelectedDiffFile"');
    expect(agentWorkbenchSource.match(/canSaveDiffFile\(path\)/g)).toHaveLength(2);
    expect(diffViewerSource).toContain("if (!props.writable || !isDirty.value || !selected.value) return;");
    expect(diffViewerSource).toContain("readOnly: !isVcsOrAgent || !writable");
  });

  it("uses the shared ordinary-write guard in every structural mutation handler", () => {
    const guardedHandlers = [
      "handleCreateEntry",
      "handleCopyEntry",
      "handleMoveEntry",
      "handleUploadFiles",
      "handleChatAttachmentUpload",
      "handleUndoWorkspaceFileOperation",
      "handleRenameEntry",
      "handleDeleteEntry"
    ];

    for (const handler of guardedHandlers) {
      const start = agentWorkbenchSource.indexOf(`function ${handler}`);
      expect(start, `${handler} should exist`).toBeGreaterThan(-1);
      expect(agentWorkbenchSource.slice(start, start + 700), `${handler} should share the guard`)
        .toContain("canWriteSelectedWorkspace.value");
    }
    expect(agentWorkbenchSource).toContain(
      "api.readFile(workspaceId, path, !canWriteSelectedWorkspace.value)"
    );
    expect(agentWorkbenchSource).toContain(':can-write="canWriteSelectedWorkspace"');
  });

  it("rejects stale branch and repository tree responses with one shared authority token", () => {
    const mainAuthority = { token: 7, appId: "app-demo", repositoryId: "repo-code", branch: "main" };

    expect(appSourceTreeAuthorityMatches(mainAuthority, mainAuthority)).toBe(true);
    expect(appSourceTreeAuthorityMatches(mainAuthority, { ...mainAuthority, token: 8, branch: "release" })).toBe(false);
    expect(appSourceTreeAuthorityMatches(mainAuthority, { ...mainAuthority, token: 8, repositoryId: "repo-docs" })).toBe(false);
    expect(agentWorkbenchSource).toContain("invalidateAppSourceTreeAuthority()");
    expect(agentWorkbenchSource).toContain("appSourceTreeAuthorityMatches(requestAuthority, appSourceTreeAuthority)");
  });

  it("rejects late progress after dialog/repository generation changes and claims a terminal result once", () => {
    const authority = {
      token: 12,
      operationId: "aso-12",
      appId: "app-demo",
      repositoryId: "repo-code",
      repositoryGeneration: 5,
      targetGeneration: 6
    };
    const current = {
      dialogOpen: true,
      appId: "app-demo",
      repositoryId: "repo-code",
      repositoryGeneration: 5
    };
    const event = {
      operationId: "aso-12",
      appId: "app-demo",
      repositoryId: "repo-code",
      targetGeneration: 6
    };

    expect(appSourceProgressBelongsToObservation(authority, current, event)).toBe(true);
    expect(appSourceProgressBelongsToObservation(authority, { ...current, dialogOpen: false }, event)).toBe(false);
    expect(appSourceProgressBelongsToObservation(authority, { ...current, repositoryId: "repo-docs" }, event)).toBe(false);
    expect(appSourceProgressBelongsToObservation(authority, { ...current, repositoryGeneration: 6 }, event)).toBe(false);
    expect(appSourceProgressBelongsToObservation(authority, current, { ...event, operationId: "aso-old" })).toBe(false);

    const handled = new Set<string>();
    expect(claimAppSourceTerminalOperation(handled, "aso-12")).toBe(true);
    expect(claimAppSourceTerminalOperation(handled, "aso-12")).toBe(false);
  });

  it("allows TEAM promotion and update while rejecting only TEAM to PERSONAL downgrade", () => {
    expect(appSourcePurposeUpdateAllowed({ generation: 5, purpose: "TEAM" }, "PERSONAL")).toBe(false);
    expect(appSourcePurposeUpdateAllowed({ generation: 5, purpose: "TEAM" }, "TEAM")).toBe(true);
    expect(appSourcePurposeUpdateAllowed({ generation: 5, purpose: "PERSONAL" }, "TEAM")).toBe(true);
    expect(appSourcePurposeUpdateAllowed({ generation: null, purpose: null }, "PERSONAL")).toBe(true);
  });

  it("matches source intent only when token, app, repository, generation, and workspace kind stay current", () => {
    const authority = {
      token: 9,
      appId: "app-a",
      repositoryId: "repo-a",
      generation: 3,
      workspaceKind: "MANAGED" as const
    };

    expect(appSourceIntentAuthorityMatches(authority, authority)).toBe(true);
    expect(appSourceIntentAuthorityMatches(authority, { ...authority, token: 10 })).toBe(false);
    expect(appSourceIntentAuthorityMatches(authority, { ...authority, appId: "app-b" })).toBe(false);
    expect(appSourceIntentAuthorityMatches(authority, { ...authority, repositoryId: "repo-b" })).toBe(false);
    expect(appSourceIntentAuthorityMatches(authority, { ...authority, generation: 4 })).toBe(false);
    expect(appSourceIntentAuthorityMatches(authority, { ...authority, workspaceKind: "APP_SOURCE" })).toBe(false);
  });

  it("invalidates recent only from the real BackendApiError code contract", () => {
    const apiError = (status: number, code: string) => new BackendApiError(status, {
      success: false,
      code,
      message: code,
      traceId: `trace-${code.toLowerCase()}`
    });

    expect(appSourceRecoveryFailureInvalidatesRecent(apiError(403, "FORBIDDEN"))).toBe(true);
    expect(appSourceRecoveryFailureInvalidatesRecent(apiError(404, "NOT_FOUND"))).toBe(true);
    expect(appSourceRecoveryFailureInvalidatesRecent(apiError(409, "CONFLICT"))).toBe(true);
    expect(appSourceRecoveryFailureInvalidatesRecent(apiError(503, "INTERNAL_ERROR"))).toBe(false);
    expect(appSourceRecoveryFailureInvalidatesRecent(new Error("FORBIDDEN"))).toBe(false);
  });
});
