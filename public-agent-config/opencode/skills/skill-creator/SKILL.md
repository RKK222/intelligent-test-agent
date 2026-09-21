---
name: skill-creator
description: Skill Creator（技能创建）。从零创建可复用、可验证、可移植的 Agent Skill。只要用户提到新建技能、把流程沉淀为技能、编写 SKILL.md、制作公共或企业技能、封装规则模板、准备技能导入包，就使用本技能；如果目标是审查或改进已有技能，改用 skill-optimizer。
compatibility: opencode
metadata:
  display-name: Skill Creator
  display-name-zh: 技能创建
  source: test-agent
  version: '1.2.1'
  emoji: 🛠️
---

# 技能创建

把用户的工作方法沉淀为边界清楚、可重复执行、可离线导入的技能包。先在当前用户拥有的个人 worktree 中创建草稿，再引导用户通过平台的 Agent 配置发布链路发布；不能只给概念说明或未落盘的示例。

## 职责边界

- 本技能负责创建新技能；已有技能的诊断、对比和迭代由 `skill-optimizer` 负责。
- 默认只创建 skill，不新增 Agent 入口、运行时配置、密钥或环境文件；用户明确要求且目标平台确有需要时再扩展。
- 优先复用现有技能、脚本、规则和模板。只有现有能力无法表达目标时才新增资源，并说明原因。
- 不承诺未实际运行的测试、导入或线上效果。
- 不把当前已安装技能目录、OpenCode 运行配置目录或公共共享运行副本当成创作工作区。即使环境变量、当前目录或对话上下文指向这些目录，也不得直接写入。

## 创建流程

### 1. 确认目标和落点

先从对话、需求文件和现有目录中提取以下信息，已明确的内容不要重复询问：

1. 技能要完成的任务和明确不做的事项；
2. 用户可见的中文名称，以及可选的英文展示名称；
3. 应触发、不得触发以及容易混淆的用户表达；
4. 输入、输出格式和成功标准；
5. 可用工具、依赖、目标运行时和离线约束；
6. 安全、权限、兼容性和敏感数据边界；
7. 可用于验证的真实示例。

关键信息缺失且不同选择会显著改变产物时再提问；否则采用最小、可逆的合理假设，并在交付说明中写明。

写任何文件前必须先确定平台当前打开的、属于当前用户的个人 worktree 根目录，并执行以下落点门禁。这里要区分平台的 `Working directory` 与 UI 中的 `Workspace root folder`：前者是应用输出根，后者通常是 Git 根目录，二者不能互相替代。

1. 普通应用个人 worktree 的草稿写入 `<working-directory>/.opencode/skills/<skill-name>/`；
2. 平台已经明确打开“公共 Agent”个人 worktree，且编辑根本身就是该个人配置仓库的 `opencode/` 根时，写入 `<public-config-root>/skills/<skill-name>/`；
3. 仅从 `PWD`、`OPENCODE_CONFIG_DIR`、`~/.config/opencode`、本技能安装路径、进程配置路径或 Git 根目录推断出的目录一律不算个人 worktree；
4. `/data/**/.config/opencode`、`.testagent/**/.config/opencode` 以及任何被平台标记为共享运行副本、只读配置根或技能安装目录的目标一律禁止写入；
5. 无法证明目标目录的用户归属和 worktree 身份时立即停止落盘，说明需要用户在平台打开个人工作区或“公共 Agent”个人 worktree 后重试，不得回退写共享目录。

用户要求“公共技能”但当前只打开普通应用个人 worktree 时，仍先在该个人 worktree 创建可审阅草稿。校验通过后，明确引导用户进入平台公共 Agent 编辑区导入草稿、查看 Diff、提交并发布；未经明确授权不得自行推送远端或绕过平台发布。

### 2. 先检索再设计

在目标技能目录、公共技能区和项目文档中按领域词、行为、输出名、工具名及近义词检索。按以下顺序决策：

1. 直接复用已有技能；
2. 扩展职责相符的已有技能；
3. 抽取确有多个调用方需要的公共资源；
4. 确认没有合适实现后创建新技能。

如果发现同名或高度重叠技能，停止创建重复入口，改为说明复用或交给 `skill-optimizer` 合并。

### 3. 设计技能包

