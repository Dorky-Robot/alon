function style(cssData) {
  return processCssData(undefined, cssData);
}

function processCssData(parent, data) {
  console.log("parent-----", parent);

  if (!data || data.length === 0) return '';

  if (isCssProperty(data)) {
    console.log("data b1-----", data);
    const [property, ...values] = data;

    return `${property}:${processCssValues(values)};`;
  } else if (typeof data[0] === 'string' && Array.isArray(data[1])) {
    console.log("data b2-----", data);
    const [selector, values, ...rest] = data;

    return `${selector}{${processCssData(selector, values)}}` + processCssData(parent, rest);
  } else {
    console.log("data b3-----", data);
    return data.map(d => processCssData(parent, d)).join('');
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