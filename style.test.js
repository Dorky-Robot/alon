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

  it('handles CSS variables with subsequent selectors with wrapper', () => {
    const input = [
      ':root', [
        ['--primary-color', '#ff5733'],
        ['--secondary-color', '#3333ff']
      ],
      '.container', [
        ['display', 'grid'],
        ['grid-template-columns', { func: ['repeat', 2, '1fr'] }]
      ]
    ];
    const expected = ':root{--primary-color:#ff5733;--secondary-color:#3333ff;}.container{display:grid;grid-template-columns:repeat(2,1fr);}';
    expect(style(input)).toBe(expected);
  });



  it('handles CSS variables with subsequent selectors with wrapper', () => {
    const input = [
      '.container', [
        'display', 'grid',
        'grid-template-columns', { func: ['repeat', 2, '1fr'] },
        '.something', ['color', 'red']
      ]
    ];
    const expected = ':root{--primary-color:#ff5733;--secondary-color:#3333ff;}.container{display:grid;grid-template-columns:repeat(2,1fr);}';
    expect(style(input)).toBe(expected);
  });

  it('handles CSS variables with subsequent selectors without wrapper', () => {
    const input = [
      ':root', [
        '--primary-color', '#ff5733',
        '--secondary-color', '#3333ff'
      ],
      '.container', [
        'display', 'grid',
        'grid-template-columns', { func: ['repeat', 2, '1fr'] }
      ]
    ];
    const expected = ':root{--primary-color:#ff5733;--secondary-color:#3333ff;}.container{display:grid;grid-template-columns:repeat(2,1fr);}';
    expect(style(input)).toBe(expected);
  });

  it('handles CSS class selector with grid properties', () => {
    const input = [
      '.grid-container', [
        'display', 'grid',
        'grid-template-columns', { func: ['repeat', 2, '1fr'] },
        'background', 'red'
      ]
    ];
    const expected = '.grid-container{display:grid;grid-template-columns:repeat(2,1fr);background:red;}';
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

  it('handles complex CSS structure with media query and CSS properties', () => {
    const input = [
      ':root', [
        ['--max-width', '768px']
      ],
      '.grid-container', [
        'display', 'grid',
        'grid-template-columns', { func: ['repeat', 2, '1fr'] },
        'gap', '10px'
      ],
      '@media screen and (max-width: var(--max-width))', [
        '.grid-container', [
          'grid-template-columns', { func: ['repeat', 1, '1fr'] }
        ]
      ]
    ];
    const expected = ':root{--max-width:768px;}.grid-container{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;}' +
      '@media screen and (max-width: var(--max-width)){.grid-container{grid-template-columns:repeat(1,1fr);}}';
    expect(style(input)).toBe(expected);
  });

  it.only('handles CSS class selector with pseudo selector', () => {
    const input = [
      '.container', [
        ['background', 'red'],
        [
          ':hover', [
            ['background', 'green'],
            ['color', 'white']
          ],
          '.stuff', [
            '.again', ['background', 'red']
          ]
        ],
        ['color', 'blue'],
      ]
    ];
    const expected = '.container{background:red;color:blue;}.container:hover{background:green;color:white;}';
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

  it('returns true for a mixed array of strings and objects with func', () => {
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
