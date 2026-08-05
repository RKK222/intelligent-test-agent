import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor, within } from "@testing-library/vue";
import { nextTick } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, SupportAccessIncidentSuggestion } from "@test-agent/shared-types";
import SupportAccessPanel from "../src/components/system/SupportAccessPanel.vue";

const currentUser: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

function backendApi(
  suggestion: Promise<SupportAccessIncidentSuggestion>
): BackendApiClient {
  return {
    getRecentSupportAccessIncident: vi.fn().mockReturnValue(suggestion),
    listUsers: vi.fn().mockResolvedValue({ items: [], page: 1, size: 100, total: 0 })
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderPanel(api: BackendApiClient) {
  return render(SupportAccessPanel, {
    props: { currentUser },
    global: { provide: { api } }
  });
}

describe("support access panel incident prefill", () => {
  it("prefills the current admin recent persisted incident", async () => {
    const api = backendApi(Promise.resolve({ incidentId: "  INC-PERSISTED  " }));
    const view = renderPanel(api);

    const input = view.getByLabelText("工单号") as HTMLInputElement;
    await waitFor(() => expect(input.value).toBe("INC-PERSISTED"));
    expect(api.getRecentSupportAccessIncident).toHaveBeenCalledTimes(1);
  });

  it("does not overwrite manual input while the suggestion request is pending", async () => {
    let resolveSuggestion!: (value: SupportAccessIncidentSuggestion) => void;
    const suggestion = new Promise<SupportAccessIncidentSuggestion>((resolve) => {
      resolveSuggestion = resolve;
    });
    const view = renderPanel(backendApi(suggestion));
    const input = view.getByLabelText("工单号") as HTMLInputElement;

    await fireEvent.update(input, "INC-MANUAL");
    resolveSuggestion({ incidentId: "INC-PERSISTED" });
    await suggestion;
    await nextTick();

    expect(input.value).toBe("INC-MANUAL");
  });

  it("prefills after the current admin profile arrives asynchronously", async () => {
    const api = backendApi(Promise.resolve({ incidentId: "INC-LATE-ACTOR" }));
    const view = render(SupportAccessPanel, {
      props: { currentUser: null },
      global: { provide: { api } }
    });

    expect(api.getRecentSupportAccessIncident).not.toHaveBeenCalled();
    await view.rerender({ currentUser });

    const input = view.getByLabelText("工单号") as HTMLInputElement;
    await waitFor(() => expect(input.value).toBe("INC-LATE-ACTOR"));
    expect(view.getByText("已自动带入当前管理员最近一次已入库工单")).toBeTruthy();
  });

  it("loads archived sessions only on demand and disables offline workspaces", async () => {
    const listSupportAccessSessions = vi.fn().mockResolvedValue({
      items: [], page: 1, size: 30, total: 0
    });
    const listUsers = vi.fn().mockResolvedValue({
      items: [{
        userId: "usr_target",
        username: "target",
        unifiedAuthId: "AUTH_TARGET",
        status: "ACTIVE",
        roles: [],
        createdAt: "2026-08-05T00:00:00Z"
      }],
      page: 1,
      size: 30,
      total: 1
    });
    const api = {
      getRecentSupportAccessIncident: vi.fn().mockResolvedValue({ incidentId: "INC-1" }),
      listUsers,
      issueSupportAccessGrant: vi.fn().mockResolvedValue({
        grantId: "sag_1",
        grantToken: "grant-token",
        expiresAt: "2099-08-05T00:00:00Z"
      }),
      revokeSupportAccessGrant: vi.fn().mockResolvedValue(undefined),
      closeSupportAccessConnections: vi.fn(),
      selectSupportAccessTarget: vi.fn().mockResolvedValue({
        userId: "usr_target",
        username: "target",
        unifiedAuthId: "AUTH_TARGET",
        status: "ACTIVE"
      }),
      listSupportAccessSessions,
      listSupportAccessWorkspaces: vi.fn().mockResolvedValue({
        items: [{
          workspaceId: "wrk_offline",
          name: "离线工作区",
          rootPath: "/tmp/offline",
          status: "ACTIVE",
          linuxServerId: "server-old",
          backendAvailability: "OFFLINE",
          createdAt: "2026-08-05T00:00:00Z",
          updatedAt: "2026-08-05T00:00:00Z"
        }],
        page: 1,
        size: 30,
        total: 1
      })
    } as Partial<BackendApiClient> as BackendApiClient;
    const view = renderPanel(api);

    await fireEvent.update(view.getByLabelText("排查原因"), "排查历史缺失");
    await fireEvent.click(view.getByLabelText(/我确认仅用于问题排查/));
    await fireEvent.click(view.getByRole("button", { name: "开启限时只读访问" }));
    const targetSelect = within(view.getByTestId("support-target-select"));
    const targetCombobox = targetSelect.getByRole("combobox", { name: "选择目标用户" });
    expect(view.queryByRole("complementary")).toBeNull();
    await fireEvent.update(targetCombobox, "target");
    await waitFor(() => expect(listUsers).toHaveBeenCalledWith({ keyword: "target", page: 1, size: 30 }));
    const targetOption = await waitFor(() => view.getByRole("option", { name: /target.*AUTH_TARGET.*usr_target/ }));
    await fireEvent.click(targetOption);
    await waitFor(() => expect(listSupportAccessSessions).toHaveBeenCalledWith(
      "grant-token",
      "usr_target",
      expect.objectContaining({ includeArchived: false, page: 1 })
    ));

    await fireEvent.click(view.getByLabelText(/包含已归档会话/));
    await waitFor(() => expect(listSupportAccessSessions).toHaveBeenLastCalledWith(
      "grant-token",
      "usr_target",
      expect.objectContaining({ includeArchived: true, page: 1 })
    ));
    await fireEvent.click(view.getByRole("button", { name: /工作区（1）/ }));

    const offlineWorkspace = view.getByRole("button", { name: /离线工作区/ });
    expect(offlineWorkspace).toHaveProperty("disabled", true);
    expect(view.getByText("离线")).toBeTruthy();
  });

  it("renders assistant parts through the same timeline used by the user homepage", async () => {
    const api = {
      getRecentSupportAccessIncident: vi.fn().mockResolvedValue({ incidentId: "INC-1" }),
      listUsers: vi.fn().mockResolvedValue({
        items: [{
          userId: "usr_target",
          username: "target",
          unifiedAuthId: "AUTH_TARGET",
          status: "ACTIVE",
          roles: [],
          createdAt: "2026-08-05T00:00:00Z"
        }],
        page: 1,
        size: 100,
        total: 1
      }),
      issueSupportAccessGrant: vi.fn().mockResolvedValue({
        grantId: "sag_1",
        grantToken: "grant-token",
        expiresAt: "2099-08-05T00:00:00Z"
      }),
      revokeSupportAccessGrant: vi.fn().mockResolvedValue(undefined),
      closeSupportAccessConnections: vi.fn(),
      selectSupportAccessTarget: vi.fn().mockResolvedValue({
        userId: "usr_target",
        username: "target",
        unifiedAuthId: "AUTH_TARGET",
        status: "ACTIVE"
      }),
      listSupportAccessSessions: vi.fn().mockResolvedValue({
        items: [{
          sessionId: "ses_target",
          workspaceId: "wrk_target",
          title: "真实首页投影",
          status: "ACTIVE",
          createdAt: "2026-08-05T00:00:00Z",
          updatedAt: "2026-08-05T00:01:00Z"
        }],
        page: 1,
        size: 30,
        total: 1
      }),
      listSupportAccessWorkspaces: vi.fn().mockResolvedValue({
        items: [], page: 1, size: 30, total: 0
      }),
      getSupportAccessSessionTreeMessages: vi.fn().mockResolvedValue({
        sessionId: "ses_target",
        events: [
          {
            eventId: "evt_user",
            runId: "run_1",
            seq: 1,
            type: "message.updated",
            traceId: "trace_test",
            occurredAt: "2026-08-05T00:00:00Z",
            payload: {
              sessionId: "ses_target",
              rootSessionId: "ses_target",
              message: { id: "msg_user", role: "user", text: "用户问题" }
            }
          },
          {
            eventId: "evt_assistant",
            runId: "run_1",
            seq: 2,
            type: "message.updated",
            traceId: "trace_test",
            occurredAt: "2026-08-05T00:00:01Z",
            payload: {
              sessionId: "ses_target",
              rootSessionId: "ses_target",
              message: { id: "msg_assistant", role: "assistant" }
            }
          },
          {
            eventId: "evt_assistant_part",
            runId: "run_1",
            seq: 3,
            type: "message.part.updated",
            traceId: "trace_test",
            occurredAt: "2026-08-05T00:00:02Z",
            payload: {
              sessionId: "ses_target",
              rootSessionId: "ses_target",
              messageId: "msg_assistant",
              part: {
                id: "part_assistant",
                messageID: "msg_assistant",
                type: "text",
                text: "真实助手正文"
              }
            }
          }
        ],
        messagesBySessionId: {},
        historyRepresentation: "FULL",
        replayAvailable: true,
        detailsAvailableUntil: null
      })
    } as Partial<BackendApiClient> as BackendApiClient;
    const view = renderPanel(api);

    await fireEvent.update(view.getByLabelText("排查原因"), "验证首页只读投影");
    await fireEvent.click(view.getByLabelText(/我确认仅用于问题排查/));
    await fireEvent.click(view.getByRole("button", { name: "开启限时只读访问" }));
    await fireEvent.click(view.getByRole("combobox", { name: "选择目标用户" }));
    const targetOption = await waitFor(() => view.getByRole("option", { name: /target.*AUTH_TARGET.*usr_target/ }));
    await fireEvent.click(targetOption);
    await waitFor(() => expect(view.getByText("真实首页投影")).toBeTruthy());
    await fireEvent.click(view.getByText("真实首页投影"));

    await waitFor(() => expect(view.getByText("真实助手正文")).toBeTruthy());
    expect(view.getByText("用户首页视角")).toBeTruthy();
    expect(view.getByLabelText("只读排查输入区")).toBeTruthy();
    const diagnosticContext = within(view.getByLabelText("会话排查标识"));
    expect(diagnosticContext.getByText("排查标识")).toBeTruthy();
    expect(diagnosticContext.getByText("会话 SESSION ID")).toBeTruthy();
    expect(diagnosticContext.getByText("最近 TRACE ID")).toBeTruthy();
    expect(diagnosticContext.getByText("ses_target")).toBeTruthy();
    expect(diagnosticContext.getByText("trace_test")).toBeTruthy();
  });
});
