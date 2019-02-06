[about]: https://github.com/luchiniatwork/resilience4clj-circuitbreaker/blob/master/docs/ABOUT.md
[breaker]: https://github.com/luchiniatwork/resilience4clj-circuitbreaker/
[circleci-badge]: https://circleci.com/gh/luchiniatwork/resilience4clj-timelimiter.svg?style=shield&circle-token=416058ed576b28e43f7ac5e0e055cf483cc0f9e0
[circleci]: https://circleci.com/gh/luchiniatwork/resilience4clj-timelimiter
[clojars-badge]: https://img.shields.io/clojars/v/resilience4clj/resilience4clj-timelimiter.svg
[clojars]: http://clojars.org/resilience4clj/resilience4clj-timelimiter
[github-issues]: https://github.com/luchiniatwork/resilience4clj-timelimiter/issues
[license-badge]: https://img.shields.io/badge/license-MIT-blue.svg
[license]: ./LICENSE
[status-badge]: https://img.shields.io/badge/project%20status-alpha-brightgreen.svg

# Resilience4Clj Time Limiter

[![CircleCI][circleci-badge]][circleci]
[![Clojars][clojars-badge]][clojars]
[![License][license-badge]][license]
![Status][status-badge]

Resilience4clj is a lightweight fault tolerance library set built on
top of GitHub's Resilience4j and inspired by Netflix Hystrix. It was
designed for Clojure and functional programming with composability in
mind.

Read more about the [motivation and details of Resilience4clj
here][about].

Resilience4Clj Time Limiter lets you decorate a function call with a
specified time limit. If the function does not return within the
established time, an exception is thrown. This is particularly useful
for external dependencies that may be too slow and you have time
budgets for them.

## Table of Contents

* [Getting Started](#getting-started)
* [Limiter Settings](#limiter-settings)
* [Exception Handling](#exception-handling)
* [Composing Further](#composing-further)
* [Bugs](#bugs)
* [Help!](#help)

## Getting Started

Add `resilience4clj/resilience4clj-timelimiter` as a dependency to
your `deps.edn` file:

``` clojure
resilience4clj/resilience4clj-timelimiter {:mvn/version "0.1.0"}
```

If you are using `lein` instead, add it as a dependency to your
`project.clj` file:

``` clojure
[resilience4clj/resilience4clj-timelimiter "0.1.0"]
```

Require the library:

``` clojure
(require '[resilience4clj-timelimiter.core :as tl])
```

Then create a time limiter calling the function `create`:

``` clojure
(def limiter (tl/create))
```

Now you can decorate any function you have with the limiter you just
defined.

For the sake of this example, let's create a function that takes 250ms
to return:

``` clojure
(defn slow-hello []
  (Thread/sleep 250)
  "Hello World!")
```

The function `decorate` will take the slow function just above and the
time limiter you created and return a time limited function:

``` clojure
(def limited-hello (tl/decorate slow-hello limiter))
```

When you call `limited-hello`, it should eval to `"Hello World!"`
after 250ms:

``` clojure
(limited-hello) ;; => "Hello World!" (after 250ms)
```

The default configuration of the limiter is to wait 1000ms (1s) that's
why nothing really exceptional happened. If you were to redefined
`slow-hello` to take 1500ms though:

``` clojure
(defn slow-hello []
  (Thread/sleep 1500)
  "Hello World!")
```

Then calling `limited-hello` (after also redefining it) would yield an
exception:

``` clojure
(limited-hello) ;; => throws ExceptionInfo
```

## Limiter Settings

When creating a time limiter, you can fine tune two of its settings:

1. `:timeout-duration` - the timeout for this limiter in milliseconds
   (i.e. 300). Default is 1000.
2. `:cancel-running-future?` - when `true` the call to the decorated
   function will be canceled in case of a timeout. If `false`, the
   call will go through even in case of a timeout. However, in that
   case, the caller will still get an exception thrown. Default is
   `true`.
   
Changing `:cancel-running-future?` to `false` is particularly useful
if you have an external call with a side effect that might be relevant
later (i.e. updating a cache with external data - in that case you
might prefer to fail the first time around if your time budget expires
but still get the cache eventually updated when the external system
returns).

These two options can be sent to `create` as a map. In the following
example, any function decorated with `limiter` will timeout in 300ms
and calls will not be canceled if a timeout occurs.

``` clojure
(def limiter (tl/create {:timeout-duration 300
                         :cancel-running-future? false}))
```

## Exception Handling

When a timeout occurs, an instance of `ExceptionInfo` will be
thrown. Its payload can be extracted with `ex-data` and the key
`:resilience4clj.anomaly/category` will be set with
`:resilience4clj.anomaly/execution-timeout`.

``` clojure
(try
  (slow-hello)
  (catch Throwable e
    (if (= :resilience4clj.anomaly/execution-timeout
           (-> e ex-data :resilience4clj.anomaly/category))
      (println "Call timed out!!!"))))
```

## Composing Further

Resilience4clj is composed of [several modules][about] that
easily compose together. For instance, if you are also using the
[circuit breaker module][breaker] and assuming your import and basic
settings look like this:

``` clojure
(ns my-app
  (:require [resilience4clj-circuitbreaker.core :as cb]
            [resilience4clj-timelimiter.core :as tl]))

;; create time limiter with default settings
(def limiter (tl/create))

;; create circuit breaker with default settings
(def breaker (cb/create "HelloService"))

;; slow function you want to limit
(defn slow-hello []
  (Thread/sleep 1500)
  "Hello World!")
```

Then you can create a protected call that combines both the time
limiter and the circuit breaker:

``` clojure
(def protected-hello (-> slow-hello
                         (tl/decorate limiter)
                         (cb/decorate breaker)))
```

The resulting function on `protected-hello` will trigger the breaker
in case of a timeout now.

## Bugs

If you find a bug, submit a [Github issue][github-issues].

## Help

This project is looking for team members who can help this project
succeed!  If you are interested in becoming a team member please open
an issue.

## License

Copyright © 2019 Tiago Luchini

Distributed under the MIT License.
