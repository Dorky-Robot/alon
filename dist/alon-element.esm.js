const ALON_EVENT = '__AlonEvent__';

function signalDown(element, payload) {
  const event = new CustomEvent(ALON_EVENT, {
    detail: { ...payload, __alonSignalDown__: true },
    bubbles: true,
    cancelable: true,
    composed: true
  });

  findLeafElements(element).forEach((leafElement) => {
    leafElement.dispatchEvent(event);
  });
}

function signalUp(element, payload) {
  element.dispatchEvent(new CustomEvent(ALON_EVENT, {
    detail: { ...payload, __alonSignalUp__: true },
    bubbles: true,
    cancelable: true,
    composed: true
  }));
}

function _genericEventHandler(e, handlerMap) {
  e.stopPropagation();

  for (const [resolver, handlers] of handlerMap.entries()) {
    const result = resolver(e.detail);
    if (result !== undefined) {
      handlers.forEach((handler) => handler(result, e));
    }
  }
}

function capture(element, resolver, handler) {
  if (!element.alonCaptureHandlers) {
    element.alonCaptureHandlers = new Map();

    element.addEventListener(ALON_EVENT, (e) => {
      if (e.detail.__alonSignalUp__) return;
      _genericEventHandler(e, element.alonCaptureHandlers);
    }, true);
  }

  const handlers = element.alonCaptureHandlers.get(resolver) || [];
  handlers.push(handler);
  element.alonCaptureHandlers.set(resolver, handlers);
}

function bubbling(element, resolver, handler) {
  if (!element.alonBubblingHandlers) {
    element.alonBubblingHandlers = new Map();

    element.addEventListener(ALON_EVENT, (e) => {
      if (e.detail.__alonSignalDown__) return;
      _genericEventHandler(e, element.alonBubblingHandlers);
    });
  }

  const handlers = element.alonBubblingHandlers.get(resolver) || [];
  handlers.push(handler);
  element.alonBubblingHandlers.set(resolver, handlers);
}

function intercept(host, targetElement, eventType, callback) {
  targetElement.addEventListener(eventType, (event) => {
    if (event.preventDefault) event.preventDefault();
    callback(event);
  });
}

function findLeafElements(element) {
  let leafElements = [];

  function walkThrough(element) {
    let children = element.childNodes;
    if (children.length === 0) {
      if (element instanceof HTMLElement) {
        leafElements.push(element);
      }
    } else {
      children.forEach((child) => {
        walkThrough(child);
      });
    }
  }

  walkThrough(element);
  return leafElements;
}

var commonjsGlobal = typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : typeof global !== 'undefined' ? global : typeof self !== 'undefined' ? self : {};

var tanaw_bundle = { exports: {} };

tanaw_bundle.exports;

(function (module, exports) {
  (function (global, factory) {
    module.exports = factory();
  })(commonjsGlobal, (function () {
    function getDefaultExportFromCjs(x) {
      return x && x.__esModule && Object.prototype.hasOwnProperty.call(x, 'default') ? x['default'] : x;
    }

    function style(stylesObject) {
      if (isEmpty(stylesObject)) return stylesObject;
      return compileStyles(processStyles({ stylesObject }));
    }

    const NO_SELECTOR = '__*__';

    function processStyles({ stylesObject, parentSelector = '', cssObject = {}, nestedSelector = '' }) {
      if (Array.isArray(stylesObject)) {
        const [property, value] = stylesObject;
        const cssProperty = `${camelToKebabCase(property)}:${value};`;
        const targetObject = nestedSelector ? (cssObject[nestedSelector] = cssObject[nestedSelector] || {}) : cssObject;
        const key = camelToKebabCase(parentSelector || NO_SELECTOR);

        targetObject[key] = (targetObject[key] || '') + cssProperty;
      } else {
        Object.keys(stylesObject).forEach(key => {
          const value = stylesObject[key];
          if (key.startsWith('@')) {
            // Inline handling for media queries and nested selectors
            processStyles({
              stylesObject: value,
              cssObject: cssObject,
              nestedSelector: key
            });
          } else if (typeof value !== 'object') {
            // Inline handling for direct property-value pairs
            const cssProperty = `${camelToKebabCase(key)}:${value};`;
            const targetObject = nestedSelector ? (cssObject[nestedSelector] = cssObject[nestedSelector] || {}) : cssObject;
            const combinedKey = camelToKebabCase(parentSelector || NO_SELECTOR);
            targetObject[combinedKey] = (targetObject[combinedKey] || '') + cssProperty;
          } else {
            // Inline handling for nested selectors
            const combinedSelector = combineSelectors(parentSelector, key);
            processStyles({
              stylesObject: value,
              parentSelector: combinedSelector,
              cssObject: cssObject,
              nestedSelector
            });
          }
        });
      }

      return cssObject;
    }

    function combineSelectors(parent, child) {
      if (!parent) return child;
      const parentSelectors = splitAndTrim(parent);
      const childSelectors = splitAndTrim(child);

      const combined = parentSelectors.map(p =>
        childSelectors.map(c => `${p}${c.startsWith(':') ? '' : ' '}${c}`).join(',')
      ).join(',');

      return combined;
    }

    function camelToKebabCase(str) {
      return str.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
    }

    function splitAndTrim(str) {
      return str.split(',').map(s => s.trim());
    }

    function compileStyles(cssObject) {
      if (typeof cssObject !== 'object' || Array.isArray(cssObject) || isEmpty(cssObject)) {
        return cssObject;
      }

      return Object.entries(cssObject).map(([key, value]) =>
        key === NO_SELECTOR ? compileStyles(value) : `${key}{${compileStyles(value)}}`
      ).join('');
    }

    function isEmpty(obj) {
      return !obj || Object.keys(obj).length === 0;
    }

    var tanaw = {
      style
    };

    var tanaw$1 = /*@__PURE__*/getDefaultExportFromCjs(tanaw);

    return tanaw$1;

  }));
}(tanaw_bundle, tanaw_bundle.exports));

