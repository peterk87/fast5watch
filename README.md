# fast5watch - Watch and archive Nanopore runs

[![Build Status](https://travis-ci.com/peterk87/fast5watch.svg?branch=master)](https://travis-ci.com/peterk87/fast5watch)

Clojure web server application for 
- watching a directory for new Nanopore runs and FAST5 files
- storing that info in a H2 DB
- archiving runs and FAST5 files to remote and/or local directories
- providing a RESTful HTTP API for accessing run and FAST5 info and files 

*generated using [Luminus](http://www.luminusweb.net/) version "3.42"*

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein run 
    
## Development

Clone repo and initialize

    git clone https://github.com/peterk87/fast5watch.git
    cd fast5watch

Create a `dev-config.edn` file for local environment variables, such as database credentials and app ports.

Example `dev-config.edn`

```clojure
{:dev true ;; development mode
 :port 3000 ;; web server port
 ;; when :nrepl-port is set the application starts the nREPL server on load
 :nrepl-port 7000
 ;; Base Nanopore run watch directory
 :nanopore-run-base-dir "/tmp/fast5watch-test"
 ;; Database connection URL (needed for migrations)
 :database-url "jdbc:h2:./fast5watch_test.db"
 ;; H2 database name
 :dbname "./fast5watch_dev.db"
 ;; [optional] Remote archiving directory
 :remote-base-dir "/tmp/fast5watch-remote-test"
 ;; [optional] Local archiving directory
 :local-base-dir "/tmp/fast5watch-local-test"}
```

Initialize local H2 database:

    lein run migrate

Start a web server for the app:

    lein run
    
Connect to the nREPL.

Run `lein test-refresh` to run tests automatically on changes.

### nREPL

Specify an `:nrepl-port` (e.g. `7000`) in your `dev-config.edn` file:

```clojure
{:dev true
 :port 3000
 ;; when :nrepl-port is set the application starts the nREPL server on load
 :nrepl-port 7000
 ...
 }
```

If using [IntelliJ IDEA](https://www.jetbrains.com/idea/) with the [Cursive plugin](https://cursive-ide.com/userguide/index.html), setup a [remote REPL](https://cursive-ide.com/userguide/repl.html#remote-repls).

Run the web server for the application with:

`lein run` 

Connect to the nREPL within IntelliJ or your editor of choice. 

You should see the following in your REPL:

    Connecting to remote nREPL server...
    Clojure 1.10.1

Check that you can eval basic expressions:

```clojure
(+ 1 1)
=> 2
```

### Running tests

Create a `test-config.edn` file in the project root, e.g.:

```clojure
;; test-config.edn
{:port 3000
 :database-url "jdbc:h2:./fast5watch_test.db"
 :dbname "./fast5watch_test.db"
 :nanopore-run-base-dir "/tmp/fast5watch-test-dir"}
```

Run tests with:

```clojure
lein test
# OR recommended
lein test-refresh
```

It's recommended that you run `lein test-refresh` while developing to run tests automatically with [lein-test-refresh](https://github.com/jakemcc/lein-test-refresh) which "is a Leiningen plug-in that automatically refreshes and then runs your `clojure.test` tests when a file in your project changes".


## License

Copyright © Government of Canada, 2019

Written by: Peter Kruczkiewicz, National Centre for Foreign Animal Disease

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License. You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0
