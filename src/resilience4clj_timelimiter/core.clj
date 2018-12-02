(ns resilience4clj-timeliter.core
  (:import
   (io.github.resilience4j.timelimiter TimeLimiterConfig
                                       TimeLimiter)
   (io.vavr.control Try)
   (java.util.function Supplier)
   (java.util.concurrent Executors
                         Callable)
   (java.time Duration)))


#_(let [config (.build (doto (TimeLimiterConfig/custom)
                         (.timeoutDuration (Duration/ofSeconds 5))
                         (.cancelRunningFuture false)))
        time-limiter (TimeLimiter/of config)
        executor-service (Executors/newSingleThreadExecutor)
        future-supplier (reify Supplier
                          (get [this]
                            (.submit executor-service (reify Callable
                                                        (call [this]
                                                          (Thread/sleep 7000)
                                                          (println "Did run")
                                                          "Hello World")))))
        decorated-call (TimeLimiter/decorateFutureSupplier time-limiter future-supplier)]
    (let [result (Try/ofCallable decorated-call)]
      (if (.isSuccess result)
        (println (.get result))
        (println "Nope!!!"))))

(defn ^:private config-data->time-limiter-config
  [{:keys [timeout-duration cancel-running-future?]}]
  (.build
   (cond-> (TimeLimiterConfig/custom)
     timeout-duration (.timeoutDuration (Duration/ofMillis timeout-duration))
     (not (nil? cancel-running-future?)) (.cancelRunningFuture cancel-running-future?))))

(defn ^:private time-limiter-config->config-data
  [limiter-config]
  {:timeout-duration (.toMillis (.getTimeoutDuration limiter-config))
   :cancel-running-future? (.shouldCancelRunningFuture limiter-config)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create
  ([]
   (create nil))
  ([opts]
   (if opts
     (TimeLimiter/of (config-data->time-limiter-config opts))
     (TimeLimiter/ofDefaults))))

(defn config
  [time-limiter]
  (-> time-limiter
      .getTimeLimiterConfig
      time-limiter-config->config-data))

(defn decorate
  [f time-limiter]
  (with-meta
    (fn [& args]
      (let [fetch-callable? (-> args last :resilience4clj/fetch-callable?)
            executor-service (Executors/newSingleThreadExecutor)
            future-supplier (reify Supplier
                              (get [this]
                                (.submit executor-service
                                         (reify Callable
                                           (call [this] (apply f args))))))
            decorated-callable (TimeLimiter/decorateFutureSupplier time-limiter future-supplier)]
        (if fetch-callable?
          decorated-callable
          (let [result (Try/ofCallable decorated-callable)]
            (if (.isSuccess result)
              (.get result)
              (throw (.getCause result)))))))
    {:resilience4clj/callable-available? true}))





(comment
  (def time-limiter (create))
  (config time-limiter)

  (def time-limiter2 (create {:timeout-duration 5000
                              :cancel-running-future? false}))
  (config time-limiter2)

  (def time-limiter3 (create {:timeout-duration 2000}))
  (config time-limiter3)

  (defn external-call []
    "Do something external")

  
  (decorate external-call
            time-limiter)

  (-> external-call
      timelimiter/decorate
      breaker/decorate)
  )
