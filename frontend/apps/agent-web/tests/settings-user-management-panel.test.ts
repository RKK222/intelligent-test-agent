import { defineComponent, h, inject, provide } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { ElMessageBox } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, RoleOption, UserManagementUser } from "@test-agent/shared-types";
import SettingsUserManagementPanel from "../src/components/settings/SettingsUserManagementPanel.vue";

const users: UserManagementUser[] = [
  {
    userId: "usr_existing",
    username: "alice",
    unifiedAuthId: "AUTH_1",
    organization: "企业",
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
    createUser: vi.fn().mockResolvedValue(users[0]),
    updateUserRole: vi.fn().mockResolvedValue({ ...users[0], roles: ["USER"], roleLabels: ["普通用户"] }),
    deleteUser: vi.fn().mockResolvedValue({ deletedUserIds: ["usr_existing"], deletedCount: 1 }),
    deleteUsers: vi.fn().mockResolvedValue({ deletedUserIds: ["usr_existing"], deletedCount: 1 }),
    syncUserFromTcds: vi.fn().mockResolvedValue({ syncedUserIds: ["usr_existing"], syncedCount: 1 }),
    syncUsersFromTcds: vi.fn().mockResolvedValue({ syncedUserIds: ["usr_existing"], syncedCount: 1 })
  };
}

const radioGroupKey = Symbol("radio-group");
const tableRowsKey = Symbol("table-rows");

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

const ElTableStub = defineComponent({
  props: ["data"],
  emits: ["selection-change"],
  setup(props, { emit, slots }) {
    const selected = new Set<string>();
    const context = {
      props,
      toggle(row: UserManagementUser, checked: boolean) {
        if (checked) {
          selected.add(row.userId);
        } else {
          selected.delete(row.userId);
        }
        emit("selection-change", (props.data ?? []).filter((item: UserManagementUser) => selected.has(item.userId)));
      }
    };
    provide(tableRowsKey, context);
    return () => h("div", { class: "ta-table-stub" }, slots.default?.());
  }
});

const ElTableColumnStub = defineComponent({
  props: ["prop", "label", "type", "selectable"],
  setup(props, { slots }) {
    const tableContext = inject<{
      props?: { data?: UserManagementUser[] };
      toggle?: (row: UserManagementUser, checked: boolean) => void;
    }>(tableRowsKey, {});
    return () =>
      props.type === "selection"
        ? h(
            "div",
            { "data-type": "selection" },
            (tableContext.props?.data ?? []).map((row) =>
              h("input", {
                type: "checkbox",
                "aria-label": `选择 ${row.username}`,
                disabled: typeof props.selectable === "function" && !props.selectable(row),
                onChange: (event: Event) =>
                  tableContext.toggle?.(row, (event.target as HTMLInputElement).checked)
              })
            )
          )
        : h(
            "div",
            { "data-prop": props.prop },
            (tableContext.props?.data ?? []).flatMap(
              (row) =>
                slots.default?.({ row }) ?? [
                  h("span", String(props.prop ? row[props.prop as keyof UserManagementUser] ?? "" : props.label))
                ]
            )
          );
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
          props: ["disabled", "type", "link", "plain"],
          template: `<button v-bind="$attrs" type="button" :disabled="disabled" @click="$emit('click')"><slot /></button>`
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
        ElTable: ElTableStub,
        ElTableColumn: ElTableColumnStub,
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
    const { container, findByText } = renderPanel(api);

    // 列表区标题渲染即表示挂载后已发起请求
    await findByText("用户列表");
    await waitFor(() => expect(api.listUsers).toHaveBeenCalledWith(undefined, 1, 20));
    expect(api.listRoles).toHaveBeenCalled();
    expect(container.textContent?.indexOf("用户列表")).toBeLessThan(container.textContent?.indexOf("新增用户") ?? 0);
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

  it("saves changed roles from the list header and refreshes list", async () => {
    const api = createApi();
    const { findByText, getByLabelText, getByRole } = renderPanel(api);

    await waitFor(() => expect(api.listUsers).toHaveBeenCalledWith(undefined, 1, 20));
    await findByText("alice");

    await fireEvent.update(getByLabelText("调整 alice 的角色"), "USER");
    await findByText("已修改");
    await fireEvent.click(getByRole("button", { name: "保存角色修改（1）" }));

    await waitFor(() => expect(api.updateUserRole).toHaveBeenCalledWith("usr_existing", { role: "USER" }));
    await waitFor(() => expect((api.listUsers as ReturnType<typeof vi.fn>).mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  it("deletes one user after confirmation and refreshes the list", async () => {
    const api = createApi();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const { findByText, getByRole } = renderPanel(api);

    await findByText("alice");
    await fireEvent.click(getByRole("button", { name: "删除" }));

    await waitFor(() => expect(api.deleteUser).toHaveBeenCalledWith("usr_existing"));
    await waitFor(() => expect((api.listUsers as ReturnType<typeof vi.fn>).mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  it("batch deletes selected users", async () => {
    const api = createApi();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const { findByText, getByLabelText, getByRole } = renderPanel(api);

    await findByText("alice");
    await fireEvent.click(getByLabelText("选择 alice"));
    await fireEvent.click(getByRole("button", { name: "批量删除（1）" }));

    await waitFor(() =>
      expect(api.deleteUsers).toHaveBeenCalledWith({ userIds: ["usr_existing"] })
    );
  });

  it("syncs one user and selected users from TCDS while keeping the same ids", async () => {
    const api = createApi();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const { findByText, getByLabelText, getByRole } = renderPanel(api);

    await findByText("alice");
    await fireEvent.click(getByRole("button", { name: "同步 TCDS" }));
    await waitFor(() => expect(api.syncUserFromTcds).toHaveBeenCalledWith("usr_existing"));

    await fireEvent.click(getByLabelText("选择 alice"));
    await fireEvent.click(getByRole("button", { name: "批量同步 TCDS（1）" }));
    await waitFor(() =>
      expect(api.syncUsersFromTcds).toHaveBeenCalledWith({ userIds: ["usr_existing"] })
    );
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
