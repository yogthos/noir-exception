`noir-exception`
==============
Middleware for displaying `noir` style exceptions in the browser.

We missed `noir` exceptions. `noir-exception` brings back `noir` styled back traces
for the browser. You will easily find problems with your code because
`noir-exception` highlights your namespaces with a diferent color.

![exception trace](https://raw.githubusercontent.com/yogthos/noir-exception/master/exception.png)
Install
-------
Add the following dependency to your `project.clj` file:

[![clojars version](https://clojars.org/noir-exception/latest-version.svg?raw=true)](https://clojars.org/noir-exception)

Usage
-------
The library provides two middlware functions called `wrap-exceptions` and `wrap-internal-error`.

The `wrap-exceptions` function will created styled stacktraces to highlight local namespaces.
This is useful for quickly seeing stacktraces during development.

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

The `wrap-internal-error` function allows catching errors and producing a standard error page.
This function should be used for handling errors in production, where you do not wish to expose
the internals of the application to the user. The function will print the stacktrace to standard
out by default. Alternatively, it can be passed an optional log function to log the exception.

```clj
(ns my.ns
  (:require [taoensso.timbre :as timbre]
            [noir-exception.core :refer [wrap-internal-error]]
            ...))

(def app
  (app-handler [routes]
    :middleware [#(wrap-internal-error % (fn [e] (timbre/error e)))]))
```


