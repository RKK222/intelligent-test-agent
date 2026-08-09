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
import firstLoginGuideSource from "../src/components/FirstLoginGuide.vue?raw";

describe("experience workspace flow", () => {
  it("offers once only after both the user and an empty application list load successfully", () => {
    const eligible = {
      userId: "usr_no_app",
      applicationsStatus: "success" as const,
      applicationCount: 0,
      offeredThisMount: false
    };

    expect(experienceOfferDecision(eligible)).toBe("OFFER");
    expect(experienceOfferDecision({ ...eligible, offeredThisMount: true })).toBe("SKIP");
    expect(experienceOfferDecision({ ...eligible, applicationCount: 1 })).toBe("SKIP");
    const failedQuery = { ...eligible, applicationsStatus: "error" as const };
    expect(experienceOfferDecision(failedQuery)).toBe("WAIT");
    // 首次查询失败不能消耗本次挂载机会，后续重试成功且仍为空时应正常询问。
    expect(experienceOfferDecision({ ...failedQuery, applicationsStatus: "success" })).toBe("OFFER");
    expect(experienceOfferDecision({ ...eligible, applicationsStatus: "pending" })).toBe("WAIT");
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
    expect(experienceInitializationConfirmationIsCurrent(requested, requested.generation, 0)).toBe(true);
    expect(experienceInitializationConfirmationIsCurrent(cancelled, requested.generation, 0)).toBe(false);
    expect(experienceInitializationConfirmationIsCurrent(requested, requested.generation, 1)).toBe(false);
  });

  it("keeps the experience prompt ahead of onboarding and cancels continuation on every authority loss", () => {
    expect(firstLoginGuideSource).toContain("enabled?: boolean");
    expect(agentWorkbenchSource).toContain(':enabled="firstLoginGuideEnabled"');
    expect(agentWorkbenchSource).toContain("cancelExperienceWorkspaceFlow(\"UNMOUNT\")");
    expect(agentWorkbenchSource).toContain("cancelExperienceWorkspaceFlow(\"APPLICATION_JOINED\")");
    expect(agentWorkbenchSource).toContain("cancelExperienceWorkspaceFlow(\"PROCESS_FAILED\")");
    expect(agentWorkbenchSource).toContain("beginExperienceWorkspaceOpen(");
    expect(agentWorkbenchSource).toContain("isCurrent: () => experienceInitializationConfirmationIsCurrent(");
    expect(agentWorkbenchSource).toContain('&& selectedWorkspaceKind.value !== "EXPERIENCE"');
  });
});
