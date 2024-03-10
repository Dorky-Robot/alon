import Posts from 'posts';

class ThreadPost extends Posts {
  constructor() {
    super();
    this.html(['user-input'])
  }

  connectedCallback() {
    this.bubbling(
      (p) => {
        return p.userInput
      },
      (input) => this.addPost(input)
    )
  }
}

customElements.define('thread-post',)

export default ThreadPost;