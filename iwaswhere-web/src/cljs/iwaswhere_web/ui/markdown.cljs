(ns iwaswhere-web.ui.markdown
  (:require [markdown.core :as md]
            [iwaswhere-web.helpers :as h]
            [clojure.string :as s]
            [cljsjs.moment]))

(defn hashtags-replacer
  "Replaces hashtags in entry text. Depending on show-hashtags? switch either displays
  the hashtag or not. Creates link for each hashtag, which opens iWasWhere in new tab,
  with the filter set to the clicked hashtag."
  [show-hashtags?]
  (fn [acc hashtag]
    (let [f-hashtag (if show-hashtags? hashtag (subs hashtag 1))
          with-link (str " <a target='_blank' href='/#" hashtag "'>" f-hashtag "</a> ")]
      (s/replace acc (re-pattern (str "[^*]" hashtag "(?!\\w)")) with-link))))

(defn mentions-replacer
  "Replaces mentions in entry text."
  [acc mention]
  (let [with-link (str " <a class='mention-link' target='_blank' href='/#" mention "'>" mention "</a> ")]
    (s/replace acc mention with-link)))

(defn- reducer
  "Generic reducer, allows calling specified function for each item in the collection."
  [text coll fun]
  (reduce fun text coll))

(defn markdown-render
  "Renders a markdown div using :dangerouslySetInnerHTML. Not that dangerous here since
  application is only running locally, so in doubt we could only harm ourselves.
  Returns nil when entry does not contain markdown text."
  [entry show-hashtags?]
  (when-let [md-string (:md entry)]
    (let [formatted-md (-> md-string
                           (reducer (:tags entry) (hashtags-replacer show-hashtags?))
                           (reducer (:mentions entry) mentions-replacer))]
      [:div {:dangerouslySetInnerHTML {:__html (md/md->html formatted-md)}}])))

(defn editable-md-render
  "Renders markdown in a pre>code element, with editable content. Sends update message to store
  component on any change to the component. The save button sends updated entry to the backend."
  [entry temp-entry hashtags mentions put-fn]
  (let [md-string (or (:md temp-entry) (:md entry) "edit here")
        ts (:timestamp entry)
        get-content #(aget (.. % -target -parentElement -parentElement -firstChild -firstChild) "innerText")
        update-temp-fn (fn [ev]
                         (let [cursor-pos {:cursor-pos (.-anchorOffset (.getSelection js/window))}
                               updated (with-meta (merge entry (h/parse-entry (get-content ev))) cursor-pos)]
                           (put-fn [:update/temp-entry {:timestamp ts :updated updated}])))
        on-keydown-fn (fn [ev] (let [key-code (.. ev -keyCode)
                                     meta-key (.. ev -metaKey)]
                                 (when (and meta-key (= key-code 83))
                                   (if (not= entry temp-entry) ; when no change, toggle edit mode
                                     (put-fn [:text-entry/update temp-entry])
                                     (put-fn [:cmd/toggle {:timestamp ts :key :show-edit-for}]))
                                   (.preventDefault ev))
                                 (when (= key-code 9)
                                   ;(put-fn [:text-entry/update temp-entry])
                                   (prn key-code)
                                   (.preventDefault ev))))]
    [:div.edit-md
     [:pre [:code {:content-editable true
                   :on-input         update-temp-fn
                   :on-key-down      on-keydown-fn}
            md-string]]
     (when temp-entry
       (let [cursor-pos (:cursor-pos (meta temp-entry))
             md (:md temp-entry)
             before-cursor (subs md 0 cursor-pos)
             current-tag (re-find (js/RegExp. "(?!^)#[\\w\\-\\u00C0-\\u017F]+$" "m") before-cursor)
             current-tag-regex (js/RegExp. current-tag "i")
             tag-substr-filter (fn [tag] (when current-tag (re-find current-tag-regex tag)))]
         [:div.suggestions
          (for [tag (filter tag-substr-filter hashtags)]
            ^{:key (str ts tag)}
            [:div
             {:on-click #(let [updated (merge entry (h/parse-entry (s/replace md current-tag tag)))]
                          (put-fn [:update/temp-entry {:timestamp ts :updated updated}]))}
             [:span.hashtag tag]])]))
     (when temp-entry
       (let [cursor-pos (:cursor-pos (meta temp-entry))
             md (:md temp-entry)
             before-cursor (subs md 0 cursor-pos)
             current-mention (re-find (js/RegExp. "@[\\w\\-\\u00C0-\\u017F]+$" "m") before-cursor)
             current-mention-regex (js/RegExp. current-mention "i")
             mention-substr-filter (fn [mention] (when current-mention (re-find current-mention-regex mention)))]
         [:div.suggestions
          (for [mention (filter mention-substr-filter mentions)]
            ^{:key (str ts mention)}
            [:div
             {:on-click #(let [updated (merge entry (h/parse-entry (s/replace md current-mention mention)))]
                          (put-fn [:update/temp-entry {:timestamp ts :updated updated}]))}
             [:span.mention mention]])]))]))

(defn md-render
  "Helper for conditionally either showing rendered output or editable markdown."
  [entry temp-entry hashtags mentions put-fn editable? show-hashtags?]
  (if editable? (editable-md-render entry temp-entry hashtags mentions put-fn)
                (markdown-render entry show-hashtags?)))