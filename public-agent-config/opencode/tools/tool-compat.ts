import { z } from "zod"

/**
 * V2 Tool.Info 兼容工厂。
 *
 * <p>现有公共工具仍使用旧的 args/execute 书写方式；此适配器把字段对象
 * 包装成 V2 标准 schema，并统一返回 V2 要求的 { output } 结果。</p>
 */
export const tool = Object.assign(
  (definition: {
    description: string
    args: Record<string, unknown>
    execute: (input: any, context: any) => Promise<any> | any
  }) => ({
    description: definition.description,
    input: z.object(definition.args as any),
    execute: async (input: any, context: any) => {
      const result = await definition.execute(input, context)
      if (result && typeof result === "object" && "output" in result) return result
      return { output: typeof result === "string" ? result : JSON.stringify(result) }
    },
  }),
  { schema: z },
)

export type ToolContext = Record<string, unknown>
