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
        file: 'dist/alon-element.bundle.js',
        format: 'umd',
        name: 'AlonElement'
      },
      {
        file: 'dist/alon-element.bundle.min.js',
        format: 'umd',
        name: 'AlonElement',
        plugins: [terser()] // Minify this output
      }
    ],
    ...commonConfig,
  },
  // Additional configuration for alon.spec.js
  {
    input: 'src/alon.spec.js',
    output: [
      {
        file: 'dist/alon-spec.bundle.js',
        format: 'umd',
        name: 'AlonSpec'
      },
      {
        file: 'dist/alon-spec.bundle.min.js',
        format: 'umd',
        name: 'AlonSpec',
        plugins: [terser()] // Minify this output
      }
    ],
    ...commonConfig,
  }
];
