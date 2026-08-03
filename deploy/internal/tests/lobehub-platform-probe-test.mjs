import assert from 'node:assert/strict';
import { createHash, createHmac } from 'node:crypto';
import { spawn } from 'node:child_process';
import { chmod, mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { once } from 'node:events';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const TEST_DIR = path.dirname(fileURLToPath(import.meta.url));
const PROBE = path.resolve(TEST_DIR, '../lobehub-platform-probe.mjs');
const SMOKE_PLATFORM_SERVER = path.resolve(TEST_DIR, 'lobehub-platform-smoke-server.mjs');
const DOCKER_HELPER = path.resolve(TEST_DIR, '../lobehub-docker.sh');
const SECRET = '0123456789abcdef0123456789abcdef';

const runProbe = (url, secret = SECRET) =>
  new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [PROBE], {
      env: {
        PLATFORM_SSO_HMAC_SECRET: secret,
        PLATFORM_SSO_REVOKE_URL: url,
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stderr = '';
    let stdout = '';
    child.stderr.setEncoding('utf8');
    child.stdout.setEncoding('utf8');
    child.stderr.on('data', (chunk) => {
      stderr += chunk;
    });
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
    });
    child.once('error', reject);
    child.once('close', (exitCode) => resolve({ exitCode, stderr, stdout }));
  });

const withServer = async (handler, callback) => {
  const server = createServer(handler);
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('probe test server did not bind');
  try {
    await callback(`http://127.0.0.1:${address.port}/api/internal/platform/lobehub-sso/grants/revoke`);
  } finally {
    server.close();
    await once(server, 'close');
  }
};

const withSmokePlatformServer = async (callback) => {
  const portReservation = createServer();
  portReservation.listen(0, '127.0.0.1');
  await once(portReservation, 'listening');
  const address = portReservation.address();
  if (!address || typeof address === 'string') throw new Error('smoke platform port was not reserved');
  const { port } = address;
  portReservation.close();
  await once(portReservation, 'close');

  const child = spawn(process.execPath, [SMOKE_PLATFORM_SERVER], {
    env: { PLATFORM_SSO_HMAC_SECRET: SECRET, SMOKE_PLATFORM_PORT: String(port) },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stderr = '';
  child.stderr.setEncoding('utf8');
  child.stderr.on('data', (chunk) => {
    stderr += chunk;
  });
  child.stdout.setEncoding('utf8');
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`smoke platform did not start: ${stderr}`)), 5_000);
    child.stdout.on('data', (chunk) => {
      if (chunk.includes('LobeHub smoke platform is ready')) {
        clearTimeout(timeout);
        resolve();
      }
    });
    child.once('close', (exitCode) => {
      clearTimeout(timeout);
      reject(new Error(`smoke platform exited with ${exitCode}: ${stderr}`));
    });
    child.once('error', reject);
  });
  try {
    await callback(
      `http://127.0.0.1:${port}/api/internal/platform/lobehub-sso/grants/revoke`,
    );
  } finally {
    child.kill('SIGTERM');
    await once(child, 'close');
  }
};

test('platform probe proves a valid HMAC request and a rejected nonce replay', async () => {
  const requests = [];
  await withServer(async (request, response) => {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const body = Buffer.concat(chunks).toString('utf8');
    const timestamp = request.headers['x-lobehub-timestamp'];
    const nonce = request.headers['x-lobehub-nonce'];
    const signature = request.headers['x-lobehub-signature'];
    const digest = createHash('sha256').update(body).digest('hex');
    const canonical = [
      timestamp,
      nonce,
      'POST',
      '/api/internal/platform/lobehub-sso/grants/revoke',
      digest,
    ].join('\n');
    const expected = createHmac('sha256', SECRET).update(canonical).digest('base64url');
    requests.push({ body, nonce, signature, timestamp });

    if (signature !== expected) {
      response.writeHead(401, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ code: 'UNAUTHENTICATED', message: 'signature-secret' }));
      return;
    }
    if (requests.length === 1) {
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ code: 'SUCCESS', data: { revoked: true } }));
      return;
    }
    response.writeHead(401, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ code: 'UNAUTHENTICATED', message: 'replay-secret' }));
  }, async (url) => {
    const result = await runProbe(url);

    assert.equal(result.exitCode, 0, result.stderr);
    assert.match(result.stdout, /platform HMAC and nonce replay verification passed/);
    assert.equal(requests.length, 2);
    assert.equal(requests[1].body, requests[0].body);
    assert.equal(requests[1].nonce, requests[0].nonce);
    assert.equal(requests[1].signature, requests[0].signature);
    assert.doesNotMatch(`${result.stdout}${result.stderr}`, /signature-secret|replay-secret/);
    assert.doesNotMatch(`${result.stdout}${result.stderr}`, new RegExp(SECRET));
  });
});

