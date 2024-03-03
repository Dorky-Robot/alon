// Import the Alon module if it's an ES6 module
// import Alon from './alon.js'; // Assuming alon.js is an ES6 module
class AlonElement extends HTMLElement {
  constructor() {
    super();
    // Any setup needed for your custom element
  }

  // Use Alon methods directly
  intercept(element, eventType, callback) {
    window.Alon.intercept(host, targetElement, eventType, (e) => {
      // You can modify this callback as necessary
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

  // ...other methods if needed
}

// Define the custom element
customElements.define('alon-element', AlonElement);
