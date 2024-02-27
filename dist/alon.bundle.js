(function (factory) {
  typeof define === 'function' && define.amd ? define(factory) :
    factory();
})((function () {
  'use strict';

  (function (window) {

    function get(path, object, defaultValue) {
      const keys = path.replace(/\[(\d+)\]/g, '.$1').split('.');
      let result = object;

      for (let key of keys) {
        if (result === undefined || result === null) {
          return defaultValue;
        }
        // Convert key to a number if it's a valid array index
        const index = isNaN(Number(key)) ? key : Number(key);
        result = result.hasOwnProperty(index) ? result[index] : undefined;
      }

      return result !== undefined ? result : defaultValue;
    }

    function signal(element, payload) {
      element.dispatchEvent(new CustomEvent(this.ALON_EVENT, {
        detail: payload,
        bubbles: true,
        cancelable: true
      }));
    }

    function subscribe(element, resolver, handler) {
      if (!element.alonHandlers) {
        element.alonHandlers = new Map();

        element.addEventListener(this.ALON_EVENT, (e) => {
          for (const [resolver, handlers] of e.currentTarget.alonHandlers.entries()) {
            const result = resolver(e.detail);
            // Check if result is not undefined to call handlers
            if (result !== undefined) {
              handlers.forEach((handler) => handler(result, e));
            }
          }
        });
      }

      if (element.alonHandlers.has(resolver)) {
        element.alonHandlers.get(resolver).push(handler);
      } else {
        element.alonHandlers.set(resolver, [handler]);
      }
    }


    window.Alon = {
      get,
      signal,
      subscribe
    };
  })(window);

}));
