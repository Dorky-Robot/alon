const {
  style,
  isCssProperty
} = require('./style');


describe('style function', () => {
  it('handles an empty array', () => {
    const input = [];
    const expected = '';
    expect(style(input)).toBe(expected);
  });

  it('handles an empty string', () => {
    const input = '';
    const expected = '';
    expect(style(input)).toBe(expected);
  });

  it('handles undefined input', () => {
    const input = undefined;
    const expected = '';
    expect(style(input)).toBe(expected);
  });

  it('handles single CSS property', () => {
    const input = ['border', '1px', 'solid', 'black'];
    const expected = 'border:1px solid black;';
    expect(style(input)).toBe(expected);
  });

  it('handles single CSS property in nested array', () => {
    const input = [
      ['border', '1px', 'solid', 'black'],
    ]
    const expected = 'border:1px solid black;';
    expect(style(input)).toBe(expected);
  });

  it('handles multiple CSS properties in nested arrays', () => {
    const input = [
      ['border', '1px', 'solid', 'black'],
      ['color', 'white']
    ]
    const expected = 'border:1px solid black;color:white;';
    expect(style(input)).toBe(expected);
  });

  it.only('handles multiple CSS properties without array wrap', () => {
    const input = [
      'border', '1px',
      'color', 'white'
    ]
    const expected = 'border:1px;color:white;';
    expect(style(input)).toBe(expected);
  });

  it('handles CSS class selector with single CSS property', () => {
    const input = [
      '.container', ['background', 'red']
    ]
    const expected = '.container{background:red;}';
    expect(style(input)).toBe(expected);
  });

  it('handles CSS class selector with single CSS property in nested array', () => {
    const input = [
      '.container', [['background', 'red']]
    ]
    const expected = '.container{background:red;}';
    expect(style(input)).toBe(expected);
  });

  it('handles multiple CSS properties with CSS class selector', () => {
    const input = [
      '.container', [
        ['background', 'red'],
        ['color', 'blue']
      ]
    ];
    const expected = '.container{background:red;color:blue;}';
    expect(style(input)).toBe(expected);
  });

  it('handles CSS variables', () => {
    const input = [
      ':root', [
        ['--primary-color', '#ff5733'],
        ['--secondary-color', '#3333ff']
      ]
    ];
    const expected = ':root{--primary-color:#ff5733;--secondary-color:#3333ff;}';
    expect(style(input)).toBe(expected);
  });

  it('handles CSS class selector with grid properties', () => {
    const input = [
      '.grid-container', [
        'display', 'grid',
        'grid-template-columns', { func: ['repeat', 2, '1fr'] }
      ]
    ];
    const expected = '.grid-container{display:grid;grid-template-columns:repeat(2,1fr);}';
    expect(style(input)).toBe(expected);
  });

  it('handles CSS class selector with media query and CSS property', () => {
    const input = [
      '@media screen and (max-width: var(--max-width))', [
        '.grid-container', [
          'grid-template-columns', { func: ['repeat', 1, '1fr'] }
        ]
      ]
    ];
    const expected = '@media screen and (max-width: var(--max-width)){.grid-container{grid-template-columns:repeat(1,1fr);}}';
    expect(style(input)).toBe(expected);
  });
});


describe('isCssProperty', () => {
  it('returns true for an array of strings', () => {
    const input = ['color', 'background', 'border'];
    expect(isCssProperty(input)).toBe(true);
  });

  it('returns true for an array of objects with func property', () => {
    const input = [{ func: ['rotate', '45deg'] }, { func: ['scale', '1.5'] }];
    expect(isCssProperty(input)).toBe(true);
  });

  it.only('returns true for a mixed array of strings and objects with func', () => {
    const input = ['color', { func: ['rotate', '45deg'] }, 'background'];
    expect(isCssProperty(input)).toBe(true);
  });

  it('returns true for an empty array', () => {
    expect(isCssProperty([])).toBe(true);
  });

  it('returns false for an array with non-string and non-function elements', () => {
    const input = ['color', 123, { key: 'value' }];
    expect(isCssProperty(input)).toBe(false);
  });

  it('returns false for non-array input', () => {
    expect(isCssProperty('color')).toBe(false);
    expect(isCssProperty({ func: ['rotate', '45deg'] })).toBe(false);
    expect(isCssProperty(123)).toBe(false);
    expect(isCssProperty(null)).toBe(false);
    expect(isCssProperty(undefined)).toBe(false);
  });
});

// describe('formatCssFunction', () => {
//   test('formats a CSS function with a single argument', () => {
//     const input = { func: ['rgb', '255', '0', '0'] };
//     const expected = 'rgb(255, 0, 0)';
//     expect(formatCssFunction(input)).toBe(expected);
//   });

