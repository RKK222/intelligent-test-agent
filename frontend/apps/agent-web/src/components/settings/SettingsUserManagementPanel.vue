<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CreateUserPayload,
  CurrentUser,
  IdentityStatus,
  RoleOption,
  UserManagementUser
} from "@test-agent/shared-types";

// SettingsPanel 统一向所有面板传入 currentUser，用于权限判断。
const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;

// 权限：仅 SUPER_ADMIN 可用，菜单层也会隐藏入口，此处双保险。
const hasPermission = computed(
  () => props.currentUser?.roles?.includes("SUPER_ADMIN") === true
);

const users = ref<UserManagementUser[]>([]);
const roles = ref<RoleOption[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref("");
const roleDrafts = ref<Record<string, string>>({});
const savingRoles = ref(false);

const loading = ref(false);
const errorMessage = ref("");

// 新增用户表单
const form = ref<CreateUserPayload>({
  unifiedAuthId: "",
  username: "",
  role: "",
  organization: "",
  rdDepartment: "",
  department: ""
});
const creating = ref(false);

// 数据库 IDENTITY 运维
const identityVisible = ref<string[]>([]);
const identityStatuses = ref<IdentityStatus[]>([]);
const identityLoading = ref(false);

async function loadIdentityStatuses() {
  identityLoading.value = true;
  try {
    identityStatuses.value = await api.listIdentityStatuses();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "加载 IDENTITY 状态失败");
  } finally {
    identityLoading.value = false;
  }
}

async function alignIdentity(row: IdentityStatus) {
  try {
    await ElMessageBox.confirm(
      `确认将 ${row.tableName} 的 IDENTITY 对齐到 max(id)+1？`,
      "对齐 IDENTITY",
      { type: "warning" }
    );
    const updated = await api.alignIdentity(row.table);
    identityStatuses.value = identityStatuses.value.map(
      (item) => (item.table === updated.table ? updated : item)
    );
    ElMessage.success("已对齐");
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error instanceof Error ? error.message : "对齐失败");
    }
  }
}

async function restartIdentity(row: IdentityStatus) {
  try {
    const { value } = await ElMessageBox.prompt(
      `输入目标值（需大于当前最大 ID ${row.maxId ?? 0}）`,
      "重置 IDENTITY",
      { inputPattern: /^\d+$/, inputErrorMessage: "请输入正整数", type: "warning" }
    );
    const target = Number(value);
    if (row.maxId != null && target <= row.maxId) {
      ElMessage.error("目标值必须大于当前最大 ID");
      return;
    }
    const updated = await api.restartIdentity(row.table, target);
    identityStatuses.value = identityStatuses.value.map(
      (item) => (item.table === updated.table ? updated : item)
    );
    ElMessage.success("已重置");
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error instanceof Error ? error.message : "重置失败");
    }
  }
}

watch(identityVisible, (visible) => {
  if (visible.includes("identity") && identityStatuses.value.length === 0) {
    loadIdentityStatuses();
  }
});

async function run(action: () => Promise<void>) {
  loading.value = true;
  errorMessage.value = "";
  try {
    await action();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "操作失败";
  } finally {
    loading.value = false;
  }
}

async function loadUsers() {
  await run(async () => {
    const result = await api.listUsers(keyword.value.trim() || undefined, page.value, size.value);
    users.value = result.items;
    total.value = result.total;
    const nextDrafts: Record<string, string> = {};
    for (const user of result.items) {
      nextDrafts[user.userId] = user.roles?.[0] ?? "";
    }
    roleDrafts.value = nextDrafts;
  });
}

