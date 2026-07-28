#!/usr/bin/env node
import { createRequire } from "node:module";

const requireFromOmniTools = createRequire(
  new URL("../omni-tools/package.json", import.meta.url),
);
const { chromium } = requireFromOmniTools("playwright");
const { PDFDocument, StandardFonts, rgb } = requireFromOmniTools("pdf-lib");

const itOrigin = process.env.TEST_AGENT_TOOLBOX_IT_ORIGIN ?? "http://127.0.0.1:4174";
const omniOrigin =
  process.env.TEST_AGENT_TOOLBOX_OMNI_ORIGIN ?? "http://127.0.0.1:4175";
const browser = await chromium.launch({ channel: "chrome", headless: true });

try {
  await smokeAsciiAndClipboard();
  await smokeOmniRuntimeTools();
  console.log(
    "ASCII、HTTP 复制兼容、FFmpeg、Ghostscript、图片压缩/编辑/QR、OCR 与 AI 抠图真实功能冒烟通过",
  );
} finally {
  await browser.close();
}

async function newOfflineContext(origin) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 900 },
  });
  const requests = [];
  const external = [];
  await context.route("**/*", async (route) => {
    const url = new URL(route.request().url());
    // Chrome 内置 PDF 查看器使用 chrome-extension/chrome 资源，不产生企业外网请求。
    if (
      url.origin === origin ||
      ["blob:", "data:", "chrome:", "chrome-extension:"].includes(url.protocol)
    ) {
      requests.push(url.href);
      await route.continue();
    } else {
      external.push(url.href);
      await route.abort("blockedbyclient");
    }
  });
  return { context, requests, external };
}

async function smokeAsciiAndClipboard() {
  const { context, requests, external } = await newOfflineContext(itOrigin);
  await context.addInitScript(() => {
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: undefined,
    });
    document.execCommand = (command) => {
      sessionStorage.setItem("toolbox-copy-command", command);
      return command === "copy";
    };
  });
  const page = await context.newPage();

  await page.goto(`${itOrigin}/toolbox/apps/it-tools/ascii-text-drawer`, {
    waitUntil: "domcontentloaded",
  });
  await page.locator("textarea").first().fill("OFFLINE");
  await page.waitForFunction(() =>
    document.body.innerText.includes("ASCII 艺术字"),
  );
  await page.waitForFunction(
    () => !document.body.innerText.includes("正在加载字体…"),
  );
  requireRequest(
    requests,
    "/toolbox/apps/it-tools/fonts/Standard.flf",
    "Figlet 字体",
  );

  await page.goto(`${itOrigin}/toolbox/apps/it-tools/token-generator`, {
    waitUntil: "domcontentloaded",
  });
  await page.getByRole("button", { name: "复制", exact: true }).click();
  const fallbackCommand = await page.evaluate(() =>
    sessionStorage.getItem("toolbox-copy-command"),
  );
  if (fallbackCommand !== "copy")
    throw new Error("HTTP 剪贴板降级未调用 execCommand(copy)");
  assertNoExternal(external);
  await context.close();
}

