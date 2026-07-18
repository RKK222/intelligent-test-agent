import { describe, expect, it } from "vitest";
import {
  analyzeSequenceParticipantDeletion,
  appendSequenceStatement,
  deleteSequenceParticipant,
  deleteSequenceStatement,
  moveSequenceParticipant,
  moveSequenceStatement,
  rebindSequenceMessage,
  renameSequenceParticipant,
  updateSequenceParticipant,
  validateMermaidSequence
} from "../src/mermaid/sequence/commands";
import { flattenMermaidSequenceStatements, type MermaidSequenceMessage } from "../src/mermaid/sequence/model";
import { parseMermaidSequence } from "../src/mermaid/sequence/parser";
import { serializeMermaidSequence } from "../src/mermaid/sequence/serializer";

describe("Mermaid Sequence 纯命令层", () => {
  it("重命名参与者时原子更新所有递归引用、分组与生命周期", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
box Aqua 服务
participant A
participant B
end
create participant C
A->>C: 创建
alt 正常
  C->>B: 调用
  Note over A,C: 说明
else 失败
  activate C
end
destroy C
C-xA: 停止`);
    const result = renameSequenceParticipant(diagram, "C", "Worker");

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    const flat = flattenMermaidSequenceStatements(result.diagram.statements);
    expect(result.diagram.participants.some((participant) => participant.id === "Worker")).toBe(true);
    expect(flat.filter((statement): statement is MermaidSequenceMessage => statement.kind === "message"))
      .toEqual(expect.arrayContaining([
        expect.objectContaining({ target: "Worker", create: expect.objectContaining({ participantId: "Worker" }) }),
        expect.objectContaining({ source: "Worker" }),
        expect.objectContaining({ destroy: expect.objectContaining({ participantId: "Worker" }) })
      ]));
    expect(flat.find((statement) => statement.kind === "note")).toMatchObject({ participants: ["A", "Worker"] });
    expect(flat.find((statement) => statement.kind === "activation")).toMatchObject({ participantId: "Worker" });
    expect(renameSequenceParticipant(result.diagram, "Worker", "A--B"))
      .toEqual(expect.objectContaining({ ok: false, reason: expect.stringContaining("ID") }));
  });

  it("支持跨分支移动，但立即拒绝跨越锁定源码", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
alt 一
  A->>B: one
else 二
  A->>B: two
end`);
    const block = diagram.statements.find((statement) => statement.kind === "block");
    if (!block || block.kind !== "block") throw new Error("缺少 alt");
    const message = block.branches[0]?.statements[0];
    const targetBranch = block.branches[1];
    if (!message || !targetBranch) throw new Error("缺少分支");

    const moved = moveSequenceStatement(diagram, message.id, targetBranch.id, 1);
    expect(moved.ok).toBe(true);
    if (moved.ok) {
      const movedBlock = moved.diagram.statements.find((statement) => statement.kind === "block");
      expect(movedBlock?.kind === "block" && movedBlock.branches[0]?.statements).toEqual([]);
      expect(movedBlock?.kind === "block" && movedBlock.branches[1]?.statements.map((item) => item.id))
        .toEqual([expect.any(String), message.id]);
    }

    const locked = parseMermaidSequence(`sequenceDiagram
A->>B: before
A()B: locked
A->>B: after`);
    const [before, , after] = locked.statements;
    if (!before || !after) throw new Error("缺少消息");
    const rejected = moveSequenceStatement(locked, before.id, "root", 3);
    expect(rejected).toEqual(expect.objectContaining({ ok: false, reason: expect.stringContaining("锁定") }));
  });

  it("删除参与者先报告影响数，确认后递归级联且保留空分支", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
alt 调用
  A->>B: request
  Note right of B: note
else 空
  activate B
end`);
    expect(analyzeSequenceParticipantDeletion(diagram, "B")).toMatchObject({
      total: 3,
      messages: 1,
      notes: 1,
      activations: 1
    });
    expect(deleteSequenceParticipant(diagram, "B", false)).toEqual(expect.objectContaining({
      ok: false,
      reason: expect.stringContaining("确认")
    }));

    const deleted = deleteSequenceParticipant(diagram, "B", true);
    expect(deleted.ok).toBe(true);
    if (!deleted.ok) return;
    expect(deleted.diagram.participants.map((participant) => participant.id)).toEqual(["A"]);
    const block = deleted.diagram.statements.find((statement) => statement.kind === "block");
    expect(block?.kind === "block" && block.branches.map((branch) => branch.statements)).toEqual([[], []]);
  });

  it("端点重绑保持消息语义，并返回可展示的中文校验原因", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
participant C
A->>B: call`);
    const message = diagram.messages[0];
    if (!message) throw new Error("缺少消息");
    const rebound = rebindSequenceMessage(diagram, message.id, "C", "A");
    expect(rebound.ok).toBe(true);
    if (rebound.ok) expect(rebound.diagram.messages[0]).toMatchObject({ source: "C", target: "A", text: "call" });

    const invalid = parseMermaidSequence(`sequenceDiagram
participant A
participant B
deactivate B
A->>B: before create
create participant C
A->>B: wrong target
destroy C
A->>B: wrong destroy`);
    const issues = validateMermaidSequence(invalid);
    expect(issues.map((issue) => issue.message)).toEqual(expect.arrayContaining([
      expect.stringContaining("尚未激活"),
      expect.stringContaining("create"),
      expect.stringContaining("destroy")
    ]));
  });

  it("快捷停用作用于消息来源，并拒绝把停用移动到激活之前", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
A->>+B: call
B-->>-A: response`);
    expect(validateMermaidSequence(diagram).filter((issue) => issue.code === "inactive-deactivate")).toEqual([]);

    const response = diagram.messages[1];
    if (!response) throw new Error("缺少响应消息");
    const moved = moveSequenceStatement(diagram, response.id, "root", 0);
    expect(moved).toEqual(expect.objectContaining({ ok: false, reason: expect.stringContaining("尚未激活") }));
  });

  it("参与者属性同步 create 声明并可往返解析", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
create participant B as 旧名称
A->>B: create`);
    const renamed = renameSequenceParticipant(diagram, "B", "Worker");
    if (!renamed.ok) throw new Error(renamed.reason);
    const updated = updateSequenceParticipant(renamed.diagram, "Worker", { text: "新名称", type: "queue" });
    if (!updated.ok) throw new Error(updated.reason);

    const source = serializeMermaidSequence(updated.diagram);
    expect(source).toContain('create participant Worker@{ "type": "queue" } as 新名称');
    expect(parseMermaidSequence(source).participants.find((participant) => participant.id === "Worker"))
      .toMatchObject({ text: "新名称", type: "queue", created: true });
  });

  it("删除包含 create 的片段会把参与者晋升为普通声明", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
loop 创建
  create participant B
  A->>B: create
end`);
    const block = diagram.statements.find((statement) => statement.kind === "block");
    if (!block) throw new Error("缺少片段");
    const deleted = deleteSequenceStatement(diagram, block.id);
    if (!deleted.ok) throw new Error(deleted.reason);

    expect(deleted.diagram.participants.find((participant) => participant.id === "B"))
      .toMatchObject({ declared: true, created: false });
    expect(serializeMermaidSequence(deleted.diagram)).toContain("participant B");
  });

  it("拒绝删除激活后遗留无效快捷停用", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
activate A
A->>B: call
A->>-B: done`);
    const activation = diagram.statements.find((statement) => statement.kind === "activation");
    if (!activation) throw new Error("缺少激活语句");
    expect(deleteSequenceStatement(diagram, activation.id))
      .toEqual(expect.objectContaining({ ok: false, reason: expect.stringContaining("尚未激活") }));
  });

  it("根级追加与参与者重排同步源码顺序，不移动穿插注释", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
%% 声明间注释
participant B
A->>B: old`);
    const reordered = moveSequenceParticipant(diagram, "B", 0);
    if (!reordered.ok) throw new Error(reordered.reason);
    const appended = appendSequenceStatement(reordered.diagram, "root", {
      id: "note-new",
      kind: "note",
      placement: "over",
      participants: ["A", "B"],
      text: "tail"
    });
    if (!appended.ok) throw new Error(appended.reason);
    const source = serializeMermaidSequence(appended.diagram);

    expect(source.indexOf("participant B")).toBeLessThan(source.indexOf("%% 声明间注释"));
    expect(source.indexOf("%% 声明间注释")).toBeLessThan(source.indexOf("participant A"));
    expect(source.trimEnd().endsWith("Note over A,B: tail")).toBe(true);
  });

  it("拒绝把非分组参与者插入 box 成员之间", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
box Aqua Group
participant A
participant C
end
participant B`);
    expect(moveSequenceParticipant(diagram, "B", 1)).toEqual(expect.objectContaining({
      ok: false,
      reason: expect.stringContaining("连续")
    }));
  });

  it("Note 位置与参与者数量不匹配时返回明确校验", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
Note over A,B: note`);
    const note = flattenMermaidSequenceStatements(diagram.statements).find((statement) => statement.kind === "note");
    if (!note) throw new Error("缺少 Note");
    Object.assign(note, { placement: "right" });
    expect(validateMermaidSequence(diagram)).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: "invalid-note-participants", message: expect.stringContaining("一个参与者") })
    ]));
  });

  it("互斥分支可分别销毁同一参与者", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
alt 成功
  destroy B
  A-xB: stop
else 失败
  destroy B
  A-xB: abort
end`);
    expect(validateMermaidSequence(diagram).filter((issue) => issue.code === "duplicate-destroy")).toEqual([]);
  });

  it("互斥分支仍拒绝重复 create 同一参与者", () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
alt 首次创建
  create participant B
  A->>B: first
else 另一路径
  Note right of A: pending
end`);
    const block = diagram.statements.find((statement) => statement.kind === "block");
    const targetBranch = block?.kind === "block" ? block.branches[1] : undefined;
    if (!targetBranch) throw new Error("缺少目标分支");

    const appended = appendSequenceStatement(diagram, targetBranch.id, {
      id: "message-duplicate-create",
      kind: "message",
      source: "A",
      target: "B",
      text: "second",
      arrow: "->>",
      create: { participantId: "B", type: "participant", text: "B" }
    });

    expect(appended).toEqual(expect.objectContaining({
      ok: false,
      reason: expect.stringContaining("重复 create")
    }));
  });
});
