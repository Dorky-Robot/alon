function style(jscss) {
  if (isBlank(jscss)) return jscss;
  return compile(process({ jscss }));
}

const NO_SELECTOR = '__*__'
function process({ jscss, parent, css = {}, nested }) {
  if (Array.isArray(jscss) && typeof jscss[0] === 'string') {
    const [propName, ...values] = jscss;
    const prop = `${propName}:${processCssValues(values)};`;
    const target = nested ? (css[nested] = css[nested] || {}) : css;
    const key = parent || NO_SELECTOR;

    target[key] = (target[key] || '') + prop;
  } else if (Array.isArray(jscss)) {
    for (let item of jscss) {
      process({ jscss: item, parent, css, nested });
    }
  } else {
    for (let [key, value] of Object.entries(jscss)) {
      process({
        jscss: value,
        parent: key.startsWith('@') ? parent : sel(parent, key),
        css,
        nested: key.startsWith('@') ? key : nested
      });
    }
  }

  return css;
}

function sel(parent, selector) {
  if (parent) {
    if (selector.startsWith(':')) {
      return `${parent}${selector}`;
    } else {
      return `${parent} ${selector}`;
    }
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
      if (key === NO_SELECTOR) {
        css += compile(jscss[key]);
      } else {
        css += `${key}{${compile(jscss[key])}}`;
      }

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