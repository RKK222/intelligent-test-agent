// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import AgentSkillHub from "../src/components/AgentSkillHub.vue";

const api = {
  listAgentSkillHubAssets: vi.fn(),
  getAgentSkillHubAsset: vi.fn(),
  materializeAgentSkillHubAsset: vi.fn(),
  readAgentSkillHubFile: vi.fn(),
  getAgentSkillHubUpdateCount: vi.fn(),
  listAgentSkillHubUpdates: vi.fn(),
  publishAgentSkillHubAsset: vi.fn(),
  updateAgentSkillHubClassification: vi.fn(),
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
    api.updateAgentSkillHubClassification.mockResolvedValue({
      assetId: "hub_asset_1",
      category: "TEST",
      subcategory: "TEST_DESIGN",
      classifiedByUserId: "usr_admin",
      classifiedAt: "2026-08-06T00:00:00Z"
    });
  });

  afterEach(() => {
    vi.useRealTimers();
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

  it("pauses a debounced catalog search while its page tab is inactive and refreshes it on return", async () => {
    vi.useFakeTimers();
    const view = renderHub({ canManage: false, pageActive: true });
    await vi.waitFor(() => expect(api.listAgentSkillHubAssets).toHaveBeenCalled());
    const initialCalls = api.listAgentSkillHubAssets.mock.calls.length;

    await fireEvent.update(view.getByPlaceholderText("搜索名称、应用或技术 ID"), "checkout");
    await view.rerender({
      selectedAppId: "app_pay",
      workspaceId: "wrk_personal",
      canManage: false,
      pageActive: false
    });
    await vi.advanceTimersByTimeAsync(500);
    expect(api.listAgentSkillHubAssets).toHaveBeenCalledTimes(initialCalls);

    await view.rerender({
      selectedAppId: "app_pay",
      workspaceId: "wrk_personal",
      canManage: false,
      pageActive: true
    });
    await vi.waitFor(() => expect(api.listAgentSkillHubAssets).toHaveBeenCalledTimes(initialCalls + 1));
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

  it("rechecks management capability when an already-open mutation dialog is submitted", async () => {
    const view = renderHub({ canManage: true });
    await waitFor(() => expect(view.getByText("结账检查")).toBeTruthy());
    await fireEvent.click(view.getByText("结账检查"));
    await fireEvent.click(await view.findByText("引用到当前应用"));

    await view.rerender({
      selectedAppId: "app_pay",
      workspaceId: "wrk_personal",
      canManage: false
    });

    expect(view.queryByText("写入引用")).toBeNull();

    expect(api.createAgentSkillHubReference).not.toHaveBeenCalled();
    expect(view.emitted().changed).toBeUndefined();
  });

  it("keeps catalog status independent from the current application's reference state", async () => {
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

    await waitFor(() => expect(view.getByText("已发布")).toBeTruthy());
    expect(view.queryByText("已发布 · 已引用")).toBeNull();
    expect(view.getByText("仅已推送")).toBeTruthy();
    await fireEvent.click(view.getByText("待更新"));
    await waitFor(() => expect(view.getByText("待确认更新")).toBeTruthy());
    expect(view.getByLabelText("待更新数量").textContent).toBe("1");
  });

  it("removes a cancelled reference from the current application immediately", async () => {
    const pending = { ...hubAsset(), referenceStatus: "PENDING_PUSH", referenced: false };
    let removed = false;
    api.listAgentSkillHubAssets.mockImplementation(async (query) => query.referencedOnly && removed
      ? { items: [], total: 0, page: 1, size: query.size }
      : { items: [pending], total: 1, page: 1, size: query.size });
    api.removeAgentSkillHubReference.mockImplementation(async () => {
      removed = true;
      return {
        referenceId: "hub_ref_1",
        assetId: "hub_asset_1",
        targetPath: ".opencode/agents/checkout.md",
        aliasTechnicalId: "checkout",
        activeRevisionId: null,
        pendingRevisionId: null,
        status: "PENDING_REMOVE",
        runtimeReloadRequired: true,
        message: "已从当前个人 worktree 移除"
      };
    });
    const view = renderHub({ canManage: true });

    await fireEvent.click(await view.findByText("当前应用"));
    await waitFor(() => expect(view.getByText("引用待推送")).toBeTruthy());
    await fireEvent.click(view.getByText("结账检查"));
    await fireEvent.click(view.getByText("取消引用"));
    await fireEvent.click(view.getByText("移除并等待推送"));
    await waitFor(() => expect(api.removeAgentSkillHubReference).toHaveBeenCalledWith(
      "wrk_personal", "hub_asset_1"
    ));
    await waitFor(() => expect(view.getByText("当前应用还没有引用能力")).toBeTruthy());
    expect(view.queryByText("取消待推送")).toBeNull();
  });

  it("shows ownership in the Skill catalog and allows a cancelled asset to be referenced again", async () => {
    const removing = {
      ...hubAsset(), type: "SKILL", technicalId: "api-check", displayName: "接口检查",
      referenceStatus: "PENDING_REMOVE", referenced: false, referenceCount: 0
    } as const;
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [removing], total: 1, page: 1, size: 100 });
    api.getAgentSkillHubAsset.mockResolvedValue({
      asset: removing,
      selectedRevisionId: "hub_rev_published",
      files: [{ path: "SKILL.md", size: 30, sha256: "a".repeat(64), mediaType: "text/markdown" }],
      dependencies: [],
      consumers: []
    });
    const view = renderHub({ canManage: true });

    await fireEvent.click(await view.findByRole("button", { name: "Skill" }));
    await waitFor(() => expect(view.getByText("接口检查")).toBeTruthy());
    expect(view.getByText("SKILL · MIMO")).toBeTruthy();
    expect(view.getByText("已发布")).toBeTruthy();
    expect(view.getByText("归属工作区：支付服务")).toBeTruthy();
    expect(view.queryByText("取消待推送")).toBeNull();
    await fireEvent.click(view.getByText("接口检查"));
    await fireEvent.click(await view.findByText("引用到当前应用"));
    await fireEvent.click(view.getByText("写入引用"));
    await waitFor(() => expect(api.createAgentSkillHubReference).toHaveBeenCalledWith(
      "wrk_personal", "hub_asset_1", "api-check"
    ));
  });

  it("filters the Skill catalog by controlled category and concrete test matter", async () => {
    const skill = {
      ...hubAsset(), type: "SKILL", technicalId: "case-design", displayName: "测试案例设计",
      category: "TEST", subcategory: "TEST_DESIGN"
    } as const;
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [skill], total: 1, page: 1, size: 100 });
    const view = renderHub({ canManage: false });

    await fireEvent.click(await view.findByRole("button", { name: "Skill" }));
    await fireEvent.click(view.getByRole("button", { name: "测试" }));
    await waitFor(() => expect(api.listAgentSkillHubAssets).toHaveBeenCalledWith(expect.objectContaining({
      type: "SKILL",
      category: "TEST",
      subcategory: undefined
    })));
    await fireEvent.click(view.getByRole("button", { name: "测试设计" }));
    await waitFor(() => expect(api.listAgentSkillHubAssets).toHaveBeenCalledWith(expect.objectContaining({
      type: "SKILL",
      category: "TEST",
      subcategory: "TEST_DESIGN"
    })));
    expect(view.getByText("测试 · 测试设计")).toBeTruthy();
  });

  it("filters SkillMarket Skills and downloads content only after explicit preview", async () => {
    const external = {
      ...hubAsset(), assetId: "hub_asset_external", type: "SKILL", technicalId: "case-design",
      displayName: "外部测试设计", sourceAppId: null, sourceWorkspaceId: null,
      sourceAppName: "SkillHub", sourceWorkspaceName: "外部能力目录",
      pushedRevisionId: null, publishedRevisionId: null, published: false,
      sourceKind: "SKILLHUB", sourceAvailable: true, contentAvailable: false,
      externalSkillId: 42, externalVersion: "1.2.0", externalPhaseName: "稳定",
      externalContributor: "000831611\r\n", externalContributorName: "徐丽娜"
    } as const;
    const materialized = {
      ...external, pushedRevisionId: "hub_rev_external", publishedRevisionId: "hub_rev_external",
      published: true, contentAvailable: true
    } as const;
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [external], total: 1, page: 1, size: 100 });
    api.getAgentSkillHubAsset.mockResolvedValue({
      asset: external, selectedRevisionId: null, files: [], dependencies: [], consumers: []
    });
    api.materializeAgentSkillHubAsset.mockResolvedValue({
      asset: materialized,
      selectedRevisionId: "hub_rev_external",
      files: [{ path: "SKILL.md", size: 30, sha256: "a".repeat(64), mediaType: "text/markdown" }],
      dependencies: [], consumers: []
    });
    const view = renderHub({ canManage: true });

    await fireEvent.click(await view.findByRole("button", { name: "Skill" }));
    await fireEvent.click(view.getByRole("button", { name: "SkillMarket" }));
    await waitFor(() => expect(api.listAgentSkillHubAssets).toHaveBeenCalledWith(expect.objectContaining({
      type: "SKILL", source: "SKILLHUB"
    })));
    expect(view.getByText("SKILL · SkillMarket")).toBeTruthy();
    expect(await view.findByText("创建人：徐丽娜")).toBeTruthy();
    await fireEvent.click(view.getByText("外部测试设计"));
    expect(await view.findByText(/版本 1\.2\.0 .* 创建人：徐丽娜/)).toBeTruthy();
    expect(api.materializeAgentSkillHubAsset).not.toHaveBeenCalled();
    await fireEvent.click(await view.findByText("预览内容"));
    await waitFor(() => expect(api.materializeAgentSkillHubAsset).toHaveBeenCalledWith(
      "hub_asset_external", "wrk_personal"
    ));
    expect(api.readAgentSkillHubFile).toHaveBeenCalledWith("hub_rev_external", "SKILL.md");
  });

  it("lets only a super administrator classify a user-pushed Skill in the detail page", async () => {
    const skill = {
      ...hubAsset(), type: "SKILL", technicalId: "case-design", displayName: "测试案例设计",
      category: "OTHER", subcategory: null
    } as const;
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [skill], total: 1, page: 1, size: 100 });
    api.getAgentSkillHubAsset.mockResolvedValue({
      asset: skill,
      selectedRevisionId: "hub_rev_published",
      files: [{ path: "SKILL.md", size: 30, sha256: "a".repeat(64), mediaType: "text/markdown" }],
      dependencies: [],
      consumers: []
    });
    const view = renderHub({ canManage: false, canClassifySkills: true });

    await fireEvent.click(await view.findByRole("button", { name: "Skill" }));
    await fireEvent.click(await view.findByText("测试案例设计"));
    await fireEvent.update(await view.findByLabelText("Skill 一级分类"), "TEST");
    await fireEvent.update(await view.findByLabelText("Skill 具体事项"), "TEST_DESIGN");
    await fireEvent.click(view.getByRole("button", { name: "保存分类" }));

    await waitFor(() => expect(api.updateAgentSkillHubClassification).toHaveBeenCalledWith(
      "hub_asset_1", "TEST", "TEST_DESIGN"
    ));
  });

  it("lets a super administrator classify a public Git Skill", async () => {
    const skill = {
      ...hubAsset(),
      assetId: "hub_builtin_SKILL_d2hpdGUtYm94LWFuYWx5c2lz",
      type: "SKILL",
      technicalId: "white-box-analysis",
      displayName: "白盒分析",
      category: "OTHER",
      subcategory: null,
      builtin: true,
      sourceAppId: "platform",
      sourceAppName: "平台内置",
      sourceWorkspaceId: "public",
      sourceWorkspaceName: "公共配置"
    } as const;
    api.listAgentSkillHubAssets.mockResolvedValue({ items: [skill], total: 1, page: 1, size: 100 });
    api.getAgentSkillHubAsset.mockResolvedValue({
      asset: skill,
      selectedRevisionId: "hub_builtin_rev_1",
      files: [{ path: "SKILL.md", size: 30, sha256: "a".repeat(64), mediaType: "text/markdown" }],
      dependencies: [],
      consumers: []
    });
    const view = renderHub({ canManage: false, canClassifySkills: true });

    await fireEvent.click(await view.findByRole("button", { name: "Skill" }));
    await fireEvent.click(await view.findByText("白盒分析"));
    await fireEvent.update(await view.findByLabelText("Skill 一级分类"), "CODE");
    await fireEvent.update(await view.findByLabelText("Skill 具体事项"), "WHITE_BOX_ANALYSIS");
    await fireEvent.click(view.getByRole("button", { name: "保存分类" }));

    await waitFor(() => expect(api.updateAgentSkillHubClassification).toHaveBeenCalledWith(
      skill.assetId, "CODE", "WHITE_BOX_ANALYSIS"
    ));
  });

  it("does not let a late initial catalog response overwrite the current application tab", async () => {
    let resolveCatalog!: (value: unknown) => void;
    const lateCatalog = new Promise((resolve) => { resolveCatalog = resolve; });
    const current = { ...hubAsset(), assetId: "hub_asset_current", displayName: "当前引用" };
    const stale = { ...hubAsset(), assetId: "hub_asset_stale", displayName: "迟到的全量能力" };
    api.listAgentSkillHubAssets.mockImplementation((query) => {
      if (query.size === 1) return Promise.resolve({ items: [], total: 0, page: 1, size: 1 });
      if (query.referencedOnly) return Promise.resolve({ items: [current], total: 1, page: 1, size: 100 });
      return lateCatalog;
    });
    const view = renderHub({ canManage: true });

    await fireEvent.click(view.getByText("当前应用"));
    await waitFor(() => expect(view.getByText("当前引用")).toBeTruthy());
    resolveCatalog({ items: [stale], total: 1, page: 1, size: 100 });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(view.getByText("当前引用")).toBeTruthy();
    expect(view.queryByText("迟到的全量能力")).toBeNull();
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

  it("keeps MCP and Tool aligned with the runtime inventory and supports resizable fullscreen detail", async () => {
    const view = renderHub({
      canManage: true,
      runtimeMcp: [{ id: "filesystem", name: "filesystem", status: "connected", description: "文件能力" }],
      runtimeTools: [{ id: "read", name: "read", status: "builtin", description: "读取文件" }]
    });

    await waitFor(() => expect(view.getByLabelText("Hub 概览").textContent).toContain("1 MCP"));
    expect(view.getByLabelText("Hub 概览").textContent).toContain("1 Tool");
    await fireEvent.click(view.getByRole("button", { name: "MCP" }));
    const mcpLabels = await view.findAllByText("filesystem");
    expect(mcpLabels.length).toBeGreaterThan(0);
    expect(view.emitted().refreshRuntime).toBeTruthy();

    await fireEvent.click(mcpLabels[0]!);
    const drawer = view.getByTestId("hub-detail-drawer");
    const resizeHandle = view.getByRole("button", { name: "调整 Hub 详情宽度" });
    expect(drawer.getAttribute("data-layout-mode")).toBe("window");
    expect((drawer.querySelector(".hub-detail-panel") as HTMLElement).style.width).toBe("640px");

    await fireEvent.keyDown(resizeHandle, { key: "ArrowLeft" });
    expect((drawer.querySelector(".hub-detail-panel") as HTMLElement).style.width).toBe("656px");
    await fireEvent.click(view.getByRole("button", { name: "进入全屏" }));
    expect(view.getByTestId("hub-detail-drawer").getAttribute("data-layout-mode")).toBe("fullscreen");
    expect((view.getByTestId("hub-detail-drawer").querySelector(".hub-detail-panel") as HTMLElement).style.width).toBe("100vw");
    expect(view.queryByRole("button", { name: "调整 Hub 详情宽度" })).toBeNull();

    await fireEvent.click(view.getByRole("button", { name: "退出全屏" }));
    await fireEvent.click(view.getByRole("button", { name: "关闭 Hub 详情" }));
    await fireEvent.click(view.getByRole("button", { name: "Tool" }));
    expect(await view.findByText("读取文件")).toBeTruthy();
    expect(view.getAllByText("read").length).toBeGreaterThan(0);
  });
});

function renderHub(props: {
  canManage: boolean;
  pageActive?: boolean;
  canClassifySkills?: boolean;
  runtimeMcp?: Array<{ id: string; name: string; status?: string; description?: string }>;
  runtimeTools?: Array<{ id: string; name: string; status?: string; description?: string }>;
}) {
  return render(AgentSkillHub, {
    props: {
      selectedAppId: "app_pay",
      workspaceId: "wrk_personal",
      pageActive: true,
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
    category: "OTHER",
    subcategory: null,
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
