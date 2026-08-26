import { describe, expect, it } from "vitest";
import {
  initialImmersivePanels,
  routeCenterTransition,
  routedCenterModeFromRouteName,
  transitionImmersivePanels,
  type ImmersivePanelSnapshot
} from "../src/components/toolbox-navigation";

describe("toolbox navigation", () => {
  it("opens a deep link and restores the previous center mode on browser back", () => {
    const entered = routeCenterTransition("toolbox", "diff", "editor");
    expect(entered).toEqual({ mode: "toolbox", beforeRoute: "diff" });

    const left = routeCenterTransition(null, entered.mode, entered.beforeRoute);
    expect(left).toEqual({ mode: "diff", beforeRoute: "diff" });

    const forwarded = routeCenterTransition("toolbox", left.mode, left.beforeRoute);
    expect(forwarded.mode).toBe("toolbox");
  });

  it("uses the named toolbox route so a trailing slash still opens immersively", () => {
    expect(routeCenterTransition("toolbox", "editor", "editor").mode).toBe("toolbox");
  });

  it("keeps all activity pages as independent routed immersive views", () => {
    const memories = routeCenterTransition("memories", "hub", "editor");
    expect(memories).toEqual({ mode: "memories", beforeRoute: "editor" });
    const toolbox = routeCenterTransition("toolbox", memories.mode, memories.beforeRoute);
    expect(toolbox).toEqual({ mode: "toolbox", beforeRoute: "editor" });
    const system = routeCenterTransition("system", toolbox.mode, toolbox.beforeRoute);
    expect(system).toEqual({ mode: "system", beforeRoute: "editor" });
    const hub = routeCenterTransition("hub", system.mode, system.beforeRoute);
    expect(hub).toEqual({ mode: "hub", beforeRoute: "editor" });
    expect(routeCenterTransition(null, hub.mode, hub.beforeRoute).mode).toBe("editor");
  });

  it("maps only named immersive activity routes to center modes", () => {
    expect(routedCenterModeFromRouteName("toolbox")).toBe("toolbox");
    expect(routedCenterModeFromRouteName("memories")).toBe("memories");
    expect(routedCenterModeFromRouteName("system")).toBe("system");
    expect(routedCenterModeFromRouteName("hub")).toBe("hub");
    expect(routedCenterModeFromRouteName("custom-menu")).toBe("custom");
    expect(routedCenterModeFromRouteName("workbench")).toBeNull();
    expect(routedCenterModeFromRouteName("settings")).toBeNull();
  });

  it("closes every panel in immersive modes and restores the exact snapshot on exit", () => {
    const initial: ImmersivePanelSnapshot = {
      leftOpen: true,
      rightOpen: false,
      bottomOpen: true,
      savedLeftOpen: false,
      savedRightOpen: true,
      savedBottomOpen: false
    };
    const entered = transitionImmersivePanels(initial, "toolbox", "editor");
    expect(entered).toEqual({
      leftOpen: false,
      rightOpen: false,
      bottomOpen: false,
      savedLeftOpen: true,
      savedRightOpen: false,
      savedBottomOpen: true
    });

    const switched = transitionImmersivePanels(entered, "memories", "toolbox");
    expect(switched).toEqual(entered);

    const exited = transitionImmersivePanels(switched, "editor", "hub");
    expect(exited).toEqual({
      ...switched,
      leftOpen: true,
      rightOpen: false,
      bottomOpen: true
    });
  });

  it("starts direct immersive routes with the surrounding workbench panels closed", () => {
    const initial: ImmersivePanelSnapshot = {
      leftOpen: true,
      rightOpen: true,
      bottomOpen: false,
      savedLeftOpen: true,
      savedRightOpen: true,
      savedBottomOpen: false
    };

    expect(initialImmersivePanels(initial, "system")).toEqual({
      leftOpen: false,
      rightOpen: false,
      bottomOpen: false,
      savedLeftOpen: true,
      savedRightOpen: true,
      savedBottomOpen: false
    });
    expect(initialImmersivePanels(initial, null)).toEqual(initial);
  });
});
