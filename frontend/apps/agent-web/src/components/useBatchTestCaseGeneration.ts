import { ref, type Ref } from "vue";
import {
  BackendApiError,
  type BackendApiClient,
  type CreateNightExecutionTaskPayload
} from "@test-agent/backend-api";
import type { NightExecutionSlots } from "@test-agent/shared-types";
import {
  BatchItemPreparationError,
  allocateBatchSchedule,
  buildBatchItemRunInput,
  mapWithConcurrency,
  type BatchGenerationRequest,
  type BatchItemExecutionState
} from "./batch-test-case-generation";
import {
  createClientRequestId,
  startRunWithConversationContext,
  type ConversationRunContextCache
} from "./conversation-run-context";
import { sessionTitleFromFirstMessage, type WorkspaceRequirementReference } from "./workbench-utils";

type BatchApi = Pick<BackendApiClient,
  "readFile" | "createBatchItemSession" | "startRun" | "createNightExecutionTask">;

export type BatchExecutionSummary = {
  total: number;
  succeeded: number;
  failed: number;
  capacityConflict: boolean;
};

export type BatchTestCaseGenerationOptions = {
  api: BatchApi;
  conversationContexts: ConversationRunContextCache;
  references: () => WorkspaceRequirementReference[];
  workspaceId: () => string | undefined;
  agent: () => string | undefined;
  model: () => string | undefined;
  mode: () => string | undefined;
  nightSlots: () => NightExecutionSlots | null | undefined;
};

type BatchIdentity = {
  batchId: string;
  itemRequestIds: Map<string, string>;
  scheduledTimes: Map<string, string>;
};

/** 批量编排完全使用局部 PromptPart，不读写当前输入、Session 或聊天上下文 store。 */
export function useBatchTestCaseGeneration(options: BatchTestCaseGenerationOptions): {
  running: Ref<boolean>;
  itemStates: Ref<Record<string, BatchItemExecutionState>>;
  execute: (request: BatchGenerationRequest) => Promise<BatchExecutionSummary>;
} {
  const running = ref(false);
  const itemStates = ref<Record<string, BatchItemExecutionState>>({});
  let identity: BatchIdentity | null = null;

  function update(referenceId: string, state: BatchItemExecutionState) {
    itemStates.value = { ...itemStates.value, [referenceId]: state };
  }

  async function execute(request: BatchGenerationRequest): Promise<BatchExecutionSummary> {
    if (running.value) return summary([]);
    const workspaceId = options.workspaceId();
    if (!workspaceId) throw new Error("未选择 Workspace");
    const selected = new Set(request.referenceIds);
    const references = options.references().filter((reference) => selected.has(reference.id));
    if (references.length === 0) return summary([]);

    if (!request.retry || !identity) {
      const batchId = createClientRequestId().replace(/^req_/, "batch_");
      identity = { batchId, itemRequestIds: new Map(), scheduledTimes: new Map() };
      references.forEach((reference, index) => {
        identity!.itemRequestIds.set(reference.id, `${batchId}_item_${index + 1}`);
        update(reference.id, { status: "idle" });
      });
    }
    for (const [index, reference] of references.entries()) {
      if (!identity.itemRequestIds.has(reference.id)) {
        identity.itemRequestIds.set(reference.id, `${identity.batchId}_item_retry_${index + 1}`);
      }
    }

    const scheduleTimes = request.executionMode === "scheduled"
      ? resolveScheduleTimes(request, references, identity, itemStates.value, options.nightSlots())
      : new Map<string, string>();

    running.value = true;
    try {
      await mapWithConcurrency(references, 4, async (reference) => {
        const itemRequestId = identity!.itemRequestIds.get(reference.id)!;
        const batchContext = { batchId: identity!.batchId, itemRequestId };
        try {
          update(reference.id, { status: "loading-context" });
          const input = await buildBatchItemRunInput({
            reference,
            requirement: request.requirement,
            readFile: (path) => options.api.readFile(workspaceId, path)
          });
          const title = sessionTitleFromFirstMessage(
            `${reference.requirementName} ${reference.subitemName} 测试案例`
          );

          if (request.executionMode === "immediate") {
            update(reference.id, { status: "creating-session" });
            const created = await options.api.createBatchItemSession(workspaceId, title, batchContext);
            update(reference.id, { status: "starting-run", sessionId: created.sessionId });
            const started = await startRunWithConversationContext({
              cache: options.conversationContexts,
              clientRequestId: `run_${itemRequestId}`,
              payload: {
                sessionId: created.sessionId,
                prompt: input.prompt,
                parts: input.parts,
                agent: options.agent(),
                model: options.model(),
                mode: options.mode()
              },
              startRun: (payload) => options.api.startRun(payload)
            });
            update(reference.id, {
              status: "succeeded",
              sessionId: created.sessionId,
              runId: started.runId
            });
          } else {
            const slotStart = scheduleTimes.get(reference.id);
            if (!slotStart || !request.scheduleMode) throw new Error("批量定时时间分配失败");
            update(reference.id, { status: "creating-task" });
            const payload: CreateNightExecutionTaskPayload = {
              clientRequestId: `task_${itemRequestId}`,
              runClientRequestId: `run_${itemRequestId}`,
              workspaceId,
              sessionTitle: title,
              prompt: input.prompt,
              parts: input.parts,
              agent: options.agent(),
              model: options.model(),
              mode: options.mode(),
              scheduleMode: request.scheduleMode,
              slotStart,
              batchContext
            };
            const created = await options.api.createNightExecutionTask(payload);
            update(reference.id, {
              status: "succeeded",
              sessionId: created.sessionId,
              taskId: created.taskId
            });
          }
          console.info("batch_test_case_item", {
            batchId: batchContext.batchId,
            itemRequestId,
            status: "SUCCEEDED"
          });
        } catch (error) {
          const failure = safeFailure(error);
          update(reference.id, { status: "failed", ...failure });
          console.info("batch_test_case_item", {
            batchId: batchContext.batchId,
            itemRequestId,
            status: "FAILED",
            errorCode: failure.errorCode,
            traceId: error instanceof BackendApiError ? error.traceId : undefined
          });
        }
      });
    } finally {
      running.value = false;
    }
    const result = summary(references.map((reference) => itemStates.value[reference.id]));
    console.info("batch_test_case_complete", {
      batchId: identity.batchId,
      count: result.total,
      succeeded: result.succeeded,
      failed: result.failed
    });
    return result;
  }

  return { running, itemStates, execute };
}

