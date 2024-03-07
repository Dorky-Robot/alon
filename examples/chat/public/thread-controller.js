class ThreadController extends AlonElement {
  constructor() {
    super();

    this.html(['slot']);
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
