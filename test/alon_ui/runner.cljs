(ns alon-ui.runner
  (:require [clojure.test :as t]))

;; Stub the browser globals state.cljs reaches for (animate-to-fit! reads
;; window dimensions; camera tween uses performance.now + rAF). Tests don't
;; exercise the visuals, but the code paths run. nbb won't let us `set!`
;; js/window, so assign onto globalThis through JS interop.
(let [g js/globalThis]
  (aset g "window" #js {:innerWidth 1024
                        :innerHeight 768
                        :addEventListener    (fn [_ _])
                        :removeEventListener (fn [_ _])})
  (when-not (.-performance g)
    (aset g "performance" #js {:now (fn [] 0)}))
  (when-not (.-requestAnimationFrame g)
    (aset g "requestAnimationFrame" (fn [_]))))

(require '[alon-ui.file-card-test]
         '[alon-ui.state-test]
         '[alon-ui.edges-test]
         '[alon-ui.highlight-test])

(defn run! []
  (let [{:keys [fail error]}
        (t/run-tests 'alon-ui.file-card-test
                     'alon-ui.state-test
                     'alon-ui.edges-test
                     'alon-ui.highlight-test)]
    (when (pos? (+ (or fail 0) (or error 0)))
      (js/process.exit 1))))

(run!)
