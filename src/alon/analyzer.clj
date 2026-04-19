(ns alon.analyzer
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]))

(defn analyze
  "Shell out to the Node-based Babel analyzer and return its JSON graph as Clojure data.
   `root` is the alon project root (where js/parse_js.mjs lives); `entry` is the file to analyze."
  [root entry]
  (let [script (str (fs/path root "js" "parse_js.mjs"))
        {:keys [exit out err]} (p/sh ["node" script entry])]
    (when-not (zero? exit)
      (binding [*out* *err*]
        (println "alon analyzer failed:")
        (println err))
      (throw (ex-info "analyzer subprocess failed" {:exit exit :stderr err})))
    (json/parse-string out true)))
