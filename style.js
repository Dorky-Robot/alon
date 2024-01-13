function style(jscss) {
  if (isBlank(jscss)) return jscss;
  return compile(process({ jscss }));
}

function process({ jscss, parent }) {
  let css;

  if (Array.isArray(jscss) && typeof jscss[0] === 'string') {
    const [propName, ...values] = jscss;
    css = `${propName}:${processCssValues(values)};`;
  } else if (Array.isArray(jscss)) {
    css = parent ? {} : '';

    for (let value of jscss) {
      const r = process({ jscss: value, parent: parent });

      if (typeof r === 'string') {
        if (parent) {
          css[parent] = css[parent] || '';
          css[parent] += r;
        } else {
          css += r;
        }
      } else {
        css = { ...css, ...r };
      }
    }
  } else {
    css = {};

    for (let [key, value] of Object.entries(jscss)) {
      if (key.startsWith('@media')) {
        css[key] = process({ jscss: value, parent: parent });
      } else {
        const selector = sel(parent, key);
        const r = process({ jscss: value, parent: selector });

        if (typeof r === 'string') {
          css[selector] = css[selector] || '';
          css[selector] += r
        } else {
          css = { ...css, ...r };
        }
      }

      css
    }
  }

  return css;
}

function sel(parent, selector) {
  if (parent) {
    return `${parent} ${selector}`;
  } else {
    return selector;
  }
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