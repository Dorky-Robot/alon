import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import url from 'node:url';

import { parseFile, collectCalls } from '../../js/visitors.mjs';

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const fixDir = path.join(__dirname, 'fixtures');

async function parseFixture(name) {
  const abs = path.join(fixDir, name);
  const code = await readFile(abs, 'utf8');
  return parseFile(abs, code, fixDir);
}

test('parseFile captures top-level function with signature', async () => {
  const rec = await parseFixture('simple.mjs');
  const outer = rec.nodes.find(n => n.name === 'outer');
  assert.ok(outer, 'outer node exists');
  assert.equal(outer.type, 'function');
  assert.equal(outer.signature, 'outer(name)');
  assert.equal(outer.parentId, null);
});

test('parseFile captures nested function with parentId pointing at enclosing fn', async () => {
  const rec = await parseFixture('simple.mjs');
  const outer = rec.nodes.find(n => n.name === 'outer');
  const inner = rec.nodes.find(n => n.name === 'inner');
  assert.ok(inner, 'inner node exists');
  assert.equal(inner.parentId, outer.id,
    'inner.parentId should point to outer.id');
});

test('parseFile captures top-level const without signature', async () => {
  const rec = await parseFixture('simple.mjs');
  const greeting = rec.nodes.find(n => n.name === 'GREETING');
  assert.ok(greeting);
  assert.equal(greeting.type, 'const');
  assert.equal(greeting.signature, null);
});

test('parseFile captures arrow const as function with signature', async () => {
  const rec = await parseFixture('simple.mjs');
  const arrow = rec.nodes.find(n => n.name === 'arrow');
  assert.ok(arrow);
  assert.equal(arrow.type, 'function');
  assert.equal(arrow.signature, 'arrow(n)');
});

test('parseFile emits an import row but does not put it in `declared`', async () => {
  const rec = await parseFixture('simple.mjs');
  const helperImport = rec.nodes.find(n => n.type === 'import' && n.name.startsWith('helper'));
  assert.ok(helperImport, 'import row is emitted');
  assert.equal(rec.declared.has('helper'), false,
    '`helper` should NOT be in declared — so calls resolve cross-file via importBindings');
});

test('node ids are unique within a file', async () => {
  const rec = await parseFixture('simple.mjs');
  const ids = rec.nodes.map(n => n.id);
  assert.equal(new Set(ids).size, ids.length, 'duplicate node ids');
});

test('collectCalls emits inner→helper call across files', async () => {
  const entry = await parseFixture('simple.mjs');
  const helperRec = await parseFixture('helper.mjs');
  const allFiles = new Map();
  allFiles.set(entry.filePath, entry);
  allFiles.set(helperRec.filePath, helperRec);
  // simulate resolve
  for (const [, b] of entry.importBindings) {
    b.resolvedFile = helperRec.filePath;
  }
  const edges = collectCalls(entry, allFiles);
  const innerId = entry.nodes.find(n => n.name === 'inner').id;
  const helperId = helperRec.nodes.find(n => n.name === 'helper').id;
  const match = edges.find(e => e.from === innerId && e.to === helperId);
  assert.ok(match, `expected edge inner→helper in ${JSON.stringify(edges)}`);
});

test('collectCalls emits outer→inner same-file call', async () => {
  const entry = await parseFixture('simple.mjs');
  const allFiles = new Map([[entry.filePath, entry]]);
  const edges = collectCalls(entry, allFiles);
  const outerId = entry.nodes.find(n => n.name === 'outer').id;
  const innerId = entry.nodes.find(n => n.name === 'inner').id;
  assert.ok(edges.find(e => e.from === outerId && e.to === innerId),
    `expected outer→inner in ${JSON.stringify(edges.map(e => [e.from, e.to]))}`);
});

test('parseFile captures object-literal methods as nested functions of the enclosing fn', async () => {
  const rec = await parseFixture('object-methods.mjs');
  const host = rec.nodes.find(n => n.name === 'host');
  assert.ok(host, 'host fn captured');

  const call = rec.nodes.find(n => n.name === 'CallExpression');
  assert.ok(call, 'object method CallExpression should be captured as a node');
  assert.equal(call.type, 'function');
  assert.equal(call.parentId, host.id,
    'object method should hang under the enclosing function that contains the object literal');
  assert.equal(call.signature, 'CallExpression(p)');

  const ident = rec.nodes.find(n => n.name === 'Identifier');
  assert.ok(ident, 'second object method Identifier captured');
  assert.equal(ident.parentId, host.id);

  const arrow = rec.nodes.find(n => n.name === 'shorthandArrow');
  assert.ok(arrow, 'function-valued ObjectProperty captured');
  assert.equal(arrow.type, 'function');
  assert.equal(arrow.parentId, host.id);
  assert.equal(arrow.signature, 'shorthandArrow(p)');
});

test('call-edge offsets are relative to enclosing function', async () => {
  const entry = await parseFixture('simple.mjs');
  const allFiles = new Map([[entry.filePath, entry]]);
  const edges = collectCalls(entry, allFiles);
  const outerId = entry.nodes.find(n => n.name === 'outer').id;
  const innerCall = edges.find(e => e.from === outerId);
  assert.ok(innerCall);
  assert.ok(innerCall.offsetStart >= 0, 'offsetStart is relative (non-negative)');
  const outer = entry.nodes.find(n => n.name === 'outer');
  const callSize = innerCall.offsetEnd - innerCall.offsetStart;
  assert.ok(callSize > 0 && callSize < (outer.end - outer.start),
    'call range fits within enclosing fn');
});
