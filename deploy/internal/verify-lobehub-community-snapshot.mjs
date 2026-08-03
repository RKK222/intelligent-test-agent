#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

const EXPECTED_SOURCE = {
  catalogUrl: 'https://market.lobehub.com/api/v1/agents/onboarding-full',
  communityBaseUrl: 'https://lobehub.com',
  license: {
    name: 'MIT',
    url: 'https://github.com/lobehub/lobe-chat-agents/blob/main/LICENSE',
  },
  marketBaseUrl: 'https://market.lobehub.com',
  repositoryUrl: 'https://github.com/lobehub/lobe-chat-agents',
};

const SUPPORTED_CATEGORIES = new Set([
  'business-strategy',
  'content-creation',
  'creator-economy',
  'design-creative',
  'engineering',
  'finance-legal',
  'learning-research',
  'marketing',
  'operations',
  'people-hr',
  'personal-life',
  'product-management',
  'sales-customer',
]);

const fail = (message) => {
  throw new Error(message);
};

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');

const requireObject = (value, label) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    fail(`${label} must be an object`);
  }
  return value;
};

const selectionShape = (value) => {
  const selection = requireObject(value, 'snapshot selection');
  if (selection.schemaVersion !== 1) fail('snapshot selection schemaVersion must be 1');
  if (typeof selection.includeCuratedOnboarding !== 'boolean') {
    fail('snapshot selection includeCuratedOnboarding must be a boolean');
  }
  if (!Array.isArray(selection.additionalAgents)) {
    fail('snapshot selection additionalAgents must be an array');
  }

  return {
    additionalAgents: selection.additionalAgents.map((item, index) => {
      const entry = requireObject(item, `additionalAgents[${index}]`);
      if (typeof entry.identifier !== 'string' || entry.identifier.trim() === '') {
        fail(`additionalAgents[${index}].identifier is required`);
      }
      if (entry.category !== undefined && !SUPPORTED_CATEGORIES.has(entry.category)) {
        fail(`additionalAgents[${index}].category is unsupported`);
      }
      return { category: entry.category, identifier: entry.identifier };
    }),
    includeCuratedOnboarding: selection.includeCuratedOnboarding,
    schemaVersion: 1,
  };
};

