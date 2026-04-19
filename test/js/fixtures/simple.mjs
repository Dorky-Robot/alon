// Entry fixture: top-level function calling into a nested helper and
// an imported function. Covers the common shapes the UI renders.
import { helper } from './helper.mjs';

const GREETING = 'hi';

function outer(name) {
  function inner(x) {
    return helper(x);
  }
  return inner(name) + GREETING;
}

const arrow = (n) => outer(n);
