#!/usr/bin/env node

import { readFile } from "node:fs/promises"

const runtimeRoot = new URL("../", import.meta.url)

async function readVersion() {
  return (await readFile(new URL("VERSION", runtimeRoot), "utf8")).trim()
}

function usage() {
  return `Usage:
  opencode --version
  opencode serve --hostname <host> --port <port> [--cors <origin>] [--print-logs]`
}

function readOption(args, index, name) {
  const current = args[index]
  const prefix = `${name}=`
  if (current.startsWith(prefix)) return { value: current.slice(prefix.length), consumed: 1 }
  const value = args[index + 1]
  if (value === undefined || value.startsWith("--")) {
    throw new Error(`${name} requires a value`)
  }
  return { value, consumed: 2 }
}

function parseServeOptions(args) {
  const options = { hostname: "127.0.0.1", port: 0, cors: [] }
  for (let index = 0; index < args.length; ) {
    const current = args[index]
    if (current === "--print-logs") {
      index += 1
      continue
    }
    if (current === "--hostname" || current.startsWith("--hostname=")) {
      const parsed = readOption(args, index, "--hostname")
      options.hostname = parsed.value
      index += parsed.consumed
      continue
    }
    if (current === "--port" || current.startsWith("--port=")) {
      const parsed = readOption(args, index, "--port")
      const port = Number(parsed.value)
      if (!Number.isInteger(port) || port < 0 || port > 65535) {
        throw new Error(`invalid --port value: ${parsed.value}`)
      }
      options.port = port
      index += parsed.consumed
      continue
    }
    if (current === "--cors" || current.startsWith("--cors=")) {
      const parsed = readOption(args, index, "--cors")
      options.cors.push(parsed.value)
      index += parsed.consumed
      continue
    }
    throw new Error(`unsupported serve option: ${current}`)
  }
  return options
}

function enableOfflineDefaults() {
  // 企业运行环境不联网；这些默认值阻止启动期更新、模型刷新、LSP 下载和远程 skill 拉取。
  const defaults = {
    OPENCODE_CLIENT: "server",
    OPENCODE_DISABLE_AUTOUPDATE: "true",
    OPENCODE_DISABLE_CONFIG_DEPENDENCY_INSTALL: "true",
    OPENCODE_DISABLE_EMBEDDED_WEB_UI: "true",
    OPENCODE_DISABLE_EXTERNAL_SKILLS: "true",
    OPENCODE_DISABLE_LSP_DOWNLOAD: "true",
    OPENCODE_DISABLE_MODELS_FETCH: "true",
  }
  for (const [key, value] of Object.entries(defaults)) {
    if (process.env[key] === undefined) process.env[key] = value
  }
}

async function serve(args) {
  const options = parseServeOptions(args)
  enableOfflineDefaults()

  // 必须先写入离线环境变量再加载 bundle；上游部分 flag 会在模块初始化时读取环境。
  const { Server } = await import(new URL("server/node.js", runtimeRoot))
  const listener = await Server.listen(options)
  console.log(`opencode node server ${await readVersion()} listening on ${listener.url.href}`)

  let stopping = false
  const stop = async () => {
    if (stopping) return
    stopping = true
    try {
      await listener.stop(true)
      // 两个 signal listener 会继续占用事件循环；HTTP 停止后显式退出，避免 Docker 超时强杀。
      process.exit(0)
    } catch (error) {
      console.error(error instanceof Error ? error.stack : String(error))
      process.exit(1)
    }
  }
  process.once("SIGINT", stop)
  process.once("SIGTERM", stop)

  // HTTP server 会保持事件循环；这个未决 Promise 让入口语义与原生 `opencode serve` 一致。
  await new Promise(() => {})
}

async function main() {
  const args = process.argv.slice(2)
  if (args.length === 1 && (args[0] === "--version" || args[0] === "-v" || args[0] === "version")) {
    console.log(await readVersion())
    return
  }
  if (args.length === 0 || args.includes("--help") || args.includes("-h")) {
    console.log(usage())
    return
  }
  if (args[0] !== "serve") throw new Error(`unsupported command: ${args[0]}`)
  await serve(args.slice(1))
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error))
  process.exitCode = 1
})
