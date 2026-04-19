(ns alon-ui.file-card
  (:require [alon-ui.state :as state]
            [reagent.core :as r]
            [clojure.string :as str]))

;; Each card renders ONE function's source. Outbound call edges whose target
;; is resolvable are spliced into the source as clickable blue spans — click
;; a call to spawn a new box for the callee. Unresolved calls stay as plain
;; text since clicking them would do nothing.

(def CARD-WIDTH      320)
(def ROW-H           34)
(def LINE-H          16)
(def CHAR-W          6.6)
(def SOURCE-PAD-X    14)
(def SOURCE-PAD-TOP  6)
(def SOURCE-PAD-BOT  12)

(defn width-for [fn-id]
  (or (get-in @state/state [:width-by-id fn-id]) CARD-WIDTH))

(defn- line-count [s]
  (if (string? s)
    (inc (count (filter #(= % \newline) s)))
    0))

(defn source-height
  "Height in canvas pixels of a rendered source block. Matches the CSS
   padding/line-height so edges can anchor analytically without measuring."
  [s]
  (if (and (string? s) (pos? (count s)))
    (+ SOURCE-PAD-TOP (* (line-count s) LINE-H) SOURCE-PAD-BOT)
    0))

(defn splice-calls
  "Walk `src` and interleave outbound call edges as clickable spans. For
   each edge (sorted by offsetStart) we emit:
     {:kind :text :text \"…\"}         raw source before the next call
     {:kind :call :edge e :text \"…\"}  the call expression itself
   Overlapping edges (a call nested inside another call's range) are
   skipped — whichever span renders first wins, so `foo(bar())` shows
   the outer `foo` as the clickable link."
  [src edges]
  (if-not (string? src)
    []
    (let [sorted (sort-by :offsetStart edges)
          n      (count src)]
      (loop [es sorted, cur 0, acc []]
        (if-let [e (first es)]
          (let [os (:offsetStart e)
                oe (:offsetEnd   e)]
            (if (or (< os cur) (nil? os) (nil? oe))
              (recur (rest es) cur acc)
              (let [c-os (min os n)
                    c-oe (min oe n)
                    before (subs src cur c-os)
                    call   (subs src c-os c-oe)]
                (recur (rest es) c-oe
                       (cond-> acc
                         (pos? (count before)) (conj {:kind :text :text before})
                         (pos? (count call))   (conj {:kind :call :edge e :text call}))))))
          (let [tail (subs src (min cur n))]
            (cond-> acc
              (pos? (count tail)) (conj {:kind :text :text tail}))))))))

(defn- offset->line-col
  "Return [line-index col-index] of character `offset` within `src`."
  [src offset]
  (let [before  (subs src 0 (min offset (count src)))
        nls     (count (filter #(= % \newline) before))
        last-nl (str/last-index-of before "\n")
        col     (if last-nl
                  (- offset last-nl 1)
                  offset)]
    [nls col]))

(defn call-site-y
  "Absolute y (in canvas coords) of the call at `offset` inside `fn-id`'s
   box. Sits on the vertical center of the source line containing it."
  [fn-id offset]
  (let [{:keys [by-id shown]} @state/state
        node (get by-id fn-id)
        pos  (get shown fn-id)]
    (when (and node pos (:source node) offset)
      (let [[nls _] (offset->line-col (:source node) offset)]
        (+ (:y pos) ROW-H SOURCE-PAD-TOP (* nls LINE-H) (/ LINE-H 2))))))

(defn fn-card [fn-id]
  (let [drag-state (r/atom nil)
        el-ref     (atom nil)
        measure!   (fn []
                     (when-let [el @el-ref]
                       (state/set-measured-height! fn-id (.-offsetHeight el))))
        on-move    (fn [e]
                     (when-let [d @drag-state]
                       (let [dx (/ (- (.-clientX e) (:mouse-x d)) (:zoom d))
                             dy (/ (- (.-clientY e) (:mouse-y d)) (:zoom d))]
                         (when (> (Math/hypot dx dy) 3)
                           (swap! drag-state assoc :moved? true))
                         (state/move-card! fn-id dx dy (:start-x d) (:start-y d)))))
        on-up      (fn [_]
                     (let [d @drag-state]
                       (reset! drag-state nil)
                       (when (and d (not (:moved? d)))
                         (state/focus! fn-id))))]
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
      (fn [fn-id]
        (let [s          @state/state
              node       (get-in s [:by-id fn-id])
              pos        (get-in s [:shown fn-id])
              focused?   (= fn-id (:focused s))
              trail?     (some #(= % fn-id) (:trail s))
              edges-out  (get-in s [:edges-by-from fn-id] [])
              segs       (splice-calls (:source node) edges-out)
              dragging?  (some? @drag-state)
              start-drag (fn [e]
                           (when (zero? (.-button e))
                             (.stopPropagation e)
                             (reset! drag-state
                                     {:start-x (:x pos)
                                      :start-y (:y pos)
                                      :mouse-x (.-clientX e)
                                      :mouse-y (.-clientY e)
                                      :zoom    (:zoom s)
                                      :moved?  false})))]
          [:div.file-card
           {:class (cond-> []
                     (:root? pos) (conj "root")
                     focused?     (conj "focused")
                     trail?       (conj "trail")
                     dragging?    (conj "dragging"))
            :ref   (fn [el] (reset! el-ref el))
            :style {:left (:x pos) :top (:y pos) :width (width-for fn-id)}}
           [:div.card
            [:div.row
             [:div.row-head
              {:on-mouse-down start-drag}
              [:span.kind (:type node)]
              [:span.name (or (:signature node) (:name node))]]
             (when (:source node)
               (into [:pre.source
                      {:on-mouse-down (fn [e] (.stopPropagation e))}]
                     (map-indexed
                      (fn [i seg]
                        (case (:kind seg)
                          :text ^{:key i} [:span (:text seg)]
                          :call ^{:key i}
                          [:span.call
                           {:title (str "spawn " (:name (get-in s [:by-id (:to (:edge seg))])))
                            :on-mouse-down (fn [e] (.stopPropagation e))
                            :on-click (fn [e]
                                        (.stopPropagation e)
                                        (state/spawn-call! fn-id (:to (:edge seg))))}
                           (:text seg)]))
                      segs)))]]
           (when-not (:root? pos)
             [:div.path
              {:on-mouse-down start-drag}
              [:span.dismiss
               {:title "dismiss this box (and everything it spawned)"
                :on-mouse-down (fn [e]
                                 (.stopPropagation e)
                                 (state/dismiss! fn-id))}
               "×"]
              (:name node)])]))})))
