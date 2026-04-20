// Covers the collectCalls shape: a function that calls another function
// passing it an object literal whose methods are themselves functions.
// Those object methods should be captured as nested children of the
// enclosing function so the UI can render them as their own cards.

export function host() {
  walk({
    CallExpression(p) {
      return p.node.callee;
    },
    Identifier(p) {
      return p.node.name;
    },
    // Arrow assigned to a property — function-valued property should
    // also be captured.
    shorthandArrow: (p) => p.node,
  });
}

function walk(_visitors) { /* stub */ }
