# alon

*Tagalog for "wave."*

A tool for exploring the **flow and architecture** of a codebase, so you
can correctly shape it through periods of massive change — especially
the kind of change AI now produces.

## Why

AI can generate, refactor, and rewrite code faster than any human can
read it file by file. The bottleneck stops being "can we write this?"
and becomes "do we still understand the shape of what we have?" When
the answer drifts toward *no*, codebases rot from the inside: layers
blur, responsibilities scatter, the architecture quietly stops matching
the intent.

Most code-reading tools were built for a slower world. They navigate by
file tree: open a directory, scroll a file, jump to a definition, lose
the thread. That's fine when changes arrive a few at a time. It's not
fine when an agent has just rewritten thirty files across four layers.

alon is a tool for the new tempo. It lets you *see* the flow and the
architecture directly — not as folders, but as the graph the program
actually forms — so you can decide what to keep, what to reshape, and
what to push back on.

## The visual model

Think Unreal Blueprint or a node-based flow chart, applied to real
codebases:

- **Nodes** are functions, methods, handlers, or module boundaries —
  each rendered as a floating box with typed input pins on the left and
  output pins on the right.
- **Edges** are the actual relationships: execution flow, data
  flow, reads/writes, events emitted and consumed. Edges are
  first-class — you can see at a glance what feeds what, what fans out,
  and where the joins are.
- **Semantic zoom.** The same edges, viewed at lower zoom, collapse
  into an architectural map: clusters of nodes become modules, modules
  become layers, repeated edge bundles become the seams of the system.
  Flow chart and architecture diagram are the same picture at different
  altitudes, not two separate artifacts that drift out of sync.

You pick a function. It appears as a box. Pull on a thread; the wave
ripples outward through the graph. Files and folders fade into the
background.

## Two sources of edges

Static analysis is the floor, not the ceiling.

- **Traced edges** come from parsing — definitions, references, imports,
  call sites the compiler can see. These are exact and cheap.
- **Inferred edges** come from an LLM reading the surrounding code and
  reasoning about intent — dynamic dispatch, string-constructed calls,
  message buses, event emitters, dependency-injected handlers,
  framework magic, codegen, runtime plugin lookups. These are the edges
  that quietly carry production behavior but never show up in a call
  graph. alon surfaces them, marked as inferred so you know which is
  which.

Together they give you a map that matches how the program actually
behaves, not just how it lexically reads.

## Shaping

The views aren't the product — what you do with them is. With flow and
architecture both visible from the same data, you can:

- See, before merging, where an AI-generated change has crossed a
  boundary you cared about.
- Find layers that have quietly blurred, and tighten them.
- Decide what to keep, what to reshape, and what to reject — your
  changes or an agent's — back toward an architecture you actually
  want, instead of one that has merely accumulated.

## How it's built

alon has three halves. No JVM anywhere:

1. **Backend — babashka (Clojure).** An `org.httpkit` server that
   coordinates the analyzer and serves the canvas. No JDK required;
   `bb` is a native binary.
2. **Analyzer — Node + Babel (subprocess).** A small `js/parse_js.mjs`
   CLI parses JS/TS with `@babel/parser`, walks the AST, and emits a
   graph of traced edges as JSON. Inferred edges (LLM-interpreted) will
   layer on top.
3. **Canvas — scittle + Reagent (no build).** ClojureScript served as
   plain `.cljs` files, interpreted in the browser by SCI. Reagent
   gives us the reactive state model; pan/zoom/drag/expand all derive
   from a single atom. No shadow-cljs, no JDK, no build pipeline.

## Running

alon expects a dev environment with `bb`, `node`, and `npm`. The
intended home is a [kubo](https://github.com/dorky-robot/kubo)
container — everything lives there, host stays clean.

```
# one-time, inside the kubo:
bin/setup              # installs bb, runs npm install

# then:
bin/alon <entry-file>
```

Open the URL it prints (default `http://localhost:4242`). The analyzer
walks from the entry file; the canvas starts with that function as the
root box. Click a box to expand its neighbors; click the source
preview to unfold the full function body; drag to rearrange; scroll to
zoom.

## Status

Early. JS/TS analyzer and canvas work. Next: inferred edges via LLM,
semantic zoom, typed pins, and diwa integration for git-history
context on each node.
