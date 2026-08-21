import { fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ElMessageBox } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, LocalClientCredential } from "@test-agent/shared-types";
import { copyTextToClipboard } from "@test-agent/ui-kit";
import SettingsPersonalPanel from "../src/components/settings/SettingsPersonalPanel.vue";

vi.mock("@test-agent/ui-kit", () => ({
  copyTextToClipboard: vi.fn()
}));

const user: CurrentUser = {
  userId: "usr_user",
  username: "user",
  unifiedAuthId: "AUTH_USER",
  roles: ["USER"]
};

const otherUser: CurrentUser = {
  userId: "usr_other",
  username: "other",
  unifiedAuthId: "AUTH_OTHER",
  roles: ["USER"]
};

const missingCredential: LocalClientCredential = {
  exists: false,
  version: 0,
  revealAvailable: false
};

const revealableCredential: LocalClientCredential = {
  exists: true,
  maskedKey: "tack_v1_ABC...WXYZ",
  version: 1,
  status: "ACTIVE",
  revealAvailable: true
};

const consumedCredential: LocalClientCredential = {
  ...revealableCredential,
  revealAvailable: false
};

const dialogStub = {
  props: ["modelValue", "title"],
  emits: ["update:modelValue", "close", "closed"],
  template: `
    <div v-if="modelValue" role="dialog" class="el-dialog-stub">
      <h3>{{ title }}</h3>
      <button type="button" aria-label="原生关闭" @click="$emit('close')">原生关闭</button>
      <slot />
      <slot name="footer" />
    </div>
  `
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function api(initial: LocalClientCredential): BackendApiClient {
  return {
    ...({} as BackendApiClient),
    listPersonalSshKeys: vi.fn().mockResolvedValue([]),
    getMyLocalClientCredential: vi.fn().mockResolvedValue(initial),
    createMyLocalClientCredential: vi.fn(),
    copyMyLocalClientCredential: vi.fn(),
    rotateMyLocalClientCredential: vi.fn(),
    revokeMyLocalClientCredential: vi.fn(),
    listMyLocalClientInstances: vi.fn().mockResolvedValue([]),
    listWorkspaces: vi.fn().mockResolvedValue({ items: [], page: 1, size: 100, total: 0 })
  };
}

function renderPanel(client: BackendApiClient, currentUser: CurrentUser = user) {
  return render(SettingsPersonalPanel, {
    props: { currentUser, pageActive: true },
    global: {
      provide: { api: client },
      stubs: { "el-dialog": dialogStub }
    }
  });
}

describe("SettingsPersonalPanel one-time Client key reveal", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.mocked(copyTextToClipboard).mockReset();
  });

  it("consumes a newly created key once, displays it in a one-time dialog and clears it on close", async () => {
    const client = api(missingCredential);
    vi.mocked(client.getMyLocalClientCredential)
      .mockResolvedValueOnce(missingCredential)
      .mockResolvedValue(consumedCredential);
    vi.mocked(client.createMyLocalClientCredential).mockResolvedValue(revealableCredential);
    vi.mocked(client.copyMyLocalClientCredential).mockResolvedValue({ clientKey: "tack_v1_created_secret" });
    vi.mocked(copyTextToClipboard).mockResolvedValue(true);
    const view = renderPanel(client);

    const createButton = await view.findByRole("button", { name: "创建" });
    await waitFor(() => expect((createButton as HTMLButtonElement).disabled).toBe(false));
    await fireEvent.click(createButton);

    await waitFor(() => expect(client.copyMyLocalClientCredential).toHaveBeenCalledTimes(1));
    expect(view.getByRole("dialog")).toBeTruthy();
    expect(view.getByText("tack_v1_created_secret")).toBeTruthy();
    expect(view.queryByRole("button", { name: "显示 Client key" })).toBeNull();

    await fireEvent.click(view.getByRole("button", { name: "复制到剪贴板" }));
    expect(copyTextToClipboard).toHaveBeenCalledWith("tack_v1_created_secret");

    await fireEvent.click(view.getByRole("button", { name: "关闭" }));
    await waitFor(() => expect(view.queryByText("tack_v1_created_secret")).toBeNull());
  });

  it("automatically consumes a rotated key and clears plaintext when the page or user changes", async () => {
    const client = api(consumedCredential);
    vi.mocked(client.getMyLocalClientCredential)
      .mockResolvedValueOnce(consumedCredential)
      .mockResolvedValue(consumedCredential);
    vi.mocked(client.rotateMyLocalClientCredential).mockResolvedValue({
      ...revealableCredential,
      version: 2
    });
    vi.mocked(client.copyMyLocalClientCredential).mockResolvedValue({ clientKey: "tack_v1_rotated_secret" });
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const view = renderPanel(client);

    const rotateButton = await view.findByRole("button", { name: "轮换" });
    await waitFor(() => expect((rotateButton as HTMLButtonElement).disabled).toBe(false));
    await fireEvent.click(rotateButton);
    await waitFor(() => expect(view.getByText("tack_v1_rotated_secret")).toBeTruthy());

    await view.rerender({ currentUser: user, pageActive: false });
    await waitFor(() => expect(view.queryByText("tack_v1_rotated_secret")).toBeNull());

    vi.mocked(client.getMyLocalClientCredential).mockResolvedValueOnce(revealableCredential);
    await view.rerender({ currentUser: user, pageActive: true });
    vi.mocked(client.copyMyLocalClientCredential).mockResolvedValueOnce({
      clientKey: "tack_v1_user_change_secret"
    });
    await waitFor(() => expect(view.getByRole("button", { name: "显示 Client key" })).toBeTruthy());
    await fireEvent.click(view.getByRole("button", { name: "显示 Client key" }));
    await waitFor(() => expect(view.getByText("tack_v1_user_change_secret")).toBeTruthy());

    await view.rerender({ currentUser: otherUser, pageActive: true });
    await waitFor(() => expect(view.queryByText("tack_v1_user_change_secret")).toBeNull());
  });

  it.each([
    ["创建", "createMyLocalClientCredential", "tack_v1_create_late_user_change", "user-change"],
    ["轮换", "rotateMyLocalClientCredential", "tack_v1_rotate_late_user_change", "user-change"],
    ["创建", "createMyLocalClientCredential", "tack_v1_create_late_inactive", "inactive"],
    ["轮换", "rotateMyLocalClientCredential", "tack_v1_rotate_late_inactive", "inactive"]
  ] as const)("does not consume or display a late %s response after %s", async (buttonName, methodName, plaintext, invalidation) => {
    const client = api(methodName === "rotateMyLocalClientCredential" ? consumedCredential : missingCredential);
    const lateResponse = deferred<LocalClientCredential>();
    vi.mocked(client[methodName]).mockReturnValue(lateResponse.promise as never);
    vi.mocked(client.copyMyLocalClientCredential).mockResolvedValue({ clientKey: plaintext });
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const view = renderPanel(client);

    const actionButton = await view.findByRole("button", { name: buttonName });
    await waitFor(() => expect((actionButton as HTMLButtonElement).disabled).toBe(false));
    await fireEvent.click(actionButton);
    await waitFor(() => expect(client[methodName]).toHaveBeenCalledTimes(1));

    if (invalidation === "user-change") {
      await view.rerender({ currentUser: otherUser, pageActive: true });
    } else {
      await view.rerender({ currentUser: user, pageActive: false });
    }
    lateResponse.resolve({ ...revealableCredential, version: buttonName === "轮换" ? 2 : 1 });
    await Promise.resolve();
    await Promise.resolve();

    expect(client.copyMyLocalClientCredential).not.toHaveBeenCalled();
    expect(view.queryByText(plaintext)).toBeNull();
  });

  it("does not consume or display a late credential response after unmount", async () => {
    const client = api(missingCredential);
    const lateResponse = deferred<LocalClientCredential>();
    vi.mocked(client.createMyLocalClientCredential).mockReturnValue(lateResponse.promise);
    const view = renderPanel(client);

    const createButton = await view.findByRole("button", { name: "创建" });
    await waitFor(() => expect((createButton as HTMLButtonElement).disabled).toBe(false));
    await fireEvent.click(createButton);
    await waitFor(() => expect(client.createMyLocalClientCredential).toHaveBeenCalledTimes(1));
    view.unmount();
    lateResponse.resolve(revealableCredential);
    await Promise.resolve();
    await Promise.resolve();

    expect(client.copyMyLocalClientCredential).not.toHaveBeenCalled();
  });

  it("clears plaintext when Element Plus starts a native dialog close without waiting for closed", async () => {
    const client = api(revealableCredential);
    vi.mocked(client.copyMyLocalClientCredential).mockResolvedValue({ clientKey: "tack_v1_native_close_secret" });
    const view = renderPanel(client);

    const showButton = await view.findByRole("button", { name: "显示 Client key" });
    await fireEvent.click(showButton);
    await waitFor(() => expect(view.getByText("tack_v1_native_close_secret")).toBeTruthy());

    await fireEvent.click(view.getByRole("button", { name: "原生关闭" }));
    expect(view.queryByText("tack_v1_native_close_secret")).toBeNull();
  });

  it("fails closed when a rolling-upgrade response omits revealAvailable", async () => {
    const client = api({
      exists: true,
      maskedKey: "tack_v1_ABC...WXYZ",
      version: 1,
      status: "ACTIVE"
    });
    const view = renderPanel(client);

    expect(await view.findByText("tack_v1_ABC...WXYZ")).toBeTruthy();
    expect(view.queryByRole("button", { name: "显示 Client key" })).toBeNull();
    expect(client.copyMyLocalClientCredential).not.toHaveBeenCalled();
  });
});
