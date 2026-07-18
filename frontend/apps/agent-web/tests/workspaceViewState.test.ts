import { describe, expect, it } from "vitest";
import type { WorkspaceViewEntry, WorkspaceViewWarning } from "@test-agent/shared-types";
import {
  migrateWorkspaceViewRefreshTargets,
  ROOT_WORKSPACE_VIEW_TARGET,
  referenceChatPath,
  revalidatedWorkspaceViewRefreshTarget,
  resolveWorkspaceViewLoadTarget,
  workspaceViewContextIsCurrent,
  workspaceViewEntries,
  workspaceViewRefreshTargets
} from "../src/components/workspaceViewState";
import * as workspaceViewState from "../src/components/workspaceViewState";

function directory(id: string, path: string): WorkspaceViewEntry {
  return {
    id,
    path,
    name: path.split("/").at(-1) ?? path,
    type: "directory",
    locator: { kind: "COMPOSITE", path },
    source: "MIXED",
    merged: true,
    collision: false,
    readonly: false,
    workspacePath: path,
    referenceAliases: ["requirements"]
  };
}

describe("workspace view state", () => {
  it("refreshes root plus only expanded nodes that still have stable locator records", () => {
    const docs = directory("mixed:docs", "docs");
    const api = directory("mixed:docs/api", "docs/api");
    const targets = workspaceViewRefreshTargets(
      new Set([docs.id, api.id, "stale:removed"]),
      new Map([[docs.id, docs], [api.id, api]])
    );

    expect(targets).toEqual([ROOT_WORKSPACE_VIEW_TARGET, docs, api]);
  });

  it("serializes reference display paths without leaking a synthetic tab id", () => {
    expect(referenceChatPath("requirements", "/docs/guide.md")).toBe(
      "references/requirements/docs/guide.md"
    );
  });

  it("keeps view-provided reference .opencode entries because backend already hides only workspace config", () => {
    const entry = {
      ...directory("reference:config", ".opencode"),
      locator: { kind: "REFERENCE" as const, path: "", referenceAlias: "assets" },
      source: "REFERENCE" as const,
      readonly: true
    };
    expect(workspaceViewEntries([entry])).toEqual([entry]);
  });

  it("resolves a physical mutation target by workspacePath without matching a reference locator path", () => {
    const reference = {
      ...directory("reference:docs", "docs"),
      locator: { kind: "REFERENCE" as const, path: "docs", referenceAlias: "assets" },
      source: "REFERENCE" as const,
      readonly: true,
      workspacePath: undefined
    };
    const workspace = {
      ...directory("workspace:docs", "docs"),
      locator: { kind: "WORKSPACE" as const, path: "docs" },
      source: "WORKSPACE" as const,
      merged: false,
      referenceAliases: []
    };
    const entries = new Map<string, WorkspaceViewEntry>([
      [reference.id, reference],
      [workspace.id, workspace]
    ]);

    expect(resolveWorkspaceViewLoadTarget("docs", entries)).toEqual(workspace);
  });

  it("rejects a reference context read completed after workspace generation changes", () => {
    expect(workspaceViewContextIsCurrent("wrk_1", 3, "wrk_1", 3)).toBe(true);
    expect(workspaceViewContextIsCurrent("wrk_1", 3, "wrk_2", 4)).toBe(false);
  });

  it("settles workspace reads invalidated by a view refresh without leaving tabs loading", () => {
    const settlements = (workspaceViewState as typeof workspaceViewState & {
      workspaceFileRefreshSettlements?: (
        tabs: Array<{ path: string; loadState?: "loading" | "loaded" | "error"; hasLoadedSnapshot?: boolean }>,
        excluded: (path: string) => boolean
      ) => unknown;
    }).workspaceFileRefreshSettlements?.([
      { path: "docs/new.md", loadState: "loading", hasLoadedSnapshot: false },
      { path: "docs/cached.md", loadState: "loading", hasLoadedSnapshot: true },
      { path: "agent:workspace:agents/test.md", loadState: "loading", hasLoadedSnapshot: false },
      { path: "docs/ready.md", loadState: "loaded", hasLoadedSnapshot: true }
    ], (path) => path.startsWith("agent:"));

    expect(settlements).toEqual([
      {
        path: "docs/new.md",
        patch: {
          loadState: "error",
          loadError: "文件视图已刷新，请重试",
          hasLoadedSnapshot: false
        }
      },
      {
        path: "docs/cached.md",
        patch: {
          loadState: "loaded",
          loadError: undefined,
          hasLoadedSnapshot: true
        }
      }
    ]);
  });

  it("detects same-directory copy conflicts through the stable directory cache id", () => {
    const docs = {
      ...directory("mixed:docs", "docs"),
      locator: { kind: "COMPOSITE" as const, path: "docs" },
      workspacePath: "docs"
    };
    const copiedTarget = (workspaceViewState as typeof workspaceViewState & {
      copiedWorkspaceFileTargetPath?: (
        sourcePath: string,
        targetDirectory: string,
        entriesByDirectory: Record<string, Array<{ name: string }>>,
        entryById: Map<string, WorkspaceViewEntry>
      ) => string;
    }).copiedWorkspaceFileTargetPath?.(
      "docs/guide.md",
      "docs",
      {
        [docs.id]: [
          { name: "guide.md" },
          { name: "guide copy.md" }
        ]
      },
      new Map([[docs.id, docs]])
    );

    expect(copiedTarget).toBe("docs/guide copy 2.md");
  });

  it("revalidates an expanded directory against the locator returned by the refreshed parent", () => {
    const previous = {
      ...directory("workspace:docs", "docs"),
      locator: { kind: "WORKSPACE" as const, path: "docs" },
      source: "WORKSPACE" as const,
      merged: false,
      referenceAliases: []
    };
    const refreshed = directory(previous.id, "docs");
    const target = (workspaceViewState as typeof workspaceViewState & {
      revalidatedWorkspaceViewRefreshTarget?: (
        target: WorkspaceViewEntry,
        entryById: Map<string, WorkspaceViewEntry>
      ) => WorkspaceViewEntry | undefined;
    }).revalidatedWorkspaceViewRefreshTarget?.(previous, new Map([[refreshed.id, refreshed]]));

    expect(target).toEqual(refreshed);
    expect(target?.locator.kind).toBe("COMPOSITE");
  });

  it("migrates expanded workspace paths and resolves new stable ids through each target ancestor", () => {
    const source = {
      ...directory("workspace:src-old", "src"),
      locator: { kind: "WORKSPACE" as const, path: "src" },
      source: "WORKSPACE" as const,
      merged: false,
      referenceAliases: []
    };
    const nested = {
      ...directory("workspace:src-nested-old", "src/nested"),
      locator: { kind: "WORKSPACE" as const, path: "src/nested" },
      source: "WORKSPACE" as const,
      merged: false,
      referenceAliases: []
    };
    const targets = migrateWorkspaceViewRefreshTargets(
      workspaceViewRefreshTargets(
        new Set([source.id, nested.id]),
        new Map([[source.id, source], [nested.id, nested]])
      ),
      "src",
      "archive/src"
    );

    expect(targets.map((target) => target.workspacePath ?? target.id)).toEqual([
      "",
      "archive",
      "archive/src",
      "archive/src/nested"
    ]);

    const archive = {
      ...directory("workspace:archive", "archive"),
      locator: { kind: "WORKSPACE" as const, path: "archive" },
      source: "WORKSPACE" as const,
      merged: false,
      referenceAliases: []
    };
    const movedSource = {
      ...directory("workspace:archive-src-new", "archive/src"),
      locator: { kind: "WORKSPACE" as const, path: "archive/src" },
      source: "WORKSPACE" as const,
      merged: false,
      referenceAliases: []
    };

    expect(revalidatedWorkspaceViewRefreshTarget(targets[1]!, new Map([[archive.id, archive]]))).toEqual(archive);
    expect(revalidatedWorkspaceViewRefreshTarget(targets[2]!, new Map([[movedSource.id, movedSource]]))).toEqual(movedSource);
  });

  it("collects nested warnings and truncation once, then clears a recovered directory", () => {
    type Snapshot = { warnings: WorkspaceViewWarning[]; truncated: boolean };
    const snapshots = new Map<string, Snapshot>([
      ["", { warnings: [{ code: "REFERENCE_UNAVAILABLE", message: "引用副本不可用", alias: "docs" }], truncated: false }],
      ["docs", {
        warnings: [{ code: "REFERENCE_UNAVAILABLE", message: "引用副本不可用", alias: "docs" }],
        truncated: true
      }]
    ]);
    const collect = (workspaceViewState as typeof workspaceViewState & {
      collectWorkspaceViewWarnings?: (snapshots: ReadonlyMap<string, Snapshot>) => WorkspaceViewWarning[];
    }).collectWorkspaceViewWarnings;

    expect(collect?.(snapshots)).toEqual([
      { code: "REFERENCE_UNAVAILABLE", message: "引用副本不可用", alias: "docs" },
      { code: "WORKSPACE_VIEW_TRUNCATED", message: "文件树条目过多，当前结果已截断" }
    ]);

    snapshots.set("docs", { warnings: [], truncated: false });
    snapshots.set("", { warnings: [], truncated: false });
    expect(collect?.(snapshots)).toEqual([]);
  });
});
