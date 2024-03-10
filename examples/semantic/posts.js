class Posts extends AlonElement {
  constructor() {
    super();

    this.html(['slot']);
  }

  addPost(postData) {
    const lastPostDisplay = this.querySelector('post-display:last-of-type');
    const newPostElement = PostDisplay[postData.type](postData.value);

    if (lastPostDisplay) {
      lastPostDisplay.insertAdjacentElement('afterend', newPostElement);
    } else {
      this.prepend(newPostElement);
    }
  }
};
