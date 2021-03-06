(ns iwaswhere-web.imports-test
  "Here, we test the handler functions of the imports component."
  (:require [clojure.test :refer [deftest testing is]]
            [iwaswhere-web.imports :as i]))

(def test-exif
  {"GPS Latitude Ref" "N"
   "GPS Latitude"     "53° 32' 41.2\""})

(deftest dms-to-dd-test
  (is (= (float 53.544777)
         (i/dms-to-dd test-exif "GPS Latitude" "GPS Latitude Ref"))))

(deftest cmp-map-test
  (testing "cmp-map contains required keys"
    (let [cmp-id :server/ft-cmp
          cmp-map (i/cmp-map cmp-id)
          handler-map (:handler-map cmp-map)]
      (is (= (:cmp-id cmp-map) cmp-id))
      (is (fn? (:import/photos handler-map)))
      (is (fn? (:import/geo handler-map)))
      (is (fn? (:import/movie handler-map)))
      (is (fn? (:import/phone handler-map))))))
