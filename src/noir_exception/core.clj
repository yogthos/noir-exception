(ns noir-exception.core
 (:require [clojure.string :as string]
           [hiccup.page :refer [html5]]
           [clj-stacktrace.core :refer [parse-exception]]
           [json-html.core :refer [edn->html]]))

(def project-path
  (try
    (-> (System/getProperties)
        (.get "clojure.compile.path")
        (.split "/target")
        first)
    (catch Exception _)))

(defn resource [file]
 (-> (Thread/currentThread)
     (.getContextClassLoader)
     (.getResource file)))

(def json-css
  (slurp (resource "json.human.css")))

(def exception-css
  (slurp (resource "css/noir-exception.css")))

(def get-ns-name
  (memoize
    (fn [file]
      (with-open [rdr (java.io.PushbackReader. (clojure.java.io/reader file))]
        (binding [*read-eval* false]
          (second (read rdr)))))))

(def clj? (memoize (fn [file] (-> file .getName (.endsWith ".clj")))))

(defn list-namespaces []
 (->> project-path
      clojure.java.io/file
      file-seq
      (filter clj?)
      (map get-ns-name)))

(defn layout [& content]
 (html5
   [:head
    [:title "Server Error"]
    [:style (str json-css exception-css)]]
   [:body content]))

(defn exception-item [{:keys[in-ns? fully-qualified file line]}]
 [:tr {:class (when in-ns? "mine")}
   [:td.dt file " :: " line]
   [:td.dd fully-qualified]])

(defn stack-trace [{:keys [exception causes]}]
  [:div#exception
    [:h2 "message: " [:span (or (:message exception) "N/A")]]
    [:h2 "class: " [:span "(" (:class exception) ")"]]
    [:table [:tbody (map exception-item (:trace-elems exception))]]
      (for [cause causes :while cause]
        [:div.cause
          (try
            (list
              [:h2 "Caused by: " (:class cause) " - " (:message cause)]
              [:table (map exception-item (:trimmed-elems cause))])
            (catch Throwable e))])])

(defn route-fn? [k]
 (and k (re-seq #".*--" k)))

(defn key->route-fn [k]
 (if (route-fn? k)
   (-> k
       (string/replace #"!dot!" ".")
       (string/replace #"--" "/")
       (string/replace #">" ":")
       (string/replace #"<" "*")
       (string/replace #"(POST|GET|HEAD|ANY|PUT|DELETE)" #(str (first %1) " :: ")))
   k))

(def local-ns?
  (memoize
   (fn [n]
     (when n
       (->> (list-namespaces) (filter symbol?) (map name) (some #(.contains n (name %))))))))

(defn ex-item [{anon :annon-fn func :fn nams :ns clj? :clojure f :file line :line :as ex}]
 (let [func-name (if (and anon func (re-seq #"eval" func))
                   "anon [fn]"
                   (key->route-fn func))
       ns-str (if clj?
                (if (route-fn? func)
                  (str nams " :: " func-name)
                  (str nams "/" func-name))
                (str (:method ex) "." (:class ex)))
       in-ns? (local-ns? nams)]
   {:fn func-name
    :ns nams
    :in-ns? in-ns?
    :fully-qualified ns-str
    :annon? anon
    :clj? clj?
    :file f
    :line line}))

(defn parse-ex [ex]
 (let [clj-parsed (iterate :cause (parse-exception ex))
       exception (first clj-parsed)
       causes (rest clj-parsed)]
   {:exception (assoc exception :trace-elems (map ex-item (:trace-elems exception)))
    :causes (for [cause causes :while cause]
              (assoc cause :trimmed-elems (map ex-item (:trimmed-elems cause))))}))

(defn error-html [error]
  (try
    (stack-trace (parse-ex error))
    (catch Throwable ex
      (println "There was a problem parsing the exception: ")
      (.printStackTrace ex)
      [:p "There was a problem presenting the error."])))

(defn request-html [request]
  (try
    (list
      [:h2 "Request"]
      (edn->html request))
    (catch Throwable ex
      (println "There was a problem parsing the request: ")
      (.printStackTrace ex)
      [:p "There was a problem presenting the request."])))

(defn prod-page [& {:keys [h msg]}]
  (layout
    [:div.internal-error
      [:h1 (or h "Something very bad has happened.")]
      [:p (or msg
              "We've dispatched a team of highly trained 
              gnomes to take care of the problem.")]]))

(defn dev-page [& {:keys [error request]}]
  {:pre [(or error request)]}
  (layout
    [:h1.internal-error "Internal Error: 500"]
    [:div
      (list
        (some-> error error-html)
        (some-> request request-html))]))

(defn wrap-internal-error [handler & {:keys [log error-response error-response-handler]}]
  (fn [request]
    (try (handler request)
      (catch Throwable t
        (let [info {:error t, :request request}]
          (if log (log info) (.printStackTrace t))
          {:status 500
           :headers {"Content-Type" "text/html; charset=utf-8"}
           :body (if error-response-handler
                   (error-response-handler info)
                   (or error-response (prod-page)))})))))

(defn wrap-exceptions [handler & [quiet?]]
 (if quiet?
   handler
   (fn [request]
     (try
       (handler request)
       (catch Exception e
         (.printStackTrace e)
         (println "request " request)
         {:status 500
          :headers {"Content-Type" "text/html"}
          :body (dev-page :error e :request request)})))))
