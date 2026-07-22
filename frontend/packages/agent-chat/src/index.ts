import "./opencode-like/styles/index.css";

export { default as AgentChat } from "./AgentChat.vue";
export type { AgentChatProps, HistoryItem } from "./AgentChat.vue";
export { default as AssistantThread } from "./AssistantThread.vue";
export type { AssistantThreadProps } from "./AssistantThread.vue";
// 作废兼容导出：AgentCard 属于旧结构化卡片主路径，新对话展示请使用 OpencodeTimeline。
export { default as AgentCard } from "./AgentCard.vue";
export { default as OpencodeTimeline } from "./opencode-like/components/OpencodeTimeline.vue";
export { default as TodoPanel } from "./opencode-like/components/TodoPanel.vue";
export { createOpencodeLikeState, createTimelineRows } from "./opencode-like";
export type {
  OpencodeLikeConversationState,
  OpencodeLikeRuntimeStatus,
  TimelineRow,
  WorkStatusEventGroup,
  WorkStatusPartRef,
  WorkStatusState
} from "./opencode-like";
export { buildComposerPromptParts, fileToPromptAttachment } from "./prompt-parts";
export type { ComposerAttachment } from "./prompt-parts";
export {
  reduceAgentChatRuntime,
  createInitialAgentChatRuntimeState,
  normalizeMessagePart,
  snapshotEventsFromRunReset,
  todoSnapshotFromMessages,
  todoSnapshotsFromMessagesByUserMessageId,
  todoSnapshotFromToolPart
} from "./runtime-reducer";
export type { AgentChatRuntimeState, AgentChatRuntimeAction } from "./runtime-reducer";
export { permissionPresentation } from "./permission-presentation";
export type { PermissionPresentation } from "./permission-presentation";
export {
  displayTextFromUserPrompt,
  promptPartsForUserDisplay,
  workspaceContextAttachmentsFromPromptParts,
  workspaceContextAttachmentsFromUserPrompt
} from "./user-message-display";
export type { UserPromptWorkspaceContextAttachment } from "./user-message-display";
export { default as MarkdownView } from "./MarkdownView.vue";
export type { MarkdownViewProps } from "./MarkdownView.vue";
