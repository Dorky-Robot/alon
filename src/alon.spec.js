describe("get", () => {
  it("returns the handlers that exactly matches the path", () => {
    const val = Alon.get('person.name', { person: { name: 2 } })
    expect(val).toEqual(2);
  });

  it("accessing a nested property using an array path", () => {
    const val = Alon.get(
      'user.address.city',
      {
        user: {
          address: {
            city: "Lorain",
            state: "Ohio"
          }
        }
      }
    );

    expect(val).toEqual("Lorain");
  });

  it("accessing a specific elements of an array", () => {
    const val = Alon.get(
      'user.addresses[1].city',
      {
        user: {
          addresses: [ // Corrected from 'address' to 'addresses'
            { city: "Lorain" },
            { city: "Cleveland" }
          ]
        }
      }
    );

    expect(val).toEqual("Cleveland");
  });

  it("returns undefined when accessing index that is out of bound", () => {
    const val = Alon.get(
      'user.addresses[33].city',
      {
        user: {
          addresses: [ // Corrected from 'address' to 'addresses'
            { city: "Lorain" },
            { city: "Cleveland" }
          ]
        }
      }
    );

    expect(val).toEqual(undefined);
  });

  it("attempting to access a non-existent property", () => {
    const val = Alon.get(
      'user.city',
      {
        user: {
          addresses: [ // Corrected from 'address' to 'addresses'
            { city: "Lorain" },
            { city: "Cleveland" }
          ]
        }
      }
    );

    expect(val).toEqual(undefined);
  });

  it("attempting to access a non-existent property with default value", () => {
    const val = Alon.get(
      'user.city',
      {
        user: {
          addresses: [ // Corrected from 'address' to 'addresses'
            { city: "Lorain" },
            { city: "Cleveland" }
          ]
        }
      },
      "Not found"
    );

    expect(val).toEqual("Not found");
  });
});

describe("subscribe", () => {
  it("allows elements to subscribe to specific paths", () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const container = Habiscript.toElement(
      ['div', { id: 'person' }, span]
    );

    document.body.appendChild(container);

    let name;
    Alon.subscribe(
      container,
      (p) => { return Alon.get('person.name', p) },
      (r) => {
        name = r;
      }
    );

    Alon.signal(
      span,
      { person: { name: 'Felix' } }
    );

    expect(name).toEqual('Felix');
  });

  it("calls multiple handlers for different resolvers", () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const container = Habiscript.toElement(['div', { id: 'person' }, span]);
    document.body.appendChild(container);

    let firstName;
    let lastName;
    Alon.subscribe(
      container,
      (p) => Alon.get('person.firstName', p), // Resolver for the first name
      (r) => { firstName = r; }
    );
    Alon.subscribe(
      container,
      (p) => Alon.get('person.lastName', p), // Resolver for the last name
      (r) => { lastName = r; }
    );

    Alon.signal(span, { person: { firstName: 'Felix', lastName: 'Flores' } });

    expect(firstName).toEqual('Felix');
    expect(lastName).toEqual('Flores');
  });

  it("does not call handlers when the resolver returns undefined", () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const container = Habiscript.toElement(['div', { id: 'person' }, span]);
    document.body.appendChild(container);

    let nameCalled = false;
    Alon.subscribe(
      container,
      (p) => undefined, // Resolver fails to resolve
      (r) => { nameCalled = true; }
    );

    Alon.signal(span, { person: { name: 'Felix' } });

    expect(nameCalled).toBe(false);
  });

  it("does call handlers when the resolver returns false", () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const container = Habiscript.toElement(['div', { id: 'person' }, span]);
    document.body.appendChild(container);

    let nameCalled = false;
    Alon.subscribe(
      container,
      (p) => false, // Resolver returns false
      (r) => { nameCalled = true; }
    );

    Alon.signal(span, { person: { name: 'Felix' } });

    expect(nameCalled).toBe(true);
  });

  it("calls the same resolver only once but multiple handlers for that resolver", () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const container = Habiscript.toElement(['div', { id: 'person' }, span]);
    document.body.appendChild(container);

    let resolverCallCount = 0;
    const resolver = (p) => {
      resolverCallCount++;
      return Alon.get('person.name', p);
    };

    let name1;
    let name2;
    Alon.subscribe(
      container,
      resolver,
      (r) => { name1 = r; }
    );
    Alon.subscribe(
      container,
      resolver,
      (r) => { name2 = r; }
    );

    Alon.signal(span, { person: { name: 'Felix Flores' } });

    // Check that the resolver was called only once
    expect(resolverCallCount).toEqual(1);

    // Check that both handlers were called with the resolved value
    expect(name1).toEqual('Felix Flores');
    expect(name2).toEqual('Felix Flores');
  });

  it("does not propagate the event to the outer element when stopPropagation is called", () => {
    const span = Habiscript.toElement(['span', { id: 'name' }]);
    const inner = Habiscript.toElement(['div', { id: 'person' }, span]);
    const outer = Habiscript.toElement(['div', inner]);

    document.body.appendChild(outer);

    let innerHandlerCalled = false;
    let outerHandlerCalled = false;

    // Subscribe handler for the inner element and stop propagation
    Alon.subscribe(
      inner,
      (p) => Alon.get('person.name', p),
      (r, e) => {
        innerHandlerCalled = true;
        e.stopPropagation(); // Stop the event from bubbling up
      }
    );

    // Subscribe handler for the outer element
    Alon.subscribe(
      outer,
      (p) => Alon.get('person.name', p),
      (r, e) => {
        outerHandlerCalled = true;
      }
    );

    Alon.signal(span, { person: { name: 'Felix Flores' } });

    expect(innerHandlerCalled).toBe(true);
    expect(outerHandlerCalled).toBe(false);
  });
})