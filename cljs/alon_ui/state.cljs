(ns alon-ui.state
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; Schema:
;;   {:graph         {:nodes [...] :edges [...] :root "..."}
;;    :by-id         {node-id node}                 ; nodes carry :parentId :start :end
;;    :by-file       {file-path [node ...]}         ; TOP-LEVEL nodes only, trace-order
;;    :children-of   {parent-id [child-id ...]}     ; declaration order (by :start)
;;    :width-by-file {file-path px}                 ; analytical width (fits widest line)
;;    :edges-by-from {node-id [edge ...]}
;;    :shown         {file-path {:x N :y N :root? bool}}
;;    :expanded      #{node-id}
;;    :pan-x N :pan-y N :zoom N}

(def ^:private CHAR-W      6.6)   ; approx px per char at 11px ui-monospace
(def ^:private SOURCE-PAD  28)    ; left + right padding on .source
(def ^:private CONTAINER-PAD 10)  ; mirror file-card's CONTAINER-PAD
(def ^:private MIN-WIDTH   280)
(def ^:private MAX-WIDTH   1100)  ; sanity cap; very long lines still scroll

(defn- max-line-len [s]
  (if (string? s)
    (transduce (map count) max 0 (str/split s #"\n"))
    0))

(defn- node-depth [node by-id]
  (loop [n node, d 0]
    (if-let [pid (:parentId n)]
      (recur (get by-id pid) (inc d))
      d)))

(defn- file-width
  "Width that fits each leaf-source line at its rendered depth — every
   container ancestor consumes 2×CONTAINER-PAD of horizontal room."
  [nodes by-id]
  (let [needs (for [n nodes
                    :when (string? (:source n))
                    :let [longest (max-line-len (:source n))
                          d       (node-depth n by-id)]]
                (+ (* longest CHAR-W)
                   SOURCE-PAD
                   (* 2 d CONTAINER-PAD)))]
    (-> (reduce max 0 needs)
        (Math/ceil)
        (max MIN-WIDTH)
        (min MAX-WIDTH)
        int)))

(declare focus!)

(defonce state
  (r/atom {:graph          nil
           :by-id          {}
           :by-file        {}
           :children-of    {}
           :width-by-file  {}
           :height-by-file {}
           :edges-by-from  {}
           :shown          {}
           :expanded       #{}
           :focused        nil
           :trail          []
           :pan-x          0
           :pan-y          0
           :zoom           1}))

(defn- compute-trace-order
  "DFS-visit index for each node, starting from nodes with no incoming edges
   (call-tree roots), then sweeping up any orphans."
  [graph]
  (let [adj      (reduce (fn [m {:keys [from to]}]
                           (update m from (fnil conj []) to))
                         {} (:edges graph))
        incoming (into #{} (map :to) (:edges graph))
        roots    (->> (:nodes graph)
                      (remove #(contains? incoming (:id %)))
                      (map :id))
        order    (volatile! {})
        counter  (volatile! 0)]
    (letfn [(visit [id]
              (when-not (contains? @order id)
                (vswap! order assoc id (vswap! counter inc))
                (doseq [nxt (get adj id)]
                  (visit nxt))))]
      (doseq [r roots] (visit r))
      (doseq [n (:nodes graph)] (visit (:id n))))
    @order))

(defn- barycenter-order
  "Reorder ids in `seed` so within-scope edges have minimal vertical
   tangle. Each iteration assigns every node a barycenter — the mean
   index of its in-scope neighbors — and re-sorts. External edges
   (touching a node not in this scope) are ignored: those endpoints
   are out of our control here.

   Ties break by current index, which keeps the sort stable and
   prevents the iteration from oscillating between equivalent layouts."
  [seed edges]
  (let [in-scope (set seed)
        adj (reduce (fn [m {:keys [from to]}]
                      (if (and (in-scope from) (in-scope to) (not= from to))
                        (-> m
                            (update from (fnil conj #{}) to)
                            (update to   (fnil conj #{}) from))
                        m))
                    {} edges)]
    (loop [order (vec seed), iter 0]
      (if (>= iter 10)
        order
        (let [pos        (into {} (map-indexed (fn [i id] [id i])) order)
              barycenter (fn [id]
                           (let [neigh (get adj id)]
                             (if (seq neigh)
                               (/ (reduce + (map pos neigh)) (count neigh))
                               (get pos id))))
              next-order (vec (sort-by (juxt barycenter pos) order))]
          (if (= next-order order)
            order
            (recur next-order (inc iter))))))))

(defn init-graph! [graph]
  (let [nodes    (:nodes graph)
        by-id    (into {} (map (juxt :id identity)) nodes)
        trace    (compute-trace-order graph)
        edges    (:edges graph)
        ;; Group children under their parentId, sorted by :start so they
        ;; appear in source order. Top-level nodes (parentId nil) are kept
        ;; separately and ordered by trace, then polished by barycenter to
        ;; minimize edge tangle.
        children-of (->> nodes
                         (filter :parentId)
                         (group-by :parentId)
                         (into {}
                               (map (fn [[pid ns]]
                                      [pid (mapv :id (sort-by :start ns))]))))
        top-by-file (->> nodes
                         (remove :parentId)
                         (group-by :file)
                         (into {}
                               (map (fn [[f ns]]
                                      (let [trace-ordered (sort-by #(get trace (:id %) 1e9) ns)
                                            by-id-local   (into {} (map (juxt :id identity)) trace-ordered)
                                            optimized     (barycenter-order (mapv :id trace-ordered) edges)]
                                        [f (mapv by-id-local optimized)])))))
        ;; Width considers ALL nodes in the file (including nested), so the
        ;; card stays the same width whether children are expanded or not.
        width-by-file (->> nodes
                           (group-by :file)
                           (into {}
                                 (map (fn [[f ns]] [f (file-width ns by-id)]))))
        edges-by-from (reduce (fn [m e] (update m (:from e) (fnil conj []) e))
                              {} (:edges graph))
        entry    (:root graph)
        start-file (or (when (contains? top-by-file entry) entry)
                       (some-> (first nodes) :file))]
    (swap! state assoc
           :graph         graph
           :by-id         by-id
           :by-file       top-by-file
           :children-of   children-of
           :width-by-file width-by-file
           :edges-by-from edges-by-from
           :shown         (if start-file
                            {start-file {:x 0 :y 0 :root? true}}
                            {})
           :expanded      #{}
           :focused       nil
           :trail         [])
    ;; Auto-focus the first top-level function in the entry file so the
    ;; canvas boots in navigation mode instead of an empty "pick a row"
    ;; state. Its source + call-site arrows come in for free.
    (let [start-focus (or (some #(when (= (:type %) "function") (:id %))
                                (get top-by-file start-file))
                          (:id (first (get top-by-file start-file))))]
      (when start-focus (focus! start-focus)))))

(defn move-card! [file-path dx dy origin-x origin-y]
  (swap! state update-in [:shown file-path]
         (fn [s]
           (-> s
               (assoc :x (+ origin-x dx))
               (assoc :y (+ origin-y dy))))))

(defn toggle-expanded! [node-id]
  (swap! state update :expanded
         (fn [e] (if (contains? e node-id) (disj e node-id) (conj e node-id)))))

(defn- ancestor-chain
  "Walk `node-id` and every :parentId up to the top, inclusive."
  [by-id node-id]
  (take-while some? (iterate #(:parentId (get by-id %)) node-id)))

;; --- Layout ---------------------------------------------------------------
;;
;; Three lanes around the focused file:
;;   left   = files holding callers of focused
;;   center = focused's own file
;;   right  = files holding callees of focused
;; Cards are laid out as columns, each column stack-centered vertically.
;; This replaces the old polar fan, which scattered cards around a circle.

(def ^:private CARD-COL-GAP      120)  ; horizontal gap between columns
(def ^:private CARD-STACK-GAP    40)   ; constant vertical gap between siblings
(def ^:private CARD-FALLBACK-H   120)  ; pre-measurement placeholder height

(defn- card-height-est
  "Real rendered height of a file card, measured from the DOM. Until the
   card mounts we use a small fallback — the first render places cards at
   estimated positions, then measurement kicks in and reflow snaps them
   to tight, constant-gap rows."
  [file]
  (or (get-in @state [:height-by-file file]) CARD-FALLBACK-H))

(declare reflow! animate-to-fit!)

(defn set-measured-height!
  "Record a card's true offsetHeight. If the value actually changed we
   reflow (siblings may need to shift) and re-fit the viewport. Identical
   re-measurements are no-ops — that's what breaks the measure/reflow/
   measure feedback loop."
  [file h]
  (let [cur (get-in @state [:height-by-file file])]
    (when (and h (or (nil? cur) (not= (int cur) (int h))))
      (swap! state assoc-in [:height-by-file file] h)
      (reflow!)
      (animate-to-fit!))))

(defn- neighbor-files
  "Files holding nodes connected to `node-id` via edges in `direction`
   (:out → callees, :in → callers). Excludes node-id's own file."
  [node-id direction]
  (let [s @state
        {:keys [by-id graph]} s
        home (some-> (get by-id node-id) :file)
        pick (case direction
               :out (fn [{:keys [from to]}] (when (= from node-id) to))
               :in  (fn [{:keys [from to]}] (when (= to node-id)   from)))]
    (->> (:edges graph)
         (keep pick)
         (keep #(:file (get by-id %)))
         distinct
         (remove #(= % home))
         vec)))

(defn- next-y-below-siblings
  "Lowest free y in the lane owned by (`opener-file`, `direction`). Cards
   on different sides of the same opener don't share a stack — siblings
   only cascade below others added on the same side."
  [shown opener-file direction fallback-y]
  (let [sibs (filter (fn [[_ pos]]
                       (and (= (:opened-by pos) opener-file)
                            (= (:direction pos) direction)))
                     shown)]
    (if (empty? sibs)
      fallback-y
      (apply max (map (fn [[f pos]]
                        (+ (:y pos) (card-height-est f) CARD-STACK-GAP))
                      sibs)))))

(defn- add-card!
  "Bring `file` onto the canvas if it isn't already there. Position is
   anchored to its `opener-file` and lane direction (:right for callees,
   :left for callers). Same y as opener to start, snapping below previously-
   added siblings on the same side. Stamps :opened-by so cascade-dismissal
   can find descendants later."
  ([file opener-file] (add-card! file opener-file :right))
  ([file opener-file direction]
   (let [s @state]
     (when (and file (not (contains? (:shown s) file)))
       (let [opener-pos (when opener-file (get-in s [:shown opener-file]))
             opener-w   (or (when opener-file
                              (get-in s [:width-by-file opener-file]))
                            0)
             new-w      (or (get-in s [:width-by-file file]) 320)
             x (cond
                 (nil? opener-pos) 0
                 (= direction :left)
                 (- (:x opener-pos) CARD-COL-GAP new-w)
                 :else
                 (+ (:x opener-pos) opener-w CARD-COL-GAP))
             y (next-y-below-siblings (:shown s)
                                      opener-file direction
                                      (or (:y opener-pos) 0))]
         (swap! state assoc-in [:shown file]
                {:x x :y y
                 :opened-by opener-file
                 :direction direction
                 :root?     (nil? opener-file)}))))))

(defn- card-depth
  "Length of the opened-by chain from `file` back to a root. Used by
   reflow! to process lanes outermost-first so a parent's position is
   already up-to-date by the time we restack its children."
  [shown file]
  (loop [f file, d 0]
    (if-let [opener (:opened-by (get shown f))]
      (recur opener (inc d))
      d)))

(defn reflow!
  "Re-stack every (opener, direction) lane in current top-down order. A
   card that grew taller (because the user expanded a row in it) pushes
   its lane-siblings down so they don't overlap. Root cards stay put.

   We sort lanes by opener depth so each lane sees its opener's already-
   reflowed position, not the stale one. Within a lane, cards keep their
   current y order (stable sort) — honors any manual drag."
  []
  (let [s @state
        shown (:shown s)
        non-root (filter (fn [[_ pos]] (some? (:opened-by pos))) shown)
        lanes    (group-by (fn [[_ pos]] [(:opened-by pos) (:direction pos)])
                           non-root)
        ordered-lanes (sort-by (fn [[opener _]] (card-depth shown opener))
                               (keys lanes))
        roots (into {} (filter (fn [[_ pos]] (nil? (:opened-by pos))) shown))]
    (loop [ks      ordered-lanes
           result  roots]
      (if (empty? ks)
        (swap! state assoc :shown result)
        (let [[opener-file direction] (first ks)
              cards      (get lanes [opener-file direction])
              opener-pos (get result opener-file)
              opener-w   (or (get-in s [:width-by-file opener-file]) 0)
              ordered    (sort-by (fn [[_ pos]] (:y pos)) cards)
              fallback-y (or (:y opener-pos) 0)
              [placed _]
              (reduce
               (fn [[acc y] [f pos]]
                 (let [w   (or (get-in s [:width-by-file f]) 320)
                       h   (card-height-est f)
                       new-x (if (= direction :left)
                               (- (:x opener-pos) CARD-COL-GAP w)
                               (+ (:x opener-pos) opener-w CARD-COL-GAP))]
                   [(assoc acc f (assoc pos :x new-x :y y))
                    (+ y h CARD-STACK-GAP)]))
               [{} fallback-y]
               ordered)]
          (recur (rest ks) (merge result placed)))))))

(defn- bring-in-around!
  "Add the focused node's file (if new) and every callee/caller file of
   the focused node (if new). Callees land in a column to the right
   (downstream), callers to the left (upstream). Existing cards keep their
   position — the user's mental map of where things live is preserved."
  [focused-id prev-focused-id]
  (let [s @state
        {:keys [by-id]} s
        focused-file (some-> (get by-id focused-id) :file)
        prev-file    (some-> (get by-id prev-focused-id) :file)
        ;; If focused's file is new, treat it as opened by where we just
        ;; came from. Direction follows the edge: if focused is a callee
        ;; of something in prev-file → focused goes right; if it calls
        ;; into prev-file → focused goes left. Default right.
        direction-from-prev
        (cond
          (nil? prev-focused-id) :right
          (some #(and (= (:from %) prev-focused-id)
                      (= (:to %) focused-id))
                (get-in s [:graph :edges])) :right
          (some #(and (= (:to %) prev-focused-id)
                      (= (:from %) focused-id))
                (get-in s [:graph :edges])) :left
          :else :right)
        opener (when (and prev-file (not= prev-file focused-file))
                 prev-file)]
    (add-card! focused-file opener direction-from-prev)
    (doseq [f (neighbor-files focused-id :out)]
      (add-card! f focused-file :right))
    (doseq [f (neighbor-files focused-id :in)]
      (add-card! f focused-file :left))))

(declare animate-to-fit!)

(defn dismiss-file!
  "Remove `file` and every card it transitively brought in. If the focused
   row lived in a dismissed file, refocus to the dismissed file's opener
   (or whichever card now sits at the top-left of the canvas)."
  [file]
  (let [s @state
        {:keys [shown by-id focused]} s]
    (when (contains? shown file)
      (let [;; BFS the opened-by graph to find descendants (incl. `file`).
            doomed (loop [acc #{file}, frontier [file]]
                     (if (empty? frontier)
                       acc
                       (let [next-frontier
                             (->> shown
                                  (keep (fn [[f pos]]
                                          (when (and (contains? (set frontier)
                                                                (:opened-by pos))
                                                     (not (contains? acc f)))
                                            f))))]
                         (recur (into acc next-frontier)
                                (vec next-frontier)))))
            new-shown (into {} (remove (fn [[f _]] (contains? doomed f))) shown)
            ;; If we dismissed the focused's home, refocus to the
            ;; dismissed file's opener (a survivor) or the first survivor.
            focused-file (some-> (get by-id focused) :file)
            new-focused
            (if (contains? doomed focused-file)
              (let [opener (:opened-by (get shown file))
                    survivor (or (when (contains? new-shown opener) opener)
                                 (first (keys new-shown)))
                    by-file (:by-file s)]
                (some-> (first (get by-file survivor)) :id))
              focused)]
        (swap! state assoc
               :shown   new-shown
               :focused new-focused
               :trail   (vec (filter #(let [f (some-> (get by-id %) :file)]
                                        (not (contains? doomed f)))
                                     (:trail s))))
        (reflow!)
        (animate-to-fit!)))))

;; --- Camera tween ---------------------------------------------------------
;;
;; After a layout change we ease the canvas pan + zoom so every visible
;; card fits the viewport. The canvas transform is
;;   transform-origin: 0 0; translate(pan-x, pan-y) scale(zoom)
;; positioned at left:50%/top:50%, so for a canvas-coords point (cx, cy)
;; to land at viewport center, pan = -center * zoom.

(defn- bbox-of-shown []
  (let [s @state
        rects (for [[f pos] (:shown s)]
                (let [w (or (get-in s [:width-by-file f]) 320)
                      h (card-height-est f)]
                  [(:x pos) (:y pos)
                   (+ (:x pos) w) (+ (:y pos) h)]))]
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
  "Tween :pan-x :pan-y :zoom toward (tx, ty, tz) over `duration-ms`. A
   monotonic token cancels any in-flight animation when a new one starts,
   so rapid focus changes don't fight each other."
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
  "Pan + zoom so the bounding box of all shown cards fits the viewport
   with a comfortable margin. Caps zoom at 1× — we never enlarge past
   actual size, since reading text at 1.5× looks blurry on hidpi."
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

(defn- ensure-focus-visible! [node-id prev-focused-id]
  (let [s @state
        {:keys [by-id]} s
        node (get by-id node-id)]
    (when node
      (swap! state update :expanded
             (fn [e] (into (or e #{}) (ancestor-chain by-id node-id))))
      (bring-in-around! node-id prev-focused-id)
      (reflow!)
      (animate-to-fit!))))

(defn focus!
  "Navigate to `node-id`.
     - already focused: toggle its expansion (so re-clicking can collapse)
     - in trail: jump back, truncating the trail just before it
     - otherwise: promote, pushing old focused onto trail

   New focuses accumulate cards (Wikipedia-style) — previously visited
   files stay until the user dismisses them. Re-click on focused just
   toggles expansion."
  [node-id]
  (let [{:keys [focused trail]} @state
        prev-focused focused]
    (cond
      (nil? node-id) nil

      (= node-id focused)
      (do (toggle-expanded! node-id)
          (reflow!)
          (animate-to-fit!))

      (some #(= % node-id) trail)
      (let [i (first (keep-indexed (fn [i id] (when (= id node-id) i)) trail))]
        (swap! state assoc
               :focused node-id
               :trail   (vec (subvec trail 0 i)))
        (ensure-focus-visible! node-id prev-focused))

      :else
      (do (swap! state assoc
                 :focused node-id
                 :trail   (if focused (conj trail focused) trail))
          (ensure-focus-visible! node-id prev-focused)))))

(defn set-pan! [x y]
  (swap! state assoc :pan-x x :pan-y y))

(defn set-zoom! [z px py]
  (let [{:keys [pan-x pan-y zoom]} @state
        ratio  (/ z zoom)
        new-px (+ (* px (- 1 ratio)) (* ratio pan-x))
        new-py (+ (* py (- 1 ratio)) (* ratio pan-y))]
    (swap! state assoc :zoom z :pan-x new-px :pan-y new-py)))
