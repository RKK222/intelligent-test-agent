import { describe, expect, it } from "vitest";
import {
  buildComposerPromptParts,
  fileToPromptAttachment,
  routeWorkspaceAttachmentsForModel,
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

  it("routes workspace code files through the native OpenCode Read path", () => {
    const attachment = workspaceFileToPromptAttachment(
      new File(["class Demo {}"], "Demo.java", { type: "application/octet-stream" }),
      ".testagent/attachments/sha256_code.java"
    );

    expect(routeWorkspaceAttachmentsForModel([attachment], undefined)[0]?.part).toMatchObject({
      path: ".testagent/attachments/sha256_code.java",
      mimeType: "text/plain",
      source: { contextType: "workspace_attachment", deliveryMode: "native" }
    });
  });

  it("routes media natively only when the selected model supports its modality", () => {
    const image = workspaceFileToPromptAttachment(
      new File([new Uint8Array([1, 2, 3])], "screen.png", { type: "image/png" }),
      ".testagent/attachments/sha256_image.png"
    );
    const supported = routeWorkspaceAttachmentsForModel([image], {
      id: "vision",
      name: "Vision",
      capabilities: { input: { image: true } }
    });
    const unsupported = routeWorkspaceAttachmentsForModel([image], {
      id: "text-only",
      name: "Text only",
      capabilities: { input: { image: false } }
    });

    expect(supported[0]?.part.source).toMatchObject({ deliveryMode: "native" });
    expect(unsupported[0]?.part.source).toEqual({ contextType: "workspace_attachment" });
  });

  it("keeps Office files on the workspace-tool path", () => {
    const excel = workspaceFileToPromptAttachment(
      new File([new Uint8Array([1, 2, 3])], "cases.xls", { type: "application/vnd.ms-excel" }),
      ".testagent/attachments/sha256_excel.xls"
    );

    expect(routeWorkspaceAttachmentsForModel([excel], {
      id: "vision",
      name: "Vision",
      capabilities: { input: { image: true, pdf: true } }
    })[0]?.part.source).toEqual({ contextType: "workspace_attachment" });
  });
});
