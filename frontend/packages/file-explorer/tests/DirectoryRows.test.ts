import { fireEvent, render, within } from "@testing-library/vue";
import { describe, expect, it } from "vitest";
import DirectoryRows from "../src/DirectoryRows.vue";

describe("DirectoryRows", () => {
  it("only exposes delete action for files", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "directory", path: "src", name: "src" },
            { type: "file", path: "README.md", name: "README.md" }
          ]
        },
        expandedDirectories: new Set<string>()
      }
    });

    const deleteButtons = view.getAllByRole("button", { name: "删除" });
    expect(deleteButtons).toHaveLength(1);

    await fireEvent.click(deleteButtons[0]);
    const dialog = view.getByRole("dialog", { name: "删除文件" });
    expect(dialog.textContent).toContain("README.md");
    expect(dialog.textContent).not.toContain("文件夹");

    await fireEvent.click(within(dialog).getByRole("button", { name: "删除" }));

    expect(view.emitted("deleteEntry")).toEqual([["README.md", "file"]]);
  });
});
