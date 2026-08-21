import { defineComponent, h } from "vue";
import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  InternalModelCapability,
  InternalModelProviderManagementResponse,
  InternalModelProviderModel
} from "@test-agent/shared-types";
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

const configuredChatModel: InternalModelProviderModel = {
  providerId: "enterprise-qwen",
  modelId: "enterprise-chat",
  upstreamModelId: "Qwen3.6-27B",
  displayName: "企业对话模型",
  contextLimit: 128000,
  embeddingDimension: null,
  enabled: true,
  declaredCapabilities: ["CHAT"],
  probedCapabilities: [],
  lastProbedAt: null
};

const configuredEmbeddingModel: InternalModelProviderModel = {
  providerId: "enterprise-qwen",
  modelId: "memory-bge-small-zh-v1.5",
  upstreamModelId: "BAAI/bge-small-zh-v1.5",
  displayName: "固定 CPU BGE",
  contextLimit: null,
  embeddingDimension: 512,
  enabled: true,
  declaredCapabilities: ["EMBEDDING"],
  probedCapabilities: [],
  lastProbedAt: null
};

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
    getInternalModelProviderModels: vi.fn().mockResolvedValue([]),
    updateInternalModelProviderModels: vi.fn().mockResolvedValue([configuredChatModel]),
    probeInternalModelProviderModel: vi.fn().mockImplementation(async (
      _providerId: string,
      _modelId: string,
      capability: InternalModelCapability
    ) => ({
      capability,
      succeeded: true,
      probedAt: "2026-08-18T08:00:00Z"
    })),
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

function renderPanel(api: BackendApiClient, pageActive = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const view = render(InternalModelProviderPanel, {
    props: { currentUser, pageActive },
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

  it("configures and probes a CHAT model without requiring browser console commands", async () => {
    const enabledProviderResponse: InternalModelProviderManagementResponse = {
      providers: [{
        ...providerResponse.providers[0]!,
        enabled: true,
        tokenId: 11,
        tokenName: "Qwen Token",
        tokenConfigured: true
      }],
      tokenConfigured: true
    };
    const api = createApi({
      getInternalModelProviders: vi.fn().mockResolvedValue(enabledProviderResponse)
    });
    const view = renderPanel(api);

    expect(await view.findByText("模型目录与能力探测")).toBeTruthy();
    await waitFor(() => expect(api.getInternalModelProviderModels).toHaveBeenCalledWith("enterprise-qwen"));
    await fireEvent.click(view.getByRole("button", { name: "新增模型" }));
    await fireEvent.update(view.getByRole("textbox", { name: "公开 Model ID 1" }), "enterprise-chat");
    await fireEvent.update(view.getByRole("textbox", { name: "上游 Model ID 1" }), "Qwen3.6-27B");
    await fireEvent.update(view.getByRole("textbox", { name: "模型显示名 1" }), "企业对话模型");
    await fireEvent.click(view.getByRole("button", { name: "保存并探测 CHAT" }));

    await waitFor(() => expect(api.updateInternalModelProviderModels).toHaveBeenCalledWith(
      "enterprise-qwen",
      { models: [expect.objectContaining({
        modelId: "enterprise-chat",
        upstreamModelId: "Qwen3.6-27B",
        displayName: "企业对话模型",
        enabled: true,
        capabilities: ["CHAT"]
      })] }
    ));
    await waitFor(() => expect(api.probeInternalModelProviderModel).toHaveBeenCalledWith(
      "enterprise-qwen", "enterprise-chat", "CHAT"
    ));
    view.queryClient.clear();
  });

  it("configures and probes the fixed EMBEDDING model from the same page", async () => {
    const enabledProviderResponse: InternalModelProviderManagementResponse = {
      providers: [{
        ...providerResponse.providers[0]!,
        enabled: true,
        tokenId: 11,
        tokenName: "Qwen Token",
        tokenConfigured: true
      }],
      tokenConfigured: true
    };
    const api = createApi({
      getInternalModelProviders: vi.fn().mockResolvedValue(enabledProviderResponse),
      getInternalModelProviderModels: vi.fn().mockResolvedValue([configuredEmbeddingModel]),
      updateInternalModelProviderModels: vi.fn().mockResolvedValue([configuredEmbeddingModel])
    });
    const view = renderPanel(api);

    expect((await view.findByRole("textbox", { name: "模型显示名 1" }) as HTMLInputElement).value)
      .toBe("固定 CPU BGE");
    await fireEvent.click(view.getByRole("button", { name: "保存并探测 EMBEDDING" }));

    await waitFor(() => expect(api.updateInternalModelProviderModels).toHaveBeenCalledWith(
      "enterprise-qwen",
      { models: [expect.objectContaining({
        modelId: "memory-bge-small-zh-v1.5",
        upstreamModelId: "BAAI/bge-small-zh-v1.5",
        embeddingDimension: 512,
        capabilities: ["EMBEDDING"]
      })] }
    ));
    await waitFor(() => expect(api.probeInternalModelProviderModel).toHaveBeenCalledWith(
      "enterprise-qwen", "memory-bge-small-zh-v1.5", "EMBEDDING"
    ));
    view.queryClient.clear();
  });

  it("reprobes previously successful capabilities after catalog replacement", async () => {
    const combinedModel: InternalModelProviderModel = {
      ...configuredEmbeddingModel,
      declaredCapabilities: ["CHAT", "EMBEDDING"],
      probedCapabilities: ["CHAT"]
    };
    const enabledProviderResponse: InternalModelProviderManagementResponse = {
      providers: [{
        ...providerResponse.providers[0]!,
        enabled: true,
        tokenId: 11,
        tokenName: "Qwen Token",
        tokenConfigured: true
      }],
      tokenConfigured: true
    };
    const api = createApi({
      getInternalModelProviders: vi.fn().mockResolvedValue(enabledProviderResponse),
      getInternalModelProviderModels: vi.fn().mockResolvedValue([combinedModel]),
      updateInternalModelProviderModels: vi.fn().mockResolvedValue([combinedModel])
    });
    const view = renderPanel(api);

    await view.findByText("已通过 CHAT");
    await fireEvent.click(view.getByRole("button", { name: "保存并探测 EMBEDDING" }));

    await waitFor(() => expect(api.probeInternalModelProviderModel).toHaveBeenCalledWith(
      "enterprise-qwen", "memory-bge-small-zh-v1.5", "CHAT"
    ));
    await waitFor(() => expect(api.probeInternalModelProviderModel).toHaveBeenCalledWith(
      "enterprise-qwen", "memory-bge-small-zh-v1.5", "EMBEDDING"
    ));
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

  it("wipes the Token input but keeps the non-sensitive editor draft when its page tab becomes inactive", async () => {
    const view = renderPanel(createApi());
    await view.findByText("Qwen Token");
    await fireEvent.click(view.getByRole("button", { name: "新增 Token" }));
    await fireEvent.update(view.getByPlaceholderText("Token 名称"), "DeepSeek Token");
    await fireEvent.update(view.getByPlaceholderText("粘贴外部 Token"), "temporary-secret");

    await view.rerender({ currentUser, pageActive: false });

    expect((view.getByPlaceholderText("粘贴外部 Token") as HTMLInputElement).value).toBe("");
    expect((view.getByPlaceholderText("Token 名称") as HTMLInputElement).value).toBe("DeepSeek Token");
    view.queryClient.clear();
  });
});
