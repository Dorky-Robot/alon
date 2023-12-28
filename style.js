function style(cssData) {
  return processCssData("", cssData);
}

function processCssData(parentSelector, data) {
  let css = "";

  if (data && data.length > 0) {
    const [key, ...value] = data;

    if (Array.isArray(value[0])) {
      const attrs = value.reduce((cssPartial, attr) => {
        return cssPartial += processCssData(undefined, attr);
      }, '');

      css = `${key}{${attrs}}`;
    } else {
      css = `${key}:${value};`;
    }
  }

  return css;
}

// function processCssData(parentSelector, data) {
//   let css = "";

//   for (let i = 0; i < data.length; i += 2) {
//     const key = data[i];
//     const value = data[i + 1];

//     if (Array.isArray(value)) {
//       // Handle nested structures (like media queries or pseudo-classes)
//       let newSelector = (typeof key === 'string' && key.startsWith('@')) ? key + ' ' + parentSelector : parentSelector + key;
//       css += processCssData(newSelector, value);
//     } else if (typeof value === 'object' && value.func) {
//       // Handle CSS functions
//       css += `${ parentSelector } { ${ key }: ${ processFunction(value.func) }; } \n`;
//     } else if (typeof key === 'string') {
//       // Regular CSS properties
//       css += `${ parentSelector } { ${ camelCaseToDash(key) }: ${ value }; } \n`;
//     }
//   }

//   return css;
// }

// function processFunction(funcArray) {
//   const [funcName, ...args] = funcArray;
//   return `${ funcName } (${ args.map(arg => Array.isArray(arg) ? processFunction(arg) : arg).join(', ') })`;
// }

// function camelCaseToDash(str) {
//   return str.replace(/([A-Z])/g, g => `- ${ g[0].toLowerCase() } `);
// }

module.exports = {
  style
}