实际读取 `references/skill-spec.md`，再按任务需要选择最小目录：

```text
<skill-name>/
├── SKILL.md
├── rules/          # 需要强约束或按场景加载时使用
├── references/     # 背景资料、协议、长说明
├── templates/      # 固定输出骨架
├── scripts/        # 确定性、重复性操作
└── evals/          # 测试提示和预期结果
```

不要为了显得完整而创建空目录、空 README 或占位文件。技能需要模板时读取 `templates/SKILL.template.md`，按实际内容替换所有占位符。

### 4. 编写触发描述

`description` 是触发入口，必须同时说明“做什么”和“何时使用”。覆盖：

- 用户可能使用的正式说法、口语、缩写和隐含意图；
- 与相邻技能的清晰边界；
- 重要文件类型、任务上下文或输出类型。

描述不要堆砌无关关键词，也不要把只应存在于正文的完整流程塞进 frontmatter。物理目录与顶层 `name` 使用稳定英文 kebab-case 并完全一致；用户只给中文名称时，用完整无声调拼音生成技术 ID。`description` 首句统一为 `English（中文）。`，`metadata.display-name/display-name-zh` 保存双语展示名，`metadata.source` 写 `test-agent`。OpenCode 继续按英文目录和顶层 `name` 识别。

### 5. 编写执行指令

- 使用祈使句，解释关键规则背后的原因，让执行者能处理未见过的输入。
- 先给主流程，再按需指向 rules、references、templates 或 scripts，避免一次加载全部材料。
- 明确输入检查、失败分支、停止条件、输出格式和验证证据。
- 对可能改写、删除、发布或发送数据的操作设置确认和目标核对。
- 脚本处理确定性工作，模型负责判断、归纳和沟通；不要让模型重复手工实现脚本已覆盖的逻辑。
- 默认面向企业离线环境，不依赖公网服务、个人目录、未声明 CLI、账号或私有绝对路径。

### 6. 建立验证样例

在 `evals/evals.json` 中至少写 3 个真实任务：

1. 一个典型成功场景；
2. 一个缺少输入或存在边界条件的场景；
3. 一个与相邻技能容易混淆的场景。

每项包含 `id`、`prompt`、`expected_output` 和 `files`。预期结果描述可观察行为，不把实现细节当成目标。可客观判断时补自动校验；涉及表达质量或业务判断时保留人工评审。

### 7. 校验和打包

从技能目录执行：

```bash
python3 scripts/validate_skill.py .
```

验证落点时显式传入目标上下文，两个参数互斥，不能依赖当前 `PWD`、配置目录或安装目录自动推断：

```bash
python3 scripts/validate_skill.py <skill-dir> --workspace-root <working-directory>
python3 scripts/validate_skill.py <skill-dir> --public-config-root <public-config-root>
```

`--workspace-root` 只接受 `<working-directory>/.opencode/skills/<skill-name>/`，`--public-config-root` 只接受公共配置 `opencode/` 根下的 `skills/<skill-name>/`。这两个参数都用于验证路径归属，不会创建目录，也不会写入共享运行副本。

如果目标技能没有校验脚本，可使用本技能的 `scripts/validate_skill.py <目标技能目录>`。随后检查：

- frontmatter、英文技术 ID、双语展示名、来源、目录和引用路径；
- 是否残留占位符、空资源或无效示例；
- 测试样例 JSON 是否可解析；
- 是否意外包含依赖目录、密钥、证书、缓存或个人文件；
- 在目标运行时中是否能被发现和加载。
- 最终文件全部位于本次确认的个人 worktree 内，`git status --short` 能在该 worktree 的 Diff 中看到；共享运行副本和已安装技能目录没有变化。

只有实际运行过的检查才能写为“通过”。目标平台不可用时，明确标记未完成的运行时验证。

## 交付格式

最终交付保持简洁并包含：

1. 技能名称、用途和触发边界；
2. 新增或复用的文件；
3. 实际执行的验证命令及结果；
4. 导入方式和目标目录；
5. 当前个人 worktree 的草稿路径，以及通过平台 Diff、提交和发布的步骤；
6. 未验证项、兼容性风险或后续优化建议。

不得把草稿、占位模板或仅通过目视检查的结果描述为可交付版本。
