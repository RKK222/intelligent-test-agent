import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type {
  WorkflowApiClient,
  WorkflowReport,
  WorkflowRepositoryGroup,
} from "@test-agent/workflow-api-client";

vi.mock("@tdesign-vue-next/chat", async () => {
  const { defineComponent, h } = await import("vue");
  const stub = (name: string) => defineComponent({
    name,
    setup(_, { slots }) {
      return () => h("div", { "data-testid": name }, slots.default?.());
    },
  });
  return {
    Chat: stub("Chat"),
    ChatContent: stub("ChatContent"),
    ChatItem: stub("ChatItem"),
    ChatSender: stub("ChatSender"),
  };
});

import BaselineSelectionCard from "../src/BaselineSelectionCard.vue";
import LocalReanalysisCard from "../src/LocalReanalysisCard.vue";
import ReportPanel from "../src/ReportPanel.vue";
import WorkflowChat from "../src/WorkflowChat.vue";
import WorkflowInputCard from "../src/WorkflowInputCard.vue";

const repositoryGroups: WorkflowRepositoryGroup[] = [
  {
    applicationId: "app_orders",
    applicationName: "订单应用",
    repositories: [
      { id: "repo_orders_12345678", name: "订单代码库", englishName: "orders" },
    ],
  },
];

describe("workflow input cards", () => {
  it("loads branches by repository and submits review mode as structured input", async () => {
    const loadBranches = vi.fn(async () => [
      { name: "main", default: true },
      { name: "feature/impact", default: false },
    ]);
    const view = render(WorkflowInputCard, {
      props: { repositoryGroups, loadBranches },
    });

    const repository = screen.getAllByRole("combobox")[0] as HTMLSelectElement;
    await fireEvent.update(repository, "repo_orders_12345678");
    await waitFor(() => expect(loadBranches).toHaveBeenCalledWith("repo_orders_12345678"));
    const targetBranch = screen.getAllByRole("combobox")[1] as HTMLSelectElement;
    await fireEvent.update(targetBranch, "feature/impact");
    await fireEvent.click(screen.getByText("交叉复核"));
    await fireEvent.click(screen.getByRole("button", { name: "开始影响分析" }));

    const emitted = view.emitted() as Record<string, unknown[][]>;
    expect(emitted.submit?.[0]?.[0]).toEqual({
      repositories: [
        { repositoryId: "repo_orders_12345678", targetBranch: "feature/impact" },
      ],
      mode: "REVIEW",
      analyzerIds: ["codex", "opencode"],
    });
  });

  it("resumes a waiting run with the selected immutable baseline", async () => {
    const view = render(BaselineSelectionCard, {
      props: {
        baselines: [
          { repositoryId: "repo_orders_12345678", availableBranches: ["develop", "release"] },
        ],
        currentInput: {
          repositories: [
            { repositoryId: "repo_orders_12345678", targetBranch: "feature/impact" },
          ],
          mode: "SINGLE",
          analyzerIds: ["codex"],
        },
        repositoryGroups,
      },
    });

    await fireEvent.update(screen.getByRole("combobox"), "develop");
    await fireEvent.click(screen.getByRole("button", { name: "按所选基线继续" }));

    const emitted = view.emitted() as Record<string, unknown[][]>;
    expect(emitted.submit?.[0]?.[0]).toMatchObject({
      repositories: [
        {
          repositoryId: "repo_orders_12345678",
          targetBranch: "feature/impact",
          baselineBranch: "develop",
        },
      ],
    });
  });

  it("submits an explicit file or symbol for local reanalysis", async () => {
    const view = render(LocalReanalysisCard, { props: { repositoryGroups } });
    const selects = screen.getAllByRole("combobox");
    await fireEvent.update(selects[0]!, "repo_orders_12345678");
    await fireEvent.update(selects[1]!, "SYMBOL");
    await fireEvent.update(screen.getByPlaceholderText("输入程序、目录、文件或符号"), "OrderService");
    await fireEvent.click(screen.getByRole("button", { name: "重新分析" }));

    const emitted = view.emitted() as Record<string, unknown[][]>;
    expect(emitted.submit?.[0]?.[0]).toEqual({
      repositoryId: "repo_orders_12345678",
      kind: "SYMBOL",
      value: "OrderService",
    });
  });
});

describe("workflow reports and administration", () => {
  const reports: WorkflowReport[] = [
    {
      id: "report_v2",
      taskId: "task_1",
      runId: "run_2",
      version: 2,
      current: true,
      createdAt: "2026-07-31T00:00:00Z",
      structuredReport: {},
      markdownReport: "# 当前报告\n\n<img src=x onerror=alert(1)>",
    },
    {
      id: "report_v1",
      taskId: "task_1",
      runId: "run_1",
      version: 1,
      current: false,
      createdAt: "2026-07-30T00:00:00Z",
      structuredReport: {},
      markdownReport: "# 历史报告",
    },
  ];

  it("renders sanitized markdown, switches immutable versions and downloads the selection", async () => {
    const view = render(ReportPanel, {
      props: { reports, selectedId: "report_v2" },
    });

    expect(screen.getByRole("heading", { name: "当前报告" })).not.toBeNull();
    expect(document.querySelector("[onerror]")).toBeNull();
    await fireEvent.update(screen.getByRole("combobox"), "report_v1");
    await view.rerender({ reports, selectedId: "report_v1" });
    await fireEvent.click(screen.getByRole("button", { name: "下载 Markdown 报告" }));

    const emitted = view.emitted() as Record<string, unknown[][]>;
    expect(emitted.select?.[0]).toEqual(["report_v1"]);
    expect(emitted.download?.[0]).toEqual(["report_v1"]);
  });

  it("shows cross-user conversation lookup only to SUPER_ADMIN", async () => {
    const conversations = vi.fn(async (_owner?: string) => []);
    const api = {
      me: vi.fn(async () => ({
        userId: "usr_admin",
        username: "管理员",
        unifiedAuthId: "AUTH_ADMIN",
        roles: ["SUPER_ADMIN"],
      })),
      repositories: vi.fn(async () => repositoryGroups),
      conversations,
    } as unknown as WorkflowApiClient;

    render(WorkflowChat, { props: { api } });
    const ownerInput = await screen.findByPlaceholderText("按用户 ID 查看");
    await fireEvent.update(ownerInput, "usr_owner");
    await fireEvent.keyDown(ownerInput, { key: "Enter" });

    await waitFor(() => expect(conversations).toHaveBeenLastCalledWith("usr_owner"));
  });
});