async function smokeOmniRuntimeTools() {
  const { context, requests, external } = await newOfflineContext(omniOrigin);
  await context.addInitScript(() => {
    // 模拟企业非 localhost 的 HTTP 入口，二进制剪贴板能力必须被安全隐藏。
    Object.defineProperty(window, "isSecureContext", {
      configurable: true,
      value: false,
    });
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: undefined,
    });
    Object.defineProperty(window, "ClipboardItem", {
      configurable: true,
      value: undefined,
    });
  });
  const page = await context.newPage();
  const browserErrors = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));

  const wav = createWavFixture();
  await page.goto(`${omniOrigin}/toolbox/apps/omni-tools/audio/change-speed`, {
    waitUntil: "domcontentloaded",
  });
  await page.getByRole("radio", { name: "WAV" }).check();
  await page
    .locator("input[type=file]")
    .first()
    .setInputFiles({ name: "tone.wav", mimeType: "audio/wav", buffer: wav });
  await page.waitForFunction(
    () => document.querySelectorAll("audio").length >= 2,
    undefined,
    { timeout: 120_000 },
  );
  requireRequest(
    requests,
    "/runtime/ffmpeg/ffmpeg-core.js",
    "FFmpeg JavaScript",
  );
  requireRequest(requests, "/runtime/ffmpeg/ffmpeg-core.wasm", "FFmpeg WASM");

  const pdf = await createPdfFixture();
  await page.goto(`${omniOrigin}/toolbox/apps/omni-tools/pdf/compress-pdf`, {
    waitUntil: "domcontentloaded",
  });
  await page
    .locator("input[type=file]")
    .first()
    .setInputFiles({
      name: "offline.pdf",
      mimeType: "application/pdf",
      buffer: pdf,
    });
  await page.waitForFunction(
    () => document.querySelectorAll("iframe").length >= 2,
    undefined,
    { timeout: 120_000 },
  );
  requireRequest(
    requests,
    "/runtime/ghostscript/gs-worker.wasm",
    "Ghostscript WASM",
  );

  const image = await createTextImageFixture(context);
  await page.goto(
    `${omniOrigin}/toolbox/apps/omni-tools/image-generic/compress`,
    { waitUntil: "domcontentloaded" },
  );
  await page
    .locator("input[type=file]")
    .first()
    .setInputFiles({
      name: "offline.png",
      mimeType: "image/png",
      buffer: image,
    });
  await page.locator('img[alt="Result"]').waitFor({ timeout: 120_000 });
  if (
    (await page.getByRole("button", { name: "Copy", exact: true }).count()) > 0
  ) {
    throw new Error("HTTP 环境仍展示不可用的图片复制操作");
  }

  await page.goto(`${omniOrigin}/toolbox/apps/omni-tools/png/compress-png`, {
    waitUntil: "domcontentloaded",
  });
  await page
    .locator("input[type=file]")
    .first()
    .setInputFiles({
      name: "offline.png",
      mimeType: "image/png",
      buffer: image,
    });
  await page.locator('img[alt="Result"]').waitFor({ timeout: 120_000 });

  await page.goto(
    `${omniOrigin}/toolbox/apps/omni-tools/image-generic/editor`,
    { waitUntil: "domcontentloaded" },
  );
  await page
    .locator("input[type=file]")
    .first()
    .setInputFiles({
      name: "offline.png",
      mimeType: "image/png",
      buffer: image,
    });
  await page.locator(".FIE_editor-content").waitFor({ timeout: 120_000 });

  await page.goto(
    `${omniOrigin}/toolbox/apps/omni-tools/image-generic/qr-code`,
    { waitUntil: "domcontentloaded" },
  );
  await page.locator('img[alt="Result"]').waitFor({ timeout: 120_000 });

  await page.goto(
    `${omniOrigin}/toolbox/apps/omni-tools/image-generic/image-to-text`,
    { waitUntil: "domcontentloaded" },
  );
  await page
    .locator("input[type=file]")
    .first()
    .setInputFiles({
      name: "offline.png",
      mimeType: "image/png",
      buffer: image,
    });
  await page.waitForFunction(
    () => {
      const field = document.querySelector('[data-testid="text-result"]');
      return (
        field instanceof HTMLTextAreaElement && field.value.trim().length > 0
      );
    },
    undefined,
    { timeout: 180_000 },
  );
  requireRequest(
    requests,
    "/runtime/tesseract/worker.min.js",
    "Tesseract worker",
  );
  requireRequest(
    requests,
    "/runtime/tesseract/lang/eng.traineddata.gz",
    "Tesseract 英文语言包",
  );

  // AI 运行时初始化失败时会抛 pageerror；与结果竞争可避免 CSP 回归后空等完整超时。
  const backgroundError = new Promise((resolve) =>
    page.once("pageerror", resolve),
  );
  await page.goto(
    `${omniOrigin}/toolbox/apps/omni-tools/image-generic/remove-background`,
    { waitUntil: "domcontentloaded" },
  );
  await page
    .locator("input[type=file]")
    .first()
    .setInputFiles({
      name: "subject.png",
      mimeType: "image/png",
      buffer: image,
    });
  const backgroundOutcome = await Promise.race([
    page
      .locator('img[alt="Result"]')
      .waitFor({ timeout: 240_000 })
      .then(() => null),
    backgroundError,
  ]);
  if (backgroundOutcome instanceof Error) {
    throw new Error(`AI 抠图运行失败: ${backgroundOutcome.message}`);
  }
  requireRequest(requests, "/runtime/imgly/resources.json", "IMG.LY 资源清单");

  assertNoExternal(external);
  if (browserErrors.length > 0)
    throw new Error(`浏览器运行错误:\n${browserErrors.join("\n")}`);
  await context.close();
}

function requireRequest(requests, suffix, label) {
  if (!requests.some((request) => request.includes(suffix))) {
    throw new Error(`${label} 未从同源路径加载: ${suffix}`);
  }
}

function assertNoExternal(external) {
  if (external.length > 0)
    throw new Error(`检测到非同源请求:\n${external.join("\n")}`);
}

function createWavFixture() {
  const sampleRate = 8_000;
  const sampleCount = sampleRate / 2;
  const buffer = Buffer.alloc(44 + sampleCount * 2);
  buffer.write("RIFF", 0);
  buffer.writeUInt32LE(36 + sampleCount * 2, 4);
  buffer.write("WAVEfmt ", 8);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(sampleRate * 2, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write("data", 36);
  buffer.writeUInt32LE(sampleCount * 2, 40);
  for (let index = 0; index < sampleCount; index += 1) {
    buffer.writeInt16LE(
      Math.round(Math.sin((index * 2 * Math.PI * 440) / sampleRate) * 10_000),
      44 + index * 2,
    );
  }
  return buffer;
}

async function createPdfFixture() {
  const document = await PDFDocument.create();
  const font = await document.embedFont(StandardFonts.Helvetica);
  const page = document.addPage([320, 180]);
  page.drawText("OFFLINE TOOLBOX PDF", {
    x: 32,
    y: 100,
    size: 22,
    font,
    color: rgb(0.1, 0.2, 0.4),
  });
  return Buffer.from(await document.save());
}

async function createTextImageFixture(context) {
  const page = await context.newPage();
  await page.setContent(
    `<!doctype html><style>html,body{margin:0;background:white}body{width:640px;height:240px;display:flex;align-items:center;justify-content:center;font:700 54px Arial;color:#111}</style>OFFLINE 123`,
  );
  const image = await page.screenshot({ type: "png" });
  await page.close();
  return image;
}
