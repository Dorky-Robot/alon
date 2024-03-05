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
}

// Define the custom element
customElements.define('alon-element', AlonElement);