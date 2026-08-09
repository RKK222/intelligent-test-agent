// @vitest-environment jsdom

import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ElMessageBox } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { MemoryView } from "@test-agent/shared-types";
import MemoryCenter from "../src/components/MemoryCenter.vue";

const personalMemory: MemoryView = {
  memoryId: "mem_personal_1",
  scope: "PERSONAL_APPLICATION",
  ownerUserId: "usr_tester",
  applicationId: "app_1",
  status: "ACTIVE",
  source: "NATIVE",
  content: "用户偏好先给结论再说明依据",
  contentAvailable: true,
  displaySummary: "用户偏好先给结论再说明依据",
  version: 2,
  confirmedAt: null,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-09T00:00:00Z"
};

const teamMemory: MemoryView = {
  ...personalMemory,
  memoryId: "mem_team_1",
  scope: "TEAM_APPLICATION",
  ownerUserId: null,
  applicationId: "app_1",
  source: "TEAM_PROPOSAL",
  status: "CANDIDATE",
  displaySummary: "缺陷结论必须附带可复核证据"
};

function createApi() {
  return {
    getQaMemoryAvailability: vi.fn().mockResolvedValue({ enabled: true }),
    listPersonalMemories: vi.fn().mockResolvedValue({ items: [personalMemory], page: 1, size: 100, total: 1 }),
    listTeamMemories: vi.fn().mockResolvedValue({ items: [teamMemory], page: 1, size: 100, total: 1 }),
    listMemorySkillProposals: vi.fn().mockResolvedValue({
      items: [{
        proposalId: "msp_1",
        memoryId: teamMemory.memoryId,
        applicationId: "app_1",
        title: "证据驱动缺陷分析",
        skillMdDraft: "",
        status: "PENDING_REVIEW",
        createdByUserId: "usr_tester",
        version: 1,
        createdAt: "2026-08-09T00:00:00Z",
        updatedAt: "2026-08-09T00:00:00Z"
      }],
      page: 1,
      size: 100,
      total: 1
    }),
    listQaMemoryEvidence: vi.fn().mockResolvedValue([{
      evidenceId: "mge_1",
      memoryId: personalMemory.memoryId,
      runId: "run_1",
      sessionId: "ses_1",
      sessionTitle: "登录体验讨论",
      transcriptAvailable: true,
      source: "NATIVE",
      summary: "用户在对话中明确表达回答偏好",
      observedAt: "2026-08-08T00:00:00Z"
    }]),
    promotePersonalMemoryGlobal: vi.fn().mockResolvedValue({
      ...personalMemory, scope: "PERSONAL_GLOBAL", applicationId: null, version: 3
    }),
    reviewTeamMemory: vi.fn().mockResolvedValue({ ...teamMemory, status: "ACTIVE", version: 3 }),
    createTeamMemoryProposal: vi.fn().mockResolvedValue(teamMemory),
    reviewMemorySkillProposal: vi.fn().mockResolvedValue({})
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderCenter(api: BackendApiClient, canManageTeam = true) {
  return render(MemoryCenter, {
    props: { selectedAppId: "app_1", canManageTeam },
    global: {
      provide: { api },
      stubs: {
        ElDialog: {
          props: ["modelValue", "title"],
          template: `<section v-if="modelValue" role="dialog" :aria-label="title"><slot /><slot name="footer" /></section>`
        },
        ElDrawer: {
          props: ["modelValue"],
          template: `<aside v-if="modelValue" role="dialog"><slot name="header" /><slot /></aside>`
        }
      }
    }
  });
}

describe("MemoryCenter", () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => cleanup());

  it("shows generic memory evidence with session identity and owner transcript link", async () => {
    const api = createApi();
    const view = renderCenter(api);

    expect(await view.findByTestId("memory-card-mem_personal_1")).toBeTruthy();
    expect(view.getByTestId("memory-tab-personal").textContent).toContain("我的记忆");
    expect(view.getByTestId("memory-tab-team").textContent).toContain("团队记忆");
    expect(view.getByTestId("memory-tab-skills").textContent).toContain("Skill 提案");

    await fireEvent.click(view.getByText("查看证据"));
    expect(await view.findByTestId("memory-evidence-rail")).toBeTruthy();
    expect(view.getByText("登录体验讨论")).toBeTruthy();
    expect(view.getByText(/会话 ID ses_1 · Run ID run_1/)).toBeTruthy();
    expect(view.getByRole("link", { name: "打开原始对话" }).getAttribute("href")).toBe("/s/ses_1");
    expect(view.getByText("使用", { selector: "strong" })).toBeTruthy();

    await fireEvent.click(view.getByRole("button", { name: "提升为个人全局" }));
    await waitFor(() => expect(api.promotePersonalMemoryGlobal).toHaveBeenCalledWith("mem_personal_1", 2));
  });

  it("keeps team candidates behind APP_ADMIN review and skill proposals behind a second review", async () => {
    const api = createApi();
    const view = renderCenter(api, true);

    await view.findByTestId("memory-card-mem_personal_1");
    await fireEvent.click(view.getByTestId("memory-tab-team"));
    expect(await view.findByTestId("memory-card-mem_team_1")).toBeTruthy();
    await fireEvent.click(view.getByText("查看证据"));
    await fireEvent.click(await view.findByRole("button", { name: "批准" }));
    await waitFor(() => expect(api.reviewTeamMemory).toHaveBeenCalledWith(
      "mem_team_1",
      { decision: "APPROVE", comment: "", expectedVersion: 2 }
    ));

    await fireEvent.click(view.getByTestId("memory-tab-skills"));
    expect(await view.findByText("证据驱动缺陷分析")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "批准并生成草稿" }));
    await waitFor(() => expect(api.reviewMemorySkillProposal).toHaveBeenCalledWith("msp_1", "APPROVE", 1));
  });

  it("manually proposes a personal memory to the team while retaining only evidence references", async () => {
    const api = createApi();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue({} as never);
    const view = renderCenter(api, true);

    await view.findByTestId("memory-card-mem_personal_1");
    await fireEvent.click(view.getByText("查看证据"));
    await fireEvent.click(view.getByRole("button", { name: "提交为团队记忆" }));

    await waitFor(() => expect(api.createTeamMemoryProposal).toHaveBeenCalledWith({
      applicationId: "app_1",
      content: "用户偏好先给结论再说明依据",
      sourceMemoryId: "mem_personal_1"
    }));
  });

  it("renders a non-invasive unavailable state for users outside the whitelist", async () => {
    const api = createApi();
    vi.mocked(api.getQaMemoryAvailability).mockResolvedValue({ enabled: false });
    const view = renderCenter(api);

    expect(await view.findByTestId("memory-unavailable")).toBeTruthy();
    expect(view.getByText(/默认白名单为空，不影响现有对话/)).toBeTruthy();
    expect(api.listPersonalMemories).not.toHaveBeenCalled();
  });
});
