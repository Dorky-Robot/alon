function style(jscss) {
  return compile(processCssData(jscss));
}

function processCssData(jscss, parent) {
  if (isBlank(jscss)) return jscss;

  if (typeof jscss === 'object' && !Array.isArray(jscss)) {
    return Object.entries(jscss).reduce((css, [key, value]) => {
      css[key] = css[key] || '';
      css[key] += processCssData(value, key);
      return css;
    }, {});
  } else if (Array.isArray(jscss[0]) && jscss.length > 1) {
    const r = jscss.reduce((c, jscss) => {
      c += processCssData(jscss, parent)
      return c;
    }, '');

    if (parent) {
      const css = {};
      css[parent] = r
      return css;
    } else {
      return r
    }
  } else {
    const [property, ...values] = jscss;
    return `${property}:${processCssValues(values)};`;
  }
}

function set(css, key, value) {
  css[key] = css[key] || '';
  css[key] += value;
}


function compile(jscss) {
  let css = jscss;

  if (
    typeof jscss === 'object'
    && !Array.isArray(jscss)
    && !isBlank(jscss)
  ) {
    css = '';

    Object.keys(jscss).forEach(key => {
      css += `${key}{${compile(jscss[key])}}`;
    });
  }

  return css;
}

const jscss = {
  ":root": {
    "--primary-color": "#ff5733",
    "--secondary-color": "#3333ff"
  },
  ".container": {
    "display": "grid",
    "grid-template-columns": "repeat(2, 1fr)"
  }
};

function isBlank(o) {
  return !o || o.length === 0 || Object.keys(o).length === 0;
}

function processCssValues(values) {
  if (typeof values === 'string') {
    return values;
  } else if (values.func) {
    const func = values.func[0];
    const args = values.func.slice(1);

    return `${func}(${args.join(',')})`;
  } else {
    return values.map(processCssValues).join(' ');
  }
}

module.exports = {
  style
}