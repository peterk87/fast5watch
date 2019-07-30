(ns fast5watch.test.handler
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :refer :all]
    [fast5watch.handler :refer :all]
    [fast5watch.db.queries :as q]
    [fast5watch.db.core :refer [*db*]]
    [fast5watch.config :refer [env]]
    [muuntaja.core :as m]
    [mount.core :as mount]
    [next.jdbc.sql :as sql]))

(def edn-format "application/edn")

(def run1 {:sample-id     "run1"
           :started       #inst"2019-07-29T12:31:48.636-00:00"
           :instrument    "instrument"
           :name          "2019-07-29-run1"
           :active        true
           :original-path "/path/to/base/2019-07-29-run1/run1/20190729_1700_instrument_flowcell_deadbeef/fast5"
           :created-at    #inst"2019-07-29T12:31:48.620-00:00"
           :flowcell-id   "flowcell"})

(def f5s [{:modified-at   #inst"2019-07-26T18:01:30.861-00:00",
           :size          0,
           :filename      "0.fast5",
           :original-path (str (:original-path run1) "/0.fast5")
           :sha256        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
           :created-at    #inst"2019-07-26T18:01:30.861-00:00"}
          {:modified-at   #inst"2019-07-26T18:01:43.873-00:00",
           :size          49183619,
           :filename      "1.fast5",
           :original-path (str (:original-path run1) "/1.fast5")
           :sha256        "1401e6e838e529a4a9be812ed5711baceea3bd80f85854832638edd979bc059d",
           :created-at    #inst"2019-07-26T18:01:43.873-00:00"}])

(defn fill-db []
  (q/create-run! *db* run1)
  (let [{:keys [id]} (first (q/get-run-by *db* {:name (:name run1)}))]
    (doseq [f5 f5s]
      (q/create-fast5!
        *db*
        (merge
          {:nanopore-run-id id}
          f5)))))

(defn empty-db []
  (sql/delete! *db* :fast5-files ["id >= ?" 1] q/sql-opts)
  (sql/delete! *db* :nanopore-runs ["id >= ?" 1] q/sql-opts))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'fast5watch.config/env
                 #'fast5watch.db.core/*db*
                 #'fast5watch.handler/app-routes)
    (fill-db)
    (f)
    (empty-db)))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 301 (:status response)))))
  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response)))))
  (testing "ping"
    (let [resp ((app) (request :get "/api/ping"))]
      (is (= 200 (:status resp)))
      (is (= "pong" (:message (m/decode-response-body resp))))))
  (testing "get runs"
    (let [response ((app) (request :get "/api/runs"))]
      (is (= 200 (:status response))))
    (let [response ((app)
                    (-> (request :get "/api/run/9999")
                        (header "Accept" "application/edn")))]
      (is (= 200 (:status response)))
      (is (= {:id 9999 :data nil} (m/decode-response-body response))))
    (let [response ((app)
                    (-> (request :get (str "/api/runs?name=" (:name run1)))
                        (header "Accept" "application/edn")))]
      (is (= 200 (:status response)))
      (is (= run1 (-> response
                      (m/decode-response-body)
                      :data
                      (first)
                      (select-keys (keys run1)))))))
  (testing "get fast5s"
    (let [{:keys [id]} (-> ((app) (request :get (str "/api/runs?name=" (:name run1))))
                           (m/decode-response-body)
                           :data
                           (first))
          response ((app)
                    (-> (request :get (str "/api/run/" id "/fast5s"))
                        (header "Accept" edn-format)))]
      (is (= 200 (:status response)))
      (let [fast5s (-> response
                       (m/decode-response-body)
                       :data)]
        (is (= 2 (count fast5s)))
        (is (= f5s (->> fast5s
                        (map #(select-keys % (keys (first f5s))))
                        vec)))))))
