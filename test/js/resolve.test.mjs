import { test } from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import url from 'node:url';

import { resolveLocal } from '../../js/resolve.mjs';

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const fixDir = path.join(__dirname, 'fixtures');

test('resolveLocal resolves relative .mjs', async () => {
  const from = path.join(fixDir, 'simple.mjs');
  const resolved = await resolveLocal('./helper.mjs', from);
  assert.equal(resolved, path.join(fixDir, 'helper.mjs'));
});

test('resolveLocal returns null for bare specifiers', async () => {
  const from = path.join(fixDir, 'simple.mjs');
  assert.equal(await resolveLocal('@babel/parser', from), null);
});

test('resolveLocal tries extension candidates', async () => {
  const from = path.join(fixDir, 'simple.mjs');
  const resolved = await resolveLocal('./helper', from);
  assert.equal(resolved, path.join(fixDir, 'helper.mjs'));
});

test('resolveLocal returns null when target missing', async () => {
  const from = path.join(fixDir, 'simple.mjs');
  assert.equal(await resolveLocal('./does-not-exist', from), null);
});
