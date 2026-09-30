#!/usr/bin/env node

/**
 * OpenCode 2.0.18 原生 smoke。脚本只访问 worker 原生 API，不绕过 TestAgent 平台；
 * 密码只从环境读取，输出只包含状态、事件类型和数量，不打印响应正文。
 *
 * 用法：
 *   TEST_AGENT_OPENCODE_SERVER_PASSWORD='...' \
 *   OPENCODE_BASE_URL=http://127.0.0.1:4296 \
 *   node tools/test-opencode-v2-native-smoke.mjs
 */

import { execFile } from "node:child_process";
import { mkdtemp, realpath, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { promisify } from "node:util";
import { join } from "node:path";

const exec = promisify(execFile);
const baseUrl = (process.env.OPENCODE_BASE_URL ?? "http://127.0.0.1:4296").replace(/\/$/, "");
const password = process.env.TEST_AGENT_OPENCODE_SERVER_PASSWORD ?? process.env.OPENCODE_PASSWORD;
const auth = password
  ? { Authorization: `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}` }
  : {};
const requestTimeoutMs = Number(process.env.OPENCODE_NATIVE_SMOKE_TIMEOUT_MS ?? 180_000);

function endpoint(pathname, query = {}) {
  const url = new URL(pathname, `${baseUrl}/`);
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null) url.searchParams.set(key, String(value));
  }
  return url;
}

async function request(pathname, { method = "GET", query, body, timeoutMs = requestTimeoutMs } = {}) {
  const headers = { ...auth, Accept: "application/json" };
  const init = { method, headers, signal: AbortSignal.timeout(timeoutMs) };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(body);
  }
  const response = await fetch(endpoint(pathname, query), init);
  const contentType = response.headers.get("content-type") ?? "";
  const raw = await response.text();
  let data;
  if (raw && contentType.includes("json")) {
    try {
      data = JSON.parse(raw);
    } catch {
      throw new Error(`OpenCode V2 returned invalid JSON: ${response.status}`);
    }
  }
  if (!response.ok) {
    throw new Error(`OpenCode V2 ${method} ${pathname} failed: HTTP ${response.status}`);
  }
  return { status: response.status, contentType, data, raw };
}

function payloadData(result) {
  if (result?.data && typeof result.data === "object" && !Array.isArray(result.data) && "data" in result.data) {
    return result.data.data;
  }
  return result?.data;
}

function expectStatus(result, expected, label) {
  if (result.status !== expected) throw new Error(`${label}: expected HTTP ${expected}, got ${result.status}`);
}

function expectObject(value, label) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error(`${label}: expected object`);
  return value;
}

function expectArray(value, label) {
  if (!Array.isArray(value)) throw new Error(`${label}: expected array`);
  return value;
}

function sessionId(result, label) {
  const value = expectObject(payloadData(result), label);
  if (typeof value.id !== "string" || !value.id.startsWith("ses")) throw new Error(`${label}: missing session id`);
  return value.id;
}

async function initializeWorkspace() {
  const workspace = await mkdtemp(join(tmpdir(), "testagent-opencode-v2-native-"));
  try {
    await writeFile(join(workspace, "probe.txt"), "V2_NATIVE_FILE_OK\n", "utf8");
    await exec("git", ["init", "-q", workspace]);
    await exec("git", ["-C", workspace, "add", "probe.txt"]);
    await exec("git", ["-C", workspace, "-c", "user.name=OpenCode V2 Smoke", "-c", "user.email=smoke@example.invalid", "commit", "-q", "-m", "probe"]);
    return workspace;
  } catch (error) {
    await rm(workspace, { recursive: true, force: true });
    throw error;
  }
}

