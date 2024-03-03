const h = Habiscript.toElement;

customElements.define('user-input',
  class extends AlonElement {
    constructor() {
      super();

      const form = Habiscript.toElement([
        'form',
        ['textarea', { name: 'input', placeholder: 'Enter message' }],
        ['button', 'Send']
      ]);

      const shadowRoot = this.attachShadow({ mode: 'open' });
      shadowRoot.appendChild(form);
    }

    get input() {
      return this.shadowRoot.querySelector('[name=input]');
    }

    get form() {
      return this.shadowRoot.querySelector('form');
    }

    get value() {
      return this.input.value;
    }

    connectedCallback() {
      Alon.intercept(this.form, 'submit', (e, { signalUp }) => {
        e.currentTarget.reset();
        signalUp({ userInput: { value: this.value } });
      });
    }
  }
)

Alon.capture(
  document.querySelector('chat'),
  (payload) => {
    return payload.userInput.value;
  },
  (message) => document.querySelector('messages').appendChild(h(['p', message]))
);
