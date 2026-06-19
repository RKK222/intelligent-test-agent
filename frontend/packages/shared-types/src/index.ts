export type ApiSuccess<T> = {
  success: true;
  traceId: string;
  data: T;
};

export type ApiFailure = {
  success: false;
  traceId: string;
  code: string;
  message: string;
  retryable?: boolean;
  details?: Record<string, unknown>;
};

export type ApiResponse<T> = ApiSuccess<T> | ApiFailure;

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
};

export type Workspace = {
  workspaceId: string;
  name: string;
  rootPath: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type FileTreeEntry = {
  path: string;
  name: string;
  type: "file" | "directory";
  size?: number;
  modifiedAt?: string;
};

export type FileContent = {
  path: string;
  content: string;
  encoding?: string;
  size?: number;
  readonly?: boolean;
};

export type FileStatus = {
  path: string;
  exists?: boolean;
  directory?: boolean;
  size?: number;
  lastModifiedAt?: string;
  status: "added" | "modified" | "deleted" | "unchanged" | string;
};

export type Session = {
  sessionId: string;
  workspaceId: string;
  title: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type Run = {
  runId: string;
  sessionId: string;
  workspaceId: string;
  status: "PENDING" | "RUNNING" | "CANCELLING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | string;
  createdAt: string;
  updatedAt: string;
};

export type RunEventType =
  | "run.created"
  | "run.started"
  | "run.cancelling"
  | "run.succeeded"
  | "run.failed"
  | "run.cancelled"
  | "assistant.message.delta"
  | "tool.started"
  | "tool.finished"
  | "diff.proposed"
  | "diff.accepted"
  | "diff.rejected"
  | "test.finished"
  | "opencode.event.unknown"
  | string;

export type RunEvent = {
  eventId: string;
  runId: string;
  seq: number;
  type: RunEventType;
  traceId: string;
  occurredAt: string;
  payload: Record<string, unknown>;
};

export type RunDiffFile = {
  path: string;
  patch: string;
  additions: number;
  deletions: number;
  status: string;
};

export type RunDiff = {
  runId: string;
  files: RunDiffFile[];
};

export type RunDiffAction = {
  runId: string;
  action: "accept" | "reject" | string;
  status: "accepted" | "rejected" | string;
  fileCount: number;
};

export type AgentMessage =
  | { id: string; role: "user"; text: string; createdAt: string }
  | { id: string; role: "assistant"; text: string; createdAt: string }
  | {
      id: string;
      role: "card";
      cardType: "plan" | "tool" | "test" | "diff" | "event";
      title: string;
      payload: Record<string, unknown>;
      createdAt: string;
    };
