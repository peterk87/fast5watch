(ns fast5watch.db.queries
  (:require [fast5watch.db.core :refer [*db*]]
            [next.jdbc.sql :as sql]
            [next.jdbc :as jdbc]))

; Column vectors

(def nanopore-run-columns [:id
                           :name
                           :sample-id
                           :instrument
                           :flowcell-id
                           :original-path
                           :created-at
                           :added-at
                           :local-archive-path
                           :remote-archive-path
                           :active
                           :complete
                           :started
                           :stopped])
(def fast5-files-columns [:id
                          :filename
                          :original-path
                          :local-archive-path
                          :remote-archive-path
                          :size
                          :sha256
                          :created-at
                          :modified-at
                          :nanopore-run-id])

(defn snake-case [s] (clojure.string/replace s #"-" "_"))

(defn as-kebab-maps [rs opts]
  (let [kebab #(clojure.string/lower-case (clojure.string/replace % #"_" "-"))]
    (next.jdbc.result-set/as-unqualified-modified-maps rs (assoc opts :qualifier-fn kebab :label-fn kebab))))

; Query SQL options
(def sql-opts {:table-fn   snake-case
               :column-fn  snake-case
               :builder-fn as-kebab-maps})

; Helper functions

(defn get-one [x]
  (if (and (vector? x) (>= (count x) 1))
    (first x)
    x))

; Queries

(defn create-run!
  "Create a new Nanopore run entry"
  ([conn m]
   (sql/insert! conn :nanopore-runs
                (select-keys m nanopore-run-columns)
                sql-opts))
  ([m]
   (create-run! *db* m)))

(defn get-run-by
  "Get a Nanopore run by map of query keys and values.
  Only valid column name keys used for query.
  Returns zero or more run maps in a vector."
  ([conn kw-map]
   (let [filt-kw-map (select-keys kw-map nanopore-run-columns)]
     (if (empty? filt-kw-map)
       []
       (sql/find-by-keys conn :nanopore-runs
                         filt-kw-map
                         sql-opts))))
  ([kw-map] (get-run-by *db* kw-map)))

(defn get-run-by-original-path
  "Get a Nanopore run by the original directory path."
  ([conn ^String path]
   (let [res (get-run-by conn {:original-path path})]
     (if (and (vector? res) (= (count res) 1))
       (first res)
       res)))
  ([^String path] (get-run-by-original-path *db* path)))

(defn get-run-by-id
  ([conn id]
   (sql/get-by-id conn :nanopore-runs id sql-opts))
  ([id] (get-run-by-id *db* id)))

(defn add-run-to-db!
  "Add Nanopore run to DB and return the newly added run data as a map."
  ([conn {:keys [original-path] :as m}]
   (create-run! conn m)
   (get-run-by-original-path conn original-path))
  ([m]
   (add-run-to-db! *db* m)))

(defn get-run-id-by-path
  "Get the run id by original directory path"
  ([conn ^String path]
   (last (into []
               (map :id)
               (jdbc/plan
                 conn
                 ["select * from nanopore_runs where original_path = ?"
                  path]))))
  ([^String path]
   (get-run-id-by-path *db* path)))

(defn get-all-runs
  "Get all Nanopore runs"
  ([conn] (sql/query conn ["select * from nanopore_runs"] sql-opts))
  ([] (get-all-runs *db*)))

(defn update-run!
  "Update a Nanopore run in the DB given a Nanopore run id and map of
   values to update"
  ([conn m id]
   (sql/update! conn :nanopore-runs
                (select-keys m nanopore-run-columns)
                {:id id}
                sql-opts))
  ([m id] (update-run! *db* m id)))

(defn create-fast5!
  ([conn m] (sql/insert! conn :fast5-files
                         (select-keys m fast5-files-columns)
                         sql-opts))
  ([m] (create-fast5! *db* m)))

(defn update-fast5!
  ([conn m id]
   (sql/update! conn :fast5-files
                (select-keys m fast5-files-columns)
                {:id id}
                sql-opts))
  ([m id] (update-fast5! *db* m id)))

(defn get-fast5-by
  ([conn kw-map]
   (let [filt-kw-map (select-keys kw-map fast5-files-columns)]
     (if (empty? filt-kw-map)
       {}
       (sql/find-by-keys conn :fast5-files filt-kw-map sql-opts))))
  ([kw-map] (get-fast5-by *db* kw-map)))

(defn get-fast5-by-original-path
  ([conn ^String path]
   (let [res (get-fast5-by conn {:original-path path})]
     (get-one res)))
  ([^String path] (get-fast5-by-original-path *db* path)))

(defn get-all-fast5-for-run
  ([conn run-id]
   (sql/find-by-keys conn :fast5-files {:nanopore-run-id run-id} sql-opts))
  ([run-id] (get-all-fast5-for-run *db* run-id)))

(defn get-fast5-by-id
  ([conn id] (sql/get-by-id conn :fast5-files id sql-opts))
  ([id] (get-fast5-by-id *db* id)))

(defn get-f5-with-run-info
  ([conn original-path]
   (let [{:keys [nanopore-run-id] :as f5}
         (get-fast5-by-original-path conn original-path)]
     (assoc f5 :nanopore-run (get-run-by-id conn nanopore-run-id))))
  ([original-path]
   (get-f5-with-run-info *db* original-path)))

(defn add-fast5-to-db!
  "Add a FAST5 file to DB and return the newly added DB record as a map."
  ([conn {:keys [original-path] :as m}]
   (create-fast5! conn m)
   (get-f5-with-run-info conn original-path))
  ([m] (add-fast5-to-db! *db* m)))
