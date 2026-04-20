(ns alon-ui.file-card-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.state :as state]
            [alon-ui.file-card :as fc]))

(defn- install-state! [nodes shown expanded-nested]
  (let [children-of (reduce (fn [m n]
                              (if-let [p (:parentId n)]
                                (update m p (fnil conj []) (:id n))
                                m))
                            {} nodes)]
    (reset! state/state
            {:by-id           (into {} (map (juxt :id identity) nodes))
             :children-of     children-of
             :edges-by-from   {}
             :width-by-id     (into {} (map (fn [n] [(:id n) 320])) nodes)
             :height-by-id    {}
             :shown           shown
             :expanded-nested (set expanded-nested)
             :fit-mode        :all
             :focused         (first (keys shown))
             :trail           []
             :pan-x 0 :pan-y 0 :zoom 1})))

(defn- kinds [plan] (mapv :kind plan))

;; ---- plan-for: calls only (no nested) -----------------------------------

(deftest plan-for-no-edges-is-single-text
  (is (= [{:kind :text :os 0 :oe 4 :text "body" :line-start 0}]
         (fc/plan-for "body" [] []))))

(deftest plan-for-interleaves-calls
  (testing "one call in the middle splits into text, call, text segments"
    (let [src "aa foo() bb"
          edges [{:to "X" :offsetStart 3 :offsetEnd 8}]
          plan (fc/plan-for src edges [])]
      (is (= [:text :call :text] (kinds plan)))
      (is (= "aa "   (:text (nth plan 0))))
      (is (= "foo()" (:text (nth plan 1))))
      (is (= " bb"   (:text (nth plan 2)))))))

(deftest plan-for-call-ranges-sort-by-offset
  (let [src "aa()bb()"
        e2  {:to "Y" :offsetStart 4 :offsetEnd 8}
        e1  {:to "X" :offsetStart 0 :offsetEnd 4}
        plan (fc/plan-for src [e2 e1] [])]
    (is (= [:call :call] (kinds plan)))
    (is (= "aa()" (:text (first plan))))
    (is (= "bb()" (:text (second plan))))))

(deftest plan-for-clamps-runaway-offsets
  (testing "offsetEnd past end-of-source is skipped, not thrown"
    (let [src   "abc"
          edges [{:to "X" :offsetStart 1 :offsetEnd 999}]
          plan  (fc/plan-for src edges [])]
      ;; Out-of-range edge is dropped; we get the raw text as a single seg.
      (is (= [:text] (kinds plan)))
      (is (= "abc" (:text (first plan)))))))

;; ---- plan-for: nested collapse ------------------------------------------

(deftest plan-for-collapses-nested-by-default
  (testing "a nested fn range becomes a one-line `foo() { … }` pill"
    (let [src ";\n  function inner() {\n    doit();\n  }\n;"
          ;; inner starts at offset 4 ("function"...), ends at 37 (after closing }).
          os  4
          oe  37
          nested-kids [{:node {:id "inner" :name "inner" :type "function"}
                        :os os :oe oe :collapsed? true}]
          plan (fc/plan-for src [] nested-kids)]
      (is (= [:text :nested-collapsed :text] (kinds plan)))
      (let [pill (nth plan 1)]
        (is (= "function inner() { … }" (:text pill)))
        (is (= "inner" (:id (:node pill))))))))

