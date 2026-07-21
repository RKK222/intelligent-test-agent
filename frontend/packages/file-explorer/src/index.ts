import "@vscode/codicons/dist/codicon.css";

export { default as FileExplorer } from "./FileExplorer.vue";
export type { FileExplorerProps, ExplorerTab } from "./FileExplorer.vue";
export { default as FileEntryCreateDialog } from "./FileEntryCreateDialog.vue";
export { default as FileEntryDeleteDialog } from "./FileEntryDeleteDialog.vue";
export { default as FileIcon } from "./FileIcon.vue";
export { getVsCodeFileIconClass, getMaterialFileIconName } from "./fileIcons";
export { filterLoadedFiles } from "./filterLoadedFiles";
export { highlightKeyword } from "./highlightKeyword";
