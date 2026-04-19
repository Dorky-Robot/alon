(ns alon-ui.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.state :as state]))

(defn- install-graph! [graph]
  (reset! state/state
          {:graph nil :by-id {} :edges-by-from {} :width-by-id {}
           :height-by-id {} :shown {} :focused nil :trail []
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
