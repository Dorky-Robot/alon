import { signalDown, signalUp, capture, bubbling, intercept } from './alon.js';
import { toElement, htmlToHabi, style } from 'habiscript'; // Assuming you have a similar 

class AlonElement extends HTMLElement {
  static _components = new Map();

  static get components() {
    return Object.fromEntries(this._components);
  }

  static selectorToHabi(selector) {
    return toHabi($(selector)[0]);
  }

  static toElement(habi) {
    return toElement(habi);
  }

  static toHabi(element) {
    return htmlToHabi(element);
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