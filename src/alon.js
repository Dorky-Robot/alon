customElements.define('alon-input',
  class extends HTMLElement {
    constructor() {
      super();

      const test = ["div", ["p", "Hello World"]];
      const shadowRoot = this.attachShadow({ mode: 'open' });
      shadowRoot.appendChild(Habiscript.toElement(test));
    }

    connectedCallback() {
      console.log("Custom element added to page.");
    }

    disconnectedCallback() {
      console.log("Custom element removed from page.");
    }

    adoptedCallback() {
      console.log("Custom element moved to new page.");
    }

    attributeChangedCallback(name, oldValue, newValue) {
      console.log(`Attribute ${name} has changed.`);
    }
  }
);
