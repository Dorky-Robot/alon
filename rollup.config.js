
import resolve from '@rollup/plugin-node-resolve';
import { terser } from 'rollup-plugin-terser';
import commonjs from '@rollup/plugin-commonjs';

const commonConfig = {
  plugins: [
    resolve(),
    commonjs(), // Add this plugin to handle CommonJS modules
    // other plugins...
  ]
};

// Export the configuration as an array to handle multiple entry points
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
        plugins: [terser()] // Ensure this is called if you want to minify this output
      }
    ],
    ...commonConfig,
  },
  {
    input: 'src/alon-composer.js',
    output: [
      {
        file: 'dist/alon-composer.bundle.js',
        format: 'umd',
        name: 'AlonComposer'
      },
      {
        file: 'dist/alon-composer.bundle.min.js',
        format: 'umd',
        name: 'AlonComposer',
        plugins: [terser()] // Ensure this is called if you want to minify this output
      }
    ],
    ...commonConfig,
  }
];
