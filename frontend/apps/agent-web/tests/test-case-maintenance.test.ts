import { describe, expect, it } from "vitest";
import {
  buildTcdsTestCaseMaintenancePayload,
  parseMarkdownTestCases
} from "../src/components/test-case-maintenance";

describe("test case maintenance", () => {
  it("parses all standard case tables and adds the AI case prefix", () => {
    const markdown = `
# 场景法案例

| 其它 | 表格 |
| --- | --- |
| 不应 | 读取 |

| 案例名称 | 测试步骤 | 测试数据 | 预期结果 |
| --- | --- | --- | --- |
| 正常交易 | 1. 准备数据<br/>2. 发起交易 | 名称=张三\\|币种=CNY | 返回成功<br />状态=0 |

## 第二组

| 案例名称 | 测试步骤 | 测试数据 | 预期结果 |
| --- | --- | --- | --- |
| [AI]超长拦截 | 输入超长字段 | 名称=141字节 | 抛出异常 |
`;

    expect(parseMarkdownTestCases(markdown)).toEqual([
      {
        name: "[AI]正常交易",
        step: "1. 准备数据\n2. 发起交易",
        data: "名称=张三|币种=CNY",
        expect: "返回成功\n状态=0"
      },
      {
        name: "[AI]超长拦截",
        step: "输入超长字段",
        data: "名称=141字节",
        expect: "抛出异常"
      }
    ]);
  });

  it("falls back to the getCacheByKey eight-column table mapping", () => {
    const markdown = `
| 测试标题 | 前置条件 | 测试步骤 | 测试数据 | 预期结果 | 测试类型 | 优先级 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 动态配置验证 | 已安装 | 1. 启动<br>2. 检查 | 开关=true | 启动成功 | 功能测试 | 高 | 无 |
`;

    expect(parseMarkdownTestCases(markdown)).toEqual([{
      name: "[AI]动态配置验证",
      step: "1. 启动\n2. 检查",
      data: "开关=true",
      expect: "启动成功"
    }]);
  });

  it("falls back to getCacheByKey case text and combines both validation sections", () => {
    const markdown = `
## 测试案例 1：正常查询

### 案例信息

案例名称：正常查询

测试步骤：
1. 构造请求
2. 调用接口

### 测试数据

| 字段 | 值 |
| --- | --- |
| status | 0 |

### 接口返回验证

| 字段 | 预期 |
| --- | --- |
| code | 0 |

### 数据库验证

| 字段 | 预期 |
| --- | --- |
| STATUS | 0 |

---

## 测试案例 2：异常查询

### 案例信息

案例名称：异常查询

测试步骤：提交非法状态

### 测试数据

status=99

### 接口返回验证

返回参数错误

### 数据库验证

数据库无新增记录
`;

    expect(parseMarkdownTestCases(markdown)).toEqual([
      {
        name: "[AI]正常查询",
        step: "1. 构造请求\n2. 调用接口",
        data: "| 字段 | 值 |\n| --- | --- |\n| status | 0 |",
        expect: "[接口返回验证]\n| 字段 | 预期 |\n| --- | --- |\n| code | 0 |\n[数据库验证]\n| 字段 | 预期 |\n| --- | --- |\n| STATUS | 0 |\n"
      },
      {
        name: "[AI]异常查询",
        step: "提交非法状态",
        data: "status=99",
        expect: "[接口返回验证]\n返回参数错误\n[数据库验证]\n数据库无新增记录\n"
      }
    ]);
  });

  it("keeps the getCacheByKey priority when multiple supported formats coexist", () => {
    const markdown = `
| 测试标题 | 前置条件 | 测试步骤 | 测试数据 | 预期结果 | 测试类型 | 优先级 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 八列案例 | 前置 | 步骤 | 数据 | 预期 | 功能测试 | 高 | 无 |

| 案例名称 | 测试步骤 | 测试数据 | 预期结果 |
| --- | --- | --- | --- |
| 四列案例 | 步骤 | 数据 | 预期 |
`;

    expect(parseMarkdownTestCases(markdown)).toEqual([{
      name: "[AI]四列案例",
      step: "步骤",
      data: "数据",
      expect: "预期"
    }]);
  });

  it("does not fall through when a higher-priority table format is empty", () => {
    const markdown = `
| 案例名称 | 测试步骤 | 测试数据 | 预期结果 |
| --- | --- | --- | --- |

## 测试案例 1：不应降级读取

案例名称：不应降级读取

测试步骤：步骤
`;

    expect(() => parseMarkdownTestCases(markdown)).toThrow("未找到可维护的案例内容");
  });

  it("joins selected task type names with an English comma in one case object", () => {
    const request = buildTcdsTestCaseMaintenancePayload({
      itemNo: "S20260703-000081",
      cases: [{
        name: "[AI]正常交易",
        step: "发起交易",
        data: "金额=100",
        expect: "交易成功",
        taskTypes: ["5", "3", "5"]
      }],
      taskTypeOptions: [
        { name: "准入测试任务", value: "5" },
        { name: "功能测试任务", value: "3" }
      ]
    });

    expect(request).toEqual({
      itemNo: "S20260703-000081",
      caseList: [
        {
          name: "[AI]正常交易",
          step: "发起交易",
          data: "金额=100",
          expect: "交易成功",
          taskType: "准入,功能测试"
        }
      ]
    });
  });

  it("rejects missing and unsupported task types", () => {
    expect(() => buildTcdsTestCaseMaintenancePayload({
      itemNo: "S20260703-000081",
      cases: [{ name: "案例", step: "", data: "", expect: "", taskTypes: [] }],
      taskTypeOptions: [{ name: "准入测试任务", value: "5" }]
    })).toThrow("请选择案例类型");
    expect(() => buildTcdsTestCaseMaintenancePayload({
      itemNo: "S20260703-000081",
      cases: [{ name: "案例", step: "", data: "", expect: "", taskTypes: ["99"] }],
      taskTypeOptions: [{ name: "准入测试任务", value: "5" }]
    })).toThrow("任务类型无效");
  });

  it("rejects unknown or mismatched task type mappings", () => {
    const baseInput = {
      itemNo: "S20260703-000081",
      cases: [{ name: "案例", step: "", data: "", expect: "", taskTypes: ["5"] }]
    };
    expect(() => buildTcdsTestCaseMaintenancePayload({
      ...baseInput,
      taskTypeOptions: [{ name: "准入", value: "5" }]
    })).toThrow("任务类型无效");
    expect(() => buildTcdsTestCaseMaintenancePayload({
      ...baseInput,
      taskTypeOptions: [{ name: "安全测试任务", value: "5" }]
    })).toThrow("任务类型无效");
    expect(() => buildTcdsTestCaseMaintenancePayload({
      ...baseInput,
      taskTypeOptions: [{ name: "探索性测试任务", value: "12" }]
    })).toThrow("任务类型无效");
  });

  it("reports an actionable error when the required case table is absent", () => {
    expect(() => parseMarkdownTestCases("# 没有可解析的案例")).toThrow("未找到可维护的案例内容");
  });
});
