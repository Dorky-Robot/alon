(ns alon-ui.highlight-test
  (:require [clojure.test :refer [deftest is testing]]
            [alon-ui.highlight :as hi]))

(defn- round-trip [s]
  (apply str (map :text (hi/tokenize s))))

(defn- classes [s]
  (mapv :class (hi/tokenize s)))

(deftest tokenize-round-trips
  (testing "concatenating token :text faithfully reproduces the input"
    (doseq [s ["" "x" "function foo(a, b) { return a + b }"
               "const x = 'hello'\n// bye\nlet y = 42"
               "`template ${x.y}`"]]
      (is (= s (round-trip s)) (str "round-trip failed for: " (pr-str s))))))

(deftest nil-input-is-safe
  (is (= [] (hi/tokenize nil))))

(deftest keyword-gets-kw-class
  (let [toks (hi/tokenize "function")]
    (is (= [:kw] (mapv :class toks)))
    (is (= "function" (:text (first toks))))))

(deftest identifier-starting-with-keyword-is-ident
  (testing "`functionish` must tokenize as one identifier, not kw + ident"
    (is (= [:ident] (classes "functionish")))))

(deftest identifier-with-dollar-is-not-keyword
  (testing "JS idents allow $/_; `new$thing` is one ident even though it starts with kw"
    (is (= [:ident] (classes "new$thing")))))

(deftest strings-in-three-flavors
  (is (= :str (:class (first (hi/tokenize "'hello'")))))
  (is (= :str (:class (first (hi/tokenize "\"hello\"")))))
  (is (= :str (:class (first (hi/tokenize "`template`"))))))

(deftest unterminated-string-falls-through
  (testing "an unterminated string should not eat the whole rest of the input"
    (let [toks (hi/tokenize "'unterminated")]
      ;; First token must not swallow everything — we still see the `u` start an ident.
      (is (not= ["'unterminated"] (mapv :text toks))))))

(deftest numbers-cover-int-float-exp
  (is (= :num (:class (first (hi/tokenize "42")))))
  (is (= :num (:class (first (hi/tokenize "3.14")))))
  (is (= :num (:class (first (hi/tokenize "1e10"))))))

(deftest line-comment-consumes-to-eol
  (let [toks (hi/tokenize "// foo\nbar")]
    (is (= :com   (:class (first toks))))
    (is (= "// foo" (:text (first toks))))
    ;; Newline + "bar" come after the comment, not swallowed by it.
    (is (re-find #"bar" (round-trip "// foo\nbar")))))

(deftest block-comment-is-non-greedy
  (let [toks (hi/tokenize "/* a */ /* b */")]
    (is (= :com (:class (first toks))))
    (is (= "/* a */" (:text (first toks))))))

(deftest whitespace-runs-merge-into-one-nil-token
  (testing "three spaces shouldn't produce three separate tokens"
    (let [toks (hi/tokenize "   ")]
      (is (= 1 (count toks)))
      (is (nil? (:class (first toks))))
      (is (= "   " (:text (first toks)))))))
