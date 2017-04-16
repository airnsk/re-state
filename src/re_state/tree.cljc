(ns re-state.tree)


;; TREE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn- state-tree
  [state-machines root-fsm-key]
  (let [;; component-keys: return the components of the state `state-key` in `fsm`
        component-keys (fn [state-key fsm]
                         (let [state (get (:states fsm) state-key)]
                           (:components state)))

        component-state-trees (fn [state-key fsm]
                                (mapv #(state-tree state-machines %)
                                      (component-keys state-key fsm)))

        key-and-components (fn [state-key fsm]
                             {state-key (apply merge (component-state-trees state-key fsm))})

        root-fsm (get state-machines root-fsm-key)
        root-fsm-states (:states root-fsm)]
    {root-fsm-key (apply merge (map #(key-and-components % root-fsm)
                                    (keys root-fsm-states)))}))


(defn- parent-links
  [m root-key]
  (let [ks (keys m)
        kvs (interleave ks (repeat root-key))
        trees (map #(get m %) ks)
        sub-kvs (map #(parent-links %1 %2) trees ks)]
    (flatten (concat kvs sub-kvs))))

(defn- parent-map
  [tree]
  (let [kvs (parent-links tree nil)]
    (apply assoc {} kvs)))

(defn set-state-tree!
  [statechart state-machines root-fsm-key]
  (let [tree (state-tree state-machines root-fsm-key)
        parents (parent-map tree)]
    (swap! statechart assoc :tree tree :parents parents)))

(defn tree
  [statechart]
  (get @statechart :tree))


(defn super
  "Given a state-key, return its superstate;
  given an fsm-key, return its super-fsm"
  [statechart k]
  (let [parent-map (get @statechart :parents)
        p (get parent-map k)]
    (get parent-map p)))



;; ACTIVE STATES ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn active-states
  [statechart]
  (get @statechart :active-states))

(defn set-active-states!
  [statechart states root-fsm-key]
  (swap! statechart assoc :active-states states))



;; LEAST COMMON ANCESTOR ;;;;;;;;;;;;;;;;;;;;;;;;;



(defn- path-to-root
  [statechart state-kw]
  (if (nil? state-kw)
    []
    (concat [state-kw] (path-to-root (super statechart state-kw)))))


(defn lca-path
  "Return states-to-exit and states-to-enter,
  which constitute the path from from-state to to-state
  via least common ancestor"
  [statechart from-state to-state]
  (cond (= :internal to-state) [[] []]
        (= from-state to-state) [[from-state] [to-state]]
        :else (let [exit-path (reverse (path-to-root statechart from-state))
                    entrance-path (reverse (path-to-root statechart to-state))]
                (loop [exit exit-path
                       entrance entrance-path]
                  (if (and (first exit)
                           (= (first exit) (first entrance)))
                    (recur (rest exit) (rest entrance))
                    [(reverse exit) entrance])))))

