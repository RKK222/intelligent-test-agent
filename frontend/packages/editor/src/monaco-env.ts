/**
 * Monaco Editor 动态加载模块
 *
 * Workers 使用 ?worker 语法导入，Vite 会自动将它们拆分为独立 chunks。
 * Monaco 主模块使用动态导入，只在需要时加载。
 */

import type * as Monaco from "monaco-editor";

// Workers 使用静态导入（?worker 语法会自动创建独立 chunk）
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
import CssWorker from "monaco-editor/esm/vs/language/css/css.worker?worker";
import HtmlWorker from "monaco-editor/esm/vs/language/html/html.worker?worker";
import TsWorker from "monaco-editor/esm/vs/language/typescript/ts.worker?worker";

let monacoInstance: typeof Monaco | null = null;
let initialized = false;

export async function loadMonaco(): Promise<typeof Monaco> {
  if (monacoInstance) {
    return monacoInstance;
  }

  // 动态导入 Monaco Editor 核心（这部分会被单独 chunk）
  const monaco = await import("monaco-editor/esm/vs/editor/editor.main.js");
  monacoInstance = monaco as typeof Monaco;

  // 只初始化一次 Worker 环境
  if (!initialized) {
    (self as unknown as { MonacoEnvironment: Monaco.Environment }).MonacoEnvironment = {
      getWorker(_workerId, label) {
        switch (label) {
          case "json":
            return new JsonWorker();
          case "css":
          case "scss":
          case "less":
            return new CssWorker();
          case "html":
          case "handlebars":
          case "razor":
            return new HtmlWorker();
          case "typescript":
          case "javascript":
            return new TsWorker();
          default:
            return new EditorWorker();
        }
      }
    };
    initialized = true;
  }

  return monacoInstance;
}

export function getMonaco(): typeof Monaco | null {
  return monacoInstance;
}
