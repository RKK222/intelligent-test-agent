import { buildPromptParts, promptPreviewTitle } from "@/utils/prompt";

describe("prompt utilities", () => {
  it("builds opencode-compatible prompt parts from text, files, images and references", () => {
    const parts = buildPromptParts({
      text: "Add tests",
      files: [{ path: "src/App.vue", name: "App.vue" }],
      images: [{ name: "screenshot.png", mimeType: "image/png", content: "base64" }],
      agents: [{ agentId: "build", name: "Build" }],
      references: [{ id: "ref_1", label: "Docs", uri: "file:///docs" }]
    });

    expect(parts).toEqual([
      { type: "text", text: "Add tests" },
      { type: "file", path: "src/App.vue", name: "App.vue" },
      { type: "file", name: "screenshot.png", mimeType: "image/png", content: "base64" },
      { type: "agent", agentId: "build", name: "Build" },
      { type: "reference", id: "ref_1", label: "Docs", uri: "file:///docs" }
    ]);
  });

  it("uses the first useful prompt line as draft title", () => {
    expect(promptPreviewTitle("  \nImplement SSE reducer\nand tests")).toBe("Implement SSE reducer");
    expect(promptPreviewTitle("")).toBe("New session");
  });
});
