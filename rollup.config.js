import resolve from '@rollup/plugin-node-resolve';
import { terser } from 'rollup-plugin-terser';
import commonjs from '@rollup/plugin-commonjs';

const commonConfig = {
  plugins: [
    resolve(),
    commonjs(), // Added to handle CommonJS modules
    // other plugins...
  ]
};

export default [
  {
    input: 'src/alon-element.js',
    output: [
      {
        file: 'dist/alon-element.esm.js',
        format: 'es', // ES module format
        name: 'AlonElement'
      },
      // UMD bundle is kept only if needed
      {
        file: 'dist/alon-element.umd.js',
        format: 'umd',
        name: 'AlonElement'
      }
    ],
    ...commonConfig,
  },
  // Additional configuration for alon.spec.js
  {
    input: 'src/alon.spec.js',
    output: [
      {
        file: 'dist/alon-spec.umd.js',
        format: 'umd',
        name: 'AlonSpec'
      },
    ],
    ...commonConfig,
  }
];
