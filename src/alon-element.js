import { signalDown, signalUp, capture, bubbling, gapUp, intercept } from './alon.js';
import { toElement, style } from 'habiscript'; // Assuming you have a similar 

class AlonElement extends HTMLElement {
  static components = new Set();

  constructor() {
    super();

    this.shadow = this.attachShadow({ mode: 'open' });
  }

  static register(webComponent) {
    const name = webComponent.name;
    customElements.define(
      webComponent.name,
      webComponent
    );

    this.components.add(webComponent)
  }

  static toKebabCase(className) {
    // This will find capital letters and prepend them with a hyphen, then convert the whole string to lowercase
    return className.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
  }

  isAlon() { return true; }

  intercept(targetElement, eventType, callback) {
    Alon.intercept(this, targetElement, eventType, (e) => {
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
      toElement(habi)
    );
  }

  style(styles) {
    return this.shadow.appendChild(
      style(styles)
    );
  }
}

export default AlonElement;