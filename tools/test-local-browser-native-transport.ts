import { afterAll, beforeAll, expect, test } from "bun:test"
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises"
import { tmpdir } from "node:os"
import { join, resolve } from "node:path"
import { pathToFileURL } from "node:url"

let fixtureRoot = ""
let versionRequests = 0
let server: ReturnType<typeof Bun.serve>

beforeAll(async () => {
  fixtureRoot = await mkdtemp(join(tmpdir(), "testagent-local-browser-transport."))
  const nodeModules = join(fixtureRoot, "node_modules")
  await writeFixturePackage(
    nodeModules,
    "playwright-core",
    "export const chromium = {}\n",
  )
  await writeFixturePackage(
    nodeModules,
    "@opencode-ai/plugin",
    [
      "const chain = new Proxy(function () { return chain }, {",
      "  get() { return function () { return chain } },",
      "})",
      "export const tool = Object.assign(value => value, {",
      "  schema: {",
      "    enum: () => chain, string: () => chain, object: () => chain,",
      "    boolean: () => chain, number: () => chain,",
      "  },",
      "})",
      "",
    ].join("\n"),
  )

  server = Bun.serve({
    hostname: "127.0.0.1",
    port: 0,
    fetch(request, current) {
      const url = new URL(request.url)
      if (url.pathname === "/json/version") {
        versionRequests += 1
        const port = versionRequests === 1
          ? current.port
          : current.port === 65_535 ? 65_534 : current.port + 1
        return Response.json({
          webSocketDebuggerUrl: `ws://127.0.0.1:${port}/devtools/browser/fixture-1`,
        })
      }
      if (current.upgrade(request)) return
      return new Response("not found", { status: 404 })
    },
    websocket: {
      message(socket, message) {
        socket.send(message)
      },
    },
  })
})

afterAll(async () => {
  server.stop(true)
  await rm(fixtureRoot, { recursive: true, force: true })
})

test("Bun 原生 transport 传递 CDP JSON，并拒绝跨端口 WebSocket", async () => {
  const sourceUrl = pathToFileURL(resolve("deploy/internal/local_browser.ts")).href
  const childScript = `
    import { connectNativeCdpTransport } from ${JSON.stringify(sourceUrl)};
    const base = ${JSON.stringify("http://127.0.0.1:")} + process.env.TEST_CDP_PORT;
    const transport = await connectNativeCdpTransport(base, 5000);
    const reply = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("echo timeout")), 5000);
      transport.onmessage = message => { clearTimeout(timer); resolve(message); };
      transport.onclose = reason => { clearTimeout(timer); reject(new Error(reason || "closed")); };
    });
    transport.send({ id: 7, method: "Browser.getVersion" });
    const message = await reply;
    if (message.id !== 7 || message.method !== "Browser.getVersion") {
      throw new Error("unexpected echo");
    }
    transport.close();
    let rejected = false;
    try {
      await connectNativeCdpTransport(base, 5000);
    } catch (error) {
      rejected = error instanceof Error && error.message === "浏览器 CDP WebSocket 地址不在本机受控范围";
    }
    if (!rejected) throw new Error("cross-port endpoint was not rejected");
    console.log("LOCAL_BROWSER_NATIVE_TRANSPORT_OK");
  `
  const child = Bun.spawn([process.execPath, "-e", childScript], {
    cwd: resolve("."),
    env: {
      ...process.env,
      NODE_PATH: join(fixtureRoot, "node_modules"),
      TEST_CDP_PORT: String(server.port),
    },
    stdout: "pipe",
    stderr: "pipe",
  })
  const [exitCode, stdout, stderr] = await Promise.all([
    child.exited,
    new Response(child.stdout).text(),
    new Response(child.stderr).text(),
  ])

  expect(stderr).toBe("")
  expect(exitCode).toBe(0)
  expect(stdout).toContain("LOCAL_BROWSER_NATIVE_TRANSPORT_OK")
  expect(versionRequests).toBe(2)
})

test("主页面导航全量放行 HTTP(S)，并拒绝本地、内部、脚本协议和内嵌凭据", async () => {
  const sourceUrl = pathToFileURL(resolve("deploy/internal/local_browser.ts")).href
  const childScript = `
    import { isAllowedMainFrameNavigation } from ${JSON.stringify(sourceUrl)};
    const allowed = [
      "http://mimo.sdc.cs.icbc:9996/",
      "http://tcds-prod.sdc.icbc/aam/onlyLogin?ticket=secret-value",
      "https://10.0.0.8:9443/internal",
    ];
    const rejected = [
      "file:///etc/passwd",
      "chrome://policy/",
      "data:text/html,secret",
      "javascript:alert(1)",
      "http://user:password@internal.test/",
      "not a url",
    ];
    if (!allowed.every(isAllowedMainFrameNavigation)) throw new Error("HTTP(S) origin was not allowed");
    if (!rejected.every(value => !isAllowedMainFrameNavigation(value))) {
      throw new Error("unsafe main-frame navigation was allowed");
    }
    console.log("LOCAL_BROWSER_HTTP_NAVIGATION_POLICY_OK");
  `
  const child = Bun.spawn([process.execPath, "-e", childScript], {
    cwd: resolve("."),
    env: {
      ...process.env,
      NODE_PATH: join(fixtureRoot, "node_modules"),
    },
    stdout: "pipe",
    stderr: "pipe",
  })
  const [exitCode, stdout, stderr] = await Promise.all([
    child.exited,
    new Response(child.stdout).text(),
    new Response(child.stderr).text(),
  ])

  expect(stderr).toBe("")
  expect(exitCode).toBe(0)
  expect(stdout).toContain("LOCAL_BROWSER_HTTP_NAVIGATION_POLICY_OK")
})

/** 测试只需要模块形状，不加载真实 Playwright；真实 Chrome 连通性由运行验收命令覆盖。 */
async function writeFixturePackage(nodeModules: string, name: string, source: string): Promise<void> {
  const directory = join(nodeModules, ...name.split("/"))
  await mkdir(directory, { recursive: true })
  await writeFile(join(directory, "package.json"), JSON.stringify({ name, type: "module", exports: "./index.js" }))
  await writeFile(join(directory, "index.js"), source)
}
