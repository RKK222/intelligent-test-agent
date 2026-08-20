import { describe, expect, it } from "vitest";
import {
  closeWorkspacePageTabs,
  defaultSystemMenuKey,
  moveWorkspacePageTab,
  openWorkspacePageTab,
  parseWorkspacePageRoute,
  restoreWorkspacePageTabs,
  serializeWorkspacePageTabs,
  workspacePageRoute,
  type WorkspacePageTabsState
} from "../src/components/workspace-page-tabs";

const emptyState = (): WorkspacePageTabsState => ({
  openIds: [],
  activeId: null,
  lastSystemId: null
});

describe("workspace page tabs", () => {
  it("opens each page once and remembers the latest system page", () => {
    const toolbox = openWorkspacePageTab(emptyState(), "toolbox");
    const runtime = openWorkspacePageTab(toolbox, "system:runtime");
    const duplicate = openWorkspacePageTab(runtime, "toolbox");

    expect(duplicate).toEqual({
      openIds: ["toolbox", "system:runtime"],
      activeId: "toolbox",
      lastSystemId: "system:runtime"
    });
  });

  it("moves a tab to the requested insertion index without changing the active page", () => {
    const state: WorkspacePageTabsState = {
      openIds: ["toolbox", "memories", "hub", "system:runtime"],
      activeId: "memories",
      lastSystemId: "system:runtime"
    };

    expect(moveWorkspacePageTab(state, "hub", 0)).toEqual({
      ...state,
      openIds: ["hub", "toolbox", "memories", "system:runtime"]
    });
    expect(moveWorkspacePageTab(state, "toolbox", 3).openIds).toEqual([
      "memories",
      "hub",
      "toolbox",
      "system:runtime"
    ]);
  });

  it("closes the active page by preferring its right neighbour and falls back to workbench", () => {
    const state: WorkspacePageTabsState = {
      openIds: ["toolbox", "memories", "hub"],
      activeId: "memories",
      lastSystemId: null
    };

    expect(closeWorkspacePageTabs(state, "memories", "current")).toEqual({
      state: {
        openIds: ["toolbox", "hub"],
        activeId: "hub",
        lastSystemId: null
      },
      navigateTo: "hub"
    });

    expect(closeWorkspacePageTabs({ ...state, openIds: ["memories"], activeId: "memories" }, "memories", "all"))
      .toEqual({
        state: { openIds: [], activeId: null, lastSystemId: null },
        navigateTo: "workbench"
      });
  });

  it("supports closing other, left and right tabs around the context target", () => {
    const state: WorkspacePageTabsState = {
      openIds: ["toolbox", "memories", "hub", "system:runtime"],
      activeId: "toolbox",
      lastSystemId: "system:runtime"
    };

    expect(closeWorkspacePageTabs(state, "hub", "others").state).toEqual({
      openIds: ["hub"],
      activeId: "hub",
      lastSystemId: "system:runtime"
    });
    expect(closeWorkspacePageTabs(state, "hub", "left").state).toEqual({
      openIds: ["hub", "system:runtime"],
      activeId: "hub",
      lastSystemId: "system:runtime"
    });
    expect(closeWorkspacePageTabs({ ...state, activeId: "system:runtime" }, "memories", "right").state).toEqual({
      openIds: ["toolbox", "memories"],
      activeId: "memories",
      lastSystemId: "system:runtime"
    });
  });

  it("restores only persistent pages that remain available to the current role", () => {
    const state: WorkspacePageTabsState = {
      openIds: ["toolbox", "system:runtime", "system:support", "system:config"],
      activeId: "system:support",
      lastSystemId: "system:runtime"
    };
    const serialized = serializeWorkspacePageTabs(state);

    expect(restoreWorkspacePageTabs(serialized, ["SUPER_ADMIN"])).toEqual({
      openIds: ["toolbox", "system:runtime", "system:config"],
      activeId: "toolbox",
      lastSystemId: "system:runtime"
    });
    expect(restoreWorkspacePageTabs(serialized, ["APP_ADMIN"])).toEqual({
      openIds: ["toolbox", "system:config"],
      activeId: "toolbox",
      lastSystemId: "system:config"
    });
  });

  it("restores the latest permitted system page even after its tab was closed", () => {
    const serialized = serializeWorkspacePageTabs({
      openIds: ["toolbox"],
      activeId: "toolbox",
      lastSystemId: "system:runtime"
    });

    expect(restoreWorkspacePageTabs(serialized, ["SUPER_ADMIN"])).toEqual({
      openIds: ["toolbox"],
      activeId: "toolbox",
      lastSystemId: "system:runtime"
    });
    expect(restoreWorkspacePageTabs(serialized, ["APP_ADMIN"])).toEqual({
      openIds: ["toolbox"],
      activeId: "toolbox",
      lastSystemId: null
    });
  });

  it("maps named routes and role-specific system defaults to page ids", () => {
    expect(parseWorkspacePageRoute("toolbox", undefined, ["USER"])).toEqual({
      id: "toolbox",
      canonicalize: false
    });
    expect(parseWorkspacePageRoute("system", "runtime", ["SUPER_ADMIN"])).toEqual({
      id: "system:runtime",
      canonicalize: false
    });
    expect(parseWorkspacePageRoute("system", "runtime", ["APP_ADMIN"])).toEqual({
      id: "system:config",
      canonicalize: true
    });
    expect(parseWorkspacePageRoute("system", "support", ["SUPER_ADMIN"])).toEqual({
      id: "system:scheduler",
      canonicalize: true
    });
    expect(parseWorkspacePageRoute("system", "support", ["SUPER_ADMIN"], true)).toEqual({
      id: "system:support",
      canonicalize: false
    });
    expect(defaultSystemMenuKey(["APP_ADMIN"])).toBe("config");
  });

  it("keeps the default system route clean and encodes non-default sections", () => {
    expect(workspacePageRoute("system:scheduler", ["SUPER_ADMIN"])).toEqual({ name: "system" });
    expect(workspacePageRoute("system:config", ["APP_ADMIN"])).toEqual({ name: "system" });
    expect(workspacePageRoute("system:apiKeys", ["SUPER_ADMIN"])).toEqual({
      name: "system",
      query: { section: "api-keys" }
    });
    expect(workspacePageRoute("memories", ["USER"])).toEqual({ name: "memories" });
  });
});
