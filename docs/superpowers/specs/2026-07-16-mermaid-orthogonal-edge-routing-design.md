# Mermaid 正交避障与紧凑元数据设计

## 目标与边界

优化 `flowchart` / `graph` 可视化编辑器的自动布局：由 ELK 同时决定节点层级、端口方向和完整正交路径，避免“ELK 只排节点、Vue Flow 再独立画 SmoothStep”造成的交叉与穿越节点。对可平面化的常见分支、汇合和回退流程，验收目标为无非必要交叉；对拓扑本身不可平面化的图，只承诺最小化交叉。

同时把 Flowchart 的坐标、固定端口、边路由与 Sequence 的参与者坐标统一压缩为一个紧凑逻辑 envelope；短数据保持单行，长数据有限分行，减少私有编辑信息对 Markdown 和 AI 上下文的占用。Sequence 的消息布局和交互不变，只迁移参与者坐标的持久化格式。

本次只修改 `frontend/packages/editor`、对应稳定文档和测试；Markdown 保存链路、后端 API、事件、数据库、全局状态、安全配置和环境文件保持不变。

## 布局与视觉

采用已确认的 B 方案：连线为水平/垂直的正交轨道，转角使用 6px 小圆角。节点沿用现有主题、字体、形状和颜色。布局使用与画布一致的包围盒：普通节点宽度 120–190px、高度 52px，判断节点 150×88px，圆形节点 92×92px；层间距 96px、同层节点间距 64px、边—节点间距 28px、边—边间距 16px。

自动布局启用 ELK Layered 的 `ORTHOGONAL` 路由、layer sweep 交叉最小化、直线偏好和多余折点清理。ELK 返回的 `sections/startPoint/bendPoints/endPoint` 写入编辑模型。ELK 端点按节点边和沿边顺序稳定映射到现有 8/12 个 Handle；Handle 与 ELK section 之间插入正交接驳段。实际 DOM 尺寸与估算尺寸存在小偏差时，渲染层从存储路由中选取源端外侧的第一个安全引导点和目标端外侧的最后一个安全引导点，在远离节点的轨道上重接 Vue Flow 实际 Handle，并以 0.5px 容差校验首段沿 source Handle 方向离开、末段沿 target Handle 反方向进入。找不到安全内部轨道、路由不正交或方向校验失败时回退 SmoothStep，不能保留贴着节点边界再折返的短段，以免 `markerEnd` 箭头倒置。

`MermaidEdge` 增加派生字段 `route?: { points: MermaidPosition[] }`。拖动节点、修改节点名称或类型、切换方向、新增/删除节点或边、重连边及同步预布局都会清除旧路由；只修改边标签保留路由。ELK 失败或某条边缺 section 时不沿用旧 route，对应边回退 `getSmoothStepPath()`。

## 紧凑文本封装与二进制协议

私有状态只允许一个逻辑 envelope。完整 Base64URL payload 不超过 240 字符时固定为 `%%@<chunk>`；超过后按原顺序连续写成首行 `%%@<chunk>` 和若干续行 `%%@+<chunk>`。writer 的非末段固定 240 字符、末段 1–240 字符，reader 接受任意 1–240 字符分段并在下次保存时规范化；为兼容历史 writer，不含续行的旧 `%%@` 单行可超过 240 字符，但仍受总编码长度限制。所有 payload 禁止空格、冒号或 `=` padding。

多行必须物理相邻；多个首行、孤立/空白续行、被其他内容打断、单段超过 240 字符、分段缺失/重复/乱序或累计超限均视为冲突。最多接受 `ceil(maxEncodedChars / 240) = 5826` 个物理行，避免大量极短续段放大内存。没有非零坐标、合法固定端口或有效正交路由时不输出注释。

Base64URL 内部为版本化二进制：

