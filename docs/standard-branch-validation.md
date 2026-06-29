# 标准库分支选择限制功能 - 验证文档

## 功能说明

在工作空间管理中创建工作空间时，如果选择的代码库是标准库（standard=true），分支只能选择 `feature_testagent_yyyyMMdd` 格式的分支，其他格式的分支要展示但置灰不可选。

**排序优化**：分支列表按以下规则排序：
- 符合格式的分支排在前面（可选择）
- 不符合格式的分支排在后面（置灰不可选）
- 每组内部按字母顺序排序

## 实现位置

**文件**: `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue`

## 核心代码

### 1. 分支格式校验函数

```typescript
function isValidStandardBranch(branch: string): boolean {
  // 正则匹配：feature_testagent_ + 8位数字
  const pattern = /^feature_testagent_\d{8}$/;
  if (!pattern.test(branch)) return false;

  // 提取并校验日期有效性
  const dateStr = branch.slice(-8);
  const year = parseInt(dateStr.slice(0, 4), 10);
  const month = parseInt(dateStr.slice(4, 6), 10);
  const day = parseInt(dateStr.slice(6, 8), 10);

  // 范围校验
  if (month < 1 || month > 12 || day < 1 || day > 31) return false;

  // 日期对象校验（自动处理2月30日等）
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year &&
         date.getMonth() === month - 1 &&
         date.getDate() === day;
}
```

### 2. 分支禁用判断

```typescript
function isBranchDisabled(branch: string): boolean {
  if (!selectedWorkspaceRepository.value?.standard) return false;
  return !isValidStandardBranch(branch);
}
```

### 3. 分支排序

```typescript
const sortedBranches = computed(() => {
  if (!selectedWorkspaceRepository.value?.standard) {
    // 非标准库：按原始顺序返回
    return branches.value;
  }
  // 标准库：符合格式的排前面，不符合的排后面
  const validBranches: string[] = [];
  const invalidBranches: string[] = [];

  branches.value.forEach(branch => {
    if (isValidStandardBranch(branch)) {
      validBranches.push(branch);
    } else {
      invalidBranches.push(branch);
    }
  });

  // 每组内部按字母顺序排序
  validBranches.sort();
  invalidBranches.sort();

  return [...validBranches, ...invalidBranches];
});
```

### 4. 手动输入校验

```typescript
function handleBranchChange(branch: string) {
  customBranchError.value = "";

  if (selectedWorkspaceRepository.value?.standard && branch) {
    if (!isValidStandardBranch(branch)) {
      customBranchError.value = "标准库只能使用 feature_testagent_yyyyMMdd 格式的分支";
    }
  }
}
```

### 5. UI 组件修改

```vue
<el-select
  v-model="workspaceBranch"
  filterable
  allow-create
  default-first-option
  placeholder="选择或输入分支"
  style="width: 100%"
  @change="handleBranchChange"
>
  <el-option
    v-for="branch in sortedBranches"
    :key="branch"
    :label="branch"
    :value="branch"
    :disabled="isBranchDisabled(branch)"
  />
</el-select>
<!-- 错误提示 -->
<div v-if="customBranchError" class="ta-branch-error">
  {{ customBranchError }}
</div>
```

## 测试场景

### 场景1：标准库 - 从下拉中选择

**步骤**：
1. 选择一个标准库（standard=true）
2. 点击"刷新分支"
3. 查看分支列表

**预期结果**：
- ✅ 符合格式的分支排在前面，可以点击选择
- ❌ 不符合格式的分支排在后面，置灰，无法选择
- 每组内部按字母顺序排列

### 场景2：标准库 - 手动输入

**步骤**：
1. 选择一个标准库
2. 在分支输入框中手动输入 `feature_testagent_20260629`
3. 查看是否显示错误提示

**预期结果**：
- 输入正确格式：无错误提示，"加载目录"按钮可用
- 输入错误格式：显示错误提示，"加载目录"按钮禁用

### 场景3：非标准库

**步骤**：
1. 选择一个非标准库（standard=false）
2. 点击"刷新分支"
3. 选择任意分支

**预期结果**：
- 所有分支都可以选择，无格式限制
- 分支按原始顺序排列
- 手动输入任何分支都不会有错误提示

### 场景4：切换代码库

**步骤**：
1. 选择标准库A，刷新分支，验证排序和禁用逻辑
2. 切换到非标准库B
3. 查看分支选择状态

**预期结果**：
- 标准库A：符合格式的分支在前，不符合的在后并置灰
- 非标准库B：所有分支可选，错误提示消失

## 分支格式示例

### ✅ 有效格式（排在前面）

- `feature_testagent_20260629` - 正常日期
- `feature_testagent_20251231` - 年末日期
- `feature_testagent_20250101` - 年初日期
- `feature_testagent_20240229` - 闰年2月29日

### ❌ 无效格式（排在后面，置灰）

- `master` - 无前缀
- `develop` - 无前缀
- `feature_abc` - 无日期
- `feature_testagent_20261` - 日期位数不足
- `feature_testagent_20261301` - 无效月份（13月）
- `feature_testagent_20260230` - 无效日期（2月30日）
- `feature_testagent_20250229` - 非闰年2月29日

## 边界测试

1. **闰年测试**：
   - 2024年是闰年，2月29日有效
   - 2025年不是闰年，2月29日无效

2. **极端日期测试**：
   - 12月31日有效
   - 1月1日有效
   - 0日无效
   - 32日无效

3. **格式边界测试**：
   - 7位数字无效
   - 9位数字无效
   - 8位数字但非日期无效

4. **排序测试**：
   - 验证符合格式的分支确实排在前面
   - 验证每组内部按字母顺序排序

## 不修改的部分

- ✅ 后端API保持不变（仍返回纯字符串列表）
- ✅ 类型定义不需要修改（`CodeRepositoryConfig` 已有 `standard` 字段）
- ✅ 数据库结构不需要修改

## 验证方法

### 手动测试

1. 启动前端应用
2. 登录并进入"应用与工作空间管理 -> 工作空间管理"
3. 选择一个标准库
4. 刷新分支，验证：
   - 符合格式的分支排在前面，可以选择
   - 不符合格式的分支排在后面，置灰
   - 每组内部按字母顺序排列
5. 手动输入分支，验证错误提示

### 单元测试（可选）

可参考 `test-branch-validation.js` 文件中的测试用例。

## 注意事项

1. **用户体验**：
   - 符合格式的分支优先展示，提升操作效率
   - 置灰而非隐藏分支，让用户看到所有可用的分支
   - 明确的错误提示，告知用户正确格式
   - 实时校验，不需要等到提交时才发现错误

2. **性能**：
   - 分支列表通常几十到几百个，校验和排序性能影响可忽略
   - 日期校验使用原生 `Date` 对象，性能良好

3. **兼容性**：
   - 向后兼容，不影响非标准库的行为
   - 不修改后端API，降低风险

## 提交信息

- Commit: `8aa5d650` - 优化分支列表排序：符合格式的排前面，不符合的排后面
- 文件: `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue`
