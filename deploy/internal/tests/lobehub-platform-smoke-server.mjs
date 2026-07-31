import { createHash, createHmac, timingSafeEqual } from 'node:crypto';
import { createServer } from 'node:http';

const secret = process.env.PLATFORM_SSO_HMAC_SECRET ?? '';
const port = Number.parseInt(process.env.SMOKE_PLATFORM_PORT ?? '8080', 10);
const revokePath = '/api/internal/platform/lobehub-sso/grants/revoke';
const consumedNonces = new Set();

if (Buffer.byteLength(secret, 'utf8') < 32 || !Number.isInteger(port) || port < 1 || port > 65_535) {
  console.error('invalid smoke platform configuration');
  process.exit(1);
}

const respond = (response, status, payload) => {
  response.writeHead(status, { 'content-type': 'application/json' });
  response.end(JSON.stringify(payload));
};

const header = (request, name) => {
  const value = request.headers[name];
  return typeof value === 'string' ? value : '';
};

const signaturesMatch = (actual, expected) => {
  const actualBytes = Buffer.from(actual, 'utf8');
  const expectedBytes = Buffer.from(expected, 'utf8');
  return actualBytes.length === expectedBytes.length && timingSafeEqual(actualBytes, expectedBytes);
};

const server = createServer(async (request, response) => {
  if (request.method !== 'POST' || request.url !== revokePath) {
    respond(response, 404, { code: 'NOT_FOUND' });
    return;
  }

  const chunks = [];
  let bytes = 0;
  for await (const chunk of request) {
    bytes += chunk.length;
    if (bytes > 16_384) {
      respond(response, 413, { code: 'PAYLOAD_TOO_LARGE' });
      return;
    }
    chunks.push(chunk);
  }
  const body = Buffer.concat(chunks).toString('utf8');
  const timestamp = header(request, 'x-lobehub-timestamp');
  const nonce = header(request, 'x-lobehub-nonce');
  const signature = header(request, 'x-lobehub-signature');
  const timestampNumber = Number(timestamp);
  const digest = createHash('sha256').update(body, 'utf8').digest('hex');
  const canonical = [timestamp, nonce, 'POST', revokePath, digest].join('\n');
  const expected = createHmac('sha256', secret).update(canonical, 'utf8').digest('base64url');
  const validPayload = (() => {
    try {
      const parsed = JSON.parse(body);
      return typeof parsed.modelGrant === 'string' && parsed.modelGrant.startsWith('preflight_');
    } catch {
      return false;
    }
  })();

  if (
    !validPayload ||
    !Number.isInteger(timestampNumber) ||
    Math.abs(Math.floor(Date.now() / 1000) - timestampNumber) > 60 ||
    !/^[A-Za-z0-9_-]{24,128}$/.test(nonce) ||
    !signaturesMatch(signature, expected) ||
    consumedNonces.has(nonce)
  ) {
    respond(response, 401, { code: 'UNAUTHENTICATED' });
    return;
  }

  consumedNonces.add(nonce);
  respond(response, 200, { code: 'SUCCESS', data: { revoked: true } });
});

server.listen(port, '0.0.0.0', () => {
  console.log('LobeHub smoke platform is ready');
});

const shutdown = () => server.close(() => process.exit(0));
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
