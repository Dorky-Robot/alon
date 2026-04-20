(ns alon-ui.file-card
  (:require [alon-ui.state :as state]
            [alon-ui.highlight :as hi]
            [reagent.core :as r]
            [clojure.string :as str]))

;; Each card renders ONE function's source. Outbound call edges whose target
;; is resolvable are spliced into the source as clickable blue spans — click
;; a call to spawn a new box for the callee.
;;
;; Nested function/method declarations inside the body collapse to a single
;; `signature { … }` pill by default, Xcode-style — click to expand, click
;; again to collapse. Each nested carries its own disclosure state; expanding
;; a parent does NOT auto-expand its children. Call-links inside collapsed
;; nesteds are hidden (no visible span) and their arrows anchor on the pill.

(def CARD-WIDTH      320)
(def ROW-H           34)
(def LINE-H          16)
(def CHAR-W          6.6)
(def SOURCE-PAD-X    14)
(def SOURCE-PAD-TOP  6)
(def SOURCE-PAD-BOT  12)

(defn width-for [fn-id]
  (or (get-in @state/state [:width-by-id fn-id]) CARD-WIDTH))

(defn- relative-to-root
  "Strip the entry file's directory from `file` so the dismiss chip reads
   `subdir/name.mjs:fn` rather than the absolute path. Falls back to the
   basename if `file` doesn't sit under the entry's directory."
  [file root]
  (if (and (string? file) (string? root))
    (let [slash  (str/last-index-of root "/")
          prefix (when slash (subs root 0 (inc slash)))]
      (cond
        (and prefix (str/starts-with? file prefix))
        (subs file (count prefix))

        :else
        (let [i (str/last-index-of file "/")]
          (if i (subs file (inc i)) file))))
    (or file "")))

