(function (factory) {
  typeof define === 'function' && define.amd ? define(factory) :
  factory();
})((function () { 'use strict';

  const ALON_EVENT = '__AlonEvent__';

  function signalUp(element, payload) {
    element.dispatchEvent(new CustomEvent(ALON_EVENT, {
      detail: { ...payload, __alonSignalUp__: true },
      bubbles: true,
      cancelable: true,
      composed: true
    }));
  }

  function _genericEventHandler(e, handlerMap) {
    e.stopPropagation();

    for (const [resolver, handlers] of handlerMap.entries()) {
      const result = resolver(e.detail);
      if (result !== undefined) {
        handlers.forEach((handler) => handler(result, e));
      }
    }
  }

  function capture(element, resolver, handler) {
    if (!element.alonCaptureHandlers) {
      element.alonCaptureHandlers = new Map();

      element.addEventListener(ALON_EVENT, (e) => {
        if (e.detail.__alonSignalUp__) return;
        _genericEventHandler(e, element.alonCaptureHandlers);
      }, true);
    }

    const handlers = element.alonCaptureHandlers.get(resolver) || [];
    handlers.push(handler);
    element.alonCaptureHandlers.set(resolver, handlers);
  }

  function bubbling(element, resolver, handler) {
    if (!element.alonBubblingHandlers) {
      element.alonBubblingHandlers = new Map();

      element.addEventListener(ALON_EVENT, (e) => {
        if (e.detail.__alonSignalDown__) return;
        _genericEventHandler(e, element.alonBubblingHandlers);
      });
    }

    const handlers = element.alonBubblingHandlers.get(resolver) || [];
    handlers.push(handler);
    element.alonBubblingHandlers.set(resolver, handlers);
  }

  describe("bubbling", () => {
    const h = Habiscript.toElement,
      span = h(['span', { id: 'name' }]),
      container = h(
        ['div', { id: 'person' }, span]
      );

    it("catches bubbling events from signalUp event and should be seen in the capture", () => {
      document.body.appendChild(container);

      let name;
      bubbling(
        container,
        (p) => p.person.name,
        (r) => name = r
      );

      let absorbed = 0;
      capture(
        container,
        (p) => p.person.name,
        (_) => absorbed++
      );

      signalUp(
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
      bubbling(
        container,
        (p) => p.person.firstName,
        (r) => { firstName = r; }
      );
      bubbling(
        container,
        (p) => p.person.lastName,
        (r) => { lastName = r; }
      );

      signalUp(span, { person: { firstName: 'Felix', lastName: 'Flores' } });

      expect(firstName).toEqual('Felix');
      expect(lastName).toEqual('Flores');
    });

    it("does not call handlers when the resolver returns undefined", () => {
      document.body.appendChild(container);

      let nameCalled = false;
      bubbling(
        container,
        (p) => undefined, // Resolver fails to resolve
        (r) => { nameCalled = true; }
      );

      signalUp(span, { person: { name: 'Felix' } });

      expect(nameCalled).toBe(false);
    });

    it("does call handlers when the resolver returns false", () => {
      document.body.appendChild(container);

      let nameCalled = false;
      bubbling(
        container,
        (p) => false, // Resolver returns false
        (r) => { nameCalled = true; }
      );

      signalUp(span, { person: { name: 'Felix' } });

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
      bubbling(
        container,
        resolver,
        (r) => { name1 = r; }
      );
      bubbling(
        container,
        resolver,
        (r) => { name2 = r; }
      );

      signalUp(span, { person: { name: 'Felix Flores' } });

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
      bubbling(
        inner,
        (p) => p.person.name,
        (r, e) => {
          innerHandlerCalled = true;
        }
      );

      // Subscribe handler for the outer element
      bubbling(
        outer,
        (p) => p.person.name,
        (r, e) => {
          outerHandlerCalled = true;
        }
      );

      signalUp(span, { person: { name: 'Felix Flores' } });

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
      bubbling(
        inner,
        (p) => p.person.random,
        (r, e) => {
          innerHandlerCalled = true;
        }
      );

      // Subscribe handler for the outer element
      let outerHandlerCalled = false;
      bubbling(
        outer,
        (p) => p.person.name,
        (r, e) => {
          console.log('outer', e, r);
          outerHandlerCalled = true;
        }
      );

      signalUp(span, { person: { name: 'Felix Flores' } });
      expect(innerHandlerCalled).toBe(false);
      expect(outerHandlerCalled).toBe(false);
    });
  });

}));
