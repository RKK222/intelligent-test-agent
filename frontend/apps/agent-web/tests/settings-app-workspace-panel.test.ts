import { defineComponent, h, inject, provide } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor, within } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CodeRepositoryConfig } from "@test-agent/shared-types";
import SettingsAppWorkspacePanel from "../src/components/settings/SettingsAppWorkspacePanel.vue";

const selectCreateRepositoryValue = "__create_repository__";

const repositories: CodeRepositoryConfig[] = [
  {
    repositoryId: "repo_wr",
    gitUrl: "file:///Users/kaka/Desktop/intelligent-test-agent/test-workspaces/F-WRTESTAPP",
    name: "F-WRTESTAPP 本地测试库",
    englishName: "wrtestapp",
    repositoryType: "TEST_WORK_REPOSITORY",
    repositoryTypeLabel: "测试工作库",
    standard: true,
    createdAt: "2026-06-26T08:00:00Z",
    updatedAt: "2026-06-26T08:00:00Z"
  },
  {
    repositoryId: "repo_mimo",
    gitUrl: "https://gitee.com/mimo/demo.git",
    name: "MIMO 示例库",
    englishName: "mimo",
    repositoryType: "APPLICATION_CODE_REPOSITORY",
    repositoryTypeLabel: "应用代码库",
    standard: false,
    createdAt: "2026-06-26T08:00:00Z",
    updatedAt: "2026-06-26T08:00:00Z"
  }
];

function createApi(): Partial<BackendApiClient> {
  return {
    listApplications: vi.fn().mockResolvedValue([{ appId: "F-COSS", appName: "F-COSS", enabled: true }]),
    listApplicationMembers: vi.fn().mockResolvedValue([]),
    listRepositories: vi.fn().mockResolvedValue({ items: repositories, page: 1, size: 100, total: repositories.length }),
    listRepositoryTypes: vi.fn().mockResolvedValue([
      { typeCode: "TEST_WORK_REPOSITORY", typeLabel: "测试工作库" },
      { typeCode: "APPLICATION_CODE_REPOSITORY", typeLabel: "应用代码库" },
      { typeCode: "APPLICATION_ASSET_REPOSITORY", typeLabel: "应用资产库" }
    ]),
    listApplicationRepositories: vi.fn().mockResolvedValue([repositories[0]]),
    listRepositoryApplications: vi.fn().mockResolvedValue([]),
    listApplicationWorkspaces: vi.fn().mockResolvedValue([]),
    listRepositoryBranches: vi.fn().mockResolvedValue(["main"]),
    listRepositoryDirectories: vi.fn().mockResolvedValue(["tests"]),
    createApplicationWorkspace: vi.fn().mockResolvedValue({}),
    getWorkspaceCreateOperation: vi.fn().mockResolvedValue({
      operationId: "wco_test",
      status: "SUCCEEDED",
      currentStep: "COMPLETED",
      steps: [{ code: "COMPLETED", name: "完成", status: "SUCCEEDED" }]
    }),
    createRepository: vi.fn().mockResolvedValue(repositories[1]),
    updateRepository: vi.fn().mockResolvedValue(repositories[0]),
    removeApplicationMember: vi.fn().mockResolvedValue(undefined),
    unlinkApplicationRepository: vi.fn().mockResolvedValue(undefined),
    deleteApplicationWorkspace: vi.fn().mockResolvedValue(undefined)
  };
}

const radioGroupKey = Symbol("radio-group");

const ElRadioGroupStub = defineComponent({
  props: ["modelValue"],
  emits: ["update:modelValue"],
  setup(_props, { emit, slots }) {
    provide(radioGroupKey, (value: string) => emit("update:modelValue", value));
    return () => h("div", slots.default?.());
  }
});

const ElRadioButtonStub = defineComponent({
  props: ["value"],
  setup(props, { slots }) {
    const selectRadio = inject<(value: string) => void>(radioGroupKey);
    return () => h("button", { type: "button", onClick: () => selectRadio?.(String(props.value)) }, slots.default?.());
  }
});

