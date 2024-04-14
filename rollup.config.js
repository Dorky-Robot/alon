import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import { terser } from 'rollup-plugin-terser';

const config = {
  input: 'src/alon-element.js',
  output: [
    {
      file: 'dist/alon-element.umd.js',
      format: 'umd',
      name: 'AlonElement',
      sourcemap: true // Enable source maps for the UMD build
    },
    {
      file: 'dist/alon-element.umd.min.js',
      format: 'umd',
      name: 'AlonElement',
      sourcemap: true, // Enable source maps explicitly for the minified UMD build
      plugins: [terser()] // Terser options
    },
    {
      file: 'dist/alon-element.esm.js',
      format: 'es',
      sourcemap: true // Enable source maps for the ES module build
    },
    {
      file: 'dist/alon-element.esm.min.js',
      format: 'es',
      sourcemap: true, // Enable source maps explicitly for the minified ES module build
      plugins: [terser()] // Terser options
    }
  ],
  plugins: [
    resolve(),
    commonjs(),
    // You can include other plugins here
  ],
  // If you want to apply Terser to all outputs, include it in the global plugins array
  // This will apply it to outputs without specific Terser plugin configuration
  // plugins: [terser({ output: { comments: false }, sourceMap: true })]
};

export default config;