//   test('formats a CSS function with multiple arguments', () => {
//     const input = { func: ['linear-gradient', 'to right', 'red', 'blue'] };
//     const expected = 'linear-gradient(to right, red, blue)';
//     expect(formatCssFunction(input)).toBe(expected);
//   });

//   test('handles nested function calls', () => {
//     const input = { func: ['calc', '100%', '-', { func: ['subtract', '50px'] }] };
//     const expected = 'calc(100% - subtract(50px))';
//     expect(formatCssFunction(input)).toBe(expected);
//   });

//   test('handles an empty function call', () => {
//     const input = { func: ['translateX'] };
//     const expected = 'translateX()';
//     expect(formatCssFunction(input)).toBe(expected);
//   });

//   test('returns empty string for invalid input', () => {
//     const input = { func: [] }; // Invalid input
//     const expected = '';
//     expect(formatCssFunction(input)).toBe(expected);
//   });

//   test('returns empty string for non-object input', () => {
//     const input = 'not-a-function-call'; // Non-object input
//     const expected = '';
//     expect(formatCssFunction(input)).toBe(expected);
//   });

//   test('ignores non-array func values', () => {
//     const input = { func: 'not-an-array' }; // func is not an array
//     const expected = '';
//     expect(formatCssFunction(input)).toBe(expected);
//   });

//   test('handles function with mixed argument types', () => {
//     const input = { func: ['rgba', 0, 0, 0, 0.75] };
//     const expected = 'rgba(0, 0, 0, 0.75)';
//     expect(formatCssFunction(input)).toBe(expected);
//   });
// });

// describe('formatCssValues', () => {
//   test('handles scalar value preceding a CSS function', () => {
//     const input = ['50%', { func: ['translateX', '100px'] }];
//     const expected = '50% translateX(100px)';
//     expect(formatCssValues(input)).toBe(expected);
//   });

//   test('handles multiple scalar values and CSS functions', () => {
//     const input = ['1em', { func: ['scaleX', 2] }, { func: ['rotate', '45deg'] }];
//     const expected = '1em scaleX(2) rotate(45deg)';
//     expect(formatCssValues(input)).toBe(expected);
//   });

//   test('handles scalar value with nested CSS function', () => {
//     const input = ['300px', { func: ['calc', '100% - 50px'] }];
//     const expected = '300px calc(100% - 50px)';
//     expect(formatCssValues(input)).toBe(expected);
//   });

//   test('handles empty array', () => {
//     const input = [];
//     const expected = '';
//     expect(formatCssValues(input)).toBe(expected);
//   });

//   test('handles single-value array', () => {
//     const input = ['10px'];
//     const expected = '10px';
//     expect(formatCssValues(input)).toBe(expected);
//   });

//   test('handles single-value array', () => {
//     const input = ['10px'];
//     const expected = '10px';
//     expect(formatCssValues(input)).toBe(expected);
//   });

//   test('handles nested function calls', () => {
//     const input = { func: ['calc', '100%', '-', { func: ['subtract', '50px'] }] };
//     const expected = 'calc(100% - subtract(50px))';
//     expect(formatCssValues(input)).toBe(expected);
//   });

//   test('handles arrays with mixed types', () => {
//     const input = ['10px', { func: ['rotate', '45deg'] }, 'solid', 'red'];
//     const expected = '10px rotate(45deg) solid red';
//     expect(formatCssValues(input)).toBe(expected);
//   });

//   test('handles non-string scalar values', () => {
//     const input = [1, 2, 3];
//     const expected = '1 2 3';
//     expect(formatCssValues(input)).toBe(expected);
//   });
// });

// describe('processCssProperty', () => {
//   test('handles standard CSS properties', () => {
//     const prop = 'color';
//     const value = 'blue';
//     const expected = 'color:blue;';
//     expect(processCssProperty(prop, value)).toBe(expected);
//   });

//   test('converts camelCase properties to kebab-case', () => {
//     const prop = 'backgroundColor';
//     const value = 'red';
//     const expected = 'background-color:red;';
//     expect(processCssProperty(prop, value)).toBe(expected);
//   });

//   test('handles array values', () => {
//     const prop = 'margin';
//     const value = ['10px', '15px'];
//     const expected = 'margin:10px 15px;';
//     expect(processCssProperty(prop, value)).toBe(expected);
//   });

//   test('handles function call values with new syntax', () => {
//     const prop = 'background';
//     const value = { func: ['linear-gradient', 'to right', 'red', 'blue'] };
//     const expected = 'background:linear-gradient(to right, red, blue);';
//     expect(processCssProperty(prop, value)).toBe(expected);
//   });

//   test('returns an empty string for undefined property', () => {
//     const prop = undefined;
//     const value = 'blue';
//     const expected = '';
//     expect(processCssProperty(prop, value)).toBe(expected);
//   });
// });

