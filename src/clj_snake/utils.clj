(ns clj-snake.utils
  (:import [org.jline.terminal TerminalBuilder Terminal]
           [org.jline.keymap   BindingReader]
           [io.reactivex.rxjava3.core Observable Scheduler ObservableEmitter]
           [io.reactivex.rxjava3.schedulers Schedulers]
           [io.reactivex.rxjava3.disposables Disposable]
           [java.util Collection])
  (:require [rx-clojure.statics   :as rx]
            [rx-clojure.operators :as op]
            [rx-clojure.functions :as fns]))

(set! *warn-on-reflection* true)

(defn terminal ^Terminal []
  (-> (TerminalBuilder/builder)
      (.jansi true)
      (.build)))

(defn char-reader ^BindingReader [^Terminal terminal]
  (BindingReader. (-> terminal .reader)))

(def ^Terminal      terminal   (terminal))
(def ^BindingReader key-reader (char-reader terminal))

(defn poll-key []
  (flush)
  (-> key-reader .readCharacter char))

(defn key-events []
  (let [sub (fn [^ObservableEmitter e]
              (let [f (future (while (not (.isDisposed e))
                                (.onNext e (poll-key))))]
              (.setCancellable e (fns/cancellable #(future-cancel f)))))]
    (-> Observable (rx/create sub))))

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
                      nxt (fn nxt [val]
                            (when (not (.isDisposed e))
                              (.dispose ^Disposable @dsp)
                              (reset! dsp (.schedulePeriodicallyDirect scheduler #(.onNext e val) 0 delay unit))))
                      err (fn err [error]
                            (.dispose ^Disposable @dsp)
                            (.onError e error))
                      com (fn com []
                            (.dispose ^Disposable @dsp)
                            (.onComplete e))]
                (.setDisposable e (-> source (op/subscribe nxt err com)))))]
    (-> Observable (rx/create sub)))))

(defn has? [^Collection coll val]
  (.contains coll val))

(defn add-vec [a b]
  (mapv + a b))

(defn in-range? [u l v]
    (and (>= v u) (< v l)))

(defn update-map [m k f & args]
  (assoc m k (apply f (cons m args))))
