# LobeHub 独立 fork 企业 Git 转运与导入

本文用于把平台同级独立仓库 `../lobehub-platform` 以自包含 Git Bundle 转运到企业内网 Git。它与
`test-agent-lobehub-offline.zip` 的运行介质交付相互独立：Bundle 解决源码托管和后续维护，运行 ZIP 解决现场
安装。企业内网禁止从 GitHub fetch，转运包不复制 checkout 的 Git 配置、credential helper 数据或企业远端地址。
Bundle 会保留 `main`/tag 可达的提交历史；构建器会扫描 fork 相对锁定上游提交新增的全部 blob、commit、tag
对象和当前 annotated tag，命中高置信私钥或常见 token 格式时失败关闭。该门禁不替代企业 Git 的持续 secret
scanning，也不能把上游公开历史中的测试字符串解释为企业凭据审计已经完成。

## 当前锁定版本与介质

- 内部版本：`v2.2.11-platform.3`；
- fork commit：`ccd0400fbe934ba929de637a315d25e969977c76`；
- 发布分支：`refs/heads/main`；
- annotated tag：`refs/tags/v2.2.11-platform.3`；
- 当前外网 Mac 转运件：
  `~/Desktop/mimoagent/0709/lobehub-fork-transfer/lobehub-fork-transfer-v2.2.11-platform.3.zip`；
- 当前外层 SHA-256：`a494d5a94b7db39fa584c2591b72fb01a1fa3bb61f426fbf59b93a2a4c2d0461`。

上述路径和摘要描述当前已生成的真实介质，不代表企业 Git 远端已创建或已推送。远端 URL、访问控制、分支保护
和仓库备份仍由企业 Git 管理员按变更单完成。

## 外网 Mac 生成

生成前确认 fork 工作树干净、`main` 与内部 annotated tag 都指向版本锁中的 commit：

```bash
cd /Users/huang/workspace/intelligent-test-agent-gitee
git -C /Users/huang/workspace/lobehub-platform status --short
git -C /Users/huang/workspace/lobehub-platform rev-parse refs/heads/main
git -C /Users/huang/workspace/lobehub-platform rev-parse 'refs/tags/v2.2.11-platform.3^{}'

deploy/internal/build-lobehub-fork-transfer.sh \
  --fork-dir /Users/huang/workspace/lobehub-platform \
  --output-dir deploy/internal/dist-lobehub-fork-transfer
```

构建器只向 Bundle 发布 `main` 和内部版本 tag，不发布上游数百个 tag；但会包含这两个 ref 可达的完整 Git
历史，因此可以在断网环境独立 clone。脚本会依次校验干净工作树、版本、祖先关系、annotated tag、fork 增量
历史高置信凭据格式、Bundle ref，再从 Bundle 创建一次独立 clone。输出目录不得等于或包含版本锁、平台仓库、
fork checkout 或部署脚本，即使使用 `--force` 也不能删除这些输入。成功后输出版本化 ZIP 和外层 `.sha256`，
ZIP 内包含：

```text
lobehub-fork-transfer-v2.2.11-platform.3/
  lobehub-platform-v2.2.11-platform.3.bundle
  refs.txt
  IMPORT.md
  SHA256SUMS
```

把 ZIP 与 `.sha256` 一起复制到外网 Mac 的 `~/Desktop/mimoagent/0709/lobehub-fork-transfer`，经批准的 U 盘
转入企业内部中转机同名目录。不得把 `.git` 工作目录、GitHub credential helper、SSH 私钥或 access token
一并转运。

## 企业中转机校验

以下命令在企业内部中转机执行，预期两次校验都全部显示 `OK`：

```bash
cd ~/Desktop/mimoagent/0709/lobehub-fork-transfer
sha256sum -c lobehub-fork-transfer-v2.2.11-platform.3.zip.sha256
unzip -q lobehub-fork-transfer-v2.2.11-platform.3.zip
cd lobehub-fork-transfer-v2.2.11-platform.3
sha256sum -c SHA256SUMS
grep -Fx 'FORK_DELTA_CREDENTIAL_SCAN=Passed' refs.txt

git bundle list-heads lobehub-platform-v2.2.11-platform.3.bundle
```

`git bundle list-heads` 必须只显示两行：`refs/heads/main` 的 commit 必须是
`ccd0400fbe934ba929de637a315d25e969977c76`，另一行为 `refs/tags/v2.2.11-platform.3`。tag 行显示的是
annotated tag object，不要求等于 fork commit；clone 后必须再校验 tag 的解引用结果。

## 导入企业 Git

企业 Git 管理员先创建空仓库并配置访问控制、审计、备份和 `main` 分支保护，再在不把凭据写入命令或日志的
前提下执行：

```bash
cd ~/Desktop/mimoagent/0709/lobehub-fork-transfer/lobehub-fork-transfer-v2.2.11-platform.3
git clone lobehub-platform-v2.2.11-platform.3.bundle lobehub-platform
cd lobehub-platform

test "$(git branch --show-current)" = main
test "$(git rev-parse HEAD)" = ccd0400fbe934ba929de637a315d25e969977c76
test "$(git rev-parse 'refs/tags/v2.2.11-platform.3^{}')" = ccd0400fbe934ba929de637a315d25e969977c76

git remote rename origin transfer
git remote add origin <enterprise-git-url>
git push --set-upstream origin main
git push origin refs/tags/v2.2.11-platform.3
```

`<enterprise-git-url>` 必须替换为现场审批的内部地址；HTTPS 使用企业 credential helper，SSH 使用企业 Git
管理员自己的受控密钥，禁止把用户名、密码、token 或私钥写回 Bundle、文档和平台仓库。

推送后用只读命令核对远端。预期 `main` 为锁定 fork commit，tag 解引用后也是同一 commit：

```bash
git ls-remote origin refs/heads/main refs/tags/v2.2.11-platform.3 \
  'refs/tags/v2.2.11-platform.3^{}'
```

把远端 URL 的脱敏标识、校验结果、管理员、时间和变更单写入企业运维记录。企业远端验证通过前，本机同级
checkout 和转运 Bundle 只能作为构建/恢复来源，不能宣称“内部 fork 托管已完成”。

## 失败与回滚

- 任一 SHA、ref、commit、tag 解引用或工作树检查不一致时立即停止，不使用 `git fsck --lost-found`、强制 push
  或从 GitHub 现场补取对象来掩盖问题；回到外网 Mac 从锁定、干净 fork 重新生成新目录。
- 命中私钥/token 格式时先按企业流程轮换凭据，再从 fork 历史彻底删除对应对象并重新建立发布 tag；只删除最新
  工作树文件仍会使旧 blob 进入 Bundle，禁止以误报名义直接跳过扫描。
- 导入失败但远端尚未被使用时，由企业 Git 管理员按变更单删除或隔离不完整空仓库后重试；工具不自动删除远端。
- 已投入使用的远端不得用 Bundle 强制覆盖。后续升级必须建立新的内部版本 tag、更新平台版本锁并按正常 Git
  审核流程合并。
- 转运 ZIP 和 `.sha256` 按源码发布介质归档；访问权限不得低于企业源代码仓库本身。

运行介质生成、客户端签名和现场安装分别见
[LobeHub 企业客户端原生构建与审批](lobehub-client-build.md) 与
[LobeHub 企业离线部署](lobehub-offline.md)。
