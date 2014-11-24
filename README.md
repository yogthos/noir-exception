`noir-exception`
==============
Middleware for displaying `noir` style exceptions in the browser.

We missed `noir` exceptions. `noir-exception` brings back `noir` styled back traces
for the browser. You will easily find problems with your code because
`noir-exception` highlights your namespaces with a diferent color. The exception will
be displayed along with the request that caused it.

![exception trace](https://raw.githubusercontent.com/yogthos/noir-exception/master/exception.png)

...

![request](https://raw.githubusercontent.com/yogthos/noir-exception/master/request.png)

Install
-------
Add the following dependency to your `project.clj` file:

[![Clojars Project](http://clojars.org/noir-exception/latest-version.svg)](http://clojars.org/noir-exception)

Usage
-------
The library provides two middleware functions called `wrap-exceptions` and `wrap-internal-error`.

The `wrap-exceptions` function will create styled stack traces to highlight local namespaces.
This is useful for quickly identifying what matters most in stacktraces during development.

If you have, as an example, <em>development</em> and <em>production</em> profiles,
use the `quiet?` optional flag to set the profile - `#(wrap-exceptions % quiet?)`.
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
out by default.

The function accepts two keyword arguments, maned `:log`  and `:error-response`. The first
allows providing a custom log function for the exceptions and the second can be used to supply
a custom error response.

Alternatively, it's possible to supply the `:error-response-handler` key that points to a function
which should accept the request as a paremeter and return a string response that will be returned
to the client. This can be useful for generating contextual errors based on the contents of the
request.

```clj
(ns my.ns
  (:require [taoensso.timbre :as timbre]
            [noir-exception.core :refer [wrap-internal-error]]
            ...))

(def app
  (app-handler [routes]
    :middleware [#(wrap-internal-error %
                    :log (fn [e] (timbre/error e))
                    :error-response {:status 500
                                     :headers {"Content-Type" "text/html"}
                                     :body "something bad happened!"})]))
```


