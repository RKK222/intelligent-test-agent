# 本地 OpenCode 客户端

本地客户端把你自己电脑上的目录注册为平台工作区：Agent 在本机直接读写文件，浏览器照常只访问平台网页。它内置了运行所需的 OpenCode 1.18.4 和 JDK，安装只写入当前账号的 `~/.local` 和 `~/.config`，不需要 sudo，也不需要安装系统 Java。当前只提供麒麟 ARM64（glibc）普通用户包；macOS、Windows 和非 glibc 系统暂不在支持范围。

客户端可以聊天（含聊天附件）、使用 OpenCode Agent 自带工具、管理本地文件和执行夜间任务；不提供浏览器终端、Git 发布、Agent 配置管理和协作分享。

## 使用前确认两件事

1. **账号已开通客户端灰度。** 客户端按账号灰度开放：只有超级管理员在“系统管理 → 用户管理”为当前账号打开“客户端灰度”后，网页才会显示下载入口、本地实例状态、本地工作区和“个人设置 → 本地 OpenCode 客户端”。没有看到这些入口时，先联系平台超级管理员，说明见[设置与权限内操作](./settings.md)。客户端灰度与记忆灰度相互独立，打开客户端灰度不会同时开通记忆。
2. **本机是麒麟 ARM64 桌面系统。** 想使用内置浏览器 Tool 时，桌面还需要已安装企业 360 浏览器；首次使用前可在托盘里做一次“浏览器设置与自检”。

## 第一步：准备 Client key

打开“设置 → 个人设置”，在“本地 OpenCode 客户端”区域找到 **Client key** 卡片：

![个人设置中的本地 OpenCode 客户端区域，显示 Client key 掩码和在线实例状态](./images/operations/settings-local-client-key.png)

- 还没有 key 时点击“创建 Client key”；已有 key 时点击“显示 Client key”。
- 明文只显示这一次，立即复制并暂时保存在本机安全位置；页面会提示“Client key 已复制，请立即粘贴到客户端配置”。
- Client key 只用于客户端接入认证，不要发送给任何人，也不要放进命令、截图或聊天消息。泄露或怀疑泄露时，在网页上“轮换 Client key”（所有设备需要用新 key 重新接入）或“撤销 Client key”（所有客户端立即断开）。

## 第二步：下载并安装

1. 仍在“个人设置 → 本地 OpenCode 客户端”，点击**下载最新客户端包**，得到 `TestAgent-Local-Client-Kylin-arm64.tar.gz`。
2. 把压缩包**完整解压**到一个本地目录。不要在压缩包预览窗口里直接运行里面的文件。
3. 打开解压出的 `TestAgent-Local-Client/` 目录，双击 `TestAgent-Local-Client`。文件管理器禁止双击可执行文件时，在该目录打开终端执行：

   ```bash
   ./TestAgent-Local-Client
   ```

4. 首次安装会弹出可见终端并校验安装包签名，随后依次提示：

   ```text
   统一认证号: （输入你的统一认证账号）
   Client key: （粘贴第一步复制的 key，输入内容不会回显）
   ```

5. 等待安装完成，终端显示“麒麟 ARM64 本地客户端已安装并接入”。

成功的判断标准：系统托盘出现 TestAgent 图标且状态为“在线”；网页“个人设置”的实例列表出现一个在线实例，能看到平台、架构和当前版本。安装只写当前账号目录，不修改系统目录；再次双击新版安装包就是升级，会保留凭据和已注册的工作区。

## 第三步：注册一个本地工作区

客户端在线后，点击托盘图标打开菜单，选择**选择并注册工作区…**，在弹出的目录选择器中选中你要交给 Agent 使用的本机目录：

![选择本地目录对话框中浏览并列出候选目录](./images/operations/settings-local-workspace-picker.png)

注册成功会自动打开对应工作台，并提示“本地工作区已注册，原目录内容未被复制或修改”。注册只是建立平台映射，不会复制、移动或上传你的目录。

## 托盘菜单怎么用

托盘图标就是客户端的主入口，菜单项从上到下：

| 菜单项 | 用途 |
| --- | --- |
| 打开网页 | 在浏览器打开平台工作台 |
| 选择并注册工作区… | 把一个本机目录注册为平台工作区（客户端在线后可用） |
| 重连 | 手动重新连接平台，恢复在线状态 |
| 360 浏览器设置与自检… | 检查浏览器 Tool 依赖的企业 360 浏览器，或在多浏览器时重新选择 |
| 公共能力 · 暂无更新 | 平台发布新的公共 Agent/Skill/Tool 后，在这里确认更新 |
| 查看日志 / 下载日志 | 打开日志目录，或把受限日志打包导出到“下载”目录 |
| 会话进度 · N 项进行中 | 查看当前正在执行的会话操作 |
| 退出客户端 | 正常退出；退出后系统不会自动重新拉起 |

