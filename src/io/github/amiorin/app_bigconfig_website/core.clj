(ns io.github.amiorin.app-bigconfig-website.core
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [org.httpkit.server :as server]
   [postal.core :as postal])
  (:gen-class)
  (:import
   [java.io StringReader]))

(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      keyword))

(defn- read-system-env []
  (->> (System/getenv)
       (map (fn [[k v]] [(keywordize k) v]))
       (into {})))

(def env (delay (read-system-env)))

(def resend-config
  (delay
    {:host (:smtp-address @env)
     :user (:smtp-username @env)
     :pass (:smtp-password @env)
     :tls  true
     :port (-> (:smtp-port @env)
               Integer/parseInt)}))

(defn send-email
  [& {:keys [subject body]}]
  (postal/send-message @resend-config
                       {:from    (:mailer-from-address @env)
                        :to      (:target-email @env)
                        :subject subject
                        :body    body}))

(comment
  (send-email :to "alberto.miorin@gmail.com"
              :subject "IMPORTANT: ClickHouse BigConfig package form"
              :body "This is sent via Resend!"))

(defn handler [{:keys [request-method uri body]}]
  (cond
    (and (= request-method :post)
         (= uri "/clickhouse")) (let [post-data (json/parse-string (slurp body) true)]
                                  (-> (send-email :subject "IMPORTANT: ClickHouse BigConfig package form"
                                                  :body (json/generate-string post-data {:pretty true}))
                                      (merge {:uri uri
                                              :post-data post-data})
                                      println)
                                  {:status 200
                                   :headers {"Content-Type" "application/json"}
                                   :body (json/generate-string {:status "success" :you-sent post-data})})

    :else
    {:status 200 :body "UP"}))

(comment
  (handler {:request-method :post
            :uri "/clickhouse"
            :body (StringReader. "{\"foo\": \"bar\"}")}))

(defn wrap-cors
  [handler]
  (fn [request]
    (if (= (:request-method request) :options)
      {:status 200
       :headers {"Access-Control-Allow-Origin" "*"
                 "Access-Control-Allow-Methods" "POST, GET, OPTIONS"
                 "Access-Control-Allow-Headers" "Content-Type, Authorization"}}
      (let [response (handler request)]
        (update response :headers merge
                {"Access-Control-Allow-Origin" "*"
                 "Access-Control-Allow-Methods" "POST, GET, OPTIONS"
                 "Access-Control-Allow-Headers" "Content-Type, Authorization"})))))

(def app (wrap-cors handler))

(defn -main [& _]
  (let [port 8080]
    (println (str "Server starting on http://localhost:" port))
    (server/run-server app {:port port})
    @(promise)))