1. 首字节 `0xA1`，高位标识紧凑格式，低位表示版本 1。
2. flags：bit0 坐标、bit1 端口、bit2 路由、bit3 Sequence；未知 bit 或图类型不匹配时拒绝。
3. entity count 与 Flow edge count 使用 unsigned LEB128；Sequence edge count 固定为 0。
4. 坐标乘 10 保留 0.1px，按实体顺序对 x/y 分别做前项增量，再经 ZigZag + unsigned LEB128 编码。
5. Flow 每条边的端口占 1 byte：低 nibble 为源端、高 nibble 为目标端；`0` 表示缺失，`1–6` 表示 `source-0..5`，`7–12` 表示 `target-0..5`，`13–15` 非法。
6. 开启路由 flag 后，每条边先写 point count。首点相对源节点坐标写两个 ZigZag LEB128；后续每段写 `(ZigZag(delta) * 2) + axis`，axis `0=x`、`1=y`。零长度、对角线、少于 2 点或超过 4096 点的路由不编码。
7. 末尾追加 little-endian FNV-1a 32-bit。校验输入依次为规范化拓扑签名的 UTF-8、一个 `0x00` 分隔字节和前述二进制 body。

Flow 拓扑签名固定为：

```ts
JSON.stringify(["flow", kind, direction, nodes.map(({ id, type, text }) => [id, type, text]), edges.map(({ source, target }) => [source, target])])
```

Sequence 拓扑签名固定为：

```ts
JSON.stringify(["sequence", participants.map(({ id, type, text }) => [id, type, text])])
```

边标签不进入签名，因此只改边标签可复用路由；方向、节点 ID、类型、文字、节点/边数量或边端点变化都会让 hash 失效。

## 兼容与失败安全

parser 继续读取旧 `%% editor-layout:` 和 `%% editor-edge-ports:`。没有新 marker 时，有效旧数据正常应用并在下次保存时转为紧凑格式；旧 writer 不再由 Flow/Sequence serializer 调用。尚未发布的展开式 `editor-edge-routes` 不作为兼容协议。新 reader 可读取历史短/长单行；旧 reader 会把 `%%@+` 当作重复 marker，因而只会保留多行 envelope 并回退旧格式，无法应用其中布局、端口或路由。滚动发布期间旧页面若无 legacy 回退会看到默认零坐标，但不会覆盖或吞掉私有注释。

唯一且有效的逻辑 envelope 优先于旧数据。损坏、重复、孤立或中断的 envelope 保留全部 marker 内容并回退旧格式；中断分段在重序列化时允许保留一个空行占位，防止原本不相邻的物理行被意外拼成有效数据。此时有效旧注释也继续保留，serializer 不写第二个 envelope。损坏旧注释无论是否存在有效新 envelope 都原样保留。解码先写入临时结果，只有 magic、flags、类型、数量、拓扑 hash、EOF、坐标、端口和全部路由同时有效才修改领域模型。

解码限制为：候选最多 1 MiB、累计编码字符不超过 `ceil(1 MiB × 4 / 3)`、多行最多 5826 段、单边最多 4096 个路由点、单个 LEB128 最多 5 bytes、绝对坐标不超过 10,000,000px，并拒绝非法 Base64URL、非最短 LEB128、未知端口值、截断和尾随数据。任何异常都不抛出到编辑流程，也不吞掉原 Markdown marker 内容。

代表图包含 7 个节点、9 条边和 40 个路由点：展开 JSON 约 5,175 字符、314 行，紧凑注释约 203 字符、1 行，减少约 96%。紧凑度优先于人工可读性和逐字段 Git diff。

## 测试与验收

- 固定 Flow 与 Sequence golden vector，锁定版本、字节顺序、hash 和确定性输出。
- 覆盖坐标、任意合法 Handle、正交路由 round trip、0.1px 精度、240 字符规范分段与短续段规范化，并验证 Flow/Sequence 多行注释均通过 Mermaid 官方 parser。
- 覆盖多个首行、孤立/空白/中断/超长续段、分段缺失/重复/乱序、累计字符和行数上限、截断、非法 Base64URL、五字节 LEB128、4097 路由点、非法端口/类型、超界坐标、尾随数据、hash 错误、拓扑变化及旧格式迁移/回退。
- 代表图断言私有注释只有一行且长度不超过展开 JSON 的 10%。
- 布局覆盖四种方向、真实判断节点尺寸、路由逐段正交、避开非端点节点、代表图无非必要 proper crossing、ELK 缺 section 与无 route 的 SmoothStep 回退。
- 交互覆盖路由 data 深拷贝、小圆角 path、路径长度中点标签、0.1–8px DOM 尺寸偏差、四种源/目标端切线方向、两点或不安全路由的 SmoothStep 回退，以及拖动、改形、改方向、增删、重连后的失效；边标签修改保留。
