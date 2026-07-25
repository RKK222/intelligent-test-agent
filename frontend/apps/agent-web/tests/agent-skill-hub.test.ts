// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import AgentSkillHub from "../src/components/AgentSkillHub.vue";

const api = {
  listAgentSkillHubAssets: vi.fn(),
  getAgentSkillHubAsset: vi.fn(),
  readAgentSkillHubFile: vi.fn(),
  getAgentSkillHubUpdateCount: vi.fn(),
  listAgentSkillHubUpdates: vi.fn(),
  publishAgentSkillHubAsset: vi.fn(),
  createAgentSkillHubReference: vi.fn(),
  removeAgentSkillHubReference: vi.fn(),
  startAgentSkillHubReferenceUpdate: vi.fn(),
  resolveAgentSkillHubUpdateConflict: vi.fn(),
  completeAgentSkillHubUpdate: vi.fn(),
  abortAgentSkillHubUpdate: vi.fn()
};

describe("AgentSkillHub", () => {
  beforeEach(() => {
    const asset = hubAsset();
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [asset], total: 1, page: 1, size: 100 });
    api.getAgentSkillHubAsset.mockResolvedValue({
      asset,
      selectedRevisionId: "hub_rev_published",
      files: [{ path: "AGENT.md", size: 30, sha256: "a".repeat(64), mediaType: "text/markdown" }],
      dependencies: [],
      consumers: []
    });
    api.readAgentSkillHubFile.mockResolvedValue({
      revisionId: "hub_rev_published",
      path: "AGENT.md",
      content: "# Checkout agent",
      size: 30,
      sha256: "a".repeat(64)
    });
    api.getAgentSkillHubUpdateCount.mockResolvedValue({ count: 2 });
    api.listAgentSkillHubUpdates.mockResolvedValue({ items: [], total: 0, page: 1, size: 100 });
    api.createAgentSkillHubReference.mockResolvedValue({
      referenceId: "hub_ref_1",
      assetId: "hub_asset_1",
      targetPath: ".opencode/agents/checkout.md",
      aliasTechnicalId: "checkout",
      pendingRevisionId: "hub_rev_published",
      status: "PENDING_PUSH",
      runtimeReloadRequired: true,
      message: "已写入当前个人 worktree"
    });
    api.removeAgentSkillHubReference.mockResolvedValue({
      referenceId: "hub_ref_1",
      assetId: "hub_asset_1",
      targetPath: ".opencode/agents/checkout.md",
      aliasTechnicalId: "checkout",
      activeRevisionId: "hub_rev_published",
      pendingRevisionId: null,
      status: "PENDING_REMOVE",
      runtimeReloadRequired: true,
      message: "已从当前个人 worktree 移除"
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("lets every authenticated user browse full pushed content while hiding management actions", async () => {
    const view = renderHub({ canManage: false });

    await waitFor(() => expect(view.getByText("结账检查")).toBeTruthy());
    await fireEvent.click(view.getByText("结账检查"));
    await waitFor(() => expect(api.readAgentSkillHubFile).toHaveBeenCalledWith("hub_rev_published", "AGENT.md"));
    expect(view.queryByText("发布")).toBeNull();
    expect(view.queryByText("引用到当前应用")).toBeNull();
  });

  it("writes a published asset to the current workspace and reports the changed Agent path", async () => {
    const view = renderHub({ canManage: true });
    await waitFor(() => expect(view.getByText("结账检查")).toBeTruthy());
    await fireEvent.click(view.getByText("结账检查"));
    await waitFor(() => expect(view.getByText("引用到当前应用")).toBeTruthy());

    await fireEvent.click(view.getByText("引用到当前应用"));
    await fireEvent.click(view.getByText("写入引用"));

    await waitFor(() => expect(api.createAgentSkillHubReference).toHaveBeenCalledWith(
      "wrk_personal", "hub_asset_1", "checkout"
    ));
    expect(view.emitted().changed?.[0]).toEqual([[".opencode/agents/checkout.md"]]);
  });

  it("shows update records and explicit text statuses instead of color-only dots", async () => {
    const published = { ...hubAsset(), referenced: true, referenceStatus: "ACTIVE" };
    const pushed = {
      ...hubAsset(),
      assetId: "hub_asset_unpublished",
      technicalId: "draft-check",
      displayName: "待发布检查",
      publishedRevisionId: null,
      published: false,
      referenced: false,
      publishedAt: null
    };
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [published, pushed], total: 2, page: 1, size: 100 });
    api.listAgentSkillHubUpdates.mockResolvedValue({
      items: [{
        referenceId: "hub_ref_update",
        assetId: published.assetId,
        technicalId: published.technicalId,
        displayName: published.displayName,
        sourceAppName: published.sourceAppName,
        sourceWorkspaceName: published.sourceWorkspaceName,
        activeRevisionId: "hub_rev_old",
        latestRevisionId: "hub_rev_new",
        status: "ACTIVE",
        targetPath: ".opencode/agents/checkout.md",
        publishedAt: "2026-07-25T00:02:00Z"
      }],
      total: 1,
      page: 1,
      size: 100
    });
    const view = renderHub({ canManage: true });

    await waitFor(() => expect(view.getByText("已发布 · 已引用")).toBeTruthy());
    expect(view.getByText("仅已推送")).toBeTruthy();
    await fireEvent.click(view.getByText("待更新"));
    await waitFor(() => expect(view.getByText("待确认更新")).toBeTruthy());
    expect(view.getByLabelText("待更新数量").textContent).toBe("1");
  });

  it("marks a new reference as pending push and supports two-phase cancellation", async () => {
    const pending = { ...hubAsset(), referenceStatus: "PENDING_PUSH", referenced: false };
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [pending], total: 1, page: 1, size: 100 });
    const view = renderHub({ canManage: true });

    await waitFor(() => expect(view.getByText("引用待推送")).toBeTruthy());
    expect(view.queryByText("已发布 · 已引用")).toBeNull();
    await fireEvent.click(view.getByText("结账检查"));
    await fireEvent.click(view.getByText("取消引用"));
    await fireEvent.click(view.getByText("移除并等待推送"));
    await waitFor(() => expect(api.removeAgentSkillHubReference).toHaveBeenCalledWith(
      "wrk_personal", "hub_asset_1"
    ));
  });

  it("shows the current application library and the applications consuming an asset", async () => {
    const referenced = { ...hubAsset(), referenced: true, referenceStatus: "ACTIVE", referenceCount: 2 };
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [referenced], total: 1, page: 1, size: 100 });
    api.getAgentSkillHubAsset.mockResolvedValue({
      asset: referenced,
      selectedRevisionId: "hub_rev_published",
      files: [{ path: "AGENT.md", size: 30, sha256: "a".repeat(64), mediaType: "text/markdown" }],
      dependencies: [],
      consumers: [{
        referenceId: "hub_ref_consumer",
        targetAppId: "app_report",
        targetAppName: "报表应用",
        targetWorkspaceId: "aw_report",
        targetWorkspaceName: "报表服务",
        aliasTechnicalId: "checkout-reviewer",
        targetPath: ".opencode/agents/checkout-reviewer.md",
        status: "ACTIVE",
        updatedAt: "2026-07-25T00:03:00Z"
      }]
    });
    const view = renderHub({ canManage: true });

    await fireEvent.click(await view.findByText("当前应用"));
    await waitFor(() => expect(api.listAgentSkillHubAssets).toHaveBeenCalledWith(expect.objectContaining({
      referencedOnly: true,
      targetWorkspaceId: "wrk_personal"
    })));
    await fireEvent.click(await view.findByText("结账检查"));
    expect(await view.findByText("报表应用")).toBeTruthy();
    expect(view.getByText("报表服务 · checkout-reviewer")).toBeTruthy();
    expect(view.getByText("2 个应用已生效")).toBeTruthy();
  });
});

function renderHub(props: { canManage: boolean }) {
  return render(AgentSkillHub, {
    props: {
      selectedAppId: "app_pay",
      workspaceId: "wrk_personal",
      ...props
    },
    global: {
      provide: { api },
      stubs: { CodeEditor: true, MergeConflictEditor: true }
    }
  });
}

function hubAsset() {
  return {
    assetId: "hub_asset_1",
    type: "AGENT",
    technicalId: "checkout",
    displayName: "结账检查",
    displayNameEn: "Checkout",
    description: "检查结账流程",
    sourceAppId: "app_pay",
    sourceAppName: "支付应用",
    sourceWorkspaceId: "awp_pay",
    sourceWorkspaceName: "支付服务",
    pushedRevisionId: "hub_rev_published",
    publishedRevisionId: "hub_rev_published",
    published: true,
    builtin: false,
    updateAvailable: false,
    referenced: false,
    deleted: false,
    referenceStatus: null,
    referenceCount: 0,
    pushedAt: "2026-07-25T00:00:00Z",
    publishedAt: "2026-07-25T00:01:00Z"
  } as const;
}
