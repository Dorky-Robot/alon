(ns alon-ui.file-card
  (:require [alon-ui.state :as state]
            [reagent.core :as r]
            [clojure.string :as str]))

(def CARD-WIDTH       320)
(def ROW-H            34)
(def LINE-H           16)
(def SOURCE-PAD-TOP   6)
(def SOURCE-PAD-BOT   12)

(defn- line-count [s]
  (if (string? s)
    (inc (count (filter #(= % \newline) s)))
    0))

(defn source-height
  "Height in canvas pixels of a rendered source block. Matches the fixed
   line-height/padding values in the CSS so edges can anchor analytically."
  [source]
  (if (and (string? source) (pos? (count source)))
    (+ SOURCE-PAD-TOP (* (line-count source) LINE-H) SOURCE-PAD-BOT)
    0))

(defn row-top-y
  "Y offset of the row-head top for `node-id` within its file-card."
  [nodes expanded node-id]
  (loop [ns nodes, y 0]
    (if-let [n (first ns)]
      (if (= (:id n) node-id)
        y
        (recur (rest ns)
               (+ y ROW-H
                  (if (and (contains? expanded (:id n)) (:source n))
                    (source-height (:source n))
                    0))))
      y)))

(defn row-y-center
  "Y offset of the row-head vertical center — the default edge anchor."
  [nodes expanded node-id]
  (+ (row-top-y nodes expanded node-id) (/ ROW-H 2)))

(defn source-line-y
  "Y offset, within a source block, of the vertical center of the line that
   contains character `offset`."
  [source offset]
  (let [before  (subs source 0 (min offset (count source)))
        nls     (count (filter #(= % \newline) before))]
    (+ SOURCE-PAD-TOP (* nls LINE-H) (/ LINE-H 2))))

(defn- short-path [file]
  (let [parts (str/split file #"/")
        n     (count parts)]
    (if (>= n 2)
      (str/join "/" (subvec (vec parts) (max 0 (- n 2))))
      file)))


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
        (let [{:keys [by-file expanded shown]} @state/state
              nodes     (get by-file file)
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
            :style {:left (:x pos) :top (:y pos) :width CARD-WIDTH}}
           [:div.card
            (for [{:keys [id name type source]} nodes
                  :let [open? (contains? expanded id)]]
              ^{:key id}
              [:div.row {:class (when open? "expanded")}
               [:div.row-head
                {:on-mouse-down (fn [e] (start-drag id e))}
                [:span.kind type]
                [:span.name name]]
               (when (and open? source)
                 [:pre.source
                  {:on-mouse-down (fn [e] (.stopPropagation e))}
                  source])])]
           [:div.path
            {:on-mouse-down (fn [e] (start-drag nil e))}
            (short-path file)]]))})))
