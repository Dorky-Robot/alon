(ns alon-ui.state
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; Schema:
;;   {:graph         {:nodes [...] :edges [...] :root "..."}
;;    :by-id         {node-id node}
;;    :children-of   {parent-id [child-id ...]}   ; immediate children, by parentId
;;    :edges-by-from {renderable-fn-id [{:to ... :type ... :offsetStart ... :offsetEnd ... :orig-from ...} ...]}
;;    :width-by-id   {fn-id px}       ; analytical width (fits widest source line)
;;    :height-by-id  {fn-id px}       ; measured from DOM
;;    :shown         {fn-id {:x N :y N :opened-by fn-id|nil :root? bool}}
;;    :expanded-nested #{nested-id ...}  ; Xcode-style disclosure: empty = all nested
;;                                       ; fns render as `fn(...) { … }` pills
;;    :fit-mode      :all | :solo    ; persistent camera mode. :all → any layout
;;                                    ; change fits the whole graph; :solo → any
;;                                    ; layout change (or new focus) fits :focused.
;;                                    ; Double-tap on a card toggles between the two.
;;                                    ; Manual pan/zoom is a one-off override that
;;                                    ; does NOT change mode.
;;    :focused       fn-id           ; in :solo mode this is also the fit target
;;    :trail         [fn-id ...]
;;    :pan-x N :pan-y N :zoom N}
;;
;; The canvas renders one box per function. Clicking a blue call-link in a
;; function's source spawns the callee as its own box with an arrow anchored
;; at the call-site. No file cards, no nested splicing — each box is just one
;; function's own source.

(def ^:private CHAR-W      6.6)
(def ^:private SOURCE-PAD  28)
(def ^:private MIN-WIDTH   280)
(def ^:private MAX-WIDTH   900)

(defn- max-line-len [s]
  (if (string? s)
    (transduce (map count) max 0 (str/split s #"\n"))
    0))

(defn- analytical-width [node]
  (-> (+ (* (max-line-len (:source node)) CHAR-W) SOURCE-PAD)
      Math/ceil
      (max MIN-WIDTH)
      (min MAX-WIDTH)
      int))

(defonce state
  (r/atom {:graph           nil
           :by-id           {}
           :children-of     {}
           :edges-by-from   {}
           :width-by-id     {}
           :height-by-id    {}
           :shown           {}
           :expanded-nested #{}
           :fit-mode        :all
           :focused         nil
           :trail           []
           :pan-x           0
           :pan-y           0
           :zoom            1}))

;; --- Renderability --------------------------------------------------------
;;
;; A node is renderable as its own box iff its type is function/method AND
;; it sits at a structurally "independent" position — module top-level or
;; directly inside a class (i.e. a ClassMethod). Nested captured functions
;; (inner FunctionDeclarations, ObjectMethods, arrow-bound-to-local-const)
;; are intentionally NOT renderable: they stay inline as plain text inside
;; their enclosing box, and any outbound call they make is lifted so the
;; call-link lives on the enclosing box's source.

(defn- renderable? [by-id id]
  (when-let [n (get by-id id)]
    (let [t  (:type n)
          pt (:type (get by-id (:parentId n)))]
      (and (or (= t "function") (= t "method"))
           (or (nil? (:parentId n)) (= pt "class"))))))

(defn- lift-to-renderable [by-id id]
  (loop [cur id]
    (cond
      (nil? cur)                 nil
      (renderable? by-id cur)    cur
      :else (recur (:parentId (get by-id cur))))))

(defn- rewrite-edges
  "Lift each call edge's :from to the nearest renderable ancestor and
   rebase the offsets relative to that ancestor's :start. Drops edges
   whose from and to collapse to the same box (intra-box hops aren't
   worth rendering)."
  [by-id edges]
  (for [{:keys [from to type offsetStart offsetEnd]} edges
        :let [lifted-from (lift-to-renderable by-id from)
              lifted-to   (lift-to-renderable by-id to)]
        :when (and lifted-from lifted-to (not= lifted-from lifted-to))
        :let [from-n   (get by-id from)
              lifted-n (get by-id lifted-from)
              delta    (- (:start from-n) (:start lifted-n))]]
    {:from        lifted-from
     :to          lifted-to
     :type        type
     :orig-from   from
     :offsetStart (+ offsetStart delta)
     :offsetEnd   (+ offsetEnd   delta)}))

;; --- Layout ---------------------------------------------------------------

(def ^:private CARD-COL-GAP   120)
(def ^:private CARD-STACK-GAP 40)
(def ^:private CARD-FALLBACK-H 160)

(defn card-height-est [fn-id]
  (or (get-in @state [:height-by-id fn-id]) CARD-FALLBACK-H))

(declare reflow! animate-to-fit! refit!)

(defn set-measured-height! [fn-id h]
  (let [cur (get-in @state [:height-by-id fn-id])]
    (when (and h (or (nil? cur) (not= (int cur) (int h))))
      (swap! state assoc-in [:height-by-id fn-id] h)
      (reflow!)
      (refit!))))

(defn- next-y-below-siblings
  "Lowest free y in the lane spawned by `opener-id`. New children of the
   same opener cascade below previously-spawned ones."
  [shown opener-id fallback-y]
  (let [sibs (filter (fn [[_ pos]] (= (:opened-by pos) opener-id)) shown)]
    (if (empty? sibs)
      fallback-y
      (apply max (map (fn [[id pos]]
                        (+ (:y pos) (card-height-est id) CARD-STACK-GAP))
                      sibs)))))

(defn- card-depth [shown fn-id]
  (loop [id fn-id, d 0]
    (if-let [opener (:opened-by (get shown id))]
      (recur opener (inc d))
      d)))

(defn reflow!
  "Re-stack children of each opener in y order so a box that grew taller
   pushes its siblings down. Root boxes keep their position. Openers are
   processed outermost-first so each lane sees its opener's updated pos."
  []
  (let [s     @state
        shown (:shown s)
        non-root (filter (fn [[_ pos]] (some? (:opened-by pos))) shown)
        lanes    (group-by (fn [[_ pos]] (:opened-by pos)) non-root)
        ordered  (sort-by (fn [opener] (card-depth shown opener)) (keys lanes))
        roots    (into {} (filter (fn [[_ pos]] (nil? (:opened-by pos))) shown))]
    (loop [ks ordered, result roots]
      (if (empty? ks)
        (swap! state assoc :shown result)
        (let [opener     (first ks)
              cards      (get lanes opener)
              opener-pos (get result opener)
              opener-w   (or (get-in s [:width-by-id opener]) 0)
              ordered-cs (sort-by (fn [[_ pos]] (:y pos)) cards)
              fallback-y (or (:y opener-pos) 0)
              [placed _]
              (reduce (fn [[acc y] [id pos]]
                        (let [h     (card-height-est id)
                              new-x (+ (:x opener-pos) opener-w CARD-COL-GAP)]
                          [(assoc acc id (assoc pos :x new-x :y y))
                           (+ y h CARD-STACK-GAP)]))
                      [{} fallback-y]
                      ordered-cs)]
          (recur (rest ks) (merge result placed)))))))

;; --- Camera tween ---------------------------------------------------------

(defn- bbox-of-shown []
  (let [s @state
        rects (for [[id pos] (:shown s)
                    :let [w (or (get-in s [:width-by-id id]) 320)
                          h (card-height-est id)]]
                [(:x pos) (:y pos) (+ (:x pos) w) (+ (:y pos) h)])]
    (when (seq rects)
      (let [x1 (apply min (map #(nth % 0) rects))
            y1 (apply min (map #(nth % 1) rects))
            x2 (apply max (map #(nth % 2) rects))
            y2 (apply max (map #(nth % 3) rects))]
        {:cx (/ (+ x1 x2) 2) :cy (/ (+ y1 y2) 2)
         :w  (- x2 x1)        :h  (- y2 y1)}))))

(defn- ease-out-cubic [t] (- 1 (Math/pow (- 1 t) 3)))

(defonce ^:private anim-token (atom 0))

(defn animate-to!
  [tx ty tz duration-ms]
  (let [s  @state
        sx (:pan-x s) sy (:pan-y s) sz (:zoom s)
        t0 (.now js/performance)
        token (swap! anim-token inc)]
    (letfn [(step [_]
              (when (= token @anim-token)
                (let [t (min 1 (/ (- (.now js/performance) t0) duration-ms))
                      e (ease-out-cubic t)]
                  (swap! state assoc
                         :pan-x (+ sx (* e (- tx sx)))
                         :pan-y (+ sy (* e (- ty sy)))
                         :zoom  (+ sz (* e (- tz sz))))
                  (when (< t 1) (js/requestAnimationFrame step)))))]
      (js/requestAnimationFrame step))))

(defn animate-to-fit!
  ([] (animate-to-fit! 380))
  ([duration-ms]
   (when-let [bb (bbox-of-shown)]
     (let [vw (.-innerWidth js/window)
           vh (.-innerHeight js/window)
           pad 100
           tz (-> (min (/ (- vw (* 2 pad)) (max 1 (:w bb)))
                       (/ (- vh (* 2 pad)) (max 1 (:h bb)))
                       1.0)
                  (max 0.2))
           tx (- (* (:cx bb) tz))
           ty (- (* (:cy bb) tz))]
       (animate-to! tx ty tz duration-ms)))))

(defn- bbox-of-node [fn-id]
  (let [s @state
        pos (get-in s [:shown fn-id])
        w   (or (get-in s [:width-by-id fn-id]) 320)
        h   (card-height-est fn-id)]
    (when pos
      {:cx (+ (:x pos) (/ w 2))
       :cy (+ (:y pos) (/ h 2))
       :w  w
       :h  h
       :top (:y pos)
       :bot (+ (:y pos) h)})))

(defn- viewport-center-world-y []
  (let [{:keys [pan-y zoom]} @state]
    (if (and zoom (not (zero? zoom)))
      (/ (- pan-y) zoom)
      0)))

(defn client-y->world-y
  "Convert a DOM clientY into the world-Y coordinate it maps to under the
   current pan/zoom. See canvas.cljs for the transform: .canvas is pinned
   at left/top 50% with origin 0,0, so screen_y = vh/2 + pan_y + world_y*zoom."
  [client-y]
  (let [{:keys [pan-y zoom]} @state
        vh (.-innerHeight js/window)]
    (if (and zoom (not (zero? zoom)))
      (/ (- client-y (/ vh 2) pan-y) zoom)
      0)))

(defn animate-to-node!
  "Solo-mode fit. Fits the node on WIDTH (capped at 1.0 so text stays
   naturally readable — long function bodies used to zoom out so far
   they were unreadable). If the node's scaled height still exceeds
   the viewport, vertically anchor on `anchor-y` (a world-Y, typically
   the user's click point) clamped to the node's own top/bottom so we
   never show whitespace around the card. Short nodes just center."
  ([fn-id] (animate-to-node! fn-id 380 nil))
  ([fn-id duration-ms] (animate-to-node! fn-id duration-ms nil))
  ([fn-id duration-ms anchor-y]
   (when-let [bb (bbox-of-node fn-id)]
     (let [vw       (.-innerWidth js/window)
           vh       (.-innerHeight js/window)
           pad      40
           ;; Fit on width so card fills most of the viewport. Cap at 2.0
           ;; so a narrow card doesn't blow up to wall-of-text on a wide
           ;; display, but the text scales well past the 1.0 "natural size"
           ;; baseline — source font is small for compactness, not display.
           tz       (-> (/ (- vw (* 2 pad)) (max 1 (:w bb)))
                        (min 2.0)
                        (max 0.2))
           scaled-h (* (:h bb) tz)
           ;; World-y half-viewport at target zoom.
           half-vh  (/ vh (* 2 tz))
           center-y (if (<= scaled-h vh)
                      (:cy bb)
                      (let [raw (or anchor-y (+ (:top bb) half-vh))]
                        (-> raw
                            (max (+ (:top bb) half-vh))
                            (min (- (:bot bb) half-vh)))))
           tx       (- (* (:cx bb) tz))
           ty       (- (* center-y tz))]
       (animate-to! tx ty tz duration-ms)))))

(defn refit!
  "Re-run the camera fit according to the current :fit-mode. Call this
   after any state change that affects what should be framed — layout
   (reflow, resize), the shown set (spawn/dismiss), or :focused in
   solo mode. Pan/zoom mutations are manual overrides and deliberately
   do NOT call refit! — the next real layout change will reassert the
   mode's invariant.

   `anchor-y` (optional world-Y) biases the solo-mode vertical framing on
   tall nodes. If omitted, we reuse the current viewport-center world-Y
   so passive refits (height measurements, collapse/expand) keep the
   user near where they were looking instead of snapping to the top.

   :all  → fit everything
   :solo → fit :focused if it's on the canvas; fall through to :all
           (so dismissing the solo target to nothing doesn't leave
            the camera stranded)"
  ([] (refit! nil))
  ([anchor-y]
   (let [s @state]
     (case (:fit-mode s)
       :solo (let [f (:focused s)
                   a (or anchor-y (viewport-center-world-y))]
               (if (and f (contains? (:shown s) f))
                 (animate-to-node! f 380 a)
                 (animate-to-fit!)))
       :all  (animate-to-fit!)
       nil))))

;; --- Navigation -----------------------------------------------------------

(defn focus!
  "Mark `fn-id` as the current you-are-here. Trail pushes the previously
   focused box; clicking a node already in the trail jumps back and truncates.
   In :solo mode, focusing a different node re-fits the camera onto it — that's
   how 'single-tap in solo mode zooms to the tapped node' is implemented.

   `anchor-y` (optional world-Y) biases solo-mode vertical framing on tall
   nodes — see refit!."
  ([fn-id] (focus! fn-id nil))
  ([fn-id anchor-y]
   (when (and fn-id (contains? (:shown @state) fn-id))
     (let [{:keys [focused trail]} @state
           in-trail-idx (first (keep-indexed (fn [i id] (when (= id fn-id) i)) trail))]
       (swap! state assoc
              :focused fn-id
              :trail   (cond
                         in-trail-idx (vec (subvec trail 0 in-trail-idx))
                         (and focused (not= focused fn-id)) (conj trail focused)
                         :else trail))
       (refit! anchor-y)))))

(defn spawn-call!
  "Reveal `to-id` in a new box anchored to the right of `from-id`. If the
   callee is already shown somewhere, we share that existing box — one
   function = one box, regardless of how many call-sites point at it."
  [from-id to-id]
  (let [s @state]
    (when (and from-id to-id (not= from-id to-id))
      (when-not (contains? (:shown s) to-id)
        (let [from-pos (get-in s [:shown from-id])
              from-w   (or (get-in s [:width-by-id from-id]) 320)
              x (+ (:x from-pos) from-w CARD-COL-GAP)
              y (next-y-below-siblings (:shown s) from-id (:y from-pos))]
          (swap! state assoc-in [:shown to-id]
                 {:x x :y y :opened-by from-id :root? false})))
      (focus! to-id)
      (reflow!)
      (refit!))))

(defn dismiss!
  "Remove `fn-id` and every box it transitively spawned. Focus jumps back
   to the opener of the dismissed box (or any surviving box)."
  [fn-id]
  (let [s @state
        {:keys [shown focused trail]} s]
    (when (contains? shown fn-id)
      (let [doomed (loop [acc #{fn-id}, frontier [fn-id]]
                     (if (empty? frontier)
                       acc
                       (let [next-frontier
                             (->> shown
                                  (keep (fn [[id pos]]
                                          (when (and (contains? (set frontier)
                                                                (:opened-by pos))
                                                     (not (contains? acc id)))
                                            id))))]
                         (recur (into acc next-frontier)
                                (vec next-frontier)))))
            new-shown (into {} (remove (fn [[id _]] (contains? doomed id))) shown)
            new-focused (if (contains? doomed focused)
                          (let [opener (:opened-by (get shown fn-id))]
                            (if (contains? new-shown opener)
                              opener
                              (first (keys new-shown))))
                          focused)]
        (swap! state assoc
               :shown   new-shown
               :focused new-focused
               :trail   (vec (remove doomed trail)))
        (reflow!)
        (refit!)))))

(defn move-card! [fn-id dx dy origin-x origin-y]
  (swap! state update-in [:shown fn-id]
         (fn [pos] (assoc pos :x (+ origin-x dx) :y (+ origin-y dy)))))

(defn toggle-nested!
  "Flip the Xcode-style disclosure on a nested function. Collapsed nesteds
   render as `fn(...) { … }` pills; expanded ones render their body inline
   with their own children still starting collapsed. The card's size changes,
   so we refit according to the current :fit-mode."
  [nested-id]
  (when nested-id
    (swap! state update :expanded-nested
           (fn [s] (if (contains? s nested-id)
                     (disj s nested-id)
                     (conj (or s #{}) nested-id))))
    (reflow!)
    (refit!)))

(defn double-tap-node!
  "Toggle between :all (fit whole graph) and :solo (fit just the focused
   node). Entering :solo uses the tapped card as the new focused + fit
   target. Exiting to :all frames the whole graph. The mode persists —
   subsequent layout changes, toggle-nested, and even single-tap on a
   different card re-fit according to the active mode.

   `anchor-y` (optional world-Y) biases solo-mode vertical framing on tall
   nodes — see refit!."
  ([fn-id] (double-tap-node! fn-id nil))
  ([fn-id anchor-y]
   (when (and fn-id (contains? (:shown @state) fn-id))
     (if (= :solo (:fit-mode @state))
       (do (swap! state assoc :fit-mode :all)
           (refit!))
       (do (swap! state assoc :fit-mode :solo)
           ;; focus! also calls refit! — which in :solo frames fn-id.
           (focus! fn-id anchor-y))))))

;; --- Init -----------------------------------------------------------------

(defn init-graph! [graph]
  (let [nodes    (:nodes graph)
        dup-ids  (->> nodes (map :id) frequencies (filter #(> (val %) 1)) (map key))
        _        (when (seq dup-ids)
                   (js/console.warn "alon: duplicate node ids — graph will be ambiguous"
                                    (clj->js (vec dup-ids))))
        by-id    (into {} (map (juxt :id identity)) nodes)
        children-of (reduce (fn [m n]
                              (if-let [p (:parentId n)]
                                (update m p (fnil conj []) (:id n))
                                m))
                            {} nodes)
        lifted   (vec (rewrite-edges by-id (:edges graph)))
        edges-by-from (reduce (fn [m e] (update m (:from e) (fnil conj []) e))
                              {} lifted)
        widths   (into {} (map (fn [n] [(:id n) (analytical-width n)])) nodes)
        entry    (:root graph)
        ;; Entry = first renderable function in the entry file. Fall back to
        ;; the first renderable anywhere if the entry has none.
        start-id (or (some #(when (and (renderable? by-id (:id %))
                                       (= (:file %) entry)
                                       (= (:type %) "function"))
                              (:id %))
                           nodes)
                     (some #(when (renderable? by-id (:id %)) (:id %)) nodes))]
    (swap! state assoc
           :graph           graph
           :by-id           by-id
           :children-of     children-of
           :edges-by-from   edges-by-from
           :width-by-id     widths
           :height-by-id    {}
           :shown           (if start-id {start-id {:x 0 :y 0 :opened-by nil :root? true}} {})
           :expanded-nested #{}
           :fit-mode        :all
           :focused         start-id
           :trail           []
           :pan-x           0
           :pan-y           0
           :zoom            1)))

(defn set-pan! [x y]
  (swap! state assoc :pan-x x :pan-y y))

(defn set-zoom! [z px py]
  (let [{:keys [pan-x pan-y zoom]} @state
        ratio  (/ z zoom)
        new-px (+ (* px (- 1 ratio)) (* ratio pan-x))
        new-py (+ (* py (- 1 ratio)) (* ratio pan-y))]
    (swap! state assoc :zoom z :pan-x new-px :pan-y new-py)))
