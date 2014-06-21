(ns noir-exception.core
 (:require [clojure.string :as string]
           [hiccup.page :refer [html5]]
           [clj-stacktrace.core :refer [parse-exception]]))

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
    [:style exception-css]]
   [:body content]))

(defn exception-item [{:keys[in-ns? fully-qualified file line]}]
 [:tr {:class (when in-ns? "mine")}
   [:td.dt file " :: " line]
   [:td.dd fully-qualified]])

(defn stack-trace [{:keys [exception causes]}]
 (layout
   [:div#exception
    [:h1 (or (:message exception) "An exception was thrown") [:span " - (" (:class exception) ")"]]
    [:table [:tbody (map exception-item (:trace-elems exception))]]
      (for [cause causes :while cause]
        [:div.cause
          (try
            [:h3 "Caused by: " (:class cause) " - " (:message cause)]
            [:table (map exception-item (:trimmed-elems cause))]
            (catch Throwable e))])]))

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

(def internal-error
 (layout
   [:div#not-found
    [:h1 "Something very bad has happened."]
    [:p "We've dispatched a team of highly trained gnomes to take
        care of the problem."]]))

(defn wrap-internal-error [handler & [{:keys [log-fn error-response]}]]
  (fn [request]
    (try (handler request)
      (catch Throwable t
        (if log-fn (log-fn t) (.printStackTrace t))
        {:status 500
         :headers {"Content-Type" "text/html"}
         :body internal-error}))))

(defn wrap-exceptions [handler & [quiet]]
 (if quiet?
   handler
   (fn [request]
     (try
       (handler request)
       (catch Exception e
         (.printStackTrace e)
         {:status 500
          :headers {"Content-Type" "text/html"}
          :body (try
                  (stack-trace (parse-ex e))
                  (catch Throwable ex
                    (.printStackTrace ex)
                    internal-error))})))))
