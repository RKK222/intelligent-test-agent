# Mermaid SequenceDiagram 结构化编辑器设计

## 目标与范围

把现有仅支持 participant/actor 和平铺消息的 Sequence 可视化编辑器升级为结构化时序编辑器。Markdown 继续作为唯一事实源，外层 Mermaid 对话框、官方 parser 双重校验、当前 fence 并发保护以及 CodeEditor dirty/save/Git Diff 链路保持不变。

首批范围以 [Mermaid 11.16 Sequence Diagram 官方语法](https://mermaid.js.org/syntax/sequenceDiagram.html) 为准，支持 participant、actor、boundary、control、entity、database、collections、queue、别名、box 分组、10 种标准消息箭头、Note、注释、显式与快捷激活、create/destroy、autonumber、loop、alt/else、opt、par/and、critical/option、break、rect、自调用和嵌套调用。半箭头、中央连接、Actor 菜单、标题/无障碍指令、par_over、分号串联和未知参与者配置作为锁定源码局部无损保留。

## 领域与源码模型

Sequence 模型由参与者、参与者分组、自动编号设置和递归语句树组成。语句联合类型覆盖消息、Note、激活、注释、组合片段和锁定源码；alt/par/critical 使用显式 branches 表达 else/and/option，其他片段使用单一 body。消息保存精确 Mermaid 箭头、起止参与者、文字、快捷激活和绑定到消息的生命周期语义。

parser 以 tokenizer 和显式容器栈构造 AST，不依赖 Mermaid 私有 DB。每个可编辑实体记录原始源码、原父容器、缩进和语义指纹；未修改实体原样写回，修改、新建或跨容器移动的实体才按当前文档缩进规范生成。未知行或未知块以只读节点固定在原容器中，不能被编辑或跨越拖动。旧 Sequence 紧凑坐标 metadata 继续复用现有 codec。

所有模型修改通过纯命令层完成，统一处理深拷贝、参与者 ID 原子重命名、递归引用、端点重绑、语句移动、生命周期与激活校验、级联删除和错误原因。官方 Mermaid parser 在打开前和应用前继续校验；本地语义或官方语法失败时保留草稿与原 Markdown。

## 画布与交互

Sequence 使用专用时序场景，不再把消息当作普通图边。纯布局层计算参与者头、生命线、消息、自调用、激活条、Note、box、rect、生命周期和递归片段几何；Vue Flow 只承载一个确定尺寸的场景节点，并提供平移、缩放和适应视图。

右侧采用已确认的“元素 / 结构 / 属性”三标签：元素按参与者、时序元素、组合片段分组；结构以树表达分支和嵌套并支持键盘/拖拽排序；属性编辑当前选择。选中画布对象时自动进入属性。消息支持生命线快捷创建、端点重绑和双击文字编辑；参与者只做横向换序，不允许自由改变时间轴纵向位置。非法生命周期、激活或锁定边界落点立即拒绝并显示原因。

删除仍被引用的参与者时先展示影响数量，用户确认后原子清理递归引用并保留空分支。锁定源码只用纯文本展示，不执行链接或 HTML。

## 验证与边界

领域、命令、布局、组件、Markdown 集成和工作台 mock E2E 分层覆盖全部语法、任意嵌套、最小 Diff、旧 metadata、锁定语法、失败安全和现有 Flowchart 回归。实现不新增 API、事件、数据库、后端状态、运行时依赖或环境配置，也不改变 `@test-agent/editor` 公共导出。
