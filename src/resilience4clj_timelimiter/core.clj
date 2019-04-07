(ns resilience4clj-timelimiter.core
  (:import
   (io.github.resilience4j.timelimiter TimeLimiterConfig
                                       TimeLimiter)

   (io.vavr.control Try)

   (java.util.function Supplier)

   (java.util.concurrent Executors
                         Callable)

   (java.time Duration)))

(defn ^:private anom-map
  [category msg]
  {:resilience4clj.anomaly/category (keyword "resilience4clj.anomaly" (name category))
   :resilience4clj.anomaly/message msg})

(defn ^:private anomaly!
  ([name msg]
   (throw (ex-info msg (anom-map name msg))))
  ([name msg cause]
   (throw (ex-info msg (anom-map name msg) cause))))

(defn ^:private config-data->time-limiter-config
  [{:keys [timeout-duration cancel-running-future?]}]
  (.build
   (cond-> (TimeLimiterConfig/custom)
     timeout-duration
     (.timeoutDuration ^Duration (Duration/ofMillis timeout-duration))

     (not (nil? cancel-running-future?))
     (.cancelRunningFuture cancel-running-future?))))

(defn ^:private time-limiter-config->config-data
  [^TimeLimiterConfig limiter-config]
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
     (TimeLimiter/of ^TimeLimiterConfig (config-data->time-limiter-config opts))
     (TimeLimiter/ofDefaults))))

(defn config
  [^TimeLimiter time-limiter]
  (-> time-limiter
      .getTimeLimiterConfig
      time-limiter-config->config-data))

(defn decorate
  [f time-limiter]
  (fn [& args]
    (let [executor-service (Executors/newSingleThreadExecutor)
          future-supplier (reify Supplier
                            (get [this]
                              (.submit executor-service
                                       (reify Callable
                                         (call [this] (apply f args))))))
          decorated-callable (TimeLimiter/decorateFutureSupplier time-limiter future-supplier)]
      (let [result (Try/ofCallable decorated-callable)]
        (if (.isSuccess result)
          (.get result)
          (let [cause (.getCause result)]
            (if (instance? java.util.concurrent.TimeoutException cause)
              (anomaly! :execution-timeout "Execution timed out" (.getCause result))
              (throw cause))))))))




(comment
  (def time-limiter (create))
  (config time-limiter)

  (def time-limiter2 (create {:timeout-duration 5000
                              :cancel-running-future? false}))
  (config time-limiter2)

  (def time-limiter3 (create {:timeout-duration 2000}))
  (config time-limiter3)

  ;; mock for an external call
  (defn external-call
    ([n]
     (external-call n nil))
    ([n {:keys [fail? wait]}]
     (when wait
       (Thread/sleep wait))
     (if-not fail?
       (str "Hello " n "!")
       (anomaly! :broken-hello "Couldn't say hello"))))

  
  (def limited-call (decorate external-call
                              time-limiter))

  (try
    (limited-call "World" {:wait 100 :fail? true})
    (catch Throwable e
      (println (ex-data e))
      (println (type e))
      (throw e)))

  (defn side-effect! [a]
    (Thread/sleep 300)
    (swap! a inc))

  (def dec-side! (decorate side-effect! (create {:cancel-running-future? false
                                                 :timeout-duration 200})))
  
  (let [a (atom 0)]
    (try
      (dec-side! a)
      (catch Throwable e)
      (finally
        (Thread/sleep 300)
        (println @a))))
  
  #_(-> external-call
        timelimiter/decorate
        breaker/decorate))
