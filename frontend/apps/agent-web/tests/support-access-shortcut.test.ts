import { describe, expect, it, vi } from "vitest";
import { createSupportAccessShortcut } from "../src/components/support-access-shortcut";
import agentWorkbenchSource from "../src/components/AgentWorkbench.vue?raw";

function key(key: string, repeat = false, code?: string) {
  return { key, repeat, code };
}

describe("support access shortcut", () => {
  it("triggers after three independent Shift presses inside two seconds", () => {
    let now = 100;
    const shortcut = createSupportAccessShortcut(() => now);

    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    now = 850;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    now = 1_600;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(true);
    now = 1_700;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
  });

  it("accepts left and right Shift code variants from older or synthetic browsers", () => {
    const shortcut = createSupportAccessShortcut(() => 100);

    expect(shortcut.handleKeydown(key("Unidentified", false, "ShiftLeft"))).toBe(false);
    expect(shortcut.handleKeydown(key("ShiftRight"))).toBe(false);
    expect(shortcut.handleKeydown(key("Shift", false, "ShiftRight"))).toBe(true);
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
    now = 2_500;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    now = 2_700;
    expect(shortcut.handleKeydown(key("Shift"))).toBe(false);
    expect(shortcut.handleKeydown(key("Shift"))).toBe(true);
  });

  it("registers the workbench shortcut in capture phase so stopped child events still reach it", () => {
    expect(agentWorkbenchSource).toContain(
      'window.addEventListener("keydown", onSupportAccessShortcutKeydown, true)'
    );
    expect(agentWorkbenchSource).toContain(
      'window.removeEventListener("keydown", onSupportAccessShortcutKeydown, true)'
    );
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
