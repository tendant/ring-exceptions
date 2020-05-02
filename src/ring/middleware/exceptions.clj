(ns ring.middleware.exceptions
  (:require [clojure.tools.logging :as log]))

(defn server-exception-response [e req uuid]
  (let [uri (:uri req)]
    ;; (println e "uri:" (:uri req) "uuid:" uuid)
    {:status 500
     :body {:message "Unexpected server exception."
            :uri (:uri req)
            :error-id uuid}}))

(defn malformed-exception-response
  "Malformed exception, mainly caused by malformed parameters in HTTP Request, this is client application error (bug), instead of user error."
  [e req uuid]
  (log/error e "malformed: erorr uuid:" uuid)
  {:status 400
   :body {:message (.getMessage e)
          :error-id uuid}})

(defn graphql-exception-response
  [e req uuid]
  {:status (or (-> e ex-data :status)
               (-> e ex-data :http-status)
               200)
   :body {:errors [{:message (.getMessage e)
                    :error-id uuid}]}})

(def default-error-fns {:invalid-params malformed-exception-response
                        :graphql-errors graphql-exception-response})

(defn wrap-exceptions
  "Wrap unhandled exception"
  ([handler]
   (wrap-exceptions handler {:error-fns default-error-fns}))
  ([handler options]
   (fn [request]
     (try
       (handler request)
       (catch clojure.lang.ExceptionInfo e  ; Catch ExceptionInfo
         (let [uuid (str (java.util.UUID/randomUUID))
               cause (-> e ex-data :cause)
               error-fns (:error-fns options)
               error-fn (get error-fns cause server-exception-response)]
           (log/error e "Caught preprocessed ExceptionInfo:" uuid)
           (error-fn e request uuid)))
       (catch Throwable e  ; Catch all other throwables
         (let [uuid (str (java.util.UUID/randomUUID))]
           (when (and (= (type e) java.sql.SQLException)
                      (.getNextException e))
             (log/error (.getNextException e) "Caught unexpected SQLException, next exception:" uuid))
           (log/error e "Caught unexpected RuntimeException:" uuid)
           (server-exception-response e request uuid)))))))