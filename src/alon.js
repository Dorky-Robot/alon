(function (window) {
  const ALON_EVENT = '__AlonEvent__';

  function signalDown(element, payload) {
    const event = new CustomEvent(ALON_EVENT, {
      detail: { ...payload, __alonSignalDown__: true },
      bubbles: true,
      cancelable: true,
      composed: true
    });

    findLeafElements(element).forEach((leafElement) => {
      leafElement.dispatchEvent(event);
    });
  }

  function signalUp(element, payload) {
    element.dispatchEvent(new CustomEvent(ALON_EVENT, {
      detail: { ...payload, __alonSignalUp__: true },
      bubbles: true,
      cancelable: true,
      composed: true
    }));
  }

  function gapUp(element, matcher, transformer) {
    capture(element, matcher, function (payload, event) {
      signalUp(
        element,
        transformer ? transformer(payload) : payload,
      );
    });
  }

  function gapDown(element, matcher, transformer) {
    absorb(element, matcher, function (payload) {
      signalUp(
        element,
        transformer ? transformer(payload) : payload);
    });
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
        _genericEventHandler(e, h);
      }, true);
    }

    const h = element.alonCaptureHandlers;
    const handlers = h.get(resolver) || [];

    handlers.push(handler);
    h.set(resolver, handlers);
  }

  function bubbling(element, resolver, handler) {
    if (!element.alonAbsorbHandlers) {
      element.alonAbsorbHandlers = new Map();

      element.addEventListener(ALON_EVENT, (e) => {
        if (e.detail.__alonSignalDown__) return;
        _genericEventHandler(e, h);
      });
    }

    const h = element.alonAbsorbHandlers;
    const handlers = h.get(resolver) || [];

    handlers.push(handler);
    h.set(resolver, handlers);
  }

  function intercept(host, targetElement, eventType, callback) {
    targetElement.addEventListener(eventType, (event) => {
      if (event.preventDefault) event.preventDefault();
      callback(event);
    });
  }

  function findLeafElements(element) {
    let leafElements = [];

    function walkThrough(element) {
      // Get all children nodes
      let children = element.childNodes;

      // If the element has no children, it's a leaf
      if (children.length === 0) {
        // Assuming we only want to consider elements, not text nodes or others
        if (element instanceof HTMLElement) {
          leafElements.push(element);
        }
      } else {
        // Otherwise, go through all the children
        children.forEach((child) => {
          walkThrough(child);
        });
      }
    }

    // Start the recursive search from the provided element
    walkThrough(element);

    return leafElements;
  }


  window.Alon = {
    signalDown,
    signalUp,
    capture,
    gapDown,
    bubbling,
    gapUp,
    intercept
  };
})(window);
