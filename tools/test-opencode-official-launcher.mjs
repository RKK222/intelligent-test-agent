#!/usr/bin/env node

import assert from "node:assert/strict"
import { execFile, spawn } from "node:child_process"
import { chmod, copyFile, lstat, mkdtemp, mkdir, readFile, readlink, realpath, rm, symlink, writeFile } from "node:fs/promises"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { setTimeout as delay } from "node:timers/promises"
import test from "node:test"
import { promisify } from "node:util"

import {
  prepareOfflineRuntime,
  reconcileProjectConfigDirectories,
  startProjectConfigMaintenance,
} from "../deploy/internal/opencode-official-launcher.mjs"

const execFileAsync = promisify(execFile)

async function createRuntime(root) {
  await mkdir(join(root, "node_modules", "@opencode-ai", "plugin"), { recursive: true })
  await mkdir(join(root, "node_modules", "@opencode-ai", "sdk"), { recursive: true })
  await mkdir(join(root, "node_modules", "effect"), { recursive: true })
  await mkdir(join(root, "node_modules", "zod"), { recursive: true })
  await writeFile(
    join(root, "node_modules", "@opencode-ai", "plugin", "package.json"),
    '{"name":"@opencode-ai/plugin","type":"module","exports":"./index.js"}\n',
  )
  await writeFile(
    join(root, "node_modules", "@opencode-ai", "plugin", "index.js"),
    "export const loaded = true\n",
  )
  await writeFile(join(root, "package.json"), '{"private":true}\n')
  await writeFile(join(root, "package-lock.json"), '{"lockfileVersion":3}\n')
  await copyFile(
    new URL("../deploy/internal/opencode-runtime.gitignore", import.meta.url),
    join(root, "opencode-runtime.gitignore"),
  )
  await writeFile(join(root, "VERSION"), "1.18.4\n")
}

async function assertToolDependencyLinks(directory, runtimeRoot) {
  assert.equal(
    await readlink(join(directory, "node_modules", "@opencode-ai", "plugin")),
    join(runtimeRoot, "node_modules", "@opencode-ai", "plugin"),
  )
  assert.equal(
    await readlink(join(directory, "node_modules", "@opencode-ai", "sdk")),
    join(runtimeRoot, "node_modules", "@opencode-ai", "sdk"),
  )
  assert.equal(await readlink(join(directory, "node_modules", "effect")), join(runtimeRoot, "node_modules", "effect"))
  assert.equal(await readlink(join(directory, "node_modules", "zod")), join(runtimeRoot, "node_modules", "zod"))
}

async function waitForPath(path, timeoutMs = 2_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      return await lstat(path)
    } catch (error) {
      if (error?.code !== "ENOENT") throw error
    }
    await delay(20)
  }
  throw new Error(`timed out waiting for ${path}`)
}

async function waitForProcessGone(pid, timeoutMs = 2_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      process.kill(pid, 0)
    } catch (error) {
      if (error?.code === "ESRCH") return
      throw error
    }
    await delay(20)
  }
  throw new Error(`timed out waiting for process ${pid} to exit`)
}

test("keeps recursive project scanning out of user startup and prepares it through reconciliation", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-"))
  try {
    const runtimeRoot = join(root, "runtime")
    const workspace = join(root, "workspace")
    const configDir = join(root, "public-config")
    const xdgConfigHome = join(root, "xdg-config")
    const home = join(root, "home")
    const nestedProjectConfig = join(workspace, "personalworktree", "user", "app", ".opencode")
    await createRuntime(runtimeRoot)
    await mkdir(nestedProjectConfig, { recursive: true })
    await mkdir(join(home, ".opencode"), { recursive: true })

    const env = {
      HOME: home,
      OPENCODE_CONFIG_CONTENT: '{"theme":"dark","subagent_depth":1}',
      OPENCODE_CONFIG_DIR: configDir,
      XDG_CONFIG_HOME: xdgConfigHome,
    }

    const prepared = await prepareOfflineRuntime({ cwd: workspace, env, runtimeRoot })

    assert.equal(prepared.OPENCODE_CLIENT, "server")
    assert.equal(prepared.OPENCODE_DISABLE_AUTOUPDATE, "true")
    assert.deepEqual(JSON.parse(prepared.OPENCODE_CONFIG_CONTENT), {
      theme: "dark",
      subagent_depth: 2,
    })

    // 用户启动只处理固定配置目录；深层项目目录交给 worker 后台任务，避免阻塞每个用户进程。
    await assert.rejects(lstat(join(nestedProjectConfig, "package.json")), { code: "ENOENT" })
    assert.equal(await reconcileProjectConfigDirectories({ cwd: workspace, runtimeRoot }), 2)

    // 共享工作区根目录是个人 worktree 的共同祖先，必须包含现场临时修复使用的四个链接。
    await assertToolDependencyLinks(workspace, runtimeRoot)
    const nestedToolDirectory = join(
      workspace,
      "personalworktree",
      "user",
      "app",
      "F-BASE",
      "workspace",
      ".opencode",
      "tools",
    )
    const importProbe = join(nestedToolDirectory, "import-probe.mjs")
    await mkdir(nestedToolDirectory, { recursive: true })
    await writeFile(
      importProbe,
      'import { loaded } from "@opencode-ai/plugin"; console.log(loaded ? "IMPORT_OK" : "IMPORT_FAILED")\n',
    )
    assert.equal((await execFileAsync(process.execPath, [importProbe])).stdout, "IMPORT_OK\n")

    const effectiveDirectories = [
      join(xdgConfigHome, "opencode"),
      join(home, ".opencode"),
      configDir,
      join(workspace, ".opencode"),
      nestedProjectConfig,
    ]
    for (const directory of effectiveDirectories) {
      assert.equal(
        await readFile(join(directory, ".gitignore"), "utf8"),
        await readFile(new URL("../deploy/internal/opencode-runtime.gitignore", import.meta.url), "utf8"),
      )
      assert.equal((await lstat(join(directory, "package.json"))).isSymbolicLink(), true)
      assert.equal((await lstat(join(directory, "package-lock.json"))).isSymbolicLink(), true)
      await assertToolDependencyLinks(directory, runtimeRoot)
    }
  } finally {
    await rm(root, { force: true, recursive: true })
  }
})

