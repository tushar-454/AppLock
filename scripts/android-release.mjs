#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';

export const BASE_VERSION = '1.0.0';
export const BASE_VERSION_CODE = 2;

function parseVersion(value) {
  const match = /^v(\d+)\.(\d+)\.(\d+)$/.exec(value);
  return match ? match.slice(1).map(Number) : null;
}

function compareVersions(a, b) {
  for (let i = 0; i < 3; i += 1) {
    if (a[i] !== b[i]) return a[i] - b[i];
  }
  return 0;
}

export function assertTagAvailable(tag, tags) {
  if (tags.includes(tag)) throw new Error(`Release tag already exists: ${tag}`);
}

export function calculateNextRelease(tags, baselineVersion = BASE_VERSION, baselineCode = BASE_VERSION_CODE) {
  const baseline = parseVersion(`v${baselineVersion}`);
  if (!baseline) throw new Error(`Invalid baseline version: ${baselineVersion}`);
  if (!Number.isSafeInteger(baselineCode) || baselineCode < 1) throw new Error('Invalid baseline version code.');

  const releases = [...new Set(tags)].map((tag) => ({ tag, version: parseVersion(tag) })).filter((item) => item.version);
  const previous = releases.reduce((latest, item) => (
    compareVersions(item.version, latest) > 0 ? item.version : latest
  ), baseline);
  const next = [...previous];
  next[2] += 1;
  const versionName = next.join('.');
  const tag = `v${versionName}`;

  assertTagAvailable(tag, tags);

  // Every published vX.Y.Z tag consumes one code after the installed 1.0.0/code 2 baseline.
  const versionCode = baselineCode + releases.length + 1;
  if (!Number.isSafeInteger(versionCode)) throw new Error('Calculated version code exceeds the safe integer range.');
  return { versionName, versionCode, tag };
}

export function writeGitHubOutput(release, outputFile) {
  appendFileSync(outputFile, `version_name=${release.versionName}\nversion_code=${release.versionCode}\ntag=${release.tag}\n`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const tags = execFileSync('git', ['tag', '--list'], { encoding: 'utf8' }).trim().split('\n').filter(Boolean);
  const release = calculateNextRelease(tags);
  if (process.env.GITHUB_OUTPUT) writeGitHubOutput(release, process.env.GITHUB_OUTPUT);
  console.log(JSON.stringify(release));
}
