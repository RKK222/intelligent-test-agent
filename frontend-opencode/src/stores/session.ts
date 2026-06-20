import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { MessagePart, PermissionRequest, QuestionRequest, Run, Session, SessionDiff, SessionMessage, TodoItem } from "@test-agent/shared-types";
import { usePlatformStore } from "@/stores/platform";
import { usePromptStore } from "@/stores/prompt";
import { useRunEventStore } from "@/stores/runEvents";
import { buildPromptParts, promptPreviewTitle, type PromptBuildInput } from "@/utils/prompt";

export type FollowupItem = { id: string; text: string };
export type RevertItem = { id: string; text: string };

export const useSessionStore = defineStore("session", () => {
  const activeSession = ref<Session>();
  const messages = ref<SessionMessage[]>([]);
  const activeRun = ref<Run>();
  const diff = ref<SessionDiff>();
  const todos = ref<TodoItem[]>([]);
  const permissions = ref<PermissionRequest[]>([]);
  const questions = ref<QuestionRequest[]>([]);
  const followups = ref<FollowupItem[]>([]);
  const revertItems = ref<RevertItem[]>([]);
  const shareUrl = ref<string>();
  const loading = ref(false);
  const sending = ref(false);
  const sharing = ref(false);
  const error = ref<string>();
  const shareError = ref<string>();

  const timeline = computed(() => {
    const runEvents = useRunEventStore();
    const projected = runEvents.timelineMessages.map((message) => ({
      messageId: message.messageId,
      sessionId: message.sessionId ?? activeSession.value?.sessionId ?? "",
      role: message.role.toUpperCase(),
      content: renderParts(message.parts),
      createdAt: message.updatedAt ?? new Date(0).toISOString()
    }));
    return [...messages.value, ...projected].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  });

  async function load(sessionId: string) {
    const platform = usePlatformStore();
    loading.value = true;
    error.value = undefined;
    try {
      const [session, messagePage, todoItems, sessionDiff, permissionItems, questionItems] = await Promise.all([
        platform.api.getSession(sessionId),
        platform.api.listSessionMessages(sessionId, 1, 200),
        platform.api.getSessionTodo(sessionId),
        platform.api.getSessionDiff(sessionId),
        platform.api.listSessionPermissions(sessionId),
        platform.api.listSessionQuestions(sessionId)
      ]);
      activeSession.value = session;
      shareUrl.value = extractShareUrl(session);
      messages.value = messagePage.items;
      todos.value = todoItems;
      diff.value = sessionDiff;
      permissions.value = permissionItems;
      questions.value = questionItems;
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : "会话加载失败";
    } finally {
      loading.value = false;
    }
  }

  async function createDraftSession(workspaceId: string, input: PromptBuildInput) {
    const platform = usePlatformStore();
    const title = promptPreviewTitle(input.text ?? "");
    const session = await platform.api.createSession(workspaceId, title);
    activeSession.value = session;
    return session;
  }

  async function sendPrompt(input: PromptBuildInput) {
    if (!activeSession.value) {
      throw new Error("缺少当前会话");
    }
    const platform = usePlatformStore();
    const runEvents = useRunEventStore();
    sending.value = true;
    try {
      const payload = compactPayload({
        sessionId: activeSession.value.sessionId,
        parts: buildPromptParts(input),
        prompt: input.text,
        agent: input.agents?.[0]?.agentId
      });
      const run = await platform.api.startRun(payload);
      activeRun.value = run;
      runEvents.subscribe(run.runId, platform.baseUrl);
      return run;
    } finally {
      sending.value = false;
    }
  }

  async function abort() {
    if (!activeSession.value) {
      return;
    }
    const platform = usePlatformStore();
    await platform.api.abortSession(activeSession.value.sessionId);
  }

  // 分享入口复刻 opencode 的 publish/unpublish 行为，只保存平台返回的公开 URL。
  async function publishShare() {
    const sessionId = requireSessionId();
    const platform = usePlatformStore();
    sharing.value = true;
    shareError.value = undefined;
    try {
      const response = await platform.api.shareSession(sessionId);
      const url = extractShareUrl(response);
      if (!url) {
        throw new Error("分享响应缺少 URL");
      }
      shareUrl.value = url;
      return url;
    } catch (cause) {
      shareError.value = cause instanceof Error ? cause.message : "会话分享失败";
      throw cause;
    } finally {
      sharing.value = false;
    }
  }

  async function unpublishShare() {
    const sessionId = requireSessionId();
    const platform = usePlatformStore();
    sharing.value = true;
    shareError.value = undefined;
    try {
      await platform.api.unshareSession(sessionId);
      shareUrl.value = undefined;
    } catch (cause) {
      shareError.value = cause instanceof Error ? cause.message : "取消分享失败";
      throw cause;
    } finally {
      sharing.value = false;
    }
  }

  // 这些动作对应 opencode composer 上方的权限、问题、follow-up、revert dock。
  async function replyPermission(requestId: string, decision: "once" | "always" | "reject") {
    const sessionId = requireSessionId();
    const platform = usePlatformStore();
    await platform.api.replySessionPermission(sessionId, requestId, { decision });
    permissions.value = permissions.value.filter((item) => item.requestId !== requestId);
  }

  async function replyQuestion(requestId: string, answers: unknown[]) {
    const sessionId = requireSessionId();
    const platform = usePlatformStore();
    await platform.api.replySessionQuestion(sessionId, requestId, { answers });
    questions.value = questions.value.filter((item) => item.requestId !== requestId);
  }

  async function rejectQuestion(requestId: string) {
    const sessionId = requireSessionId();
    const platform = usePlatformStore();
    await platform.api.rejectSessionQuestion(sessionId, requestId);
    questions.value = questions.value.filter((item) => item.requestId !== requestId);
  }

  function queueFollowup(text: string) {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }
    followups.value.push({ id: `follow_${Date.now().toString(36)}_${followups.value.length}`, text: trimmed });
  }

  async function sendFollowup(id: string) {
    const item = followups.value.find((entry) => entry.id === id);
    if (!item) {
      return;
    }
    await sendPrompt({ text: item.text });
    followups.value = followups.value.filter((entry) => entry.id !== id);
  }

  function editFollowup(id: string) {
    const item = followups.value.find((entry) => entry.id === id);
    if (!item) {
      return;
    }
    const prompt = usePromptStore();
    prompt.text = item.text;
    followups.value = followups.value.filter((entry) => entry.id !== id);
  }

  async function restoreRevert(messageId: string) {
    const sessionId = requireSessionId();
    const platform = usePlatformStore();
    await platform.api.unrevertSession(sessionId, { messageId });
    revertItems.value = revertItems.value.filter((entry) => entry.id !== messageId);
  }

  function requireSessionId() {
    const sessionId = activeSession.value?.sessionId;
    if (!sessionId) {
      throw new Error("缺少当前会话");
    }
    return sessionId;
  }

  return {
    activeSession,
    activeRun,
    messages,
    timeline,
    diff,
    todos,
    permissions,
    questions,
    followups,
    revertItems,
    shareUrl,
    loading,
    sending,
    sharing,
    error,
    shareError,
    load,
    createDraftSession,
    sendPrompt,
    abort,
    publishShare,
    unpublishShare,
    replyPermission,
    replyQuestion,
    rejectQuestion,
    queueFollowup,
    sendFollowup,
    editFollowup,
    restoreRevert
  };
});

function renderParts(parts: Record<string, MessagePart & { text?: string }>) {
  return Object.values(parts)
    .map((part) => ("text" in part ? part.text : ""))
    .filter(Boolean)
    .join("");
}

function compactPayload(input: {
  sessionId: string;
  parts: ReturnType<typeof buildPromptParts>;
  prompt?: string;
  agent?: string;
}) {
  return Object.fromEntries(Object.entries(input).filter(([, value]) => value !== undefined && value !== "")) as {
    sessionId: string;
    parts: ReturnType<typeof buildPromptParts>;
    prompt?: string;
    agent?: string;
  };
}

function extractShareUrl(value: unknown): string | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  const nested = isRecord(value.share) ? value.share : undefined;
  return readString(value.url) ?? readString(nested?.url);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function readString(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}
