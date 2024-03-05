describe("bubbling", () => {
  const h = Habiscript.toElement,
    span = h(['span', { id: 'name' }]),
    container = h(
      ['div', { id: 'person' }, span]
    );

  it("catches bubbling events from signalUp event and should be seen in the capture", () => {
    document.body.appendChild(container);

    let name;
    Alon.bubbling(
      container,
      (p) => p.person.name,
      (r) => name = r
    );

    let absorbed = 0;
    Alon.capture(
      container,
      (p) => p.person.name,
      (_) => absorbed++
    );

    Alon.signalUp(
      span,
      { person: { name: 'Felix' } }
    );

    expect(name).toEqual('Felix');
    expect(absorbed).toEqual(0);
  });

  it("calls multiple handlers for different resolvers", () => {
    document.body.appendChild(container);

    let firstName;
    let lastName;
    Alon.bubbling(
      container,
      (p) => p.person.firstName,
      (r) => { firstName = r; }
    );
    Alon.bubbling(
      container,
      (p) => p.person.lastName,
      (r) => { lastName = r; }
    );

    Alon.signalUp(span, { person: { firstName: 'Felix', lastName: 'Flores' } });

    expect(firstName).toEqual('Felix');
    expect(lastName).toEqual('Flores');
  });

  it("does not call handlers when the resolver returns undefined", () => {
    document.body.appendChild(container);

    let nameCalled = false;
    Alon.bubbling(
      container,
      (p) => undefined, // Resolver fails to resolve
      (r) => { nameCalled = true; }
    );

    Alon.signalUp(span, { person: { name: 'Felix' } });

    expect(nameCalled).toBe(false);
  });

  it("does call handlers when the resolver returns false", () => {
    document.body.appendChild(container);

    let nameCalled = false;
    Alon.bubbling(
      container,
      (p) => false, // Resolver returns false
      (r) => { nameCalled = true; }
    );

    Alon.signalUp(span, { person: { name: 'Felix' } });

    expect(nameCalled).toBe(true);
  });

  it("calls the same resolver only once but multiple handlers for that resolver", () => {
    document.body.appendChild(container);

    let resolverCallCount = 0;
    const resolver = (p) => {
      resolverCallCount++;
      return p.person.name;
    };

    let name1;
    let name2;
    Alon.bubbling(
      container,
      resolver,
      (r) => { name1 = r; }
    );
    Alon.bubbling(
      container,
      resolver,
      (r) => { name2 = r; }
    );

    Alon.signalUp(span, { person: { name: 'Felix Flores' } });

    // Check that the resolver was called only once
    expect(resolverCallCount).toEqual(1);

    // Check that both handlers were called with the resolved value
    expect(name1).toEqual('Felix Flores');
    expect(name2).toEqual('Felix Flores');
  });

  it("does not propagate the event to the outer element if the capture resolves", () => {
    const span = h(['span', { id: 'name' }]);
    const inner = h(['div', { id: 'person' }, span]);
    const outer = h(['div', inner]);

    document.body.appendChild(outer);

    let innerHandlerCalled = false;
    let outerHandlerCalled = false;

    // Subscribe handler for the inner element and stop propagation
    Alon.bubbling(
      inner,
      (p) => p.person.name,
      (r, e) => {
        innerHandlerCalled = true;
      }
    );

    // Subscribe handler for the outer element
    Alon.bubbling(
      outer,
      (p) => p.person.name,
      (r, e) => {
        outerHandlerCalled = true;
      }
    );

    Alon.signalUp(span, { person: { name: 'Felix Flores' } });

    expect(innerHandlerCalled).toBe(true);
    expect(outerHandlerCalled).toBe(false);
  });

  it("does not propagate the event to the outer element if the resolver does not resolves", () => {
    const span = h(['span', { id: 'name' }]);
    const inner = h(['div', { id: 'person' }, span]);
    const outer = h(['div', inner]);

    document.body.appendChild(outer);

    // Subscribe handler for the inner element and stop propagation
    let innerHandlerCalled = false;
    Alon.bubbling(
      inner,
      (p) => p.person.random,
      (r, e) => {
        innerHandlerCalled = true;
      }
    );

    // Subscribe handler for the outer element
    let outerHandlerCalled = false;
    Alon.bubbling(
      outer,
      (p) => p.person.name,
      (r, e) => {
        console.log('outer', e, r)
        outerHandlerCalled = true;
      }
    );

    Alon.signalUp(span, { person: { name: 'Felix Flores' } });
    expect(innerHandlerCalled).toBe(false);
    expect(outerHandlerCalled).toBe(false);
  });
})