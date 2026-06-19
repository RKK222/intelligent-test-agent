import { describe, expect, it } from "vitest";
import { languageFromPath } from "../src";

describe("languageFromPath", () => {
  it("maps common test project files to monaco languages", () => {
    expect(languageFromPath("tests/checkout.spec.ts")).toBe("typescript");
    expect(languageFromPath("skills/analyze.py")).toBe("python");
    expect(languageFromPath("playwright.config.json")).toBe("json");
    expect(languageFromPath("README.md")).toBe("markdown");
  });
});
