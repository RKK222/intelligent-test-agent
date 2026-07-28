#!/usr/bin/env node

import { readFile, readdir, stat } from 'node:fs/promises';
import { extname, join, relative } from 'node:path';
import process from 'node:process';
import ts from 'typescript';
import { compileTemplate, parse as parseSfc } from '@vue/compiler-sfc';
import { parse as parseYaml } from 'yaml';

const PROJECT_ROOT = new URL('../', import.meta.url).pathname;
const TOOLS_ROOT = join(PROJECT_ROOT, 'src/tools');
const TOOL_REGISTRY = join(TOOLS_ROOT, 'index.ts');
const SHARED_VISIBLE_FILES = [
  join(PROJECT_ROOT, 'src/components/FormatTransformer.vue'),
  join(PROJECT_ROOT, 'src/components/InputCopyable.vue'),
  join(PROJECT_ROOT, 'src/components/SpanCopyable.vue'),
  join(PROJECT_ROOT, 'src/components/TextareaCopyable.vue'),
  join(PROJECT_ROOT, 'src/composable/copy.ts'),
];
const SHARED_UI_ROOT = join(PROJECT_ROOT, 'src/ui');

// 目录路由与上游源码目录有四处历史命名差异，审计必须显式固定，避免误扫到后 42 条路由。
const ROUTE_DIRECTORIES = new Map([
  ['token-generator', 'token-generator'],
  ['hash-text', 'hash-text'],
  ['bcrypt', 'bcrypt'],
  ['uuid-generator', 'uuid-generator'],
  ['ulid-generator', 'ulid-generator'],
  ['encryption', 'encryption'],
  ['bip39-generator', 'bip39-generator'],
  ['hmac-generator', 'hmac-generator'],
  ['rsa-key-pair-generator', 'rsa-key-pair-generator'],
  ['password-strength-analyser', 'password-strength-analyser'],
  ['pdf-signature-checker', 'pdf-signature-checker'],
  ['date-converter', 'date-time-converter'],
  ['base-converter', 'integer-base-converter'],
  ['roman-numeral-converter', 'roman-numeral-converter'],
  ['base64-string-converter', 'base64-string-converter'],
  ['base64-file-converter', 'base64-file-converter'],
  ['color-converter', 'color-converter'],
  ['case-converter', 'case-converter'],
  ['text-to-nato-alphabet', 'text-to-nato-alphabet'],
  ['text-to-binary', 'text-to-binary'],
  ['text-to-unicode', 'text-to-unicode'],
  ['yaml-to-json-converter', 'yaml-to-json-converter'],
  ['yaml-to-toml', 'yaml-to-toml'],
  ['json-to-yaml-converter', 'json-to-yaml-converter'],
  ['json-to-toml', 'json-to-toml'],
  ['list-converter', 'list-converter'],
  ['toml-to-json', 'toml-to-json'],
  ['toml-to-yaml', 'toml-to-yaml'],
  ['xml-to-json', 'xml-to-json'],
  ['json-to-xml', 'json-to-xml'],
  ['markdown-to-html', 'markdown-to-html'],
  ['url-encoder', 'url-encoder'],
  ['html-entities', 'html-entities'],
  ['url-parser', 'url-parser'],
  ['device-information', 'device-information'],
  ['basic-auth-generator', 'basic-auth-generator'],
  ['og-meta-generator', 'meta-tag-generator'],
  ['otp-generator', 'otp-code-generator-and-validator'],
  ['mime-types', 'mime-types'],
  ['jwt-parser', 'jwt-parser'],
  ['keycode-info', 'keycode-info'],
  ['slugify-string', 'slugify-string'],
  ['html-wysiwyg-editor', 'html-wysiwyg-editor'],
  ['user-agent-parser', 'user-agent-parser'],
  ['http-status-codes', 'http-status-codes'],
  ['json-diff', 'json-diff'],
  ['safelink-decoder', 'safelink-decoder'],
  ['qrcode-generator', 'qr-code-generator'],
  ['wifi-qrcode-generator', 'wifi-qr-code-generator'],
  ['svg-placeholder-generator', 'svg-placeholder-generator'],
  ['git-memo', 'git-memo'],
  ['random-port-generator', 'random-port-generator'],
  ['crontab-generator', 'crontab-generator'],
  ['json-prettify', 'json-viewer'],
  ['json-minify', 'json-minify'],
  ['json-to-csv', 'json-to-csv'],
  ['sql-prettify', 'sql-prettify'],
  ['chmod-calculator', 'chmod-calculator'],
  ['docker-run-to-docker-compose-converter', 'docker-run-to-docker-compose-converter'],
  ['xml-formatter', 'xml-formatter'],
  ['yaml-prettify', 'yaml-viewer'],
  ['email-normalizer', 'email-normalizer'],
  ['regex-tester', 'regex-tester'],
  ['regex-memo', 'regex-memo'],
  ['ipv4-subnet-calculator', 'ipv4-subnet-calculator'],
  ['ipv4-address-converter', 'ipv4-address-converter'],
  ['ipv4-range-expander', 'ipv4-range-expander'],
  ['mac-address-lookup', 'mac-address-lookup'],
  ['mac-address-generator', 'mac-address-generator'],
  ['ipv6-ula-generator', 'ipv6-ula-generator'],
  ['math-evaluator', 'math-evaluator'],
  ['eta-calculator', 'eta-calculator'],
  ['percentage-calculator', 'percentage-calculator'],
  ['chronometer', 'chronometer'],
  ['temperature-converter', 'temperature-converter'],
  ['benchmark-builder', 'benchmark-builder'],
  ['lorem-ipsum-generator', 'lorem-ipsum-generator'],
  ['text-statistics', 'text-statistics'],
  ['emoji-picker', 'emoji-picker'],
  ['string-obfuscator', 'string-obfuscator'],
  ['text-diff', 'text-diff'],
  ['numeronym-generator', 'numeronym-generator'],
  ['ascii-text-drawer', 'ascii-text-drawer'],
  ['phone-parser-and-formatter', 'phone-parser-and-formatter'],
  ['iban-validator-and-parser', 'iban-validator-and-parser'],
]);

