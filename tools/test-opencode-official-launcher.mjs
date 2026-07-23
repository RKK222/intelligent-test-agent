#!/usr/bin/env node

import assert from "node:assert/strict"
import { execFile } from "node:child_process"
import { chmod, copyFile, lstat, mkdtemp, mkdir, readFile, readlink, rm, symlink, writeFile } from "node:fs/promises"
import { tmpdir } from "node:os"
import { join } from "node:path"
import test from "node:test"
import { promisify } from "node:util"

import { prepareOfflineRuntime } from "../deploy/internal/opencode-official-launcher.mjs"

const execFileAsync = promisify(execFile)

async function createRuntime(root) {
  await mkdir(join(root, "node_modules", "@opencode-ai", "plugin"), { recursive: true })
  await mkdir(join(root, "node_modules", "@opencode-ai", "sdk"), { recursive: true })
  await mkdir(join(root, "node_modules", "effect"), { recursive: true })
  await mkdir(join(root, "node_modules", "zod"), { recursive: true })
  await writeFile(join(root, "package.json"), '{"private":true}\n')
  await writeFile(join(root, "package-lock.json"), '{"lockfileVersion":3}\n')
  await copyFile(
    new URL("../deploy/internal/opencode-runtime.gitignore", import.meta.url),
    join(root, "opencode-runtime.gitignore"),
  )
  await writeFile(join(root, "VERSION"), "1.18.4\n")
}

test("prepares every effective config directory for offline tools and enforces depth two", async () => {
  const root = await mkdtemp(join(tmpdir(), "opencode-official-launcher-"))
  try {
    const runtimeRoot = join(root, "runtime")
    const workspace = join(root, "workspace")
    const configDir = join(root, "public-config")
    const xdgConfigHome = join(root, "xdg-config")
    await createRuntime(runtimeRoot)
    await mkdir(workspace, { recursive: true })

    const env = {
      HOME: join(root, "home"),
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

    const effectiveDirectories = [
      join(xdgConfigHome, "opencode"),
      configDir,
      join(workspace, ".opencode"),
    ]
    for (const directory of effectiveDirectories) {
      assert.equal(
        await readFile(join(directory, ".gitignore"), "utf8"),
        await readFile(new URL("../deploy/internal/opencode-runtime.gitignore", import.meta.url), "utf8"),
      )
      assert.equal((await lstat(join(directory, "package.json"))).isSymbolicLink(), true)
      assert.equal((await lstat(join(directory, "package-lock.json"))).isSymbolicLink(), true)
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
  } finally {
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
    await mkdir(join(workspaceConfig, "node_modules", "effect"), { recursive: true })
    await writeFile(join(workspaceConfig, "package.json"), '{"name":"workspace-owned"}\n')

    await prepareOfflineRuntime({
      cwd: workspace,
      env: { HOME: join(root, "home") },
      runtimeRoot,
    })

    assert.equal(await readFile(join(workspaceConfig, "package.json"), "utf8"), '{"name":"workspace-owned"}\n')
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