图标悬浮文字就是当前状态：**在线 · 本地服务已就绪**表示可以开始会话；**正在连接**表示还在连平台；**异常**表示启动或连接失败，先按文末“排查”处理。

## 在工作台使用本地工作区

- 顶部“工作空间”菜单的**本地工作区**分组会列出已注册目录，选择即可打开；客户端离线时目录呈灰色或提示离线，先在本机启动客户端或点托盘“重连”。
- 聊天里的附件会随对话上传并落到本地目录的 `.testagent/attachments`，随下一条任务交给 Agent 读取；对话操作见[对话与上下文](./conversation.md)。
- 重连或更新后，平台会自动恢复路径和身份仍匹配的历史本地工作区，不需要重新注册；目录被移动后，从托盘重新选择一次原目录。
- 不想继续共享某个目录时，在“个人设置 → 已注册本地工作区”里注销；注销只删除平台记录，本地目录和文件不受影响。删除平台工作区同样不会删除本地文件。

## 更新与版本

- **客户端本体**支持静默自更新：平台设置目标版本后，客户端自动下载、校验签名并切换，失败会自动回退；更新结果会在通知中心提示。个人设置里显示“平台尚未设置目标版本”属于正常状态，表示平台暂时没有统一安排升级。
- **公共能力**（公共 Agent/Skill/Tool）的更新需要你确认：托盘会弹出“检测到新的公共能力版本”，显示变更内容。只有 Agent/Skill 变化时热加载立即生效；包含 Tool 或依赖变化时会重启本地 OpenCode。应用失败时自动回到上一版本，不影响已保存的工作区。也可以在网页“个人设置 → 本地 OpenCode 客户端”里确认待更新版本。

## 凭据失效时重新接入

客户端提示凭据已失效（例如 key 被轮换、撤销，或服务端拒绝了旧 key）时，不需要重装：

1. 在网页“个人设置”重新复制最新 Client key。
2. 在本机终端执行：

   ```bash
   "$HOME/.local/bin/test-agent-local-client" enroll
   ```

3. 按提示重新输入统一认证号和 Client key，看到“本地客户端重新接入成功”即可。

## 常用命令速查

麒麟用户在终端执行，均不需要 sudo：

```bash
# 启动或重新连接（等价于从应用菜单打开“Test Agent 本地客户端”）
"$HOME/.local/bin/test-agent-local-client" start

# 查看当前安装的版本
"$HOME/.local/bin/test-agent-local-client" --version

# 查看用户级服务状态
systemctl --user status test-agent-local-opencode-client.service

# 查看最近日志
tail -n 200 "$HOME/.local/state/testagent/local-opencode-client/logs/launcher.log"
tail -n 200 "$HOME/.local/state/testagent/local-opencode-client/logs/client.log"
```

## 排查

| 现象 | 处理 |
| --- | --- |
| 托盘一直“正在连接”或显示离线 | 确认本机能访问平台内网入口，然后点托盘“重连”；仍失败时联系管理员检查平台入口 |
| 输入 Client key 后提示认证失败 | 确认 key 没有被轮换或撤销，在网页重新复制后再试；多次失败时让管理员轮换一个新 key，再执行上面的 `enroll` 重新接入 |
| 网页看不到本地工作区，或目录显示离线 | 对应客户端不在线：先在本机启动客户端（应用菜单“Test Agent 本地客户端”）或点托盘“重连” |
| 个人设置里没有本地客户端入口 | 账号未开通客户端灰度，联系超级管理员，见[设置与权限内操作](./settings.md) |
| 安装、接入或更新失败 | 点托盘“下载日志”，把导出的日志压缩包提供给管理员；上报内容模板见[常见问题与排查](./faq.md) |

说明：客户端日志只记录受控元数据，不包含统一认证号、Client key、聊天内容和你的工作区文件；日志里出现 `REDACTED` 是正常脱敏，不要尝试补发敏感值。

## 卸载（如需）

::: warning 卸载会清除本机的客户端凭据和运行数据
卸载后需要重新下载安装并重新接入才能再次使用；已注册目录里的文件始终不受影响。确认不再使用后再操作。
:::

先在托盘点**退出客户端**，然后在终端逐条执行：

```bash
systemctl --user disable --now test-agent-local-opencode-client.service
rm -f ~/.config/systemd/user/test-agent-local-opencode-client.service
rm -f ~/.local/share/applications/test-agent-local-client.desktop
rm -f ~/.local/bin/test-agent-local-client
rm -rf ~/.local/share/testagent/local-opencode-client
rm -rf ~/.config/testagent/local-opencode-client
rm -rf ~/.local/state/testagent/local-opencode-client
```

如需同时清理平台侧记录，可在网页“个人设置”撤销 Client key。更多问答见[常见问题与排查](./faq.md)，工作台整体能力见[功能总览](./feature-overview.md)。
