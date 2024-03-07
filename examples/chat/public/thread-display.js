class ThreadDisplay extends AlonElement {
  constructor() {
    super();
  }

  addPost(message) {
    this.shadowRoot.appendChild(PostDisplay.text(message));
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
