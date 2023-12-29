function style(cssData) {
  return processCssData(undefined, cssData);
}

function processCssData(parent, data) {
  if (!data || data.length === 0) return '';

  if (isCssProperty(data)) {
    const [property, ...values] = data;

    return `${property}:${processCssValues(values)};`;
  } else if (typeof data[0] === 'string' && Array.isArray(data[1])) {
    const [selector, next, ...rest] = data;

    if (Array.isArray(next[0])) {
      return `${sel(parent, selector)}{${processCssData(selector, next)}}`
        + `${processCssData(parent, rest)}`;
    } else {
      const [property, value, ...additionalValue] = next;

      console.log("selector----", selector);
      return `${sel(parent, selector)}{${processCssData(selector, [property, value])}`
        + `${processCssData(selector, additionalValue)}}`
        + processCssData(parent, rest)
    }
  } else {
    return data.map(d => processCssData(parent, d)).join('');
  }
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