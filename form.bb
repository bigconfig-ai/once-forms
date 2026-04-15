#!/usr/local/bin/bb

(require '[org.httpkit.server :as server]
         '[cheshire.core :as json])

(defn handler [{:keys [request-method uri body]}]
  (case request-method
    :post
    (let [;; Slurp the body stream and parse JSON
          post-data (json/parse-string (slurp body) true)]
      (println "Received POST to" uri "with data:" post-data)
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:status "success" :you-sent post-data})})

    ;; Default response for non-POST requests
    {:status 200 :body "UP"}))

(defn wrap-cors
  "Middleware to allow POST requests from a specific origin."
  [handler]
  (fn [request]
    ;; Handle the Preflight (OPTIONS) request
    (if (= (:request-method request) :options)
      {:status 200
       :headers {"Access-Control-Allow-Origin" "*"
                 "Access-Control-Allow-Methods" "POST, GET, OPTIONS"
                 "Access-Control-Allow-Headers" "Content-Type, Authorization"}}
      ;; Handle the actual POST/GET request
      (let [response (handler request)]
        (update response :headers merge
          {"Access-Control-Allow-Origin" "*"
           "Access-Control-Allow-Methods" "POST, GET, OPTIONS"
           "Access-Control-Allow-Headers" "Content-Type, Authorization"})))))

(def app (wrap-cors handler))

(let [port 8080]
  (println (str "Server starting on http://localhost:" port))
  (server/run-server app {:port port})
  ;; Keep the main thread alive
  @(promise))

