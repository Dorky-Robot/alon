(ns alon-ui.edges-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.state :as state]
            [alon-ui.edges :as edges]))

(defn- install-graph! [graph]
  (reset! state/state
          {:graph nil :by-id {} :by-file {} :children-of {} :width-by-file {}
           :height-by-file {} :edges-by-from {} :shown {} :expanded #{}
           :focused nil :trail [] :pan-x 0 :pan-y 0 :zoom 1})
  (state/init-graph! graph))

(def ^:private two-file
  {:root "/a.mjs"
   :nodes [{:id "a/outer"  :name "outer"  :type "function" :file "/a.mjs"
            :source "fn" :start 0  :end 10 :parentId nil}
           {:id "b/helper" :name "helper" :type "function" :file "/b.mjs"
            :source "fn" :start 0  :end 10 :parentId nil}]
   :edges [{:from "a/outer" :to "b/helper" :type "call" :offsetStart 2}]})

(defn- path-nodes [hiccup]
  (->> (tree-seq sequential? seq hiccup)
       (filter #(and (vector? %) (= :path (first %))))))

(deftest edges-component-renders-marker-def
  (install-graph! two-file)
  ;; Both files must be on the canvas for the edge to render — outer is
  ;; auto-focused at init-graph! and bring-in-around! pulls in helper's file.
  (let [hiccup (edges/edges)
        [tag _attrs defs] hiccup]
    (is (= :svg.edges tag))
    (is (= :defs (first defs)))
    (let [marker (second defs)]
      (is (= :marker (first marker)))
      (is (= "arrow" (:id (second marker))))
      (testing "marker triangle carries its own fill so CSS can't erase it"
        (let [[_ mattrs] (nth marker 2)]
          (is (= "#5a8aff" (:fill mattrs))))))
    (let [paths (path-nodes hiccup)
          ;; First :path is the marker triangle inside <defs>; edge paths come after.
          edge-paths (drop 1 paths)]
      (is (pos? (count edge-paths)) "expected at least one edge path")
      (testing "every rendered edge path has marker-end set"
        (doseq [p edge-paths]
          (is (= "url(#arrow)" (:marker-end (second p)))
              (str "edge missing marker-end: " (pr-str p))))))))
