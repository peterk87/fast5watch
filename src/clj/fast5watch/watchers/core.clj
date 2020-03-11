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
    [fast5watch.db.queries :as q :refer [get-all-incomplete-runs get-run-by set-run-complete!]]
    [clojure.core.async :as ca :refer [>! <!]])
  (:import (java.util Date)
           (java.io File)))

; watchers stores temp local state of run watchers
(def watchers (atom {}))

(defn base-dir []
  (env :nanopore-run-base-dir))

(defn create-fast5-watcher!
  [^File watch-path]
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

(defn create-final-summary-txt-watcher!
  [^File watch-path]
  (let [watch-path-str (str watch-path)
        parent-path (.getParentFile watch-path)
        parent-path-str (str parent-path)]
    (log/debug (str "Creating watch for '" parent-path-str "/final_summary.txt'"))
    (hawk/watch! [{:paths   [parent-path-str]
                   :filter  (fn [_ {:keys [file] :as e}]
                              (log/debug (str "final summary filter file=" file " | e=" e))
                              (and (hawk/file? _ e)
                                   (hawk/created? _ e)
                                   (boolean (re-matches #"final_summary\.txt" (.getName file)))))
                   :handler (fn [_ {:keys [file]}]
                              (log/info (str "Found " file " in " parent-path-str))
                              (log/info (str "Run complete, stopping all watching on '" watch-path-str "'!"))
                              (let [seq-run (q/get-run-by-original-path watch-path-str)
                                    f5-watcher (get-in @watchers [watch-path-str :fast5-watcher])
                                    completion-watcher (get-in @watchers [watch-path-str :completion-watcher])]
                                (log/info (str "Stopping fast5 watcher on '" watch-path-str "': " f5-watcher))
                                (hawk/stop! f5-watcher)
                                (log/debug (str "Stopping completion watcher: " completion-watcher))
                                (hawk/stop! completion-watcher)
                                (log/debug (str "Completion Watcher alive? " (.isAlive (:thread completion-watcher))))
                                (q/set-run-complete! (:id seq-run))
                                (log/debug (str "Completed run id=" (:id seq-run) " | " (q/get-run-by-id (:id seq-run))))
                                (swap! watchers dissoc watch-path-str)))}])))

(comment
  (q/get-all-runs)
  (q/get-run-by {:original-path (:original-path (first (get-all-incomplete-runs)))})
  (get-all-incomplete-runs)
  (q/update-run! {:complete false} 4)
  (def some-path (File. (:original-path (q/get-run-by-id 4))))
  (->> (file-seq some-path) (filter #(.isFile %)) (map str))

  (->> @watchers)
  (->> @watchers
       vals
       first
       :completion-watcher
       :thread
       .isAlive))

(defn fast5-dir?
  [_ {:keys [file] :as e}]
  (and (hawk/directory? _ e)
       (hawk/created? _ e)
       (= (.getName file) "fast5")))

(defn setup-run-dir-watch!
  [^File file]
  (let [dir-str (str file)
        m (merge (u/run-path->map dir-str)
                 {:original-path      dir-str
                  :created-at         (Date. (.lastModified file))
                  :fast5-watcher      (create-fast5-watcher! file)
                  :completion-watcher (create-final-summary-txt-watcher! file)
                  :start-watch        (u/date-now)
                  :active             true
                  :complete           false
                  :started            (u/date-now)
                  :files              []})]
    (log/debug (str "Watch map: " m))
    (ca/go
      (let [c ((nanopore-run-processor :process) m)]
        (log/debug "c type=" (type c))
        (log/debug "dir-str=" dir-str)
        (log/debug "watcher map=" m)
        (swap! watchers assoc dir-str
               (merge m (<! c)))))))

(defn run-watcher-handler!
  [_ {:keys [file]}]
  (try
    (log/info (str "Found new run at '" file "'"))
    (setup-run-dir-watch! file)
    (catch Throwable e
      (log/error e "run-watcher-handler ERROR!")
      )))

(defn stop-all-watches!
  [w]
  (hawk/stop! w)
  (doseq [[f5w completion-watcher original-path id] (->> @watchers
                                                         vals
                                                         #(map % [:fast5-watcher
                                                                  :completion-watcher
                                                                  :original-path
                                                                  :id]))]
    (log/info (str "Stopping FAST5 watch on run id=" id " '" original-path "'"))
    (hawk/stop! f5w)
    (hawk/stop! completion-watcher)))

(defn restart-all-fast5-watches
  []
  (doseq [[f5w original-path id] (->> @watchers
                                      vals
                                      (u/vals-by-keys [:fast5-watcher :original-path :id]))]
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
                       _ (log/info (str "Starting run watcher for '" watch-dir "'..."))
                       rw (hawk/watch! [{:paths   [(base-dir)]
                                         :filter  fast5-dir?
                                         :handler run-watcher-handler!}])
                       incomplete-runs (get-all-incomplete-runs)]
                   (when (not-empty incomplete-runs)
                     (log/warn (str (count incomplete-runs) " INCOMPLETE RUNS!"))
                     (doseq [run incomplete-runs]
                       (let [file (File. ^String (:original-path run))]
                         (log/info (str "Restarting watch for incomplete run id=" (:id run) ". Path=" (str file)))
                         (setup-run-dir-watch! file))))
                   (log/info
                     "Started run watcher for '"
                     watch-dir
                     "'. Watch thread "
                     (if (.isAlive (:thread rw))
                       "IS "
                       "NOT ")
                     "alive.")
                   (log/debug "run-watcher=" rw)
                   rw)
          :stop (hawk/stop! run-watcher))

; REPL commands
(comment
  ; check what's in watchers atom
  (identity @watchers)
  ; check if run-watcher thread is alive
  (.isAlive (:thread (identity run-watcher)))
  ; restart all FAST5 watches
  (restart-all-fast5-watches)
  ; peek at run-watcher
  (identity run-watcher)
  ; stop run-watcher
  (mount.core/stop #'fast5watch.watchers.core/run-watcher)
  ; start run-watcher
  (mount.core/start #'fast5watch.watchers.core/run-watcher)
  ; check if first completion-watcher is alive
  (->> (deref watchers)
       vals
       first
       :completion-watcher
       :thread
       (.isAlive))
  ; get first completion-watcher sun.nio.fs.LinuxWatchService object
  (->> (deref watchers)
       vals
       first
       :completion-watcher
       :watcher))