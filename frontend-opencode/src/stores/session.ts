import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { MessagePart, PermissionRequest, QuestionRequest, Run, Session, SessionDiff, SessionMessage, TodoItem } from "@test-agent/shared-types";
import { usePlatformStore } from "@/stores/platform";
import { useRunEventStore } from "@/stores/runEvents";
import { buildPromptParts, promptPreviewTitle, type PromptBuildInput } from "@/utils/prompt";

export const useSessionStore = defineStore("session", () => {
  const activeSession = ref<Session>();
  const messages = ref<SessionMessage[]>([]);
  const activeRun = ref<Run>();
  const diff = ref<SessionDiff>();
  const todos = ref<TodoItem[]>([]);
  const permissions = ref<PermissionRequest[]>([]);
  const questions = ref<QuestionRequest[]>([]);
  const loading = ref(false);
  const sending = ref(false);
  const error = ref<string>();

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
      const run = await platform.api.startRun({
        sessionId: activeSession.value.sessionId,
        parts: buildPromptParts(input),
        prompt: input.text,
        agent: input.agents?.[0]?.agentId
      });
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

  return {
    activeSession,
    activeRun,
    messages,
    timeline,
    diff,
    todos,
    permissions,
    questions,
    loading,
    sending,
    error,
    load,
    createDraftSession,
    sendPrompt,
    abort
  };
});

function renderParts(parts: Record<string, MessagePart & { text?: string }>) {
  return Object.values(parts)
    .map((part) => ("text" in part ? part.text : ""))
    .filter(Boolean)
    .join("");
}
