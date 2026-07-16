# 包说明：@test-agent/editor/src

## 职责

封装 Monaco 文件编辑体验，并向 app 层暴露 prompt 选区上下文所需的受控信息。

## 主要程序清单

- `CodeEditor.vue`：Monaco 编辑器默认 `wordWrap=on` 按中间区域可视宽度自动换行，并提供保存反馈、只读/脏状态展示、无文件极简空态和当前选区上报回调；无文件时提供通用 `empty-actions` slot，由 app 层注入主页操作，editor 包不依赖手册等具体业务。Markdown 文件的预览已改为 Monaco + markdown-it 模式（Monaco 组件负责编辑，markdown-it 负责分屏渲染），组件本身只接受 `showPreview` 和 `previewMode` 受控 prop 并在下方追加 `MarkdownPreview` 分屏。具备 `ResizeObserver` 布局监听与分屏/切换文件时的 `editor.layout()` 布局重计算，并在源码宿主有实际宽高时显式传入尺寸；Monaco 按需加载期间会丢弃过期文件模型，防止预览分屏或容器尺寸变更时 Monaco DOM 坍塌白屏；在非只读文件下通过 `editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyS, …)` 拦截浏览器的「保存网页」行为，向调用方 emit `save`；调用方再按脏文件 / livePreview / 已保存中等条件决定是否真正落盘。
- `CodeEditor.vue` 的受控内容 watcher 只在当前 Monaco model URI 与 `path` 一致时写入 `content`；路径和正文同 tick 更新或 Monaco 异步加载时，旧模型保持原文件内容。
- `MarkdownPreview.vue`：Markdown 安全渲染、Mermaid 脚本/图表切换和可视化编辑流程编排；只定位当前 fence、调用官方 parser、懒加载编辑对话框，并将完整 Markdown 通过 `change` 交回 `CodeEditor`；渲染依赖失败时展示原文纯文本，避免已读取的文件内容变成空白。
- `mermaid/model.ts`、`node-shapes.ts`、`parser.ts`、`serializer.ts`、`layout.ts`：Flowchart/graph 的 14 类节点目录、强类型模型、旧/现代语法双向转换、unknown line 保留，以及与画布共用节点尺寸的 ELK 端口/正交边路由布局。可视化结果统一写为现代 `ID@{ shape: <短名>, label: "<文本>" }`；派生路由随模型深拷贝，几何或拓扑变化必须清除，边标签变化可保留。
- `mermaid/compact-metadata.ts`：Flowchart 节点坐标/端口/路由与 Sequence 参与者坐标的统一紧凑 codec。一个逻辑 envelope 以 `%%@<chunk>` 开始，超过 240 个 Base64URL payload 字符时使用紧邻的 `%%@+<chunk>` 续行；writer 固定按 240 分段，reader 接受较短分段并兼容历史超长单行。内部版本/flags、unsigned LEB128、ZigZag、端口 nibble、正交轴向增量和 little-endian FNV-1a 32-bit 不变；解码绑定规范化拓扑签名，并限制 1 MiB、最多 5826 个物理行、单边 4096 点和单个 LEB128 5 字节。`metadata.ts`、`edge-port-metadata.ts` 只承担旧格式读取迁移；损坏、重复、孤立或中断的新 marker 必须保留并阻止写入第二个 marker。
- `mermaid/sequence/`：Sequence diagram 的 participant/actor、有序 message 模型、parser、serializer、布局和 Vue Flow 适配；复杂控制语句暂不编辑但必须保留，参与者坐标与 Flow 私有信息共用紧凑 codec。
- `mermaid/visual-editor/`：通用可视化对话框和 Flowchart Vue Flow 画布；右侧节点图形库按“流程图 / 文档与显示”展示 14 类共享 SVG 轮廓，支持拖放、点击创建和双列“图标＋名称”快捷建连。快捷菜单 Teleport 到 `body` 后按屏幕坐标定位，不随画布缩放，并在视口边缘自动翻转或夹取。各轮廓按目录使用 8/12 Handle；换形时端口迁移到新轮廓最近位置并保持自环两端不同。独立拖线控制器使用固定屏幕半径处理起线、目标激活、近点吸附、SmoothStep 临时路径和窗口级取消生命周期，Vue Flow 原生 connect 关闭。自动布局边优先按 ELK 路由绘制 6px 圆角正交 path；实际 DOM 尺寸与 ELK 估算存在微小差异时，`edge-path.ts` 丢弃贴边折返段，在节点外安全轨道重接实际 Handle，并校验源端向外、目标端向内。没有安全内部轨道、路由非法或只有两个端点时回退 SmoothStep。Sequence 画布位于 `mermaid/sequence/visual-editor/`，消息使用按顺序错层的自定义边避免重叠。
- `monaco-env.ts`：Monaco Web Worker 配置（懒加载）。
- `language.ts`：路径到 Monaco language 映射。

## 允许依赖

- Vue 3。
- `monaco-editor`（原生）。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。
- `@vue-flow/core`（仅 Mermaid 可视化编辑异步 chunk 使用）。

## 禁止依赖

- backend-api、event-stream-client、opencode server。
- Run 启动、Diff 落盘动作或 `PromptPart` 提交。

## 修改时必须同步更新

- 本包 README 和测试。