// describe('style function', () => {
//   test('handles basic CSS properties', () => {
//     const cssData = [":root", { "--max-width": "768px" }];
//     const expectedCss = `:root{--max-width:768px;}`;
//     expect(style(cssData)).toBe(expectedCss);
//   });

//   test('handles CSS properties with array values', () => {
//     const cssData = [".container", { "margin": ["10px", "15px"] }];
//     const expectedCss = `.container{margin:10px 15px;}`;
//     expect(style(cssData)).toBe(expectedCss);
//   });

//   test('converts camelCase properties to kebab-case', () => {
//     const cssData = [".box", { "backgroundColor": "red" }];
//     const expectedCss = `.box{background-color:red;}`;
//     expect(style(cssData)).toBe(expectedCss);
//   });

//   test('handles CSS function calls', () => {
//     const cssData = [
//       ".box", [
//         "background", { func: ["linear-gradient", "to right", "red", "blue"] }
//       ]
//     ];
//     const expectedCss = `.box{background:linear-gradient(to right, red, blue);}`;
//     expect(style(cssData)).toBe(expectedCss);
//   });

//   describe('style function with @media queries', () => {
//     test('handles @media queries with a single property', () => {
//       const cssData = [
//         "@media screen and (max-width: 768px)", [
//           [".container", { "padding": "20px" }]
//         ]
//       ];
//       const expectedCss = `@media screen and (max-width: 768px){.container{padding:20px;}}`;
//       expect(style(cssData)).toBe(expectedCss);
//     });

//     test('handles @media queries with multiple properties', () => {
//       const cssData = [
//         "@media screen and (max-width: 768px)", [
//           [".container", {
//             "padding": "20px",
//             "margin": "0 auto"
//           }]
//         ]
//       ];
//       const expectedCss = `@media screen and (max-width: 768px){.container{padding:20px;margin:0 auto;}}`;
//       expect(style(cssData)).toBe(expectedCss);
//     });

//     test('handles @media queries with CSS function calls', () => {
//       const cssData = [
//         "@media screen and (max-width: 768px)", [
//           [".container", {
//             "background": { func: ["linear-gradient", "to bottom", "red", "blue"] }
//           }]
//         ]
//       ];
//       const expectedCss = `@media screen and (max-width: 768px){.container{background:linear-gradient(to bottom, red, blue);}}`;
//       expect(style(cssData)).toBe(expectedCss);
//     });

//     test('handles multiple @media queries', () => {
//       const cssData = [
//         "@media screen and (max-width: 768px)", [
//           [".container", { "padding": "20px" }]
//         ],
//         "@media screen and (min-width: 769px)", [
//           [".container", { "padding": "50px" }]
//         ]
//       ];
//       const expectedCss = `@media screen and (max-width: 768px){.container{padding:20px;}}@media screen and (min-width: 769px){.container{padding:50px;}}`;
//       expect(style(cssData)).toBe(expectedCss);
//     });

//     test('combines @media queries with regular CSS properties', () => {
//       const cssData = [
//         ".container", { "color": "black" },
//         "@media screen and (max-width: 768px)", [
//           [".container", { "padding": "20px" }]
//         ]
//       ];
//       const expectedCss = `.container{color:black;}@media screen and (max-width: 768px){.container{padding:20px;}}`;
//       expect(style(cssData)).toBe(expectedCss);
//     });

//     test('handles nested @media queries within another selector', () => {
//       const cssData = [
//         ".parent", [
//           [".child", { "color": "blue" }],
//           ["@media screen and (max-width: 768px)", [
//             [".child", { "color": "red" }]
//           ]]
//         ]
//       ];
//       const expectedCss = `.parent{.child{color:blue;}@media screen and (max-width: 768px){.child{color:red;}}}`;
//       expect(style(cssData)).toBe(expectedCss);
//     });
//   });

//   test('handles pseudo-classes', () => {
//     const cssData = [
//       [".link", {
//         ":hover": { "color": "blue" }
//       }]
//     ];
//     const expectedCss = `.link{:hover{color:blue;}}`;
//     expect(style(cssData)).toBe(expectedCss);
//   });

//   test('handles complex structures with multiple properties', () => {
//     const cssData = [
//       ".grid-item", {
//         "border": { func: ["1px", "solid", "#ccc"] },
//         "padding": "10px",
//         "textAlign": "center",
//         ":hover": {
//           "backgroundColor": "lightgray"
//         }
//       }
//     ];
//     const expectedCss = `.grid-item{border:1px solid #ccc;padding:10px;text-align:center;:hover{background-color:lightgray;}}`;
//     expect(style(cssData)).toBe(expectedCss);
//   });

//   // Additional tests can be added here to cover edge cases and other variations.
// });
