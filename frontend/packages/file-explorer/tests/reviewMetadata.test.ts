import { describe, expect, it } from "vitest";
import type { TeamReviewEntry } from "@test-agent/shared-types";
import { formatTeamReviewMetadata } from "../src/reviewMetadata";

describe("review provenance", () => {
  const entry: TeamReviewEntry = { path: "spec/a.md", name: "a.md", directory: false, size: 1, latestUncertain: false, alternatives: [],
    selected: { source: { userId: "u", username: "工作区所有者", personalWorkspaceId: "p", workspaceId: "w", linuxServerId: "s" },
      file: { path: "spec/a.md", name: "a.md", directory: false, size: 1, contentVersion: "sha", author: "真正作者", changedAt: "2026-09-01T00:00:00Z", timeType: "GIT_COMMIT", changeType: "COMMITTED", deleted: false } } };
  it("shows the Git author, not workspace owner", () => {
    expect(formatTeamReviewMetadata(entry)).toContain("真正作者");
    expect(formatTeamReviewMetadata(entry)).not.toContain("工作区所有者");
  });
  it("labels file time and uncertainty honestly", () => {
    const dirty = { ...entry, selected: { ...entry.selected, file: { ...entry.selected.file, author: null, changedAt: null, fileTime: "2026-09-01T00:00:00Z", timeType: "FILE_TIME" as const } } };
    expect(formatTeamReviewMetadata(dirty)).toContain("来源 工作区所有者 · 文件时间");
    expect(formatTeamReviewMetadata({ ...entry, latestUncertain: true })).toBe("最新待核验 · 请选择成员");
  });
});
