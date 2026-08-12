import { describe, expect, it } from "vitest";
import {
  beginExperienceWorkspaceOpen,
  cancelExperienceContinuation,
  experienceInitializationConfirmationIsCurrent,
  experienceOfferDecision,
  initialExperienceContinuation,
  requestExperienceContinuation
} from "../src/components/experience-workspace";
import agentWorkbenchSource from "../src/components/AgentWorkbench.vue?raw";
import figmaShellSource from "../src/components/FigmaShell.vue?raw";
import firstLoginGuideSource from "../src/components/FirstLoginGuide.vue?raw";

describe("experience workspace flow", () => {
  it("keeps the automatic prompt for app-less users without making it the access gate", () => {
    const eligible = {
      userId: "usr_no_app",
      applicationsStatus: "success" as const,
      applicationCount: 0,
      offeredThisMount: false
    };

    expect(experienceOfferDecision(eligible)).toBe("OFFER");
    expect(experienceOfferDecision({ ...eligible, offeredThisMount: true })).toBe("SKIP");
    expect(experienceOfferDecision({ ...eligible, applicationCount: 1 })).toBe("SKIP");
    expect(experienceOfferDecision({ ...eligible, applicationsStatus: "error" })).toBe("WAIT");
    expect(experienceOfferDecision({ ...eligible, userId: "" })).toBe("WAIT");
  });

  it("claims a READY continuation once and rejects duplicate or late callbacks", () => {
    const requested = requestExperienceContinuation(initialExperienceContinuation());
    const first = beginExperienceWorkspaceOpen(requested, requested.generation);

    expect(first.shouldOpen).toBe(true);
    expect(first.state.phase).toBe("OPENING");
    expect(beginExperienceWorkspaceOpen(first.state, requested.generation).shouldOpen).toBe(false);

    const cancelled = cancelExperienceContinuation(requested);
    expect(cancelled.generation).toBeGreaterThan(requested.generation);
    expect(cancelled.phase).toBe("IDLE");
    expect(beginExperienceWorkspaceOpen(cancelled, requested.generation).shouldOpen).toBe(false);
    expect(experienceInitializationConfirmationIsCurrent(requested, requested.generation)).toBe(true);
    expect(experienceInitializationConfirmationIsCurrent(cancelled, requested.generation)).toBe(false);
  });

  it("keeps a persistent experience entry and only cancels continuation on real flow loss", () => {
    expect(firstLoginGuideSource).toContain("enabled?: boolean");
    expect(agentWorkbenchSource).toContain(':enabled="firstLoginGuideEnabled"');
    expect(agentWorkbenchSource).toContain("cancelExperienceWorkspaceFlow(\"UNMOUNT\")");
    expect(agentWorkbenchSource).toContain('@open-experience="openExperienceWorkspaceDialog"');
    expect(figmaShellSource).toContain("随时进入本服务器的共享体验工作区");
    expect(figmaShellSource).toContain('emit("open-experience")');
    expect(agentWorkbenchSource).not.toContain("cancelExperienceWorkspaceFlow(\"APPLICATION_JOINED\")");
    expect(agentWorkbenchSource).toContain("cancelExperienceWorkspaceFlow(\"PROCESS_FAILED\")");
    expect(agentWorkbenchSource).toContain("beginExperienceWorkspaceOpen(");
    expect(agentWorkbenchSource).toContain("isCurrent: () => experienceInitializationConfirmationIsCurrent(");
    expect(agentWorkbenchSource).toContain('&& selectedWorkspaceKind.value !== "EXPERIENCE"');
  });
});
