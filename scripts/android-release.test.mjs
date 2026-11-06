import test from 'node:test';
import assert from 'node:assert/strict';
import { assertTagAvailable, calculateNextRelease } from './android-release.mjs';

test('first public release follows the installed 1.0.0 / code 2 baseline', () => {
  assert.deepEqual(calculateNextRelease([]), { versionName: '1.0.1', versionCode: 3, tag: 'v1.0.1' });
});

test('patch releases advance and version codes never depend on Actions run numbers', () => {
  assert.deepEqual(calculateNextRelease(['v1.0.1']), { versionName: '1.0.2', versionCode: 4, tag: 'v1.0.2' });
  assert.deepEqual(calculateNextRelease(['v1.0.1', 'v1.0.2']), { versionName: '1.0.3', versionCode: 5, tag: 'v1.0.3' });
});

test('version selection follows the newest stable release tag', () => {
  assert.deepEqual(calculateNextRelease(['v1.0.1', 'v1.2.0']), { versionName: '1.2.1', versionCode: 5, tag: 'v1.2.1' });
});

test('duplicate tag conflicts and invalid baselines fail closed', () => {
  assert.throws(() => assertTagAvailable('v1.0.1', ['v1.0.1']), /already exists/);
  assert.throws(() => calculateNextRelease([], 'v1'), /Invalid baseline/);
});
