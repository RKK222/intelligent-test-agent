import { createHash, createHmac, randomBytes } from 'node:crypto';

const secret = process.env.PLATFORM_SSO_HMAC_SECRET ?? '';
const revokeUrl = process.env.PLATFORM_SSO_REVOKE_URL ?? '';

const fail = (message) => {
  console.error(message);
  process.exitCode = 1;
};

const main = async () => {
  if (Buffer.byteLength(secret, 'utf8') < 32) {
    throw new Error('platform HMAC verification requires a 32-byte secret');
  }

  const url = new URL(revokeUrl);
  if (
    url.protocol !== 'http:' ||
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    url.pathname !== '/api/internal/platform/lobehub-sso/grants/revoke'
  ) {
    throw new Error('platform HMAC verification requires the fixed HTTP revoke endpoint');
  }

  const body = JSON.stringify({ modelGrant: `preflight_${randomBytes(32).toString('base64url')}` });
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = randomBytes(24).toString('base64url');
  const digest = createHash('sha256').update(body, 'utf8').digest('hex');
  const canonical = [timestamp, nonce, 'POST', url.pathname, digest].join('\n');
  const signature = createHmac('sha256', secret).update(canonical, 'utf8').digest('base64url');
  const request = {
    body,
    headers: {
      'Content-Type': 'application/json',
      'X-LobeHub-Nonce': nonce,
      'X-LobeHub-Signature': signature,
      'X-LobeHub-Timestamp': timestamp,
    },
    method: 'POST',
    signal: AbortSignal.timeout(10_000),
  };

  const first = await fetch(url, request);
  if (!first.ok) {
    throw new Error(`platform HMAC verification failed (HTTP ${first.status})`);
  }
  const envelope = await first.json().catch(() => undefined);
  if (envelope?.data?.revoked !== true) {
    throw new Error('platform HMAC verification returned an invalid success envelope');
  }

  const replay = await fetch(url, request);
  if (replay.status !== 401) {
    throw new Error(`platform nonce replay was not rejected (HTTP ${replay.status})`);
  }

  console.log('LobeHub platform HMAC and nonce replay verification passed');
};

try {
  await main();
} catch (error) {
  fail(error instanceof Error ? error.message : 'platform HMAC verification failed');
}
