import { describe, expect, it } from "vitest";
import { resolveSessionListDrawerPlacement } from "../src/components/session-list-drawer";

describe("session list drawer placement", () => {
  it("anchors the drawer immediately left of the conversation panel on desktop", () => {
    const placement = resolveSessionListDrawerPlacement(
      { top: 40, left: 900, width: 450, height: 720 },
      { width: 1440, height: 900 }
    );

    expect(placement).toEqual({
      mode: "left",
      top: 40,
      left: 540,
      width: 360,
      height: 720
    });
  });

  it("keeps the drawer inside the viewport when the left side is too narrow", () => {
    const placement = resolveSessionListDrawerPlacement(
      { top: 40, left: 220, width: 450, height: 720 },
      { width: 320, height: 844 }
    );

    expect(placement).toEqual({
      mode: "overlay",
      top: 40,
      left: 8,
      width: 304,
      height: 720
    });
  });

  it("uses the left placement at the minimum desktop threshold", () => {
    const placement = resolveSessionListDrawerPlacement(
      { top: 24, left: 288, width: 420, height: 600 },
      { width: 1024, height: 768 }
    );

    expect(placement).toMatchObject({ mode: "left", left: 8, width: 280 });
  });

  it("clips the drawer vertically to the viewport margins", () => {
    const placement = resolveSessionListDrawerPlacement(
      { top: -20, left: 900, width: 450, height: 1_000 },
      { width: 1_440, height: 800 }
    );

    expect(placement).toEqual({
      mode: "left",
      top: 8,
      left: 540,
      width: 360,
      height: 784
    });
  });
});
