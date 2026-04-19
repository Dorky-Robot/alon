(ns alon-ui.edges-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.state :as state]
            [alon-ui.edges :as edges]))

(defn- install-graph! [graph]
  (reset! state/state
          {:graph nil :by-id {} :edges-by-from {} :width-by-id {}
           :height-by-id {} :shown {} :focused nil :trail []
           :pan-x 0 :pan-y 0 :zoom 1})
  (state/init-graph! graph))

(defn- path-nodes [hiccup]
  (->> (tree-seq sequential? seq hiccup)
       (filter #(and (vector? %) (= :path (first %))))))

;; Fixture: two top-level fns in the same file with a call from outer → helper.
;; init-graph! seeds only `outer` as shown; an edge to an unshown box must NOT
;; render. After spawn-call! brings helper in, the edge renders.

(def ^:private two-fn-graph
  {:root "/a.mjs"
   :nodes [{:id "a/outer"  :name "outer"  :type "function" :file "/a.mjs"
            :source "outer(){ helper() }"
            :start 0 :end 25 :parentId nil}
           {:id "a/helper" :name "helper" :type "function" :file "/a.mjs"
            :source "helper(){}"
            :start 30 :end 40 :parentId nil}]
   :edges [{:from "a/outer" :to "a/helper" :type "call"
            :offsetStart 9 :offsetEnd 17}]})

(deftest marker-def-is-present-and-carries-own-fill
  (install-graph! two-fn-graph)
  (let [hiccup (edges/edges)
        [tag _attrs defs] hiccup]
    (is (= :svg.edges tag))
    (is (= :defs (first defs)))
    (let [marker (second defs)]
      (is (= :marker (first marker)))
      (is (= "arrow" (:id (second marker))))
      (testing "marker triangle carries its own fill so CSS can't erase it"
        (let [[_ mattrs] (nth marker 2)]
          (is (= "#5a8aff" (:fill mattrs))))))))

(deftest edge-hidden-until-both-endpoints-are-shown
  (testing "init-graph! seeds only outer — edge to unshown helper is skipped"
    (install-graph! two-fn-graph)
    (let [hiccup (edges/edges)
          edge-paths (drop 1 (path-nodes hiccup))]
      (is (zero? (count edge-paths))
          "no box for a/helper yet, so the outer→helper path must not render")))
  (testing "after spawn-call! brings helper in, the edge renders with marker-end"
    (install-graph! two-fn-graph)
    (state/spawn-call! "a/outer" "a/helper")
    (let [hiccup (edges/edges)
          edge-paths (drop 1 (path-nodes hiccup))]
      (is (= 1 (count edge-paths))
          "exactly one outer→helper path now that both boxes are on the canvas")
      (is (= "url(#arrow)" (:marker-end (second (first edge-paths))))))))
