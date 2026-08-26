import { describe, expect, it } from "vitest";
import {
  browserUploadRelativePath,
  markdownImageSources,
  planWorkspaceUpload,
  resolveMarkdownWorkspaceImagePath
} from "../src/components/markdown-workspace-images";

function folderFile(content: BlobPart, name: string, relativePath: string, type = "application/octet-stream") {
  const file = new File([content], name, { type });
  Object.defineProperty(file, "webkitRelativePath", { configurable: true, value: relativePath });
  return file;
}

describe("markdown workspace images", () => {
  it("只收集 Markdown 图片，不误判外链、data URL 和代码块", () => {
    const markdown = [
      "![流程图](./流程图.png)",
      "![引用图][diagram]",
      "![外链](https://example.com/a.png)",
      "![内联](data:image/png;base64,AA)",
      "`![代码](./code.png)`",
      "",
      "[diagram]: images/拓扑图.svg"
    ].join("\n");

    expect(markdownImageSources(markdown)).toEqual([
      "./流程图.png",
      "images/拓扑图.svg",
      "https://example.com/a.png",
      "data:image/png;base64,AA"
    ]);
  });

  it("按 Markdown 所在目录解析中文路径并阻断越出工作区", () => {
    expect(resolveMarkdownWorkspaceImagePath("docs/方案.md", "./图片/流程 图.png?raw=1#x"))
      .toBe("docs/图片/流程 图.png");
    expect(resolveMarkdownWorkspaceImagePath("docs/v2/方案.md", "../拓扑.png"))
      .toBe("docs/拓扑.png");
    expect(resolveMarkdownWorkspaceImagePath("docs/方案.md", "/assets/logo.png"))
      .toBe("assets/logo.png");
    expect(resolveMarkdownWorkspaceImagePath("方案.md", "../secret.png")).toBeUndefined();
    expect(resolveMarkdownWorkspaceImagePath("方案.md", "https://example.com/a.png")).toBeUndefined();
  });

  it("选择整个目录时去掉选择器顶层目录并保留内部层级", async () => {
    const markdown = folderFile("![图](./images/a.png)", "方案.md", "本机方案/方案.md", "text/markdown");
    const image = folderFile("png", "a.png", "本机方案/images/a.png", "image/png");

    expect(browserUploadRelativePath(image)).toBe("images/a.png");
    const plan = await planWorkspaceUpload("docs", [markdown, image]);
    expect(plan.items.map((item) => item.targetPath)).toEqual(["docs/方案.md", "docs/images/a.png"]);
    expect(plan.missingMarkdownImages).toEqual([]);
  });

  it("Markdown 引用四张图但只多选三张时仍生成上传计划并只提示缺少一张", async () => {
    const names = ["新旧对比图.png", "拓扑图.png", "CD阶段.png", "泳道图.png"];
    const markdown = new File(
      [names.map((name) => `![${name}](./${name})`).join("\n")],
      "SLB策略发布重构总体方案.md",
      { type: "text/markdown" }
    );
    const selectedImages = names.slice(0, 3).map((name) => new File(["png"], name, { type: "image/png" }));

    const plan = await planWorkspaceUpload("设计", [markdown, ...selectedImages]);

    expect(plan.items).toHaveLength(4);
    expect(plan.items.map((item) => item.targetPath)).toContain("设计/SLB策略发布重构总体方案.md");
    expect(plan.missingMarkdownImages).toEqual([{
      markdownPath: "设计/SLB策略发布重构总体方案.md",
      source: "./泳道图.png",
      targetPath: "设计/泳道图.png"
    }]);
  });

  it("普通多选时按唯一文件名把图片放到 Markdown 引用的子目录", async () => {
    const markdown = new File(["![图](./images/a.png)"], "方案.md", { type: "text/markdown" });
    const image = new File(["png"], "a.png", { type: "image/png" });

    const plan = await planWorkspaceUpload("docs", [markdown, image]);

    expect(plan.items.map((item) => item.targetPath)).toEqual(["docs/方案.md", "docs/images/a.png"]);
    expect(plan.missingMarkdownImages).toEqual([]);
  });
});
