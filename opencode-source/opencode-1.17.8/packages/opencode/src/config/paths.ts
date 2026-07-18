export * as ConfigPaths from "./paths"

import path from "path"
import { Flag } from "@opencode-ai/core/flag/flag"
import { Global } from "@opencode-ai/core/global"
import { unique } from "remeda"
import * as Effect from "effect/Effect"
import { FSUtil } from "@opencode-ai/core/fs-util"

function configuredPath(name: string) {
  const value = process.env[name]?.trim()
  return value ? path.resolve(value) : undefined
}

function isWithin(root: string, candidate: string) {
  const relative = path.relative(root, candidate)
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative))
}

/**
 * 平台个人 worktree 的固定结构为 version/user/repository/branch。只在受信根内完成映射，
 * 把当前个人目录换算到同版本应用共享副本；任何越界或非平台目录都不启用应用共享层。
 */
export function applicationSharedLocation(directory: string, worktree?: string) {
  const personalRoot = configuredPath("OPENCODE_PERSONAL_WORKTREE_ROOT")
  const applicationRoot = configuredPath("OPENCODE_APP_WORKSPACE_ROOT")
  if (!personalRoot || !applicationRoot || !worktree) return undefined

  const resolvedWorktree = path.resolve(worktree)
  const resolvedDirectory = path.resolve(directory)
  if (!isWithin(personalRoot, resolvedWorktree) || !isWithin(resolvedWorktree, resolvedDirectory)) return undefined

  const segments = path.relative(personalRoot, resolvedWorktree).split(path.sep).filter(Boolean)
  if (segments.length !== 4) return undefined
  const [version, , repository] = segments
  const applicationWorktree = path.resolve(applicationRoot, version, repository)
  if (!isWithin(applicationRoot, applicationWorktree)) return undefined
  return {
    directory: path.resolve(applicationWorktree, path.relative(resolvedWorktree, resolvedDirectory)),
    worktree: applicationWorktree,
  }
}

export const files = Effect.fn("ConfigPaths.projectFiles")(function* (
  name: string,
  directory: string,
  worktree?: string,
) {
  const afs = yield* FSUtil.Service
  return (yield* afs.up({
    targets: [`${name}.jsonc`, `${name}.json`],
    start: directory,
    stop: worktree,
  })).toReversed()
})

export const directories = Effect.fn("ConfigPaths.directories")(function* (directory: string, worktree?: string) {
  const afs = yield* FSUtil.Service
  const application = applicationSharedLocation(directory, worktree)
  const applicationDirectories = application
    ? (yield* afs.up({ targets: [".opencode"], start: application.directory, stop: application.worktree })).toReversed()
    : []
  const projectDirectories = !Flag.OPENCODE_DISABLE_PROJECT_CONFIG
    ? (yield* afs.up({ targets: [".opencode"], start: directory, stop: worktree })).toReversed()
    : []
  const publicPersonal = configuredPath("OPENCODE_PUBLIC_PERSONAL_CONFIG_DIR")
  return unique([
    Global.Path.config,
    ...(yield* afs.up({
      targets: [".opencode"],
      start: Global.Path.home,
      stop: Global.Path.home,
    })),
    ...(Flag.OPENCODE_CONFIG_DIR ? [Flag.OPENCODE_CONFIG_DIR] : []),
    ...(publicPersonal ? [publicPersonal] : []),
    ...applicationDirectories,
    ...projectDirectories,
  ])
})

export function fileInDirectory(dir: string, name: string) {
  return [path.join(dir, `${name}.json`), path.join(dir, `${name}.jsonc`)]
}
