(ns fast5watch.config
  (:require
    [cprop.core :refer [load-config]]
    [cprop.source :as source]
    [mount.core :refer [args defstate]]
    [clojure.java.io :as io]
    [fast5watch.util.core :refer [create-archive-dir]]
    [clojure.tools.logging :as log]))

(defn abspath-exists? [path]
  (let [f (io/file path)]
    (and (.isAbsolute f) (.exists f))))

(defn check-cfg-base-dir [cfg kw]
  (let [bd (cfg kw)]
    (if-let [bd-file (io/file bd)]
      (do
        (when (and (.isAbsolute bd-file) (not (.exists bd-file)))
          (create-archive-dir bd))
        (when-not (abspath-exists? bd)
          (throw
            (Exception. (str kw " must exist and be an absolute path! '" bd "'")))))
      (log/warn (str "No archiving base dir for " kw " specified!")))))

(defstate env
          :start
          (dosync
            (let [cfg (load-config
                        :merge
                        [(args)
                         (source/from-system-props)
                         (source/from-env)])]
              (check-cfg-base-dir cfg :local-base-dir)
              (check-cfg-base-dir cfg :remote-base-dir)
              cfg)))
