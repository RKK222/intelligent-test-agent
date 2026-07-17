import { fireEvent, render, within } from "@testing-library/vue";
import { describe, expect, it } from "vitest";
import DirectoryRows from "../src/DirectoryRows.vue";
import { applicationWorkspaceRestrictionsFixture } from "../../../tests/fixtures/application-workspace-restrictions";

describe("DirectoryRows", () => {
  it("exposes minus delete actions for both files and directories", async () => {
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

    const deleteButtons = view.getAllByRole("button", { name: /^删除 / });
    expect(deleteButtons).toHaveLength(2);

    await fireEvent.click(view.getByRole("button", { name: "删除 README.md" }));
    const dialog = view.getByRole("dialog", { name: "删除文件" });
    expect(dialog.textContent).toContain("README.md");

    await fireEvent.click(within(dialog).getByRole("button", { name: "确认删除" }));

    expect(view.emitted("deleteEntry")).toEqual([["README.md", "file"]]);

    await fireEvent.click(view.getByRole("button", { name: "删除 src" }));
    const directoryDialog = view.getByRole("dialog", { name: "删除文件夹" });
    expect(directoryDialog.textContent).toContain("文件夹及其中的全部内容都会被删除");
    await fireEvent.click(within(directoryDialog).getByRole("button", { name: "确认删除" }));

    expect(view.emitted("deleteEntry")).toEqual([
      ["README.md", "file"],
      ["src", "directory"]
    ]);
  });

  it("opens the shared confirmation dialog when Delete is pressed on a focused row", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [{ type: "directory", path: "docs", name: "docs" }] },
        expandedDirectories: new Set<string>()
      }
    });

    await fireEvent.keyDown(view.getByRole("button", { name: "docs" }), { key: "Delete" });
    const dialog = view.getByRole("dialog", { name: "删除文件夹" });
    await fireEvent.click(within(dialog).getByRole("button", { name: "确认删除" }));

    expect(view.emitted("deleteEntry")).toEqual([["docs", "directory"]]);
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

    await fireEvent.dblClick(view.getByRole("button", { name: "README.md" }));
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

    await fireEvent.dblClick(view.getByRole("button", { name: "tests" }));
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

    expect(view.queryByRole("button", { name: "新建或上传到此目录" })).toBeNull();
    expect(view.queryByRole("button", { name: /^删除 / })).toBeNull();

    const readme = view.getByRole("button", { name: "README.md" });
    await fireEvent.dblClick(readme);
    expect(view.queryByRole("textbox", { name: "重命名工作区条目" })).toBeNull();

    await fireEvent.click(readme);
    expect(view.emitted("openFile")).toEqual([["README.md"]]);
    expect(view.emitted("createEntry")).toBeUndefined();
    expect(view.emitted("deleteEntry")).toBeUndefined();
    expect(view.emitted("renameEntry")).toBeUndefined();
  });

  it("supports Ctrl/Cmd copy, cut and paste for focused files", async () => {
    const props = {
      directory: "",
      entriesByDirectory: {
        "": [
          { type: "directory" as const, path: "docs", name: "docs" },
          { type: "file" as const, path: "README.md", name: "README.md" }
        ]
      },
      expandedDirectories: new Set<string>(),
      clipboardEntry: { path: "README.md", mode: "copy" as const }
    };
    const view = render(DirectoryRows, { props });
    const readme = view.getByRole("button", { name: "README.md" });
    const docs = view.getByRole("button", { name: "docs" });

    await fireEvent.keyDown(readme, { key: "c", ctrlKey: true });
    await fireEvent.keyDown(readme, { key: "x", metaKey: true });
    await fireEvent.keyDown(docs, { key: "v", ctrlKey: true });

    expect(view.emitted("setClipboard")).toEqual([
      ["README.md", "copy"],
      ["README.md", "move"]
    ]);
    expect(view.emitted("pasteEntry")).toEqual([["docs"]]);
  });

  it("moves a dragged file when it is dropped on a directory", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "directory", path: "docs", name: "docs" },
            { type: "file", path: "README.md", name: "README.md" }
          ]
        },
        expandedDirectories: new Set<string>()
      }
    });
    const data = new Map<string, string>();
    const dataTransfer = {
      effectAllowed: "none",
      dropEffect: "none",
      setData: (type: string, value: string) => data.set(type, value),
      getData: (type: string) => data.get(type) ?? ""
    };
    const readme = view.getByRole("button", { name: "README.md" });
    const docs = view.getByRole("button", { name: "docs" });

    await fireEvent.dragStart(readme, { dataTransfer });
    await fireEvent.dragOver(docs, { dataTransfer });
    await fireEvent.drop(docs, { dataTransfer });

    expect(view.emitted("moveEntry")).toEqual([["README.md", "docs"]]);
  });

  it("offers upload from the selected directory plus menu and shows the target path", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [{ type: "directory", path: "docs", name: "docs" }] },
        expandedDirectories: new Set<string>()
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "新建或上传到此目录" }));
    const dialog = view.getByRole("dialog", { name: "新建或上传文件" });
    expect(dialog.textContent).toContain("目标目录");
    expect(dialog.textContent).toContain("docs");
    await fireEvent.click(within(dialog).getByRole("button", { name: "上传" }));
    await fireEvent.click(within(dialog).getByRole("button", { name: "选择文件" }));

    expect(view.emitted("requestUpload")).toEqual([["docs"]]);
  });

  it("emits undo for Ctrl/Cmd+Z when the current personal worktree has history", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [{ type: "file", path: "README.md", name: "README.md" }] },
        expandedDirectories: new Set<string>(),
        canUndo: true
      }
    });

    await fireEvent.keyDown(view.getByRole("button", { name: "README.md" }), { key: "z", metaKey: true });

    expect(view.emitted("undoEntry")).toEqual([[]]);
  });

  it("clears a directory drop highlight when the root drag reset token changes", async () => {
    const props = {
      directory: "",
      entriesByDirectory: { "": [{ type: "directory" as const, path: "docs", name: "docs" }] },
      expandedDirectories: new Set<string>(),
      dragResetToken: 0
    };
    const view = render(DirectoryRows, { props });
    const docs = view.getByRole("button", { name: "docs" });

    await fireEvent.dragOver(docs, { dataTransfer: { dropEffect: "none" } });
    expect(docs.classList.contains("is-drop-target")).toBe(true);

    await view.rerender({ ...props, dragResetToken: 1 });
    expect(docs.classList.contains("is-drop-target")).toBe(false);
  });
});