const USER_VISIBLE_ATTRIBUTES = new Set([
  'aria-label',
  'description',
  'feedback',
  'label',
  'placeholder',
  'title',
  'tooltip',
]);

const USER_VISIBLE_PROPERTIES = new Set([
  'ariaLabel',
  'description',
  'feedback',
  'label',
  'message',
  'name',
  'placeholder',
  'plural',
  'text',
  'title',
  'tooltip',
  'unit',
]);

// 这里只放无需汉化、且会真实展示给用户的协议/算法/格式名；普通英文单词不能加入。
const TECHNICAL_TERMS = new Set([
  'ace', 'aes', 'api', 'ascii', 'base32', 'base64', 'bcrypt', 'bip39', 'blowfish', 'bban', 'cidr', 'chmod', 'cmyk', 'compose', 'cpu', 'cron', 'crontab', 'css', 'csv', 'des', 'docker', 'eap', 'ecdsa', 'emoji', 'eta', 'excel', 'facebook', 'feb',
  'abs', 'apr', 'cos', 'git', 'hex', 'hmac', 'hsl', 'html', 'http', 'https', 'hwb', 'i18n', 'iban', 'id', 'ietf', 'internationalization', 'ip', 'ipsum', 'ipv4', 'ipv6', 'isbn', 'iso', 'javascript', 'jan', 'jcard', 'js', 'json', 'jwt', 'lch', 'linkedin', 'lorem',
  'mac', 'mar', 'markdown', 'md5', 'mdn', 'meta', 'mgf1', 'mime', 'mon', 'mongo', 'ms', 'nato', 'numeronym', 'objectid', 'og', 'otp', 'p-256', 'p-384', 'p-521', 'passport', 'pdf', 'pem', 'qr', 'regexplained',
  'rabbit', 'rc4', 'rfc', 'rgb', 'rgba', 'ripemd-160', 'ripemd160', 'rsa', 'rsassa-pkcs1-v1', 'rsassa-pss', 'sha-1', 'sha-2', 'sha-3', 'sha-224', 'sha-256',
  'sha-384', 'sha-512', 'sha1', 'sha3', 'sha224', 'sha256', 'sha384', 'sha512', 'shaken', 'sip', 'slug', 'toml', 'totp', 'tripledes',
  'sin', 'spark', 'sqrt', 'sql', 'sqlite', 'ssid', 'sun', 'svg', 'tls', 'twitter', 'ula', 'ulid', 'unicode', 'unix', 'uri', 'url', 'utc', 'utf8', 'uuid', 'voip', 'w3c', 'webdav', 'wep', 'wifi', 'wpa', 'wpa2', 'wysiwyg', 'xml', 'yaml', 'yml',
]);

