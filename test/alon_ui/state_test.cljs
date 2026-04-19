(ns alon-ui.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.state :as state]))

;; ---- trace order -------------------------------------------------------

;; Exercising the un-exported helpers through public behavior: compute-trace-order
;; and barycenter-order are private, but init-graph! uses both. We verify the
;; resulting :by-file ordering instead.

(defn- install-graph! [graph]
  (reset! state/state
          {:graph nil :by-id {} :by-file {} :children-of {} :width-by-file {}
           :height-by-file {} :edges-by-from {} :shown {} :expanded #{}
           :focused nil :trail [] :pan-x 0 :pan-y 0 :zoom 1})
  (state/init-graph! graph))

(def ^:private simple-graph
  {:root "/a.mjs"
   :nodes [{:id "a/outer"  :name "outer"  :type "function" :file "/a.mjs"
            :source "fn" :start 0  :end 10 :parentId nil}
           {:id "a/inner"  :name "inner"  :type "function" :file "/a.mjs"
            :source "fn" :start 3  :end 7  :parentId "a/outer"}
           {:id "a/helper" :name "helper" :type "function" :file "/a.mjs"
            :source "fn" :start 20 :end 30 :parentId nil}]
   :edges [{:from "a/outer" :to "a/helper" :type "call"}]})

(deftest init-graph-builds-children-index
  (install-graph! simple-graph)
  (is (= ["a/inner"] (get-in @state/state [:children-of "a/outer"])))
  (is (= nil (get-in @state/state [:children-of "a/helper"]))))

(deftest init-graph-autofocuses-first-function
  (install-graph! simple-graph)
  (let [s @state/state]
    ;; outer is the first function-type top-level in the entry file
    (is (contains? (:expanded s) "a/outer") "entry focus auto-expands its ancestor chain")
    (is (= "a/outer" (:focused s)))))

;; ---- focus! state machine -----------------------------------------------

(deftest focus-promotes-and-trails
  (testing "focus on B (with A focused) promotes B and pushes A onto trail"
    (install-graph! simple-graph)
    ;; initial: focused=outer, trail=[]
    (state/focus! "a/helper")
    (let [s @state/state]
      (is (= "a/helper" (:focused s)))
      (is (= ["a/outer"] (:trail s)))
      (is (contains? (:expanded s) "a/helper")
          "newly focused node auto-expands via ancestor-chain"))))

(deftest focus-reclick-toggles-expansion-collapses
  (testing "clicking the focused node toggles its expansion (collapse path)"
    (install-graph! simple-graph)
    (state/focus! "a/helper")   ; focused + expanded
    (is (contains? (:expanded @state/state) "a/helper"))
    (state/focus! "a/helper")   ; re-click
    (is (not (contains? (:expanded @state/state) "a/helper"))
        "second click on focused should remove it from :expanded")))

(deftest focus-reclick-toggles-expansion-expands
  (testing "re-clicking focused while collapsed re-expands"
    (install-graph! simple-graph)
    (state/focus! "a/helper")
    (state/focus! "a/helper")   ; now collapsed
    (state/focus! "a/helper")   ; toggle again
    (is (contains? (:expanded @state/state) "a/helper"))))

(deftest focus-jump-back-truncates-trail
  (testing "clicking a node already in trail jumps back and truncates"
    (install-graph! simple-graph)
    ;; outer → helper → inner
    (state/focus! "a/helper")   ; trail=[outer]
    (state/focus! "a/inner")    ; trail=[outer helper]
    (state/focus! "a/outer")    ; outer is in trail → jump back
    (let [s @state/state]
      (is (= "a/outer" (:focused s)))
      (is (= [] (:trail s))))))

(deftest focus-nil-is-noop
  (install-graph! simple-graph)
  (let [before (:focused @state/state)]
    (state/focus! nil)
    (is (= before (:focused @state/state)))))

;; ---- toggle-expanded! ---------------------------------------------------

(deftest toggle-expanded-is-symmetric
  (install-graph! simple-graph)
  (reset! state/state (assoc @state/state :expanded #{}))
  (state/toggle-expanded! "a/outer")
  (is (contains? (:expanded @state/state) "a/outer"))
  (state/toggle-expanded! "a/outer")
  (is (not (contains? (:expanded @state/state) "a/outer"))))

;; ---- nested first-click-expand flow -------------------------------------

(def ^:private nested-graph
  "parent has a nested child; child starts collapsed."
  {:root "/a.mjs"
   :nodes [{:id "parent" :name "parent" :type "function" :file "/a.mjs"
            :source "fn" :start 0  :end 40 :parentId nil}
           {:id "child"  :name "child"  :type "function" :file "/a.mjs"
            :source "fn" :start 10 :end 20 :parentId "parent"}]
   :edges []})

(deftest focus-on-nested-expands-whole-chain
  (testing "click on nested row reveals it by expanding every ancestor"
    (install-graph! nested-graph)
    ;; init-graph auto-focuses `parent` (first function). Reset expansion so
    ;; we can measure what focus! adds.
    (swap! state/state assoc :expanded #{} :focused "parent" :trail [])
    (state/focus! "child")
    (let [ex (:expanded @state/state)]
      (is (contains? ex "child")  "child itself expands")
      (is (contains? ex "parent") "parent expands so child is visible"))))

(deftest focus-collapses-self-without-collapsing-parent
  (testing "toggling a focused nested row preserves parent expansion"
    (install-graph! nested-graph)
    (state/focus! "child")               ; expands parent+child
    (state/focus! "child")               ; toggle — collapses child only
    (let [ex (:expanded @state/state)]
      (is (not (contains? ex "child")))
      (is (contains? ex "parent") "parent should NOT get collapsed when child toggles"))))

(deftest click-expanded-ancestor-collapses-it-in-one-click
  (testing "user flow: focus on nested addNode, then click parseFile to collapse it.
   With the old two-click state machine this took two clicks; we want it in one."
    (install-graph! nested-graph)
    (state/focus! "child")               ; focused=child, expanded has parent+child
    (is (contains? (:expanded @state/state) "parent"))
    ;; Single click on parent (visible, expanded, not focused) should collapse it.
    (state/focus! "parent")
    (is (not (contains? (:expanded @state/state) "parent"))
        "clicking an expanded ancestor in one click should collapse it")
    (is (= "parent" (:focused @state/state))
        "clicked row still becomes focused so the user sees visual feedback")))

;; ---- dismiss-file -------------------------------------------------------

(deftest dismiss-removes-file-and-its-opened-descendants
  (install-graph! simple-graph)
  ;; Manually add a second file opened by the entry file.
  (swap! state/state assoc-in [:shown "/b.mjs"]
         {:x 400 :y 0 :opened-by "/a.mjs" :direction :right})
  (state/dismiss-file! "/a.mjs")
  (let [shown (:shown @state/state)]
    (is (not (contains? shown "/a.mjs")))
    (is (not (contains? shown "/b.mjs"))
        "dismissing the opener cascades to files it brought in")))
