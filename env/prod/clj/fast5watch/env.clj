(ns fast5watch.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[fast5watch started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[fast5watch has shut down successfully]=-"))
   :middleware identity})
