---
name: sync-integrated-api-request-structure
description: 当接口自动化脚本生成后，或用户指定需求子条目/042 文件并给出具体接口英文名，查询一体化平台的 reqParamStruct，同步 042-测试执行中的请求报文结构；没有明确接口英文名或目标文件不唯一时不使用。
metadata:
  display-name: Integrated API Request Structure Sync
  display-name-zh: 一体化平台接口报文结构同步
  agent-id: test-execution-api
  version: 1.0.0
  emoji: "🔄"
---

# 一体化平台接口报文结构同步

把一体化平台返回的 `interfaceInfo.reqParamStruct` 作为请求报文的最终字段、层级和容器类型白名单，更新当前需求子条目 `042-测试执行` 内的目标接口自动化脚本。

## 何时使用

仅在同时满足以下条件时使用：

1. 用户正在生成或调整接口自动化脚本/请求报文；
2. 当前上下文来自“增强接口自动化脚本生成”流程，或用户明确引用了一个需求子条目、`042-测试执行` 目录或其中的文件；
3. 用户给出具体接口英文名，例如“请按一体化平台 `Moegglobalpaysubmit` 接口调整报文结构”；
4. 用户要求按一体化平台结构调整请求报文。

没有具体接口英文名、只有中文接口名、只有接口编号，或目标需求子条目/文件无法唯一确定时，返回 `INCOMPLETE` 并列出缺口；不得猜测、模糊匹配、批量修改整个 `042-测试执行`。

## 不可变约束

- 应用名和月度版本只能从当前应用工作空间的物理或逻辑路径解析，不能从用户描述、案例正文或示例值补造。
- 固定使用本 Skill 查询脚本内置的一体化平台接口；不得接受对话传入的替代 URL。
- 正式文件只能位于已解析需求子条目的 `04-测试/<子条目>/042-测试执行/`。
- 一体化平台原始响应、查询入参、内部 URL、临时 JSON 和结构校验明细不得写入正式脚本。
- 查询失败、返回缺少非空 `interfaceInfo.reqParamStruct`、路径解析失败或目标不唯一时，不修改任何文件。
- 不改变请求字段之外的案例信息、接口信息、数据准备和断言。

## 目标文件解析

按以下优先级确定一个目标 Markdown：

1. 用户明确指定且位于当前 `042-测试执行` 的文件；
2. 增强生成流程当前案例的 `generatedFiles` 或 `resolvedOutputTarget`；
3. 当前需求子条目的 `042-测试执行` 中，接口表格的“接口英文名”“英文名”或“接口名称”值与用户给出的英文名**精确相等**的唯一 Markdown。

第 3 种情况使用：

```text
python -B <本Skill目录>/scripts/locate_request_markdown.py \
  --execution-root <042-测试执行目录> \
  --interface-en-name <接口英文名>
```

脚本无匹配或返回多个候选时停止，不用正文子串或文件名猜测。

## 查询接口结构

调用：

```text
python -B <本Skill目录>/scripts/query_interface_contract.py \
  --workspace-root <应用工作空间根目录或其下任一路径> \
  --interface-en-name <接口英文名>
```

脚本从路径中解析：

- 应用名：名为 `workspace` 的路径段前一个目录，例如 `F-BASE/workspace` 得到 `F-BASE`；
- 月度版本：路径中的唯一合法 `YYYYMMDD` 日期段转为 `YYYY年M月`；同一日期重复可接受，不同日期并存视为歧义。

查询脚本只向 stdout 输出必要的结构化结果。不要把完整响应写入工作空间。

## 报文结构规则

- `reqParamStruct` 只有一个带 `children` 的包装根节点时，从该节点的 `children` 生成最终请求；包装节点名称本身不进入请求报文。
- 兄弟节点顺序按 `reqParamStruct` 保持确定性。
- 删除不在结构中的额外字段；补齐缺失字段；同一精确字段路径下已有的非 `null` 叶子值原样保留。
- 缺失或 `null` 叶子使用空字符串 `""`，正式请求中不得保留 `null`。
- Map/DTO/Object 节点生成 JSON 对象；List/Collection/数组节点生成 JSON 数组。
- 带 `children` 的列表缺失或为空时，生成一个按 `children` 补齐的结构元素，以满足增强流程的完整结构校验；已有列表元素逐个规范化。
- 只同步结构，不根据 `minLen/maxLen/desc` 编造业务值。

规范化调用：

```text
<包含 payload 和 reqParamStruct 的 JSON> | python -B \
  <本Skill目录>/scripts/normalize_request_payload.py --input -
```

只有输出 `passed=true` 后才能进入写回。

## 两种写回模式

### 模式 A：增强确定性渲染流程

如果调用方仍持有生成阶段的 `values`、renderer 和 manifest：

1. 用查询结果规范化 `values.requestPayload`；
2. 使用原 Skill 的 renderer 重新渲染整份脚本；
3. 使用原 Skill 的 verifier 重新生成/校验 manifest；
4. 再调用 `validate-automation-script-format`。

禁止直接编辑正式 Markdown 绕过原渲染器或复用旧 manifest。

### 模式 B：既有 042 文件

如果当前生成流程没有确定性 renderer/manifest，或用户明确要求调整既有文件，调用一体化脚本：

```text
python -B <本Skill目录>/scripts/sync_request_structure.py \
  --workspace-root <应用工作空间根目录或其下任一路径> \
  --interface-en-name <接口英文名> \
  --target-file <唯一目标Markdown>
```

也可在未指定文件时把最后一个参数改为 `--execution-root <042-测试执行目录>`，由脚本执行精确唯一匹配。该脚本在内存中完成查询、规范化和二次校验，只原子替换“## 3. 请求报文”下唯一 JSON 代码块。

写回后重新读取正式文件，确认：

- 请求 JSON 可解析；
- 结构复核 `passed=true`；
- 文件仍位于同一个 `042-测试执行`；
- `validate-automation-script-format` 通过。

## 输出

返回：

- `stageStatus`: `COMPLETED` 或 `INCOMPLETE`；
- `interfaceEnName`、从路径解析的 `appName/version`；
- `resolvedOutputTarget`、实际更新的 `generatedFiles`；
- 是否展开包装根、删除/补齐了哪些字段路径、是否修复容器或 `null`；
- `skillUsage` 中记录本 Skill 和后续格式校验 Skill。

不要在最终回复或正式文件中展示一体化平台原始响应、内部接口地址或完整结构树。
