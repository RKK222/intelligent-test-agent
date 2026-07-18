import { defineComponent, h, inject, provide } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor, within } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CodeRepositoryConfig, RepositoryTreeResponse } from "@test-agent/shared-types";
import SettingsAppWorkspacePanel from "../src/components/settings/SettingsAppWorkspacePanel.vue";

const selectCreateRepositoryValue = "__create_repository__";
const branchRuleTooltip = "测试工作库的分支命名规则为：feature_testagent_yyyymmdd，yyyymmdd为投产日。";

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

const repositoryTree: RepositoryTreeResponse = {
  nodes: [
    {
      name: "F-COSS",
      path: "F-COSS",
      type: "directory",
      children: [
        {
          name: "W1",
          path: "F-COSS/W1",
          type: "directory",
          children: [
            {
              name: "F1",
              path: "F-COSS/W1/F1",
              type: "directory",
              children: []
            },
            {
              name: "case.md",
              path: "F-COSS/W1/case.md",
              type: "file",
              children: []
            }
          ]
        },
        {
          name: "W2",
          path: "F-COSS/W2",
          type: "directory",
          children: []
        }
      ]
    },
    {
      name: "OTHER-APP",
      path: "OTHER-APP",
      type: "directory",
      children: []
    }
  ]
};

function createApi(): Partial<BackendApiClient> {
  return {
    listApplications: vi.fn().mockResolvedValue([{ appId: "F-COSS", appName: "F-COSS", enabled: true }]),
    createApplication: vi.fn().mockResolvedValue({ appId: "F-NEW", appName: "新应用", enabled: true }),
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
    getRepositoryTree: vi.fn().mockResolvedValue(repositoryTree),
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
  setup(props, { attrs, slots }) {
    const selectRadio = inject<(value: string) => void>(radioGroupKey);
    return () => h("button", { type: "button", ...attrs, onClick: () => selectRadio?.(String(props.value)) }, slots.default?.());
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
  props: ["label", "value", "disabled", "title"],
  setup(props) {
    return () =>
      h(
        "option",
        {
          value: String(props.value),
          disabled: Boolean(props.disabled),
          title: props.title ? String(props.title) : undefined
        },
        String(props.label)
      );
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

function getTreePathElement(container: ParentNode, path: string) {
  return Array.from(container.querySelectorAll(".ta-workspace-tree-path")).find((item) => item.textContent === path) as HTMLElement | undefined;
}

function getTreePathButton(container: ParentNode, path: string) {
  return getTreePathElement(container, path)!.closest("button") as HTMLButtonElement;
}

function renderPanel(api = createApi(), roles = ["APP_ADMIN"], initialAppTab?: "members" | "repositories" | "workspaces") {
  return render(SettingsAppWorkspacePanel, {
    props: {
      initialAppTab,
      currentUser: {
        userId: "usr_admin",
        username: "admin",
        unifiedAuthId: "AUTH_ADMIN",
        roles
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
          props: ["disabled"],
          emits: ["click"],
          template: `<button type="button" :disabled="disabled" @click="$emit('click')"><slot /></button>`
        },
        ElCheckbox: ElCheckboxStub,
        ElDatePicker: ElDatePickerStub,
        ElDialog: {
          props: ["modelValue"],
          emits: ["update:modelValue"],
          template: `<section v-if="modelValue"><slot /><slot name="footer" /></section>`
        },
        ElForm: { template: `<form><slot /></form>` },
        ElFormItem: { template: `<label><slot /></label>` },
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

it("lets only a super admin create an enabled application from settings", async () => {
  const api = createApi();
  vi.mocked(api.listApplications!).mockResolvedValueOnce([{ appId: "F-COSS", appName: "F-COSS", enabled: true }])
    .mockResolvedValueOnce([
      { appId: "F-COSS", appName: "F-COSS", enabled: true },
      { appId: "F-NEW", appName: "新应用", enabled: true }
    ]);
  const view = renderPanel(api, ["SUPER_ADMIN"]);
  await waitFor(() => expect(view.getByTestId("create-application-open")).toBeTruthy());

  await fireEvent.click(view.getByTestId("create-application-open"));
  await fireEvent.update(view.getByPlaceholderText("例如 F-COSS"), "F-NEW");
  await fireEvent.update(view.getByPlaceholderText("请输入应用名称"), "新应用");
  await fireEvent.click(view.getByTestId("create-application-submit"));

  await waitFor(() => expect(api.createApplication).toHaveBeenCalledWith({ appId: "F-NEW", appName: "新应用" }));
});

describe("SettingsAppWorkspacePanel repository settings", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("uses version-library wording and adds a dedicated management tab", async () => {
    const { container, findByText, getByText, queryByText } = renderPanel();

    await findByText("应用人员管理");
    const tabs = Array.from(container.querySelectorAll(".ta-sub-tab-group button")).map((item) => item.textContent?.trim());
    expect(tabs).toEqual(["应用人员管理", "应用与版本库关联", "工作空间管理"]);
    expect(container.querySelector('[data-onboarding="settings-app-members"]')?.textContent).toContain("应用人员管理");
    expect(container.querySelector('[data-onboarding="settings-app-repositories"]')?.textContent).toContain("应用与版本库关联");
    expect(container.querySelector('[data-onboarding="settings-app-workspaces"]')?.textContent).toContain("工作空间管理");

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

  it("opens the tab requested by the onboarding guide", async () => {
    const { findByText, getByText, queryByText } = renderPanel(createApi(), ["APP_ADMIN"], "workspaces");

    expect(await findByText("创建工作空间")).toBeTruthy();
    expect(getByText("工作空间管理")).toBeTruthy();
    expect(queryByText("添加成员")).toBeNull();
  });

  it("formats repository select options with name and URL and exposes create option", async () => {
    const { findByText, getAllByLabelText, getByText } = renderPanel();

    await findByText("应用人员管理");
    await fireEvent.click(getByText("应用与版本库关联"));

    const repositorySelect = getAllByLabelText("选择版本库")[0];
    expect(within(repositorySelect).getByText("F-WRTESTAPP 本地测试库(file:///Users/kaka/Desktop/intelligent-test-agent/test-workspaces/F-WRTESTAPP)")).toBeTruthy();
    expect(within(repositorySelect).getByText("MIMO 示例库(https://gitee.com/mimo/demo.git)")).toBeTruthy();
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

  it("shows workspace creation as a single form and automatically loads branches and tree", async () => {
    const api = createApi();
    api.listRepositoryBranches = vi.fn().mockResolvedValue(["feature_testagent_20260707", "main"]);
    api.getRepositoryTree = vi.fn().mockResolvedValue(repositoryTree);
    const { container, findByText, getByText, queryByText, getAllByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    const createSection = getAllByText("创建工作空间").find(el => el.tagName === "H4")?.closest(".ta-section");
    expect(createSection).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("已关联版本库")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("分支")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("目录树")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("工作空间别名")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("保存")).toBeTruthy();
    expect(queryByText("加载分支")).toBeNull();
    expect(queryByText("加载目录")).toBeNull();
    expect(container.querySelectorAll(".ta-workspace-step").length).toBe(0);
    await waitFor(() => expect(api.listRepositoryBranches).toHaveBeenCalledWith("repo_wr"));
    await waitFor(() => expect(api.getRepositoryTree).toHaveBeenCalledWith("F-COSS", "repo_wr", "feature_testagent_20260707"));
  });

  it("only offers linked test work repositories when creating a workspace", async () => {
    const api = createApi();
    const explicitlyNonTestStandardRepository: CodeRepositoryConfig = {
      ...repositories[1],
      repositoryId: "repo_explicit_non_test",
      name: "显式应用代码库",
      standard: true
    };
    const legacyTestRepository: CodeRepositoryConfig = {
      ...repositories[0],
      repositoryId: "repo_legacy_test",
      name: "历史测试工作库",
      repositoryType: null,
      repositoryTypeLabel: null,
      standard: true
    };
    api.listApplicationRepositories = vi.fn().mockResolvedValue([
      explicitlyNonTestStandardRepository,
      legacyTestRepository,
      repositories[1],
      repositories[0]
    ]);
    api.listRepositoryBranches = vi.fn().mockResolvedValue(["feature_testagent_20260707"]);
    const { findByText, getByLabelText, getByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    const repositorySelect = getByLabelText("选择已关联版本库");
    expect(within(repositorySelect).getByText("历史测试工作库(file:///Users/kaka/Desktop/intelligent-test-agent/test-workspaces/F-WRTESTAPP)")).toBeTruthy();
    expect(within(repositorySelect).getByText("F-WRTESTAPP 本地测试库(file:///Users/kaka/Desktop/intelligent-test-agent/test-workspaces/F-WRTESTAPP)")).toBeTruthy();
    expect(within(repositorySelect).queryByText("显式应用代码库(https://gitee.com/mimo/demo.git)")).toBeNull();
    expect(within(repositorySelect).queryByText("MIMO 示例库(https://gitee.com/mimo/demo.git)")).toBeNull();
    expect(getByText("只能关联类型为测试工作库的版本库。")).toBeTruthy();
    await waitFor(() => expect(api.listRepositoryBranches).toHaveBeenCalledWith("repo_legacy_test"));
    expect(api.listRepositoryBranches).not.toHaveBeenCalledWith("repo_explicit_non_test");
    expect(api.listRepositoryBranches).not.toHaveBeenCalledWith("repo_mimo");
  });

  it("disables invalid test-work-repository branches and exposes the immediate tooltip text", async () => {
    const api = createApi();
    api.listRepositoryBranches = vi.fn().mockResolvedValue(["main", "feature_testagent_20260707"]);
    const { findByText, getByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    await waitFor(() => expect(api.listRepositoryBranches).toHaveBeenCalled());
    const invalidOption = Array.from(document.querySelectorAll("option")).find((option) => option.textContent === "main") as HTMLOptionElement;
    expect(invalidOption.disabled).toBe(true);
    expect(invalidOption.title).toBe(branchRuleTooltip);
  });

  it("shows all root directories without filtering by app name, expanding to direct-child directories by default", async () => {
    const api = createApi();
    api.listRepositoryBranches = vi.fn().mockResolvedValue(["feature_testagent_20260707"]);
    api.getRepositoryTree = vi.fn().mockResolvedValue(repositoryTree);
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("12345678-1234-1234-1234-123456789abc");
    const { container, findByText, getByText, queryByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    expect(await findByText("F-COSS")).toBeTruthy();
    expect(await findByText("OTHER-APP")).toBeTruthy();
    expect(await findByText("F-COSS/W1")).toBeTruthy();
    expect((getByText("F-COSS/W1").closest("button") as HTMLButtonElement).disabled).toBe(false);
    expect(await findByText("F-COSS/W1/F1")).toBeTruthy();
    expect(await findByText("F-COSS/W1/case.md")).toBeTruthy();
    expect((getByText("F-COSS/W1/F1").closest("button") as HTMLButtonElement).disabled).toBe(true);
    expect((getByText("F-COSS/W1/case.md").closest("button") as HTMLButtonElement).disabled).toBe(true);

    await fireEvent.click(getTreePathButton(container, "F-COSS/W1"));
    await fireEvent.click(getByText("保存"));

    await waitFor(() => expect(api.createApplicationWorkspace).toHaveBeenCalledWith("F-COSS", {
      repositoryId: "repo_wr",
      branch: "feature_testagent_20260707",
      directoryPath: "F-COSS/W1",
      workspaceName: "ai-test",
      operationId: "wco_12345678123412341234123456789abc"
    }));
    await waitFor(() => expect(api.getWorkspaceCreateOperation).toHaveBeenCalledWith("wco_12345678123412341234123456789abc"));
  });

  it("adds a new direct child directory in memory and sends directoryNew on save", async () => {
    const api = createApi();
    api.listRepositoryBranches = vi.fn().mockResolvedValue(["feature_testagent_20260707"]);
    api.getRepositoryTree = vi.fn().mockResolvedValue({ nodes: [repositoryTree.nodes[0]] });
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("32345678-1234-1234-1234-123456789abc");
    const { container, findAllByText, findByText, getByPlaceholderText, getByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    await findByText("F-COSS");
    await fireEvent.update(getByPlaceholderText("新增一级目录"), "W3");
    await fireEvent.click(getByText("新增目录"));
    expect((await findAllByText("F-COSS/W3")).length).toBeGreaterThan(0);
    await fireEvent.click(getByText("保存"));

    await waitFor(() => expect(api.createApplicationWorkspace).toHaveBeenCalledWith("F-COSS", expect.objectContaining({
      repositoryId: "repo_wr",
      branch: "feature_testagent_20260707",
      directoryPath: "F-COSS/W3",
      workspaceName: "ai-test",
      directoryNew: true,
      operationId: "wco_32345678123412341234123456789abc"
    })));
  });

  it("defaults workspace alias to ai-test and disables saving duplicate aliases", async () => {
    const api = createApi();
    api.listRepositoryBranches = vi.fn().mockResolvedValue(["feature_testagent_20260707"]);
    api.listApplicationWorkspaces = vi.fn().mockResolvedValue([
      {
        workspaceId: "ws_existing",
        appId: "F-COSS",
        repositoryId: "repo_wr",
        branch: "feature_testagent_20260701",
        directoryPath: "F-COSS/W0",
        workspaceName: "ai-test",
        createdAt: "2026-07-01T00:00:00Z",
        updatedAt: "2026-07-01T00:00:00Z"
      }
    ]);
    const { findByText, getByPlaceholderText, getByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    expect((getByPlaceholderText("ai-test") as HTMLInputElement).value).toBe("ai-test");
    expect(await findByText("工作空间别名已存在")).toBeTruthy();
    expect((getByText("保存") as HTMLButtonElement).disabled).toBe(true);
  });

  it("keeps workspace repository selection empty when only non-test repositories are linked", async () => {
    const api = createApi();
    api.listApplicationRepositories = vi.fn().mockResolvedValue([repositories[1]]);
    const { findByText, getByLabelText, getByText, queryByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    const repositorySelect = getByLabelText("选择已关联版本库") as HTMLSelectElement;
    expect(repositorySelect.value).toBe("");
    expect(within(repositorySelect).queryByText("MIMO 示例库(https://gitee.com/mimo/demo.git)")).toBeNull();
    expect(queryByText("非标准库版本")).toBeNull();
    expect(api.listRepositoryBranches).not.toHaveBeenCalled();
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

  it("shows existing workspaces as read-only items without rename or delete operations", async () => {
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
    const { findByText, getByText, queryByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));
    expect(await findByText("测试工作空间")).toBeTruthy();

    expect(queryByText("重命名")).toBeNull();
    expect(queryByText("删除")).toBeNull();
    expect(api.deleteApplicationWorkspace).not.toHaveBeenCalled();
    expect(queryByText("确认删除工作空间")).toBeNull();
  });
});
