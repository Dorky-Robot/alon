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

(defonce state
  (r/atom {:graph         nil
           :by-id         {}
           :by-file       {}
           :children-of   {}
           :width-by-file {}
           :edges-by-from {}
           :shown         {}
           :expanded      #{}
           :pan-x         0
           :pan-y         0
           :zoom          1}))

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
           :expanded      #{})))

(defn- node-neighbor-files [node-id]
  (let [{:keys [graph by-id]} @state]
    (into #{}
          (comp
           (keep (fn [{:keys [from to]}]
                   (cond (= from node-id) to
                         (= to node-id)   from)))
           (keep #(get by-id %))
           (map :file))
          (:edges graph))))

(defn expand-node!
  "Bring in file cards for every file that holds a neighbor of `node-id`."
  [node-id]
  (let [{:keys [shown by-id]} @state
        home    (some-> (get by-id node-id) :file)
        center  (get shown home)
        targets (->> (node-neighbor-files node-id)
                     (remove #(= % home))
                     (remove #(contains? shown %))
                     vec)]
    (when (and center (seq targets))
      (let [step (/ (* 2 Math/PI) (count targets))
            dist 560
            placed (into {}
                         (map-indexed
                          (fn [i f]
                            (let [angle (- (* i step) (/ Math/PI 2))]
                              [f {:x (+ (:x center) (* (Math/cos angle) dist))
                                  :y (+ (:y center) (* (Math/sin angle) dist))}]))
                          targets))]
        (swap! state update :shown merge placed)))))

(defn move-card! [file-path dx dy origin-x origin-y]
  (swap! state update-in [:shown file-path]
         (fn [s]
           (-> s
               (assoc :x (+ origin-x dx))
               (assoc :y (+ origin-y dy))))))

(defn toggle-expanded! [node-id]
  (swap! state update :expanded
         (fn [e] (if (contains? e node-id) (disj e node-id) (conj e node-id)))))

(defn set-pan! [x y]
  (swap! state assoc :pan-x x :pan-y y))

(defn set-zoom! [z px py]
  (let [{:keys [pan-x pan-y zoom]} @state
        ratio  (/ z zoom)
        new-px (+ (* px (- 1 ratio)) (* ratio pan-x))
        new-py (+ (* py (- 1 ratio)) (* ratio pan-y))]
    (swap! state assoc :zoom z :pan-x new-px :pan-y new-py)))
