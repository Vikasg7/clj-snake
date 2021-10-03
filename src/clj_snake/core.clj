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
            [clojure.core.match :refer [match]]))

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
      (op/filter #{\w \a \s \d})
      (op/scan filter-opposite)
      (op/distinctUntilChanged)
      (op/compose to-async)))

(defn -main [& args]
  (-> directions
      (repeat-latest-on-interval 750 TimeUnit/MILLISECONDS)
      (op/blockingSubscribe println println)))