test("defers recursive reconciliation to the timer", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-maintenance-"))
  let maintenance = null
  try {
    const runtimeRoot = join(root, "runtime")
    const workspace = join(root, "workspace")
    const existingConfig = join(workspace, "personalworktree", "existing-user", "app", ".opencode")
    await createRuntime(runtimeRoot)
    await mkdir(existingConfig, { recursive: true })

    // 关闭事件监听，只观察定时器路径，避免 macOS 在注册监听器时回放已有目录事件。
    maintenance = startProjectConfigMaintenance({
      cwd: workspace,
      runtimeRoot,
      scanIntervalMs: 250,
      watchEnabled: false,
    })
    await delay(50)
    await assert.rejects(lstat(join(existingConfig, "package.json")), { code: "ENOENT" })
    assert.equal((await waitForPath(join(existingConfig, "package.json"))).isSymbolicLink(), true)
  } finally {
    maintenance?.stop()
    await rm(root, { force: true, recursive: true })
  }
})

test("automatically prepares a project config created after maintenance starts", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-watch-"))
  let maintenance = null
  try {
    const runtimeRoot = join(root, "runtime")
    const workspace = join(root, "workspace")
    const createdConfig = join(workspace, "personalworktree", "new-user", "app", ".opencode")
    await createRuntime(runtimeRoot)
    await mkdir(workspace, { recursive: true })

    maintenance = startProjectConfigMaintenance({ cwd: workspace, runtimeRoot, scanIntervalMs: 60_000 })
    await mkdir(createdConfig, { recursive: true })

    assert.equal((await waitForPath(join(createdConfig, "package.json"))).isSymbolicLink(), true)
    assert.equal((await waitForPath(join(createdConfig, "package-lock.json"))).isSymbolicLink(), true)
    await assertToolDependencyLinks(createdConfig, runtimeRoot)
  } finally {
    maintenance?.stop()
    await rm(root, { force: true, recursive: true })
  }
})

test("worker entrypoint runs one background maintainer without delaying manager startup", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-worker-entrypoint-"))
  let worker = null
  let exited = null
  try {
    const programsRoot = join(root, "programs")
    const workspace = join(root, "workspace")
    const manager = join(programsRoot, "bin", "opencode-manager")
    const launcher = join(programsRoot, "opencode", "bin", "opencode")
    const managerPidFile = join(root, "manager.pid")
    const maintenancePidFile = join(root, "maintenance.pid")
    const maintenanceArgsFile = join(root, "maintenance.args")
    await mkdir(dirname(manager), { recursive: true })
    await mkdir(dirname(launcher), { recursive: true })
    await mkdir(workspace, { recursive: true })
    await writeFile(
      manager,
      `#!/usr/bin/env bash\nprintf '%s\\n' "$$" >"${managerPidFile}"\ntrap 'exit 0' INT TERM\nwhile true; do sleep 1; done\n`,
    )
    await writeFile(
      launcher,
      `#!/usr/bin/env bash\nprintf '%s\\n' "$$" >"${maintenancePidFile}"\nprintf '%s\\n' "$*" >"${maintenanceArgsFile}"\ntrap 'exit 0' INT TERM\nwhile true; do sleep 1; done\n`,
    )
    await chmod(manager, 0o755)
    await chmod(launcher, 0o755)

    worker = spawn("bash", [join(process.cwd(), "deploy/internal/opencode-worker-entrypoint.sh"), "run"], {
      cwd: workspace,
      env: { ...process.env, OPENCODE_BIN: "", TEST_AGENT_PROGRAM_ROOT: programsRoot },
      stdio: "ignore",
    })
    exited = new Promise((resolveExit) => worker.once("exit", (code, signal) => resolveExit({ code, signal })))
    await waitForPath(managerPidFile)
    await waitForPath(maintenancePidFile)
    const managerPid = Number((await readFile(managerPidFile, "utf8")).trim())
    const maintenancePid = Number((await readFile(maintenancePidFile, "utf8")).trim())
    const maintenanceArgs = (await readFile(maintenanceArgsFile, "utf8")).trim()

    worker.kill("SIGTERM")
    const result = await exited
    await waitForProcessGone(managerPid)
    await waitForProcessGone(maintenancePid)

    assert.equal(maintenanceArgs, `__maintain-project-config --root ${await realpath(workspace)}`)
    assert.equal(result.signal, null)
    assert.equal(result.code, 143)
  } finally {
    if (worker?.exitCode === null && worker?.signalCode === null) {
      worker.kill("SIGTERM")
      await Promise.race([exited ?? Promise.resolve(), delay(500)])
      if (worker.exitCode === null && worker.signalCode === null) worker.kill("SIGKILL")
    }
    await rm(root, { force: true, recursive: true })
  }
})

