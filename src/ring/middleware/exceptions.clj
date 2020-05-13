(ns ring.middleware.exceptions
  (:require [clojure.tools.logging :as log]))

(defn server-exception-response [e req uuid]
  (let [uri (:uri req)]
    ;; (println e "uri:" (:uri req) "uuid:" uuid)
    (log/error e "Caught unexpected RuntimeException:" uuid)
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

(defn unauthenticated-exception-response
  "Unauthenticated exception"
  [e req uuid]
  (log/error e "Unauthenticated: erorr uuid:" uuid)
  {:status 401
   :body {:message (.getMessage e)
          :error-id uuid}})

(defn graphql-exception-response
  [e req uuid]
  {:status (or (-> e ex-data :status)
               (-> e ex-data :http-status)
               200)
   :body {:errors [{:message (.getMessage e)
                    :error-id uuid}]}})

(defn sql-exception-response
  [e req uuid]
  (log/error (.getNextException e) "Caught unexpected SQLException, next exception:" uuid)
  (server-exception-response e req uuid))

(def default-error-fns {:invalid-params malformed-exception-response
                        :graphql-errors graphql-exception-response
                        :unauthenticated unauthenticated-exception-response
                        :sql-exception-response sql-exception-response
                        :server-exception-response server-exception-response})

(defn wrap-exceptions
  "Wrap unhandled exception"
  ([handler]
   (wrap-exceptions handler {:error-fns default-error-fns
                             :pre-hook nil}))
  ([handler options]
   (fn [request]
     (let [error-fns (:error-fns options)
           pre-hook (:pre-hook options)]
       (try
         (handler request)
         (catch clojure.lang.ExceptionInfo e  ; Catch ExceptionInfo
           (let [uuid (str (java.util.UUID/randomUUID))
                 cause (-> e ex-data :cause)
                 error-fn (get error-fns cause server-exception-response)]
             (log/error e "Caught preprocessed ExceptionInfo:" uuid)
             (if pre-hook
               (pre-hook e request uuid))
             (error-fn e request uuid)))
         (catch Throwable e  ; Catch all other throwables
           (let [uuid (str (java.util.UUID/randomUUID))
                 sql-exception-response-fn (:sql-exception-response error-fns)
                 server-exception-response-fn (:server-exception-response error-fns)]
             (if pre-hook
               (pre-hook e request uuid))
             (if (= (type e) java.sql.SQLException)
               (sql-exception-response-fn e request uuid)
               (server-exception-response-fn e request uuid)))))))))