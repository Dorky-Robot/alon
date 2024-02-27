describe("get", () => {
  it("returns the handlers that exactly matches the path", () => {
    const handler = () => { };
    const handlers = Alon.get({
      path: 'person.name',
      candidates: { person: { name: handler } }
    });

    expect(handlers).toEqual([handler]);
  });


  it("accessing a nested property using an array path", () => {
    const handler = () => console.log('Anytown');
    const handlers = Alon.get({
      path: 'user.address.city',
      candidates: {
        user: {
          address: {
            city: handler
          }
        }
      },
      payload: 1
    });

    expect(handlers).toEqual([handler]);
  });

  it("attempting to access a non-existent property", () => {
    const handlers = Alon.get({
      path: 'user.age',
      candidates: {
        user: {
          name: () => { }
        }
      },
      payload: 1
    });

    expect(handlers).toEqual([]);
  });

  it("using a non-existent path", () => {
    const handlers = Alon.get({
      path: 'user.history[0].details',
      candidates: {
        user: {
          history: [
            {
              details: () => { }
            }
          ]
        }
      },
      payload: 1
    });

    // Assuming getHandlers is designed to not handle array indices in paths directly
    expect(handlers).toEqual([]);
  });

  describe('get', () => {
    it("using a non-existent path in a deeply nested object", () => {
      const result = Alon.get({
        path: 'user.details.age',
        candidates: {
          user: {
            name: 'John',
            history: []
          }
        },
        payload: 1
      });
      expect(result).toEqual([]);
    });

    it("using a non-existent array index", () => {
      const result = Alon.get({
        path: 'user.history[5]',
        candidates: {
          user: {
            history: [
              { event: 'joined' },
              { event: 'promoted' }
            ]
          }
        },
        payload: 1
      });
      expect(result).toEqual([]);
    });

    it("using a non-existent path with mixed types", () => {
      const result = Alon.get({
        path: 'user.settings.notifications.email',
        candidates: {
          user: {
            settings: {
              notifications: {
                sms: true
              }
            }
          }
        },
        payload: 1
      });
      expect(result).toEqual([]);
    });

    it("using a path that leads to an undefined value", () => {
      const result = Alon.get({
        path: 'user.preferences.theme',
        candidates: {
          user: {
            preferences: {}
          }
        },
        payload: 1
      });
      expect(result).toEqual([]);
    });

    it("using a completely irrelevant path", () => {
      const result = Alon.get({
        path: 'nonexistent.property.deeply.nested',
        candidates: {
          user: {
            history: [
              { event: 'logged in' }
            ]
          }
        },
        payload: 1
      });
      expect(result).toEqual([]);
    });
  });
});

describe('Alon.extractLeafNodes', () => {
  it('extracts leaf nodes from a nested object', () => {
    const obj = {
      a: 1,
      b: {
        c: 2,
        d: {
          e: 3,
          f: 4,
        },
      },
      g: 5,
    };
    expect(Alon.extractLeafNodes(obj)).toEqual([1, 2, 3, 4, 5]);
  });

  it('handles objects with array properties by treating array entries as leaf nodes', () => {
    const obj = {
      a: [1, 2],
      b: {
        c: [3, 4],
        d: 5,
      },
    };
    expect(Alon.extractLeafNodes(obj)).toEqual([[1, 2], [3, 4], 5]);
  });

  it('returns an empty array for an empty object', () => {
    expect(Alon.extractLeafNodes({})).toEqual([]);
  });

  it('returns the same object if it is already a leaf node', () => {
    expect(Alon.extractLeafNodes(10)).toEqual([10]);
    expect(Alon.extractLeafNodes("string")).toEqual(["string"]);
  });

  it('handles objects with nested empty objects', () => {
    const obj = {
      a: {},
      b: {
        c: {},
      },
      d: 1,
    };
    expect(Alon.extractLeafNodes(obj)).toEqual([{}, {}, 1]);
  });

  it('ignores null and undefined values', () => {
    const obj = {
      a: null,
      b: undefined,
      c: {
        d: null,
        e: 2,
      },
    };
    expect(Alon.extractLeafNodes(obj)).toEqual([null, undefined, null, 2]);
  });
});


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
});