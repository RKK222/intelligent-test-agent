#!/usr/bin/env node

import { spawn } from "node:child_process"
import { lstat, mkdir, readFile, realpath, symlink } from "node:fs/promises"
import { homedir } from "node:os"
import { dirname, join, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const TOOL_DEPENDENCIES = ["@opencode-ai/plugin", "@opencode-ai/sdk", "effect", "zod"]
const OFFLINE_DEFAULTS = {
  OPENCODE_CLIENT: "server",
  OPENCODE_DISABLE_AUTOUPDATE: "true",
  OPENCODE_DISABLE_CONFIG_DEPENDENCY_INSTALL: "true",
  OPENCODE_DISABLE_EMBEDDED_WEB_UI: "true",
  OPENCODE_DISABLE_EXTERNAL_SKILLS: "true",
  OPENCODE_DISABLE_LSP_DOWNLOAD: "true",
  OPENCODE_DISABLE_MODELS_FETCH: "true",
}

async function pathExists(path) {
  try {
    await lstat(path)
    return true
  } catch (error) {
    if (error?.code === "ENOENT") return false
    throw error
  }
}

async function linkIfMissing(source, target) {
  if (await pathExists(target)) return
  await mkdir(dirname(target), { recursive: true })
  try {
    await symlink(source, target)
  } catch (error) {
    // 多个进程并发启动同一工作区时，另一个进程可能刚好已经创建链接。
    if (error?.code !== "EEXIST") throw error
  }
}

function effectiveConfigDirectories(cwd, env) {
  const home = env.HOME || homedir()
  const xdgConfigHome = env.XDG_CONFIG_HOME || join(home, ".config")
  const directories = new Set([join(xdgConfigHome, "opencode"), join(cwd, ".opencode")])
  if (env.OPENCODE_CONFIG_DIR) directories.add(resolve(env.OPENCODE_CONFIG_DIR))
  if (env.OPENCODE_CONFIG) directories.add(dirname(resolve(env.OPENCODE_CONFIG)))
  return [...directories]
}

function withRequiredConfig(env, supportsSubagentDepth) {
  let inherited = {}
  if (env.OPENCODE_CONFIG_CONTENT) {
    try {
      inherited = JSON.parse(env.OPENCODE_CONFIG_CONTENT)
    } catch (error) {
      throw new Error(`invalid OPENCODE_CONFIG_CONTENT: ${error.message}`, { cause: error })
    }
    if (inherited === null || Array.isArray(inherited) || typeof inherited !== "object") {
      throw new Error("invalid OPENCODE_CONFIG_CONTENT: root value must be an object")
    }
  }
  const config = { ...inherited }
  if (supportsSubagentDepth) {
    config.subagent_depth = 2
  } else {
    // 1.17.x 会将新字段判定为非法配置；回滚时必须主动移除。
    delete config.subagent_depth
  }
  return JSON.stringify(config)
}

async function runtimeSupportsSubagentDepth(runtimeRoot) {
  const version = (await readFile(join(runtimeRoot, "VERSION"), "utf8")).trim()
  const match = /^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$/.exec(version)
  if (!match) throw new Error(`invalid OpenCode runtime VERSION: ${version}`)
  const [, major, minor, patch] = match.map(Number)
  return major > 1 || (major === 1 && (minor > 18 || (minor === 18 && patch >= 2)))
}

/**
 * 为官方单文件程序准备完全离线的 Tool 运行目录。
 * 所有链接均为非覆盖式，工作区自行维护的依赖和 package 元数据优先保留。
 */
export async function prepareOfflineRuntime({ cwd = process.cwd(), env = process.env, runtimeRoot }) {
  const resolvedRuntimeRoot = resolve(runtimeRoot)
  const prepared = { ...env, ...OFFLINE_DEFAULTS }
  prepared.OPENCODE_CONFIG_CONTENT = withRequiredConfig(
    prepared,
    await runtimeSupportsSubagentDepth(resolvedRuntimeRoot),
  )
  prepared.OPENCODE_OFFLINE_TOOL_NODE_MODULES = join(resolvedRuntimeRoot, "node_modules")

  for (const directory of effectiveConfigDirectories(resolve(cwd), prepared)) {
    await mkdir(directory, { recursive: true })
    await linkIfMissing(join(resolvedRuntimeRoot, "package.json"), join(directory, "package.json"))
    await linkIfMissing(join(resolvedRuntimeRoot, "package-lock.json"), join(directory, "package-lock.json"))
    for (const dependency of TOOL_DEPENDENCIES) {
      await linkIfMissing(
        join(resolvedRuntimeRoot, "node_modules", ...dependency.split("/")),
        join(directory, "node_modules", ...dependency.split("/")),
      )
    }
  }
  return prepared
}

async function runOfficialBinary() {
  const args = process.argv.slice(2)
  const runtimeRoot = fileURLToPath(new URL("../", import.meta.url))
  const officialBinary = process.env.OPENCODE_REAL_BIN || join(runtimeRoot, "bin", "opencode-official")
  const env = args[0] === "serve"
    ? await prepareOfflineRuntime({ cwd: process.cwd(), env: process.env, runtimeRoot })
    : { ...process.env, ...OFFLINE_DEFAULTS }

  const child = spawn(officialBinary, args, { env, stdio: "inherit" })
  let forwardedSignal = null
  const forward = (signal) => {
    forwardedSignal = signal
    if (child.exitCode === null && child.signalCode === null) child.kill(signal)
  }
  const handlers = new Map([
    ["SIGINT", () => forward("SIGINT")],
    ["SIGTERM", () => forward("SIGTERM")],
  ])
  for (const [signal, handler] of handlers) process.once(signal, handler)

  const exitCode = await new Promise((resolveExit, reject) => {
    child.once("error", reject)
    child.once("exit", (code, signal) => {
      if (forwardedSignal && (signal === forwardedSignal || code === 128 + (forwardedSignal === "SIGTERM" ? 15 : 2))) {
        resolveExit(0)
        return
      }
      resolveExit(code ?? (signal === "SIGTERM" ? 143 : 1))
    })
  })
  for (const [signal, handler] of handlers) process.off(signal, handler)
  return exitCode
}

async function isMainModule() {
  if (!process.argv[1]) return false
  try {
    return await realpath(process.argv[1]) === await realpath(fileURLToPath(import.meta.url))
  } catch {
    return false
  }
}

if (await isMainModule()) {
  runOfficialBinary()
    .then((exitCode) => {
      process.exitCode = exitCode
    })
    .catch((error) => {
      console.error(error instanceof Error ? error.stack : String(error))
      process.exitCode = 1
    })
}
