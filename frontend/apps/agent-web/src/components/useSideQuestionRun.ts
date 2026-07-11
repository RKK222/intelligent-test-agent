import { onScopeDispose, ref } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import { subscribeRunEvents, type RunEventSubscribeOptions, type RunEventSubscription } from "@test-agent/event-stream-client";
import type { RunEvent, SideQuestionRunRequest } from "@test-agent/shared-types";

const PROGRESS_COPY: Record<string, string> = {
  preparing_context: "正在读取当前上下文",
  forking: "正在准备临时上下文",
  compacting: "正在压缩较长上下文",
  reading: "正在执行只读检查",
  tool: "正在执行只读检查",
  composing: "正在整理答案"
};

type SideQuestionApi = Pick<BackendApiClient, "startSideQuestionRun">;

export type SideQuestionRunInput = SideQuestionRunRequest & {
  sessionId: string;
};

export type UseSideQuestionRunOptions = {
  api: SideQuestionApi;
  baseUrl?: string;
  /** 当前登录态 token 由调用方按订阅时刻提供，避免宠物旁路 SSE 退化为匿名 EventSource。 */
  getAuthToken?: () => string | null;
  subscribe?: (options: RunEventSubscribeOptions) => RunEventSubscription;
};

/**
 * 管理宠物旁路问答自己的 Run 与 SSE 生命周期。
 *
 * 该组合式函数刻意不接收主 Run 的 cancel/abort 能力，关闭浮层只释放旁路订阅，
 * 避免用户查看临时答案时意外终止仍在执行的主任务。
 */
export function useSideQuestionRun(options: UseSideQuestionRunOptions) {
  const loading = ref(false);
  const progress = ref<string | null>(null);
  const answer = ref<string | null>(null);
  const error = ref<string | null>(null);
  const runId = ref<string | null>(null);
  const displaySessionId = ref<string | null>(null);
  const subscribe = options.subscribe ?? subscribeRunEvents;
  let subscription: RunEventSubscription | null = null;
  let generation = 0;
  let hasReceivedProgress = false;

  function closeSubscription() {
    const current = subscription;
    subscription = null;
    current?.close();
  }

  function releaseRun(expectedGeneration: number) {
    if (expectedGeneration !== generation) return;
    closeSubscription();
    loading.value = false;
    progress.value = null;
    runId.value = null;
  }

  function handleEvent(event: RunEvent, expectedGeneration: number) {
    if (expectedGeneration !== generation || event.runId !== runId.value) return;
    if (event.type === "side_question.progress") {
      const stage = typeof event.payload.stage === "string" ? event.payload.stage : "";
      const copy = PROGRESS_COPY[stage];
      if (copy) {
        hasReceivedProgress = true;
        progress.value = copy;
      }
      return;
    }
    if (event.type === "side_question.delta") {
      const delta = typeof event.payload.delta === "string" ? event.payload.delta : "";
      if (delta) answer.value = `${answer.value ?? ""}${delta}`;
      return;
    }
    if (event.type === "run.succeeded") {
      // durable 终态的完整 answer 是权威结果，可校准 SSE 重连期间遗漏的 transient delta。
      if (typeof event.payload.answer === "string") {
        answer.value = event.payload.answer;
      }
      error.value = null;
      releaseRun(expectedGeneration);
      return;
    }
    if (event.type === "run.failed" || event.type === "run.cancelled") {
      error.value = typeof event.payload.message === "string" && event.payload.message.trim()
        ? event.payload.message.trim()
        : "旁路问答暂时不可用，请修改问题后重试";
      releaseRun(expectedGeneration);
    }
  }

  async function submit(input: SideQuestionRunInput) {
    if (loading.value) return;

    const currentGeneration = ++generation;
    loading.value = true;
    displaySessionId.value = input.sessionId;
    progress.value = null;
    hasReceivedProgress = false;
    answer.value = null;
    error.value = null;
    runId.value = null;

    try {
      const { sessionId, ...payload } = input;
      const started = await options.api.startSideQuestionRun(sessionId, payload);
      // 浮层可能在启动 HTTP 尚未返回时已关闭；此时不得再挂上迟到的 EventSource。
      if (currentGeneration !== generation) return;
      runId.value = started.runId;

      let terminalBeforeSubscriptionAttached = false;
      const nextSubscription = subscribe({
        baseUrl: options.baseUrl,
        runId: started.runId,
        token: options.getAuthToken?.() ?? null,
        onEvent: (event) => {
          handleEvent(event, currentGeneration);
          if (!loading.value && currentGeneration === generation) {
            terminalBeforeSubscriptionAttached = true;
          }
        },
        onError: () => {
          if (currentGeneration === generation && loading.value && !hasReceivedProgress) {
            progress.value = "连接中断，正在重连";
          }
        }
      });
      if (currentGeneration !== generation || terminalBeforeSubscriptionAttached) {
        nextSubscription.close();
        return;
      }
      subscription = nextSubscription;
    } catch (cause) {
      if (currentGeneration !== generation) return;
      error.value = cause instanceof Error && cause.message.trim()
        ? cause.message
        : "旁路问答暂时不可用，请稍后重试";
      releaseRun(currentGeneration);
    }
  }

  /** 显式关闭/宠物收起时清空当前旁路展示，并让下一次提问获得新的单飞锁。 */
  function reset() {
    generation += 1;
    closeSubscription();
    loading.value = false;
    progress.value = null;
    hasReceivedProgress = false;
    answer.value = null;
    error.value = null;
    runId.value = null;
    displaySessionId.value = null;
  }

  /** 切换或新建主会话时释放旧旁路订阅，避免旧上下文答案显示到新会话。 */
  function resetForSessionChange(nextSessionId: string | null | undefined) {
    if (displaySessionId.value && displaySessionId.value !== (nextSessionId ?? null)) {
      reset();
    }
  }

  onScopeDispose(reset);

  return { loading, progress, answer, error, runId, submit, reset, resetForSessionChange };
}
