import { describe, expect, it } from "vitest";
import { highlightKeyword } from "../src";

describe("highlightKeyword", () => {
  it("returns a single non-matching segment when keyword is empty", () => {
    expect(highlightKeyword("AgentConfig.vue", "")).toEqual([{ text: "AgentConfig.vue", match: false }]);
  });

  it("splits text into matching and non-matching segments", () => {
    expect(highlightKeyword("AgentConfig.vue", "config")).toEqual([
      { text: "Agent", match: false },
      { text: "Config", match: true },
      { text: ".vue", match: false }
    ]);
  });

  it("matches case-insensitively but preserves original casing", () => {
    const segments = highlightKeyword("AgentConfig.vue", "CONFIG");
    expect(segments).toEqual([
      { text: "Agent", match: false },
      { text: "Config", match: true },
      { text: ".vue", match: false }
    ]);
  });

  it("highlights all occurrences of the keyword", () => {
    expect(highlightKeyword("config/config.vue", "config")).toEqual([
      { text: "config", match: true },
      { text: "/", match: false },
      { text: "config", match: true },
      { text: ".vue", match: false }
    ]);
  });

  it("returns a single non-matching segment when keyword is not found", () => {
    expect(highlightKeyword("AgentConfig.vue", "xyz")).toEqual([{ text: "AgentConfig.vue", match: false }]);
  });

  it("handles keyword with leading/trailing whitespace", () => {
    expect(highlightKeyword("AgentConfig.vue", "  config  ")).toEqual([
      { text: "Agent", match: false },
      { text: "Config", match: true },
      { text: ".vue", match: false }
    ]);
  });
});
