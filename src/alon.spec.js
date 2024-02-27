describe("subscribe", () => {
  it("allows elements to subscribe to specific paths", () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const container = Habiscript.toElement(
      ['div', { id: 'person' }, span]
    );

    document.body.appendChild(container);

    let num = 3;
    function handler(payload) {
      num = num + payload;
    }

    Alon.subscribe({ element: container, path: 'person.name', handler });
    Alon.signal({ element: span, path: 'person.name', payload: 2 });

    expect(num).toEqual(5);
  });

  it('allows elements to subscribe hierarchically', () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const container = Habiscript.toElement(
      ['div', { id: 'person' }, span]
    );

    document.body.appendChild(container);

    let num = 3;
    function handler(payload) {
      num = num + payload;
    }

    Alon.subscribe({ element: container, path: 'person', handler });
    Alon.signal({ element: span, path: 'person.name', payload: 2 });

    expect(num).toEqual(5);
  });


  it('allows wildcard event subscription', () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const container = Habiscript.toElement(
      ['div', { id: 'person' }, span]
    );

    document.body.appendChild(container);

    let num = 3;
    function handler(payload) {
      num = num + payload;
    }

    Alon.subscribe({ element: container, path: 'person.*', handler });
    Alon.signal({ element: span, path: 'person.name', payload: 2 });

    expect(num).toEqual(5);
  });
});


describe('_registerSubscriber', () => {
  it('adds a subscriber to the subscribers array found in "*"', () => {
    const handlers = {};
    const handler = () => { };
    Alon._registerSubscriber({
      pathSegments: ['person'],
      handlers,
      handler
    });

    expect(handlers['person']['*']).toEqual([handler]);
  });

  it('appends new handlers instead of overrides', () => {
    const handlers = {};
    const handler1 = () => { };
    const handler2 = () => { };

    Alon._registerSubscriber({
      pathSegments: ['person'],
      handlers,
      handler: handler1
    });

    Alon._registerSubscriber({
      pathSegments: ['person'],
      handlers,
      handler: handler2
    });

    expect(handlers['person']['*']).toEqual([handler1, handler2]);
  });
})