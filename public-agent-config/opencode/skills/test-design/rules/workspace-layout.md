# 应用工作区与 I/S 解析规则

## 标准层级

```text
<I需求项编号>-<需求项名称>/
├── 01-需求/
│   └── <S子条目编号>-<子条目名称>/
├── 02-设计/
│   └── <S子条目编号>-<子条目名称>/
├── 03-编码/
│   └── <S子条目编号>-<子条目名称>/
└── 04-测试/
    └── <S子条目编号>-<子条目名称>/
        ├── 041-测试设计/
        │   └── 测试设计文档/
        └── 042-测试执行/
```

`01-需求/` 允许存在 I 级公共需求材料；只在材料明确适用于当前 S 时读取。

## 编号识别

- 需求项目录以 `I` 或 `i` 开头；
- 子条目目录以 `S` 或 `s` 开头；
- 编号取目录名第一个 `-` 前的片段，并把首字母规范化为大写；
- 例如 `i2026000-需求项` 规范化为 `I2026000`，`s0001-子条目` 规范化为 `S0001`；
- 正式创建目录时使用大写 I/S；
- 跨阶段按规范化编号关联，不要求 `01-需求`、`02-设计`、`03-编码`、`04-测试` 下的中文名称完全一致。

同一阶段下出现多个相同规范化编号的目录时视为歧义，不得自行选择。

## workUnit

一个测试设计工作单元只绑定：

```yaml
requirementItemId: I...
subItemId: S...
```

用户明确要求处理多个 S 时，为每个 S 创建独立 `workUnit`，分别执行完整测试设计流程。不得把多个 S 的对象、证据、中间产物或案例写入同一文件。

## workspaceContext

编排阶段应解析并传递：

```yaml
workspaceRoot:
requirementItemDir:
requirementItemId:
requirementItemName:
subItemId:
canonicalSubItemDirName:
stageSubItemDirs:
  requirement:
  design:
  code:
  test:
requirementRoots: []
designRoot:
codeRoot:
testSubItemRoot:
designOutputRoot:
designDocumentRoot:
caseOutputRoot:
executionOutputRoot:
resolutionEvidence: []
resolutionStatus: RESOLVED | AMBIGUOUS | NOT_FOUND
```

默认路径：

```text
designOutputRoot    = <I>/04-测试/<S>/041-测试设计/
designDocumentRoot  = <I>/04-测试/<S>/041-测试设计/测试设计文档/
caseOutputRoot      = <I>/04-测试/<S>/041-测试设计/
executionOutputRoot = <I>/04-测试/<S>/042-测试执行/
```

`executionOutputRoot` 只作为工作区上下文传递；测试执行的具体读写规则以 `test-execution/rules/output-paths.md` 为准。

## I/S 解析优先级

1. 用户明确给出的 I/S 路径或编号；
2. 当前选中文件、右键目录、附件映射路径、`sourceFiles`、`sourceDirs` 的最近 I/S 祖先；
3. 已识别 I 下，在 `01-需求`、`02-设计`、`03-编码`、`04-测试` 中具有相同规范化 S 编号的目录；
4. 当前 I 下唯一的 S 候选。

多个输入指向不同 I 或不同 S 时，列出冲突并询问。只确定 I、但存在多个 S 且用户没有明确要求“全部处理”时，也必须询问。

## 04-测试下的 S 目录名

选择或创建 `04-测试/<S目录>/` 时按以下顺序确定目录名：

1. `04-测试` 下已有、编号匹配的唯一 S 目录；
2. 用户明确选中的 S 目录名；
3. `02-设计` 下编号匹配的 S 目录名；
4. `01-需求` 下编号匹配的 S 目录名；
5. `03-编码` 下编号匹配的 S 目录名。

不得凭空创造 S 名称。中文名称不同不影响编号关联，但应在 `resolutionEvidence` 中记录实际映射。

## 阶段 1 材料范围

默认只读取当前 workUnit：

- `01-需求/<S>/`；
- `01-需求/` 中有证据表明适用于当前 S 的 I 级公共材料；
- `02-设计/<S>/`；
- `03-编码/<S>/`：仅按需作为实现证据，不能覆盖需求或设计口径；
- 用户明确指定的其他材料。

只有用户明确要求增补已有案例、Review 既有设计或回归分析时，才读取当前 S 的 `<I>/04-测试/<S>/041-测试设计/`。

禁止：

- 搜索或混入其他 S 子条目；
- 把其他 S 的设计、代码、案例或执行结果作为当前事实；
- 把 `04-测试/<S>/042-测试执行/` 的执行结果改写成需求规则；
- 默认读取未编号的历史 `测试设计/`、`测试执行/` 目录。

## 目录创建

当 I 和 S 已唯一确认且 `writeAllowed=true` 时，可以创建缺失的：

```text
<I>/04-测试/<S>/041-测试设计/
<I>/04-测试/<S>/041-测试设计/测试设计文档/
```

无法确认 I/S 时返回 `INCOMPLETE`，不得在工作区根目录创建 `041-测试设计/`，也不得改写到其他候选 S。
