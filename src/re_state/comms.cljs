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

;; FIXME these are nasty
(rf/reg-sub
  :db/get-in
  (fn [db [_ & args]]
    (apply get-in db args)))

(rf/reg-event-db
  :db/assoc-in
  (fn [db [_ & args]]
    (apply assoc-in db args)))

(rf/reg-fx
  :log
  (fn [string]
    (log string)))

