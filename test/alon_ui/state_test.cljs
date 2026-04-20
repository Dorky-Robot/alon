(ns alon-ui.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.state :as state]))

(defn- install-graph! [graph]
  (reset! state/state
          {:graph nil :by-id {} :children-of {} :edges-by-from {} :width-by-id {}
           :height-by-id {} :shown {} :expanded-nested #{} :fit-mode :all
           :focused nil :trail []
           :pan-x 0 :pan-y 0 :zoom 1})
  (state/init-graph! graph))

;; Three top-level functions in one file. `outer` calls `helper`; a nested
;; ObjectMethod inside `outer` calls `sink`. The nested edge should be lifted
;; to outer's id so clicking inside outer's body can spawn sink directly.
(def ^:private sample-graph
  {:root "/a.mjs"
   :nodes [{:id "a/outer"   :name "outer"   :type "function" :file "/a.mjs"
            :source "function outer(){ walk({ M(p){ sink(p) } }); helper() }"
            :start 0   :end 60 :parentId nil}
           {:id "a/nested"  :name "M"       :type "function" :file "/a.mjs"
            :source "M(p){ sink(p) }"
            :start 20  :end 36 :parentId "a/outer"}
           {:id "a/helper"  :name "helper"  :type "function" :file "/a.mjs"
            :source "function helper(){}"
            :start 100 :end 118 :parentId nil}
           {:id "a/sink"    :name "sink"    :type "function" :file "/a.mjs"
            :source "function sink(x){}"
            :start 200 :end 217 :parentId nil}]
   :edges [{:from "a/outer"  :to "a/helper" :type "call" :offsetStart 45 :offsetEnd 53}
           {:from "a/nested" :to "a/sink"   :type "call" :offsetStart 6  :offsetEnd 13}]})

(deftest init-graph-shows-only-the-entry-function
  (install-graph! sample-graph)
  (let [s @state/state]
    (is (= ["a/outer"] (vec (keys (:shown s))))
        "only the first renderable function in the entry file starts shown")
    (is (= "a/outer" (:focused s)))
    (is (true? (:root? (get-in s [:shown "a/outer"]))))))

