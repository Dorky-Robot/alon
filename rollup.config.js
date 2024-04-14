import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import { terser } from 'rollup-plugin-terser';

// Define Terser options once and reuse them
const terserOptions = terser({
  mangle: {
    reserved: ['AlonElement'] // Preserve class name to prevent mangling
  },
  output: {
    comments: false // Remove comments in the minified output
  }
});

const config = {
  input: 'src/alon-element.js',
  output: [
    // UMD non-minified
    {
      file: 'dist/alon-element.umd.js',
      format: 'umd',
      name: 'AlonElement',
      sourcemap: true
    },
    // UMD minified
    {
      file: 'dist/alon-element.umd.min.js',
      format: 'umd',
      name: 'AlonElement',
      sourcemap: true,
      plugins: [terserOptions]
    },
    // ESM non-minified
    {
      file: 'dist/alon-element.esm.js',
      format: 'es',
      sourcemap: true
    },
    // ESM minified
    {
      file: 'dist/alon-element.esm.min.js',
      format: 'es',
      sourcemap: true,
      plugins: [terserOptions]
    }
  ],
  plugins: [
    resolve(),
    commonjs()
  ]
};

export default config;
