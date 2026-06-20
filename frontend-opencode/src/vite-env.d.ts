/// <reference types="vite/client" />

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>;
  export default component;
}

declare module "monaco-editor/esm/vs/editor/editor.api.js" {
  export * from "monaco-editor";
}
