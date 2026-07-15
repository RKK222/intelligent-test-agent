import { fireEvent, render, within } from "@testing-library/vue";
import { describe, expect, it } from "vitest";
import DirectoryRows from "../src/DirectoryRows.vue";
import { applicationWorkspaceRestrictionsFixture } from "../../../tests/fixtures/application-workspace-restrictions";

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

  it("double-clicks a file to edit its name and emits the renamed filename", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [{ type: "file", path: "README.md", name: "README.md" }]
        },
        expandedDirectories: new Set<string>()
      }
    });

    await fireEvent.dblClick(view.getByRole("button", { name: /README\.md/ }));
    const input = view.getByRole("textbox", { name: "重命名工作区条目" }) as HTMLInputElement;
    expect(input.value).toBe("README.md");

    await fireEvent.update(input, "详细设计.md");
    await fireEvent.keyDown(input, { key: "Enter" });

    expect(view.emitted("renameEntry")).toEqual([["README.md", "详细设计.md"]]);
  });

  it("double-clicks a directory to edit its name and emits the renamed directory", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [{ type: "directory", path: "tests", name: "tests" }]
        },
        expandedDirectories: new Set<string>()
      }
    });

    await fireEvent.dblClick(view.getByRole("button", { name: /tests/ }));
    const input = view.getByRole("textbox", { name: "重命名工作区条目" }) as HTMLInputElement;
    expect(input.value).toBe("tests");

    await fireEvent.update(input, "回归测试");
    await fireEvent.keyDown(input, { key: "Enter" });

    expect(view.emitted("renameEntry")).toEqual([["tests", "回归测试"]]);
  });

  it("keeps a feature workspace browsable but hides every file mutation entry when readonly", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [...applicationWorkspaceRestrictionsFixture.tree.root] },
        expandedDirectories: new Set<string>(),
        canWrite: false
      }
    });

    expect(view.queryByRole("button", { name: "新建文件或文件夹" })).toBeNull();
    expect(view.queryByRole("button", { name: "删除" })).toBeNull();

    const readme = view.getByRole("button", { name: /README\.md/ });
    await fireEvent.dblClick(readme);
    expect(view.queryByRole("textbox", { name: "重命名工作区条目" })).toBeNull();

    await fireEvent.click(readme);
    expect(view.emitted("openFile")).toEqual([["README.md"]]);
    expect(view.emitted("createEntry")).toBeUndefined();
    expect(view.emitted("deleteEntry")).toBeUndefined();
    expect(view.emitted("renameEntry")).toBeUndefined();
  });
});
