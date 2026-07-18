import { afterEach, describe, expect, test } from "bun:test"
import path from "path"
import { applicationSharedLocation } from "@/config/paths"

const previousApplicationRoot = process.env.OPENCODE_APP_WORKSPACE_ROOT
const previousPersonalRoot = process.env.OPENCODE_PERSONAL_WORKTREE_ROOT

afterEach(() => {
  if (previousApplicationRoot === undefined) delete process.env.OPENCODE_APP_WORKSPACE_ROOT
  else process.env.OPENCODE_APP_WORKSPACE_ROOT = previousApplicationRoot
  if (previousPersonalRoot === undefined) delete process.env.OPENCODE_PERSONAL_WORKTREE_ROOT
  else process.env.OPENCODE_PERSONAL_WORKTREE_ROOT = previousPersonalRoot
})

describe("applicationSharedLocation", () => {
  test("maps a managed personal worktree to the application shared replica", () => {
    process.env.OPENCODE_APP_WORKSPACE_ROOT = "/data/appworkspace"
    process.env.OPENCODE_PERSONAL_WORKTREE_ROOT = "/data/personalworktree"
    const worktree = "/data/personalworktree/20260618/usr_test/coss/feature_usr_default"

    expect(applicationSharedLocation(path.join(worktree, "F-COSS/workspace"), worktree)).toEqual({
      directory: "/data/appworkspace/20260618/coss/F-COSS/workspace",
      worktree: "/data/appworkspace/20260618/coss",
    })
  })

  test("rejects directories outside the trusted personal root", () => {
    process.env.OPENCODE_APP_WORKSPACE_ROOT = "/data/appworkspace"
    process.env.OPENCODE_PERSONAL_WORKTREE_ROOT = "/data/personalworktree"

    expect(applicationSharedLocation("/tmp/project", "/tmp/project")).toBeUndefined()
  })
})
