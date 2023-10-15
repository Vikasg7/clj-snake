(ns clj-snake.core
  (:gen-class)
  (:require [clojure.string :refer [join]]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-snake.utils :as util]
            [clojure.core.async :as async]))

(set! *warn-on-reflection* true)

(def offset
  {\w [-1 0]
   \a [0 -1]
   \s [1 0]
   \d [0 1]})

(def head first)

(def tail rest)

(defn ran-into-tail? [snake]
  (util/has? (tail snake) (head snake)))

(defn ran-into-wall? [rows cols snake]
  (let [[r c] (head snake)]
  (not (and (>= r 0) (< r rows)
            (>= c 0) (< c cols)))))

(defn game-over? [{:keys [rows cols snake]}]
  (or (ran-into-tail? snake)
      (ran-into-wall? rows cols snake)))

(defn pick-rand-pos [rows cols]
  (mapv rand-int [rows cols]))

(defn make-frame [{:keys [rows cols snake food]}]
  (for [r (range rows)]
  (for [c (range cols)]
    (cond (util/has? snake [r c]) "O"
          (= [r c] food)     "#"
          :else              "-"))))

(defn print-frame [{:keys [rows cols] :as game}]
  (let [frame (->> (make-frame game)
                   (mapv (partial join " "))
                   (join "\n"))]
  (println (str "\033[" (str rows) "A"
                "\033[" (str cols) "D" 
                frame))))

(defn ate? [food snake]
  (= food (head snake)))

(defn food [{:keys [food snake rows cols]}]
  (if (ate? food snake) (pick-rand-pos rows cols) food))

(defn grow [{:keys [food snake key]}]
  (let [nhead (util/add-vec (head snake) (offset key))
        snake (cons nhead snake)
        ate?  (ate? food snake)]
  (cond ate?  snake
        :else (butlast snake))))

(defn update-game [game]
  (assoc game
    :snake (grow game)
    :food  (food game)
    :over? (game-over? game)))

(defn uturn? [prev curr]
  (case [prev curr]
    ([\w \s] 
     [\s \w] 
     [\a \d] 
     [\d \a]) true 
     #_else   false))

(defn initial-state [rows cols]
  {:rows  rows
   :cols  cols
   :snake (reverse [[0 0] [0 1] [0 2]]) ;; First element is snake head.
   :key   \d
   :food  (pick-rand-pos rows cols)
   :over? false})

(def WASD ^:const [\w \a \s \d])

(defn snake-game [rows cols speed]
  (let [keys-chan (async/chan)]
  (async/go (util/poll-keys keys-chan))
  (loop [game  (initial-state rows cols)
         start (System/currentTimeMillis)]
    (when-not (:over? game)
      (print-frame game)
      (let [now    (System/currentTimeMillis)
            elapsd (- now start)
            key    (async/alt!! keys-chan ([key] key) :default nil)
            ngame (if (or (nil? key)
                          (not (util/has? WASD key))
                          (uturn? key (:key game)))
                    game
                  (assoc game :key key))]
      (if (or (>= elapsd speed)
              (some? key))
        (recur (update-game ngame) now)
      (recur ngame start)))))))

(defn run-game [{:keys [rows cols delay-in-ms]}]
  (snake-game rows cols delay-in-ms))

(def cli-options
  [["-r" "--rows ROWS" "No. of Rows in the Grid"
    :default 15
    :parse-fn #(Integer/parseInt %)]
   ["-c" "--cols COLS" "No. of Cols in the Grid"
    :default 30
    :parse-fn #(Integer/parseInt %)]
   ["-d" "--delay-in-ms DELAY IN MILLISECONDS" "Delay between each frame in milliseconds"
    :default 500
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options
                errors
                summary]} (parse-opts args cli-options)]
  (cond (:help options) (println summary)
        (some? errors)  (println (str summary "\n\n" (join "\n" errors)))
        :else           (run-game options))))

