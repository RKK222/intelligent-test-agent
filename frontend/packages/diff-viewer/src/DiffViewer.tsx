"use client";

import { DiffEditor as MonacoDiffEditor } from "@monaco-editor/react";
import { Check, ChevronDown, ChevronUp, MessageSquareQuote, RotateCcw } from "lucide-react";
import * as React from "react";
import type { PromptPart, RunDiffFile } from "@test-agent/shared-types";
import { Badge, Button, FeedbackBanner, type Feedback, cn } from "@test-agent/ui-kit";
import { hunkToPromptPart, parseDiffHunks, selectAdjacentHunk, type DiffHunk } from "./hunks";
import { parseUnifiedPatch } from "./unifiedPatch";

export type DiffViewerProps = {
  files: RunDiffFile[];
  selectedPath?: string;
  source?: "run" | "session" | "vcs";
  viewMode?: "split" | "unified";
  accepting?: boolean;
  rejecting?: boolean;
  feedback?: Feedback | null;
  onSelectFile: (path: string) => void;
  onSourceChange?: (source: "run" | "session" | "vcs") => void;
  onViewModeChange?: (mode: "split" | "unified") => void;
  onRefresh?: () => void;
  onAcceptRun: () => void;
  onRejectRun: () => void;
  onCurrentFileFeedback: (action: "accept-current" | "reject-current", path: string) => void;
  onUseHunkContext?: (part: Extract<PromptPart, { type: "file" }>) => void;
};

export function DiffViewer({
  files,
  selectedPath,
  source = "run",
  viewMode = "split",
  accepting,
  rejecting,
  feedback,
  onSelectFile,
  onSourceChange,
  onViewModeChange,
  onRefresh,
  onAcceptRun,
  onRejectRun,
  onCurrentFileFeedback,
  onUseHunkContext
}: DiffViewerProps) {
  const selected = React.useMemo(() => files.find((file) => file.path === selectedPath) ?? files[0], [files, selectedPath]);
  const parsed = React.useMemo(() => parseUnifiedPatch(selected?.patch ?? ""), [selected?.patch]);
  const hunks = React.useMemo(() => (selected ? parseDiffHunks(selected) : []), [selected]);
  const [selectedHunkIndex, setSelectedHunkIndex] = React.useState(0);
  const selectedHunk = hunks[selectedHunkIndex] ?? hunks[0];
  const diffEditorRef = React.useRef<MinimalDiffEditor | null>(null);

  React.useEffect(() => {
    setSelectedHunkIndex(0);
  }, [selected?.path, selected?.patch]);

  React.useEffect(() => {
    if (selectedHunkIndex >= hunks.length) {
      setSelectedHunkIndex(0);
    }
  }, [hunks.length, selectedHunkIndex]);

  React.useEffect(() => {
    if (!selectedHunk || !diffEditorRef.current) {
      return;
    }
    const modified = diffEditorRef.current.getModifiedEditor();
    const lineNumber = Math.max(1, selectedHunk.newStart);
    modified.revealLineInCenter(lineNumber);
    modified.setPosition({ lineNumber, column: 1 });
  }, [selectedHunk]);

  function moveHunk(direction: "previous" | "next") {
    const next = selectAdjacentHunk(hunks, selectedHunkIndex, direction);
    if (next) {
      setSelectedHunkIndex(next.index);
    }
  }

  if (!files.length) {
    return (
      <div className="flex h-full min-h-0 flex-col bg-[var(--ta-panel-2)] text-slate-500">
        <DiffToolbar
          source={source}
          viewMode={viewMode}
          onSourceChange={onSourceChange}
          onViewModeChange={onViewModeChange}
          onRefresh={onRefresh}
        />
        <div className="flex flex-1 items-center justify-center text-center text-[12px]">暂无 Diff</div>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--ta-panel-2)]">
      <div className="flex min-h-10 flex-wrap items-center gap-2 border-b border-slate-800 bg-slate-950 px-3 py-1">
        <DiffToolbar
          source={source}
          viewMode={viewMode}
          onSourceChange={onSourceChange}
          onViewModeChange={onViewModeChange}
          onRefresh={onRefresh}
        />
        <div className="min-w-0 flex-1 truncate text-[12px] font-semibold text-slate-200">{sourceTitle(source)}</div>
        <HunkToolbar
          hunks={hunks}
          selectedHunk={selectedHunk}
          onPrevious={() => moveHunk("previous")}
          onNext={() => moveHunk("next")}
          onUseContext={() => selected && selectedHunk && onUseHunkContext?.(hunkToPromptPart(selected, selectedHunk))}
          canUseContext={Boolean(onUseHunkContext && selected && selectedHunk)}
        />
        <Button size="sm" variant="secondary" disabled={!selected} onClick={() => selected && onCurrentFileFeedback("accept-current", selected.path)}>
          当前文件接受
        </Button>
        <Button size="sm" variant="secondary" disabled={!selected} onClick={() => selected && onCurrentFileFeedback("reject-current", selected.path)}>
          当前文件拒绝
        </Button>
        <Button size="sm" variant="primary" disabled={accepting} onClick={onAcceptRun}>
          <Check className="h-4 w-4" />
          接受全部
        </Button>
        <Button size="sm" variant="danger" disabled={rejecting} onClick={onRejectRun}>
          <RotateCcw className="h-4 w-4" />
          拒绝全部
        </Button>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-[260px_minmax(0,1fr)]">
        <div className="min-h-0 overflow-auto border-r border-slate-800 bg-[var(--ta-panel)] p-2">
          {files.map((file) => (
            <button
              key={file.path}
              type="button"
              className={cn(
                "mb-1 flex w-full items-center gap-2 rounded-md border border-transparent px-2 py-2 text-left hover:bg-[#13203f]",
                selected?.path === file.path && "border-[#1d4ed8] bg-[rgba(96,165,250,.12)]"
              )}
              onClick={() => onSelectFile(file.path)}
            >
              <Badge tone={file.status === "deleted" ? "danger" : file.status === "added" ? "success" : "warning"}>{file.status}</Badge>
              <span className="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{file.path}</span>
            </button>
          ))}
          {hunks.length ? (
            <div className="mt-3 border-t border-slate-800 pt-2">
              <div className="mb-1 px-1 text-[11px] font-semibold uppercase text-slate-500">Hunks</div>
              {hunks.map((hunk) => (
                <button
                  key={`${hunk.filePath}:${hunk.index}`}
                  type="button"
                  className={cn(
                    "mb-1 w-full rounded-md border border-transparent px-2 py-1.5 text-left text-[11px] hover:bg-[#13203f]",
                    selectedHunk?.index === hunk.index && "border-[rgba(34,211,238,.4)] bg-[rgba(34,211,238,.08)]"
                  )}
                  onClick={() => setSelectedHunkIndex(hunk.index)}
                >
                  <div className="font-mono text-slate-200">+{hunk.newStart},{hunk.newLines}</div>
                  <div className="truncate text-slate-500">{hunk.heading || hunk.patch.split("\n")[0]}</div>
                </button>
              ))}
            </div>
          ) : null}
        </div>
        <div className="min-w-0">
          <MonacoDiffEditor
            original={parsed.original}
            modified={parsed.modified}
            language={selected?.path.endsWith(".py") ? "python" : "typescript"}
            theme="vs-dark"
            loading={<div className="p-4 text-[12px] text-slate-500">加载 Diff...</div>}
            options={{
              readOnly: true,
              renderSideBySide: viewMode === "split",
              minimap: { enabled: false },
              automaticLayout: true,
              scrollBeyondLastLine: false,
              fontSize: 13,
              lineHeight: 21
            }}
            onMount={(editor) => {
              diffEditorRef.current = editor as MinimalDiffEditor;
            }}
          />
        </div>
      </div>
      <FeedbackBanner feedback={feedback} />
    </div>
  );
}

