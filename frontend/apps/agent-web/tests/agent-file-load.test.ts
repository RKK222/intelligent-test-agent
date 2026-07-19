import { describe, expect, it } from "vitest";
import {
  agentFileInfo,
  agentTabPath,
  shouldReloadPersonalRuntimeCatalog
} from "../src/components/agentFileLoad";

describe("Agent 文件标签路由", () => {
  it("按 workspace、worktree 和 server 隔离标签身份并可还原原始路由", () => {
    const featureTabPath = agentTabPath(
      "WORKSPACE",
      "agents/shared:name.md",
      "wrk_feature_a",
      "worktree_a"
    );
    const otherWorkspaceTabPath = agentTabPath(
      "WORKSPACE",
      "agents/shared:name.md",
      "wrk_feature_b",
      "worktree_a"
    );
    const publicTabPath = agentTabPath(
      "PUBLIC",
      "agents/shared:name.md",
      undefined,
      "public_worktree",
      "server_a"
    );

    expect(featureTabPath).not.toBe(otherWorkspaceTabPath);
    expect(agentFileInfo(featureTabPath)).toEqual({
      scope: "WORKSPACE",
      path: "agents/shared:name.md",
      workspaceId: "wrk_feature_a",
      worktreeId: "worktree_a",
      linuxServerId: undefined
    });
    expect(agentFileInfo(publicTabPath)).toEqual({
      scope: "PUBLIC",
      path: "agents/shared:name.md",
      workspaceId: undefined,
      worktreeId: "public_worktree",
      linuxServerId: "server_a"
    });
  });

  it("公共和应用个人 worktree 的目录定义保存后都热加载本人运行态", () => {
    expect(shouldReloadPersonalRuntimeCatalog("WORKSPACE", "agents/reviewer.md")).toBe(true);
    expect(shouldReloadPersonalRuntimeCatalog("WORKSPACE", "skills/test-design/SKILL.md")).toBe(true);
    expect(shouldReloadPersonalRuntimeCatalog("WORKSPACE", "opencode.jsonc")).toBe(true);
    expect(shouldReloadPersonalRuntimeCatalog("WORKSPACE", "skills/test-design/rules/check.md")).toBe(false);
    expect(shouldReloadPersonalRuntimeCatalog("PUBLIC", "agents/reviewer.md")).toBe(true);
    expect(shouldReloadPersonalRuntimeCatalog("PUBLIC", "skills/test-design/SKILL.md")).toBe(true);
    expect(shouldReloadPersonalRuntimeCatalog("PUBLIC", "opencode.jsonc")).toBe(true);
    expect(shouldReloadPersonalRuntimeCatalog("PUBLIC", "skills/test-design/rules/check.md")).toBe(false);
  });
});
