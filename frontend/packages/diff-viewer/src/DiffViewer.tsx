"use client";

import { DiffEditor as MonacoDiffEditor } from "@monaco-editor/react";
import { Check, RotateCcw } from "lucide-react";
import * as React from "react";
import type { RunDiffFile } from "@test-agent/shared-types";
import { Badge, Button, FeedbackBanner, type Feedback, cn } from "@test-agent/ui-kit";
import { parseUnifiedPatch } from "./unifiedPatch";

export type DiffViewerProps = {
  files: RunDiffFile[];
  selectedPath?: string;
  accepting?: boolean;
  rejecting?: boolean;
  feedback?: Feedback | null;
  onSelectFile: (path: string) => void;
  onAcceptRun: () => void;
  onRejectRun: () => void;
  onCurrentFileFeedback: (action: "accept-current" | "reject-current", path: string) => void;
};

export function DiffViewer({
  files,
  selectedPath,
  accepting,
  rejecting,
  feedback,
  onSelectFile,
  onAcceptRun,
  onRejectRun,
  onCurrentFileFeedback
}: DiffViewerProps) {
  const selected = React.useMemo(() => files.find((file) => file.path === selectedPath) ?? files[0], [files, selectedPath]);
  const parsed = React.useMemo(() => parseUnifiedPatch(selected?.patch ?? ""), [selected?.patch]);

  if (!files.length) {
    return (
      <div className="flex h-full min-h-0 items-center justify-center bg-[var(--ta-panel-2)] text-slate-500">
        <div className="text-center text-[12px]">暂无 Diff</div>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--ta-panel-2)]">
      <div className="flex h-10 items-center gap-2 border-b border-slate-800 bg-slate-950 px-3">
        <div className="min-w-0 flex-1 truncate text-[12px] font-semibold text-slate-200">Run Diff</div>
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
                "mb-1 flex w-full items-center gap-2 rounded-md border border-transparent px-2 py-2 text-left hover:bg-slate-800",
                selected?.path === file.path && "border-blue-900 bg-blue-950/60"
              )}
              onClick={() => onSelectFile(file.path)}
            >
              <Badge tone={file.status === "deleted" ? "danger" : file.status === "added" ? "success" : "warning"}>{file.status}</Badge>
              <span className="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{file.path}</span>
            </button>
          ))}
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
              renderSideBySide: true,
              minimap: { enabled: false },
              automaticLayout: true,
              scrollBeyondLastLine: false,
              fontSize: 13,
              lineHeight: 21
            }}
          />
        </div>
      </div>
      <FeedbackBanner feedback={feedback} />
    </div>
  );
}
