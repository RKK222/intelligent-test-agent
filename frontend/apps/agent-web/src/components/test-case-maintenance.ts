import { marked, type Tokens } from "marked";
import type { TcdsTaskTypeOption } from "@test-agent/shared-types";

export type MarkdownTestCase = {
  name: string;
  step: string;
  data: string;
  expect: string;
};

export type TestCaseMaintenanceDraft = MarkdownTestCase & {
  taskTypes: string[];
};

export type TcdsTestCaseMaintenancePayload = {
  itemNo: string;
  caseList: Array<{
    name: string;
    step: string;
    data: string;
    expect: string;
    taskType: string;
  }>;
};

const CASE_TABLE_HEADERS = ["案例名称", "测试步骤", "测试数据", "预期结果"] as const;
const EXTENDED_CASE_TABLE_HEADERS = [
  "测试标题",
  "前置条件",
  "测试步骤",
  "测试数据",
  "预期结果",
  "测试类型",
  "优先级",
  "备注"
] as const;

const TASK_TYPE_MAPPINGS_BY_VALUE = new Map<string, { name: string; requestName: string }>([
  ["0", { name: "自定义测试任务", requestName: "自定义" }],
  ["1", { name: "安全测试任务", requestName: "安全" }],
  ["2", { name: "业务风险防控测试任务", requestName: "业务风险防控" }],
  ["3", { name: "功能测试任务", requestName: "功能测试" }],
  ["4", { name: "验收测试任务", requestName: "验收" }],
  ["5", { name: "准入测试任务", requestName: "准入" }],
  ["6", { name: "灰度测试任务", requestName: "灰度" }],
  ["7", { name: "投产验证测试任务", requestName: "投产验证" }],
  ["8", { name: "非功能性测试任务", requestName: "非功能性" }],
  ["11", { name: "验收准入测试任务", requestName: "验收准入" }]
]);

/** 同时校验 TCDS 的 value 与展示名，按确认的枚举 code 生成 createGraphCase 业务名称。 */
function toTaskTypeRequestName(option: TcdsTaskTypeOption): string {
  const mapping = TASK_TYPE_MAPPINGS_BY_VALUE.get(option.value.trim());
  if (!mapping || option.name.trim() !== mapping.name) throw new Error("任务类型无效");
  return mapping.requestName;
}

function buildTaskTypeRequestNames(options: TcdsTaskTypeOption[]): Map<string, string> {
  const requestNames = new Map<string, string>();
  const uniqueNames = new Set<string>();
  for (const option of options) {
    const value = option.value.trim();
    const requestName = toTaskTypeRequestName(option);
    if (!value || requestNames.has(value) || !uniqueNames.add(requestName)) {
      throw new Error("任务类型无效");
    }
    requestNames.set(value, requestName);
  }
  return requestNames;
}

function normalizeHeader(value: string): string {
  return value.replace(/\s+/g, "").trim();
}

function normalizeCell(value: string): string {
  return value
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/\\\|/g, "|")
    .trim();
}

function hasTableHeaders(header: Array<{ text: string }>, expected: readonly string[]): boolean {
  return Array.isArray(header)
    && header.length === expected.length
    && expected.every((name, index) => normalizeHeader(header[index]?.text ?? "") === name);
}

function withAiPrefix(name: string): string {
  const normalized = normalizeCell(name);
  return normalized.startsWith("[AI]") ? normalized : `[AI]${normalized}`;
}

function validateParsedCases(cases: MarkdownTestCase[]): MarkdownTestCase[] {
  if (cases.length === 0) throw new Error("未找到可维护的案例内容");
  if (cases.some((testCase) => !testCase.name || testCase.name === "[AI]")) {
    throw new Error("解析出的案例名称为空，请检查模板是否正确");
  }
  return cases;
}

function parseCaseTables(
  tables: Tokens.Table[],
  headers: readonly string[],
  columns: { name: number; step: number; data: number; expect: number }
): MarkdownTestCase[] {
  return tables
    .filter((table) => hasTableHeaders(table.header, headers))
    .flatMap((table) => table.rows)
    .filter((row: Tokens.TableCell[]) => row.length === headers.length)
    .map((row: Tokens.TableCell[]): MarkdownTestCase => ({
      name: withAiPrefix(row[columns.name]?.text ?? ""),
      step: normalizeCell(row[columns.step]?.text ?? ""),
      data: normalizeCell(row[columns.data]?.text ?? ""),
      expect: normalizeCell(row[columns.expect]?.text ?? "")
    }))
    .filter((testCase) => Object.values(testCase).some(Boolean));
}

