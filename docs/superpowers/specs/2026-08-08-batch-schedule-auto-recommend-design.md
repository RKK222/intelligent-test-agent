# 批量定时执行智能推荐时间段设计

## 背景

批量生成子条目测试案例弹层在打开定时选择后，夜间时段默认全部未选，用户需要自行判断哪些时段容量足够并逐个勾选。当一批选择较多子条目时，单时段容量可能不足以覆盖，用户难以及时感知应选择几个时段、哪些时段排队最少，容易反复试错或触发容量不足提示。

夜间时段接口已经返回每个时段的 `reservedCount`（排队任务数）与 `capacity`，单任务场景也已有“未满且待执行任务最少”的系统推荐。批量场景缺少据此自动推荐并选中的能力。

## 目标

- 打开定时选择或所选子条目数量变化时，前端根据**所选子条目数量**与各夜间时段的**排队任务数**，自动推荐能够覆盖总数的时间段并选中。
- 在“定时执行”按钮上方显示“已根据选择的子条目数量智能推荐定时执行时间段”，明确当前选择为系统推荐结果。
- 用户手动改选后保留其选择，不再覆盖；关闭定时选择或重新打开后按最新子条目数重新推荐。
- 不修改 HTTP API、RunEvent、数据库、后端容量分配或单任务夜间执行链路；不改变批量创建进度页、重试与幂等身份。

## 推荐算法

新增纯函数 `recommendBatchScheduleTimes({ itemCount, slots })`，返回推荐的 `slotStart` 数组：

1. 过滤掉已满或不可用时段，仅保留 `available && capacity > reservedCount` 的时段。
2. 按已排队数 `reservedCount` 升序、再按 `slotStart` 升序排序。该顺序与单任务系统推荐一致；统一容量下“已排队最少”即“剩余余量最多”，因此自然用最少时段覆盖全部子条目。
3. 依次累加 `capacity - reservedCount`，直到覆盖 `itemCount`。
4. 返回按时间升序排序的 `slotStart`。
5. 全部时段总余量仍不足以覆盖时返回全部可用时段，交由既有 `allocateBatchSchedule` 与容量提示兜底。

`itemCount <= 0` 或没有可用时段时返回空数组。

## 交互设计

### 自动推荐触发

- 打开定时选择（`openSchedule`）：立即按当前所选子条目数与已加载时段推荐并选中。时段尚未加载时先选中空集，时段数据到达后由监听器在仍为自动推荐状态时重新推荐。
- 切回夜间时段模式（`setScheduleMode("NIGHT_WINDOW")`）：按当前子条目数重新推荐。
- 所选子条目数量变化、夜间时段数据刷新：若当前仍处于自动推荐状态，则重新推荐；用户已手动改选则保留其选择不覆盖。

### 自动推荐状态

- 新增 `scheduleAutoRecommended` 标记当前夜间选择是否由系统自动推荐。
- `applyRecommendedSchedule()` 在夜间模式下把 `selectedNightTimes` 设为推荐值并置标记为 `true`。
- 用户手动 `toggleNightTime` 改选后置 `false`，后续数量或时段变化不再覆盖。
- 关闭定时选择（`closeSchedule`）、重置弹层（`resetDialogState`）、切换到测试时间模式后置 `false`。

### 提示与按钮

- “定时执行”按钮仍只在 `selectedScheduleTimes.length > 0 && scheduleAllocation.ok` 时出现，即推荐必须能覆盖所选子条目数量才允许提交。
- 按钮上方显示“已根据选择的子条目数量智能推荐定时执行时间段”，条件为当前处于夜间模式且仍为自动推荐。
- 推荐无法覆盖全部子条目（总余量不足）时，按钮不出现，定时选择区域继续展示既有“所选时段总余量 N，不足以安排 M 个子条目”容量提示；此时不展示推荐提示，避免暗示可提交。
- 用户手动改选后提示消失，即便选择仍可提交也不再显示推荐文案。

### 范围边界

- 自动推荐只作用于首次提交前的定时选择区域，且仅在夜间模式下生效。测试时间模式（`ADMIN_CUSTOM`）由超级管理员手工选择精确分钟，不参与自动推荐。
- 进度页容量冲突重选区域沿用现状，不自动推荐，避免与重试幂等身份和原时间分配冲突。
- 关闭定时选择仍清空已选夜间时间、自定义时间与校验提示，重新打开按最新子条目数重新推荐。

## 状态与实现边界

改动集中在：

- `batch-test-case-generation.ts`：新增 `recommendBatchScheduleTimes` 纯函数，不修改 `allocateBatchSchedule` 等既有导出。
- `BatchTestCaseGenerationDialog.vue`：
  - 新增 `scheduleAutoRecommended` 与 `recommendedNightTimes`；
  - `openSchedule`、`setScheduleMode("NIGHT_WINDOW")` 调用 `applyRecommendedSchedule`；
  - 监听所选子条目数量与 `nightSlots` 变化，在仍为自动推荐时重新推荐；
  - `toggleNightTime`、`closeSchedule`、`resetDialogState` 与切换到测试时间模式清空自动推荐标记；
  - “定时执行”按钮外包一层纵向容器，按钮上方按条件显示推荐提示。

不修改 `ExecutionTimePicker.vue`、`FigmaChatPanel.vue`、`AgentWorkbench.vue`、编排 composable 或后端接口。

## 测试设计

- `batch-test-case-generation.test.ts` 覆盖 `recommendBatchScheduleTimes`：单时段可覆盖时只推荐该时段；需多时段时按已排队数升序补齐并按时间升序返回；总余量不足时返回全部可用时段；已满/不可用时段被忽略；无子条目或无时段返回空。
- `BatchTestCaseGenerationDialog.test.ts` 覆盖：打开定时后自动选中可覆盖时段并显示推荐提示；关闭后恢复立即执行入口，重新打开按当前子条目数重新推荐；手动改选后提示消失且仍可提交；总余量不足时按钮隐藏并展示容量提示；多时段自动推荐并禁用运行中关闭。
- `workbench.spec.ts` 批量定时 E2E：打开定时后断言推荐提示与定时执行按钮可见，直接提交，校验夜间任务按推荐时段创建。

## 兼容性与范围

本次只改变批量弹层首次提交前的夜间时段推荐与选中行为，不修改 HTTP API、RunEvent、数据库/Flyway、后端、权限、安全、性能路径、环境配置、generated SDK 或 OpenCode 源码。已有单任务夜间执行、批量立即执行、批量创建进度页、重试与幂等身份保持兼容。