const TECHNICAL_PHRASES = [
  'Basic Authentication',
  'Basic Auth',
  'HTTP GET',
  'Access Token',
  'ID Token',
  'Open Graph',
  'SIP Call-Id',
  'SIP CSeq',
  'SIP Date',
  'SIP From',
  'SIP Via',
  'User-Agent',
  'QR-IBAN',
  'Microsoft Outlook Safe Links',
  'Outlook Safe Links',
  'Safe Links',
  'Docker Compose',
  'docker run',
  'GCP BigQuery',
  'IBM DB2',
  'Apache Hive',
  'MariaDB',
  'MySQL',
  'Couchbase N1QL',
  'Oracle PL/SQL',
  'PostgreSQL',
  'Amazon Redshift',
  'Standard SQL',
  'SQL Server Transact-SQL',
  'docker-compose.yml',
  'E.164',
  'RFC3966',
];

// 示例输入和生成数据按任务约束保持原样；每一项都必须是精确匹配，禁止泛化为英文白名单。
const EXACT_EXAMPLE_TEXT = new Set([
  'ASCII ART',
  'Lorem ipsum dolor sit amet',
  'The quick brown fox jumps over the lazy dog',
  '例如“Hello Avengers”',
  '例如“Hello world”',
  '大写字母（ABC…）',
  '小写字母（abc…）',
  '输入 MIME 类型，例如 application/json',
  '请输入前缀，例如 64:16:7F',
  '请输入数学表达式，例如 2*sqrt(6)…',
  '请输入单词，例如“internationalization”',
  '缩写将在此显示，例如“i18n”',
]);

// 服务边界的稳定错误码不会直接展示给用户，只允许逐项精确登记。
const EXACT_TECHNICAL_TEXT = new Set([
  'INVALID_BINARY_STRING',
  'INVALID_SAFELINK_URL',
  'CLIPBOARD_WRITE_FAILED',
  'A',
  'B',
  'C',
  'D',
  'E',
  'K',
  '°C',
  '°F',
  '°R',
  '°De',
  '°N',
  '°Ré',
  '°Rø',
  'u',
  'g',
  'o',
  'X',
  'Y',
  'value',
  'array',
  'object',
  'added',
  'removed',
  'unchanged',
  'children-updated',
  'updated',
  'null',
  'i',
  'm',
  's',
  'v',
  'WPA/WPA2',
  'WPA2-EAP',
  '1xx 信息响应',
  '2xx 成功响应',
  '3xx 重定向',
  '4xx 客户端错误',
  '5xx 服务器错误',
  'jan,feb,mar,apr ...',
  'sun,mon ...',
  '(u)',
  '(g)',
  '(o)',
]);

