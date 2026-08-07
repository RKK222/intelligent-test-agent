# Local GLM 5.2 OpenCode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure the current local `test` platform so `enterprise-glm/glm-5.2` is selectable and usable in the platform conversation UI.

**Architecture:** Store the upstream secret and Base URL through the existing internal-model Token/Provider management boundary, then publish a secret-free OpenCode Provider/model entry through the existing public configuration workflow. Reload the current user OpenCode runtime and verify the native model catalog plus one minimal conversation.

**Tech Stack:** Vue 3 admin UI, Spring WebFlux management APIs, PostgreSQL runtime configuration, OpenCode 1.18.4 JSONC public configuration, local opencode-manager.

## Global Constraints

- Apply only to the current local `test` platform; do not change enterprise deployment templates.
- Use internal provider ID `glm-prod`, OpenCode Provider ID `enterprise-glm`, model ID `glm-5.2`, and display name `GLM 5.2`.
- Candidate upstream Base URL is `https://api.kjdfhl.school/v1`; verify it before publishing.
- Do not change the existing default model or existing Provider order beyond appending `enterprise-glm`.
- The user-provided API Key must not enter this repository, public configuration Git, OpenCode JSONC, `.env*`, browser persistence, logs, commit messages, or reports.
- Do not modify generated SDK files or `opencode-source/opencode-1.18.4/`.
- Use platform management and rollout paths; do not write the database directly or edit a user runtime directory directly.
- Preserve unrelated working-tree changes in `BatchTestCaseGenerationDialog.vue` and its test.

---

### Task 1: Verify the upstream and configure the local internal Provider

**Files:**
- Runtime data only: local `internal_model_tokens` and `internal_model_providers` through existing management APIs.
- Inspect: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/InternalModelTokenManagementController.java`
- Inspect: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/InternalModelProviderManagementController.java`

**Interfaces:**
- Consumes: the user-provided API Key from the current request, without persisting it outside the protected management request.
- Produces: enabled Provider `glm-prod` with Base URL `https://api.kjdfhl.school/v1` and an associated configured Token.

- [ ] **Step 1: Confirm the existing management contract and current local service health**

Run read-only checks:

```bash
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
curl -fsS http://127.0.0.1:3000/
```

Expected: backend readiness reports `UP`; frontend returns HTML.

- [ ] **Step 2: Verify the upstream catalog without logging the secret**

Use the signed-in browser/admin workflow or a process-local secret input that does not echo command arguments. Request:

```http
GET https://api.kjdfhl.school/v1/models
Authorization: Bearer <the user-provided API Key from the current request>
```

Expected: HTTP 200 OpenAI-compatible model list containing exact ID `glm-5.2`. If `/models` is unsupported, send one non-streaming `POST /v1/chat/completions` with `model=glm-5.2`, `max_tokens=1`, and a non-sensitive one-word prompt. Stop on authentication failure, missing model, or incompatible response shape.

- [ ] **Step 3: Create or reuse a protected Token definition**

In “系统管理 → 内部模型供应商”, create a local Token definition named `GLM 5.2 本地` with the user-provided Key. If that exact local Token definition already exists, rotate its value through the edit form rather than creating a duplicate.

Expected: the returned/listed Token record exposes only ID, name, reference count and timestamps; no secret value is returned.

- [ ] **Step 4: Create or update the internal Provider**

Create/update the Provider with the exact values:

```json
{
  "providerId": "glm-prod",
  "name": "GLM 5.2",
  "baseUrl": "https://api.kjdfhl.school/v1",
  "enabled": true,
  "tokenId": "<ID returned for the protected GLM 5.2 本地 Token>"
}
```

The `tokenId` expression denotes the concrete server-returned identifier and is not a value to invent or store in the repository.

Expected: Provider snapshot shows `glm-prod`, enabled, configured Token state, and no Token value.

### Task 2: Publish the local OpenCode Provider and model

**Files:**
- Modify through platform public-config management: local public configuration Git file `opencode/opencode.jsonc` (the mounted OpenCode directory presents it as `opencode.jsonc`).
- Do not modify: `deploy/internal/opencode.jsonc.example`.
- Do not modify: `deploy/internal/opencode-models.json`.

**Interfaces:**
- Consumes: internal Provider route key `glm-prod` from Task 1.
- Produces: secret-free OpenCode catalog entry `enterprise-glm/glm-5.2` in the current local public configuration.

- [ ] **Step 1: Open the current local public worktree and preserve existing configuration**

Use “系统管理 → 配置管理 → opencode公共配置管理” to mount or create the current super administrator's stable public worktree on the initialized local server. Open `opencode.jsonc` and retain all existing top-level fields, Providers, models, MCP definitions and comments.

- [ ] **Step 2: Add the Provider to the whitelist**

Append `enterprise-glm` to the existing `enabled_providers` array. Do not remove or reorder existing entries and do not change `model` or `small_model`.

