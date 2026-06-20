import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import type * as Monaco from "monaco-editor";

export type MonacoModule = typeof Monaco;

let workerConfigured = false;

export async function loadMonacoEditor(): Promise<MonacoModule> {
  configureMonacoWorker();
  return import("monaco-editor/esm/vs/editor/editor.api.js") as Promise<MonacoModule>;
}

function configureMonacoWorker() {
  if (workerConfigured || typeof globalThis === "undefined") {
    return;
  }
  const target = globalThis as typeof globalThis & {
    MonacoEnvironment?: {
      getWorker?: () => Worker;
    };
  };
  target.MonacoEnvironment ??= {};
  // Monaco 在 Vite 中需要显式 worker，Review 面板只用基础 editor worker。
  target.MonacoEnvironment.getWorker ??= () => new EditorWorker();
  workerConfigured = true;
}
