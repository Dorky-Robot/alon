const ALON_EVENT = '__AlonEvent__';

export function signalDown(element, payload) {
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

export function signalUp(element, payload) {
  element.dispatchEvent(new CustomEvent(ALON_EVENT, {
    detail: { ...payload, __alonSignalUp__: true },
    bubbles: true,
    cancelable: true,
    composed: true
  }));
}

export function gapUp(element, matcher, transformer) {
  bubbling(element, matcher, function (payload, event) {
    signalUp(
      element,
      transformer ? transformer(payload) : payload,
    );
  });
}

export function gapDown(element, matcher, transformer) {
  capture(element, matcher, function (payload) {
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

export function capture(element, resolver, handler) {
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

export function bubbling(element, resolver, handler) {
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

export function intercept(host, targetElement, eventType, callback) {
  targetElement.addEventListener(eventType, (event) => {
    if (event.preventDefault) event.preventDefault();
    callback(event);
  });
}

function findLeafElements(element) {
  let leafElements = [];

  function walkThrough(element) {
    let children = element.childNodes;
    if (children.length === 0) {
      if (element instanceof HTMLElement) {
        leafElements.push(element);
      }
    } else {
      children.forEach((child) => {
        walkThrough(child);
      });
    }
  }

  walkThrough(element);
  return leafElements;
}

