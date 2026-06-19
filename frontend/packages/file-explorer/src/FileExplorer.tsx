"use client";

import { ChevronRight, FileText, Folder, RefreshCw, Search } from "lucide-react";
import * as React from "react";
import type { FileStatus, FileTreeEntry, RunDiffFile } from "@test-agent/shared-types";
import { Badge, Button, Input, SegmentedTabs, cn } from "@test-agent/ui-kit";
import { filterLoadedFiles } from "./filterLoadedFiles";

export type FileExplorerProps = {
  workspaceName?: string;
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedDirectories: Set<string>;
  activePath?: string;
  changedFiles: RunDiffFile[];
  statuses?: Record<string, FileStatus>;
  loadingPath?: string | null;
  onToggleDirectory: (path: string) => void;
  onOpenFile: (path: string) => void;
  onOpenDiff: (path: string) => void;
  onRefresh: () => void;
};

type ExplorerTab = "explorer" | "search" | "changes";

export function FileExplorer({
  workspaceName = "Workspace",
  entriesByDirectory,
  expandedDirectories,
  activePath,
  changedFiles,
  loadingPath,
  onToggleDirectory,
  onOpenFile,
  onOpenDiff,
  onRefresh
}: FileExplorerProps) {
  const [tab, setTab] = React.useState<ExplorerTab>("explorer");
  const [keyword, setKeyword] = React.useState("");
  const searchResults = React.useMemo(() => filterLoadedFiles(entriesByDirectory, keyword), [entriesByDirectory, keyword]);

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
      <div className="flex h-9 items-center justify-between border-b border-slate-800 px-2">
        <div className="min-w-0 truncate text-[12px] font-semibold text-slate-200">{workspaceName}</div>
        <Button size="icon" variant="ghost" title="刷新文件树" onClick={onRefresh}>
          <RefreshCw className="h-4 w-4" />
        </Button>
      </div>
      <SegmentedTabs
        value={tab}
        onValueChange={setTab}
        items={[
          { id: "explorer", label: "工作空间" },
          { id: "search", label: "搜索" },
          { id: "changes", label: "变更", count: changedFiles.length }
        ]}
      />
      {tab === "explorer" ? (
        <div className="min-h-0 flex-1 overflow-auto p-2 font-mono text-[12px]">
          <DirectoryRows
            directory=""
            entriesByDirectory={entriesByDirectory}
            expandedDirectories={expandedDirectories}
            activePath={activePath}
            loadingPath={loadingPath}
            onToggleDirectory={onToggleDirectory}
            onOpenFile={onOpenFile}
            depth={0}
          />
        </div>
      ) : null}
      {tab === "search" ? (
        <div className="min-h-0 flex-1 overflow-auto p-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2 top-2 h-4 w-4 text-slate-500" />
            <Input className="pl-7" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="过滤已加载文件名" />
          </div>
          <div className="mt-2 space-y-1">
            {searchResults.map((entry) => (
              <button
                key={entry.path}
                type="button"
                className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left font-mono text-[12px] text-slate-300 hover:bg-slate-800"
                onClick={() => onOpenFile(entry.path)}
              >
                <FileText className="h-4 w-4 text-slate-500" />
                <span className="min-w-0 truncate">{entry.path}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}
      {tab === "changes" ? (
        <div className="min-h-0 flex-1 overflow-auto p-2">
          <div className="space-y-1">
            {changedFiles.map((file) => (
              <button
                key={file.path}
                type="button"
                className="flex w-full items-center gap-2 rounded-md border border-slate-800 bg-slate-950 px-2 py-2 text-left hover:border-slate-600"
                onClick={() => onOpenDiff(file.path)}
              >
                <Badge tone={file.status === "deleted" ? "danger" : file.status === "added" ? "success" : "warning"}>{file.status}</Badge>
                <span className="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{file.path}</span>
                <span className="text-[11px] text-emerald-300">+{file.additions}</span>
                <span className="text-[11px] text-red-300">-{file.deletions}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function DirectoryRows({
  directory,
  entriesByDirectory,
  expandedDirectories,
  activePath,
  loadingPath,
  onToggleDirectory,
  onOpenFile,
  depth
}: {
  directory: string;
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedDirectories: Set<string>;
  activePath?: string;
  loadingPath?: string | null;
  onToggleDirectory: (path: string) => void;
  onOpenFile: (path: string) => void;
  depth: number;
}) {
  const entries = entriesByDirectory[directory] ?? [];
  return (
    <div>
      {entries.map((entry) => {
        const expanded = expandedDirectories.has(entry.path);
        const active = activePath === entry.path;
        return (
          <div key={entry.path}>
            <button
              type="button"
              className={cn(
                "flex h-7 w-full items-center gap-1 rounded-md px-1 text-left text-slate-300 hover:bg-slate-800",
                active && "bg-blue-950 text-white"
              )}
              style={{ paddingLeft: depth * 14 + 4 }}
              onClick={() => (entry.type === "directory" ? onToggleDirectory(entry.path) : onOpenFile(entry.path))}
            >
              {entry.type === "directory" ? (
                <>
                  <ChevronRight className={cn("h-3.5 w-3.5 text-slate-500 transition", expanded && "rotate-90")} />
                  <Folder className="h-4 w-4 text-blue-300" />
                </>
              ) : (
                <>
                  <span className="w-3.5" />
                  <FileText className="h-4 w-4 text-slate-500" />
                </>
              )}
              <span className="min-w-0 flex-1 truncate">{entry.name}</span>
              {loadingPath === entry.path ? <span className="text-[10px] text-slate-500">...</span> : null}
            </button>
            {entry.type === "directory" && expanded ? (
              <DirectoryRows
                directory={entry.path}
                entriesByDirectory={entriesByDirectory}
                expandedDirectories={expandedDirectories}
                activePath={activePath}
                loadingPath={loadingPath}
                onToggleDirectory={onToggleDirectory}
                onOpenFile={onOpenFile}
                depth={depth + 1}
              />
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
