#!/usr/bin/env node

import { spawn } from "node:child_process"
import { appendFile, lstat, mkdir, readFile, readdir, realpath, symlink } from "node:fs/promises"
import { homedir } from "node:os"
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"

const V2_TOOL_DEPENDENCIES = ["@opencode/plugin", "@opencode/client", "effect", "playwright-core", "zod"]
const V1_TOOL_DEPENDENCIES = ["@opencode-ai/plugin", "@opencode-ai/sdk", "effect", "playwright-core", "zod"]
const PROJECT_CONFIG_RECONCILE_COMMAND = "__reconcile-project-config"
const LEGACY_PROJECT_CONFIG_MAINTENANCE_COMMAND = "__maintain-project-config"
const PROJECT_SCAN_IGNORED_DIRECTORIES = new Set([
  ".git",
  ".gradle",
  ".idea",
  "build",
  "dist",
  "node_modules",
  "target",
])
const OFFLINE_DEFAULTS = {
  OPENCODE_CLIENT: "server",
  OPENCODE_DISABLE_AUTOUPDATE: "true",
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
  const directories = new Set([
    join(xdgConfigHome, "opencode"),
    join(cwd, ".opencode"),
  ])
  if (env.OPENCODE_CONFIG_DIR) directories.add(resolve(env.OPENCODE_CONFIG_DIR))
  if (env.OPENCODE_CONFIG) directories.add(dirname(resolve(env.OPENCODE_CONFIG)))
  return [...directories]
}

/**
 * OpenCode 会从每个请求 workspace 向上发现 `.opencode`，并在每个命中的目录启动 npm 安装。
 * 定时扫描已有工作区配置并预置本地链接；不跟随软链接，也不进入依赖和构建产物目录。
 */
async function visitProjectConfigDirectories(root, visitor) {
  const pending = [root]
  while (pending.length > 0) {
    const directory = pending.pop()
    let entries
    try {
      entries = await readdir(directory, { withFileTypes: true })
    } catch (error) {
      // 并发删除或无权读取的业务目录不能阻断本轮其它工作区维护。
      if (error?.code === "ENOENT" || error?.code === "EACCES" || error?.code === "EPERM") continue
      throw error
    }
    for (const entry of entries) {
      if (!entry.isDirectory()) continue
      if (entry.name === ".opencode") {
        await visitor(join(directory, entry.name))
        continue
      }
      if (PROJECT_SCAN_IGNORED_DIRECTORIES.has(entry.name)) continue
      pending.push(join(directory, entry.name))
    }
  }
}

/**
 * 只补齐一个已经存在且物理路径仍位于工作区内的 `.opencode` 目录；定时扫描不能经父级软链接越界。
 */
async function prepareExistingProjectConfigDirectory(directory, runtimeRoot, workspaceRoot, dependencies) {
  let info
  try {
    info = await lstat(directory)
  } catch (error) {
    if (error?.code === "ENOENT" || error?.code === "EACCES" || error?.code === "EPERM") return false
    throw error
  }
  if (info.isSymbolicLink() || !info.isDirectory()) return false
  const [physicalDirectory, physicalWorkspaceRoot] = await Promise.all([
    realpath(directory),
    realpath(workspaceRoot),
  ])
  const physicalChild = relative(physicalWorkspaceRoot, physicalDirectory)
  if (physicalChild === ".." || physicalChild.startsWith(`..${sep}`) || isAbsolute(physicalChild)) return false
  await prepareConfigDirectory(directory, runtimeRoot, dependencies)
  return true
}

/**
 * 周期任务扫描已存在的项目配置目录。单个目录被删除或暂时不可读时继续处理其它工作区。
 */
export async function reconcileProjectConfigDirectories({ cwd = process.cwd(), runtimeRoot }) {
  const resolvedRuntimeRoot = resolve(runtimeRoot)
  const resolvedCwd = resolve(cwd)
  const dependencies = runtimeContract(await runtimeVersion(resolvedRuntimeRoot), resolvedRuntimeRoot).dependencies
  let preparedCount = 0
  await visitProjectConfigDirectories(resolvedCwd, async (directory) => {
    try {
      if (await prepareExistingProjectConfigDirectory(directory, resolvedRuntimeRoot, resolvedCwd, dependencies)) preparedCount += 1
    } catch (error) {
      if (error?.code !== "ENOENT" && error?.code !== "EACCES" && error?.code !== "EPERM") throw error
    }
  })
  return preparedCount
}

