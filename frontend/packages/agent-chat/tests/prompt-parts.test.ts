import { describe, expect, it } from "vitest";
import {
  buildComposerPromptParts,
  fileToPromptAttachment,
  workspaceFileToPromptAttachment
} from "../src/prompt-parts";

describe("prompt part attachments", () => {
  it("reads text files as inline file prompt parts", async () => {
    const file = new File(["hello phase 11"], "notes.txt", { type: "text/plain", lastModified: 1 });

    const attachment = await fileToPromptAttachment(file);

    expect(attachment).toMatchObject({
      id: "notes.txt:14:1",
      name: "notes.txt",
      mimeType: "text/plain",
      size: 14,
      part: {
        type: "file",
        name: "notes.txt",
        mimeType: "text/plain",
        content: "hello phase 11"
      }
    });
    expect(attachment.part).not.toHaveProperty("url");
  });

  it("reads images as data-url file prompt parts", async () => {
    const file = new File([new Uint8Array([137, 80, 78, 71])], "screen.png", { type: "image/png", lastModified: 2 });

    const attachment = await fileToPromptAttachment(file);

    expect(attachment).toMatchObject({
      id: "screen.png:4:2",
      name: "screen.png",
      mimeType: "image/png",
      size: 4,
      part: {
        type: "file",
        name: "screen.png",
        mimeType: "image/png"
      }
    });
    expect(attachment.part.url).toBe("data:image/png;base64,iVBORw==");
    expect(attachment.part).not.toHaveProperty("content");
  });

  it("combines trimmed text with attachment parts", async () => {
    const attachment = await fileToPromptAttachment(new File(["source"], "case.md", { type: "text/markdown" }));

    expect(buildComposerPromptParts("  run tests  ", [attachment])).toEqual([
      { type: "text", text: "run tests" },
      attachment.part
    ]);
  });

  it("keeps large workspace files as path-only prompt parts", () => {
    const file = new File(
      [new Uint8Array(602 * 1024)],
      "cases.xlsx",
      {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        lastModified: 3
      }
    );

    const attachment = workspaceFileToPromptAttachment(
      file,
      ".testagent/attachments/req_123-cases.xlsx"
    );

    expect(attachment).toEqual({
      id: "workspace:.testagent/attachments/req_123-cases.xlsx",
      name: "cases.xlsx",
      mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      size: 602 * 1024,
      part: {
        type: "file",
        path: ".testagent/attachments/req_123-cases.xlsx",
        name: "cases.xlsx",
        mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        source: { contextType: "workspace_attachment" }
      }
    });
    expect(attachment.part).not.toHaveProperty("content");
    expect(attachment.part).not.toHaveProperty("url");
  });
});
