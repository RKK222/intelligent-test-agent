import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type {
  WorkflowAgUiEvent,
  WorkflowApiClient,
  WorkflowReport,
  WorkflowRepositoryGroup,
  WorkflowSubmission,
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

  it("closes the previous conversation stream before a super administrator changes owner", async () => {
    const close = vi.fn();
    const conversations = vi.fn(async (owner?: string) => owner ? [] : [{
      id: "conv_current",
      title: "当前用户对话",
      ownerUserId: "usr_admin",
      createdAt: "2026-07-31T00:00:00Z",
    }]);
    const api = {
      me: vi.fn(async () => ({
        userId: "usr_admin",
        username: "管理员",
        unifiedAuthId: "AUTH_ADMIN",
        roles: ["SUPER_ADMIN"],
      })),
      repositories: vi.fn(async () => repositoryGroups),
      conversations,
      conversation: vi.fn(async () => ({
        id: "conv_current",
        title: "当前用户对话",
        ownerUserId: "usr_admin",
        createdAt: "2026-07-31T00:00:00Z",
        messages: [],
      })),
      connectEvents: vi.fn(() => ({
        close,
        done: new Promise<void>(() => undefined),
        lastEventId: () => undefined,
      })),
    } as unknown as WorkflowApiClient;

    render(WorkflowChat, { props: { api } });
    await waitFor(() => expect(api.connectEvents).toHaveBeenCalledTimes(1));
    const ownerInput = screen.getByPlaceholderText("按用户 ID 查看");
    await fireEvent.update(ownerInput, "usr_owner");
    await fireEvent.keyDown(ownerInput, { key: "Enter" });

    await waitFor(() => expect(conversations).toHaveBeenLastCalledWith("usr_owner"));
    expect(close).toHaveBeenCalledTimes(1);
  });

  it("lets a waiting scope run submit a corrected selector when no candidate was found", async () => {
    let publishEvent: ((event: WorkflowAgUiEvent) => void) | undefined;
    const submitMessage = vi.fn(async () => ({
      messageId: "msg_scope_retry",
      assistantMessageId: "msg_scope_assistant",
      taskId: "task_12345678",
      runId: "run_12345678",
      status: "QUEUED",
      requiredInput: [],
    }));
    const conversation = {
      id: "conv_scope",
      title: "局部重分析",
      ownerUserId: "usr_owner",
      createdAt: "2026-07-31T00:00:00Z",
      messages: [],
    };
    const api = {
      me: vi.fn(async () => ({
        userId: "usr_owner",
        username: "分析用户",
        unifiedAuthId: "AUTH_OWNER",
        roles: [],
      })),
      repositories: vi.fn(async () => repositoryGroups),
      conversations: vi.fn(async () => [conversation]),
      conversation: vi.fn(async () => conversation),
      connectEvents: vi.fn((_conversationId, onEvent) => {
        publishEvent = onEvent;
        return {
          close: vi.fn(),
          done: new Promise<void>(() => undefined),
          lastEventId: () => undefined,
        };
      }),
      submitMessage,
    } as unknown as WorkflowApiClient;

    render(WorkflowChat, { props: { api } });
    await waitFor(() => expect(publishEvent).toBeTypeOf("function"));
    publishEvent?.({
      type: "CUSTOM",
      name: "workflow.input_required",
      value: {
        kind: "SCOPE_DISAMBIGUATION",
        scope: [{
          selector: {
            repositoryId: "repo_orders_12345678",
            kind: "SYMBOL",
            value: "MissingOrderService",
          },
          reason: "NOT_FOUND",
          candidatePaths: [],
        }, {
          selector: {
            repositoryId: "repo_orders_12345678",
            kind: "DIRECTORY",
            value: "orders",
          },
          reason: "AMBIGUOUS",
          candidatePaths: ["src/orders", "tests/orders"],
        }],
        currentInput: {
          repositories: [{
            repositoryId: "repo_orders_12345678",
            targetBranch: "feature/impact",
          }],
          mode: "SINGLE",
          analyzerIds: ["codex"],
          scopeSelectors: [{
            repositoryId: "repo_orders_12345678",
            kind: "FILE",
            value: "src/resolved/PaymentService.java",
          }, {
            repositoryId: "repo_orders_12345678",
            kind: "SYMBOL",
            value: "MissingOrderService",
          }, {
            repositoryId: "repo_orders_12345678",
            kind: "DIRECTORY",
            value: "orders",
          }],
        },
      },
    });

    const selectorInput = (await screen.findAllByPlaceholderText("输入程序、目录、文件或符号"))[0]!;
    const selects = screen.getAllByRole("combobox");
    await fireEvent.update(selects[0]!, "repo_orders_12345678");
    await fireEvent.update(selects[1]!, "FILE");
    await fireEvent.update(selectorInput, "src/OrderService.java");
    await fireEvent.click(screen.getAllByRole("button", { name: "重新分析" })[0]!);

    await waitFor(() => expect(submitMessage).toHaveBeenCalledWith(
      "conv_scope",
      expect.objectContaining({
        structuredInput: {
          scopeSelectors: [
            {
              repositoryId: "repo_orders_12345678",
              kind: "FILE",
              value: "src/resolved/PaymentService.java",
            },
            {
              repositoryId: "repo_orders_12345678",
              kind: "FILE",
              value: "src/OrderService.java",
            },
            {
              repositoryId: "repo_orders_12345678",
              kind: "DIRECTORY",
              value: "orders",
            },
          ],
        },
      }),
    ));
  });

  it("keeps a newer SSE scope request when it arrives before the submission response", async () => {
    let publishEvent: ((event: WorkflowAgUiEvent) => void) | undefined;
    let resolveSubmission: ((value: WorkflowSubmission) => void) | undefined;
    const submitMessage = vi.fn(() => new Promise<WorkflowSubmission>((resolve) => {
      resolveSubmission = resolve;
    }));
    const conversation = {
      id: "conv_scope_race",
      title: "局部重分析竞态",
      ownerUserId: "usr_owner",
      createdAt: "2026-07-31T00:00:00Z",
      messages: [],
    };
    const api = {
      me: vi.fn(async () => ({
        userId: "usr_owner",
        username: "分析用户",
        unifiedAuthId: "AUTH_OWNER",
        roles: [],
      })),
      repositories: vi.fn(async () => repositoryGroups),
      conversations: vi.fn(async () => [conversation]),
      conversation: vi.fn(async () => conversation),
      connectEvents: vi.fn((_conversationId, onEvent) => {
        publishEvent = onEvent;
        return {
          close: vi.fn(),
          done: new Promise<void>(() => undefined),
          lastEventId: () => undefined,
        };
      }),
      submitMessage,
      reports: vi.fn(async () => []),
    } as unknown as WorkflowApiClient;

    render(WorkflowChat, { props: { api } });
    await waitFor(() => expect(publishEvent).toBeTypeOf("function"));
    publishEvent?.({
      type: "CUSTOM",
      name: "workflow.input_required",
      value: {
        runId: "run_scope_race",
        taskId: "task_scope_race",
        kind: "SCOPE_DISAMBIGUATION",
        scope: [{
          selector: {
            repositoryId: "repo_orders_12345678",
            kind: "SYMBOL",
            value: "MissingOrderService",
          },
          reason: "NOT_FOUND",
          candidatePaths: [],
        }],
        currentInput: {
          scopeSelectors: [{
            repositoryId: "repo_orders_12345678",
            kind: "SYMBOL",
            value: "MissingOrderService",
          }],
        },
      },
    });

    await fireEvent.update(
      await screen.findByPlaceholderText("输入程序、目录、文件或符号"),
      "src/OrderService.java",
    );
    await fireEvent.click(screen.getByRole("button", { name: "重新分析" }));
    await waitFor(() => expect(submitMessage).toHaveBeenCalledTimes(1));

    // Worker很快再次进入WAITING_INPUT时，SSE可以早于仍在途中的POST响应到达。
    publishEvent?.({
      type: "CUSTOM",
      name: "workflow.input_required",
      value: {
        runId: "run_scope_race",
        taskId: "task_scope_race",
        kind: "SCOPE_DISAMBIGUATION",
        scope: [{
          selector: {
            repositoryId: "repo_orders_12345678",
            kind: "DIRECTORY",
            value: "orders",
          },
          reason: "NOT_FOUND",
          candidatePaths: [],
        }],
        currentInput: {
          scopeSelectors: [{
            repositoryId: "repo_orders_12345678",
            kind: "DIRECTORY",
            value: "orders",
          }],
        },
      },
    });
    await waitFor(() => expect(screen.getByDisplayValue("orders")).toBeTruthy());

    resolveSubmission?.({
      messageId: "msg_scope_race",
      assistantMessageId: null,
      taskId: "task_scope_race",
      runId: "run_scope_race",
      status: "QUEUED",
      requiredInput: [],
    });

    await waitFor(() => expect(screen.getByDisplayValue("orders")).toBeTruthy());
    expect(screen.getByText("等待补充")).toBeTruthy();

    const secondSubmit = screen.getByRole("button", { name: "重新分析" }) as HTMLButtonElement;
    await waitFor(() => expect(secondSubmit.disabled).toBe(false));
    await fireEvent.click(secondSubmit);
    await waitFor(() => expect(submitMessage).toHaveBeenCalledTimes(2));
    publishEvent?.({
      type: "RUN_FINISHED",
      runId: "run_scope_race",
      taskId: "task_scope_race",
      status: "SUCCEEDED",
    });
    await waitFor(() => expect(screen.getByText("已完成")).toBeTruthy());
    await waitFor(() => expect(screen.queryByDisplayValue("orders")).toBeNull());
    resolveSubmission?.({
      messageId: "msg_scope_race_finished",
      assistantMessageId: null,
      taskId: "task_scope_race",
      runId: "run_scope_race",
      status: "QUEUED",
      requiredInput: [],
    });

    await waitFor(() => expect(screen.getByText("已完成")).toBeTruthy());
    expect(screen.queryByRole("button", { name: "重新分析" })).toBeNull();
  });
});
