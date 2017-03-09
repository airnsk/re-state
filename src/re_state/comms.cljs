(ns re-state.comms
  (:require [re-frame.core :as rf]
            [re-frame.loggers :as loggers]
            [re-frame.registrar :as registrar]
            [re-frame.db :as db]))

(def dispatch rf/dispatch)
(def subscribe rf/subscribe)
(def run-queue #()) ;; rf runs the queue automatically

(def register-handler rf/reg-event-fx)
(def lookup-handler (partial registrar/get-handler :event))

(def loggers (loggers/get-loggers))
(def log (:log loggers))
(def warn (:warn loggers))
(def error (:error loggers))

(def statechart db/app-db)
(def path-key :statechart)
(def path (rf/path path-key))
#_(add-watch statechart :debug
           (fn [_ _ old-state new-state]
             (when-not (identical? (path-key old-state) (path-key new-state))
               (h/pp (path-key new-state)))))

(rf/reg-fx :log log)

