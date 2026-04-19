(ns alon-ui.file-card-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.state :as state]
            [alon-ui.file-card :as fc]))

(defn- install-state! [nodes shown]
  (reset! state/state
          {:by-id         (into {} (map (juxt :id identity) nodes))
           :edges-by-from {}
           :width-by-id   (into {} (map (fn [n] [(:id n) 320])) nodes)
           :height-by-id  {}
           :shown         shown
           :focused       (first (keys shown))
           :trail         []
           :pan-x 0 :pan-y 0 :zoom 1}))

;; ---- splice-calls --------------------------------------------------------
;;
;; splice-calls is the inline rendering primitive: for a function's source
;; plus its outbound edges, it returns a sequence of text/call segments
;; interleaved at call-site offsets. Call spans are clickable in the UI.

(deftest splice-calls-no-edges-returns-single-text
  (testing "source with no outbound edges renders as a single :text chunk"
    (is (= [{:kind :text :text "body"}]
           (fc/splice-calls "body" [])))))

(deftest splice-calls-nil-source-returns-empty
  (testing "nil source (unknown body) collapses to no segments"
    (is (= [] (fc/splice-calls nil [{:offsetStart 0 :offsetEnd 3}])))))

(deftest splice-calls-interleaves-text-and-calls
  (testing "one edge in the middle: [:text before] [:call span] [:text after]"
    (let [src   "aa foo() bb"
          ;; `foo(` begins at offset 3, closing `)` at offset 8. The full
          ;; call expression "foo()" spans [3, 8).
          edges [{:to "X" :offsetStart 3 :offsetEnd 8}]]
      (is (= [{:kind :text :text "aa "}
              {:kind :call :edge (first edges) :text "foo()"}
              {:kind :text :text " bb"}]
             (fc/splice-calls src edges))))))

(deftest splice-calls-sorts-by-offset
  (testing "edges given out of order are emitted in offset order"
    (let [src "aa()bb()"
          e2  {:to "Y" :offsetStart 4 :offsetEnd 8}
          e1  {:to "X" :offsetStart 0 :offsetEnd 4}
          segs (fc/splice-calls src [e2 e1])]
      (is (= [:call :call] (map :kind segs)))
      (is (= "aa()"  (:text (first segs))))
      (is (= "bb()"  (:text (second segs)))))))

(deftest splice-calls-skips-nested-inside-outer
  (testing "overlapping calls: the outer (emitted first) wins and the inner is dropped"
    (let [src "foo(bar())"
          outer {:to "FOO" :offsetStart 0  :offsetEnd 10}
          inner {:to "BAR" :offsetStart 4  :offsetEnd 9}
          segs  (fc/splice-calls src [outer inner])]
      (is (= 1 (count segs)))
      (is (= :call (:kind (first segs))))
      (is (= "foo(bar())" (:text (first segs)))))))

(deftest splice-calls-clamps-runaway-offsets
  (testing "offsets past end-of-source clamp so we don't throw"
    (let [src   "abc"
          edges [{:to "X" :offsetStart 1 :offsetEnd 999}]
          segs  (fc/splice-calls src edges)]
      (is (= [{:kind :text :text "a"}
              {:kind :call :edge (first edges) :text "bc"}]
             segs)))))

;; ---- source-height -------------------------------------------------------

(deftest source-height-blank
  (testing "empty string sources contribute no height"
    (is (zero? (fc/source-height ""))))
  (testing "nil source contributes no height"
    (is (zero? (fc/source-height nil)))))

(deftest source-height-counts-newlines
  (testing "height = top-pad + (lines * LINE-H) + bot-pad"
    ;; 3 lines ("a", "b", "c") → pad-top + 3*LINE-H + pad-bot.
    (is (= (+ fc/SOURCE-PAD-TOP (* 3 fc/LINE-H) fc/SOURCE-PAD-BOT)
           (fc/source-height "a\nb\nc")))))

;; ---- call-site-y ---------------------------------------------------------
;;
;; call-site-y is how edges.cljs anchors an arrow's tail at the exact line
;; of a call-site inside a function's body. The math needs to match what
;; CSS actually renders, because edges draw analytically (no DOM measure).

(deftest call-site-y-first-line
  (testing "offset 0 lands on the middle of the first source line"
    (install-state!
     [{:id "f" :name "f" :type "function" :parentId nil :file "/a.mjs"
       :start 0 :end 10 :source "foo()\nbar()"}]
     {"f" {:x 0 :y 0 :root? true}})
    (is (= (+ fc/ROW-H fc/SOURCE-PAD-TOP (/ fc/LINE-H 2))
           (fc/call-site-y "f" 0)))))

(deftest call-site-y-later-line
  (testing "offset on a later line advances by LINE-H per newline before it"
    (install-state!
     [{:id "f" :name "f" :type "function" :parentId nil :file "/a.mjs"
       :start 0 :end 10 :source "foo()\nbar()"}]
     {"f" {:x 0 :y 0 :root? true}})
    ;; offset 6 is the 'b' in "bar()" — 1 newline before it, so line index 1.
    (is (= (+ fc/ROW-H fc/SOURCE-PAD-TOP fc/LINE-H (/ fc/LINE-H 2))
           (fc/call-site-y "f" 6)))))

(deftest call-site-y-unshown-returns-nil
  (testing "unshown box yields nil — edges.cljs uses that to skip the path"
    (install-state!
     [{:id "f" :name "f" :type "function" :parentId nil :file "/a.mjs"
       :start 0 :end 10 :source "foo()"}]
     {})
    (is (nil? (fc/call-site-y "f" 0)))))

;; ---- width-for ----------------------------------------------------------

(deftest width-for-falls-back-to-card-width
  (testing "missing width entry falls back to CARD-WIDTH constant"
    (install-state! [] {})
    (is (= fc/CARD-WIDTH (fc/width-for "unknown")))))

(deftest width-for-uses-state-entry
  (testing "explicit width in :width-by-id wins"
    (reset! state/state
            {:by-id {} :edges-by-from {} :width-by-id {"f" 512}
             :height-by-id {} :shown {} :focused nil :trail []
             :pan-x 0 :pan-y 0 :zoom 1})
    (is (= 512 (fc/width-for "f")))))
