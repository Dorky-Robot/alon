function kebabCaseProp(prop) {
  if (typeof prop !== 'string') {
    return undefined;
  }
  return prop.replace(/([A-Z])/g, g => `-${g[0].toLowerCase()}`);
}

function formatCssFunction(obj) {
  if (obj && Array.isArray(obj.func) && obj.func.length > 0) {
    const [funcName, ...args] = obj.func;

    const processedArgs = args.map(arg => {
      if (arg && typeof arg === 'object' && arg.func) {
        // Special handling for 'calc' function
        if (funcName === 'calc' && typeof arg === 'string') {
          return ` ${arg} `;
        }
        return formatCssFunction(arg);
      }
      return Array.isArray(arg) ? arg.join(' ') : arg;
    });

    const argsJoined = funcName === 'calc' ? processedArgs.join(' ') : processedArgs.join(', ');

    return `${funcName}(${argsJoined})`;
  }

  return '';
}

function formatCssValues(values) {
  // Handle the case where values is an object with a 'func' key
  if (typeof values === 'object' && values.func) {
    return formatCssFunction(values);
  }

  if (typeof values === 'string') {
    return values;
  }

  if (Array.isArray(values)) {
    return values.map(value => {
      if (typeof value === 'object' && value.func) {
        return formatCssFunction(value);
      } else if (Array.isArray(value)) {
        // Recursively process each value in the array
        return formatCssValues(value);
      }
      return value;
    }).join(' '); // Join with a space for simple arrays
  }
  return '';
}

function processCssProperty(prop, value) {
  if (typeof prop !== 'string') return '';
  return `${kebabCaseProp(prop)}:${formatCssValues(value)};`;
}

function style(cssData) {
  if (!Array.isArray(cssData)) {
    throw new Error('Invalid cssData: Expected an array');
  }

  let css = '';

  for (const [selector, styles] of cssData) {
    if (typeof selector === 'string') {
      css += `${selector}{`;

      for (const prop in styles) {
        if (styles.hasOwnProperty(prop)) {
          const value = styles[prop];
          if (typeof value === 'object' && value.func) {
            css += `${kebabCaseProp(prop)}:${formatCssFunction(value)};`;
          } else {
            css += `${kebabCaseProp(prop)}:${formatCssValues(value)};`;
          }
        }
      }

      css += '}';
    }
  }

  return css;
}

module.exports = {
  style,
  kebabCaseProp,
  processCssProperty,
  formatCssValues,
  formatCssFunction,
};

