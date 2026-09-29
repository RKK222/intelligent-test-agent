import { execFile } from "node:child_process"
import { Plugin } from "@opencode/plugin"

const DEFAULT_TIMEOUT_MS = 2_000

function enabled(env) {
  return String(env.TEST_AGENT_RTK_ENABLED ?? "").trim().toLowerCase() === "true"
}

function binaryPath(env) {
  const value = String(env.TEST_AGENT_RTK_BIN ?? "").trim()
  return value || "rtk"
}

function rewriteCommand(command, env, timeoutMs, runner) {
  return new Promise((resolve) => {
    runner(binaryPath(env), ["rewrite", command], { env, timeout: timeoutMs, maxBuffer: 1024 * 1024 }, (error, stdout) => {
      // RTK 的 ask 状态仍可能返回可执行的改写命令；只有明确 deny 才保持原命令。
      if (error?.code === 2 || typeof stdout !== "string") {
        resolve(command)
        return
      }
      const rewritten = stdout.trim()
      resolve(rewritten && rewritten !== command ? rewritten : command)
    })
  })
}

export function createRtkPlugin(options = {}) {
  const env = options.env ?? process.env
  if (!enabled(env)) return {}
  const timeoutMs = Number.isSafeInteger(options.timeoutMs) && options.timeoutMs > 0
    ? options.timeoutMs
    : DEFAULT_TIMEOUT_MS
  const runner = options.runner ?? execFile
  return {
    "tool.execute.before": async (input, output) => {
      const tool = String(input?.tool ?? "").toLowerCase()
      const command = output?.args?.command
      if ((tool !== "bash" && tool !== "shell") || typeof command !== "string" || !command) return
      const rewritten = await rewriteCommand(command, env, timeoutMs, runner)
      if (rewritten !== command && output?.args) output.args.command = rewritten
    },
  }
}

export default Plugin.define({
  id: "test-agent-rtk",
  async setup(ctx) {
    const configuration = createRtkPlugin()
    if (!configuration["tool.execute.before"]) return
    return ctx.tool.hook("execute.before", async (event) => {
      await configuration["tool.execute.before"](
        event,
        { args: event.input },
      )
    })
  },
})
