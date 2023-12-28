// Assuming the style function is defined elsewhere and exported
const {
  style,
  formatCssFunction,
  formatCssValues,
  kebabCaseProp,
  processCssProperty
} = require('./style');

describe('style function', () => {
  describe.only('kebabCaseProp', () => {
    test('converts camelCase to kebab-case for a single word', () => {
      const input = 'backgroundColor';
      const expected = 'background-color';
      expect(kebabCaseProp(input)).toBe(expected);
    });

    test('returns an empty string if the input is empty', () => {
      const input = '';
      const expected = '';
      expect(kebabCaseProp(input)).toBe(expected);
    });

    test('returns undefined for non-string inputs', () => {
      const input = null; // or undefined, or 123, etc.
      const expected = undefined;
      expect(kebabCaseProp(input)).toBe(expected);
    });
  });
});

describe.only('formatCssFunction', () => {
  test('formats a CSS function with a single argument', () => {
    const input = { func: ['rgb', '255', '0', '0'] };
    const expected = 'rgb(255, 0, 0)';
    expect(formatCssFunction(input)).toBe(expected);
  });

  test('formats a CSS function with multiple arguments', () => {
    const input = { func: ['linear-gradient', 'to right', 'red', 'blue'] };
    const expected = 'linear-gradient(to right, red, blue)';
    expect(formatCssFunction(input)).toBe(expected);
  });

  test('handles nested function calls', () => {
    const input = { func: ['calc', '100%', '-', { func: ['subtract', '50px'] }] };
    const expected = 'calc(100% - subtract(50px))';
    expect(formatCssFunction(input)).toBe(expected);
  });

  test('handles an empty function call', () => {
    const input = { func: ['translateX'] };
    const expected = 'translateX()';
    expect(formatCssFunction(input)).toBe(expected);
  });

  test('returns empty string for invalid input', () => {
    const input = { func: [] }; // Invalid input
    const expected = '';
    expect(formatCssFunction(input)).toBe(expected);
  });

  test('returns empty string for non-object input', () => {
    const input = 'not-a-function-call'; // Non-object input
    const expected = '';
    expect(formatCssFunction(input)).toBe(expected);
  });

  test('ignores non-array func values', () => {
    const input = { func: 'not-an-array' }; // func is not an array
    const expected = '';
    expect(formatCssFunction(input)).toBe(expected);
  });

  test('handles function with mixed argument types', () => {
    const input = { func: ['rgba', 0, 0, 0, 0.75] };
    const expected = 'rgba(0, 0, 0, 0.75)';
    expect(formatCssFunction(input)).toBe(expected);
  });
});

describe.only('formatCssValues', () => {
  test('handles scalar value preceding a CSS function', () => {
    const input = ['50%', { func: ['translateX', '100px'] }];
    const expected = '50% translateX(100px)';
    expect(formatCssValues(input)).toBe(expected);
  });

  test('handles multiple scalar values and CSS functions', () => {
    const input = ['1em', { func: ['scaleX', 2] }, { func: ['rotate', '45deg'] }];
    const expected = '1em scaleX(2) rotate(45deg)';
    expect(formatCssValues(input)).toBe(expected);
  });

  test('handles scalar value with nested CSS function', () => {
    const input = ['300px', { func: ['calc', '100% - 50px'] }];
    const expected = '300px calc(100% - 50px)';
    expect(formatCssValues(input)).toBe(expected);
  });

  test('handles empty array', () => {
    const input = [];
    const expected = '';
    expect(formatCssValues(input)).toBe(expected);
  });

  test('handles single-value array', () => {
    const input = ['10px'];
    const expected = '10px';
    expect(formatCssValues(input)).toBe(expected);
  });

  test('handles single-value array', () => {
    const input = ['10px'];
    const expected = '10px';
    expect(formatCssValues(input)).toBe(expected);
  });

  test('handles nested function calls', () => {
    const input = { func: ['calc', '100%', '-', { func: ['subtract', '50px'] }] };
    const expected = 'calc(100% - subtract(50px))';
    expect(formatCssValues(input)).toBe(expected);
  });

  test('handles arrays with mixed types', () => {
    const input = ['10px', { func: ['rotate', '45deg'] }, 'solid', 'red'];
    const expected = '10px rotate(45deg) solid red';
    expect(formatCssValues(input)).toBe(expected);
  });

  test('handles non-string scalar values', () => {
    const input = [1, 2, 3];
    const expected = '1 2 3';
    expect(formatCssValues(input)).toBe(expected);
  });
});

