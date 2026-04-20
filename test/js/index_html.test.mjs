import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

function nsToSrcPath(ns) {
  // alon-ui.file-card → /cljs/alon_ui/file_card.cljs
  return '/cljs/' + ns.replaceAll('-', '_').replaceAll('.', '/') + '.cljs';
}

async function collectCljsFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const p = path.join(dir, entry.name);
    if (entry.isFile() && entry.name.endsWith('.cljs')) files.push(p);
    else if (entry.isDirectory()) files.push(...(await collectCljsFiles(p)));
  }
  return files;
}

function extractNs(src) {
  const m = src.match(/\(ns\s+([A-Za-z0-9_.\-]+)/);
  return m ? m[1] : null;
}

function extractRequires(src) {
  // grab every [alon-ui.* :as X] inside a :require form
  const out = new Set();
  const re = /\[\s*(alon-ui\.[A-Za-z0-9_.\-]+)\s+:as\b/g;
  let m;
  while ((m = re.exec(src)) !== null) out.add(m[1]);
  return out;
}

function extractScriptSrcs(html) {
  // capture every <script ... src="..."> regardless of type
  const out = [];
  const re = /<script\b[^>]*\bsrc\s*=\s*"([^"]+)"/g;
  let m;
  while ((m = re.exec(html)) !== null) out.push(m[1]);
  return out;
}

test('every alon-ui.* namespace loaded via cljs has a <script> tag in index.html', async () => {
  const cljsFiles = await collectCljsFiles(path.join(root, 'cljs'));
  const html = await readFile(path.join(root, 'public', 'index.html'), 'utf8');
  const scriptSrcs = new Set(extractScriptSrcs(html));

  const declared = new Map(); // ns → source file path
  const required = new Map(); // ns → [declaring file paths]

  for (const file of cljsFiles) {
    const src = await readFile(file, 'utf8');
    const ns = extractNs(src);
    if (ns) declared.set(ns, file);
    for (const req of extractRequires(src)) {
      if (!required.has(req)) required.set(req, []);
      required.get(req).push(file);
    }
  }

  // Every required alon-ui ns must either be declared in our cljs tree
  // AND have a matching <script src="..."> in index.html.
  const problems = [];
  for (const [ns, dependants] of required) {
    if (!declared.has(ns)) {
      problems.push(`ns ${ns} is required by [${dependants.join(', ')}] but no cljs file declares it`);
      continue;
    }
    const expectedSrc = nsToSrcPath(ns);
    if (!scriptSrcs.has(expectedSrc)) {
      problems.push(
        `ns ${ns} is required by [${dependants.map((p) => path.basename(p)).join(', ')}] ` +
          `but public/index.html has no <script src="${expectedSrc}">. ` +
          `Scittle only loads ns files declared as <script> tags — without one, the browser ` +
          `errors with "Could not find namespace ${ns}".`,
      );
    }
  }

  assert.equal(problems.length, 0, problems.join('\n'));
});
