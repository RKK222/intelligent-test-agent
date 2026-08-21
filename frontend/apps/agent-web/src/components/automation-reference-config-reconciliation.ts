import {
  BackendApiError,
  type AutomationReferenceRepositoryStatus,
  type BackendApiClient
} from "@test-agent/backend-api";
import {
  reconcileAutomationReferenceConfig,
  type ManagedReferenceConfigPatch
} from "./reference-config-jsonc";

const OPENCODE_CONFIG_PATH = "opencode.jsonc";

export function automationReferencePatches(
  appId: string,
  repositories: AutomationReferenceRepositoryStatus[]
): ManagedReferenceConfigPatch[] {
  return repositories.flatMap((repository) => {
    const configuration = repository.currentConfiguration;
    if (!configuration || configuration.status !== "READY") return [];
    return [{
      alias: configuration.alias,
      path: configuration.logicalPath,
      folder: configuration.directoryName,
      merge: false,
      sddFolderName: configuration.directoryName,
      description: configuration.description,
      managedFields: {
        "testagent-reference-kind": "automation",
        "testagent-automation-app-id": appId,
        "testagent-automation-repository-id": repository.repositoryId,
        "testagent-automation-generation": configuration.generation
      }
    }];
  });
}

async function readWorkspaceConfig(api: BackendApiClient, workspaceId: string) {
  try {
    return (await api.readWorkspaceAgentFile(workspaceId, OPENCODE_CONFIG_PATH)).content;
  } catch (error) {
    if (error instanceof BackendApiError && (error.code === "FILE_NOT_FOUND" || error.code === "NOT_FOUND")) {
      return "";
    }
    throw error;
  }
}

/**
 * 通过既有 Agent 文件 RPC 对账当前工作树；返回值只说明是否写盘，不触发或隐藏任何 Run 上下文注入。
 */
export async function reconcileAutomationReferenceWorkspace(
  api: BackendApiClient,
  appId: string,
  workspaceId: string,
  knownRepositories?: AutomationReferenceRepositoryStatus[]
) {
  const repositories = knownRepositories ?? await api.listAutomationReferenceRepositories(appId);
  const latest = await readWorkspaceConfig(api, workspaceId);
  const content = reconcileAutomationReferenceConfig(
    latest,
    appId,
    automationReferencePatches(appId, repositories)
  );
  if (content !== latest) {
    await api.writeWorkspaceAgentFile(workspaceId, OPENCODE_CONFIG_PATH, content);
  }
  return { changed: content !== latest, repositories };
}
