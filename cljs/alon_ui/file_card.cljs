(ns alon-ui.file-card
  (:require [alon-ui.state :as state]
            [reagent.core :as r]
            [clojure.string :as str]))

(def CARD-WIDTH       320)   ; fallback when state has no width for a file
(def ROW-H            34)
(def CONTAINER-PAD    10)    ; matches CSS .body.container padding
(def CONTAINER-GAP     8)    ; matches CSS .body.container gap

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

(defn- ordered-children
  "Direct children of `parent-id`, in declaration order."
  [parent-id]
  (let [{:keys [children-of by-id]} @state/state]
    (->> (get children-of parent-id [])
         (keep by-id)
         (sort-by :start))))

(defn- meaningful-text?
  "Non-whitespace source survives as its own segment; blank-line glue
   between nested defs would otherwise show up as empty dark bands."
  [s]
  (boolean (re-find #"\S" s)))

(defn splice-source
  "Walk `parent`'s source and emit segments in source order:
     [:text \"...\"]   raw interstitial slice (only if non-blank)
     [:child child-id] hole where a captured nested decl lives
   Children are addressed by absolute file offsets (:start), so we subtract
   the parent's :start to map into its own source string."
  [parent]
  (let [{:keys [by-id]} @state/state
        pstart (:start parent)
        psrc   (:source parent)
        kids   (ordered-children (:id parent))]
    (if (or (nil? pstart) (not (string? psrc)))
      []
      (loop [cur 0, ks kids, acc []]
        (if-let [k (first ks)]
          (let [rs (max cur (- (:start k) pstart))
                re (max rs  (- (:end k)   pstart))
                before (subs psrc (min cur (count psrc))
                                  (min rs  (count psrc)))
                acc'   (cond-> acc (meaningful-text? before) (conj [:text before]))]
            (recur re (rest ks) (conj acc' [:child (:id k)])))
          (let [tail (subs psrc (min cur (count psrc)))]
            (cond-> acc (meaningful-text? tail) (conj [:text tail]))))))))

(declare row-height)

(defn- segment-height [seg]
  (let [[kind v] seg]
    (case kind
      :text  (source-height v)
      :child (row-height (get-in @state/state [:by-id v])))))

(defn- segments-total-height
  "Body height of a spliced container: padding + segment stack with GAP
   between every adjacent pair. Empty stack collapses to zero."
  [segs]
  (if (empty? segs)
    0
    (+ CONTAINER-PAD
       (reduce + (map segment-height segs))
       (* CONTAINER-GAP (dec (count segs)))
       CONTAINER-PAD)))

(defn row-height
  "Total pixel height of a row given current expanded state.

   When expanded, a node with captured children splices its own source
   around the child rows so the caller can see how the nested functions
   are wired in. A leaf falls back to raw source, collapsed is just the
   row-head."
  [node]
  (let [{:keys [expanded]} @state/state
        open? (contains? expanded (:id node))
        kids  (when open? (ordered-children (:id node)))]
    (+ ROW-H
       (cond
         (not open?)    0
         (seq kids)     (segments-total-height (splice-source node))
         (:source node) (source-height (:source node))
         :else          0))))

(defn- y-before-child
  "Y offset within a spliced body at which `child-id` starts — sums
   preceding segments and the GAP between them."
  [segs child-id]
  (loop [ss segs, y 0]
    (if-let [seg (first ss)]
      (let [[kind v] seg]
        (if (and (= kind :child) (= v child-id))
          y
          (recur (rest ss) (+ y (segment-height seg) CONTAINER-GAP))))
      y)))

(defn row-top-y
  "Y offset of the row-head top for `node-id` within its file-card.
   Walks the ancestor chain so nested rows know where they live."
  [node-id]
  (let [{:keys [by-id by-file]} @state/state
        node (get by-id node-id)]
    (when node
      (if-let [pid (:parentId node)]
        (let [parent (get by-id pid)
              segs   (splice-source parent)
              before (y-before-child segs node-id)]
          (+ (row-top-y pid) ROW-H CONTAINER-PAD before))
        (let [siblings (get by-file (:file node))]
          (loop [ss siblings, y 0]
            (if-let [s (first ss)]
              (if (= (:id s) node-id)
                y
                (recur (rest ss) (+ y (row-height s))))
              y)))))))

(defn row-y-center [node-id]
  (+ (row-top-y node-id) (/ ROW-H 2)))

(defn- source-line-y
  "Vertical center (within a source block) of the line that contains
   character `offset`."
  [src offset]
  (let [before (subs src 0 (min offset (count src)))
        nls    (count (filter #(= % \newline) before))]
    (+ SOURCE-PAD-TOP (* nls LINE-H) (/ LINE-H 2))))

(defn call-site-y
  "Absolute y of a call-site at `offset` within `node-id`'s card.
   In leaf mode offset indexes directly into :source. In spliced container
   mode, offset indexes into the parent's full source but only interstitial
   :text segments are rendered — walk the splice, map offset to the segment
   containing it. Offsets that land inside a child range anchor at that
   child's row center."
  [node-id offset]
  (let [{:keys [by-id expanded children-of]} @state/state
        node      (get by-id node-id)
        open?     (contains? expanded node-id)
        has-kids? (seq (get children-of node-id))]
    (when (and open? (:source node) offset)
      (let [segs   (splice-source node)
            pstart (:start node)]
        (if (not has-kids?)
          (+ (row-top-y node-id) ROW-H
             (source-line-y (:source node) offset))
          (loop [ss segs, y CONTAINER-PAD, cursor 0, i 0]
            (if-let [seg (first ss)]
              (let [[kind v] seg
                    seg-y (if (pos? i) (+ y CONTAINER-GAP) y)]
                (case kind
                  :text
                  (let [start cursor
                        end   (+ cursor (count v))]
                    (if (and (>= offset start) (< offset end))
                      (+ (row-top-y node-id) ROW-H seg-y
                         (source-line-y v (- offset start)))
                      (recur (rest ss) (+ seg-y (source-height v)) end (inc i))))
                  :child
                  (let [child    (get by-id v)
                        c-start  (- (:start child) pstart)
                        c-end    (- (:end child)   pstart)
                        kid-h    (row-height child)]
                    (if (and (>= offset c-start) (< offset c-end))
                      (+ (row-top-y node-id) ROW-H seg-y (/ kid-h 2))
                      (recur (rest ss) (+ seg-y kid-h) c-end (inc i))))))
              ;; offset past every segment — fall back to row center.
              (+ (row-top-y node-id) (/ ROW-H 2)))))))))

(defn row-depth
  "How many captured-fn ancestors `node-id` has. Top-level = 0."
  [node-id]
  (loop [id node-id, d 0]
    (if-let [pid (:parentId (get-in @state/state [:by-id id]))]
      (recur pid (inc d))
      d)))

(defn row-x-inset
  "Horizontal padding (each side) consumed by container ancestors before a
   nested row's edge. Lets edges anchor at the nested card's own border
   instead of swooping out to the outer file-card edge."
  [node-id]
  (* CONTAINER-PAD (row-depth node-id)))

(defn- short-path [file]
  (let [parts (str/split file #"/")
        n     (count parts)]
    (if (>= n 2)
      (str/join "/" (subvec (vec parts) (max 0 (- n 2))))
      file)))

(defn row
  "Recursive renderer. `start-drag` is closed over by the file-card so all
   nested rows share its drag state and lifecycle. Expanded container
   nodes render their source SPLICED around nested child rows so the reader
   can see how the parent hands off to its inner functions. Leaves with no
   captured children just show their source verbatim."
  [start-drag node]
  (let [{:keys [by-id expanded focused trail]} @state/state
        {:keys [id name signature type source]} node
        open?     (contains? expanded id)
        kids      (when open? (ordered-children id))
        has-kids? (seq kids)
        focused?  (= id focused)
        trail?    (some #(= % id) trail)]
    [:div.row {:class (cond-> []
                        open?     (conj "expanded")
                        has-kids? (conj "container")
                        focused?  (conj "focused")
                        trail?    (conj "trail"))}
     [:div.row-head
      {:on-mouse-down (fn [e] (start-drag id e))}
      [:span.kind type]
      [:span.name (or signature name)]]
     (when open?
       (cond
         has-kids?
         (into [:div.body.container
                {:on-mouse-down (fn [e] (.stopPropagation e))}]
               (map-indexed
                (fn [i seg]
                  (let [[kind v] seg]
                    (case kind
                      :text  ^{:key i} [:pre.source v]
                      :child ^{:key (str "c-" v)}
                      [row start-drag (get by-id v)])))
                (splice-source node)))

         source
         [:div.body
          {:on-mouse-down (fn [e] (.stopPropagation e))}
          [:pre.source source]]))]))

(defn file-card [file]
  (let [drag-state (r/atom nil)
        el-ref     (atom nil)
        ;; Measure the card's true rendered height and let state decide
        ;; whether anything needs to reflow. offsetHeight is in unscaled
        ;; CSS pixels even when an ancestor has a CSS transform, which is
        ;; exactly the coordinate space our layout math works in.
        measure!   (fn []
                     (when-let [el @el-ref]
                       (state/set-measured-height! file (.-offsetHeight el))))
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
                         (state/focus! (:click-id d)))))]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (.addEventListener js/window "mousemove" on-move)
        (.addEventListener js/window "mouseup"   on-up)
        (measure!))
      :component-did-update
      (fn [_ _] (measure!))
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
            :ref   (fn [el] (reset! el-ref el))
            :style {:left (:x pos) :top (:y pos) :width (width-for file)}}
           (into [:div.card]
                 (for [n top-nodes]
                   ^{:key (:id n)} [row start-drag n]))
           [:div.path
            {:on-mouse-down (fn [e] (start-drag nil e))}
            [:span.dismiss
             {:title "dismiss this file (and everything it brought in)"
              :on-mouse-down (fn [e]
                               (.stopPropagation e)
                               (state/dismiss-file! file))}
             "×"]
            (short-path file)]]))})))
