import AlonElement from "./alon-element";

class AlonComposer extends AlonElement {
  constructor() {
    super()

    this.html(['div', 'bys'])
  }

}

customElements.define('alon-composer', AlonComposer)