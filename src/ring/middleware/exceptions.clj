(ns ring.middleware.exceptions
  (:require [clojure.tools.logging :as log]
            [reitit.ring.middleware.exception :as exception]))

(defn unwrap-exception [ex]
  (when (and ex
             (instance? Throwable ex))
    (let [cause (-> ex ex-data :type)]
      (if cause
        ex
        (if (.getCause ex)
          (recur (.getCause ex))
          ex)))))

;; https://cljdoc.org/d/metosin/reitit/0.5.11/doc/ring/pluggable-coercion#pretty-printing-spec-errors
(defn create-error-handler [status]
  (let [handler (exception/create-coercion-handler status)]
    (fn [exception request]
      (log/info "PROBLEMS:" (-> exception ex-data :problems))
      (handler exception request))))

(defn server-exception-response [e req]
  (let [uri (:uri req)
        ex-id (:ex-id req)]
    (log/error e "Caught unexpected RuntimeException:" ex-id)
    {:status 500
     :body {:message "Unexpected server exception."
            :uri (:uri req)
            :error-id ex-id}}))

(defn malformed-exception-response
  "Malformed exception, mainly caused by malformed parameters in HTTP Request, this is client application error (bug), instead of user error."
  [e req]
  (log/error e "malformed: erorr id:" (:ex-id req))
  {:status 400
   :body {:message (.getMessage e)
          :error-id (:ex-id req)}})

(defn unauthenticated-exception-response
  "Unauthenticated exception"
  [e req]
  (log/error e "Unauthenticated: erorr id:" (:ex-id req))
  {:status 401
   :body {:message (.getMessage e)
          :error-id (:ex-id req)}})

(defn graphql-exception-response
  [e req]
  (let [cause-ex (unwrap-exception e)]
    {:status (or (-> cause-ex ex-data :status)
                 (-> cause-ex ex-data :http-status)
                 200)
     :body {:errors [{:message (.getMessage cause-ex)
                      :error (-> cause-ex ex-data :error)
                      :error-id (:ex-id req)}]}}))

(defn sql-exception-response
  [e req]
  (log/error (.getNextException e) "Caught unexpected SQLException, next exception:" (:ex-id req))
  (server-exception-response e req (:ex-id req)))

(def default-error-fns {:invalid-params malformed-exception-response
                        :graphql-errors graphql-exception-response
                        :unauthenticated unauthenticated-exception-response
                        :sql-exception-response sql-exception-response
                        :server-exception-response server-exception-response})

(defn default-id-fn
  [req]
  (str (java.util.UUID/randomUUID)))

(defn wrap-exceptions
  "Wrap unhandled exception, using error-fn to process exception determined by :type in ex-data of ex-info."
  ([handler]
   (wrap-exceptions handler {:error-fns default-error-fns
                             :pre-hook nil
                             :id-fn default-id-fn}))
  ([handler options]
   (fn [req]
     (let [id-fn (or (:id-fn options)
                     default-id-fn)
           ex-id (id-fn req)
           request (assoc req :ex-id ex-id)
           error-fns (:error-fns options)
           server-exception-response-fn (:server-exception-response error-fns)
           pre-hook (:pre-hook options)]
       (try
         (handler request)
         (catch clojure.lang.ExceptionInfo e  ; Catch ExceptionInfo
           (let [cause-ex (unwrap-exception e)
                 cause (-> cause-ex ex-data :type)
                 error-fn (get error-fns cause server-exception-response-fn)]
             (if pre-hook
               (pre-hook e request))
             (error-fn e request)))
         (catch Throwable e  ; Catch all other throwables
           (let [sql-exception-response-fn (:sql-exception-response error-fns)
                 server-exception-response-fn (:server-exception-response error-fns)]
             (if pre-hook
               (pre-hook e request))
             (if (= (type e) java.sql.SQLException)
               (sql-exception-response-fn e request)
               (server-exception-response-fn e request)))))))))