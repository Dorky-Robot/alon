(function (window) {
  const Stuff = {};
  const ALON_EVENT = '__AlonEvent__';

  function signal({ element, path, payload }) {
    const mergedDetail = { payload, timestamp: new Date().getTime(), path };

    element.dispatchEvent(new CustomEvent(this.ALON_EVENT, {
      detail: mergedDetail,
      bubbles: true,
    }));
  }

  /**
   * Internal function to register a handler based on a series of path segments.
   * This function constructs or traverses a nested structure of handlers, registering
   * the provided handler function at the level indicated by the pathSegments array.
   * It's designed for internal use within a larger routing or event handling system
   * where paths need to be dynamically registered and associated with specific handlers.
   * 
   * Note: This function mutates the `handlers` object in place for efficiency.
   * 
   */
  function _registerSubscriber({ pathSegments, handlers, handler }) {
    const [segment, ...rest] = pathSegments;

    if (rest.length > 0) {
      handlers[segment] = handlers[segment] || {};

      _registerSubscriber({
        pathSegments: rest, handlers: handlers[segment], handler
      });
    } else {
      if (segment === '*') {
        handlers[segment] = handlers[segment] || [];
        handlers[segment].push(handler);
      } else {
        handlers[segment] = handlers[segment] || {};
        handlers[segment]['*'] = handlers[segment]['*'] || []
        handlers[segment]['*'].push(handler);
      }
    }
  };

  function _getHandlers({ pathSegments, candidates }) {
    const [segment, ...rest] = pathSegments;

    let handlers = [];

    if (candidates[segment] && candidates[segment]['*']) {
      handlers.push(...candidates[segment]['*']);
    }

    if (rest.length > 0 && candidates[segment]) {
      handlers.push(..._getHandlers({
        pathSegments: rest,
        candidates: candidates[segment]
      }));
    }

    return handlers;
  }

  function subscribe({ element, path, handler }) {
    element.addEventListener(this.ALON_EVENT, (e) => {
      const handlers = _getHandlers({
        pathSegments: e.detail.path.split('.'),
        candidates: e.currentTarget.alonHandlers
      });

      handlers.map(f => f(e.detail.payload))
    });

    element.alonHandlers = element.alonHandlers || {}; // Add data property to elem object
    _registerSubscriber({
      pathSegments: path.split('.'),
      handlers: element.alonHandlers,
      handler
    });

  }

  window.Alon = {
    signal,
    subscribe,
    _registerSubscriber
  };
})(window);


// Alon.dispatch = function ({ path, payload }) {
//   Alon.listeners = Alon.listeners || {};
//   if (Object.values(Alon.listeners).length === 0) return;

//   let handlers = [];
//   const dispatchTo = (segments, obj) => {
//     // If there are no more segments or obj is undefined, stop the recursion
//     if (!segments.length) return;
//     const segment = segments.shift();

//     if (Array.isArray(obj[segment])) {
//       if (segment !== '*') handlers.push(...obj['*']);
//       handlers.push(...obj[segment]);
//     } else {
//       dispatchTo(segments, obj[segment]);
//     }
//   };

//   console.log(path, handlers)
//   dispatchTo(path.split('.'), Alon.listeners);
//   handlers.map((handler) => handler(payload));
// }



// Alon.init = function () {
//   document.addEventListener(this.ALON_EVENT, (e) => {
//     Alon.dispatch({
//       path: e.detail.path,
//       payload: e.detail.payload
//     })
//   });
// }
// document.addEventListener('DOMContentLoaded', () => {
//   //   Alon.init();

//   //   Alon.subscribe(document, 'person.first', (e) => {
//   //     console.log('person.first', e)
//   //   })

//   Alon.subscribe(document, 'person.*', (e) => {
//     console.log('person.*', e)
//   })

//   //   Alon.subscribe(document, 'person.name', (e) => {
//   //     console.log('person.name', e)
//   //   })
// });



// function Text({ name, placeholder }) {
//   return ['input', { type: 'text', name, placeholder }];
// }

// function TextArea({ name, placeholder }) {
//   return ['textarea', { name, placeholder }];
// }

// function RadioGroup(name, choices) {
//   const choiceElements = choices.flatMap((choice) => {
//     const id = `${name}-${choice.value.replace(/\s+/g, '-')}`;
//     return [
//       ['input', { type: 'radio', name: name, id: id, value: choice.value }],
//       ['label', { for: id }, choice.label]
//     ];
//   });

//   return ['fieldset', ...choiceElements];
// }

// customElements.define('alon-input',
//   class extends HTMLElement {
//     constructor() {
//       super();

//       const form = Habiscript.toElement([
//         'form',
//         TextArea({ name: 'input', placeholder: 'Alon Message' }),
//         ['button', 'Send']
//       ]);

//       const shadowRoot = this.attachShadow({ mode: 'open' });
//       shadowRoot.appendChild(form);
//     }

//     get input() {
//       return this.shadowRoot.querySelector('[name=input]');
//     }

//     get form() {
//       return this.shadowRoot.querySelector('form');
//     }

//     get value() {
//       return this.input.value;
//     }

//     connectedCallback() {
//       const self = this;

//       this.form.addEventListener('submit', (e) => {
//         e.preventDefault();

//         Alon.signal({
//           element: self,
//           path: 'person',
//           payload: { name: self.value }
//         });

//         self.form.reset();
//       });
//     }
//   }
// )


// const button = Habiscript.toElement([
//   'button',
//   {
//     onclick: (e) => {
//       e.preventDefault();
//       document.dispatchEvent(new CustomEvent('alon.person', {
//         bubbles: true, cancelable: true,
//         detail: {
//           value: 'testing'
//         }
//       }));
//     }
//   },
//   'Click me'
// ])

// document.body.appendChild(button);

