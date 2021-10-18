(ns clj-snake.core
  (:import [io.reactivex.rxjava3.core Observable]
           [java.util.concurrent TimeUnit])
  (:require [rx-clojure.statics   :as rx]
            [rx-clojure.operators :as op]
            [clojure.core.match :refer [match]]
            [clojure.string :refer [join]]
            [clojure.tools.cli :refer [parse-opts]])
  (:use [clj-snake.utils]))

(set! *warn-on-reflection* true)

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
      ;; un-comment this line to make snake NOT dash forward
      ;; while holding the key down
      ;; (op/distinctUntilChanged) 
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

(defn print-frame [{:keys [rows cols] :as state}]
  (let [frame (->> (make-frame state)
                   (mapv (partial join " "))
                   (join "\n"))]
  (println (str "\033[" (str rows) "A"
                "\033[" (str cols) "D" 
                frame))))

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
      (update-map :snake grow direction)
      (update-map :food  food)))

(defn initial-state [rows cols]
  {:rows  rows
   :cols  cols
   :snake [[0 1] [0 0]] ;; First element is snake head.
   :food  (pick-rand-pos rows cols)})

(defn snake-game [rows cols speed]
  (let [initial (initial-state rows cols)]
  (-> directions
      (repeat-latest-on-interval speed TimeUnit/MILLISECONDS)
      (op/scan initial update-state)
      (op/takeWhile (comp not game-over?)))))

(def cli-options
  [["-r" "--rows" "No. of Rows in the Grid"
    :default 15
    :parse-fn #(Integer/parseInt %)]
   ["-c" "--cols" "No. of Cols in the Grid"
    :default 30
    :parse-fn #(Integer/parseInt %)]
   ["-d" "--delay-in-ms" "Delay between each frame in milliseconds"
    :default 250
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])

(defn run-game [{:keys [rows cols delay-in-ms]}]
  (-> (snake-game rows cols delay-in-ms)
      (op/blockingSubscribe print-frame println)))

(defn -main [& args]
  (let [{:keys [options
                arguments
                errors
                summary]} (parse-opts args cli-options)]
  (cond (:help options) (println summary)
        (some? errors)  (println summary "\n\n" (join "\n" errors))
        :else           (run-game options))))

