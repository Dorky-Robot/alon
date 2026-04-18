(ns alon.main
  (:require [alon.server :as server]
            [babashka.fs :as fs]))

(defn parse-args [args]
  (loop [acc {:port 4242} [a & more] args]
    (cond
      (nil? a)             acc
      (= a "--port")       (recur (assoc acc :port (Integer/parseInt (first more))) (rest more))
      (re-matches #"--port=(\d+)" a)
      (recur (assoc acc :port (Integer/parseInt (second (re-matches #"--port=(\d+)" a)))) more)
      :else                (recur (assoc acc :entry a) more))))

(defn -main [& args]
  (let [{:keys [entry port]} (parse-args args)]
    (when-not entry
      (binding [*out* *err*]
        (println "usage: bb start <entry-file> [--port N]"))
      (System/exit 2))
    (let [abs (str (fs/absolutize entry))]
      (when-not (fs/exists? abs)
        (binding [*out* *err*]
          (println "alon: file not found:" abs))
        (System/exit 1))
      (server/start! {:entry abs :port port})
      (println (str "alon: serving http://localhost:" port))
      (println (str "alon: exploring " abs))
      @(promise))))