const verifySnapshot = async (forkDirectory) => {
  const snapshotPath = path.join(
    forkDirectory,
    'src/services/communityAgentSnapshot.snapshot.json',
  );
  const selectionPath = path.join(
    forkDirectory,
    'scripts/community-agent-snapshot.selection.json',
  );
  const avatarDirectory = path.join(
    forkDirectory,
    'public/community-agent-snapshot/avatars',
  );

  const [snapshotBytes, selectionBytes, avatarEntries] = await Promise.all([
    readFile(snapshotPath),
    readFile(selectionPath),
    readdir(avatarDirectory, { withFileTypes: true }),
  ]);
  const snapshot = requireObject(JSON.parse(snapshotBytes.toString('utf8')), 'Community snapshot');
  const selection = selectionShape(JSON.parse(selectionBytes.toString('utf8')));

  if (snapshot.schemaVersion !== 1) fail('Community snapshot schemaVersion must be 1');
  if (snapshot.locale !== 'zh-CN') fail('Community snapshot locale must be zh-CN');
  if (typeof snapshot.fetchedAt !== 'string') fail('Community snapshot fetchedAt is required');
  const fetchedAt = new Date(snapshot.fetchedAt);
  if (Number.isNaN(fetchedAt.valueOf()) || fetchedAt.toISOString() !== snapshot.fetchedAt) {
    fail('Community snapshot fetchedAt must be a canonical UTC timestamp');
  }
  if (JSON.stringify(selectionShape(snapshot.selection)) !== JSON.stringify(selection)) {
    fail('Generated Community snapshot selection does not match the committed selection file');
  }

  const source = requireObject(snapshot.source, 'Community snapshot source');
  for (const [key, expected] of Object.entries(EXPECTED_SOURCE)) {
    if (typeof expected === 'object') {
      if (JSON.stringify(source[key]) !== JSON.stringify(expected)) {
        fail(`Community snapshot source.${key} does not match the approved value`);
      }
    } else if (source[key] !== expected) {
      fail(`Community snapshot source.${key} does not match the approved value`);
    }
  }

  if (!Array.isArray(snapshot.agents) || snapshot.agents.length === 0) {
    fail('Community snapshot must contain at least one Agent');
  }
  const shippedAssets = new Map();
  for (const entry of avatarEntries) {
    if (!entry.isFile() || !/^[0-9a-f]{64}\.(gif|jpg|png|webp)$/.test(entry.name)) {
      fail(`Unexpected Community avatar entry: ${entry.name}`);
    }
    const bytes = await readFile(path.join(avatarDirectory, entry.name));
    shippedAssets.set(entry.name, sha256(bytes));
  }

  const identifiers = new Set();
  const referencedAssets = new Set();
  for (const [index, agentValue] of snapshot.agents.entries()) {
    const agent = requireObject(agentValue, `agents[${index}]`);
    if (typeof agent.identifier !== 'string' || !/^[a-z0-9][a-z0-9._-]*$/.test(agent.identifier)) {
      fail(`agents[${index}].identifier is invalid`);
    }
    if (identifiers.has(agent.identifier)) fail(`Duplicate Community Agent: ${agent.identifier}`);
    identifiers.add(agent.identifier);
    if (agent.isOfficial !== true || agent.isValidated !== true) {
      fail(`Community Agent is not official and validated: ${agent.identifier}`);
    }
    if (typeof agent.name !== 'string' || agent.name.trim() === '') {
      fail(`Community Agent name is missing: ${agent.identifier}`);
    }
    if (!SUPPORTED_CATEGORIES.has(agent.category)) {
      fail(`Community Agent category is unsupported: ${agent.identifier}`);
    }
    const config = requireObject(agent.config, `${agent.identifier}.config`);
    if (typeof config.systemRole !== 'string' || config.systemRole.trim() === '') {
      fail(`Community Agent systemRole is missing: ${agent.identifier}`);
    }
    if (!Array.isArray(config.plugins) || config.plugins.length > 0) {
      fail(`Community Agent plugins must be an empty array: ${agent.identifier}`);
    }
    if (!Array.isArray(config.knowledgeBases) || config.knowledgeBases.length > 0) {
      fail(`Community Agent knowledgeBases must be an empty array: ${agent.identifier}`);
    }
    const author = requireObject(agent.author, `${agent.identifier}.author`);
    if (![author.name, author.userName].some((value) => typeof value === 'string' && value.trim())) {
      fail(`Community Agent author is missing: ${agent.identifier}`);
    }
    if (agent.sourceUrl !== `https://lobehub.com/agent/${encodeURIComponent(agent.identifier)}`) {
      fail(`Community Agent source URL is invalid: ${agent.identifier}`);
    }
    try {
      if (new URL(agent.avatarSourceUrl).protocol !== 'https:') throw new Error();
    } catch {
      fail(`Community Agent avatar source must use HTTPS: ${agent.identifier}`);
    }
    const avatarMatch = /^\/community-agent-snapshot\/avatars\/([^/]+)$/.exec(agent.avatar);
    if (!avatarMatch) fail(`Community Agent avatar path is invalid: ${agent.identifier}`);
    const filename = avatarMatch[1];
    if (!/^[0-9a-f]{64}\.(gif|jpg|png|webp)$/.test(filename)) {
      fail(`Community Agent avatar filename is invalid: ${agent.identifier}`);
    }
    if (!/^[0-9a-f]{64}$/.test(agent.avatarSha256)) {
      fail(`Community Agent avatar SHA-256 is invalid: ${agent.identifier}`);
    }
    if (!shippedAssets.has(filename) || shippedAssets.get(filename) !== agent.avatarSha256) {
      fail(`Community Agent avatar hash mismatch: ${agent.identifier}`);
    }
    if (!filename.startsWith(agent.avatarSha256)) {
      fail(`Community Agent avatar filename does not match its hash: ${agent.identifier}`);
    }
    referencedAssets.add(filename);
  }

  if (referencedAssets.size !== shippedAssets.size) {
    fail('Community avatar directory contains an unreferenced asset');
  }

  const assetSetSha256 = sha256(
    [...shippedAssets]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([filename, digest]) => `${filename}\t${digest}\n`)
      .join(''),
  );
  return {
    agentCount: snapshot.agents.length,
    assetSetSha256,
    fetchedAt: snapshot.fetchedAt,
    license: EXPECTED_SOURCE.license,
    repositoryUrl: EXPECTED_SOURCE.repositoryUrl,
    selectionSha256: sha256(selectionBytes),
    snapshotSha256: sha256(snapshotBytes),
  };
};

const main = async () => {
  const forkDirectory = process.argv[2];
  if (!forkDirectory || process.argv.length !== 3) {
    fail('Usage: node verify-lobehub-community-snapshot.mjs <lobehub-fork-directory>');
  }
  const result = await verifySnapshot(path.resolve(forkDirectory));
  console.log(`COMMUNITY_AGENT_COUNT=${result.agentCount}`);
  console.log(`COMMUNITY_SNAPSHOT_FETCHED_AT=${result.fetchedAt}`);
  console.log(`COMMUNITY_SNAPSHOT_SHA256=${result.snapshotSha256}`);
  console.log(`COMMUNITY_SELECTION_SHA256=${result.selectionSha256}`);
  console.log(`COMMUNITY_ASSET_SET_SHA256=${result.assetSetSha256}`);
  console.log(`COMMUNITY_SOURCE_REPOSITORY=${result.repositoryUrl}`);
  console.log(`COMMUNITY_SOURCE_LICENSE=${result.license.name}`);
  console.log(`COMMUNITY_SOURCE_LICENSE_URL=${result.license.url}`);
};

main().catch((error) => {
  console.error(`Community snapshot verification failed: ${error.message}`);
  process.exitCode = 1;
});
