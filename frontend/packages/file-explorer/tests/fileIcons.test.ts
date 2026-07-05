import { describe, expect, it } from "vitest";
import type { FileTreeEntry } from "@test-agent/shared-types";
import { getMaterialFileIconName } from "../src/fileIcons";

describe("getMaterialFileIconName", () => {
  it("returns empty string for directories and Material Icon Theme symbol IDs for files", () => {
    expect(icon({ name: "src", path: "src", type: "directory" })).toBe("");
    expect(icon({ name: "README.md", path: "README.md", type: "file" })).toBe("Readme");
    expect(icon({ name: "package.json", path: "package.json", type: "file" })).toBe("Nodejs");
    expect(icon({ name: "AgentConfigTreeNode.vue", path: "src/AgentConfigTreeNode.vue", type: "file" })).toBe("Vue");
    expect(icon({ name: "hero.png", path: "assets/hero.png", type: "file" })).toBe("Image");
    expect(icon({ name: "artifact.unknown", path: "artifact.unknown", type: "file" })).toBe("Document");
  });
});

function icon(entry: FileTreeEntry) {
  return getMaterialFileIconName(entry);
}
