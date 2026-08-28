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

/** 只有 createGraphCase 成功后才继续缓存跳转，失败时不触发任何新页面。 */
export async function maintainTcdsTestCasesBeforeNavigate(input: {
  maintain: () => Promise<void>;
  navigate: () => Promise<void>;
}): Promise<void> {
  await input.maintain();
  await input.navigate();
}

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

/** createGraphCase 直接使用本次 getTaskTypes 返回的完整名称，逗号仅保留为多选分隔符。 */
function buildTaskTypeNames(options: TcdsTaskTypeOption[]): Map<string, string> {
  const namesByValue = new Map<string, string>();
  const uniqueNames = new Set<string>();
  for (const option of options) {
    const value = option.value.trim();
    const name = option.name.trim();
    if (!value || !name || name.includes(",") || name.includes("，")
      || namesByValue.has(value) || uniqueNames.has(name)) {
      throw new Error("任务类型无效");
    }
    namesByValue.set(value, name);
    uniqueNames.add(name);
  }
  return namesByValue;
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

/** 每个案例保留一个对象，多任务类型使用实时查询到的完整名称并以英文逗号连接。 */
export function buildTcdsTestCaseMaintenancePayload(input: {
  cases: TestCaseMaintenanceDraft[];
  itemNo: string;
  taskTypeOptions: TcdsTaskTypeOption[];
}): TcdsTestCaseMaintenancePayload {
  const itemNo = input.itemNo.trim();
  if (!itemNo) throw new Error("无法识别需求子条目编号");
  if (hasMissingTaskTypes(input.cases)) throw new Error("请选择案例类型");
  const taskTypeNames = buildTaskTypeNames(input.taskTypeOptions);

  return {
    itemNo,
    caseList: input.cases.map((testCase) => {
      const taskType = [...new Set(testCase.taskTypes)].map((value) => {
        const name = taskTypeNames.get(value);
        if (!name) throw new Error("任务类型无效");
        return name;
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
