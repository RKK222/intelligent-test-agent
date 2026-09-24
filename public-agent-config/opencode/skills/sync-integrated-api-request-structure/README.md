# 一体化平台接口报文结构同步 Skill

版本：1.0.0（2026-09-22）

## 用途

当用户在接口自动化脚本生成后，或指定某个需求子条目/042 文件并给出具体接口英文名时，本 Skill 从工作空间路径解析应用名和月度版本，查询一体化平台接口结构，并使请求 JSON 与 `interfaceInfo.reqParamStruct` 一致。

## 安装

把压缩包中的整个 `sync-integrated-api-request-structure/` 目录放到公共配置的：

```text
opencode/skills/sync-integrated-api-request-structure/
```

如果执行 Agent 不会自动发现新增 Skill，可在 `test-execution-api` 的“生成脚本”步骤后补充：

> 当用户给出具体接口英文名并要求按一体化平台调整报文结构时，加载 `sync-integrated-api-request-structure`；目标不唯一或查询失败时返回 INCOMPLETE，不得猜测或改写其他 042 文件。

## 文件

- `SKILL.md`：触发条件、路径与写回约束。
- `scripts/query_interface_contract.py`：从工作空间路径解析应用/月度版本并查询固定接口。
- `scripts/normalize_request_payload.py`：按 `reqParamStruct` 规范化和复核请求 JSON。
- `scripts/locate_request_markdown.py`：在一个 042 目录内精确定位唯一目标文件。
- `scripts/update_markdown_request.py`：原子替换唯一请求 JSON 代码块。
- `scripts/sync_request_structure.py`：既有 042 文件的一体化同步入口。
- `tests/test_skill_scripts.py`：不访问真实内网接口的离线测试。
- `evals/evals.json`：触发与失败关闭行为样例。

## 本地验证

在解压后的 Skill 目录运行：

```text
python -m unittest discover -s tests -v
python <skill-creator目录>/scripts/quick_validate.py .
```

测试使用 mock 响应，不会调用真实一体化平台，也不会修改版本库。
