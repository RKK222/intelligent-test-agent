export { default as WorkbenchShell } from "./WorkbenchShell.vue";
export type { WorkbenchShellProps } from "./WorkbenchShell.vue";
export {
  useWorkbenchStore,
  editorTabIsDirty,
  mockVcsDiffFiles,
  mockPublicAgentDiffs,
  mockWorkspaceAgentDiffs
} from "./workbenchStore";
export type { EditorTab, MockAgentConfigDiffFile } from "./workbenchStore";
