(ns clj-snake.core
  (:import [org.jline.terminal TerminalBuilder Terminal]
           [org.jline.keymap   BindingReader]
           [io.reactivex.rxjava3.core Observable Scheduler ObservableEmitter Scheduler$Worker]
           [io.reactivex.rxjava3.schedulers Schedulers]
           [io.reactivex.rxjava3.disposables Disposable]
           [java.util.concurrent TimeUnit])
  (:require [rx-clojure.statics   :as rx]
            [rx-clojure.operators :as op]
            [rx-clojure.functions :as fns]))

(set! *warn-on-reflection* true)

(defn terminal ^Terminal []
  (-> (TerminalBuilder/builder)
      (.jansi true)
      (.build)))

(defn char-reader ^BindingReader [^Terminal terminal]
  (-> terminal .enterRawMode)
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
                (let [dsp (atom nil)
                      nxt (fn nxt [value]
                            (when (not (.isDisposed e))
                              (.onNext e value)
                              (when-let [d ^Disposable @dsp] (.dispose d))
                              (reset! dsp (.scheduleDirect scheduler (fns/runnable #(nxt value)) delay unit))))
                      err (fn err [error]
                            (when-let [d ^Disposable @dsp] (.dispose d))
                            (.onError e error))
                      com (fn com []
                            (when-let [d ^Disposable @dsp] (.dispose d))
                            (.onComplete e))]
                (-> source (op/subscribe nxt err com))))]
    (-> Observable (rx/create sub)))))

(def directions
  (-> Observable (rx/fromIterable (repeatedly key-inputs))
      (op/startWithItem "r")
      (op/distinctUntilChanged)
      (op/compose to-async)))

(defn -main [& args]
  (-> directions
      (repeat-latest-on-interval 1000 TimeUnit/MILLISECONDS)
      (op/blockingSubscribe println)))

;; (dorun (->> (repeatedly key-inputs)
;;             (take 10)
;;             (map println)))