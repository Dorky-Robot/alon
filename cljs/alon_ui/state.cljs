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
;; Code organization is pure-core / impure-rim:
;;
;;   • Pure helpers (renderable?, rewrite-edges, reflow-shown, focus, dismiss,
;;     spawn-call, toggle-nested, double-tap-node, set-measured-height) take
;;     a state map and return the next state map. They compose via `->`.
;;
;;   • Effect wrappers (focus!, dismiss!, spawn-call!, …) are thin shells:
;;     `(swap! state pure-fn args...)`, then call refit! if the identity of
;;     the state ref actually moved. Exactly one atom write per user action.
;;
;; Camera animation and DOM measurement live at the effect boundary too —
;; rAF ticks and `.-innerWidth` reads are genuinely stateful.

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

(defn- height-of
  "Measured height, or CARD-FALLBACK-H until the DOM reports one."
  [height-by-id fn-id]
  (or (get height-by-id fn-id) CARD-FALLBACK-H))

(defn card-height-est
  "Public accessor for file-card geometry. Reads from the current state."
  [fn-id]
  (height-of (:height-by-id @state) fn-id))

(defn- card-depth [shown fn-id]
  (loop [id fn-id, d 0]
    (if-let [opener (:opened-by (get shown id))]
      (recur opener (inc d))
      d)))

(defn- next-y-below-siblings
  "Lowest free y in the lane spawned by `opener-id`. New children of the
   same opener cascade below previously-spawned ones."
  [shown height-by-id opener-id fallback-y]
  (->> shown
       (filter (fn [[_ pos]] (= (:opened-by pos) opener-id)))
       (map (fn [[id pos]] (+ (:y pos) (height-of height-by-id id) CARD-STACK-GAP)))
       (reduce max fallback-y)))

(defn- place-lane
  "Pure: stack a lane of cards vertically below their opener's position.
   Returns a map of {id pos} for just the placed cards."
  [opener-pos opener-w height-by-id lane-cards]
  (let [new-x   (+ (:x opener-pos) opener-w CARD-COL-GAP)
        ordered (sort-by (fn [[_ pos]] (:y pos)) lane-cards)]
    (first
     (reduce (fn [[acc y] [id pos]]
               [(assoc acc id (assoc pos :x new-x :y y))
                (+ y (height-of height-by-id id) CARD-STACK-GAP)])
             [{} (or (:y opener-pos) 0)]
             ordered))))

