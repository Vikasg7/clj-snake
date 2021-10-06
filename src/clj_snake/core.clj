(ns clj-snake.core
  (:import [org.jline.terminal TerminalBuilder Terminal]
           [org.jline.keymap   BindingReader]
           [io.reactivex.rxjava3.core Observable Scheduler ObservableEmitter]
           [io.reactivex.rxjava3.schedulers Schedulers]
           [io.reactivex.rxjava3.disposables Disposable]
           [java.util.concurrent TimeUnit])
  (:require [rx-clojure.statics   :as rx]
            [rx-clojure.operators :as op]
            [rx-clojure.functions :as fns]
            [clojure.core.match :refer [match]]
            [clojure.string :refer [join]])
  (:use [clj-snake.utils]))

(set! *warn-on-reflection* true)

(defn terminal ^Terminal []
  (-> (TerminalBuilder/builder)
      (.jansi true)
      (.build)))

(defn char-reader ^BindingReader [^Terminal terminal]
  (BindingReader. (-> terminal .reader)))

(def ^BindingReader key-reader (char-reader (terminal)))

(defn key-inputs []
  (flush)
  (-> key-reader .readCharacter char))

(defn to-async [source]
  (-> source
      (op/subscribeOn (Schedulers/io))
      (op/observeOn (Schedulers/single))))

(defn repeat-latest-on-interval 
  ([source delay unit]
    (repeat-latest-on-interval source delay unit (Schedulers/io)))
  ([source delay unit ^Scheduler scheduler]
    (let [sub (fn [^ObservableEmitter e]
                (let [dsp (atom (Disposable/empty))
                      nxt (fn nxt [value]
                            (when (not (.isDisposed e))
                              (.onNext e value)
                              (.dispose ^Disposable @dsp)
                              (reset! dsp (.scheduleDirect scheduler (fns/runnable #(nxt value)) delay unit))))
                      err (fn err [error]
                            (.dispose ^Disposable @dsp)
                            (.onError e error))
                      com (fn com []
                            (.dispose ^Disposable @dsp)
                            (.onComplete e))]
                (-> source (op/subscribe nxt err com))))]
    (-> Observable (rx/create sub)))))

(defn filter-opposite [prev curr]
  (match [prev curr]
    [\w \s] \w
    [\s \w] \s
    [\a \d] \a
    [\d \a] \d
    :else   curr))

(def directions
  (-> Observable (rx/fromIterable (repeatedly key-inputs))
      (op/startWithItem \d)
      (op/filter #{\w \a \s \d})
      (op/scan filter-opposite)
      (op/compose to-async)))

(def offset
  {\w [-1 0]
   \a [0 -1]
   \s [1 0]
   \d [0 1]})

(def head first)

(def tail rest)

(defn ran-into-tail? [snake]
  (has? (tail snake) (head snake)))

(defn ran-into-wall? [rows cols snake]
  (let [[r c] (head snake)]
  (or (not (in-range? 0 rows r))
      (not (in-range? 0 cols c)))))

(defn game-over? [{:keys [rows cols snake] :as state}]
  (or (ran-into-tail? snake)
      (ran-into-wall? rows cols snake)))

(defn pick-rand-pos [rows cols]
  (mapv rand-int [rows cols]))

(defn make-frame [{:keys [rows cols snake food] :as state}]
  (for [r (range rows)]
  (for [c (range cols)]
    (cond (has? snake [r c]) "O"
          (= [r c] food)     "#"
          :else              "."))))

(defn print-frame [state]
  (let [frame (->> (make-frame state)
                   (mapv (partial join " "))
                   (join "\n"))]
  (println (str "\033[H\033[2J" frame))))

(defn ate? [food snake]
  (= food (head snake)))

(defn food [{:keys [food snake rows cols] :as state}]
  (if (ate? food snake) (pick-rand-pos rows cols) food))

(defn grow [{:keys [food snake] :as state} direction]
  (let [nhead (add-vec (head snake) (offset direction))
        snake (cons nhead snake)
        ate?  (ate? food snake)]
  (cond ate?  snake
        :else (butlast snake))))

(defn update-state [state direction]
  (-> state
      (assoc :snake (grow state direction))
      (assoc :food  (food state))))

(defn initial-state [rows cols]
  {:rows  rows
   :cols  cols
   :snake [[0 1] [0 0]] ;; First element is snake head.
   :food  (pick-rand-pos rows cols)
   :ate?  false})

(defn snake-game [rows cols speed]
  (let [initial (initial-state rows cols)]
  (-> directions
      (repeat-latest-on-interval speed TimeUnit/MILLISECONDS)
      (op/scan initial update-state)
      (op/takeWhile (comp not game-over?)))))

(defn -main [& args]
  (-> (snake-game 15 25 500)
      (op/blockingSubscribe print-frame identity)))
