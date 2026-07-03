import type {
  AgentMessage,
  MessagePart,
  ModelInfo,
  PermissionRequest,
  ProviderInfo,
  QuestionRequest,
  RunDiffFile,
  SessionDiff,
  TodoItem
} from "@test-agent/shared-types";

export type OpencodeLikeRuntimeStatus = {
  type: "idle" | "busy" | "retry" | "failed" | "cancelled" | string;
  attempt?: number;
  message?: string;
};

export type ModelCatalog = {
  providersById: Record<string, ProviderInfo>;
  modelsByKey: Record<string, ModelInfo & { providerId?: string }>;
};

export type OpencodeLikeConversationInput = {
  messages: AgentMessage[];
  providers?: ProviderInfo[];
  models?: ModelInfo[];
  permissions?: PermissionRequest[];
  questions?: QuestionRequest[];
  todos?: TodoItem[];
  diff?: SessionDiff;
  diffFiles?: RunDiffFile[];
  running?: boolean;
  status?: string;
  runtimeStatus?: OpencodeLikeRuntimeStatus;
  streamingTextByPartId?: Record<string, string>;
  showReasoningSummaries?: boolean;
};

export type OpencodeLikeConversationState = {
  messages: AgentMessage[];
  messageById: Record<string, AgentMessage>;
  userMessages: Extract<AgentMessage, { role: "user" }>[];
  orphanAssistantMessages: Extract<AgentMessage, { role: "assistant" }>[];
  assistantMessagesByParent: Record<string, Extract<AgentMessage, { role: "assistant" }>[]>;
  partsByMessageId: Record<string, MessagePart[]>;
  streamingTextByPartId: Record<string, string>;
  modelCatalog: ModelCatalog;
  runtimeStatus: OpencodeLikeRuntimeStatus;
  diffFiles: RunDiffFile[];
  permissions: PermissionRequest[];
  questions: QuestionRequest[];
  todos: TodoItem[];
  running: boolean;
  showReasoningSummaries: boolean;
};

export type TimelineRow =
  | { type: "turn-gap"; key: string; userMessageId: string }
  | { type: "user-message"; key: string; userMessageId: string }
  | {
      type: "context-tool-group";
      key: string;
      userMessageId: string;
      messageId: string;
      refs: Array<{ messageId: string; partId: string }>;
      busy: boolean;
      previousAssistantPart: boolean;
    }
  | {
      type: "assistant-part";
      key: string;
      userMessageId: string;
      messageId: string;
      partId: string;
      previousAssistantPart: boolean;
    }
  | { type: "thinking"; key: string; userMessageId: string }
  | { type: "retry"; key: string; userMessageId: string; attempt?: number }
  | { type: "diff-summary"; key: string; userMessageId: string; files: RunDiffFile[] }
  | { type: "error"; key: string; message: string };

export type RenderablePartGroup =
  | { type: "part"; partId: string }
  | { type: "context-tool-group"; key: string; refs: Array<{ partId: string }> };
