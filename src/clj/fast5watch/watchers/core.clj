(ns fast5watch.watchers.core
  (:require
    [clojure.tools.logging :as log]
    [clojure.java.io :as io]
    [digest]
    [hawk.core :as hawk]
    [mount.core :refer [defstate]]
    [fast5watch.config :refer [env]]
    [fast5watch.util.core :as u]
    [fast5watch.processors.seqrun :refer [nanopore-run-processor]]
    [fast5watch.processors.fast5 :refer [nanopore-fast5-processor]]
    [clojure.core.async :as ca :refer [>! <!]])
  (:import (java.util Date)))

(def watchers (atom {}))

(defn base-dir []
  (env :nanopore-run-base-dir))

(defn date-now []
  (Date.))

(defn create-fast5-watcher!
  [^java.io.File watch-path]
  (let [watch-path-str (str watch-path)]
    (hawk/watch! [{:paths   [watch-path-str]
                   :filter  (fn [_ {:keys [file] :as e}]
                              (and (hawk/file? _ e)
                                   (hawk/created? _ e)
                                   (boolean (re-matches #".*\.fast5" (.getName file)))))
                   :handler (fn [_ {:keys [file]}]
                              (log/info (str "Adding FAST5 to DB: " file))
                              (ca/go
                                (let [c ((nanopore-fast5-processor :process) file)]
                                  (log/debug (str "TYPE c=" (type c)))
                                  (let [v (<! c)]
                                    (log/debug "F5VALUE=" v)
                                    (swap! watchers
                                           #(update-in %
                                                      [watch-path-str :files]
                                                      conj
                                                      v))))))}])))

(defn fast5-dir?
  [_ {:keys [file] :as e}]
  (and (hawk/directory? _ e)
       (hawk/created? _ e)
       (= (.getName file) "fast5")))

(defn run-watcher-handler!
  [_ {:keys [file]}]
  (log/info (str "Found new run at '" file "'"))
  (let [dir-str (str file)
        m (merge (u/run-path->map dir-str)
                 {:original-path dir-str
                  :created-at    (Date. (.lastModified file))
                  :fast5-watcher (create-fast5-watcher! file)
                  :start-watch   (date-now)
                  :active        true
                  :complete      false
                  :started       (date-now)
                  :files         []})]
    (log/debug (str "Watch map: " m))
    (ca/go
      (let [c ((nanopore-run-processor :process) m)]
        (log/debug "c type=" (type c))
        (swap! watchers assoc dir-str
               (merge m (<! c)))))))

(defn stop-all-watches
  [w]
  (hawk/stop! w)
  (doseq [[f5w original-path id] (->> @watchers
                                      vals
                                      #(map % [:fast5-watcher
                                               :original-path
                                               :id]))]

    (log/info (str "Stopping FAST5 watch on run id=" id " '" original-path "'"))
    (hawk/stop! f5w)))

(defn vals-by-keys [ks m]
  (map #(map % ks) m))

(defn restart-all-fast5-watches
  []
  (doseq [[f5w original-path id] (->> @watchers
                                      vals
                                      (vals-by-keys [:fast5-watcher :original-path :id]))
          ]
    (prn "original-path" original-path)
    (log/info (str "Stopping FAST5 watch on run id=" id " '" original-path "'"))
    (hawk/stop! f5w)
    (log/info (str "Creating new FAST5 watch on run id=" id " '" original-path "'"))
    (swap! watchers #(update-in %
                                [original-path :fast5-watcher]
                                assoc :fast5-watcher
                                (create-fast5-watcher!
                                  (io/as-file original-path))))))

(defstate run-watcher
          :start (let [watch-dir (base-dir)
                       _ (log/info (str "Starting run watcher for '" watch-dir "'..." ))
                       rw (hawk/watch! [{:paths   [(base-dir)]
                                         :filter  fast5-dir?
                                         :handler run-watcher-handler!}])]
                   (log/info
                     "Started run watcher for '"
                     watch-dir
                     "'. Watch thread "
                     (when-not (.isAlive (:thread rw))
                       "NOT ")
                     "alive.")
                   (log/debug "run-watcher=" rw)
                   rw)
          :stop (hawk/stop! run-watcher))
