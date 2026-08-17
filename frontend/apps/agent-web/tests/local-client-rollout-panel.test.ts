// @vitest-environment jsdom

import { cleanup, fireEvent, render, waitFor, within } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import LocalClientRolloutPanel from "../src/components/system/LocalClientRolloutPanel.vue";

function api() {
  return {
    listLocalClientRolloutUsers: vi.fn().mockResolvedValue({
      items: [{
        userId: "usr_enabled",
        enabled: true,
        updatedByUserId: "usr_admin",
        createdAt: "2026-08-17T10:00:00Z",
        updatedAt: "2026-08-17T10:00:00Z"
      }],
      page: 1,
      size: 200,
      total: 1
    }),
    listUsers: vi.fn().mockResolvedValue({
      items: [{
        userId: "usr_candidate",
        username: "灰度用户",
        unifiedAuthId: "AUTH_CANDIDATE",
        status: "ACTIVE",
        roles: ["USER"],
        createdAt: "2026-08-17T10:00:00Z"
      }, {
        userId: "usr_inactive",
        username: "停用用户",
        unifiedAuthId: "AUTH_INACTIVE",
        status: "INACTIVE",
        roles: ["USER"],
        createdAt: "2026-08-17T10:00:00Z"
      }],
      page: 1,
      size: 30,
      total: 2
    }),
    enableLocalClientRolloutUser: vi.fn().mockResolvedValue({
      userId: "usr_candidate",
      enabled: true,
      updatedByUserId: "usr_admin",
      createdAt: "2026-08-17T10:00:00Z",
      updatedAt: "2026-08-17T10:00:00Z"
    }),
    disableLocalClientRolloutUser: vi.fn().mockResolvedValue(undefined)
  } as Partial<BackendApiClient> as BackendApiClient;
}

describe("LocalClientRolloutPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("lists rollout users and adds an active platform user by userId", async () => {
    const backendApi = api();
    const view = render(LocalClientRolloutPanel, { global: { provide: { api: backendApi } } });

    expect(await view.findByText("usr_enabled")).toBeTruthy();
    await fireEvent.click(view.getByTestId("add-local-client-rollout-user"));
    const dialog = await view.findByRole("dialog", { name: "添加本地客户端灰度用户" });
    const select = within(dialog).getByRole("combobox", { name: "选择本地客户端灰度用户" });
    await fireEvent.update(select, "candidate");
    await waitFor(() => expect(backendApi.listUsers).toHaveBeenCalledWith({
      keyword: "candidate", page: 1, size: 30
    }));
    expect(view.queryByRole("option", { name: /停用用户/ })).toBeNull();
    await fireEvent.click(await view.findByRole("option", { name: /灰度用户.*AUTH_CANDIDATE.*usr_candidate/ }));
    await fireEvent.click(within(dialog).getByRole("button", { name: "确认添加" }));

    await waitFor(() => expect(backendApi.enableLocalClientRolloutUser).toHaveBeenCalledWith("usr_candidate"));
  });

  it("shows that download is hidden for everyone when the rollout list is empty", async () => {
    const backendApi = api();
    vi.mocked(backendApi.listLocalClientRolloutUsers).mockResolvedValue({ items: [], page: 1, size: 200, total: 0 });
    const view = render(LocalClientRolloutPanel, { global: { provide: { api: backendApi } } });

    expect(await view.findByText("当前没有灰度用户，下载入口对所有用户隐藏")).toBeTruthy();
  });
});
