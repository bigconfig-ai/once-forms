(ns io.github.amiorin.app-bigconfig-website.core
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [reitit.ring :as ring]
   [org.httpkit.server :as server]
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

(defn handle-form
  [{:keys [body uri] :as req}]
  (let [form-name (get-in req [:path-params :form-name])
        post-data (json/parse-string (slurp body) true)]
    (-> (send-email :subject (format "IMPORTANT: %s form submitted" form-name)
                    :body (json/generate-string post-data {:pretty true}))
        (merge {:uri uri
                :post-data post-data})
        println)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string {:status "success" :you-sent post-data})}))

(def app-routes
  (ring/router
   ["form/:form-name" {:post handle-form}]))

(def ring-handler
  (ring/ring-handler app-routes (constantly {:status 200 :body "UP"})))

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

(def app (wrap-cors ring-handler))

(defn -main [& _]
  (let [port 8080
        stop-server (server/run-server app {:port port})
        shutdown-promise (promise)]
    (println (format "Server started on http://localhost:%s. Press Ctrl+C to stop. " port))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (println "\nShutting down...")
                                 (stop-server) ; Stops http-kit
                                 (deliver shutdown-promise true))))
    @shutdown-promise
    (println "Exit complete.")))
