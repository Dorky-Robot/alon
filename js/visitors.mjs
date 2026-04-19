// Per-file Babel pass: declare every named function / top-level decl /
// import binding (parseFile), then a second pass once all files are known
// to emit call edges that may cross file boundaries (collectCalls).

import path from 'node:path';
import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';

const traverse = _traverse.default || _traverse;

export function parseFile(filePath, code, rootDir) {
  const ext = path.extname(filePath);
  // Qualify ids with the path relative to the entry file's directory so
  // that two files sharing a basename (e.g. a/index.js and b/index.js)
  // can't collide into a single node.
  const relPath = rootDir ? path.relative(rootDir, filePath) : filePath;
  const ast = parse(code, {
    sourceType: 'module',
    plugins: [
      ext === '.ts' || ext === '.tsx' ? 'typescript' : null,
      'jsx',
    ].filter(Boolean),
    errorRecovery: true,
  });

  const nodes = [];
  const declared = new Map();
  const importBindings = new Map();
  const fnNodeToId = new WeakMap();

  const nodeId = (name, line) => `${relPath}:${name}@${line}`;

  function nearestCapturedAncestor(p) {
    let cur = p.getFunctionParent();
    while (cur) {
      const target = cur.node.type === 'VariableDeclarator' ? cur.node.init : cur.node;
      if (fnNodeToId.has(target)) return fnNodeToId.get(target);
      if (fnNodeToId.has(cur.node)) return fnNodeToId.get(cur.node);
      cur = cur.getFunctionParent();
    }
    return null;
  }

  function buildSignature(astNode) {
    // Slice each param's own source range so defaults, rest, and destructuring
    // patterns round-trip exactly as written instead of being re-stringified.
    if (!Array.isArray(astNode.params)) return null;
    return astNode.params.map(pr => code.slice(pr.start, pr.end)).join(', ');
  }

  function addNode(name, type, astNode, p, opts = {}) {
    const { registerInDeclared = true } = opts;
    // `declared` is the call-resolver's bare-name lookup; only true top-level
    // bindings should populate it so a local object-property named `parse`
    // doesn't shadow an imported `parse` in the same file.
    if (registerInDeclared && declared.has(name)) return declared.get(name);
    const line = astNode.loc.start.line;
    const id = nodeId(name, line);
    const source = code.slice(astNode.start, astNode.end);
    const parentId = p ? nearestCapturedAncestor(p) : null;
    const signature = (type === 'function' || type === 'method')
      ? `${name}(${buildSignature(astNode) || ''})`
      : null;
    nodes.push({
      id, name, type, file: filePath, line, source, signature,
      start: astNode.start, end: astNode.end, parentId,
    });
    if (registerInDeclared) declared.set(name, id);
    fnNodeToId.set(astNode, id);
    return id;
  }

  // Expression-body arrows (one-liners) are too small to warrant their own
  // card — they'd just duplicate the source line above. Block-body functions
  // (FunctionExpression or arrow with `{ ... }`) still get captured.
  const hasBlockBody = (astNode) =>
    astNode.body && astNode.body.type === 'BlockStatement';

  traverse(ast, {
    FunctionDeclaration(p) {
      if (p.node.id) addNode(p.node.id.name, 'function', p.node, p);
    },
    VariableDeclarator(p) {
      const init = p.node.init;
      if (!init || p.node.id.type !== 'Identifier') return;
      if (init.type === 'ArrowFunctionExpression' || init.type === 'FunctionExpression') {
        if (!hasBlockBody(init)) return;
        addNode(p.node.id.name, 'function', init, p);
      } else if (!p.getFunctionParent()) {
        // Top-level const/let/var only — locals inside function bodies
        // aren't structural enough to deserve their own row.
        const decl = p.parentPath.node;
        addNode(p.node.id.name, decl.kind || 'const', decl, p);
      }
    },
    ClassDeclaration(p) {
      if (p.node.id) addNode(p.node.id.name, 'class', p.node, p);
    },
    ClassMethod(p) {
      if (p.node.key.type === 'Identifier') {
        addNode(p.node.key.name, 'method', p.node, p);
      }
    },
    ObjectMethod(p) {
      // Methods on object literals passed as arguments — e.g. visitor tables
      // like `traverse(ast, { CallExpression(p) {...} })` — render as their
      // own nested cards, but must NOT be registered in `declared`: the same
      // key name (e.g. `parse`) can appear in many object literals and would
      // otherwise shadow an imported `parse` for call resolution.
      if (p.node.key.type === 'Identifier') {
        addNode(p.node.key.name, 'function', p.node, p, { registerInDeclared: false });
      }
    },
    ObjectProperty(p) {
      const val = p.node.value;
      if (p.node.key.type !== 'Identifier' || !val) return;
      if (val.type === 'ArrowFunctionExpression' || val.type === 'FunctionExpression') {
        if (!hasBlockBody(val)) return;
        addNode(p.node.key.name, 'function', val, p, { registerInDeclared: false });
      }
    },
    ImportDeclaration(p) {
      const spec = p.node.source.value;
      const specs = p.node.specifiers || [];
      if (specs.length === 0) return;
      // Track each binding so call resolution can follow it across files;
      // the visible row groups them under the first binding's name.
      for (const s of specs) {
        const local = s.local && s.local.name;
        if (!local) continue;
        const imported =
          s.type === 'ImportDefaultSpecifier' ? 'default' :
          s.type === 'ImportNamespaceSpecifier' ? '*' :
          (s.imported && s.imported.name) || local;
        importBindings.set(local, { spec, imported });
      }
      const first = specs[0].local && specs[0].local.name;
      const name = specs.length > 1 ? `${first}, …` : first;
      // Render the import as a row but DON'T register it in `declared` —
      // the call resolver should prefer the cross-file follow via
      // importBindings. Otherwise a call to an imported name would resolve
      // to this stub node instead of the real function in the other file.
      const line = p.node.loc.start.line;
      const id = nodeId(name, line);
      const source = code.slice(p.node.start, p.node.end);
      nodes.push({
        id, name, type: 'import', file: filePath, line, source,
        start: p.node.start, end: p.node.end,
        parentId: nearestCapturedAncestor(p),
      });
      fnNodeToId.set(p.node, id);
    },
  });

  return { filePath, code, ast, nodes, declared, importBindings, fnNodeToId };
}

