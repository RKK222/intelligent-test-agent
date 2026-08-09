import { describe, expect, it } from "vitest";
import {
  routeCenterTransition,
  transitionImmersivePanels,
  type ImmersivePanelSnapshot
} from "../src/components/toolbox-navigation";

describe("toolbox navigation", () => {
  it("opens a deep link and restores the previous center mode on browser back", () => {
    const entered = routeCenterTransition("toolbox", "system", "editor");
    expect(entered).toEqual({ mode: "toolbox", beforeRoute: "system" });

    const left = routeCenterTransition(null, entered.mode, entered.beforeRoute);
    expect(left).toEqual({ mode: "system", beforeRoute: "system" });

    const forwarded = routeCenterTransition("toolbox", left.mode, left.beforeRoute);
    expect(forwarded.mode).toBe("toolbox");
  });

  it("uses the named toolbox route so a trailing slash still opens immersively", () => {
    expect(routeCenterTransition("toolbox", "editor", "editor").mode).toBe("toolbox");
  });

  it("keeps toolbox and memories as independent routed immersive views", () => {
    const memories = routeCenterTransition("memories", "hub", "editor");
    expect(memories).toEqual({ mode: "memories", beforeRoute: "hub" });
    const toolbox = routeCenterTransition("toolbox", memories.mode, memories.beforeRoute);
    expect(toolbox).toEqual({ mode: "toolbox", beforeRoute: "hub" });
    expect(routeCenterTransition(null, toolbox.mode, toolbox.beforeRoute).mode).toBe("hub");
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
});
