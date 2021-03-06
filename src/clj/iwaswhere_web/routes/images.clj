(ns iwaswhere-web.routes.images
  "Functions for serving images."
  (:require [compojure.core :refer [GET]]
            [iwaswhere-web.files :as f]
            [clojure.java.io :as io]
            [image-resizer.format :refer :all]
            [image-resizer.resize :refer :all]
            [image-resizer.scale-methods :refer :all]
            [image-resizer.rotate :refer :all]
            [iwaswhere-web.imports :as i]
            [clojure.string :as s]
            [iwaswhere-web.file-utils :as fu]))

(def img-resized-route
  (GET "/photos2/:filename" [filename :as r]
    (let [filename (str fu/data-path "/images/" filename)
          file (io/file filename)
          exif (i/extract-exif file)
          orientation (get exif "Orientation" "")
          rotate-fn (cond (s/includes? orientation "(Rotate 90 CW)")
                          (rotate-270-counter-clockwise-fn)
                          (s/includes? orientation "(Rotate 180)")
                          (rotate-180-counter-clockwise-fn)
                          (s/includes? orientation "(Rotate 270 CW)")
                          (rotate-90-counter-clockwise-fn)
                          :else identity)
          params (:params r)
          width (Integer/parseInt (or (:width params) "1024"))
          height (Integer/parseInt (or (:height params) "1024"))]
      (-> file
          (rotate-fn)
          ((resize-fn width height speed))
          (as-stream-by-mime-type "image/jpeg")))))
