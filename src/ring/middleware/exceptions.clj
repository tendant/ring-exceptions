(ns ring.middleware.exceptions
  (:require [clojure.tools.logging :as log]))

(defn server-exception-response [e req id]
  (let [uri (:uri req)]
    ;; (println e "uri:" (:uri req) "id:" id)
    (log/error e "Caught unexpected RuntimeException:" id)
    {:status 500
     :body {:message "Unexpected server exception."
            :uri (:uri req)
            :error-id id}}))

(defn malformed-exception-response
  "Malformed exception, mainly caused by malformed parameters in HTTP Request, this is client application error (bug), instead of user error."
  [e req id]
  (log/error e "malformed: erorr id:" id)
  {:status 400
   :body {:message (.getMessage e)
          :error-id id}})

(defn unauthenticated-exception-response
  "Unauthenticated exception"
  [e req id]
  (log/error e "Unauthenticated: erorr id:" id)
  {:status 401
   :body {:message (.getMessage e)
          :error-id id}})

(defn graphql-exception-response
  [e req id]
  (let [cause-ex (unwrap-exception e)]
    {:status (or (-> cause-ex ex-data :status)
                 (-> cause-ex ex-data :http-status)
                 200)
     :body {:errors [{:message (.getMessage cause-ex)
                      :error (-> cause-ex ex-data :error)
                      :error-id id}]}}))

(defn sql-exception-response
  [e req id]
  (log/error (.getNextException e) "Caught unexpected SQLException, next exception:" id)
  (server-exception-response e req id))

(def default-error-fns {:invalid-params malformed-exception-response
                        :graphql-errors graphql-exception-response
                        :unauthenticated unauthenticated-exception-response
                        :sql-exception-response sql-exception-response
                        :server-exception-response server-exception-response})

(defn default-id-fn
  [req]
  (str (java.util.UUID/randomUUID)))

(defn unwrap-exception [ex]
  (when (and ex
             (instance? Throwable ex))
    (let [cause (-> ex ex-data :cause)]
      (if cause
        ex
        (if (.getCause ex)
          (recur (.getCause ex))
          ex)))))

(defn wrap-exceptions
  "Wrap unhandled exception"
  ([handler]
   (wrap-exceptions handler {:error-fns default-error-fns
                             :pre-hook nil
                             :id-fn default-id-fn}))
  ([handler options]
   (fn [request]
     (let [error-fns (:error-fns options)
           server-exception-response-fn (:server-exception-response error-fns)
           pre-hook (:pre-hook options)
           id-fn (or (:id-fn options)
                     default-id-fn)
           id (id-fn request)]
       (try
         (handler request)
         (catch clojure.lang.ExceptionInfo e  ; Catch ExceptionInfo
           (let [cause-ex (unwrap-exception e)
                 cause (-> cause-ex ex-data :cause)
                 error-fn (get error-fns cause server-exception-response-fn)]
             (log/error e "Caught preprocessed ExceptionInfo:" id)
             (if pre-hook
               (pre-hook e request id))
             (error-fn e request id)))
         (catch Throwable e  ; Catch all other throwables
           (let [sql-exception-response-fn (:sql-exception-response error-fns)
                 server-exception-response-fn (:server-exception-response error-fns)]
             (if pre-hook
               (pre-hook e request id))
             (if (= (type e) java.sql.SQLException)
               (sql-exception-response-fn e request id)
               (server-exception-response-fn e request id)))))))))