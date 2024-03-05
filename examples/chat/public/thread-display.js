class ThreadDisplay extends AlonElement {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  addPost(message) {
    const post = Habiscript.toElement(['p', message]);
    this.shadowRoot.appendChild(post);
  }

  connectedCallback() {
    this.capture(
      (p) => p.thread.post.message,
      (post) => {
        this.addPost(post);
      }
    );
  }
}

customElements.define('thread-display', ThreadDisplay);