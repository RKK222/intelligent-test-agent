import { z } from "zod"

/** V2 Tool.Info 兼容工厂，供随 worker 发布的内部工具使用。 */
export const tool = Object.assign(
  (definition) => ({
    description: definition.description,
    input: z.object(definition.args ?? {}),
    execute: async (input, context) => {
      const result = await definition.execute(input, context)
      if (result && typeof result === "object" && "output" in result) return result
      return { output: typeof result === "string" ? result : JSON.stringify(result) }
    },
  }),
  { schema: z },
)
