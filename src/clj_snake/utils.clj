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
  (BindingReader. (-> terminal .reader)))

(def ^BindingReader key-reader (char-reader (terminal)))

(defn poll-keys [dst]
  (async/go (while true
    (flush)
    (async/>!! dst (-> key-reader .readCharacter char)))))

(defn repeat-lastest-on-interval [src interval]
  (let [dst (async/chan)]
  (async/go-loop [frm (System/currentTimeMillis)
                  prv nil]
    (let [end (System/currentTimeMillis)
          dif (- end frm)
          nxt (async/alt!! src ([val] val) :default nil)]
    (if (-> nxt (or (>= dif interval)))
      (do (async/>!! dst nxt)
          (recur end nxt))
    (recur frm prv))))
  (-> dst)))

(defn has? [^Collection coll val]
  (.contains coll val))

(defmacro add-vec [a b]
  `(mapv + ~a ~b))
