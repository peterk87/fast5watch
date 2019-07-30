(ns fast5watch.processors.fast5
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as ca :refer [go go-loop chan >! <! pipeline-blocking]]
            [fast5watch.db.queries :as db]
            [fast5watch.config :refer [env]]
            [digest]
            [mount.core :refer [defstate]]
            [clojure.java.io :as io])
  (:import (java.util Date)
           (java.io File)))

(defn copy-file-to-dir
  "Copy a file to a destination directory similar to `cp file.txt dir/`"
  ^File
  [source-path dest-dir-path]
  (let [source-file (io/file source-path)
        dest-file (io/file dest-dir-path (.getName source-file))]
    (io/copy source-file dest-file)
    dest-file))

(defn check-sha256
  [^String sha256 f]
  (= sha256 (digest/sha-256 (io/as-file f))))

(defn add-to-chan [ch x]
  (go (>! ch x)))

(defn fast5-file-map [^File f5file]
  (let [modified-at (Date. (.lastModified f5file))
        parent-dir ^String (.getParent f5file)]
    {:filename        (.getName f5file)
     :original-path   (str f5file)
     :nanopore-run-id (db/get-run-id-by-path parent-dir)
     :size            (.length f5file)
     :sha256          (digest/sha-256 f5file)
     :created-at      modified-at
     :modified-at     modified-at}))

(defn archive-fast5!
  [{:keys [id
           nanopore-run-id
           original-path
           sha256]}]
  (let [{:keys [local-archive-path
                remote-archive-path]}
        (db/get-run-by-id nanopore-run-id)
        f5-file-src (io/file original-path)]
    (doseq [[k archive-dir] [[:local-archive-path local-archive-path]
                             [:remote-archive-path remote-archive-path]]]
      (when archive-dir
        (let [dest-file (copy-file-to-dir f5-file-src archive-dir)]
          (assert (check-sha256 sha256 dest-file)
                  (str "sha256 for archived file '"
                       dest-file
                       "' different!"))
          (db/update-fast5! {k (str dest-file)} id))))
    (db/get-f5-with-run-info original-path)))

(defn start [concurrency]
  (let [in-chan (chan)
        out-chan (chan)]
    (pipeline-blocking concurrency
                       out-chan
                       (map (comp
                              archive-fast5!
                              db/add-fast5-to-db!
                              fast5-file-map))
                       in-chan
                       false
                       (fn [error]
                         (log/error (str "Could not add FAST5 to DB. Error: " (.getMessage error)))
                         (log/error error)))
    {:stop    (fn []
                (ca/close! in-chan)
                (ca/close! out-chan)
                (log/info "Nanopore FAST5 processor is stopped!"))
     :process (fn [x]
                (add-to-chan in-chan x)
                out-chan)}))

(defstate nanopore-fast5-processor
          :start (start (env :nanopore-processor-concurrency 4))
          :stop ((nanopore-fast5-processor :stop)))
