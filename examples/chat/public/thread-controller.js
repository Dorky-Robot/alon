class ThreadController extends AlonElement {
  constructor() {
    super();

    const shadowRoot = this.attachShadow({ mode: 'open' });
    shadowRoot.appendChild(
      Habiscript.toElement(['slot'])
    )
  }

  connectedCallback() {
    this.bubbling(
      (p) => {
        return p.userInput.value
      },
      (message) => {
        // this.querySelector('thread-display').addPost(message)

        this.signalDown({
          thread: {
            post: {
              message
            }
          }
        });
      }

    )
  }
}

customElements.define('thread-controller', ThreadController);