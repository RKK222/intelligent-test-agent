import type { BackendApiClient } from "@test-agent/backend-api";

/** 前端只调用后端权威对账器，不读取、解析或拼接自动化 JSONC。 */
export function reconcileAutomationReferenceWorkspace(
  api: BackendApiClient,
  workspaceId: string
) {
  return api.reconcileWorkspaceAutomationReferences(workspaceId);
}