const findings = [];
const usedLocaleKeys = new Map();
const LOCALE_KEY_PATTERN = /^(?:common\.[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*|tools\.[A-Za-z0-9_-]+\.[A-Za-z0-9_.-]+)$/;

function lineOf(source, offset) {
  return source.slice(0, offset).split('\n').length;
}

function addFinding(file, line, kind, value) {
  findings.push({ file: relative(PROJECT_ROOT, file), line, kind, value: value.replace(/\s+/g, ' ').trim() });
}

function englishWords(value) {
  if (!value || EXACT_EXAMPLE_TEXT.has(value.trim()) || EXACT_TECHNICAL_TEXT.has(value.trim())) {
    return [];
  }

  // Vue I18n 插值参数不是最终展示文案，先精确移除再判断自然语言英文。
  const withoutInterpolations = value
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`[^`]*`/g, '')
    .replace(/https?:\/\/[^\s)]+/g, '')
    .replace(/\{[A-Za-z_][A-Za-z0-9_]*\}/g, '');
  const withoutTechnicalPhrases = TECHNICAL_PHRASES.reduce(
    (text, phrase) => text.replaceAll(new RegExp(phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi'), ''),
    withoutInterpolations,
  );
  return (withoutTechnicalPhrases.match(/[A-Za-z][A-Za-z0-9'-]*/g) ?? []).filter((word) => {
    const normalized = word.toLowerCase();
    if (TECHNICAL_TERMS.has(normalized)) {
      return false;
    }
    return true;
  });
}

function containsNaturalEnglish(value) {
  return englishWords(value).length > 0;
}

function getStaticPropertyName(node) {
  if (ts.isIdentifier(node) || ts.isStringLiteral(node) || ts.isNumericLiteral(node)) {
    return node.text;
  }
  return undefined;
}

function recordLocaleOccurrence(key, occurrence) {
  const occurrences = usedLocaleKeys.get(key) ?? [];
  if (occurrences.some(({ route, file, line }) => route === occurrence.route && file === occurrence.file && line === occurrence.line)) {
    return;
  }
  occurrences.push(occurrence);
  usedLocaleKeys.set(key, occurrences);
}

function recordLocaleKey(route, file, source, node, offset) {
  if (LOCALE_KEY_PATTERN.test(node.text)) {
    recordLocaleOccurrence(node.text, { route, file, line: lineOf(source, node.getStart()) + offset });
    return true;
  }

  const call = node.parent;
  if (!ts.isCallExpression(call) || call.arguments[0] !== node) {
    return false;
  }

  const callee = call.expression;
  const calleeName = ts.isIdentifier(callee)
    ? callee.text
    : ts.isPropertyAccessExpression(callee)
      ? callee.name.text
      : undefined;
  if (!['t', '$t', 'translate'].includes(calleeName ?? '')) {
    return false;
  }

  recordLocaleOccurrence(node.text, { route, file, line: lineOf(source, node.getStart()) + offset });
  return true;
}

function containingVariableName(node) {
  let current = node.parent;
  while (current && !ts.isSourceFile(current)) {
    if (ts.isVariableDeclaration(current) && ts.isIdentifier(current.name)) {
      return current.name.text;
    }
    current = current.parent;
  }
  return undefined;
}

function isNonVisibleExpressionText(node) {
  const parent = node.parent;

  // JWT 视图只用确切前缀判断 locale key；禁止按方法名泛化跳过其它调用参数。
  if (node.text === 'tools.') {
    return true;
  }
  // 模块路径只参与程序寻址，不是表达式最终展示的文案。
  if ((ts.isImportDeclaration(parent) || ts.isExportDeclaration(parent)) && parent.moduleSpecifier === node) {
    return true;
  }
  return ts.isCallExpression(parent) && parent.expression.kind === ts.SyntaxKind.ImportKeyword;
}

function isUserVisibleScriptString(node, file, rootExpressionVisible, route) {
  const parent = node.parent;

  if (rootExpressionVisible && !isNonVisibleExpressionText(node)) {
    return true;
  }

  if (ts.isPropertyAssignment(parent)) {
    const propertyName = getStaticPropertyName(parent.name);
    const isLanguageOptionKey = parent.name === node && /languages/i.test(containingVariableName(node) ?? '');
    const isVisibleName = propertyName === 'name'
      && /(?:details|formats|information|schema|sections)/i.test(containingVariableName(node) ?? file);
    return (USER_VISIBLE_PROPERTIES.has(propertyName ?? '') && propertyName !== 'name') || isVisibleName || isLanguageOptionKey;
  }

  if (ts.isArrayLiteralExpression(parent)) {
    return /(?:descriptions|details|formats|labels|languages|options|properties)/i.test(containingVariableName(node) ?? '');
  }

  if ((ts.isReturnStatement(parent) && route !== 'shared')
    || ts.isThrowStatement(parent)
    || (rootExpressionVisible && ts.isExpressionStatement(parent))) {
    return true;
  }

  if (ts.isNewExpression(parent) && ts.isIdentifier(parent.expression) && parent.expression.text === 'Error') {
    return true;
  }

  if (ts.isCallExpression(parent)) {
    const callee = parent.expression;
    return ts.isPropertyAccessExpression(callee) && ['error', 'info', 'success', 'warning'].includes(callee.name.text);
  }

  return false;
}

function isIdentifierReference(node) {
  const parent = node.parent;
  return !(ts.isPropertyAccessExpression(parent) && parent.name === node)
    && !(ts.isPropertyAssignment(parent) && parent.name === node)
    && !(ts.isVariableDeclaration(parent) && parent.name === node)
    && !(ts.isParameter(parent) && parent.name === node);
}

function inspectVisibleBindingInitializer(route, file, binding, visibleBindings, visitedBindings) {
  const sourceFile = ts.createSourceFile(file, binding.source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const statement = sourceFile.statements[0];
  if (!statement || !ts.isExpressionStatement(statement)) {
    return;
  }

  function createLexicalScope(block, parent) {
    const scope = { bindings: new Map(), parent };
    for (const child of block.statements) {
      if (!ts.isVariableStatement(child)) {
        continue;
      }
      for (const declaration of child.declarationList.declarations) {
        if (ts.isIdentifier(declaration.name) && declaration.initializer) {
          scope.bindings.set(declaration.name.text, { initializer: declaration.initializer, scope });
        }
      }
    }
    return scope;
  }

  function findLocalBinding(scope, name) {
    let current = scope;
    while (current) {
      const localBinding = current.bindings.get(name);
      if (localBinding) {
        return localBinding;
      }
      current = current.parent;
    }
    return undefined;
  }

  function visitReturnedExpressions(node, scope, visitedLocalBindings) {
    if (ts.isReturnStatement(node)) {
      if (node.expression) {
        visitVisibleResult(node.expression, scope, visitedLocalBindings);
      }
      return;
    }

    // 内层函数的 return 不属于当前 computed/ref 回调，不能泄漏到外层可见结果。
    if (ts.isArrowFunction(node)
      || ts.isFunctionExpression(node)
      || ts.isFunctionDeclaration(node)
      || ts.isMethodDeclaration(node)
      || ts.isGetAccessorDeclaration(node)
      || ts.isSetAccessorDeclaration(node)
      || ts.isConstructorDeclaration(node)) {
      return;
    }

    if (ts.isBlock(node)) {
      const childScope = createLexicalScope(node, scope);
      node.statements.forEach(child => visitReturnedExpressions(child, childScope, visitedLocalBindings));
      return;
    }

    ts.forEachChild(node, child => visitReturnedExpressions(child, scope, visitedLocalBindings));
  }

  function visitFunctionBody(block, parentScope, visitedLocalBindings) {
    const scope = createLexicalScope(block, parentScope);
    block.statements.forEach(child => visitReturnedExpressions(child, scope, visitedLocalBindings));
  }

  function visitVisibleResult(node, lexicalScope, visitedLocalBindings = new Set()) {
    if (ts.isStringLiteralLike(node) || ts.isTemplateLiteralToken(node)) {
      if (!recordLocaleKey(route, file, binding.source, node, binding.offset)
        && containsNaturalEnglish(node.text)) {
        addFinding(file, lineOf(binding.source, node.getStart()) + binding.offset, '脚本用户文案', node.text);
      }
      return;
    }

    if (ts.isIdentifier(node)) {
      const localBinding = findLocalBinding(lexicalScope, node.text);
      if (localBinding) {
        if (!visitedLocalBindings.has(localBinding)) {
          const nextVisitedLocalBindings = new Set(visitedLocalBindings);
          nextVisitedLocalBindings.add(localBinding);
          visitVisibleResult(localBinding.initializer, localBinding.scope, nextVisitedLocalBindings);
        }
        // 即使局部绑定形成循环，也不能穿透遮蔽继续解析同名顶层变量。
        return;
      }

      const nestedBinding = visibleBindings.get(node.text);
      if (nestedBinding && !visitedBindings.has(node.text)) {
        const nextVisitedBindings = new Set(visitedBindings);
        nextVisitedBindings.add(node.text);
        inspectVisibleBindingInitializer(route, file, nestedBinding, visibleBindings, nextVisitedBindings);
      }
      return;
    }

    if (ts.isTemplateExpression(node)) {
      visitVisibleResult(node.head, lexicalScope, visitedLocalBindings);
      node.templateSpans.forEach((span) => {
        visitVisibleResult(span.expression, lexicalScope, visitedLocalBindings);
        visitVisibleResult(span.literal, lexicalScope, visitedLocalBindings);
      });
      return;
    }

    if (ts.isConditionalExpression(node)) {
      visitVisibleResult(node.whenTrue, lexicalScope, visitedLocalBindings);
      visitVisibleResult(node.whenFalse, lexicalScope, visitedLocalBindings);
      return;
    }

    if (ts.isBinaryExpression(node)
      && [ts.SyntaxKind.PlusToken, ts.SyntaxKind.BarBarToken, ts.SyntaxKind.QuestionQuestionToken].includes(node.operatorToken.kind)) {
      visitVisibleResult(node.left, lexicalScope, visitedLocalBindings);
      visitVisibleResult(node.right, lexicalScope, visitedLocalBindings);
      return;
    }

    if (ts.isParenthesizedExpression(node)
      || ts.isAsExpression(node)
      || ts.isTypeAssertionExpression(node)
      || ts.isNonNullExpression(node)
      || ts.isAwaitExpression(node)) {
      visitVisibleResult(node.expression, lexicalScope, visitedLocalBindings);
      return;
    }

    if (ts.isPropertyAccessExpression(node)) {
      visitVisibleResult(node.expression, lexicalScope, visitedLocalBindings);
      return;
    }

    if (ts.isArrowFunction(node) || ts.isFunctionExpression(node)) {
      if (ts.isBlock(node.body)) {
        visitFunctionBody(node.body, lexicalScope, visitedLocalBindings);
      }
      else {
        visitVisibleResult(node.body, lexicalScope, visitedLocalBindings);
      }
      return;
    }

    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)
      && ['computed', 'ref', 'shallowRef'].includes(node.expression.text)
      && node.arguments[0]) {
      visitVisibleResult(node.arguments[0], lexicalScope, visitedLocalBindings);
    }
  }

  visitVisibleResult(statement.expression, undefined);
}

function inspectTypescript(
  route,
  file,
  source,
  offset = 0,
  rootExpressionVisible = false,
  visibleBindings = new Map(),
  visitedBindings = new Set(),
) {
  const sourceFile = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);

  function visit(node) {
    if (rootExpressionVisible && ts.isIdentifier(node) && isIdentifierReference(node)) {
      const binding = visibleBindings.get(node.text);
      if (binding && !visitedBindings.has(node.text)) {
        const nextVisitedBindings = new Set(visitedBindings);
        nextVisitedBindings.add(node.text);
        inspectVisibleBindingInitializer(route, file, binding, visibleBindings, nextVisitedBindings);
      }
    }

    const isStringLiteral = ts.isStringLiteralLike(node);
    const isTemplateFragment = ts.isTemplateLiteralToken(node);
    if (isStringLiteral || isTemplateFragment) {
      if (isStringLiteral && recordLocaleKey(route, file, source, node, offset)) {
        return;
      }
      if (isUserVisibleScriptString(node, file, rootExpressionVisible, route) && containsNaturalEnglish(node.text)) {
        addFinding(file, lineOf(source, node.getStart()) + offset, '脚本用户文案', node.text);
      }
    }
    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
}

function collectVisibleBindings(file, source, offset = 0) {
  const bindings = new Map();
  const sourceFile = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);

  // 模板只能访问模块/`script setup` 顶层绑定，不能让函数内部同名变量覆盖真实来源。
  for (const statement of sourceFile.statements) {
    if (!ts.isVariableStatement(statement)) {
      continue;
    }
    for (const declaration of statement.declarationList.declarations) {
      if (ts.isIdentifier(declaration.name) && declaration.initializer) {
        bindings.set(declaration.name.text, {
          source: declaration.initializer.getText(sourceFile),
          offset: offset + lineOf(source, declaration.initializer.getStart()) - 1,
        });
      }
    }
  }
  return bindings;
}

function inspectTemplate(route, file, source, template, visibleBindings) {
  const result = compileTemplate({
    filename: file,
    id: file,
    source: template.content,
    compilerOptions: { expressionPlugins: ['typescript'] },
  });
  if (result.errors.length > 0) {
    result.errors.forEach(error => addFinding(file, template.loc.start.line, '模板解析错误', String(error)));
    return;
  }

  function inspectExpression(expression, line, { interpolation = false, visibleAttribute } = {}) {
    const expressionSource = expression?.loc?.source ?? expression?.content;
    if (!expressionSource) {
      return;
    }
    const rootExpressionVisible = interpolation || USER_VISIBLE_ATTRIBUTES.has(visibleAttribute);
    inspectTypescript(
      route,
      file,
      expressionSource,
      template.loc.start.line + line - 2,
      rootExpressionVisible,
      visibleBindings,
    );
  }

  function visit(node) {
    if (node.type === 2 && containsNaturalEnglish(node.content)) {
      addFinding(file, template.loc.start.line + node.loc.start.line - 1, '模板可见文本', node.content);
    }

    if (node.type === 1) {
      for (const prop of node.props) {
        if (prop.type === 6 && USER_VISIBLE_ATTRIBUTES.has(prop.name) && containsNaturalEnglish(prop.value?.content ?? '')) {
          addFinding(file, template.loc.start.line + prop.loc.start.line - 1, `静态属性 ${prop.name}`, prop.value.content);
        }
        // 动态绑定中还可能内联 options/items 等对象；由 TS AST 只检查其中的用户文案字段。
        if (prop.type === 7) {
          const visibleAttribute = prop.name === 'bind' && prop.arg?.type === 4 && prop.arg.isStatic
            ? prop.arg.content
            : undefined;
          inspectExpression(prop.exp, prop.loc.start.line, { visibleAttribute });
        }
      }
    }

    if (node.type === 5) {
      inspectExpression(node.content, node.loc.start.line, { interpolation: true });
    }

    for (const child of node.children ?? []) {
      visit(child);
    }
    for (const branch of node.branches ?? []) {
      visit(branch);
    }
  }

  visit(result.ast);
}

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...await walk(path));
    }
    else if (['.md', '.ts', '.vue'].includes(extname(entry.name))
      && !/(?:\.test|\.spec|\.e2e\.spec)\.[^.]+$/.test(entry.name)
      && entry.name !== 'demo.vue') {
      files.push(path);
    }
  }
  return files;
}

async function pathExists(path) {
  try {
    await stat(path);
    return true;
  }
  catch (error) {
    if (error?.code === 'ENOENT') {
      return false;
    }
    throw error;
  }
}

async function validateToolRegistry() {
  // 审计 fixture 可以只测语法分支；真实构建必须把清单与实际 toolsByCategory 双向对齐。
  if (!await pathExists(TOOL_REGISTRY)) {
    return;
  }

  const source = await readFile(TOOL_REGISTRY, 'utf8');
  const imports = new Map();
  for (const match of source.matchAll(/import\s*\{\s*tool\s+as\s+([A-Za-z0-9_$]+)\s*\}\s*from\s*['"]\.\/([^'"]+)['"]/g)) {
    imports.set(match[1], match[2]);
  }
  const registryStart = source.indexOf('export const toolsByCategory');
  const registryEnd = source.indexOf('export const tools =', registryStart);
  const registryBody = registryStart >= 0 && registryEnd > registryStart
    ? source.slice(registryStart, registryEnd)
    : '';
  const registeredDirectories = new Set(
    [...imports].filter(([alias]) => new RegExp(`\\b${alias}\\b`).test(registryBody)).map(([, directory]) => directory),
  );
  const expectedDirectories = new Set([...ROUTE_DIRECTORIES.values(), 'camera-recorder']);
  const missing = [...expectedDirectories].filter(directory => !registeredDirectories.has(directory));
  const unexpected = [...registeredDirectories].filter(directory => !expectedDirectories.has(directory));

  if (missing.length > 0 || unexpected.length > 0 || registeredDirectories.size !== expectedDirectories.size) {
    addFinding(
      TOOL_REGISTRY,
      1,
      '工具注册表与 85 条审计路由不一致',
      `缺少: ${missing.join(', ') || '无'}; 多出: ${unexpected.join(', ') || '无'}`,
    );
  }
}

async function inspectSharedVisibleSources() {
  const files = [];
  for (const file of SHARED_VISIBLE_FILES) {
    if (await pathExists(file)) {
      files.push(file);
    }
  }
  if (await pathExists(SHARED_UI_ROOT)) {
    files.push(...(await walk(SHARED_UI_ROOT)).filter(file =>
      file.endsWith('.vue') && !file.includes('/demo/') && !file.endsWith('.demo.vue')));
  }
  for (const file of new Set(files)) {
    await inspectFile('shared', file);
  }
}

async function inspectFile(route, file) {
  const source = await readFile(file, 'utf8');
  // 先收集原文件中的全部字面量位置；AST 再负责识别可见文案，并按文件/行去重同一 key。
  for (const match of source.matchAll(/['"]((?:tools|common)\.[A-Za-z0-9_.-]+)['"]/g)) {
    if (LOCALE_KEY_PATTERN.test(match[1])) {
      recordLocaleOccurrence(match[1], { route, file, line: lineOf(source, match.index) });
    }
  }
  if (file.endsWith('.vue')) {
    const { descriptor, errors } = parseSfc(source, { filename: file });
    errors.forEach(error => addFinding(file, 1, 'SFC 解析错误', String(error)));
    const visibleBindings = new Map();
    for (const script of [descriptor.script, descriptor.scriptSetup]) {
      if (script) {
        for (const [name, binding] of collectVisibleBindings(file, script.content, script.loc.start.line - 1)) {
          visibleBindings.set(name, binding);
        }
        inspectTypescript(route, file, script.content, script.loc.start.line - 1);
      }
    }
    if (descriptor.template) {
      inspectTemplate(route, file, source, descriptor.template, visibleBindings);
    }
    return;
  }

  if (file.endsWith('.ts')) {
    inspectTypescript(route, file, source);
    return;
  }

  let inFence = false;
  source.split('\n').forEach((line, index) => {
    if (line.trim().startsWith('```')) {
      inFence = !inFence;
    }
    else if (!inFence && containsNaturalEnglish(line)) {
      addFinding(file, index + 1, 'Markdown 可见文本', line);
    }
  });
}

