(ns fast5watch.util.core
  (:require [clojure.string :as s]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io])
  (:import (java.io File)
           (java.util Date)))

; Regular expression pattern to capture info about Nanopore sequencing run
(def regex-fast5-abspath
  #"^(.*)\/([^\/]+)\/([^\/]+)\/(\d{8}_\d{4})_([a-zA-Z0-9]+)_([a-zA-Z0-9]+)_([a-f0-9]{8})\/fast5")

(defn run-path->map
  [path]
  (zipmap [:base-path
           :name
           :sample-id
           :timestamp
           :instrument
           :flowcell-id
           :protocol-run-id-uuid8]
          (-> (re-seq regex-fast5-abspath path)
              first
              rest)))


(defn create-archive-dir
  "Create an archive directory with base from a list of dirs and subdirs.
  If an archive directory is sucessfully created or already exists and
  is writable, then the archive path string is returned.
  Otherwise nil is returned.
  e.g.
  (create-archive-dir \"/tmp\" \"a\" \"b\" \"c\")
  ;; log> Created archive directory at '/tmp/a/b/c'
  => \"tmp/a/b/c\"

  (create-archive-dir \"/cannot-write-here\" \"a\" \"b\" \"c\")
  ;; log> Directory '/cannot-write-here/a/b/c' not created!
  => nil
  "
  [& paths]
  (let [d ^File (apply io/file (map str (conj (vec paths) "DUMMY")))
        p ^File (.getParentFile d)]
    (if (io/make-parents d)
      (do
        (log/info (str "Created archive directory at '" p "'"))
        (str p))
      (if (and (.exists p) (.canWrite p))
        (do
          (log/info (str "Archive directory at '" p "' already exists!"))
          (str p))
        (log/error (str "Directory '" p "' not created!"))))))

(defn vals-by-keys
  "Get the values of some keys `ks` of a collection of maps `m`"
  [ks m]
  (map #(map % ks) m))

(defn date-now
  ^Date
  []
  (Date.))