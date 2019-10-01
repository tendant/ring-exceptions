(defproject tendant/ring-exceptions "0.1.0-SNAPSHOT"
  :description "Wrap exceptions for ring handler"
  :url "https://github.com/tendant/ring-exceptions"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "0.5.0"]]
  :repl-options {:init-ns ring-exceptions.core})
