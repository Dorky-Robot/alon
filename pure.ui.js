import { createElement, nestmlToHtml } from 'nestml';

const createWebComponent = (nestmlStructure, componentName) => {
  // Define a class for the new web component
  class CustomElement extends HTMLElement {
    constructor() {
      super();
      // Attach a shadow root to the element
      this.attachShadow({ mode: 'open' });

      // Convert the NestML structure to HTML and append to the shadow root
      const content = nestmlToHtml(nestmlStructure);
      this.shadowRoot.appendChild(content);
    }
  }

  // Define the custom element
  customElements.define(componentName, CustomElement);
};