const ElSelectStub = defineComponent({
  props: ["modelValue", "placeholder", "ariaLabel"],
  emits: ["update:modelValue", "change"],
  setup(props, { emit, slots }) {
    return () =>
      h(
        "select",
        {
          "aria-label": props.ariaLabel || props.placeholder,
          value: props.modelValue,
          onChange: (event: Event) => {
            const value = (event.target as HTMLSelectElement).value;
            emit("update:modelValue", value);
            emit("change", value);
          }
        },
        slots.default?.()
      );
  }
});

const ElOptionStub = defineComponent({
  props: ["label", "value"],
  setup(props) {
    return () => h("option", { value: String(props.value) }, String(props.label));
  }
});

const ElInputStub = defineComponent({
  props: ["modelValue", "placeholder"],
  emits: ["update:modelValue"],
  methods: {
    focus() {
      (this.$el as HTMLInputElement).focus();
    }
  },
  render() {
    return h("input", {
      placeholder: this.placeholder,
      value: this.modelValue,
      onInput: (event: Event) => this.$emit("update:modelValue", (event.target as HTMLInputElement).value)
    });
  }
});

const ElAutocompleteStub = defineComponent({
  props: ["modelValue", "placeholder"],
  emits: ["update:modelValue", "select"],
  setup(props, { emit }) {
    return () =>
      h("input", {
        placeholder: props.placeholder,
        value: props.modelValue,
        onInput: (event: Event) => emit("update:modelValue", (event.target as HTMLInputElement).value)
      });
  }
});

const ElDatePickerStub = defineComponent({
  props: ["modelValue", "placeholder"],
  emits: ["update:modelValue"],
  inheritAttrs: false,
  setup(props, { emit }) {
    return () =>
      h("input", {
        placeholder: props.placeholder,
        value: props.modelValue,
        onInput: (event: Event) => emit("update:modelValue", (event.target as HTMLInputElement).value)
      });
  }
});

const ElCheckboxStub = defineComponent({
  props: ["modelValue"],
  emits: ["update:modelValue"],
  setup(props, { emit, slots }) {
    return () =>
      h("label", [
        h("input", {
          type: "checkbox",
          checked: props.modelValue,
          onChange: (event: Event) => emit("update:modelValue", (event.target as HTMLInputElement).checked)
        }),
        slots.default?.()
      ]);
  }
});

function renderPanel(api = createApi()) {
  return render(SettingsAppWorkspacePanel, {
    props: {
      currentUser: {
        userId: "usr_admin",
        username: "admin",
        unifiedAuthId: "AUTH_ADMIN",
        roles: ["APP_ADMIN"]
      }
    },
    global: {
      stubs: {
        ElAlert: {
          props: ["title"],
          template: `<div role="alert">{{ title }}</div>`
        },
        ElAutocomplete: ElAutocompleteStub,
        ElButton: {
          emits: ["click"],
          template: `<button type="button" @click="$emit('click')"><slot /></button>`
        },
        ElCheckbox: ElCheckboxStub,
        ElDatePicker: ElDatePickerStub,
        ElIcon: {
          template: `<span><slot /></span>`
        },
        ElInput: ElInputStub,
        ElRadioButton: ElRadioButtonStub,
        ElRadioGroup: ElRadioGroupStub,
        ElSelect: ElSelectStub,
        ElOption: ElOptionStub,
        ElProgress: {
          template: `<div class="el-progress">Progress</div>`
        },
        ElTooltip: {
          props: ["content"],
          template: `<span :title="content"><slot /></span>`
        }
      },
      provide: {
        api: api as BackendApiClient
      }
    }
  });
}