function resolveScheduleTimes(
  request: BatchGenerationRequest,
  references: WorkspaceRequirementReference[],
  identity: BatchIdentity,
  states: Record<string, BatchItemExecutionState>,
  slots: NightExecutionSlots | null | undefined
): Map<string, string> {
  if (!request.scheduleMode) throw new Error("请选择定时执行模式");
  const needAllocation = references.filter((reference) =>
    !request.retry
    || !identity.scheduledTimes.has(reference.id)
    || states[reference.id]?.errorCode === "SLOT_CAPACITY_CONFLICT"
  );
  if (needAllocation.length > 0) {
    const allocation = allocateBatchSchedule({
      itemCount: needAllocation.length,
      scheduleMode: request.scheduleMode,
      selectedTimes: request.slotStarts ?? [],
      slots: slots?.slots
    });
    if (!allocation.ok) {
      throw new Error(`所选夜间时段总余量仅 ${allocation.remainingCapacity}`);
    }
    needAllocation.forEach((reference, index) => {
      identity.scheduledTimes.set(reference.id, allocation.times[index]!);
    });
  }
  return identity.scheduledTimes;
}

function safeFailure(error: unknown): Pick<BatchItemExecutionState, "errorCode" | "message"> {
  if (error instanceof BatchItemPreparationError) {
    return { errorCode: error.code, message: error.message };
  }
  if (error instanceof BackendApiError) {
    const capacityConflict = error.status === 409 && Array.isArray(error.details.slots);
    return {
      errorCode: capacityConflict ? "SLOT_CAPACITY_CONFLICT" : error.code,
      message: capacityConflict ? "时段容量冲突，请重新选择" : error.message
    };
  }
  return { errorCode: "BATCH_ITEM_FAILED", message: error instanceof Error ? error.message : "执行失败" };
}

function summary(states: Array<BatchItemExecutionState | undefined>): BatchExecutionSummary {
  const succeeded = states.filter((state) => state?.status === "succeeded").length;
  const failedStates = states.filter((state) => state?.status === "failed");
  return {
    total: states.length,
    succeeded,
    failed: failedStates.length,
    capacityConflict: failedStates.some((state) => state?.errorCode === "SLOT_CAPACITY_CONFLICT")
  };
}
