(ns alon-ui.highlight)

;; Tiny JS tokenizer for source display inside function cards. Not a real
;; parser — just left-to-right regex matching of the common token shapes.
;; Returns a vector of {:class keyword-or-nil :text string} chunks whose
;; concatenated :text exactly reproduces the input.
;;
;; Keeps alon honest against its no-build constraint: no vendored hljs /
;; prism, no npm dep, just enough regex to paint keywords, strings, numbers,
;; comments. Good enough for a viewer; not good enough to ship to a linter.

(def ^:private PATTERNS
  ;; Order matters. Comments and strings must match before identifiers
  ;; so `// foo` doesn't tokenize `foo` as an ident, etc.
  [[#"^//[^\n]*"                                     :com]
   [#"^/\*[\s\S]*?\*/"                               :com]
   [#"^`(?:\\[\s\S]|[^\\`])*`"                       :str]
   [#"^'(?:\\[\s\S]|[^\\'\n])*'"                     :str]
   [#"^\"(?:\\[\s\S]|[^\\\"\n])*\""                  :str]
   [#"^\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?"     :num]
   ;; Keyword boundary via negative lookahead over identifier chars —
   ;; \b won't do because JS identifiers include $ and _ which \b ignores.
   [#"^(?:function|class|const|let|var|return|if|else|for|while|do|break|continue|switch|case|default|typeof|instanceof|in|of|new|this|super|extends|implements|interface|import|export|from|as|async|await|yield|try|catch|finally|throw|null|undefined|true|false|void|delete|static|public|private|protected|readonly|get|set)(?![A-Za-z0-9_$])" :kw]
   [#"^[A-Za-z_$][A-Za-z0-9_$]*"                     :ident]])

(defn- longest-match
  "Run the pattern list against the head of `s`; return [class text] of the
   first matching pattern, or nil if none match."
  [s]
  (some (fn [[re cls]]
          (when-let [m (re-find re s)]
            (let [t (if (string? m) m (first m))]
              (when (and t (pos? (count t)))
                [cls t]))))
        PATTERNS))

(defn tokenize
  "Tokenize a JS-ish source string. Unrecognized single chars fall through
   with :class nil — consecutive fallthroughs are merged so whitespace runs
   don't blow up to one span per character."
  [s]
  (if-not (string? s)
    []
    (loop [rest-s s, acc []]
      (if (zero? (count rest-s))
        acc
        (if-let [[cls t] (longest-match rest-s)]
          (recur (subs rest-s (count t))
                 (conj acc {:class cls :text t}))
          (let [ch (subs rest-s 0 1)
                rest' (subs rest-s 1)]
            (if (and (seq acc) (nil? (:class (peek acc))))
              (recur rest' (update-in acc [(dec (count acc)) :text] str ch))
              (recur rest' (conj acc {:class nil :text ch})))))))))
