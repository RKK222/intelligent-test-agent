"use client";

import MonacoEditor from "@monaco-editor/react";
import { Save } from "lucide-react";
import * as React from "react";
import { Button, FeedbackBanner, type Feedback } from "@test-agent/ui-kit";
import { languageFromPath } from "./language";

export type CodeEditorProps = {
  path?: string;
  content?: string;
  dirty?: boolean;
  readonly?: boolean;
  saving?: boolean;
  feedback?: Feedback | null;
  onChange: (content: string) => void;
  onSave: () => void;
  onSelectionChange?: (selection: EditorSelectionContext | undefined) => void;
};

export type EditorSelectionContext = {
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
  text: string;
};

export function CodeEditor({ path, content = "", dirty, readonly, saving, feedback, onChange, onSave, onSelectionChange }: CodeEditorProps) {
  if (!path) {
    return (
      <div className="flex h-full min-h-0 items-center justify-center bg-[var(--ta-panel-2)] text-slate-500">
        <div className="text-center">
          <div className="text-[14px] font-semibold text-slate-300">未打开文件</div>
          <div className="mt-1 text-[12px]">从左侧文件树选择一个测试脚本或配置文件</div>
        </div>
      </div>
    );
  }
  return (
    <div className="flex h-full min-h-0 flex-col bg-[#0a1324]">
      <div className="flex h-10 items-center gap-2 border-b border-[var(--ta-border)] bg-[#0d1628] px-3">
        <div className="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{path}</div>
        {dirty ? <span className="rounded-full bg-[rgba(245,158,11,.15)] px-2 py-0.5 text-[11px] text-[#fcd34d]">未保存</span> : null}
        {readonly ? <span className="rounded-full bg-slate-800 px-2 py-0.5 text-[11px] text-slate-400">只读</span> : null}
        <Button size="sm" variant="primary" disabled={!dirty || readonly || saving} onClick={onSave}>
          <Save className="h-4 w-4" />
          {saving ? "保存中" : "保存"}
        </Button>
      </div>
      <div className="min-h-0 flex-1">
        <MonacoEditor
          path={path}
          language={languageFromPath(path)}
          theme="vs-dark"
          value={content}
          loading={<div className="p-4 text-[12px] text-slate-500">加载编辑器...</div>}
          options={{
            readOnly: readonly,
            minimap: { enabled: false },
            fontSize: 13,
            lineHeight: 21,
            scrollBeyondLastLine: false,
            automaticLayout: true,
            wordWrap: "off"
          }}
          onChange={(value) => onChange(value ?? "")}
          onMount={(editor) => {
            if (!onSelectionChange) {
              return;
            }
            const emitSelection = () => {
              const selection = editor.getSelection();
              const model = editor.getModel();
              if (!selection || !model || selection.isEmpty()) {
                onSelectionChange(undefined);
                return;
              }
              onSelectionChange({
                startLineNumber: selection.startLineNumber,
                startColumn: selection.startColumn,
                endLineNumber: selection.endLineNumber,
                endColumn: selection.endColumn,
                text: model.getValueInRange(selection)
              });
            };
            editor.onDidChangeCursorSelection(emitSelection);
            emitSelection();
          }}
        />
      </div>
      <FeedbackBanner feedback={feedback} />
    </div>
  );
}
