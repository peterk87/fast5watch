(ns fast5watch.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [fast5watch.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[fast5watch started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[fast5watch has shut down successfully]=-"))
   :middleware wrap-dev})
