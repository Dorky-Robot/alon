function style(cssData) {
  console.log("cssData-----", cssData);
  return processCssData(cssData);
}

function processCssData(data, parent) {
  let css = '';
  if (!data || data.length === 0) return css;

  if (isCssProperty(data)) {
    const [property, ...values] = data;

    css = `${property}:${processCssValues(values)};`;
  } else if (typeof data[0] === 'string' && Array.isArray(data[1])) {
    const [selector, next, ...rest] = data;
    const nextSelector = sel(parent, selector);

    if (selector.startsWith('@media')) {
      css = `${nextSelector}{${processCssData(next, nextSelector)}`
        + `${processCssData(rest, nextSelector)}`;
    } else if (Array.isArray(next[0])) {
      css = `${nextSelector}{${processCssData(next, nextSelector)}}`
        + `${processCssData(rest, nextSelector)}`;
    } else {
      const [property, value, ...additionalValues] = next;
      const nextSelector = sel(parent, selector);

      css = `${nextSelector}{${processCssData([property, value], nextSelector)}`
        + `${groupInPairs(additionalValues).map(a => processCssData(a, nextSelector)).join('')}}`
        + processCssData(rest, nextSelector)
    }
  } else {
    css = data.map(processCssData).join('');
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

function extractNested(cssString) {
  const regex = /[{;]+[^{]*[^{]+\s*({[^}]*)/g;
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