(ns alon.server
  (:require [alon.analyzer :as analyzer]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.server :as http]))

(def MIME
  {"html" "text/html; charset=utf-8"
   "js"   "text/javascript; charset=utf-8"
   "mjs"  "text/javascript; charset=utf-8"
   "cljs" "text/plain; charset=utf-8"
   "css"  "text/css; charset=utf-8"
   "json" "application/json; charset=utf-8"
   "svg"  "image/svg+xml"})

(defn- ext [path]
  (let [i (.lastIndexOf path ".")]
    (when (pos? i) (subs path (inc i)))))

(defn- strip-prefix
  "Tolerate proxies that prepend a path (e.g. /proxy/4242/foo → /foo).
   Recognize our routes by suffix and rewrite to the canonical path."
  [p]
  (cond
    (or (= p "/") (= p ""))    "/"
    (str/ends-with? p "/api/graph") "/api/graph"
    (str/includes? p "/cljs/") (subs p (str/index-of p "/cljs/"))
    (str/includes? p "/js/")   (subs p (str/index-of p "/js/"))
    (str/includes? p "/public/") (subs p (str/index-of p "/public/"))
    :else p))

(defn- file-response [root rel-path]
  (let [safe (str/replace rel-path #"\.\.+" ".")
        abs  (fs/path root (subs safe 1))]
    (if (and (fs/exists? abs) (fs/regular-file? abs))
      {:status  200
       :headers {"content-type" (or (MIME (ext (str abs))) "application/octet-stream")}
       :body    (fs/read-all-bytes abs)}
      {:status 404 :body "not found"})))

(defn- render-index [root graph]
  (let [tmpl (slurp (str (fs/path root "public" "index.html")))
        injected (-> (json/generate-string graph)
                     (str/replace "<" "\\u003c"))]
    {:status  200
     :headers {"content-type" "text/html; charset=utf-8"}
     :body    (str/replace tmpl "__ALON_GRAPH__" injected)}))

(defn- handler [root entry req]
  (let [orig (:uri req)
        p    (strip-prefix orig)
        graph-fn (fn [] (analyzer/analyze root entry))
        res  (cond
               (or (= p "/") (= p ""))   (render-index root (graph-fn))
               (= p "/api/graph")        {:status  200
                                          :headers {"content-type" "application/json"}
                                          :body    (json/generate-string (graph-fn))}
               (str/starts-with? p "/cljs/")   (file-response root p)
               (str/starts-with? p "/js/")     (file-response root p)
               (str/starts-with? p "/public/") (file-response root p)
               :else {:status 404 :body "not found"})]
    (println (format "%s %s → %s %d"
                     (-> req :request-method name str/upper-case)
                     orig p (:status res)))
    res))

(defonce ^:private server (atom nil))

(defn project-root []
  ;; bin/alon cd's to the project dir before invoking bb, so cwd is reliable.
  (or (System/getenv "ALON_HOME") (System/getProperty "user.dir")))

(defn start! [{:keys [entry port]}]
  (let [root (project-root)]
    ;; Fail fast if the entry can't be analyzed at boot.
    (analyzer/analyze root entry)
    (when-let [stop @server] (stop))
    (reset! server (http/run-server #(handler root entry %) {:port port}))
    {:port port :root root}))
