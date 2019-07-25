(ns core-test
  (:require [resilience4clj-timelimiter.core :as timelimiter]
            [clojure.test :refer :all]))

;; mock for an external call
(defn ^:private external-call
  ([n]
   (external-call n nil))
  ([n {:keys [fail? wait]}]
   (when wait
     (Thread/sleep wait))
   (if-not fail?
     (str "Hello " n "!")
     (throw (ex-info "Couldn't say hello" {:extra-info :here})))))

(defn ^:private external-call!
  [a]
  (Thread/sleep 250)
  (swap! a inc))

(deftest limiter-creation
  (testing "default config"
    (let [limiter (timelimiter/create)]
      (is (= {:timeout-duration 1000
              :cancel-running-future? true}
             (timelimiter/config limiter)))))

  (testing "custom config"
    (let [limiter (timelimiter/create {:timeout-duration 5000
                                       :cancel-running-future? false})]
      (is (= {:timeout-duration 5000
              :cancel-running-future? false}
             (timelimiter/config limiter)))))

  (testing "partial custom config 1"
    (let [limiter (timelimiter/create {:timeout-duration 2000})]
      (is (= {:timeout-duration 2000
              :cancel-running-future? true}
             (timelimiter/config limiter)))))

  (testing "partial custom config 2"
    (let [limiter (timelimiter/create {:cancel-running-future? false})]
      (is (= {:timeout-duration 1000
              :cancel-running-future? false}
             (timelimiter/config limiter))))))

(deftest limiter-decorates
  (testing "simple decoration, nothing wrong"
    (let [limiter (timelimiter/create)
          decorated (timelimiter/decorate external-call limiter)]
      (is (= "Hello World!"
             (decorated "World")))))

  (testing "simple decoration, accepatable delay"
    (let [limiter (timelimiter/create)
          decorated (timelimiter/decorate external-call limiter)]
      (is (= "Hello World!"
             (decorated "World" {:wait 100}))))))

(deftest limiter-timeouts
  (testing "limiter does time out"
    (let [limiter (timelimiter/create {:timeout-duration 200})
          decorated (timelimiter/decorate external-call limiter)]
      (try
        (decorated "World" {:wait 250})
        (catch Throwable e
          (is (= :resilience4clj.anomaly/execution-timeout
                 (-> e ex-data :resilience4clj.anomaly/category))))))))

(deftest limiter-cascades-throws
  (let [limiter (timelimiter/create {:timeout-duration 200})
        decorated (timelimiter/decorate external-call limiter)]
    (try
      (decorated "World" {:fail? true})
      (catch Throwable e
        (is (= :here
               (-> e ex-data :extra-info)))))))

(deftest cancel-future-tests
  (let [a (atom 0)]
    (are [cancels? target-value]
        (let [limiter (timelimiter/create {:timeout-duration 200
                                           :cancel-running-future? cancels?})
              ;; external-call! always takes 250ms
              decorated! (timelimiter/decorate external-call! limiter)]
          (try
            (decorated! a)
            (catch Throwable e)
            (finally
              ;; to make sure the 250ms passed
              (Thread/sleep 100)))
          (= target-value @a))

      ;; cancelled, so atom should remain at 0
      true  0
      ;; not cancelled, so atom should bump to 1
      false 1
      ;; cancelled, so atom should remain at 1
      true 1
      ;; not cancelled, so atom should bump to 2
      false 2)))
