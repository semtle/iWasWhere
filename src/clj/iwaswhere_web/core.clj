(ns iwaswhere-web.core
  "In this namespace, the individual components are initialized and wired
  together to form the backend system."
  (:gen-class)
  (:require [matthiasn.systems-toolbox.switchboard :as sb]
            [matthiasn.systems-toolbox-sente.server :as sente]
    ;[matthiasn.systems-toolbox-probe.probe :as probe]
            [iwaswhere-web.index :as idx]
            [iwaswhere-web.specs]
            [clojure.tools.logging :as log]
            [clj-pid.core :as pid]
            [iwaswhere-web.store :as st]
            [iwaswhere-web.fulltext-search :as ft]
            [iwaswhere-web.upload :as up]
            [iwaswhere-web.blink :as bl]
            [iwaswhere-web.imports :as i]
            [matthiasn.systems-toolbox.scheduler :as sched]
            [matthiasn.systems-toolbox-zipkin.core :as z]))

(defonce switchboard (sb/component :server/switchboard))

(defn restart!
  "Starts or restarts system by asking switchboard to fire up the ws-cmp for
   serving the client side application and providing bi-directional
   communication with the client, plus the store and imports components.
   Then, routes messages to the store and imports components for which those
   have a handler function. Also route messages from imports to store component.
   Finally, sends all messages from store component to client via the ws
   component."
  [switchboard]
  (sb/send-mult-cmd
    switchboard
    [[:cmd/init-comp #{(z/trace-cmp (sente/cmp-map :server/ws-cmp idx/sente-map))
                       (z/trace-cmp (sched/cmp-map :server/scheduler-cmp))
                       (z/trace-cmp (i/cmp-map :server/imports-cmp))
                       (z/trace-cmp (st/cmp-map :server/store-cmp))
                       (z/trace-cmp (up/cmp-map :server/upload-cmp))
                       (z/trace-cmp (bl/cmp-map :server/blink-cmp))
                       (z/trace-cmp (ft/cmp-map :server/ft-cmp))}]

     [:cmd/route {:from :server/ws-cmp
                  :to   #{:server/store-cmp
                          :server/blink-cmp
                          :server/imports-cmp}}]

     [:cmd/route {:from :server/imports-cmp
                  :to   :server/store-cmp}]

     [:cmd/route {:from :server/upload-cmp
                  :to   :server/store-cmp}]

     [:cmd/route {:from :server/store-cmp
                  :to   #{:server/ws-cmp
                          :server/ft-cmp}}]

     [:cmd/route {:from :server/scheduler-cmp
                  :to   #{:server/store-cmp
                          :server/blink-cmp
                          :server/imports-cmp
                          :server/ws-cmp}}]

     [:cmd/route {:from #{:server/store-cmp
                          :server/blink-cmp
                          :server/imports-cmp}
                  :to   :server/scheduler-cmp}]

     [:cmd/send {:to  :server/scheduler-cmp
                 :msg [:cmd/schedule-new {:timeout (* 5 60 1000)
                                          :message [:import/spotify]
                                          :repeat  true
                                          :initial true}]}]]))

(defn -main
  "Starts the application from command line, saves and logs process ID. The
   system that is fired up when restart! is called proceeds in core.async's
   thread pool. Since we don't want the application to exit when the current
   thread is out of work, we just put it to sleep."
  [& _args]
  (pid/save "iwaswhere.pid")
  (pid/delete-on-shutdown! "iwaswhere.pid")
  (log/info "Application started, PID" (pid/current))
  (restart! switchboard)
  #_(when (get (System/getenv) "PROBE")
      (probe/start! switchboard))
  (Thread/sleep Long/MAX_VALUE))