(deftest plan-for-expanded-emits-zero-length-expander
  (testing "an expanded nested emits a ▾ expander at its start so users can re-collapse it IDE-style"
    (let [src "; function inner() { doit() } ;"
          ;; inner.os = 2 ("function"), inner.oe = 30 (after closing })
          nested-kids [{:node {:id "inner" :name "inner" :type "function"}
                        :os 2 :oe 30 :collapsed? false}]
          plan (fc/plan-for src [] nested-kids)
          expander (some #(when (= :nested-expander (:kind %)) %) plan)]
      (is (some? expander)
          "expanded nested must emit a :nested-expander segment")
      (is (= (:os expander) (:oe expander))
          "expander is zero-length — it doesn't consume source, just hangs a click target")
      (is (= 2 (:os expander))
          "expander sits at the nested's start offset, just before the `function` keyword")
      (is (= "▾" (:text expander))
          "displayed glyph is the open-fold triangle"))))

(deftest plan-for-collapsed-pill-is-one-display-line
  (testing "line-start after a collapsed nested reflects the pill's single line, not the original body's multi-line length"
    (let [src "A\nfunction inner() {\n  x()\n  y()\n}\nB"
          ;; inner range: start at offset 2 ("function"), end right after closing brace at offset 34.
          nested-kids [{:node {:id "inner" :name "inner" :type "function"}
                        :os 2 :oe 34 :collapsed? true}]
          plan (fc/plan-for src [] nested-kids)
          [seg-before seg-nested seg-after] plan]
      (is (= 0 (:line-start seg-before)))
      ;; seg-before is "A\n" — one newline → nested starts on line 1.
      (is (= 1 (:line-start seg-nested)))
      ;; After the pill (single line, no newline inside), the tail seg
      ;; starts on the SAME line as the pill. The tail itself is "\nB",
      ;; whose first char is the newline that ends the pill's line.
      (is (= 1 (:line-start seg-after))))))

(deftest plan-for-suppresses-calls-inside-collapsed-nesteds
  (testing "a call-site inside a collapsed nested's range is not emitted as a :call segment"
    (let [src "outer() { function inner() { sink() } }"
          ;; inner range: offset 10 ("function") through 38 (just before outer's closing).
          nested-kids [{:node {:id "inner" :name "inner" :type "function"}
                        :os 10 :oe 38 :collapsed? true}]
          ;; sink() lives inside inner at offset 29..35.
          edges [{:to "sink" :offsetStart 29 :offsetEnd 35}]
          plan (fc/plan-for src edges nested-kids)]
      (is (not (some #(= :call (:kind %)) plan))
          "call inside collapsed nested must not appear as a clickable span")
      (is (some #(= :nested-collapsed (:kind %)) plan)))))

(deftest plan-for-keeps-calls-in-expanded-nesteds
  (testing "when a nested is expanded, calls inside its body render normally and it gets a re-collapse expander"
    (let [inner {:id "inner" :name "inner" :type "function"}
          src "outer() { function inner() { sink() } }"
          nested-kids [{:node inner :os 10 :oe 38 :collapsed? false}]
          edges [{:to "sink" :offsetStart 29 :offsetEnd 35}]
          plan (fc/plan-for src edges nested-kids)]
      (is (some #(= :nested-expander (:kind %)) plan)
          "expanded nested must contribute a re-collapse expander")
      (is (some #(= :call (:kind %)) plan)
          "calls inside an expanded nested are still clickable"))))

;; ---- call-site-y --------------------------------------------------------

(deftest call-site-y-first-line
  (testing "offset 0 lands on the middle of the first source line"
    (install-state!
     [{:id "f" :name "f" :type "function" :parentId nil :file "/a.mjs"
       :start 0 :end 10 :source "foo()\nbar()"}]
     {"f" {:x 0 :y 0 :root? true}}
     #{})
    (is (= (+ fc/ROW-H fc/SOURCE-PAD-TOP (/ fc/LINE-H 2))
           (fc/call-site-y "f" 0)))))

(deftest call-site-y-later-line
  (testing "offset on a later line advances by LINE-H per newline before it"
    (install-state!
     [{:id "f" :name "f" :type "function" :parentId nil :file "/a.mjs"
       :start 0 :end 10 :source "foo()\nbar()"}]
     {"f" {:x 0 :y 0 :root? true}}
     #{})
    ;; offset 6 is the 'b' in "bar()" — 1 newline before it, so line index 1.
    (is (= (+ fc/ROW-H fc/SOURCE-PAD-TOP fc/LINE-H (/ fc/LINE-H 2))
           (fc/call-site-y "f" 6)))))

(deftest call-site-y-inside-collapsed-nested-anchors-on-pill
  (testing "a call whose offset falls in a collapsed nested's range anchors at the pill's line"
    (install-state!
     [{:id "outer" :name "outer" :type "function" :parentId nil :file "/a.mjs"
       :start 0 :end 60
       :source "A\nfunction inner() {\n  doit()\n}\n"}
      ;; inner is a child of outer; its range (absolute) overlaps the source.
      {:id "inner" :name "inner" :type "function" :parentId "outer" :file "/a.mjs"
       :start 2 :end 31 :source "function inner() {\n  doit()\n}"}]
     {"outer" {:x 0 :y 0 :root? true}}
     #{}) ; no expansion → inner is collapsed
    ;; offset 23 (inside "doit()") falls inside inner's range. The pill
    ;; sits on line 1 (after "A\n"), so we expect pill-line y.
    (is (= (+ fc/ROW-H fc/SOURCE-PAD-TOP fc/LINE-H (/ fc/LINE-H 2))
           (fc/call-site-y "outer" 23)))))

(deftest call-site-y-inside-expanded-nested-uses-body-line
  (testing "expanding the nested reveals the real call line — y reflects newlines up to the call"
    (install-state!
     [{:id "outer" :name "outer" :type "function" :parentId nil :file "/a.mjs"
       :start 0 :end 60
       :source "A\nfunction inner() {\n  doit()\n}\n"}
      {:id "inner" :name "inner" :type "function" :parentId "outer" :file "/a.mjs"
       :start 2 :end 31 :source "function inner() {\n  doit()\n}"}]
     {"outer" {:x 0 :y 0 :root? true}}
     #{"inner"}) ; inner expanded → body lines count for real
    ;; "doit()" starts at offset 23 in outer.source. Newlines before it:
    ;; "A\nfunction inner() {\n  doit()..." → 2 newlines → line 2.
    (is (= (+ fc/ROW-H fc/SOURCE-PAD-TOP (* 2 fc/LINE-H) (/ fc/LINE-H 2))
           (fc/call-site-y "outer" 23)))))

(deftest call-site-y-unshown-returns-nil
  (install-state!
   [{:id "f" :name "f" :type "function" :parentId nil :file "/a.mjs"
     :start 0 :end 10 :source "foo()"}]
   {}
   #{})
  (is (nil? (fc/call-site-y "f" 0))))

;; ---- width-for ---------------------------------------------------------

(deftest width-for-falls-back-to-card-width
  (install-state! [] {} #{})
  (is (= fc/CARD-WIDTH (fc/width-for "unknown"))))

(deftest width-for-uses-state-entry
  (reset! state/state
          {:by-id {} :children-of {} :edges-by-from {}
           :width-by-id {"f" 512}
           :height-by-id {} :shown {} :expanded-nested #{} :fit-mode :all
           :focused nil :trail [] :pan-x 0 :pan-y 0 :zoom 1})
  (is (= 512 (fc/width-for "f"))))
