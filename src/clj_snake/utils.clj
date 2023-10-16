(ns clj-snake.utils
  (:require [clojure.core.async :as async])
  (:import [org.jline.terminal TerminalBuilder Terminal]
           [org.jline.keymap   BindingReader]
           [java.util Collection]))

(defn terminal ^Terminal []
  (-> (TerminalBuilder/builder)
      (.jansi true)
      (.build)))

(defn char-reader ^BindingReader [^Terminal terminal]
  (. terminal enterRawMode)
  (BindingReader. (-> terminal .reader)))

(def ^BindingReader key-reader (char-reader (terminal)))

(defn poll-keys [dst]
  (async/go (while true
    (flush)
    (async/>!! dst (-> key-reader .readCharacter char))))
  (-> dst))

(defn repeat-lastest-on-interval [src interval]
  (let [dst (async/chan)]
  (async/go-loop [frm (System/currentTimeMillis)
                  prv (async/<!! src)]
    (let [end (System/currentTimeMillis)
          dif (- end frm)
          val (async/poll! src)]
    (if (or (some? val) 
            (>= dif interval))
      (do (async/>!! dst (or val prv))
          (recur end (or val prv)))
    (recur frm prv))))
  (-> dst)))

(defn start-with [dst val]
  (async/go (async/>! dst val))
  (-> dst))

(defn has? [^Collection coll val]
  (.contains coll val))

(defmacro add-vec [a b]
  `(mapv + ~a ~b))
