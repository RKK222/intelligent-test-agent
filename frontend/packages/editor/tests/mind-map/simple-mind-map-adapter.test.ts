import { describe, expect, it } from "vitest";
import {
  fromSimpleMindMapData,
  toSimpleMindMapData
} from "../../src/mind-map/simple-mind-map-adapter";
import { parseMindMapMarkdown } from "../../src/mind-map/markdown";

describe("SimpleMindMap 安全数据适配器", () => {
  it("只向画布下发字面文本、ID、折叠状态和白名单样式", () => {
    const document = parseMindMapMarkdown(
      "# 根\n\n<!-- mm:id=root -->\n\n- <script>alert(1)</script> **字面加粗** <!-- mm:id=n1 -->\n"
    ).document!;
    document.root.children[0]!.collapsed = true;
    document.root.children[0]!.style = {
      textColor: "#112233",
      fillColor: "#445566",
      borderColor: "#778899",
      borderWidth: 2,
      shape: "roundedRectangle",
      fontSize: 18,
      bold: true,
      lineColor: "#AABBCC",
      lineWidth: 3,
      lineDash: "dashed"
    };
    Object.assign(document.root.children[0]!, {
      image: "https://example.com/a.png",
      hyperlink: "javascript:alert(1)",
      note: "不应执行",
      icon: ["x"],
      tag: ["x"],
      generalization: { text: "x" }
    });

    const data = toSimpleMindMapData(document);

    expect(data.children[0]).toEqual({
      data: {
        text: "<script>alert(1)</script> **字面加粗**",
        uid: "n1",
        expand: false,
        richText: false,
        color: "#112233",
        fillColor: "#445566",
        borderColor: "#778899",
        borderWidth: 2,
        shape: "roundedRectangle",
        fontSize: 18,
        fontWeight: "bold",
        lineColor: "#AABBCC",
        lineWidth: 3,
        lineDasharray: "6,4"
      },
      children: []
    });
    expect(data.children[0]?.data).not.toHaveProperty("image");
    expect(data.children[0]?.data).not.toHaveProperty("hyperlink");
    expect(data.children[0]?.data).not.toHaveProperty("note");
  });

  it("从画布结果只读取白名单，新生成的第三方 uid 留给 ID 分配器处理", () => {
    const template = parseMindMapMarkdown(
      "# 根\n\n<!-- mm:id=root -->\n\n- A <!-- mm:id=n1 -->\n"
    ).document!;
    template.root.children[0]!.unknownFields = [
      { type: 0x7e, value: Uint8Array.from([9]) }
    ];

    const document = fromSimpleMindMapData({
      data: { text: "根", uid: "root", expand: true, richText: false },
      children: [
        {
          data: {
            text: "A2",
            uid: "n1",
            expand: true,
            richText: false,
            color: "#112233",
            image: "data:image/png;base64,unsafe",
            hyperlink: "https://example.com",
            note: "unsafe"
          },
          children: []
        },
        {
          data: {
            text: "新增",
            uid: "a-third-party-uuid",
            expand: true,
            richText: true,
            icon: ["unsafe"]
          },
          children: []
        }
      ]
    }, template);

    expect(document.root.children[0]).toMatchObject({
      id: "n1",
      text: "A2",
      style: { textColor: "#112233" },
      unknownFields: [{ type: 0x7e, value: Uint8Array.from([9]) }]
    });
    expect(document.root.children[1]).toMatchObject({ id: undefined, text: "新增" });
    expect(document.root.children[1]).not.toHaveProperty("image");
    expect(document.root.children[1]).not.toHaveProperty("icon");
  });

  it("安全预览会把重复或非法 ID 替换为唯一的临时画布 uid", () => {
    const document = parseMindMapMarkdown(
      "# 根\n\n- A\n- B\n- C\n- D\n"
    ).document!;
    document.root.children[0]!.id = "n1";
    document.root.children[1]!.id = "n1";
    document.root.children[2]!.id = "root";
    document.root.children[3]!.id = "bad-id";

    const data = toSimpleMindMapData(document);
    const uids = [data.data.uid, ...data.children.map((child) => child.data.uid)];

    expect(uids[0]).toBe("root");
    expect(uids[1]).toBe("n1");
    expect(new Set(uids).size).toBe(uids.length);
    expect(uids.slice(2)).toEqual(["mm-draft-1", "mm-draft-2", "mm-draft-3"]);
  });
});
