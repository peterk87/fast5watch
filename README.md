# fast5watch - Watch and archive Nanopore runs

Clojure web server application for 
- watching a directory for new Nanopore runs and FAST5 files
- storing that info in a H2 DB
- archiving runs and FAST5 files to remote and/or local directories
- providing a RESTful HTTP API for accessing run and FAST5 info and files 

*generated using Luminus version "3.42"*

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
 ;; H2 database name
 :dbname "./fast5watch_dev.db"
 ;; [optional] Remote archiving directory
 :remote-base-dir "/tmp/fast5watch-remote-test"
 ;; [optional] Local archiving directory
 :local-base-dir "/tmp/fast5watch-local-test"}
```

Initialize  database
    lein run migrate

### nREPL

Specify a port in your `dev-config.edn` file:

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



## License

Copyright © Government of Canada, 2019

Written by: Peter Kruczkiewicz, National Centre for Foreign Animal Disease

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License. You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0