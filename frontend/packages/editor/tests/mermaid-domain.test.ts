import { describe, expect, it } from "vitest";
import { autoLayoutMermaidGraph } from "../src/mermaid/layout";
import { findMermaidBlocks, replaceMermaidBlock } from "../src/mermaid/markdown-blocks";
import { parseMermaidFlowchart } from "../src/mermaid/parser";
import { serializeMermaidGraph } from "../src/mermaid/serializer";

describe("Mermaid flowchart 领域层", () => {
  it("解析节点、边、标签、方向、布局 metadata 并保留未知语句", () => {
    const graph = parseMermaidFlowchart(`flowchart LR
%% editor-layout:
%% {
%%   "A": { "x": 120, "y": 80 },
%%   "B": { "x": 360, "y": 80 }
%% }
A[开始] -->|通过| B{检查}
classDef important fill:red`);

    expect(graph.kind).toBe("flowchart");
    expect(graph.direction).toBe("LR");
    expect(graph.nodes).toEqual([
      { id: "A", text: "开始", type: "rectangle", position: { x: 120, y: 80 } },
      { id: "B", text: "检查", type: "diamond", position: { x: 360, y: 80 } }
    ]);
    expect(graph.edges).toMatchObject([
      { source: "A", target: "B", label: "通过", relation: "arrow" }
    ]);
    expect(graph.preservedLines).toEqual(["classDef important fill:red"]);
  });

  it("支持 graph、独立节点声明和常用节点类型", () => {
    const graph = parseMermaidFlowchart(`graph TD
A([开始])
B((结束))
C[普通]
A -.-> B
B --- C`);

    expect(graph.kind).toBe("graph");
    expect(graph.nodes.map((node) => [node.id, node.type])).toEqual([
      ["A", "stadium"],
      ["B", "circle"],
      ["C", "rectangle"]
    ]);
    expect(graph.edges.map((edge) => edge.relation)).toEqual(["dotted", "line"]);
  });

  it("解析约定的十一种旧式节点和三种现代文档节点", () => {
    const graph = parseMermaidFlowchart(`flowchart LR
Start([开始/结束])
Process[普通处理步骤]
Rounded(圆角处理节点)
Subroutine[[子程序]]
Database[(数据库)]
Connector((连接点))
Decision{条件判断}
Prepare{{准备步骤}}
Input[/输入或输出/]
Manual[/人工处理\\]
Stop(((终止节点)))
Text@{ shape: text, label: "文本块" }
Doc@{ shape: doc, label: "文档" }
Docs@{ shape: docs, label: "多文档" }
Start --> Process
Process --> Rounded
Rounded --> Decision
Decision -->|调用子流程| Subroutine
Subroutine --> Database
Database --> Connector
Decision -->|准备数据| Prepare
Prepare --> Input
Input --> Manual
Manual --> Connector
Connector --> Stop`);

    expect(graph.nodes.map(({ id, text, type }) => ({ id, text, type }))).toEqual([
      { id: "Start", text: "开始/结束", type: "stadium" },
      { id: "Process", text: "普通处理步骤", type: "rectangle" },
      { id: "Rounded", text: "圆角处理节点", type: "rounded" },
      { id: "Subroutine", text: "子程序", type: "subroutine" },
      { id: "Database", text: "数据库", type: "database" },
      { id: "Connector", text: "连接点", type: "circle" },
      { id: "Decision", text: "条件判断", type: "diamond" },
      { id: "Prepare", text: "准备步骤", type: "hexagon" },
      { id: "Input", text: "输入或输出", type: "parallelogram" },
      { id: "Manual", text: "人工处理", type: "trapezoid" },
      { id: "Stop", text: "终止节点", type: "double-circle" },
      { id: "Text", text: "文本块", type: "text" },
      { id: "Doc", text: "文档", type: "doc" },
      { id: "Docs", text: "多文档", type: "docs" }
    ]);
    expect(graph.edges.map(({ source, target, label }) => ({ source, target, label }))).toEqual([
      { source: "Start", target: "Process", label: "" },
      { source: "Process", target: "Rounded", label: "" },
      { source: "Rounded", target: "Decision", label: "" },
      { source: "Decision", target: "Subroutine", label: "调用子流程" },
      { source: "Subroutine", target: "Database", label: "" },
      { source: "Database", target: "Connector", label: "" },
      { source: "Decision", target: "Prepare", label: "准备数据" },
      { source: "Prepare", target: "Input", label: "" },
      { source: "Input", target: "Manual", label: "" },
      { source: "Manual", target: "Connector", label: "" },
      { source: "Connector", target: "Stop", label: "" }
    ]);
  });

  it("现代节点语法允许字段换序、空白变化和省略 label", () => {
    const graph = parseMermaidFlowchart(`flowchart TD
A@{ label: "普通处理步骤", shape: rect }
B@{shape: doc}`);

    expect(graph.nodes.map(({ id, text, type }) => ({ id, text, type }))).toEqual([
      { id: "A", text: "普通处理步骤", type: "rectangle" },
      { id: "B", text: "B", type: "doc" }
    ]);
  });

  it("受支持的现代节点可以直接内联在连线两端", () => {
    const graph = parseMermaidFlowchart(`flowchart LR
A@{ shape: rect, label: "处理" } --> B@{ shape: doc, label: "文档" }`);

    expect(graph.nodes.map(({ id, text, type }) => ({ id, text, type }))).toEqual([
      { id: "A", text: "处理", type: "rectangle" },
      { id: "B", text: "文档", type: "doc" }
    ]);
    expect(graph.edges[0]).toMatchObject({ source: "A", target: "B" });
    expect(graph.preservedLines).toEqual([]);
  });

  it("兼容 Mermaid 官方接受的 YAML 单引号现代属性", async () => {
    const source = "flowchart TD\nA@{ shape: 'rect', label: '单引号' }";
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(source)).resolves.toBeTruthy();
    const graph = parseMermaidFlowchart(source);
    expect(graph.nodes[0]).toMatchObject({ id: "A", type: "rectangle", text: "单引号" });
    expect(parseMermaidFlowchart(serializeMermaidGraph(graph)).nodes[0]?.text).toBe("单引号");
  });

  it("YAML 单引号标签把反斜杠视为普通字符", async () => {
    const source = String.raw`flowchart TD
A@{ shape: 'rect', label: '路径\' }`;
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(source)).resolves.toBeTruthy();
    const graph = parseMermaidFlowchart(source);
    expect(graph.nodes[0]?.text).toBe("路径\\");
    await expect(mermaid.parse(serializeMermaidGraph(graph))).resolves.toBeTruthy();
  });

  it("支持多行文本的解析与序列化且被官方 parser 接受", async () => {
    const source = "flowchart TD\nA@{ shape: rect, label: \"第一行\\n第二行\" }\nB@{ shape: rect, label: \"新行\" }\nA -->|连线<br>第二行| B";
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(source)).resolves.toBeTruthy();
    const graph = parseMermaidFlowchart(source);
    expect(graph.nodes[0]).toMatchObject({ id: "A", type: "rectangle", text: "第一行\n第二行" });
    expect(graph.edges[0]).toMatchObject({ source: "A", target: "B", label: "连线\n第二行" });

    const serialized = serializeMermaidGraph(graph);
    expect(serialized).toContain("A@{ shape: rect, label: \"第一行\\n第二行\" }");
    expect(serialized).toContain("A -->|连线<br>第二行| B");
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
  });

  it.each(["false", "null", "~", "0x10"])(
    "YAML 非字符串裸标量 label=%s 时保留原句及关联边",
    (label) => {
      const source = `flowchart TD\nA@{ shape: rect, label: ${label} }\nA --> B`;
      const graph = parseMermaidFlowchart(source);

      expect(graph.nodes).toEqual([]);
      expect(graph.edges).toEqual([]);
      expect(graph.preservedLines).toEqual([
        `A@{ shape: rect, label: ${label} }`,
        "A --> B"
      ]);
      expect(serializeMermaidGraph(graph)).toContain(`A@{ shape: rect, label: ${label} }`);
    }
  );

  it("YAML 专有双引号转义无法无损解码时保留节点及关联边", async () => {
    const source = `flowchart TD
A@{ shape: rect, label: "\\x41" }
A --> B`;
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(source)).resolves.toBeTruthy();
    const graph = parseMermaidFlowchart(source);
    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.preservedLines).toEqual([
      'A@{ shape: rect, label: "\\x41" }',
      "A --> B"
    ]);
  });

  it("带额外属性的现代节点及其连线保持原文，不降级为矩形", () => {
    const graph = parseMermaidFlowchart(`flowchart TD
A@{ shape: doc, label: "文档", width: 160 }
A --> B`);

    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.preservedLines).toEqual([
      'A@{ shape: doc, label: "文档", width: 160 }',
      "A --> B"
    ]);
  });

  it("内联未知现代形状不会被后续裸 ID 连线降级为矩形", () => {
    const graph = parseMermaidFlowchart(`flowchart TD
A@{ shape: cloud, label: "外部节点" } --> B
A --> C`);

    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.preservedLines).toEqual([
      'A@{ shape: cloud, label: "外部节点" } --> B',
      "A --> C"
    ]);
  });

  it("混合未知与受支持现代节点的保留语句会隔离全部参与 ID", () => {
    const graph = parseMermaidFlowchart(`flowchart TD
A@{ shape: cloud, label: "外部" } --> B@{ shape: doc, label: "文档" }
B --> C`);

    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.preservedLines).toEqual([
      'A@{ shape: cloud, label: "外部" } --> B@{ shape: doc, label: "文档" }',
      "B --> C"
    ]);
  });

  it.each([
    [
      'B@{ shape: rect, label: "矩形" }',
      'A@{ shape: cloud, label: "外部" } --> B@{ shape: doc, label: "文档" }'
    ],
    [
      'A@{ shape: cloud, label: "外部" } --> B@{ shape: doc, label: "文档" }',
      'B@{ shape: rect, label: "矩形" }'
    ]
  ])("隔离 ID 的独立现代声明无论前后顺序都不会重复接管", (first, second) => {
    const graph = parseMermaidFlowchart(`flowchart TD\n${first}\n${second}`);

    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.preservedLines).toEqual([first, second]);
  });

  it("无法安全拆边的受支持现代语句会隔离其中全部 ID", async () => {
    const source = `flowchart TD
A@{ shape: rect, label: "前 --> 后" } --> B@{ shape: doc, label: "文档" }
A --> C`;
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(source)).resolves.toBeTruthy();
    const graph = parseMermaidFlowchart(source);
    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.preservedLines).toEqual([
      'A@{ shape: rect, label: "前 --> 后" } --> B@{ shape: doc, label: "文档" }',
      "A --> C"
    ]);
  });

  it("损坏的现代节点声明不会被后续裸 ID 连线降级为矩形", () => {
    const graph = parseMermaidFlowchart(`flowchart TD
A@{ shape: rect, label: "未闭合 }
A --> B`);

    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.preservedLines).toEqual([
      'A@{ shape: rect, label: "未闭合 }',
      "A --> B"
    ]);
  });

  it("把十四种节点统一序列化为现代短名并稳定往返", async () => {
    const graph = parseMermaidFlowchart(`flowchart LR
Start([开始/结束])
Process[普通处理步骤]
Rounded(圆角处理节点)
Subroutine[[子程序]]
Database[(数据库)]
Connector((连接点))
Decision{条件判断}
Prepare{{准备步骤}}
Input[/输入或输出/]
Manual[/人工处理\\]
Stop(((终止节点)))
Text@{ shape: text, label: "文本块" }
Doc@{ shape: doc, label: "文档" }
Docs@{ shape: docs, label: "多文档" }`);
    let serialized = "";

    expect(() => {
      serialized = serializeMermaidGraph(graph);
    }).not.toThrow();
    expect(serialized).toContain('Start@{ shape: stadium, label: "开始/结束" }');
    expect(serialized).toContain('Process@{ shape: rect, label: "普通处理步骤" }');
    expect(serialized).toContain('Rounded@{ shape: rounded, label: "圆角处理节点" }');
    expect(serialized).toContain('Subroutine@{ shape: fr-rect, label: "子程序" }');
    expect(serialized).toContain('Database@{ shape: cyl, label: "数据库" }');
    expect(serialized).toContain('Connector@{ shape: circle, label: "连接点" }');
    expect(serialized).toContain('Decision@{ shape: diam, label: "条件判断" }');
    expect(serialized).toContain('Prepare@{ shape: hex, label: "准备步骤" }');
    expect(serialized).toContain('Input@{ shape: lean-r, label: "输入或输出" }');
    expect(serialized).toContain('Manual@{ shape: trap-b, label: "人工处理" }');
    expect(serialized).toContain('Stop@{ shape: dbl-circ, label: "终止节点" }');
    expect(serialized).toContain('Text@{ shape: text, label: "文本块" }');
    expect(serialized).toContain('Doc@{ shape: doc, label: "文档" }');
    expect(serialized).toContain('Docs@{ shape: docs, label: "多文档" }');

    const mermaid = (await import("mermaid")).default;
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(serializeMermaidGraph(parseMermaidFlowchart(serialized))).toBe(serialized);
  });

  it("序列化结果可被 Mermaid 官方 parser 接受并稳定 round trip", async () => {
    const original = `flowchart TD
A[开始] --> B(处理)
B ==>|完成| C((结束))
classDef important fill:red`;
    const serialized = serializeMermaidGraph(parseMermaidFlowchart(original));
    const roundTrip = parseMermaidFlowchart(serialized);
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(roundTrip.nodes.map(({ id, text, type }) => ({ id, text, type }))).toEqual([
      { id: "A", text: "开始", type: "rectangle" },
      { id: "B", text: "处理", type: "rounded" },
      { id: "C", text: "结束", type: "circle" }
    ]);
    expect(roundTrip.preservedLines).toContain("classDef important fill:red");
    expect(serializeMermaidGraph(roundTrip)).toBe(serialized);
  });

  it("接管完整 HEX 节点和连线文字样式并规范化为六位大写", async () => {
    const graph = parseMermaidFlowchart(`flowchart LR
A[开始] -->|通过| B[结束]
style A color:#abc, stroke:#123456, fill:#def
linkStyle 0 color:#0a7
style A fill:#010203`);

    expect(graph.nodes[0]?.style).toEqual({
      textColor: "#AABBCC",
      fillColor: "#010203",
      strokeColor: "#123456"
    });
    expect(graph.edges[0]?.style).toEqual({ textColor: "#00AA77" });
    expect(graph.preservedLines).toEqual([]);

    const serialized = serializeMermaidGraph(graph);
    expect(serialized).toContain("style A fill:#010203,stroke:#123456,color:#AABBCC");
    expect(serialized).toContain("linkStyle 0 color:#00AA77");
    expect(serialized.lastIndexOf("style A ")).toBeGreaterThan(serialized.indexOf("A -->|通过| B"));
    const mermaid = (await import("mermaid")).default;
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(serializeMermaidGraph(parseMermaidFlowchart(serialized))).toBe(serialized);
  });

  it("重复直接样式后者覆盖，删除边后 linkStyle 按当前索引重写", () => {
    const graph = parseMermaidFlowchart(`flowchart LR
A --> B
B --> C
style A color:#111
style A color:#eee
linkStyle 0 color:#123
linkStyle 1 color:#456`);

    expect(graph.nodes[0]?.style?.textColor).toBe("#EEEEEE");
    expect(graph.edges.map((edge) => edge.style?.textColor)).toEqual(["#112233", "#445566"]);
    graph.edges.shift();

    const serialized = serializeMermaidGraph(graph);
    expect(serialized).not.toContain("linkStyle 1");
    expect(serialized).toContain("linkStyle 0 color:#445566");
  });

  it("保留的多边语句参与 Mermaid 全局 linkStyle 索引", async () => {
    const graph = parseMermaidFlowchart(`flowchart LR
A --> B & C
D --> E
linkStyle 2 color:#abc`);

    expect(graph.edges).toHaveLength(1);
    expect(graph.edges[0]).toMatchObject({ source: "D", target: "E", style: { textColor: "#AABBCC" } });

    const serialized = serializeMermaidGraph(graph);
    expect(serialized).toContain("A --> B & C");
    expect(serialized).toContain("linkStyle 2 color:#AABBCC");
    expect(serialized).not.toContain("linkStyle 0 color:#AABBCC");
    const mermaid = (await import("mermaid")).default;
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
  });

  it("注释与无障碍描述中的箭头文本不参与 linkStyle 索引", async () => {
    const graph = parseMermaidFlowchart(`flowchart LR
%% 旧方案：A --> B
accTitle: C --> D
accDescr {
  旧描述 E --> F
}
G --> H
linkStyle 0 color:#123`);

    expect(graph.edges[0]).toMatchObject({ source: "G", target: "H", style: { textColor: "#112233" } });
    const serialized = serializeMermaidGraph(graph);
    expect(serialized).toContain("linkStyle 0 color:#112233");
    expect(serialized).not.toContain("linkStyle 1 color:#112233");
    const mermaid = (await import("mermaid")).default;
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
  });

  it("命名色、透明色、额外属性、无效边索引和类样式继续原样保留", () => {
    const preserved = [
      "style A color:red",
      "style A fill:#FFF,stroke:#000,stroke-width:2px",
      "style A fill:transparent",
      "linkStyle 4 color:#ABC",
      "linkStyle 0 stroke:#ABC",
      "classDef important fill:#fff",
      "class A important"
    ];
    const graph = parseMermaidFlowchart(["flowchart LR", "A --> B", ...preserved].join("\n"));

    expect(graph.nodes.every((node) => node.style === undefined)).toBe(true);
    expect(graph.edges[0]?.style).toBeUndefined();
    expect(graph.preservedLines).toEqual(preserved);
    const serialized = serializeMermaidGraph(graph);
    for (const line of preserved) expect(serialized).toContain(line);
  });

  it("文本块只接管文字颜色，表面样式原样保留", () => {
    const graph = parseMermaidFlowchart(`flowchart TD
T@{ shape: text, label: "说明" }
style T color:#123
style T fill:#fff,stroke:#000`);

    expect(graph.nodes[0]?.style).toEqual({ textColor: "#112233" });
    expect(graph.preservedLines).toEqual(["style T fill:#fff,stroke:#000"]);
    expect(serializeMermaidGraph(graph)).toContain("style T color:#112233");
  });

  it("全部恢复默认后删除规范化 style 和 linkStyle", () => {
    const graph = parseMermaidFlowchart(`flowchart LR
A --> B
style A fill:#fff,stroke:#000,color:#123
linkStyle 0 color:#456`);
    delete graph.nodes[0]!.style;
    delete graph.edges[0]!.style;

    const serialized = serializeMermaidGraph(graph);
    expect(serialized).not.toContain("style A");
    expect(serialized).not.toContain("linkStyle");
  });

  it("直接写入领域模型的三位及小写 HEX 也会稳定规范化输出", () => {
    const graph = parseMermaidFlowchart("flowchart LR\nA --> B");
    graph.nodes[0]!.style = { fillColor: "#abc", strokeColor: "#123def", textColor: "#0a7" };
    graph.edges[0]!.style = { textColor: "#fed" };

    const serialized = serializeMermaidGraph(graph);

    expect(serialized).toContain("style A fill:#AABBCC,stroke:#123DEF,color:#00AA77");
    expect(serialized).toContain("linkStyle 0 color:#FFEEDD");
  });

  it("现代节点标签转义引号并把换行稳定保留", async () => {
    const graph = parseMermaidFlowchart('flowchart TD\nA@{ shape: rect, label: "原始" }');
    graph.nodes[0]!.text = '他说 "完成"\n下一步 | 检查';

    const serialized = serializeMermaidGraph(graph);
    const mermaid = (await import("mermaid")).default;

    expect(serialized).toContain('A@{ shape: rect, label: "他说 \\"完成\\"\\n下一步 | 检查" }');
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(parseMermaidFlowchart(serialized).nodes[0]?.text).toBe('他说 "完成"\n下一步 | 检查');
  });

  it("现代节点标签转义反斜杠且通过官方 parser 稳定往返", async () => {
    const graph = parseMermaidFlowchart('flowchart TD\nA@{ shape: rect, label: "原始" }');
    graph.nodes[0]!.text = "路径 C:\\temp\\";

    const serialized = serializeMermaidGraph(graph);
    const mermaid = (await import("mermaid")).default;

    expect(serialized).toContain('label: "路径 C:\\\\temp\\\\"');
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(parseMermaidFlowchart(serialized).nodes[0]?.text).toBe("路径 C:\\temp\\");
  });

  it("现代节点标签用 JSON/YAML 安全转义保存控制字符", async () => {
    const source = 'flowchart TD\nA@{ shape: rect, label: "ESC \\u001b BS \\b FF \\f TAB \\t" }';
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(source)).resolves.toBeTruthy();
    const graph = parseMermaidFlowchart(source);
    const serialized = serializeMermaidGraph(graph);

    expect(serialized).toContain('label: "ESC \\u001b BS \\b FF \\f TAB \\t"');
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(parseMermaidFlowchart(serialized).nodes[0]?.text).toBe(graph.nodes[0]?.text);
  });

  it("显式空标签保存后仍保持为空", () => {
    const serialized = serializeMermaidGraph(
      parseMermaidFlowchart('flowchart TD\nA@{ shape: text, label: "" }')
    );

    expect(serialized).toContain('A@{ shape: text, label: "" }');
    expect(parseMermaidFlowchart(serialized).nodes[0]?.text).toBe("");
  });

  it("保存并恢复用户选择的固定端口 metadata", () => {
    const graph = parseMermaidFlowchart(`flowchart LR
%% editor-edge-ports:
%% [
%%   {
%%     "source": "A",
%%     "target": "B",
%%     "sourceHandle": "target-2",
%%     "targetHandle": "source-1"
%%   }
%% ]
A --> B`);

    expect(graph.edges[0]).toMatchObject({
      source: "A",
      target: "B",
      sourceHandle: "target-2",
      targetHandle: "source-1"
    });
    const serialized = serializeMermaidGraph(graph);
    expect(serialized.split("\n").filter((line) => /^%%@(?!\+)/.test(line))).toHaveLength(1);
    expect(serialized).not.toContain("editor-edge-ports");
    expect(parseMermaidFlowchart(serialized).edges[0]).toMatchObject({
      sourceHandle: "target-2",
      targetHandle: "source-1"
    });
  });

  it("旧边保持无固定端口且合法陈旧 metadata 在保存时清理", () => {
    const legacy = parseMermaidFlowchart("flowchart TD\nA --> B");
    expect(legacy.edges[0]).not.toHaveProperty("sourceHandle");

    const stale = parseMermaidFlowchart(`flowchart TD
%% editor-edge-ports:
%% [{"source":"X","target":"Y","sourceHandle":"source-0","targetHandle":"target-0"}]
A --> B`);
    expect(stale.preservedLines).not.toContain("%% editor-edge-ports:");
    expect(serializeMermaidGraph(stale)).not.toContain("editor-edge-ports");
  });

  it("损坏或无法唯一匹配的端口 metadata 原样保留", () => {
    const damaged = parseMermaidFlowchart(`flowchart TD
%% editor-edge-ports:
%% not-json
A --> B`);
    expect(serializeMermaidGraph(damaged)).toContain("%% editor-edge-ports:\n%% not-json");

    const ambiguous = parseMermaidFlowchart(`flowchart TD
%% editor-edge-ports:
%% [{"source":"A","target":"B","sourceHandle":"source-0","targetHandle":"target-0"}]
A --> B
A --> B`);
    expect(ambiguous.edges.every((edge) => !edge.sourceHandle && !edge.targetHandle)).toBe(true);
    expect(serializeMermaidGraph(ambiguous)).toContain("%% editor-edge-ports:");
  });

  it("删除固定端口边后不再序列化对应 metadata", () => {
    const graph = parseMermaidFlowchart(`flowchart TD
%% editor-edge-ports:
%% [{"source":"A","target":"B","sourceHandle":"source-0","targetHandle":"target-0"}]
A --> B`);
    graph.edges = [];

    expect(serializeMermaidGraph(graph)).not.toContain("editor-edge-ports");
  });

  it("只替换指定 Mermaid fence 内容并检测外部刷新冲突", () => {
    const markdown = `# 设计

\`\`\`mermaid
flowchart TD
A --> B
\`\`\`

正文

~~~mermaid
graph LR
X --> Y
~~~`;
    const blocks = findMermaidBlocks(markdown);

    expect(blocks.map((block) => block.source)).toEqual([
      "flowchart TD\nA --> B\n",
      "graph LR\nX --> Y\n"
    ]);
    const next = replaceMermaidBlock(markdown, 1, "graph LR\nX[新] --> Y\n", blocks[1]?.source);
    expect(next).toContain("flowchart TD\nA --> B");
    expect(next).toContain("graph LR\nX[新] --> Y");
    expect(() => replaceMermaidBlock(next, 1, "graph TD", blocks[1]?.source)).toThrow(
      "Mermaid 代码块已发生变化"
    );
  });

  it("自动布局按图方向生成确定性坐标且不改变输入模型", async () => {
    const graph = parseMermaidFlowchart("flowchart LR\nA --> B\nA --> C\nC --> D");
    const laidOut = await autoLayoutMermaidGraph(graph);

    expect(laidOut).not.toBe(graph);
    expect(laidOut.nodes.find((node) => node.id === "A")?.position.x).toBeLessThan(
      laidOut.nodes.find((node) => node.id === "B")?.position.x ?? 0
    );
    expect(laidOut.nodes.find((node) => node.id === "A")?.position.x).toBeLessThan(
      laidOut.nodes.find((node) => node.id === "C")?.position.x ?? 0
    );
    expect(graph.nodes.every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
    expect(await autoLayoutMermaidGraph(graph)).toEqual(laidOut);
  });

  it("拒绝非 flowchart/graph 和缺少图头的内容", () => {
    expect(() => parseMermaidFlowchart("sequenceDiagram\nA->>B: hi")).toThrow(
      "仅支持 flowchart 或 graph"
    );
    expect(() => parseMermaidFlowchart("A --> B")).toThrow("缺少 flowchart 或 graph 图头");
  });

  it("subgraph 整块不参与可视化并保持内部语句连续", () => {
    const serialized = serializeMermaidGraph(parseMermaidFlowchart(`flowchart TD
subgraph Cluster[内部]
  A --> B
end
C --> D`));

    expect(serialized).toContain("subgraph Cluster[内部]\n  A --> B\nend");
    expect(serialized.indexOf("subgraph Cluster")).toBeLessThan(serialized.indexOf("C --> D"));
    expect(parseMermaidFlowchart(serialized).edges).toHaveLength(1);
    expect(parseMermaidFlowchart(serialized).edges[0]).toMatchObject({ source: "C", target: "D" });
  });

  it("自动布局时，重排后的连接点优先连到一般节点的中间，且优先连到判断（菱形）节点的四个顶点", async () => {
    // 1. 一般节点：优先连接到边的中间
    // 构造两个矩形节点，其连线偏向左上方，验证增加了中点偏好惩罚后，
    // 它依然会选择较靠近中部的端口（如 target-2），而不是因为偏角极小而选择角落端口（如 target-0）。
    const graph = parseMermaidFlowchart("flowchart TD\nA --> B");
    
    graph.nodes[0]!.position = { x: 10, y: 70 };
    graph.nodes[1]!.position = { x: 150, y: 210 };
    
    const laidOut = await autoLayoutMermaidGraph(graph);
    const edge = laidOut.edges[0]!;
    
    // Loose Handle 的 source/target 前缀不再限制边角色；两个端口都位于矩形顶边中部附近。
    expect(["target-2", "source-2"]).toContain(edge.targetHandle);
    
    // 2. 判断（菱形）节点：优先连接到四个顶点
    const diamondGraph = parseMermaidFlowchart("flowchart LR\nX{判断} --> Y");
    // X 是判断节点，其出边即使倾斜也应该优先连到右顶点 source-0，而不是斜边端口
    diamondGraph.nodes[0]!.type = "diamond";
    diamondGraph.nodes[0]!.position = { x: 80, y: 70 };
    diamondGraph.nodes[1]!.position = { x: 300, y: 210 };
    
    const laidOutDiamond = await autoLayoutMermaidGraph(diamondGraph);
    const diamondEdge = laidOutDiamond.edges[0]!;
    
    expect(diamondEdge.sourceHandle).toBe("source-0");
  });
});
