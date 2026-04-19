(ns alon-ui.edges
  (:require [alon-ui.state :as state]
            [alon-ui.file-card :as fc]))

(def OFFSET 10000)

(defn- visible?
  "A node is rendered iff every ancestor in its chain is in :expanded."
  [s node-id]
  (loop [id node-id]
    (let [pid (:parentId (get-in s [:by-id id]))]
      (cond
        (nil? pid) true
        (not (contains? (:expanded s) pid)) false
        :else (recur pid)))))

(defn- nearest-visible-id
  "Climb until we hit a node that is actually rendered. Falls back to the
   nearest visible ancestor when the requested node is hidden inside a
   collapsed parent — otherwise row-top-y returns a phantom y far below
   the visible card."
  [s node-id]
  (loop [id node-id]
    (cond
      (nil? id) nil
      (visible? s id) id
      :else (recur (:parentId (get-in s [:by-id id]))))))

(defn- ancestor?
  "True if `a` is an ancestor of `d` in the captured-fn tree."
  [s a d]
  (loop [id d]
    (let [pid (:parentId (get-in s [:by-id id]))]
      (cond
        (nil? pid) false
        (= pid a)  true
        :else      (recur pid)))))

(defn- anchor
  "Compute endpoint {:x :y :right?} for `node-id` relative to `other-center-x`.
   In container mode, nested rows live inset from the file-card edge by
   their depth × CONTAINER-PAD on each side, so anchor at that nested edge
   instead of the outer file-card border."
  [node-id other-center-x]
  (let [s @state/state
        vis-id (nearest-visible-id s node-id)
        n      (get-in s [:by-id vis-id])]
    (when n
      (when-let [pos (get-in s [:shown (:file n)])]
        (let [card-w     (fc/width-for (:file n))
              inset      (fc/row-x-inset vis-id)
              row-left   (+ (:x pos) inset)
              row-right  (+ (:x pos) (- card-w inset))
              row-center (/ (+ row-left row-right) 2)
              right?     (>= other-center-x row-center)
              x          (if right? row-right row-left)
              y-local    (fc/row-y-center vis-id)]
          {:x x :y (+ (:y pos) y-local) :right? right?})))))

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
  (let [s @state/state]
    [:svg.edges {:xmlns "http://www.w3.org/2000/svg"}
     [:defs
      [:marker {:id "arrow" :viewBox "0 0 10 10"
                :refX 9 :refY 5
                :markerWidth 6 :markerHeight 6
                :orient "auto-start-reverse"}
       [:path {:d "M 0 0 L 10 5 L 0 10 z"
               :fill "#5a8aff" :opacity 0.9}]]]
     (for [{:keys [from to type]} (get-in s [:graph :edges])
           :let [fn'  (get-in s [:by-id from])
                 tn   (get-in s [:by-id to])
                 fpos (get-in s [:shown (:file fn')])
                 tpos (get-in s [:shown (:file tn)])]
           ;; Suppress parent⇄child edges: containment IS the relationship,
           ;; an arrow swooping into the box that already holds the target
           ;; just adds noise.
           :when (and fn' tn fpos tpos
                      (not (ancestor? s from to))
                      (not (ancestor? s to from)))
           :let [t-cx (+ (:x tpos) (/ (fc/width-for (:file tn)) 2))
                 f-cx (+ (:x fpos) (/ (fc/width-for (:file fn')) 2))
                 a (anchor from t-cx)
                 b (anchor to   f-cx)]
           :when (and a b)]
       ^{:key (str from "→" to)}
       [:path {:d (bezier (:x a) (:y a) (:right? a)
                          (:x b) (:y b) (:right? b))
               :class type
               :marker-end "url(#arrow)"}])]))
