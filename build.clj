(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [simple.build :as sb]))

(def lib 'org.clojars.wang/ring-exceptions)

;; if you want a version of MAJOR.MINOR.COMMITS:
(def version (format "2.0.%s" (b/git-count-revs nil)))

(defn jar
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/clean)
      (bb/jar)))

(defn tag
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (sb/tag)))

(defn uberjar
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/clean)
      (sb/uberjar)))

(defn install
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (sb/install)))

(defn release
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (sb/release)))