(deftest init-graph-lifts-nested-edges-to-renderable-ancestor
  (install-graph! sample-graph)
  (let [out (get-in @state/state [:edges-by-from "a/outer"])
        sink-edge (first (filter #(= (:to %) "a/sink") out))]
    (is (some? sink-edge)
        "nested M→sink edge is lifted so it appears on outer's outbound list")
    (is (= "a/nested" (:orig-from sink-edge))
        "lift preserves the original nested from-id for display/debug")
    (testing "offsets rebased onto outer's source"
      ;; nested.start=20, outer.start=0. Original offset 6 → rebased 20+6-0=26.
      (is (= 26 (:offsetStart sink-edge))))))

(deftest spawn-call-shares-boxes
  (install-graph! sample-graph)
  (state/spawn-call! "a/outer" "a/helper")
  (let [s @state/state]
    (is (contains? (:shown s) "a/helper"))
    (is (= "a/outer" (:opened-by (get-in s [:shown "a/helper"]))))
    (is (= "a/helper" (:focused s))))
  ;; calling spawn again on the same callee must not create a second box
  (let [before (:shown @state/state)]
    (state/spawn-call! "a/outer" "a/helper")
    (is (= (keys before) (keys (:shown @state/state)))
        "one function = one box, regardless of how many call-sites point at it")))

(deftest spawn-call-pushes-previous-focused-onto-trail
  (install-graph! sample-graph)
  (state/spawn-call! "a/outer" "a/helper")
  (is (= ["a/outer"] (:trail @state/state))
      "spawning a new box pushes the previously-focused box onto the trail"))

(deftest focus-jump-back-truncates-trail
  (install-graph! sample-graph)
  (state/spawn-call! "a/outer"  "a/helper")   ; focused=helper, trail=[outer]
  (state/spawn-call! "a/helper" "a/sink")     ; focused=sink,   trail=[outer helper]
  (state/focus! "a/outer")
  (is (= "a/outer" (:focused @state/state)))
  (is (= [] (:trail @state/state))))

(deftest focus-ignores-unshown-nodes
  (install-graph! sample-graph)
  (state/focus! "a/sink")
  (is (= "a/outer" (:focused @state/state))
      "focus! on a fn-id that isn't on the canvas must be a no-op"))

(deftest dismiss-cascades-through-opened-by
  (install-graph! sample-graph)
  (state/spawn-call! "a/outer"  "a/helper")
  (state/spawn-call! "a/helper" "a/sink")
  (state/dismiss! "a/helper")
  (let [shown (:shown @state/state)]
    (is (not (contains? shown "a/helper")))
    (is (not (contains? shown "a/sink"))
        "dismissing an opener cascades to everything it transitively spawned")
    (is (contains? shown "a/outer"))))

(deftest dismiss-refocuses-to-opener-if-focused-disappears
  (install-graph! sample-graph)
  (state/spawn-call! "a/outer" "a/helper")   ; focused=helper
  (state/dismiss! "a/helper")
  (is (= "a/outer" (:focused @state/state))
      "dismissing the focused box refocuses its opener so the trail stays coherent"))

(deftest init-graph-starts-all-nested-collapsed
  (install-graph! sample-graph)
  (is (= #{} (:expanded-nested @state/state))
      "fresh graph: every nested fn starts collapsed, Xcode-style"))

(deftest init-graph-builds-children-of-index
  (install-graph! sample-graph)
  (let [kids (get-in @state/state [:children-of "a/outer"])]
    (is (some #(= % "a/nested") kids)
        ":children-of must point outer at its nested ObjectMethod child")))

(deftest toggle-nested-flips-membership
  (install-graph! sample-graph)
  (state/toggle-nested! "a/nested")
  (is (contains? (:expanded-nested @state/state) "a/nested")
      "first toggle expands the nested")
  (state/toggle-nested! "a/nested")
  (is (not (contains? (:expanded-nested @state/state) "a/nested"))
      "second toggle collapses it back"))

;; ---- camera modes -------------------------------------------------------
;;
;; :fit-mode is persistent. :all frames the whole graph on every layout
;; change; :solo frames the currently focused node. Double-tap toggles
;; between them. Manual pan/zoom is a momentary override and does not
;; change the mode — the next real layout change reasserts it.

(deftest init-graph-starts-in-fit-all-mode
  (install-graph! sample-graph)
  (is (= :all (:fit-mode @state/state))
      "a freshly loaded graph begins in fit-all camera mode"))

(deftest double-tap-from-all-enters-solo-and-focuses-the-tapped-node
  (install-graph! sample-graph)
  (state/spawn-call! "a/outer" "a/helper")
  ;; focused is helper; double-tap outer should re-focus + enter :solo.
  (state/double-tap-node! "a/outer")
  (is (= :solo    (:fit-mode @state/state)))
  (is (= "a/outer" (:focused  @state/state))
      "entering :solo on a card makes it the focused / fit target"))

(deftest double-tap-from-solo-exits-to-all
  (install-graph! sample-graph)
  (swap! state/state assoc :fit-mode :solo)
  (state/double-tap-node! "a/outer")
  (is (= :all (:fit-mode @state/state))
      "second double-tap exits :solo → :all"))

(deftest double-tap-ignores-unshown-nodes
  (install-graph! sample-graph)
  (state/double-tap-node! "a/sink")
  (is (= :all (:fit-mode @state/state))
      "double-tapping a fn-id that isn't on the canvas is a no-op"))

(deftest pan-does-not-change-fit-mode
  (install-graph! sample-graph)
  (swap! state/state assoc :fit-mode :solo)
  (state/set-pan! 42 42)
  (is (= :solo (:fit-mode @state/state))
      "manual pan is a momentary override; :fit-mode persists until double-tap"))

(deftest zoom-does-not-change-fit-mode
  (install-graph! sample-graph)
  (swap! state/state assoc :fit-mode :solo)
  (state/set-zoom! 1.2 0 0)
  (is (= :solo (:fit-mode @state/state))
      "manual zoom is a momentary override; :fit-mode persists until double-tap"))

(deftest toggle-nested-preserves-fit-mode
  (install-graph! sample-graph)
  (swap! state/state assoc :fit-mode :solo)
  (state/toggle-nested! "a/nested")
  (is (= :solo (:fit-mode @state/state))
      "expanding a nested while solo keeps you solo so you can keep inspecting the card"))

(deftest spawn-call-in-solo-preserves-mode-and-follows-focus
  (install-graph! sample-graph)
  (swap! state/state assoc :fit-mode :solo)
  (state/spawn-call! "a/outer" "a/helper")
  (is (= :solo     (:fit-mode @state/state)))
  (is (= "a/helper" (:focused @state/state))
      "spawn-call focuses the callee; in :solo mode the fit target = :focused → helper"))

(deftest single-tap-on-new-card-in-solo-retargets-fit
  (install-graph! sample-graph)
  (state/spawn-call! "a/outer" "a/helper")  ; focused=helper
  (swap! state/state assoc :fit-mode :solo)
  (state/focus! "a/outer")
  (is (= "a/outer" (:focused @state/state))
      "tapping another card in :solo shifts focus (and thereby the camera target)")
  (is (= :solo (:fit-mode @state/state))
      "the mode itself does not change — only the fit target"))
