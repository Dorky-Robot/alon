function style(jscss) {
  return compile(
    processCssData(jscss)
  )
}

function isCssProperty(arr) {
  return Array.isArray(arr) && arr.every((item) => {
    return typeof item === 'string' || item.func;
  });
}

function processCssData(data, parent) {
  let css = {};

  if (!data || data.length === 0) return css;

  if (isCssProperty(data)) {
    const [property, ...values] = data;

    css[property] = processCssValues(values);
  } else if (typeof data[0] === 'string' && Array.isArray(data[1])) {
    const [selector, next, ...rest] = data;

    if (selector.startsWith('@media')) {
      css[selector] = css.merge(processCssData(next))
        + `${processCssData(rest)}`;
    } else if (Array.isArray(next[0])) {
      const prop = {}
      prop[sel(parent, selector)] = processCssData(next);

      css = merge(prop, processCssData(rest));
    } else {
      const [property, value, ...additionalValues] = next;

      css[sel(parent, selector)] = merge(
        processCssData([property, value]),
        ...groupInPairs(additionalValues).map(processCssData)
      );

      css = merge(css, processCssData(rest));
    }
  } else {
    css = merge(...data.map(processCssData));
  }

  return css;
}

function merge(...objects) {
  return Object.assign({}, ...objects);
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

function compile(jscss) {
  let css = '';

  for (let key in jscss) {
    if (typeof jscss[key] === 'string') {
      css += `${key}:${processCssValues(jscss[key])};`;
    } else {
      css += `${key}{${compile(jscss[key])}}`;
    }
  }

  return css;
}

function groupInPairs(arr) {
  const result = [];

  for (let i = 0; i < arr.length; i += 2) {
    result.push(arr.slice(i, i + 2));
  }

  return result;
}

function sel(parent, selector) {
  if (parent) {
    return `${parent} ${selector} `;
  } else {
    return selector;
  }
}

module.exports = {
  style,
  isCssProperty
}