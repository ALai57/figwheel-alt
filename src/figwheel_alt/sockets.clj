(ns figwheel-alt.sockets
  (:require [cljs.repl :as repl]
            [cljs.repl.browser :as browser])
  (:import [java.net InetSocketAddress Socket InetAddress ServerSocket])
  (:gen-class))

(def LOCALHOST
  (InetAddress/getLoopbackAddress))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server sockets
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn accept!
  "The socket blocks and waits for requests to come in over the network."
  [^ServerSocket sock]
  (.accept sock))

(defn close!
  "Close the socket"
  [^ServerSocket socket]
  (.close socket))

(defn get-input-stream
  [^Socket s]
  (.getInputStream s))

(defn server
  "Starts a server that tries to accept a connection and handle it in an infinte
  loop. The server runs in a separate thread. To shut it off, call the `close!`
  function and supply the Server's socket as an argument. This will cause all
  connections in `accept` state to throw a SocketException."
  ([]
   (server {}))
  ([{:keys [n-threads port]
     :or   {n-threads 4
            port      0}}]
   (let [socket (new ServerSocket port)]
     (future (try
               (loop []
                 (let [s (accept! socket)]
                   (println "Input Stream")
                   (println (slurp (get-input-stream s)))
                   (println "Accepted new connection!"))
                 (recur))
               (catch Exception e
                 (println "Caught exception!")
                 (println e)
                 (println "Shutting down thread"))))
     socket)))

;; https://medium.com/geekculture/a-tour-of-netty-5020ecee5494

(comment

  ;; What is Jetty doing under the Hood?
  (def srv
    (server {:port 9876}))

  (close! srv)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client sockets
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn socket-addr
  "Create a Socket"
  [{:keys [port host]
    :or   {host LOCALHOST}}]
  (InetSocketAddress. host port))

(defn connect
  "Connects to the socket specified."
  [^InetSocketAddress socket-address]
  (doto (Socket.)
    (.connect socket-address)))


(comment
  (.getInetAddress sock)
  (.getLocalPort sock)
  (.getLocalSocketAddress sock)
  )
