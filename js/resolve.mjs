// Resolve a `./foo` / `../bar` specifier against `fromFile` to an absolute
// file path. Bare specifiers (node_modules) and unresolvable paths return
// null so the caller can skip them.

import fs from 'node:fs/promises';
import path from 'node:path';

export const RESOLVE_EXTS = ['', '.mjs', '.js', '.ts', '.tsx', '.jsx',
                             '/index.mjs', '/index.js', '/index.ts'];

export async function resolveLocal(spec, fromFile) {
  if (!spec.startsWith('.')) return null;
  const base = path.resolve(path.dirname(fromFile), spec);
  for (const ext of RESOLVE_EXTS) {
    const cand = base + ext;
    try {
      const stat = await fs.stat(cand);
      if (stat.isFile()) return cand;
    } catch {}
  }
  return null;
}
