(ns alon-ui.edges
  (:require [alon-ui.state :as state]
            [alon-ui.file-card :as fc]))

(def OFFSET 10000)

(defn- box-anchor
  "Endpoint on `fn-id`'s box edge, picking the side closest to `other-cx`.
   Vertical anchor is the row-head center so arrowheads land on the title."
  [fn-id other-cx]
  (let [s @state/state]
    (when-let [pos (get-in s [:shown fn-id])]
      (let [w      (fc/width-for fn-id)
            left   (:x pos)
            right  (+ left w)
            center (/ (+ left right) 2)
            right? (>= other-cx center)
            x      (if right? right left)
            y      (+ (:y pos) (/ fc/ROW-H 2))]
        {:x x :y y :right? right?}))))

(defn- call-anchor
  "Tail anchor at the line of the call-site inside `from-id`'s body. X
   hugs the nearer box edge; Y sits on the vertical center of the line."
  [from-id edge other-cx]
  (let [s @state/state]
    (when-let [pos (get-in s [:shown from-id])]
      (let [w      (fc/width-for from-id)
            left   (:x pos)
            right  (+ left w)
            center (/ (+ left right) 2)
            right? (>= other-cx center)
            x      (if right? right left)
            y      (fc/call-site-y from-id (:offsetStart edge))]
        (when y
          {:x x :y y :right? right?})))))

(defn- bezier [ax ay a-right? bx by b-right?]
  (let [dx  (Math/abs (- bx ax))
        len (max 60 (* dx 0.4))
        acx (+ ax (if a-right? len (- len)))
        bcx (+ bx (if b-right? len (- len)))]
    (str "M " (+ ax OFFSET) " " (+ ay OFFSET)
         " C " (+ acx OFFSET) " " (+ ay OFFSET)
         ", " (+ bcx OFFSET) " " (+ by OFFSET)
         ", " (+ bx OFFSET) " " (+ by OFFSET))))

(defn edges []
  (let [s      @state/state
        shown  (:shown s)
        eb     (:edges-by-from s)
        visible (for [[from-id es] eb
                      :when (contains? shown from-id)
                      e es
                      :when (contains? shown (:to e))]
                  [from-id e])]
    [:svg.edges {:xmlns "http://www.w3.org/2000/svg"}
     [:defs
      [:marker {:id "arrow" :viewBox "0 0 10 10"
                :refX 9 :refY 5
                :markerWidth 6 :markerHeight 6
                :orient "auto-start-reverse"}
       [:path {:d "M 0 0 L 10 5 L 0 10 z"
               :fill "#5a8aff" :opacity 0.9}]]]
     (for [[from-id e] visible
           :let [to-id (:to e)
                 tpos  (get shown to-id)
                 fpos  (get shown from-id)
                 t-cx  (+ (:x tpos) (/ (fc/width-for to-id) 2))
                 f-cx  (+ (:x fpos) (/ (fc/width-for from-id) 2))
                 a     (call-anchor from-id e t-cx)
                 b     (box-anchor  to-id   f-cx)]
           :when (and a b)]
       ^{:key (str from-id "→" to-id "@" (:offsetStart e))}
       [:path {:d (bezier (:x a) (:y a) (:right? a)
                          (:x b) (:y b) (:right? b))
               :class (or (:type e) "call")
               :marker-end "url(#arrow)"}])]))