function getNestedValue(root, key) {
  return key.split('.').reduce((value, part) => value?.[part], root);
}

function collectLeafKeys(value, prefix = '', keys = new Set()) {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    for (const [part, child] of Object.entries(value)) {
      collectLeafKeys(child, prefix ? `${prefix}.${part}` : part, keys);
    }
  }
  else if (prefix) {
    keys.add(prefix);
  }
  return keys;
}

async function validateLocales() {
  const enFile = join(PROJECT_ROOT, 'locales/en.yml');
  const zhFile = join(PROJECT_ROOT, 'locales/zh.yml');
  const [en, zh] = await Promise.all([
    readFile(enFile, 'utf8').then(parseYaml),
    readFile(zhFile, 'utf8').then(parseYaml),
  ]);

  const enLeafKeys = collectLeafKeys(en);
  const zhLeafKeys = collectLeafKeys(zh);
  for (const key of enLeafKeys) {
    if (!zhLeafKeys.has(key)) {
      addFinding(enFile, 1, '中文 locale 缺失', key);
    }
  }
  for (const key of zhLeafKeys) {
    if (!enLeafKeys.has(key)) {
      addFinding(zhFile, 1, '英文 locale 缺失', key);
    }
  }

  for (const [key, locations] of usedLocaleKeys) {
    for (const location of locations) {
      const allowedPrefix = key === `tools.${location.route}.title`
        || key === `tools.${location.route}.description`
        || key.startsWith(`tools.${location.route}.ui.`)
        || key.startsWith('common.');
      if (!allowedPrefix) {
        addFinding(location.file, location.line, 'locale key 越界', key);
      }
    }

    const [location] = locations;
    const enValue = getNestedValue(en, key);
    const zhValue = getNestedValue(zh, key);
    if (typeof enValue !== 'string' || enValue.trim() === '') {
      addFinding(location.file, location.line, '英文 locale 缺失', key);
    }
    if (typeof zhValue !== 'string' || zhValue.trim() === '') {
      addFinding(location.file, location.line, '中文 locale 缺失', key);
    }
    else if (containsNaturalEnglish(zhValue)) {
      addFinding(location.file, location.line, '中文 locale 仍含自然语言英文', `${key}: ${zhValue}`);
    }
  }
}

