(ns iwaswhere-web.store-test
  "Here, we test the handler functions of the server side store component."
  (:require [clojure.test :refer [deftest testing is]]
            [iwaswhere-web.files :as f]
            [iwaswhere-web.store :as s]
            [iwaswhere-web.graph.query :as gq]
            [me.raynes.fs :as fs]
            [iwaswhere-web.files :as f]
            [iwaswhere-web.utils.misc :as u]
            [clojure.set :as set]
            [iwaswhere-web.graph.stats :as gs]
            [iwaswhere-web.file-utils :as fu]
            [iwaswhere-web.location :as loc]
            [iwaswhere-web.store-test-common :as stc]))

(def some-test-entry
  {:mentions   #{"@SantaClaus"}
   :tags       #{"#test" "#xmas"}
   :timezone   "Europe/Berlin"
   :utc-offset -120
   :longitude  9.9999
   :latitude   53.112233
   :timestamp  1450998000000
   :md         "Ho ho ho, #some-test #xmas @SantaClaus"})

(defn mk-test-entry
  "Generate test entry with current timestamp."
  [test-ts]
  {:mentions   #{"@myself" "@someone"}
   :tags       #{"#test" "#new" "#entry" "#blah"}
   :timezone   "Europe/Berlin"
   :utc-offset -120
   :longitude  9.9999
   :latitude   53.112233
   :timestamp  test-ts
   :md         "Some #test #entry @myself #new #blah @someone"})

(def simple-query
  {:search-text ""
   :tags        #{}
   :not-tags    #{}
   :mentions    #{}
   :date-string nil
   :timestamp   nil
   :n           40})


(defn mk-test-state
  "Create test state by calling the component's state function and returning
   a state snapshot"
  [test-ts]
  (let [test-daily-logs-path (str "./test-data/daily-logs/" test-ts "/")
        test-path (str "./test-data")]
    (fs/mkdirs test-daily-logs-path)
    (with-redefs [fu/data-path test-path
                  fu/daily-logs-path test-daily-logs-path]
      {:current-state @(:state (s/state-fn (fn [_])))
       :logs-path     test-daily-logs-path
       :test-path     test-path})))

