(ns io.github.amiorin.app-bigconfig-website.core
  (:require [org.httpkit.server :as server]
            [cheshire.core :as json]
            [clojure.string :as str]
            [postal.core :as postal])
  (:gen-class))

(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      keyword))

(defn- read-system-env []
  (->> (System/getenv)
       (map (fn [[k v]] [(keywordize k) v]))
       (into {})))

(def env (read-system-env))

(def resend-config
  {:host (:smtp-address env)
   :user (:smtp-username env)
   :pass (:smtp-password env)
   :tls  true
   :port (-> (:smtp-port env)
             Integer/parseInt)})

(defn send-email
  [& {:keys [to subject body]}]
  (postal/send-message resend-config
                       {:from    (:mailer-from-address env)
                        :to      to
                        :subject subject
                        :body    body}))

(comment
  (send-email :to "alberto.miorin@gmail.com"
              :subject "IMPORTANT: ClickHouse BigConfig package form"
              :body "This is sent via Resend!"))

(defn handler [{:keys [request-method uri body]}]
  (case request-method
    :post (let [post-data (json/parse-string (slurp body) true)]
            (println "Received POST to " uri " with data: " post-data)
            (send-email :to "alberto.miorin@gmail.com"
                        :subject "IMPORTANT: ClickHouse BigConfig package form"
                        :body (json/generate-string post-data {:pretty true}))
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body (json/generate-string {:status "success" :you-sent post-data})})
    {:status 200 :body "UP"}))

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
