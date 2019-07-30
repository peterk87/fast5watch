(ns fast5watch.test.db.core
  (:require
    [fast5watch.db.core :refer [*db*]]
    [fast5watch.db.queries :as q]
    [luminus-migrations.core :as migrations]
    [clojure.test :refer :all]
    [next.jdbc :as jdbc]
    [fast5watch.config :refer [env]]
    [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'fast5watch.config/env
      #'fast5watch.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-run
  (let [run {:sample-id     "sample-id"
             :started       #inst"2019-07-29T12:31:48.636-00:00"
             :instrument    "instrument"
             :name          "2019-07-29-name"
             :active        true
             :original-path "/path/to/base/2019-07-29-name/sample-id/20190729_1700_instrument_flowcell_deadbeef/fast5"
             :created-at    #inst"2019-07-29T12:31:48.620-00:00"
             :flowcell-id   "flowcell"}
        f5s [{:modified-at   #inst"2019-07-26T18:01:30.861-00:00",
              :size          0,
              :filename      "0.fast5",
              :original-path (str (:original-path run) "/0.fast5")
              :sha256        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
              :created-at    #inst"2019-07-26T18:01:30.861-00:00"}
             {:modified-at   #inst"2019-07-26T18:01:43.873-00:00",
              :size          49183619,
              :filename      "1.fast5",
              :original-path (str (:original-path run) "/1.fast5")
              :sha256        "1401e6e838e529a4a9be812ed5711baceea3bd80f85854832638edd979bc059d",
              :created-at    #inst"2019-07-26T18:01:43.873-00:00"}]]
    (jdbc/with-transaction
      [tx *db* {:rollback-only true}]
      (is (= run
             (select-keys
               (q/get-run-by-id tx (:id (q/create-run! tx run)))
               (keys run))))
      (is (= run
             (select-keys
               (q/get-run-by-original-path tx (:original-path run))
               (keys run))))
      (let [nid (q/get-run-id-by-path tx (:original-path run))
            [f5-1 f5-2] f5s]
        (let [f5-1-w-nid (merge {:nanopore-run-id nid} f5-1)
              f5-2-w-nid (merge {:nanopore-run-id nid} f5-2)]
          (is (= f5-1
                 (select-keys
                   (q/get-fast5-by-id
                     tx
                     (:id (q/create-fast5! tx f5-1-w-nid)))
                   (keys f5-1)))
              "After adding fast5 record to DB, should be able to get the same map back.")
          (is (= f5-2
                 (select-keys
                   (q/get-fast5-by-id
                     tx
                     (:id (q/create-fast5! tx f5-2-w-nid)))
                   (keys f5-2)))
              "After adding fast5 record to DB, should be able to get the same map back."))
        (is (= 2 (count (q/get-all-fast5-for-run tx nid)))))
      )))
