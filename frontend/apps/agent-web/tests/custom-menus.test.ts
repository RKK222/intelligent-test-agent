// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import {
  createCustomMenuId,
  loadCustomMenus,
  normalizeCustomMenuUrl,
  saveCustomMenus,
  type CustomMenuStorage
} from "../src/components/custom-menus";

function memoryStorage(): CustomMenuStorage {
  const values = new Map<string, string>();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value)
  };
}

describe("custom menus", () => {
  it("accepts HTTP(S) and same-origin root paths while rejecting unsafe URL forms", () => {
    expect(normalizeCustomMenuUrl("/quality/dashboard?range=7d", "https://agent.example.com"))
      .toBe("/quality/dashboard?range=7d");
    expect(normalizeCustomMenuUrl("https://tools.example.com/report", "https://agent.example.com"))
      .toBe("https://tools.example.com/report");
    expect(() => normalizeCustomMenuUrl("javascript:alert(1)", "https://agent.example.com"))
      .toThrow("URL 需以 http://、https:// 或 / 开头");
    expect(() => normalizeCustomMenuUrl("//evil.example.com", "https://agent.example.com"))
      .toThrow("URL 不能使用省略协议的写法");
    expect(() => normalizeCustomMenuUrl("https://user:secret@tools.example.com", "https://agent.example.com"))
      .toThrow("URL 不能包含账号或密码");
  });

  it("stores a sanitized per-user menu directory and ignores malformed entries", () => {
    const storage = memoryStorage();
    const item = {
      id: createCustomMenuId(1_700_000_000_000, 0.25),
      name: "质量看板",
      icon: "chart" as const,
      url: "/quality/dashboard"
    };
    expect(saveCustomMenus(storage, "usr_1", [item])).toBe(true);
    expect(loadCustomMenus(storage, "usr_1", "https://agent.example.com")).toEqual([item]);
    expect(loadCustomMenus(storage, "usr_2", "https://agent.example.com")).toEqual([]);
  });
});
