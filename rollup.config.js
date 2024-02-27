import { terser } from 'rollup-plugin-terser';

const config = [
  {
    input: 'src/alon.js',
    output: [
      {
        file: 'dist/alon.bundle.js',
        format: 'umd',
        name: 'Alon'
      },
      {
        file: 'dist/alon.bundle.min.js',
        format: 'umd',
        name: 'Alon',
        plugins: [terser()]
      }
    ]
  }
];

export default config;