function runtimeContract(runtimeVersion, runtimeRoot) {
  if (runtimeVersion.major >= 2) {
    return {
      v2: true,
      dependencies: V2_TOOL_DEPENDENCIES,
      observabilityPlugin: pathToFileURL(join(runtimeRoot, "opencode-observability-plugin")).href,
      rtkPlugin: pathToFileURL(join(runtimeRoot, "opencode-rtk-plugin")).href,
      observabilityEntry: join(runtimeRoot, "opencode-observability-plugin", "index.mjs"),
      rtkEntry: join(runtimeRoot, "opencode-rtk-plugin", "index.mjs"),
    }
  }
  return {
    v2: false,
    dependencies: V1_TOOL_DEPENDENCIES,
    observabilityPlugin: pathToFileURL(join(runtimeRoot, "opencode-observability-plugin-v1.mjs")).href,
    rtkPlugin: pathToFileURL(join(runtimeRoot, "opencode-rtk-plugin-v1.mjs")).href,
    observabilityEntry: join(runtimeRoot, "opencode-observability-plugin-v1.mjs"),
    rtkEntry: join(runtimeRoot, "opencode-rtk-plugin-v1.mjs"),
  }
}

function withRequiredConfig(env, runtimeVersion, runtimeRoot) {
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
  const contract = runtimeContract(runtimeVersion, runtimeRoot)
  if (contract.v2) {
    // V2 显式 plugins 只接受目录，目录中的 index.mjs 才是 server entrypoint。
    const inheritedPlugins = Array.isArray(config.plugins) ? config.plugins : []
    const plugins = inheritedPlugins.map((entry) =>
      typeof entry === "string" ? { package: entry } : entry,
    )
    plugins.push({ package: contract.observabilityPlugin })
    if (env.TEST_AGENT_RTK_ENABLED === "true") {
      plugins.push({ package: contract.rtkPlugin })
    }
    config.plugins = plugins.filter((entry, index, all) =>
      all.findIndex((candidate) => candidate.package === entry.package) === index,
    )
    delete config.plugin
    // V2 的顶层 subagent_depth 被规范化器丢弃，必须放入 experimental。
    delete config.subagent_depth
    const experimental = config.experimental && typeof config.experimental === "object" && !Array.isArray(config.experimental)
      ? { ...config.experimental } : {}
    experimental.subagent_depth = 2
    config.experimental = experimental
  } else {
    // V1 仍使用旧的 plugin 数组和单文件入口；保留 1.18.4 回滚包的 ABI。
    const inheritedPlugins = Array.isArray(config.plugin)
      ? config.plugin
      : Array.isArray(config.plugins) ? config.plugins : []
    const plugins = inheritedPlugins.map((entry) => typeof entry === "string" ? entry : entry?.package).filter(Boolean)
    plugins.push(contract.observabilityPlugin)
    if (env.TEST_AGENT_RTK_ENABLED === "true") plugins.push(contract.rtkPlugin)
    config.plugin = [...new Set(plugins)]
    delete config.plugins
    if (runtimeVersion.minor > 18 || (runtimeVersion.minor === 18 && runtimeVersion.patch >= 2)) {
      config.subagent_depth = 2
    } else {
      // 1.17.x 会将新字段判定为非法配置；回滚时必须主动移除。
      delete config.subagent_depth
    }
  }
  return JSON.stringify(config)
}

async function runtimeVersion(runtimeRoot) {
  const version = (await readFile(join(runtimeRoot, "VERSION"), "utf8")).trim()
  const match = /^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$/.exec(version)
  if (!match) throw new Error(`invalid OpenCode runtime VERSION: ${version}`)
  const [, major, minor, patch] = match.map(Number)
  return { major, minor, patch }
}

/**
 * 在 OpenCode 创建 package/lockfile 前补齐运行文件忽略规则。
 * 现场已有规则原样保留，只追加交付基线中缺失的规则，重复启动不会重复写入。
 */
async function ensureRuntimeGitIgnore(directory, runtimeRoot) {
  const rules = (await readFile(join(runtimeRoot, "opencode-runtime.gitignore"), "utf8"))
    .split(/\r?\n/u)
    .map((rule) => rule.trim())
    .filter((rule) => rule && !rule.startsWith("#"))
  const gitignore = join(directory, ".gitignore")
  let existing = ""
  try {
    existing = await readFile(gitignore, "utf8")
  } catch (error) {
    if (error?.code !== "ENOENT") throw error
  }
  const existingRules = new Set(existing.split(/\r?\n/u))
  const missing = rules.filter((rule) => !existingRules.has(rule))
  if (missing.length === 0) return
  const separator = existing && !existing.endsWith("\n") ? "\n" : ""
  await appendFile(gitignore, `${separator}${missing.join("\n")}\n`, "utf8")
}

async function prepareConfigDirectory(directory, runtimeRoot, dependencies) {
  await mkdir(directory, { recursive: true })
  await ensureRuntimeGitIgnore(directory, runtimeRoot)
  await linkIfMissing(join(runtimeRoot, "package.json"), join(directory, "package.json"))
  await linkIfMissing(join(runtimeRoot, "package-lock.json"), join(directory, "package-lock.json"))
  for (const dependency of dependencies) {
    await linkIfMissing(
      join(runtimeRoot, "node_modules", ...dependency.split("/")),
      join(directory, "node_modules", ...dependency.split("/")),
    )
  }
}

