function style(cssData) {
  console.log("cssData-----", cssData);
  return processCssData(cssData);
}

function processCssData(data) {
  console.log("data-----", data);
  let css = '';
  if (!data || data.length === 0) return css;

  if (isCssProperty(data)) {
    const [property, ...values] = data;

    css = `${property}:${processCssValues(values)};`;
  } else if (typeof data[0] === 'string' && Array.isArray(data[1])) {
    const [selector, next, ...rest] = data;
    console.log("selector-----", selector);
    console.log("next-----", next);
    console.log("rest-----", rest);

    if (selector.startsWith('@media')) {
      css = `${selector}{${processCssData(next)}}`
        + `${processCssData(rest)}`;
    } else if (Array.isArray(next[0])) {
      css = `${selector}{${processCssData(next)}}`
        + `${processCssData(rest)}`;
    } else {
      const [property, value, ...additionalValues] = next;

      css = `${selector}{${processCssData([property, value])}`
        + `${groupInPairs(additionalValues).map(processCssData).join('')}}`
        + processCssData(rest)
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