(defn- count-newlines [s]
  (if (string? s)
    (count (filter #(= % \newline) s))
    0))

(defn- line-count [s]
  (if (string? s)
    (inc (count-newlines s))
    0))

(defn source-height
  "Height in canvas pixels of a rendered source block. Matches the CSS
   padding/line-height so edges can anchor analytically without measuring."
  [s]
  (if (and (string? s) (pos? (count s)))
    (+ SOURCE-PAD-TOP (* (line-count s) LINE-H) SOURCE-PAD-BOT)
    0))

;; --- Plan building -------------------------------------------------------
;;
;; plan-for walks a renderable function's source and produces a flat
;; segment sequence in display order. Each segment carries its source-local
;; [os, oe) range plus the :line-start at which its first character
;; displays. Collapsed nesteds replace their body range with a one-line
;; pill, compressing the display; expanded nesteds are transparent — their
;; body text and any call-sites inside pass through as regular segments,
;; and their own descendants still start collapsed until clicked.

(defn- fn-like? [node]
  (and node (#{"function" "method"} (:type node))))

(defn- visible-nested-kids
  "All nested fn/method descendants of `renderable-id` that should appear
   in its plan. Collapsed ones emit as a `foo() { … }` pill; expanded ones
   emit a zero-length ▾ expander at their start so users can re-collapse.
   Expanded nesteds are otherwise transparent — we descend into them so
   grand-descendants also get their own pill/expander segments."
  [by-id children-of expanded-set renderable-id R-start]
  (letfn [(walk [acc node-id]
            (reduce
             (fn [acc cid]
               (let [child (get by-id cid)]
                 (if (fn-like? child)
                   (let [expanded? (contains? expanded-set cid)
                         seg {:node child
                              :os (- (:start child) R-start)
                              :oe (- (:end   child) R-start)
                              :collapsed? (not expanded?)}
                         acc' (conj acc seg)]
                     (if expanded? (walk acc' cid) acc'))
                   acc)))
             acc
             (get children-of node-id [])))]
    (walk [] renderable-id)))

(defn- nested-sig
  "One-line pill text for a collapsed nested. Takes the prefix up to the
   first `{` in the nested's source, whitespace-collapses it, and appends
   ` { … }`. Falls back to the nested's name if we can't find a brace."
  [node R-source os oe]
  (let [body  (subs R-source os (min oe (count R-source)))
        brace (str/index-of body "{")
        prefix (if brace
                 (subs body 0 brace)
                 (or (:name node) "fn"))
        one-line (-> prefix
                     (str/replace #"\s+" " ")
                     str/trim)]
    (str one-line " { … }")))

(defn- advance-by [line text]
  (+ line (count-newlines text)))

(defn- candidate->segment
  "Expand a candidate into its full display segment, given the offset
   `os` at which its prefix text starts, the source it slices from, and
   the display line its first char lands on."
  [R-source c line]
  (let [{:keys [os oe]} c]
    (case (:kind c)
      :call
      {:kind :call :os os :oe oe :edge (:edge c)
       :text (subs R-source os oe)
       :line-start line}

      :nested-collapsed
      {:kind :nested-collapsed :os os :oe oe :node (:node c)
       :text (nested-sig (:node c) R-source os oe)
       :line-start line}

      :nested-expander
      ;; Zero-length: the body passes through as text/calls and the ▾
      ;; sits just before the `function` keyword.
      {:kind :nested-expander :os os :oe os :node (:node c)
       :text "▾" :line-start line})))

(defn- step-candidate
  "Reducer step: extend the accumulating plan with any gap-text before
   `c` and `c`'s own segment. Drops `c` when it overlaps a segment already
   emitted (a call-site inside a collapsed nested's range, for instance)."
  [R-source {:keys [cur line acc] :as acc-state} c]
  (let [os (:os c)]
    (if (< os cur)
      acc-state
      (let [before (subs R-source cur os)
            acc1   (cond-> acc
                     (pos? (count before))
                     (conj {:kind :text :os cur :oe os
                            :text before :line-start line}))
            line1  (advance-by line before)
            seg    (candidate->segment R-source c line1)]
        {:cur (:oe seg)
         :line (advance-by line1 (:text seg))
         :acc (conj acc1 seg)}))))

(defn plan-for
  "Build a segment plan for rendering renderable R's source given call
   edges (already lifted to R-local offsets) and its nested descendants.

   Returns a vector of segments, each with:
     :kind       :text | :call | :nested-collapsed | :nested-expander
     :os :oe     source-local offsets (R-source coords)
     :text       display text
     :line-start display-line index of the segment's first char
   :call adds :edge; :nested-* add :node.

   :nested-expander is zero-length (:os == :oe) and renders as a clickable
   ▾ glyph just before an expanded nested's signature — clicking it collapses
   the block back to its pill form, IDE-fold style."
  [R-source edges nested-kids]
  (if-not (string? R-source)
    []
    (let [len (count R-source)
          collapsed-ranges (for [k nested-kids :when (:collapsed? k)]
                             [(:os k) (:oe k)])
          inside-collapsed? (fn [o]
                              (some (fn [[ns ne]] (and (<= ns o) (< o ne)))
                                    collapsed-ranges))
          call-cands (for [e edges
                           :let [os (:offsetStart e)
                                 oe (:offsetEnd   e)]
                           :when (and os oe
                                      (<= 0 os) (<= oe len)
                                      (< os oe)
                                      (not (inside-collapsed? os)))]
                       {:kind :call :os os :oe oe :edge e})
          nested-cands (for [{:keys [node os oe collapsed?]} nested-kids
                             :when (and os oe (<= 0 os) (< os oe) (<= oe len))]
                         (if collapsed?
                           {:kind :nested-collapsed :os os :oe oe :node node}
                           {:kind :nested-expander  :os os :oe os :node node}))
          candidates (sort-by :os (concat nested-cands call-cands))
          {:keys [cur line acc]}
          (reduce (partial step-candidate R-source)
                  {:cur 0 :line 0 :acc []}
                  candidates)
          tail (subs R-source cur len)]
      (cond-> acc
        (pos? (count tail))
        (conj {:kind :text :os cur :oe len
               :text tail :line-start line})))))

(defn- compute-plan
  "Collect what plan-for needs for a renderable fn-id from the current
   app state."
  [s fn-id]
  (when-let [node (get-in s [:by-id fn-id])]
    (let [edges       (get-in s [:edges-by-from fn-id] [])
          nested-kids (visible-nested-kids (:by-id s)
                                           (:children-of s)
                                           (or (:expanded-nested s) #{})
                                           fn-id
                                           (:start node))]
      (plan-for (:source node) edges nested-kids))))

(defn- seg-containing [plan offset]
  (some (fn [seg]
          (when (and (<= (:os seg) offset) (< offset (:oe seg)))
            seg))
        plan))

(defn call-site-y
  "Absolute y (in canvas coords) of the call at `offset` inside `fn-id`'s
   box. Uses the current plan so collapsed nesteds don't throw off the
   line math — a call-site inside a collapsed nested anchors on the pill."
  [fn-id offset]
  (let [s   @state/state
        pos (get-in s [:shown fn-id])]
    (when (and pos offset)
      (when-let [plan (compute-plan s fn-id)]
        (let [seg (seg-containing plan offset)
              line (cond
                     (nil? seg) 0
                     (= :text (:kind seg))
                     (+ (:line-start seg)
                        (count-newlines
                         (subs (:text seg) 0
                               (min (- offset (:os seg))
                                    (count (:text seg))))))
                     :else (:line-start seg))]
          (+ (:y pos) ROW-H SOURCE-PAD-TOP (* line LINE-H) (/ LINE-H 2)))))))

;; --- Render --------------------------------------------------------------

(defn- tokenized-spans
  "Render plain source text as per-token spans so the stylesheet can paint
   keywords/strings/etc. Whitespace runs come back as one :class nil span,
   so this doesn't explode into one element per character."
  [text key-prefix]
  (map-indexed
   (fn [i t]
     ^{:key (str key-prefix "-" i)}
     [:span (when (:class t) {:class (str "tok-" (name (:class t)))})
      (:text t)])
   (hi/tokenize text)))

(defn- render-segment [from-id s i seg]
  (case (:kind seg)
    :text
    (tokenized-spans (:text seg) (str "t" i))

    :call
    (let [to-id  (:to (:edge seg))
          callee (get-in s [:by-id to-id])]
      [^{:key (str "c" i)}
       [:span.call
        {:title (str "spawn " (:name callee))
         :on-mouse-down    (fn [e] (.stopPropagation e))
         :on-double-click  (fn [e] (.stopPropagation e))
         :on-click (fn [e]
                     (.stopPropagation e)
                     (state/spawn-call! from-id to-id))}
        (:text seg)]])

    :nested-collapsed
    (let [n (:node seg)]
      [^{:key (str "n" i)}
       [:span.nested-collapsed
        {:title (str "expand " (:name n))
         :on-mouse-down    (fn [e] (.stopPropagation e))
         :on-double-click  (fn [e] (.stopPropagation e))
         :on-click (fn [e]
                     (.stopPropagation e)
                     (state/toggle-nested! (:id n)))}
        (:text seg)]])

    :nested-expander
    (let [n (:node seg)]
      [^{:key (str "e" i)}
       [:span.nested-expander
        {:title (str "collapse " (:name n))
         :on-mouse-down    (fn [e] (.stopPropagation e))
         :on-double-click  (fn [e] (.stopPropagation e))
         :on-click (fn [e]
                     (.stopPropagation e)
                     (state/toggle-nested! (:id n)))}
        (:text seg)]])))

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
              plan       (compute-plan s fn-id)
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
            :style {:left (:x pos) :top (:y pos) :width (width-for fn-id)}
            ;; Click anywhere in the card (body whitespace, source non-text)
            ;; focuses it. Interactive children (call-link, pill, expander,
            ;; dismiss) stopPropagation on click, so they keep their own
            ;; semantics. row-head/path also route through focus! via the
            ;; drag-end handler on drag-with-no-move, which is idempotent.
            :on-click (fn [e]
                        (.stopPropagation e)
                        (state/focus! fn-id (state/client-y->world-y (.-clientY e))))
            :on-double-click (fn [e]
                               (.stopPropagation e)
                               (state/double-tap-node!
                                fn-id (state/client-y->world-y (.-clientY e))))}
           [:div.card
            [:div.row
             [:div.row-head
              {:on-mouse-down start-drag}
              [:span.kind (:type node)]
              [:span.name (or (:signature node) (:name node))]]
             (when (:source node)
               (into [:pre.source
                      {:on-mouse-down (fn [e] (.stopPropagation e))
                       ;; Let native dblclick = word-select work inside source.
                       :on-double-click (fn [e] (.stopPropagation e))}]
                     (mapcat (fn [i seg] (render-segment fn-id s i seg))
                             (range) plan)))]]
           (when-not (:root? pos)
             [:div.path
              {:on-mouse-down start-drag}
              [:span.dismiss
               {:title "dismiss this box (and everything it spawned)"
                :on-double-click (fn [e] (.stopPropagation e))
                :on-mouse-down (fn [e]
                                 (.stopPropagation e)
                                 (state/dismiss! fn-id))}
               "×"]
              (str (relative-to-root (:file node) (get-in s [:graph :root]))
                   ":" (:name node))])]))})))
