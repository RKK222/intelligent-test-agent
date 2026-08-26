import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it } from "vitest";
import FileExplorer from "../src/FileExplorer.vue";

describe("FileExplorer", () => {
  it("only renders server search results whose file name contains the keyword", () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: {},
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        activeTab: "search",
        searchKeyword: "需求",
        searchResults: [
          {
            path: "需求资料/贷款申请.md",
            name: "贷款申请.md",
            directory: "需求资料",
            size: 10
          },
          {
            path: "docs/需求说明.md",
            name: "需求说明.md",
            directory: "docs",
            size: 20
          }
        ]
      }
    });

    expect(view.queryByText("贷款申请.md")).toBeNull();
    expect(view.getByText("需求").closest("button")?.textContent).toContain("需求说明.md");
  });

  it("emits download events for files and directories", async () => {
    const entries = [
      { type: "directory" as const, path: "docs", name: "docs" },
      { type: "file" as const, path: "README.md", name: "README.md" }
    ];
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: { "": entries },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "下载文件夹 docs" }));
    await fireEvent.click(view.getByRole("button", { name: "下载文件 README.md" }));

    expect(view.emitted("downloadEntry")).toEqual([[entries[0]], [entries[1]]]);
  });

  it("hides and blocks attach, download and mutation entry points in strict read-only mode", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: {
          "": [{ type: "file", path: "secret.txt", name: "secret.txt" }]
        },
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        canWrite: false,
        canAttach: false,
        canDownload: false
      }
    });
    const file = view.getByRole("button", { name: "secret.txt" });

    expect(view.queryByRole("button", { name: "下载文件 secret.txt" })).toBeNull();
    expect(view.queryByRole("button", { name: "新建或上传到工作区根目录" })).toBeNull();
    await fireEvent.contextMenu(file);
    await fireEvent.keyDown(file, { key: "x", ctrlKey: true });
    await fireEvent.change(view.getByLabelText("选择要上传到工作区的文件"), {
      target: { files: [new File(["content"], "blocked.txt", { type: "text/plain" })] }
    });

    expect(view.queryByText("添加文件到对话")).toBeNull();
    expect(view.emitted("addFileContext")).toBeUndefined();
    expect(view.emitted("moveEntry")).toBeUndefined();
    expect(view.emitted("uploadFiles")).toBeUndefined();
  });

  it("forwards view entries so duplicate logical paths keep their locator identity", async () => {
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
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: { "": [reference] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "guide.md" }));
    expect(view.emitted("openViewFile")).toEqual([[reference]]);
    expect(view.emitted("openFile")).toBeUndefined();
  });

  it("uploads selected files to the workspace root", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });
    const file = new File([new Uint8Array([0, 1, 2])], "icon.bin", { type: "application/octet-stream" });

    await fireEvent.change(view.getByLabelText("选择要上传到工作区的文件"), {
      target: { files: [file] }
    });

    expect(view.emitted("uploadFiles")).toEqual([["", [file]]]);
  });

  it("uploads operating-system files dropped on the workspace root", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });
    const file = new File(["content"], "notes.txt", { type: "text/plain" });
    const tree = view.container.querySelector(".ta-file-tree-scroll") as HTMLElement;
    const dataTransfer = {
      files: [file],
      dropEffect: "none",
      getData: () => ""
    };

    await fireEvent.dragOver(tree, { dataTransfer });
    await fireEvent.drop(tree, { dataTransfer });

    expect(view.emitted("uploadFiles")).toEqual([["", [file]]]);
  });

  it("forwards internal copy and move operations to the app layer", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: {
          "": [
            { type: "directory", path: "docs", name: "docs" },
            { type: "file", path: "README.md", name: "README.md" }
          ]
        },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });
    const readme = view.getByRole("button", { name: "README.md" });
    const docs = view.getByRole("button", { name: "docs" });

    await fireEvent.keyDown(readme, { key: "c", ctrlKey: true });
    await fireEvent.keyDown(docs, { key: "v", ctrlKey: true });
    await fireEvent.keyDown(readme, { key: "x", ctrlKey: true });
    await fireEvent.keyDown(docs, { key: "v", ctrlKey: true });

    expect(view.emitted("copyEntry")).toEqual([["README.md", "docs"]]);
    expect(view.emitted("moveEntry")).toEqual([["README.md", "docs"]]);
  });

  it("uses a normal click as the anchor for the next Shift range selection", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: {
          "": [
            { type: "directory", path: "docs", name: "docs" },
            { type: "file", path: "README.md", name: "README.md" }
          ],
          docs: [{ type: "file", path: "docs/guide.md", name: "guide.md" }]
        },
        expandedDirectories: new Set(["docs"]),
        changedFiles: []
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "docs" }));
    await fireEvent.click(view.getByRole("button", { name: "README.md" }), { shiftKey: true });

    expect(view.emitted("selectionChange")?.at(-1)).toEqual([[
      { path: "docs", type: "directory" },
      { path: "docs/guide.md", type: "file" },
      { path: "README.md", type: "file" }
    ]]);
    expect(view.getByRole("button", { name: "docs" }).classList.contains("is-selected")).toBe(true);
    expect(view.getByRole("button", { name: "guide.md" }).classList.contains("is-selected")).toBe(true);
    expect(view.getByRole("button", { name: "README.md" }).classList.contains("is-selected")).toBe(true);
  });

  it("opens root upload from the plus menu and forwards undo", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: { "": [{ type: "file", path: "README.md", name: "README.md" }] },
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        canUndo: true
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "新建或上传到工作区根目录" }));
    const dialog = view.getByRole("dialog", { name: "新建或上传文件" });
    expect(dialog.textContent).toContain("目标目录");
    expect(dialog.textContent).toContain("工作区根目录");
    await fireEvent.click(view.getByRole("button", { name: "README.md" }));
    await fireEvent.keyDown(view.getByRole("button", { name: "README.md" }), { key: "z", ctrlKey: true });

    expect(view.emitted("undoEntry")).toEqual([[]]);
  });

  it("clears the root blue drop frame after dragend", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });
    const tree = view.container.querySelector(".ta-file-tree-scroll") as HTMLElement;

    await fireEvent.dragOver(tree, { dataTransfer: { dropEffect: "none" } });
    expect(tree.classList.contains("is-root-drop-target")).toBe(true);
    await fireEvent.dragEnd(window);
    expect(tree.classList.contains("is-root-drop-target")).toBe(false);
  });

  it("shares the internal drag source with recursive rows and clears the source row after dragend", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: { "": [{ type: "directory", path: "docs", name: "docs" }] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });
    const data = new Map<string, string>();
    const dataTransfer = {
      effectAllowed: "none",
      setData: (type: string, value: string) => data.set(type, value),
      getData: (type: string) => data.get(type) ?? ""
    };
    const docs = view.getByRole("button", { name: "docs" });

    await fireEvent.dragStart(docs, { dataTransfer });
    expect(docs.classList.contains("is-dragging")).toBe(true);
    await fireEvent.dragEnd(window, { dataTransfer });

    expect(docs.classList.contains("is-dragging")).toBe(false);
  });

  it("moves an internal source to the blank workspace root but rejects its current parent", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: {
          "": [
            { type: "directory", path: "docs", name: "docs" },
            { type: "file", path: "README.md", name: "README.md" }
          ],
          docs: [{ type: "file", path: "docs/guide.md", name: "guide.md" }]
        },
        expandedDirectories: new Set(["docs"]),
        changedFiles: []
      }
    });
    const tree = view.container.querySelector(".ta-file-tree-scroll") as HTMLElement;
    const data = new Map<string, string>();
    const dataTransfer = {
      effectAllowed: "none",
      dropEffect: "none",
      setData: (type: string, value: string) => data.set(type, value),
      getData: (type: string) => data.get(type) ?? ""
    };
    const guide = view.getByRole("button", { name: "guide.md" });
    const readme = view.getByRole("button", { name: "README.md" });

    await fireEvent.dragStart(guide, { dataTransfer });
    await fireEvent.dragOver(tree, { dataTransfer });
    await fireEvent.drop(tree, { dataTransfer });
    expect(view.emitted("moveEntry")).toEqual([["docs/guide.md", ""]]);

    await fireEvent.dragStart(readme, { dataTransfer });
    await fireEvent.dragOver(tree, { dataTransfer });
    expect(dataTransfer.dropEffect).toBe("none");
    await fireEvent.drop(tree, { dataTransfer });
    expect(dataTransfer.dropEffect).toBe("none");
    expect(view.emitted("moveEntry")).toEqual([["docs/guide.md", ""]]);
  });

  it("keeps nested directory and file moves working after window drop capture clears the shared source", async () => {
    const view = render(FileExplorer, {
      props: {
        entriesByDirectory: {
          "": [
            { type: "directory", path: "src", name: "src" },
            { type: "directory", path: "target", name: "target" }
          ],
          src: [{ type: "file", path: "src/guide.md", name: "guide.md" }],
          target: [{ type: "directory", path: "target/nested", name: "nested" }]
        },
        expandedDirectories: new Set(["src", "target"]),
        changedFiles: []
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
    const guide = view.getByRole("button", { name: "guide.md" });
    const nested = view.getByRole("button", { name: "nested" });

    await fireEvent.dragStart(src, { dataTransfer });
    expect(src.classList.contains("is-dragging")).toBe(true);
    await fireEvent.drop(nested, { dataTransfer });
    expect(src.classList.contains("is-dragging")).toBe(false);

    await fireEvent.dragStart(guide, { dataTransfer });
    expect(guide.classList.contains("is-dragging")).toBe(true);
    await fireEvent.drop(nested, { dataTransfer });
    expect(guide.classList.contains("is-dragging")).toBe(false);

    expect(view.emitted("moveEntry")).toEqual([
      ["src", "target/nested"],
      ["src/guide.md", "target/nested"]
    ]);
  });
});