export function collectCalls(file, allFiles) {
  const edges = [];
  const { ast, declared, importBindings, fnNodeToId } = file;

  function resolveCallee(name) {
    if (declared.has(name)) return declared.get(name);
    const binding = importBindings.get(name);
    if (!binding) return null;
    const target = allFiles.get(binding.resolvedFile);
    if (!target) return null;
    if (binding.imported === 'default') {
      // No exports tracking yet. A "first function in file" guess would
      // silently misattribute the edge when the default export isn't the
      // first declared function, which is worse than no edge — a wrong
      // arrow looks identical to a verified one. Drop the edge instead.
      return null;
    }
    return target.declared.get(binding.imported) || null;
  }

  traverse(ast, {
    CallExpression(p) {
      const callee = p.node.callee;
      let calleeName = null;
      if (callee.type === 'Identifier') calleeName = callee.name;
      else if (callee.type === 'MemberExpression' && callee.property.type === 'Identifier') {
        calleeName = callee.property.name;
      }
      if (!calleeName) return;
      const toId = resolveCallee(calleeName);
      if (!toId) return;

      const enclosing = p.getFunctionParent();
      if (!enclosing) return;
      // Look up the enclosing node directly in fnNodeToId (set at capture
      // time) instead of re-deriving a bare name. This unifies every captured
      // shape (FunctionDeclaration, VariableDeclarator-bound fn, ClassMethod,
      // ObjectMethod, function-valued ObjectProperty) and correctly drops
      // edges that originate inside uncaptured anonymous fns.
      const fromId = fnNodeToId.get(enclosing.node);
      if (!fromId || fromId === toId) return;

      // Offsets are relative to the enclosing function so the renderer can
      // anchor the edge at the actual call-site line in the source block.
      const fnStart = enclosing.node.start;
      edges.push({
        from: fromId, to: toId, type: 'call',
        offsetStart: p.node.start - fnStart,
        offsetEnd:   p.node.end   - fnStart,
      });
    },
  });
  return edges;
}