var tanaw_bundleExports = tanaw_bundle.exports;

const { style: tanawStyle } = tanaw_bundleExports;

const classRegex = /\.[^.#]+/g;
const idRegex = /#[^.#]+/;

/**
 * PRIVATE FUNCTION: Creates an HTML element with specified attributes and children.
 *
 * This function is intended for internal use within the module/library it is defined in.
 * It simplifies the process of creating DOM elements in JavaScript, allowing for 
 * a more declarative approach to building complex HTML structures without the verbosity
 * of traditional DOM manipulation methods.
 *
 * @param {string} tag - The type of element to create (e.g., 'div', 'span').
 * @param {Object} attrs - An object representing attributes to set on the element. 
 *                         Special handling for:
 *                         - style: Accepts an object with CSS properties and values.
 *                         - event listeners: Properties starting with 'on' (e.g., 'onClick')
 *                           are treated as event listeners. The property name should be 
 *                           the event's name in camelCase.
 * @param {Array} children - An array of elements or strings to append as children. 
 *                           Can handle:
 *                           - Strings: will be converted to text nodes.
 *                           - Node objects: will be appended directly.
 *                           - Arrays: assumed to be in a specific format and converted accordingly.
 *
 * @returns {Node} The newly created element.
 *
 * NOTE: This function is not exposed publicly and should only be used within the context
 * of its defining module/library.
 *
 * Sample Usage (internal):
 * ```javascript
 * const card = makeElement('div', { class: 'card', style: { width: '300px', boxShadow: '0 2px 4px rgba(0,0,0,.1)' } }, [
 *   makeElement('img', { src: 'image.jpg', alt: 'Sample Image', style: { width: '100%', display: 'block' } }),
 *   makeElement('div', { class: 'card-body' }, [
 *     makeElement('h5', { class: 'card-title' }, ['Card Title']),
 *     makeElement('p', { class: 'card-text' }, ['Some quick example text to build on the card title and make up the bulk of the card\'s content.']),
 *     makeElement('a', { href: '#', class: 'btn btn-primary' }, ['Go somewhere'])
 *   ])
 * ]);
 *
 * document.body.appendChild(card);
 * ```
 *
 * This example is for illustrative purposes only to demonstrate how `makeElement`
 * might be used internally.
 */
function makeElement(tag, attrs = {}, children) {
  const element = document.createElement(tag);

  for (const [attr, value] of Object.entries(attrs)) {
    if (attr === "style" && typeof value === "object") {
      Object.assign(element.style, value);
    } else if (typeof value === "function" && attr.startsWith("on")) {
      const event = attr.substring(2).toLowerCase();
      element.addEventListener(event, value);
    } else if (value !== undefined) {
      element.setAttribute(attr, value);
    }
  }

  // Append children
  for (const child of children) {
    if (typeof child === "string") {
      element.appendChild(document.createTextNode(child));
    } else if (child instanceof Node) {
      element.appendChild(child);
    } else if (Array.isArray(child)) {
      element.appendChild(habiToHtml(child));
    }
  }

  return element;
}

/**
 * Converts a CSS string to an object containing key-value pairs of CSS styles.
 * @param {string} cssString - The CSS string to be converted.
 * @returns {Object} - An object containing key-value pairs of CSS styles.
 */
function cssStringToObject(cssString) {
  let styleObject = {};

  cssString.split(';').forEach(style => {
    let [key, value] = style.split(':');
    if (key && value) {
      key = key.trim();
      // Convert key to camelCase
      key = key.replace(/-./g, match => match.charAt(1).toUpperCase());

      styleObject[key] = value.trim();
    }
  });

  return styleObject;
}

function habiToHtml(habi) {
  if (typeof habi === "string") {
    return document.createTextNode(habi);
  } else if (habi instanceof Node) {
    return habi;
  }

  const [first, ...rest] = habi;
  let tag, attrs = {};

  if (typeof first === "string") {
    tag = first.split(/[#.]/)[0];
    const classMatch = first.match(classRegex);
    const idMatch = first.match(idRegex);

    if (classMatch) {
      attrs.class = classMatch.map((c) => c.substring(1)).join(" ");
    }

    if (idMatch) {
      attrs.id = idMatch[0].substring(1);
    }
  } else {
    throw new Error("The first element of the habiscript array must be a string.");
  }

  if (rest.length > 0 && typeof rest[0] === "object" && !Array.isArray(rest[0])) {
    Object.assign(attrs, rest.shift());
  }

  const children = rest.flatMap((child) =>
    Array.isArray(child) ? habiToHtml(child) : child
  );

  return makeElement(tag, attrs, children);
}

/**
 * Alias for `habiToHtml`.
 * @alias habiToHtml
 */
function toElement(habi) {
  return habiToHtml(habi);
}

/**
 * Converts an HTML element or HTML string to a habiscript format.
 *
 * This function is designed to facilitate the conversion of HTML structures into a more
 * concise and easy-to-manage format, habiscript, which represents the HTML as a nested array structure.
 * It's particularly useful for situations where you need to serialize HTML elements for 
 * storage, transmission, or processing in a format that's easier to handle than a string of HTML.
 *
 * @param {Node|string} element - The HTML element (Node) or HTML string to be converted.
 *                                If a string is provided, it's first parsed into an HTML element.
 *
 * @returns {Array} The Habiscript representation of the provided HTML. This format is an array where:
 *                  - The first element is a string representing the tag, optionally including
 *                    its id and classes (e.g., 'div#id.class1.class2').
 *                  - The second element, if present, is an object representing the element's
 *                    attributes (excluding class and id, which are handled in the tag string).
 *                  - Subsequent elements represent child nodes, each converted to habiscript format.
 *
 * Sample Usage:
 * ```javascript
 * // HTML element example
 * const div = document.makeElement('div');
 * div.id = 'example';
 * div.className = 'container';
 * div.innerHTML = '<span>Hello world</span>';
 * const habiscript = htmlToHabi(div);
 * console.log(habiscript); // Outputs: ['div#example.container', {}, ['span', {}, 'Hello world']]
 *
 * // HTML string example
 * const habiFromString = htmlToHabi('<div id="example" class="container"><span>Hello world</span></div>');
 * console.log(habiFromString); // Same output as above
 * ```
 *
 * In these examples, `htmlToHabi` is used to convert an HTML element and an HTML string
 * into the Habiscript format. This format provides a more structured and readable way to represent
 * HTML content, especially when dealing with complex or deeply nested structures.
 */
function htmlToHabi(element) {
  if (typeof element === "string") {
    const parser = new DOMParser();
    const doc = parser.parseFromString(element, "text/html");
    return htmlToHabi(doc.body.firstChild);
  }

  if (element.nodeType === Node.TEXT_NODE) {
    return element.textContent;
  }

  let tag = element.tagName.toLowerCase();
  if (element.id) {
    tag += `#${element.id}`;
  }
  if (element.className) {
    tag += `.${element.className.split(" ").join(".")}`;
  }

  const habi = [tag];
  const attributes = {};
  for (const attr of element.attributes) {
    if (attr.name !== "class" && attr.name !== "id") {
      if (attr.name === "style") {
        attributes.style = cssStringToObject(attr.value);
      } else {
        attributes[attr.name] = attr.value;
      }
    }
  }

  if (Object.keys(attributes).length > 0) {
    habi.push(attributes);
  }

  for (const child of element.childNodes) {
    habi.push(htmlToHabi(child));
  }

  return habi;
}

function style(tanawJS) {
  return this.toElement([
    'style',
    tanawStyle(tanawJS)
  ]);
}

var src = {
  habiToHtml,
  htmlToHabi,
  toElement,
  style
};

class AlonElement extends HTMLElement {
  static _components = new Map();

  static get components() {
    return Object.fromEntries(this._components);
  }

  static selectorToHabi(selector) {
    return toHabi($(selector)[0]);
  }

  static toElement(habi) {
    return src.toElement(habi);
  }

  static toHabi(element) {
    return src.htmlToHabi(element);
  }

  constructor() {
    super();

    this.shadow = this.attachShadow({ mode: 'open' });
  }

  static register(webComponent) {
    const name = this.toKebabCase(webComponent.name);

    customElements.define(
      name,
      webComponent
    );

    this._components.set(name, webComponent);
  }

  static toKebabCase(className) {
    // This will find capital letters and prepend them with a hyphen, then convert the whole string to lowercase
    return className.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
  }

  isAlon() { return true; }

  intercept(targetElement, eventType, callback) {
    intercept(this, targetElement, eventType, (e) => {
      callback(e);
    });
  }

  // You can also directly reference the other methods
  signalUp(payload) {
    signalUp(this, payload);
  }

  signalDown(payload) {
    signalDown(this, payload);
  }

  bubbling(resolver, handler) {
    bubbling(this, resolver, handler);
  }

  capture(resolver, handler) {
    capture(this, resolver, handler);
  }

  html(habi) {
    return this.shadow.appendChild(
      src.toElement(habi)
    );
  }

  style(styles) {
    return this.shadow.appendChild(
      src.style(styles)
    );
  }
}

export { AlonElement as default };
