"use client";

import * as React from "react";
import { Send, Square, Terminal as TerminalIcon } from "lucide-react";
import type { TerminalTicketResponse } from "@test-agent/shared-types";
import { Badge, Button, Input } from "@test-agent/ui-kit";
import {
  createTerminalSession,
  type TerminalSnapshot,
  type TerminalSession,
  type TerminalWebSocketConstructor
} from "./terminal-client";

export type TerminalPanelProps = {
  baseUrl: string;
  createTicket: () => Promise<TerminalTicketResponse>;
  disabled?: boolean;
  disabledReason?: string;
  WebSocketCtor?: TerminalWebSocketConstructor;
};

const initialSnapshot: TerminalSnapshot = { status: "idle", output: "" };

export function TerminalPanel({ baseUrl, createTicket, disabled, disabledReason, WebSocketCtor }: TerminalPanelProps) {
  const [snapshot, setSnapshot] = React.useState<TerminalSnapshot>(initialSnapshot);
  const [command, setCommand] = React.useState("");
  const sessionRef = React.useRef<TerminalSession | null>(null);
  const connecting = snapshot.status === "connecting";
  const open = snapshot.status === "open";

  React.useEffect(() => () => sessionRef.current?.close("unmount"), []);

  async function connect() {
    if (disabled || connecting || open) {
      return;
    }
    sessionRef.current?.close("reconnect");
    setSnapshot({ status: "connecting", output: "" });
    try {
      const ticket = await createTicket();
      // ticket 只保存在当前内存会话里，WebSocket 生命周期由 terminal package 统一管理。
      const nextSession = createTerminalSession({
        baseUrl,
        ticket,
        WebSocketCtor,
        onEvent: () => setSnapshot(nextSession.snapshot())
      });
      sessionRef.current = nextSession;
      setSnapshot(nextSession.snapshot());
    } catch (error) {
      setSnapshot({
        status: "error",
        output: "",
        error: { code: "PTY_TICKET_FAILED", message: error instanceof Error ? error.message : "terminal ticket failed" }
      });
    }
  }

  function send(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = command.trim();
    if (!value || !open) {
      return;
    }
    sessionRef.current?.sendInput(`${value}\n`);
    setCommand("");
  }

  function close() {
    sessionRef.current?.close("user");
    setSnapshot(sessionRef.current?.snapshot() ?? { ...initialSnapshot, status: "closed" });
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
      <div className="flex h-10 items-center gap-2 border-b border-slate-800 bg-slate-950 px-3">
        <TerminalIcon className="h-4 w-4 text-emerald-300" />
        <div className="min-w-0 flex-1 text-[12px] font-semibold text-slate-200">终端</div>
        <Badge tone={snapshot.status === "error" ? "danger" : open ? "success" : connecting ? "info" : "neutral"}>
          {snapshot.status}
        </Badge>
        <Button size="sm" variant="secondary" disabled={disabled || connecting || open} onClick={() => void connect()}>
          连接终端
        </Button>
        <Button size="sm" variant="secondary" disabled={!open} onClick={close}>
          <Square className="h-3.5 w-3.5" />
          关闭
        </Button>
      </div>
      {disabled ? (
        <div className="border-b border-slate-800 px-3 py-2 text-[12px] text-slate-500">{disabledReason ?? "终端当前不可用"}</div>
      ) : null}
      {snapshot.error ? (
        <div className="border-b border-red-900/60 bg-red-950/30 px-3 py-2 text-[12px] text-red-100">
          {snapshot.error.code}: {snapshot.error.message}
        </div>
      ) : null}
      {snapshot.warnings?.map((warning) => (
        <div key={`${warning.code}:${warning.message}`} className="border-b border-amber-900/60 bg-amber-950/25 px-3 py-2 text-[12px] text-amber-100">
          {warning.code}: {warning.message}
        </div>
      ))}
      <pre className="min-h-0 flex-1 overflow-auto whitespace-pre-wrap p-3 font-mono text-[12px] leading-6 text-slate-300">
        {snapshot.output || "连接后显示终端输出..."}
      </pre>
      <form className="flex gap-2 border-t border-slate-800 bg-slate-950 p-3" onSubmit={send}>
        <Input value={command} onChange={(event) => setCommand(event.target.value)} disabled={!open} placeholder="输入命令" />
        <Button type="submit" variant="primary" disabled={!open || !command.trim()}>
          <Send className="h-3.5 w-3.5" />
          发送
        </Button>
      </form>
    </div>
  );
}
