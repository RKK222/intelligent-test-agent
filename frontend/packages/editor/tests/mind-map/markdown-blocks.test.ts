import { describe, expect, it } from "vitest";
import {
  findMindMapBlocks,
  replaceMindMapBlock
} from "../../src/mind-map/markdown-blocks";

describe("Markdown mind fence 定位", () => {
  it("只识别准确的 mind fence 并按出现顺序编号", () => {
    const markdown = `# 文档

\`\`\`mind
# 一

- A
\`\`\`

\`\`\`mindmap
# 不是思维导图
\`\`\`

~~~~mind readonly
# 二

- B
~~~~
`;

    expect(findMindMapBlocks(markdown).map((block) => block.source)).toEqual([
      "# 一\n\n- A\n",
      "# 二\n\n- B\n"
    ]);
  });

  it("多个 fence 中只替换目标块并保留其余原文", () => {
    const markdown = "```mind\n# 一\n\n- A\n```\n正文\n```mind\n# 二\n\n- B\n```\n";
    const expected = findMindMapBlocks(markdown)[1]!.source;
    const updated = replaceMindMapBlock(markdown, 1, "# 二\n\n- C", expected);

    expect(updated).toBe("```mind\n# 一\n\n- A\n```\n正文\n```mind\n# 二\n\n- C\n```\n");
  });

  it("闭合 fence 更长且多个块内容相同时仍精确替换第一个块", () => {
    const source = "# 相同\n\n- A\n";
    const markdown = `\`\`\`\`mind\n${source}\`\`\`\`\`\n正文\n\`\`\`mind\n${source}\`\`\`\n`;

    const blocks = findMindMapBlocks(markdown);
    const updated = replaceMindMapBlock(markdown, 0, "# 已修改\n\n- B", source);

    expect(blocks).toHaveLength(2);
    expect(updated).toBe(`\`\`\`\`mind\n# 已修改\n\n- B\n\`\`\`\`\`\n正文\n\`\`\`mind\n${source}\`\`\`\n`);
  });

  it("与 MarkdownPreview 一致地只把小写 mind 识别为可编辑块", () => {
    const markdown = "```MIND\n# 大写\n\n- A\n```\n```mind\n# 小写\n\n- B\n```\n";

    expect(findMindMapBlocks(markdown).map((block) => block.source)).toEqual([
      "# 小写\n\n- B\n"
    ]);
  });

  it("不会把其它 fenced code block 内的 mind 文本误识别为可编辑块", () => {
    const markdown = "`````text\n```mind\n# 只是示例\n\n- A\n```\n`````\n";

    expect(findMindMapBlocks(markdown)).toEqual([]);
  });

  it("打开后目标 fence 已变化时拒绝并发覆盖", () => {
    const original = "```mind\n# 根\n\n- A\n```\n";
    const refreshed = original.replace("- A", "- B");

    expect(() => replaceMindMapBlock(refreshed, 0, "# 根\n\n- C", "# 根\n\n- A\n"))
      .toThrow(/已发生变化/);
  });
});
