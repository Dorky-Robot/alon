#!/usr/bin/env node
// CLI: node parse_js.mjs <entry-file>
// Emits a JSON graph { nodes: [...], edges: [...], root } to stdout.
// BFS-walks reachable local files via relative imports so the graph spans
// every reachable module, and call edges can cross file boundaries.

import fs from 'node:fs/promises';
import path from 'node:path';
import { resolveLocal } from './resolve.mjs';
import { parseFile, collectCalls } from './visitors.mjs';

async function buildGraph(entryFile) {
  const rootDir = path.dirname(entryFile);
  const queue = [entryFile];
  const allFiles = new Map();

  while (queue.length) {
    const file = queue.shift();
    if (allFiles.has(file)) continue;
    const code = await fs.readFile(file, 'utf8');
    const rec = parseFile(file, code, rootDir);
    allFiles.set(file, rec);

    // Resolve each import binding's spec to an absolute file path so the
    // second pass can chase callees across files. Bare specifiers stay null.
    for (const [, binding] of rec.importBindings) {
      if (binding.resolvedFile !== undefined) continue;
      binding.resolvedFile = await resolveLocal(binding.spec, file);
      if (binding.resolvedFile && !allFiles.has(binding.resolvedFile)) {
        queue.push(binding.resolvedFile);
      }
    }
  }

  const allNodes = [];
  const allEdges = [];
  for (const file of allFiles.values()) allNodes.push(...file.nodes);
  for (const file of allFiles.values()) allEdges.push(...collectCalls(file, allFiles));

  return { nodes: allNodes, edges: allEdges, root: entryFile };
}

const entry = process.argv[2];
if (!entry) {
  process.stderr.write('usage: node parse_js.mjs <entry-file>\n');
  process.exit(2);
}

try {
  const graph = await buildGraph(path.resolve(entry));
  process.stdout.write(JSON.stringify(graph));
} catch (err) {
  process.stderr.write(`parse_js: ${err.message}\n`);
  process.exit(1);
}