test('runtime smoke platform validates the real probe contract', async () => {
  await withSmokePlatformServer(async (url) => {
    const result = await runProbe(url);
    assert.equal(result.exitCode, 0, result.stderr);
    assert.match(result.stdout, /platform HMAC and nonce replay verification passed/);
  });
});

test('platform probe fails closed without exposing a rejected response body', async () => {
  await withServer(async (request, response) => {
    for await (const _chunk of request) {
      // Drain the request so the real client completes normally.
    }
    response.writeHead(401, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ code: 'UNAUTHENTICATED', message: 'do-not-log-this-body' }));
  }, async (url) => {
    const result = await runProbe(url, 'fedcba9876543210fedcba9876543210');

    assert.notEqual(result.exitCode, 0);
    assert.match(result.stderr, /platform HMAC verification failed \(HTTP 401\)/);
    assert.doesNotMatch(result.stderr, /do-not-log-this-body/);
  });
});

test('offline deployment helper runs the real platform probe in the locked app image', async () => {
  let requestCount = 0;
  await withServer(async (request, response) => {
    for await (const _chunk of request) {
      // Drain the request; signature behavior is covered by the focused probe test above.
    }
    requestCount += 1;
    response.writeHead(requestCount === 1 ? 200 : 401, { 'content-type': 'application/json' });
    response.end(
      requestCount === 1
        ? JSON.stringify({ code: 'SUCCESS', data: { revoked: true } })
        : JSON.stringify({ code: 'UNAUTHENTICATED' }),
    );
  }, async (url) => {
    const fixture = await mkdtemp(path.join(os.tmpdir(), 'lobehub-platform-helper-'));
    const baseDir = path.join(fixture, 'testagent');
    const binDir = path.join(fixture, 'bin');
    const envFile = path.join(baseDir, 'config/lobehub.env');
    const releaseDir = path.join(baseDir, 'lobehub/release');
    const installedProbe = path.join(baseDir, 'deploy/internal/lobehub-platform-probe.mjs');
    const fakeDocker = path.join(binDir, 'docker');
    const origin = new URL(url).origin;
    await mkdir(path.dirname(envFile), { recursive: true });
    await mkdir(releaseDir, { recursive: true });
    await mkdir(path.dirname(installedProbe), { recursive: true });
    await mkdir(binDir, { recursive: true });
    await writeFile(installedProbe, await import('node:fs/promises').then(({ readFile }) => readFile(PROBE)));
    await writeFile(
      envFile,
      [
        'LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.7',
        'LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.7',
        'LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.7',
        'POSTGRES_DB=lobehub',
        'POSTGRES_USER=lobehub',
        'POSTGRES_PASSWORD=database-password-at-least-32-bytes',
        'DATABASE_URL=postgresql://lobehub:database-password-at-least-32-bytes@test-agent-lobehub-db:5432/lobehub',
        'DATABASE_DRIVER=node',
        'LOBEHUB_REDIS_HOST=redis.internal',
        'LOBEHUB_REDIS_PORT=6379',
        'LOBEHUB_REDIS_USERNAME=lobehub',
        'LOBEHUB_REDIS_PASSWORD=redis-password-at-least-32-bytes',
        'REDIS_URL=redis://lobehub:redis-password-at-least-32-bytes@redis.internal:6379/0',
        'REDIS_PREFIX=lobehub:app',
        'RUSTFS_ACCESS_KEY=rustfs-access-key-at-least-16',
        'RUSTFS_SECRET_KEY=rustfs-secret-key-at-least-thirty-two-bytes',
        'LOBEHUB_S3_BUCKET=lobehub-private',
        'S3_ENDPOINT=http://test-agent-lobehub-rustfs:9000',
        'S3_BUCKET=lobehub-private',
        'S3_ACCESS_KEY_ID=rustfs-access-key-at-least-16',
        'S3_SECRET_ACCESS_KEY=rustfs-secret-key-at-least-thirty-two-bytes',
        'S3_ENABLE_PATH_STYLE=1',
        'S3_SET_ACL=0',
        'MC_HOST_lobehub=http://rustfs-access-key-at-least-16:rustfs-secret-key-at-least-thirty-two-bytes@test-agent-lobehub-rustfs:9000',
        'APP_URL=http://chat.internal',
        'INTERNAL_APP_URL=http://test-agent-lobehub-app:3210',
        'LOBEHUB_APP_BIND_ADDRESS=127.0.0.1',
        'AUTH_SECRET=auth-secret-at-least-thirty-two-bytes',
        'KEY_VAULTS_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=',
        'ENTERPRISE_INTERNAL_SCHEDULER_SECRET=scheduler-secret-at-least-thirty-two-bytes',
        'PLATFORM_SSO_ENABLED=1',
        `PLATFORM_LAUNCH_URL=${origin}/lobehub/launch`,
        `PLATFORM_SSO_REDEEM_URL=${origin}/api/internal/platform/lobehub-sso/tickets/redeem`,
        `PLATFORM_SSO_REVOKE_URL=${url}`,
        `PLATFORM_MODEL_GATEWAY_BASE_URL=${origin}/api/internal/platform/model-gateway/v1`,
        `PLATFORM_SSO_HMAC_SECRET=${SECRET}`,
        'PLATFORM_MODEL_GRANT_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=',
        'LOBEHUB_ENTERPRISE_OFFLINE=1',
        'AGENT_RUNTIME_MODE=local',
        'TELEMETRY_DISABLED=1',
        'LOBEHUB_DEVICE_EXECUTION_MODE=disabled',
        '',
      ].join('\n'),
    );
    await chmod(envFile, 0o600);
    await writeFile(
      path.join(releaseDir, 'release.env'),
      [
        'LOBEHUB_APP_IMAGE=test-agent/lobehub:v2.2.11-platform.7',
        `LOBEHUB_APP_IMAGE_ID=sha256:${'a'.repeat(64)}`,
        'LOBEHUB_PARADEDB_IMAGE=test-agent/paradedb:pg17-v2.2.11-platform.7',
        `LOBEHUB_PARADEDB_IMAGE_ID=sha256:${'b'.repeat(64)}`,
        'LOBEHUB_RUSTFS_IMAGE=test-agent/rustfs:v2.2.11-platform.7',
        `LOBEHUB_RUSTFS_IMAGE_ID=sha256:${'c'.repeat(64)}`,
        '',
      ].join('\n'),
    );
    await writeFile(
      fakeDocker,
      `#!/usr/bin/env bash
set -euo pipefail
if [[ "\${1:-}" == network && "\${2:-}" == inspect ]]; then exit 0; fi
if [[ "\${1:-}" == image && "\${2:-}" == inspect && "\${3:-}" == -f ]]; then
  case "\${4:-}:\${5:-}" in
    '{{.Architecture}}':*) echo amd64 ;;
    '{{.Id}}':test-agent/lobehub:*) echo sha256:${'a'.repeat(64)} ;;
    '{{.Id}}':test-agent/paradedb:*) echo sha256:${'b'.repeat(64)} ;;
    '{{.Id}}':test-agent/rustfs:*) echo sha256:${'c'.repeat(64)} ;;
    *) exit 1 ;;
  esac
  exit 0
fi
if [[ "\${1:-}" == run ]]; then
  shift
  env_file=''
  mounted_probe=''
  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --rm|--read-only) shift ;;
      --network|--cap-drop|--security-opt|--pids-limit) shift 2 ;;
      --env-file) env_file="$2"; shift 2 ;;
      -v) mounted_probe="\${2%%:*}"; shift 2 ;;
      -*) exit 1 ;;
      *) shift; break ;;
    esac
  done
  [[ -n "\${env_file}" && -n "\${mounted_probe}" ]]
  set -a
  source "\${env_file}"
  set +a
  exec "\${LOBEHUB_TEST_NODE:?}" "\${mounted_probe}"
fi
exit 1
`,
    );
    await chmod(fakeDocker, 0o755);

    try {
      const result = await new Promise((resolve, reject) => {
        const child = spawn('bash', [DOCKER_HELPER, 'verify-platform'], {
          env: {
            ...process.env,
            LOBEHUB_ENV_FILE: envFile,
            LOBEHUB_TEST_NODE: process.execPath,
            PATH: `${binDir}:${process.env.PATH}`,
            TEST_AGENT_BASE_DIR: baseDir,
          },
          stdio: ['ignore', 'pipe', 'pipe'],
        });
        let stderr = '';
        let stdout = '';
        child.stderr.setEncoding('utf8');
        child.stdout.setEncoding('utf8');
        child.stderr.on('data', (chunk) => {
          stderr += chunk;
        });
        child.stdout.on('data', (chunk) => {
          stdout += chunk;
        });
        child.once('error', reject);
        child.once('close', (exitCode) => resolve({ exitCode, stderr, stdout }));
      });

      assert.equal(result.exitCode, 0, result.stderr);
      assert.match(result.stdout, /platform HMAC and nonce replay verification passed/);
      assert.equal(requestCount, 2);
    } finally {
      await rm(fixture, { force: true, recursive: true });
    }
  });
});
