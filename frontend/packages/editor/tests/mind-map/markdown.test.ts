import { describe, expect, it } from "vitest";
import {
  parseMindMapMarkdown,
  prepareMindMapDocument
} from "../../src/mind-map/markdown";

const SAMPLE = `# 产品冷启动

- 用户分析
  - 用户画像
  - 核心痛点
  - 使用场景
- 产品验证
  - MVP
  - 用户反馈
  - 留存率
- 用户增长
  - SEO
  - 社群
  - 内容营销
`;

describe("思维导图 Markdown 领域模型", () => {
  it("把单个 H1 与无序层级列表解析为树", () => {
    const result = parseMindMapMarkdown(SAMPLE);

    expect(result.status).toMatchObject({ canEdit: true });
    expect(result.document?.root.text).toBe("产品冷启动");
    expect(result.document?.root.children.map((node) => node.text)).toEqual([
      "用户分析",
      "产品验证",
      "用户增长"
    ]);
    expect(result.document?.root.children[0]?.children.map((node) => node.text)).toEqual([
      "用户画像",
      "核心痛点",
      "使用场景"
    ]);
  });

  it.each([
    ["Tab 缩进", "# 根\n\n- 一级\n\t- 二级\n", 4],
    ["跳级", "# 根\n\n- 一级\n  - 二级\n      - 跳级\n", 5],
    ["多根", "# 根一\n\n- 节点\n\n# 根二\n", 5],
    ["有序列表", "# 根\n\n1. 节点\n", 3],
    ["续行", "# 根\n\n- 节点\n  续行\n", 4],
    ["粗体富文本", "# 根\n\n- **粗体**\n", 3],
    ["星号斜体富文本", "# 根\n\n- *斜体*\n", 3],
    ["下划线斜体富文本", "# 根\n\n- _斜体_\n", 3],
    ["引用链接富文本", "# 根\n\n- [文档][guide]\n", 3]
  ])("拒绝%s并报告源码行号", (_name, source, line) => {
    const result = parseMindMapMarkdown(source);

    expect(result.status.canEdit).toBe(false);
    expect(result.status.message).toContain(`第 ${line} 行`);
  });

  it("首次应用按前序生成 ID、元数据并规范化项目符号和缩进", () => {
    const parsed = parseMindMapMarkdown("# 根\n\n* A\n    + B\n* C\n");
    const applied = prepareMindMapDocument(parsed.document!);

    expect(applied.content).toMatch(
      /^# 根\n\n<!-- mm:id=root -->\n\n- A <!-- mm:id=n1 -->\n  - B <!-- mm:id=n2 -->\n- C <!-- mm:id=n3 -->\n\n<!--mm:v1:[A-Za-z0-9_-]+-->\n$/
    );
    expect(applied.document.root.id).toBe("root");
    expect(applied.document.root.children[0]?.id).toBe("n1");
    expect(applied.document.root.children[0]?.children[0]?.id).toBe("n2");
    expect(applied.document.root.children[1]?.id).toBe("n3");
    expect(applied.document.nextId).toBe(4);
  });

  it("无元数据时保留合法的部分编号并从最大编号之后补齐", () => {
    const parsed = parseMindMapMarkdown(
      "# 根\n\n<!-- mm:id=root -->\n\n- 已编号 <!-- mm:id=n4 -->\n- 待编号\n"
    );
    const applied = prepareMindMapDocument(parsed.document!);

    expect(applied.content).toContain("- 已编号 <!-- mm:id=n4 -->");
    expect(applied.content).toContain("- 待编号 <!-- mm:id=n5 -->");
    expect(applied.document.nextId).toBe(6);
  });

  it("删除节点后新增节点使用元数据中的 nextId 而不复用旧编号", () => {
    const first = prepareMindMapDocument(
      parseMindMapMarkdown("# 根\n\n- A\n- B\n").document!
    );
    const reopened = parseMindMapMarkdown(first.content).document!;
    reopened.root.children.splice(1, 1);
    reopened.root.children.push({ text: "C", children: [] });

    const second = prepareMindMapDocument(reopened);

    expect(second.content).toContain("- A <!-- mm:id=n1 -->");
    expect(second.content).toContain("- C <!-- mm:id=n3 -->");
    expect(second.content).not.toContain("C <!-- mm:id=n2 -->");
  });

  it("规范化输出保留输入的 CRLF 风格", () => {
    const applied = prepareMindMapDocument(
      parseMindMapMarkdown("# 根\r\n\r\n- A\r\n  - B\r\n").document!
    );

    expect(applied.content).toContain("\r\n<!-- mm:id=root -->\r\n");
    expect(applied.content.replaceAll("\r\n", "")).not.toContain("\n");
  });

  it("已有元数据时缺失、重复或非法 ID 只允许安全预览", () => {
    const applied = prepareMindMapDocument(
      parseMindMapMarkdown("# 根\n\n- A\n- B\n").document!
    ).content;
    const missing = applied.replace(" <!-- mm:id=n2 -->", "");
    const duplicate = applied.replace("mm:id=n2", "mm:id=n1");
    const invalid = applied.replace("mm:id=n2", "mm:id=bad id");

    for (const source of [missing, duplicate, invalid]) {
      const result = parseMindMapMarkdown(source);
      expect(result.document?.root.children).toHaveLength(2);
      expect(result.status.canEdit).toBe(false);
    }
  });

  it("未知版本、重复元数据和损坏校验均只允许安全预览", () => {
    const applied = prepareMindMapDocument(
      parseMindMapMarkdown("# 根\n\n- A\n").document!
    ).content;
    const marker = applied.match(/<!--mm:v1:[A-Za-z0-9_-]+-->/)?.[0] ?? "";
    const payload = marker.slice("<!--mm:v1:".length, -3);
    const corruptIndex = Math.max(1, Math.floor(payload.length / 2));
    const corruptPayload = `${payload.slice(0, corruptIndex)}${payload[corruptIndex] === "A" ? "B" : "A"}${payload.slice(corruptIndex + 1)}`;

    for (const source of [
      applied.replace("<!--mm:v1:", "<!--mm:v2:"),
      `${applied.trimEnd()}\n${marker}\n`,
      applied.replace(payload, corruptPayload)
    ]) {
      const result = parseMindMapMarkdown(source);
      expect(result.document?.root.text).toBe("根");
      expect(result.status.canEdit).toBe(false);
    }
  });

  it("超出节点和层级限制时只保留限制内的安全预览树", () => {
    const tooManyNodes = ["# 根", "", ...Array.from({ length: 2_001 }, (_, index) => `- N${index + 1}`), ""].join("\n");
    const nodeResult = parseMindMapMarkdown(tooManyNodes);
    let retainedNodeCount = 0;
    const nodeStack = nodeResult.document ? [nodeResult.document.root] : [];
    while (nodeStack.length > 0) {
      const node = nodeStack.pop()!;
      retainedNodeCount += 1;
      nodeStack.push(...node.children);
    }

    const tooDeep = ["# 根", "", ...Array.from({ length: 129 }, (_, depth) => `${"  ".repeat(depth)}- L${depth + 1}`), ""].join("\n");
    const depthResult = parseMindMapMarkdown(tooDeep);
    let retainedDepth = 0;
    let current = depthResult.document?.root.children[0];
    while (current) {
      retainedDepth += 1;
      current = current.children[0];
    }

    expect(nodeResult.status.canEdit).toBe(false);
    expect(nodeResult.status.issues.filter((item) => item.code === "node-limit")).toHaveLength(1);
    expect(retainedNodeCount).toBe(2_000);
    expect(depthResult.status.canEdit).toBe(false);
    expect(retainedDepth).toBe(128);
  });

  it("超过元数据 uint32 表达范围的节点 ID 在解析阶段进入安全预览", () => {
    const result = parseMindMapMarkdown(
      "# 根\n\n<!-- mm:id=root -->\n\n- A <!-- mm:id=n4294967295 -->\n"
    );

    expect(result.document?.root.children[0]?.text).toBe("A");
    expect(result.status.canEdit).toBe(false);
    expect(result.status.issues.some((item) => item.code === "invalid-id")).toBe(true);
  });

  it("最大节点 ID 之后仍有待编号节点时只允许安全预览", () => {
    const result = parseMindMapMarkdown(
      "# 根\n\n<!-- mm:id=root -->\n\n- 已编号 <!-- mm:id=n4294967294 -->\n- 待编号\n"
    );

    expect(result.document?.root.children).toHaveLength(2);
    expect(result.status.canEdit).toBe(false);
    expect(result.status.issues.some((item) => item.code === "id-exhausted")).toBe(true);
  });
});
