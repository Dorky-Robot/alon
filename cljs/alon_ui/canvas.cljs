(ns alon-ui.canvas
  (:require [alon-ui.state :as state]
            [alon-ui.file-card :as file-card]
            [alon-ui.edges :as edges]
            [reagent.core :as r]))

(defn- viewport []
  (let [pan-state (r/atom nil)
        on-down   (fn [e]
                    (let [target (.-target e)]
                      (when (or (= target (.-currentTarget e))
                                (.. target -classList (contains "viewport"))
                                (.. target -classList (contains "canvas")))
                        (reset! pan-state {:x (- (.-clientX e) (:pan-x @state/state))
                                           :y (- (.-clientY e) (:pan-y @state/state))}))))
        on-move   (fn [e]
                    (when-let [p @pan-state]
                      (state/set-pan! (- (.-clientX e) (:x p))
                                      (- (.-clientY e) (:y p)))))
        on-up     (fn [_] (reset! pan-state nil))
        on-wheel  (fn [e]
                    (.preventDefault e)
                    (if (.-ctrlKey e)
                      ;; Trackpad pinch surfaces as wheel + ctrlKey → zoom.
                      (let [factor   (if (pos? (.-deltaY e)) 0.9 1.1)
                            zoom     (:zoom @state/state)
                            new-zoom (max 0.2 (min 3 (* zoom factor)))
                            rect     (.. e -currentTarget getBoundingClientRect)
                            px       (- (.-clientX e) (.-left rect) (/ (.-width rect) 2))
                            py       (- (.-clientY e) (.-top rect)  (/ (.-height rect) 2))]
                        (state/set-zoom! new-zoom px py))
                      ;; Plain wheel (mouse scroll or two-finger drag) pans
                      ;; the canvas. Cards don't have internal scroll, so
                      ;; wheel-over-a-card falls up to us and pans the stage.
                      (let [{:keys [pan-x pan-y]} @state/state]
                        (state/set-pan! (- pan-x (.-deltaX e))
                                        (- pan-y (.-deltaY e))))))]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (.addEventListener js/window "mousemove" on-move)
        (.addEventListener js/window "mouseup"   on-up))
      :component-will-unmount
      (fn [_]
        (.removeEventListener js/window "mousemove" on-move)
        (.removeEventListener js/window "mouseup"   on-up))
      :reagent-render
      (fn []
        (let [{:keys [shown pan-x pan-y zoom]} @state/state]
          [:div.viewport
           {:class         (when @pan-state "panning")
            :on-mouse-down on-down
            :on-wheel      on-wheel}
           [:div.canvas
            {:style {:transform
                     (str "translate(" pan-x "px," pan-y "px) scale(" zoom ")")}}
            [edges/edges]
            (for [fn-id (keys shown)]
              ^{:key fn-id} [file-card/fn-card fn-id])]
           [:button.fit-btn
            {:title "fit all cards to viewport"
             :on-mouse-down (fn [e] (.stopPropagation e))
             :on-click (fn [_] (state/animate-to-fit!))}
            "fit"]]))})))

(defn root []
  [viewport])
