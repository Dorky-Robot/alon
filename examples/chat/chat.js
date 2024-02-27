const h = Habiscript.toElement;

customElements.define('user-input',
  class extends HTMLElement {
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
      this.form.addEventListener('submit', (e) => {
        const t = e.currentTarget;
        Alon.signal(t, { userInput: { value: t.value } });

        e.preventDefault();
        t.reset();
      });
    }
  }
)

const messages = document.querySelector('messages');

Alon.subscribe(
  messages,
  (payload) => {
    console.log(payload.userInput.value)
    return payload.userInput.value;
  },
  (message) => messages.appendChild(h(['p', message]))
);