function extractCaseTextField(caseBlock: string, marker: RegExp): string {
  const match = marker.exec(caseBlock);
  if (!match || match.index === undefined) return "";

  const inlineValue = match[1]?.trim() ?? "";
  const remaining = caseBlock.slice(match.index + match[0].length);
  const nextSectionIndex = remaining.search(/^\s*(?:#{2,}\s+|\[[^\]\r\n]+\])|^\s*---\s*$/m);
  const sectionValue = (nextSectionIndex >= 0 ? remaining.slice(0, nextSectionIndex) : remaining).trim();
  return [inlineValue, sectionValue].filter(Boolean).join("\n");
}

/** 按 getCacheByKey 的兜底规则解析“## 测试案例 N”分段文本。 */
function parseCaseText(markdown: string): MarkdownTestCase[] {
  const headings = [...markdown.matchAll(/^##\s+测试案例\s+\d+[^\r\n]*$/gm)];
  return headings.map((heading, index): MarkdownTestCase => {
    const start = (heading.index ?? 0) + heading[0].length;
    const end = headings[index + 1]?.index ?? markdown.length;
    const caseBlock = markdown.slice(start, end);
    const nameMatch = /^\s*案例名称\s*[：:]\s*(.*?)\s*$/m.exec(caseBlock);
    if (!nameMatch?.[1]?.trim()) throw new Error("解析出的案例名称为空，请检查模板是否正确");

    const interfaceValidation = extractCaseTextField(
      caseBlock,
      /^\s*(?:#{1,6}\s*)?\[?接口返回验证\]?\s*[：:]?\s*(.*?)\s*$/m
    );
    const databaseValidation = extractCaseTextField(
      caseBlock,
      /^\s*(?:#{1,6}\s*)?\[?数据库验证\]?\s*[：:]?\s*(.*?)\s*$/m
    );
    return {
      name: withAiPrefix(nameMatch[1]),
      step: extractCaseTextField(caseBlock, /^\s*(?:#{1,6}\s*)?测试步骤\s*[：:]\s*(.*?)\s*$/m),
      data: extractCaseTextField(caseBlock, /^\s*(?:#{1,6}\s*)?\[?测试数据\]?\s*[：:]?\s*(.*?)\s*$/m),
      expect: `[接口返回验证]\n${interfaceValidation}\n[数据库验证]\n${databaseValidation}\n`
    };
  });
}

/** 按 getCacheByKey 的优先级从当前编辑器正文生成弹窗案例。 */
export function parseMarkdownTestCases(markdown: string): MarkdownTestCase[] {
  const tables = marked.lexer(markdown).filter((token): token is Tokens.Table => token.type === "table");
  if (tables.some((table) => hasTableHeaders(table.header, CASE_TABLE_HEADERS))) {
    return validateParsedCases(parseCaseTables(tables, CASE_TABLE_HEADERS, {
      name: 0,
      step: 1,
      data: 2,
      expect: 3
    }));
  }

  if (tables.some((table) => hasTableHeaders(table.header, EXTENDED_CASE_TABLE_HEADERS))) {
    return validateParsedCases(parseCaseTables(tables, EXTENDED_CASE_TABLE_HEADERS, {
      name: 0,
      step: 2,
      data: 3,
      expect: 4
    }));
  }

  return validateParsedCases(parseCaseText(markdown));
}

export function hasMissingTaskTypes(cases: TestCaseMaintenanceDraft[]): boolean {
  return cases.some((testCase) => testCase.taskTypes.length === 0);
}

/** 每个案例保留一个对象，多任务类型按 TCDS 契约转换为业务名称并用英文逗号连接。 */
export function buildTcdsTestCaseMaintenancePayload(input: {
  cases: TestCaseMaintenanceDraft[];
  itemNo: string;
  taskTypeOptions: TcdsTaskTypeOption[];
}): TcdsTestCaseMaintenancePayload {
  const itemNo = input.itemNo.trim();
  if (!itemNo) throw new Error("无法识别需求子条目编号");
  if (hasMissingTaskTypes(input.cases)) throw new Error("请选择案例类型");
  const taskTypeRequestNames = buildTaskTypeRequestNames(input.taskTypeOptions);

  return {
    itemNo,
    caseList: input.cases.map((testCase) => {
      const taskType = [...new Set(testCase.taskTypes)].map((value) => {
        const requestName = taskTypeRequestNames.get(value);
        if (!requestName) throw new Error("任务类型无效");
        return requestName;
      }).join(",");
      return {
        name: testCase.name,
        step: testCase.step,
        data: testCase.data,
        expect: testCase.expect,
        taskType
      };
    })
  };
}
