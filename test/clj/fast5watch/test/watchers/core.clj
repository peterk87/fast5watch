(ns fast5watch.test.watchers.core
  (:require
    [clojure.test :refer :all]
    [mount.core :as mount]
    [fast5watch.config :refer [env]]
    [clojure.java.io :as io]
    [fast5watch.db.core :refer [*db*]]
    [me.raynes.fs :as fs]
    [digest]
    [fast5watch.watchers.core :refer [run-watcher]]
    [fast5watch.db.queries :as q])
  (:import (java.io Writer)))

(use-fixtures
    :once
    (fn [f]
      (mount/start
        #'fast5watch.config/env
        #'fast5watch.db.core/*db*
        #'fast5watch.watchers.core/run-watcher
        #'fast5watch.processors.seqrun/nanopore-run-processor
        #'fast5watch.processors.fast5/nanopore-fast5-processor)
      (f)
      (fs/delete-dir (str (env :nanopore-run-base-dir) "/x"))
      (mount/stop
        #'fast5watch.db.core/*db*
        #'fast5watch.watchers.core/run-watcher
        #'fast5watch.processors.seqrun/nanopore-run-processor
        #'fast5watch.processors.fast5/nanopore-fast5-processor)))

(defn tmp-run-dir []
  (io/file
    (env :nanopore-run-base-dir)
    "x"
    "y"
    "20190729_1200_X_Y_12345678"
    "fast5"))

(defn mk-fast5-dir [path]
  (io/make-parents (io/file path "IGNORED")))

;; TODO: add test for archiving FAST5 files [pkruczkiewicz|Tue Jul 30 14:38:49 CDT 2019]
(deftest watch-for-new-run
  (testing "creating new run with fast5s"
    (is (zero? (count (q/get-all-runs *db*))))
    (mk-fast5-dir (str (tmp-run-dir)))
    (Thread/sleep 100)
    (let [runs (q/get-all-runs *db*)
          run1 (first runs)
          exp-run {:name "x"
                   :sample-id "y"
                   :instrument "X"
                   :flowcell-id "Y"
                   :original-path (str (tmp-run-dir))
                   :active true
                   :complete false}]
      (is (= (count runs)
             1))
      (is (= (select-keys run1 (keys exp-run))
             exp-run))
      (doseq [^String fname ["a" "b" "c"]]
        (let [tmp-file (fs/temp-file "fast5watch-test")
              f5-file (io/file (:original-path exp-run) (str fname ".fast5"))]
          (with-open [fw ^Writer (io/writer tmp-file :encoding "UTF-8")]
            (.write fw fname))
          (fs/rename tmp-file f5-file)))
      (Thread/sleep 500)
      (let [f5s (q/get-all-fast5-for-run *db* (:id run1))]
        (doseq [{:keys [filename size sha256]} f5s]
          (is (= size 1))
          (is (= (digest/sha-256 (second (re-find #"(\w+)\.fast5" filename)))
                 sha256)))))))

