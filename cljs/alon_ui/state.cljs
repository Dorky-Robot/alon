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
;;    :fit-mode      :all | :solo    ; double-tap zoom toggle; any user-initiated
;;                                    ; camera change resets this to :all
;;    :focused       fn-id
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

(declare reflow! animate-to-fit!)

(defn set-measured-height! [fn-id h]
  (let [cur (get-in @state [:height-by-id fn-id])]
    (when (and h (or (nil? cur) (not= (int cur) (int h))))
      (swap! state assoc-in [:height-by-id fn-id] h)
      (reflow!)
      (animate-to-fit!))))

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
       (swap! state assoc :fit-mode :all)
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
       :h  h})))

(defn animate-to-node!
  "Pan/zoom so `fn-id`'s card fills the viewport with padding. Unlike
   animate-to-fit!, this does NOT touch :fit-mode — the caller owns that
   so double-tap can enter :solo without it getting stomped."
  ([fn-id] (animate-to-node! fn-id 380))
  ([fn-id duration-ms]
   (when-let [bb (bbox-of-node fn-id)]
     (let [vw  (.-innerWidth js/window)
           vh  (.-innerHeight js/window)
           pad 80
           tz  (-> (min (/ (- vw (* 2 pad)) (max 1 (:w bb)))
                        (/ (- vh (* 2 pad)) (max 1 (:h bb)))
                        1.5)
                   (max 0.2))
           tx  (- (* (:cx bb) tz))
           ty  (- (* (:cy bb) tz))]
       (animate-to! tx ty tz duration-ms)))))

;; --- Navigation -----------------------------------------------------------

(defn focus!
  "Mark `fn-id` as the current you-are-here. Trail pushes the previously
   focused box; clicking a node already in the trail jumps back and truncates."
  [fn-id]
  (when (and fn-id (contains? (:shown @state) fn-id))
    (let [{:keys [focused trail]} @state
          in-trail-idx (first (keep-indexed (fn [i id] (when (= id fn-id) i)) trail))]
      (swap! state assoc
             :focused fn-id
             :trail   (cond
                        in-trail-idx (vec (subvec trail 0 in-trail-idx))
                        (and focused (not= focused fn-id)) (conj trail focused)
                        :else trail)))))

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
      (animate-to-fit!))))

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
        (animate-to-fit!)))))

(defn move-card! [fn-id dx dy origin-x origin-y]
  (swap! state update-in [:shown fn-id]
         (fn [pos] (assoc pos :x (+ origin-x dx) :y (+ origin-y dy)))))

(defn toggle-nested!
  "Flip the Xcode-style disclosure on a nested function. Collapsed nesteds
   render as `fn(...) { … }` pills; expanded ones render their body inline
   with their own children still starting collapsed."
  [nested-id]
  (when nested-id
    (swap! state update :expanded-nested
           (fn [s] (if (contains? s nested-id)
                     (disj s nested-id)
                     (conj (or s #{}) nested-id))))
    (reflow!)
    (animate-to-fit!)))

(defn double-tap-node!
  "Toggle between fit-to-single-node (:solo) and fit-to-everything (:all).
   First double-tap zooms onto that card; second double-tap anywhere zooms
   back out. Any other user-initiated camera move resets to :all, so the
   next double-tap always enters solo cleanly."
  [fn-id]
  (when (and fn-id (contains? (:shown @state) fn-id))
    (if (= :solo (:fit-mode @state))
      (animate-to-fit!)
      (do
        (swap! state assoc :fit-mode :solo)
        (animate-to-node! fn-id)))))

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
  (swap! state assoc :pan-x x :pan-y y :fit-mode :all))

(defn set-zoom! [z px py]
  (let [{:keys [pan-x pan-y zoom]} @state
        ratio  (/ z zoom)
        new-px (+ (* px (- 1 ratio)) (* ratio pan-x))
        new-py (+ (* py (- 1 ratio)) (* ratio pan-y))]
    (swap! state assoc :zoom z :pan-x new-px :pan-y new-py :fit-mode :all)))
