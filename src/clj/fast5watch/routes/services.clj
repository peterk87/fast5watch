(ns fast5watch.routes.services
  (:require
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [fast5watch.middleware.formats :as formats]
    [fast5watch.middleware.exception :as exception]
    [ring.util.http-response :refer :all]
    [fast5watch.db.queries :as db]
    [clojure.java.io :as io])
  (:import (java.io File)))

(defn file-readable? [f]
  (when (and f (= (type f) File))
    (.canRead f)))

(defn service-routes []
  ["/api"
   {:coercion   spec-coercion/coercion
    :muuntaja   formats/instance
    :swagger    {:id ::api}
    :middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware]}

   ;; swagger documentation
   ["" {:no-doc  true
        :swagger {:info {:title       "my-api"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url    "/api/swagger.json"
              :config {:validator-url nil}})}]]
   ["/ping"
    {:get (constantly (ok {:message "pong"}))}]
   ["/runs"
    {:get {:summary    "Get info on all runs in DB"
           :parameters {:query {any? any?}}
           :responses  {200 {:body {:data [{}]}}}
           :handler    (fn [x]
                         (let [query-params (get-in x [:parameters :query])
                               runs (if (and query-params
                                             (not-empty query-params))
                                      (db/get-run-by query-params)
                                      (db/get-all-runs))]
                           {:status 200
                            :body   {:parameters (:parameters x)
                                     :count      (count runs)
                                     :data       runs}}))}}]
   ["/run/:id"
    {:get {:summary    "Get run info"
           :parameters {:path {:id pos-int?}}
           :responses  {200 {:body {:data any?}}}
           :handler    (fn [{{{:keys [id]} :path} :parameters}]
                         {:status 200
                          :body   {:id   id
                                   :data (db/get-run-by-id id)}})}}]
   ["/run/:id/fast5s"
    {:get {:summary    "Get run FAST5 infos"
           :parameters {:path {:id pos-int?}}
           :responses  {200 {:body {:data any?}}}
           :handler    (fn [{{{:keys [id]} :path} :parameters}]
                         (let [res (db/get-all-fast5-for-run id)]
                           {:status 200
                            :body   {:id    id
                                     :count (count res)
                                     :data  res}}))}}]
   ["/fast5/:id"
    {:get {:summary    "Get FAST5 info"
           :parameters {:path {:id pos-int?}}
           :responses  {200 {:body {:data any?}}}
           :handler    (fn [{{{:keys [id]} :path} :parameters}]
                         {:status 200
                          :body   {:data (db/get-fast5-by-id id)
                                   :id   id}})}}]
   ["/fast5/:id/file"
    {:get {:summary    "Download FAST5 file"
           :parameters {:path {:id pos-int?}}
           :swagger    {:produces ["application/vnd.fast5"]}
           :handler    (fn [{{{:keys [id]} :path} :parameters}]
                         (let [{:keys [original-path
                                       local-archive-path
                                       remote-archive-path
                                       filename]} (db/get-fast5-by-id id)
                               file-orig (io/as-file original-path)
                               file-local (io/as-file local-archive-path)
                               file-remote (io/as-file remote-archive-path)
                               stream (cond
                                        (file-readable? file-orig)
                                        (-> file-orig (io/input-stream))
                                        (file-readable? file-local)
                                        (-> file-local (io/input-stream))
                                        (file-readable? file-remote)
                                        (-> file-remote (io/input-stream))
                                        :else nil)]
                           (if stream
                             {:status 200
                              :header {"Content-Type"        "application/vnd.fast5"
                                       "Content-Disposition" (str "attachment; filename=\"" filename "\"")}
                              :body   stream}
                             {:status 404
                              :body   "FAST5 file not found!"})))}}]])
