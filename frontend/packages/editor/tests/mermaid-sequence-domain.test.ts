import { describe, expect, it } from "vitest";
import { parseMermaidDiagram, serializeMermaidDiagram } from "../src/mermaid/diagram";
import {
  flattenMermaidSequenceStatements,
  type MermaidSequenceBlock,
  type MermaidSequenceMessage
} from "../src/mermaid/sequence/model";
import { autoLayoutMermaidSequence } from "../src/mermaid/sequence/layout";
import { parseMermaidSequence } from "../src/mermaid/sequence/parser";
import { serializeMermaidSequence } from "../src/mermaid/sequence/serializer";

async function expectOfficialMermaidParse(source: string): Promise<void> {
  const css = window.CSS as { supports?: (property: string, value?: string) => boolean };
  const originalSupports = css.supports;
  if (!originalSupports) Object.defineProperty(css, "supports", { configurable: true, value: () => true });
  try {
    const mermaid = (await import("mermaid")).default;
    await expect(mermaid.parse(source)).resolves.toBeTruthy();
  } finally {
    if (originalSupports) Object.defineProperty(css, "supports", { configurable: true, value: originalSupports });
    else delete css.supports;
  }
}

describe("Mermaid sequenceDiagram 领域层", () => {
  it("解析 8 类参与者、别名、box、autonumber 和旧坐标 metadata", async () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
%% editor-layout:
%% {
%%   "U": { "x": 80, "y": 70 },
%%   "S": { "x": 320, "y": 70 }
%% }
box Aqua 接入层
actor U as 用户
participant S as 服务
participant BO@{ "type": "boundary" } as 边界
participant CO@{ "type": "control" } as 控制
end
participant EN@{ "type": "entity" } as 实体
participant DB@{ "type": "database" } as 数据库
participant CS@{ "type": "collections" } as 集合
participant Q@{ "type": "queue" } as 队列
autonumber 10 5
U->>S: 请求`);

    expect(diagram.kind).toBe("sequenceDiagram");
    expect(diagram.participants.map(({ id, text, type, groupId, position }) => ({
      id, text, type, groupId, position
    }))).toEqual([
      { id: "U", text: "用户", type: "actor", groupId: "box-1", position: { x: 80, y: 70 } },
      { id: "S", text: "服务", type: "participant", groupId: "box-1", position: { x: 320, y: 70 } },
      { id: "BO", text: "边界", type: "boundary", groupId: "box-1", position: { x: 0, y: 0 } },
      { id: "CO", text: "控制", type: "control", groupId: "box-1", position: { x: 0, y: 0 } },
      { id: "EN", text: "实体", type: "entity", groupId: undefined, position: { x: 0, y: 0 } },
      { id: "DB", text: "数据库", type: "database", groupId: undefined, position: { x: 0, y: 0 } },
      { id: "CS", text: "集合", type: "collections", groupId: undefined, position: { x: 0, y: 0 } },
      { id: "Q", text: "队列", type: "queue", groupId: undefined, position: { x: 0, y: 0 } }
    ]);
    expect(diagram.groups).toMatchObject([
      { id: "box-1", label: "接入层", color: "Aqua", participantIds: ["U", "S", "BO", "CO"] }
    ]);
    expect(diagram.autonumber).toMatchObject({ enabled: true, start: 10, step: 5 });

    // 强制 8 类声明走 serializer 生成路径，并由项目锁定的 Mermaid 11.16 parser 验证。
    diagram.participants.forEach((participant) => { participant.text = `${participant.text}·`; });
    await expectOfficialMermaidParse(serializeMermaidSequence(diagram));
  });

  it("识别 Mermaid 的 10 种标准箭头和激活快捷语义", async () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
A->B: 实线开放
A-->B: 虚线开放
A->>B: 实线箭头
A-->>B: 虚线箭头
A<<->>B: 双向实线
A<<-->>B: 双向虚线
A-xB: 实线丢失
A--xB: 虚线丢失
A-)+B: 异步并激活
B--)-A: 异步响应并停用`);
    const messages = flattenMermaidSequenceStatements(diagram.statements)
      .filter((statement): statement is MermaidSequenceMessage => statement.kind === "message");

    expect(messages.map((message) => message.arrow)).toEqual([
      "->", "-->", "->>", "-->>", "<<->>", "<<-->>", "-x", "--x", "-)", "--)"
    ]);
    expect(messages.at(-2)?.activation).toBe("activate-target");
    expect(messages.at(-1)?.activation).toBe("deactivate-source");
    messages.forEach((message, index) => { message.text = `重写 ${index + 1}`; });
    await expectOfficialMermaidParse(serializeMermaidSequence(diagram));
  });

  it("把 Note、注释、显式激活、生命周期和多行文本建模为可编辑语句", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A as 调用方
create participant B as 工作器
A->>+B: 创建<br/>并调用
Note right of B: 后台任务<br/>可重试
%% 可编辑注释
activate A
B-->>-A: 已接收
deactivate A
destroy B
B-xA: 中断`);
    const statements = flattenMermaidSequenceStatements(diagram.statements);
    const created = statements.find((statement): statement is MermaidSequenceMessage =>
      statement.kind === "message" && statement.create !== undefined
    );
    const destroyed = statements.find((statement): statement is MermaidSequenceMessage =>
      statement.kind === "message" && statement.destroy !== undefined
    );

    expect(created).toMatchObject({
      source: "A",
      target: "B",
      text: "创建\n并调用",
      activation: "activate-target",
      create: { participantId: "B", type: "participant", text: "工作器" }
    });
    expect(destroyed).toMatchObject({ destroy: { participantId: "B" } });
    expect(statements.map((statement) => statement.kind)).toEqual([
      "message", "note", "comment", "activation", "message", "activation", "message"
    ]);
    expect(statements[1]).toMatchObject({ kind: "note", placement: "right", participants: ["B"], text: "后台任务\n可重试" });
  });

  it("使用递归 AST 解析全部组合片段和任意嵌套分支", async () => {
    const source = `sequenceDiagram
participant A
participant B
loop 重试
  alt 成功
    A->>B: 请求
  else 失败
    par 记录
      A-)B: 审计
    and 告警
      opt 已配置
        B-->>A: 通知
      end
    end
  end
