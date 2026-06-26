import { defineComponent, h, inject, provide } from "vue";
import { describe, expect, it, vi } from "vitest";
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
    createRepository: vi.fn().mockResolvedValue(repositories[1])
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
  it("uses version-library wording and adds a dedicated management tab", async () => {
    const { findByText, getByText, queryByText } = renderPanel();

    await findByText("应用人员管理");
    expect(getByText("版本库管理")).toBeTruthy();

    await fireEvent.click(getByText("应用与版本库关联"));

    expect(await findByText("关联版本库到当前应用")).toBeTruthy();
    expect(getByText("版本库与应用双向关联")).toBeTruthy();
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
});
