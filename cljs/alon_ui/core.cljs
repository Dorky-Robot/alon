(ns alon-ui.core
  (:require [alon-ui.state :as state]
            [alon-ui.canvas :as canvas]
            [reagent.dom :as rdom]))

(defn- read-injected-graph []
  (-> (.getElementById js/document "alon-graph")
      .-textContent
      js/JSON.parse
      (js->clj :keywordize-keys true)))

(defn- update-hud! [graph]
  (let [files (count (into #{} (map :file) (:nodes graph)))]
    (set! (.-textContent (.getElementById js/document "hud-file"))
          (str (:root graph)
               " · " files " files"
               " · " (count (:nodes graph)) " fns"
               " · " (count (:edges graph)) " edges"
               (when (zero? (count (:nodes graph)))
                 " — no functions found")))))

(defn- show-error! [msg]
  (.error js/console "alon init failed:" msg)
  (set! (.-textContent (.getElementById js/document "hud-file"))
        (str "init error: " msg)))

(defn ^:export init []
  (try
    (let [graph (read-injected-graph)]
      (.log js/console "alon: graph parsed" (clj->js graph))
      (state/init-graph! graph)
      (update-hud! graph)
      (rdom/render [canvas/root] (.getElementById js/document "app"))
      (.log js/console "alon: render complete"))
    (catch :default e
      (show-error! (or (.-message e) (str e))))))

(init)
