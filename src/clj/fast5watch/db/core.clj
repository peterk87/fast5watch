(ns fast5watch.db.core
  (:require
    [next.jdbc.connection :as connection]
    [next.jdbc.specs :as specs]
    [next.jdbc.result-set :as rs]
    [clojure.string :as s]
    [clojure.instant :refer [read-instant-date]]
    [mount.core :refer [defstate]]
    [fast5watch.config :refer [env]])
  (:import (com.zaxxer.hikari HikariDataSource)
           (org.h2.api TimestampWithTimeZone)))

(specs/instrument)

(defstate ^:dynamic *db*
          :start (connection/->pool HikariDataSource {:dbtype "h2"
                                                      :dbname (env :dbname)})
          :stop (.close *db*))

(defn h2-dt-tz->inst
  [^TimestampWithTimeZone dt-tz]
  (read-instant-date (str (s/replace (str dt-tz) #" " "T") ":00")))

(extend-protocol rs/ReadableColumn
  java.sql.Date
  (read-column-by-label ^java.time.LocalDate [^java.sql.Date v _]
    (.toLocalDate v))
  (read-column-by-index ^java.time.LocalDate [^java.sql.Date v _2 _3]
    (.toLocalDate v))
  java.sql.Timestamp
  (read-column-by-label ^java.time.Instant [^java.sql.Timestamp v _]
    (.toInstant v))
  (read-column-by-index ^java.time.Instant [^java.sql.Timestamp v _2 _3]
    (.toInstant v))
  TimestampWithTimeZone
  (read-column-by-label ^java.util.Date [^TimestampWithTimeZone v _]
    (h2-dt-tz->inst v))
  (read-column-by-index ^java.util.Date [^TimestampWithTimeZone v _2 _3]
    (h2-dt-tz->inst v)))


