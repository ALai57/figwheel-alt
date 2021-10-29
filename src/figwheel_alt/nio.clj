(ns figwheel-alt.nio
  "Introducing Java NIO (Non-block IO) and contrasting with Java IO"
  (:import [java.net Socket InetSocketAddress]
           [java.io OutputStream
            ByteArrayOutputStream
            OutputStreamWriter
            PrintWriter
            BufferedReader
            InputStreamReader]
           [java.nio ByteBuffer]
           [java.nio.charset StandardCharsets]
           [java.nio.channels
            Selector
            SelectionKey
            ServerSocketChannel
            SocketChannel]))


;; https://www.alibabacloud.com/forum/read-620


;; Java IO was first introduced in Java 1.0 (Jan 23, 1996)
;; It provides
;; - InputStreams and Output Streams (these provide data one byte at a time)
;; - Reader and Writer (convenience wrappers for character streams)
;; - Blocking mode (wait for a complete message)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Demo of how streams work
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  ;; If we want to directly write to a Stream, we can only write bytes. So we
  ;; need to figure out how to translate our Object into Bytes manually (.getBytes).
  (let [s    "hello"
        baos (ByteArrayOutputStream.)]
    (.write baos (.getBytes s) 0 (count s)) ;; Write bytes to the stream
    (.toString baos)                        ;; Confirm we wrote the bytes by inspecting the stream
    )
  )

(comment
  ;; If we want to write encoded characters, we can use a Writer.
  ;; Writers allow us to write characters instead of bytes.
  (let [s      "hello"
        baos   (ByteArrayOutputStream.)
        writer (OutputStreamWriter. baos)]  ;; This actually has a buffer https://stackoverflow.com/questions/36809823/does-an-outputstreamwriter-without-buffering-exist
    (.write writer s)
    (.flush writer)    ;; Flushing will ensure that any buffered input gets written
    (.toString baos)))

