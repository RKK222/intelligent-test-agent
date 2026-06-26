import { defineComponent, h } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, RoleOption, UserManagementUser } from "@test-agent/shared-types";
import SettingsUserManagementPanel from "../src/components/settings/SettingsUserManagementPanel.vue";

const users: UserManagementUser[] = [
  {
    userId: "usr_existing",
    username: "alice",
    unifiedAuthId: "AUTH_1",
    organization: "工行",
    rdDepartment: "研发部",
    department: "测试部",
    status: "ACTIVE",
    roles: ["APP_ADMIN"],
    roleLabels: ["应用管理员"],
    createdAt: "2026-06-26T00:00:00Z"
  }
];

const roles: RoleOption[] = [
  { roleCode: "SUPER_ADMIN", roleLabel: "超级管理员" },
  { roleCode: "APP_ADMIN", roleLabel: "应用管理员" },
  { roleCode: "USER", roleLabel: "普通用户" }
];

function createApi(): Partial<BackendApiClient> {
  return {
    listUsers: vi.fn().mockResolvedValue({ items: users, page: 1, size: 20, total: users.length }),
    listRoles: vi.fn().mockResolvedValue(roles),
    createUser: vi.fn().mockResolvedValue(users[0])
  };
}

const radioGroupKey = Symbol("radio-group");

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
  render() {
    return h("input", {
      placeholder: this.placeholder,
      value: this.modelValue,
      onInput: (event: Event) => this.$emit("update:modelValue", (event.target as HTMLInputElement).value)
    });
  }
});

const adminUser: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_ADMIN",
  roles: ["SUPER_ADMIN"]
};

function renderPanel(api: Partial<BackendApiClient> = createApi(), currentUser: CurrentUser = adminUser) {
  return render(SettingsUserManagementPanel, {
    props: { currentUser },
    global: {
      stubs: {
        ElAlert: {
          props: ["title", "description", "type"],
          template: `<div role="alert" :data-type="type"><span>{{ title }}</span><span>{{ description }}</span></div>`
        },
        ElButton: {
          emits: ["click"],
          props: ["disabled", "type"],
          template: `<button type="button" :disabled="disabled" @click="$emit('click')"><slot /></button>`
        },
        ElIcon: { template: `<span><slot /></span>` },
        ElInput: ElInputStub,
        ElSelect: ElSelectStub,
        ElOption: ElOptionStub,
        ElForm: { template: `<form><slot /></form>` },
        ElFormItem: {
          props: ["label"],
          template: `<div><label>{{ label }}</label><slot /></div>`
        },
        ElTable: { template: `<div class="ta-table-stub"><slot /></div>` },
        ElTableColumn: { props: ["prop", "label"], template: `<span :data-prop="prop">{{ label }}</span>` },
        ElPagination: { props: ["total"], template: `<div class="ta-pagination-stub">total={{ total }}</div>` }
      },
      provide: {
        api: api as BackendApiClient
      }
    }
  });
}

describe("SettingsUserManagementPanel", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads users and roles on mount for super admin", async () => {
    const api = createApi();
    const { findByText } = renderPanel(api);

    // 列表区标题渲染即表示挂载后已发起请求
    await findByText("用户列表");
    await waitFor(() => expect(api.listUsers).toHaveBeenCalledWith(undefined, 1, 20));
    expect(api.listRoles).toHaveBeenCalled();
  });

  it("creates user with default role and refreshes list", async () => {
    const api = createApi();
    const { getByPlaceholderText, getByRole } = renderPanel(api);

    // 等待角色加载完成（默认选中 USER 后填完必填项即可提交）
    await waitFor(() => expect(api.listRoles).toHaveBeenCalled());

    await fireEvent.update(getByPlaceholderText("必填，登录体系唯一"), "AUTH_2");
    await fireEvent.update(getByPlaceholderText("必填"), "bob");

    await fireEvent.click(getByRole("button", { name: "新增用户" }));

    await waitFor(() => expect(api.createUser).toHaveBeenCalled());
    const payload = (api.createUser as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(payload).toMatchObject({ unifiedAuthId: "AUTH_2", username: "bob", role: "USER" });
    // 创建成功后刷新列表
    await waitFor(() => expect((api.listUsers as ReturnType<typeof vi.fn>).mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  it("shows no-permission alert for non-super-admin and does not call api", async () => {
    const api = createApi();
    const normalUser: CurrentUser = {
      userId: "usr_normal",
      username: "normal",
      unifiedAuthId: "AUTH_NORMAL",
      roles: ["APP_ADMIN"]
    };
    const { findAllByText } = renderPanel(api, normalUser);

    const alerts = await findAllByText("无权限");
    expect(alerts.length).toBeGreaterThan(0);
    expect(api.listUsers).not.toHaveBeenCalled();
    expect(api.listRoles).not.toHaveBeenCalled();
  });
});
