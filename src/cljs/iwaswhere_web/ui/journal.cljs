(ns iwaswhere-web.ui.journal
  (:require [iwaswhere-web.utils.misc :as u]
            [iwaswhere-web.ui.entry.entry :as e]
            [re-frame.core :refer [subscribe]]
            [iwaswhere-web.utils.parse :as ps]
            [reagent.ratom :refer-macros [reaction]]
            [clojure.set :as set]
            [iwaswhere-web.ui.draft :as draft]))

(defn journal-view
  "Renders journal div, one entry per item, with map if geo data exists in the
   entry."
  [local-cfg put-fn]
  (let [cfg (subscribe [:cfg])
        query-cfg (subscribe [:query-cfg])
        options (subscribe [:options])
        entries-map (subscribe [:entries-map])
        results (subscribe [:results])
        active (reaction (-> @cfg :active))]
    (fn journal-view-render [local-cfg put-fn]
      (let [conf (merge @cfg @options)
            query-id (:query-id local-cfg)
            query (reaction (get-in @query-cfg [:queries (:query-id local-cfg)]))
            active-id (query-id @active)
            entries (query-id @results)
            entries-map-deref @entries-map
            entries (map (fn [ts] (get entries-map-deref ts)) entries)
            show-pvt? (:show-pvt conf)
            filtered-entries (if show-pvt?
                               entries
                               (filter (u/pvt-filter conf @entries-map) entries))
            show-context? (:show-context conf)
            comments-w-entries? (not (:comments-standalone conf))
            with-comments? (fn [entry] (and (or (and comments-w-entries?
                                                     (not (:comment-for entry)))
                                                (not comments-w-entries?))
                                            (or (:new-entry entry) show-context?)))
            find-missing (u/find-missing-entry @entries-map put-fn)]
        [:div.journal
         [:div.journal-entries
          (when-let [story (:story local-cfg)]
            (find-missing story)
            ^{:key story}
            [e/entry-with-comments story put-fn local-cfg])
          (for [entry filtered-entries]
            (when (with-comments? entry)
              ^{:key (:timestamp entry)}
              [e/entry-with-comments (:timestamp entry) put-fn local-cfg]))
          (when (> (count entries) 1)
            (let [show-more #(put-fn [:show/more {:query-id query-id}])]
              [:div.show-more {:on-click show-more :on-mouse-over show-more}
               [:span.show-more-btn [:span.fa.fa-plus-square] " show more"]]))]]))))