describe('processCssProperty', () => {
  test('handles standard CSS properties', () => {
    const prop = 'color';
    const value = 'blue';
    const expected = 'color:blue;';
    expect(processCssProperty(prop, value)).toBe(expected);
  });

  test('converts camelCase properties to kebab-case', () => {
    const prop = 'backgroundColor';
    const value = 'red';
    const expected = 'background-color:red;';
    expect(processCssProperty(prop, value)).toBe(expected);
  });

  test('handles array values', () => {
    const prop = 'margin';
    const value = ['10px', '15px'];
    const expected = 'margin:10px 15px;';
    expect(processCssProperty(prop, value)).toBe(expected);
  });

  test('handles function call values with new syntax', () => {
    const prop = 'background';
    const value = { func: ['linear-gradient', 'to right', 'red', 'blue'] };
    const expected = 'background:linear-gradient(to right, red, blue);';
    expect(processCssProperty(prop, value)).toBe(expected);
  });

  test('returns an empty string for undefined property', () => {
    const prop = undefined;
    const value = 'blue';
    const expected = '';
    expect(processCssProperty(prop, value)).toBe(expected);
  });
});

describe('style function', () => {
  test('handles basic CSS properties', () => {
    const cssData = [":root", { "--max-width": "768px" }];
    const expectedCss = `:root{--max-width:768px;}`;
    expect(style(cssData)).toBe(expectedCss);
  });

  test('handles CSS properties with array values', () => {
    const cssData = [".container", { "margin": ["10px", "15px"] }];
    const expectedCss = `.container{margin:10px 15px;}`;
    expect(style(cssData)).toBe(expectedCss);
  });

  test('converts camelCase properties to kebab-case', () => {
    const cssData = [".box", { "backgroundColor": "red" }];
    const expectedCss = `.box{background-color:red;}`;
    expect(style(cssData)).toBe(expectedCss);
  });

  test('handles CSS function calls', () => {
    const cssData = [
      ".box", [
        "background", { func: ["linear-gradient", "to right", "red", "blue"] }
      ]
    ];
    const expectedCss = `.box{background:linear-gradient(to right, red, blue);}`;
    expect(style(cssData)).toBe(expectedCss);
  });

  describe('style function with @media queries', () => {
    test('handles @media queries with a single property', () => {
      const cssData = [
        "@media screen and (max-width: 768px)", [
          [".container", { "padding": "20px" }]
        ]
      ];
      const expectedCss = `@media screen and (max-width: 768px){.container{padding:20px;}}`;
      expect(style(cssData)).toBe(expectedCss);
    });

    test('handles @media queries with multiple properties', () => {
      const cssData = [
        "@media screen and (max-width: 768px)", [
          [".container", {
            "padding": "20px",
            "margin": "0 auto"
          }]
        ]
      ];
      const expectedCss = `@media screen and (max-width: 768px){.container{padding:20px;margin:0 auto;}}`;
      expect(style(cssData)).toBe(expectedCss);
    });

    test('handles @media queries with CSS function calls', () => {
      const cssData = [
        "@media screen and (max-width: 768px)", [
          [".container", {
            "background": { func: ["linear-gradient", "to bottom", "red", "blue"] }
          }]
        ]
      ];
      const expectedCss = `@media screen and (max-width: 768px){.container{background:linear-gradient(to bottom, red, blue);}}`;
      expect(style(cssData)).toBe(expectedCss);
    });

    test('handles multiple @media queries', () => {
      const cssData = [
        "@media screen and (max-width: 768px)", [
          [".container", { "padding": "20px" }]
        ],
        "@media screen and (min-width: 769px)", [
          [".container", { "padding": "50px" }]
        ]
      ];
      const expectedCss = `@media screen and (max-width: 768px){.container{padding:20px;}}@media screen and (min-width: 769px){.container{padding:50px;}}`;
      expect(style(cssData)).toBe(expectedCss);
    });

    test('combines @media queries with regular CSS properties', () => {
      const cssData = [
        ".container", { "color": "black" },
        "@media screen and (max-width: 768px)", [
          [".container", { "padding": "20px" }]
        ]
      ];
      const expectedCss = `.container{color:black;}@media screen and (max-width: 768px){.container{padding:20px;}}`;
      expect(style(cssData)).toBe(expectedCss);
    });

    test('handles nested @media queries within another selector', () => {
      const cssData = [
        ".parent", [
          [".child", { "color": "blue" }],
          ["@media screen and (max-width: 768px)", [
            [".child", { "color": "red" }]
          ]]
        ]
      ];
      const expectedCss = `.parent{.child{color:blue;}@media screen and (max-width: 768px){.child{color:red;}}}`;
      expect(style(cssData)).toBe(expectedCss);
    });
  });

  test('handles pseudo-classes', () => {
    const cssData = [
      [".link", {
        ":hover": { "color": "blue" }
      }]
    ];
    const expectedCss = `.link{:hover{color:blue;}}`;
    expect(style(cssData)).toBe(expectedCss);
  });

  test('handles complex structures with multiple properties', () => {
    const cssData = [
      ".grid-item", {
        "border": { func: ["1px", "solid", "#ccc"] },
        "padding": "10px",
        "textAlign": "center",
        ":hover": {
          "backgroundColor": "lightgray"
        }
      }
    ];
    const expectedCss = `.grid-item{border:1px solid #ccc;padding:10px;text-align:center;:hover{background-color:lightgray;}}`;
    expect(style(cssData)).toBe(expectedCss);
  });

  // Additional tests can be added here to cover edge cases and other variations.
});