type MinimalDiffEditor = {
  getModifiedEditor: () => {
    revealLineInCenter: (lineNumber: number) => void;
    setPosition: (position: { lineNumber: number; column: number }) => void;
  };
};

function HunkToolbar({
  hunks,
  selectedHunk,
  canUseContext,
  onPrevious,
  onNext,
  onUseContext
}: {
  hunks: DiffHunk[];
  selectedHunk?: DiffHunk;
  canUseContext: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onUseContext: () => void;
}) {
  return (
    <div className="flex items-center gap-1">
      <Button type="button" size="icon" variant="secondary" title="上一处 hunk" disabled={hunks.length === 0} onClick={onPrevious}>
        <ChevronUp className="h-4 w-4" />
      </Button>
      <Button type="button" size="icon" variant="secondary" title="下一处 hunk" disabled={hunks.length === 0} onClick={onNext}>
        <ChevronDown className="h-4 w-4" />
      </Button>
      <span className="min-w-[52px] text-center font-mono text-[11px] text-slate-500">
        {selectedHunk ? `${selectedHunk.index + 1}/${hunks.length}` : "0/0"}
      </span>
      <Button type="button" size="icon" variant="secondary" title="引用 hunk" disabled={!canUseContext} onClick={onUseContext}>
        <MessageSquareQuote className="h-4 w-4" />
      </Button>
    </div>
  );
}

function DiffToolbar({
  source,
  viewMode,
  onSourceChange,
  onViewModeChange,
  onRefresh
}: {
  source: "run" | "session" | "vcs";
  viewMode: "split" | "unified";
  onSourceChange?: (source: "run" | "session" | "vcs") => void;
  onViewModeChange?: (mode: "split" | "unified") => void;
  onRefresh?: () => void;
}) {
  return (
    <div className="flex items-center gap-1">
      <select
        value={source}
        className="h-8 rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200"
        onChange={(event) => onSourceChange?.(event.target.value as "run" | "session" | "vcs")}
      >
        <option value="run">Run</option>
        <option value="session">Session</option>
        <option value="vcs">VCS</option>
      </select>
      <select
        value={viewMode}
        className="h-8 rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200"
        onChange={(event) => onViewModeChange?.(event.target.value as "split" | "unified")}
      >
        <option value="split">Split</option>
        <option value="unified">Unified</option>
      </select>
      <Button size="sm" variant="secondary" onClick={onRefresh}>
        刷新
      </Button>
    </div>
  );
}

function sourceTitle(source: "run" | "session" | "vcs") {
  if (source === "session") {
    return "Session Diff";
  }
  if (source === "vcs") {
    return "VCS Diff";
  }
  return "Run Diff";
}
