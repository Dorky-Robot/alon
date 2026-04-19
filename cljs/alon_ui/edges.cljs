(ns alon-ui.edges
  (:require [alon-ui.state :as state]
            [alon-ui.file-card :as fc]))

(def OFFSET 10000)

(defn- derive-offset
  "Best-effort call-site offset within the caller's source: scan for the
   callee's name. Lets the anchor land on the right line even when the
   analyzer didn't send offsetStart."
  [from-node to-node]
  (when-let [src (:source from-node)]
    (when-let [nm (:name to-node)]
      (let [i (.indexOf src nm)]
        (when (>= i 0) i)))))

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

(defn- anchor
  "Compute endpoint {:x :y :right?} for `node-id` relative to `other-center-x`.
   If the requested node is hidden inside a collapsed ancestor, anchor at the
   nearest visible ancestor's row-head. If `call-offset` is given and the
   visible node's source is expanded, anchor at the call-site line."
  [node-id call-offset other-center-x]
  (let [s @state/state
        vis-id (nearest-visible-id s node-id)
        n (get-in s [:by-id vis-id])]
    (when n
      (when-let [pos (get-in s [:shown (:file n)])]
        (let [expanded (:expanded s)
              same?    (= vis-id node-id)
              open?    (contains? expanded vis-id)
              w        (fc/width-for (:file n))
              y-local  (if (and same? call-offset open? (:source n))
                         (+ (fc/row-top-y vis-id) fc/ROW-H
                            (fc/call-site-y vis-id call-offset))
                         (fc/row-y-center vis-id))
              my-center (+ (:x pos) (/ w 2))
              right?    (>= other-center-x my-center)
              x         (+ (:x pos) (if right? w 0))]
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
     (for [{:keys [from to type offsetStart]} (get-in s [:graph :edges])
           :let [fn'  (get-in s [:by-id from])
                 tn   (get-in s [:by-id to])
                 fpos (get-in s [:shown (:file fn')])
                 tpos (get-in s [:shown (:file tn)])]
           :when (and fn' tn fpos tpos)
           :let [t-center (+ (:x tpos) (/ (fc/width-for (:file tn)) 2))
                 f-center (+ (:x fpos) (/ (fc/width-for (:file fn')) 2))
                 offset (or offsetStart (derive-offset fn' tn))
                 a (anchor from offset t-center)
                 b (anchor to   nil    f-center)]
           :when (and a b)]
       ^{:key (str from "→" to "@" offset)}
       [:path {:d (bezier (:x a) (:y a) (:right? a)
                          (:x b) (:y b) (:right? b))
               :class type
               :marker-end "url(#arrow)"}])]))