(defn- reflow-shown
  "Pure: re-stack each lane below its opener so taller cards push siblings
   down. Root boxes keep their position. Openers are processed outermost-
   first so each lane sees its opener's already-updated position."
  [shown width-by-id height-by-id]
  (let [non-root        (filter (fn [[_ pos]] (some? (:opened-by pos))) shown)
        lanes           (group-by (fn [[_ pos]] (:opened-by pos)) non-root)
        ordered-openers (sort-by #(card-depth shown %) (keys lanes))
        roots           (into {} (filter (fn [[_ pos]] (nil? (:opened-by pos))) shown))]
    (reduce
     (fn [result opener]
       (let [opener-pos (get result opener)
             opener-w   (or (get width-by-id opener) 0)
             placed     (place-lane opener-pos opener-w height-by-id
                                    (get lanes opener))]
         (merge result placed)))
     roots
     ordered-openers)))

(defn- reflow
  "Apply reflow-shown to the :shown map in `s`."
  [s]
  (assoc s :shown (reflow-shown (:shown s) (:width-by-id s) (:height-by-id s))))

;; --- Camera tween ---------------------------------------------------------

(defn- bbox-of-shown [s]
  (let [rects (for [[id pos] (:shown s)
                    :let [w (or (get-in s [:width-by-id id]) 320)
                          h (height-of (:height-by-id s) id)]]
                [(:x pos) (:y pos) (+ (:x pos) w) (+ (:y pos) h)])]
    (when (seq rects)
      (let [x1 (apply min (map #(nth % 0) rects))
            y1 (apply min (map #(nth % 1) rects))
            x2 (apply max (map #(nth % 2) rects))
            y2 (apply max (map #(nth % 3) rects))]
        {:cx (/ (+ x1 x2) 2) :cy (/ (+ y1 y2) 2)
         :w  (- x2 x1)        :h  (- y2 y1)}))))

(defn- bbox-of-node [s fn-id]
  (when-let [pos (get-in s [:shown fn-id])]
    (let [w (or (get-in s [:width-by-id fn-id]) 320)
          h (height-of (:height-by-id s) fn-id)]
      {:cx (+ (:x pos) (/ w 2))
       :cy (+ (:y pos) (/ h 2))
       :w  w :h  h
       :top (:y pos)
       :bot (+ (:y pos) h)})))

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
   (when-let [bb (bbox-of-shown @state)]
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

(defn- viewport-center-world-y [s]
  (let [{:keys [pan-y zoom]} s]
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
  "Solo-mode fit. Fits the node on WIDTH (capped at 2.0 so a narrow card
   doesn't blow up on a wide display, but text can scale well past the
   1.0 baseline). If the node's scaled height still exceeds the viewport,
   vertically anchor on `anchor-y` (a world-Y, typically the user's click
   point) clamped to the node's own top/bottom so we never show whitespace
   around the card. Short nodes just center."
  ([fn-id] (animate-to-node! fn-id 380 nil))
  ([fn-id duration-ms] (animate-to-node! fn-id duration-ms nil))
  ([fn-id duration-ms anchor-y]
   (when-let [bb (bbox-of-node @state fn-id)]
     (let [vw       (.-innerWidth js/window)
           vh       (.-innerHeight js/window)
           pad      40
           tz       (-> (/ (- vw (* 2 pad)) (max 1 (:w bb)))
                        (min 2.0)
                        (max 0.2))
           scaled-h (* (:h bb) tz)
           half-vh  (/ vh (* 2 tz))
           center-y (if (<= scaled-h vh)
                      (:cy bb)
                      (-> (or anchor-y (+ (:top bb) half-vh))
                          (max (+ (:top bb) half-vh))
                          (min (- (:bot bb) half-vh))))
           tx       (- (* (:cx bb) tz))
           ty       (- (* center-y tz))]
       (animate-to! tx ty tz duration-ms)))))

(defn refit!
  "Re-run the camera fit according to the current :fit-mode. Call after
   any state change that affects what should be framed — layout (reflow,
   resize), the shown set (spawn/dismiss), or :focused in solo mode.
   Pan/zoom mutations are manual overrides and deliberately do NOT call
   refit! — the next real layout change will reassert the mode's invariant.

   `anchor-y` (optional world-Y) biases solo-mode vertical framing on tall
   nodes. If omitted, we reuse the current viewport-center world-Y so
   passive refits (height measurements, collapse/expand) keep the user
   near where they were looking instead of snapping to the top."
  ([] (refit! nil))
  ([anchor-y]
   (let [s @state]
     (case (:fit-mode s)
       :solo (let [f (:focused s)
                   a (or anchor-y (viewport-center-world-y s))]
               (if (and f (contains? (:shown s) f))
                 (animate-to-node! f 380 a)
                 (animate-to-fit!)))
       :all  (animate-to-fit!)
       nil))))

;; --- Pure state transitions ----------------------------------------------
;;
;; Each transition is a pure (state, args) -> state fn. Callers compose
;; them with `->`. The matching imperative `foo!` wrappers below do the
;; swap and then trigger any side effect (refit!) that the transition
;; implies, only if the state actually changed.

(defn- focus
  "Make `fn-id` the focused card and push the previously focused one onto
   the trail (or truncate the trail if we're jumping back). No-op if the
   target isn't on the canvas."
  [s fn-id]
  (if-not (and fn-id (contains? (:shown s) fn-id))
    s
    (let [{:keys [focused trail]} s
          in-trail-idx (first (keep-indexed (fn [i id] (when (= id fn-id) i)) trail))]
      (assoc s
             :focused fn-id
             :trail   (cond
                        in-trail-idx                       (vec (subvec trail 0 in-trail-idx))
                        (and focused (not= focused fn-id)) (conj trail focused)
                        :else                              trail)))))

(defn- spawn-call
  "Add `to-id` as a box spawned by `from-id` (or share the existing box if
   it's already on the canvas) and focus it. Also reflows so siblings
   push down."
  [s from-id to-id]
  (let [shown (:shown s)]
    (if (or (nil? from-id) (nil? to-id) (= from-id to-id)
            (not (contains? shown from-id)))
      s
      (-> (if (contains? shown to-id)
            s
            (let [from-pos (get shown from-id)
                  from-w   (or (get-in s [:width-by-id from-id]) 320)
                  x (+ (:x from-pos) from-w CARD-COL-GAP)
                  y (next-y-below-siblings shown (:height-by-id s)
                                           from-id (:y from-pos))]
              (assoc-in s [:shown to-id]
                        {:x x :y y :opened-by from-id :root? false})))
          (focus to-id)
          reflow))))

(defn- doomed-from
  "Pure: set of shown ids transitively spawned by `root-id` (inclusive)."
  [shown root-id]
  (loop [acc #{root-id} frontier #{root-id}]
    (if (empty? frontier)
      acc
      (let [kids (into #{}
                       (keep (fn [[id pos]]
                               (when (and (contains? frontier (:opened-by pos))
                                          (not (contains? acc id)))
                                 id)))
                       shown)]
        (recur (into acc kids) kids)))))

(defn- dismiss
  "Remove `fn-id` and everything it transitively spawned. Focus jumps back
   to the opener of the dismissed box (or any surviving box)."
  [s fn-id]
  (let [{:keys [shown focused trail]} s]
    (if-not (contains? shown fn-id)
      s
      (let [doomed      (doomed-from shown fn-id)
            new-shown   (into {} (remove (comp doomed key)) shown)
            new-focused (if (contains? doomed focused)
                          (let [opener (:opened-by (get shown fn-id))]
                            (if (contains? new-shown opener)
                              opener
                              (first (keys new-shown))))
                          focused)]
        (-> s
            (assoc :shown   new-shown
                   :focused new-focused
                   :trail   (vec (remove doomed trail)))
            reflow)))))

(defn- toggle-nested
  "Flip the Xcode-style disclosure on a nested function."
  [s nested-id]
  (if-not nested-id
    s
    (-> s
        (update :expanded-nested
                (fn [xs]
                  (let [xs (or xs #{})]
                    (if (contains? xs nested-id)
                      (disj xs nested-id)
                      (conj xs nested-id)))))
        reflow)))

(defn- double-tap-node
  "Toggle between :all and :solo. Entering :solo focuses `fn-id`; exiting
   leaves :focused alone (so re-entering :solo later remembers what you
   were looking at)."
  [s fn-id]
  (cond
    (not (contains? (:shown s) fn-id)) s
    (= :solo (:fit-mode s))             (assoc s :fit-mode :all)
    :else (-> s (assoc :fit-mode :solo) (focus fn-id))))

(defn- set-measured-height
  "Record a DOM-measured height; reflow since the lane may need re-stacking."
  [s fn-id h]
  (let [cur (get-in s [:height-by-id fn-id])]
    (if (and h (or (nil? cur) (not= (int cur) (int h))))
      (-> s
          (assoc-in [:height-by-id fn-id] h)
          reflow)
      s)))

;; --- Effect wrappers (swap once; refit if anything moved) ----------------

(defn- change!
  "Apply a pure transition to the state atom. If it changes the map's
   identity, also call refit! with the given anchor. Returns true iff
   the state was updated — handy in tests."
  [tx anchor-y]
  (let [before @state
        after  (swap! state tx)]
    (when-not (identical? before after)
      (refit! anchor-y)
      true)))

(defn focus!
  ([fn-id] (focus! fn-id nil))
  ([fn-id anchor-y]
   (change! #(focus % fn-id) anchor-y)))

(defn spawn-call! [from-id to-id]
  (change! #(spawn-call % from-id to-id) nil))

(defn dismiss! [fn-id]
  (change! #(dismiss % fn-id) nil))

(defn toggle-nested! [nested-id]
  (change! #(toggle-nested % nested-id) nil))

(defn double-tap-node!
  ([fn-id] (double-tap-node! fn-id nil))
  ([fn-id anchor-y]
   (change! #(double-tap-node % fn-id) anchor-y)))

(defn set-measured-height! [fn-id h]
  (change! #(set-measured-height % fn-id h) nil))

(defn move-card! [fn-id dx dy origin-x origin-y]
  (swap! state update-in [:shown fn-id]
         (fn [pos] (assoc pos :x (+ origin-x dx) :y (+ origin-y dy)))))

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