describe("SettingsAppWorkspacePanel repository settings", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("uses version-library wording and adds a dedicated management tab", async () => {
    const { container, findByText, getByText, queryByText } = renderPanel();

    await findByText("应用人员管理");
    const tabs = Array.from(container.querySelectorAll(".ta-sub-tab-group button")).map((item) => item.textContent?.trim());
    expect(tabs).toEqual(["应用人员管理", "应用与版本库关联", "工作空间管理"]);

    await fireEvent.click(getByText("应用与版本库关联"));

    expect(await findByText("按应用关联版本库")).toBeTruthy();
    expect(container.querySelector(".ta-section-title-app")?.textContent).toBe("F-COSS");
    expect(queryByText("按版本库管理应用")).toBeNull();
    expect(queryByText("应用 ID")).toBeNull();
    expect(queryByText("关联应用")).toBeNull();
    expect(container.querySelector('[role="separator"][aria-label="版本库关联模式分隔符"]')).toBeNull();
    expect(queryByText("关联版本库到当前应用")).toBeNull();
    expect(queryByText("版本库与应用双向关联")).toBeNull();
    expect(queryByText("编辑版本库")).toBeNull();
    expect(queryByText("新增版本库")).toBeNull();
    expect(queryByText("应用与代码库关联")).toBeNull();
    expect(queryByText("新增代码库")).toBeNull();
  });

  it("formats repository select options with name and URL and exposes create option", async () => {
    const { findByText, getAllByLabelText, getByText } = renderPanel();

    await findByText("应用人员管理");
    await fireEvent.click(getByText("应用与版本库关联"));

    const repositorySelect = getAllByLabelText("选择版本库")[0];
    expect(within(repositorySelect).getByText("F-WRTESTAPP 本地测试库(file:///Users/kaka/Desktop/intelligent-test-agent/test-workspaces/F-WRTESTAPP)")).toBeTruthy();
    expect(within(repositorySelect).getByText("添加版本库")).toBeTruthy();
  });

  it("emits switch-menu event from the select create option", async () => {
    const { findByText, getAllByLabelText, getByText, emitted } = renderPanel();

    await findByText("应用人员管理");
    await fireEvent.click(getByText("应用与版本库关联"));

    await fireEvent.update(getAllByLabelText("选择版本库")[0], selectCreateRepositoryValue);
    expect(emitted()["switch-menu"]).toBeTruthy();
    expect(emitted()["switch-menu"][0]).toEqual(["repository"]);
  });

  it("shows workspace creation as three labeled steps", async () => {
    const { container, findByText, getByText, queryByText, getAllByText } = renderPanel();

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    const createSection = getAllByText("创建工作空间").find(el => el.tagName === "H4")?.closest(".ta-section");
    expect(createSection).toBeTruthy();
    const steps = container.querySelectorAll(".ta-workspace-step");
    expect(steps[0].textContent).toContain("刷新分支");
    expect(steps[1].textContent).toContain("加载目录");
    expect(steps[2].textContent).toContain("创建工作空间");
    expect(within(createSection as HTMLElement).getByText("已关联版本库")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("分支")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("目录")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("工作空间名称")).toBeTruthy();
    expect(within(createSection as HTMLElement).getAllByText("刷新分支").length).toBeGreaterThan(0);
    expect(queryByText("加载分支")).toBeNull();
    expect(container.querySelectorAll(".ta-workspace-step").length).toBe(3);
  });

  it("creates standard workspaces with an operation id and polls backend progress", async () => {
    const api = createApi();
    api.listRepositoryBranches = vi.fn().mockResolvedValue(["feature_testagent_20260707"]);
    api.listRepositoryDirectories = vi.fn().mockResolvedValue(["F-WRTESTAPP/workspace"]);
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("12345678-1234-1234-1234-123456789abc");
    const { findByText, getByText, getAllByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));
    await fireEvent.click(getAllByText("刷新分支").find(el => el.tagName === "BUTTON")!);
    await fireEvent.click(getAllByText("加载目录").find(el => el.tagName === "BUTTON")!);
    await fireEvent.click(getByText("创建"));

    await waitFor(() => expect(api.createApplicationWorkspace).toHaveBeenCalledWith("F-COSS", {
      repositoryId: "repo_wr",
      branch: "feature_testagent_20260707",
      directoryPath: "F-WRTESTAPP/workspace",
      workspaceName: undefined,
      operationId: "wco_12345678123412341234123456789abc"
    }));
    await waitFor(() => expect(api.getWorkspaceCreateOperation).toHaveBeenCalledWith("wco_12345678123412341234123456789abc"));
  });

  it("requires yyyyMMdd version when creating a non-standard workspace", async () => {
    const api = createApi();
    api.listApplicationRepositories = vi.fn().mockResolvedValue([repositories[1]]);
    api.listRepositoryBranches = vi.fn().mockResolvedValue(["feature/demo"]);
    api.listRepositoryDirectories = vi.fn().mockResolvedValue(["src"]);
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("22345678-1234-1234-1234-123456789abc");
    const { findByText, getByPlaceholderText, getByText, getAllByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));
    await fireEvent.click(getAllByText("刷新分支").find(el => el.tagName === "BUTTON")!);
    await fireEvent.click(getAllByText("加载目录").find(el => el.tagName === "BUTTON")!);
    expect(await findByText("非标准库版本")).toBeTruthy();

    await fireEvent.update(getByPlaceholderText("选择日期"), "20260707");
    await fireEvent.click(getByText("创建"));

    await waitFor(() => expect(api.createApplicationWorkspace).toHaveBeenCalledWith("F-COSS", expect.objectContaining({
      repositoryId: "repo_mimo",
      branch: "feature/demo",
      directoryPath: "src",
      version: "20260707"
    })));
  });

  it("confirms before removing an application member", async () => {
    const api = createApi();
    api.listApplicationMembers = vi.fn().mockResolvedValue([
      {
        userId: "usr_member",
        username: "成员用户",
        unifiedAuthId: "AUTH_MEMBER",
        roles: []
      }
    ]);
    const { findByText, getByLabelText, getByText, queryByText } = renderPanel(api);

    expect(await findByText("成员用户")).toBeTruthy();

    await fireEvent.click(getByLabelText("移除成员"));
    expect(await findByText("确认移除成员")).toBeTruthy();
    expect(getByText("确认移除成员[成员用户]吗？")).toBeTruthy();
    expect(api.removeApplicationMember).not.toHaveBeenCalled();

    await fireEvent.click(getByText("取消"));
    expect(queryByText("确认移除成员[成员用户]吗？")).toBeNull();

    await fireEvent.click(getByLabelText("移除成员"));
    await fireEvent.click(getByText("确认移除"));
    await waitFor(() => expect(api.removeApplicationMember).toHaveBeenCalledWith("F-COSS", "usr_member"));
  });

  it("confirms before unlinking a repository from the selected application", async () => {
    const api = createApi();
    const { findByText, getByText, queryByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("应用与版本库关联"));
    expect(await findByText("F-WRTESTAPP 本地测试库")).toBeTruthy();

    await fireEvent.click(getByText("解除"));
    expect(await findByText("确认解除关联")).toBeTruthy();
    expect(getByText("确认解除版本库[F-WRTESTAPP 本地测试库]与当前应用的关联吗？")).toBeTruthy();
    expect(api.unlinkApplicationRepository).not.toHaveBeenCalled();

    await fireEvent.click(getByText("取消"));
    expect(queryByText("确认解除版本库[F-WRTESTAPP 本地测试库]与当前应用的关联吗？")).toBeNull();

    await fireEvent.click(getByText("解除"));
    await fireEvent.click(getByText("确认解除"));
    await waitFor(() => expect(api.unlinkApplicationRepository).toHaveBeenCalledWith("F-COSS", "repo_wr"));
  });

  it("confirms before deleting a workspace", async () => {
    const api = createApi();
    api.listApplicationWorkspaces = vi.fn().mockResolvedValue([
      {
        workspaceId: "ws_test",
        workspaceName: "测试工作空间",
        branch: "feature_testagent_20260707",
        directoryPath: "tests",
        repositoryId: "repo_wr"
      }
    ]);
    const { findByText, getByText, queryByText, getAllByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));
    expect(await findByText("测试工作空间")).toBeTruthy();

    await fireEvent.click(getByText("删除"));
    expect(await findByText("确认删除工作空间")).toBeTruthy();
    expect(getByText("确认删除工作空间[测试工作空间]吗？删除后数据将无法恢复。")).toBeTruthy();
    expect(api.deleteApplicationWorkspace).not.toHaveBeenCalled();

    await fireEvent.click(getByText("取消"));
    expect(queryByText("确认删除工作空间[测试工作空间]吗？删除后数据将无法恢复。")).toBeNull();

    await fireEvent.click(getAllByText("删除").find(el => el.tagName === "BUTTON")!);
    await fireEvent.click(getByText("确认删除"));
    await waitFor(() => expect(api.deleteApplicationWorkspace).toHaveBeenCalledWith("F-COSS", "ws_test"));
  });
});
