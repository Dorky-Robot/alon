
const Alon = {
  ALON_EVENT: '__AlonEvent__',
};


Alon.dispatch = function ({ path, payload }) {
  Alon.listeners = Alon.listeners || {};
  if (Object.values(Alon.listeners).length === 0) return;

  let handlers = [];


  const dispatchTo = (segments, obj) => {
    const segment = segments.shift();

    if (segments.length === 0) {
      handlers.push(...obj['*'])
      if (obj[segment]) handlers.push(...obj[segment])
    } else {
      dispatchTo(segments, obj[segment]);
    }
  }

  dispatchTo(path.split('.'), Alon.listeners);
  handlers.map((handler) => handler(payload));
}

Alon.init = function () {
  document.addEventListener(this.ALON_EVENT, (e) => {
    Alon.dispatch({
      path: e.detail.path,
      payload: e.detail.payload
    })
  });
}

Alon.signal = function ({ element, path, payload }) {
  const mergedDetail = { payload, timestamp: new Date().getTime(), path };

  element.dispatchEvent(new CustomEvent(this.ALON_EVENT, {
    detail: mergedDetail,
    bubbles: true,
  }));
}

Alon.subscribe = function (elem, path, handler) {
  Alon.listeners = Alon.listeners || {};
  addListener(path.split('.'), Alon.listeners);

  function addListener(segments, obj) {
    const segment = segments.shift();
    if (segments.length === 0) {
      obj[segment] = obj[segment] || []
      obj[segment].push(handler);
    } else {
      obj[segment] = obj[segment] || {};
      addListener(segments, obj[segment]);
    }
  };
}


document.addEventListener('DOMContentLoaded', () => {
  Alon.init();

  Alon.subscribe(document, 'person.*', (e) => {
    console.log(e)
  })
  // document.addEventListener('alon/person.name', (e) => {
  //   e.stopPropagation();
  //   console.log(`hi ${e.detail.value}`);
  // }, true);
});



function Text({ name, placeholder }) {
  return ['input', { type: 'text', name, placeholder }];
}

function TextArea({ name, placeholder }) {
  return ['textarea', { name, placeholder }];
}

function RadioGroup(name, choices) {
  const choiceElements = choices.flatMap((choice) => {
    const id = `${name}-${choice.value.replace(/\s+/g, '-')}`;
    return [
      ['input', { type: 'radio', name: name, id: id, value: choice.value }],
      ['label', { for: id }, choice.label]
    ];
  });

  return ['fieldset', ...choiceElements];
}

customElements.define('alon-input',
  class extends HTMLElement {
    constructor() {
      super();

      const form = Habiscript.toElement([
        'form',
        TextArea({ name: 'input', placeholder: 'Alon Message' }),
        ['button', 'Send']
      ]);

      const shadowRoot = this.attachShadow({ mode: 'open' });
      shadowRoot.appendChild(form);
    }

    get input() {
      return this.shadowRoot.querySelector('[name=input]');
    }

    get form() {
      return this.shadowRoot.querySelector('form');
    }

    get value() {
      return this.input.value;
    }

    connectedCallback() {
      const self = this;

      this.form.addEventListener('submit', (e) => {
        e.preventDefault();

        Alon.signal({
          element: self,
          path: 'person.name',
          payload: { name: self.value }
        });

        self.form.reset();
      });
    }
  }
)


const button = Habiscript.toElement([
  'button',
  {
    onclick: (e) => {
      e.preventDefault();
      document.dispatchEvent(new CustomEvent('alon.person', {
        bubbles: true, cancelable: true,
        detail: {
          value: 'testing'
        }
      }));
    }
  },
  'Click me'
])

document.body.appendChild(button);

