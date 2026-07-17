import { describe, expect, it } from "vitest";
import { canShowReferenceConfiguration } from "../src/components/reference-configuration-access";
import agentWorkbenchSource from "../src/components/AgentWorkbench.vue?raw";

describe("reference configuration access", () => {
  it.each([
    [{ roles: ["APP_ADMIN"], personalWorkspaceId: "pw-1", runtimeWorkspaceId: "wrk-1", appId: "app-1" }, true],
    [{ roles: ["SUPER_ADMIN"], personalWorkspaceId: "pw-1", runtimeWorkspaceId: "wrk-1", appId: "app-1" }, true],
    [{ roles: ["USER"], personalWorkspaceId: "pw-1", runtimeWorkspaceId: "wrk-1", appId: "app-1" }, false],
    [{ roles: ["APP_ADMIN"], personalWorkspaceId: "pw-1", runtimeWorkspaceId: undefined, appId: "app-1" }, false],
    [{ roles: ["APP_ADMIN"], personalWorkspaceId: "pw-1", runtimeWorkspaceId: "wrk-1", appId: undefined }, false],
    [{ roles: ["APP_ADMIN"], personalWorkspaceId: undefined, runtimeWorkspaceId: "wrk-1", appId: "app-1" }, false],
    [{ roles: undefined, personalWorkspaceId: "pw-1", runtimeWorkspaceId: "wrk-1", appId: "app-1" }, false]
  ])("evaluates complete app/personal/runtime context %#", (context, expected) => {
    expect(canShowReferenceConfiguration(context)).toBe(expected);
  });

  it("binds AgentWorkbench visibility to the selected runtime workspace and keeps the click-to-open event chain", () => {
    expect(agentWorkbenchSource).toMatch(
      /canShowReferenceConfiguration\(\{[\s\S]*runtimeWorkspaceId:\s*selectedWorkspace\.value\?\.workspaceId[\s\S]*appId:\s*selectedAppId\.value[\s\S]*\}\)/
    );
    expect(agentWorkbenchSource).toContain('@open-reference-configuration="openReferenceConfiguration"');
    expect(agentWorkbenchSource).toMatch(/function openReferenceConfiguration\(\)[\s\S]*referenceConfigurationOpen\.value = true/);
  });
});
