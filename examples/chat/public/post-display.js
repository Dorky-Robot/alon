/**
 * PostDisplay Web Component Usage Example:
 * 
 * Include this script in your HTML file:
 * <script src="post-display.js" defer></script>
 * 
 * Then, use the <post-display> element in your HTML to dynamically slot content based on the element type. No need to specify slots manually:
 * 
 * <style>
 *   post-display {
 *     --post-margin: 2rem;
 *     --post-padding: 1.5rem;
 *     --post-border: 2px solid #007bff;
 *     --post-border-radius: 10px;
 *     --post-background: #e7f1ff;
 *     --post-color: #0056b3;
 *     --post-font-size: 1.2rem;
 *   }
 * 
 *   .dark-theme {
 *     --post-background: #333;
 *     --post-color: #fff;
 *     --post-border: 2px solid #666;
 *   }
 * </style>
 * 
 * Usage examples:
 * 
 * <!-- For text content -->
 * <post-display>This is some sample text content that will be automatically slotted.</post-display>
 * 
 * <!-- For an image -->
 * <post-display>
 *   <img src="path/to/image.jpg" alt="Sample Image">
 * </post-display>
 * 
 * <!-- For a video -->
 * <post-display>
 *   <video controls>
 *     <source src="path/to/video.mp4" type="video/mp4">
 *   </video>
 * </post-display>
 * 
 * <!-- Example with a different theme -->
 * <post-display class="dark-theme">
 *   This post has a dark theme and also supports text directly.
 * </post-display>
 * 
 * This setup demonstrates how to include the PostDisplay component in your project
 * and how to style it using CSS custom properties. The component automatically handles different content types without needing to specify slots.
 */

class PostDisplay extends HTMLElement {
  constructor() {
    super();
    // Use HabiScript to define styles and slot
    const shadowRoot = this.attachShadow({ mode: 'open' });
    const styles = HabiScript.style({
      ':host': {
        display: 'block',
        margin: 'var(--post-margin, 1rem)',
        padding: 'var(--post-padding, 1rem)',
        border: 'var(--post-border, 1px solid #ccc)',
        borderRadius: 'var(--post-border-radius, 8px)',
        background: 'var(--post-background, #f9f9f9)',
      },
      '::slotted(*)': {
        maxWidth: '100%',
        height: 'auto',
      }
    });

    // Define the slot within the shadow DOM using HabiScript
    const slot = HabiScript.toElement(['slot', { name: 'content' }]);

    // Append styles and slot to the shadow DOM
    shadowRoot.appendChild(styles);
    shadowRoot.appendChild(slot);
  }

  connectedCallback() {
    // Dynamically assign slots based on child element types
    Array.from(this.children).forEach(child => {
      // Use the child tag name to dynamically set the slot name
      child.setAttribute('slot', child.tagName.toLowerCase()); // Assign slot as 'img', 'video', 'audio', etc.
    });
  }
}

customElements.define('post-display', PostDisplay);

