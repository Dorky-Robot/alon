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
 * <post-display>
 *   <p>This is some sample text content that will be automatically slotted.</p>
 * </post-display>
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
 *   <p>This post has a dark theme and also supports text directly.</p>
 * </post-display>
 * 
 * This setup demonstrates how to include the PostDisplay component in your project
 * and how to style it using CSS custom properties. The component automatically handles different content types without needing to specify slots.
 */
class PostDisplay extends AlonElement {
  constructor() {
    super();

    this.style({
      ':host': {
        display: 'block',
        margin: 'var(--post-margin, 1rem 0.2rem)',
        padding: 'var(--post-padding, 0.3rem)',
        border: 'var(--post-border, 1px solid rgba(0, 0, 0, 0.1))',
        borderRadius: 'var(--post-border-radius, 10px)',
        background: 'var(--post-background, white)',
        boxShadow: 'var(--post-shadow, 0 1px 1px rgba(0, 0, 0, 0.1))',
        fontFamily: 'var(--post-font-family, "Helvetica Neue", Helvetica, Arial, sans-serif)',
        boxSizing: 'border-box',
      },
      '::slotted(*)': {
        margin: 'var(--post-content-margin, 0.2rem 0.5rem)',
        padding: 0,
        maxWidth: '100%',
        height: 'auto',
        boxSizing: 'border-box',
      },
      '::slotted(img)': {
        maxWidth: '100%',
        height: 'auto',
        display: 'block',
        borderRadius: '8px',
      },
      '::slotted(video)': {
        maxWidth: '100%',
        display: 'block',
        borderRadius: '8px',
      },
      '::slotted(p)': {
        color: 'var(--post-text-color, #333)',
        lineHeight: 'var(--post-text-line-height, 1.6)',
      },
    });

    this.html(['slot']);
  }

  static text(content) {
    return AlonElement.toElement([
      'post-display',
      ['p', content]
    ]);
  }

  static image({ src, alt }) {
    return AlonElement.toElement([
      'post-display',
      [
        'img',
        { src, alt }
      ]
    ]);
  }
}

AlonElement.register(PostDisplay);
