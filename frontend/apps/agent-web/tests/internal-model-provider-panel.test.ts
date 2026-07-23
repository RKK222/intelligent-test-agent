import { defineComponent, h } from "vue";
import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, InternalModelProviderManagementResponse } from "@test-agent/shared-types";
import InternalModelProviderPanel from "../src/components/system/InternalModelProviderPanel.vue";

const currentUser: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

const providerResponse: InternalModelProviderManagementResponse = {
  providers: [{
    providerId: "enterprise-qwen",
    name: "Enterprise Qwen",
    baseUrl: "https://qwen.example/v1",
    enabled: false,
    sortOrder: 1,
    tokenId: null,
    tokenName: null,
    tokenConfigured: false
  }],
  tokenConfigured: true
};

const tokenDefinitions = [{
  tokenId: 11,
  name: "Qwen Token",
  referencedProviderCount: 2,
  createdAt: "2026-07-22T08:00:00Z",
  updatedAt: "2026-07-22T08:00:00Z"
}];

function createApi(overrides: Partial<BackendApiClient> = {}): BackendApiClient {
  return {
    getInternalModelProviders: vi.fn().mockResolvedValue(providerResponse),
    getInternalModelProviderRefreshStatus: vi.fn().mockResolvedValue({
      providers: providerResponse.providers,
      tokenConfigured: true,
      loadedAt: "2026-07-22T08:00:00Z",
      traceId: "trace_refresh"
    }),
    listInternalModelTokens: vi.fn().mockResolvedValue(tokenDefinitions),
    createInternalModelToken: vi.fn().mockResolvedValue(tokenDefinitions[0]),
    updateInternalModelToken: vi.fn().mockResolvedValue(tokenDefinitions[0]),
    deleteInternalModelToken: vi.fn().mockResolvedValue({ tokenId: 11, deleted: true }),
    updateInternalModelProviders: vi.fn().mockResolvedValue(providerResponse),
    refreshInternalModelProviders: vi.fn().mockResolvedValue({
      providers: [], tokenConfigured: true, loadedAt: "2026-07-22T08:00:00Z"
    }),
    ...overrides
  } as Partial<BackendApiClient> as BackendApiClient;
}

const ElInputStub = defineComponent({
  props: ["modelValue", "placeholder", "ariaLabel", "type", "size"],
  emits: ["update:modelValue"],
  setup(props, { emit }) {
    return () => h("input", {
      "aria-label": props.ariaLabel,
      placeholder: props.placeholder,
      type: props.type === "password" ? "password" : "text",
      value: props.modelValue ?? "",
      onInput: (event: Event) => emit("update:modelValue", (event.target as HTMLInputElement).value)
    });
  }
});

const ElSelectStub = defineComponent({
  props: ["modelValue", "placeholder", "ariaLabel", "size"],
  emits: ["update:modelValue", "change"],
  setup(props, { emit, slots }) {
    return () => h("select", {
      "aria-label": props.ariaLabel || props.placeholder,
      value: props.modelValue ?? "",
      onChange: (event: Event) => {
        const value = (event.target as HTMLSelectElement).value;
        emit("update:modelValue", value);
        emit("change", value);
      }
    }, slots.default?.());
  }
});

const ElSwitchStub = defineComponent({
  props: ["modelValue", "ariaLabel", "size"],
  emits: ["update:modelValue", "change"],
  setup(props, { emit }) {
    return () => h("input", {
      type: "checkbox",
      "aria-label": props.ariaLabel,
      checked: props.modelValue,
      onChange: (event: Event) => {
        const checked = (event.target as HTMLInputElement).checked;
        emit("update:modelValue", checked);
        emit("change", checked);
      }
    });
  }
});

function renderPanel(api: BackendApiClient) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const view = render(InternalModelProviderPanel, {
    props: { currentUser },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      provide: { api },
      stubs: {
        ElButton: {
          props: ["disabled", "ariaLabel"],
          emits: ["click"],
          template: `<button type="button" :aria-label="ariaLabel" :disabled="disabled" @click="$emit('click')"><slot /></button>`
        },
        ElInput: ElInputStub,
        ElSelect: ElSelectStub,
        ElOption: { props: ["label", "value"], template: `<option :value="value ?? ''">{{ label }}</option>` },
        ElSwitch: ElSwitchStub,
        ElInputNumber: ElInputStub,
        ElTag: { template: `<span><slot /></span>` },
        ElDivider: { template: `<hr />` }
      }
    }
  });
  return { ...view, queryClient };
}

