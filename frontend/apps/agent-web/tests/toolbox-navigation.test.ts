import { describe, expect, it } from "vitest";
import {
  routeCenterTransition,
  transitionImmersivePanels,
  type ImmersivePanelSnapshot
} from "../src/components/toolbox-navigation";

describe("toolbox navigation", () => {
  it("opens a deep link and restores the previous center mode on browser back", () => {
    const entered = routeCenterTransition(true, "system", "editor");
    expect(entered).toEqual({ mode: "toolbox", beforeToolbox: "system" });

    const left = routeCenterTransition(false, entered.mode, entered.beforeToolbox);
    expect(left).toEqual({ mode: "system", beforeToolbox: "system" });

    const forwarded = routeCenterTransition(true, left.mode, left.beforeToolbox);
    expect(forwarded.mode).toBe("toolbox");
  });

  it("uses the named toolbox route so a trailing slash still opens immersively", () => {
    expect(routeCenterTransition(true, "editor", "editor").mode).toBe("toolbox");
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

    const switched = transitionImmersivePanels(entered, "hub", "toolbox");
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
