"use client";

import * as React from "react";
import type { AgentMessage } from "@test-agent/shared-types";
import { Button, SegmentedTabs, Textarea } from "@test-agent/ui-kit";
import { AgentCard } from "./cards";

export type AgentChatProps = {
  messages: AgentMessage[];
  history: { id: string; title: string; preview: string; status: string; updatedAt: string }[];
  running?: boolean;
  onSend: (prompt: string) => void;
  onOpenDiff: () => void;
  onRetry: () => void;
  onCancel: () => void;
};

type AgentTab = "agent" | "history";

export function AgentChat({ messages, history, running, onSend, onOpenDiff, onRetry, onCancel }: AgentChatProps) {
  const [tab, setTab] = React.useState<AgentTab>("agent");
  const [text, setText] = React.useState("");

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const prompt = text.trim();
    if (!prompt) {
      return;
    }
    onSend(prompt);
    setText("");
    setTab("agent");
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--ta-panel-2)]">
      <SegmentedTabs
        value={tab}
        onValueChange={setTab}
        items={[
          { id: "agent", label: "Agent" },
          { id: "history", label: "历史", count: history.length }
        ]}
      />
      {tab === "agent" ? (
        <>
          <div className="min-h-0 flex-1 space-y-3 overflow-auto p-3">
            {messages.map((message) =>
              message.role === "card" ? (
                <AgentCard key={message.id} message={message} onOpenDiff={onOpenDiff} />
              ) : (
                <div key={message.id} className={message.role === "user" ? "flex justify-end" : "flex justify-start"}>
                  <div className={message.role === "user" ? "max-w-[92%] rounded-md border border-blue-900 bg-blue-950 px-3 py-2" : "max-w-[92%] rounded-md border border-slate-800 bg-slate-950 px-3 py-2"}>
                    <div className="mb-1 text-[11px] text-slate-500">{message.role === "user" ? "You" : "Agent"}</div>
                    <div className="whitespace-pre-wrap text-[12px] leading-6 text-slate-100">{message.text}</div>
                  </div>
                </div>
              )
            )}
          </div>
          <form className="border-t border-slate-800 bg-slate-950 p-3" onSubmit={submit}>
            <Textarea
              value={text}
              rows={3}
              placeholder="描述测试任务，例如：跑 checkout 模块并分析失败原因"
              onChange={(event) => setText(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  event.currentTarget.form?.requestSubmit();
                }
              }}
            />
            <div className="mt-2 flex items-center justify-between gap-2">
              <div className="text-[11px] text-slate-500">{running ? "Run 正在执行" : "Enter 发送"}</div>
              <div className="flex gap-2">
                <Button type="button" size="sm" variant="secondary" disabled={!running} onClick={onCancel}>
                  取消
                </Button>
                <Button type="button" size="sm" variant="secondary" onClick={onRetry}>
                  重试
                </Button>
                <Button type="submit" size="sm" variant="primary" disabled={running || !text.trim()}>
                  发送
                </Button>
              </div>
            </div>
          </form>
        </>
      ) : (
        <div className="min-h-0 flex-1 space-y-2 overflow-auto p-3">
          {history.map((item) => (
            <button key={item.id} type="button" className="w-full rounded-md border border-slate-800 bg-slate-950 p-3 text-left hover:border-slate-600">
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300">{item.status}</span>
                <span className="min-w-0 flex-1 truncate text-[12px] font-semibold text-slate-100">{item.title}</span>
                <span className="text-[11px] text-slate-500">{item.updatedAt}</span>
              </div>
              <div className="mt-1 truncate text-[12px] text-slate-500">{item.preview}</div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
