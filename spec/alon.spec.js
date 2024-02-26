const { JSDOM } = require("jsdom");
const { get, subscribe, extractLeafNodes } = require("./alon.js");

describe("subscribe", () => {
  beforeAll(() => {
    // Setup JSDOM
    const jsdom = new JSDOM("");
    global.document = jsdom.window.document;
    global.Node = jsdom.window.Node;
    document = jsdom.window.document;
  });
})

describe("get", () => {
  it("returns the handlers that exactly matches the path", () => {
    const handler = () => { };
    const handlers = get({
      path: 'person.name',
      candidates: { person: { name: handler } }
    });

    expect(handlers).toStrictEqual([handler]);
  });


  it("accessing a nested property using an array path", () => {
    const handler = () => console.log('Anytown');
    const handlers = get({
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

    expect(handlers).toStrictEqual([handler]);
  });

  it("attempting to access a non-existent property", () => {
    const handlers = get({
      path: 'user.age',
      candidates: {
        user: {
          name: () => { }
        }
      },
      payload: 1
    });

    expect(handlers).toStrictEqual([]);
  });

  it("using a non-existent path", () => {
    const handlers = get({
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
    expect(handlers).toStrictEqual([]);
  });

  describe('get', () => {
    it("using a non-existent path in a deeply nested object", () => {
      const result = get({
        path: 'user.details.age',
        candidates: {
          user: {
            name: 'John',
            history: []
          }
        },
        payload: 1
      });
      expect(result).toStrictEqual([]);
    });

    it("using a non-existent array index", () => {
      const result = get({
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
      expect(result).toStrictEqual([]);
    });

    it("using a non-existent path with mixed types", () => {
      const result = get({
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
      expect(result).toStrictEqual([]);
    });

    it("using a path that leads to an undefined value", () => {
      const result = get({
        path: 'user.preferences.theme',
        candidates: {
          user: {
            preferences: {}
          }
        },
        payload: 1
      });
      expect(result).toStrictEqual([]);
    });

    it("using a completely irrelevant path", () => {
      const result = get({
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
      expect(result).toStrictEqual([]);
    });
  });
});

describe('extractLeafNodes', () => {
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
    expect(extractLeafNodes(obj)).toEqual([1, 2, 3, 4, 5]);
  });

  it('handles objects with array properties by treating array entries as leaf nodes', () => {
    const obj = {
      a: [1, 2],
      b: {
        c: [3, 4],
        d: 5,
      },
    };
    expect(extractLeafNodes(obj)).toEqual([[1, 2], [3, 4], 5]);
  });

  it('returns an empty array for an empty object', () => {
    expect(extractLeafNodes({})).toEqual([]);
  });

  it('returns the same object if it is already a leaf node', () => {
    expect(extractLeafNodes(10)).toEqual([10]);
    expect(extractLeafNodes("string")).toEqual(["string"]);
  });

  it('handles objects with nested empty objects', () => {
    const obj = {
      a: {},
      b: {
        c: {},
      },
      d: 1,
    };
    expect(extractLeafNodes(obj)).toEqual([{}, {}, 1]);
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
    expect(extractLeafNodes(obj)).toEqual([null, undefined, null, 2]);
  });
});
