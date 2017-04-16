(ns re-state.core
  (:require [clojure.walk :as w]
            [re-state.comms :refer [dispatch error lookup-handler]]
            [re-state.tree :refer [super
                                   active-states
                                   set-active-states!
                                   set-state-tree!]]
            [re-state.chart :refer [get-start-state
                                    enter-state
                                    register-statechart]]))



;; CLONE FSM ;;;;;;;;;;;;;;;;;;;;



(defn- get-ns
  [fsm]
  (-> fsm :states keys first namespace))

(defn- transform-kw
  [kw orig-ns ns]
  (if (= (namespace kw) orig-ns)
    (keyword ns (name kw))
    kw))

(defn nested-replace [pred replacer data]
  (w/postwalk
    (fn [el]
      (if (pred el)
        (replacer el)
        el))
    data))

(defn clone-fsm
  "Make a copy of fsm, replacing its namespace in every keyword with ns"
  [fsm ns]
  (let [selector keyword?
        orig-ns (get-ns fsm)
        transform-fn #(transform-kw % orig-ns ns)]
    (nested-replace selector transform-fn fsm)))



;; DISPATCH ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn- leaves-of-active
  "Currently active states that have no currently active child states"
  [statechart]
  (loop [active (active-states statechart)
         leaves []]
    (let [s (first active)]
      (if (nil? s)
        leaves
        (let [s-has-child? (some #(= s (super statechart %)) (rest active))]
          (recur (rest active)
                 (if s-has-child?
                   leaves
                   (conj leaves s))))))))


(defn- bubble-up
  "Find first ancestor state for which the transition key [state trigger] is registered"
  [statechart state trigger]
  (loop [state state]
    (if (or (nil? state)
            (lookup-handler [state trigger]))
      state
      (recur (super statechart state)))))


(defn dispatch-transition
  "Bubble up the state hierarchy from the leaf active states
  to states that implement the transition, and dispatch"
  [statechart event-v]
  (let [trigger (first event-v)
        bubble-states (->> (leaves-of-active statechart)
                           (map #(bubble-up statechart % trigger))
                           (remove #(nil? %))
                           ;; remove duplicates:
                           set)]
    (doseq [state bubble-states]
      (let [new-event-v (vec (concat [[state trigger]] (rest event-v)))]
        (dispatch new-event-v)))))



;; MAIN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn- merge-no-clobber
  "Merge the maps but go nuts if a key is repeated"
  [& maps]
  (let [f (fn [val0 val1] (error "Clobbering a handler! " val0 " " val1))]
    (apply merge-with f maps)))


(defn start-app
  "root-fsm-key is the namespace of the starting active state"
  [statechart middleware state-machines root-fsm-key]
  (set-state-tree! statechart state-machines root-fsm-key)

  (let [fsms (vals state-machines)
        obtain (fn [prop]
                 (apply merge-no-clobber
                        (map #(get % prop) fsms)))

        all-actions      (obtain :actions)
        all-activities   (obtain :activities)
        all-transitions  (obtain :transitions)
        all-states       (obtain :states)
        all-start-states (remove nil? (map #(get % :start-state) fsms))

        chart-data {:all-actions all-actions
                    :all-activities all-activities
                    :all-transitions all-transitions
                    :all-states all-states
                    :all-start-states all-start-states}]

    (register-statechart middleware chart-data)

    (let [app-start-state (get-start-state root-fsm-key all-start-states)
          active-states (enter-state app-start-state
                                     [] #{} chart-data)]
      (set-active-states! statechart active-states root-fsm-key))))

