(ns io.github.bigconfig-ai.once-forms.core-test
  (:require
   [cheshire.core :as json]
   [clj-yaml.core :as yaml]
   [clojure.test :refer [deftest is testing]]
   [io.github.bigconfig-ai.once-forms.core :as core])
  (:import
   [java.io StringReader]))

(def ^:private keywordize #'core/keywordize)

(deftest keywordize-test
  (testing "upper-case env-style names get lower-cased"
    (is (= :smtp-address (keywordize "SMTP_ADDRESS")))
    (is (= :mailer-from-address (keywordize "MAILER_FROM_ADDRESS"))))
  (testing "dots and underscores both become dashes"
    (is (= :java-home (keywordize "java.home")))
    (is (= :a-b-c (keywordize "A.B_C")))))

(defn- form-request [form-name payload]
  {:uri (str "/form/" form-name)
   :request-method :post
   :path-params {:form-name form-name}
   :body (StringReader. (json/generate-string payload))})

(deftest handle-form-test
  (let [sent (atom nil)
        stub-send (fn [& args]
                    (reset! sent (apply hash-map args))
                    {:code 0 :error :SUCCESS})]
    (testing "returns a 200 JSON success response that echoes the payload"
      (with-redefs [core/send-email stub-send]
        (reset! sent nil)
        (let [payload {:name "Ada" :email "ada@example.com"}
              resp (core/handle-form (form-request "clickhouse" payload))]
          (is (= 200 (:status resp)))
          (is (= "application/json" (get-in resp [:headers "Content-Type"])))
          (is (= {:status "success" :you-sent payload}
                 (json/parse-string (:body resp) true))))))
    (testing "sends an email whose subject includes the form name and body is the YAML payload"
      (with-redefs [core/send-email stub-send]
        (reset! sent nil)
        (let [payload {:msg "hi"}]
          (core/handle-form (form-request "demo" payload))
          (is (= "IMPORTANT: demo form submitted" (:subject @sent)))
          (is (= payload (yaml/parse-string (:body @sent)))))))))

(deftest ring-handler-routes-form-test
  (testing "POST /form/:form-name is dispatched to handle-form, not the default UP handler"
    (with-redefs [core/send-email (fn [& _] {:code 0 :error :SUCCESS})]
      (let [payload {:name "Ada"}
            resp (core/ring-handler
                  {:uri "/form/clickhouse"
                   :request-method :post
                   :body (StringReader. (json/generate-string payload))})]
        (is (= 200 (:status resp)))
        (is (= "application/json" (get-in resp [:headers "Content-Type"])))
        (is (= {:status "success" :you-sent payload}
               (json/parse-string (:body resp) true)))))))

(deftest wrap-cors-test
  (testing "OPTIONS preflight short-circuits with CORS headers and does not call the handler"
    (let [called? (atom false)
          handler (core/wrap-cors (fn [_] (reset! called? true) {:status 500}))
          resp (handler {:request-method :options})]
      (is (false? @called?))
      (is (= 200 (:status resp)))
      (is (= "*" (get-in resp [:headers "Access-Control-Allow-Origin"])))
      (is (= "POST, GET, OPTIONS" (get-in resp [:headers "Access-Control-Allow-Methods"])))
      (is (= "Content-Type, Authorization" (get-in resp [:headers "Access-Control-Allow-Headers"])))))
  (testing "non-OPTIONS responses get CORS headers merged without clobbering existing ones"
    (let [inner (fn [_] {:status 201 :headers {"X-Custom" "y"} :body "ok"})
          handler (core/wrap-cors inner)
          resp (handler {:request-method :get})]
      (is (= 201 (:status resp)))
      (is (= "y" (get-in resp [:headers "X-Custom"])))
      (is (= "*" (get-in resp [:headers "Access-Control-Allow-Origin"]))))))