test("preserves existing Git ignore rules and appends runtime rules only once", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-gitignore-"))
  try {
    const runtimeRoot = join(root, "runtime")
    const workspace = join(root, "workspace")
    const workspaceConfig = join(workspace, ".opencode")
    await createRuntime(runtimeRoot)
    await mkdir(workspaceConfig, { recursive: true })
    await writeFile(join(workspaceConfig, ".gitignore"), "custom-cache/")

    const options = {
      cwd: workspace,
      env: { HOME: join(root, "home") },
      runtimeRoot,
    }
    await prepareOfflineRuntime(options)
    await prepareOfflineRuntime(options)

    const lines = (await readFile(join(workspaceConfig, ".gitignore"), "utf8")).trim().split("\n")
    assert.equal(lines[0], "custom-cache/")
    for (const rule of ["node_modules", "package.json", "package-lock.json", "bun.lock", ".gitignore"]) {
      assert.equal(lines.filter((line) => line === rule).length, 1)
    }
  } finally {
    await rm(root, { force: true, recursive: true })
  }
})

test("does not overwrite workspace-owned dependency metadata", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-existing-"))
  try {
    const runtimeRoot = join(root, "runtime")
    const workspace = join(root, "workspace")
    const workspaceConfig = join(workspace, ".opencode")
    await createRuntime(runtimeRoot)
    await mkdir(join(workspace, "node_modules", "zod"), { recursive: true })
    await mkdir(join(workspaceConfig, "node_modules", "effect"), { recursive: true })
    await writeFile(join(workspaceConfig, "package.json"), '{"name":"workspace-owned"}\n')

    await prepareOfflineRuntime({
      cwd: workspace,
      env: { HOME: join(root, "home") },
      runtimeRoot,
    })

    assert.equal(await readFile(join(workspaceConfig, "package.json"), "utf8"), '{"name":"workspace-owned"}\n')
    assert.equal((await lstat(join(workspace, "node_modules", "zod"))).isDirectory(), true)
    assert.equal((await lstat(join(workspaceConfig, "node_modules", "effect"))).isDirectory(), true)
  } finally {
    await rm(root, { force: true, recursive: true })
  }
})

test("rejects invalid inherited OPENCODE_CONFIG_CONTENT instead of discarding it", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-invalid-"))
  try {
    const runtimeRoot = join(root, "runtime")
    await createRuntime(runtimeRoot)
    await assert.rejects(
      prepareOfflineRuntime({
        cwd: root,
        env: { HOME: root, OPENCODE_CONFIG_CONTENT: "{invalid" },
        runtimeRoot,
      }),
      /OPENCODE_CONFIG_CONTENT/,
    )
  } finally {
    await rm(root, { force: true, recursive: true })
  }
})

test("does not inject unsupported subagent depth into the 1.17 rollback runtime", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-legacy-"))
  try {
    const runtimeRoot = join(root, "runtime")
    await createRuntime(runtimeRoot)
    await writeFile(join(runtimeRoot, "VERSION"), "1.17.8\n")

    const prepared = await prepareOfflineRuntime({
      cwd: root,
      env: {
        HOME: join(root, "home"),
        OPENCODE_CONFIG_CONTENT: '{"theme":"dark","subagent_depth":2}',
      },
      runtimeRoot,
    })

    assert.deepEqual(JSON.parse(prepared.OPENCODE_CONFIG_CONTENT), { theme: "dark" })
  } finally {
    await rm(root, { force: true, recursive: true })
  }
})

test("executes the official binary when invoked through the installed symlink", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-symlink-"))
  try {
    const launcher = new URL("../deploy/internal/opencode-official-launcher.mjs", import.meta.url)
    const launcherLink = join(root, "opencode")
    const officialBinary = join(root, "opencode-official")
    await symlink(launcher, launcherLink)
    await writeFile(
      officialBinary,
      '#!/usr/bin/env node\nprocess.stdout.write(`official:${process.argv.slice(2).join(",")}\\n`)\n',
    )
    await chmod(officialBinary, 0o755)

    const { stdout } = await execFileAsync(process.execPath, [launcherLink, "--version"], {
      env: { ...process.env, OPENCODE_REAL_BIN: officialBinary },
    })
    assert.equal(stdout, "official:--version\n")
  } finally {
    await rm(root, { force: true, recursive: true })
  }
})