async function readEvents(signal, eventTypes) {
  const response = await fetch(endpoint("/api/event"), { headers: auth, signal });
  if (!response.ok || !response.body) throw new Error(`OpenCode V2 event stream failed: HTTP ${response.status}`);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (!signal.aborted) {
    const next = await reader.read();
    if (next.done) break;
    buffer += decoder.decode(next.value, { stream: true });
    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";
    for (const frame of frames) {
      for (const line of frame.split("\n")) {
        if (!line.startsWith("data: ")) continue;
        try {
          const event = JSON.parse(line.slice(6));
          if (typeof event.type === "string") eventTypes.push(event.type);
        } catch {
          // 心跳或未知 SSE frame 不影响 smoke；业务事件仍按 type 记录。
        }
      }
    }
  }
  reader.releaseLock();
}

async function waitForAssistant(session, userMessageId) {
  const deadline = Date.now() + requestTimeoutMs;
  while (Date.now() < deadline) {
    const result = await request(`/api/session/${encodeURIComponent(session)}/message`, {
      query: { order: "asc", limit: 200 },
    });
    const messages = expectArray(payloadData(result), "session messages");
    const assistant = messages.find((message) => message?.type === "assistant");
    if (assistant) return { messages, assistant };
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  throw new Error(`OpenCode V2 prompt did not produce an assistant message for ${userMessageId}`);
}

async function waitForEventType(eventTypes, type) {
  const deadline = Date.now() + requestTimeoutMs;
  while (Date.now() < deadline) {
    if (eventTypes.includes(type)) return;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`OpenCode V2 event was not observed: ${type}`);
}

async function main() {
  const workspace = await initializeWorkspace();
  const sessions = new Set();
  const eventTypes = [];
  const eventAbort = new AbortController();
  let eventError;
  const eventTask = readEvents(eventAbort.signal, eventTypes).catch((error) => {
    if (!eventAbort.signal.aborted) eventError = error;
  });
  try {
    const info = await request("/api/info");
    const infoData = expectObject(info.data, "info");
    if (infoData.version !== "2.0.18") throw new Error(`Expected OpenCode 2.0.18, got ${String(infoData.version)}`);

    const location = { "location[directory]": workspace };
    const readOnlyRoutes = [
      ["/api/location", location], ["/api/agent", location], ["/api/model", location],
      ["/api/provider", location], ["/api/config", location], ["/api/command", location],
      ["/api/reference", location], ["/api/mcp", location], ["/api/session/active", location],
      ["/api/fs/list", { ...location, path: "." }], ["/api/fs/find", { ...location, query: "probe" }],
      ["/api/vcs", location], ["/api/vcs/status", location], ["/api/vcs/branch", location],
      ["/api/vcs/diff", { ...location, mode: "working" }], ["/api/pty", location],
    ];
    for (const [path, query] of readOnlyRoutes) expectStatus(await request(path, { query }), 200, path);
    const file = await request("/api/fs/read/probe.txt", { query: location });
    expectStatus(file, 200, "/api/fs/read/*");
    if (!file.raw.includes("V2_NATIVE_FILE_OK")) throw new Error("V2 file read did not return the probe content");

    const projectResult = await request("/api/project", { query: location });
    const projects = expectArray(projectResult.data, "/api/project");
    const workspaceCanonical = await realpath(workspace);
    const project = projects.find((item) => item?.canonical === workspaceCanonical);
    if (typeof project?.id !== "string") throw new Error("OpenCode V2 did not register the smoke workspace project");
    expectStatus(await request("/api/worktree", { query: { projectID: project.id } }), 200, "/api/worktree");

    const created = await request("/api/session", { method: "POST", body: { title: "V2 native smoke", location: { directory: workspace } } });
    const session = sessionId(created, "session.create");
    sessions.add(session);
    const fetchedSession = await request(`/api/session/${session}`);
    expectStatus(fetchedSession, 200, "session.get");
    if (sessionId(fetchedSession, "session.get") !== session) throw new Error("OpenCode V2 returned a different session");
    expectStatus(await request(`/api/session/${session}/shell`, { method: "POST", body: { command: "printf V2_NATIVE_SHELL_OK" } }), 204, "session.shell");
    expectStatus(await request(`/api/experimental/session/${session}/wait`, { method: "POST", body: {} }), 204, "session.wait");
    const shellHistory = await request(`/api/session/${session}/message`, { query: { order: "asc", limit: 200 } });
    const shellMessages = expectArray(payloadData(shellHistory), "shell messages");
    if (!shellMessages.some((message) => message?.type === "shell" && message?.output?.output === "V2_NATIVE_SHELL_OK")) {
      throw new Error("OpenCode V2 shell output was not persisted in session messages");
    }

    const prompt = await request(`/api/session/${session}/prompt`, { method: "POST", body: { text: "Reply exactly V2_NATIVE_OK." } });
    expectStatus(prompt, 200, "session.prompt");
    const user = expectObject(payloadData(prompt), "prompt response");
    const userMessageId = user.id;
    const completed = await waitForAssistant(session, userMessageId);
    if (completed.messages.filter((message) => message?.type === "assistant").length === 0) throw new Error("No assistant message");
    expectStatus(await request(`/api/session/${session}/diff`, { query: { from: userMessageId } }), 200, "session.diff");

    const fork = await request(`/api/session/${session}/fork`, { method: "POST", body: { before: userMessageId } });
    const forkId = sessionId(fork, "session.fork");
    sessions.add(forkId);
    expectStatus(await request(`/api/session/${session}/revert/stage`, { method: "POST", body: { messageID: userMessageId, files: false } }), 200, "session.revert.stage");
    expectStatus(await request(`/api/session/${session}/revert/commit`, { method: "POST" }), 204, "session.revert.commit");

    // 空 session 的 compact 可能合法返回 200 但产生 compaction.failed；先准备一轮
    // assistant 消息，再核对真实的 session.compaction.ended，避免把接受请求误判为完成。
    const compact = await request("/api/session", { method: "POST", body: { title: "V2 compact smoke", location: { directory: workspace } } });
    const compactSession = sessionId(compact, "compact session.create");
    sessions.add(compactSession);
    const compactPrompt = await request(`/api/session/${compactSession}/prompt`, {
      method: "POST",
      body: { text: "Reply exactly V2_COMPACT_PREPARED." },
    });
    const compactUser = expectObject(payloadData(compactPrompt), "compact prompt response");
    await waitForAssistant(compactSession, compactUser.id);
    expectStatus(await request(`/api/session/${compactSession}/compact`, { method: "POST", body: {} }), 200, "session.compact");
    await waitForEventType(eventTypes, "session.compaction.ended");

    const interrupt = await request("/api/session", { method: "POST", body: { title: "V2 interrupt smoke", location: { directory: workspace } } });
    const interruptSession = sessionId(interrupt, "interrupt session.create");
    sessions.add(interruptSession);
    expectStatus(await request(`/api/session/${interruptSession}/interrupt`, { method: "POST" }), 200, "session.interrupt");
    expectStatus(await request(`/api/experimental/session/${interruptSession}/wait`, { method: "POST" }), 204, "interrupt session.wait");

    await new Promise((resolve) => setTimeout(resolve, 200));
    eventAbort.abort();
    await eventTask;
    if (eventError) throw eventError;
    const requiredEvents = ["session.created", "session.execution.started", "session.execution.succeeded"];
    for (const event of requiredEvents) if (!eventTypes.includes(event)) throw new Error(`Missing V2 SSE event: ${event}`);
    console.log(JSON.stringify({
      version: infoData.version,
      readOnlyRoutes: readOnlyRoutes.length + 2,
      sessions: sessions.size,
      promptAssistant: true,
      eventTypes: [...new Set(eventTypes)].sort(),
    }));
  } finally {
    eventAbort.abort();
    await eventTask.catch(() => undefined);
    for (const session of sessions) await request(`/api/session/${encodeURIComponent(session)}`, { method: "DELETE" }).catch(() => undefined);
    await rm(workspace, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : "OpenCode V2 native smoke failed");
  process.exitCode = 1;
});
