(ns alon-ui.edges
  (:require [alon-ui.state :as state]
            [alon-ui.file-card :as fc]))

(def OFFSET 10000)

(defn- anchor
  "Compute endpoint {:x :y :right?} for `node-id` relative to `other-center-x`.
   If `call-offset` is given and the node's source is expanded, anchor at the
   call-site line; otherwise at the row-head center."
  [s node-id call-offset other-center-x]
  (when-let [n (get-in s [:by-id node-id])]
    (when-let [pos (get-in s [:shown (:file n)])]
      (let [nodes    (get-in s [:by-file (:file n)])
            expanded (:expanded s)
            open?    (contains? expanded node-id)
            y-local  (if (and call-offset open? (:source n))
                       (let [row-top (fc/row-top-y nodes expanded node-id)]
                         (+ row-top fc/ROW-H
                            (fc/source-line-y (:source n) call-offset)))
                       (fc/row-y-center nodes expanded node-id))
            my-center (+ (:x pos) (/ fc/CARD-WIDTH 2))
            right?    (>= other-center-x my-center)
            x         (+ (:x pos) (if right? fc/CARD-WIDTH 0))]
        {:x x :y (+ (:y pos) y-local) :right? right?}))))

(defn- bezier [ax ay a-right? bx by b-right?]
  (let [dx  (Math/abs (- bx ax))
        len (max 60 (* dx 0.4))
        acx (+ ax (if a-right? len (- len)))
        bcx (+ bx (if b-right? len (- len)))]
    (str "M " (+ ax OFFSET) " " (+ ay OFFSET)
         " C " (+ acx OFFSET) " " (+ ay OFFSET)
         ", " (+ bcx OFFSET) " " (+ by OFFSET)
         ", " (+ bx OFFSET) " " (+ by OFFSET))))

(defn- derive-offset
  "Best-effort call-site offset: if the analyzer didn't send one, scan the
   caller's source for the callee's name. Lets the anchor land on the right
   line even when the cached graph predates the offset field."
  [from-node to-node]
  (when-let [src (:source from-node)]
    (when-let [nm (:name to-node)]
      (let [i (.indexOf src nm)]
        (when (>= i 0) i)))))

(defn edges []
  (let [s @state/state
        half (/ fc/CARD-WIDTH 2)]
    [:svg.edges {:xmlns "http://www.w3.org/2000/svg"}
     [:defs
      [:marker {:id "arrow" :viewBox "0 0 10 10"
                :refX 9 :refY 5
                :markerWidth 6 :markerHeight 6
                :orient "auto-start-reverse"}
       [:path {:d "M 0 0 L 10 5 L 0 10 z"
               :fill "#5a8aff" :opacity 0.9}]]]
     (for [{:keys [from to type offsetStart]} (get-in s [:graph :edges])
           :let [fn'  (get-in s [:by-id from])
                 tn   (get-in s [:by-id to])
                 fpos (get-in s [:shown (:file fn')])
                 tpos (get-in s [:shown (:file tn)])]
           :when (and fn' tn fpos tpos)
           :let [offset (or offsetStart (derive-offset fn' tn))
                 a (anchor s from offset (+ (:x tpos) half))
                 b (anchor s to   nil    (+ (:x fpos) half))]
           :when (and a b)]
       ^{:key (str from "→" to "@" offset)}
       [:path {:d (bezier (:x a) (:y a) (:right? a)
                          (:x b) (:y b) (:right? b))
               :class type
               :marker-end "url(#arrow)"}])]))
