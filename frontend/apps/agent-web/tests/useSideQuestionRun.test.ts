import { effectScope } from "vue";
import { describe, expect, it, vi } from "vitest";
import type { RunEvent } from "@test-agent/shared-types";
import type { RunEventSubscribeOptions, RunEventSubscription } from "@test-agent/event-stream-client";
import { useSideQuestionRun } from "../src/components/useSideQuestionRun";

function event(type: string, payload: Record<string, unknown> = {}): RunEvent {
  return {
    eventId: `evt_${type}_${Math.random()}`,
    runId: "run_side_1",
    seq: 1,
    type,
    traceId: "trace_1",
    occurredAt: "2026-07-11T00:00:00Z",
    payload
  };
}

function setup() {
  const startSideQuestionRun = vi.fn().mockResolvedValue({ runId: "run_side_1" });
  const close = vi.fn();
  let options: RunEventSubscribeOptions | undefined;
  const subscribe = vi.fn((next: RunEventSubscribeOptions): RunEventSubscription => {
    options = next;
    return { close };
  });
  const scope = effectScope();
  const state = scope.run(() => useSideQuestionRun({
    api: { startSideQuestionRun },
    baseUrl: "http://backend.test",
    subscribe
  }))!;
  return { startSideQuestionRun, subscribe, close, getOptions: () => options!, scope, state };
}

describe("useSideQuestionRun", () => {
  it("starts one side-question run and subscribes through the existing RunEvent client", async () => {
    const fixture = setup();

    await fixture.state.submit({
      sessionId: "ses_main",
      question: "现在做到哪里了？",
      messageId: "msg_remote",
      model: "provider/model"
    });
    await fixture.state.submit({ sessionId: "ses_main", question: "不要重复启动" });

    expect(fixture.startSideQuestionRun).toHaveBeenCalledOnce();
    expect(fixture.startSideQuestionRun).toHaveBeenCalledWith("ses_main", {
      question: "现在做到哪里了？",
      messageId: "msg_remote",
      model: "provider/model"
    });
    expect(fixture.subscribe).toHaveBeenCalledOnce();
    expect(fixture.getOptions().runId).toBe("run_side_1");
    expect(fixture.getOptions().baseUrl).toBe("http://backend.test");
    expect(fixture.state.loading.value).toBe(true);
  });

  it("renders only real progress stages and appends answer deltas", async () => {
    const fixture = setup();
    await fixture.state.submit({ sessionId: "ses_main", question: "刚才做了什么？" });

    fixture.getOptions().onEvent(event("side_question.progress", { stage: "compacting" }));
    expect(fixture.state.progress.value).toBe("正在压缩较长上下文");
    fixture.getOptions().onEvent(event("side_question.progress", { stage: "tool", toolName: "read" }));
    expect(fixture.state.progress.value).toBe("正在执行只读检查");
    fixture.getOptions().onEvent(event("side_question.delta", { delta: "第一段" }));
    fixture.getOptions().onEvent(event("side_question.delta", { delta: "\n第二段" }));

    expect(fixture.state.answer.value).toBe("第一段\n第二段");
  });

  it("shows reconnect feedback without releasing the single-flight lock", async () => {
    const fixture = setup();
    await fixture.state.submit({ sessionId: "ses_main", question: "连接测试" });

    fixture.getOptions().onError?.(new Event("error"));

    expect(fixture.state.progress.value).toBe("连接中断，正在重连");
    expect(fixture.state.loading.value).toBe(true);
    await fixture.state.submit({ sessionId: "ses_main", question: "不能重复启动" });
    expect(fixture.startSideQuestionRun).toHaveBeenCalledOnce();
  });

  it("uses the succeeded terminal answer to calibrate lost deltas and releases the single-flight lock", async () => {
    const fixture = setup();
    await fixture.state.submit({ sessionId: "ses_main", question: "第一次" });
    fixture.getOptions().onEvent(event("side_question.delta", { delta: "不完整" }));
    fixture.getOptions().onEvent(event("run.succeeded", { answer: "最终完整答案" }));

    expect(fixture.state.answer.value).toBe("最终完整答案");
    expect(fixture.state.loading.value).toBe(false);
    expect(fixture.close).toHaveBeenCalledOnce();

    await fixture.state.submit({ sessionId: "ses_main", question: "第二次" });
    expect(fixture.startSideQuestionRun).toHaveBeenCalledTimes(2);
  });

  it("keeps a safe failure visible and permits editing then retrying", async () => {
    const fixture = setup();
    await fixture.state.submit({ sessionId: "ses_main", question: "第一次" });
    fixture.getOptions().onEvent(event("run.failed", {
      message: "暂时无法回答",
      command: "cat /secret",
      error: { message: "不应把内部异常直接显示" }
    }));

    expect(fixture.state.error.value).toBe("暂时无法回答");
    expect(fixture.state.loading.value).toBe(false);
    expect(fixture.close).toHaveBeenCalledOnce();

    await fixture.state.submit({ sessionId: "ses_main", question: "修改后重试" });
    expect(fixture.startSideQuestionRun).toHaveBeenCalledTimes(2);
    expect(fixture.state.error.value).toBeNull();
  });

  it("closes the side subscription on explicit close and scope disposal without any main-run abort dependency", async () => {
    const fixture = setup();
    await fixture.state.submit({ sessionId: "ses_main", question: "关闭测试" });

    fixture.state.reset();
    expect(fixture.close).toHaveBeenCalledOnce();
    expect(fixture.state.loading.value).toBe(false);
    expect(fixture.state.answer.value).toBeNull();

    await fixture.state.submit({ sessionId: "ses_main", question: "卸载测试" });
    fixture.scope.stop();
    expect(fixture.close).toHaveBeenCalledTimes(2);
  });

  it("releases an old side subscription and answer when the main session changes", async () => {
    const fixture = setup();
    await fixture.state.submit({ sessionId: "ses_main", question: "旧会话问题" });

    fixture.state.resetForSessionChange("ses_other");

    expect(fixture.close).toHaveBeenCalledOnce();
    expect(fixture.state.loading.value).toBe(false);
    expect(fixture.state.answer.value).toBeNull();
  });

  it("does not attach a late SSE subscription after the dialog closes during the start request", async () => {
    let resolveStart!: (value: { runId: string }) => void;
    const startSideQuestionRun = vi.fn(() => new Promise<{ runId: string }>((resolve) => {
      resolveStart = resolve;
    }));
    const subscribe = vi.fn();
    const scope = effectScope();
    const state = scope.run(() => useSideQuestionRun({
      api: { startSideQuestionRun },
      subscribe
    }))!;

    const pending = state.submit({ sessionId: "ses_main", question: "启动中关闭" });
    state.reset();
    resolveStart({ runId: "run_late" });
    await pending;

    expect(subscribe).not.toHaveBeenCalled();
    expect(state.loading.value).toBe(false);
  });
});
