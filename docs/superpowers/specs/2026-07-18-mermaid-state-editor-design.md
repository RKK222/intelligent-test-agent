# Mermaid State Diagram 可视化编辑器设计

## 目标与范围

在现有 Mermaid 编辑器中增加一等 State Diagram 编辑能力，支持 `stateDiagram` 与 `stateDiagram-v2`，并保留原始头部。Markdown 继续作为唯一事实源，外层 Mermaid 对话框、官方 parser 双重校验、当前 fence 并发保护以及 CodeEditor dirty/save/Git Diff 链路保持不变。

首批范围以 Mermaid 11.16.0 State Diagram 语法为准，支持开始/结束、普通状态、状态说明、自循环、带标签转换、复合与嵌套状态、Choice、Fork/Join、并发区域、Note、各层级方向设置和限定范围的直接样式。注释、`classDef`/`class`、可访问性等不影响拓扑的指令原样保留；不能安全映射的结构语法只允许源码编辑。

## 复用边界

Markdown 围栏定位与替换、`expectedSource` 并发保护、Mermaid/ELK 懒加载、官方语法校验、对话框外壳和既有保存链直接复用。Vue Flow 画布壳、屏幕坐标连线拖拽、端口命中、端点重连、行内标签编辑、颜色控件和路径绘制抽象为由领域规则驱动的共享交互内核，Flowchart 保持原有规则。

State 不扩展扁平 `MermaidGraph`。Flow 的扁平 parser/serializer、节点形状目录、同源同目标边禁止规则、缩放语义、`linkStyle` 和扁平 ELK 布局都不复用。State 使用独立树形领域、递归转换、层级布局、伪状态约束和紧凑元数据。

## 领域与源码模型

`MermaidStateDiagram` 持有原始头部和根 `StateScope`。Scope 包含方向和一个或多个 `StateRegion`；复合状态作为父 Region 中的节点持有子 Scope。节点联合类型覆盖普通/复合状态、开始、结束、Choice、Fork 和 Join；转换、Note 与原样保留指令分别建模。

命名状态 ID 全图唯一。转换只能连接同一 Region 内的节点；父层可以连接复合状态本身，但不能直接连接其内部节点，也不能跨 Scope 或跨并发 Region。多个同源同目标的带标签转换合法。`[*]` 使用按 Scope、Region 和出现顺序生成的稳定内部 ID；Mermaid 11.16.0 每次出现 `[*]` 都会创建独立伪状态，因此开始节点必须零入边、恰好一条出边，结束节点必须恰好一条入边、零出边，多终止分支使用多个结束节点。

parser 先通过官方 `mermaid.parse`，再以 tokenizer、显式 Scope 栈和 Region 分隔构造模型，不依赖 Mermaid 私有 StateDB。支持状态别名与说明、递归 `{}`、`--`、方向、转换标签、Note、伪状态声明和直接样式。原样指令绑定所属 Scope；若未知语法可能改变拓扑或容器边界，则整图降级为源码模式。

serializer 先执行领域校验，再规范化输出 State 源码，并交给官方 parser 复验。仅当全部成功且当前围栏仍匹配 `expectedSource` 时替换 Markdown。失败保留草稿、原源码和可操作错误信息。

State 使用独立版本的紧凑元数据，沿用既有 `%%@` 封装，记录各 Scope/Region 的局部节点坐标和转换端口。拓扑指纹包含层级、Region、节点与转换顺序；当前聚焦路径不持久化。损坏、重复或未知标记原样保留并回退自动布局，不生成第二个冲突标记。

## 画布与交互

根画布采用“概览 + 聚焦”：复合状态显示内部摘要，双击进入内部 Scope，面包屑返回上层。聚焦 Scope 同时显示全部并发 Region；`TB/BT` 时 Region 横向排列，`LR/RL` 时纵向排列。层级 ELK 先分别布局各 Region，再按方向交叉轴打包；Fork/Join 随方向旋转，复合状态按摘要与内容自动计算尺寸。

元素面板分为普通/复合状态、开始/结束/Choice/Fork/Join 和 Note。支持点击或拖放创建、节点拖动、转换创建与重连、并行转换、自循环和转换标签行内编辑。普通状态可编辑名称和多行说明；Note 仅依附普通或复合状态，支持左/右位置与多行正文。

连接阶段立即拒绝必然非法的作用域、Region、端点方向和最大基数；草稿可暂时缺少必需连接。应用时严格检查：Choice/Fork 恰好一条入边且至少两条出边，Join 至少两条入边且恰好一条出边，自循环仅允许普通/复合状态，每个并发 Region 非空，Note 目标和全部引用存在。

普通/复合状态可编辑文字、填充和边框色；Choice/Fork/Join 可编辑填充和边框色；开始/结束、Note、转换线和标签不开放颜色编辑。只消费精确 `#RGB/#RRGGBB` 的直接样式并规范化为大写 `#RRGGBB`，复杂 CSS 与类样式原样保留，用户编辑后的直接样式最后输出以明确优先级。

## 验证与边界

领域、parser/serializer、校验、元数据、布局、组件、Markdown 集成和 Workbench mock E2E 分层覆盖全部支持语法、任意嵌套、并发、失败安全以及 Flowchart/Sequence 回归。实现不新增 API、事件、数据库、后端状态、运行时依赖、环境配置或 `@test-agent/editor` 公共导出；继续兼容 Chrome 108，并保持 Mermaid/ELK 懒加载和 DOMPurify 安全边界。