if (ROUTE_DIRECTORIES.size !== 85) {
  throw new Error(`IT-Tools 路由数量必须为 85，实际为 ${ROUTE_DIRECTORIES.size}`);
}

await validateToolRegistry();
for (const [route, directory] of ROUTE_DIRECTORIES) {
  const files = await walk(join(TOOLS_ROOT, directory));
  for (const file of files) {
    await inspectFile(route, file);
  }
}
await inspectSharedVisibleSources();
await validateLocales();

findings.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line || a.kind.localeCompare(b.kind));

if (findings.length > 0) {
  console.error(`IT-Tools 中文 UI 审计失败：发现 ${findings.length} 项。`);
  for (const finding of findings) {
    console.error(`${finding.file}:${finding.line} [${finding.kind}] ${finding.value}`);
  }
  process.exitCode = 1;
}
else {
  console.log(`IT-Tools 中文 UI 审计通过：85 个路由，${usedLocaleKeys.size} 个已用 locale key。`);
  console.log(`技术词白名单（${TECHNICAL_TERMS.size}）：${[...TECHNICAL_TERMS].sort().join(', ')}`);
  console.log(`精确技术文本白名单（${EXACT_TECHNICAL_TEXT.size}）：${[...EXACT_TECHNICAL_TEXT].join(' | ')}`);
  console.log(`示例文本白名单（${EXACT_EXAMPLE_TEXT.size}）：${[...EXACT_EXAMPLE_TEXT].join(' | ')}`);
}
