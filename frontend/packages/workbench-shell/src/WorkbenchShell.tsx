"use client";

import { DockviewReact, type DockviewReadyEvent, type IDockviewPanelProps } from "dockview";
import { Activity, GitBranch, PlayCircle } from "lucide-react";
import * as React from "react";
import { Badge, cn } from "@test-agent/ui-kit";

export type WorkbenchShellProps = {
  workspaceName: string;
  branchName: string;
  runStatus?: string;
  left: React.ReactNode;
  center: React.ReactNode;
  right: React.ReactNode;
  bottom: React.ReactNode;
};

type PanelParams = {
  slot: "left" | "center" | "right" | "bottom";
};

type WorkbenchSlots = Pick<WorkbenchShellProps, "left" | "center" | "right" | "bottom">;

const WorkbenchSlotsContext = React.createContext<WorkbenchSlots>({
  left: null,
  center: null,
  right: null,
  bottom: null
});

function DockPanel(props: IDockviewPanelProps<PanelParams>) {
  const slots = React.useContext(WorkbenchSlotsContext);
  return <div className="h-full min-h-0 overflow-hidden">{slots[props.params.slot]}</div>;
}

export function WorkbenchShell({ workspaceName, branchName, runStatus = "IDLE", left, center, right, bottom }: WorkbenchShellProps) {
  const slots = React.useMemo(() => ({ left, center, right, bottom }), [left, center, right, bottom]);
  const components = React.useMemo(() => ({ panel: DockPanel }), []);
  const initialized = React.useRef(false);

  const onReady = React.useCallback((event: DockviewReadyEvent) => {
    if (initialized.current) {
      return;
    }
    initialized.current = true;
    event.api.addPanel({ id: "editor", title: "编辑器", component: "panel", params: { slot: "center" } });
    event.api.addPanel({
      id: "workspace",
      title: "工作空间",
      component: "panel",
      params: { slot: "left" },
      position: { referencePanel: "editor", direction: "left" },
      initialWidth: 280
    });
    event.api.addPanel({
      id: "agent",
      title: "Agent",
      component: "panel",
      params: { slot: "right" },
      position: { referencePanel: "editor", direction: "right" },
      initialWidth: 420
    });
    event.api.addPanel({
      id: "run",
      title: "运行",
      component: "panel",
      params: { slot: "bottom" },
      position: { referencePanel: "editor", direction: "below" },
      initialHeight: 180
    });
  }, []);

  return (
    <div
      className="min-h-0 bg-[var(--ta-bg)] text-[var(--ta-text)]"
      style={{ display: "grid", gridTemplateRows: "44px minmax(0, 1fr)", height: "100vh" }}
    >
      <header className="flex items-center justify-between border-b border-[var(--ta-border)] bg-[linear-gradient(180deg,#0f1a33,#0b1220)] px-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="h-3.5 w-3.5 rounded-[4px] bg-[conic-gradient(from_0deg,#60a5fa,#22d3ee,#a78bfa,#60a5fa)] shadow-[0_0_0_2px_rgba(96,165,250,.15)]" />
            <span className="font-semibold">TestAgent IDE</span>
          </div>
          <div className="hidden min-w-0 items-center gap-2 md:flex">
            <Badge tone="info">{workspaceName}</Badge>
            <span className="flex items-center gap-1 text-[12px] text-slate-400">
              <GitBranch className="h-3.5 w-3.5" />
              {branchName}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={cn(
              "flex items-center gap-1 rounded-full border border-[var(--ta-border)] bg-[#101b33] px-2 py-1 text-[12px] text-[var(--ta-muted)]",
              runStatus === "RUNNING" && "border-[#1b2d5a] text-[#8db6f5] shadow-[0_0_10px_rgba(96,165,250,.4)]",
              runStatus === "FAILED" && "border-[#3b0d0d] text-[#fca5a5]",
              runStatus === "SUCCEEDED" && "border-[#0b2a1a] text-[#86efac]"
            )}
          >
            {runStatus === "RUNNING" ? (
              <Activity className="h-3.5 w-3.5 animate-[ta-pulse_1.2s_infinite]" />
            ) : (
              <PlayCircle className="h-3.5 w-3.5" />
            )}
            {runStatus}
          </span>
        </div>
      </header>
      <main className="relative min-h-0 overflow-hidden" style={{ height: "100%" }}>
        <WorkbenchSlotsContext.Provider value={slots}>
          <DockviewReact components={components} onReady={onReady} />
        </WorkbenchSlotsContext.Provider>
      </main>
    </div>
  );
}
