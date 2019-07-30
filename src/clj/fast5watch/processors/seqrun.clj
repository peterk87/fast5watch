(ns fast5watch.processors.seqrun
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as ca :refer [go go-loop chan >! <! pipeline-blocking]]
            [fast5watch.db.queries :as db]
            [fast5watch.config :refer [env]]
            [fast5watch.util.core :as u]
            [mount.core :refer [defstate]]))

(defn add-to-chan [ch x]
  (go (>! ch x)))

(defn join-with [sep & coll]
  (apply str (interpose sep coll)))

(def join-with- (partial join-with "-"))

(defn create-archive-dirs
  [{:keys [id name sample-id started]}]
  (let [date-str (format "%1$tY-%1$tm-%1$td" started)
        dirname (join-with- date-str id name sample-id)
        local-base-dir (env :local-base-dir)
        remote-base-dir (env :remote-base-dir)]
    (when local-base-dir
      (db/update-run!
        {:local-archive-path (u/create-archive-dir local-base-dir dirname)}
        id))
    (when remote-base-dir
      (db/update-run!
        {:remote-archive-path (u/create-archive-dir remote-base-dir dirname)}
        id))
    (first (db/get-run-by {:id id}))))

(defn start [concurrency]
  (let [in-chan (chan)
        db-rec-chan (chan)
        db-rec-chan-2 (chan)
        out-chan (chan)]
    (pipeline-blocking concurrency
                       db-rec-chan
                       (map db/add-run-to-db!)
                       in-chan
                       false
                       (fn [error]
                         (log/error (str "Could not add run to DB. Error: " (.getMessage error)))))
    (pipeline-blocking concurrency
                       db-rec-chan-2
                       (map create-archive-dirs)
                       db-rec-chan
                       false
                       (fn [error]
                         (log/error (str "Could not create archive directory. Error: " (.getMessage error)))))
    (pipeline-blocking concurrency
                       out-chan
                       (map (fn [x]
                              (log/debug "RUN:" x)
                              x))
                       db-rec-chan-2
                       )
    {:stop    (fn []
                (ca/close! in-chan)
                (ca/close! db-rec-chan)
                (ca/close! db-rec-chan-2)
                (ca/close! out-chan)
                (log/info "Nanopore run processor is stopped!"))
     :process (fn [x]
                (add-to-chan in-chan x)
                out-chan)}))

(defstate nanopore-run-processor
          :start (start (env :nanopore-processor-concurrency 4))
          :stop ((nanopore-run-processor :stop)))
