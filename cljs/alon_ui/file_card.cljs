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

(defn row-height
  "Total pixel height of a row given current expanded state.

   When expanded, a node with captured children becomes a container —
   its body holds child rows in a vertical stack. Otherwise an expanded
   leaf shows its source. Collapsed rows are just the row-head."
  [node]
  (let [{:keys [expanded]} @state/state
        open? (contains? expanded (:id node))
        kids  (when open? (ordered-children (:id node)))]
    (+ ROW-H
       (cond
         (not open?)    0
         (seq kids)     (+ CONTAINER-PAD
                           (reduce + (map row-height kids))
                           (* CONTAINER-GAP (max 0 (dec (count kids))))
                           CONTAINER-PAD)
         (:source node) (source-height (:source node))
         :else          0))))

(defn row-top-y
  "Y offset of the row-head top for `node-id` within its file-card.
   Walks the ancestor chain so nested rows know where they live."
  [node-id]
  (let [{:keys [by-id by-file]} @state/state
        node (get by-id node-id)]
    (when node
      (if-let [pid (:parentId node)]
        (let [siblings (ordered-children pid)
              before   (loop [ss siblings, y 0]
                         (if-let [s (first ss)]
                           (if (= (:id s) node-id)
                             y
                             (recur (rest ss)
                                    (+ y (row-height s) CONTAINER-GAP)))
                           y))]
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
  "Absolute y of a call-site at `offset` within `node-id`'s card. Only
   meaningful for an expanded leaf — a container shows child rows instead
   of source, so there's no line to anchor to."
  [node-id offset]
  (let [{:keys [by-id expanded children-of]} @state/state
        node  (get by-id node-id)
        leaf? (empty? (get children-of node-id))
        open? (contains? expanded node-id)]
    (when (and open? leaf? (:source node) offset)
      (+ (row-top-y node-id) ROW-H
         (source-line-y (:source node) offset)))))

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
   nested rows share its drag state and lifecycle. Expanded nodes with
   captured children render in container mode (children as nested rows);
   leaves render their source."
  [start-drag node]
  (let [{:keys [expanded focused trail]} @state/state
        {:keys [id name type source]} node
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
      [:span.name name]]
     (when open?
       (cond
         has-kids?
         (into [:div.body.container
                {:on-mouse-down (fn [e] (.stopPropagation e))}]
               (for [k kids]
                 ^{:key (:id k)} [row start-drag k]))

         source
         [:div.body
          {:on-mouse-down (fn [e] (.stopPropagation e))}
          [:pre.source source]]))]))

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
                         (state/focus! (:click-id d)))))]
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
            [:span.dismiss
             {:title "dismiss this file (and everything it brought in)"
              :on-mouse-down (fn [e]
                               (.stopPropagation e)
                               (state/dismiss-file! file))}
             "×"]
            (short-path file)]]))})))
