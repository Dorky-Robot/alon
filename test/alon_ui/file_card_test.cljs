(ns alon-ui.file-card-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.state :as state]
            [alon-ui.file-card :as fc]))

;; Utilities ----------------------------------------------------------------

(defn- mk-node [id name start end source & {:keys [parent type file]
                                              :or   {type "function"
                                                     file "/fake/a.mjs"}}]
  {:id id :name name :type type :source source :signature nil
   :start start :end end :parentId parent :file file})

(defn- install-state! [nodes expanded]
  (let [by-id       (into {} (map (juxt :id identity) nodes))
        children-of (->> nodes
                         (filter :parentId)
                         (group-by :parentId)
                         (reduce-kv
                          (fn [m pid xs] (assoc m pid (mapv :id (sort-by :start xs))))
                          {}))
        by-file     (->> nodes
                         (remove :parentId)
                         (group-by :file)
                         (reduce-kv (fn [m f xs] (assoc m f (vec (sort-by :start xs)))) {}))]
    (reset! state/state
            {:by-id       by-id
             :by-file     by-file
             :children-of children-of
             :expanded    (set expanded)
             :shown       {"/fake/a.mjs" {:x 0 :y 0 :root? true}}
             :width-by-file {"/fake/a.mjs" 320}})))

;; ---- splice-source -------------------------------------------------------

(deftest splice-source-leaf-emits-single-tail
  (testing "leaf with no children: tail text falls through as one :text segment
   (the caller never hits container mode for leaves — it keys off (seq kids))"
    (let [leaf (mk-node "leaf" "leaf" 0 10 "body")]
      (install-state! [leaf] #{"leaf"})
      (is (= [[:text "body"]] (fc/splice-source leaf))))))

(deftest splice-source-blank-leaf-emits-nothing
  (testing "purely whitespace source gets dropped by meaningful-text?"
    (let [leaf (mk-node "leaf" "leaf" 0 4 "    ")]
      (install-state! [leaf] #{"leaf"})
      (is (= [] (fc/splice-source leaf))))))

(deftest splice-source-interleaves-text-and-children
  (testing "parent source is sliced into [:text ...] around captured child holes"
    (let [;; parent source: "AAAAkid1BBBBkid2CCCC" — child offsets are absolute
          parent (mk-node "p" "p" 0 20 "AAAAkid1BBBBkid2CCCC")
          k1     (mk-node "k1" "k1" 4 8 "kid1" :parent "p")
          k2     (mk-node "k2" "k2" 12 16 "kid2" :parent "p")]
      (install-state! [parent k1 k2] #{"p"})
      (let [segs (fc/splice-source parent)]
        (is (= [[:text "AAAA"] [:child "k1"]
                [:text "BBBB"] [:child "k2"]
                [:text "CCCC"]]
               segs))))))

(deftest splice-source-skips-blank-interstitials
  (testing "whitespace-only text between children is dropped"
    (let [parent (mk-node "p" "p" 0 16 "    kid1\n\n  kid2")
          k1     (mk-node "k1" "k1" 4 8 "kid1" :parent "p")
          k2     (mk-node "k2" "k2" 12 16 "kid2" :parent "p")]
      (install-state! [parent k1 k2] #{"p"})
      ;; No meaningful text before kid1 or between kids — only children emit
      (is (= [[:child "k1"] [:child "k2"]]
             (fc/splice-source parent))))))

;; ---- row-height ---------------------------------------------------------

(deftest row-height-collapsed
  (testing "collapsed row is exactly ROW-H"
    (let [n (mk-node "x" "x" 0 4 "body")]
      (install-state! [n] #{})
      (is (= fc/ROW-H (fc/row-height n))))))

(deftest row-height-expanded-leaf
  (testing "expanded leaf adds source-height to row-head"
    (let [n (mk-node "x" "x" 0 4 "one")]
      (install-state! [n] #{"x"})
      (is (= (+ fc/ROW-H (fc/source-height "one"))
             (fc/row-height n))))))

(deftest row-height-expanded-container-uses-segments
  (testing "container mode accounts for CONTAINER-PAD on both ends + gap between segs"
    (let [parent (mk-node "p" "p" 0 12 "AAAAkid1BBBB")
          k1     (mk-node "k1" "k1" 4 8 "kid1" :parent "p")]
      (install-state! [parent k1] #{"p"})
      ;; segs = [:text "AAAA"] [:child k1] [:text "BBBB"] = 3 segments
      (let [expected-body (+ fc/CONTAINER-PAD
                             (fc/source-height "AAAA")
                             (fc/row-height k1)
                             (fc/source-height "BBBB")
                             (* 2 fc/CONTAINER-GAP)
                             fc/CONTAINER-PAD)]
        (is (= (+ fc/ROW-H expected-body)
               (fc/row-height parent)))))))

;; ---- row-top-y ---------------------------------------------------------

(deftest row-top-y-top-level-siblings-stack
  (testing "top-level sibling ys sum preceding row-heights"
    (let [a (mk-node "a" "a" 0  4 "aa")   ; row-head only (collapsed)
          b (mk-node "b" "b" 4  8 "bb")
          c (mk-node "c" "c" 8 12 "cc")]
      (install-state! [a b c] #{})
      (is (= 0                 (fc/row-top-y "a")))
      (is (= fc/ROW-H          (fc/row-top-y "b")))
      (is (= (* 2 fc/ROW-H)    (fc/row-top-y "c"))))))

(deftest row-top-y-nested-under-expanded-parent
  (testing "nested row lives at parent-top + ROW-H + CONTAINER-PAD + preceding splice height"
    (let [parent (mk-node "p" "p" 0 12 "AAAAkid1BBBB")
          k1     (mk-node "k1" "k1" 4 8 "kid1" :parent "p")]
      (install-state! [parent k1] #{"p"})
      ;; segs: [:text "AAAA"] [:child k1] [:text "BBBB"]
      ;; y-before-child walks: seg 0 (:text "AAAA") adds source-height + gap;
      ;; then seg 1 is our :child — stop.
      (let [expected (+ 0                              ; parent top
                        fc/ROW-H                       ; past parent head
                        fc/CONTAINER-PAD               ; body top padding
                        (fc/source-height "AAAA")      ; preceding text
                        fc/CONTAINER-GAP)]             ; gap after it
        (is (= expected (fc/row-top-y "k1")))))))

;; ---- row-depth / row-x-inset -------------------------------------------

(deftest row-depth-counts-ancestors
  (let [a (mk-node "a" "a" 0 10 "x")
        b (mk-node "b" "b" 2  4 "y" :parent "a")
        c (mk-node "c" "c" 3  3 "z" :parent "b")]
    (install-state! [a b c] #{"a" "b"})
    (is (= 0 (fc/row-depth "a")))
    (is (= 1 (fc/row-depth "b")))
    (is (= 2 (fc/row-depth "c")))
    (is (= 0                       (fc/row-x-inset "a")))
    (is (= fc/CONTAINER-PAD        (fc/row-x-inset "b")))
    (is (= (* 2 fc/CONTAINER-PAD)  (fc/row-x-inset "c")))))
