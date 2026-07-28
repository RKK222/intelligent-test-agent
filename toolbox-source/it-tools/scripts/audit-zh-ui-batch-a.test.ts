import { copyFile, mkdir, mkdtemp, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { afterEach, describe, expect, it } from 'vitest';

const PROJECT_ROOT = fileURLToPath(new URL('../', import.meta.url));
const AUDIT_SCRIPT = join(PROJECT_ROOT, 'scripts/audit-zh-ui-batch-a.mjs');
const SOURCE_DIRECTORIES = [
  'token-generator',
  'hash-text',
  'bcrypt',
  'uuid-generator',
  'ulid-generator',
  'encryption',
  'bip39-generator',
  'hmac-generator',
  'rsa-key-pair-generator',
  'password-strength-analyser',
  'pdf-signature-checker',
  'date-time-converter',
  'integer-base-converter',
  'roman-numeral-converter',
  'base64-string-converter',
  'base64-file-converter',
  'color-converter',
  'case-converter',
  'text-to-nato-alphabet',
  'text-to-binary',
  'text-to-unicode',
  'yaml-to-json-converter',
  'yaml-to-toml',
  'json-to-yaml-converter',
  'json-to-toml',
  'list-converter',
  'toml-to-json',
  'toml-to-yaml',
  'xml-to-json',
  'json-to-xml',
  'markdown-to-html',
  'url-encoder',
  'html-entities',
  'url-parser',
  'device-information',
  'basic-auth-generator',
  'meta-tag-generator',
  'otp-code-generator-and-validator',
  'mime-types',
  'jwt-parser',
  'keycode-info',
  'slugify-string',
  'html-wysiwyg-editor',
] as const;

const fixtureRoots: string[] = [];

afterEach(async () => {
  await Promise.all(fixtureRoots.splice(0).map(root => rm(root, { recursive: true, force: true })));
});

async function createAuditFixture() {
  const root = await mkdtemp(join(tmpdir(), 'it-tools-zh-audit-'));
  fixtureRoots.push(root);
  await mkdir(join(root, 'scripts'), { recursive: true });
  await mkdir(join(root, 'locales'), { recursive: true });
  await Promise.all(SOURCE_DIRECTORIES.map(directory => mkdir(join(root, 'src/tools', directory), { recursive: true })));
  await symlink(join(PROJECT_ROOT, 'node_modules'), join(root, 'node_modules'), 'dir');
  await copyFile(AUDIT_SCRIPT, join(root, 'scripts/audit-zh-ui-batch-a.mjs'));
  await writeFile(join(root, 'locales/en.yml'), 'tools: {}\n', 'utf8');
  await writeFile(join(root, 'locales/zh.yml'), 'tools: {}\n', 'utf8');
  return root;
}

async function writeFixtureSource(root: string, relativePath: string, source: string) {
  const target = join(root, 'src/tools', relativePath);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, source, 'utf8');
}

function runAudit(root: string) {
  return spawnSync(process.execPath, [join(root, 'scripts/audit-zh-ui-batch-a.mjs')], {
    cwd: root,
    encoding: 'utf8',
  });
}