(comment
  ;; If we want some additional nice-to-haves, we can use a PrintWriter
  ;; (println, format, etc). This knows how to write text and also has some nice
  ;; built-in formatting.
  (let [s      "hello"
        baos   (ByteArrayOutputStream.)
        writer (PrintWriter. (OutputStreamWriter. baos) true) ;; true for auto-flushing
        ]
    (.println writer s)
    (.toString baos)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Socket example
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn socket
  "We want to do our IO from a Socket in this example."
  [{:keys [host port]
    :or   {host "localhost"}}]
  (new Socket host port))

(defn socket-address
  [{:keys [host port]
    :or   {host "localhost"
           port 0}}]
  (new InetSocketAddress host port))

(defn get-socket-writer
  "Returns a writer able to write characters to the socket's output stream."
  [^Socket sock]
  (PrintWriter. (OutputStreamWriter. (.getOutputStream sock)) true))

(defn get-socket-reader
  "Returns a writer capable of writing characters to a socket's input stream."
  [^Socket sock]
  (BufferedReader. (InputStreamReader. (.getInputStream sock))))

;; https://www.infoworld.com/article/2853780/socket-programming-for-scalable-systems.html
(defn write!
  "OutputStreams allow us to write data to our sink. However, they only support
  writing bytes at a time. This would be quite tedious, so we use a `Writer`,
  which is a wrapper around a `Stream` that has convenient methods for encoding
  and decoding objects into Bytes."
  [^Socket sock]
  (let [writer (get-socket-writer sock)]
    ;; Write to the socket
    (.println writer "GET / HTTP/1.1")
    (.println writer "")
    (println "Wrote to socket")))

(defn ready?
  [reader]
  (.ready reader))

(defn read!
  "OutputStreams allow us to write data to our sink. In the case that we are
  writing to an HTTP socket, we expect a response, and we want to read the
  response (which will be an InputStream)."
  [^Socket sock]
  (let [reader (get-socket-reader sock)]
    ;; Block and wait for response
    (println "Waiting for socket response...")
    (loop [line (.readLine reader)]
      (when (and (ready? reader) line) ;; I believe the .ready is necessary
        ;; https://stackoverflow.com/questions/15521352/bufferedreader-readline-blocks
        ;;(println "Line received:" (type line) (count line))
        (println line)
        (recur (.readLine reader))))

    (.close reader)
    (.close sock)))

(comment
  ;; Using Java IO to read and write from a Socket
  (def the-socket
    (socket {:host "www.google.com"
             :port 80}))

  (write! the-socket)
  (read! the-socket))



;; Java NIO (Nonblocking-IO) was introduced in Java 1.4 (Feb 6, 2002) and
;; updated in Java 1.7 (NIO.2 - July 2011) It provides:
;; - Buffers (read chunks of data at a time)
;; - Channel (for communicating with outside world)
;; - Selector (multiplex on a SelectableChannel and provide access to Channels ready for IO)

;; https://examples.javacodegeeks.com/core-java/nio/java-nio-socket-example/
;; https://www.devdiaries.net/blog/java.nio-How-To-Build-a-non-blocking-server-in-java/

(defn get-selected-keys
  [^Selector selector]
  (-> selector
      (.selectedKeys)
      (.iterator)))

(defn f'
  "Compatibility for Java's ability to do 'functional programming'"
  [f]
  (reify
    java.util.function.Consumer
    (accept [this arg]
      (f arg))))

(defn accept-key!
  [k]
  (let [ch (.channel k)]
    (println "Accepting: " ch)
    (.accept ch)))

(defn read-key
  [k]
  (let [ch (.channel k)
        bb (ByteBuffer/allocate 1024)
        n  (.read ch bb)]
    (println (format "Processed %d bytes" n))
    (if (= -1 n)
      (.close ch)
      (.flip bb))))

(defn process-selected
  "This is a blocking select operation"
  [^Selector selector]
  (.select selector)
  (try
    (let [ks (get-selected-keys selector)]
      (.forEachRemaining ks (f' (fn [k]
                                  (.remove ks)
                                  (println "k" k)
                                  (cond
                                    (not (.isValid k)) (println "Invalid key " k)
                                    (.isAcceptable k)  (let [^SocketChannel new-ch (accept-key! k)]
                                                         (println "Registering channel for selection:" new-ch)
                                                         (.configureBlocking new-ch false)
                                                         (.register new-ch selector SelectionKey/OP_READ))
                                    (.isReadable k)    (when-let [result (read-key k)]
                                                         (println (String. (.array result) StandardCharsets/US_ASCII))))))))
    (catch java.util.NoSuchElementException e
      (println "No more elements to process"))))

;; http://tutorials.jenkov.com/java-nio/server-socket-channel.html
(defn nio-server
  [shutdown? {:keys [host port]
              :or   {host "localhost"
                     port 0}
              :as sock}]
  (fn []
    (let [selector    (Selector/open)
          server-chan (ServerSocketChannel/open)]
      (doto server-chan
        (.configureBlocking false)
        (.bind (socket-address sock))
        (.register selector SelectionKey/OP_ACCEPT))

      (println "Server started on" (.getLocalAddress server-chan))
      (loop []
        (if-not (realized? shutdown?)
          (do (println "Next iteration: Sleeping for 500ms")
              (Thread/sleep 500)
              (process-selected selector)
              (recur))
          (println "Received shutdown signal, terminating"))))))

(defn shutdown!
  [server]
  (deliver server true))

(def server
  (let [shutdown? (promise)]
    (.start (Thread. (nio-server shutdown? {})))
    shutdown?))

(comment
  (shutdown! server)
  )

;; Why does this matter? Because we can wait for hundreds of threads and not
;; block on any of them!
;; Load testing time!

;; Netty
;; https://www.baeldung.com/netty
;; https://dzone.com/articles/thousands-of-socket-connections-in-java-practical
;; http://www.kegel.com/c10k.html
