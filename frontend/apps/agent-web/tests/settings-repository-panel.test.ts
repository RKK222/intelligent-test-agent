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
    deploymentMode: "EXTERNAL",
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
    deploymentMode: "EXTERNAL",
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
    getRepositoryDeploymentOptions: vi.fn().mockResolvedValue({
      defaultDeploymentMode: "EXTERNAL",
      internalSshPrefix: "ssh://AUTH_ADMIN@",
      options: [
        { mode: "EXTERNAL", label: "外部部署" },
        { mode: "INTERNAL", label: "内部部署" }
      ]
    }),
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
  props: ["modelValue", "placeholder", "disabled"],
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
      disabled: this.disabled,
      onInput: (event: Event) => this.$emit("update:modelValue", (event.target as HTMLInputElement).value)
    });
  }
});

function renderPanel(api = createApi(), extraProps = {}) {
  return render(SettingsRepositoryPanel, {
    props: {
      currentUser: {
        userId: "usr_admin",
        username: "admin",
        unifiedAuthId: "AUTH_ADMIN",
        roles: ["APP_ADMIN"]
      },
      ...extraProps
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
        ElDialog: {
          props: ["modelValue", "title"],
          emits: ["update:modelValue", "opened"],
          watch: {
            modelValue: {
              handler(val) {
                if (val) {
                  this.$nextTick(() => this.$emit('opened'));
                }
              },
              immediate: true
            }
          },
          template: `<div v-if="modelValue" class="el-dialog-stub" role="dialog"><h3>{{ title }}</h3><slot /><slot name="footer" /></div>`
        },
        ElForm: {
          template: `<form><slot /></form>`
        },
        ElFormItem: {
          props: ["label"],
          template: `<div><label>{{ label }}</label><slot /></div>`
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
    const { findByText, getByText, queryByText } = renderPanel();

    expect(await findByText("共 2 个版本库")).toBeTruthy();
    expect(getByText("已有版本库")).toBeTruthy();
    expect(queryByText("新增版本库")).toBeNull();
    expect(getByText("F-WRTESTAPP 本地测试库")).toBeTruthy();
    expect(getByText("MIMO 示例库")).toBeTruthy();
  });

  it("creates repositories in management tab", async () => {
    const api = createApi();
    const { findByText, getByLabelText, getByPlaceholderText, getByText, container } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.click(getByText("新增")); // Open dialog
    expect(container.querySelector(".el-dialog-stub")).toBeTruthy();

    await fireEvent.update(getByPlaceholderText("Git URL"), "https://gitee.com/mimo/new-repo.git");
    await fireEvent.update(getByPlaceholderText("中文名称"), "新增测试库");
    await fireEvent.update(getByPlaceholderText("英文名称"), "NewRepo");
    await fireEvent.update(getByLabelText("版本库类型"), "TEST_WORK_REPOSITORY");
    await fireEvent.click(within(container.querySelector(".el-dialog-stub")!).getByText("新增"));

    await waitFor(() => expect(api.createRepository).toHaveBeenCalledWith({
      gitUrl: "https://gitee.com/mimo/new-repo.git",
      name: "新增测试库",
      englishName: "newrepo",
      deploymentMode: "EXTERNAL",
      repositoryType: "TEST_WORK_REPOSITORY",
      standard: true
    }));
  });

  it("rejects invalid repository english names before calling the backend", async () => {
    const api = createApi();
    const { findByText, getByPlaceholderText, getByLabelText, getByText, container } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.click(getByText("新增")); // Open dialog

    await fireEvent.update(getByPlaceholderText("Git URL"), "https://gitee.com/mimo/new-repo.git");
    await fireEvent.update(getByPlaceholderText("中文名称"), "新增测试库");
    await fireEvent.update(getByLabelText("版本库类型"), "APPLICATION_CODE_REPOSITORY");
    await fireEvent.update(getByPlaceholderText("英文名称"), "-newrepo");
    await fireEvent.click(within(container.querySelector(".el-dialog-stub")!).getByText("新增"));

    expect(await findByText("版本库英文名称只能使用字母、数字 and 连字符，长度 1 到 128，且不能以连字符开头或结尾").catch(() => null) || await findByText("版本库英文名称只能使用字母、数字和连字符，长度 1 到 128，且不能以连字符开头或结尾")).toBeTruthy();
    expect(api.createRepository).not.toHaveBeenCalled();
  });

  it("creates internal repositories with readonly ssh prefix and derived english name", async () => {
    const api = createApi();
    vi.mocked(api.getRepositoryDeploymentOptions!).mockResolvedValue({
      defaultDeploymentMode: "INTERNAL",
      internalSshPrefix: "ssh://AUTH_ADMIN@",
      options: [
        { mode: "EXTERNAL", label: "外部部署" },
        { mode: "INTERNAL", label: "内部部署" }
      ]
    });
    const { findByDisplayValue, findByText, getByLabelText, getByPlaceholderText, getByText, container } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.click(getByText("新增"));
    expect(await findByText("ssh://AUTH_ADMIN@")).toBeTruthy();

    await fireEvent.update(
      getByPlaceholderText("scm-share.sdc.cs.enterprise:29418/group/repository"),
      "scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform"
    );
    await fireEvent.update(getByPlaceholderText("中文名称"), "接口平台");
    await fireEvent.update(getByLabelText("版本库类型"), "APPLICATION_CODE_REPOSITORY");
    expect(await findByDisplayValue("hzefficiencytools-interfaceplatform")).toBeTruthy();
    await fireEvent.click(within(container.querySelector(".el-dialog-stub")!).getByText("新增"));

    await waitFor(() => expect(api.createRepository).toHaveBeenCalledWith({
      gitUrl: "scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform",
      name: "接口平台",
      englishName: "hzefficiencytools-interfaceplatform",
      deploymentMode: "INTERNAL",
      repositoryType: "APPLICATION_CODE_REPOSITORY",
      standard: false
    }));
  });

  it("validates that repository name and type are required", async () => {
    const api = createApi();
    const { findByText, getByPlaceholderText, getByLabelText, getByText, container } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.click(getByText("新增"));

    // Case 1: Empty name
    await fireEvent.update(getByPlaceholderText("Git URL"), "https://gitee.com/mimo/new-repo.git");
    await fireEvent.update(getByLabelText("版本库类型"), "APPLICATION_CODE_REPOSITORY");
    await fireEvent.click(within(container.querySelector(".el-dialog-stub")!).getByText("新增"));
    expect(await findByText("请输入版本库名称")).toBeTruthy();
    expect(api.createRepository).not.toHaveBeenCalled();

    // Case 2: Empty type
    await fireEvent.update(getByPlaceholderText("中文名称"), "新增测试库");
    // Clear repository type (select option placeholder / empty)
    await fireEvent.update(getByLabelText("版本库类型"), "");
    await fireEvent.click(within(container.querySelector(".el-dialog-stub")!).getByText("新增"));
    expect(await findByText("请选择版本库类型")).toBeTruthy();
    expect(api.createRepository).not.toHaveBeenCalled();
  });

  it("disables English name input in internal deployment mode and derives it automatically", async () => {
    const api = createApi();
    vi.mocked(api.getRepositoryDeploymentOptions!).mockResolvedValue({
      defaultDeploymentMode: "INTERNAL",
      internalSshPrefix: "ssh://AUTH_ADMIN@",
      options: [
        { mode: "EXTERNAL", label: "外部部署" },
        { mode: "INTERNAL", label: "内部部署" }
      ]
    });
    const { findByText, getByPlaceholderText, getByText, container } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.click(getByText("新增"));

    // Check that english name input is disabled when in internal mode
    const englishNameInput = getByPlaceholderText("英文名称") as HTMLInputElement;
    expect(englishNameInput.disabled).toBe(true);

    // Enter Git URL and check derived English name
    await fireEvent.update(
      getByPlaceholderText("scm-share.sdc.cs.enterprise:29418/group/repository"),
      "scm-share.sdc.cs.enterprise:29418/foo/bar-repo"
    );
    expect(englishNameInput.value).toBe("foo-bar-repo");
  });

  it("warns when selected repository deployment mode differs from current deployment mode", async () => {
    const api = createApi();
    const { findByText, getByLabelText, getByText } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.click(getByText("新增"));
    await fireEvent.update(getByLabelText("部署模式"), "INTERNAL");

    expect(await findByText("当前部署模式为外部部署，若修改部署模式，将导致无法访问版本库。")).toBeTruthy();
  });

  it("lists internal repositories with stored git url and shows dynamic url only in edit dialog", async () => {
    const api = createApi();
    vi.mocked(api.listRepositories!).mockResolvedValue({
      items: [{
        repositoryId: "repo_internal",
        gitUrl: "scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform",
        name: "接口平台",
        englishName: "hzefficiencytools-interfaceplatform",
        repositoryType: "APPLICATION_CODE_REPOSITORY",
        repositoryTypeLabel: "应用代码库",
        deploymentMode: "INTERNAL",
        standard: false,
        createdAt: "2026-07-02T08:00:00Z",
        updatedAt: "2026-07-02T08:00:00Z"
      }],
      page: 1,
      size: 100,
      total: 1
    });
    const { findByText, getByText, queryByText, container } = renderPanel(api);

    expect(await findByText("hzefficiencytools-interfaceplatform · scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform")).toBeTruthy();
    expect(queryByText("ssh://AUTH_ADMIN@scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform")).toBeNull();

    await fireEvent.click(getByText("编辑"));

    expect(container.querySelector(".el-dialog-stub")).toBeTruthy();
    expect(await findByText("ssh://AUTH_ADMIN@scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform")).toBeTruthy();
  });

  it("labels repository management forms and cancels repository editing", async () => {
    const { container, findByText, getAllByText, getByPlaceholderText, getByText, queryByPlaceholderText, queryByText } = renderPanel();

    expect(await findByText("共 2 个版本库")).toBeTruthy();
    
    await fireEvent.click(getByText("新增")); // Open dialog
    expect(await findByText("版本库地址")).toBeTruthy();
    expect(getAllByText("版本库名称").length).toBeGreaterThanOrEqual(1);
    expect(getAllByText("版本库英文名称").length).toBeGreaterThanOrEqual(1);

    await fireEvent.click(within(container.querySelector(".el-dialog-stub")!).getByText("取消")); // Close dialog

    await fireEvent.click(getAllByText("编辑")[0]); // Open edit dialog
    expect(container.querySelector(".el-dialog-stub")).toBeTruthy();
    expect(within(container.querySelector(".el-dialog-stub")!).getByText("测试工作库")).toBeTruthy();

    await fireEvent.update(getByPlaceholderText("名称"), "临时名称");
    await fireEvent.click(within(container.querySelector(".el-dialog-stub")!).getByText("取消"));

    expect(queryByText("取消")).toBeNull();
    expect(queryByPlaceholderText("名称")).toBeNull();
  });

  it("edits repositories via edit dialog", async () => {
    const api = createApi();
    const { findByText, getByPlaceholderText, getByText, getAllByText, container } = renderPanel(api);

    expect(await findByText("共 2 个版本库")).toBeTruthy();

    await fireEvent.click(getAllByText("编辑")[0]); // Click first editing button
    expect(container.querySelector(".el-dialog-stub h3")?.textContent).toBe("编辑版本库");

    await fireEvent.update(getByPlaceholderText("名称"), "新名称");
    await fireEvent.update(getByPlaceholderText("英文名称"), "newname");
    await fireEvent.click(within(container.querySelector(".el-dialog-stub")!).getByText("保存"));

    await waitFor(() => expect(api.updateRepository).toHaveBeenCalledWith("repo_wr", {
      name: "新名称",
      englishName: "newname"
    }));
  });

  it("automatically opens the dialog when autoOpenCreate prop is true", async () => {
    const { container, findByText } = renderPanel(createApi(), { autoOpenCreate: true });
    expect(await findByText("共 2 个版本库")).toBeTruthy();
    expect(container.querySelector(".el-dialog-stub")).toBeTruthy();
  });
});
