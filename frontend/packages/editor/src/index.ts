export { default as CodeEditor } from "./CodeEditor.vue";
export type { CodeEditorProps, CodeEditorEmits, EditorSelectionContext } from "./CodeEditor.vue";
export { default as MermaidEditorDialog } from "./mermaid/visual-editor/MermaidEditorDialog.vue";
export { ensureMermaid } from "./mermaid/init";
export {
  parseMermaidDiagram,
  serializeMermaidDiagram,
  cloneMermaidDiagram,
  type MermaidEditableDiagram
} from "./mermaid/diagram";
export type {
  MermaidEdge,
  MermaidEdgeStyle,
  MermaidNode,
  MermaidNodeStyle,
  MermaidNodeType
} from "./mermaid/model";
export { languageFromPath } from "./language";

