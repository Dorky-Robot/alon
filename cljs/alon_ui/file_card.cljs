(ns alon-ui.file-card
  (:require [alon-ui.state :as state]
            [reagent.core :as r]
            [clojure.string :as str]))

(def CARD-WIDTH       320)   ; fallback when state has no width for a file
(def ROW-H            34)

(defn width-for [file]
  (or (get-in @state/state [:width-by-file file]) CARD-WIDTH))
(def LINE-H           16)
(def SOURCE-PAD-TOP   6)
(def SOURCE-PAD-BOT   12)

(declare row)

(defn- line-count [s]
  (if (string? s)
    (inc (count (filter #(= % \newline) s)))
    0))

(defn source-height
  "Height in canvas pixels of a rendered source-text segment. Matches the
   fixed line-height/padding values in the CSS so edges can anchor analytically."
  [s]
  (if (and (string? s) (pos? (count s)))
    (+ SOURCE-PAD-TOP (* (line-count s) LINE-H) SOURCE-PAD-BOT)
    0))

(defn- meaningful-text?
  "A source segment is worth rendering only if it contains at least one
   non-whitespace character. Pure blank-line glue between nested function
   defs would otherwise show up as tall dark bands between the inset rows."
  [s]
  (boolean (re-find #"\S" s)))

(defn splice-source
  "Walk the parent node's source and emit a vector of segments:
     [:text \"...\"]   raw source slice (only if it has real content)
     [:child child-id] hole where a captured nested function lives
   Children are addressed by absolute file offsets (:start), so we subtract
   the parent's :start to map into its own source string."
  [parent children-of by-id]
  (let [pstart (:start parent)
        psrc   (:source parent)
        kids   (->> (get children-of (:id parent) [])
                    (keep by-id)
                    (sort-by :start))]
    (loop [cur 0, ks kids, acc []]
      (if-let [k (first ks)]
        (let [rs (- (:start k) pstart)
              re (- (:end k)   pstart)
              before (subs psrc (max cur 0) (max rs cur))
              acc'   (cond-> acc (meaningful-text? before) (conj [:text before]))]
          (recur re (rest ks) (conj acc' [:child (:id k)])))
        (let [tail (subs psrc cur)]
          (cond-> acc (meaningful-text? tail) (conj [:text tail])))))))

(defn row-height
  "Total pixel height of a row given current expanded state. Recursive
   through nested children."
  [node]
  (let [{:keys [children-of by-id expanded]} @state/state
        open? (contains? expanded (:id node))]
    (+ ROW-H
       (if (and open? (:source node))
         (reduce
          (fn [acc seg]
            (case (first seg)
              :text  (+ acc (source-height (second seg)))
              :child (+ acc (row-height (get by-id (second seg))))))
          0
          (splice-source node children-of by-id))
         0))))

(defn row-top-y
  "Y offset of the row-head top for `node-id` within its file-card.
   Walks the ancestor chain so nested rows know where they live."
  [node-id]
  (let [{:keys [by-id by-file children-of expanded]} @state/state
        node (get by-id node-id)]
    (when node
      (if-let [pid (:parentId node)]
        (let [parent  (get by-id pid)
              ;; parent header + segments above this child
              segs    (splice-source parent children-of by-id)
              before  (reduce
                       (fn [acc seg]
                         (let [[kind v] seg]
                           (cond
                             (and (= kind :child) (= v node-id))
                             (reduced acc)
                             (= kind :text)
                             (+ acc (source-height v))
                             :else
                             (+ acc (row-height (get by-id v))))))
                       0
                       segs)]
          (+ (row-top-y pid) ROW-H before))
        ;; top-level: walk siblings in the file in declaration order
        (let [siblings (get by-file (:file node))]
          (loop [ss siblings, y 0]
            (if-let [s (first ss)]
              (if (= (:id s) node-id)
                y
                (recur (rest ss) (+ y (row-height s))))
              y)))))))

(defn row-y-center [node-id]
  (+ (row-top-y node-id) (/ ROW-H 2)))

(defn source-line-y
  "Y offset, within a source string `src`, of the vertical center of the
   line that contains character `offset`."
  [src offset]
  (let [before (subs src 0 (min offset (count src)))
        nls    (count (filter #(= % \newline) before))]
    (+ SOURCE-PAD-TOP (* nls LINE-H) (/ LINE-H 2))))

(defn call-site-y
  "Absolute y of a call-site at `offset` within node-id's source, accounting
   for nested children that may live above the offset and consume more space
   than the raw text they replace."
  [node-id offset]
  (let [{:keys [by-id children-of expanded]} @state/state
        node (get by-id node-id)
        segs (splice-source node children-of by-id)
        pstart (:start node)]
    (loop [ss segs, y 0, cursor 0]
      (if-let [seg (first ss)]
        (let [[kind v] seg]
          (case kind
            :text
            (let [seg-len (count v)
                  end     (+ cursor seg-len)]
              (if (and (>= offset cursor) (< offset end))
                (+ y (source-line-y v (- offset cursor)))
                (recur (rest ss) (+ y (source-height v)) end)))
            :child
            (let [child  (get by-id v)
                  c-rel-start (- (:start child) pstart)
                  c-rel-end   (- (:end child)   pstart)]
              (if (and (>= offset c-rel-start) (< offset c-rel-end))
                ;; offset falls inside the child block — punt to row center
                (+ y (/ (row-height child) 2))
                (recur (rest ss) (+ y (row-height child)) c-rel-end)))))
        y))))

(defn- short-path [file]
  (let [parts (str/split file #"/")
        n     (count parts)]
    (if (>= n 2)
      (str/join "/" (subvec (vec parts) (max 0 (- n 2))))
      file)))

(defn row
  "Recursive renderer. `start-drag` is closed over by the file-card so all
   nested rows share its drag state and lifecycle."
  [start-drag node]
  (let [{:keys [children-of by-id expanded]} @state/state
        {:keys [id name type source]} node
        open?    (contains? expanded id)
        segments (when (and open? source)
                   (splice-source node children-of by-id))]
    [:div.row {:class (when open? "expanded")}
     [:div.row-head
      {:on-mouse-down (fn [e] (start-drag id e))}
      [:span.kind type]
      [:span.name name]]
     (when open?
       (into [:div.body
              {:on-mouse-down (fn [e] (.stopPropagation e))}]
             (map-indexed
              (fn [i seg]
                (let [[kind v] seg]
                  (case kind
                    :text  ^{:key i} [:pre.source v]
                    :child ^{:key i} [row start-drag (get by-id v)])))
              segments)))]))

(defn file-card [file]
  (let [drag-state (r/atom nil)
        on-move    (fn [e]
                     (when-let [d @drag-state]
                       (let [dx (/ (- (.-clientX e) (:mouse-x d)) (:zoom d))
                             dy (/ (- (.-clientY e) (:mouse-y d)) (:zoom d))]
                         (when (> (Math/hypot dx dy) 3)
                           (swap! drag-state assoc :moved? true))
                         (state/move-card! file dx dy (:start-x d) (:start-y d)))))
        on-up      (fn [_]
                     (let [d @drag-state]
                       (reset! drag-state nil)
                       (when (and d (not (:moved? d)) (:click-id d))
                         (state/toggle-expanded! (:click-id d))
                         (state/expand-node!     (:click-id d)))))]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (.addEventListener js/window "mousemove" on-move)
        (.addEventListener js/window "mouseup"   on-up))
      :component-will-unmount
      (fn [_]
        (.removeEventListener js/window "mousemove" on-move)
        (.removeEventListener js/window "mouseup"   on-up))
      :reagent-render
      (fn [file]
        (let [{:keys [by-file shown]} @state/state
              top-nodes (get by-file file)
              pos       (get shown file)
              dragging? (some? @drag-state)
              start-drag (fn [click-id e]
                           (when (zero? (.-button e))
                             (.stopPropagation e)
                             (reset! drag-state
                                     {:start-x  (:x pos)
                                      :start-y  (:y pos)
                                      :mouse-x  (.-clientX e)
                                      :mouse-y  (.-clientY e)
                                      :zoom     (:zoom @state/state)
                                      :click-id click-id
                                      :moved?   false})))]
          [:div.file-card
           {:class (cond-> []
                     (:root? pos) (conj "root")
                     dragging?    (conj "dragging"))
            :style {:left (:x pos) :top (:y pos) :width (width-for file)}}
           (into [:div.card]
                 (for [n top-nodes]
                   ^{:key (:id n)} [row start-drag n]))
           [:div.path
            {:on-mouse-down (fn [e] (start-drag nil e))}
            (short-path file)]]))})))