/**
 * 为官方单文件程序准备完全离线的 Tool 运行目录。
 * 所有链接均为非覆盖式，工作区自行维护的依赖和 package 元数据优先保留。
 */
export async function prepareOfflineRuntime({ cwd = process.cwd(), env = process.env, runtimeRoot }) {
  const resolvedRuntimeRoot = resolve(runtimeRoot)
  const resolvedCwd = resolve(cwd)
  const prepared = { ...env, ...OFFLINE_DEFAULTS }
  const version = await runtimeVersion(resolvedRuntimeRoot)
  const contract = runtimeContract(version, resolvedRuntimeRoot)
  if (!(await pathExists(contract.observabilityEntry))) {
    throw new Error(`OpenCode ${contract.v2 ? "V2" : "V1"} observability plugin is missing from the offline runtime`)
  }
  if (!prepared.OPENCODE_PASSWORD && prepared.TEST_AGENT_OPENCODE_SERVER_PASSWORD) {
    // V2 serve 默认随机生成密码；平台由同一受控 secret 让 worker 与 Java gateway 共享认证。
    prepared.OPENCODE_PASSWORD = prepared.TEST_AGENT_OPENCODE_SERVER_PASSWORD
  }
  if (prepared.TEST_AGENT_RTK_ENABLED === "true") {
    // 运行包内固定二进制是平台交付边界，不允许父进程环境把 RTK 替换成任意路径。
    prepared.TEST_AGENT_RTK_BIN = join(resolvedRuntimeRoot, "bin", process.platform === "win32" ? "rtk.exe" : "rtk")
    prepared.RTK_TELEMETRY_DISABLED = "1"
    prepared.RTK_RECALL = "0"
    if (!(await pathExists(contract.rtkEntry))) {
      throw new Error("RTK plugin is missing from the offline OpenCode runtime")
    }
    const rtkBinaryInfo = await lstat(prepared.TEST_AGENT_RTK_BIN)
    if (!rtkBinaryInfo.isFile() || (rtkBinaryInfo.mode & 0o111) === 0) {
      throw new Error("RTK binary is missing or not executable in the offline OpenCode runtime")
    }
  }
  prepared.OPENCODE_CONFIG_CONTENT = withRequiredConfig(
    prepared,
    version,
    resolvedRuntimeRoot,
  )
  prepared.OPENCODE_OFFLINE_TOOL_NODE_MODULES = join(resolvedRuntimeRoot, "node_modules")

  // OpenCode 进程工作目录是所有个人 worktree 的共同祖先；在这里投影依赖后，
  // 深层应用 workspace 的 .opencode/tools 也能按 Node 标准祖先规则离线解析模块。
  for (const dependency of contract.dependencies) {
    await linkIfMissing(
      join(resolvedRuntimeRoot, "node_modules", ...dependency.split("/")),
      join(resolvedCwd, "node_modules", ...dependency.split("/")),
    )
  }

  const configDirectories = new Set([
    ...effectiveConfigDirectories(resolvedCwd, prepared),
  ])
  const existingHomeConfig = join(prepared.HOME || homedir(), ".opencode")
  if (await pathExists(existingHomeConfig)) configDirectories.add(existingHomeConfig)
  for (const directory of configDirectories) {
    await prepareConfigDirectory(directory, resolvedRuntimeRoot, contract.dependencies)
  }
  return prepared
}

async function runProjectConfigReconciliation() {
  const args = process.argv.slice(3)
  let root = process.cwd()
  if (args.length > 0) {
    if (args.length !== 2 || args[0] !== "--root" || !args[1]) {
      throw new Error(`usage: ${PROJECT_CONFIG_RECONCILE_COMMAND} [--root <workspace-root>]`)
    }
    root = args[1]
  }
  const runtimeRoot = fileURLToPath(new URL("../", import.meta.url))
  await reconcileProjectConfigDirectories({ cwd: root, runtimeRoot })
  return 0
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
      // OpenCode V2 的 Bun serve 收到 SIGTERM 后会清理 watcher 再以 130 退出；
      // 仅在本启动器确实转发停止信号时将该退出视为受控停止。
      if (forwardedSignal && (signal === forwardedSignal || code === 130 || code === 128 + (forwardedSignal === "SIGTERM" ? 15 : 2))) {
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
  const internalCommand = process.argv[2]
  const run = internalCommand === PROJECT_CONFIG_RECONCILE_COMMAND
    || internalCommand === LEGACY_PROJECT_CONFIG_MAINTENANCE_COMMAND
    ? runProjectConfigReconciliation
    : runOfficialBinary
  run()
    .then((exitCode) => {
      process.exitCode = exitCode
    })
    .catch((error) => {
      console.error(error instanceof Error ? error.stack : String(error))
      process.exitCode = 1
    })
}
