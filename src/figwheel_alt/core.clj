(ns figwheel-alt.core
  (:require [cljs.repl :as repl]
            [cljs.repl.browser :as browser])
  (:import [io.netty.channel.epoll EpollServerSocketChannel EpollEventLoopGroup]
           [io.netty.bootstrap ServerBootstrap]
           [java.net InetSocketAddress])
  (:gen-class))


(defn tiny-server
  "Starting a server without Netty - just running a loop on another thread"
  []
  (.start (Thread. (bound-fn []
                     (loop [x 5]
                       (when (pos? x)
                         (println "Looping!")
                         (Thread/sleep 400)
                         (recur (dec x))))))))



(defn available-processors
  "Get the available number of processors on this Runtime (the environment on
  which the application is running)"
  []
  (.availableProcessors (Runtime/getRuntime)))

(defn epoll-event-loop-group
  "An event loop group.

  Event loop groups....."
  []
  (EpollEventLoopGroup. (available-processors)))

(defn socket
  "Create a Socket with a wildcard IP address and the given port"
  [port]
  (InetSocketAddress. port))

(defn new-server
  "Creates a new ServerChannel.

  Channels are a key abstraction in Netty.

  Channels are connections to network sockets which use network I/O to
  communicate (read, write, connect, bind). They form the base of Java NIO.

  Channels allow us to read and write from byte buffers, but we need to
  interpret the result of the Byte Buffer afterwards.
  "
  []
  ;; https://livebook.manning.com/book/netty-in-action/chapter-8/11
  ;; https://netty.io/wiki/user-guide-for-4.x.html
  ;; https://www.fatalerrors.org/a/super-detailed-introduction-to-netty-this-is-enough.html
  (doto (ServerBootstrap.)
    (.group (epoll-event-loop-group))     ;; Set the event loop group
    (.channel EpollServerSocketChannel)   ;; Set the Factory for accepting new connections
    ;; a server devotes a parent channel to accepting connections from clients and creating child channels for conversing with them
    ))


(defn start-server
  [port]
  (let [server (new-server)]
    (doto server
      (.bind (socket port)))))

(defn -main
  [& args])

(comment

  (EpollServerSocketChannel.)
  (bound-fn []
    (println "Hello"))

  (def env (browser/repl-env))

  env
  (repl/repl env)


  binding-conveyor-fn
  +
  )
