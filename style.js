function style(cssData) {
  return compile(processCssData(cssData));
}

function processCssData(data, parent) {
  if (!data || data.length === 0 || Object.keys(data).length === 0) return data;

  let css;
  if (isCssProperty(data)) {
    const [property, ...values] = data;

    css = `${property}:${processCssValues(values)};`;
  } else if (typeof data[0] === 'string' && Array.isArray(data[1])) {
    const [selector, next, ...rest] = data;
    const nextSelector = sel(parent, selector);

    css = {}

    if (selector.startsWith('@media')) {
      const nested = {}
      nested[next[0]] = processCssData(next[1], parent)

      css[selector] = nested
    }
    else if (Array.isArray(next[0])) {
      css[nextSelector] = processCssData(next, nextSelector);
    } else {
      const [property, value, ...additionalValues] = next;
      const nextSelector = sel(parent, selector);

      css[nextSelector] = processCssData([property, value], nextSelector)

      for (let i = 0; i < additionalValues.length; i++) {
        const value = additionalValues[i];
        let additional;

        if (typeof value === 'string') {
          additional = processCssData(
            [additionalValues[i], additionalValues[i + 1]],
            nextSelector
          );

          i++;
        } else {
          additional = processCssData(value, nextSelector);
        }

        if (typeof additional === 'string') {
          css[nextSelector] += additional;
        } else {
          css = merge(css, additional);
        }
      }
    }

    css = merge(css, processCssData(rest, parent));
  } else {
    for (let i = 0; i < data.length; i++) {
      css += processCssData(data[i], parent);
    }
  }

  return css;
}

function merge(...objects) {
  return Object.assign({}, ...objects);
}

function compile(jscss) {
  if (!jscss || jscss.length === 0 || Object.keys(jscss).length === 0) return jscss;

  if (typeof jscss === 'string') return jscss;
  let css = '';

  for (let key in jscss) {
    if (typeof jscss[key] === 'object') {
      css += `${key}{${compile(jscss[key])}}`;
    } else {
      css += `${key}{${jscss[key]}}`;
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

function isCssProperty(arr) {
  return Array.isArray(arr) && arr.every((item) => {
    return typeof item === 'string' || item.func;
  });
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
  style,
  isCssProperty
}