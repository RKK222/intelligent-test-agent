import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CommonParameterChangeLog, CurrentUser, GeneralParameter, PageResponse } from "@test-agent/shared-types";
import GeneralParamManagementPanel from "../src/components/system/GeneralParamManagementPanel.vue";

const currentUser: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

const macosParameter: GeneralParameter = {
  parameterId: "opencode_session_dir_macos",
  englishName: "OPENCODE_SESSION_DIR",
  chineseName: "OpenCode会话目录",
  parameterValue: "$TEST_AGENT_ROOT/temp/opencode-session",
  platform: "macos",
  createdAt: "2026-06-29T00:00:00Z",
  updatedAt: "2026-06-29T00:00:00Z"
};

function renderPanel(backendApi: BackendApiClient) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = render(GeneralParamManagementPanel, {
    props: { currentUser },
    global: {
      plugins: [[VueQueryPlugin, { queryClient: client }]],
      stubs: {
        ElButton: { emits: ["click"], template: `<button type="button" @click="$emit('click')"><slot /></button>` },
        ElSelect: {
          props: ["modelValue", "placeholder"],
          emits: ["update:modelValue", "change"],
          template: `<select :aria-label="placeholder" :value="modelValue" @change="$emit('update:modelValue', $event.target.value); $emit('change', $event.target.value)"><slot /></select>`
        },
        ElOption: { props: ["label", "value"], template: `<option :value="value">{{ label }}</option>` },
        ElDialog: { template: `<div><slot /></div>` },
        ElDrawer: {
          props: ["modelValue", "title"],
          emits: ["update:modelValue", "close"],
          template: `<section v-if="modelValue" role="dialog" :aria-label="title"><slot /></section>`
        },
        ElInput: { template: `<input />` },
        ElForm: { template: `<form><slot /></form>` },
        ElFormItem: { template: `<div><slot /></div>` },
        ElEmpty: { template: `<div><slot /></div>` },
        ElTag: { template: `<span><slot /></span>` }
      },
      provide: { api: backendApi }
    }
  });
  return { ...view, queryClient: client };
}

describe("general parameter management panel", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("lists macos and refreshes immediately after platform selection", async () => {
    const listGeneralParameters = vi.fn(async ({ platform }: { platform?: string }) => ({
      items: platform === "macos" ? [macosParameter] : [],
      page: 1,
      size: 50,
      total: platform === "macos" ? 1 : 0
    } satisfies PageResponse<GeneralParameter>));
    const backendApi = {
      listGeneralParameters,
      listCommonParameterChangeLogs: vi.fn().mockResolvedValue([])
    } as Partial<BackendApiClient> as BackendApiClient;
    const view = renderPanel(backendApi);

    await waitFor(() => expect(listGeneralParameters).toHaveBeenCalledWith({
      platform: undefined,
      page: 1,
      size: 50
    }));
    expect(view.getByRole("option", { name: "macos" })).toBeTruthy();

    await fireEvent.update(view.getByRole("listbox", { name: "平台" }), "macos");

    await waitFor(() => expect(listGeneralParameters).toHaveBeenCalledWith({
      platform: "macos",
      page: 1,
      size: 50
    }));
    expect(await view.findByText("$TEST_AGENT_ROOT/temp/opencode-session")).toBeTruthy();
    view.queryClient.clear();
  });

  it("opens change logs drawer with current parameter details and history rows", async () => {
    const changeLog: CommonParameterChangeLog = {
      logId: "log_1",
      parameterId: macosParameter.parameterId,
      oldValue: "$OLD_ROOT/temp/opencode-session",
      newValue: macosParameter.parameterValue,
      changedByUserId: "usr_admin",
      changedByUsername: "admin",
      traceId: "trace_1",
      createdAt: "2026-06-30T02:00:00Z"
    };
    const listGeneralParameters = vi.fn(async () => ({
      items: [macosParameter],
      page: 1,
      size: 50,
      total: 1
    } satisfies PageResponse<GeneralParameter>));
    const listCommonParameterChangeLogs = vi.fn(async () => [changeLog]);
    const backendApi = {
      listGeneralParameters,
      listCommonParameterLoadSnapshots: vi.fn().mockResolvedValue([]),
      listCommonParameterChangeLogs
    } as Partial<BackendApiClient> as BackendApiClient;
    const view = renderPanel(backendApi);

    await view.findByText("OPENCODE_SESSION_DIR");
    await fireEvent.click(view.getByRole("button", { name: "修改历史" }));

    expect(await view.findByRole("dialog", { name: "修改历史 - OPENCODE_SESSION_DIR" })).toBeTruthy();
    expect(view.getAllByText("OpenCode会话目录").length).toBeGreaterThan(0);
    expect(view.getAllByText("$TEST_AGENT_ROOT/temp/opencode-session").length).toBeGreaterThan(0);
    await waitFor(() => expect(listCommonParameterChangeLogs).toHaveBeenCalledWith(macosParameter.parameterId));
    expect(await view.findByText("$OLD_ROOT/temp/opencode-session")).toBeTruthy();
    view.queryClient.clear();
  });
});