describe("InternalModelProviderPanel", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("creates, edits and deletes externally supplied Token definitions", async () => {
    const api = createApi();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const view = renderPanel(api);

    expect(await view.findByText("Qwen Token")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "新增 Token" }));
    await fireEvent.update(view.getByPlaceholderText("Token 名称"), "DeepSeek Token");
    await fireEvent.update(view.getByPlaceholderText("粘贴外部 Token"), " external-deepseek-secret ");
    await fireEvent.click(view.getByRole("button", { name: "保存 Token" }));
    await waitFor(() => expect(api.createInternalModelToken).toHaveBeenCalledWith({
      name: "DeepSeek Token",
      token: " external-deepseek-secret "
    }));
    await waitFor(() => expect(view.queryByPlaceholderText("Token 名称")).toBeNull());

    await fireEvent.click(view.getByRole("button", { name: "编辑 Qwen Token" }));
    expect((view.getByPlaceholderText("Token 名称") as HTMLInputElement).value).toBe("Qwen Token");
    expect((view.getByPlaceholderText("留空则不修改") as HTMLInputElement).value).toBe("");
    await fireEvent.update(view.getByPlaceholderText("Token 名称"), "Qwen Production Token");
    await fireEvent.click(view.getByRole("button", { name: "保存 Token" }));
    await waitFor(() => expect(api.updateInternalModelToken).toHaveBeenCalledWith(11, {
      name: "Qwen Production Token",
      token: undefined
    }));

    await fireEvent.click(view.getByRole("button", { name: "删除 Qwen Token" }));
    await waitFor(() => expect(api.deleteInternalModelToken).toHaveBeenCalledWith(11));
    view.queryClient.clear();
  });

  it("clears a failed secret draft immediately after the request settles", async () => {
    const api = createApi({
      createInternalModelToken: vi.fn().mockRejectedValue(new Error("保存失败"))
    });
    vi.spyOn(ElMessage, "error").mockImplementation(() => undefined as never);
    const view = renderPanel(api);

    await view.findByText("Qwen Token");
    await fireEvent.click(view.getByRole("button", { name: "新增 Token" }));
    await fireEvent.update(view.getByPlaceholderText("Token 名称"), "Failed Token");
    const secretInput = view.getByPlaceholderText("粘贴外部 Token") as HTMLInputElement;
    await fireEvent.update(secretInput, "must-not-remain");
    await fireEvent.click(view.getByRole("button", { name: "保存 Token" }));

    await waitFor(() => expect(api.createInternalModelToken).toHaveBeenCalled());
    await waitFor(() => expect(secretInput.value).toBe(""));
    await waitFor(() => expect(
      JSON.stringify(view.queryClient.getMutationCache().getAll().map((mutation) => mutation.state.variables))
    ).not.toContain("must-not-remain"));
    view.queryClient.clear();
  });

  it("requires an enabled provider to select a Token and saves the Provider-ID association", async () => {
    const api = createApi();
    const warning = vi.spyOn(ElMessage, "warning").mockImplementation(() => undefined as never);
    const view = renderPanel(api);

    const enabledSwitch = await view.findByRole("checkbox", { name: "启用 enterprise-qwen" });
    await fireEvent.click(enabledSwitch);
    await fireEvent.click(view.getByRole("button", { name: "保存供应商" }));
    expect(warning).toHaveBeenCalledWith("启用的供应商必须选择 Token");
    expect(api.updateInternalModelProviders).not.toHaveBeenCalled();

    await fireEvent.update(view.getByRole("combobox", { name: "enterprise-qwen 的 Token" }), "11");
    await fireEvent.click(view.getByRole("button", { name: "保存供应商" }));
    await waitFor(() => expect(api.updateInternalModelProviders).toHaveBeenCalledWith({
      providers: [expect.objectContaining({
        providerId: "enterprise-qwen",
        enabled: true,
        tokenId: 11
      })]
    }));
    view.queryClient.clear();
  });

  it("shows the referenced-token conflict returned by delete", async () => {
    const api = createApi({
      deleteInternalModelToken: vi.fn().mockRejectedValue(new Error("内部模型 Token 仍被供应商引用，不能删除"))
    });
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const error = vi.spyOn(ElMessage, "error").mockImplementation(() => undefined as never);
    const view = renderPanel(api);

    await view.findByText("Qwen Token");
    await fireEvent.click(view.getByRole("button", { name: "删除 Qwen Token" }));

    await waitFor(() => expect(error).toHaveBeenCalledWith("内部模型 Token 仍被供应商引用，不能删除"));
    view.queryClient.clear();
  });
});