- [ ] **Step 3: Add the secret-free OpenCode Provider block**

Add this Provider under the existing top-level `provider` object:

```jsonc
"enterprise-glm": {
  "name": "企业 GLM",
  "npm": "@ai-sdk/openai-compatible",
  "api": "{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}",
  "env": [
    "TEST_AGENT_INTERNAL_PROXY_API_KEY",
    "TEST_AGENT_INTERNAL_PROXY_BASE_URL",
    "ENTERPRISE_UCID"
  ],
  "options": {
    "baseURL": "{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}",
    "apiKey": "{env:TEST_AGENT_INTERNAL_PROXY_API_KEY}",
    "includeUsage": false,
    "timeout": false,
    "headerTimeout": 30000,
    "chunkTimeout": 120000,
    "headers": {
      "X-Enterprise-Model-Provider": "glm-prod",
      "ucid": "{env:ENTERPRISE_UCID}"
    }
  },
  "models": {
    "glm-5.2": {
      "name": "GLM 5.2",
      "id": "glm-5.2",
      "reasoning": true,
      "interleaved": { "field": "reasoning_content" },
      "tool_call": true,
      "temperature": true,
      "limit": {
        "context": 128000,
        "output": 8192
      }
    }
  }
}
```

If Task 1 returns trustworthy numeric context/output limits, replace only `128000`/`8192` with those exact values before saving. Do not infer limits from the model name.

- [ ] **Step 4: Validate, commit and publish through the platform workflow**

Save the JSONC, inspect the public Git diff, stage only `opencode.jsonc`, commit with Chinese message `新增本地GLM 5.2模型配置`, and publish through the existing public rollout action.

Expected: publish succeeds; current local shared public configuration points to the new commit; no secret-like value appears in the diff.

### Task 3: Reload OpenCode and verify selection plus one conversation

**Files:**
- Runtime state only: current user's local OpenCode process, managed by the platform.
- Inspect logs only: `.tmp/dev-services/backend.log`, `.tmp/dev-services/opencode-manager.log`, and the current user OpenCode process log resolved by manager state.

**Interfaces:**
- Consumes: published public configuration containing `enterprise-glm/glm-5.2`.
- Produces: verified model visibility and one successful platform Run using that exact selection.

- [ ] **Step 1: Reload the current user's runtime through the platform**

Use the existing public-config reload action for the current user. If the platform requires a process restart, use the existing UI restart control so the backend follows `OpencodeProcessStopService`, `OpencodeProcessStartupService`, and `OpencodeProcessStatusQueryService`.

Expected: current user's service status returns `READY` and public-config rollout gate is open.

- [ ] **Step 2: Verify the native effective config and catalogs**

Through the platform runtime APIs/UI, verify:

```text
enabled_providers contains enterprise-glm
provider catalog contains enterprise-glm
model catalog contains enterprise-glm/glm-5.2
```

Expected: all three conditions are true without adding Zen or changing default selection.

- [ ] **Step 3: Verify the platform model picker**

Open the conversation model picker and select `GLM 5.2` under `企业 GLM`.

Expected: selected runtime value is `enterprise-glm/glm-5.2` and remains selected after the catalog settles.

- [ ] **Step 4: Run a minimal non-sensitive conversation**

Send `只回复 OK` in a disposable/new local conversation.

Expected: the Run reaches success, returns a non-empty assistant response, and the Run/model metadata remains `enterprise-glm/glm-5.2`.

- [ ] **Step 5: Scan logs for accidental secret exposure**

Use an in-memory exact comparison against the user-provided secret without printing matching lines or the secret itself. Report only match counts per inspected log.

Expected: zero matches in backend, manager, OpenCode and frontend logs.

### Task 4: Record the local configuration result and commit repository documentation

**Files:**
- Modify: `.agents/session-log.huangzhenren.md`
- Preserve: `frontend/apps/agent-web/src/components/BatchTestCaseGenerationDialog.vue`
- Preserve: `frontend/apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts`

**Interfaces:**
- Consumes: verification evidence from Tasks 1–3.
- Produces: a secret-free local handoff record and a final repository commit containing only this task's documentation.

- [ ] **Step 1: Add one session-level handoff entry**

Append one `Why / What / How / Result` entry that records Provider/model IDs, local-only scope, verification commands/outcomes, API/event/database/security/compatibility impact and any remaining risk. State only that the Token is configured; do not record the Key or any fragment of it.

- [ ] **Step 2: Run repository safety checks**

Run:

```bash
git diff --check
git status --short
```

Review all `.agents/session-log*.md` recent entries again before staging. Confirm the two unrelated frontend modifications remain unstaged and unmodified by this task.

- [ ] **Step 3: Stage and commit only this task's handoff**

Run:

```bash
git add .agents/session-log.huangzhenren.md
git diff --cached --check
git commit -m "记录本地GLM 5.2模型配置结果"
```

Expected: commit succeeds; unrelated frontend files remain modified but unstaged.
