import { describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import { reconcileAutomationReferenceWorkspace } from "../src/components/automation-reference-config-reconciliation";

describe("automation reference workspace reconciliation contract", () => {
  it("delegates the whole operation to the backend file RPC without reading JSONC in the browser", async () => {
    const reconcileWorkspaceAutomationReferences = vi.fn().mockResolvedValue({
      changed: true,
      warnings: ["自动化库 B 尚未就绪"]
    });
    const api = { reconcileWorkspaceAutomationReferences } as unknown as BackendApiClient;

    await expect(reconcileAutomationReferenceWorkspace(api, "wrk_personal"))
      .resolves.toEqual({ changed: true, warnings: ["自动化库 B 尚未就绪"] });
    expect(reconcileWorkspaceAutomationReferences).toHaveBeenCalledWith("wrk_personal");
  });
});
