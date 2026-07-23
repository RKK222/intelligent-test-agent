import { fireEvent, render, within } from "@testing-library/vue";
import { describe, expect, it } from "vitest";
import DirectoryRows from "../src/DirectoryRows.vue";
import { applicationWorkspaceRestrictionsFixture } from "../../../tests/fixtures/application-workspace-restrictions";

describe("DirectoryRows", () => {
  it("selects duplicate workspace view rows only by stable node id", () => {
    const entries = [
      {
        id: "workspace:guide",
        type: "file" as const,
        path: "docs/guide.md",
        name: "guide.md",
        locator: { kind: "WORKSPACE" as const, path: "docs/guide.md" },
        source: "WORKSPACE" as const,
        merged: false,
        collision: false,
        readonly: false,
        referenceAliases: []
      },
      {
        id: "reference:requirements:guide",
        type: "file" as const,
        path: "docs/guide.md",
        name: "guide.md",
        locator: { kind: "REFERENCE" as const, path: "guide.md", referenceAlias: "docs-requirements" },
        source: "REFERENCE" as const,
        merged: true,
        collision: false,
        readonly: true,
        referenceAliases: ["docs-requirements"]
      }
    ];
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": entries },
        expandedDirectories: new Set<string>(),
        activePath: "docs/guide.md"
      }
    });

    const rows = view.getAllByRole("button", { name: "guide.md" });
    expect(rows[0]?.classList.contains("is-active")).toBe(false);
    expect(rows[1]?.classList.contains("is-active")).toBe(false);
  });

  it("renders duplicate logical names by stable id with semantic reference source details", async () => {
    const entries = [
      {
        id: "workspace:guide",
        type: "file" as const,
        path: "docs/guide.md",
        name: "guide.md",
        locator: { kind: "WORKSPACE" as const, path: "docs/guide.md" },
        source: "WORKSPACE" as const,
        merged: false,
        collision: false,
        readonly: false,
        referenceAliases: []
      },
      {
        id: "reference:requirements:guide",
        type: "file" as const,
        path: "docs/guide.md",
        name: "guide.md",
        locator: { kind: "REFERENCE" as const, path: "docs/guide.md", referenceAlias: "requirements" },
        source: "REFERENCE" as const,
        merged: true,
        collision: false,
        readonly: true,
        referenceAliases: ["requirements"]
      },
      {
        id: "reference:legacy:guide",
        type: "file" as const,
        path: "docs/guide.md",
        name: "guide.md",
        locator: { kind: "REFERENCE" as const, path: "docs/guide.md", referenceAlias: "legacy" },
        source: "REFERENCE" as const,
        merged: true,
        collision: true,
        readonly: true,
        referenceAliases: ["legacy"]
      },
      {
        id: "reference:plain",
        type: "file" as const,
        path: "plain.md",
        name: "plain.md",
        locator: { kind: "REFERENCE" as const, path: "plain.md", referenceAlias: "plain" },
        source: "REFERENCE" as const,
        merged: false,
        collision: false,
        readonly: true,
        referenceAliases: ["plain"]
      }
    ];
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": entries },
        expandedDirectories: new Set<string>()
      }
    });

    const duplicateRows = view.getAllByRole("button", { name: "guide.md" });
    expect(duplicateRows).toHaveLength(3);
    expect(duplicateRows[1]?.classList.contains("is-reference-merged")).toBe(true);
    expect(duplicateRows[1]?.title).toContain("引用来源：requirements");
    expect(duplicateRows[2]?.classList.contains("is-reference-collision")).toBe(true);
    expect(duplicateRows[2]?.title).toContain("引用冲突：legacy");
    expect(view.getByRole("button", { name: /plain\.md/ }).classList.contains("is-reference-merged")).toBe(false);

    await fireEvent.click(duplicateRows[1]!);
    expect(view.emitted("openViewFile")).toEqual([[entries[1]]]);
  });

  it("keeps mixed directories ordinary and allows child writes only through workspacePath", async () => {
    const mixed = {
      id: "mixed:docs",
      type: "directory" as const,
      path: "docs",
      name: "docs",
      locator: { kind: "COMPOSITE" as const, path: "docs" },
      source: "MIXED" as const,
      merged: true,
      collision: false,
      readonly: false,
      workspacePath: "docs",
      referenceAliases: ["requirements"]
    };
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [mixed] },
        expandedDirectories: new Set<string>(),
        clipboardEntry: { path: "README.md", mode: "copy" }
      }
    });
    const row = view.getByRole("button", { name: "docs" });

    expect(row.classList.contains("is-reference-merged")).toBe(false);
    expect(view.queryByRole("button", { name: "删除 docs" })).toBeNull();
    await fireEvent.dblClick(row);
    expect(view.queryByRole("textbox", { name: "重命名工作区条目" })).toBeNull();
    await fireEvent.keyDown(row, { key: "v", ctrlKey: true });
    expect(view.emitted("pasteEntry")).toEqual([["docs"]]);
    await fireEvent.click(view.getByRole("button", { name: "新建或上传到此目录" }));
    expect(view.getByRole("dialog", { name: "新建或上传文件" }).textContent).toContain("docs");
  });

  it("does not render an empty context menu for a mixed directory without available actions", async () => {
    const mixed = {
      id: "mixed:docs",
      type: "directory" as const,
      path: "docs",
      name: "docs",
      locator: { kind: "COMPOSITE" as const, path: "docs" },
      source: "MIXED" as const,
      merged: true,
      collision: false,
      readonly: false,
      workspacePath: "docs",
      referenceAliases: ["requirements"]
    };
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [mixed] },
        expandedDirectories: new Set<string>()
      }
    });

    await fireEvent.contextMenu(view.getByRole("button", { name: "docs" }));

    expect(view.queryByRole("menu")).toBeNull();
  });

  it("blocks mutations and git badges for pure reference nodes", async () => {
    const reference = {
      id: "reference:requirements:guide",
      type: "file" as const,
      path: "docs/guide.md",
      name: "guide.md",
      locator: { kind: "REFERENCE" as const, path: "docs/guide.md", referenceAlias: "requirements" },
      source: "REFERENCE" as const,
      merged: true,
      collision: false,
      readonly: true,
      referenceAliases: ["requirements"]
    };
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [reference] },
        expandedDirectories: new Set<string>(),
        changeStats: { "docs/guide.md": { additions: 2, deletions: 1 } },
        clipboardEntry: { path: "README.md", mode: "copy" }
      }
    });
    const row = view.getByRole("button", { name: "guide.md" });

    expect(row.getAttribute("draggable")).toBe("false");
    expect(view.queryByRole("button", { name: "删除 guide.md" })).toBeNull();
    expect(view.queryByText("+2")).toBeNull();
    await fireEvent.keyDown(row, { key: "Delete" });
    await fireEvent.keyDown(row, { key: "c", ctrlKey: true });
    await fireEvent.keyDown(row, { key: "v", ctrlKey: true });
    await fireEvent.contextMenu(row);
    expect(view.queryByRole("menuitem", { name: /粘贴到此处/ })).toBeNull();
    expect(view.emitted("deleteEntry")).toBeUndefined();
    expect(view.emitted("setClipboard")).toBeUndefined();
    expect(view.emitted("pasteEntry")).toBeUndefined();
  });

  it("blocks keyboard and context-menu undo from a pure reference row", async () => {
    const reference = {
      id: "reference:requirements:guide",
      type: "file" as const,
      path: "docs/guide.md",
      name: "guide.md",
      locator: { kind: "REFERENCE" as const, path: "docs/guide.md", referenceAlias: "requirements" },
      source: "REFERENCE" as const,
      merged: true,
      collision: false,
      readonly: true,
      referenceAliases: ["requirements"]
    };
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [reference] },
        expandedDirectories: new Set<string>(),
        canUndo: true
      }
    });
    const row = view.getByRole("button", { name: "guide.md" });

    await fireEvent.keyDown(row, { key: "z", metaKey: true });
    await fireEvent.contextMenu(row);

    expect(view.emitted("undoEntry")).toBeUndefined();
    expect(view.queryByRole("menuitem", { name: /撤销上一步/ })).toBeNull();
  });

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

  it("renames a file from the context menu and ignores double click", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [{ type: "file", path: "README.md", name: "README.md" }]
        },
        expandedDirectories: new Set<string>()
      }
    });

    const row = view.getByRole("button", { name: "README.md" });
    await fireEvent.dblClick(row);
    expect(view.queryByRole("textbox", { name: "重命名工作区条目" })).toBeNull();
    await fireEvent.contextMenu(row);
    await fireEvent.click(view.getByRole("menuitem", { name: "重命名" }));
    const input = view.getByRole("textbox", { name: "重命名工作区条目" }) as HTMLInputElement;
    expect(input.value).toBe("README.md");

    await fireEvent.update(input, "详细设计.md");
    await fireEvent.keyDown(input, { key: "Enter" });

    expect(view.emitted("renameEntry")).toEqual([["README.md", "详细设计.md"]]);
  });

  it("renames a directory from the context menu", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [{ type: "directory", path: "tests", name: "tests" }]
        },
        expandedDirectories: new Set<string>()
      }
    });

    await fireEvent.contextMenu(view.getByRole("button", { name: "tests" }));
    await fireEvent.click(view.getByRole("menuitem", { name: "重命名" }));
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

  it("selects files with Ctrl/Cmd and exposes multi-file context actions", async () => {
    const selectedEntries = [
      { path: "a.md", type: "file" as const },
      { path: "b.md", type: "file" as const }
    ];
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "file" as const, path: "a.md", name: "a.md" },
            { type: "file" as const, path: "b.md", name: "b.md" }
          ]
        },
        expandedDirectories: new Set<string>(),
        selectedEntries
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "a.md" }), { ctrlKey: true });
    expect(view.emitted("selectionChange")?.[0]).toEqual([[selectedEntries[1]]]);

    await fireEvent.contextMenu(view.getByRole("button", { name: "a.md" }));
    await fireEvent.click(view.getByRole("menuitem", { name: /^复制/ }));
    expect(view.emitted("setClipboardEntries")).toEqual([[selectedEntries, "copy"]]);

    await fireEvent.contextMenu(view.getByRole("button", { name: "a.md" }));
    await fireEvent.click(view.getByRole("menuitem", { name: "删除 2 个条目" }));
    const dialog = view.getByRole("dialog", { name: "删除多个条目" });
    await fireEvent.click(within(dialog).getByRole("button", { name: "确认删除" }));
    expect(view.emitted("deleteEntries")).toEqual([[selectedEntries]]);
  });

  it("moves all selected files when one selected row is dragged", async () => {
    const selectedEntries = [
      { path: "a.md", type: "file" as const },
      { path: "b.md", type: "file" as const }
    ];
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "directory" as const, path: "archive", name: "archive" },
            { type: "file" as const, path: "a.md", name: "a.md" },
            { type: "file" as const, path: "b.md", name: "b.md" }
          ]
        },
        expandedDirectories: new Set<string>(),
        selectedEntries
      }
    });
    const data = new Map<string, string>();
    const dataTransfer = {
      effectAllowed: "none",
      dropEffect: "none",
      setData: (type: string, value: string) => data.set(type, value),
      getData: (type: string) => data.get(type) ?? ""
    };

    await fireEvent.dragStart(view.getByRole("button", { name: "a.md" }), { dataTransfer });
    await fireEvent.dragOver(view.getByRole("button", { name: "archive" }), { dataTransfer });
    await fireEvent.drop(view.getByRole("button", { name: "archive" }), { dataTransfer });

    expect(view.emitted("moveEntries")).toEqual([[['a.md', 'b.md'], "archive"]]);
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

  it("moves a dragged directory and marks its source row while the drag is active", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "directory" as const, path: "archive", name: "archive" },
            { type: "directory" as const, path: "src", name: "src" }
          ]
        },
        expandedDirectories: new Set<string>(),
        dragSourcePath: "src"
      }
    });
    const data = new Map<string, string>();
    const dataTransfer = {
      effectAllowed: "none",
      dropEffect: "none",
      setData: (type: string, value: string) => data.set(type, value),
      getData: (type: string) => data.get(type) ?? ""
    };
    const src = view.getByRole("button", { name: "src" });
    const archive = view.getByRole("button", { name: "archive" });

    expect(src.getAttribute("draggable")).toBe("true");
    expect(src.classList.contains("is-dragging")).toBe(true);
    await fireEvent.dragStart(src, { dataTransfer });
    await fireEvent.dragOver(archive, { dataTransfer });
    await fireEvent.drop(archive, { dataTransfer });
    await fireEvent.dragEnd(src, { dataTransfer });

    expect(view.emitted("moveEntry")).toEqual([["src", "archive"]]);
    expect(view.emitted("dragSourceChange")).toEqual([[["src"]], [undefined]]);
  });

  it("rejects a directory dropped onto itself, its parent, or a descendant without matching sibling prefixes", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "directory" as const, path: "docs", name: "docs" },
            { type: "directory" as const, path: "docs2", name: "docs2" }
          ],
          docs: [{ type: "directory" as const, path: "docs/guides", name: "guides" }]
        },
        expandedDirectories: new Set(["docs"]),
        dragSourcePath: "docs"
      }
    });
    const dataTransfer = { dropEffect: "none", getData: () => "docs" };
    const docs = view.getByRole("button", { name: "docs" });
    const guides = view.getByRole("button", { name: "guides" });
    const docs2 = view.getByRole("button", { name: "docs2" });

    await fireEvent.dragOver(docs, { dataTransfer });
    await fireEvent.drop(docs, { dataTransfer });
    await fireEvent.dragOver(guides, { dataTransfer });
    await fireEvent.drop(guides, { dataTransfer });
    await fireEvent.dragOver(docs2, { dataTransfer });
    await fireEvent.drop(docs2, { dataTransfer });

    expect(docs.classList.contains("is-drop-target")).toBe(false);
    expect(guides.classList.contains("is-drop-target")).toBe(false);
    expect(view.emitted("moveEntry")).toEqual([["docs", "docs2"]]);
  });

  it("blocks pure reference drops from bubbling and uses a mixed directory workspacePath as the move target", async () => {
    const reference = {
      id: "reference:docs",
      type: "directory" as const,
      path: "reference-docs",
      name: "reference-docs",
      locator: { kind: "REFERENCE" as const, path: "reference-docs", referenceAlias: "requirements" },
      source: "REFERENCE" as const,
      merged: true,
      collision: false,
      readonly: true,
      referenceAliases: ["requirements"]
    };
    const mixed = {
      id: "mixed:docs",
      type: "directory" as const,
      path: "references/docs",
      name: "mixed-docs",
      locator: { kind: "COMPOSITE" as const, path: "references/docs" },
      source: "MIXED" as const,
      merged: true,
      collision: false,
      readonly: false,
      workspacePath: "workspace/docs",
      referenceAliases: ["requirements"]
    };
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: { "": [reference, mixed] },
        expandedDirectories: new Set<string>(),
        dragSourcePath: "src/README.md"
      }
    });
    const dataTransfer = { dropEffect: "none", getData: () => "src\\README.md" };
    const referenceRow = view.getByRole("button", { name: "reference-docs" });
    const mixedRow = view.getByRole("button", { name: "mixed-docs" });
    let bubbled = false;
    view.container.addEventListener("drop", () => { bubbled = true; });

    expect(referenceRow.getAttribute("draggable")).toBe("false");
    expect(mixedRow.getAttribute("draggable")).toBe("false");
    await fireEvent.dragOver(referenceRow, { dataTransfer });
    await fireEvent.drop(referenceRow, { dataTransfer });
    await fireEvent.dragOver(mixedRow, { dataTransfer });
    await fireEvent.drop(mixedRow, { dataTransfer });

    expect(referenceRow.classList.contains("is-drop-target")).toBe(false);
    expect(bubbled).toBe(false);
    expect(view.emitted("moveEntry")).toEqual([["src/README.md", "workspace/docs"]]);
  });

  it("blocks drops on file and reference rows without bubbling, while reference files remain non-draggable", async () => {
    const referenceFile = {
      id: "reference:guide",
      type: "file" as const,
      path: "reference/guide.md",
      name: "reference-guide.md",
      locator: { kind: "REFERENCE" as const, path: "reference/guide.md", referenceAlias: "requirements" },
      source: "REFERENCE" as const,
      merged: true,
      collision: false,
      readonly: true,
      referenceAliases: ["requirements"]
    };
    const referenceDirectory = {
      id: "reference:docs",
      type: "directory" as const,
      path: "reference/docs",
      name: "reference-docs",
      locator: { kind: "REFERENCE" as const, path: "reference/docs", referenceAlias: "requirements" },
      source: "REFERENCE" as const,
      merged: true,
      collision: false,
      readonly: true,
      referenceAliases: ["requirements"]
    };
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "file" as const, path: "README.md", name: "README.md" },
            referenceFile,
            referenceDirectory
          ]
        },
        expandedDirectories: new Set<string>(),
        dragSourcePath: "src/README.md"
      }
    });
    const dataTransfer = { dropEffect: "move", getData: () => "src/README.md" };
    const rows = [
      view.getByRole("button", { name: "README.md" }),
      view.getByRole("button", { name: "reference-guide.md" }),
      view.getByRole("button", { name: "reference-docs" })
    ];
    let dragOverBubbles = 0;
    let dropBubbles = 0;
    view.container.addEventListener("dragover", () => { dragOverBubbles += 1; });
    view.container.addEventListener("drop", () => { dropBubbles += 1; });

    expect(rows[1]?.getAttribute("draggable")).toBe("false");
    for (const row of rows) {
      dataTransfer.dropEffect = "move";
      await fireEvent.dragOver(row!, { dataTransfer });
      expect(dataTransfer.dropEffect).toBe("none");
      dataTransfer.dropEffect = "move";
      await fireEvent.drop(row!, { dataTransfer });
      expect(dataTransfer.dropEffect).toBe("none");
    }

    expect(dragOverBubbles).toBe(0);
    expect(dropBubbles).toBe(0);
    expect(view.emitted("moveEntry")).toBeUndefined();
  });

  it("blocks all readonly workspace file and directory drops without bubbling", async () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "directory" as const, path: "docs", name: "docs" },
            { type: "file" as const, path: "README.md", name: "README.md" }
          ]
        },
        expandedDirectories: new Set<string>(),
        canWrite: false,
        dragSourcePath: "src/README.md"
      }
    });
    const dataTransfer = { dropEffect: "move", getData: () => "src/README.md" };
    let dropBubbles = 0;
    view.container.addEventListener("drop", () => { dropBubbles += 1; });

    for (const name of ["docs", "README.md"]) {
      const row = view.getByRole("button", { name });
      await fireEvent.dragOver(row, { dataTransfer });
      await fireEvent.drop(row, { dataTransfer });
    }

    expect(dropBubbles).toBe(0);
    expect(view.emitted("moveEntry")).toBeUndefined();
  });

  it("makes both files and directories non-draggable when the workspace is readonly", () => {
    const view = render(DirectoryRows, {
      props: {
        directory: "",
        entriesByDirectory: {
          "": [
            { type: "directory" as const, path: "docs", name: "docs" },
            { type: "file" as const, path: "README.md", name: "README.md" }
          ]
        },
        expandedDirectories: new Set<string>(),
        canWrite: false
      }
    });

    expect(view.getByRole("button", { name: "docs" }).getAttribute("draggable")).toBe("false");
    expect(view.getByRole("button", { name: "README.md" }).getAttribute("draggable")).toBe("false");
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
    await fireEvent.click(within(dialog).getByRole("radio", { name: "上传" }));
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