describe('Task 2B 中文 UI 审计器', () => {
  it('检查动态可见属性和插值中的根级英文字符串', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/dynamic-visible.vue', `
<template>
  <c-input-text :label="'Delete account'" :placeholder="'Enter name'" />
  <div>{{ 'Visible English' }}</div>
</template>
`);

    const result = runAudit(root);

    expect(result.status).toBe(1);
    expect(result.stderr).toContain('Delete account');
    expect(result.stderr).toContain('Enter name');
    expect(result.stderr).toContain('Visible English');
  });

  it('递归检查动态可见属性逻辑表达式中的英文回退值', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/logical-fallback.vue', [
      '<template>',
      '  <c-card :title="fallback || \'Delete account\'" :aria-label="fallback || `Archive record`" />',
      '</template>',
      '',
    ].join('\n'));

    const result = runAudit(root);

    expect(result.status).toBe(1);
    expect(result.stderr).toContain('Delete account');
    expect(result.stderr).toContain('Archive record');
  });

  it('递归检查插值逻辑表达式中的英文回退值', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/interpolation-fallback.vue', `
<template>
  <div>{{ status || 'Unknown status' }}</div>
</template>
`);

    const result = runAudit(root);

    expect(result.status).toBe(1);
    expect(result.stderr).toContain('Unknown status');
  });

  it('递归检查动态 tooltip 模板字符串的头部、中部和尾部英文', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/template-tooltip.vue', [
      '<template>',
      '  <c-button :tooltip="`Remove $' + '{name} from $' + '{group} account`" />',
      '</template>',
      '',
    ].join('\n'));

    const result = runAudit(root);

    expect(result.status).toBe(1);
    expect(result.stderr).toContain('Remove');
    expect(result.stderr).toContain('from');
    expect(result.stderr).toContain('account');
  });

  it('递归可见表达式时忽略 import、稳定错误码、内部前缀和技术词', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/non-visible-strings.vue', [
      '<script setup lang="ts">',
      'import formatter from \'./Internal formatter\';',
      'const status = \'INVALID_BINARY_STRING\';',
      '</script>',
      '<template>',
      '  <c-button :tooltip="status.startsWith(\'tools.\') ? formatter({ internal_key: status }) : `PDF $' + '{name}`" />',
      '</template>',
      '',
    ].join('\n'));

    const result = runAudit(root);

    expect(result.status).toBe(0);
    expect(result.stderr).toBe('');
  });

  it('检查通过 Object.keys 返回并展示的英文对象键', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/visible-object-key.vue', `
<template>
  <span>{{ Object.keys({ 'Delete account': true })[0] }}</span>
</template>
`);

    const result = runAudit(root);

    expect(result.status).toBe(1);
    expect(result.stderr).toContain('Delete account');
  });

  it('检查可返回参数的自定义 includes 调用中的英文', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/custom-includes.vue', `
<template>
  <span>{{ custom.includes('Unknown status') }}</span>
</template>
`);

    const result = runAudit(root);

    expect(result.status).toBe(1);
    expect(result.stderr).toContain('Unknown status');
  });

  it('检查可返回匹配文本的标准 match 调用中的英文', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/standard-match.vue', `
<template>
  <span>{{ value.match('Visible match')[0] }}</span>
</template>
`);

    const result = runAudit(root);

    expect(result.status).toBe(1);
    expect(result.stderr).toContain('Visible match');
  });

  it('拒绝任意全大写英文和拆开的精确技术短语，同时保留明确技术名词', async () => {
    const root = await createAuditFixture();
    await writeFixtureSource(root, 'token-generator/allowlist.vue', `
<template>
  <div>DELETE</div>
  <div>ERROR</div>
  <div>Open</div>
  <div>Graph</div>
  <div>Open Graph</div>
  <div>PDF</div>
</template>
`);

    const result = runAudit(root);
    const findingLines = result.stderr.split('\n').filter(line => line.includes('[模板可见文本]'));

    expect(result.status).toBe(1);
    expect(findingLines.some(line => line.endsWith('DELETE'))).toBe(true);
    expect(findingLines.some(line => line.endsWith('ERROR'))).toBe(true);
    expect(findingLines.some(line => line.endsWith('Open'))).toBe(true);
    expect(findingLines.some(line => line.endsWith('Graph'))).toBe(true);
    expect(findingLines.some(line => line.endsWith('Open Graph'))).toBe(false);
    expect(findingLines.some(line => line.endsWith('PDF'))).toBe(false);
  });

  it('逐一校验同一 locale key 的跨路由使用位置', async () => {
    const root = await createAuditFixture();
    const locale = `
tools:
  hash-text:
    ui:
      shared: Shared label
`;
    await writeFile(join(root, 'locales/en.yml'), locale, 'utf8');
    await writeFile(join(root, 'locales/zh.yml'), locale.replace('Shared label', '共享标签'), 'utf8');
    await writeFixtureSource(root, 'token-generator/early-invalid.ts', `
export const option = { label: 'tools.hash-text.ui.shared' };
`);
    await writeFixtureSource(root, 'hash-text/later-valid.ts', `
export const option = { label: 'tools.hash-text.ui.shared' };
`);

    const result = runAudit(root);

    expect(result.status).toBe(1);
    expect(result.stderr).toContain('src/tools/token-generator/early-invalid.ts');
    expect(result.stderr).toContain('[locale key 越界] tools.hash-text.ui.shared');
  });
});
