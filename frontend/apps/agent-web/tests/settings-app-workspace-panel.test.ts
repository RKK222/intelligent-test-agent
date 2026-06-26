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
    standard: true,
    createdAt: "2026-06-26T08:00:00Z",
    updatedAt: "2026-06-26T08:00:00Z"
  },
  {
    repositoryId: "repo_mimo",
    gitUrl: "https://gitee.com/mimo/demo.git",
    name: "MIMO 示例库",
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
    listApplicationRepositories: vi.fn().mockResolvedValue([repositories[0]]),
    listRepositoryApplications: vi.fn().mockResolvedValue([]),
    listApplicationWorkspaces: vi.fn().mockResolvedValue([]),
    listRepositoryBranches: vi.fn().mockResolvedValue(["main"]),
    listRepositoryDirectories: vi.fn().mockResolvedValue(["tests"]),
    createApplicationWorkspace: vi.fn().mockResolvedValue({}),
    createRepository: vi.fn().mockResolvedValue(repositories[1]),
    updateRepository: vi.fn().mockResolvedValue(repositories[0]),
    removeApplicationMember: vi.fn().mockResolvedValue(undefined),
    unlinkApplicationRepository: vi.fn().mockResolvedValue(undefined)
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
        ElIcon: {
          template: `<span><slot /></span>`
        },
        ElInput: ElInputStub,
        ElRadioButton: ElRadioButtonStub,
        ElRadioGroup: ElRadioGroupStub,
        ElSelect: ElSelectStub,
        ElOption: ElOptionStub,
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
    expect(tabs).toEqual(["应用人员管理", "版本库管理", "应用与版本库关联", "工作空间管理"]);
    expect(getByText("版本库管理")).toBeTruthy();

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

  it("switches to repository management and focuses create form from the select create option", async () => {
    const { findByText, getAllByLabelText, getAllByTitle, getByPlaceholderText, getByText } = renderPanel();

    await findByText("应用人员管理");
    await fireEvent.click(getByText("应用与版本库关联"));

    await fireEvent.update(getAllByLabelText("选择版本库")[0], selectCreateRepositoryValue);
    expect(await findByText("已有版本库")).toBeTruthy();
    expect(getByText("共 2 个版本库")).toBeTruthy();
    await waitFor(() => expect(document.activeElement).toBe(getByPlaceholderText("Git URL")));
    expect(getAllByTitle("标准代码库是指测试自己去git申请，专门用于测试智能体的版本库。").length).toBeGreaterThan(0);
  });

  it("shows repository count and creates repositories in management tab", async () => {
    const api = createApi();
    const { findByText, getByPlaceholderText, getByText } = renderPanel(api);

    await findByText("应用人员管理");
    await fireEvent.click(getByText("版本库管理"));

    expect(await findByText("共 2 个版本库")).toBeTruthy();
    expect(getByText("已有版本库")).toBeTruthy();
    expect(getByText("新增版本库")).toBeTruthy();

    await fireEvent.update(getByPlaceholderText("Git URL"), "https://gitee.com/mimo/new-repo.git");
    await fireEvent.update(getByPlaceholderText("中文名称"), "新增测试库");
    await fireEvent.click(getByText("新增"));

    await waitFor(() => expect(api.createRepository).toHaveBeenCalledWith({
      gitUrl: "https://gitee.com/mimo/new-repo.git",
      name: "新增测试库",
      standard: false
    }));
  });

  it("labels repository management forms and cancels repository editing", async () => {
    const { container, findByText, getAllByText, getByPlaceholderText, getByText, queryByPlaceholderText, queryByText } = renderPanel();

    await findByText("应用人员管理");
    await fireEvent.click(getByText("版本库管理"));

    expect(await findByText("版本库地址")).toBeTruthy();
    expect(getAllByText("版本库名称").length).toBeGreaterThanOrEqual(1);
    const createNameRow = container.querySelector(".ta-repository-create-name-row");
    expect(createNameRow?.textContent).toContain("版本库名称");
    expect(createNameRow?.querySelector("input")?.getAttribute("placeholder")).toBe("中文名称");

    await fireEvent.click(getAllByText("编辑")[0]);
    expect(getByText("取消")).toBeTruthy();
    expect(getAllByText("版本库名称").length).toBeGreaterThanOrEqual(2);

    await fireEvent.update(getByPlaceholderText("名称"), "临时名称");
    await fireEvent.click(getByText("取消"));

    expect(queryByText("取消")).toBeNull();
    expect(queryByPlaceholderText("名称")).toBeNull();
  });

  it("shows workspace creation as three labeled steps", async () => {
    const { container, findByText, getByText, queryByText } = renderPanel();

    await findByText("应用人员管理");
    await fireEvent.click(getByText("工作空间管理"));

    const createSection = getByText("创建工作空间").closest(".ta-section");
    expect(createSection).toBeTruthy();
    const createSectionText = createSection?.textContent ?? "";
    expect(createSectionText.indexOf("第一步：刷新分支")).toBeLessThan(createSectionText.indexOf("第二步：加载目录"));
    expect(createSectionText.indexOf("第二步：加载目录")).toBeLessThan(createSectionText.indexOf("第三步：创建工作空间"));
    expect(within(createSection as HTMLElement).getByText("已关联版本库")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("分支")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("目录")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("工作空间名称")).toBeTruthy();
    expect(within(createSection as HTMLElement).getByText("刷新分支")).toBeTruthy();
    expect(queryByText("加载分支")).toBeNull();
    expect(container.querySelectorAll(".ta-workspace-step").length).toBe(3);
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
});