(def private-tags #{"#pvt" "#private" "#nsfw" "#consumption"})

(deftest geo-entry-persist-test
  (testing
    "Validates that handler properly adds entry and persists entry, including
     storing the hashtags and mentions in graph."
    (let [test-ts (System/currentTimeMillis)
          {:keys [current-state test-path logs-path]} (mk-test-state test-ts)
          test-entry (stc/enrich-geoname-mock (mk-test-entry test-ts))]
      (with-redefs [fu/data-path test-path
                    fu/daily-logs-path logs-path
                    loc/enrich-geoname stc/enrich-geoname-mock]
        (let [{:keys [new-state emit-msg]}
              (f/geo-entry-persist-fn {:current-state current-state
                                       :msg-payload   test-entry})
              res (gq/get-filtered new-state simple-query)]

          (testing
            "entry added to graph"
            (is (contains? (:sorted-entries new-state) (:timestamp test-entry)))
            ;; graph query also contains lists of comments and linked entries,
            ;; which would be empty here
            (is (= test-entry (-> (get (:entries-map res) (:timestamp test-entry))
                                  (dissoc :comments)
                                  (dissoc :last-saved)
                                  (dissoc :id)
                                  (dissoc :linked-entries-list)))))

          (testing
            "hashtag was created for entry"
            (is (= (:tags test-entry)
                   (set (gq/find-all-hashtags new-state)))))

          (testing
            "mention was created for entry"
            (is (= (:mentions test-entry) (gq/find-all-mentions new-state))))

          (testing
            "log was appended by entry"
            (let [files (file-seq (clojure.java.io/file logs-path))
                  last-log (last (f/filter-by-name files #"\d{4}-\d{2}-\d{2}.jrn"))]
              (with-open [reader (clojure.java.io/reader last-log)]
                (let [last-line (last (line-seq reader))
                      parsed (clojure.edn/read-string last-line)]
                  (is (= (dissoc parsed :id :last-saved) test-entry))))))

          (testing
            "handler emits saved message"
            (let [entry-saved-msg (first emit-msg)
                  saved-msg (second entry-saved-msg)]
              (is (= :entry/saved (first entry-saved-msg)))
              (is (= test-entry (dissoc saved-msg :id :last-saved))))))))))

(defn geo-entry-update-assertions
  "Common assertions in geo-entry-update-test, can be used with both the initial in-memory graph
  and the same but reconstructed from log files."
  [state res test-entry]
  (testing
    "entry updated in graph"
    (is (contains? (:sorted-entries state) (:timestamp test-entry)))
    ;; graph query also contains lists of comments and linked entries,
    ;; which would be empty here
    (is (= test-entry (-> (get (:entries-map res) (:timestamp test-entry))
                          (dissoc :comments)
                          (dissoc :id)
                          (dissoc :last-saved)
                          (dissoc :linked-entries-list)))))

  (testing
    "hashtag was created for entry"
    (is (= (:tags test-entry)
           (set (gq/find-all-hashtags state)))))

  (testing
    "mention was created for entry"
    (is (= (:mentions test-entry) (gq/find-all-mentions state)))))

(deftest geo-entry-update-test
  (testing
    "Validates that handler properly updates and persists entry. In particular,
     if only the old entry contained a particular tag, this orphan should be
     removed from database."
    (let [test-ts (System/currentTimeMillis)
          {:keys [current-state test-path logs-path]} (mk-test-state test-ts)
          test-entry (mk-test-entry test-ts)
          updated-test-entry (merge test-entry
                                    {:tags     #{"#testing" "#new" "#entry"}
                                     :md       "Some #testing #entry @me #new"
                                     :mentions #{"@me"}})
          updated-test-entry (stc/enrich-geoname-mock updated-test-entry)]
      (with-redefs [fu/data-path test-path
                    fu/daily-logs-path logs-path
                    loc/enrich-geoname stc/enrich-geoname-mock]
        (let [{:keys [new-state]} (f/geo-entry-persist-fn
                                    {:current-state current-state
                                     :msg-payload   test-entry})
              {:keys [new-state emit-msg]} (f/geo-entry-persist-fn
                                             {:current-state new-state
                                              :msg-payload   updated-test-entry})
              res (gq/get-filtered new-state simple-query)

              state-from-disk (:current-state (mk-test-state test-ts))
              res-from-disk (gq/get-filtered state-from-disk simple-query)]
          ;; test with in-memory graph
          (geo-entry-update-assertions new-state res updated-test-entry)

          (testing
            "log was appended by new entry"
            (let [files (file-seq (clojure.java.io/file logs-path))
                  last-log (last (f/filter-by-name files #"\d{4}-\d{2}-\d{2}.jrn"))]
              (with-open [reader (clojure.java.io/reader last-log)]
                (let [last-line (last (line-seq reader))
                      parsed (clojure.edn/read-string last-line)]
                  (is (= updated-test-entry
                         (dissoc parsed :id :last-saved)))))))

          (testing
            "handler emits updated message"
            (let [entry-saved-msg (first emit-msg)
                  saved-msg (second entry-saved-msg)]
              (is (= :entry/saved (first entry-saved-msg)))
              (is (= updated-test-entry
                     (dissoc saved-msg :id :last-saved)))))

          ;; test with graph reconstructed from disk
          (geo-entry-update-assertions state-from-disk res-from-disk updated-test-entry))))))


(defn trash-entry-assertions
  "Common assertions in trash-entry-test, can be used with both the initial
   in-memory graph and the same but reconstructed from log files."
  [state res test-entry]
  (testing "entry not in graph after deletion"
    (is (not (contains? (:sorted-entries state) (:timestamp test-entry))))
    (is (empty? (filter #(= (:timestamp %) (:timestamp test-entry))
                        (:entries res)))))

  (testing
    "unique hashtags from entry not in graph after last entry containing
     these tags removed"
    (is (empty? (set/intersection (:hashtags res)
                                  (set/difference (:mentions test-entry)
                                                  (:mentions some-test-entry))))))

  (testing
    "mentions from entry not in graph after last entry containing tags removed"
    (is (empty? (set/intersection (:mentions res) (:mentions test-entry))))))

(deftest trash-entry-test
  (testing
    "Validates that handler properly deletes entry from graph and persists the
     deletion message. Also runs the same assertions against graph reconstructed
     from files."
    (let [test-ts (System/currentTimeMillis)
          {:keys [current-state test-path logs-path]} (mk-test-state test-ts)
          test-entry (mk-test-entry test-ts)
          delete-msg {:timestamp (:timestamp test-entry) :deleted true}]
      (with-redefs [fu/data-path test-path
                    fu/daily-logs-path logs-path]
        (let [{:keys [new-state]} (f/geo-entry-persist-fn
                                    {:current-state current-state
                                     :msg-payload   some-test-entry})
              {:keys [new-state]} (f/geo-entry-persist-fn
                                    {:current-state new-state
                                     :msg-payload   test-entry})
              {:keys [new-state]} (f/trash-entry-fn {:current-state new-state
                                                     :msg-payload   delete-msg})
              res (gq/get-filtered new-state simple-query)

              state-from-disk (:current-state (mk-test-state test-ts))
              res-from-disk (gq/get-filtered state-from-disk simple-query)]

          ;; run tests for in-memory graph
          (trash-entry-assertions new-state res test-entry)

          ;; also run tests for state reconstructed from log files
          (trash-entry-assertions state-from-disk res-from-disk test-entry)

          (testing
            "log was appended by deletion entry"
            (let [files (file-seq (clojure.java.io/file logs-path))
                  last-log (last (f/filter-by-name files #"\d{4}-\d{2}-\d{2}.jrn"))]
              (with-open [reader (clojure.java.io/reader last-log)]
                (let [last-line (last (line-seq reader))
                      parsed (clojure.edn/read-string last-line)]
                  (is (= parsed delete-msg)))))))))))

(deftest cmp-map-test
  (testing "cmp-map contains required keys"
    (let [cmp-id :server/store-cmp
          cmp-map (s/cmp-map cmp-id)
          handler-map (:handler-map cmp-map)]
      (is (= (:cmp-id cmp-map) cmp-id))
      (is (fn? (:state-fn cmp-map)))
      (is (fn? (:entry/import handler-map)))
      (is (fn? (:entry/find handler-map)))
      (is (fn? (:entry/update handler-map)))
      (is (fn? (:entry/trash handler-map)))
      (is (fn? (:state/search handler-map)))
      (is (fn? (:cfg/refresh handler-map)))
      (is (fn? (:cmd/keep-alive handler-map)))
      (is (fn? (:stats/get handler-map)))
      (is (fn? (:state/stats-tags-get handler-map))))))
