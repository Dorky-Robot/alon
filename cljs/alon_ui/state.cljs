(ns alon-ui.state
  (:require [reagent.core :as r]))

;; Schema:
;;   {:graph         {:nodes [...] :edges [...] :root "..."}
;;    :by-id         {node-id node}
;;    :by-file       {file-path [node ...]}   ; sorted by trace order (DFS from roots)
;;    :edges-by-from {node-id [edge ...]}
;;    :shown         {file-path {:x N :y N :root? bool}}
;;    :expanded      #{node-id}
;;    :pan-x N :pan-y N :zoom N}

(defonce state
  (r/atom {:graph         nil
           :by-id         {}
           :by-file       {}
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

(defn init-graph! [graph]
  (let [nodes    (:nodes graph)
        by-id    (into {} (map (juxt :id identity)) nodes)
        trace    (compute-trace-order graph)
        by-file  (reduce (fn [acc n] (update acc (:file n) (fnil conj []) n))
                         {} nodes)
        by-file  (into {}
                       (map (fn [[f ns]]
                              [f (vec (sort-by #(get trace (:id %) 1e9) ns))]))
                       by-file)
        edges-by-from (reduce (fn [m e] (update m (:from e) (fnil conj []) e))
                              {} (:edges graph))
        entry    (:root graph)
        start-file (or (when (contains? by-file entry) entry)
                       (some-> (first nodes) :file))]
    (swap! state assoc
           :graph         graph
           :by-id         by-id
           :by-file       by-file
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
