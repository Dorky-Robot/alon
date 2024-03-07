// Import the Alon module if it's an ES6 module
// import Alon from './alon.js'; // Assuming alon.js is an ES6 module
class AlonElement extends HTMLElement {
  constructor() {
    super();
  }

  intercept(targetElement, eventType, callback) {
    window.Alon.intercept(this, targetElement, eventType, (e) => {
      callback(e);
    });
  }

  // You can also directly reference the other methods
  signalUp(payload) {
    window.Alon.signalUp(this, payload);
  }

  signalDown(payload) {
    window.Alon.signalDown(this, payload);
  }

  bubbling(resolver, handler) {
    window.Alon.bubbling(this, resolver, handler);
  }

  capture(resolver, handler) {
    window.Alon.capture(this, resolver, handler);
  }

  static h(habi) {
    return Habiscript.toElement(habi);
  }

  h(habi) {
    return this.constructor.h(habi);
  }

  style(styles) {
    return this.constructor.style(styles);
  }

  static style(styles) {
    return Habiscript.style(styles);
  }
}

// Define the custom element
customElements.define('alon-element', AlonElement);
