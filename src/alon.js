(function (window) {
  const Stuff = {};
  const ALON_EVENT = '__AlonEvent__';

  function get({ path, candidates }) {
    if (typeof path !== 'string' || !path.trim() || typeof candidates !== 'object' || candidates === null) {
      return [];
    }

    // Optimization for single-level paths
    if (!path.includes('.')) {
      return Reflect.has(candidates, path) ? [candidates[path]].filter(Boolean) : [];
    }

    const keys = path.split('.').filter(Boolean);
    let current = candidates;
    for (const key of keys) {
      if (!Reflect.has(current, key)) return [];
      current = current[key];
      if (typeof current !== 'object' || current === null) break;
    }

    return Array.isArray(current) ? current : [current].filter(Boolean);
  }

  function extractLeafNodes(obj) {
    // Early return for top-level empty object
    if (Object.keys(obj).length === 0 && obj.constructor === Object) {
      return [];
    }

    let leaves = [];

    // Simplifies the object check, directly excluding null values
    const isObject = (value) => typeof value === 'object' && value !== null;

    function traverse(node) {
      // Checks if the node is an array or a primitive (including null and undefined)
      if (Array.isArray(node) || !isObject(node)) {
        leaves.push(node);
      } else {
        // For non-empty objects, iterate over values recursively
        if (Object.keys(node).length === 0) {
          // Directly add empty objects encountered during traversal
          leaves.push(node);
        } else {
          Object.values(node).forEach(traverse);
        }
      }
    }

    traverse(obj);
    return leaves;
  }

  function signal({ element, path, payload }) {
    const mergedDetail = { payload, timestamp: new Date().getTime(), path };

    element.dispatchEvent(new CustomEvent(this.ALON_EVENT, {
      detail: mergedDetail,
      bubbles: true,
    }));
  }

  function subscribe({ element, path, handler }) {
    element.addEventListener(this.ALON_EVENT, (e) => {
      // const candidates = this.get({
      //   candidates: e.currentTarget.alonHandlers,
      //   path: e.detail.path
      // });


      const pathSegments = e.detail.path.split('.');
      let handlers = [];
      function getHandlers(pathSegments, obj) {
        const segment = pathSegments.shift();
        if (obj[segment] && obj[segment]['*']) handlers.push(...obj[segment]['*']);

        if (pathSegments.length > 0) {
          getHandlers(pathSegments, obj[segment]);
        }
      }
      getHandlers(pathSegments, e.currentTarget.alonHandlers);

      handlers.map(f => f(e.detail.payload))
    });

    element.alonHandlers = element.alonHandlers || {}; // Add data property to elem object
    sub(path.split('.'), element.alonHandlers);

    function sub(segments, obj) {
      const segment = segments.shift();
      if (segments.length === 0) {
        obj[segment] = obj[segment] || {};
        obj[segment]['*'] = obj[segment]['*'] || []

        obj[segment]['*'].push(handler);
      } else {
        obj[segment] = obj[segment] || {};
        sub(segments, obj[segment]);
      }
    };
  }

  window.Alon = {
    get,
    extractLeafNodes,
    signal,
    subscribe
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

