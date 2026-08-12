import { describe, expect, it } from "vitest";
import {
  beginExperienceWorkspaceOpen,
  cancelExperienceContinuation,
  experienceInitializationConfirmationIsCurrent,
  experienceOfferDecision,
  hasAcknowledgedExperienceWorkspace,
  initialExperienceContinuation,
  markExperienceWorkspaceAcknowledged,
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
      offeredThisMount: false,
      acknowledgedPreviously: false
    };

    expect(experienceOfferDecision(eligible)).toBe("OFFER");
    expect(experienceOfferDecision({ ...eligible, offeredThisMount: true })).toBe("SKIP");
    expect(experienceOfferDecision({ ...eligible, acknowledgedPreviously: true })).toBe("SKIP");
    expect(experienceOfferDecision({ ...eligible, applicationCount: 1 })).toBe("SKIP");
    expect(experienceOfferDecision({ ...eligible, applicationsStatus: "error" })).toBe("WAIT");
    expect(experienceOfferDecision({ ...eligible, userId: "" })).toBe("WAIT");
  });

  it("remembers a successful experience acknowledgement per user", () => {
    const values = new Map<string, string>();
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value)
    };

    expect(hasAcknowledgedExperienceWorkspace(storage, "usr_one")).toBe(false);
    markExperienceWorkspaceAcknowledged(storage, "usr_one");
    expect(hasAcknowledgedExperienceWorkspace(storage, "usr_one")).toBe(true);
    expect(hasAcknowledgedExperienceWorkspace(storage, "usr_two")).toBe(false);
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
    expect(figmaShellSource).toContain('data-testid="experience-workspace-open"');
    expect(figmaShellSource).toContain("'已在平台体验' : '进入平台体验'");
    expect(figmaShellSource).toContain('emit("open-experience")');
    expect(figmaShellSource).not.toContain('<span class="figma-app-menu-item-name">平台体验</span>');
    expect(agentWorkbenchSource).not.toContain("cancelExperienceWorkspaceFlow(\"APPLICATION_JOINED\")");
    expect(agentWorkbenchSource).toContain("cancelExperienceWorkspaceFlow(\"PROCESS_FAILED\")");
    expect(agentWorkbenchSource).toContain("beginExperienceWorkspaceOpen(");
    expect(agentWorkbenchSource).toContain("isCurrent: () => experienceInitializationConfirmationIsCurrent(");
    const defaultSelectionStart = agentWorkbenchSource.indexOf("function trySelectDefaultApp()");
    const defaultSelectionEnd = agentWorkbenchSource.indexOf("watch(\n  () => managedApplicationsQuery.data.value", defaultSelectionStart);
    expect(defaultSelectionStart).toBeGreaterThan(-1);
    expect(agentWorkbenchSource.slice(defaultSelectionStart, defaultSelectionEnd))
      .toContain('selectedWorkspaceKind.value === "EXPERIENCE"');
    expect(agentWorkbenchSource.slice(defaultSelectionStart, defaultSelectionEnd))
      .toContain("experienceJourneyActive.value");
    expect(agentWorkbenchSource).toContain('selectedWorkspaceKind.value !== "EXPERIENCE"\n    && !experienceJourneyActive.value');
    expect(agentWorkbenchSource).toContain("&& !experienceJourneyActive.value\n    && !retryingWorkspaceAfterOpencodeReady");
    expect(agentWorkbenchSource).toContain("markExperienceWorkspaceAcknowledged(");
  });
});
