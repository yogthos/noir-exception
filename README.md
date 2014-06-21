`noir-exception`
==============
Middleware for displaying `noir` style exceptions in the browser.

We missed `noir` exceptions. `noir-exception` brings back `noir` styled back traces
for the browser. You will easily find problems with your code because
`noir-exception` highlights your namespaces with a diferent color.

Install
-------
Add the following dependency to your `project.clj` file:

[![clojars version](https://clojars.org/noir-exception/latest-version.svg?raw=true)](https://clojars.org/noir-exception)

Usage
-------
If you have, as an example, <em>development</em> and <em>production</em> profiles,
use the `quiet?` optional flag so set the profile - `#(wrap-exceptions % quiet?)`.
If you always want to show the stack traces, just use `wrap-exceptions`.
```clj
(ns my.ns
  (:require [noir-exception.core :refer [wrap-exceptions]]
            ...))

(def quiet? (not (= (System/getenv "PROFILE") "dev")))

(def app
  (app-handler [routes]
    :middleware [#(wrap-exceptions % quiet?)]))
```