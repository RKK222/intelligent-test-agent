import type {
  NightExecutionScheduleMode,
  NightExecutionSlot,
  PromptPart
} from "@test-agent/shared-types";
import { CHAT_CONTEXT_LIMITS } from "../stores/chatContextStore";
import { looksBinaryContent, type WorkspaceRequirementReference } from "./workbench-utils";

export type BatchItemExecutionStatus =
  | "idle"
  | "loading-context"
  | "creating-session"
  | "starting-run"
  | "creating-task"
  | "succeeded"
  | "failed";

export type BatchItemExecutionState = {
  status: BatchItemExecutionStatus;
  errorCode?: string;
  message?: string;
  sessionId?: string;
  runId?: string;
  taskId?: string;
};

export type BatchGenerationRequest = {
  referenceIds: string[];
  requirement: string;
  executionMode: "immediate" | "scheduled";
  scheduleMode?: NightExecutionScheduleMode;
  slotStarts?: string[];
  retry?: boolean;
};

export type BatchItemPreparationErrorCode =
  | "INPUT_REQUIRED"
  | "INPUT_TOO_LARGE"
  | "FILE_READ_FAILED"
  | "FILE_TOO_LARGE"
  | "CONTEXT_TOO_LARGE"
  | "NO_READABLE_FILES";

/** 单项准备失败携带稳定安全码，UI 不需要展示文件正文或完整异常。 */
export class BatchItemPreparationError extends Error {
  constructor(
    readonly code: BatchItemPreparationErrorCode,
    message: string
  ) {
    super(message);
    this.name = "BatchItemPreparationError";
  }
}

export type BatchItemRunInput = {
  prompt: string;
  parts: PromptPart[];
  readableFileCount: number;
  contextCharCount: number;
};

/** 顺序读取 # 候选已经聚合好的 filePaths，并构造只属于该批量条目的 PromptPart。 */
export async function buildBatchItemRunInput(options: {
  reference: WorkspaceRequirementReference;
  requirement: string;
  readFile: (path: string) => Promise<{ content: string }>;
}): Promise<BatchItemRunInput> {
  const requirement = options.requirement.trim();
  if (!requirement) {
    throw new BatchItemPreparationError("INPUT_REQUIRED", "请输入批量案例生成要求");
  }
  if (requirement.length > CHAT_CONTEXT_LIMITS.MAX_USER_INPUT_CHARS) {
    throw new BatchItemPreparationError("INPUT_TOO_LARGE", "批量案例生成要求不能超过 2 万字");
  }
  const prompt = `${requirement}\n\n需求项：${options.reference.requirementName}\n子条目：${options.reference.subitemName}`;
  const parts: PromptPart[] = [{ type: "text", text: prompt }];
  let readableFileCount = 0;
  let contextCharCount = 0;

  for (const path of options.reference.filePaths) {
    let content: string;
    try {
      content = (await options.readFile(path)).content;
    } catch {
      throw new BatchItemPreparationError("FILE_READ_FAILED", "读取子条目关联文件失败");
    }
    if (looksBinaryContent(content)) continue;
    if (content.length > CHAT_CONTEXT_LIMITS.MAX_FILE_CHARS) {
      throw new BatchItemPreparationError("FILE_TOO_LARGE", "子条目存在超过 8 万字的文件");
    }
    if (contextCharCount + content.length > CHAT_CONTEXT_LIMITS.MAX_TOTAL_CONTEXT_CHARS) {
      throw new BatchItemPreparationError("CONTEXT_TOO_LARGE", "子条目关联文件总量超过 12 万字");
    }
    contextCharCount += content.length;
    readableFileCount += 1;
    const name = path.split(/[\\/]+/).filter(Boolean).at(-1) ?? path;
    parts.push({
      type: "file",
      path,
      name,
      mimeType: "text/plain",
      content,
      source: {
        text: content,
        start: 0,
        end: content.length,
        contextType: "file"
      }
    });
  }

  if (readableFileCount === 0) {
    throw new BatchItemPreparationError("NO_READABLE_FILES", "子条目没有可读取的文本文件");
  }
  return { prompt, parts, readableFileCount, contextCharCount };
}

export type BatchScheduleAllocation =
  | { ok: true; times: string[] }
  | { ok: false; remainingCapacity: number };

/** 按时间升序轮询分配；夜间模式同时扣除当前已预约容量。 */
export function allocateBatchSchedule(options: {
  itemCount: number;
  scheduleMode: NightExecutionScheduleMode;
  selectedTimes: string[];
  slots?: NightExecutionSlot[];
}): BatchScheduleAllocation {
  const times = Array.from(new Set(options.selectedTimes)).sort();
  if (options.itemCount <= 0) return { ok: true, times: [] };
  if (options.scheduleMode === "ADMIN_CUSTOM") {
    if (times.length === 0) return { ok: false, remainingCapacity: 0 };
    return {
      ok: true,
      times: Array.from({ length: options.itemCount }, (_, index) => times[index % times.length]!)
    };
  }

  const slotsByStart = new Map((options.slots ?? []).map((slot) => [slot.slotStart, slot]));
  const capacity = times.map((time) => {
    const slot = slotsByStart.get(time);
    return Math.max(0, slot && slot.available ? slot.capacity - slot.reservedCount : 0);
  });
  const remainingCapacity = capacity.reduce((sum, value) => sum + value, 0);
  if (remainingCapacity < options.itemCount) return { ok: false, remainingCapacity };

  const assignments: string[] = [];
  let cursor = 0;
  while (assignments.length < options.itemCount) {
    const index = cursor % times.length;
    cursor += 1;
    if ((capacity[index] ?? 0) <= 0) continue;
    assignments.push(times[index]!);
    capacity[index] -= 1;
  }
  return { ok: true, times: assignments };
}

/** 固定并发 worker 池；结果顺序始终与输入顺序一致。 */
export async function mapWithConcurrency<T, R>(
  items: readonly T[],
  concurrency: number,
  worker: (item: T, index: number) => Promise<R>
): Promise<R[]> {
  const results = new Array<R>(items.length);
  let nextIndex = 0;
  const workerCount = Math.max(1, Math.min(Math.floor(concurrency), items.length));
  await Promise.all(Array.from({ length: workerCount }, async () => {
    while (nextIndex < items.length) {
      const index = nextIndex;
      nextIndex += 1;
      results[index] = await worker(items[index]!, index);
    }
  }));
  return results;
}
