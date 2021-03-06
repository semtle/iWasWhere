(ns iwaswhere-web.ui.menu
  (:require [iwaswhere-web.helpers :as h]
            [re-frame.core :refer [subscribe]]
            [matthiasn.systems-toolbox.component :as stc]
            [reagent.core :as r]
            [iwaswhere-web.utils.parse :as up]
            [iwaswhere-web.utils.parse :as p]))

(defn toggle-option-view
  "Render button for toggle option."
  [{:keys [option cls]} put-fn]
  (let [cfg (subscribe [:cfg])]
    (fn toggle-option-render [{:keys [option cls]} put-fn]
      (let [show-option? (option @cfg)
            toggle-option #(put-fn [:cmd/toggle-key {:path [:cfg option]}])]
        [:span.fa.toggle
         {:class    (str cls (when-not show-option? " inactive"))
          :on-click toggle-option}]))))

(def toggle-options
  [{:option :show-pvt :cls "fa-user-secret"}
   {:option :comments-standalone :cls "fa-comments"}
   {:option :mute :cls "fa-volume-off"}
   {:option :hide-hashtags :cls "fa-hashtag"}
   {:option :sort-asc :cls " fa-sort-asc"}])

(defn new-import-view
  "Renders new and import buttons."
  [put-fn]
  [:div.new-import
   [:button.menu-new {:on-click (h/new-entry-fn put-fn {} nil)}
    [:span.fa.fa-plus-square] " new"]
   [:button.menu-new
    {:on-click (h/new-entry-fn put-fn {:entry-type :saga} nil)}
    [:span.fa.fa-plus-square] " new saga"]
   [:button.menu-new
    {:on-click (h/new-entry-fn put-fn {:entry-type :story} nil)}
    [:span.fa.fa-plus-square] " new story"]
   [:button {:on-click #(do (put-fn [:import/photos])
                            (put-fn [:import/geo])
                            (put-fn [:import/spotify])
                            (put-fn [:import/weight])
                            (put-fn [:import/phone]))}
    [:span.fa.fa-map] " import"]])

(defn cfg-view
  "Renders component for toggling display of options such as maps, comments.
   The options, with their respective config key and Font-Awesome icon classes
   are defined in the toggle-options vector above. The value for each is then
   set on the application's config, which is persisted in localstorage.
   The default is always false, as initially the key would not be defined at
   all (unless set in default-config)."
  [put-fn]
  (let [cfg (subscribe [:cfg])]
    (fn [put-fn]
      (let [toggle-qr-code
            (fn [_ev]
              (let [msg {:path [:cfg :qr-code]}
                    reset-msg (merge msg {:reset-to false})]
                (put-fn [:cmd/schedule-new {:timeout 20000
                                            :message [:cmd/toggle-key reset-msg]}])
                (put-fn [:cmd/toggle-key (merge msg {:reset-to true})])))]
        [:div
         (for [option toggle-options]
           ^{:key (str "toggle" (:cls option))}
           [toggle-option-view option put-fn])
         [:span.fa.fa-qrcode.toggle
          {:on-click toggle-qr-code
           :class    (when-not (:qr-code @cfg) "inactive")}]]))))

(defn upload-view
  "Renders QR-code with upload address."
  []
  (let [cfg (subscribe [:cfg])]
    (fn upload-view2-render []
      (when (:qr-code @cfg)
        [:img {:src (str "/upload-address/" (stc/make-uuid) "/qrcode.png")}]))))

(defn calendar-view
  "Renders calendar component."
  [put-fn]
  (let [calendar (r/adapt-react-class (aget js/window "deps" "Calendar" "default"))
        briefings (subscribe [:briefings])
        cfg (subscribe [:cfg])
        select-date (fn [dt]
                      (let [fmt (.format dt "YYYY-MM-DD")
                            q (up/parse-search (str "b:" fmt))]
                        (when-not (get @briefings fmt)
                          (let [weekday (.format dt "dddd")
                                md (str "## " weekday "'s #briefing")
                                new-entry (merge
                                            (p/parse-entry md)
                                            {:briefing     {:day fmt}
                                             :linked-story (-> @cfg :briefing :story)})
                                new-entry-fn (h/new-entry-fn put-fn new-entry nil)]
                            (new-entry-fn)))
                        (put-fn [:search/add {:tab-group :briefing :query q}])
                        (put-fn [:search/update q])))]
    (fn stats-view-render [put-fn]
      (let [briefings (mapv #(js/moment %) (keys @briefings))]
        [:div.calendar
         [calendar {:select-date select-date
                    :briefings   briefings}]]))))

(defn busy-status
  "Renders busy status indicator."
  []
  (let [busy (subscribe [:busy])]
    (fn busy-status-render []
      [:div.busy-status {:class (if @busy "red" "green")}])))

(defn menu-view
  "Renders component for rendering new and import buttons."
  [put-fn]
  [:div.menu-header
   [busy-status]
   [new-import-view put-fn]
   [calendar-view put-fn]
   [:h1 "iWasWhere?"]
   [cfg-view put-fn]
   [upload-view]])