async function loadRoles() {
  try {
    roles.value = await api.listRoles();
    // 默认选中普通用户，减少测试造号时的心智负担
    if (!form.value.role && roles.value.length) {
      const userRole = roles.value.find((item) => item.roleCode === "USER");
      form.value.role = userRole?.roleCode ?? roles.value[0].roleCode;
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载角色失败";
  }
}

async function search() {
  page.value = 1;
  await loadUsers();
}

async function handlePageChange(next: number) {
  page.value = next;
  await loadUsers();
}

async function createUser() {
  creating.value = true;
  errorMessage.value = "";
  try {
    const payload: CreateUserPayload = {
      unifiedAuthId: form.value.unifiedAuthId.trim(),
      username: form.value.username.trim(),
      role: form.value.role,
      organization: form.value.organization?.trim() || null,
      rdDepartment: form.value.rdDepartment?.trim() || null,
      department: form.value.department?.trim() || null
    };
    await api.createUser(payload);
    ElMessage.success("已创建，默认密码 123456");
    // 重置表单，保留角色选择便于连续造号
    form.value = {
      ...form.value,
      unifiedAuthId: "",
      username: "",
      organization: "",
      rdDepartment: "",
      department: ""
    };
    await loadUsers();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "创建用户失败";
  } finally {
    creating.value = false;
  }
}

function currentRole(row: UserManagementUser) {
  return row.roles?.[0] ?? "";
}

const changedRoleRows = computed(() =>
  users.value.filter((row) => {
    const role = roleDrafts.value[row.userId];
    return Boolean(role && role !== currentRole(row));
  })
);

const changedRoleCount = computed(() => changedRoleRows.value.length);

async function saveRoleChanges() {
  const changes = changedRoleRows.value.map((row) => ({
    userId: row.userId,
    username: row.username,
    role: roleDrafts.value[row.userId]
  }));
  if (changes.length === 0) {
    return;
  }
  savingRoles.value = true;
  errorMessage.value = "";
  try {
    // 后端目前是单用户角色更新接口，这里按用户逐条提交，避免新增批量契约。
    for (const change of changes) {
      await api.updateUserRole(change.userId, { role: change.role });
    }
    ElMessage.success(`已保存 ${changes.length} 个用户角色`);
    await loadUsers();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "保存角色失败";
  } finally {
    savingRoles.value = false;
  }
}

onMounted(() => {
  if (hasPermission.value) {
    loadUsers();
    loadRoles();
  }
});
</script>

<template>
  <div class="ta-user-mgmt">
    <el-alert v-if="!hasPermission" type="error" :closable="false" show-icon title="无权限" description="仅超级管理员可使用用户管理功能。" />
    <template v-else>
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" />

      <!-- 用户列表 -->
      <section class="ta-section ta-section-primary">
        <div class="ta-list-header">
          <h4 class="ta-section-title">用户列表</h4>
          <div class="ta-list-actions">
            <div class="ta-list-search">
              <el-input
                v-model="keyword"
                placeholder="按用户名/认证号搜索"
                style="width: 220px"
                clearable
                @keyup.enter="search"
              />
              <el-button :disabled="loading || savingRoles" @click="search">查询</el-button>
            </div>
            <el-button
              type="primary"
              :disabled="savingRoles || changedRoleCount === 0"
              @click="saveRoleChanges"
            >
              {{ changedRoleCount > 0 ? `保存角色修改（${changedRoleCount}）` : '保存角色修改' }}
            </el-button>
          </div>
        </div>
        <el-table :data="users" v-loading="loading" size="small" border class="ta-user-table">
          <el-table-column prop="username" label="用户名" min-width="120" />
          <el-table-column prop="unifiedAuthId" label="统一认证号" min-width="140" />
          <el-table-column prop="organization" label="组织" min-width="120" />
          <el-table-column prop="department" label="部门" min-width="120" />
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">
              <div class="ta-role-cell">
                <el-select
                  v-model="roleDrafts[row.userId]"
                  :aria-label="`调整 ${row.username} 的角色`"
                  size="small"
                  style="width: 160px"
                >
                  <el-option
                    v-for="role in roles"
                    :key="role.roleCode"
                    :label="role.roleLabel"
                    :value="role.roleCode"
                  />
                </el-select>
                <span v-if="roleDrafts[row.userId] && roleDrafts[row.userId] !== currentRole(row)" class="ta-role-dirty">
                  已修改
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" />
        </el-table>
        <div class="ta-pagination">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page"
            :page-size="size"
            :total="total"
            @current-change="handlePageChange"
          />
        </div>
      </section>

      <!-- 新增用户表单 -->
      <section class="ta-section ta-create-section">
        <div class="ta-create-heading">
          <h4 class="ta-section-title">新增用户</h4>
          <span class="ta-inline-note">默认密码 123456</span>
        </div>
        <el-form label-position="top" class="ta-settings-form">
          <div class="ta-form-row">
            <el-form-item label="统一认证号">
              <el-input v-model="form.unifiedAuthId" placeholder="必填，登录体系唯一" style="width: 240px" />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="form.username" placeholder="必填" style="width: 240px" />
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="form.role" placeholder="选择角色" style="width: 200px">
                <el-option
                  v-for="role in roles"
                  :key="role.roleCode"
                  :label="role.roleLabel"
                  :value="role.roleCode"
                />
              </el-select>
            </el-form-item>
          </div>
          <div class="ta-form-row">
            <el-form-item label="组织">
              <el-input v-model="form.organization" placeholder="选填" style="width: 240px" />
            </el-form-item>
            <el-form-item label="研发部门">
              <el-input v-model="form.rdDepartment" placeholder="选填" style="width: 240px" />
            </el-form-item>
            <el-form-item label="部门">
              <el-input v-model="form.department" placeholder="选填" style="width: 240px" />
            </el-form-item>
          </div>
          <el-form-item>
            <el-button
              type="primary"
              class="ta-create-button"
              :disabled="creating || !form.unifiedAuthId.trim() || !form.username.trim() || !form.role"
              @click="createUser"
            >
              新增用户
            </el-button>
          </el-form-item>
        </el-form>
      </section>

      <!-- 数据库 IDENTITY 运维 -->
      <el-collapse v-model="identityVisible" class="ta-identity-collapse">
        <el-collapse-item title="数据库 IDENTITY 运维（仅超级管理员）" name="identity">
          <div class="ta-identity-toolbar">
            <el-button size="small" :disabled="identityLoading" @click="loadIdentityStatuses">刷新</el-button>
            <span v-if="identityStatuses.some((s) => s.conflict)" class="ta-identity-warn">
              存在序列落后于已有主键的表，新增数据可能冲突，建议对齐。
            </span>
          </div>
          <el-table :data="identityStatuses" size="small" border>
            <el-table-column prop="tableName" label="表名" />
            <el-table-column prop="currentValue" label="序列当前值" />
            <el-table-column prop="maxId" label="最大ID" />
            <el-table-column label="是否错位">
              <template #default="{ row }">
                <el-tag :type="row.conflict ? 'danger' : 'success'" size="small">
                  {{ row.conflict ? '错位' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" @click="alignIdentity(row)">对齐</el-button>
                <el-button size="small" @click="restartIdentity(row)">重置</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </template>
  </div>
</template>

<style scoped>
.ta-user-mgmt {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ta-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  box-sizing: border-box;
}
.ta-section-primary {
  padding-top: 12px;
}
.ta-create-section {
  background: #fcfcfd;
}
.ta-section-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
.ta-create-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ta-inline-note {
  color: #909399;
  font-size: 12px;
}
.ta-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.ta-list-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.ta-list-search {
  display: flex;
  gap: 8px;
}
.ta-user-table {
  width: 100%;
}
.ta-form-row {
  display: flex;
  flex-wrap: wrap;
  column-gap: 18px;
  row-gap: 4px;
}
.ta-role-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ta-role-dirty {
  flex: none;
  color: #d97706;
  font-size: 12px;
}
.ta-create-button {
  min-width: 96px;
}
.ta-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
.ta-identity-collapse {
  margin-top: 2px;
}
.ta-identity-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.ta-identity-warn {
  color: #d46b08;
  font-size: 12px;
}
</style>
