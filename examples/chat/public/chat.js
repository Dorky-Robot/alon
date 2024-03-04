const primaryColor = '#ccc';

UserInput.primaryColor = primaryColor;
UserInput.focusColor = '#ccc';

Alon.capture(
  document.querySelector('chat'),
  (p) => p.userInput.value,
  (message) => {
    document.querySelector('messages').appendChild(
      Habiscript.toElement(['p', message])
    )
  }
);
