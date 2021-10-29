(defproject figwheel-alt "0.1.0-SNAPSHOT"
  :description "Learning about Figwheel by reconstructing it"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [io.netty/netty-all "4.1.65.Final"]]
  :main ^:skip-aot figwheel-alt.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
