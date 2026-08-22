export const REFERENCE_REPOSITORY_ACTIVE_STATUSES = new Set([
  "INITIALIZING",
  "SYNCHRONIZING",
  "VERIFYING"
]);

type RetryTarget = {
  status: string;
  servers?: Array<{ status: string }>;
} | null | undefined;

/** 应用资产库与自动化代码库共用同一套操作重试判定，避免两个页签的状态机再次分叉。 */
export function canRetryReferenceRepositoryOperation(
  requestState: "REQUESTING" | "ACCEPTED" | "FAILED" | undefined,
  target: RetryTarget
) {
  if (!requestState || requestState === "REQUESTING") return false;
  return requestState === "FAILED"
    || target?.status === "FAILED"
    || Boolean(target?.servers?.some((server) => server.status === "RETRY_WAIT"));
}

export function mustTerminateReferenceRepositoryBeforeRetry(target: RetryTarget) {
  return Boolean(target && REFERENCE_REPOSITORY_ACTIVE_STATUSES.has(target.status));
}
