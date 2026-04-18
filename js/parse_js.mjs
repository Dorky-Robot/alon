#!/usr/bin/env node
// CLI: node parse_js.mjs <entry-file>
// Emits a JSON graph { nodes: [...], edges: [...], root } to stdout.
// Each node carries its function source via Babel start/end offsets so the
// canvas can render the actual code, not just metadata.

import fs from 'node:fs/promises';
import path from 'node:path';
import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';

const traverse = _traverse.default || _traverse;

async function analyze(filePath) {
  const code = await fs.readFile(filePath, 'utf8');
  const ext = path.extname(filePath);
  const ast = parse(code, {
    sourceType: 'module',
    plugins: [
      ext === '.ts' || ext === '.tsx' ? 'typescript' : null,
      'jsx',
    ].filter(Boolean),
    errorRecovery: true,
  });

  const nodes = [];
  const edges = [];
  const declared = new Map();

  const nodeId = (name, line) =>
    `${path.basename(filePath)}:${name}@${line}`;

  function addNode(name, type, astNode) {
    if (declared.has(name)) return declared.get(name);
    const line = astNode.loc.start.line;
    const id = nodeId(name, line);
    const source = code.slice(astNode.start, astNode.end);
    nodes.push({ id, name, type, file: filePath, line, source });
    declared.set(name, id);
    return id;
  }

  traverse(ast, {
    FunctionDeclaration(p) {
      if (p.node.id) addNode(p.node.id.name, 'function', p.node);
    },
    VariableDeclarator(p) {
      const init = p.node.init;
      if (!init) return;
      if (
        (init.type === 'ArrowFunctionExpression' || init.type === 'FunctionExpression') &&
        p.node.id.type === 'Identifier'
      ) {
        addNode(p.node.id.name, 'function', init);
      }
    },
    ClassMethod(p) {
      if (p.node.key.type === 'Identifier') {
        addNode(p.node.key.name, 'method', p.node);
      }
    },
  });

  traverse(ast, {
    CallExpression(p) {
      const callee = p.node.callee;
      let calleeName = null;
      if (callee.type === 'Identifier') calleeName = callee.name;
      else if (callee.type === 'MemberExpression' && callee.property.type === 'Identifier') {
        calleeName = callee.property.name;
      }
      if (!calleeName || !declared.has(calleeName)) return;

      const enclosing = p.getFunctionParent();
      if (!enclosing) return;

      let enclosingName = null;
      const n = enclosing.node;
      if (n.type === 'FunctionDeclaration' && n.id) {
        enclosingName = n.id.name;
      } else if (n.type === 'FunctionExpression' || n.type === 'ArrowFunctionExpression') {
        const parent = enclosing.parent;
        if (parent.type === 'VariableDeclarator' && parent.id.type === 'Identifier') {
          enclosingName = parent.id.name;
        }
      } else if (n.type === 'ClassMethod' && n.key.type === 'Identifier') {
        enclosingName = n.key.name;
      }

      if (!enclosingName || !declared.has(enclosingName)) return;
      const from = declared.get(enclosingName);
      const to = declared.get(calleeName);
      if (from === to) return;
      // offsets of the call expression relative to the enclosing function's
      // source — lets the frontend highlight the call site and anchor the
      // edge at its line.
      const fnStart = enclosing.node.start;
      edges.push({
        from, to, type: 'call',
        offsetStart: p.node.start - fnStart,
        offsetEnd:   p.node.end   - fnStart,
      });
    },
  });

  return { nodes, edges, root: filePath };
}

const entry = process.argv[2];
if (!entry) {
  process.stderr.write('usage: node parse_js.mjs <entry-file>\n');
  process.exit(2);
}

try {
  const graph = await analyze(path.resolve(entry));
  process.stdout.write(JSON.stringify(graph));
} catch (err) {
  process.stderr.write(`parse_js: ${err.message}\n`);
  process.exit(1);
}
