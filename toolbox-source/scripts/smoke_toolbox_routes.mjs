#!/usr/bin/env node
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const requireFromOmniTools = createRequire(new URL('../omni-tools/package.json', import.meta.url));
const { chromium } = requireFromOmniTools('playwright');
const catalogPath = fileURLToPath(
  new URL('../../backend/test-agent-integration/src/main/resources/toolbox/catalog-v1.json', import.meta.url)
);
const catalog = JSON.parse(await readFile(catalogPath, 'utf8'));

const options = parseArgs(process.argv.slice(2));
const origins = {
  IT_TOOLS: new URL(options.itOrigin),
  OMNI_TOOLS: new URL(options.omniOrigin)
};

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const attemptedExternalRequests = [];
const browserErrors = [];

try {
  for (const source of ['IT_TOOLS', 'OMNI_TOOLS']) {
    const origin = origins[source];
    const context = await browser.newContext();
    await context.route('**/*', async (route) => {
      const requestUrl = new URL(route.request().url());
      // 本地预览只承载派生应用；为退出地址模拟真实部署中的平台 SPA，避免把预览 404 当成工具错误。
      if (requestUrl.origin === origin.origin && requestUrl.pathname === '/toolbox') {
        await route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>工具盒子</h1>' });
        return;
      }
      if (requestUrl.origin === origin.origin || ['blob:', 'data:'].includes(requestUrl.protocol)) {
        await route.continue();
        return;
      }
      attemptedExternalRequests.push(`${source} ${requestUrl.href}`);
      await route.abort('blockedbyclient');
    });

    const page = await context.newPage();
    page.on('pageerror', (error) => browserErrors.push(`${source} pageerror: ${error.message}`));
    page.on('console', (message) => {
      if (message.type() === 'error') browserErrors.push(`${source} console: ${message.text()}`);
    });

    const tools = catalog.tools.filter((tool) => tool.source === source);
    for (const [index, tool] of tools.entries()) {
      const upstreamPath = tool.launchPath.replace(
        source === 'IT_TOOLS' ? '/toolbox/apps/it-tools/' : '/toolbox/apps/omni-tools/',
        ''
      );
      const target = new URL(upstreamPath, `${origin.href.replace(/\/$/, '')}/`).href;
      const response = await page.goto(target, { waitUntil: 'domcontentloaded', timeout: 20_000 });
      if (!response?.ok()) throw new Error(`${tool.toolId} 深链响应 ${response?.status() ?? 'none'}`);
      await page.getByRole('link', { name: '工具盒子', exact: true }).first().waitFor({ timeout: 10_000 });
      const body = await page.locator('body').innerText();
      if (/Buy me a coffee|Support IT-Tools|OmniTools Home/i.test(body)) {
        throw new Error(`${tool.toolId} 仍显示上游门户或支持入口`);
      }
      if ((index + 1) % 25 === 0 || index + 1 === tools.length) {
        console.log(`${source}: ${index + 1}/${tools.length}`);
      }
    }

    // 两个离线剔除项即使被猜中直达地址，也必须回到平台，不能加载原上游能力。
    const excludedPath = source === 'IT_TOOLS'
      ? '/toolbox/apps/it-tools/camera-recorder'
      : '/toolbox/apps/omni-tools/pdf/editor';
    await page.goto(new URL(excludedPath, origin.origin).href, { waitUntil: 'domcontentloaded', timeout: 20_000 });
    await page.waitForURL(new URL('/toolbox', origin.origin).href, { timeout: 10_000 });
    await context.close();
  }
} finally {
  await browser.close();
}

if (attemptedExternalRequests.length > 0) {
  throw new Error(`检测到非同源请求:\n${attemptedExternalRequests.join('\n')}`);
}
if (browserErrors.length > 0) {
  throw new Error(`浏览器控制台存在错误:\n${browserErrors.join('\n')}`);
}
console.log(`工具盒子 ${catalog.tools.length} 条深链逐项加载且无非同源请求`);

function parseArgs(args) {
  const parsed = {
    itOrigin: 'http://127.0.0.1:4174/toolbox/apps/it-tools/',
    omniOrigin: 'http://127.0.0.1:4175/toolbox/apps/omni-tools/'
  };
  for (let index = 0; index < args.length; index += 1) {
    if (args[index] === '--it-origin') parsed.itOrigin = args[++index];
    else if (args[index] === '--omni-origin') parsed.omniOrigin = args[++index];
    else throw new Error(`未知参数: ${args[index]}`);
  }
  return parsed;
}
