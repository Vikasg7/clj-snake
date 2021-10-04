(ns clj-snake.utils)

(defn has? [coll val]
  (.contains coll val))

(defn add-vec [a b]
  (mapv + a b))

(defn in-range? [u l v]
    (and (>= v u) (< v l)))
