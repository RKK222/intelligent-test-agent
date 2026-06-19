"use client";

import * as React from "react";
import { createBackendApiClient } from "@test-agent/backend-api";
import type { Session, SessionMessage } from "@test-agent/shared-types";
import { FeedbackBanner, type Feedback } from "@test-agent/ui-kit";

const apiBaseUrl = process.env.NEXT_PUBLIC_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";

export function ReadonlyTranscript({ sessionId }: { sessionId: string }) {
  const api = React.useMemo(() => createBackendApiClient({ baseUrl: apiBaseUrl }), []);
  const [session, setSession] = React.useState<Session | null>(null);
  const [messages, setMessages] = React.useState<SessionMessage[]>([]);
  const [feedback, setFeedback] = React.useState<Feedback | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [nextSession, page] = await Promise.all([api.getSession(sessionId), api.listSessionMessages(sessionId, 1, 200)]);
        if (!cancelled) {
          setSession(nextSession);
          setMessages(page.items);
        }
      } catch (error) {
        if (!cancelled) {
          setFeedback({
            kind: "error",
            title: "Transcript 加载失败",
            description: error instanceof Error ? error.message : "未知错误"
          });
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [api, sessionId]);

  return (
    <main className="min-h-screen bg-[var(--ta-bg)] text-slate-100">
      <section className="mx-auto flex min-h-screen w-full max-w-4xl flex-col px-4 py-6">
        <header className="border-b border-slate-800 pb-4">
          <div className="text-[12px] uppercase tracking-wide text-slate-500">Readonly transcript</div>
          <h1 className="mt-2 text-2xl font-semibold">{session?.title ?? sessionId}</h1>
          <div className="mt-2 flex flex-wrap gap-2 text-[12px] text-slate-500">
            <span>{session?.status ?? "loading"}</span>
            <span>{session?.updatedAt ? new Date(session.updatedAt).toLocaleString("zh-CN", { hour12: false }) : ""}</span>
          </div>
        </header>
        <div className="min-h-0 flex-1 space-y-3 py-4">
          {messages.map((message) => (
            <article key={message.messageId} className="rounded-md border border-slate-800 bg-slate-950 p-3">
              <div className="mb-2 flex items-center justify-between gap-2 text-[11px] text-slate-500">
                <span>{message.role}</span>
                <span>{new Date(message.createdAt).toLocaleString("zh-CN", { hour12: false })}</span>
              </div>
              <div className="whitespace-pre-wrap text-[13px] leading-6 text-slate-100">{message.content}</div>
            </article>
          ))}
          {!messages.length && !feedback ? <div className="py-12 text-center text-[12px] text-slate-500">暂无消息</div> : null}
        </div>
        <FeedbackBanner feedback={feedback} />
      </section>
    </main>
  );
}
