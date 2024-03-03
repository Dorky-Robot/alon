const h = Habiscript.toElement;

class UserInput extends AlonElement {
  static basePadding = '0.5rem';
  static baseBorderRadius = '0.5rem';
  static primaryColor = '#5c6bc0';
  static focusColor = '#4a90e2';

  constructor() {
    super();

    // Inline styles using static properties
    const form = h([
      'form',
      {
        style: {
          display: 'flex',
          alignItems: 'center',
          gap: UserInput.basePadding,
          width: '100%',
        }
      },
      [
        'textarea',
        {
          name: 'input',
          placeholder: 'Enter message',
          style: {
            flex: 1,
            padding: UserInput.basePadding,
            border: `1px solid #ccc`,
            borderRadius: UserInput.baseBorderRadius,
            resize: 'vertical',
            fontSize: '1rem',
            ':focus': {
              outline: 'none',
              borderColor: UserInput.focusColor,
              boxShadow: `0 0 0 0.0625rem ${UserInput.focusColor}`
            }
          }
        }
      ],
      [
        'button',
        {
          type: 'submit',
          style: {
            padding: UserInput.basePadding,
            border: 'none',
            borderRadius: UserInput.baseBorderRadius,
            backgroundColor: UserInput.primaryColor,
            color: 'white',
            cursor: 'pointer',
            fontSize: '1rem',
            transition: 'background-color 0.3s',
            ':hover': {
              backgroundColor: `${this.shadeColor(UserInput.primaryColor, -20)}`
            },
            ':active': {
              backgroundColor: `${this.shadeColor(UserInput.primaryColor, -30)}`
            }
          }
        },
        'Send'
      ]
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
    this.intercept(this.form, 'submit', (e) => {
      this.signalUp({ userInput: { value: this.value } });
      e.currentTarget.reset();
    });
  }

  shadeColor(color, percent) {
    let R = parseInt(color.substring(1, 3), 16);
    let G = parseInt(color.substring(3, 5), 16);
    let B = parseInt(color.substring(5, 7), 16);

    R = parseInt(R * (100 + percent) / 100);
    G = parseInt(G * (100 + percent) / 100);
    B = parseInt(B * (100 + percent) / 100);

    R = (R < 255) ? R : 255;
    G = (G < 255) ? G : 255;
    B = (B < 255) ? B : 255;

    const RR = ((R.toString(16).length == 1) ? "0" + R.toString(16) : R.toString(16));
    const GG = ((G.toString(16).length == 1) ? "0" + G.toString(16) : G.toString(16));
    const BB = ((B.toString(16).length == 1) ? "0" + B.toString(16) : B.toString(16));

    return "#" + RR + GG + BB;
  }
}

customElements.define('user-input', UserInput);

Alon.capture(
  document.querySelector('chat'),
  (payload) => {
    return payload.userInput.value;
  },
  (message) => {
    document.querySelector('messages').appendChild(h(['p', message]))
  }
);
