# Draggable Robot and Process Status Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the idle MIMO robot subtly colorful and user-positionable, and make the expanded TestAgent process card appear at the dragged status-dot location.

**Architecture:** Extend the two current UI owners rather than creating new components: `FigmaShell.vue` owns robot animation and position preference, while `FigmaChatPanel.vue` owns the process-dot preference and anchored card. Both interactions use bounded viewport coordinates and browser-local persistence only; no API, event, or data-model changes are needed.

**Tech Stack:** Vue 3 Composition API, TypeScript, scoped CSS, Vitest, Vue Test Utils, jsdom.

---

## Chunk 1: MIMO 小宠物

### Task 1: Lock down draggable robot behavior with tests

**Files:**
- Modify: `frontend/apps/agent-web/tests/FigmaShell.test.ts`
- Modify: `frontend/apps/agent-web/src/components/FigmaShell.vue: robot state and SVG section`

- [x] **Step 1: Write failing component tests**

Extend the existing `FigmaShell` tests without replacing current coverage. Use fake timers, clear `localStorage`, and mock focused/visible document state in setup; restore timers and globals in teardown. Add isolated tests that assert cyan/violet accent classes, a drag beyond the threshold saves root-element `{ x, y }` coordinates and pauses autonomous behavior, a saved position restores after remount, a resize reclamps and persists the saved value, below-threshold pointer movement does not pause autonomous behavior, and read/write storage failures safely retain the default or in-memory position.

- [x] **Step 2: Run test to verify RED**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts`

Expected: FAIL because no colored accent or drag-persistence behavior exists.

- [x] **Step 3: Add the smallest robot position controller**

In `FigmaShell.vue`, add named robot width, height, viewport-margin, and drag-threshold constants; persist root-element `left/top` coordinates (not the existing state’s pre-offset Y value) and use that same convention in style binding, load, resize, and tests. Use guarded storage read/write helpers so blocked storage retains the default or current in-memory position without throwing. Add manual-placement state, pointer-drag bookkeeping, pointer capture/release, and resize handling. On a drag beyond the threshold, clear current behavior timers, save the clamped coordinate, and leave the robot idle instead of scheduling another behavior. Guard activity, inactivity, and exit scheduling while manual placement is active so global mouse movement cannot remove it during a drag. Restore a saved position as a static idle robot; otherwise preserve the current random spawn/action flow. During only an active drag, set `document.body` to `grabbing`/nonselectable; restore both styles and release capture on pointerup, pointercancel, and unmount. Apply muted cyan and violet SVG accent classes without replacing the current SVG structure. Add Chinese comments for the coordinate contract, behavior-pause rule, and listener/body-style teardown.

- [x] **Step 4: Run test to verify GREEN**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts`

Expected: PASS.

## Chunk 2: 进程绿点的锚定展开卡

### Task 2: Add failing tests for anchored process-card placement

**Files:**
- Modify: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts: process-status test block`
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue: process dot and status card section`

- [x] **Step 1: Write failing tests**

For a READY process status, use Pointer Events to drag the visible dot beyond its threshold, assert the persisted `figma-chat-process-dot-pos`, click it, and assert that the status card receives fixed `left`/`top` coordinates adjacent to that location. Mock and restore `window.innerWidth`, `window.innerHeight`, and the card’s `getBoundingClientRect` so a bottom-right case can assert horizontal/vertical flipping and an expanded-card resize case can assert reclamping inside the new viewport. Also assert the card’s fixed positioning so it cannot reserve chat-layout space.

- [x] **Step 2: Run test to verify RED**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaChatPanel.test.ts`

Expected: FAIL because the expanded card has no fixed anchor style and remains in normal chat layout.

- [x] **Step 3: Implement one shared placement calculation**

In `FigmaChatPanel.vue`, add a card element ref, measured card-size state, and one computed placement calculation based on the existing dot coordinate. Prefer right/bottom, flip on unavailable space, then clamp to viewport margins. After `nextTick`, measure `getBoundingClientRect`; attach a `ResizeObserver` when available to remeasure on card content changes, and disconnect it on collapse/unmount. Also remeasure and recompute on window resize. Bind the resulting fixed style to the expanded card. Give the floating status dot a distinct class from timeline dots so later timeline CSS cannot override its geometry. Preserve initialization-button event suppression and current keyboard collapse behavior. Add Chinese comments for the dot-coordinate contract, size-observer lifecycle, and edge-flip priority.

- [x] **Step 4: Run test to verify GREEN**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaChatPanel.test.ts`

Expected: PASS.

## Chunk 3: Documentation and full verification

### Task 3: Record behavior and verify the runnable frontend

**Files:**
- Modify: `frontend/apps/agent-web/README.md: interaction behavior summary`
- Modify: `.agents/session-log.md: newest entry`

- [x] **Step 1: Update stable documentation**

Add one concise README bullet documenting browser-local positioning for the MIMO pet and TestAgent process status card, including that no API/event contract changes are introduced.

- [x] **Step 2: Run targeted and workspace checks**

Run:

```bash
cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/FigmaChatPanel.test.ts
cd frontend && corepack pnpm lint
cd frontend && corepack pnpm typecheck
cd frontend && corepack pnpm build
```

Expected: all commands exit 0.

- [ ] **Step 3: Start the runnable UI and inspect the interaction**

Run from repository root:

```bash
./restart-dev-services.sh --profile test --env-file .env.test
```

Expected: frontend dev server starts and its URL is printed in `.tmp/dev-services/` logs. Verify: wait for/show the pet, drag it, refresh to confirm it remains static at the saved location, then resize the viewport to confirm it remains clamped. Drag the green process dot to right-bottom, right-top, and left-bottom; expand it at each location and confirm the card chooses the visible direction. Resize once while expanded and confirm the card remains fully in the viewport.

- [x] **Step 4: Add the session entry and make one final commit**

Before staging, reread the recent `.agents/session-log.md` entries and complete the applicable items in `docs/guides/self-checklist.md`. Record actual commands, outcomes, running URL/log path, and any remaining verification risk in `.agents/session-log.md`, then commit only this task’s files using a Chinese message.
