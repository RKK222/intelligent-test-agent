import { defineComponent, h, inject, provide } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor, within } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CodeRepositoryConfig } from "@test-agent/shared-types";
import SettingsRepositoryPanel from "../src/components/settings/SettingsRepositoryPanel.vue";

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
    listRepositories: vi.fn().mockResolvedValue({ items: repositories, page: 1, size: 100, total: repositories.length }),
    listRepositoryTypes: vi.fn().mockResolvedValue([
      { typeCode: "TEST_WORK_REPOSITORY", typeLabel: "测试工作库" },
      { typeCode: "APPLICATION_CODE_REPOSITORY", typeLabel: "应用代码库" },
      { typeCode: "APPLICATION_ASSET_REPOSITORY", typeLabel: "应用资产库" }
    ]),
    createRepository: vi.fn().mockResolvedValue(repositories[1]),
    updateRepository: vi.fn().mockResolvedValue(repositories[0])
  };
}

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

function renderPanel(api = createApi()) {
  return render(SettingsRepositoryPanel, {
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
        ElButton: {
          emits: ["click"],
          template: `<button type="button" @click="$emit('click')"><slot /></button>`
        },
        ElIcon: {
          template: `<span><slot /></span>`
        },
        ElInput: ElInputStub,
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

describe("SettingsRepositoryPanel settings", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows repository count and lists existing repositories", async () => {
    const { findByText, getByText } = renderPanel();

    expect(await findByText("共 2 个版本库")).toBeTruthy();
    expect(getByText("已有版本库")).toBeTruthy();
    expect(getByText("新增版本库")).toBeTruthy();
    expect(getByText("F-WRTESTAPP 本地测试库")).toBeTruthy();
    expect(getByText("MIMO 示例库")).toBeTruthy();
  });

  it("creates repositories in management tab", async () => {
    const api = createApi();
    const { findByText, getByLabelText, getByPlaceholderText, getByText } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.update(getByPlaceholderText("Git URL"), "https://gitee.com/mimo/new-repo.git");
    await fireEvent.update(getByPlaceholderText("中文名称"), "新增测试库");
    await fireEvent.update(getByPlaceholderText("英文名称"), "NewRepo");
    await fireEvent.update(getByLabelText("版本库类型"), "TEST_WORK_REPOSITORY");
    await fireEvent.click(getByText("新增"));

    await waitFor(() => expect(api.createRepository).toHaveBeenCalledWith({
      gitUrl: "https://gitee.com/mimo/new-repo.git",
      name: "新增测试库",
      englishName: "newrepo",
      repositoryType: "TEST_WORK_REPOSITORY",
      standard: true
    }));
  });

  it("rejects invalid repository english names before calling the backend", async () => {
    const api = createApi();
    const { findByText, getByPlaceholderText, getByText } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.update(getByPlaceholderText("Git URL"), "https://gitee.com/mimo/new-repo.git");
    await fireEvent.update(getByPlaceholderText("中文名称"), "新增测试库");
    await fireEvent.update(getByPlaceholderText("英文名称"), "new-repo");
    await fireEvent.click(getByText("新增"));

    expect(await findByText("版本库英文名称只能输入 1 到 29 位英文字母")).toBeTruthy();
    expect(api.createRepository).not.toHaveBeenCalled();
  });

  it("labels repository management forms and cancels repository editing", async () => {
    const { container, findByText, getAllByText, getByPlaceholderText, getByText, queryByPlaceholderText, queryByText } = renderPanel();

    expect(await findByText("共 2 个版本库")).toBeTruthy();
    expect(await findByText("版本库地址")).toBeTruthy();
    expect(getAllByText("版本库名称").length).toBeGreaterThanOrEqual(1);
    expect(getAllByText("版本库英文名称").length).toBeGreaterThanOrEqual(1);
    const createNameRow = container.querySelector(".ta-repository-create-name-row");
    expect(createNameRow?.textContent).toContain("版本库名称");
    expect(createNameRow?.textContent).toContain("版本库英文名称");
    expect(createNameRow?.textContent).toContain("版本库类型");
    expect(createNameRow?.querySelector("input")?.getAttribute("placeholder")).toBe("中文名称");

    await fireEvent.click(getAllByText("编辑")[0]);
    expect(getByText("取消")).toBeTruthy();
    expect(getAllByText("版本库名称").length).toBeGreaterThanOrEqual(2);
    expect(getAllByText("版本库英文名称").length).toBeGreaterThanOrEqual(2);
    expect(getByText("类型：测试工作库")).toBeTruthy();

    await fireEvent.update(getByPlaceholderText("名称"), "临时名称");
    await fireEvent.click(getByText("取消"));

    expect(queryByText("取消")).toBeNull();
    expect(queryByPlaceholderText("名称")).toBeNull();
  });
});
