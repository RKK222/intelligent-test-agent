import { describe, expect, it, vi } from "vitest";
import { createSupportAccessShortcut } from "../src/components/support-access-shortcut";

function key(key: string, repeat = false) {
  return { key, repeat };
}

describe("support access shortcut", () => {
  it("triggers after three independent Shift presses inside one second", () => {
    let now = 100;
    const shortcut = createSupportAccessShortcut(() => now);

    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    now = 450;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    now = 800;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(true);
    now = 900;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
  });

  it("ignores key-repeat and resets after another key or an expired window", () => {
    let now = 100;
    const shortcut = createSupportAccessShortcut(() => now);

    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    expect(shortcut.handleKeydown(key("Shift", true))).toBe(false);
    now = 300;
    expect(shortcut.handleKeydown(key("KeyA"))).toBe(false);
    now = 400;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    now = 1_500;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    now = 1_700;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    expect(shortcut.handleKeydown(key("Shift"))).toBe(true);
  });

  it("supports an explicit reset when the actor loses SUPER_ADMIN", () => {
    const now = vi.fn().mockReturnValue(100);
    const shortcut = createSupportAccessShortcut(now);

    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    shortcut.reset();
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
  });
});