end
critical 写入
  A->>B: 提交
option 降级
  A->B: 缓存
end
break 中断
  B-xA: 失败
end
rect rgba(40, 80, 120, 0.12)
  Note over A,B: 受控区域
end`;
    const diagram = parseMermaidSequence(source);
    const blocks = flattenMermaidSequenceStatements(diagram.statements)
      .filter((statement): statement is MermaidSequenceBlock => statement.kind === "block");

    expect(blocks.map((block) => block.blockType)).toEqual([
      "loop", "alt", "par", "opt", "critical", "break", "rect"
    ]);
    expect(blocks.find((block) => block.blockType === "alt")?.branches.map((branch) => branch.label))
      .toEqual(["成功", "失败"]);
    expect(blocks.find((block) => block.blockType === "par")?.branches.map((branch) => branch.label))
      .toEqual(["记录", "告警"]);
    expect(blocks.find((block) => block.blockType === "critical")?.branches.map((branch) => branch.label))
      .toEqual(["写入", "降级"]);

    const serialized = serializeMermaidSequence(diagram);
    const mermaid = (await import("mermaid")).default;
    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(serialized).toBe(source);
  });

  it("局部锁定高级语法并在编辑相邻语句时原样保留", () => {
    const source = `sequenceDiagram\r\nparticipant A\r\nparticipant B\r\nA->>B: before\r\npar_over A,B\r\n  A--|\\B: half\r\nend\r\nA()B: central\r\nA->>B: after; B-->>A: chained\r\nA->>B: editable\r\n`;
    const diagram = parseMermaidSequence(source);
    const locked = flattenMermaidSequenceStatements(diagram.statements)
      .filter((statement) => statement.kind === "locked");
    const editable = flattenMermaidSequenceStatements(diagram.statements)
      .find((statement): statement is MermaidSequenceMessage => statement.kind === "message" && statement.text === "editable");

    expect(locked).toHaveLength(3);
    expect(locked[0]).toMatchObject({ locked: true, reason: "暂不支持 par_over 创建与结构化编辑" });
    if (!editable) throw new Error("缺少可编辑消息");
    editable.text = "已编辑";

    const serialized = serializeMermaidSequence(diagram);
    expect(serialized).toContain("par_over A,B\r\n  A--|\\B: half\r\nend");
    expect(serialized).toContain("A()B: central");
    expect(serialized).toContain("A->>B: after; B-->>A: chained");
    expect(serialized).toContain("A->>B: 已编辑");
    expect(serialized).not.toContain("A->>B: editable");
  });

  it("锁定 Actor 菜单、标题/无障碍、半箭头与未知参与者配置", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
title: 高级标题
accTitle: 无障碍标题
accDescr: 无障碍描述
participant A@{ "type": "actor", "role": "admin" } as 管理员
participant B
link A: 控制台 @ https://example.com
A--|B: 半箭头
A->>B: editable`);
    diagram.messages.at(-1)!.text = "changed";
    const serialized = serializeMermaidSequence(diagram);

    for (const raw of [
      "title: 高级标题",
      "accTitle: 无障碍标题",
      "accDescr: 无障碍描述",
      'participant A@{ "type": "actor", "role": "admin" } as 管理员',
      "link A: 控制台 @ https://example.com",
      "A--|B: 半箭头"
    ]) expect(serialized).toContain(raw);
    expect(serialized).toContain("A->>B: changed");
  });

  it("编辑消息时保持锁定指令、空行、注释和参与者声明的原相对位置", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
title: 原始标题

participant A
%% 声明间注释
participant B
A->>B: old`);
    const message = diagram.messages[0];
    if (!message) throw new Error("缺少消息");
    message.text = "new";
    const serialized = serializeMermaidSequence(diagram);

    const tokens = ["title: 原始标题", "\n\n", "participant A", "%% 声明间注释", "participant B", "A->>B: new"];
    const positions = tokens.map((token) => serialized.indexOf(token));
    expect(positions.every((position) => position >= 0)).toBe(true);
    expect(positions).toEqual([...positions].sort((left, right) => left - right));
  });

  it("按 Mermaid 颜色规则区分 box 颜色和英文标题", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
box Another Group
participant A
end
box Aqua Colored Group
participant B
end
A->>B: old`);

    expect(diagram.groups[0]).toMatchObject({ label: "Another Group" });
    expect(diagram.groups[0]).not.toHaveProperty("color");
    expect(diagram.groups[1]).toMatchObject({ label: "Colored Group", color: "Aqua" });
    diagram.messages[0]!.text = "new";
    expect(serializeMermaidSequence(diagram)).toContain("box Another Group\nparticipant A\nend");
  });

  it("消息编辑后保持 box 内空行与注释，并锁定额外 autonumber 开关", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
autonumber 3 2
box Aqua Group
  participant A

  %% box 内注释
  participant B
end
A->>B: old
autonumber off
A-->>B: tail`);
    diagram.messages[0]!.text = "new";
    const serialized = serializeMermaidSequence(diagram);

    expect(serialized).toContain("box Aqua Group\n  participant A\n\n  %% box 内注释\n  participant B\nend");
    expect(serialized.indexOf("autonumber 3 2")).toBeLessThan(serialized.indexOf("A->>B: new"));
    expect(serialized.indexOf("autonumber off")).toBeGreaterThan(serialized.indexOf("A->>B: new"));
    expect(serialized.indexOf("autonumber off")).toBeLessThan(serialized.indexOf("A-->>B: tail"));
  });

  it("重复参与者声明在相邻消息编辑后仍原位保留", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A as One
participant A as Two
participant B
A->>B: old`);
    diagram.messages[0]!.text = "new";
    const serialized = serializeMermaidSequence(diagram);

    expect(serialized).toContain("participant A as One\nparticipant A as Two\nparticipant B\nA->>B: new");
  });

  it("参与者和 create 别名支持多行文本往返", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A as 调用<br/>入口
create participant B as 后台<br/>工作器
A->>B: create`);
    expect(diagram.participants).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: "A", text: "调用\n入口" }),
      expect.objectContaining({ id: "B", text: "后台\n工作器" })
    ]));
    expect(serializeMermaidSequence(diagram)).toContain("create participant B as 后台<br/>工作器");
  });

  it("未修改源码保持 CRLF、缩进、大小写和快捷写法，单项修改只重写该项", () => {
    const source = "  sequenceDiagram\r\n\tActor A as 用户\r\n\tparticipant B\r\n\tA ->>+ B: 第一行<br/>第二行\r\n";
    const diagram = parseMermaidSequence(source);
    expect(serializeMermaidSequence(diagram)).toBe(source);

    const message = diagram.statements.find((statement): statement is MermaidSequenceMessage => statement.kind === "message");
    if (!message) throw new Error("缺少消息");
    message.text = "新文本";
    expect(serializeMermaidSequence(diagram)).toBe(
      "  sequenceDiagram\r\n\tActor A as 用户\r\n\tparticipant B\r\n\tA->>+B: 新文本\r\n"
    );
  });

  it("损坏片段失败安全：即使修改内部文本也不臆造缺失的 end", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
loop 未闭合
  A->>B: 原文本`);
    const message = flattenMermaidSequenceStatements(diagram.statements)
      .find((statement): statement is MermaidSequenceMessage => statement.kind === "message");
    if (!message) throw new Error("缺少消息");
    message.text = "已编辑";

    const serialized = serializeMermaidSequence(diagram);
    expect(serialized).toContain("A->>B: 已编辑");
    expect(serialized).not.toMatch(/\n\s*end\s*$/);
  });

  it("自动布局参与者并保持输入模型不变", () => {
    const diagram = parseMermaidSequence("sequenceDiagram\nparticipant A\nparticipant B\nparticipant C");
    const laidOut = autoLayoutMermaidSequence(diagram);

    expect(laidOut).not.toBe(diagram);
    expect(laidOut.participants.map((participant) => participant.position)).toEqual([
      { x: 80, y: 70 },
      { x: 300, y: 70 },
      { x: 520, y: 70 }
    ]);
    expect(diagram.participants.every((participant) => participant.position.x === 0)).toBe(true);
  });

  it("通用入口按图头分发 flowchart 与 sequenceDiagram", () => {
    expect(parseMermaidDiagram("flowchart TD\nA --> B").kind).toBe("flowchart");
    expect(parseMermaidDiagram("sequenceDiagram\nA->>B: hi").kind).toBe("sequenceDiagram");
    expect(serializeMermaidDiagram(parseMermaidDiagram("sequenceDiagram\nA->>B: hi"))).toContain(
      "A->>B: hi"
    );
    expect(() => parseMermaidDiagram("classDiagram\nA <|-- B")).toThrow(
      "仅支持 flowchart、graph 或 sequenceDiagram"
    );
  });
});
