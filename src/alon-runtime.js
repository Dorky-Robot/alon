import AlonElement from "./alon-element";

class AlonList extends AlonElement {

}

Alon.register(AlonComposer);

class AlonComposition extends AlonElement {

}

Alon.register(AlonComposition);

class AlonComposer extends AlonElement {
  constructor() {
    super()

    this.html([
      ['alon-component-list'],
      ['alon-composition']
    ])
  }
}

customElements.define('alon-runtime', AlonRuntime);